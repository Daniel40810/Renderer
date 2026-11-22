package com.dan.physics.waves;

import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.raum.Utm;

import java.time.Instant;

/**
 * Der Rand des Seegangs aus ERA5 (Werkbuch HalligHooge Teil A, 12.9, P8b, F74 A): je Randzelle eines Gitters das
 * einlaufende Spektrum E(f, θ) aus den Kennwerten von ERA5 an der Mitte der Geisterzelle ({@code Gitter.geist}),
 * bilinear im Ort (Richtungen als Einheitsvektoren), linear in der Zeit.
 * <p>
 * Je Teil (Windsee {@code shww, mpww, mdww, dwww}; Dünung {@code shts, mpts, mdts, dwps}) ein JONSWAP-Spektrum mit
 * γ = {@link #gamma} [G], dessen T_m−1,0 auf dem Gitter der Frequenzen gleich der mittleren Periode von ERA5 ist [S: ERA5
 * gibt die mittlere Periode aus dem Moment −1], auf dem Gitter genau auf die Wellenhöhe des Teils gebracht. Richtungen
 * nach cos²ˢ((θ − θ_m)/2) mit s = 2/w² − 1 aus der Richtungsbreite w von ERA5 (w² = 2 (1 − m₁), m₁ = s/(s + 1) für
 * diese Verteilung), s in [{@link #sMin}, {@link #sMax}] [G]. Die Richtung von ERA5 (woher, von geographisch Nord im
 * Uhrzeigersinn) wird zur Richtung, wohin die Wellen laufen, im Gitter (0 = +x, gegen den Uhrzeigersinn), mit der
 * Meridiankonvergenz gedreht wie der Wind in hydro. Zuletzt wird das Spektrum auf die Wellenhöhe gesamt von ERA5
 * ({@code swh}) gebracht: Sie ist die verlässlichste Größe, und Windsee und Dünung zusammen treffen sie nur ungefähr.
 */
public final class Randspektrum {
    public double gamma = 3.3, sMin = 1, sMax = 60;
    /** Unter dieser Wellenhöhe eines Teils (m) bleibt er weg [G]. */
    public double hsMin = 0.01;

    private final Wetterfeld wf;
    private final Waves w;
    private final int rz;
    private final Gradraster.Gewichte[] gew;
    /** je Randzelle cos und sin der Meridiankonvergenz. */
    private final double[] kos, sin;
    /** Die Größen: acht der Teile, dann swh. */
    private final int[] gr = new int[9];
    private final int gSwh;
    /** Zwischenspeicher: die Felder der beiden Stunden um die zuletzt gelesene Zeit. */
    private int stundeA = -1;
    private float[][] feldA, feldB;

    /** @param g das Gitter (UTM oder geographisch) von {@code w}, gleiche Zellen */
    public Randspektrum(Wetterfeld wf, Gitter g, Waves w) {
        if (g.nx != w.nx || g.ny != w.ny) throw new IllegalArgumentException("Randspektrum: Gitter " + g.nx + " × " + g.ny + " gegen Waves " + w.nx + " × " + w.ny);
        this.wf = wf;
        this.w = w;
        rz = g.randZellen();
        String[] namen = {"shww", "mpww", "mdww", "dwww", "shts", "mpts", "mdts", "dwps"};
        for (int k = 0; k < 8; k++) {
            gr[k] = wf.groesse(namen[k]);
            if (gr[k] < 0) throw new IllegalArgumentException("Randspektrum: Größe " + namen[k] + " fehlt im Seegang aus ERA5");
        }
        gSwh = wf.groesse("swh");
        gr[8] = gSwh;
        gew = new Gradraster.Gewichte[rz];
        kos = new double[rz];
        sin = new double[rz];
        for (int r = 0; r < rz; r++) {
            double[] p = g.geist(r), ll = g.grad(p[0], p[1]);
            gew[r] = wf.raster.gewichte(ll[0], ll[1], null, 0, 1, 0);
            if (gew[r] == null) throw new IllegalArgumentException(String.format(java.util.Locale.ROOT, "Randspektrum: Geisterzelle %d (%.3f° O, %.3f° N) liegt außerhalb von ERA5 (%s)", r, ll[0], ll[1], wf.raster));
            double gam = g.lage == Gitter.Lage.UTM ? Math.toRadians(Utm.faktoren(ll[0], ll[1], g.zone)[0]) : 0;
            kos[r] = Math.cos(gam);
            sin[r] = Math.sin(gam);
        }
    }

    /** Wellenhöhe gesamt (swh) von ERA5 an der Geisterzelle r zur Zeit t (m); für die Prüfung. */
    public double swh(int r, Instant t) { return wf.wert(gSwh, t, gew[r]); }

