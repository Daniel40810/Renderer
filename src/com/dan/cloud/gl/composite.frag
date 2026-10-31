// composite.frag — legt das Wolkenbild über das Ziel: Ziel · Durchlässigkeit + eingestreutes Licht (Mischung ONE,
// SRC_ALPHA). Das Wolkenbild wird dabei bilinear auf volle Größe gezogen.
// Blitze (Phase 10): Lichtpunkte in oder unter der Wolke beleuchten sie hier, im fertigen Bild ohne Nachziehen der
// zeitlichen Mischung (ein Blitz dauert nur wenige Bilder). Am mittleren Ort der Wolke im Blick: Licht, das durch die
// Wolke wandert (Diffusion, Länge uDiff), und Licht von unten auf die Unterseite.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uClouds, uDist;
uniform int uFlashN;
uniform vec4 uFlashPos[4];      // Ort relativ zur Szene
uniform vec4 uFlashRgb[4];      // Lichtstärke RGB in cd · Vorbelichtung
uniform float uDiff;            // Diffusionslänge in der Wolke (m)
uniform mat4 uInvVP;
uniform int uReversed;
uniform int uPano;
uniform vec3 uCam;
uniform float uMaxDist;         // ohne Wolke im Blick steht dort die größte Entfernung: dann kein Licht
uniform sampler2D uDepth;       // seit Brocken Phase 3: Tiefe der Szene in voller Größe (uUseDepth = 1)
uniform int uUseDepth;
const float PI = 3.14159265;
void main() {
    vec4 c = texture(uClouds, vUV);
    if (uUseDepth == 1 && uPano == 0) {
        // Seit Brocken Phase 3: Hochrechnen nach der Tiefe. Das Wolkenbild ist kleiner als das Bild; an den Kanten des
        // Geländes (und wo das Nebelmeer den Hang schneidet) mischte die bilineare Abfrage Texel, die mit einer ganz
        // anderen Entfernung zur Geometrie gerechnet wurden. Darum zählen nur die Texel, deren Entfernung zur Geometrie
        // (Kanal G von uDist) zu der dieses Bildpunkts passt; passt keines, das nächstgelegene.
        float z = texelFetch(uDepth, ivec2(gl_FragCoord.xy), 0).r;
        float sd = 1e30;
        if (uReversed == 1 ? z > 0.0 : z < 1.0) {
            vec4 q = uInvVP * vec4(vUV * 2.0 - 1.0, uReversed == 1 ? z : z * 2.0 - 1.0, 1.0);
            sd = length(q.xyz / q.w - uCam);
        }
        vec2 ts = vec2(textureSize(uDist, 0));
        vec2 f = fract(vUV * ts - 0.5);
        vec4 sg = textureGather(uDist, vUV, 1);
        vec4 w = vec4((1.0 - f.x) * f.y, f.x * f.y, f.x * (1.0 - f.y), (1.0 - f.x) * (1.0 - f.y));
        bool sky = sd > 1e29;
        vec4 diff = sky ? vec4(greaterThan(sg, vec4(1e29))) : abs(sg - vec4(sd)) / max(sd, 1.0);
        vec4 ok = sky ? diff : vec4(lessThan(diff, vec4(0.05)));
        vec4 wk = w * ok;
        float ws = wk.x + wk.y + wk.z + wk.w;
        if (ws < 1e-4) {
            // keines passt: das Texel mit der ähnlichsten Entfernung; Gelände, um das herum nur Himmel gerechnet wurde (an
            // der Kimm), bekommt keine Wolken: die Wolken jener Texel stehen über der Kimm, nicht vor dem Hang
            vec4 dd = sky ? vec4(1.0) - diff : diff;
            float m = min(min(dd.x, dd.y), min(dd.z, dd.w));
            wk = vec4(equal(dd, vec4(m)));
            ws = wk.x + wk.y + wk.z + wk.w;
            if (!sky && dot(wk, vec4(greaterThan(sg, vec4(1e29)))) > 0.0) { frag = vec4(0.0, 0.0, 0.0, 1.0); return; }
        }
        c = vec4(dot(textureGather(uClouds, vUV, 0), wk), dot(textureGather(uClouds, vUV, 1), wk),
                 dot(textureGather(uClouds, vUV, 2), wk), dot(textureGather(uClouds, vUV, 3), wk)) / ws;
    }
    if (uFlashN > 0 && c.a < 0.999) {
        vec3 d;
        if (uPano == 1) {
            float az = (vUV.x - 0.5) * 2.0 * PI, el = (vUV.y - 0.5) * PI;
            d = vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el));
        } else {
            vec4 q = uInvVP * vec4(vUV * 2.0 - 1.0, uReversed == 1 ? 0.5 : 0.0, 1.0);
            d = normalize(q.xyz / q.w - uCam);
        }
        // bilinear nur über die Texel, die eine Wolke sehen: an den Rändern mischte die Abfrage sonst die Wolke mit
        // der größten Entfernung dahinter und landete irgendwo zwischen ihnen
        vec2 ts = vec2(textureSize(uDist, 0));
        vec2 f = fract(vUV * ts - 0.5);
        vec4 dg = textureGather(uDist, vUV, 0);           // (0,1) (1,1) (1,0) (0,0)
        vec4 w = vec4((1.0 - f.x) * f.y, f.x * f.y, f.x * (1.0 - f.y), (1.0 - f.x) * (1.0 - f.y));
        w *= vec4(lessThan(dg, vec4(uMaxDist * 0.98)));
        float ws = w.x + w.y + w.z + w.w;
        if (ws < 1e-4) { frag = c; return; }
        float dist = dot(dg, w) / ws;
        vec3 P = uCam + d * dist;
        vec3 L = vec3(0.0);
        for (int i = 0; i < 4; i++) {
            if (i >= uFlashN) break;
            vec3 q = uFlashPos[i].xyz - P;
            float r2 = dot(q, q), r = sqrt(r2);
            float inside = 3.0 * exp(-r / uDiff) / (4.0 * PI * (r2 + 640000.0));
            float below = q.y < 0.0 ? 0.8 * (-q.y / max(r, 1.0)) / (PI * max(r2, 22500.0)) : 0.0;
            L += uFlashRgb[i].rgb * (inside + below);
        }
        c.rgb += L * (1.0 - c.a);
    }
    frag = c;
}
