package com.dan.tree.demo;

import com.dan.tree.grass.GrassField;
import com.dan.tree.grass.ground.GrassGround;
import com.dan.tree.TreeSpecies;
import com.dan.tree.demo.TreeDemo;

import java.util.ArrayList;
import java.util.List;

/**
 * Vorschau einer Grasart auf dem Prozessor: Büschel, Wiese (aus einem {@link GrassField} auf dem Rasenboden
 * {@link GrassGround}, Stufe nach Entfernung, in der Ferne ausgedünnt) und alle Stufen nebeneinander.
 * Die Pakete {@code com.dan.tree.grass.<art>.demo} rufen nur {@link #run} auf.
 *
 * <pre>
 * Fenster:  …Demo [ansicht 0..2] [variante] [stufe 0..3|auto] [tag]
 * Bild:     …Demo --bild datei.png [einzel|wiese|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]
 * </pre>
 */
public final class GrassDemo {

    private GrassDemo() {}

    public static void run(TreeSpecies sp, GrassField field, String[] args) throws Exception {
        TreeDemo.run(demo(sp, field), args);
    }

    /** Vorschau mit Wiese und Rasenboden, auch für eigene Oberflächen. */
    public static TreeDemo demo(TreeSpecies sp, GrassField field) {
        TreeDemo d = new TreeDemo(sp);
        d.layout = layout(field);
        d.groundTexture = GrassGround.texture();
        d.groundTileX = GrassGround.TILE_X;
        d.groundTileZ = GrassGround.TILE_Z;
        return d;
    }

    /** Die Wiese als Anordnung für die Demos (Kreis um den Ursprung). */
    public static TreeDemo.Layout layout(GrassField field) {
        return (variants, radius) -> {
            List<TreeDemo.Placed> out = new ArrayList<>();
            for (GrassField.Clump c : field.place(variants, 0, 0, radius))
                out.add(new TreeDemo.Placed(c.variant(), c.x(), c.y(), c.z(), c.yaw(), c.scale(), -1));
            return out;
        };
    }
}
