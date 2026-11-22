package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Bauten;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.bau.Hausbild;

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
 * Die Häuser in der Szene (P12a, Werkbuch HalligHooge Teil A, 12.13; Prüfsteine 6, 9 und 10): je Szenario und Ansicht
 * ein Standbild der Bühne mit Häusern und eines ohne, bei fester Belichtung.
 * <ul>
 * <li><b>Prüfstein 9 (Teil):</b> Außerhalb der Häuser, ihrer Schatten und ihrer Spiegelbilder (Rechtecke im Bild, aus
 * den Hüllen der Häuser und der Richtung der Sonne) sind beide Bilder bitgleich: Die Häuser ändern nichts sonst.</li>
 * <li><b>Prüfstein 6:</b> Zeit der Durchgänge „Häuser…“ auf der Karte je Bild.</li>
 * <li><b>Prüfstein 10:</b> die Bilder zur Sichtprüfung, {@code docs/status/P12a_Warften.png}.</li>
 * </ul>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.HausCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen
 *      [--ansichten hanswarft,kirchwarft,backenswarft] [--zeiten ruhig=2026-10-30T11:00:00Z,sturm=2026-10-25T23:00:00Z]
 * </pre>
 */
public final class HausCheck {
    private static final Attest A = new Attest("com.dan.lauf (Häuser)", Hausbild.VERSION);
    static final int BREITE = 960, HOEHE = 540;

    record Probe(String text, float[] mit, float[] ohne) { }

