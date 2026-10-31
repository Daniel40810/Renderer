package com.dan.herd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Haustiere als einfache Körper mit kurzen Bewegungen (Haithabu F10): Hühner, Ziegen, Schweine, Hunde. Jedes Tier
 * gehört zu einem Gehege ({@link Pen}: ein Kreis, in dem es bleibt), tut abwechselnd etwas Kurzes (stehen, fressen mit
 * dem Kopf am Boden, ein paar Schritte gehen, liegen, sich kratzen) und hält sich lose an die anderen seines Geheges.
 * Kommt ein Störer (das Auge) näher als die Fluchtdistanz, läuft es weg; der Hund kommt neugierig näher und setzt sich.
 * <p>
 * <b>Tag und Jahr:</b> Nachts sind Hühner im Stall (weg), Ziegen und Schweine liegen, der Hund liegt an der Tür. Bei
 * Regen über 2 mm/h gehen die Hühner hinein. Bei tiefem Schnee (über 20 cm) bleiben Ziegen und Schweine im Stall [G].
 * <p>
 * Ohne Zustand außerhalb dieser Klasse; gleicher Startwert, gleiche Bewegung. Nur Java 21, x Ost, y oben, z Süd.
 */
public final class Herd {

    /** Die Arten mit Maßen und Gangart. */
    public enum Kind {
        //        Name       Länge  Höhe  gehen  laufen  Schritt/s  Flucht  je Gehege
        HUHN("Huhn",       0.38, 0.40, 0.35, 2.2, 3.2, 3.0, 7),
        ZIEGE("Ziege",     1.05, 0.70, 0.55, 2.8, 1.6, 7.0, 6),
        SCHWEIN("Schwein", 1.10, 0.62, 0.45, 2.0, 1.5, 4.0, 4),
        HUND("Hund",       0.95, 0.58, 0.9, 4.0, 2.0, 0, 1);
        public final String label;
        /** Länge und Schulterhöhe (m); Geschwindigkeit im Gehen und Laufen (m/s); Schritte je s im Gehen; Fluchtdistanz (m). */
        public final double length, height, walk, run, stepHz, flight;
        /** Übliche Zahl je Gehege. */
        public final int group;
        Kind(String label, double length, double height, double walk, double run, double stepHz, double flight, int group) {
            this.label = label; this.length = length; this.height = height; this.walk = walk; this.run = run; this.stepHz = stepHz;
            this.flight = flight; this.group = group;
        }
    }

    /** Was ein Tier gerade tut. */
    public enum State { STAND, FEED, WALK, RUN, LIE, SIT, AWAY }

    /** Boden (Höhe am Ort), eigene kleine Schnittstelle. */
    @FunctionalInterface public interface Ground { double height(double x, double z); }

    /** Darf ein Tier hier stehen? (Wasser, Häuser, Zäune nein.) */
    @FunctionalInterface public interface Walkable { boolean at(double x, double z); }

    /** Ein Gehege: Kreis, Art, Zahl der Tiere; der Hund hat dazu Wegpunkte, zwischen denen er trabt. */
    public record Pen(String name, Kind kind, double x, double z, double r, int count, List<double[]> waypoints) {
        public Pen(String name, Kind kind, double x, double z, double r, int count) { this(name, kind, x, z, r, count, List.of()); }
    }

    /** Ein Tier. */
    public static final class Animal {
        public final int id;
        public final Kind kind;
        public final int pen;
        public final double[] pos = new double[3];
        /** Richtung (Grad von Nord), Geschwindigkeit (m/s), Phase des Gangs (rad), Kopf unten 0..1, Liegen 0..1, Schwanz. */
        public double heading, speed, gait, head, lie, wag;
        /** Farbe 0..1 (wählt Fell oder Gefieder), Größe um 1. */
        public final double tone, size;
        public volatile State state = State.STAND;
        double timer, tx, tz, peck, nextCall = -1;
        int wp;
        /** Seit Haithabu Phase 14: der erste Vogel jedes Hühnerhofs ist der Hahn (sieht aus wie die Hennen). */
        boolean rooster;
        public boolean rooster() { return rooster; }
        final Random rnd;
        Animal(int id, Kind k, int pen, long seed) {
            this.id = id; this.kind = k; this.pen = pen;
            rnd = new Random(seed * 1_000_003L + id * 7919L + k.ordinal() * 104729L);
            tone = rnd.nextDouble();
            size = k == Kind.HUND ? 1 : 0.85 + 0.3 * rnd.nextDouble();
        }
    }

