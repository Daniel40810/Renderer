package com.dan.tree.mobiletree;

import com.dan.tree.TreeSpecies;

/**
 * Laubbaum aus „Mobile Tree Packs“ (FBX, 24 m, rund 2 000 Dreiecke): Zweigbüschel als Karten, helle Rinde,
 * ohne Skelett; für viele Bäume auf schwacher Hardware. Die Art gibt das Pack nicht an.
 */
public final class MobileTree {

    private MobileTree() {}

    public static final String[] VARIANTS = {"mobiletree.dtree"};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = new TreeSpecies("Mobile-Baum", "Art nicht angegeben", MobileTree.class, VARIANTS);
        s.variantNames = new String[]{"Mobile"};
        s.seasonFromLeafTexture(0.9f);
        return s;
    }
}
