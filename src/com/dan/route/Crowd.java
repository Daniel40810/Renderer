package com.dan.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Ströme auf einem Netz (seit Timmendorf Phase 10): Gänger oder Fahrzeuge mit Zielen, die je Kante zählen, wie dicht es
 * ist, und danach gehen oder fahren. Fußgänger gehen nach Weidmann 1993 (die Dichte auf der Kante in Personen je m²,
 * jeder mit eigener freier Geschwindigkeit um 1,34 m/s, σ 0,26 m/s), Fahrzeuge nach Greenshields (Geschwindigkeit fällt
 * linear mit der Dichte bis zur Staudichte 1/7 m je Fahrstreifen), höchstens so schnell wie erlaubt. Wo es eng wird
 * (Seebrücke, Treppen), staut es sich von selbst.
 * <p>
 * Ziele ({@link Goal}) haben ein Gewicht, eine Verweildauer und einen Punkt, zu dem man dort schaut (Zuschauer).
 * Gewählt wird nach dem Gewicht und der Entfernung (Schwerkraftmodell: Gewicht / (1 + d / {@link #reach})); der Weg
 * kommt aus {@link Route#astar}. Gleicher Startwert, gleicher Lauf.
 * <p>
 * Der Kern kennt nur Java 21; die Figuren dazu zeichnet die App (in Timmendorf {@code com.dan.figure}).
 */
public final class Crowd {
    public enum Mode { WALK, DRIVE }

    /** Ein Ziel: Knoten, Gewicht, Verweildauer (s, von bis), Blickpunkt (x, z; NaN = frei), Name. */
    public record Goal(int node, double weight, double dwellMin, double dwellMax, double lookX, double lookZ, String name) {
        public Goal(int node, double weight, double dwellMin, double dwellMax, String name) { this(node, weight, dwellMin, dwellMax, Double.NaN, Double.NaN, name); }
    }

    /** Ein Gänger oder Fahrzeug. Lage und Richtung (Azimut von Nord, rad) liest die App jedes Bild. */
    public static final class Agent {
        public final int id;
        public double x, y, z, heading, speed, v0;
        /** Gehend oder fahrend unterwegs (false: verweilt am Ziel). */
        public boolean moving;
        /** Ziel (Index in {@link Crowd#goals}), seit wann es verweilt, Strecke seit dem Start (m). */
        public int goal = -1;
        public double dwellLeft, travelled;
        /** Seitlicher Versatz auf der Kante (m, rechts der Laufrichtung positiv). */
        public double lane;
        /** Kleiner Zufall je Agent (Größe, Kleidung, Fahrzeugart) 0..1. */
        public final double look;
        Route.Path path;
        int seg;
        double s;
        boolean leaving;
        double leaveMark;

        Agent(int id, double look) { this.id = id; this.look = look; }

        /** Die Kante, auf der er gerade ist (−1 beim Verweilen). */
        public int edge() { return moving && path != null && seg < path.edges().length ? path.edges()[seg] : -1; }
    }

    public final Route g;
    public final Mode mode;
    public final List<Goal> goals;
    public final List<Agent> agents = new ArrayList<>();
    /** Reichweite des Schwerkraftmodells (m): je weiter ein Ziel, desto seltener. */
    public double reach = 800;
    /** Staudichte der Fahrzeuge je Fahrstreifen (1/m). */
    public double jamDensity = 1.0 / 7;
    /** Wer zu viel ist, geht nach so vielen Metern an einer Kreuzung ab, wenn er sein Ziel nicht vorher erreicht (m). */
    public double leaveAfter = 150;
    /** Anteil der neuen Agenten, die schon unterwegs beginnen (über ihren Weg verteilt). */
    public double spreadShare = 0.7;
    /** Fußgänger: Kanten kürzer als das zählen für die Dichte so lang (m); die Netze haben Stützpunkte alle 10 m. */
    public double minLength = 10;
    /** Fußgänger: Breite, unter der eine Kante als so breit gilt (m). */
    public double minWidth = 1.5;
    public final long seed;
    private final Random rnd;
    private final int[] occ;
    private final List<Agent>[] on;
    private final List<Integer> touched = new ArrayList<>();
    /** Fußgänger: Fenster vor jedem, in dem die Dichte gezählt wird (m), wie in Zellen- und Kraftmodellen. */
    public double window = 4;
    private int nextId;
    private double time;
    private int arrivals, routed, failed;

    public Crowd(Route g, Mode mode, List<Goal> goals, long seed) {
        this.g = g;
        this.mode = mode;
        this.goals = List.copyOf(goals);
        this.weights = new double[this.goals.size()];
        for (int i = 0; i < weights.length; i++) weights[i] = this.goals.get(i).weight();
        this.seed = seed;
        this.rnd = new Random(seed);
        this.occ = new int[g.edgeCount()];
        @SuppressWarnings("unchecked") List<Agent>[] o = new List[g.edgeCount()];
        this.on = o;
    }

    // ------------------------------------------------------------------ Zahl

    /**
     * Auf n Agenten bringen: neue beginnen an einem zufälligen Ziel (verweilend, damit sie nicht alle gleichzeitig
     * losgehen), überzählige gehen, sobald sie ihr nächstes Ziel erreichen.
     */
    public void target(int n) {
        int alive = 0;
        for (Agent a : agents) if (!a.leaving) alive++;
        for (int i = agents.size() - 1; i >= 0 && alive > n; i--) {
            Agent a = agents.get(i);
            if (a.leaving) continue;
            a.leaving = true;
            a.leaveMark = a.travelled;
            alive--;
            if (!a.moving) { agents.remove(i); }
        }
        while (alive < n && !goals.isEmpty()) {
            Agent a = new Agent(nextId++, rnd.nextDouble());
            a.v0 = mode == Mode.WALK ? Math.max(0.6, g.v0 + 0.26 * rnd.nextGaussian()) : 1.0 + 0.08 * rnd.nextGaussian();
            a.goal = pick(-1, null);
            Route.Node p = g.node(goals.get(a.goal).node());
            a.x = p.x();
            a.y = p.y();
            a.z = p.z();
            a.heading = rnd.nextDouble() * 2 * Math.PI;
            a.dwellLeft = rnd.nextDouble() * dwell(goals.get(a.goal));
            a.lane = laneFor(a);
            // die meisten schon unterwegs, irgendwo auf ihrem Weg (sonst stünden alle an den Zielen dicht gedrängt)
            if (rnd.nextDouble() < spreadShare) {
                depart(a);
                if (a.moving) {
                    double f = rnd.nextDouble() * a.path.length();
                    int[] es = a.path.edges();
                    while (a.seg < es.length - 1 && f > g.edge(es[a.seg]).length()) { f -= g.edge(es[a.seg]).length(); a.seg++; }
                    a.s = Math.min(f, g.edge(es[a.seg]).length());
                    place(a, a.path.nodes(), es, 10);
                }
            }
            agents.add(a);
            alive++;
        }
    }

    public int count() { return agents.size(); }

    /** Seit Timmendorf Phase 12: die Gewichte der Ziele, veränderlich (Anfang = {@link Goal#weight()}). */
    private final double[] weights;

    /** Gewicht eines Ziels setzen (0 = wird nicht mehr gewählt; wer dort ist, bleibt bis zum Ende seiner Verweildauer). */
    public void weight(int goal, double w) { weights[goal] = Math.max(0, w); }

    public double weight(int goal) { return weights[goal]; }

    /**
     * Seit Timmendorf Phase 12: alle, die an einem der Ziele verweilen, brechen in höchstens maxDwell s auf (verteilt), etwa
     * nach dem Feuerwerk. Gibt die Zahl.
     */
    public int hurry(java.util.function.IntPredicate goal, double maxDwell) {
        int n = 0;
        for (Agent a : agents)
            if (!a.moving && a.goal >= 0 && goal.test(a.goal) && a.dwellLeft > maxDwell) { a.dwellLeft = rnd.nextDouble() * maxDwell; n++; }
        return n;
    }

    /** Wie viele gerade an einem der Ziele verweilen. */
    public int dwelling(java.util.function.IntPredicate goal) {
        int n = 0;
        for (Agent a : agents) if (!a.moving && a.goal >= 0 && goal.test(a.goal)) n++;
        return n;
    }

    private double dwell(Goal q) { return q.dwellMin() + rnd.nextDouble() * Math.max(0, q.dwellMax() - q.dwellMin()); }

    private double laneFor(Agent a) {
        if (mode == Mode.DRIVE) return 0;
        return (rnd.nextDouble() - 0.5) * 0.7;   // Anteil der halben Breite, mit der Kante skaliert
    }

    /** Ein neues Ziel nach dem Schwerkraftmodell (nicht das jetzige). */
    private int pick(int current, Route.Node from) {
        double sum = 0;
        double[] w = new double[goals.size()];
        for (int i = 0; i < w.length; i++) {
            if (i == current && goals.size() > 1) continue;
            double d = 0;
            if (from != null) {
                Route.Node p = g.node(goals.get(i).node());
                d = Math.hypot(p.x() - from.x(), p.z() - from.z());
            }
            w[i] = weights[i] / (1 + d / reach);
            sum += w[i];
        }
        double r = rnd.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) { r -= w[i]; if (r <= 0 && w[i] > 0) return i; }
        for (int i = w.length - 1; i >= 0; i--) if (w[i] > 0) return i;   // Rundung; seit Phase 12 nie ein Ziel mit Gewicht 0
        return Math.max(0, current);
    }

    // ------------------------------------------------------------------ Schritt

    /** Ein Schritt (s): Dichten zählen, gehen oder fahren, ankommen, verweilen, weiter. */
    public void step(double dt) {
        if (dt <= 0) return;
        time += dt;
        java.util.Arrays.fill(occ, 0);
        for (int e : touched) on[e].clear();
        touched.clear();
        for (Agent a : agents) {
            int e = a.edge();
            if (e < 0) continue;
            occ[e]++;
            if (on[e] == null) on[e] = new ArrayList<>();
            if (on[e].isEmpty()) touched.add(e);
            on[e].add(a);
        }
        for (int i = agents.size() - 1; i >= 0; i--) {
            Agent a = agents.get(i);
            if (!a.moving) {
                a.speed = 0;
                Goal q = goals.get(a.goal);
                if (!Double.isNaN(q.lookX())) a.heading = turn(a.heading, Math.atan2(q.lookX() - a.x, -(q.lookZ() - a.z)), dt);
                a.dwellLeft -= dt;
                if (a.dwellLeft > 0) continue;
                if (a.leaving) { agents.remove(i); continue; }
                depart(a);
                continue;
            }
            move(a, dt);
        }
    }

    private void depart(Agent a) {
        Route.Node from = g.node(goals.get(a.goal).node());
        for (int k = 0; k < 4; k++) {
            int next = pick(a.goal, from);
            Route.Path p = mode == Mode.WALK ? g.astar(goals.get(a.goal).node(), goals.get(next).node(), g.walking(), 1 / g.v0)
                    : g.astar(goals.get(a.goal).node(), goals.get(next).node(), Route.driving(), 1 / 36.0);
            routed++;
            if (p.found() && p.edges().length > 0) {
                a.path = p;
                a.seg = 0;
                a.s = 0;
                a.goal = next;
                a.moving = true;
                return;
            }
            failed++;
        }
        a.dwellLeft = 30 + rnd.nextDouble() * 60;   // kein Weg: noch etwas bleiben
    }

    private void move(Agent a, double dt) {
        int[] ns = a.path.nodes(), es = a.path.edges();
        double left = dt;
        while (left > 0 && a.moving) {
            int ei = es[a.seg];
            Route.Edge e = g.edge(ei);
            double v = speedOn(a, e, ei);
            a.speed = v;
            double room = e.length() - a.s, go = v * left;
            if (go < room) {
                a.s += go;
                a.travelled += go;
                left = 0;
            } else {
                a.travelled += room;
                left -= v > 1e-6 ? room / v : left;
                a.seg++;
                a.s = 0;
                if (a.leaving && a.travelled - a.leaveMark > leaveAfter) {   // biegt ab (ins Haus, zum Auto): weg im nächsten Schritt
                    a.seg = Math.min(a.seg, es.length - 1);
                    a.moving = false;
                    a.speed = 0;
                    a.dwellLeft = 0;
                    break;
                }
                if (a.seg >= es.length) {
                    a.moving = false;
                    a.speed = 0;
                    arrivals++;
                    a.dwellLeft = dwell(goals.get(a.goal));
                    if (weights[a.goal] <= 0) a.dwellLeft = Math.min(a.dwellLeft, rnd.nextDouble() * 60);   // seit Phase 12: zu spät gekommen
                    if (a.leaving) a.dwellLeft = 0;
                }
            }
        }
        place(a, ns, es, dt);
    }

    /** Geschwindigkeit auf der Kante nach der Dichte (m/s). */
    double speedOn(Agent a, Route.Edge e, int ei) {
        if (mode == Mode.WALK) {
            double rho = ahead(a, e, ei);
            double v = a.v0 * g.weidmann(rho) / g.v0;
            if (e.kind() == Route.Kind.STEPS) v *= 0.5;
            return Math.max(0.05, v);
        }
        double vmax = e.speed() > 0 ? e.speed() : 13.9;
        double k = aheadCars(a, e, ei);
        return Math.max(0.3, vmax * a.v0 * Math.max(0, 1 - k / jamDensity));
    }

    /**
     * Dichte vor einem Fußgänger (P/m²): wer in Laufrichtung im Fenster vor ihm geht (Gegenverkehr halb), auch auf der
     * nächsten Kante seines Wegs. So löst sich ein Stau von vorn auf, wie in Wirklichkeit.
     */
    double ahead(Agent a, Route.Edge e, int ei) {
        boolean fwd = a.path.nodes()[a.seg] == e.a();
        double n = count(ei, fwd, a.s, a.s + window, a);
        double over = a.s + window - e.length();
        if (over > 0 && a.seg + 1 < a.path.edges().length) {
            int nx = a.path.edges()[a.seg + 1];
            Route.Edge en = g.edge(nx);
            n += count(nx, a.path.nodes()[a.seg + 1] == en.a(), -1e-9, over, null);
        }
        return n / (window * Math.max(minWidth, e.width()));
    }

    /** Fahrzeuge je m im eigenen Fahrstreifen vor einem Wagen (Fenster {@link #driveWindow}, Gegenverkehr zählt nicht). */
    double aheadCars(Agent a, Route.Edge e, int ei) {
        double n = 0, from = a.s, left = driveWindow;
        int seg = a.seg;
        Route.Edge cur = e;
        int cei = ei;
        while (left > 0 && seg < a.path.edges().length) {
            boolean fwd = a.path.nodes()[seg] == cur.a();
            double to = from + left;
            n += countSame(cei, fwd, from, to, a);
            left -= cur.length() - from;
            seg++;
            if (seg >= a.path.edges().length) break;
            cei = a.path.edges()[seg];
            cur = g.edge(cei);
            from = -1e-9;
        }
        return n / driveWindow;
    }

    /** Fahrzeuge: Fenster nach vorn (m). */
    public double driveWindow = 60;

    private double countSame(int ei, boolean fwd, double from, double to, Agent self) {
        List<Agent> l = on[ei];
        if (l == null || l.isEmpty()) return 0;
        Route.Edge e = g.edge(ei);
        double c = 0;
        for (Agent b : l) {
            if (b == self) continue;
            boolean bf = b.path.nodes()[b.seg] == e.a();
            if (bf != fwd && !e.oneway()) continue;
            double pos = bf == fwd ? b.s : e.length() - b.s;
            if (pos > from && pos <= to) c++;
        }
        return c;
    }

    private double count(int ei, boolean fwd, double from, double to, Agent self) {
        List<Agent> l = on[ei];
        if (l == null || l.isEmpty()) return 0;
        Route.Edge e = g.edge(ei);
        double c = 0;
        for (Agent b : l) {
            if (b == self) continue;
            boolean bf = b.path.nodes()[b.seg] == e.a();
            double pos = bf == fwd ? b.s : e.length() - b.s;
            if (pos > from && pos <= to) c += bf == fwd ? 1 : 0.5;
        }
        return c;
    }

    private void place(Agent a, int[] ns, int[] es, double dt) {
        int seg = Math.min(a.seg, es.length - 1);
        Route.Node p = g.node(ns[seg]), q = g.node(ns[seg + 1]);
        Route.Edge e = g.edge(es[seg]);
        double t = a.moving ? Math.min(1, a.s / Math.max(1e-6, e.length())) : 1;
        double dx = q.x() - p.x(), dz = q.z() - p.z(), l = Math.max(1e-6, Math.hypot(dx, dz));
        double rx = -dz / l, rz = dx / l;   // rechts der Laufrichtung (x Ost, z Süd)
        double off = mode == Mode.DRIVE ? (e.oneway() ? 0 : Math.min(1.75, Math.max(1.0, e.width() / 4))) : a.lane * Math.max(minWidth, e.width()) / 2;
        a.x = p.x() + dx * t + rx * off;
        a.y = p.y() + (q.y() - p.y()) * t;
        a.z = p.z() + dz * t + rz * off;
        double want = Math.atan2(dx, -dz);
        a.heading = turn(a.heading, want, dt);
    }

    private static double turn(double h, double want, double dt) {
        double d = Math.IEEEremainder(want - h, 2 * Math.PI);
        double max = 4 * dt;
        return h + Math.max(-max, Math.min(max, d));
    }

    // ------------------------------------------------------------------ Abfragen

    /** Dichte auf einer Kante: Fußgänger je m², Fahrzeuge je m und Fahrstreifen. */
    public double density(int edge) {
        Route.Edge e = g.edge(edge);
        if (mode == Mode.WALK) return occ[edge] / (Math.max(minLength, e.length()) * Math.max(minWidth, e.width()));
        return occ[edge] / (Math.max(1, e.length()) * (e.oneway() ? 1 : 2));
    }

    /** Wie viele unterwegs sind und wie viele davon im Stau (langsamer als 30 % ihrer freien Geschwindigkeit). */
    public int[] moving() {
        int m = 0, jam = 0;
        for (Agent a : agents) {
            if (!a.moving) continue;
            m++;
            double free = mode == Mode.WALK ? a.v0 : 13.9 * a.v0;
            if (a.speed < 0.3 * free) jam++;
        }
        return new int[]{m, jam};
    }

    public double time() { return time; }
    public int arrivals() { return arrivals; }

    /** Agenten im Umkreis r um (x, z). */
    public int near(double x, double z, double r) {
        int n = 0;
        for (Agent a : agents) if (Math.hypot(a.x - x, a.z - z) < r) n++;
        return n;
    }

    /** „Fußgänger: 820 (610 unterwegs, 12 im Stau), 1 340 Ankünfte, Wege 2 210 (3 ohne)“ */
    public String describe() {
        int[] m = moving();
        return String.format(Locale.GERMAN, "%s: %,d (%,d unterwegs, %d im Stau), %,d Ankünfte, Wege %,d (%d ohne)", mode == Mode.WALK ? "Fußgänger" : "Fahrzeuge",
                agents.size(), m[0], m[1], arrivals, routed, failed);
    }
}
