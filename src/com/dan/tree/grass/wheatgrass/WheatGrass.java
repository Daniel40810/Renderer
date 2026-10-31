package com.dan.tree.grass.wheatgrass;

import com.dan.tree.grass.GrassField;
import com.dan.tree.grass.GrassSpecies;
import com.dan.tree.TreeSpecies;

/**
 * Weizengras (Megascans „Wheat Grass“, Triticum): sechs Büschel mit Ähren, bis 1,3 m, je zwei Stufen aus dem
 * Asset; Variante F liegt flach am Boden. Reif und trocken, die Farbe ändert sich über das Jahr wenig.
 */
public final class WheatGrass {

    private WheatGrass() {}

    /** je Variante die Stufen aus dem Asset */
    public static final String[][] LODS = {{"wheatgrass_a0.dtree", "wheatgrass_a1.dtree"}, {"wheatgrass_b0.dtree", "wheatgrass_b1.dtree"}, {"wheatgrass_c0.dtree", "wheatgrass_c1.dtree"}, {"wheatgrass_d0.dtree", "wheatgrass_d1.dtree"}, {"wheatgrass_e0.dtree", "wheatgrass_e1.dtree"}, {"wheatgrass_f0.dtree", "wheatgrass_f1.dtree"}};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = GrassSpecies.create("Weizengras", "Triticum", WheatGrass.class, LODS);
        s.variantNames = new String[]{"A", "B", "C", "D", "E kurz", "F liegend"};
        GrassSpecies.seasonFromTexture(s, 0.2f);
        return s;
    }

    /** Wiese mit der üblichen Dichte (6 Büschel je m²). */
    public static GrassField field() {
        return new GrassField(6f, 7);
    }
}
