package com.dan.wetglass;

import java.util.Locale;
import java.util.Random;

/**
 * Tropfen auf einer Scheibe oder einer anderen glatten Fläche. Jeder Tropfen hat ein Volumen, das als Kugelkappe auf
 * der Fläche sitzt (Kontaktwinkel 60°). Er haftet, solange seine Kontaktlinie ihn hält. Wird er groß genug, rutscht er,
 * und zwar ruckartig: Die Fläche hat Haftstellen, an denen er hängen bleibt, bis ein anderer Tropfen ihn einholt oder er
 * sich losreißt. Unterwegs lässt er kleine Resttröpfchen zurück, verliert dabei Wasser und zieht einen nassen Film, auf
 * dem spätere Tropfen leichter laufen. Berühren sich zwei Tropfen, verschmelzen sie mit einem Satz zu einem größeren.
 * <p>
 * Die Fläche ist ein Rechteck von {@link #width} × {@link #height} m in Flächenkoordinaten (x nach rechts, y nach oben).
 * Wohin die Schwerkraft zieht, sagt {@link #slopeX}/{@link #slopeY} (Anteil von g entlang der Fläche; eine senkrechte
 * Scheibe hat (0, −1), eine liegende (0, 0)). Wind über der Fläche schiebt die Tropfen. Einschläge kommen als Rate
 * ({@link #hitRate}) oder einzeln ({@link #hit}); {@link #wipe} wischt.
 * <p>
 * Dazu kommen drei Schichten, die keine einzelnen Tropfen sind: <b>Beschlag</b> auf der Innenseite
 * ({@link #condensation}, mit {@link #wipeInside} gewischt, die Spur beschlägt langsamer wieder), eine <b>Salzkruste</b>
 * außen aus Sprühnebel und salzigen Einschlägen ({@link #saltRate}, {@link #spray}), die Regen und Läufer wieder lösen,
 * und die <b>Überspülung</b> ({@link #flood}): Ein Schwall legt eine Wasserhaut über die Fläche, die abläuft und
 * dann in viele Tropfen zerreißt.
 * <p>
 * Gekrümmte Flächen (F10): Mit {@link #surface} rechnet die Simulation im Flächenraum einer beliebigen Fläche, die je
 * Ort sagt, wohin die Schwerkraft zieht, ob ein Tropfen aufliegt oder hängt, wie der Wind entlang der Fläche weht und
 * wie sehr der Ort dem Regen zugewandt ist. {@link Cylinder} ist so eine Fläche, stehend (Turmwand, Pfosten) oder
 * liegend (Handlauf, Rohr).
 * <p>
 * Nur Java 21. Das Paket kennt weder Regen noch Gischt noch die Szene. Gleicher {@link #seed}, gleiche Tropfen.
 */
public final class Wetglass {

    /** Stufen: höchstens Tropfen je Fläche und Auflösung der Karte (Texel über die Breite). */
    public enum Quality {
        LOW(1_500, 512), MEDIUM(3_000, 1024), HIGH(4_000, 2048), ULTRA(6_000, 2048);
        public final int maxDrops, mapRes;
        Quality(int d, int r) { maxDrops = d; mapRes = r; }
    }

    // ------------------------------------------------------------------ Eingänge

    /**
     * Größe der Fläche in m. Mit {@link #periodic} ist sie eine Kachel, die sich nahtlos wiederholt (rechts geht es
     * links weiter, unten oben): So reicht eine Kachel von 35 cm für eine große Scheibe, und die Tropfen stehen so
     * dicht wie in Wirklichkeit.
     */
    public volatile double width = 0.35, height = 0.35;
    /** Kachel statt Rechteck (siehe oben). */
    public volatile boolean periodic = true;
    /**
     * Wie lang die echte Scheibe in Laufrichtung ist (m): Ein Tropfen, der unten aus der Kachel läuft, kommt oben
     * wieder herein, nur mit der Wahrscheinlichkeit Kachel/Scheibe läuft er unten ab.
     */
    public volatile double drainLength = 1.05;
    /** Schwerkraft entlang der Fläche als Anteil von g: senkrecht (0, −1), geneigt kürzer, liegend (0, 0). */
    public volatile double slopeX = 0, slopeY = -1;
    /** Wind über der Fläche in m/s (Komponenten in Flächenkoordinaten): schiebt die Tropfen. */
    public volatile double windX, windY;
    /** Einschläge je m² und Sekunde (Tropfen ab 0,3 mm) und Λ der Größenverteilung in 1/mm (Marshall-Palmer). */
    public volatile double hitRate, hitLambda = 3;
    /** Verdunstung: Halbmesser schrumpft um so viel m/s (im Regen fast nichts, in der Sonne mehr). */
    public volatile double evaporation = 2e-8;
    /** Wie lange der nasse Film einer Spur hält (s). */
    public volatile double filmLife = 90;
    public volatile Quality quality = Quality.MEDIUM;
    public volatile int seed = 1885;
    /**
     * Gekrümmte oder unebene Fläche: Schwerkraft, Wind und Regen je Ort. {@code null} = eben, dann gelten
     * {@link #slopeX}/{@link #slopeY} und {@link #windX}/{@link #windY} überall gleich.
     */
    public volatile Surface surface;
    /**
     * Beschlag innen: so viel Wasser kondensiert je Sekunde auf der Innenseite, als Schichtdicke in m/s (rund 1e-8, wenn
     * es drinnen warm und feucht und draußen kalt ist). Negativ trocknet der Beschlag ab.
     */
    public volatile double condensation;
    /** Salz aus dem Sprühnebel: Bedeckung je Sekunde (1 = weiße Kruste von rund 5 g/m²). */
    public volatile double saltRate;

    // ------------------------------------------------------------------ Konstanten

    /** Kontaktwinkel (Glas, leicht verschmutzt) in Grad. */
    public static final double CONTACT_DEG = 60;
    /** Kritischer Kontakthalbmesser auf einer senkrechten Scheibe bei mittlerer Haftung: darüber rutscht ein Tropfen. */
    public static final double A_CRIT = 1.6e-3;
    /** Größter Kontakthalbmesser (größere Tropfen reißen ab). */
    public static final double A_MAX = 4.5e-3;
    /** Kleinster Tropfen, der noch zählt (darunter verdunstet er sofort). */
    public static final double A_MIN = 5e-5;
    /** Kantenlänge des Films und der Haftkarte in Zellen über die Breite. */
    public static final int GRID = 128;
    /**
     * Feintropfen: Einschläge mit kleinerem Kontakthalbmesser bleiben nicht als einzelne Tropfen, sondern füllen eine
     * Bedeckung 0..1 je Zelle (viele Tröpfchen bis 0,6 mm, die der Shader aus der Bedeckung zeichnet). Voll ist sie
     * bei rund 4 µm Wasser (4 cm³ je m²).
     */
    public static final double A_FINE = 0.5e-3, FINE_FULL = 4e-6;
    /** Beschlag: voll bei 1 µm Wasser (1 g/m²) auf der Innenseite. */
    public static final double FOG_FULL = 1e-6;
    /**
     * Salz: Bedeckung 1 entspricht rund 5 g/m² (eine weiße Kruste). Seewasser trägt 35 kg/m³; um eine volle Kruste zu
     * lösen, braucht es rund 14 µm Süßwasser (Löslichkeit 360 g/l).
     */
    public static final double SALT_FULL = 5, SEA_SALT = 35_000, SALT_WASH = 14e-6;
    /** Hängende Tropfen (unter einer Fläche) fallen ab diesem Kontakthalbmesser ab (bei mittlerer Haftung). */
    public static final double A_DRIP = 2.4e-3;
    /** Unter dieser Dicke zerreißt eine ablaufende Wasserhaut in Tropfen (m). */
    public static final double SHEET_BREAK = 0.06e-3;

