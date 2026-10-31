package com.dan.tree.europeanbeech;

import com.dan.tree.TreeSpecies;

/**
 * Rotbuche (Fagus sylvatica) aus dem Procedural Vegetation Editor von Unreal (Fab/Megascans, Timmendorf Phase 19, F24 A),
 * vier Varianten: A alt und breit (26,6 m), B hoch (23,6 m), C jung (8,7 m), D jung, mit Ästen (9,7 m).
 * Jahreszeit [G nach dem Deutschen Wetterdienst, Phänologie Schleswig-Holstein]: Blattentfaltung um den 1. Mai (Tag 121),
 * frisch hellgrün, im Sommer dunkel; Verfärbung ab Mitte Oktober über Gelb zu Kupferbraun, kahl Mitte November.
 * Rinde glatt, silbergrau.
 */
public final class EuropeanBeech {

    private EuropeanBeech() {}

    public static final String[] VARIANTS = {"beech_a.dtree", "beech_b.dtree", "beech_c.dtree", "beech_d.dtree"};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = new TreeSpecies("Rotbuche", "Fagus sylvatica", EuropeanBeech.class, VARIANTS);
        s.variantNames = new String[]{"A alt", "B hoch", "C jung", "D jung mit Ästen"};
        s.bark = new float[]{0.20f, 0.20f, 0.19f};
        s.flexTrunk = 0.6f;
        s.flexBranch = 0.85f;
        s.flutter = 0.7f;
        s.season.leafOut = 121;
        s.season.flushDays = 12;
        s.season.matureDays = 30;
        s.season.colorStart = 288;
        s.season.colorFull = 305;
        s.season.bare = 318;
        s.season.spring = new float[]{0.16f, 0.34f, 0.04f};
        s.season.summer = new float[]{0.035f, 0.085f, 0.02f};
        s.season.autumn = new float[][]{{0.32f, 0.22f, 0.03f}, {0.28f, 0.10f, 0.02f}, {0.20f, 0.07f, 0.015f}};
        return s;
    }
}
