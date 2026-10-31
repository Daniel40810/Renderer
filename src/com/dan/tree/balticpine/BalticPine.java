package com.dan.tree.balticpine;

import com.dan.tree.TreeSpecies;

/**
 * Waldkiefer (Pinus sylvestris), junge Bäume („Baltic Pine Saplings“) aus dem Procedural Vegetation Editor von Unreal
 * (Fab/Megascans, Timmendorf Phase 19, F24 A), vier Varianten: A 4,0 m, B 2,4 m, C 1,2 m, D 1,0 m. Immergrün,
 * Nadeln dunkel graugrün, Rinde unten graubraun [G]. Ausgewachsene Kiefern fehlen im Bestand noch: eine App, die
 * höhere Bäume braucht, nimmt dafür eine andere Art.
 */
public final class BalticPine {

    private BalticPine() {}

    public static final String[] VARIANTS = {"pine_a.dtree", "pine_b.dtree", "pine_c.dtree", "pine_d.dtree"};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = new TreeSpecies("Waldkiefer (jung)", "Pinus sylvestris", BalticPine.class, VARIANTS);
        s.variantNames = new String[]{"A 4 m", "B 2,4 m", "C 1,2 m", "D 1 m"};
        s.bark = new float[]{0.13f, 0.09f, 0.07f};
        s.flexTrunk = 2.5f;
        s.flexBranch = 0.35f;
        s.flutter = 0.35f;
        s.season.evergreen = true;
        s.season.summer = new float[]{0.035f, 0.06f, 0.03f};
        return s;
    }
}
