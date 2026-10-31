package com.dan.tree.grass.drygrass;

import com.dan.tree.grass.GrassField;
import com.dan.tree.grass.GrassSpecies;
import com.dan.tree.TreeSpecies;

/**
 * Trockenes Gras (Megascans „Dry Grass“): fünf Büschel aus dürren Halmen mit Samenständen, 0,4 bis 0,7 m,
 * je zwei Stufen aus dem Asset. Schon dürr, die Farbe ändert sich über das Jahr nur wenig.
 */
public final class DryGrass {

    private DryGrass() {}

    /** je Variante die Stufen aus dem Asset */
    public static final String[][] LODS = {{"drygrass_a0.dtree", "drygrass_a1.dtree"}, {"drygrass_b0.dtree", "drygrass_b1.dtree"}, {"drygrass_c0.dtree", "drygrass_c1.dtree"}, {"drygrass_d0.dtree", "drygrass_d1.dtree"}, {"drygrass_e0.dtree", "drygrass_e1.dtree"}};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = GrassSpecies.create("Trockenes Gras", "Poaceae, Art nicht angegeben", DryGrass.class, LODS);
        s.variantNames = new String[]{"A", "B", "C", "D", "E"};
        GrassSpecies.seasonFromTexture(s, 0.15f);
        return s;
    }

    /** Wiese mit der üblichen Dichte (12 Büschel je m²). */
    public static GrassField field() {
        return new GrassField(12f, 7);
    }
}
