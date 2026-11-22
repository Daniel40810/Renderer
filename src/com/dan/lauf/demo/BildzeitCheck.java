package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Lauf;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;

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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import static com.dan.core.pruef.Attest.f;

/**
 * Kosten im Bild und Nachthimmel (P13d; Werkbuch HalligHooge Teil A, 12.14, Datei 106).
 * <ul>
 * <li><b>Teil zeiten</b> (A41, A30): je Ansicht {@link #RUNDEN} Runden, darin der Schalter (Brandung, Bewuchs)
 * abwechselnd aus–an und an–aus, je {@link #AUFWAERMEN} Bilder zum Aufwärmen und {@link #BILDER} gemessene Bilder
 * (Zeitstempel der Karte je Abschnitt). Je Runde der Unterschied der Mediane; über die Runden Median und Streuung, dazu
 * je Bildteil das Mittel. Prüfsteine 3 (Brandung an der Westspitze ≤ 2 ms), 4 (Streuung, Auskunft), 5 (Bewuchs ≤ 0,5 ms).</li>
 * <li><b>Teil nacht</b> (A36): Xaver am 6.12. um 04:00 MEZ und der ruhige Tag am 30.10. um 04:00 MEZ, je vier Ansichten,
 * Streulicht des Nachthimmels aus und an; Prüfsteine 6 (aus–an–aus bitgleich), 7 (Helligkeit 0,3–5° über der Kimm
 * gegen 10–20°, Tag bitgleich); Bild {@code docs/status/P13d_Nacht.png} zur Sichtprüfung (Prüfstein 9).</li>
 * </ul>
 * Attest {@code docs/status/P13d_Bild.md}.
 */
public final class BildzeitCheck {
    private static final Attest A = new Attest("com.dan.lauf (Bild)", Lauf.VERSION);
    static final int BREITE = 1280, HOEHE = 720, RUNDEN = 5, AUFWAERMEN = 30, BILDER = 120;
    static final DateTimeFormatter UHR = DateTimeFormatter.ofPattern("d.M. HH:mm", Locale.GERMAN).withZone(ZoneId.of("Europe/Berlin"));
    /** Ansichten der Brandung (wie P13c, Prüfstein 9); die erste ist die Westspitze (Prüfstein 3). */
    static String brandung = "xaver/westkante/2013-12-05T13:30:00Z,xaver/landunter_hanswarft/2013-12-05T13:30:00Z,xaver/brandung_hanswarft/2013-12-05T13:30:00Z,"
            + "xaver/steinkante_west/2013-12-05T12:00:00Z,xaver/warften/2013-12-05T13:30:00Z";
    /** Ansichten des Bewuchses (A30). */
    static String bewuchs = "ruhig/gehoelz/2026-10-30T10:00:00Z,ruhig/hanswarft_haeuser/2026-10-30T10:00:00Z,ruhig/vorland/2026-10-30T10:00:00Z,"
            + "xaver/landunter_hanswarft/2013-12-05T13:30:00Z";
    static String nachtXaver = "landunter_hanswarft,landunter_kirchwarft,warften,westkante", nachtRuhig = "westspitze,hanswarft,japsand,abend";
    static final String ZEIT_XAVER = "2013-12-06T03:00:00Z", ZEIT_RUHIG = "2026-10-30T03:00:00Z", TAG_RUHIG = "2026-10-30T10:00:00Z";

