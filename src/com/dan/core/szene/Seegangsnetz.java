package com.dan.core.szene;

import com.dan.core.gitter.Gitter;

/**
 * Ein Gitter des Seegangs für das Bild (P8b, Werkbuch HalligHooge Teil A, 12.9, F76 A): die Kennwerte aus
 * {@code waves.*} in zwei Ständen, wie {@link Spiegel} für das Wasser (das Bild liegt linear zwischen ihnen; ein
 * jüngerer Stand ersetzt den älteren, ein früherer setzt neu an, gleiche Zeit ersetzt den jüngsten).
 * <p>
 * Je Platz und Zelle sechs Werte ({@link #FELDER}): Hs (m), Tp (s), die Richtung, wohin die Wellen laufen, als
 * Einheitsvektor im Gitter (cos, sin; 0 = Ost, gegen den Uhrzeigersinn), mss und diss (W/m²). Die Richtung kommt als
 * {@code waves.dir} (woher, von Gitternord im Uhrzeigersinn) und wird hier zum Vektor, damit das Bild sie bilinear
 * mitteln kann.
 */
public final class Seegangsnetz {
    public static final int FELDER = 6;
    public final Gitter gitter;
    public final int zellen;
    /** Werte je Platz: [Platz][Feld · zellen + Zelle]. */
    public final float[][] werte = new float[2][];
    final double[] zeit = {Double.NaN, Double.NaN};
    /** Platz des jüngeren Stands (−1: noch keiner). */
    public int platzB = -1;
    /** Was seit dem letzten Hochladen neu ist (Bit 0, 1: Plätze). */
    public int neu;

    public Seegangsnetz(Gitter gitter) {
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Seegang: nur Gitter in UTM, nicht " + gitter);
        this.gitter = gitter;
        zellen = gitter.zellen();
        werte[0] = new float[FELDER * zellen];
        werte[1] = new float[FELDER * zellen];
    }

    /** Ein Stand: {@code waves.hs}, {@code .tp}, {@code .dir} (woher, Grad), {@code .mss}, {@code .diss}. */
    public synchronized void stand(double t, float[] hs, float[] tp, float[] dir, float[] mss, float[] diss) {
        int p;
        boolean beide = false;
        if (platzB < 0 || t < zeit[platzB]) {
            p = 0;
            beide = true;
        } else if (t == zeit[platzB]) p = platzB;
        else p = 1 - platzB;
        schreibe(p, t, hs, tp, dir, mss, diss);
        if (beide) schreibe(1, t, hs, tp, dir, mss, diss);
        platzB = p;
    }

    private void schreibe(int p, double t, float[] hs, float[] tp, float[] dir, float[] mss, float[] diss) {
        float[] w = werte[p];
        int n = zellen;
        for (int c = 0; c < n; c++) {
            w[c] = hs[c];
            w[n + c] = tp[c];
            // woher (von Gitternord im Uhrzeigersinn) → wohin, mathematisch im Gitter: θ = 270° − dir
            double th = Math.toRadians(270 - dir[c]);
            w[2 * n + c] = hs[c] > 0 ? (float) Math.cos(th) : 0f;
            w[3 * n + c] = hs[c] > 0 ? (float) Math.sin(th) : 0f;
            w[4 * n + c] = mss[c];
            w[5 * n + c] = diss[c];
        }
        zeit[p] = t;
        neu |= 1 << p;
    }

    public synchronized boolean gesetzt() { return platzB >= 0; }

    /** Gewicht des jüngeren Stands zur Zeit t (0 … 1) und der Platz des älteren; {s, platzA}. */
    public synchronized double[] gewicht(double t) {
        if (platzB < 0) return new double[] {0, 0};
        int a = 1 - platzB;
        double ta = zeit[a], tb = zeit[platzB];
        double s = tb > ta ? Math.max(0, Math.min(1, (t - ta) / (tb - ta))) : 1;
        return new double[] {s, a};
    }

