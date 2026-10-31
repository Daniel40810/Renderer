package com.dan.rime.demo;

import com.dan.rime.Rime;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Wachstum, Richtung, Dichte, Raureif, Klareis, Tauen, Abfall; seit Brocken Phase 10 der
 * Fangkoeffizient nach Finstad (Grenzen, Verlauf), die Raten an Zweig und Mast, nasses Wachstum und Lasten.
 */
public final class RimeCheck {
    private static int bad;

    public static void main(String[] args) {
        Rime r = new Rime();
        Rime.Ansatz mast = new Rime.Ansatz(0.05, true);   // ein Mast mit Kanten und Schrauben: rau
        Rime.Lage lage = Rime.raueislage();
        r.step(mast, lage, 6 * 3600);
        check(mast.art == Rime.Art.RAUEIS && mast.dicke > 0.01, "Raueislage: in 6 h wächst an einem rauen Mast Raueis über 1 cm (" + mast + ")");
        check(mast.richtung == lage.fromDeg, "die Fahne zeigt gegen den Wind");
        check(mast.dichte >= 100 && mast.dichte <= 917, "Dichte nach Macklin in 100..917 kg/m³");
        Rime.Ansatz a = new Rime.Ansatz(0.05), b = new Rime.Ansatz(0.05);
        Rime.Lage schwach = Rime.raueislage(), stark = Rime.raueislage();
        schwach.wind = 4;
        stark.wind = 16;
        r.step(a, schwach, 3600);
        r.step(b, stark, 3600);
        check(b.masse > a.masse, "mehr Wind, mehr Eis");
        Rime.Ansatz c = new Rime.Ansatz(0.05), d = new Rime.Ansatz(0.05);
        Rime.Lage duenn = Rime.raueislage(), dicht = Rime.raueislage();
        duenn.lwc = 0.1;
        dicht.lwc = 0.5;
        r.step(c, duenn, 3600);
        r.step(d, dicht, 3600);
        check(d.masse > c.masse, "mehr Flüssigwasser, mehr Eis");
        check(Rime.alpha1(15, 10, 0.005) > Rime.alpha1(15, 10, 0.5), "dünne Zweige fangen besser als dicke Stämme");
        check(Rime.alpha1(5, 10, 0.05) < Rime.alpha1(30, 10, 0.05), "große Tröpfchen fangen besser");
        check(Rime.macklin(15, 15, -2) > Rime.macklin(15, 3, -15), "nasser und windiger: dichteres Eis");
        Rime.Ansatz reif = new Rime.Ansatz(0.01);
        r.step(reif, Rime.klareNacht(), 10 * 3600);
        check(reif.art == Rime.Art.RAUREIF && Double.isNaN(reif.richtung) && reif.dicke > 0.001 && reif.dicke < 0.005, "klare Nacht: Raureif ohne Richtung, wenige mm");
        Rime.Ansatz glas = new Rime.Ansatz(0.05);
        r.step(glas, Rime.eisregen(), 3600);
        check(glas.art == Rime.Art.KLAREIS && glas.dichte > 850, "Eisregen: Klareis mit hoher Dichte");
        int[] fiel = new int[1];
        r.onAbfall = (x, kg) -> fiel[0]++;
        r.step(mast, Rime.tauwetter(), 6 * 3600);
        check(mast.dicke == 0 && mast.art == Rime.Art.KEINS, "Tauwetter: das Eis ist weg");
        check(fiel[0] >= 1, "beim Tauen fällt eine Fahne ab und wird gemeldet");
        Rime r1 = new Rime(), r2 = new Rime();
        r1.seed = r2.seed = 7;
        Rime.Ansatz x1 = new Rime.Ansatz(0.03), x2 = new Rime.Ansatz(0.03);
        r1.step(x1, lage, 7200);
        r2.step(x2, lage, 7200);
        r1.step(x1, Rime.tauwetter(), 1800);
        r2.step(x2, Rime.tauwetter(), 1800);
        check(x1.dicke == x2.dicke, "gleicher seed, gleicher Verlauf");
        // seit Brocken Phase 10
        boolean steigt = true;
        double vor = -1;
        for (double k = 0.2; k < 40; k *= 1.3) {
            double dk = Rime.RHO_WASSER * 20e-6 * 20e-6 * 12 / (9 * Rime.MU_LUFT * k);   // Durchmesser zu dieser Stokes-Zahl
            double e = Rime.alpha1(20, 12, dk);
            steigt &= e >= vor - 1e-9;
            vor = e;
        }
        check(steigt && Rime.alpha1(20, 12, 5) == 0 && Rime.alpha1(20, 30, 0.0005) > 0.9,
                String.format(java.util.Locale.GERMAN, "α1 nach Finstad: steigt mit der Stokes-Zahl, 0 unter K = 0,17, %.2f am feinen Zweig im Sturm", Rime.alpha1(20, 30, 0.0005)));
        Rime.Lage rl = Rime.raueislage();
        rl.tropfenUm = 20;
        double zweig = Rime.mmJeStunde(0.004, rl), glatt = Rime.mmJeStunde(0.3, rl), rau = Rime.mmJeStunde(0.3, true, rl);
        check(zweig > 4 && zweig < 30 && glatt < 0.5 && rau > 2, String.format(java.util.Locale.GERMAN,
                "Raueislage (−6 °C, 0,3 g/m³, 12 m/s, 20 µm): Zweig 4 mm %.1f mm/h, glatter Mast 30 cm %.1f mm/h, rauer Mast %.1f mm/h", zweig, glatt, rau));
        Rime.Ansatz nass = new Rime.Ansatz(0.01);
        Rime.Lage warm = Rime.raueislage();
        warm.tempC = -0.5; warm.lwc = 0.8; warm.wind = 15;
        r.step(nass, warm, 3600);
        check(nass.art == Rime.Art.KLAREIS && Rime.alpha3(-0.5, 0.004) < 0.8 && Rime.alpha3(-6, 0.004) == 1,
                String.format(java.util.Locale.GERMAN, "nahe 0 °C nasses Wachstum: α3 %.2f, Klareis (%s)", Rime.alpha3(-0.5, 0.004), nass));
        Rime.Ansatz ast = new Rime.Ansatz(0.004, true);
        r.step(ast, rl, 12 * 3600);
        double last = Rime.baumLast(ast, 60);
        check(last > 10 && last < 2000, String.format(java.util.Locale.GERMAN, "Last an einer Krüppelfichte (60 m Zweige) nach 12 h: %.0f kg (%s)", last, ast));
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private RimeCheck() { }
}
