package com.dan.wake;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Kielwasser und Bugwelle: das Kelvin-Muster hinter jedem Boot und Schiff, der Schaum im Kielwasser und der Schwell der
 * Fähren, der Minuten später am Strand aufläuft (Werkbuch Timmendorf, Abschnitte 6 und 7; Hülle seit Phase 1, Physik
 * seit Phase 8).
 * <p>
 * <b>Das Muster.</b> Ein Boot ist ein Druckpunkt am Bug und ein Sog gleicher Stärke am Heck (eine Bootslänge dahinter),
 * beide über die halbe Breite verschmiert. Jeder Punkt erzeugt im tiefen Wasser das Kelvin-Muster; dessen Wellen werden
 * mit der Methode der stationären Phase gerechnet (Lighthill 1978, Kap. 3.10): Für einen Punkt x hinter und y neben der
 * Quelle gibt es unter dem Winkel α = atan(y/x) bis arcsin(1/3) zwei Wellen, die quer laufenden (θ klein) und die
 * schrägen (θ groß), mit tan α = tan θ / (1 + 2 tan² θ) (geschlossen gelöst), Wellenzahl k = g/(V² cos² θ) und Phase
 * K₀ · (x cos θ + y sin θ) / cos² θ. Die Höhe nimmt mit 1/√r ab; an der Grenze des Keils, wo beide Wellen
 * zusammenfallen, mit r^(−1/3) (Airy), dahinter klingt sie ab. Kurze Wellen dämpft die Breite des Druckfelds
 * (exp(−(k·b)²/4)). Die Überlagerung von Bug und Heck lässt bei hoher Froude-Zahl die langen Wellen ausfallen; der
 * sichtbare Keil wird dann enger (Rabaud und Moisy 2013) – das ergibt sich hier von selbst.
 * <p>
 * <b>Kurven.</b> Das Muster hängt an der Spur: jede Quelle merkt sich, wo ihr Bug war; ein Punkt wird in Spurkoordinaten
 * (Weg hinter dem Bug, Abstand quer) umgerechnet. So folgt das Kielwasser einer Wende.
 * <p>
 * <b>Höhe.</b> Die Wellenhöhe skaliert mit dem Tiefgang und der Froude-Zahl: Amplitude in einer Bootslänge Abstand
 * 0,12 · T · Fr² / (0,08 + Fr²) [G, an Messungen hinter Fähren (0,5 m in 100 m bei 18 kn) und kleinen Booten
 * angepasst].
 * <p>
 * <b>Schaum</b>: ein Streifen hinter dem Heck, der breiter wird und mit dem Alter vergeht (Motorschiffe mit Schraubenwasser
 * dicht und lange, Segelboote nur bei Fahrt).
 * <p>
 * <b>Schwell</b>: der Rand des Keils großer Schiffe läuft weit; {@link #watch} meldet über {@link Arrival}, wann er eine
 * Linie (den Strand) erreicht.
 * <p>
 * Raum der Szenen: x Ost, y oben, z Süd, Meter; Kurs als Kompasskurs (0 Nord, 90 Ost). Der Kern kennt nur Java 21.
 */
public final class Wake {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7): Größe der Kacheln in {@code gl/}. */
    public enum Quality {
        LOW(256), MEDIUM(384), HIGH(512), ULTRA(768);
        public final int tile;
        Quality(int tile) { this.tile = tile; }
    }

    /** Halber Öffnungswinkel des Kelvin-Keils: arcsin(1/3) in Grad (19,47°), unabhängig von der Fahrt im tiefen Wasser. */
    public static final double KELVIN_DEG = Math.toDegrees(Math.asin(1.0 / 3.0));
    public static final double G = 9.81;
    /** Spurpunkte je Quelle für Kacheln und {@code gl/} (nach hinten immer weiter auseinander: bei Fähren gut 20 km). */
    public static final int UPLOAD = 96;
    /** tan des Kelvin-Winkels: 1/√8. */
    static final double TAN_K = 1 / Math.sqrt(8);

    /** Ereignis nach außen: der Schwell einer Quelle erreicht eine Linie (der Strand); Höhe als Amplitude (m). */
    @FunctionalInterface
    public interface Arrival { void arrive(Source s, double x, double z, double height, double t); }

    /** Eine Quelle: Name, Lage (Mitte), Kurs (Grad), Fahrt (m/s), Länge in der Wasserlinie, Breite, Tiefgang des Rumpfs (m). */
    public static final class Source {
        public final String name;
        public double x, z, headingDeg, speed, length, beam, draft;
        /** Motorschiff mit Schraube: dichter Schaum im Kielwasser. */
        public boolean motor;
        /** Fährt die Quelle selbst ihren Kurs ({@link #step}), wie in der Hülle? Sonst setzt der Aufrufer die Lage ({@link Wake#moveTo}). */
        public boolean autopilot = true;
        // Spur des Bugs: x, z, Weg (m), Zeit (s); neuester Punkt zuletzt
        double[] tx = new double[64], tz = new double[64], td = new double[64], tt = new double[64];
        int count;
        double odo, normFr = -1, normAmp, lastArrival = -1e9;
        boolean arrived;

        public Source(String name, double x, double z, double headingDeg, double speed, double length) {
            this.name = name; this.x = x; this.z = z; this.headingDeg = headingDeg; this.speed = speed; this.length = length;
            this.beam = Math.max(0.5, length / 3.2);
            this.draft = Math.max(0.12, 0.05 * length);
        }

        /** Froude-Zahl Fr = V / √(g·L). */
        public double froude() { return Wake.froude(speed, length); }

        /** Lage des Bugs (x, z). */
        public double[] bow() {
            double h = Math.toRadians(headingDeg);
            return new double[]{x + Math.sin(h) * length / 2, z - Math.cos(h) * length / 2};
        }

        /** Punkte der Spur (für Bilder und {@code gl/}). */
        public int trailPoints() { return count; }

        /** Wie lange die Spur zurückreicht (s): große Schiffe 50 min (Schwell bis an den Strand), Boote 3 min. */
        public double maxAge() { return length > 50 ? 3000 : 180; }

        /** Abstand der Spurpunkte (m). */
        double spacing() { return Math.max(0.3, Math.min(length / 4, 25)); }

        synchronized void record(double t) {
            double[] b = bow();
            if (count > 0) {
                double dx = b[0] - tx[count - 1], dz = b[1] - tz[count - 1];
                double d = Math.hypot(dx, dz);
                if (d < spacing()) return;
                odo += d;
            }
            if (count == tx.length) {
                int keep = count;
                // alte Punkte verwerfen (zu alt oder zu viele)
                int drop = 0;
                while (drop < count - 2 && (t - tt[drop] > maxAge() || count - drop > 4000)) drop++;
                if (drop > 0) {
                    System.arraycopy(tx, drop, tx, 0, count - drop);
                    System.arraycopy(tz, drop, tz, 0, count - drop);
                    System.arraycopy(td, drop, td, 0, count - drop);
                    System.arraycopy(tt, drop, tt, 0, count - drop);
                    count -= drop;
                }
                if (count == tx.length) {
                    tx = Arrays.copyOf(tx, keep * 2);
                    tz = Arrays.copyOf(tz, keep * 2);
                    td = Arrays.copyOf(td, keep * 2);
                    tt = Arrays.copyOf(tt, keep * 2);
                }
            }
            tx[count] = b[0];
            tz[count] = b[1];
            td[count] = odo;
            tt[count] = t;
            count++;
        }

        /** Spur löschen (etwa nach einem Sprung der Lage). */
        public synchronized void clearTrail() { count = 0; odo = 0; }
    }

    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Wassertiefe (m); unter Fr_h = V/√(g·h) ≈ 1 gibt es keinen Kelvin-Keil mehr (die Szene setzt die Tiefe der Bucht). */
    public double depth = 12;
    /** Stärke des Musters (1 = wie gemessen) und des Schaums [G]. */
    public double strength = 1, foamStrength = 1;
    public Arrival onArrival;
    private final List<Source> sources = new ArrayList<>();
    private double time, watchTimer;
    private double[][] watch = new double[0][];

    // ------------------------------------------------------------------ Presets (Punkt 5)

    /** Lübecker Bucht vor Timmendorf: mittlere Tiefe 12 m (GEBCO). */
    public static Wake luebeckerBucht() { Wake w = new Wake(); w.depth = 12; return w; }
    /** Hafenbecken (Niendorf) [G]. */
    public static Wake harbour() { Wake w = new Wake(); w.depth = 3.5; return w; }

    // ------------------------------------------------------------------ Quellen

    public Source add(String name, double x, double z, double headingDeg, double speed, double length) {
        Source s = new Source(name, x, z, headingDeg, speed, length);
        sources.add(s);
        return s;
    }

    /** Seit Phase 8: eine Quelle mit Breite und Tiefgang; {@code motor} für Schaum aus der Schraube. */
    public Source add(String name, double x, double z, double headingDeg, double speed, double length, double beam, double draft, boolean motor) {
        Source s = add(name, x, z, headingDeg, speed, length);
        s.beam = beam;
        s.draft = draft;
        s.motor = motor;
        return s;
    }

    public List<Source> sources() { return List.copyOf(sources); }

    public void remove(Source s) { sources.remove(s); }

    public void clear() { sources.clear(); }

    /** Lage einer Quelle setzen, die der Aufrufer fährt (seit Phase 8); schaltet den Autopiloten ab. */
    public void moveTo(Source s, double x, double z, double headingDeg, double speed) {
        s.autopilot = false;
        s.x = x;
        s.z = z;
        s.headingDeg = headingDeg;
        s.speed = speed;
    }

    /** Eine Linie (Punkte x, z), an der der Schwell großer Schiffe gemeldet wird ({@link #onArrival}). */
    public void watch(double[][] line) { watch = line == null ? new double[0][] : line.clone(); }

    /** Rechnet dt Sekunden weiter: Quellen mit Autopilot fahren ihren Kurs, alle schreiben ihre Spur; Schwell am Ufer. */
    public void step(double dt) {
        time += dt;
        for (Source s : sources) {
            if (s.autopilot) {
                double h = Math.toRadians(s.headingDeg);
                s.x += Math.sin(h) * s.speed * dt;
                s.z -= Math.cos(h) * s.speed * dt;
            }
            s.record(time);
        }
        watchTimer -= dt;
        if (watchTimer <= 0 && watch.length > 0 && onArrival != null) {
            watchTimer = 2;
            for (Source s : sources) {
                if (s.length < 50) continue;
                double best = 0, bx = 0, bz = 0;
                for (double[] p : watch) {
                    double e = envelope(s, p[0], p[1]);
                    if (e > best) { best = e; bx = p[0]; bz = p[1]; }
                }
                if (!s.arrived && best > 0.01) { s.arrived = true; s.lastArrival = time; onArrival.arrive(s, bx, bz, best, time); }
                else if (s.arrived && best < 0.003 && time - s.lastArrival > 60) s.arrived = false;
            }
        }
    }

    // ------------------------------------------------------------------ Grundgrößen

    public static double froude(double speed, double length) { return speed / Math.sqrt(G * Math.max(1e-6, length)); }

    /** Wellenlänge der Querwellen im tiefen Wasser: λ = 2πV²/g (m). */
    public static double transverseWavelength(double speed) { return 2 * Math.PI * speed * speed / G; }

    /** Tiefen-Froude-Zahl Fr_h = V / √(g·h); über 1 gibt es keinen Kelvin-Keil mehr (überkritisch). */
    public static double depthFroude(double speed, double depth) { return speed / Math.sqrt(G * Math.max(1e-6, depth)); }

    /**
     * Halber Winkel des sichtbaren Keils (Grad) nach Rabaud und Moisy 2013: Kelvin bis Fr ≈ 0,5, darüber
     * arctan(1 / (√(8π) · Fr)). Zum Vergleich; das Muster selbst rechnet den Keil aus Bug und Heck.
     */
    public static double wakeAngleDeg(double froude) {
        double rm = Math.toDegrees(Math.atan(1 / (Math.sqrt(8 * Math.PI) * Math.max(1e-6, froude))));
        return Math.min(KELVIN_DEG, rm);
    }

    /** Ob der Punkt (x, z) im Keil der Quelle liegt (hinter ihr, innerhalb des Winkels; geradeaus gedacht). */
    public static boolean inWedge(Source s, double x, double z) {
        double h = Math.toRadians(s.headingDeg), fx = Math.sin(h), fz = -Math.cos(h);
        double dx = x - s.x, dz = z - s.z, back = -(dx * fx + dz * fz);
        if (back <= 0) return false;
        double side = Math.abs(dx * -fz + dz * fx);
        return Math.toDegrees(Math.atan2(side, back)) <= wakeAngleDeg(s.froude());
    }

    // ------------------------------------------------------------------ Kelvin

    /**
     * Die beiden Wellen eines Druckpunkts (Einheitsstärke) im Punkt x hinter und y neben ihm: je Welle {Amplitude, Phase}
     * (out[0..1] quer laufend, out[2..3] schräg). K₀ = g/V², b halbe Breite des Druckfelds, kMax größte Wellenzahl
     * (darüber blendet sie aus, gegen Flimmern im Raster).
     */
    public static void branches(double x, double y, double k0, double b, double kMax, double[] out) {
        out[0] = out[1] = out[2] = out[3] = 0;
        if (x <= 1e-6) return;
        double ay = Math.abs(y), r = Math.hypot(x, ay), ta = ay / x, outside = 1;
        if (ta > TAN_K) {
            double over = Math.atan(ta) - Math.atan(TAN_K);
            outside = Math.exp(-3 * over * Math.pow(k0 * r, 2.0 / 3));
            if (outside < 1e-4) return;
            ta = TAN_K;
            double c = 1 / Math.sqrt(1 + ta * ta);
            x = r * c;
            ay = r * ta * c;
        }
        double disc = Math.sqrt(Math.max(0, 1 - 8 * ta * ta));
        for (int br = 0; br < 2; br++) {
            double t;
            if (br == 0) t = ta < 1e-9 ? 0 : (1 - disc) / (4 * ta);
            else { if (ta < 1e-9) continue; t = (1 + disc) / (4 * ta); }
            double c = 1 / Math.sqrt(1 + t * t), s = t * c, S = 1 + t * t;
            double k = k0 * S;
            if (k > kMax * 1.6) continue;
            // Phase K₀ · (x cos θ − |y| sin θ) / cos² θ: stationär genau für tan α = tan θ / (1 + 2 tan² θ)
            double u = x * c - ay * s, u1 = -x * s - ay * c;
            double S1 = 2 * S * t, S2 = 4 * S * t * t + 2 * S * S, S3 = 4 * S1 * t * t + 8 * S * S * t + 4 * S * S1;
            double F = u * S, F2 = -u * S + 2 * u1 * S1 + u * S2, F3 = -u1 * S - 3 * u * S1 + 3 * u1 * S2 + u * S3;
            double amp = Math.min(Math.sqrt(2 * Math.PI / (k0 * Math.abs(F2) + 1e-12)), 1.547 * Math.pow(k0 * Math.abs(F3) / 6 + 1e-12, -1.0 / 3));
            double w = Math.exp(-(k * b) * (k * b) / 4);
            if (k > kMax) w *= Math.max(0, 1 - (k - kMax) / (0.6 * kMax));
            out[br * 2] = S * S * w * amp * outside;
            out[br * 2 + 1] = k0 * F + (F2 > 0 ? Math.PI / 4 : -Math.PI / 4);
        }
    }

    /** Höhe des Musters einer Quelle (Einheitsstärke) x hinter dem Bug und y quer: Bug minus Heck. */
    public static double shape(double x, double y, double k0, double len, double b, double kMax) {
        double[] p = new double[4], q = new double[4];
        branches(x, y, k0, b, kMax, p);
        branches(x - len, y, k0, b, kMax, q);
        return p[0] * Math.cos(p[1]) + p[2] * Math.cos(p[3]) - q[0] * Math.cos(q[1]) - q[2] * Math.cos(q[3]);
    }

    /** Hüllkurve des Musters (ohne Phase des Orts, mit der Überlagerung von Bug und Heck). */
    public static double shapeEnvelope(double x, double y, double k0, double len, double b, double kMax) {
        double[] p = new double[4], q = new double[4];
        branches(x, y, k0, b, kMax, p);
        branches(x - len, y, k0, b, kMax, q);
        double e = 0;
        for (int br = 0; br < 2; br++) {
            double re = p[br * 2] * Math.cos(p[br * 2 + 1]) - q[br * 2] * Math.cos(q[br * 2 + 1]);
            double im = p[br * 2] * Math.sin(p[br * 2 + 1]) - q[br * 2] * Math.sin(q[br * 2 + 1]);
            e += Math.hypot(re, im);
        }
        return e;
    }

    /** Amplitude in einer Bootslänge Abstand quer (m) [G]: 0,12 · T · Fr² / (0,08 + Fr²). */
    public static double targetAmplitude(Source s) {
        double fr = s.froude();
        return 0.12 * s.draft * fr * fr / (0.08 + fr * fr);
    }

    /** Faktor vom Einheitsmuster zur Höhe in m (je Quelle gemerkt, neu bei 3 % anderer Froude-Zahl). */
    double amplitude(Source s) {
        double fr = s.froude();
        if (s.speed < 0.15) return 0;
        if (s.normFr < 0 || Math.abs(fr - s.normFr) > 0.03 * s.normFr) {
            double k0 = G / (s.speed * s.speed), mx = 0;
            for (int i = 1; i <= 64; i++) {
                double x = s.length * (0.2 + 4.0 * i / 64);
                mx = Math.max(mx, shapeEnvelope(x, s.length, k0, s.length, s.beam / 2, 1e9));
            }
            s.normFr = fr;
            s.normAmp = mx < 1e-12 ? 0 : targetAmplitude(s) / mx;
        }
        return s.normAmp * strength;
    }

    /**
     * Spurkoordinaten eines Punkts: out = {Weg hinter dem Bug (m), Abstand quer (m), Alter (s)}. Gibt false, wenn der
     * Punkt vor dem Bug oder weit neben der Spur liegt.
     */
    public boolean trailCoords(Source s, double px, double pz, double[] out) {
        synchronized (s) { return trailCoordsLocked(s, px, pz, out); }
    }

    private boolean trailCoordsLocked(Source s, double px, double pz, double[] out) {
        double[] b = s.bow();
        double best = Double.MAX_VALUE;
        boolean found = false;
        double ax = b[0], az = b[1], ad = 0, at = time;
        for (int i = s.count - 1; i >= -1; i--) {
            double bx, bz, bd, bt;
            if (i < 0) break;
            bx = s.tx[i];
            bz = s.tz[i];
            bd = ad + Math.hypot(bx - ax, bz - az);
            bt = s.tt[i];
            double sx = bx - ax, sz = bz - az, l2 = sx * sx + sz * sz;
            double u = l2 < 1e-12 ? 0 : ((px - ax) * sx + (pz - az) * sz) / l2;
            if (u >= 0 && u <= 1) {
                double qx = ax + sx * u - px, qz = az + sz * u - pz, d2 = qx * qx + qz * qz;
                if (d2 < best) {
                    best = d2;
                    out[0] = ad + Math.sqrt(l2) * u;
                    out[1] = Math.sqrt(d2);
                    out[2] = time - (at + (bt - at) * u);
                    found = true;
                }
            }
            ax = bx; az = bz; ad = bd; at = bt;
        }
        if (!found) {
            // hinter dem ältesten Punkt: gerade weiter gedacht; vor dem Bug: nichts
            double h = Math.toRadians(s.headingDeg), fx = Math.sin(h), fz = -Math.cos(h);
            double back = -((px - b[0]) * fx + (pz - b[1]) * fz);
            if (s.count == 0 && back > 0) {
                out[0] = back;
                out[1] = Math.abs((px - b[0]) * -fz + (pz - b[1]) * fx);
                out[2] = back / Math.max(0.1, s.speed);
                return true;
            }
            return false;
        }
        return out[0] > 1e-6;
    }

    private final double[] tc = new double[3];

    /** Höhe einer Quelle im Punkt (m). */
    public synchronized double height(Source s, double x, double z) { return height(s, x, z, 1e9); }

    synchronized double height(Source s, double x, double z, double kMax) {
        if (s.speed < 0.15 || !trailCoords(s, x, z, tc)) return 0;
        double a = amplitude(s);
        if (a == 0) return 0;
        double k0 = G / (s.speed * s.speed);
        return a * shape(tc[0], tc[1], k0, s.length, s.beam / 2, kMax) * damping(s);
    }

    /** Hüllkurve einer Quelle im Punkt (m): wie hoch die Wellen dort werden. */
    public synchronized double envelope(Source s, double x, double z) {
        if (s.speed < 0.15 || !trailCoords(s, x, z, tc)) return 0;
        double a = amplitude(s);
        double k0 = G / (s.speed * s.speed);
        return a * shapeEnvelope(tc[0], tc[1], k0, s.length, s.beam / 2, 1e9) * damping(s);
    }

    /** Im flachen Wasser (Fr_h gegen 1) bricht der Keil zusammen [G: weich ab Fr_h 0,7]. */
    double damping(Source s) {
        double fh = depthFroude(s.speed, depth);
        return fh < 0.7 ? 1 : Math.max(0.2, 1 - (fh - 0.7) * 1.5);
    }

    /** Schaum einer Quelle im Punkt (0..1): Streifen hinter dem Heck, breiter werdend, mit dem Alter vergehend [G]. */
    public synchronized double foam(Source s, double x, double z) {
        if (s.speed < 0.3 || !trailCoords(s, x, z, tc)) return 0;
        double along = tc[0] - s.length * 0.8, q = tc[1], age = tc[2];
        if (along < -0.2 * s.length) return 0;
        double w = 0.55 * s.beam + 0.12 * Math.sqrt(Math.max(0, along) * s.beam);
        double f0 = s.motor ? 0.9 : 0.35 * smooth(0.5, 3.0, s.speed), tau = s.motor ? 150 : 25;
        double across = 1 - smooth(0.5 * w, w, q);
        return Math.min(1, foamStrength * f0 * Math.exp(-Math.max(0, age) / tau) * across);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Höhe des Wassers aus allen Kielwassern (m). */
    public double heightAt(double x, double z, double t) {
        double h = 0;
        for (Source s : sources) h += height(s, x, z);
        return h;
    }

    /** Schaum aus allen Kielwassern (0..1). */
    public double foamAt(double x, double z) {
        double f = 0;
        for (Source s : sources) f = Math.max(f, foam(s, x, z));
        return f;
    }

    /** Hüllkurve aus allen Quellen (m). */
    public double envelopeAt(double x, double z) {
        double e = 0;
        for (Source s : sources) e += envelope(s, x, z);
        return e;
    }

    // ------------------------------------------------------------------ Kacheln

    /**
     * Die Spur einer Quelle so ausgedünnt, wie sie {@code gl/} bekommt: höchstens {@code max} Punkte, nahe dem Bug dicht,
     * weiter hinten weiter auseinander. Je Punkt x, z, Weg hinter dem Bug, Alter (s); der erste ist der Bug selbst.
     */
    public double[][] uploadTrail(Source s, int max) {
        synchronized (s) { return uploadTrailLocked(s, max); }
    }

    private double[][] uploadTrailLocked(Source s, int max) {
        double[] b = s.bow();
        List<double[]> out = new ArrayList<>();
        out.add(new double[]{b[0], b[1], 0, 0});
        double ax = b[0], az = b[1], ad = 0, next = s.spacing();
        int n = s.count;
        for (int i = n - 1; i >= 0 && out.size() < max; i--) {
            ad += Math.hypot(s.tx[i] - ax, s.tz[i] - az);
            ax = s.tx[i];
            az = s.tz[i];
            if (ad >= next || i == 0) {
                out.add(new double[]{ax, az, ad, time - s.tt[i]});
                // Abstand wächst nach hinten: alle 16 Punkte doppelt so weit
                next = ad + s.spacing() * Math.pow(2, Math.floor(out.size() / 16.0));
            }
        }
        return out.toArray(new double[0][]);
    }

    /** Alle Größen einer Quelle für {@code gl/}: Bug x, z, K₀, Länge; Amplitude, halbe Breite, Schaum, Schaumdauer; Beam, Reichweite. */
    public double[] uploadParams(Source s) {
        double[] b = s.bow();
        double v = Math.max(0.15, s.speed);
        double fr = s.froude();
        double reach = s.length > 50 ? 1e9 : Math.max(60, 12 * s.length + 40 * v);
        return new double[]{b[0], b[1], G / (v * v), s.length,
                s.speed < 0.15 ? 0 : amplitude(s) * damping(s), s.beam / 2, foamStrength * (s.motor ? 0.9 : 0.35 * smooth(0.5, 3.0, s.speed)), s.motor ? 150 : 25,
                s.beam, reach, fr, 0};
    }

    /**
     * Eine Kachel auf dem Prozessor, wie {@code gl/wake.comp} sie rechnet: n × n Zellen über (x0, z0) bis (x0 + size,
     * z0 + size), je Zelle Höhe (m) und Schaum (0..1) in {@code out} (2 · n · n). Mit der ausgedünnten Spur
     * ({@link #uploadTrail}, {@link #UPLOAD} Punkte) und der größten Wellenzahl nach der Zellgröße (3 Zellen je Welle).
     */
    public void tile(double x0, double z0, double size, int n, float[] out) {
        double cell = size / n, kMax = 2 * Math.PI / (3 * cell);
        List<double[][]> trails = new ArrayList<>();
        List<double[]> params = new ArrayList<>();
        for (Source s : sources) { trails.add(uploadTrail(s, UPLOAD)); params.add(uploadParams(s)); }
        double[] o = new double[3];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double px = x0 + (i + 0.5) * cell, pz = z0 + (j + 0.5) * cell, h = 0, f = 0;
                for (int k = 0; k < trails.size(); k++) {
                    double[] p = params.get(k);
                    if (p[4] == 0 && p[6] == 0) continue;
                    if (Math.hypot(px - p[0], pz - p[1]) > p[9] + 2 * p[3]) continue;
                    if (!trailCoordsUpload(trails.get(k), px, pz, o)) continue;
                    h += p[4] * shape(o[0], o[1], p[2], p[3], p[5], kMax);
                    f = Math.max(f, foamFrom(p, o));
                }
                out[2 * (j * n + i)] = (float) h;
                out[2 * (j * n + i) + 1] = (float) f;
            }
    }

    /** Schaum aus den Größen von {@link #uploadParams} und Spurkoordinaten (wie im Shader). */
    static double foamFrom(double[] p, double[] o) {
        double along = o[0] - p[3] * 0.8, q = o[1], age = o[2];
        if (along < -0.2 * p[3] || p[6] <= 0) return 0;
        double w = 0.55 * p[8] + 0.12 * Math.sqrt(Math.max(0, along) * p[8]);
        return Math.min(1, p[6] * Math.exp(-Math.max(0, age) / p[7]) * (1 - smooth(0.5 * w, w, q)));
    }

    /** Spurkoordinaten über der ausgedünnten Spur (wie im Shader). */
    public static boolean trailCoordsUpload(double[][] tr, double px, double pz, double[] out) {
        double best = Double.MAX_VALUE;
        boolean found = false;
        for (int i = 0; i + 1 < tr.length; i++) {
            double[] a = tr[i], b = tr[i + 1];
            double sx = b[0] - a[0], sz = b[1] - a[1], l2 = sx * sx + sz * sz;
            double u = l2 < 1e-12 ? 0 : ((px - a[0]) * sx + (pz - a[1]) * sz) / l2;
            if (u < 0 || u > 1) continue;
            double qx = a[0] + sx * u - px, qz = a[1] + sz * u - pz, d2 = qx * qx + qz * qz;
            if (d2 < best) {
                best = d2;
                out[0] = a[2] + (b[2] - a[2]) * u;
                out[1] = Math.sqrt(d2);
                out[2] = a[3] + (b[3] - a[3]) * u;
                found = true;
            }
        }
        return found && out[0] > 1e-6;
    }

    /** Wie viel los ist (0..1), für Anzeigen. */
    public double strength() {
        double v = 0;
        for (Source s : sources) v = Math.max(v, s.speed);
        return Math.min(1, v / 10);
    }

    public double time() { return time; }
}
