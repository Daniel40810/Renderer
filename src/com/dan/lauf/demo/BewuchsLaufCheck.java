package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.bewuchs.Bewuchsbild;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.dan.core.pruef.Attest.f;

/**
 * Der Bewuchs in der Szene (P12c, Werkbuch HalligHooge Teil A, 12.13, Datei 75; Prüfsteine 18 und 10): je Szenario und
 * Ansicht ein Standbild der Bühne mit Bewuchs (Gehölze, Halme) und eines ohne, bei fester Belichtung, dazu die Zeiten.
 * <ul>
 * <li><b>Prüfstein 18:</b> was der Bewuchs je Bild auf der Karte kostet (ganzes Bild mit gegen ohne, Median aus 20):
 * ≤ 3 ms; das ganze Bild ≤ 33,3 ms (≥ 30 Bilder/s).</li>
 * <li><b>Prüfstein 10:</b> die Bilder zur Sichtprüfung, {@code docs/status/P12c_Bewuchs.png} und einzeln.</li>
 * </ul>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.BewuchsLaufCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen
 * </pre>
 */
public final class BewuchsLaufCheck {
    private static final Attest A = new Attest("com.dan.lauf (Bewuchs)", Bewuchsbild.VERSION);
    static final int BREITE = HausCheck.BREITE, HOEHE = HausCheck.HOEHE;

    record Probe(String text, float[] mit, float[] ohne) { }

