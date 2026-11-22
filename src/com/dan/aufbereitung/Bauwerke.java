package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deiche und Siele als Kanten eines Rechengitters (Werkbuch HalligHooge Teil A, 10.4, Schritt 5; für hydro in P4).
 * Grund: Ein Damm im Inneren einer Zelle wird vom Subgrid nicht gesehen (5.4), das Wasser liefe durch ihn hindurch.
 * <ol>
 * <li><b>Linien</b> aus OSM nach einem Filter des Rezepts (Deiche, Dämme), am Gebiet des Gitters geschnitten.</li>
 * <li><b>Lücken:</b> Endet eine Linie höchstens {@code luecke} vor einer anderen, wird die Verbindung eine eigene
 * Linie, wenn das Gelände dort einen Damm zeigt (Krone längs der Verbindung höchstens {@code rinne} unter der
 * niedrigeren der beiden Enden) oder wenn OSM dort einen Durchlass nennt (dann mit Siel). OSM zeichnet Deiche oft in
 * Stücken, die sich nicht berühren; im Gitter wäre der Ring sonst offen.</li>
 * <li><b>Kantenzug.</b> Jede Linie wird auf einen vierfach verbundenen Zug von Kanten des Gitters gelegt: von Ecke zu
 * Ecke immer der Schritt, dessen Ecke der Linie näher liegt. Eine Kante liegt höchstens eine halbe Zelldiagonale von
 * der Linie entfernt.</li>
 * <li><b>Krone je Kante</b> als Profil aus {@code fein} Stellen (je Pixel des Feingeländes eine): an jeder Stelle das
 * höchste Pixel auf einem Querschnitt von ±{@code korridor} quer zur Linie, durch den Punkt der Linie, der der Stelle
 * am nächsten liegt. Die Krone der Kante ist die niedrigste Stelle des Profils: Dort läuft das Wasser zuerst über.
 * Legen sich zwei Linien auf dieselbe Kante, gilt je Stelle die höhere.</li>
 * <li><b>Siele:</b> wo OSM einen Durchlass nennt (ein Schleusentor nah an der Linie, ein Durchlass oder Graben mit dem
 * Namen eines Siels, der die Linie kreuzt oder an ihr endet, eine Brücke in der Linie selbst) oder wo die Krone längs
 * der Linie eine Rinne zeigt, die bis zum Fuß hinabreicht (mindestens {@code rinne} unter dem Median der Umgebung und
 * höchstens {@code rinne} über dem niedrigeren Fuß 10–25 m neben der Linie). Kandidaten im Abstand
 * {@code sielAbstand} sind ein Siel. Die Kanten in seiner Nähe bekommen, wo ihr Profil tiefer liegt, die Krone der
 * Umgebung (das Tor ist zu); das Siel selbst trägt Sohle, Querschnitt A und Beiwert μ [G]. Tiefe Stellen ohne Siel
 * stehen im Bericht ({@link #niedrig}).</li>
 * <li><b>Geschnittene Zellen:</b> Ein Pixel zwischen Linie und Kantenzug liegt in einer Zelle auf der falschen Seite.
 * Es bekommt als Mindesthöhe die Krone an der Linie; gebacken wird mit max(Gelände, Mindesthöhe). Der Deich wird im
 * Modell breiter, das Bild (der Stapel) bleibt.</li>
 * <li><b>Ringe:</b> Zellen, die vom Rand des Gitters nur über eine Deichkante zu erreichen sind.</li>
 * </ol>
 * Ablage: {@code bauwerke.txt} (Kanten, Siele, Ringe) und die Stufe {@code mindesthoehe/} im Format von
 * {@link Gelaende}.
 */
public final class Bauwerke {
    /** Eine Linie aus OSM, im Gebiet: Quelle (z. B. way/123) und Punkte in UTM. */
    public record Linie(String quelle, double[] x, double[] y, double[] normale) {
        public Linie(String quelle, double[] x, double[] y) { this(quelle, x, y, null); }

        /** Einheitsnormale (nach links) des Stücks s; bei einer Verbindung die des Deichs, den sie fortsetzt. */
        public double[] normale(int s) {
            if (normale != null) return normale;
            double dx = x[s + 1] - x[s], dy = y[s + 1] - y[s], l = Math.max(1e-12, Math.hypot(dx, dy));
            return new double[] {-dy / l, dx / l};
        }

        public double laenge() {
            double l = 0;
            for (int i = 1; i < x.length; i++) l += Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
            return l;
        }

        public boolean geschlossen() { return x.length > 3 && x[0] == x[x.length - 1] && y[0] == y[y.length - 1]; }
    }

    /** Eine Kante mit Bauwerk. */
    public static final class Kante {
        /** Index wie {@code Subgrid.kanteX} (quer zu x) bzw. {@code kanteY}. */
        public final int index, i, j;
        public final boolean querX;
        /** Krone je Stelle (m NHN), von Süd nach Nord bzw. West nach Ost. */
        public final float[] profil;
        public String art = "deich";
        public final TreeSet<String> quellen = new TreeSet<>();
        /** Für das Siel angehoben (das geschlossene Tor). */
        public boolean angehoben;

        Kante(int index, boolean querX, int i, int j, int fein) {
            this.index = index;
            this.querX = querX;
            this.i = i;
            this.j = j;
            profil = new float[fein];
            Arrays.fill(profil, Float.NaN);
        }

        public double krone() {
            double m = Double.POSITIVE_INFINITY;
            for (float v : profil) if (!Float.isNaN(v)) m = Math.min(m, v);
            return m == Double.POSITIVE_INFINITY ? Double.NaN : m;
        }

        public double mittel() {
            double s = 0;
            int n = 0;
            for (float v : profil) if (!Float.isNaN(v)) { s += v; n++; }
            return n == 0 ? Double.NaN : s / n;
        }
    }

    /** Ein Siel an einer Kante. */
    public record Siel(String name, int kante, double ost, double nord, double sohle, double krone, double a, double mu, String quellen) { }

    /** Ein Ring: Zellen, die nur über Deichkanten vom Rand zu erreichen sind. */
    public record Ring(int nummer, int zellen, double flaeche, double niedrigsteKrone, int niedrigsteKante, double hoechsteKrone, int randKanten,
                       double seitenwechsel, double gehoben, List<String> namen) { }

    /**
     * Einstellungen [G]: Querschnitt ±m, Abstand für Siele (m), Tiefe der Rinne (m), Umgebung längs der Linie (±m),
     * A (m²), μ, größte Lücke zwischen dem Ende einer Linie und einer anderen, die geschlossen wird (m).
     */
    public double korridor = 5, sielAbstand = 20, rinne = 0.5, umgebung = 50, sielA = 1.0, sielMu = 0.6, luecke = 60;
    /** Geschlossene und offen gebliebene Lücken (Bericht). */
    public final List<String> luecken = new ArrayList<>();
    /** Durchlässe nach OSM: Punkte (Schleusentor) und Wege (Durchlass, Graben mit Namen eines Siels). */
    private final List<Osm.Punkt> hinweisPunkte = new ArrayList<>();
    private final List<Osm.Weg> hinweisWege = new ArrayList<>();
    private Osm.Filter bruecke = Osm.Filter.lies("");
    /** Verbindungen, die ein Siel schließen: Index der Linie → Hinweis. */
    private final Map<Integer, String> sielLuecken = new HashMap<>();
    private final List<Osm.Weg> quellWege = new ArrayList<>();

    public final Gitter gitter;
    public final int fein;
    private final Gelaende gelaende;
    private final Gelaende.Stufe pixel;
    public final List<Linie> linien = new ArrayList<>();
    public final TreeMap<Integer, Kante> kanten = new TreeMap<>();
    public final List<Siel> siele = new ArrayList<>();
    public final List<Ring> ringe = new ArrayList<>();
    /** Je Zelle: Nummer des Rings (ab 1), 0 außen. */
    public int[] ring;
    /** Je Ring: der Ring, in dem er ganz liegt (0: keiner). */
    public int[] innen;
    /** Kronen längs jeder Linie (je 1 m): x, y, Krone. */
    private final List<double[][]> laengs = new ArrayList<>();
    /** Pfade: Ecken je Linie und für jeden Schritt das Stück der Linie. */
    private final List<int[]> pfadA = new ArrayList<>(), pfadB = new ArrayList<>(), pfadStueck = new ArrayList<>();
    /** Mindesthöhe je Kachel der Pixelstufe (Schlüssel tx, ty). */
    private final Map<Long, float[]> mindest = new HashMap<>();
    public long seitenwechselPixel, gehobenPixel;
    private final StringBuilder bericht = new StringBuilder();

    /** @param gelaende der Kachelstapel; seine feinste Stufe gibt die Pixel, {@code fein} = Zelle / Pixel */
    public Bauwerke(Gitter gitter, Gelaende gelaende) {
        if (gitter.lage != Gitter.Lage.UTM || gitter.dx != gitter.dy) throw new IllegalArgumentException("Bauwerke: nur UTM mit quadratischen Zellen");
        this.gitter = gitter;
        this.gelaende = gelaende;
        this.pixel = gelaende.stufen()[0];
        double f = gitter.dx / pixel.zelle;
        if (Math.abs(f - Math.round(f)) > 1e-9) throw new IllegalArgumentException("Bauwerke: Zelle " + gitter.dx + " ist kein Vielfaches des Pixels " + pixel.zelle);
        this.fein = (int) Math.round(f);
    }

    // ------------------------------------------------------------------ Linien

    /** Nimmt die Wege aus OSM, die {@code auswahl} und nicht {@code ohne} passen, im Gebiet des Gitters. */
    public void linien(Osm osm, Osm.Filter auswahl, Osm.Filter ohne) {
        double x0 = gitter.x0, y0 = gitter.y0, x1 = gitter.x1(), y1 = gitter.y1();
        for (Osm.Weg w : osm.wege) {
            if (!auswahl.passt(w.tags()) || ohne.passt(w.tags())) continue;
            quellWege.add(w);
            // in Stücke innerhalb des Gebiets schneiden (Liang–Barsky je Abschnitt)
            List<double[]> s = new ArrayList<>();
            for (int k = 0; k + 1 < w.ost().length; k++) {
                double[] c = schneide(w.ost()[k], w.nord()[k], w.ost()[k + 1], w.nord()[k + 1], x0, y0, x1, y1);
                if (c == null) {
                    if (s.size() >= 2) linien.add(linie(w.name(), s));
                    s.clear();
                    continue;
                }
                double[] a = {c[0], c[1]}, b = {c[2], c[3]};
                if (!s.isEmpty() && (s.get(s.size() - 1)[0] != a[0] || s.get(s.size() - 1)[1] != a[1])) {
                    if (s.size() >= 2) linien.add(linie(w.name(), s));
                    s.clear();
                }
                if (s.isEmpty()) s.add(a);
                s.add(b);
            }
            if (s.size() >= 2) linien.add(linie(w.name(), s));
        }
    }

    /** Eine Linie von Hand (für die Prüfung). */
    public void linie(String quelle, double[] x, double[] y) { linien.add(new Linie(quelle, x.clone(), y.clone())); }

    private static Linie linie(String q, List<double[]> s) {
        double[] x = new double[s.size()], y = new double[s.size()];
        for (int i = 0; i < s.size(); i++) {
            x[i] = s.get(i)[0];
            y[i] = s.get(i)[1];
        }
        return new Linie(q, x, y);
    }

    private static double[] schneide(double ax, double ay, double bx, double by, double x0, double y0, double x1, double y1) {
        double t0 = 0, t1 = 1, dx = bx - ax, dy = by - ay;
        double[] p = {-dx, dx, -dy, dy}, q = {ax - x0, x1 - ax, ay - y0, y1 - ay};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) return null;
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0) t0 = Math.max(t0, t);
                else t1 = Math.min(t1, t);
            }
        }
        if (t0 > t1) return null;
        return new double[] {t0 == 0 ? ax : ax + t0 * dx, t0 == 0 ? ay : ay + t0 * dy, t1 == 1 ? bx : ax + t1 * dx, t1 == 1 ? by : ay + t1 * dy};
    }

    // ------------------------------------------------------------------ Bau

    /** Kantenzüge, Kronen, Siele, Ringe und Mindesthöhen. */
    public void bauen(Osm osm, Osm.Filter sielPunkte, Osm.Filter sielWege, Osm.Filter sielBruecke, Map<String, double[]> sonden) {
        if (osm != null) {
            for (Osm.Punkt p : osm.punkte) if (sielPunkte.passt(p.tags())) hinweisPunkte.add(p);
            for (Osm.Weg w : osm.wege) if (sielWege.passt(w.tags())) hinweisWege.add(w);
        }
        this.bruecke = sielBruecke;
        luecken();
        for (int l = 0; l < linien.size(); l++) {
            pfad(linien.get(l));
            laengs.add(laengs(linien.get(l)));
        }
        for (int l = 0; l < linien.size(); l++) profile(l);
        siele();
        ringe(sonden);
        mindesthoehen();
    }

    /**
     * Lücken: Endet eine Linie höchstens {@code luecke} vor einer anderen (oder vor einem fernen Teil ihrer selbst),
     * wird die Verbindung zum nächsten Punkt eine eigene Linie, wenn das Gelände dort einen Damm zeigt: Die niedrigste
     * Krone längs der Verbindung liegt höchstens {@code rinne} unter der niedrigeren der beiden Enden. OSM zeichnet
     * Deiche oft in Stücken, die sich nicht berühren; im Gitter wäre der Ring dann offen.
     */
    private void luecken() {
        int n0 = linien.size();
        long[] lz = {Long.MIN_VALUE};
        float[][] tz = {null};
        List<Linie> neu = new ArrayList<>();
        for (int l = 0; l < n0; l++) {
            Linie L = linien.get(l);
            if (L.geschlossen()) continue;
            double gesamt = L.laenge();
            for (int ende = 0; ende < 2; ende++) {
                int e = ende == 0 ? 0 : L.x.length - 1, e2 = ende == 0 ? 1 : L.x.length - 2;
                double ex = L.x[e], ey = L.y[e];
                double[] best = null;
                int bm = -1;
                for (int m = 0; m < n0; m++) {
                    Linie M = linien.get(m);
                    double[] q = naechster(M, ex, ey, 0, M.x.length);
                    if (q == null) continue;
                    if (m == l) {
                        double w = laengsWeg(L, q);
                        if ((ende == 0 ? w : gesamt - w) <= 2 * luecke) continue;
                    }
                    if (best == null || q[4] < best[4]) { best = q; bm = m; }
                }
                if (best == null || best[4] > luecke || best[4] == 0) continue;
                Linie M = linien.get(bm);
                double dx = best[0] - ex, dy = best[1] - ey, ll = Math.hypot(dx, dy);
                // Richtung des Deichs am Ende: vom Punkt rund 10 m zurück zum Ende; quer dazu die Querschnitte
                double[] r = zurueck(L, ende == 0, 10);
                double sx = ex - r[0], sy = ey - r[1], sl = Math.max(1e-12, Math.hypot(sx, sy)), nx = -sy / sl, ny = sx / sl;
                double kv = Double.POSITIVE_INFINITY;
                for (double d = 0; d <= ll; d += pixel.zelle) kv = Math.min(kv, querschnitt(ex + dx * d / ll, ey + dy * d / ll, nx, ny, lz, tz));
                double kE = querschnitt(ex, ey, nx, ny, lz, tz), kN = querschnitt(best[0], best[1], nx, ny, lz, tz);
                boolean damm = kv >= Math.min(kE, kN) - rinne;
                String siel = damm ? null : hinweis(ex, ey, best[0], best[1]);
                String t = f("%s–%s: %.1f m bei O %.0f N %.0f, Krone %.2f m (Enden %.2f / %.2f m)", L.quelle(), M.quelle(), ll, ex, ey, kv, kE, kN);
                luecken.add((damm ? "geschlossen: " : siel != null ? "mit Siel geschlossen (" + siel + "): " : "offen: ") + t);
                if (damm || siel != null) {
                    if (siel != null) sielLuecken.put(linien.size() + neu.size(), siel);
                    neu.add(new Linie("Lücke:" + L.quelle() + "-" + M.quelle(), new double[] {ex, best[0]}, new double[] {ey, best[1]}, new double[] {nx, ny}));
                }
            }
        }
        linien.addAll(neu);
    }

    /** Punkt rund {@code d} Meter längs der Linie vor ihrem Anfang bzw. Ende. */
    private static double[] zurueck(Linie L, boolean anfang, double d) {
        int n = L.x.length;
        double rest = d;
        for (int k = 0; k + 1 < n; k++) {
            int a = anfang ? k : n - 1 - k, b = anfang ? k + 1 : n - 2 - k;
            double l = Math.hypot(L.x[b] - L.x[a], L.y[b] - L.y[a]);
            if (l >= rest && l > 0) return new double[] {L.x[a] + (L.x[b] - L.x[a]) * rest / l, L.y[a] + (L.y[b] - L.y[a]) * rest / l};
            rest -= l;
        }
        return anfang ? new double[] {L.x[n - 1], L.y[n - 1]} : new double[] {L.x[0], L.y[0]};
    }

    /** Ein Durchlass nach OSM nahe der Strecke a–b (Punkt im Abstand ≤ sielAbstand, Weg im Abstand ≤ sielAbstand / 2). */
    private String hinweis(double ax, double ay, double bx, double by) {
        for (Osm.Punkt p : hinweisPunkte)
            if (abstandStrecke(p.ost(), p.nord(), ax, ay, bx, by) <= sielAbstand) return "node/" + p.id() + name(p.tags());
        for (Osm.Weg w : hinweisWege)
            for (int u = 0; u + 1 < w.ost().length; u++) {
                if (kreuzung(ax, ay, bx, by, w.ost()[u], w.nord()[u], w.ost()[u + 1], w.nord()[u + 1]) != null) return w.name() + name(w.tags());
                if (abstandStrecke(w.ost()[u], w.nord()[u], ax, ay, bx, by) <= sielAbstand / 2) return w.name() + name(w.tags());
            }
        return null;
    }

    static double abstandStrecke(double x, double y, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, ll = dx * dx + dy * dy;
        double t = ll == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / ll));
        return Math.hypot(x - ax - t * dx, y - ay - t * dy);
    }

    private static double laengsWeg(Linie L, double[] q) {
        double w = 0;
        int s = (int) q[2];
        for (int i = 0; i < s; i++) w += Math.hypot(L.x[i + 1] - L.x[i], L.y[i + 1] - L.y[i]);
        return w + q[3] * Math.hypot(L.x[s + 1] - L.x[s], L.y[s + 1] - L.y[s]);
    }

    /** Vierfach verbundener Kantenzug: Ecken (a, b) mit x = x0 + a · dx. */
    private void pfad(Linie L) {
        List<int[]> ecken = new ArrayList<>();
        List<Integer> stueck = new ArrayList<>();
        int a = ecke(L.x[0], gitter.x0, gitter.dx, gitter.nx), b = ecke(L.y[0], gitter.y0, gitter.dy, gitter.ny);
        ecken.add(new int[] {a, b});
        for (int s = 0; s + 1 < L.x.length; s++) {
            int aq = ecke(L.x[s + 1], gitter.x0, gitter.dx, gitter.nx), bq = ecke(L.y[s + 1], gitter.y0, gitter.dy, gitter.ny);
            double px = L.x[s], py = L.y[s], qx = L.x[s + 1], qy = L.y[s + 1];
            while (a != aq || b != bq) {
                int na = a, nb = b;
                if (a != aq && b != bq) {
                    int ax = a + Integer.signum(aq - a), by = b + Integer.signum(bq - b);
                    double d1 = abstandGerade(gitter.x0 + ax * gitter.dx, gitter.y0 + b * gitter.dy, px, py, qx, qy);
                    double d2 = abstandGerade(gitter.x0 + a * gitter.dx, gitter.y0 + by * gitter.dy, px, py, qx, qy);
                    if (d1 <= d2) na = ax;
                    else nb = by;
                } else if (a != aq) na = a + Integer.signum(aq - a);
                else nb = b + Integer.signum(bq - b);
                a = na;
                b = nb;
                ecken.add(new int[] {a, b});
                stueck.add(s);
            }
        }
        // Spitzen (hin und zurück) entfernen
        for (int k = 1; k + 1 < ecken.size(); ) {
            int[] p = ecken.get(k - 1), q = ecken.get(k + 1);
            if (p[0] == q[0] && p[1] == q[1]) {
                ecken.remove(k + 1);
                ecken.remove(k);
                stueck.remove(k);
                stueck.remove(k - 1);
                if (k > 1) k--;
            } else k++;
        }
        int n = ecken.size();
        int[] pa = new int[n], pb = new int[n], ps = new int[Math.max(0, n - 1)];
        for (int k = 0; k < n; k++) {
            pa[k] = ecken.get(k)[0];
            pb[k] = ecken.get(k)[1];
        }
        for (int k = 0; k + 1 < n; k++) ps[k] = stueck.get(k);
        pfadA.add(pa);
        pfadB.add(pb);
        pfadStueck.add(ps);
    }

    private static int ecke(double v, double v0, double d, int n) { return Math.max(0, Math.min(n, (int) Math.round((v - v0) / d))); }

    /** Abstand eines Punkts zur Geraden durch p und q (zum Punkt p, wenn p = q). */
    static double abstandGerade(double x, double y, double px, double py, double qx, double qy) {
        double dx = qx - px, dy = qy - py, l = Math.hypot(dx, dy);
        if (l == 0) return Math.hypot(x - px, y - py);
        return Math.abs((x - px) * dy - (y - py) * dx) / l;
    }

    /** Index der Kante zwischen zwei benachbarten Ecken. */
    int kantenIndex(int a1, int b1, int a2, int b2) {
        if (b1 == b2) return (gitter.nx + 1) * gitter.ny + Math.min(a1, a2) + gitter.nx * b1;                // quer zu y
        return a1 + (gitter.nx + 1) * Math.min(b1, b2);                                                       // quer zu x
    }

    /** Höhe des Pixels unter einem Punkt: feinste Stufe, wo sie einen Wert hat, sonst das Gelände. */
    private float pixelHoehe(double x, double y, long[] letzter, float[][] t) {
        int n = pixel.kachelZellen;
        double c = (x - pixel.suedwestOst) / pixel.zelle, r = (pixel.suedwestNord + pixel.kachelnNord * pixel.kachel() - y) / pixel.zelle;
        float v = Float.NaN;
        if (c >= 0 && r >= 0 && c < pixel.spalten() && r < pixel.zeilen()) {
            int ci = (int) c, ri = (int) r, tx = ci / n, ty = ri / n;
            long key = ((long) tx << 32) | ty;
            if (key != letzter[0]) {
                t[0] = pixel.kachel(tx, ty);
                letzter[0] = key;
            }
            if (t[0].length > 0) v = t[0][(ri - ty * n) * n + ci - tx * n];
        }
        return Float.isNaN(v) ? gelaende.hoehe(x, y, pixel.zelle) : v;
    }

    /** Höchstes Pixel auf dem Querschnitt ±korridor durch (x, y) in Richtung (nx, ny), Schritt ein halbes Pixel. */
    private double querschnitt(double x, double y, double nx, double ny, long[] letzter, float[][] t) {
        double m = Double.NEGATIVE_INFINITY, h = pixel.zelle / 2;
        int k = (int) Math.round(korridor / h);
        for (int s = -k; s <= k; s++) {
            float v = pixelHoehe(x + s * h * nx, y + s * h * ny, letzter, t);
            if (!Float.isNaN(v) && v > m) m = v;
        }
        return m == Double.NEGATIVE_INFINITY ? Double.NaN : m;
    }

    /** Krone längs einer Linie, je Pixel ein Punkt: {x[], y[], Krone[], Weg[]}. */
    private double[][] laengs(Linie L) {
        List<double[]> p = new ArrayList<>();
        long[] letzter = {Long.MIN_VALUE};
        float[][] t = {null};
        double weg = 0;
        for (int s = 0; s + 1 < L.x.length; s++) {
            double dx = L.x[s + 1] - L.x[s], dy = L.y[s + 1] - L.y[s], l = Math.hypot(dx, dy);
            if (l == 0) continue;
            double ux = dx / l, uy = dy / l;
            double[] nv = L.normale(s);
            int n = (int) Math.ceil(l / pixel.zelle);
            for (int k = 0; k < n; k++) {
                double d = k * l / n, x = L.x[s] + ux * d, y = L.y[s] + uy * d;
                p.add(new double[] {x, y, querschnitt(x, y, nv[0], nv[1], letzter, t), weg + d});
            }
            weg += l;
        }
        int e = L.x.length - 1;
        if (e > 0) {
            double[] nv = L.normale(e - 1);
            p.add(new double[] {L.x[e], L.y[e], querschnitt(L.x[e], L.y[e], nv[0], nv[1], letzter, t), weg});
        }
        double[][] a = new double[4][p.size()];
        for (int i = 0; i < p.size(); i++) for (int k = 0; k < 4; k++) a[k][i] = p.get(i)[k];
        return a;
    }

    /** Nächster Punkt auf den Stücken [s0, s1] einer Linie: {x, y, Stück, t, Abstand}. */
    static double[] naechster(Linie L, double x, double y, int s0, int s1) {
        double[] best = null;
        for (int s = Math.max(0, s0); s <= Math.min(L.x.length - 2, s1); s++) {
            double ax = L.x[s], ay = L.y[s], dx = L.x[s + 1] - ax, dy = L.y[s + 1] - ay, ll = dx * dx + dy * dy;
            double t = ll == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / ll));
            double px = ax + t * dx, py = ay + t * dy, d = Math.hypot(x - px, y - py);
            if (best == null || d < best[4]) best = new double[] {px, py, s, t, d};
        }
        return best;
    }

    /** Profile der Kanten einer Linie. */
    private void profile(int l) {
        Linie L = linien.get(l);
        int[] pa = pfadA.get(l), pb = pfadB.get(l), ps = pfadStueck.get(l);
        long[] letzter = {Long.MIN_VALUE};
        float[][] t = {null};
        double h = gitter.dx / fein;
        for (int k = 0; k + 1 < pa.length; k++) {
            int a1 = pa[k], b1 = pb[k], a2 = pa[k + 1], b2 = pb[k + 1];
            boolean querX = a1 == a2;
            int idx = kantenIndex(a1, b1, a2, b2), i = querX ? a1 : Math.min(a1, a2), j = querX ? Math.min(b1, b2) : b1;
            Kante e = kanten.computeIfAbsent(idx, q -> new Kante(q, querX, i, j, fein));
            e.quellen.add(L.quelle());
            double xa = gitter.x0 + i * gitter.dx, ya = gitter.y0 + j * gitter.dy;
            for (int s = 0; s < fein; s++) {
                double x = querX ? xa : xa + (s + 0.5) * h, y = querX ? ya + (s + 0.5) * h : ya;
                double[] p = naechster(L, x, y, 0, L.x.length);
                double[] nv = L.normale((int) p[2]);
                double q = querschnitt(p[0], p[1], nv[0], nv[1], letzter, t);
                if (!Double.isNaN(q) && !(e.profil[s] >= q)) e.profil[s] = (float) q;
            }
        }
    }

    // ------------------------------------------------------------------ Siele

    private record Kandidat(int linie, double x, double y, double weg, String quelle, boolean rinne, double sohle) { }

    private void siele() {
        List<Kandidat> k = new ArrayList<>();
        // Lücken, die ein Durchlass nach OSM schließt
        for (Map.Entry<Integer, String> e : sielLuecken.entrySet()) {
            Linie L = linien.get(e.getKey());
            double x = 0.5 * (L.x[0] + L.x[L.x.length - 1]), y = 0.5 * (L.y[0] + L.y[L.y.length - 1]);
            k.add(new Kandidat(e.getKey(), x, y, 0.5 * L.laenge(), e.getValue(), false, Double.NaN));
        }
        // Brücken im Deich: darunter läuft Wasser
        for (int l = 0; l < linien.size(); l++) {
            Linie L = linien.get(l);
            Osm.Weg w = null;
            for (Osm.Weg q : quellWege) if (q.name().equals(L.quelle())) w = q;
            if (w == null || !bruecke.passt(w.tags())) continue;
            double[] m = zurueck(L, true, 0.5 * L.laenge());
            double[] q = naechster(L, m[0], m[1], 0, L.x.length);
            k.add(new Kandidat(l, q[0], q[1], weg(l, q), L.quelle() + " (Brücke)", false, Double.NaN));
        }
        // aus OSM: Punkte nah an einer Linie, Wege, die eine Linie kreuzen oder an ihr enden
        for (Osm.Punkt p : hinweisPunkte) {
            for (int l = 0; l < linien.size(); l++) {
                Linie L = linien.get(l);
                double[] q = naechster(L, p.ost(), p.nord(), 0, L.x.length);
                if (q != null && q[4] <= sielAbstand) k.add(new Kandidat(l, q[0], q[1], weg(l, q), "node/" + p.id() + name(p.tags()), false, Double.NaN));
            }
        }
        for (Osm.Weg w : hinweisWege) {
            for (int l = 0; l < linien.size(); l++) {
                Linie L = linien.get(l);
                for (int ende : new int[] {0, w.ost().length - 1}) {
                    double[] q = naechster(L, w.ost()[ende], w.nord()[ende], 0, L.x.length);
                    if (q != null && q[4] <= sielAbstand / 2) k.add(new Kandidat(l, q[0], q[1], weg(l, q), w.name() + name(w.tags()) + " (Ende)", false, Double.NaN));
                }
                for (int s = 0; s + 1 < L.x.length; s++)
                    for (int u = 0; u + 1 < w.ost().length; u++) {
                        double[] c = kreuzung(L.x[s], L.y[s], L.x[s + 1], L.y[s + 1], w.ost()[u], w.nord()[u], w.ost()[u + 1], w.nord()[u + 1]);
                        if (c != null) {
                            double[] q = naechster(L, c[0], c[1], s, s);
                            k.add(new Kandidat(l, c[0], c[1], weg(l, q), w.name() + name(w.tags()), false, Double.NaN));
                        }
                    }
            }
        }
        // aus dem Gelände: Rinnen in der Krone längs der Linie, die bis zum Fuß hinabreichen
        long[] lz = {Long.MIN_VALUE};
        float[][] tz = {null};
        for (int l = 0; l < linien.size(); l++) {
            Linie L = linien.get(l);
            double[][] a = laengs.get(l);
            int n = a[0].length;
            double[] ref = new double[n], tiefste = {Double.NaN, 0};
            String[] tiefsteText = {null};
            double[] tiefsteOrt = new double[2];
            for (int i = 0; i < n; i++) ref[i] = median(a, i, umgebung);
            for (int i = 0; i < n; ) {
                if (!(a[2][i] < ref[i] - rinne)) { i++; continue; }
                int e = i, tief = i;
                while (e < n && a[2][e] < ref[e] - rinne) {
                    if (a[2][e] < a[2][tief]) tief = e;
                    e++;
                }
                double[] q = naechster(L, a[0][tief], a[1][tief], 0, L.x.length), nv = L.normale((int) q[2]);
                double fl = fuss(a[0][tief], a[1][tief], nv[0], nv[1], lz, tz), fr = fuss(a[0][tief], a[1][tief], -nv[0], -nv[1], lz, tz);
                if (a[2][tief] < Math.min(fl, fr) + rinne)
                    k.add(new Kandidat(l, a[0][tief], a[1][tief], a[3][tief], f("Rinne %.2f m", a[2][tief]), true, a[2][tief]));
                else if (!(a[2][tief] - ref[tief] >= tiefste[0])) {
                    tiefste[0] = a[2][tief] - ref[tief];
                    tiefsteText[0] = f("%s: Krone %.2f m bei O %.0f N %.0f (Umgebung %.2f m, Fuß %.2f / %.2f m)", L.quelle(), a[2][tief], a[0][tief], a[1][tief], ref[tief], fl, fr);
                    tiefsteOrt[0] = a[0][tief];
                    tiefsteOrt[1] = a[1][tief];
                    tiefste[1]++;
                } else tiefste[1]++;
                i = e;
            }
            if (tiefsteText[0] != null) niedrigOrt.add(new Object[] {tiefsteText[0] + (tiefste[1] > 1 ? f(" und %.0f weitere Stellen", tiefste[1] - 1) : ""), tiefsteOrt[0], tiefsteOrt[1]});
        }
        // (je Linie die tiefste Stelle, die nicht bis zum Fuß reicht, kommt nach 'niedrig', wenn kein Siel nah ist)
        // zusammenfassen: Kandidaten im Abstand ≤ sielAbstand (auch über Linien hinweg) sind ein Siel
        int[] gruppe = new int[k.size()];
        for (int i = 0; i < k.size(); i++) gruppe[i] = i;
        for (int i = 0; i < k.size(); i++)
            for (int j = i + 1; j < k.size(); j++)
                if (Math.hypot(k.get(i).x - k.get(j).x, k.get(i).y - k.get(j).y) <= sielAbstand) {
                    int a = wurzel(gruppe, i), b = wurzel(gruppe, j);
                    if (a != b) gruppe[Math.max(a, b)] = Math.min(a, b);
                }
        Map<Integer, List<Kandidat>> gruppen = new TreeMap<>();
        for (int i = 0; i < k.size(); i++) gruppen.computeIfAbsent(wurzel(gruppe, i), x -> new ArrayList<>()).add(k.get(i));
        long[] letzter = {Long.MIN_VALUE};
        float[][] t = {null};
        int nr = 0;
        for (List<Kandidat> g : gruppen.values()) {
            Kandidat ort = g.get(0);
            double sohle = Double.NaN;
            TreeSet<String> q = new TreeSet<>();
            for (Kandidat c : g) {
                q.add(c.quelle);
                if (c.rinne && !(c.sohle >= sohle)) {
                    sohle = c.sohle;
                    ort = c;
                }
            }
            int l = ort.linie;
            if (Double.isNaN(sohle)) {                                                                      // Rohr: tiefstes Pixel im Umkreis
                double m = Double.POSITIVE_INFINITY, r = sielAbstand / 2;
                for (double dy = -r; dy <= r; dy += pixel.zelle)
                    for (double dx = -r; dx <= r; dx += pixel.zelle)
                        if (dx * dx + dy * dy <= r * r) {
                            float v = pixelHoehe(ort.x + dx, ort.y + dy, letzter, t);
                            if (v < m) m = v;
                        }
                sohle = m == Double.POSITIVE_INFINITY ? Double.NaN : m;
            }
            // Krone der Umgebung ohne die Rinne; Kanten der Rinne schließen
            double ref = kroneUmgebung(ort.x, ort.y);
            int beste = -1;
            double bd = Double.MAX_VALUE;
            for (Kante kk : kanten.values()) {
                double[] m = mitte(kk);
                double d = Math.hypot(m[0] - ort.x, m[1] - ort.y);
                if (d < bd) { bd = d; beste = kk.index; }
                if (d <= sielAbstand + gitter.dx / 2)
                    for (int s = 0; s < fein; s++)
                        if (kk.profil[s] < ref - rinne) {
                            kk.profil[s] = (float) ref;
                            kk.angehoben = true;
                        }
            }
            if (beste < 0) continue;
            Kante sk = kanten.get(beste);
            sk.art = "siel";
            String name = "Siel" + (++nr);
            for (Kandidat c : g)
                if (c.quelle.contains("„")) { name = c.quelle.substring(c.quelle.indexOf('„') + 1, c.quelle.indexOf('“')).replace(' ', '_'); break; }
            siele.add(new Siel(name, beste, ort.x, ort.y, sohle, ref, sielA, sielMu, String.join("+", q).replace(' ', '_')));
        }
        for (Object[] o : niedrigOrt) {
            boolean nah = false;
            for (Siel si : siele) if (Math.hypot(si.ost() - (double) o[1], si.nord() - (double) o[2]) <= 2 * sielAbstand) nah = true;
            if (!nah) niedrig.add((String) o[0]);
        }
    }

    private final List<Object[]> niedrigOrt = new ArrayList<>();

    /** Median der Kronen der Deichkanten im Umkreis 2 · umgebung: die Höhe des geschlossenen Tors. */
    private double kroneUmgebung(double x, double y) {
        List<Double> v = new ArrayList<>();
        for (Kante e : kanten.values()) {
            double[] m = mitte(e);
            double k = e.mittel();
            if (Math.hypot(m[0] - x, m[1] - y) <= 2 * umgebung && !Double.isNaN(k)) v.add(k);
        }
        if (v.isEmpty()) return Double.NaN;
        v.sort(Double::compare);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : 0.5 * (v.get(n / 2 - 1) + v.get(n / 2));
    }

    private static int wurzel(int[] g, int i) {
        while (g[i] != i) i = g[i] = g[g[i]];
        return i;
    }

    /** Median des Geländes 10–25 m [G] neben der Linie in Richtung (nx, ny): der Fuß des Deichs. */
    private double fuss(double x, double y, double nx, double ny, long[] letzter, float[][] t) {
        double[] v = new double[16];
        int m = 0;
        for (int d = 10; d <= 25; d++) {
            float z = pixelHoehe(x + d * nx, y + d * ny, letzter, t);
            if (!Float.isNaN(z)) v[m++] = z;
        }
        if (m == 0) return Double.NaN;
        Arrays.sort(v, 0, m);
        return m % 2 == 1 ? v[m / 2] : 0.5 * (v[m / 2 - 1] + v[m / 2]);
    }

    /** Niedrige Stellen der Krone, die nicht bis zum Fuß reichen (keine Siele, aber Stellen, an denen das Wasser zuerst überläuft). */
    public final List<String> niedrig = new ArrayList<>();

    private static String name(Map<String, String> tags) {
        String n = tags.get("name");
        return n == null ? "" : " „" + n + "“";
    }

    private double weg(int l, double[] q) {
        Linie L = linien.get(l);
        double w = 0;
        int s = (int) q[2];
        for (int i = 0; i < s; i++) w += Math.hypot(L.x[i + 1] - L.x[i], L.y[i + 1] - L.y[i]);
        return w + q[3] * Math.hypot(L.x[s + 1] - L.x[s], L.y[s + 1] - L.y[s]);
    }

    private static int nachIndex(double[][] a, double weg) {
        int i = Arrays.binarySearch(a[3], weg);
        return Math.max(0, Math.min(a[3].length - 1, i < 0 ? -i - 1 : i));
    }

    /** Median der Krone im Umkreis ±r (längs) um Punkt i. */
    private static double median(double[][] a, int i, double r) {
        int lo = i, hi = i, n = a[0].length;
        while (lo > 0 && a[3][i] - a[3][lo - 1] <= r) lo--;
        while (hi < n - 1 && a[3][hi + 1] - a[3][i] <= r) hi++;
        double[] v = new double[hi - lo + 1];
        int m = 0;
        for (int k = lo; k <= hi; k++) if (!Double.isNaN(a[2][k])) v[m++] = a[2][k];
        if (m == 0) return Double.NaN;
        Arrays.sort(v, 0, m);
        return m % 2 == 1 ? v[m / 2] : 0.5 * (v[m / 2 - 1] + v[m / 2]);
    }

    private static double[] kreuzung(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy) {
        double rx = bx - ax, ry = by - ay, sx = dx - cx, sy = dy - cy, d = rx * sy - ry * sx;
        if (d == 0) return null;
        double t = ((cx - ax) * sy - (cy - ay) * sx) / d, u = ((cx - ax) * ry - (cy - ay) * rx) / d;
        if (t < 0 || t > 1 || u < 0 || u > 1) return null;
        return new double[] {ax + t * rx, ay + t * ry};
    }

    private List<Kante> kantenDerLinie(int l) {
        int[] pa = pfadA.get(l), pb = pfadB.get(l);
        List<Kante> r = new ArrayList<>();
        for (int k = 0; k + 1 < pa.length; k++) r.add(kanten.get(kantenIndex(pa[k], pb[k], pa[k + 1], pb[k + 1])));
        return r;
    }

    /** Mitte einer Kante in UTM. */
    public double[] mitte(Kante e) {
        double x = gitter.x0 + e.i * gitter.dx, y = gitter.y0 + e.j * gitter.dy;
        return e.querX ? new double[] {x, y + gitter.dy / 2} : new double[] {x + gitter.dx / 2, y};
    }

    // ------------------------------------------------------------------ Ringe

    /** Zellen beiderseits einer Kante: {links bzw. unten, rechts bzw. oben} als Index, −1 außerhalb. */
    int[] zellen(Kante e) {
        int nx = gitter.nx, ny = gitter.ny;
        if (e.querX) return new int[] {e.i > 0 ? e.i - 1 + nx * e.j : -1, e.i < nx ? e.i + nx * e.j : -1};
        return new int[] {e.j > 0 ? e.i + nx * (e.j - 1) : -1, e.j < ny ? e.i + nx * e.j : -1};
    }

    private void ringe(Map<String, double[]> sonden) {
        int nx = gitter.nx, ny = gitter.ny, n = nx * ny;
        boolean[] zu = new boolean[(nx + 1) * ny + nx * (ny + 1)];
        for (Kante e : kanten.values()) zu[e.index] = true;
        ring = new int[n];
        Arrays.fill(ring, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < nx; i++) for (int j : new int[] {0, ny - 1}) if (ring[i + nx * j] < 0) { ring[i + nx * j] = 0; q.add(i + nx * j); }
        for (int j = 0; j < ny; j++) for (int i : new int[] {0, nx - 1}) if (ring[i + nx * j] < 0) { ring[i + nx * j] = 0; q.add(i + nx * j); }
        flut(q, zu, 0);
        int nummer = 0;
        for (int c = 0; c < n; c++) {
            if (ring[c] >= 0) continue;
            nummer++;
            ring[c] = nummer;
            q.add(c);
            flut(q, zu, nummer);
        }
        // Kennzahlen je Ring
        int[] zellen = new int[nummer + 1];
        for (int c = 0; c < n; c++) zellen[ring[c]]++;
        double[] tief = new double[nummer + 1], hoch = new double[nummer + 1];
        int[] tiefK = new int[nummer + 1], rand = new int[nummer + 1];
        Arrays.fill(tief, Double.POSITIVE_INFINITY);
        Arrays.fill(hoch, Double.NEGATIVE_INFINITY);
        // Nachbarn je Ring über Deichkanten; ein Ring mit nur einem Nachbarn (≠ außen) liegt in diesem (Warft im Ring)
        List<java.util.Set<Integer>> nachbarn = new ArrayList<>();
        for (int r = 0; r <= nummer; r++) nachbarn.add(new java.util.HashSet<>());
        for (Kante e : kanten.values()) {
            int[] z = zellen(e);
            int r1 = z[0] < 0 ? 0 : ring[z[0]], r2 = z[1] < 0 ? 0 : ring[z[1]];
            if (r1 != r2) { nachbarn.get(r1).add(r2); nachbarn.get(r2).add(r1); }
        }
        innen = new int[nummer + 1];
        for (int r = 1; r <= nummer; r++) if (nachbarn.get(r).size() == 1 && !nachbarn.get(r).contains(0)) innen[r] = nachbarn.get(r).iterator().next();
        for (Kante e : kanten.values()) {
            int[] z = zellen(e);
            int r1 = z[0] < 0 ? 0 : ring[z[0]], r2 = z[1] < 0 ? 0 : ring[z[1]];
            if (r1 == r2) continue;
            double k = e.krone();
            for (int r : new int[] {r1, r2}) {
                if (r == 0) continue;
                int anderer = r == r1 ? r2 : r1;
                if (innen[anderer] == r) continue;                                                       // Rand eines Rings, der in diesem liegt
                rand[r]++;
                if (k < tief[r]) { tief[r] = k; tiefK[r] = e.index; }
                if (k > hoch[r]) hoch[r] = k;
            }
        }
        Map<Integer, List<String>> namen = new HashMap<>();
        for (Map.Entry<String, double[]> s : sonden.entrySet()) {
            double[] p = s.getValue();
            if (!gitter.enthaelt(p[0], p[1])) continue;
            namen.computeIfAbsent(ring[gitter.index(gitter.i(p[0]), gitter.j(p[1]))], k -> new ArrayList<>()).add(s.getKey());
        }
        for (int r = 1; r <= nummer; r++)
            ringe.add(new Ring(r, zellen[r], zellen[r] * gitter.dx * gitter.dy, tief[r], tiefK[r], hoch[r], rand[r], 0, 0, namen.getOrDefault(r, List.of())));
        if (namen.containsKey(0)) bericht.append("Sonden außerhalb jedes Rings: ").append(namen.get(0)).append('\n');
    }

    private void flut(ArrayDeque<Integer> q, boolean[] zu, int nummer) {
        int nx = gitter.nx, ny = gitter.ny;
        while (!q.isEmpty()) {
            int c = q.poll(), i = c % nx, j = c / nx;
            if (i > 0 && !zu[i + (nx + 1) * j] && ring[c - 1] < 0) { ring[c - 1] = nummer; q.add(c - 1); }
            if (i < nx - 1 && !zu[i + 1 + (nx + 1) * j] && ring[c + 1] < 0) { ring[c + 1] = nummer; q.add(c + 1); }
            if (j > 0 && !zu[(nx + 1) * ny + i + nx * j] && ring[c - nx] < 0) { ring[c - nx] = nummer; q.add(c - nx); }
            if (j < ny - 1 && !zu[(nx + 1) * ny + i + nx * (j + 1)] && ring[c + nx] < 0) { ring[c + nx] = nummer; q.add(c + nx); }
        }
    }

    // ------------------------------------------------------------------ Geschnittene Zellen

    private void mindesthoehen() {
        int nx = gitter.nx;
        long[] wechsel = new long[ringe.size() + 1], hoch = new long[ringe.size() + 1];
        long[] letzter = {Long.MIN_VALUE};
        float[][] t = {null};
        for (int l = 0; l < linien.size(); l++) {
            Linie L = linien.get(l);
            int[] pa = pfadA.get(l), pb = pfadB.get(l);
            if (pa.length < 2) continue;
            // Seite der Zellen am Zug: +1 links, −1 rechts, 0 unklar
            Map<Integer, Integer> seite = new LinkedHashMap<>();
            java.util.Set<Integer> eigen = new java.util.HashSet<>();
            for (int k = 0; k + 1 < pa.length; k++) {
                int a1 = pa[k], b1 = pb[k], a2 = pa[k + 1], b2 = pb[k + 1];
                eigen.add(kantenIndex(a1, b1, a2, b2));
                int[] lr = linksRechts(a1, b1, a2, b2);
                setze(seite, lr[0], 1);
                setze(seite, lr[1], -1);
            }
            for (int schritt = 0; schritt < 2; schritt++) {                                                 // zwei Zellen weit über Kanten ohne diese Linie
                Map<Integer, Integer> neu = new LinkedHashMap<>(seite);
                for (Map.Entry<Integer, Integer> e : seite.entrySet()) {
                    int c = e.getKey(), i = c % nx, j = c / nx;
                    if (e.getValue() == 0) continue;
                    int[][] nb = {{i - 1, j, i, j, 1}, {i + 1, j, i + 1, j, 1}, {i, j - 1, i, j, 0}, {i, j + 1, i, j + 1, 0}};
                    for (int[] v : nb) {
                        if (v[0] < 0 || v[1] < 0 || v[0] >= nx || v[1] >= gitter.ny) continue;
                        int kidx = v[4] == 1 ? v[2] + (nx + 1) * v[3] : (nx + 1) * gitter.ny + v[2] + nx * v[3];
                        if (eigen.contains(kidx)) continue;
                        int d = v[0] + nx * v[1];
                        if (!seite.containsKey(d)) setze(neu, d, e.getValue());
                    }
                }
                seite = neu;
            }
            double[][] la = laengs.get(l);
            boolean offen = !L.geschlossen();
            double grenze = gitter.dx;
            for (Map.Entry<Integer, Integer> e : seite.entrySet()) {
                if (e.getValue() == 0) continue;
                int c = e.getKey(), ci = c % nx, cj = c / nx;
                double cx0 = gitter.x0 + ci * gitter.dx, cy0 = gitter.y0 + cj * gitter.dy;
                // Stücke der Linie in der Nähe der Zelle
                List<Integer> st = new ArrayList<>();
                for (int s = 0; s + 1 < L.x.length; s++) {
                    double mx = Math.min(L.x[s], L.x[s + 1]), Mx = Math.max(L.x[s], L.x[s + 1]), my = Math.min(L.y[s], L.y[s + 1]), My = Math.max(L.y[s], L.y[s + 1]);
                    if (Mx >= cx0 - grenze && mx <= cx0 + gitter.dx + grenze && My >= cy0 - grenze && my <= cy0 + gitter.dy + grenze) st.add(s);
                }
                if (st.isEmpty()) continue;
                for (int v = 0; v < fein; v++)
                    for (int u = 0; u < fein; u++) {
                        double x = cx0 + (u + 0.5) * pixel.zelle, y = cy0 + (v + 0.5) * pixel.zelle;
                        double[] best = null;
                        for (int s : st) {
                            double[] p = naechster(L, x, y, s, s);
                            if (best == null || p[4] < best[4]) best = p;
                        }
                        if (best == null || best[4] > grenze) continue;
                        int s = (int) best[2];
                        if (offen && (s == 0 && best[3] == 0 || s == L.x.length - 2 && best[3] == 1)) continue;   // jenseits der Enden
                        int sp = seiteDesPunkts(L, s, best[3], x, y);
                        if (sp == 0 || sp == e.getValue()) continue;
                        double krone = la[2][nachIndex(la, weg(l, best))];
                        if (Double.isNaN(krone)) continue;
                        setzeMindest(x, y, (float) krone);
                    }
            }
        }
        // Fläche je Ring: Pixel mit Mindesthöhe in seinen Zellen und den Nachbarzellen; gehoben, wo das Gelände tiefer liegt
        int n = pixel.kachelZellen;
        for (Map.Entry<Long, float[]> e : mindest.entrySet()) {
            int tx = (int) (e.getKey() >>> 32), ty = (int) (long) e.getKey();
            float[] k = e.getValue(), gz = pixel.kachel(tx, ty);
            for (int p = 0; p < k.length; p++) {
                if (Float.isNaN(k[p])) continue;
                double x = pixel.suedwestOst + (tx * n + p % n + 0.5) * pixel.zelle, y = pixel.suedwestNord + pixel.kachelnNord * pixel.kachel() - (ty * n + p / n + 0.5) * pixel.zelle;
                float z = gz.length > 0 && !Float.isNaN(gz[p]) ? gz[p] : gelaende.hoehe(x, y, pixel.zelle);
                seitenwechselPixel++;
                if (z < k[p]) gehobenPixel++;
                if (!gitter.enthaelt(x, y)) continue;
                int ci = gitter.i(x), cj = gitter.j(y);
                java.util.Set<Integer> rs = new java.util.HashSet<>();
                rs.add(ring[ci + nx * cj]);
                if (ci > 0) rs.add(ring[ci - 1 + nx * cj]);
                if (ci < nx - 1) rs.add(ring[ci + 1 + nx * cj]);
                if (cj > 0) rs.add(ring[ci + nx * (cj - 1)]);
                if (cj < gitter.ny - 1) rs.add(ring[ci + nx * (cj + 1)]);
                for (int r : rs) {
                    wechsel[r]++;
                    if (z < k[p]) hoch[r]++;
                }
            }
        }
        double a = pixel.zelle * pixel.zelle;
        for (int r = 0; r < ringe.size(); r++) {
            Ring g = ringe.get(r);
            ringe.set(r, new Ring(g.nummer(), g.zellen(), g.flaeche(), g.niedrigsteKrone(), g.niedrigsteKante(), g.hoechsteKrone(), g.randKanten(),
                    wechsel[r + 1] * a, hoch[r + 1] * a, g.namen()));
        }
    }

    private static void setze(Map<Integer, Integer> m, int c, int s) {
        if (c < 0) return;
        Integer alt = m.get(c);
        m.put(c, alt == null ? s : alt == s ? s : 0);
    }

    /** Zellen links und rechts eines Schritts von Ecke (a1, b1) nach (a2, b2). */
    int[] linksRechts(int a1, int b1, int a2, int b2) {
        int nx = gitter.nx, ny = gitter.ny;
        int[][] lr;
        if (b1 == b2) lr = a2 > a1 ? new int[][] {{a1, b1}, {a1, b1 - 1}} : new int[][] {{a2, b1 - 1}, {a2, b1}};
        else lr = b2 > b1 ? new int[][] {{a1 - 1, b1}, {a1, b1}} : new int[][] {{a1, b2}, {a1 - 1, b2}};
        int[] r = new int[2];
        for (int k = 0; k < 2; k++) r[k] = lr[k][0] < 0 || lr[k][1] < 0 || lr[k][0] >= nx || lr[k][1] >= ny ? -1 : lr[k][0] + nx * lr[k][1];
        return r;
    }

    /** Seite eines Punkts zur Linie (+1 links, −1 rechts) am nächsten Punkt (Stück s, Anteil t); an einer Ecke beide Stücke. */
    static int seiteDesPunkts(Linie L, int s, double t, double x, double y) {
        double k = kreuz(L, s, x, y);
        if (t == 0 && s > 0) k += kreuz(L, s - 1, x, y);
        else if (t == 1 && s + 2 < L.x.length) k += kreuz(L, s + 1, x, y);
        else if (t == 0 && s == 0 && L.geschlossen()) k += kreuz(L, L.x.length - 2, x, y);
        else if (t == 1 && s == L.x.length - 2 && L.geschlossen()) k += kreuz(L, 0, x, y);
        return k > 0 ? 1 : k < 0 ? -1 : 0;
    }

    private static double kreuz(Linie L, int s, double x, double y) {
        double dx = L.x[s + 1] - L.x[s], dy = L.y[s + 1] - L.y[s], l = Math.hypot(dx, dy);
        return l == 0 ? 0 : (dx * (y - L.y[s]) - dy * (x - L.x[s])) / l;
    }

    private void setzeMindest(double x, double y, float v) {
        int n = pixel.kachelZellen;
        int c = (int) Math.floor((x - pixel.suedwestOst) / pixel.zelle), r = (int) Math.floor((pixel.suedwestNord + pixel.kachelnNord * pixel.kachel() - y) / pixel.zelle);
        if (c < 0 || r < 0 || c >= pixel.spalten() || r >= pixel.zeilen()) return;
        int tx = c / n, ty = r / n;
        float[] k = mindest.computeIfAbsent(((long) tx << 32) | ty, q -> {
            float[] a = new float[n * n];
            Arrays.fill(a, Float.NaN);
            return a;
        });
        int p = (r - ty * n) * n + c - tx * n;
        if (!(k[p] >= v)) k[p] = v;
    }

    // ------------------------------------------------------------------ Ablage

    /** Schreibt {@code bauwerke.txt} und die Stufe der Mindesthöhen nach {@code ordner}. */
    public void schreibe(File ordner, String kopf) throws IOException {
        Files.createDirectories(ordner.toPath());
        File mo = new File(ordner, "mindesthoehe");
        Stapel.leere(mo);
        Gelaende.Stufe m = Gelaende.Stufe.anlegen(mo, pixel.zelle, pixel.kachelZellen, pixel.suedwestOst, pixel.suedwestNord, pixel.kachelnOst, pixel.kachelnNord,
                "Mindesthöhe für das Backen von " + gitter.name + ": Pixel zwischen Deichlinie und Kantenzug tragen die Krone (Werkbuch HalligHooge Teil A, 10.4, Schritt 5)");
        for (Map.Entry<Long, float[]> e : mindest.entrySet()) m.schreibe((int) (e.getKey() >>> 32), (int) (long) e.getKey(), e.getValue());
        StringBuilder b = new StringBuilder();
        b.append("# Bauwerke von ").append(gitter.name).append(" (Werkbuch HalligHooge Teil A, 10.4, Schritt 5)\n");
        for (String z : kopf.split("\n")) b.append("# ").append(z).append('\n');
        b.append("#\n# gitter: West Süd Zelle nx ny; fein: Stellen je Kante\n");
        b.append(f("gitter %s %.3f %.3f %.3f %d %d\nfein %d\n", gitter.name, gitter.x0, gitter.y0, gitter.dx, gitter.nx, gitter.ny, fein));
        b.append("#\n# ring: Nummer, Zellen, Fläche (m²), niedrigste und höchste Krone am Rand (m NHN), Kante der niedrigsten, Randkanten, ")
                .append("Fläche mit Seitenwechsel im Ring und seinen Nachbarzellen und davon gehoben (m²), Ring, in dem er liegt (0 keiner), Namen\n")
                .append("# Rand: Deichkanten zwischen dem Ring und außen oder einem Ring, der nicht in ihm liegt\n");
        for (Ring r : ringe)
            b.append(f("ring %d %d %.0f %.3f %.3f %d %d %.0f %.0f %d %s\n", r.nummer(), r.zellen(), r.flaeche(), endlich(r.niedrigsteKrone()), endlich(r.hoechsteKrone()), r.niedrigsteKante(),
                    r.randKanten(), r.seitenwechsel(), r.gehoben(), innen[r.nummer()], r.namen().isEmpty() ? "-" : String.join(",", r.namen())));
        b.append("#\n# siel: Name, Kante, Ost, Nord, Sohle, Krone (geschlossen), A (m²) [G], μ [G], Quellen\n");
        for (Siel s : siele)
            b.append(f("siel %s %d %.1f %.1f %.3f %.3f %.2f %.2f %s\n", s.name(), s.kante(), s.ost(), s.nord(), s.sohle(), s.krone(), s.a(), s.mu(), s.quellen()));
        b.append("#\n# kante: Index (wie Subgrid.kanteX/kanteY), quer zu x oder y, i, j, Art, Krone (niedrigste Stelle), Mittel, angehoben (1: Tor eines Siels), ")
                .append("Quellen, Profil je Stelle (m NHN)\n");
        for (Kante e : kanten.values()) {
            b.append(f("kante %d %s %d %d %s %.3f %.3f %d %s", e.index, e.querX ? "x" : "y", e.i, e.j, e.art, e.krone(), e.mittel(), e.angehoben ? 1 : 0, String.join(",", e.quellen)));
            for (float v : e.profil) b.append(' ').append(Float.isNaN(v) ? "nan" : f("%.3f", v));
            b.append('\n');
        }
        Files.writeString(new File(ordner, "bauwerke.txt").toPath(), b.toString(), StandardCharsets.UTF_8);
    }

    private static double endlich(double v) { return Double.isInfinite(v) ? Double.NaN : v; }

    /** Was beim Bau auffiel (Sonden außerhalb jedes Rings). */
    public String bericht() { return bericht.toString(); }

    /** Kronen längs der Linie l: {x[], y[], Krone[], Weg[]} (für Prüfung und Bild). */
    public double[][] laengs(int l) { return laengs.get(l); }

    /** Ecken des Kantenzugs der Linie l: {a[], b[]}. */
    public int[][] pfad(int l) { return new int[][] {pfadA.get(l), pfadB.get(l)}; }

    static String f(String format, Object... w) { return String.format(Locale.ROOT, format, w); }

    /** Liest die Kanten einer {@code bauwerke.txt}: Index → {Krone, Art (0 Deich, 1 Siel)} und das Profil. */
    public static Map<Integer, float[]> lies(Path datei, Map<Integer, String[]> zeilen) throws IOException {
        Map<Integer, float[]> m = new TreeMap<>();
        for (String z : Files.readAllLines(datei, StandardCharsets.UTF_8)) {
            if (!z.startsWith("kante ")) continue;
            String[] t = z.split(" ");
            int fein = t.length - 10;
            float[] p = new float[fein];
            for (int s = 0; s < fein; s++) p[s] = t[10 + s].equals("nan") ? Float.NaN : Float.parseFloat(t[10 + s]);
            int idx = Integer.parseInt(t[1]);
            m.put(idx, p);
            if (zeilen != null) zeilen.put(idx, t);
        }
        return m;
    }
}
