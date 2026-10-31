package com.dan.tree.grass.vegmix;

import com.dan.tree.grass.GrassField;
import com.dan.tree.grass.GrassSpecies;
import com.dan.tree.TreeSpecies;

/**
 * Vegetationsmix („Grass Vegitation mix“): ein Rasenstück von etwa 2,7 m × 2,5 m aus 65 Meshes mit vier
 * Texturen (Gräser, Kräuter, Laub am Boden), zu einem Atlas zusammengefasst, 0,7 m hoch. Als Kachel gestreut
 * ergibt es eine dichte, gemischte Wiese. Eine Stufe aus dem Asset, die übrigen werden ausgedünnt.
 */
public final class VegetationMix {

    private VegetationMix() {}

    public static final String[][] LODS = {{"vegmix.dtree"}};

    public static TreeSpecies species() {
        TreeSpecies s = GrassSpecies.create("Vegetationsmix", "Gräser und Kräuter, gemischt", VegetationMix.class, LODS);
        s.variantNames = new String[]{"Rasenstück"};
        // eine Kachel ist breit: Stufen nach Kachelgröße statt nach Halmhöhe
        s.lodDistance = new float[]{5, 12, 30};
        // 195 000 Dreiecke je Kachel: in der Ferne kräftig ausdünnen und früher enden
        s.lodKeep = new float[]{1, 0.15f, 0.05f, 0.02f};
        s.fadeDistance = 35;
        s.maxDistance = 60;
        GrassSpecies.seasonFromTexture(s, 0.8f);
        return s;
    }

    /** Kacheln: etwa eine je 3 m², beliebig gedreht, so dass sich die Stücke überlappen. */
    public static GrassField field() {
        GrassField f = new GrassField(0.35f, 11);
        f.scaleMin = 0.9f;
        f.scaleMax = 1.1f;
        return f;
    }
}