    // ------------------------------------------------------------------ Zustand (Struktur aus Feldern)

    private volatile int n;   // ohne Sperre lesbar (Avatar-Phase 19: count() wartete im Bild bis 2 s auf step())
    private double[] x = new double[0], y = new double[0], a = new double[0], vx = new double[0], vy = new double[0], trail = new double[0];
    private boolean[] moving = new boolean[0];
    private float[] film = new float[0], pin = new float[0], fine = new float[0], fog = new float[0], smear = new float[0], salt = new float[0];
    private int gw, gh;
    private Random rnd;
    private double time, hitAcc, builtW = -1, builtH = -1;
    private int builtSeed;
    private long hits, merges, residues, slides, drips, floods;
    private double sheet;
    private boolean sheetSalty;
    private final double[] sf = new double[6];
    private volatile Snapshot snapshot;

    public Wetglass() { reset(); }

    // ------------------------------------------------------------------ Voreinstellungen

    public static Wetglass calm() { return beaufort(1); }
    public static Wetglass breeze() { return beaufort(5); }
    public static Wetglass gale() { return beaufort(8); }
    public static Wetglass storm() { return beaufort(10); }

    /**
     * Eine senkrechte Scheibe auf der Wetterseite bei Wind nach Beaufort und dem Regen, der dazu oft fällt [G]: Der
     * Wind drückt die Tropfen seitwärts, die Rate folgt Marshall-Palmer und der Neigung des Regens.
     */
    public static Wetglass beaufort(int bft) {
        int b = Math.max(0, Math.min(12, bft));
        Wetglass w = new Wetglass();
        w.rain(RATE[b], WIND[b], 1);
        w.windX = WIND[b] * 0.35;
        return w;
    }

    static final double[] WIND = {0.2, 0.9, 2.4, 4.4, 6.7, 9.3, 12.3, 15.5, 18.9, 22.6, 26.4, 30.5, 34.0};
    static final double[] RATE = {0.2, 0.3, 0.5, 0.8, 1.2, 2, 3, 5, 8, 12, 20, 30, 40};

    /**
     * Regen auf diese Fläche: Rate in mm/h, Wind in m/s und wie sehr die Fläche dem Regen zugewandt ist (1 = voll
     * gegen den Wind, 0 = abgewandt). Rechnet Einschläge je m² und Λ nach Marshall-Palmer (eine Kopie der Formeln
     * aus dem Regen, damit das Paket allein steht): Eine senkrechte Fläche fängt so viel, wie der Wind den Regen
     * neigt, eine liegende alles.
     */
    public void rain(double rateMmH, double windMs, double facing) {
        if (rateMmH <= 0) { hitRate = 0; return; }
        double lam = 4.1 * Math.pow(rateMmH, -0.21), sum = 0, dd = 0.02;
        for (double d = 0.3 + dd / 2; d < 6; d += dd) sum += 8000 * Math.exp(-lam * d) * Math.max(0.5, 9.65 - 10.3 * Math.exp(-0.6 * d)) * dd;
        double vt = 6.5, tilt = Math.hypot(slopeX, slopeY);
        // senkrechte Fläche: Anteil |wind|/|v|; liegende: 1
        double vertical = Math.min(1, tilt);
        double share = (1 - vertical) + vertical * Math.max(0, Math.min(1, facing)) * windMs / Math.hypot(windMs, vt);
        hitRate = sum * share;
        hitLambda = lam;
    }

    // ------------------------------------------------------------------ Lauf

    public double time() { return time; }

    /** Alles trocken, Haftkarte neu (gleicher seed, gleiche Haftstellen). */
    public synchronized void reset() {
        rnd = new Random(seed);
        n = 0;
        time = hitAcc = 0;
        hits = merges = residues = slides = drips = floods = 0;
        sheet = 0;
        sheetSalty = false;
        buildGrids();
        snapshot = build();
    }

    private void buildGrids() {
        gw = GRID;
        gh = Math.max(4, (int) Math.round(GRID * height / width));
        film = new float[gw * gh];
        fine = new float[gw * gh];
        fog = new float[gw * gh];
        smear = new float[gw * gh];
        salt = new float[gw * gh];
        // Haftkarte: Wertrauschen aus zwei Oktaven, 0,6 bis 1,5 (Schmutz, Kratzer, Kalk)
        pin = new float[gw * gh];
        Random r = new Random(seed * 31L + 7);
        int c1 = 16, c2 = 48;
        double[] o1 = new double[(c1 + 1) * (c1 + 1)], o2 = new double[(c2 + 1) * (c2 + 1)];
        for (int i = 0; i < o1.length; i++) o1[i] = r.nextDouble();
        for (int i = 0; i < o2.length; i++) o2[i] = r.nextDouble();
        for (int j = 0; j < gh; j++)
            for (int i = 0; i < gw; i++) {
                double u = i / (double) gw, v = j / (double) gh;
                double val = 0.65 * smooth(o1, c1, u, v) + 0.35 * smooth(o2, c2, u, v);
                pin[j * gw + i] = (float) (0.6 + 0.9 * val * val * (3 - 2 * val));
            }
        builtW = width;
        builtH = height;
        builtSeed = seed;
    }

    private static double smooth(double[] g, int c, double u, double v) {
        double fx = u * c, fy = v * c;
        int ix = Math.min(c - 1, (int) fx), iy = Math.min(c - 1, (int) fy);
        double tx = fx - ix, ty = fy - iy;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        // kachelbar: das Gitter wiederholt sich nach c Zellen
        int x1 = (ix + 1) % c, y1 = (iy + 1) % c;
        double a0 = g[iy * (c + 1) + ix], b0 = g[iy * (c + 1) + x1], a1 = g[y1 * (c + 1) + ix], b1 = g[y1 * (c + 1) + x1];
        return (a0 + (b0 - a0) * tx) + ((a1 + (b1 - a1) * tx) - (a0 + (b0 - a0) * tx)) * ty;
    }

