package com.dan.fall;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Ein Wasserfall als Kern ohne Grafikkarte (Engine seit Phase 6 von Geiranger).
 * <p>
 * <b>Eingänge:</b> eine Kante als Linie ({@link #lipA} bis {@link #lipB}), die Richtung, in die das Wasser abfließt
 * ({@link #headingDeg}), der Abfluss ({@link #discharge}), Wind, Temperatur, ein Wasserspiegel unten und, wenn es
 * eine gibt, eine Abfrage für Boden und Wand ({@link #ground}).
 * <p>
 * <b>Physik</b> (alles in geschlossener Form, kein Schrittverfahren, Zeile für Zeile wie {@code gl/fall.glsl}):
 * <ol>
 * <li><b>Abriss:</b> über einer scharfen Kante stellt sich die Grenztiefe ein, h<sub>c</sub> = ∛(q²/g); am Rand ist
 * der Strahl 0,715 h<sub>c</sub> dick und fließt mit q / (0,715 h<sub>c</sub>) ab.</li>
 * <li><b>Freier Fall mit Luftreibung</b> (quadratisch, waagerecht und senkrecht getrennt): senkrecht
 * y = (v<sub>t</sub>²/g) · ln cosh(g t / v<sub>t</sub>), waagerecht nähert sich die Geschwindigkeit dem Wind,
 * x = W t + ln(1 + k |r₀| t) / k mit k = g / v<sub>t</sub>².</li>
 * <li><b>Zerfall:</b> der geschlossene Strahl (Endgeschwindigkeit {@link #coreTerminal}) zerfällt nach der Höhe
 * L<sub>b</sub> in Tropfenpakete (Endgeschwindigkeit um {@link #dropTerminal}); ab da rechnet jedes Paket mit dem
 * Zustand am Zerfall weiter, dazu eine seitliche Streuung ({@link #dropSpread}). Daraus wird der Schleier breiter und
 * der Wind biegt ihn.</li>
 * <li><b>Aufprall</b> auf Wasser oder Fels: Ort, Geschwindigkeit, daraus Tosbecken, Gischt, Nebel, Ringwellen und
 * Lautstärke.</li>
 * <li><b>Eis:</b> unter 0 °C friert der Rand, unter rund −15 °C steht der Fall (Anteil {@link #ice()}).</li>
 * </ol>
 * <b>Ausgänge:</b> Strähnen und Bündel für die Grafikkarte ({@code gl/FallGl}), Ringwellen ({@link #ripples()}),
 * nasse Linie für die Wand ({@link #wetLine(double)}), Lautstärke ({@link #soundLevel(double)}), Dichte des
 * Nebels ({@link #mist()}). Maße mit [G] sind gesetzt, nicht gemessen.
 * <p>
 * <b>Lauf über Fels</b> (seit Geiranger Phase 7, {@link #overRock}): Wände aus einem groben Höhenfeld stehen selten
 * senkrecht; ein freier Fall von der Kante landete gleich im Hang. Mit {@code overRock} rechnet jede Strähne ihren Weg
 * über die Abfrage {@link #ground}: frei durch die Luft (dieselbe Reibung wie oben, Zerfall nach derselben Höhe),
 * Aufschlag auf den Fels (Anteil quer zum Hang verloren, längs bleibt {@link #impactKeep}), Gleiten mit Reibung
 * (Endgeschwindigkeit {@link #slideTerminal} auf senkrechter Wand) und Abspringen, wo der Fels schneller wegfällt als
 * die Wurfbahn. Der Weg liegt als Tabelle in gleichen Zeitschritten vor ({@link Strand#path}, {@link #PATH} Proben);
 * Prozessor und Grafikkarte lesen dieselbe Tabelle gleich ({@link #parcel}, {@code fallCourse} in {@code gl/fall.glsl}).
 * <p>
 * Paketvertrag: nur Java 21 im Kern, JOGL in {@code gl/}, FStyle nur in {@code demo/}; keine Abhängigkeit von der
 * App und keinem anderen Element-Package; alle Felder öffentlich; gleicher {@link #seed}, gleiches Bild, auch für
 * jede Strähne.
 */
public final class Fall {
    /** Qualitätsstufen: Zahl der Strähnen (höchstens), Abschnitte je Strähne, Bündel im Schleier, Nebelballen. */
    public enum Quality {
        LOW(5, 40, 1500, 24), MEDIUM(7, 56, 4000, 48), HIGH(9, 72, 9000, 96), ULTRA(12, 96, 20000, 160);
        public final int strands, segments, bundles, mist;

        Quality(int s, int g, int b, int m) { strands = s; segments = g; bundles = b; mist = m; }
    }

    /** Boden und Wand als Höhenfeld (die Szene gibt es; ohne Abfrage zählt nur der Wasserspiegel). */
    public interface Ground { double height(double x, double z); }

    /** Stand des Packages (für App und Prüfung). */
    public static final String STAND = "Engine (Phase 7: Lauf über Fels)";
    /** Proben je Strähne im Lauf über Fels (Spalten der Tabelle auf der Grafikkarte). */
    public static final int PATH = 192;
    /** Höchstzahl der Strähnen (so viele Felder hat {@code gl/fall.glsl}). */
    public static final int MAX_STRANDS = 16;

    public Quality quality = Quality.MEDIUM;
    /** Zufall: gleicher Wert, gleiches Bild. */
    public long seed = 1;
    /** Erdbeschleunigung (m/s²). */
    public double gravity = 9.81;

    // ------------------------------------------------------------------ Eingänge
    /** Kante: Anfang und Ende (x, y, z; y nach oben). */
    public double[] lipA = {-20, 250, 0}, lipB = {20, 250, 0};
    /** Richtung, in die das Wasser die Kante verlässt (Kompass, 0 = Nord = −z, 90 = Ost = +x). */
    public double headingDeg = 180;
    /** Abfluss über die ganze Kante (m³/s). */
    public double discharge = 2.0;
    /** Wind: Geschwindigkeit (m/s) und Richtung, aus der er kommt (Kompass). */
    public double windSpeed = 0, windFromDeg = 270;
    /** Lufttemperatur (°C). */
    public double temperature = 10;
    /** Wasserspiegel unten (y); NaN: kein Wasser, nur Boden. */
    public double waterLevel = 0;
    /** Boden und Wand (oder null). */
    public Ground ground;
    /** Name (für Anzeigen; der Kern braucht ihn nicht). */
    public String name = "Fall";
    /** Zahl der Strähnen fest (1 bis {@link #MAX_STRANDS}), 0 = nach Breite und Stufe. Die Sieben Schwestern haben sieben, auf jeder Stufe. */
    public int strandCount;
    /** Breite der Strähnen als Anteil ihres Platzes auf der Kante (im Mittel 0,65 · dieser Wert); kleiner gibt dünne Fäden mit Lücken. */
    public double strandShare = 1;

    // ------------------------------------------------------------------ Lauf über Fels (seit Phase 7)
    /** Weg über den Fels rechnen statt frei von der Kante zu fallen (braucht {@link #ground}). */
    public boolean overRock;
    /** Abstand des Wassers über dem Fels (m): Film, Gischt, und damit es nicht im Fels steckt. */
    public double standoff = 0.8;
    /**
     * Gleiten über rauen Fels (m/s) [G]: die Reibung wächst mit dem Andruck (cos θ), darum gleitet das Wasser am Hang mit
     * θ mit slideTerminal · √(tan θ) (45°: 9 m/s, 72°: 16 m/s); an der senkrechten Wand streift es nur und fällt fast frei.
     */
    public double slideTerminal = 9;
    /** Anteil der Geschwindigkeit längs des Hangs, der einen senkrechten Aufschlag übersteht [G]; streifend bleibt mehr (1 − (1 − k) · sin φ). */
    public double impactKeep = 0.55;
    /** Zeitschritt der Rechnung (s). */
    public double courseStep = 0.02;
    /**
     * Querfreiheit 0..1 [G]: wie stark das Wasser auf dem Fels der Neigung quer zu seiner Flussrichtung ({@link #headingDeg})
     * folgt. 1 ist reine Mechanik (das Wasser sammelt sich in Rinnen); kleiner hält jede Strähne auf ihrer Linie, wie
     * Wasser, das in dünnen Fäden über eine raue Wand läuft und Rinnen kleiner als das Raster nicht sieht.
     */
    public double lateralFreedom = 1;

    // ------------------------------------------------------------------ Stoffwerte [G]
    /** Endgeschwindigkeit des geschlossenen Strahls (m/s) [G]: dick, wenig Reibung. */
    public double coreTerminal = 60;
    /** Endgeschwindigkeit der Tropfenpakete im Mittel (m/s) [G]: Pakete aus vielen Tropfen fallen schneller als ein Tropfen (3 mm: 8 m/s), Bündel streuen ± 30 %. */
    public double dropTerminal = 16;
    /** Seitliche Streuung der Pakete am Zerfall (m/s, eine Standardabweichung) [G]. */
    public double dropSpread = 0.45;
    /** Zerfallshöhe: L_b = breakupScale · √(Dicke am Abriss) (m) [G], je Strähne ± 30 %. */
    public double breakupScale = 110;
    /**
     * Maßstab der Gischt (seit Geiranger Phase 10): Größe der Bündel und Menge des Nebels. 1 für einen Fall, der frei
     * in ein Becken stürzt; kleiner für Kaskaden in einem Fluss, deren Wasser über Fels schäumt, statt zu zerstäuben
     * (ohne das lag der Storfossen als Schleier mit Ballen über dem Ufer).
     */
    public double sprayScale = 1;

    // ------------------------------------------------------------------ Ergebnis von update()
    /** Eine Strähne: Teil des Abflusses, der als eigener Strahl abreißt. */
    public static final class Strand {
        /** Lage auf der Kante 0..1, Fuß der Strähne auf der Kante (x, y, z), Breite (m), Anteil am Abfluss. */
        public double u, x, y, z, width, share;
        /** Abfluss je m (m²/s), Dicke (m) und Geschwindigkeit (m/s) am Abriss. */
        public double q, depth, v0;
        /** Zerfallshöhe (m unter der Kante) und -zeit (s). */
        public double breakup, tBreak;
        /** Endgeschwindigkeit der Pakete dieser Strähne (m/s). */
        public double vtDrop;
        /** Zeit bis zum Aufprall (s), Ort und Geschwindigkeit dort, auf Wasser? */
        public double tImpact, ix, iy, iz, impactSpeed;
        public boolean onWater;
        /** Startwert der Strähne (für Bild und Bündel). */
        public int seed;
        /**
         * Lauf über Fels: {@link #PATH} Proben in gleichen Zeitschritten von 0 bis {@link #tImpact}, je Probe x, y, z und
         * die Geschwindigkeit (m/s), negativ, wo das Wasser auf dem Fels liegt. Ohne Lauf über Fels null.
         */
        public float[] path;
        /** Lauf über Fels: Anteil der Zeit auf dem Fels, Aufschläge, längster freier Fall (m), Länge des Wegs (m). */
        public double rockShare, longestFree, length;
        public int landings;
    }

    /** Ein Tropfenpaket im Schleier (Eingang der Grafikkarte; die Bahn rechnet der Scheitelshader). */
    public static final class Bundle {
        /** Strähne, Phase 0..1 im Umlauf, seitliche Geschwindigkeit längs der Kante und in Flussrichtung (m/s). */
        public int strand;
        public double phase, lateral, forward;
        /** Endgeschwindigkeit (m/s), Größe (m), Lebensdauer bis zum Aufprall (s), Zufall 0..1. */
        public double vt, size, life, rnd;
    }

    public final List<Strand> strands = new ArrayList<>();
    public final List<Bundle> bundles = new ArrayList<>();
    /** Breite der Kante (m), Abfluss je m (m²/s), Grenztiefe (m). */
    public double width, qPerMeter, critical;
    /** Fallhöhe (Kante bis Aufprall, Mittel der Strähnen) und Fallzeit (s). */
    public double height, fallTime;
    private long stamp;

    // ------------------------------------------------------------------ Richtungen

    /** Einheitsvektor der Flussrichtung (waagerecht). */
    public double[] dir() {
        double a = Math.toRadians(headingDeg);
        return new double[]{Math.sin(a), 0, -Math.cos(a)};
    }

    /** Wind als Vektor (m/s, wohin er weht). */
    public double[] wind() {
        double a = Math.toRadians(windFromDeg);
        return new double[]{-Math.sin(a) * windSpeed, 0, Math.cos(a) * windSpeed};
    }

    /** Einheitsvektor längs der Kante (waagerecht, von A nach B). */
    public double[] along() {
        double dx = lipB[0] - lipA[0], dz = lipB[2] - lipA[2], l = Math.hypot(dx, dz);
        return l < 1e-9 ? new double[]{1, 0, 0} : new double[]{dx / l, 0, dz / l};
    }

    /** Eisanteil 0..1 aus der Temperatur (stationär) [G]: ab 0 °C Eis am Rand, bei −15 °C steht der Fall. */
    public double ice() { return smooth(0, -15, temperature); }

    // ------------------------------------------------------------------ geschlossene Form

    /** ln cosh(x), auch für große x stabil. */
    public static double lnCosh(double x) {
        double a = Math.abs(x);
        return a + Math.log1p(Math.exp(-2 * a)) - Math.log(2);
    }

    /** ln sinh(x) für x > 0, stabil. */
    public static double lnSinh(double x) { return x + Math.log1p(-Math.exp(-2 * x)) - Math.log(2); }

    /**
     * Senkrechter Fall mit quadratischer Reibung: Strecke nach unten (m) und Geschwindigkeit (m/s, nach unten) nach der
     * Zeit t, Start mit v0 nach unten (0 ≤ v0), Endgeschwindigkeit vt.
     */
    public static double[] drop(double vt, double v0, double t, double g) {
        double s = g * t / vt;
        if (v0 < vt * 0.9999) {
            double a = atanh(v0 / vt);
            return new double[]{vt * vt / g * (lnCosh(a + s) - lnCosh(a)), vt * Math.tanh(a + s)};
        }
        double b = atanh(vt / Math.max(v0, vt * 1.0001));   // schneller als vt: bremst auf vt ab
        return new double[]{vt * vt / g * (lnSinh(b + s) - lnSinh(b)), vt / Math.tanh(b + s)};
    }

    /** Waagerechter Weg (m) mit Reibung: Relativgeschwindigkeit r0 zum Wind nimmt ab wie r0 / (1 + k r0 t). */
    public static double slide(double r0, double vt, double t, double g) {
        double k = g / (vt * vt);
        return r0 < 1e-9 ? 0 : Math.log1p(k * r0 * t) / k;
    }

    /** Relativgeschwindigkeit nach der Zeit t (gleiche Richtung). */
    static double slideSpeed(double r0, double vt, double t, double g) { return r0 / (1 + g / (vt * vt) * r0 * t); }

    static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }

    /**
     * Ort eines Pakets (x, y, z) und seine Geschwindigkeit (Betrag) zur Flugzeit t: bis zum Zerfall als Strahl, danach
     * als Tropfenpaket mit der Endgeschwindigkeit vt und der seitlichen Zusatzgeschwindigkeit (lat längs der Kante, fwd
     * in Flussrichtung). Wie {@code fallParcel} in {@code gl/fall.glsl}.
     */
    public double[] parcel(Strand s, double t, double vt, double lat, double fwd) {
        if (s.path != null) return course(s, t, lat, fwd);
        double[] d = dir(), w = wind(), al = along();
        double g = gravity, vc = coreTerminal;
        double rx = s.v0 * d[0] - w[0], rz = s.v0 * d[2] - w[2], r0 = Math.hypot(rx, rz);
        double hx = r0 > 1e-9 ? rx / r0 : 0, hz = r0 > 1e-9 ? rz / r0 : 0;
        double t1 = Math.min(t, s.tBreak);
        double[] v1 = drop(vc, 0, t1, g);
        double sl = slide(r0, vc, t1, g);
        double x = s.x + w[0] * t1 + hx * sl, z = s.z + w[2] * t1 + hz * sl, y = s.y - v1[0];
        double rb = slideSpeed(r0, vc, t1, g);
        if (t <= s.tBreak) {
            double hxv = w[0] + hx * rb, hzv = w[2] + hz * rb;
            return new double[]{x, y, z, Math.sqrt(hxv * hxv + hzv * hzv + v1[1] * v1[1])};
        }
        double tau = t - s.tBreak;
        // am Zerfall: Relativgeschwindigkeit plus Streuung
        double qx = hx * rb + al[0] * lat + d[0] * fwd, qz = hz * rb + al[2] * lat + d[2] * fwd, q0 = Math.hypot(qx, qz);
        double ux = q0 > 1e-9 ? qx / q0 : 0, uz = q0 > 1e-9 ? qz / q0 : 0;
        double[] v2 = drop(vt, v1[1], tau, g);
        double s2 = slide(q0, vt, tau, g), qs = slideSpeed(q0, vt, tau, g);
        double hxv = w[0] + ux * qs, hzv = w[2] + uz * qs;
        return new double[]{x + w[0] * tau + ux * s2, y - v2[0], z + w[2] * tau + uz * s2, Math.sqrt(hxv * hxv + hzv * hzv + v2[1] * v2[1])};
    }

    /**
     * Lauf über Fels: Ort und Geschwindigkeit eines Pakets zur Zeit t aus der Tabelle der Strähne, Zeile für Zeile wie
     * {@code fallCourse} in {@code gl/fall.glsl} (in float). Die Pakete streuen quer zum Weg ({@code lat}, m/s) und vom
     * Fels weg ({@code fwd}), höchstens 6 s lang. Die Geschwindigkeit ist negativ, wo das Wasser auf dem Fels liegt.
     */
    public double[] course(Strand s, double t, double lat, double fwd) {
        float[] P = s.path;
        float u = (float) (Math.max(0, Math.min(1, t / Math.max(1e-6, s.tImpact))) * (PATH - 1));
        int k = Math.min((int) Math.floor(u), PATH - 2);
        float f = u - k;
        int a = k * 4, b = a + 4;
        float px = P[a] + (P[b] - P[a]) * f, py = P[a + 1] + (P[b + 1] - P[a + 1]) * f, pz = P[a + 2] + (P[b + 2] - P[a + 2]) * f;
        float w = P[a + 3] + (P[b + 3] - P[a + 3]) * f;
        float tx = P[b] - P[a], ty = P[b + 1] - P[a + 1], tz = P[b + 2] - P[a + 2];
        float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl < 1e-5f) { tx = 0; ty = -1; tz = 0; } else { tx /= tl; ty /= tl; tz /= tl; }
        // quer zum Weg: waagerecht (Weg × oben), bei senkrechtem Weg die Kante
        float lx = -tz, lz = tx, ll = (float) Math.sqrt(lx * lx + lz * lz);
        double[] al = along(), d = dir();
        if (ll < 1e-3f) { lx = (float) al[0]; lz = (float) al[2]; } else { lx /= ll; lz /= ll; }
        // vom Fels weg: quer × Weg, nach oben und in Flussrichtung gedreht
        float nx = -lz * ty, ny = lz * tx - lx * tz, nz = lx * ty;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-5f) { nx = (float) d[0]; ny = 0; nz = (float) d[2]; nl = 1; }
        nx /= nl; ny /= nl; nz /= nl;
        if (ny + 0.5f * (float) (nx * d[0] + nz * d[2]) < 0) { nx = -nx; ny = -ny; nz = -nz; }
        float sp = (float) Math.min(t, 6.0);
        float ql = (float) lat * sp, qn = (float) Math.abs(fwd) * sp * 1.5f;
        return new double[]{px + lx * ql + nx * qn, py + ny * qn, pz + lz * ql + nz * qn, w};
    }

    /**
     * Rechnet den Lauf über Fels einer Strähne: Luft (Schwerkraft und quadratische Reibung gegen den Wind, Strahl bis zum
     * Zerfall, dann Tropfenpakete), Aufschlag, Gleiten mit Reibung, Abspringen; Ende im Wasser, auf flachem Grund oder
     * nach 180 s. Setzt Weg, Aufprall, Zerfallszeit und Kennwerte der Strähne.
     */
    void runCourse(Strand s) {
        double dt = courseStep, g = gravity;
        double[] dv = dir(), wv = wind();
        double x = s.x, y = s.y, z = s.z, vx = dv[0] * s.v0, vy = 0, vz = dv[2] * s.v0;
        boolean rock = false, broken = false;
        double airTop = y, tBreak = Double.NaN, onRock = 0, len = 0, still = 0;
        int max = (int) Math.ceil(180 / dt) + 2;
        float[] rec = new float[max * 5];
        int n = 0;
        double t = 0;
        boolean water = false;
        while (true) {
            double sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
            rec[n * 5] = (float) x; rec[n * 5 + 1] = (float) y; rec[n * 5 + 2] = (float) z; rec[n * 5 + 3] = (float) sp; rec[n * 5 + 4] = rock ? 1 : 0;
            n++;
            if (n >= max - 1) break;
            double px = x, py = y, pz = z;
            if (!rock) {
                double vt = broken ? s.vtDrop : coreTerminal, k = g / (vt * vt);
                // Mittelpunktregel: Schwerkraft und Reibung gegen die Luft (Wind)
                double rx = vx - wv[0], ry = vy, rz = vz - wv[2], r = Math.sqrt(rx * rx + ry * ry + rz * rz);
                double ax = -k * r * rx, ay = -g - k * r * ry, az = -k * r * rz;
                double mx = vx + ax * dt / 2, my = vy + ay * dt / 2, mz = vz + az * dt / 2;
                rx = mx - wv[0]; ry = my; rz = mz - wv[2]; r = Math.sqrt(rx * rx + ry * ry + rz * rz);
                ax = -k * r * rx; ay = -g - k * r * ry; az = -k * r * rz;
                x += mx * dt; y += my * dt; z += mz * dt;
                vx += ax * dt; vy += ay * dt; vz += az * dt;
                if (!broken && airTop - y >= s.breakup) { broken = true; tBreak = t + dt; }
                if (hitsWater(x, y, z)) { y = Math.max(y, waterLevel); water = true; t += dt; break; }
                double gy = ground.height(x, z) + standoff;
                if (y <= gy) {
                    // Aufschlag: quer zum Hang verloren, längs bleibt ein Teil
                    y = gy;
                    double[] nn = normal(x, z);
                    double vn = vx * nn[0] + vy * nn[1] + vz * nn[2], vv = Math.max(1e-9, Math.sqrt(vx * vx + vy * vy + vz * vz));
                    // je steiler der Aufschlag, desto mehr geht verloren; streifend bleibt fast alles
                    double keep = 1 - (1 - impactKeep) * Math.min(1, Math.abs(vn) / vv);
                    vx = (vx - vn * nn[0]) * keep; vy = (vy - vn * nn[1]) * keep; vz = (vz - vn * nn[2]) * keep;
                    rock = true;
                    s.landings++;
                    if (!broken) { broken = true; tBreak = t + dt; }
                    s.longestFree = Math.max(s.longestFree, airTop - y);
                }
            } else {
                double[] nn = normal(x, z);
                // Schwerkraft längs des Hangs, Reibung mit der Endgeschwindigkeit auf senkrechter Wand
                double gn = -g * nn[1];
                double gx = -gn * nn[0], gyv = -g - gn * nn[1], gz = -gn * nn[2];
                if (lateralFreedom < 1) {
                    // quer zur Flussrichtung nur ein Teil der Hangkraft (Querfreiheit)
                    double across = gx * dv[2] - gz * dv[0];
                    gx -= (1 - lateralFreedom) * across * dv[2];
                    gz += (1 - lateralFreedom) * across * dv[0];
                }
                double kk = g / (slideTerminal * slideTerminal) * Math.max(0.02, nn[1]);   // Reibung nach dem Andruck
                vx += (gx - kk * sp * vx) * dt; vy += (gyv - kk * sp * vy) * dt; vz += (gz - kk * sp * vz) * dt;
                double vn = vx * nn[0] + vy * nn[1] + vz * nn[2];
                vx -= vn * nn[0]; vy -= vn * nn[1]; vz -= vn * nn[2];
                double nx2 = x + vx * dt, nz2 = z + vz * dt;
                double fly = y + vy * dt - 0.5 * g * dt * dt, gy = ground.height(nx2, nz2) + standoff;
                if (fly > gy + 0.02) {
                    // der Fels fällt schneller weg als die Wurfbahn: das Wasser springt ab
                    rock = false;
                    x = nx2; z = nz2; y = fly; vy -= g * dt;
                    airTop = y;
                } else {
                    double v2 = Math.sqrt(vx * vx + vy * vy + vz * vz);
                    double ddx = nx2 - x, ddy = gy - y, ddz = nz2 - z, dl = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                    x = nx2; y = gy; z = nz2;
                    if (dl > 1e-9) { vx = ddx / dl * v2; vy = ddy / dl * v2; vz = ddz / dl * v2; }
                    onRock += dt;
                    if (hitsWater(x, y - standoff, z)) { water = true; t += dt; break; }
                }
                still = v2(vx, vy, vz) < 0.15 ? still + dt : 0;
                if (still > 4) { t += dt; break; }   // auf flachem Grund versickert
            }
            len += Math.sqrt((x - px) * (x - px) + (y - py) * (y - py) + (z - pz) * (z - pz));
            t += dt;
        }
        if (!rock) s.longestFree = Math.max(s.longestFree, airTop - y);
        double sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
        rec[n * 5] = (float) x; rec[n * 5 + 1] = (float) y; rec[n * 5 + 2] = (float) z; rec[n * 5 + 3] = (float) sp; rec[n * 5 + 4] = rock ? 1 : 0;
        n++;
        s.tImpact = Math.max(dt, t);
        s.tBreak = Double.isNaN(tBreak) ? s.tImpact : tBreak;
        s.breakup = Math.min(s.breakup, s.y - y);
        s.rockShare = onRock / s.tImpact;
        s.length = len;
        s.ix = x; s.iy = y; s.iz = z; s.impactSpeed = sp;
        s.onWater = water;
        // in gleiche Zeitschritte umtasten (die Aufzeichnung hat den Schritt dt, die letzte Probe liegt bei tImpact)
        s.path = new float[PATH * 4];
        for (int k = 0; k < PATH; k++) {
            double tk = s.tImpact * k / (PATH - 1), u = Math.min(n - 1.0, tk / dt);
            int i = Math.min((int) Math.floor(u), n - 2);
            double f = u - i;
            if (i < 0) { i = 0; f = 0; }
            for (int c = 0; c < 3; c++) s.path[k * 4 + c] = (float) (rec[i * 5 + c] + (rec[(i + 1) * 5 + c] - rec[i * 5 + c]) * f);
            boolean onR = (f < 0.5 ? rec[i * 5 + 4] : rec[(i + 1) * 5 + 4]) > 0.5;
            float v = (float) (rec[i * 5 + 3] + (rec[(i + 1) * 5 + 3] - rec[i * 5 + 3]) * f);
            s.path[k * 4 + 3] = onR ? -Math.max(0.01f, v) : Math.max(0.01f, v);
        }
        // Knicke an den Aufschlägen weich (1-2-1 über die Proben, Anfang und Ende bleiben), nie tiefer als der Fels
        float[] sm = s.path.clone();
        for (int k = 1; k < PATH - 1; k++)
            for (int c = 0; c < 3; c++) sm[k * 4 + c] = 0.25f * s.path[k * 4 - 4 + c] + 0.5f * s.path[k * 4 + c] + 0.25f * s.path[k * 4 + 4 + c];
        for (int k = 1; k < PATH - 1; k++) sm[k * 4 + 1] = (float) Math.max(sm[k * 4 + 1], ground.height(sm[k * 4], sm[k * 4 + 2]) + standoff);
        s.path = sm;
    }

    static double v2(double x, double y, double z) { return Math.sqrt(x * x + y * y + z * z); }

    /** Ob ein Punkt im Wasser liegt (unter dem Spiegel über Grund, der tiefer liegt). */
    boolean hitsWater(double x, double y, double z) {
        return !Double.isNaN(waterLevel) && y <= waterLevel && ground.height(x, z) < waterLevel;
    }

    /** Normale des Bodens aus dem Gefälle über 3 m. */
    double[] normal(double x, double z) {
        double e = 3;
        double gx = (ground.height(x + e, z) - ground.height(x - e, z)) / (2 * e), gz = (ground.height(x, z + e) - ground.height(x, z - e)) / (2 * e);
        double l = Math.sqrt(gx * gx + 1 + gz * gz);
        return new double[]{-gx / l, 1 / l, -gz / l};
    }

    /** Mitte einer Strähne zur Flugzeit t (ohne Streuung). */
    public double[] center(Strand s, double t) { return parcel(s, t, s.vtDrop, 0, 0); }

    /** Höhe von Boden oder Wasser an (x, z). */
    public double surface(double x, double z) {
        double h = ground == null ? Double.NEGATIVE_INFINITY : ground.height(x, z);
        return Double.isNaN(waterLevel) ? h : Math.max(h, waterLevel);
    }

    /** Zeit bis zum Aufprall eines Pakets (s): grob in 0,1-s-Schritten, dann halbiert; höchstens 120 s. */
    public double impactTime(Strand s, double vt, double lat, double fwd) {
        if (s.path != null) return s.tImpact;
        double prev = 0;
        for (double t = 0.1; t <= 120; t += 0.1) {
            double[] p = parcel(s, t, vt, lat, fwd);
            if (p[1] <= surface(p[0], p[2])) {
                double a = prev, b = t;
                for (int i = 0; i < 30; i++) {
                    double m = 0.5 * (a + b);
                    double[] q = parcel(s, m, vt, lat, fwd);
                    if (q[1] <= surface(q[0], q[2])) b = m; else a = m;
                }
                return b;
            }
            prev = t;
        }
        return 120;
    }

    // ------------------------------------------------------------------ Aufbau

    /** Rechnet Strähnen, Zerfall, Aufprall und Bündel aus den Eingängen neu (nach jeder Änderung aufrufen). */
    public void update() {
        strands.clear();
        bundles.clear();
        width = Math.max(0.5, Math.hypot(lipB[0] - lipA[0], lipB[2] - lipA[2]));
        double flow = discharge * (1 - 0.9 * ice());
        qPerMeter = flow / width;
        critical = Math.cbrt(qPerMeter * qPerMeter / gravity);
        SplittableRandom r = new SplittableRandom(seed);
        int n = strandCount > 0 ? Math.min(MAX_STRANDS, strandCount) : Math.max(1, Math.min(quality.strands, (int) Math.round(width / 3)));
        boolean course = overRock && ground != null;
        double[] wts = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) sum += wts[i] = 0.5 + r.nextDouble();
        double hs = 0, ts = 0;
        for (int i = 0; i < n; i++) {
            Strand s = new Strand();
            s.seed = r.nextInt(1 << 20);
            s.u = n == 1 ? 0.5 : (i + 0.5 + (r.nextDouble() - 0.5) * 0.5) / n;
            s.x = lipA[0] + (lipB[0] - lipA[0]) * s.u;
            s.y = lipA[1] + (lipB[1] - lipA[1]) * s.u;
            s.z = lipA[2] + (lipB[2] - lipA[2]) * s.u;
            s.share = wts[i] / sum;
            s.width = Math.max(0.3, width / n * (0.45 + 0.4 * r.nextDouble()) * strandShare);
            s.q = flow * s.share / s.width;
            double hc = Math.cbrt(s.q * s.q / gravity);
            s.depth = 0.715 * hc;
            s.v0 = s.depth > 1e-6 ? s.q / s.depth : 0;
            s.breakup = breakupScale * Math.sqrt(Math.max(s.depth, 1e-4)) * (0.7 + 0.6 * r.nextDouble());
            s.tBreak = coreTerminal / gravity * acosh(Math.exp(s.breakup * gravity / (coreTerminal * coreTerminal)));
            s.vtDrop = dropTerminal * (0.85 + 0.3 * r.nextDouble());
            if (course) runCourse(s);
            else {
                s.tImpact = impactTime(s, s.vtDrop, 0, 0);
                double[] p = center(s, s.tImpact);
                s.ix = p[0]; s.iy = p[1]; s.iz = p[2]; s.impactSpeed = Math.abs(p[3]);
                s.onWater = !Double.isNaN(waterLevel) && (ground == null || ground.height(p[0], p[2]) < waterLevel);
            }
            strands.add(s);
            hs += (s.y - s.iy) * s.share;
            ts += s.tImpact * s.share;
        }
        height = hs;
        fallTime = ts;
        // Bündel: je Strähne nach Abfluss verteilt, Lebensdauer je Bündel bis zum Aufprall
        int nb = quality.bundles;
        for (int i = 0; i < nb; i++) {
            Bundle b = new Bundle();
            double pick = r.nextDouble(), acc = 0;
            int k = 0;
            for (; k < n - 1; k++) { acc += strands.get(k).share; if (pick < acc) break; }
            Strand s = strands.get(k);
            b.strand = k;
            b.phase = r.nextDouble();
            b.lateral = gauss(r) * dropSpread * sprayScale + (r.nextDouble() - 0.5) * s.width * 0.3;
            b.forward = gauss(r) * dropSpread * 0.5;
            b.vt = s.vtDrop * (0.7 + 0.6 * r.nextDouble());
            b.size = (2.5 + 4.5 * r.nextDouble() * Math.sqrt(Math.min(4, s.q / 0.05 + 0.25))) * sprayScale;
            b.rnd = r.nextDouble();
            b.life = course ? s.tImpact : impactTime(s, b.vt, b.lateral, b.forward);
            bundles.add(b);
        }
        stamp++;
    }

    /**
     * Eine Kopie mit denselben Eingängen und Stoffwerten (ohne Ergebnis; {@link #update()} danach). Für Szenen, die einen
     * Fall in einem anderen Faden neu rechnen und dann austauschen, während die Grafikkarte noch den alten zeichnet.
     */
    public Fall copy() {
        Fall f = new Fall();
        f.quality = quality; f.seed = seed; f.gravity = gravity;
        f.lipA = lipA.clone(); f.lipB = lipB.clone(); f.headingDeg = headingDeg; f.discharge = discharge;
        f.windSpeed = windSpeed; f.windFromDeg = windFromDeg; f.temperature = temperature; f.waterLevel = waterLevel; f.ground = ground;
        f.name = name; f.strandCount = strandCount; f.strandShare = strandShare;
        f.overRock = overRock; f.standoff = standoff; f.slideTerminal = slideTerminal; f.impactKeep = impactKeep; f.courseStep = courseStep; f.lateralFreedom = lateralFreedom;
        f.coreTerminal = coreTerminal; f.dropTerminal = dropTerminal; f.dropSpread = dropSpread; f.breakupScale = breakupScale; f.sprayScale = sprayScale;
        return f;
    }

    /** Zähler der Neuberechnungen (die Grafikkarte lädt neu, wenn er sich ändert). */
    public long stamp() { return stamp; }

    static double acosh(double x) { return Math.log(x + Math.sqrt(x * x - 1)); }

    static double sq(double x) { return x * x; }

    static double gauss(SplittableRandom r) {
        double u = Math.max(1e-12, r.nextDouble()), v = r.nextDouble();
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    }

    // ------------------------------------------------------------------ Ausgänge

    /** Fallzeit (s) bis {@code drop} m unter der Kante für die Mitte der Strähne s (halbiert). */
    public double timeAtDrop(Strand s, double drop) {
        double a = 0, b = Math.max(s.tImpact, 0.01);
        for (int i = 0; i < 50; i++) {
            double m = 0.5 * (a + b);
            if (s.y - center(s, m)[1] < drop) a = m; else b = m;
        }
        return 0.5 * (a + b);
    }

    /**
     * Breite des Schleiers (m) {@code drop} m unter der Kante: Abstand der äußeren Pakete (zwei Standardabweichungen
     * Streuung) der äußeren Strähnen, längs der Kante gemessen.
     */
    public double veilWidth(double drop) {
        if (strands.isEmpty()) update();
        double[] al = along();
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (Strand s : strands) {
            double t = timeAtDrop(s, drop);
            for (int sg = -1; sg <= 1; sg += 2) {
                double[] p = parcel(s, t, s.vtDrop, sg * 2 * dropSpread, 0);
                double u = p[0] * al[0] + p[2] * al[2] + sg * s.width / 2;
                lo = Math.min(lo, u);
                hi = Math.max(hi, u);
            }
        }
        return hi - lo;
    }

    /** Ringwellen für das Wasser: je Strähne mit Aufprall auf Wasser {x, z, Höhe (m), Periode (s)} [G]. */
    public List<double[]> ripples() {
        List<double[]> out = new ArrayList<>();
        for (Strand s : strands) {
            if (!s.onWater) continue;
            double e = discharge * s.share * s.impactSpeed * s.impactSpeed;
            out.add(new double[]{s.ix, s.iz, Math.min(0.4, 0.015 * Math.sqrt(e)), 0.8 + 0.4 * Math.sqrt(Math.max(0.1, s.width))});
        }
        return out;
    }

    /**
     * Nasse Linie für die Wand: Punkte {x, z, Breite (m)} entlang der Bahn alle {@code step} m Fallhöhe, wo der
     * Schleier höchstens 6 m vor dem Boden steht, dazu der Aufprall (für {@code cliff} oder die Szene).
     */
    public List<double[]> wetLine(double step) {
        List<double[]> out = new ArrayList<>();
        for (Strand s : strands) {
            if (s.path != null) {
                // Lauf über Fels: wo das Wasser auf dem Fels liegt oder höchstens 8 m davor fliegt, alle step m des Wegs
                double acc = step;
                for (int k = 0; k < PATH; k++) {
                    if (k > 0) acc += Math.sqrt(sq(s.path[k * 4] - s.path[k * 4 - 4]) + sq(s.path[k * 4 + 1] - s.path[k * 4 - 3]) + sq(s.path[k * 4 + 2] - s.path[k * 4 - 2]));
                    if (acc < step) continue;
                    double px = s.path[k * 4], py = s.path[k * 4 + 1], pz = s.path[k * 4 + 2];
                    boolean onR = s.path[k * 4 + 3] < 0;
                    if (onR || py - ground.height(px, pz) < 8) { out.add(new double[]{px, pz, s.width * 1.5 + (onR ? 2 : 4)}); acc = 0; }
                }
                out.add(new double[]{s.ix, s.iz, s.width * 2 + 4});
                continue;
            }
            for (double h = 0; h <= s.y - s.iy; h += step) {
                double[] p = center(s, timeAtDrop(s, h));
                double gap = ground == null ? Double.POSITIVE_INFINITY : nearestWall(p);
                if (gap <= 6) out.add(new double[]{p[0], p[2], s.width + h * 0.08});
            }
            out.add(new double[]{s.ix, s.iz, s.width * 2 + 4});
        }
        return out;
    }

    /** Waagerechter Abstand zur Wand (m): erste Stelle hinter dem Paket (gegen die Flussrichtung), die höher steht. */
    double nearestWall(double[] p) {
        double[] d = dir();
        for (double k = 0; k <= 12; k += 1) if (ground.height(p[0] - d[0] * k, p[2] - d[2] * k) >= p[1]) return k;
        return Double.POSITIVE_INFINITY;
    }

    /** Schallpegel (dB) in {@code dist} m Abstand [G]: rund ein Millionstel der Fallleistung als Schall, kugelförmig. */
    public double soundLevel(double dist) {
        double p = 0;
        for (Strand s : strands) p += 1000 * gravity * discharge * s.share * (1 - 0.9 * ice()) * Math.max(0, s.y - s.iy);
        double lw = 10 * Math.log10(Math.max(1e-9, p * 1e-6 / 1e-12));
        return lw - 20 * Math.log10(Math.max(1, dist)) - 11;
    }

    /** Nebel am Fuß 0..1 [G]: wächst mit Abfluss und Aufprallgeschwindigkeit. */
    public double mist() {
        double e = 0;
        for (Strand s : strands) e += discharge * s.share * (1 - 0.9 * ice()) * s.impactSpeed * s.impactSpeed;
        return (1 - Math.exp(-e / 800)) * Math.min(1, sprayScale);
    }

    public static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Fallzeit im luftleeren Raum für die Höhe h (m): √(2h/g); mit Reibung fällt das Wasser langsamer. */
    public double freeFallSeconds(double h) { return Math.sqrt(2 * Math.max(0, h) / gravity); }

    /** Lauf über Fels: „über Fels 64 % der Zeit, 3 Aufschläge, freier Fall bis 120 m, Weg 610 m“ (Mittel nach Abfluss), sonst leer. */
    public String courseLine() {
        if (strands.isEmpty() || strands.get(0).path == null) return "";
        double rs = 0, lf = 0, ln = 0, la = 0;
        for (Strand s : strands) { rs += s.rockShare * s.share; lf = Math.max(lf, s.longestFree); ln += s.length * s.share; la += s.landings * s.share; }
        return String.format(Locale.GERMAN, "über Fels %.0f %% der Zeit, %.0f Aufschläge, freier Fall bis %.0f m, Weg %.0f m", rs * 100, la, lf, ln);
    }

    /** „Fall · Engine (Phase 6) · MEDIUM · 7 Strähnen … “ */
    public String describe() {
        if (strands.isEmpty()) return "Fall · " + STAND + " · " + quality + " · seed " + seed;
        double dm = 0, vm = 0, bm = 0, im = 0;
        for (Strand s : strands) { dm += s.depth * s.share; vm += s.v0 * s.share; bm += s.breakup * s.share; im += s.impactSpeed * s.share; }
        String c = courseLine();
        return String.format(Locale.GERMAN, "Fall · %s · %s · %d Strähnen, %d Bündel · Kante %.0f m, %.2f m³/s%s · Abriss %.2f m dick, %.1f m/s · Zerfall nach %.0f m · "
                        + "%.0f m in %.1f s (frei %.1f s) · Schleier unten %.0f m · Aufprall %.0f m/s%s · %.0f dB in 100 m%s",
                STAND, quality, strands.size(), bundles.size(), width, discharge, ice() > 0.01 ? String.format(Locale.GERMAN, " (Eis %.0f %%)", ice() * 100) : "",
                dm, vm, bm, height, fallTime, freeFallSeconds(height), veilWidth(Math.max(1, height - 1)), im, strands.get(0).onWater ? " ins Wasser" : " auf Fels",
                soundLevel(100), c.isEmpty() ? "" : " · " + c);
    }
}
