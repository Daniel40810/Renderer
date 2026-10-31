package com.dan.tree;

import com.dan.tree.balticpine.BalticPine;
import com.dan.tree.blackalder.BlackAlder;
import com.dan.tree.europeanbeech.EuropeanBeech;
import com.dan.tree.grass.GrassField;
import com.dan.tree.grass.drygrass.DryGrass;
import com.dan.tree.grass.footpath.FootPath;
import com.dan.tree.grass.kikuyu.Kikuyu;
import com.dan.tree.grass.vegmix.VegetationMix;
import com.dan.tree.grass.wheatgrass.WheatGrass;
import com.dan.tree.grass.wildgrass.WildGrass;
import com.dan.tree.greasewood.Greasewood;
import com.dan.tree.hptree.HpTree;
import com.dan.tree.mobiletree.MobileTree;
import com.dan.tree.supertree.SuperTree;
import com.dan.tree.trees60.Trees60;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Einstieg in {@code com.dan.tree} (seit Timmendorf Phase 5 im Renderer, aus dem Projekt Nature): alle Arten mit ihrem
 * Namen, die Qualitätsstufen des Packages (Paketvertrag Punkt 7) und der Startwert. Bäume, Sträucher, Gräser und
 * Bodenstücke kommen alle aus demselben Kern ({@link TreeSpecies}, {@link TreeAsset}, {@link TreeBaker}); die Gräser liegen
 * in {@code com.dan.tree.grass}, weil sie diesen Kern brauchen und kein Package ein anderes kennen darf.
 */
public final class Tree {
    private Tree() { }

    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7): wie weit die Stufen 0 bis 3 reichen. */
    public enum Quality {
        LOW(0.5f), MEDIUM(0.75f), HIGH(1f), ULTRA(1.4f);
        /** Faktor auf die Entfernungen der Stufen ({@link TreeLod}). */
        public final float distanceScale;
        Quality(float d) { distanceScale = d; }
    }

    /** Eine Art mit Namen, Voreinstellung und der Höhe, die ihre Varianten haben (m, für den Selbsttest). */
    public record Art(String key, String name, Supplier<TreeSpecies> species, Supplier<GrassField> field, float minH, float maxH) {
        public TreeSpecies create() { return species.get(); }
        public boolean grass() { return field != null; }
    }

    /** Bäume und Sträucher. */
    public static final List<Art> BAEUME = List.of(
            new Art("schwarzerle", "Schwarzerle (Alnus glutinosa)", BlackAlder::species, null, 9, 20),
            new Art("rotbuche", "Rotbuche (Fagus sylvatica)", EuropeanBeech::species, null, 7, 30),
            new Art("kiefer", "Waldkiefer, jung (Pinus sylvestris)", BalticPine::species, null, 0.8f, 5),
            new Art("greasewood", "Greasewood (Strauch)", Greasewood::species, null, 0.3f, 4f),
            new Art("laubbaum", "Laubbaum mit Herbstlaub (Trees60)", Trees60::species, null, 12f, 29f),
            new Art("grosser-laubbaum", "großer Laubbaum (HpTree)", HpTree::species, null, 20f, 32f),
            new Art("laubbaum-leicht", "Laubbaum leicht (MobileTree)", MobileTree::species, null, 18f, 30f),
            new Art("laubbaum-400", "Laubbaum mit 400 Dreiecken (SuperTree)", SuperTree::species, null, 18f, 30f));

    /** Gräser (mit ihrer Wiese) und Bodenstücke. */
    public static final List<Art> GRAESER = List.of(
            new Art("wildgras", "Wildgras", WildGrass::species, WildGrass::field, 0.05f, 0.6f),
            new Art("kikuyu", "Kikuyugras", Kikuyu::species, Kikuyu::field, 0.1f, 0.8f),
            new Art("trockengras", "trockenes Gras", DryGrass::species, DryGrass::field, 0.0f, 1.0f),
            new Art("weizengras", "Weizengras", WheatGrass::species, WheatGrass::field, 0.0f, 1.6f),
            new Art("vegetationsmix", "Vegetationsmix", VegetationMix::species, VegetationMix::field, 0.3f, 1.2f),
            new Art("fussweg", "Fußweg im Gras (Gelände)", FootPath::species, null, 15, 30));

    /** Die Art mit dem Schlüssel (etwa „schwarzerle“, „wildgras“), oder null. */
    public static Art art(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        for (Art a : BAEUME) if (a.key().equals(k)) return a;
        for (Art a : GRAESER) if (a.key().equals(k)) return a;
        return null;
    }

    /** „schwarzerle, greasewood, …“ */
    public static String schluessel() {
        StringBuilder sb = new StringBuilder();
        for (Art a : BAEUME) sb.append(sb.length() > 0 ? ", " : "").append(a.key());
        for (Art a : GRAESER) sb.append(", ").append(a.key());
        return sb.toString();
    }
}
