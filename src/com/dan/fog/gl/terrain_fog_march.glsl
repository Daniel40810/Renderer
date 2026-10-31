// terrain_fog_march.glsl — seit Brocken Phase 7 der Blick durch den Nebel als Funktion fogMarch(Bildpunkt), für
// terrain_fog.frag (je Texel in voller, halber oder drittel Auflösung) und terrain_fog_up.frag (Bildpunkte an Kanten,
// die keiner der vier Texel trifft). Beschreibung des Verfahrens in terrain_fog.frag.
uniform mat4 uInvVP;
uniform vec3 uCam;
uniform sampler2D uDepth;
uniform int uReversed;
uniform int uSteps;
uniform float uMaxDist, uCurv;     // Kimm (m), Absenkung je m² Abstand (1 / (2 R_eff))
uniform vec2 uViewport;            // volle Bildgröße
uniform vec4 uTfBounds;            // tiefste und höchste Stelle mit Nebel (m), —, —
uniform vec3 uSunDir, uSunCol, uMoonDir, uMoonCol, uAmbTop, uAmbHor;
uniform float uCapSun;             // Licht der Sonne unter der Decke (0..1)
uniform int uFrame;
uniform sampler2D uPhase;
uniform int uPhaseOn;
uniform sampler2D uSunH;
uniform vec4 uSunHRect;            // x0, z0, Kantenlänge, an (1) / aus (0)

float hgF(float c, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }

// Phasenfunktion auf 4π normiert (isotrop = 1)
vec3 phase(float c) {
    if (uPhaseOn == 1) return texture(uPhase, vec2(acos(clamp(c, -1.0, 1.0)) / 3.14159265, 0.5)).rgb;
    return vec3(hgF(c, 0.75) * 12.566);
}

// Entfernung des Bildpunkts (Himmel: Kimm) und sein Punkt in der Welt
float pixelDist(ivec2 px, out vec3 P) {
    vec2 uv = (vec2(px) + 0.5) / uViewport;
    float dz = texelFetch(uDepth, px, 0).r;
    bool sky = uReversed == 1 ? dz <= 0.0 : dz >= 1.0;
    float zN = sky ? (uReversed == 1 ? 1e-6 : 0.999999) : (uReversed == 1 ? dz : dz * 2.0 - 1.0);
    vec4 w = uInvVP * vec4(uv * 2.0 - 1.0, zN, 1.0);
    P = w.xyz / w.w;
    return sky ? uMaxDist : min(length(P - uCam), uMaxDist);
}

// Schatten des Geländes: 1 über der Höhe, ab der die Sonne scheint, weich über ± 6 m
float terrainSun(vec3 q) {
    if (uSunHRect.w < 0.5) return 1.0;
    vec2 m = (q.xz - uSunHRect.xy) / uSunHRect.z;
    if (m.x < 0.0 || m.y < 0.0 || m.x > 1.0 || m.y > 1.0) return 1.0;
    float h = texture(uSunH, m).g;
    return smoothstep(h - 6.0, h + 6.0, q.y);
}

