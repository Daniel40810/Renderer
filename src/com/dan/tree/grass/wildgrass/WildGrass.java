package com.dan.tree.grass.wildgrass;

import com.dan.tree.grass.GrassField;
import com.dan.tree.grass.GrassSpecies;
import com.dan.tree.TreeSpecies;

/**
 * Wildgras (Megascans „Wild Grass“): acht üppige, grüne Büschel, 0,1 bis 0,4 m, je drei Stufen aus dem
 * Asset. Im Frühjahr frisch, im Herbst vergilbt, im Winter dürr.
 */
public final class WildGrass {

    private WildGrass() {}

    /** je Variante die Stufen aus dem Asset */
    public static final String[][] LODS = {{"wildgrass_a0.dtree", "wildgrass_a1.dtree", "wildgrass_a2.dtree"}, {"wildgrass_b0.dtree", "wildgrass_b1.dtree", "wildgrass_b2.dtree"}, {"wildgrass_c0.dtree", "wildgrass_c1.dtree", "wildgrass_c2.dtree"}, {"wildgrass_d0.dtree", "wildgrass_d1.dtree", "wildgrass_d2.dtree"}, {"wildgrass_e0.dtree", "wildgrass_e1.dtree", "wildgrass_e2.dtree"}, {"wildgrass_f0.dtree", "wildgrass_f1.dtree", "wildgrass_f2.dtree"}, {"wildgrass_g0.dtree", "wildgrass_g1.dtree", "wildgrass_g2.dtree"}, {"wildgrass_h0.dtree", "wildgrass_h1.dtree", "wildgrass_h2.dtree"}};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = GrassSpecies.create("Wildgras", "Poaceae, Art nicht angegeben", WildGrass.class, LODS);
        s.variantNames = new String[]{"A", "B", "C", "D", "E", "F", "G", "H"};
        GrassSpecies.seasonFromTexture(s, 1.0f);
        return s;
    }

    /** Wiese mit der üblichen Dichte (14 Büschel je m²). */
    public static GrassField field() {
        return new GrassField(14f, 7);
    }
}
