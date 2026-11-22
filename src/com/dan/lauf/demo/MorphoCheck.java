package com.dan.lauf.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.pruef.Attest;
import com.dan.lauf.Lauf;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.hydro.HydroModul;
import com.dan.physics.sediment.Sediment;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Prüfsteine 1–7 von P9c (Werkbuch HalligHooge Teil A, 12.10, Feinplan P9c): Rückwirkung des Bodens auf hydro,
 * morphologischer Faktor, lockere Auflage, Spirale und Rauheit – alles auf dem Prozessor in double, ohne Karte und ohne
 * Gebiet (läuft auch in der Arbeitsumgebung).
 * <ul>
 * <li>1 Boden verschieben erhält das Wasser ({@code Hydro.boden}).</li>
 * <li>2 Gleichmäßige Hebung von Boden und Rand ändert die Strömung nicht.</li>
 * <li>3 Gekoppelt (hydro mit Subgrid, Sediment, Rückwirkung): ein flacher Hügel im Gerinne wandert mit der Geschwindigkeit
 * c = −F′(h) / ((1 − n) ρ_s (1 − Fr²)), F(h) das Geschiebe bei festem Durchfluss.</li>
 * <li>4 Faktor 10 über ein Zehntel der Zeit gibt dieselbe Sohle wie Faktor 1; Massenbilanz mit Faktor.</li>
 * <li>5 Lockere Auflage: Absetzen in ruhendem Wasser, Wiederaufnahme über τ_a.</li>
 * <li>6 Spirale: Krümmung einer Kreisströmung; Ablenkung des Geschiebes.</li>
 * <li>7 Rauheit: Σ Δs/n aus n; Gefälle im ebenen Gerinne nach Manning.</li>
 * </ul>
 * Mit {@code --gebiet <Ordner>} dazu Prüfstein 8 (braucht die Karte): derselbe Lauf am Bus über ein kleines Fenster von G3
 * (hydro, Sediment mit Rückwirkung, Faktor, Auflage und Spirale, ohne Seegang) einmal auf dem Prozessor in double, einmal
 * auf der Karte; verglichen werden {@code sed.c}, die Bodenänderung und die lockere Auflage am Ende.
 * <p>
 * Attest {@code docs/status/P9c_Kern.md}. Die Kopplung im Gerinne geht hier von Hand (Intervalle von 300 s wie am Bus):
 * hydro rechnet, das Sediment bekommt V/Fläche, den mittleren Fluss, Tiefe und Geschwindigkeit, hydro den Boden zurück.
 */
