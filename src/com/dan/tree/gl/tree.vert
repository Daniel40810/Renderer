// tree.vert — ein Asset-Baum je Instanz: Netz aus com.dan.tree.TreeMesh (Fuß bei 0, örtlich), im Wind verschoben
// wie TreeWind.apply, gedreht, skaliert und an seinen Ort gesetzt. Blätter, die zur Jahreszeit fehlen, fallen weg.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec4 aWind;     // Hauptast-Ausschlag, Zweig-Ausschlag, Zittern, Phase Hauptast
layout(location = 3) in vec3 aLook;     // Phase Zweig, Art (0 Holz, 1 Laub, 2 Hülle), Verdeckung·100 + Zufall
layout(location = 6) in vec2 aUv;       // Texturkoordinaten (Bildzeilen von oben)
layout(location = 4) in vec4 iA;        // x, y, z, Drehung (rad)
layout(location = 5) in vec4 iB;        // Maßstab, Höhe des Netzes (m), Phase des Baums, Dämpfung der Böen
uniform mat4 uVP;
uniform float uTime;
uniform vec4 uWind;                     // wohin x, z, Geschwindigkeit (m/s), Böigkeit 0..1
uniform vec3 uFlex;                     // Stamm, Äste, Zittern
uniform vec2 uVisible;                  // sichtbar: lo <= Zufall < hi
uniform vec3 uLeafLo, uLeafHi, uBark;   // linear
uniform float uSnow;
uniform vec3 uLeafRef;                  // Sommergrün: Laubtexturen werden mit Farbe / Bezug getönt
uniform ivec2 uTextured;                // Rinde, Laub mit Textur
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out float vLeaf;
out float vAo;
out vec2 vUv;
out float vTex;                         // 0 keine, 1 Rinde, 2 Laub

const float TAU = 6.2832;

void main() {
    float kind = aLook.y;
    float rand = fract(aLook.z);
    float ao = floor(aLook.z) / 100.0;
    if (kind > 0.5 && (rand < uVisible.x || rand >= uVisible.y)) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);            // ganzes Blatt fällt weg (alle Ecken gleich)
        vWorld = vec3(0.0); vNormal = vec3(0.0, 1.0, 0.0); vColor = vec3(0.0); vLeaf = 0.0; vAo = 0.0;
        vUv = vec2(0.0); vTex = 0.0;
        return;
    }
    float c = cos(iA.w), s = sin(iA.w);
    // Wind in den Baumraum
    vec2 w = vec2(uWind.x * c + uWind.y * s, -uWind.x * s + uWind.y * c);
    float bend = min(pow(uWind.z / 20.0, 2.0), 2.0);
    float sd = dot(iA.xz, uWind.xy);
    float wave = 0.6 * sin(0.7 * uTime - 0.08 * sd) + 0.4 * sin(1.9 * uTime - 0.21 * sd + 1.7);
    float gust = max(0.0, 1.0 + uWind.w * (1.0 - iB.w) * wave);
    float B = bend * gust;
    float sB = sqrt(bend) * gust;
    float t = uTime;
    vec3 p = aPos;
    vec3 d = vec3(0.0);

    float h = max(0.0, p.y) / max(iB.y, 1.0);
    float sway = 1.0 + 0.25 * sin(TAU * 0.22 * t + TAU * iB.z);
    float trunk = uFlex.x * 0.035 * max(iB.y, 1.0) * B * sway * h * h;
    d.xz += w * trunk;
    d.y -= 0.5 * trunk * trunk / max(p.y, 0.5) * h;

    float limb = aWind.x * uFlex.y;
    if (limb > 0.0) {
        float ph = TAU * aWind.w;
        float along = 0.10 * B * (0.75 + 0.25 * sin(1.3 * t + ph));
        float across = 0.04 * sB * sin(1.1 * t + ph * 1.7);
        float up = 0.03 * sB * sin(1.7 * t + ph + 1.0);
        d += limb * vec3(w.x * along - w.y * across, up, w.y * along + w.x * across);
    }
    float twig = aWind.y * uFlex.y;
    if (twig > 0.0) {
        float ph = TAU * aLook.x;
        float osc = 0.12 * sB * sin(2.7 * t + ph);
        d += twig * vec3(cos(ph) * osc + w.x * 0.08 * B, 0.5 * sin(ph * 1.3) * osc, sin(ph) * osc + w.y * 0.08 * B);
    }
    float fl = aWind.z * uFlex.z;
    if (fl > 0.0) {
        float ph = TAU * 7.0 * aLook.x + (p.x + p.z) * 5.0;
        d += aNrm * fl * 0.025 * (0.3 * sB + 0.7 * B) * sin(11.0 * t + ph);
    }
    p += d;

    p *= iB.x;
    p = vec3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
    vec3 world = iA.xyz + p;
    gl_Position = uVP * vec4(world, 1.0);
    vWorld = world;
    vNormal = vec3(c * aNrm.x - s * aNrm.z, aNrm.y, s * aNrm.x + c * aNrm.z);
    vLeaf = kind;
    vAo = ao;
    vUv = aUv;
    vTex = kind > 0.5 ? (kind < 1.5 && uTextured.y == 1 ? 2.0 : 0.0) : (uTextured.x == 1 ? 1.0 : 0.0);
    vec3 col;
    if (kind > 0.5) {
        col = mix(uLeafLo, uLeafHi, rand);
        if (vTex > 1.5) col = min(col / max(uLeafRef, vec3(1e-3)), vec3(3.0));
        col *= (0.85 + 0.3 * fract(rand * 7.31)) * ao;
    } else {
        col = (vTex > 0.5 ? vec3(1.0) : uBark) * (0.85 + 0.3 * rand) * (0.55 + 0.45 * ao);
    }
    float snow = uSnow * smoothstep(0.25, 0.8, aNrm.y) * (kind > 0.5 ? 0.6 : 1.0);
    vColor = mix(col, vec3(0.85), snow);
}
