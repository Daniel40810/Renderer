// scene.frag — die Szene der Vorführung, ohne Dreiecke gerechnet: Nachthimmel, See mit kleinen Wellen, ein
// rot-weißer Turm mit Laterne und Linse; innen der Laternenraum mit Dielen, Glas, Kuppel, Linsensockel und Linse.
// Das Feuer kommt aus beacon.glsl: Glitzer auf der See, die Linse von außen, die Lichtflecken innen.
// Schreibt Farbe (linear, vorbelichtet) und Tiefe (klassisch, −1..1).
in vec2 vUV;
out vec4 frag;
uniform mat4 uInvVP, uVP;
uniform vec3 uCam;
uniform vec3 uMoonDir;
uniform float uMoon;           // Mondlicht in lx (vorbelichtet)
uniform float uSkyL;           // Himmelsleuchtdichte am Horizont (vorbelichtet)
uniform float uTime;
uniform float uWet;            // 0 trocken, 1 Regen (raue See, dunstiger Himmel)
uniform float uSigma;          // Auslöschung der Luft 1/m (Dunst, Regen)

const float PI = 3.14159265;
const float TOWER_R = 3.0, TOWER_TOP = 22.6, GALLERY = 23.0, LANTERN_R = 1.85, GLASS0 = 23.5, GLASS1 = 25.6, DOME = 27.6;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

float hitCyl(vec3 o, vec3 d, float r, float y0, float y1, bool inside, out vec3 n) {
    float a = dot(d.xz, d.xz), b = dot(o.xz, d.xz), c = dot(o.xz, o.xz) - r * r;
    float disc = b * b - a * c;
    n = vec3(0.0);
    if (disc < 0.0 || a < 1e-9) return -1.0;
    float s = sqrt(disc);
    float t = inside ? (-b + s) / a : (-b - s) / a;
    if (t < 0.0) return -1.0;
    vec3 p = o + d * t;
    if (p.y < y0 || p.y > y1) return -1.0;
    n = normalize(vec3(p.x, 0.0, p.z)) * (inside ? -1.0 : 1.0);
    return t;
}

float ggx(float nh, float a) { float a2 = a * a; float d = nh * nh * (a2 - 1.0) + 1.0; return a2 / (PI * d * d); }

vec3 sky(vec3 d) {
    float h = max(d.y, 0.0);
    vec3 c = vec3(0.30, 0.42, 0.65) * uSkyL * (0.35 + 0.65 * exp(-h * 6.0));
    float st = step(0.997, hash(floor(d.xz / max(d.y, 0.05) * 300.0))) * (1.0 - uWet) * smoothstep(0.05, 0.3, h);
    return c + vec3(st) * uSkyL * 40.0;
}

