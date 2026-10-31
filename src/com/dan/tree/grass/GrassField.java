package com.dan.tree.grass;

import com.dan.tree.TreeBaker;

import java.util.ArrayList;
import java.util.List;

/**
 * Streut Grasbüschel über eine Fläche: ein Raster mit Zellen von 1/√Dichte, je Zelle höchstens ein Büschel,
 * Lage in der Zelle, Variante, Drehung und Größe aus einem Hash von Zelle und Startwert. Gleicher Startwert,
 * gleiche Wiese, auch wenn nur ein Ausschnitt verlangt wird. Wo, sagt die {@link Mask} (0..1), wie hoch der
 * Boden ist, die Schnittstelle {@link Ground}.
 */
public final class GrassField {

    /** Höhe des Bodens am Ort. */
    @FunctionalInterface
    public interface Ground {
        float height(float x, float z);

        Ground FLAT = (x, z) -> 0;
    }

    /** Anteil der Zellen mit Gras am Ort, 0..1 (etwa ein Weg, wo kein Gras wächst). */
    @FunctionalInterface
    public interface Mask {
        float at(float x, float z);
    }

    /** Ein Büschel. */
    public record Clump(int variant, float x, float y, float z, float yaw, float scale) {}

    /** Büschel je m² */
    public float density = 6;
    public long seed = 7;
    public float scaleMin = 0.8f, scaleMax = 1.25f;
    public Ground ground = Ground.FLAT;
    public Mask mask = (x, z) -> 1;
    /** Gewicht je Variante (null: gleich) */
    public float[] variantWeight;

    public GrassField() {}

    public GrassField(float density, long seed) {
        this.density = density;
        this.seed = seed;
    }

    /** Zellgröße in m */
    public float cell() {
        return (float) (1 / Math.sqrt(Math.max(density, 1e-3)));
    }

    /** Alle Büschel im Rechteck [x0, x1] × [z0, z1]. */
    public List<Clump> place(int variants, float x0, float z0, float x1, float z1) {
        List<Clump> out = new ArrayList<>();
        float c = cell();
        int i0 = (int) Math.floor(x0 / c), i1 = (int) Math.floor(x1 / c), k0 = (int) Math.floor(z0 / c), k1 = (int) Math.floor(z1 / c);
        int s = (int) seed;
        for (int k = k0; k <= k1; k++)
            for (int i = i0; i <= i1; i++) {
                int h = i * 73856093 ^ k * 19349663;
                float x = (i + TreeBaker.hash(h, s)) * c, z = (k + TreeBaker.hash(h, s + 1)) * c;
                if (x < x0 || x > x1 || z < z0 || z > z1) continue;
                if (TreeBaker.hash(h, s + 2) >= mask.at(x, z)) continue;
                int v = pick(TreeBaker.hash(h, s + 3), variants);
                float yaw = TreeBaker.hash(h, s + 4) * 6.2832f;
                float sc = scaleMin + (scaleMax - scaleMin) * TreeBaker.hash(h, s + 5);
                out.add(new Clump(v, x, ground.height(x, z), z, yaw, sc));
            }
        return out;
    }

    /** Alle Büschel im Kreis um (cx, cz). */
    public List<Clump> place(int variants, float cx, float cz, float radius) {
        List<Clump> all = place(variants, cx - radius, cz - radius, cx + radius, cz + radius);
        all.removeIf(g -> Math.hypot(g.x() - cx, g.z() - cz) > radius);
        return all;
    }

    private int pick(float r, int variants) {
        if (variantWeight == null) return Math.min(variants - 1, (int) (r * variants));
        float sum = 0;
        for (int v = 0; v < variants; v++) sum += variantWeight[Math.min(v, variantWeight.length - 1)];
        float t = r * sum;
        for (int v = 0; v < variants; v++) {
            t -= variantWeight[Math.min(v, variantWeight.length - 1)];
            if (t < 0) return v;
        }
        return variants - 1;
    }
}