    /** Haftung 0,6..1,5 am Ort (bilinear in der Haftkarte). */
    public double pinningAt(double px, double py) { return grid(pin, px, py); }

    /** Nasser Film 0..1 am Ort. */
    public double filmAt(double px, double py) { return grid(film, px, py); }

    /** Bedeckung mit Feintropfen 0..1 am Ort. */
    public double fineAt(double px, double py) { return grid(fine, px, py); }

    /** Beschlag 0..1 am Ort (Innenseite). */
    public double fogAt(double px, double py) { return Math.min(1, grid(fog, px, py)); }

    /** Salzkruste am Ort (1 = rund 5 g/m²). */
    public double saltAt(double px, double py) { return grid(salt, px, py); }

    /** Mittlere Bedeckung mit Feintropfen über die ganze Fläche. */
    public synchronized double fineMean() { return mean(fine, 1); }

    /** Mittlerer Beschlag 0..1. */
    public synchronized double fogMean() { return mean(fog, 1); }

    /** Mittlere Salzkruste (1 = rund 5 g/m²). */
    public synchronized double saltMean() { return mean(salt, 99); }

    /** Dicke der Wasserhaut nach einer Überspülung in mm (0 = keine). */
    public synchronized double sheetMm() { return sheet * 1000; }

    private static double mean(float[] g, double cap) {
        double s = 0;
        for (float f : g) s += Math.min(cap, f);
        return g.length == 0 ? 0 : s / g.length;
    }

    private double grid(float[] g, double px, double py) {
        double fx = px / width * gw - 0.5, fy = py / height * gh - 0.5;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
        double tx = fx - ix, ty = fy - iy;
        double a0 = cell(g, ix, iy), b0 = cell(g, ix + 1, iy), a1 = cell(g, ix, iy + 1), b1 = cell(g, ix + 1, iy + 1);
        return (a0 + (b0 - a0) * tx) + ((a1 + (b1 - a1) * tx) - (a0 + (b0 - a0) * tx)) * ty;
    }

    private double cell(float[] g, int i, int j) {
        if (periodic) {
            i = Math.floorMod(i, gw);
            j = Math.floorMod(j, gh);
        } else {
            i = Math.max(0, Math.min(gw - 1, i));
            j = Math.max(0, Math.min(gh - 1, j));
        }
        return g[j * gw + i];
    }

    /** Takt der inneren Schritte in Hz (60; 30 reicht, wenn die Tropfen nur aus der Ferne zu sehen sind). */
    public volatile double stepRate = 60;

    /** Innere Schritte je Sperre: dazwischen kommen andere Aufrufe (Einschläge, Überspülung, Wischen) an die Reihe. */
    public static final int CHUNK = 32;

    /**
     * Einen Schritt weiter (dt in s, intern in Schritten von höchstens 1/{@link #stepRate} s). Seit Sylt Phase 19 hält ein
     * langer Schritt (ein Nachholen über 30 s) die Fläche nicht mehr am Stück: nach je {@link #CHUNK} inneren Schritten
     * gibt er sie kurz frei, so dass {@link #flood}, {@link #hit} oder {@link #wipe} aus einem anderen Faden höchstens
     * so lange warten (bis dahin über eine Sekunde, Lighthouse-Dauerlauf). Die Rechnung selbst bleibt dieselbe.
     */
    public void step(double dt) {
        int sub;
        double h;
        synchronized (this) {
            if (width != builtW || height != builtH || seed != builtSeed) reset();
            sub = Math.max(1, (int) Math.ceil(dt * Math.max(10, stepRate) - 1e-9));
            h = dt / sub;
        }
        for (int s0 = 0; s0 < sub && h > 0; s0 += CHUNK) {
            synchronized (this) {
                int e = Math.min(sub, s0 + CHUNK);
                for (int s = s0; s < e; s++) substep(h);
            }
        }
        synchronized (this) { snapshot = build(); }
    }

    private void substep(double dt) {
        time += dt;
        double area = width * height;
        hitAcc += hitRate * area * dt;
        int cnt = (int) hitAcc;
        hitAcc -= cnt;
        Surface sur = surface;
        for (int k = 0; k < cnt; k++) {
            double d = sampleDiameter(rnd.nextDouble());
            double px = rnd.nextDouble() * width, py = rnd.nextDouble() * height;
            // auf einer gekrümmten Fläche trifft es nur, wo sie dem Regen zugewandt ist
            double keepHit = rnd.nextDouble();
            if (sur != null) {
                sur.at(px, py, sf);
                if (keepHit >= sf[5]) continue;
            }
            double ar = contactRadius(d);
            if (ar < A_FINE) addFine(px, py, volume(ar));
            else addDrop(px, py, ar);
            dissolve(px, py, volume(ar));
            hits++;
            // große Tropfen zerplatzen beim Aufschlag zum Teil in feine Spritzer
            if (d > 2.0) {
                int sp = 1 + rnd.nextInt(3);
                for (int q = 0; q < sp; q++) {
                    double ang = rnd.nextDouble() * 2 * Math.PI, rr = 0.002 + 0.006 * rnd.nextDouble();
                    double sa = (0.08 + 0.12 * rnd.nextDouble()) * 1e-3;
                    addFine(px + Math.cos(ang) * rr, py + Math.sin(ang) * rr, volume(sa));
                }
            }
        }
        // Film trocknet, Feintropfen verdunsten langsam (im Regen kaum)
        float keep = (float) Math.exp(-dt / Math.max(1, filmLife));
        float fkeep = (float) Math.exp(-dt * evaporation / 2e-8 / 1800);
        float skeep = (float) Math.exp(-dt / 900);
        float cond = (float) (condensation * dt / FOG_FULL), srate = (float) (saltRate * dt / 1.05);
        for (int i = 0; i < film.length; i++) {
            film[i] *= keep;
            fine[i] *= fkeep;
            // Beschlag: wo gewischt wurde, keimt er langsamer (der Schmierfilm hält die Spur sichtbar)
            if (cond != 0) fog[i] = Math.max(0, Math.min(1.5f, fog[i] + (cond > 0 ? cond * (1 - 0.6f * smear[i]) : cond)));
            smear[i] *= skeep;
            // Salz aus dem Sprühnebel setzt sich an rauen Stellen mehr ab
            if (srate > 0) salt[i] = Math.min(3, salt[i] + srate * pin[i]);
        }
        if (sheet > 0) { sheet(dt); return; }
        coalesce(dt);
        move(dt);
        mergeAll();
        cull();
    }

    /** Süßwasser (m³) löst Salz um den Ort. */
    private void dissolve(double px, double py, double vol) {
        int k = index(px, py);
        if (k < 0 || salt[k] <= 0) return;
        double cellArea = width / gw * height / gh;
        salt[k] = (float) Math.max(0, salt[k] - vol / (SALT_WASH * cellArea));
    }

