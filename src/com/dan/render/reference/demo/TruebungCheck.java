package com.dan.render.reference.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Pfadverfolger;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.Szene;

import java.nio.file.Path;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * P10d, S8 (Werkbuch HalligHooge Teil A, 12.11, Prüfstein 17 ohne Gebiet): die Trübung am Eintrittspunkt in der
 * Referenz ({@link Szene#truebung}). 1 Gibt die Szene überall den Schwebstoff der Szene zurück, rechnet die Referenz
 * bitgleich wie ohne (gleiche Saat, drei Kanäle und spektral). 2 Gibt sie überall den doppelten zurück, rechnet sie
 * bitgleich wie eine Szene, deren {@link Wasseroptik#schwebstoff} doppelt ist – die Trübung am Ort wirkt also genau wie
 * die der Szene. Glattes Wasser 3 m über einem Grund mit Albedo 0,3, Sonne 40°, Blick 45° hinab, mit Luft.
 * Attest {@code docs/status/P10_Truebung.md}.
 */
public final class TruebungCheck {
    private static final Attest A = new Attest("com.dan.render.reference", Referenz.VERSION);

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        System.out.println("TruebungCheck – Trübung am Eintrittspunkt in der Referenz (Werkbuch HalligHooge Teil A, P10d, S8)");
        try {
            pruefe();
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P10_Truebung.md"), "P10d – TruebungCheck (S8)");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Eine Szene, die alles von einer anderen nimmt und überall die Trübung c zurückgibt. */
    record Mit(Szene s, double c) implements Szene {
        @Override public boolean schneide(double[] p, double[] d, double tMax, Treffer aus) { return s.schneide(p, d, tMax, aus); }
        @Override public double augeHoehe() { return s.augeHoehe(); }
        @Override public double truebung(double[] p) { return c; }
    }

    static Referenz referenz(Szene sz, double schwebstoff, boolean spektral) {
        Wasseroptik w = new Wasseroptik();
        w.schwebstoff = schwebstoff;
        Referenz ref = new Referenz(sz, Atmosphere.forVisibility(30, 0.06), w, 0);
        ref.spektral = spektral;
        ref.sigma2 = 1e-8;
        ref.lichter.clear();
        double hs = Math.toRadians(40);
        ref.lichter.add(new Licht(new double[] {0, Math.sin(hs), -Math.cos(hs)}, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
        return ref;
    }

    /** Summe der Strahldichte je Bahn über n Pfade auf einem Faden, Blick 45° hinab. */
    static double[] summe(Referenz ref, int n) {
        Pfadverfolger pv = ref.verfolger(4711);
        double hv = Math.toRadians(45);
        double[] v = {0, -Math.sin(hv), Math.cos(hv)}, s = new double[pv.wl.n];
        for (int i = 0; i < n; i++) {
            pv.neueWellen();
            double[] L = pv.verfolge(new double[3], v);
            for (int k = 0; k < s.length; k++) s[k] += L[k];
        }
        return s;
    }

    static void pruefe() {
        double c0 = new Wasseroptik().schwebstoff;
        int n = 200_000;
        Ebenen e = new Ebenen(-3.5, -0.5, new double[] {0.3, 0.3, 0.3}, 10);
        A.teil("1 Überall der Schwebstoff der Szene: bitgleich");
        for (boolean sp : new boolean[] {false, true}) {
            double[] a = summe(referenz(e, c0, sp), n), b = summe(referenz(new Mit(e, c0), c0, sp), n);
            A.pruefe(java.util.Arrays.equals(a, b), (sp ? "spektral" : "drei Kanäle") + f(": Trübung am Ort = Schwebstoff der Szene (%.0f g/m³) gegen ohne, %d Pfade, gleiche Saat", c0, n),
                    f("Summe G %.6g gegen %.6g", a[Math.min(1, a.length - 1)], b[Math.min(1, b.length - 1)]), "bitgleich");
        }
        A.teil("2 Überall der doppelte: wie eine Szene mit doppeltem Schwebstoff");
        for (boolean sp : new boolean[] {false, true}) {
            double[] a = summe(referenz(e, 2 * c0, sp), n), b = summe(referenz(new Mit(e, 2 * c0), c0, sp), n), o = summe(referenz(e, c0, sp), n);
            A.pruefe(java.util.Arrays.equals(a, b), (sp ? "spektral" : "drei Kanäle") + f(": Trübung am Ort %.0f g/m³ in einer Szene mit %.0f gegen eine Szene mit %.0f", 2 * c0, c0, 2 * c0),
                    f("Summe G %.6g gegen %.6g (mit %.0f g/m³: %.6g)", b[Math.min(1, b.length - 1)], a[Math.min(1, a.length - 1)], c0, o[Math.min(1, o.length - 1)]), "bitgleich");
        }
    }
}
