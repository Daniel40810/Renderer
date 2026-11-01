// clipmap.glsl — Baustein für den Scheitelshader der App (com.dan.clipmap, Phase 4 von LituyaBay1958).
// Einsetzen: als Include von /com/dan/clipmap/gl/clipmap.glsl (oder ClipmapGl.glsl()), dann im Scheitelshader
//     ClipVertex c = clipmapVertex();   gl_Position = uVP * vec4(c.world, 1.0);
// und im Pixelshader (dort genügt der Baustein auch) am Anfang: if (clipmapHidden(vWorld.xz)) discard;
// Raum: x Ost, z Süd, y oben (m). Die Höhen liegen je Stufe ringförmig in einer Texturschicht (R32F, uClipT²); Gitter-
// punkt (i, j) einer Stufe steht im Texel (i mod T, j mod T). Eingang je Scheitel: aClipGrid = (k, l), 0 … uClipM − 1.
// Übergang zur gröberen Stufe im Band am Rand (morph), gleich gerechnet wie Clipmap.morph() und vertexHeight() in Java.
uniform highp sampler2DArray uClipH;
uniform int uClipT;          // Kantenlänge der Speicher
uniform int uClipM;          // Punkte je Kante einer Stufe
uniform int uClipLevel;      // diese Stufe
uniform int uClipCoarse;     // 1 = es gibt eine gröbere Stufe
uniform ivec2 uClipIdx;      // Index des Scheitels (0, 0) im Gitter dieser Stufe
uniform vec4 uClipGeo;       // Ursprung x, z; Zellweite; 1/(2R') für die Erdkrümmung (0 = flach)
uniform vec3 uClipCam;       // Auge
uniform float uClipNoData;   // Ersatz für NaN
uniform vec4 uClipInner;     // Rechteck der feineren Stufe (xmin, zmin, xmax, zmax), xmin > xmax: keins

#ifdef CLIPMAP_VERTEX
layout(location = 0) in vec2 aClipGrid;   // ClipmapGl.ATTRIB

// i mod T auch für negative i (in GLSL ist % mit negativen Zahlen undefiniert; Befund Phase 4: Mesa verschob die Zeilen)
int clipMod(int i, int T) { return i - T * int(floor(float(i) / float(T))); }

float clipFetch(int level, int i, int j) {
    int T = uClipT;
    float h = texelFetch(uClipH, ivec3(clipMod(i, T), clipMod(j, T), level), 0).r;
    return isnan(h) ? uClipNoData : h;
}

// Höhe der gröberen Stufe am Gitterpunkt (i, j) dieser Stufe: bilinear zwischen ihren Punkten (Mitte einer Kante oder Zelle).
float clipCoarse(int i, int j) {
    int ci = i >> 1, cj = j >> 1, L = uClipLevel + 1;
    bool ox = (i & 1) != 0, oz = (j & 1) != 0;
    float a = clipFetch(L, ci, cj);
    if (!ox && !oz) return a;
    if (ox && !oz) return 0.5 * (a + clipFetch(L, ci + 1, cj));
    if (!ox) return 0.5 * (a + clipFetch(L, ci, cj + 1));
    return 0.25 * (a + clipFetch(L, ci + 1, cj) + clipFetch(L, ci, cj + 1) + clipFetch(L, ci + 1, cj + 1));
}

struct ClipVertex {
    vec3 world;      // Ort mit Erdkrümmung
    vec3 normal;     // Normale (mit Übergang)
    float height;    // Höhe ohne Krümmung
    float morph;     // 0 innen … 1 am Rand
};

ClipVertex clipmapVertex() {
    int k = int(aClipGrid.x + 0.5), l = int(aClipGrid.y + 0.5);
    int i0 = uClipIdx.x, j0 = uClipIdx.y, M = uClipM, L = uClipLevel;
    int i = i0 + k, j = j0 + l;
    float c = uClipGeo.z;
    int hf = (M - 1) / 2;
    float h = clipFetch(L, i, j);
    int kl = max(k - 1, 0), kr = min(k + 1, M - 1), lu = max(l - 1, 0), ld = min(l + 1, M - 1);
    float gx = (clipFetch(L, i0 + kr, j) - clipFetch(L, i0 + kl, j)) / (float(kr - kl) * c);
    float gz = (clipFetch(L, i, j0 + ld) - clipFetch(L, i, j0 + lu)) / (float(ld - lu) * c);
    vec3 n = normalize(vec3(-gx, 1.0, -gz));
    float a = 0.0;
    if (uClipCoarse == 1) {
        int d = max(abs(k - hf), abs(l - hf));
        int w = max(2, (M - 1) / 10);
        a = clamp(float(d - (hf - w - 1)) / float(w), 0.0, 1.0);
        if (a > 0.0) {
            float hc = clipCoarse(i, j);
            float cx = (clipCoarse(i + 2, j) - clipCoarse(i - 2, j)) / (4.0 * c);
            float cz = (clipCoarse(i, j + 2) - clipCoarse(i, j - 2)) / (4.0 * c);
            h = mix(h, hc, a);
            n = normalize(mix(n, normalize(vec3(-cx, 1.0, -cz)), a));
        }
    }
    vec2 xz = uClipGeo.xy + vec2(float(i), float(j)) * c;
    vec2 r = xz - uClipCam.xz;
    ClipVertex o;
    o.world = vec3(xz.x, h - dot(r, r) * uClipGeo.w, xz.y);
    o.normal = n;
    o.height = h;
    o.morph = a;
    return o;
}
#endif

// Liegt der Ort im Rechteck der feineren Stufe? Dann zeichnet diese ihn, die gröbere verwirft ihn.
bool clipmapHidden(vec2 xz) {
    return xz.x > uClipInner.x && xz.x < uClipInner.z && xz.y > uClipInner.y && xz.y < uClipInner.w;
}
