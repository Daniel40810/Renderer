package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Flotte;
import com.dan.lauf.Kamera;
import com.dan.lauf.Schwimmerblick;
import com.dan.lauf.Szenen;
import com.dan.render.water.Wasserbild;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static com.dan.core.pruef.Attest.f;

/**
 * S9a Leistung (Seegang Werkbuch 106, SF164 A, SF168 A): Wohin geht die Zeit eines Bilds in Hoch? Je Ansicht der Abnahme
 * ({@link SeegangAbnahme#FAELLE}, 1920 × 1080) das ganze Bild und das Netz des Wassers, dann je ein Teil der Schattierung
 * aus ({@link Wasserbild#mess} und die Schalter des Wasserbilds) und je ein Bildteil aus (Brandung, Gestalt, Bewuchs,
 * Kimmung). Seit dem ersten Versuch in L7a (die Zeit driftete in der ersten Ansicht von 55 auf 37 ms, der Schwimmerblick
 * ändert sich mit jedem Bild) misst sie an einem <b>stehenden Bild</b>: nach dem Vorlauf (Fläche und Körperfläche bereit,
 * dann {@link #LAUF} Bilder laufend) bleiben Zeit und Blick fest, und je Schalter wechseln sich {@link #RUNDEN} Blöcke ohne
 * und mit Schalter zu je {@link #BLOCK} Bildern ab. Der Preis ist der Median ohne minus der Median mit (je Bild das ganze
 * Bild; das Netz als Mittel der Blöcke). So hebt sich eine langsame Drift heraus.
 * <ul>
 * <li><b>Kalt gegen warm:</b> die erste Ansicht am Anfang und noch einmal am Ende, nach der ganzen Messung unter Last.</li>
 * <li><b>Benannt (Prüfstein 43):</b> die Summe der Preise der Teile, die einander nicht enthalten, gegen die Zeit des Netzes;
 * der Rest sind Fuß, η des Bilds, Rasterung und was kein Schalter trifft.</li>
 * <li><b>A30:</b> im Schwimmerblick je Bild die Zeit unter Wasser und der Linse über {@link #SCHWIMMER_BILDER} Bilder.</li>
 * <li><b>Ausgabe:</b> Attest {@code docs/status/S9a_Leistung.md}, alle Zahlen in {@code docs/status/S9a_Leistung.csv}.</li>
 * </ul>
 * Seit S9b (Seegang Werkbuch 107, {@code --satz s9b}, der Vorgabe): statt aller Teile die neuen Wege gegen die alten
 * (Spiegelung mit grober Tiefe, Kräusel mit Abbruch, Linse in Kacheln: Ersparnis am stehenden Bild) und Prüfstein 45/46 je
 * Ansicht (das Bild mit den neuen Wegen gegen die alten: ΔE₀₀ und gleiche Bildpunkte); dazu S9b-0: je Abschnitt laufend gegen
 * stehend, die stockenden Bilder (über dem Doppelten des Medians) mit ihren teuersten Abschnitten, und der Faden der Flotte je
 * Bild gegen die Bildzeit (SF174). Ausgabe {@code docs/status/S9b_Leistung.md} und {@code .csv}.
 * <pre>  LeistungCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--satz s9a|s9b]</pre>
 */
public final class LeistungCheck {
    static Attest A = new Attest("Seegang: Leistung, Messung je Teil (S9a)", "S9a");
    /** Seit S9b: welcher Satz von Schaltern (s9a alle Teile, s9b die neuen Wege gegen die alten). */
    static String satz = "s9b";
    static final int BASIS_VOR = 20, BASIS = 60, SCHWIMMER_BILDER = 600, BEREIT_MAX = 1200, LAUF = 60, BLOCK = 10, RUNDEN = 3, STEHEND = 60;
    /** Prüfstein 43: so viel der Zeit des Netzes soll benannt sein [G]. */
    static final double BENANNT = 0.8;
    static final String NETZ = "Wasser, Netz";

    /** Ein Schalter: Name, aus, wieder an; {@code teil} = zählt zur Summe des Benannten (enthält keinen anderen). */
    record Schalter(String name, boolean teil, Consumer<Buehne> aus, Consumer<Buehne> an) { }

    /** Seit S9b: die neuen Wege gegen die alten; „aus“ heißt hier: der alte Weg. */
    static List<Schalter> schalterS9b() {
        List<Schalter> l = new ArrayList<>();
        l.add(new Schalter("Spiegelung: alter Weg", false, b -> b.wasserbild().spiegelGrob = false, b -> b.wasserbild().spiegelGrob = true));
        l.add(new Schalter("Kräusel: alter Weg", false, b -> b.wasserbild().kraeuselAbbruch = false, b -> b.wasserbild().kraeuselAbbruch = true));
        l.add(new Schalter("Linse: alter Weg", false, b -> { if (b.linsenbild() != null) b.linsenbild().rechtecke = false; },
                b -> { if (b.linsenbild() != null) b.linsenbild().rechtecke = true; }));
        l.add(new Schalter("Spiegelung des Geländes", false, b -> b.wasserbild().spiegeln = false, b -> b.wasserbild().spiegeln = true));
        l.add(new Schalter("Kräuseln", false, b -> b.wasserbild().kraeuseln = false, b -> b.wasserbild().kraeuseln = true));
        return l;
    }

