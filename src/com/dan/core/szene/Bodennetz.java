package com.dan.core.szene;

import com.dan.core.gitter.Gitter;

/**
 * Das Gitter des Sediments für das Bild (P9b, Werkbuch HalligHooge Teil A, 12.10): die Trübung des Wassers und der
 * Boden in je zwei Ständen, wie {@link Seegangsnetz} für den Seegang (das Bild liegt linear zwischen ihnen; ein
 * jüngerer Stand ersetzt den älteren, ein früherer setzt neu an, gleiche Zeit ersetzt den jüngsten).
 * <ul>
 * <li>{@link #truebung}: je Zelle ein Wert, die optisch wirksame Konzentration C_opt = Schlick + {@link #SAND} · Sand
 * (g/m³) aus {@code sed.c}; Sand streut je Masse viel schwächer als Schlick [G].</li>
 * <li>{@link #boden}: je Zelle sechs Werte ({@link #BODEN}): Höhe und Länge der Rippel (m), ihre Richtung als Vektor im
 * Gitter (cos, sin; 0 = Ost, gegen den Uhrzeigersinn; Länge 0 ohne Rippel), der Schlickanteil der Mischschicht und die
 * Bodenänderung (m) aus {@code bed.ripple}, {@code bed.mud}, {@code bed.dz}. Der Boden ändert sich langsam und kommt
 * seltener als die Trübung.</li>
 * </ul>
 */
public final class Bodennetz {
    /** Gewicht des Sands in der optisch wirksamen Konzentration [G]. */
    public static final double SAND = 0.05;
    /** Zum Rand des Netzes geht die Trübung über so viele Zellen in die der Szene über [G] (seit P10d hier, vorher in {@code Wasserbild}). */
    public static final double RAND_ZELLEN = 25;
    public static final int BODEN = 6;

    /** Eine Größe in zwei Ständen. */
    public static final class Spur {
        public final int felder, zellen;
        /** Werte je Platz: [Platz][Feld · zellen + Zelle]. */
        public final float[][] werte = new float[2][];
        final double[] zeit = {Double.NaN, Double.NaN};
        /** Platz des jüngeren Stands (−1: noch keiner). */
        public int platzB = -1;
        /** Was seit dem letzten Hochladen neu ist (Bit 0, 1: Plätze). */
        public int neu;

        Spur(int felder, int zellen) {
            this.felder = felder;
            this.zellen = zellen;
            werte[0] = new float[felder * zellen];
            werte[1] = new float[felder * zellen];
        }

        /** Der Platz, auf den ein Stand zur Zeit t gehört; {Platz, 1 = auch den anderen füllen}. */
        synchronized int[] platz(double t) {
            if (platzB < 0 || t < zeit[platzB]) return new int[] {0, 1};
            return new int[] {t == zeit[platzB] ? platzB : 1 - platzB, 0};
        }

        synchronized void gesetzt(int p, double t, boolean beide) {
            if (beide) {
                System.arraycopy(werte[p], 0, werte[1 - p], 0, werte[p].length);
                zeit[1 - p] = t;
                neu |= 3;
            }
            zeit[p] = t;
            neu |= 1 << p;
            platzB = p;
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

        /** Die Zeiten der Plätze (für Prüfungen). */
        public synchronized double[] zeiten() { return zeit.clone(); }
    }

    public final Gitter gitter;
    public final int zellen;
    public final Spur truebung, boden;

    public Bodennetz(Gitter gitter) {
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Boden: nur Gitter in UTM, nicht " + gitter);
        this.gitter = gitter;
        zellen = gitter.zellen();
        truebung = new Spur(1, zellen);
        boden = new Spur(BODEN, zellen);
    }

    /** Ein Stand der Trübung aus {@code sed.c}: je Zelle Sand, Schlick (kg/m³). */
    public void truebung(double t, float[] c) {
        int[] p = truebung.platz(t);
        float[] w = truebung.werte[p[0]];
        for (int z = 0; z < zellen; z++) w[z] = (float) (1000 * (c[2 * z + 1] + SAND * c[2 * z]));
        truebung.gesetzt(p[0], t, p[1] == 1);
    }

    /** Ein Stand der Trübung mit überall derselben Konzentration (g/m³; für Prüfungen). */
    public void truebung(double t, double cOpt) {
        int[] p = truebung.platz(t);
        java.util.Arrays.fill(truebung.werte[p[0]], (float) cOpt);
        truebung.gesetzt(p[0], t, p[1] == 1);
    }

