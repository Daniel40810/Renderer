package com.dan.lauf.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Kornfeld;
import com.dan.core.gitter.Rasterfeld;
import com.dan.core.pruef.Attest;
import com.dan.core.pruef.Statistik;
import com.dan.lauf.Archivquelle;
import com.dan.physics.sediment.Sediment;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Teil 2 von {@link SedimentLaufCheck} (P9b, Werkbuch HalligHooge Teil A, 12.10): die Prüfsteine aus den Archiven und
 * der Aufnahme der Läufe – 4 (Karte gegen Prozessor), 5 und 6 (Strömung und Kornschubspannung gegen EasyGSH 2015),
 * 7 (Trübung gegen Sentinel-2), 8 (Rippel). Status-Bild {@code docs/status/P9b_Sediment.png}.
 */
final class SedimentFeldCheck {
    private SedimentFeldCheck() { }

    /** Gewicht des Sands in der Optik [G, Feinplan P9b]. */
    static final double SAND_OPTIK = 0.05;
    /** Periode der M2 (s). */
    static final double M2 = 12.4206012 * 3600;

    static void teil2(Attest A, File gb, File ar, File arR, int[] f3) throws Exception {
        A.teil("2 Aus den Archiven");
        vergleich(A, gb, ar, f3, "Sturm");
        if (arR == null) return;
        vergleich(A, gb, arR, f3, "ruhiger Tag");
        Gitter g = SedimentLaufCheck.gitter(gb, f3);
        float[][] karten = new float[4][];
        File o = new File(arR, SedimentLaufCheck.ORDNER);
        if (new File(o, "statistik.bin").isFile()) karten[3] = easygsh(A, gb, g, SedimentLaufCheck.statistik(o, g));
        else A.pruefe(false, "Prüfsteine 5 und 6: Feldvergleich gegen EasyGSH 2015", "Statistik fehlt in " + o, "Rangkorrelation, Verhältnis");
        try (Archivquelle q = new Archivquelle(arR.toPath())) {
            if (q.sedimentGitter() == null) throw new IllegalStateException(arR + ": kein Sediment im Archiv");
            float[][] t = truebung(A, gb, g, q);
            karten[0] = t[0];
            karten[1] = t[1];
            karten[2] = rippel(A, gb, g, q);
        }
        bild(A, g, karten);
    }

    // ------------------------------------------------------------------ Prüfstein 4

    /** Prüfstein 4: Karte gegen Prozessor mit dem aufgenommenen Eingang. */
    static void vergleich(Attest A, File gb, File ar, int[] f3, String wer) throws Exception {
        File o = new File(ar, SedimentLaufCheck.ORDNER);
        if (!new File(o, "aufnahme.bin").isFile()) {
            A.pruefe(false, "Prüfstein 4: Karte gegen Prozessor (" + wer + ")", "Aufnahme fehlt in " + o, "im Mittel ≤ 1 · 10⁻³");
            return;
        }
        SedimentLaufCheck.Eingang ein = SedimentLaufCheck.Eingang.lies(o);
        boolean karte = !SedimentLaufCheck.sediment.equals("prozessor");
        long n0 = System.nanoTime();
        double[] r = SedimentLaufCheck.karteGegenProzessor(gb, f3, ein, karte);
        int[] w = ein.fenster();
        A.info("Aufnahme (" + wer + ")", f("%d × %d Zellen ab Zelle %d, %d des Gitters, %d Intervalle (%.1f h), %s; %.0f Teilschritte; %.0f s", w[2], w[3], w[0], w[1], ein.schritte().size(),
                (ein.zeiten().get(ein.zeiten().size() - 1)[1] - ein.zeiten().get(0)[0]) / 3600, ein.seegang() ? "mit Seegang" : "ohne Seegang", r[9], (System.nanoTime() - n0) / 1e9));
        String wert = f("sed.c Sand %.1e (im Mittel %.2e kg/m³, größte Abweichung %.1e), Schlick %.1e (%.2e kg/m³, %.1e), Bodenänderung %.1e (%.2e m, %.1e m); Bilanz der Karte Sand %.1e, Schlick %.1e", r[0], r[6], r[3],
                r[1], r[7], r[4], r[2], r[8], r[5], r[11], r[12]);
        if (karte) A.pruefe(r[0] <= 1e-3 && r[1] <= 1e-3 && r[2] <= 1e-3, "Prüfstein 4: Karte gegen Prozessor über " + ein.schritte().size() + " Intervalle, mittlere Abweichung relativ zum mittleren Betrag (" + wer + ")", wert,
                "≤ 1 · 10⁻³ [G]; größte als Auskunft (S3)");
        else A.info("Prüfstein 4 nur mit der Karte; Prozessor gegen Prozessor (" + wer + ")", wert);
    }

