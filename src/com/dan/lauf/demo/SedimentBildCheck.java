package com.dan.lauf.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Bodennetz;
import com.dan.core.szene.Wasseroptik;
import com.dan.lauf.Archivquelle;
import com.dan.render.water.Wasserbild;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static com.dan.core.pruef.Attest.f;

/**
 * Teil 3 von {@link SedimentLaufCheck} (P9b, Werkbuch HalligHooge Teil A, 12.10): das Sediment im Bild, aus dem Archiv
 * des ruhigen Tags. Prüfsteine 10 (ein Netz mit überall 30 g/m³ gibt das bisherige Bild), 11 (die Tafeln der Stufen
 * gegen den bisherigen Weg: eine Stufe genau, zwischen zwei Stufen als Auskunft), 12 (Bilder/s). Status-Bild
 * {@code docs/status/P9b_Bild.png} für die Sichtprüfung (Prüfstein 15): Fahnen im Ebbstrom von oben, Rippel im Watt bei
 * Niedrigwasser, die Westspitze mit und ohne Trübung am Ort.
 */
final class SedimentBildCheck {
    private SedimentBildCheck() { }

    static int breite = 1920, hoehe = 1080, bilder = 60;
    /** Feste Blicke statt der aus dem Archiv gewählten: Fahnen {Ost, Nord, Höhe m NHN, Richtung, Neigung}, Rippel {Ost, Nord, Richtung}; Zeit der Rippel. */
    static double[] fahneBlick, rippelBlick;
    static Instant rippelZeit;

