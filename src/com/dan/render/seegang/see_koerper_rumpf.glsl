// see_koerper_rumpf.glsl — Seegang Werkbuch, S7d2 (Datei 90): der Rumpf von see_koerper.comp (ein Faden je Punkt) und
// see_koerper16.comp, see_koerper64.comp (ZV_PARALLEL: eine Gruppe je Punkt, die Fäden teilen sich Stützstellen, Fernfeld
// und die Summen je Band; Befund L5d 1: mit wenigen Punkten bestimmt die Latenz eines Fadens die Zeit, nicht der Durchsatz).
// Vor dem Einbinden: local_size, BAENDER, seit S7d4 KOERPERFLAECHE, see_gemein.glsl, seit S7d6 KIEL_QUELLEN 24 und
// see_kiel_spur.glsl (vorher KIEL_BINDING 24 und see_kiel_am.glsl).
// Je Punkt (der Karte eines Körpers, Ort relativ zu B) zur Zeit der Karte:
//  - am festen Ort x die rohen Summen der linearen Wellen je Band (BAENDER in see_gemein.glsl);
//  - die Fläche über dem festen Ort (Euler): x₀ + ξ(x₀) = x, feste Punktiteration wie core.szene.Oberflaeche.euler, bis
//    der Schritt unter 1 mm liegt oder nach P.iter Schritten; hat die Anfrage dieselben Punkte wie im letzten Bild (P.warm),
//    beginnt sie bei ξ des letzten Bilds (Bindung 23) und rechnet höchstens P.iterWarm Schritte; η gilt am letzten Ort, an
//    dem gerechnet wurde;
//  - das Kielwasser am festen Ort, seit S7d2 ohne die eigene Quelle des Körpers (Eingabe z: ihre Nummer, −1 keine); seit S7d6
//    (Seegang Werkbuch 94, SF133 A) direkt aus den Spuren der Quellen (Bindung 24, see_kiel_spur.glsl) mit der größten
//    Wellenzahl KIEL_KMAX, nicht mehr bilinear aus den Kacheln: genau und vom Auge unabhängig. Mit ZV_PARALLEL teilen sich die
//    Fäden die Stücke der Spur, Faden 0 nimmt das nächste und rechnet das Muster.
// Alle Wellen zählen (d = 0), wie in der Referenz. Den Puffer liest der Prozessor einige Bilder später
// (render.seegang.Koerpersuche, core.szene.Wasserpunkte, physics.auftrieb.Bandwasser).

// Bindung 22 (in see_gemein.glsl): Kopf, dann bdPk[i], i < maxPunkte: Eingabe (x, y relativ zu B, Lage der eigenen Quelle
// im Kielwasser oder −1, –). Ausgabe ab maxPunkte je Punkt 3 + 4 nb vec4: (η über dem festen Ort, Re Z am festen Ort,
// letzter Schritt der Suche (m), Schritte), (h, k_p, m0, Gitter), (Höhe des Kielwassers, seine Wellenzahl, ξx, ξy), dann die
// Bänder wie bdSchreibe. Seit S7d3: Gitter = 256 · round(255 w_fein) + round(255 w_grob) (der Rest ist das Fernfeld); seit
// S7d4 (Seegang Werkbuch 92, mit KOERPERFLAECHE) vier Zahlen zu 6 Bit: w_fein und w_grob des Auges, die Anteile fein und grob
// der Körperfläche (Koerpersuche.gitter).
// Seit S7d3 (SF119 A): mit Startwert höchstens P.iterWarm Schritte (1); ist der letzte Schritt dann noch über 2 cm (ein
// schlechter Startwert, R15), rechnet sie weiter wie ohne Startwert bis P.iter.
layout(std430, binding = 23) buffer Start { vec4 start[]; };          // je Punkt ξ des letzten Bilds (x, y), –, –
layout(push_constant) uniform Suche { int zahl; int maxPunkte; int nbAus; int iter; int warm; int iterWarm; int kiel; int pad; } P;

// seit S7d6: größte Wellenzahl des Kielwassers an den Körpern [G], wie die nahe Kachel bis S7d4 (1 m je Zelle, drei Zellen
// je Welle: Wellen ab 3 m); Koerpersuche.KIEL_KMAX
const float KIEL_KMAX = 2.0 * 3.14159265358979 / 3.0;
#ifdef ZV_PARALLEL
shared float ksD2[gl_WorkGroupSize.x];
shared int ksK[gl_WorkGroupSize.x];
shared vec3 ksO[gl_WorkGroupSize.x];
#endif

