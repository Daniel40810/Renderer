package com.dan.lens;

/**
 * Die Irisblende: n Lamellen (0 = kreisrund), wie rund ihre Kanten sind und wie die Blende gedreht ist. Die Form
 * bestimmt drei Dinge im Bild: die Form der Unschärfekreise (Bokeh), die der Geisterbilder (jedes ist ein Bild der Blende)
 * und den Stern um die Sonne. Gerade Kanten beugen das Licht senkrecht zu sich in zwei Strahlen; bei gerader Lamellenzahl
 * fallen die Strahlen gegenüberliegender Kanten zusammen (n Strahlen), bei ungerader nicht (2n).
 * <p>Maß: der Umkreis hat den Radius 1 (die Spitzen der Lamellen); {@link #areaFactor()} ist die Fläche durch π. Die
 * Blendenzahl bezieht sich auf die Fläche, also auf einen Kreis gleicher Fläche (so belichtet eine Sechseckblende wie ein
 * Kreis derselben Blendenzahl).
 * <p>Gerundete Kanten [G]: r(φ) = r_n(φ) + rund · (1 − r_n(φ)), zwischen dem Vieleck (0) und dem Kreis (1). Moderne
 * Objektive haben 7 bis 11 gerundete Lamellen; die Klassiker der 1950er 5 bis 16, oft gerade.
 */
public final class Aperture {
    /** Zahl der Lamellen (0 oder unter 3: Kreis). */
    public volatile int blades = 8;
    /** 0 = gerade Kanten (Vieleck), 1 = Kreis. */
    public volatile double roundness = 0.35;
    /** Drehung der Blende (Grad). */
    public volatile double rotationDeg = 0;

    public Aperture() { }

    public Aperture(int blades, double roundness, double rotationDeg) {
        this.blades = blades;
        this.roundness = roundness;
        this.rotationDeg = rotationDeg;
    }

    public boolean round() { return blades < 3 || roundness >= 0.999; }

    /** Radius des Rands in Richtung φ (rad), Umkreis 1. */
    public double radius(double phi) {
        if (blades < 3) return 1;
        double seg = 2 * Math.PI / blades;
        double a = phi - Math.toRadians(rotationDeg);
        a = a - seg * Math.floor(a / seg);
        double rn = Math.cos(Math.PI / blades) / Math.cos(a - Math.PI / blades);
        return rn + roundness * (1 - rn);
    }

    public boolean inside(double x, double y) {
        double r = Math.hypot(x, y);
        return r <= radius(Math.atan2(y, x));
    }

    /** Fläche durch π (Kreis 1, Sechseck mit geraden Kanten 0,827). */
    public double areaFactor() {
        if (blades < 3) return 1;
        int n = 2048;
        double s = 0;
        for (int i = 0; i < n; i++) { double r = radius(2 * Math.PI * (i + 0.5) / n); s += r * r; }
        return s / n;   // ∫ r²/2 dφ / π
    }

    /** Länge einer geraden Kante (Umkreis 1); mit runden Kanten nur der gerade Anteil [G]. */
    public double straightEdge() {
        if (blades < 3) return 0;
        return 2 * Math.sin(Math.PI / blades) * Math.max(0, 1 - roundness);
    }

    /** Richtungen der Beugungsstrahlen (rad): senkrecht zu den Kanten, je Kante beide Richtungen. */
    public double[] spikeAngles() {
        if (blades < 3 || straightEdge() <= 1e-6) return new double[0];
        int n = blades, k = n % 2 == 0 ? n : 2 * n;
        double[] a = new double[k];
        double rot = Math.toRadians(rotationDeg);
        for (int i = 0; i < k; i++) a[i] = rot + Math.PI / n + (n % 2 == 0 ? 2 * Math.PI * i / n : Math.PI * i / n);
        return a;
    }

    /** Kanten je Strahl: 2 bei gerader Zahl (zwei parallele Kanten, im Mittel doppelte Energie), sonst 1. */
    public int edgesPerSpike() { return blades % 2 == 0 ? 2 : 1; }

    /** Die Blende als Maske size × size (Umkreis füllt das Bild), mit 4 × 4 Unterabtastung je Bildpunkt. */
    public float[] mask(int size) {
        float[] m = new float[size * size];
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++) {
                int in = 0;
                for (int sj = 0; sj < 4; sj++)
                    for (int si = 0; si < 4; si++) {
                        double x = (i + (si + 0.5) / 4) / size * 2 - 1, y = (j + (sj + 0.5) / 4) / size * 2 - 1;
                        if (inside(x, y)) in++;
                    }
                m[j * size + i] = in / 16f;
            }
        return m;
    }

    public Aperture copy() { return new Aperture(blades, roundness, rotationDeg); }

    @Override public String toString() {
        return blades < 3 ? "kreisrund" : blades + " Lamellen" + (roundness > 0.01 ? String.format(java.util.Locale.GERMAN, ", gerundet %.0f %%", roundness * 100) : ", gerade");
    }
}
