package com.dan.aurora;

/**
 * Zufall und Rauschen ohne Zustand: gleicher {@code seed}, gleiche Zahlen (Paketvertrag: reproduzierbar). Alles sind
 * reine Funktionen von seed und Ort, darum darf jeder Faden sie gleichzeitig rufen.
 */
public final class Rauschen {
    private Rauschen() { }

    /** Hash aus seed und drei ganzen Zahlen, Ergebnis gleichverteilt in 0..1. */
    public static double hash(long seed, long a, long b, long c) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L + c * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Wertrauschen in einer Dimension, 0..1, mit quintischer Glättung; {@code kanal} trennt unabhängige Felder. */
    public static double wert1(long seed, double x, int kanal) {
        long i = (long) Math.floor(x);
        double f = x - i, s = f * f * f * (f * (f * 6 - 15) + 10);
        double a = hash(seed, i, 0, kanal), b = hash(seed, i + 1, 0, kanal);
        return a + (b - a) * s;
    }

    /** Wertrauschen in zwei Dimensionen, 0..1. */
    public static double wert2(long seed, double x, double y, int kanal) {
        long ix = (long) Math.floor(x), iy = (long) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * fx * (fx * (fx * 6 - 15) + 10);
        fy = fy * fy * fy * (fy * (fy * 6 - 15) + 10);
        double a = hash(seed, ix, iy, kanal), b = hash(seed, ix + 1, iy, kanal), c = hash(seed, ix, iy + 1, kanal), d = hash(seed, ix + 1, iy + 1, kanal);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    /** Mehrere Oktaven in einer Dimension, etwa −1..1: Summe von (2·wert − 1)·amp, amp fällt mit {@code rauheit}. */
    public static double fbm1(long seed, double x, int oktaven, double rauheit, int kanal) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int o = 0; o < oktaven; o++) {
            sum += amp * (2 * wert1(seed, x * f + o * 17.31, kanal * 8 + o) - 1);
            norm += amp;
            amp *= rauheit;
            f *= 2.03;
        }
        return sum / norm;
    }

    /** Mehrere Oktaven in zwei Dimensionen, etwa −1..1. */
    public static double fbm2(long seed, double x, double y, int oktaven, double rauheit, int kanal) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int o = 0; o < oktaven; o++) {
            sum += amp * (2 * wert2(seed, x * f + o * 17.31, y * f - o * 11.7, kanal * 8 + o) - 1);
            norm += amp;
            amp *= rauheit;
            f *= 2.03;
        }
        return sum / norm;
    }

    /** Gratrauschen 0..1: 1 − |fbm|, spitz an den Graten (für Bergkämme in Demos). */
    public static double grat(long seed, double x, int oktaven, int kanal) {
        return 1 - Math.abs(fbm1(seed, x, oktaven, 0.5, kanal));
    }
}
