package com.dan.succession;

/** Der Boden eines Platzes nach der Störung (seit LituyaBay1958 Phase 14): bis zum Fels abgeräumt, offener Mineralboden oder Schutt, Waldboden (Moder, Moos, Totholz). */
public enum Boden {
    FELS("Fels"), MINERAL("Mineralboden"), WALDBODEN("Waldboden");
    public final String text;

    Boden(String t) { text = t; }

    @Override public String toString() { return text; }
}
