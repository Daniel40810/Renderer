package com.dan.tree.hptree;

import com.dan.tree.TreeSpecies;

/**
 * Großer Laubbaum aus „HighPoly Tree Packs“ (FBX, 27,5 m): rund 13 000 einzelne Blätter als Karten mit
 * herzförmiger Blatttextur, Stamm mit Rindentextur, ohne Skelett. Die Art gibt das Pack nicht an.
 */
public final class HpTree {

    private HpTree() {}

    public static final String[] VARIANTS = {"hptree.dtree"};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = new TreeSpecies("HighPoly-Baum", "Art nicht angegeben", HpTree.class, VARIANTS);
        s.variantNames = new String[]{"HighPoly"};
        s.seasonFromLeafTexture(1.0f);
        s.flutter = 1.2f;
        return s;
    }
}
