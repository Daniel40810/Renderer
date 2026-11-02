package com.dan.surface;

/**
 * Stochastische Kachelung auf einem Sechseckgitter (Mikkelsen 2022, „Practical Real-Time Hex-Tiling“, JCGT 11(3)),
 * auf dem Prozessor gerechnet wie im Baustein {@code gl/surface.glsl}: Die Ebene wird in Dreiecke eines schiefen Gitters
 * geteilt; jede Ecke bekommt aus einem ganzzahligen Hash einen Versatz (und, wenn gewünscht, eine Drehung) der Textur,
 * und die drei Abfragen werden mit Gewichten gemischt, die zu den Ecken hin steil werden (Exponent {@link #GAIN}). So
 * wiederholt sich keine Kachel sichtbar, ohne dass der Speicher wächst. Für die Prüfung ({@code SurfaceCheck}:
 * Autokorrelation über 1 km Strand).
 */
public final class HexTiling {
    private HexTiling() { }

    /** Steilheit der Mischung (Mikkelsen: Kontrast der Gewichte); gleich im Shader. */
    public static final double GAIN = 6;
    /** Größe der Sechsecke in Kacheln (Mikkelsen: 2√3 Teilungen je Kachel, hier gröber: eine Zelle ≈ 0,5 Kachel). */
    public static final double CELLS = 2 * Math.sqrt(3) * 0.5;

    /** Ein Wert der Textur an (u, v) in Kacheln (gekachelt). */
    public interface Tex { double at(double u, double v); }

    /** PCG-Hash (wie im Shader, 32 Bit ohne Vorzeichen) → zwei Zahlen 0 … 1. */
    static double[] hash(int x, int y) {
        int s = x * 1597334677 ^ y * (int) 3812015801L;
        int a = pcg(s), b = pcg(a);
        return new double[]{(a >>> 8) / 16777216.0, (b >>> 8) / 16777216.0};
    }

    static int pcg(int v) {
        int state = v * 747796405 + -1403630843;   // 2891336453
        int word = ((state >>> ((state >>> 28) + 4)) ^ state) * 277803737;
        return (word >>> 22) ^ word;
    }

    /** Hex-gekachelter Wert an (u, v) (Kacheln); rotate: zufällig drehen. */
    public static double sample(Tex t, double u, double v, boolean rotate) {
        double su = u * CELLS, sv = v * CELLS;
        // schiefes Gitter: x' = x, y' = −x/√3 + 2y/√3
        double gx = su, gy = -0.57735027 * su + 1.15470054 * sv;
        int bx = (int) Math.floor(gx), by = (int) Math.floor(gy);
        double fx = gx - bx, fy = gy - by, fz = 1 - fx - fy;
        double s = fz < 0 ? 1 : 0, s2 = 2 * s - 1;
        double[] w = {-fz * s2, s - fy * s2, s - fx * s2};
        int[][] vtx = {{bx + (int) s, by + (int) s}, {bx + (int) s, by + 1 - (int) s}, {bx + 1 - (int) s, by + (int) s}};
        double sum = 0, wsum = 0;
        for (int k = 0; k < 3; k++) {
            double[] h = hash(vtx[k][0], vtx[k][1]);
            double uu = u, vv = v;
            if (rotate) {
                double a = h[0] * 2 * Math.PI, c = Math.cos(a), sn = Math.sin(a);
                double ru = c * u - sn * v, rv = sn * u + c * v;
                uu = ru;
                vv = rv;
            }
            double val = t.at(uu + h[0] * 17.0, vv + h[1] * 17.0);
            double ww = Math.pow(Math.max(w[k], 0), GAIN);
            sum += val * ww;
            wsum += ww;
        }
        return sum / Math.max(wsum, 1e-12);
    }
}
