package com.dan.understory.demo;

import com.dan.understory.Kind;
import com.dan.understory.PlantMesh;
import com.dan.understory.Understory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung von {@code com.dan.understory} ohne Fenster: Netze gültig und gestuft, Licht nach Beer-Lambert, Ansprüche der
 * Arten in der richtigen Reihenfolge, die gepflanzte Dichte gleich der erwarteten, derselbe Startwert dieselbe Kachel,
 * Reichweite, Stufen und Ausblenden, Bartflechten an den Ankern. Endet mit „Alles in Ordnung.“
 */
public final class UnderstoryCheck {
    static int bad;
    static final Locale DE = Locale.GERMAN;

    public static void main(String[] a) {
        // Netze
        for (Kind k : Kind.values()) {
            int[] tris = new int[2];
            boolean ok = true;
            float size = 0, height = 0;
            for (int v = 0; v < Understory.VARIANTS; v++)
                for (int l = 0; l < 2; l++) {
                    PlantMesh m = PlantMesh.build(k, v, l);
                    ok &= m.nt > 0 && m.v.length == m.nv * PlantMesh.STRIDE;
                    for (float x : m.v) ok &= Float.isFinite(x);
                    for (int t : m.tri) ok &= t >= 0 && t < m.nv;
                    ok &= m.size >= k.sizeMin && m.size <= k.sizeMax;
                    tris[l] += m.nt;
                    if (v == 0 && l == 0) { size = m.size; height = m.height; }
                    ok &= PlantMesh.build(k, v, l).nt == m.nt && Arrays.equals(PlantMesh.build(k, v, l).v, m.v);
                }
            check(ok && tris[1] < tris[0], String.format(DE, "%s: 4 Varianten gültig und gleich bei gleichem Startwert, Stufe 0 %,d / Stufe 1 %,d Dreiecke, Größe %.2f m, Höhe %.2f m",
                    k.name, tris[0] / 4, tris[1] / 4, size, height));
        }
        PlantMesh moss = PlantMesh.build(Kind.MOSS, 0, 0), log = PlantMesh.build(Kind.DEADWOOD, 0, 0), li = PlantMesh.build(Kind.LICHEN, 0, 0), dc = PlantMesh.build(Kind.DEVILS_CLUB, 0, 0);
        check(moss.height < 0.5 * moss.size && log.height < 0.25 * log.size + 1.6 && li.height > 0.1 && maxY(li) <= 1e-6 && dc.height > 0.8,
                String.format(DE, "Formen: Moos flach (%.2f / %.2f m), Totholz liegt (%.2f m hoch bei %.1f m), Flechte hängt (%.2f m), Teufelskeule steht (%.2f m)",
                        moss.height, moss.size, log.height, log.size, li.height, dc.height));
        // Licht
        double l0 = Kind.light(0, Kind.K_CONIFER), l8 = Kind.light(8, Kind.K_CONIFER);
        check(Math.abs(l0 - 1) < 1e-12 && Math.abs(l8 - Math.exp(-3.6)) < 1e-12, String.format(DE, "Beer-Lambert: offen %.3f, Blattflächenindex 8 → %.3f des Lichts", l0, l8));
        // Ansprüche: Lage des Maximums
        double[][] opt = new double[Kind.values().length][];
        for (Kind k : Kind.values()) {
            double best = -1, bl = 0, bm = 0;
            for (int i = 0; i <= 100; i++)
                for (int j = 0; j <= 100; j++) {
                    double light = Math.exp(Math.log(0.01) * (1 - i / 100.0)), m = j / 100.0, s = k.suitability(light, m);
                    if (s > best) { best = s; bl = light; bm = m; }
                }
            opt[k.ordinal()] = new double[]{bl, bm};
        }
        check(opt[Kind.BERRY.ordinal()][0] > opt[Kind.FERN.ordinal()][0] && opt[Kind.FERN.ordinal()][0] > opt[Kind.MOSS.ordinal()][0]
                        && opt[Kind.DEVILS_CLUB.ordinal()][1] > opt[Kind.MOSS.ordinal()][1] && opt[Kind.LITTER.ordinal()][1] < opt[Kind.FERN.ordinal()][1]
                        && opt[Kind.LITTER.ordinal()][0] < opt[Kind.FERN.ordinal()][0],
                String.format(DE, "Ansprüche: Licht Beeren %.2f > Farn %.2f > Moos %.2f; Feuchte Teufelskeule %.2f > Moos %.2f; Streu dunkel (%.2f) und trocken (%.2f)",
                        opt[Kind.BERRY.ordinal()][0], opt[Kind.FERN.ordinal()][0], opt[Kind.MOSS.ordinal()][0], opt[Kind.DEVILS_CLUB.ordinal()][1], opt[Kind.MOSS.ordinal()][1],
                        opt[Kind.LITTER.ordinal()][0], opt[Kind.LITTER.ordinal()][1]));
        // Dichte: gleichmäßiger Ort, 192 × 192 m
        List<Kind> ground = List.of(Kind.MOSS, Kind.FERN, Kind.DEVILS_CLUB, Kind.BERRY, Kind.DEADWOOD, Kind.LITTER);
        Understory.Site site = new Understory.Site() {
            public float ground(double x, double z) { return 0; }
            public double lai(double x, double z) { return 4; }
            public double moisture(double x, double z) { return 0.7; }
        };
        Understory u = new Understory(ground, site, 1958);
        u.completeNow(new double[]{96, 2, 96});
        int[] n = new int[ground.size()];
        int[] all = count(u, 0, 0, 192, 192, n);
        StringBuilder sb = new StringBuilder();
        boolean dens = true;
        for (int k = 0; k < ground.size(); k++) {
            double want = u.density(ground.get(k), 0, 0) * 192 * 192;
            double got = n[k];
            dens &= Math.abs(got - want) <= Math.max(0.05 * want, 3 * Math.sqrt(want));
            sb.append(String.format(DE, "%s %.0f/%d ", ground.get(k).name, want, n[k]));
        }
        check(dens, "Dichte bei Licht " + String.format(DE, "%.2f", u.light(0, 0)) + " und Feuchte 0,7 (erwartet/gepflanzt): " + sb.toString().trim());
        // Startwert
        Understory u2 = new Understory(ground, site, 1958), u3 = new Understory(ground, site, 1959);
        u2.completeNow(new double[]{96, 2, 96});
        u3.completeNow(new double[]{96, 2, 96});
        int[] n2 = new int[ground.size()], n3 = new int[ground.size()];
        count(u2, 0, 0, 192, 192, n2);
        count(u3, 0, 0, 192, 192, n3);
        check(Arrays.equals(n, n2) && !Arrays.equals(n, n3), "derselbe Startwert pflanzt dasselbe, ein anderer anderes");
        // Reichweite, Stufen, Ausblenden; Sichtkegel
        u.update(new double[]{96, 2, 96}, null, 0);
        boolean range = true, lod = true, fade = true;
        for (int b = 0; b < u.buckets(); b++) {
            Kind k = u.kindOf(b);
            for (int q = 0; q < u.count[b]; q++) {
                float[] o = u.bucket[b];
                double d = Math.sqrt(Math.pow(o[q * 8] - 96, 2) + Math.pow(o[q * 8 + 1] - 2, 2) + Math.pow(o[q * 8 + 2] - 96, 2));
                range &= d <= k.range + 1e-6;
                if (Understory.lodOf(b) == 0) lod &= d <= k.range * u.lod0 + 1e-6;
                fade &= o[q * 8 + 7] >= 0 && o[q * 8 + 7] <= 1 && (d < k.range * u.fadeFrom - 1e-6 ? o[q * 8 + 7] == 1 : true);
            }
        }
        int open = u.shown;
        float[] vp = lookDown(new double[]{96, 2, 96}, new double[]{196, 2, 96});
        u.update(new double[]{96, 2, 96}, vp, 0);
        check(range && lod && fade && u.shown < open * 0.6 && u.shown > open * 0.1,
                String.format(DE, "Reichweite je Art, Stufe 0 nur nah, Ausblenden 0..1 am Rand; Sichtkegel %,d von %,d", u.shown, open));
        // Bartflechten an Ankern
        Understory ul = new Understory(List.of(Kind.LICHEN), new Understory.Site() {
            public float ground(double x, double z) { return 0; }
            public double lai(double x, double z) { return 1.8; }
            public double moisture(double x, double z) { return 0.9; }
        }, 7);
        float[] an = new float[3 * 4000];
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < 4000; i++) { an[3 * i] = r.nextFloat() * 100; an[3 * i + 1] = 4 + 10 * r.nextFloat(); an[3 * i + 2] = r.nextFloat() * 100; }
        ul.setAnchors(an);
        ul.completeNow(new double[]{50, 5, 50});
        ul.lod0 = 1;
        ul.update(new double[]{50, 5, 50}, null, 0);
        double p = ul.density(Kind.LICHEN, 50, 50);
        int onAnchor = 0;
        java.util.Set<Long> set = new java.util.HashSet<>();
        for (int i = 0; i < 4000; i++) set.add(((long) Float.floatToIntBits(an[3 * i]) << 32) ^ Float.floatToIntBits(an[3 * i + 2]));
        int total = 0;
        for (int b = 0; b < ul.buckets(); b++)
            for (int q = 0; q < ul.count[b]; q++) {
                float[] o = ul.bucket[b];
                total++;
                if (set.contains(((long) Float.floatToIntBits(o[q * 8]) << 32) ^ Float.floatToIntBits(o[q * 8 + 2]))) onAnchor++;
            }
        int inRange = 0;
        for (int i = 0; i < 4000; i++) if (Math.sqrt(Math.pow(an[3 * i] - 50, 2) + Math.pow(an[3 * i + 1] - 5, 2) + Math.pow(an[3 * i + 2] - 50, 2)) <= Kind.LICHEN.range) inRange++;
        check(onAnchor == total && Math.abs(total - p * inRange) <= 4 * Math.sqrt(p * inRange) + 5,
                String.format(DE, "Bartflechten: %,d an Ankern (erwartet %.0f von %,d in Reichweite), alle genau am Anker", total, p * inRange, inRange));
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static float maxY(PlantMesh m) {
        float y = -1e9f;
        for (int i = 0; i < m.nv; i++) y = Math.max(y, m.v[i * PlantMesh.STRIDE + 1]);
        return y;
    }

