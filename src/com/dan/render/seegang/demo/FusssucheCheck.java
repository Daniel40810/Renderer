package com.dan.render.seegang.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.szene.Brandung;
import com.dan.core.szene.Lippenpruefung;
import com.dan.core.szene.Fusswellen;
import com.dan.core.szene.Kanten;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Profilpuffer;
import com.dan.render.seegang.Fusssuche;
import com.dan.render.seegang.SeegangKarte;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S5b-4 (Seegang Werkbuch, Datei 46): die Kämme am Fuß der Kanten ({@link Fusssuche}, {@code see_fuss.comp})
 * und die Brandung, die ihnen folgt ({@link Brandung} mit {@link Fusswellen}), in der Szene von {@link
 * SeegangKarteCheck} (fein, grob, Fernfeld; Rampe 20 m bis trocken; Seegang Hs 1,4 m, Tp 6,5 s nach 20°).
 * <ol>
 * <li>Karte gegen Referenz: 16 Füße je auf den Linien von 8, 4 und 2 m Tiefe, je ein Auge davor, zu drei Zeiten (bis drei
 * Tage): je Fuß die drei Kämme (Zeit des Durchgangs, H = 2 |Z'|, lineare Höhe 2 |Z|) gegen die Kämme der Referenz in
 * double ({@link Oberflaeche#punkt} mit der Auflösung der Stufe, 20 Hz); H_s und T_m01 am Fuß.</li>
 * <li>Folge über 30 s zu 30 Bildern je s (Ring wie in der Anwendung, das Ergebnis drei Bilder später): jeder Kamm der
 * Referenz genau einmal in der Folge, Zeit und Höhe.</li>
 * <li>Brandung an 16 Kanten (Deckwerk 1:4) an den Füßen der 2-m-Linie, 20 s: alle Abschnitte folgen den Kämmen der
 * Fläche; jede Welle schlägt Laufzeit nach ihrem Durchgang am Fuß auf (gegen die Referenz); in der Pause bitgleich; nach
 * einem Sprung der Zeit zuerst die eigene Folge, dann wieder die Fläche.</li>
 * <li>Überlauf aus der Fläche gegen EurOtop 2018 (nur Prozessor): 6 Füße auf der 2-m-Linie, je 1 800 s Kämme der
 * Referenz; Rang aus 2 |Z| / H_s der gezeichneten Wellen, Abstand der Wellen T_m01.</li>
 * <li>Rechenzeit der Suche für 500 aktive Abschnitte wie in der Anwendung (je Bild jeder vierte Fuß, nur der letzte Kamm;
 * auf der 680M höchstens {@link #ZEIT_MS} ms, auf lavapipe Auskunft), zum Vergleich alle Füße mit drei Kämmen.</li>
 * <li>Seit S5b-5 (Datei 50): Lippen an aneinanderliegenden Abschnitten (Deckwerk 1:4) auf der 2-m-Linie, je 20 s mit
 * den Enden mit den Nachbarn geteilt und zum Vergleich jeder Abschnitt für sich: sichtbare Kanten (Kacheln, B45).
 * Seit S5b-5b (Datei 51): die Füße wie in den Kanten des Gebiets (steilster Punkt je Zelle von 5 m, Zickzack am Hang,
 * Richtung gestreut); Auswertung {@link Lippenpruefung}. Seit S5b-5 ohne Lippe abgeschlossen (Datei 55): nur Auskunft.</li>
 * </ol>
 * Teil 1 sucht alle Füße mit drei Kämmen je Suche; die Teile 2, 3, 5 und 6 wie die Anwendung (seit S5b-4c, Datei 48).
 * Attest {@code docs/status/S5b4_Fusssuche.md}. Aufruf: {@code FusssucheCheck [Teil …]}.
 */
public final class FusssucheCheck {
    static final Attest A = new Attest("com.dan.render.seegang (Fusssuche)", "Seegang Werkbuch");
    /** Grenzen [G], vor der Messung gesetzt. */
    static final double T_TOL = 0.05, H_TOL_REL = 0.03, H_TOL_ABS = 0.02, ANTEIL_MIN = 0.95, KLEIN = 0.1, HS_TOL = 0.02, TM_TOL = 0.02;
    static final double FOLGE_TOL = 0.1, LAUF_TOL = 0.1, Q_TOL = 0.3, POV_TOL = 0.25;
    /**
     * Seit S5b-5: sichtbare Kante an den Grenzen der Abschnitte (Abstand eines sichtbaren Endes zur Lippe des Nachbarn
     * oder seine Höhe über Wasser und Grund), 95 % höchstens (m) [G].
     */
    static final double KANTE_95 = 0.15;
    /** Seit S5b-5d (Datei 52, vor der Messung): Zerfall – Anteil der sichtbaren Länge in Stücken unter 10 m höchstens, Median der Stücklänge mindestens (m) [G]. */
    static final double STUECK_KURZ = 0.10, STUECK_MEDIAN = 20;
    /** Seit S5b-4b (Befund L3a: 1,66 ms): Rechenzeit der Suche für 500 Füße auf der Karte höchstens (ms) [G]; auf lavapipe Auskunft. */
    static final double ZEIT_MS = 0.5;
    static final double[] TIEFEN = {8, 4, 2};
    static final int FUESSE = 16, TAKT = 30;
    /** Wie in der Anwendung (Fusssuche.teile): je Bild jeder vierte Fuß, nur der letzte Kamm (Teile 2, 3, 5). */
    static final int TEILE = 4;
    static final double ABSTAND = 12, TAKT_REF = 20, X0 = 463500, Y0 = 6043500, YM = 2500;
    /** Kante der Brandung in Teil 3: Fuß bei −2 m NHN (Wasser 0 m), Krone 1,5 m, 1:4, glatt, Deckwerk. */
    static final double FUSS = -2.0, KRONE = 1.5, NEIGUNG = 0.25, RAU = 1.0;

    static Profilpuffer puffer;
    static Oberflaeche ob;
    static SeegangKarte karte;
    static Fusssuche suche;
    static Geraet g;

    private FusssucheCheck() { }

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        java.util.Set<String> nur = new java.util.HashSet<>(Arrays.asList(args));
        System.out.println("FusssucheCheck – S5b-4 (Seegang Werkbuch)");
        try {
            puffer = new Profilpuffer(SeegangKarteCheck.OM, SeegangKarteCheck.UNTEN, SeegangKarteCheck.OBEN, SeegangKarteCheck.ND, 0x5EE6A3L, false);
            ob = SeegangKarteCheck.szene(puffer);
            karte = SeegangKarte.mitGeraet(ob, SeegangKarteCheck.STUFEN, SeegangKarteCheck.N, SeegangKarteCheck.D0);
            g = karte.geraet();
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            A.info("Fusswellen", f("Version %s; gleich unter %.1f T_m01, behalten %.0f s, frisch %.0f s, Sprung ab %.0f s", Fusswellen.VERSION, Fusswellen.GLEICH, Fusswellen.BEHALTEN, Fusswellen.FRISCH, Fusswellen.SPRUNG));
            karte.eingaben();
            suche = new Fusssuche(g, g.konto("fuss", 64L << 20));
            Runnable[] teile = {FusssucheCheck::karteGegenReferenz, FusssucheCheck::folge, FusssucheCheck::brandung, FusssucheCheck::ueberlauf, FusssucheCheck::zeit, FusssucheCheck::lippen};
            for (int i = 0; i < teile.length; i++) {
                if (!nur.isEmpty() && !nur.contains(String.valueOf(i + 1))) continue;
                long ta = System.nanoTime();
                teile[i].run();
                System.out.printf("  [Teil %d: %.1f s]%n", i + 1, (System.nanoTime() - ta) * 1e-9);
            }
        } catch (Throwable e) {
            A.fehler(e);
        } finally {
            if (suche != null) suche.close();
            if (karte != null) karte.close();
        }
        A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", "S5b4_Fusssuche.md"), "S5b-4 – Kämme am Fuß der Kanten (Fußsuche)");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Szene

    static double tiefe(double x, double y) { return SeegangKarteCheck.tiefe(x - X0, y - Y0); }

    /** Fuß auf der Linie der Tiefe h bei y' (relativ zu Y0): {Ost, Nord, Normale seewärts (zum tieferen Wasser) Ost, Nord}. */
    static double[] fuss(double h, double yRel) {
        double x = (20 - h + 1.5 * Math.sin(yRel / 400)) / 0.0045;
        double gx = -0.0045, gy = 1.5 / 400 * Math.cos(yRel / 400), l = Math.hypot(gx, gy);   // Gefälle der Tiefe
        return new double[] {X0 + x, Y0 + yRel, gx / l, gy / l};
    }

    /** 16 Füße auf der Linie der Tiefe h, je ABSTAND m auseinander um YM. */
    static double[][] fuesse(double h) {
        double[][] r = new double[FUESSE][];
        for (int i = 0; i < FUESSE; i++) r[i] = fuss(h, YM + (i - (FUESSE - 1) / 2.0) * ABSTAND);
        return r;
    }

    /** Das Auge 20 m seewärts des mittleren Fußes. */
    static double[] auge(double[][] fu) {
        double[] m = fu[FUESSE / 2];
        return new double[] {m[0] + 20 * m[2], m[1] + 20 * m[3]};
    }

    /** Die Karte auf ein Auge: Bezug, Zeit, alle Stufen (danach bleiben die Mitten, {@link SeegangKarte#zeit} ändert sie nicht). */
    static void karteAuf(double[] auge, double t) {
        karte.bezug(Math.floor(auge[0] / 64) * 64, Math.floor(auge[1] / 64) * 64);
        karte.fernOst = auge[0];
        karte.fernNord = auge[1];
        karte.zeit(t);
        karte.stufen(auge[0], auge[1], null);
    }

    static Fusswellen.Anfrage anfrage(double[][] fu) {
        Fusswellen.Anfrage a = new Fusswellen.Anfrage();
        a.zahl = fu.length;
        a.kante = new int[fu.length];
        a.ost = new double[fu.length];
        a.nord = new double[fu.length];
        a.omega = new double[fu.length];
        for (int i = 0; i < fu.length; i++) {
            a.kante[i] = i;
            a.ost[i] = fu[i][0];
            a.nord[i] = fu[i][1];
            a.omega[i] = 2 * Math.PI / SeegangKarteCheck.TP;
        }
        return a;
    }

    /**
     * Kämme der Referenz am Ort von t0 bis t1 (TAKT_REF), mit der Auflösung d: je Kamm {Zeit, H = 2 |Z'|, 2 |Z|}. Ein
     * Kamm ist, wo θ = arg Z fallend durch 0 geht (die Änderung je Schritt modulo 2π, auch wo θ nahe einer Nullstelle
     * der Hülle schnell umläuft).
     */
    static List<double[]> kaemme(double x, double y, double t0, double t1, double d, double nah) {
        double aufl = ob.aufloesung;
        ob.aufloesung = d;
        Oberflaeche.Punkt p = new Oberflaeche.Punkt();
        List<double[]> l = new ArrayList<>();
        int n = (int) Math.ceil((t1 - t0) * TAKT_REF);
        double thV = Double.NaN, hV = 0, hlV = 0, tV = 0;
        for (int s = 0; s <= n; s++) {
            double t = t0 + s / TAKT_REF;
            ob.punkt(x, y, t, p, nah);
            double za = Math.hypot(p.zRe, p.zIm), th = za > 0 ? Math.atan2(p.zIm, p.zRe) : 0, h = 2 * p.s * p.huelle * za, hl = 2 * za;
            double dth = th - thV;
            dth -= 2 * Math.PI * Math.rint(dth / (2 * Math.PI));               // Änderung je Schritt, auch über ±π
            if (thV > 0 && dth < 0 && thV + dth <= 0) {
                double a = thV / -dth;
                l.add(new double[] {tV + a * (t - tV), hV + a * (h - hV), hlV + a * (hl - hlV)});
            }
            thV = th; hV = h; hlV = hl; tV = t;
        }
        ob.aufloesung = aufl;
        return l;
    }

    static double[] naechster(List<double[]> ref, double t) {
        double[] b = null;
        for (double[] r : ref) if (b == null || Math.abs(r[0] - t) < Math.abs(b[0] - t)) b = r;
        return b;
    }

    static boolean hoeheOk(double a, double b) { return Math.abs(a - b) <= Math.max(H_TOL_ABS, H_TOL_REL * b); }

    // ------------------------------------------------------------------ Teil 1

    static void karteGegenReferenz() {
        A.teil("1 Karte gegen Referenz");
        suche.teile = 1;                                                    // hier alle Füße und alle drei Kämme je Suche
        suche.alleKaemme = true;
        try { karteGegenReferenz1(); } finally { suche.teile = TEILE; suche.alleKaemme = false; }
    }

    static void karteGegenReferenz1() {
        double[] zeiten = {1000.3, 1037.6, 260000.7};
        int fuesseIn = 0, fuesseAlle = 0, kaemmeK = 0, gut = 0, ohne = 0, letzteR = 0, letzteGefunden = 0;
        double dtMax = 0, dhMax = 0, dhlMax = 0, hsMax = 0, tmMax = 0, gezMax = 0;
        int[] stufen = new int[9];
        Fusswellen fw = new Fusswellen();
        Oberflaeche.Punkt p = new Oberflaeche.Punkt();
        for (double h : TIEFEN) {
            double[][] fu = fuesse(h);
            double[] au = auge(fu);
            karteAuf(au, zeiten[0]);
            Fusswellen.Anfrage an = anfrage(fu);
            for (double t : zeiten) {
                karte.zeit(t);
                fw.leeren();
                suche.sofort(karte, t, an, fw);
                for (int i = 0; i < FUESSE; i++) {
                    fuesseAlle++;
                    Fusswellen.Folge fo = fw.folge(i, t);
                    if (fo == null || fo.stufe == 0) continue;
                    fuesseIn++;
                    int L = Math.abs(fo.stufe) - 1;
                    stufen[L]++;
                    double d = karte.abstand(L);
                    // H_s und T_m01 gegen die Referenz (m0 und die Bänder am Ort)
                    double aufl = ob.aufloesung;
                    ob.aufloesung = d;
                    ob.punkt(fu[i][0], fu[i][1], t, p);
                    ob.aufloesung = aufl;
                    double hsR = 4 * Math.sqrt(p.m0), gezR = 4 * Math.sqrt(p.gezeichnet);
                    hsMax = Math.max(hsMax, Math.abs(fo.hs / hsR - 1));
                    gezMax = Math.max(gezMax, Math.abs(fo.hsGezeichnet / gezR - 1));
                    double tmR = tm01(fu[i][0], fu[i][1]);
                    tmMax = Math.max(tmMax, Math.abs(fo.tm01 / tmR - 1));
                    List<double[]> ref = kaemme(fu[i][0], fu[i][1], t - 2.5 * SeegangKarteCheck.TP, t + 2.5 * SeegangKarteCheck.TP, d, 1);
                    // der letzte Kamm der Referenz vor t (über 0,1 H_s): hat ihn die Karte?
                    double[] letzter = null;
                    for (double[] r : ref) if (r[0] <= t && r[2] >= KLEIN * hsR) letzter = r;
                    if (letzter != null) {
                        letzteR++;
                        for (Fusswellen.Kamm k : fo.kaemme) if (Math.abs(k.zeit - letzter[0]) <= T_TOL) { letzteGefunden++; break; }
                    }
                    for (Fusswellen.Kamm k : fo.kaemme) {
                        if (k.linear < KLEIN * hsR) continue;
                        kaemmeK++;
                        double[] r = naechster(ref, k.zeit);
                        if (r == null || Math.abs(r[0] - k.zeit) > 0.25 * fo.tm01) { ohne++; continue; }
                        double dt = Math.abs(r[0] - k.zeit), dh = Math.abs(k.hoehe - r[1]), dhl = Math.abs(k.linear - r[2]);
                        dtMax = Math.max(dtMax, dt);
                        dhMax = Math.max(dhMax, dh);
                        dhlMax = Math.max(dhlMax, dhl);
                        if (dt <= T_TOL && hoeheOk(k.hoehe, r[1]) && hoeheOk(k.linear, r[2])) gut++;
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int l = 0; l < stufen.length; l++) if (stufen[l] > 0) sb.append(f("%sStufe %d: %d", sb.length() > 0 ? ", " : "", l, stufen[l]));
        double af = fuesseAlle > 0 ? (double) fuesseIn / fuesseAlle : 0;
        A.pruefe(af >= ANTEIL_MIN, f("Füße in der Clipmap mit gefundenen Kämmen (16 je Linie von %s m Tiefe, je drei Zeiten; %s)", "8, 4 und 2", sb), f("%.3f (%d von %d)", af, fuesseIn, fuesseAlle), f("≥ %.2f", ANTEIL_MIN));
        A.info("gefundene Kämme der Karte (ohne die unter " + f("%.1f", KLEIN) + " H_s)", f("%d; ohne Kamm der Referenz in einer Viertelperiode %d; größte Abweichung der Zeit %.3f s, von H = 2|Z'| %.3f m, von 2|Z| %.3f m", kaemmeK, ohne, dtMax, dhMax, dhlMax));
        double anteil = kaemmeK > 0 ? (double) gut / kaemmeK : 0;
        A.pruefe(anteil >= ANTEIL_MIN, f("Kämme der Karte, die zur Referenz passen (Zeit ≤ %.2f s, H und 2|Z| ≤ %.0f %% oder %.0f cm)", T_TOL, 100 * H_TOL_REL, 100 * H_TOL_ABS),
                f("%.3f (%d von %d)", anteil, gut, kaemmeK), f("≥ %.2f", ANTEIL_MIN));
        double al = letzteR > 0 ? (double) letzteGefunden / letzteR : 0;
        A.pruefe(al >= ANTEIL_MIN, f("letzter Kamm der Referenz vor der Zeit der Karte gefunden (± %.2f s)", T_TOL), f("%.3f (%d von %d)", al, letzteGefunden, letzteR), f("≥ %.2f", ANTEIL_MIN));
        A.hoechstens("H_s am Fuß gegen 4 √m0 der Referenz, größte relative Abweichung", hsMax, HS_TOL, "");
        A.hoechstens("H_s der gezeichneten Wellen am Fuß gegen 4 √(Σ E g²) der Referenz (seit S5b-4c, der Maßstab für 2 |Z|), größte relative Abweichung", gezMax, HS_TOL, "");
        A.hoechstens("T_m01 am Fuß gegen die Referenz (2π Σ E / Σ E ω), größte relative Abweichung", tmMax, TM_TOL, "");
    }

    /** T_m01 am Ort in double ({@link Oberflaeche#tm01}). */
    static double tm01(double x, double y) { return ob.tm01(x, y); }

    // ------------------------------------------------------------------ Teil 2

    static List<Fusswellen.Folge> folgeEnde;

    static void folge() {
        A.teil("2 Folge über 30 s, 30 Bilder je s");
        double[][] fu = fuesse(2);
        double[] au = auge(fu);
        double t1 = 5000.0;
        karteAuf(au, t1);
        Fusswellen fw = new Fusswellen();
        Fusswellen.Anfrage an = anfrage(fu);
        fw.anfragen(an.zahl, an.kante, an.ost, an.nord, an.omega);
        int bilder = 30 * TAKT;
        double tEnde = t1;
        for (int s = 0; s < bilder; s++) {
            tEnde = t1 + (double) s / TAKT;
            bild(fw, tEnde, null);
        }
        int refZahl = 0, einmal = 0, fehlt = 0, doppelt = 0, extra = 0, festGut = 0, festZahl = 0;
        double dtMax = 0, dhMax = 0;
        for (int i = 0; i < FUESSE; i++) {
            Fusswellen.Folge fo = fw.folge(i, tEnde);
            if (fo == null) { fehlt++; continue; }
            int L = Math.abs(fo.stufe) - 1;
            double hsR = fo.hs, von = t1 + 1, bis = tEnde - 0.5 * fo.tm01;
            List<double[]> ref = kaemme(fu[i][0], fu[i][1], t1 - 5, tEnde + 5, karte.abstand(L), 1);
            for (double[] r : ref) {
                if (r[0] < von || r[0] > bis || r[2] < KLEIN * hsR) continue;
                refZahl++;
                int m = 0;
                for (Fusswellen.Kamm k : fo.kaemme) if (Math.abs(k.zeit - r[0]) <= FOLGE_TOL) m++;
                if (m == 1) einmal++;
                else if (m == 0) fehlt++;
                else doppelt++;
            }
            for (Fusswellen.Kamm k : fo.kaemme) {
                if (k.zeit < von || k.zeit > bis || k.linear < KLEIN * hsR) continue;
                double[] r = naechster(ref, k.zeit);
                if (r == null || Math.abs(r[0] - k.zeit) > FOLGE_TOL) {
                    extra++;
                    if (System.getProperty("fuss.zeige") != null) System.out.printf("    zu viel: Fuß %d, Kamm %.3f s vor dem Ende, 2|Z| %.3f, fest %b; Referenz am nächsten %s%n", i, tEnde - k.zeit, k.linear, k.fest, r == null ? "-" : f("%.3f s vor dem Ende, 2|Z| %.3f", tEnde - r[0], r[2]));
                    continue;
                }
                if (k.fest) {
                    festZahl++;
                    dtMax = Math.max(dtMax, Math.abs(r[0] - k.zeit));
                    dhMax = Math.max(dhMax, Math.abs(r[2] - k.linear));
                    if (Math.abs(r[0] - k.zeit) <= T_TOL && hoeheOk(k.linear, r[2])) festGut++;
                }
            }
        }
        A.info("Folge", f("%d Suchen; im letzten Ergebnis %d Füße, %d in der Clipmap; %d Folgen", fw.suchen, fw.fuesseZuletzt, fw.gueltigZuletzt, fw.folgenZahl));
        A.info("Kämme der Referenz an den 16 Füßen (2 m), ohne die unter " + f("%.1f", KLEIN) + " H_s", f("%d; genau einmal in der Folge %d, fehlen %d, doppelt %d; Kämme der Folge ohne Kamm der Referenz %d", refZahl, einmal, fehlt, doppelt, extra));
        double anteil = refZahl > 0 ? (double) einmal / refZahl : 0;
        A.pruefe(anteil >= ANTEIL_MIN && extra == 0, f("Kämme der Referenz genau einmal in der Folge (± %.1f s), keiner zu viel", FOLGE_TOL), f("%.3f, zu viel %d", anteil, extra), f("≥ %.2f, 0", ANTEIL_MIN));
        double af = festZahl > 0 ? (double) festGut / festZahl : 0;
        A.pruefe(af >= ANTEIL_MIN, f("feste Kämme der Folge gegen die Referenz (Zeit ≤ %.2f s, 2|Z| ≤ %.0f %% oder %.0f cm; größte Abweichung %.3f s, %.3f m)", T_TOL, 100 * H_TOL_REL, 100 * H_TOL_ABS, dtMax, dhMax),
                f("%.3f (%d von %d)", af, festGut, festZahl), f("≥ %.2f", ANTEIL_MIN));
    }

    /** Ein Bild wie in der Anwendung: Zeit der Karte, Ergebnis von vor drei Bildern an die Folge, neue Suche; dann die Brandung. */
    static void bild(Fusswellen fw, double t, Runnable danach) {
        karte.zeit(t);
        Ablauf a = new Ablauf(g);
        suche.lies(fw);
        suche.suche(a, karte, t, fw.anfrage());
        g.rechnen.einmal(c -> a.aufnehmen(c, null));
        if (danach != null) danach.run();
    }

    // ------------------------------------------------------------------ Teil 3

    static void brandung() {
        A.teil("3 Brandung an 16 Kanten (Deckwerk 1:4), 20 s");
        double[][] fu = fuesse(2);
        double[] au = auge(fu);
        double t2 = 7000.0;
        karteAuf(au, t2);
        List<Kanten.Kante> l = new ArrayList<>();
        for (double[] x : fu) l.add(new Kanten.Kante(x[0], x[1], x[2], x[3], FUSS, KRONE, NEIGUNG, RAU, 5));
        Kanten kanten = new Kanten(l);
        Oberflaeche.Punkt p = new Oberflaeche.Punkt();
        ob.punkt(fu[FUESSE / 2][0], fu[FUESSE / 2][1], t2, p);
        double hsF = 4 * Math.sqrt(p.m0), wo = Math.toRadians(SeegangKarteCheck.WOHIN);
        Brandung.Wasser w = new Brandung.Wasser() {
            @Override public float eta(double o, double n) { return 0f; }
            @Override public double[] seegang(double o, double n) { return new double[] {hsF, SeegangKarteCheck.TP, Math.cos(wo), Math.sin(wo), 0.01, 2}; }
        };
        Brandung b = new Brandung(Math.round(au[0]), Math.round(au[1]));
        b.protokollieren = true;
        b.netzBereich = new double[] {au[0] - 500, au[0] + 500, au[1] - 500, au[1] + 500};
        Fusswellen fw = new Fusswellen();
        b.fuesse = fw;
        A.info("Kanten und Seegang", f("16 Abschnitte auf der 2-m-Linie, Fuß %.1f m, Krone %.1f m NHN, 1:%.0f, γ_f %.1f; Wasser 0 m NHN, Hs %.2f m (4 √m0 am mittleren Fuß), Tp %.1f s nach %.0f°; Laufzeit vom Fuß zur Wasserlinie %.2f s",
                FUSS, KRONE, 1 / NEIGUNG, RAU, hsF, SeegangKarteCheck.TP, SeegangKarteCheck.WOHIN, Brandung.laufzeit(-FUSS, NEIGUNG)));
        int bilder = 20 * TAKT, abAktiv = 0, gekoppeltVoll = 0, zaehlbar = 0;
        Map<Long, double[]> wellen = new HashMap<>();                     // (Kante, Nummer) → {Kante, Aufschlag, Höhe, Laufzeit}
        double t = t2;
        for (int s = 0; s < bilder; s++) {
            t = t2 + (double) s / TAKT;
            double tt = t;
            bild(fw, t, () -> b.rechne(kanten, w, tt, au[0], au[1], 3, 1));
            if (s >= Fusssuche.RING + TEILE) {
                zaehlbar++;
                if (b.aktiv > 0) abAktiv++;
                if (b.aktiv > 0 && b.gekoppelt == b.aktiv) gekoppeltVoll++;
                else if (System.getProperty("fuss.zeige") != null) System.out.printf("    Bild %d: aktiv %d, gekoppelt %d%n", s, b.aktiv, b.gekoppelt);
            }
            for (double[] q : b.wellen) if (q[4] == 1) wellen.put((long) q[0] * 1_000_000L + (long) q[1], new double[] {q[0], q[2], q[3], q[5]});
        }
        A.info("Abschnitte", f("aktiv %d, im Netz %d, gefragt %d, den Kämmen gefolgt %d (letztes Bild); Überläufe im letzten Bild %d", b.aktiv, b.imNetz, b.angefragt, b.gekoppelt, b.ueberlaeufe));
        A.pruefe(zaehlbar > 0 && gekoppeltVoll == zaehlbar, f("Bilder ab dem %d. (jeder Fuß einmal gesucht nach der ersten Meldung der Brandung, je Bild ein %d.), in denen alle aktiven Abschnitte den Kämmen der Fläche folgen", Fusssuche.RING + TEILE + 1, TEILE),
                f("%d von %d (mit aktiven Abschnitten %d)", gekoppeltVoll, zaehlbar, abAktiv), "alle");
        // jede gekoppelte Welle: Aufschlag − Laufzeit gegen einen Kamm der Referenz am Fuß
        Map<Integer, List<double[]>> ref = new HashMap<>();
        int gut = 0, zahl = 0;
        double dMax = 0;
        for (double[] q : wellen.values()) {
            int i = (int) q[0];
            double durch = q[1] - q[3];
            if (durch < t2 - 1 || durch > t) continue;
            List<double[]> r = ref.computeIfAbsent(i, x -> kaemme(fu[x][0], fu[x][1], t2 - 30, t2 + 25, karte.abstand(stufe(fu[x])), 1));
            double[] n = naechster(r, durch);
            zahl++;
            double d = n == null ? Double.POSITIVE_INFINITY : Math.abs(n[0] - durch);
            dMax = Math.max(dMax, d);
            if (d <= LAUF_TOL) gut++;
        }
        double anteil = zahl > 0 ? (double) gut / zahl : 0;
        A.pruefe(anteil >= ANTEIL_MIN, f("Wellen der Brandung: Aufschlag minus Laufzeit gegen den Durchgang eines Kamms der Referenz am Fuß (≤ %.1f s; größte Abweichung %.3f s)", LAUF_TOL, dMax),
                f("%.3f (%d von %d)", anteil, gut, zahl), f("≥ %.2f", ANTEIL_MIN));
        // Pause: die Zeit steht, die Karte sucht weiter
        long[] summe = new long[16];
        for (int s = 0; s < summe.length; s++) {
            double tt = t;
            bild(fw, t, () -> b.rechne(kanten, w, tt, au[0], au[1], 3, 1));
            summe[s] = summe(b);
        }
        boolean gleich = true;
        int ab = Fusssuche.RING + TEILE;
        for (int s = ab + 1; s < summe.length; s++) gleich &= summe[s] == summe[ab];
        A.pruefe(gleich, f("Pause: Netz und Gischt der Brandung von Bild %d bis %d", ab + 1, summe.length), gleich ? "bitgleich" : "verschieden", "bitgleich");
        // Sprung der Zeit um 1 000 s: zuerst die eigene Folge, dann wieder die Fläche
        double ts = t + 1000;
        int erstes = -1, wieder = -1;
        for (int s = 0; s < 14; s++) {
            double tt = ts + (double) s / TAKT;
            bild(fw, tt, () -> b.rechne(kanten, w, tt, au[0], au[1], 3, 1));
            if (s == 0) erstes = b.gekoppelt;
            if (System.getProperty("fuss.zeige") != null) System.out.printf("    nach dem Sprung Bild %d: aktiv %d, gekoppelt %d, Folgen %d, Suchen %d%n", s + 1, b.aktiv, b.gekoppelt, fw.folgenZahl, fw.suchen);
            if (wieder < 0 && b.aktiv > 0 && b.gekoppelt == b.aktiv) wieder = s + 1;
        }
        int grenze = Fusssuche.RING + TEILE + 1;
        A.pruefe(erstes == 0 && wieder > 0 && wieder <= grenze, "Sprung der Zeit um 1 000 s: im ersten Bild die eigene Folge, alle Abschnitte wieder an der Fläche nach höchstens " + grenze + " Bildern",
                f("erstes Bild %d gekoppelt; wieder alle im Bild %d", erstes, wieder), f("0; ≤ %d", grenze));
    }

    static int stufe(double[] fuss) {
        for (int l = 0; l < karte.stufen; l++) {
            double[] m = karte.mitte(l);
            double d = karte.abstand(l), qx = (fuss[0] - m[0]) / d + karte.n / 2, qy = (fuss[1] - m[1]) / d + karte.n / 2;
            if (qx >= 2 && qx <= karte.n - 3 && qy >= 2 && qy <= karte.n - 3) return l;
        }
        return karte.stufen - 1;
    }

    static long summe(Brandung b) {
        long h = 17;
        for (int j = 0; j < b.scheitel * Brandung.SCHEITEL; j++) h = h * 31 + Float.floatToIntBits(b.netz[j]);
        for (int j = 0; j < b.buendelZahl * Brandung.BUENDEL; j++) h = h * 31 + Float.floatToIntBits(b.buendel[j]);
        return h * 31 + b.buendelZahl;
    }

    // ------------------------------------------------------------------ Teil 4

    static void ueberlauf() {
        A.teil("4 Überlauf aus der Fläche gegen EurOtop 2018 (Prozessor)");
        double dauer = 1800, rc = 0.9, tan = NEIGUNG, rau = RAU, tp = SeegangKarteCheck.TP;
        int wellen = 0, ov = 0;
        double sv = 0, zeit = 0, hsSumme = 0, verhaeltnis = 0;
        Oberflaeche.Punkt p = new Oberflaeche.Punkt();
        double qSoll = 0, povSoll = 0;
        for (int i = 0; i < 6; i++) {
            double[] fu = fuss(2, YM + (i - 2.5) * 150);
            ob.punkt(fu[0], fu[1], 0, p);
            double hs = 4 * Math.sqrt(p.m0), hsG = 4 * Math.sqrt(p.gezeichnet), tm = tm01(fu[0], fu[1]);
            double ru2 = Brandung.ru2(hs, tp, tan, rau), q = Brandung.ueberlauf(hs, tp, tan, rau, rc), pov = Brandung.anteilUeberlauf(rc, ru2);
            List<double[]> k = kaemme(fu[0], fu[1], 20000, 20000 + dauer, 0, 1);
            for (double[] c : k) {
                wellen++;
                verhaeltnis += c[2] / hsG;
                double ru = ru2 * (c[2] / hsG) / 1.398, v = Brandung.volumen(ru, rc, ru2, hs, tp, tan, rau, tm);
                if (v > 0) { ov++; sv += v; }
            }
            zeit += dauer;
            qSoll += q * dauer;
            povSoll += pov * k.size();
            hsSumme += hs;
            A.info(f("Fuß %d (%.0f, %.0f)", i + 1, fu[0], fu[1]), f("Hs %.2f m, T_m01 %.2f s, %d Kämme (mittlerer Abstand %.2f s); Ru2%% %.2f m, P_ov %.3f, q %.3g m³/s je m", hs, tm, k.size(), dauer / Math.max(1, k.size()), ru2, pov, q));
        }
        double rq = (sv / zeit) / (qSoll / zeit), rp = ov / povSoll;
        A.info("2 |Z| / H_s der gezeichneten Wellen der Kämme im Mittel", f("%.3f (Rayleigh: √(2π) / 4 = %.3f); %d Kämme", verhaeltnis / wellen, Math.sqrt(2 * Math.PI) / 4, wellen));
        A.pruefe(Math.abs(rp - 1) <= POV_TOL, f("Anteil der Wellen mit Überlauf gegen P_ov nach EurOtop 2018 (R_c %.1f m, 1:%.0f; %d von %d Kämmen)", rc, 1 / tan, ov, wellen), f("%.3f", rp), f("1 ± %.2f", POV_TOL));
        A.pruefe(Math.abs(rq - 1) <= Q_TOL, "mittlerer Überlauf aus der Fläche (Volumen je Kamm mit T_m01) gegen EurOtop 2018", f("%.3f", rq), f("1 ± %.2f", Q_TOL));
    }

    // ------------------------------------------------------------------ Teil 5

    static void zeit() {
        A.teil("5 Rechenzeit");
        double[][] fu = new double[500][];
        for (int i = 0; i < fu.length; i++) fu[i] = fuss(2 + 6.0 * (i % 5) / 4, YM + (i / 5 - 50) * 9.0);
        double[] au = auge(fuesse(4));
        karteAuf(au, 9000);
        Fusswellen.Anfrage an = anfrage(fu);
        for (int i = 0; i < fu.length; i++) an.kante[i] = i;
        boolean software = g.name.toLowerCase().contains("llvmpipe") || g.name.toLowerCase().contains("lavapipe");
        // wie in der Anwendung (je Bild jeder vierte Fuß, nur der letzte Kamm) und zum Vergleich alles (wie L3a/L3b)
        double app = messen(an, TEILE, false), voll = messen(an, 1, true);
        String was = f("Suche für 500 aktive Abschnitte je Bild wie in der Anwendung: je Bild jeder %d. Fuß, nur der letzte Kamm, je Fuß eine Gruppe von 32 Fäden (Mittel aus 16 Bildern nach 8 zum Anlaufen)", TEILE);
        if (software || !Double.isFinite(app)) A.info(was, f("%.3f ms auf %s (sagt über die 680M nichts)", app, g.name));
        else A.hoechstens(was, app, ZEIT_MS, "ms");
        A.info("zum Vergleich: alle 500 Füße mit drei Kämmen je Bild (L3a 1,66 ms, L3b 1,44 ms)", f("%.3f ms", voll));
    }

    static double messen(Fusswellen.Anfrage an, int teile, boolean alle) {
        suche.teile = teile;
        suche.alleKaemme = alle;
        Fusswellen fw = new Fusswellen();
        int runden = 24, gelesen = 0;
        double summe;
        try (Stoppuhr uhr = new Stoppuhr(g, g.rechnen, 8)) {
            for (int r = 0; r < runden; r++) {
                karte.zeit(9000 + r / 30.0);
                Ablauf a = new Ablauf(g);
                suche.lies(fw);
                suche.suche(a, karte, 9000 + r / 30.0, an);
                g.rechnen.einmal(c -> { uhr.runde(c); a.aufnehmen(c, uhr); });
                gelesen += uhr.sammle(true);
                if (r == 7) { uhr.summenLeeren(); gelesen = 0; }
            }
            summe = uhr.verfuegbar ? uhr.summen().getOrDefault("see_fuss", 0.0) / Math.max(1, gelesen) : Double.NaN;
        } finally {
            suche.teile = TEILE;
            suche.alleKaemme = false;
            suche.leeren();
        }
        return summe;
    }

    // ------------------------------------------------------------------ Teil 6 (seit S5b-5)

    static void lippen() {
        A.teil("6 Lippen an Abschnitten wie in den Kanten des Gebiets (Deckwerk 1:4), je 20 s");
        // seit S5b-5b: Füße wie die Kantenkarte sie legt – je Zelle von 5 m (hier 30° gegen die Linie gedreht) ein Punkt
        // der 2-m-Linie, zufällig in der Zelle; jeder zweite im Mittel oben am Hang (2,5 m landwärts, Fuß um 2,5 tan α
        // höher), Richtung zur See um bis zu ±10° gestreut [G]
        java.util.Random r = new java.util.Random(5);
        double dreh = Math.toRadians(30), cd = Math.cos(dreh), sd = Math.sin(dreh);
        Map<Long, List<double[]>> zellen = new java.util.LinkedHashMap<>();
        for (double y = YM - 110; y <= YM + 110; y += 0.25) {
            double[] q = fuss(2, y);
            double u = q[0] * cd + q[1] * sd, v = -q[0] * sd + q[1] * cd;
            zellen.computeIfAbsent(((long) Math.floor(u / Kanten.LAENGE) << 32) ^ ((long) Math.floor(v / Kanten.LAENGE) & 0xffffffffL), x -> new ArrayList<>()).add(q);
        }
        List<double[]> fl = new ArrayList<>();
        List<Kanten.Kante> l = new ArrayList<>();
        int oben = 0;
        for (List<double[]> z : zellen.values()) {
            double[] q = z.get(r.nextInt(z.size()));
            double hoch = r.nextBoolean() ? 2.5 : 0, w = Math.toRadians(20 * (r.nextDouble() - 0.5));
            if (hoch > 0) oben++;
            double ox = q[0] - hoch * q[2], oy = q[1] - hoch * q[3];
            double nx = q[2] * Math.cos(w) - q[3] * Math.sin(w), ny = q[2] * Math.sin(w) + q[3] * Math.cos(w);
            fl.add(new double[] {ox, oy, nx, ny});
            l.add(new Kanten.Kante(ox, oy, nx, ny, FUSS + hoch * NEIGUNG, KRONE, NEIGUNG, RAU, 5));
        }
        int n = l.size();
        double[] mi = fl.get(n / 2);
        double[] au = {mi[0] + 20 * mi[2], mi[1] + 20 * mi[3]};
        double t0 = 11000.0;
        karteAuf(au, t0);
        Kanten kanten = new Kanten(l);
        double[] ab = new double[n - 1];
        for (int i = 0; i + 1 < n; i++) ab[i] = Math.hypot(fl.get(i + 1)[0] - fl.get(i)[0], fl.get(i + 1)[1] - fl.get(i)[1]);
        java.util.Arrays.sort(ab);
        A.info("Füße", f("%d auf 220 m, davon %d oben am Hang; Abstand der Füße hintereinander 5 %% %.1f m, Median %.1f m, 95 %% %.1f m", n, oben, ab[(int) (0.05 * (n - 2))], ab[(n - 2) / 2], ab[(int) (0.95 * (n - 2))]));
        Oberflaeche.Punkt p = new Oberflaeche.Punkt();
        ob.punkt(mi[0], mi[1], t0, p);
        double hsF = 4 * Math.sqrt(p.m0), wo = Math.toRadians(SeegangKarteCheck.WOHIN);
        Brandung.Wasser w = new Brandung.Wasser() {
            @Override public float eta(double o, double nn) { return 0f; }
            @Override public double[] seegang(double o, double nn) { return new double[] {hsF, SeegangKarteCheck.TP, Math.cos(wo), Math.sin(wo), 0.01, 2}; }
        };
        double msMit = 0, formenMit = 0;
        for (int lauf = 0; lauf < 4; lauf++) {
            // 0: Enden geteilt, 1: jeder für sich (beide mit Protokoll); 2: geteilt, 3: ohne Lippen (beide nur für die Zeit)
            boolean nachbarn = lauf != 1, nurZeit = lauf >= 2;
            Brandung b = new Brandung(Math.round(au[0]), Math.round(au[1]));
            b.protokollieren = !nurZeit;
            b.lippenNachbarn = nachbarn;
            b.lippen = lauf != 3;
            b.netzBereich = new double[] {au[0] - 500, au[0] + 500, au[1] - 500, au[1] + 500};
            Fusswellen fw = new Fusswellen();
            b.fuesse = fw;
            suche.leeren();
            Lippenpruefung lp = new Lippenpruefung();
            double ms = 0;
            long formen = 0;
            int bilder = 20 * TAKT;
            for (int s = 0; s < bilder; s++) {
                double tt = t0 + (lauf == 1 ? 100 : 0) + (double) s / TAKT;   // lauf 2, 3: dieselben Bilder wie 0
                bild(fw, tt, () -> b.rechne(kanten, w, tt, au[0], au[1], 3, 1));
                if (s < Fusssuche.RING + TEILE) continue;
                ms += b.ms;
                formen += b.formenGerechnet;
                if (!nurZeit) lp.bild(b.lippenProtokoll);
            }
            int gezaehlt = bilder - Fusssuche.RING - TEILE;
            if (lauf == 2) { msMit = ms / gezaehlt; formenMit = (double) formen / gezaehlt; continue; }
            if (lauf == 3) {
                A.info("Rechenzeit der Brandung (Prozessor) je Bild, " + n + " Abschnitte im Netz", f("mit Lippen %.3f ms (%.1f Formen gerechnet), ohne %.3f ms", msMit, formenMit, ms / gezaehlt));
                continue;
            }
            String wie = nachbarn ? "Enden mit den Nachbarn geteilt, freie Enden tauchen ab" : "jeder Abschnitt für sich (Enden mit dem Unterschied der Ankunft längs der Kante)";
            A.info("Lippen, " + wie, lp.bericht());
            if (nachbarn)                                                   // seit S5b-5 ohne Lippe abgeschlossen (Datei 55): Auskunft
                A.info("  Lippe (Auskunft, früher Prüfsteine)", f("sichtbare Enden %d; Zerfall %.0f %% kurz, Median %.1f m (Ziel ≤ %.0f %%, ≥ %.0f m); sichtbare Kante 95 %% %.3f m (Ziel ≤ %.2f m)",
                        lp.enden, 100 * lp.anteilKurz(10), Lippenpruefung.q(lp.stuecke, 0.5), 100 * STUECK_KURZ, STUECK_MEDIAN, Lippenpruefung.q(lp.kante, 0.95), KANTE_95));
        }
    }

    static double q(List<Double> l, double p) {
        if (l.isEmpty()) return Double.NaN;
        double[] a = l.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return a[(int) (p * (a.length - 1))];
    }

}
