package com.dan.longship;

/** Werkstoffe der Schiffe; der Shader in {@code gl/} malt jeden aus Rauschen (u läuft mit der Faser bzw. dem Tuch). */
public enum Material {
    /** Planken außen: Eiche, geteert, fast schwarzbraun, Nietreihe an jeder Überlappung. */
    HULL,
    /** Planken innen: Eiche, heller, Spantabdrücke. */
    INNER,
    /** Kiel, Steven, Spanten, Duchten, Mast, Rah, Riemen: geölte Eiche, Faser entlang u. */
    WOOD,
    /** Segel aus Wolle: rot-weiße Bahnen senkrecht (u quer, v hoch), leicht durchscheinend. */
    SAIL,
    /** Schilde: Linde, bemalt (Farbe nach der Nummer in v), Buckel aus Eisen. */
    SHIELD,
    /** Eisen: Nieten, Schildbuckel, Beschläge. */
    IRON,
    /** Taue: Hanf, gedreht. */
    ROPE,
    /** Bodenbretter und Decks. */
    DECK,
    /** Ladung: Ballen und Fässer im Laderaum. */
    CARGO
}