    static void teil3(Attest A, Geraet g, Konto konto, File gb, File ar) throws Exception {
        A.teil("3 Bild");
        Gebiet ge = Gebiet.lies(gb);
        try (Archivquelle q = new Archivquelle(ar.toPath()); BildCheck.Szene sz = new BildCheck.Szene(g, konto, gb, ge, q, breite, hoehe)) {
            Gitter sg = q.sedimentGitter();
            if (sg == null) throw new IllegalStateException(ar + ": kein Sediment im Archiv");
            Instant t0 = q.start;
            int k3 = SeegangCheck.index(q.gitter(), "G3"), n = sg.zellen();
            double[] zs = q.sedimentZeiten(), zb = q.bodenZeiten();
            float[] c = new float[2 * n], eta = new float[n], h = new float[n], age = new float[n], r = new float[3 * n];
            // Zeit der Fahnen: der Stand bei Tag (09–15 UTC) mit der größten mittleren Trübung im Wasser; Ort: die trübste Zelle mit ≥ 1 m Wasser
            double tFahne = zs[zs.length / 2], beste = -1;
            for (double ts : zs) {
                int std = t0.plusMillis(Math.round(ts * 1000)).atZone(ZoneOffset.UTC).getHour();
                if (std < 9 || std >= 15) continue;
                q.sediment(ts, c);
                double s = 0;
                int m = 0;
                for (int z = 0; z < n; z++) {
                    double co = c[2 * z + 1] + Bodennetz.SAND * c[2 * z];
                    if (co > 0) { s += co; m++; }
                }
                if (m > 0 && s / m > beste) { beste = s / m; tFahne = ts; }
            }
            q.sediment(tFahne, c);
            q.stand(tFahne, k3, eta, h, age);
            int zf = -1;
            double cf = -1;
            int rand = 40;
            for (int j = rand; j < sg.ny - rand; j++)
                for (int i = rand; i < sg.nx - rand; i++) {
                    int z = sg.index(i, j);
                    double co = c[2 * z + 1] + Bodennetz.SAND * c[2 * z];
                    if (h[z] >= 1 && co > cf) { cf = co; zf = z; }
                }
            // Zeit und Ort der Rippel: der Stand des Bodens bei Tag mit den meisten trockenen Zellen; dort die trockene Zelle mit den höchsten Rippeln
            double tRippel = zb[zb.length / 2];
            int trockenMax = -1;
            for (double ts : zb) {
                int std = t0.plusMillis(Math.round(ts * 1000)).atZone(ZoneOffset.UTC).getHour();
                if (std < 8 || std >= 16 || Arrays.binarySearch(q.zeiten(), ts) < 0) continue;
                q.stand(ts, k3, eta, h, age);
                int tr = 0;
                for (float x : h) if (x < 0.05f) tr++;
                if (tr > trockenMax) { trockenMax = tr; tRippel = ts; }
            }
            q.stand(tRippel, k3, eta, h, age);
            q.boden(tRippel, r, null, null);
            int zr = -1;
            float hr = 0;
            for (int j = rand; j < sg.ny - rand; j++)
                for (int i = rand; i < sg.nx - rand; i++) {
                    int z = sg.index(i, j);
                    // im Watt: eben trocken gefallen (nicht das Land), mit Rippeln
                    if (h[z] < 0.05f && age[z] < 0 && age[z] > -6 * 3600 && r[3 * z] > hr) { hr = r[3 * z]; zr = z; }
                }
            var gel = sz.gelaende.gelaende;
            double westH = gel.hoehe(BildCheck.WEST_OST, BildCheck.WEST_NORD, 1) + 1.7;
            Blick west = new Blick(BildCheck.WEST_OST, BildCheck.WEST_NORD, westH, BildCheck.WEST_RICHTUNG, -2, 55).groesse(breite, hoehe);
            double fo = zf >= 0 ? sg.x(zf % sg.nx) : 470_400, fn = zf >= 0 ? sg.y(zf / sg.nx) : 6_044_300;
            Blick fahne = fahneBlick != null ? new Blick(fahneBlick[0], fahneBlick[1], fahneBlick[2], fahneBlick[3], fahneBlick[4], 60).groesse(breite, hoehe)
                    : new Blick(fo, fn - 700, 400, 0, -32, 60).groesse(breite, hoehe);
            double ro = zr >= 0 ? sg.x(zr % sg.nx) : 469_000, rn = zr >= 0 ? sg.y(zr / sg.nx) : 6_046_000, rr = 250;
            if (rippelBlick != null) { ro = rippelBlick[0]; rn = rippelBlick[1]; rr = rippelBlick[2]; }
            if (rippelZeit != null) tRippel = (rippelZeit.toEpochMilli() - t0.toEpochMilli()) / 1000.0;
            Blick rippel = new Blick(ro, rn, gel.hoehe(ro, rn, 1) + 1.2, rr, -22, 50).groesse(breite, hoehe);
            if (fahneBlick != null) A.info("Blick auf die Fahnen (--fahne)", f("O %.0f, N %.0f, %.0f m NHN, Richtung %.0f°, Neigung %.0f°", fahneBlick[0], fahneBlick[1], fahneBlick[2], fahneBlick[3], fahneBlick[4]));
            if (rippelBlick != null || rippelZeit != null) A.info("Blick auf die Rippel (--rippel, --rippelzeit)", f("O %.0f, N %.0f, Richtung %.0f°, %s", ro, rn, rr, BildCheck.zeit(t0, tRippel)));
            A.info("Fahnen", f("%s: mittlere Trübung im Wasser %.1f g/m³; trübste Zelle mit ≥ 1 m Wasser bei O %.0f, N %.0f (%.0f g/m³); Blick aus 400 m Höhe, 700 m südlich, nach Norden", BildCheck.zeit(t0, tFahne),
                    1000 * beste, fo, fn, 1000 * cf));
            A.info("Rippel", f("%s (Niedrigwasser bei Tag: %d trockene Zellen): höchste Rippel im trockengefallenen Watt bei O %.0f, N %.0f (%.1f mm hoch, %.0f mm lang); Blick aus 1,2 m über dem Boden nach Westsüdwest",
                    BildCheck.zeit(t0, tRippel), trockenMax, ro, rn, 1000 * hr, zr >= 0 ? 1000 * r[3 * zr + 1] : 0));
            Wasserbild w = sz.wasser;
            double szene = w.optik.schwebstoff;
            // Prüfstein 10: Netz mit überall 30 g/m³ gegen den bisherigen Weg
            w.optik.schwebstoff = new Wasseroptik().schwebstoff;
            float[] alt = bild(sz, west, t0, tFahne, false, Double.NaN);
            float[] gleich = bild(sz, west, t0, tFahne, true, w.optik.schwebstoff);
            int[] d10 = unterschied(alt, gleich);
            A.pruefe(d10[0] <= 1, f("Prüfstein 10: Netz mit überall %.0f g/m³ gegen das bisherige Bild (Westspitze)", w.optik.schwebstoff), d10[0] + " von 255", "≤ 1");
            // Prüfstein 11: eine Stufe der Tafeln gegen den bisherigen Weg mit derselben Konzentration; zwischen zwei Stufen als Auskunft
            double c3 = Wasserbild.stufe(3), cz = Math.sqrt(Wasserbild.stufe(2) * Wasserbild.stufe(3));
            w.optik.schwebstoff = c3;
            int[] d11 = unterschied(bild(sz, west, t0, tFahne, false, Double.NaN), bild(sz, west, t0, tFahne, true, c3));
            w.optik.schwebstoff = cz;
            int[] d11z = unterschied(bild(sz, west, t0, tFahne, false, Double.NaN), bild(sz, west, t0, tFahne, true, cz));
            w.optik.schwebstoff = szene;
            A.pruefe(d11[0] <= 1, f("Prüfstein 11: Trübung am Ort mit der Stufe %.1f g/m³ gegen den bisherigen Weg mit derselben Konzentration (Westspitze)", c3), d11[0] + " von 255", "≤ 1");
            A.info(f("Zwischen zwei Stufen (%.1f g/m³): Fehler der Mischung gegen den bisherigen Weg", cz), f("größter %d von 255, im Mittel %.2f", d11z[0], d11z[1] / 100.0));
            // Ansichten für die Sichtprüfung
            w.optik.schwebstoff = 20;
            float[] bFahne = bild(sz, fahne, t0, tFahne, true, Double.NaN), bFahneOhne = bild(sz, fahne, t0, tFahne, false, Double.NaN);
            float[] bRippel = bild(sz, rippel, t0, tRippel, true, Double.NaN), bWest = bild(sz, west, t0, tFahne, true, Double.NaN);
            int[] dF = unterschied(bFahne, bFahneOhne);
            A.info("Fahnen im Bild: mit Trübung am Ort gegen überall 20 g/m³", f("größter Unterschied %d von 255, im Mittel %.2f", dF[0], dF[1] / 100.0));
            // Prüfstein 12: Bilder/s aus dem Archiv mit Sediment
            w.truebung = true;
            for (int k = 0; k < 5; k++) sz.zeichne(west, t0, tFahne + k, null);
            long n0 = System.nanoTime();
            for (int k = 0; k < bilder; k++) sz.zeichne(west, t0, tFahne + 5 + k * 2.0, null);
            double fps = bilder / ((System.nanoTime() - n0) * 1e-9);
            for (int k = 0; k < 5; k++) sz.zeichne(fahne, t0, tFahne + k, null);
            n0 = System.nanoTime();
            for (int k = 0; k < bilder; k++) sz.zeichne(fahne, t0, tFahne + 5 + k * 2.0, null);
            double fpsF = bilder / ((System.nanoTime() - n0) * 1e-9);
            w.optik.schwebstoff = szene;
            A.pruefe(fps >= 30, f("Prüfstein 12: Bilder/s aus dem Archiv mit Sediment, %d × %d, Westspitze (je Bild gewartet)", breite, hoehe), f("%.1f (Fahnen von oben: %.1f)", fps, fpsF), "≥ 30");
            statusbild(A, new float[][] {bFahne, bFahneOhne, bRippel, bWest},
                    new String[] {"Fahnen von oben, Trübung am Ort · " + BildCheck.zeit(t0, tFahne), "dieselbe Sicht, überall 20 g/m³ · " + BildCheck.zeit(t0, tFahne),
                            "Rippel im Watt bei Niedrigwasser · " + BildCheck.zeit(t0, tRippel), "Westspitze, Trübung am Ort · " + BildCheck.zeit(t0, tFahne)});
        }
    }

