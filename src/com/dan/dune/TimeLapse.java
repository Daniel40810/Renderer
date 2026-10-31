package com.dan.dune;

import java.util.Locale;

/**
 * Der Zeitraffer im eigenen Faden (seit Sylt Phase 9, F12 A): {@link Werner} rechnet {@link #yearsPerSecond} Jahre je
 * Sekunde, die Darstellung liest {@link #snapshot()} – eine Kopie von Oberfläche, Strandhafer und Übersandung, die zum
 * Zeitpunkt der Kopie zusammenpasst. In Echtzeit gibt es nur Rippel und Fahnen ({@link Ripples}, {@link Grains}).
 */
public final class TimeLapse {
    /**
     * Kopie für die Darstellung: Oberfläche (m), dieselbe geglättet (zweimal 3 × 3, ohne die Stufen der Platten),
     * Bedeckung, Übersandung (m), Jahre, Sand (Platten).
     */
    public record Snapshot(float[] surface, float[] smooth, float[] veg, float[] burial, double years, long sand) { }

    public final Werner model;
    public volatile double yearsPerSecond = 0.5;
    private volatile Snapshot snap;
    private volatile boolean running;
    private Thread thread;

    public TimeLapse(Werner w) {
        model = w;
        snap = copy();
    }

    /** Startet den Faden (Daemon); läuft, bis {@link #stop}. */
    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "dune-zeitraffer");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            thread = null;
        }
    }

    public boolean running() { return running; }

    private void loop() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double dt = Math.min(0.25, (now - last) / 1e9);
            last = now;
            synchronized (model) { model.advanceYears(dt * yearsPerSecond); }
            snap = copy();
            try { Thread.sleep(15); } catch (InterruptedException e) { return; }
        }
    }

    /** Rechnet ohne Faden um so viele Jahre weiter (für Standbilder und Prüfung). */
    public void advance(double years) {
        synchronized (model) { model.advanceYears(years); }
        snap = copy();
    }

    private Snapshot copy() {
        SandBed b = model.bed;
        float[] s = new float[b.base.length];
        for (int k = 0; k < s.length; k++) s[k] = (float) b.surface(k);
        float[] m = s.clone(), tmp = new float[s.length];
        for (int pass = 0; pass < 2; pass++) {
            for (int j = 0; j < b.ny; j++)
                for (int i = 0; i < b.nx; i++) {
                    double sum = 0;
                    int c = 0;
                    for (int dj = -1; dj <= 1; dj++)
                        for (int di = -1; di <= 1; di++) {
                            int ii = i + di, jj = j + dj;
                            if (ii < 0 || jj < 0 || ii >= b.nx || jj >= b.ny) continue;
                            sum += m[jj * b.nx + ii];
                            c++;
                        }
                    tmp[j * b.nx + i] = (float) (sum / c);
                }
            float[] t = m;
            m = tmp;
            tmp = t;
        }
        return new Snapshot(s, m, b.veg.clone(), b.burial.clone(), b.years, b.total());
    }

    public Snapshot snapshot() { return snap; }

    /** Die geglättete Oberfläche der letzten Kopie als Grund (für {@link FluxField} und das Bild neben dem rechnenden Faden). */
    public Dune.Ground ground() {
        Snapshot s = snap;
        SandBed b = model.bed;
        return (x, z) -> {
            double[] l = b.local(x, z);
            double u = Math.max(0, Math.min(b.nx - 1, l[0])), v = Math.max(0, Math.min(b.ny - 1, l[1]));
            int i = Math.min(b.nx - 2, (int) u), j = Math.min(Math.max(0, b.ny - 2), (int) v), j1 = Math.min(b.ny - 1, j + 1);
            double fu = u - i, fv = b.ny == 1 ? 0 : v - j;
            float[] h = s.smooth();
            double a = h[j * b.nx + i] * (1 - fu) + h[j * b.nx + i + 1] * fu, c = h[j1 * b.nx + i] * (1 - fu) + h[j1 * b.nx + i + 1] * fu;
            return a * (1 - fv) + c * fv;
        };
    }

    /** Der Strandhafer der letzten Kopie als Bewuchs λ. */
    public Dune.Cover cover() {
        Snapshot s = snap;
        SandBed b = model.bed;
        return (x, z) -> {
            int k = b.nearest(x, z);
            if (k < 0) return 0;
            double ex = Math.max(0, Math.min(1, 1 - s.burial()[k] / b.marramHeight));
            return b.marramLambda * s.veg()[k] * ex;
        };
    }

    /** „Zeitraffer: 0,5 Jahre/s, 3,2 Jahre“ */
    public String describe() {
        Snapshot s = snap;
        return String.format(Locale.GERMAN, "Zeitraffer: %.2g Jahre/s, %.1f Jahre%s", yearsPerSecond, s.years(), running ? "" : " (angehalten)");
    }
}
