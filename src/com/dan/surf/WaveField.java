package com.dan.surf;

import java.util.Locale;

/**
 * Das Wellenfeld über dem Grund (seit Sylt Phase 5, F7 A): stationärer Seegang auf einem Gitter vor der Küste, mit
 * Shoaling, Refraktion und Brechen nach Battjes und Janssen (1978), dazu der Wasserstau (Set-up) aus den
 * Strahlungsspannungen und die mittlere Strömung: Längsstrom nach Longuet-Higgins (1970), Rückstrom unter den Brechern
 * und in den Rinnen aus der Erhaltung der Masse.
 * <p>
 * <b>Gitter.</b> Die Achse u zeigt zur Küste ({@link #shorewardDeg}), v längs der Küste (u um 90° nach links gedreht);
 * die Zeile i = 0 ist der Rand zur See, dort kommt die See aus {@link Surf} herein (H_s, T_p, Richtung über tiefem
 * Wasser, mit Shoaling und Snell bis zur Tiefe am Rand).
 * <p>
 * <b>Seegang</b> (eine Richtung, eine Periode, Wellenhöhen nach Rayleigh, wie Battjes und Janssen): von Zeile zu Zeile
 * zur Küste gerechnet. Die Wellenzahl längs der Küste k_v folgt aus der Wirbelfreiheit des Wellenzahlvektors
 * (∂k_v/∂u = ∂k_u/∂v); auf geraden Tiefenlinien bleibt k_v fest, das ist das Gesetz von Snell. Der Energiefluss
 * F = E·c_g·cos θ nimmt mit dem Fluss längs der Küste und mit der Dissipation ab:
 * ∂F/∂u + ∂(E·c_g·sin θ)/∂v = −D. Ohne Dissipation bleibt E·c_g erhalten (Green: H ∝ h^−¼ im Flachen).
 * D = α/4 · ρ·g·f_p·Q_b·H_m², H_m = γ·h, Q_b aus (1 − Q_b)/(−ln Q_b) = (H_rms/H_m)², γ = 0,5 + 0,4·tanh(33·s₀)
 * nach Battjes und Stive (1985).
 * <p>
 * <b>Set-up:</b> ∂η/∂u = −∂S_uu/∂u / (ρ·g·(h + η)) je Querprofil; S_uu = E·(n·(1 + cos²θ) − ½).
 * <b>Längsstrom:</b> die Kraft der brechenden Wellen D·sin θ/c und das Gefälle des Set-up längs der Küste gegen die
 * Reibung am Grund τ = ρ·c_f·(2/π)·u_b·V (linearisiert, u_b Geschwindigkeit der Wellen am Grund), mit seitlicher
 * Vermischung. <b>Rückstrom:</b> die Wellen tragen Wasser zur Küste (M = E/(ρ·c)); was sie bringen, fließt am Grund
 * zurück (Unterstrom) und, wo der Längsstrom zusammenläuft, in den Rinnen (Rippströmung). Die Querströmung folgt aus
 * der Masse, vom Ufer seewärts aufsummiert.
 * <p>
 * Gerechnet auf dem Prozessor, je Zelle einige Mikrosekunden; {@link #update} rechnet im eigenen Faden neu, wenn sich
 * See, Pegel oder Grund ändern, und tauscht das Ergebnis danach aus (Abfragen lesen immer ein ganzes Ergebnis).
 */
public final class WaveField {
    public static final double RHO = 1025, G = Surf.G;

    public final Surf surf;
    /** Rand zur See: Ecke (x0, z0) bei i = 0, j = 0; Blick zur Küste (Grad von Nord); Länge quer und längs; Zelle (m). */
    public final double x0, z0, shorewardDeg, across, along, cell;
    public final int nu, nv;
    /** Einheitsvektoren: zur Küste (sx, sz) und längs (ax, az). */
    public final double sx, sz, ax, az;

