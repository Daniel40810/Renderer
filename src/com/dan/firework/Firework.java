package com.dan.firework;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Feuerwerk: Bomben aus Mörsern und vom Ponton, Steigzeit und Zerlegungshöhe nach Kaliber, Sterne mit Masse und
 * Abbrand, Farbe aus dem Spektrum der Metallsalze, Spuren aus Funken, Glitzern, Rauch im Wind, Licht je Bombe für
 * Rauch, Wolken und Wasser, der Knall mit Laufzeit (Werkbuch Timmendorf, Abschnitte 6 und 7, F16 A). Hülle seit
 * Timmendorf Phase 1, <b>das Feuerwerk seit Phase 11</b>.
 * <p>
 * Physik: Bombe und Sterne fliegen mit Luftwiderstand (m·dv/dt = m·g − ½·ρ·c_w·A·|v − w|·(v − w)), die Sterne
 * brennen von außen ab (Masse linear, Durchmesser mit der dritten Wurzel), werden dabei leichter und bremsen stärker.
 * Faustregeln [Kosanke, Kosanke, „Pyrotechnic Ballistics“ (1990), und die Praxisregel „Steighöhe rund 100 ft, Bild
 * rund 70 ft je Zoll“]: eine Bombe von 75 mm zerlegt sich in rund 90 m Höhe, ihr Bild hat rund 60 m Durchmesser; die
 * Mündungsgeschwindigkeit ist so gewählt, dass die Zerlegung dort liegt, die Sternengeschwindigkeit so, dass das Bild
 * beim Erlöschen so groß ist.
 * <p>
 * Licht: je Stern eine Lichtstärke (farbige Sterne um 1 500 cd, weiße Blitzsterne um 6 000 cd, Gold 400 cd [G nach
 * Shimizu]); zusammengefasst je Bombe ({@link #lights()}): Schwerpunkt der brennenden Sterne, Summe der Lichtstärken
 * als lineares RGB, dazu der Blitz der Zerlegung. Spuren ({@link Star#trail}) halten die letzten {@link #trailSeconds}
 * s je Stern (für Bewegungsunschärfe und die Langzeitbelichtung). Rauch als Wolken, die wachsen, steigen und mit dem
 * Wind ziehen.
 * <p>
 * Raum der Szenen: x Ost, y oben, z Süd, Meter, Zeit in s. Der Kern kennt nur Java 21. Der Wind kommt über die eigene
 * Schnittstelle {@link Wind}.
 */
public final class Firework {
    /** Qualitätsstufen (Paketvertrag Punkt 7): Sterne je Bombe von 100 mm 40, 80, 150, 300; Rauch dichter. */
    public enum Quality {
        LOW(40, 0.4), MEDIUM(80, 0.7), HIGH(150, 1), ULTRA(300, 1.5);
        public final int stars;
        public final double smoke;
        Quality(int s, double k) { stars = s; smoke = k; }
    }

    /**
     * Die Effekte (Bild der Zerlegung) nach Shimizu: Päonie (Kugel farbiger Sterne ohne Schweif), Chrysantheme (mit
     * Funkenschweif), Weide (Gold, lange Brenndauer, hängende Zweige), Kamuro (Gold und Silber, glitzernd, „Krone“),
     * Palme (wenige große Kometen, mit Schweif beim Steigen), Ring (Sterne in einer Ebene), Crossette (Sterne teilen sich
     * in vier), Komet (Schweif beim Steigen, kleine Zerlegung), Fächer (Kometen vom Boden in einem Fächer).
     */
    public enum Effect { PEONY, CHRYSANTHEMUM, WILLOW, KAMURO, PALM, RING, CROSSETTE, COMET, FAN }

    /**
     * Farbgebende Salze mit der Wellenlänge ihrer kräftigsten Emission (nm); seit Phase 11 dazu Gold (Holzkohle, nur
     * Glühen). Die Farbe kommt aus dem ganzen Spektrum ({@link Spectrum}).
     */
    public enum Salt {
        STRONTIUM(636), CALCIUM(606), SODIUM(589), BARIUM(524), COPPER(452), VIOLET(420), WHITE(0), GOLD(0);
        public final double nm;
        Salt(double nm) { this.nm = nm; }
        /** Farbe als lineares sRGB, Leuchtdichte 1. */
        public double[] rgb() { return Spectrum.rgbCached(this); }
    }

    /** Wind am Ort (m/s je Achse x, z), etwa aus {@code wind}. */
    @FunctionalInterface
    public interface Wind { void velocity(double x, double y, double z, double t, double[] out); }

    /** Ereignisse nach außen: Abschuss und Zerlegung (für Klang, Licht und Regie). */
    public interface Events {
        default void onLaunch(Shell s, double t) { }
        default void onBurst(Shell s, double x, double y, double z, double strength, double t) { }
    }

    /**
     * Eine Bombe: Kaliber (mm), Effekt, Salz, Abschussort, Zeit des Abschusses (s), Verzögerung bis zur Zerlegung (s).
     */
    public static final class Shell {
        public final int caliberMm;
        public final Effect effect;
        public final Salt salt;
        public final double launchT, fuse;
        public double x, y, z, vx, vy, vz;
        public boolean launched, burst;
        /** Zeit der Zerlegung (s), NaN vorher. */
        public double burstT = Double.NaN;
        /** Seit Phase 11: Neigung des Mörsers (Grad zur Senkrechten) und Richtung (Grad von Nord); Fächer: Öffnung. */
        public double tiltDeg, azimuthDeg;
        public Shell(int caliberMm, Effect effect, Salt salt, double x, double y, double z, double launchT) {
            this.caliberMm = caliberMm; this.effect = effect; this.salt = salt; this.x = x; this.y = y; this.z = z; this.launchT = launchT;
            this.fuse = fuseFor(caliberMm);
        }
        public double mass() { return massFor(caliberMm); }
        public double area() { double r = caliberMm / 2000.0 * 0.9; return Math.PI * r * r; }
    }

    /**
     * Ein Stern: Ort, Geschwindigkeit, Durchmesser (m), Brenndauer und Alter (s), Salz; die Spur hält die letzten Orte
     * mit ihrer Lichtstärke (je {@link #TRAIL_STEP} s ein Punkt: x, y, z, cd).
     */
    public static final class Star {
        public double x, y, z, vx, vy, vz;
        public final double d0, burn, density;
        public double age;
        public final Salt salt;
        public final Effect effect;
        public final Shell shell;
        /** Lichtstärke beim Zünden (cd), Glitzern (Hz, 0 = ruhig), Funkenschweif (Anteil der Spur, die leuchtet). */
        public final double cd0, twinkle, sparks;
        public boolean alive = true;
        boolean split;
        /** Laufende Nummer (für Wiederholbarkeit, statt der Speicheradresse). */
        public int id;
        /** Spur: Ring aus Punkten (x, y, z, cd), {@link #trailCount} gültig, der neueste bei {@link #trailHead}. */
        public float[] trail;
        public int trailHead = -1, trailCount;
        double trailClock;
        final double phase;

        Star(Shell sh, Salt salt, Effect fx, double x, double y, double z, double vx, double vy, double vz, double d0, double burn, double cd0, double twinkle,
             double sparks, double density, int cap, double phase) {
            shell = sh; this.salt = salt; effect = fx; this.x = x; this.y = y; this.z = z; this.vx = vx; this.vy = vy; this.vz = vz;
            this.d0 = d0; this.burn = burn; this.cd0 = cd0; this.twinkle = twinkle; this.sparks = sparks; this.density = density; this.phase = phase;
            trail = new float[cap * 4];
        }

        /** Durchmesser jetzt (m): brennt von außen ab, Masse linear. */
        public double diameter() { return d0 * Math.cbrt(Math.max(0, 1 - age / burn)); }
        public double mass() { double d = diameter(); return density * Math.PI * d * d * d / 6; }

        /** Lichtstärke jetzt (cd): voll, im letzten Zehntel erlöschend; Glitzern als Blinken. */
        public double candela() {
            if (!alive) return 0;
            double f = age / burn, fade = f > 0.9 ? (1 - f) / 0.1 : 1, rise = Math.min(1, age / 0.06);
            double tw = twinkle > 0 ? 0.25 + 0.75 * (0.5 + 0.5 * Math.signum(Math.sin(2 * Math.PI * twinkle * age + phase))) : 1;
            return cd0 * fade * rise * tw;
        }

        /** Punkt i der Spur, 0 = neuester: {x, y, z, cd}. */
        public float[] trailPoint(int i, float[] out) {
            int cap = trail.length / 4, k = ((trailHead - i) % cap + cap) % cap;
            out[0] = trail[k * 4]; out[1] = trail[k * 4 + 1]; out[2] = trail[k * 4 + 2]; out[3] = trail[k * 4 + 3];
            return out;
        }
    }

    /** Eine Rauchwolke: Mitte, Halbmesser (m), optische Dichte durch die Mitte, Alter und Lebensdauer (s). */
    public static final class Smoke {
        public double x, y, z, radius, density, age;
        public final double life;
        Smoke(double x, double y, double z, double r, double d, double life) { this.x = x; this.y = y; this.z = z; radius = r; density = d; this.life = life; }
        /** Optische Dichte jetzt (nimmt mit dem Wachsen und dem Alter ab). */
        public double opacity() { return Math.max(0, density * (1 - age / life)); }
    }

    /** Licht einer Bombe: Ort, Lichtstärke je Farbkanal (cd, linear sRGB), Summe (cd). */
    public record Light(double x, double y, double z, double r, double g, double b, double cd) { }

    /** Abstand der Punkte einer Spur (s). */
    public static final double TRAIL_STEP = 1.0 / 30;

    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Lufttemperatur (°C) und Luftdichte (kg/m³); Widerstandsbeiwert einer Kugel [G]. */
    public double temperatureC = 18, airDensity = 1.2, dragCoefficient = 0.47;
    /** Wie lange die Spuren gehalten werden (s): 0,4 für Bewegungsunschärfe, 4 für eine Langzeitbelichtung. */
    public double trailSeconds = 1;
    /** Turbulente Diffusion des Rauchs (m²/s) und sein Auftrieb (m/s) [G]. */
    public double smokeDiffusion = 0.6, smokeRise = 0.25;
    public Wind wind = (x, y, z, t, o) -> { o[0] = 0; o[1] = 0; o[2] = 0; };
    public Events events = new Events() { };
    private final List<Shell> shells = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<Smoke> smoke = new ArrayList<>();
    private final List<double[]> flashes = new ArrayList<>();
    private double time;
    private Random rnd;
    private int starsMade;

    // ------------------------------------------------------------------ Presets (Punkt 5)

    /** Ein kurzes Feuerwerk über dem Wasser bei (x, z): zwölf Bomben in 30 s [G]. */
    public static Firework demo(double x, double z) {
        Firework f = new Firework();
        f.load(x, 1.5, z, "0 75 PEONY STRONTIUM\n2 100 CHRYSANTHEMUM WHITE\n4 75 PEONY BARIUM\n6 125 WILLOW GOLD\n9 100 RING COPPER\n"
                + "11 75 PALM SODIUM\n14 150 KAMURO GOLD\n17 100 CROSSETTE STRONTIUM\n20 75 COMET BARIUM\n22 100 FAN CALCIUM\n25 150 PEONY VIOLET\n28 200 WILLOW GOLD");
        return f;
    }

    /**
     * Seit Phase 11: ein Feuerwerk zur Travemünder Woche vom Ponton (rund 5½ min, 91 Bomben) [G]: Eröffnung mit Kometen
     * und Fächern, Bilder in Rot, Grün, Blau, ein Mittelteil mit Weiden und Kamuro, ein Finale mit Salven aus 75 mm und
     * drei Bomben von 300 mm.
     */
    public static Firework travemuenderWoche(double x, double z) {
        StringBuilder p = new StringBuilder("# Eröffnung\n");
        Salt[] farbe = {Salt.STRONTIUM, Salt.BARIUM, Salt.COPPER, Salt.SODIUM, Salt.CALCIUM, Salt.VIOLET, Salt.WHITE};
        Effect[] bild = {Effect.PEONY, Effect.CHRYSANTHEMUM, Effect.RING, Effect.CROSSETTE, Effect.PALM};
        for (int i = 0; i < 6; i++) p.append(i * 1.5).append(" 75 COMET ").append(farbe[i % 3]).append('\n');
        p.append("10 100 FAN WHITE\n12 100 FAN GOLD\n");
        double t = 16;
        for (int i = 0; i < 40; i++, t += 4.5) p.append(String.format(java.util.Locale.ROOT, "%.1f %d %s %s%n", t, i % 5 == 4 ? 150 : 100, bild[i % bild.length], farbe[(i * 3) % farbe.length]));
        p.append("# Mittelteil\n");
        for (int i = 0; i < 16; i++, t += 6) p.append(String.format(java.util.Locale.ROOT, "%.1f %d %s GOLD%n", t, i % 4 == 3 ? 200 : 125, i % 2 == 0 ? "WILLOW" : "KAMURO"));
        p.append("# Finale\n");
        for (int i = 0; i < 24; i++) p.append(String.format(java.util.Locale.ROOT, "%.1f 75 %s %s%n", t + i * 0.5, bild[i % 2], farbe[i % farbe.length]));
        t += 14;
        p.append(String.format(java.util.Locale.ROOT, "%.1f 300 PEONY WHITE%n%.1f 300 KAMURO GOLD%n%.1f 300 WILLOW GOLD%n", t, t + 1.5, t + 3));
        Firework f = new Firework();
        f.load(x, 1.5, z, p.toString());
        return f;
    }

    /**
     * Lädt einen Zündplan als Text: je Zeile „Zeit Kaliber Effekt Salz“ (Zeit in s ab dem Start, Kaliber in mm), seit
     * Phase 11 wahlweise „neigung=Grad richtung=Grad“ dahinter; leere Zeilen und Zeilen ab „#“ zählen nicht. Gibt die
     * Zahl der Bomben zurück; ein Fehler nennt die Zeile.
     */
    public int load(double x, double y, double z, String plan) {
        int n = 0, line = 0;
        for (String l : plan.split("\n")) {
            line++;
            String s = l.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] p = s.split("\\s+");
            if (p.length < 4) throw new IllegalArgumentException("Zündplan Zeile " + line + ": „Zeit Kaliber Effekt Salz“ erwartet");
            try {
                Shell sh = new Shell(Integer.parseInt(p[1]), Effect.valueOf(p[2]), Salt.valueOf(p[3]), x, y, z, Double.parseDouble(p[0].replace(',', '.')));
                for (int k = 4; k < p.length; k++) {
                    String[] kv = p[k].split("=");
                    if (kv.length != 2) throw new IllegalArgumentException("„" + p[k] + "“ verstehe ich nicht");
                    double v = Double.parseDouble(kv[1].replace(',', '.'));
                    switch (kv[0]) {
                        case "neigung" -> sh.tiltDeg = v;
                        case "richtung" -> sh.azimuthDeg = v;
                        default -> throw new IllegalArgumentException("„" + kv[0] + "“ gibt es nicht (neigung, richtung)");
                    }
                }
                shells.add(sh);
                n++;
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Zündplan Zeile " + line + ": " + e.getMessage(), e);
            }
        }
        return n;
    }

    public List<Shell> shells() { return List.copyOf(shells); }
    public List<Star> stars() { return stars; }
    public List<Smoke> smoke() { return smoke; }
    public double time() { return time; }
    /** Wie viele Sterne bisher entstanden sind. */
    public int starsMade() { return starsMade; }

    /** Dauer des Plans (s): letzter Abschuss plus Steigzeit plus die längste Brenndauer. */
    public double duration() {
        double d = 0;
        for (Shell s : shells) d = Math.max(d, s.launchT + s.fuse + starBurn(s.caliberMm, s.effect) * 1.6);
        return d;
    }

    // ------------------------------------------------------------------ Faustwerte nach Kaliber

    /** Masse der Bombe (kg): rund 0,25 kg bei 75 mm, wächst mit dem Volumen. */
    public static double massFor(int mm) { return 0.25 * Math.pow(mm / 75.0, 3); }

    /**
     * Abschussgeschwindigkeit aus dem Mörser (m/s): seit Phase 11 so gerechnet, dass die Zerlegung nach der
     * Verzögerung bei 1,2 m je mm Kaliber liegt (Praxisregel 100 ft je Zoll), in ruhiger Luft bei 1,2 kg/m³.
     */
    public static double muzzleSpeed(int mm) {
        int c = Math.max(50, Math.min(400, mm));
        return MUZZLE.computeIfAbsent(c, k -> {
            // Halbierung: Geschwindigkeit, mit der die Bombe nach der Verzögerung in 1,2 m je mm Höhe ist
            double lo = 10, hi = 600, want = 1.2 * k;
            for (int i = 0; i < 60; i++) {
                double v = (lo + hi) / 2;
                if (rise(k, v) < want) lo = v; else hi = v;
            }
            return (lo + hi) / 2;
        });
    }

    private static final java.util.Map<Integer, Double> MUZZLE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Höhe nach der Verzögerung bei Abschuss mit v (m/s), Luft wie voreingestellt. */
    static double rise(int mm, double v) {
        double r = mm / 2000.0 * 0.9, a = Math.PI * r * r, m = massFor(mm), c = 0.5 * 1.2 * 0.47 * a / m, y = 0, dt = 0.002;
        for (double t = 0; t < fuseFor(mm); t += dt) {
            v -= (9.81 + c * Math.abs(v) * v) * dt;
            y += v * dt;
        }
        return y;
    }

    /** Verzögerung bis zur Zerlegung (s), nahe dem Scheitel: rund 0,0175 s je mm (75 mm: 2,8 s; 150 mm: 4,1 s). */
    public static double fuseFor(int mm) { return 1.5 + 0.0175 * mm; }

    /** Durchmesser der Sterne (m): 75 mm Bomben haben Sterne um 14 mm, 300 mm um 32 mm [G]. */
    public static double starDiameter(int mm) { return (8 + 0.08 * mm) / 1000; }

    /** Brenndauer der Sterne (s) nach Kaliber und Effekt [G]: farbig 2,2 s bei 75 mm; Weide 2,6 ×, Kamuro 2 ×. */
    public static double starBurn(int mm, Effect fx) {
        double b = 1.6 + 0.008 * mm;
        return switch (fx) {
            case WILLOW -> b * 2.6;
            case KAMURO -> b * 2.0;
            case PALM, FAN, COMET -> b * 1.2;
            default -> b;
        };
    }

    /** Halbmesser des Bilds beim Erlöschen (m): Praxisregel 70 ft je Zoll Durchmesser, also rund 0,42 m je mm. */
    public static double burstRadius(int mm) { return 0.42 * mm; }

    /** Schallgeschwindigkeit in Luft (m/s) bei T °C: 331,3·√(1 + T/273,15). */
    public static double soundSpeed(double tempC) { return 331.3 * Math.sqrt(1 + tempC / 273.15); }

    /** Laufzeit des Knalls über d m bei T °C (s). */
    public static double soundDelay(double d, double tempC) { return d / soundSpeed(tempC); }

    /**
     * Anfangsgeschwindigkeit der Sterne (m/s), damit sie bei Luftwiderstand in der Brenndauer T den Halbmesser R
     * erreichen: im quadratischen Widerstand ist der Weg x(t) = (m/k)·ln(1 + k·v₀·t/m), also v₀ = (e^(R·k/m) − 1)·m/(k·T),
     * mit Masse und Fläche zur halben Brenndauer [Näherung, die Prüfung misst nach].
     */
    double starSpeed(int mm, Effect fx, double density) {
        double d = starDiameter(mm) * Math.cbrt(0.5) * (fx == Effect.PALM ? 2 : 1), T = starBurn(mm, fx);
        double m = density * Math.PI * d * d * d / 6, k = 0.5 * airDensity * dragCoefficient * Math.PI * d * d / 4;
        double R = burstRadius(mm) * (fx == Effect.WILLOW ? 0.85 : fx == Effect.PALM ? 0.9 : 1);
        double a = Math.min(30, R * k / m);
        return (Math.exp(a) - 1) * m / (k * T);
    }

    // ------------------------------------------------------------------ Schritt

    /** Rechnet dt Sekunden weiter: Abschuss nach Plan, Bahnen, Zerlegung, Sterne, Spuren, Rauch. */
    public void step(double dt) {
        if (rnd == null) rnd = new Random(seed);
        double h = Math.min(0.02, Math.max(1e-4, dt));
        int n = (int) Math.ceil(dt / h - 1e-9);
        for (int k = 0; k < n; k++) sub(dt / n);
    }

    private void sub(double dt) {
        time += dt;
        double[] w = new double[3];
        for (Shell s : shells) {
            if (s.burst) continue;
            if (!s.launched) {
                if (time < s.launchT) continue;
                s.launched = true;
                double v = muzzleSpeed(s.caliberMm), tl = Math.toRadians(s.tiltDeg), az = Math.toRadians(s.azimuthDeg);
                s.vx = v * Math.sin(tl) * Math.sin(az);
                s.vy = v * Math.cos(tl);
                s.vz = -v * Math.sin(tl) * Math.cos(az);
                events.onLaunch(s, time);
                if (s.effect == Effect.FAN) { fan(s); continue; }
                if (s.effect == Effect.PALM || s.effect == Effect.COMET) {   // Schweif beim Steigen: ein Stern, der mit der Bombe fliegt
                    Star c = new Star(s, s.effect == Effect.COMET ? s.salt : Salt.GOLD, s.effect, s.x, s.y, s.z, s.vx, s.vy, s.vz, starDiameter(s.caliberMm) * 2,
                            s.fuse, s.effect == Effect.COMET ? 2500 : 900, 0, 1, 1600, cap(), rnd.nextDouble() * 6);
                    c.age = 0;
                    c.id = starsMade++;
                    stars.add(c);
                    tails.put(s, c);
                }
            }
            wind.velocity(s.x, s.y, s.z, time, w);
            double rx = s.vx - w[0], ry = s.vy - w[1], rz = s.vz - w[2], r = Math.sqrt(rx * rx + ry * ry + rz * rz);
            double c = 0.5 * airDensity * dragCoefficient * s.area() * r / s.mass();
            s.vx -= c * rx * dt;
            s.vy -= (9.81 + c * ry) * dt;
            s.vz -= c * rz * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.z += s.vz * dt;
            Star tail = tails.get(s);
            if (tail != null) { tail.x = s.x; tail.y = s.y; tail.z = s.z; tail.vx = s.vx; tail.vy = s.vy; tail.vz = s.vz; }
            if (time - s.launchT >= s.fuse) burst(s);
        }
        stepStars(dt, w);
        stepSmoke(dt, w);
        flashes.removeIf(f -> time - f[4] > 0.12);
    }

    private final java.util.Map<Shell, Star> tails = new java.util.IdentityHashMap<>();

    private int cap() { return Math.max(4, (int) Math.ceil(trailSeconds / TRAIL_STEP) + 2); }

    /** Zerlegung: Sterne nach dem Effekt, der Blitz, eine Wolke aus der Zerlegerladung. */
    private void burst(Shell s) {
        s.burst = true;
        s.burstT = time;
        Star tail = tails.remove(s);
        if (tail != null) tail.age = Math.max(tail.age, tail.burn * 0.92);
        int mm = s.caliberMm;
        double scale = Math.pow(mm / 100.0, 1.2);
        int count = (int) Math.max(8, Math.round(quality.stars * scale));
        Effect fx = s.effect;
        Salt salt = fx == Effect.WILLOW || fx == Effect.KAMURO ? Salt.GOLD : s.salt;
        double density = fx == Effect.WILLOW ? 900 : fx == Effect.KAMURO ? 1100 : 1600;
        if (fx == Effect.PALM) count = Math.max(6, count / 6);
        if (fx == Effect.CROSSETTE) count = Math.max(8, count / 4);
        if (fx == Effect.COMET) count = 6;
        double v0 = starSpeed(mm, fx, density) * (fx == Effect.COMET ? 0.3 : 1);
        double d0 = starDiameter(mm) * (fx == Effect.PALM ? 2 : 1), burn = starBurn(mm, fx);
        double cd = switch (salt) { case WHITE -> 6000; case GOLD -> 400; default -> 1500; } * Math.pow(d0 / 0.014, 2);
        double twinkle = fx == Effect.KAMURO ? 14 : fx == Effect.WILLOW ? 0 : salt == Salt.WHITE && fx == Effect.CHRYSANTHEMUM ? 9 : 0;
        double sparks = fx == Effect.CHRYSANTHEMUM || fx == Effect.WILLOW || fx == Effect.KAMURO || fx == Effect.PALM ? 1 : 0.15;
        // Ring: eine Ebene, meist steil zum Betrachter [G: zufällig gedreht]
        double ra = rnd.nextDouble() * Math.PI, rt = (rnd.nextDouble() - 0.5) * 0.8;
        double[] nrm = {Math.sin(ra) * Math.cos(rt), Math.sin(rt), -Math.cos(ra) * Math.cos(rt)};
        for (int i = 0; i < count; i++) {
            double[] d;
            if (fx == Effect.RING) {
                double a = 2 * Math.PI * i / count;
                double[] u = perp(nrm), v = cross(nrm, u);
                d = new double[]{u[0] * Math.cos(a) + v[0] * Math.sin(a), u[1] * Math.cos(a) + v[1] * Math.sin(a), u[2] * Math.cos(a) + v[2] * Math.sin(a)};
            } else d = fibonacci(i, count, rnd.nextDouble() * 0.02);
            double sp = v0 * (0.93 + 0.14 * rnd.nextDouble());
            Star st = new Star(s, salt, fx, s.x, s.y, s.z, s.vx * 0.3 + d[0] * sp, s.vy * 0.3 + d[1] * sp, s.vz * 0.3 + d[2] * sp, d0, burn * (0.9 + 0.2 * rnd.nextDouble()),
                    cd, twinkle, sparks, density, cap(), rnd.nextDouble() * 6.28);
            st.id = starsMade++;
            stars.add(st);
        }
        // Blitz der Zerlegerladung: rund 2 Mcd für 0,1 s [G], weiß
        flashes.add(new double[]{s.x, s.y, s.z, 2e6 * scale, time});
        // Rauch der Zerlegerladung: Wolken um die Mitte
        int puffs = (int) Math.max(2, Math.round(6 * quality.smoke * scale));
        for (int i = 0; i < puffs; i++) {
            double[] d = fibonacci(i, puffs, rnd.nextDouble());
            double r0 = burstRadius(mm) * 0.25;
            smoke.add(new Smoke(s.x + d[0] * r0, s.y + d[1] * r0, s.z + d[2] * r0, r0 * 0.6, 0.9, 50 + 30 * rnd.nextDouble()));
        }
        events.onBurst(s, s.x, s.y, s.z, mm / 150.0, time);
    }

    /** Fächer: sieben Kometen vom Boden, in einer senkrechten Ebene von −45° bis 45° [G]. */
    private void fan(Shell s) {
        s.burst = true;
        s.burstT = time;
        double az = Math.toRadians(s.azimuthDeg + 90), v = muzzleSpeed(s.caliberMm) * 0.85;
        double burn = starBurn(s.caliberMm, Effect.FAN), d0 = starDiameter(s.caliberMm) * 1.6;
        for (int i = 0; i < 7; i++) {
            double a = Math.toRadians(-45 + 15 * i);
            double hx = Math.sin(a) * Math.sin(az), hz = -Math.sin(a) * Math.cos(az);
            Star st = new Star(s, s.salt, Effect.FAN, s.x, s.y, s.z, hx * v, Math.cos(a) * v, hz * v, d0, burn, 2500, 0, 1, 1600, cap(), i);
            st.id = starsMade++;
            stars.add(st);
        }
        events.onBurst(s, s.x, s.y, s.z, 0.2, time);
    }

    private void stepStars(double dt, double[] w) {
        List<Star> born = new ArrayList<>();
        for (Star st : stars) {
            if (st.alive) {
                st.age += dt;
                if (st.age >= st.burn) st.alive = false;
                if (st.effect == Effect.CROSSETTE && !st.split && st.age > 0.4 * st.burn && st.shell.effect == Effect.CROSSETTE) {
                    st.split = true;
                    st.alive = false;
                    double sp = Math.sqrt(st.vx * st.vx + st.vy * st.vy + st.vz * st.vz) * 0.6 + 12;
                    double[] f = {st.vx, st.vy, st.vz};
                    double fl = Math.max(1e-6, Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]));
                    for (int q = 0; q < 3; q++) f[q] /= fl;
                    double[] u = perp(f), v = cross(f, u);
                    for (int q = 0; q < 4; q++) {
                        double a = q * Math.PI / 2;
                        double dx = u[0] * Math.cos(a) + v[0] * Math.sin(a), dy = u[1] * Math.cos(a) + v[1] * Math.sin(a), dz = u[2] * Math.cos(a) + v[2] * Math.sin(a);
                        Star c = new Star(st.shell, st.salt, Effect.PEONY, st.x, st.y, st.z, st.vx * 0.5 + dx * sp, st.vy * 0.5 + dy * sp, st.vz * 0.5 + dz * sp,
                                st.d0 * 0.7, st.burn * 0.55, st.cd0 * 0.6, 0, 0.6, st.density, st.trail.length / 4, st.phase + q);
                        born.add(c);
                    }
                }
                if (st.alive && !tailOf(st)) {
                    wind.velocity(st.x, st.y, st.z, time, w);
                    double rx = st.vx - w[0], ry = st.vy - w[1], rz = st.vz - w[2], r = Math.sqrt(rx * rx + ry * ry + rz * rz);
                    double d = st.diameter(), m = Math.max(1e-7, st.mass());
                    double c = 0.5 * airDensity * dragCoefficient * Math.PI * d * d / 4 * r / m;
                    c = Math.min(c, 0.9 / dt);   // fast ausgebrannt: nicht über das Ziel hinaus bremsen
                    st.vx -= c * rx * dt;
                    st.vy -= (9.81 + c * ry) * dt;
                    st.vz -= c * rz * dt;
                    st.x += st.vx * dt;
                    st.y += st.vy * dt;
                    st.z += st.vz * dt;
                    // Rauch aus dem Abbrand: alle 0,6 s eine kleine Wolke von jedem dritten Stern
                    if (((int) ((st.age - dt) / 0.6)) != ((int) (st.age / 0.6)) && (st.id % 3 == 0) && rnd.nextDouble() < quality.smoke)
                        smoke.add(new Smoke(st.x, st.y, st.z, 0.8, 0.5, 40 + 30 * rnd.nextDouble()));
                }
            } else st.age += dt;
            // Spur: je TRAIL_STEP ein Punkt, mit der Lichtstärke dieses Augenblicks (0 nach dem Erlöschen)
            st.trailClock += dt;
            if (st.trailClock >= TRAIL_STEP || st.trailHead < 0) {
                st.trailClock = st.trailHead < 0 ? 0 : st.trailClock - TRAIL_STEP;
                int cap = st.trail.length / 4;
                st.trailHead = (st.trailHead + 1) % cap;
                int o = st.trailHead * 4;
                st.trail[o] = (float) st.x; st.trail[o + 1] = (float) st.y; st.trail[o + 2] = (float) st.z; st.trail[o + 3] = (float) st.candela();
                st.trailCount = Math.min(cap, st.trailCount + 1);
            }
        }
        for (Star c : born) c.id = starsMade++;
        stars.addAll(born);
        // weg, wenn erloschen und die Spur verblasst ist
        stars.removeIf(st -> !st.alive && st.age > st.burn + trailSeconds + 0.1);
    }

    private boolean tailOf(Star st) { return tails.containsValue(st); }

    private void stepSmoke(double dt, double[] w) {
        for (Smoke s : smoke) {
            s.age += dt;
            wind.velocity(s.x, s.y, s.z, time, w);
            s.x += w[0] * dt;
            s.y += smokeRise * dt;
            s.z += w[2] * dt;
            double r2 = s.radius * s.radius + 2 * smokeDiffusion * dt;
            // gleiche Rauchmenge in größerem Volumen: die optische Dichte durch die Mitte fällt mit dem Halbmesser
            s.density *= s.radius / Math.sqrt(r2);
            s.radius = Math.sqrt(r2);
        }
        smoke.removeIf(s -> s.age > s.life || s.opacity() < 0.002);
    }

    // ------------------------------------------------------------------ Licht

    /**
     * Licht je Bombe (zusammengefasst, wie im Werkbuch vorgesehen): Schwerpunkt der brennenden Sterne nach ihrer
     * Lichtstärke, Summe als lineares RGB (cd), dazu der Blitz der Zerlegung. Für Rauch, Wolken, Wasser und Gelände.
     */
    public List<Light> lights() {
        java.util.Map<Shell, double[]> m = new java.util.IdentityHashMap<>();
        for (Star st : stars) {
            double c = st.candela();
            if (c <= 0) continue;
            double[] a = m.computeIfAbsent(st.shell, k -> new double[7]);
            double[] rgb = st.salt.rgb();
            a[0] += st.x * c; a[1] += st.y * c; a[2] += st.z * c;
            a[3] += rgb[0] * c; a[4] += rgb[1] * c; a[5] += rgb[2] * c; a[6] += c;
        }
        List<Light> l = new ArrayList<>();
        for (double[] a : m.values()) l.add(new Light(a[0] / a[6], a[1] / a[6], a[2] / a[6], a[3], a[4], a[5], a[6]));
        for (double[] f : flashes) {
            double k = Math.max(0, 1 - (time - f[4]) / 0.12);
            l.add(new Light(f[0], f[1], f[2], f[3] * k, f[3] * k, f[3] * k * 0.95, f[3] * k));
        }
        return l;
    }

    /** Beleuchtungsstärke (lx, lineares RGB) am Ort p mit Normale n aus allen Lichtern (ohne Schatten). */
    public double[] illuminance(double px, double py, double pz, double nx, double ny, double nz) {
        double[] e = new double[3];
        for (Light l : lights()) {
            double dx = l.x() - px, dy = l.y() - py, dz = l.z() - pz, d2 = dx * dx + dy * dy + dz * dz, d = Math.sqrt(d2);
            double cos = nx == 0 && ny == 0 && nz == 0 ? 1 : Math.max(0, (dx * nx + dy * ny + dz * nz) / d);
            e[0] += l.r() * cos / d2; e[1] += l.g() * cos / d2; e[2] += l.b() * cos / d2;
        }
        return e;
    }

    // ------------------------------------------------------------------ Abfragen

    /** Scheitelhöhe einer senkrecht geschossenen Bombe mit Luftwiderstand (m), ohne Wind; numerisch. */
    public double apex(int mm) {
        Shell s = new Shell(mm, Effect.PEONY, Salt.WHITE, 0, 0, 0, 0);
        double v = muzzleSpeed(mm), y = 0, dt = 0.001;
        while (v > 0) {
            double c = 0.5 * airDensity * dragCoefficient * s.area() / s.mass();
            v -= (9.81 + c * v * v) * dt;
            y += v * dt;
        }
        return y;
    }

    /** Höhe der Zerlegung einer senkrecht geschossenen Bombe (m), ohne Wind. */
    public double burstHeight(int mm) {
        Shell s = new Shell(mm, Effect.PEONY, Salt.WHITE, 0, 0, 0, 0);
        double v = muzzleSpeed(mm), y = 0, dt = 0.001;
        for (double t = 0; t < s.fuse; t += dt) {
            double c = 0.5 * airDensity * dragCoefficient * s.area() / s.mass();
            v -= (9.81 + c * Math.abs(v) * v) * dt;
            y += v * dt;
        }
        return y;
    }

    /** Wie viel los ist (0..1). */
    public double strength() {
        long inAir = shells.stream().filter(s -> s.launched && !s.burst).count();
        return Math.min(1, 0.3 + inAir * 0.2 + stars.size() / 500.0);
    }

    /** „Feuerwerk: 12 Bomben, 5 abgeschossen, 3 zerlegt · 340 Sterne, 41 Rauchwolken · 380 kcd“ */
    public String describe() {
        long l = shells.stream().filter(s -> s.launched).count(), b = shells.stream().filter(s -> s.burst).count();
        double cd = 0;
        for (Light x : lights()) cd += x.cd();
        return String.format(java.util.Locale.GERMAN, "Feuerwerk: %d Bomben, %d abgeschossen, %d zerlegt · %,d Sterne, %,d Rauchwolken · %,.0f kcd · %.1f s",
                shells.size(), l, b, stars.size(), smoke.size(), cd / 1000, time);
    }

    // ------------------------------------------------------------------ Hilfen

    static double[] fibonacci(int i, int n, double jitter) {
        double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(Math.max(0, 1 - y * y)), a = i * 2.399963229728653 + jitter * 6.283;
        return new double[]{r * Math.cos(a), y, r * Math.sin(a)};
    }

    static double[] perp(double[] n) {
        double[] a = Math.abs(n[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] c = cross(n, a);
        double l = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        return new double[]{c[0] / l, c[1] / l, c[2] / l};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
