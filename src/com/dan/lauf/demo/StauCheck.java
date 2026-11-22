package com.dan.lauf.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.pruef.Attest;
import com.dan.lauf.Lauf;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.waves.Waves;
import com.dan.physics.waves.gpu.WavesKarte;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Stau aus dem Seegang am ebenen Strand (P8c, Werkbuch HalligHooge Teil A, 12.9, Prüfsteine 1 und 2): Seegang
 * ({@link Waves}, Referenz) und Strömung ({@link Hydro}, Prozessor) gekoppelt wie im Lauf – hydro bekommt die Kraft aus dem
 * Gefälle der Strahlungsspannung ({@link Waves#kraft}), der Seegang die Tiefe aus hydro – bis der Spiegel steht.
 * <ul>
 * <li><b>Fall:</b> Strand 1:100 von 15,5 m Tiefe, an der Küste bis +2,5 m NHN (180 Zellen zu 10 m, drei Reihen),
 * senkrecht einlaufend JONSWAP (Hs 2 m, Tp 8 s, γ 3,3), nur Brechen (Battjes–Janssen); am offenen Rand im Westen
 * Wasserstand 0, Manning 0,02, ohne Coriolis. Die Kraft wird über 1 h ohne Knick angefahren; gemittelt über die letzte
 * Stunde von 6 h. Der Seegang rechnet alle 900 s auf der Tiefe aus hydro neu (5 s Schritte).</li>
 * <li><b>Prüfstein 1:</b> der Spiegel gegen die stationäre Lösung derselben Bilanz, unabhängig integriert (RK4, 0,5 m):
 * Energiefluss je Frequenz mit dem Brechen nach Battjes–Janssen in der Tiefe d + η und
 * dη/dx = −(dS_xx/dx) / (ρ g (d + η)), S_xx = ρ g Σ E_m (2 n_m − ½). Maß: über die Zellen mit 0,3–4 m Ruhetiefe der
 * Betrag der Abweichung im Mittel, bezogen auf den größten Stau der Integration; Grenze ±10 % [G].</li>
 * <li><b>Prüfstein 2:</b> dieselbe Tiefe auf der Karte ({@link WavesKarte}, float) gegen die Referenz: Kraft und Hs nach
 * 3 000 s, größte relative Abweichung ≤ 1 · 10⁻³; dazu, wie oft die Kappung (W7) griff.</li>
 * </ul>
 * Attest {@code docs/status/P8c_Stau.md}.
 */
public final class StauCheck {
    private static final Attest A = new Attest("com.dan.lauf (Stau am Strand)", Lauf.VERSION);
    static final int NX = 180, NY = 3;
    static final double DX = 10, D0 = 15.5, GEFAELLE = 0.01, HS = 2, TP = 8, RHO = 1025, G = 9.81;

    public static void main(String[] args) {
        List<String> a = List.of(args);
        boolean ohneKarte = a.contains("--ohne-karte");
        long n0 = System.nanoTime();
        try {
            double[] tiefe = pruefstein1();
            if (!ohneKarte) pruefstein2(tiefe);
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.0f s", (System.nanoTime() - n0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", "P8c_Stau.md"), "P8c – StauCheck (Stau aus dem Seegang am Strand)");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static double z(double x) { return -D0 + GEFAELLE * x; }

    /** Der Seegang auf dem Strand: nur Brechen, senkrecht von Westen. */
    static Waves seegang() {
        Waves w = new Waves(NX, 1, DX, DX, 14, 0.06, 0.35, 36);
        w.mitWind = w.mitSchaum = w.mitQuad = w.mitReibung = false;
        w.mitBrechen = true;
        w.periodischY = true;
        double[] ef = jonswap(w, HS, TP, 3.3);
        w.randW = (i, j, m, q) -> q == 0 ? ef[m] / w.dTheta : 0;
        return w;
    }

    /** JONSWAP über der Frequenz (m²/Hz), auf dem Gitter auf Hs gebracht (wie WavesCheck). */
    static double[] jonswap(Waves w, double hs, double tp, double gamma) {
        double fp = 1 / tp, s0 = 0;
        double[] e = new double[w.nf];
        for (int m = 0; m < w.nf; m++) {
            double x = w.f[m] / fp, sg = x <= 1 ? 0.07 : 0.09;
            e[m] = Math.pow(w.f[m], -5) * Math.exp(-1.25 * Math.pow(x, -4)) * Math.pow(gamma, Math.exp(-(x - 1) * (x - 1) / (2 * sg * sg)));
            s0 += e[m] * w.df[m];
        }
        double z = hs * hs / 16 / s0;
        for (int m = 0; m < w.nf; m++) e[m] *= z;
        return e;
    }

    static Hydro strand() {
        Gitter g = Gitter.utm("Strand", 32, 0, 0, DX, NX, NY);
        int fein = 4, wi = NX * fein, he = NY * fein;
        double s = DX / fein;
        double[] zf = new double[wi * he], nf = new double[wi * he];
        for (int j = 0; j < he; j++)
            for (int i = 0; i < wi; i++) {
                zf[i + wi * j] = z((i + 0.5) * s);
                nf[i + wi * j] = 0.02;
            }
        Subgrid sg = Subgrid.bauen(g, fein, Subgrid.aus(zf, wi, he), Subgrid.aus(nf, wi, he), Subgrid.STUFEN);
        Hydro h = new Hydro(g, sg.packen());
        Arrays.fill(h.f, 0);
        h.fuellen(0);
        h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
        h.randwert[Hydro.WEST] = (t, x, y) -> 0;
        h.kraftX = new double[h.zellen];
        h.kraftY = new double[h.zellen];
        return h;
    }

    /** Mittlere Tiefe je Spalte aus hydro (Volumen durch Zellfläche, über die Reihen gemittelt). */
    static double[] tiefe(Hydro h) {
        double[] t = new double[NX];
        for (int i = 0; i < NX; i++) {
            double s = 0;
            for (int j = 0; j < NY; j++) s += h.vol[i + NX * j] / h.zellflaeche;
            t[i] = s / NY;
        }
        return t;
    }

    static double[] eta(Hydro h) {
        double[] e = new double[NX];
        for (int i = 0; i < NX; i++) {
            double s = 0;
            for (int j = 0; j < NY; j++) s += h.eta[i + NX * j];
            e[i] = s / NY;
        }
        return e;
    }

    /** Gibt die Tiefe am Ende zurück (für Prüfstein 2). */
    static double[] pruefstein1() {
        A.teil("1 Stau am Strand: gekoppelt gegen die stationäre Lösung");
        Hydro h = strand();
        Waves w = seegang();
        double anfahren = 3600, ende = 6 * 3600, mittelVon = 5 * 3600, takt = 900;
        double[] mittel = new double[NX];
        int nm = 0;
        double[] kr = new double[NX];
        int kappungen = 0;
        for (double t = 0; t < ende - 1e-9; t += takt) {
            double[] d = tiefe(h);
            for (int i = 0; i < NX; i++) w.tiefe[i] = Math.max(0, d[i]);
            w.vorbereiten();
            int nz = (int) Math.round((t == 0 ? 3000 : takt) / 5);
            for (int s = 0; s < nz; s++) w.schritt(5);
            for (int i = 0; i < NX; i++) if (w.nass(i) && w.m0(i) >= 0.999 * w.gammaBJ * w.tiefe[i] * w.gammaBJ * w.tiefe[i] / 8) kappungen++;
            double[][] k = w.kraft();
            for (int i = 0; i < NX; i++) kr[i] = k[0][i];
            // hydro: 900 s in Stücken zu 60 s, die Kraft ohne Knick angefahren
            for (double u = 0; u < takt - 1e-9; u += 60) {
                double r = rampe(t + u + 30, anfahren);
                for (int i = 0; i < NX; i++) for (int j = 0; j < NY; j++) h.kraftX[i + NX * j] = r * kr[i];
                h.lauf(t + u + 60, 5);
                if (t + u + 60 > mittelVon + 1e-9) {
                    double[] e = eta(h);
                    for (int i = 0; i < NX; i++) mittel[i] += e[i];
                    nm++;
                }
            }
        }
        for (int i = 0; i < NX; i++) mittel[i] /= nm;
        // ---- unabhängig: Fluss je Frequenz und Spiegel, von der Mitte der ersten Zelle zur Küste
        double[] fl = new double[w.nf];
        for (int m = 0; m < w.nf; m++) fl[m] = w.gruppe(0, m) * w.e[w.ix(0, m, 0)] * w.dTheta * w.df[m];
        double x = 0.5 * DX, eta = mittel[0], hstep = 0.5;
        double[] ode = new double[NX];
        Arrays.fill(ode, Double.NaN);
        ode[0] = eta;
        for (int c = 1; c < NX; c++) {
            double ziel = (c + 0.5) * DX;
            boolean aus = false;
            while (x < ziel - 1e-9) {
                double dA = -z(x) + eta, dB = -z(x + hstep) + eta;
                if (dB < 0.25) { aus = true; break; }
                double sA = spannung(w, fl, dA);
                double dM = -z(x + hstep / 2) + eta;
                double[] k1 = rate(w, fl, dA), k2 = rate(w, plus(fl, k1, hstep / 2), dM);
                double[] k3 = rate(w, plus(fl, k2, hstep / 2), dM), k4 = rate(w, plus(fl, k3, hstep), dB);
                for (int m = 0; m < w.nf; m++) fl[m] = Math.max(0, fl[m] + hstep / 6 * (k1[m] + 2 * k2[m] + 2 * k3[m] + k4[m]));
                double sB = spannung(w, fl, dB);
                eta -= (sB - sA) / (RHO * G * dM);
                x += hstep;
            }
            if (aus) break;
            ode[c] = eta;
        }
        double etaMax = 0;
        for (int c = 0; c < NX; c++) if (Double.isFinite(ode[c]) && -z((c + 0.5) * DX) >= 0.3) etaMax = Math.max(etaMax, ode[c]);
        double summe = 0, groesste = 0, setdown = 0;
        int n = 0;
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < NX; c++) {
            double d0 = -z((c + 0.5) * DX);
            if (Double.isFinite(ode[c])) setdown = Math.min(setdown, ode[c]);
            if (d0 < 0.3 || d0 > 4 || !Double.isFinite(ode[c])) continue;
            double ab = Math.abs(mittel[c] - ode[c]) / etaMax;
            summe += ab;
            n++;
            groesste = Math.max(groesste, ab);
            if (Math.abs(d0 - 4) < 0.051 || Math.abs(d0 - 2) < 0.051 || Math.abs(d0 - 1) < 0.051 || Math.abs(d0 - 0.5) < 0.051 || Math.abs(d0 - 0.3) < 0.051)
                sb.append(f("%.1f m: %+.3f/%+.3f m; ", d0, mittel[c], ode[c]));
        }
        double modellMax = 0;
        for (int c = 0; c < NX; c++) if (-z((c + 0.5) * DX) >= 0.3) modellMax = Math.max(modellMax, mittel[c]);
        A.info("Fall", f("Strand 1:%.0f, %d × %d Zellen zu %.0f m, Hs %.1f m, Tp %.0f s, senkrecht; 6 h, Kraft über 1 h angefahren, Mittel der letzten Stunde (%d Stände)",
                1 / GEFAELLE, NX, NY, DX, HS, TP, nm));
        A.info("Spiegel über der Ruhetiefe (Modell/Integration)", sb.toString());
        A.info("größter Stau (Ruhetiefe ≥ 0,3 m); tiefster Sog vor dem Brechen (Integration)", f("Modell %.3f m, Integration %.3f m; %.4f m", modellMax, etaMax, setdown));
        A.pruefe(n > 0 && summe / n <= 0.10 && etaMax > 0, "Prüfstein 1: Stau am Strand gegen die stationäre Lösung, 0,3–4 m Ruhetiefe, Mittel des Betrags bezogen auf den größten Stau",
                f("%.1f %% (größte %.1f %%, %d Zellen)", 100 * summe / Math.max(1, n), 100 * groesste, n), "≤ 10 % [G]");
        A.info("Kappung (W7) gegriffen", kappungen + " Zellen × Takte");
        return tiefe(h);
    }

    static double rampe(double t, double T) { return t >= T ? 1 : 0.5 * (1 - Math.cos(Math.PI * t / T)); }

    /** S_xx (N/m) aus dem Energiefluss je Frequenz in der Tiefe d, senkrechter Einfall. */
    static double spannung(Waves w, double[] fl, double d) {
        double s = 0;
        for (int m = 0; m < w.nf; m++) {
            double k = Waves.wellenzahl(w.sig[m], d), cg = Waves.gruppe(w.sig[m], k, d), em = fl[m] / cg, n = cg * k / w.sig[m];
            s += em * (2 * n - 0.5);
        }
        return RHO * G * s;
    }

    static double[] plus(double[] a, double[] b, double h) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = Math.max(0, a[i] + h * b[i]);
        return r;
    }

    /** dF_m/dx für die Brecherbilanz in der Tiefe d (wie WavesCheck, Prüfstein 3 von P8a). */
    static double[] rate(Waves w, double[] fl, double d) {
        double[] em = new double[w.nf], r = new double[w.nf];
        double et = 0, es = 0;
        for (int m = 0; m < w.nf; m++) {
            em[m] = fl[m] / Waves.gruppe(w.sig[m], Waves.wellenzahl(w.sig[m], d), d);
            et += em[m]; es += em[m] * w.sig[m];
        }
        if (et <= 0 || d <= 0) return r;
        double hm = w.gammaBJ * d, qb = Waves.brecheranteil(Math.sqrt(8 * et) / hm);
        double gam = 0.25 * w.alphaBJ * qb * (es / et) / (2 * Math.PI) * hm * hm / et;
        for (int m = 0; m < w.nf; m++) r[m] = -gam * em[m];
        return r;
    }

    static void pruefstein2(double[] tiefe) {
        A.teil("2 Kraft auf der Karte gegen die Referenz");
        Waves ref = seegang(), kar = seegang();
        for (int i = 0; i < NX; i++) { ref.tiefe[i] = Math.max(0, tiefe[i]); kar.tiefe[i] = ref.tiefe[i]; }
        ref.vorbereiten();
        kar.vorbereiten();
        int schritte = 600;
        for (int s = 0; s < schritte; s++) ref.schritt(5);
        double[] sp = new double[kar.nf * kar.nd];
        for (int m = 0; m < kar.nf; m++) for (int q = 0; q < kar.nd; q++) sp[m * kar.nd + q] = kar.randW.e(0, 0, m, q);
        try (WavesKarte k = WavesKarte.mitGeraet(kar, sp, 1)) {
            for (int s = 0; s < schritte; s++) k.schritt(5);
            k.runter();
        }
        double[][] fr = ref.kraft(), fk = kar.kraft();
        double fmax = 0, hmax = 0;
        for (int c = 0; c < NX; c++) { fmax = Math.max(fmax, Math.abs(fr[0][c])); hmax = Math.max(hmax, ref.hs(c)); }
        double df = 0, dh = 0;
        int wo = 0;
        for (int c = 0; c < NX; c++) {
            double a = Math.abs(fk[0][c] - fr[0][c]) / fmax;
            if (a > df) { df = a; wo = c; }
            if (ref.hs(c) > 0.05 * hmax) dh = Math.max(dh, Math.abs(kar.hs(c) / ref.hs(c) - 1));
        }
        A.info("Fall", f("Tiefe aus Prüfstein 1, %d Schritte zu 5 s; Kraft bis %.2f N/m², Hs bis %.2f m", schritte, fmax, hmax));
        A.hoechstens("Prüfstein 2: Kraft, Karte gegen Referenz, größte Abweichung bezogen auf die größte Kraft", df, 1e-3, "");
        A.info("Prüfstein 2: Ort; Hs, größte relative Abweichung", f("Zelle %d (%.2f m Tiefe): %.4f gegen %.4f N/m²; Hs %.1e", wo, ref.tiefe[wo], fk[0][wo], fr[0][wo], dh));
    }
}