    // ------------------------------------------------------------------ Prüfsteine 5 und 6

    /** Median über die Tiden je Zelle. */
    static float[] median(float[][][] st, int feld) {
        int n = st.length, nc = st[0][feld].length;
        float[] m = new float[nc], w = new float[n];
        for (int c = 0; c < nc; c++) {
            for (int t = 0; t < n; t++) w[t] = st[t][feld][c];
            Arrays.sort(w);
            m[c] = n % 2 == 1 ? w[n / 2] : 0.5f * (w[n / 2 - 1] + w[n / 2]);
        }
        return m;
    }

    /** Rangkorrelation nach Spearman (Ränge ohne Mittelung gleicher Werte). */
    static double rang(double[] a, double[] b) {
        return Statistik.korrelation(raenge(a), raenge(b));
    }

    static double[] raenge(double[] a) {
        Integer[] o = new Integer[a.length];
        for (int i = 0; i < o.length; i++) o[i] = i;
        Arrays.sort(o, (x, y) -> Double.compare(a[x], a[y]));
        double[] r = new double[a.length];
        for (int i = 0; i < o.length; ) {
            int j = i;
            while (j + 1 < o.length && a[o[j + 1]] == a[o[i]]) j++;
            for (int k = i; k <= j; k++) r[o[k]] = 0.5 * (i + j);
            i = j + 1;
        }
        return r;
    }

    static double median(double[] a) {
        double[] s = a.clone();
        Arrays.sort(s);
        return s.length == 0 ? Double.NaN : s.length % 2 == 1 ? s[s.length / 2] : 0.5 * (s[s.length / 2 - 1] + s[s.length / 2]);
    }

    /**
     * Modell gegen einen Kennwert von EasyGSH je Pixel des Kennwerts: Mittel des Modells über die Zellen im Pixel, wenn
     * mindestens die Hälfte nass war. Gibt {Rangkorrelation, Median des Verhältnisses Modell / EasyGSH, Zahl der Pixel,
     * Median des Modells, Median von EasyGSH} zurück.
     */
    static double[] gegen(Gitter g, float[] modell, SeegangCheck.Kennwert k) {
        int px = (int) Math.round(k.weite() / g.dx);
        List<double[]> paare = new ArrayList<>();
        // Pixel des Kennwerts, die ganz im Gitter liegen
        int i0 = (int) Math.ceil((g.x0 - k.west()) / k.weite() - 1e-9), i1 = (int) Math.floor((g.x1() - k.west()) / k.weite() + 1e-9);
        int j0 = (int) Math.ceil((k.nord() - g.y1()) / k.weite() - 1e-9), j1 = (int) Math.floor((k.nord() - g.y0) / k.weite() + 1e-9);
        for (int j = Math.max(0, j0); j < Math.min(k.ny(), j1); j++)
            for (int i = Math.max(0, i0); i < Math.min(k.nx(), i1); i++) {
                double e = k.v()[i + k.nx() * j];
                if (!(e > 0) || !(e < 1e3)) continue;
                int ci = (int) Math.round((k.west() + i * k.weite() - g.x0) / g.dx), cj = (int) Math.round((k.nord() - (j + 1) * k.weite() - g.y0) / g.dy);
                double s = 0;
                int n = 0;
                for (int b = 0; b < px; b++) for (int a = 0; a < px; a++) {
                    int x = ci + a, y = cj + b;
                    if (x < 0 || y < 0 || x >= g.nx || y >= g.ny) continue;
                    float w = modell[g.index(x, y)];
                    if (w > 0) { s += w; n++; }
                }
                if (2 * n >= px * px) paare.add(new double[] {s / n, e});
            }
        double[] m = new double[paare.size()], e = new double[paare.size()], v = new double[paare.size()];
        for (int q = 0; q < m.length; q++) { m[q] = paare.get(q)[0]; e[q] = paare.get(q)[1]; v[q] = m[q] / e[q]; }
        return new double[] {m.length < 10 ? Double.NaN : rang(m, e), median(v), m.length, median(m), median(e)};
    }

