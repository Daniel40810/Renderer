package com.dan.physics.vegetation;

import com.dan.core.szene.Zonen;

/**
 * Pflanzen der Salzwiese je Zone (P13b, F152 A, F153 A) [G nach Literatur]: Höhe und Bedeckung im Sommer und im
 * Winter, dazwischen nach dem Tag des Jahres; Rauheit z0 = {@link #z0Anteil} · Höhe. Beweidet (Fenne der Gestalt)
 * ist die Höhe kleiner ({@link #weide}).
 * <ul>
 * <li>Pionierzone: Queller und Schlickgras, licht; im Winter fast kahl (Queller stirbt ab).</li>
 * <li>Untere Salzwiese: Andel, Strandflieder; dichter Rasen.</li>
 * <li>Obere Salzwiese: Rotschwingel, Strand-Beifuß; dicht, höher.</li>
 * </ul>
 * Beispiel (Sommer): Andel 0,15 m → z0 15 mm → n nach Strickler 0,033; obere 0,30 m → n 0,038; Fenne oben (beweidet)
 * 0,12 m → n 0,032. Die Rauheitsklassen der Packung haben Fenne 0,030, Salzwiese 0,035.
 */
public final class Salzwiese {
    /** Höhe (m) und Bedeckung (0–1) je Zone (Index wie {@link Zonen}: 1 Pionier, 2 unten, 3 oben), Sommer und Winter [G]. */
    public double[] hoeheSommer = {0, 0.20, 0.15, 0.30}, hoeheWinter = {0, 0.04, 0.08, 0.15};
    public double[] deckungSommer = {0, 0.40, 0.90, 0.95}, deckungWinter = {0, 0.10, 0.75, 0.90};
    /** Beweidete Flächen (Fenne): Faktor auf die Höhe [G]. */
    public double weide = 0.4;
    /** z0 = Anteil · Höhe [G, üblich 0,1]. */
    public double z0Anteil = 0.1;
    /** Tag des Jahres mit dem kleinsten Stand (Mitte Januar) [G]; der größte ein halbes Jahr später. */
    public int tagWinter = 20;

    /** Stand der Jahreszeit 0 (Winter) … 1 (Sommer) am Tag des Jahres. */
    public double sommer(int tag) { return 0.5 - 0.5 * Math.cos(2 * Math.PI * (tag - tagWinter) / 365.25); }

    public double hoehe(int zone, boolean beweidet, int tag) {
        if (zone <= 0 || zone > 3) return 0;
        double s = sommer(tag), h = hoeheWinter[zone] + s * (hoeheSommer[zone] - hoeheWinter[zone]);
        return beweidet ? h * weide : h;
    }

    public double deckung(int zone, int tag) {
        if (zone <= 0 || zone > 3) return 0;
        double s = sommer(tag);
        return deckungWinter[zone] + s * (deckungSommer[zone] - deckungWinter[zone]);
    }

    public double z0(int zone, boolean beweidet, int tag) { return z0Anteil * hoehe(zone, beweidet, tag); }

    /** Manning-n nach Strickler wie {@code physics.hydro} (n = k_s^(1/6) / 26, k_s = 30 z0), zur Auskunft. */
    public static double manning(double z0) { return z0 > 0 ? Math.pow(30 * z0, 1.0 / 6) / 26 : 0; }
}
