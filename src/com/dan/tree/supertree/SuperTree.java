package com.dan.tree.supertree;

import com.dan.tree.TreeSpecies;

/**
 * Laubbaum aus „Super Mobile Tree Packs“ (FBX, 25 m, rund 400 Dreiecke): wenige große Zweigkarten, ohne
 * Skelett; schon Stufe 0 ist so leicht wie die Hülle anderer Bäume. Die Art gibt das Pack nicht an.
 */
public final class SuperTree {

    private SuperTree() {}

    public static final String[] VARIANTS = {"supertree.dtree"};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = new TreeSpecies("Super-Baum", "Art nicht angegeben", SuperTree.class, VARIANTS);
        s.variantNames = new String[]{"Super"};
        s.seasonFromLeafTexture(0.9f);
        return s;
    }
}