    /** Prüfsteine 5 und 6; gibt die größte Kornschubspannung bei Flut zurück (für das Bild). */
    static float[] easygsh(Attest A, File gb, Gitter g, float[][][] st) throws Exception {
        File d = SedimentLaufCheck.easygsh != null ? SedimentLaufCheck.easygsh : new File(gb.getAbsoluteFile().getParentFile(), "gezeiten" + File.separator + "easygsh_kennwerte" + File.separator + "2015");
        String[] dateien = {"2015_100m_max_flood_current_velocity_50q.tif", "2015_100m_max_ebb_current_velocity_50q.tif", "2015_100m_max_eff_bedshearstress_flood_current_50q.tif",
                "2015_100m_max_eff_bedshearstress_ebb_current_50q.tif"};
        String[] namen = {"Flutstrom", "Ebbstrom", "Kornschubspannung bei Flut", "Kornschubspannung bei Ebbe"}, einheit = {"m/s", "m/s", "N/m²", "N/m²"};
        float[][] med = new float[4][];
        for (int q = 0; q < 4; q++) med[q] = median(st, q);
        boolean[] gut = {true, true};
        StringBuilder[] text = {new StringBuilder(), new StringBuilder()};
        for (int q = 0; q < 4; q++) {
            File t = new File(d, dateien[q]);
            if (!t.isFile()) {
                gut[q / 2] = false;
                text[q / 2].append(namen[q]).append(": ").append(t).append(" fehlt; ");
                continue;
            }
            double[] r = gegen(g, med[q], SeegangCheck.kennwert(t));
            boolean strom = q < 2;
            boolean ok = strom ? r[0] >= 0.7 && r[1] >= 0.8 && r[1] <= 1.5 : r[0] >= 0.6 && r[1] >= 0.5 && r[1] <= 2;
            gut[q / 2] &= ok;
            text[q / 2].append(f("%s: Rangkorrelation %.2f, Median des Verhältnisses %.2f (Modell %.2f, EasyGSH %.2f %s; %.0f Pixel); ", namen[q], r[0], r[1], r[3], r[4], einheit[q], r[2]));
        }
        A.info("Tiden im Feldvergleich", f("%d (ab dem Beginn des Archivs, je %.2f h); Flut bei steigendem, Ebbe bei fallendem Wasser der Zelle; je Pixel von EasyGSH (100 m) das Mittel der Zellen", st.length,
                SedimentLaufCheck.TIDE / 3600));
        A.pruefe(gut[0], "Prüfstein 5: größter Flut- und Ebbstrom je Tide gegen EasyGSH 2015 (Median des Jahres), ruhiger Tag", text[0].toString(), "Rangkorrelation ≥ 0,7; Verhältnis 0,8–1,5 [G]");
        A.pruefe(gut[1], "Prüfstein 6: Kornschubspannung des Stroms gegen EasyGSH 2015, ruhiger Tag", text[1].toString(), "Rangkorrelation ≥ 0,6; Verhältnis 0,5–2 [G]");
        return med[2];
    }

    // ------------------------------------------------------------------ Prüfstein 7

