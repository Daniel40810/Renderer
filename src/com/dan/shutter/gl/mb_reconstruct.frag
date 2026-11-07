// mb_reconstruct.frag — Bewegungsunschärfe (com.dan.shutter): der Rekonstruktionsfilter nach McGuire u. a. (I3D 2012).
// Längs der größten Bewegung der Nachbarschaft vN sammelt jeder Bildpunkt S Punkte über ±|vN|; ein Punkt Y zählt
//  - wenn er vor X liegt und seine eigene Bewegung bis X reicht (Kegel: er streicht über X),
//  - wenn X vor ihm liegt und X' Bewegung bis Y reicht (X verschmiert über Y; der Hintergrund kommt hinter X hervor),
//  - wenn beide sich gleich bewegen (Zylinder: gleichmäßig verwischt).
// Tiefe weich verglichen. Der Mittelpunkt mit 1/|V_X|. Mit einem Zittern je Bildpunkt gegen Bänder.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uColor;
uniform sampler2D uVel;         // halbe Bewegung, Abstand (m)
uniform sampler2D uNeighbor;
uniform int uK;
uniform int uS;
uniform float uSoftZ;
float cone(float dist, float lv) { return clamp(1.0 - dist / max(lv, 1e-4), 0.0, 1.0); }
float cylinder(float dist, float lv) { return 1.0 - smoothstep(0.95 * lv, 1.05 * lv + 1e-4, dist); }
float softDepth(float za, float zb) { return clamp(1.0 - (za - zb) / (uSoftZ * max(1.0, min(za, zb))), 0.0, 1.0); }
void main() {
    ivec2 x = ivec2(gl_FragCoord.xy), sz = textureSize(uColor, 0);
    vec2 vn = texelFetch(uNeighbor, x / uK, 0).xy;
    vec3 cx = texelFetch(uColor, x, 0).rgb;
    float ln = length(vn);
    if (ln <= 0.5) { frag = vec4(cx, 1.0); return; }
    vec4 vx4 = texelFetch(uVel, x, 0);
    float lx = max(length(vx4.xy), 0.5), zx = vx4.z;
    float wsum = 1.0 / lx;
    vec3 sum = cx * wsum;
    float j = fract(sin(dot(vec2(x), vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    for (int i = 0; i < uS; i++) {
        if (i == (uS - 1) / 2) continue;
        float t = mix(-1.0, 1.0, (float(i) + j + 1.0) / float(uS + 1));
        ivec2 y = clamp(ivec2(floor(vec2(x) + 0.5 + vn * t)), ivec2(0), sz - 1);
        vec4 vy4 = texelFetch(uVel, y, 0);
        float ly = max(length(vy4.xy), 0.5), zy = vy4.z;
        float dist = length(vec2(y - x));
        float f = softDepth(zx, zy), b = softDepth(zy, zx);
        float ay = f * cone(dist, ly) + b * cone(dist, lx) + cylinder(dist, ly) * cylinder(dist, lx) * 2.0;
        wsum += ay;
        sum += ay * texelFetch(uColor, y, 0).rgb;
    }
    frag = vec4(sum / wsum, 1.0);
}
