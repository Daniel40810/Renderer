package com.dan.aurora;

/**
 * Ein Beobachtungsort: Name, geographische Breite und Länge und die Deklination des echten Felds (WMM 2025, auf ein
 * halbes Grad gerundet), weil der Dipol die Deklination in Europa um 30° verfehlt. {@code Double.NaN} als Deklination
 * heißt: aus dem Dipol nehmen. Die Presets sind Orte aus den Szenenprojekten und bekannte Nordlichtorte.
 */
public final class Ort {
    public String name;
    /** Grad, nördlich und östlich positiv. */
    public double breite, laenge;
    /** Deklination in Grad, östlich positiv; NaN = aus dem Dipol. */
    public double deklination;

    public Ort(String name, double breite, double laenge, double deklination) {
        this.name = name;
        this.breite = breite;
        this.laenge = laenge;
        this.deklination = deklination;
    }

    public Ort kopie() { return new Ort(name, breite, laenge, deklination); }

    @Override public String toString() { return name; }

    public static Ort tromsoe() { return new Ort("Tromsø", 69.65, 18.96, 11); }
    public static Ort lofoten() { return new Ort("Lofoten (Reine)", 67.93, 13.09, 8); }
    public static Ort abisko() { return new Ort("Abisko", 68.35, 18.83, 10); }
    public static Ort reykjavik() { return new Ort("Reykjavík", 64.13, -21.90, -13); }
    public static Ort fairbanks() { return new Ort("Fairbanks", 64.84, -147.72, 15); }
    public static Ort yellowknife() { return new Ort("Yellowknife", 62.45, -114.37, 13); }
    public static Ort lituya() { return new Ort("Lituya Bay", 58.64, -137.56, 18); }
    public static Ort geiranger() { return new Ort("Geiranger", 62.10, 7.21, 4); }
    public static Ort sylt() { return new Ort("Sylt (List)", 55.02, 8.43, 3.5); }
    public static Ort hamburg() { return new Ort("Hamburg", 53.55, 9.99, 4); }
    public static Ort brocken() { return new Ort("Brocken", 51.80, 10.62, 4); }
    public static Ort hobart() { return new Ort("Hobart (Südlicht)", -42.88, 147.33, 15); }

    /** Alle Presets, von Nord nach Süd. */
    public static Ort[] alle() {
        return new Ort[]{tromsoe(), abisko(), lofoten(), fairbanks(), reykjavik(), yellowknife(), geiranger(), lituya(), sylt(), hamburg(), brocken(), hobart()};
    }
}