    public static void main(String[] args) throws Exception {
        String datei = "..\\HalligHooge\\src\\szenen\\Hooge.szenen", ansichten = "gehoelz,halme,hanswarft_haeuser,kirchwarft";
        String zeiten = "ruhig=2026-10-30T11:00:00Z,sturm=2026-10-25T23:00:00Z";
        for (int i = 0; i + 1 < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> datei = args[++i];
                case "--ansichten" -> ansichten = args[++i];
                case "--zeiten" -> zeiten = args[++i];
                default -> { }
            }
        }
        System.out.println("BewuchsLaufCheck – Bewuchs in der Szene (Werkbuch HalligHooge Teil A, P12c)");
        List<Probe> proben = new ArrayList<>();
        try {
            Szenen sz = Szenen.lies(new File(datei));
            File gb = sz.gebiet();
            for (String zt : zeiten.split(",")) {
                String[] kv = zt.split("=");
                Szenario s = null;
                for (Szenario x : sz.szenarien()) if (x.schluessel.equals(kv[0])) s = x;
                if (s == null) { A.info("Szenario " + kv[0], "nicht in der Szenendatei"); continue; }
                A.teil("Szenario " + s.name + ", " + kv[1]);
                try (Buehne b = Buehne.ohneFenster(gb, BREITE, HOEHE)) {
                    A.pruefe(b.hatBewuchs(), "Bewuchs im Gebiet (bau/gehoelz.bin)", b.hatBewuchs() ? "vorhanden" : "fehlt", "vorhanden");
                    if (!b.hatBewuchs()) break;
                    b.oeffne(s);
                    double t = (java.time.Instant.parse(kv[1]).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
                    // im Sturm nur die ersten beiden (Gehölze im Sturm, Halme im Landunter)
                    String[] liste = ansichten.split(",");
                    if (kv[0].equals("sturm")) liste = java.util.Arrays.copyOf(liste, Math.min(2, liste.length));
                    for (String an : liste) {
                        Ansicht a = sz.ansicht(an.strip());
                        if (a == null) { A.info("Ansicht " + an, "nicht in der Szenendatei"); continue; }
                        Blick bl = new Blick();
                        a.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                        bl.groesse(BREITE, HOEHE);
                        var bel = b.belichtung();
                        double evMin = bel.evMin, evMax = bel.evMax;
                        b.bewuchs(false);
                        b.standbild(bl, t);
                        double ev = bel.ev();
                        bel.evMin = bel.evMax = ev;
                        try {
                            bel.setze(ev);
                            float[] ohne = b.standbild(bl, t);
                            b.bewuchs(true);
                            bel.setze(ev);
                            float[] mit = b.standbild(bl, t);
                            long anders = 0;
                            for (int k = 0; k < BREITE * HOEHE; k++)
                                for (int c = 0; c < 3; c++)
                                    if (Float.floatToRawIntBits(mit[4 * k + c]) != Float.floatToRawIntBits(ohne[4 * k + c])) { anders++; break; }
                            A.info(a.name, f("Bewuchs ändert %.1f %% des Bilds; Belichtung %+.2f Blenden", 100.0 * anders / (BREITE * HOEHE), ev));
                            proben.add(new Probe(s.name + " – " + a.name, mit, ohne));
                            // Prüfstein 18: Zeiten mit und ohne Bewuchs
                            Buehne.Bildzeiten zm = b.bildzeiten(bl, t, 20);
                            b.bewuchs(false);
                            Buehne.Bildzeiten zo = b.bildzeiten(bl, t, 20);
                            b.bewuchs(true);
                            double km = teil(zm.abschnitte()), ko = teil(zo.abschnitte()), alle = median(zm.karte()), alleOhne = median(zo.karte());
                            A.info(a.name + ": Durchgänge mit Bewuchs", abschnitte(zm.abschnitte()));
                            A.info(a.name + ": Durchgänge ohne Bewuchs", abschnitte(zo.abschnitte()));
                            A.pruefe(alle - alleOhne <= 3.0, "Prüfstein 18: " + a.name + " – Bewuchs auf der Karte je Bild (ganzes Bild mit gegen ohne, Median aus 20)",
                                    f("%+.2f ms (%.2f gegen %.2f ms); davon Bewuchs %.2f ms; Gehölze nah %d, mittel %d, fern %d; %d Büschel", alle - alleOhne, alle, alleOhne, km - ko,
                                            b.bewuchsbild().jeStufe[0], b.bewuchsbild().jeStufe[1], b.bewuchsbild().jeStufe[2], b.bewuchsbild().bueschel()),
                                    "≤ 3 ms [G]");
                            A.pruefe(alle <= 1000.0 / 30, "Prüfstein 18: " + a.name + " – ganzes Bild auf der Karte (Median aus 20)",
                                    f("%.2f ms, %.0f Bilder/s", alle, 1000 / alle), "≤ 33,3 ms (≥ 30 Bilder/s)");
                        } finally {
                            bel.evMin = evMin;
                            bel.evMax = evMax;
                            b.bewuchs(true);
                        }
                    }
                }
            }
            if (!proben.isEmpty()) statusBild(proben);
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P12c_BewuchsLauf.md"), "P12c – BewuchsLaufCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static double median(double[] x) {
        double[] y = x.clone();
        java.util.Arrays.sort(y);
        return y[y.length / 2];
    }

    /** Summe der Durchgänge des Bewuchses (Gehölze, Schatten, Halme). */
    static double teil(Map<String, Double> m) {
        double s = 0;
        for (Map.Entry<String, Double> e : m.entrySet())
            if (e.getKey().startsWith("Bewuchs")) s += e.getValue();
        return s;
    }

    static String abschnitte(Map<String, Double> m) {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, Double> e : m.entrySet())
            if (e.getKey().startsWith("Gelände") || e.getKey().startsWith("Häuser") || e.getKey().startsWith("Bewuchs"))
                s.append(e.getKey()).append(' ').append(String.format(Locale.GERMAN, "%.2f", e.getValue())).append("; ");
        return s.toString();
    }

    static void statusBild(List<Probe> proben) throws Exception {
        int w = BREITE / 2, h = HOEHE / 2;
        BufferedImage alle = new BufferedImage(2 * w, h * proben.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = alle.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int i = 0; i < proben.size(); i++) {
            Probe p = proben.get(i);
            g.drawImage(HausCheck.bild(p.mit), 0, i * h, w, h, null);
            g.drawImage(HausCheck.bild(p.ohne), w, i * h, w, h, null);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(4, i * h + 4, g.getFontMetrics().stringWidth(p.text) + 8, 18);
            g.setColor(Color.WHITE);
            g.drawString(p.text, 8, i * h + 17);
            g.drawString("ohne Bewuchs", w + 8, i * h + 17);
        }
        g.dispose();
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        File png = d.resolve("P12c_Bewuchs.png").toFile();
        ImageIO.write(alle, "png", png);
        for (int i = 0; i < proben.size(); i++) ImageIO.write(HausCheck.bild(proben.get(i).mit), "png", d.resolve(String.format(Locale.ROOT, "P12c_Bewuchs_%d.png", i + 1)).toFile());
        A.info("Bilder zur Sichtprüfung (Prüfstein 10)", png.getPath() + " und P12c_Bewuchs_1 … " + proben.size() + ".png");
    }
}
