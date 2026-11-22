// kopplung.glsl — Schachtel auf der Karte: die Randkanten des feinen Gitters und die groben Zellen außen (Werkbuch
// 12.5, Stand P4c; wie com.dan.physics.hydro.Kette). Erwartet die Bindungen KOPPLUNG_BINDUNG … + 2 und die Funktion
// nass(c) für eine grobe Zelle.
// je Randindex des feinen (West j, Ost j, Süd i, Nord i): Kante, Vorzeichen (+1: positiver Fluss geht hinein; 0: nicht
// geschachtelt), Nummer der Zelle außen, Nummer der Nachbarin in der Reihe (−1: keine)
layout(std430, binding = KOPPLUNG_BINDUNG) readonly buffer Kopplung { int kop[]; };
layout(std430, binding = KOPPLUNG_BINDUNG + 1) readonly buffer Gewicht { float gew[]; };     // Gewicht der Nachbarin (0 … ½)
layout(std430, binding = KOPPLUNG_BINDUNG + 2) readonly buffer AussenZelle { int zelleO[]; }; // je Zelle außen: Zelle des groben

/** Gewicht der Nachbarin für den Randindex rr im jetzigen Stand: nur nasse Zellen zählen. */
float gewicht(int rr) {
    int o2 = kop[4 * rr + 3];
    float w = gew[rr];
    if (o2 < 0 || w == 0.0) return 0.0;
    bool na = nass(zelleO[kop[4 * rr + 2]]), nb = nass(zelleO[o2]);
    return na == nb ? w : nb ? 1.0 : 0.0;
}
