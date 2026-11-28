// see_kiel_am.glsl — Seegang Werkbuch, S7d (Datei 89): das Kielwasser an einem Ort aus den Kacheln von see_kiel.comp.
// Vor dem Einbinden KIEL_BINDING setzen. kielAm(p, d) (p relativ zu B, d: Abstand der Stellen, die fragen) gibt (Höhe,
// Schaum, mittlere Wellenzahl, 1 im Bereich): aus der nahen Kachel, wo sie reicht; über ihre letzten acht Zellen in die
// weite überblendet [G]; sonst aus der weiten; außerhalb beider 0. Wer gröber fragt als 1,5 Zellen einer Kachel, bekommt
// sie nicht (sonst faltet sich das Muster in lange Wellen) [G]. Bilinear zwischen den Mitten der Zellen.
layout(std430, binding = KIEL_BINDING) readonly buffer KielKacheln { int n; int an; int k1; int k2; vec4 kachel[2]; vec4 z[]; } KI;

// Seit S7d6 (Seegang Werkbuch 94, SF133 A): nur noch Lage 0 (alle Quellen); die Körper rechnen ihr Kielwasser direkt aus den
// Spuren (see_kiel_spur.glsl), die Lagen der Quellen und kielAmOhne sind weg.
vec4 kielKachel(int t, vec2 p, out float rand) {
    int n = KI.n;
    vec4 kd = KI.kachel[t];
    vec2 q = (p - kd.xy) / kd.z - 0.5;
    rand = -1.0;
    if (q.x < 0.0 || q.y < 0.0 || q.x > float(n - 1) || q.y > float(n - 1)) return vec4(0.0);
    ivec2 i = min(ivec2(floor(q)), ivec2(n - 2));
    vec2 a = q - vec2(i);
    int b = t * n * n + i.y * n + i.x;
    vec4 v = mix(mix(KI.z[b], KI.z[b + 1], a.x), mix(KI.z[b + n], KI.z[b + n + 1], a.x), a.y);
    rand = min(min(q.x, q.y), min(float(n - 1) - q.x, float(n - 1) - q.y));
    return v;
}

vec4 kielAm(vec2 p, float d) {
    if (KI.an == 0 || KI.n < 2 || d > 1.5 * KI.kachel[1].z) return vec4(0.0);
    float r0, r1;
    if (d > 1.5 * KI.kachel[0].z) {
        vec4 weit = kielKachel(1, p, r1);
        return r1 < 0.0 ? vec4(0.0) : vec4(weit.xyz, 1.0);
    }
    vec4 nah = kielKachel(0, p, r0);
    if (r0 >= 8.0) return vec4(nah.xyz, 1.0);
    vec4 weit = kielKachel(1, p, r1);
    if (r0 < 0.0) return r1 < 0.0 ? vec4(0.0) : vec4(weit.xyz, 1.0);
    float w = clamp(r0 / 8.0, 0.0, 1.0);
    return vec4(mix(weit.xyz, nah.xyz, w), 1.0);
}
