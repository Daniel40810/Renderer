// stich.frag — der Druck je Bildpunkt (wie Stich.stechen): Papier, wo gestochene Punkte ihn decken (Summe der
// geglätteten Kanten, höchstens 1), sonst Farbe als Schicht der Dicke 1 + Wolken + Korn. Zeile 0 des Ziels ist unten.
uniform usampler2D uZustand;
uniform vec2 uDruck;
uniform vec3 uPapier, uLnVerh, uAusgleich;   // Papier linear, ln(Farbe / Papier), Ausgleich der Farbe (ln)
uniform float uWolken, uKorn;
layout(location = 0) out vec4 oFarbe;

float srgb(float c) { return c <= 0.0031308 ? 12.92 * c : 1.055 * pow(max(c, 0.0), 1.0 / 2.4) - 0.055; }

void main() {
    ivec2 px = ivec2(int(gl_FragCoord.x), int(uDruck.y) - 1 - int(gl_FragCoord.y));   // oben zuerst, wie Java
    vec2 q = (vec2(px) + 0.5) / uZelle;
    ivec2 ci = ivec2(floor(q));
    float deckt = 0.0;
    for (int dj = -uReich; dj <= uReich; dj++)
        for (int di = -uReich; di <= uReich; di++) {
            ivec2 c = ci + ivec2(di, dj);
            if (texelFetch(uZustand, c + uReich, 0).r == 0u) continue;
            Form f = form(c);
            float d = abstand(q, f) * uZelle + einzug(f, 1.0);
            deckt += clamp(0.5 - d, 0.0, 1.0);
        }
    deckt = min(deckt, 1.0);
    float k = korn(px);
    float dicke = 1.0 + uWolken * wolken((vec2(px) + 0.5) / uDruck.x) + uKorn * k;
    vec3 papier = uPapier * (1.0 + 0.5 * uKorn * k);
    vec3 farbe = uPapier * exp(dicke * uLnVerh - uAusgleich);
    vec3 lin = deckt * papier + (1.0 - deckt) * farbe;
    oFarbe = vec4(srgb(lin.r), srgb(lin.g), srgb(lin.b), 1.0);
}
