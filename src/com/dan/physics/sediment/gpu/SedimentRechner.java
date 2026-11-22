package com.dan.physics.sediment.gpu;

import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.physics.sediment.Sediment;
import com.dan.physics.sediment.SedimentModul;

/**
 * Das Sediment am Bus auf der Karte (P9b): eine {@link SedimentKarte} auf einem eigenen Gerät. Der Zustand (Schwebstoff,
 * Boden, Rippel) bleibt zwischen den Intervallen auf der Karte; je Intervall geht der Eingang hinauf (Tiefe, Flüsse,
 * Strom, Seegang) und der Zustand für die Felder am Bus herunter.
 */
public final class SedimentRechner implements SedimentModul.Rechner {
    private final Geraet g;
    private final Konto konto;
    private SedimentKarte karte;

    public SedimentRechner() {
        g = new Geraet();
        konto = g.konto("sediment", 1L << 30);
    }

    @Override public void einrichten(Sediment s) {
        if (karte != null) throw new IllegalStateException("SedimentRechner: schon eingerichtet");
        karte = new SedimentKarte(g, konto, s, false);
        karte.hoch();
    }

    @Override public int schritt(Sediment s, double dt) {
        if (karte == null || karte.s != s) throw new IllegalStateException("SedimentRechner: nicht eingerichtet");
        karte.eingang();
        int n = karte.schritt(dt);
        karte.runter();
        return n;
    }

    @Override public String geraet() { return "Karte (float)"; }

    @Override public void close() {
        if (karte != null) karte.close();
        karte = null;
        g.close();
    }
}
