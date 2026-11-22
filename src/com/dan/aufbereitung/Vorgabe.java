package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Vorgabe der Tide am Rand eines Gitters (Werkbuch HalligHooge Teil A, 12.5, Plan P4d), bis P5 sie mit
 * {@code tide.bc} ersetzt: je Randkante Mittelwasser, Amplitude und Phase einer Partialtide aus drei Rastern (EasyGSH
 * 2015: M2-Amplitude, M2-Phase, Tidemittelwasser), dazu Pegel aus dem Rezept mit denselben Werten.
 * <ul>
 * <li>Ein Punkt nimmt das nächste Pixel, in dem alle drei Raster einen Wert haben und das Gelände unter dem
 * Niedrigwasser der Tide liegt (Mittel − Amplitude; Höhe aus dem Stapel mit Schritt der Rasterzelle), im Umkreis
 * {@code umkreis} [G]. Auf Watt, das trockenfällt, sind Mittelwasser und Amplitude von EasyGSH nur über die nasse Zeit
 * gerechnet (am Rand bei Husum: Mittelwasser 0,62 statt 0,11 m).</li>
 * <li>Eine Randkante ohne solches Pixel übernimmt die Werte der nächsten Kante derselben Seite mit Wert (Watt, auf dem
 * EasyGSH keine M2 rechnet); eine Seite ganz ohne Werte bleibt ohne (im Lauf Wand).</li>
 * <li>Randkanten in der Folge West (j), Ost (j), Süd (i), Nord (i), Ort an der Mitte der Kante.</li>
 * </ul>
 */
public final class Vorgabe {
    /** Ein Raster, ganz gelesen. */
    static final class Raster {
        final GeoTiff t;
        final float[] v;

        Raster(Path p) throws IOException {
            try (GeoTiff g = GeoTiff.oeffne(p)) {
                t = g;
                v = g.lies(0, 0, g.breite, g.hoehe);
            }
        }

        float wert(int s, int z) { return s < 0 || z < 0 || s >= t.breite || z >= t.hoehe ? Float.NaN : v[z * t.breite + s]; }
    }

    /** Werte an einem Punkt: Mittel, Amplitude, Phase, Abstand zum Pixel (m); NaN ohne. */
    record Wert(double mittel, double amplitude, double phase, double abstand) {
        static final Wert KEINER = new Wert(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        boolean da() { return !Double.isNaN(amplitude); }
    }

    final Raster a, ph, m;
    final double umkreis;
    /** Stapel für die Höhe unter dem Pixel (null: ohne Bedingung). */
    final Gelaende gelaende;
    /** Pixel mit Werten, aber über dem Niedrigwasser (verworfen). */
    long trocken;

    Vorgabe(Path amplitude, Path phase, Path mittel, double umkreis, Gelaende gelaende) throws IOException {
        this.gelaende = gelaende;
        a = new Raster(amplitude);
        ph = new Raster(phase);
        m = new Raster(mittel);
        this.umkreis = umkreis;
        for (Raster r : new Raster[] {ph, m})
            if (r.t.breite != a.t.breite || r.t.hoehe != a.t.hoehe || r.t.x0 != a.t.x0 || r.t.y0 != a.t.y0 || r.t.dx != a.t.dx || r.t.dy != a.t.dy)
                throw new IOException(r.t.datei + ": anderes Raster als " + a.t.datei);
    }

    /** Das nächste Pixel mit allen drei Werten im Umkreis. */
    Wert am(double x, double y) {
        GeoTiff t = a.t;
        int s0 = (int) Math.round(t.spalte(x)), z0 = (int) Math.round(t.zeile(y)), r = (int) Math.ceil(umkreis / Math.abs(t.dx)) + 1;
        Wert best = Wert.KEINER;
        double bd = Double.POSITIVE_INFINITY;
        for (int z = z0 - r; z <= z0 + r; z++)
            for (int s = s0 - r; s <= s0 + r; s++) {
                float va = a.wert(s, z), vp = ph.wert(s, z), vm = m.wert(s, z);
                if (Float.isNaN(va) || Float.isNaN(vp) || Float.isNaN(vm)) continue;
                double d = Math.hypot(t.x(s) - x, t.y(z) - y);
                if (d <= umkreis && d < bd && !ueberNiedrigwasser(s, z, vm - va)) {
                    bd = d;
                    best = new Wert(vm, va, vp, d);
                }
            }
        return best;
    }

    /** Liegt das Gelände unter dem Pixel über dem Niedrigwasser nw (dann ist das Pixel nicht immer nass)? Gemerkt je Pixel. */
    private final java.util.Map<Integer, Boolean> trockenJe = new java.util.HashMap<>();

    private boolean ueberNiedrigwasser(int s, int z, double nw) {
        if (gelaende == null) return false;
        return trockenJe.computeIfAbsent(z * a.t.breite + s, k -> {
            float h = gelaende.hoehe(a.t.x(s), a.t.y(z), Math.abs(a.t.dx));
            boolean tr = !Float.isNaN(h) && h >= nw;
            if (tr) trocken++;
            return tr;
        });
    }

