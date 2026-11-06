// floe.frag — Licht auf dem Eis: oben Schnee und altes Eis, an den Seiten blaues Gletschereis, an der Wasserlinie nass,
// gegen die Sonne blau durchscheinend; Dunst nach Entfernung. Im Schatten-Lauf nur der Faktor zum Multiplizieren.
in vec3 vWorld, vNrm;
in float vPart, vTint, vLocalY;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor;
uniform vec4 uFog;
uniform int uShadow;
uniform float uShadowK;

float h3(vec3 p) { return fract(sin(dot(floor(p), vec3(127.1, 311.7, 74.7))) * 43758.5453); }

void main() {
    if (uShadow == 1) {
        float dist = length(vWorld - uCam);
        float k = uShadowK * exp(-dist / max(uFog.w, 1.0));
        frag = vec4(vec3(1.0 - k), 1.0);
        return;
    }
    vec3 n = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    if (dot(n, V) < 0.0) n = -n;
    float t = vTint;
    vec3 schnee = mix(vec3(0.78, 0.83, 0.88), vec3(0.88, 0.91, 0.94), t);
    vec3 blau = mix(vec3(0.56, 0.66, 0.76), vec3(0.72, 0.79, 0.86), t * t);   // W6 Runde 3: weniger gesättigt (wirkte mintgrün)
    float rau = 0.88 + 0.24 * h3(vWorld * 2.3);
    vec3 alb;
    if (vPart < 0.5) alb = schnee;
    else if (vPart < 1.5) alb = mix(schnee, blau, 0.25);
    else {
        alb = mix(blau, schnee, smoothstep(0.35, 0.8, vLocalY) * 0.5);
        alb = mix(vec3(0.10, 0.20, 0.26), alb, smoothstep(0.0, 0.18, vLocalY));   // nass an der Wasserlinie
    }
    alb *= rau;
    float nl = dot(n, uSunDir);
    vec3 col = alb * (uSunColor * max(nl, 0.0) + uSkyColor * (0.55 + 0.45 * n.y));
    // gegen die Sonne: blau durchscheinend an Kanten und Seiten [G]
    // W6 Runde 3: mit der Helligkeit der Sonne, nicht ihrer Farbe (blaugrün mal Abendrot gab Mintgrün)
    if (vPart > 0.5) col += vec3(0.10, 0.26, 0.45) * dot(uSunColor, vec3(0.3, 0.5, 0.2)) * max(-nl, 0.0) * 0.2 * max(dot(-V, uSunDir), 0.0);
    float dist = length(vWorld - uCam);
    col = mix(uFog.rgb, col, exp(-dist / max(uFog.w, 1.0)));
    frag = vec4(col, 1.0);
}