    /** Reibungsbeiwert am Grund für die Strömung [G, 0,005 auf Sand, abgestimmt an Komar und Inman 1970; Thornton und Guza 1986: 0,006 bis 0,015]. */
    public double cf = 0.005;
    /**
     * Wie stark das Gefälle des Set-up längs der Küste die Strömung treibt (0..1) [G]: das Set-up kommt je Querprofil
     * für sich; in Wahrheit gleicht der Wasserspiegel in der Fläche aus, darum nur ein Teil, und längs geglättet über
     * {@link #feederSmooth} m.
     */
    public double feeder = 0.2, feederSmooth = 60;
    /**
     * Glättung der Wellenzahl längs je Zeile (gegen Schwingen von Gitterpunkt zu Gitterpunkt hinter Riffen und Rinnen)
     * und wie weit sich eine Rippströmung jenseits der Brandung je Zeile seitlich ausbreitet (Anteil) [G].
     */
    public double kvSmooth = 0.2, ripSpread = 0.4;
    /** Breite, über die die Zufuhr zur Rippströmung verteilt wird (m), und größte Strömung (m/s) [G: Rippströme bis rund 2 m/s]. */
    public double ripSmooth = 60, maxCurrent = 2.0;
    /** Seitliche Vermischung des Längsstroms (Anteil je Glättung), Zahl der Glättungen quer und längs, dann nur längs [G]. */
    public double mixing = 0.25;
    public int mixingPasses = 4, alongPasses = 10;
    /** Dissipation nach Battjes und Janssen: α (1) und γ (NaN: nach Battjes und Stive aus der Steilheit). */
    public double alpha = 1, gammaBJ = Double.NaN;
    /**
     * Seit Sylt Phase 7a: seitliche Glättung des Energieflusses je Zeile (0..0,5), genähert für Beugung und die Streuung
     * der Richtungen einer echten See [G]. Ohne sie bündeln Riffe und Rinnen auf echtem Grund die Energie zu Brennlinien
     * und lassen dahinter fast keine Wellen. 0 = aus (wie bis Phase 7).
     */
    public double spread = 0;
    /** Unter dieser Tiefe (m) ist eine Zelle trocken. */
    public double dry = 0.05;

    /** Ein ganzes Ergebnis (unveränderlich nach dem Rechnen). */
    public static final class Result {
        public final int nu, nv;
        /** Je Zelle: Tiefe mit Set-up, H_rms, Richtung θ (rad, zur Achse u), Anteil Brecher Q_b, Dissipation (W/m²), Set-up η, Strömung u (zur Küste), v (längs). */
        public final float[] depth, hrms, theta, qb, diss, eta, cu, cv;
        public double level, hs0, tp, fromDeg, gamma, ms;
        public int version;
        Result(int nu, int nv) {
            this.nu = nu; this.nv = nv;
            int n = nu * nv;
            depth = new float[n]; hrms = new float[n]; theta = new float[n]; qb = new float[n]; diss = new float[n];
            eta = new float[n]; cu = new float[n]; cv = new float[n];
        }
    }

    private volatile Result result;
    private volatile Thread worker;
    private volatile String key = "";
    private int version;
    /** Wird von außen erhöht, wenn sich der Grund geändert hat (Abbruch, Sand, Rinnen nach Sturm). */
    public volatile int bedVersion;

    public WaveField(Surf surf, double x0, double z0, double shorewardDeg, double across, double along, double cell) {
        this.surf = surf;
        this.x0 = x0;
        this.z0 = z0;
        this.shorewardDeg = shorewardDeg;
        this.across = across;
        this.along = along;
        this.cell = cell;
        this.nu = (int) Math.round(across / cell) + 1;
        this.nv = (int) Math.round(along / cell) + 1;
        double a = Math.toRadians(shorewardDeg);
        sx = Math.sin(a);
        sz = -Math.cos(a);
        ax = -sz;
        az = sx;
    }

    public double x(int i, int j) { return x0 + i * cell * sx + j * cell * ax; }
    public double z(int i, int j) { return z0 + i * cell * sz + j * cell * az; }

    /** Das letzte fertige Ergebnis (null vor dem ersten Rechnen). */
    public Result result() { return result; }

    // ------------------------------------------------------------------ Rechnen