    // ------------------------------------------------------------------ Eingänge
    public volatile long seed = 1;
    public volatile Ground ground = (x, z) -> 0;
    public volatile Walkable walkable = (x, z) -> true;
    public final List<Pen> pens = new CopyOnWriteArrayList<>();
    /** Wer stört (Auge): Orte x, y, z. */
    public final List<double[]> disturbers = new CopyOnWriteArrayList<>();
    /** Sonnenhöhe (Grad), Regen (mm/h), Schneehöhe (m). */
    public volatile double sunAltDeg = 30, rain, snow;
    /** Gebellt: Ort und Stärke (für einen Klang in einem anderen Package). */
    public volatile Bark onBark;
    @FunctionalInterface public interface Bark { void bark(double x, double y, double z, double strength, Animal a); }
    /**
     * Seit Haithabu Phase 14: ein Ruf (für einen Klang in einem anderen Package): Hühner gackern, der Hahn kräht in der
     * Dämmerung (auch aus dem Stall), Ziegen meckern, Schweine grunzen beim Fressen; beim Weglaufen rufen sie warnend
     * (Stärke 1). Der Hund meldet sich weiter über {@link #onBark}.
     */
    public volatile Call onCall;
    @FunctionalInterface public interface Call { void call(double x, double y, double z, double strength, Animal a); }

    // ------------------------------------------------------------------ Zustand
    private final List<Animal> animals = new CopyOnWriteArrayList<>();
    private int builtFor = -1;
    private double time;
    private long barks, calls;

    public List<Animal> animals() { return animals; }
    /** Rufe seit dem Start (ohne Bellen). */
    public long calls() { return calls; }
    public double time() { return time; }
    public long barks() { return barks; }

    /** Tiere in die Gehege setzen (geschieht beim ersten Schritt und wenn sich die Gehege ändern). */
    public void populate() {
        animals.clear();
        int id = 0;
        for (int p = 0; p < pens.size(); p++) {
            Pen pen = pens.get(p);
            for (int i = 0; i < pen.count(); i++) {
                Animal a = new Animal(id++, pen.kind(), p, seed);
                a.rooster = pen.kind() == Kind.HUHN && i == 0;
                double[] q = pointIn(a, pen);
                a.pos[0] = q[0]; a.pos[2] = q[1];
                a.pos[1] = groundAt(a.pos[0], a.pos[2]);
                a.heading = a.rnd.nextDouble() * 360;
                a.gait = a.rnd.nextDouble() * 6.28;
                choose(a);
                animals.add(a);
            }
        }
        builtFor = pens.hashCode();
    }

    private double groundAt(double x, double z) { Ground g = ground; return g == null ? 0 : g.height(x, z); }

    private boolean ok(double x, double z) { Walkable w = walkable; return w == null || w.at(x, z); }

    private double[] pointIn(Animal a, Pen p) {
        for (int k = 0; k < 24; k++) {
            double an = a.rnd.nextDouble() * 6.283, rr = p.r() * Math.sqrt(a.rnd.nextDouble());
            double x = p.x() + Math.sin(an) * rr, z = p.z() - Math.cos(an) * rr;
            if (ok(x, z)) return new double[]{x, z};
        }
        return new double[]{p.x(), p.z()};
    }

    public boolean night() { return sunAltDeg < -3; }

    /** Um dt Sekunden weiter (große Schritte in Stücken von 1/20 s, höchstens 3 s je Aufruf). */
    public void step(double dt) {
        if (builtFor != pens.hashCode()) populate();
        if (dt <= 0) return;
        double left = Math.min(3, dt);
        while (left > 1e-9) {
            double h = Math.min(0.05, left);
            left -= h;
            time += h;
            for (Animal a : animals) { stepAnimal(a, h); voice(a, h); }
        }
    }

    /** Ist das Tier draußen zu sehen, oder im Stall? */
    boolean inside(Animal a) {
        return switch (a.kind) {
            case HUHN -> night() || rain > 2 || snow > 0.15;
            case ZIEGE, SCHWEIN -> snow > 0.2;
            case HUND -> false;
        };
    }

