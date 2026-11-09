package com.dan.aurora.demo;

import com.dan.aurora.Aurora;
import com.dan.aurora.Emission;
import com.dan.aurora.Rauschen;
import com.dan.aurora.Spektrum;

import java.awt.image.BufferedImage;
import java.util.stream.IntStream;

/**
 * Die Testszene der Demo: eine Bucht im Winter bei Nacht. Hinten eine verschneite Bergkette, vorn Wasser, das den
 * Himmel spiegelt, darüber Sterne und das Polarlicht aus {@link Aurora}. Alles wird auf dem Prozessor je Bildpunkt
 * gerechnet, in cd/m², und erst zuletzt für die Kamera oder das Auge in Anzeigewerte gewandelt:
 * <ul>
 * <li>Himmel: Polarlicht (kR je Linie → lineares sRGB), Nachthimmel 2·10⁻⁴ cd/m² mit van-Rhijn-Aufhellung zum Horizont,
 * 2 600 Sterne nach der Häufigkeit ihrer Größenklassen (ein Stern 0. Größe gibt 2,5 µlx), alle mit der Extinktion der Luft.</li>
 * <li>Berge: Albedo Schnee 0,75 und Fels 0,12, beleuchtet vom Himmel und von der Bestrahlungsstärke des Polarlichts,
 * die einmal je Bild über den Himmel integriert wird. So färbt ein heller Vorhang den Schnee grün.</li>
 * <li>Wasser: Spiegelung nach Fresnel (Schlick), kleine Wellen als Steigungsrauschen, die die Spiegelung in die Länge ziehen.</li>
 * </ul>
 * Die Szene kennt kein anderes Paket; sie ist nur für die Demo da.
 */
public final class AuroraSzene {
    /** Wie das Licht gezeigt wird: Kamera mit Belichtungszeit (f/2,8, ISO 3200) oder das dunkeladaptierte Auge. */
    public enum Sicht {
        KAMERA_2("Kamera 2 s · f/2,8 · ISO 3200", 2), KAMERA_8("Kamera 8 s · f/2,8 · ISO 3200", 8), KAMERA_20("Kamera 20 s · f/2,8 · ISO 3200", 20), AUGE("Auge, dunkeladaptiert", 0);
        public final String titel;
        public final double sekunden;

        Sicht(String t, double s) { titel = t; sekunden = s; }

        @Override public String toString() { return titel; }
    }

    /** Blick: Azimut (Grad, 0 = Nord, 90 = Ost) und Höhe der Bildmitte, waagerechtes Blickfeld in Grad, Augenhöhe über dem Wasser in m. */
    public double azimut = 0, hoehe = 18, fov = 95, augenhoehe = 2;
    /** Steigung der Wellen (Standardabweichung) und Leuchtdichte des Nachthimmels im Zenit in cd/m² (21,8 mag/arcsec²). */
    public double wellen = 0.03, himmel = 2.0e-4;
    /** Leuchtdichte, die dem Auge als Weiß erscheint (cd/m²). */
    public double anpassung = 0.3;
    public Sicht sicht = Sicht.KAMERA_8;
    public long seed = 7;
    public boolean berge = true, wasser = true, sterne = true;
    public double blende = 2.8, iso = 3200;

    /** Das Bild und Kennzahlen. */
    public static final class Ergebnis {
        public BufferedImage bild;
        public double sekunden, maxLeuchtdichte;
        public final double[] maxKr = new double[Emission.LINIEN];
        /** Bestrahlungsstärke des Polarlichts auf waagerechtem Boden in lx. */
        public double bestrahlung;
    }

    private Sterne sternbild;
    private long sternSeed = Long.MIN_VALUE;

