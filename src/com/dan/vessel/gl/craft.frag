#version 450
// craft.frag — Licht und Werkstoffe des Bootsbauers (com.dan.vessel.Shipwright, seit LituyaBay1958 W8a).
// Licht: Sonne (GGX und Lambert, eigener Schatten aus der Schattenkarte, Sichtbarkeit von außen), Himmel als Halbkugel
// mit der Verdeckung der Ecke, Spiegelung des Rundumbilds (optional) mit Fresnel und Glanzverdeckung, Dunst. Maßstab
// wie com.dan.forest: Strahldichte = Albedo · (Sonne · cos + Himmel · Halbkugel).
// Werkstoffe (Nummer im Netz): 0 Rumpf (Unterwasserschiff, Wasserpass, Freibord, Schergang nach der Höhe; Plankennähte,
// Stoßfugen, Rostfahnen unter den Speigatten, Bewuchs und Schleim am Wasserpass, Schrammen, abgeplatzte Farbe, nass bis
// über die Wasserlinie), 1 Deck (Planken mit Kalfaterung, vergraut), 2 Lack, 3 Farbe, 4 Stahl (verzinkt), 5 Eisen (Rost),
// 6 Glas (eigener Durchgang), 7 Tauwerk, 8 Segeltuch (durchscheinend), 9 Lampe, 10 Schrift, 11 Dach, 12 Messing,
// 13 Holz (roh), 14 innen.
in vec3 vW, vN, vC, vL;
in vec2 vUV;
flat in int vM;
in float vAO, vEdge;
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uGroundColor, uFogColor;
uniform float uFogDist, uSunVis, uWet, uWaterline, uSeed, uExposure;
uniform vec4 uHull;            // Seitenhöhe mittschiffs, Sprung vorn, Sprung achtern, Länge
uniform vec4 uZones;           // Schergang, Wasserpass über, unter der Wasserlinie, Abnutzung
uniform vec4 uAge;             // Rost, Bewuchs, Höhe des Nassen über der Wasserlinie (m), –
uniform vec3 uTop, uSheer, uBoot, uBottom;
uniform vec3 uLamp;            // Ankerlicht an, Fahrtlichter an, Strahldichte
uniform float uMastTop;
uniform sampler2D uPano;       // Rundumbild (Azimut von Nord, Höhe), vorbelichtet; optional
uniform int uPanoOn;
uniform float uPanoGain;
uniform sampler2DShadow uShadow;
uniform mat4 uShadowVP;
uniform int uShadowOn;
uniform sampler2D uLabels;
uniform int uGlass;            // 1: Glasdurchgang (Ausgabe vormultipliziert)
uniform int uTonemap;
out vec4 frag;

const float PI = 3.14159265;

float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float h11(float x) { return fract(sin(x * 91.3458) * 47453.5453); }
float vn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 x) { return 0.5 * vn(x) + 0.25 * vn(x * 2.07 + 3.1) + 0.125 * vn(x * 4.13 + 7.7) + 0.0625 * vn(x * 8.3 + 1.3); }
// Linie der Breite w (in Einheiten von x) bei ganzzahligen x, gefiltert nach der Bildpunktgröße
float lines(float x, float w) {
    float d = abs(fract(x + 0.5) - 0.5);
    float fw = max(fwidth(x), 1e-5);
    return 1.0 - smoothstep(w * 0.5 - fw, w * 0.5 + fw, d) * 1.0;
}
float fade(float x) { return clamp(1.0 - fwidth(x) * 1.5, 0.0, 1.0); }   // Muster ausblenden, wenn es feiner als ein Bildpunkt wird

vec3 pano(vec3 d, float lod) {
    float az = atan(d.x, -d.z) / 6.28318530718 + 0.5;
    float el = asin(clamp(d.y, -1.0, 1.0)) / PI + 0.5;
    return textureLod(uPano, vec2(az, el), lod).rgb * uPanoGain;
}

