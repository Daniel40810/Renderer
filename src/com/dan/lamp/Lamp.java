package com.dan.lamp;

/**
 * Eine Leuchte (seit Timmendorf Phase 11, F18 A): Ort, Lampenart, Lichtstrom, Lichtstärkeverteilung, Dämmerungsschalter,
 * Halbnachtschaltung und Anlauf. Lichtstärke in jede Richtung (cd), Beleuchtungsstärke an jedem Ort (lx).
 * <p>
 * Raum der Szenen: x Ost, y oben, z Süd, Meter. {@code facing} ist die Richtung zur Straße als Azimut von Nord (Grad).
 * Die Verteilungen sind gestaltet [G], aber nach den IES-Typen geformt: {@link Distribution#STREET} wirft das Licht
 * längs der Straße (Typ II, voll abgeschirmt), {@link Distribution#ROUND} rundum mit etwas Licht nach oben (Typ V,
 * Pilz- und Laternenleuchten), {@link Distribution#DOWN} nach unten (Poller, Einbauleuchten), {@link Distribution#GLOBE}
 * gleich nach allen Seiten (Kugelleuchte). Jede ist so normiert, dass der Lichtstrom stimmt.
 */
public final class Lamp {
    /** Lampenarten mit Lichtausbeute (lm/W) und Anlaufzeit (s, bis rund 63 %) [G nach Herstellerangaben]. */
    public enum Type {
        LED_3000K(130, 0.1), LED_4000K(140, 0.1), AMBER_LED(90, 0.1), HPS(110, 90), LPS(180, 300), MERCURY(55, 120),
        HALOGEN(20, 0.1), INCANDESCENT(12, 0.1), GAS(1.5, 3);
        public final double efficacy, warmup;
        Type(double e, double w) { efficacy = e; warmup = w; }
        public double[] rgb() { return Spectra.rgb(this); }
    }

    /** Lichtstärkeverteilungen. */
    public enum Distribution { STREET, ROUND, DOWN, GLOBE }

    public double x, y, z;
    public double facing;
    public Type type;
    public Distribution distribution;
    /** Lichtstrom der Lampe (lm) und Leuchtenbetriebswirkungsgrad. */
    public double lumen, lor = 0.8;
    /** Leuchtende Fläche (m²), für die Leuchtdichte des Kopfes. */
    public double area = 0.04;
    /** Dämmerungsschalter: an unter onLux, aus über offLux [G, übliche Einstellung 30–50 lx]. */
    public double onLux = 35, offLux = 70;
    /** Halbnachtschaltung: von halfFrom bis halfTo (Uhr) auf halfLevel [G]; halfLevel 1 = aus. */
    public double halfFrom = 23, halfTo = 5, halfLevel = 1;
    /** Kennung (für Fensterhäuser und Prüfungen), Ausfall (dunkel) und Flackern (Gas, kaputte Röhre). */
    public int id;
    public boolean broken;
    /**
     * Seit Timmendorf Phase 12: eigene Lichtfarbe (lineares sRGB, Leuchtdichte 1) statt der Farbe der Lampenart, etwa für
     * fremde Lichter, die als Leuchte mitgerechnet werden (das Licht einer Feuerwerksbombe); null = nach {@link #type}.
     */
    public double[] color;
    public double flicker;
    private boolean on;
    private double level, time;

    public Lamp(double x, double y, double z, Type type, Distribution d, double lumen) {
        this.x = x; this.y = y; this.z = z; this.type = type; distribution = d; this.lumen = lumen;
    }

    public Lamp facing(double azimuthDeg) { facing = azimuthDeg; return this; }

    public boolean on() { return on; }

    /** Lichtfarbe (lineares sRGB, Leuchtdichte 1): {@link #color} oder die der Lampenart. */
    public double[] rgb() { return color != null ? color : type.rgb(); }

    /**
     * Seit Timmendorf Phase 12: ein fremdes Punktlicht als Leuchte (gleich nach allen Seiten, sofort an), etwa das Licht einer
     * Feuerwerksbombe: Ort, Lichtstärke (cd) und Farbe (lineares sRGB, wird auf Leuchtdichte 1 gebracht).
     */
    public static Lamp point(double x, double y, double z, double cd, double r, double g, double b) {
        Lamp l = new Lamp(x, y, z, Type.LED_4000K, Distribution.GLOBE, 0);
        l.lor = 1;
        l.lumen = cd / norm(Distribution.GLOBE);
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        l.color = lum > 0 ? new double[]{r / lum, g / lum, b / lum} : new double[]{1, 1, 1};
        l.on = true;
        l.level = 1;
        l.area = 1;
        l.onLux = Double.MAX_VALUE;   // immer an
        l.offLux = Double.MAX_VALUE;
        return l;
    }
    public double level() { return level; }

    /** Schaltet nach Umgebungslicht (lx) und Uhrzeit (h), läuft an; sofort, wenn dt ≤ 0. */
    public void step(double dt, double ambientLux, double hour) {
        time += Math.max(0, dt);
        if (ambientLux < onLux) on = true;
        else if (ambientLux > offLux) on = false;
        double h = ((hour % 24) + 24) % 24;
        boolean half = halfFrom > halfTo ? h >= halfFrom || h < halfTo : h >= halfFrom && h < halfTo;
        double target = broken || !on ? 0 : half ? halfLevel : 1;
        if (dt <= 0 || target < level) level = target;             // aus geht sofort
        else level += (target - level) * (1 - Math.exp(-dt / type.warmup));
    }

