package com.dan.core.demo;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;

import java.util.SplittableRandom;

/**
 * Prüfstücke für {@link CoreCheck} und {@link CoreBild}: ein Stück Watt als feines Gelände und drei kleine Module,
 * die den Bus und den Takt belasten. Sie rechnen keine Physik; ihre Felder sind so gewählt, dass das richtige
 * Ergebnis bekannt ist.
 */
final class Probe {
    /** Rechengitter des Prüfstücks: 24 × 16 Zellen zu 20 m bei Hooge, darunter 1 m. */
    static final Gitter G = Gitter.utm("P", 32, 468_000, 6_046_000, 20, 24, 16);
    static final int FEIN = 20;

    /**
     * Ein Stück Watt, 480 × 320 Pixel zu 1 m, Höhen in m NHN auf Zentimeter gerundet (wie das DGM1): eine Wattfläche
     * um +0,3 m mit leichtem Gefälle und Rippeln, ein Priel mit 3 m Sohle auf −2,0 m und steilen Kanten (7 m breit),
     * der von West nach Ost mäandert, ein Nebenpriel mit 2 m Sohle, eine Hallig mit Sommerdeich und Warft.
     */
    static double[] watt(long seed) {
        int nx = G.nx * FEIN, ny = G.ny * FEIN;
        double[] z = new double[nx * ny];
        SplittableRandom r = new SplittableRandom(seed);
        double p1 = r.nextDouble() * 6.28, p2 = r.nextDouble() * 6.28;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                double x = i + 0.5, y = j + 0.5;
                double h = 0.30 + 0.0006 * (y - 160) + 0.03 * Math.sin(x * 0.9 + 0.3 * Math.sin(y * 0.21 + p1)) + 0.02 * (r.nextDouble() - 0.5);
                double mitte = 120 + 45 * Math.sin(x / 110 + p1) + 12 * Math.sin(x / 30 + p2), d = Math.abs(y - mitte);
                if (d < 3.5) h = Math.min(h, -2.0 + 2.3 * Math.max(0, d - 1.5) / 2.0);                  // Priel: Sohle 3 m, Kanten je 2 m
                double neben = 60 + 0.35 * x + 12 * Math.sin(x / 31 + p2), e = Math.abs(y - neben);
                if (x > 140 && y < mitte && e < 3) h = Math.min(h, -0.9 + 1.2 * Math.max(0, e - 1) / 2);   // Nebenpriel: Sohle 2 m
                double hx = (x - 330) / 95, hy = (y - 255) / 48, hallig = hx * hx + hy * hy;
                if (hallig < 1) {
                    h = Math.max(h, 1.35 + 0.25 * (1 - hallig));
                    if (hallig > 0.86) h = Math.max(h, 1.95);                                              // Sommerdeich
                    double wx = (x - 350) / 22, wy = (y - 262) / 16, warft = wx * wx + wy * wy;
                    if (warft < 1) h = Math.max(h, 1.6 + 2.9 * Math.min(1, 2.2 * (1 - warft)));            // Warft
                }
                z[i + nx * j] = Math.rint(h * 100) / 100;
            }
        return z;
    }

    /** Volumen (m³) über einer Zelle des feinen Geländes beim Wasserstand η: die Summe über die Pixel. */
    static double volumenFein(double[] z, int i, int j, double eta) {
        int nx = G.nx * FEIN;
        double v = 0;
        for (int q = 0; q < FEIN; q++)
            for (int p = 0; p < FEIN; p++) v += Math.max(0, eta - z[i * FEIN + p + nx * (j * FEIN + q)]);
        return v * (G.dx / FEIN) * (G.dy / FEIN);
    }

    /** Leitwert (m³/s) einer Kante quer zu x (zwischen Zelle i − 1 und i) aus den Pixeln, mit der Rauheit des Kerns. */
    static double leitwertFeinX(double[] z, int i, int j, double eta, double manning) {
        int nx = G.nx * FEIN;
        double k = 0;
        for (int q = 0; q < FEIN; q++) {
            int zeile = nx * (j * FEIN + q);
            double a = i > 0 ? z[i * FEIN - 1 + zeile] : Double.NaN, b = i < G.nx ? z[i * FEIN + zeile] : Double.NaN;
            double s = Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.max(a, b), h = eta - s;
            if (h > 0) k += G.dy / FEIN / manning * Math.pow(h, 5.0 / 3);
        }
        return k;
    }

    /** Mittlere benetzte Tiefe h_A an der Kante quer zu x (i, j), Summe über die Pixelpaare (P4, F25). */
    static double querschnittFeinX(double[] z, int i, int j, double eta) {
        int nx = G.nx * FEIN;
        double s = 0;
        for (int q = 0; q < FEIN; q++) {
            int zeile = nx * (j * FEIN + q);
            double a = i > 0 ? z[i * FEIN - 1 + zeile] : Double.NaN, b = i < G.nx ? z[i * FEIN + zeile] : Double.NaN;
            double t = Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.max(a, b), h = eta - t;
            if (h > 0) s += h;
        }
        return s / FEIN;
    }

    // ------------------------------------------------------------------ Module

    /**
     * Schreibt {@code water.eta}, {@code water.h} und den Fluss {@code water.q}; liest {@code tide.bc}. Der Wasserstand
     * ist in jeder Zelle gleich und folgt dem Randwert: η(t) = Randwert zur Startzeit des Intervalls + 0,001·t.
     * Der Fluss ist in jeder Kante q(t) = 2 + 0,01·t; gesammelt wird er in Schritten von 5 s.
     */
    static final class Hydro implements Modul {
        final Gitter g;
        final double kopplung;
        /** Rechenzeit je Intervall (ms), um eine langsame Physik zu spielen. */
        volatile double bremse;
        Feld rand;
        long schritte;

        Hydro(Gitter g, double kopplung) {
            this.g = g;
            this.kopplung = kopplung;
        }

        @Override public String name() { return "hydro"; }

        @Override public void anmelden(Anmeldung a) {
            a.kopplung(kopplung).liest("tide.bc", "m, m²/s", Ort.RAND, g)
                    .schreibt("water.eta", "m NHN", Ort.ZELLE, g, Feld.Zahl.DOUBLE).schreibt("water.h", "m", Ort.ZELLE, g, Feld.Zahl.FLOAT)
                    .schreibt("water.q", "m²/s", Ort.KANTE, g, Feld.Zahl.DOUBLE);
        }

        @Override public void beginn(double t0, Bus bus) {
            rand = bus.lesefeld(this, "tide.bc");
            bus.lies(this, "tide.bc", t0, rand);
            lege(bus, t0, rand.hol(0) + 0.001 * t0);
            bus.schreibfeld(this, "water.q").fuellen(2 + 0.01 * t0);
            bus.lege(this, "water.q", t0);
        }

        @Override public void schritt(double t0, double t1, Bus bus) {
            bus.lies(this, "tide.bc", t0, rand);
            for (double t = t0; t < t1 - 1e-9; t += 5) {
                double dt = Math.min(5, t1 - t);
                bus.schreibfeld(this, "water.q").fuellen(2 + 0.01 * (t + dt / 2));
                bus.sammle(this, "water.q", dt);
            }
            if (bremse > 0) {
                long ende = System.nanoTime() + (long) (bremse * 1e6);
                while (System.nanoTime() < ende) Thread.onSpinWait();
            }
            bus.lege(this, "water.q", t1);
            lege(bus, t1, rand.hol(0) + 0.001 * t1);
            schritte++;
        }

        private void lege(Bus bus, double t, double eta) {
            bus.schreibfeld(this, "water.eta").fuellen(eta);
            bus.lege(this, "water.eta", t);
            bus.schreibfeld(this, "water.h").fuellen(eta + 3);
            bus.lege(this, "water.h", t);
        }
    }

    /** Schreibt {@code tide.bc}: Wasserstand am Rand 0,5·sin(2π t / 44 714 s), Durchfluss 0. */
    static final class Tide implements Modul {
        final Gitter g;
        final double kopplung;

        Tide(Gitter g, double kopplung) {
            this.g = g;
            this.kopplung = kopplung;
        }

        @Override public String name() { return "tide"; }
        @Override public void anmelden(Anmeldung a) { a.kopplung(kopplung).schreibt("tide.bc", "m, m²/s", Ort.RAND, g, Feld.Zahl.DOUBLE); }
        @Override public void beginn(double t0, Bus bus) { lege(bus, t0); }
        @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }

        static double pegel(double t) { return 0.5 * Math.sin(2 * Math.PI * t / 44_714); }

        private void lege(Bus bus, double t) {
            Feld f = bus.schreibfeld(this, "tide.bc");
            for (int i = 0; i < f.laenge; i += 2) {
                f.setz(i, pegel(t));
                f.setz(i + 1, 0);
            }
            bus.lege(this, "tide.bc", t);
        }
    }

    /**
     * Liest {@code water.h} auf einem gröberen Gitter und schreibt {@code surf.wet}: ein Zufallsfeld aus dem
     * {@code seed}, gewichtet mit dem gelesenen Mittel – so hängt das Ergebnis von der Reihenfolge im Takt ab.
     */
    static final class Atmos implements Modul {
        final Gitter g;
        final double kopplung;
        final SplittableRandom zufall;
        Feld h;
        double pruefsumme;

        Atmos(Gitter g, double kopplung, long seed) {
            this.g = g;
            this.kopplung = kopplung;
            zufall = new SplittableRandom(seed);
        }

        @Override public String name() { return "atmos"; }

        @Override public void anmelden(Anmeldung a) {
            a.kopplung(kopplung).liest("water.h", "m", Ort.ZELLE, g).schreibt("surf.wet", "–", Ort.ZELLE, g, Feld.Zahl.FLOAT);
        }

        @Override public void beginn(double t0, Bus bus) {
            h = bus.lesefeld(this, "water.h");
            lege(bus, t0, t0);
        }

        @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t0, t1); }

        private void lege(Bus bus, double lesezeit, double t) {
            bus.lies(this, "water.h", lesezeit, h);
            double mittel = h.summe() / h.laenge;
            Feld f = bus.schreibfeld(this, "surf.wet");
            for (int i = 0; i < f.laenge; i++) f.setz(i, zufall.nextDouble() * mittel / 4);
            pruefsumme += f.summe();
            bus.lege(this, "surf.wet", t);
        }
    }

    /** Das Bild: liest {@code water.eta} und {@code surf.wet} zu beliebigen Zeiten, ohne Takt. */
    static final class Bild implements Modul {
        final Gitter g, grob;

        Bild(Gitter g, Gitter grob) {
            this.g = g;
            this.grob = grob;
        }

        @Override public String name() { return "render.water"; }
        @Override public void anmelden(Anmeldung a) { a.ohneTakt().liest("water.eta", "m NHN", Ort.ZELLE, g).liest("surf.wet", "–", Ort.ZELLE, grob); }
        @Override public void beginn(double t0, Bus bus) { }
        @Override public void schritt(double t0, double t1, Bus bus) { }
    }

    private Probe() { }
}
