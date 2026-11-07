// lens.frag — das Bild auf dem Sensor (com.dan.lens): scharfes und unscharfes Bild nach dem Unschärfekreis gemischt, die
// gestreuten Lichter darüber; je Farbkanal an der Stelle, an die das Objektiv ihn legt (Verzeichnung aus den echten
// Hauptstrahlen, Farbquerfehler); Schleierlicht (ein kleiner Teil allen Lichts über das ganze Bild, Kern ∝ r^−2,5 [G]
// aus der Pyramide glare_down/glare_up); Randabfall (Fassungen · cos⁴). Ein- und Ausgabe in Einheiten der Szene (vor der Belichtung).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uColor;       // scharf, voll, mit Mip-Stufen
uniform sampler2D uDepth;
uniform sampler2D uState;
uniform sampler2D uDof;         // halbe Auflösung: rgb, a = Anteil
uniform sampler2D uSprites;     // halbe Auflösung: gestreute Lichter
uniform int uDofOn, uDistOn, uChromaOn, uVigOn, uGlareOn;
uniform float uRI[17], uDist[17], uLatR[17], uLatB[17];
uniform float uHalfDiag;        // mm
uniform float uZoom;            // gegen leere Ecken bei Tonnenverzeichnung
uniform float uGlare, uVigGain;
uniform sampler2D uGlareTex;    // Schleierlicht (halbe Auflösung, normiert), aus glare_down/glare_up

float focusMm() {
    float x = texelFetch(uState, ivec2(0), 0).x;
    return x > 0.0 ? 1000.0 / x : uFocusFallback;
}

vec3 sensorAt(vec2 uv, float fmm) {
    vec3 sharp = textureLod(uColor, uv, 0.0).rgb;
    if (uDofOn == 0) return sharp;
    float c = abs(cocMm(viewDepth(textureLod(uDepth, uv, 0.0).r), fmm)) / uPitch;   // voll, Durchmesser
    vec4 dof = texture(uDof, uv);
    // kleine Kreise (unter 10 Bildpunkten) in voller Auflösung: 1 + 16 Punkte gleichmäßig auf der Scheibe
    vec3 own = sharp;
    if (c > 0.8) {
        float r = 0.5 * min(c, 10.0);
        vec3 acc = sharp;
        for (int i = 0; i < 16; i++) {
            float a = float(i) * 2.39996 + 0.7, rr = r * sqrt((float(i) + 0.5) / 16.0);
            acc += textureLod(uColor, uv + rr * vec2(cos(a), sin(a)) / uFull, 0.0).rgb;
        }
        own = acc / 17.0;
    }
    vec3 base = mix(own, dof.rgb, smoothstep(7.0, 11.0, c));
    return mix(base, dof.rgb, dof.a) + texture(uSprites, uv).rgb;   // a: Deckung durch unscharfen Vordergrund
}

void main() {
    vec2 px = vUV * uFull - 0.5 * uFull;
    float rmm = length(px) * uPitch;
    float r = rmm / uHalfDiag;
    float fmm = focusMm();
    vec3 sc = vec3(1.0);
    if (uDistOn == 1) sc /= table17(uDist, r);
    if (uChromaOn == 1) { sc.r /= table17(uLatR, r); sc.b /= table17(uLatB, r); }
    sc *= uZoom;
    vec3 o;
    if (sc.r == sc.g && sc.g == sc.b) o = sensorAt(0.5 + (vUV - 0.5) * sc.g, fmm);
    else {
        o.r = sensorAt(0.5 + (vUV - 0.5) * sc.r, fmm).r;
        o.g = sensorAt(0.5 + (vUV - 0.5) * sc.g, fmm).g;
        o.b = sensorAt(0.5 + (vUV - 0.5) * sc.b, fmm).b;
    }
    if (uGlareOn == 1 && uGlare > 0.0) o = mix(o, texture(uGlareTex, vUV).rgb, uGlare);
    if (uVigOn == 1) o *= mix(1.0, table17(uRI, r), uVigGain);
    frag = vec4(max(o, vec3(0.0)), 1.0);
}