    /**
     * Die sechs Werte am Ort (Ost, Nord) zur Zeit t wie {@code seegang.glsl} (seeWerte): bilinear zwischen den Zellmitten
     * (am Rand der Randwert), linear in der Zeit: Hs, Tp, cos θ_m, sin θ_m (wohin, mathematisch im Gitter), mss, diss.
     * null außerhalb des Gitters oder ohne Stand (seit S3c, Seegang Werkbuch: das Fernfeld der Synthese).
     */
    public synchronized double[] wert(double ost, double nord, double t) {
        if (platzB < 0) return null;
        double ux = (ost - gitter.x0) / gitter.dx, uy = (nord - gitter.y0) / gitter.dy;
        if (ux < 0 || uy < 0 || ux >= gitter.nx || uy >= gitter.ny) return null;
        return wertU(ux, uy, t);
    }

    /**
     * Seit S3c-7 (SF33 A): die Werte wie {@link #wert}, außerhalb des Gitters vom nächsten Ort am Rand (die Randwerte).
     * null ohne Stand.
     */
    public synchronized double[] wertRand(double ost, double nord, double t) {
        if (platzB < 0) return null;
        double ux = (ost - gitter.x0) / gitter.dx, uy = (nord - gitter.y0) / gitter.dy;
        return wertU(Math.max(0, Math.min(gitter.nx, ux)), Math.max(0, Math.min(gitter.ny, uy)), t);
    }

    private double[] wertU(double ux, double uy, double t) {
        int nx = gitter.nx, ny = gitter.ny;
        double bx = Math.max(0, Math.min(nx - 1, ux - 0.5)), by = Math.max(0, Math.min(ny - 1, uy - 0.5));
        int i0 = Math.max(0, Math.min((int) bx, nx - 2)), j0 = Math.max(0, Math.min((int) by, ny - 2));
        double fx = bx - i0, fy = by - j0;
        int i1 = Math.min(i0 + 1, nx - 1), j1 = Math.min(j0 + 1, ny - 1);
        double[] g = gewicht(t), w = new double[FELDER];
        int pa = (int) g[1];
        float[] a = werte[pa], b = werte[1 - pa];
        for (int f = 0; f < FELDER; f++) {
            int o = f * zellen;
            double v00 = a[o + i0 + nx * j0] + g[0] * (b[o + i0 + nx * j0] - a[o + i0 + nx * j0]);
            double v10 = a[o + i1 + nx * j0] + g[0] * (b[o + i1 + nx * j0] - a[o + i1 + nx * j0]);
            double v01 = a[o + i0 + nx * j1] + g[0] * (b[o + i0 + nx * j1] - a[o + i0 + nx * j1]);
            double v11 = a[o + i1 + nx * j1] + g[0] * (b[o + i1 + nx * j1] - a[o + i1 + nx * j1]);
            w[f] = (1 - fy) * ((1 - fx) * v00 + fx * v10) + fy * ((1 - fx) * v01 + fx * v11);
        }
        return w;
    }

    /**
     * Seit S3c-6 (SF31 A, Seegang Werkbuch 21/22): Breite des Übergangs am Rand eines feineren Netzes zum nächsten
     * gröberen (m) [G]; vorher sprang der Seegang dort (Rand von G3w, B49b). Seit S3c-7 (SF33 A, Werkbuch 23/24) liegt der
     * Streifen außen ({@link #RAND_ART}).
     */
    public static final double RAND_MISCHUNG = 500;

    /** Arten des Übergangs ({@link #RAND_ART}). */
    public static final int RAND_AUS = 0, RAND_INNEN = 1, RAND_AUSSEN = 2;

    /**
     * Seit S3c-7 (SF33 A): wo der Übergang liegt; {@code -Dseegang.rand=aussen|innen|aus}, Vorgabe außen.
     * <ul>
     * <li>außen: im feineren Netz gilt nur das feinere; außerhalb geht es über {@link #RAND_MISCHUNG} von dessen Randwert in
     * das gröbere über (die Teiche nahe dem Rand von G3w bleiben, wie G3w sie hat);</li>
     * <li>innen (S3c-6, SF31 A): im Streifen am Rand des feineren mit dem gröberen gemischt; G2w brachte dort die offene
     * See in Teiche und Priele (Bild 05 in L2i);</li>
     * <li>aus: das feinste Netz, das den Ort hat, ohne Übergang (bis S3c-5).</li>
     * </ul>
     * Wasserbild gibt die Art dem Shader weiter (seeZ[0].z).
     */
    public static volatile int RAND_ART = switch (System.getProperty("seegang.rand", "aussen").toLowerCase()) {
        case "innen" -> RAND_INNEN;
        case "aus" -> RAND_AUS;
        default -> RAND_AUSSEN;
    };