    /** Rechnet jetzt (im aufrufenden Faden) für die Szenenzeit t und gibt das Ergebnis. */
    public Result compute(double t) {
        Result r = solve(t);
        result = r;
        key = keyOf(t);
        return r;
    }

    /**
     * Rechnet im eigenen Faden neu, wenn sich See, Pegel (mehr als 2 cm) oder Grund ({@link #bedVersion}) geändert haben
     * und gerade nichts läuft. Gibt true, wenn eine Rechnung angestoßen wurde.
     */
    public boolean update(double t) {
        String k = keyOf(t);
        if (k.equals(key) || worker != null) return false;
        key = k;
        Thread th = new Thread(() -> {
            try { result = solve(t); } finally { worker = null; }
        }, "surf-wellenfeld");
        th.setDaemon(true);
        worker = th;
        th.start();
        return true;
    }

    /** Ob gerade im eigenen Faden gerechnet wird. */
    public boolean busy() { return worker != null; }

    private String keyOf(double t) {
        return String.format(Locale.ROOT, "%.3f %.3f %.2f %.2f %d", surf.hs(), surf.tp(), surf.fromDeg(), Math.round(surf.level.level(t) / 0.02) * 0.02, bedVersion);
    }

    /** γ nach Battjes und Stive (1985) aus der Steilheit draußen s₀ = H_rms0 / L₀. */
    public double gammaFor(double hrms0, double tp) {
        if (!Double.isNaN(gammaBJ)) return gammaBJ;
        double s0 = hrms0 / Surf.deepLength(tp);
        return 0.5 + 0.4 * Math.tanh(33 * s0);
    }

    /** Anteil gebrochener Wellen Q_b aus b = H_rms / H_m: (1 − Q)/(−ln Q) = b² (Battjes und Janssen). */
    public static double qb(double b) {
        if (!(b > 0)) return 0;
        if (b >= 1) return 1;
        double b2 = b * b;
        // f(Q) = 1 − Q + b²·ln Q steigt von −∞ (Q → 0) bis 0 bei Q = 1 und hat dazwischen die Nullstelle; Halbierung in ln Q
        double lo = -60, hi = -1e-12;
        for (int it = 0; it < 48; it++) {
            double m = 0.5 * (lo + hi), q = Math.exp(m);
            double f = 1 - q + b2 * m;
            if (f > 0) hi = m; else lo = m;
        }
        return Math.exp(0.5 * (lo + hi));
    }

    private Result solve(double t) {
        long t0 = System.nanoTime();
        Result r = new Result(nu, nv);
        double level = surf.level.level(t), tp = surf.tp(), w = 2 * Math.PI / tp;
        double hrms0 = surf.hs() / Math.sqrt(2);
        // Richtung draußen relativ zur Achse u
        double f = Math.toRadians(surf.fromDeg());
        double tx = -Math.sin(f), tz = Math.cos(f);      // Laufrichtung (Welt)
        double th0 = Math.atan2(tx * ax + tz * az, tx * sx + tz * sz);
        double gamma = gammaFor(hrms0, tp);
        r.level = level; r.hs0 = surf.hs(); r.tp = tp; r.fromDeg = surf.fromDeg(); r.gamma = gamma;
        double[] bed = new double[nu * nv];
        for (int i = 0; i < nu; i++)
            for (int j = 0; j < nv; j++) bed[i * nv + j] = surf.bed.height(x(i, j), z(i, j));
        double[] eta = new double[nu * nv];
        // zwei Durchgänge: Wellen, Set-up, Wellen mit der Tiefe aus Pegel + Set-up, Set-up
        for (int pass = 0; pass < 2; pass++) {
            waves(r, bed, eta, level, tp, w, hrms0, th0, gamma);
            setup(r, bed, eta, level, w);
        }
        currents(r, w);
        r.ms = (System.nanoTime() - t0) / 1e6;
        r.version = ++version;
        return r;
    }

