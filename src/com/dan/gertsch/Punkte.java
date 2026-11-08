package com.dan.gertsch;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

/**
 * Wo der Holzschnitt sticht: ein Feld von Zellen, je Zelle ein möglicher Punkt mit einem <b>Rang</b> zwischen 0 und 1.
 * Ein Punkt wird gestochen, wenn der Ton an seiner Mitte über der Schwelle seines Rangs liegt; je heller, desto mehr
 * Punkte. Gleicher Rang heißt bei jedem Ton gleicher Punkt, darum wandern die Punkte nicht, wenn sich der Ton ändert.
 * <p>
 * <b>Rang.</b> Zwei Teile, gemischt nach {@link #ordnung}:
 * <ul>
 * <li>Blue Noise: Ränge nach dem Verfahren „Void and Cluster“ von Ulichney (1993) auf einer Kachel von {@link #N} × {@link #N}
 * Zellen. Jede Teilmenge der kleinsten Ränge ist gleichmäßig verteilt: kein Punkt nah am anderen, keine großen Lücken.</li>
 * <li>Weißes Rauschen: ein Zufall je Zelle ({@link Zufall}, ohne Kachel).</li>
 * </ul>
 * Gemischt wird v = o · blau + (1 − o) · weiß, und v kommt über seine Verteilung (ein Trapez) wieder gleichverteilt
 * heraus. Gertschs Punkte liegen nach der Messung zufällig (Clark–Evans R 1,01, {@link Druck#ordnung}); reines Blue
 * Noise ist auf der Kachel deutlich gleichmäßiger (R 1,63). Die Messung eines Drucks erkennt die Ordnung erst ab etwa
 * 0,8; die Vorgabe 0,75 lässt die dünnen Gegenden so zufällig wie bei Gertsch und macht die Mitteltöne um ein Drittel
 * ruhiger.
 * <p>
 * <b>Form.</b> Jeder Punkt ist eine Ellipse um die Mitte seiner Zelle, um höchstens {@link #zittern} verschoben, mit
 * Durchmesser {@link #groesse} Zellen (flächengleich; ± {@link #streuung}), bis {@link #laenge}-mal so lang wie breit, in
 * zufälliger Richtung. So sieht ein Stich mit dem Hohleisen aus, und das Raster der Zellen verschwindet.
 * <p>
 * <b>Eichung.</b> {@link #kurve()} gibt den Anteil Papier, wenn alle Punkte mit Rang unter f gestochen sind. Ein Punkt
 * mit Rang r wird darum bei Ton {@code kurve(r)} gestochen: dann ist der Anteil Papier gleich dem Ton.
 * <p>
 * Alle Rechnungen gibt es in GLSL gleich ({@code gl/stich.glsl}); die Kachel geht als Textur auf die Grafikkarte.
 */
public final class Punkte {
    /** Kantenlänge der Kachel der Blue-Noise-Ränge (Zellen). */
    public static final int N = 256;
    /** Breite des Gauß-Kerns von Void and Cluster (Zellen) [G]. */
    public static final double SIGMA = 1.5;
    /** Stellen der Eichkurve: {@code kurve()[k]} gilt für f = k / KURVE. */
    public static final int KURVE = 256;

    /** Anteil Blue Noise im Rang (0 Zufall, 1 reines Blue Noise). */
    public double ordnung = 0.75;
    /** Verschiebung der Mitte, höchstens so viele Zellen. */
    public double zittern = 0.3;
    /** Durchmesser eines Punkts in Zellen (flächengleiche Scheibe). */
    public double groesse = 1.45;
    /** Streuung des Durchmessers (± Anteil). */
    public double streuung = 0.12;
    /** Längstes Verhältnis Länge zu Breite. */
    public double laenge = 1.35;
    /** seed des weißen Teils und der Formen. */
    public int seed = 1;

    private static float[] blau;
    private float[] kurve;
    private String kurveFuer;

    /** Die Kachel der Blue-Noise-Ränge (N × N, zeilenweise, (Rang + 0,5) / N²), einmal gerechnet. */
    public static synchronized float[] blau() {
        if (blau == null) blau = leereUndHaufen(N, SIGMA, 1);
        return blau;
    }

