package com.dan.render.terrain;

import com.dan.core.szene.Boden;
import com.dan.render.terrain.clipmap.Source;

import java.io.File;
import java.io.IOException;

/**
 * Das Material des Geländes für die Clipmaps (P6b, F41 A). Die Werte und Regeln stehen seit P7a (F52 A) in
 * {@link Boden} (core.szene), damit Echtzeitbild und Referenz denselben Boden lesen; hier bleiben nur die vier Quellen
 * R, G, B und Watt über demselben Speicher.
 */
public final class Material {
    public final Boden boden;
    public final double[][] albedo;

    public Material(Boden boden) {
        this.boden = boden;
        this.albedo = boden.albedo;
    }

    /** Aus dem Gebiet, wie {@link Boden#aus}. */
    public static Material aus(File gebiet, double e0, double n0) throws IOException { return new Material(Boden.aus(gebiet, e0, n0)); }

    /** Liest {@code albedo.txt}, wie {@link Boden#albedo}. */
    public static double[][] albedo(File datei) throws IOException { return Boden.albedo(datei); }

    /** Ein Punkt der Clipmap bei (x, z) mit Schritt c: {R, G, B, Watt}. */
    public float[] punkt(double x, double z, double c) { return boden.punkt(x, z, c); }

    /** Die Quelle eines Kanals (0 R, 1 G, 2 B, 3 Watt). */
    public Source kanal(int k) {
        return new Source() {
            @Override public float height(double x, double z, double cell) { return boden.punkt(x, z, cell)[k]; }
            @Override public void fill(double x0, double z0, double cell, int w, int h, float[] out) { System.arraycopy(boden.rechteck(x0, z0, cell, w, h)[k], 0, out, 0, w * h); }
            @Override public double finestCell() { return boden.k1.zelle; }
            @Override public String describe() { return "Material, Kanal " + "RGBW".charAt(k); }
        };
    }
}
