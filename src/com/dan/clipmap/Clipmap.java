package com.dan.clipmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Geometrie-Clipmap um das Auge (Losasso und Hoppe 2004, vereinfacht): {@link #levels()} Stufen, jede ein Gitter aus
 * {@link #grid} × {@link #grid} Punkten, die Zellweite verdoppelt sich von Stufe zu Stufe (ab {@link #cell0}). Jede Stufe
 * liegt mittig um das Auge, auf ein Vielfaches ihrer doppelten Zellweite gerastet; so liegen die Ränder einer Stufe auf
 * Gitterlinien der nächst gröberen, und die feinere deckt die Mitte der gröberen ab.
 * <p>
 * <b>Nähte ohne Risse:</b> In einem Band am Rand ({@link #morphWidth()} Zellen) gleitet die Höhe jeder Stufe auf die
 * der gröberen über ({@link #morph}); am Rand selbst sind beide gleich, die Dreiecke der feinen Stufe treffen die
 * Kanten der groben genau. Die grobe Stufe verwirft, was die feinere zeichnet ({@code clipmapHidden} im Baustein
 * {@code gl/clipmap.glsl}).
 * <p>
 * <b>Nachladen:</b> Ein eigener Faden ({@link #start()}) holt bei jeder Bewegung des Auges nur die neuen Streifen aus
 * der {@link Source}, legt sie ringförmig (toroidal) in die Speicher der Stufen (je {@link #texSize}²) und gibt alle
 * Stufen zugleich frei. Der Bildlauf ruft {@link #update} und lädt mit {@code gl.ClipmapGl} nur die geänderten
 * Rechtecke hoch.
 * <p>
 * Raum: x nach Osten, z nach Süden, y oben, in m; der Ursprung des feinsten Gitters liegt bei ({@link #originX},
 * {@link #originZ}) (bei Kacheln die Mitte einer Datenzelle, dann ist die feinste Stufe genau die Kachel).
 * <b>Erdkrümmung:</b> {@link #curvatureRadius} (mit Refraktion, Vorgabe R/(1 − 0,13)) senkt jeden Punkt um r²/2R'
 * (r waagerecht vom Auge), 0 schaltet ab.
 */
public final class Clipmap {
    /** Qualitätsstufen (Paketvertrag Punkt 7): Punkte je Kante einer Stufe. */
    /**
     * Mitte der Clipmap für ein Tele (aus LituyaBay1958, Werkbuch Welle W5, K15): die Ringe liegen um ihre Mitte, und die
     * Mitte war immer das Auge; ein Hang in 2 km bekam mit 135 mm die Stufe zu 16–32 m, obwohl ein Bildpunkt dort rund
     * 0,3 m deckt (Berge aus Pappe). Jetzt wandert die Mitte ab einem Bildwinkel von wideDeg (27°, rund 50 mm auf 24 mm
     * Bildhöhe) zum Zielpunkt der Bildmitte, voll ab wideDeg − 11° (16°, rund 85 mm), höchstens maxDist m weit (6 km).
     * hit: Zielpunkt der Bildmitte {x, z} oder null (kein Gelände im Blick). Ergebnis {x, z}.
     */
    public static double[] focusCenter(double eyeX, double eyeZ, double[] hit, double fovyDeg, double wideDeg, double maxDist) {
        double w = Math.max(0, Math.min(1, (wideDeg - fovyDeg) / 11.0));
        if (w <= 0 || hit == null) return new double[]{eyeX, eyeZ};
        double dx = hit[0] - eyeX, dz = hit[1] - eyeZ, d = Math.hypot(dx, dz);
        double k = w * Math.min(1, maxDist / Math.max(d, 1));
        return new double[]{eyeX + dx * k, eyeZ + dz * k};
    }

    /** Wie oben mit 27° und 6 km (Lituya). */
    public static double[] focusCenter(double eyeX, double eyeZ, double[] hit, double fovyDeg) { return focusCenter(eyeX, eyeZ, hit, fovyDeg, 27.0, 6000); }

    public enum Quality {
        LOW(129), MEDIUM(193), HIGH(257), ULTRA(385);
        public final int grid;
        Quality(int grid) { this.grid = grid; }
    }

    public final Source source;
    /** Punkte je Kante einer Stufe ((grid − 1) durch 4 teilbar). */
    public final int grid;
    /** Kantenlänge der ringförmigen Speicher (grid + Rand für die Bewegung zwischen zwei Freigaben). */
    public final int texSize;
    public final double cell0, originX, originZ, range;
    private final int levelCount;
    /** Höhe, wo die Quelle nichts hat (etwa Meeresgrund außerhalb der Daten). */
    public volatile float noData = 0;
    /** Krümmungsradius mit Refraktion (m); 0 = flach. */
    public volatile double curvatureRadius = 6_371_000 / (1 - 0.13);

    private final Object lock = new Object();
    private final Level[] lv;
    private volatile double tx, tz;
    private volatile boolean hasTarget;
    private volatile long wanted, done;
    private Thread worker;
    private volatile boolean running;
    private volatile double lastRoundMs, lastRoundSamples;
    private long rounds;

    static final class Level {
        final int index;
        final double cell;
        final float[] h;
        int ci, cj;
        boolean loaded;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        final List<int[]> dirty = new ArrayList<>();

        Level(int index, double cell, int t) {
            this.index = index;
            this.cell = cell;
            this.h = new float[t * t];
        }
    }

    /**
     * Eine Clipmap über source. grid: Punkte je Kante ((grid − 1) durch 4 teilbar, etwa {@link Quality#grid}); cell0:
     * Zellweite der feinsten Stufe; (originX, originZ): ein Punkt des feinsten Gitters; range: wie weit die gröbste
     * Stufe mindestens reicht (m vom Auge).
     */
    public Clipmap(Source source, int grid, double cell0, double originX, double originZ, double range) {
        if (grid < 17 || (grid - 1) % 4 != 0) throw new IllegalArgumentException("grid − 1 muss durch 4 teilbar sein: " + grid);
        this.source = source;
        this.grid = grid;
        this.texSize = grid + 7;
        this.cell0 = cell0;
        this.originX = originX;
        this.originZ = originZ;
        this.range = range;
        int n = 1;
        while (half() * cell0 * Math.pow(2, n - 1) < range && n < 24) n++;
        levelCount = n;
        lv = new Level[n];
        for (int i = 0; i < n; i++) lv[i] = new Level(i, cell0 * Math.pow(2, i), texSize);
    }

    public Clipmap(Source source, Quality q, double cell0, double originX, double originZ, double range) {
        this(source, q.grid, cell0, originX, originZ, range);
    }

    public int levels() { return levelCount; }
    public int half() { return (grid - 1) / 2; }
    public double cell(int level) { return lv[level].cell; }
    /** Breite des Übergangsbands am Rand einer Stufe (in Zellen der Stufe). */
    public int morphWidth() { return Math.max(2, (grid - 1) / 10); }

    /** Mitte (Index im Gitter der Stufe, gerade) für ein Auge bei x. */
    int centerIndex(int level, double x, double origin) {
        double c = lv[level].cell;
        return (int) (2 * Math.round((x - origin) / (2 * c)));
    }

    // ------------------------------------------------------------------ Faden

    /** Startet den Ladefaden (Daemon). */
    public synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::loop, "clipmap-lader");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        if (worker != null) worker.interrupt();
        worker = null;
    }

    /** Wohin das Auge schaut (je Bild aus dem Bildlauf; blockiert nicht). */
    public void update(double x, double z) {
        if (hasTarget && x == tx && z == tz) return;
        tx = x;
        tz = z;
        hasTarget = true;
        wanted++;
        Thread w = worker;
        if (w != null) java.util.concurrent.locks.LockSupport.unpark(w);
    }

    /** Wartet, bis die Stufen für das zuletzt gesetzte Auge geladen und freigegeben sind (für Prüfungen und Standbilder). */
    public boolean await(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (done < wanted || !allLoaded()) {
            if (System.currentTimeMillis() > end) return false;
            if (!running) refreshNow();
            else try { Thread.sleep(5); } catch (InterruptedException e) { return false; }
        }
        return true;
    }

    private boolean allLoaded() {
        synchronized (lock) {
            for (Level l : lv) if (!l.loaded) return false;
            return true;
        }
    }

    private void loop() {
        while (running) {
            if (!hasTarget || done >= wanted) {
                java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
                continue;
            }
            try {
                refreshNow();
            } catch (RuntimeException e) {
                System.err.println("clipmap: Laden fehlgeschlagen: " + e);
                java.util.concurrent.locks.LockSupport.parkNanos(200_000_000L);
            }
        }
    }

    /** Eine Runde: alle Stufen für das gesetzte Auge nachladen und gemeinsam freigeben (auch ohne Faden nutzbar). */
    public void refreshNow() {
        long ver = wanted;
        double x = tx, z = tz;
        long t0 = System.nanoTime();
        int half = half(), m = grid;
        List<Runnable> apply = new ArrayList<>();
        long samples = 0;
        int[][] centers = new int[levelCount][2];
        for (int L = levelCount - 1; L >= 0; L--) {
            Level l = lv[L];
            int ci = centerIndex(L, x, originX), cj = centerIndex(L, z, originZ);
            centers[L][0] = ci;
            centers[L][1] = cj;
            int oci, ocj;
            boolean loaded;
            synchronized (lock) { oci = l.ci; ocj = l.cj; loaded = l.loaded; }
            if (loaded && oci == ci && ocj == cj) continue;
            int a = ci - half, b = cj - half;
            List<int[]> rects = new ArrayList<>();
            if (!loaded || Math.abs(ci - oci) >= m || Math.abs(cj - ocj) >= m) rects.add(new int[]{a, b, m, m});
            else {
                int oa = oci - half, ob = ocj - half;
                // neue Spalten (ganze Höhe des neuen Fensters)
                if (a < oa) rects.add(new int[]{a, b, Math.min(m, oa - a), m});
                if (a > oa) { int s = Math.max(a, oa + m); rects.add(new int[]{s, b, a + m - s, m}); }
                // neue Zeilen (nur in den Spalten, die alt und neu gemeinsam haben)
                int ca = Math.max(a, oa), cb = Math.min(a + m, oa + m);
                if (cb > ca) {
                    if (b < ob) rects.add(new int[]{ca, b, cb - ca, Math.min(m, ob - b)});
                    if (b > ob) { int s = Math.max(b, ob + m); rects.add(new int[]{ca, s, cb - ca, b + m - s}); }
                }
            }
            for (int[] r : rects) {
                if (r[2] <= 0 || r[3] <= 0) continue;
                float[] buf = new float[r[2] * r[3]];
                source.fill(originX + r[0] * l.cell, originZ + r[1] * l.cell, l.cell, r[2], r[3], buf);
                samples += buf.length;
                float nd = noData;
                float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE;
                for (int i = 0; i < buf.length; i++) {
                    if (Float.isNaN(buf[i])) buf[i] = nd;
                    mn = Math.min(mn, buf[i]);
                    mx = Math.max(mx, buf[i]);
                }
                final float fmn = mn, fmx = mx;
                apply.add(() -> {
                    int t = texSize;
                    for (int j = 0; j < r[3]; j++) {
                        int row = Math.floorMod(r[1] + j, t) * t;
                        for (int i = 0; i < r[2]; i++) l.h[row + Math.floorMod(r[0] + i, t)] = buf[j * r[2] + i];
                    }
                    l.dirty.add(r);
                    l.min = Math.min(l.min, fmn);
                    l.max = Math.max(l.max, fmx);
                });
            }
        }
        synchronized (lock) {
            for (Runnable r : apply) r.run();
            for (int L = 0; L < levelCount; L++) {
                lv[L].ci = centers[L][0];
                lv[L].cj = centers[L][1];
                lv[L].loaded = true;
            }
            rounds++;
        }
        if (samples > 0) {
            lastRoundMs = (System.nanoTime() - t0) / 1e6;
            lastRoundSamples = samples;
        }
        done = ver;
    }

    // ------------------------------------------------------------------ Abfragen

    /** Was eine Stufe gerade zeigt (freigegeben): Mitte, geladen, kleinste und größte Höhe. */
    public record Window(int level, double cell, int ci, int cj, boolean loaded, float min, float max) {
        /** Westrand, Nordrand, Ostrand, Südrand ihrer Punkte (m). */
        public double[] rect(Clipmap c) {
            int h = c.half();
            return new double[]{c.originX + (ci - h) * cell, c.originZ + (cj - h) * cell, c.originX + (ci + h) * cell, c.originZ + (cj + h) * cell};
        }
    }

    /** Die freigegebenen Fenster aller Stufen (für das Zeichnen; ein Schnappschuss). */
    public Window[] windows() {
        synchronized (lock) {
            Window[] w = new Window[levelCount];
            for (Level l : lv) w[l.index] = new Window(l.index, l.cell, l.ci, l.cj, l.loaded, l.min, l.max);
            return w;
        }
    }

    /** Ein Rechteck, das hochgeladen werden muss: Stufe, erste Spalte und Zeile (Index im Gitter der Stufe), Breite, Höhe. */
    public interface Upload {
        void rect(int level, int i0, int j0, int w, int h, float[] store, int texSize);
    }

    /** Gibt alle geänderten Rechtecke an u (im GL-Faden; hält die Sperre, solange u läuft). */
    public void drain(Upload u) {
        synchronized (lock) {
            for (Level l : lv) {
                for (int[] r : l.dirty) u.rect(l.index, r[0], r[1], r[2], r[3], l.h, texSize);
                l.dirty.clear();
            }
        }
    }

    /** Wert im Speicher einer Stufe am Gitterpunkt (i, j) (ohne Prüfung, ob er im Fenster liegt). */
    float raw(int level, int i, int j) {
        int t = texSize;
        return lv[level].h[Math.floorMod(j, t) * t + Math.floorMod(i, t)];
    }

    /** Ob der Gitterpunkt (i, j) im freigegebenen Fenster der Stufe liegt. */
    boolean inside(int level, int i, int j, int margin) {
        Level l = lv[level];
        int h = half() - margin;
        return l.loaded && Math.abs(i - l.ci) <= h && Math.abs(j - l.cj) <= h;
    }

    /** Höhe am Gitterpunkt (i, j) der Stufe, wie sie das Gitter zeichnet (ohne Übergang), oder NaN außerhalb. */
    public float gridHeight(int level, int i, int j) {
        synchronized (lock) { return inside(level, i, j, 0) ? raw(level, i, j) : Float.NaN; }
    }

    /**
     * Anteil des Übergangs zur gröberen Stufe am Gitterpunkt (k, l) des Fensters (0 … grid − 1): 0 innen, 1 am Rand. Gleich
     * gerechnet wie im Baustein {@code clipmap.glsl}.
     */
    public double morph(int k, int l) {
        int h = half(), w = morphWidth();
        int d = Math.max(Math.abs(k - h), Math.abs(l - h));
        return Math.min(1, Math.max(0, (d - (h - w - 1)) / (double) w));
    }

    /** Höhe der gröberen Stufe level + 1 am Gitterpunkt (i, j) der Stufe level (bilinear zwischen ihren Punkten). */
    float coarseAt(int level, int i, int j) {
        int ci = Math.floorDiv(i, 2), cj = Math.floorDiv(j, 2);
        boolean ox = (i & 1) != 0, oz = (j & 1) != 0;
        float a = raw(level + 1, ci, cj);
        if (!ox && !oz) return a;
        if (ox && !oz) return 0.5f * (a + raw(level + 1, ci + 1, cj));
        if (!ox) return 0.5f * (a + raw(level + 1, ci, cj + 1));
        return 0.25f * (a + raw(level + 1, ci + 1, cj) + raw(level + 1, ci, cj + 1) + raw(level + 1, ci + 1, cj + 1));
    }

    /** Höhe des Scheitels (k, l) der Stufe mit Übergang, wie der Baustein sie rechnet (ohne Erdkrümmung). */
    public float vertexHeight(int level, int k, int l) {
        synchronized (lock) {
            Level v = lv[level];
            int i = v.ci - half() + k, j = v.cj - half() + l;
            float h = raw(level, i, j);
            if (level + 1 >= levelCount) return h;
            double a = morph(k, l);
            return a <= 0 ? h : (float) (h + (coarseAt(level, i, j) - h) * a);
        }
    }

    /** Ort des Scheitels (k, l) der Stufe: {x, z}. */
    public double[] vertexXZ(int level, int k, int l) {
        synchronized (lock) {
            Level v = lv[level];
            return new double[]{originX + (v.ci - half() + k) * v.cell, originZ + (v.cj - half() + l) * v.cell};
        }
    }

    /** Die Stufe mit Mitte (ci, cj) (für Prüfungen). */
    public int[] center(int level) {
        synchronized (lock) { return new int[]{lv[level].ci, lv[level].cj}; }
    }

    /**
     * Höhe bei (x, z) aus der feinsten Stufe, die den Ort im Inneren hat (bilinear, ohne Übergang und Krümmung), oder NaN,
     * wenn noch nichts geladen ist. Für Kamera, Boden und Prüfungen.
     */
    public float height(double x, double z) {
        synchronized (lock) {
            for (Level l : lv) {
                if (!l.loaded) continue;
                double u = (x - originX) / l.cell, v = (z - originZ) / l.cell;
                int i = (int) Math.floor(u), j = (int) Math.floor(v);
                if (!inside(l.index, i, j, 2) || !inside(l.index, i + 1, j + 1, 2)) continue;
                double fx = u - i, fz = v - j;
                double a = raw(l.index, i, j), b = raw(l.index, i + 1, j), c = raw(l.index, i, j + 1), d = raw(l.index, i + 1, j + 1);
                return (float) ((a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz);
            }
            return Float.NaN;
        }
    }

    /** Wie weit die Erdkrümmung einen Punkt in r Meter Abstand senkt (m). */
    public double drop(double r) { return curvatureRadius > 0 ? r * r / (2 * curvatureRadius) : 0; }

    /** „13 Stufen à 257², 0,5 m bis 2 048 m, bis 262 km; letzte Runde 4,1 ms für 12 000 Punkte“ */
    public String describe() {
        return String.format(Locale.GERMAN, "%d Stufen à %d², %s m bis %s m, bis %.0f km; letzte Runde %.1f ms für %.0f Punkte",
                levelCount, grid, TileFolder.fmt(cell0), TileFolder.fmt(cell(levelCount - 1)), half() * cell(levelCount - 1) / 1000, lastRoundMs, lastRoundSamples);
    }

    public long rounds() { synchronized (lock) { return rounds; } }
}
