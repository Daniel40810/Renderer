package com.dan.render.reference;

import com.dan.core.szene.Saeule;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

/**
 * Die Tabellen der Wassersäule (P7d, Werkbuch HalligHooge Teil A, 12.8, F55 A), gebacken mit Monte Carlo an einer
 * Platte: Wasser unbegrenzt in der Fläche, Tiefe x, Grund schwarz oder nach Lambert, Streuung nach Fournier-Forand
 * ({@link Phasen}, b_b/b = {@link Wellen#BB_ZU_B}).
 * <p>
 * Ähnlichkeit: gerechnet wird mit K = a + b_b = 1, also b_b = u, a = 1 − u, b = u / (b_b/b), c = a + b. Die Tiefe x ist
 * dann (a + b_b) · D, die Werte gelten für jedes Wasser mit demselben u.
 * <p>
 * Schätzer: Die Photonen kommen von oben (waagerechte Bestrahlung 1 unter der Fläche) oder vom Grund (Ausstrahlung 1,
 * Lambert). Absorption geht ins Gewicht (ω je Stoß, unter 10⁻³ russisches Roulette). Nach jedem Stoß wird die neue
 * Richtung gezogen; zeigt sie nach oben, zählt der Erwartungswert, dass das Photon die Fläche ohne weiteren Stoß
 * erreicht (w ω e^(−c d/μ)), in ihrem Fach von μ; erreicht der wirkliche Flug die Fläche, endet das Photon ohne zweite
 * Zählung. Ein Lauf von oben gilt für alle Tiefen zugleich: Ein Beitrag zählt für jede Platte, deren Grund das Photon
 * noch nicht erreicht hat (bei schwarzem Grund endet es dort), und T am Grund ist das Gewicht beim ersten Erreichen.
 * Die Richtung läuft in drei Dimensionen; je Fach zählt neben dem Mittel über den Azimut der Anteil mit cos φ.
 * Die Fläche ist glatt: Licht, das sie von unten erreicht, geht mit dem Gewicht F (Fresnel, jenseits des Kegels von
 * 48,3° ganz) gespiegelt zurück ins Wasser. Gezählt wird die Leuchtdichte unter der Fläche nach oben (vor dem
 * Durchgang); den Durchgang (1 − F) / n² rechnet das Echtzeitbild.
 */
public final class Saeulentabelle {
    /** Photonen je Lauf von oben und vom Grund [G]. */
    public int photonenOben = 200_000, photonenGrund = 100_000;
    /** Saat. */
    public long saat = 20261114L;

    /** Höchstens so viele Stöße je Photon (zur Prüfung des ersten Stoßes) [G]. */
    public int hoechstensStoesse = Integer.MAX_VALUE;

    /**
     * Ergebnis eines Laufs von oben bei (u, μ_s): L[x][μv] (über den Azimut gemittelt), C[x][μv] (Mittel von L cos φ,
     * φ der Azimut der Richtung des Lichts gegen den der Sonnenstrahlen, also L(φ) ≈ L + 2 C cos φ), L1 (erster Stoß),
     * T[x], Fluss hinaus je Platte.
     */
    public static final class Oben {
        public final double[][] l = new double[Saeule.NX][Saeule.NMU], cos = new double[Saeule.NX][Saeule.NMU], l1 = new double[Saeule.NX][Saeule.NMU];
        public final double[] t = new double[Saeule.NX], hinaus = new double[Saeule.NX];
    }

    /** Ergebnis eines Laufs vom Grund bei (u, x): U[μv], R. */
    public static final class Grund {
        public final double[] u = new double[Saeule.NMU];
        public double r;
    }

    /** Brechzahl, für die die Tabellen gebacken sind [G] (Wasseroptik.n). */
    public static final double N = 1.34;

    /** Reflexion an der glatten Fläche für Licht von unten mit cos μ zum Lot (jenseits des Kegels 1). */
    public static double fresnelUnten(double mu) {
        double s2 = N * N * (1 - mu * mu);
        if (s2 >= 1) return 1;
        return com.dan.core.szene.Wasseroptik.fresnel(Math.sqrt(1 - s2), N);
    }

    public static double cVon(double u) { return 1 - u + u / Wellen.BB_ZU_B; }

    public static double omegaVon(double u) { return u / Wellen.BB_ZU_B / cVon(u); }

    static int fach(double mu) {
        if (mu < Saeule.MU_MIN) return -1;
        return Math.min(Saeule.NMU - 1, (int) ((mu - Saeule.MU_MIN) / (1 - Saeule.MU_MIN) * Saeule.NMU));
    }

    /** Erste Platte, deren Grund tiefer liegt als d (alle Platten ab dort zählen). */
    static int ersteTiefer(double d) {
        for (int j = 0; j < Saeule.NX; j++) if (Saeule.X[j] >= d) return j;
        return Saeule.NX;
    }

