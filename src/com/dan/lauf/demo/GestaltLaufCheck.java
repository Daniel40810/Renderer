package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.terrain.Gelaendebild;

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
 * Die Gestalt in der Szene (P12b, Werkbuch HalligHooge Teil A, 12.13, Datei 69; Prüfsteine 15 und 10): je Szenario und
 * Ansicht ein Standbild der Bühne mit Gestalt und eines ohne, bei fester Belichtung, dazu die Zeiten auf der Karte.
 * <ul>
 * <li><b>Prüfstein 15:</b> Was die Gestalt je Bild auf der Karte kostet (Durchgänge „Gelände…“ und „Häuser…“ mit gegen
 * ohne Gestalt, Median aus 20 Bildern): ≤ 1,5 ms; das ganze Bild auf der Karte ≤ 33,3 ms (≥ 30 Bilder/s).</li>
 * <li><b>Prüfstein 10:</b> die Bilder zur Sichtprüfung, {@code docs/status/P12b_Gestalt.png} und einzeln.</li>
 * </ul>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.GestaltLaufCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen
 *      [--ansichten fething,lahnungen,hafen,hanswarft_haeuser] [--zeiten ruhig=2026-10-30T09:30:00Z,sturm=2026-10-25T23:00:00Z]
 * </pre>
 */
public final class GestaltLaufCheck {
    private static final Attest A = new Attest("com.dan.lauf (Gestalt)", Gelaendebild.VERSION);
    static final int BREITE = HausCheck.BREITE, HOEHE = HausCheck.HOEHE;

    record Probe(String text, float[] mit, float[] ohne) { }

    public static void main(String[] args) throws Exception {
        String datei = "..\\HalligHooge\\src\\szenen\\Hooge.szenen", ansichten = "fething,lahnungen,hafen,hanswarft_haeuser";
        String zeiten = "ruhig=2026-10-30T09:30:00Z,sturm=2026-10-25T23:00:00Z";
        for (int i = 0; i + 1 < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> datei = args[++i];
                case "--ansichten" -> ansichten = args[++i];
                case "--zeiten" -> zeiten = args[++i];
                default -> { }
            }
        }
        System.out.println("GestaltLaufCheck – Gestalt in der Szene (Werkbuch HalligHooge Teil A, P12b)");
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
                    A.pruefe(b.hatGestalt(), "Gestalt im Gebiet (gestalt/, bau/koerper.bin)", b.hatGestalt() ? "vorhanden" : "fehlt", "vorhanden");
                    if (!b.hatGestalt()) break;
                    b.oeffne(s);
                    double t = (java.time.Instant.parse(kv[1]).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
                    // die Ansichten nur einmal je Szenario: im Sturm nur die erste (Landunter über dem Fething)
                    String[] liste = ansichten.split(",");
                    if (kv[0].equals("sturm")) liste = new String[] {liste[0]};
                    for (String an : liste) {
                        Ansicht a = sz.ansicht(an.strip());
                        if (a == null) { A.info("Ansicht " + an, "nicht in der Szenendatei"); continue; }
                        Blick bl = new Blick();
                        a.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                        bl.groesse(BREITE, HOEHE);
                        var bel = b.belichtung();
                        double evMin = bel.evMin, evMax = bel.evMax;
                        b.gestalt(false);
                        b.standbild(bl, t);
                        double ev = bel.ev();
                        bel.evMin = bel.evMax = ev;
                        try {
                            bel.setze(ev);
                            float[] ohne = b.standbild(bl, t);
                            b.gestalt(true);
                            bel.setze(ev);
                            float[] mit = b.standbild(bl, t);
                            long anders = 0;
                            for (int k = 0; k < BREITE * HOEHE; k++)
                                for (int c = 0; c < 3; c++)
                                    if (Float.floatToRawIntBits(mit[4 * k + c]) != Float.floatToRawIntBits(ohne[4 * k + c])) { anders++; break; }
                            A.info(a.name, f("Gestalt ändert %.1f %% des Bilds; Belichtung %+.2f Blenden", 100.0 * anders / (BREITE * HOEHE), ev));
                            proben.add(new Probe(s.name + " – " + a.name, mit, ohne));
                            // Prüfstein 15: Zeiten mit und ohne Gestalt
                            Buehne.Bildzeiten zm = b.bildzeiten(bl, t, 20);
                            b.gestalt(false);
                            Buehne.Bildzeiten zo = b.bildzeiten(bl, t, 20);
                            b.gestalt(true);
                            double km = teil(zm.abschnitte()), ko = teil(zo.abschnitte()), alle = median(zm.karte()), alleOhne = median(zo.karte());
                            A.info(a.name + ": Durchgänge mit Gestalt", abschnitte(zm.abschnitte()));
                            A.info(a.name + ": Durchgänge ohne Gestalt", abschnitte(zo.abschnitte()));
                            A.pruefe(alle - alleOhne <= 1.5, "Prüfstein 15: " + a.name + " – Gestalt auf der Karte je Bild (ganzes Bild mit gegen ohne, Median aus 20)",
                                    f("%+.2f ms (%.2f gegen %.2f ms); davon Gelände und Häuser %+.2f ms (%.2f gegen %.2f)", alle - alleOhne, alle, alleOhne, km - ko, km, ko),
                                    "≤ 1,5 ms [G]");
                            A.pruefe(alle <= 1000.0 / 30, "Prüfstein 15: " + a.name + " – ganzes Bild auf der Karte (Median aus 20)",
                                    f("%.2f ms, %.0f Bilder/s", alle, 1000 / alle), "≤ 33,3 ms (≥ 30 Bilder/s)");
                        } finally {
                            bel.evMin = evMin;
                            bel.evMax = evMax;
                            b.gestalt(true);
                        }
                    }
                }
            }
            if (!proben.isEmpty()) statusBild(proben);
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P12b_GestaltLauf.md"), "P12b – GestaltLaufCheck");
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

    /** Summe der Durchgänge, die die Gestalt berührt: Gelände und Häuser (mit den Körpern). */
    static double teil(Map<String, Double> m) {
        double s = 0;
        for (Map.Entry<String, Double> e : m.entrySet())
            if (e.getKey().startsWith("Gelände") || e.getKey().startsWith("Häuser")) s += e.getValue();
        return s;
    }

    static String abschnitte(Map<String, Double> m) {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, Double> e : m.entrySet())
            if (e.getKey().startsWith("Gelände") || e.getKey().startsWith("Häuser"))
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
            g.drawString("ohne Gestalt", w + 8, i * h + 17);
        }
        g.dispose();
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        File png = d.resolve("P12b_Gestalt.png").toFile();
        ImageIO.write(alle, "png", png);
        for (int i = 0; i < proben.size(); i++) ImageIO.write(HausCheck.bild(proben.get(i).mit), "png", d.resolve(String.format(Locale.ROOT, "P12b_Gestalt_%d.png", i + 1)).toFile());
        A.info("Bilder zur Sichtprüfung (Prüfstein 10)", png.getPath() + " und P12b_Gestalt_1 … " + proben.size() + ".png");
    }
}
