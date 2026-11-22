package com.dan.aufbereitung;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entfernt den Füllwert aus Höhenkacheln (Werkbuch HalligHooge Teil A, 10.4, Schritt 2). Das DGM1 von 2024 trägt über
 * Wasser und Watt überall genau 1,30 m NHN; gemessen ist dort nichts. Füllwert sind zusammenhängende Flächen
 * (Vierernachbarschaft, auch über Kachelgrenzen) mit {@link #wert} ± {@link #toleranz} ab {@link #flaeche} m². Kleinere
 * Flächen mit diesem Wert, etwa einzelne Pixel auf der Fenne, bleiben.
 */
public final class Fuellwert {
    public final double wert, toleranz, flaeche;

    /** Was entfernt wurde. */
    public record Ergebnis(long pixel, int flaechen, long kleinBehalten, int flaechenBehalten) { }

    public Fuellwert(double wert, double toleranz, double flaeche) {
        this.wert = wert;
        this.toleranz = toleranz;
        this.flaeche = flaeche;
    }

    /**
     * Setzt die Pixel des Füllwerts auf NaN. {@code kacheln}: Schlüssel {@link #schluessel}(Spalte, Zeile) der Kachel im
     * Kachelraster (Spalte nach Ost, Zeile nach Nord), Werte n × n mit Zeilen von Nord; Zellgröße {@code zelle} m.
     */
    public Ergebnis entferne(Map<Long, float[]> kacheln, int n, double zelle) {
        List<Long> keys = new ArrayList<>(kacheln.keySet());
        Map<Long, int[]> marken = new HashMap<>();
        Map<Long, Integer> basis = new HashMap<>();
        List<Integer> groessen = new ArrayList<>();
        // 1. Flächen je Kachel
        for (long k : keys) {
            float[] v = kacheln.get(k);
            int[] m = new int[n * n];
            int[] stapel = new int[n * n];
            int b = groessen.size();
            basis.put(k, b);
            int zahl = 0;
            for (int p = 0; p < n * n; p++) {
                if (m[p] != 0 || !kandidat(v[p])) continue;
                zahl++;
                int g = 0, sp = 0;
                stapel[sp++] = p;
                m[p] = zahl;
                while (sp > 0) {
                    int q = stapel[--sp];
                    g++;
                    int x = q % n, y = q / n;
                    if (x > 0 && m[q - 1] == 0 && kandidat(v[q - 1])) { m[q - 1] = zahl; stapel[sp++] = q - 1; }
                    if (x < n - 1 && m[q + 1] == 0 && kandidat(v[q + 1])) { m[q + 1] = zahl; stapel[sp++] = q + 1; }
                    if (y > 0 && m[q - n] == 0 && kandidat(v[q - n])) { m[q - n] = zahl; stapel[sp++] = q - n; }
                    if (y < n - 1 && m[q + n] == 0 && kandidat(v[q + n])) { m[q + n] = zahl; stapel[sp++] = q + n; }
                }
                groessen.add(g);
            }
            marken.put(k, m);
        }
        // 2. über Kachelgrenzen vereinen
        int[] eltern = new int[groessen.size()];
        for (int i = 0; i < eltern.length; i++) eltern[i] = i;
        for (long k : keys) {
            int sx = spalte(k), sy = zeile(k);
            int[] m = marken.get(k);
            int b = basis.get(k);
            long ost = schluessel(sx + 1, sy), nord = schluessel(sx, sy + 1);
            if (marken.containsKey(ost)) {                                                  // Ostrand von k mit Westrand des Nachbarn
                int[] mo = marken.get(ost);
                int bo = basis.get(ost);
                for (int y = 0; y < n; y++) {
                    int a = m[y * n + n - 1], c = mo[y * n];
                    if (a != 0 && c != 0) vereine(eltern, b + a - 1, bo + c - 1);
                }
            }
            if (marken.containsKey(nord)) {                                                 // Nordrand von k (Zeile 0) mit Südrand des Nachbarn (Zeile n − 1)
                int[] mn = marken.get(nord);
                int bn = basis.get(nord);
                for (int x = 0; x < n; x++) {
                    int a = m[x], c = mn[(n - 1) * n + x];
                    if (a != 0 && c != 0) vereine(eltern, b + a - 1, bn + c - 1);
                }
            }
        }
        long[] summe = new long[eltern.length];
        for (int i = 0; i < eltern.length; i++) summe[wurzel(eltern, i)] += groessen.get(i);
        double pixel = zelle * zelle;
        boolean[] weg = new boolean[eltern.length];
        int flaechen = 0, behalten = 0;
        long kleinBehalten = 0;
        for (int i = 0; i < eltern.length; i++) {
            if (wurzel(eltern, i) != i) continue;
            if (summe[i] * pixel >= flaeche) flaechen++;
            else { behalten++; kleinBehalten += summe[i]; }
        }
        for (int i = 0; i < eltern.length; i++) weg[i] = summe[wurzel(eltern, i)] * pixel >= flaeche;
        // 3. entfernen
        long pixelWeg = 0;
        for (long k : keys) {
            float[] v = kacheln.get(k);
            int[] m = marken.get(k);
            int b = basis.get(k);
            for (int p = 0; p < n * n; p++)
                if (m[p] != 0 && weg[b + m[p] - 1]) { v[p] = Float.NaN; pixelWeg++; }
        }
        return new Ergebnis(pixelWeg, flaechen, kleinBehalten, behalten);
    }

    private boolean kandidat(float z) { return Math.abs(z - wert) <= toleranz; }

    private static int wurzel(int[] e, int i) {
        while (e[i] != i) {
            e[i] = e[e[i]];
            i = e[i];
        }
        return i;
    }

    private static void vereine(int[] e, int a, int b) {
        a = wurzel(e, a);
        b = wurzel(e, b);
        if (a != b) e[Math.max(a, b)] = Math.min(a, b);
    }

    /** Schlüssel einer Kachel aus Spalte (nach Ost) und Zeile (nach Nord) im Kachelraster. */
    public static long schluessel(int spalte, int zeile) { return ((long) spalte << 32) | (zeile & 0xffffffffL); }
    public static int spalte(long k) { return (int) (k >> 32); }
    public static int zeile(long k) { return (int) k; }
}
