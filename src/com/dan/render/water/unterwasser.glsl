// unterwasser.glsl – render.water (Seegang Werkbuch, Datei 104, S8d, SF160 A): der Schwimmerblick unter Wasser. Die Linse
// liegt an der Wasserlinie; was unter ihr liegt, sieht das Wasser von unten: das Snellsche Fenster (Himmel durch die Fläche
// gebrochen, halber Winkel asin(1/n) = 48,6°), außerhalb davon Totalreflexion, dazu die Trübung exp(−c s) zum Licht des
// Wassers selbst, der Grund und Lichtstreifen [G]. Raum wie render.water: x Ost, y oben, z Süd.
//
// Licht im Wasser wie das Wasser von oben (seit L6b, Befund: das Licht des Wassers war rund sechsmal zu dunkel gegen das Bild
// von oben): die Leuchtdichte nach oben unter der Fläche aus der Tafel der Säule (core.szene.Saeule, tief) je Einheit der
// Bestrahlung unter der Fläche, mit der Sonne und dem Himmel ins Wasser (licht.comp, Wert himmelWasser); sie gilt für alle
// Richtungen außerhalb des Fensters [G]. Im Fenster der Himmel mal (1 − F) n² (Leuchtdichte im dichteren Medium).
//
// Bindungen: 0 Kopf, 1 Himmel/Werte, 2 Himmel/Durchlaessigkeit, 3 Himmel/Ansicht, 4 Licht des Wassers (licht.comp).
#include "/com/dan/core/gpu/luft.glsl"
layout(set = 0, binding = 0, std430) readonly buffer Kopf {
    vec4 rechts;    // Bild: rechts (Einheitsvektor); w tan des halben Bildwinkels waagrecht
    vec4 oben;      // Bild: oben; w tan des halben Bildwinkels senkrecht
    vec4 vorn;      // Blickachse; w Höhe des Auges über dem Boden für die Luft (m)
    vec4 linse;     // Breite, Höhe der Linse (m); Tiefe ihrer Mitte unter der Fläche (m); Zunahme der Tiefe je m nach rechts
    vec4 wasser;    // Tiefe des Grunds unter der Fläche am Auge (m; < 0: kein Grund), Brechzahl, Zeit (s, mod 1000), 1 = an
    vec4 normale;   // Normale der Fläche am Auge (nach oben); w Stärke der Kräusel für die Fläche von unten [G]
    vec4 c;         // Strahldämpfung c (1/m) R G B; Albedo des Grunds
    vec4 kd;        // diffuse Dämpfung K_d (1/m) R G B; Stärke der Lichtstreifen [G]
    vec4 rinf;      // Reflexion tiefen Wassers R∞ R G B; Breite des Meniskus auf der Linse (m)
    vec4 bild;      // Breite, Höhe des Bilds (Bildpunkte); Zunahme der Tiefe je m nach oben auf der Linse (≈ −1); 0
    vec4 luS[8];    // Säule, tief: L nach oben je Einheit der Bestrahlung unter der Fläche aus μ_s = Saeule.MU_S[q] (1/sr) R G B
    vec4 luH;       // dasselbe für den Himmel, gleich hell in allen Richtungen der Luft (Saeule.himmelGewichte)
};
layout(set = 0, binding = 1, std430) readonly buffer Werte { Himmelswerte hw; };
layout(set = 0, binding = 2) uniform sampler2D durch;
layout(set = 0, binding = 3) uniform sampler2D ansichtT;
layout(set = 0, binding = 4, std430) readonly buffer LichtW { vec4 himmelLuft; vec4 himmelWasser; };

vec3 uwSonne(vec3 l, vec3 e, float h) {
    float r = R_BOTTOM + h, q = qAus(h);
    if (trifftBoden(r, q, l.y)) return vec3(0.0);
    return e * durchlaessigkeit(durch, r, q, l.y);
}

// Richtung des Bildpunkts uv (0 … 1, y nach unten) im Raum
vec3 uwStrahl(vec2 uv) { return normalize(vorn.xyz + rechts.xyz * (2.0 * uv.x - 1.0) * rechts.w + oben.xyz * (1.0 - 2.0 * uv.y) * oben.w); }

// Tiefe des Punkts der Linse unter dem Bildpunkt uv unter der Fläche (m; ≤ 0: über Wasser), in der Ebene der Fläche am Auge.
// Die Linse füllt das Bild.
float uwTiefe(vec2 uv) {
    float xl = (uv.x - 0.5) * linse.x, yl = (0.5 - uv.y) * linse.y;
    return linse.z + xl * linse.w + yl * bild.z;
}

