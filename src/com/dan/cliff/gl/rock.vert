// rock.vert — com.dan.cliff: Fels als Netz (Überhänge, die Wand der Vorführung). Ecken mit Lage und Normale in der
// Welt; mit uDisplace = 1 verschiebt die Feinform die Ecken entlang der Normale (cliff.glsl).
#define CLIFF_VERTEX
#include "cliff.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
uniform mat4 uVP;
uniform vec3 uEye;
uniform int uDisplace;
uniform float uPixelAngle;     // Öffnung eines Bildpunkts (rad)
uniform float uRange;          // Reichweite der Feinform (m)
out vec3 vWorld;
out vec3 vNormal;
void main() {
    vec3 p = aPos;
    float d = distance(p, uEye);
    if (uDisplace == 1) p = cliffDisplace(p, aNormal, d * uPixelAngle * 4.0, d, uRange);
    vWorld = p;
    vNormal = aNormal;
    gl_Position = uVP * vec4(p, 1.0);
}
