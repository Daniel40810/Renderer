// rime.glsl — Baustein für fremde Shader (wie weathering.glsl): Eis auf einer Fläche. Einbinden mit
// #include "/com/dan/rime/gl/rime.glsl"; RimeGl.bind() setzt die Uniforms für das ganze Bild, die *Local-Funktionen
// nehmen eigene Werte je Objekt oder je Höhe.
//   uRimeDicke  Dicke des Eises (m) · uRimeArt 0 keins, 1 Raueis, 2 Raureif, 3 Klareis
//   uRimeVon    Richtung, aus der der Wind kam (Einheitsvektor, x Ost, z Süd): dorthin zeigen die Fahnen
// rimeCover(n) sagt, wie weit die Fläche mit der Normalen n bedeckt ist (Raueis nur auf der Luvseite, Raureif überall);
// rimeApply mischt Farbe und Rauheit; rimeOffset schiebt Ecken eines Netzes gegen den Wind (die Fahne als Form, seit
// Brocken Phase 10); rimeFeder bricht die Bedeckung in Federn auf (Raueis wächst in Zapfen, nicht als glatte Schicht).
uniform float uRimeDicke;
uniform int uRimeArt;
uniform vec3 uRimeVon;

float rimeLocal(vec3 n, float dicke, int art, vec3 von) {
    if (art == 0 || dicke <= 0.0) return 0.0;
    float d = smoothstep(0.0005, 0.02, dicke);
    if (art == 2) return d * (0.55 + 0.45 * max(n.y, 0.0));
    if (art == 3) return d;
    // Raueis: auf der Luvseite, mit der Dicke greift es um die Kanten (Fahnen über 5 cm auch oben und an den Seiten)
    float um = smoothstep(0.02, 0.12, dicke);
    return d * smoothstep(-0.2 - 0.5 * um, 0.6 - 0.4 * um, dot(n, von));
}

float rimeCover(vec3 n) { return rimeLocal(n, uRimeDicke, uRimeArt, uRimeVon); }

// Federn: Raueis wächst in Zapfen gegen den Wind; p Ort (m), gibt 0..1 (1 = Eis). Ohne Rauschtextur: Hash-Zellen
// quer zum Wind, gestreckt längs des Windes [G].
float rimeHash(vec2 c) { return fract(sin(dot(c, vec2(127.1, 311.7))) * 43758.5453); }
float rimeFeder(vec3 p, vec3 von, float cover) {
    if (cover <= 0.0) return 0.0;
    vec3 q = normalize(cross(von, vec3(0.0, 1.0, 0.0)) + vec3(1e-4));
    vec2 uv = vec2(dot(p, q) * 40.0, p.y * 40.0);   // Zellen von 2,5 cm quer zum Wind
    float h = rimeHash(floor(uv));
    return smoothstep(h * 0.7, h * 0.7 + 0.3, cover);
}

// Versatz einer Ecke mit Normale n gegen den Wind (Weltkoordinaten, m): die Fahne als Form. Volle Dicke auf der
// Luvseite, die Hälfte an den Flanken, nichts in Lee; Raureif und Klareis legen sich nur um (ein Drittel nach außen).
vec3 rimeOffsetLocal(vec3 n, float dicke, int art, vec3 von) {
    if (art == 0 || dicke <= 0.0) return vec3(0.0);
    if (art != 1) return n * dicke * 0.33;
    float luv = smoothstep(-0.3, 0.7, dot(n, von));
    return von * dicke * luv + n * dicke * 0.15 * luv;
}
vec3 rimeOffset(vec3 n) { return rimeOffsetLocal(n, uRimeDicke, uRimeArt, uRimeVon); }

// Farbe (linear) mit Eis: Raueis und Raureif weiß mit Blaustich und matt, Klareis dunkel und glänzend (rauheit sinkt).
vec3 rimeApplyLocal(vec3 albedo, float c, int art, inout float rauheit) {
    if (art == 3) { rauheit = mix(rauheit, 0.08, c); return mix(albedo, albedo * 0.7 + vec3(0.04, 0.05, 0.06), c); }
    rauheit = mix(rauheit, 0.9, c);
    return mix(albedo, vec3(0.86, 0.90, 0.95), c);
}
vec3 rimeApply(vec3 albedo, vec3 n, inout float rauheit) { return rimeApplyLocal(albedo, rimeCover(n), uRimeArt, rauheit); }
