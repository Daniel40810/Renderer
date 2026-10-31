package com.dan.sail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Eine Regatta (seit Timmendorf Phase 8, F13 A): eine Bahn auf und ab (Luv-Lee) mit Startlinie zwischen Startschiff
 * und Pin, Luvtonne und Leetor; das Startverfahren nach den Wettfahrtregeln (Ankündigung 5 min, Vorbereitung 4 min,
 * eine Minute, Start); ein Feld von Booten, das sich vor dem Start unter der Linie hält, auf die Linie zufährt, kreuzt
 * (Wende an den Anliegelinien oder am Rand des Felds), die Luvtonne rundet, mit Gennaker raumschots zum Tor fährt und
 * nach der letzten Runde durchs Ziel geht.
 * <p>
 * Wegerecht vereinfacht nach den Wettfahrtregeln 10 bis 12: Backbordbug weicht Steuerbordbug, auf gleichem Bug weicht
 * Luv, klar achteraus weicht klar voraus. Wer ausweichen muss, fällt ab (taucht hinter dem anderen durch), luvt an oder
 * nimmt Fahrt weg. Frühstarter müssen zurück unter die Linie.
 * <p>
 * Abdeckung: jedes Boot nimmt den Booten in seiner Lee Wind weg (Kegel nach Lee, 10 Masthöhen lang, bis 30 % weniger)
 * [G nach dem Bild der Windschatten bei Marchaj 1979]. Die Regatta setzt dazu eine eigene {@link Sail.Wind} in den
 * {@link Sail}-Kern; der Wind von außen ({@link #base}) bleibt die Quelle.
 * <p>
 * Raum der Szenen: x Ost, z Süd, Meter; Kurse als Kompasskurs, der Wind, woher er kommt.
 */
public final class Regatta {
    /** Abschnitt eines Boots. */
    public enum Leg { PRESTART, BEAT, RUN, FINISH, DONE }

    /** Ereignisse nach außen (Klang, Regie, HUD). */
    public interface Listener {
        /** Signal des Startverfahrens: „Ankündigung“, „Vorbereitung“, „Eine Minute“, „Start“, „Einzelrückruf“, „Ziel“. */
        default void onSignal(String what, double t) { }
        default void onRound(Entry e, String mark, double t) { }
        default void onFinish(Entry e, int place, double t) { }
        /** {@code keepClear} weicht {@code right} aus; Regel 10, 11 oder 12. */
        default void onGiveWay(Entry keepClear, Entry right, int rule, double t) { }
    }

    /**
     * Die Bahn: Mitte der Startlinie, Richtung, aus der der Wind kommt (dorthin liegt die Luvtonne), Länge bis zur
     * Luvtonne, Länge der Linie, Runden. Startschiff an Steuerbord (rechts, wenn man nach Luv schaut), Pin an Backbord;
     * das Leetor 80 m über der Linie, 70 m weit; Ziel ist die Startlinie.
     */
    public static final class Course {
        public final double cx, cz, axisDeg, length, line;
        public final int laps;

        public Course(double cx, double cz, double axisDeg, double length, double line, int laps) {
            this.cx = cx; this.cz = cz; this.axisDeg = ((axisDeg % 360) + 360) % 360; this.length = length; this.line = line; this.laps = Math.max(1, laps);
        }

        /** Bahn aus Startschiff und Luvtonne (etwa aus dem Netz {@code TD_NET_SEE}); die Linie reicht vom Startschiff nach Backbord. */
        public static Course fromMarks(double boatX, double boatZ, double windwardX, double windwardZ, double line, int laps) {
            double ax = windwardX - boatX, az = windwardZ - boatZ;
            double axis = Math.toDegrees(Math.atan2(ax, -az));
            double[] p = perp(axis);
            double cx = boatX - p[0] * line / 2, cz = boatZ - p[1] * line / 2;
            double len = Math.hypot(windwardX - cx, windwardZ - cz);
            return new Course(cx, cz, axis, len, line, laps);
        }

        /** Einheitsvektor nach Luv (x, z). */
        public double[] up() { double a = Math.toRadians(axisDeg); return new double[]{Math.sin(a), -Math.cos(a)}; }
        /** Einheitsvektor nach rechts, wenn man nach Luv schaut (x, z). */
        public double[] right() { return perp(axisDeg); }
        static double[] perp(double axis) { double a = Math.toRadians(axis); return new double[]{Math.cos(a), Math.sin(a)}; }
        public double[] committee() { double[] p = right(); return new double[]{cx + p[0] * line / 2, cz + p[1] * line / 2}; }
        public double[] pin() { double[] p = right(); return new double[]{cx - p[0] * line / 2, cz - p[1] * line / 2}; }
        public double[] windward() { double[] u = up(); return new double[]{cx + u[0] * length, cz + u[1] * length}; }
        public double[] gate(int side) {
            double[] u = up(), p = right();
            return new double[]{cx + u[0] * 80 + side * p[0] * 35, cz + u[1] * 80 + side * p[1] * 35};
        }
        /** Abstand über der Linie (m, + auf der Bahnseite). */
        public double above(double x, double z) { double[] u = up(); return (x - cx) * u[0] + (z - cz) * u[1]; }
        /** Lage längs der Linie (m, + zum Startschiff). */
        public double along(double x, double z) { double[] p = right(); return (x - cx) * p[0] + (z - cz) * p[1]; }
        /** Alle Tonnen mit Namen: Startschiff, Pin, Luv, Tor links, Tor rechts. */
        public List<double[]> marks() { return List.of(committee(), pin(), windward(), gate(-1), gate(1)); }
        public static final String[] MARK_NAMES = {"Startschiff", "Pin", "Luvtonne", "Tor links", "Tor rechts"};
    }

    /** Ein Boot im Feld. */
    public static final class Entry {
        public final Sail.Boat boat;
        public final int number;
        public Leg leg = Leg.PRESTART;
        public int lap, place, tacks, gybes;
        public double finishTime = Double.NaN;
        /** Frühstart: muss zurück unter die Linie. */
        public boolean ocs;
        /** Wunschplatz an der Linie (−0,5 Pin … 0,5 Startschiff), Breite des Felds, in dem es kreuzt (Anteil). */
        final double spot, corridor;
        boolean goingRight, wasAbove;
        double lastTurn = -1e9, avoidUntil = -1e9, avoidDelta, avoidEase, lastFlip = -1e9;
        Entry(Sail.Boat b, int number, double spot, double corridor) { this.boat = b; this.number = number; this.spot = spot; this.corridor = corridor; }
        /** „Nr. 7 · 2. Kreuz“ */
        public String status() {
            return switch (leg) {
                case PRESTART -> ocs ? "Frühstart, zurück" : "vor dem Start";
                case BEAT -> (lap + 1) + ". Kreuz" + (ocs ? " (Frühstart)" : "");
                case RUN -> (lap + 1) + ". Vorwind";
                case FINISH -> "zum Ziel";
                case DONE -> place + ". im Ziel";
            };
        }
    }

    public final Sail sail;
    public final Course course;
    /** Wind ohne Abdeckung (von außen). */
    public final Sail.Wind base;
    public final List<Entry> entries = new ArrayList<>();
    public Listener listener = new Listener() { };
    /** Abdeckung an (Vorgabe) oder aus. */
    public boolean shadows = true;
    /**
     * Wind, nach dem die Crews ihre Winkel wählen (m/s; NaN: der Wind an der Bahn). Die Szene setzt hier das Mittel, damit
     * Böen nicht jedes Mal neue Polaren verlangen.
     */
    public double tacticsWind = Double.NaN;
    private double time, gun = Double.NaN, sequence = 300;
    private int finished, giveWays, contacts, lastSignal = -1, race;
    private final Random rnd;
    private final double[] lastFinish = {Double.NaN};

    /**
     * Ein Feld auf einer Bahn. Der Wind kommt aus {@code sail.wind} (oder dessen offenen Werten); die Regatta legt die
     * Abdeckung darüber und setzt sie in {@code sail}.
     */
    public Regatta(Sail sail, Course course, long seed) {
        this.sail = sail;
        this.course = course;
        Sail.Wind w = sail.wind;
        final double ws = sail.windSpeed, wf = sail.windFromDeg;
        this.base = w != null ? w : new Sail.Wind() {
            public double speed(double x, double z, double t) { return ws; }
            public double fromDeg(double x, double z, double t) { return wf; }
        };
        this.rnd = new Random(seed);
        sail.wind = new Sail.Wind() {
            public double speed(double x, double z, double t) { return base.speed(x, z, t) * (shadows ? shade(x, z, t) : 1); }
            public double fromDeg(double x, double z, double t) { return base.fromDeg(x, z, t); }
        };
    }

    /** Boote einer Klasse ins Feld setzen, unter der Linie verteilt. */
    public List<Entry> add(Sail.Rig rig, int count) {
        List<Entry> out = new ArrayList<>();
        double[] u = course.up(), p = course.right();
        for (int i = 0; i < count; i++) {
            Sail.Boat b = new Sail.Boat(rig);
            double along = (rnd.nextDouble() - 0.5) * course.line * 1.2, below = 60 + rnd.nextDouble() * 160;
            b.x = course.cx + p[0] * along - u[0] * below;
            b.z = course.cz + p[1] * along - u[1] * below;
            b.headingDeg = (course.axisDeg + 90 + rnd.nextDouble() * 20) % 360;
            b.speed = Sail.hullSpeed(rig) * 0.5;
            b.skill = 0.92 + 0.08 * rnd.nextDouble();
            int nr = entries.size() + 1;
            b.label = String.valueOf(nr);
            double spot = (rnd.nextDouble() - 0.5) * 0.9, corridor = 0.25 + 0.75 * rnd.nextDouble();
            Entry e = new Entry(b, nr, spot, corridor);
            entries.add(e);
            out.add(e);
        }
        return out;
    }

    /** Das Startverfahren beginnt jetzt; Start nach {@code sequence} Sekunden (Wettfahrtregeln: 300). */
    public void start(double sequence) {
        this.sequence = Math.max(30, sequence);
        gun = time + this.sequence;
        lastSignal = -1;
        finished = 0;
        race++;
        for (Entry e : entries) {
            e.leg = Leg.PRESTART;
            e.lap = 0;
            e.place = 0;
            e.ocs = false;
            e.finishTime = Double.NaN;
            e.boat.ease = 0;
        }
    }

    /**
     * Seit Timmendorf Phase 17 („Weiter wie zuletzt“): eine gespeicherte Wettfahrt fortsetzen. Laufzeit, Startschuss
     * (NaN: kein Verfahren), Zahl im Ziel und Nummer der Wettfahrt; Lage, Schlag und Runde der Boote setzt der Aufrufer
     * in {@link #entries}. Die Signale vor der Laufzeit kommen nicht noch einmal.
     */
    public void resume(double time, double gun, int finished, int race) {
        this.time = time;
        this.gun = gun;
        this.finished = Math.max(0, finished);
        this.race = Math.max(this.race, race);
        double tg = Double.isNaN(gun) ? Double.NaN : gun - time;
        double[] at = {300, 240, 60, 0};
        lastSignal = -1;
        if (!Double.isNaN(tg)) for (int i = 0; i < at.length; i++) if (tg <= at[i]) lastSignal = i;
    }

    /** Laufzeit (s), Zeit bis zum Start (s, negativ danach). */
    public double time() { return time; }
    public double toGun() { return Double.isNaN(gun) ? Double.NaN : gun - time; }
    public int finished() { return finished; }
    public int giveWays() { return giveWays; }
    /** Berührungen (Rümpfe näher als eine halbe Länge). */
    public int contacts() { return contacts; }
    public int race() { return race; }
    /** Alle im Ziel (oder ohne Boote). */
    public boolean over() { return !Double.isNaN(gun) && finished >= entries.size(); }

    // ------------------------------------------------------------------ Abdeckung

    /**
     * Anteil des Winds am Ort (0..1) hinter allen Booten: Kegel nach Lee ab einer halben Bootslänge, Breite 0,6 Masthöhen
     * plus 12° Öffnung, bis 30 % weniger, abnehmend über 5 Masthöhen [G].
     */
    public double shade(double x, double z, double t) {
        double from = Math.toRadians(base.fromDeg(x, z, t)), dx = -Math.sin(from), dz = Math.cos(from);
        double f = 1;
        for (Entry e : entries) {
            Sail.Boat b = e.boat;
            if (b.capsized || b.furled || e.leg == Leg.DONE) continue;
            double rx = x - b.x, rz = z - b.z, down = rx * dx + rz * dz, h = b.rig.mastHeight();
            if (down < b.rig.loa() * 0.5 || down > 10 * h) continue;
            double lat = Math.abs(rx * dz - rz * dx), wid = 0.6 * h + down * Math.tan(Math.toRadians(12));
            f *= 1 - 0.30 * Math.exp(-down / (5 * h)) * Math.exp(-(lat / wid) * (lat / wid));
        }
        return f;
    }

    // ------------------------------------------------------------------ Schritt

    /** Rechnet dt Sekunden: Signale, Taktik je Boot, Wegerecht, Segeln, Tonnen und Ziel. */
    public void step(double dt) {
        if (dt <= 0) return;
        time += dt;
        signals();
        double tws = Double.isNaN(tacticsWind) ? base.speed(course.cx, course.cz, time) : tacticsWind, twf = base.fromDeg(course.cx, course.cz, time);
        for (Entry e : entries) tactics(e, tws, twf);
        rules(twf);
        for (Entry e : entries) {
            // seit Timmendorf Phase 9: vor dem Start kreuzen alle mit Groß und Fock, der Gennaker bleibt im Sack
            e.boat.downwindAllowed = e.leg != Leg.PRESTART;
            double before = course.above(e.boat.x, e.boat.z);
            boolean side = e.boat.windSide() > 0;
            sail.step(e.boat, dt);
            if ((e.boat.windSide() > 0) != side && e.leg != Leg.PRESTART && time - e.lastFlip > 8) {
                if (e.boat.last[4] < 90) e.tacks++; else e.gybes++;
                e.lastFlip = time;
            }
            marks(e, before);
        }
        separate();
    }

    private void signals() {
        if (Double.isNaN(gun)) return;
        double tg = gun - time;
        double[] at = {300, 240, 60, 0};
        String[] what = {"Ankündigung", "Vorbereitung", "Eine Minute", "Start"};
        for (int i = 0; i < at.length; i++)
            if (i > lastSignal && tg <= at[i] && at[i] <= sequence + 1e-9) {
                lastSignal = i;
                listener.onSignal(what[i], time);
                if (i == 3) start();
            }
    }

    /** Der Startschuss: wer über der Linie ist, ist Frühstarter (Einzelrückruf). */
    private void start() {
        sail.events.onStart(time);
        boolean any = false;
        for (Entry e : entries) {
            if (e.leg != Leg.PRESTART) continue;
            double a = course.above(e.boat.x, e.boat.z);
            e.ocs = a > 0 && Math.abs(course.along(e.boat.x, e.boat.z)) < course.line * 0.6;
            any |= e.ocs;
            e.leg = Leg.BEAT;
            e.boat.ease = 0;
            e.goingRight = false;
            e.wasAbove = a > 0;
        }
        if (any) listener.onSignal("Einzelrückruf", time);
    }

    /** Taktik eines Boots: Ziel, Bug, Wende an der Anliegelinie oder am Rand seines Felds; vor dem Start Zeit und Platz. */
    private void tactics(Entry e, double tws, double twf) {
        Sail.Boat b = e.boat;
        if (b.capsized) return;
        double[] v = sail.vmgCached(b.rig, tws);
        double up = v[0], dn = v[3];
        double[] u = course.up(), p = course.right();
        double ease = 0, heading;
        switch (e.leg) {
            case PRESTART -> {
                if (Double.isNaN(gun)) { heading = (course.axisDeg + 180 + 90) % 360; ease = 0.6; break; }
                double tg = gun - time;
                double[] spot = {course.cx + p[0] * e.spot * course.line - u[0] * 3, course.cz + p[1] * e.spot * course.line - u[1] * 3};
                double d = Math.hypot(spot[0] - b.x, spot[1] - b.z);
                double vClose = Math.max(0.5, v[2]);
                double eta = d / vClose + 6;
                if (tg > eta + 25) {
                    // halten: 60 bis 150 m unter der Linie auf und ab reichen
                    double a = course.above(b.x, b.z);
                    double back = 70 + 28 * (e.number % 5);
                    double hx = spot[0] - u[0] * back, hz = spot[1] - u[1] * back;
                    boolean far = Math.hypot(hx - b.x, hz - b.z) > 60 || a > -30;
                    heading = far ? clampTwa(bearing(b.x, b.z, hx, hz), twf, up + 5) : (course.axisDeg + (course.along(b.x, b.z) > e.spot * course.line ? -95 : 95) + 360) % 360;
                    ease = far ? 0 : 0.55;
                } else {
                    heading = clampTwa(bearing(b.x, b.z, spot[0], spot[1]), twf, up);
                    double early = tg - eta;
                    ease = Math.max(0, Math.min(0.85, -early < 0 ? early / 12 : 0));
                }
            }
            case BEAT -> {
                if (e.ocs) {
                    // zurück unter die Linie, dann neu starten
                    heading = (course.axisDeg + 180) % 360;
                    if (course.above(b.x, b.z) < -2 * b.rig.loa()) { e.ocs = false; e.lastTurn = time; }
                    break;
                }
                double[] m = course.windward();
                heading = laylineHeading(e, b, m, course.axisDeg, up, true, twf, up);
            }
            case RUN -> {
                double[] m = gateFor(b);
                heading = laylineHeading(e, b, m, (course.axisDeg + 180) % 360, 180 - dn, false, twf, up);
            }
            case FINISH -> {
                double[] m = {course.cx, course.cz};
                heading = laylineHeading(e, b, m, (course.axisDeg + 180) % 360, 180 - dn, false, twf, up);
            }
            default -> {
                // im Ziel: zur Seite abfallen und Fahrt wegnehmen
                heading = (course.axisDeg + 125) % 360;
                ease = 0.7;
            }
        }
        if (time < e.avoidUntil) {
            heading = (heading + e.avoidDelta + 360) % 360;
            ease = Math.max(ease, e.avoidEase);
        }
        b.targetHeadingDeg = heading;
        b.ease = ease;
    }

    /**
     * Kurs auf einem Schlag nach Luv oder Lee: Achse zur Tonne, Winkel θ zur Achse; Wende (oder Halse), wenn die Tonne
     * auf der Anliegelinie liegt oder das Boot den Rand seines Felds erreicht.
     */
    private double laylineHeading(Entry e, Sail.Boat b, double[] m, double axis, double theta, boolean beat, double twf, double up) {
        double ar = Math.toRadians(axis), ax = Math.sin(ar), az = -Math.cos(ar), rx = Math.cos(ar), rz = Math.sin(ar);
        double dx = m[0] - b.x, dz = m[1] - b.z;
        double along = dx * ax + dz * az, lat = dx * rx + dz * rz;   // lat > 0: Tonne rechts der Achse
        // an der Tonne vorbei (sie liegt nicht mehr voraus): direkt hin, so hoch es geht
        if (along < 0) return clampTwa(bearing(b.x, b.z, m[0], m[1]), twf, up);
        double tan = Math.tan(Math.toRadians(theta)) * 1.04;
        double field = e.corridor * course.length * 0.45;
        boolean turn;
        if (along < 2 * b.rig.loa()) turn = false;
        else if (e.goingRight) turn = lat <= -Math.min(Math.max(0, along) * tan, field) || lat <= -Math.max(0, along) * tan;
        else turn = lat >= Math.min(Math.max(0, along) * tan, field) || lat >= Math.max(0, along) * tan;
        if (turn && time - e.lastTurn > 15) { e.goingRight = !e.goingRight; e.lastTurn = time; }
        double h = axis + (e.goingRight ? theta : -theta);
        // nahe der Tonne direkt hin, wenn sie anliegt
        double brg = bearing(b.x, b.z, m[0], m[1]);
        double off = Math.abs(((brg - axis) % 360 + 540) % 360 - 180);
        if (along < 8 * b.rig.loa() && (beat ? off >= theta : off <= theta)) h = brg;
        return (h % 360 + 360) % 360;
    }

    private double[] gateFor(Sail.Boat b) {
        double[] l = course.gate(-1), r = course.gate(1);
        return Math.hypot(l[0] - b.x, l[1] - b.z) < Math.hypot(r[0] - b.x, r[1] - b.z) ? l : r;
    }

    /** Tonnen runden, Ziel. */
    private void marks(Entry e, double before) {
        Sail.Boat b = e.boat;
        double r = 2.5 * b.rig.loa() + 8;
        switch (e.leg) {
            case BEAT -> {
                double[] m = course.windward();
                if (!e.ocs && Math.hypot(m[0] - b.x, m[1] - b.z) < r) {
                    listener.onRound(e, "Luvtonne", time);
                    e.leg = e.lap + 1 >= course.laps ? Leg.FINISH : Leg.RUN;
                }
            }
            case RUN -> {
                double[] m = gateFor(b);
                if (Math.hypot(m[0] - b.x, m[1] - b.z) < r) {
                    listener.onRound(e, "Leetor", time);
                    e.lap++;
                    e.leg = Leg.BEAT;
                }
            }
            case FINISH -> {
                double a = course.above(b.x, b.z);
                if (before > 0 && a <= 0 && Math.abs(course.along(b.x, b.z)) <= course.line / 2 + b.rig.loa()) {
                    e.leg = Leg.DONE;
                    e.place = ++finished;
                    e.finishTime = time - gun;
                    lastFinish[0] = time;
                    listener.onFinish(e, e.place, time);
                    if (e.place == 1) listener.onSignal("Ziel", time);
                }
            }
            default -> { }
        }
    }

    /**
     * Wegerecht (Regeln 10 bis 12, vereinfacht): sagt der nächste Annäherungspunkt der beiden Boote binnen 8 s weniger als
     * 1,3 Bootslängen voraus, weicht der Pflichtige aus.
     */
    private void rules(double twf) {
        int n = entries.size();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                Entry a = entries.get(i), c = entries.get(j);
                Sail.Boat p = a.boat, q = c.boat;
                if (a.leg == Leg.DONE || c.leg == Leg.DONE || p.capsized || q.capsized) continue;
                double dx = q.x - p.x, dz = q.z - p.z, len = (p.rig.loa() + q.rig.loa()) / 2;
                if (dx * dx + dz * dz > Math.pow(6 * len + 20, 2)) continue;
                double[] vp = vel(p), vq = vel(q);
                double rvx = vq[0] - vp[0], rvz = vq[1] - vp[1], rv2 = rvx * rvx + rvz * rvz;
                double tc = rv2 < 1e-6 ? 0 : Math.max(0, Math.min(8, -(dx * rvx + dz * rvz) / rv2));
                double mx = dx + rvx * tc, mz = dz + rvz * tc;
                if (Math.hypot(mx, mz) > 1.3 * len + 1.5) continue;
                Entry keep, right;
                int rule;
                double sp = p.windSide(), sq = q.windSide();
                if (sp != sq) {
                    rule = 10;
                    keep = sp < 0 ? a : c;
                } else {
                    // gleicher Bug: überlappt? (Abstand längs des Kurses kleiner als eine Länge)
                    double hr = Math.toRadians(p.headingDeg), fx = Math.sin(hr), fz = -Math.cos(hr);
                    double ahead = dx * fx + dz * fz;
                    if (Math.abs(ahead) < len) {
                        rule = 11;
                        double from = Math.toRadians(twf), wx = Math.sin(from), wz = -Math.cos(from);
                        keep = (p.x * wx + p.z * wz) > (q.x * wx + q.z * wz) ? a : c;   // weiter in Luv
                    } else {
                        rule = 12;
                        keep = ahead > 0 ? a : c;                                        // klar achteraus
                    }
                }
                right = keep == a ? c : a;
                if (time < keep.avoidUntil) continue;
                Sail.Boat k = keep.boat;
                double ws = k.windSide();
                switch (rule) {
                    case 10 -> { keep.avoidDelta = -ws * 30; keep.avoidEase = 0.15; }       // abfallen, hinter dem anderen durch
                    case 11 -> { keep.avoidDelta = ws * 12; keep.avoidEase = 0.3; }         // anluven
                    default -> { keep.avoidDelta = -ws * 10; keep.avoidEase = 0.5; }        // Fahrt weg, etwas nach Lee
                }
                keep.avoidUntil = time + 6;
                giveWays++;
                listener.onGiveWay(keep, right, rule, time);
            }
    }

    /** Rümpfe, die sich trotz allem berühren, auseinanderschieben (und Fahrt wegnehmen); gezählt als Berührung. */
    private void separate() {
        int n = entries.size();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                Sail.Boat p = entries.get(i).boat, q = entries.get(j).boat;
                double dx = q.x - p.x, dz = q.z - p.z, d = Math.hypot(dx, dz), min = 0.5 * (p.rig.loa() + q.rig.loa()) / 2;
                long key = (long) i * 4096 + j;
                if (d >= min || d < 1e-9) {
                    if (d >= min * 1.5) touching.remove(key);
                    continue;
                }
                double push = (min - d) / 2;
                p.x -= dx / d * push; p.z -= dz / d * push;
                q.x += dx / d * push; q.z += dz / d * push;
                p.speed *= 0.995;
                q.speed *= 0.995;
                if (touching.add(key)) contacts++;
            }
    }

    private final java.util.Set<Long> touching = new java.util.HashSet<>();

    static double[] vel(Sail.Boat b) {
        double h = Math.toRadians(b.headingDeg);
        return new double[]{Math.sin(h) * b.speed, -Math.cos(h) * b.speed};
    }

    static double bearing(double x0, double z0, double x1, double z1) { return (Math.toDegrees(Math.atan2(x1 - x0, -(z1 - z0))) + 360) % 360; }

    /** Kurs so begrenzen, dass er nicht höher als {@code minTwa} am Wind liegt (auf dem nähergelegenen Bug). */
    static double clampTwa(double heading, double windFrom, double minTwa) {
        double d = ((heading - windFrom) % 360 + 540) % 360 - 180;
        if (Math.abs(d) >= minTwa) return heading;
        return (windFrom + (d >= 0 ? minTwa : -minTwa) + 360) % 360;
    }

    /** Die Reihenfolge: im Ziel nach Platz, dann die übrigen nach ihrem Fortschritt. */
    public List<Entry> ranking() {
        List<Entry> l = new ArrayList<>(entries);
        l.sort((a, b) -> Double.compare(progress(b), progress(a)));
        return l;
    }

    /** Fortschritt eines Boots auf der Bahn (Schläge mal Länge plus Weg im jetzigen Schlag; Ziel ganz vorn). */
    public double progress(Entry e) {
        if (e.leg == Leg.DONE) return 1e9 - e.place;
        double a = course.above(e.boat.x, e.boat.z);
        int legs = e.lap * 2 + (e.leg == Leg.RUN || e.leg == Leg.FINISH ? 1 : 0);
        double inLeg = e.leg == Leg.BEAT ? a : e.leg == Leg.PRESTART ? a - course.length : course.length - a;
        return legs * course.length + inLeg;
    }

    /** „Regatta J/70: 2. Kreuz · vorn Nr. 7 · 3 von 20 im Ziel · 14 Ausweichmanöver“ */
    public String describe() {
        if (entries.isEmpty()) return "Regatta: kein Feld";
        String cls = entries.get(0).boat.rig.name();
        double tg = toGun();
        if (Double.isNaN(tg)) return "Regatta " + cls + ": wartet auf das Startverfahren";
        if (tg > 0) return String.format(Locale.GERMAN, "Regatta %s, Lauf %d: Start in %d:%02d · %d Boote", cls, race, (int) tg / 60, (int) tg % 60, entries.size());
        Entry lead = ranking().get(0);
        return String.format(Locale.GERMAN, "Regatta %s, Lauf %d: %s · vorn Nr. %d (%s) · %d von %d im Ziel · %d Ausweichmanöver", cls, race,
                fmt(-tg), lead.number, lead.status(), finished, entries.size(), giveWays);
    }

    static String fmt(double s) { return String.format(Locale.GERMAN, "%d:%02d nach dem Start", (int) s / 60, (int) s % 60); }

    /** Zeit (s) seit der letzte im Ziel ist (NaN, solange keiner im Ziel ist). */
    public double sinceLastFinish() { return Double.isNaN(lastFinish[0]) ? Double.NaN : time - lastFinish[0]; }
}
