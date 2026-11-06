package com.dan.fog;

import java.util.Locale;

/**
 * Nebel über Gelände (seit Brocken Phase 6). Drei Teile, die sich addieren:
 * <ul>
 * <li><b>Nebelmeer</b> (Hochnebel unter einer Inversion): eine Schicht zwischen {@code seaBase} und {@code seaTop} in
 * m über dem Bezug (nicht über dem Boden), die Obergrenze wogt um ± {@code seaRough}. Kuppen ragen heraus, Täler
 * liegen darin. Am Tag löst sie sich von unten auf: die Sonne wärmt den Boden, die Untergrenze steigt mit
 * {@link #burnRate} · Erwärmung; nachts sinkt sie wieder zum Ziel der Lage. Ist die Schicht dünner als
 * {@link #MIN_THICKNESS}, verschwindet sie.</li>
 * <li><b>Talnebel</b> aus Kaltluft: wo ein Kaltluftsee liegt ({@link ColdPool}), füllt Nebel ihn bis zum Anteil
 * {@code valleyFill} seiner Tiefe, über dem Boden gemessen.</li>
 * <li><b>Stau am Hang</b>: liegt eine Wolkendecke mit der Basis {@code capBase} auf dem Berg, drückt der Wind die Luft
 * am Luvhang hoch; dort reicht die Wolke tiefer (Hangnebel), um {@link #liftPerMs} m je m/s Aufwind ({@link Wind}, 40 m
 * über Grund), höchstens {@code capBelt}. Die Decke selbst zeichnet die App mit ihren Wolken; hier entsteht nur der
 * Gürtel unter ihr.</li>
 * </ul>
 * Schwaden (Rauschen) ziehen mit dem Wind am Ort {@link #windAnchor}. Dasselbe Feld rechnet der Shader in
 * {@code gl/terrain_fog_common.glsl}; {@link #density(Snapshot, float[], double, double, double)} ist die Vorlage.
 * Gelände, Kaltluft und Aufwind kommen als Karte ({@link #buildMap}) über ein Rechteck, in dem die App sie kennt; außerhalb
 * gibt es nur das Nebelmeer.
 * <p>
 * Nur Java 21. Ohne Gelände ({@code ground == null}) liegt die Karte auf 0 m.
 */
public final class TerrainFog {

    /**
     * Höhe des Geländes (eigene kleine Schnittstelle, Paketvertrag Punkt 4). In {@code TerrainFog} verschachtelt, damit
     * kein zweites {@code Ground} neben {@code com.dan.forest.Ground} steht (Vorgänger mit beiden Packages im Import).
     */
    @FunctionalInterface
    public interface Ground {
        /** Höhe des Bodens am Ort (m, gleicher Bezug wie y der Szene). */
        double height(double x, double z);
    }

    /**
     * Tiefe eines Kaltluftsees am Ort (m, 0 = keiner): in klaren, ruhigen Nächten sammelt sich kalte Luft in Mulden und
     * Tälern; feucht genug, wird sie zu Talnebel. Etwa {@code nebel.cold = (x, z) -> windfeld.coldPoolAt(x, z, 0)}.
     */
    @FunctionalInterface
    public interface ColdPool {
        double depth(double x, double z);
    }

    /**
     * Stufen: Schritte des Blicks, Zellen der Karte je Seite und (seit Brocken Phase 7) die Auflösung des Bilddurchgangs:
     * 1 = jeder Bildpunkt, 2 = halbe, 3 = drittel Auflösung in jeder Richtung, kantentreu hochgerechnet.
     */
    public enum Quality {
        LOW(12, 128, 3), MEDIUM(20, 192, 3), HIGH(28, 256, 2), ULTRA(40, 256, 1);
        public final int steps, mapSize, scale;
        Quality(int s, int m, int sc) { steps = s; mapSize = m; scale = sc; }
    }

    // ------------------------------------------------------------------ Eingänge
    public volatile long seed = 1;
    public volatile Ground ground;
    public volatile ColdPool cold;
    public volatile Wind wind;
    /** Ort (x, y, z), an dem der Wind für das Ziehen der Schwaden gefragt wird. */
    public volatile double[] windAnchor = {0, 1000, 0};
    /** Ohne {@link #wind}: Wind (m/s) und woher (Grad von Nord). */
    public volatile double windSpeed = 3, windFromDeg = 240;
    /** Rechteck der Karte {x0, z0, Kantenlänge} (m). */
    public volatile double[] mapRect = {-6400, -6400, 12_800};
    /** Wird erhöht, wenn sich Gelände, Kaltluft oder Wind geändert haben (die Grafik baut die Karte neu). */
    public volatile int mapVersion;
    public volatile Quality quality = Quality.MEDIUM;