    /** Ergebnis für ein Gitter. */
    record Ergebnis(Wert[] rand, int[] direkt, int[] uebernommen, int[] ohne) { }

    /** Die Randkanten eines Gitters. */
    Ergebnis rand(Gitter g) {
        int n = 2 * g.nx + 2 * g.ny;
        Wert[] w = new Wert[n];
        int[] anfang = {0, g.ny, 2 * g.ny, 2 * g.ny + g.nx}, laenge = {g.ny, g.ny, g.nx, g.nx};
        int[] direkt = new int[4], ueber = new int[4], ohne = new int[4];
        for (int s = 0; s < 4; s++) {
            for (int k = 0; k < laenge[s]; k++) {
                double[] p = ort(g, s, k);
                w[anfang[s] + k] = am(p[0], p[1]);
                if (w[anfang[s] + k].da()) direkt[s]++;
            }
            // Lücken: nächste Kante derselben Seite mit Wert
            Wert[] neu = new Wert[laenge[s]];
            for (int k = 0; k < laenge[s]; k++) {
                Wert x = w[anfang[s] + k];
                if (x.da()) { neu[k] = x; continue; }
                int q = -1;
                for (int d = 1; d < laenge[s] && q < 0; d++) {
                    if (k - d >= 0 && w[anfang[s] + k - d].da()) q = k - d;
                    else if (k + d < laenge[s] && w[anfang[s] + k + d].da()) q = k + d;
                }
                if (q < 0) { neu[k] = Wert.KEINER; ohne[s]++; continue; }
                Wert y = w[anfang[s] + q];
                double[] p = ort(g, s, k), pq = ort(g, s, q);
                neu[k] = new Wert(y.mittel, y.amplitude, y.phase, y.abstand + Math.hypot(p[0] - pq[0], p[1] - pq[1]));
                ueber[s]++;
            }
            System.arraycopy(neu, 0, w, anfang[s], laenge[s]);
        }
        return new Ergebnis(w, direkt, ueber, ohne);
    }

    /** Mitte der Randkante k auf der Seite s (0 West, 1 Ost, 2 Süd, 3 Nord). */
    static double[] ort(Gitter g, int s, int k) {
        return switch (s) {
            case 0 -> new double[] {g.x0, g.y0 + (k + 0.5) * g.dy};
            case 1 -> new double[] {g.x0 + g.nx * g.dx, g.y0 + (k + 0.5) * g.dy};
            case 2 -> new double[] {g.x0 + (k + 0.5) * g.dx, g.y0};
            default -> new double[] {g.x0 + (k + 0.5) * g.dx, g.y0 + g.ny * g.dy};
        };
    }

    /** Die Pegel aus dem Rezept: Schlüssel {@code pegel.<Name> = Ost Nord}. */
    static Map<String, double[]> pegel(Rezept r) {
        Map<String, double[]> p = new LinkedHashMap<>();
        for (String k : r.alle().keySet()) if (k.startsWith("pegel.")) p.put(k.substring(6), r.zahlen(k, 2));
        return p;
    }

    static String z(double v, String fmt) { return Double.isNaN(v) ? "nan" : String.format(Locale.ROOT, fmt, v); }

    /** Die Zeilen von {@code vorgabe.txt} (ohne Kopf): Randkanten, dann Pegel (Ort, Werte). */
    static List<String> zeilen(Gitter g, Ergebnis e, Map<String, double[]> orte, Map<String, Wert> pegel) {
        List<String> l = new ArrayList<>();
        int[] anfang = {0, g.ny, 2 * g.ny, 2 * g.ny + g.nx}, laenge = {g.ny, g.ny, g.nx, g.nx};
        l.add("# rand Seite (0 West, 1 Ost, 2 Süd, 3 Nord) Nummer Ost Nord Mittelwasser(m NHN) Amplitude(m) Phase(°) Abstand(m)");
        for (int s = 0; s < 4; s++)
            for (int k = 0; k < laenge[s]; k++) {
                double[] p = ort(g, s, k);
                Wert w = e.rand[anfang[s] + k];
                l.add(String.format(Locale.ROOT, "rand %d %d %.1f %.1f %s %s %s %s", s, k, p[0], p[1], z(w.mittel, "%.4f"), z(w.amplitude, "%.4f"), z(w.phase, "%.2f"), z(w.abstand, "%.0f")));
            }
        l.add("# pegel Name Ost Nord Mittelwasser(m NHN) Amplitude(m) Phase(°) Abstand(m)");
        for (Map.Entry<String, Wert> p : pegel.entrySet()) {
            Wert w = p.getValue();
            double[] o = orte.get(p.getKey());
            l.add(String.format(Locale.ROOT, "pegel %s %.1f %.1f %s %s %s %s", p.getKey(), o[0], o[1], z(w.mittel, "%.4f"), z(w.amplitude, "%.4f"), z(w.phase, "%.2f"), z(w.abstand, "%.0f")));
        }
        return l;
    }
}
