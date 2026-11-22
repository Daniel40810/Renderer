package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Messquelle;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.analysis.Messblick;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Messblicks über dem Gebiet (P11c2, Werkbuch HalligHooge Teil A, 12.12; Prüfstein 19 aus Datei 55 und die
 * Bilder zur Sichtprüfung). Je Probe eine frische Bühne ohne Fenster:
 * <ol>
 * <li>Standbild ohne Messblick, Zeiten je Bild (Median aus 30);</li>
 * <li>Messblick mit den Ebenen der Probe: Standbild (Bild für die Sichtprüfung), Zeiten je Bild mit der LIC in jedem Bild
 * (der teuerste Fall), mit Zeitstempeln der Karte je Abschnitt;</li>
 * <li>eine frische Bühne mit angelegtem, ausgeschaltetem Messblick: Standbild, bitgleich zu 1.</li>
 * </ol>
 * Aufruf: {@code MessblickCheck --szenen <Datei> [--breite 1920 --hoehe 1080]}; Attest {@code docs/status/P11c2_Messblick.md},
 * Bilder {@code docs/status/P11c2_Messblick_<n>.png} und eine Übersicht {@code P11c2_Messblick.png}.
 */
public final class MessblickCheck {
    private static final Attest A = new Attest("com.dan.render.analysis + lauf.Buehne", Messblick.VERSION);

    /** Eine Probe: Szenario, Ansicht, Ebenen, Art der Linien. */
    private record Probe(String szenario, String ansicht, double stunden, boolean partikel, boolean lic, boolean linien, Messblick.Linienart art, String text) { }

    private static final Probe[] PROBEN = {
            new Probe("sediment", "prielmuendung", 0, true, false, true, Messblick.Linienart.WASSERSTAND, "Partikel und Wasserstand"),
            new Probe("ruhig", "vogelblick", 0, false, true, true, Messblick.Linienart.TIEFE, "LIC und Tiefe"),
            new Probe("sturm", "vogelblick", 0, true, true, true, Messblick.Linienart.WASSERSTAND, "alle Ebenen, Wasserstand"),
            new Probe("morpho", "japsand_oben", 10, true, true, true, Messblick.Linienart.TRUEBUNG, "alle Ebenen, Trübung"),
    };

