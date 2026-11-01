package com.dan.surface.demo;

import com.dan.surface.Bake;
import com.dan.surface.Material;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Gerechnete Probesätze für Prüfung und Vorschau, wenn keine gebackenen Texturen da sind: je Material ein kachelbares
 * Muster aus Kieseln (Zellen mit Rundungen), Farbe aus einer Grundfarbe je Kurzname, Normale aus der Höhe. Geschrieben im
 * Format von ambientCG ({@code _Color}, {@code _NormalGL}, {@code _Roughness}, {@code _AmbientOcclusion},
 * {@code _Displacement}) und dann mit {@link Bake} gebacken, so läuft der ganze Weg auch ohne Daten.
 */
public final class Probe {
    private Probe() { }

    /** Grundfarbe (sRGB 0 … 1) nach dem Kurznamen. */
    static double[] color(String id) {
        return switch (id) {
            case "strandkies", "moraene" -> new double[]{0.52, 0.50, 0.47};
            case "sand" -> new double[]{0.62, 0.58, 0.50};
            case "schiefer_verwittert" -> new double[]{0.36, 0.37, 0.35};
            case "schiefer_frisch" -> new double[]{0.55, 0.54, 0.52};
            case "waldboden" -> new double[]{0.30, 0.26, 0.18};
            case "moos", "seetang" -> new double[]{0.30, 0.42, 0.16};
            case "firn" -> new double[]{0.90, 0.92, 0.95};
            case "eis" -> new double[]{0.70, 0.84, 0.92};
            case "rinde" -> new double[]{0.36, 0.25, 0.18};
            case "holz_entrindet" -> new double[]{0.45, 0.40, 0.33};
            case "kutter_holz" -> new double[]{0.85, 0.83, 0.76};
            case "kutter_tau" -> new double[]{0.70, 0.62, 0.45};
            default -> new double[]{0.5, 0.5, 0.5};
        };
    }

    /** Schreibt einen Probesatz (Ordner mit fünf Bildern, n² Bildpunkte) für Material m. */
    public static File set(Material m, File dir, int n, long seed) throws IOException {
        dir.mkdirs();
        Random r = new Random(seed ^ m.id.hashCode());
        int cells = 6 + r.nextInt(5);
        double[][] pts = new double[cells * cells][];
        for (int j = 0; j < cells; j++) for (int i = 0; i < cells; i++) pts[j * cells + i] = new double[]{(i + r.nextDouble()) / cells, (j + r.nextDouble()) / cells, 0.6 + 0.8 * r.nextDouble()};
        float[] h = new float[n * n];
        float[] tone = new float[n * n];
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double u = (x + 0.5) / n, v = (y + 0.5) / n, best = 9, second = 9;
                int bi = 0;
                int ci = (int) (u * cells), cj = (int) (v * cells);
                for (int oy = -1; oy <= 1; oy++)
                    for (int ox = -1; ox <= 1; ox++) {
                        int gi = ci + ox, gj = cj + oy;
                        int k = Math.floorMod(gj, cells) * cells + Math.floorMod(gi, cells);
                        double px = pts[k][0] + Math.floorDiv(gi, cells), py = pts[k][1] + Math.floorDiv(gj, cells);
                        double dx = u - px, dy = v - py, d = Math.sqrt(dx * dx + dy * dy);
                        if (d < best) { second = best; best = d; bi = k; } else if (d < second) second = d;
                    }
                double edge = (second - best) * cells;
                h[y * n + x] = (float) Math.min(1, Math.sqrt(Math.max(0, edge)) * 0.9);
                tone[y * n + x] = (float) pts[bi][2];
            }
        double[] c = color(m.id);
        BufferedImage col = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB), nor = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB),
                rou = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB), ao = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB),
                dis = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int i = y * n + x;
                double k = (0.75 + 0.25 * h[i]) * tone[i];
                col.setRGB(x, y, rgb(c[0] * k, c[1] * k, c[2] * k));
                double hx = h[y * n + (x + 1) % n] - h[y * n + (x + n - 1) % n], hy = h[((y + n - 1) % n) * n + x] - h[((y + 1) % n) * n + x];
                double nx = -hx * 4, ny = -hy * 4, l = Math.sqrt(nx * nx + ny * ny + 1);
                nor.setRGB(x, y, rgb(0.5 + nx / l / 2, 0.5 + ny / l / 2, 0.5 + 1 / l / 2));
                rou.setRGB(x, y, gray(0.6 + 0.3 * (1 - h[i])));
                ao.setRGB(x, y, gray(0.55 + 0.45 * h[i]));
                dis.setRGB(x, y, gray(h[i]));
            }
        String b = "Probe_" + m.id + "_";
        ImageIO.write(col, "jpg", new File(dir, b + "Color.jpg"));
        ImageIO.write(nor, "png", new File(dir, b + "NormalGL.png"));
        ImageIO.write(rou, "png", new File(dir, b + "Roughness.png"));
        ImageIO.write(ao, "png", new File(dir, b + "AmbientOcclusion.png"));
        ImageIO.write(dis, "png", new File(dir, b + "Displacement.png"));
        return dir;
    }

    /** Alle Materialien der Liste als Probe backen nach out/&lt;id&gt; (n²); Abgeleitete aus ihrem Ausgang. */
    public static void bakeAll(List<Material> list, File work, File out, int n) throws IOException {
        for (Material m : list) {
            if (m.derivedFrom != null) continue;
            Bake.bake(m, set(m, new File(work, m.id), n, 1958), new File(out, m.id), n);
        }
        for (Material m : list) if (m.derivedFrom != null) Bake.derive(m, new File(out, m.derivedFrom), new File(out, m.id));
    }

    static int rgb(double r, double g, double b) { return (u8(r) << 16) | (u8(g) << 8) | u8(b); }
    static int gray(double v) { return rgb(v, v, v); }
    static int u8(double v) { return (int) Math.round(Math.max(0, Math.min(1, v)) * 255); }
}
