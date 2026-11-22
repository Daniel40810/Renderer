package com.dan.core.gitter;

import com.dan.core.raum.Erde;
import com.dan.core.raum.Utm;

/**
 * Ein Rechteckgitter (Werkbuch HalligHooge Teil A, 5.4): geographisch (Grad auf GRS80) oder in UTM (Meter). Zellen
 * zählen von Südwest: i nach Osten, j nach Norden, {@code index = i + nx · j}. Gitter werden verschachtelt
 * ({@link #passtIn}); ein adaptives Gitter gibt es nicht, das Feine kommt über {@link Subgrid}.
 * <p>
 * In UTM ist die Fläche einer Zelle die Fläche im Gitter (dx · dy); in der Natur ist sie um das Quadrat des Maßstabs
 * kleiner ({@code raum.Ebene}).
 */
public final class Gitter {
    public enum Lage { GEOGRAFISCH, UTM }

    public final String name;
    public final Lage lage;
    /** UTM-Zone (0 bei geographischer Lage). */
    public final int zone;
    /** Südwestecke und Zellgröße: Grad bei geographischer Lage, Meter in UTM. */
    public final double x0, y0, dx, dy;
    public final int nx, ny;

    private Gitter(String name, Lage lage, int zone, double x0, double y0, double dx, double dy, int nx, int ny) {
        if (nx < 1 || ny < 1 || !(dx > 0) || !(dy > 0)) throw new IllegalArgumentException("Gitter " + name + ": " + nx + " × " + ny + ", Zelle " + dx + " × " + dy);
        this.name = name;
        this.lage = lage;
        this.zone = zone;
        this.x0 = x0;
        this.y0 = y0;
        this.dx = dx;
        this.dy = dy;
        this.nx = nx;
        this.ny = ny;
    }

    /** Gitter in UTM mit quadratischen Zellen. */
    public static Gitter utm(String name, int zone, double ost0, double nord0, double zelle, int nx, int ny) {
        return new Gitter(name, Lage.UTM, zone, ost0, nord0, zelle, zelle, nx, ny);
    }

    /** Gitter in UTM über ein Rechteck; die Seiten müssen ganze Zellen sein. */
    public static Gitter utmBereich(String name, int zone, double ost0, double nord0, double ost1, double nord1, double zelle) {
        double nx = (ost1 - ost0) / zelle, ny = (nord1 - nord0) / zelle;
        if (Math.abs(nx - Math.rint(nx)) > 1e-9 || Math.abs(ny - Math.rint(ny)) > 1e-9)
            throw new IllegalArgumentException("Gitter " + name + ": das Rechteck ist kein Vielfaches der Zelle " + zelle + " m");
        return utm(name, zone, ost0, nord0, zelle, (int) Math.rint(nx), (int) Math.rint(ny));
    }

    /** Geographisches Gitter: Südwestecke und Zellgröße in Grad. */
    public static Gitter geografisch(String name, double west, double sued, double dLon, double dLat, int nx, int ny) {
        return new Gitter(name, Lage.GEOGRAFISCH, 0, west, sued, dLon, dLat, nx, ny);
    }

    /**
     * Gitter in UTM, das ein Rechteck in Grad umschließt, auf volle Vielfache von {@code raster} Metern gelegt (wie
     * {@code utm_huelle} im Werkzeug für das Kartenmaterial).
     */
    public static Gitter utmHuelle(String name, int zone, double west, double sued, double ost, double nord, double zelle, double raster) {
        double e0 = Double.MAX_VALUE, n0 = Double.MAX_VALUE, e1 = -Double.MAX_VALUE, n1 = -Double.MAX_VALUE;
        for (int k = 0; k <= 40; k++) {
            double a = k / 40.0;
            double[][] p = {Utm.vorwaerts(west + a * (ost - west), sued, zone), Utm.vorwaerts(west + a * (ost - west), nord, zone),
                    Utm.vorwaerts(west, sued + a * (nord - sued), zone), Utm.vorwaerts(ost, sued + a * (nord - sued), zone)};
            for (double[] q : p) {
                e0 = Math.min(e0, q[0]);
                e1 = Math.max(e1, q[0]);
                n0 = Math.min(n0, q[1]);
                n1 = Math.max(n1, q[1]);
            }
        }
        return utmBereich(name, zone, Math.floor(e0 / raster) * raster, Math.floor(n0 / raster) * raster, Math.ceil(e1 / raster) * raster,
                Math.ceil(n1 / raster) * raster, zelle);
    }

