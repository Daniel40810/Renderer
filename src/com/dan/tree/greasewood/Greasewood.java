package com.dan.tree.greasewood;

import com.dan.tree.TreeSpecies;

/**
 * Greasewood (Sarcobatus vermiculatus), Strauch der Salzsteppe im Westen Nordamerikas, aus dem Procedural
 * Vegetation Editor von Unreal, vier Varianten (A groß bis D klein). Sommergrün: treibt im April aus, die
 * fleischigen Blätter sind hellgrün, im Herbst gelblich, im Winter steht der dornige Strauch kahl.
 */
public final class Greasewood {

    private Greasewood() {}

    public static final String[] VARIANTS = {"greasewood_a.dtree", "greasewood_b.dtree", "greasewood_c.dtree", "greasewood_d.dtree"};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = new TreeSpecies("Greasewood", "Sarcobatus vermiculatus", Greasewood.class, VARIANTS);
        s.variantNames = new String[]{"A groß", "B", "C", "D klein"};
        s.bark = new float[]{0.16f, 0.14f, 0.12f};
        s.flexTrunk = 0.3f;
        s.flexBranch = 1.2f;
        s.flutter = 0.5f;
        s.season.leafOut = 100;
        s.season.colorStart = 275;
        s.season.colorFull = 298;
        s.season.bare = 315;
        s.season.spring = new float[]{0.13f, 0.26f, 0.05f};
        s.season.summer = new float[]{0.08f, 0.17f, 0.04f};
        s.season.autumn = new float[][]{{0.22f, 0.20f, 0.04f}, {0.30f, 0.20f, 0.05f}};
        return s;
    }
}