    private int index(double px, double py) {
        int i = (int) Math.floor(px / width * gw), j = (int) Math.floor(py / height * gh);
        if (periodic) { i = Math.floorMod(i, gw); j = Math.floorMod(j, gh); }
        else if (i < 0 || j < 0 || i >= gw || j >= gh) return -1;
        return j * gw + i;
    }

    /**
     * Die Wasserhaut einer Überspülung: Sie nimmt alle Tropfen und Feintropfen auf, läuft ab (dünne Haut langsamer, die
     * Menge fließt mit der dritten Potenz der Dicke) und zerreißt unter {@link #SHEET_BREAK} in viele Tropfen und
     * Feintropfen. War es Seewasser, bleibt danach Salz.
     */
    private void sheet(double dt) {
        double area = width * height, cellArea = area / (gw * gh);
        double water = 0;
        for (int i = 0; i < n; i++) water += volume(a[i]);
        for (int i = 0; i < fine.length; i++) { water += fine[i] * FINE_FULL * cellArea; fine[i] = 0; film[i] = 1; }
        n = 0;
        sheet += water / area;
        double g = surface != null ? 0.8 : Math.min(1, Math.hypot(slopeX, slopeY));
        double rate = g * (sheet / 0.3e-3) * (sheet / 0.3e-3) / 0.8;
        sheet *= Math.exp(-dt * rate);
        if (sheet >= SHEET_BREAK && g > 0.02) return;
        // zerreißen: gut zwei Drittel werden Tropfen, der Rest Feintropfen
        double left = sheet * area * 0.7;
        int max = (int) (quality.maxDrops * 0.6);
        while (left > 0 && n < max) {
            double u = rnd.nextDouble();
            double ar = (0.5 + 1.8 * u * u * u) * 1e-3;
            double px = rnd.nextDouble() * width, py = rnd.nextDouble() * height;
            if (surface != null) { surface.at(px, py, sf); if (sf[2] < -0.3 && rnd.nextDouble() < 0.5) ar *= 0.6; }
            addDrop(px, py, ar);
            left -= volume(ar);
        }
        float f = (float) Math.min(0.9, (sheet * area * 0.3 + Math.max(0, left)) / (FINE_FULL * area));
        for (int i = 0; i < fine.length; i++) fine[i] = Math.min(1f, fine[i] + f * (0.6f + 0.4f * pin[i] / 1.5f));
        if (sheetSalty) for (int i = 0; i < salt.length; i++) salt[i] = Math.min(3, salt[i] + 0.6f * pin[i] / 1.05f);
        sheet = 0;
        sheetSalty = false;
    }

    /** Wasser (m³) als Feintropfen um den Ort, weich auf 3 × 3 Zellen verteilt (sonst sieht man das Gitter). */
    private void addFine(double px, double py, double vol) {
        int i0 = (int) Math.floor(px / width * gw), j0 = (int) Math.floor(py / height * gh);
        double cellArea = width / gw * height / gh;
        for (int dj = -1; dj <= 1; dj++)
            for (int di = -1; di <= 1; di++) {
                int i = i0 + di, j = j0 + dj;
                if (periodic) { i = Math.floorMod(i, gw); j = Math.floorMod(j, gh); }
                else if (i < 0 || j < 0 || i >= gw || j >= gh) continue;
                double w = (di == 0 ? 0.5 : 0.25) * (dj == 0 ? 0.5 : 0.25);
                fine[j * gw + i] = (float) Math.min(1, fine[j * gw + i] + w * vol / (FINE_FULL * cellArea));
            }
    }

    /**
     * Wo die Feintropfen dicht stehen, laufen benachbarte zusammen: Ab Bedeckung 0,7 entsteht dort hin und wieder ein
     * Tropfen von 0,6 bis 1 mm, und die Bedeckung sinkt um sein Wasser.
     */
    private void coalesce(double dt) {
        double cellArea = width / gw * height / gh;
        for (int k = 0; k < fine.length; k++) {
            float f = fine[k];
            if (f < 0.7f) continue;
            if (rnd.nextDouble() > (f - 0.7) * 3 * dt) continue;
            double ar = (0.6 + 0.4 * rnd.nextDouble()) * 1e-3;
            double cover = volume(ar) / (FINE_FULL * cellArea);
            fine[k] = (float) Math.max(0, f - cover);
            int i = k % gw, j = k / gw;
            addDrop((i + rnd.nextDouble()) / gw * width, (j + rnd.nextDouble()) / gh * height, ar);
        }
    }

    /** Durchmesser in mm aus der Marshall-Palmer-Verteilung zwischen 0,3 und 5 mm. */
    double sampleDiameter(double u) {
        double lam = Math.max(0.5, hitLambda), d0 = 0.3, d1 = 5;
        return d0 - Math.log(1 - u * (1 - Math.exp(-lam * (d1 - d0)))) / lam;
    }

    /** Kontakthalbmesser in m eines Tropfens mit Durchmesser d mm, der auf Glas zur Kappe mit 60° zerläuft. */
    public static double contactRadius(double dMm) {
        double vol = Math.PI / 6 * Math.pow(dMm * 1e-3, 3);
        return radiusForVolume(vol);
    }

    /** Volumen einer Kappe mit Kontakthalbmesser a (m) und Kontaktwinkel 60°. */
    public static double volume(double a) {
        double th = Math.toRadians(CONTACT_DEG), r = a / Math.sin(th), hh = r * (1 - Math.cos(th));
        return Math.PI / 3 * hh * hh * (3 * r - hh);
    }

    /** Kontakthalbmesser einer Kappe mit diesem Volumen. */
    public static double radiusForVolume(double v) {
        // volume(a) = c·a³
        double c = volume(1);
        return Math.cbrt(Math.max(0, v) / c);
    }

    private void ensure(int cap) {
        if (x.length >= cap) return;
        int m = Math.max(cap, x.length * 2 + 16);
        x = java.util.Arrays.copyOf(x, m);
        y = java.util.Arrays.copyOf(y, m);
        a = java.util.Arrays.copyOf(a, m);
        vx = java.util.Arrays.copyOf(vx, m);
        vy = java.util.Arrays.copyOf(vy, m);
        trail = java.util.Arrays.copyOf(trail, m);
        moving = java.util.Arrays.copyOf(moving, m);
    }

    /** Einen Tropfen setzen (Ort in m, Kontakthalbmesser in m). */
    public synchronized void addDrop(double px, double py, double ar) {
        if (periodic) {
            px = px - width * Math.floor(px / width);
            py = py - height * Math.floor(py / height);
        }
        if (ar < A_MIN || px < 0 || py < 0 || px > width || py > height) return;
        ensure(n + 1);
        x[n] = px;
        y[n] = py;
        a[n] = Math.min(A_MAX, ar);
        vx[n] = vy[n] = 0;
        trail[n] = 0;
        moving[n] = false;
        n++;
    }

