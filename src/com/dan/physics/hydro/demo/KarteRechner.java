package com.dan.physics.hydro.demo;

import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.hydro.gpu.HydroKarte;
import com.dan.physics.hydro.gpu.KetteKarte;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Prüfung auf der Karte (HydroCheck --karte): jedes {@code Hydro} bekommt beim Start eine {@link HydroKarte};
 * ein Schritt rechnet auf der Karte und holt den Stand zurück, damit die Fälle ihn lesen wie in der Referenz.
 */
final class KarteRechner implements HydroCheck.Rechner {
    final Geraet g;
    private final Konto konto;
    private final Map<Hydro, HydroKarte> karten = new IdentityHashMap<>();
    private final List<KetteKarte> ketten = new ArrayList<>();

    KarteRechner() {
        g = new Geraet();
        konto = g.konto("hydro", 8L << 30);
    }

    HydroKarte karte(Hydro h) {
        HydroKarte k = karten.get(h);
        if (k == null) karten.put(h, k = new HydroKarte(g, konto, h, 64));
        return k;
    }

    @Override public void start(Hydro h) {
        HydroKarte k = karten.get(h);
        if (k == null) karte(h);
        else k.hoch();
    }

    @Override public void schritt(Hydro h, double dt) {
        HydroKarte k = karte(h);
        k.schritte(1, dt);
        k.herunter();
    }

    @Override public double dtCfl(Hydro h) { return karte(h).dtCfl(); }

    @Override public HydroCheck.Schachtel kette(Hydro... h) {
        KetteKarte k = new KetteKarte(g, konto, h);
        ketten.add(k);
        return new HydroCheck.Schachtel() {
            public void schritt(double dt) {
                k.schritt(dt);
                k.herunter();
            }
            public double dtCfl() { return k.ebene[0].dtCfl(); }
            public double volumen() { return k.volumen(); }
            public double abgeschnitten() { return k.abgeschnitten(); }
            public long schritte(int e) { return k.schritte[e]; }
        };
    }

    /** Das Konto der Prüfung (für die Ketten in Fall 9, 10 und 14, die ihren Stand nicht je Schritt abgleichen). */
    Konto konto() { return konto; }

    @Override public boolean karte() { return true; }

    @Override public void ende() {
        for (HydroKarte k : karten.values()) k.close();
        karten.clear();
        for (KetteKarte k : ketten) k.close();
        ketten.clear();
    }

    @Override public String geraet() { return g.name + " (" + g.treiber + ")"; }

    void schliessen() {
        ende();
        g.close();
    }
}