    /** Prüfstein 7; gibt {C_opt des Modells, Schwebstoff aus Sentinel-2} je Zelle in g/m³ zurück (NaN, wo nicht verglichen wird). */
    static float[][] truebung(Attest A, File gb, Gitter g, Archivquelle q) throws Exception {
        File text = SedimentLaufCheck.sentinel != null ? SedimentLaufCheck.sentinel : new File(gb, "pruef" + File.separator + "sentinel_spm.txt");
        float[] modell = new float[g.zellen()], bild = new float[g.zellen()];
        Arrays.fill(modell, Float.NaN);
        Arrays.fill(bild, Float.NaN);
        if (!text.isFile()) {
            A.pruefe(false, "Prüfstein 7: Trübung gegen Sentinel-2", text + " fehlt – zuerst die Aufbereitung mit --schritte sentinel", "Rangkorrelation ≥ 0,4; Verhältnis ⅓–3");
            return new float[][] {modell, bild};
        }
        Rasterfeld spm = Rasterfeld.lies(text);
        Instant zeit = Instant.parse(spm.kopf.get("zeit"));
        // Zeiten des Archivs mit derselben Phase der M2 wie die Szene [G: die Phase der M2 allein]
        double[] zs = q.sedimentZeiten();
        double dt = (zeit.toEpochMilli() - q.start.toEpochMilli()) / 1000.0;
        List<Double> zeiten = new ArrayList<>();
        for (double t = dt - Math.floor((dt - zs[0]) / M2) * M2; t <= zs[zs.length - 1]; t += M2) if (t >= zs[0]) zeiten.add(t);
        if (zeiten.isEmpty()) throw new IllegalStateException("Prüfstein 7: keine Zeit im Archiv mit der Tidephase der Szene");
        int k3 = SeegangCheck.index(q.gitter(), "G3");
        float[] c = new float[2 * g.zellen()], eta = new float[g.zellen()], h = new float[g.zellen()], age = new float[g.zellen()];
        double[] cs = new double[g.zellen()], hm = new double[g.zellen()];
        Arrays.fill(hm, Double.MAX_VALUE);
        List<String> wann = new ArrayList<>();
        for (double t : zeiten) {
            int i = naechste(zs, t);
            q.sediment(zs[i], c);
            q.stand(zs[i], k3, eta, h, age);
            for (int z = 0; z < g.zellen(); z++) {
                cs[z] += 1000 * (c[2 * z + 1] + SAND_OPTIK * c[2 * z]) / zeiten.size();
                hm[z] = Math.min(hm[z], h[z]);
            }
            wann.add(BildCheck.zeit(q.start, zs[i]));
        }
        List<double[]> paare = new ArrayList<>(), tief = new ArrayList<>();
        int wasser = 0;
        for (int z = 0; z < g.zellen(); z++) {
            double wert = spm.bei(g.x(z % g.nx), g.y(z / g.nx));
            if (!(wert > 0)) continue;                                           // kein Wasser in der Szene
            wasser++;
            if (!(hm[z] >= 0.3)) continue;                                       // im Modell nass und nicht so flach, dass der Boden durchscheint [G]
            modell[z] = (float) cs[z];
            bild[z] = (float) wert;
            if (cs[z] > 0) paare.add(new double[] {cs[z], wert});
            if (cs[z] > 0 && hm[z] >= TIEF) tief.add(new double[] {cs[z], wert});
        }
        double[] m = new double[paare.size()], s = new double[paare.size()], v = new double[paare.size()];
        for (int i = 0; i < m.length; i++) { m[i] = paare.get(i)[0]; s[i] = paare.get(i)[1]; v[i] = m[i] / s[i]; }
        double r = m.length < 100 ? Double.NaN : rang(m, s), vm = median(v);
        A.info("Sentinel-2", f("%s, Aufnahme %s; Schwebstoff nach Nechad u. a. (2010) aus B04 (Aufbereitung, Schritt sentinel: Reflexion = Wert · %s + %s); Wasser mit Wert in %d Zellen des Gitters",
                spm.kopf.get("szene"), zeit, spm.kopf.get("massstab"), spm.kopf.getOrDefault("versatz", "0").split("\\s+")[0], wasser));
        A.info("Modell zur Tidephase der Szene (Phase der M2 [G])", String.join(", ", wann) + f("; C_opt = Schlick + %.2f · Sand; verglichen, wo das Modell mindestens 0,3 m Wasser hat", SAND_OPTIK));
        A.pruefe(r >= 0.4 && vm >= 1.0 / 3 && vm <= 3, "Prüfstein 7: Trübung des Modells gegen Schwebstoff aus Sentinel-2 (anderer Tag, gleiche Tidephase)",
                f("Rangkorrelation %.2f, Median des Verhältnisses %.2f (Modell %.1f, Sentinel-2 %.1f g/m³; %d Zellen)", r, vm, median(m), median(s), m.length), "Rangkorrelation ≥ 0,4; Verhältnis ⅓–3 [G]");
        // F86 A (P9c): über flachem Watt sieht der Satellit im Roten den Boden mit – nur Zellen mit mindestens TIEF m Wasser
        double[] mt = new double[tief.size()], st = new double[tief.size()], vt = new double[tief.size()];
        for (int i = 0; i < mt.length; i++) { mt[i] = tief.get(i)[0]; st[i] = tief.get(i)[1]; vt[i] = mt[i] / st[i]; }
        double rt = mt.length < 100 ? Double.NaN : rang(mt, st), vmt = mt.length > 0 ? median(vt) : Double.NaN;
        String wertT = mt.length > 0 ? f("Rangkorrelation %.2f, Median des Verhältnisses %.2f (Modell %.1f, Sentinel-2 %.1f g/m³; %d Zellen)", rt, vmt, median(mt), median(st), mt.length) : "keine Zellen";
        if (SedimentLaufCheck.attest != null)
            A.pruefe(rt >= 0.4 && vmt >= 1.0 / 3 && vmt <= 3, f("P9c Prüfstein 16: Trübung gegen Sentinel-2, nur Zellen mit mindestens %.0f m Wasser (F86 A)", TIEF), wertT, "Rangkorrelation ≥ 0,4; Verhältnis ⅓–3 [G]");
        else A.info(f("Trübung gegen Sentinel-2, nur Zellen mit mindestens %.0f m Wasser (F86 A, P9c)", TIEF), wertT);
        return new float[][] {modell, bild};
    }

