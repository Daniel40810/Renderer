#version 430 core
// rail.frag — Licht wie in den anderen Packages: Sonne als Bestrahlung (vorbelichtet), Himmel als Bestrahlung/π,
// Dunst zur Ferne. Der Kopf der Schiene glänzt; Schnee legt sich auf alles, was nach oben schaut; nass wird es dunkler.
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in float vKind;
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uFogColor;
uniform float uFogDist;
uniform vec2 uSnowWet;
out vec4 frag;

void main() {
    vec3 n = normalize(vNormal);
    vec3 V = normalize(uCam - vWorld);
    if (dot(n, V) < 0.0) n = -n;
    vec3 alb = vColor * (1.0 - 0.3 * uSnowWet.y);
    // Schnee: oben liegend, auf Schotter und Schwellen; die Schienenköpfe fahren die Züge frei (nur zum Teil bedeckt)
    float kopf = step(0.35, vColor.r) * step(vKind, 0.5);
    float sn = uSnowWet.x * smoothstep(0.4, 0.9, n.y) * (1.0 - 0.7 * kopf);
    alb = mix(alb, vec3(0.80, 0.83, 0.88), sn);
    float diff = max(dot(n, uSunDir), 0.0);
    vec3 col = alb * (uSunColor * diff / 3.14159265 + uSkyColor * (0.6 + 0.4 * n.y));
    // blanker Schienenkopf: Glanz zur Sonne
    vec3 H = normalize(uSunDir + V);
    col += uSunColor * kopf * (1.0 - sn) * 0.25 * pow(max(dot(n, H), 0.0), 40.0);
    float d = length(uCam - vWorld);
    col = mix(col, uFogColor, 1.0 - exp(-d / max(uFogDist, 1.0)));
    frag = vec4(col, 1.0);
}
