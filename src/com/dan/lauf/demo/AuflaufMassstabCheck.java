package com.dan.lauf.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Brandung;
import com.dan.core.szene.Dispersion;
import com.dan.core.szene.Gestalt;
import com.dan.core.szene.Kanten;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S6b-3 (Seegang Werkbuch, Datei 72; SF70 A, SF71 A): Auflauf und Überlauf des Lösers in der Anwendung gegen die
 * Formeln. Wie {@link AuflaufCheck} führt sie die Bildschleife nach (Zeit, Clipmap, Auflauf in einem Ablauf, 60 Bilder je
 * s), aber {@link #DAUER} s je Fall, und je Bild dazu der Saum je Zeile ({@code SwashKarte.saum}: der höchste Grund unter
 * mindestens 1 cm Wasser, Kontur nach SF60), relativ zum Spiegel am Rand in diesem Bild ({@code Auflauf.wasserstand}; in
 * der Prüfung zeichnet die Bühne kein neues Bild, der Spiegel bleibt beim Standbild, wie im Antrieb).
 * <ul>
 * <li><b>Auflauf Welle für Welle:</b> je vierte Zeile nach {@link #VORLAUF_S} s Anlauf: Maxima zwischen zwei Aufwärts-
 * durchgängen des Saums durch seine Ausgleichsgerade (wie Stockdon u. a. 2006; ± 0,1 σ Hysterese); R2 % = 98-%-Quantil
 * aller Maxima aller Zeilen über dem Spiegel.</li>
 * <li><b>Überlauf:</b> was die offene Landseite nach dem Anlauf verlässt, je m Ufer und s.</li>
 * <li><b>Formeln:</b> Seegang am Rand aus der Synthese (H_s = 4 √m₀, Mittel der Punkte), Tp aus den Netzen. Sand: Stockdon
 * u. a. (2006) mit H₀ linear zurückgerechnet (Tiefe am Rand) und tan β der Wasserlinie. Bauwerk: EurOtop 2018 mit tan α,
 * γ_f und Krone der gewählten Kante, H_m0 am Rand ({@code Brandung.ru2}, {@code Brandung.ueberlauf}). Seit Datei 74 wie
 * in EurOtop vorgeschrieben mit H_m0 und T_m−1,0 (einlaufend) am Fuß: in 8 Zeilen aufgenommen, Fuß je Zeile die erste Zelle
 * mit Grund ≥ SWL − 1,5 H_m0 (am Rand); die Werte am Rand bleiben Auskunft.</li>
 * </ul>
 * Fälle: Sand (aktive Kante, weder Deich noch Warft noch Deckwerk, mit dem höchsten Seegang am ruhigen Tag mit Seegang,
 * 30.10.2026 14:00), Deckwerk (Sturm 25.10.2026 12:30), Deich (Sturm, Überlauf).
 * <p>
 * Prüfsteine [G, vor der Messung, Datei 71/72] je Fall: Rechteck gesetzt; kein Fehler; Bilanz ≤ 1e-4; dann am Sand R2 %
 * ± 20 % gegen Stockdon; am Bauwerk R2 % ± 20 % gegen EurOtop, wenn die Krone mindestens 1,2 R2 % (EurOtop) über dem
 * Ruhewasser liegt, sonst der Überlauf im Mittel innerhalb eines Faktors 3 der Formel (deren Streuung); erwartet die Formel
 * weniger als 1e-6 m³/s je m, nur Auskunft. Seit Datei 74 (SF75 A) ist der Strand Auskunft, seit Datei 76 (SF81 A) auch
 * der Überlauf am Bauwerk (die Richtungsverteilung der Synthese trägt den Fehlbetrag; den Löser prüft SwashKarteCheck Teil
 * 13); am Strand gilt seit Datei 76 (SF82 A) die Wasserlinie bei 5 cm. Seit Datei 80 (SF89 A, nach S6c-1b und S6c-3) ist
 * der Überlauf am Bauwerk wieder Prüfstein, gegen EurOtop am Fuß mit γ_β (kurzkämmig), Faktor 3; Stockdon mit Tp = 1,1
 * T_m−1,0 am Rand (SF90 A).
 * <p>
 * Aufruf: {@code AuflaufMassstabCheck --szenen <Hooge.szenen> [--dauer s] [--fall Sand|Deckwerk|Deich]}; Attest
 * {@code docs/status/S6b_Massstab.md}.
 */
public final class AuflaufMassstabCheck {
    /** Modellzeit je Fall (s), Anlauf ohne Auswertung (s), Bilder je s, je wievielte Zeile [G]. */
    static final double DAUER = 900, VORLAUF_S = 60;
    static final int BILDER_JE_S = 60, ZEILE_JEDE = 4;
    /** Grenzen [G]. */
    static final double R2_TOL = 0.2, Q_FAKTOR = 3, Q_KLEIN = 1e-6, FREI = 1.2, BILANZ = 1e-4, HYSTERESE = 0.1;
    /** Seit Datei 73 (SF72): Regeln für den Strand [G]: Abstand zu Bauwerken (m), tan α höchstens, H_s am Fuß mindestens (m). */
    static final double SAND_ABSTAND = 100, SAND_TAN = 0.15, SAND_HS = 0.3;
    /** Seit Datei 73 (SF73): Seegang aus dem Antrieb: Aufnahme je so viele Bilder, Band der einlaufenden Welle (Hz). */
    static final int ANTRIEB_JE = 6;
    /** Seit Datei 76 (SF82 A): Kontur der Wasserlinie (m Wasser) am Strand für den Vergleich mit Stockdon (wie SWASH-Studien). */
    static final double STOCKDON_KONTUR = 0.05;
    static final double F_VON = 0.04, F_BIS = 2.0;
    /**
     * Seit Datei 74: Seegang im Rechteck, damit EurOtop wie vorgeschrieben H_m0 und T_m−1,0 am Fuß bekommt: so viele Zeilen
     * (gleich verteilt), je wievielte Zelle quer, Mindesttiefe (m) der aufgenommenen Zellen, Abstand der Auskunft längs (m).
     */
    static final int FUSS_ZEILEN = 8, FUSS_JEDE = 2;
    static final double FUSS_TIEFE = 0.3, PROFIL_ABSTAND = 4;
    /**
     * Seit Datei 82 (SF94 A): Fuß der Böschung je Zeile – von der Krone seewärts das Ende des steilen Stücks, wo der Grund über
     * {@link #FUSS_STRECKE} m flacher als {@link #FUSS_NEIGUNG} steigt [G]; H_m0 und T_m−1,0 für EurOtop dort aus einer
     * Rechnung ohne Bauwerk (landwärts des Fußes ein Strand 1:{@link #OHNE_STRAND}) über {@link #OHNE_DAUER} s nach dem Anlauf.
     */
    static final double FUSS_NEIGUNG = 0.25, FUSS_STRECKE = 1.0, OHNE_STRAND = 30, OHNE_DAUER = 300;
    /** Seit Datei 83: die Messung ohne Bauwerk gilt nur, wenn am Fuß mindestens dieser Anteil des Stands am Rand (4 σ) ankommt [G]. */
    static final double OHNE_MIN = 0.25;

    private AuflaufMassstabCheck() { }

    record Fall(String name, String szenario, String zeit, boolean sand, IntPredicate arten) { }

    public static void main(String[] args) {
        File szenen = null;
        double dauer = DAUER;
        String nur = null;
        int breite = 1920, hoehe = 1080;
        for (int i = 0; i + 1 < args.length; i += 2)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--dauer" -> dauer = Double.parseDouble(args[i + 1]);
                case "--fall" -> nur = args[i + 1];
                default -> { }
            }
        Attest A = new Attest("Seegang, Auflauf und Überlauf gegen die Formeln (S6b-3)", "Seegang Werkbuch");
        System.out.println("AuflaufMassstabCheck – S6b-3 (Seegang Werkbuch)");
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
                A.pruefe(au != null, "Auflauf an der Bühne", au == null ? "keiner" : "ja", "vorhanden");
                if (au == null) return;
                A.info("Messung", f("Auflauf %s, Swash %s; %.0f s je Fall zu %d Bildern je s, ausgewertet nach %.0f s Anlauf; Saum mit 1 cm Wasser, je %d. Zeile; Grenzen: R2 %% ± %.0f %%, Überlauf Faktor %.0f",
                        Auflauf.VERSION, Swash.VERSION, dauer, BILDER_JE_S, VORLAUF_S, ZEILE_JEDE, 100 * R2_TOL, Q_FAKTOR));
                // seit Datei 73 (SF72): der Sand ein Strand nach Regeln [G] (Art 0–2, mindestens SAND_ABSTAND m von Deich, Warft
                // und Deckwerk, tan α höchstens SAND_TAN, H_s am Fuß mindestens SAND_HS), zuerst im Sturm, sonst am ruhigen Tag;
                // in L4f lag der „Sand“ am Fuß des Deichs (8 cm Seegang, ξ₀ ≈ 8, außerhalb von Stockdon)
                Fall[] faelle = {
                    new Fall("Sand", "seegang", "2026-10-25T12:30:00Z", true, art -> art != Gestalt.DEICH && art != Gestalt.WARFT && art != Gestalt.DECKWERK),
                    new Fall("Sand", "ruhigsee", "2026-10-30T14:00:00Z", true, art -> art != Gestalt.DEICH && art != Gestalt.WARFT && art != Gestalt.DECKWERK),
                    new Fall("Deckwerk", "seegang", "2026-10-25T12:30:00Z", false, art -> art == Gestalt.DECKWERK),
                    new Fall("Deich", "seegang", "2026-10-25T12:30:00Z", false, art -> art == Gestalt.DEICH)};
                String offen = null;
                boolean sandFertig = false;
                for (Fall fa : faelle) {
                    if (nur != null && !nur.equalsIgnoreCase(fa.name)) continue;
                    if (fa.sand && sandFertig) continue;
                    Szenario sc = SchaumCheck.szenario(sz, fa.szenario);
                    if (!fa.szenario.equals(offen)) { b.oeffne(sc); offen = fa.szenario; }
                    Blick bl = SchaumCheck.blick(an, fa.zeit, breite, hoehe);
                    b.wasserbild().seegang = true;
                    b.brandung(true);
                    b.standbild(bl, SchaumCheck.t);
                    double t0 = SchaumCheck.t;
                    if (fa.sand) {
                        bl = strand(A, b, t0, breite, hoehe, fa.szenario + " " + fa.zeit);
                        if (bl == null) continue;
                        sandFertig = true;
                        A.teil(fa.name);
                    } else {
                        A.teil(fa.name);
                        bl = AuflaufCheck.kante(A, b, t0, breite, hoehe, fa.name, fa.arten);
                        if (bl == null) continue;
                    }
                    fall(A, b, au, sz, sc, bl, t0, dauer, fa, AuflaufCheck.letzteKante);
                }
                if ((nur == null || nur.equalsIgnoreCase("Sand")) && !sandFertig)
                    A.info("Sand", f("kein Strand nach den Regeln (Art 0–2, mindestens %.0f m von Bauwerken, tan α ≤ %.2f, H_s ≥ %.1f m) in beiden Szenarien; der Vergleich mit Stockdon entfällt", SAND_ABSTAND, SAND_TAN, SAND_HS));
            } finally {
                b.close();
            }
            A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - start) / 1e9));
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "S6b_Massstab.md"), "S6b-3 – Auflauf und Überlauf gegen die Formeln");
        System.out.println();
        System.out.println("Attest: " + p.toAbsolutePath());
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void fall(Attest A, Buehne b, Auflauf au, Szenen sz, Szenario sc, Blick bl, double t0, double dauer, Fall fa, Kanten.Kante ka) throws Exception {
        Geraet g = b.geraet();
        String name = fa.name;
        Seegangsflaeche sf = Seegangsflaeche.baue(g, b.konto(), sz.gebiet(), sc.archiv.toPath(), b.seegangsnetze(), bl, t0, null);
        try {
            SeegangKarte k = sf.karte;
            k.bezug(Math.floor(bl.ost / 64) * 64, Math.floor(bl.nord / 64) * 64);
            k.fernOst = bl.ost;
            k.fernNord = bl.nord;
            au.vergiss();
            boolean gesetzt = au.vorBild(bl, t0, 1, b.seegangsnetze());
            A.info("Setzen, " + name, au.bericht);
            A.pruefe(gesetzt, "Rechteck gesetzt, " + name, gesetzt ? "ja" : "nein", "ja");
            if (!gesetzt) return;
            Swash s = au.swash();
            boolean[] see = au.seeZellen;
            double v0 = s.volumen(), ruhe = s.bezug, tiefeRand = 0;
            int nr = 0;
            for (int j = 0; j < s.ny; j++) { double h = ruhe - s.b[s.id(0, j)]; if (h > 0) { tiefeRand += h; nr++; } }
            tiefeRand = nr > 0 ? tiefeRand / nr : Double.NaN;
            // seit Datei 73: Krone je Zeile (höchster Grund); dahinter liegt das Land, in das der Überlauf fließt
            int[] ik = new int[s.ny];
            double[] krone = new double[s.ny];
            for (int j = 0; j < s.ny; j++) {
                double m = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < s.nx; i++) { double bb = s.b[s.id(i, j)]; if (bb > m) { m = bb; ik[j] = i; } }
                krone[j] = m - ruhe;
            }
            // seit Datei 82 (SF94 A): Fuß der Böschung je Zeile (−1: keiner)
            int[] fussB = new int[s.ny];
            for (int j = 0; j < s.ny; j++) fussB[j] = fussBoeschung(s, j, ik[j], ruhe);
            int n = (int) Math.round(dauer * BILDER_JE_S), n0 = (int) Math.round(VORLAUF_S * BILDER_JE_S), zeilen = (s.ny + ZEILE_JEDE - 1) / ZEILE_JEDE;
            float[][] saum = new float[zeilen][n - n0 + 1];
            int npW = (int) Math.ceil(Auflauf.NY * Auflauf.DX / Auflauf.PUNKT_ABSTAND) + 1, na = (n - n0) / ANTRIEB_JE + 1;
            float[][] eta = new float[npW][na], qn = new float[npW][na], qt = new float[npW][na];
            // seit Datei 74: Stand und Fluss quer in FUSS_ZEILEN Zeilen, je FUSS_JEDE-te Zelle, wo es tiefer als FUSS_TIEFE ist
            int nf = (s.nx + FUSS_JEDE - 1) / FUSS_JEDE;
            int[] jf = new int[FUSS_ZEILEN];
            float[][][] etaF = new float[FUSS_ZEILEN][nf][], qF = new float[FUSS_ZEILEN][nf][];
            for (int zf = 0; zf < FUSS_ZEILEN; zf++) {
                jf[zf] = (int) ((2L * zf + 1) * s.ny / (2 * FUSS_ZEILEN));
                for (int c = 0; c < nf; c++) if (ruhe - s.b[s.id(c * FUSS_JEDE, jf[zf])] > FUSS_TIEFE) { etaF[zf][c] = new float[na]; qF[zf][c] = new float[na]; }
            }
            double[] lage = au.lage();
            double[] land0 = null;
            long t1 = System.nanoTime();
            for (int q = 0; q <= n; q++) {
                double t = t0 + (double) q / BILDER_JE_S;
                Ablauf ab = new Ablauf(g);
                k.zeit(ab, t);
                k.bild(ab, bl.ost, bl.nord);
                au.vorbereiten(ab, null, k, t);
                au.karte().saum(ab, fa.sand ? STOCKDON_KONTUR : 0.01);     // seit Datei 76 (SF82 A): am Strand die Wasserlinie bei 5 cm
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                if (q == n0) { au.karte().runter(); land0 = hinter(au, s, ik, see); }
                if (q >= n0) {
                    double pegel = au.wasserstand() - ruhe;                    // Spiegel, mit dem der Antrieb rechnet (in der Anwendung läuft die Tide)
                    double[] z = au.karte().saumLesen();
                    for (int r = 0; r < zeilen; r++) saum[r][q - n0] = (float) (z[r * ZEILE_JEDE] - pegel);
                    if ((q - n0) % ANTRIEB_JE == 0) {
                        float[] rw = au.randwelle().lies();
                        int a = (q - n0) / ANTRIEB_JE;
                        for (int p = 0; p < npW; p++) {
                            eta[p][a] = rw[8 * p];
                            qn[p][a] = (float) (rw[8 * p + 1] * lage[2] + rw[8 * p + 2] * lage[3]);
                            qt[p][a] = (float) (-rw[8 * p + 1] * lage[3] + rw[8 * p + 2] * lage[2]);   // seit Datei 75: längs (+y)
                        }
                        for (int zf = 0; zf < FUSS_ZEILEN; zf++) {
                            float[] r = au.karte().zeileLesen(jf[zf]);
                            for (int c = 0; c < nf; c++) {
                                if (etaF[zf][c] == null) continue;
                                int i = c * FUSS_JEDE;
                                etaF[zf][c][a] = (float) (r[3 * i] + s.b[s.id(i, jf[zf])] - ruhe);
                                qF[zf][c][a] = r[3 * i + 1];
                            }
                        }
                    }
                }
            }
            double sekunden = (System.nanoTime() - t1) / 1e9;
            A.pruefe(au.an, "Auflauf nach den Bildern noch an (kein Fehler), " + name, au.an ? "ja" : au.bericht, "ja");
            au.karte().runter();
            double v = s.volumen(), zu = s.zufluss(), vs = s.versickert(), ag = s.abgeschnitten();
            A.hoechstens("Bilanz des Wassers, relativ, " + name, (v - v0 - zu + vs - ag) / v0, BILANZ, "");
            // Überlauf (seit Datei 73): was über die Krone ins Land dahinter kam = Zunahme dort + dort versickert + dort über das
            // trockene Außen hinaus (E, S, N); vorher nur der Ausfluss über E (in L4f vor allem Wasser, das über N ins Land lief)
            double[] land1 = hinter(au, s, ik, see);
            double ueber = (land1[0] - land0[0]) + (land1[1] - land0[1]) + (land1[2] - land0[2]);
            double qMess = ueber / (s.ny * s.dx * (dauer - VORLAUF_S));
            double ausE = randAus(au);
            // Seegang aus dem Antrieb (seit Datei 73, SF73): einlaufend nach linearer Theorie je Frequenz getrennt
            double[] wa = welle(eta, qn, s, ANTRIEB_JE / (double) BILDER_JE_S);
            double hsIn = wa[0], tmIn = wa[1], tpIn = wa[2];
            // seit Datei 74: Seegang im Rechteck je Zeile und Zelle {H_m0 einlaufend, T_m−1,0 einlaufend, H_m0 gesamt}
            double dtA = ANTRIEB_JE / (double) BILDER_JE_S;
            double[][][] wf = new double[FUSS_ZEILEN][nf][];
            for (int zf = 0; zf < FUSS_ZEILEN; zf++)
                for (int c = 0; c < nf; c++) if (etaF[zf][c] != null) wf[zf][c] = punkt(etaF[zf][c], qF[zf][c], ruhe - s.b[s.id(c * FUSS_JEDE, jf[zf])], dtA);
            etaF = null;
            qF = null;
            // Fuß nach EurOtop: je Zeile die erste aufgenommene Zelle mit Grund ≥ SWL − 1,5 H_m0 (einlaufend am Rand); Median der Zeilen
            List<Double> fx = new ArrayList<>(), fh = new ArrayList<>(), ft = new ArrayList<>(), fg = new ArrayList<>(), fz = new ArrayList<>();
            for (int zf = 0; zf < FUSS_ZEILEN; zf++)
                for (int c = 0; c < nf; c++) {
                    double z = s.b[s.id(c * FUSS_JEDE, jf[zf])] - ruhe;
                    if (wf[zf][c] == null || Double.isNaN(z) || z < -1.5 * hsIn) continue;
                    fx.add((c * FUSS_JEDE + 0.5) * s.dx); fz.add(z); fh.add(wf[zf][c][0]); ft.add(wf[zf][c][1]); fg.add(wf[zf][c][2]);
                    break;
                }
            double hsF = median(fh), tmF = median(ft);
            // Welle für Welle
            List<Double> maxima = new ArrayList<>();
            double wellen = 0, mittel = 0;
            int zeilenMit = 0;
            for (float[] reihe : saum) {
                double[] w = maxima(reihe);
                if (w == null) continue;
                zeilenMit++;
                mittel += w[0];
                wellen += w.length - 1;
                for (int i = 1; i < w.length; i++) maxima.add(w[i]);
            }
            double[] mx = maxima.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double r2 = mx.length > 0 ? mx[Math.min(mx.length - 1, (int) Math.floor(0.98 * mx.length))] : Double.NaN;
            double rMittel = mx.length > 0 ? Arrays.stream(mx).average().orElse(Double.NaN) : Double.NaN;
            double[] kr = krone.clone();
            Arrays.sort(kr);
            double rc = kr[kr.length / 2];
            A.info("Messung, " + name, f("%.0f s Modellzeit in %.0f s; Saum in %d von %d Zeilen, im Mittel %.1f Wellen je Zeile; Spiegel des Saums im Mittel %+.3f m (Wellenstau), Auflauf im Mittel %.3f m, R2 %% %.3f m über dem Spiegel; Überlauf über die Krone %.3f m³ (%.2e m³/s je m: Zunahme hinter der Krone %.3f, dort versickert %.3f, hinaus %.3f m³); über E hinaus seit dem Setzen %.3f m³",
                    dauer, sekunden, zeilenMit, saum.length, zeilenMit > 0 ? wellen / zeilenMit : 0, zeilenMit > 0 ? mittel / zeilenMit : Double.NaN, rMittel, r2,
                    ueber, qMess, land1[0] - land0[0], land1[1] - land0[1], land1[2] - land0[2], ausE));
            A.info("Seegang am Rand aus dem Antrieb, " + name, f("einlaufend H_m0 %.2f m, T_m−1,0 %.2f s, Tp %.1f s; gesamt H_m0 %.2f m, T_m−1,0 %.2f s; Reflexion in der Synthese %.2f (%d Punkte, %.2f–%.1f Hz); Netze beim Setzen: Hs %.2f m, Tp %.1f s",
                    hsIn, tmIn, tpIn, wa[3], wa[4], wa[5], (int) wa[6], F_VON, F_BIS, au.hs, au.tp));
            {   // seit Datei 78 (S6c-1b, SF85 A): welcher Anteil der Energie am Rand im Löser frei läuft (Datei 77)
                double hr = au.randwelle() != null ? au.randwelle().bezugTiefe() : 0;
                double[] fl = freiLaufend(eta, tiefeRand, ANTRIEB_JE / (double) BILDER_JE_S, Auflauf.PUNKT_ABSTAND);
                A.info("Frei laufend im Löser (S6c-1b, Auskunft), " + name, f("Richtung erhalten %s; Anteil der Energie am Rand mit |k_y| < ω/√(g h) (h %.2f m): bis 0,2 Hz %.0f %%, 0,2–0,5 Hz %.0f %%, darüber %.0f %%, gesamt %.0f %%; mit |k_y| < k(h) linear gesamt %.0f %% (Datei 77, ohne: Deich 53 %%, Deckwerk 41 %%)",
                        hr > 0 ? f("an (Tiefe des Rands %.2f m)", hr) : "aus", tiefeRand, 100 * fl[0], 100 * fl[1], 100 * fl[2], 100 * fl[3], 100 * fl[4]));
            }
            {   // seit Datei 74 (SF76 A): woher der Seegang am Rand kommt – Netze, Oberfläche (Nahfeld fein, grob, Fernfeld), Synthese
                Swash sw = au.swash();
                double ro = sw.weltX(-0.5 * sw.dx, 0.5 * sw.ny * sw.dx), rn = sw.weltY(-0.5 * sw.dx, 0.5 * sw.ny * sw.dx);
                A.info("Quellen des Seegangs am Rand (SF76 A), " + name, quellen(sf, b.seegangsnetze(), t0, ro, rn)
                        + f("; Synthese am Rand gesamt H_m0 %.2f m: bis 0,2 Hz %.2f, 0,2–0,5 Hz %.2f, 0,5–2 Hz %.2f m", wa[3], wa[7], wa[8], wa[9]));
                // seit Datei 75 (SF78 A): die Windsee im Nahfeld gegen Young und Verhagen (1996) mit Wind, Fetch und Tiefe am Ort
                A.info("Windsee gegen Young und Verhagen (SF78 A, Auskunft), " + name, windsee(sz, sc, sf, t0, ro, rn));
            }
            {   // seit Datei 74: Seegang im Rechteck längs (Median der Zeilen) und quer (am Rand je Zeile von S nach N)
                StringBuilder sl = new StringBuilder();
                int schritt = Math.max(1, (int) Math.round(PROFIL_ABSTAND / (FUSS_JEDE * s.dx)));
                for (int c = 0; c < nf; c += schritt) {
                    List<Double> zz = new ArrayList<>(), hh = new ArrayList<>(), gg = new ArrayList<>();
                    for (int zf = 0; zf < FUSS_ZEILEN; zf++) {
                        zz.add(s.b[s.id(c * FUSS_JEDE, jf[zf])] - ruhe);
                        if (wf[zf][c] != null) { hh.add(wf[zf][c][0]); gg.add(wf[zf][c][2]); }
                    }
                    if (sl.length() > 0) sl.append("; ");
                    sl.append(f("%.0f m: Grund %+.2f", (c * FUSS_JEDE + 0.5) * s.dx, median(zz)));
                    if (hh.size() * 2 > FUSS_ZEILEN) sl.append(f(", H_m0 %.2f / %.2f", median(hh), median(gg)));
                }
                A.info("Seegang im Rechteck längs (seit Datei 74), " + name, "x von W, Grund über dem Ruhewasser, H_m0 einlaufend / gesamt (Median von " + FUSS_ZEILEN + " Zeilen): " + sl);
                StringBuilder sq = new StringBuilder();
                for (int zf = 0; zf < FUSS_ZEILEN; zf++) sq.append(zf > 0 ? " " : "").append(wf[zf][1] != null ? f("%.2f", wf[zf][1][0]) : "–");
                A.info("Seegang im Rechteck quer (seit Datei 74), " + name, f("H_m0 einlaufend bei x %.1f m je Zeile von S nach N (y %.0f … %.0f m): ", (FUSS_JEDE + 0.5) * s.dx, (jf[0] + 0.5) * s.dx, (jf[FUSS_ZEILEN - 1] + 0.5) * s.dx) + sq);
                A.info("Seegang am Fuß (EurOtop, seit Datei 74), " + name, f("Fuß je Zeile die erste Zelle mit Grund ≥ SWL − 1,5 H_m0 (am Rand, %.2f m) in %d von %d Zeilen: x %.1f m, Grund %+.2f m; H_m0 einlaufend %.2f m (%.2f am Rand), gesamt %.2f m, T_m−1,0 einlaufend %.2f s (%.2f am Rand)",
                        1.5 * hsIn, fh.size(), FUSS_ZEILEN, median(fx), median(fz), hsF, hsIn, median(fg), tmF, tmIn));
            }
            A.info("Ort, " + name, f("Tiefe am Rand %.2f m; tan β der Wasserlinie %.3f; Krone je Zeile Median %+.2f m (%+.2f … %+.2f) über dem Ruhewasser; Kante: Art %s, tan α %.3f (1:%.1f), γ_f %.2f, Krone %.2f m NHN",
                    tiefeRand, lage[5], rc, kr[0], kr[kr.length - 1], Kanten.artName(ka.art()), ka.neigung(), 1 / ka.neigung(), ka.rau(), ka.krone()));
            if (fa.sand) {
                // seit Datei 80 (SF90 A): Tp = 1,1 T_m−1,0 der einlaufenden Welle am Rand; der Gipfel sprang zwischen den Bändern
                // (L4m: 3,5 → 2,3 s bei fast gleichem T_m−1,0, Stockdon 0,298 → 0,107 m)
                double tpS = 1.1 * tmIn;
                double h0 = hsIn / shoaling(tpS, tiefeRand), st = Auflauf.stockdon(h0, tpS, lage[5]), l0 = 9.81 * tpS * tpS / (2 * Math.PI);
                A.info("Stockdon u. a. (2006), " + name, f("H₀ %.2f m (Ks %.3f), Tp %.1f s (1,1 T_m−1,0 am Rand; Gipfel %.1f s), tan β %.3f, ξ₀ %.2f; R2 %% %.3f m", h0, hsIn / h0, tpS, tpIn, lage[5], lage[5] / Math.sqrt(h0 / l0), st));
                // seit Datei 74 (SF75 A): nur Auskunft; der Vergleich mit Stockdon als Prüfstein steht im SwashKarteCheck (Teil 12)
                A.info("R2 % gegen Stockdon (Auskunft, SF75 A; Wasserlinie bei 5 cm seit Datei 76, SF82 A), " + name, f("%+.1f %%", 100 * (r2 / st - 1)));
            } else {
                // EurOtop 2018 (5.3.4): Neigung im Mittel zwischen SWL − 1,5 H_m0 und SWL + min(Ru2 %, R_c), je Zeile aus dem Grund
                // des Rechtecks (Median), iteriert; mit T_m−1,0 und H_m0 der einlaufenden Welle (seit Datei 73, SF73)
                // seit Datei 74: H_m0 und T_m−1,0 am Fuß (EurOtop 2018, 1.4); die Werte am Rand nur noch zum Vergleich
                double[] er = euro(s, ruhe, ik, hsIn, tmIn, rc, ka);
                // seit Datei 82 (SF94 A): am Fuß der Böschung, ohne Bauwerk gerechnet; die Regel aus Datei 74 (SWL − 1,5 H_m0, mit
                // Bauwerk getrennt) nur noch zum Vergleich
                double[] oh = ohneBauwerk(A, b, au, k, bl, t0, fussB, jf, ruhe, name);
                boolean ohneGilt = oh != null && oh[0] > 0 && oh[1] > 0;
                if (ohneGilt) A.info("Seegang am Fuß der Böschung ohne Bauwerk (SF94 A), " + name, f("Fuß je Zeile das Ende des steilen Stücks (über %.0f m flacher als 1:%.0f) in %d von %d Zeilen: x %.1f m, Grund %+.2f m; landwärts ein Strand 1:%.0f, %.0f s nach %.0f s Anlauf: H_m0 einlaufend %.2f m, gesamt %.2f m, T_m−1,0 einlaufend %.2f s. Zum Vergleich mit Bauwerk bei SWL − 1,5 H_m0 (Datei 74): H_m0 %.2f m, T_m−1,0 %.2f s",
                        FUSS_STRECKE, 1 / FUSS_NEIGUNG, (int) oh[5], FUSS_ZEILEN, oh[3], oh[4], OHNE_STRAND, OHNE_DAUER, VORLAUF_S, oh[0], oh[2], oh[1], hsF, tmF));
                else A.info("Seegang am Fuß der Böschung ohne Bauwerk (SF94 A), " + name, oh == null ? "kein Fuß der Böschung gefunden oder das Rechteck nicht gleich gesetzt; EurOtop mit der Regel aus Datei 74" : "keine Messung; EurOtop mit der Regel aus Datei 74");
                if (ohneGilt) { hsF = oh[0]; tmF = oh[1]; }
                boolean mitFuss = hsF > 0 && tmF > 0;
                double hsE = mitFuss ? hsF : hsIn, tmE = mitFuss ? tmF : tmIn;
                double[] ef = mitFuss ? euro(s, ruhe, ik, hsF, tmF, rc, ka) : er;
                double tanA = ef[0], ru = ef[1], qe = ef[2], xi = tanA / Math.sqrt(hsE / (9.81 * tmE * tmE / (2 * Math.PI)));
                A.info("EurOtop 2018, " + name, f("am Fuß%s: H_m0 %.2f m, T_m−1,0 %.2f s, tan α aus dem Profil %.3f (1:%.1f; die Kante %.3f), ξ %.2f, γ_f %.2f, R_c %.2f m: Ru2 %% %.3f m, R_c / Ru2 %% %.2f; Überlauf im Mittel %.2e m³/s je m. Zum Vergleich mit dem Seegang am Rand wie in L4h: H_m0 %.2f m, T_m−1,0 %.2f s, tan α %.3f, Ru2 %% %.3f m, Überlauf %.2e; wie in L4f (Synthese gesamt, Tp der Netze, tan α der Kante): Ru2 %% %.3f m, Überlauf %.2e",
                        mitFuss ? "" : " (ohne Messung am Fuß: am Rand)", hsE, tmE, tanA, 1 / tanA, ka.neigung(), xi, ka.rau(), rc, ru, rc / ru, qe,
                        hsIn, tmIn, er[0], er[1], er[2],
                        Brandung.ru2(wa[3], au.tp, ka.neigung(), ka.rau()), Brandung.ueberlauf(wa[3], au.tp, ka.neigung(), ka.rau(), ka.krone() - ruhe)));
                if (mitFuss && er[2] >= Q_KLEIN && qMess > 0) A.info("Überlauf gegen EurOtop mit dem Seegang am Rand (Auskunft, wie L4h), " + name, f("%.2f", qMess / er[2]));
                double qb;
                {   // seit Datei 75 (SF80 A): Richtung der einlaufenden Synthese am Rand und EurOtop mit der Schräge γ_β (5.29/5.30, kurzkämmig)
                    double[] ri = richtung(eta, qn, qt, s, ANTRIEB_JE / (double) BILDER_JE_S);
                    double bet = Math.abs(ri[0]), gb = 1 - 0.0033 * Math.min(bet, 80), fb = bet > 80 ? Math.max(0, (110 - bet) / 30) : 1;
                    qb = Brandung.ueberlauf(hsE * fb, 1.1 * tmE * fb, tanA, ka.rau() * gb, rc);
                    A.info("Richtung der Synthese am Rand (SF80 A, Auskunft), " + name, f("einlaufend gegen die Normale, nach Energie gemittelt: |β| %.0f° (mit Vorzeichen %+.0f°); bis 0,2 Hz %+.0f°, 0,2–0,5 Hz %+.0f°, darüber %+.0f°",
                            ri[0], ri[1], ri[2], ri[3], ri[4]));
                    A.info("EurOtop am Fuß mit der Schräge (SF80 A), " + name, f("γ_β %.2f%s: Überlauf im Mittel %.2e m³/s je m",
                            gb, fb < 1 ? f(", H_m0 und T_m−1,0 × %.2f", fb) : "", qb));
                }
                if (rc >= FREI * ru) A.hoechstens("R2 % gegen EurOtop (Krone hoch genug), relativ, " + name, r2 / ru - 1, R2_TOL, "");
                else if (qb < Q_KLEIN) A.info("Überlauf, " + name, f("die Formel mit γ_β erwartet weniger als %.0e m³/s je m; gemessen %.2e (nur Auskunft)", Q_KLEIN, qMess));
                else {
                    // seit Datei 80 (SF89 A): wieder Prüfstein, nach S6c-1b (Rand mit erhaltener Richtung) und S6c-3 (Boden aus den
                    // Klassen), gegen EurOtop am Fuß mit γ_β für kurzkämmigen Seegang (5.29/5.30), Faktor 3; Datei 76–79 Auskunft
                    double fb = qMess > 0 ? qMess / qb : 0;
                    A.pruefe(fb >= 1 / Q_FAKTOR && fb <= Q_FAKTOR, "Überlauf im Mittel gegen EurOtop am Fuß mit γ_β, Verhältnis, " + name,
                            f("%.2f (%.2e gegen %.2e m³/s je m)", fb, qMess, qb), f("%.2f … %.0f", 1 / Q_FAKTOR, Q_FAKTOR));
                    A.info("Überlauf gegen EurOtop am Fuß ohne γ_β (Auskunft, bis Datei 79 so geführt), " + name, f("%.2f (%.2e gegen %.2e m³/s je m)", qMess > 0 ? qMess / qe : 0, qMess, qe));
                    A.info("Auflauf am Bauwerk (Auskunft), " + name, f("R2 %% %.3f m gegen Ru2 %% %.3f m nach EurOtop (die Krone schneidet ab: R_c %.2f m)", r2, ru, rc));
                }
            }
        } finally {
            b.geraet().ruhe();
            sf.close();
        }
    }

    /** Seit Datei 82 (SF94 A): Fuß der Böschung in Zeile j: von der Krone ik seewärts das Ende des steilen Stücks; unter dem Ruhewasser, sonst −1. */
    static int fussBoeschung(Swash s, int j, int ik, double ruhe) {
        int n = Math.max(1, (int) Math.round(FUSS_STRECKE / s.dx));
        boolean steil = false;
        for (int i = ik; i - n >= 0; i--) {
            double b1 = s.b[s.id(i, j)], b0 = s.b[s.id(i - n, j)];
            if (Double.isNaN(b0) || Double.isNaN(b1)) return -1;
            double t = (b1 - b0) / (n * s.dx);
            if (t >= FUSS_NEIGUNG) steil = true;
            else if (steil) return b1 < ruhe ? i : -1;
        }
        return -1;
    }

    /**
     * Seit Datei 82 (SF94 A): dasselbe Rechteck noch einmal, landwärts des Fußes der Böschung je Zeile ein Strand
     * 1:{@link #OHNE_STRAND}; nach {@link #VORLAUF_S} s Anlauf {@link #OHNE_DAUER} s Stand und Fluss am Fuß in den Zeilen jf
     * (je {@link #ANTRIEB_JE} Bilder). {H_m0 einlaufend, T_m−1,0 einlaufend, H_m0 gesamt, x des Fußes (m), Grund (m über dem
     * Ruhewasser), Zahl der Zeilen} als Median der Zeilen; null ohne Fuß oder wenn das Rechteck anders liegt. Danach ist der
     * Auflauf vergessen (der Fall ist ausgewertet).
     */
    static double[] ohneBauwerk(Attest A, Buehne b, Auflauf au, SeegangKarte k, Blick bl, double t0, int[] fussB, int[] jf, double ruhe, String name) {
        int mit = 0;
        for (int zf = 0; zf < jf.length; zf++) if (fussB[jf[zf]] >= 0) mit++;
        if (mit == 0) return null;
        Swash alt = au.swash();
        double o0 = alt.weltX(0, 0), n0w = alt.weltY(0, 0);
        au.grundAendern = (s2, r) -> {
            for (int j = 0; j < s2.ny; j++) {
                int f0 = fussB[j];
                if (f0 < 0) continue;
                double bf = s2.b[s2.id(f0, j)];
                if (Double.isNaN(bf)) continue;
                for (int i = f0 + 1; i < s2.nx; i++) s2.b[s2.id(i, j)] = bf + (i - f0) * s2.dx / OHNE_STRAND;
            }
        };
        try {
            au.vergiss();
            boolean gesetzt = au.vorBild(bl, t0, 1, b.seegangsnetze());
            Swash s = au.swash();
            if (!gesetzt || s == null || Math.hypot(s.weltX(0, 0) - o0, s.weltY(0, 0) - n0w) > 0.01) {
                A.info("Ohne Bauwerk, " + name, gesetzt ? "das Rechteck liegt anders als im Fall" : "nicht gesetzt: " + au.bericht);
                return null;
            }
            Geraet g = b.geraet();
            int n0 = (int) Math.round(VORLAUF_S * BILDER_JE_S), n = n0 + (int) Math.round(OHNE_DAUER * BILDER_JE_S), na = (n - n0) / ANTRIEB_JE + 1;
            float[][] et = new float[jf.length][na], qq = new float[jf.length][na];
            // seit Datei 83 (Befund L4o, Deich: H_m0 am Fuß 0,00 m): Diagnose je Lauf – Bilder mit trockenem Fuß, Stand am Rand
            int trockenFuss = 0, proben = 0;
            int npW = (int) Math.ceil(Auflauf.NY * Auflauf.DX / Auflauf.PUNKT_ABSTAND) + 1;
            double sRand = 0, qRand = 0;
            long nRand = 0;
            for (int q = 0; q <= n; q++) {
                double t = t0 + (double) q / BILDER_JE_S;
                Ablauf ab = new Ablauf(g);
                k.zeit(ab, t);
                k.bild(ab, bl.ost, bl.nord);
                au.vorbereiten(ab, null, k, t);
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                if (q >= n0 && (q - n0) % ANTRIEB_JE == 0) {
                    int a = (q - n0) / ANTRIEB_JE;
                    for (int zf = 0; zf < jf.length; zf++) {
                        int i = fussB[jf[zf]];
                        if (i < 0) continue;
                        float[] r = au.karte().zeileLesen(jf[zf]);
                        et[zf][a] = (float) (r[3 * i] + s.b[s.id(i, jf[zf])] - ruhe);
                        qq[zf][a] = r[3 * i + 1];
                        proben++;
                        if (r[3 * i] <= s.hTrocken) trockenFuss++;
                    }
                    float[] rw = au.randwelle().lies();
                    for (int p = 0; p < npW; p++) { double e = rw[8 * p]; sRand += e; qRand += e * e; nRand++; }
                }
            }
            double mRand = nRand > 0 ? sRand / nRand : 0, hRand = nRand > 0 ? 4 * Math.sqrt(Math.max(0, qRand / nRand - mRand * mRand)) : Double.NaN;
            if (!au.an) { A.info("Ohne Bauwerk, " + name, "Auflauf aus: " + au.bericht); return null; }
            List<Double> hi = new ArrayList<>(), ti = new ArrayList<>(), hg = new ArrayList<>(), xs = new ArrayList<>(), zs = new ArrayList<>();
            for (int zf = 0; zf < jf.length; zf++) {
                int i = fussB[jf[zf]];
                if (i < 0) continue;
                double tiefe = ruhe - s.b[s.id(i, jf[zf])];
                double[] w = punkt(et[zf], qq[zf], tiefe, ANTRIEB_JE / (double) BILDER_JE_S);
                if (w == null) continue;
                hi.add(w[0]); ti.add(w[1]); hg.add(w[2]); xs.add(s.x(i)); zs.add(-tiefe);
            }
            double aTrocken = proben > 0 ? (double) trockenFuss / proben : Double.NaN;
            A.info("Ohne Bauwerk, Diagnose (seit Datei 83), " + name, f("Stand am Rand aus dem Antrieb 4 σ %.2f m (über %d Punkte); Fuß trocken in %.1f %% der Proben; H_m0 am Fuß einlaufend je Zeile %s",
                    hRand, npW, 100 * aTrocken, hi.stream().map(x -> f("%.2f", x)).collect(java.util.stream.Collectors.joining(" "))));
            if (hi.isEmpty()) return null;
            double hm = median(hi);
            // seit Datei 83: unbrauchbar, wenn am Fuß kaum Seegang ankommt oder er meist trocken liegt (dann die Regel aus Datei 74)
            if (!(hm >= OHNE_MIN * hRand) || aTrocken > 0.5) {
                A.info("Ohne Bauwerk, " + name, f("Messung unbrauchbar: H_m0 am Fuß %.2f m gegen %.2f m am Rand (Grenze %.0f %%), Fuß trocken in %.0f %%", hm, hRand, 100 * OHNE_MIN, 100 * aTrocken));
                return new double[] {0, 0, median(hg), median(xs), median(zs), hi.size()};
            }
            return new double[] {hm, median(ti), median(hg), median(xs), median(zs), hi.size()};
        } finally {
            au.grundAendern = null;
            au.vergiss();
        }
    }

    /**
     * Seit Datei 73: hinter der Krone (je Zeile die Zellen landwärts des höchsten Grunds) {Wasser (m³), dort versickert seit
     * dem Setzen (m³), über das trockene Außen hinter der Krone hinaus (m³: E, S und N)}, aus dem letzten {@code runter}.
     */
    static double[] hinter(Auflauf au, Swash s, int[] ik, boolean[] see) {
        com.dan.physics.swash.gpu.SwashKarte kk = au.karte();
        double v = 0, vs = 0, aus = 0;
        for (int j = 0; j < s.ny; j++)
            for (int i = ik[j] + 1; i < s.nx; i++) { v += s.h[s.id(i, j)] * s.dx * s.dx; vs += kk.versickert(i, j); }
        double[] e = kk.randZufluss(Swash.E), so = kk.randZufluss(Swash.S), no = kk.randZufluss(Swash.N);
        for (int j = 0; j < s.ny; j++) if (ik[j] < s.nx - 1 && !see[j * s.nx + s.nx - 1]) aus -= e[j];
        for (int i = ik[0] + 1; i < s.nx - 1; i++) if (!see[i]) aus -= so[i];                       // die Ecke zählt E
        for (int i = ik[s.ny - 1] + 1; i < s.nx - 1; i++) if (!see[(s.ny - 1) * s.nx + i]) aus -= no[i];
        return new double[] {v, vs, aus};
    }

    /**
     * Seit Datei 74: EurOtop 2018 für H_m0 und T_m−1,0 (am Fuß oder am Rand): {tan α aus dem Profil (5.3.4, iteriert), Ru2 %,
     * Überlauf im Mittel}.
     */
    static double[] euro(Swash s, double ruhe, int[] ik, double hs, double tm, double rc, Kanten.Kante ka) {
        double tp11 = 1.1 * tm, ru = 1.5 * hs, tanA = ka.neigung();
        for (int it = 0; it < 6; it++) {
            double t = neigungProfil(s, ruhe, ik, hs, Math.min(ru, rc));
            if (!(t > 0)) break;
            tanA = t;
            ru = Brandung.ru2(hs, tp11, tanA, ka.rau());
        }
        return new double[] {tanA, ru, Brandung.ueberlauf(hs, tp11, tanA, ka.rau(), rc)};
    }

    /**
     * Seit Datei 74: Seegang an einem Punkt der Tiefe h (m) im Gitter: {H_m0 einlaufend, T_m−1,0 einlaufend, H_m0 gesamt}.
     * Getrennt mit der Beziehung des Lösers (Flachwasser, k/ω = 1/√(g h)): Im Gitter laufen die Wellen ohne Dispersion; mit
     * der linearen Dispersion wäre die einlaufende Welle im Container bis 20 % zu hoch (Tp 4,5 s in 2,8 m Tiefe).
     */
    static double[] punkt(float[] eta, float[] q, double h, double dt) {
        int nn = Integer.highestOneBit(eta.length);
        double[] acc = new double[5];
        trenne(eta, q, h, dt, nn, acc, null, null, true);
        double norm = 2.0 / ((double) nn * nn);
        return new double[] {4 * Math.sqrt(acc[0] * norm), acc[1] / acc[0], 4 * Math.sqrt(acc[3] * norm)};
    }

    static double median(List<Double> l) {
        if (l.isEmpty()) return Double.NaN;
        List<Double> m = new ArrayList<>(l);
        m.sort(Double::compare);
        return m.get(m.size() / 2);
    }

    /**
     * Seit Datei 73 (EurOtop 2018, 5.3.4): mittlere Neigung zwischen SWL − 1,5 H_m0 und SWL + oben (m über dem Ruhewasser),
     * je vierter Zeile von der See bis zur Krone, Median; NaN ohne Zeile.
     */
    static double neigungProfil(Swash s, double ruhe, int[] ik, double hm0, double oben) {
        List<Double> l = new ArrayList<>();
        for (int j = 0; j < s.ny; j += ZEILE_JEDE) {
            int i1 = -1, i2 = -1;
            for (int i = 0; i <= ik[j]; i++) {
                double z = s.b[s.id(i, j)] - ruhe;
                if (Double.isNaN(z)) continue;
                if (i1 < 0 && z >= -1.5 * hm0) i1 = i;
                if (i1 >= 0 && z >= oben) { i2 = i; break; }
            }
            if (i2 < 0) i2 = ik[j];
            if (i1 < 0 || i2 <= i1) continue;
            l.add((s.b[s.id(i2, j)] - s.b[s.id(i1, j)]) / ((i2 - i1) * s.dx));
        }
        if (l.isEmpty()) return Double.NaN;
        l.sort(Double::compare);
        return l.get(l.size() / 2);
    }

    /**
     * Seit Datei 73 (SF73): Seegang aus Stand η und Fluss q (senkrecht ins Gitter) an den Punkten W, Schritt dt (s): je
     * Frequenz zwischen {@link #F_VON} und {@link #F_BIS} die einlaufende Welle (η + q k/ω)/2 nach linearer Theorie in der
     * Tiefe des Punkts. {H_m0 einlaufend, T_m−1,0 einlaufend, Tp einlaufend, H_m0 gesamt, T_m−1,0 gesamt, Reflexion, Punkte,
     * seit Datei 74 H_m0 gesamt je Band 0,04–0,2, 0,2–0,5, 0,5–2 Hz};
     * Punkte mitten im Rand (ohne je 8 an den Enden).
     */
    static double[] welle(float[][] eta, float[][] qn, Swash s, double dt) {
        int np = eta.length, na = eta[0].length, nn = Integer.highestOneBit(na);
        double m0i = 0, m1i = 0, m0g = 0, m1g = 0, m0o = 0;
        double[] band = new double[3];
        double[] spek = new double[nn / 2 + 1];
        int pz = 0;
        for (int p = Math.min(8, np / 4); p < np - Math.min(8, np / 4); p++) {
            int j = (int) Math.max(0, Math.min(s.ny - 1, Math.round(p * Auflauf.PUNKT_ABSTAND / s.dx)));
            double h = s.bezug - s.b[s.id(0, j)];
            if (!(h > 0.1)) continue;
            double[] acc = new double[5];
            trenne(eta[p], qn[p], h, dt, nn, acc, band, spek, false);
            m0i += acc[0]; m1i += acc[1]; m0o += acc[2]; m0g += acc[3]; m1g += acc[4];
            pz++;
        }
        if (pz == 0) return new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN, Double.NaN};
        double norm = 2.0 / ((double) nn * nn * pz);
        // Tp: Spitze des über ± 0,01 Hz geglätteten einlaufenden Spektrums
        int bw = Math.max(1, (int) Math.round(0.01 * nn * dt));
        double best = -1, fp = Double.NaN;
        for (int kb = 1; kb <= nn / 2; kb++) {
            double f = kb / (nn * dt);
            if (f < F_VON || f > F_BIS) continue;
            double sm = 0;
            for (int d = -bw; d <= bw; d++) if (kb + d >= 1 && kb + d <= nn / 2) sm += spek[kb + d];
            if (sm > best) { best = sm; fp = f; }
        }
        return new double[] {4 * Math.sqrt(m0i * norm), m1i / m0i, 1 / fp, 4 * Math.sqrt(m0g * norm), m1g / m0g, Math.sqrt(m0o / m0i), pz,
            4 * Math.sqrt(band[0] * norm), 4 * Math.sqrt(band[1] * norm), 4 * Math.sqrt(band[2] * norm)};
    }

    /**
     * Seit Datei 78 (S6c-1b, SF85 A): Anteil der Energie längs der Punkte am Rand (Abstand dy, alle gleich tief h), der im
     * Löser frei läuft: Spektrum über Frequenz und Wellenzahl längs k_y (Hann in Zeit und Ort, die letzten 2^n Aufnahmen,
     * längs auf 2 · 2^m Stellen aufgefüllt), Anteil mit |k_y| < ω/√(g h) je Band (bis 0,2 Hz, 0,2–0,5 Hz, darüber) und
     * gesamt, dazu gesamt mit |k_y| < k(h) (linear). {Band 0, 1, 2, gesamt, gesamt linear}; ohne Energie NaN.
     */
    static double[] freiLaufend(float[][] eta, double h, double dt, double dy) {
        int np = eta.length, na = eta[0].length, nn = Integer.highestOneBit(na), m = 2 * Integer.highestOneBit(2 * np - 1);
        double[][] fr = new double[np][], fi = new double[np][];
        double[] wt = new double[nn];
        for (int a = 0; a < nn; a++) wt[a] = 0.5 - 0.5 * Math.cos(2 * Math.PI * a / (nn - 1));
        for (int p = 0; p < np; p++) {
            double me = 0;
            for (int a = na - nn; a < na; a++) me += eta[p][a];
            me /= nn;
            fr[p] = new double[nn]; fi[p] = new double[nn];
            for (int a = 0; a < nn; a++) fr[p][a] = (eta[p][na - nn + a] - me) * wt[a];
            fft(fr[p], fi[p]);
        }
        double[] frei = new double[3], alle = new double[3];
        double lin = 0, ges = 0;
        double[] xr = new double[m], xi = new double[m];
        for (int kb = 1; kb < nn / 2; kb++) {
            double f = kb / (nn * dt);
            if (f < F_VON || f > F_BIS) continue;
            java.util.Arrays.fill(xr, 0);
            java.util.Arrays.fill(xi, 0);
            for (int p = 0; p < np; p++) { double w = np > 1 ? 0.5 - 0.5 * Math.cos(2 * Math.PI * p / (np - 1)) : 1; xr[p] = w * fr[p][kb]; xi[p] = w * fi[p][kb]; }
            fft(xr, xi);
            double om = 2 * Math.PI * f, kSw = om / Math.sqrt(9.81 * h), kLin = Dispersion.wellenzahl(om, h);
            int band = f < 0.2 ? 0 : f < 0.5 ? 1 : 2;
            for (int j = 0; j < m; j++) {
                double ky = 2 * Math.PI * (j <= m / 2 ? j : j - m) / (m * dy), e = xr[j] * xr[j] + xi[j] * xi[j];
                alle[band] += e; ges += e;
                if (Math.abs(ky) < kSw) frei[band] += e;
                if (Math.abs(ky) < kLin) lin += e;
            }
        }
        double fg = frei[0] + frei[1] + frei[2];
        return new double[] {frei[0] / alle[0], frei[1] / alle[1], frei[2] / alle[2], ges > 0 ? fg / ges : Double.NaN, ges > 0 ? lin / ges : Double.NaN};
    }

    /**
     * Seit Datei 75 (SF80 A): Richtung der einlaufenden Welle am Rand gegen die Normale (°, + nach +y), je Frequenz aus den
     * Kreuzspektren: Für eine einlaufende (Energie E_i, Winkel θ) und ihre Reflexion (E_r, −θ quer) gilt ⟨η q_n⟩ ∝ (E_i − E_r)
     * cos θ und ⟨η q_t⟩ ∝ (E_i + E_r) sin θ. {|θ| nach E_i gemittelt, θ nach E_i gemittelt, θ je Band bis 0,2, 0,2–0,5,
     * darüber}; Punkte wie {@link #welle}.
     */
    static double[] richtung(float[][] eta, float[][] qn, float[][] qt, Swash s, double dt) {
        int np = eta.length, na = eta[0].length, nn = Integer.highestOneBit(na);
        double wa = 0, wv = 0, w = 0;
        double[] bt = new double[3], bw = new double[3];
        for (int p = Math.min(8, np / 4); p < np - Math.min(8, np / 4); p++) {
            int j = (int) Math.max(0, Math.min(s.ny - 1, Math.round(p * Auflauf.PUNKT_ABSTAND / s.dx)));
            double h = s.bezug - s.b[s.id(0, j)];
            if (!(h > 0.1)) continue;
            double[] er = new double[nn], ei = new double[nn], nr = new double[nn], ni = new double[nn], tr = new double[nn], ti = new double[nn];
            double me = 0, mn = 0, mt = 0;
            for (int a = na - nn; a < na; a++) { me += eta[p][a]; mn += qn[p][a]; mt += qt[p][a]; }
            me /= nn; mn /= nn; mt /= nn;
            for (int a = 0; a < nn; a++) { er[a] = eta[p][na - nn + a] - me; nr[a] = qn[p][na - nn + a] - mn; tr[a] = qt[p][na - nn + a] - mt; }
            fft(er, ei); fft(nr, ni); fft(tr, ti);
            for (int kb = 1; kb <= nn / 2; kb++) {
                double f = kb / (nn * dt);
                if (f < F_VON || f > F_BIS) continue;
                double om = 2 * Math.PI * f, c = Dispersion.wellenzahl(om, h) / om;
                double ar = 0.5 * (er[kb] + c * nr[kb]), ai = 0.5 * (ei[kb] + c * ni[kb]), br = 0.5 * (er[kb] - c * nr[kb]), bi = 0.5 * (ei[kb] - c * ni[kb]);
                double eiE = ar * ar + ai * ai, erE = br * br + bi * bi;
                double xn = (er[kb] * nr[kb] + ei[kb] * ni[kb]) * c, xt = (er[kb] * tr[kb] + ei[kb] * ti[kb]) * c;
                if (!(eiE > 0) || !(eiE + erE > 0) || !(Math.abs(eiE - erE) > 1e-12 * eiE)) continue;
                double th = Math.toDegrees(Math.atan2(xt / (eiE + erE), xn / (eiE - erE)));
                wa += eiE * Math.abs(th); wv += eiE * th; w += eiE;
                int band = f < 0.2 ? 0 : f < 0.5 ? 1 : 2;
                bt[band] += eiE * th; bw[band] += eiE;
            }
        }
        return new double[] {wa / w, wv / w, bt[0] / bw[0], bt[1] / bw[1], bt[2] / bw[2]};
    }

    /**
     * Seit Datei 74 (aus {@link #welle}): Trennung an einem Punkt der Tiefe h über die letzten nn Werte; addiert zu acc {m₀
     * einlaufend, m₋₁ einlaufend, m₀ auslaufend, m₀ gesamt, m₋₁ gesamt} (ungenormt), zu band m₀ gesamt je Band und zu spek
     * die einlaufende Energie je Frequenz (beide dürfen null sein). flach: k/ω = 1/√(g h) wie im Löser, sonst lineare
     * Dispersion (am Rand: so rechnet die Synthese).
     */
    static void trenne(float[] eta, float[] qn, double h, double dt, int nn, double[] acc, double[] band, double[] spek, boolean flach) {
        int na = eta.length;
        double[] er = new double[nn], ei = new double[nn], qr = new double[nn], qi = new double[nn];
        double me = 0, mq = 0;
        for (int a = na - nn; a < na; a++) { me += eta[a]; mq += qn[a]; }
        me /= nn;
        mq /= nn;
        for (int a = 0; a < nn; a++) { er[a] = eta[na - nn + a] - me; qr[a] = qn[na - nn + a] - mq; }
        fft(er, ei);
        fft(qr, qi);
        for (int kb = 1; kb <= nn / 2; kb++) {
            double f = kb / (nn * dt);
            if (f < F_VON || f > F_BIS) continue;
            double om = 2 * Math.PI * f, c = flach ? 1 / Math.sqrt(9.81 * h) : Dispersion.wellenzahl(om, h) / om;
            double ar = 0.5 * (er[kb] + c * qr[kb]), ai = 0.5 * (ei[kb] + c * qi[kb]), br = 0.5 * (er[kb] - c * qr[kb]), bi = 0.5 * (ei[kb] - c * qi[kb]);
            double pe = ar * ar + ai * ai, po = br * br + bi * bi, pg = er[kb] * er[kb] + ei[kb] * ei[kb];
            acc[0] += pe; acc[1] += pe / f; acc[2] += po; acc[3] += pg; acc[4] += pg / f;
            if (band != null) band[f < 0.2 ? 0 : f < 0.5 ? 1 : 2] += pg;
            if (spek != null) spek[kb] += pe;
        }
    }

    /**
     * Seit Datei 74 (SF76 A): Seegang am Ort (UTM) aus den Quellen: Netze (Hs, Tp), die Oberfläche der Synthese (H_m0 aus
     * m0, T_m01, Gewichte der Stufen), je Stufe (fein, grob) H_m0 der nächsten Zelle je Band (unter 0,2 Hz, 0,2–0,5 Hz,
     * darüber) und das Fernfeld.
     */
    static String quellen(Seegangsflaeche sf, List<com.dan.core.szene.Seegangsnetz> netze, double t, double ost, double nord) {
        com.dan.core.szene.Oberflaeche ob = sf.oberflaeche;
        com.dan.core.szene.Oberflaeche.Punkt p = new com.dan.core.szene.Oberflaeche.Punkt();
        ob.punkt(ost, nord, t, p);
        double tm = ob.tm01(ost, nord);
        double[] nz = netze == null || netze.isEmpty() ? null : com.dan.core.szene.Seegangsoptik.werte(netze, t, ost, nord);
        StringBuilder sb = new StringBuilder();
        sb.append(f("Netze Hs %.2f m, Tp %.1f s; Oberfläche H_m0 %.2f m, T_m01 %.2f s (Gewichte fein %.2f, grob %.2f, fern %.2f)",
                nz != null ? nz[0] : Double.NaN, nz != null ? nz[1] : Double.NaN, 4 * Math.sqrt(Math.max(0, p.m0)), tm, p.wFein, p.wGrob, p.wFern));
        for (com.dan.core.szene.Oberflaeche.Stufe st : new com.dan.core.szene.Oberflaeche.Stufe[] {ob.fein, ob.grob}) {
            if (st == null || st.kenn == null) continue;
            int i = (int) Math.floor((ost - st.x0) / st.dx), j = (int) Math.floor((nord - st.y0) / st.dx);
            if (i < 0 || j < 0 || i >= st.nx || j >= st.ny) { sb.append(f("; %s: außerhalb", st.name)); continue; }
            int n = st.kennWerte(), c = j * st.nx + i;
            double m0 = 0;
            double[] bd = new double[3];
            for (int bb = 0; bb < st.nb; bb++) {
                double e = st.kenn[c * n + 2 + bb], fq = ob.omega[bb] / (2 * Math.PI);
                m0 += e;
                bd[fq < 0.2 ? 0 : fq < 0.5 ? 1 : 2] += e;
            }
            sb.append(f("; %s (Zelle %.0f m): H_m0 %.2f m (bis 0,2 Hz %.2f, 0,2–0,5 Hz %.2f, darüber %.2f; Bänder %.2f–%.2f Hz)", st.name, st.dx,
                    4 * Math.sqrt(m0), 4 * Math.sqrt(bd[0]), 4 * Math.sqrt(bd[1]), 4 * Math.sqrt(bd[2]), ob.omega[0] / (2 * Math.PI), ob.omega[st.nb - 1] / (2 * Math.PI)));
        }
        if (ob.fernfeld != null) { double[] w = ob.fernfeld.werte(ost, nord); sb.append(f("; Fernfeld Hs %.2f m, Tp %.1f s", w[0], w[1])); }
        return sb.toString();
    }

    /**
     * Seit Datei 75 (SF78 A): Wind am Ort, Fetch luvwärts über Wasser (Schritt 25 m, bis 50 km, Ende unter 0,1 m Tiefe),
     * mittlere Tiefe darauf, H_m0 und Tp nach Young und Verhagen (1996) gegen die Windsee des Nahfelds am Ort (Bänder ab
     * 0,2 Hz der feinen Stufe, sonst der groben).
     */
    static String windsee(Szenen sz, Szenario sc, Seegangsflaeche sf, double t, double ost, double nord) {
        try (com.dan.lauf.Nahfeldquelle q = new com.dan.lauf.Nahfeldquelle(sz.gebiet(), sc.archiv.toPath())) {
            double[] w = q.windBei(ost, nord, t);
            if (w == null) return "kein Wind im Archiv";
            double u = Math.hypot(w[0], w[1]);
            if (!(u > 1)) return f("Wind %.1f m/s: keine Windsee", u);
            double ex = -w[0] / u, ey = -w[1] / u, aus = (Math.toDegrees(Math.atan2(ex, ey)) + 360) % 360;
            double[] fe = q.fetch(ost, nord, ex, ey, t, 25, 50000, 0.1);
            double[] yv = com.dan.lauf.Nahfeldquelle.youngVerhagen(u, Math.max(25, fe[0]), Math.max(0.1, fe[1]));
            double[] yg = com.dan.lauf.Nahfeldquelle.youngVerhagen(u, 1e7, Math.max(0.1, fe[1]));
            com.dan.core.szene.Oberflaeche ob = sf.oberflaeche;
            double mw = Double.NaN, mg = Double.NaN;
            String stufe = "außerhalb";
            for (com.dan.core.szene.Oberflaeche.Stufe st : new com.dan.core.szene.Oberflaeche.Stufe[] {ob.fein, ob.grob}) {
                if (st == null || st.kenn == null) continue;
                int i = (int) Math.floor((ost - st.x0) / st.dx), j = (int) Math.floor((nord - st.y0) / st.dx);
                if (i < 0 || j < 0 || i >= st.nx || j >= st.ny) continue;
                int n = st.kennWerte(), c = j * st.nx + i;
                mw = 0; mg = 0;
                for (int bb = 0; bb < st.nb; bb++) { double e = st.kenn[c * n + 2 + bb]; mg += e; if (ob.omega[bb] / (2 * Math.PI) >= 0.2) mw += e; }
                stufe = st.name;
                break;
            }
            double hw = 4 * Math.sqrt(mw);
            return f("Wind %.1f m/s aus %03.0f°; Fetch luvwärts %.1f km (%s), Tiefe darauf im Mittel %.2f m; Young und Verhagen: H_m0 %.2f m, Tp %.1f s (Fetch unbegrenzt: %.2f m, %.1f s); Nahfeld (%s) ab 0,2 Hz: H_m0 %.2f m (gesamt %.2f m), gegen Young und Verhagen %.2f",
                    u, aus, fe[0] / 1000, fe[2] > 0.5 ? "bis Land" : "Grenze", fe[1], yv[0], yv[1], yg[0], yg[1], stufe, hw, 4 * Math.sqrt(mg), hw / yv[0]);
        } catch (Exception e) {
            return "nicht bestimmt: " + e;
        }
    }

    /** FFT an Ort und Stelle (Radix 2, Länge eine Zweierpotenz). */
    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double w = -2 * Math.PI / len, wr = Math.cos(w), wi = Math.sin(w);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }

    /**
     * Seit Datei 73 (SF72): Blick auf einen Strand nach den Regeln (Art 0–2, mindestens {@link #SAND_ABSTAND} m von Deich,
     * Warft und Deckwerk, tan α ≤ {@link #SAND_TAN}, H_s am Fuß ≥ {@link #SAND_HS}) mit dem höchsten Seegang; null ohne.
     */
    static Blick strand(Attest A, Buehne b, double t0, int breite, int hoehe, String titel) {
        com.dan.render.brandung.Brandungsbild bb = b.brandungsbild();
        if (bb == null || bb.quelle == null) { A.info("Strand, " + titel, "keine Brandung"); return null; }
        Brandung such = new Brandung(bb.brandung.e0, bb.brandung.n0);
        such.weite = 1e9;
        such.abschnitteMax = Integer.MAX_VALUE;
        such.protokollieren = true;
        double[] w = bb.quelle.wind();
        such.rechne(bb.kanten, bb.quelle.wasser(), t0, bb.brandung.e0, bb.brandung.n0, w[0], w[1]);
        List<Kanten.Kante> alle = bb.kanten.kanten;
        double[] wahl = null;
        int aktiv = 0, regel = 0;
        for (double[] q : such.protokoll) {
            Kanten.Kante ka = alle.get((int) q[0]);
            if (ka.art() > Gestalt.SALZWIESE) continue;
            aktiv++;
            if (ka.neigung() > SAND_TAN || q[6] < SAND_HS) continue;
            double ab = Double.POSITIVE_INFINITY;
            for (Kanten.Kante o : alle) if (o.art() >= Gestalt.DEICH && o.art() <= Gestalt.DECKWERK) ab = Math.min(ab, Math.hypot(o.ost() - ka.ost(), o.nord() - ka.nord()));
            if (ab < SAND_ABSTAND) continue;
            regel++;
            if (wahl == null || q[6] > wahl[6]) wahl = q;
        }
        A.info("Strand, " + titel, f("aktive Kanten der Arten 0–2: %d, davon nach den Regeln: %d", aktiv, regel));
        if (wahl == null) return null;
        Kanten.Kante ka = alle.get((int) wahl[0]);
        AuflaufCheck.letzteKante = ka;
        AuflaufCheck.letzteWahl = wahl;
        double ost = ka.ost() + WalzeCheck.KANTE_ABSTAND * ka.nx(), nord = ka.nord() + WalzeCheck.KANTE_ABSTAND * ka.ny();
        double[] wa = b.wasserUndSeegang(ost, nord);
        double et = wa != null && Double.isFinite(wa[0]) ? wa[0] : 0;
        Blick bl = new Blick(ost, nord, et + WalzeCheck.NAH_HOEHE, Math.toDegrees(Math.atan2(-ka.nx(), -ka.ny())), WalzeCheck.NAH_NEIGUNG, 60);
        bl.groesse(breite, hoehe);
        A.info("Blick auf den Strand, " + titel, f("Kante %d (Art %d, Fuß %.2f m, Krone %.2f m NHN, 1:%.1f) bei (%.0f, %.0f) mit Hs am Fuß %.2f m und Tp %.1f s; Auge %.0f m seewärts",
                (int) wahl[0], ka.art(), ka.fuss(), ka.krone(), 1 / ka.neigung(), ka.ost(), ka.nord(), wahl[6], wahl[7], WalzeCheck.KANTE_ABSTAND));
        return bl;
    }

    /** Was die Landseite bisher verlassen hat (m³), aus dem letzten {@code runter}. */
    static double randAus(Auflauf au) {
        double a = 0;
        for (double x : au.karte().randZufluss(Swash.E)) a -= x;
        return a;
    }

    /** Verhältnis H / H₀ bei linearem Shoaling auf die Tiefe h (Tp). */
    static double shoaling(double tp, double h) {
        if (!(tp > 0) || !(h > 0)) return 1;
        double sigma = 2 * Math.PI / tp, k = Dispersion.wellenzahl(sigma, h), cg = Dispersion.gruppe(sigma, k, h), cg0 = 9.81 / (2 * sigma);
        return Math.sqrt(cg0 / cg);
    }

    /**
     * Maxima des Saums Welle für Welle (wie Stockdon u. a. 2006): {Mittel, Maximum je Welle …}; die Wellen zwischen zwei
     * Aufwärtsdurchgängen durch die Ausgleichsgerade der Reihe (Hysterese ± {@link #HYSTERESE} σ der Abweichung); die
     * Maxima über dem Spiegel (nicht über der Geraden). null, wenn die Reihe zu viele Lücken hat.
     */
    static double[] maxima(float[] r) {
        double st = 0, sx = 0, stt = 0, stx = 0;
        int n = 0;
        for (int i = 0; i < r.length; i++) if (Float.isFinite(r[i])) { st += i; sx += r[i]; stt += (double) i * i; stx += (double) i * r[i]; n++; }
        if (n < 0.9 * r.length || n < 10) return null;
        double nen = n * stt - st * st, b = nen > 0 ? (n * stx - st * sx) / nen : 0, a = (sx - b * st) / n, s2 = 0;
        for (int i = 0; i < r.length; i++) if (Float.isFinite(r[i])) { double d = r[i] - a - b * i; s2 += d * d; }
        double eps = HYSTERESE * Math.sqrt(s2 / n);
        List<Double> w = new ArrayList<>();
        w.add(sx / n);
        boolean oben = false, begonnen = false;
        double m = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < r.length; i++) {
            float x = r[i];
            if (!Float.isFinite(x)) continue;
            double d = x - a - b * i;
            if (!oben && d > eps) {                                           // Aufwärtsdurchgang: eine neue Welle beginnt
                if (begonnen) w.add(m);
                begonnen = true;
                oben = true;
                m = x;
            } else if (oben && d < -eps) oben = false;
            if (begonnen) m = Math.max(m, x);
        }
        return w.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
