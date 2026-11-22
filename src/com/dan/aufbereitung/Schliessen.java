package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Land hinter den Deichen, das die Gitter G0–G2 nicht sehen (Werkbuch HalligHooge Teil A, 10.4, Schritt 7b; F23,
 * F24): eine Maske im Raster von TrilaWatt (10 m) über die UTM-Hülle von G1, soweit TrilaWatt reicht.
 * <ul>
 * <li><b>Wand</b> ist Gelände ab {@link #hoehe} und, wo TrilaWatt fehlt, Land nach Natural Earth. Lücken der Wand bis
 * {@link #luecke} werden geschlossen: Dilatation und Erosion mit einem Quadrat von 2r + 1 Pixeln, r = ⌈Lücke / 20 m⌉.</li>
 * <li>Die <b>Flutfüllung</b> geht von allen Pixeln am Rand aus, die keine Wand sind (vierfach verbunden).</li>
 * <li><b>Geschlossen</b> ist, was sie nicht erreicht, außer Gelände ab {@link #hoehe} (das behält seine Höhe), dazu
 * jedes Pixel ohne TrilaWatt in G2 oder auf Land.</li>
 * </ul>
 * Ein Punkt zwischen den Pixelmitten (die Feinpixel von G1 und G2 liegen 5 m neben denen von TrilaWatt) ist
 * geschlossen, wenn mindestens zwei seiner vier Nachbarn es sind (bilinear ab ½; {@link #zu}).
 */
public final class Schliessen {
    /** Wandhöhe (m NHN) und größte Lücke (m), die geschlossen wird [G]. */
    public double hoehe = 4.0, luecke = 60;
    /** Das Rechteck R im Raster von TrilaWatt: erste Spalte und Zeile, Breite, Höhe; Wörter je Zeile. */
    public int ca, ra, w, h, s;
    /** Zählwerte (Pixel): Wand vor und nach dem Schließen, erreicht, geschlossen mit TrilaWatt, ohne TrilaWatt. */
    public long wand0, wand, erreicht, zuMitGrund, zuOhneGrund;
    private long[] zu;
    private GeoTiff tw;

    /** Baut die Maske. {@code land}: Land nach Natural Earth je 100-m-Pixel von G1 (Zeilen von Nord). */
    public void bauen(Gitter g1, int fein1, Gitter g2, Gelaende.Stufe zehn, GeoTiff tw, byte[] land) throws IOException {
        this.tw = tw;
        double z1 = g1.dx / fein1;
        int W1 = g1.nx * fein1;
        ca = Math.max(0, (int) Math.floor((g1.x0 - tw.x0) / tw.dx));
        int cb = Math.min(tw.breite, (int) Math.ceil((g1.x1() - tw.x0) / tw.dx));
        ra = Math.max(0, (int) Math.floor((g1.y1() - tw.y0) / tw.dy));
        int rb = Math.min(tw.hoehe, (int) Math.ceil((g1.y0 - tw.y0) / tw.dy));
        w = cb - ca;
        h = rb - ra;
        if (w <= 0 || h <= 0) throw new IOException("Schließen: TrilaWatt liegt nicht in " + g1);
        s = (w + 63) >>> 6;
        long[] hoch = new long[s * h], nan = new long[s * h], wa = new long[s * h];
        int band = 256;
        for (int r0 = 0; r0 < h; r0 += band) {
            int nb = Math.min(band, h - r0);
            float[] t = tw.lies(ca, ra + r0, w, nb);
            if (zehn != null) Grob.ueberschreibe(t, zehn, tw, ca, ra + r0, w, nb);
            final int rr = r0;
            IntStream.range(0, nb).parallel().forEach(v -> {
                int r = rr + v;
                double y = tw.y(ra + r);
                int r100 = (int) Math.floor((g1.y1() - y) / z1);
                for (int u = 0; u < w; u++) {
                    float x = t[v * w + u];
                    int o = r * s + (u >>> 6);
                    long bit = 1L << (u & 63);
                    if (Float.isNaN(x)) {
                        nan[o] |= bit;
                        int c100 = (int) Math.floor((tw.x(ca + u) - g1.x0) / z1);
                        if (r100 >= 0 && c100 >= 0 && c100 < W1 && r100 < g1.ny * fein1 && land[r100 * W1 + c100] != 0) wa[o] |= bit;
                    } else if (x >= hoehe) {
                        hoch[o] |= bit;
                        wa[o] |= bit;
                    }
                }
            });
        }
        wand0 = zaehle(wa);
        int r = (int) Math.ceil(luecke / (2 * tw.dx) - 1e-9);
        for (int i = 0; i < r; i++) dehneX(wa);
        for (int i = 0; i < r; i++) dehneY(wa);
        fuelleRand(wa, true);
        for (int i = 0; i < r; i++) schrumpfeX(wa);
        for (int i = 0; i < r; i++) schrumpfeY(wa);
        fuelleRand(wa, false);
        wand = zaehle(wa);
        long[] er = flute(wa);
        erreicht = zaehle(er);
        // geschlossen: nicht erreicht und nicht hoch; dazu ohne TrilaWatt in G2 oder auf Land
        zu = new long[s * h];
        for (int v = 0; v < h; v++) {
            double y = tw.y(ra + v);
            boolean inG2Zeile = y > g2.y0 && y < g2.y1();
            int r100 = (int) Math.floor((g1.y1() - y) / z1);
            for (int k = 0; k < s; k++) {
                int o = v * s + k;
                long m = ~er[o] & ~hoch[o];
                long n = nan[o];
                if (n != 0) {
                    for (int b = 0; b < 64; b++) {
                        if ((n >>> b & 1) == 0) continue;
                        int u = (k << 6) + b;
                        double x = tw.x(ca + u);
                        int c100 = (int) Math.floor((x - g1.x0) / z1);
                        boolean landHier = r100 >= 0 && c100 >= 0 && c100 < W1 && r100 < g1.ny * fein1 && land[r100 * W1 + c100] != 0;
                        if (inG2Zeile && x > g2.x0 && x < g2.x1() || landHier) m |= 1L << b;
                    }
                }
                zu[o] = m & maske(k);
            }
        }
        for (int v = 0; v < h; v++)
            for (int k = 0; k < s; k++) {
                long m = zu[v * s + k];
                zuOhneGrund += Long.bitCount(m & nan[v * s + k]);
                zuMitGrund += Long.bitCount(m & ~nan[v * s + k]);
            }
    }

    /** Geschlossen am Pixel (Spalte, Zeile) von TrilaWatt (ganzes Bild); außerhalb von R false. */
    public boolean zu(int spalte, int zeile) {
        int u = spalte - ca, v = zeile - ra;
        if (u < 0 || v < 0 || u >= w || v >= h) return false;
        return (zu[v * s + (u >>> 6)] >>> (u & 63) & 1) != 0;
    }

    /** Liegt das Pixel (Spalte, Zeile) von TrilaWatt in R? */
    public boolean inR(int spalte, int zeile) { return spalte >= ca && zeile >= ra && spalte < ca + w && zeile < ra + h; }

    /**
     * Anteil geschlossener Punkte je 100-m-Pixel von G1 (10 × 10 Punkte an den Mitten der Teilpixel, je Punkt zwei von
     * vier Nachbarn); außerhalb von R Land nach Natural Earth.
     */
    public float[] anteil(Gitter g1, int fein1, byte[] land) {
        int W = g1.nx * fein1, H = g1.ny * fein1;
        int c0 = (int) Math.rint((g1.x0 - tw.x0) / tw.dx - 0.5), r0 = (int) Math.rint((g1.y1() - tw.y0) / tw.dy - 0.5);
        int k = (int) Math.rint(g1.dx / fein1 / tw.dx);
        float[] a = new float[W * H];
        IntStream.range(0, H).parallel().forEach(r -> {
            for (int c = 0; c < W; c++) {
                int n = 0;
                for (int l = 0; l < k; l++)
                    for (int m = 0; m < k; m++) {
                        int cc = c0 + k * c + m, rr = r0 + k * r + l;
                        int z = 0;
                        boolean ganzInR = true;
                        for (int q = 0; q < 4; q++) {
                            int x = cc + (q & 1), y = rr + (q >> 1);
                            if (!inR(x, y)) ganzInR = false;
                            else if (zu(x, y)) z++;
                        }
                        if (ganzInR ? z >= 2 : land[r * W + c] != 0) n++;
                    }
                a[r * W + c] = n / (float) (k * k);
            }
        });
        return a;
    }

    /** Schreibt die Maske als Stufe (1 = geschlossen, sonst NaN); Kacheln ohne geschlossenes Pixel fehlen. */
    public Gelaende.Stufe schreibe(File ordner) throws IOException {
        int kz = 1000, ko = (w + kz - 1) / kz, kn = (h + kz - 1) / kz;
        double west = tw.x0 + ca * tw.dx, nord = tw.y0 + ra * tw.dy;
        Stapel.leere(ordner);
        Gelaende.Stufe st = Gelaende.Stufe.anlegen(ordner, tw.dx, kz, west, nord - kn * kz * tw.dx, ko, kn,
                String.format(Locale.ROOT, "Geschlossen (1) für das Backen von G1 und G2: Wand ab %.1f m NHN, Lücken bis %.0f m (Werkbuch HalligHooge Teil A, 10.4, Schritt 7b)", hoehe, luecke));
        for (int ty = 0; ty < kn; ty++)
            for (int tx = 0; tx < ko; tx++) {
                float[] k = new float[kz * kz];
                Arrays.fill(k, Float.NaN);
                for (int j = 0; j < kz; j++)
                    for (int i = 0; i < kz; i++) if (zu(ca + tx * kz + i, ra + ty * kz + j)) k[j * kz + i] = 1f;
                st.schreibe(tx, ty, k);
            }
        return st;
    }

    /** Ein Punkt zwischen den Pixelmitten einer Maske als Stufe (1 = geschlossen): ab zwei von vier Nachbarn. */
    public static boolean zu(Gelaende.Stufe maske, double ost, double nord) { return zu(maske.leser(), maske, ost, nord); }

    /** Wie {@link #zu(Gelaende.Stufe, double, double)} mit einem Leser der Maske. */
    public static boolean zu(Gelaende.Stufe.Leser l, Gelaende.Stufe maske, double ost, double nord) {
        double c = maske.spalte(ost), r = maske.zeile(nord);
        int i = (int) Math.floor(c), j = (int) Math.floor(r);
        int n = 0;
        if (l.wert(i, j) == 1f) n++;
        if (l.wert(i + 1, j) == 1f) n++;
        if (l.wert(i, j + 1) == 1f) n++;
        if (l.wert(i + 1, j + 1) == 1f) n++;
        return n >= 2;
    }

    // ------------------------------------------------------------------ Bitfelder

    private long maske(int k) { return k < s - 1 || (w & 63) == 0 ? -1L : (1L << (w & 63)) - 1; }

    private long zaehle(long[] b) {
        long n = 0;
        for (int v = 0; v < h; v++) for (int k = 0; k < s; k++) n += Long.bitCount(b[v * s + k] & maske(k));
        return n;
    }

    /** Setzt (true) oder löscht (false) die Bits hinter dem letzten Pixel jeder Zeile. */
    private void fuelleRand(long[] b, boolean an) {
        if ((w & 63) == 0) return;
        long m = maske(s - 1);
        for (int v = 0; v < h; v++) b[v * s + s - 1] = an ? b[v * s + s - 1] | ~m : b[v * s + s - 1] & m;
    }

    private void dehneX(long[] b) {
        IntStream.range(0, h).parallel().forEach(v -> {
            int o = v * s;
            long vor = 0, cur = b[o];
            for (int k = 0; k < s; k++) {
                long nach = k + 1 < s ? b[o + k + 1] : 0;
                b[o + k] = cur | cur << 1 | vor >>> 63 | cur >>> 1 | nach << 63;
                vor = cur;
                cur = nach;
            }
            b[o + s - 1] &= maske(s - 1);
        });
    }

    private void schrumpfeX(long[] b) {
        IntStream.range(0, h).parallel().forEach(v -> {
            int o = v * s;
            long vor = -1L, cur = b[o];
            for (int k = 0; k < s; k++) {
                long nach = k + 1 < s ? b[o + k + 1] : -1L;
                b[o + k] = cur & (cur << 1 | vor >>> 63) & (cur >>> 1 | nach << 63);
                vor = cur;
                cur = nach;
            }
        });
    }

    private void dehneY(long[] b) { zeilen(b, false); }

    private void schrumpfeY(long[] b) { zeilen(b, true); }

    private void zeilen(long[] b, boolean und) {
        IntStream.range(0, (s + 7) / 8).parallel().forEach(g -> {
            int k0 = g * 8, k1 = Math.min(s, k0 + 8);
            long[] vor = new long[k1 - k0];
            Arrays.fill(vor, und ? -1L : 0);
            for (int v = 0; v < h; v++)
                for (int k = k0; k < k1; k++) {
                    long cur = b[v * s + k], nach = v + 1 < h ? b[(v + 1) * s + k] : und ? -1L : 0;
                    b[v * s + k] = und ? vor[k - k0] & cur & nach : vor[k - k0] | cur | nach;
                    vor[k - k0] = cur;
                }
        });
    }

    /** Flutfüllung (vierfach, Zeilenstücke) von allen Randpixeln, die keine Wand sind. */
    private long[] flute(long[] wa) {
        long[] er = new long[s * h];
        long[] st = new long[1 << 16];
        int sp = 0;
        for (int u = 0; u < w; u++) { st = push(st, sp++, u, 0); st = push(st, sp++, u, h - 1); }
        for (int v = 0; v < h; v++) { st = push(st, sp++, 0, v); st = push(st, sp++, w - 1, v); }
        while (sp > 0) {
            long p = st[--sp];
            int u = (int) (p >>> 32), v = (int) p;
            if (!frei(wa, er, u, v)) continue;
            int a = u, b = u;
            while (a > 0 && frei(wa, er, a - 1, v)) a--;
            while (b < w - 1 && frei(wa, er, b + 1, v)) b++;
            for (int x = a; x <= b; x++) er[v * s + (x >>> 6)] |= 1L << (x & 63);
            for (int dv = -1; dv <= 1; dv += 2) {
                int y = v + dv;
                if (y < 0 || y >= h) continue;
                boolean lauf = false;
                for (int x = a; x <= b; x++) {
                    boolean f = frei(wa, er, x, y);
                    if (f && !lauf) {
                        if (sp == st.length) st = Arrays.copyOf(st, st.length * 2);
                        st[sp++] = (long) x << 32 | y;
                    }
                    lauf = f;
                }
            }
        }
        return er;
    }

    private static long[] push(long[] st, int i, int u, int v) {
        if (i == st.length) st = Arrays.copyOf(st, st.length * 2);
        st[i] = (long) u << 32 | v;
        return st;
    }

    private boolean frei(long[] wa, long[] er, int u, int v) {
        int o = v * s + (u >>> 6);
        long bit = 1L << (u & 63);
        return (wa[o] & bit) == 0 && (er[o] & bit) == 0;
    }

    @Override public String toString() {
        double a = tw.dx * tw.dx / 1e6;
        return String.format(Locale.GERMAN, "Raster von TrilaWatt %d × %d Pixel ab Spalte %d, Zeile %d; Wand %.0f km², geschlossen %.0f km², erreicht %.0f km²; geschlossen mit TrilaWatt %.1f km², ohne %.1f km²",
                w, h, ca, ra, wand0 * a, wand * a, erreicht * a, zuMitGrund * a, zuOhneGrund * a);
    }
}