    /** Ein einzelner Einschlag (Gischt, ein Tropfen von der Kuppel): Ort in m, Durchmesser in mm. */
    public synchronized void hit(double px, double py, double dMm) {
        double ar = contactRadius(dMm);
        if (ar < A_FINE) addFine(px, py, volume(ar));
        else addDrop(px, py, ar);
        hits++;
    }

    /**
     * Ein salziger Einschlag (Gischt): wie {@link #hit}, das Seewasser lässt beim Trocknen Salz zurück, verteilt über
     * die Zellen um den Ort.
     */
    public synchronized void spray(double px, double py, double dMm) {
        double ar = contactRadius(dMm);
        if (ar < A_FINE) addFine(px, py, volume(ar));
        else addDrop(px, py, ar);
        hits++;
        double cellArea = width / gw * height / gh;
        double add = volume(ar) * SEA_SALT / (SALT_FULL * cellArea);
        int i0 = (int) Math.floor(px / width * gw), j0 = (int) Math.floor(py / height * gh);
        for (int dj = -1; dj <= 1; dj++)
            for (int di = -1; di <= 1; di++) {
                int i = i0 + di, j = j0 + dj;
                if (periodic) { i = Math.floorMod(i, gw); j = Math.floorMod(j, gh); }
                else if (i < 0 || j < 0 || i >= gw || j >= gh) continue;
                double w = (di == 0 ? 0.5 : 0.25) * (dj == 0 ? 0.5 : 0.25);
                salt[j * gw + i] = (float) Math.min(3, salt[j * gw + i] + w * add);
            }
    }

    /**
     * Überspülung: Ein Schwall legt eine Wasserhaut von depthMm über die ganze Fläche (Seewasser, wenn salty). Sie
     * nimmt alle Tropfen auf, läuft in einigen Sekunden ab und zerreißt dann in viele Tropfen.
     */
    public synchronized void flood(double depthMm, boolean salty) {
        if (depthMm <= 0) return;
        sheet = Math.max(sheet, depthMm * 1e-3);
        sheetSalty |= salty;
        floods++;
        sheet(0);
        snapshot = build();
    }

    /** Beschlag innen im Kreis wegwischen; dort bleibt ein Schmierfilm, auf dem er langsamer wiederkommt. */
    public synchronized void wipeInside(double px, double py, double r) {
        for (int j = 0; j < gh; j++)
            for (int i = 0; i < gw; i++) {
                double cx = (i + 0.5) / gw * width - px, cy = (j + 0.5) / gh * height - py;
                if (periodic) { cx -= width * Math.rint(cx / width); cy -= height * Math.rint(cy / height); }
                double dd = Math.sqrt(cx * cx + cy * cy) / r;
                if (dd >= 1) continue;
                // weicher Rand: außen bleibt etwas Beschlag stehen
                float keep = (float) Math.max(0, Math.min(1, (dd - 0.7) / 0.3));
                fog[j * gw + i] *= keep;
                smear[j * gw + i] = Math.max(smear[j * gw + i], 1 - keep);
            }
        snapshot = build();
    }

    /** Den ganzen Beschlag wegwischen. */
    public synchronized void wipeAllInside() {
        java.util.Arrays.fill(fog, 0f);
        java.util.Arrays.fill(smear, 1f);
        snapshot = build();
    }

    /** Wischen: alle Tropfen im Kreis weg, zurück bleibt ein dünner Film. */
    public synchronized void wipe(double px, double py, double r) {
        for (int i = 0; i < n; i++) {
            double dx = x[i] - px, dy = y[i] - py;
            if (periodic) { dx -= width * Math.rint(dx / width); dy -= height * Math.rint(dy / height); }
            if (dx * dx + dy * dy < (r + a[i]) * (r + a[i])) a[i] = 0;
        }
        for (int j = 0; j < gh; j++)
            for (int i = 0; i < gw; i++) {
                double cx = (i + 0.5) / gw * width - px, cy = (j + 0.5) / gh * height - py;
                if (periodic) { cx -= width * Math.rint(cx / width); cy -= height * Math.rint(cy / height); }
                if (cx * cx + cy * cy < r * r) { film[j * gw + i] = Math.max(film[j * gw + i], 0.5f); fine[j * gw + i] = 0; salt[j * gw + i] = 0; }
            }
        cull();
        snapshot = build();
    }

    /**
     * Ein Wischblatt-Strich (Phase 13, für Scheibenwischer): alle Kreise mit Radius r um die Punkte {x0, y0, x1, y1, …}
     * auf einmal wischen; nur die Zellen um jeden Punkt werden besucht, der Zustand wird einmal neu gebaut.
     */
    public synchronized void wipeStroke(double[] pts, double r) {
        if (pts == null || pts.length < 2) return;
        int m = pts.length / 2;
        for (int i = 0; i < n; i++) {
            double rr = (r + a[i]) * (r + a[i]);
            for (int k = 0; k < m; k++) {
                double dx = x[i] - pts[2 * k], dy = y[i] - pts[2 * k + 1];
                if (periodic) { dx -= width * Math.rint(dx / width); dy -= height * Math.rint(dy / height); }
                if (dx * dx + dy * dy < rr) { a[i] = 0; break; }
            }
        }
        double cw = width / gw, ch = height / gh;
        for (int k = 0; k < m; k++) {
            double px = pts[2 * k], py = pts[2 * k + 1];
            int i0 = (int) Math.floor((px - r) / cw), i1 = (int) Math.ceil((px + r) / cw), j0 = (int) Math.floor((py - r) / ch), j1 = (int) Math.ceil((py + r) / ch);
            for (int jj = j0; jj <= j1; jj++)
                for (int ii = i0; ii <= i1; ii++) {
                    int i = ii, j = jj;
                    if (periodic) { i = Math.floorMod(i, gw); j = Math.floorMod(j, gh); }
                    else if (i < 0 || j < 0 || i >= gw || j >= gh) continue;
                    double cx = (ii + 0.5) * cw - px, cy = (jj + 0.5) * ch - py;
                    if (cx * cx + cy * cy < r * r) { film[j * gw + i] = Math.max(film[j * gw + i], 0.5f); fine[j * gw + i] = 0; salt[j * gw + i] = 0; }
                }
        }
        cull();
        snapshot = build();
    }

    /** Alle Tropfen weg (die ganze Scheibe gewischt). */
    public synchronized void wipeAll() {
        n = 0;
        sheet = 0;
        java.util.Arrays.fill(film, 0.35f);
        java.util.Arrays.fill(fine, 0f);
        java.util.Arrays.fill(salt, 0f);
        snapshot = build();
    }

