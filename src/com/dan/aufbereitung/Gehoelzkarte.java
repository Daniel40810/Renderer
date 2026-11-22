package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.szene.Bauten;
import com.dan.core.szene.Gehoelz;
import com.dan.core.szene.Gestalt;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schritt {@code gehoelz} (P12c, F136 A, F137 A): die Gehölze der Hallig aus dem bDOM (Oberfläche, 20 cm) minus DGM1 und
 * dem NDVI des DOP20.
 * <ol>
 * <li>Je Kachel des bDOM (1 km) ein Raster zu 0,4 m mit 20 m Rand aus den Nachbarn: Höhe über dem Boden h = größte
 * Oberfläche der vier 20-cm-Pixel − DGM1 (bilinear), NDVI = (NIR − R)/(NIR + R) im Mittel.</li>
 * <li>Pflanze, wo h ≥ {@link #minHoehe} und NDVI ≥ {@link #minNdvi} und kein Haus (Dreiecke aus {@code bau/gebaeude.bin},
 * 0,8 m breiter) [G].</li>
 * <li>Kronen: Gipfel des geglätteten h (Höchstwert im Kreis r = 0,12 h + 0,6 m, 0,8 … 2,5 m), dann Wachsen der Kronen vom
 * höchsten Gipfel an über Nachbarn, die höchstens 0,5 m höher liegen, bis 10 m vom Gipfel. Je Krone acht Halbmesser
 * (95 % der Pixel je Richtung), Höhe (größtes geglättetes h), Fläche.</li>
 * <li>Art nach Regeln [G]: nass (Wasser der Gestalt im Umkreis 5 m) – Schwarzerle (≥ 8 m), Salweide (darunter); sonst
 * Esche (≥ 8 m), Eberesche (5–8 m), Holunder (2,5–5 m), Hundsrose (darunter).</li>
 * </ol>
 * Die Werte für Prüfstein 17 (Teil der Daten): Anteil der Pflanzenfläche mit h ≥ 2 m, die eine Krone bedeckt, und Anteil
 * der Kronen, der keine Pflanze ist.
 */
public final class Gehoelzkarte {
    static final Pattern BDOM = Pattern.compile("bdom20\\w*_32_(\\d{3})_(\\d{4})_1_sh_\\d{4}\\.tif");
    static final Pattern DOP = Pattern.compile("dop20rgbi_32_(\\d{3})_(\\d{4})_1_sh_\\d{4}(?:_bw)?\\.tif");

    public double zelle = 0.4, rand = 20, minHoehe = 1.0, minNdvi = 0.15, hausRand = 0.8, wachsen = 0.5, weit = 10, nass = 5;

    public final List<Gehoelz.Pflanze> pflanzen = new ArrayList<>();
    public final StringBuilder bericht = new StringBuilder();
    /** Prüfstein 17 (Daten): Pflanzenfläche mit h ≥ 2 m unter einer Krone; Kronenfläche ohne Pflanze. */
    public long flaecheHoch, flaecheHochBedeckt, flaecheKrone, flaecheKroneLeer;
    public int kacheln;

    private Gelaende.Abfrage ab;
    private Bauten haeuser;
    private Gestalt gestalt;
    private double e0, n0;

