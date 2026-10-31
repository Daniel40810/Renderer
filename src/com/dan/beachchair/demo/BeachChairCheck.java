package com.dan.beachchair.demo;

import com.dan.beachchair.BeachChair;

import java.util.Locale;

/**
 * Prüfklasse ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den
 * Verstößen (Rückgabewert 1).
 */
public final class BeachChairCheck {
    private static int bad;

    public static void main(String[] args) {
        Locale.setDefault(Locale.GERMAN);
        BeachChair c = BeachChair.timmendorf();
        System.out.println(c.describe());

        // Netz: endlich, Maße wie eingestellt, Stufen werden kleiner
        BeachChair.Mesh near = c.mesh(BeachChair.Lod.NEAR), mid = c.mesh(BeachChair.Lod.MID), far = c.mesh(BeachChair.Lod.FAR);
        boolean fin = true;
        for (float f : near.data) fin &= Float.isFinite(f);
        check(fin && near.vertices % 3 == 0, "Netz nah: alle Werte endlich, nur Dreiecke");
        double[] b = near.bounds();
        check(Math.abs(b[4] - c.height) < 0.01, f("Höhe %.3f m (soll %.2f)", b[4], c.height));
        check(Math.abs(b[1]) < 1e-6, "steht auf dem Boden (y = 0)");
        check(b[3] - b[0] < c.width + 0.08 && b[3] - b[0] > c.width, f("Breite %.3f m mit Armlehnen (Korb %.2f)", b[3] - b[0], c.width));
        check(b[5] > c.depth / 2 && b[2] < -c.depth / 2 + 0.05, f("Tiefe von %.2f bis %.2f m", b[2], b[5]));
        check(near.triangles() > mid.triangles() && mid.triangles() > far.triangles() && far.triangles() >= 40,
                f("Stufen: nah %d > mittel %d > fern %d Dreiecke", near.triangles(), mid.triangles(), far.triangles()));
        int[] pp = near.perPart();
        check(pp[BeachChair.WICKER] > 0 && pp[BeachChair.WOOD] > 0 && pp[BeachChair.CANVAS] > 0 && pp[BeachChair.CUSHION] > 0 && pp[BeachChair.ROPE] > 0
                && pp[BeachChair.SLATS] > 0, "nah hat Geflecht, Holz, Stoff, Polster, Tau und Verschluss");
        check(far.perPart()[BeachChair.ROPE] == 0, "fern ohne Griffe");

        // Sitz: Oberkante des Polsters wie eingestellt
        double top = -1;
        for (int i = 0; i < near.vertices; i++)
            if (near.part(i) == BeachChair.CUSHION && near.group(i) == BeachChair.FIXED) top = Math.max(top, near.y(i));
        check(Math.abs(top - c.seatHeight) < 1e-4 && Math.abs(c.seat()[1] - c.seatHeight) < 1e-9, f("Sitz %.3f m", top));

        // Stellung: Lehne nach hinten, Fußstützen nach vorn, Verschluss nur abends
        BeachChair.Chair upright = new BeachChair.Chair(0, 0, 0, 0, 1), back = new BeachChair.Chair(0, 0, 0, 0, 1), shut = new BeachChair.Chair(0, 0, 0, 0, 1);
        back.recline = 1; back.footrest = 1;
        shut.closed = true; shut.recline = 1; shut.footrest = 1;
        BeachChair.Mesh mu = c.posed(near, upright), mb = c.posed(near, back), ms = c.posed(near, shut);
        double[] bu = mu.bounds(), bb = mb.bounds();
        check(bb[2] < bu[2] - 0.2 && bb[4] < bu[4] + 0.15, f("Lehne hinten: Rücken bei z %.2f statt %.2f, die Haube vorn hebt sich auf %.2f m", bb[2], bu[2], bb[4]));
        check(bb[5] > bu[5] + 0.3, f("Fußstützen heraus: vorn bis %.2f statt %.2f m", bb[5], bu[5]));
        check(countGroup(mu, BeachChair.COVER) == 0 && countGroup(ms, BeachChair.COVER) > 0, "Verschluss nur bei verschlossenen Körben");
        check(sameBounds(ms.bounds(), mu.bounds()) || ms.bounds()[5] <= bu[5] + 0.03, "verschlossen: Lehne aufrecht, Fußstützen drin");

        // Farben und Stellungen: reproduzierbar, Palette nach Gewicht
        BeachChair c2 = BeachChair.timmendorf();
        boolean same = true;
        for (int i = 0; i < 200; i++) {
            BeachChair.Chair a = c.chair(i, 0, 0, 0, i), a2 = c2.chair(i, 0, 0, 0, i);
            c.pose(a, 0.7, 0.5);
            c2.pose(a2, 0.7, 0.5);
            same &= a.closed == a2.closed && a.recline == a2.recline && a.footrest == a2.footrest && a.stripe[0] == a2.stripe[0];
        }
        check(same, "gleicher seed, gleiche Farben und Stellungen");
        c2.seed = 99;
        int diff = 0;
        for (int i = 0; i < 200; i++) if (c.stripeFor(i)[2] != c2.stripeFor(i)[2]) diff++;
        check(diff > 20, f("anderer seed, andere Farben (%d von 200)", diff));
        int blau = 0, n = 5000, offen = 0;
        for (int i = 0; i < n; i++) {
            double[] s = c.stripeFor(i);
            if (s[2] > 0.4) blau++;
            BeachChair.Chair a = c.chair(0, 0, 0, 0, i);
            c.pose(a, 0.8, 0.5);
            if (!a.closed) offen++;
        }
        check(Math.abs(blau / (double) n - 0.62) < 0.03, f("blau-weiß %.1f %% (Palette 62 %%)", 100.0 * blau / n));
        check(Math.abs(offen / (double) n - 0.8) < 0.03, f("offen %.1f %% (soll 80 %%)", 100.0 * offen / n));
        BeachChair.Chair z = c.chair(0, 0, 0, 0, 5);
        c.pose(z, 0, 1);
        check(z.closed && z.recline == 0 && z.footrest == 0, "nachts alle zu");

        // Blickrichtung: zur See, bei Sturm mit dem Rücken in den Wind, höchstens 60° weg
        double ruhig = BeachChair.facing(0, 270, 3, 1), sturm = BeachChair.facing(0, 270, 18, 1);
        check(Math.abs(ruhig) < 9 && sturm > 45 && sturm < 69, f("zur See %.0f°, bei Sturm aus West %.0f°", ruhig, sturm));

        // Umriss für den Schatten: über dem Boden, so hoch wie der Korb
        double[][] sil = c.silhouette(back);
        double hi = 0;
        for (double[] p : sil) hi = Math.max(hi, p[1]);
        check(sil.length > 10 && hi > 1.2 && hi <= c.height + 0.15, f("Umriss: %d Punkte, bis %.2f m", sil.length, hi));

        // Nordsee: eckig, anders als Ostsee
        BeachChair nord = BeachChair.sylt();
        check(nord.profile(8).length == 4 && c.profile(8).length == 11, "Nordsee eckig (eine Fase), Ostsee rund (acht Segmente)");
        check(nord.mesh(BeachChair.Lod.NEAR).triangles() < near.triangles(), "Nordsee braucht weniger Dreiecke");
        BeachChair k = c.copy();
        k.width = 1.5;
        check(c.width == 1.24 && Math.abs(k.mesh(BeachChair.Lod.FAR).bounds()[3] - 0.75) < 0.05, "copy() ist unabhängig, Breite wirkt");

        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static int countGroup(BeachChair.Mesh m, int g) {
        int n = 0;
        for (int i = 0; i < m.vertices; i++) if (m.group(i) == g) n++;
        return n;
    }

    static boolean sameBounds(double[] a, double[] b) {
        for (int i = 0; i < 6; i++) if (Math.abs(a[i] - b[i]) > 0.03) return false;
        return true;
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMAN, s, a); }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private BeachChairCheck() { }
}
