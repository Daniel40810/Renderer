// fleck.vert — ein Fleck je Eintrag der Liste (aus auswahl.comp): das Blatt aus dem seed seines Zweigs, im Wind, nach
// der Größenregel vergrößert (√k für k zusammengefasste Blätter, mindestens uZiel), als Viereck in seiner Blattebene.
layout(std430, binding = 0) readonly buffer Zweige { Zweig zweig[]; };
layout(std430, binding = 1) readonly buffer Liste { uvec4 eintrag[]; };
layout(std430, binding = 7) readonly buffer ListeSonne { uvec2 sonnenEintrag[]; };

uniform int uModus;            // 0 Ansicht, 1 Sonne (nur Tiefe)
uniform mat4 uVP;
uniform vec3 uVorn;
uniform float uFokus, uZiel;

out vec2 vST;
flat out vec3 vFarbe;
flat out float vY;

void main() {
    uvec4 e = uModus == 0 ? eintrag[gl_InstanceID] : uvec4(sonnenEintrag[gl_InstanceID], 0u, 0u);
    Zweig z = zweig[e.x];
    uint i = e.y & 0xFFu, kf = (e.y >> 8u) & 0xFFFu;
    Blatt b = blatt(z, i);
    imWind(b, z);
    float g = sqrt(b.laenge * b.breite);
    float blattPx = uModus == 0 ? uFokus * g / max(0.3, dot(b.p - uAuge, uVorn)) : uFokus * g;
    float sk = max(sqrt(float(kf)), uZiel / max(blattPx, 1e-6));
    vec3 quer = cross(b.n, b.a);
    vec2 st = vec2((gl_VertexID & 1) == 0 ? -1.0 : 1.0, (gl_VertexID & 2) == 0 ? -1.0 : 1.0);
    vec3 p = b.p + b.a * (0.5 * b.laenge * sk * st.y) + quer * (0.5 * b.breite * sk * st.x);
    vST = st;
    vFarbe = vec3(0.0);
    vY = 0.0;
    if (uModus == 0) {
        int band = int((e.y >> 20u) & 7u), stufe = int((e.y >> 23u) & 7u);
        bool akzent = ((e.y >> 26u) & 1u) != 0u;
        vFarbe = farbeZu(akzent ? -1 : stufe, band);
        vY = uintBitsToFloat(e.z);
    }
    gl_Position = uVP * vec4(p, 1.0);
}