    static double neu(double mu, double m, SplittableRandom r) {
        double cp = Math.cos(2 * Math.PI * r.nextDouble());
        double v = mu * m + Math.sqrt(Math.max(0, 1 - mu * mu)) * Math.sqrt(Math.max(0, 1 - m * m)) * cp;
        return Math.max(-1, Math.min(1, v));
    }

    /** Lauf von oben: Licht unter der Fläche mit cos μ_s (zum Lot), waagerechte Bestrahlung 1. */
    public Oben oben(double u, double mus, long s, int n) { return oben(u, mus, s, n, Saeule.X[Saeule.NX - 1]); }

    /** Wie {@link #oben(double, double, long, int)}, die Photonen enden unter der Tiefe xmax (Platten bis dort gelten). */
    public Oben oben(double u, double mus, long s, int n, double xmax) {
        SplittableRandom r = new SplittableRandom(s);
        double c = cVon(u), om = omegaVon(u);
        Oben o = new Oben();
        double[][] lj = new double[Saeule.NX + 1][Saeule.NMU], cj = new double[Saeule.NX + 1][Saeule.NMU], l1j = new double[Saeule.NX + 1][Saeule.NMU];
        double[] hj = new double[Saeule.NX + 1];
        double sms = Math.sqrt(Math.max(0, 1 - mus * mus));
        for (int i = 0; i < n; i++) {
            // Richtung (Lauf der Sonnenstrahlen waagerecht nach +x, μ nach oben)
            double d = 0, mu = -mus, ux = sms, uy = 0, w = 1, dmax = 0;
            o.t[0] += 1;
            int jmin = 0, stoss = 0;
            while (true) {
                double sl = -Math.log(1 - r.nextDouble()) / c, dn = d - mu * sl;
                if (dn <= 0) {
                    // an der Fläche von unten: Fresnel (jenseits des Kegels ganz) zurück ins Wasser
                    w *= fresnelUnten(mu);
                    if (w <= 0) break;
                    d = 0;
                    mu = -mu;
                    continue;
                }
                if (dn > dmax) {
                    for (int j = jmin; j < Saeule.NX && Saeule.X[j] < dn; j++) if (Saeule.X[j] > dmax) o.t[j] += w;
                    dmax = dn;
                    jmin = ersteTiefer(dmax);
                    if (dmax > xmax) break;
                }
                d = dn;
                w *= om;
                stoss++;
                double[] q = Phasen.umAchse(new double[] {ux, uy, mu}, Phasen.ffZiehe(r.nextDouble()), r);
                double ql = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                ux = q[0] / ql;
                uy = q[1] / ql;
                mu = q[2] / ql;
                if (mu > 0) {
                    double e = w * Math.exp(-c * d / mu);
                    hj[jmin] += e * (1 - fresnelUnten(mu));
                    int f = fach(mu);
                    if (f >= 0) {
                        double h = Math.hypot(ux, uy);
                        lj[jmin][f] += e;
                        cj[jmin][f] += h > 1e-12 ? e * ux / h : 0;
                        if (stoss == 1) l1j[jmin][f] += e;
                    }
                }
                if (stoss >= hoechstensStoesse) break;
                if (w < 1e-3) {
                    if (r.nextDouble() < 0.1) w *= 10; else break;
                }
            }
        }
        // kumulieren: ein Beitrag mit erster tieferer Platte j zählt für alle Platten ab j
        double[] sum = new double[Saeule.NMU], sumc = new double[Saeule.NMU], sum1 = new double[Saeule.NMU];
        double sh = 0;
        for (int j = 0; j < Saeule.NX; j++) {
            for (int k = 0; k < Saeule.NMU; k++) {
                sum[k] += lj[j][k];
                sum1[k] += l1j[j][k];
                sumc[k] += cj[j][k];
                double om2 = Saeule.raumFach(k);
                o.l[j][k] = sum[k] / (n * om2);
                o.cos[j][k] = sumc[k] / (n * om2);
                o.l1[j][k] = sum1[k] / (n * om2);
            }
            sh += hj[j];
            o.hinaus[j] = sh / n;
            o.t[j] /= n;
        }
        return o;
    }