    /** Seegang von der See zur Küste. */
    private void waves(Result r, double[] bed, double[] eta, double level, double tp, double w, double hrms0, double th0, double gamma) {
        int n = nu * nv;
        double[] k = kArr = new double[n], kv = new double[n], cg = new double[n], c = new double[n], F = new double[n];
        boolean incoming = Math.abs(th0) < Math.PI / 2 - 1e-6;
        double kDeep = w * w / G, cDeep = G / w, cgDeep = cDeep / 2;
        double fp = 1 / tp;
        for (int i = 0; i < nu; i++) {
            // Tiefe, Wellenzahl, Geschwindigkeiten der Zeile
            for (int j = 0; j < nv; j++) {
                int q = i * nv + j;
                double h = level - bed[q] + eta[q];
                r.depth[q] = (float) Math.max(0, h);
                if (h > dry) {
                    k[q] = Surf.wavenumber(tp, h);
                    c[q] = w / k[q];
                    double kh2 = 2 * k[q] * h;
                    cg[q] = c[q] * (kh2 > 600 ? 0.5 : 0.5 * (1 + kh2 / Math.sinh(kh2)));
                }
            }
            if (i == 0) {
                for (int j = 0; j < nv; j++) {
                    int q = j;
                    if (k[q] == 0 || !incoming) continue;
                    // von tief bis zum Rand: Snell (k_v fest) und Energiefluss (ohne Brechen davor)
                    kv[q] = kDeep * Math.sin(th0);
                    double sinT = Math.max(-0.999, Math.min(0.999, kv[q] / k[q])), cosT = Math.sqrt(1 - sinT * sinT);
                    double e0 = RHO * G * hrms0 * hrms0 / 8;
                    F[q] = e0 * cgDeep * Math.cos(th0);
                    double e = F[q] / (cg[q] * cosT);
                    double hm = gamma * r.depth[q], hr = Math.sqrt(8 * e / (RHO * G));
                    if (hr > hm) { hr = hm; e = RHO * G * hr * hr / 8; F[q] = e * cg[q] * cosT; }
                    r.hrms[q] = (float) hr;
                    r.theta[q] = (float) Math.asin(sinT);
                    double b = hr / Math.max(1e-6, hm), qbv = qb(b);
                    r.qb[q] = (float) qbv;
                    r.diss[q] = (float) (alpha / 4 * RHO * G * fp * qbv * hm * hm);
                }
                continue;
            }
            int p = (i - 1) * nv;     // vorige Zeile
            // Wellenzahl längs der Küste: ∂k_v/∂u = ∂k_u/∂v (Wirbelfreiheit), leicht geglättet gegen Schwingen
            for (int j = 0; j < nv; j++) {
                int q = i * nv + j, qp = p + j;
                if (k[q] == 0 || k[qp] == 0) { kv[q] = 0; continue; }
                int jm = Math.max(0, j - 1), jp = Math.min(nv - 1, j + 1);
                double kuM = ku(k[p + jm], kv[p + jm]), kuP = ku(k[p + jp], kv[p + jp]);
                double dkudv = (k[p + jm] > 0 && k[p + jp] > 0) ? (kuP - kuM) / ((jp - jm) * cell) : 0;
                double lap = (k[p + jm] > 0 ? kv[p + jm] : kv[qp]) - 2 * kv[qp] + (k[p + jp] > 0 ? kv[p + jp] : kv[qp]);
                double v = kv[qp] + cell * dkudv + kvSmooth * lap;
                kv[q] = Math.max(-0.95 * k[q], Math.min(0.95 * k[q], v));
            }
            // Energiefluss: Transport längs der Küste (gegen den Wind, in Teilschritten), dann Dissipation
            double[] Fp = new double[nv], G2 = new double[nv];
            double cfl = 0;
            for (int j = 0; j < nv; j++) {
                int qp = p + j;
                Fp[j] = F[qp];
                if (k[qp] > 0) {
                    double sinT = kv[qp] / k[qp], cosT = Math.sqrt(1 - sinT * sinT);
                    G2[j] = sinT / cosT;    // F·tan θ ist der Fluss längs
                    cfl = Math.max(cfl, Math.abs(G2[j]));
                }
            }
            int sub = Math.max(1, (int) Math.ceil(cfl));
            for (int s = 0; s < sub; s++) {
                double[] nf = Fp.clone();
                for (int j = 0; j < nv - 1; j++) {
                    // Fluss über die Kante j+½ (gegen den Wind)
                    double tj = 0.5 * (G2[j] + G2[j + 1]);
                    double fr = tj >= 0 ? Fp[j] * tj : Fp[j + 1] * tj;
                    nf[j] -= fr / sub;
                    nf[j + 1] += fr / sub;
                }
                // seitliche Ränder offen: draußen liegt dieselbe See wie am Rand (kein Schatten, kein Stau)
                nf[0] += Fp[0] * G2[0] / sub;
                nf[nv - 1] -= Fp[nv - 1] * G2[nv - 1] / sub;
                Fp = nf;
            }
            // seit Sylt Phase 7a: seitliche Glättung der Energie (Beugung und Richtungsstreuung der See, genähert) [G]
            if (spread > 0) {
                double[] nf = Fp.clone();
                for (int j = 0; j < nv; j++) {
                    double a = j > 0 ? Fp[j - 1] : Fp[j], b = j < nv - 1 ? Fp[j + 1] : Fp[j];
                    nf[j] = Fp[j] + spread * (a - 2 * Fp[j] + b);
                }
                Fp = nf;
            }
            for (int j = 0; j < nv; j++) {
                int q = i * nv + j, qp = p + j;
                if (k[q] == 0 || Fp[j] <= 0 || !incoming) { F[q] = 0; r.hrms[q] = 0; r.qb[q] = 0; r.diss[q] = 0; continue; }
                double sinT = kv[q] / k[q], cosT = Math.sqrt(1 - sinT * sinT);
                double hm = gamma * r.depth[q];
                double dPrev = r.diss[qp];
                // Prädiktor: Dissipation der vorigen Zeile, Korrektor: Mittel mit der neuen
                double fPred = Math.max(0, Fp[j] - cell * dPrev);
                double ePred = fPred / (cg[q] * cosT);
                double hPred = Math.sqrt(8 * ePred / (RHO * G));
                double dNew = alpha / 4 * RHO * G * fp * qb(hPred / Math.max(1e-6, hm)) * hm * hm;
                double fNew = Math.max(0, Fp[j] - cell * 0.5 * (dPrev + dNew));
                double e = fNew / (cg[q] * cosT);
                double hr = Math.sqrt(8 * e / (RHO * G));
                if (hr > hm) { hr = hm; e = RHO * G * hr * hr / 8; fNew = e * cg[q] * cosT; }
                F[q] = fNew;
                r.hrms[q] = (float) hr;
                r.theta[q] = (float) Math.asin(sinT);
                double qbv = qb(hr / Math.max(1e-6, hm));
                r.qb[q] = (float) qbv;
                r.diss[q] = (float) (alpha / 4 * RHO * G * fp * qbv * hm * hm);
            }
        }
    }