    /**
     * Ein Bild (8 Bit, RGBA als float 0 … 1) zur Zeit t; {@code amOrt}: Trübung aus dem Netz; {@code ueberall}: statt der
     * Werte des Archivs überall diese Konzentration im Netz (g/m³; NaN: die Werte des Archivs).
     */
    static float[] bild(BildCheck.Szene sz, Blick b, Instant t0, double t, boolean amOrt, double ueberall) {
        sz.wasser.truebung = amOrt;
        sz.bild.zu(t, b.ost, b.nord);
        Bodennetz.Spur s = sz.bild.boden().truebung;
        float[][] alt = null;
        if (!Double.isNaN(ueberall))
            synchronized (s) {
                alt = new float[][] {s.werte[0].clone(), s.werte[1].clone()};
                Arrays.fill(s.werte[0], (float) ueberall);
                Arrays.fill(s.werte[1], (float) ueberall);
                s.neu = 3;
            }
        for (int k = 0; k < 12; k++) {
            sz.rahmen.belichtung.sofort();
            sz.zeichne(b, t0, t, null);
        }
        float[] p = sz.aus.lies();
        if (alt != null)
            synchronized (s) {
                System.arraycopy(alt[0], 0, s.werte[0], 0, alt[0].length);
                System.arraycopy(alt[1], 0, s.werte[1], 0, alt[1].length);
                s.neu = 3;
            }
        sz.wasser.truebung = true;
        return p;
    }