    private void stepAnimal(Animal a, double h) {
        Pen pen = pens.get(a.pen);
        if (inside(a)) { a.state = State.AWAY; return; }
        if (a.state == State.AWAY) { double[] q = pointIn(a, pen); a.pos[0] = q[0]; a.pos[2] = q[1]; choose(a); }
        a.timer -= h;
        // Störer: weglaufen (der Hund kommt neugierig näher und setzt sich)
        double[] d = nearest(a);
        if (d != null) {
            double dx = a.pos[0] - d[0], dz = a.pos[2] - d[2], dist = Math.hypot(dx, dz);
            if (a.kind == Kind.HUND) {
                if (dist < 14 && dist > 2.2 && a.state != State.RUN && !night()) {
                    a.state = State.WALK;
                    a.tx = d[0] + dx / Math.max(dist, 1e-6) * 1.8;
                    a.tz = d[2] + dz / Math.max(dist, 1e-6) * 1.8;
                    a.timer = 6;
                } else if (dist <= 2.4 && a.state == State.WALK) {
                    a.state = State.SIT;
                    a.heading = Math.toDegrees(Math.atan2(-dx, dz));
                    a.timer = 8 + a.rnd.nextDouble() * 10;
                    bark(a, 0.5);
                }
            } else if (dist < a.kind.flight * a.size && a.state != State.RUN) {
                a.state = State.RUN;
                if (a.kind != Kind.SCHWEIN || a.rnd.nextDouble() < 0.3) call(a, 1);
                double l = Math.max(dist, 1e-6);
                a.tx = a.pos[0] + dx / l * (a.kind.flight * 2.2 + 2);
                a.tz = a.pos[2] + dz / l * (a.kind.flight * 2.2 + 2);
                // im Gehege bleiben
                double ox = a.tx - pen.x(), oz = a.tz - pen.z(), ol = Math.hypot(ox, oz);
                if (ol > pen.r() * 1.3) { a.tx = pen.x() + ox / ol * pen.r() * 1.3; a.tz = pen.z() + oz / ol * pen.r() * 1.3; }
                a.timer = 4;
            }
        }
        if (a.timer <= 0) choose(a);
        switch (a.state) {
            case WALK, RUN -> {
                double v = a.state == State.RUN ? a.kind.run : a.kind.walk * (a.kind == Kind.HUND && a.wp >= 0 ? 1.4 : 1);
                double dx = a.tx - a.pos[0], dz = a.tz - a.pos[2], dist = Math.hypot(dx, dz);
                if (dist < 0.15) { a.speed = 0; if (a.state == State.RUN || a.kind != Kind.HUND) choose(a); else arrive(a, pen); break; }
                double want = Math.toDegrees(Math.atan2(dx, -dz));
                a.heading = turn(a.heading, want, (a.state == State.RUN ? 400 : 160) * h);
                a.speed += (v - a.speed) * Math.min(1, h * 4);
                double fa = Math.toRadians(a.heading), nx = a.pos[0] + Math.sin(fa) * a.speed * h, nz = a.pos[2] - Math.cos(fa) * a.speed * h;
                if (ok(nx, nz)) { a.pos[0] = nx; a.pos[2] = nz; }
                else { a.heading = (a.heading + 120 + a.rnd.nextDouble() * 120) % 360; a.tx = a.pos[0]; a.tz = a.pos[2]; a.speed = 0; choose(a); }
                a.head += (0 - a.head) * Math.min(1, h * 5);
                a.lie = Math.max(0, a.lie - h * 2);
            }
            case FEED -> {
                a.speed *= Math.max(0, 1 - h * 6);
                // Hühner picken schnell, Ziegen und Schweine halten den Kopf unten und gehen ab und zu einen Schritt
                if (a.kind == Kind.HUHN) {
                    a.peck -= h;
                    if (a.peck <= 0) a.peck = 0.25 + a.rnd.nextDouble() * 0.8;
                    double target = a.peck < 0.18 ? 1 : 0.25;
                    a.head += (target - a.head) * Math.min(1, h * 18);
                } else {
                    a.head += (1 - a.head) * Math.min(1, h * 3);
                    if (a.rnd.nextDouble() < h / 3) {
                        double fa = Math.toRadians(a.heading + (a.rnd.nextDouble() - 0.5) * 60);
                        double nx = a.pos[0] + Math.sin(fa) * 0.3, nz = a.pos[2] - Math.cos(fa) * 0.3;
                        if (ok(nx, nz) && Math.hypot(nx - pen.x(), nz - pen.z()) < pen.r()) { a.pos[0] = nx; a.pos[2] = nz; a.gait += 1.5; }
                        a.heading = Math.toDegrees(fa);
                    }
                }
                a.lie = Math.max(0, a.lie - h * 2);
            }
            case LIE -> { a.speed = 0; a.lie = Math.min(1, a.lie + h * 1.2); a.head += (0.3 - a.head) * Math.min(1, h * 2); }
            case SIT -> { a.speed = 0; a.lie = Math.max(0, a.lie - h * 2); a.head *= Math.max(0, 1 - h * 3); }
            default -> { a.speed *= Math.max(0, 1 - h * 6); a.head += (0 - a.head) * Math.min(1, h * 4); a.lie = Math.max(0, a.lie - h * 2); }
        }
        // Gang: Phase nach zurückgelegtem Weg (Schrittlänge aus Frequenz und Gehgeschwindigkeit)
        double stride = a.kind.walk / a.kind.stepHz;
        a.gait = (a.gait + a.speed * h / stride * 2 * Math.PI) % (2 * Math.PI);
        a.wag = a.kind == Kind.HUND ? Math.sin(time * (a.state == State.SIT ? 9 : 5) + a.id) * (a.state == State.LIE ? 0.1 : 0.6) : 0;
        a.pos[1] = groundAt(a.pos[0], a.pos[2]);
        if (a.kind == Kind.HUND && !night() && a.rnd.nextDouble() < h / 90) bark(a, 0.8);
    }

