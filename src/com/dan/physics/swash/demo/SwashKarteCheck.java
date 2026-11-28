package com.dan.physics.swash.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.physics.swash.Boden;
import com.dan.physics.swash.Swash;
import com.dan.physics.swash.gpu.SwashKarte;

import java.nio.file.Path;
import java.util.function.Supplier;

import static com.dan.core.pruef.Attest.f;

/**
 * Der Löser auf der Karte gegen die Referenz (Seegang Werkbuch, S6b-1; Grenzen [G] vor der Messung, Datei 66): dieselben
 * Fälle wie {@link SwashCheck}, je mit dem Zeitschritt und dem Antrieb der Referenz, Karte in float gegen Referenz in double.
 * <ol>
 * <li>Ruhender See: |u| auf der Karte ≤ 1e-4 m/s.</li>
 * <li>Volumen ohne und mit Versickern ≤ 1e-6 relativ, abgeschnitten ≤ 1e-7.</li>
 * <li>Stoker: Σ|h_Karte − h_Referenz| Δx / ((h_L − h_R) (S + c_L) t) ≤ 1e-4.</li>
 * <li>Synolakis: Auflauf der Karte gegen den der Referenz ± 1 %.</li>
 * <li>Carrier und Greenspan (Rand mit Antrieb): ± 2 %.</li>
 * <li>Zunge: ± 2 %.</li>
 * <li>Versickern: versickertes Wasser nach 25, 75 und 150 s ± 1 %.</li>
 * <li>Manning: Geschwindigkeit nach 10 s ± 1e-4.</li>
 * <li>Kosten: das Rechteck 1024 × 256 zu 0,25 m, ein Schritt auf der Karte ≤ 0,9 ms (Ziel ≤ 1 ms je Bild bei rund einem
 * Schritt je Bild); auf einem Software-Gerät nur Auskunft. Dazu das Tempo je Zeile gegen die Referenz. Seit S6b-1b (Grenze
 * vor der Messung): dasselbe für das Rechteck wie im Lauf (64 m quer zum Ufer in x, 256 m längs in y, 1:30, Wasserlinie
 * 24 m vor dem Landrand); der einzelne Weg als Auskunft.</li>
 * <li>Seit S6b-1b: der verschmolzene Weg gegen den einzelnen auf der Karte, größte Abweichung der Tiefe ≤ 1e-5 m (Becken,
 * Carrier und Greenspan).</li>
 * <li>Seit Swash 0.3 (SF74 A): Steilhang 1:2,9, JONSWAP, R2 % gegen EurOtop ± 10 %.</li>
 * <li>Seit Swash 0.3 (SF75 A): Strand 1:12, JONSWAP, R2 % gegen Stockdon u. a. (2006) ± 20 %; seit Seegang Werkbuch 75
 * (SF77 A) kurzkämmig in 2D (64 m breit, cos^2s(θ/2) mit s 10, Wände an S und N), R2 % aus den Maxima von 12 Zeilen; dazu
 * als Auskunft Stau, S_inc und S_IG gegen Stockdon und der Fall langkämmig in 1D. Seit Werkbuch 76 (SF82 A) mit der
 * Wasserlinie bei 5 cm und ± 30 %.</li>
 * <li>Seit Seegang Werkbuch 76 (SF81 A): Überlauf im Kanal in 1D (Hang 1:2,4, Krone 0,5 m breit, dahinter ein Becken),
 * JONSWAP senkrecht, gegen EurOtop 2018 (Mittelwert) innerhalb eines Faktors 3, zwei Fälle wie am Deckwerk und am Deich.</li>
 * </ol>
 * Die Teile 1–8 rechnen auf dem verschmolzenen Weg.
 * Attest {@code docs/status/S6b_SwashKarte.md}.
 */
public final class SwashKarteCheck {
    static final Attest A = new Attest("com.dan.physics.swash.gpu (SwashKarte)", Swash.VERSION);
    static final double GR = 9.81;
    static final int BLOCK = 256;
    static Geraet geraet;
    static Konto konto;
    /** Weg der Karte in {@link #paar} (seit S6b-1b verschmolzen, Vorgabe). */
    static boolean verschmolzen = true;

