package com.dan.surface;

import java.util.Locale;

/**
 * Ein Material: wie es heißt, wie groß eine Kachel in der Welt ist, wie es gelegt wird und woher die Karten kommen.
 * Die Karten selbst (Farbe, Normale, Rauheit, Verdeckung, Höhe) liegen gebacken in einem Ordner ({@link Bake},
 * {@link MaterialSet}).
 * <p>
 * Tönung: Die Aufbereitung multipliziert die Farbe (linear) mit {@link #tint}, mischt sie um {@link #desaturate} zum Grau
 * und hebt sie um {@link #brighten} an; so wird aus einem Seeeis Gletschereis oder aus altem Holz silbergraues Treibholz.
 * Alle Werte sind öffentlich und lassen sich ändern (Paketvertrag Punkt 5).
 */
public final class Material {
    /** Kurzname, zugleich Ordner in daten/texturen und Schicht im Texturfeld. */
    public final String id;
    public String name;
    /** Kantenlänge einer Kachel in der Welt (m). */
    public double size;
    /** Zufällig drehen beim Hex-Kacheln (nein bei gerichteten Mustern: Rinde, Bretter, Tau). */
    public boolean rotate;
    /** An steilen Flächen dreiseitig projizieren (Fels, Eis, Rinde). */
    public boolean triplanar;
    /** Höhe der Prägung in m (für das Mischen über die Höhe und die Normale). */
    public double heightScale;
    /** Tönung (linear, je Kanal), Entsättigung 0 … 1, Aufhellung (Faktor). */
    public double[] tint = {1, 1, 1};
    public double desaturate, brighten = 1;
    /** Wie sehr Nässe dunkelt (poröse Stoffe stark, Eis kaum) und wie glatt sie macht. */
    public double wetDarken = 0.45;
    /** Quelle: Anbieter, Kennung des Satzes, Adresse, Lizenz; oder abgeleitet aus einem anderen Material. */
    public String provider, assetId, url, license;
    public String derivedFrom;

    public Material(String id, String name, double size) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.rotate = true;
        this.heightScale = size * 0.02;
    }

    /** CC0-Satz von ambientCG (2K, JPG). */
    public Material ambientCG(String asset) {
        provider = "ambientCG";
        assetId = asset;
        url = "https://ambientcg.com/view?id=" + asset;
        license = "CC0 1.0";
        return this;
    }

    public Material derived(String from) {
        derivedFrom = from;
        provider = "abgeleitet";
        license = "wie " + from;
        return this;
    }

    public Material tint(double r, double g, double b) { tint = new double[]{r, g, b}; return this; }
    public Material desaturate(double d) { desaturate = d; return this; }
    public Material brighten(double k) { brighten = k; return this; }
    public Material fixed() { rotate = false; return this; }
    public Material triplanar() { triplanar = true; return this; }
    public Material height(double m) { heightScale = m; return this; }
    public Material wet(double darken) { wetDarken = darken; return this; }

    /** Ob die Tönung etwas ändert. */
    public boolean tinted() { return tint[0] != 1 || tint[1] != 1 || tint[2] != 1 || desaturate != 0 || brighten != 1; }

    /** „strandkies: Strandkies und Gerölle, 1,5 m, ambientCG Gravel041 (CC0 1.0)“ */
    public String describe() {
        return String.format(Locale.GERMAN, "%s: %s, %s m%s%s, %s", id, name, fmt(size), rotate ? "" : ", fest", triplanar ? ", dreiseitig" : "",
                derivedFrom != null ? "abgeleitet aus " + derivedFrom : provider + " " + assetId + " (" + license + ")");
    }

    static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.GERMAN, "%.1f", v); }

    @Override public String toString() { return id; }
}