    /** Ziel des Nebelmeers aus der Lage: Unter- und Obergrenze (m über dem Bezug); NaN = kein Meer. */
    public volatile double seaBaseTarget = Double.NaN, seaTopTarget = Double.NaN;
    /** Sicht im Nebelmeer (m) und Wogen der Obergrenze (± m) [G]. */
    public volatile double seaVisibility = 120, seaRough = 14;
    /** Erwärmung des Bodens −1 … 1 (Sonne am Tag, Ausstrahlung nachts); treibt das Auflösen von unten. */
    public volatile double heating;
    /** Wie schnell die Untergrenze bei voller Erwärmung steigt (m/h) [G: ein Meer von 400 m ist im Oktober zum Mittag fort]. */
    public volatile double burnRate = 260;

    /** Talnebel: Anteil der Tiefe des Kaltluftsees, den er füllt (0 = keiner, 1 = ganz), und seine Sicht (m). */
    public volatile double valleyFill, valleyVisibility = 150;

    /** Stau: Basis der Decke (m über dem Bezug, NaN = keine), Sicht im Gürtel (m), größte Tiefe des Gürtels unter ihr (m). */
    public volatile double capBase = Double.NaN, capVisibility = 180, capBelt = 260;
    /** m tiefer je m/s Aufwind 40 m über Grund [G]. */
    public volatile double liftPerMs = 110;

    /** Größe der Schwaden (m) und wie stark sie die Dichte ändern (0..1). */
    public volatile double bankSize = 1600, wisps = 0.4;
    /**
     * Seit LituyaBay1958 W7: Quellen auf dem Nebelmeer – kleine Kuppen von ± {@code billows} m und rund {@code billowSize} m
     * Größe, die mit dem Wind ziehen; von oben gesehen tragen sie Licht und Schatten (die Seite zur Sonne hell, die andere
     * im Schatten der Kuppe). 0 = wie bisher (glatte Wogen aus {@link #seaRough} und {@link #bankSize}).
     */
    public volatile double billows = 0, billowSize = 320;

    /** Unter dieser Dicke (m) vergeht das Nebelmeer. */
    public static final double MIN_THICKNESS = 25;

    // ------------------------------------------------------------------ Vorstoß (seit LituyaBay1958 W7)
    /**
     * Seit LituyaBay1958 W7: das Nebelmeer liegt nicht überall, sondern stößt als Bank vor. {@code advance} ist der Weg
     * (m), den die Front vom Rand der Karte auf der Seite, von der der Nebel kommt, zurückgelegt hat; NaN = kein Vorstoß
     * (das Meer liegt überall wie bisher). Der Weg zählt nicht in Luftlinie: die Karte rechnet für jede Zelle, wie weit
     * der Nebel durch Gelände unter seiner Obergrenze gehen muss (Täler, Sunde, der Eingang einer Bucht); Grate über der
     * Obergrenze halten ihn auf, quer zum Wind und gegen ihn kommt er langsamer voran. So kriecht eine Seenebelbank
     * durch den Eingang in die Bucht und füllt sie von dort aus, statt über die Kämme zu springen.
     */
    public volatile double advance = Double.NaN;
    /** Wie schnell die Front vorstößt (m/s, negativ: sie zieht sich zurück); {@link #step} schreibt {@link #advance} fort. */
    public volatile double advanceRate;
    /** Breite der Front (m), Finger und Zungen an ihr 0..1, und so weit unter der Obergrenze muss Gelände liegen, damit Nebel hindurchgeht (m) [G]. */
    public volatile double advanceSoft = 350, advanceFingers = 0.5, advancePass = 40;
    /** Richtung, aus der der Nebel kommt (Grad von Nord); NaN = aus dem Wind am Anker. */
    public volatile double advanceFromDeg = Double.NaN;
    /** Nicht erreichbar (m Weg). */
    public static final double FAR = 1e7;
    private int advKeyBuilt = Integer.MIN_VALUE;

    // ------------------------------------------------------------------ Zustand des Nebelmeers
    private double seaBase = Double.NaN, seaTop = Double.NaN, seaAmount, time, offX, offZ;
    private volatile Snapshot snapshot = build();

