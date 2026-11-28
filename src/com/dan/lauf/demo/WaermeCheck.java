package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.lauf.Buehne;
import com.dan.lauf.Szenen;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * S9b-5 (Seegang Werkbuch 107): Wird die Karte unter Dauerlast langsamer? In L7b und L7c lief die Abnahme nach 3 min
 * Warmlauf in allen Ansichten rund 20 % langsamer als in L7d ohne Warmlauf. Hier in einer Ansicht (Vorgabe anleger_see):
 * das stehende Bild als Thermometer (dieselbe Arbeit, Zeit und Blick fest), dann je Abschnitt Dauerlast (laufend, ohne
 * Pause) oder Ruhe, danach wieder das Thermometer. Folge: T0, je Block 180 s Last und Thermometer ({@code --bloecke}, Vorgabe 2),
 * dann 120 s Ruhe, Thermometer, 300 s Ruhe, Thermometer.
 * Wird T1/T2 langsamer und T3/T4 wieder schneller, ist es die Wärme der Karte (Takt), nicht die Szene.
 * <pre>  WaermeCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--ansicht 0]</pre>
 */
public final class WaermeCheck {
    static final Attest A = new Attest("Seegang: Karte unter Dauerlast (S9b-5)", "S9b");
    static final int THERMO = 30;

    public static void main(String[] args) {
        File szenen = null;
        int fall = 0, bloecke = 2;
        boolean ruhe = true, schalter = false, sprung = false;
        int versatz = 0;
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                case "--ansicht" -> fall = Integer.parseInt(args[++i]);
                case "--bloecke" -> bloecke = Integer.parseInt(args[++i]);
                case "--ohne-ruhe" -> ruhe = false;
                case "--schalter" -> schalter = true;
                case "--sprung" -> sprung = true;
                case "--versatz" -> versatz = Integer.parseInt(args[++i]);
                default -> { }
            }
        System.out.println("WaermeCheck – S9b-5 (Seegang Werkbuch 107)");
        long t0 = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            if (sprung) sprungTest(Szenen.lies(szenen), fall, versatz);
            else if (schalter) schalterTest(Szenen.lies(szenen), fall, versatz);
            else pruefe(Szenen.lies(szenen), fall, bloecke, ruhe);
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        Path pa = A.schreibe(Path.of("docs", "status", "S9b_Waerme.md"), "S9b-5 – Karte unter Dauerlast");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void pruefe(Szenen sz, int fall, int bloecke, boolean ruhe) throws Exception {
        String[] fa = SeegangAbnahme.FAELLE[fall];
        Buehne b = Buehne.ohneFenster(sz.gebiet(), 1920, 1080);
        A.info("Gerät", b.geraet().name + ", 1920 × 1080");
        try {
            A.teil(fa[0] + ", " + fa[1]);
            SchwimmerCheck.pegel(b, sz);
            b.oeffne(SchaumCheck.szenario(sz, fa[0]));
            b.wasserbild().seegang = true;
            LeistungCheck.Fall c = LeistungCheck.einrichten(b, sz, fa, 1920, 1080);
            LeistungCheck.festhalten(c);
            List<double[]> t = new ArrayList<>();
            List<String> namen = new ArrayList<>();
            t.add(thermo(b, c, "T0 nach dem Vorlauf"));
            namen.add("T0");
            for (int q = 1; q <= bloecke; q++) {
                last(b, c, 180, "Last " + q);
                t.add(thermo(b, c, f("T%d nach %d s Last", q, 180 * q)));
                namen.add(f("T%d (%d s Last)", q, 180 * q));
            }
            if (ruhe) {
                Thread.sleep(120_000);
                t.add(thermo(b, c, "nach 120 s Ruhe"));
                namen.add("120 s Ruhe");
                Thread.sleep(300_000);
                t.add(thermo(b, c, "nach weiteren 300 s Ruhe"));
                namen.add("420 s Ruhe");
            }
            A.teil("Urteil");
            StringBuilder sb = new StringBuilder();
            for (int q = 0; q < t.size(); q++) sb.append(f("%s%s %.2f (×%.3f)", q > 0 ? "; " : "", namen.get(q), t.get(q)[0], t.get(q)[0] / t.get(0)[0]));
            A.info("Thermometer (stehendes Bild, Median, ms)", sb.toString());
        } finally {
            b.close();
        }
    }

