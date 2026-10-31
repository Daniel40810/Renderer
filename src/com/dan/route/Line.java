package com.dan.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eine Linie mit Fahrplan (seit Timmendorf Phase 10): Bus oder Fähre fährt über ihre Halte, Takt, erste und letzte
 * Abfahrt, Halt je Haltestelle. Ohne Zustand: wo die Fahrzeuge sind, folgt allein aus der Uhrzeit ({@link #vehicles}),
 * darum sind Zeitraffer und Sprünge in der Zeit kein Problem. Der Weg zwischen den Halten ist der kürzeste im Netz.
 * Fahrzeiten mit der erlaubten Geschwindigkeit der Kanten (höchstens {@link #cruise}), am Anfang und Ende jedes
 * Abschnitts mit 1 m/s² beschleunigt und gebremst.
 */
public final class Line {
    /** Ein Fahrzeug zur Zeit t: Lage, Richtung (Azimut, rad), an einem Halt, Nummer der Abfahrt. */
    public record Vehicle(double x, double y, double z, double heading, boolean atStop, int run) { }

    public final String name;
    public final Route g;
    /** Halte als Knoten-Indizes, Takt (s), erste und letzte Abfahrt (s nach Mitternacht), Halt je Haltestelle (s). */
    public final int[] stops;
    public final double headway, first, last, dwell;
    /** Reisegeschwindigkeit (m/s), höchstens; Beschleunigung (m/s²). */
    public final double cruise, accel = 1.0;
    private final List<Leg> legs = new ArrayList<>();
    private final double duration;
    private final boolean ok;

    /** Ein Abschnitt zwischen zwei Halten: Weg, Abfahrt nach Start (s), Fahrzeit (s), Länge (m). */
    private record Leg(Route.Path path, double depart, double time, double length, double[] cum) { }

    public Line(String name, Route g, int[] stops, double cruise, double dwell, double headway, double first, double last, Route.Cost cost) {
        this.name = name;
        this.g = g;
        this.stops = stops.clone();
        this.cruise = cruise;
        this.dwell = dwell;
        this.headway = headway;
        this.first = first;
        this.last = last;
        double t = 0;
        boolean good = stops.length >= 2;
        for (int i = 0; i + 1 < stops.length && good; i++) {
            Route.Path p = g.astar(stops[i], stops[i + 1], cost, 0);
            if (!p.found()) { good = false; break; }
            double[] cum = new double[p.edges().length + 1];
            for (int k = 0; k < p.edges().length; k++) cum[k + 1] = cum[k] + g.edge(p.edges()[k]).length();
            double tt = travel(p.length());
            legs.add(new Leg(p, t, tt, p.length(), cum));
            t += tt + dwell;
        }
        ok = good;
        duration = good ? t - dwell : 0;
    }

    /** Fahrzeit (s) über eine Strecke L mit Anfahren und Bremsen. */
    public double travel(double L) {
        double tAcc = cruise / accel, dAcc = 0.5 * accel * tAcc * tAcc;
        if (2 * dAcc >= L) return 2 * Math.sqrt(L / accel);
        return 2 * tAcc + (L - 2 * dAcc) / cruise;
    }

    /** Strecke nach t Sekunden in einem Abschnitt der Länge L. */
    public double distance(double t, double L) {
        double T = travel(L);
        t = Math.max(0, Math.min(T, t));
        double tAcc = cruise / accel, dAcc = 0.5 * accel * tAcc * tAcc;
        if (2 * dAcc >= L) {
            double h = T / 2;
            return t <= h ? 0.5 * accel * t * t : L - 0.5 * accel * (T - t) * (T - t);
        }
        if (t < tAcc) return 0.5 * accel * t * t;
        if (t > T - tAcc) return L - 0.5 * accel * (T - t) * (T - t);
        return dAcc + cruise * (t - tAcc);
    }

    public boolean ok() { return ok; }
    /** Dauer einer Fahrt vom ersten bis zum letzten Halt (s). */
    public double duration() { return duration; }
    /** Länge (m). */
    public double length() { double l = 0; for (Leg x : legs) l += x.length(); return l; }
    /** Planmäßige Ankunft am Halt i nach der Abfahrt am ersten Halt (s). */
    public double atStop(int i) { return i == 0 ? 0 : legs.get(i - 1).depart() + legs.get(i - 1).time(); }

    /** Nächste Abfahrt am Halt i ab der Uhrzeit t (s nach Mitternacht); NaN, wenn heute keine mehr fährt. */
    public double nextDeparture(int i, double t) {
        double off = atStop(i) + (i == 0 ? 0 : dwell);
        double k = Math.ceil((t - off - first) / headway - 1e-9);
        double d = first + Math.max(0, k) * headway;
        return d > last + 1e-9 ? Double.NaN : d + off;
    }

    /** Alle Fahrzeuge der Linie zur Uhrzeit t (s nach Mitternacht). */
    public List<Vehicle> vehicles(double t) {
        List<Vehicle> v = new ArrayList<>();
        if (!ok) return v;
        int k0 = (int) Math.max(0, Math.floor((t - duration - first) / headway)), k1 = (int) Math.floor((Math.min(t, last) - first) / headway);
        for (int k = k0; k <= k1; k++) {
            double tau = t - (first + k * headway);
            if (tau < 0 || tau > duration) continue;
            v.add(at(tau, k));
        }
        return v;
    }

    /** Wo eine Fahrt tau Sekunden nach ihrer Abfahrt ist. */
    public Vehicle at(double tau, int run) {
        for (int i = 0; i < legs.size(); i++) {
            Leg l = legs.get(i);
            if (tau < l.depart()) {                       // steht noch am Halt i (vor dem Abschnitt)
                return pos(legs.get(Math.max(0, i - 1)), i == 0 ? 0 : legs.get(i - 1).length(), true, run);
            }
            if (tau <= l.depart() + l.time()) return pos(l, distance(tau - l.depart(), l.length()), false, run);
        }
        Leg l = legs.get(legs.size() - 1);
        return pos(l, l.length(), true, run);
    }

    private Vehicle pos(Leg l, double s, boolean stop, int run) {
        int[] es = l.path().edges(), ns = l.path().nodes();
        int k = 0;
        while (k + 1 < l.cum().length - 1 && l.cum()[k + 1] < s) k++;
        Route.Node p = g.node(ns[k]), q = g.node(ns[k + 1]);
        double t = Math.max(0, Math.min(1, (s - l.cum()[k]) / Math.max(1e-6, g.edge(es[k]).length())));
        double dx = q.x() - p.x(), dz = q.z() - p.z();
        return new Vehicle(p.x() + dx * t, p.y() + (q.y() - p.y()) * t, p.z() + dz * t, Math.atan2(dx, -dz), stop, run);
    }

    /** „Linie 40: 12 Halte, 9,4 km, 21 min, alle 30 min von 6:00 bis 22:00“ */
    public String describe() {
        return String.format(Locale.GERMAN, "%s: %d Halte, %.1f km, %.0f min, alle %.0f min von %d:%02d bis %d:%02d%s", name, stops.length, length() / 1000, duration / 60, headway / 60,
                (int) first / 3600, (int) first % 3600 / 60, (int) last / 3600, (int) last % 3600 / 60, ok ? "" : " (kein Weg)");
    }
}
