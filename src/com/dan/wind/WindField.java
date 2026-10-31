package com.dan.wind;

/**
 * Wind über Gelände (neu für Brocken, F6 A). Ein Windfeld über einem Höhenfeld: der Wind der Lage (Stärke, Richtung,
 * Schichtung) kommt von außen, das Gelände über die eigene kleine Schnittstelle {@link Ground}. Das Package kennt
 * keinen Berg und keine App.
 * <p>
 * Seit Brocken Phase 1: das Profil über dem Grund nach Monin-Obukhov (logarithmisch bei neutraler Schichtung, steiler
 * bei stabiler, flacher bei labiler), die Turbulenz aus Rauigkeit und Höhe, Böen als Muster, das mit dem Wind zieht
 * (Taylor), das geländefolgende Gitter mit zur Höhe hin gestreckten Schichten (F7 A) und die Abtastung für eine
 * 3D-Textur.
 * <p>
 * <b>Seit Brocken Phase 4 der Kern:</b> {@link #solve} rechnet das massenkonsistente Feld ({@link MassSolver}): das
 * Startfeld aus Profil, Rauigkeit, Windschatten hinter Kanten (Lee), Hangwind (am Tag hinauf, nachts hinab) und
 * Kaltluft in Senken wird so angepasst, dass keine Luft entsteht oder verschwindet (wie MATHEW nach Sherman 1978 und
 * WindNinja). Über Kuppen wird der Wind schneller, in Sätteln gebündelt; die Schichtung entscheidet, ob die Luft über den
 * Berg geht oder um ihn herum. Mit gelöstem Feld ({@link #solution}) geben {@link #windAt}, {@link #gustAt},
 * {@link #coldPoolAt} und {@link #sample} das Feld; ohne bleibt die Näherung der Hülle (Phase 1) als Rückfall.
 * <p>
 * Andere Packages nehmen das Feld nicht als Klasse an, sondern über ihre eigene kleine Schnittstelle (F8 A); die App
 * verdrahtet sie mit einem Methodenverweis, etwa {@code field::windAt}.
 */