    public static void main(String[] args) {
        String nur = args.length >= 2 && args[0].equals("--teil") ? args[1] : null;
        long t0 = System.nanoTime();
        try (Geraet g = new Geraet()) {
            geraet = g;
            konto = g.konto("swash", 2L << 30);
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            if (nur == null || nur.equals("1")) ruhe();
            if (nur == null || nur.equals("2")) volumen();
            if (nur == null || nur.equals("3")) stoker();
            if (nur == null || nur.equals("4")) synolakis();
            if (nur == null || nur.equals("5")) carrierGreenspan();
            if (nur == null || nur.equals("6")) zunge();
            if (nur == null || nur.equals("7")) darcy();
            if (nur == null || nur.equals("8")) manning();
            if (nur == null || nur.equals("9")) kosten();
            if (nur == null || nur.equals("10")) wege();
            if (nur == null || nur.equals("11")) steilhang();
            if (nur == null || nur.equals("12")) strand();
            if (nur == null || nur.equals("13")) ueberlauf();
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) / 1e9));
        if (nur == null) {
            Path p = A.schreibe(Path.of("docs", "status", "S6b_SwashKarte.md"), "S6b-1 – Auflauf und Rücklauf auf der Karte gegen die Referenz (Swash " + Swash.VERSION + ")");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 11 und 12: unregelmäßiger Seegang (seit Swash 0.3)

    /** Seit Swash 0.3 (SF74 A, SF75 A): Grenzen [G]: Steilhang gegen EurOtop ± 10 %, Strand gegen Stockdon ± 20 %. */
    static final double STEIL_TOL = 0.10;
    /** Seit Seegang Werkbuch 76 (SF82 A): ± 30 % (Streuung von Stockdon u. a. 2006: 38 cm RMS bei 144 cm, rund 26 %) [G]. */
    static final double STRAND_TOL = 0.30;
    /**
     * Seit Seegang Werkbuch 76 (SF82 A): Kontur der Wasserlinie (m Wasser) für den Vergleich mit Stockdon wie in Studien mit
     * SWASH (5 cm); sonst 1 cm (SF60).
     */
    static final double STOCKDON_KONTUR = 0.05, KONTUR = 0.01;
    /** Seit Seegang Werkbuch 75 (SF77 A): Breite (m) und Streuung s (cos^2s(θ/2)) des kurzkämmigen Strands [G]. */
    static final double STRAND_BREITE = 64, STRAND_S = 10;

    static void steilhang() {
        A.teil("11 Unregelmäßiger Seegang am Steilhang 1:2,9 gegen EurOtop (seit Swash 0.3, SF74 A)");
        double hm0 = 0.55, tp = 5.3, tan = 0.35;
        double[] r = jonswapLauf(hm0, tp, tan, 3.0, 660, 60, 0.25, 8);
        double tm = r[3], l = GR * tm * tm / (2 * Math.PI), xi = tan / Math.sqrt(hm0 / l), ru = hm0 * Math.min(1.65 * xi, 4 - 1.5 / Math.sqrt(xi));
        A.info("Fall", f("JONSWAP H_m0 %.2f m, Tp %.1f s (T_m−1,0 %.2f s) in 3 m Tiefe, glatter Hang 1:%.2f, 0,25 m, 660 s (ausgewertet ab 60 s); EurOtop 2018 glatt: ξ %.2f, Ru2 %% %.3f m", hm0, tp, tm, 1 / tan, xi, ru));
        A.info("Auflauf", f("R2 %% Karte %.3f m (%d Wellen), Referenz %.3f m (%d Wellen); Teilgitter aus (Referenz): %.3f m", r[0], (int) r[4], r[1], (int) r[5], r[2]));
        A.hoechstens("R2 % der Karte gegen EurOtop, relativ, Betrag", Math.abs(r[0] / ru - 1), STEIL_TOL, "");
    }

    static void strand() {
        A.teil("12 Unregelmäßiger Seegang am Strand 1:12 gegen Stockdon u. a. (2006) (seit Swash 0.3, SF75 A)");
        double hm0 = 1.0, tp = 8, tan = 1 / 12.0, h0 = 3.0;
        double[] r = jonswapLauf(hm0, tp, tan, h0, 660, 60, 0.25, 8, STOCKDON_KONTUR);
        double sigma = 2 * Math.PI / tp, k = com.dan.core.szene.Dispersion.wellenzahl(sigma, h0), cg = com.dan.core.szene.Dispersion.gruppe(sigma, k, h0);
        double ks = Math.sqrt(GR / (2 * sigma) / cg), hd = hm0 / ks, l0 = GR * tp * tp / (2 * Math.PI), hl = Math.sqrt(hd * l0);
        double st = 1.1 * (0.35 * tan * hl + Math.sqrt(hd * l0 * (0.563 * tan * tan + 0.004)) / 2);
        double stauE = 0.35 * tan * hl, sincE = 0.75 * tan * hl, sigE = 0.06 * hl;
        A.info("Fall", f("JONSWAP H_m0 %.2f m, Tp %.1f s in %.0f m Tiefe, Hang 1:%.0f, 0,25 m, 660 s (ausgewertet ab 60 s); Stockdon: H₀ %.2f m (Ks %.3f), ξ₀ %.2f, R2 %% %.3f m (Stau %.3f, S_inc %.3f, S_IG %.3f m)",
                hm0, tp, h0, 1 / tan, hd, ks, tan / Math.sqrt(hd / l0), st, stauE, sincE, sigE));
        A.info("Langkämmig in 1D (Auskunft, wie seit SF75)", f("Wasserlinie bei %.0f cm (SF82 A): R2 %% Karte %.3f m (%d Wellen, %+.0f %%), Referenz %.3f m (%d Wellen); Teilgitter aus (Referenz): %.3f m",
                100 * STOCKDON_KONTUR, r[0], (int) r[4], 100 * (r[0] / st - 1), r[1], (int) r[5], r[2]));
        // seit Seegang Werkbuch 75 (SF77 A): kurzkämmig in 2D; Stockdon beruht auf natürlichen Stränden
        double[] q = jonswapLauf2D(hm0, tp, tan, h0, 660, 60, 0.25, STRAND_BREITE, STRAND_S, STOCKDON_KONTUR);
        A.info("Kurzkämmig in 2D (SF77 A)", f("%.0f m breit, cos^2s(θ/2) mit s %.0f (σ_θ der Komponenten %.1f°), Wände an S und N; Wasserlinie bei %.0f cm (SF82 A); R2 %% aus den Maxima von %d Zeilen: Karte %.3f m (%d Wellen), Referenz %.3f m (%d Wellen); zum Vergleich Referenz mit 1 cm: R2 %% %.3f m, Stau %.3f m",
                STRAND_BREITE, STRAND_S, q[7], 100 * STOCKDON_KONTUR, (int) q[8], q[0], (int) q[2], q[1], (int) q[3], q[9], q[10]));
        A.info("Stau und Schwall gegen Stockdon (Auskunft, SF77 A)", f("Karte: Stau (Saum im Mittel) %.3f m gegen %.3f (%+.0f %%), S_inc %.3f gegen %.3f (%+.0f %%), S_IG %.3f gegen %.3f m (%+.0f %%); S = 4 σ des Saums über bzw. unter 0,05 Hz, Wasserlinie bei %.0f cm",
                q[4], stauE, 100 * (q[4] / stauE - 1), q[5], sincE, 100 * (q[5] / sincE - 1), q[6], sigE, 100 * (q[6] / sigE - 1), 100 * STOCKDON_KONTUR));
        A.hoechstens("R2 % der Karte gegen Stockdon (kurzkämmig), relativ, Betrag", Math.abs(q[0] / st - 1), STRAND_TOL, "");
    }

    // ------------------------------------------------------------------ 13: Überlauf im Kanal (seit Seegang Werkbuch 76)

    /** Seit Seegang Werkbuch 76 (SF81 A): Überlauf gegen EurOtop innerhalb dieses Faktors (Streuung der Formel) [G]. */
    static final double UEBER_FAKTOR = 3;

    static void ueberlauf() {
        A.teil("13 Überlauf im Kanal in 1D gegen EurOtop (seit Seegang Werkbuch 76, SF81 A)");
        // H_m0, Tp, R_c (m) wie am Fuß von Deckwerk und Deich in L4j; Hang 1:2,4 wie dort aus dem Profil
        double[][] faelle = {{0.52, 7.0, 0.98}, {0.57, 7.4, 0.92}};
        double tan = 0.42;
        for (double[] fa : faelle) {
            double[] r = ueberlaufLauf(fa[0], fa[1], tan, fa[2], 0.5, 660, 60, 0.25);
            double qe = com.dan.core.szene.Brandung.ueberlauf(fa[0], 1.1 * r[2], tan, 1.0, fa[2]);
            String fall = f("H_m0 %.2f m, R_c %.2f m", fa[0], fa[2]);
            A.info("Fall, " + fall, f("JONSWAP Tp %.1f s (T_m−1,0 %.2f s) in 3 m Tiefe, glatter Hang 1:%.1f, Krone 0,5 m breit, dahinter ein Becken; 0,25 m, 660 s (ausgewertet ab 60 s); EurOtop 2018 (Mittelwert) %.2e m³/s je m",
                    fa[1], r[2], 1 / tan, qe));
            A.info("Überlauf, " + fall, f("Karte %.2e, Referenz %.2e m³/s je m", r[0], r[1]));
            double v = r[0] / qe;
            A.pruefe(v >= 1 / UEBER_FAKTOR && v <= UEBER_FAKTOR, "Überlauf der Karte gegen EurOtop, Verhältnis, " + fall, f("%.2f", v), f("%.2f … %.0f", 1 / UEBER_FAKTOR, UEBER_FAKTOR));
        }
        // seit Seegang Werkbuch 82 (SF95 A): flacher Fuß und niedrige Krone wie am Deich von Hooge (Container: Löser 0,42 von
        // EurOtop); H_m0 und T_m−1,0 am Fuß aus einer Rechnung ohne Bauwerk (SF94 A, EurOtop 2018)
        double[] r = ueberlaufFlach(FLACH[0], FLACH[1], FLACH[2], FLACH[3], FLACH[4], FLACH[5], 2.0, 660, 60, 0.25);
        double qe = com.dan.core.szene.Brandung.ueberlauf(r[2], 1.1 * r[3], FLACH[4], 1.0, FLACH[5]);
        String fall = f("flacher Fuß %.1f m, H_m0 %.2f m, R_c %.2f m", FLACH[3], FLACH[0], FLACH[5]);
        A.info("Fall, " + fall, f("JONSWAP H_m0 %.2f m, Tp %.1f s in %.2f m Tiefe, Vorland 1:10 bis %.2f m, glatter Hang 1:%.1f bis R_c %.2f m, Krone 2 m breit, dahinter ein Becken; 0,25 m, 660 s (ab 60 s); am Fuß ohne Bauwerk (Strand 1:30) einlaufend H_m0 %.3f m, T_m−1,0 %.2f s; EurOtop 2018 (Mittelwert) %.2e m³/s je m",
                FLACH[0], FLACH[1], FLACH[2], FLACH[3], 1 / FLACH[4], FLACH[5], r[2], r[3], qe));
        A.info("Überlauf, " + fall, f("Karte %.2e, Referenz %.2e m³/s je m", r[0], r[1]));
        double v = r[0] / qe;
        A.pruefe(v >= 1 / UEBER_FAKTOR && v <= UEBER_FAKTOR, "Überlauf der Karte gegen EurOtop, Verhältnis, " + fall, f("%.2f", v), f("%.2f … %.0f", 1 / UEBER_FAKTOR, UEBER_FAKTOR));
    }

    /** Seit Seegang Werkbuch 82 (SF95 A): der Fall mit flachem Fuß {H_m0, Tp, Tiefe am Rand, Tiefe am Fuß, tan α, R_c} [G: Deich von Hooge, L4n]. */
    static final double[] FLACH = {0.88, 6.6, 2.8, 1.3, 1 / 2.6, 0.92};

    /**
     * Seit Seegang Werkbuch 82 (SF95 A): Kanal (ny 4) mit 8 m ebenem Grund in h0, Vorland 1:10 bis zur Tiefe hf, Hang tan bis
     * R_c, Krone gc breit, dahinter 1:2 hinab auf −1,5 m und eben 30 m; JONSWAP am Rand W (γ 3,3, 200 Komponenten, Phasen
     * fest). Karte und Referenz im Gleichschritt wie {@link #ueberlaufLauf}; dazu die Referenz ohne Bauwerk (landwärts des
     * Fußes ein Strand 1:30 bis ans Land), am Fuß einlaufend nach Guza u. a. (1984) aus η und hu. {Überlauf Karte, Referenz
     * (m³/s je m), H_m0 und T_m−1,0 einlaufend am Fuß ohne Bauwerk}.
     */
    static double[] ueberlaufFlach(double hm0, double tp, double h0, double hf, double tan, double rc, double gc, double dauer, double vorlauf, double dx) {
        int nf = 200, ny = 4;
        double fp = 1 / tp, df = 2 * fp / nf, m0 = 0;
        double[] fr = new double[nf], am = new double[nf], ph = new double[nf];
        java.util.Random zufall = new java.util.Random(7);
        for (int i = 0; i < nf; i++) {
            fr[i] = 0.5 * fp + (i + 0.5) * df;
            double sg = fr[i] < fp ? 0.07 : 0.09;
            am[i] = Math.pow(fr[i], -5) * Math.exp(-1.25 * Math.pow(fp / fr[i], 4)) * Math.pow(3.3, Math.exp(-Math.pow(fr[i] - fp, 2) / (2 * sg * sg * fp * fp)));
            ph[i] = zufall.nextDouble() * 2 * Math.PI;
            m0 += am[i] * df;
        }
        for (int i = 0; i < nf; i++) am[i] = Math.sqrt(2 * am[i] * df * (hm0 * hm0 / 16) / m0);
        double xFuss = 8 + (h0 - hf) * 10, xKrone = xFuss + (hf + rc) / tan, xHinten = xKrone + gc, c0 = Math.sqrt(GR / h0);
        double xStrand = xFuss + 30 * hf + 4;                                  // ohne Bauwerk: der Strand reicht 4 m über die Linie in Ruhe
        int nx = (int) Math.round(Math.max(xHinten + 3 + 30, xStrand) / dx), ik = (int) Math.ceil(xKrone / dx), iFuss = (int) Math.floor(xFuss / dx);
        java.util.function.Function<Boolean, Swash> bau = ohne -> {
            Swash s = new Swash(nx, ny, dx, 0, 0, 0);
            s.grund((x, y) -> x < 8 ? -h0 : x < xFuss ? -h0 + (x - 8) / 10 : ohne ? -hf + (x - xFuss) / 30
                    : x < xKrone ? -hf + tan * (x - xFuss) : x < xHinten ? rc : Math.max(-1.5, rc - 0.5 * (x - xHinten)))
             .rauheit((x, y) -> 0.012);
            s.antrieb(Swash.W, (t, x, y, aus) -> {
                double e = 0;
                for (int i = 0; i < nf; i++) e += am[i] * Math.cos(2 * Math.PI * fr[i] * t + ph[i]);
                e *= Math.min(1, t / 20);
                aus[0] = e; aus[1] = e * c0; aus[2] = 0;
            });
            return s.fuellen(0, new Swash.Anfang() { @Override public double stand(double x, double y) { return ohne || x < xKrone ? 0 : -1e3; } });
        };
        Swash ref = bau.apply(false), kar = bau.apply(false);
        java.util.function.ToDoubleFunction<Swash> hinten = s -> { double v = 0; for (int j = 0; j < ny; j++) for (int i = ik; i < nx; i++) v += s.tiefe(i, j) * dx * dx; return v; };
        double[] v0 = new double[2];
        try (SwashKarte k = new SwashKarte(geraet, konto, kar, 2 * BLOCK, false)) {
            k.verschmolzen = verschmolzen;
            k.hoch();
            for (double ziel : new double[] {vorlauf, dauer}) {
                while (ref.zeit() < ziel - 1e-9) {
                    Ablauf ab = new Ablauf(geraet);
                    int n = 0;
                    while (n < BLOCK && ref.zeit() < ziel - 1e-12) {
                        double ta = ref.zeit(), dt = ref.schritt(ziel - ta);
                        k.antrieb(2 * n, ta);
                        k.antrieb(2 * n + 1, ta + dt);
                        k.schritt(ab, dt, 2 * n, 2 * n + 1);
                        n++;
                    }
                    k.antriebHoch();
                    geraet.rechnen.einmal(c -> ab.aufnehmen(c, null));
                }
                k.runter();
                if (ziel == vorlauf) { v0[0] = hinten.applyAsDouble(kar); v0[1] = hinten.applyAsDouble(ref); }
            }
        }
        double nen = ny * dx * (dauer - vorlauf);
        double qk = (hinten.applyAsDouble(kar) - v0[0]) / nen, qr = (hinten.applyAsDouble(ref) - v0[1]) / nen;
        // ohne Bauwerk: η und hu am Fuß in der Zeile 1 je 0,1 s ab vorlauf (nur die Referenz)
        Swash oh = bau.apply(true);
        int nRec = (int) Math.round((dauer - vorlauf) / 0.1);
        double[] et = new double[nRec], qq = new double[nRec], hh = new double[nRec];
        int m = 0;
        double tN = vorlauf;
        while (oh.zeit() < dauer - 1e-9) {
            oh.schritt(Math.min(0.1, dauer - oh.zeit()));
            if (oh.zeit() >= tN - 1e-9 && m < nRec) {
                int c = oh.id(iFuss, 1);
                et[m] = oh.h[c] + oh.b[c]; qq[m] = oh.hu[c]; hh[m] = oh.h[c]; m++;
                tN += 0.1;
            }
        }
        double me = 0, mq = 0, mh = 0;
        for (int i = 0; i < m; i++) { me += et[i]; mq += qq[i]; mh += hh[i]; }
        me /= m; mq /= m; mh /= m;
        double c = Math.sqrt(GR * Math.max(0.05, mh));
        double[] ein = new double[m];
        for (int i = 0; i < m; i++) ein[i] = 0.5 * ((et[i] - me) + (qq[i] - mq) / c);
        double[] mo = momente(ein, 0.1);
        return new double[] {qk, qr, 4 * Math.sqrt(mo[0]), mo[1] / mo[0]};
    }

    /** Seit Seegang Werkbuch 82: m0 und m−1 einer Reihe (Periodogramm mit Hann, 0,02–1 Hz). */
    static double[] momente(double[] x, double dt) {
        int n = x.length, mm = Integer.highestOneBit(n);
        double[] re = new double[mm], im = new double[mm];
        double w = 0;
        for (int k = 0; k < mm; k++) { double h = 0.5 - 0.5 * Math.cos(2 * Math.PI * k / (mm - 1)); re[k] = x[n - mm + k] * h; w += h * h; }
        for (int i = 1, j = 0; i < mm; i++) {
            int bit = mm >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= mm; len <<= 1) {
            double ang = -2 * Math.PI / len;
            for (int i = 0; i < mm; i += len)
                for (int k = 0; k < len / 2; k++) {
                    double wr = Math.cos(ang * k), wi = Math.sin(ang * k);
                    int u = i + k, v = i + k + len / 2;
                    double xr = re[v] * wr - im[v] * wi, xi = re[v] * wi + im[v] * wr;
                    re[v] = re[u] - xr; im[v] = im[u] - xi; re[u] += xr; im[u] += xi;
                }
        }
        double m0 = 0, m1 = 0, df = 1 / (mm * dt);
        for (int k = 1; k < mm / 2; k++) {
            double f = k * df;
            if (f < 0.02 || f > 1) continue;
            double sp = 2 * (re[k] * re[k] + im[k] * im[k]) * dt / w;
            m0 += sp * df; m1 += sp / f * df;
        }
        return new double[] {m0, m1};
    }

    /**
     * Seit Seegang Werkbuch 76 (SF81 A): Kanal (ny 4) mit 30 m ebenem Grund in 3 m Tiefe, Hang tan bis R_c, Krone gc breit,
     * dahinter 1:2 hinab auf −1,5 m und eben 30 m (anfangs trocken); JONSWAP am Rand W wie {@link #jonswapLauf}. Referenz
     * und Karte im Gleichschritt. Überlauf = Zunahme des Wassers hinter der Krone zwischen vorlauf und dauer je m und s.
     * {Überlauf Karte, Referenz (m³/s je m), T_m−1,0 (s)}.
     */
    static double[] ueberlaufLauf(double hm0, double tp, double tan, double rc, double gc, double dauer, double vorlauf, double dx) {
        int nf = 200, ny = 4;
        double fp = 1 / tp, df = 2 * fp / nf, m0 = 0, m1 = 0, mm0 = 0, h0 = 3;
        double[] fr = new double[nf], am = new double[nf], ph = new double[nf];
        java.util.Random zufall = new java.util.Random(7);
        for (int i = 0; i < nf; i++) {
            fr[i] = 0.5 * fp + (i + 0.5) * df;
            double sg = fr[i] < fp ? 0.07 : 0.09;
            am[i] = Math.pow(fr[i], -5) * Math.exp(-1.25 * Math.pow(fp / fr[i], 4)) * Math.pow(3.3, Math.exp(-Math.pow(fr[i] - fp, 2) / (2 * sg * sg * fp * fp)));
            ph[i] = zufall.nextDouble() * 2 * Math.PI;
            m0 += am[i] * df;
        }
        for (int i = 0; i < nf; i++) { am[i] = Math.sqrt(2 * am[i] * df * (hm0 * hm0 / 16) / m0); mm0 += 0.5 * am[i] * am[i]; m1 += 0.5 * am[i] * am[i] / fr[i]; }
        double xKrone = 30 + (h0 + rc) / tan, xHinten = xKrone + gc, c0 = Math.sqrt(GR / h0);
        int nx = (int) Math.round((xHinten + 3 + 30) / dx), ik = (int) Math.ceil(xKrone / dx);
        java.util.function.Supplier<Swash> bau = () -> {
            Swash s = new Swash(nx, ny, dx, 0, 0, 0);
            s.grund((x, y) -> x < 30 ? -h0 : x < xKrone ? -h0 + tan * (x - 30) : x < xHinten ? rc : Math.max(-1.5, rc - 0.5 * (x - xHinten)))
             .rauheit((x, y) -> 0.012);
            s.antrieb(Swash.W, (t, x, y, aus) -> {
                double e = 0;
                for (int i = 0; i < nf; i++) e += am[i] * Math.cos(2 * Math.PI * fr[i] * t + ph[i]);
                e *= Math.min(1, t / 20);
                aus[0] = e; aus[1] = e * c0; aus[2] = 0;
            });
            return s.fuellen(0, new Swash.Anfang() { @Override public double stand(double x, double y) { return x < xKrone ? 0 : -1e3; } });
        };
        Swash ref = bau.get(), kar = bau.get();
        java.util.function.ToDoubleFunction<Swash> hinten = s -> { double v = 0; for (int j = 0; j < ny; j++) for (int i = ik; i < nx; i++) v += s.tiefe(i, j) * dx * dx; return v; };
        double[] v0 = new double[2];
        try (SwashKarte k = new SwashKarte(geraet, konto, kar, 2 * BLOCK, false)) {
            k.verschmolzen = verschmolzen;
            k.hoch();
            for (double ziel : new double[] {vorlauf, dauer}) {
                while (ref.zeit() < ziel - 1e-9) {
                    Ablauf ab = new Ablauf(geraet);
                    int n = 0;
                    while (n < BLOCK && ref.zeit() < ziel - 1e-12) {
                        double ta = ref.zeit(), dt = ref.schritt(ziel - ta);
                        k.antrieb(2 * n, ta);
                        k.antrieb(2 * n + 1, ta + dt);
                        k.schritt(ab, dt, 2 * n, 2 * n + 1);
                        n++;
                    }
                    k.antriebHoch();
                    geraet.rechnen.einmal(c -> ab.aufnehmen(c, null));
                }
                k.runter();
                if (ziel == vorlauf) { v0[0] = hinten.applyAsDouble(kar); v0[1] = hinten.applyAsDouble(ref); }
            }
        }
        double nen = ny * dx * (dauer - vorlauf);
        return new double[] {(hinten.applyAsDouble(kar) - v0[0]) / nen, (hinten.applyAsDouble(ref) - v0[1]) / nen, m1 / mm0};
    }

    /**
     * Seit Swash 0.3: JONSWAP (γ 3,3; 200 Komponenten von 0,5 bis 2,5 f_p, Phasen fest) am Rand W in der Tiefe h0 (30 m
     * eben), dann der Hang tan bis 2,5 m über dem Ruhewasser; Wände an S, N und E. Referenz und Karte im Gleichschritt
     * (wie {@link #paar}), dazu die Referenz ohne Teilgitter. Saum je 0,1 s in der mittleren Zeile ab vorlauf (Karte:
     * swash_saum, Referenz: dieselbe Regel in Java). {R2 % Karte, Referenz, ohne Teilgitter, T_m−1,0, Wellen Karte, Wellen Referenz}.
     */
    static double[] jonswapLauf(double hm0, double tp, double tan, double h0, double dauer, double vorlauf, double dx, int ny) {
        return jonswapLauf(hm0, tp, tan, h0, dauer, vorlauf, dx, ny, KONTUR);
    }

    /** Seit Seegang Werkbuch 76 (SF82 A): wie oben, mit der Kontur des Saums (m Wasser). */
    static double[] jonswapLauf(double hm0, double tp, double tan, double h0, double dauer, double vorlauf, double dx, int ny, double kontur) {
        int nf = 200;
        double fp = 1 / tp, df = 2 * fp / nf, m0 = 0, m1 = 0;
        double[] fr = new double[nf], am = new double[nf], ph = new double[nf];
        java.util.Random zufall = new java.util.Random(7);
        for (int i = 0; i < nf; i++) {
            fr[i] = 0.5 * fp + (i + 0.5) * df;
            double sg = fr[i] < fp ? 0.07 : 0.09;
            am[i] = Math.pow(fr[i], -5) * Math.exp(-1.25 * Math.pow(fp / fr[i], 4)) * Math.pow(3.3, Math.exp(-Math.pow(fr[i] - fp, 2) / (2 * sg * sg * fp * fp)));
            ph[i] = zufall.nextDouble() * 2 * Math.PI;
            m0 += am[i] * df;
        }
        for (int i = 0; i < nf; i++) { am[i] = Math.sqrt(2 * am[i] * df * (hm0 * hm0 / 16) / m0); m1 += 0.5 * am[i] * am[i] / fr[i]; }
        double mm0 = 0;
        for (int i = 0; i < nf; i++) mm0 += 0.5 * am[i] * am[i];
        double tm = m1 / mm0;
        int nx = (int) Math.round((30 + (h0 + 2.5) / tan + 10) / dx);
        double c0 = Math.sqrt(GR / h0);
        java.util.function.Function<Boolean, Swash> bau = teil -> {
            Swash s = new Swash(nx, ny, dx, 0, 0, 0);
            s.teilgitter = teil;
            s.grund((x, y) -> x < 30 ? -h0 : Math.min(2.5, -h0 + tan * (x - 30))).rauheit((x, y) -> 0.012);
            s.antrieb(Swash.W, (t, x, y, aus) -> {
                double e = 0;
                for (int i = 0; i < nf; i++) e += am[i] * Math.cos(2 * Math.PI * fr[i] * t + ph[i]);
                e *= Math.min(1, t / 20);
                aus[0] = e; aus[1] = e * c0; aus[2] = 0;
            });
            return s.fuellen(0, null);
        };
        Swash ref = bau.apply(true), kar = bau.apply(true), alt = bau.apply(false);
        int jm = ny / 2;
        java.util.List<Double> sk = new java.util.ArrayList<>(), sr = new java.util.ArrayList<>(), sa = new java.util.ArrayList<>();
        double ts = vorlauf;
        try (SwashKarte k = new SwashKarte(geraet, konto, kar, 2 * BLOCK, false)) {
            k.verschmolzen = verschmolzen;
            k.hoch();
            while (ref.zeit() < dauer - 1e-9) {
                Ablauf ab = new Ablauf(geraet);
                int n = 0;
                double ziel = Math.min(dauer, ts);
                while (n < BLOCK && ref.zeit() < ziel - 1e-12) {
                    double ta = ref.zeit(), dt = ref.schritt(ziel - ta);
                    k.antrieb(2 * n, ta);
                    k.antrieb(2 * n + 1, ta + dt);
                    k.schritt(ab, dt, 2 * n, 2 * n + 1);
                    n++;
                }
                boolean probe = ref.zeit() >= ts - 1e-9;
                if (probe) k.saum(ab, kontur);
                k.antriebHoch();
                geraet.rechnen.einmal(c -> ab.aufnehmen(c, null));
                while (alt.zeit() < ref.zeit() - 1e-12) alt.schritt(ref.zeit() - alt.zeit());
                if (probe) {
                    sk.add(k.saumLesen()[jm]);
                    sr.add(saumReferenz(ref, jm, kontur));
                    sa.add(saumReferenz(alt, jm, kontur));
                    ts += 0.1;
                }
            }
        }
        double[] wk = r2(sk), wr = r2(sr), wa = r2(sa);
        return new double[] {wk[0], wr[0], wa[0], tm, wk[1], wr[1]};
    }

    /**
     * Seit Seegang Werkbuch 75 (SF77 A): wie {@link #jonswapLauf}, aber kurzkämmig: je Komponente eine Richtung θ aus
     * cos^2s(θ/2) (feste Zufallszahlen), am Rand η = Σ a cos(ω t − k sin θ y + φ), u_n und u_t aus √(g/h0) η cos θ bzw.
     * sin θ, k = ω / √(g h0) (Flachwasser wie der Löser). Breite b (m), Wände an S, N und E. Saum je 0,1 s in Zeilen alle
     * 4 m, 8 m von den Wänden; Kontur des Saums kontur (m), dazu die Referenz mit 1 cm. {R2 % Karte, Referenz, Wellen Karte,
     * Referenz, Stau Karte, S_inc Karte, S_IG Karte, σ_θ (°), Zeilen, R2 % Referenz mit 1 cm, Stau Referenz mit 1 cm}.
     */
    static double[] jonswapLauf2D(double hm0, double tp, double tan, double h0, double dauer, double vorlauf, double dx, double breite, double sSpread, double kontur) {
        int nf = 200, ny = (int) Math.round(breite / dx);
        double fp = 1 / tp, df = 2 * fp / nf, m0 = 0;
        double[] fr = new double[nf], am = new double[nf], ph = new double[nf], om = new double[nf], ky = new double[nf], cs = new double[nf], sn = new double[nf];
        java.util.Random zufall = new java.util.Random(7);
        for (int i = 0; i < nf; i++) {
            fr[i] = 0.5 * fp + (i + 0.5) * df;
            double sg = fr[i] < fp ? 0.07 : 0.09;
            am[i] = Math.pow(fr[i], -5) * Math.exp(-1.25 * Math.pow(fp / fr[i], 4)) * Math.pow(3.3, Math.exp(-Math.pow(fr[i] - fp, 2) / (2 * sg * sg * fp * fp)));
            ph[i] = zufall.nextDouble() * 2 * Math.PI;
            m0 += am[i] * df;
        }
        int nt = 2001;
        double[] cdf = new double[nt];
        double sum = 0, s2 = 0;
        for (int t = 0; t < nt; t++) { double th = -Math.PI / 2 + Math.PI * t / (nt - 1); sum += Math.pow(Math.cos(th / 2), 2 * sSpread); cdf[t] = sum; }
        java.util.Random zr = new java.util.Random(11);
        double cw = Math.sqrt(GR * h0), c0 = Math.sqrt(GR / h0);
        for (int i = 0; i < nf; i++) {
            am[i] = Math.sqrt(2 * am[i] * df * (hm0 * hm0 / 16) / m0);
            double u = zr.nextDouble() * sum;
            int t = 0;
            while (t < nt - 1 && cdf[t] < u) t++;
            double th = -Math.PI / 2 + Math.PI * t / (nt - 1);
            s2 += th * th;
            om[i] = 2 * Math.PI * fr[i]; ky[i] = om[i] / cw * Math.sin(th); cs[i] = Math.cos(th); sn[i] = Math.sin(th);
        }
        int nx = (int) Math.round((30 + (h0 + 2.5) / tan + 10) / dx);
        java.util.function.Supplier<Swash> bau = () -> {
            Swash s = new Swash(nx, ny, dx, 0, 0, 0);
            s.grund((x, y) -> x < 30 ? -h0 : Math.min(2.5, -h0 + tan * (x - 30))).rauheit((x, y) -> 0.012);
            // cos(ω t + φ − k_y y) = cos(ω t + φ) cos(k_y y) + sin(ω t + φ) sin(k_y y): die Zeit einmal je t, der Ort einmal je Zeile
            final double[] tz = {Double.NaN}, ct = new double[nf], st = new double[nf];
            final java.util.Map<Double, double[]> zeile = new java.util.HashMap<>();
            s.antrieb(Swash.W, (t, x, y, aus) -> {
                synchronized (tz) {
                    if (t != tz[0]) { for (int i = 0; i < nf; i++) { ct[i] = Math.cos(om[i] * t + ph[i]); st[i] = Math.sin(om[i] * t + ph[i]); } tz[0] = t; }
                    double[] cy = zeile.computeIfAbsent(y, yy -> { double[] w = new double[2 * nf]; for (int i = 0; i < nf; i++) { w[2 * i] = Math.cos(ky[i] * yy); w[2 * i + 1] = Math.sin(ky[i] * yy); } return w; });
                    double e = 0, un = 0, ut = 0;
                    for (int i = 0; i < nf; i++) { double v = am[i] * (ct[i] * cy[2 * i] + st[i] * cy[2 * i + 1]); e += v; un += v * cs[i]; ut += v * sn[i]; }
                    double r = Math.min(1, t / 20);
                    aus[0] = r * e; aus[1] = r * un * c0; aus[2] = r * ut * c0;
                }
            });
            return s.fuellen(0, null);
        };
        Swash ref = bau.get(), kar = bau.get();
        java.util.List<Integer> zeilen = new java.util.ArrayList<>();
        int rand = (int) Math.round(8 / dx), schritt = (int) Math.round(4 / dx);
        for (int j = rand; j < ny - rand; j += schritt) zeilen.add(j);
        java.util.List<java.util.List<Double>> sk = new java.util.ArrayList<>(), sr = new java.util.ArrayList<>(), s1 = new java.util.ArrayList<>();
        for (int j : zeilen) { sk.add(new java.util.ArrayList<>()); sr.add(new java.util.ArrayList<>()); s1.add(new java.util.ArrayList<>()); }
        double ts = vorlauf;
        try (SwashKarte k = new SwashKarte(geraet, konto, kar, 2 * BLOCK, false)) {
            k.verschmolzen = verschmolzen;
            k.hoch();
            while (ref.zeit() < dauer - 1e-9) {
                Ablauf ab = new Ablauf(geraet);
                int n = 0;
                double ziel = Math.min(dauer, ts);
                while (n < BLOCK && ref.zeit() < ziel - 1e-12) {
                    double ta = ref.zeit(), dt = ref.schritt(ziel - ta);
                    k.antrieb(2 * n, ta);
                    k.antrieb(2 * n + 1, ta + dt);
                    k.schritt(ab, dt, 2 * n, 2 * n + 1);
                    n++;
                }
                boolean probe = ref.zeit() >= ts - 1e-9;
                if (probe) k.saum(ab, kontur);
                k.antriebHoch();
                geraet.rechnen.einmal(c -> ab.aufnehmen(c, null));
                if (probe) {
                    double[] z = k.saumLesen();
                    for (int q = 0; q < zeilen.size(); q++) {
                        sk.get(q).add(z[zeilen.get(q)]);
                        sr.get(q).add(saumReferenz(ref, zeilen.get(q), kontur));
                        s1.get(q).add(saumReferenz(ref, zeilen.get(q), KONTUR));     // seit Werkbuch 76: zum Vergleich 1 cm
                    }
                    ts += 0.1;
                }
            }
        }
        java.util.List<Double> mk = new java.util.ArrayList<>(), mr = new java.util.ArrayList<>(), m1 = new java.util.ArrayList<>();
        double stau = 0, sinc = 0, sig = 0, stau1 = 0;
        for (int q = 0; q < zeilen.size(); q++) {
            maxima(sk.get(q), mk);
            maxima(sr.get(q), mr);
            maxima(s1.get(q), m1);
            double[] w = schwall(sk.get(q), 0.1);
            stau += w[0]; sinc += w[1]; sig += w[2];
            stau1 += schwall(s1.get(q), 0.1)[0];
        }
        int nz = zeilen.size();
        double[] qk = quantil98(mk), qr = quantil98(mr), q1 = quantil98(m1);
        return new double[] {qk[0], qr[0], qk[1], qr[1], stau / nz, sinc / nz, sig / nz, Math.toDegrees(Math.sqrt(s2 / nf)), nz, q1[0], stau1 / nz};
    }

    /** Seit Seegang Werkbuch 75: die Maxima einer Reihe des Saums wie in {@link #r2}, an mx angehängt. */
    static void maxima(java.util.List<Double> reihe, java.util.List<Double> mx) {
        int n = 0;
        double st = 0, sx = 0, stt = 0, stx = 0;
        for (int i = 0; i < reihe.size(); i++) { double x = reihe.get(i); if (Double.isFinite(x)) { st += i; sx += x; stt += (double) i * i; stx += i * x; n++; } }
        if (n < 10) return;
        double nen = n * stt - st * st, b = nen > 0 ? (n * stx - st * sx) / nen : 0, a = (sx - b * st) / n, s2 = 0;
        for (int i = 0; i < reihe.size(); i++) { double x = reihe.get(i); if (Double.isFinite(x)) { double d = x - a - b * i; s2 += d * d; } }
        double eps = 0.1 * Math.sqrt(s2 / n), m = Double.NEGATIVE_INFINITY;
        boolean oben = false, begonnen = false;
        for (int i = 0; i < reihe.size(); i++) {
            double x = reihe.get(i);
            if (!Double.isFinite(x)) continue;
            double d = x - a - b * i;
            if (!oben && d > eps) { if (begonnen) mx.add(m); begonnen = true; oben = true; m = x; }
            else if (oben && d < -eps) oben = false;
            if (begonnen) m = Math.max(m, x);
        }
    }

    /** {98-%-Quantil, Zahl}; NaN ohne Werte. */
    static double[] quantil98(java.util.List<Double> mx) {
        if (mx.isEmpty()) return new double[] {Double.NaN, 0};
        double[] v = mx.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return new double[] {v[Math.min(v.length - 1, (int) Math.floor(0.98 * v.length))], v.length};
    }

    /**
     * Seit Seegang Werkbuch 75 (SF77 A): {Mittel, S_inc, S_IG} einer Reihe des Saums (Schritt dt, s): S = 4 √Varianz über
     * bzw. unter 0,05 Hz (wie Stockdon u. a. 2006), über die letzten 2^n Werte.
     */
    static double[] schwall(java.util.List<Double> r, double dt) {
        int n = Integer.highestOneBit(r.size()), k0 = r.size() - n;
        double[] re = new double[n], im = new double[n];
        double me = 0;
        int nn = 0;
        for (int i = 0; i < n; i++) { double v = r.get(k0 + i); if (Double.isFinite(v)) { me += v; nn++; } }
        me = nn > 0 ? me / nn : 0;
        for (int i = 0; i < n; i++) { double v = r.get(k0 + i); re[i] = Double.isFinite(v) ? v - me : 0; }
        fft(re, im);
        double vi = 0, vg = 0;
        for (int k = 1; k <= n / 2; k++) { double f = k / (n * dt), p = (re[k] * re[k] + im[k] * im[k]) * 2.0 / ((double) n * n); if (f < 0.05) vg += p; else vi += p; }
        return new double[] {me, 4 * Math.sqrt(vi), 4 * Math.sqrt(vg)};
    }

    /** Seit Seegang Werkbuch 75: FFT an Ort und Stelle (Radix 2, Länge eine Zweierpotenz), wie in lauf.demo.AuflaufMassstabCheck. */
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

    /** Seit Swash 0.3: der Saum der Zeile j in der Referenz nach der Regel von swash_saum (Kontur 1 cm; im Teilgitter an der Front w − 1 cm). */
    static double saumReferenz(Swash s, int j) { return saumReferenz(s, j, KONTUR); }

    /** Seit Seegang Werkbuch 76 (SF82 A): wie oben, mit der Kontur (m Wasser) als Wasserlinie. */
    static double saumReferenz(Swash s, int j, double kontur) {
        double m = Double.NaN;
        for (int i = 0; i < s.nx; i++) {
            double h = s.tiefe(i, j);
            if (!(h > s.hTrocken) || Double.isNaN(s.stand(i, j))) continue;
            double b = s.stand(i, j) - h, kand = h >= kontur ? b : Double.NaN;
            if (s.teilgitter) {
                double bl = s.stand(i - 1, j) - s.tiefe(i - 1, j), br = s.stand(i + 1, j) - s.tiefe(i + 1, j);
                double bd = s.stand(i, j - 1) - s.tiefe(i, j - 1), bo = s.stand(i, j + 1) - s.tiefe(i, j + 1);
                double gx = Double.isNaN(bl) || Double.isNaN(br) ? 0 : minmod(b - bl, br - b), gy = Double.isNaN(bd) || Double.isNaN(bo) ? 0 : minmod(b - bd, bo - b);
                double sg = Math.abs(gx) + Math.abs(gy);
                boolean keil = sg > 0 && h < 0.5 * sg;
                boolean front = keil || trocken(s, i - 1, j) || trocken(s, i + 1, j) || trocken(s, i, j - 1) || trocken(s, i, j + 1);
                if (front) {
                    double w = keil ? b - 0.5 * sg + Math.sqrt(2 * h * sg) : h + b;
                    kand = w - kontur >= b - 0.5 * Math.abs(gx) ? Math.min(w - kontur, b + 0.5 * Math.abs(gx)) : Double.NaN;
                }
            }
            if (!Double.isNaN(kand) && !(m >= kand)) m = kand;
        }
        return m - s.bezug;
    }

    /** Nachbar trocken (Fels zählt nicht). */
    static boolean trocken(Swash s, int i, int j) { return !Double.isNaN(s.stand(i, j)) && !(s.tiefe(i, j) > s.hTrocken); }

    static double minmod(double a, double b) { return a * b <= 0 ? 0 : (Math.abs(a) < Math.abs(b) ? a : b); }

    /**
     * Seit Swash 0.3: {R2 %, Zahl der Wellen} aus einer Reihe des Saums (wie lauf.demo.AuflaufMassstabCheck.maxima: Maxima
     * zwischen Aufwärtsdurchgängen durch die Ausgleichsgerade, Hysterese ± 0,1 σ; R2 % das 98-%-Quantil, über dem Ruhewasser).
     */
    static double[] r2(java.util.List<Double> reihe) {
        int n = 0;
        double st = 0, sx = 0, stt = 0, stx = 0;
        for (int i = 0; i < reihe.size(); i++) { double x = reihe.get(i); if (Double.isFinite(x)) { st += i; sx += x; stt += (double) i * i; stx += i * x; n++; } }
        if (n < 10) return new double[] {Double.NaN, 0};
        double nen = n * stt - st * st, b = nen > 0 ? (n * stx - st * sx) / nen : 0, a = (sx - b * st) / n, s2 = 0;
        for (int i = 0; i < reihe.size(); i++) { double x = reihe.get(i); if (Double.isFinite(x)) { double d = x - a - b * i; s2 += d * d; } }
        double eps = 0.1 * Math.sqrt(s2 / n), m = Double.NEGATIVE_INFINITY;
        boolean oben = false, begonnen = false;
        java.util.List<Double> mx = new java.util.ArrayList<>();
        for (int i = 0; i < reihe.size(); i++) {
            double x = reihe.get(i);
            if (!Double.isFinite(x)) continue;
            double d = x - a - b * i;
            if (!oben && d > eps) { if (begonnen) mx.add(m); begonnen = true; oben = true; m = x; }
            else if (oben && d < -eps) oben = false;
            if (begonnen) m = Math.max(m, x);
        }
        if (mx.isEmpty()) return new double[] {Double.NaN, 0};
        double[] v = mx.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return new double[] {v[Math.min(v.length - 1, (int) Math.floor(0.98 * v.length))], v.length};
    }

    /**
     * Rechnet denselben Fall in der Referenz und auf der Karte bis tEnde (der Zeitschritt und der Antrieb jeder Stufe aus
     * der Referenz); bei tLoeschen (NaN: nie) löschen beide ihre Spuren. Gibt {Referenz, Karte} zurück.
     */
    static Swash[] paar(Supplier<Swash> bau, double tEnde, double tLoeschen) {
        Swash ref = bau.get(), kar = bau.get();
        boolean geloescht = Double.isNaN(tLoeschen);
        try (SwashKarte k = new SwashKarte(geraet, konto, kar, 2 * BLOCK, false)) {
            k.verschmolzen = verschmolzen;
            k.hoch();
            while (ref.zeit() < tEnde - 1e-12) {
                Ablauf ab = new Ablauf(geraet);
                int n = 0;
                boolean jetzt = false;
                while (n < BLOCK && ref.zeit() < tEnde - 1e-12) {
                    double ziel = geloescht ? tEnde : Math.min(tEnde, tLoeschen), ta = ref.zeit();
                    double dt = ref.schritt(ziel - ta);
                    k.antrieb(2 * n, ta);
                    k.antrieb(2 * n + 1, ta + dt);
                    k.schritt(ab, dt, 2 * n, 2 * n + 1);
                    n++;
                    if (!geloescht && ref.zeit() >= tLoeschen - 1e-12) { jetzt = true; break; }
                }
                k.antriebHoch();
                geraet.rechnen.einmal(c -> ab.aufnehmen(c, null));
                if (jetzt) {
                    ref.spurenLoeschen();
                    k.runter();
                    kar.spurenLoeschen();
                    k.hoch();
                    geloescht = true;
                }
            }
            k.runter();
        }
        return new Swash[] {ref, kar};
    }

    static void ruhe() {
        A.teil("1 Ruhender See über welligem Grund mit trockenem Ufer");
        Swash[] p = paar(() -> SwashCheck.becken().fuellen(0.013, null), 30, Double.NaN);
        A.info("Fall", f("%s; %d Schritte", p[0].beschreibung(), p[1].schritte()));
        A.hoechstens("größte Geschwindigkeit auf der Karte nach 30 s (nasse Zellen)", p[1].hoechsteGeschwindigkeit(0), 1e-4, "m/s");
        A.info("Referenz", f("%.2e m/s", p[0].hoechsteGeschwindigkeit(0)));
    }

    static final Swash.Anfang BUCKEL = (x, y) -> 0.013 + 0.35 * Math.exp(-((x - 5) * (x - 5) + (y - 6) * (y - 6)) / 4);

    static void volumen() {
        A.teil("2 Volumen im geschlossenen Becken mit Wellen über trockene Hänge");
        double v0 = SwashCheck.becken().fuellen(0.013, BUCKEL).volumen();
        Swash[] p = paar(() -> SwashCheck.becken().fuellen(0.013, BUCKEL), 60, Double.NaN);
        A.hoechstens("Volumen auf der Karte nach 60 s, relativ", (p[1].volumen() - v0) / v0, 1e-6, "");
        A.hoechstens("abgeschnittene negative Tiefen auf der Karte, relativ", p[1].abgeschnitten() / v0, 1e-7, "");
        A.info("Karte gegen Referenz", f("Volumen %.3e relativ; höchster Auflauf %.4f m gegen %.4f m", (p[1].volumen() - p[0].volumen()) / v0, p[1].auflauf(), p[0].auflauf()));
        Supplier<Swash> mit = () -> {
            Swash t = SwashCheck.becken();
            t.durchlaessigkeit((x, y) -> Boden.durchlaessigkeit(0.2e-3)).grundwasser((x, y) -> 0.0);
            return t.fuellen(0.013, BUCKEL);
        };
        Swash[] q = paar(mit, 60, Double.NaN);
        A.hoechstens("mit Versickern: Wasser im Gitter plus versickertes auf der Karte gegen den Anfang, relativ", (q[1].volumen() + q[1].versickert() - v0) / v0, 1e-6, "");
        A.info("versickert", f("Karte %.4f m³, Referenz %.4f m³", q[1].versickert(), q[0].versickert()));
    }

    static void stoker() {
        A.teil("3 Dammbruch auf nassem Grund (Stoker)");
        double hl = 1.0, hr = 0.1, xd = 50, t = 5, dx = 0.05;
        Swash[] p = paar(() -> {
            Swash s = new Swash(2000, 1, dx, 0, 0, 0);
            s.grund((x, y) -> 0.0);
            return s.fuellen(0, (x, y) -> x < xd ? hl : hr);
        }, t, Double.NaN);
        double d = 0, cl = Math.sqrt(GR * hl), sS = 3.105;
        for (int i = 0; i < p[0].nx; i++) d += Math.abs(p[1].tiefe(i, 0) - p[0].tiefe(i, 0)) * dx;
        A.hoechstens("Σ|h_Karte − h_Referenz| Δx / ((h_L − h_R)(S + c_L) t)", d / ((hl - hr) * (sS + cl) * t), 1e-4, "");
    }

    static void synolakis() {
        A.teil("4 Einzelwelle auf der Böschung (Synolakis)");
        double d = 1, cot = 19.85, hd = 0.0185, H = hd * d, xs = 4, dx = 0.05;
        double gam = Math.sqrt(3 * hd / 4), x1 = xs + d * cot + SwashCheck.acosh(Math.sqrt(20)) / gam * d;
        int nx = (int) Math.ceil((x1 + 30) / dx);
        Swash[] p = paar(() -> {
            Swash s = new Swash(nx, 1, dx, 0, 0, 0);
            s.hSpitze = 1e-4;
            s.grund((x, y) -> Math.max(-d, (xs - x) / cot));
            s.rand(Swash.E, Swash.Rand.OFFEN);
            return s.fuellen(0, new Swash.Anfang() {
                public double stand(double x, double y) { double c = 1 / Math.cosh(gam * (x - x1) / d); return H * c * c; }

                public double[] u(double x, double y) {
                    double c = 1 / Math.cosh(gam * (x - x1) / d), e = H * c * c;
                    return new double[] {-2 * (Math.sqrt(GR * (d + e)) - Math.sqrt(GR * d)), 0};
                }
            });
        }, 30, Double.NaN);
        A.info("Auflauf", f("R/d Karte %.5f, Referenz %.5f", p[1].auflauf() / d, p[0].auflauf() / d));
        A.hoechstens("Auflauf der Karte gegen die Referenz, relativ", p[1].auflauf() / p[0].auflauf() - 1, 0.01, "");
    }

    static void carrierGreenspan() {
        A.teil("5 Periodische Welle auf ebener Böschung, Rand mit Antrieb (Carrier und Greenspan)");
        double d = 0.5, tanb = 0.05, per = 20, a = 0.02, xs = 3, flach = 2, dx = 0.025, om = 2 * Math.PI / per, rampe = 2 * per;
        int nx = (int) Math.round((xs + d / tanb + flach) / dx);
        double tMess = rampe + 3 * per, tEnde = tMess + 4 * per;
        Swash[] p = paar(() -> {
            Swash s = new Swash(nx, 1, dx, 0, 0, 0);
            s.hSpitze = 1e-4;
            s.grund((x, y) -> Math.max(-d, (xs - x) * tanb));
            s.antrieb(Swash.E, (t, x, y, aus) -> {
                double w = t < rampe ? 0.5 * (1 - Math.cos(Math.PI * t / rampe)) : 1, e = a * w * Math.sin(om * t);
                aus[0] = e;
                aus[1] = 2 * (Math.sqrt(GR * (d + e)) - Math.sqrt(GR * d));
                aus[2] = 0;
            });
            return s.fuellen(0, null);
        }, tEnde, tMess);
        A.info("Auflauf", f("Karte %.4f m, Referenz %.4f m (%d Schritte); Zufluss über den Rand Karte %.4f, Referenz %.4f m²", p[1].auflauf(), p[0].auflauf(), p[1].schritte(), p[1].zufluss(), p[0].zufluss()));
        A.hoechstens("Auflauf der Karte gegen die Referenz, relativ", p[1].auflauf() / p[0].auflauf() - 1, 0.02, "");
    }

    static void zunge() {
        A.teil("6 Zunge auf der Böschung (Shen und Meyer)");
        double tanb = 0.05, h0 = 0.5, xd = 55, dx = 0.05, c0 = Math.sqrt(GR * h0);
        Swash[] p = paar(() -> {
            Swash s = new Swash(1600, 1, dx, 0, 0, 0);
            s.hSpitze = 1e-4;
            s.grund((x, y) -> (x - xd) * tanb);
            s.rand(Swash.W, Swash.Rand.OFFEN);
            return s.fuellen(0, (x, y) -> x < xd ? (x - xd) * tanb + h0 : -1e9);
        }, 2 * c0 / (GR * tanb) + 3, Double.NaN);
        A.info("Auflauf", f("Karte %.4f m, Referenz %.4f m", p[1].auflauf(), p[0].auflauf()));
        A.hoechstens("Auflauf der Karte gegen die Referenz, relativ", p[1].auflauf() / p[0].auflauf() - 1, 0.02, "");
    }

    static void darcy() {
        A.teil("7 Versickern in einem Tümpel (Green und Ampt)");
        double h0 = 0.05, k = 1e-4, schlimmst = 0;
        StringBuilder b = new StringBuilder();
        for (double t : new double[] {25, 75, 150}) {
            Swash[] p = paar(() -> {
                Swash s = new Swash(20, 4, 0.25, 0, 0, 0);
                s.grund((x, y) -> 0.0).durchlaessigkeit((x, y) -> k).grundwasser((x, y) -> -10.0);
                return s.fuellen(h0, null);
            }, t, Double.NaN);
            double r = p[1].versickert() / p[0].versickert() - 1;
            schlimmst = Math.max(schlimmst, Math.abs(r));
            b.append(f("%s%.0f s: %.5f gegen %.5f m³", b.length() == 0 ? "" : "; ", t, p[1].versickert(), p[0].versickert()));
        }
        A.info("versickert, Karte gegen Referenz", b.toString());
        A.hoechstens("größte Abweichung des versickerten Wassers, relativ", schlimmst, 0.01, "");
    }

    static void manning() {
        A.teil("8 Abbremsung nach Manning");
        double hh = 0.1, u0 = 1, nn = 0.025;
        Swash[] p = paar(() -> {
            Swash s = new Swash(20, 1, 0.25, 0, 0, 0);
            s.grund((x, y) -> 0.0).rauheit((x, y) -> nn);
            for (int sd = 0; sd < 4; sd++) s.rand(sd, Swash.Rand.OFFEN);
            return s.fuellen(hh, new Swash.Anfang() {
                public double stand(double x, double y) { return hh; }

                public double[] u(double x, double y) { return new double[] {u0, 0}; }
            });
        }, 10, Double.NaN);
        double uk = p[1].geschwindigkeit(10, 0)[0], ur = p[0].geschwindigkeit(10, 0)[0];
        A.hoechstens(f("Geschwindigkeit der Karte nach 10 s gegen die Referenz (%.6f gegen %.6f m/s), relativ", uk, ur), uk / ur - 1, 1e-4, "");
    }

    static void kosten() {
        A.teil("9 Kosten auf der Karte");
        String name = geraet.name.toLowerCase(java.util.Locale.ROOT);
        boolean software = name.contains("llvmpipe") || name.contains("lavapipe") || name.contains("swiftshader");
        Supplier<Swash> alt = () -> {                                       // wie in S6b-1: 256 m in x mit dem Hang, 64 m in y
            Swash s = new Swash(1024, 256, 0.25, 0, 0, 0);
            s.grund((x, y) -> -2 + x / 30 + 0.1 * Math.sin(y / 7)).rauheit((x, y) -> Boden.manningSand(0.2e-3))
                    .durchlaessigkeit((x, y) -> Boden.durchlaessigkeit(0.2e-3)).grundwasser((x, y) -> 0.0);
            s.antrieb(Swash.W, welle(2));
            return s.fuellen(0, null);
        };
        Supplier<Swash> lauf = () -> {                                      // wie im Lauf: 64 m quer (x), 256 m längs (y)
            Swash s = new Swash(256, 1024, 0.25, 0, 0, 0);
            s.grund((x, y) -> (x - 40) / 30 + 0.1 * Math.sin(y / 7)).rauheit((x, y) -> Boden.manningSand(0.2e-3))
                    .durchlaessigkeit((x, y) -> Boden.durchlaessigkeit(0.2e-3)).grundwasser((x, y) -> 0.0);
            s.antrieb(Swash.W, welle(40.0 / 30));
            return s.fuellen(0, null);
        };
        double[] a = messe(alt, true, true), b = messe(lauf, true, false), ae = messe(alt, false, false), be = messe(lauf, false, false);
        A.info("Rechteck 1024 × 256 wie in S6b-1 (Hang über 256 m in x)", f("verschmolzen %.3f ms je Schritt (%s), einzeln %.3f ms; nasse Kacheln am Ende %.0f %%", a[0], teile(a), ae[0], 100 * a[1]));
        A.info("Rechteck wie im Lauf (64 m quer, 256 m längs, 1:30)", f("verschmolzen %.3f ms je Schritt, einzeln %.3f ms; nasse Kacheln am Ende %.0f %%", b[0], be[0], 100 * b[1]));
        // seit S6e (Seegang Werkbuch 85): Kachel 16 × 16 gegen 16 × 8 im Wechsel (8, 16, 8, 16), Rechteck wie im Lauf
        int alt8 = SwashKarte.kachelY;
        double[] k8 = new double[2], k16 = new double[2];
        try {
            for (int r = 0; r < 2; r++) {
                SwashKarte.kachelY = 8;
                k8[r] = messe(lauf, true, false)[0];
                SwashKarte.kachelY = 16;
                k16[r] = messe(lauf, true, false)[0];
            }
        } finally {
            SwashKarte.kachelY = alt8;
        }
        double m8 = 0.5 * (k8[0] + k8[1]), m16 = 0.5 * (k16[0] + k16[1]);
        A.info("Kachel 16 × 16 gegen 16 × 8 (seit S6e), Rechteck wie im Lauf, im Wechsel", f("16 × 8: %.3f / %.3f ms, 16 × 16: %.3f / %.3f ms je Schritt; im Mittel %.3f gegen %.3f ms (%+.1f %%); die Karte rechnet mit 16 × %d",
                k8[0], k8[1], k16[0], k16[1], m8, m16, 100 * (m16 / m8 - 1), SwashKarte.kachelY));
        if (software) A.info("Kosten je Schritt (Software-Gerät, nur Auskunft)", f("%.3f ms und %.3f ms", a[0], b[0]));
        else {
            A.hoechstens("Kosten eines Schritts, Rechteck 1024 × 256 wie in S6b-1", a[0], 0.9, "ms");
            A.hoechstens("Kosten eines Schritts, Rechteck wie im Lauf (seit S6b-1b)", b[0], 0.9, "ms");
        }
    }

    static String teile(double[] m) { return m.length > 2 ? f("Ränder %.3f, Stufen %.3f", m[2], m[3]) : ""; }

    static Swash.Antrieb welle(double d) {
        return (t, x, y, aus) -> {
            double e = 0.4 * Math.sin(2 * Math.PI * t / 6);
            aus[0] = e;
            aus[1] = 2 * (Math.sqrt(GR * Math.max(0, d + e)) - Math.sqrt(GR * d));
            aus[2] = 0;
        };
    }

    /** Seit S6b-3 (SF68 A, Datei 71/72): Vorlauf der Karte vor der Messung (s Rechnung) und gemessene Schritte [G]. */
    static final double VORLAUF_S = 1.0;
    static final int MESS_SCHRITTE = 200;

    /**
     * Misst einen Schritt auf der Karte (Zeitschritt und Antrieb aus der Referenz); gibt {ms, Anteil der nassen Kacheln, ms
     * Ränder, ms Stufen}. Mit {@code tempo} dazu das Tempo gegen die Referenz. Seit S6b-3 (SF68 A): erst rund
     * {@link #VORLAUF_S} s Rechnung mit den ersten 16 Schritten (der Takt der Karte steigt; L4e maß nach 8 Schritten
     * Vorlauf 1,72 statt 0,77–0,86 ms), dann von vorn ({@code hoch}) 8 + {@link #MESS_SCHRITTE} Schritte, gemessen die
     * letzten {@link #MESS_SCHRITTE} (vorher 32).
     */
    static double[] messe(Supplier<Swash> bau, boolean versch, boolean tempo) {
        Swash ref = bau.get(), kar = bau.get();
        int n = 8 + MESS_SCHRITTE;
        double[] dts = new double[n];
        try (SwashKarte k = new SwashKarte(geraet, konto, kar, 2 * n, false); Stoppuhr uhr = new Stoppuhr(geraet, geraet.rechnen, 16)) {
            k.verschmolzen = versch;
            k.hoch();
            for (int q = 0; q < n; q++) {
                double ta = ref.zeit();
                dts[q] = ref.schritt();
                k.antrieb(2 * q, ta);
                k.antrieb(2 * q + 1, ta + dts[q]);
            }
            k.antriebHoch();
            long v0 = System.nanoTime();
            for (int q = 0; System.nanoTime() - v0 < VORLAUF_S * 1e9; q = (q + 1) % 16) {   // Vorlauf: der Takt steigt
                Ablauf ab = new Ablauf(geraet);
                k.schritt(ab, dts[q], 2 * q, 2 * q + 1);
                geraet.rechnen.einmal(c -> ab.aufnehmen(c, null));
            }
            k.hoch();
            for (int q = 0; q < n; q++) {
                Ablauf ab = new Ablauf(geraet);
                k.schritt(ab, dts[q], 2 * q, 2 * q + 1);
                int qq = q;
                geraet.rechnen.einmal(c -> { if (qq >= 8) uhr.runde(c); ab.aufnehmen(c, qq >= 8 ? uhr : null); });
                if (q >= 8) uhr.sammle(true);
            }
            if (tempo) {
                Ablauf at = new Ablauf(geraet);
                k.tempo(at);
                geraet.rechnen.einmal(c -> at.aufnehmen(c, null));
                double tk = k.tempoLesen(), tr = 0;
                for (int j = 0; j < ref.ny; j++)
                    for (int i = 0; i < ref.nx; i++) {
                        double hh = ref.tiefe(i, j);
                        if (hh <= ref.hTrocken) continue;
                        double[] v = ref.geschwindigkeit(i, j);
                        tr = Math.max(tr, Math.max(Math.abs(v[0]), Math.abs(v[1])) + Math.sqrt(GR * hh));
                    }
                A.hoechstens(f("Tempo je Zeile auf der Karte gegen die Referenz (%.5f gegen %.5f m/s), relativ", tk, tr), tk / tr - 1, 1e-4, "");
            }
            k.runter();
            double ms = 0, rand = 0;
            for (java.util.Map.Entry<String, Double> e : uhr.summen().entrySet()) {
                ms += e.getValue();
                if (e.getKey().equals("swash_rand")) rand += e.getValue();
            }
            int runden = n - 8;
            int nk = 0, nn = 0;
            for (int ky = 0; ky < (kar.ny + 7) / 8; ky++)
                for (int kx = 0; kx < (kar.nx + 15) / 16; kx++) {
                    nk++;
                    boolean nass = false;
                    for (int j = ky * 8; j < Math.min(kar.ny, ky * 8 + 8) && !nass; j++)
                        for (int i = kx * 16; i < Math.min(kar.nx, kx * 16 + 16); i++) if (kar.tiefe(i, j) > kar.hTrocken) { nass = true; break; }
                    if (nass) nn++;
                }
            if (!uhr.verfuegbar) return new double[] {Double.NaN, (double) nn / nk};
            return new double[] {ms / runden, (double) nn / nk, rand / runden, (ms - rand) / runden};
        }
    }

    static void wege() {
        A.teil("10 Verschmolzen gegen einzeln auf der Karte");
        double schlimmst = 0;
        StringBuilder b = new StringBuilder();
        for (int fall = 0; fall < 2; fall++) {
            Supplier<Swash> bau;
            double tEnde;
            if (fall == 0) { bau = () -> SwashCheck.becken().fuellen(0.013, BUCKEL); tEnde = 60; }
            else {
                double d = 0.5, tanb = 0.05, per = 20, a = 0.02, xs = 3, dx = 0.025, om = 2 * Math.PI / per, rampe = 2 * per;
                int nx = (int) Math.round((xs + d / tanb + 2) / dx);
                bau = () -> {
                    Swash s = new Swash(nx, 1, dx, 0, 0, 0);
                    s.hSpitze = 1e-4;
                    s.grund((x, y) -> Math.max(-d, (xs - x) * tanb));
                    s.antrieb(Swash.E, (t, x, y, aus) -> {
                        double w = t < rampe ? 0.5 * (1 - Math.cos(Math.PI * t / rampe)) : 1, e = a * w * Math.sin(om * t);
                        aus[0] = e;
                        aus[1] = 2 * (Math.sqrt(GR * (d + e)) - Math.sqrt(GR * d));
                        aus[2] = 0;
                    });
                    return s.fuellen(0, null);
                };
                tEnde = 120;
            }
            verschmolzen = false;
            Swash e = paar(bau, tEnde, Double.NaN)[1];
            verschmolzen = true;
            Swash v = paar(bau, tEnde, Double.NaN)[1];
            double m = 0;
            for (int j = 0; j < e.ny; j++) for (int i = 0; i < e.nx; i++) m = Math.max(m, Math.abs(v.tiefe(i, j) - e.tiefe(i, j)));
            schlimmst = Math.max(schlimmst, m);
            b.append(f("%s%s: %.2e m, Auflauf %.5f gegen %.5f m", fall == 0 ? "" : "; ", fall == 0 ? "Becken 60 s" : "Carrier und Greenspan 120 s", m, v.auflauf(), e.auflauf()));
        }
        A.info("größte Abweichung der Tiefe", b.toString());
        A.hoechstens("verschmolzen gegen einzeln, größte Abweichung der Tiefe", schlimmst, 1e-5, "m");
    }
}