// Streulicht und Durchlässigkeit für den Bildpunkt px (volle Auflösung); tEnd = seine Entfernung (Himmel: Kimm)
vec4 fogMarch(ivec2 px, out float tEnd) {
    vec3 P;
    tEnd = pixelDist(px, P);
    vec2 uv = (vec2(px) + 0.5) / uViewport;
    vec4 w1 = uInvVP * vec4(uv * 2.0 - 1.0, uReversed == 1 ? 1e-6 : 0.999999, 1.0);
    vec3 dir = normalize(w1.xyz / w1.w - uCam);
    // Platte zwischen den Grenzen; die Krümmung senkt die Szene um bis zu (uMaxDist² · uCurv), darum unten großzügig
    float y0 = uTfBounds.x - tEnd * tEnd * uCurv, y1 = uTfBounds.y;
    float t0 = 0.0, t1 = tEnd;
    if (abs(dir.y) > 1e-5) {
        float ta = (y0 - uCam.y) / dir.y, tb = (y1 - uCam.y) / dir.y;
        t0 = max(0.0, min(ta, tb));
        t1 = min(tEnd, max(ta, tb));
    } else if (uCam.y < y0 || uCam.y > y1) t1 = -1.0;
    if (t1 <= t0) return vec4(0.0, 0.0, 0.0, 1.0);
    float cs = dot(dir, uSunDir), cm = dot(dir, uMoonDir);
    // einfach gestreut (mit der Phasenfunktion, fällt im Schatten weg) und vielfach gestreut (genähert)
    vec3 sunS = uSunCol * phase(cs) * 0.3, sunM = uSunCol * 0.12;
    vec3 moonL = uMoonCol * (phase(cm) * 0.3 + 0.12);
    bool casters = uTfCastN > 0 && uSunCol.r + uSunCol.g + uSunCol.b > 0.0;
    float sunUp = max(0.25, uSunDir.y), moonUp = max(0.25, uMoonDir.y);   // flaches Licht kommt im Nebel vielfach gestreut an
    int n = max(2, uSteps);
    float span = t1 - t0;
    // 1. Suche: grob bis zum ersten Nebel (nur Dichte), dann den Eintritt halbieren – sonst stünde der Rand des Meers
    //    in Stufen (Höhenlinien) da, wo die Schritte groß sind
    float p = clamp(log(max(span / 3.0, 1.0001)) / log(float(n)), 1.0, 4.0);
    float tPrev = t0, tHit = -1.0;
    for (int i = 1; i <= n; i++) {
        float tc = t0 + span * pow(float(i) / float(n), p);
        vec3 q = uCam + dir * tc;
        vec2 dxz = q.xz - uCam.xz;
        q.y += dot(dxz, dxz) * uCurv;
        if (tfDensity(q) > 0.0) { tHit = tc; break; }
        tPrev = tc;
    }
    if (tHit < 0.0) return vec4(0.0, 0.0, 0.0, 1.0);
    for (int k = 0; k < 6; k++) {
        float tm = 0.5 * (tPrev + tHit);
        vec3 q = uCam + dir * tm;
        vec2 dxz = q.xz - uCam.xz;
        q.y += dot(dxz, dxz) * uCurv;
        if (tfDensity(q) > 0.0) tHit = tm; else tPrev = tm;
    }
    // 2. Licht: vom Eintritt an mit Schritten, die von 1,5 m an um je 35 % wachsen; der Rest bis zum Ende in einem Zug
    float T = 1.0;
    vec3 S = vec3(0.0);
    float t = tPrev, dt = 1.5;
    vec3 Llast = vec3(0.0);
    float dLast = 0.0;
    for (int i = 0; i < n; i++) {
        if (t >= t1) break;
        float tb = min(t1, t + dt);
        float tm = 0.5 * (t + tb), h = tb - t;
        vec3 q = uCam + dir * tm;
        vec2 dxz = q.xz - uCam.xz;
        q.y += dot(dxz, dxz) * uCurv;          // wahre Höhe des Punkts
        vec4 m = tfMap(q.xz);
        float top = uTfSea.z > 1e-7 ? tfSeaTop(q.xz) : 0.0;
        float dens = tfDensityMT(q, m, top);
        t = tb;
        dt *= 1.35;
        if (dens <= 0.0) continue;
        float tr = exp(-dens * h);
        // Nebel über dem Punkt: im Meer bis zur Obergrenze, im Talnebel bis zum Rand, unter der Decke dunkel
        float above = 0.0;
        if (uTfSea.z > 1e-7) above = max(above, top - q.y);
        if (m.w > 0.5 && uTfValley.x > 1e-3) above = max(above, m.y * uTfValley.x - (q.y - m.x));
        float capDark = (uTfCap.y > 1e-7 && m.w > 0.5 && m.z > 0.5 && q.y > uTfCap.x - m.z - 20.0) ? uCapSun : 1.0;
        float sT = exp(-dens * above / sunUp * 0.35) * capDark, mT = exp(-dens * above / moonUp * 0.35) * capDark;
        float ter = terrainSun(q);
        float body = casters ? tfSunShadow(q) : 1.0;
        vec3 amb = mix(uAmbHor, uAmbTop, 0.5) * (0.6 + 0.4 * exp(-dens * above * 0.3)) * mix(0.55, 1.0, capDark);
        vec3 L = (sunS * (body * ter) + sunM * mix(0.3, 1.0, ter)) * sT + moonL * mT + amb;
        S += T * (1.0 - tr) * L;
        T *= tr;
        Llast = L;
        dLast = dens;
        if (T < 0.003) { T = 0.0; break; }
    }
    // was nach den Schritten bis zum Ende bleibt, mit der letzten Dichte und dem letzten Licht (Nebelmeer: meist schon dicht)
    if (T > 0.0 && t < t1 && dLast > 0.0) {
        float tr = exp(-dLast * (t1 - t) * 0.5);
        S += T * (1.0 - tr) * Llast;
        T *= tr;
    }
    return vec4(S, T);
}