    /** Größter Unterschied zweier Bilder in 8 Bit und das Hundertfache des mittleren. */
    static int[] unterschied(float[] a, float[] b) {
        int max = 0;
        long sum = 0, n = 0;
        for (int i = 0; i < a.length; i++) {
            if (i % 4 == 3) continue;
            int d = Math.abs(Math.round(a[i] * 255) - Math.round(b[i] * 255));
            max = Math.max(max, d);
            sum += d;
            n++;
        }
        return new int[] {max, (int) Math.round(100.0 * sum / Math.max(1, n))};
    }

    static void statusbild(Attest A, float[][] bilder, String[] namen) throws Exception {
        int w = breite / 2, h = hoehe / 2;
        BufferedImage alle = new BufferedImage(2 * w, 2 * h, BufferedImage.TYPE_INT_RGB);
        for (int n = 0; n < bilder.length; n++) {
            float[] p = bilder[n];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int i = 4 * ((2 * y) * breite + 2 * x);
                    int rr = Math.round(p[i] * 255), gg = Math.round(p[i + 1] * 255), bb = Math.round(p[i + 2] * 255);
                    alle.setRGB(x + (n % 2) * w, y + (n / 2) * h, (rr << 16) | (gg << 8) | bb);
                }
            Graphics2D g2 = alle.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, h / 36)));
            int x0 = (n % 2) * w + 8, y0 = (n / 2) * h + h - 10;
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRect(x0 - 4, y0 - g2.getFontMetrics().getAscent() - 2, g2.getFontMetrics().stringWidth(namen[n]) + 8, g2.getFontMetrics().getHeight() + 2);
            g2.setColor(Color.WHITE);
            g2.drawString(namen[n], x0, y0);
            g2.dispose();
        }
        File png = new File("docs" + File.separator + "status" + File.separator + (SedimentLaufCheck.attest != null ? SedimentLaufCheck.attest : "P9b") + "_Bild.png");
        png.getParentFile().mkdirs();
        ImageIO.write(alle, "png", png);
        A.info("Prüfstein 15: Status-Bild (Sichtprüfung)", png.getPath());
    }
}
