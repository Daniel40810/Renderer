package com.dan.lauf;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Eine Sonde (seit W5, Werkbuch HalligHooge 126, F209 A): Zeitreihen an einem Ort über alle Stände einer Quelle –
 * Wasserstand (m NHN; NaN trocken), Wassertiefe (m; 0 trocken), Strömung (m/s, Betrag; nur wenn die Quelle sie hat) und
 * Wind (m/s, Betrag, 10 m). Der Wasserstand kommt aus einer {@link Pegelreihe} an der Zelle des Orts (Umkreis 0), der
 * Grund aus der Packung des Gebiets; Strömung und Wind aus ihren eigenen Gittern (nächste Zelle), zu ihren eigenen Zeiten.
 * <p>
 * Liest jeden Stand einmal ganz (wie die Pegelreihe); auf deinem Rechner rund eine Sekunde je Sonde [S].
 */
public final class Sonde {
    /** Eine Reihe: Name, Einheit, Zeiten (s seit {@link #start}) und Werte (NaN: kein Wert). */
    public record Reihe(String name, String einheit, double[] t, double[] wert) {
        /** Linear zwischen den Ständen; außerhalb NaN. */
        public double bei(double ts) {
            if (t.length == 0 || ts < t[0] || ts > t[t.length - 1]) return Double.NaN;
            int i = java.util.Arrays.binarySearch(t, ts);
            if (i >= 0) return wert[i];
            int b = -i - 1, a = b - 1;
            double w = (ts - t[a]) / (t[b] - t[a]);
            return wert[a] + w * (wert[b] - wert[a]);
        }

        public double min() { double m = Double.NaN; for (double x : wert) if (!Double.isNaN(x) && !(m <= x)) m = x; return m; }

        public double max() { double m = Double.NaN; for (double x : wert) if (!Double.isNaN(x) && !(m >= x)) m = x; return m; }
    }

    public static final String WASSERSTAND = "Wasserstand", TIEFE = "Wassertiefe", STROEMUNG = "Strömung", WIND = "Wind";

    public final String name;
    public final double ost, nord;
    public final Instant start;
    /** Grund an der Zelle (m NHN; NaN ohne Gebiet). */
    public final double grund;
    /** Die Pegelreihe an der Zelle (für Marken: Hoch- und Niedrigwasser). */
    public final Pegelreihe pegel;
    public final List<Reihe> reihen;
    /** Dauer des Lesens (s). */
    public final double sekunden;

    private Sonde(String name, double ost, double nord, Instant start, Pegelreihe p, List<Reihe> r, double s) {
        this.name = name;
        this.ost = ost;
        this.nord = nord;
        this.start = start;
        this.pegel = p;
        this.grund = p.grund;
        this.reihen = List.copyOf(r);
        this.sekunden = s;
    }

    public Reihe reihe(String name) {
        for (Reihe r : reihen) if (r.name.equals(name)) return r;
        return null;
    }

    /** Liest die Sonde aus einer Quelle (Archiv oder Lauf); {@code ge} für den Grund (null: ohne Tiefe). */
    public static Sonde aus(Bild.Quelle q, Instant start, Gebiet ge, String name, double ost, double nord) {
        long n0 = System.nanoTime();
        Pegelreihe p = Pegelreihe.aus(q, start, ge, name, ost, nord, 0);
        double[] t = p.zeiten(), eta = p.pegel();
        boolean[] nass = p.nassAlle();
        double[] w = new double[t.length], d = new double[t.length];
        for (int i = 0; i < t.length; i++) {
            w[i] = nass[i] ? eta[i] : Double.NaN;
            d[i] = Double.isNaN(p.grund) ? Double.NaN : nass[i] ? Math.max(0, eta[i] - p.grund) : 0;
        }
        List<Reihe> r = new ArrayList<>();
        r.add(new Reihe(WASSERSTAND, "m NHN", t, w));
        r.add(new Reihe(TIEFE, "m", t, d));
        // Strömung: eigenes Gitter, eigene Zeiten
        Gitter sg = q.stroemungGitter();
        if (sg != null) {
            int c = zelle(sg, p.gitter, ost, nord);
            if (c >= 0) {
                double[] ts = q.stroemungZeiten();
                float[] uv = new float[2];
                double[] s = new double[ts.length];
                for (int i = 0; i < ts.length; i++) s[i] = q.stroemungZelle(ts[i], c, uv) ? Math.hypot(uv[0], uv[1]) : Double.NaN;
                r.add(new Reihe(STROEMUNG, "m/s", ts, s));
            }
        }
        // Wind: zu den Zeiten der Stände
        Gitter wg = q.windGitter();
        if (wg != null) {
            int c = zelle(wg, p.gitter, ost, nord);
            if (c >= 0) {
                float[] uv = new float[2];
                double[] s = new double[t.length];
                for (int i = 0; i < t.length; i++) s[i] = q.windZelle(t[i], c, uv) ? Math.hypot(uv[0], uv[1]) : Double.NaN;
                r.add(new Reihe(WIND, "m/s", t, s));
            }
        }
        return new Sonde(name, ost, nord, start, p, r, (System.nanoTime() - n0) / 1e9);
    }

    /** Liest die Sonde aus dem Archiv eines Szenarios, ohne es zu öffnen (für den Vergleich, F210 A). */
    public static Sonde ausArchiv(Szenario s, Gebiet ge, String name, double ost, double nord) throws Exception {
        if (s.archiv == null) throw new IllegalArgumentException("Sonde: " + s.name + " ist live, ohne Archiv");
        try (Archivquelle q = new Archivquelle(Path.of(s.archiv.getPath()))) {
            return aus(q, q.start, ge, name, ost, nord);
        }
    }

    /** Die Zelle des Orts in einem Gitter (UTM oder Grad; Grad über das Gitter des Wassers); -1: außerhalb. */
    static int zelle(Gitter g, Gitter wasser, double ost, double nord) {
        double x = ost, y = nord;
        if (g.lage != Gitter.Lage.UTM) {
            double[] ll = wasser.grad(ost, nord);
            x = ll[0];
            y = ll[1];
        }
        if (!g.enthaelt(x, y)) return -1;
        return Math.max(0, Math.min(g.nx - 1, g.i(x))) + g.nx * Math.max(0, Math.min(g.ny - 1, g.j(y)));
    }

    /** Als CSV: eine Zeile je Zeit der Stände (UTC), Spalten je Reihe (Strömung linear an diese Zeiten). */
    public String csv() {
        StringBuilder sb = new StringBuilder("# Sonde ").append(name).append(String.format(java.util.Locale.ROOT, " Ost %.1f Nord %.1f, Grund %.3f m NHN%n", ost, nord, grund));
        sb.append("zeit_utc");
        for (Reihe r : reihen) sb.append(';').append(r.name).append(" (").append(r.einheit).append(')');
        sb.append('\n');
        Reihe w = reihen.get(0);
        for (int i = 0; i < w.t.length; i++) {
            sb.append(start.plusMillis(Math.round(w.t[i] * 1000)));
            for (Reihe r : reihen) {
                double x = r.t == w.t ? r.wert[i] : r.bei(w.t[i]);
                sb.append(';').append(Double.isNaN(x) ? "" : String.format(java.util.Locale.ROOT, "%.4f", x));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
