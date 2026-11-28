// linse.glsl – render.frame (Seegang Werkbuch, Datei 104, S8a, SF158 A): Tropfen auf der Linse. Die Linse ist eine Scheibe
// vor der Kamera (Breite × Höhe in m, ganz im Bild); die Tropfen (com.dan.wetglass) liegen als Kugelkappen mit 60°
// Randwinkel darauf. Ein Strahl der Kamera tritt durch die Scheibe ins Wasser (eben, Brechung nach Snellius), trifft die
// Kappe und tritt in die Luft aus; sein neuer Weg sagt, welchen Punkt der Szene der Bildpunkt zeigt. Ohne Totalreflexion
// zeigt der Tropfen die Welt kopfüber; am Rand wirft er das Licht zurück (dunkler Rand).
//
// Bindungen: 0 Kopf, 1 Tropfen (je Tropfen zwei vec4 wie Wetglass.Snapshot), 2 Höhe (je Zelle Höhe in m und Gefälle).
// Seit S9b-3 (Seegang Werkbuch 107): Kacheln (linse_hoehe.comp Bindung 3, linse_kachel.vert Bindung 4): je Kachel von
// kachel.w × kachel.w Zellen die Tropfen, deren Kasten sie berührt (aufsteigend), und die Kacheln, in denen etwas bricht.
layout(set = 0, binding = 0, std430) readonly buffer Kopf {
    vec4 linse;     // Breite, Höhe der Scheibe (m), Zellen nx, ny der Höhenkarte
    vec4 bild;      // tan des halben Bildwinkels waagrecht, senkrecht; Zahl der Tropfen; Haut der Überspülung (m)
    vec4 mehr;      // Brechzahl des Wassers, Durchlässigkeit beider Flächen, Breite, Höhe des Bilds (Bildpunkte)
    vec4 meniskus;  // seit S8d: Wasser vor der Scheibe: Tiefe ihrer Mitte unter der Fläche (m), Zunahme je m nach rechts und oben; w 1 = da
    vec4 kachel;    // seit S9b-3: 1 = Kacheln gesetzt, Kacheln quer, hoch, Zellen je Kachel
};
layout(set = 0, binding = 1, std430) readonly buffer Tropfen { vec4 tr[]; };
#ifdef LINSE_SCHREIBT
layout(set = 0, binding = 2, std430) buffer Hoehe { vec4 hk[]; };
#else
layout(set = 0, binding = 2, std430) readonly buffer Hoehe { vec4 hk[]; };
#endif

const float L_KONTAKT_S = 0.86602540, L_KONTAKT_C = 0.5;                // sin, cos 60°

// Höhe einer Kappe bei q = Abstand / Kontakthalbmesser, bezogen auf diesen (wie Wetglass.capHeight)
float lKappe(float q) { return sqrt(max(0.0, 1.0 / (L_KONTAKT_S * L_KONTAKT_S) - q * q)) - L_KONTAKT_C / L_KONTAKT_S; }

// Höhe (m) und Gefälle (∂h/∂x, ∂h/∂y) der Kappe des Tropfens i am Ort p (wie Wetglass.Snapshot.capMm); h = 0 außerhalb.
// Das Gefälle genau aus der Kugel: ∂h/∂p = −(a' u / (1 + s) + c (−u_y, u_x)) / (a √(1/sin²θ − q²)).
vec3 lKappeAn(int i, vec2 p) {
    vec4 a = tr[2 * i], b = tr[2 * i + 1];
    float ar = a.z;
    vec2 d = p - a.xy;
    float along = d.x * b.x + d.y * b.y, across = -d.x * b.y + d.y * b.x;
    along = (along + ar * a.w * 0.5) / (1.0 + a.w);
    float d2 = (along * along + across * across) / (ar * ar);
    if (d2 >= 1.0) return vec3(0.0);
    float w = sqrt(max(1e-6, 1.0 / (L_KONTAKT_S * L_KONTAKT_S) - d2));
    return vec3(lKappe(sqrt(d2)) * ar, -(along * b.xy / (1.0 + a.w) + across * vec2(-b.y, b.x)) / (ar * w));
}

