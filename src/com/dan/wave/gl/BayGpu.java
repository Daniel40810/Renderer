package com.dan.wave.gl;

import com.dan.wave.Bay;
import com.dan.wave.BayGrid;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link Bay} auf der Grafikkarte (seit LituyaBay1958 Phase 11b, Werkbuch 6.1): derselbe Löser in float als Compute
 * Shader, auch die Verschachtelung (Randzellen aus dem gröberen Gitter, Unterschritte, Rückgabe, Abgleich der Flüsse).
 * Der Prozessor baut die Bucht ({@link Bay#build}), {@link #upload} lädt sie hoch, danach rechnet nur noch die
 * Grafikkarte; {@link #sync} holt den Stand zurück in die Felder der Gitter, dann gelten alle Abfragen von {@link Bay}
 * (die Pegel lesen schon während der Rechnung im Takt der Bucht ab, je Pegel zwei kleine Rückfragen). Je Schritt eine Rückfrage: die größte Geschwindigkeit für den Zeitschritt.
 * <pre>
 *   Bay b = …; b.build(…);
 *   BayGpu g = new BayGpu(b);
 *   g.upload(gl);                        // im GL-Faden
 *   g.run(gl, 60);                       // oder je Bild g.step(gl)
 *   g.sync(gl);                          // dann b.runup(…), b.heightAt(…), b.volume() …
 *   g.dispose(gl);
 * </pre>
 * Shader: {@code bay_*.comp} als Ressourcen in diesem Package. Fels als Grund 1e30, nie nass als Höchststand −1e30.
 */
public final class BayGpu {
    private final Bay bay;
    private final List<Grid> grids = new ArrayList<>();
    private final Map<String, Integer> prog = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> loc = new HashMap<>();
    private double time;
    private long steps;
    private long busyNs;
    private double[] tp0, tp1;
    private double nextSample;
    private int[][] gaugeCells; // je Pegel {Gitter, Zelle} oder null
    /** Pegel der Bucht im Takt {@link Bay#sampleDt} ablesen (zwei kleine Rückfragen je Pegel und Takt). */
    public boolean gauges = true;
    /** Zeiten je Gitter über Abfragen der Grafikkarte messen (kostet wenig, aber etwas). */
    public boolean timing = true;
    private final List<int[]> pending = new ArrayList<>(); // {Abfrage, Gitter}
    private final List<Integer> pool = new ArrayList<>();

    static final class Grid {
        BayGrid g;
        int nx, nz, px, n, nf;
        int bed, a, s, old, flux, mflux, eta, stats, acc, aux, fcells, fvals, nforced;
        float[] fbuf;
        long gpuNs, cellSteps, gridSteps, syncedSteps, syncedNs;
        int[] place; // feineres Gitter in diesem
    }

    public BayGpu(Bay bay) {
        if (!bay.built()) throw new IllegalStateException("wave (GPU): build() fehlt");
        this.bay = bay;
    }

    // ------------------------------------------------------------------ Aufbau

    /** Shader übersetzen, Puffer anlegen, Stand der Bucht hochladen (im GL-Faden). */
    public void upload(GL4 gl) {
        for (String k : new String[]{"flux", "update", "edges", "maxspeed", "ghost", "restrict", "accum", "reflux", "force"}) prog.put(k, program(gl, "bay_" + k + ".comp"));
        List<BayGrid> bg = bay.grids();
        for (int k = 0; k < bg.size(); k++) {
            Grid q = new Grid();
            q.g = bg.get(k);
            q.nx = q.g.nx; q.nz = q.g.nz; q.px = q.g.px; q.n = q.px * (q.nz + 4);
            q.nf = (q.nx + 1) * q.nz + q.nx * (q.nz + 1);
            q.place = k + 1 < bg.size() ? bay.placement(k) : null;
            float[] st = state(q.g, q.n);
            q.bed = buffer(gl, bed(q.g.bedCells()));
            q.a = buffer(gl, st);
            q.s = buffer(gl, st);
            q.old = buffer(gl, st);
            q.flux = buffer(gl, new float[5 * q.nf]);
            q.mflux = buffer(gl, new float[q.nf]);
            float[] e = new float[q.n];
            double[] em = q.g.etaMaxCells();
            for (int c = 0; c < q.n; c++) e[c] = Double.isNaN(em[c]) ? -1e30f : (float) em[c];
            q.eta = buffer(gl, e);
            q.stats = buffer(gl, new float[4]);
            q.acc = buffer(gl, new float[2 * q.nz + 2 * q.nx]);
            q.aux = buffer(gl, new float[4 * (Math.max(q.nx, q.nz) + 4)]);
            int[] fc = bay.forcedCells(k);
            q.nforced = fc.length;
            if (fc.length > 0) {
                int[] ib = new int[1];
                gl.glGenBuffers(1, ib, 0);
                q.fcells = ib[0];
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, q.fcells);
                gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, fc.length * 4L, IntBuffer.wrap(fc), GL4.GL_STATIC_DRAW);
                q.fbuf = new float[3 * fc.length];
                q.fvals = buffer(gl, q.fbuf);
            }
            grids.add(q);
        }
        tp0 = new double[grids.size()];
        tp1 = new double[grids.size()];
        time = bay.time();
        steps = bay.steps();
        nextSample = bay.nextSample();
        List<Bay.Gauge> gs = bay.gauges();
        gaugeCells = new int[gs.size()][];
        for (int i = 0; i < gs.size(); i++) {
            Bay.Gauge ga = gs.get(i);
            BayGrid g = bay.gridAt(ga.x, ga.z);
            if (g != null) gaugeCells[i] = new int[]{bg.indexOf(g), g.id(g.cellI(ga.x), g.cellJ(ga.z))};
        }
    }

    private static float[] bed(double[] b) {
        float[] f = new float[b.length];
        for (int c = 0; c < b.length; c++) f[c] = Double.isNaN(b[c]) ? 1e30f : (float) b[c];
        return f;
    }

    private static float[] state(BayGrid g, int n) {
        float[] f = new float[3 * n];
        double[] h = g.depthCells(), hu = g.qxCells(), hv = g.qzCells();
        for (int c = 0; c < n; c++) { f[c] = (float) h[c]; f[n + c] = (float) hu[c]; f[2 * n + c] = (float) hv[c]; }
        return f;
    }

    // ------------------------------------------------------------------ Rechnung

    /** Ein Schritt mit dem Zeitschritt nach CFL wie {@link Bay#step}; gibt dt zurück. */
    public double step(GL4 gl) {
        double dt = Double.POSITIVE_INFINITY, mul = 1;
        float[] s = maxSpeeds(gl);
        for (int k = 0; k < grids.size(); k++) {
            if (k > 0) mul *= grids.get(k - 1).place[4];
            dt = Math.min(dt, bay.cfl * grids.get(k).g.dx / Math.max(bay.minSpeed, s[k]) * mul);
        }
        return step(gl, dt);
    }

    /** Ein Schritt mit vorgegebenem dt (für den Vergleich mit dem Prozessor). */
    public double step(GL4 gl, double dt) {
        long t0 = System.nanoTime();
        collectTimes(gl);
        advance(gl, 0, time, dt);
        time += dt;
        steps++;
        if (gauges && time + 1e-12 >= nextSample) { sample(gl); nextSample += bay.sampleDt; }
        busyNs += System.nanoTime() - t0;
        return dt;
    }

    /** Rechnet bis tEnd (ohne {@link #sync}). */
    public void run(GL4 gl, double tEnd) { while (time < tEnd) step(gl); }

    /** Pegel ablesen wie Bay.heightAt: Stand über dem Spiegel, NaN wenn trocken (unter 1 cm) oder Fels. */
    private void sample(GL4 gl) {
        double[] eta = new double[gaugeCells.length];
        for (int i = 0; i < eta.length; i++) {
            int[] gc = gaugeCells[i];
            eta[i] = Double.NaN;
            if (gc == null) continue;
            Grid q = grids.get(gc[0]);
            float h = readAt(gl, q.a, gc[1]), b = readAt(gl, q.bed, gc[1]);
            if (b < 1e29f && h > 0.01f) eta[i] = (double) h + b - bay.level;
        }
        bay.record(time, eta);
    }

    private static float readAt(GL4 gl, int buf, int index) {
        ByteBuffer bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, index * 4L, 4, bb);
        return bb.getFloat(0);
    }

    /** Größte Wellengeschwindigkeit je Gitter (eine Rückfrage). */
    public float[] maxSpeeds(GL4 gl) {
        IntBuffer zero = IntBuffer.wrap(new int[1]);
        for (Grid q : grids) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, q.stats);
            gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 4, zero.rewind());
            bind(gl, q.bed, q.a, q.stats);
            int[] sk = q.place == null ? new int[4] : q.place;
            run(gl, "maxspeed", q.nx * q.nz, "nx", q.nx, "nz", q.nz, "si0", sk[0], "sj0", sk[1], "sni", sk[2], "snj", sk[3], "g", (float) q.g.gravity, "hDry", (float) q.g.hDry, "hVel", (float) q.g.hVel);
        }
        float[] out = new float[grids.size()];
        for (int k = 0; k < grids.size(); k++) out[k] = Float.intBitsToFloat(readInts(gl, grids.get(k).stats, 1)[0]);
        return out;
    }

    private void advance(GL4 gl, int k, double t, double dt) {
        Grid q = grids.get(k);
        int query = timing ? begin(gl, k) : 0;
        if (k + 1 < grids.size()) copy(gl, q.a, q.old, 3L * q.n * 4);
        ghosts(gl, k, q.a, t);
        stage(gl, q, q.a, q.s, 1, dt);
        ghosts(gl, k, q.s, t + dt);
        stage(gl, q, q.s, q.a, 2, dt);
        // Gitter 0: Zufluss über die Seiten; feinere: Volumina über den Rand für den Abgleich
        bind(gl, q.mflux, q.acc);
        run(gl, "accum", 2 * q.nz + 2 * q.nx, "nx", q.nx, "nz", q.nz, "dt", (float) dt, "dx", (float) q.g.dx);
        if (timing) gl.glEndQuery(GL4.GL_TIME_ELAPSED);
        q.cellSteps += (long) q.nx * q.nz;
        q.gridSteps++;
        if (k + 1 < grids.size()) {
            Grid c = grids.get(k + 1);
            int[] pl = q.place;
            int r = pl[4];
            tp0[k] = t;
            tp1[k] = t + dt;
            zero(gl, c.acc, 2 * c.nz + 2 * c.nx);
            for (int s = 0; s < r; s++) advance(gl, k + 1, t + s * dt / r, dt / r);
            bind(gl, q.bed, q.a, c.bed, c.a);
            run(gl, "restrict", pl[2] * pl[3], "pnx", q.nx, "pnz", q.nz, "cnx", c.nx, "cnz", c.nz, "i0", pl[0], "j0", pl[1], "ni", pl[2], "nj", pl[3], "r", r,
                    "hDry", (float) q.g.hDry);
            bind(gl, q.bed, q.a, q.mflux, c.acc, q.stats);
            run(gl, "reflux", 2 * pl[3] + 2 * pl[2], "pnx", q.nx, "pnz", q.nz, "cnx", c.nx, "cnz", c.nz, "i0", pl[0], "j0", pl[1], "ni", pl[2], "nj", pl[3], "r", r,
                    "pdx", (float) q.g.dx, "dtp", (float) dt, "hDry", (float) q.g.hDry);
        }
    }

    /** Randzellen von st zur Zeit t: Seiten des gröbsten Gitters oder aus dem gröberen Gitter; dann die Linien im Inneren. */
    private void ghosts(GL4 gl, int k, int st, double t) {
        edgesOrParent(gl, k, st, t);
        Grid q = grids.get(k);
        if (q.nforced == 0) return;
        bay.forcedValues(k, t, q.fbuf);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, q.fvals);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, q.fbuf.length * 4L, FloatBuffer.wrap(q.fbuf));
        bind(gl, q.bed, st, q.fcells, q.fvals);
        run(gl, "force", q.nforced, "count", q.nforced, "n", q.n, "hVel", (float) q.g.hVel);
    }

    private void edgesOrParent(GL4 gl, int k, int st, double t) {
        Grid q = grids.get(k);
        if (k == 0) {
            for (int side = 0; side < 4; side++) {
                BayGrid.Edge e = q.g.edge(side);
                if (e == BayGrid.Edge.NEST) continue;
                int m = side < 2 ? q.nz : q.nx;
                int kind = e == BayGrid.Edge.WALL ? 0 : e == BayGrid.Edge.OPEN ? 1 : 2;
                Bay.Inlet in = q.g.inlet(side);
                if (kind == 2 && in == null) kind = 1; // Einlass ohne Werte: wie der Prozessor (Grund und Tiefe von innen, Impuls offen)
                if (kind == 2) {
                    float[] aux = new float[4 * (m + 4)];
                    for (int tt = 0; tt < (m + 4) * 2; tt++) {
                        int kk = tt / 2 - 2, l = tt % 2 + 1, gi, gj;
                        if (side == BayGrid.W) { gi = -l; gj = kk; }
                        else if (side == BayGrid.E) { gi = q.nx - 1 + l; gj = kk; }
                        else if (side == BayGrid.S) { gi = kk; gj = -l; }
                        else { gi = kk; gj = q.nz - 1 + l; }
                        double[] v = in.at(t, q.g.cx(gi), q.g.cz(gj));
                        aux[2 * tt] = (float) v[0];
                        aux[2 * tt + 1] = (float) v[1];
                    }
                    gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, q.aux);
                    gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, aux.length * 4L, FloatBuffer.wrap(aux));
                }
                bind(gl, q.bed, st, q.aux);
                run(gl, "edges", (m + 4) * 2, "nx", q.nx, "nz", q.nz, "side", side, "kind", kind, "hDry", (float) q.g.hDry, "hVel", (float) q.g.hVel);
            }
        } else {
            Grid p = grids.get(k - 1);
            float w = (float) ((t - tp0[k - 1]) / (tp1[k - 1] - tp0[k - 1]));
            bind(gl, q.bed, st, p.bed, p.a, p.old);
            run(gl, "ghost", q.n, "cnx", q.nx, "cnz", q.nz, "pnx", p.nx, "pnz", p.nz, "cdx", (float) q.g.dx, "cx0", (float) q.g.x0, "cz0", (float) q.g.z0,
                    "pdx", (float) p.g.dx, "px0", (float) p.g.x0, "pz0", (float) p.g.z0, "w", w, "hDry", (float) q.g.hDry, "hVel", (float) q.g.hVel);
        }
    }

    private void stage(GL4 gl, Grid q, int src, int dst, int stage, double dt) {
        BayGrid g = q.g;
        bind(gl, q.bed, src, q.flux, q.mflux);
        run(gl, "flux", (q.nx + 1) * q.nz, "nx", q.nx, "nz", q.nz, "mode", 0, "stage", stage, "g", (float) g.gravity, "hDry", (float) g.hDry, "hVel", (float) g.hVel);
        run(gl, "flux", q.nx * (q.nz + 1), "nx", q.nx, "nz", q.nz, "mode", 1, "stage", stage, "g", (float) g.gravity, "hDry", (float) g.hDry, "hVel", (float) g.hVel);
        bind(gl, q.bed, src, dst, q.flux, q.eta, q.stats);
        run(gl, "update", q.nx * q.nz, "nx", q.nx, "nz", q.nz, "stage", stage, "g", (float) g.gravity, "hDry", (float) g.hDry, "hVel", (float) g.hVel,
                "dt", (float) dt, "dx", (float) g.dx, "manning", (float) g.manning);
    }

    // ------------------------------------------------------------------ Rückholen

    /**
     * Holt den Stand in die Felder der Gitter (Tiefe, Durchfluss, Grund samt Rückgabe an halb trockene gröbere Zellen,
     * Höchststand) und übergibt Zeit, Schritte, abgeschnittenes Volumen, Zufluss und Rechenzeit an die Bucht.
     */
    public void sync(GL4 gl) {
        collectTimes(gl);
        for (int k = 0; k < grids.size(); k++) {
            Grid q = grids.get(k);
            float[] st = readFloats(gl, q.a, 3 * q.n), b = readFloats(gl, q.bed, q.n), e = readFloats(gl, q.eta, q.n);
            double[] h = q.g.depthCells(), hu = q.g.qxCells(), hv = q.g.qzCells(), bb = q.g.bedCells(), em = q.g.etaMaxCells();
            for (int c = 0; c < q.n; c++) {
                h[c] = st[c]; hu[c] = st[q.n + c]; hv[c] = st[2 * q.n + c];
                bb[c] = b[c] > 1e29f ? Double.NaN : b[c];
                em[c] = e[c] < -1e29f ? Double.NaN : e[c];
            }
            int[] s = readInts(gl, q.stats, 4);
            double cut = (s[2] & 0xffffffffL) * 1e-6 * q.g.dx * q.g.dx + (s[3] & 0xffffffffL) * 1e-3;
            double inflow = 0;
            if (k == 0) {
                float[] acc = readFloats(gl, q.acc, 2 * q.nz + 2 * q.nx);
                int[][] range = {{0, q.nz}, {q.nz, 2 * q.nz}, {2 * q.nz, 2 * q.nz + q.nx}, {2 * q.nz + q.nx, 2 * q.nz + 2 * q.nx}};
                for (int side = 0; side < 4; side++) if (q.g.edge(side) == BayGrid.Edge.INLET) for (int i = range[side][0]; i < range[side][1]; i++) inflow += acc[i];
                zero(gl, q.acc, acc.length);
            }
            // Zähler zurück, damit ein zweites sync nichts doppelt zählt (Feld 0 setzt maxSpeeds je Schritt)
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, q.stats);
            gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 16, IntBuffer.wrap(new int[4]));
            q.g.addGpu(cut, inflow, q.gridSteps - q.syncedSteps, q.gpuNs - q.syncedNs);
            q.syncedSteps = q.gridSteps;
            q.syncedNs = q.gpuNs;
        }
        bay.takeOver(time, steps, busyNs / 1e6, nextSample);
    }

    /** Wasserstand je Zelle von Gitter k (nx · nz, Zeile für Zeile, j = 0 zuerst), NaN wo trocken (unter 1 cm) oder Fels – für die Vorrechnung. */
    public float[] surface(GL4 gl, int k) {
        Grid q = grids.get(k);
        float[] h = readFloats(gl, q.a, q.n), b = readFloats(gl, q.bed, q.n), out = new float[q.nx * q.nz];
        for (int j = 0; j < q.nz; j++)
            for (int i = 0; i < q.nx; i++) {
                int c = (j + 2) * q.px + i + 2;
                out[j * q.nx + i] = b[c] > 1e29f || h[c] <= 0.01f ? Float.NaN : h[c] + b[c];
            }
        return out;
    }

    /** Höchster Stand je Zelle von Gitter k bisher (nx · nz), NaN wo nie nass. */
    public float[] maxSurface(GL4 gl, int k) {
        Grid q = grids.get(k);
        float[] e = readFloats(gl, q.eta, q.n), out = new float[q.nx * q.nz];
        for (int j = 0; j < q.nz; j++) for (int i = 0; i < q.nx; i++) { float v = e[(j + 2) * q.px + i + 2]; out[j * q.nx + i] = v < -1e29f ? Float.NaN : v; }
        return out;
    }

    public double time() { return time; }

    public long steps() { return steps; }

    /** Zeit der Grafikkarte für Gitter k bisher (ms; ohne die Arbeit für seine feineren Gitter). */
    public double gpuMs(int k) { return grids.get(k).gpuNs / 1e6; }

    /** Zellschritte von Gitter k bisher. */
    public long cellSteps(int k) { return grids.get(k).cellSteps; }

    public String info() {
        StringBuilder b = new StringBuilder("wave (GPU): ");
        for (int k = 0; k < grids.size(); k++) {
            Grid q = grids.get(k);
            b.append(String.format(Locale.GERMAN, "%sGitter %d %d × %d: %.1f ms, %.1f ns je Zelle", k == 0 ? "" : " · ", k, q.nx, q.nz, gpuMs(k),
                    q.cellSteps == 0 ? 0 : q.gpuNs / (double) q.cellSteps));
        }
        return b.toString();
    }

    public void dispose(GL4 gl) {
        for (Grid q : grids) {
            gl.glDeleteBuffers(10, new int[]{q.bed, q.a, q.s, q.old, q.flux, q.mflux, q.eta, q.stats, q.acc, q.aux}, 0);
            if (q.nforced > 0) gl.glDeleteBuffers(2, new int[]{q.fcells, q.fvals}, 0);
        }
        for (int p : prog.values()) gl.glDeleteProgram(p);
        int[] qs = new int[pool.size() + pending.size()];
        int i = 0;
        for (int p : pool) qs[i++] = p;
        for (int[] p : pending) qs[i++] = p[0];
        if (qs.length > 0) gl.glDeleteQueries(qs.length, qs, 0);
        grids.clear();
        prog.clear();
    }

    // ------------------------------------------------------------------ Zeiten

    private int begin(GL4 gl, int k) {
        int q;
        if (pool.isEmpty()) { int[] a = new int[1]; gl.glGenQueries(1, a, 0); q = a[0]; } else q = pool.remove(pool.size() - 1);
        gl.glBeginQuery(GL4.GL_TIME_ELAPSED, q);
        pending.add(new int[]{q, k});
        return q;
    }

    private void collectTimes(GL4 gl) {
        long[] r = new long[1];
        for (int[] p : pending) {
            gl.glGetQueryObjecti64v(p[0], GL4.GL_QUERY_RESULT, r, 0);
            grids.get(p[1]).gpuNs += r[0];
            pool.add(p[0]);
        }
        pending.clear();
    }

    // ------------------------------------------------------------------ GL

    private static int buffer(GL4 gl, float[] data) {
        int[] b = new int[1];
        gl.glGenBuffers(1, b, 0);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b[0]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, data.length * 4L, FloatBuffer.wrap(data), GL4.GL_DYNAMIC_COPY);
        return b[0];
    }

    private static void zero(GL4 gl, int buf, int n) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, n * 4L, FloatBuffer.wrap(new float[n]));
    }

    private static void copy(GL4 gl, int from, int to, long bytes) {
        gl.glBindBuffer(GL4.GL_COPY_READ_BUFFER, from);
        gl.glBindBuffer(GL4.GL_COPY_WRITE_BUFFER, to);
        gl.glCopyBufferSubData(GL4.GL_COPY_READ_BUFFER, GL4.GL_COPY_WRITE_BUFFER, 0, 0, bytes);
    }

    private static float[] readFloats(GL4 gl, int buf, int n) {
        ByteBuffer bb = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder());
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, n * 4L, bb);
        float[] f = new float[n];
        bb.asFloatBuffer().get(f);
        return f;
    }

    private static int[] readInts(GL4 gl, int buf, int n) {
        ByteBuffer bb = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder());
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, n * 4L, bb);
        int[] f = new int[n];
        bb.asIntBuffer().get(f);
        return f;
    }

    private static void bind(GL4 gl, int... bufs) { for (int i = 0; i < bufs.length; i++) gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, i, bufs[i]); }

    /** Ein Durchlauf mit count Fäden; Uniforms als Paare Name, Wert (Integer → int, Float → float). */
    private void run(GL4 gl, String name, int count, Object... uni) {
        int p = prog.get(name);
        gl.glUseProgram(p);
        Map<String, Integer> l = loc.computeIfAbsent(p, x -> new HashMap<>());
        for (int i = 0; i < uni.length; i += 2) {
            String u = (String) uni[i];
            int at = l.computeIfAbsent(u, x -> gl.glGetUniformLocation(p, x));
            if (at < 0) continue;
            if (uni[i + 1] instanceof Integer) gl.glUniform1i(at, (Integer) uni[i + 1]);
            else gl.glUniform1f(at, (Float) uni[i + 1]);
        }
        gl.glDispatchCompute((count + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
    }

    // ------------------------------------------------------------------ Shader

    public static String source(String file) {
        try (InputStream in = BayGpu.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("wave (GPU): Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("wave (GPU): " + file + ": " + e.getMessage(), e);
        }
    }

    private static int program(GL4 gl, String file) {
        int sh = gl.glCreateShader(GL4.GL_COMPUTE_SHADER);
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("wave (GPU): " + file + " lässt sich nicht übersetzen:\n" + shaderLog(gl, sh));
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, sh);
        gl.glLinkProgram(p);
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("wave (GPU): " + file + " lässt sich nicht binden: " + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        gl.glDeleteShader(sh);
        return p;
    }

    private static String shaderLog(GL4 gl, int sh) {
        int[] len = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
        return new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8);
    }

    /** Name der Grafikkarte. */
    public static String renderer(GL4 gl) { return gl.glGetString(GL.GL_RENDERER); }
}