    /**
     * Seit L7h: Wer macht das laufende Bild nach rund 6,5 min Szenenzeit 12 ms teurer? Die Ansicht beginnt {@code versatz} s
     * später; erst das stehende Bild jetzt (Inhalt) und laufend, bis der Median über {@link #SPRUNG} ms liegt (höchstens 15 min),
     * dann je Schalter zweimal im Wechsel 60 Bilder an und 60 aus.
     */
    static void schalterTest(Szenen sz, int fall, int versatz) throws Exception {
        String[] fa0 = SeegangAbnahme.FAELLE[fall];
        String[] fa = {fa0[0], fa0[1], java.time.Instant.parse(fa0[2]).plusSeconds(versatz).toString()};
        Buehne b = Buehne.ohneFenster(sz.gebiet(), 1920, 1080);
        A.info("Gerät", b.geraet().name + ", 1920 × 1080");
        try {
            A.teil(fa[0] + ", " + fa[1] + f(" ab %s (%+d s)", fa[2], versatz));
            SchwimmerCheck.pegel(b, sz);
            b.oeffne(SchaumCheck.szenario(sz, fa[0]));
            b.wasserbild().seegang = true;
            LeistungCheck.Fall c = LeistungCheck.einrichten(b, sz, fa, 1920, 1080);
            LeistungCheck.festhalten(c);
            thermo(b, c, "stehendes Bild am Anfang");
            long bis = System.nanoTime() + 15 * 60_000_000_000L;
            double med = 0;
            int runde = 0;
            while (System.nanoTime() < bis) {
                med = laufend(b, c, 300);
                runde++;
                if (runde == 1 || med > SPRUNG) A.info(f("laufend, Szenenzeit %+.0f s", c.zt[0] - c.tFest), f("Median %.1f ms über 300 Bilder", med));
                if (med > SPRUNG) break;
            }
            A.info("Stand vor den Schaltern", f("Median %.1f ms nach %d Runden zu 300 Bildern, Szenenzeit %+.0f s seit dem Vorlauf", med, runde, c.zt[0] - c.tFest));
            // das stehende Bild zur jetzigen Zeit: ist es der Inhalt?
            com.dan.core.raum.Blick bl = c.k != null ? c.fest : c.bl0;
            Buehne.Bildzeiten zj = b.bildzeiten(bl, c.zt[0], THERMO, true);
            A.info("stehendes Bild zur jetzigen Zeit", f("ganzes Bild %.2f ms (Median), Himmel %.2f ms, Netz %.2f ms", zj.karte()[THERMO / 2],
                    zj.abschnitte().getOrDefault("Himmel", 0.0), zj.abschnitte().getOrDefault("Wasser, Netz", 0.0)));
            com.dan.lauf.Flaechenwart w = c.w;
            Object[][] sl = {
                    {"Auflauf", (Runnable) () -> { if (w.auflauf != null) w.auflauf.an = false; }, (Runnable) () -> { if (w.auflauf != null) w.auflauf.an = true; }},
                    {"Brandung", (Runnable) () -> b.brandung(false), (Runnable) () -> b.brandung(true)},
                    {"Gestalt", (Runnable) () -> b.gestalt(false), (Runnable) () -> b.gestalt(true)},
                    {"Bewuchs", (Runnable) () -> b.bewuchs(false), (Runnable) () -> b.bewuchs(true)},
                    {"Kimmung", (Runnable) () -> b.kimmung.an = false, (Runnable) () -> b.kimmung.an = true},
                    {"Spiegelung des Geländes", (Runnable) () -> b.wasserbild().spiegeln = false, (Runnable) () -> b.wasserbild().spiegeln = true},
                    {"Kräuseln", (Runnable) () -> b.wasserbild().kraeuseln = false, (Runnable) () -> b.wasserbild().kraeuseln = true}};
            for (Object[] x : sl) {
                List<Double> an = new ArrayList<>(), aus = new ArrayList<>();
                for (int q = 0; q < 2; q++) {
                    an.add(laufend(b, c, 60));
                    ((Runnable) x[1]).run();
                    try {
                        laufend(b, c, 5);
                        aus.add(laufend(b, c, 60));
                    } finally {
                        ((Runnable) x[2]).run();
                    }
                    laufend(b, c, 5);
                }
                A.info("Schalter " + x[0], f("an %.1f ms, aus %.1f ms (Mittel zweier Mediane); Preis %.1f ms", (an.get(0) + an.get(1)) / 2, (aus.get(0) + aus.get(1)) / 2,
                        (an.get(0) + an.get(1)) / 2 - (aus.get(0) + aus.get(1)) / 2));
            }
            // Flotte ruht: Bilder ohne Auftrag an die Flotte
            List<Double> an = new ArrayList<>(), ruht = new ArrayList<>();
            for (int q = 0; q < 2; q++) {
                an.add(laufend(b, c, 60));
                List<Double> l = new ArrayList<>();
                for (int i = 0; i < 65; i++) {
                    Buehne.Bildzeiten z = BildzeitDiagnose.ohneFlotte(b, c);
                    if (i >= 5) l.add(z.karte()[0]);
                }
                ruht.add(SchaumCheck.median(l));
            }
            A.info("Schalter Flotte ruht", f("an %.1f ms, ruht %.1f ms; Preis %.1f ms", (an.get(0) + an.get(1)) / 2, (ruht.get(0) + ruht.get(1)) / 2,
                    (an.get(0) + an.get(1)) / 2 - (ruht.get(0) + ruht.get(1)) / 2));
            A.info("Auflauf zuletzt", w.auflauf == null ? "keiner" : f("aktiv %s, Watt %s", w.auflauf.aktiv(), w.auflauf.watt()));
        } finally {
            b.close();
        }
    }

