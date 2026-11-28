package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Flotte;
import com.dan.lauf.Kamera;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.dan.core.pruef.Attest.f;

/**
 * Abnahme des Seegangs, Prüfstein 42 (Seegang Werkbuch, Datei 104, S8e; SF162 A): die Bildrate je Qualitätsstufe in den
 * drei Szenarien mit Seegang, ohne Fenster, 1920 × 1080, wie die Anwendung sie zeichnet (Fläche bereit, Schwimmer, Brandung,
 * Bordkamera und Schwimmerblick wo die Ansicht an Bord ist).
 * <ul>
 * <li><b>Stufen</b> wie {@code com.dan.steuerhaus.analyse.Qualitaet}: Hoch alles; Mittel ohne Bewuchs und Kimmung, seit S9b-4
 * ohne Spiegelung des Geländes; Niedrig dazu ohne Brandung, Gestalt und Fläche des Seegangs.</li>
 * <li><b>Gemessen:</b> je Bild die Zeit der Karte (Summe der Durchgänge, {@code Buehne.bildzeiten}); Median über
 * {@link #BILDER} Bilder nach {@link #VORLAUF} Bildern Vorlauf je Stufe. Bildrate = 1000 / Median. Dazu die Mehrkosten von
 * Hoch gegen Niedrig (Seegang, Brandung, Gestalt, Bewuchs, Kimmung; Datei 1: für den Seegang höchstens 8 ms in Hoch) und die
 * teuersten Durchgänge (bis L6b2 die Summe der Durchgänge des Seegangs; sie zählte Teile doppelt).</li>
 * <li><b>Grenzen:</b> Hoch mindestens {@link #KINO} Bilder/s (Teil C, Prüfstein 6 im Kino), Xaver mindestens {@link #XAVER}
 * (A53); Ziel aus Datei 1 60 Bilder/s als Auskunft.</li>
 * </ul>
 * Seit S9b-5 (Seegang Werkbuch 107, Befund 2.2 aus Datei 106): eingeschwungen und warm gemessen. Der Vorlauf wartet auch auf
 * die Körperfläche und läuft danach noch {@link #LAUF} Bilder; mit {@code --warm s} läuft die erste Ansicht vorher s Sekunden
 * unter Last (die Karte wird warm, rund 11 % langsamer). Prüfstein 44: Hoch warm mindestens 30 Bilder/s, Xaver 20.
 * <p>
 * Seit S9c-2 (Seegang Werkbuch 108, SF180 A): vor und nach jeder Stufe die {@link Eichlast} der Karte. Liegt sie mehr als
 * 10 % über dem besten Wert des Laufs, wurde die Stufe bis S9d-1 wiederholt (höchstens zweimal), sonst galt sie als
 * gestört. Am Ende wird jede Stufe noch einmal gegen den besten Wert des ganzen Laufs geprüft. Prüfstein 44 wertet je
 * Szenario nur ungestörte Messungen in Hoch; ist eine Ansicht nur gestört gemessen, ist er nicht erfüllt (SF183 A: „im
 * ruhigen Zustand alle Ansichten“). Dazu je Stufe die Uhrzeit, zum Abgleich mit der Mitschrift der Maschine (SF181 A).
 * <p>
 * Seit S9d-1 (Seegang Werkbuch 109, SF184 A, SF185 A): die Eichlast mit Vorlast ({@link Eichlast#VORLAST_MS} ms), und
 * Prüfstein 47 – an Bord in Hoch rechnet die Himmelsansicht je Messung höchstens {@link #HIMMEL_BORD}-mal neu (die Höhe
 * des Auges löst erst ab 10 m aus; vorher rund jedes fünfte Bild eine ganze Ansicht). Je Stufe dazu, wie oft die Ansicht
 * neu war und was ihre Gruppen je Bild kosteten. Mit {@code --aus} der Name der Ausgaben (Vorgabe {@code S9d1}), damit
 * zwei Abnahmen in einem Lauf nebeneinander stehen (SF186 A).
 * <p>
 * Seit S9d-2 (Seegang Werkbuch 110, SF187 A, SF189 A):
 * <ul>
 * <li><b>Wächter ist die Bildzeit selbst:</b> Hoch wird zweimal gemessen; weichen die beiden um mehr als
 * {@link #ABWEICHUNG} ab, ein dritter Versuch. Gewertet wird der Median der Versuche (bei zweien ihr Mittel). Die
 * {@link Eichlast} steht nur noch als Auskunft im Attest; sie wiederholt nichts und wertet nichts.</li>
 * <li><b>Fester Messbeginn:</b> Der Vorlauf läuft, bis die Fläche steht, und mindestens bis {@link #MESS_AB_S} s nach der
 * Zeit des Falls; dann {@link #LAUF} Bilder und die Messung. So misst jede Abnahme zur selben Szenenzeit, an Bord also
 * denselben Kurs (L7j–L7l: Bord Kutter 33,8–44,1 ms je nach Länge des Vorlaufs). Weg [G]: vorwärts bis zur festen Zeit
 * statt zurück auf den Anfang – ein Sprung rückwärts setzt die Flotte neu und stört den gemessenen Zeitraffer des
 * Flächenwarts (rund −29 nach einem Bild, abklingend über 1 s).</li>
 * <li><b>Alle Durchgänge</b> in Hoch je Ansicht im Attest (Mittel je Bild), nicht nur die sechs teuersten.</li>
 * </ul>
 * <p>
 * Seit S9 abgeschlossen ist (Seegang Werkbuch 111, SF192 A, SF193 A):
 * <ul>
 * <li><b>Ohne Eichlast:</b> Die Abnahme ruft sie nicht mehr; {@link Eichlast} bleibt als Werkzeug.</li>
 * <li><b>Prüfstein 44 nach dem Mittel aller Bilder:</b> je Versuch das Mittel der {@link #BILDER} Bilder, Bildrate =
 * 1000 / Mittel (so viele Bilder zeigt die Anwendung, wenn die Karte bremst); der Median steht daneben. Über die Versuche
 * gilt weiter der Median (SF187 A). In L7m lag der Median an Bord 3,5 ms über dem Mittel, bei Xaver bis 4,4 ms darunter.</li>
 * </ul>
 * Ausgabe {@code docs/status/<aus>_Abnahme.md}, Bilder {@code <aus>_<Szenario>_<Ansicht>.png}.
 * <pre>  SeegangAbnahme --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--warm 600] [--aus S9e]</pre>
 */