    /**
     * bdom, dop: Ordner der Kacheln; gebiet: Ost0, Nord0, Ost1, Nord1; e0, n0: Ursprung des Raums der Bauten.
     * haeuser und gestalt dürfen null sein.
     */
    public void bauen(Path bdom, Path dop, Gelaende gel, Bauten haeuser, Gestalt gestalt, double[] gebiet, double e0, double n0) throws IOException {
        this.ab = gel.abfrage();
        this.haeuser = haeuser;
        this.gestalt = gestalt;
        this.e0 = e0;
        this.n0 = n0;
        Map<Long, Path> b = kacheln(bdom, BDOM), d = kacheln(dop, DOP);
        List<Long> liste = new ArrayList<>(b.keySet());
        liste.sort(null);
        for (long k : liste) {
            int ek = (int) (k >> 16), nk = (int) (k & 0xFFFF);
            double x0 = ek * 1000.0, y0 = nk * 1000.0;
            if (x0 + 1000 <= gebiet[0] || x0 >= gebiet[2] || y0 + 1000 <= gebiet[1] || y0 >= gebiet[3]) continue;
            if (gestalt != null && !landIn(x0, y0)) continue;                      // nur wo die Gestalt Land kennt
            kachel(ek, nk, b, d);
            kacheln++;
        }
        int[] z = new int[Gehoelz.ARTEN];
        for (Gehoelz.Pflanze p : pflanzen) z[p.art]++;
        StringBuilder a = new StringBuilder();
        for (int i = 0; i < Gehoelz.ARTEN; i++) a.append(i > 0 ? ", " : "").append(Gehoelz.name(i)).append(' ').append(z[i]);
        bericht.append(String.format(Locale.ROOT, "%d Kacheln, %d Gehölze (%s); Prüfstein 17 (Daten): Pflanzen ≥ 2 m unter einer Krone %.1f %%, "
                        + "Krone ohne Pflanze %.1f %%", kacheln, pflanzen.size(), a, 100.0 * bedeckt(), 100.0 * leer()));
    }

    public double bedeckt() { return flaecheHoch > 0 ? (double) flaecheHochBedeckt / flaecheHoch : Double.NaN; }

    public double leer() { return flaecheKrone > 0 ? (double) flaecheKroneLeer / flaecheKrone : Double.NaN; }

    private boolean landIn(double x0, double y0) {
        for (Gestalt.Kachel k : gestalt.kacheln)
            if (k.ost() < x0 + 1000 && k.ost() + gestalt.kantenlaenge() > x0 && k.nord() < y0 + 1000 && k.nord() + gestalt.kantenlaenge() > y0) return true;
        return false;
    }