    /** Zählt je Art die gepflanzten Pflanzen im Rechteck (über update ohne Sichtkegel, alle Stufen, große Reichweite). */
    static int[] count(Understory u, double x0, double z0, double x1, double z1, int[] n) {
        double rf = u.rangeFactor;
        u.rangeFactor = 50;
        u.update(new double[]{(x0 + x1) / 2, 2, (z0 + z1) / 2}, null, 0);
        for (int b = 0; b < u.buckets(); b++)
            for (int q = 0; q < u.count[b]; q++) {
                float x = u.bucket[b][q * 8], z = u.bucket[b][q * 8 + 2];
                if (x >= x0 && x < x1 && z >= z0 && z < z1) n[b / Understory.LODS / Understory.VARIANTS]++;
            }
        u.rangeFactor = rf;
        return n;
    }

    /** Projektion·Blick für einen Blick von e nach c (60°, 16:9). */
    static float[] lookDown(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= l; fy /= l; fz /= l;
        double sx = -fz, sz = fx;
        l = Math.hypot(sx, sz); sx /= l; sz /= l;
        double ux = -sz * fy, uy = sz * fx - sx * fz, uz = sx * fy;
        float[] v = {(float) sx, (float) ux, (float) -fx, 0, 0, (float) uy, (float) -fy, 0, (float) sz, (float) uz, (float) -fz, 0,
                (float) -(sx * e[0] + sz * e[2]), (float) -(ux * e[0] + uy * e[1] + uz * e[2]), (float) (fx * e[0] + fy * e[1] + fz * e[2]), 1};
        float t = (float) (1 / Math.tan(Math.toRadians(30))), asp = 16 / 9f, nn = 0.3f, ff = 500;
        float[] p = {t / asp, 0, 0, 0, 0, t, 0, 0, 0, 0, (ff + nn) / (nn - ff), -1, 0, 0, 2 * ff * nn / (nn - ff), 0};
        float[] r = new float[16];
        for (int col = 0; col < 4; col++) for (int row = 0; row < 4; row++) { float s = 0; for (int k = 0; k < 4; k++) s += p[k * 4 + row] * v[col * 4 + k]; r[col * 4 + row] = s; }
        return r;
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
        if (!ok) bad++;
    }
}