    public static void main(String[] args) {
        System.out.println("BildzeitCheck – Kosten im Bild und Nachthimmel (Werkbuch HalligHooge Teil A, P13d)");
        File szenen = null;
        String teile = "zeiten,nacht";
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--teil" -> teile = args[i + 1];
                case "--brandung" -> brandung = args[i + 1];
                case "--bewuchs" -> bewuchs = args[i + 1];
                default -> { }
            }
        }
        long n0 = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            for (String t : teile.split(",")) {
                switch (t.strip()) {
                    case "zeiten" -> zeiten(sz);
                    case "nacht" -> nacht(sz);
                    default -> A.info("Teil " + t, "unbekannt");
                }
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.1f min", (System.nanoTime() - n0) / 6e10));
        Path p = A.schreibe(Path.of("docs", "status", "P13d_Bild.md"), "P13d – BildzeitCheck (" + teile + ")");
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

    static String name(Szenen sz, String ansicht, String utc) { return sz.ansicht(ansicht).name + ", " + UHR.format(Instant.parse(utc)) + " MEZ"; }

    // ------------------------------------------------------------------ Teil zeiten

    /** Ergebnis einer Ansicht: Unterschied an − aus je Runde (ms), Mediane aus und an, Unterschied je Abschnitt. */
    record Messung(String name, double[] delta, double aus, double an, Map<String, Double> abschnitte) {
        double median() { return med(delta); }
        double streuung() {
            double m = 0, q = 0;
            for (double d : delta) m += d / delta.length;
            for (double d : delta) q += (d - m) * (d - m);
            return Math.sqrt(q / Math.max(1, delta.length - 1));
        }
    }

    static double med(double[] a) {
        double[] s = a.clone();
        Arrays.sort(s);
        int n = s.length;
        return n == 0 ? Double.NaN : n % 2 == 1 ? s[n / 2] : 0.5 * (s[n / 2 - 1] + s[n / 2]);
    }

    static void zeiten(Szenen sz) throws Exception {
        A.teil("Kosten im Bild, abwechselnd und wiederholt (Prüfsteine 3–5)");
        A.info("Messung", f("%d × %d; je Ansicht %d Runden, Schalter abwechselnd aus–an und an–aus; je Messung %d Bilder zum Aufwärmen, dann %d Bilder, Median der Karte (Summe aller Abschnitte je Bild)",
                BREITE, HOEHE, RUNDEN, AUFWAERMEN, BILDER));
        List<Messung> br = reihe(sz, "Brandung", brandung, Buehne::brandung);
        List<Messung> bw = reihe(sz, "Bewuchs", bewuchs, Buehne::bewuchs);
        if (!br.isEmpty()) {
            Messung w = br.get(0);
            A.pruefe(w.median() <= 2.0, "Prüfstein 3: Brandung an der Westspitze, Median aus " + RUNDEN + " abwechselnden Runden (A41)",
                    f("%+.2f ms (%s; aus %.2f, an %.2f ms)", w.median(), w.name, w.aus, w.an), "≤ 2 ms (wie P13c)");
            double s = 0;
            String sn = "";
            for (Messung m : br) if (m.streuung() > s) { s = m.streuung(); sn = m.name; }
            for (Messung m : bw) if (m.streuung() > s) { s = m.streuung(); sn = m.name; }
            A.info("Prüfstein 4: Streuung des Unterschieds zwischen den Runden (Standardabweichung), größte", f("%.2f ms (%s); Westspitze %.2f ms%s", s, sn, w.streuung(),
                    w.streuung() > 1 ? " – über 1 ms: Prüfstein 3 gilt als nicht messbar" : ""));
        }
        if (!bw.isEmpty()) {
            Messung g = bw.get(0);
            for (Messung m : bw) if (m.median() > g.median()) g = m;
            A.pruefe(g.median() <= 0.5, "Prüfstein 5: Bewuchs, größter Median der Mehrkosten (A30)", f("%+.2f ms (%s)", g.median(), g.name), "≤ +0,5 ms (wie P13b, Prüfstein 14)");
        }
    }

    static List<Messung> reihe(Szenen sz, String was, String liste, BiConsumer<Buehne, Boolean> schalter) throws Exception {
        List<Messung> out = new ArrayList<>();
        String offen = null;
        Buehne b = null;
        try {
            for (String e : liste.split(",")) {
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
                String name = name(sz, q[1], q[2]);
                double[] delta = new double[RUNDEN], aus = new double[RUNDEN], an = new double[RUNDEN];
                Map<String, double[]> ab = new TreeMap<>();
                for (int r = 0; r < RUNDEN; r++) {
                    boolean[] folge = r % 2 == 0 ? new boolean[] {false, true} : new boolean[] {true, false};
                    double[] med = new double[2];
                    Map<String, Double>[] abs = new Map[2];
                    for (boolean x : folge) {
                        schalter.accept(b, x);
                        b.bildzeiten(bl, t, AUFWAERMEN);
                        Buehne.Bildzeiten z = b.bildzeiten(bl, t, BILDER, true);
                        med[x ? 1 : 0] = med(z.karte());
                        abs[x ? 1 : 0] = z.abschnitte();
                    }
                    delta[r] = med[1] - med[0];
                    aus[r] = med[0];
                    an[r] = med[1];
                    java.util.Set<String> namen = new java.util.TreeSet<>(abs[0].keySet());
                    namen.addAll(abs[1].keySet());
                    for (String n : namen) ab.computeIfAbsent(n, k -> new double[RUNDEN])[r] = abs[1].getOrDefault(n, 0.0) - abs[0].getOrDefault(n, 0.0);
                }
                schalter.accept(b, true);
                Map<String, Double> abm = new LinkedHashMap<>();
                ab.entrySet().stream().sorted((x, y) -> Double.compare(Math.abs(med(y.getValue())), Math.abs(med(x.getValue()))))
                        .forEach(x -> abm.put(x.getKey(), med(x.getValue())));
                Messung m = new Messung(name, delta, med(aus), med(an), abm);
                out.add(m);
                StringBuilder sb = new StringBuilder();
                int k = 0;
                for (Map.Entry<String, Double> x : abm.entrySet()) {
                    if (k++ >= 5 || Math.abs(x.getValue()) < 0.05) break;
                    sb.append(sb.length() > 0 ? ", " : "").append(f("%s %+.2f", x.getKey(), x.getValue()));
                }
                StringBuilder rd = new StringBuilder();
                for (double d : delta) rd.append(rd.length() > 0 ? " / " : "").append(f("%+.2f", d));
                A.info(was + ": " + name, f("aus %.2f ms, an %.2f ms; Unterschied Median %+.2f ms, Streuung %.2f ms (Runden %s); Abschnitte (Median der Unterschiede, ms): %s",
                        m.aus, m.an, m.median(), m.streuung(), rd, sb.length() > 0 ? sb : "alle unter 0,05"));
            }
        } finally {
            if (b != null) b.close();
        }
        return out;
    }

    // ------------------------------------------------------------------ Teil nacht

    record Paar(String text, float[] aus, float[] an) { }

    static void nacht(Szenen sz) throws Exception {
        A.teil("Nachthimmel (A36; Prüfsteine 6, 7, 9)");
        List<Paar> paare = new ArrayList<>();
        double schlecht = Double.POSITIVE_INFINITY;
        String schlechtName = "";
        int gemessen = 0;
        for (String[] fall : new String[][] {{"xaver", nachtXaver, ZEIT_XAVER}, {"ruhig", nachtRuhig, ZEIT_RUHIG}}) {
            Szenario s = szenario(sz, fall[0]);
            if (s == null) { A.info("Szenario " + fall[0], "fehlt"); continue; }
            try (Buehne b = Buehne.ohneFenster(sz.gebiet(), BREITE, HOEHE)) {
                b.oeffne(s);
                double t = zeit(b, fall[2]);
                if (fall[0].equals("xaver")) bitgleichNacht(b, sz, t);
                for (String an : fall[1].split(",")) {
                    Blick bl = blick(b, sz, an.strip(), t);
                    if (bl == null) { A.info("Ansicht " + an, "nicht in der Szenendatei"); continue; }
                    String name = name(sz, an.strip(), fall[2]);
                    float[][] bild = new float[2][];
                    double[] v = new double[2];
                    for (int k = 0; k < 2; k++) {
                        b.nachtStreu(k == 1);
                        b.belichtung().sofort();
                        b.standbild(bl, t);
                        b.standbild(bl, t);
                        bild[k] = b.standbild(bl, t).clone();
                        v[k] = verhaeltnis(bild[k], bl);
                    }
                    b.nachtStreu(false);
                    paare.add(new Paar(name, bild[0], bild[1]));
                    A.info("Nacht: " + name, Double.isNaN(v[1]) ? "Band 10–20° nicht im Bild" : f("Helligkeit 0,3–5° über der Kimm gegen 10–20°: aus %.2f, an %.2f", v[0], v[1]));
                    if (!Double.isNaN(v[1])) {
                        gemessen++;
                        if (v[1] < schlecht) { schlecht = v[1]; schlechtName = name; }
                    }
                }
                if (fall[0].equals("ruhig")) tagBitgleich(b, sz);
            }
        }
        A.pruefe(gemessen > 0 && schlecht >= 0.5, "Prüfstein 7: Nacht mit Streulicht, Helligkeit 0,3–5° über der Kimm gegen 10–20°, kleinste über " + gemessen + " Ansichten",
                f("%.2f (%s)", schlecht, schlechtName), "≥ 0,5");
        if (!paare.isEmpty()) statusBild(paare);
    }

    /**
     * Median der Leuchtdichte (Rec. 709 aus dem Standbild) über die Zeilen 0,3–5° über der Waagrechten gegen 10–20°; die
     * Höhe je Zeile aus dem Blick (senkrechter Bildwinkel, Neigung). NaN, wenn eins der Bänder nicht im Bild ist.
     */
    static double verhaeltnis(float[] p, Blick bl) {
        double[][] bas = bl.basis();
        double tn = Math.tan(Math.toRadians(bl.bildwinkel) / 2);
        List<Double> unten = new ArrayList<>(), oben = new ArrayList<>();
        for (int y = 0; y < HOEHE; y++) {
            double ny = (1 - 2 * (y + 0.5) / HOEHE) * tn;
            double dy = bas[2][1] + ny * bas[1][1], dl = Math.sqrt(sq(bas[2][0] + ny * bas[1][0]) + sq(dy) + sq(bas[2][2] + ny * bas[1][2]));
            double h = Math.toDegrees(Math.asin(dy / dl));
            List<Double> ziel = h >= 0.3 && h <= 5 ? unten : h >= 10 && h <= 20 ? oben : null;
            if (ziel == null) continue;
            for (int x = 0; x < BREITE; x++) {
                int k = 4 * (y * BREITE + x);
                ziel.add(0.2126 * p[k] + 0.7152 * p[k + 1] + 0.0722 * p[k + 2]);
            }
        }
        if (unten.size() < BREITE || oben.size() < BREITE) return Double.NaN;
        return medL(unten) / Math.max(1e-9, medL(oben));
    }

    static double sq(double x) { return x * x; }

    static double medL(List<Double> l) {
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return med(a);
    }

    /** Prüfstein 6: Streulicht aus, an, aus – das erste und das letzte Bild bitgleich (feste Belichtung). */
    static void bitgleichNacht(Buehne b, Szenen sz, double t) throws Exception {
        Blick bl = blick(b, sz, "landunter_hanswarft", t);
        if (bl == null) return;
        var bel = b.belichtung();
        double min = bel.evMin, max = bel.evMax;
        b.nachtStreu(false);
        bel.sofort();
        b.standbild(bl, t);
        double ev = bel.ev();
        bel.evMin = bel.evMax = ev;
        bel.setze(ev);
        float[] a = b.standbild(bl, t).clone();
        b.nachtStreu(true);
        bel.setze(ev);
        b.standbild(bl, t);
        bel.setze(ev);
        float[] m = b.standbild(bl, t).clone();
        b.nachtStreu(false);
        bel.setze(ev);
        b.standbild(bl, t);
        bel.setze(ev);
        float[] e = b.standbild(bl, t);
        int gleich = 0, anders = 0;
        for (int i = 0; i < e.length; i++) {
            if (Float.floatToRawIntBits(a[i]) == Float.floatToRawIntBits(e[i])) gleich++;
            if (Float.floatToRawIntBits(a[i]) != Float.floatToRawIntBits(m[i])) anders++;
        }
        bel.evMin = min;
        bel.evMax = max;
        A.pruefe(gleich == e.length, "Prüfstein 6: Streulicht aus – an – aus, Hanswarft im Landunter 6.12. 04:00 MEZ, erstes gegen letztes Bild", f("%d von %d Werten gleich (an: %d anders)", gleich, e.length, anders), "bitgleich");
    }

    /** Prüfstein 7, zweiter Teil: bei Tag Streulicht an gegen aus bitgleich (feste Belichtung). */
    static void tagBitgleich(Buehne b, Szenen sz) throws Exception {
        double t = zeit(b, TAG_RUHIG);
        Blick bl = blick(b, sz, "westspitze", t);
        if (bl == null) return;
        var bel = b.belichtung();
        double min = bel.evMin, max = bel.evMax;
        b.nachtStreu(false);
        bel.sofort();
        b.standbild(bl, t);
        double ev = bel.ev();
        bel.evMin = bel.evMax = ev;
        bel.setze(ev);
        b.standbild(bl, t);
        bel.setze(ev);
        float[] aus = b.standbild(bl, t).clone();
        b.nachtStreu(true);
        bel.setze(ev);
        b.standbild(bl, t);
        bel.setze(ev);
        float[] an = b.standbild(bl, t);
        int gleich = 0;
        for (int i = 0; i < an.length; i++) if (Float.floatToRawIntBits(an[i]) == Float.floatToRawIntBits(aus[i])) gleich++;
        b.nachtStreu(false);
        bel.evMin = min;
        bel.evMax = max;
        A.pruefe(gleich == an.length, "Prüfstein 7: bei Tag (Westspitze 30.10. 11:00 MEZ) Streulicht an gegen aus", f("%d von %d Werten gleich", gleich, an.length), "bitgleich");
    }

    static BufferedImage bild(float[] p) {
        BufferedImage im = new BufferedImage(BREITE, HOEHE, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < BREITE * HOEHE; k++) {
            int r = Math.min(255, Math.max(0, Math.round(255 * p[4 * k]))), g = Math.min(255, Math.max(0, Math.round(255 * p[4 * k + 1]))),
                    bl = Math.min(255, Math.max(0, Math.round(255 * p[4 * k + 2])));
            im.setRGB(k % BREITE, k / BREITE, r << 16 | g << 8 | bl);
        }
        return im;
    }

    static void statusBild(List<Paar> paare) throws Exception {
        int w = BREITE / 2, h = HOEHE / 2;
        BufferedImage alle = new BufferedImage(2 * w, paare.size() * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = alle.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        for (int i = 0; i < paare.size(); i++) {
            Paar p = paare.get(i);
            for (int k = 0; k < 2; k++) {
                BufferedImage bi = bild(k == 0 ? p.aus : p.an);
                int x = k * w, y = i * h;
                g.drawImage(bi, x, y, w, h, null);
                String text = p.text + (k == 0 ? " – ohne Streulicht (bis P13c)" : " – mit Streulicht (P13d)");
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect(x + 4, y + 4, g.getFontMetrics().stringWidth(text) + 8, 18);
                g.setColor(Color.WHITE);
                g.drawString(text, x + 8, y + 17);
                if (k == 1) ImageIO.write(bi, "png", d.resolve(f("P13d_Nacht_%d.png", i + 1)).toFile());
            }
        }
        g.dispose();
        File png = d.resolve("P13d_Nacht.png").toFile();
        ImageIO.write(alle, "png", png);
        A.info("Bilder zur Sichtprüfung (Prüfstein 9)", png.getPath() + " (links ohne, rechts mit Streulicht) und P13d_Nacht_1 … " + paare.size() + ".png");
    }
}
