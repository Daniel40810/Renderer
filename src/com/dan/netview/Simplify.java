package com.dan.netview;

import java.util.Arrays;

/**
 * Douglas-Peucker für Linienzüge (x, y, z): welche Punkte bleiben, wenn die Abweichung höchstens tol (m) sein darf. Anfang
 * und Ende bleiben immer; ein geschlossener Zug (Anfang = Ende) bleibt geschlossen.
 */
public final class Simplify {
    private Simplify() { }

    /** Indizes der behaltenen Punkte, aufsteigend. */
    public static int[] keep(float[] xyz, double tol) {
        int n = xyz.length / 3;
        if (n <= 2 || tol <= 0) { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i; return a; }
        boolean[] k = new boolean[n];
        k[0] = k[n - 1] = true;
        int[] stack = new int[2 * n];
        int sp = 0;
        stack[sp++] = 0; stack[sp++] = n - 1;
        // geschlossen: zuerst am weitesten entfernten Punkt teilen, sonst fiele alles auf den Anfang zusammen
        if (dist(xyz, 0, n - 1) < 1e-6) {
            int far = 1;
            double best = -1;
            for (int i = 1; i < n - 1; i++) { double d = dist(xyz, 0, i); if (d > best) { best = d; far = i; } }
            k[far] = true;
            sp = 0;
            stack[sp++] = 0; stack[sp++] = far; stack[sp++] = far; stack[sp++] = n - 1;
        }
        while (sp > 0) {
            int b = stack[--sp], a = stack[--sp];
            double best = -1;
            int at = -1;
            for (int i = a + 1; i < b; i++) {
                double d = segDist(xyz, i, a, b);
                if (d > best) { best = d; at = i; }
            }
            if (at >= 0 && best > tol) {
                k[at] = true;
                stack[sp++] = a; stack[sp++] = at; stack[sp++] = at; stack[sp++] = b;
            }
        }
        int c = 0;
        for (boolean x : k) if (x) c++;
        int[] out = new int[c];
        c = 0;
        for (int i = 0; i < n; i++) if (k[i]) out[c++] = i;
        return out;
    }

    static double dist(float[] p, int a, int b) {
        double dx = p[3 * b] - p[3 * a], dy = p[3 * b + 1] - p[3 * a + 1], dz = p[3 * b + 2] - p[3 * a + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Abstand des Punkts i von der Strecke a–b. */
    static double segDist(float[] p, int i, int a, int b) {
        double ax = p[3 * a], ay = p[3 * a + 1], az = p[3 * a + 2];
        double ux = p[3 * b] - ax, uy = p[3 * b + 1] - ay, uz = p[3 * b + 2] - az, l2 = ux * ux + uy * uy + uz * uz;
        double vx = p[3 * i] - ax, vy = p[3 * i + 1] - ay, vz = p[3 * i + 2] - az;
        double t = l2 < 1e-18 ? 0 : Math.max(0, Math.min(1, (vx * ux + vy * uy + vz * uz) / l2));
        double dx = vx - ux * t, dy = vy - uy * t, dz = vz - uz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Größte Abweichung des ganzen Zugs vom vereinfachten (für die Prüfung). */
    public static double maxDeviation(float[] xyz, int[] kept) {
        double worst = 0;
        for (int s = 0; s + 1 < kept.length; s++)
            for (int i = kept[s] + 1; i < kept[s + 1]; i++) worst = Math.max(worst, segDist(xyz, i, kept[s], kept[s + 1]));
        return worst;
    }

    static int[] all(int n) { int[] a = new int[n]; Arrays.setAll(a, i -> i); return a; }
}
