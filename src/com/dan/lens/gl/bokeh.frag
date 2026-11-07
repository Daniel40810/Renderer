// bokeh.frag — Tiefenschärfe in halber Auflösung durch Sammeln (com.dan.lens). Jeder Bildpunkt fragt in Ringen um sich
// (bis zum größten Kreis der Nachbarkacheln), welche Punkte ihn mit ihrem Unschärfekreis treffen. Ob ein Punkt s den
// Empfänger p trifft, sagt die Pupille selbst: q = −2(p − s)/c ist der Ort in der Pupille (Kreis gleicher Fläche = 1), durch
// den das Licht von s nach p kommt – mit der Form der Blende, dem Katzenauge der Fassungen und, je Farbkanal, dem
// Längsfarbfehler. Die Helligkeit im Kreis folgt der sphärischen Aberration (heller Rand auf der einen Seite des Fokus,
// heller Kern auf der anderen). Hintergrund darf nicht über schärferen Vordergrund laufen (sein Kreis wird auf den des
// Empfängers begrenzt); Vordergrund vor dem Fokus wird eigens gesammelt und mit seiner Deckung darübergelegt – auch über
// sich selbst, so wird sein Rand von beiden Seiten weich, und was dahinter liegt, kommt aus der Nachbarschaft.
// Ergebnis: rgb, a = Deckung durch unscharfen Vordergrund (lens.frag nimmt dann dieses Bild statt seines eigenen).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHalf;        // Farbe (ohne die gestreuten Lichter), a = c (halbe Bildpunkte)
uniform sampler2D uTiles;       // x: größter |c| ringsum, y: größter Kreis davor
uniform int uTile;
uniform int uRings;             // Ringe der Abtastung (8k Punkte im Ring k)
uniform float uMaxCoc;

vec3 dcHalf() { return vec3(-uDz.x, 0.0, -uDz.y) / uFNumber / (2.0 * uPitch); }

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    ivec2 sz = textureSize(uHalf, 0);
    vec4 c0 = texelFetch(uHalf, p, 0);
    vec2 tl = texelFetch(uTiles, p / uTile, 0).xy;
    float reach = 0.5 * tl.x * uAreaRoot * max(1.0, uSqueeze) + 0.5;
    if (reach < 1.0) { frag = vec4(c0.rgb, 0.0); return; }
    vec2 posFull = (vec2(p) + 0.5) * 2.0;
    vec2 d = posFull - 0.5 * uFull;
    float th = atan(length(d) * uPitch / uEfl * uField);
    vec2 er = length(d) > 0.5 ? normalize(d) : vec2(1.0, 0.0);
    vec3 dc = dcHalf();
    float mmPerHalf = 2.0 * uPitch;

    int nS = 4 * uRings * (uRings + 1) + 1;
    float aSample = reach * reach / float(nS);      // Fläche je Punkt / π
    vec3 bgA = vec3(0.0), bgW = vec3(0.0), fgA = vec3(0.0), fgW = vec3(0.0);
    float c0c = c0.a;
    bool fgC = c0c < -1.0;
    for (int k = 0; k <= uRings; k++) {
        int n = k == 0 ? 1 : 8 * k;
        float rr = k == 0 ? 0.0 : (float(k) - 0.25) / (float(uRings) - 0.25) * reach;
        float ph = float(k) * 2.39996;               // goldener Winkel je Ring gegen Muster
        for (int i = 0; i < n; i++) {
            float a = ph + 6.2831853 * float(i) / float(n);
            vec2 o = rr * vec2(cos(a), sin(a));
            ivec2 sp = clamp(p + ivec2(round(o)), ivec2(0), sz - 1);
            vec4 s = texelFetch(uHalf, sp, 0);
            vec2 off = vec2(sp - p);
            float cs = s.a;
            bool back = cs >= c0c;
            vec3 w3;
            for (int ch = 0; ch < 3; ch++) {
                float cc = cs + dc[ch];
                if (back) { float lim = max(abs(c0c + dc[ch]), 1.0); cc = clamp(cc, -lim, lim); }   // Hintergrund nicht über Schärferes
                float ac = max(abs(cc), 0.75);
                vec2 q = 2.0 * off / (sign(cc + 1e-6) * ac);           // Pupille: q = −2(p − s)/c, p − s = −off
                float wEdge = 1.5 / ac;
                float cov = pupil(q, wEdge, th, er);
                w3[ch] = cov * saWeight(min(length(q), 1.0), cc * mmPerHalf) / (ac * ac);
            }
            bool fgS = cs < -1.0 && cs <= c0c + 0.5;                   // vor dem Fokus und nicht hinter dem Empfänger
            if (fgS) {
                vec3 k3 = w3 * aSample * 4.0;                          // Deckung: Fläche je Punkt / Fläche des Kreises
                fgA += s.rgb * k3;
                fgW += k3;
            } else if (fgC) {
                vec3 wf = vec3(1.0 / max(cs * cs, 1.0));                // was hinter dem Vordergrund liegt: aus der Nähe
                bgA += s.rgb * wf;
                bgW += wf;
            } else {
                bgA += s.rgb * w3;
                bgW += w3;
            }
        }
    }
    vec3 bg = bgA / max(bgW, vec3(1e-8));
    vec3 fga = clamp(fgW, 0.0, 1.0);
    vec3 fg = fgA / max(fgW, vec3(1e-8));
    vec3 col = bgW.g > 1e-8 ? mix(bg, fg, fga) : fg;
    float cover = fgC ? 0.0 : max(fga.r, max(fga.g, fga.b));   // Deckung eines unscharfen Vordergrunds über diesem Punkt
    frag = vec4(col, cover);
}