    /** Wellenzahl je Zelle aus dem letzten Durchgang der Wellen (für Set-up und Strömung, im rechnenden Faden). */
    private double[] kArr;

    static double ku(double k, double kv) { return k > 0 ? Math.sqrt(Math.max(0, k * k - kv * kv)) : 0; }

    /** Set-up je Querprofil aus der Strahlungsspannung S_uu. */
    private void setup(Result r, double[] bed, double[] eta, double level, double w) {
        for (int j = 0; j < nv; j++) {
            // am Rand zur See schon der Set-down gegenüber tiefem Wasser (Longuet-Higgins und Stewart 1964)
            double prevS = Double.NaN, e0 = 0;
            double h0 = r.depth[j];
            if (h0 > dry && r.hrms[j] > 0 && kArr[j] > 0) e0 = -r.hrms[j] * r.hrms[j] * kArr[j] / (8 * Math.sinh(Math.min(300, 2 * kArr[j] * h0)));
            for (int i = 0; i < nu; i++) {
                int q = i * nv + j;
                double h = level - bed[q] + eta[q];
                if (h <= dry) {
                    // hinter der Wasserlinie: der Spiegel bleibt (Auflauf kommt mit Phase 7)
                    eta[q] = e0;
                    r.eta[q] = (float) e0;
                    continue;
                }
                double s = suu(r, q, w);
                if (!Double.isNaN(prevS)) {
                    double dS = s - prevS;
                    e0 += -dS / (RHO * G * Math.max(dry, h));
                }
                prevS = s;
                eta[q] = e0;
                r.eta[q] = (float) e0;
            }
        }
    }

