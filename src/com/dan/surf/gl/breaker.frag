// breaker.frag — com.dan.surf: Wasser der Brandung und Sand. Wasser: Spiegelung des Himmels nach Fresnel, Sonne als
// Glanz, Farbe des Wasserkörpers nach der Tiefe (im Flachen scheint der Sand durch), Licht durch die dünne Lippe
// (grünlich, gegen die Sonne gesehen am stärksten), Schaum weiß und in Flocken zerrissen (Rauschen am Ort, das mit der
// Zeit wandert). Sand: nass unterhalb der höchsten Wasserlinie dunkler. Ausgabe linear und vorbelichtet; mit
// uDisplay > 0 gleich mit Filmkurve und Gamma (Vorführung ohne Nachbearbeitung).
in vec3 vWorld;
in vec3 vNormal;
in vec4 vAttr;
out vec4 frag;
uniform vec3 uEye, uSunDir, uSunColor, uSkyTop, uSkyHorizon, uFogColor;
uniform vec3 uWaterDeep, uWaterShallow, uSand;
uniform float uFogDist, uTime, uDisplay, uWetLine;
uniform vec3 uGlow;   // seit Sylt Phase 13: Meeresleuchten (vorbelichtet), 0 = aus
uniform int uMode;   // 0 Wasser, 1 Sand (Wasserlinie), 2 Sand (Nässe je Ecke)
// seit Sylt Phase 13: Spuren im Sand (R8 über uTrackRect in der Szene; das Netz liegt mit uTrackO und uTrackAxes darin)
uniform sampler2D uTracks;
uniform vec4 uTrackRect, uTrackAxes;
uniform vec2 uTrackO;

float trackAt(vec3 w) {
    if (uTrackRect.w <= 0.0) return 0.0;
    vec2 s = uTrackO + w.x * uTrackAxes.xy + w.z * uTrackAxes.zw;
    vec2 uv = (s - uTrackRect.xy) / uTrackRect.z;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    return textureLod(uTracks, uv, 0.0).r;
}

float bHash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float bNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(bHash(i), bHash(i + vec2(1, 0)), f.x), mix(bHash(i + vec2(0, 1)), bHash(i + vec2(1, 1)), f.x), f.y);
}
vec3 skyAt(vec3 d) {
    float t = sqrt(clamp(d.y, 0.0, 1.0));
    return mix(uSkyHorizon, uSkyTop, t);
}

