package com.dan.understory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Unterwuchs um das Auge: wo welche Pflanze steht und welche Stufe sie im nächsten Bild bekommt. Kacheln zu
 * {@link #tile} m werden beim ersten Bedarf aus Startwert und Kachel bepflanzt (immer gleich) und behalten, solange sie in
 * Reichweite liegen. Je Art ein Gitter mit dem Abstand 1/√Dichte; jeder Platz trägt eine Pflanze mit der Wahrscheinlichkeit
 * {@link Kind#suitability} aus Licht (Beer-Lambert unter dem Kronendach, {@link Site#lai}) und Feuchte. Bartflechten
 * hängen an Ankerpunkten der App ({@link #setAnchors}), etwa an Ästen der Bäume.
 * <p>
 * Je Bild füllt {@link #update} Eimer nach Art, Variante und Stufe (0 nah, 1 fern); je Pflanze 8 Werte: x, y, z, Drehung,
 * Maßstab, Tönung, Phase, Ausblenden (1 sichtbar … 0 weg, am Rand der Reichweite).
 */
public final class Understory {
    /** Der Ort: Boden, Blattflächenindex des Kronendachs darüber, Feuchte. */
    public interface Site {
        float ground(double x, double z);
        /** Blattflächenindex (m²/m²) des Kronendachs über (x, z); 0 = offen, alter Regenwald 6 bis 10. */
        double lai(double x, double z);
        /** Feuchte 0..1 (Quellhang, Bachrand, Senke 1; Kuppe, Rücken 0). */
        double moisture(double x, double z);
    }

    /** Qualitätsstufen (Paketvertrag 7): Faktor auf die Reichweite und auf die Dichte. */
    public enum Quality {
        LOW(0.55, 0.6), MEDIUM(0.8, 0.8), HIGH(1.0, 1.0), ULTRA(1.35, 1.0);
        public final double range, density;
        Quality(double range, double density) { this.range = range; this.density = density; }
    }

    public static final int LODS = 2, STRIDE = 8, VARIANTS = 4;
    static final int TSTRIDE = 9;   // x, y, z, Drehung, Maßstab, Tönung, Art, Variante, Phase

    public final List<Kind> kinds;
    public final Site site;
    private final long seed;
    public double tile = 16, lightK = Kind.K_CONIFER;
    /** Reichweite und Dichte als Faktor ({@link #apply}); Anteil der Reichweite für Stufe 0; Anteil, ab dem ausgeblendet wird. */
    public double rangeFactor = 1, densityFactor = 1, lod0 = 0.35, fadeFrom = 0.8;
    private final Map<Long, float[]> tiles = new HashMap<>();
    private final Map<Long, List<float[]>> anchors = new HashMap<>();
    public final float[][] bucket;
    public final int[] count;
    public int planted, shown;

    public Understory(List<Kind> kinds, Site site, long seed) {
        this.kinds = List.copyOf(kinds);
        this.site = site;
        this.seed = seed;
        bucket = new float[buckets()][];
        count = new int[buckets()];
        for (int i = 0; i < bucket.length; i++) bucket[i] = new float[STRIDE * 64];
    }

    public int buckets() { return kinds.size() * VARIANTS * LODS; }
    public int bucketOf(int k, int v, int lod) { return (k * VARIANTS + v) * LODS + lod; }
    public Kind kindOf(int bucket) { return kinds.get(bucket / LODS / VARIANTS); }
    public static int variantOf(int bucket) { return bucket / LODS % VARIANTS; }
    public static int lodOf(int bucket) { return bucket % LODS; }

    public void apply(Quality q) {
        rangeFactor = q.range;
        if (densityFactor != q.density) { densityFactor = q.density; clear(); }
    }

    public void clear() { tiles.clear(); planted = 0; }

    /** Licht am Boden (0..1) bei (x, z). */
    public double light(double x, double z) { return Kind.light(site.lai(x, z), lightK); }

    /** Erwartete Dichte der Art (je m²; Flechte: Anteil der Anker) an (x, z). */
    public double density(Kind k, double x, double z) { return k.density * densityFactor * k.suitability(light(x, z), site.moisture(x, z)); }

    /**
     * Ankerpunkte für Bartflechten (je 3 Werte x, y, z: wo der Bart hängt, etwa an Ästen in 3 bis 20 m Höhe). Ersetzt die
     * bisherigen; die Kacheln werden neu bepflanzt.
     */
    public void setAnchors(float[] xyz) {
        anchors.clear();
        for (int i = 0; i + 2 < xyz.length; i += 3) {
            long key = key((int) Math.floor(xyz[i] / tile), (int) Math.floor(xyz[i + 2] / tile));
            anchors.computeIfAbsent(key, q -> new ArrayList<>()).add(new float[]{xyz[i], xyz[i + 1], xyz[i + 2]});
        }
        clear();
    }

    // ------------------------------------------------------------------ Pflanzen

    static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    static long mix(long h) {
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L; h ^= h >>> 33;
        return h;
    }

    static double u01(long h) { return (h >>> 11) * 0x1.0p-53; }

    /** Bepflanzt die Kachel (ti, tj). */
    float[] plant(int ti, int tj) {
        float[] out = new float[TSTRIDE * 32];
        int n = 0;
        double x0 = ti * tile, z0 = tj * tile;
        for (int k = 0; k < kinds.size(); k++) {
            Kind kind = kinds.get(k);
            long base = mix(seed ^ (ti * 0x9E3779B97F4A7C15L) ^ (tj * 0xC2B2AE3D27D4EB4FL) ^ (k * 0x165667B19E3779F9L));
            if (kind == Kind.LICHEN) {
                List<float[]> an = anchors.get(key(ti, tj));
                if (an == null) continue;
                for (int a = 0; a < an.size(); a++) {
                    float[] p = an.get(a);
                    long h = mix(base + a * 0x9E3779B97F4A7C15L);
                    if (u01(h) >= Math.min(1, density(kind, p[0], p[2]))) continue;
                    out = put(out, n++, p[0], p[1], p[2], h, k, 1);
                }
                continue;
            }
            // Gitter mindestens so dicht wie verlangt, der Rest über die Wahrscheinlichkeit (sonst runden 16 m auf ganze Zellen)
            double want = kind.density * densityFactor;
            int m = Math.max(1, (int) Math.ceil(tile * Math.sqrt(want)));
            double cell = tile / m, keep = Math.min(1, want * cell * cell);
            for (int j = 0; j < m; j++)
                for (int i = 0; i < m; i++) {
                    long h = mix(base + (j * 1031L + i) * 0x9E3779B97F4A7C15L);
                    double x = x0 + (i + 0.1 + 0.8 * u01(mix(h + 1))) * cell, z = z0 + (j + 0.1 + 0.8 * u01(mix(h + 2))) * cell;
                    double p = kind.suitability(light(x, z), site.moisture(x, z));
                    if (u01(h) >= p * keep) continue;
                    float scale = (float) ((0.75 + 0.5 * u01(mix(h + 3))) * Math.pow(p, 0.3));
                    out = put(out, n++, (float) x, site.ground(x, z), (float) z, h, k, scale);
                }
        }
        planted += n;
        return Arrays.copyOf(out, n * TSTRIDE);
    }

    private float[] put(float[] out, int n, float x, float y, float z, long h, int k, float scale) {
        if ((n + 1) * TSTRIDE > out.length) out = Arrays.copyOf(out, out.length * 2);
        int o = n * TSTRIDE;
        out[o] = x; out[o + 1] = y; out[o + 2] = z;
        out[o + 3] = (float) (u01(mix(h + 4)) * 2 * Math.PI);
        out[o + 4] = scale;
        out[o + 5] = (float) u01(mix(h + 5));
        out[o + 6] = k;
        out[o + 7] = (int) (u01(mix(h + 6)) * VARIANTS);
        out[o + 8] = (float) (u01(mix(h + 7)) * 2 * Math.PI);
        return out;
    }

    // ------------------------------------------------------------------ je Bild

    private final double[][] planes = new double[4][4];

    /** Wählt die Pflanzen um eye für Projektion·Blick vp (null: alle in Reichweite); höchstens maxNew Kacheln neu. */
    public void update(double[] eye, float[] vp, int maxNew) {
        Arrays.fill(count, 0);
        shown = 0;
        double reach = 0;
        for (Kind k : kinds) reach = Math.max(reach, k.range * rangeFactor);
        planesOn = false;
        if (vp != null) frustum(vp);
        int r = (int) Math.ceil(reach / tile) + 1;
        int ci = (int) Math.floor(eye[0] / tile), cj = (int) Math.floor(eye[2] / tile);
        List<long[]> missing = new ArrayList<>();
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++) {
                double tx = (i + 0.5) * tile - eye[0], tz = (j + 0.5) * tile - eye[2], d2 = tx * tx + tz * tz;
                if (d2 > (reach + tile) * (reach + tile)) continue;
                float[] t = tiles.get(key(i, j));
                if (t == null) { missing.add(new long[]{i, j, (long) d2}); continue; }
                if (vp != null && !visible((i + 0.5) * tile, site.ground((i + 0.5) * tile, (j + 0.5) * tile) + 5, (j + 0.5) * tile, tile * 0.75 + 25)) continue;
                emit(t, eye);
            }
        missing.sort((a, b) -> Long.compare(a[2], b[2]));
        for (int q = 0; q < Math.min(maxNew, missing.size()); q++) tiles.put(key((int) missing.get(q)[0], (int) missing.get(q)[1]), plant((int) missing.get(q)[0], (int) missing.get(q)[1]));
        if (tiles.size() > 3 * (2 * r + 1) * (2 * r + 1)) {
            double keep = reach * 1.5 + tile;
            tiles.keySet().removeIf(kk -> {
                int i = (int) (kk >> 32), j = (int) (long) kk;
                double dx = (i + 0.5) * tile - eye[0], dz = (j + 0.5) * tile - eye[2];
                return dx * dx + dz * dz > keep * keep;
            });
        }
    }

    /** Bepflanzt alle Kacheln in Reichweite auf einmal (für Standbilder). */
    public void completeNow(double[] eye) { update(eye, null, Integer.MAX_VALUE); }

    private void emit(float[] t, double[] eye) {
        for (int q = 0; q < t.length; q += TSTRIDE) {
            int k = (int) t[q + 6];
            Kind kind = kinds.get(k);
            double range = kind.range * rangeFactor;
            double dx = t[q] - eye[0], dy = t[q + 1] - eye[1], dz = t[q + 2] - eye[2], d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > range) continue;
            double rad = (kind.sizeMax + 1) * t[q + 4];
            if (planesOn && !visible(t[q], t[q + 1] + rad * 0.5, t[q + 2], rad)) continue;
            float fade = (float) Math.min(1, (range - d) / (range * (1 - fadeFrom)));
            int lod = d < range * lod0 ? 0 : 1;
            int b = bucketOf(k, (int) t[q + 7], lod);
            if ((count[b] + 1) * STRIDE > bucket[b].length) bucket[b] = Arrays.copyOf(bucket[b], bucket[b].length * 2);
            float[] o = bucket[b];
            int p = count[b]++ * STRIDE;
            o[p] = t[q]; o[p + 1] = t[q + 1]; o[p + 2] = t[q + 2]; o[p + 3] = t[q + 3];
            o[p + 4] = t[q + 4]; o[p + 5] = t[q + 5]; o[p + 6] = t[q + 8]; o[p + 7] = fade;
            shown++;
        }
    }

    /** Pflanzen je Art im letzten {@link #update}. */
    public int[] kindCounts() {
        int[] n = new int[kinds.size()];
        for (int b = 0; b < count.length; b++) n[b / LODS / VARIANTS] += count[b];
        return n;
    }

    private boolean planesOn;

    private void frustum(float[] m) {
        planesOn = true;
        for (int p = 0; p < 4; p++) {
            int row = p >> 1;
            double s = (p & 1) == 0 ? 1 : -1;
            double[] q = planes[p];
            for (int c = 0; c < 4; c++) q[c] = m[c * 4 + 3] + s * m[c * 4 + row];
            double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
            for (int c = 0; c < 4; c++) q[c] /= l;
        }
    }

    private boolean visible(double x, double y, double z, double rad) {
        if (!planesOn) return true;
        for (double[] q : planes) if (q[0] * x + q[1] * y + q[2] * z + q[3] < -rad) return false;
        return true;
    }
}
