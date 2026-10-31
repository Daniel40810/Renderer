package com.dan.tree.demo;

import com.dan.tree.Tree;
import com.dan.tree.TreeSpecies;
import com.dan.tree.grass.GrassField;
import com.dan.tree.grass.footpath.FootPath;

import java.util.List;

/**
 * Selbsttest der Gräser ohne Fenster: je Art alles aus {@link TreeCheck} (Format, Stufen, Wind, Jahreszeit) und dazu
 * die Wiese ({@link GrassField}: Dichte, Reproduzierbarkeit, Ausschnitt gleich Ganzes, Maske); beim Fußweg die Höhe
 * am Ort. Alle Gräser aus {@link Tree#GRAESER} ({@code main}) oder eine Art ({@code main kikuyu}). Endet mit „Alles in
 * Ordnung.“
 */
public final class GrassCheck {

    private GrassCheck() {}

    public static void main(String[] args) {
        List<Tree.Art> arten = new java.util.ArrayList<>();
        for (String a : args) { Tree.Art x = Tree.art(a); if (x != null) arten.add(x); }
        if (arten.isEmpty()) arten.addAll(Tree.GRAESER);
        int bestanden = 0, fehler = 0;
        for (Tree.Art a : arten) {
            TreeSpecies sp = a.create();
            if (a.grass()) {
                if (field(sp, a.field().get())) bestanden += 5; else fehler++;
            }
            if (a.key().equals("fussweg")) {
                if (boden()) bestanden++; else fehler++;
            }
            int[] r = TreeCheck.pruefen(sp, a.minH(), a.maxH());
            bestanden += r[0];
            fehler += r[1];
        }
        TreeCheck.ende(bestanden, fehler);
    }

    /** Prüft eine Grasart mit ihrer Wiese und endet (wie früher je Art). */
    public static void run(TreeSpecies sp, GrassField field, float minH, float maxH) {
        boolean ok = field == null || field(sp, field);
        int[] r = TreeCheck.pruefen(sp, minH, maxH);
        TreeCheck.ende(r[0] + (ok ? 5 : 0), r[1] + (ok ? 0 : 1));
    }

    /** Der Fußweg: Gelände mit Höhen von mehr als 0,5 und weniger als 5 m. */
    static boolean boden() {
        GrassField.Ground g = FootPath.ground();
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float x = -15; x <= 15; x += 1)
            for (float z = -20; z <= 20; z += 1) {
                float y = g.height(x, z);
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
        boolean ok = hi - lo > 0.5f && hi - lo < 5 && Float.isFinite(lo);
        System.out.printf("Höhe am Ort%n  %s Höhen von %.2f bis %.2f m%n", ok ? "ok    " : "FEHLER", lo, hi);
        return ok;
    }

    static boolean field(TreeSpecies sp, GrassField f) {
        System.out.println("Wiese");
        int n = sp.variantCount();
        List<GrassField.Clump> a = f.place(n, -10, -10, 10, 10), b = f.place(n, -10, -10, 10, 10);
        boolean same = a.equals(b);
        double perM2 = a.size() / 400.0;
        List<GrassField.Clump> part = f.place(n, 0, 0, 10, 10);
        boolean sub = a.containsAll(part);
        int[] count = new int[n];
        for (GrassField.Clump c : a) count[c.variant()]++;
        boolean allVariants = true;
        for (int c : count) allVariants &= c > 0 || a.size() < n * 5;
        GrassField g = new GrassField(f.density, f.seed);
        g.mask = (x, z) -> Math.abs(x) < 2 ? 0 : 1;
        boolean masked = g.place(n, -10, -10, 10, 10).stream().noneMatch(c -> Math.abs(c.x()) < 2);
        System.out.printf("  %s gleiche Wiese bei gleichem Startwert%n", same ? "ok    " : "FEHLER");
        System.out.printf("  %s Dichte %.2f je m² (verlangt %.2f)%n", Math.abs(perM2 - f.density) < f.density * 0.15 ? "ok    " : "FEHLER", perM2, f.density);
        System.out.printf("  %s Ausschnitt gehört zur ganzen Wiese%n", sub ? "ok    " : "FEHLER");
        System.out.printf("  %s alle Varianten kommen vor%n", allVariants ? "ok    " : "FEHLER");
        System.out.printf("  %s Maske hält einen Weg frei%n", masked ? "ok    " : "FEHLER");
        return same && sub && allVariants && masked && Math.abs(perM2 - f.density) < f.density * 0.15;
    }
}