    /** Seit S9c-1 (Seegang Werkbuch 108): die Spiegelung in halber Auflösung gegen voll, dazu was von ihr bleibt. */
    static List<Schalter> schalterS9c() {
        List<Schalter> l = new ArrayList<>();
        l.add(new Schalter("Spiegelung voll: alter Weg", false, b -> b.wasserbild().spiegelHalb = false, b -> b.wasserbild().spiegelHalb = true));
        l.add(new Schalter("Spiegelung des Geländes", false, b -> b.wasserbild().spiegeln = false, b -> b.wasserbild().spiegeln = true));
        l.add(new Schalter("Kräuseln", false, b -> b.wasserbild().kraeuseln = false, b -> b.wasserbild().kraeuseln = true));
        return l;
    }

    /** Seit S9b: alle neuen Wege aus (der Stand von S9a) oder wieder an. */
    static void alteWege(Buehne b, boolean alt) {
        b.wasserbild().spiegelGrob = !alt;
        b.wasserbild().kraeuselAbbruch = !alt;
        if (b.linsenbild() != null) b.linsenbild().rechtecke = !alt;
    }

    static List<Schalter> schalter() {
        List<Schalter> l = new ArrayList<>();
        String[] bits = {"Clipmap (Normalen, Aufgelöstes)", "Weißwasser", "Schaumköpfe", "Schaumfeld", "Muster des Schaums", "Auflauf im Netz",
                "Werte des Seegangs", "Suche B47", "Säule", "Schatten der Häuser"};
        boolean[] teil = {true, true, true, true, false, true, false, true, true, true};
        for (int i = 0; i < bits.length; i++) {
            int bit = 1 << i;
            l.add(new Schalter(bits[i], teil[i], b -> b.wasserbild().mess = bit, b -> b.wasserbild().mess = 0));
        }
        l.add(new Schalter("Kräuseln", true, b -> b.wasserbild().kraeuseln = false, b -> b.wasserbild().kraeuseln = true));
        l.add(new Schalter("Spiegelung des Geländes", true, b -> b.wasserbild().spiegeln = false, b -> b.wasserbild().spiegeln = true));
        l.add(new Schalter("Trübung am Ort", true, b -> b.wasserbild().truebung = false, b -> b.wasserbild().truebung = true));
        l.add(new Schalter("Katzenpfoten", true, b -> b.wasserbild().katzen = false, b -> b.wasserbild().katzen = true));
        l.add(new Schalter("wirksame Fläche (P7g)", true, b -> b.wasserbild().wirksam = false, b -> b.wasserbild().wirksam = true));
        l.add(new Schalter("Brandung im Wasser (P13c)", false, b -> b.wasserbild().brandung = false, b -> b.wasserbild().brandung = true));
        l.add(new Schalter("Stufe 2 (Seegang im Wasser)", false, b -> b.wasserbild().seegang = false, b -> b.wasserbild().seegang = true));
        l.add(new Schalter("Netz (nur der alte Weg)", false, b -> b.wasserbild().netz = false, b -> b.wasserbild().netz = true));
        l.add(new Schalter("Bildteil Brandung", false, b -> b.brandung(false), b -> b.brandung(true)));
        l.add(new Schalter("Bildteil Gestalt", false, b -> b.gestalt(false), b -> b.gestalt(true)));
        l.add(new Schalter("Bildteil Bewuchs", false, b -> b.bewuchs(false), b -> b.bewuchs(true)));
        l.add(new Schalter("Bildteil Kimmung", false, b -> b.kimmung.an = false, b -> b.kimmung.an = true));
        return l;
    }

