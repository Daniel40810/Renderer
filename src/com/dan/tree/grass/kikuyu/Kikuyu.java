package com.dan.tree.grass.kikuyu;

import com.dan.tree.grass.GrassField;
import com.dan.tree.grass.GrassSpecies;
import com.dan.tree.TreeSpecies;

/**
 * Kikuyugras (Cenchrus clandestinus, Megascans „Kikuyu Grass“): acht flache, dichte Büschel des kriechenden
 * Rasengrases, 0,2 bis 0,5 m, je drei Stufen aus dem Asset. Immergrün, im Winter etwas fahler.
 */
public final class Kikuyu {

    private Kikuyu() {}

    /** je Variante die Stufen aus dem Asset */
    public static final String[][] LODS = {{"kikuyu_a0.dtree", "kikuyu_a1.dtree", "kikuyu_a2.dtree"}, {"kikuyu_b0.dtree", "kikuyu_b1.dtree", "kikuyu_b2.dtree"}, {"kikuyu_c0.dtree", "kikuyu_c1.dtree", "kikuyu_c2.dtree"}, {"kikuyu_d0.dtree", "kikuyu_d1.dtree", "kikuyu_d2.dtree"}, {"kikuyu_e0.dtree", "kikuyu_e1.dtree", "kikuyu_e2.dtree"}, {"kikuyu_f0.dtree", "kikuyu_f1.dtree", "kikuyu_f2.dtree"}, {"kikuyu_g0.dtree", "kikuyu_g1.dtree", "kikuyu_g2.dtree"}, {"kikuyu_h0.dtree", "kikuyu_h1.dtree", "kikuyu_h2.dtree"}};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = GrassSpecies.create("Kikuyugras", "Cenchrus clandestinus", Kikuyu.class, LODS);
        s.variantNames = new String[]{"A", "B", "C", "D", "E", "F", "G", "H"};
        GrassSpecies.seasonFromTexture(s, 0.6f);
        s.lodKeep = new float[]{1, 0.2f, 0.08f, 0.04f};      // dichte, breite Büschel: in der Ferne stärker ausdünnen
        return s;
    }

    /** Wiese mit der üblichen Dichte (8 Büschel je m²). */
    public static GrassField field() {
        return new GrassField(8f, 7);
    }
}