    /** Kritischer Kontakthalbmesser am Ort: Haftung gegen Gewicht und Wind. */
    double critical(double pinning, double drive) {
        return A_CRIT * Math.sqrt(pinning / Math.max(1e-3, drive));
    }

    private void move(double dt) {
        Surface sur = surface;
        double sx = slopeX, sy = slopeY, gn = Math.sqrt(Math.max(0, 1 - sx * sx - sy * sy));
        double wx = windX, wy = windY;
        for (int i = 0; i < n; i++) {
            double ai = a[i];
            if (sur != null) {
                sur.at(x[i], y[i], sf);
                sx = sf[0];
                sy = sf[1];
                gn = sf[2];
                wx = sf[3];
                wy = sf[4];
            }
            // Antrieb: Gewicht entlang der Fläche plus Winddruck (wirkt auf die Fläche ∝ a², Masse ∝ a³)
            double wk = 0.004 * (2e-3 / ai);
            double dx = sx + wx * Math.abs(wx) * wk, dy = sy + wy * Math.abs(wy) * wk;
            double drive = Math.hypot(dx, dy);
            double p = pinningAt(x[i], y[i]) * (1 - 0.45 * filmAt(x[i], y[i])) * (1 + 0.6 * Math.min(1, saltAt(x[i], y[i])));
            // unter einer Fläche hängend: ab einer Größe fällt der Tropfen ab, ein Rest bleibt hängen
            if (gn < -0.05 && ai > A_DRIP * Math.sqrt(p / -gn)) {
                double rest = ai * (0.3 + 0.1 * rnd.nextDouble());
                a[i] = rest;
                vx[i] = vy[i] = 0;
                moving[i] = false;
                drips++;
                continue;
            }
            double ac = critical(p, drive);
            if (ai > ac && drive > 1e-6) {
                if (!moving[i]) slides++;
                moving[i] = true;
                double target = Math.min(0.5, 0.06 * ((ai / ac) * (ai / ac) - 1));
                double ux = dx / drive, uy = dy / drive;
                // Zickzack: quer zur Bahn dorthin, wo es weniger haftet (und auf nassen Spuren)
                double e = 0.004;
                double gpx = (pinningAt(x[i] + e, y[i]) * (1 - 0.45 * filmAt(x[i] + e, y[i])) - pinningAt(x[i] - e, y[i]) * (1 - 0.45 * filmAt(x[i] - e, y[i]))) / (2 * e);
                double gpy = (pinningAt(x[i], y[i] + e) * (1 - 0.45 * filmAt(x[i], y[i] + e)) - pinningAt(x[i], y[i] - e) * (1 - 0.45 * filmAt(x[i], y[i] - e))) / (2 * e);
                double side = -(gpx * -uy + gpy * ux) * 0.012 + (rnd.nextDouble() - 0.5) * 0.15;
                double tx = ux + -uy * side, ty = uy + ux * side, tl = Math.hypot(tx, ty);
                tx /= tl;
                ty /= tl;
                double k = Math.min(1, dt / 0.04);
                vx[i] += (tx * target - vx[i]) * k;
                vy[i] += (ty * target - vy[i]) * k;
                double ox = x[i], oy = y[i];
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
                double step = Math.hypot(x[i] - ox, y[i] - oy);
                double got = paintFilm(ox, oy, x[i], y[i], ai);
                if (got > 0) a[i] = Math.min(A_MAX, radiusForVolume(volume(a[i]) + got));
                trail[i] += step;
                // Resttröpfchen hinter sich lassen
                double spacing = ai * (3 + 4 * rnd.nextDouble());
                if (trail[i] > spacing) {
                    trail[i] = 0;
                    double ar = ai * (0.18 + 0.14 * rnd.nextDouble());
                    double v = volume(ai) - volume(ar);
                    double sp = Math.hypot(vx[i], vy[i]);
                    double bx = sp > 1e-6 ? vx[i] / sp : 0, by = sp > 1e-6 ? vy[i] / sp : -1;
                    a[i] = radiusForVolume(v);
                    addDrop(x[i] - bx * ai * 1.1, y[i] - by * ai * 1.1, ar);
                    residues++;
                }
            } else {
                moving[i] = false;
                double k = Math.exp(-dt / 0.02);
                vx[i] *= k;
                vy[i] *= k;
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
            }
            a[i] -= evaporation * dt;
        }
    }

