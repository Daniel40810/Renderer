package com.dan.tree.grass;

import com.dan.tree.TreeSpecies;

/**
 * Voreinstellungen für Gras auf einer {@link TreeSpecies}: Form {@code GRASS}, eigene Stufen aus dem Asset
 * (Megascans LOD0, LOD1, …), Stufengrenzen nach Entfernung in Grashöhen, Ausdünnen in der Ferne,
 * Wind (der ganze Büschel biegt sich mit h², jeder Halm wippt für sich) und ganzjähriges Laub, das im
 * Sommer frisch ist, im Herbst vergilbt und im Winter dürr steht.
 */
public final class GrassSpecies {

    private GrassSpecies() {}

    /**
     * Neue Grasart.
     *
     * @param lods je Variante die Ressourcen der Stufen 0, 1, … (fehlende Stufen werden ausgedünnt)
     */
    public static TreeSpecies create(String name, String latin, Class<?> owner, String[][] lods) {
        String[] first = new String[lods.length];
        for (int v = 0; v < lods.length; v++) first[v] = lods[v][0];
        TreeSpecies s = new TreeSpecies(name, latin, owner, first);
        s.form = TreeSpecies.Form.GRASS;
        s.lods = lods;
        s.lodKeep = new float[]{1, 0.3f, 0.12f, 0.05f};
        // Grenzen in Grashöhen: bei 0,5 m etwa 2 m, 5 m, 12,5 m; ab 35 Höhen dünner, ab 70 weg
        s.lodDistance = new float[]{4, 10, 25};
        s.fadeDistance = 35;
        s.maxDistance = 70;
        s.flexTrunk = 12;
        s.flexBranch = 1.6f;
        s.flutter = 1.2f;
        return s;
    }

    /**
     * Jahreszeit aus der Laubtextur: Sommer = mittlere Farbe der Textur, Frühling frischer, Herbst gelber,
     * Winter dürr; {@code green} 0 (schon dürres Gras, ändert sich kaum) bis 1 (grünes Gras).
     */
    public static TreeSpecies seasonFromTexture(TreeSpecies s, float green) {
        float[] m = s.load(0).leafColor;
        s.season.persistent = true;
        s.season.summer = m.clone();
        float g = green;
        s.season.spring = new float[]{m[0] * (1 + 0.1f * g), m[1] * (1 + 0.35f * g), m[2] * (1 + 0.05f * g)};
        s.season.autumn = new float[][]{{m[0] * (1 + 0.8f * g), m[1] * (1 + 0.2f * g), m[2] * (1 - 0.3f * g)},
                {m[0] * (1 + 1.1f * g), m[1] * (1 + 0.1f * g), m[2] * (1 - 0.4f * g)}};
        float lum = 0.3f * m[0] + 0.6f * m[1] + 0.1f * m[2];
        s.season.winter = new float[]{m[0] + (lum * 1.3f - m[0]) * g, m[1] + (lum * 1.05f - m[1]) * g, m[2] + (lum * 0.6f - m[2]) * g};
        s.season.leafOut = 85;
        s.season.flushDays = 25;
        s.season.colorStart = 255;
        s.season.colorFull = 300;
        s.season.bare = 330;
        return s;
    }
}