void main() {
    vec4 c = uInvVP * vec4(vUV * 2.0 - 1.0, 1.0, 1.0);
    vec3 d = normalize(c.xyz / c.w - uCam);
    vec3 o = uCam;
    float tBest = 1e9;
    vec3 col = sky(d), n = vec3(0.0, 1.0, 0.0), p;
    int kind = 0;
    bool inside = length(o.xz) < LANTERN_R && o.y > GALLERY && o.y < DOME;
    vec3 nn;
    float t;
    if (!inside) {
        // See
        if (d.y < 0.0) { t = -o.y / d.y; if (t < tBest) { tBest = t; kind = 1; } }
        // Turm, Galerie, Laterne (Glas), Kuppel
        t = hitCyl(o, d, TOWER_R, -2.0, TOWER_TOP, false, nn); if (t > 0.0 && t < tBest) { tBest = t; kind = 2; n = nn; }
        t = hitCyl(o, d, 3.6, TOWER_TOP, GALLERY, false, nn); if (t > 0.0 && t < tBest) { tBest = t; kind = 3; n = nn; }
        t = hitCyl(o, d, LANTERN_R, GALLERY, GLASS0, false, nn); if (t > 0.0 && t < tBest) { tBest = t; kind = 3; n = nn; }
        t = hitCyl(o, d, LANTERN_R, GLASS0, GLASS1, false, nn); if (t > 0.0 && t < tBest) { tBest = t; kind = 4; n = nn; }
        t = hitCyl(o, d, LANTERN_R + 0.05, GLASS1, DOME - 0.6, false, nn); if (t > 0.0 && t < tBest) { tBest = t; kind = 5; n = nn; }
    } else {
        // Laternenraum von innen: Dielen, Glas, Kuppel, Sockel, Linse
        if (d.y < 0.0) { t = (GALLERY + 0.02 - o.y) / d.y; if (t < tBest) { tBest = t; kind = 6; n = vec3(0.0, 1.0, 0.0); } }
        t = hitCyl(o, d, LANTERN_R - 0.02, GALLERY, GLASS1 + 0.2, true, nn); if (t > 0.0 && t < tBest) { tBest = t; kind = 7; n = nn; }
        if (d.y > 0.0) { t = (GLASS1 + 0.2 - o.y) / d.y; vec3 q = o + d * t; if (t > 0.0 && t < tBest && length(q.xz) < LANTERN_R) { tBest = t; kind = 8; n = vec3(0.0, -1.0, 0.0); } }
        vec3 lc = bcnPos.xyz;
        t = hitCyl(o, d, 0.40, GALLERY, lc.y - 0.495, false, nn); if (t > 0.0 && t < tBest) { tBest = t; kind = 9; n = nn; }
        t = hitCyl(o, d, bcnLens.x, lc.y - bcnLens.y, lc.y + bcnLens.y, false, nn); if (t > 0.0 && t < tBest) { tBest = t; kind = 10; n = nn; }
    }
    p = o + d * tBest;
    vec3 V = -d;
    if (kind == 1) {
        // See: kleine Wellen, Spiegelung von Himmel und Feuer
        vec2 q = p.xz * 0.35;
        float e = 0.05;
        float h0 = vnoise(q + uTime * 0.3) + 0.5 * vnoise(q * 2.3 - uTime * 0.4);
        float hx = vnoise(q + vec2(e, 0) + uTime * 0.3) + 0.5 * vnoise((q + vec2(e, 0)) * 2.3 - uTime * 0.4);
        float hz = vnoise(q + vec2(0, e) + uTime * 0.3) + 0.5 * vnoise((q + vec2(0, e)) * 2.3 - uTime * 0.4);
        float amp = mix(0.25, 0.6, uWet);
        n = normalize(vec3(-(hx - h0) / e * amp * 0.35, 1.0, -(hz - h0) / e * amp * 0.35));
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
        vec3 r = reflect(d, n);
        col = mix(vec3(0.004, 0.012, 0.012) * (uMoon + uSkyL), sky(vec3(r.x, abs(r.y), r.z)), F);
        vec3 toL = bcnPos.xyz - p;
        float dl = length(toL);
        vec3 L = toL / dl;
        vec3 E = bcnIntensity(-L) / (dl * dl) * exp(-uSigma * dl);
        vec3 H = normalize(L + V);
        float rough = clamp(0.06 + tBest * 0.00004, 0.06, 0.2);
        col += E * ggx(max(dot(n, H), 0.0), rough) * F * 0.25 / max(dot(n, V), 0.05);
        vec3 Hm = normalize(uMoonDir + V);
        col += vec3(uMoon) * ggx(max(dot(n, Hm), 0.0), rough) * F * 0.25 / max(dot(n, V), 0.05);
    } else if (kind >= 2 && kind <= 5) {
        vec3 alb = kind == 2 ? (p.y < 8.0 ? vec3(0.03) : (p.y > 12.6 && p.y < 16.4 ? vec3(0.5, 0.04, 0.03) : vec3(0.75))) : kind == 5 ? vec3(0.03) : vec3(0.7);
        col = alb / PI * (uMoon * max(dot(n, uMoonDir), 0.0) + uSkyL * 1.5);
        if (kind == 4) {
            // Glas: dahinter die Linse, sonst der dunkle Raum mit dem Widerschein des Lichts
            vec3 nl;
            float tl = hitCyl(p, d, bcnLens.x, bcnPos.y - bcnLens.y, bcnPos.y + bcnLens.y, false, nl);
            vec3 w = normalize(uCam - bcnPos.xyz);
            float F = 0.04 + 0.96 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
            vec3 inner = tl > 0.0 ? bcnLensLuminance(w) * (0.75 + 0.25 * sin((p + d * tl).y * 190.0)) : bcnNearIrradiance(p - n * 0.05, n) * 0.04 / PI;
            col = inner * (1.0 - F) + sky(reflect(d, n)) * F;
        }
    } else if (kind >= 6) {
        // innen: Licht der Linse auf Dielen, Glas, Decke, Sockel
        vec3 alb = kind == 6 ? vec3(0.32, 0.20, 0.11) : kind == 7 ? vec3(0.06) : kind == 8 ? vec3(0.55) : vec3(0.25);
        if (kind == 6) alb *= 0.8 + 0.4 * hash(vec2(floor(p.x / 0.12), 3.0));
        vec3 E = bcnNearIrradiance(p, n);
        col = alb / PI * (E + vec3(uMoon * 0.2 + uSkyL));
        if (kind == 7) col += sky(d) * 0.6;
        if (kind == 10) {
            vec3 w = normalize(uCam - bcnPos.xyz);
            col = bcnLensLuminance(w) * (0.7 + 0.3 * sin(p.y * 190.0)) + vec3(0.02) * uSkyL;
        }
    }
    // Dunst und Regen zur Ferne
    if (kind != 0 && kind < 6) col = mix(col, sky(normalize(vec3(d.x, 0.02, d.z))) * 0.6, 1.0 - exp(-uSigma * tBest));
    frag = vec4(col, 1.0);
    float dist = kind == 0 ? 1e6 : tBest;
    vec4 cp = uVP * vec4(uCam + d * min(dist, 5e4), 1.0);
    gl_FragDepth = kind == 0 ? 1.0 : clamp(cp.z / cp.w * 0.5 + 0.5, 0.0, 1.0);
}
