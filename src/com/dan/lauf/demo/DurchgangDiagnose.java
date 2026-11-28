package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Lichtquellen;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flotte;
import com.dan.lauf.Szenen;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.dan.core.pruef.Attest.f;

/**
 * S9d-2 (Seegang Werkbuch 110, SF188 A): messen, nicht bauen. Zwei offene Punkte aus L7l bekommen alle Durchgänge je Bild,
 * nicht nur die sechs teuersten:
 * <ol>
 * <li><b>Bord Kutter am Kurs:</b> nach dem Vorlauf wie in der Abnahme (fester Messbeginn) {@link #PROBEN} Proben im Abstand
 * von {@link #ABSTAND_S} s Szenenzeit (zusammen 2 min). Je Probe in Hoch {@link #LAUFEND} Bilder laufend (Flotte nebenher,
 * wie die Anwendung) und {@link #STEHEND} Bilder stehend am Bordblick dieses Augenblicks (dieselbe Lage, Zeit fest); dazu
 * Kurs, Krängung und Trimm des Kutters, Höhe des Auges, Winkel der Sonne zur Blickachse und ob sie im Bild steht, und die
 * Anteile im Bild aus der Tiefe des stehenden Bilds: Himmel, Wasser nah (bis {@link #NAH_M} m), Wasser fern, Land und Bauten,
 * nah am Auge (bis {@link #AUGE_M} m, meist das eigene Schiff). Ein kleines Bild je Probe.</li>
 * <li><b>Xaver Hanswarft laufend:</b> nach demselben Vorlauf {@link #RUNDEN} Runden im Wechsel: A laufend mit Flotte, C laufend
 * ohne Auftrag an die Flotte, S stehend (Blick und Zeit fest), je {@link #BLOCK} Bilder (S {@link #STEHEND}). Je Art alle
 * Durchgänge; die Unterschiede A − S und A − C je Durchgang.</li>
 * </ol>
 * Ausgaben: {@code docs/status/S9d2_Durchgaenge.md} (Attest), {@code S9d2_Durchgaenge.csv} (je Probe und Art alle
 * Durchgänge, ms je Bild), {@code S9d2_bord_<n>.png} (640 × 360).
 * <pre>  DurchgangDiagnose --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--nur bord|xaver]</pre>
 */
public final class DurchgangDiagnose {
    static final Attest A = new Attest("Seegang: Durchgänge je Bild, Bord Kutter am Kurs und Xaver laufend (S9d-2)", "S9d");
    static final int PROBEN = 12, LAUFEND = 30, STEHEND = 10, RUNDEN = 3, BLOCK = 30;
    static final double ABSTAND_S = 10, NAH_M = 100, AUGE_M = 15, WASSER_UEBER_M = 3;
    /** Raster für die Anteile aus der Tiefe (jeder n-te Bildpunkt in beiden Richtungen). */
    static final int RASTER = 8;
    static final String[] BORD = {"seegang", "bord_kutter", "2026-10-25T13:30:00Z"};
    static final String[] XAVER = {"xaver", "brandung_hanswarft", "2013-12-05T13:00:00Z"};

    /** Zeilen der CSV: Art, Probe, Werte je Durchgang. */
    static final List<Map.Entry<String, Map<String, Double>>> ZEILEN = new ArrayList<>();
    static final Map<String, String> KOPF = new LinkedHashMap<>();