    public int zellen() { return nx * ny; }
    public int index(int i, int j) { return i + nx * j; }
    /** Mitte der Zelle i in x (Grad oder m). */
    public double x(int i) { return x0 + (i + 0.5) * dx; }
    /** Mitte der Zelle j in y (Grad oder m). */
    public double y(int j) { return y0 + (j + 0.5) * dy; }
    public double x1() { return x0 + nx * dx; }
    public double y1() { return y0 + ny * dy; }
    /** Spalte der Zelle, in der x liegt (kann außerhalb 0..nx−1 liegen). */
    public int i(double x) { return (int) Math.floor((x - x0) / dx); }
    /** Zeile der Zelle, in der y liegt. */
    public int j(double y) { return (int) Math.floor((y - y0) / dy); }
    public boolean enthaelt(double x, double y) { return x >= x0 && x < x1() && y >= y0 && y < y1(); }

    /** Fläche einer Zelle der Zeile j in m² (geographisch: auf dem Ellipsoid; UTM: im Gitter). */
    public double flaeche(int j) { return lage == Lage.UTM ? dx * dy : Erde.zonenflaeche(y0 + j * dy, y0 + (j + 1) * dy, dx); }

    /** Breite einer Zelle der Zeile j in m (Ost–West, in der Mitte der Zelle). */
    public double breite(int j) { return lage == Lage.UTM ? dx : dx * Erde.meterJeGradLaenge(y(j)); }

    /** Höhe einer Zelle der Zeile j in m (Süd–Nord). */
    public double hoehe(int j) { return lage == Lage.UTM ? dy : dy * Erde.meterJeGradBreite(y(j)); }

    /** Gesamtfläche in m². */
    public double flaeche() {
        double a = 0;
        for (int j = 0; j < ny; j++) a += flaeche(j) * nx;
        return a;
    }

    /** {Länge, Breite} in Grad zu einem Punkt des Gitters (x, y in Grad oder m). */
    public double[] grad(double x, double y) { return lage == Lage.UTM ? Utm.rueckwaerts(x, y, zone) : new double[] {x, y}; }

    /** Punkt des Gitters (x, y) zu Länge und Breite in Grad. */
    public double[] punkt(double lonGrad, double latGrad) { return lage == Lage.UTM ? Utm.vorwaerts(lonGrad, latGrad, zone) : new double[] {lonGrad, latGrad}; }

    /** Punkt dieses Gitters zu einem Punkt eines anderen (über Länge und Breite, wenn die Lage verschieden ist). */
    public double[] von(Gitter anderes, double x, double y) {
        if (anderes.lage == lage && anderes.zone == zone) return new double[] {x, y};
        double[] g = anderes.grad(x, y);
        return punkt(g[0], g[1]);
    }

    /** Dasselbe Gitter (Lage, Ecke, Zelle, Größe)? Der Name zählt nicht. */
    public boolean gleich(Gitter g) {
        return g != null && g.lage == lage && g.zone == zone && g.nx == nx && g.ny == ny && nah(g.x0, x0, dx) && nah(g.y0, y0, dy) && nah(g.dx, dx, dx) && nah(g.dy, dy, dy);
    }

    /**
     * Liegt dieses Gitter als Schachtel in einem gröberen: gleiche Lage, ganzzahliges Verhältnis der Zellen, die Ecken
     * auf Zellgrenzen des groben, ganz innerhalb? Gibt das Verhältnis zurück, sonst 0.
     */
    public int passtIn(Gitter grob) {
        if (grob.lage != lage || grob.zone != zone) return 0;
        double v = grob.dx / dx, w = grob.dy / dy;
        int k = (int) Math.rint(v);
        if (k < 1 || !nah(v, k, 1) || !nah(w, k, 1)) return 0;
        double a = (x0 - grob.x0) / grob.dx, b = (y0 - grob.y0) / grob.dy, c = nx / (double) k, d = ny / (double) k;
        if (!nah(a, Math.rint(a), 1) || !nah(b, Math.rint(b), 1) || !nah(c, Math.rint(c), 1) || !nah(d, Math.rint(d), 1)) return 0;
        if (a < -1e-9 || b < -1e-9 || a + c > grob.nx + 1e-9 || b + d > grob.ny + 1e-9) return 0;
        return k;
    }