public final class SeegangAbnahme {
    static final Attest A = new Attest("Seegang: Abnahme, Bildrate je Stufe (Prüfstein 44 nach dem Mittel aller Bilder; Versuche, fester Messbeginn)", "S9e");
    static final int BILDER = 60, VORLAUF = 20, BEREIT_MAX = 1800, LAUF = 60, THERMO = 10;
    /** Seit S9d-2 (SF187 A): weichen zwei Versuche in Hoch um mehr als das ab (relativ zum kleineren), ein dritter [G]. */
    static final double ABWEICHUNG = 0.05;
    /** Seit S9d-2 (SF189 A): fester Messbeginn – der Vorlauf läuft mindestens so lange Szenenzeit (s) [G]; L7j–L7l brauchten bis 33,9 s. */
    static final double MESS_AB_S = 40;
    static final java.time.format.DateTimeFormatter UHR = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault());
    /** Seit S9d-1: Name der Ausgaben ({@code --aus}). */
    static String aus = "S9e";
    /** Seit S9d-1 (Prüfstein 47): so oft darf die Himmelsansicht an Bord in Hoch je Messung höchstens neu sein [G]. */
    static final int HIMMEL_BORD = 3;
    /** Seit S9d-1: an Bord in Hoch – Ansicht, wie oft die Himmelsansicht neu war, Bilder der Messung. */
    static final Map<String, Integer> HIMMEL_HOCH = new LinkedHashMap<>();
    /** Eine Messung einer Stufe; laufend als Median und (seit SF193 A, gewertet) als Mittel der Bilder. */
    record Messung(Stufe stufe, double stehend, double laufend, double mittel, int bau, int stocken, Map<String, Double> summe, int versuch, int himmel) { }

    /** Seit S9d-2: eine eingerichtete Ansicht nach dem Vorlauf (auch für {@link DurchgangDiagnose}). */
    static final class Fall {
        final String[] fa;
        Ansicht an;
        Blick bl0;
        Kamera k;
        Flaechenwart w;
        double[] zt;
        final long[] un = {0};
        final double dt = 1.0 / 30;
        /** Zeit des Falls (s seit Beginn der Uhr) und Bilder im Vorlauf. */
        double t0;
        int vorlauf;

        Fall(String[] fa) { this.fa = fa; }

        /** Ein Bild wie die Anwendung (Flotte nebenher), Zeit und Uhr weiter. */
        Buehne.Bildzeiten bild(Buehne b) {
            Buehne.Bildzeiten z = SeegangAbnahme.bild(b, w, k, bl0, zt, dt, un[0]);
            un[0] += Math.round(dt * 1e9);
            return z;
        }

        /** Der Blick des nächsten Bilds (an Bord die Kamera, wie sie jetzt steht; sie geht dabei einen Schritt weiter). */
        Blick blickJetzt() {
            Blick bl = k != null ? k.weiter(dt, zt[0], bl0) : null;
            if (bl == null) bl = bl0.kopie();
            bl.groesse(bl0.breite, bl0.hoehe_px);
            return bl;
        }
    }

    /** Seit S9c-2: Hoch je Ansicht – Bildzeit, Mehrkosten gegen Niedrig, die Messung in Hoch. */
    record Hoch(double ms, double seegang, Messung m) { }

    /** Seit S9b-5: so lange (s) läuft die erste Ansicht vor der Messung unter Last. */
    static int warm = 0;
    static final double KINO = 30, XAVER = 20, ZIEL = 60, SEEGANG_MS = 8;
    /** Fälle: Szenario, Ansicht, Zeit (UTC). */
    static final String[][] FAELLE = {
            {"ruhigsee", "anleger_see", "2026-10-30T14:30:00Z"},
            {"ruhigsee", "schwimmer", "2026-10-30T14:30:00Z"},
            {"ruhigsee", "abend", "2026-10-30T15:30:00Z"},
            {"seegang", "brandung", "2026-10-25T12:30:00Z"},
            {"seegang", "westspitze", "2026-10-25T12:55:00Z"},
            {"seegang", "bord_kutter", "2026-10-25T13:30:00Z"},
            {"xaver", "brandung_hanswarft", "2013-12-05T13:00:00Z"},
            {"xaver", "westspitze", "2013-12-05T13:30:00Z"}};
    enum Stufe { HOCH, MITTEL, NIEDRIG }
    static final String[] SEEGANG = {"see_", "nah_", "Seegang", "Wasser, Netz", "Wasser, Scheitel des Netzes", "Brandung", "Gischt", "Schaumfeld", "Unterwasser", "Linse",
            "Körpersuche", "Kielwasser", "Randwelle", "Fußsuche", "swash", "Swash", "Auflauf"};

    public static void main(String[] args) {
        File szenen = null;
        int breite = 1920, hoehe = 1080;
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                case "--groesse" -> { String[] g = args[++i].split("x"); breite = Integer.parseInt(g[0]); hoehe = Integer.parseInt(g[1]); }
                case "--warm" -> warm = Integer.parseInt(args[++i]);
                case "--aus" -> aus = args[++i];
                default -> { }
            }
        System.out.println("SeegangAbnahme – Prüfstein 44 nach dem Mittel aller Bilder, Prüfstein 47 (Seegang Werkbuch 111, SF192 A, SF193 A); Ausgaben " + aus);
        long t0 = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            alles(Szenen.lies(szenen), breite, hoehe);
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        Path pa = A.schreibe(Path.of("docs", "status", aus + "_Abnahme.md"), "Abnahme des Seegangs: Bildrate je Stufe nach dem Mittel aller Bilder (" + aus + ")");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void alles(Szenen sz, int breite, int hoehe) throws Exception {
        Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
        A.info("Gerät", b.geraet().name + f(", %d × %d", breite, hoehe));
        Map<String, Hoch> hochJe = new LinkedHashMap<>();
        try {
            A.info("Himmelsansicht (seit S9d-1, SF185 A)", f("neu ab %.0f m Höhe des Auges (dazu 0,1 %% relativ), in einem Zug ab %.0f m und 5 %%; Grenze %s",
                    com.dan.render.air.Himmel.HOEHE_MIN, com.dan.render.air.Himmel.HOEHE_SPRUNG, b.himmel().hoeheGrenze ? "an" : "AUS (-Dlauf.himmel.hoehe=alt)"));
            String offen = null;
            for (String[] fa : FAELLE) {
                A.teil(fa[0] + ", " + fa[1]);
                if (!fa[0].equals(offen)) {
                    SchwimmerCheck.pegel(b, sz);
                    b.oeffne(SchaumCheck.szenario(sz, fa[0]));
                    b.wasserbild().seegang = true;
                    offen = fa[0];
                }
                Hoch h = fall(b, sz, fa, breite, hoehe);
                if (h != null) hochJe.put(fa[0] + ", " + fa[1], h);
            }
        } finally {
            b.close();
        }
        // Prüfsteine je Szenario: die langsamste Ansicht in Hoch (Median der Versuche, je Versuch das Mittel aller Bilder; SF187 A, SF193 A)
        Map<String, double[]> jeSz = new TreeMap<>();
        Map<String, String> woSz = new TreeMap<>();
        for (Map.Entry<String, Hoch> en : hochJe.entrySet()) {
            String s = en.getKey().substring(0, en.getKey().indexOf(','));
            Hoch h = en.getValue();
            double[] v = jeSz.get(s);
            if (v == null || h.ms() > v[0]) { jeSz.put(s, new double[] {h.ms(), h.seegang()}); woSz.put(s, en.getKey().substring(en.getKey().indexOf(',') + 2)); }
        }
        A.teil("Urteil");
        for (Map.Entry<String, double[]> en : jeSz.entrySet()) {
            double ms = en.getValue()[0], bs = 1000 / ms, grenze = en.getKey().equals("xaver") ? XAVER : KINO;
            Hoch h = hochJe.get(en.getKey() + ", " + woSz.get(en.getKey()));
            A.pruefe(bs >= grenze, "Bildrate in Hoch, " + en.getKey() + " (langsamste Ansicht, Mittel aller Bilder; Prüfstein 44)",
                    f("%.1f Bilder/s (%.2f ms je Bild im Mittel, %s; Median %.2f ms); Ziel aus Datei 1: %.0f", bs, ms, woSz.get(en.getKey()), h != null ? h.m().laufend() : Double.NaN, ZIEL),
                    f("≥ %.0f Bilder/s", grenze));
        }
        double seeMax = 0;
        String wo = "";
        for (Map.Entry<String, Hoch> e : hochJe.entrySet()) if (e.getValue().seegang() > seeMax) { seeMax = e.getValue().seegang(); wo = e.getKey(); }
        A.pruefe(seeMax <= SEEGANG_MS, "Mehrkosten von Hoch gegen Niedrig (Seegang mit Brandung, Gestalt, Bewuchs, Kimmung; Datei 1, Abschnitt 8)", f("höchstens %.2f ms (%s)", seeMax, wo), f("≤ %.0f ms für den Seegang", SEEGANG_MS));
        // seit S9d-1 (SF185 A): Prüfstein 47, die Himmelsansicht an Bord
        int hMax = -1;
        StringBuilder hl = new StringBuilder();
        for (Map.Entry<String, Integer> e : HIMMEL_HOCH.entrySet()) {
            hMax = Math.max(hMax, e.getValue());
            hl.append(f("%s%s %d-mal", hl.length() > 0 ? "; " : "", e.getKey(), e.getValue()));
        }
        if (hMax >= 0)
            A.pruefe(hMax <= HIMMEL_BORD, "Himmelsansicht an Bord in Hoch neu je Messung (Prüfstein 47, seit S9d-1)", hl + f(" in %d Bildern laufend und %d stehend", VORLAUF + BILDER, THERMO),
                    f("≤ %d-mal [G]", HIMMEL_BORD));
    }

    /**
     * Seit S9d-2: Ansicht setzen, Hoch, Standbild, Flotte, Kamera an Bord; Vorlauf, bis die Fläche (und die Körperfläche)
     * steht und mindestens {@link #MESS_AB_S} s Szenenzeit vergangen sind (SF189 A); dann {@link #LAUF} Bilder.
     */
    static Fall einrichten(Buehne b, Szenen sz, String[] fa, int breite, int hoehe, Attest a) throws Exception {
        Fall c = new Fall(fa);
        c.an = sz.ansicht(fa[1]);
        c.t0 = (Instant.parse(fa[2]).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        c.bl0 = new Blick();
        c.an.setze(c.bl0, b.gelaende, b.utm(), b.uhr().bei(c.t0));
        c.bl0.groesse(breite, hoehe);
        if (c.an.bord == null) {
            double[] wa = b.wasserUndSeegang(c.bl0.ost, c.bl0.nord);
            Ansicht.hebe(c.bl0, wa[0], wa[1], c.an.ueberWasser);
        }
        stufe(b, Stufe.HOCH);
        b.standbild(c.bl0, c.t0);
        Flotte fl = b.flotte();
        if (fl != null) fl.neuSetzen();
        c.k = c.an.bord != null ? b.bordKamera(c.an) : null;
        c.w = b.wart();
        c.zt = new double[] {c.t0};
        int i = 0, bereitBei = -1;
        for (; i < BEREIT_MAX; i++) {
            c.bild(b);
            // seit S9b-5: auch die Körperfläche (L6b2 maß zu früh)
            boolean bereit = i > 60 && c.w.bereit() && !c.w.wechselt() && (c.w.koerpermitte() == null || c.w.koerperBereit());
            if (bereit && bereitBei < 0) bereitBei = i;
            if (bereit && c.zt[0] >= c.t0 + MESS_AB_S - 1e-9) break;              // seit S9d-2: fester Messbeginn
        }
        c.vorlauf = i;
        for (int j = 0; j < LAUF; j++) c.bild(b);
        a.info("Vorlauf", f("Fläche %s nach %d Bildern, Körperfläche %s; Messbeginn %s (%.1f s nach der Zeit des Falls, nach %d Bildern), dann %d Bilder%s",
                c.w.bereit() ? "bereit" : "NICHT bereit", bereitBei, c.w.koerpermitte() == null ? "keine" : c.w.koerperBereit() ? "bereit" : "NICHT bereit",
                Math.abs(c.vorlauf / 30.0 - MESS_AB_S) < 0.05 ? "fest" : "NICHT fest", c.vorlauf / 30.0, c.vorlauf, LAUF,
                c.k != null ? ", an Bord (" + c.k.getClass().getSimpleName() + ")" : ""));
        return c;
    }

    /** Ein Fall: einrichten, je Stufe messen (seit S9d-2 Hoch zwei- bis dreimal, gewertet der Median). */
    static Hoch fall(Buehne b, Szenen sz, String[] fa, int breite, int hoehe) throws Exception {
        Fall c = einrichten(b, sz, fa, breite, hoehe, A);
        Ansicht an = c.an;
        Kamera k = c.k;
        Flaechenwart w = c.w;
        Blick bl0 = c.bl0;
        double dt = c.dt;
        long[] un = c.un;
        double[] zt = c.zt;
        int warmBilder = 0;
        if (warm > 0) {                                                       // seit S9b-5: die Karte warm laufen lassen
            long ende = System.nanoTime() + warm * 1_000_000_000L;
            while (System.nanoTime() < ende) {
                c.bild(b);
                warmBilder++;
            }
            warm = 0;
            A.info("Warmlauf", f("%d Bilder", warmBilder));
        }
        double[] hoch = null;
        Messung mHoch = null;
        StringBuilder zeile = new StringBuilder();
        StringBuilder diag = new StringBuilder();
        StringBuilder versuche = new StringBuilder();
        String name = fa[0] + ", " + fa[1];
        for (Stufe s : Stufe.values()) {
            stufe(b, s);
            // seit S9d-2 (SF187 A): Hoch zweimal, bei mehr als ABWEICHUNG ein dritter; Mittel und Niedrig einmal
            List<Messung> mv = new ArrayList<>();
            int n = s == Stufe.HOCH ? 2 : 1;
            for (int v = 0; v < n || (s == Stufe.HOCH && v == 2 && abweichung(mv) > ABWEICHUNG); v++) {
                Messung mm = messe(b, w, k, bl0, zt, dt, un, s, v + 1);
                mv.add(mm);
                System.out.println(f("         %s %s Versuch %d: laufend im Mittel %.2f ms (Median %.2f), stehend %.2f ms", name, s, v + 1, mm.mittel(), mm.laufend(), mm.stehend()));
            }
            double[] lv = new double[mv.size()];
            for (int q = 0; q < lv.length; q++) lv[q] = mv.get(q).mittel();      // seit SF193 A: je Versuch das Mittel aller Bilder
            double medV = medianVersuche(lv);
            Messung m = mv.get(0);                                             // die Messung, die dem gewerteten Wert am nächsten liegt
            for (Messung x : mv) if (Math.abs(x.mittel() - medV) < Math.abs(m.mittel() - medV)) m = x;
            if (mv.size() > 1) versuche.append(f("%s%s %s → %.2f ms", versuche.length() > 0 ? "; " : "", s, liste(lv), medV));
            double med = medV;
            diag.append(f("%s%s: stehend %.2f ms, laufend im Mittel %.2f ms (Median %.2f), Bau der Fläche in %d von %d Bildern, über 2 × Median %d, Himmelsansicht %d-mal neu (Gruppen %.2f ms je Bild)",
                    diag.length() > 0 ? "; " : "", s, m.stehend(), med, m.laufend(), m.bau(), BILDER, m.stocken(), m.himmel(), m.summe().getOrDefault("Himmel, Ansicht, Gruppe", 0.0)));
            zeile.append(f("%s%s %.1f B/s (%.2f ms)", zeile.length() > 0 ? "; " : "", s, 1000 / med, med));
            if (s == Stufe.NIEDRIG && hoch != null) hoch[1] = hoch[0] - med;
            if (s == Stufe.HOCH) {
                hoch = new double[] {med, Double.NaN};
                mHoch = m;
                if (an.bord != null) HIMMEL_HOCH.put(name, m.himmel());
                List<Map.Entry<String, Double>> l = new ArrayList<>(m.summe().entrySet());
                l.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
                StringBuilder top = new StringBuilder();
                for (int q = 0; q < Math.min(6, l.size()); q++) top.append(f("%s%s %.2f", q > 0 ? ", " : "", l.get(q).getKey(), l.get(q).getValue()));
                A.info("teuerste Durchgänge in Hoch (ms)", top.toString());
                StringBuilder alle = new StringBuilder();                     // seit S9d-2 (SF189 A): alle Durchgänge
                double sum = 0;
                for (Map.Entry<String, Double> e : l) {
                    sum += e.getValue();
                    if (e.getValue() >= 0.005) alle.append(f("%s%s %.2f", alle.length() > 0 ? "; " : "", e.getKey(), e.getValue()));
                }
                A.info("alle Durchgänge in Hoch (ms je Bild, Mittel; seit S9d-2)", alle + f(" – Summe %.2f", sum));
                javax.imageio.ImageIO.write(SeegangBilder.bild(b.letztesBild(), breite, hoehe), "png", Path.of("docs", "status", aus + "_" + fa[0] + "_" + fa[1] + ".png").toFile());
            }
        }
        A.info("Bildrate je Stufe (Mittel aller Bilder; seit SF193 A)", zeile + (hoch != null ? f("; Hoch gegen Niedrig +%.2f ms", hoch[1]) : ""));
        A.info("Diagnose je Stufe (seit L7d)", diag.toString());
        if (versuche.length() > 0) A.info("Versuche in Hoch (Mittel je Versuch), gewertet der Median (SF187 A, SF193 A)", versuche.toString());
        A.info("Speicher der Karte (seit L7d)", speicher(b));
        stufe(b, Stufe.HOCH);
        if (k != null) b.bordKamera(null);
        return hoch == null ? null : new Hoch(hoch[0], hoch[1], mHoch);
    }

    /** Eine Stufe messen – stehendes Bild, Vorlauf und Bilder laufend (seit SF192 A ohne Eichlast). */
    static Messung messe(Buehne b, Flaechenwart w, Kamera k, Blick bl0, double[] zt, double dt, long[] un, Stufe s, int versuch) {
        int himmel0 = b.himmel().ansichtGerechnet;
        // seit L7d (Seegang Werkbuch 107): Thermometer – das stehende Bild der Ansicht in dieser Stufe (dieselbe Arbeit)
        double thermo = SeegangAbnahme.median(b.bildzeiten(bl0, zt[0], THERMO, false).karte());
        List<Double> ms = new ArrayList<>();
        Map<String, Double> summe = new TreeMap<>();
        int bau = 0;
        for (int j = 0; j < VORLAUF + BILDER; j++) {
            Buehne.Bildzeiten z = bild(b, w, k, bl0, zt, dt, un[0]);
            un[0] += Math.round(dt * 1e9);
            if (j < VORLAUF) continue;
            ms.add(z.karte()[0]);
            if (w.baut()) bau++;
            for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) summe.merge(e.getKey(), e.getValue() / BILDER, Double::sum);
        }
        int himmel = b.himmel().ansichtGerechnet - himmel0;
        double med = SchaumCheck.median(ms), mit = 0;
        for (double x : ms) mit += x / ms.size();
        int stocken = 0;
        for (double x : ms) if (x > 2 * med) stocken++;
        return new Messung(s, thermo, med, mit, bau, stocken, summe, versuch, himmel);
    }

    static Buehne.Bildzeiten bild(Buehne b, Flaechenwart w, Kamera k, Blick bl0, double[] zt, double dt, long uhrNs) {
        w.zeitraffer(1);
        if (b.flotte() != null) b.flotteBild(zt[0], 1, dt);
        Blick bl = k != null ? k.weiter(dt, zt[0], bl0) : null;
        if (bl == null) bl = bl0.kopie();
        bl.groesse(bl0.breite, bl0.hoehe_px);
        w.vorBild(bl, zt[0], uhrNs);
        Buehne.Bildzeiten z = b.bildzeiten(bl, zt[0], 1, true);
        w.nachBild();
        zt[0] += dt;
        return z;
    }

    /** Seit L7d: die Heaps der Karte, Belegung gegen Budget des Treibers. */
    static String speicher(Buehne b) {
        StringBuilder sb = new StringBuilder();
        for (com.dan.core.gpu.Geraet.Heap h : b.geraet().heaps())
            sb.append(f("%sHeap %d%s: %.0f von %.0f MB (Budget), Größe %.0f MB", sb.length() > 0 ? "; " : "", h.nummer(), h.geraetelokal() ? " (lokal)" : "",
                    h.belegt() / 1048576.0, h.budget() / 1048576.0, h.groesse() / 1048576.0));
        return sb.toString();
    }

    static boolean seegang(String abschnitt) {
        for (String s : SEEGANG) if (abschnitt.startsWith(s)) return true;
        return false;
    }

    /** Die Ebenen wie {@code Qualitaet.Stufe.erlaubt} (seit S9b-4 auch die Spiegelung des Geländes nur in Hoch). */
    static void stufe(Buehne b, Stufe s) {
        b.bewuchs(s == Stufe.HOCH);
        b.kimmung.an = s == Stufe.HOCH;
        b.brandung(s != Stufe.NIEDRIG);
        b.gestalt(s != Stufe.NIEDRIG);
        b.seegangFlaeche(s != Stufe.NIEDRIG);
        b.wasserbild().spiegeln = s == Stufe.HOCH;                             // seit S9b-4 (SF173 A): wie Qualitaet.Teil.SPIEGELUNG
    }

    /** Seit S9d-2: der gewertete Wert der Versuche – Median, bei zweien ihr Mittel. */
    static double medianVersuche(double[] v) {
        double[] c = v.clone();
        Arrays.sort(c);
        int n = c.length;
        return n % 2 == 1 ? c[n / 2] : (c[n / 2 - 1] + c[n / 2]) / 2;
    }

    /** Seit S9d-2: Abweichung der ersten beiden Versuche, relativ zum kleineren. */
    static double abweichung(List<Messung> mv) {
        if (mv.size() < 2) return 0;
        double a = mv.get(0).mittel(), b = mv.get(1).mittel();
        return Math.abs(a - b) / Math.min(a, b);
    }

    static String liste(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (double x : v) sb.append(f("%s%.2f", sb.length() > 0 ? " / " : "", x));
        return sb.toString();
    }

    static double median(double[] a) {
        double[] c = a.clone();
        Arrays.sort(c);
        return c[c.length / 2];
    }
}