    /** S_uu am Ort (N/m). */
    private double suu(Result r, int q, double w) {
        double hr = r.hrms[q], h = r.depth[q];
        if (hr <= 0 || h <= dry) return 0;
        double e = RHO * G * hr * hr / 8, k = kArr[q] > 0 ? kArr[q] : Surf.wavenumber(2 * Math.PI / w, h), kh2 = 2 * k * h;
        double n = kh2 > 600 ? 0.5 : 0.5 * (1 + kh2 / Math.sinh(kh2)), ct = Math.cos(r.theta[q]);
        return e * (n * (1 + ct * ct) - 0.5);
    }

    /** Längsstrom und Querströmung. */
    private void currents(Result r, double w) {
        int n = nu * nv;
        double[] V = new double[n], c = new double[n], ub = new double[n];
        for (int q = 0; q < n; q++) {
            double h = r.depth[q];
            if (h <= dry || r.hrms[q] <= 0) continue;
            double k = kArr[q] > 0 ? kArr[q] : Surf.wavenumber(2 * Math.PI / w, h);
            c[q] = w / k;
            ub[q] = w * r.hrms[q] / (2 * Math.sinh(Math.min(300, k * h)));
        }
        // Set-up längs geglättet (gleitendes Mittel über feederSmooth)
        int sw = Math.max(1, (int) Math.round(feederSmooth / cell / 2));
        double[] etaS = new double[n];
        for (int i = 0; i < nu; i++) {
            double acc = 0;
            int cnt = 0;
            for (int j = -sw; j < nv + sw; j++) {
                int jAdd = j + sw, jDel = j - sw - 1;
                if (jAdd < nv) { acc += r.eta[i * nv + jAdd]; cnt++; }
                if (jDel >= 0) { acc -= r.eta[i * nv + jDel]; cnt--; }
                if (j >= 0 && j < nv) etaS[i * nv + j] = acc / Math.max(1, cnt);
            }
        }
        // Längsstrom: Kraft der Brecher (D·sin θ/c) und Gefälle des Set-up längs gegen die Reibung
        for (int i = 0; i < nu; i++)
            for (int j = 0; j < nv; j++) {
                int q = i * nv + j;
                double h = r.depth[q];
                if (h <= dry) continue;
                double force = c[q] > 0 ? r.diss[q] * Math.sin(r.theta[q]) / c[q] : 0;
                int jm = Math.max(0, j - sw), jp = Math.min(nv - 1, j + sw);
                double detadv = (etaS[i * nv + jp] - etaS[i * nv + jm]) / ((jp - jm) * cell);
                force -= feeder * RHO * G * h * detadv;
                // Reibung am Grund: linear mit den Wellen (2/π·u_b), dazu quadratisch mit der Strömung selbst
                double a = (2 / Math.PI) * ub[q], fa = Math.abs(force) / (RHO * cf);
                double vAbs = 0.5 * (-a + Math.sqrt(a * a + 4 * fa));
                V[q] = Math.copySign(vAbs, force);
            }
        // seitliche Vermischung (quer und längs geglättet)
        for (int pss = 0; pss < mixingPasses + alongPasses; pss++) {
            double[] nV = V.clone();
            for (int i = 0; i < nu; i++)
                for (int j = 0; j < nv; j++) {
                    int q = i * nv + j;
                    if (r.depth[q] <= dry) continue;
                    double sum = 0;
                    int cnt = 0;
                    int[][] nb = pss < mixingPasses ? new int[][]{{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}} : new int[][]{{i, j - 1}, {i, j + 1}};
                    for (int[] b : nb) {
                        if (b[0] < 0 || b[0] >= nu || b[1] < 0 || b[1] >= nv) continue;
                        int qb2 = b[0] * nv + b[1];
                        sum += r.depth[qb2] > dry ? V[qb2] : 0;    // an der Wasserlinie kein Gleiten
                        cnt++;
                    }
                    nV[q] = (1 - mixing) * V[q] + mixing * sum / cnt;
                }
            V = nV;
        }
        // Querströmung aus der Masse: Q_u = h·U + M_u, ∂Q_u/∂u = −∂Q_v/∂v, am Ufer Q_u = 0, seewärts aufsummiert
        double[] Mu = new double[n], Qv = new double[n];
        for (int q = 0; q < n; q++) {
            double h = r.depth[q];
            if (h <= dry || c[q] <= 0) continue;
            double m = G * r.hrms[q] * r.hrms[q] / 8 / c[q];
            Mu[q] = m * Math.cos(r.theta[q]);
            Qv[q] = h * V[q] + m * Math.sin(r.theta[q]);
        }
        // Q_u je Zeile vom Ufer seewärts; die Zufuhr längs (∂Q_v/∂v) über ripSmooth m geglättet: eine Rippströmung ist
        // so breit wie die Rinne, nicht wie der Wechsel des Längsstroms im Gitter
        double[] qu = new double[nv], dq = new double[nv];
        int rs = Math.max(1, (int) Math.round(ripSmooth / cell / 2));
        for (int i = nu - 1; i >= 0; i--) {
            // seewärts der Brandung breitet sich der Kopf einer Rippströmung aus (Masse bleibt: Glättung längs)
            double qbRow = 0;
            for (int j = 0; j < nv; j++) qbRow += r.qb[i * nv + j] / nv;
            if (qbRow < 0.03)
                for (int pass = 0; pass < 3; pass++) {
                    double[] sq = qu.clone();
                    for (int j = 1; j < nv - 1; j++) qu[j] = sq[j] + ripSpread * 0.5 * (sq[j - 1] - 2 * sq[j] + sq[j + 1]);
                }
            for (int j = 0; j < nv; j++) {
                int jm = Math.max(0, j - 1), jp = Math.min(nv - 1, j + 1);
                dq[j] = j == 0 || j == nv - 1 ? 0 : (Qv[i * nv + jp] - Qv[i * nv + jm]) / ((jp - jm) * cell);   // offene Ränder
            }
            double acc = 0;
            int cnt = 0;
            for (int j = -rs; j < nv + rs; j++) {
                int jAdd = j + rs, jDel = j - rs - 1;
                if (jAdd < nv) { acc += dq[jAdd]; cnt++; }
                if (jDel >= 0) { acc -= dq[jDel]; cnt--; }
                if (j < 0 || j >= nv) continue;
                int q = i * nv + j;
                double h = r.depth[q];
                if (h <= dry) { qu[j] = 0; continue; }
                qu[j] += cell * acc / Math.max(1, cnt);
                double U = (qu[j] - Mu[q]) / Math.max(0.3, h);
                r.cu[q] = (float) Math.max(-maxCurrent, Math.min(maxCurrent, U));
                r.cv[q] = (float) Math.max(-maxCurrent, Math.min(maxCurrent, V[q]));
            }
        }
    }