    public static void main(String[] args) {
        File szenen = null;
        int breite = 1920, hoehe = 1080, pause = 0;
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                case "--groesse" -> { String[] g = args[++i].split("x"); breite = Integer.parseInt(g[0]); hoehe = Integer.parseInt(g[1]); }
                case "--pause" -> pause = Integer.parseInt(args[++i]);
                case "--satz" -> satz = args[++i];
                default -> { }
            }
        if (satz.equals("s9b")) A = new Attest("Seegang: Leistung, neue Wege gegen alte (S9b)", "S9b");
        if (satz.equals("s9c")) A = new Attest("Seegang: Leistung, Spiegelung in halber Auflösung (S9c)", "S9c");
        System.out.println("LeistungCheck – " + satz.toUpperCase() + " (Seegang Werkbuch 106/107)");
        if (pause > 0) {                                                       // die Karte kühlt ab (kalt gegen warm)
            System.out.printf("Pause %d s ...%n", pause);
            try { Thread.sleep(1000L * pause); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        long t0 = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            alles(Szenen.lies(szenen), breite, hoehe);
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        Path pa = satz.equals("s9c") ? A.schreibe(Path.of("docs", "status", "S9c_Leistung.md"), "S9c – Leistung: Spiegelung in halber Auflösung")
                : satz.equals("s9b") ? A.schreibe(Path.of("docs", "status", "S9b_Leistung.md"), "S9b – Leistung: neue Wege gegen alte, Lücke laufend gegen stehend")
                : A.schreibe(Path.of("docs", "status", "S9a_Leistung.md"), "S9a – Leistung: Messung je Teil der Schattierung");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Eine eingerichtete Ansicht: Blick, Kamera an Bord, Wart, Uhr. */
    static final class Fall {
        final String[] fa;
        Blick bl0;
        Kamera k;
        Flaechenwart w;
        double[] zt;
        double dt = 1.0 / 30;
        long uhrNs;
        /** Das stehende Bild: Blick und Zeit nach dem Vorlauf. */
        Blick fest;
        double tFest;

        Fall(String[] fa) { this.fa = fa; }
    }

    /** Messung über n Bilder nach vor Bildern: Median ganzes Bild, Median Netz, Mittel je Abschnitt, je Bild die Abschnitte. */
    record Messung(double ganz, double netz, Map<String, Double> mittel, List<Map<String, Double>> jeBild, List<Double> ganzJe, double koerper, double faden,
                   List<Double> wandJe, List<Double> fadenJe) { }

    static void alles(Szenen sz, int breite, int hoehe) throws Exception {
        Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
        A.info("Gerät", b.geraet().name + f(", %d × %d", breite, hoehe));
        boolean s9c = satz.equals("s9c"), s9b = satz.equals("s9b") || s9c;
        List<Schalter> sl = s9c ? schalterS9c() : s9b ? schalterS9b() : schalter();
        Path csv = Path.of("docs", "status", s9c ? "S9c_Leistung.csv" : s9b ? "S9b_Leistung.csv" : "S9a_Leistung.csv");
        List<double[]> faden = new ArrayList<>();
        List<double[]> gleich = new ArrayList<>();
        List<String> gleichWo = new ArrayList<>();
        Files.createDirectories(csv.getParent());
        List<double[]> benannt = new ArrayList<>();
        List<double[]> a30 = new ArrayList<>();
        double[] kalt = null;
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            out.println("szenario;ansicht;schalter;ganz_ms;netz_ms;d_ganz_ms;d_netz_ms;teuerste");
            String offen = null;
            for (int q = 0; q < SeegangAbnahme.FAELLE.length; q++) {
                String[] fa = SeegangAbnahme.FAELLE[q];
                A.teil(fa[0] + ", " + fa[1]);
                if (!fa[0].equals(offen)) {
                    SchwimmerCheck.pegel(b, sz);
                    b.oeffne(SchaumCheck.szenario(sz, fa[0]));
                    b.wasserbild().seegang = true;
                    offen = fa[0];
                }
                Fall c = einrichten(b, sz, fa, breite, hoehe);
                Messung m1 = messe(b, c, BASIS_VOR, BASIS);                     // laufend: Karte, Faden, das Bild in Bewegung
                Map<String, Double> stAb = new TreeMap<>();
                double[] st0 = stehendesBild(b, c, STEHEND, stAb);
                if (q == 0) kalt = st0;
                A.info("laufend: ganzes Bild, Netz (Median)", f("%.2f ms; %s %.2f ms", m1.ganz(), NETZ, m1.netz()));
                A.info("stehend: ganzes Bild, Netz", f("%.2f ms; %s %.2f ms", st0[0], NETZ, st0[1]));
                A.info("teuerste Abschnitte, laufend (ms)", teuerste(m1.mittel(), 8));
                A.info("A30 Karte und Faden (laufend)", f("see_koerper %.3f ms (Median); Faden %.2f ms je Teilschritt (Mittel)", m1.koerper(), m1.faden()));
                a30.add(new double[] {m1.koerper(), m1.faden()});
                if (s9b) {
                    // S9b-0: wo ist das laufende Bild teurer als das stehende? Stockende Bilder; der Faden je Bild (SF174)
                    A.info("S9b-0 laufend gegen stehend, je Abschnitt (ms, größte Unterschiede)", luecke(m1.mittel(), stAb, 8)
                            + f("; Summe laufend %.2f, stehend %.2f", m1.ganz(), st0[0]));
                    A.info("S9b-0 stockende Bilder (laufend, über 2 × Median)", stocken(m1.ganzJe(), m1.jeBild(), m1.wandJe(), 5));
                    if (!m1.fadenJe().isEmpty()) {
                        double fm = SchaumCheck.median(m1.fadenJe()), f99 = quantil(m1.fadenJe(), 0.99);
                        faden.add(new double[] {fm, f99, m1.ganz()});
                        A.info("S9b-0 Faden der Flotte je Bild (SF174)", f("Median %.2f ms, 99 %% %.2f ms, höchstens %.2f ms; Bild (Karte, Median) %.2f ms",
                                fm, f99, quantil(m1.fadenJe(), 1.0), m1.ganz()));
                    }
                    // Prüfstein 45/46: dasselbe stehende Bild mit den neuen und den alten Wegen
                    double[] v = s9c ? vergleichHalb(b, c, fa) : vergleich(b, c);
                    gleich.add(v);
                    gleichWo.add(fa[0] + ", " + fa[1]);
                    A.info(s9c ? "Prüfstein 45: Spiegelung halb gegen voll, stehendes Bild, feste Belichtung (Bildpunkte gleich; zweimal halb)" : "Prüfstein 45/46: neue Wege gegen alte, stehendes Bild", f("ΔE₀₀ Mittel %.3f, 95 %% %.3f, größter %.3f; Bildpunkte gleich %.3f %%; "
                            + "zweimal neu: %d Bildpunkte anders", v[0], v[1], v[2], 100 * v[3], (long) v[4]));
                    out.printf(java.util.Locale.ROOT, "%s;%s;%s;%.3f;%.3f;%.3f;%.3f;%n", fa[0], fa[1], "vergleich_dE_p95_max_gleich", v[1], v[2], v[3], v[4]);
                }
                out.printf(java.util.Locale.ROOT, "%s;%s;%s;%.3f;%.3f;%.3f;%.3f;%s%n", fa[0], fa[1], "laufend", m1.ganz(), m1.netz(), 0.0, 0.0, teuerste(m1.mittel(), 8).replace(';', ','));
                out.printf(java.util.Locale.ROOT, "%s;%s;%s;%.3f;%.3f;%.3f;%.3f;%n", fa[0], fa[1], "stehend", st0[0], st0[1], 0.0, 0.0);
                StringBuilder sb = new StringBuilder();
                double summe = 0;
                for (Schalter s : sl) {
                    double[] p = preis(b, c, s);
                    if (s9b && s.name().endsWith("alter Weg"))
                        sb.append(f("%s%s: Ersparnis %.2f (Netz %.2f)", sb.length() > 0 ? "; " : "", s.name(), -p[0], -p[1]));
                    else sb.append(f("%s%s %.2f (Netz %.2f)", sb.length() > 0 ? "; " : "", s.name(), p[0], p[1]));
                    if (s.teil()) summe += Math.max(0, p[1]);
                    out.printf(java.util.Locale.ROOT, "%s;%s;%s;%.3f;%.3f;%.3f;%.3f;%n", fa[0], fa[1], s.name(), p[2], p[3], p[0], p[1]);
                }
                double n0 = st0[1];
                A.info("Preis je Teil (ms, ganzes Bild und Netz; stehendes Bild)", sb.toString());
                if (!s9b) {
                    A.info("benannt im Netz", f("%.2f von %.2f ms (%.0f %%)", summe, n0, 100 * summe / Math.max(1e-9, n0)));
                    benannt.add(new double[] {summe, n0});
                }
                if (c.k instanceof Schwimmerblick sbl) schwimmer(b, c, sbl);
                if (c.k != null) b.bordKamera(null);
                out.flush();
            }
            // warm: die erste Ansicht noch einmal
            String[] fa = SeegangAbnahme.FAELLE[0];
            A.teil("Kalt gegen warm: " + fa[0] + ", " + fa[1]);
            SchwimmerCheck.pegel(b, sz);
            b.oeffne(SchaumCheck.szenario(sz, fa[0]));
            b.wasserbild().seegang = true;
            Fall c = einrichten(b, sz, fa, breite, hoehe);
            messe(b, c, BASIS_VOR, BASIS);
            double[] warm = stehend(b, c);
            if (kalt != null)
                A.info("kalt gegen warm (stehendes Bild)", f("ganzes Bild %.2f → %.2f ms (×%.3f); %s %.2f → %.2f ms (×%.3f)", kalt[0], warm[0], warm[0] / kalt[0],
                        NETZ, kalt[1], warm[1], warm[1] / Math.max(1e-9, kalt[1])));
            out.printf(java.util.Locale.ROOT, "%s;%s;%s;%.3f;%.3f;%.3f;%.3f;%n", fa[0], fa[1], "warm", warm[0], warm[1],
                    kalt != null ? warm[0] - kalt[0] : 0, kalt != null ? warm[1] - kalt[1] : 0);
        } finally {
            b.close();
        }
        A.teil("Urteil");
        List<Double> ko = new ArrayList<>(), fa = new ArrayList<>();
        for (double[] v : a30) { if (!Double.isNaN(v[0])) ko.add(v[0]); if (!Double.isNaN(v[1])) fa.add(v[1]); }
        A.info("A30 über alle Messungen", f("see_koerper %d Messungen: Median %.3f, höchstens %.3f ms; Faden %d Messungen: Median %.2f, höchstens %.2f ms je Teilschritt",
                ko.size(), ko.isEmpty() ? Double.NaN : SchaumCheck.median(ko), ko.isEmpty() ? Double.NaN : quantil(ko, 1.0),
                fa.size(), fa.isEmpty() ? Double.NaN : SchaumCheck.median(fa), fa.isEmpty() ? Double.NaN : quantil(fa, 1.0)));
        if (s9b) {
            // Prüfstein 45: ΔE₀₀ < 1 (größter je Ansicht); 46: mindestens 99 % der Bildpunkte gleich (dieselben Treffer)
            double dMax = 0, gMin = 1;
            String dWo = "", gWo = "";
            for (int i = 0; i < gleich.size(); i++) {
                if (gleich.get(i)[2] > dMax) { dMax = gleich.get(i)[2]; dWo = gleichWo.get(i); }
                if (gleich.get(i)[3] < gMin) { gMin = gleich.get(i)[3]; gWo = gleichWo.get(i); }
            }
            if (s9c) {
                double mMax = 0;
                String mWo = "";
                for (int i = 0; i < gleich.size(); i++) if (gleich.get(i)[0] > mMax) { mMax = gleich.get(i)[0]; mWo = gleichWo.get(i); }
                A.pruefe(mMax < 1, "Prüfstein 45: Spiegelung halb gegen voll, ΔE₀₀ im Mittel (feste Belichtung; größtes Mittel)", f("%.3f (%s); größter Einzelwert %.1f (%s)", mMax, mWo, dMax, dWo), "< 1");
            } else {
                A.pruefe(dMax < 1, "Prüfstein 45: neue Wege gegen alte, Farbabstand (größter über alle Ansichten)", f("ΔE₀₀ %.3f (%s)", dMax, dWo), "< 1");
                A.pruefe(gMin >= 0.99, "Prüfstein 46: neue Wege gegen alte, gleiche Bildpunkte (kleinster Anteil)", f("%.3f %% (%s)", 100 * gMin, gWo), "≥ 99 %");
            }
            double fq = 0;
            String fWo = "";
            for (int i = 0; i < faden.size(); i++) if (faden.get(i)[1] / faden.get(i)[2] > fq) { fq = faden.get(i)[1] / faden.get(i)[2]; fWo = f("%.2f gegen %.2f ms", faden.get(i)[1], faden.get(i)[2]); }
            A.pruefe(faden.isEmpty() || fq < 1, "SF174: der Faden der Flotte bleibt unter der Bildzeit (99 % je Bild gegen den Median des Bilds; höchster Anteil)",
                    faden.isEmpty() ? "keine Flotte" : f("%.0f %% (%s)", 100 * fq, fWo), "< 100 %");
            return;
        }
        List<Double> anteil = new ArrayList<>();
        for (double[] v : benannt) anteil.add(v[0] / Math.max(1e-9, v[1]));
        double med = SchaumCheck.median(anteil);
        A.pruefe(med >= BENANNT, "Prüfstein 43: die Teile der Schattierung erklären die Zeit des Netzes (Median über die Ansichten)",
                f("%.0f %% (je Ansicht %s)", 100 * med, liste(anteil)), f("≥ %.0f %% [G]", 100 * BENANNT));
    }

    static String liste(List<Double> l) {
        StringBuilder sb = new StringBuilder();
        for (double v : l) sb.append(f("%s%.0f %%", sb.length() > 0 ? ", " : "", 100 * v));
        return sb.toString();
    }

    static String teuerste(Map<String, Double> m, int n) {
        List<Map.Entry<String, Double>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, l.size()); i++) sb.append(f("%s%s %.2f", i > 0 ? "; " : "", l.get(i).getKey(), l.get(i).getValue()));
        return sb.toString();
    }

    /** Wie {@code SeegangAbnahme.fall} bis zum Vorlauf: Ansicht, Hoch, Standbild, Flotte, Kamera an Bord, Fläche bereit. */
    static Fall einrichten(Buehne b, Szenen sz, String[] fa, int breite, int hoehe) throws Exception {
        Fall c = new Fall(fa);
        Ansicht an = sz.ansicht(fa[1]);
        double t = (Instant.parse(fa[2]).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        c.bl0 = new Blick();
        an.setze(c.bl0, b.gelaende, b.utm(), b.uhr().bei(t));
        c.bl0.groesse(breite, hoehe);
        if (an.bord == null) {
            double[] wa = b.wasserUndSeegang(c.bl0.ost, c.bl0.nord);
            Ansicht.hebe(c.bl0, wa[0], wa[1], an.ueberWasser);
        }
        SeegangAbnahme.stufe(b, SeegangAbnahme.Stufe.HOCH);
        b.wasserbild().mess = 0;
        b.standbild(c.bl0, t);
        Flotte fl = b.flotte();
        if (fl != null) fl.neuSetzen();
        c.k = an.bord != null ? b.bordKamera(an) : null;
        c.w = b.wart();
        c.zt = new double[] {t};
        int i = 0;
        for (; i < BEREIT_MAX; i++) {
            SeegangAbnahme.bild(b, c.w, c.k, c.bl0, c.zt, c.dt, c.uhrNs);
            c.uhrNs += Math.round(c.dt * 1e9);
            if (i > 60 && c.w.bereit() && !c.w.wechselt() && (c.w.koerpermitte() == null || c.w.koerperBereit())) break;
        }
        for (int j = 0; j < LAUF; j++) {                                        // seit dem ersten Versuch: noch eine Weile laufen
            SeegangAbnahme.bild(b, c.w, c.k, c.bl0, c.zt, c.dt, c.uhrNs);
            c.uhrNs += Math.round(c.dt * 1e9);
        }
        A.info("Vorlauf", f("Fläche %s nach %d Bildern, Körperfläche %s, dann %d Bilder%s", c.w.bereit() ? "bereit" : "NICHT bereit", i,
                c.w.koerpermitte() == null ? "keine" : c.w.koerperBereit() ? "bereit" : "NICHT bereit", LAUF, c.k != null ? ", an Bord (" + c.k.getClass().getSimpleName() + ")" : ""));
        return c;
    }

    /** Hält Blick und Zeit des nächsten Bilds fest (mit der Kamera an Bord, wie sie jetzt steht). */
    static void festhalten(Fall c) {
        Blick bl = c.k != null ? c.k.weiter(c.dt, c.zt[0], c.bl0) : null;
        if (bl == null) bl = c.bl0.kopie();
        bl.groesse(c.bl0.breite, c.bl0.hoehe_px);
        c.fest = bl;
        c.tFest = c.zt[0];
    }

    /** Das stehende Bild: {ganzes Bild Median, Netz Mittel} über n Bilder (Blick und Zeit fest). */
    static double[] stehendesBild(Buehne b, Fall c, int n) { return stehendesBild(b, c, n, null); }

    /** Wie oben; seit S9b-0 legt es die Mittel je Abschnitt in {@code ab} (wenn nicht null). */
    static double[] stehendesBild(Buehne b, Fall c, int n, Map<String, Double> ab) {
        if (c.fest == null) festhalten(c);
        Buehne.Bildzeiten z = b.bildzeiten(c.fest, c.tFest, n, true);
        if (ab != null) ab.putAll(z.abschnitte());
        return new double[] {z.karte()[n / 2], z.abschnitte().getOrDefault(NETZ, 0.0)};
    }

    /** S9b-0: die n Abschnitte mit dem größten Unterschied laufend − stehend (ms). */
    static String luecke(Map<String, Double> lauf, Map<String, Double> steh, int n) {
        Map<String, Double> d = new TreeMap<>();
        for (Map.Entry<String, Double> e : lauf.entrySet()) d.put(e.getKey(), e.getValue() - steh.getOrDefault(e.getKey(), 0.0));
        for (Map.Entry<String, Double> e : steh.entrySet()) d.putIfAbsent(e.getKey(), -e.getValue());
        List<Map.Entry<String, Double>> l = new ArrayList<>(d.entrySet());
        l.sort((x, y) -> Double.compare(Math.abs(y.getValue()), Math.abs(x.getValue())));
        StringBuilder sb = new StringBuilder();
        double summe = 0;
        for (Map.Entry<String, Double> e : l) summe += e.getValue();
        for (int i = 0; i < Math.min(n, l.size()); i++)
            sb.append(f("%s%s %+.2f%s", i > 0 ? "; " : "", l.get(i).getKey(), l.get(i).getValue(), steh.containsKey(l.get(i).getKey()) ? "" : " (nur laufend)"));
        return sb + f("; Summe aller Unterschiede %+.2f", summe);
    }

    /** S9b-0: Bilder über dem Doppelten des Medians (Karte), die schlimmsten n mit ihren drei teuersten Abschnitten. */
    static String stocken(List<Double> ganz, List<Map<String, Double>> jeBild, List<Double> wand, int n) {
        if (ganz.isEmpty()) return "keine Bilder";
        double med = SchaumCheck.median(ganz), wMed = wand.isEmpty() ? Double.NaN : SchaumCheck.median(wand);
        List<Integer> st = new ArrayList<>();
        int wandSt = 0;
        for (int i = 0; i < ganz.size(); i++) {
            if (ganz.get(i) > 2 * med) st.add(i);
            if (i < wand.size() && wand.get(i) > 2 * wMed) wandSt++;
        }
        st.sort((x, y) -> Double.compare(ganz.get(y), ganz.get(x)));
        StringBuilder sb = new StringBuilder(f("%d von %d Bildern (Karte, Median %.2f ms); Uhr des Prozessors über 2 × Median (%.2f ms): %d", st.size(), ganz.size(), med, wMed, wandSt));
        for (int q = 0; q < Math.min(n, st.size()); q++) {
            int i = st.get(q);
            sb.append(f(" | Bild %d: %.1f ms: %s", i, ganz.get(i), teuerste(jeBild.get(i), 3)));
        }
        return sb.toString();
    }

    /**
     * Prüfstein 45/46 (S9b): das stehende Bild mit den neuen Wegen, mit den alten, noch einmal mit den neuen (Rauschen).
     * {ΔE₀₀ Mittel, 95 %, größter, Anteil gleicher Bildpunkte neu/alt, Bildpunkte anders neu/neu}.
     */
    /**
     * Seit S9c-1: Prüfstein 45 für die Spiegelung in halber Auflösung – das stehende Bild mit fester Belichtung halb, voll,
     * noch einmal halb. {ΔE₀₀ Mittel, 95 %, größter, Anteil gleicher Bildpunkte halb/voll, Bildpunkte anders halb/halb}; für
     * anleger_see und bord_kutter beide Bilder als PNG (Sichtprüfung).
     */
    static double[] vergleichHalb(Buehne b, Fall c, String[] fa) {
        if (c.fest == null) festhalten(c);
        int w = c.bl0.breite, h = c.bl0.hoehe_px;
        b.bildzeiten(c.fest, c.tFest, 30, false);
        double dt0 = b.bildzeitenDt;
        b.bildzeitenDt = 0;
        try {
            b.bildzeiten(c.fest, c.tFest, 3, false);
            float[] halb = b.letztesBild();
            b.wasserbild().spiegelHalb = false;
            float[] voll;
            try {
                b.bildzeiten(c.fest, c.tFest, 3, false);
                voll = b.letztesBild();
            } finally {
                b.wasserbild().spiegelHalb = true;
            }
            b.bildzeiten(c.fest, c.tFest, 3, false);
            float[] halb2 = b.letztesBild();
            long gleich = 0, rauschen = 0, n = (long) w * h;
            for (int i = 0; i < n; i++) {
                boolean g = true, g2 = true;
                for (int k = 0; k < 3; k++) {
                    if (Float.floatToRawIntBits(halb[4 * i + k]) != Float.floatToRawIntBits(voll[4 * i + k])) g = false;
                    if (Float.floatToRawIntBits(halb[4 * i + k]) != Float.floatToRawIntBits(halb2[4 * i + k])) g2 = false;
                }
                if (g) gleich++;
                if (!g2) rauschen++;
            }
            if (fa[1].equals("anleger_see") || fa[1].equals("bord_kutter"))
                try {
                    javax.imageio.ImageIO.write(SeegangBilder.bild(halb, w, h), "png", Path.of("docs", "status", "S9c_" + fa[0] + "_" + fa[1] + "_halb.png").toFile());
                    javax.imageio.ImageIO.write(SeegangBilder.bild(voll, w, h), "png", Path.of("docs", "status", "S9c_" + fa[0] + "_" + fa[1] + "_voll.png").toFile());
                } catch (java.io.IOException e) { throw new RuntimeException(e); }
            com.dan.core.pruef.Bildvergleich.Ergebnis e = com.dan.core.pruef.Bildvergleich.vergleiche(SeegangBilder.bild(halb, w, h), SeegangBilder.bild(voll, w, h), 1.0);
            return new double[] {e.mittel(), e.p95(), e.groesste(), gleich / (double) n, rauschen};
        } finally {
            b.bildzeitenDt = dt0;
        }
    }

    static double[] vergleich(Buehne b, Fall c) {
        if (c.fest == null) festhalten(c);
        int w = c.bl0.breite, h = c.bl0.hoehe_px;
        b.bildzeiten(c.fest, c.tFest, 2, false);
        float[] neu = b.letztesBild();
        alteWege(b, true);
        float[] alt;
        try {
            b.bildzeiten(c.fest, c.tFest, 2, false);
            alt = b.letztesBild();
        } finally {
            alteWege(b, false);
        }
        b.bildzeiten(c.fest, c.tFest, 2, false);
        float[] neu2 = b.letztesBild();
        long gleich = 0, rauschen = 0, n = (long) w * h;
        for (int i = 0; i < n; i++) {
            boolean g = true, g2 = true;
            for (int k = 0; k < 3; k++) {
                if (Float.floatToRawIntBits(neu[4 * i + k]) != Float.floatToRawIntBits(alt[4 * i + k])) g = false;
                if (Float.floatToRawIntBits(neu[4 * i + k]) != Float.floatToRawIntBits(neu2[4 * i + k])) g2 = false;
            }
            if (g) gleich++;
            if (!g2) rauschen++;
        }
        com.dan.core.pruef.Bildvergleich.Ergebnis e = com.dan.core.pruef.Bildvergleich.vergleiche(SeegangBilder.bild(neu, w, h), SeegangBilder.bild(alt, w, h), 1.0);
        return new double[] {e.mittel(), e.p95(), e.groesste(), gleich / (double) n, rauschen};
    }

    /** Für kalt gegen warm: das stehende Bild dieses Falls. */
    static double[] stehend(Buehne b, Fall c) { return stehendesBild(b, c, STEHEND); }

    /**
     * Preis eines Schalters am stehenden Bild: RUNDEN-mal ein Block ohne, ein Block mit Schalter (je BLOCK Bilder, vorher
     * ein Bild zum Einschwingen). {Preis ganzes Bild, Preis Netz, ganzes Bild mit Schalter, Netz mit Schalter}.
     */
    static double[] preis(Buehne b, Fall c, Schalter s) {
        if (c.fest == null) festhalten(c);
        List<Double> ohne = new ArrayList<>(), mit = new ArrayList<>();
        double nOhne = 0, nMit = 0;
        for (int r = 0; r < RUNDEN; r++) {
            b.bildzeiten(c.fest, c.tFest, 1, false);
            Buehne.Bildzeiten z0 = b.bildzeiten(c.fest, c.tFest, BLOCK, true);
            for (double x : z0.karte()) ohne.add(x);
            nOhne += z0.abschnitte().getOrDefault(NETZ, 0.0) / RUNDEN;
            s.aus().accept(b);
            try {
                b.bildzeiten(c.fest, c.tFest, 1, false);
                Buehne.Bildzeiten z1 = b.bildzeiten(c.fest, c.tFest, BLOCK, true);
                for (double x : z1.karte()) mit.add(x);
                nMit += z1.abschnitte().getOrDefault(NETZ, 0.0) / RUNDEN;
            } finally {
                s.an().accept(b);
            }
        }
        double go = SchaumCheck.median(ohne), gm = SchaumCheck.median(mit);
        return new double[] {go - gm, nOhne - nMit, gm, nMit};
    }

    static Messung messe(Buehne b, Fall c, int vor, int n) throws InterruptedException {
        List<Double> ganz = new ArrayList<>(), netz = new ArrayList<>();
        Map<String, Double> mittel = new TreeMap<>();
        List<Map<String, Double>> jeBild = new ArrayList<>();
        List<Double> koerper = new ArrayList<>(), wand = new ArrayList<>(), fadenJe = new ArrayList<>();
        double faden = 0;
        int fadenN = 0;
        Flotte fl = b.flotte();
        for (int j = 0; j < vor + n; j++) {
            Buehne.Bildzeiten z = SeegangAbnahme.bild(b, c.w, c.k, c.bl0, c.zt, c.dt, c.uhrNs);
            c.uhrNs += Math.round(c.dt * 1e9);
            if (j < vor) continue;
            ganz.add(z.karte()[0]);
            wand.add(z.wand()[0]);
            netz.add(z.abschnitte().getOrDefault(NETZ, 0.0));
            if (z.abschnitte().containsKey("see_koerper")) koerper.add(z.abschnitte().get("see_koerper"));
            if (fl != null) {                                                  // A30: der Faden der Flotte je Teilschritt (4 ms)
                fl.warte();
                faden += fl.fadenMs / (c.dt / 0.004);
                fadenJe.add(fl.fadenMs);
                fadenN++;
            }
            jeBild.add(z.abschnitte());
            for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) mittel.merge(e.getKey(), e.getValue() / n, Double::sum);
        }
        return new Messung(SchaumCheck.median(ganz), SchaumCheck.median(netz), mittel, jeBild, ganz, koerper.isEmpty() ? Double.NaN : SchaumCheck.median(koerper), fadenN == 0 ? Double.NaN : faden / fadenN,
                wand, fadenJe);
    }

    /** A30 im Schwimmerblick: je Bild unter Wasser und Linse, über SCHWIMMER_BILDER Bilder. */
    static void schwimmer(Buehne b, Fall c, Schwimmerblick sbl) throws InterruptedException {
        List<Double> uw = new ArrayList<>(), li = new ArrayList<>(), ganzJe = new ArrayList<>(), wandJe = new ArrayList<>();
        List<Map<String, Double>> jeBild = new ArrayList<>();
        int uwN = 0, liN = 0, ueber = 0, linie = 0, unter = 0;
        StringBuilder spitzen = new StringBuilder();
        for (int j = 0; j < SCHWIMMER_BILDER; j++) {
            Buehne.Bildzeiten z = SeegangAbnahme.bild(b, c.w, c.k, c.bl0, c.zt, c.dt, c.uhrNs);
            c.uhrNs += Math.round(c.dt * 1e9);
            ganzJe.add(z.karte()[0]);
            wandJe.add(z.wand()[0]);
            jeBild.add(z.abschnitte());
            double u = 0, l = 0;
            for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) {
                if (e.getKey().startsWith("Unterwasser")) u += e.getValue();
                if (e.getKey().startsWith("Linse")) l += e.getValue();
            }
            String lage = sbl.ganzUnter() ? "unter" : !Double.isNaN(sbl.tiefe) && sbl.tiefe > -0.05 ? "Linie" : "über";
            if (lage.equals("unter")) unter++; else if (lage.equals("Linie")) linie++; else ueber++;
            if (u > 0) { uw.add(u); uwN++; }
            if (l > 0) { li.add(l); liN++; }
            if (u > 1.5 && spitzen.length() < 400) spitzen.append(f("%s%d: %.2f ms (%s, ganzes Bild %.1f ms)", spitzen.length() > 0 ? "; " : "", j, u, lage, z.karte()[0]));
        }
        A.info("A30 Schwimmerblick: Lage der Scheibe", f("%d Bilder: über %d, an der Linie %d, unter %d", SCHWIMMER_BILDER, ueber, linie, unter));
        A.info("A30 unter Wasser je Bild", uwN == 0 ? "nicht gezeichnet" : f("%d Bilder: Median %.3f, 99 %% %.3f, höchstens %.3f ms", uwN, SchaumCheck.median(uw), quantil(uw, 0.99), quantil(uw, 1.0)));
        A.info("A30 unter Wasser über 1,5 ms", spitzen.length() == 0 ? "keine" : spitzen.toString());
        A.info("A30 Linse je Bild", liN == 0 ? "nicht gezeichnet" : f("%d Bilder: Median %.3f, 99 %% %.3f, höchstens %.3f ms", liN, SchaumCheck.median(li), quantil(li, 0.99), quantil(li, 1.0)));
        if (satz.equals("s9b") || satz.equals("s9c")) {
            A.info("S9b-0 stockende Bilder im Schwimmerblick (über 2 × Median)", stocken(ganzJe, jeBild, wandJe, 6));
            com.dan.render.frame.Linse lb = b.linsenbild();
            if (lb != null) A.info("S9b-3 Linse zuletzt", lb.kacheln < 0 ? "ganzes Bild" : f("%d von %d Kacheln", lb.kacheln, com.dan.render.frame.Linse.KX * com.dan.render.frame.Linse.KY));
        }
    }

    static double quantil(List<Double> l, double p) {
        double[] a = l.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        if (a.length == 0) return Double.NaN;
        return a[Math.min(a.length - 1, (int) Math.floor(p * (a.length - 1) + 0.5))];
    }
}