    public static void main(String[] args) throws Exception {
        String datei = null, attest = "P11c2_Messblick";
        int breite = 1920, hoehe = 1080;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> datei = args[++i];
                case "--breite" -> breite = Integer.parseInt(args[++i]);
                case "--hoehe" -> hoehe = Integer.parseInt(args[++i]);
                case "--attest" -> attest = args[++i];
                default -> throw new IllegalArgumentException("unbekannt: " + args[i]);
            }
        }
        System.out.println("MessblickCheck – Messblick " + Messblick.VERSION + " (Werkbuch HalligHooge Teil A, P11c2, P11c3)");
        // mit der Validierungsschicht (-Dcore.gpu.validierung=true) zählen die Fehler, die Zeiten nur zur Auskunft
        boolean val = Boolean.getBoolean("core.gpu.validierung");
        if (val) A.info("Validierung", "an: Zeiten nur zur Auskunft, je Probe die Fehler der Schicht");
        BufferedImage alle = new BufferedImage(2 * breite / 2, 2 * hoehe / 2, BufferedImage.TYPE_INT_RGB);
        try {
            Szenen sz = Szenen.lies(new File(datei));
            A.info("Szenendatei", sz.datei.getPath() + f("; %d × %d Bildpunkte", breite, hoehe));
            int n = 0;
            for (Probe p : PROBEN) {
                n++;
                Szenario s = null;
                for (Szenario x : sz.szenarien()) if (x.schluessel.equals(p.szenario())) s = x;
                if (s == null) {
                    A.info("Probe " + n, "Szenario " + p.szenario() + " fehlt in der Szenendatei");
                    continue;
                }
                A.teil(n + " " + s.name + " – " + p.text());
                try (Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe)) {
                    b.oeffne(s);
                    // der Nachlauf beginnt in Ruhe: eine Probe am ersten Stand rückt um einige Stunden in den Tag
                    double t = b.uhr().sekunden() + 3600 * p.stunden();
                    Ansicht an = sz.ansicht(p.ansicht());
                    Blick bl = new Blick();
                    an.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                    bl.groesse(breite, hoehe);
                    Messquelle mq = b.messquelle();
                    A.info("Quelle", f("Ansicht %s, %s; Gitter %s; Strömung %s, Trübung %s", an.name, BildCheck.zeit(b.uhr().start, t), mq.gitter,
                            mq.stroemung ? "aus hydro_G3" : "keine", mq.truebung ? "ja" : "nein"));
                    float[] ohne = b.standbild(bl, t);
                    Buehne.Bildzeiten z0 = b.bildzeiten(bl, t, 30);
                    Messblick m = b.messblick();
                    m.partikel = p.partikel();
                    m.lic = p.lic();
                    m.linien = p.linien();
                    m.art = p.art();
                    b.messblick(true);
                    float[] mit = b.standbild(bl, t);
                    m.licJedesBild = true;
                    Buehne.Bildzeiten z1 = b.bildzeiten(bl, t, 30);
                    m.licJedesBild = false;
                    Buehne.Bildzeiten z2 = b.bildzeiten(bl, t, 30);
                    A.info("Messblick", f("%d Partikel, %d Strecken der Isolinien (%s, %.0f ms auf dem Prozessor), LIC %s", p.partikel() ? m.zahl : 0, p.linien() ? m.strecken() : 0,
                            p.art(), m.linienMs, p.lic() ? f("%d × %d", 2 * mq.gitter.nx, 2 * mq.gitter.ny) : "aus"));
                    StringBuilder ab = new StringBuilder();
                    for (var e : z1.abschnitte().entrySet()) ab.append(ab.length() == 0 ? "" : ", ").append(f("%s %.3f", e.getKey().replace("Messblick ", ""), e.getValue()));
                    A.info("Abschnitte auf der Karte (ms, Mittel, LIC laufend neu)", ab.toString());
                    if (!val) A.pruefe(median(z1.messblick()) <= 2.0, "Prüfstein 19: Kosten des Messblicks je Bild auf der Karte, alle Ebenen der Probe, LIC laufend neu (je Bild ein Streifen)",
                            f("%.2f ms, größtes von 30 %.2f ms (LIC nur bei neuem Stand %.2f ms)", median(z1.messblick()), z1.messblick()[z1.messblick().length - 1], median(z2.messblick())),
                            "≤ 2 ms [G] (Median aus 30, Zeitstempel)");
                    A.info("Bild, Uhr des Prozessors", f("ohne %.2f ms, mit %.2f ms (LIC nur bei neuem Stand %.2f ms): %+.2f ms; Median aus 30, jedes Bild abgeschickt und abgewartet",
                            median(z0.wand()), median(z1.wand()), median(z2.wand()), median(z1.wand()) - median(z0.wand())));
                    // seit W5b (Werkbuch HalligHooge 128, F218 A): Prüfstein 19 misst den Messblick, nicht die Landschaft;
                    // „ohne“ vor und nach dem Messblick gemessen (die Karte taktet), die Bildrate selbst gilt in der App
                    b.messblick(false);
                    Buehne.Bildzeiten z3 = b.bildzeiten(bl, t, 30);
                    b.messblick(true);
                    double ohneMs = (median(z0.karte()) + median(z3.karte())) / 2, mitMs = median(z2.karte());
                    if (!val) A.pruefe(mitMs - ohneMs <= 2.0, "Prüfstein 19: Bild mit Messblick gegen ohne, Zeit auf der Karte (Summe aller Durchgänge, LIC nur bei neuem Stand)",
                            f("%+.2f ms: mit %.1f ms, ohne %.1f ms (vorher %.1f, nachher %.1f); LIC laufend neu %.1f ms", mitMs - ohneMs, mitMs, ohneMs, median(z0.karte()), median(z3.karte()), median(z1.karte())),
                            "≤ ohne + 2 ms [G]");
                    A.info("Bildrate dieser Probe (Auskunft, seit W5b ohne Grenze)", f("%.0f Bilder/s mit, %.0f ohne Messblick bei %d × %d", 1000 / mitMs, 1000 / ohneMs, breite, hoehe));
                    long geaendert = 0;
                    for (int i = 0; i < ohne.length; i++) if (Math.abs(ohne[i] - mit[i]) > 1.0 / 255) geaendert++;
                    A.info("Wirkung im Bild", f("%.1f %% der Werte vom Messblick geändert", 100.0 * geaendert / ohne.length));
                    if (val) A.pruefe(b.validierungsFehler() == 0, "Prüfstein 20: Validierungsschicht über Messblick und Bühne", String.valueOf(b.validierung()), "0 Fehler");
                    // eine frische Bühne mit angelegtem, aber ausgeschaltetem Messblick gegen die erste ohne ihn
                    try (Buehne b2 = Buehne.ohneFenster(sz.gebiet(), breite, hoehe)) {
                        b2.oeffne(s);
                        b2.messblick(true);
                        b2.messblick(false);
                        float[] wieder = b2.standbild(bl, t);
                        double gr = 0;
                        long anders = 0;
                        for (int i = 0; i < ohne.length; i++) {
                            double d = Math.abs(ohne[i] - wieder[i]);
                            gr = Math.max(gr, d);
                            if (d > 0) anders++;
                        }
                        A.pruefe(anders == 0, "Prüfstein 19: frische Bühne, Messblick angelegt und aus – bitgleich zur Bühne ohne", f("%d Werte verschieden (größte %.4f)", anders, gr), "0");
                    }
                    BufferedImage img = bild(mit, breite, hoehe, s.name + " · " + BildCheck.zeit(b.uhr().start, t) + " · " + p.text());
                    File png = new File("docs" + File.separator + "status" + File.separator + (val ? "P11c3_Validierung_" : "P11c2_Messblick_") + n + ".png");
                    png.getParentFile().mkdirs();
                    ImageIO.write(img, "png", png);
                    A.info("Bild zur Sichtprüfung", png.getPath());
                    if (n <= 4) {
                        Graphics2D g2 = alle.createGraphics();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2.drawImage(img, ((n - 1) % 2) * breite / 2, ((n - 1) / 2) * hoehe / 2, breite / 2, hoehe / 2, null);
                        g2.dispose();
                    }
                }
            }
            schnittProbe(sz, breite, hoehe, val);
            File ue = new File("docs" + File.separator + "status" + File.separator + (val ? "P11c3_Messblick_Validierung.png" : "P11c2_Messblick.png"));
            ImageIO.write(alle, "png", ue);
            A.info("Übersicht", ue.getPath());
        } catch (Throwable x) {
            A.fehler(x);
        }
        Path at = A.schreibe(Path.of("docs", "status", attest + ".md"), "P11c2, P11c3 – Messblick über dem Gebiet");
        if (at != null) System.out.println("\nAttest: " + at);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /**
     * Seit P11c3: ein Schnitt durch die Rinne südlich von Hooge (O 469 500–470 700, N 6 043 610) im Ruhigen Tag mit
     * Sediment: Reihe Q(t), Bilanz über die Tide, Profil und ein Bild mit dem Schnitt (weiß) und Isolinien der Tiefe.
     */
    static void schnittProbe(Szenen sz, int breite, int hoehe, boolean val) throws Exception {
        Szenario s = null;
        for (Szenario x : sz.szenarien()) if (x.schluessel.equals("ruhigsed")) s = x;
        if (s == null) return;
        A.teil("5 " + s.name + " – Schnitt (P11c3)");
        try (Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe)) {
            b.oeffne(s);
            double t = b.uhr().sekunden();
            Blick bl = new Blick(470100, 6042000, 450, 0, -32, 55);
            bl.groesse(breite, hoehe);
            b.messblickEbenen(false, false, true, 1);
            b.standbild(bl, t);                                             // Stände laden
            com.dan.lauf.Schnitt q = b.schnitt(new double[] {469500, 6043610}, new double[] {470700, 6043610});
            A.pruefe(q != null, "Schnitt gelegt", q == null ? "nein" : f("von %.0f, %.0f nach %.0f, %.0f, %.0f m", q.ostA, q.nordA, q.ostB, q.nordB, q.laenge), "ja");
            if (q == null) return;
            long n0 = System.nanoTime();
            double[][] r = b.schnittReihe(q);
            double sek = (System.nanoTime() - n0) / 1e9;
            int gueltig = 0;
            double qmax = 0;
            for (double v : r[1])
                if (!Double.isNaN(v)) {
                    gueltig++;
                    qmax = Math.max(qmax, Math.abs(v));
                }
            A.pruefe(gueltig >= r[0].length - 1 && r[0].length > 1, "Reihe Q(t) aus water.q", f("%d Stände, %d gelesen, |Q| bis %.0f m³/s, %.1f s", r[0].length, gueltig, qmax, sek), "alle Stände");
            com.dan.lauf.Schnitt.Bilanz bi = com.dan.lauf.Schnitt.bilanz(r[0], r[1], t - 22_356, t + 22_356);
            A.info("Bilanz über die Tide um " + BildCheck.zeit(b.uhr().start, t), f("nach links (Nord) %.2f Mio. m³, nach rechts (Süd) %.2f Mio. m³, netto %+.2f Mio. m³", bi.links() / 1e6, bi.rechts() / 1e6, bi.netto() / 1e6));
            float[] bild = b.standbild(bl, t);
            com.dan.lauf.Schnitt.Profil p = b.schnittProfil(q);
            int nass = 0;
            double qm = 0;
            for (int k = 0; k < p.punkte(); k++)
                if (!Double.isNaN(p.eta()[k])) {
                    nass++;
                    qm = Math.max(qm, Math.abs(p.quer()[k]));
                }
            A.info("Profil", f("%d Punkte, %d nass, größte Geschwindigkeit quer %.2f m/s", p.punkte(), nass, qm));
            if (val) A.pruefe(b.validierungsFehler() == 0, "Prüfstein 20: Validierungsschicht mit Schnitt", String.valueOf(b.validierung()), "0 Fehler");
            File png = new File("docs" + File.separator + "status" + File.separator + (val ? "P11c3_Validierung_Schnitt.png" : "P11c3_Schnitt_Bild.png"));
            ImageIO.write(bild(bild, breite, hoehe, s.name + " · " + BildCheck.zeit(b.uhr().start, t) + " · Schnitt und Tiefe"), "png", png);
            A.info("Bild zur Sichtprüfung", png.getPath());
        }
    }

    static double median(double[] s) { return s.length == 0 ? Double.NaN : s[s.length / 2]; }

    static BufferedImage bild(float[] p, int w, int h, String text) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = 4 * (y * w + x);
                int r = Math.min(255, Math.max(0, Math.round(p[i] * 255))), g = Math.min(255, Math.max(0, Math.round(p[i + 1] * 255))), b = Math.min(255, Math.max(0, Math.round(p[i + 2] * 255)));
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(12, h / 40)));
        int x0 = 10, y0 = h - 12;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(x0 - 4, y0 - g2.getFontMetrics().getAscent() - 2, g2.getFontMetrics().stringWidth(text) + 8, g2.getFontMetrics().getHeight() + 2);
        g2.setColor(Color.WHITE);
        g2.drawString(text, x0, y0);
        g2.dispose();
        return img;
    }
}
