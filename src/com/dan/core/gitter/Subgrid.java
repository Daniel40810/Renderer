package com.dan.core.gitter;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Subgrid-Tabellen (Werkbuch HalligHooge Teil A, 5.4; Casulli 2009, Volp u. a. 2013, Sehili u. a. 2014): Jede Zelle
 * des Rechengitters trägt aus dem feinen Gelände ihre Speicherkurve V(η) und ihre benetzte Fläche A(η), jede Kante
 * ihren Leitwert K(η). Volumen und benetzte Fläche sind damit die des feinen Geländes, und Priele, die schmaler als
 * eine Zelle sind, führen Wasser. Katalogfeld {@code ground.hyps} (m³, m², m³/s).
 * <p>
 * <b>Zelle.</b> Das feine Gelände hat je Pixel eine Höhe z. Dann ist V(η) = Σ a · max(0, η − z) stückweise linear
 * mit einem Knick an jeder Pixelhöhe. Die Tabelle hält {@link #stufen} Stützstellen (12 [G]) und ist dazwischen
 * linear; an den Stützstellen ist sie genau. Unter der tiefsten Stufe ist die Zelle trocken, über der höchsten ganz
 * nass: V wächst dort mit der vollen Fläche, also genau. Hat die Zelle höchstens so viele verschiedene Höhen wie
 * Stufen, ist die Tabelle überall genau.
 * <p>
 * Die Stützstellen werden auf zwei Arten gewählt, und es gilt die Wahl mit dem kleineren größten Fehler:
 * (1) größter Fehler zuerst – beginnend mit der tiefsten und der höchsten Pixelhöhe kommt immer die Pixelhöhe dazu, an
 * der die Sehne am weitesten von der Kurve liegt; (2) gleiche Schritte im Mittel aus Höhe und benetzter Fläche
 * (beide auf 0..1 gebracht). Für (2) gilt eine Schranke: Der Fehler zwischen zwei Stufen ist höchstens
 * Zellfläche · Spanne / (4 · (Stufen − 1)²), bei 12 Stufen 0,21 % – und damit auch für die gewählte Tabelle.
 * <p>
 * <b>Kante.</b> Längs der Kante liegen {@link #fein} Pixelpaare; die Schwelle eines Paars ist die höhere der beiden
 * Höhen. Der Leitwert nach Manning ist K(η) = Σ Δs/n · max(0, η − Schwelle)^(5/3) in m³/s (Durchfluss je Wurzel des
 * Gefälles). Abgelegt wird die <i>wirksame Tiefe</i> h_K = (K / Σ Δs/n)^(3/5): Sie ist bei ebener Kante gleich der
 * Wassertiefe, immer konvex, und wächst weit über der Kante mit der Steigung 1 – darum trägt die lineare Tabelle
 * besser als eine für K selbst. Die höchste Stufe liegt um die Spanne der Schwellen über der höchsten Schwelle;
 * darüber gilt h_K = h_K(oben) + (η − oben). Die Stützstellen werden nach dem größten relativen Fehler gewählt
 * (unter 1 cm wirksamer Tiefe gegen 1 cm, {@link #FILM}): In einem Film von Millimetern ist der Leitwert winzig und
 * sein relativer Fehler ohne Belang.
 * <p>
 * <b>Querschnitt</b> (seit P4, F25): An denselben Stufen trägt die Kante die mittlere benetzte Tiefe
 * h_A(η) = Σ max(0, η − Schwelle) / {@link #fein}; der benetzte Querschnitt ist A = Kantenlänge · h_A. Der Impuls braucht A
 * für den Durchfluss und das Gefälle, K für die Reibung. An den Stufen ist h_A genau, darüber wächst es mit der Steigung 1.
 * <p>
 * <b>Grenzen:</b> Ein Damm im Inneren einer Zelle wird nicht gesehen (die Zelle ist ein Becken). Die Rauheit ist je
 * Pixel fest; ändert sie sich, wird neu gebacken ({@link #neu}). Die Tabellen in double brauchen 688 Byte je Zelle;
 * große Gitter werden streifenweise gebacken und gepackt ({@link #packen}).
 */
public final class Subgrid {
    /** Das feine Gelände (oder die Rauheit) unter dem Rechengitter: Pixel von Südwest, i nach Osten, j nach Norden. */
    public interface Feingelaende {
        /**
         * Schreibt die Werte des Fensters [i0, i0 + breite) × [j0, j0 + hoehe) zeilenweise von Süd nach Nord nach
         * {@code ziel}. Das Fenster darf über das Gelände hinausreichen; dort steht NaN.
         */
        void lies(int i0, int j0, int breite, int hoehe, double[] ziel);
    }

    /** Stufen je Tabelle [G] und Rauheit nach Manning (s/m^(1/3)), wenn keine gegeben ist [G]. */
    public static final int STUFEN = 12;
    public static final double MANNING = 0.025;
    /**
     * Wirksame Tiefe (m), ab der die Tabelle der Kante auf relative Genauigkeit gewählt wird [G]; darunter (dünner
     * Film) zählt die Abweichung gegen diese Tiefe.
     */
    public static final double FILM = 0.01;

    public final Gitter gitter;
    /** Feine Pixel je Zellseite und Stufen je Tabelle. */
    public final int fein, stufen;
    /** Fläche eines feinen Pixels (m²) und einer Zelle. */
    /**
     * Fläche eines Pixels und einer Zelle (m²). Auf einem geographischen Gitter (seit P5, G0) wechseln beide mit der
     * Zeile ({@link #zellflaeche(int)}); die Felder sind dann NaN, und die Packung ist relativ zur Fläche der Zeile.
     */
    public final double pixel, zellflaeche;
    private final double[] zeilenFlaeche;
    /** Je Zelle und Stufe: Höhe der Stufe (m NHN), Volumen (m³), benetzte Fläche (m²). */
    public final double[] stufe, volumen, flaeche;
    /** Zahl der Kanten (wie {@code feld.Ort.KANTE}: erst quer zu x, dann quer zu y). */
    public final int kanten;
    /** Je Kante und Stufe: Höhe der Stufe (m NHN), wirksame Tiefe h_K (m) und mittlere benetzte Tiefe h_A (m). */
    public final double[] kStufe, kTiefe, kQuer;
    /** Je Kante: Σ Δs/n (m^(4/3)/s); K = kWeite · h_K^(5/3). */
    public final double[] kWeite;
    private final Feingelaende hoehen, rauheit;

    /** Backt die Tabellen für das ganze Gitter mit 12 Stufen und einheitlicher Rauheit. */
    public static Subgrid bauen(Gitter gitter, int fein, Feingelaende hoehen) { return new Subgrid(gitter, fein, hoehen, null, STUFEN); }

    /** Backt die Tabellen; {@code rauheit} gibt Manning-n je Pixel (null: {@link #MANNING}). */
    public static Subgrid bauen(Gitter gitter, int fein, Feingelaende hoehen, Feingelaende rauheit, int stufen) {
        return new Subgrid(gitter, fein, hoehen, rauheit, stufen);
    }

    private Subgrid(Gitter gitter, int fein, Feingelaende hoehen, Feingelaende rauheit, int stufen) {
        if (fein < 1 || stufen < 2) throw new IllegalArgumentException("Subgrid: fein " + fein + ", Stufen " + stufen);
        this.gitter = gitter;
        this.fein = fein;
        this.stufen = stufen;
        this.hoehen = hoehen;
        this.rauheit = rauheit;
        boolean kugel = gitter.lage == Gitter.Lage.GEOGRAFISCH;
        pixel = kugel ? Double.NaN : gitter.dx * gitter.dy / ((double) fein * fein);
        zellflaeche = kugel ? Double.NaN : gitter.dx * gitter.dy;
        zeilenFlaeche = new double[gitter.ny];
        for (int j = 0; j < gitter.ny; j++) zeilenFlaeche[j] = kugel ? gitter.flaeche(j) : zellflaeche;
        int n = gitter.zellen();
        kanten = (gitter.nx + 1) * gitter.ny + gitter.nx * (gitter.ny + 1);
        if ((long) kanten * stufen > Integer.MAX_VALUE - 8) throw new IllegalArgumentException("Subgrid: Gitter zu groß für ein Stück; streifenweise backen");
        stufe = new double[n * stufen];
        volumen = new double[n * stufen];
        flaeche = new double[n * stufen];
        kStufe = new double[kanten * stufen];
        kTiefe = new double[kanten * stufen];
        kQuer = new double[kanten * stufen];
        kWeite = new double[kanten];
        neu(0, 0, gitter.nx, gitter.ny);
    }

    /**
     * Backt die Zellen [i0, i1) × [j0, j1) und ihre Kanten neu aus dem feinen Gelände – dort, wo sich der Boden
     * geändert hat (Werkbuch 7.4).
     */
    public void neu(int i0, int j0, int i1, int j1) {
        i0 = Math.max(0, i0);
        j0 = Math.max(0, j0);
        i1 = Math.min(gitter.nx, i1);
        j1 = Math.min(gitter.ny, j1);
        if (i0 >= i1 || j0 >= j1) return;
        long breite = (long) (i1 - i0) * fein + 2;
        int band = (int) Math.max(1, Math.min(j1 - j0, (8L << 20) / (breite * fein)));       // rund 64 MB je Streifen
        for (int j = j0; j < j1; j += band) backe(i0, j, i1, Math.min(j1, j + band));
    }

    private void backe(int i0, int j0, int i1, int j1) {
        int fi0 = i0 * fein - 1, fj0 = j0 * fein - 1, fw = (i1 - i0) * fein + 2, fh = (j1 - j0) * fein + 2;
        double[] z = new double[fw * fh], r = rauheit == null ? null : new double[fw * fh];
        hoehen.lies(fi0, fj0, fw, fh, z);
        if (r != null) rauheit.lies(fi0, fj0, fw, fh, r);
        IntStream.range(j0, j1 + 1).parallel().forEach(j -> {
            Arbeit a = new Arbeit(fein, stufen);
            int oy = (j - j0) * fein + 1;                                                  // erste Pixelzeile der Zellzeile j im Fenster
            for (int i = i0; i <= i1; i++) {
                int ox = (i - i0) * fein + 1;
                if (i < i1 && j < j1) zelle(i + gitter.nx * j, z, fw, ox, oy, a);
                if (j < j1) kante(i + (gitter.nx + 1) * j, z, r, ox - 1 + fw * oy, 1, fw, gitter.hoehe(j) / fein, a);           // quer zu x
                if (i < i1) kante((gitter.nx + 1) * gitter.ny + i + gitter.nx * j, z, r, ox + fw * (oy - 1), fw, 1, kantenLaengeY(j) / fein, a);   // quer zu y
            }
        });
    }

    /** Fläche einer Zelle der Zeile j (m²). */
    public double zellflaeche(int j) { return zeilenFlaeche[j]; }

    /** Länge einer Kante quer zu y auf der Kantenzeile j (0..ny), in m: auf der Kugel auf ihrer Breite. */
    private double kantenLaengeY(int j) {
        return gitter.lage == Gitter.Lage.UTM ? gitter.dx : gitter.dx * com.dan.core.raum.Erde.meterJeGradLaenge(gitter.y0 + j * gitter.dy);
    }

    /** Arbeitsfelder eines Fadens. */
    private static final class Arbeit {
        final double[] p, hz, hv, w, kz, kh, ka;
        final int[] hc, lev;
        final double[] sz, sv, sa;
        Arbeit(int fein, int stufen) {
            sz = new double[stufen];
            sv = new double[stufen];
            sa = new double[stufen];
            p = new double[fein * fein];
            hz = new double[fein * fein];
            hv = new double[fein * fein];
            hc = new int[fein * fein];
            w = new double[fein];
            kz = new double[10 * fein + 8];
            kh = new double[10 * fein + 8];
            ka = new double[10 * fein + 8];
            lev = new int[stufen];
        }
    }

    private void zelle(int c, double[] z, int fw, int ox, int oy, Arbeit a) {
        double pixel = zeilenFlaeche[c / gitter.nx] / ((double) fein * fein);
        double[] p = a.p;
        int n = fein * fein;
        for (int v = 0; v < fein; v++) System.arraycopy(z, ox + fw * (oy + v), p, v * fein, fein);
        for (int k = 0; k < n; k++) if (Double.isNaN(p[k])) throw new IllegalStateException("Subgrid: Lücke im feinen Gelände in Zelle " + c);
        Arrays.sort(p, 0, n);
        // verschiedene Höhen mit Volumen und Zahl der Pixel bis dorthin; nur positive Glieder, also ohne Auslöschung
        int m = 0;
        double vol = 0;
        for (int k = 0; k < n; ) {
            int e = k;
            while (e < n && p[e] == p[k]) e++;
            if (m > 0) vol += a.hc[m - 1] * pixel * (p[k] - a.hz[m - 1]);
            a.hz[m] = p[k];
            a.hv[m] = vol;
            a.hc[m] = e;
            m++;
            k = e;
        }
        int nl = stuetzen(a.hz, a.hv, null, m, a.lev, 0);
        for (int l = 0; l < stufen; l++) {
            int q = a.lev[Math.min(l, nl - 1)], o = c * stufen + l;
            stufe[o] = a.hz[q];
            volumen[o] = a.hv[q];
            flaeche[o] = a.hc[q] * pixel;
        }
        if (m <= stufen) return;                                                           // jede Pixelhöhe ist Stufe: genau
        // zweite Wahl: gleiche Schritte im Mittel aus Höhe und benetzter Fläche; die bessere gilt
        double unten = a.hz[0], spanne = a.hz[m - 1] - unten;
        for (int l = 0, q = 0; l < stufen; l++) {
            double ziel = l / (double) (stufen - 1), eta;
            while (true) {
                if (0.5 * (a.hz[q] - unten) / spanne + 0.5 * a.hc[q] / n >= ziel || q == m - 1) { eta = a.hz[q]; break; }
                if (0.5 * (a.hz[q + 1] - unten) / spanne + 0.5 * a.hc[q] / n >= ziel) { eta = Math.min(a.hz[q + 1], unten + spanne * (2 * ziel - a.hc[q] / (double) n)); break; }
                q++;
            }
            a.sz[l] = eta;
            a.sv[l] = a.hv[q] + a.hc[q] * pixel * (eta - a.hz[q]);
            a.sa[l] = a.hc[q] * pixel;
        }
        a.sz[stufen - 1] = a.hz[m - 1];
        a.sv[stufen - 1] = a.hv[m - 1];
        a.sa[stufen - 1] = a.hc[m - 1] * pixel;
        if (fehler(a.sz, 0, a.sv, 0, a.hz, a.hv, m) < fehler(stufe, c * stufen, volumen, c * stufen, a.hz, a.hv, m)) {
            System.arraycopy(a.sz, 0, stufe, c * stufen, stufen);
            System.arraycopy(a.sv, 0, volumen, c * stufen, stufen);
            System.arraycopy(a.sa, 0, flaeche, c * stufen, stufen);
        }
    }

    /** Größter Abstand zwischen der Tabelle (Stufen ab os, Werte ab ow) und der Kurve an ihren m Knicken. */
    private double fehler(double[] s, int os, double[] w, int ow, double[] x, double[] y, int m) {
        double f = 0;
        for (int q = 0, l = 0; q < m; q++) {
            while (l < stufen - 2 && s[os + l + 1] <= x[q]) l++;
            double d = s[os + l + 1] - s[os + l];
            f = Math.max(f, Math.abs(d > 0 ? w[ow + l] + (w[ow + l + 1] - w[ow + l]) * (x[q] - s[os + l]) / d - y[q] : w[ow + l] - y[q]));
        }
        return f;
    }

    /**
     * Wählt bis zu {@link #stufen} Stützstellen unter m Kandidaten (x aufsteigend, y konvex): erst die Enden, dann
     * immer die Stelle mit dem größten Abstand zwischen Sehne und Kurve. Mit {@code boden} > 0 zählt der Abstand
     * relativ zum Wert der Kurve (unter {@code boden} relativ zu diesem). Mit einer zweiten Kurve {@code y2} zählt je
     * Stelle der größere der beiden Abstände (Kanten: Leitwert und Querschnitt, P4). Gibt die Zahl zurück, lev ist
     * aufsteigend.
     */
    private int stuetzen(double[] x, double[] y, double[] y2, int m, int[] lev, double boden) {
        lev[0] = 0;
        if (m == 1) return 1;
        lev[1] = m - 1;
        int nl = 2;
        while (nl < stufen) {
            double best = 0;
            int wo = -1;
            for (int s = 0; s + 1 < nl; s++) {
                int u = lev[s], o = lev[s + 1];
                double steig = (y[o] - y[u]) / (x[o] - x[u]), steig2 = y2 == null ? 0 : (y2[o] - y2[u]) / (x[o] - x[u]);
                for (int q = u + 1; q < o; q++) {
                    double e = Math.abs(y[u] + steig * (x[q] - x[u]) - y[q]);
                    if (boden > 0) e /= Math.max(y[q], boden);
                    if (y2 != null) {
                        double e2 = Math.abs(y2[u] + steig2 * (x[q] - x[u]) - y2[q]);
                        if (boden > 0) e2 /= Math.max(y2[q], boden);
                        e = Math.max(e, e2);
                    }
                    if (e > best) { best = e; wo = q; }
                }
            }
            if (wo < 0) break;
            int s = nl;
            while (lev[s - 1] > wo) { lev[s] = lev[s - 1]; s--; }
            lev[s] = wo;
            nl++;
        }
        return nl;
    }

    /**
     * Eine Kante: {@code fein} Pixelpaare ab {@code start} im Fenster, Schritt längs der Kante {@code laengs}, Schritt
     * zum Nachbarn über die Kante {@code quer}.
     */
    private void kante(int e, double[] z, double[] r, int start, int quer, int laengs, double ds, Arbeit a) {
        double[] w = a.w, p = a.p, kz = a.kz, kh = a.kh, ka = a.ka;
        double weite = 0;
        for (int k = 0; k < fein; k++) {
            int u = start + k * laengs, o = u + quer;
            double zu = z[u], zo = z[o];
            if (Double.isNaN(zu) && Double.isNaN(zo)) throw new IllegalStateException("Subgrid: Lücke im feinen Gelände an Kante " + e);
            p[k] = Double.isNaN(zu) ? zo : Double.isNaN(zo) ? zu : Math.max(zu, zo);
            double n = r == null ? MANNING : Double.isNaN(r[u]) ? r[o] : Double.isNaN(r[o]) ? r[u] : 0.5 * (r[u] + r[o]);
            w[k] = ds / n;
            weite += w[k];
        }
        kWeite[e] = weite;
        // Kandidaten: jede verschiedene Schwelle; darüber bis zur nächsten Schwelle (zuletzt bis zur Spanne über der
        // höchsten) eine Leiter von 2,5 mm, 1 cm, 4 cm … 2,56 m und ein Achtel und die Hälfte des Abstands. Dicht über
        // einer Schwelle krümmt sich die Kurve am stärksten. Je Schwelle höchstens 9 Stellen.
        double[] s = a.hz;
        System.arraycopy(p, 0, s, 0, fein);
        Arrays.sort(s, 0, fein);
        double unten = s[0], oben = s[fein - 1], spanne = oben - unten;
        int m = 0;
        for (int k = 0; k < fein && spanne > 0; k++) {
            if (k > 0 && s[k] == s[k - 1]) continue;
            double naechste = oben + spanne;
            for (int v = fein - 1; v > k; v--) if (s[v] > s[k]) naechste = s[v];
            int ab = m;
            kz[m++] = s[k];
            double d = 0.0025;
            for (int v = 0; v < 6 && s[k] + d < naechste; v++, d *= 4) kz[m++] = s[k] + d;                // höchstens bis 2,56 m
            kz[m++] = s[k] + 0.125 * (naechste - s[k]);
            kz[m++] = s[k] + 0.5 * (naechste - s[k]);
            Arrays.sort(kz, ab, m);
            int zu = ab + 1;                                                            // doppelte und fast gleiche Stellen fallen weg
            for (int q = ab + 1; q < m; q++) if (kz[q] - kz[zu - 1] > 1e-6 && naechste - kz[q] > 1e-6) kz[zu++] = kz[q];
            m = zu;
        }
        kz[m++] = spanne > 0 ? oben + spanne : unten;
        for (int q = 0; q < m; q++) {
            double sum = 0, benetzt = 0;
            for (int k = 0; k < fein; k++) {
                double h = kz[q] - p[k];
                if (h > 0) {
                    sum += w[k] * h * Math.cbrt(h * h);
                    benetzt += h;
                }
            }
            kh[q] = Math.pow(sum / weite, 0.6);
            ka[q] = benetzt / fein;
        }
        int nl = stuetzen(kz, kh, ka, m, a.lev, FILM);
        for (int l = 0; l < stufen; l++) {
            int q = a.lev[Math.min(l, nl - 1)], o = e * stufen + l;
            kStufe[o] = kz[q];
            kTiefe[o] = kh[q];
            kQuer[o] = ka[q];
        }
    }

    // ------------------------------------------------------------------ Abfragen

    /** Tiefste Pixelhöhe der Zelle (m NHN): darunter ist sie trocken. */
    public double grund(int zelle) { return stufe[zelle * stufen]; }

    /** Höchste Pixelhöhe der Zelle (m NHN): darüber ist sie ganz nass. */
    public double hoechste(int zelle) { return stufe[zelle * stufen + stufen - 1]; }

    /** Volumen (m³) in der Zelle beim Wasserstand η. */
    public double volumen(int zelle, double eta) {
        int o = zelle * stufen, l = intervall(stufe, o, eta);
        if (l < 0) return 0;
        if (l == stufen - 1) return volumen[o + l] + zeilenFlaeche[zelle / gitter.nx] * (eta - stufe[o + l]);
        return volumen[o + l] + (volumen[o + l + 1] - volumen[o + l]) * (eta - stufe[o + l]) / (stufe[o + l + 1] - stufe[o + l]);
    }

    /** Benetzte Fläche (m²) der Zelle beim Wasserstand η. */
    public double flaeche(int zelle, double eta) {
        int o = zelle * stufen, l = intervall(stufe, o, eta);
        if (l < 0) return 0;
        if (l == stufen - 1) return zeilenFlaeche[zelle / gitter.nx];
        return flaeche[o + l] + (flaeche[o + l + 1] - flaeche[o + l]) * (eta - stufe[o + l]) / (stufe[o + l + 1] - stufe[o + l]);
    }

    /** Wasserstand η (m NHN) zum Volumen V (m³): die Umkehrung von {@link #volumen}; trocken gibt den Grund. */
    public double eta(int zelle, double v) {
        int o = zelle * stufen;
        if (!(v > 0)) return stufe[o];
        int l = stufen - 1;
        while (l > 0 && volumen[o + l] > v) l--;
        if (l == stufen - 1) return stufe[o + l] + (v - volumen[o + l]) / zeilenFlaeche[zelle / gitter.nx];
        return stufe[o + l] + (v - volumen[o + l]) / (volumen[o + l + 1] - volumen[o + l]) * (stufe[o + l + 1] - stufe[o + l]);
    }

    /** Wirksame Tiefe h_K (m) der Kante beim Wasserstand η. */
    public double wirksameTiefe(int kante, double eta) {
        int o = kante * stufen, l = intervall(kStufe, o, eta);
        if (l < 0) return 0;
        if (l == stufen - 1) return kTiefe[o + l] + (eta - kStufe[o + l]);
        return kTiefe[o + l] + (kTiefe[o + l + 1] - kTiefe[o + l]) * (eta - kStufe[o + l]) / (kStufe[o + l + 1] - kStufe[o + l]);
    }

    /** Mittlere benetzte Tiefe h_A (m) der Kante beim Wasserstand η; der Querschnitt ist Kantenlänge · h_A. */
    public double querschnitt(int kante, double eta) {
        int o = kante * stufen, l = intervall(kStufe, o, eta);
        if (l < 0) return 0;
        if (l == stufen - 1) return kQuer[o + l] + (eta - kStufe[o + l]);
        return kQuer[o + l] + (kQuer[o + l + 1] - kQuer[o + l]) * (eta - kStufe[o + l]) / (kStufe[o + l + 1] - kStufe[o + l]);
    }

    /** Leitwert K (m³/s) der Kante beim Wasserstand η: Durchfluss = K · √Gefälle. */
    public double leitwert(int kante, double eta) {
        double h = wirksameTiefe(kante, eta);
        return kWeite[kante] * h * Math.cbrt(h * h);
    }

    /** Kante quer zu x zwischen den Zellen (i − 1, j) und (i, j). */
    public int kanteX(int i, int j) { return i + (gitter.nx + 1) * j; }

    /** Kante quer zu y zwischen den Zellen (i, j − 1) und (i, j). */
    public int kanteY(int i, int j) { return (gitter.nx + 1) * gitter.ny + i + gitter.nx * j; }

    /** Letzte Stufe l mit Höhe ≤ η (bei gleichen Höhen die letzte), −1 unter der tiefsten. */
    private int intervall(double[] t, int o, double eta) {
        if (!(eta > t[o])) return -1;
        int l = stufen - 1;
        while (t[o + l] > eta) l--;
        return l;
    }

    // ------------------------------------------------------------------ Packung für die Karte

    /**
     * Die Tabellen als float16 für die Karte (Werkbuch 5.4): je Zelle und Kante eine Basis in float32, die Stufen in
     * float16 relativ dazu.
     *
     * @param basis   je Zelle die tiefste Höhe (m NHN); dahinter je Kante die tiefste Schwelle und Σ Δs/n
     * @param tabelle je Zelle 3 · Stufen Werte (Höhe über der Basis in m, mittlere Tiefe V/Zellfläche in m, benetzter
     *                Anteil 0..1); dahinter je Kante {@code kantenWerte} · Stufen Werte (Höhe über der Basis, wirksame Tiefe
     *                h_K in m und, ab Version 2, mittlere benetzte Tiefe h_A in m)
     * @param kantenWerte 2 (Version 1, P1–P3) oder 3 (Version 2, P4, F25)
     */
    public record Packung(float[] basis, short[] tabelle, int zellen, int kanten, int stufen, double zellflaeche, int kantenWerte) {
        /** Größe auf der Karte in Byte. */
        public long bytes() { return 4L * basis.length + 2L * tabelle.length; }

        /** Volumen (m³) aus der gepackten Tabelle – so rechnet die Karte. */
        public double volumen(int zelle, double eta) {
            int o = zelle * 3 * stufen;
            float x = (float) (eta - basis[zelle]);
            if (!(x > Float.float16ToFloat(tabelle[o]))) return 0;
            int l = stufen - 1;
            while (Float.float16ToFloat(tabelle[o + l]) > x) l--;
            float x0 = Float.float16ToFloat(tabelle[o + l]), d0 = Float.float16ToFloat(tabelle[o + stufen + l]);
            if (l == stufen - 1) return zellflaeche * (d0 + (x - x0));
            float x1 = Float.float16ToFloat(tabelle[o + l + 1]), d1 = Float.float16ToFloat(tabelle[o + stufen + l + 1]);
            return zellflaeche * (d0 + (d1 - d0) * (x - x0) / (x1 - x0));
        }

        /** Erster Wert der Tabelle einer Kante. */
        public int kantenVersatz(int kante) { return zellen * 3 * stufen + kante * kantenWerte * stufen; }

        /** Wirksame Tiefe h_K (m) der Kante aus der gepackten Tabelle. */
        public double wirksameTiefe(int kante, double eta) { return kantenWert(kante, eta, 1); }

        /** Mittlere benetzte Tiefe h_A (m) der Kante aus der gepackten Tabelle (ab Version 2). */
        public double querschnitt(int kante, double eta) {
            if (kantenWerte < 3) throw new IllegalStateException("Packung Version 1 trägt keinen Querschnitt (F25)");
            return kantenWert(kante, eta, 2);
        }

        /** Σ Δs/n der Kante. */
        public double weite(int kante) { return basis[zellen + 2 * kante + 1]; }

        /**
         * Ein Fenster der Packung eines Gitters g mit nx × ny Zellen: ni × nj Zellen ab Zelle (i0, j0), mit allen Kanten
         * des Fensters, auch denen auf seinem Rand (bitgleich mit dem Backen des Fensters; aus {@code HydroCheck}, P4b).
         */
        public Packung fenster(Gitter g, int i0, int j0, int ni, int nj) {
            int st = stufen, kw = kantenWerte, n = ni * nj, kx = (ni + 1) * nj, k = kx + ni * (nj + 1);
            float[] b = new float[n + 2 * k];
            short[] tab = new short[n * 3 * st + k * kw * st];
            int gkx = (g.nx + 1) * g.ny;
            for (int j = 0; j < nj; j++)
                for (int i = 0; i < ni; i++) {
                    int a = i + ni * j, q = (i0 + i) + g.nx * (j0 + j);
                    b[a] = basis[q];
                    System.arraycopy(tabelle, q * 3 * st, tab, a * 3 * st, 3 * st);
                }
            for (int j = 0; j <= nj; j++)
                for (int i = 0; i <= ni; i++)
                    for (int r = 0; r < 2; r++) {
                        if (r == 0 && j == nj || r == 1 && i == ni) continue;
                        int es = r == 0 ? i + (ni + 1) * j : kx + i + ni * j;
                        int ev = r == 0 ? (i0 + i) + (g.nx + 1) * (j0 + j) : gkx + (i0 + i) + g.nx * (j0 + j);
                        b[n + 2 * es] = basis[g.zellen() + 2 * ev];
                        b[n + 2 * es + 1] = basis[g.zellen() + 2 * ev + 1];
                        System.arraycopy(tabelle, kantenVersatz(ev), tab, n * 3 * st + es * kw * st, kw * st);
                    }
            return new Packung(b, tab, n, k, st, zellflaeche, kw);
        }

        private double kantenWert(int kante, double eta, int welche) {
            int o = kantenVersatz(kante);
            float x = (float) (eta - basis[zellen + 2 * kante]);
            if (!(x > Float.float16ToFloat(tabelle[o]))) return 0;
            int l = stufen - 1;
            while (Float.float16ToFloat(tabelle[o + l]) > x) l--;
            float x0 = Float.float16ToFloat(tabelle[o + l]), d0 = Float.float16ToFloat(tabelle[o + welche * stufen + l]);
            if (l == stufen - 1) return d0 + (x - x0);
            float x1 = Float.float16ToFloat(tabelle[o + l + 1]), d1 = Float.float16ToFloat(tabelle[o + welche * stufen + l + 1]);
            return d0 + (d1 - d0) * (x - x0) / (x1 - x0);
        }
    }

    /** Packt die Tabellen für die Karte. */
    public Packung packen() {
        int n = gitter.zellen();
        float[] basis = new float[n + 2 * kanten];
        short[] tab = new short[n * 3 * stufen + kanten * 3 * stufen];
        for (int c = 0; c < n; c++) {
            basis[c] = (float) stufe[c * stufen];
            for (int l = 0; l < stufen; l++) {
                int o = c * stufen + l, q = c * 3 * stufen + l;
                tab[q] = Float.floatToFloat16((float) (stufe[o] - basis[c]));
                tab[q + stufen] = Float.floatToFloat16((float) (volumen[o] / zeilenFlaeche[c / gitter.nx]));
                tab[q + 2 * stufen] = Float.floatToFloat16((float) (flaeche[o] / zeilenFlaeche[c / gitter.nx]));
            }
        }
        for (int e = 0; e < kanten; e++) {
            basis[n + 2 * e] = (float) kStufe[e * stufen];
            basis[n + 2 * e + 1] = (float) kWeite[e];
            for (int l = 0; l < stufen; l++) {
                int o = e * stufen + l, q = n * 3 * stufen + e * 3 * stufen + l;
                tab[q] = Float.floatToFloat16((float) (kStufe[o] - basis[n + 2 * e]));
                tab[q + stufen] = Float.floatToFloat16((float) kTiefe[o]);
                tab[q + 2 * stufen] = Float.floatToFloat16((float) kQuer[o]);
            }
        }
        return new Packung(basis, tab, n, kanten, stufen, zellflaeche, 3);
    }

    /** Ein feines Gelände aus einem Feld im Speicher: nx · ny Werte von Südwest zeilenweise. */
    public static Feingelaende aus(double[] werte, int nx, int ny) {
        return (i0, j0, breite, hoehe, ziel) -> {
            for (int v = 0; v < hoehe; v++)
                for (int u = 0; u < breite; u++) {
                    int i = i0 + u, j = j0 + v;
                    ziel[u + breite * v] = i < 0 || j < 0 || i >= nx || j >= ny ? Double.NaN : werte[i + nx * j];
                }
        };
    }
}