    // ------------------------------------------------------------------ Abfragen

    /** Gitterlage (i, j) des Orts als Gleitkommazahlen; NaN außerhalb. */
    public double[] gridOf(double x, double z) {
        double dx = x - x0, dz = z - z0;
        double u = (dx * sx + dz * sz) / cell, v = (dx * ax + dz * az) / cell;
        if (u < -1e-6 || v < -1e-6 || u > nu - 1 + 1e-6 || v > nv - 1 + 1e-6) return null;
        u = Math.max(0, Math.min(nu - 1, u));
        v = Math.max(0, Math.min(nv - 1, v));
        return new double[]{u, v};
    }

    private double sample(float[] a, double x, double z, double outside) {
        Result r = result;
        double[] g = gridOf(x, z);
        if (r == null || g == null) return outside;
        int i = (int) Math.floor(g[0]), j = (int) Math.floor(g[1]);
        int i1 = Math.min(nu - 1, i + 1), j1 = Math.min(nv - 1, j + 1);
        double fi = g[0] - i, fj = g[1] - j;
        return (a[i * nv + j] * (1 - fj) + a[i * nv + j1] * fj) * (1 - fi) + (a[i1 * nv + j] * (1 - fj) + a[i1 * nv + j1] * fj) * fi;
    }

    /** Signifikante Wellenhöhe H_s = √2·H_rms (m) am Ort; NaN außerhalb oder vor dem ersten Rechnen. */
    public double hsAt(double x, double z) { Result r = result; return r == null ? Double.NaN : Math.sqrt(2) * sample(r.hrms, x, z, Double.NaN); }
    public double hrmsAt(double x, double z) { Result r = result; return r == null ? Double.NaN : sample(r.hrms, x, z, Double.NaN); }
    /** Anteil gebrochener Wellen 0..1. */
    public double breakingAt(double x, double z) { Result r = result; return r == null ? 0 : sample(r.qb, x, z, 0); }
    /** Set-up (m) am Ort. */
    public double setupAt(double x, double z) { Result r = result; return r == null ? 0 : sample(r.eta, x, z, 0); }
    /** Dissipation (W/m²). */
    public double dissipationAt(double x, double z) { Result r = result; return r == null ? 0 : sample(r.diss, x, z, 0); }