    /** Seit S3c-7: Abstand des Orts vom Gitter (m), 0 innen (euklidisch; an den Ecken rund). */
    public double aussenAbstand(double ost, double nord) {
        double x1 = gitter.x0 + gitter.nx * gitter.dx, y1 = gitter.y0 + gitter.ny * gitter.dy;
        double ax = Math.max(0, Math.max(gitter.x0 - ost, ost - x1)), ay = Math.max(0, Math.max(gitter.y0 - nord, nord - y1));
        return Math.hypot(ax, ay);
    }

    /** Liest die Werte eines Netzes am Ort; {@code rand}: außerhalb die Randwerte. */
    public interface Leser { double[] lies(Seegangsnetz s, double ost, double nord, boolean rand); }

    /**
     * Seit S3c-7: die Werte am Ort aus dem feinsten Netz, das ihn hat, mit dem Übergang nach {@link #RAND_ART}
     * (dieselbe Regel wie {@code seegang.glsl}, seeWerte); die Netze fein zuerst. null ohne Netz am Ort.
     */
    public static double[] gemischt(java.util.List<Seegangsnetz> netze, double ost, double nord, Leser l) {
        int art = RAND_ART;
        for (int k = 0; k < netze.size(); k++) {
            Seegangsnetz s = netze.get(k);
            double[] w = l.lies(s, ost, nord, false);
            if (w == null) continue;
            if (art == RAND_INNEN) {
                double r = s.randAbstand(ost, nord);
                if (r < RAND_MISCHUNG)
                    for (int q = k + 1; q < netze.size(); q++) {
                        double[] v = l.lies(netze.get(q), ost, nord, false);
                        if (v == null) continue;
                        double a = r / RAND_MISCHUNG;
                        for (int f = 0; f < w.length; f++) w[f] = v[f] + a * (w[f] - v[f]);
                        break;
                    }
            } else if (art == RAND_AUSSEN)
                for (int h = 0; h < k; h++) {
                    Seegangsnetz fein = netze.get(h);
                    double d = fein.aussenAbstand(ost, nord);
                    if (d >= RAND_MISCHUNG) continue;
                    double[] v = l.lies(fein, ost, nord, true);
                    if (v == null) continue;
                    double a = d / RAND_MISCHUNG;
                    for (int f = 0; f < w.length; f++) w[f] = v[f] + a * (w[f] - v[f]);
                    break;
                }
            return w;
        }
        return null;
    }

    /** Seit S3c-6: Abstand des Orts vom Rand des Gitters (m); negativ außerhalb. */
    public double randAbstand(double ost, double nord) {
        double ux = (ost - gitter.x0) / gitter.dx, uy = (nord - gitter.y0) / gitter.dy;
        return Math.min(Math.min(ux, gitter.nx - ux) * gitter.dx, Math.min(uy, gitter.ny - uy) * gitter.dy);
    }

    /**
     * Seit S3c-6 (SF31 A), seit S3c-7 nach {@link #RAND_ART}: die sechs Werte wie {@link #wert} aus dem feinsten Netz der
     * Liste, das den Ort hat, mit dem Übergang am Rand ({@link #gemischt}). null ohne Netz am Ort.
     */
    public static double[] wertGemischt(java.util.List<Seegangsnetz> netze, double ost, double nord, double t) {
        return gemischt(netze, ost, nord, (s, o, n, rand) -> rand ? s.wertRand(o, n, t) : s.wert(o, n, t));
    }

    /** Die Zeiten der Plätze (für Prüfungen). */
    public synchronized double[] zeiten() { return zeit.clone(); }
}