// Fresnel für unpolarisiertes Licht (cos des Einfalls ci, Verhältnis n = n₁ / n₂ der Brechzahlen vor und hinter der Fläche;
// n > 1 aus dem Wasser: Totalreflexion ab sin θ = 1/n)
float uwFresnel(float ci, float n) {
    float st2 = n * n * (1.0 - ci * ci);
    if (st2 >= 1.0) return 1.0;
    float ct = sqrt(1.0 - st2);
    float rs = (n * ci - ct) / (n * ci + ct), rp = (n * ct - ci) / (n * ct + ci);
    return 0.5 * (rs * rs + rp * rp);
}

float uwRausch(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453), b = fract(sin(dot(i + vec2(1, 0), vec2(127.1, 311.7))) * 43758.5453);
    float cc = fract(sin(dot(i + vec2(0, 1), vec2(127.1, 311.7))) * 43758.5453), d = fract(sin(dot(i + vec2(1, 1), vec2(127.1, 311.7))) * 43758.5453);
    return mix(mix(a, b, f.x), mix(cc, d, f.x), f.y);
}

// Waagerechte Bestrahlung unter der Fläche (lx je Kanal) aus der Sonne (mit 1 − F) und aus dem Himmel (licht.comp)
vec3 uwSonneEin() {
    float mu = max(0.0, hw.sonne.y);
    return uwSonne(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, max(0.5, vorn.w)) * mu * (1.0 - uwFresnel(mu, 1.0 / wasser.y));
}
vec3 uwEd0() { return uwSonneEin() + himmelWasser.rgb; }

// Leuchtdichte nach oben unter der Fläche aus der Säule (tief): Sonne nach ihrer Richtung im Wasser, Himmel gleich verteilt
vec3 uwLu() {
    float n = wasser.y, mua = max(0.0, hw.sonne.y);
    float mus = sqrt(max(0.0, 1.0 - (1.0 - mua * mua) / (n * n)));
    float k = clamp((mus - 0.665) / (1.0 - 0.665) * 7.0, 0.0, 7.0);
    int k0 = min(int(k), 6);
    vec3 lS = mix(luS[k0].rgb, luS[k0 + 1].rgb, k - float(k0));
    return lS * uwSonneEin() + luH.rgb * himmelWasser.rgb;
}

// Was der Strahl d aus der Tiefe D unter der Fläche sieht (Leuchtdichte, cd/m²). frei: 1 im Snellschen Fenster, 0 bei
// Totalreflexion, −1 ohne Fläche (nach unten); t: Durchlässigkeit exp(−c s) des Wegs (Mittel der Kanäle).
vec3 uwLeuchtdichte(vec3 d, float D, out float frei, out float t) {
    vec3 E0 = uwEd0();
    vec3 Lw = uwLu() * exp(-kd.rgb * D);                                   // Licht des Wassers selbst in der Tiefe D
    frei = -1.0;
    t = 0.0;
    float n = wasser.y;
    if (d.y > 1e-3) {
        float s = D / d.y;
        vec3 hit = d * s;                                                     // relativ zum Auge
        // Normale der Fläche von unten: die am Auge, dazu Kräusel [G]
        vec2 q = hit.xz * 1.7 + vec2(0.37, -0.21) * wasser.z;
        vec3 nr = normalize(normale.xyz + normale.w * vec3(uwRausch(q) - 0.5, 0.0, uwRausch(q + 13.1) - 0.5));
        float ci = clamp(dot(d, nr), 0.0, 1.0);
        float F = uwFresnel(ci, n);                                           // aus dem Wasser (n) in die Luft (1)
        vec3 Ls;
        if (F < 1.0) {
            vec3 o = refract(d, -nr, n);
            Ls = ansicht(ansichtT, normalize(vec3(o.x, max(o.y, 0.01), o.z)), hw.sonne.w) * (1.0 - F) * n * n + F * Lw;
            frei = 1.0;
        } else {
            Ls = Lw;
            frei = 0.0;
        }
        vec3 tt = exp(-c.rgb * s);
        t = dot(tt, vec3(1.0 / 3.0));
        // Lichtstreifen [G]: Bündel der Sonne, die die Kräusel bündeln, als Muster längs des Wegs
        float st = 1.0 + kd.w * (uwRausch(hit.xz * 0.9 + vec2(wasser.z * 0.25, 0.0)) - 0.5);
        return Ls * tt + Lw * st * (1.0 - tt);
    }
    // nach unten oder waagrecht: der Grund, wenn es einen gibt
    if (wasser.x > D && d.y < -1e-3) {
        float s = (wasser.x - D) / -d.y;
        vec3 Lb = c.w * E0 * exp(-kd.rgb * wasser.x) / L_PI;
        vec3 tt = exp(-c.rgb * s);
        t = dot(tt, vec3(1.0 / 3.0));
        return Lb * tt + Lw * (1.0 - tt);
    }
    return Lw;
}
