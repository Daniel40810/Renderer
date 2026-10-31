package com.dan.lamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Alle Leuchten einer Szene (seit Timmendorf Phase 11, F18 A): Reihen längs Wegen, die Seebrücke, Laternen einer
 * Altstadt; schalten gemeinsam nach Dämmerung und Uhrzeit; Abfrage der Beleuchtungsstärke über ein Gitter (nur Leuchten,
 * deren Licht dort noch über {@link #cutoffLux} liegt); Auswahl der wichtigsten für die Grafikkarte.
 */
public final class Lamps {
    /** Qualitätsstufen (Paketvertrag Punkt 7): Leuchten, die die Grafikkarte je Bild rechnet. */
    public enum Quality {
        LOW(32), MEDIUM(64), HIGH(128), ULTRA(256);
        public final int gpuLamps;
        Quality(int n) { gpuLamps = n; }
    }

    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Ab welcher Beleuchtungsstärke eine Leuchte nicht mehr zählt (lx). */
    public double cutoffLux = 0.05;
    /** Gitterweite (m). */
    public static final double CELL = 25;
    private final List<Lamp> lamps = new ArrayList<>();
    private final Map<Long, List<Lamp>> grid = new HashMap<>();
    private boolean dirty = true;

    public List<Lamp> all() { return lamps; }
    public int size() { return lamps.size(); }

    public Lamp add(Lamp l) {
        l.id = lamps.size();
        lamps.add(l);
        dirty = true;
        return l;
    }

    /** Alle schalten und anlaufen lassen (Umgebungslicht lx, Uhrzeit h). */
    public void step(double dt, double ambientLux, double hour) {
        for (Lamp l : lamps) l.step(dt, ambientLux, hour);
    }

    /** Reichweite einer Leuchte (m): wo ihre höchste Lichtstärke auf {@link #cutoffLux} fällt. */
    public double reach(Lamp l) { return Math.sqrt(l.peakCandela() / cutoffLux); }

    private void index() {
        if (!dirty) return;
        grid.clear();
        for (Lamp l : lamps) {
            double r = reach(l);
            int x0 = cell(l.x - r), x1 = cell(l.x + r), z0 = cell(l.z - r), z1 = cell(l.z + r);
            for (int i = x0; i <= x1; i++) for (int k = z0; k <= z1; k++) grid.computeIfAbsent(key(i, k), q -> new ArrayList<>()).add(l);
        }
        dirty = false;
    }

    /** Nach dem Verschieben von Leuchten aufrufen. */
    public void moved() { dirty = true; }

    static int cell(double v) { return (int) Math.floor(v / CELL); }
    static long key(int i, int k) { return ((long) i << 32) ^ (k & 0xffffffffL); }

    /** Leuchten, die am Ort (x, z) etwas beitragen. */
    public List<Lamp> near(double x, double z) {
        index();
        List<Lamp> l = grid.get(key(cell(x), cell(z)));
        return l == null ? List.of() : l;
    }

    /** Beleuchtungsstärke (lx, lineares RGB) am Ort p mit Normale n aus allen Leuchten in Reichweite (ohne Schatten). */
    public double[] illuminance(double px, double py, double pz, double nx, double ny, double nz) {
        double[] e = new double[3];
        for (Lamp l : near(px, pz)) {
            double v = l.illuminance(px, py, pz, nx, ny, nz);
            if (v <= 0) continue;
            double[] c = l.rgb();
            e[0] += v * c[0]; e[1] += v * c[1]; e[2] += v * c[2];
        }
        return e;
    }

    /** Waagerechte Beleuchtungsstärke (lx, Helligkeit) am Boden. */
    public double groundLux(double px, double py, double pz) {
        double e = 0;
        for (Lamp l : near(px, pz)) e += l.illuminance(px, py, pz, 0, 1, 0);
        return e;
    }

    /**
     * Die n Leuchten, die das Auge am meisten angehen: brennend, nach höchster Lichtstärke durch Abstand² (so zählt die
     * helle Leuchte in 200 m mehr als der Poller in 50 m).
     */
    public List<Lamp> important(double ex, double ey, double ez, int n) {
        List<Lamp> lit = new ArrayList<>();
        for (Lamp l : lamps) if (l.output() > 0) lit.add(l);
        lit.sort((a, b) -> Double.compare(score(b, ex, ey, ez), score(a, ex, ey, ez)));
        return lit.size() > n ? new ArrayList<>(lit.subList(0, n)) : lit;
    }

    static double score(Lamp l, double ex, double ey, double ez) {
        double dx = l.x - ex, dy = l.y - ey, dz = l.z - ez;
        return l.peakCandela() * l.output() / Math.max(25, dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------ Bausteine

    /**
     * Eine Reihe Leuchten von (x0, z0) nach (x1, z1) im Abstand spacing (m), Lichtpunkthöhe h über ground; die Straße
     * liegt rechts der Laufrichtung, wenn side = 1, links bei −1. Gibt die Zahl.
     */
    public int row(double x0, double z0, double x1, double z1, double ground, double spacing, double h, Lamp.Type t, Lamp.Distribution d, double lumen, int side) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
        if (len < 1e-6) return 0;
        int n = Math.max(1, (int) Math.floor(len / spacing) + 1);
        double run = Math.toDegrees(Math.atan2(dx, -dz));
        for (int i = 0; i < n; i++) {
            double f = n == 1 ? 0.5 : (double) i / (n - 1);
            add(new Lamp(x0 + dx * f, ground + h, z0 + dz * f, t, d, lumen).facing(run + 90 * side));
        }
        return n;
    }

    /**
     * Eine Seebrücke von (x0, z0) nach (x1, z1) auf Deckhöhe deck: Poller beidseits alle 8 m (LED 3000 K, nach unten,
     * 1 m hoch), dazu alle 32 m Mastleuchten in der Mitte (4,5 m, rundum) und am Kopf vier Laternen [G].
     */
    public int pier(double x0, double z0, double x1, double z1, double deck, double width) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz), ux = dx / len, uz = dz / len, nx = -uz, nz = ux;
        int n = 0;
        for (int side = -1; side <= 1; side += 2) {
            double ox = nx * side * (width / 2 - 0.3), oz = nz * side * (width / 2 - 0.3);
            n += row(x0 + ox, z0 + oz, x1 + ox, z1 + oz, deck, 8, 1.0, Lamp.Type.LED_3000K, Lamp.Distribution.DOWN, 400, side);
        }
        n += row(x0, z0, x1 - ux * 6, z1 - uz * 6, deck, 32, 4.5, Lamp.Type.LED_3000K, Lamp.Distribution.ROUND, 3000, 1);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            add(new Lamp(x1 + Math.cos(a) * width * 0.6, deck + 3.5, z1 + Math.sin(a) * width * 0.6, Lamp.Type.LED_3000K, Lamp.Distribution.ROUND, 2500));
            n++;
        }
        return n;
    }

    /**
     * Gaslaternen einer Altstadt längs eines Wegs (für Heidelberg und den Kneiphof): Glühstrumpf, Abstand 25–35 m,
     * 3,8 m, rundum; einzelne flackern [G].
     */
    public int gasLanterns(double[] xz, double ground) {
        Random r = new Random(seed ^ 0x9a5L);
        int n = 0;
        for (int i = 0; i + 3 < xz.length; i += 2) {
            double x0 = xz[i], z0 = xz[i + 1], x1 = xz[i + 2], z1 = xz[i + 3], len = Math.hypot(x1 - x0, z1 - z0);
            for (double s = 0; s < len; s += 25 + 10 * r.nextDouble()) {
                Lamp l = add(new Lamp(x0 + (x1 - x0) * s / len, ground + 3.8, z0 + (z1 - z0) * s / len, Lamp.Type.GAS, Lamp.Distribution.ROUND, 1200));
                l.area = 0.02;
                l.flicker = r.nextDouble() < 0.1 ? 0.15 : 0.02;
                n++;
            }
        }
        return n;
    }

    public String describe() {
        int on = 0;
        double w = 0;
        for (Lamp l : lamps) if (l.output() > 0) { on++; w += l.watts() * l.output(); }
        return String.format(java.util.Locale.GERMAN, "Leuchten: %d, %d an, %,.0f W", lamps.size(), on, w);
    }
}
