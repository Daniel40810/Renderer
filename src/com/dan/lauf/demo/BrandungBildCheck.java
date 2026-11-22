package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.brandung.Brandungsbild;
import com.dan.render.water.Brandprobe;
import com.dan.render.water.Wasserbild;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Brandung im Bild auf der Karte (seit P13c, Werkbuch HalligHooge Teil A, 12.14; Lauf C):
 * <ul>
 * <li><b>Teil probe:</b> Prüfstein 15 (Höhe der langen Wellen, Karte gegen Prozessor) und 18 auf der Karte (Mittel der
 * Schaumköpfe gegen die Bedeckung) im Szenario xaver an der Hanswarft.</li>
 * <li><b>Teil bitgleich:</b> Prüfstein 7 – ohne Seegang ist das Bild mit Brandung an und aus bitgleich.</li>
 * <li><b>Teil bild:</b> Prüfsteine 9 (Kosten mit und ohne Brandung) und 10 (Bilder zur Sichtprüfung
 * {@code docs/status/P13c_Brandung_Bild.png}, je Ansicht mit und ohne).</li>
 * </ul>
 * Attest {@code docs/status/P13c_BrandungBild.md}.
 * <pre>  BrandungBildCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--teil probe,bitgleich,bild]</pre>
 */
public final class BrandungBildCheck {
    private static final Attest A = new Attest("com.dan.render.brandung (Bild)", Brandungsbild.VERSION);
    static final int BREITE = 1280, HOEHE = 720;
    static final DateTimeFormatter UHR = DateTimeFormatter.ofPattern("d.M. HH:mm", Locale.GERMAN).withZone(ZoneId.of("Europe/Berlin"));
    /** Ansichten und Zeiten (UTC) der Bilder: Szenario, Ansicht, Zeit. */
    static String bilder = "xaver/landunter_hanswarft/2013-12-05T13:30:00Z,xaver/brandung_hanswarft/2013-12-05T13:30:00Z,xaver/steinkante_west/2013-12-05T12:00:00Z,"
            + "xaver/westkante/2013-12-05T13:30:00Z,xaver/warften/2013-12-05T13:30:00Z,ruhigsee/westspitze/2026-10-30T10:00:00Z";

    record Probe(String text, float[] an, float[] aus) { }

