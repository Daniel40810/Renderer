package com.dan.tree;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Eine Baumart aus Unreal-Assets: Varianten als {@code .dtree}-Ressourcen neben {@link #owner},
 * Jahreszeit, Farben und Biegsamkeit. Die Pakete {@code com.dan.tree.<art>} liefern je eine
 * Voreinstellung; alle Felder sind öffentlich und lassen sich ändern.
 */
public final class TreeSpecies {

    public String name, latin;
    /** Klasse, neben der die Ressourcen liegen */
    public Class<?> owner;
    /** Ressourcen je Variante, etwa {"alder_a.dtree", …} */
    public String[] variants;
    /** Namen der Varianten für Oberflächen */
    public String[] variantNames;

    /** Rindenfarbe, linear; null = Farbe aus dem Asset */
    public float[] bark;
    public TreeSeason season = new TreeSeason();

    /** Biegsamkeit: Stamm, Äste, Zittern der Blätter (1 = mittel) */
    public float flexTrunk = 1, flexBranch = 1, flutter = 1;

    /** Was es ist: Baum oder Strauch, Gras (Büschel, dicht gestreut), Boden (statisches Gelände, bewegt sich nicht) */
    public enum Form { TREE, GRASS, GROUND }

    public Form form = Form.TREE;

    /**
     * Eigene Stufen aus dem Asset (etwa Megascans LOD0, LOD1, …): je Variante die Ressourcen der Stufen 0, 1, …;
     * null = alle Stufen aus einer Datei mit {@link TreeLod#of}. Stufen ohne eigene Datei nehmen die letzte
     * und dünnen sie mit {@link #lodKeep} aus.
     */
    public String[][] lods;
    /** Anteil der Karten je Stufe, wenn eine Stufe keine eigene Datei hat */
    public float[] lodKeep = {1, 0.35f, 0.12f, 0.04f};
    /** Grenzen der Stufen 0, 1, 2 in Höhen der Art, für {@code gl/TreeGl} und die Demos */
    public float[] lodDistance = {1.4f, 3.5f, 14f};
    /** ab dieser Entfernung (Höhen) dünnt {@code gl/TreeGl} aus, bis sie bei {@link #maxDistance} ganz wegfallen (Gras) */
    public float fadeDistance = Float.MAX_VALUE, maxDistance = Float.MAX_VALUE;

    private final Map<String, TreeAsset> assets = new HashMap<>();
    private final Map<String, BufferedImage> textures = new HashMap<>();

    public TreeSpecies(String name, String latin, Class<?> owner, String... variants) {
        this.name = name;
        this.latin = latin;
        this.owner = owner;
        this.variants = variants;
        this.variantNames = new String[variants.length];
        for (int i = 0; i < variants.length; i++) variantNames[i] = String.valueOf((char) ('A' + i));
    }

    public int variantCount() { return variants.length; }

    /** Lädt die Variante (Stufe 0; einmal, danach aus dem Zwischenspeicher). */
    public TreeAsset load(int variant) {
        return resource(lods != null ? lods[variant][0] : variants[variant]);
    }

    /** Asset der Stufe: eigene Datei, wenn {@link #lods} eine hat, sonst die letzte vorhandene. */
    public TreeAsset load(int variant, int level) {
        if (lods == null) return load(variant);
        String[] l = lods[variant];
        return resource(l[Math.min(level, l.length - 1)]);
    }

    private synchronized TreeAsset resource(String name) {
        return assets.computeIfAbsent(name, n -> TreeAsset.load(owner, n));
    }

    /** Textur neben {@link #owner} (Name aus {@link TreeAsset#barkTexture} / {@link TreeAsset#leafTexture}), oder null. */
    public synchronized BufferedImage texture(String name) {
        if (name == null || name.isEmpty()) return null;
        return textures.computeIfAbsent(name, n -> {
            try (InputStream in = owner.getResourceAsStream(n)) {
                if (in == null) throw new IllegalArgumentException("Textur fehlt: " + n + " neben " + owner.getName());
                return ImageIO.read(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Jahreszeit für Laubtexturen (FBX-Arten): Sommer ist die mittlere Farbe der Laubtextur der ersten Variante
     * (Tönung 1), Austrieb heller, Herbst wärmer und dunkler, Laubfall ab Tag 300, kahl ab Tag 315.
     * {@code flex} stellt Stamm (0,8 · flex) und Äste (flex) ein.
     */
    public TreeSpecies seasonFromLeafTexture(float flex) {
        float[] m = load(0).leafColor;
        flexTrunk = 0.8f * flex;
        flexBranch = flex;
        season.summer = m.clone();
        season.spring = new float[]{m[0] * 1.3f, m[1] * 1.45f, m[2] * 1.1f};
        season.autumn = new float[][]{{m[0] * 1.9f, m[1] * 1.1f, m[2] * 0.6f}, {m[0] * 1.5f, m[1] * 0.7f, m[2] * 0.5f}};
        season.leafOut = 105;
        season.colorStart = 280;
        season.reference = null;
        season.colorFull = 300;
        season.bare = 315;
        return this;
    }

    /** Netz einer Variante in einer Stufe. */
    public TreeMesh bake(int variant, TreeLod lod) {
        return TreeBaker.bake(load(variant), lod);
    }

    /** Netz einer Variante in Stufe 0..3; mit {@link #lods} die Datei der Stufe, sonst {@link TreeLod#of}. */
    public TreeMesh bake(int variant, int level) {
        if (lods == null) return bake(variant, TreeLod.of(level));
        int have = lods[variant].length;
        TreeLod l = TreeLod.of(0);
        l.level = level;
        if (level >= have) {
            l.leafKeep = lodKeep[Math.min(level - have + 1, lodKeep.length - 1)];
            l.clusterCells = TreeLod.of(level).clusterCells;
            l.trunkOriginal = false;
        }
        return TreeBaker.bake(load(variant, level), l);
    }

    @Override
    public String toString() {
        return name + " (" + latin + ") " + Arrays.toString(variantNames);
    }
}
