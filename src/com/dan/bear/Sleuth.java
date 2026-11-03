package com.dan.bear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Eine Gruppe Braunbären in ihrem Gebiet (Phase 18a): Verhalten, Gang, Haltung und Lage jedes Tiers. Gelände, Wasser,
 * Bäche, Strände und Störer kommen von außen ({@link World}, {@link Stream}, {@link Beach}, {@link Disturber}).
 * <ul>
 * <li><b>Umherziehen</b> im eigenen Gebiet ({@link Range}), schnüffelnd oder gehend; Ruhe am Tag öfter, wenn die Sonne
 * hoch steht.</li>
 * <li><b>Fischen</b> (Lachsmonate): zum nächsten Platz am Bach, dort im flachen Wasser stehen, den Kopf tief, ab und zu
 * ein Sprung nach vorn; mit Erfolg ein Lachs, den er am Ufer frisst.</li>
 * <li><b>Graben</b> am Strand, wenn er trocken liegt: mit den Vorderpfoten nach Muscheln scharren.</li>
 * <li><b>Störer</b>: unter der Warnweite richtet er sich auf und nimmt Witterung, unter der Fluchtdistanz flieht er im
 * Galopp, dann im Trab, und bleibt eine Weile scheu (meidet die Gegend des Störers). Boote zählen halb so nah.</li>
 * <li><b>Beben</b> ({@link #quake}): alle fliehen im Galopp bergauf, weg vom Wasser, und halten oben an.</li>
 * <li><b>Junge</b> folgen der Mutter in wenigen Metern, fliehen mit ihr und rufen, wenn sie zurückbleiben.</li>
 * </ul>
 * Zeit in Sekunden; {@link #step} teilt in Schritte von höchstens {@link #MAX_STEP} s. Welt: x Ost, y oben, z Süd;
 * Richtung 0 = Norden, im Uhrzeigersinn (wie {@code gl/bear.vert}).
 */
public final class Sleuth {
    /** Der Ort, den die Gruppe abfragt: Grund und Wasserspiegel (NaN, wo es trocken ist), m über Null. */
    public interface World {
        double ground(double x, double z);
        double water(double x, double z);
    }

    /** Ein Bach mit Plätzen zum Fischen (je {x, z}). */
    public record Stream(String name, double[][] spots) { }
    /** Ein Strand zum Graben: Mitte, Halbmesser (m). */
    public record Beach(double x, double z, double radius) { }
    /** Das Gebiet eines Bären: Mitte, Halbmesser (m). */
    public record Range(double x, double z, double radius) { }
    /** Ein Störer: Ort, Stärke 0..1 (1 ein Mensch zu Fuß), Boot. */
    public record Disturber(double x, double z, double strength, boolean boat) { }

    @FunctionalInterface public interface TrackSink { void track(double x, double z, double dirX, double dirZ, int leg, double length); }
    @FunctionalInterface public interface CatchSink { void caught(double x, double z); }

    public enum Activity {
        STEHEN("steht"), GEHEN("geht"), SCHNUEFFELN("schnüffelt"), GRABEN("gräbt"), AUFRICHTEN("richtet sich auf"),
        FISCHEN("fischt"), FRESSEN("frisst"), LIEGEN("liegt"), FLUCHT("flieht"), FOLGEN("folgt");
        public final String label;
        Activity(String l) { label = l; }
    }

    /** Ein Bär. */
    public static final class Animal {
        public final int id;
        public final boolean cub, female;
        public final int mother;
        public final double length, mass;
        public final float tone;
        public double x, y, z, heading, speed;
        public Activity act = Activity.STEHEN;
        /** Was nach dem Weg kommt (FISCHEN, GRABEN, LIEGEN …) und wohin. */
        Activity then;
        double tx, tz, want, timer, shy, alertCool, pounce, lunge;
        public final float[] pose = new float[BearBody.N];
        final float[] goal = new float[BearBody.N];
        /** Takt des Gangs (Doppelschritte), Höhe des Grunds unter der Hüfte (Körperkoordinaten). */
        public double phase, level = -0.39;
        public Bear.Gait gait = Bear.Gait.STAND;
        public double wet;
        final double[] lastU = {-1, -1, -1, -1};
        int range;
        Animal(int id, boolean cub, boolean female, int mother, double length, double mass, float tone) {
            this.id = id; this.cub = cub; this.female = female; this.mother = mother; this.length = length; this.mass = mass; this.tone = tone;
        }
    }

    public final Bear bear;
    public World world;
    public final List<Stream> streams = new ArrayList<>();
    public final List<Beach> beaches = new ArrayList<>();
    public final List<Range> ranges = new ArrayList<>();
    public final List<Disturber> disturbers = new ArrayList<>();
    public TrackSink onTrack;
    public CatchSink onCatch;
    /** Monat 1..12 und Sonnenhöhe (Grad), je Bild gesetzt. */
    public volatile int month = 7;
    public volatile double sunAltDeg = 30;
    /** Erwachsene und Junge (die Jungen gehören zur ersten Bärin). */
    public volatile int count = 3, cubs = 2;
    public static final double MAX_STEP = 0.05;

    private final List<Animal> animals = new ArrayList<>();
    private SplittableRandom rng;
    private double time;
    private int builtCount = -1, builtCubs = -1;
    private long builtSeed;
    /** Zähler für Prüfung und HUD. */
    public int catches, pounces, digs, flights, quakeFlights, rears, tracks, calls;

    public Sleuth(Bear bear) { this.bear = bear; rng = new SplittableRandom(bear.seed); }

    public List<Animal> animals() { return Collections.unmodifiableList(animals); }
    public double time() { return time; }
    public boolean stale() { return builtCount != count || builtCubs != cubs || builtSeed != bear.seed; }

    // ------------------------------------------------------------------ Aufbau

    /** Neu aufstellen: Tiere in ihren Gebieten, auf begehbarem Grund. */
    public void reset() {
        animals.clear();
        rng = new SplittableRandom(bear.seed);
        time = 0;
        catches = pounces = digs = flights = quakeFlights = rears = tracks = calls = 0;
        int n = Math.max(0, Math.min(count, bear.maxAnimals));
        for (int i = 0; i < n; i++) {
            boolean f = i % 2 == 0;
            double k = 0.92 + 0.16 * rng.nextDouble();
            Animal a = new Animal(animals.size(), false, f, -1, (f ? bear.femaleLength : bear.maleLength) * k, (f ? bear.femaleMass : bear.maleMass) * k * k * k, (float) rng.nextDouble());
            a.range = ranges.isEmpty() ? -1 : i % ranges.size();
            placeInRange(a);
            animals.add(a);
        }
        if (!animals.isEmpty())
            for (int c = 0; c < cubs && animals.size() < bear.maxAnimals + cubs; c++) {
                Animal m = animals.get(0);
                Animal a = new Animal(animals.size(), true, rng.nextBoolean(), m.id, bear.cubLength * (0.9 + 0.2 * rng.nextDouble()), bear.cubMass, (float) Math.min(1, m.tone + 0.15 * rng.nextDouble()));
                double an = rng.nextDouble() * 2 * Math.PI;
                a.x = m.x + Math.cos(an) * 3;
                a.z = m.z + Math.sin(an) * 3;
                if (!walkable(a.x, a.z, 0.4)) { a.x = m.x; a.z = m.z; }
                a.heading = m.heading;
                a.act = Activity.FOLGEN;
                animals.add(a);
            }
        for (Animal a : animals) { a.timer = 5 + 20 * rng.nextDouble(); posture(a, 1e3); }
        builtCount = count;
        builtCubs = cubs;
        builtSeed = bear.seed;
    }

    /** Nach einem Sprung der Uhr: Haltungen und Höhe ohne Übergang. */
    public void settle() {
        if (stale()) reset();
        for (Animal a : animals) {
            if (a.act == Activity.FLUCHT) { a.act = Activity.STEHEN; a.speed = 0; }
            posture(a, 1e3);
        }
    }

    private void placeInRange(Animal a) {
        Range r = a.range >= 0 ? ranges.get(a.range) : new Range(0, 0, 200);
        for (int k = 0; k < 200; k++) {
            double an = rng.nextDouble() * 2 * Math.PI, d = Math.sqrt(rng.nextDouble()) * r.radius();
            double x = r.x() + Math.cos(an) * d, z = r.z() + Math.sin(an) * d;
            if (walkable(x, z, 0.3)) { a.x = x; a.z = z; a.heading = rng.nextDouble() * 360; return; }
        }
        a.x = r.x(); a.z = r.z();
    }

    // ------------------------------------------------------------------ Welt

    /** Wassertiefe (m), 0 wo trocken. */
    public double depth(double x, double z) {
        double w = world.water(x, z), g = world.ground(x, z);
        return Double.isNaN(w) || Double.isNaN(g) ? 0 : Math.max(0, w - g);
    }

    /** Begehbar: Grund da, nicht steiler als 37°, Wasser höchstens so tief (m). */
    public boolean walkable(double x, double z, double maxDepth) {
        double g = world.ground(x, z);
        if (Double.isNaN(g)) return false;
        if (depth(x, z) > maxDepth) return false;
        double gx = world.ground(x + 1.5, z) - world.ground(x - 1.5, z), gz = world.ground(x, z + 1.5) - world.ground(x, z - 1.5);
        return Math.hypot(gx, gz) / 3 < 0.75;
    }

    /** Nächster Störer: {Abstand wirksam (Boote doppelt so weit gezählt), x, z}; unendlich ohne. */
    public double[] nearestDisturber(double x, double z) {
        double best = Double.POSITIVE_INFINITY, bx = 0, bz = 0;
        for (Disturber d : disturbers) {
            double e = Math.hypot(d.x() - x, d.z() - z) / Math.max(0.05, d.strength()) * (d.boat() ? 2 : 1);
            if (e < best) { best = e; bx = d.x(); bz = d.z(); }
        }
        return new double[]{best, bx, bz};
    }

    // ------------------------------------------------------------------ Ereignisse

    /** Ein Beben (Stärke 0..1): alle fliehen bergauf. */
    public void quake(double strength) {
        if (stale()) reset();
        for (Animal a : animals) {
            if (a.cub) continue;
            double[] up = uphill(a.x, a.z, 60);
            flee(a, up[0], up[1], 25 + 35 * strength * (0.7 + 0.6 * rng.nextDouble()));
            quakeFlights++;
        }
        call(animals.isEmpty() ? 0 : animals.get(0).x, animals.isEmpty() ? 0 : animals.get(0).z, Bear.CallKind.SCHNAUBEN, 1);
    }

    /** Richtung bergauf: unter 16 Richtungen in d m die höchste begehbare, weg vom Wasser. {dx, dz} (Einheit). */
    double[] uphill(double x, double z, double d) {
        double best = -Double.MAX_VALUE, bx = 0, bz = -1, g0 = world.ground(x, z);
        for (int k = 0; k < 16; k++) {
            double an = 2 * Math.PI * k / 16, dx = Math.cos(an), dz = Math.sin(an);
            double px = x + dx * d, pz = z + dz * d;
            if (!walkable(px, pz, 0.2)) continue;
            double v = world.ground(px, pz) - g0 - 30 * depth(x + dx * d * 2, z + dz * d * 2);
            if (v > best) { best = v; bx = dx; bz = dz; }
        }
        return new double[]{bx, bz};
    }

    /** Weg vom Störer, etwas bergauf und weg vom Wasser: {dx, dz} (Einheit). */
    double[] away(Animal a, double sx, double sz) {
        double dx = a.x - sx, dz = a.z - sz, l = Math.max(1e-6, Math.hypot(dx, dz));
        double[] up = uphill(a.x, a.z, 40);
        double ex = dx / l + 0.7 * up[0], ez = dz / l + 0.7 * up[1], m = Math.max(1e-6, Math.hypot(ex, ez));
        return new double[]{ex / m, ez / m};
    }

    private boolean hasCubs(Animal a) {
        for (Animal c : animals) if (c.cub && c.mother == a.id) return true;
        return false;
    }

    private void flee(Animal a, double dx, double dz, double seconds) {
        a.act = Activity.FLUCHT;
        a.then = null;
        a.tx = a.x + dx * 400;
        a.tz = a.z + dz * 400;
        // eine Bärin mit Jungen läuft so schnell, wie die Jungen mitkommen
        a.want = bear.fleeSpeed * Math.sqrt(a.length / Bear.REF) * (hasCubs(a) ? 0.75 : 1);
        a.timer = seconds;
        a.shy = bear.shyTime;
        flights++;
        call(a.x, a.z, Bear.CallKind.SCHNAUBEN, 0.8);
    }

    private void call(double x, double z, Bear.CallKind k, double s) {
        calls++;
        Bear.Call c = bear.onCall;
        if (c != null) c.call(x, z, k, s);
    }

    // ------------------------------------------------------------------ Lauf

    /** Weiter um dt Sekunden. */
    public void step(double dt) {
        if (world == null) return;
        if (stale()) reset();
        while (dt > 1e-9) {
            double h = Math.min(MAX_STEP, dt);
            for (Animal a : animals) update(a, h);
            time += h;
            dt -= h;
        }
    }

    private void update(Animal a, double dt) {
        a.timer -= dt;
        a.shy = Math.max(0, a.shy - dt);
        a.alertCool = Math.max(0, a.alertCool - dt);
        if (a.cub) cub(a, dt);
        else adult(a, dt);
        move(a, dt);
        posture(a, dt);
        // Nässe
        double dep = depth(a.x, a.z);
        if (dep > 0.15) a.wet = Math.min(1, a.wet + dt * Math.min(1, dep / 0.4) / 4);
        else a.wet *= Math.pow(0.5, dt / (bear.furHalfMinutes * 60));
    }

    private void adult(Animal a, double dt) {
        double[] dist = nearestDisturber(a.x, a.z);
        double fl = bear.flightDistance * (a.female && cubs > 0 && a.id == 0 ? 1.4 : 1), al = bear.alertDistance;
        if (a.act != Activity.FLUCHT) {
            if (dist[0] < fl) {
                double[] d = away(a, dist[1], dist[2]);
                flee(a, d[0], d[1], 20 + 15 * rng.nextDouble());
                return;
            }
            if (dist[0] < al && a.alertCool <= 0 && a.act != Activity.AUFRICHTEN) {
                a.act = Activity.AUFRICHTEN;
                a.then = null;
                a.timer = 4 + 5 * rng.nextDouble();
                a.want = 0;
                a.alertCool = 40;
                a.heading = bearing(a.x, a.z, dist[1], dist[2]);
                a.shy = Math.max(a.shy, bear.shyTime * 0.3);
                rears++;
                call(a.x, a.z, Bear.CallKind.KIEFER, 0.5);
                return;
            }
        }
        switch (a.act) {
            case FLUCHT -> {
                if (a.timer <= 0 || arrived(a, 3)) {
                    a.act = Activity.GEHEN;
                    a.want = bear.trotSpeed * 0.8;
                    double dx = a.tx - a.x, dz = a.tz - a.z, l = Math.max(1e-6, Math.hypot(dx, dz));
                    a.tx = a.x + dx / l * 120;
                    a.tz = a.z + dz / l * 120;
                    a.then = Activity.AUFRICHTEN;
                }
            }
            case GEHEN, SCHNUEFFELN -> {
                if (a.shy > 0 && dist[0] < 2.5 * fl && a.then != Activity.FLUCHT) {
                    // noch zu nah: im Trab weiter weg
                    double[] d = away(a, dist[1], dist[2]);
                    a.tx = a.x + d[0] * 80;
                    a.tz = a.z + d[1] * 80;
                    a.want = bear.trotSpeed * 0.8 * Math.sqrt(a.length / Bear.REF);
                    a.act = Activity.GEHEN;
                    a.then = Activity.AUFRICHTEN;
                    a.timer = 60;
                    break;
                }
                if (arrived(a, a.then == Activity.FISCHEN ? 1.0 : 2.5) || a.timer <= 0) {
                    if (a.then != null) begin(a, a.then);
                    else choose(a);
                }
            }
            case FISCHEN -> {
                a.want = 0;
                if (!bear.salmon(month)) { choose(a); break; }
                if (a.lunge > 0) {
                    a.lunge -= dt;
                    a.want = 4;
                    if (a.lunge <= 0) {
                        a.want = 0;
                        if (rng.nextDouble() < bear.catchChance) {
                            catches++;
                            CatchSink c = onCatch;
                            if (c != null) c.caught(a.x, a.z);
                            a.act = Activity.FRESSEN;
                            a.timer = bear.eatMin + (bear.eatMax - bear.eatMin) * rng.nextDouble();
                        }
                    }
                } else if ((a.pounce -= dt) <= 0) {
                    pounces++;
                    a.lunge = 0.45;
                    a.pounce = bear.pounceMin + (bear.pounceMax - bear.pounceMin) * rng.nextDouble();
                    a.tx = a.x + Math.sin(Math.toRadians(a.heading)) * 2;
                    a.tz = a.z - Math.cos(Math.toRadians(a.heading)) * 2;
                    if (!walkable(a.tx, a.tz, 0.9)) { a.tx = a.x; a.tz = a.z; }
                }
                if (a.timer <= 0) choose(a);
            }
            case FRESSEN -> {
                a.want = 0;
                if (rng.nextDouble() < dt / 20) call(a.x, a.z, Bear.CallKind.BRUMMEN, 0.3);
                if (a.timer <= 0) begin(a, bear.salmon(month) && rng.nextDouble() < 0.7 ? Activity.FISCHEN : Activity.LIEGEN);
            }
            case GRABEN -> {
                a.want = 0;
                if (a.timer <= 0) choose(a);
            }
            case AUFRICHTEN, STEHEN, LIEGEN -> {
                a.want = 0;
                if (a.timer <= 0) {
                    if (a.shy > 0 && dist[0] < 2.5 * fl) { a.act = Activity.GEHEN; a.then = null; a.timer = 0; }   // beim nächsten Schritt weiter weg
                    else choose(a);
                }
            }
            default -> { if (a.timer <= 0) choose(a); }
        }
    }

    private void cub(Animal a, double dt) {
        Animal m = animals.get(a.mother);
        double d = Math.hypot(m.x - a.x, m.z - a.z);
        boolean run = m.act == Activity.FLUCHT || d > 12;
        if (m.act == Activity.LIEGEN && d < 4) { a.act = Activity.LIEGEN; a.want = 0; return; }
        a.act = run ? Activity.FLUCHT : Activity.FOLGEN;
        // einige Meter hinter und neben der Mutter
        double h = Math.toRadians(m.heading), side = (a.id % 2 == 0 ? 1 : -1) * 1.6;
        double bx = m.x - Math.sin(h) * 3.2 + Math.cos(h) * side, bz = m.z + Math.cos(h) * 3.2 + Math.sin(h) * side;
        a.tx = bx;
        a.tz = bz;
        double e = Math.hypot(bx - a.x, bz - a.z);
        double top = bear.fleeSpeed * 0.8 * Math.sqrt(a.length / Bear.REF) * 1.6;
        a.want = e < 0.6 ? 0 : Math.min(top, Math.max(m.speed * 1.1, 0.5 + e * 0.5));
        if (d > 20 && rng.nextDouble() < dt / 3) call(a.x, a.z, Bear.CallKind.JUNGES, 0.6);
    }

    /** Die nächste Tätigkeit nach Lust und Lage. */
    private void choose(Animal a) {
        Range r = a.range >= 0 ? ranges.get(a.range) : new Range(a.x, a.z, 200);
        double wFish = 0, wDig = 0, wSniff = 1.5, wWalk = 2.0, wRest = a.shy > 0 ? 0 : 0.8 + (sunAltDeg > 35 ? 0.8 : 0), wStand = 0.6;
        double[] spot = null;
        if (bear.salmon(month) && a.shy <= 0) { spot = nearestSpot(a, r); if (spot != null) wFish = 3.5; }
        Beach beach = null;
        if (a.shy <= 0) for (Beach b : beaches) if (Math.hypot(b.x() - r.x(), b.z() - r.z()) < r.radius() + b.radius() && depth(b.x(), b.z()) < 0.05) { beach = b; wDig = 1.5; break; }
        double sum = wFish + wDig + wSniff + wWalk + wRest + wStand, u = rng.nextDouble() * sum;
        if ((u -= wFish) < 0) {
            goTo(a, spot[0], spot[1], bear.walkSpeed, Activity.FISCHEN);
        } else if ((u -= wDig) < 0) {
            double[] p = pointIn(beach.x(), beach.z(), beach.radius(), 0.05);
            goTo(a, p[0], p[1], bear.walkSpeed, Activity.GRABEN);
        } else if ((u -= wSniff) < 0) {
            double[] p = wanderPoint(a, r, 40);
            goTo(a, p[0], p[1], bear.sniffSpeed, null);
            a.act = Activity.SCHNUEFFELN;
            a.timer = 20 + 40 * rng.nextDouble();
        } else if ((u -= wWalk) < 0) {
            double[] p = wanderPoint(a, r, 150);
            goTo(a, p[0], p[1], bear.walkSpeed * (0.85 + 0.3 * rng.nextDouble()), null);
        } else if ((u -= wRest) < 0) {
            begin(a, Activity.LIEGEN);
        } else begin(a, Activity.STEHEN);
    }

    private void begin(Animal a, Activity k) {
        a.act = k;
        a.then = null;
        a.want = 0;
        switch (k) {
            case FISCHEN -> { a.timer = 300 + 600 * rng.nextDouble(); a.pounce = 5 + 20 * rng.nextDouble(); a.lunge = 0; }
            case GRABEN -> { a.timer = 30 + 90 * rng.nextDouble(); digs++; }
            case LIEGEN -> a.timer = 120 + 480 * rng.nextDouble();
            case AUFRICHTEN -> { a.timer = 3 + 4 * rng.nextDouble(); rears++; }
            default -> a.timer = 5 + 20 * rng.nextDouble();
        }
    }

    private void goTo(Animal a, double x, double z, double v, Activity then) {
        a.act = Activity.GEHEN;
        a.tx = x;
        a.tz = z;
        a.want = v * Math.sqrt(a.length / Bear.REF);
        a.then = then;
        a.timer = Math.hypot(x - a.x, z - a.z) / Math.max(0.2, a.want) * 2 + 30;
    }

    private boolean arrived(Animal a, double r) { return Math.hypot(a.tx - a.x, a.tz - a.z) < r; }

    private double[] nearestSpot(Animal a, Range r) {
        double best = Double.POSITIVE_INFINITY;
        double[] p = null;
        for (Stream s : streams)
            for (double[] q : s.spots()) {
                if (Math.hypot(q[0] - r.x(), q[1] - r.z()) > r.radius() + 400) continue;
                double d = Math.hypot(q[0] - a.x, q[1] - a.z) * (0.6 + 0.8 * rng.nextDouble());
                if (d < best) { best = d; p = q; }
            }
        return p;
    }

    private double[] wanderPoint(Animal a, Range r, double reach) {
        double[] dist = nearestDisturber(a.x, a.z);
        for (int k = 0; k < 40; k++) {
            double an = rng.nextDouble() * 2 * Math.PI, d = reach * (0.3 + 0.7 * rng.nextDouble());
            double x = a.x + Math.cos(an) * d, z = a.z + Math.sin(an) * d;
            if (Math.hypot(x - r.x(), z - r.z()) > r.radius()) continue;
            if (a.shy > 0 && Math.hypot(x - dist[1], z - dist[2]) < Math.hypot(a.x - dist[1], a.z - dist[2])) continue;
            if (walkable(x, z, 0.3)) return new double[]{x, z};
        }
        return new double[]{a.x, a.z};
    }

    private double[] pointIn(double cx, double cz, double rad, double maxDepth) {
        for (int k = 0; k < 40; k++) {
            double an = rng.nextDouble() * 2 * Math.PI, d = Math.sqrt(rng.nextDouble()) * rad;
            double x = cx + Math.cos(an) * d, z = cz + Math.sin(an) * d;
            if (walkable(x, z, maxDepth)) return new double[]{x, z};
        }
        return new double[]{cx, cz};
    }

    static double bearing(double x0, double z0, double x1, double z1) {
        double h = Math.toDegrees(Math.atan2(x1 - x0, -(z1 - z0)));
        return h < 0 ? h + 360 : h;
    }

    // ------------------------------------------------------------------ Bewegung

    private void move(Animal a, double dt) {
        double v = a.want;
        double dx = a.tx - a.x, dz = a.tz - a.z, d = Math.hypot(dx, dz);
        boolean go = v > 0 && d > 0.3;
        if (go) {
            // Richtung zum Ziel, gedreht mit höchstens so viel Grad je Sekunde
            double h = bearing(a.x, a.z, a.tx, a.tz), diff = ((h - a.heading + 540) % 360) - 180;
            double turn = (a.speed > 6 ? 70 : 110) * dt;
            a.heading = (a.heading + Math.max(-turn, Math.min(turn, diff)) + 360) % 360;
            // langsamer in engen Kurven und am Ziel
            v = Math.min(v, Math.max(0.3, d * 0.8));
            if (Math.abs(diff) > 60) v = Math.min(v, 1.0);
        } else v = 0;
        double acc = (v > a.speed ? (a.speed > 4 ? 5 : 2.5) : 6) * dt;
        a.speed = a.speed + Math.max(-acc, Math.min(acc, v - a.speed));
        if (a.speed < 0.02) { a.speed = 0; return; }
        double h = Math.toRadians(a.heading);
        double nx = a.x + Math.sin(h) * a.speed * dt, nz = a.z - Math.cos(h) * a.speed * dt;
        double maxDepth = a.act == Activity.FISCHEN || a.then == Activity.FISCHEN ? 0.9 : a.act == Activity.FLUCHT ? 0.5 : 0.4;
        if (a.cub) maxDepth *= 0.6;
        if (walkable(nx, nz, maxDepth)) { a.x = nx; a.z = nz; }
        else {
            a.speed *= 0.3;
            if (a.act == Activity.FLUCHT) {
                // um das Hindernis: eine andere Richtung, möglichst weiter weg
                double[] up = uphill(a.x, a.z, 30);
                a.tx = a.x + up[0] * 200;
                a.tz = a.z + up[1] * 200;
            } else if (!a.cub) {
                a.tx = a.x;
                a.tz = a.z;
            }
        }
    }

    // ------------------------------------------------------------------ Haltung

    /** Haltung nach Tätigkeit und Gang, Höhe auf dem Grund, Trittsiegel. */
    private void posture(Animal a, double dt) {
        float[] p = a.pose, g = a.goal;
        java.util.Arrays.fill(g, 0);
        double L = a.length, h = Math.toRadians(a.heading);
        double fx = Math.sin(h), fz = -Math.cos(h);
        // Neigung nach dem Gelände zwischen Hüfte und Schulter
        double gh = world.ground(a.x, a.z), gs = world.ground(a.x + fx * 0.47 * L, a.z + fz * 0.47 * L);
        double slope = Double.isNaN(gh) || Double.isNaN(gs) ? 0 : Math.atan2(gs - gh, 0.47 * L);
        g[BearBody.PITCH] = (float) slope;
        double sway = Math.sin(time * 0.7 + a.id * 1.3);
        switch (a.act) {
            case SCHNUEFFELN -> { g[BearBody.HEAD_LIFT] = -0.45f; g[BearBody.HEAD_YAW] = (float) (0.3 * sway); }
            case FISCHEN -> { g[BearBody.HEAD_LIFT] = a.lunge > 0 ? -0.6f : -0.32f; g[BearBody.HEAD_YAW] = (float) (0.15 * sway); }
            case FRESSEN -> { g[BearBody.HEAD_LIFT] = -0.55f; g[BearBody.JAW] = (float) (0.25 + 0.2 * Math.sin(time * 9 + a.id)); }
            case GRABEN -> { g[BearBody.HEAD_LIFT] = -0.42f; g[BearBody.ARCH] = 0.06f; }
            case AUFRICHTEN -> { g[BearBody.PITCH] = (float) (slope * 0.3 + 1.12); g[BearBody.HEAD_LIFT] = -0.75f; g[BearBody.HEAD_YAW] = (float) (0.25 * sway); }
            case LIEGEN -> { g[BearBody.HEAD_LIFT] = -0.18f; g[BearBody.ROLL] = 0.12f; g[BearBody.PITCH] = (float) (slope * 0.6); }
            case STEHEN -> { g[BearBody.HEAD_LIFT] = 0.05f; g[BearBody.HEAD_YAW] = (float) (0.45 * sway); }
            case FLUCHT -> g[BearBody.HEAD_LIFT] = 0.1f;
            default -> g[BearBody.HEAD_LIFT] = -0.12f;
        }
        // die Wirbelsäule weich hin zum Ziel (Aufrichten etwas schneller)
        double k = 1 - Math.exp(-dt * (a.act == Activity.AUFRICHTEN ? 3 : 2.2));
        for (int i = 0; i < BearBody.SWING; i++) p[i] += (float) ((g[i] - p[i]) * k);
        a.gait = bear.gait(a.speed, L);
        double level;
        if (a.act == Activity.AUFRICHTEN && a.speed < 0.3) level = rear(p);
        else if (a.act == Activity.LIEGEN && a.speed < 0.3) level = lie(a, p, k);
        else if (a.act == Activity.GRABEN) level = dig(p, time + a.id);
        else {
            a.phase += Bear.frequency(a.gait, a.speed, L) * dt;
            level = Bear.legs(a.gait, a.phase - Math.floor(a.phase), a.speed, L, p);
            footfalls(a, fx, fz);
        }
        a.level += (level - a.level) * (dt >= 1e2 ? 1 : 1 - Math.exp(-dt * 12));
        double gr = Double.isNaN(gh) ? 0 : gh;
        double dep = depth(a.x, a.z);
        a.y = gr - a.level * L - Math.min(dep, 0.0) * 0;
    }

    /** Aufgerichtet: die Hinterpfoten unter der Hüfte auf dem Grund, die Vorderbeine hängen. */
    private static double rear(float[] p) {
        double[] dz = new double[4], up = new double[4];
        float[] q = p.clone();
        for (int l = 2; l < 4; l++) BearBody.solveLeg(l, -0.42, 0.06, q);
        double plane = Math.min(BearBody.sole(2, q)[1], BearBody.sole(3, q)[1]);
        for (int l = 2; l < 4; l++) BearBody.solveLeg(l, plane, 0.06, p);
        for (int l = 0; l < 2; l++) { p[BearBody.SWING + l] += (float) ((0.55 - p[BearBody.SWING + l]) * 0.2); p[BearBody.BEND + l] += (float) ((0.9 - p[BearBody.BEND + l]) * 0.2); }
        return plane;
    }

    /** Liegend: Vorderbeine nach vorn gestreckt, Hinterbeine untergeschlagen, der Bauch auf dem Grund. */
    private static double lie(Animal a, float[] p, double k) {
        float[] t = {1.25f, 1.25f, 1.05f, 1.05f}, b = {0.25f, 0.25f, 2.0f, 2.0f};
        for (int l = 0; l < 4; l++) {
            p[BearBody.SWING + l] += (float) ((t[l] - p[BearBody.SWING + l]) * k);
            p[BearBody.BEND + l] += (float) ((b[l] - p[BearBody.BEND + l]) * k);
        }
        return Math.min(BearBody.lowest(p), BearBody.lowestFoot(p));
    }

    /** Graben: die Vorderpfoten scharren im Wechsel, die Hinterbeine stehen. */
    private static double dig(float[] p, double t) {
        double ph = t * 2 * Math.PI * 1.6;
        double[] dz = {0.10 + 0.10 * Math.sin(ph), 0.10 + 0.10 * Math.sin(ph + Math.PI), 0, 0};
        double[] up = {0.03 * Math.max(0, Math.cos(ph)), 0.03 * Math.max(0, Math.cos(ph + Math.PI)), 0, 0};
        return Bear.stand(dz, up, new boolean[]{false, false, true, true}, p);
    }

    /** Trittsiegel: wenn eine Pfote aufsetzt (Beginn ihres Stands), an ihrem Ort. */
    private void footfalls(Animal a, double fx, double fz) {
        if (a.gait == Bear.Gait.STAND) return;
        double t = a.phase - Math.floor(a.phase);
        for (int l = 0; l < 4; l++) {
            double u = t + a.gait.phase[l];
            u -= Math.floor(u);
            if (a.lastU[l] >= 0 && u < a.lastU[l] && depth(a.x, a.z) < 0.05) {
                double[] s = BearBody.sole(l, a.pose);
                double rx = Math.cos(Math.toRadians(a.heading)), rz = Math.sin(Math.toRadians(a.heading));
                double x = a.x + (rx * s[0] + fx * s[2]) * a.length, z = a.z + (rz * s[0] + fz * s[2]) * a.length;
                tracks++;
                TrackSink ts = onTrack;
                if (ts != null) ts.track(x, z, fx, fz, l, a.length);
            }
            a.lastU[l] = u;
        }
    }

    // ------------------------------------------------------------------ Ausgabe

    /** Zahl je Tätigkeit. */
    public int[] census() {
        int[] c = new int[Activity.values().length];
        for (Animal a : animals) c[a.act.ordinal()]++;
        return c;
    }

    /** Werte je Tier für {@code gl/BearGl}. */
    public static final int INST = 24;

    /**
     * Daten für die Grafik: je Tier {@link #INST} Werte (x, y, z, Länge · Richtung (rad), Nässe, Fell (+2 beim Jungen),
     * Takt · Kopf heben, Kopf drehen, Buckel, Neigung · Rolle, Kiefer, 0, 0 · Schwung ×4 · Beugung ×4). Gibt die Zahl.
     */
    public int instances(float[] out) {
        int n = 0;
        for (Animal a : animals) {
            if ((n + 1) * INST > out.length) break;
            write(out, n * INST, a.x, a.y, a.z, a.length, a.heading, a.wet, a.tone + (a.cub ? 2 : 0), a.phase, a.pose);
            n++;
        }
        return n;
    }

    /** Eine Instanz von Hand (Demo): Hüfte, Länge, Richtung (Grad), Nässe, Fell, Takt, Haltung. */
    public static void write(float[] out, int o, double x, double y, double z, double length, double headingDeg, double wet, double tone, double phase, float[] p) {
        out[o] = (float) x; out[o + 1] = (float) y; out[o + 2] = (float) z; out[o + 3] = (float) length;
        out[o + 4] = (float) Math.toRadians(headingDeg); out[o + 5] = (float) wet; out[o + 6] = (float) tone; out[o + 7] = (float) (phase - Math.floor(phase));
        out[o + 8] = p[BearBody.HEAD_LIFT]; out[o + 9] = p[BearBody.HEAD_YAW]; out[o + 10] = p[BearBody.ARCH]; out[o + 11] = p[BearBody.PITCH];
        out[o + 12] = p[BearBody.ROLL]; out[o + 13] = p[BearBody.JAW]; out[o + 14] = 0; out[o + 15] = 0;
        for (int l = 0; l < 4; l++) { out[o + 16 + l] = p[BearBody.SWING + l]; out[o + 20 + l] = p[BearBody.BEND + l]; }
    }

    /** Schatten: je Tier Mitte des Körpers x, Grund, z, Länge, Richtung (rad) (5 Werte); nicht im tiefen Wasser. Gibt die Zahl. */
    public int shadows(float[] out) {
        int n = 0;
        for (Animal a : animals) {
            if ((n + 1) * 5 > out.length || depth(a.x, a.z) > 0.3) continue;
            int o = n * 5;
            double h = Math.toRadians(a.heading), cx = a.x + Math.sin(h) * 0.4 * a.length, cz = a.z - Math.cos(h) * 0.4 * a.length;
            out[o] = (float) cx; out[o + 1] = (float) world.ground(cx, cz); out[o + 2] = (float) cz; out[o + 3] = (float) a.length; out[o + 4] = (float) h;
            n++;
        }
        return n;
    }

    /** „Bären 3 (2 Junge): 1 fischt, 1 geht, 1 liegt · 4 Lachse · Störer 420 m“ */
    public String describe() {
        int[] c = census();
        StringBuilder b = new StringBuilder();
        for (Activity k : Activity.values()) if (c[k.ordinal()] > 0) b.append(b.length() == 0 ? "" : ", ").append(c[k.ordinal()]).append(' ').append(k.label);
        int nc = 0;
        for (Animal a : animals) if (a.cub) nc++;
        double d = Double.POSITIVE_INFINITY;
        for (Animal a : animals) d = Math.min(d, nearestDisturber(a.x, a.z)[0]);
        return String.format(Locale.GERMAN, "Bären %d%s: %s · %d Lachse%s", animals.size() - nc, nc > 0 ? " (" + nc + " Junge)" : "", b, catches,
                Double.isInfinite(d) ? "" : String.format(Locale.GERMAN, " · Störer %.0f m", d));
    }
}