    /** Hash der Zelle (i, j): Grundlage aller Züge. Wie {@code zelle(ivec2)} in stich.glsl. */
    public int zelle(int i, int j) { return Zufall.pcg(i + Zufall.pcg(j + Zufall.pcg(seed))); }

    /** Zug k der Zelle mit Hash h, gleichverteilt in [0, 1). */
    static float zug(int h, int k) { return Zufall.eins(h + k * 0x9E3779B9); }

    /** Der Rang der Zelle (i, j), gleichverteilt in (0, 1). */
    public float rang(int i, int j) {
        float b = blau()[Math.floorMod(j, N) * N + Math.floorMod(i, N)];
        return mischen(b, zug(zelle(i, j), 0), (float) ordnung);
    }

    /**
     * v = o · b + (1 − o) · u mit b, u gleichverteilt, über die Verteilung von v (Trapez) wieder gleichverteilt. Für
     * o = 1 bleibt b, für o = 0 bleibt u.
     */
    public static float mischen(float b, float u, float o) {
        float a = Math.min(o, 1 - o), c = Math.max(o, 1 - o);
        if (a < 1e-4f) return o >= 0.5f ? b : u;
        float v = o * b + (1 - o) * u;
        if (v < a) return v * v / (2 * a * c);
        if (v <= c) return (v - 0.5f * a) / c;
        float q = 1 - v;
        return 1 - q * q / (2 * a * c);
    }

    /**
     * Form des Punkts der Zelle (i, j): {Mitte x, Mitte y (in Zellen), Halbachse a, Halbachse b, cos, sin}. Wie
     * {@code form()} in stich.glsl.
     */
    public void form(int i, int j, float[] o) {
        int h = zelle(i, j);
        float z = (float) zittern, r = 0.5f * (float) groesse * (1 + (float) streuung * (2 * zug(h, 3) - 1));
        float asp = 1 + ((float) laenge - 1) * zug(h, 4), wurzel = (float) Math.sqrt(asp), wi = (float) Math.PI * zug(h, 5);
        o[0] = i + 0.5f + z * (2 * zug(h, 1) - 1);
        o[1] = j + 0.5f + z * (2 * zug(h, 2) - 1);
        o[2] = r * wurzel;
        o[3] = r / wurzel;
        o[4] = (float) Math.cos(wi);
        o[5] = (float) Math.sin(wi);
    }

    /**
     * Abstand des Punkts (x, y) (in Zellen) vom Rand der Ellipse f (aus {@link #form}), in Zellen; negativ innen.
     * Näherung nach Quílez: k0 (k0 − 1) / k1 mit k0 = |p/ab|, k1 = |p/ab²|; am Rand genau.
     */
    public static float abstand(float x, float y, float[] f) {
        float dx = x - f[0], dy = y - f[1];
        float px = f[4] * dx + f[5] * dy, py = -f[5] * dx + f[4] * dy;
        float ax = px / f[2], ay = py / f[3];
        float k0 = (float) Math.sqrt(ax * ax + ay * ay);
        float bx = px / (f[2] * f[2]), by = py / (f[3] * f[3]);
        float k1 = (float) Math.sqrt(bx * bx + by * by);
        return k1 < 1e-12f ? -Math.min(f[2], f[3]) : k0 * (k0 - 1) / k1;
    }

    /**
     * Einzug der Kante (px), damit die geglättete Kante die Fläche trifft. Die Abdeckung clamp(0,5 − d / aa) über eine
     * gewölbte Form gibt π aa² / 12 zu viel (nach Steiner: Fläche + Umfang · t + π t², gemittelt über t von −aa/2 bis
     * aa/2); um δ = π aa² / (12 U) eingezogen stimmt sie. U ≈ 2π √((a² + b²) / 2). Bei 1,7 px wären es sonst 11 % zu viel
     * Papier.
     */
    public static float einzug(float[] f, float s, float aa) {
        float rq = (float) Math.sqrt(0.5f * (f[2] * f[2] + f[3] * f[3])) * s;
        return Math.min(0.5f * Math.min(f[2], f[3]) * s, aa * aa / (24 * rq));
    }

