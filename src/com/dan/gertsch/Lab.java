package com.dan.gertsch;

/**
 * CIELAB (Weißpunkt D65, 2°-Beobachter) aus sRGB und zurück. Die Messung rechnet in Lab, weil dort gleiche Abstände
 * ungefähr gleich große sichtbare Unterschiede sind (ΔE*ab nach CIE 1976) und die Helligkeit L* (0 schwarz, 100 weiß)
 * von der Farbe (a* grün–rot, b* blau–gelb) getrennt ist. Formeln nach CIE 15:2004, sRGB nach IEC 61966-2-1.
 */
public final class Lab {
    private Lab() { }

    /** Weißpunkt D65 in XYZ (Y = 1). */
    static final double XN = 0.95047, YN = 1.0, ZN = 1.08883;
    static final double E = 216.0 / 24389.0, K = 24389.0 / 27.0;
    private static final double[] LIN = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            LIN[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
    }

    /** sRGB-Wert 0..255 → linear 0..1 (Tabelle). */
    public static double linear(int v8) { return LIN[v8 & 255]; }

    /** linear 0..1 → sRGB 0..255, begrenzt. */
    public static int srgb8(double c) {
        c = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(Math.max(0, c), 1 / 2.4) - 0.055;
        return (int) Math.max(0, Math.min(255, Math.round(c * 255)));
    }

    private static double f(double t) { return t > E ? Math.cbrt(t) : (K * t + 16) / 116; }

    private static double fInv(double t) { double t3 = t * t * t; return t3 > E ? t3 : (116 * t - 16) / K; }

    /** Lineares RGB (0..1) → L*, a*, b* in {@code o[0..2]}. */
    public static void vonLinear(double r, double g, double b, double[] o) {
        double x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b;
        double fx = f(x / XN), fy = f(y / YN), fz = f(z / ZN);
        o[0] = 116 * fy - 16;
        o[1] = 500 * (fx - fy);
        o[2] = 200 * (fy - fz);
    }

    /** sRGB (0xRRGGBB, Alpha egal) → {L*, a*, b*}. */
    public static double[] vonRgb(int rgb) {
        double[] o = new double[3];
        vonLinear(linear(rgb >> 16), linear(rgb >> 8), linear(rgb), o);
        return o;
    }

    /** L*, a*, b* → lineares RGB in {@code o[0..2]} (nicht begrenzt, kann außerhalb von sRGB liegen). */
    public static void zuLinear(double l, double a, double b, double[] o) {
        double fy = (l + 16) / 116, fx = fy + a / 500, fz = fy - b / 200;
        double x = XN * fInv(fx), y = YN * (l > K * E ? fy * fy * fy : l / K), z = ZN * fInv(fz);
        o[0] = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z;
        o[1] = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z;
        o[2] = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z;
    }

    /** L*, a*, b* → sRGB 0xRRGGBB (Farben außerhalb von sRGB je Kanal begrenzt). */
    public static int zuRgb(double l, double a, double b) {
        double[] o = new double[3];
        zuLinear(l, a, b, o);
        return (srgb8(o[0]) << 16) | (srgb8(o[1]) << 8) | srgb8(o[2]);
    }

    /** ΔE*ab (CIE 1976) zwischen zwei Lab-Farben. */
    public static double deltaE(double[] p, double[] q) {
        double dl = p[0] - q[0], da = p[1] - q[1], db = p[2] - q[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /** Buntheit C*ab. */
    public static double buntheit(double a, double b) { return Math.hypot(a, b); }

    /** Farbton h_ab in Grad 0..360 (0 = +a* rot, 90 = +b* gelb, 180 = −a* grün, 270 = −b* blau). */
    public static double farbton(double a, double b) {
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
    }
}