    /**
     * Seit P10d (S8): die Trübung am Ort (UTM) zur Zeit t wie {@code truebungOrt} in {@code render.water/wasser.frag}:
     * bilinear zwischen den Zellmitten über die Zellen mit Wasser (Wert &gt; 0), linear zwischen den zwei Ständen; zum Rand
     * des Netzes hin über {@code randZellen} Zellen in den Schwebstoff der Szene {@code szene} übergehend, außerhalb er.
     */
    public double truebungAm(double ost, double nord, double t, double szene, double randZellen) {
        if (!truebung.gesetzt()) return szene;
        Gitter g = gitter;
        double ux = (ost - g.x0) / g.dx, uy = (nord - g.y0) / g.dx;
        int nx = g.nx, ny = g.ny;
        if (ux < 0 || uy < 0 || ux >= nx || uy >= ny) return szene;
        double bx = Math.max(0, Math.min(nx - 1, ux - 0.5)), by = Math.max(0, Math.min(ny - 1, uy - 0.5));
        int i0 = Math.max(Math.min((int) bx, nx - 2), 0), j0 = Math.max(Math.min((int) by, ny - 2), 0);
        double fx = bx - i0, fy = by - j0;
        int i1 = Math.min(i0 + 1, nx - 1), j1 = Math.min(j0 + 1, ny - 1);
        double[] gw = truebung.gewicht(t);
        float[] wa = truebung.werte[(int) gw[1]], wb = truebung.werte[1 - (int) gw[1]];
        double summe = 0, gewicht = 0;
        for (int k = 0; k < 4; k++) {
            int c = ((k & 1) == 0 ? i0 : i1) + nx * (k < 2 ? j0 : j1);
            double w = ((k & 1) == 0 ? 1 - fx : fx) * (k < 2 ? 1 - fy : fy);
            double a = wa[c], b = wb[c], wert = a + gw[0] * (b - a);
            if (wert > 0) {
                summe += w * wert;
                gewicht += w;
            }
        }
        if (gewicht < 1e-4) return szene;
        double rand = Math.max(0, Math.min(1, Math.min(Math.min(ux, nx - ux), Math.min(uy, ny - uy)) / randZellen));
        return szene + (summe / gewicht - szene) * rand;
    }

    /**
     * Seit P10d (S8): die Bodenänderung {@code bed.dz} (m) am Ort (UTM) im jüngsten Stand des Bodens, wie
     * {@code bodenDzOrt} in {@code render.terrain/gelaende.vert}: bilinear zwischen den Zellmitten, Zellen außerhalb des
     * Gitters und NaN zählen 0. 0 ohne Boden.
     */
    public double dzAm(double ost, double nord) {
        if (!boden.gesetzt()) return 0;
        float[] w = boden.werte[boden.platzB];
        Gitter g = gitter;
        double ux = (ost - g.x0) / g.dx - 0.5, uy = (nord - g.y0) / g.dx - 0.5;
        int i0 = (int) Math.floor(ux), j0 = (int) Math.floor(uy);
        double fx = ux - i0, fy = uy - j0, s = 0;
        for (int k = 0; k < 4; k++) {
            int i = i0 + (k & 1), j = j0 + (k >> 1);
            if (i < 0 || j < 0 || i >= g.nx || j >= g.ny) continue;
            float v = w[5 * zellen + i + g.nx * j];
            if (Float.isNaN(v)) continue;
            s += ((k & 1) == 0 ? 1 - fx : fx) * ((k >> 1) == 0 ? 1 - fy : fy) * v;
        }
        return s;
    }

    /** Größte Hebung des Bodens im jüngsten Stand (m, nicht unter 0), für Schranken. */
    public double dzMax() {
        if (!boden.gesetzt()) return 0;
        float[] w = boden.werte[boden.platzB];
        double m = 0;
        for (int z = 0; z < zellen; z++) if (w[5 * zellen + z] > m) m = w[5 * zellen + z];
        return m;
    }

    /**
     * Ein Stand des Bodens: {@code bed.ripple} (je Zelle Höhe, Länge in m, Richtung in Grad von Gitternord im
     * Uhrzeigersinn), {@code bed.mud}, {@code bed.dz} (m).
     */
    public void boden(double t, float[] rippel, float[] schlick, float[] dz) {
        int[] p = boden.platz(t);
        float[] w = boden.werte[p[0]];
        int n = zellen;
        for (int z = 0; z < n; z++) {
            float h = rippel[3 * z];
            w[z] = h;
            w[n + z] = rippel[3 * z + 1];
            double th = Math.toRadians(90 - rippel[3 * z + 2]);                  // Kompass → mathematisch im Gitter
            w[2 * n + z] = h > 0 ? (float) Math.cos(th) : 0f;
            w[3 * n + z] = h > 0 ? (float) Math.sin(th) : 0f;
            w[4 * n + z] = schlick[z];
            w[5 * n + z] = dz[z];
        }
        boden.gesetzt(p[0], t, p[1] == 1);
    }
}
