package com.dan.netview;

/**
 * Farbrampen für Werte (linear zwischen Stützfarben, sRGB 0xRRGGBB): {@link #TIME} für Laufzeiten (gelb früh, über Rot zu
 * Violett spät), {@link #HEIGHT} für Höhen wie die Spurlinie (blau niedrig, grün, gelb, rot hoch). min und max legen den
 * Bereich fest; Werte außerhalb werden geklemmt.
 */
public final class Ramp {
    public final int[] stops;
    public double min, max;

    public Ramp(double min, double max, int... stops) {
        if (stops.length < 2) throw new IllegalArgumentException("Rampe: mindestens zwei Farben");
        this.min = min; this.max = max; this.stops = stops.clone();
    }

    public static final Ramp TIME = new Ramp(0, 600, 0xFFE680, 0xFFA040, 0xF04848, 0xB03090, 0x5030A0);
    public static final Ramp HEIGHT = new Ramp(0, 524, 0x3060D0, 0x30B0A0, 0x80D040, 0xF0D030, 0xF06030, 0xC02020);

    /** Farbe (0xRRGGBB) zum Wert v. */
    public int color(double v) {
        double t = (v - min) / Math.max(1e-12, max - min);
        t = Math.max(0, Math.min(1, t)) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) Math.floor(t));
        double u = t - i;
        int a = stops[i], b = stops[i + 1], out = 0;
        for (int s = 16; s >= 0; s -= 8) out |= (int) Math.round(((a >> s) & 255) * (1 - u) + ((b >> s) & 255) * u) << s;
        return out;
    }

    public Ramp copy(double min, double max) { return new Ramp(min, max, stops); }
}