    /** Laufrichtung der Wellen am Ort als Einheitsvektor in der Welt (x, z). */
    public double[] directionAt(double x, double z) {
        Result r = result;
        double th = r == null ? 0 : sample(r.theta, x, z, 0);
        return new double[]{Math.cos(th) * sx + Math.sin(th) * ax, Math.cos(th) * sz + Math.sin(th) * az};
    }

    /** Mittlere Strömung am Ort in der Welt (m/s, x und z). */
    public double[] currentAt(double x, double z) {
        Result r = result;
        if (r == null) return new double[2];
        double u = sample(r.cu, x, z, 0), v = sample(r.cv, x, z, 0);
        return new double[]{u * sx + v * ax, u * sz + v * az};
    }

    /** Brechertyp am Ort nach Battjes aus ξ_b (Hang in Laufrichtung über {@link Surf#slopeBase}); NONE, wo kaum Wellen brechen. */
    public Surf.Breaker typeAt(double x, double z) {
        Result r = result;
        if (r == null || breakingAt(x, z) < 0.05) return Surf.Breaker.NONE;
        double[] d = directionAt(x, z);
        double half = surf.slopeBase / 2;
        double s = Math.max(0, (surf.bed.height(x + d[0] * half, z + d[1] * half) - surf.bed.height(x - d[0] * half, z - d[1] * half)) / surf.slopeBase);
        return Surf.typeAtBreaking(Surf.iribarren(s, Math.max(1e-3, hsAt(x, z)), r.tp));
    }

    /** „Wellenfeld 301 × 801 zu 5 m · H_s draußen 1,0 m, 8 s aus 285° · γ 0,62 · Set-up bis 0,21 m · Strom bis 0,6 m/s · 41 ms“ */
    public String describe() {
        Result r = result;
        if (r == null) return String.format(Locale.GERMAN, "Wellenfeld %d × %d zu %.0f m · noch nicht gerechnet", nu, nv, cell);
        double eMax = 0, sMax = 0, bz = 0;
        for (int q = 0; q < r.eta.length; q++) {
            if (r.depth[q] > dry) eMax = Math.max(eMax, r.eta[q]);
            sMax = Math.max(sMax, Math.hypot(r.cu[q], r.cv[q]));
            if (r.qb[q] > 0.05) bz++;
        }
        return String.format(Locale.GERMAN, "Wellenfeld %d × %d zu %.0f m · H_s draußen %.1f m, %.0f s aus %03.0f° · γ %.2f · Brandung %.0f %% · Set-up bis %.2f m · Strom bis %.2f m/s · %.0f ms%s",
                nu, nv, cell, r.hs0, r.tp, r.fromDeg, r.gamma, 100 * bz / r.eta.length, eMax, sMax, r.ms, busy() ? " · rechnet" : "");
    }
}
