package com.dan.core.pruef;

import com.dan.core.astro.Partialtide;
import com.dan.core.zeit.Zeit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Harmonische Analyse einer Pegelreihe (Werkbuch HalligHooge Teil A, 5.9): kleinste Quadrate für das Mittel und je
 * Partialtide Amplitude und Phase, mit Knotenfaktoren und dem Rayleigh-Kriterium.
 * <p>
 * Modell: {@code y(t) = Z₀ + Σ f(t) · [a·cos(V(t) + u(t)) + b·sin(V(t) + u(t))]}; f und u gelten zu jedem Wert der
 * Reihe, nicht nur zur Mitte. Amplitude A = √(a² + b²), Phase G = atan2(b, a) (Greenwich): Die Gleichgewichtstide
 * hat G = 0.
 * <p>
 * <b>Rayleigh:</b> Zwei Tiden lassen sich aus einer Reihe der Dauer T nur trennen, wenn sich ihre Zahl der Wellen in
 * T um mindestens {@code rayleigh} (üblich 1) unterscheidet. {@link #trennbar} nimmt die Tiden in der Reihenfolge
 * ihrer Gleichgewichtsamplitude auf und lässt aus, was sich von einer schon aufgenommenen nicht trennen lässt.
 */
public final class Harmonik {
    /** Eine gefundene Tide: Amplitude (m oder die Einheit der Reihe) und Phase G (Grad, 0..360). */
    public record Tide(Partialtide tide, double amplitude, double phaseGrad) { }

    /** Ergebnis einer Analyse. */
    public record Ergebnis(double mittel, List<Tide> tiden, List<Partialtide> ausgelassen, double restRms, int werte, double dauerStunden) {
        /** Die Tide mit diesem Namen oder null. */
        public Tide tide(String name) {
            for (Tide t : tiden) if (t.tide.name.equalsIgnoreCase(name)) return t;
            return null;
        }

        /** Der Pegel aus den gefundenen Tiden zur Zeit t. */
        public double wert(Instant t) {
            double y = mittel, n = Partialtide.knotenlaenge(t);
            double[] g = Partialtide.grundargumente(t);
            for (Tide q : tiden) {
                double[] fu = q.tide.fu(n);
                y += fu[0] * q.amplitude * Math.cos(Math.toRadians(q.tide.argumentGrad(g) + fu[1] - q.phaseGrad));
            }
            return y;
        }
    }

    /** Die Tiden, die sich aus einer Reihe der Dauer T (Stunden) trennen lassen, und in {@code ausgelassen} der Rest. */
    public static List<Partialtide> trennbar(List<Partialtide> kandidaten, double dauerStunden, double rayleigh, List<Partialtide> ausgelassen) {
        List<Partialtide> ordnung = new ArrayList<>(kandidaten), gut = new ArrayList<>();
        ordnung.sort(Comparator.comparingDouble((Partialtide p) -> -p.gleichgewicht));          // stabil: gleiche bleiben in ihrer Reihenfolge
        for (Partialtide p : ordnung) {
            boolean geht = p.gradJeStunde() * dauerStunden >= 360 * rayleigh;
            for (Partialtide q : gut) if (Math.abs(p.gradJeStunde() - q.gradJeStunde()) * dauerStunden < 360 * rayleigh) geht = false;
            if (geht) gut.add(p);
            else if (ausgelassen != null) ausgelassen.add(p);
        }
        return gut;
    }

    /** Analyse einer Reihe mit festem Schritt; die Tiden wählt das Rayleigh-Kriterium aus den Kandidaten. */
    public static Ergebnis analyse(Instant start, double schrittSekunden, double[] werte, List<Partialtide> kandidaten, double rayleigh) {
        double[] t = new double[werte.length];
        double s0 = start.getEpochSecond() + start.getNano() * 1e-9;
        for (int i = 0; i < t.length; i++) t[i] = s0 + i * schrittSekunden;
        return analyse(t, werte, kandidaten, rayleigh);
    }

    /** Analyse einer Reihe mit beliebigen Zeiten (Sekunden seit 1970 UTC); NaN-Werte werden übergangen. */
    public static Ergebnis analyse(double[] sekundenSeit1970, double[] werte, List<Partialtide> kandidaten, double rayleigh) {
        int n = werte.length;
        double t0 = Double.MAX_VALUE, t1 = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) if (!Double.isNaN(werte[i])) { t0 = Math.min(t0, sekundenSeit1970[i]); t1 = Math.max(t1, sekundenSeit1970[i]); }
        double dauer = (t1 - t0) / 3600;
        List<Partialtide> aus = new ArrayList<>(), tiden = trennbar(kandidaten, dauer, rayleigh, aus);
        int k = tiden.size(), m = 2 * k + 1, gut = 0;
        double[][] ata = new double[m][m];
        double[] aty = new double[m], zeile = new double[m];
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(werte[i])) continue;
            basis(Zeit.ausSekunden(sekundenSeit1970[i]), tiden, zeile);
            for (int p = 0; p < m; p++) {
                aty[p] += zeile[p] * werte[i];
                for (int q = p; q < m; q++) ata[p][q] += zeile[p] * zeile[q];
            }
            gut++;
        }
        if (gut < m) throw new IllegalArgumentException("Harmonische Analyse: " + gut + " Werte für " + m + " Unbekannte");
        for (int p = 0; p < m; p++) for (int q = 0; q < p; q++) ata[p][q] = ata[q][p];
        double[] x = loese(ata, aty);
        List<Tide> gefunden = new ArrayList<>();
        for (int j = 0; j < k; j++) {
            double a = x[1 + 2 * j], b = x[2 + 2 * j], g = Math.toDegrees(Math.atan2(b, a));
            gefunden.add(new Tide(tiden.get(j), Math.hypot(a, b), g < 0 ? g + 360 : g));
        }
        double q = 0;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(werte[i])) continue;
            basis(Zeit.ausSekunden(sekundenSeit1970[i]), tiden, zeile);
            double y = 0;
            for (int p = 0; p < m; p++) y += zeile[p] * x[p];
            q += (y - werte[i]) * (y - werte[i]);
        }
        return new Ergebnis(x[0], gefunden, aus, Math.sqrt(q / gut), gut, dauer);
    }

    private static void basis(Instant t, List<Partialtide> tiden, double[] zeile) {
        double[] g = Partialtide.grundargumente(t);
        double n = (360 - g[4]) % 360;
        zeile[0] = 1;
        for (int j = 0; j < tiden.size(); j++) {
            Partialtide p = tiden.get(j);
            double[] fu = p.fu(n);
            double w = Math.toRadians(p.argumentGrad(g) + fu[1]);
            zeile[1 + 2 * j] = fu[0] * Math.cos(w);
            zeile[2 + 2 * j] = fu[0] * Math.sin(w);
        }
    }

    /** Löst A·x = b (A symmetrisch positiv definit) nach Gauß mit Spaltenpivot. */
    static double[] loese(double[][] a, double[] b) {
        int n = b.length;
        for (int s = 0; s < n; s++) {
            int p = s;
            for (int z = s + 1; z < n; z++) if (Math.abs(a[z][s]) > Math.abs(a[p][s])) p = z;
            if (Math.abs(a[p][s]) < 1e-300) throw new ArithmeticException("Harmonische Analyse: Tiden nicht trennbar (singulär)");
            double[] h = a[s];
            a[s] = a[p];
            a[p] = h;
            double hb = b[s];
            b[s] = b[p];
            b[p] = hb;
            for (int z = s + 1; z < n; z++) {
                double f = a[z][s] / a[s][s];
                if (f == 0) continue;
                for (int c = s; c < n; c++) a[z][c] -= f * a[s][c];
                b[z] -= f * b[s];
            }
        }
        double[] x = new double[n];
        for (int z = n - 1; z >= 0; z--) {
            double s = b[z];
            for (int c = z + 1; c < n; c++) s -= a[z][c] * x[c];
            x[z] = s / a[z][z];
        }
        return x;
    }

    /**
     * Harmonische Analyse vieler Orte mit denselben Zeiten, etwa jeder Zelle eines Gitters (P5d): Die Matrix der
     * Normalgleichungen ist für alle Orte dieselbe, je Ort wird nur die rechte Seite gesammelt. Die Tiden stehen fest
     * (vorher mit {@link #trennbar} gewählt); Werte, die NaN sind, gelten als 0 und sollten nicht vorkommen. {@link #dazu}
     * aus einem Faden; {@link #loesung} und {@link #tide} auch aus vielen zugleich (die Umkehrung baut der erste, die
     * anderen warten).
     */
    public static final class Sammler {
        public final List<Partialtide> tiden;
        public final int punkte;
        private final int m;
        private final double[][] ata;
        private final double[] aty, zeile;
        private int werte;
        private double[][] inverse;

        public Sammler(List<Partialtide> tiden, int punkte) {
            this.tiden = List.copyOf(tiden);
            this.punkte = punkte;
            m = 2 * tiden.size() + 1;
            ata = new double[m][m];
            aty = new double[Math.multiplyExact(punkte, m)];
            zeile = new double[m];
        }

        /** Ein Stand aller Orte zur Zeit t. */
        public void dazu(Instant t, double[] wert) {
            basis(t, tiden, zeile);
            for (int p = 0; p < m; p++) for (int q = p; q < m; q++) ata[p][q] += zeile[p] * zeile[q];
            final double[] z = zeile.clone();
            java.util.stream.IntStream.range(0, punkte).parallel().forEach(c -> {
                double y = wert[c];
                if (Double.isNaN(y)) return;
                int o = c * m;
                for (int p = 0; p < m; p++) aty[o + p] += z[p] * y;
            });
            werte++;
            synchronized (this) { inverse = null; }
        }

        /** Zahl der Stände. */
        public int werte() { return werte; }

        /** Die Konstanten eines Orts: {Mittel, a₁, b₁, a₂, b₂, …}. */
        public double[] loesung(int punkt) {
            double[][] inverse = inverse();
            double[] x = new double[m];
            int o = punkt * m;
            for (int p = 0; p < m; p++) {
                double s = 0;
                for (int q = 0; q < m; q++) s += inverse[q][p] * aty[o + q];
                x[p] = s;
            }
            return x;
        }

        /** Die Umkehrung der Normalmatrix, einmal je Stand der Summen; auch aus parallelen Aufrufen (erst ganz gebaut, dann gesetzt). */
        private synchronized double[][] inverse() {
            if (inverse == null) {
                if (werte < m) throw new IllegalStateException("Harmonische Analyse: " + werte + " Stände für " + m + " Unbekannte");
                double[][] a = new double[m][m];
                for (int p = 0; p < m; p++) for (int q = 0; q < m; q++) a[p][q] = p <= q ? ata[p][q] : ata[q][p];
                double[][] inv = new double[m][];
                for (int q = 0; q < m; q++) {
                    double[] e = new double[m];
                    e[q] = 1;
                    double[][] k = new double[m][];
                    for (int p = 0; p < m; p++) k[p] = a[p].clone();
                    inv[q] = loese(k, e);
                }
                inverse = inv;
            }
            return inverse;
        }

        /** Amplitude und Phase G (Grad, 0..360) der Tide k am Ort; die Tide mit diesem Namen über {@link #index}. */
        public double[] tide(int punkt, int k) {
            double[] x = loesung(punkt);
            double a = x[1 + 2 * k], b = x[2 + 2 * k], g = Math.toDegrees(Math.atan2(b, a));
            return new double[] {Math.hypot(a, b), g < 0 ? g + 360 : g};
        }

        /** Index der Tide mit diesem Namen oder −1. */
        public int index(String name) {
            for (int k = 0; k < tiden.size(); k++) if (tiden.get(k).name.equalsIgnoreCase(name)) return k;
            return -1;
        }
    }

    private Harmonik() { }
}