// Die höchste Kappe am Ort p der Scheibe (m; x rechts, y oben, Ursprung links unten): Höhe, Gefälle, Nummer (−1: keine)
// ein Tropfen i an der Stelle p: höher als best, dann ist er es (seit S9b-3 geteilt mit der Schleife über die Kachel)
void lTropfen(int i, vec2 p, inout vec4 best) {
    vec4 a = tr[2 * i];
    vec2 d = p - a.xy;
    if (abs(d.x) > a.z * 2.3 || abs(d.y) > a.z * 2.3) return;
    vec3 k = lKappeAn(i, p);
    if (k.x > best.x) best = vec4(k, float(i));
}

vec4 lHoeheTropfen(vec2 p) {
    vec4 best = vec4(0.0, 0.0, 0.0, -1.0);
    int n = int(bild.z + 0.5);
    for (int i = 0; i < n; i++) lTropfen(i, p, best);
    return best;
}

// Höhe und Gefälle am Ort p: die Kappe, die die Zelle am Ort führt, genau gerechnet; sonst bilinear aus der Karte; dazu die
// Haut der Überspülung
vec3 lHoehe3(vec2 p) {
    vec2 g = p / linse.xy * linse.zw - 0.5;
    ivec2 n = ivec2(linse.zw);
    ivec2 c = clamp(ivec2(floor(g + 0.5)), ivec2(0), n - 1);
    int i = int(hk[c.x + n.x * c.y].w);
    if (i >= 0) {
        vec3 k = lKappeAn(i, p);
        if (k.x > 0.0) return vec3(k.x + bild.w, k.yz);
    }
    vec2 f = floor(g);
    vec2 u = g - f;
    ivec2 i0 = clamp(ivec2(f), ivec2(0), n - 1), i1 = clamp(ivec2(f) + 1, ivec2(0), n - 1);
    vec3 a = hk[i0.x + n.x * i0.y].xyz, b = hk[i1.x + n.x * i0.y].xyz, cc = hk[i0.x + n.x * i1.y].xyz, d = hk[i1.x + n.x * i1.y].xyz;
    vec3 r = mix(mix(a, b, u.x), mix(cc, d, u.x), u.y);
    return vec3(r.x + bild.w, r.yz);
}

float lHoehe(vec2 p) { return lHoehe3(p).x; }

// Der Bildpunkt uv (0 … 1, y nach unten) zeigt durch den Tropfen nach uvOut; t: Anteil des Lichts (0: Totalreflexion).
// Gibt false zurück, wo kein Wasser ist (der Bildpunkt bleibt).
bool lDurch(vec2 uv, out vec2 uvOut, out float t) {
    uvOut = uv;
    t = 1.0;
    vec2 p = vec2(uv.x, 1.0 - uv.y) * linse.xy;
    // seit S8d: unter der Wasserlinie bricht kein Tropfen
    if (meniskus.w > 0.5 && meniskus.x + meniskus.y * (p.x - 0.5 * linse.x) + meniskus.z * (p.y - 0.5 * linse.y) > 0.0) return false;
    float h0 = lHoehe(p);
    if (h0 <= 1e-7) return false;
    vec3 d = normalize(vec3((2.0 * uv.x - 1.0) * bild.x, (1.0 - 2.0 * uv.y) * bild.y, 1.0));
    float nw = mehr.x;
    vec3 dw = refract(d, vec3(0.0, 0.0, -1.0), 1.0 / nw);                  // durch die ebene Scheibe ins Wasser
    // die Fläche des Wassers längs des Strahls im Wasser: drei Schritte des Fixpunkts z = h(p + dw.xy z / dw.z)
    vec2 q = p;
    float z = h0;
    for (int k = 0; k < 4; k++) {
        q = p + dw.xy * (z / dw.z);
        z = lHoehe(q);
    }
    vec2 gr = lHoehe3(q).yz;
    vec3 nrm = normalize(vec3(-gr, 1.0));                                   // nach außen
    vec3 o = refract(dw, -nrm, nw);
    if (dot(o, o) < 0.5 || o.z <= 1e-3) { t = 0.0; return true; }
    t = mehr.y;
    uvOut = vec2(0.5 + 0.5 * (o.x / o.z) / bild.x, 0.5 - 0.5 * (o.y / o.z) / bild.y);
    return true;
}
