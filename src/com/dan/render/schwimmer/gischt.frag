#version 460
// gischt.frag – render.schwimmer (S8a): Licht in den Tropfen (seit L6a rechnet es gischt.vert je Tropfen, hier nur Form und Deckung). Sonne und Mond mit der Durchlässigkeit der Luft, gestreut
// nach Henyey–Greenstein (Wassertropfen streuen nach vorn, g = 0,72 für Spritzwasser, 0,55 für Nebel; wie com.dan.spray),
// dazu der Himmel als mittlere Leuchtdichte aus fünf Richtungen; Albedo 0,9 [G]. Ausgabe Leuchtdichte mal Vorbelichtung,
// vormultipliziert mit der Deckung; die Luft löscht nach exp(−β d) (Koschmieder) [G]. Dicht vor dem Auge blendet sie aus
// (unter 0,3 m), damit eine Tafel nicht das Bild deckt; was die Linse trifft, zeigt render.frame.Linse.
#include "/com/dan/render/schwimmer/gischt.glsl"
layout(location = 0) in vec2 vUv;
layout(location = 1) in float vAlpha;
layout(location = 2) in vec3 vRel;
layout(location = 3) flat in int vArt;
layout(location = 4) flat in float vZufall;
layout(location = 5) flat in float vAlter;
layout(location = 6) flat in vec3 vL;
layout(location = 0) out vec4 farbe;

void main() {
    float r2 = dot(vUv, vUv), form;
    if (vArt == 1) form = exp(-r2 * 3.2) * smoothstep(1.0, 0.6, r2) * (0.7 + 0.6 * vZufall);
    else form = exp(-vUv.x * vUv.x * 3.0) * smoothstep(1.0, 0.55, abs(vUv.y));
    float d = length(vRel);
    float a = min(0.97, vAlpha * form * mehr.y * (vArt == 1 ? 0.10 : 1.0)) * smoothstep(0.3, 1.0, d);
    if (a < 0.002) discard;
    farbe = vec4(vL * a, a * exp(-werte.w * d));
}
