package com.dan.rail;

import java.util.Locale;

/**
 * Ein Gleis nach seiner Achse (neu für Brocken, F10 A): eine Folge von Punkten {x, y, z} in der Szene (x Ost, y oben,
 * z Süd), gleichmäßig nachgetastet. Gibt die Lage an jeder Stelle s (m ab Anfang): Ort, Richtung, Steigung und
 * Krümmung. Das Package kennt keine Strecke: Achse und Höhen kommen von außen (am Brocken aus OpenStreetMap und dem
 * Gelände, F11 A).
 * <p>
 * Seit Brocken Phase 12: Gradiente aus dem Gelände mit Grenze der Steigung ({@link #profile}), Bahnhöfe
 * ({@link Station}), Überhöhung im Bogen ({@link #cant}), Umläufe um einen Punkt ({@link #turnsAround}), Bahnübergänge
 * ({@link #crossings}); das Formen des Geländes macht {@link Bed}, die Achse aus Flächen {@link Corridor}.
 */
public final class Track {
    /** Spurweiten (m): Meterspur (Harzer Schmalspurbahnen) und Normalspur. */
    public static final double METERSPUR = 1.000, NORMALSPUR = 1.435;

    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7): Abstand der Stützpunkte (m). */
    public enum Quality {
        LOW(10), MEDIUM(5), HIGH(2.5), ULTRA(1);
        public final double step;
        Quality(double step) { this.step = step; }
    }

    /** Die Lage an einer Stelle: Ort, Richtung (Grad von Nord), Steigung (dy/ds), Krümmung (1/m, + nach rechts). */
    public record Pose(double s, double x, double y, double z, double heading, double gradient, double curvature) {
        /** Halbmesser des Bogens (m), unendlich auf der Geraden. */
        public double radius() { return Math.abs(curvature) < 1e-9 ? Double.POSITIVE_INFINITY : 1 / Math.abs(curvature); }
    }

    public final String name;
    public final double gauge;
    /** Startwert des Zufalls (Lage der Schwellen, ab Phase 12 Schotter und Verschleiß): gleicher seed, gleiches Gleis. */
    public long seed = 1;
    /** Abstand der Schwellen (m) [G: 0,65 m bei der Meterspur]. */
    public double schwellenAbstand = 0.65;
    private final double[] px, py, pz, ps;

    /** Ein Gleis aus der Achse {x, y, z}…, nachgetastet im Abstand der Stufe. */
    public Track(String name, double[][] axis, double gauge, Quality q) {
        if (axis.length < 2) throw new IllegalArgumentException("rail: Gleis " + name + " hat zu wenige Punkte");
        this.name = name;
        this.gauge = gauge;
        double[] cum = new double[axis.length];
        for (int i = 1; i < axis.length; i++) cum[i] = cum[i - 1] + Math.hypot(axis[i][0] - axis[i - 1][0], axis[i][2] - axis[i - 1][2]);
        double len = cum[cum.length - 1];
        int n = Math.max(2, (int) Math.ceil(len / q.step) + 1);
        px = new double[n];
        py = new double[n];
        pz = new double[n];
        ps = new double[n];
        int j = 0;
        for (int i = 0; i < n; i++) {
            double s = len * i / (n - 1);
            while (j < axis.length - 2 && cum[j + 1] < s) j++;
            double seg = Math.max(1e-9, cum[j + 1] - cum[j]), t = Math.max(0, Math.min(1, (s - cum[j]) / seg));
            px[i] = axis[j][0] + (axis[j + 1][0] - axis[j][0]) * t;
            py[i] = axis[j][1] + (axis[j + 1][1] - axis[j][1]) * t;
            pz[i] = axis[j][2] + (axis[j + 1][2] - axis[j][2]) * t;
            ps[i] = s;
        }
    }

    /** Länge in der Ebene (m). */
    public double length() { return ps[ps.length - 1]; }

    /** Zahl der Stützpunkte. */
    public int points() { return ps.length; }

    /** Die Lage an der Stelle s (m ab Anfang), auf das Gleis begrenzt. */
    public Pose at(double s) {
        s = Math.max(0, Math.min(length(), s));
        int n = ps.length;
        double f = s / length() * (n - 1);
        int i = Math.min(n - 2, (int) Math.floor(f));
        double t = f - i;
        double x = px[i] + (px[i + 1] - px[i]) * t, y = py[i] + (py[i + 1] - py[i]) * t, z = pz[i] + (pz[i + 1] - pz[i]) * t;
        double ds = ps[i + 1] - ps[i];
        double hd = heading(i);
        double grad = (py[i + 1] - py[i]) / Math.max(1e-9, ds);
        // Krümmung über ±20 m gemittelt (die Achse ist ein Streckenzug, an seinen Ecken springt die Richtung)
        int w = Math.max(1, (int) Math.round(20 / Math.max(1e-6, ds)));
        int a = Math.max(0, i - w), b = Math.min(n - 2, i + w);
        double dh = ((heading(b) - heading(a) + 540) % 360) - 180;
        double curv = b > a ? Math.toRadians(dh) / (ps[b] - ps[a]) : 0;
        return new Pose(s, x, y, z, hd, grad, curv);
    }

    private double heading(int i) {
        double h = Math.toDegrees(Math.atan2(px[i + 1] - px[i], -(pz[i + 1] - pz[i])));
        return h < 0 ? h + 360 : h;
    }

    // ------------------------------------------------------------------ seit Brocken Phase 12

    /**
     * Ein Bahnhof: Name, Stelle der Mitte (m), Länge (m); dort liegt das Gleis fast eben. Seit Brocken Phase 14 mit der
     * Seite des Bahnsteigs (+1 rechts von wachsendem s, −1 links, 0 ohne Bahnsteig, etwa ein Kreuzungsbahnhof).
     */
    public record Station(String name, double s, double length, int side) {
        public Station(String name, double s, double length) { this(name, s, length, 1); }

        public double s0() { return s - length / 2; }
        public double s1() { return s + length / 2; }
    }

    /** Die Bahnhöfe dieses Gleises (die App setzt sie, {@link #profile} hält sie eben). */
    public final java.util.List<Station> stations = new java.util.ArrayList<>();
    /** Entwurfsgeschwindigkeit (km/h) für die Überhöhung, größte Überhöhung (m) [G: Schmalspur 30 km/h, 70 mm]. */
    public double designSpeed = 30, maxCant = 0.07;

    /**
     * Eine Gradiente zu den Geländehöhen ground (gleichmäßig im Abstand ds), deren Steigung nirgends über maxGrade liegt,
     * in den Bahnhöfen ([s0, s1] in m) höchstens stationGrade. Gerechnet aus den beiden Hüllen mit dieser Grenze: der
     * höchsten unter dem Gelände (nur Einschnitt) und der tiefsten über ihm (nur Damm); die Gradiente liegt zwischen
     * ihnen beim Anteil fill (0 = nur Einschnitt, 0,5 = so wenig größte Abweichung wie möglich), danach über smoothM m
     * geglättet (Ausrundungen) und noch einmal begrenzt. Rückgabe: Höhe der Schienenoberkante je Stelle.
     */
    public static double[] profile(double[] ground, double ds, double maxGrade, double smoothM, double[][] stations, double stationGrade, double fill) {
        int n = ground.length;
        double[] lim = new double[n];
        for (int i = 0; i < n; i++) {
            lim[i] = maxGrade * ds;
            for (double[] st : stations) if (i * ds >= st[0] && i * ds <= st[1]) lim[i] = stationGrade * ds;
        }
        double[] up = ground.clone(), lo = ground.clone();
        for (int i = 1; i < n; i++) { up[i] = Math.min(up[i], up[i - 1] + lim[i]); lo[i] = Math.max(lo[i], lo[i - 1] - lim[i]); }
        for (int i = n - 2; i >= 0; i--) { up[i] = Math.min(up[i], up[i + 1] + lim[i + 1]); lo[i] = Math.max(lo[i], lo[i + 1] - lim[i + 1]); }
        double[] p = new double[n];
        for (int i = 0; i < n; i++) p[i] = up[i] + (lo[i] - up[i]) * fill;
        p = boxSmooth(p, (int) Math.round(smoothM / 2 / ds));
        for (int i = 1; i < n; i++) p[i] = Math.max(p[i - 1] - lim[i], Math.min(p[i - 1] + lim[i], p[i]));
        for (int i = n - 2; i >= 0; i--) p[i] = Math.max(p[i + 1] - lim[i + 1], Math.min(p[i + 1] + lim[i + 1], p[i]));
        return p;
    }

    static double[] boxSmooth(double[] v, int r) {
        if (r <= 0) return v.clone();
        int n = v.length;
        double[] c = new double[n + 1], o = new double[n];
        for (int i = 0; i < n; i++) c[i + 1] = c[i] + v[i];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - r), b = Math.min(n - 1, i + r);
            o[i] = (c[b + 1] - c[a]) / (b - a + 1);
        }
        return o;
    }

    /**
     * Überhöhung (m) an der Stelle s: das Gleichgewicht für die Entwurfsgeschwindigkeit (u = (Spur + 0,067 m) · v² / (g·R)),
     * zu 60 % ausgeführt (Fehlbetrag für langsame Züge [G]), höchstens maxCant; Vorzeichen nach der Seite des Bogens.
     */
    public double cant(double s) {
        Pose p = at(s);
        double v = designSpeed / 3.6, r = p.radius();
        if (Double.isInfinite(r)) return 0;
        double u = Math.min(maxCant, 0.6 * (gauge + 0.067) * v * v / (9.81 * r));
        return Math.signum(p.curvature()) * u;
    }

    /** Wie oft das Gleis zwischen s0 und s1 um den Punkt (cx, cz) läuft (Winkel um den Punkt / 360°, + rechts herum von oben). */
    public double turnsAround(double cx, double cz, double s0, double s1) {
        double sum = 0;
        Pose a = at(s0);
        double prev = Math.atan2(a.x() - cx, -(a.z() - cz));
        for (double s = s0 + 2; s <= s1; s += 2) {
            Pose b = at(s);
            double ang = Math.atan2(b.x() - cx, -(b.z() - cz)), d = ang - prev;
            if (d > Math.PI) d -= 2 * Math.PI;
            if (d < -Math.PI) d += 2 * Math.PI;
            sum += d;
            prev = ang;
        }
        return Math.toDegrees(sum) / 360;
    }

    /**
     * Wo das Gleis eine Fläche quert (etwa eine Straße, Ringe {x0, z0, x1, z1, …}): je Querung {s0, s1} (m), wo die
     * Achse in der Fläche liegt (Abtastung alle 0,5 m; kürzer als 0,6 m zählt nicht).
     */
    public java.util.List<double[]> crossings(java.util.List<double[][]> polygons) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (double[][] rings : polygons) {
            double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (double[] r : rings) for (int k = 0; k + 1 < r.length; k += 2) { box[0] = Math.min(box[0], r[k]); box[1] = Math.min(box[1], r[k + 1]); box[2] = Math.max(box[2], r[k]); box[3] = Math.max(box[3], r[k + 1]); }
            double in0 = Double.NaN;
            for (double s = 0; s <= length(); s += 0.5) {
                Pose p = at(s);
                boolean in = p.x() >= box[0] && p.x() <= box[2] && p.z() >= box[1] && p.z() <= box[3] && inside(rings, p.x(), p.z());
                if (in && Double.isNaN(in0)) in0 = s;
                if (!in && !Double.isNaN(in0)) { if (s - in0 > 0.6) out.add(new double[]{in0, s}); in0 = Double.NaN; }
            }
        }
        out.sort((u, v) -> Double.compare(u[0], v[0]));
        return out;
    }

    static boolean inside(double[][] rings, double x, double z) {
        boolean in = false;
        for (double[] r : rings) {
            int n = r.length / 2;
            for (int a = 0, b = n - 1; a < n; b = a++)
                if ((r[a * 2 + 1] > z) != (r[b * 2 + 1] > z) && x < r[a * 2] + (z - r[a * 2 + 1]) / (r[b * 2 + 1] - r[a * 2 + 1]) * (r[b * 2] - r[a * 2])) in = !in;
        }
        return in;
    }

    /** Die Station, in der die Stelle s liegt, oder null. */
    /**
     * Seit Brocken Phase 13: ein Gleis neben diesem von s0 bis s1, um offset m versetzt (+ rechts von wachsendem s), an
     * beiden Enden über taper m weich angeschlossen (Weichen; etwa ein Ausweichgleis in einem Kreuzungsbahnhof). Die
     * Höhe folgt diesem Gleis; nachgetastet alle 2 m.
     */
    public Track parallel(String name, double s0, double s1, double offset, double taper) {
        int n = Math.max(2, (int) Math.ceil((s1 - s0) / 2) + 1);
        double[][] ax = new double[n][];
        for (int i = 0; i < n; i++) {
            double s = s0 + (s1 - s0) * i / (n - 1), o = offset(s, s0, s1, offset, taper);
            Pose p = at(s);
            double h = Math.toRadians(p.heading());
            ax[i] = new double[]{p.x() + Math.cos(h) * o, p.y(), p.z() + Math.sin(h) * o};
        }
        Track t = new Track(name, ax, gauge, Quality.HIGH);
        t.seed = seed + 7;
        t.designSpeed = 15;
        return t;
    }

    /** Der Versatz eines {@link #parallel} gelegten Gleises an der Stelle s dieses Gleises (m; 0 außerhalb). */
    public static double offset(double s, double s0, double s1, double offset, double taper) {
        if (s <= s0 || s >= s1) return 0;
        double u = Math.min(1, Math.min(s - s0, s1 - s) / Math.max(1e-6, taper));
        return offset * u * u * (3 - 2 * u);
    }

    public Station stationAt(double s) {
        for (Station st : stations) if (s >= st.s0() && s <= st.s1()) return st;
        return null;
    }

    /** Größte Steigung über eine Strecke von {@code base} m (Betrag, dy/ds). */
    public double maxGradient(double base) {
        double m = 0;
        for (double s = 0; s + base <= length(); s += base / 4) m = Math.max(m, Math.abs(at(s + base).y() - at(s).y()) / base);
        return m;
    }

    /** Zahl der Schwellen auf dem ganzen Gleis. */
    public int schwellen() { return (int) Math.floor(length() / schwellenAbstand) + 1; }

    /** Stelle der Schwelle i (m), mit einem kleinen, aus seed reproduzierbaren Versatz von bis zu ±3 cm. */
    public double schwelle(int i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        double r = (h >>> 11) * 0x1.0p-53;
        return Math.max(0, Math.min(length(), i * schwellenAbstand + (r - 0.5) * 0.06));
    }

    /** Wie oft sich die Richtung über das ganze Gleis gedreht hat (Windungen, + rechts herum). */
    public double windungen() {
        double sum = 0;
        for (int i = 1; i < ps.length - 1; i++) sum += ((heading(i) - heading(i - 1) + 540) % 360) - 180;
        return sum / 360;
    }

    /**
     * Eine Spirale als Probe: um den Mittelpunkt (cx, cz), Halbmesser von r0 auf r1, so viele Windungen, Höhe von y0
     * auf y1, rechts herum (im Uhrzeigersinn von oben), Beginn im Süden.
     */
    public static double[][] spirale(double cx, double cz, double r0, double r1, double windungen, double y0, double y1, int n) {
        double[][] p = new double[n][];
        for (int i = 0; i < n; i++) {
            double u = i / (n - 1.0), a = u * windungen * 2 * Math.PI, r = r0 + (r1 - r0) * u;
            // Beginn im Süden (+z), im Uhrzeigersinn: Süden → Westen → Norden → Osten
            p[i] = new double[]{cx - r * Math.sin(a), y0 + (y1 - y0) * u, cz + r * Math.cos(a)};
        }
        return p;
    }

    /** „Spirale · 3,4 km · Meterspur · größte Steigung 3,1 % · 1,5 Windungen“ */
    public String describe() {
        return String.format(Locale.GERMAN, "%s · %.1f km · %s · größte Steigung %.1f %% · %.1f Windungen", name, length() / 1000,
                gauge == METERSPUR ? "Meterspur" : gauge == NORMALSPUR ? "Normalspur" : String.format(Locale.GERMAN, "Spur %.3f m", gauge),
                maxGradient(100) * 100, Math.abs(windungen()));
    }
}
