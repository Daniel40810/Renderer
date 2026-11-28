package com.dan.physics.waves.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.waves.Waves;

import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Diagnose des Seegangs (Waves 0.3, Seegang Werkbuch S1, SF6 A), ohne Grafikkarte:
 * <ol>
 * <li>Anfangsspektrum ({@link Waves#jonswap}): m₀ genau Hs²/16, Tp und Richtung wie gesetzt, die Richtungsbreite gegen
 * √(2/(s+1)) (Kuik u. a. 1988 für cos^(2s)).</li>
 * <li>Windsee und Dünung ({@link Waves#partitionen}): eine Windsee mit dem Wind und eine Dünung gegen ihn, je als
 * JONSWAP gesetzt, kommen getrennt wieder heraus (Hs, Tp, Richtung); die Summe ist m₀; ohne Wind ist alles Dünung.</li>
 * <li>Senken ({@link Waves#dissipation}): je Senke allein (Schaumkronen, Brechen, Reibung) ein Schritt der Quellen
 * von 0,01 s in einer Zelle, deren Nachbarn und Rand dasselbe Spektrum halten (die Ausbreitung ändert nichts): Die Rate der
 * Diagnose gleich dem Verlust an m₀ im Schritt (± 1 %), Schaumkronen und Brechen dazu gleich {@code diss}.</li>
 * <li>Richtungen: woher und wohin.</li>
 * </ol>
 * Attest {@code docs/status/S1_Diagnose.md}.
 */
public final class DiagnoseCheck {
    static final Attest A = new Attest("com.dan.physics.waves (Diagnose)", Waves.VERSION);

    public static void main(String[] args) {
        try {
            anfang();
            trennung();
            senken();
            richtungen();
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "S1_Diagnose.md"), "S1 – Diagnose des Seegangs (Waves " + Waves.VERSION + ")");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Eine Zelle (3 × 3, die Mitte zählt), alle mit Tiefe d, ohne Strömung. */
    static Waves zelle(double d) {
        Waves w = new Waves(3, 3, 100, 100, 28, 0.04, 0.6, 36);
        java.util.Arrays.fill(w.tiefe, d);
        w.vorbereiten();
        return w;
    }

    static double winkel(double a, double b) { return Math.abs(Math.IEEEremainder(a - b, 360)); }

    static void anfang() {
        A.teil("1 Anfangsspektrum (JONSWAP, cos^2s)");
        Waves w = zelle(30);
        int c = 4;
        double hs = 1.7, tp = 6.5, r = 37, s = 10;
        w.jonswap(c, hs, tp, r, s);
        double m0 = w.m0(c);
        A.pruefe(Math.abs(m0 / (hs * hs / 16) - 1) <= 1e-12, "m₀ gegen Hs²/16", f("%.12f gegen %.12f", m0, hs * hs / 16), "relativ ≤ 1e-12");
        A.pruefe(Math.abs(w.tp(c) / tp - 1) <= 0.03, "Tp gegen die Vorgabe", f("%.3f s gegen %.3f s", w.tp(c), tp), "± 3 %");
        A.pruefe(winkel(w.richtung(c), r) <= 0.5, "mittlere Richtung", f("%.2f° gegen %.2f°", w.richtung(c), r), "± 0,5°");
        double[] p = w.partitionen(c);                                         // ohne Wind: alles Dünung
        double soll = Math.toDegrees(Math.sqrt(2 / (s + 1)));
        A.pruefe(Math.abs(p[7] - soll) <= 1.5, "Richtungsbreite σ_θ (Kuik) gegen √(2/(s+1)) für cos^(2s)", f("%.2f° gegen %.2f° (s = %.0f, 36 Richtungen)", p[7], soll, s), "± 1,5°");
        w.jonswap(c, 0, tp, r, s);
        A.pruefe(w.m0(c) == 0, "ohne Höhe leer", f("m₀ %.3g", w.m0(c)), "0");
    }

    static void trennung() {
        A.teil("2 Windsee und Dünung");
        Waves w = zelle(25);
        int c = 4;
        double[] sea = new double[w.nf * w.nd];
        w.jonswap(c, 1.5, 5.0, 0, 10);                                         // Windsee nach +x, mit dem Wind
        System.arraycopy(w.e, c * w.nf * w.nd, sea, 0, sea.length);
        w.jonswap(c, 1.0, 12.0, 180, 20);                                      // Dünung nach −x, gegen den Wind
        for (int i = 0; i < sea.length; i++) w.e[c * w.nf * w.nd + i] += sea[i];
        w.windU[c] = 15;
        w.windV[c] = 0;
        double[] p = w.partitionen(c);
        double hs = w.hs(c), sum = p[0] * p[0] + p[4] * p[4];
        A.info("Fall", f("Tiefe 25 m, Wind 15 m/s nach +x (u* nach Wu %.3f m/s); Windsee Hs 1,50 m, Tp 5,0 s nach 0°; Dünung Hs 1,00 m, Tp 12,0 s nach 180°", Waves.ustern(15)));
        A.info("Ergebnis", f("Windsee Hs %.3f m, Tp %.2f s, nach %.1f°, Breite %.1f°; Dünung Hs %.3f m, Tp %.2f s, nach %.1f°, Breite %.1f°", p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]));
        A.pruefe(Math.abs(sum / (hs * hs) - 1) <= 1e-12, "Summe der beiden m₀ gegen m₀ der Zelle", f("%.12f gegen %.12f m²", sum / 16, hs * hs / 16), "relativ ≤ 1e-12");
        A.pruefe(Math.abs(p[0] / 1.5 - 1) <= 0.03 && Math.abs(p[4] / 1.0 - 1) <= 0.03, "Hs der Windsee und der Dünung", f("%.3f und %.3f m gegen 1,5 und 1,0 m", p[0], p[4]), "± 3 %");
        A.pruefe(Math.abs(p[1] / 5.0 - 1) <= 0.05 && Math.abs(p[5] / 12.0 - 1) <= 0.05, "Tp der Windsee und der Dünung", f("%.2f und %.2f s gegen 5 und 12 s", p[1], p[5]), "± 5 %");
        A.pruefe(winkel(p[2], 0) <= 2 && winkel(p[6], 180) <= 2, "Richtungen", f("%.1f° und %.1f° gegen 0° und 180°", p[2], p[6]), "± 2°");
        w.windU[c] = 0;
        double[] q = w.partitionen(c);
        A.pruefe(q[0] == 0 && Math.abs(q[4] / hs - 1) <= 1e-12, "ohne Wind alles Dünung", f("Windsee %.3g m, Dünung %.4f m (Hs %.4f m)", q[0], q[4], hs), "0 und Hs");
        w.windU[c] = 15;
        w.uStern = new double[w.nc];
        w.uStern[c] = 0.3;                                                     // schwächerer Schub: die Windsee wird kleiner
        double[] r = w.partitionen(c);
        A.pruefe(r[0] <= p[0] + 1e-12, "u* aus dem Schub statt nach Wu (0,3 m/s): Windsee nicht größer", f("%.3f m gegen %.3f m", r[0], p[0]), "≤");
    }

    /**
     * Eine Senke allein: drei mal drei Zellen, alle mit demselben Spektrum, der Rand liefert es auch; ein Schritt von
     * 0,01 s, gemessen in der Mitte (kurz, weil der Schritt halb implizit ist: Mit 60 s verliert eine stark brechende Zelle
     * weniger als die Rate mal 60 s; die Rate auf dem Spektrum nach dem Schritt gibt dann den Verlust des Schritts).
     */
    static double[] schritt(double d, double hs, double tp, int senke) {
        Waves w = zelle(d);
        for (int c = 0; c < w.nc; c++) w.jonswap(c, hs, tp, 30, 10);
        int n = w.nf * w.nd;
        double[] spektrum = new double[n];
        System.arraycopy(w.e, 4 * n, spektrum, 0, n);
        Waves.Rand r = (i, j, m, q) -> spektrum[m * w.nd + q];
        w.randW = r; w.randO = r; w.randS = r; w.randN = r;
        w.mitWind = false; w.mitQuad = false; w.kappung = false;
        w.mitSchaum = senke == 0; w.mitBrechen = senke == 1; w.mitReibung = senke == 2;
        double[] rate = w.dissipation(4);
        double m0 = w.m0(4), dt = 0.01;                                  // kurz: der Schritt ist halb implizit, 1/(1 + g dt) ≈ 1 − g dt
        w.schritt(dt);
        double verlust = Waves.RHO_W * Waves.G * (m0 - w.m0(4)) / dt;
        return new double[] {rate[senke], verlust, w.diss[4], rate[0] + rate[1] + rate[2]};
    }

    static void senken() {
        A.teil("3 Senken: Rate der Diagnose gegen den Verlust in einem Schritt von 0,01 s");
        String[] namen = {"Schaumkronen (Tiefe 30 m, Hs 2,5 m, Tp 6 s)", "Brechen an der Tiefe (Tiefe 1,5 m, Hs 1,2 m, Tp 5 s)", "Bodenreibung (Tiefe 6 m, Hs 1,0 m, Tp 9 s)"};
        double[][] faelle = {{30, 2.5, 6}, {1.5, 1.2, 5}, {6, 1.0, 9}};
        for (int s = 0; s < 3; s++) {
            double[] x = schritt(faelle[s][0], faelle[s][1], faelle[s][2], s);
            A.pruefe(x[0] > 0 && Math.abs(x[0] / x[1] - 1) <= 0.01, namen[s] + ": Rate gegen Verlust an m₀", f("%.4g gegen %.4g W/m²", x[0], x[1]), "± 1 %");
            if (s < 2) A.pruefe(Math.abs(x[0] / x[2] - 1) <= 0.01, namen[s] + ": Rate gegen diss des Schritts", f("%.4g gegen %.4g W/m²", x[0], x[2]), "± 1 %");
            A.pruefe(Math.abs(x[3] - x[0]) <= 1e-12 * Math.max(1, x[0]), namen[s] + ": die anderen Senken aus", f("Summe %.4g, diese %.4g W/m²", x[3], x[0]), "gleich");
        }
        Waves tief = zelle(200);
        tief.jonswap(4, 2, 6, 0, 10);
        tief.mitSchaum = false;
        tief.mitBrechen = false;
        double[] r = tief.dissipation(4);
        A.pruefe(r[2] < 1e-9 && r[1] == 0, "Tiefe 200 m, Tp 6 s: keine Reibung, kein Brechen", f("Reibung %.3g, Brechen %.3g W/m²", r[2], r[1]), "0");
    }

    static void richtungen() {
        A.teil("4 Richtungen: woher und wohin");
        boolean ok = Waves.woher(0) == 270 && Waves.woher(90) == 180 && Waves.woher(270) == 0 && Waves.wohin(0) == 270 && Waves.wohin(270) == 0;
        double g = 0;
        for (double a = -720; a <= 720; a += 7.3) g = Math.max(g, winkel(Waves.wohin(Waves.woher(a)), a));
        A.pruefe(ok && g < 1e-9, "nach +x heißt aus West (270°), nach −y aus Nord (0°); wohin(woher(a)) = a", f("größte Abweichung %.2g°", g), "genau");
    }
}