    public static void main(String[] args) {
        File szenen = null;
        String nur = null;
        int breite = 1920, hoehe = 1080;
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                case "--nur" -> nur = args[++i];
                default -> { }
            }
        System.out.println("DurchgangDiagnose – S9d-2 (Seegang Werkbuch 110, SF188 A)");
        long t0 = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            alles(Szenen.lies(szenen), breite, hoehe, nur);
        } catch (Throwable e) {
            A.fehler(e);
        }
        try {
            csv(Path.of("docs", "status", "S9d2_Durchgaenge.csv"));
        } catch (IOException e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        Path pa = A.schreibe(Path.of("docs", "status", "S9d2_Durchgaenge.md"), "S9d-2 – Durchgänge je Bild: Bord Kutter am Kurs, Xaver laufend");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void alles(Szenen sz, int breite, int hoehe, String nur) throws Exception {
        Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
        A.info("Gerät", b.geraet().name + f(", %d × %d", breite, hoehe));
        try {
            if (nur == null || nur.equals("bord")) bord(b, sz, breite, hoehe);
            if (nur == null || nur.equals("xaver")) xaver(b, sz, breite, hoehe);
        } finally {
            b.close();
        }
    }

    // ------------------------------------------------------------------ 1 Bord Kutter

    static void bord(Buehne b, Szenen sz, int breite, int hoehe) throws Exception {
        A.teil("1 Bord Kutter am Kurs");
        SchwimmerCheck.pegel(b, sz);
        b.oeffne(SchaumCheck.szenario(sz, BORD[0]));
        b.wasserbild().seegang = true;
        SeegangAbnahme.Fall c = SeegangAbnahme.einrichten(b, sz, BORD, breite, hoehe, A);
        Flotte fl = b.flotte();
        Flotte.Schwimmer kutter = null;
        if (fl != null) for (Flotte.Schwimmer s : fl.schwimmer) if (s.angabe.name.equals(c.an.bord)) kutter = s;
        double tStart = c.zt[0];
        List<double[]> werte = new ArrayList<>();
        for (int p = 0; p < PROBEN; p++) {
            double tProbe = tStart + p * ABSTAND_S;
            while (c.zt[0] < tProbe - 1e-9) c.bild(b);                      // laufen bis zur Probe (nicht gemessen)
            // laufend
            Map<String, Double> lauf = new TreeMap<>();
            List<Double> lk = new ArrayList<>();
            int bau = 0;
            for (int i = 0; i < LAUFEND; i++) {
                Buehne.Bildzeiten z = c.bild(b);
                lk.add(z.karte()[0]);
                if (c.w.baut()) bau++;
                for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) lauf.merge(e.getKey(), e.getValue() / LAUFEND, Double::sum);
            }
            // Lage des Kutters und Bordblick dieses Augenblicks
            if (fl != null) fl.warte();
            double kurs = Double.NaN, kr = Double.NaN, tr = Double.NaN;
            if (kutter != null) { kurs = kutter.koerper.kurs(); kr = kutter.koerper.kraengung(); tr = kutter.koerper.trimm(); }
            Blick fest = c.blickJetzt();
            double tFest = c.zt[0];
            Buehne.Bildzeiten zs = b.bildzeiten(fest, tFest, STEHEND, true);
            double steh = zs.karte()[STEHEND / 2];
            double[] an = anteile(b, fest);
            double[] so = sonne(b, fest, tFest);
            javax.imageio.ImageIO.write(klein(SeegangBilder.bild(b.letztesBild(), breite, hoehe), 640, 360), "png", Path.of("docs", "status", f("S9d2_bord_%02d.png", p + 1)).toFile());
            double laufMed = SchaumCheck.median(lk);
            String name = f("Probe %d (+%.0f s)", p + 1, p * ABSTAND_S);
            ZEILEN.add(Map.entry("bord laufend;" + (p + 1), lauf));
            ZEILEN.add(Map.entry("bord stehend;" + (p + 1), new TreeMap<>(zs.abschnitte())));
            KOPF.put("bord;" + (p + 1), String.format(Locale.ROOT, "%.3f;%.3f;%.1f;%.2f;%.2f;%.2f;%.1f;%d;%.4f;%.4f;%.4f;%.4f;%.4f;%d", laufMed, steh, kurs, kr, tr, fest.hoehe, so[0], (int) so[1],
                    an[0], an[1], an[2], an[3], an[4], bau));
            A.info(name, f("laufend %.2f ms, stehend am Bordblick %.2f ms; Kurs %.0f°, Krängung %+.1f°, Trimm %+.1f°, Auge %.1f m; Sonne %.0f° von der Blickachse%s; "
                            + "Himmel %.0f %%, Wasser bis %.0f m %.0f %%, Wasser weiter %.0f %%, Land und Bauten %.0f %%, nah am Auge %.0f %%; Bau der Fläche in %d von %d",
                    laufMed, steh, kurs, kr, tr, fest.hoehe, so[0], so[1] > 0 ? " (im Bild)" : "", 100 * an[0], NAH_M, 100 * an[1], 100 * an[2], 100 * an[3], 100 * an[4], bau, LAUFEND));
            A.info(name + ", teuerste laufend (ms)", oben(lauf, 10));
            A.info(name + ", laufend − stehend (ms, größte)", BildzeitDiagnose.unterschiedKarten(lauf, zs.abschnitte(), 6));
            werte.add(new double[] {laufMed, steh, so[0], an[1], an[3]});
        }
        if (c.k != null) b.bordKamera(null);
        // Zusammenhang: schnellste gegen langsamste Probe, je Durchgang
        int iMin = 0, iMax = 0;
        for (int p = 1; p < werte.size(); p++) {
            if (werte.get(p)[0] < werte.get(iMin)[0]) iMin = p;
            if (werte.get(p)[0] > werte.get(iMax)[0]) iMax = p;
        }
        A.teil("1 Zusammenfassung");
        A.info("Bord Kutter laufend über die Proben", f("%.2f bis %.2f ms (Proben %d und %d); stehend am Bordblick %.2f bis %.2f ms", werte.get(iMin)[0], werte.get(iMax)[0], iMin + 1, iMax + 1,
                min(werte, 1), max(werte, 1)));
        A.info("langsamste gegen schnellste Probe, laufend, je Durchgang (ms, größte)", BildzeitDiagnose.unterschiedKarten(ZEILEN.get(2 * iMax).getValue(), ZEILEN.get(2 * iMin).getValue(), 12));
        A.info("langsamste gegen schnellste Probe, stehend am Bordblick, je Durchgang (ms, größte)",
                BildzeitDiagnose.unterschiedKarten(ZEILEN.get(2 * iMax + 1).getValue(), ZEILEN.get(2 * iMin + 1).getValue(), 12));
        int unter30 = 0;
        for (double[] w : werte) if (w[0] <= 1000.0 / 30) unter30++;
        A.info("Proben mit 30 Bilder/s in Hoch (laufend)", f("%d von %d", unter30, werte.size()));
    }

    /** Anteile im Bild aus der Tiefe des letzten Bilds: Himmel, Wasser nah, Wasser fern, Land und Bauten, nah am Auge. */
    static double[] anteile(Buehne b, Blick bl) {
        float[] d = b.tiefe();
        int w = bl.breite, h = bl.hoehe_px;
        if (d == null || d.length < w * h) return new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        double[] f = bl.achse();
        int n = 0;
        double[] z = new double[5];
        for (int y = RASTER / 2; y < h; y += RASTER)
            for (int x = RASTER / 2; x < w; x += RASTER) {
                n++;
                float t = d[y * w + x];
                if (!(t > 1e-12f)) { z[0]++; continue; }
                double[] v = bl.strahl(x + 0.5, y + 0.5);
                double axial = bl.nah / t, cos = v[0] * f[0] + v[1] * f[1] + v[2] * f[2];
                double r = axial / Math.max(1e-6, cos);
                if (r < AUGE_M) { z[4]++; continue; }
                double ost = bl.ost + v[0] * r, nord = bl.nord - v[2] * r, hh = bl.hoehe + v[1] * r;
                float eta = b.wasserstand(bl, ost, nord);
                if (!Float.isNaN(eta) && hh < eta + WASSER_UEBER_M) z[r <= NAH_M ? 1 : 2]++;
                else z[3]++;
            }
        for (int i = 0; i < 5; i++) z[i] /= Math.max(1, n);
        return z;
    }

    /** Winkel der Sonne zur Blickachse (Grad) und 1, wenn sie im Bild steht. */
    static double[] sonne(Buehne b, Blick bl, double t) {
        Instant zeit = b.uhr().start.plusMillis(Math.round(t * 1000));
        Lichtquellen.Stand q = Lichtquellen.bei(zeit, bl.ost, bl.nord, b.himmel().zone);
        double[] s = q.sonneSicht(), f = bl.achse();
        double cos = s[0] * f[0] + s[1] * f[1] + s[2] * f[2];
        double[] p = bl.bildpunkt(new double[] {s[0] * 1e6, s[1] * 1e6, s[2] * 1e6});
        boolean im = p != null && p[0] >= 0 && p[0] < bl.breite && p[1] >= 0 && p[1] < bl.hoehe_px;
        return new double[] {Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))), im ? 1 : 0};
    }

    // ------------------------------------------------------------------ 2 Xaver

    static void xaver(Buehne b, Szenen sz, int breite, int hoehe) throws Exception {
        A.teil("2 Xaver Hanswarft laufend");
        SchwimmerCheck.pegel(b, sz);
        b.oeffne(SchaumCheck.szenario(sz, XAVER[0]));
        b.wasserbild().seegang = true;
        SeegangAbnahme.Fall c = SeegangAbnahme.einrichten(b, sz, XAVER, breite, hoehe, A);
        Flotte fl = b.flotte();
        A.info("Flotte", fl == null ? "keine" : f("%d Schwimmer", fl.schwimmer.size()));
        BildzeitDiagnose.Art a = new BildzeitDiagnose.Art("A laufend, Flotte nebenher"), cc = new BildzeitDiagnose.Art("C laufend, Flotte ruht"),
                s = new BildzeitDiagnose.Art("S stehend, Blick und Zeit fest");
        List<String> runde = new ArrayList<>();
        for (int r = 0; r < RUNDEN; r++) {
            double ra, rc, rs;
            List<Double> x = new ArrayList<>();
            for (int i = 0; i < BLOCK; i++) {
                Buehne.Bildzeiten z = c.bild(b);
                a.nimm(z, c.w.baut(), Double.NaN);
                x.add(z.karte()[0]);
            }
            ra = SchaumCheck.median(x);
            if (fl != null) fl.warte();
            x.clear();
            for (int i = 0; i < BLOCK; i++) {
                c.w.zeitraffer(1);
                Blick bl = c.blickJetzt();
                c.w.vorBild(bl, c.zt[0], c.un[0]);
                Buehne.Bildzeiten z = b.bildzeiten(bl, c.zt[0], 1, true);
                c.w.nachBild();
                c.zt[0] += c.dt;
                c.un[0] += Math.round(c.dt * 1e9);
                cc.nimm(z, c.w.baut(), Double.NaN);
                x.add(z.karte()[0]);
            }
            rc = SchaumCheck.median(x);
            Blick fest = c.blickJetzt();
            Buehne.Bildzeiten z = b.bildzeiten(fest, c.zt[0], STEHEND, true);
            x.clear();
            for (int i = 0; i < STEHEND; i++) x.add(z.karte()[i]);
            for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) s.summe.merge(e.getKey(), e.getValue() * STEHEND, Double::sum);
            s.karte.addAll(x);
            s.bilder += STEHEND;
            rs = SchaumCheck.median(x);
            runde.add(f("Runde %d: A %.2f, C %.2f, S %.2f ms", r + 1, ra, rc, rs));
        }
        double[] an = anteile(b, c.bl0);
        A.info("Runden (Median je Block)", String.join("; ", runde));
        A.info("Anteile im Bild", f("Himmel %.0f %%, Wasser bis %.0f m %.0f %%, Wasser weiter %.0f %%, Land und Bauten %.0f %%, nah am Auge %.0f %%", 100 * an[0], NAH_M, 100 * an[1], 100 * an[2],
                100 * an[3], 100 * an[4]));
        for (BildzeitDiagnose.Art x : new BildzeitDiagnose.Art[] {a, cc}) A.info("Art " + x.name.charAt(0), x.text());
        A.info("Art S", f("Karte %.2f ms (Median über %d Bilder)", SchaumCheck.median(s.karte), s.bilder));
        Map<String, Double> ma = mittel(a), mc = mittel(cc), ms = mittel(s);
        ZEILEN.add(Map.entry("xaver A;0", ma));
        ZEILEN.add(Map.entry("xaver C;0", mc));
        ZEILEN.add(Map.entry("xaver S;0", ms));
        A.info("A, alle Durchgänge (ms je Bild)", oben(ma, 999));
        A.info("S, alle Durchgänge (ms je Bild)", oben(ms, 999));
        A.info("A − S je Durchgang (ms, größte)", BildzeitDiagnose.unterschiedKarten(ma, ms, 14));
        A.info("A − C je Durchgang (ms, größte)", BildzeitDiagnose.unterschiedKarten(ma, mc, 8));
        A.info("Summe der Durchgänge", f("A %.2f, C %.2f, S %.2f ms je Bild (Mittel)", summe(ma), summe(mc), summe(ms)));
    }

    // ------------------------------------------------------------------ Hilfen

    static Map<String, Double> mittel(BildzeitDiagnose.Art x) {
        Map<String, Double> m = new TreeMap<>();
        for (String k : x.summe.keySet()) m.put(k, x.mittel(k));
        return m;
    }

    static double summe(Map<String, Double> m) {
        double s = 0;
        for (double v : m.values()) s += v;
        return s;
    }

    static String oben(Map<String, Double> m, int n) {
        List<Map.Entry<String, Double>> l = new ArrayList<>(m.entrySet());
        l.sort((p, q) -> Double.compare(q.getValue(), p.getValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, l.size()); i++)
            if (l.get(i).getValue() >= 0.005) sb.append(f("%s%s %.2f", sb.length() > 0 ? "; " : "", l.get(i).getKey(), l.get(i).getValue()));
        return sb + f(" – Summe %.2f", summe(m));
    }

    static double min(List<double[]> w, int i) { double m = Double.POSITIVE_INFINITY; for (double[] x : w) m = Math.min(m, x[i]); return m; }

    static double max(List<double[]> w, int i) { double m = Double.NEGATIVE_INFINITY; for (double[] x : w) m = Math.max(m, x[i]); return m; }

    static java.awt.image.BufferedImage klein(java.awt.image.BufferedImage q, int w, int h) {
        java.awt.image.BufferedImage z = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = z.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(q, 0, 0, w, h, null);
        g.dispose();
        return z;
    }

    /** CSV: je Zeile Art;Probe;Kopfwerte (nur Bord);Durchgänge in fester Reihenfolge (ms je Bild). */
    static void csv(Path p) throws IOException {
        TreeSet<String> namen = new TreeSet<>();
        for (Map.Entry<String, Map<String, Double>> z : ZEILEN) namen.addAll(z.getValue().keySet());
        Files.createDirectories(p.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
            StringBuilder k = new StringBuilder("art;probe;laufend_ms;stehend_ms;kurs;kraengung;trimm;auge_m;sonne_grad;sonne_im_bild;himmel;wasser_nah;wasser_fern;land;nah_auge;bau");
            for (String n : namen) k.append(';').append(n.replace(';', ','));
            w.println(k);
            for (Map.Entry<String, Map<String, Double>> z : ZEILEN) {
                String[] ap = z.getKey().split(";");
                String kopf = KOPF.getOrDefault(ap[0].startsWith("bord") ? "bord;" + ap[1] : "-", ";;;;;;;;;;;;;");
                StringBuilder sb = new StringBuilder(ap[0]).append(';').append(ap[1]).append(';').append(kopf);
                for (String n : namen) {
                    Double v = z.getValue().get(n);
                    sb.append(';').append(v == null ? "" : String.format(Locale.ROOT, "%.4f", v));
                }
                w.println(sb);
            }
        }
    }
}