    /** Wassertiefe (m), ab der der Vergleich mit Sentinel-2 als frei vom Boden gilt (F86 A) [G]. */
    static final double TIEF = 2.0;

    static int naechste(double[] z, double t) {
        int b = 0;
        for (int i = 1; i < z.length; i++) if (Math.abs(z[i] - t) < Math.abs(z[b] - t)) b = i;
        return b;
    }

    // ------------------------------------------------------------------ Prüfstein 8

    /** Prüfstein 8; gibt die Rippelhöhe bei Niedrigwasser zurück (m). */
    static float[] rippel(Attest A, File gb, Gitter g, Archivquelle q) throws Exception {
        Sediment s = new Sediment(1, 1, g.dx, g.dy);
        double[] d50 = new double[g.zellen()];
        Arrays.fill(d50, 2e-4);
        if (Kornfeld.vorhanden(gb)) {
            double[] d = Kornfeld.lies(gb).auf(g, Kornfeld.D50);
            for (int c = 0; c < d50.length; c++) d50[c] = Math.max(6.3e-5, Math.min(2e-3, d[c]));
        }
        float[] gross = new float[g.zellen()];
        for (int c = 0; c < gross.length; c++) gross[c] = (float) Sediment.rippel(d50[c], Sediment.dStern(d50[c], s.rhoS / s.rho, s.nu), 0)[0];
        double[] zb = q.bodenZeiten(), zw = q.zeiten();
        int k3 = SeegangCheck.index(q.gitter(), "G3");
        float[] r = new float[3 * g.zellen()], eta = new float[g.zellen()], h = new float[g.zellen()], age = new float[g.zellen()];
        long ueber = 0;
        double weit = 0;
        int trockenMax = -1, nassMax = -1, iNw = 0, iHw = 0;
        for (int i = 0; i < zb.length; i++) {
            q.boden(zb[i], r, null, null);
            for (int c = 0; c < g.zellen(); c++) {
                double zu = r[3 * c] - gross[c] * 1.002 - 1e-5;                  // float16 im Archiv
                if (zu > 0) { ueber++; weit = Math.max(weit, zu); }
            }
            if (Arrays.binarySearch(zw, zb[i]) < 0) continue;
            q.stand(zb[i], k3, eta, h, age);
            int trocken = 0;
            for (float x : h) if (x < 0.05f) trocken++;
            if (trocken > trockenMax) { trockenMax = trocken; iNw = i; }
            if (g.zellen() - trocken > nassMax) { nassMax = g.zellen() - trocken; iHw = i; }
        }
        A.pruefe(ueber == 0, "Prüfstein 8: Rippelhöhe im Archiv höchstens der Größtwert der Formel für das Korn der Zelle", f("%d Werte darüber (größter Überschuss %.1e m) in %d Ständen", ueber, weit, zb.length), "0");
        // Watt: bei Niedrigwasser trocken, bei Hochwasser nass
        float[] hNw = new float[g.zellen()], hHw = new float[g.zellen()], hoehe = new float[g.zellen()];
        q.stand(zb[iNw], k3, eta, hNw, age);
        q.stand(zb[iHw], k3, eta, hHw, age);
        q.boden(zb[iNw], r, null, null);
        int watt = 0, mit = 0;
        double sum = 0;
        for (int c = 0; c < g.zellen(); c++) {
            hoehe[c] = r[3 * c];
            if (!(hNw[c] < 0.05f && hHw[c] >= 0.05f)) continue;
            watt++;
            if (r[3 * c] > 0.005f) { mit++; sum += r[3 * c]; }
        }
        A.info("Rippel im Watt bei Niedrigwasser (" + BildCheck.zeit(q.start, zb[iNw]) + ")", f("%d Wattzellen (trocken bei Niedrigwasser, nass bei Hochwasser um %s); davon %.1f %% mit Rippeln über 5 mm, im Mittel %.1f mm hoch", watt,
                BildCheck.zeit(q.start, zb[iHw]), watt == 0 ? 0 : 100.0 * mit / watt, mit == 0 ? 0 : 1000 * sum / mit));
        return hoehe;
    }

