package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Utm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Die Stufe 100 m über der UTM-Hülle von G1 (Werkbuch HalligHooge Teil A, 10.4, Schritte 4 und 7a; F22).
 * <p>
 * Die Pixel liegen auf den Feinpixeln von G1 (Ränder auf vollen 100 m). Ein Pixel trägt das Mittel an den Mitten
 * seiner 10 × 10 Teilpixel; dort wird das 10-m-Feld (die 10-m-Stufe, sonst TrilaWatt „ger“) bilinear gelesen, also das
 * Mittel aus 2 × 2 Pixeln – zusammen genau das Flächenmittel der 10-m-Pixel. Ein Punkt ohne TrilaWatt nimmt den Ersatz an
 * der Mitte des Pixels: an Land (Natural Earth) GEBCO, im Meer EMODnet und, wo EMODnet fehlt, GEBCO, beide um die
 * Korrektur k(E, N) auf NHN gebracht.
 * <p>
 * <b>Korrektur.</b> Unterschied Quelle − TrilaWatt an den Pixeln, deren 11 × 11 Pixel von TrilaWatt alle Werte haben,
 * tiefer als {@link #unter} liegen und im Meer nach Natural Earth; Median je Feld von {@link #feld} m mit mindestens
 * {@link #punkte} Punkten; darüber ein Polynom zweiten Grades in E und N (ab 12 Feldern), ersten Grades (ab 4) oder ein
 * fester Wert.
 */
public final class Grob {
    /** TrilaWatt-Grenze, Feldgröße (m) und kleinste Zahl der Punkte je Feld für die Anpassung [G]. */
    public double unter = -0.5, feld = 10000;
    public int punkte = 100;

    /** Ein Raster in Grad ganz im Speicher: Pixel von der Nordwestecke, Zeilen von Nord. */
    public static final class Grad {
        public final double west, nord, d;
        public final int w, h;
        public final float[] v;
        public final List<Path> dateien;

        Grad(double west, double nord, double d, int w, int h, float[] v, List<Path> dateien) {
            this.west = west;
            this.nord = nord;
            this.d = d;
            this.w = w;
            this.h = h;
            this.v = v;
            this.dateien = dateien;
        }

        /** Liest eine oder mehrere Kacheln gleicher Pixelgröße und Lage zu einem Mosaik. */
        public static Grad lies(List<Path> dateien) throws IOException {
            List<GeoTiff> t = new ArrayList<>();
            try {
                double w0 = 1e9, n0 = -1e9, o0 = -1e9, s0 = 1e9, d = 0;
                for (Path p : dateien) {
                    GeoTiff g = GeoTiff.oeffne(p);
                    t.add(g);
                    if (!g.geografisch) throw new IOException(p + ": nicht geographisch (EPSG " + g.epsg + ")");
                    if (d == 0) d = g.dx;
                    if (Math.abs(g.dx - d) > 1e-12 || Math.abs(g.dy + d) > 1e-12) throw new IOException(p + ": Pixel " + g.dx + " × " + g.dy + " statt " + d);
                    w0 = Math.min(w0, g.x0);
                    n0 = Math.max(n0, g.y0);
                    o0 = Math.max(o0, g.x0 + g.breite * d);
                    s0 = Math.min(s0, g.y0 - g.hoehe * d);
                }
                int w = (int) Math.round((o0 - w0) / d), h = (int) Math.round((n0 - s0) / d);
                float[] v = new float[w * h];
                Arrays.fill(v, Float.NaN);
                for (GeoTiff g : t) {
                    int c0 = (int) Math.round((g.x0 - w0) / d), r0 = (int) Math.round((n0 - g.y0) / d);
                    if (Math.abs(c0 - (g.x0 - w0) / d) > 1e-6 || Math.abs(r0 - (n0 - g.y0) / d) > 1e-6) throw new IOException(g.datei + ": nicht auf dem Raster des Mosaiks");
                    float[] a = g.lies(0, 0, g.breite, g.hoehe);
                    for (int r = 0; r < g.hoehe; r++) System.arraycopy(a, r * g.breite, v, (r0 + r) * w + c0, g.breite);
                }
                return new Grad(w0, n0, d, w, h, v, List.copyOf(dateien));
            } finally {
                for (GeoTiff g : t) g.close();
            }
        }

        /** Bilinear zwischen den Pixelmitten (Regel von {@link Gelaende#bilinear}); NaN außerhalb. */
        public float wert(double lon, double lat) {
            double c = (lon - west) / d - 0.5, r = (nord - lat) / d - 0.5;
            int i = (int) Math.floor(c), j = (int) Math.floor(r);
            return Gelaende.bilinear(px(i, j), px(i + 1, j), px(i, j + 1), px(i + 1, j + 1), c - i, r - j);
        }

        /** Wert des Pixels (Spalte von West, Zeile von Nord), NaN außerhalb. */
        public float px(int i, int j) { return i < 0 || j < 0 || i >= w || j >= h ? Float.NaN : v[j * w + i]; }

        /** Enthält das Raster den Punkt (in einem Pixel)? */
        public boolean enthaelt(double lon, double lat) { return lon >= west && lon < west + w * d && lat <= nord && lat > nord - h * d; }
    }

    /**
     * Die Korrektur k(E, N) = a0 + a1·u + a2·v + a3·u² + a4·u·v + a5·v² mit u = (E − e0)/100 km, v = (N − n0)/100 km.
     * {@code grad}: 2, 1, 0 oder −1 (keine Felder: k = 0). Rest: |Quelle − k − TrilaWatt| an den Punkten der Anpassung.
     */
    public record Korrektur(String quelle, double[] a, double e0, double n0, int grad, int felder, long zahl, double restMedian, double rest95) {
        public double wert(double e, double n) {
            double u = (e - e0) / 1e5, v = (n - n0) / 1e5;
            return a[0] + a[1] * u + a[2] * v + a[3] * u * u + a[4] * u * v + a[5] * v * v;
        }

        /** Zeile für {@code gebiet.txt}: Quelle, e0, n0, Grad, Felder, a0 … a5. */
        public String zeile() {
            StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "korrektur %s %.0f %.0f %d %d", quelle, e0, n0, grad, felder));
            for (double x : a) b.append(' ').append(x);
            return b.toString();
        }

        /** Aus einer Zeile von {@link #zeile}. */
        public static Korrektur aus(String z) {
            String[] t = z.strip().split("\\s+");
            double[] a = new double[6];
            for (int i = 0; i < 6; i++) a[i] = Double.parseDouble(t[6 + i]);
            return new Korrektur(t[1], a, Double.parseDouble(t[2]), Double.parseDouble(t[3]), Integer.parseInt(t[4]), Integer.parseInt(t[5]), 0, Double.NaN, Double.NaN);
        }

        @Override public String toString() {
            return String.format(Locale.GERMAN, "%s: Grad %d aus %d Feldern (%d Punkte); Rest im Median %.3f m, 95 %% %.3f m", quelle, grad, felder, zahl, restMedian, rest95);
        }
    }

    public Korrektur emodnet, gebco;
    /** Zählwerte: Pixel der Hülle, ganz aus TrilaWatt, teilweise, ohne; Ersatz aus EMODnet, GEBCO im Meer, GEBCO an Land. */
    public long pixel, ganz, teilweise, ohne, ausEmodnet, ausGebcoMeer, ausGebcoLand;
    /** Spannweite der Korrektur an den Feldern (für den Bericht). */
    public double[] spanneE = {Double.NaN, Double.NaN}, spanneG = {Double.NaN, Double.NaN};

    /**
     * Baut die Stufe. {@code g1}: die Hülle (Zellen 1 km, Feinpixel 100 m); {@code zehn}: die 10-m-Stufe (darf null sein);
     * {@code tw}: TrilaWatt „ger“; {@code land}: Land nach Natural Earth je 100-m-Pixel der Hülle (Zeilen von Nord).
     */
    public Gelaende.Stufe bauen(Gitter g1, int fein, Gelaende.Stufe zehn, GeoTiff tw, Grad emod, Grad geb, byte[] land, File ordner, String kommentar) throws IOException {
        double z = g1.dx / fein;
        if (Math.abs(z - 100) > 1e-9) throw new IllegalArgumentException("Stufe 100 m: Feinpixel von " + g1.name + " sind " + z + " m");
        if (Math.abs(tw.dx - 10) > 1e-9 || Math.abs(tw.dy + 10) > 1e-9) throw new IOException(tw.datei + ": TrilaWatt braucht 10-m-Pixel");
        int W = g1.nx * fein, H = g1.ny * fein;
        double x0 = g1.x0, y1 = g1.y1();
        // Spalte/Zeile von TrilaWatt der Pixelmitte am Westrand bzw. Nordrand eines 100-m-Pixels
        double cTw = (x0 - tw.x0) / tw.dx - 0.5, rTw = (y1 - tw.y0) / tw.dy - 0.5;
        if (Math.abs(cTw - Math.rint(cTw)) > 1e-6 || Math.abs(rTw - Math.rint(rTw)) > 1e-6)
            throw new IOException(tw.datei + ": die Pixelmitten von TrilaWatt liegen nicht auf den Rändern der 100-m-Pixel");
        int twC0 = (int) Math.rint(cTw), twR0 = (int) Math.rint(rTw);
        float[] summe = new float[W * H], ersatzE = new float[W * H], ersatzG = new float[W * H];
        byte[] gueltig = new byte[W * H];
        boolean[] voll = new boolean[W * H];
        double[] mitte = new double[W * H];                                              // Mittel aus TrilaWatt, wo voll
        int band = 40, tw10 = 10 * W + 1;
        for (int r0 = 0; r0 < H; r0 += band) {
            int nb = Math.min(band, H - r0), th = 10 * nb + 1;
            float[] t = tw.lies(twC0, twR0 + 10 * r0, tw10, th);
            if (zehn != null) ueberschreibe(t, zehn, tw, twC0, twR0 + 10 * r0, tw10, th);
            final int rr0 = r0;
            IntStream.range(0, nb).parallel().forEach(rb -> {
                int r = rr0 + rb;
                for (int c = 0; c < W; c++) {
                    double s = 0;
                    int n = 0, nGanz = 0;
                    for (int l = 0; l <= 10; l++)
                        for (int k = 0; k <= 10; k++) if (!Float.isNaN(t[(10 * rb + l) * tw10 + 10 * c + k])) nGanz++;
                    for (int l = 0; l < 10; l++)
                        for (int k = 0; k < 10; k++) {
                            int o = (10 * rb + l) * tw10 + 10 * c + k;
                            float v = Gelaende.bilinear(t[o], t[o + 1], t[o + tw10], t[o + tw10 + 1], 0.5, 0.5);
                            if (!Float.isNaN(v)) { s += v; n++; }
                        }
                    int p = r * W + c;
                    summe[p] = (float) s;
                    gueltig[p] = (byte) n;
                    voll[p] = nGanz == 121;
                    mitte[p] = n == 100 ? s / 100 : Double.NaN;
                    double e = x0 + (c + 0.5) * z, nn = y1 - (r + 0.5) * z;
                    double[] g = Utm.rueckwaerts(e, nn, g1.zone);
                    ersatzE[p] = emod == null ? Float.NaN : emod.wert(g[0], g[1]);
                    ersatzG[p] = geb.wert(g[0], g[1]);
                }
            });
        }
        emodnet = anpassen("EMODnet", ersatzE, mitte, voll, land, g1, W, H, z, spanneE);
        gebco = anpassen("GEBCO", ersatzG, mitte, voll, land, g1, W, H, z, spanneG);
        // die Stufe
        int kz = 1000, ko = (W + kz - 1) / kz, kn = (H + kz - 1) / kz;
        Stapel.leere(ordner);
        Gelaende.Stufe st = Gelaende.Stufe.anlegen(ordner, z, kz, x0, y1 - kn * kz * z, ko, kn, kommentar);
        float[] wert = new float[W * H];
        for (int r = 0; r < H; r++)
            for (int c = 0; c < W; c++) {
                int p = r * W + c;
                pixel++;
                if (gueltig[p] == 100) { wert[p] = (float) (summe[p] / 100.0); ganz++; continue; }
                double e = x0 + (c + 0.5) * z, nn = y1 - (r + 0.5) * z;
                double ersatz;
                if (land[p] != 0) { ersatz = ersatzG[p]; ausGebcoLand++; }
                else if (!Float.isNaN(ersatzE[p])) { ersatz = ersatzE[p] - emodnet.wert(e, nn); ausEmodnet++; }
                else { ersatz = ersatzG[p] - gebco.wert(e, nn); ausGebcoMeer++; }
                if (gueltig[p] > 0) teilweise++;
                else ohne++;
                wert[p] = (float) ((summe[p] + (100 - gueltig[p]) * ersatz) / 100.0);
            }
        for (int ty = 0; ty < kn; ty++)
            for (int tx = 0; tx < ko; tx++) {
                float[] k = new float[kz * kz];
                Arrays.fill(k, Float.NaN);
                for (int j = 0; j < kz; j++) {
                    int r = ty * kz + j;
                    if (r >= H) break;
                    int n = Math.min(kz, W - tx * kz);
                    if (n > 0) System.arraycopy(wert, r * W + tx * kz, k, j * kz, n);
                }
                st.schreibe(tx, ty, k);
            }
        return st;
    }

    /** Der Wert von Grob für ein Pixel ohne TrilaWatt (für die Prüfung): Ersatz an der Mitte. */
    public static double ersatz(boolean land, float emod, float geb, Korrektur ke, Korrektur kg, double e, double n) {
        if (land) return geb;
        if (!Float.isNaN(emod)) return emod - ke.wert(e, n);
        return geb - kg.wert(e, n);
    }

    /** Setzt im Fenster von TrilaWatt die Werte der 10-m-Stufe ein, wo sie welche hat. */
    static void ueberschreibe(float[] t, Gelaende.Stufe zehn, GeoTiff tw, int c0, int r0, int w, int h) {
        int s0 = (int) Math.rint(zehn.spalte(tw.x(c0))), z0 = (int) Math.rint(zehn.zeile(tw.y(r0)));
        if (Math.abs(zehn.spalte(tw.x(c0)) - s0) > 1e-6 || Math.abs(zehn.zeile(tw.y(r0)) - z0) > 1e-6)
            throw new IllegalArgumentException("10-m-Stufe liegt nicht auf dem Raster von TrilaWatt");
        int u0 = Math.max(0, -s0), u1 = Math.min(w, zehn.spalten() - s0), v0 = Math.max(0, -z0), v1 = Math.min(h, zehn.zeilen() - z0);
        if (u0 >= u1 || v0 >= v1) return;
        IntStream.range(v0, v1).parallel().forEach(v -> {
            for (int u = u0; u < u1; u++) {
                float x = zehn.wert(s0 + u, z0 + v);
                if (!Float.isNaN(x)) t[v * w + u] = x;
            }
        });
    }

    private Korrektur anpassen(String name, float[] q, double[] tw, boolean[] voll, byte[] land, Gitter g1, int W, int H, double z, double[] spanne) {
        Map<Long, float[]> felder = new HashMap<>();
        Map<Long, int[]> zahl = new HashMap<>();
        for (int r = 0; r < H; r++)
            for (int c = 0; c < W; c++) {
                int p = r * W + c;
                if (!voll[p] || land[p] != 0 || Float.isNaN(q[p]) || !(tw[p] < unter)) continue;
                double e = g1.x0 + (c + 0.5) * z, n = g1.y1() - (r + 0.5) * z;
                long key = ((long) Math.floor(e / feld) << 32) | (Math.floorMod((long) Math.floor(n / feld), 1L << 32));
                float[] l = felder.computeIfAbsent(key, k -> new float[256]);
                int[] m = zahl.computeIfAbsent(key, k -> new int[1]);
                if (m[0] == l.length) { l = Arrays.copyOf(l, 2 * l.length); felder.put(key, l); }
                l[m[0]++] = (float) (q[p] - tw[p]);
            }
        List<double[]> pkt = new ArrayList<>();
        long n = 0;
        for (Map.Entry<Long, float[]> me : felder.entrySet()) {
            int m = zahl.get(me.getKey())[0];
            if (m < punkte) continue;
            float[] l = Arrays.copyOf(me.getValue(), m);
            Arrays.sort(l);
            double med = m % 2 == 1 ? l[m / 2] : 0.5 * (l[m / 2 - 1] + l[m / 2]);
            long k = me.getKey();
            double fe = ((k >> 32) + 0.5) * feld, fn = ((int) k + 0.5) * feld;
            pkt.add(new double[] {fe, fn, med});
            n += m;
        }
        pkt.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        double e0 = 0.5 * (g1.x0 + g1.x1()), n0 = 0.5 * (g1.y0 + g1.y1());
        int grad = pkt.size() >= 12 ? 2 : pkt.size() >= 4 ? 1 : pkt.isEmpty() ? -1 : 0;
        double[] a = new double[6];
        if (grad >= 0) {
            int[] glieder = grad == 2 ? new int[] {0, 1, 2, 3, 4, 5} : grad == 1 ? new int[] {0, 1, 2} : new int[] {0};
            int m = glieder.length;
            double[][] ata = new double[m][m];
            double[] atb = new double[m];
            for (double[] p : pkt) {
                double u = (p[0] - e0) / 1e5, v = (p[1] - n0) / 1e5;
                double[] f = {1, u, v, u * u, u * v, v * v};
                for (int i = 0; i < m; i++) {
                    atb[i] += f[glieder[i]] * p[2];
                    for (int j = 0; j < m; j++) ata[i][j] += f[glieder[i]] * f[glieder[j]];
                }
            }
            double[] x = loese(ata, atb);
            for (int i = 0; i < m; i++) a[glieder[i]] = x[i];
        }
        Korrektur k = new Korrektur(name, a, e0, n0, grad, pkt.size(), n, Double.NaN, Double.NaN);
        double kmin = Double.POSITIVE_INFINITY, kmax = Double.NEGATIVE_INFINITY;
        for (double[] p : pkt) {
            double v = k.wert(p[0], p[1]);
            kmin = Math.min(kmin, v);
            kmax = Math.max(kmax, v);
        }
        spanne[0] = kmin;
        spanne[1] = kmax;
        // Rest an den Punkten
        float[] rest = new float[(int) Math.min(n, Integer.MAX_VALUE - 8)];
        int i = 0;
        for (int r = 0; r < H && i < rest.length; r++)
            for (int c = 0; c < W && i < rest.length; c++) {
                int p = r * W + c;
                if (!voll[p] || land[p] != 0 || Float.isNaN(q[p]) || !(tw[p] < unter)) continue;
                double e = g1.x0 + (c + 0.5) * z, nn = g1.y1() - (r + 0.5) * z;
                long key = ((long) Math.floor(e / feld) << 32) | (Math.floorMod((long) Math.floor(nn / feld), 1L << 32));
                if (zahl.get(key)[0] < punkte) continue;
                rest[i++] = (float) Math.abs(q[p] - k.wert(e, nn) - tw[p]);
            }
        rest = Arrays.copyOf(rest, i);
        Arrays.sort(rest);
        double med = i == 0 ? Double.NaN : rest[i / 2], p95 = i == 0 ? Double.NaN : rest[Math.min(i - 1, (int) (0.95 * i))];
        return new Korrektur(name, a, e0, n0, grad, pkt.size(), n, med, p95);
    }

    /** Gauß mit Spaltenpivot. */
    static double[] loese(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][];
        for (int i = 0; i < n; i++) m[i] = Arrays.copyOf(a[i], n);
        double[] x = b.clone();
        for (int k = 0; k < n; k++) {
            int p = k;
            for (int i = k + 1; i < n; i++) if (Math.abs(m[i][k]) > Math.abs(m[p][k])) p = i;
            double[] t = m[k]; m[k] = m[p]; m[p] = t;
            double s = x[k]; x[k] = x[p]; x[p] = s;
            if (Math.abs(m[k][k]) < 1e-300) throw new IllegalStateException("Korrektur: Gleichungen singulär");
            for (int i = k + 1; i < n; i++) {
                double f = m[i][k] / m[k][k];
                for (int j = k; j < n; j++) m[i][j] -= f * m[k][j];
                x[i] -= f * x[k];
            }
        }
        for (int k = n - 1; k >= 0; k--) {
            double s = x[k];
            for (int j = k + 1; j < n; j++) s -= m[k][j] * x[j];
            x[k] = s / m[k][k];
        }
        return x;
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%d Pixel: %d ganz aus TrilaWatt, %d teilweise, %d ohne; Ersatz aus EMODnet %d, GEBCO im Meer %d, GEBCO an Land %d",
                pixel, ganz, teilweise, ohne, ausEmodnet, ausGebcoMeer, ausGebcoLand);
    }
}
