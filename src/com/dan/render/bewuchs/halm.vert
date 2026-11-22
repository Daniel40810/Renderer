#version 460
// halm.vert – render.bewuchs (P12c, F135 A): Halme am Auge. Je Instanz ein Büschel (Lage, Art, Albedo des Bodens), je
// Büschel bis zu HALME Halme zu 5 Dreiecken (zwei Glieder als Viereck, die Spitze als Dreieck). Form nach der Art [G]:
// Höhe, Breite, Neigung; im Wind gebogen (mittlere Biegung mit U, Böen, Schwanken). Zwischen den Radien info.x und info.y
// schrumpfen die Halme auf 0 (Übergang in das Material des Geländes). Licht wie das Laub (baum.frag).
#include "/com/dan/render/bewuchs/bewuchs.glsl"
#define HALME 8
struct Bueschel {
    vec4 p;     // x, Boden (m NHN), z, Art (1 Fenne, 2 Salzwiese, 3 Deich, 4 Warft, 24 Grasweg)
    vec4 c;     // Albedo des Bodens R G B (linear), Zufall
    vec4 f;     // Halme in diesem Büschel, Kantenlänge der Zelle (m), 0, 0
};
layout(set = 0, binding = 10, std430) readonly buffer Bueschelliste { Bueschel bu[]; };
layout(set = 0, binding = 11, std430) readonly buffer Halmkopf { vec4 info; };   // Radius Beginn und Ende des Übergangs (m), Höhe × , Tag im Jahr (seit P13b)
layout(location = 0) out vec3 vRel;
layout(location = 1) out vec3 vN;
layout(location = 2) out vec3 vCol;
layout(location = 3) flat out float vTeil;

float h1(float x) { return fract(sin(x * 12.9898 + 78.233) * 43758.5453); }

void main() {
    Bueschel b = bu[gl_InstanceIndex];
    int k = gl_VertexIndex / 15, e = gl_VertexIndex % 15;
    if (float(k) >= b.f.x) { gl_Position = vec4(0.0, 0.0, -2.0, 1.0); return; }
    float sd = b.c.w * 97.0 + float(k) * 7.31;
    int art = int(b.p.w + 0.5);
    // Höhe und Breite nach der Art [G] (Ende Oktober, geweidet)
    float hoch = art == 1 ? 0.20 : art == 2 ? 0.12 : art == 3 ? 0.12 : art == 4 ? 0.08 : 0.06;
    float breit = art == 2 ? 0.006 : 0.008;
    // P13b (F152 A): Zonen der Salzwiese nach dem Tag im Jahr [G nach Literatur, wie physics.vegetation.Salzwiese]:
    // s = 0 Mitte Januar … 1 Mitte Juli; Pionierzone Queller 0,04–0,20 m, untere Salzwiese 0,08–0,15, obere 0,15–0,30, Fenne unten beweidet
    float tag = info.w, s = 0.5 - 0.5 * cos(6.2832 * (tag - 20.0) / 365.25);
    if (art == 6) { hoch = mix(0.04, 0.20, s); breit = 0.010; }
    if (art == 7) { hoch = mix(0.08, 0.15, s); breit = 0.005; }
    if (art == 8) { hoch = mix(0.15, 0.30, s); breit = 0.006; }
    if (art == 9) { hoch = mix(0.04, 0.08, s) * 1.5; breit = 0.005; }
    hoch *= 0.6 + 0.8 * h1(sd + 1.0);
    // Übergang: weit weg kürzer, bis 0
    vec3 r0 = rel(b.p.xyz);
    float d = length(r0.xz);
    hoch *= clamp((info.y - d) / max(info.y - info.x, 1e-3), 0.0, 1.0) * info.z;
    if (hoch < 0.005) { gl_Position = vec4(0.0, 0.0, -2.0, 1.0); return; }
    // Fuß im Büschel, Richtung der Fläche, Neigung
    float a = 6.2832 * h1(sd + 2.0), rr = 0.35 * b.f.y * sqrt(h1(sd + 3.0));
    vec3 fuss = b.p.xyz + vec3(cos(a) * rr, -0.02, sin(a) * rr);
    float dreh = 6.2832 * h1(sd + 4.0);
    vec2 quer = vec2(cos(dreh), sin(dreh));
    vec2 neig = vec2(cos(dreh + 1.5708), sin(dreh + 1.5708)) * (0.15 + 0.35 * h1(sd + 5.0));
    // Wind
    vec2 w = wind.xy;
    float U = length(w);
    vec2 wd = U > 0.1 ? w / U : vec2(0.0);
    float boe = 1.0 + wind.w * sin(0.5 * wind.z + 0.21 * dot(b.p.xz, wd)) + 0.3 * sin(2.3 * wind.z + sd);
    vec2 biege = neig + wd * min(0.035 * U * boe, 1.0);
    // Scheitel: Stufen 0, 1/3, 2/3, 1; Dreiecke (0,1)(1,2) als Vierecke, Spitze
    const int stufe[15] = int[](0, 0, 1, 0, 1, 1, 1, 1, 2, 1, 2, 2, 2, 2, 3);
    const float seite[15] = float[](-1.0, 1.0, -1.0, 1.0, 1.0, -1.0, -1.0, 1.0, -1.0, 1.0, 1.0, -1.0, -1.0, 1.0, 0.0);
    float t = float(stufe[e]) / 3.0;
    float br = breit * (1.0 - 0.85 * t);
    vec3 q = fuss + vec3(biege.x * t * t * hoch, hoch * t * (1.0 - 0.3 * dot(biege, biege) * t), biege.y * t * t * hoch);
    q.xz += quer * seite[e] * br;
    vec3 n = normalize(vec3(-quer.y, 0.0, quer.x) + vec3(0.0, 0.4, 0.0));
    vN = normalize(mix(n, vec3(0.0, 1.0, 0.0), 0.6));     // Schattierung zum Boden hin [G]
    // Farbe: Albedo des Bodens, unten dunkler, oben heller und gelber (Mittel 1 über den Halm [G]); Queller rot
    vec3 alb = b.c.rgb;
    if (art == 2 && h1(floor(b.p.x / 3.0) * 13.0 + floor(b.p.z / 3.0)) > 0.7) alb = vec3(0.16, 0.05, 0.035);
    // P13b: Queller der Pionierzone rot von September bis November, Strandflieder der unteren Salzwiese lila im
    // Juli/August (die Blüte oben am Halm), im Winter alles strohig [G]
    float fleck = h1(floor(b.p.x / 1.5) * 17.0 + floor(b.p.z / 1.5));
    if (art == 6 && tag > 240.0 && tag < 335.0) alb = mix(alb, vec3(0.20, 0.05, 0.04), smoothstep(240.0, 270.0, tag) * (1.0 - smoothstep(320.0, 335.0, tag)));
    if (art == 7 && fleck > 0.75 && t > 0.6) alb = mix(alb, vec3(0.20, 0.12, 0.30), smoothstep(185.0, 200.0, tag) * (1.0 - smoothstep(240.0, 255.0, tag)));
    if (art >= 6 && art <= 9) alb = mix(alb, alb * vec3(1.15, 1.05, 0.75), 1.0 - s);
    vec3 ton = mix(vec3(0.75), vec3(1.2, 1.15, 0.95), t);
    vCol = alb * ton;
    vTeil = 1.0;
    vRel = rel(q);
    gl_Position = vp * vec4(vRel, 1.0);
}