    public double seaBase() { return seaBase; }
    public double seaTop() { return seaTop; }
    public double seaAmount() { return seaAmount; }
    public double time() { return time; }

    // ------------------------------------------------------------------ Presets

    /** Nebelmeer 450 bis 850 m [G], wie die Inversion am Brocken. */
    public static TerrainFog nebelmeer() { TerrainFog f = new TerrainFog(); f.seaBaseTarget = 450; f.seaTopTarget = 850; f.settle(); return f; }
    /** Talnebel: Kaltluftseen ganz gefüllt. */
    public static TerrainFog talnebel() { TerrainFog f = new TerrainFog(); f.valleyFill = 1; f.settle(); return f; }
    /** Stau: Decke ab 850 m, der Gürtel darunter am Luvhang. */
    public static TerrainFog stau() { TerrainFog f = new TerrainFog(); f.capBase = 850; f.settle(); return f; }

    // ------------------------------------------------------------------ Zeit

    /** Das Nebelmeer sofort auf das Ziel setzen (Sprünge der Uhr, Start). */
    public synchronized void settle() {
        boolean on = !Double.isNaN(seaBaseTarget) && !Double.isNaN(seaTopTarget) && seaTopTarget > seaBaseTarget + MIN_THICKNESS;
        seaBase = on ? seaBaseTarget : Double.NaN;
        seaTop = on ? seaTopTarget : Double.NaN;
        seaAmount = on ? 1 : 0;
        snapshot = build();
    }

    /**
     * Das Nebelmeer auf das Ziel setzen, das schon um {@code burned} m von unten aufgelöst ist (Sprünge der Uhr: die App
     * rechnet die Erwärmung seit Beginn der Lage zusammen, {@code burned} = {@link #burnRate} · ∫ heating dt). Die
     * Obergrenze sinkt dabei wie in {@link #step} um 15 % davon.
     */
    public synchronized void settle(double burned) {
        boolean on = !Double.isNaN(seaBaseTarget) && !Double.isNaN(seaTopTarget) && seaTopTarget > seaBaseTarget;
        if (!on) { settle(); return; }
        burned = Math.max(0, burned);
        seaBase = seaBaseTarget + burned;
        seaTop = seaTopTarget - 0.15 * burned;
        seaAmount = smooth(MIN_THICKNESS, MIN_THICKNESS + 60, seaTop - seaBase);
        if (seaAmount < 1e-4) { seaAmount = 0; seaBase = seaTop = Double.NaN; }
        snapshot = build();
    }

    /**
     * Um dt Sekunden weiter: die Obergrenze folgt dem Ziel (1 h), die Untergrenze steigt bei Erwärmung und sinkt nachts
     * zum Ziel zurück (3 h); unter {@link #MIN_THICKNESS} oder ohne Ziel vergeht das Meer (30 min). Schwaden ziehen.
     */
    public synchronized void step(double dt) {
        if (dt < 0) dt = 0;
        time += dt;
        double h = dt / 3600;
        boolean on = !Double.isNaN(seaBaseTarget) && !Double.isNaN(seaTopTarget) && seaTopTarget > seaBaseTarget;
        if (on) {
            if (Double.isNaN(seaBase) || seaAmount < 1e-3) { seaBase = seaBaseTarget; seaTop = seaTopTarget; }
            seaTop += (seaTopTarget - seaTop) * (1 - Math.exp(-h / 1.0));
            if (heating > 0) {
                seaBase += burnRate * heating * h;
                seaTop -= 0.15 * burnRate * heating * h;          // oben trocknet es etwas ab
            } else {
                seaBase += (seaBaseTarget - seaBase) * (1 - Math.exp(-h / 3.0));
            }
            seaBase = Math.max(seaBase, seaBaseTarget - 1e-9);
        }
        double thick = on ? seaTop - seaBase : 0;
        double want = on ? smooth(MIN_THICKNESS, MIN_THICKNESS + 60, thick) : 0;
        seaAmount += (want - seaAmount) * (1 - Math.exp(-h / (want < seaAmount ? 0.25 : 0.5)));    // vergeht schneller, als es entsteht
        if (seaAmount < 1e-4 && !on) { seaAmount = 0; seaBase = seaTop = Double.NaN; }
        if (!Double.isNaN(advance)) advance = Math.max(-5 * advanceSoft, advance + advanceRate * dt);
        advKey();
        double[] d = drift();
        double per = Fog.PERIOD * Math.max(1, bankSize);
        offX = mod(offX - d[0] * dt, per);
        offZ = mod(offZ - d[1] * dt, per);
        snapshot = build();
    }

