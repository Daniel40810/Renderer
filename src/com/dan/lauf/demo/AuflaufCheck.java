package com.dan.lauf.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Auflauf;
import com.dan.lauf.Buehne;
import com.dan.lauf.Seegangsflaeche;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.swash.Swash;
import com.dan.render.seegang.SeegangKarte;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S6b-2 (Seegang Werkbuch, Datei 68): der Auflauf in der Anwendung ({@link Auflauf}), angetrieben aus der
 * Synthese einer Fläche des Seegangs, so wie ihn die Bühne baut ({@link Buehne#auflauf()}: Gelände und Spiegel des
 * Wassers der Bühne). Die Bildschleife läuft hier nicht; die Prüfung führt sie nach: je Bild die Zeit, die Clipmap und
 * der Auflauf ({@code Seegangbild.Anhang}) in einem Ablauf, 60 Bilder je s Modellzeit.
 * <ol>
 * <li>Sturm 25.10.2026 12:30 und Xaver 5.12.2013 13:30: Blick auf die Kante mit dem höchsten Seegang am Fuß im ganzen
 * Gebiet ({@link WalzeCheck#KANTE_ABSTAND} m seewärts, wie S5b-4).</li>
 * <li>Sturm: die Ansicht „brandung“ wie sie ist (ob sie eine Wasserlinie trifft, ist Auskunft).</li>
 * </ol>
 * Seit S6b-3 (Datei 72): die landseitige Spalte zählt nur Zeilen, für die das Rechteck gelegt ist (in Ruhe trocken, nicht
 * hinter einer Krone, Wasserlinie mindestens 2 R / tan β vor der Landseite; SF69 A, SF70 A); Überlauf über die offene
 * Landseite als Auskunft.
 * Prüfsteine je gesetztem Rechteck [G, vor der Messung, Datei 68]: kein Fehler in den Bildern; Bilanz |V − V₀ − Zufluss + Versickert − Abgeschnitten|
 * / V₀ ≤ 1e-4; seeseitiger Rand am Ende nass in mindestens 90 % der Zeilen; die landseitige Spalte nie nass in
 * mindestens 90 % der Zeilen, in denen sie in Ruhe trocken ist; kein Bild mit zu wenig Schritten; Kosten je Bild
 * (Antrieb und Löser) ≤ 1 ms auf der 680M (Grenze aus Datei 64 für S6b, hier zum ersten Mal in der Anwendung).
 * Dazu bei den Kanten: Rechteck gesetzt. Auskunft: Lage, höchster Auflauf und je Zeile gegen die Schätzung (Maßstab erst
 * S6b-3), Zufluss je m Ufer, Schritte je Bild, Rechenzeit des Setzens. Seit S6b-2b (Datei 70; 120 s je Fall): Tempo je Bild
 * (Median, 99 %, höchstes mit Zeile), Bilder je Zahl der Schritte, Ort der Spitzen über 8 m/s (Zelle, Tiefe,
 * Geschwindigkeit, Grund, ob hinter der Krone), Zeitreihe des Wassers alle 10 s, Hs am seeseitigen Rand aus der Synthese
 * neben dem aus den Netzen.
 * <p>
 * Aufruf: {@code AuflaufCheck --szenen <Hooge.szenen> [--dauer s]}; Attest {@code docs/status/S6b_Auflauf.md}.
 */
public final class AuflaufCheck {
    /** Modellzeit je Fall (s), Bilder je s, nicht gemessene Bilder am Anfang [G]. */
    static final double DAUER = 120;
    static final int BILDER_JE_S = 60, VORLAUF = 60;
    /** Grenzen [G]. */
    static final double BILANZ = 1e-4, NASS_SEE = 0.9, TROCKEN_LAND = 0.9, KOSTEN_MS = 1.0;
    /** Diagnose (seit S6b-2b, SF66 A; Auskunft): Ort des Tempos lesen ab so viel m/s, höchstens so oft; Zeitreihe alle s. */
    static final double TEMPO_ORT = 8, REIHE_S = 10;
    static final int ORTE_MAX = 12;
    /** Seit S6c-2 (Seegang Werkbuch 82): Spülsaum des Bilds gegen die Linie des höchsten Stands je Zeile (m) und Anteil der Zeilen [G, Datei 64]. */
    static final double SAUM_TOL = 0.5, SAUM_ANTEIL = 0.95;
    /** Seit Datei 83: Kosten des Bilds des Auflaufs höchstens (ms) [G]; Messung ohne Bild so viele Sekunden Modellzeit. */
    static final double KOSTEN_BILD_MS = 0.1;
    static final int KOSTEN_OHNE_S = 30;

    private AuflaufCheck() { }

    public static void main(String[] args) {
        File szenen = null;
        double dauer = DAUER;
        int breite = 1920, hoehe = 1080;
        for (int i = 0; i + 1 < args.length; i += 2)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--dauer" -> dauer = Double.parseDouble(args[i + 1]);
                default -> { }
            }
        Attest A = new Attest("Seegang, Auflauf in der Anwendung (S6b-2)", "Seegang Werkbuch");
        System.out.println("AuflaufCheck – S6b-2 (Seegang Werkbuch)");
        long start = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            Ansicht an = sz.ansicht("brandung");
            Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            SchaumCheck.b = b;
            try {
                Geraet g = b.geraet();
                A.info("Gerät", g.name);
                Auflauf au = b.auflauf();
                A.info("Auflauf", f("Version %s; Rechteck %d × %d Zellen zu %.2f m (%.0f m quer, %.0f m längs), landwärts %.0f–%.0f m (%.0f × Auflauf / tan β), seeseitiger Rand mindestens %.1f m tief, Zeitschritt × %.1f auf das Tempo, höchstens %d Schritte je Bild; Swash %s",
                        Auflauf.VERSION, Auflauf.NX, Auflauf.NY, Auflauf.DX, Auflauf.NX * Auflauf.DX, Auflauf.NY * Auflauf.DX, Auflauf.LAND_MIN, Auflauf.LAND_MAX, Auflauf.AUFLAUF_FAKTOR, Auflauf.TIEFE_MIN, Auflauf.SICHERHEIT, Auflauf.SCHRITTE_MAX, Swash.VERSION));
                A.pruefe(au != null, "Auflauf an der Bühne (ohne -Dlauf.auflauf=aus)", au == null ? "keiner" : "ja", "vorhanden");
                if (au == null) return;
                String[][] faelle = {{"seegang", "2026-10-25T12:30:00Z", "Sturm", "kante"}, {"seegang", "2026-10-25T12:30:00Z", "Sturm", "ansicht"}, {"xaver", "2013-12-05T13:30:00Z", "Xaver", "kante"}};
                String offen = null;
                for (String[] fa : faelle) {
                    Szenario sc = SchaumCheck.szenario(sz, fa[0]);
                    String name = fa[2] + (fa[3].equals("kante") ? ", Kante" : ", Ansicht brandung");
                    A.teil(name);
                    if (!fa[0].equals(offen)) { b.oeffne(sc); offen = fa[0]; }
                    Blick bl = SchaumCheck.blick(an, fa[1], breite, hoehe);
                    b.wasserbild().seegang = true;
                    b.brandung(true);
                    b.standbild(bl, SchaumCheck.t);
                    double t0 = SchaumCheck.t;
                    if (fa[3].equals("kante")) {
                        bl = kante(A, b, t0, breite, hoehe, name);
                        if (bl == null) continue;
                    }
                    fall(A, b, au, sz, sc, bl, t0, dauer, fa[3].equals("kante"), name);
                }
            } finally {
                b.close();
            }
            A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - start) / 1e9));
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "S6b_Auflauf.md"), "S6b-2 – Auflauf in der Anwendung");
        System.out.println();
        System.out.println("Attest: " + p.toAbsolutePath());
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Die zuletzt gewählte Kante und ihr Protokoll (Brandung: {Kante, …, Hs am Fuß [6], Tp [7]}), für AuflaufMassstabCheck. */
    static com.dan.core.szene.Kanten.Kante letzteKante;
    static double[] letzteWahl;

    /** Blick auf die Kante mit dem höchsten Seegang am Fuß (wie {@code WalzeCheck.fussTeil}); null ohne aktive Kante. */
    static Blick kante(Attest A, Buehne b, double t0, int breite, int hoehe, String name) { return kante(A, b, t0, breite, hoehe, name, art -> true); }

    /** Wie oben, nur Kanten, deren Art {@code arten} annimmt (seit S6b-3: Sand, Deckwerk, Deich). */
    static Blick kante(Attest A, Buehne b, double t0, int breite, int hoehe, String name, java.util.function.IntPredicate arten) {
        com.dan.render.brandung.Brandungsbild bb = b.brandungsbild();
        if (bb == null || bb.quelle == null) { A.pruefe(false, "Brandung im Gebiet, " + name, "keine", "vorhanden"); return null; }
        com.dan.core.szene.Brandung such = new com.dan.core.szene.Brandung(bb.brandung.e0, bb.brandung.n0);
        such.weite = 1e9;
        such.abschnitteMax = Integer.MAX_VALUE;
        such.protokollieren = true;
        double[] w = bb.quelle.wind();
        such.rechne(bb.kanten, bb.quelle.wasser(), t0, bb.brandung.e0, bb.brandung.n0, w[0], w[1]);
        double[] wahl = null;
        int aktiv = 0;
        for (double[] q : such.protokoll) {
            if (!arten.test(bb.kanten.kanten.get((int) q[0]).art())) continue;
            aktiv++;
            if (wahl == null || q[6] > wahl[6]) wahl = q;
        }
        if (wahl == null) { A.pruefe(false, "aktive Kante im Gebiet, " + name, "keine", "mindestens eine"); return null; }
        com.dan.core.szene.Kanten.Kante ka = bb.kanten.kanten.get((int) wahl[0]);
        letzteKante = ka;
        letzteWahl = wahl;
        double ost = ka.ost() + WalzeCheck.KANTE_ABSTAND * ka.nx(), nord = ka.nord() + WalzeCheck.KANTE_ABSTAND * ka.ny();
        double[] wa = b.wasserUndSeegang(ost, nord);
        double eta = wa != null && Double.isFinite(wa[0]) ? wa[0] : 0;
        Blick bl = new Blick(ost, nord, eta + WalzeCheck.NAH_HOEHE, Math.toDegrees(Math.atan2(-ka.nx(), -ka.ny())), WalzeCheck.NAH_NEIGUNG, 60);
        bl.groesse(breite, hoehe);
        A.info("Blick auf die Kante, " + name, f("Kante %d (Art %d, Fuß %.2f m, Krone %.2f m NHN, 1:%.1f) bei (%.0f, %.0f) mit Hs am Fuß %.2f m und Tp %.1f s (die höchste von %d aktiven); Auge %.0f m seewärts in %.1f m NHN, Richtung %.0f°",
                (int) wahl[0], ka.art(), ka.fuss(), ka.krone(), 1 / ka.neigung(), ka.ost(), ka.nord(), wahl[6], wahl[7], aktiv, WalzeCheck.KANTE_ABSTAND, bl.hoehe, bl.richtung));
        return bl;
    }

    /**
     * Seit S6c-2 (Seegang Werkbuch 82, SF93 A): das Bild des Rechtecks (swash_bild.comp, seit S6c-2b auch swash_stufe) nach
     * dem letzten Bild gegen den Löser, nach {@code runter}.
     * <ul>
     * <li>Seit S6c-2b (Seegang Werkbuch 83): der Spülsaum, wie er gezeichnet wird (die Zeilen rechnet die Karte nur jedes
     * {@code SAUM_JE}-te Bild), gegen die Auflaufgrenze des Lösers je Zeile – von der See her die letzte Zelle, deren
     * höchster Stand mindestens die Kontur über dem Grund lag (± {@link #SAUM_TOL} m in {@link #SAUM_ANTEIL} der Zeilen).
     * Bis S6c-2 gegen die Linie, an der der höchste Stand der Zeile den Grund schneidet; die nahm Senken hinter der Krone
     * mit, in die nie Wasser kam, und lag landwärts der Spitze, wo eine Walze über der Spitze höher stand (Befund L4o:
     * 0,84 und 0,80). Sie bleibt Auskunft, mit den Zeilen, in denen der Auflauf die Krone überlief.</li>
     * <li>Danach die Zeilen neu und alle Werte bitgleich mit {@code SwashKarte.bildReferenz} (Nässe gegen den Löser).</li>
     * </ul>
     */
    static void bildPruefen(Attest A, Auflauf au, Swash s, String name) {
        if (!au.bildGilt()) { A.pruefe(false, "Bild des Rechtecks gerechnet (S6c-2), " + name, "nein", "ja"); return; }
        com.dan.physics.swash.gpu.SwashKarte k = au.karte();
        float[] gz = k.bildLesen(), rb = com.dan.physics.swash.gpu.SwashKarte.bildReferenz(s, k.zeit(), Auflauf.KONTUR_BILD);
        int nZ = 4 * s.nx * s.ny;
        // 1 Spülsaum wie gezeichnet gegen die Auflaufgrenze der Referenz
        int mitSaum = 0, nah = 0, ueber = 0, alteNah = 0, alteZ = 0;
        double[] d = new double[s.ny];
        int nd = 0;
        for (int j = 0; j < s.ny; j++) {
            double xg = com.dan.physics.swash.gpu.SwashKarte.saumAusBild(gz[nZ + 4 * j], s.dx), xr = com.dan.physics.swash.gpu.SwashKarte.saumAusBild(rb[nZ + 4 * j], s.dx);
            if (Double.isNaN(xr) && Double.isNaN(xg)) continue;
            mitSaum++;
            double dd = Double.isNaN(xr) || Double.isNaN(xg) ? 1e9 : Math.abs(xg - xr);
            d[nd++] = dd;
            if (dd <= SAUM_TOL) nah++;
            // Auskunft: die Regel bis S6c-2 (höchster Stand der Zeile schneidet den Grund) und ob der Auflauf die Krone überlief
            double r = s.auflauf(j);
            int ik = 0;
            double krone = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < s.nx; i++) { double bb = s.b[s.id(i, j)]; if (!Double.isNaN(bb) && bb > krone) { krone = bb; ik = i; } }
            boolean lief = !Double.isNaN(xr) && xr >= s.x(ik);
            if (lief) ueber++;
            if (Double.isFinite(r) && !lief) {
                int iw = -1;
                for (int i = 0; i < s.nx; i++) { int c = s.id(i, j); if (!Double.isNaN(s.b[c]) && s.b[c] <= s.bezug + r) iw = i; }
                if (iw >= 0 && !Double.isNaN(xg)) { alteZ++; if (Math.abs(xg - s.x(iw)) <= SAUM_TOL) alteNah++; }
            }
        }
        double[] ds = Arrays.copyOf(d, nd);
        Arrays.sort(ds);
        A.info("Spülsaum je Zeile (Auskunft), " + name, f("in %d von %d Zeilen; Abstand des gezeichneten zur Auflaufgrenze des Lösers%s; der Auflauf lief in %d Zeilen über die Krone; Regel bis S6c-2 (höchster Stand schneidet den Grund) in den übrigen: %d von %d Zeilen ± %.1f m",
                mitSaum, s.ny, nd > 0 ? f(" im Median %.2f m, 95 %% %.2f m", ds[nd / 2], ds[Math.min(nd - 1, (int) (0.95 * nd))]) : "", ueber, alteNah, alteZ, SAUM_TOL));
        A.pruefe(mitSaum > 0 && nah >= SAUM_ANTEIL * mitSaum, f("Spülsaum wie gezeichnet an der Auflaufgrenze des Lösers (± %.1f m), %s", SAUM_TOL, name),
                mitSaum > 0 ? f("%.3f von %d Zeilen", (double) nah / mitSaum, mitSaum) : "keine Zeile mit Auflauf", f("≥ %.2f", SAUM_ANTEIL));
        // 2 Zeilen neu, dann alles bitgleich
        com.dan.core.gpu.Geraet g = k.geraet();
        com.dan.core.gpu.Ablauf ab = new com.dan.core.gpu.Ablauf(g);
        k.bildZeilen(ab);
        g.rechnen.einmal(c -> ab.aufnehmen(c, null));
        g.ruhe();
        float[] gb = k.bildLesen();
        int anders = 0, film = 0, netz = 0, je = 0;
        for (int i = 0; i < gb.length; i++) if (Float.floatToRawIntBits(gb[i]) != Float.floatToRawIntBits(rb[i])) anders++;
        for (int c = 0; c < s.nx * s.ny; c++) {
            if (gb[4 * c] >= 0.0005f) film++;
            if (gb[4 * c] >= 0.03f) netz++;
            if (gb[4 * c + 2] > -1e29f) je++;
        }
        A.pruefe(anders == 0, "Bild des Rechtecks gegen den Löser (h, Stand, zuletzt nass, versickert; Spülsaum je Zeile), " + name,
                f("%d von %d Werten anders", anders, gb.length), "0 (bitgleich)");
        A.info("Bild des Rechtecks (Auskunft), " + name, f("Film ab 0,5 mm in %d Zellen, Wasser des Netzes ab 3 cm in %d, je nass gewesen %d von %d", film, netz, je, s.nx * s.ny));
    }

    /**
     * Seit Datei 83: Kosten je Bild (ms) für Antrieb und Löser ohne das Bild des Auflaufs (bildAn aus), über
     * {@link #KOSTEN_OHNE_S} s nach {@link #VORLAUF} Bildern Anlauf ab der Zeit t; NaN ohne Zeitstempel. Danach ist das Bild
     * wieder an (es füllt sich beim nächsten Bild neu).
     */
    /** Seit S6d-2 (L4v/L4w: Xaver ohne Bild 1,38 / 1,94 ms gegen 0,96 mit): Schritte je Bild in der Messung ohne Bild (Auskunft). */
    static double schritteOhneBild = Double.NaN;
    static String zeitenOhneBild = "";

    static double kostenOhneBild(Auflauf au, Geraet g, SeegangKarte k, Blick bl, double t) {
        au.bildAn = false;
        int n = KOSTEN_OHNE_S * BILDER_JE_S, gem = 0;
        long s0 = au.schritte;
        double ms = 0;
        try (Stoppuhr uhr = new Stoppuhr(g, g.rechnen, 96)) {
            for (int q = 1; q <= n + VORLAUF; q++) {
                double tq = t + (double) q / BILDER_JE_S;
                Ablauf ab = new Ablauf(g);
                k.zeit(ab, tq);
                k.bild(ab, bl.ost, bl.nord);
                au.vorbereiten(ab, null, k, tq);
                boolean m = q > VORLAUF;
                g.rechnen.einmal(c -> { if (m) uhr.runde(c); ab.aufnehmen(c, m ? uhr : null); });
                if (m) { uhr.sammle(true); gem++; }
            }
            schritteOhneBild = (double) (au.schritte - s0) / (n + VORLAUF);
            if (!uhr.verfuegbar) return Double.NaN;
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, Double> e : uhr.summen().entrySet())
                if (e.getKey().startsWith("swash") || e.getKey().startsWith("see_antrieb")) { ms += e.getValue(); sb.append(f("%s%s %.3f", sb.length() == 0 ? "" : ", ", e.getKey(), e.getValue() / Math.max(1, gem))); }
            zeitenOhneBild = sb.toString();
        } finally {
            au.bildAn = true;
        }
        return gem > 0 ? ms / gem : Double.NaN;
    }

    /** Die Zelle mit dem höchsten Tempo (|u| + √(g h)) im Stand der Referenz nach {@code runter}: Lage, Tiefe, Geschwindigkeit, Grund, Krone davor. */
    static String ortDesTempos(Swash s, double ruhe, double land) {
        int cm = -1, im = 0, jm = 0;
        double vm = 0;
        for (int j = 0; j < s.ny; j++)
            for (int i = 0; i < s.nx; i++) {
                int c = s.id(i, j);
                double hh = s.h[c];
                if (hh <= s.hTrocken) continue;
                double[] u = s.geschwindigkeit(i, j);
                double v = Math.max(Math.abs(u[0]), Math.abs(u[1])) + Math.sqrt(s.schwere * hh);
                if (v > vm) { vm = v; cm = c; im = i; jm = j; }
            }
        if (cm < 0) return "keine nasse Zelle";
        double krone = Double.NEGATIVE_INFINITY;
        int ik = 0;
        for (int i = 0; i <= im; i++) { double b = s.b[s.id(i, jm)]; if (!Double.isNaN(b) && b > krone) { krone = b; ik = i; } }
        double[] u = s.geschwindigkeit(im, jm);
        boolean hinter = ik < im && s.b[cm] < krone - 0.05 && krone > ruhe;
        return f("%.2f m/s in Zelle (%d, %d), %+.1f m quer zur Wasserlinie bei P, Tiefe %.4f m, u %+.2f m/s quer, v %+.2f m/s längs, Grund %+.2f m über dem Ruhewasser; höchster Grund seewärts in der Zeile %+.2f m bei %+.1f m%s",
                vm, im, jm, s.x(im) - (s.nx * s.dx - land), s.h[cm], u[0], u[1], s.b[cm] - ruhe, krone - ruhe, s.x(ik) - (s.nx * s.dx - land), hinter ? " – hinter der Krone (Überlauf)" : "");
    }

    static void fall(Attest A, Buehne b, Auflauf au, Szenen sz, Szenario sc, Blick bl, double t0, double dauer, boolean muss, String name) throws Exception {
        Geraet g = b.geraet();
        Seegangsflaeche sf = Seegangsflaeche.baue(g, b.konto(), sz.gebiet(), sc.archiv.toPath(), b.seegangsnetze(), bl, t0, null);
        try {
            A.info("Fläche, " + name, sf.bericht);
            SeegangKarte k = sf.karte;
            k.bezug(Math.floor(bl.ost / 64) * 64, Math.floor(bl.nord / 64) * 64);
            k.fernOst = bl.ost;
            k.fernNord = bl.nord;
            au.vergiss();
            long ts = System.nanoTime();
            boolean gesetzt = au.vorBild(bl, t0, 1, b.seegangsnetze());
            double msSetzen = (System.nanoTime() - ts) * 1e-6;
            A.info("Setzen, " + name, f("%s (%.0f ms)", au.bericht, msSetzen));
            if (muss) A.pruefe(gesetzt, "Rechteck gesetzt, " + name, gesetzt ? "ja" : "nein", "ja");
            if (!gesetzt) return;
            Swash s = au.swash();
            double v0 = s.volumen(), ruhe = s.bezug;
            // seit S6b-3 (SF69 A, SF70 A): je Zeile in Ruhe die Wasserlinie (Abstand zur Landseite) und ob die Landseite
            // hinter einer Krone liegt; der Prüfstein der landseitigen Spalte zählt nur Zeilen, für die das Rechteck gelegt ist
            double[] abstand = new double[s.ny];
            boolean[] eTrocken = new boolean[s.ny], hinterKrone = new boolean[s.ny];
            for (int j = 0; j < s.ny; j++) {
                int i = 0;
                while (i < s.nx && !(s.b[s.id(i, j)] >= ruhe) && !Double.isNaN(s.b[s.id(i, j)])) i++;
                abstand[j] = i < s.nx ? s.x(s.nx - 1) - s.x(i) : Double.NaN;
                double bE = s.b[s.id(s.nx - 1, j)], zMax = Double.NEGATIVE_INFINITY;
                eTrocken[j] = Double.isNaN(bE) || bE >= ruhe;
                for (int ii = i; ii < s.nx; ii++) { double bz = s.b[s.id(ii, j)]; if (!Double.isNaN(bz)) zMax = Math.max(zMax, bz); }
                hinterKrone[j] = !Double.isNaN(bE) && zMax - bE >= Auflauf.KRONE_ABFALL;
            }
            long verpasst0 = au.verpasst, schritte0 = au.schritte;
            int n = (int) Math.round(dauer * BILDER_JE_S), gemessen = 0, orte = 0;
            double msAuflauf = 0, msAlles = 0, tempoMax = 0, tempoMaxT = 0, ortSchwelle = TEMPO_ORT;
            int tempoMaxZeile = -1;
            String ort = "nie über " + TEMPO_ORT + " m/s";
            double[] tempi = new double[n + 1];
            int[] schritteJe = new int[Auflauf.SCHRITTE_MAX + 2];
            StringBuilder reihe = new StringBuilder();
            double land = au.lage()[7];
            try (Stoppuhr uhr = new Stoppuhr(g, g.rechnen, 96)) {
                for (int q = 0; q <= n; q++) {
                    double t = t0 + (double) q / BILDER_JE_S;
                    Ablauf ab = new Ablauf(g);
                    k.zeit(ab, t);
                    k.bild(ab, bl.ost, bl.nord);
                    long sv = au.schritte;                                  // seit S6b-3 (F2): vor dem Bild, vorher danach
                    au.vorbereiten(ab, null, k, t);
                    boolean m = q >= VORLAUF;
                    g.rechnen.einmal(c -> { if (m) uhr.runde(c); ab.aufnehmen(c, m ? uhr : null); });
                    if (m) { uhr.sammle(true); gemessen++; }
                    // Diagnose (Auskunft): Tempo je Bild mit der Zeile, Schritte je Bild, Ort der Spitzen, Zeitreihe des Wassers
                    schritteJe[(int) Math.min(schritteJe.length - 1, au.schritte - sv)]++;
                    float[] tz = au.karte().tempoZeilen();
                    int jm = 0;
                    for (int j = 1; j < tz.length; j++) if (tz[j] > tz[jm]) jm = j;
                    tempi[q] = tz[jm];
                    if (tz[jm] > tempoMax) { tempoMax = tz[jm]; tempoMaxZeile = jm; tempoMaxT = t - t0; }
                    if (tz[jm] > ortSchwelle && orte < ORTE_MAX) {
                        au.karte().runter();
                        ort = f("%.1f s: %s", t - t0, ortDesTempos(s, ruhe, land));
                        ortSchwelle = tz[jm] * 1.1;
                        orte++;
                    }
                    if (q > 0 && q % Math.round(REIHE_S * BILDER_JE_S) == 0) {
                        au.karte().runter();
                        reihe.append(f("%s%.0f s: ΔV %.1f, Zufluss %.1f, versickert %.1f m³", reihe.length() == 0 ? "" : "; ", t - t0, s.volumen() - v0, s.zufluss(), s.versickert()));
                    }
                }
                if (uhr.verfuegbar)
                    for (java.util.Map.Entry<String, Double> e : uhr.summen().entrySet()) {
                        msAlles += e.getValue();
                        if (e.getKey().startsWith("swash") || e.getKey().startsWith("see_antrieb")) msAuflauf += e.getValue();
                    }
                if (uhr.verfuegbar) {
                    StringBuilder sb = new StringBuilder();
                    for (java.util.Map.Entry<String, Double> e : uhr.summen().entrySet())
                        if (e.getKey().startsWith("swash") || e.getKey().startsWith("see_antrieb")) sb.append(f(", %s %.3f", e.getKey(), e.getValue() / gemessen));
                    A.info("Zeiten je Bild, " + name, f("Antrieb und Löser %.3f ms%s; das ganze Bild ohne Netz %.3f ms", msAuflauf / gemessen, sb, msAlles / gemessen));
                }
            }
            A.pruefe(au.an, "Auflauf nach den Bildern noch an (kein Fehler), " + name, au.an ? "ja" : au.bericht, "ja");
            au.karte().runter();
            bildPruefen(A, au, s, name);
            long schritte = au.schritte - schritte0;
            A.info("Schritte, " + name, f("%d in %d Bildern (%.2f je Bild, %.1f ms Modellzeit je Schritt), Tempo zuletzt %.2f m/s", schritte, n, (double) schritte / n, 1000.0 * dauer / schritte, au.karte().tempoLesen()));
            StringBuilder sj = new StringBuilder();
            for (int i = 0; i < schritteJe.length; i++) if (schritteJe[i] > 0) sj.append(f("%s%d%s: %d", sj.length() == 0 ? "" : ", ", i, i == schritteJe.length - 1 ? "+" : "", schritteJe[i]));
            double[] tso = Arrays.copyOf(tempi, tempi.length);
            Arrays.sort(tso);
            A.info("Tempo je Bild (Auskunft, SF66 A), " + name, f("Median %.2f, 99 %% %.2f, höchstes %.2f m/s nach %.1f s in Zeile %d; Bilder je Zahl der Schritte: %s", tso[tso.length / 2], tso[(int) (0.99 * (tso.length - 1))], tempoMax, tempoMaxT, tempoMaxZeile, sj));
            A.info("Ort des Tempos über " + TEMPO_ORT + " m/s (zuletzt die höchste gelesene Spitze, " + orte + " gelesen), " + name, ort);
            A.info("Zeitreihe des Wassers (Auskunft), " + name, reihe.toString());
            float[] rw = au.randwelle().lies();
            int np = (int) Math.ceil(s.ny * s.dx / Auflauf.PUNKT_ABSTAND) + 1;                // seit S6d-2: mit der Zelle des Rechtecks
            double m0 = 0;
            for (int i = 0; i < np; i++) m0 += rw[8 * i + 3];
            A.info("Seegang am seeseitigen Rand (Auskunft), " + name, f("Hs aus der Synthese 4 √m₀ %.2f m (Mittel über %d Punkte), aus den Netzen beim Setzen %.2f m, Tp %.1f s", 4 * Math.sqrt(Math.max(0, m0 / np)), np, au.hs, au.tp));
            double v = s.volumen(), z = s.zufluss(), vs = s.versickert(), ag = s.abgeschnitten();
            double rest = (v - v0 - z + vs - ag) / v0;
            A.info("Wasser, " + name, f("V₀ %.1f m³, V %.1f m³; Zufluss %.2f m³ (%.4f m³ je m Ufer und s), versickert %.2f m³, abgeschnitten %.3g m³", v0, v, z, z / (s.ny * s.dx * dauer), vs, ag));
            A.hoechstens("Bilanz des Wassers, relativ, " + name, rest, BILANZ, "");
            int nassSee = 0, seeInRuhe = 0, landZeilen = 0, landTrocken = 0, inRuheTrocken = 0, zuNah = 0, kroneZeilen = 0, nassAlle = 0;
            double ll = au.landLinie;
            java.util.List<Double> nassAbstand = new java.util.ArrayList<>();
            for (int j = 0; j < s.ny; j++) {
                // seit S6d-2: nur Zeilen, deren seeseitige Zelle in Ruhe unter dem Spiegel liegt (am Watt liegen Sände am Rand; am Strand alle)
                double b0 = s.b[s.id(0, j)];
                if (!Double.isNaN(b0) && b0 < ruhe) { seeInRuhe++; if (s.tiefe(0, j) > s.hTrocken) nassSee++; }
                if (!eTrocken[j]) continue;
                inRuheTrocken++;
                boolean nass = !Double.isNaN(s.etaMax[s.id(s.nx - 1, j)]);
                if (nass) { nassAlle++; nassAbstand.add(abstand[j]); }
                if (hinterKrone[j]) { kroneZeilen++; continue; }
                if (!(abstand[j] >= ll - 0.5 * s.dx)) { zuNah++; continue; }
                landZeilen++;
                if (!nass) landTrocken++;
            }
            double aSee = (double) nassSee / Math.max(1, seeInRuhe), aLand = landZeilen > 0 ? (double) landTrocken / landZeilen : Double.NaN;
            A.pruefe(aSee >= NASS_SEE, "seeseitiger Rand am Ende nass (Zeilen, deren Rand in Ruhe unter dem Spiegel liegt), " + name, f("%.3f von %d Zeilen", aSee, seeInRuhe), f("≥ %.2f", NASS_SEE));
            java.util.Collections.sort(nassAbstand);
            A.info("Landseite in Ruhe (Auskunft, SF69 A), " + name, f("%d von %d Zeilen trocken; davon %d hinter einer Krone (Überlauf), %d mit der Wasserlinie näher als %.1f m (2 R / tan β) an der Landseite; landseitig nass wurden %d%s",
                    inRuheTrocken, s.ny, kroneZeilen, zuNah, ll, nassAlle, nassAbstand.isEmpty() ? "" : f(", ihre Wasserlinie in Ruhe %.1f bis %.1f m (Median %.1f m) vor der Landseite", nassAbstand.get(0), nassAbstand.get(nassAbstand.size() - 1), nassAbstand.get(nassAbstand.size() / 2))));
            A.pruefe(landZeilen == 0 || aLand >= TROCKEN_LAND, "landseitige Spalte nie nass, " + name, landZeilen == 0 ? "keine Zeile, für die das Rechteck gelegt ist" : f("%.3f von %d Zeilen (in Ruhe trocken, nicht hinter einer Krone, Wasserlinie mindestens %.1f m vor der Landseite)", aLand, landZeilen, ll), f("≥ %.2f", TROCKEN_LAND));
            double[] ue = au.karte().randZufluss(Swash.E);
            double aus = 0;
            for (double x : ue) aus -= x;
            A.info("Überlauf über die Landseite (Auskunft, SF70 A), " + name, f("%.3f m³ in %.0f s, %.2e m³ je m Ufer und s%s", aus, dauer, aus / (s.ny * s.dx * dauer), Double.isNaN(au.kroneX) ? "; keine Krone im Rechteck" : f("; Krone %.2f m über dem Ruhewasser, %.0f m landwärts von P", au.kroneZ - ruhe, au.kroneX)));
            A.pruefe(au.verpasst == verpasst0, "kein Bild mit zu wenig Schritten, " + name, f("%d", au.verpasst - verpasst0), "0");
            double[] r = new double[s.ny];
            int nr = 0;
            for (int j = 0; j < s.ny; j++) { double x = s.auflauf(j); if (Double.isFinite(x)) r[nr++] = x; }
            r = Arrays.copyOf(r, nr);
            Arrays.sort(r);
            double[] lage = au.lage();
            A.info("Auflauf (Auskunft; Maßstab in S6b-3), " + name, nr == 0 ? "in keiner Zeile über die Wasserlinie" :
                    f("höchster %.2f m über dem Ruhewasser; je Zeile (%d von %d): Median %.2f m, 98 %% %.2f m; geschätzt R2 %% %.2f m (Stockdon/EurOtop, tan β %.3f)",
                            s.auflauf(), nr, s.ny, r[nr / 2], r[Math.min(nr - 1, (int) (0.98 * nr))], lage[6], lage[5]));
            double ms = gemessen > 0 ? msAuflauf / gemessen : Double.NaN;
            // seit Datei 83 (S6c-2b): die Kosten getrennt – der Löser wie in S6b (ohne das Bild des Auflaufs) gegen 1 ms, das
            // Bild (seit S6c-2b in swash_stufe mitgeschrieben, dazu swash_bild) gegen KOSTEN_BILD_MS. In L4o–L4r zählte das Bild
            // im selben Prüfstein mit (1,14 → 1,02 ms). Gemessen in KOSTEN_OHNE_S s nach dem Fall, ohne Bild
            double msOhne = kostenOhneBild(au, g, k, bl, t0 + dauer);
            A.info("Messung ohne das Bild des Auflaufs (Auskunft), " + name, f("%.2f Schritte je Bild (im Fall %.2f); je Bild %s", schritteOhneBild, (double) schritte / Math.max(1, n), zeitenOhneBild));
            String gn = g.name.toLowerCase(java.util.Locale.ROOT);
            boolean software = gn.contains("llvmpipe") || gn.contains("lavapipe") || gn.contains("swiftshader");
            if (!(ms >= 0)) A.info("Zeitstempel, " + name, "nicht verfügbar");
            else if (software) A.info("Kosten je Bild, Antrieb und Löser (Software-Gerät, nur Auskunft), " + name, f("%.3f ms mit dem Bild, %.3f ms ohne", ms, msOhne));
            else {
                A.info("Kosten je Bild mit dem Bild des Auflaufs (Auskunft), " + name, f("%.3f ms", ms));
                A.hoechstens(f("Kosten je Bild, Antrieb und Löser ohne das Bild des Auflaufs (wie S6b), bei %d Bildern je s, %s", BILDER_JE_S, name), msOhne, KOSTEN_MS, "ms");
                A.hoechstens(f("Kosten je Bild des Bilds des Auflaufs (mit gegen ohne), %s", name), ms - msOhne, KOSTEN_BILD_MS, "ms");
            }
        } finally {
            b.geraet().ruhe();
            sf.close();
        }
    }
}
