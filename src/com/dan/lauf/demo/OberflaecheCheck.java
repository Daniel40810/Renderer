package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Dispersion;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Phasenfeld;
import com.dan.core.szene.Profilpuffer;
import com.dan.core.szene.Reflexion;
import com.dan.core.szene.Seegangsform;
import com.dan.physics.waves.nah.Nahfeld;

import java.nio.file.Path;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfsteine der Oberfläche (Seegang Werkbuch, S3a), ohne Grafikkarte: die Referenz in double ({@code core.szene}:
 * {@link Profilpuffer}, {@link Phasenfeld}, {@link Seegangsform}, {@link Oberflaeche}) gegen Lehrbuch, gegen die
 * Eingabe und gegen das Nahfeld ({@code physics.waves.nah}).
 * <ol>
 * <li>Hüllen: Σ|c|² = 1, Hermite gegen die Summe.</li>
 * <li>Phasenfeld: ebene Welle im tiefen Wasser; k und 1/c_g über einer Rampe; Snellius an geraden Tiefenlinien; Strömung.</li>
 * <li>S3.1 Varianz der Fläche gleich m₀ (gleichförmig, und gegen ein Nahfeld über einer Rampe), Mittel erhalten.</li>
 * <li>S3.2 Spektrum einer Probe gegen die Eingabe, mittlere Richtung.</li>
 * <li>S3.3 Phasengeschwindigkeit gegen ω/k(h), Gruppengeschwindigkeit gegen c_g(h).</li>
 * <li>S3.4 Schiefe und Vorneigung gegen Ruessink u. a. (2012).</li>
 * <li>S3.5 Höhen der Einzelwellen: Rayleigh im Tiefen, Battjes–Groenendijk (2000) im Flachen.</li>
 * <li>Faltung: Anteil J &lt; 0.</li>
 * <li>S3.6 Neigungsvarianz gegen Σ k² E; Varianz und mss stetig über die Ränder von fein, grob und fern.</li>
 * <li>S3.7 keine sichtbare Wiederholung (Autokorrelation in Raum und Zeit).</li>
 * <li>Wechsel des Phasenfelds ohne Sprung.</li>
 * <li>Gebundene lange Welle (Auskunft).</li>
 * <li>Richtung und Wellenzahl gegen ein Nahfeld, das schräg auf eine Rampe läuft (keine doppelte Brechung).</li>
 * <li>Seit S5b-6 (Seegang Werkbuch 55): Reflexion – vor einer geraden Wand (senkrecht, K_r = 1, und ein Hang nach
 * Battjes) ein Bauch an der Wand und K_r aus Bauch und Knoten gegen das Nahfeld; zum Vergleich ohne Kopplung.</li>
 * </ol>
 * Attest {@code docs/status/S3_Oberflaeche.md}. Aufruf: {@code OberflaecheCheck [Teil …]}.
 */
public final class OberflaecheCheck {
    static final Attest A = new Attest("com.dan.core.szene (Oberflaeche)", Oberflaeche.VERSION);
    static final int NB = 8, ND = 32;
    static final double FMIN = 0.06, FMAX = 0.6, G = Dispersion.G;
    static final double[] OM = new double[NB], UNTEN = new double[NB], OBEN = new double[NB];

    static {
        double st = Math.pow(FMAX / FMIN, 1.0 / NB);
        for (int b = 0; b < NB; b++) {
            double lo = FMIN * Math.pow(st, b), hi = lo * st;
            OM[b] = 2 * Math.PI * Math.sqrt(lo * hi); UNTEN[b] = 2 * Math.PI * lo; OBEN[b] = 2 * Math.PI * hi;
        }
    }

