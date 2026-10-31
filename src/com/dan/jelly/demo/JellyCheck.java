package com.dan.jelly.demo;

import com.dan.jelly.Bloom;

import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10): Jahreszeit, Größe, Schlag, Trift im Wind, Tiefe bei Seegang,
 * Strandung, ehrliche Dichte, Kachel um den Blick, Rechenzeit. Endet mit „Alles in Ordnung.“ oder mit den Verstößen.
 */
public final class JellyCheck {
    private static int bad;

    public static void main(String[] args) {
        // seed
        Bloom a = Bloom.summer(), b = Bloom.summer();
        a.seed = b.seed = 7;
        run(a, 120, 0.1);
        run(b, 120, 0.1);
        check(a.all()[5].x == b.all()[5].x && a.all()[17].z == b.all()[17].z, "gleicher seed, gleiche Quallen");
        b.seed = 8;
        run(b, 120, 0.1);
        check(a.all()[5].x != b.all()[5].x, "anderer seed, andere Quallen");

        // Jahreszeit
        Bloom s = Bloom.summer();
        s.dayOfYear = 20;
        double jan = s.seasonFactor();
        s.dayOfYear = 90;
        double mar = s.seasonFactor();
        s.dayOfYear = 240;
        double aug = s.seasonFactor();
        s.dayOfYear = 350;
        double dec = s.seasonFactor();
        check(jan == 0 && mar == 0 && dec == 0 && aug > 0.9, f("Jahr: Januar %.2f, März %.2f, Ende August %.2f, Dezember %.2f", jan, mar, aug, dec));
        s.dayOfYear = 240;
        s.waterTemp = 5;
        check(s.seasonFactor() == 0, "unter 6 °C Wasser keine Medusen");
        s.waterTemp = 18;
        s.dayOfYear = 140;
        double may = s.meanDiameter();
        s.dayOfYear = 235;
        double augD = s.meanDiameter();
        check(may < 0.07 && augD > 0.17 && augD < 0.26, f("Schirm im Mai %.0f cm, im August %.0f cm", may * 100, augD * 100));

        // Schlag
        check(Bloom.pulseRate(0.05, 16) > Bloom.pulseRate(0.2, 16), f("kleine schlagen schneller: 5 cm %.2f/s, 20 cm %.2f/s", Bloom.pulseRate(0.05, 16), Bloom.pulseRate(0.2, 16)));
        check(Bloom.pulseRate(0.15, 22) > Bloom.pulseRate(0.15, 12), "Wärme beschleunigt den Schlag");
        check(Bloom.contraction(0) < 1e-9 && Math.abs(Bloom.contraction(0.3) - 1) < 1e-9 && Bloom.contraction(0.6) < Bloom.contraction(0.4)
                && Bloom.contraction(0.999) < 0.01, "Schlag: schnell zusammen, langsam auf");

        // Presets
        Bloom none = Bloom.none();
        run(none, 10, 0.1);
        check(none.visibleCount() == 0, "none(): keine Qualle");
        check(Bloom.few().density < Bloom.summer().density && Bloom.summer().density < Bloom.bloom().density, "Presets steigen von few bis bloom");
        check(Bloom.beaufort(-2).windSpeed == Bloom.beaufort(0).windSpeed && Bloom.beaufort(40).windSpeed == Bloom.beaufort(12).windSpeed, "beaufort(n) begrenzt auf 0..12");
        Bloom ruhig = Bloom.beaufort(1), rau = Bloom.beaufort(6);
        ruhig.seed = rau.seed = 3;
        run(ruhig, 600, 1);
        run(rau, 600, 1);
        check(meanDepth(rau) > meanDepth(ruhig) + 0.5, f("bei Seegang tiefer: Bft 1 %.2f m, Bft 6 %.2f m", meanDepth(ruhig), meanDepth(rau)));
        check(ruhig.seen(ruhig.visibility) > 0.49 && ruhig.seen(ruhig.visibility) < 0.51, "in der Sichttiefe sieht man die Hälfte");

        // Trift: Westwind 5 m/s treibt nach Osten, rund 15 cm/s
        Bloom w = Bloom.summer();
        w.windSpeed = 5;
        w.windFromDeg = 270;
        w.quality = Bloom.Quality.ULTRA;
        w.seed = 11;
        w.step(0.1, 0, 0);
        int n = w.all().length;
        double[] x0 = new double[n], z0 = new double[n];
        for (int i = 0; i < n; i++) { x0[i] = w.all()[i].x; z0[i] = w.all()[i].z; }
        for (int i = 0; i < 600; i++) w.step(0.1, 0, 0);
        double sx = 0, sz = 0;
        int m = 0;
        for (int i = 0; i < n; i++) {
            double dx = w.all()[i].x - x0[i], dz = w.all()[i].z - z0[i];
            if (Math.abs(dx) > w.tileSize() / 2) continue;   // herumgekommen
            sx += dx;
            sz += dz;
            m++;
        }
        sx /= m;
        sz /= m;
        check(sx > 6 && sx < 12 && Math.abs(sz) < 2, f("Westwind 5 m/s: in 60 s %.1f m nach Osten, %.1f m quer", sx, sz));

        // Strandung: Grund steigt nach Osten, Ufer bei x = 40 m, Westwind treibt sie hin
        Bloom st = Bloom.summer();
        st.seed = 5;
        st.windSpeed = 8;
        st.windFromDeg = 270;
        st.bed = (x, z) -> -2 + 0.05 * x;
        int[] calls = new int[1];
        st.onStranding = (x, z, d) -> calls[0]++;
        for (int i = 0; i < 6000; i++) st.step(0.5, 0, 0);
        boolean onBed = true, noneOnLand = true;
        for (Bloom.Jelly j : st.all()) {
            if (!st.visible(j)) continue;
            if (j.stranded() && Math.abs(j.y - (-2 + 0.05 * j.x) - 0.005) > 1e-6) onBed = false;
            if (!j.stranded() && -2 + 0.05 * j.x > -0.1) noneOnLand = false;
        }
        check(st.strandings() > 10 && calls[0] == st.strandings(), f("Westwind aufs Ufer: %d gestrandet, Schnittstelle %d", st.strandings(), calls[0]));
        check(onBed, "gestrandete liegen auf dem Grund");
        check(noneOnLand, "keine schwimmende Qualle über Land");

        // ehrliche Dichte, auch wenn die Stufe deckelt
        Bloom d = Bloom.bloom();
        d.quality = Bloom.Quality.LOW;
        d.seed = 9;
        run(d, 5, 0.1);
        double dens = d.visibleCount() / (d.tileSize() * d.tileSize());
        check(Math.abs(dens / (d.density * d.seasonFactor()) - 1) < 0.15, f("Dichte gehalten: %.2f je m² (soll %.2f), Kachel %.0f m", dens, d.density * d.seasonFactor(), d.tileSize()));

        // Kachel folgt dem Blick
        Bloom k = Bloom.summer();
        k.seed = 4;
        run(k, 10, 0.1);
        k.step(0.1, 1000, -500);
        boolean inside = true;
        for (Bloom.Jelly j : k.all()) if (Math.abs(j.x - 1000) > k.tileSize() || Math.abs(j.z + 500) > k.tileSize()) inside = false;
        check(inside, "Sprung des Blicks: das Feld zieht mit");
        check(k.countNear(1000, -500, 30) > 0, f("um den Blick: %d in 30 m", k.countNear(1000, -500, 30)));

        // Rechenzeit: 3 000 Tiere, 1 000 Schritte
        Bloom u = Bloom.bloom();
        u.quality = Bloom.Quality.ULTRA;
        u.step(0.01, 0, 0);
        long t0 = System.nanoTime();
        for (int i = 0; i < 1000; i++) u.step(1 / 60.0, 0, 0);
        double ms = (System.nanoTime() - t0) / 1e6 / 1000;
        check(ms < 1.0, f("Rechenzeit %.3f ms je Schritt für %d Tiere", ms, u.all().length));

        System.out.println(Bloom.summer().describe());
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void run(Bloom b, int steps, double dt) { for (int i = 0; i < steps; i++) b.step(dt, 0, 0); }

    static double meanDepth(Bloom b) {
        double s = 0;
        int n = 0;
        for (Bloom.Jelly j : b.all()) if (b.visible(j)) { s += j.depth; n++; }
        return n == 0 ? 0 : s / n;
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private JellyCheck() { }
}