    /** Wie weit (Zellen) ein Punkt höchstens in Nachbarzellen reicht: 2 genügt für die Vorgaben. */
    public int reichweite() {
        double r = 0.5 * groesse * (1 + streuung) * Math.sqrt(laenge) + zittern;
        return (int) Math.ceil(r - 0.5);
    }

    /**
     * Eichkurve: Anteil Papier, wenn alle Punkte mit Rang unter f = k / {@link #KURVE} gestochen sind (k = 0 …
     * KURVE). Gerechnet auf 256 × 256 Zellen mit 4 × 4 Proben je Zelle: je Probe der kleinste Rang der Punkte, die sie
     * decken; die Summenverteilung davon ist die Kurve. Steigt von 0 bis zur Deckung aller Punkte (fast 1).
     */
    public synchronized float[] kurve() {
        String fuer = ordnung + "/" + zittern + "/" + groesse + "/" + streuung + "/" + laenge + "/" + seed;
        if (kurve != null && fuer.equals(kurveFuer)) return kurve;
        int m = 256, p = 4, reich = reichweite();
        float[] rg = new float[m * m];
        float[][] fo = new float[m * m][6];
        for (int j = 0; j < m; j++) for (int i = 0; i < m; i++) { rg[j * m + i] = rang(i, j); form(i, j, fo[j * m + i]); }
        long[] fach = new long[KURVE + 1];
        long[][] teil = new long[m][];
        IntStream.range(reich, m - reich).parallel().forEach(j -> {
            long[] f = new long[KURVE + 1];
            for (int i = reich; i < m - reich; i++)
                for (int sy = 0; sy < p; sy++)
                    for (int sx = 0; sx < p; sx++) {
                        float x = i + (sx + 0.5f) / p, y = j + (sy + 0.5f) / p, min = 2;
                        for (int dj = -reich; dj <= reich; dj++)
                            for (int di = -reich; di <= reich; di++) {
                                int c = (j + dj) * m + i + di;
                                if (rg[c] < min && abstand(x, y, fo[c]) <= 0) min = rg[c];
                            }
                        f[min > 1 ? KURVE : Math.min(KURVE - 1, (int) (min * KURVE))]++;
                    }
            teil[j] = f;
        });
        long gesamt = 0;
        for (long[] f : teil) if (f != null) for (int k = 0; k <= KURVE; k++) { fach[k] += f[k]; gesamt += f[k]; }
        float[] c = new float[KURVE + 1];
        long s = 0;
        for (int k = 0; k <= KURVE; k++) {
            c[k] = (float) (s / (double) gesamt);
            if (k < KURVE) s += fach[k];
        }
        kurve = c;
        kurveFuer = fuer;
        return c;
    }

    /** Schwelle des Tons, ab der ein Punkt mit Rang r gestochen wird: die Eichkurve bei r, linear. */
    public static float schwelle(float[] kurve, float r) {
        float x = Math.max(0, Math.min(1, r)) * KURVE;
        int k = Math.min(KURVE - 1, (int) x);
        return kurve[k] + (x - k) * (kurve[k + 1] - kurve[k]);
    }

    // ------------------------------------------------------------------ Void and Cluster

