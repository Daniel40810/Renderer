#version 430 core
// firework.vert — Sterne und Rauch als Bildtafeln zum Auge hin, sechs Ecken je Instanz ohne Eckpuffer (seit Timmendorf Phase 11).
// Stern  (uMode 0): iA = Ort, Lichtstärke (cd); iB = Farbe (linear, Leuchtdichte 1), Durchmesser (m).
// Rauch  (uMode 1): iA = Ort, Halbmesser (m); iB = beleuchtete Strahldichte (cd/m², linear) und Deckung.
// Spiegelung (uMirror 1): y an der Wasserfläche gespiegelt, alles unter dem Wasser abgeschnitten.
layout(location = 0) in vec4 iA;
layout(location = 1) in vec4 iB;
uniform mat4 uViewProj;
uniform vec3 uEye;
uniform int uMode;
uniform int uMirror;
uniform float uWaterY;
uniform float uPxPerRad;   // Bildpunkte je Bogenmaß (Bildhöhe / (2 tan(fov/2)))
uniform float uMinPx;      // kleinster Halbmesser eines Sterns in Bildpunkten
out vec2 vQ;
out vec4 vColor;
out float gl_ClipDistance[1];
void main() {
    const vec2 C[6] = vec2[](vec2(-1,-1), vec2(1,-1), vec2(1,1), vec2(-1,-1), vec2(1,1), vec2(-1,1));
    vec2 c = C[gl_VertexID % 6];
    vec3 p = iA.xyz;
    gl_ClipDistance[0] = uMirror == 1 ? p.y - uWaterY : 1.0;
    if (uMirror == 1) p.y = 2.0 * uWaterY - p.y;
    vec3 v = p - uEye;
    float d = max(length(v), 0.1);
    vec3 f = v / d;
    vec3 r = normalize(cross(abs(f.y) > 0.99 ? vec3(1,0,0) : vec3(0,1,0), f));
    vec3 u = cross(f, r);
    float ext;
    if (uMode == 0) {
        // Gauß mit σ = sichtbarer Halbmesser; die Fläche 2πσ² trägt die ganze Beleuchtungsstärke I/d²
        float sigma = max(0.5 * iB.w / d, uMinPx / uPxPerRad);
        float L = iA.w / (d * d) / (6.2831853 * sigma * sigma);
        vColor = vec4(iB.rgb * L, 1.0);
        ext = 3.0 * sigma * d;
    } else {
        vColor = vec4(iB.rgb * iB.a, iB.a);   // vormultipliziert
        ext = iA.w;
    }
    vQ = c * (uMode == 0 ? 3.0 : 1.0);
    gl_Position = uViewProj * vec4(p + (r * c.x + u * c.y) * ext, 1.0);
}