    /** Das Randfeld zur Zeit t: {@code Waves.randZellen()} · nf · nd Werte, Index (r · nf + m) · nd + q. */
    public double[] feld(Instant t) {
        double s = (t.getEpochSecond() - wf.start.getEpochSecond() + t.getNano() * 1e-9) / wf.schritt;
        if (s < -1e-9 || s > wf.zeiten - 1 + 1e-9) throw new IllegalArgumentException("Randspektrum: " + t + " außerhalb des Seegangs aus ERA5 (" + wf.start + " … " + wf.ende() + ")");
        int k = Math.min(wf.zeiten - 2, Math.max(0, (int) Math.floor(s)));
        double a = Math.max(0, Math.min(1, s - k));
        if (k != stundeA) {
            feldA = new float[9][];
            feldB = new float[9][];
            for (int q = 0; q < 9; q++) { feldA[q] = wf.feld(k, gr[q]); feldB[q] = wf.feld(k + 1, gr[q]); }
            stundeA = k;
        }
        int nfd = w.nf * w.nd;
        double[] feld = new double[rz * nfd];
        double[] teil = new double[nfd];
        int gs = gr.length;
        for (int r = 0; r < rz; r++) {
            for (int p = 0; p < 2; p++) {
                int o = 4 * p;
                double hs = mittel(o, r, a), tm = mittel(o + 1, r, a), wb = mittel(o + 3, r, a);
                double[] dir = richtung(o + 2, r, a);
                if (!(hs >= hsMin) || !(tm > 0)) continue;
                // woher (geographisch, im Uhrzeigersinn von Nord) → wohin, mathematisch; dann um die Konvergenz ins Gitter
                double ex = -dir[0], ny = -dir[1];                       // Einheitsvektor wohin: Ost, Nord
                double gx = ex * kos[r] - ny * sin[r], gy = ny * kos[r] + ex * sin[r];
                spektrum(hs, tm, Math.atan2(gy, gx), wb, teil);
                for (int n = 0; n < nfd; n++) feld[r * nfd + n] += teil[n];
            }
            double m0 = 0;
            for (int m = 0; m < w.nf; m++) for (int q = 0; q < w.nd; q++) m0 += feld[r * nfd + m * w.nd + q] * w.df[m] * w.dTheta;
            double soll = mittel(gs - 1, r, a);
            if (m0 > 0 && soll > 0) {
                double z = soll * soll / 16 / m0;
                for (int n = 0; n < nfd; n++) feld[r * nfd + n] *= z;
            }
        }
        return feld;
    }

    /** Linear in der Zeit, bilinear im Ort. */
    private double mittel(int q, int r, double a) {
        Gradraster.Gewichte g = gew[r];
        double x = 0, y = 0;
        for (int k = 0; k < g.index().length; k++) { x += g.gewicht()[k] * feldA[q][g.index()[k]]; y += g.gewicht()[k] * feldB[q][g.index()[k]]; }
        return (1 - a) * x + a * y;
    }

    /** Richtung (woher, Grad) als Einheitsvektor {Ost, Nord}, gemittelt über Ort und Zeit. */
    private double[] richtung(int q, int r, double a) {
        Gradraster.Gewichte g = gew[r];
        double x = 0, y = 0;
        for (int k = 0; k < g.index().length; k++) {
            double wa = (1 - a) * g.gewicht()[k], wb = a * g.gewicht()[k];
            double da = Math.toRadians(feldA[q][g.index()[k]]), db = Math.toRadians(feldB[q][g.index()[k]]);
            x += wa * Math.sin(da) + wb * Math.sin(db);
            y += wa * Math.cos(da) + wb * Math.cos(db);
        }
        double n = Math.hypot(x, y);
        return n > 0 ? new double[] {x / n, y / n} : new double[] {0, 1};
    }

    /**
     * Ein Teil: JONSWAP zur Höhe hs und zur mittleren Periode tm (T_m−1,0), Richtungen um θ_m (rad, wohin, im Gitter)
     * mit der Breite wb von ERA5; Ergebnis E(f, θ) (m²/(Hz·rad)) in {@code aus}, Index m · nd + q.
     */
    public void spektrum(double hs, double tm, double thetaM, double wb, double[] aus) {
        double tp = tm / 0.9;
        double[] e = new double[w.nf];
        for (int it = 0; it < 8; it++) {                                        // T_m−1,0 auf dem Gitter der Frequenzen treffen
            jonswap(tp, e);
            double m0 = 0, mm1 = 0;
            for (int m = 0; m < w.nf; m++) { m0 += e[m] * w.df[m]; mm1 += e[m] * w.df[m] / w.f[m]; }
            double t = m0 > 0 ? mm1 / m0 : tp;
            if (Math.abs(t / tm - 1) < 1e-6) break;
            tp *= tm / t;
        }
        double s0 = 0;
        for (int m = 0; m < w.nf; m++) s0 += e[m] * w.df[m];
        double z = s0 > 0 ? hs * hs / 16 / s0 : 0;
        double sp = Math.max(sMin, Math.min(sMax, wb > 0 ? 2 / (wb * wb) - 1 : sMax));
        double[] d = new double[w.nd];
        double sd = 0;
        for (int q = 0; q < w.nd; q++) {
            double c = Math.cos(0.5 * (w.theta[q] - thetaM));
            d[q] = Math.pow(Math.abs(c), 2 * sp);
            sd += d[q] * w.dTheta;
        }
        for (int m = 0; m < w.nf; m++) for (int q = 0; q < w.nd; q++) aus[m * w.nd + q] = e[m] * z * d[q] / sd;
    }

    private void jonswap(double tp, double[] e) {
        double fp = 1 / tp;
        for (int m = 0; m < w.nf; m++) {
            double x = w.f[m] / fp, sg = x <= 1 ? 0.07 : 0.09;
            e[m] = Math.pow(w.f[m], -5) * Math.exp(-1.25 * Math.pow(x, -4)) * Math.pow(gamma, Math.exp(-(x - 1) * (x - 1) / (2 * sg * sg)));
        }
    }
}
