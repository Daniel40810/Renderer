package com.dan.gertsch;

/**
 * Lochkamera: Auge, Blickrichtung (Gier um die Senkrechte, Neigung), waagerechter Bildwinkel, Bildgröße. Welt mit y nach
 * oben, Blick bei Gier 0 nach +z. {@link #projizieren} rechnet einen Punkt der Welt in Bildpunkte (y nach unten) und
 * Tiefe entlang des Blicks. Gertsch fotografierte frontal in das Dickicht; ohne Himmel, ohne Fluchtpunkt im Bild.
 */
public final class Kamera {
    /** Auge (m), Gier (Grad, 0 = Blick nach +z, positiv nach rechts), Neigung (Grad, negativ = nach unten). */
    public double[] auge = {0, 1.6, 0};
    public double gier = 0, neigung = -1;
    /** Waagerechter Bildwinkel (Grad). */
    public double bildwinkel = 50;
    /** Bildgröße (Bildpunkte). */
    public int breite = 1600, hoehe = 1062;
    /** Nächste Tiefe, die noch gezeichnet wird (m). */
    public double nah = 0.3;

    final double[] rechts = new double[3], oben = new double[3], vorn = new double[3];
    double brennweite;

    /** Rechnet Achsen und Brennweite aus den offenen Werten; vor {@link #projizieren} rufen (nach jeder Änderung). */
    public Kamera stellen() {
        double g = Math.toRadians(gier), n = Math.toRadians(neigung);
        vorn[0] = Math.sin(g) * Math.cos(n);
        vorn[1] = Math.sin(n);
        vorn[2] = Math.cos(g) * Math.cos(n);
        // rechts = oben(Welt) × vorn, waagerecht
        double rx = vorn[2], rz = -vorn[0], l = Math.hypot(rx, rz);
        rechts[0] = rx / l;
        rechts[1] = 0;
        rechts[2] = rz / l;
        // oben = vorn × rechts
        oben[0] = vorn[1] * rechts[2] - vorn[2] * rechts[1];
        oben[1] = vorn[2] * rechts[0] - vorn[0] * rechts[2];
        oben[2] = vorn[0] * rechts[1] - vorn[1] * rechts[0];
        brennweite = breite / 2.0 / Math.tan(Math.toRadians(bildwinkel) / 2);
        return this;
    }

    /** Brennweite in Bildpunkten (nach {@link #stellen}). */
    public double brennweite() { return brennweite; }

    /** Tiefe eines Punkts entlang des Blicks (m). */
    public double tiefe(double x, double y, double z) {
        return (x - auge[0]) * vorn[0] + (y - auge[1]) * vorn[1] + (z - auge[2]) * vorn[2];
    }

    /**
     * Punkt der Welt → {Bild x, Bild y, Tiefe} in {@code o}. Gibt false zurück, wenn der Punkt näher als {@link #nah}
     * oder hinter dem Auge liegt.
     */
    public boolean projizieren(double x, double y, double z, double[] o) {
        double dx = x - auge[0], dy = y - auge[1], dz = z - auge[2];
        double t = dx * vorn[0] + dy * vorn[1] + dz * vorn[2];
        if (t < nah) return false;
        double px = dx * rechts[0] + dy * rechts[1] + dz * rechts[2], py = dx * oben[0] + dy * oben[1] + dz * oben[2];
        o[0] = breite / 2.0 + brennweite * px / t;
        o[1] = hoehe / 2.0 - brennweite * py / t;
        o[2] = t;
        return true;
    }

    /** Richtung des Strahls durch den Bildpunkt (x, y) (Einheitsvektor) in {@code o}. */
    public void strahl(double x, double y, double[] o) {
        double px = (x - breite / 2.0) / brennweite, py = (hoehe / 2.0 - y) / brennweite;
        double dx = vorn[0] + px * rechts[0] + py * oben[0], dy = vorn[1] + px * rechts[1] + py * oben[1], dz = vorn[2] + px * rechts[2] + py * oben[2];
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        o[0] = dx / l;
        o[1] = dy / l;
        o[2] = dz / l;
    }

    /** Liegt der Punkt (mit Rand in Bildpunkten) im Bild? */
    public boolean imBild(double x, double y, double z, double rand, double[] tmp) {
        if (!projizieren(x, y, z, tmp)) return false;
        return tmp[0] > -rand && tmp[0] < breite + rand && tmp[1] > -rand && tmp[1] < hoehe + rand;
    }
}