    /** Helligkeit jetzt (0..1) mit Flackern. */
    public double output() {
        if (flicker <= 0) return level;
        double n = Math.sin(time * 37.1 + id) * Math.sin(time * 11.3 + id * 3) * Math.sin(time * 5.7);
        return level * (1 - flicker * (0.5 + 0.5 * n));
    }

    /** Leistung (W) bei voller Helligkeit. */
    public double watts() { return lumen / type.efficacy; }

    /** Lichtstärke (cd) in Richtung (dx, dy, dz) vom Leuchtenmittelpunkt (Einheitsvektor), mit Helligkeit. */
    public double candela(double dx, double dy, double dz) {
        double fx = Math.sin(Math.toRadians(facing)), fz = -Math.cos(Math.toRadians(facing));
        return output() * lumen * lor * norm(distribution) * shape(distribution, dx, dy, dz, fx, fz);
    }

    /** Lichtstärke bei voller Helligkeit (cd) – für Prüfungen und die größte Reichweite. */
    public double candelaFull(double dx, double dy, double dz) {
        double fx = Math.sin(Math.toRadians(facing)), fz = -Math.cos(Math.toRadians(facing));
        return lumen * lor * norm(distribution) * shape(distribution, dx, dy, dz, fx, fz);
    }

    /** Höchste Lichtstärke (cd) bei voller Helligkeit. */
    public double peakCandela() { return lumen * lor * norm(distribution) * PEAK[distribution.ordinal()]; }

    /** Beleuchtungsstärke (lx) am Ort p auf einer Fläche mit Normale n (0, 0, 0 = senkrecht zum Licht). */
    public double illuminance(double px, double py, double pz, double nx, double ny, double nz) {
        double dx = px - x, dy = py - y, dz = pz - z, d2 = dx * dx + dy * dy + dz * dz;
        if (d2 < 1e-4) return 0;
        double d = Math.sqrt(d2);
        dx /= d; dy /= d; dz /= d;
        double cos = nx == 0 && ny == 0 && nz == 0 ? 1 : -(dx * nx + dy * ny + dz * nz);
        if (cos <= 0) return 0;
        return candela(dx, dy, dz) * cos / d2;
    }

    /** Leuchtdichte des Leuchtenkopfs (cd/m²) zum Auge. */
    public double headLuminance(double ex, double ey, double ez) {
        double dx = ex - x, dy = ey - y, dz = ez - z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return d < 1e-6 ? 0 : candela(dx / d, dy / d, dz / d) / area;
    }

    /**
     * Form der Verteilung (ohne Maßstab) in Richtung d, Straße in Richtung f (waagerecht). θ ist der Winkel zum Nadir.
     * Dieselben Formeln stehen in {@code gl/lamp.glsl}.
     */
    public static double shape(Distribution dist, double dx, double dy, double dz, double fx, double fz) {
        double c = -dy;                                            // cos θ
        switch (dist) {
            case DOWN:
                return c <= 0 ? 0 : c * c;
            case GLOBE:
                return 1;
            case ROUND: {
                double s = Math.sqrt(Math.max(0, 1 - c * c));
                return c >= 0 ? 0.35 + 0.65 * s : 0.15 * (1 + c);          // unter dem Horizont zur Seite, oben 15 %
            }
            default: {
                if (c <= 0.0) return 0;
                double s = Math.sqrt(Math.max(0, 1 - c * c)), h = s > 1e-6 ? 1 / s : 0;
                double hx = dx * h, hz = dz * h;
                double along = Math.abs(hx * fz - hz * fx);                  // |cos| zur Straßenachse
                double toStreet = hx * fx + hz * fz;
                double cut = smooth(0.035, 0.17, c);                        // voll abgeschirmt, ab 80° weich zu
                double t = (Math.acos(Math.min(1, c)) - Math.toRadians(65)) / Math.toRadians(11);
                return (c + 3.0 * Math.exp(-0.5 * t * t) * along * along) * (1 + 0.35 * toStreet) * cut;
            }
        }
    }

    static double smooth(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static final double[] NORM = new double[Distribution.values().length], PEAK = new double[Distribution.values().length];

    static {
        for (Distribution d : Distribution.values()) {
            double sum = 0, peak = 0;
            int nt = 360, np = 360;
            for (int i = 0; i < nt; i++) {
                double th = Math.PI * (i + 0.5) / nt, st = Math.sin(th), ct = Math.cos(th);
                for (int j = 0; j < np; j++) {
                    double ph = 2 * Math.PI * (j + 0.5) / np;
                    double v = shape(d, st * Math.cos(ph), -ct, st * Math.sin(ph), 0, -1);
                    sum += v * st;
                    peak = Math.max(peak, v);
                }
            }
            sum *= (Math.PI / nt) * (2 * Math.PI / np);
            NORM[d.ordinal()] = 1 / sum;
            PEAK[d.ordinal()] = peak;
        }
    }

    /** Maßstab der Form: Lichtstärke je Lumen (cd/lm), so dass ∫ I dΩ = Lichtstrom. */
    public static double norm(Distribution d) { return NORM[d.ordinal()]; }
}