/** Seit S7d6: das Kielwasser aller Quellen außer eigen am Ort x: (Höhe, Schaum, mittleres K, Σ g); alle Fäden der Gruppe. */
vec4 kielDirekt(vec2 x, int eigen) {
    float h = 0.0, f = 0.0, sk = 0.0, sg = 0.0;
    for (int qq = 0; qq < E.zahl; qq++) {
        int b = qq * KS_JE;
        if (qq == eigen || !kielInReichweite(b, x)) continue;          // für die Gruppe gleich
        float best = 3.0e38;
        int bk = 1 << 30;
        vec3 o = vec3(0.0);
#ifdef ZV_PARALLEL
        int l = int(gl_LocalInvocationID.x), nf = int(gl_WorkGroupSize.x);
        kielSpurTeil(b, x, l, nf, best, bk, o);
        ksD2[l] = best;
        ksK[l] = bk;
        ksO[l] = o;
        barrier();
        if (l == 0)
            for (int j = 1; j < nf; j++)
                if (ksD2[j] < best || ksD2[j] == best && ksK[j] < bk) { best = ksD2[j]; bk = ksK[j]; o = ksO[j]; }
        barrier();
        if (l != 0) continue;
#else
        kielSpurTeil(b, x, 0, 1, best, bk, o);
#endif
        if (!(best < 1.0e38) || !(o.x > 1e-6)) continue;
        vec4 e = kielBeitrag(b, o, KIEL_KMAX, true);
        h += e.x;
        sk += e.z;
        sg += e.w;
        f = max(f, e.y);
    }
    return vec4(h, f, sg > 0.0 ? sk / sg : 0.0, sg);
}

void main() {
#ifdef ZV_PARALLEL
    int i = int(gl_WorkGroupID.x);                                   // eine Gruppe je Punkt; Faden 0 schreibt
    bool schreibt = gl_LocalInvocationID.x == 0u;
#else
    int i = int(gl_GlobalInvocationID.x);
    bool schreibt = true;
#endif
    if (i >= P.zahl) return;
    int je = 3 + 4 * P.nbAus;
    int o = P.maxPunkte + i * je;
    if (schreibt) for (int k = 0; k < je; k++) bdPk[o + k] = vec4(0.0);
    vec4 ein = bdPk[i];
    vec2 x = ein.xy;
    int eigen = int(round(ein.z));
    vec4 a, b;
    vec2 auf, wl;
    float khp;
    bdBasis = o + 3;
    bdAn = true;
    punktK(x, 0.0, 1.0, a, b, auf, khp, wl);
    bdAn = false;
    float reZ = b.x;
    vec3 kopf = bdKopf;
    // seit S7d4: je 6 Bit fein und grob des Auges, fein und grob der Körperfläche (Anteile; 63 = 1)
    float gitter = 64.0 * (64.0 * (64.0 * round(63.0 * clamp(bdGitter.x, 0.0, 1.0)) + round(63.0 * clamp(bdGitter.y, 0.0, 1.0)))
            + round(63.0 * clamp(bdKoerper.x, 0.0, 1.0))) + round(63.0 * clamp(bdKoerper.y, 0.0, 1.0));
    bool warm = P.warm != 0;
    vec2 p = warm ? x - start[i].xy : x - a.yz;
    int maxIt = warm ? P.iterWarm : P.iter;
    float eta = a.x, schritt = 1.0;
    int it = 0;
    do {
        punktK(p, 0.0, 1.0, a, b, auf, khp, wl);
        vec2 np = x - a.yz;
        schritt = length(np - p);
        eta = a.x;
        p = np;
        it++;
    } while (it < maxIt && schritt >= 0.001);
    while (warm && schritt > 0.02 && it < P.iter) {                     // seit S7d3: Rückfall bei schlechtem Startwert
        punktK(p, 0.0, 1.0, a, b, auf, khp, wl);
        vec2 np = x - a.yz;
        schritt = length(np - p);
        eta = a.x;
        p = np;
        it++;
    }
    vec4 kw = P.kiel != 0 ? kielDirekt(x, eigen) : vec4(0.0);           // seit S7d6 (SF133 A) direkt, vor dem Rücksprung
    if (!schreibt) return;
    start[i] = vec4(x - p, 0.0, 0.0);
    bdPk[o] = vec4(eta + kw.x, reZ, schritt, float(it));
    bdPk[o + 1] = vec4(kopf, gitter);
    bdPk[o + 2] = vec4(kw.x, kw.z, x - p);
}
