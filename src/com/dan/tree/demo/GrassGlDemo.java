package com.dan.tree.demo;

import com.dan.tree.grass.GrassField;
import com.dan.tree.grass.ground.GrassGround;
import com.dan.tree.TreeSpecies;
import com.dan.tree.demo.TreeGlDemo;

/**
 * Wiese einer Grasart mit JOGL (OpenGL 4.3): Büschel aus einem {@link GrassField}, Stufe nach Entfernung,
 * in der Ferne ausgedünnt, auf dem Rasenboden {@link GrassGround}.
 *
 * <pre>
 * Fenster:  …GlDemo [dichte je m²] [fläche m] [tag]
 * Bild:     …GlDemo --bild datei.png [dichte je m²] [fläche m] [tag] [wind m/s] [breite] [höhe]
 * </pre>
 */
public final class GrassGlDemo {

    private GrassGlDemo() {}

    public static void run(TreeSpecies sp, GrassField field, String[] args) throws Exception {
        int def = Math.max(1, Math.round(field.density));
        TreeGlDemo.run((count, area) -> {
            GrassField f = new GrassField(count == def ? field.density : count, field.seed);
            f.scaleMin = field.scaleMin;
            f.scaleMax = field.scaleMax;
            f.ground = field.ground;
            f.mask = field.mask;
            f.variantWeight = field.variantWeight;
            TreeGlDemo d = new TreeGlDemo(sp, 0, area, GrassDemo.layout(f));
            d.groundTexture = GrassGround.texture();
            d.groundTileX = GrassGround.TILE_X;
            d.groundTileZ = GrassGround.TILE_Z;
            return d;
        }, args, def);
    }
}