    // ------------------------------------------------------------------ Status-Bild

    /** Vier Karten: C_opt des Modells, Schwebstoff aus Sentinel-2 (g/m³, log), Rippelhöhe bei Niedrigwasser (mm), Kornschubspannung bei Flut (N/m²). */
    static void bild(Attest A, Gitter g, float[][] k) throws Exception {
        int sk = Math.max(1, 700 / g.nx), w = g.nx * sk, h = g.ny * sk, rand = 22;
        BufferedImage im = new BufferedImage(2 * w + 6, 2 * (h + rand) + 6, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        String[] titel = {"Modell: Trübung C_opt zur Tidephase der Szene, 1–100 g/m³ (log)", "Sentinel-2, 01.05.2026: Schwebstoff nach Nechad, 1–100 g/m³ (log)",
                "Modell: Rippelhöhe bei Niedrigwasser, 0–20 mm", "Modell: größte Kornschubspannung des Stroms bei Flut, 0–1 N/m²"};
        for (int n = 0; n < 4; n++) {
            int x0 = (n % 2) * (w + 6), y0 = (n / 2) * (h + rand + 6);
            float[] f = k[n];
            for (int j = 0; j < g.ny; j++)
                for (int i = 0; i < g.nx; i++) {
                    float v = f == null ? Float.NaN : f[g.index(i, j)];
                    double t = n < 2 ? Math.log10(Math.max(1e-3, v)) / 2 : n == 2 ? v / 0.02 : v;
                    int rgb = Float.isNaN(v) || (n >= 2 && v <= 0) ? 0x202020 : farbe(Math.max(0, Math.min(1, t)));
                    for (int b = 0; b < sk; b++) for (int a = 0; a < sk; a++) im.setRGB(x0 + i * sk + a, y0 + (g.ny - 1 - j) * sk + b, rgb);
                }
            g2.setColor(Color.WHITE);
            g2.drawString(titel[n], x0 + 6, y0 + h + 15);
        }
        g2.dispose();
        File d = Path.of("docs", "status", (SedimentLaufCheck.attest != null ? SedimentLaufCheck.attest : "P9b") + "_Sediment.png").toFile();
        d.getParentFile().mkdirs();
        ImageIO.write(im, "png", d);
        A.info("Status-Bild", d.toString());
    }

    /** Farbe von dunkelblau über grün und gelb nach rot. */
    static int farbe(double t) {
        double[][] st = {{0.05, 0.10, 0.35}, {0.10, 0.50, 0.60}, {0.45, 0.75, 0.35}, {0.95, 0.85, 0.30}, {0.80, 0.20, 0.15}};
        double x = t * (st.length - 1);
        int i = Math.min(st.length - 2, (int) x);
        double a = x - i;
        int r = (int) Math.round(255 * (st[i][0] + a * (st[i + 1][0] - st[i][0]))), gr = (int) Math.round(255 * (st[i][1] + a * (st[i + 1][1] - st[i][1]))),
                b = (int) Math.round(255 * (st[i][2] + a * (st[i + 1][2] - st[i][2])));
        return r << 16 | gr << 8 | b;
    }
}