    /** Lauf vom Grund in der Tiefe x (Lambert, Ausstrahlung 1). */
    public Grund grund(double u, double x, long s, int n) {
        SplittableRandom r = new SplittableRandom(s);
        double c = cVon(u), om = omegaVon(u);
        Grund g = new Grund();
        double[] uf = new double[Saeule.NMU];
        double zur = 0;
        for (int i = 0; i < n; i++) {
            double d = x, mu = Math.sqrt(r.nextDouble()), w = 1;
            int f0 = fach(mu);
            if (f0 >= 0) uf[f0] += Math.exp(-c * d / mu);
            while (true) {
                double sl = -Math.log(1 - r.nextDouble()) / c, dn = d - mu * sl;
                if (dn <= 0) {
                    w *= fresnelUnten(mu);
                    if (w <= 0) break;
                    d = 0;
                    mu = -mu;
                    continue;
                }
                if (dn >= x) {
                    zur += w;
                    break;
                }
                d = dn;
                w *= om;
                mu = neu(mu, Phasen.ffZiehe(r.nextDouble()), r);
                if (mu > 0) {
                    int f = fach(mu);
                    if (f >= 0) uf[f] += w * Math.exp(-c * d / mu);
                }
                if (w < 1e-3) {
                    if (r.nextDouble() < 0.1) w *= 10; else break;
                }
            }
        }
        for (int k = 0; k < Saeule.NMU; k++) g.u[k] = uf[k] / (n * Saeule.raumFach(k));
        g.r = zur / n;
        return g;
    }

    /** Alle Tabellen. */
    public Saeule backe() {
        int nu = Saeule.NU, nx = Saeule.NX, nm = Saeule.NMU;
        float[] l = new float[nu * nx * nm * nm], cs = new float[nu * nx * nm * nm], t = new float[nu * nx * nm], uu = new float[nu * nx * nm], rr = new float[nu * nx];
        // Läufe einzeln an die Fäden, die teuren (großes u) zuerst
        verteile(nu * nm, q -> -(q / nm), q -> {
            int iu = q / nm, ks = q % nm;
            Oben o = oben(Saeule.U[iu], Saeule.MU_S[ks], saat + 1000L * iu + ks, photonenOben);
            for (int j = 0; j < nx; j++) {
                t[(iu * nx + j) * nm + ks] = (float) o.t[j];
                for (int kv = 0; kv < nm; kv++) {
                    l[((iu * nx + j) * nm + ks) * nm + kv] = (float) o.l[j][kv];
                    cs[((iu * nx + j) * nm + ks) * nm + kv] = (float) o.cos[j][kv];
                }
            }
        });
        verteile(nu * nx, q -> -(q / nx), q -> {
            int iu = q / nx, j = q % nx;
            Grund g = Saeule.X[j] <= 0 ? null : grund(Saeule.U[iu], Saeule.X[j], saat + 500_000L + 100L * iu + j, photonenGrund);
            for (int kv = 0; kv < nm; kv++) uu[(iu * nx + j) * nm + kv] = (float) (g == null ? 1 / Math.PI : g.u[kv]);
            rr[iu * nx + j] = (float) (g == null ? 0 : g.r);
        });
        return new Saeule(l, cs, t, uu, rr);
    }

    /** Führt die Aufgaben 0 … n−1 auf allen Kernen aus, jede für sich, in der Reihenfolge von {@code rang}. */
    static void verteile(int n, java.util.function.IntUnaryOperator rang, java.util.function.IntConsumer tu) {
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            java.util.List<java.util.concurrent.Callable<Void>> a = new java.util.ArrayList<>();
            IntStream.range(0, n).boxed().sorted(java.util.Comparator.comparingInt(rang::applyAsInt)).forEach(q -> a.add(() -> {
                tu.accept(q);
                return null;
            }));
            for (java.util.concurrent.Future<Void> f : ex.invokeAll(a)) f.get();
        } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e);
        } finally {
            ex.shutdown();
        }
    }

    /** Schreibt die Tabellen in die Datei, die {@link Saeule#lade()} liest. */
    public static void schreibe(Saeule s, File f) throws IOException {
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            o.writeInt(Saeule.MAGIE);
            o.writeInt(Saeule.NU);
            o.writeInt(Saeule.NX);
            o.writeInt(Saeule.NMU);
            for (float[] a : new float[][] {s.l, s.c, s.t, s.u, s.r}) for (float v : a) o.writeFloat(v);
        }
    }

    /** Backt die Tabellen und schreibt sie: {@code Saeulentabelle <Datei> [Photonen oben] [Photonen Grund]}. */
    public static void main(String[] a) throws IOException {
        Saeulentabelle b = new Saeulentabelle();
        if (a.length > 1) b.photonenOben = Integer.parseInt(a[1]);
        if (a.length > 2) b.photonenGrund = Integer.parseInt(a[2]);
        long t0 = System.nanoTime();
        Saeule s = b.backe();
        File f = new File(a.length > 0 ? a[0] : "src/com/dan/core/szene/saeule.bin");
        schreibe(s, f);
        System.out.printf("Säule gebacken: %d + %d Photonen je Lauf, %.1f s, %s%n", b.photonenOben, b.photonenGrund, (System.nanoTime() - t0) / 1e9, f);
    }
}
