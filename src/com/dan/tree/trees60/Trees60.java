package com.dan.tree.trees60;

import com.dan.tree.TreeSpecies;

/**
 * Zehn Laubbäume aus „Trees60“ (FBX, Stamm und Laubkarten mit Textur, ohne Skelett), 14 bis 27 m hoch.
 * Die Laubtextur ist schon herbstlich rot-orange. Seit Timmendorf Phase 6 ist sie der Herbst: im Sommer tönt die
 * Jahreszeit sie grün (Bezug ist die mittlere Farbe der Textur, Sommerfarbe ein Laubgrün gleicher Helligkeit [G]),
 * im Herbst zeigt sie ihre eigene Farbe, im Winter sind die Bäume kahl. Die Art gibt das Pack nicht an.
 */
public final class Trees60 {

    private Trees60() {}

    public static final String[] VARIANTS = {"trees60_1.dtree", "trees60_2.dtree", "trees60_3.dtree", "trees60_4.dtree", "trees60_5.dtree", "trees60_6.dtree", "trees60_7.dtree", "trees60_8.dtree", "trees60_9.dtree", "trees60_10.dtree"};

    /** Jede Variante wird beim ersten Gebrauch geladen; jeder Aufruf liefert eine frische Art. */
    public static TreeSpecies species() {
        TreeSpecies s = new TreeSpecies("Trees60", "Art nicht angegeben", Trees60.class, VARIANTS);
        s.variantNames = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
        s.seasonFromLeafTexture(1.0f);
        float[] m = s.load(0).leafColor;
        float y = 0.2126f * m[0] + 0.7152f * m[1] + 0.0722f * m[2];
        // Laubgrün mit der Helligkeit der Textur (Verhältnis wie das Sommergrün von TreeSeason) [G]
        float[] g = {0.03f, 0.095f, 0.025f};
        float yg = 0.2126f * g[0] + 0.7152f * g[1] + 0.0722f * g[2], k = y / yg;
        s.season.reference = m.clone();
        s.season.summer = new float[]{g[0] * k, g[1] * k, g[2] * k};
        s.season.spring = new float[]{g[0] * k * 1.5f, g[1] * k * 1.35f, g[2] * k * 1.2f};
        s.season.autumn = new float[][]{{m[0] * 1.1f, m[1], m[2]}, {m[0] * 0.9f, m[1] * 0.75f, m[2] * 0.7f}};
        return s;
    }
}
