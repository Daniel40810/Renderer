// halm.vert — ein Halm als Band aus vier Abschnitten (zehn Ecken), zum Blick gedreht, spitz zulaufend; im Wind neigt
// sich die Spitze mit dem Quadrat der Höhe (die Auslenkung eines Zweigs in seiner Höhe, doppelt für den weichen Halm).
layout(std430, binding = 6) readonly buffer Halme { vec4 hd[]; };   // je Halm 5 × (Punkt, w): Breite, r, g, b, Phase

uniform mat4 uVP;
uniform vec3 uVorn;
uniform float uFokus;

out vec3 vP;
flat out vec3 vAlbedo;

vec3 punkt(int hi, int k) {
    vec3 p = hd[5 * hi + k].xyz;
    if (uWind.x > 0.0) {
        float t = float(k) / 4.0;
        p.xz += 2.0 * windVersatz(vec3(hd[5 * hi].x, hd[5 * hi + 4].y, hd[5 * hi].z), hd[5 * hi + 4].w) * t * t;
    }
    return p;
}

void main() {
    int hi = gl_InstanceID, k = gl_VertexID >> 1;
    float seite = (gl_VertexID & 1) == 0 ? -1.0 : 1.0;
    vec3 p = punkt(hi, k), richtung = k < 4 ? punkt(hi, k + 1) - p : p - punkt(hi, k - 1);
    richtung = normalize(richtung);
    vec3 quer = cross(richtung, normalize(uAuge - p));
    quer = length(quer) < 1e-5 ? vec3(1.0, 0.0, 0.0) : normalize(quer);
    float breite = hd[5 * hi].w * (1.0 - float(k) / 4.0) * 0.5;
    breite = max(breite, k < 4 ? 0.35 * max(0.3, dot(p - uAuge, uVorn)) / uFokus : 0.0);
    vP = p;
    vAlbedo = vec3(hd[5 * hi + 1].w, hd[5 * hi + 2].w, hd[5 * hi + 3].w);
    gl_Position = uVP * vec4(p + quer * breite * seite, 1.0);
}