    /** Spur eines laufenden Tropfens: Film nass, Feintropfen im Weg nimmt er auf. Gibt das aufgenommene Wasser zurück (m³). */
    private double paintFilm(double x0, double y0, double x1, double y1, double ai) {
        int steps = 1 + (int) (Math.hypot(x1 - x0, y1 - y0) / (width / gw));
        double cellArea = width / gw * height / gh, got = 0;
        // Anteil einer Zelle, den die Bahn (Breite 2a) in diesem Schritt überstreicht
        double share = Math.min(1, 2 * ai * Math.hypot(x1 - x0, y1 - y0) / cellArea);
        int last = -1;
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps, px = x0 + (x1 - x0) * t, py = y0 + (y1 - y0) * t;
            int i = (int) Math.floor(px / width * gw), j = (int) Math.floor(py / height * gh);
            if (periodic) { i = Math.floorMod(i, gw); j = Math.floorMod(j, gh); }
            else if (i < 0 || j < 0 || i >= gw || j >= gh) continue;
            int k = j * gw + i;
            film[k] = 1f;
            if (k == last) continue;
            last = k;
            if (salt[k] > 0) salt[k] = (float) Math.max(0, salt[k] - share * 1.5);
            double take = fine[k] * share * 0.8;
            fine[k] -= (float) take;
            got += take * FINE_FULL * cellArea;
        }
        return got;
    }

    // Verschmelzen über ein Gitter mit Zellen von 2 A_MAX
    private int[] head = new int[0], next = new int[0];

    private void mergeAll() {
        double cs = 2 * A_MAX;
        // Zellen mindestens cs groß (bei der Kachel genau aufgeteilt, damit die Nachbarn über den Rand stimmen)
        int cw = Math.max(1, (int) Math.floor(width / cs)), ch = Math.max(1, (int) Math.floor(height / cs));
        if (head.length != cw * ch) head = new int[cw * ch];
        java.util.Arrays.fill(head, -1);
        if (next.length < n) next = new int[x.length];
        for (int i = 0; i < n; i++) {
            int ci = Math.max(0, Math.min(cw - 1, (int) (x[i] / width * cw))), cj = Math.max(0, Math.min(ch - 1, (int) (y[i] / height * ch)));
            int c = cj * cw + ci;
            next[i] = head[c];
            head[c] = i;
        }
        for (int i = 0; i < n; i++) {
            if (a[i] <= 0) continue;
            int ci = Math.max(0, Math.min(cw - 1, (int) (x[i] / width * cw))), cj = Math.max(0, Math.min(ch - 1, (int) (y[i] / height * ch)));
            for (int dj = -1; dj <= 1; dj++)
                for (int di = -1; di <= 1; di++) {
                    int qi = ci + di, qj = cj + dj;
                    if (periodic) {
                        if (cw < 3 && di != 0 && Math.floorMod(qi, cw) == ci) continue;
                        if (ch < 3 && dj != 0 && Math.floorMod(qj, ch) == cj) continue;
                        qi = Math.floorMod(qi, cw);
                        qj = Math.floorMod(qj, ch);
                    } else if (qi < 0 || qj < 0 || qi >= cw || qj >= ch) continue;
                    for (int j = head[qj * cw + qi]; j >= 0; j = next[j]) {
                        if (j <= i || a[j] <= 0 || a[i] <= 0) continue;
                        double dx = x[j] - x[i], dy = y[j] - y[i], rr = (a[i] + a[j]) * 0.85;
                        if (periodic) {
                            dx -= width * Math.rint(dx / width);
                            dy -= height * Math.rint(dy / height);
                        }
                        if (dx * dx + dy * dy >= rr * rr) continue;
                        // Volumen und Impuls bleiben; der Größere bestimmt den Ort mehr
                        double vi = volume(a[i]), vj = volume(a[j]), vt = vi + vj;
                        x[i] = x[i] + dx * vj / vt;
                        y[i] = y[i] + dy * vj / vt;
                        vx[i] = (vx[i] * vi + vx[j] * vj) / vt;
                        vy[i] = (vy[i] * vi + vy[j] * vj) / vt;
                        a[i] = Math.min(A_MAX, radiusForVolume(vt));
                        moving[i] = moving[i] || moving[j];
                        a[j] = 0;
                        merges++;
                    }
                }
        }
    }

    private void cull() {
        int m = 0;
        for (int i = 0; i < n; i++) {
            boolean out;
            if (periodic) {
                out = a[i] < A_MIN;
                if (!out && y[i] < 0) {
                    // unten aus der Kachel: auf der echten Scheibe läuft er hier nur selten unten ab
                    if (rnd.nextDouble() < height / Math.max(height, drainLength)) out = true;
                    else y[i] += height;
                }
                if (!out && y[i] >= height) y[i] -= height;
                if (!out) x[i] -= width * Math.floor(x[i] / width);
            } else {
                out = a[i] < A_MIN || x[i] < -a[i] || x[i] > width + a[i] || y[i] < -a[i] || y[i] > height + a[i];
            }
            if (out) continue;
            if (m != i) {
                x[m] = x[i]; y[m] = y[i]; a[m] = a[i]; vx[m] = vx[i]; vy[m] = vy[i]; trail[m] = trail[i]; moving[m] = moving[i];
            }
            m++;
        }
        n = m;
        // zu viele: die kleinsten ruhenden Tropfen verdunsten zuerst (von hinten, damit es reproduzierbar bleibt)
        int max = quality.maxDrops;
        if (n > max) {
            double lim = 0.15e-3;
            while (n > max) {
                int k = 0;
                for (int i = 0; i < n && n > max; i++) if (!moving[i] && a[i] < lim) { a[i] = 0; k++; }
                if (k > 0) {
                    int mm = 0;
                    for (int i = 0; i < n; i++) {
                        if (a[i] <= 0) continue;
                        x[mm] = x[i]; y[mm] = y[i]; a[mm] = a[i]; vx[mm] = vx[i]; vy[mm] = vy[i]; trail[mm] = trail[i]; moving[mm] = moving[i];
                        mm++;
                    }
                    n = mm;
                }
                lim *= 1.5;
                if (lim > A_MAX * 2) { n = max; break; }
            }
        }
    }

    // ------------------------------------------------------------------ Ausgänge

    /** Unveränderlicher Zustand für die Grafikkarte und Prüfungen. */
    public record Snapshot(double time, double width, double height, int count, float[] drops, float[] film, float[] fine, int filmW, int filmH, Quality quality, int seed, boolean periodic,
                           float[] fog, float[] salt, double sheetMm, double flowX, double flowY) {
        /** Daten je Tropfen in {@link #drops}: x, y, a (m), Streckung, Richtung x, Richtung y, 0, 0. */
        public static final int FLOATS = 8;

        /**
         * Höhe des Wassers in mm am Ort (Höchstwert aller Kappen), Zeile für Zeile wie {@code wg_height.frag}. Ein
         * laufender Tropfen ist in Laufrichtung gestreckt und nach hinten verschoben (Tränenform).
         */
        public double heightMm(double px, double py) {
            double best = 0;
            for (int i = 0; i < count; i++) best = Math.max(best, capMm(i, px, py));
            return best;
        }

        double capMm(int i, double px, double py) {
            int o = i * FLOATS;
            double cx = drops[o], cy = drops[o + 1], ar = drops[o + 2], st = drops[o + 3], ux = drops[o + 4], uy = drops[o + 5];
            double dx = px - cx, dy = py - cy;
            if (periodic) { dx -= width * Math.rint(dx / width); dy -= height * Math.rint(dy / height); }
            double along = dx * ux + dy * uy, across = -dx * uy + dy * ux;
            along = (along + ar * st * 0.5) / (1 + st);
            double d2 = (along * along + across * across) / (ar * ar);
            if (d2 >= 1) return 0;
            return capHeight(Math.sqrt(d2)) * ar * 1000;
        }

        /** Gesamtvolumen in mm³ (Prüfung). */
        public double volumeMm3() {
            double s = 0;
            for (int i = 0; i < count; i++) s += volume(drops[i * FLOATS + 2]) * 1e9;
            return s;
        }
    }

    /** Höhe einer Kappe mit 60° Kontaktwinkel bei relativem Abstand q = d/a, bezogen auf a. */
    public static double capHeight(double q) {
        double th = Math.toRadians(CONTACT_DEG), s = Math.sin(th), c = Math.cos(th);
        return Math.sqrt(Math.max(0, 1 / (s * s) - q * q)) - c / s;
    }

    private Snapshot build() {
        float[] d = new float[n * Snapshot.FLOATS];
        for (int i = 0; i < n; i++) {
            int o = i * Snapshot.FLOATS;
            double sp = Math.hypot(vx[i], vy[i]);
            d[o] = (float) x[i];
            d[o + 1] = (float) y[i];
            d[o + 2] = (float) a[i];
            d[o + 3] = (float) Math.min(1.2, sp * 5);
            d[o + 4] = (float) (sp > 1e-6 ? vx[i] / sp : 0);
            d[o + 5] = (float) (sp > 1e-6 ? vy[i] / sp : -1);
        }
        float[] fg = new float[fog.length];
        for (int i = 0; i < fg.length; i++) fg[i] = Math.min(1f, fog[i]);
        double fx = slopeX, fy = slopeY;
        if (surface != null) { surface.at(width / 2, height / 2, sf); fx = sf[0]; fy = sf[1]; }
        double fl = Math.hypot(fx, fy);
        return new Snapshot(time, width, height, n, d, film.clone(), fine.clone(), gw, gh, quality, seed, periodic,
                fg, salt.clone(), sheet * 1000, fl > 1e-6 ? fx / fl : 0, fl > 1e-6 ? fy / fl : -1);
    }

    public Snapshot snapshot() { return snapshot; }

    public int count() { return n; }

    public synchronized int moving() {
        int k = 0;
        for (int i = 0; i < n; i++) if (moving[i]) k++;
        return k;
    }

    /** Wasser auf der Fläche in mm³ (Tropfen und Feintropfen, ohne Film). */
    public synchronized double waterMm3() {
        double s = 0;
        for (int i = 0; i < n; i++) s += volume(a[i]) * 1e9;
        double cellArea = width / gw * height / gh;
        for (float f : fine) s += f * FINE_FULL * cellArea * 1e9;
        return s + sheet * width * height * 1e9;
    }

    /** Größter Kontakthalbmesser in mm. */
    public synchronized double largestMm() {
        double m = 0;
        for (int i = 0; i < n; i++) m = Math.max(m, a[i]);
        return m * 1000;
    }

    /** Bisher: Einschläge, Verschmelzungen, Resttröpfchen, Losrutschen, abgefallene Tropfen, Überspülungen. */
    public synchronized long[] totals() { return new long[]{hits, merges, residues, slides, drips, floods}; }

    /** Zugriff für Prüfungen: Tropfen i als {x, y, a, vx, vy, läuft}. */
    public synchronized double[] drop(int i) { return new double[]{x[i], y[i], a[i], vx[i], vy[i], moving[i] ? 1 : 0}; }

    public String describe() {
        String s = String.format(Locale.GERMAN, "%,d Tropfen, %d laufen, größter %.1f mm, Feintropfen %.0f %% · %.0f Einschläge/m²s", count(), moving(), largestMm(), fineMean() * 100, hitRate);
        double fm = fogMean(), sm = saltMean(), sh = sheetMm();
        if (fm > 0.01) s += String.format(Locale.GERMAN, " · Beschlag %.0f %%", fm * 100);
        if (sm > 0.01) s += String.format(Locale.GERMAN, " · Salz %.1f g/m²", sm * SALT_FULL);
        if (sh > 0) s += String.format(Locale.GERMAN, " · überspült %.2f mm", sh);
        return s;
    }

    // ------------------------------------------------------------------ Gekrümmte Flächen

    /**
     * Eine Fläche, die je Ort sagt, wie Schwerkraft, Wind und Regen auf sie wirken (F10). Flächenkoordinaten in m wie
     * bei der ebenen Scheibe; die Simulation rechnet im abgewickelten Flächenraum.
     */
    public interface Surface {
        /**
         * Am Ort (x, y): out[0], out[1] Schwerkraft entlang der Fläche als Anteil von g; out[2] Schwerkraft senkrecht
         * zur Fläche (1 = der Tropfen liegt oben auf, 0 = senkrechte Wand, −1 = er hängt darunter); out[3], out[4] Wind
         * entlang der Fläche in m/s; out[5] wie sehr der Ort dem Regen zugewandt ist (0..1, bezogen auf
         * {@link Wetglass#hitRate}).
         */
        void at(double x, double y, double[] out);
    }

    /**
     * Ein Zylinder, abgewickelt: x läuft um ihn herum (Umfang 2πr, setze {@link Wetglass#width} darauf und
     * {@link Wetglass#periodic}), y entlang der Achse. Stehend (Turmwand, Pfosten) liegt x = 0 auf der Windseite und
     * die Schwerkraft zieht überall nach unten; der Wind umströmt ihn (an den Flanken doppelt so schnell, dahinter
     * still) und schiebt die Tropfen zur Seite. Liegend (Handlauf, Rohr) liegt x = 0 oben; Tropfen liegen oben auf,
     * laufen über die Flanken nach unten, sammeln sich unten und fallen ab. Der Regen trifft, wo der Zylinder ihm
     * zugewandt ist; {@link Wetglass#hitRate} gilt dann für eine Fläche quer zum fallenden Regen
     * ({@link Wetglass#rainAcross}).
     */
    public static final class Cylinder implements Surface {
        public volatile double radius;
        public volatile boolean horizontal;
        /** Anströmung quer zur Achse in m/s (bei liegendem Zylinder von der Seite x = −πr/2 her). */
        public volatile double wind;
        /** Fallgeschwindigkeit des Regens in m/s (für die Neigung der Tropfen im Wind). */
        public volatile double fall = 6.5;

        public Cylinder(double radius, boolean horizontal) {
            this.radius = radius;
            this.horizontal = horizontal;
        }

        /** Umfang in m (die Breite der Fläche). */
        public double circumference() { return 2 * Math.PI * radius; }

        @Override
        public void at(double x, double y, double[] out) {
            double th = x / radius, s = Math.sin(th), c = Math.cos(th);
            // Umströmung: tangential 2U·sin α vom Staupunkt weg, hinter der Ablösung (gut 100°) fast still
            double al = horizontal ? th - 1.5 * Math.PI : th;
            double ut = 2 * wind * Math.sin(al) * (Math.cos(al) > -0.17 ? 1 : 0.12);
            if (horizontal) {
                out[0] = s;
                out[1] = 0;
                out[2] = c;
                out[3] = ut;
                out[4] = 0;
                double d = Math.hypot(wind, fall);
                out[5] = d > 0 ? Math.max(0, c * fall - s * wind) / d : Math.max(0, c);
            } else {
                out[0] = 0;
                out[1] = -1;
                out[2] = 0;
                out[3] = ut;
                out[4] = 0;
                out[5] = Math.max(0, c);
            }
        }
    }

    /**
     * Regen auf eine Fläche quer zum fallenden Regen (Einschläge je m² und Sekunde, für gekrümmte Flächen, deren
     * {@link Surface} je Ort den Anteil sagt): Rate in mm/h, Wind in m/s.
     */
    public void rainAcross(double rateMmH, double windMs) {
        if (rateMmH <= 0) { hitRate = 0; return; }
        double lam = 4.1 * Math.pow(rateMmH, -0.21), sum = 0, dd = 0.02;
        for (double d = 0.3 + dd / 2; d < 6; d += dd) sum += 8000 * Math.exp(-lam * d) * Math.max(0.5, 9.65 - 10.3 * Math.exp(-0.6 * d)) * dd;
        hitRate = sum * Math.hypot(windMs, 6.5) / 6.5;
        hitLambda = lam;
    }
}
