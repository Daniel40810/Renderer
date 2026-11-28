// spiegelung.glsl – render.water, seit S9c-1 (Seegang Werkbuch 108): die Spiegelung des Geländes im Bild darunter, aus
// wasser_optik.glsl gelöst, damit auch spiegel_halb.comp sie rechnen kann. Vorher einbinden: wasser.glsl (Kopf, messAus).
// Bindungen 6 (Bild darunter) und 7 (Tiefe darunter) wie in wasser.frag; die grobe Tiefe mit SPIEGEL_GROB_BINDUNG.
layout(set = 0, binding = 6) uniform sampler2D unten;
layout(set = 0, binding = 7) uniform sampler2D tiefeUnten;
#ifdef SPIEGEL_GROB_BINDUNG
// seit S9b-1 (Seegang Werkbuch 107, SF171 A): je Kachel von sgKopf.x Bildpunkten aus tiefe_grob.comp {nächste Tiefe des
// Geländes längs der Blickachse (1e30 ohne), höchste Höhe H über dem Auge, die ein Bildpunkt dort zeigt, größte Steigung G
// seiner Richtung (≥ 0)}; sgAlle dasselbe H und G für das ganze Bild (geordnet als uint). sgKopf: Kachel, Zahl der Kacheln
// quer und hoch, 1 = gesetzt. Ein Treffer liegt höchstens um die Dicke hinter dem Gelände, also nicht höher als H + G · Dicke.
layout(set = 0, binding = SPIEGEL_GROB_BINDUNG, std430) readonly buffer SpiegelGrob { ivec4 sgKopf; uvec4 sgAlle; vec4 sg[]; };

float sgZahl(uint u) { return uintBitsToFloat((u & 0x80000000u) != 0u ? (u & 0x7fffffffu) : ~u); }

// Wo erreicht der Strahl P + r s (s > s1) im Bild die Gerade n = u? n ist (a + b s) / (c + d s) in Einheiten des halben
// Bilds. Kein Schnitt nach s1: 1e30.
float sgGrenze(float a, float b, float c, float d, float u, float s1) {
    float n = b - u * d;
    if (abs(n) < 1e-12) return 1e30;
    float s = (u * c - a) / n;
    return s > s1 ? s : 1e30;
}
#endif
const int SPIEGEL_SCHRITTE = 64;

// Bildpunkt eines Orts relativ zum Auge (x, y in Bildpunkten, z Abstand längs der Blickachse)
vec3 bildpunkt(vec3 Q) {
    float z = dot(Q, vorn.xyz);
    vec2 n = vec2(dot(Q, rechts.xyz) / dot(rechts.xyz, rechts.xyz), -dot(Q, oben.xyz) / dot(oben.xyz, oben.xyz)) / z;
    return vec3((n * 0.5 + 0.5) * bild.xy, z);
}