    /**
     * Rufe nach Art und Tageszeit [G]: Huhn alle 25 s im Mittel, der Hahn bei Sonnenhöhe −8° bis 12° alle 40 s (auch
     * im Stall), sonst alle 6 min; Ziege alle 2 min; Schwein beim Fressen alle 10 s, sonst alle 40 s.
     */
    private void voice(Animal a, double h) {
        if (a.kind == Kind.HUND) return;
        double rate;
        boolean away = a.state == State.AWAY;
        if (a.rooster && sunAltDeg > -8 && sunAltDeg < 12) rate = 1 / 40.0;
        else if (away || night()) rate = 0;
        else rate = switch (a.kind) {
            case HUHN -> a.rooster ? 1 / 360.0 : 1 / 25.0;
            case ZIEGE -> 1 / 120.0;
            case SCHWEIN -> a.state == State.FEED ? 1 / 10.0 : 1 / 40.0;
            default -> 0;
        };
        if (rate <= 0) return;
        // neu ansetzen, wenn der Ruf noch weit weg ist, die Rate aber gestiegen ist (der Hahn in der Dämmerung)
        if (a.nextCall < 0 || a.nextCall - time > 4 / rate) a.nextCall = time + a.rnd.nextDouble() / rate;
        if (time < a.nextCall) return;
        a.nextCall = time + 1.5 - Math.log(1 - a.rnd.nextDouble()) / rate;
        call(a, 0.5 + 0.3 * a.rnd.nextDouble());
    }

    private void call(Animal a, double s) {
        calls++;
        Call c = onCall;
        if (c != null) c.call(a.pos[0], a.pos[1] + a.kind.height * 0.8, a.pos[2], s, a);
    }

    private void bark(Animal a, double s) {
        barks++;
        Bark b = onBark;
        if (b != null) b.bark(a.pos[0], a.pos[1] + 0.5, a.pos[2], s, a);
    }

    /** Der Hund erreicht einen Wegpunkt: schnüffeln, setzen oder weiter. */
    private void arrive(Animal a, Pen p) {
        a.state = a.rnd.nextDouble() < 0.4 ? State.FEED : a.rnd.nextDouble() < 0.5 ? State.SIT : State.STAND;
        a.timer = 3 + a.rnd.nextDouble() * 8;
    }

    private double[] nearest(Animal a) {
        double best = 1e18;
        double[] r = null;
        for (double[] d : disturbers) {
            double q = (d[0] - a.pos[0]) * (d[0] - a.pos[0]) + (d[2] - a.pos[2]) * (d[2] - a.pos[2]);
            if (Math.abs(d[1] - a.pos[1]) < 6 && q < best) { best = q; r = d; }
        }
        return r;
    }