    /**
     * Ränge nach Ulichney (1993) auf dem Torus n × n mit Gauß-Kern sigma: Anfangsmuster (10 % Zufall), entspannt, bis
     * der dichteste Haufen der leerste Platz ist; Phase 1 nimmt aus dem Anfangsmuster je den dichtesten Haufen (Ränge
     * abwärts), Phase 2 und 3 setzen je in die größte Lücke (Ränge aufwärts). Mit dem Kern über die Einsen ist „dichtester
     * Haufen der Nullen“ dasselbe wie „größte Lücke der Einsen“, darum sind Phase 2 und 3 eins.
     * <p>
     * Die Suche nach dem größten bzw. kleinsten Wert geht über Höchst- und Tiefstwerte je Zeile; nach jeder Änderung
     * werden nur die Zeilen im Kern neu gerechnet.
     */
    static float[] leereUndHaufen(int n, double sigma, long seed) {
        int r = (int) Math.ceil(3 * sigma), kn = 2 * r + 1;
        float[] kern = new float[kn * kn];
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++) kern[(dy + r) * kn + dx + r] = (float) Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma));
        Feld f = new Feld(n, r, kern);
        SplittableRandom rnd = new SplittableRandom(seed);
        int start = n * n / 10;
        for (int k = 0; k < start; ) {
            int i = rnd.nextInt(n * n);
            if (!f.eins[i]) { f.setzen(i, true); k++; }
        }
        // entspannen
        for (int schritt = 0; schritt < 4 * n * n; schritt++) {
            int haufen = f.haufen();
            f.setzen(haufen, false);
            int luecke = f.luecke();
            f.setzen(luecke, true);
            if (luecke == haufen) break;
        }
        boolean[] anfang = f.eins.clone();
        int[] rang = new int[n * n];
        // Phase 1: abwärts
        for (int k = start - 1; k >= 0; k--) {
            int haufen = f.haufen();
            f.setzen(haufen, false);
            rang[haufen] = k;
        }
        // zurück zum Anfangsmuster, dann Phase 2 und 3: aufwärts
        f = new Feld(n, r, kern);
        for (int i = 0; i < n * n; i++) if (anfang[i]) f.setzen(i, true);
        for (int k = start; k < n * n; k++) {
            int luecke = f.luecke();
            f.setzen(luecke, true);
            rang[luecke] = k;
        }
        float[] o = new float[n * n];
        for (int i = 0; i < n * n; i++) o[i] = (rang[i] + 0.5f) / (n * n);
        return o;
    }

    /** Muster und Energie auf dem Torus, mit Höchstwert der Einsen und Tiefstwert der Nullen je Zeile. */
    private static final class Feld {
        final int n, r, kn;
        final float[] kern, e;
        final boolean[] eins;
        final int[] zeileMax, zeileMin;

        Feld(int n, int r, float[] kern) {
            this.n = n;
            this.r = r;
            this.kn = 2 * r + 1;
            this.kern = kern;
            e = new float[n * n];
            eins = new boolean[n * n];
            zeileMax = new int[n];
            zeileMin = new int[n];
            for (int y = 0; y < n; y++) zeile(y);
        }

        void setzen(int i, boolean wert) {
            if (eins[i] == wert) return;
            eins[i] = wert;
            int x0 = i % n, y0 = i / n;
            float s = wert ? 1 : -1;
            for (int dy = -r; dy <= r; dy++) {
                int y = Math.floorMod(y0 + dy, n);
                for (int dx = -r; dx <= r; dx++) e[y * n + Math.floorMod(x0 + dx, n)] += s * kern[(dy + r) * kn + dx + r];
            }
            for (int dy = -r; dy <= r; dy++) zeile(Math.floorMod(y0 + dy, n));
        }

        private void zeile(int y) {
            int mx = -1, mn = -1;
            float vmx = Float.NEGATIVE_INFINITY, vmn = Float.POSITIVE_INFINITY;
            for (int x = 0, i = y * n; x < n; x++, i++) {
                if (eins[i]) { if (e[i] > vmx) { vmx = e[i]; mx = i; } }
                else if (e[i] < vmn) { vmn = e[i]; mn = i; }
            }
            zeileMax[y] = mx;
            zeileMin[y] = mn;
        }

        /** Die Eins mit der größten Energie (dichtester Haufen). */
        int haufen() {
            int b = -1;
            for (int y = 0; y < n; y++) { int i = zeileMax[y]; if (i >= 0 && (b < 0 || e[i] > e[b])) b = i; }
            return b;
        }

        /** Die Null mit der kleinsten Energie (größte Lücke). */
        int luecke() {
            int b = -1;
            for (int y = 0; y < n; y++) { int i = zeileMin[y]; if (i >= 0 && (b < 0 || e[i] < e[b])) b = i; }
            return b;
        }
    }

    /** Ränge der Kachel als Kopie (für Prüfungen). */
    public static float[] kachel() { return Arrays.copyOf(blau(), N * N); }
}
