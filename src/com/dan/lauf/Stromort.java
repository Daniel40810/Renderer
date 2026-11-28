package com.dan.lauf;

import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Utm;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Der Strom am Ort für die Schwimmer (Seegang Werkbuch, S7b-4, Datei 89, A25): aus dem Nachlauf der Strömung, den das
 * Archiv hält ({@link Bild.Quelle#stroemung}, {@code water.u}, {@code water.v} auf G3). Je Zeit gilt der jüngste Stand
 * bis zu dieser Zeit; ein neuer Stand wird in einem eigenen Faden gelesen, bis dahin gilt der alte (die Bildschleife wartet
 * nie). Am Ort die Zelle, in der er liegt [G]; außerhalb des Gitters kein Strom.
 */
final class Stromort implements AutoCloseable {
    private final Bild.Quelle q;
    private final Gitter g;
    private final double[] zeiten;
    private volatile float[] u, v;
    private volatile double gelesen = Double.NaN;
    private volatile boolean liest;
    private final ExecutorService faden = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Strom am Ort");
        t.setDaemon(true);
        return t;
    });

    private Stromort(Bild.Quelle q, Gitter g) {
        this.q = q;
        this.g = g;
        this.zeiten = q.stroemungZeiten();
    }

    /** Für die Quelle eines Bilds; null, wenn sie keine Strömung hat. */
    static Stromort fuer(Bild.Quelle q) {
        if (q == null) return null;
        Gitter g = q.stroemungGitter();
        if (g == null || q.stroemungZeiten().length == 0) return null;
        return new Stromort(q, g);
    }

    /** Strom {nach Ost, nach Nord} (m/s) am Ort (UTM 32) zur Zeit ts der Szene, oder null (noch keiner oder außerhalb). */
    double[] am(double ts, double ost, double nord) {
        double tst = zeiten[0];
        for (double z : zeiten) if (z <= ts) tst = z;
        if (tst != gelesen && !liest) {
            liest = true;
            final double tz = tst;
            faden.execute(() -> {
                try {
                    float[] uu = new float[g.zellen()], vv = new float[g.zellen()];
                    if (q.stroemung(tz, uu, vv)) { u = uu; v = vv; }
                    gelesen = tz;
                } catch (RuntimeException e) {
                    gelesen = tz;
                } finally {
                    liest = false;
                }
            });
        }
        float[] uu = u, vv = v;
        if (uu == null) return null;
        double x = ost, y = nord;
        if (g.lage == Gitter.Lage.GEOGRAFISCH) {
            double[] ll = Utm.rueckwaerts(ost, nord, 32);
            x = ll[0];
            y = ll[1];
        }
        if (!g.enthaelt(x, y)) return null;
        int c = g.index(g.i(x), g.j(y));
        float a = uu[c], b = vv[c];
        if (Float.isNaN(a) || Float.isNaN(b)) return null;
        return new double[] {a, b};
    }

    @Override public void close() { faden.shutdownNow(); }
}