void main() {
    vec3 n = normalize(vNormal);
    vec3 V = normalize(uEye - vWorld);
    if (!gl_FrontFacing) n = -n;
    float dist = distance(uEye, vWorld);
    float sunN = max(dot(n, uSunDir), 0.0);
    vec3 amb = uSkyHorizon * 0.55 + uSkyTop * 0.45;
    vec3 col;
    if (uMode == 1) {
        float wet = 1.0 - smoothstep(uWetLine - 0.05, uWetLine + 0.25, vWorld.y);
        float g = 0.85 + 0.3 * bNoise(vWorld.xz * 3.0);
        vec3 s = uSand * g * mix(1.0, 0.55, wet);
        col = s * (uSunColor * sunN / 3.14159265 + amb * (0.6 + 0.4 * n.y));
    } else if (uMode == 2) {
        float moist = clamp(vAttr.x, 0.0, 1.0), film = clamp(vAttr.y, 0.0, 1.0), bub = vAttr.z, wrack = vAttr.w;
        float g = 0.85 + 0.3 * bNoise(vWorld.xz * 3.0);
        vec3 s = uSand * g * mix(1.0, 0.52, smoothstep(0.05, 0.9, moist));
        // Tang (dunkel, grünbraun) und Schill (hell) im Spülsaum, in Flecken
        float wn = bNoise(vWorld.xz * 2.2 + 7.0) * 0.6 + bNoise(vWorld.xz * 9.0) * 0.4;
        float tang = smoothstep(0.55, 0.75, wrack * (0.4 + wn));
        s = mix(s, vec3(0.10, 0.09, 0.05), tang * 0.85);
        float schill = step(0.86, bHash(floor(vWorld.xz * 14.0))) * smoothstep(0.15, 0.5, wrack);
        s = mix(s, vec3(0.80, 0.78, 0.72), schill * 0.7);
        // seit Sylt Phase 13: Tritte der Vögel – die Mulde dunkler (Wasser steht darin), der Rand etwas heller
        float tr = trackAt(vWorld);
        s *= 1.0 - 0.45 * tr * smoothstep(0.1, 0.6, moist);
        col = s * (uSunColor * sunN / 3.14159265 + amb * (0.6 + 0.4 * n.y));
        // der Film: glatter Spiegel des Himmels mit Sonnenglanz
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
        vec3 R = reflect(-V, n);
        vec3 refl = skyAt(R) + uSunColor * pow(max(dot(R, uSunDir), 0.0), 600.0) * 0.8;
        col = mix(col, refl, film * F * 0.9);
        // Blasen: weiße, glänzende Pünktchen, die vergehen
        float b = step(1.0 - 0.35 * clamp(bub, 0.0, 1.0), bHash(floor(vWorld.xz * 22.0)));
        col = mix(col, vec3(0.9) * (uSunColor * 0.35 + amb), b * 0.8);
        // seit Sylt Phase 13: Meeresleuchten in den Blasen des Schwalls (funkelnd, ein Drittel des Kamms)
        if (uGlow.g > 0.0) {
            float sp = step(0.82, bHash(floor(vWorld.xz * 30.0) + floor(uTime * 6.0)));
            col += uGlow * clamp(bub, 0.0, 1.0) * (0.15 + 0.85 * sp) * 0.35;
        }
    } else {
        float foam = vAttr.x, thin = vAttr.y, depth = vAttr.z;
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
        vec3 R = reflect(-V, n);
        vec3 refl = skyAt(R) + uSunColor * pow(max(dot(R, uSunDir), 0.0), 400.0) * 0.6;
        // Wasserkörper: im Flachen der Sand, tiefer Türkis bis Grünblau
        float clear = exp(-depth * 0.9);
        vec3 lightIn = uSunColor * (0.25 + 0.75 * sunN) / 3.14159265 + amb * 0.8;
        vec3 body = mix(uWaterDeep, uWaterShallow, clamp(1.0 - depth / 3.0, 0.0, 1.0)) * lightIn;
        body = mix(body, uSand * 0.8 * lightIn, clear * 0.7);
        // die dünne Lippe: Licht scheint durch (grünlich), am meisten gegen die Sonne gesehen
        float back = pow(max(dot(-V, uSunDir), 0.0), 2.0);
        body += vec3(0.10, 0.42, 0.34) * uSunColor * thin * (0.15 + 0.85 * back) * 0.45;
        col = mix(body, refl, F);
        // Schaum in Flocken
        float nz = 0.55 * bNoise(vWorld.xz * 1.3 + vec2(uTime * 0.05, 0.0)) + 0.45 * bNoise(vWorld.xz * 5.0 + vec2(0.0, uTime * 0.11) + vWorld.y * 2.0);
        float fe = smoothstep(0.30, 0.70, foam * (0.6 + 0.8 * nz));
        vec3 foamCol = vec3(0.86, 0.88, 0.88) * (uSunColor * (0.3 + 0.7 * sunN) / 3.14159265 + amb);
        col = mix(col, foamCol, fe);
        // seit Sylt Phase 13: Meeresleuchten im stürzenden Kamm und im frischen Schaum, flackernd
        if (uGlow.g > 0.0) {
            float fl = 0.55 + 0.45 * bNoise(vWorld.xz * 4.0 + vec2(uTime * 2.3, -uTime * 1.7));
            col += uGlow * clamp(foam * 1.3 + thin * 0.8, 0.0, 1.0) * fl;
        }
    }
    col = mix(col, uFogColor, 1.0 - exp(-dist / max(1.0, uFogDist)));
    if (uDisplay > 0.0) col = pow(1.0 - exp(-col * uDisplay), vec3(1.0 / 2.2));
    frag = vec4(col, 1.0);
}