    /** Was als Nächstes: nach Art, Tageszeit und den anderen im Gehege. */
    private void choose(Animal a) {
        Pen p = pens.get(a.pen);
        boolean night = night();
        double u = a.rnd.nextDouble();
        if (night && a.kind != Kind.HUHN) { a.state = State.LIE; a.timer = 60 + a.rnd.nextDouble() * 240; return; }
        if (a.kind == Kind.HUND && !p.waypoints().isEmpty()) {
            if (u < 0.55) {
                a.wp = (a.wp + 1 + a.rnd.nextInt(Math.max(1, p.waypoints().size() - 1))) % p.waypoints().size();
                double[] w = p.waypoints().get(a.wp);
                a.tx = w[0] + (a.rnd.nextDouble() - 0.5) * 2;
                a.tz = w[1] + (a.rnd.nextDouble() - 0.5) * 2;
                a.state = State.WALK;
                a.timer = 60;
            } else if (u < 0.75) { a.state = State.LIE; a.timer = 20 + a.rnd.nextDouble() * 60; }
            else { a.state = State.SIT; a.timer = 6 + a.rnd.nextDouble() * 15; }
            return;
        }
        // Mittagsruhe der Ziegen und Schweine (sie liegen öfter, wenn die Sonne hoch steht)
        double rest = a.kind == Kind.HUHN ? 0.03 : sunAltDeg > 30 ? 0.15 : 0.05;
        if (u < rest) { a.state = State.LIE; a.timer = a.kind == Kind.HUHN ? 8 + a.rnd.nextDouble() * 25 : 20 + a.rnd.nextDouble() * 60; return; }
        if (u < rest + (a.kind == Kind.HUHN ? 0.5 : 0.45)) { a.state = State.FEED; a.timer = (a.kind == Kind.HUHN ? 3 : 8) + a.rnd.nextDouble() * 15; return; }
        if (u < rest + 0.75) {
            // ein paar Schritte, eher zur Gruppe hin
            double cx = 0, cz = 0;
            int n = 0;
            for (Animal o : animals) if (o != a && o.pen == a.pen && o.state != State.AWAY) { cx += o.pos[0]; cz += o.pos[2]; n++; }
            double gx = n > 0 ? cx / n : p.x(), gz = n > 0 ? cz / n : p.z();
            double reach = a.kind == Kind.HUHN ? 1.5 : 4;
            for (int k = 0; k < 8; k++) {
                double an = a.rnd.nextDouble() * 6.283, rr = reach * (0.3 + a.rnd.nextDouble());
                double tx = a.pos[0] + Math.sin(an) * rr + (gx - a.pos[0]) * 0.25, tz = a.pos[2] - Math.cos(an) * rr + (gz - a.pos[2]) * 0.25;
                if (Math.hypot(tx - p.x(), tz - p.z()) < p.r() && ok(tx, tz)) { a.tx = tx; a.tz = tz; a.state = State.WALK; a.timer = 15; return; }
            }
        }
        a.state = State.STAND;
        a.timer = 1 + a.rnd.nextDouble() * 5;
    }

    static double turn(double from, double to, double maxStep) {
        double d = ((to - from) % 360 + 540) % 360 - 180;
        return (from + Math.max(-maxStep, Math.min(maxStep, d)) + 360) % 360;
    }

    /** Zahl der Tiere je Art, die draußen sind. */
    public int outside(Kind k) {
        int n = 0;
        for (Animal a : animals) if (a.kind == k && a.state != State.AWAY) n++;
        return n;
    }

    public int count(Kind k, State s) {
        int n = 0;
        for (Animal a : animals) if (a.kind == k && a.state == s) n++;
        return n;
    }

    /** „31 Tiere: 21 Hühner (13 draußen), 6 Ziegen (2 liegen), 3 Schweine, 1 Hund · 4 mal gebellt“ */
    public String describe() {
        StringBuilder b = new StringBuilder(String.format(Locale.GERMAN, "%d Tiere:", animals.size()));
        String[] names = {"Hühner", "Ziegen", "Schweine", "Hunde"};
        boolean first = true;
        for (Kind k : Kind.values()) {
            int n = 0;
            for (Animal a : animals) if (a.kind == k) n++;
            if (n == 0) continue;
            int out = outside(k), lie = count(k, State.LIE);
            b.append(first ? " " : ", ").append(n).append(' ').append(n == 1 && k == Kind.HUND ? "Hund" : names[k.ordinal()]);
            if (out < n) b.append(" (").append(out).append(" draußen)");
            else if (lie > 0) b.append(" (").append(lie).append(" liegen)");
            first = false;
        }
        b.append(String.format(Locale.GERMAN, " · %d mal gebellt", barks));
        return b.toString();
    }

    /** Gehege als Liste (für Anzeigen). */
    public List<String> penNames() {
        List<String> l = new ArrayList<>();
        for (Pen p : pens) l.add(p.name());
        return l;
    }
}
