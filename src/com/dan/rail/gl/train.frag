#version 430 core
// train.frag — Licht wie im Gleis: Sonne als Bestrahlung (vorbelichtet), Himmel als Bestrahlung/π, Dunst. Lack glänzt
// zur Sonne, Glas spiegelt den Himmel, die Lampen der Lok leuchten in Fahrtrichtung (uLicht).
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in float vMat;
in float vFront;
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uFogColor;
uniform float uFogDist;
uniform vec2 uSnowWet;
uniform float uLicht;                   // Lok: +1 fährt zu wachsendem s (vorn = Schornstein), −1 umgekehrt; 0 Wagen
out vec4 frag;

void main() {
    vec3 n = normalize(vNormal);
    vec3 V = normalize(uCam - vWorld);
    if (dot(n, V) < 0.0) n = -n;
    vec3 alb = vColor * (1.0 - 0.25 * uSnowWet.y);
    float sn = uSnowWet.x * smoothstep(0.6, 0.95, n.y) * 0.7 * step(vMat, 1.5);
    alb = mix(alb, vec3(0.80, 0.83, 0.88), sn);
    float diff = max(dot(n, uSunDir), 0.0);
    vec3 col = alb * (uSunColor * diff / 3.14159265 + uSkyColor * (0.55 + 0.45 * n.y));
    vec3 H = normalize(uSunDir + V);
    float lack = step(0.5, vMat) * step(vMat, 1.5) * (1.0 - sn);
    col += uSunColor * lack * (0.04 + 0.1 * uSnowWet.y) * pow(max(dot(n, H), 0.0), 60.0);
    if (vMat > 1.5 && vMat < 2.5) {
        // Glas: dunkel, spiegelt den Himmel nach Fresnel
        float f = 0.04 + 0.96 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
        col = mix(col, uSkyColor * 3.14159265 * 0.8, f) + uSunColor * 0.5 * pow(max(dot(n, H), 0.0), 200.0);
    }
    if (vMat > 2.5) {
        bool vorn = uLicht * vFront > 0.0;
        col = uLicht != 0.0 && vorn ? vec3(2.0, 1.72, 1.2) : vec3(0.04);
    }
    float d = length(uCam - vWorld);
    col = mix(col, uFogColor, 1.0 - exp(-d / max(uFogDist, 1.0)));
    frag = vec4(col, 1.0);
}