    static final double SPRUNG = 45;

    /**
     * Seit L7i (S9c-0, SF177 A): Was ändert sich um den Sprung? Die Ansicht beginnt {@code versatz} s später; je 300 Bilder
     * laufend der Median, die Schwimmer im Umkreis von 3 km mit Abstand und Richtung (Lotse: Phase), die Abschnitte gegen das
     * erste Fenster; Bilder (PNG) am Anfang und nach dem Sprung, dazu je das stehende Bild zu dieser Zeit je Abschnitt.
     */
    static void sprungTest(Szenen sz, int fall, int versatz) throws Exception {
        String[] fa0 = SeegangAbnahme.FAELLE[fall];
        String[] fa = {fa0[0], fa0[1], java.time.Instant.parse(fa0[2]).plusSeconds(versatz).toString()};
        Buehne b = Buehne.ohneFenster(sz.gebiet(), 1920, 1080);
        A.info("Gerät", b.geraet().name + ", 1920 × 1080");
        try {
            A.teil(fa[0] + ", " + fa[1] + f(" ab %s (%+d s)", fa[2], versatz));
            SchwimmerCheck.pegel(b, sz);
            b.oeffne(SchaumCheck.szenario(sz, fa[0]));
            b.wasserbild().seegang = true;
            LeistungCheck.Fall c = LeistungCheck.einrichten(b, sz, fa, 1920, 1080);
            LeistungCheck.festhalten(c);
            java.util.Map<String, Double> stehend0 = null, erstes = null;
            boolean gesprungen = false;
            int nach = 0;
            for (int runde = 0; runde < 40 && nach < 2; runde++) {
                List<Double> ms = new ArrayList<>();
                java.util.Map<String, Double> summe = new java.util.TreeMap<>();
                for (int i = 0; i < 300; i++) {
                    Buehne.Bildzeiten z = SeegangAbnahme.bild(b, c.w, c.k, c.bl0, c.zt, c.dt, c.uhrNs);
                    c.uhrNs += Math.round(c.dt * 1e9);
                    ms.add(z.karte()[0]);
                    for (java.util.Map.Entry<String, Double> e : z.abschnitte().entrySet()) summe.merge(e.getKey(), e.getValue() / 300, Double::sum);
                }
                double med = SchaumCheck.median(ms);
                if (erstes == null) erstes = summe;
                java.time.Instant jetzt = b.uhr().start.plusMillis(Math.round(c.zt[0] * 1000));
                A.info(f("Runde %d, %s", runde, jetzt), f("laufend %.1f ms; gegen die erste Runde: %s; Schwimmer: %s", med,
                        BildzeitDiagnose.unterschiedKarten(summe, erstes, 6), schwimmer(b, c.bl0)));
                boolean bild = runde == 0 || (!gesprungen && med > SPRUNG) || (gesprungen && nach == 1);
                if (med > SPRUNG && !gesprungen) gesprungen = true;
                if (gesprungen) nach++;
                if (bild) {
                    String name = f("S9c0_%s_%s_%s.png", fa[0], fa[1], runde == 0 ? "vor" : "nach" + runde);
                    javax.imageio.ImageIO.write(SeegangBilder.bild(b.letztesBild(), 1920, 1080), "png", Path.of("docs", "status", name).toFile());
                    Buehne.Bildzeiten zs = b.bildzeiten(c.bl0, c.zt[0], THERMO, true);
                    if (stehend0 == null) stehend0 = zs.abschnitte();
                    A.info("stehendes Bild zur Zeit, " + name, f("%.2f ms; gegen das erste: %s", zs.karte()[THERMO / 2], BildzeitDiagnose.unterschiedKarten(zs.abschnitte(), stehend0, 8)));
                }
            }
        } finally {
            b.close();
        }
    }