public final class MorphoCheck {
    private static final Attest A = new Attest("com.dan.lauf", Lauf.VERSION);
    static String nur = "1,2,3,4,5,6,7";
    /** Dauer des Falls mit Faktor 1 (h) und Höhe des Hügels (m). */
    static double stunden = 200, huegel = 0.05;
    /** Prüfstein 8 (nur mit {@code --gebiet}, braucht die Karte): Gebiet, Fenster von G3, Start, Dauer (h), Faktor. */
    static String gebiet, fenster = "670,990,150,120", startK = "2026-10-13T00:00:00Z";
    static double stundenK = 4, faktorK = 50;

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String v = i + 1 < args.length ? args[i + 1] : null;
            switch (args[i]) {
                case "--nur" -> nur = "," + v + ",";
                case "--stunden" -> stunden = Double.parseDouble(v);
                case "--gebiet" -> gebiet = v;
                case "--fenster3" -> fenster = v;
                case "--start" -> startK = v;
                case "--stunden-karte" -> stundenK = Double.parseDouble(v);
                default -> { }
            }
        }
        nur = "," + nur.replace(" ", "") + ",";
        System.out.println("MorphoCheck – P9c, Prüfsteine 1–7 (Werkbuch HalligHooge Teil A, 12.10)");
        long n0 = System.nanoTime();
        try {
            if (will(1)) verschieben();
            if (will(2)) hebung();
            if (will(3) || will(4)) huegel();
            if (will(5)) auflage();
            if (will(6)) spirale();
            if (will(7)) rauheit();
            if (gebiet != null) karte();
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.0f s", (System.nanoTime() - n0) / 1e9));
        Path p = A.schreibe(Path.of("docs", "status", "P9c_Kern.md"), "P9c – MorphoCheck (Rückwirkung, Faktor, Auflage, Spirale, Rauheit)");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    private static boolean will(int k) { return nur.contains("," + k + ","); }

    // ------------------------------------------------------------------ Hilfen

    /** Ein Gitter in UTM mit Zellen von dx Metern und sein Hydro aus Pixelhöhen (fein × fein je Zelle); ohne Coriolis. */
    static Hydro hydro(int nx, int ny, double dx, int fein, double[] pixel) {
        Gitter g = Gitter.utm("T", 32, 500_000, 6_000_000, dx, nx, ny);
        Hydro h = new Hydro(g, Subgrid.bauen(g, fein, Subgrid.aus(pixel, nx * fein, ny * fein)).packen());
        Arrays.fill(h.f, 0);
        return h;
    }

    /** Ein Gerinne längs x mit Durchfluss q (m²/s) im Westen, über anlauf Sekunden wachsend, und Wasserstand 0 im Osten. */
    static Hydro gerinne(int nx, int ny, double dx, double[] boden, double q, double anlauf) {
        Hydro h = hydro(nx, ny, dx, 1, boden);
        h.rand[Hydro.WEST] = Hydro.Rand.DURCHFLUSS;
        h.randwert[Hydro.WEST] = (t, x, y) -> q * Math.min(1, t / anlauf);
        h.rand[Hydro.OST] = Hydro.Rand.WASSERSTAND;
        h.randwert[Hydro.OST] = (t, x, y) -> 0;
        h.fuellen(0);
        return h;
    }

    /** Das Sediment zum Gerinne: nur Geschiebe, Boden aus der Packung wie am Bus. */
    static Sediment sediment(Hydro h, double[] boden) {
        Sediment s = new Sediment(h.nx, h.ny, h.dx, h.dy);
        s.tiefe = new double[s.nc];
        s.ug = new double[s.nc];
        s.vg = new double[s.nc];
        s.mitSchweb = false; s.mitSchlick = false; s.mitRippel = false; s.mitRutsch = false; s.mitDiffusion = false;
        System.arraycopy(boden, 0, s.grund, 0, s.nc);
        return s;
    }

    /** Koppelt von Hand bis zur Zeit bis (s), in Intervallen von tau: hydro, dann das Sediment, dann der Boden zurück. Gibt den größten Fehler der Wasserbilanz zurück (relativ). */
    static void koppeln(Hydro h, Sediment s, double tau, double bis, boolean mitSediment) {
        if (h.qSumme == null) h.qSumme = new double[h.kanten];
        double[] dz = new double[s.nc];
        while (h.zeit < bis - 1e-6) {
            Arrays.fill(h.qSumme, 0);
            for (int c = 0; c < s.nc; c++) s.h[c] = h.vol[c] / h.flaeche(c);
            double t1 = Math.min(bis, h.zeit + tau), dt = t1 - h.zeit;
            h.lauf(t1, Double.POSITIVE_INFINITY);
            h.zeit = t1;
            if (!mitSediment) continue;
            for (int j = 0; j < s.ny; j++) for (int i = 0; i <= s.nx; i++) { int e = h.kanteX(i, j); s.qx[i + (s.nx + 1) * j] = h.qSumme[e] / (dt * h.laenge(e)); }
            for (int j = 0; j <= s.ny; j++) for (int i = 0; i < s.nx; i++) { int e = h.kanteY(i, j); s.qy[i + s.nx * j] = h.qSumme[e] / (dt * h.laenge(e)); }
            for (int c = 0; c < s.nc; c++) {
                double a = h.vol[c] > 0 ? h.benetzt(c, h.eta[c]) : 0;
                s.tiefe[c] = a > 0 ? h.vol[c] / a : 0;
                double[] uv = h.geschwindigkeit(c);
                s.ug[c] = uv[0];
                s.vg[c] = uv[1];
            }
            s.schritt(dt);
            for (int c = 0; c < s.nc; c++) dz[c] = s.dzS[c] + s.dzM[c];
            h.boden(dz);
        }
    }

    // ------------------------------------------------------------------ 1, 2

    private static void verschieben() {
        A.teil("1 Boden verschieben erhält das Wasser");
        int nx = 24, ny = 18, fein = 4;
        Random r = new Random(7);
        double[] px = new double[nx * fein * ny * fein];
        for (int j = 0; j < ny * fein; j++) for (int i = 0; i < nx * fein; i++) px[i + nx * fein * j] = -2 + 3.0 * i / (nx * fein) + 0.4 * Math.sin(0.5 * j) + 0.2 * r.nextDouble();
        Hydro h = hydro(nx, ny, 20, fein, px);
        h.fuellen(0.2);
        double[] v0 = h.vol.clone(), e0 = h.eta.clone(), dz = new double[h.zellen];
        double fehler = 0, trocken = 0;
        int nTrocken = 0;
        for (int runde = 0; runde < 3; runde++) {
            for (int c = 0; c < h.zellen; c++) dz[c] = r.nextDouble() - 0.5;
            h.boden(dz);
            for (int c = 0; c < h.zellen; c++) {
                if (v0[c] > 0) fehler = Math.max(fehler, Math.abs(h.volumen(c, h.eta[c]) - v0[c]) / v0[c]);
                else { trocken = Math.max(trocken, Math.abs(h.eta[c] - h.grund(c))); if (runde == 0) nTrocken++; }
                fehler = Math.max(fehler, Math.abs(h.eta[c] - e0[c] - dz[c]) / 1.0);
            }
        }
        A.pruefe(fehler <= 1e-12 && trocken <= 1e-12 && Arrays.equals(v0, h.vol), "Prüfstein 1: Volumen der Zelle aus der verschobenen Tabelle gegen vorher; η = η₀ + Δ; trockene Zellen auf ihrem Grund",
                f("größter Fehler %.1e (relativ bzw. m), trocken %.1e m (%d von %d Zellen trocken), drei zufällige Verschiebungen bis ±0,5 m", fehler, trocken, nTrocken, h.zellen), "≤ 1 · 10⁻¹²");
    }

    private static void hebung() {
        A.teil("2 Gleichmäßige Hebung");
        int nx = 40, ny = 30, fein = 2;
        double[] px = new double[nx * fein * ny * fein];
        for (int j = 0; j < ny * fein; j++)
            for (int i = 0; i < nx * fein; i++) px[i + nx * fein * j] = -3 + 4.0 * i / (nx * fein) + 0.6 * Math.sin(0.21 * j) * Math.cos(0.13 * i);
        double[][] eta = new double[2][];
        double[] nass = new double[2];
        for (int k = 0; k < 2; k++) {
            final double hub = k == 0 ? 0 : 0.5;
            Hydro h = hydro(nx, ny, 50, fein, px);
            h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
            h.randwert[Hydro.WEST] = (t, x, y) -> hub + 0.5 * Math.sin(2 * Math.PI * t / 21600);
            h.fuellen(0);
            if (k == 1) {
                double[] dz = new double[h.zellen];
                Arrays.fill(dz, hub);
                h.boden(dz);
            }
            h.lauf(21600, Double.POSITIVE_INFINITY);
            eta[k] = h.eta.clone();
            for (int c = 0; c < h.zellen; c++) if (h.vol[c] > 0) nass[k]++;
        }
        double d = 0;
        for (int c = 0; c < eta[0].length; c++) d = Math.max(d, Math.abs(eta[1][c] - 0.5 - eta[0][c]));
        A.pruefe(d <= 1e-9 && nass[0] == nass[1], "Prüfstein 2: alle Zellen und der Rand +0,5 m, Tide von ±0,5 m über 6 h: η − 0,5 m gegen den Lauf ohne Hebung",
                f("größter Unterschied %.1e m; nasse Zellen am Ende %.0f und %.0f von %d", d, nass[0], nass[1], eta[0].length), "≤ 1 · 10⁻⁹ m");
    }

    // ------------------------------------------------------------------ 3, 4

    /** Geschiebe (kg/(s m)) bei Durchfluss q und Tiefe h wie in {@link Sediment} ohne Wellen und Rippel. */
    static double geschiebe(Sediment s, double d, double q, double h) {
        double u = q / h, tm = s.rho * Sediment.cdStrom(h, d / 12) * u * u, ds = Sediment.dStern(d, s.rhoS / s.rho, s.nu);
        double tcr = Sediment.thetaKrit(ds) * Sediment.G * (s.rhoS - s.rho) * d;
        return Sediment.geschiebe(d, ds, s.rho, s.rhoS, tm, tm, tcr);
    }

    private static void huegel() {
        A.teil("3, 4 Hügel im Gerinne, gekoppelt");
        int nx = 160, ny = 3;
        double dx = 10, q = 2.4, tiefe = 3, x0 = 500, sigma = 80, tau = 300, anlauf = 3 * 3600;
        double[] flach = new double[nx * ny], boden = new double[nx * ny];
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            double x = (i + 0.5) * dx;
            flach[i + nx * j] = -tiefe;
            boden[i + nx * j] = -tiefe + huegel * Math.exp(-0.5 * (x - x0) * (x - x0) / (sigma * sigma));
        }
        // Faktor 1 und 10 mit Hügel; dazu Faktor 10 ohne Hügel: Der flache Boden ändert sich im Gerinne auch (das Gefälle der
        // Reibung beschleunigt den Strom), der Hügel ist der Unterschied.
        double[] faktoren = {1, 10, 10};
        double[][] sohle = new double[3][];
        double[] lage = new double[3], bilanzS = new double[3], bilanzW = new double[3];
        double c = Double.NaN, hOrt = 0, uOrt = 0, lage0 = 0;
        long n0 = System.nanoTime();
        for (int k = 0; k < 3; k++) {
            double[] grund = k == 2 ? flach : boden;
            Hydro h = gerinne(nx, ny, dx, grund, q, 1800);
            Sediment s = sediment(h, grund);
            s.faktor = faktoren[k];
            koppeln(h, s, tau, anlauf, false);                                    // die Strömung stellt sich ein
            double v0 = h.volumen(), zu0 = h.zufluss, ab0 = h.abfluss;
            if (k == 0) {
                int i0 = (int) (x0 / dx), cz = i0 + nx;
                hOrt = h.eta[cz] + tiefe;
                uOrt = h.geschwindigkeit(cz)[0] * (h.eta[cz] - boden[cz]) / hOrt;   // über dem flachen Boden bei gleichem Durchfluss
                double qOrt = uOrt * hOrt, e = 1e-3 * hOrt;
                double fs = (geschiebe(s, 2e-4, qOrt, hOrt + e) - geschiebe(s, 2e-4, qOrt, hOrt - e)) / (2 * e), fr2 = uOrt * uOrt / (h.g * hOrt);
                c = -fs / ((1 - s.poros) * s.rhoS * (1 - fr2));
                lage0 = schwerpunkt(boden, flach, nx, ny, dx);
                A.info("Fall", f("Gerinne %d × %d Zellen zu %.0f m, Durchfluss %.2f m²/s, am Hügel h = %.3f m, u = %.3f m/s, Fr² = %.3f; Hügel %.0f mm hoch, σ = %.0f m; Sand 0,2 mm, nur Geschiebe; "
                        + "Geschiebe %.4f kg/(s m), c = %.3e m/s", nx, ny, dx, qOrt, hOrt, uOrt, fr2, 1000 * huegel, sigma, geschiebe(s, 2e-4, qOrt, hOrt), c));
            }
            double dauer = stunden * 3600 / faktoren[k];
            koppeln(h, s, tau, anlauf + dauer, true);
            double[] z = new double[nx * ny];
            for (int cz = 0; cz < z.length; cz++) z[cz] = s.boden(cz);
            sohle[k] = z;
            lage[k] = schwerpunkt(z, flach, nx, ny, dx);
            double[] w = s.wirklich();
            bilanzS[k] = Math.abs(w[0] + s.aus[2]) / Math.max(1e-9, Math.abs(s.aus[2]) + Math.abs(w[0]));
            bilanzW[k] = Math.abs(h.volumen() - v0 - (h.zufluss - zu0) + (h.abfluss - ab0)) / Math.max(h.volumen(), h.zufluss - zu0);
        }
        double weg = c * stunden * 3600, w0 = schwerpunkt(sohle[0], sohle[2], nx, ny, dx) - lage0, w1 = schwerpunkt(sohle[1], sohle[2], nx, ny, dx) - lage0, diff = 0;
        for (int cz = 0; cz < sohle[0].length; cz++) diff += Math.abs(sohle[1][cz] - sohle[0][cz]);
        diff /= sohle[0].length;
        if (will(3)) A.pruefe(Math.abs(w0 / weg - 1) <= 0.10 && bilanzW[0] < 1e-5, "Prüfstein 3: Wanderung des Hügels mit Rückwirkung über " + f("%.0f", stunden) + " h (Faktor 1) gegen c · t; Wasserbilanz",
                f("Schwerpunkt des Hügels (Sohle minus Sohle des Laufs ohne Hügel) %+.2f m gegen %+.2f m (%+.1f %%); Wasserbilanz %.1e; %.0f s Rechnung", w0, weg, 100 * (w0 / weg - 1), bilanzW[0], (System.nanoTime() - n0) / 1e9), "±10 % [G]; < 1 · 10⁻⁵");
        if (will(4)) {
            A.pruefe(diff <= 0.02 * huegel, "Prüfstein 4: Sohle mit Faktor 10 über ein Zehntel der Zeit gegen Faktor 1", f("im Mittel %.2e m (%.2f %% der Hügelhöhe); Schwerpunkt %+.2f m gegen %+.2f m", diff, 100 * diff / huegel, w1, w0),
                    "≤ 2 % der Hügelhöhe [G]");
            A.pruefe(bilanzS[0] <= 1e-6 && bilanzS[1] <= 1e-6, "Prüfstein 4: Massenbilanz des Sands (wirklich aufgenommen gegen den Fluss über den Rand), Faktor 1 und 10", f("%.1e und %.1e", bilanzS[0], bilanzS[1]), "≤ 1 · 10⁻⁶");
        }
    }

    /** Schwerpunkt längs x der Erhebung über dem flachen Boden (m). */
    static double schwerpunkt(double[] z, double[] flach, int nx, int ny, double dx) {
        double m = 0, mx = 0;
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            double d = z[i + nx * j] - flach[i + nx * j];
            m += d;
            mx += d * (i + 0.5) * dx;
        }
        return mx / m;
    }

    // ------------------------------------------------------------------ 8

    private static void karte() throws Exception {
        A.teil("8 Karte gegen Prozessor am Bus, mit Rückwirkung");
        String[] t = fenster.split(",");
        int[] f3 = {Integer.parseInt(t[0].trim()), Integer.parseInt(t[1].trim()), Integer.parseInt(t[2].trim()), Integer.parseInt(t[3].trim())};
        double[][][] erg = new double[2][][];
        double[] sek = new double[2];
        String wo = "";
        for (int k = 0; k < 2; k++) {
            Lauf.Einstellung e = new Lauf.Einstellung();
            e.gebiet = new java.io.File(gebiet);
            e.start = java.time.Instant.parse(startK);
            e.stufen(BildCheck.stufen);
            e.karte = k == 1;
            e.fenster3 = f3;
            e.anlauf = 3600;
            e.sediment = k == 1 ? Lauf.Einstellung.Sediment.KARTE : Lauf.Einstellung.Sediment.PROZESSOR;
            e.sedimentAb = 3600;
            e.morphFaktor = faktorK;
            long n0 = System.nanoTime();
            try (Lauf l = Lauf.bauen(e)) {
                l.bis(stundenK * 3600);
                Sediment s = l.sediment.sediment;
                double[] c = new double[s.nc], dz = new double[s.nc];
                for (int z = 0; z < s.nc; z++) { c[z] = s.cs[z] + s.cm[z]; dz[z] = s.dzS[z] + s.dzM[z]; }
                erg[k] = new double[][] {c, dz, s.mAuflage.clone()};
                if (k == 1) wo = l.rechnung.geraet() + "; " + l.sediment;
                A.info(k == 0 ? "Prozessor" : "Karte", f("%s; Bilanz Sand %.1e, Schlick %.1e; hydro hat %d Zellstände neu gesetzt; %.0f s", l, l.sediment.bilanz()[0], l.sediment.bilanz()[1], l.hydro.bodenZellen,
                        (System.nanoTime() - n0) / 1e9));
            }
            sek[k] = (System.nanoTime() - n0) / 1e9;
        }
        String[] name = {"sed.c (kg/m³)", "Bodenänderung (m)", "lockere Auflage (kg/m²)"};
        double groesst = 0;
        StringBuilder text = new StringBuilder();
        for (int q = 0; q < 3; q++) {
            double sd = 0, sr = 0;
            for (int z = 0; z < erg[0][q].length; z++) { sd += Math.abs(erg[1][q][z] - erg[0][q][z]); sr += Math.abs(erg[0][q][z]); }
            double rel = sr > 0 ? sd / sr : 0;
            groesst = Math.max(groesst, rel);
            text.append(q > 0 ? "; " : "").append(f("%s %.1e (Mittel des Betrags %.3g)", name[q], rel, sr / erg[0][q].length));
        }
        A.pruefe(groesst <= 1e-3, f("Prüfstein 8: Karte gegen Prozessor nach %.0f h am Bus (Fenster %s, Faktor %.0f), Summe der Unterschiede durch Summe der Beträge", stundenK, fenster, faktorK), text.toString(),
                "≤ 1 · 10⁻³ [G]");
        A.info("Gerät", wo);
    }

    // ------------------------------------------------------------------ 5

    private static void auflage() {
        A.teil("5 Lockere Auflage");
        // (a) Absetzen in ruhendem Wasser
        Sediment s = new Sediment(3, 3, 20, 20);
        s.mitSchweb = false; s.mitGeschiebe = false; s.mitRippel = false; s.mitRutsch = false; s.mitAuflage = true;
        double h = 2, c0 = 0.1;
        Arrays.fill(s.h, h);
        Arrays.fill(s.cm, c0);
        for (int k = 0; k < 720; k++) s.schritt(5);
        double k1 = s.wsSchlick / h, soll = c0 * Math.exp(-k1 * 3600), ab = (c0 - s.cm[4]) * h;
        double[] w = s.wirklich(), m = s.masse();
        double a = 9 * 400.0, rest = Math.abs(m[2] + w[1] + w[2] - 9 * 400 * h * c0) / (9 * 400 * h * c0);
        A.pruefe(Math.abs(s.cm[4] / soll - 1) <= 0.02 && Math.abs(s.mAuflage[4] / ((1 - s.anteilTief) * ab) - 1) <= 0.02 && rest <= 1e-6,
                "Prüfstein 5: Absetzen in ruhendem Wasser über 1 h (2 m, 0,1 kg/m³): Konzentration gegen c₀ e^(−w t/h); Auflage gegen (1 − D) des Abgesetzten; Masse",
                f("%.5f gegen %.5f kg/m³ (%+.2f %%); Auflage %.5f gegen %.5f kg/m² (%+.2f %%), Mischschicht %+.5f kg/m²; Masse %.1e", s.cm[4], soll, 100 * (s.cm[4] / soll - 1), s.mAuflage[4], (1 - s.anteilTief) * ab,
                        100 * (s.mAuflage[4] / ((1 - s.anteilTief) * ab) - 1), w[1] / a, rest), "±2 % [G]; ≤ 1 · 10⁻⁶");
        // (b) Wiederaufnahme im Strom über der Schwelle
        s = new Sediment(3, 3, 20, 20);
        s.mitSchweb = false; s.mitGeschiebe = false; s.mitRippel = false; s.mitRutsch = false; s.mitAuflage = true; s.mitDiffusion = false;
        double u = 0.45, m0 = 0.2;
        Arrays.fill(s.h, h);
        Arrays.fill(s.qx, u * h);
        Arrays.fill(s.mAuflage, m0);
        for (int k = 0; k < 720; k++) s.schritt(5);
        double tx = s.tauMax[4], rate = s.mA1 * (tx / s.tauA - 1), sollM = m0 * Math.exp(-rate * 3600);
        w = s.wirklich();
        m = s.masse();
        rest = Math.abs(m[2] + w[1] + w[2] + s.aus[1] - a * m0) / (a * m0);
        A.pruefe(tx > s.tauD && m0 * s.mA1 < s.mA0 && Math.abs(s.mAuflage[4] / sollM - 1) <= 0.02 && rest <= 1e-6,
                "Prüfstein 5: Wiederaufnahme der Auflage über 1 h im Strom (τ über τ_a und τ_d): Auflage gegen m₀ e^(−M₁ (τ/τ_a − 1) t); Masse",
                f("τ = %.3f N/m² (τ_a = %.2f); Auflage %.5f gegen %.5f kg/m² (%+.2f %%); Schlick im Wasser %.4f kg/m³; Masse %.1e", tx, s.tauA, s.mAuflage[4], sollM, 100 * (s.mAuflage[4] / sollM - 1), s.cm[4], rest),
                "±2 % [G]; ≤ 1 · 10⁻⁶");
    }

    // ------------------------------------------------------------------ 6

    private static void spirale() {
        A.teil("6 Spirale");
        int nx = 81, ny = 81;
        double dx = 20, om = 1e-3;
        float[] tiefe = new float[nx * ny], u = new float[nx * ny], v = new float[nx * ny], sp = new float[nx * ny];
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            double x = (i - 40) * dx, y = (j - 40) * dx;
            tiefe[i + nx * j] = 4;
            u[i + nx * j] = (float) (-om * y);
            v[i + nx * j] = (float) (om * x);
        }
        HydroModul.spirale(tiefe, u, v, nx, ny, dx, dx, sp);
        double f1 = 0;
        int n = 0;
        for (int j = 1; j < ny - 1; j++) for (int i = 1; i < nx - 1; i++) {
            double r = Math.hypot(i - 40, j - 40) * dx, uu = om * r;
            if (r < 200) continue;
            f1 = Math.max(f1, Math.abs(sp[i + nx * j] / (4 * uu / r) - 1));
            n++;
        }
        A.pruefe(f1 <= 0.02, "Prüfstein 6: water.spiral einer Kreisströmung (links herum) gegen h |u| / R, ab 10 Zellen vom Mittelpunkt", f("größte Abweichung %.2e in %d Zellen; Vorzeichen positiv", f1, n), "±2 % [G]");
        Sediment s = new Sediment(3, 3, 20, 20);
        s.mitSchweb = false; s.mitSchlick = false; s.mitRippel = false; s.mitRutsch = false;
        Arrays.fill(s.h, 4);
        Arrays.fill(s.qx, 0.8 * 4);
        s.spiral = new double[s.nc];
        double hR = 4.0 / 500;
        Arrays.fill(s.spiral, 4 * 0.8 / 500);
        s.teil(1);
        double[] g = s.geschiebeVektor(4);
        double al = Sediment.alphaSpirale(Sediment.cdStrom(4, 2e-4 / 12), 1), soll = Math.atan(al * hR), ist = Math.atan2(g[1], g[0]);
        A.pruefe(g[0] > 0 && Math.abs(ist / soll - 1) <= 0.02, "Prüfstein 6: Ablenkung des Geschiebes bei h/R = 0,008 (Strom nach Osten, Krümmung nach links): Winkel gegen atan(α_I h/R)",
                f("%.3f° gegen %.3f° nach links; α_I = %.2f; Betrag %.4f kg/(s m) (ohne Spirale %.4f)", Math.toDegrees(ist), Math.toDegrees(soll), al, Math.hypot(g[0], g[1]), s.geschiebe(4)), "±2 % [G]");
    }

    // ------------------------------------------------------------------ 7

    private static void rauheit() {
        A.teil("7 Rauheit aus dem Sediment");
        int nx = 160, ny = 3;
        double dx = 10, q = 2.4, n = 0.020;
        double[] boden = new double[nx * ny];
        Arrays.fill(boden, -3);
        Hydro h = gerinne(nx, ny, dx, boden, q, 1800);
        double[] nn = new double[h.zellen];
        Arrays.fill(nn, n);
        int geaendert = h.rauheit(nn);
        double fw = 0;
        for (int e = 0; e < h.kanten; e++) fw = Math.max(fw, Math.abs(h.weite(e) / (h.laenge(e) / n) - 1));
        A.pruefe(fw <= 1e-12 && geaendert == h.kanten, "Prüfstein 7: Σ Δs/n der Kanten aus n = 0,020 gegen Länge / n", f("größte Abweichung %.1e; %d von %d Kanten geändert (Packung: n = %.3f)", fw, geaendert, h.kanten,
                h.laenge(1) / h.weitePackung(1)), "±1 % (hier Rundung)");
        double z0 = 0.0017;
        A.info("Strickler", f("z0 = %.1f mm (Rippel 15 mm hoch, 130 mm lang) gibt n = %.4f; z0 = d50/12 für 0,2 mm gibt n = %.4f (im Lauf wenigstens 0,015)", 1000 * z0, HydroModul.manning(z0), HydroModul.manning(2e-4 / 12)));
        h.lauf(6 * 3600, Double.POSITIVE_INFINITY);
        int a = 40 + nx, b = 120 + nx, m = 80 + nx;
        double gef = -(h.eta[b] - h.eta[a]) / ((120 - 40) * dx), tiefe = h.eta[m] + 3, u = h.geschwindigkeit(m)[0], fr2 = u * u / (h.g * tiefe);
        double soll = n * n * u * u / Math.pow(tiefe, 4.0 / 3), ist = gef * (1 - fr2);
        A.pruefe(Math.abs(ist / soll - 1) <= 0.02, "Prüfstein 7: Reibungsgefälle im ebenen Gerinne (Spiegelgefälle mal 1 − Fr²) gegen n² u² / h^(4/3)",
                f("%.4e gegen %.4e (%+.2f %%); h = %.3f m, u = %.3f m/s", ist, soll, 100 * (ist / soll - 1), tiefe, u), "±2 % [G]");
    }
}