    // ------------------------------------------------------------------ Rand (Ort.RAND)

    /** Seiten des Rands in der Folge von {@code feld.Ort#RAND}. */
    public static final int RAND_SUED = 0, RAND_OST = 1, RAND_NORD = 2, RAND_WEST = 3;

    /** Zahl der Randzellen: 2 · (nx + ny), je Randkante eine (die Ecken zweimal). */
    public int randZellen() { return 2 * (nx + ny); }

    /**
     * Die Randzelle r in der Folge von {@code feld.Ort#RAND}: im Süden beginnend gegen den Uhrzeigersinn – Süd (i von
     * West nach Ost), Ost (j von Süd nach Nord), Nord (i von Ost nach West), West (j von Nord nach Süd). Gibt
     * {i, j, Seite} zurück, Seite nach {@link #RAND_SUED} usw.; die Randzelle steht für die Kante der Zelle auf dieser Seite.
     */
    public int[] randZelle(int r) {
        if (r < 0 || r >= randZellen()) throw new IllegalArgumentException("Randzelle " + r + " von " + randZellen());
        if (r < nx) return new int[] {r, 0, RAND_SUED};
        if (r < nx + ny) return new int[] {nx - 1, r - nx, RAND_OST};
        if (r < 2 * nx + ny) return new int[] {nx - 1 - (r - nx - ny), ny - 1, RAND_NORD};
        return new int[] {0, ny - 1 - (r - 2 * nx - ny), RAND_WEST};
    }

    /** Index der Randzelle zur Seite und zur Zahl längs der Seite (i bei Süd und Nord, j bei Ost und West). */
    public int rand(int seite, int k) {
        return switch (seite) {
            case RAND_SUED -> k;
            case RAND_OST -> nx + k;
            case RAND_NORD -> nx + ny + (nx - 1 - k);
            case RAND_WEST -> 2 * nx + ny + (ny - 1 - k);
            default -> throw new IllegalArgumentException("Seite " + seite);
        };
    }

    /** Mitte der Geisterzelle vor der Randzelle r: eine halbe Zelle außerhalb des Gitters (x, y in Grad oder m). */
    public double[] geist(int r) {
        int[] z = randZelle(r);
        return switch (z[2]) {
            case RAND_SUED -> new double[] {x(z[0]), y0 - 0.5 * dy};
            case RAND_OST -> new double[] {x1() + 0.5 * dx, y(z[1])};
            case RAND_NORD -> new double[] {x(z[0]), y1() + 0.5 * dy};
            default -> new double[] {x0 - 0.5 * dx, y(z[1])};
        };
    }

    /** Ein Gitter über demselben Gebiet mit k-mal kleineren Zellen (das feine Gelände unter dem Rechengitter). */
    public Gitter verfeinert(String name, int k) { return new Gitter(name, lage, zone, x0, y0, dx / k, dy / k, nx * k, ny * k); }

    private static boolean nah(double a, double b, double mass) { return Math.abs(a - b) <= 1e-9 * Math.max(1, Math.abs(mass)); }

    @Override public String toString() {
        return lage == Lage.UTM
                ? String.format(java.util.Locale.ROOT, "%s UTM %d, O %.0f–%.0f, N %.0f–%.0f, Zelle %s m, %d × %d", name, zone, x0, x1(), y0, y1(), kurz(dx), nx, ny)
                : String.format(java.util.Locale.ROOT, "%s geographisch, %.3f–%.3f° O, %.3f–%.3f° N, Zelle %.5f° × %.5f°, %d × %d", name, x0, x1(), y0, y1(), dx, dy, nx, ny);
    }

    private static String kurz(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }
}