    /** Richtung, in die der Nebel vorstößt, als Einheitsvektor {x, z} (aus {@link #advanceFromDeg} oder dem Wind). */
    public double[] advanceTo() {
        double from = advanceFromDeg;
        if (Double.isNaN(from)) {
            double[] dd = drift();
            double l = Math.hypot(dd[0], dd[1]);
            if (l > 1e-6) return new double[]{dd[0] / l, dd[1] / l};
            from = windFromDeg;
        }
        double a = Math.toRadians(from);
        return new double[]{-Math.sin(a), Math.cos(a)};
    }

    /** Neue Karte, wenn sich Richtung (über 5°) oder Obergrenze des Vorstoßes (über 20 m) geändert haben. */
    private void advKey() {
        if (Double.isNaN(advance)) return;
        double[] to = advanceTo();
        int key = (int) Math.round(Math.toDegrees(Math.atan2(to[0], to[1])) / 5) * 10_000 + (int) Math.round((Double.isNaN(seaTopTarget) ? 0 : seaTopTarget - advancePass) / 20);
        if (key != advKeyBuilt) { advKeyBuilt = key; mapVersion++; }
    }

    /** Wohin die Schwaden ziehen (m/s, x Ost, z Süd): 80 % des Winds am Anker. */
    public double[] drift() {
        Wind w = wind;
        if (w != null) {
            double[] p = windAnchor, v = w.at(p[0], p[1], p[2]);
            return new double[]{v[0] * Fog.DRIFT, v[2] * Fog.DRIFT};
        }
        double a = Math.toRadians(windFromDeg + 180), v = windSpeed * Fog.DRIFT;
        return new double[]{Math.sin(a) * v, -Math.cos(a) * v};
    }

    // ------------------------------------------------------------------ Schnappschuss

    /** Unveränderliche Werte für Grafikkarte und Abfragen. */
    public record Snapshot(double seaBase, double seaTop, double seaSigma, double seaRough, double valleyFill, double valleySigma,
                           double capBase, double capSigma, double capBelt, double bankSize, double wisps, double offX, double offZ,
                           int seed, Quality quality, double time,
                           double advance, double advanceSoft, double advanceFingers, double toX, double toZ, double[] rect,
                           double billows, double billowSize) {
        public boolean sea() { return seaSigma > 1e-7; }
        /** Seit LituyaBay1958 W7: stößt das Nebelmeer vor (sonst liegt es überall)? */
        public boolean advancing() { return !Double.isNaN(advance); }
        public boolean valley() { return valleyFill > 1e-3 && valleySigma > 1e-7; }
        public boolean cap() { return capSigma > 1e-7; }
        public boolean any() { return sea() || valley() || cap(); }
        /**
         * Seit LituyaBay1958 W7: derselbe Stand mit einer anderen Untergrenze des Nebelmeers, etwa der Wasserspiegel für
         * ein Spiegelbild (dessen Strahl beginnt unter dem Spiegel, dort darf kein Nebel liegen).
         */
        public Snapshot withSeaBase(double b) {
            return new Snapshot(b, seaTop, seaSigma, seaRough, valleyFill, valleySigma, capBase, capSigma, capBelt, bankSize, wisps, offX, offZ,
                    seed, quality, time, advance, advanceSoft, advanceFingers, toX, toZ, rect, billows, billowSize);
        }
    }

    public Snapshot snapshot() { return snapshot; }

    private Snapshot build() {
        boolean sea = seaAmount > 1e-4 && !Double.isNaN(seaBase);
        boolean cap = !Double.isNaN(capBase);
        return new Snapshot(sea ? seaBase : 0, sea ? seaTop : 0, sea ? seaAmount * Fog.SIGMA_PER_VIS / Math.max(1, seaVisibility) : 0, Math.max(0, seaRough),
                Math.max(0, valleyFill), Fog.SIGMA_PER_VIS / Math.max(1, valleyVisibility),
                cap ? capBase : 0, cap ? Fog.SIGMA_PER_VIS / Math.max(1, capVisibility) : 0, Math.max(0, capBelt),
                Math.max(10, bankSize), clamp(wisps, 0, 1), offX, offZ, (int) (seed * 0x9E3779B1L) + 77, quality, time,
                advance, Math.max(1, advanceSoft), clamp(advanceFingers, 0, 1), advanceTo()[0], advanceTo()[1], mapRect.clone(),
                Math.max(0, billows), Math.max(20, billowSize));
    }