float shadowAt(vec3 w, vec3 n) {
    if (uShadowOn == 0) return 1.0;
    vec3 p = w + n * 0.03 + uSunDir * 0.02;
    vec4 s = uShadowVP * vec4(p, 1.0);
    vec3 c = s.xyz / s.w * 0.5 + 0.5;
    if (any(lessThan(c.xy, vec2(0.0))) || any(greaterThan(c.xy, vec2(1.0))) || c.z > 1.0) return 1.0;
    vec2 ts = 1.0 / vec2(textureSize(uShadow, 0));
    float acc = 0.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) acc += texture(uShadow, vec3(c.xy + vec2(x, y) * ts * 1.2, c.z - 0.0008));
    return acc / 9.0;
}

float D_ggx(float nh, float a) { float a2 = a * a; float d = nh * nh * (a2 - 1.0) + 1.0; return a2 / (PI * d * d); }
float G_smith(float nv, float nl, float a) { float k = a * 0.5; return nv / (nv * (1.0 - k) + k) * nl / (nl * (1.0 - k) + k); }

void main() {
    vec3 n = normalize(vN);
    vec3 v = normalize(uCam - vW);
    if (dot(n, v) < 0.0) n = -n;          // Netze sind zweiseitig (Innenwände)
    vec3 base = vC;
    float rough = 0.55, metal = 0.0, F0 = 0.04, emit = 0.0, trans = 0.0;
    vec3 emitC = vec3(0.0);
    float ao = clamp(vAO, 0.0, 1.0);
    float wear = uZones.w;
    float seed = uSeed * 13.17;
    float wet = uWet;
    float alpha = 1.0;

    if (vM == 0) {
        // ---------------- Rumpf: Zonen nach der Höhe, Planken nach dem Abstand unter der Deckskante (vUV.y)
        float y = vL.y, s = vUV.x, below = vUV.y;
        float wl = uWaterline;
        float j = fbm(vec2(s * 1.7, 3.0 + seed)) * 0.03;              // Pinsel: der Wasserpass ist von Hand gezogen
        if (y < wl - uZones.z + j) { base = uBottom; rough = 0.75; }
        else if (y < wl + uZones.y + j) { base = uBoot; rough = 0.5; }
        else if (below > 0.0 && below < uZones.x) base = uSheer;
        else base = uTop;
        // Plankennähte (16 cm) und Stoßfugen (versetzt alle 3,6 m)
        float pk = below / 0.16;
        float plank = floor(pk);
        float seam = lines(pk, 0.06) * fade(pk);
        float butt = lines((s + h11(plank + seed) * 3.6) / 3.6, 0.004) * fade(s / 3.6 * 40.0) * step(0.0, below);
        base *= 1.0 - 0.32 * max(seam, butt * 0.8);
        // Farbe leicht wolkig, Pinselstriche längs
        base *= 0.93 + 0.10 * fbm(vec2(s * 2.0, below * 14.0) + seed);
        // Rostfahnen unter den Speigatten (alle 0,9 m wie die Verschanzung) und unter den Püttingen [G]
        float cell = floor((s + 50.0) / 0.9);
        float sc = (cell + 0.5) * 0.9 - 50.0 + (h11(cell + seed) - 0.5) * 0.2;
        float dx = abs(s - sc);
        float len = 0.35 + 0.9 * h11(cell * 3.1 + seed);
        float streak = (1.0 - smoothstep(0.012, 0.05 + 0.02 * h11(cell), dx)) * (1.0 - smoothstep(0.0, len, below)) * step(-0.02, below);
        streak *= step(0.35, h11(cell * 7.3 + seed)) * (0.6 + 0.4 * vn(vec2(s * 40.0, below * 6.0)));
        vec3 rustC = vec3(0.21, 0.075, 0.025);
        base = mix(base, rustC, clamp(streak * uAge.x * 1.4, 0.0, 0.85));
        // Bewuchs am Wasserpass: grünbrauner Saum, darüber eine Schmutzlinie [G]
        float gz = 1.0 - smoothstep(0.0, 0.08 + 0.05 * vn(vec2(s * 3.0, 1.0)), abs(y - (wl + 0.01)));
        float g = gz * uAge.y * (0.55 + 0.45 * fbm(vec2(s * 5.0, y * 30.0)));
        base = mix(base, vec3(0.045, 0.06, 0.02), clamp(g, 0.0, 0.9));
        float scum = (1.0 - smoothstep(0.0, 0.025, abs(y - (wl + 0.11 + 0.03 * vn(vec2(s * 2.0, 0.0)))))) * 0.5 * uAge.y;
        base = mix(base, vec3(0.16, 0.14, 0.10), scum);
        // Schrammen längs (Fender, Eis, Anlegen) im Freibord und abgeplatzte Farbe an Kanten
        float scr = smoothstep(0.82, 0.95, vn(vec2(s * 0.6, below * 45.0) + 11.0 + seed)) * step(wl + 0.2, y) * step(0.05, below);
        float chip = smoothstep(0.70, 0.76, fbm(vec2(s * 9.0, below * 9.0) + seed * 2.0) + vEdge * 0.35) * wear;
        base = mix(base, vec3(0.23, 0.20, 0.16), clamp(scr * wear * 0.8 + chip, 0.0, 1.0));
        rough = mix(rough, 0.6, chip);
        // nass bis über die Wasserlinie (Spritzer, Schwell), unten glatter Wasserfilm
        float wz = 1.0 - smoothstep(0.0, max(0.05, uAge.z), y - wl);
        wet = max(wet, wz);
    } else if (vM == 1) {
        // ---------------- Deck: Planken 11 cm längs, Kalfaterung, Stoßfugen versetzt, vergraut, Maserung
        float q = vUV.y, s = vUV.x;
        float pq = q / 0.11;
        float row = floor(pq);
        float seam = lines(pq, 0.08) * fade(pq);
        float butt = lines((s + h11(row + seed) * 4.2) / 4.2, 0.006) * fade(s / 4.2 * 30.0);
        float grain = fbm(vec2(s * 1.2, (pq - row) * 6.0 + row * 7.0)) * 0.6 + 0.4 * vn(vec2(s * 22.0, pq * 3.0 + row));
        base = vC * (0.78 + 0.34 * grain) * (0.92 + 0.16 * h11(row * 1.7 + seed));
        base *= 1.0 - 0.55 * max(seam, butt);
        rough = 0.72;
        // dunklere, nasse Wege an den Speigatten und um die Luke [G]
        wet = max(wet, 0.25 * smoothstep(0.55, 0.8, fbm(vec2(s * 0.8, q * 1.5) + 5.0)));
    } else if (vM == 2 || vM == 13 || vM == 14) {
        // ---------------- Holz: Maserung längs der größten Ausdehnung, Lack glänzt, rohes Holz vergraut
        vec3 a = abs(n);
        vec2 p = a.y > 0.7 ? vL.xz : (a.x > a.z ? vL.zy : vL.xy);
        float axis = p.x;
        float rings = sin((p.y * 40.0 + fbm(vec2(axis * 0.8, p.y * 3.0)) * 6.0) * 3.0) * 0.5 + 0.5;
        base = vC * (0.80 + 0.30 * rings * fade(p.y * 40.0)) * (0.9 + 0.2 * vn(vec2(axis * 3.0, p.y * 8.0)));
        if (vM == 2) { rough = 0.22; F0 = 0.05; float worn = smoothstep(0.55, 0.75, vEdge * 0.6 + fbm(p * 6.0 + seed) * 0.6) * wear;
                       base = mix(base, vec3(0.20, 0.17, 0.13), worn); rough = mix(rough, 0.7, worn); }
        else if (vM == 13) { rough = 0.75; base = mix(base, vec3(0.30, 0.28, 0.25), 0.35 * wear); }
        else { rough = 0.4; base *= 0.6; }
    } else if (vM == 3 || vM == 11) {
        // ---------------- Farbe (Aufbauten, Leisten), Dach mit Segeltuch unter der Farbe
        vec3 a = abs(n);
        vec2 p = a.y > 0.7 ? vL.xz : (a.x > a.z ? vL.zy : vL.xy);
        base = vC * (0.94 + 0.08 * fbm(p * vec2(3.0, 18.0) + seed));
        rough = 0.42;
        float chip = smoothstep(0.72, 0.78, fbm(p * 11.0 + seed * 3.0) + vEdge * 0.40) * wear;
        base = mix(base, vec3(0.24, 0.21, 0.17), chip);
        // Laufspuren unter Kanten (Regenwasser, Ruß vom Schornstein) [G]
        float run = smoothstep(0.6, 0.9, vn(vec2(p.x * 25.0, p.y * 1.5) + seed)) * 0.15;
        base *= 1.0 - run;
        if (vM == 11) {
            float weave = (sin(vL.x * 900.0) * sin(vL.z * 900.0)) * 0.5 + 0.5;
            base *= 0.9 + 0.1 * weave * fade(vL.x * 140.0);
            rough = 0.78;
        }
    } else if (vM == 4) {
        // ---------------- Stahl, verzinkt: Flitter, stumpf
        base = vC * (0.85 + 0.3 * vn(vL.xz * 60.0 + vL.y * 30.0));
        metal = 0.6; rough = 0.38;
        base = mix(base, vec3(0.18, 0.08, 0.03), smoothstep(0.7, 0.85, fbm(vL.xy * 9.0 + vL.zz * 7.0)) * uAge.x);
    } else if (vM == 5) {
        // ---------------- Eisen: dunkel, Rostflecken
        float r = smoothstep(0.45, 0.8, fbm(vL.xy * 7.0 + vL.zx * 5.0 + seed));
        base = mix(vC, vec3(0.22, 0.08, 0.025), r * (0.4 + 0.6 * uAge.x));
        rough = mix(0.55, 0.9, r);
    } else if (vM == 7) {
        // ---------------- Tauwerk: Schläge (dreischäftig), rau
        float tw = sin((vUV.x * 3.0 + vUV.y * 18.0) * 6.2831) * 0.5 + 0.5;
        base = vC * (0.75 + 0.35 * tw * fade(vUV.y * 18.0));
        rough = 0.9;
    } else if (vM == 8) {
        // ---------------- Segeltuch: Gewebe, Nähte der Bahnen, durchscheinend gegen die Sonne
        float seams = lines(vL.y / 0.6, 0.02) * fade(vL.y / 0.6 * 20.0);
        base = vC * (0.92 + 0.08 * fbm(vL.xy * 20.0)) * (1.0 - 0.25 * seams);
        rough = 0.85; trans = 0.25;
    } else if (vM == 9) {
        // ---------------- Lampe: Linse; leuchtet, wenn an
        bool white = vC.r > 0.6 && vC.g > 0.6;
        bool anchor = white && vL.y > uMastTop - 1.0;
        float on = anchor ? uLamp.x : uLamp.y;
        base = vC * 0.3; rough = 0.1; F0 = 0.05;
        emitC = vC * uLamp.z * on;
    } else if (vM == 10) {
        // ---------------- Schrift: aus der Schrifttextur, sonst nichts
        float a = texture(uLabels, vUV).r;
        if (a < 0.45) discard;
        base = vC; rough = 0.45;
        float chip = smoothstep(0.66, 0.74, fbm(vUV * vec2(40.0, 8.0) + seed)) * wear;
        if (chip > 0.5) discard;
    } else if (vM == 12) {
        // ---------------- Messing/Bronze: angelaufen, an Griffstellen blank
        metal = 1.0;
        float tarn = smoothstep(0.3, 0.8, fbm(vL.xy * 14.0 + vL.zx * 9.0));
        base = mix(vec3(0.60, 0.42, 0.16), vec3(0.16, 0.14, 0.07), tarn * 0.75);
        rough = mix(0.25, 0.55, tarn);
    }

    // Nässe: dunkler (Wasser füllt die Poren), glatter; Lampe und Glas ausgenommen [Lekner und Dorf 1988, vereinfacht]
    if (vM != 9 && vM != 6) {
        float wv = clamp(wet, 0.0, 1.0);
        base *= mix(1.0, 0.55, wv * (1.0 - metal));
        rough = mix(rough, 0.08, wv * 0.85);
    }

    // ---------------- Licht
    vec3 l = normalize(uSunDir);
    float nl = max(dot(n, l), 0.0), nv = max(dot(n, v), 1e-3);
    float sh = shadowAt(vW, n) * uSunVis;
    vec3 hemi = mix(uGroundColor, uSkyColor, 0.5 + 0.5 * n.y);
    vec3 diffC = base * (1.0 - metal);
    vec3 f0 = mix(vec3(F0), base, metal);
    vec3 col = diffC * (uSunColor * nl * sh + hemi * ao);
    // Durchscheinen (Segel) von der Rückseite
    col += base * trans * uSunColor * max(dot(-n, l), 0.0) * sh;
    // Sonnenglanz (GGX), im Maßstab Albedo · E/π: BRDF · π · Sonne · cos
    float a = max(rough * rough, 0.002);
    vec3 hv = normalize(l + v);
    float nh = max(dot(n, hv), 0.0), vh = max(dot(v, hv), 0.0);
    vec3 F = f0 + (1.0 - f0) * pow(1.0 - vh, 5.0);
    vec3 spec = D_ggx(nh, a) * G_smith(nv, nl, a) * F / max(4.0 * nv * max(nl, 1e-3), 1e-4);
    col += spec * PI * uSunColor * nl * sh;
    // Spiegelung der Umgebung: Rundumbild (oder Himmelsfarbe) mit Fresnel nach Schlick-Rauigkeit und Glanzverdeckung
    vec3 r = reflect(-v, n);
    vec3 Fe = f0 + (max(vec3(1.0 - rough), f0) - f0) * pow(1.0 - nv, 5.0);
    float so = clamp(pow(nv + ao, exp2(-16.0 * rough - 1.0)) - 1.0 + ao, 0.0, 1.0);
    vec3 env = uPanoOn == 1 ? pano(r.y < 0.0 ? vec3(r.x, -r.y * 0.3, r.z) : r, rough * 6.0) * (r.y < 0.0 ? 0.35 : 1.0) : mix(uGroundColor, uSkyColor * 1.3, smoothstep(-0.2, 0.3, r.y));
    col += env * Fe * so * (1.0 - 0.6 * (1.0 - metal) * rough);
    col += emitC;

    if (uGlass == 1) {
        // ---------------- Glas: Spiegelung nach Fresnel, dahinter das (dunkle) Innere; Schmutz und Salz am Rand [G]
        float Fg = 0.04 + 0.96 * pow(1.0 - nv, 5.0);
        vec3 envg = uPanoOn == 1 ? pano(r.y < 0.0 ? vec3(r.x, -r.y * 0.3, r.z) : r, 0.0) * (r.y < 0.0 ? 0.3 : 1.0) : uSkyColor * 1.2;
        vec3 sp = D_ggx(nh, 0.01) * 0.25 * Fg * PI * uSunColor * nl * sh;
        float salt = 0.12 + 0.2 * fbm(vW.xy * 6.0 + vW.zz * 4.0);
        vec3 cg = envg * Fg + sp + uSkyColor * salt * 0.15 * ao;
        float al = clamp(Fg + salt * 0.25 + 0.18, 0.0, 1.0);
        float dist = length(uCam - vW);
        float fogf = 1.0 - exp(-dist / uFogDist);
        cg = mix(cg, uFogColor * al, fogf);
        frag = vec4(cg, al);
        return;
    }

    float dist = length(uCam - vW);
    col = mix(col, uFogColor, 1.0 - exp(-dist / uFogDist));
    if (uTonemap == 1) {
        vec3 x = col * uExposure;
        col = pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2));
    }
    frag = vec4(col, alpha);
}
