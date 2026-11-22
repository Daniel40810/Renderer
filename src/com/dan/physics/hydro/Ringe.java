package com.dan.physics.hydro;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Flächen und Ringe eines Gitters mit Bauwerkskanten (Werkbuch HalligHooge Teil A, 12.5, Plan P4b): Eine Fläche hängt
 * über alle Kanten zusammen, die kein Bauwerk sind. Eine Fläche am Rand des Gitters hat die Tiefe 0, jede andere eine
 * mehr als ihre flachste Nachbarfläche über eine Bauwerkskante. „Binnen“ eines Siels ist seine tiefere Seite.
 * Kanten wie {@code Subgrid.kanteX/kanteY}: erst quer zu x ((nx + 1) · ny), dann quer zu y (nx · (ny + 1)).
 */
public final class Ringe {
    public final int nx, ny;
    /** Fläche je Zelle (0 … flaechen − 1). */
    public final int[] flaeche;
    /** Tiefe je Fläche (0 am Rand; Integer.MAX_VALUE, wenn vom Rand nicht zu erreichen). */
    public final int[] tiefe;
    public final int flaechen;

    /** @param bauwerk je Kante: ist sie ein Bauwerk (Deich oder Siel)? */
    public Ringe(int nx, int ny, boolean[] bauwerk) {
        this.nx = nx;
        this.ny = ny;
        int zellen = nx * ny, kantenX = (nx + 1) * ny;
        flaeche = new int[zellen];
        Arrays.fill(flaeche, -1);
        int n = 0;
        int[] stapel = new int[zellen];
        for (int c0 = 0; c0 < zellen; c0++) {
            if (flaeche[c0] >= 0) continue;
            int oben = 0;
            stapel[oben++] = c0;
            flaeche[c0] = n;
            while (oben > 0) {
                int c = stapel[--oben], i = c % nx, j = c / nx;
                if (i > 0 && !bauwerk[i + (nx + 1) * j] && flaeche[c - 1] < 0) { flaeche[c - 1] = n; stapel[oben++] = c - 1; }
                if (i < nx - 1 && !bauwerk[i + 1 + (nx + 1) * j] && flaeche[c + 1] < 0) { flaeche[c + 1] = n; stapel[oben++] = c + 1; }
                if (j > 0 && !bauwerk[kantenX + i + nx * j] && flaeche[c - nx] < 0) { flaeche[c - nx] = n; stapel[oben++] = c - nx; }
                if (j < ny - 1 && !bauwerk[kantenX + i + nx * (j + 1)] && flaeche[c + nx] < 0) { flaeche[c + nx] = n; stapel[oben++] = c + nx; }
            }
            n++;
        }
        flaechen = n;
        tiefe = new int[n];
        Arrays.fill(tiefe, Integer.MAX_VALUE);
        ArrayDeque<Integer> warte = new ArrayDeque<>();
        for (int c = 0; c < zellen; c++) {
            int i = c % nx, j = c / nx;
            if ((i == 0 || j == 0 || i == nx - 1 || j == ny - 1) && tiefe[flaeche[c]] != 0) {
                tiefe[flaeche[c]] = 0;
                warte.add(flaeche[c]);
            }
        }
        // Nachbarn über Bauwerkskanten je Fläche (als Liste von Kantenpaaren)
        int[][] nachbarn = nachbarn(bauwerk, n);
        while (!warte.isEmpty()) {
            int f = warte.poll();
            for (int g : nachbarn[f])
                if (tiefe[g] == Integer.MAX_VALUE) {
                    tiefe[g] = tiefe[f] + 1;
                    warte.add(g);
                }
        }
    }

    private int[][] nachbarn(boolean[] bauwerk, int n) {
        java.util.List<java.util.Set<Integer>> l = new java.util.ArrayList<>();
        for (int f = 0; f < n; f++) l.add(new java.util.HashSet<>());
        for (int e = 0; e < bauwerk.length; e++) {
            if (!bauwerk[e]) continue;
            int[] z = zellen(e);
            if (z[0] < 0 || z[1] < 0) continue;
            int a = flaeche[z[0]], b = flaeche[z[1]];
            if (a == b) continue;
            l.get(a).add(b);
            l.get(b).add(a);
        }
        int[][] r = new int[n][];
        for (int f = 0; f < n; f++) r[f] = l.get(f).stream().mapToInt(Integer::intValue).toArray();
        return r;
    }

    /** Die Zellen links und rechts (unten und oben) einer Kante; −1 außerhalb. */
    public int[] zellen(int e) {
        int kantenX = (nx + 1) * ny;
        if (e < kantenX) {
            int i = e % (nx + 1), j = e / (nx + 1);
            return new int[] {i > 0 ? i - 1 + nx * j : -1, i < nx ? i + nx * j : -1};
        }
        int k = e - kantenX, i = k % nx, j = k / nx;
        return new int[] {j > 0 ? i + nx * (j - 1) : -1, j < ny ? i + nx * j : -1};
    }

    /** Tiefe der Zelle c. */
    public int tiefe(int c) { return tiefe[flaeche[c]]; }

    /**
     * Binnen eines Siels auf der Kante e: +1 die rechte (obere) Zelle, −1 die linke (untere), 0 unbestimmt (beide gleich
     * tief, Randkante oder beide vom Rand nicht zu erreichen).
     */
    public int binnen(int e) {
        int[] z = zellen(e);
        if (z[0] < 0 || z[1] < 0) return 0;
        int ta = tiefe(z[0]), tb = tiefe(z[1]);
        return ta == tb ? 0 : tb > ta ? 1 : -1;
    }
}