    // ------------------------------------------------------------------ Karte

    /** Werte je Zelle der Karte: Boden (m), Tiefe der Kaltluft (m), Gürtel unter der Decke (m), seit LituyaBay1958 W7 der Weg des Vorstoßes (m). */
    public static final int MAP_STRIDE = 4;

    /**
     * Karte über {@link #mapRect} mit n × n Zellen (Mitten der Zellen): Boden, Kaltluft, wie weit die Decke am Ort
     * tiefer reicht (aus dem Aufwind 40 m über Grund · {@link #liftPerMs}, 0 bis {@code capBelt}). Dazu am Ende
     * {kleinster Boden, größter Boden, größte Kaltluft, größter Gürtel}.
     */
    public float[] buildMap(int n) {
        double[] r = mapRect;
        Ground g = ground;
        ColdPool c = cold;
        Wind w = wind;
        double lift = liftPerMs, belt = capBelt;
        float[] m = new float[n * n * MAP_STRIDE + 4];
        double gMin = 1e9, gMax = -1e9, cMax = 0, bMax = 0;
        double cell = r[2] / n;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = r[0] + (i + 0.5) * cell, z = r[1] + (j + 0.5) * cell;
                double y = g != null ? g.height(x, z) : 0;
                double cd = c != null ? Math.max(0, c.depth(x, z)) : 0;
                double lo = 0;
                if (w != null) lo = clamp(lift * w.at(x, y + 40, z)[1], 0, belt);
                int o = (j * n + i) * MAP_STRIDE;
                m[o] = (float) y;
                m[o + 1] = (float) cd;
                m[o + 2] = (float) lo;
                gMin = Math.min(gMin, y); gMax = Math.max(gMax, y); cMax = Math.max(cMax, cd); bMax = Math.max(bMax, lo);
            }
        int o = n * n * MAP_STRIDE;
        m[o] = (float) gMin; m[o + 1] = (float) gMax; m[o + 2] = (float) cMax; m[o + 3] = (float) bMax;
        advanceWay(m, n, r);
        return m;
    }

    /**
     * Seit LituyaBay1958 W7: der Weg des Vorstoßes je Zelle (Kanal 3). Start an den Rändern, die zur Herkunft zeigen, mit
     * dem Abstand in Windrichtung (eine gerade Front kommt an); weiter mit Dijkstra über acht Nachbarn durch Zellen, deren
     * Boden {@link #advancePass} unter der Obergrenze liegt; quer zum Wind zählt ein Schritt 1,5-mal, gegen ihn 3,3-mal
     * so viel [G]. Zellen darüber bekommen den Weg ihres Nachbarn, führen aber nicht weiter. Ohne Vorstoß überall 0.
     */
    void advanceWay(float[] m, int n, double[] r) {
        if (Double.isNaN(advance)) { for (int k = 0; k < n * n; k++) m[k * MAP_STRIDE + 3] = 0; return; }
        double[] to = advanceTo();
        double cell = r[2] / n, cx = r[0] + r[2] / 2, cz = r[1] + r[2] / 2, half = r[2] / 2 * (Math.abs(to[0]) + Math.abs(to[1]));
        double pass = Double.isNaN(seaTopTarget) ? 1e9 : seaTopTarget - advancePass;
        double[] dist = new double[n * n];
        java.util.Arrays.fill(dist, FAR);
        java.util.PriorityQueue<double[]> q = new java.util.PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                boolean edge = (i == 0 && to[0] > 0.2) || (i == n - 1 && to[0] < -0.2) || (j == 0 && to[1] > 0.2) || (j == n - 1 && to[1] < -0.2);
                int k = j * n + i;
                if (!edge || m[k * MAP_STRIDE] >= pass) continue;
                double x = r[0] + (i + 0.5) * cell, z = r[1] + (j + 0.5) * cell;
                dist[k] = Math.max(0, (x - cx) * to[0] + (z - cz) * to[1] + half);
                q.add(new double[]{dist[k], k});
            }
        int[] di = {1, -1, 0, 0, 1, 1, -1, -1}, dj = {0, 0, 1, -1, 1, -1, 1, -1};
        double[] cost = new double[8];
        for (int e = 0; e < 8; e++) {
            double len = Math.hypot(di[e], dj[e]), c = (di[e] * to[0] + dj[e] * to[1]) / len;
            cost[e] = len * cell / (0.3 + 0.7 * (0.5 + 0.5 * c));
        }
        while (!q.isEmpty()) {
            double[] top = q.poll();
            int k = (int) top[1];
            if (top[0] > dist[k]) continue;
            if (m[k * MAP_STRIDE] >= pass) continue;          // über der Obergrenze: erreicht, aber nicht weiter
            int i = k % n, j = k / n;
            for (int e = 0; e < 8; e++) {
                int a = i + di[e], b = j + dj[e];
                if (a < 0 || b < 0 || a >= n || b >= n) continue;
                int kk = b * n + a;
                double nd = top[0] + cost[e];
                if (nd < dist[kk]) { dist[kk] = nd; q.add(new double[]{nd, kk}); }
            }
        }
        for (int k = 0; k < n * n; k++) m[k * MAP_STRIDE + 3] = (float) dist[k];
    }

    /** Werte der Karte am Ort (bilinear) als {Boden, Kaltluft, Gürtel, Weg des Vorstoßes}, oder null außerhalb. */
    public static float[] mapAt(float[] map, int n, double[] rect, double x, double z) {
        if (map == null) return null;
        double u = (x - rect[0]) / rect[2] * n - 0.5, v = (z - rect[1]) / rect[2] * n - 0.5;
        if (u < -0.5 || v < -0.5 || u > n - 0.5 || v > n - 0.5) return null;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fu = u - i, fv = v - j;
        int i0 = Math.max(0, Math.min(n - 1, i)), i1 = Math.max(0, Math.min(n - 1, i + 1)), j0 = Math.max(0, Math.min(n - 1, j)), j1 = Math.max(0, Math.min(n - 1, j + 1));
        float[] out = new float[4];
        for (int k = 0; k < 4; k++) {
            double a = map[(j0 * n + i0) * MAP_STRIDE + k], b = map[(j0 * n + i1) * MAP_STRIDE + k], c = map[(j1 * n + i0) * MAP_STRIDE + k], d = map[(j1 * n + i1) * MAP_STRIDE + k];
            out[k] = (float) ((a + (b - a) * fu) * (1 - fv) + (c + (d - c) * fu) * fv);
        }
        return out;
    }

    // ------------------------------------------------------------------ Feld (wie terrain_fog_common.glsl)

    /** Obergrenze des Nebelmeers am Ort (m). */
    public static double seaTopAt(Snapshot s, double x, double z) {
        double qx = (x + s.offX()) / s.bankSize(), qz = (z + s.offZ()) / s.bankSize();
        double n = 0.7 * Fog.vnoise(qx + 3, qz + 1, s.seed() + 11) + 0.3 * Fog.vnoise(qx * 3.1 + 7, qz * 3.1 + 2, s.seed() + 12);
        double top = s.seaTop() + s.seaRough() * (2 * n - 1);
        if (s.billows() > 0) top += s.billows() * billow(s, x, z);
        return top;
    }

    /** Seit LituyaBay1958 W7: Kuppen auf dem Nebelmeer −1..1 (rund: oben breit, unten schmal), wie tfBillow im Shader. */
    public static double billow(Snapshot s, double x, double z) {
        double bx = (x + s.offX()) / s.billowSize(), bz = (z + s.offZ()) / s.billowSize();
        double b = 0.62 * Fog.vnoise(bx + 11, bz + 5, s.seed() + 13) + 0.38 * Fog.vnoise(bx * 2.3 + 2, bz * 2.3 + 9, s.seed() + 14);
        return 2 * Math.sqrt(Math.max(0, b)) - 1.3;
    }

    /**
     * Auslöschungskoeffizient (1/m) am Ort, m = Werte der Karte dort ({@link #mapAt}) oder null. Zeile für Zeile wie
     * {@code tfDensity} im Shader.
     */
    public static double density(Snapshot s, float[] m, double x, double y, double z) {
        double d = 0;
        if (s.sea()) {
            double top = seaTopAt(s, x, z);
            d += s.seaSigma() * smooth(s.seaBase() - 15, s.seaBase() + 15, y) * smooth(top + 6, top - 6, y) * advanceFactor(s, m, x, z);
        }
        if (m != null) {
            double hag = y - m[0];
            if (s.valley() && m[1] > 0.5 && hag > -3) {
                double depth = m[1] * s.valleyFill();
                d += s.valleySigma() * smooth(depth + 4, depth * 0.75 - 2, hag);
            }
            if (s.cap() && m[2] > 0.5) {
                double lb = s.capBase() - m[2];
                d += s.capSigma() * smooth(lb - 15, lb + 25, y) * smooth(s.capBase() + 40, s.capBase(), y);
            }
        }
        if (d <= 0) return 0;
        if (s.wisps() > 0) {
            double q = Fog.vnoise((x + s.offX() * 1.15) / 90 + y / 37, (z + s.offZ() * 1.15) / 90 - y / 29, s.seed() + 5);
            d *= Math.max(0, 1 + s.wisps() * (q - 0.5) * 1.6);
        }
        return d;
    }

    /**
     * Seit LituyaBay1958 W7: 1 hinter der Front des Vorstoßes, 0 vor ihr (ohne Vorstoß immer 1). Weg aus der Karte (m =
     * {@link #mapAt}), außerhalb in Windrichtung vom Rand gezählt; Finger aus Rauschen (1,2 km, ziehen mit). Wie
     * tfAdvance im Shader.
     */
    public static double advanceFactor(Snapshot s, float[] m, double x, double z) {
        if (!s.advancing()) return 1;
        double way;
        if (m != null && m.length > 3) way = m[3];
        else {
            double[] r = s.rect();
            double cx = r[0] + r[2] / 2, cz = r[1] + r[2] / 2, half = r[2] / 2 * (Math.abs(s.toX()) + Math.abs(s.toZ()));
            way = Math.max(0, (x - cx) * s.toX() + (z - cz) * s.toZ() + half);
        }
        if (way >= FAR * 0.5) return 0;
        double soft = s.advanceSoft();
        if (s.advanceFingers() > 0) {
            double nn = 0.65 * Fog.vnoise((x + s.offX()) / 1200 + 5, (z + s.offZ()) / 1200 + 3, s.seed() + 21)
                      + 0.35 * Fog.vnoise((x + s.offX()) / 430 + 1, (z + s.offZ()) / 430 + 8, s.seed() + 22);
            way += s.advanceFingers() * (nn - 0.5) * 2 * (soft * 1.6 + 250);
        }
        return smooth(s.advance() + soft, s.advance() - soft, way);
    }

    // ------------------------------------------------------------------ Abfragen (Prozessor)
    private volatile float[] cpuMap;
    private volatile int cpuMapN, cpuMapVersion = Integer.MIN_VALUE;
    private volatile double[] cpuMapRect;

    /** Karte für die Abfragen auf dem Prozessor (64 × 64, neu bei {@link #mapVersion}). */
    private float[] cpuMap() {
        if (cpuMapVersion != mapVersion || cpuMap == null || cpuMapRect != mapRect) {
            cpuMapN = 64;
            cpuMapRect = mapRect;
            cpuMap = buildMap(cpuMapN);
            cpuMapVersion = mapVersion;
        }
        return cpuMap;
    }

    /** Dichte (1/m) am Ort mit der Karte des Prozessors. */
    public double density(double x, double y, double z) {
        float[] map = cpuMap();
        return density(snapshot, mapAt(map, cpuMapN, cpuMapRect, x, z), x, y, z);
    }

    /** Waagerechte Sicht am Ort (m, bis 100 km). */
    public double visibilityAt(double x, double y, double z) {
        double s = density(x, y, z);
        return s <= Fog.SIGMA_PER_VIS / 1e5 ? 1e5 : Fog.SIGMA_PER_VIS / s;
    }

    /** Durchlässigkeit zwischen zwei Punkten (64 Schritte). */
    public double transmittance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz), tau = 0;
        for (int i = 0; i < 64; i++) {
            double t = (i + 0.5) / 64;
            tau += density(ax + dx * t, ay + dy * t, az + dz * t) * len / 64;
            if (tau > 30) break;
        }
        return Math.exp(-tau);
    }

    /** „Nebelmeer 470–840 m (100 %) · Talnebel 60 % · Stau ab 850 m, Gürtel bis 260 m“ */
    public String describe() {
        Snapshot s = snapshot;
        StringBuilder b = new StringBuilder();
        if (s.sea()) b.append(String.format(Locale.GERMAN, "Nebelmeer %.0f–%.0f m (%.0f %%)", seaBase, seaTop, seaAmount * 100));
        if (s.sea() && s.advancing()) b.append(String.format(Locale.GERMAN, ", Vorstoß %.1f km weit (%+.1f m/s)", advance / 1000, advanceRate));
        if (s.valley()) b.append(b.length() > 0 ? " · " : "").append(String.format(Locale.GERMAN, "Talnebel %.0f %% der Kaltluft", s.valleyFill() * 100));
        if (s.cap()) b.append(b.length() > 0 ? " · " : "").append(String.format(Locale.GERMAN, "Stau ab %.0f m, Gürtel am Luvhang bis %.0f m tiefer", s.capBase(), s.capBelt()));
        return b.length() == 0 ? "kein Nebel am Berg" : b.toString();
    }

    // ------------------------------------------------------------------ Schatten im Nebel (seit Brocken Phase 7)

    /** Werte je Schattenwerfer in {@code casters}: Achse von (x, y, z) bis (x, y, z), Halbmesser (m). */
    public static final int CASTER_STRIDE = 7;
    /** Höchstens so viele Schattenwerfer nimmt die Grafik. */
    public static final int MAX_CASTERS = 8;

    /**
     * Anteil direkten Sonnenlichts am Ort (x, y, z) hinter Schattenwerfern (1 = frei), Zeile für Zeile wie
     * {@code tfSunShadow} in {@code gl/terrain_fog_common.glsl}. Jeder Körper ist eine Kapsel (Achse, Halbmesser); sein
     * Schatten eine Säule von der Sonne weg ({@code sunDir} zeigt zur Sonne), deren Rand mit dem Abstand s um
     * s · tan(sunRadius) weich wird; hinter dem Kernschatten (r / tan(sunRadius), für einen Menschen rund 54 m) bleibt
     * ein flacher Halbschatten. So entsteht im Nebel das Brockengespenst.
     */
    public static double sunShadow(double[] casters, double[] sunDir, double sunRadius, double x, double y, double z) {
        if (casters == null) return 1;
        double lit = 1;
        double lx = sunDir[0], ly = sunDir[1], lz = sunDir[2];
        for (int i = 0; i + CASTER_STRIDE <= casters.length && i / CASTER_STRIDE < MAX_CASTERS; i += CASTER_STRIDE) {
            double ax = casters[i], ay = casters[i + 1], az = casters[i + 2], r = casters[i + 6];
            double px = x - ax, py = y - ay, pz = z - az;
            double ex = casters[i + 3] - ax, ey = casters[i + 4] - ay, ez = casters[i + 5] - az;
            double pl = px * lx + py * ly + pz * lz, s = -pl;
            if (s <= 0) continue;
            double qx = px - lx * pl, qy = py - ly * pl, qz = pz - lz * pl;
            double el = ex * lx + ey * ly + ez * lz;
            ex -= lx * el; ey -= ly * el; ez -= lz * el;
            double ee = ex * ex + ey * ey + ez * ez;
            double h = ee > 1e-8 ? clamp((qx * ex + qy * ey + qz * ez) / ee, 0, 1) : 0;
            double dx = qx - ex * h, dy = qy - ey * h, dz = qz - ez * h, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double w = Math.max(1e-3, s * Math.tan(sunRadius));
            double occ = clamp((r + w - dist) / (2 * w), 0, 1) * Math.min(1, r * r / (w * w));
            lit *= 1 - occ;
        }
        return lit;
    }

    /**
     * Ein stehender Mensch als zwei Schattenwerfer (Körper und Kopf), Füße bei (x, y, z), Größe in m [G: Schultern
     * 0,25 m Halbmesser, Kopf 0,12 m]. Das Auge eines Wanderers liegt im Kopf: dann steht der Schatten genau im
     * Gegenpunkt der Sonne, mitten in der Glorie.
     */
    public static double[] person(double x, double y, double z, double height) {
        double hgt = Math.max(1, height);
        return new double[]{
                x, y + 0.25, z, x, y + hgt * 0.80, z, 0.25,
                x, y + hgt * 0.90, z, x, y + hgt * 0.97, z, 0.12};
    }

    // ------------------------------------------------------------------ Hilfen

    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }

    /** smoothstep, der auch mit a > b fällt (wie im Shader). */
    static double smooth(double a, double b, double x) {
        double t = clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static double mod(double v, double p) { double r = v % p; return r < 0 ? r + p : r; }
}