    /** Malt die Szene mit dem (vorbereiteten) Polarlicht, Breite w, Höhe 2/3 davon. */
    public Ergebnis malen(Aurora a, int w) {
        long t0 = System.nanoTime();
        int h = Math.max(1, Math.round(w * 2 / 3f));
        if (sternbild == null || sternSeed != seed) {
            sternbild = new Sterne(seed);
            sternSeed = seed;
        }
        Sterne st = sternbild;
        // Bestrahlung durch das Polarlicht: Integral über den Himmel, einmal je Bild
        double[] eAurora = new double[3];
        {
            double[] kr = new double[Emission.LINIEN], rgb = new double[3];
            int nAz = 72, nEl = 12;
            for (int i = 0; i < nAz; i++)
                for (int j = 0; j < nEl; j++) {
                    double az = 2 * Math.PI * (i + 0.5) / nAz, el = 0.5 * Math.PI * (j + 0.5) / nEl;
                    a.strahlung(Math.sin(az) * Math.cos(el), Math.cos(az) * Math.cos(el), Math.sin(el), kr);
                    Spektrum.rgb(kr, rgb);
                    double dOmega = Math.cos(el) * (0.5 * Math.PI / nEl) * (2 * Math.PI / nAz);
                    for (int c = 0; c < 3; c++) eAurora[c] += rgb[c] * Math.sin(el) * dOmega;
                }
        }
        double eHimmel = Math.PI * himmel * 1.3 + 2e-4;
        double[] eBoden = {eAurora[0] + eHimmel, eAurora[1] + eHimmel, eAurora[2] + eHimmel};

        double az0 = Math.toRadians(azimut), el0 = Math.toRadians(hoehe), tanF = Math.tan(Math.toRadians(fov) / 2);
        double fx = Math.sin(az0) * Math.cos(el0), fy = Math.cos(az0) * Math.cos(el0), fz = Math.sin(el0);
        double rx = Math.cos(az0), ry = -Math.sin(az0);
        double ux = ry * fz, uy = -rx * fz, uz = rx * fy - ry * fx;
        double pixelWinkel = 2 * tanF / w;
        int[] pix = new int[w * h];
        double[][] zeilenMax = new double[h][Emission.LINIEN + 1];
        IntStream.range(0, h).parallel().forEach(y -> {
            double[] kr = new double[Emission.LINIEN], rgb = new double[3], tmp = new double[3], max = zeilenMax[y];
            for (int x = 0; x < w; x++) {
                double sx = (2 * (x + 0.5) / w - 1) * tanF, sy = (1 - 2 * (y + 0.5) / h) * tanF * h / (double) w;
                double dx = fx + sx * rx + sy * ux, dy = fy + sx * ry + sy * uy, dz = fz + sy * uz;
                double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= n; dy /= n; dz /= n;
                if (dz >= 0 || !wasser) {
                    double azG = Math.toDegrees(Math.atan2(dx, dy)), elG = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dz))));
                    double kamm = berge ? kammFern(azG) : -90, nah = berge ? kammNah(azG) : -90;
                    if (elG < nah) berg(azG, elG, true, eBoden, rgb);
                    else if (elG < kamm) berg(azG, elG, false, eBoden, rgb);
                    else {
                        himmel(a, st, dx, dy, dz, pixelWinkel * 0.8, kr, rgb, tmp);
                        for (int l = 0; l < Emission.LINIEN; l++) max[l] = Math.max(max[l], kr[l]);
                        max[Emission.LINIEN] = Math.max(max[Emission.LINIEN], 0.2126 * tmp[0] + 0.7152 * tmp[1] + 0.0722 * tmp[2]);
                    }
                } else {
                    // Wasser: Treffpunkt, Wellen, Spiegelung
                    double t = augenhoehe / -dz, px = dx * t, py = dy * t;
                    double fade = 1 / (1 + t / 400);
                    double sxw = wellen * fade * 2 * Rauschen.fbm2(seed, px / 1.5, py / 1.5, 3, 0.5, 20);
                    double syw = wellen * fade * 2 * Rauschen.fbm2(seed, px / 1.5 + 31, py / 1.5 - 17, 3, 0.5, 21);
                    double nn = Math.sqrt(sxw * sxw + syw * syw + 1);
                    double nx = -sxw / nn, ny = -syw / nn, nz = 1 / nn;
                    double dn = dx * nx + dy * ny + dz * nz;
                    double gx = dx - 2 * dn * nx, gy = dy - 2 * dn * ny, gz = dz - 2 * dn * nz;
                    if (gz < 0.002) gz = 0.002;
                    double gn = Math.sqrt(gx * gx + gy * gy + gz * gz);
                    gx /= gn; gy /= gn; gz /= gn;
                    double cosI = Math.min(1, -dn), fres = 0.02 + 0.98 * Math.pow(1 - cosI, 5);
                    double azG = Math.toDegrees(Math.atan2(gx, gy)), elG = Math.toDegrees(Math.asin(gz));
                    double kamm = berge ? kammFern(azG) : -90, nah = berge ? kammNah(azG) : -90;
                    if (elG < nah) berg(azG, elG, true, eBoden, rgb);
                    else if (elG < kamm) berg(azG, elG, false, eBoden, rgb);
                    else himmel(a, st, gx, gy, gz, pixelWinkel * (0.8 + 40 * wellen * fade), kr, rgb, tmp);
                    for (int c = 0; c < 3; c++) rgb[c] *= fres;
                }
                pix[y * w + x] = farbe(rgb);
            }
        });
        Ergebnis e = new Ergebnis();
        e.bild = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        e.bild.setRGB(0, 0, w, h, pix, 0, w);
        for (double[] m : zeilenMax) {
            for (int l = 0; l < Emission.LINIEN; l++) e.maxKr[l] = Math.max(e.maxKr[l], m[l]);
            e.maxLeuchtdichte = Math.max(e.maxLeuchtdichte, m[Emission.LINIEN]);
        }
        e.bestrahlung = 0.2126 * eAurora[0] + 0.7152 * eAurora[1] + 0.0722 * eAurora[2];
        e.sekunden = (System.nanoTime() - t0) / 1e9;
        return e;
    }

    /** Himmel in einer Richtung: Polarlicht + Nachthimmel + Sterne, lineares sRGB in cd/m²; {@code aurora} erhält das Polarlicht allein. */
    void himmel(Aurora a, Sterne st, double dx, double dy, double dz, double sigma, double[] kr, double[] rgb, double[] aurora) {
        a.strahlung(dx, dy, dz, kr);
        Spektrum.rgb(kr, aurora);
        double el = Math.max(0, dz);
        double grund = himmel * (1 + 1.2 * (1 - el));
        rgb[0] = aurora[0] + grund * 0.82;
        rgb[1] = aurora[1] + grund * 1.06;
        rgb[2] = aurora[2] + grund * 0.92;
        if (sterne) {
            double durchlass = a.durchlass(Emission.GRUEN, Math.toDegrees(Math.asin(Math.min(1, el))));
            st.strahlung(dx, dy, dz, sigma, durchlass, rgb);
        }
    }

    /** Berg in einer Richtung: Schnee oder Fels im Licht von Himmel und Polarlicht, lineares sRGB in cd/m². */
    void berg(double azG, double elG, boolean nah, double[] eBoden, double[] rgb) {
        double c = Math.cos(Math.toRadians(azG)), s = Math.sin(Math.toRadians(azG));
        double schatten = 0.55 + 0.45 * (0.5 + 0.5 * Rauschen.fbm2(seed, 14 * c + elG * 1.7, 14 * s - elG * 1.3, 3, 0.5, nah ? 33 : 31));
        double albedo;
        if (nah) albedo = 0.08;
        else {
            double schnee = glatt(0.25, 0.65, 0.5 + 0.5 * Rauschen.fbm2(seed, 9 * c + elG * 0.8, 9 * s + elG * 0.6, 3, 0.5, 32)) * glatt(0.8, 2.5, elG);
            albedo = 0.12 + (0.75 - 0.12) * schnee;
        }
        for (int i = 0; i < 3; i++) rgb[i] = albedo / Math.PI * eBoden[i] * schatten;
    }

    /** Höhenwinkel der fernen Kette in Grad nach dem Azimut, periodisch. */
    double kammFern(double azG) {
        double c = Math.cos(Math.toRadians(azG)), s = Math.sin(Math.toRadians(azG));
        double g = 1 - Math.abs(Rauschen.fbm2(seed, 5.5 * c + 10, 5.5 * s + 10, 4, 0.5, 30));
        double fein = Rauschen.fbm2(seed, 18 * c, 18 * s, 3, 0.5, 34);
        return 0.6 + 5.2 * Math.pow(g, 1.8) + 0.5 * fein;
    }

    /** Höhenwinkel der nahen Felsen am Ufer (nur seitlich des Blicks), Grad. */
    double kammNah(double azG) {
        double d = Math.abs(((azG - azimut) % 360 + 540) % 360 - 180);
        double seite = glatt(18, 40, d);
        if (seite <= 0) return -90;
        double c = Math.cos(Math.toRadians(azG)), s = Math.sin(Math.toRadians(azG));
        double g = 1 - Math.abs(Rauschen.fbm2(seed, 12 * c + 3, 12 * s + 3, 4, 0.55, 35));
        return -0.8 + seite * (2.6 * Math.pow(g, 1.5));
    }

    static double glatt(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Anzeigewert aus linearem sRGB in cd/m² nach der gewählten Sicht. */
    int farbe(double[] rgb) {
        if (sicht == Sicht.AUGE) return Spektrum.auge(rgb[0], rgb[1], rgb[2], anpassung);
        return Spektrum.kamera(rgb[0], rgb[1], rgb[2], sicht.sekunden, iso, blende);
    }

    // ------------------------------------------------------------------ Sterne

    /** Ein Sternhimmel aus dem seed: 2 600 Sterne über dem Horizont, Größenklassen nach ihrer Häufigkeit (×3,2 je Klasse), Farben nach B−V. */
    static final class Sterne {
        final int n = 2600;
        final double[] az = new double[n], el = new double[n], lux = new double[n], r = new double[n], g = new double[n], b = new double[n];
        final int[][] zellen = new int[360 * 90][];

        Sterne(long seed) {
            int[] zahl = new int[360 * 90];
            for (int i = 0; i < n; i++) {
                az[i] = 2 * Math.PI * Rauschen.hash(seed, i, 1, 40);
                el[i] = Math.asin(Rauschen.hash(seed, i, 2, 40));
                double u = Math.max(1e-6, Rauschen.hash(seed, i, 3, 40));
                double m = Math.max(-1.5, 6.5 + 2 * Math.log10(u));
                lux[i] = 2.54e-6 * Math.pow(10, -0.4 * m);
                double bv = 0.6 + 0.45 * (Rauschen.hash(seed, i, 4, 40) + Rauschen.hash(seed, i, 5, 40) - 1) * 2;
                double rr = Math.max(0.3, 1 + 0.45 * (bv - 0.6)), bb = Math.max(0.3, 1 - 0.45 * (bv - 0.6)), gg = 1;
                double y = 0.2126 * rr + 0.7152 * gg + 0.0722 * bb;
                r[i] = rr / y; g[i] = gg / y; b[i] = bb / y;
                zahl[zelle(az[i], el[i])]++;
            }
            for (int z = 0; z < zellen.length; z++) zellen[z] = new int[zahl[z]];
            int[] k = new int[zellen.length];
            for (int i = 0; i < n; i++) {
                int z = zelle(az[i], el[i]);
                zellen[z][k[z]++] = i;
            }
        }

        static int zelle(double az, double el) {
            int ia = (int) Math.floor(Math.toDegrees(az)) % 360, ie = (int) Math.floor(Math.toDegrees(el));
            if (ia < 0) ia += 360;
            return Math.min(89, Math.max(0, ie)) * 360 + ia;
        }

        /** Addiert die Sterne um die Richtung: jeder als Gaußfleck der Breite sigma (rad), Leuchtdichte = Beleuchtungsstärke · Fleckdichte. */
        void strahlung(double dx, double dy, double dz, double sigma, double durchlass, double[] rgb) {
            double a0 = Math.atan2(dx, dy), e0 = Math.asin(Math.min(1, dz));
            if (a0 < 0) a0 += 2 * Math.PI;
            int ia = (int) Math.floor(Math.toDegrees(a0)), ie = (int) Math.floor(Math.toDegrees(e0));
            double k = 1 / (2 * Math.PI * sigma * sigma), s2 = 2 * sigma * sigma, ce = Math.cos(e0);
            for (int de = -1; de <= 1; de++) {
                int je = ie + de;
                if (je < 0 || je > 89) continue;
                for (int da = -1; da <= 1; da++) {
                    int ja = ((ia + da) % 360 + 360) % 360;
                    for (int i : zellen[je * 360 + ja]) {
                        double dAz = az[i] - a0;
                        if (dAz > Math.PI) dAz -= 2 * Math.PI;
                        if (dAz < -Math.PI) dAz += 2 * Math.PI;
                        double dEl = el[i] - e0, q = dEl * dEl + dAz * dAz * ce * ce;
                        if (q > 12 * sigma * sigma) continue;
                        double w = lux[i] * k * Math.exp(-q / s2) * durchlass;
                        rgb[0] += w * r[i];
                        rgb[1] += w * g[i];
                        rgb[2] += w * b[i];
                    }
                }
            }
        }
    }
}
