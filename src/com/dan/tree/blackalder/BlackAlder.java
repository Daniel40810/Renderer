package com.dan.tree.blackalder;

import com.dan.tree.TreeSpecies;

/**
 * Schwarzerle (Alnus glutinosa) aus dem Procedural Vegetation Editor von Unreal, vier Varianten:
 * A breit und hoch (15,6 m), B jung und klein (10,8 m), C schlank (14,1 m), D groß (16,8 m).
 * Jahreszeit und Farben wie {@code com.dan.forest.Species.alder()}: färbt sich kaum und verliert das
 * Laub spät, noch fast grün.
 */
public final class BlackAlder {

    private BlackAlder() {}

    public static final String[] VARIANTS = {"alder_a.dtree", "alder_b.dtree", "alder_c.dtree", "alder_d.dtree"};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = new TreeSpecies("Schwarzerle", "Alnus glutinosa", BlackAlder.class, VARIANTS);
        s.variantNames = new String[]{"A breit", "B jung", "C schlank", "D groß"};
        s.bark = new float[]{0.055f, 0.048f, 0.042f};
        s.flexTrunk = 0.7f;
        s.flexBranch = 0.9f;
        s.flutter = 0.9f;
        s.season.leafOut = 110;
        s.season.colorStart = 290;
        s.season.colorFull = 312;
        s.season.bare = 322;
        s.season.spring = new float[]{0.10f, 0.26f, 0.05f};
        s.season.summer = new float[]{0.03f, 0.095f, 0.025f};
        s.season.autumn = new float[][]{{0.06f, 0.10f, 0.03f}, {0.10f, 0.11f, 0.035f}, {0.14f, 0.11f, 0.04f}};
        return s;
    }
}