// Spiegelung im Bild darunter: Schritte wachsend um 15 % ab 0,2 m [G], ein Treffer liegt hinter dem Gelände im Bild,
// höchstens um die Länge des Schritts und 1 m [G]; dann vier Schritte Halbierung. Gewicht: zum Bildrand hin 0 [G].
// Seit S9b-1 (SF171 A): mit der groben Tiefe (SPIEGEL_GROB_BINDUNG) überspringt der Strahl, was vor allem Gelände seiner
// Kachel liegt: bis er die Kachel verlässt oder die nächste Tiefe darin erreicht. Die Schritte bleiben dieselben (s wächst
// weiter um 15 %), nur ihr Lesen entfällt, wo kein Treffer sein kann; so trifft er, wo der alte Weg traf.
vec3 spiegelung(vec3 P, vec3 r, out float w) {
    w = 0.0;
    float s = max(0.2, 2e-3 * length(P)), s0 = 0.0;
#ifdef SPIEGEL_GROB_BINDUNG
    bool grob = sgKopf.w == 1 && !messAus(MESS_SPIEGEL_ALT);
    float bis = 0.0;
    vec3 rx = rechts.xyz / dot(rechts.xyz, rechts.xyz), oy = oben.xyz / dot(oben.xyz, oben.xyz);
    float gAx = dot(P, rx), gBx = dot(r, rx), gAy = -dot(P, oy), gBy = -dot(r, oy), gC = dot(P, vorn.xyz), gD = dot(r, vorn.xyz);
    // die Dicke wächst ab dem zweiten Schritt um (1 − 1/1,15) |r · vorn| je m; steigt der Strahl schneller, bleibt er darüber
    float wachs = 0.1305 * abs(gD), hAlle = grob ? sgZahl(sgAlle.x) : 0.0, gAlle = grob ? sgZahl(sgAlle.y) : 0.0;
    bool hochAlle = grob && r.y >= gAlle * wachs;
#endif
    for (int k = 0; k < SPIEGEL_SCHRITTE; k++) {
#ifdef SPIEGEL_GROB_BINDUNG
        if (grob && s < bis) {
            s0 = s;
            s *= 1.15;
            continue;
        }
#endif
        vec3 Q = P + r * s, b = bildpunkt(Q);
        if (b.z <= vorn.w || b.x < 0.0 || b.y < 0.0 || b.x >= bild.x || b.y >= bild.y) return vec3(0.0);
#ifdef SPIEGEL_GROB_BINDUNG
        if (grob) {
            float dk = (s - s0) * abs(gD) + 1.0, rand = 1e-4 * b.z + 0.01;
            // Höhenschranke: über allem Gelände im Bild und schneller steigend als die Dicke – kein Treffer mehr
            if (hochAlle && Q.y > hAlle + gAlle * dk + rand) return vec3(0.0);
            int K = sgKopf.x;
            ivec2 kt = min(ivec2(b.xy) / K, sgKopf.yz - 1);
            vec4 t = sg[kt.y * sgKopf.y + kt.x];
            float zmin = t.x * 0.999;
            bool vor = b.z < zmin, drueber = r.y >= t.z * wachs && Q.y > t.y + t.z * dk + rand;
            if (vor || drueber) {
                float x0 = 2.0 * float(kt.x * K) / bild.x - 1.0, x1 = 2.0 * float(kt.x * K + K) / bild.x - 1.0;
                float y0 = 2.0 * float(kt.y * K) / bild.y - 1.0, y1 = 2.0 * float(kt.y * K + K) / bild.y - 1.0;
                float se = min(min(sgGrenze(gAx, gBx, gC, gD, x0, s), sgGrenze(gAx, gBx, gC, gD, x1, s)),
                               min(sgGrenze(gAy, gBy, gC, gD, y0, s), sgGrenze(gAy, gBy, gC, gD, y1, s)));
                // vor dem Gelände (und nicht darüber): nur bis die Tiefe die nächste der Kachel erreicht
                if (!drueber && gD > 0.0 && zmin < 1e29) se = min(se, (zmin - gC) / gD);
                bis = se < 1e29 ? s + 0.98 * (se - s) : 1e30;
                s0 = s;
                s *= 1.15;
                continue;
            }
        }
#endif
        float tg = texelFetch(tiefeUnten, ivec2(b.xy), 0).r;
        if (tg > 0.0) {
            float zt = vorn.w / tg, dicke = (s - s0) * abs(dot(r, vorn.xyz)) + 1.0;
            if (b.z > zt && b.z - zt < dicke) {
                float a = s0, e = s;
                for (int i = 0; i < 4; i++) {
                    float m = 0.5 * (a + e);
                    vec3 bm = bildpunkt(P + r * m);
                    float tm = texelFetch(tiefeUnten, ivec2(clamp(bm.xy, vec2(0.0), bild.xy - 1.0)), 0).r;
                    if (tm > 0.0 && bm.z > vorn.w / tm) e = m; else a = m;
                }
                b = bildpunkt(P + r * e);
                vec2 rand = min(b.xy, bild.xy - b.xy) / (0.05 * bild.y);
                w = clamp(min(rand.x, rand.y), 0.0, 1.0);
                return texelFetch(unten, ivec2(clamp(b.xy, vec2(0.0), bild.xy - 1.0)), 0).rgb;
            }
        }
        s0 = s;
        s *= 1.15;
    }
    return vec3(0.0);
}
