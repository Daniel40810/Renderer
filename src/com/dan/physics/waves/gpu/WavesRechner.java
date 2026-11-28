package com.dan.physics.waves.gpu;

import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.physics.waves.Waves;
import com.dan.physics.waves.WavesModul;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Der Seegang am Bus auf der Karte (P8b): je Gitter eine {@link WavesKarte} auf einem eigenen Gerät. Das Spektrum
 * bleibt zwischen den Intervallen auf der Karte; herunter kommt es einmal je Intervall für die Kennwerte und die
 * Schachtel.
 */
public final class WavesRechner implements WavesModul.Rechner {
    private final Geraet g;
    private final Konto konto;
    private final Map<Waves, WavesKarte> karten = new IdentityHashMap<>();

    public WavesRechner() {
        g = new Geraet();
        konto = g.konto("waves", 4L << 30);
    }

    @Override public void einrichten(Waves w) { karten.put(w, new WavesKarte(g, konto, w, null, 15, false)); }

    private WavesKarte k(Waves w) {
        WavesKarte k = karten.get(w);
        if (k == null) throw new IllegalStateException("WavesRechner: Gitter nicht eingerichtet");
        return k;
    }

    @Override public void felder(Waves w) { k(w).felder(); }

    @Override public void rand(Waves w, double[] feld) { k(w).rand(feld); }

    @Override public void schritt(Waves w, double dt) { k(w).schritt(dt); }

    @Override public void hoch(Waves w) { k(w).hoch(); }

    @Override public void herunter(Waves w) {
        WavesKarte k = k(w);
        k.runter();
        k.diss();
    }

    @Override public String geraet() { return "Karte (float)"; }

    @Override public double[] stoppuhr(Waves w) {
        WavesKarte k = karten.get(w);
        return k == null ? null : new double[] {k.sAusbreitung, k.sQuellen, k.nTeil, k.nSchritte};
    }

    @Override public void close() {
        for (WavesKarte k : karten.values()) k.close();
        karten.clear();
        g.close();
    }
}
