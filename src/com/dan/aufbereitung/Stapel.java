package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Baut den Kachelstapel des Feingeländes (Werkbuch HalligHooge Teil A, 10.4, Schritte 1–4): die Grundstufe aus einem
 * GeoTIFF ({@link #grund}) und die Landstufe aus gemessenen Kacheln mit Saum und Hof, danach die Mittel in der
 * Grundstufe ({@link #land}).
 * <p>
 * <b>Saum.</b> Außerhalb der gemessenen Fläche wird der Grund zur Randhöhe des Lands hin verschoben. Jedes Randpixel e
 * des Lands hat den Unterschied Δ_e = z_L(e) − z_G(e) und eine Breite s_e = |Δ_e| / steigung, mindestens {@code saum},
 * höchstens {@code saumMax}; so ist die Rampe nie steiler als {@code steigung}. Im Abstand d_e &lt; s_e (euklidisch, von
 * Mitte zu Mitte) hat es den Rest t_e = 1 − d_e/s_e und das Gewicht w_e = t_e/d_e. Dann ist
 * z = z_G(p) + max t_e · Σ w_e Δ_e / Σ w_e. Das Mittel statt des nächsten Randpixels hält den Saum auch zwischen zwei
 * Landstücken verschiedener Höhe stetig (Hafen: Kaje 3,6 m, Becken 0,5 m), und mit dem Gewicht 1/d statt 1/d² ist der
 * Übergang zwischen zwei Rändern linear statt s-förmig; weil jedes Gewicht am Ende seines Umkreises gegen null geht, ist
 * er auch dort stetig. z_G ist der Grund bilinear, genau wie {@link Gelaende} ihn liest. Summiert
 * wird in der Folge der Zeilen von Nord und der Spalten von West, also für jedes Pixel gleich, in welcher Kachel es
 * gerechnet wird.
 * <p>
 * <b>Hof.</b> Ein Pixel des Grunds, in dem Land oder Saum liegt, bekommt das Mittel des zusammengesetzten Felds. Damit
 * eine Abtastung mit dem Schritt der Landstufe diese geänderten Pixel nie liest, trägt die Landstufe auch jedes Pixel,
 * dessen vier bilineare Nachbarn im Grund einen solchen Pixel enthalten, mit dem Grund wie vorher. So sieht die
 * 1-m-Abtastung außerhalb der Landstufe genau den Grund der Quelle, und die Grundstufe hält über Land das Mittel.
 */
public final class Stapel {
    private Stapel() { }

    /** Zählwerte der Landstufe. */
    public static final class Ergebnis {
        public long land, saum, hof, grundPixel;
        public int kacheln;
        public double groessteVerschiebung;
        /** Pixel des Grunds (Spalte, Zeile) mit Land oder Saum: Schlüssel {@link Fuellwert#schluessel}. */
        public final Set<Long> geaendert = new HashSet<>();

        @Override public String toString() {
            return String.format(java.util.Locale.GERMAN, "%d Kacheln, %d Pixel Land, %d Saum, %d Hof; %d Pixel des Grunds mit Mittel; größte Verschiebung im Saum %.3f m",
                    kacheln, land, saum, hof, grundPixel, groessteVerschiebung);
        }
    }

    /**
     * Legt die Grundstufe aus einem GeoTIFF an: das Fenster [west, ost) × [sued, nord) im Raster der Quelle (auf ganze
     * Pixel nach außen gerundet), Kacheln zu {@code kachelZellen}; vorhandene Kacheln der Stufe werden ersetzt.
     */
    public static Gelaende.Stufe grund(GeoTiff q, File ordner, double west, double sued, double ost, double nord, int kachelZellen, String kommentar) throws IOException {
        if (q.dx <= 0 || q.dy >= 0 || Math.abs(q.dx + q.dy) > 1e-9 * q.dx) throw new IOException(q.datei + ": Grund braucht quadratische Pixel, Norden oben");
        double d = q.dx;
        int c0 = (int) Math.floor((west - q.x0) / d + 1e-9), c1 = (int) Math.ceil((ost - q.x0) / d - 1e-9);
        int r0 = (int) Math.floor((q.y0 - nord) / d + 1e-9), r1 = (int) Math.ceil((q.y0 - sued) / d - 1e-9);
        int ko = (c1 - c0 + kachelZellen - 1) / kachelZellen, kn = (r1 - r0 + kachelZellen - 1) / kachelZellen;
        double swO = q.x0 + c0 * d, nordrand = q.y0 - r0 * d, swN = nordrand - kn * kachelZellen * d;
        leere(ordner);
        Gelaende.Stufe s = Gelaende.Stufe.anlegen(ordner, d, kachelZellen, swO, swN, ko, kn, kommentar);
        int n = kachelZellen;
        for (int ty = 0; ty < kn; ty++) {
            float[] band = q.lies(c0, r0 + ty * n, ko * n, n);
            for (int tx = 0; tx < ko; tx++) {
                float[] k = new float[n * n];
                for (int r = 0; r < n; r++) System.arraycopy(band, r * ko * n + tx * n, k, r * n, n);
                s.schreibe(tx, ty, k);
            }
        }
        return s;
    }

    /** Löscht die Kacheln (.r32) eines Ordners. */
    static void leere(File ordner) throws IOException {
        File[] alt = ordner.listFiles((f, n) -> n.endsWith(".r32") || n.endsWith(".r32.neu"));
        if (alt != null) for (File f : alt) java.nio.file.Files.delete(f.toPath());
    }

    /**
     * Legt die Landstufe an und setzt die Mittel in der Grundstufe.
     *
     * @param land  gemessene Kacheln (Füllwert schon entfernt): Schlüssel {@link Fuellwert#schluessel}(Ost km, Nord km),
     *              Werte n × n zu {@code zelle} m, Zeilen von Nord
     * @param grund die Grundstufe; ihre Pixel müssen ganze Vielfache der Landpixel sein und auf deren Grenzen liegen
     * @param west  Südwestecke und Ausdehnung der Landstufe (m), ganze Kacheln
     * @param saum     kleinste Breite des Saums (m)
     * @param saumMax  größte Breite des Saums (m)
     * @param steigung größte Neigung der Rampe im Saum (m je m): die Breite an einem Randpixel ist |Δ| / steigung,
     *                 zwischen saum und saumMax
     */
    public static Ergebnis land(Map<Long, float[]> land, int n, double zelle, Gelaende.Stufe grund, File ordner, double west, double sued, int kachelnOst,
                                int kachelnNord, double saum, double saumMax, double steigung, String kommentar) throws IOException {
        double kachel = n * zelle;
        int f = (int) Math.round(grund.zelle / zelle);
        if (Math.abs(f * zelle - grund.zelle) > 1e-9 || Math.abs(Math.IEEEremainder(grund.suedwestOst - west, zelle)) > 1e-9
                || Math.abs(Math.IEEEremainder(grund.suedwestNord - sued, zelle)) > 1e-9)
            throw new IllegalArgumentException("Landstufe: Grundpixel " + grund.zelle + " m liegen nicht auf dem Raster der Landpixel " + zelle + " m");
        leere(ordner);
        Gelaende.Stufe s = Gelaende.Stufe.anlegen(ordner, zelle, n, west, sued, kachelnOst, kachelnNord, kommentar);
        if (!(saum > 0) || saumMax < saum || !(steigung > 0)) throw new IllegalArgumentException("Saum " + saum + " bis " + saumMax + " m, Steigung " + steigung);
        int saumPx = (int) Math.ceil(saumMax / zelle - 1e-9);
        int rand = saumPx + 2 * f + 2;                                                     // Saum, dazu der Hof (bis anderthalb Grundpixel)
        int w = n + 2 * rand;
        // Kacheln der Stufe, die Land haben oder neben Land liegen
        Set<Long> kandidaten = new HashSet<>();
        for (long k : land.keySet()) {
            int ek = Fuellwert.spalte(k), nk = Fuellwert.zeile(k);
            int tx = (int) Math.round((ek * kachel - west) / kachel), tyS = (int) Math.round((nk * kachel - sued) / kachel);
            for (int a = -1; a <= 1; a++)
                for (int b = -1; b <= 1; b++) {
                    int x = tx + a, y = tyS + b;
                    if (x >= 0 && y >= 0 && x < kachelnOst && y < kachelnNord) kandidaten.add(Fuellwert.schluessel(x, y));
                }
        }
        Ergebnis e = new Ergebnis();
        List<Long> liste = new ArrayList<>(kandidaten);
        liste.sort(Long::compare);
        float[] lw = new float[w * w], zs = new float[w * w];
        double[] gs = new double[w * w], ds = new double[w * w], fade = new double[w * w];
        boolean[] istSaum = new boolean[w * w];
        for (long key : liste) {
            int tx = Fuellwert.spalte(key), tyS = Fuellwert.zeile(key);
            double ostT = west + tx * kachel, nordT = sued + (tyS + 1) * kachel;              // Nordwestecke der Kachel
            double ox = ostT - rand * zelle, oy = nordT + rand * zelle;                         // Nordwestecke des Fensters
            // 1. Land im Fenster
            boolean hatLand = false;
            for (int v = 0; v < w; v++) {
                double y = oy - (v + 0.5) * zelle;
                int nk = (int) Math.floor(y / kachel), j = n - 1 - (int) Math.floor((y - nk * kachel) / zelle);
                int letzteEk = Integer.MIN_VALUE;
                float[] t = null;
                for (int u = 0; u < w; u++) {
                    double x = ox + (u + 0.5) * zelle;
                    int ek = (int) Math.floor(x / kachel);
                    if (ek != letzteEk) {
                        t = land.get(Fuellwert.schluessel(ek, nk));
                        letzteEk = ek;
                    }
                    float z = Float.NaN;
                    if (t != null) z = t[j * n + (int) Math.floor((x - ek * kachel) / zelle)];
                    lw[v * w + u] = z;
                    if (!Float.isNaN(z)) hatLand = true;
                }
            }
            if (!hatLand) continue;
            // 2. Saum: Verschiebung aller Randpixel im Umkreis, gewichtet mit (1 − d/s)/d, abklingend mit dem stärksten Rest
            Arrays.fill(fade, 0);
            Arrays.fill(istSaum, false);
            Arrays.fill(gs, 0);
            Arrays.fill(ds, 0);
            for (int v = 0; v < w; v++)
                for (int u = 0; u < w; u++) {
                    int p = v * w + u;
                    if (Float.isNaN(lw[p])) continue;
                    boolean randPixel = u > 0 && Float.isNaN(lw[p - 1]) || u < w - 1 && Float.isNaN(lw[p + 1]) || v > 0 && Float.isNaN(lw[p - w]) || v < w - 1 && Float.isNaN(lw[p + w]);
                    if (!randPixel) continue;
                    double delta = lw[p] - grund.bilinear(ox + (u + 0.5) * zelle, oy - (v + 0.5) * zelle);
                    if (Double.isNaN(delta)) continue;
                    double se = Math.min(saumMax, Math.max(saum, Math.abs(delta) / steigung));     // Breite dieses Randpixels (m)
                    int r = (int) Math.ceil(se / zelle - 1e-9);
                    for (int b = -r; b <= r; b++)
                        for (int a = -r; a <= r; a++) {
                            int uu = u + a, vv = v + b;
                            if (uu < 0 || vv < 0 || uu >= w || vv >= w) continue;
                            int q = vv * w + uu;
                            if (!Float.isNaN(lw[q])) continue;
                            double d = Math.sqrt(a * a + b * b) * zelle, t = 1 - d / se;
                            if (t <= 0) continue;
                            double g = t / d;
                            gs[q] += g;
                            ds[q] += g * delta;
                            if (t > fade[q]) fade[q] = t;
                            istSaum[q] = true;
                        }
                }
            for (int p = 0; p < w * w; p++) {
                if (!istSaum[p]) continue;
                int u = p % w, v = p / w;
                double x = ox + (u + 0.5) * zelle, y = oy - (v + 0.5) * zelle;
                double verschiebung = fade[p] * ds[p] / gs[p];
                zs[p] = (float) (grund.bilinear(x, y) + verschiebung);
                if (u >= rand && v >= rand && u < rand + n && v < rand + n) e.groessteVerschiebung = Math.max(e.groessteVerschiebung, Math.abs(verschiebung));
            }
            // 3. Pixel des Grunds mit Land oder Saum, deren Fläche ganz im Fenster liegt
            int gc0 = (int) Math.floor(grund.spalte(ox) - 1), gc1 = (int) Math.ceil(grund.spalte(ox + w * zelle) + 1);
            int gr0 = (int) Math.floor(grund.zeile(oy) - 1), gr1 = (int) Math.ceil(grund.zeile(oy - w * zelle) + 1);
            int gw = gc1 - gc0 + 1;
            boolean[] u0 = new boolean[gw * (gr1 - gr0 + 1)];
            List<Long> u0Liste = new ArrayList<>();
            for (int gr = gr0; gr <= gr1; gr++)
                for (int gc = gc0; gc <= gc1; gc++) {
                    int u1 = (int) Math.round((grund.ost(gc) - grund.zelle / 2 - ox) / zelle), v1 = (int) Math.round((oy - grund.nord(gr) - grund.zelle / 2) / zelle);
                    if (u1 < 0 || v1 < 0 || u1 + f > w || v1 + f > w) continue;
                    boolean mit = false;
                    for (int b = 0; b < f && !mit; b++)
                        for (int a = 0; a < f; a++) {
                            int p = (v1 + b) * w + u1 + a;
                            if (!Float.isNaN(lw[p]) || istSaum[p]) { mit = true; break; }
                        }
                    if (mit) {
                        u0[(gr - gr0) * gw + gc - gc0] = true;
                        u0Liste.add(Fuellwert.schluessel(gc, gr));
                    }
                }
            // 4. die Kachel
            float[] k = new float[n * n];
            Arrays.fill(k, Float.NaN);
            boolean etwas = false;
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    int p = (j + rand) * w + i + rand;
                    double x = ostT + (i + 0.5) * zelle, y = nordT - (j + 0.5) * zelle;
                    float z;
                    if (!Float.isNaN(lw[p])) { z = lw[p]; e.land++; }
                    else if (istSaum[p]) { z = zs[p]; e.saum++; }
                    else {
                        int ci = (int) Math.floor(grund.spalte(x)) - gc0, ri = (int) Math.floor(grund.zeile(y)) - gr0;
                        boolean hof = u0[ri * gw + ci] || u0[ri * gw + ci + 1] || u0[(ri + 1) * gw + ci] || u0[(ri + 1) * gw + ci + 1];
                        if (!hof) continue;
                        z = grund.bilinear(x, y);
                        e.hof++;
                    }
                    k[j * n + i] = z;
                    etwas = true;
                }
            // Pixel des Grunds, die diese Kachel berühren
            for (long g : u0Liste) {
                double gx = grund.ost(Fuellwert.spalte(g)), gy = grund.nord(Fuellwert.zeile(g)), h = grund.zelle / 2;
                if (gx + h > ostT && gx - h < ostT + kachel && gy + h > nordT - kachel && gy - h < nordT) e.geaendert.add(g);
            }
            int tyN = kachelnNord - 1 - tyS;
            s.schreibe(tx, tyN, k);
            if (etwas) e.kacheln++;
        }
        // 5. Mittel in der Grundstufe
        Gelaende.Stufe landStufe = s;
        Map<Long, float[]> grundKacheln = new HashMap<>();
        for (long g : e.geaendert) {
            int gc = Fuellwert.spalte(g), gr = Fuellwert.zeile(g);
            double gx = grund.ost(gc), gy = grund.nord(gr);
            double summe = 0;
            int zahl = 0;
            for (int b = 0; b < f; b++)
                for (int a = 0; a < f; a++) {
                    double x = gx - grund.zelle / 2 + (a + 0.5) * zelle, y = gy + grund.zelle / 2 - (b + 0.5) * zelle;
                    int sc = (int) Math.round(landStufe.spalte(x)), sr = (int) Math.round(landStufe.zeile(y));
                    float z = landStufe.wert(sc, sr);
                    if (Float.isNaN(z)) z = grund.bilinear(x, y);                               // außerhalb der Landstufe: Grund wie vorher
                    if (!Float.isNaN(z)) { summe += z; zahl++; }
                }
            if (zahl == 0) continue;
            int tx = gc / grund.kachelZellen, ty = gr / grund.kachelZellen;
            long tk = Fuellwert.schluessel(tx, ty);
            float[] t = grundKacheln.computeIfAbsent(tk, x -> grund.kachel(tx, ty).clone());
            if (t.length == 0) continue;
            t[(gr - ty * grund.kachelZellen) * grund.kachelZellen + gc - tx * grund.kachelZellen] = (float) (summe / zahl);
            e.grundPixel++;
        }
        for (Map.Entry<Long, float[]> me : grundKacheln.entrySet())
            if (me.getValue().length > 0) grund.schreibe(Fuellwert.spalte(me.getKey()), Fuellwert.zeile(me.getKey()), me.getValue());
        return e;
    }
}