    /** Die Schwimmer im Umkreis von 3 km um das Auge: Name, Abstand (m), Richtung (Grad), Phase des Lotsen. */
    static String schwimmer(Buehne b, com.dan.core.raum.Blick bl) {
        com.dan.lauf.Flotte fl = b.flotte();
        if (fl == null) return "keine Flotte";
        StringBuilder sb = new StringBuilder();
        int fern = 0;
        for (com.dan.lauf.Flotte.Schwimmer sw : fl.schwimmer) {
            double[] l = sw.koerper.lage();
            double d = Math.hypot(l[0] - bl.ost, l[1] - bl.nord);
            if (!(d < 3000)) { fern++; continue; }
            double r = (Math.toDegrees(Math.atan2(l[0] - bl.ost, l[1] - bl.nord)) + 360) % 360;
            String ph = sw.phase();
            sb.append(f("%s%s %.0f m %.0f°%s", sb.length() > 0 ? ", " : "", sw.angabe.name, d, r, ph != null ? " (" + ph + ")" : ""));
        }
        return sb + f(" [Blick %.0f°; %d weiter weg]", bl.richtung, fern);
    }

    /** n Bilder laufend wie die Abnahme; Median der Karte. */
    static double laufend(Buehne b, LeistungCheck.Fall c, int n) {
        List<Double> ms = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Buehne.Bildzeiten z = SeegangAbnahme.bild(b, c.w, c.k, c.bl0, c.zt, c.dt, c.uhrNs);
            c.uhrNs += Math.round(c.dt * 1e9);
            ms.add(z.karte()[0]);
        }
        return SchaumCheck.median(ms);
    }

    /** Das stehende Bild: {Median der Karte, Himmel im Mittel}. */
    static double[] thermo(Buehne b, LeistungCheck.Fall c, String name) {
        Buehne.Bildzeiten z = b.bildzeiten(c.fest, c.tFest, THERMO, true);
        double[] v = {z.karte()[THERMO / 2], z.abschnitte().getOrDefault("Himmel", 0.0), z.abschnitte().getOrDefault("Wasser, Netz", 0.0)};
        A.info(name, f("ganzes Bild %.2f ms (Median), Himmel %.2f ms, Netz %.2f ms (Mittel)", v[0], v[1], v[2]));
        return v;
    }

    /** Dauerlast: laufend wie die Abnahme, s Sekunden ohne Pause; Median je 30 s. */
    /** Seit L7g: die Mittel je Abschnitt des ersten Fensters (Bezug für die Unterschiede). */
    static java.util.Map<String, Double> bezug;

    static void last(Buehne b, LeistungCheck.Fall c, int s, String name) {
        long ende = System.nanoTime() + s * 1_000_000_000L, abschnitt = System.nanoTime() + 30_000_000_000L;
        List<Double> ms = new ArrayList<>();
        java.util.Map<String, Double> summe = new java.util.TreeMap<>();
        StringBuilder sb = new StringBuilder();
        int n = 0, bau = 0, fenster = 0;
        com.dan.lauf.Flotte fl = b.flotte();
        while (System.nanoTime() < ende) {
            Buehne.Bildzeiten z = SeegangAbnahme.bild(b, c.w, c.k, c.bl0, c.zt, c.dt, c.uhrNs);
            c.uhrNs += Math.round(c.dt * 1e9);
            ms.add(z.karte()[0]);
            if (c.w.baut()) bau++;
            for (java.util.Map.Entry<String, Double> e : z.abschnitte().entrySet()) summe.merge(e.getKey(), e.getValue(), Double::sum);
            n++;
            fenster++;
            if (System.nanoTime() > abschnitt) {
                sb.append(f("%s%.1f", sb.length() > 0 ? ", " : "", SchaumCheck.median(ms)));
                // seit L7g: je Fenster der Zustand – Bau der Fläche, Zählung der Baue, Art der Flotte, Abschnitte gegen das erste Fenster
                java.util.Map<String, Double> mittel = new java.util.TreeMap<>();
                for (java.util.Map.Entry<String, Double> e : summe.entrySet()) mittel.put(e.getKey(), e.getValue() / fenster);
                if (bezug == null) bezug = mittel;
                double[] zw = c.w.zahlen();
                A.info(f("%s, Fenster bis Szenenzeit %+.0f s", name, c.zt[0] - c.tFest), f("Median %.1f ms; Bau der Fläche in %d von %d Bildern; Baue %.0f, Stücke %.0f; Phase %d; Flotte Art %s, Faden %.1f ms; gegen das erste Fenster: %s",
                        SchaumCheck.median(ms), bau, fenster, zw[0], zw[1], c.w.phase(), fl != null ? String.valueOf(fl.art) : "–", fl != null ? fl.fadenMs : Double.NaN,
                        BildzeitDiagnose.unterschiedKarten(mittel, bezug, 6)));
                ms.clear();
                summe.clear();
                bau = 0;
                fenster = 0;
                abschnitt += 30_000_000_000L;
            }
        }
        if (!ms.isEmpty()) sb.append(f("%s%.1f", sb.length() > 0 ? ", " : "", SchaumCheck.median(ms)));
        A.info(name, f("%d Bilder in %d s; laufend je 30 s (Median, ms): %s", n, s, sb));
    }
}