    public static void main(String[] args) throws Exception {
        String datei = "..\\HalligHooge\\src\\szenen\\Hooge.szenen", ansichten = "hanswarft_haeuser,kirchwarft,backenswarft";
        String zeiten = "ruhig=2026-10-30T11:00:00Z,sturm=2026-10-25T23:00:00Z";
        for (int i = 0; i + 1 < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> datei = args[++i];
                case "--ansichten" -> ansichten = args[++i];
                case "--zeiten" -> zeiten = args[++i];
                default -> { }
            }
        }
        System.out.println("HausCheck – Häuser in der Szene (Werkbuch HalligHooge Teil A, P12a)");
        List<Probe> proben = new ArrayList<>();
        try {
            Szenen sz = Szenen.lies(new File(datei));
            File gb = sz.gebiet();
            Bauten bt = Bauten.lies(new File(gb, "bau"));
            A.pruefe(bt != null, "Häuser im Gebiet (bau/gebaeude.bin)", bt == null ? "fehlt" : f("%d Häuser, %d Dreiecke", bt.gebaeude.size(), bt.dreiecke()), "vorhanden");
            if (bt == null) throw new IllegalStateException("ohne Häuser keine Prüfung");
            for (String zt : zeiten.split(",")) {
                String[] kv = zt.split("=");
                Szenario s = null;
                for (Szenario x : sz.szenarien()) if (x.schluessel.equals(kv[0])) s = x;
                if (s == null) { A.info("Szenario " + kv[0], "nicht in der Szenendatei"); continue; }
                A.teil("Szenario " + s.name + ", " + kv[1]);
                try (Buehne b = Buehne.ohneFenster(gb, BREITE, HOEHE)) {
                    b.oeffne(s);
                    double t = (java.time.Instant.parse(kv[1]).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
                    for (String an : ansichten.split(",")) {
                        Ansicht a = sz.ansicht(an.strip());
                        if (a == null) { A.info("Ansicht " + an, "nicht in der Szenendatei"); continue; }
                        Blick bl = new Blick();
                        a.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                        bl.groesse(BREITE, HOEHE);
                        // Belichtung aus dem Bild ohne Häuser, dann fest für beide
                        var bel = b.belichtung();
                        double evMin = bel.evMin, evMax = bel.evMax;
                        b.haeuser(false);
                        b.standbild(bl, t);
                        double ev = bel.ev();
                        bel.evMin = bel.evMax = ev;
                        try {
                            bel.setze(ev);
                            float[] ohne = b.standbild(bl, t);
                            b.haeuser(true);
                            bel.setze(ev);
                            float[] mit = b.standbild(bl, t);
                            Hausbild hb = b.hausbild();
                            boolean[] maske = maske(bt, bl, hb.quelle >= 0 ? hb.quelle : -1, b);
                            long anders = 0, draussen = 0, drin = 0;
                            for (int k = 0; k < BREITE * HOEHE; k++) {
                                boolean gleich = true;
                                for (int c = 0; c < 4; c++) if (Float.floatToRawIntBits(mit[4 * k + c]) != Float.floatToRawIntBits(ohne[4 * k + c])) gleich = false;
                                if (maske[k]) drin++;
                                if (!gleich) {
                                    anders++;
                                    if (!maske[k]) draussen++;
                                }
                            }
                            A.pruefe(draussen == 0, "Prüfstein 9: " + a.name + " – außerhalb der Häuser, Schatten und Spiegelbilder bitgleich",
                                    f("%d Bildpunkte anders, davon %d außerhalb (Maske %.1f %% des Bilds)", anders, draussen, 100.0 * drin / (BREITE * HOEHE)), "0 außerhalb");
                            A.info(a.name + ": Licht und Fenster", f("Schatten von %s; Fenster mit Licht %.0f %%; Belichtung %+.2f Blenden",
                                    hb.quelle == 0 ? "der Sonne" : hb.quelle == 1 ? "dem Mond" : "keiner Quelle", 100 * hb.fensterAnteil, ev));
                            proben.add(new Probe(s.name + " – " + a.name, mit, ohne));
                            // Prüfstein 6: Zeiten der Häuser auf der Karte
                            Buehne.Bildzeiten z = b.bildzeiten(bl, t, 20);
                            double haus = 0, alle = z.karte()[z.karte().length / 2];
                            for (Map.Entry<String, Double> e : z.abschnitte().entrySet())
                                if (e.getKey().startsWith("Häuser")) haus += e.getValue();
                            A.pruefe(haus > 0 && haus <= 3.0, "Prüfstein 6: " + a.name + " – Häuser auf der Karte je Bild (Schatten, Licht, Zeichnen)",
                                    f("%.2f ms von %.2f ms des ganzen Bilds auf der Karte (Median); %s", haus, alle, abschnitte(z.abschnitte())), "> 0 und ≤ 3 ms [G]");
                        } finally {
                            bel.evMin = evMin;
                            bel.evMax = evMax;
                            b.haeuser(true);
                        }
                    }
                }
            }
            if (!proben.isEmpty()) statusBild(proben);
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P12a_Haeuser.md"), "P12a – HausCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static String abschnitte(Map<String, Double> m) {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, Double> e : m.entrySet())
            if (e.getKey().startsWith("Häuser")) s.append(e.getKey()).append(' ').append(String.format(Locale.GERMAN, "%.2f", e.getValue())).append("; ");
        return s.toString();
    }

    /**
     * Wo die Häuser das Bild ändern dürfen: je Haus die Spalten um die Ecken seiner Hülle und die Ecken ihres Schattens
     * auf dem Boden (längs der Quelle, bis 2 km), von ihrer Oberkante bis zum unteren Bildrand – darunter liegen die
     * Spiegelbilder, die das raue Wasser lang hinabzieht; 6 Bildpunkte Rand [G].
     */
    static boolean[] maske(Bauten bt, Blick bl, int quelle, Buehne b) {
        boolean[] m = new boolean[BREITE * HOEHE];
        double[] l = null;
        if (quelle >= 0) {
            double[] w = b.hausbild().letzteQuelle();
            if (w != null) l = w;
        }
        for (Bauten.Gebaeude g : bt.gebaeude) {
            double x0 = g.huelle[0], z0 = g.huelle[1], x1 = g.huelle[2], z1 = g.huelle[3];
            double y0 = g.boden - 1, y1 = g.first + 0.5;
            List<double[]> pk = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double x = (i & 1) == 0 ? x0 : x1, y = (i & 2) == 0 ? y0 : y1, z = (i & 4) == 0 ? z0 : z1;
                pk.add(new double[] {x, y, z});
                pk.add(new double[] {x, 2 * y0 - y - 4, z});                                     // Spiegelbild (Wasser bis 4 m unter dem Boden)
                if (l != null && l[1] > 0.01) {
                    double s = Math.min(2000, (y - y0 + 1) / l[1]);
                    pk.add(new double[] {x - l[0] * s, y0 - 1, z - l[2] * s});
                }
            }
            double px0 = Double.MAX_VALUE, py0 = Double.MAX_VALUE, px1 = -Double.MAX_VALUE, py1 = -Double.MAX_VALUE;
            int vorn = 0;
            for (double[] p : pk) {
                double ost = bt.e0 + p[0], nord = bt.n0 - p[2];
                double[] rel = bl.relativ(ost, nord, p[1]);
                double drop = (rel[0] * rel[0] + rel[2] * rel[2]) / (2 * 6_371_000 / (1 - 0.13));
                rel[1] -= drop;
                double[] q = bl.bildpunkt(rel);
                if (q == null) continue;
                vorn++;
                px0 = Math.min(px0, q[0]); px1 = Math.max(px1, q[0]);
                py0 = Math.min(py0, q[1]); py1 = Math.max(py1, q[1]);
            }
            if (vorn == 0) continue;
            if (vorn < pk.size()) { px0 = 0; px1 = BREITE; }                             // teils hinter dem Auge: breit
            int a = (int) Math.max(0, Math.floor(px0) - 6), c = (int) Math.min(BREITE - 1, Math.ceil(px1) + 6);
            // nach unten bis zum Rand: Spiegelbilder auf rauem Wasser ziehen sich lang hinab (Befund P12a, Fensterlicht im Landunter)
            int bb = (int) Math.max(0, Math.floor(py0) - 6), d = HOEHE - 1;
            for (int y = bb; y <= d; y++) for (int x = a; x <= c; x++) m[y * BREITE + x] = true;
        }
        return m;
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
            g.drawImage(bild(p.mit), 0, i * h, w, h, null);
            g.drawImage(bild(p.ohne), w, i * h, w, h, null);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(4, i * h + 4, g.getFontMetrics().stringWidth(p.text) + 8, 18);
            g.setColor(Color.WHITE);
            g.drawString(p.text, 8, i * h + 17);
            g.drawString("ohne Häuser", w + 8, i * h + 17);
        }
        g.dispose();
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        File png = d.resolve("P12a_Warften.png").toFile();
        ImageIO.write(alle, "png", png);
        // jedes Bild mit Häusern auch einzeln in voller Größe
        for (int i = 0; i < proben.size(); i++) ImageIO.write(bild(proben.get(i).mit), "png", d.resolve(f("P12a_Warften_%d.png", i + 1)).toFile());
        A.info("Bilder zur Sichtprüfung (Prüfstein 10)", png.getPath() + " und P12a_Warften_1 … " + proben.size() + ".png");
    }

    static BufferedImage bild(float[] p) {
        BufferedImage im = new BufferedImage(BREITE, HOEHE, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < BREITE * HOEHE; k++) {
            int r = Math.min(255, Math.max(0, Math.round(255 * p[4 * k]))), gg = Math.min(255, Math.max(0, Math.round(255 * p[4 * k + 1]))),
                    b = Math.min(255, Math.max(0, Math.round(255 * p[4 * k + 2])));
            im.setRGB(k % BREITE, k / BREITE, r << 16 | gg << 8 | b);
        }
        return im;
    }
}