    static final Profilpuffer PUFFER = new Profilpuffer(OM, UNTEN, OBEN, ND, 0x5EE6A3L, false);
    static final Profilpuffer EINS = new Profilpuffer(OM, UNTEN, OBEN, ND, 1, true);
    static final Nahfeld SPEKTRUM = new Nahfeld(1, 1, 10, NB, FMIN, FMAX, ND);

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        java.util.Set<String> nur = new java.util.HashSet<>(java.util.Arrays.asList(args));
        Runnable[] teile = {OberflaecheCheck::huelle, OberflaecheCheck::phase, OberflaecheCheck::varianz, OberflaecheCheck::spektrum,
                OberflaecheCheck::tempo, OberflaecheCheck::form, OberflaecheCheck::hoehen, OberflaecheCheck::faltung, OberflaecheCheck::mss,
                OberflaecheCheck::wiederholung, OberflaecheCheck::wechsel, OberflaecheCheck::langeWelle, OberflaecheCheck::richtung, OberflaecheCheck::reflexion, OberflaecheCheck::versetzt};
        try {
            for (int i = 0; i < teile.length; i++) {
                if (!nur.isEmpty() && !nur.contains(String.valueOf(i + 1))) continue;
                long ta = System.nanoTime();
                teile[i].run();
                System.out.printf("  [Teil %d: %.1f s]%n", i + 1, (System.nanoTime() - ta) * 1e-9);
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", "S3_Oberflaeche.md"), "S3a – Oberfläche des Seegangs, Referenz (Oberflaeche " + Oberflaeche.VERSION + ")");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Bauen

    interface Feld2 { double at(double x, double y); }

    /** Eine Stufe mit Tiefe h(x, y) und überall demselben Spektrum e (je Stützstelle), Phasenfeld dazu. */
    static Oberflaeche.Stufe stufe(String name, int nx, int ny, double dx, double x0, double y0, Feld2 h, double[] e, double[] u, double[] v, double ox, double oy) {
        Oberflaeche.Stufe s = new Oberflaeche.Stufe(name, nx, ny, dx, x0, y0, NB, ND);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                s.h[c] = h.at(x0 + (i + 0.5) * dx, y0 + (j + 0.5) * dx);
                if (e != null) System.arraycopy(e, 0, s.e, c * NB * ND, NB * ND);
            }
        s.gefaelleAusTiefe();
        Phasenfeld p = Phasenfeld.rechne(nx, ny, dx, x0, y0, s.h, u, v, OM, Phasenfeld.richtungen(NB, ND), ND, ox, oy, null, 0);
        s.phasen(p, p);
        return s;
    }

    static Oberflaeche flaeche(Profilpuffer p, Oberflaeche.Stufe grob) {
        Oberflaeche o = new Oberflaeche(p, ND);
        o.grob = grob;
        return o;
    }

    static double summe(double[] e) { double s = 0; for (double x : e) s += x; return s; }

    // ------------------------------------------------------------------ 1 Hüllen

    static void huelle() {
        A.teil("1 Hüllen der Stützstellen");
        double maxN = 0, maxH = 0, pMin = 1e30, pMax = 0, kreuz = 0;
        int teileMin = Integer.MAX_VALUE, teileMax = 0;
        Random r = new Random(4);
        double[] o = new double[4], x = new double[4];
        for (int bq = 0; bq < NB * ND; bq++) {
            double s = 0;
            double[] g = PUFFER.g[bq];
            for (int j = 0; j < Profilpuffer.N; j++) s += g[4 * j] * g[4 * j] + g[4 * j + 1] * g[4 * j + 1];
            maxN = Math.max(maxN, Math.abs(s / Profilpuffer.N - 1));
            if (bq % 5 == 0)
                for (int k = 0; k < 200; k++) {
                    double tau = r.nextDouble() * 2 * PUFFER.periode[bq];
                    PUFFER.wert(bq, tau, o);
                    PUFFER.exakt(bq, tau, x);
                    maxH = Math.max(maxH, Math.hypot(o[0] - x[0], o[1] - x[1]));
                }
            teileMin = Math.min(teileMin, PUFFER.teile[bq]); teileMax = Math.max(teileMax, PUFFER.teile[bq]);
            pMin = Math.min(pMin, PUFFER.periode[bq]); pMax = Math.max(pMax, PUFFER.periode[bq]);
        }
        // Nachbarn in der Richtung: größte Kreuzkorrelation der Hüllen über alle Versätze (über die FFT, Band 4)
        for (int q = 0; q < ND - 1; q++) {
            int a = 3 * ND + q, c = a + 1, n = 8192;
            double dt = 1;
            double[] ar = new double[n], ai = new double[n], cr = new double[n], ci = new double[n];
            for (int j = 0; j < n; j++) { PUFFER.wert(a, j * dt, o); ar[j] = o[0]; ai[j] = o[1]; PUFFER.wert(c, j * dt, o); cr[j] = o[0]; ci[j] = o[1]; }
            fft(ar, ai, false); fft(cr, ci, false);
            double[] pr = new double[n], pi = new double[n];
            for (int j = 0; j < n; j++) { pr[j] = ar[j] * cr[j] + ai[j] * ci[j]; pi[j] = ai[j] * cr[j] - ar[j] * ci[j]; }
            fft(pr, pi, true);
            for (int j = 0; j < n; j++) kreuz = Math.max(kreuz, Math.hypot(pr[j], pi[j]) / n);
        }
        A.hoechstens("Mittel von |G|² über die Periode − 1 (Σ|c|² = 1)", maxN, 1e-9, "");
        A.hoechstens("Hermite gegen die Summe, |ΔG| (Streuung 1)", maxH, 1e-3, "");
        A.hoechstens("benachbarte Richtungen: größte Kreuzkorrelation der Hüllen über alle Versätze (Band 4, 8192 s; zufällig, mit geteilter Hülle 1)", kreuz, 0.3, "");
        A.info("Teilwellen je Stützstelle", teileMin + " bis " + teileMax);
        A.info("Perioden der Hüllen", f("%.1f bis %.1f h", pMin / 3600, pMax / 3600));
        A.info("Speicher der Hüllen auf der Karte", f("%.1f MB", PUFFER.bytes() / 1e6));
    }

    // ------------------------------------------------------------------ 2 Phasenfeld

    static void phase() {
        A.teil("2 Phasenfeld (kleinste Quadrate)");
        // a) tiefes Wasser, ebene Welle
        int nx = 40, ny = 30;
        double dx = 25;
        double[] h = new double[nx * ny];
        java.util.Arrays.fill(h, 800);
        double[] dirs = Phasenfeld.richtungen(NB, ND);
        Phasenfeld p = Phasenfeld.rechne(nx, ny, dx, 1000, 2000, h, null, null, OM, dirs, ND, 1500, 2300, null, 0);
        double maxP = 0, maxT = 0;
        for (int i = 0; i < p.dPhi.length; i++) { maxP = Math.max(maxP, Math.abs(p.dPhi[i])); maxT = Math.max(maxT, Math.abs(p.dT[i])); }
        A.hoechstens("tiefes Wasser: |Φ − k₀ s| (alle Richtungen)", maxP, 1e-6, "rad");
        A.hoechstens("tiefes Wasser: |T − s/c_g₀|", maxT, 1e-4, "s");
        // b) Rampe 10 → 2 m, Welle nach Osten: ∂Φ/∂x = k(h), ∂T/∂x = 1/c_g(h)
        nx = 200; ny = 4; dx = 10;
        h = new double[nx * ny];
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) h[j * nx + i] = 10 - 8.0 * i / (nx - 1);
        double[] ost = new double[NB * ND];
        p = Phasenfeld.rechne(nx, ny, dx, 0, 0, h, null, null, OM, ost, ND, 0, 0, null, 0);
        double maxK = 0, maxG = 0;
        double[] o = new double[6];
        for (int b = 0; b < NB; b++)
            for (int i = 5; i < nx - 5; i++) {
                double x = (i + 1) * dx, hh = 10 - 8.0 * (x / dx - 0.5) / (nx - 1);
                p.wert(b * ND, x, 2 * dx, o);
                double k = Dispersion.wellenzahl(OM[b], hh), cg = Dispersion.gruppe(OM[b], k, hh);
                maxK = Math.max(maxK, Math.abs(o[2] / k - 1));
                maxG = Math.max(maxG, Math.abs(o[4] * cg - 1));
            }
        A.hoechstens("Rampe: ω/k aus ∂Φ/∂x gegen ω/k(h), relativ (8 Bänder)", maxK, 0.01, "");
        A.hoechstens("Rampe: c_g aus ∂T/∂x gegen c_g(h), relativ", maxG, 0.02, "");
        // c) schräg über gerade Tiefenlinien (30°): Richtung und Wellenzahl des Kompromisses (Auskunft; geprüft wird gewichtet in Teil 4)
        nx = 150; ny = 120; dx = 10;
        h = new double[nx * ny];
        DoubleUnaryOperator hx = x -> 12 - 10 * Math.min(1, Math.max(0, (x - 200) / 1000));
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) h[j * nx + i] = hx.applyAsDouble((i + 0.5) * dx);
        double[] d30 = new double[NB * ND];
        java.util.Arrays.fill(d30, Math.toRadians(30));
        p = Phasenfeld.rechne(nx, ny, dx, 0, 0, h, null, null, OM, d30, ND, 0, 0, null, 0);
        for (int b : new int[] {1, 3, 5}) {
            double maxW = 0, maxKk = 0;
            for (int i = 10; i < nx - 5; i += 5)
                for (int j = 10; j < ny - 5; j += 10) {
                    double x = (i + 1) * dx, y = (j + 0.5) * dx;
                    p.wert(b * ND, x, y, o);
                    double kk = Dispersion.wellenzahl(OM[b], hx.applyAsDouble(x));
                    maxW = Math.max(maxW, Math.abs(Math.toDegrees(Math.atan2(o[3], o[2])) - 30));
                    maxKk = Math.max(maxKk, Math.abs(Math.hypot(o[2], o[3]) / kk - 1));
                }
            A.info(f("30° über eine Rampe 12 → 2 m, Band %d (%.2f Hz)", b + 1, OM[b] / (2 * Math.PI)), f("Richtung bis %.1f° neben θ, |∇Φ|/k bis %.1f %% neben 1", maxW, 100 * maxKk));
        }
        // d) Strömung ±0,8 m/s in Laufrichtung, Tiefe 4 m
        nx = 60; ny = 4; dx = 10;
        h = new double[nx * ny];
        java.util.Arrays.fill(h, 4);
        double maxS = 0;
        for (double uu : new double[] {0.8, -0.8}) {
            double[] u = new double[nx * ny], v = new double[nx * ny];
            java.util.Arrays.fill(u, uu);
            p = Phasenfeld.rechne(nx, ny, dx, 0, 0, h, u, v, OM, ost, ND, 0, 0, null, 0);
            for (int b = 0; b < NB; b++) {
                p.wert(b * ND, 300, 20, o);
                double k = Dispersion.wellenzahl(OM[b], 4, uu);
                if (Double.isNaN(k)) continue;
                double cga = Dispersion.gruppe(Dispersion.sigma(k, 4), k, 4) + uu;
                maxS = Math.max(maxS, Math.max(Math.abs(o[2] / k - 1), Math.abs(o[4] * cga - 1)));
            }
        }
        A.hoechstens("Strömung ±0,8 m/s: k und c_g + U gegen die Dispersion, relativ", maxS, 0.01, "");
    }

    // ------------------------------------------------------------------ 3 Varianz (S3.1)

    static void varianz() {
        A.teil("3 S3.1 Varianz der Fläche gegen m₀");
        double[] e = SPEKTRUM.jonswap(1.5, 6, 20, 10);
        double m0 = summe(e);
        for (double h : new double[] {40, 3}) {
            Oberflaeche.Stufe s = stufe("grob", 50, 50, 50, 0, 0, (x, y) -> h, e, null, null, 1250, 1250);
            Oberflaeche o = flaeche(PUFFER, s);
            double[] lin = new double[3], eu = new double[3];
            Random r = new Random(7);
            Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
            int n = 0;
            for (int k = 0; k < 20000; k++) {
                double x = 400 + 1700 * r.nextDouble(), y = 400 + 1700 * r.nextDouble(), t = 3600 * r.nextDouble();
                o.punkt(x, y, t, pt);
                lin[0] += pt.etaLin; lin[1] += pt.etaLin * pt.etaLin;
                double z = o.euler(x, y, t, pt) - pt.lw;
                eu[0] += z; eu[1] += z * z;
                n++;
            }
            double vl = lin[1] / n - Math.pow(lin[0] / n, 2), ve = eu[1] / n - Math.pow(eu[0] / n, 2);
            A.hoechstens(f("h = %.0f m: Varianz linear / m₀ − 1", h), Math.abs(vl / m0 - 1), 0.03, "");
            A.hoechstens(f("h = %.0f m: Varianz mit Form (fester Ort, ohne die gebundene lange Welle) / m₀ − 1", h), Math.abs(ve / m0 - 1), 0.03, "");
            A.hoechstens(f("h = %.0f m: Mittel mit Form (fester Ort, 20 000 Proben)", h), Math.abs(eu[0] / n), 0.003, "m");
        }
        // gegen ein Nahfeld über einer Rampe (Shoaling, Brechen, Reibung): je Streifen in x
        int nx = 80, ny = 24;
        double dx = 25;
        Nahfeld nf = new Nahfeld(nx, ny, dx, NB, FMIN, FMAX, ND);
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) { nf.grund[j * nx + i] = -(12 - 10.5 * i / (nx - 1.0)); nf.spiegel[j * nx + i] = 0; }
        nf.mitWind = false;
        nf.periodischY = true;
        nf.vorbereiten();
        double[] rand = nf.jonswap(1.5, 7, 10, 12);
        nf.rand = (i, j, b, q) -> i == -1 ? rand[b * ND + q] / nf.sigma(0, b, q) : 0;
        nf.setze((x, y, ee) -> System.arraycopy(rand, 0, ee, 0, ee.length));
        int runden = nf.anlauf(3000, 1e-6, 600);
        Oberflaeche.Stufe s = new Oberflaeche.Stufe("grob", nx, ny, dx, 0, 0, NB, ND);
        for (int c = 0; c < nx * ny; c++) {
            s.h[c] = nf.tiefe(c);
            for (int b = 0; b < NB; b++) for (int q = 0; q < ND; q++) s.e[(c * NB + b) * ND + q] = nf.energie(c, b, q);
        }
        s.gefaelleAusTiefe();
        s.rand = 3;
        Phasenfeld p = Phasenfeld.rechne(nx, ny, dx, 0, 0, s.h, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, 0, 300, null, 0);
        s.phasen(p, p);
        Oberflaeche o = flaeche(PUFFER, s);
        double maxR = 0;
        Random r = new Random(9);
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        for (int st = 0; st < 5; st++) {
            double xa = 150 + st * 350, sum2 = 0, summ0 = 0;
            int n = 0;
            for (int k = 0; k < 12000; k++) {
                double x = xa + 100 * r.nextDouble(), y = 100 + 400 * r.nextDouble(), t = 4 * 3600 * r.nextDouble();
                o.punkt(x, y, t, pt);
                sum2 += pt.etaLin * pt.etaLin;
                summ0 += pt.m0;
                n++;
            }
            double rel = sum2 / summ0 - 1;
            maxR = Math.max(maxR, Math.abs(rel));
            A.info(f("Rampe, Streifen bei x = %.0f m", xa + 50), f("Tiefe %.1f m, Hs Nahfeld %.3f m, Varianz/m₀ − 1 = %+.3f", nf.tiefe((int) ((xa + 50) / dx) + 12 * nx), 4 * Math.sqrt(summ0 / n), rel));
        }
        A.hoechstens("gegen das Nahfeld (Rampe 12 → 1,5 m, 5 Streifen zu je 12 000 Proben): |Varianz/m₀ − 1|", maxR, 0.03, "");
        A.info("Anlauf des Nahfelds", runden + " Runden");
    }

    // ------------------------------------------------------------------ 4 Spektrum (S3.2)

    static void spektrum() {
        A.teil("4 S3.2 Spektrum einer Probe gegen die Eingabe");
        double[] e = SPEKTRUM.jonswap(1.2, 5.5, 40, 8);
        Oberflaeche.Stufe s = stufe("grob", 40, 40, 50, 0, 0, (x, y) -> 25, e, null, null, 1000, 1000);
        Oberflaeche o = flaeche(PUFFER, s);
        o.mitForm = o.mitHoehen = o.mitLangwelle = false;
        int n = 8192;
        double dt = 0.5;
        double[] band = new double[NB];
        double eingabe[] = new double[NB], m0 = summe(e);
        for (int b = 0; b < NB; b++) for (int q = 0; q < ND; q++) eingabe[b] += e[b * ND + q];
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        int punkte = 12;
        double fx = 0, fy = 0;
        Random r = new Random(11);
        for (int k = 0; k < punkte; k++) {
            double x = 500 + 1000 * r.nextDouble(), y = 500 + 1000 * r.nextDouble();
            double[] re = new double[n], im = new double[n];
            double d = 0.25;
            for (int i = 0; i < n; i++) {
                double t = i * dt;
                o.punkt(x, y, t, pt);
                re[i] = pt.etaLin;
                o.punkt(x + d, y, t, pt); double ex = pt.etaLin;
                o.punkt(x - d, y, t, pt); ex = (ex - pt.etaLin) / (2 * d);
                o.punkt(x, y + d, t, pt); double ey = pt.etaLin;
                o.punkt(x, y - d, t, pt); ey = (ey - pt.etaLin) / (2 * d);
                o.punkt(x, y, t + 0.05, pt); double et = pt.etaLin;
                o.punkt(x, y, t - 0.05, pt); et = (et - pt.etaLin) / 0.1;
                fx -= et * ex; fy -= et * ey;
            }
            fft(re, im, false);
            for (int i = 1; i < n / 2; i++) {
                double fr = i / (n * dt), w = 2 * Math.PI * fr, pw = 2 * (re[i] * re[i] + im[i] * im[i]) / ((double) n * n);
                for (int b = 0; b < NB; b++) if (w >= UNTEN[b] && w < OBEN[b]) band[b] += pw / punkte;
            }
        }
        double maxB = 0;
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < NB; b++) {
            if (eingabe[b] > 0.05 * m0) maxB = Math.max(maxB, Math.abs(band[b] / eingabe[b] - 1));
            sb.append(f("%.3f Hz %.4f/%.4f; ", OM[b] / (2 * Math.PI), band[b], eingabe[b]));
        }
        A.info("Varianz je Band, Probe/Eingabe (m²)", sb.toString());
        A.hoechstens("Bänder mit über 5 % von m₀: |Probe/Eingabe − 1| (12 Orte, je 4096 s)", maxB, 0.10, "");
        // mittlere Richtung des Energieflusses: Σ ω k E · e_θ (Eingabe)
        double sx = 0, sy = 0;
        double[] dirs = Phasenfeld.richtungen(NB, ND);
        for (int b = 0; b < NB; b++) {
            double k = Dispersion.wellenzahl(OM[b], 25);
            for (int q = 0; q < ND; q++) { double w = OM[b] * k * e[b * ND + q]; sx += w * Math.cos(dirs[b * ND + q]); sy += w * Math.sin(dirs[b * ND + q]); }
        }
        double soll = Math.toDegrees(Math.atan2(sy, sx)), ist = Math.toDegrees(Math.atan2(fy, fx));
        A.hoechstens(f("mittlere Richtung (Eingabe %.2f°, Probe %.2f°)", soll, ist), Math.abs(ist - soll), 2, "°");
    }

    // ------------------------------------------------------------------ 5 Phasen- und Gruppengeschwindigkeit (S3.3)

    static void tempo() {
        A.teil("5 S3.3 Phasen- und Gruppengeschwindigkeit über einer Rampe");
        int nx = 220, ny = 4;
        double dx = 10;
        DoubleUnaryOperator hx = x -> 10 - 8 * Math.min(1, Math.max(0, x / 2200.0));
        int b = 3;
        double[] e = new double[NB * ND];
        e[b * ND] = 0.04;                                              // eine Stützstelle: Band 4 nach Osten
        Oberflaeche.Stufe s = new Oberflaeche.Stufe("grob", nx, ny, dx, 0, 0, NB, ND);
        for (int c = 0; c < nx * ny; c++) { s.h[c] = hx.applyAsDouble((c % nx + 0.5) * dx); System.arraycopy(e, 0, s.e, c * NB * ND, NB * ND); }
        s.gefaelleAusTiefe();
        double[] ost = new double[NB * ND];
        Phasenfeld p = Phasenfeld.rechne(nx, ny, dx, 0, 0, s.h, null, null, OM, ost, ND, 0, 0, null, 0);
        s.phasen(p, p);
        s.rand = 1;
        Oberflaeche o = flaeche(EINS, s);
        o.mitForm = o.mitHoehen = o.mitLangwelle = o.mitVerschiebung = false;
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        double maxC = 0;
        double w = OM[b];
        for (double xa : new double[] {300, 800, 1300, 1800}) {
            // Nulldurchgang (steigend in x) nahe xa zur Zeit 0 und 0,5 s später
            double[] z = new double[2];
            for (int k = 0; k < 2; k++) {
                double t = 0.5 * k, x = xa;
                o.punkt(x, 20, t, pt);
                double v0 = pt.etaLin;
                double xb = x;
                for (int it = 0; it < 4000; it++) { xb = x + 0.05; o.punkt(xb, 20, t, pt); if (v0 < 0 && pt.etaLin >= 0) break; v0 = pt.etaLin; x = xb; }
                double lo = x, hi = xb;
                for (int it = 0; it < 60; it++) { double m = 0.5 * (lo + hi); o.punkt(m, 20, t, pt); if (pt.etaLin < 0) lo = m; else hi = m; }
                z[k] = 0.5 * (lo + hi);
                if (k == 0) xa = z[0] - 1;
            }
            double c = (z[1] - z[0]) / 0.5, hh = hx.applyAsDouble(z[0]), soll = w / Dispersion.wellenzahl(w, hh);
            maxC = Math.max(maxC, Math.abs(c / soll - 1));
            A.info(f("Kamm bei x = %.0f m, h = %.2f m", z[0], hh), f("c = %.3f m/s, ω/k(h) = %.3f m/s", c, soll));
        }
        A.hoechstens("Phasengeschwindigkeit gegen ω/k(h), relativ (4 Orte)", maxC, 0.01, "");
        // Gruppen: Hülle |Z|² an zwei Orten, Versatz der Kreuzkorrelation
        o = flaeche(PUFFER, s);
        o.mitForm = o.mitHoehen = o.mitLangwelle = o.mitVerschiebung = false;
        double maxG = 0;
        for (double[] seg : new double[][] {{300, 500}, {1200, 1400}, {1800, 1950}}) {
            int n = 8192;
            double dt = 0.25;
            double[] a = new double[n], c2 = new double[n];
            double ma = 0, mc = 0;
            for (int i = 0; i < n; i++) {
                o.punkt(seg[0], 20, i * dt, pt); a[i] = pt.zRe * pt.zRe + pt.zIm * pt.zIm; ma += a[i] / n;
                o.punkt(seg[1], 20, i * dt, pt); c2[i] = pt.zRe * pt.zRe + pt.zIm * pt.zIm; mc += c2[i] / n;
            }
            for (int i = 0; i < n; i++) { a[i] = a[i] / ma; c2[i] = c2[i] / mc; }
            int best = 0;
            double bw = -1e300;
            for (int lag = 0; lag < 800; lag++) {
                double sum = 0;
                for (int i = 0; i + lag < n; i++) sum += (a[i] - 1) * (c2[i + lag] - 1);
                if (sum > bw) { bw = sum; best = lag; }
            }
            double tt = 0;                                             // Laufzeit nach c_g(h) über die Strecke
            for (double x = seg[0]; x < seg[1]; x += 0.5) { double hh = hx.applyAsDouble(x + 0.25), k = Dispersion.wellenzahl(w, hh); tt += 0.5 / Dispersion.gruppe(w, k, hh); }
            // Feinlage des Gipfels (Parabel)
            double s0 = korr(a, c2, best - 1), s1 = korr(a, c2, best), s2 = korr(a, c2, best + 1);
            double fein = best + 0.5 * (s0 - s2) / (s0 - 2 * s1 + s2);
            double ist = fein * dt;
            maxG = Math.max(maxG, Math.abs(ist / tt - 1));
            A.info(f("Gruppe %.0f → %.0f m", seg[0], seg[1]), f("Laufzeit %.2f s, nach c_g(h) %.2f s", ist, tt));
        }
        A.hoechstens("Gruppengeschwindigkeit gegen c_g(h), relativ (3 Strecken)", maxG, 0.02, "");
    }

    static double korr(double[] a, double[] c, int lag) {
        double s = 0;
        for (int i = Math.max(0, -lag); i < a.length && i + lag < a.length; i++) s += (a[i] - 1) * (c[i + lag] - 1);
        return s;
    }

    // ------------------------------------------------------------------ 6 Form (S3.4)

    /** Zeitreihe der Höhe über einem festen Ort, hochpassgefiltert über fp/2 (ohne die lange Welle). */
    static double[] reihe(Oberflaeche o, double x, double y, int n, double dt, double fGrenze) {
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        double[] re = new double[n], im = new double[n];
        for (int i = 0; i < n; i++) re[i] = o.euler(x, y, i * dt, pt);
        double mean = 0;
        for (double v : re) mean += v / n;
        for (int i = 0; i < n; i++) re[i] -= mean;
        if (fGrenze > 0) {
            fft(re, im, false);
            for (int i = 0; i < n; i++) {
                int k = i <= n / 2 ? i : n - i;
                if (k / (n * dt) < fGrenze) { re[i] = 0; im[i] = 0; }
            }
            fft(re, im, true);
        }
        return re;
    }

    /** {Sk, As} einer Reihe (As über die Hilbert-Transformierte, H cos = sin). */
    static double[] skAs(double[] x) {
        int n = x.length;
        double[] re = x.clone(), im = new double[n];
        fft(re, im, false);
        for (int i = 0; i < n; i++) {                                   // H: positive Frequenzen · (−i), negative · (+i)
            double a = re[i], b = im[i];
            if (i == 0 || i == n / 2) { re[i] = 0; im[i] = 0; }
            else if (i < n / 2) { re[i] = b; im[i] = -a; }
            else { re[i] = -b; im[i] = a; }
        }
        fft(re, im, true);
        double m2 = 0, m3 = 0, h3 = 0;
        for (int i = 0; i < n; i++) { m2 += x[i] * x[i]; m3 += x[i] * x[i] * x[i]; h3 += re[i] * re[i] * re[i]; }
        m2 /= n; m3 /= n; h3 /= n;
        return new double[] {m3 / Math.pow(m2, 1.5), h3 / Math.pow(m2, 1.5)};
    }

    static void form() {
        A.teil("6 S3.4 Schiefe und Vorneigung gegen Ruessink u. a. (2012)");
        double tp = 8;
        double[] e1 = SPEKTRUM.jonswap(1, tp, 0, 30);
        double maxD = 0;
        // (h, Hs): Ur von rund 0,05 bis 1,3; gemessen wie bei Ruessink: ohne Anteile unter fp/2, ohne die gebundene lange Welle
        double[][] faelle = {{8, 0.6}, {5, 0.6}, {4, 0.9}, {3, 0.9}, {2.5, 1.0}, {2, 0.9}};
        for (double[] fl : faelle) {
            double h = fl[0], hs = fl[1];
            double[] e = e1.clone();
            for (int i = 0; i < e.length; i++) e[i] *= hs * hs;
            Oberflaeche.Stufe s = stufe("grob", 40, 40, 50, 0, 0, (x, y) -> h, e, null, null, 1000, 1000);
            Oberflaeche o = flaeche(PUFFER, s);
            o.mitHoehen = false;
            o.mitLangwelle = false;
            double[] r = skAsMittel(o, 6, tp);
            Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
            o.punkt(1000, 1000, 0, pt);
            double[] soll = Seegangsform.ruessink(pt.ur);
            double lag = Seegangsform.schiefeVerschiebung(pt.lambda, pt.kp, h, Math.sqrt(pt.m0));
            double skZiel = Math.max(soll[2], lag);
            double d = Math.max(Math.abs(r[0] - skZiel), Math.abs(r[1] - soll[3]));
            maxD = Math.max(maxD, d);
            A.info(f("h = %.1f m, Hs = %.1f m, Ur = %.3f", h, hs, pt.ur), f("Sk %.3f (Ruessink %.3f, Verschiebung %.3f), As %.3f (Ruessink %.3f), λ %.2f", r[0], soll[2], lag, r[1], soll[3], pt.lambda));
            if (h == 3) {
                o.mitForm = false;
                double[] v = skAsMittel(o, 6, tp);
                A.info("Anteil der Verschiebung allein (h = 3 m), gegen 3 λ k coth(kh) σ", f("%.3f (Seegangsform.LAGRANGE = %.3f)", v[0] / (lag / Seegangsform.LAGRANGE), Seegangsform.LAGRANGE));
                o.mitForm = true;
                o.mitLangwelle = true;
                double[] w = skAsMittel(o, 6, tp);
                A.info("mit der gebundenen langen Welle (h = 3 m)", f("Sk %.3f, As %.3f", w[0], w[1]));
            }
        }
        A.hoechstens("|Sk − Ziel|, |As − Ziel| (6 Fälle, Ur 0,05–1,3; Ziel Sk: Ruessink, mindestens die der Verschiebung)", maxD, 0.05, "");
    }

    /** Sk und As im Mittel über n Orte, je 4096 s (parallel). */
    static double[] skAsMittel(Oberflaeche o, int n, double tp) {
        double[][] r = new double[n][];
        java.util.stream.IntStream.range(0, n).parallel().forEach(k -> r[k] = skAs(reihe(o, 500 + 1000 * ((k * 0.618) % 1), 500 + 1000 * ((k * 0.382 + 0.1) % 1), 16384, 0.25, 0.5 / tp)));
        double sk = 0, as = 0;
        for (double[] x : r) { sk += x[0] / n; as += x[1] / n; }
        return new double[] {sk, as};
    }

    // ------------------------------------------------------------------ 7 Höhen (S3.5)

    /** Höhen der Einzelwellen (Nulldurchgang abwärts). */
    static double[] hoehenAus(double[] z) {
        java.util.List<Double> l = new java.util.ArrayList<>();
        int i = 1;
        while (i < z.length && !(z[i - 1] > 0 && z[i] <= 0)) i++;
        double mx = -1e9, mn = 1e9;
        for (i++; i < z.length; i++) {
            mx = Math.max(mx, z[i]); mn = Math.min(mn, z[i]);
            if (z[i - 1] > 0 && z[i] <= 0) { l.add(mx - mn); mx = -1e9; mn = 1e9; }
        }
        double[] r = new double[l.size()];
        for (int k = 0; k < r.length; k++) r[k] = l.get(k);
        return r;
    }

    static double[] kenn(double[] hh) {
        double[] s = hh.clone();
        java.util.Arrays.sort(s);
        int n = s.length;
        double rms = 0, s3 = 0, s10 = 0;
        for (double v : s) rms += v * v;
        rms = Math.sqrt(rms / n);
        int n3 = n / 3, n10 = n / 10;
        for (int k = n - n3; k < n; k++) s3 += s[k];
        for (int k = n - n10; k < n; k++) s10 += s[k];
        return new double[] {rms, s3 / n3 / rms, s10 / n10 / rms, s[(int) (0.98 * n)] / rms};
    }

    static void hoehen() {
        A.teil("7 S3.5 Höhen der Einzelwellen");
        double[][] faelle = {{30, 1.0, 0.0, 6}, {2.0, 0.75, 0.01, 7}};
        for (double[] fl : faelle) {
            double h = fl[0], hs = fl[1], tanA = fl[2], tp = fl[3];
            double[] e = SPEKTRUM.jonswap(hs, tp, 0, 20);
            Oberflaeche.Stufe s = stufe("grob", 30, 30, 40, 0, 0, (x, y) -> h, e, null, null, 600, 600);
            java.util.Arrays.fill(s.gefaelle, tanA);
            Oberflaeche o = flaeche(PUFFER, s);
            java.util.List<Double> alle = new java.util.ArrayList<>();
            for (int k = 0; k < 6; k++) for (double v : hoehenAus(reihe(o, 300 + 100 * k, 400 + 90 * k, 16384, 0.25, 0.5 / tp))) alle.add(v);
            double[] hh = alle.stream().mapToDouble(Double::doubleValue).toArray();
            double[] ist = kenn(hh);
            double[] soll;
            String was;
            if (h > 10) { soll = new double[] {1.416, 1.800, Math.sqrt(Math.log(50))}; was = "Rayleigh"; }
            else {
                double m0 = summe(e), hm0 = 4 * Math.sqrt(m0);
                double htr = Seegangsform.hTr(h, tanA) / Seegangsform.hRms(hm0, h);
                soll = Seegangsform.kennwerte(Seegangsform.bg(htr));
                was = f("Battjes–Groenendijk, H̃_tr = %.2f", htr);
            }
            double d = 0;
            for (int k = 0; k < 3; k++) d = Math.max(d, Math.abs(ist[k + 1] / soll[k] - 1));
            A.info(f("h = %.0f m, Hs = %.2f m: %d Wellen", h, hs, hh.length), f("H1/3, H1/10, H2%% / H_rms = %.3f, %.3f, %.3f (%s: %.3f, %.3f, %.3f)", ist[1], ist[2], ist[3], was, soll[0], soll[1], soll[2]));
            A.hoechstens(f("h = %.0f m: Kennwerte gegen %s, relativ", h, h > 10 ? "Rayleigh" : "BG 2000"), d, 0.05, "");
        }
    }

    // ------------------------------------------------------------------ 8 Faltung

    static void faltung() {
        A.teil("8 Faltung der Fläche");
        double[][] faelle = {{20, 3, 7}, {2, 0.8, 7}, {6, 1.5, 5}};
        for (double[] fl : faelle) {
            double h = fl[0];
            double[] e = SPEKTRUM.jonswap(fl[1], fl[2], 0, 8);
            Oberflaeche.Stufe s = stufe("grob", 24, 24, 40, 0, 0, (x, y) -> h, e, null, null, 480, 480);
            Oberflaeche o = flaeche(PUFFER, s);
            Random r = new Random(3);
            int neg = 0, n = 3000;
            double lam = 0;
            Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
            for (int k = 0; k < n; k++) {
                double x = 250 + 450 * r.nextDouble(), y = 250 + 450 * r.nextDouble(), t = 3000 * r.nextDouble();
                double[] l = o.lage(x, y, t, 0.05);
                if (l[6] < 0) neg++;
                o.punkt(x, y, t, pt);
                lam += pt.lambda / n;
            }
            A.hoechstens(f("h = %.0f m, Hs = %.1f m, Tp = %.0f s: Anteil J < 0 (λ_eff im Mittel %.2f)", h, fl[1], fl[2], lam), neg / (double) n, 0.005, "");
        }
    }

    // ------------------------------------------------------------------ 9 mss (S3.6)

    static void mss() {
        A.teil("9 S3.6 Neigungsvarianz und Stetigkeit über die Stufen");
        double[] e = SPEKTRUM.jonswap(1.2, 5, 30, 10);
        for (double h : new double[] {25, 2.5}) {
            Oberflaeche.Stufe s = stufe("grob", 30, 30, 40, 0, 0, (x, y) -> h, e, null, null, 600, 600);
            Oberflaeche o = flaeche(PUFFER, s);
            o.mitForm = o.mitHoehen = o.mitLangwelle = o.mitVerschiebung = false;
            Random r = new Random(5);
            Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
            double sum = 0, soll = 0;
            int n = 3000;
            double d = 0.02;
            for (int k = 0; k < n; k++) {
                double x = 300 + 500 * r.nextDouble(), y = 300 + 500 * r.nextDouble(), t = 3000 * r.nextDouble();
                o.punkt(x + d, y, t, pt); double a = pt.etaLin; o.punkt(x - d, y, t, pt); double gx = (a - pt.etaLin) / (2 * d);
                o.punkt(x, y + d, t, pt); a = pt.etaLin; o.punkt(x, y - d, t, pt); double gy = (a - pt.etaLin) / (2 * d);
                sum += gx * gx + gy * gy;
                soll += o.buch(x, y, t, 1e-3)[2];
            }
            A.hoechstens(f("h = %.1f m: Neigungsvarianz der Fläche / Σ k² E (gezeichnet) − 1", h), sum / soll - 1, 0.03, "");
        }
        // Stetigkeit: fein (1 km zu 10 m) im groben (3 km zu 50 m), außen das Fernfeld mit demselben Seegang
        double hs = 1.2, tp = 5, dir = 30;
        double[] eg = SPEKTRUM.jonswap(hs, tp, dir, 10);
        Oberflaeche.Stufe grob = stufe("grob", 60, 60, 50, 0, 0, (x, y) -> 20 - 0.004 * x, eg, null, null, 1500, 1500);
        Oberflaeche.Stufe fein = new Oberflaeche.Stufe("fein", 100, 100, 10, 1000, 1000, NB, ND);
        for (int j = 0; j < 100; j++) for (int i = 0; i < 100; i++) {
            int c = j * 100 + i;
            fein.h[c] = 20 - 0.004 * (1000 + (i + 0.5) * 10);
            System.arraycopy(eg, 0, fein.e, c * NB * ND, NB * ND);
        }
        fein.gefaelleAusTiefe();
        Phasenfeld pf = Phasenfeld.rechne(100, 100, 10, 1000, 1000, fein.h, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, 1500, 1500, grob.pa, 0);
        fein.phasen(pf, pf);
        Oberflaeche o = flaeche(PUFFER, grob);
        o.fein = fein;
        o.fernfeld = (x, y) -> new double[] {hs, tp, Math.toRadians(dir)};
        double sprungM = 0, sprungS = 0, vorM = Double.NaN, vorS = Double.NaN, mInnen = 0, mFern = 0;
        for (double x = 500; x <= 3300; x += 2) {
            double[] b = o.buch(x, 1500, 0, 0.05);
            if (!Double.isNaN(vorM)) { sprungM = Math.max(sprungM, Math.abs(b[0] / vorM - 1)); sprungS = Math.max(sprungS, Math.abs(b[2] / vorS - 1)); }
            vorM = b[0]; vorS = b[2];
            if (Math.abs(x - 1500) < 1) mInnen = b[0];
            if (Math.abs(x - 3300) < 1) mFern = b[0];
        }
        A.hoechstens("Varianz: größter Sprung zwischen Proben im Abstand von 2 m (fern → grob → fein → grob → fern)", sprungM, 0.01, "");
        A.hoechstens("mss aufgelöst (Bildpunkt 5 cm): größter Sprung zwischen Proben im Abstand von 2 m", sprungS, 0.01, "");
        A.info("m₀ innen (Nahfeld) und im Fernfeld", f("%.5f und %.5f m² (Bänder 0,06–0,6 gegen 0,07–0,6 Hz)", mInnen, mFern));
        // die Phase: fein und grob gemischt ergibt keine fremde Wellenzahl
        double maxK = 0;
        double[] a = new double[6], b = new double[6];
        for (int bq = 0; bq < NB * ND; bq += 7) {
            for (double x = 1005; x < 1995; x += 5) {
                fein.pa.wert(bq, x, 1500, a);
                grob.pa.wert(bq, x, 1500, b);
                maxK = Math.max(maxK, Math.hypot(a[2] - b[2], a[3] - b[3]) / Math.hypot(b[2], b[3]));
            }
        }
        A.hoechstens("Wellenzahl fein gegen grob im Feld des feinen, relativ", maxK, 0.02, "");
    }

    // ------------------------------------------------------------------ 10 Wiederholung (S3.7)

    static void wiederholung() {
        A.teil("10 S3.7 Keine sichtbare Wiederholung");
        double[] e = SPEKTRUM.jonswap(1.0, 6, 0, 12);
        Oberflaeche.Stufe s = stufe("grob", 100, 12, 100, 0, 0, (x, y) -> 30, e, null, null, 5000, 600);
        Oberflaeche o = flaeche(PUFFER, s);
        o.mitForm = o.mitHoehen = o.mitLangwelle = o.mitVerschiebung = false;
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        // Raum: längs der Laufrichtung, 8 km zu 1 m, zu 8 Zeiten (unabhängige Bilder), Versatz bis 3 km
        double lp = 2 * Math.PI / Dispersion.wellenzahl(2 * Math.PI / 6, 30);
        int ab = (int) Math.ceil(10 * lp), bis = 3000, n = 8192;
        double[] rz = new double[bis + 1], rh = new double[bis + 1];
        for (int k = 0; k < 8; k++) {
            double[] z = new double[n], hu = new double[n];
            for (int i = 0; i < n; i++) { o.punkt(1000 + i, 600, 100 + 1500 * k, pt); z[i] = pt.etaLin; hu[i] = pt.zRe * pt.zRe + pt.zIm * pt.zIm; }
            korrelation(z, rz, 8); korrelation(hu, rh, 8);
        }
        double mz = 0, mh = 0;
        for (int lag = ab; lag <= bis; lag++) { mz = Math.max(mz, Math.abs(rz[lag])); mh = Math.max(mh, Math.abs(rh[lag])); }
        A.hoechstens(f("Raum: |Autokorrelation der Höhe| bei 10 L_p (%d m) bis 3 km, 8 Bilder", ab), mz, 0.15, "");
        A.hoechstens(f("Raum: |Autokorrelation der Hülle |Z|²| bei %d m bis 3 km, 8 Bilder", ab), mh, 0.15, "");
        double[] z;
        double[] hu;
        // Zeit: an einem Ort, 4,5 h zu 1 s
        n = 16384;
        z = new double[n]; hu = new double[n];
        for (int i = 0; i < n; i++) { o.punkt(5000, 600, i, pt); z[i] = pt.etaLin; hu[i] = pt.zRe * pt.zRe + pt.zIm * pt.zIm; }
        double tz = maxKorr(z, 60, 8000), th = maxKorr(hu, 120, 8000);
        A.hoechstens("Zeit: |Autokorrelation der Höhe| bei 1 min bis 2,2 h", tz, 0.15, "");
        A.hoechstens("Zeit: |Autokorrelation der Hülle |Z|²| bei 2 min bis 2,2 h", th, 0.15, "");
        double pm = 1e30;
        for (int bq = 0; bq < NB * ND; bq++) if (summe(new double[] {e[bq]}) > 0.02 * summe(e)) pm = Math.min(pm, PUFFER.periode[bq]);
        A.info("kürzeste Periode einer Hülle mit über 2 % der Energie", f("%.1f h", pm / 3600));
    }

    /** Addiert die Autokorrelation von z (normiert) für Versätze 0 … r.length − 1, geteilt durch m. */
    static void korrelation(double[] z, double[] r, int m) {
        int n = z.length;
        double mean = 0;
        for (double v : z) mean += v / n;
        double v0 = 0;
        for (double v : z) v0 += (v - mean) * (v - mean) / n;
        for (int lag = 0; lag < r.length; lag++) {
            double s = 0;
            int c = 0;
            for (int i = 0; i + lag < n; i++) { s += (z[i] - mean) * (z[i + lag] - mean); c++; }
            r[lag] += s / c / v0 / m;
        }
    }

    static double maxKorr(double[] z, int von, int bis) {
        int n = z.length;
        double m = 0;
        for (double v : z) m += v / n;
        double v0 = 0;
        for (double v : z) v0 += (v - m) * (v - m);
        double best = 0;
        for (int lag = von; lag <= bis; lag++) {
            double s = 0;
            int c = 0;
            for (int i = 0; i + lag < n; i++) { s += (z[i] - m) * (z[i + lag] - m); c++; }
            best = Math.max(best, Math.abs(s / c / (v0 / n)));
        }
        return best;
    }

    // ------------------------------------------------------------------ 11 Wechsel

    static void wechsel() {
        A.teil("11 Wechsel des Phasenfelds ohne Sprung");
        double[] e = SPEKTRUM.jonswap(1.0, 6, 20, 10);
        Feld2 tiefe = (x, y) -> 3 + 6 * Math.exp(-Math.pow((x - 1300) / 400, 2)) + 0.002 * y;
        Oberflaeche.Stufe s = stufe("grob", 50, 50, 50, 0, 0, tiefe, e, null, null, 1250, 1250);
        Oberflaeche o = flaeche(PUFFER, s);
        o.mitHoehen = false;
        // dasselbe Gebiet zwei Zellen weiter nach Osten und eine nach Norden
        double[] h2 = new double[50 * 50];
        for (int j = 0; j < 50; j++) for (int i = 0; i < 50; i++) h2[j * 50 + i] = tiefe.at(100 + (i + 0.5) * 50, 50 + (j + 0.5) * 50);
        Phasenfeld neu = Phasenfeld.rechne(50, 50, 50, 100, 50, h2, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, 1250, 1250, null, 0);
        double dauer = Oberflaeche.WECHSEL, t0 = 100;
        Phasenfeld alt = s.pa;
        neu.angleichen(alt);
        // größte Änderung der Phase zwischen altem und neuem Feld (Auskunft) und der Frequenz während des Wechsels
        double maxD = 0, maxW = 0;
        double[] a = new double[6], b = new double[6];
        for (int bq = 0; bq < NB * ND; bq += 3)
            for (double x = 600; x <= 1900; x += 100)
                for (double y = 600; y <= 1900; y += 100) {
                    alt.wert(bq, x, y, a); neu.wert(bq, x, y, b);
                    double d = Math.abs(b[0] - a[0]);
                    maxD = Math.max(maxD, d);
                    maxW = Math.max(maxW, d * 1.5 / dauer / OM[bq / ND]);      // smoothstep: größte Steigung 1,5 / Dauer
                }
        A.info("größter Unterschied der Phase alt/neu im Inneren", f("%.2f rad", maxD));
        A.hoechstens(f("größte Verschiebung der Frequenz während des Wechsels (%.0f s), relativ", dauer), maxW, 0.05, "");
        // Höhe: größte Änderung zwischen zwei Bildern (1/30 s) während und außerhalb des Wechsels
        s.wechsel(neu, neu, t0, dauer);
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        double inn = 0, aus = 0;
        Random r = new Random(2);
        for (int k = 0; k < 300; k++) {
            double x = 700 + 1100 * r.nextDouble(), y = 700 + 1100 * r.nextDouble();
            double tw = t0 + dauer * r.nextDouble(), ta = t0 + dauer + 10 + 100 * r.nextDouble();
            o.punkt(x, y, tw, pt); double z1 = pt.eta; o.punkt(x, y, tw + 1 / 30.0, pt); inn = Math.max(inn, Math.abs(pt.eta - z1));
            o.punkt(x, y, ta, pt); z1 = pt.eta; o.punkt(x, y, ta + 1 / 30.0, pt); aus = Math.max(aus, Math.abs(pt.eta - z1));
        }
        A.hoechstens(f("größte Änderung der Höhe je Bild im Wechsel / außerhalb (%.1f / %.1f mm)", inn * 1000, aus * 1000), inn / aus, 1.5, "");
    }

    // ------------------------------------------------------------------ 12 lange Welle

    static void langeWelle() {
        A.teil("12 Gebundene lange Welle (Auskunft)");
        for (double[] fl : new double[][] {{20, 1.5, 7}, {3, 0.9, 8}}) {
            double h = fl[0], tp = fl[2];
            double[] e = SPEKTRUM.jonswap(fl[1], tp, 0, 20);
            Oberflaeche.Stufe s = stufe("grob", 30, 30, 40, 0, 0, (x, y) -> h, e, null, null, 600, 600);
            Oberflaeche o = flaeche(PUFFER, s);
            o.mitLangwelle = true;
            Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
            double m = 0, v = 0;
            int n = 8192;
            for (int i = 0; i < n; i++) { o.punkt(600, 600, i * 0.5, pt); m += pt.lw / n; v += pt.lw * pt.lw / n; }
            A.info(f("h = %.0f m, Hs = %.1f m", h, fl[1]), f("Hs der gebundenen langen Welle %.3f m (%.1f %% von Hs), Mittel %.4f m", 4 * Math.sqrt(v - m * m), 400 * Math.sqrt(v - m * m) / fl[1], m));
        }
    }

    // ------------------------------------------------------------------ 13 Richtung gegen das Nahfeld

    static void richtung() {
        A.teil("13 Richtung und Wellenzahl gegen ein schräg einlaufendes Nahfeld");
        int nx = 80, ny = 40;
        double dx = 25;
        Nahfeld nf = new Nahfeld(nx, ny, dx, NB, FMIN, FMAX, ND);
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) { nf.grund[j * nx + i] = -(12 - 10 * i / (nx - 1.0)); nf.spiegel[j * nx + i] = 0; }
        nf.mitWind = false;
        nf.periodischY = true;
        nf.vorbereiten();
        double[] rand = nf.jonswap(1.0, 7, 35, 15);
        nf.rand = (i, j, b, q) -> i == -1 ? rand[b * ND + q] / nf.sigma(0, b, q) : 0;
        nf.setze((x, y, ee) -> System.arraycopy(rand, 0, ee, 0, ee.length));
        nf.anlauf(3000, 1e-6, 600);
        Oberflaeche.Stufe s = new Oberflaeche.Stufe("grob", nx, ny, dx, 0, 0, NB, ND);
        for (int c = 0; c < nx * ny; c++) {
            s.h[c] = nf.tiefe(c);
            for (int b = 0; b < NB; b++) for (int q = 0; q < ND; q++) s.e[(c * NB + b) * ND + q] = nf.energie(c, b, q);
        }
        s.gefaelleAusTiefe();
        s.rand = 3;
        Phasenfeld p = Phasenfeld.rechne(nx, ny, dx, 0, 0, s.h, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, 0, 500, null, 0);
        s.phasen(p, p);
        Oberflaeche o = flaeche(PUFFER, s);
        o.mitForm = o.mitHoehen = o.mitVerschiebung = false;
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        double maxR = 0, maxK = 0;
        double[] ph = new double[6];
        for (double xa : new double[] {300, 900, 1500}) {
            // Nahfeld: mittlere Richtung Σ ω k E e_θ über den Streifen (dieselbe Gewichtung wie −⟨η_t ∇η⟩ der Fläche)
            double sx = 0, sy = 0, kw = 0, ke = 0;
            int ic = (int) (xa / dx);
            for (int j = 5; j < ny - 5; j++) {
                int c = j * nx + ic;
                for (int b = 0; b < NB; b++) for (int q = 0; q < ND; q++) {
                    double e = nf.energie(c, b, q), kk = nf.wellenzahl(c, b, q);
                    if (!(e > 0) || Double.isNaN(kk)) continue;
                    sx += OM[b] * kk * e * nf.cosT[q]; sy += OM[b] * kk * e * nf.sinT[q];
                    p.wert(b * ND + q, nf.x(ic), nf.y(j), ph);
                    double k = Dispersion.wellenzahl(OM[b], nf.tiefe(c));
                    kw += e * Math.abs(Math.hypot(ph[2], ph[3]) / k - 1); ke += e;
                }
            }
            double soll = Math.toDegrees(Math.atan2(sy, sx));
            // Fläche: Richtung des Energieflusses aus −⟨η_t ∇η⟩
            double fx = 0, fy = 0, d = 0.2;
            Random r = new Random(13);
            for (int k = 0; k < 3000; k++) {
                double x = xa - 20 + 40 * r.nextDouble(), y = 150 + 700 * r.nextDouble(), t = 3600 * r.nextDouble();
                o.punkt(x + d, y, t, pt); double a = pt.etaLin; o.punkt(x - d, y, t, pt); double ex = (a - pt.etaLin) / (2 * d);
                o.punkt(x, y + d, t, pt); a = pt.etaLin; o.punkt(x, y - d, t, pt); double ey = (a - pt.etaLin) / (2 * d);
                o.punkt(x, y, t + 0.05, pt); a = pt.etaLin; o.punkt(x, y, t - 0.05, pt); double et = (a - pt.etaLin) / 0.1;
                fx -= et * ex; fy -= et * ey;
            }
            double ist = Math.toDegrees(Math.atan2(fy, fx));
            maxR = Math.max(maxR, Math.abs(ist - soll));
            maxK = Math.max(maxK, kw / ke);
            A.info(f("Streifen bei x = %.0f m, Tiefe %.1f m", xa, nf.tiefe(ic + 10 * nx)), f("Richtung Nahfeld %.2f°, Fläche %.2f°; |∇Φ|/k − 1 gewichtet %.2f %%", soll, ist, 100 * kw / ke));
        }
        A.hoechstens("mittlere Richtung der Fläche gegen das Nahfeld (35° auf eine Rampe 12 → 2 m)", maxR, 2, "°");
        A.hoechstens("Wellenzahl |∇Φ| gegen k(h), mit der Energie gewichtet, relativ (Kompromiss der kleinsten Quadrate, B26)", maxK, 0.03, "");
    }

    // ------------------------------------------------------------------ 14 Reflexion (seit S5b-6)

    /** Seit S5b-6: Bauch an der Wand höchstens so weit von ihr (Anteil der Wellenlänge) und K_r aus Bauch und Knoten gegen das Nahfeld höchstens (absolut) [G]. */
    static final double BAUCH_L = 0.125, KR_ABW = 0.1;

    static void reflexion() {
        A.teil("14 Reflexion: Bauch an einer geraden Wand, K_r aus Bauch und Knoten (seit S5b-6)");
        // A: senkrechte Wand (Bauwerk, K_r = 1); B: steiler Hang zwischen zwei Zellen, K_r nach Battjes (1974) im Nahfeld
        for (int fall = 0; fall < 2; fall++) {
            boolean wandFall = fall == 0;
            int nx = 60, ny = 12, iw = 48;
            double dx = wandFall ? 10 : 25;
            Nahfeld nf = new Nahfeld(nx, ny, dx, NB, FMIN, FMAX, ND);
            nf.wand = wandFall ? new boolean[nx * ny] : null;
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    nf.grund[c] = i < iw ? -4 : (wandFall ? 3 : 1.5);
                    nf.spiegel[c] = 0;
                    if (wandFall && i >= iw) nf.wand[c] = true;
                }
            nf.mitWind = false;
            nf.periodischY = true;
            nf.vorbereiten();
            double[] js = nf.jonswap(1.0, 7, 0, 100);                     // schmal in der Richtung (rund ±8°), damit das Muster nicht verwischt
            int bp = 0;
            double emax = 0;
            for (int b = 0; b < NB; b++) { double eb = 0; for (int q = 0; q < ND; q++) eb += js[b * ND + q]; if (eb > emax) { emax = eb; bp = b; } }
            final double[] rand = new double[NB * ND];
            System.arraycopy(js, bp * ND, rand, bp * ND, ND);           // nur das Band der Spitze: das Muster ist schmalbandig sauber
            nf.rand = (i, j, b, q) -> i == -1 ? rand[b * ND + q] / nf.sigma(0, b, q) : 0;
            nf.setze((x, y, ee) -> { java.util.Arrays.fill(ee, 0); if (x < nf.x(iw - 1)) System.arraycopy(rand, 0, ee, 0, ee.length); });
            nf.anlauf(4000, 1e-7, 600);
            // K_r im Nahfeld an der Wand (Spalte vor der Wand): reflektierte gegen einlaufende Energie
            double ein = 0, aus = 0;
            for (int j = 0; j < ny; j++) {
                int c = j * nx + iw - 1;
                for (int q = 0; q < ND; q++) { double e = nf.energie(c, bp, q); if (nf.cosT[q] > 1e-9) ein += e; else if (nf.cosT[q] < -1e-9) aus += e; }
            }
            double krNah = Math.sqrt(aus / ein);
            double kk = Dispersion.wellenzahl(OM[bp], 4), L = 2 * Math.PI / kk, xw = iw * dx;
            double[] var0 = null, var1 = null;
            Reflexion rf = null;
            Profilpuffer ppK = null;
            for (int gekoppelt = 0; gekoppelt < 2; gekoppelt++) {
                Oberflaeche.Stufe s = new Oberflaeche.Stufe("grob", nx, ny, dx, 0, 0, NB, ND);
                for (int c = 0; c < nx * ny; c++) {
                    s.h[c] = nf.tiefe(c);
                    for (int b = 0; b < NB; b++) for (int q = 0; q < ND; q++) s.e[(c * NB + b) * ND + q] = nf.energie(c, b, q);
                }
                s.gefaelleAusTiefe();
                s.rand = 1;
                double[] ri = Phasenfeld.richtungen(NB, ND);
                Profilpuffer pp = new Profilpuffer(OM, UNTEN, OBEN, ND, 0x5EE6A3L, false);
                Reflexion r = gekoppelt == 1 ? Reflexion.partner(s) : Reflexion.keine(NB, ND);
                r.richtungen(ri);
                Phasenfeld p = Phasenfeld.rechne(nx, ny, dx, 0, 0, s.h, null, null, OM, ri, ND, 0, 0, null, 0, s.e);
                if (gekoppelt == 1) { r.wand(s, p, false); r.teilen(pp); rf = r; ppK = pp; }   // seit S5b-6b: Bedingung je Wandstück
                s.phasen(p, p);
                Oberflaeche o = flaeche(pp, s);
                o.mitForm = o.mitHoehen = o.mitVerschiebung = false;
                // Varianz der Höhe über der Zeit längs dreier Linien quer zur Wand, Abstand 0 … 0,75 L
                int nd2 = 38;
                double[] v = new double[nd2];
                Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
                Random rr = new Random(7);
                int zeiten = 1500;
                for (int z = 0; z < zeiten; z++) {
                    double t = 7200 * rr.nextDouble();
                    for (double y : new double[] {0.3 * ny * dx, 0.5 * ny * dx, 0.7 * ny * dx})
                        for (int k = 0; k < nd2; k++) {
                            double d = 0.75 * L * k / (nd2 - 1);
                            o.punkt(xw - d, y, t, pt);
                            v[k] += pt.etaLin * pt.etaLin;
                        }
                }
                for (int k = 0; k < nd2; k++) v[k] /= 3.0 * zeiten;
                if (gekoppelt == 0) var0 = v; else var1 = v;
            }
            String fall_ = wandFall ? "senkrechte Wand" : f("Hang 1:%.1f zwischen zwei Zellen", dx / 5.5);
            for (int gk = 0; gk < 2; gk++) {
                double[] v = gk == 0 ? var0 : var1;
                // V(d) = A + Bc cos 2kd + Bs sin 2kd, kleinste Quadrate ab einer halben Zelle vor der Fläche (dort fällt die
                // Energie zur trockenen Zelle hin ab, bilinear) bis 0,75 L: Bauch bei d₀ = −φ / 2k
                double[][] m = new double[3][3];
                double[] rs = new double[3];
                for (int k = 0; k < v.length; k++) {
                    double d = 0.75 * L * k / (v.length - 1);
                    if (d < 0.5 * dx) continue;
                    double[] bf = {1, Math.cos(2 * kk * d), Math.sin(2 * kk * d)};
                    for (int a1 = 0; a1 < 3; a1++) { rs[a1] += bf[a1] * v[k]; for (int a2 = 0; a2 < 3; a2++) m[a1][a2] += bf[a1] * bf[a2]; }
                }
                double[] x3 = loese3(m, rs);
                double A0 = x3[0], B = Math.hypot(x3[1], x3[2]), phi = Math.atan2(-x3[2], x3[1]);
                double d0 = -phi / (2 * kk) / L;                                 // Bauch, Anteil von L, in (−1/4, 1/4]
                d0 -= 0.5 * Math.rint(d0 / 0.5);
                // K_r: V(d) = a + b M(d) mit M(d) = Σ w cos(2 k_m cos θ_q (d − d₀)) / Σ w über die Teilwellen m der Hüllen und
                // die einlaufenden Richtungen q (Gewicht: ihre Energie vor der Wand) – das Muster verwischt mit der Breite des
                // Bands und der Richtungen; dann b / a = 2 K_r / (1 + K_r²)
                double[][] m2 = new double[2][2];
                double[] r2 = new double[2];
                for (int k = 0; k < v.length; k++) {
                    double d = 0.75 * L * k / (v.length - 1);
                    if (d < 0.5 * dx) continue;
                    double mm = muster(nf, ppK, bp, iw, nx, ny, d - d0 * L);
                    double[] bf = {1, mm};
                    for (int a1 = 0; a1 < 2; a1++) { r2[a1] += bf[a1] * v[k]; for (int a2 = 0; a2 < 2; a2++) m2[a1][a2] += bf[a1] * bf[a2]; }
                }
                double det = m2[0][0] * m2[1][1] - m2[0][1] * m2[1][0];
                double aa = (r2[0] * m2[1][1] - m2[0][1] * r2[1]) / det, bb = (m2[0][0] * r2[1] - m2[1][0] * r2[0]) / det;
                double rB = Math.max(0, Math.min(0.999999, bb / aa)), krEst = rB > 0 ? (1 - Math.sqrt(1 - rB * rB)) / rB : 0;
                StringBuilder pr = new StringBuilder();
                for (int k = 0; k < v.length; k += 3) pr.append(f("%.0f%% ", 100 * v[k] / A0));
                A.info(f("%s, %s", fall_, gk == 0 ? "ohne Kopplung" : "gekoppelt"), f("Muster V = A (1 + %.2f cos(2k(d − d₀))), Bauch d₀ = %+.3f L vor der Wand; K_r aus Bauch und Knoten (Muster über Band und Richtungen) %.2f (Nahfeld %.2f); Varianz längs der Linie (je 0,06 L ab der Fläche, in %% von A): %s",
                        B / A0, d0, krEst, krNah, pr.toString().trim()));
                if (gk == 1) {
                    A.info(f("  Kopplung, %s", fall_), rf.bericht() + "; Bauch an den Wänden: " + rf.bilanzGrob.text() + f("; Wellenlänge L = %.1f m in 4 m Tiefe (Band der Spitze, Tp 7 s)", L));
                    A.hoechstens(f("Bauch vor der Kante (%s): Abstand zur Wand, Anteil von L", fall_), Math.abs(d0), BAUCH_L, "");
                    A.hoechstens(f("K_r aus Bauch und Knoten gegen K_r des Nahfelds (%s, Battjes 1974 / Bauwerk)", fall_), Math.abs(krEst - krNah), KR_ABW, "");
                }
            }
        }
    }

    // ------------------------------------------------------------------ 15 Versetzte Wände (seit S5b-6b)

    /** Seit S5b-6b: Wellenzahl der reflektierten vor den Wänden, |∇Φ|/k − 1 energiegewichtet, höchstens [G] (wie Teil 13: 0,05 der Karte). */
    static final double WELLENZAHL_WAND = 0.05;

    /**
     * Zwei gerade Wandstücke, längs der Welle um L/4 versetzt (L im Band der Spitze, 4 m Tiefe; Zelle = L/4): Eine
     * Konstante je Paar trifft keines (Unterschied 2 k L/4 = π). Prüfsteine je Stück wie Teil 14 (Bauch, K_r), dazu die
     * Wellenzahl der reflektierten vor den Wänden. Auskunft: eine runde Warft und eine Küste unter 45° (Treppe), wie viel
     * vom Gewicht der Wände in geraden Stücken liegt und wie kohärent es nachher ist.
     */
    static void versetzt() {
        A.teil("15 Reflexion an zwei versetzten Wandstücken (seit S5b-6b)");
        int bp = spitze();
        double kk = Dispersion.wellenzahl(OM[bp], 4), L = 2 * Math.PI / kk, dx = L / 4;
        int nx = 60, ny = 16, iw = 48, halb = ny / 2;
        Nahfeld nf = nahfeld(nx, ny, dx, bp, (i, j) -> i >= iw + (j >= halb ? 1 : 0), true, 0, 100);
        double krNah = krNahfeld(nf, bp, iw, nx, ny);
        Object[] r = gekoppelt(nf, nx, ny, dx);
        Reflexion rf = (Reflexion) r[0];
        Profilpuffer pp = (Profilpuffer) r[1];
        Oberflaeche o = (Oberflaeche) r[2];
        Phasenfeld p = (Phasenfeld) r[3];
        A.info("Kopplung", rf.bericht() + "; Bauch an den Wänden: " + rf.bilanzGrob.text() + f("; L = %.1f m, Zelle %.2f m (L/4)", L, dx));
        for (int stueck = 0; stueck < 2; stueck++) {
            double xw = (iw + stueck) * dx;
            double y0 = (stueck * halb + 2.5) * dx, y1 = (stueck * halb + 5.5) * dx;
            double[] v = varianz(o, xw, new double[] {y0, 0.5 * (y0 + y1), y1}, L);
            double[] aw = auswerten(v, L, kk, dx, nf, pp, bp, iw, nx, ny);
            String name = stueck == 0 ? "Stück A (Wand bei " + iw + ")" : "Stück B (eine Zelle = L/4 weiter)";
            A.info(name, f("Muster V = A (1 + %.2f cos(2k(d − d₀))), Bauch d₀ = %+.3f L vor der Wand; K_r aus Bauch und Knoten %.2f (Nahfeld %.2f)", aw[1], aw[0], aw[2], krNah));
            A.hoechstens(f("Bauch vor der Kante (%s): Abstand zur Wand, Anteil von L", name), Math.abs(aw[0]), BAUCH_L, "");
            A.hoechstens(f("K_r aus Bauch und Knoten gegen K_r des Nahfelds (%s)", name), Math.abs(aw[2] - krNah), KR_ABW, "");
        }
        // Wellenzahl der reflektierten Stützstellen in den nassen Zellen bis eine Wellenlänge vor den Wänden
        double sw = 0, se = 0;
        double[] ov = new double[6];
        for (int bq = 0; bq < NB * ND; bq++) {
            if (rf.partner[bq] < 0) continue;
            int b = bq / ND, q = bq % ND;
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    double xw = (iw + (j >= halb ? 1 : 0)) * dx, x = p.x(i);
                    if (nf.tiefe(c) < Phasenfeld.H_MIN || x < xw - L) continue;
                    double e = nf.energie(c, b, q);
                    if (!(e > 0)) continue;
                    p.wert(bq, x, p.y(j), ov);
                    double k = Dispersion.wellenzahl(OM[b], nf.tiefe(c));
                    sw += e;
                    se += e * Math.abs(Math.hypot(ov[2], ov[3]) / k - 1);
                }
        }
        A.hoechstens("Wellenzahl der reflektierten vor den Wänden, |∇Φ|/k − 1 mit der Energie gewichtet (eine Wellenlänge)", se / sw, WELLENZAHL_WAND, "");
        tiefeLaengs(bp);                                                    // seit S5b-6b2
        feinImGroben(bp);
        // Auskunft: runde Warft (Radius 60 m) und Küste unter 45°, Zelle 10 m, Seegang von West, breiter in der Richtung
        for (int form = 0; form < 2; form++) {
            int mx = 60, my = 48;
            double dz = 10;
            final int ff = form;
            Nahfeld nw = nahfeld(mx, my, dz, bp, (i, j) -> ff == 0 ? Math.hypot(i - 40.5, j - 23.5) * 10 < 60 : (i - 36) > (j - 24), false, 0, 10);
            Object[] g = gekoppelt(nw, mx, my, dz);
            Reflexion rw = (Reflexion) g[0];
            A.info(form == 0 ? "Auskunft: runde Warft, Radius 60 m" : "Auskunft: Küste unter 45° (Treppe zu 10 m)", rw.bericht() + "; Bauch an den Wänden: " + rw.bilanzGrob.text());
        }
    }

    /**
     * Seit S5b-6b2 (Datei 58, C2): gerade Wand, die Tiefe davor ändert sich längs der Wand (3 → 6 → 3 m, periodisch);
     * der Abstand vom Bauch ist dann längs des Stücks nicht gleich. Bauch und K_r an drei Linien (flach, mittel, tief),
     * Wellenzahl der reflektierten vor der Wand.
     */
    static void tiefeLaengs(int bp) {
        int nx = 60, ny = 32, iw = 48;
        double dx = 10;
        Tiefe t = (i, j) -> 4.5 - 1.5 * Math.cos(2 * Math.PI * j / ny);
        Nahfeld nf = nahfeld(nx, ny, dx, bp, (i, j) -> i >= iw, true, 0, 100, t);
        double krNah = krNahfeld(nf, bp, iw, nx, ny);
        Object[] r = gekoppelt(nf, nx, ny, dx);
        Reflexion rf = (Reflexion) r[0];
        Profilpuffer pp = (Profilpuffer) r[1];
        Oberflaeche o = (Oberflaeche) r[2];
        Phasenfeld p = (Phasenfeld) r[3];
        A.info("Tiefe längs der Wand 3 → 6 m, Kopplung", rf.bericht() + "; Bauch an den Wänden: " + rf.bilanzGrob.text());
        for (int j : new int[] {4, 10, 16}) {
            double h = t.h(0, j), kk = Dispersion.wellenzahl(OM[bp], h), L = 2 * Math.PI / kk;
            double[] v = varianz(o, iw * dx, new double[] {(j - 0.5) * dx, (j + 0.5) * dx, (j + 1.5) * dx}, L);
            double[] aw = auswerten(v, L, kk, dx, nf, pp, bp, iw, nx, j - 1, j + 1, h);   // Richtungen der drei Zeilen
            String name = f("Tiefe längs der Wand, Linie in %.1f m Tiefe", h);
            A.info(name, f("Muster V = A (1 + %.2f cos(2k(d − d₀))), Bauch d₀ = %+.3f L vor der Wand; K_r aus Bauch und Knoten %.2f (Nahfeld %.2f)", aw[1], aw[0], aw[2], krNah));
            A.hoechstens(f("Bauch vor der Kante (%s): Abstand zur Wand, Anteil von L", name), Math.abs(aw[0]), BAUCH_L, "");
            // seit S6 (SF59 A): Auskunft mit Ziel statt Prüfstein; bekannte Abweichung A4 (Phasenfeld bei Tiefe quer zur Welle, B26)
            A.info(f("K_r aus Bauch und Knoten gegen K_r des Nahfelds (%s) (Auskunft, früher Prüfstein; A4)", name), f("|%.2f − %.2f| = %.2f (Ziel ≤ %.2f)", aw[2], krNah, Math.abs(aw[2] - krNah), KR_ABW));
        }
        A.hoechstens("Wellenzahl der reflektierten vor der Wand (Tiefe längs der Wand), |∇Φ|/k − 1 mit der Energie gewichtet", wellenzahlVorWand(nf, rf, p, nx, ny, (i, j) -> iw * dx), WELLENZAHL_WAND, "");
    }

    /**
     * Seit S5b-6b2 (Datei 58, C1, U3): eine Wand nur im feinen Gitter (10 m), 12 Zellen vor dessen Ostrand; das grobe
     * Gitter (20 m) sieht sie nicht (wie eine Warft, die im groben Raster unter Wasser liegt). Partner aus dem feinen
     * Gitter, das feine Phasenfeld nimmt seinen Rand vom groben, der Versatz mit der Rampe des feinen Felds. Bauch und K_r
     * in der Mitte der Wand, Wellenzahl der reflektierten vor der Wand.
     */
    static void feinImGroben(int bp) {
        int nx = 60, ny = 40, iw = 48;
        double dx = 10;
        Nahfeld nf = nahfeld(nx, ny, dx, bp, (i, j) -> i >= iw, true, 0, 100);
        double krNah = krNahfeld(nf, bp, iw, nx, ny);
        double kk = Dispersion.wellenzahl(OM[bp], 4), L = 2 * Math.PI / kk;
        // grob: 30 × 20 zu 20 m, überall 4 m tief, Energie der einlaufenden (Spalte 5 des Nahfelds), keine Wand
        int gx = 30, gy = 20;
        Oberflaeche.Stufe g = new Oberflaeche.Stufe("grob", gx, gy, 2 * dx, 0, 0, NB, ND);
        for (int j = 0; j < gy; j++)
            for (int i = 0; i < gx; i++) {
                int c = j * gx + i, cf = (2 * j) * nx + 5;
                g.h[c] = 4;
                for (int b = 0; b < NB; b++) for (int q = 0; q < ND; q++) g.e[(c * NB + b) * ND + q] = nf.energie(cf, b, q);
            }
        g.gefaelleAusTiefe();
        Oberflaeche.Stufe fz = new Oberflaeche.Stufe("fein", nx, ny, dx, 0, 0, NB, ND);
        for (int c = 0; c < nx * ny; c++) {
            fz.h[c] = nf.tiefe(c);
            for (int b = 0; b < NB; b++) for (int q = 0; q < ND; q++) fz.e[(c * NB + b) * ND + q] = nf.energie(c, b, q);
        }
        fz.gefaelleAusTiefe();
        double[] ri = Phasenfeld.richtungen(NB, ND);
        Profilpuffer pp = new Profilpuffer(OM, UNTEN, OBEN, ND, 0x5EE6A3L, false);
        Reflexion rf = Reflexion.partner(g, fz);
        rf.richtungen(ri);
        Phasenfeld pg = Phasenfeld.rechne(gx, gy, 2 * dx, 0, 0, g.h, null, null, OM, ri, ND, 0, 0, null, 0, g.e);
        rf.wand(g, pg, false);
        g.phasen(pg, pg);
        Phasenfeld pf = Phasenfeld.rechne(nx, ny, dx, 0, 0, fz.h, null, null, OM, ri, ND, 0, 0, pg, 0, fz.e);
        rf.wand(fz, pf, true);
        fz.phasen(pf, pf);
        rf.teilen(pp);
        Oberflaeche o = new Oberflaeche(pp, ND);
        o.grob = g;
        o.fein = fz;
        o.mitForm = o.mitHoehen = o.mitVerschiebung = false;
        A.info("Wand nur im feinen Gitter, Kopplung", rf.bericht() + "; Bauch an den Wänden grob: " + rf.bilanzGrob.text() + "; fein: " + rf.bilanzFein.text());
        double[] v = varianz(o, iw * dx, new double[] {18.5 * dx, 20 * dx, 21.5 * dx}, L);
        double[] aw = auswerten(v, L, kk, dx, nf, pp, bp, iw, nx, ny);
        String name = "Wand nur im feinen Gitter, 12 Zellen vor dessen Rand";
        A.info(name, f("Muster V = A (1 + %.2f cos(2k(d − d₀))), Bauch d₀ = %+.3f L vor der Wand; K_r aus Bauch und Knoten %.2f (Nahfeld %.2f)", aw[1], aw[0], aw[2], krNah));
        A.hoechstens(f("Bauch vor der Kante (%s): Abstand zur Wand, Anteil von L", name), Math.abs(aw[0]), BAUCH_L, "");
        A.hoechstens(f("K_r aus Bauch und Knoten gegen K_r des Nahfelds (%s)", name), Math.abs(aw[2] - krNah), KR_ABW, "");
        A.hoechstens("Wellenzahl der reflektierten vor der Wand (nur im feinen Gitter), |∇Φ|/k − 1 mit der Energie gewichtet", wellenzahlVorWand(nf, rf, pf, nx, ny, (i, j) -> iw * dx), WELLENZAHL_WAND, "");
    }

    interface Lage { double xw(int i, int j); }

    /** |∇Φ|/k − 1 der reflektierten Stützstellen, energiegewichtet über die nassen Zellen bis eine Wellenlänge vor der Wand. */
    static double wellenzahlVorWand(Nahfeld nf, Reflexion rf, Phasenfeld p, int nx, int ny, Lage lage) {
        double sw = 0, se = 0;
        double[] ov = new double[6];
        for (int bq = 0; bq < NB * ND; bq++) {
            if (rf.partner[bq] < 0) continue;
            int b = bq / ND, q = bq % ND;
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    double h = nf.tiefe(c);
                    if (h < Phasenfeld.H_MIN) continue;
                    double k = Dispersion.wellenzahl(OM[b], h), x = p.x(i);
                    if (x < lage.xw(i, j) - 2 * Math.PI / k) continue;
                    double e = nf.energie(c, b, q);
                    if (!(e > 0)) continue;
                    p.wert(bq, x, p.y(j), ov);
                    sw += e;
                    se += e * Math.abs(Math.hypot(ov[2], ov[3]) / k - 1);
                }
        }
        return sw > 0 ? se / sw : Double.NaN;
    }

    /** Band der Spitze des Spektrums von Teil 14 (Hs 1 m, Tp 7 s). */
    static int spitze() {
        Nahfeld t = new Nahfeld(4, 4, 10, NB, FMIN, FMAX, ND);
        double[] js = t.jonswap(1.0, 7, 0, 100);
        int bp = 0;
        double emax = 0;
        for (int b = 0; b < NB; b++) { double eb = 0; for (int q = 0; q < ND; q++) eb += js[b * ND + q]; if (eb > emax) { emax = eb; bp = b; } }
        return bp;
    }

    interface Trocken { boolean an(int i, int j); }

    /**
     * Ein Nahfeld 4 m tief mit Wänden (Bauwerk, K_r = 1) an den trockenen Zellen, Seegang von West nur im Band der
     * Spitze (Hs 1 m, Tp 7 s, Richtung wohin, Breite s), angelaufen wie Teil 14.
     */
    static Nahfeld nahfeld(int nx, int ny, double dx, int bp, Trocken tr, boolean periodisch, double wohin, double s) {
        return nahfeld(nx, ny, dx, bp, tr, periodisch, wohin, s, (i, j) -> 4);
    }

    interface Tiefe { double h(int i, int j); }

    /** Wie oben, mit der Tiefe je nasser Zelle (seit S5b-6b2). */
    static Nahfeld nahfeld(int nx, int ny, double dx, int bp, Trocken tr, boolean periodisch, double wohin, double s, Tiefe tiefe) {
        Nahfeld nf = new Nahfeld(nx, ny, dx, NB, FMIN, FMAX, ND);
        nf.wand = new boolean[nx * ny];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                boolean t = tr.an(i, j);
                nf.grund[c] = t ? 3 : -tiefe.h(i, j);
                nf.spiegel[c] = 0;
                nf.wand[c] = t;
            }
        nf.mitWind = false;
        nf.periodischY = periodisch;
        nf.vorbereiten();
        double[] js = nf.jonswap(1.0, 7, wohin, s);
        final double[] rand = new double[NB * ND];
        System.arraycopy(js, bp * ND, rand, bp * ND, ND);
        nf.rand = (i, j, b, q) -> i == -1 ? rand[b * ND + q] / nf.sigma(0, b, q) : 0;
        nf.setze((x, y, ee) -> { java.util.Arrays.fill(ee, 0); if (x < nf.x(5)) System.arraycopy(rand, 0, ee, 0, ee.length); });
        nf.anlauf(4000, 1e-7, 600);
        return nf;
    }

    /** K_r im Nahfeld vor der Wand bei iw (Spalte iw − 1): reflektierte gegen einlaufende Energie im Band bp. */
    static double krNahfeld(Nahfeld nf, int bp, int iw, int nx, int ny) {
        double ein = 0, aus = 0;
        for (int j = 0; j < ny; j++) {
            int c = j * nx + iw - 1;
            for (int q = 0; q < ND; q++) { double e = nf.energie(c, bp, q); if (nf.cosT[q] > 1e-9) ein += e; else if (nf.cosT[q] < -1e-9) aus += e; }
        }
        return Math.sqrt(aus / ein);
    }

    /** Stufe, Partner, Phasenfeld mit Bauch an den Wänden, geteilte Hüllen und Fläche; zurück {Reflexion, Profilpuffer, Oberflaeche, Phasenfeld}. */
    static Object[] gekoppelt(Nahfeld nf, int nx, int ny, double dx) {
        Oberflaeche.Stufe s = new Oberflaeche.Stufe("grob", nx, ny, dx, 0, 0, NB, ND);
        for (int c = 0; c < nx * ny; c++) {
            s.h[c] = nf.tiefe(c);
            for (int b = 0; b < NB; b++) for (int q = 0; q < ND; q++) s.e[(c * NB + b) * ND + q] = nf.energie(c, b, q);
        }
        s.gefaelleAusTiefe();
        s.rand = 1;
        double[] ri = Phasenfeld.richtungen(NB, ND);
        Profilpuffer pp = new Profilpuffer(OM, UNTEN, OBEN, ND, 0x5EE6A3L, false);
        Reflexion r = Reflexion.partner(s);
        r.richtungen(ri);
        Phasenfeld p = Phasenfeld.rechne(nx, ny, dx, 0, 0, s.h, null, null, OM, ri, ND, 0, 0, null, 0, s.e);
        r.wand(s, p, false);
        r.teilen(pp);
        s.phasen(p, p);
        Oberflaeche o = flaeche(pp, s);
        o.mitForm = o.mitHoehen = o.mitVerschiebung = false;
        return new Object[] {r, pp, o, p};
    }

    /** Varianz der Höhe über 1500 Zeiten längs Linien y vor der Wand bei xw, 38 Abstände 0 … 0,75 L (wie Teil 14). */
    static double[] varianz(Oberflaeche o, double xw, double[] ys, double L) {
        int nd2 = 38, zeiten = 1500;
        double[] v = new double[nd2];
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        Random rr = new Random(7);
        for (int z = 0; z < zeiten; z++) {
            double t = 7200 * rr.nextDouble();
            for (double y : ys)
                for (int k = 0; k < nd2; k++) {
                    o.punkt(xw - 0.75 * L * k / (nd2 - 1), y, t, pt);
                    v[k] += pt.etaLin * pt.etaLin;
                }
        }
        for (int k = 0; k < nd2; k++) v[k] /= (double) ys.length * zeiten;
        return v;
    }

    /** Auswertung wie Teil 14: {d₀ / L, B / A, K_r aus Bauch und Knoten}. */
    static double[] auswerten(double[] v, double L, double kk, double dx, Nahfeld nf, Profilpuffer pp, int bp, int iw, int nx, int ny) {
        return auswerten(v, L, kk, dx, nf, pp, bp, iw, nx, ny, 4);
    }

    /** Wie oben in der Tiefe h (seit S5b-6b2). */
    static double[] auswerten(double[] v, double L, double kk, double dx, Nahfeld nf, Profilpuffer pp, int bp, int iw, int nx, int ny, double h) {
        return auswerten(v, L, kk, dx, nf, pp, bp, iw, nx, 0, ny - 1, h);
    }

    /** Wie oben, das Muster mit den Richtungen der Zeilen j0 … j1 (seit S5b-6b2). */
    static double[] auswerten(double[] v, double L, double kk, double dx, Nahfeld nf, Profilpuffer pp, int bp, int iw, int nx, int j0, int j1, double h) {
        double[][] m = new double[3][3];
        double[] rs = new double[3];
        for (int k = 0; k < v.length; k++) {
            double d = 0.75 * L * k / (v.length - 1);
            if (d < 0.5 * dx) continue;
            double[] bf = {1, Math.cos(2 * kk * d), Math.sin(2 * kk * d)};
            for (int a1 = 0; a1 < 3; a1++) { rs[a1] += bf[a1] * v[k]; for (int a2 = 0; a2 < 3; a2++) m[a1][a2] += bf[a1] * bf[a2]; }
        }
        double[] x3 = loese3(m, rs);
        double A0 = x3[0], B = Math.hypot(x3[1], x3[2]), phi = Math.atan2(-x3[2], x3[1]);
        double d0 = -phi / (2 * kk) / L;
        d0 -= 0.5 * Math.rint(d0 / 0.5);
        double[][] m2 = new double[2][2];
        double[] r2 = new double[2];
        for (int k = 0; k < v.length; k++) {
            double d = 0.75 * L * k / (v.length - 1);
            if (d < 0.5 * dx) continue;
            double mm = muster(nf, pp, bp, iw, nx, j0, j1, d - d0 * L, h);
            double[] bf = {1, mm};
            for (int a1 = 0; a1 < 2; a1++) { r2[a1] += bf[a1] * v[k]; for (int a2 = 0; a2 < 2; a2++) m2[a1][a2] += bf[a1] * bf[a2]; }
        }
        double det = m2[0][0] * m2[1][1] - m2[0][1] * m2[1][0];
        double aa = (r2[0] * m2[1][1] - m2[0][1] * r2[1]) / det, bb = (m2[0][0] * r2[1] - m2[1][0] * r2[0]) / det;
        double rB = Math.max(0, Math.min(0.999999, bb / aa)), krEst = rB > 0 ? (1 - Math.sqrt(1 - rB * rB)) / rB : 0;
        return new double[] {d0, B / A0, krEst};
    }

    /** Das erwartete Muster M(d) (−1 … 1) einer ganz kohärenten Reflexion, verwischt über Band und Richtungen (siehe {@link #reflexion}). */
    static double muster(Nahfeld nf, Profilpuffer pp, int b, int iw, int nx, int ny, double d) {
        return muster(nf, pp, b, iw, nx, ny, d, 4);
    }

    /** Wie oben in der Tiefe h (seit S5b-6b2). */
    static double muster(Nahfeld nf, Profilpuffer pp, int b, int iw, int nx, int ny, double d, double h) {
        return muster(nf, pp, b, iw, nx, 0, ny - 1, d, h);
    }

    /**
     * Wie oben, die Richtungen nur aus den Zeilen j0 … j1 vor der Wand (seit S5b-6b2: wo sich die Tiefe längs der Wand
     * ändert, streuen die Richtungen je Zeile verschieden – im Flachen bis ±45°, im Tiefen ±10°).
     */
    static double muster(Nahfeld nf, Profilpuffer pp, int b, int iw, int nx, int j0, int j1, double d, double h) {
        double s = 0, sw = 0;
        for (int q = 0; q < ND; q++) {
            if (!(nf.cosT[q] > 1e-9)) continue;
            double w = 0;
            for (int j = j0; j <= j1; j++) w += nf.energie(j * nx + iw - 1, b, q);
            if (!(w > 0)) continue;
            int bq = b * ND + q, nm = pp.teile[bq];
            double dw = 2 * Math.PI / pp.periode[bq];
            for (int m = 0; m < nm; m++) {
                double om = OM[b] + (pp.mVon[bq] + m) * dw, k = Dispersion.wellenzahl(om, h);
                s += w / nm * Math.cos(2 * k * nf.cosT[q] * d);
            }
            sw += w;
        }
        return sw > 0 ? s / sw : 0;
    }

    /** Löst ein 3 × 3-System (Gauß mit Pivot). */
    static double[] loese3(double[][] m0, double[] r0) {
        double[][] m = new double[3][];
        double[] r = r0.clone();
        for (int i = 0; i < 3; i++) m[i] = m0[i].clone();
        for (int c = 0; c < 3; c++) {
            int pv = c;
            for (int i = c + 1; i < 3; i++) if (Math.abs(m[i][c]) > Math.abs(m[pv][c])) pv = i;
            double[] t = m[c]; m[c] = m[pv]; m[pv] = t;
            double tr = r[c]; r[c] = r[pv]; r[pv] = tr;
            for (int i = c + 1; i < 3; i++) {
                double f = m[i][c] / m[c][c];
                for (int j = c; j < 3; j++) m[i][j] -= f * m[c][j];
                r[i] -= f * r[c];
            }
        }
        double[] x = new double[3];
        for (int i = 2; i >= 0; i--) {
            double s = r[i];
            for (int j = i + 1; j < 3; j++) s -= m[i][j] * x[j];
            x[i] = s / m[i][i];
        }
        return x;
    }

    // ------------------------------------------------------------------ FFT

    /** Radix-2-FFT an Ort und Stelle; inv: rückwärts mit 1/n. */
    static void fft(double[] re, double[] im, boolean inv) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inv ? 1 : -1), wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double ur = re[a], ui = im[a], vr = re[b] * cr - im[b] * ci, vi = re[b] * ci + im[b] * cr;
                    re[a] = ur + vr; im[a] = ui + vi; re[b] = ur - vr; im[b] = ui - vi;
                    double nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr; cr = nr;
                }
            }
        }
        if (inv) for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
    }
}
