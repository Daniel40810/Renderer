package com.dan.surge;

/**
 * Die Front einer Riesenwelle (Tsunami in einem Fjord, Schwall nach einem Bergsturz, Dammbruch) als Bild, aus einer
 * Flutkarte (aus LituyaBay1958, Werkbuch Welle W3b bis W5). Hält die Lippe ({@link Lip}) und die Schleier am Auflauf
 * ({@link Veil}) mit ihren Einstellungen; die Bausteine für Eckenformer, Bildpunktformer und Strahlengang des Wirts liegen
 * in {@code gl/} ({@code surge_front.glsl}, {@code surge_surface.glsl}, {@code surge_spray.glsl}).
 * <p>
 * Qualitätsstufen regeln Abstand und Umkreis der Tropfen, den Umkreis der Lippe und die Fäden je Randpunkt. Vorgabe ist
 * {@code HIGH}, wie in LituyaBay1958.
 */
public final class Surge {
    public enum Quality {
        LOW(1.0, 1200, 2500, 16), MEDIUM(0.75, 1700, 3500, 24), HIGH(0.5, 2200, 4500, 40), ULTRA(0.35, 3000, 6000, 60);
        public final double dropSpacing, dropRange, lipRange;
        public final int threadsPerPoint;
        Quality(double s, double r, double l, int t) { dropSpacing = s; dropRange = r; lipRange = l; threadsPerPoint = t; }
    }

    public final Lip lip = new Lip();
    public final Veil veil = new Veil();
    private Quality quality = Quality.HIGH;

    public Quality quality() { return quality; }

    public Surge quality(Quality q) {
        quality = q;
        lip.dropSpacing = q.dropSpacing;
        lip.dropRange = q.dropRange;
        lip.range = q.lipRange;
        veil.perPoint = q.threadsPerPoint;
        return this;
    }

    /** Die Einstellungen von LituyaBay1958 (Riesenwelle von 1958, Wellen 8–80 m in der Bucht, Auflauf bis 524 m). */
    public static Surge lituya1958() { return new Surge().quality(Quality.HIGH); }
}
