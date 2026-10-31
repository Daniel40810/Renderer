package com.dan.tree;

/**
 * Wie fein {@link TreeBaker} einen Baum baut. Vier Stufen sind voreingestellt ({@link #of(int)});
 * alle Felder sind öffentlich, eigene Stufen sind erlaubt.
 */
public final class TreeLod {

    public int level;
    /** Stamm und Äste als Originalnetz aus Unreal; sonst Röhren aus dem Skelett */
    public boolean trunkOriginal;
    public int trunkSides = 6;
    /** Röhren nur für Gelenke ab diesem Radius (m) */
    public float trunkMinRadius = 0.006f;
    /** gerade Ketten von Gelenken zu einer Röhre zusammenfassen: Knick bis Grad, Länge bis m */
    public float chainAngle = 12, chainLength = 2;
    /** Röhren nur bis zu dieser Ordnung (0 Stamm, 1 Hauptäste, …) */
    public int trunkMaxOrder = 99;
    /** Bäume ohne Skelett (FBX): Stamm in Rasterzellen der Größe Höhe/clusterCells vereinfachen */
    public int clusterCells = 0;

    /** Holz der Zweige als Röhren */
    public boolean twigWood;
    public int twigSides = 3;
    /** Zweigholz nur ab diesem Radius (m, vor dem Maßstab der Instanz) */
    public float twigMinRadius = 0;
    /** Knotenketten im Zweigholz zusammenfassen, Knick bis Grad */
    public float twigChainAngle = 15;

    /** Anteil der Blätter, die gebaut werden; die übrigen werden größer, so dass die Laubfläche bleibt */
    public float leafKeep = 1;
    /** 6 Sechseck (6 Dreiecke), 4 Raute (2), 3 Dreieck (1) */
    public int leafShape = 6;

    /** ferne Stufe: Krone als Hülle statt Blätter */
    public boolean silhouette;
    public int hullSectors = 8, hullBands = 5;

    public static TreeLod of(int level) {
        TreeLod l = new TreeLod();
        l.level = level;
        switch (level) {
            case 0 -> {
                l.trunkOriginal = true;
                l.twigWood = true;
            }
            case 1 -> {
                l.trunkSides = 5;
                l.trunkMinRadius = 0.006f;
                l.twigWood = true;
                l.twigMinRadius = 0.003f;
                l.twigChainAngle = 30;
                l.chainAngle = 15;
                l.clusterCells = 160;
                l.leafKeep = 0.35f;
                l.leafShape = 4;
            }
            case 2 -> {
                l.trunkSides = 4;
                l.trunkMinRadius = 0.02f;
                l.chainAngle = 25;
                l.chainLength = 4;
                l.clusterCells = 60;
                l.leafKeep = 0.1f;
                l.leafShape = 4;
            }
            default -> {
                l.level = 3;
                l.trunkSides = 3;
                l.trunkMinRadius = 0.03f;
                l.chainAngle = 40;
                l.chainLength = 8;
                l.trunkMaxOrder = 1;
                l.clusterCells = 24;
                l.leafKeep = 0;
                l.silhouette = true;
            }
        }
        return l;
    }

    public static final int LEVELS = 4;

    @Override
    public String toString() {
        return "Stufe " + level;
    }
}
