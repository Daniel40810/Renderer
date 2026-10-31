package com.dan.tree;

/**
 * Wertet das Skelett eines {@link TreeAsset} für den Wind aus: Welche Gelenke bilden einen Ast,
 * welcher Ordnung (0 Stamm, 1 Hauptast, 2 und mehr Zweige), wo setzt der Hauptast an, wie weit
 * ist ein Gelenk von dort entfernt. An einer Gabel setzt das Kind den Ast fort, das am besten
 * in die bisherige Richtung zeigt; die anderen beginnen einen neuen Ast eine Ordnung höher.
 */
public final class TreeSkeleton {

    public final int n;
    public final int[] parent;
    public final float[] pos;
    public final float[] radius;
    /** true: Gelenk setzt den Ast seines Elternteils fort */
    public final boolean[] continues;
    /** 0 Stamm, 1 Hauptast, 2… Zweige */
    public final int[] order;
    /** erstes Gelenk des Asts, zu dem das Gelenk gehört */
    public final int[] branch;
    /** Weg vom Ansatz des eigenen Asts (m) */
    public final float[] along;
    /** Länge des eigenen Asts: größter Weg {@link #along} eines seiner Gelenke */
    public final float[] branchLength;
    /** Hauptast (erstes Gelenk) oder −1 am Stamm */
    public final int[] limb;
    /** Weg vom Ansatz des Hauptasts, durch alle Unteräste hindurch */
    public final float[] limbAlong;
    /** größter {@link #limbAlong} im ganzen Hauptast samt Unterästen */
    public final float[] limbReach;
    /** Höhe des Stamms (höchstes Gelenk der Ordnung 0) */
    public final float trunkTop;

    public TreeSkeleton(TreeAsset a) {
        n = a.joints();
        parent = a.jointParent;
        pos = a.jointPos;
        radius = a.jointRadius;
        continues = new boolean[n];
        order = new int[n];
        branch = new int[n];
        along = new float[n];
        branchLength = new float[n];
        limb = new int[n];
        limbAlong = new float[n];
        limbReach = new float[n];

        // Fortsetzung je Elternteil: das Kind mit der Richtung am nächsten zur Richtung Großeltern → Eltern
        int[] best = new int[n];
        float[] bestDot = new float[n];
        java.util.Arrays.fill(best, -1);
        java.util.Arrays.fill(bestDot, -2);
        for (int j = 0; j < n; j++) {
            int p = parent[j];
            if (p < 0) continue;
            float[] din = dir(p);
            float dx = pos[j * 3] - pos[p * 3], dy = pos[j * 3 + 1] - pos[p * 3 + 1], dz = pos[j * 3 + 2] - pos[p * 3 + 2];
            float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float d = l > 1e-6f ? (dx * din[0] + dy * din[1] + dz * din[2]) / l : 1;
            // dickere Kinder setzen eher fort
            d += radius[j] / Math.max(radius[p], 1e-4f) * 0.25f;
            if (d > bestDot[p]) { bestDot[p] = d; best[p] = j; }
        }

        float top = 0;
        for (int j = 0; j < n; j++) {           // Eltern stehen vor den Kindern
            int p = parent[j];
            if (p < 0) {
                continues[j] = true;
                order[j] = 0;
                branch[j] = j;
                limb[j] = -1;
                continue;
            }
            float seg = dist(p, j);
            continues[j] = best[p] == j;
            if (continues[j]) {
                order[j] = order[p];
                branch[j] = branch[p];
                along[j] = along[p] + seg;
            } else {
                order[j] = order[p] + 1;
                branch[j] = j;
                along[j] = seg;
            }
            if (order[j] == 0) {
                limb[j] = -1;
            } else if (order[j] == 1) {
                limb[j] = branch[j];
                limbAlong[j] = along[j];
            } else {
                limb[j] = limb[p];
                limbAlong[j] = limbAlong[p] + seg;
            }
            if (order[j] == 0) top = Math.max(top, pos[j * 3 + 1]);
        }
        trunkTop = top;
        float[] reach = new float[n];
        for (int j = 0; j < n; j++) {
            branchLength[branch[j]] = Math.max(branchLength[branch[j]], along[j]);
            if (limb[j] >= 0) reach[limb[j]] = Math.max(reach[limb[j]], limbAlong[j]);
        }
        for (int j = 0; j < n; j++) {
            branchLength[j] = Math.max(branchLength[branch[j]], 1e-3f);
            limbReach[j] = limb[j] >= 0 ? Math.max(reach[limb[j]], 1e-3f) : 0;
        }
    }

    /** Richtung, in die der Ast am Gelenk j wächst (vom Elternteil her; an der Wurzel nach oben). */
    public float[] dir(int j) {
        int p = parent[j];
        if (p < 0) return new float[]{0, 1, 0};
        float dx = pos[j * 3] - pos[p * 3], dy = pos[j * 3 + 1] - pos[p * 3 + 1], dz = pos[j * 3 + 2] - pos[p * 3 + 2];
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return l < 1e-6f ? new float[]{0, 1, 0} : new float[]{dx / l, dy / l, dz / l};
    }

    public float dist(int a, int b) {
        float dx = pos[a * 3] - pos[b * 3], dy = pos[a * 3 + 1] - pos[b * 3 + 1], dz = pos[a * 3 + 2] - pos[b * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Anzahl der Gelenke je Ordnung. */
    public int[] orderHistogram() {
        int max = 0;
        for (int o : order) max = Math.max(max, o);
        int[] h = new int[max + 1];
        for (int o : order) h[o]++;
        return h;
    }
}