public final class WindField {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7): Zelle des Gitters und Zahl der Schichten. */
    public enum Quality {
        LOW(100, 16), MEDIUM(50, 30), HIGH(50, 30), ULTRA(25, 40);
        /** Zelle waagerecht (m) und Schichten bis {@link WindField#top}. */
        public final double cell;
        public final int layers;
        Quality(double cell, int layers) { this.cell = cell; this.layers = layers; }
    }

    /** Schichtung der Luft (bestimmt, ob die Luft über den Berg geht oder um ihn herum, ab Phase 4). */
    public enum Stability {
        LABIL(-200), NEUTRAL(Double.POSITIVE_INFINITY), STABIL(300), INVERSION(80);
        /** Obukhov-Länge in m (negativ labil, unendlich neutral) [G]. */
        public final double obukhov;
        Stability(double obukhov) { this.obukhov = obukhov; }
    }

    /** Höhe des Grunds über dem Bezug (m) am Ort (x, z). Szene: x Ost, z Süd. */
    @FunctionalInterface
    public interface Ground { double height(double x, double z); }

    /** Rauigkeitslänge z0 (m) am Ort: rund 0,0002 Schnee, 0,03 Moor und Heide, 0,1 Fels, 0,5 bis 1 Wald. */
    @FunctionalInterface
    public interface Roughness { double z0(double x, double z); }

    /** Dicke der ersten Schicht über dem höchsten Grund (m). */
    public static final double FIRST_LAYER = 10;
    /** Konstante von Kármán. */
    public static final double KARMAN = 0.4;
    /** Mitte jeder Beaufort-Stufe in m/s (WMO), eigene Kopie. */
    static final double[] BFT_MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    /** Startwert des Zufalls (Böen): gleicher seed, gleiches Feld. */
    public long seed = 1;
    /** Wind der Lage: Stärke in m/s in {@link #refHeight} über Grund, Richtung, aus der er kommt (Grad). */
    public double speed = 8, fromDeg = 270, refHeight = 10;
    /** Schichtung und Höhe der Grenzschicht (m über Grund): darüber bleibt der Wind gleich. */
    public Stability stability = Stability.NEUTRAL;
    public double top = 1500;
    /** Rauigkeit, wenn keine {@link #roughness} gesetzt ist (m). */
    public double z0 = 0.3;
    /** Böen: Länge der Muster (m) und wie stark sie über der Turbulenz liegen (Spitzenfaktor). */
    public double gustScale = 120, peakFactor = 3.0;
    public Quality quality = Quality.MEDIUM;
    public Ground ground = (x, z) -> 0;
    /** Rauigkeit je Ort; null = überall {@link #z0}. */
    public Roughness roughness;

    // ------------------------------------------------------------------ Kern (seit Brocken Phase 4)
    /**
     * Erwärmung des Bodens −1 … 1: am Tag mit Sonne positiv (Hangwind hinauf, labil), nachts mit klarem Himmel negativ
     * (Hangwind hinab, Kaltluft in Senken). 0 = keiner.
     */
    public double heating;
    /** Hangwind im Strahl (m/s, + hinauf); NaN = aus {@link #heating} und dem Wind der Lage (bei starkem Wind schwach) [G]. */
    public double slopeFlowOverride = Double.NaN;
    /** Verhältnis der Anpassung senkrecht gegen waagerecht (b/a); NaN = nach der Schichtung (labil 1,2, neutral 0,7, stabil 0,35, Inversion 0,12) [G]. */
    public double verticalRatio = Double.NaN;
    /** Wie weit luvwärts eine Kante Windschatten wirft (m), nach Winstral und Marks (2002) [G]. */
    public double shelterReach = 400;
    /** Umkreis, gegen dessen Mittel eine Senke als Kaltluftsee zählt (m) [G]. */
    public double coldRadius = 1500;
    /** Löser: höchste Zahl der Schritte und Rest, bei dem er aufhört (relativ). */
    public int maxIterations = 600;
    public double tolerance = 1e-5;
    /** Das gelöste Feld oder null (dann die Näherung der Hülle). */
    public volatile WindSolution solution;
    /** Hangwind, mit dem das letzte Feld gerechnet wurde (m/s), für die Beschreibung. */
    double slopeFlow;

    // ------------------------------------------------------------------ Presets (Punkt 5)
    public static WindField calm() { return beaufort(1); }
    public static WindField breeze() { return beaufort(3); }
    /** Westlage über dem Mittelgebirge: Bft 6 aus 260°, neutral [G]. */
    public static WindField westlage() {
        WindField w = beaufort(6);
        w.fromDeg = 260;
        return w;
    }
    /** Hochdruck im Herbst: schwacher Wind aus Ost, Inversion (das Nebelmeer, Phase 6) [G]. */
    public static WindField inversion() {
        WindField w = beaufort(2);
        w.fromDeg = 90;
        w.stability = Stability.INVERSION;
        return w;
    }
    public static WindField sturm() { return beaufort(10); }

    /** Preset nach Beaufort 0..12 in {@link #refHeight}: Wind in der Mitte der Stufe, aus West, neutral. */
    public static WindField beaufort(int bft) {
        WindField w = new WindField();
        w.speed = BFT_MID[Math.max(0, Math.min(12, bft))];
        return w;
    }

    /** Beaufort-Stufe einer Geschwindigkeit (m/s). */
    public static int beaufortOf(double v) {
        double[] up = {0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7};
        for (int i = 0; i < up.length; i++) if (v < up[i]) return i;
        return 12;
    }

    // ------------------------------------------------------------------ Profil

    double z0At(double x, double z) { return Math.max(1e-4, roughness != null ? roughness.z0(x, z) : z0); }

    /** Korrektur des Profils nach Monin-Obukhov (Businger-Dyer) für ζ = h/L. */
    static double psiM(double zeta) {
        if (zeta >= 0) return -5 * Math.min(zeta, 1.0);   // stabil, über ζ = 1 gedeckelt
        double x = Math.pow(1 - 16 * zeta, 0.25);
        return 2 * Math.log((1 + x) / 2) + Math.log((1 + x * x) / 2) - 2 * Math.atan(x) + Math.PI / 2;
    }

    /** Ungestörte Windgeschwindigkeit (m/s) in h m über Grund bei Rauigkeit z0, so dass in refHeight {@link #speed} gilt. */
    public double profile(double h, double z0) {
        double L = stability.obukhov;
        double hh = Math.max(0, Math.min(top, h));
        double f = Math.log((hh + z0) / z0) - psiM(hh / L), fr = Math.log((refHeight + z0) / z0) - psiM(refHeight / L);
        return Math.max(0, speed * f / Math.max(1e-6, fr));
    }

    /** Einheitsvektor, wohin der Wind weht (x Ost, z Süd). */
    public double[] toVector() {
        double a = Math.toRadians(fromDeg + 180);
        return new double[]{Math.sin(a), 0, -Math.cos(a)};
    }

    // ------------------------------------------------------------------ Kern lösen (seit Brocken Phase 4)

    /** Hangwind im Strahl (m/s) aus {@link #heating}: am Tag bis 1,2 m/s hinauf, nachts bis 2,5 m/s hinab, bei Wind schwächer [G]. */
    public double slopeFlow() {
        if (!Double.isNaN(slopeFlowOverride)) return slopeFlowOverride;
        double h = Math.max(-1, Math.min(1, heating));
        return (h >= 0 ? 1.2 * h : 2.5 * h) * Math.exp(-speed / 6);
    }

    /** Stärke der Kaltluft 0..1: nachts bei stabiler Luft und Inversion, in einer Inversion auch am Tag zum Teil [G]. */
    public double coldStrength() {
        double night = Math.max(0, Math.min(1, -heating));
        return switch (stability) {
            case STABIL -> 0.6 * night;
            case INVERSION -> Math.max(0.4, night);
            default -> 0;
        } * Math.exp(-speed / 8);
    }

    /**
     * Rechnet das massenkonsistente Feld über dem Rechteck (xMin, zMin, xMax, zMax) mit den Feldern dieses Objekts
     * (Lage, Schichtung, Erwärmung, Gelände, Rauigkeit, Stufe), setzt {@link #solution} und gibt es zurück. Teuer
     * (Sekunden auf dem vollen Gitter): in einem eigenen Faden aufrufen und das Ergebnis tauschen.
     */
    public WindSolution solve(double xMin, double zMin, double xMax, double zMax) {
        slopeFlow = slopeFlow();
        WindField copy = copy();
        copy.slopeFlowOverride = slopeFlow;
        WindSolution s = MassSolver.solve(copy, xMin, zMin, xMax, zMax);
        solution = s;
        return s;
    }

    /** Eine Kopie aller Eingänge (ohne Lösung), etwa um im Hintergrund zu rechnen, während das Original weiterläuft. */
    public WindField copy() {
        WindField c = new WindField();
        c.seed = seed;
        c.speed = speed;
        c.fromDeg = fromDeg;
        c.refHeight = refHeight;
        c.stability = stability;
        c.top = top;
        c.z0 = z0;
        c.gustScale = gustScale;
        c.peakFactor = peakFactor;
        c.quality = quality;
        c.ground = ground;
        c.roughness = roughness;
        c.heating = heating;
        c.slopeFlowOverride = slopeFlowOverride;
        c.verticalRatio = verticalRatio;
        c.shelterReach = shelterReach;
        c.coldRadius = coldRadius;
        c.maxIterations = maxIterations;
        c.tolerance = tolerance;
        return c;
    }

    /**
     * Wind (m/s) am Ort (x, y, z) zur Zeit t als {u Ost, v oben, w Süd}. Mit gelöstem Feld ({@link #solution}) dessen
     * Wert; sonst die Näherung der Hülle: das Profil über dem Grund, am Hang entlang dem Gelände geneigt (der Anteil
     * nimmt über 300 m [G] ab), ohne Massenerhaltung.
     */
    public double[] windAt(double x, double y, double z, double t) {
        WindSolution so = solution;
        if (so != null && so.contains(x, z)) return so.at(x, y, z);
        return hullAt(x, y, z);
    }

    /** Die Näherung der Hülle (Phase 1). */
    public double[] hullAt(double x, double y, double z) {
        double g = ground.height(x, z), h = y - g;
        if (h <= 0) return new double[3];
        double[] d = toVector();
        double u = profile(h, z0At(x, z));
        double e = 25, gx = (ground.height(x + e, z) - ground.height(x - e, z)) / (2 * e), gz = (ground.height(x, z + e) - ground.height(x, z - e)) / (2 * e);
        double up = (d[0] * gx + d[2] * gz) * Math.exp(-h / 300);
        return new double[]{d[0] * u, u * up, d[2] * u};
    }

    /** Turbulenzgrad I = σ/U (0..1) nach Rauigkeit und Höhe, I ≈ 1/ln(h/z0), nach der Schichtung gewichtet. */
    public double turbulenceAt(double x, double y, double z) {
        double h = Math.max(1, y - ground.height(x, z)), r = z0At(x, z);
        double i = 1 / Math.log(Math.max(1.5, (h + r) / r));
        double s = switch (stability) { case LABIL -> 1.2; case NEUTRAL -> 1.0; case STABIL -> 0.7; case INVERSION -> 0.5; };
        // seit Phase 4: im Lee hinter Kanten böiger (abgelöste Strömung, Wirbel) [G]
        WindSolution so = solution;
        if (so != null && so.contains(x, z)) s *= 1 + 0.8 * MassSolver.smooth(0.05, 0.35, so.shelterAt(x, z)) * Math.exp(-h / 150);
        return Math.min(1, i * s);
    }

    /**
     * Böe (m/s) am Ort zur Zeit t: Mittel · (1 + I · k · n), n ist ein Rauschen von −1 bis 1, das mit dem Wind zieht;
     * im Mittel über die Zeit gleich dem Wind, die Spitzen bei {@link #peakFactor}.
     */
    public double gustAt(double x, double y, double z, double t) {
        double[] w = windAt(x, y, z, t);
        double u = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]);
        if (u <= 0) return 0;
        double[] d = toVector();
        double px = x - d[0] * u * t, pz = z - d[2] * u * t;
        double n = (noise(px / gustScale, pz / gustScale, 0) * 0.65 + noise(px / gustScale * 2.7, pz / gustScale * 2.7, 1) * 0.35) * 2 - 1;
        return Math.max(0, u * (1 + turbulenceAt(x, y, z) * peakFactor * n));
    }

    /** Kaltluft in einer Senke (m Tiefe des Sees aus Kaltluft) am Ort; seit Phase 4 aus dem gelösten Feld, sonst 0. */
    public double coldPoolAt(double x, double z, double t) {
        WindSolution so = solution;
        return so != null && so.contains(x, z) ? so.coldAt(x, z) : 0;
    }

    // ------------------------------------------------------------------ Gitter (F7 A)

    /**
     * Das geländefolgende Gitter: nx × nz Zellen zu {@code cell} m ab (x0, z0), {@code layers} Schichten bis
     * {@code top} m über Grund, zur Höhe hin gestreckt (die erste Schicht {@code first} m dick, jede folgende um
     * {@code stretch} dicker).
     */
    public record Grid(double x0, double z0, double cell, int nx, int nz, int layers, double top, double first, double stretch) {
        /** Höhe über Grund (m) der Mitte von Schicht k (dort liegt die Mitte des Texels, s = k + ½). */
        public double layerHeight(int k) { return first * (Math.pow(stretch, k + 0.5) - 1) / (stretch - 1); }
        /** Schicht (mit Bruchteil) für h m über Grund; Umkehrung der Streckung, wie in {@code wind.glsl}. */
        public double layerOf(double h) {
            double s = Math.log(1 + Math.max(0, h) * (stretch - 1) / first) / Math.log(stretch);
            return Math.max(0, Math.min(layers, s));
        }
        public int cells() { return nx * nz * layers; }
    }

    /** Ein Gitter über dem Rechteck (xMin, zMin, xMax, zMax) nach der Stufe; die erste Schicht 10 m [G]. */
    public Grid grid(double xMin, double zMin, double xMax, double zMax) {
        double c = quality.cell, first = 10;
        int nx = Math.max(2, (int) Math.round((xMax - xMin) / c)), nz = Math.max(2, (int) Math.round((zMax - zMin) / c)), n = quality.layers;
        return new Grid(xMin, zMin, c, nx, nz, n, top, first, stretchFor(top, first, n));
    }

    /** Streckung r, bei der n Schichten ab {@code first} m genau {@code top} m hoch reichen (Halbierung). */
    static double stretchFor(double top, double first, int n) {
        if (first * n >= top) return 1.000001;
        double lo = 1.000001, hi = 2;
        for (int i = 0; i < 80; i++) {
            double r = (lo + hi) / 2, sum = first * (Math.pow(r, n) - 1) / (r - 1);
            if (sum > top) hi = r; else lo = r;
        }
        return (lo + hi) / 2;
    }

    /** Das Feld auf dem Gitter als RGBA je Zelle (u, v, w, Böe), Schicht für Schicht, für {@code gl.WindGl}. */
    public float[] sample(Grid g, double t) {
        float[] out = new float[g.cells() * 4];
        int i = 0;
        for (int k = 0; k < g.layers(); k++)
            for (int j = 0; j < g.nz(); j++)
                for (int n = 0; n < g.nx(); n++) {
                    double x = g.x0() + (n + 0.5) * g.cell(), z = g.z0() + (j + 0.5) * g.cell();
                    WindSolution so = solution;
                    // über dem Grund des gelösten Felds (zwischen den Mitten seiner Spalten), sonst über dem Gelände
                    double y = (so != null && so.contains(x, z) ? so.groundAt(x, z) : ground.height(x, z)) + g.layerHeight(k);
                    double[] w = windAt(x, y, z, t);
                    out[i++] = (float) w[0];
                    out[i++] = (float) w[1];
                    out[i++] = (float) w[2];
                    out[i++] = (float) gustAt(x, y, z, t);
                }
        return out;
    }

    /** „Bft 6 aus 260°, neutral, z0 0,30 m, Gitter 50 m · 30 Schichten bis 1 500 m · gelöst: …“ */
    public String describe() {
        WindSolution so = solution;
        return String.format(java.util.Locale.GERMAN, "Bft %d aus %03.0f°, %s, z0 %.2f m, Gitter %.0f m · %d Schichten bis %.0f m · %s", beaufortOf(speed), fromDeg,
                stability.name().toLowerCase(java.util.Locale.ROOT), z0, quality.cell, quality.layers, top, so == null ? "ungelöst (Näherung)" : "gelöst: " + so.describe());
    }

    /** Wertrauschen 0..1 mit weicher Interpolation, nur aus seed und Gitter (reproduzierbar). */
    double noise(double x, double z, int octave) {
        long ix = (long) Math.floor(x), iz = (long) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz, octave), b = hash(ix + 1, iz, octave), c = hash(ix, iz + 1, octave), d = hash(ix + 1, iz + 1, octave);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    double hash(long x, long z, int o) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L + o * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