    public static void main(String[] args) {
        System.out.println("BrandungBildCheck – Brandung auf der Karte (Werkbuch HalligHooge Teil A, P13c)");
        File szenen = null;
        String teile = "probe,bitgleich,bild";
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--teil" -> teile = args[i + 1];
                case "--bilder" -> bilder = args[i + 1];
                default -> { }
            }
        }
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            for (String t : teile.split(",")) {
                switch (t.strip()) {
                    case "probe" -> probe(sz);
                    case "bitgleich" -> bitgleich(sz);
                    case "bild" -> bild(sz);
                    case "diagnose" -> diagnose(sz);
                    default -> A.info("Teil " + t, "unbekannt");
                }
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", teile.strip().equals("diagnose") ? "P13c_Diagnose.md" : "P13c_BrandungBild.md"), "P13c – BrandungBildCheck (" + teile + ")");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static Szenario szenario(Szenen sz, String k) {
        for (Szenario x : sz.szenarien()) if (x.schluessel.equals(k)) return x;
        return null;
    }

    static Blick blick(Buehne b, Szenen sz, String ansicht, double t) {
        Ansicht a = sz.ansicht(ansicht);
        if (a == null) return null;
        Blick bl = new Blick();
        a.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
        bl.groesse(BREITE, HOEHE);
        return bl;
    }

    static double zeit(Buehne b, String utc) { return (Instant.parse(utc).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0; }

    // ------------------------------------------------------------------ Teil probe

    static void probe(Szenen sz) throws Exception {
        A.teil("Probe auf der Karte: Höhe der langen Wellen (Prüfstein 15), Schaumköpfe (Prüfstein 18)");
        Szenario s = szenario(sz, "xaver");
        if (s == null) { A.pruefe(false, "Szenario xaver", "fehlt", "vorhanden"); return; }
        try (Buehne b = Buehne.ohneFenster(sz.gebiet(), BREITE, HOEHE)) {
            b.oeffne(s);
            double t = zeit(b, "2013-12-05T13:30:00Z");
            Blick bl = blick(b, sz, "landunter_hanswarft", t);
            b.brandung(true);
            b.standbild(bl, t);
            Wasserbild w = b.wasserbild();
            Random r = new Random(15);
            int n = 400_000;                                  // Lauf C: 40 000 gaben bei W = 1,5 % einen Standardfehler von 4 %
            float[] orte = new float[2 * n];
            for (int i = 0; i < n; i++) {
                double d = 30 + 970 * r.nextDouble(), a = Math.toRadians(bl.richtung + (r.nextDouble() - 0.5) * 60);
                orte[2 * i] = (float) (d * Math.sin(a));
                orte[2 * i + 1] = (float) (-d * Math.cos(a));
            }
            for (float fuss : new float[] {0.5f, 20f}) {
                Brandprobe.Ergebnis e = Brandprobe.rechne(b.geraet(), b.konto(), w, bl.ost, bl.nord, orte, fuss, 2.3f);
                double dz = 0, dzs = 0, sk = 0, sw = 0;
                int da = 0;
                for (int i = 0; i < n; i++) {
                    if (e.karte()[8 * i + 7] < 0.5 || e.prozessor()[8 * i + 7] < 0.5) continue;
                    da++;
                    dz = Math.max(dz, Math.abs(e.karte()[8 * i] - e.prozessor()[8 * i]));
                    dzs = Math.max(dzs, Math.abs(e.karte()[8 * i + 1] - e.prozessor()[8 * i + 1]));
                    sk += e.karte()[8 * i + 6];
                    sw += e.prozessor()[8 * i + 6];
                }
                if (fuss < 1) A.pruefe(da > 0 && dz <= 1e-3, f("Prüfstein 15: Höhe ζ der langen Wellen, Karte gegen Prozessor (%d Orte, Bildpunkt %.1f m)", da, fuss),
                        f("größte Abweichung %.2f mm; mit spitzem Kamm %.2f mm", 1000 * dz, 1000 * dzs), "≤ 1 mm");
                A.pruefe(da > 0 && Math.abs(sk / Math.max(1e-9, sw) - 1) <= 0.05, f("Prüfstein 18 (Karte): Mittel der Schaumköpfe gegen die Bedeckung W, Bildpunkt %.1f m", fuss),
                        f("%.4f gegen %.4f: %.3f", sk / Math.max(1, da), sw / Math.max(1, da), sk / Math.max(1e-9, sw)), "1 ± 0,05");
            }
        }
    }

    // ------------------------------------------------------------------ Teil bitgleich

    static void bitgleich(Szenen sz) throws Exception {
        A.teil("Ohne Seegang unverändert (Prüfstein 7)");
        Szenario s = szenario(sz, "xaver");
        if (s == null) return;
        try (Buehne b = Buehne.ohneFenster(sz.gebiet(), BREITE, HOEHE)) {
            b.oeffne(s);
            double t = zeit(b, "2013-12-05T13:30:00Z");
            Blick bl = blick(b, sz, "landunter_hanswarft", t);
            // bei fester Belichtung (wie VegetationLaufCheck, Prüfstein 7 von P13b)
            var bel = b.belichtung();
            b.wasserbild().seegang = false;
            b.brandung(false);
            bel.sofort();
            b.standbild(bl, t);
            double ev = bel.ev();
            bel.evMin = bel.evMax = ev;
            bel.setze(ev);
            float[] aus = b.standbild(bl, t).clone();
            b.brandung(true);
            bel.setze(ev);
            b.standbild(bl, t);
            bel.setze(ev);
            float[] an = b.standbild(bl, t);
            int gleich = 0;
            for (int i = 0; i < an.length; i++) if (Float.floatToRawIntBits(an[i]) == Float.floatToRawIntBits(aus[i])) gleich++;
            A.pruefe(gleich == an.length, "Prüfstein 7: Xaver ohne Seegang, Hanswarft, Brandung an gegen aus", f("%d von %d Werten gleich", gleich, an.length), "bitgleich");
            b.wasserbild().seegang = true;
        }
    }

    // ------------------------------------------------------------------ Teil bild

    static void bild(Szenen sz) throws Exception {
        A.teil("Bilder mit und ohne Brandung (Prüfsteine 9 und 10)");
        List<Probe> proben = new ArrayList<>();
        double langsam = 0, mehr = 0;
        String langsamName = "", mehrName = "";
        String offen = null;
        Buehne b = null;
        try {
            for (String e : bilder.split(",")) {
                String[] q = e.strip().split("/");
                if (!q[0].equals(offen)) {
                    if (b != null) b.close();
                    Szenario s = szenario(sz, q[0]);
                    if (s == null) { A.info("Szenario " + q[0], "fehlt"); b = null; offen = null; continue; }
                    b = Buehne.ohneFenster(sz.gebiet(), BREITE, HOEHE);
                    b.oeffne(s);
                    offen = q[0];
                }
                double t = zeit(b, q[2]);
                Blick bl = blick(b, sz, q[1], t);
                if (bl == null) { A.info("Ansicht " + q[1], "nicht in der Szenendatei"); continue; }
                float[][] bild = new float[2][];
                double[] ms = new double[2];
                for (int k = 0; k < 2; k++) {
                    b.brandung(k == 1);
                    b.standbild(bl, t);
                    b.standbild(bl, t);
                    bild[k] = b.standbild(bl, t);
                    Buehne.Bildzeiten z = b.bildzeiten(bl, t, 20);
                    ms[k] = z.karte()[z.karte().length / 2];
                }
                Brandungsbild bb = b.brandungsbild();
                String name = sz.ansicht(q[1]).name + ", " + UHR.format(Instant.parse(q[2])) + " MEZ";
                proben.add(new Probe(name, bild[1], bild[0]));
                A.info("Bild: " + name, f("ohne %.2f ms, mit %.2f ms (%+.2f ms) auf der Karte (Median aus 20); Kern %s", ms[0], ms[1], ms[1] - ms[0],
                        bb == null ? "ohne Kanten" : f("%.2f ms, %d Scheitel, %d Bündel", bb.letzteMs, bb.letzteScheitel, bb.letzteBuendel)));
                if (ms[1] > langsam) { langsam = ms[1]; langsamName = name; }
                if (ms[1] - ms[0] > mehr) { mehr = ms[1] - ms[0]; mehrName = name; }
            }
        } finally {
            if (b != null) b.close();
        }
        A.pruefe(langsam > 0 && 1000 / langsam >= 30, "Prüfstein 9: langsamstes Bild mit Brandung", f("%.2f ms, %.0f Bilder/s (%s)", langsam, 1000 / Math.max(1e-3, langsam), langsamName), "≥ 30 Bilder/s");
        A.pruefe(mehr <= 2.0, "Prüfstein 9: Mehrkosten der Brandung (Wasser mit Höhe, Netze, Gischt), größte", f("%+.2f ms (%s)", mehr, mehrName), "≤ 2 ms [G]");
        if (!proben.isEmpty()) statusBild(proben);
    }

    /** Standbild (RGBA float, BREITE × HOEHE) als Bild; HausCheck.bild rechnet mit 960 × 540 (Lauf C: verzerrte Bilder). */
    static BufferedImage bild(float[] p) {
        BufferedImage im = new BufferedImage(BREITE, HOEHE, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < BREITE * HOEHE; k++) {
            int r = Math.min(255, Math.max(0, Math.round(255 * p[4 * k]))), gg = Math.min(255, Math.max(0, Math.round(255 * p[4 * k + 1]))),
                    b = Math.min(255, Math.max(0, Math.round(255 * p[4 * k + 2])));
            im.setRGB(k % BREITE, k / BREITE, r << 16 | gg << 8 | b);
        }
        return im;
    }

    // ------------------------------------------------------------------ Teil diagnose (Lauf C4, Datei 102: A35)

    /**
     * Die Westspitze (A35) mit je einem Teil aus: woher kommen die grauen Streifen? Bilder bei fester Belichtung in
     * {@code docs/status/P13c_Diagnose.png}; je Bild der Anteil der Bildpunkte, der sich gegen „alles“ ändert.
     */
    static void diagnose(Szenen sz) throws Exception {
        A.teil("Diagnose A35: Westspitze, 5.12. 14:30 MEZ, je ein Teil aus");
        Szenario s = szenario(sz, "xaver");
        if (s == null) return;
        String[] namen = {"alles", "Bewuchs aus", "Gestalt aus", "Häuser aus", "Brandung aus", "Seegang aus", "Kräuselung aus",
                "Spiegelung des Geländes aus", "Wasser: nur Spiegelung", "Wasser: nur Glanz", "Wasser: nur Säule", "Wasser: nur Grund"};
        List<Probe> proben = new ArrayList<>();
        try (Buehne b = Buehne.ohneFenster(sz.gebiet(), BREITE, HOEHE)) {
            b.oeffne(s);
            double t = zeit(b, "2013-12-05T13:30:00Z");
            Blick bl = blick(b, sz, "westkante", t);
            Wasserbild w = b.wasserbild();
            var bel = b.belichtung();
            bel.sofort();
            b.standbild(bl, t);
            double ev = bel.ev();
            bel.evMin = bel.evMax = ev;
            float[] ref = null;
            for (int k = 0; k < namen.length; k++) {
                b.bewuchs(k != 1);
                b.gestalt(k != 2);
                b.haeuser(k != 3);
                b.brandung(k != 4);
                w.seegang = k != 5;
                w.kraeuseln = k != 6;
                w.spiegeln = k != 7;
                w.teil = k >= 8 ? (k == 8 ? 1 : k == 9 ? 2 : k == 10 ? 3 : 5) : 0;
                bel.setze(ev);
                b.standbild(bl, t);
                bel.setze(ev);
                float[] p = b.standbild(bl, t).clone();
                if (k == 0) ref = p;
                int anders = 0;
                for (int i = 0; i < p.length; i += 4)
                    if (Math.abs(p[i] - ref[i]) + Math.abs(p[i + 1] - ref[i + 1]) + Math.abs(p[i + 2] - ref[i + 2]) > 0.02f) anders++;
                A.info("Diagnose: " + namen[k], f("%.1f %% der Bildpunkte anders als „alles“", 100.0 * anders / (p.length / 4)));
                proben.add(new Probe(namen[k], p, p));
            }
            b.bewuchs(true);
            b.gestalt(true);
            b.haeuser(true);
            b.brandung(true);
            w.seegang = w.kraeuseln = w.spiegeln = true;
            w.teil = 0;
        }
        int bw = BREITE / 2, bh = HOEHE / 2, sp = 3, ze = (proben.size() + sp - 1) / sp;
        BufferedImage alle = new BufferedImage(sp * bw, ze * bh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = alle.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int i = 0; i < proben.size(); i++) {
            int x = (i % sp) * bw, y = (i / sp) * bh;
            g.drawImage(bild(proben.get(i).an), x, y, bw, bh, null);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(x + 4, y + 4, g.getFontMetrics().stringWidth(proben.get(i).text) + 8, 18);
            g.setColor(Color.WHITE);
            g.drawString(proben.get(i).text, x + 8, y + 17);
        }
        g.dispose();
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        ImageIO.write(alle, "png", d.resolve("P13c_Diagnose.png").toFile());
        A.info("Bild", "docs\\status\\P13c_Diagnose.png");
    }

    static void statusBild(List<Probe> proben) throws Exception {
        int w = BREITE / 2, h = HOEHE / 2, spalten = 2, zeilen = proben.size();
        BufferedImage alle = new BufferedImage(spalten * w, zeilen * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = alle.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        for (int i = 0; i < proben.size(); i++) {
            Probe p = proben.get(i);
            for (int k = 0; k < 2; k++) {
                BufferedImage bi = bild(k == 0 ? p.aus : p.an);
                int x = k * w, y = i * h;
                g.drawImage(bi, x, y, w, h, null);
                String text = p.text + (k == 0 ? " – ohne Brandung (P13b)" : " – mit Brandung (P13c)");
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect(x + 4, y + 4, g.getFontMetrics().stringWidth(text) + 8, 18);
                g.setColor(Color.WHITE);
                g.drawString(text, x + 8, y + 17);
                if (k == 1) ImageIO.write(bi, "png", d.resolve(f("P13c_Brandung_%d.png", i + 1)).toFile());
            }
        }
        g.dispose();
        File png = d.resolve("P13c_Brandung_Bild.png").toFile();
        ImageIO.write(alle, "png", png);
        A.info("Bilder zur Sichtprüfung (Prüfstein 10)", png.getPath() + " (links ohne, rechts mit Brandung) und P13c_Brandung_1 … " + proben.size() + ".png");
    }
}