    static Map<Long, Path> kacheln(Path ordner, Pattern p) throws IOException {
        Map<Long, Path> m = new HashMap<>();
        if (ordner == null || !Files.isDirectory(ordner)) return m;
        try (var s = Files.list(ordner)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                Matcher x = p.matcher(f.getFileName().toString());
                if (x.matches()) m.put(((long) Integer.parseInt(x.group(1)) << 16) | Integer.parseInt(x.group(2)), f);
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ eine Kachel

    private void kachel(int ek, int nk, Map<Long, Path> bdom, Map<Long, Path> dop) throws IOException {
        double wx0 = ek * 1000.0 - rand, wy1 = nk * 1000.0 + 1000 + rand;           // Fenster: Westkante, Nordkante
        int n = (int) Math.round((1000 + 2 * rand) / zelle), f = (int) Math.round(zelle / 0.2);   // Zellen; 20-cm-Pixel je Zelle
        int m = n * f;                                                              // 20-cm-Pixel im Fenster
        float[] ob = fenster(bdom, wx0, wy1, m, 0, Float.NaN);
        float[] rot = fenster(dop, wx0, wy1, m, 0, Float.NaN), nir = fenster(dop, wx0, wy1, m, 3, Float.NaN);
        // DGM1 für das Fenster, 1 m, mit einem Pixel Rand
        int gm = (int) Math.ceil((1000 + 2 * rand)) + 2;
        float[] dgm = new float[gm * gm];
        for (int j = 0; j < gm; j++)
            for (int i = 0; i < gm; i++) dgm[j * gm + i] = ab.hoehe(wx0 + i - 0.5, wy1 - j + 0.5, 1);
        float[] h = new float[n * n], ndvi = new float[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = (i + 0.5) * zelle, y = (j + 0.5) * zelle;                // vom Nordwesten des Fensters
                float boden = bilinear(dgm, gm, x + 0.5, y + 0.5);
                float hm = Float.NEGATIVE_INFINITY, r = 0, ni = 0;
                int z = 0;
                for (int b = 0; b < f; b++)
                    for (int a = 0; a < f; a++) {
                        int o = (j * f + b) * m + i * f + a;
                        if (!Float.isNaN(ob[o])) hm = Math.max(hm, ob[o]);
                        if (!Float.isNaN(rot[o]) && !Float.isNaN(nir[o])) { r += rot[o]; ni += nir[o]; z++; }
                    }
                h[j * n + i] = hm == Float.NEGATIVE_INFINITY || Float.isNaN(boden) ? Float.NaN : hm - boden;
                ndvi[j * n + i] = z == 0 || r + ni <= 0 ? Float.NaN : (ni - r) / (ni + r);
            }
        boolean[] haus = hausMaske(wx0, wy1, n);
        boolean[] veg = new boolean[n * n];
        for (int k = 0; k < n * n; k++) veg[k] = !haus[k] && h[k] >= minHoehe && ndvi[k] >= minNdvi;
        // geglättet (3 × 3, nur Pflanzen)
        float[] hs = new float[n * n];
        for (int j = 1; j < n - 1; j++)
            for (int i = 1; i < n - 1; i++) {
                int k = j * n + i;
                if (!veg[k]) continue;
                float s = 0;
                int z = 0;
                for (int b = -1; b <= 1; b++)
                    for (int a = -1; a <= 1; a++) {
                        int q = k + b * n + a;
                        if (veg[q]) { s += h[q]; z++; }
                    }
                hs[k] = s / z;
            }
        // Gipfel
        List<int[]> gipfel = new ArrayList<>();
        for (int j = 1; j < n - 1; j++)
            for (int i = 1; i < n - 1; i++) {
                int k = j * n + i;
                if (!veg[k] || hs[k] < minHoehe + 0.5) continue;
                double r = Math.max(0.8, Math.min(2.5, 0.12 * hs[k] + 0.6));
                int rz = (int) Math.ceil(r / zelle);
                boolean max = true;
                for (int b = -rz; b <= rz && max; b++)
                    for (int a = -rz; a <= rz && max; a++) {
                        if (a == 0 && b == 0 || (a * a + b * b) * zelle * zelle > r * r) continue;
                        int ii = i + a, jj = j + b;
                        if (ii < 0 || jj < 0 || ii >= n || jj >= n) continue;
                        float v = hs[jj * n + ii];
                        if (v > hs[k] || v == hs[k] && (jj * n + ii) < k) max = false;     // gleich hoch: der erste gewinnt
                    }
                if (max) gipfel.add(new int[] {i, j});
            }
        // Wachsen
        int[] marke = new int[n * n];
        Arrays.fill(marke, -1);
        PriorityQueue<float[]> q = new PriorityQueue<>((u, v) -> Float.compare(v[0], u[0]));
        for (int g = 0; g < gipfel.size(); g++) {
            int k = gipfel.get(g)[1] * n + gipfel.get(g)[0];
            marke[k] = g;
            q.add(new float[] {hs[k], k, g});
        }
        int[] nb = {1, -1, n, -n};
        double weit2 = (weit / zelle) * (weit / zelle);
        while (!q.isEmpty()) {
            float[] e = q.poll();
            int k = (int) e[1], g = (int) e[2];
            int gi = gipfel.get(g)[0], gj = gipfel.get(g)[1];
            for (int o : nb) {
                int k2 = k + o;
                if (k2 < 0 || k2 >= n * n || Math.abs(k2 % n - k % n) > 1) continue;
                if (!veg[k2] || marke[k2] >= 0) continue;
                if (hs[k2] > hs[k] + wachsen) continue;
                int i2 = k2 % n, j2 = k2 / n;
                if ((double) (i2 - gi) * (i2 - gi) + (double) (j2 - gj) * (j2 - gj) > weit2) continue;
                marke[k2] = g;
                q.add(new float[] {hs[k2], k2, g});
            }
        }
        // Kronen
        int gz = gipfel.size();
        List<List<float[]>> je = new ArrayList<>(gz);     // je Gipfel: (Richtung, Abstand)
        float[] top = new float[gz];
        int[] zahl = new int[gz];
        for (int g = 0; g < gz; g++) je.add(new ArrayList<>());
        for (int k = 0; k < n * n; k++) {
            int g = marke[k];
            if (g < 0) continue;
            int gi = gipfel.get(g)[0], gj = gipfel.get(g)[1];
            double dx = (k % n - gi) * zelle, dy = -(k / n - gj) * zelle;                       // nach Ost, nach Nord
            je.get(g).add(new float[] {(float) Math.atan2(dy, dx), (float) Math.hypot(dx, dy)});
            top[g] = Math.max(top[g], hs[k]);
            zahl[g]++;
        }
        boolean[] krone = new boolean[n * n];
        int kern0 = (int) Math.round(rand / zelle), kern1 = n - kern0;
        for (int g = 0; g < gz; g++) {
            int gi = gipfel.get(g)[0], gj = gipfel.get(g)[1];
            double flaeche = zahl[g] * zelle * zelle;
            if (flaeche < 0.6 || top[g] < minHoehe) continue;
            Gehoelz.Pflanze p = new Gehoelz.Pflanze();
            float[][] sek = new float[Gehoelz.RICHTUNGEN][];
            int[] sz = new int[Gehoelz.RICHTUNGEN];
            for (float[] v : je.get(g)) sz[sektor(v[0])]++;
            for (int s = 0; s < Gehoelz.RICHTUNGEN; s++) sek[s] = new float[sz[s]];
            Arrays.fill(sz, 0);
            for (float[] v : je.get(g)) { int s = sektor(v[0]); sek[s][sz[s]++] = v[1]; }
            for (int s = 0; s < Gehoelz.RICHTUNGEN; s++) {
                if (sek[s].length == 0) { p.krone[s] = -1; continue; }
                Arrays.sort(sek[s]);
                p.krone[s] = sek[s][(int) Math.floor(0.95 * (sek[s].length - 1))] + (float) (zelle / 2);
            }
            for (int s = 0; s < Gehoelz.RICHTUNGEN; s++)                                  // leere Richtung: halbes Mittel der Nachbarn
                if (p.krone[s] < 0) {
                    float a = p.krone[(s + 1) % Gehoelz.RICHTUNGEN], c = p.krone[(s + Gehoelz.RICHTUNGEN - 1) % Gehoelz.RICHTUNGEN];
                    p.krone[s] = (float) Math.max(zelle / 2, 0.25 * (Math.max(a, 0) + Math.max(c, 0)));
                }
            // Krone als Fläche (für die Prüfung) – nur im Kern der Kachel
            krone(krone, n, gi, gj, p);
            if (gi < kern0 || gi >= kern1 || gj < kern0 || gj >= kern1) continue;    // Gipfel im Rand: gehört der Nachbarkachel
            double ost = wx0 + (gi + 0.5) * zelle, nord = wy1 - (gj + 0.5) * zelle;
            p.x = (float) (ost - e0);
            p.z = (float) (n0 - nord);
            float bo = ab.hoehe(ost, nord, 1);
            p.boden = Float.isNaN(bo) ? 0 : bo;
            p.hoehe = top[g];
            p.flaeche = (float) flaeche;
            p.art = art(p.hoehe, ost, nord);
            pflanzen.add(p);
        }
        // Prüfstein 17 (Daten) im Kern
        for (int j = kern0; j < kern1; j++)
            for (int i = kern0; i < kern1; i++) {
                int k = j * n + i;
                if (veg[k] && h[k] >= 2) {
                    flaecheHoch++;
                    if (krone[k]) flaecheHochBedeckt++;
                }
                if (krone[k]) {
                    flaecheKrone++;
                    if (!veg[k]) flaecheKroneLeer++;
                }
            }
    }

    static int sektor(double a) {
        double t = a / (2 * Math.PI / Gehoelz.RICHTUNGEN);
        int s = (int) Math.round(t);
        return ((s % Gehoelz.RICHTUNGEN) + Gehoelz.RICHTUNGEN) % Gehoelz.RICHTUNGEN;
    }

    /** Die Krone als Polygon aus acht Halbmessern ins Raster (Zellen vom Nordwesten). */
    private void krone(boolean[] m, int n, int gi, int gj, Gehoelz.Pflanze p) {
        int r = (int) Math.ceil(p.kroneMax() / zelle) + 1;
        for (int b = -r; b <= r; b++)
            for (int a = -r; a <= r; a++) {
                int i = gi + a, j = gj + b;
                if (i < 0 || j < 0 || i >= n || j >= n) continue;
                double dx = a * zelle, dy = -b * zelle, d = Math.hypot(dx, dy);
                if (d <= p.kroneIn(Math.atan2(dy, dx))) m[j * n + i] = true;
            }
    }

    private int art(float h, double ost, double nord) {
        boolean wasser = false;
        if (gestalt != null)
            for (double dy = -nass; dy <= nass && !wasser; dy += 1)
                for (double dx = -nass; dx <= nass && !wasser; dx += 1) {
                    if (dx * dx + dy * dy > nass * nass) continue;
                    int[] w = gestalt.bei(ost + dx, nord + dy);
                    if (w != null && Gestalt.wasser(w[0])) wasser = true;
                }
        if (wasser) return h >= 8 ? Gehoelz.SCHWARZERLE : Gehoelz.SALWEIDE;
        if (h >= 8) return Gehoelz.ESCHE;
        if (h >= 5) return Gehoelz.EBERESCHE;
        if (h >= 2.5) return Gehoelz.HOLUNDER;
        return Gehoelz.HUNDSROSE;
    }

    static float bilinear(float[] g, int gm, double x, double y) {
        int i = (int) Math.floor(x - 0.5), j = (int) Math.floor(y - 0.5);
        double fx = x - 0.5 - i, fy = y - 0.5 - j;
        i = Math.max(0, Math.min(gm - 2, i));
        j = Math.max(0, Math.min(gm - 2, j));
        float a = g[j * gm + i], b = g[j * gm + i + 1], c = g[(j + 1) * gm + i], d = g[(j + 1) * gm + i + 1];
        return (float) ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy);
    }

    /** Ein Fenster m × m (20 cm, vom Nordwesten wx0, wy1) aus den Kacheln, ein Kanal; fehlende Pixel = leer. */
    static float[] fenster(Map<Long, Path> kacheln, double wx0, double wy1, int m, int band, float leer) throws IOException {
        float[] f = new float[m * m];
        Arrays.fill(f, leer);
        double wx1 = wx0 + m * 0.2, wy0 = wy1 - m * 0.2;
        for (int ek = (int) Math.floor(wx0 / 1000); ek * 1000.0 < wx1; ek++)
            for (int nk = (int) Math.floor(wy0 / 1000); nk * 1000.0 < wy1; nk++) {
                Path p = kacheln.get(((long) ek << 16) | nk);
                if (p == null) continue;
                try (GeoTiff t = GeoTiff.oeffne(p)) {
                    double kx0 = Math.max(wx0, ek * 1000.0), kx1 = Math.min(wx1, ek * 1000.0 + 1000);
                    double ky0 = Math.max(wy0, nk * 1000.0), ky1 = Math.min(wy1, nk * 1000.0 + 1000);
                    int sx = (int) Math.round((kx0 - ek * 1000.0) / 0.2), sy = (int) Math.round((nk * 1000.0 + 1000 - ky1) / 0.2);
                    int w = (int) Math.round((kx1 - kx0) / 0.2), h = (int) Math.round((ky1 - ky0) / 0.2);
                    if (w <= 0 || h <= 0) continue;
                    float[] d = t.lies(sx, sy, w, h, band);
                    int ox = (int) Math.round((kx0 - wx0) / 0.2), oy = (int) Math.round((wy1 - ky1) / 0.2);
                    for (int j = 0; j < h; j++) System.arraycopy(d, j * w, f, (oy + j) * m + ox, w);
                }
            }
        return f;
    }

    /** Wo Häuser stehen (Dreiecke der Bauten, um {@link #hausRand} breiter). */
    private boolean[] hausMaske(double wx0, double wy1, int n) {
        boolean[] m = new boolean[n * n];
        if (haeuser == null) return m;
        float[] s = haeuser.scheitel;
        int F = Bauten.FLOATS;
        double wx1 = wx0 + n * zelle, wy0 = wy1 - n * zelle;
        for (int t = 0; t + 2 < s.length / F; t += 3) {
            double[] xs = new double[3], ys = new double[3];
            for (int v = 0; v < 3; v++) {
                xs[v] = e0 + s[(t + v) * F];
                ys[v] = n0 - s[(t + v) * F + 2];
            }
            double x0 = Math.min(xs[0], Math.min(xs[1], xs[2])) - hausRand, x1 = Math.max(xs[0], Math.max(xs[1], xs[2])) + hausRand;
            double y0 = Math.min(ys[0], Math.min(ys[1], ys[2])) - hausRand, y1 = Math.max(ys[0], Math.max(ys[1], ys[2])) + hausRand;
            if (x1 < wx0 || x0 > wx1 || y1 < wy0 || y0 > wy1) continue;
            int i0 = Math.max(0, (int) Math.floor((x0 - wx0) / zelle)), i1 = Math.min(n - 1, (int) Math.floor((x1 - wx0) / zelle));
            int j0 = Math.max(0, (int) Math.floor((wy1 - y1) / zelle)), j1 = Math.min(n - 1, (int) Math.floor((wy1 - y0) / zelle));
            for (int j = j0; j <= j1; j++)
                for (int i = i0; i <= i1; i++) {
                    double x = wx0 + (i + 0.5) * zelle, y = wy1 - (j + 0.5) * zelle;
                    if (abstandDreieck(x, y, xs, ys) <= hausRand) m[j * n + i] = true;
                }
        }
        return m;
    }

    static double abstandDreieck(double x, double y, double[] xs, double[] ys) {
        double d1 = (x - xs[1]) * (ys[0] - ys[1]) - (xs[0] - xs[1]) * (y - ys[1]);
        double d2 = (x - xs[2]) * (ys[1] - ys[2]) - (xs[1] - xs[2]) * (y - ys[2]);
        double d3 = (x - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (y - ys[0]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        if (!(neg && pos)) return 0;
        double m = Double.MAX_VALUE;
        for (int a = 0; a < 3; a++) {
            int b = (a + 1) % 3;
            double dx = xs[b] - xs[a], dy = ys[b] - ys[a], l2 = dx * dx + dy * dy;
            double t = l2 > 0 ? Math.max(0, Math.min(1, ((x - xs[a]) * dx + (y - ys[a]) * dy) / l2)) : 0;
            m = Math.min(m, Math.hypot(x - xs[a] - t * dx, y - ys[a] - t * dy));
        }
        return m;
    }

    /** Schreibt {@code bau/gehoelz.bin}, {@code bau/gehoelz.txt} und die Werte für den GehoelzCheck. */
    public void schreibe(File bauOrdner, String kopf) throws IOException {
        Gehoelz g = new Gehoelz(e0, n0, pflanzen);
        g.schreibe(bauOrdner);
        StringBuilder t = new StringBuilder("# Gehölze (Schritt gehoelz, Werkbuch HalligHooge Datei 76); " + kopf + "\n# " + bericht
                + "\n# Ost Nord Boden Höhe Krone_mittel Krone_max Fläche Art\n");
        for (Gehoelz.Pflanze p : pflanzen)
            t.append(String.format(Locale.ROOT, "%.1f %.1f %.2f %.2f %.2f %.2f %.1f %s%n", e0 + p.x, n0 - p.z, p.boden, p.hoehe, p.kroneMittel(),
                    p.kroneMax(), p.flaeche, Gehoelz.name(p.art)));
        Files.writeString(new File(bauOrdner, "gehoelz.txt").toPath(), t, StandardCharsets.UTF_8);
        Files.writeString(new File(bauOrdner, "gehoelz_pruefung.txt").toPath(), String.format(Locale.ROOT,
                "# Schritt gehoelz, Prüfstein 17 (Daten)%nkacheln = %d%ngehoelze = %d%nhoch = %d%nhoch_bedeckt = %d%nkrone = %d%nkrone_leer = %d%nzelle = %.2f%n",
                kacheln, pflanzen.size(), flaecheHoch, flaecheHochBedeckt, flaecheKrone, flaecheKroneLeer, zelle), StandardCharsets.UTF_8);
    }
}
