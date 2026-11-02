package com.dan.clipmap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Mehrere Quellen übereinander, von fein nach grob (etwa Lidar 0,5 m, IfSAR 5 m, 3DEP 30 m). Für eine Abtastung mit
 * Zellweite c nimmt der Stapel die gröbste Quelle, die noch feiner oder gleich c ist (die feinste, wenn keine so fein
 * ist); wo sie nichts hat, die nächst gröbere, dann die feineren. Außerhalb aller Quellen gilt {@link #outside}
 * (Vorgabe NaN; die Clipmap setzt dann ihren eigenen Ersatzwert).
 */
public final class TileStack implements Source {
    private final Source[] sources;
    public volatile float outside = Float.NaN;

    public TileStack(Source... s) {
        sources = s.clone();
        Arrays.sort(sources, Comparator.comparingDouble(Source::finestCell));
    }

    /**
     * Alle Unterordner von dir mit {@code STUFE.txt} (etwa {@code daten/gelaende/kern_0.5m}, {@code szene_5m},
     * {@code fern_30m}); e0, n0: Ost und Nord des Ursprungs.
     */
    public static TileStack folders(File dir, double e0, double n0) throws IOException {
        File[] d = dir.listFiles(f -> new File(f, "STUFE.txt").isFile());
        if (d == null || d.length == 0) throw new IOException("keine Kachelordner mit STUFE.txt in " + dir.getPath());
        List<Source> l = new ArrayList<>();
        for (File f : d) l.add(new TileFolder(f, e0, n0));
        return new TileStack(l.toArray(new Source[0]));
    }

    public Source[] sources() { return sources.clone(); }

    @Override public double finestCell() { return sources[0].finestCell(); }

    /** Index der Quelle, die für Zellweite c zuerst gefragt wird. */
    int first(double c) {
        int p = 0;
        for (int i = 0; i < sources.length; i++) if (sources[i].finestCell() <= c * 1.0001) p = i;
        return p;
    }

    @Override public float height(double x, double z, double cell) {
        int p = first(cell);
        for (int i = p; i < sources.length; i++) {
            float v = sources[i].height(x, z, cell);
            if (!Float.isNaN(v)) return v;
        }
        for (int i = p - 1; i >= 0; i--) {
            float v = sources[i].height(x, z, cell);
            if (!Float.isNaN(v)) return v;
        }
        return outside;
    }

    /** Das Rechteck aus der Quelle für c; nur die Punkte, wo sie nichts hat, fragen die übrigen Quellen. */
    @Override public void fill(double x0, double z0, double cell, int w, int h, float[] out) {
        int p = first(cell);
        sources[p].fill(x0, z0, cell, w, h, out);
        boolean missing = false;
        for (float v : out) if (Float.isNaN(v)) { missing = true; break; }
        if (!missing) return;
        float[] tmp = new float[out.length];
        int[] order = new int[sources.length - 1];
        int n = 0;
        for (int i = p + 1; i < sources.length; i++) order[n++] = i;
        for (int i = p - 1; i >= 0; i--) order[n++] = i;
        for (int s : order) {
            sources[s].fill(x0, z0, cell, w, h, tmp);
            missing = false;
            for (int i = 0; i < out.length; i++)
                if (Float.isNaN(out[i])) {
                    out[i] = tmp[i];
                    if (Float.isNaN(out[i])) missing = true;
                }
            if (!missing) return;
        }
        for (int i = 0; i < out.length; i++) if (Float.isNaN(out[i])) out[i] = outside;
    }

    @Override public String describe() {
        StringBuilder b = new StringBuilder();
        for (Source s : sources) b.append(b.length() > 0 ? " · " : "").append(s.describe());
        return b.toString();
    }

    /** Welche Quelle für Zellweite c gilt (für Anzeigen). */
    public String sourceFor(double c) {
        Source s = sources[first(c)];
        return s instanceof TileFolder t ? t.name : String.format(Locale.GERMAN, "%s m", s.finestCell());
    }
}
