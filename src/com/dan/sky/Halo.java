package com.dan.sky;

/**
 * Halos an Eiskristallen (seit Brocken Phase 7): der 22°-Ring an regellos liegenden Säulen und die Nebensonnen an
 * waagerecht schwebenden Plättchen. Beide entstehen beim Durchgang durch zwei Seitenflächen eines Sechseckprismas, die
 * einen Winkel von 60° bilden; die kleinste Ablenkung ist D = 2 · asin(n · sin 30°) − 60°. Für Nebensonnen gilt statt n
 * der Index nach Bravais für schräge Strahlen, n' = √(n² − sin² e) / cos e (e = Sonnenhöhe): mit der Sonne rücken sie
 * vom 22°-Ring nach außen, ab rund 61° gibt es keine mehr.
 * <p>
 * Brechungsindex von Eis aus {@link Mie#iceIndex}: rot innen (1,307 bei 656 nm), blau außen (1,317 bei 450 nm). Die
 * Helligkeit malt {@code gl/sky.frag} ({@code halo(d)}) nach diesen Winkeln. Nur Java.
 */
public final class Halo {
    private Halo() { }

    /** Wellenlängen (µm) der drei Kanäle für die Lage der Ränder. */
    public static final double RED = 0.656, GREEN = 0.550, BLUE = 0.450;

    /** Kleinste Ablenkung an einem 60°-Prisma (Grad) mit dem Index n; NaN, wenn kein Strahl durchkommt. */
    public static double minDeviationDeg(double n) {
        double s = n * 0.5;
        return s >= 1 ? Double.NaN : 2 * Math.toDegrees(Math.asin(s)) - 60;
    }

    /** Halbmesser des 22°-Rings (Grad) für eine Wellenlänge (µm): sein scharfer Innenrand. */
    public static double ringDeg(double lambdaUm) { return minDeviationDeg(Mie.iceIndex(lambdaUm)); }

    /**
     * Abstand der Nebensonnen von der Sonne im Azimut (Grad) bei der Sonnenhöhe {@code sunAltDeg}, rot: 21,6° an der
     * Kimm, 24,4° bei 20°, 36,0° bei 40° (als Winkel am Himmel 21,6°, 22,9°, 27,4°); NaN ab rund 61°.
     */
    public static double parhelionDeg(double sunAltDeg, double lambdaUm) {
        double e = Math.toRadians(sunAltDeg), n = Mie.iceIndex(lambdaUm);
        if (Math.abs(sunAltDeg) >= 89) return Double.NaN;
        double nb = Math.sqrt(n * n - Math.sin(e) * Math.sin(e)) / Math.cos(e);
        return minDeviationDeg(nb);
    }

    /**
     * Uniform für den Shader: Ring (rot, grün, blau) und Nebensonnen (rot, grün, blau) in Grad, bei Sonnenhöhe
     * {@code sunAltDeg}; Nebensonnen 0, wenn es keine gibt.
     */
    public static double[] shaderAngles(double sunAltDeg) {
        double[] a = new double[6];
        double[] l = {RED, GREEN, BLUE};
        for (int c = 0; c < 3; c++) {
            a[c] = ringDeg(l[c]);
            double p = parhelionDeg(sunAltDeg, l[c]);
            a[3 + c] = Double.isNaN(p) ? 0 : p;
        }
        return a;
    }
}
