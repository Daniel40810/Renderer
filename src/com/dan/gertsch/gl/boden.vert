// boden.vert — der Boden als ein großes Viereck in y = 0 (das Licht rechnet boden.frag je Bildpunkt). Seit LituyaBay1958
// W10 mit Gelände: ein Gitter aus (nx − 1) · (nz − 1) Zellen zu zwei Dreiecken über der Höhentextur (R32F, nx × nz),
// die Höhe an den Punkten genau, dazwischen linear wie Gelaende.hoehe; die Normale aus zentralen Differenzen.
uniform mat4 uVP;
uniform vec4 uBodenRect;   // x0, z0, x1, z1
uniform int uGelaende;     // 0: Ebene, 1: Gitter
uniform sampler2D uHoehe;
uniform vec3 uGelaendeUrsprung;   // x0, z0, Zelle
uniform ivec2 uGelaendeN;         // nx, nz
uniform int uWasserAn;            // seit W10b: Wasserspiegel in uWasser (die Fläche liegt nicht tiefer)
uniform float uWasser;
out vec3 vP;
out vec3 vN;
out float vNass;                  // > 0 unter dem Wasserspiegel (Tiefe, m)

float hoehe(ivec2 q) { return texelFetch(uHoehe, clamp(q, ivec2(0), uGelaendeN - 1), 0).r; }

void main() {
    if (uGelaende == 0) {
        vec2 q = vec2((gl_VertexID & 1) == 0 ? uBodenRect.x : uBodenRect.z, (gl_VertexID & 2) == 0 ? uBodenRect.y : uBodenRect.w);
        vP = vec3(q.x, 0.0, q.y);
        vN = vec3(0.0, 1.0, 0.0);
        vNass = -1.0;
    } else {
        // je Zelle sechs Ecken: (0,0) (1,0) (0,1) · (1,0) (1,1) (0,1)
        int zelle = gl_VertexID / 6, ecke = gl_VertexID % 6;
        int zx = zelle % (uGelaendeN.x - 1), zz = zelle / (uGelaendeN.x - 1);
        ivec2 d = ecke == 0 ? ivec2(0, 0) : ecke == 1 || ecke == 3 ? ivec2(1, 0) : ecke == 4 ? ivec2(1, 1) : ivec2(0, 1);
        ivec2 q = ivec2(zx, zz) + d;
        float s = uGelaendeUrsprung.z;
        vP = vec3(uGelaendeUrsprung.x + float(q.x) * s, hoehe(q), uGelaendeUrsprung.y + float(q.y) * s);
        float dx = (hoehe(q + ivec2(1, 0)) - hoehe(q - ivec2(1, 0))) / (2.0 * s), dz = (hoehe(q + ivec2(0, 1)) - hoehe(q - ivec2(0, 1))) / (2.0 * s);
        vN = normalize(vec3(-dx, 1.0, -dz));
        vNass = uWasserAn == 1 ? uWasser - vP.y : -1.0;
        if (vNass > 0.0) vP.y = uWasser;
    }
    gl_Position = uVP * vec4(vP, 1.0);
}
