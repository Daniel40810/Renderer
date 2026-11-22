package com.dan.physics.hydro;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Flachwasser auf einem Rechteckgitter mit Subgrid, Referenz in double auf dem Prozessor (Werkbuch HalligHooge Teil A,
 * 7.1 und 12.5). Versetzt: Wasserstand η und Volumen V je Zelle, Geschwindigkeit u normal je Kante (erst die Kanten
 * quer zu x, dann quer zu y, wie {@code Subgrid.kanteX/kanteY}).
 * <ul>
 * <li><b>Tabellen</b> aus der Packung (float16, wie auf der Karte): V(η) und benetzte Fläche je Zelle; je Kante
 * Querschnitt A(η) = Kantenlänge · h_A und Leitwert K(η) = Σ Δs/n · h_K^(5/3). Die Tiefe kommt immer aus der
 * Tabelle, nie aus η − b.</li>
 * <li><b>Schritt</b> (Stelling und Duinmeijer 2003, explizit): erst die Masse mit den alten Geschwindigkeiten:
 * V ← V − Δt Σ Q, Q = u A(η_oben); dann der Impuls aus dem neuen Stand mit denselben Q. Advektion impulserhaltend
 * (Q̄ u* − u ΔQ̄) / V̄ mit V̄ = Mittel der Volumen beider Zellen zum neuen Zeitpunkt, wo die Strömung langsamer wird
 * und an der Front über trockenes Land; sonst energieerhaltend (stromauf); quer dazu stromauf. In dieser Reihenfolge
 * bleibt der Impuls auch über die Zeit erhalten (Sprung nach Stoker unabhängig von der Courant-Zahl). Reibung
 * halbimplizit: u ← (u* − Δt g ∂η/∂x) / (1 + Δt g |u| A²/K²).</li>
 * <li><b>Kräfte</b>: Gefälle, Reibung, Coriolis (f je Zeile), Wind (τ = ρ_L C_d |U| U, C_d nach Wu 1982),
 * Luftdruckgefälle, Kraft aus dem Seegang, Smagorinsky.</li>
 * <li><b>Nass und trocken</b>: Eine Kante führt Wasser, wenn ihre mittlere Tiefe h_A beim Wasserstand stromauf
 * mindestens {@link #hTrocken} ist und die Zelle stromauf Wasser hat; sonst ist u = 0. Eine trockene Zelle hat
 * η = tiefste Pixelhöhe. Abfließendes Wasser ist auf das Volumen der Zelle begrenzt. Trockene Kanten sind für die
 * Viskosität und die Advektion quer Ufer ohne Haftung, wie der Rand.</li>
 * <li><b>Ränder</b> je Seite: Wand, Wasserstand (eine Geisterzelle außen) oder Durchfluss.</li>
 * <li><b>Bauwerke</b> (P4b): Deich- und Sielkanten ersetzen den Impuls. Überfall je Stelle der Krone nach Poleni, unter
 * Rückstau nach Villemonte; Siel Q = μ A √(2 g Δh) nur, wenn binnen höher steht (binnen aus {@link Ringe}). Q aus η beider
 * Zellen am Anfang des Schritts, begrenzt auf den Ausgleich beider Seiten.</li>
 * </ul>
 * Ändert sich das Volumen einer Zelle nicht, bleibt ihr η unverändert (ruhender See ohne Rundung über die Umkehrung).
 */
public final class Hydro {
    public static final String VERSION = "0.2";

    /** Randart je Seite (West, Ost, Süd, Nord). */
    public enum Rand { WAND, WASSERSTAND, DURCHFLUSS }

    /** Wert am Rand zur Zeit t am Ort (x, y) des Randpunkts: Wasserstand (m NHN) oder Durchfluss ins Gebiet (m²/s). */
    @FunctionalInterface public interface Randwert { double wert(double t, double x, double y); }

    public static final int WEST = 0, OST = 1, SUED = 2, NORD = 3;

    public final Gitter gitter;
    /** Die Packung, aus der die Tabellen kommen (für die Karte unverändert). */
    public final Subgrid.Packung packung;
    public final int nx, ny, zellen, kantenX, kanten, stufen;
    /**
     * Zellgröße (m) und Zellfläche (m²). In UTM überall gleich; auf geographischen Gittern (seit P5, G0) die Werte der
     * mittleren Zeile, nur zur Auskunft – gerechnet wird mit der Geometrie je Zeile ({@link #zeilenFlaeche} usw.).
     */
    public final double dx, dy, zellflaeche;
    /** Liegt das Gitter geographisch (Grad auf dem Ellipsoid)? Dann wechselt die Geometrie mit der Zeile. */
    public final boolean kugel;
    /**
     * Geometrie je Zeile j (0..ny−1): Zellfläche (m²), Breite (Abstand der Mitten längs x, m), Höhe (Länge der Kanten
     * quer zu x, m), Tangens der Breite durch den Erdradius (1/m, metrisches Glied; in UTM 0). Je Kantenzeile quer zu y
     * (0..ny): Länge der Kante (m), Abstand der Mitten (m; am Rand eine Zellhöhe) und Tangens durch Radius.
     */
    public final double[] zeilenFlaeche, zeilenBreite, zeilenHoehe, zeilenTan, kantenLaengeY, kantenAbstandY, kantenTanY;
    /** Kleinster Abstand der Mitten (m), für {@link #dtTeile} ohne nasse Kante. */
    public final double abstandMin;

    // Tabellen (aus float16) in double
    final double[] zSt, zV, zA;           // je Zelle und Stufe: Höhe, Volumen (m³), benetzte Fläche (m²)
    final double[] kSt, kHk, kHa, kWeite; // je Kante und Stufe: Höhe, h_K, h_A; je Kante Σ Δs/n
    /** Seit 0.2 (P9c): Σ Δs/n der Packung, die Bodenänderung je Zelle, die in den Stufen steckt (m; null: keine), und die Basis dazu für die Karte. */
    private final double[] kWeite0;
    private double[] bodenDz;
    private float[] basisJetzt;

    /** Zustand: Wasserstand (m NHN) und Volumen (m³) je Zelle, Geschwindigkeit (m/s) je Kante. */
    public final double[] eta, vol, u;
    private final double[] uNeu, ha, q;
    final double[] fluss;

    /**
     * Antrieb je Zelle (null: keiner): Wind 10 m (m/s, Schub nach Wu), Luftdruck (Pa), Kraft an der Oberfläche (N/m²:
     * seit P5e der Windschub vom Bus, wenn der Wind null ist; später auch die Kraft aus dem Seegang).
     */
    public double[] windX, windY, druck, kraftX, kraftY;
    /**
     * Gleichgewichtstide je Zelle (m; null: keine), schon mit dem Faktor 1 + k₂ − h₂ (seit P5, {@code tide.eq}): Das
     * Gefälle wirkt auf η − η_eq.
     */
    public double[] gleich;
    /** Coriolis-Parameter je Zeile (1/s); vorgegeben aus der Breite der Zeilenmitte. */
    public final double[] f;

    public double g = 9.81, rhoW = 1025, rhoL = 1.25, smagorinsky = 0.1;
    /** Faktor auf die Grenzen des Schritts für Wellen in zwei Richtungen [G] (seit P5, F32 A; bis P5a 0,7 je Kante). */
    public double cfl = 0.9;
    /** Tiefe (m), unter der Wind und Seegang nicht mehr angreifen [G]. */
    public double hWind = 0.01;
    /** Mittlere Tiefe h_A (m), unter der eine Kante trocken ist [G] (kein Film, der der Front vorausläuft). */
    public double hTrocken = 1e-4;

    /** Überfallbeiwert nach Poleni [G]. */
    public double muPoleni = 0.6;

    public final Rand[] rand = {Rand.WAND, Rand.WAND, Rand.WAND, Rand.WAND};
    public final Randwert[] randwert = new Randwert[4];
    /**
     * Je Seite mit Wasserstand (darf null sein): Boden der Geisterzelle am Ort (m NHN). Steht der Wasserstand nicht
     * darüber, ist sie trocken: Aus ihr fließt nichts zu (Schachtel über trockenen groben Zellen).
     */
    public final Randwert[] randBoden = new Randwert[4];
    /**
     * Je Seite mit Wasserstand: Abstand vom Ort des Werts am Rand zur Mitte der inneren Zelle, in Zellweiten (1: eine
     * Geisterzelle; in der {@link Kette} die Mitte der groben Zelle außen, (r + 1) / 2).
     */
    public final double[] randAbstand = {1, 1, 1, 1};

    public double zeit;
    public long schritte;
    /** Bilanz: zugeflossen und abgeflossen über die Ränder (m³), abgeschnitten (negative Volumen aus Rundung, m³). */
    public double zufluss, abfluss, abgeschnitten;

    public Hydro(Gitter gitter, Subgrid.Packung p) {
        if (p.kantenWerte() < 3) throw new IllegalArgumentException("hydro: Packung ohne Querschnitt (Version 1); neu backen (F25)");
        if (p.zellen() != gitter.zellen()) throw new IllegalArgumentException("hydro: Packung passt nicht zu " + gitter);
        this.gitter = gitter;
        this.packung = p;
        nx = gitter.nx;
        ny = gitter.ny;
        zellen = nx * ny;
        kantenX = (nx + 1) * ny;
        kanten = kantenX + nx * (ny + 1);
        stufen = p.stufen();
        kugel = gitter.lage == Gitter.Lage.GEOGRAFISCH;
        zeilenFlaeche = new double[ny];
        zeilenBreite = new double[ny];
        zeilenHoehe = new double[ny];
        zeilenTan = new double[ny];
        kantenLaengeY = new double[ny + 1];
        kantenAbstandY = new double[ny + 1];
        kantenTanY = new double[ny + 1];
        double radius = 6_371_000.0;
        for (int j = 0; j < ny; j++) {
            zeilenFlaeche[j] = kugel ? gitter.flaeche(j) : p.zellflaeche();
            zeilenBreite[j] = gitter.breite(j);
            zeilenHoehe[j] = gitter.hoehe(j);
            zeilenTan[j] = kugel ? Math.tan(Math.toRadians(gitter.y(j))) / radius : 0;
        }
        for (int j = 0; j <= ny; j++) {
            double lat = gitter.y0 + j * gitter.dy;
            kantenLaengeY[j] = kugel ? gitter.dx * com.dan.core.raum.Erde.meterJeGradLaenge(lat) : gitter.dx;
            kantenAbstandY[j] = j == 0 ? zeilenHoehe[0] : j == ny ? zeilenHoehe[ny - 1] : 0.5 * (zeilenHoehe[j - 1] + zeilenHoehe[j]);
            kantenTanY[j] = kugel ? Math.tan(Math.toRadians(lat)) / radius : 0;
        }
        dx = zeilenBreite[ny / 2];
        dy = zeilenHoehe[ny / 2];
        zellflaeche = zeilenFlaeche[ny / 2];
        double am = Double.POSITIVE_INFINITY;
        for (int j = 0; j < ny; j++) am = Math.min(am, Math.min(zeilenBreite[j], zeilenHoehe[j]));
        abstandMin = am;
        int S = stufen;
        zSt = new double[zellen * S];
        zV = new double[zellen * S];
        zA = new double[zellen * S];
        for (int c = 0; c < zellen; c++) {
            int o = c * 3 * S;
            for (int l = 0; l < S; l++) {
                zSt[c * S + l] = (double) p.basis()[c] + Float.float16ToFloat(p.tabelle()[o + l]);
                zV[c * S + l] = zeilenFlaeche[c / nx] * Float.float16ToFloat(p.tabelle()[o + S + l]);
                zA[c * S + l] = zeilenFlaeche[c / nx] * Float.float16ToFloat(p.tabelle()[o + 2 * S + l]);
            }
        }
        kSt = new double[kanten * S];
        kHk = new double[kanten * S];
        kHa = new double[kanten * S];
        kWeite = new double[kanten];
        for (int e = 0; e < kanten; e++) {
            int o = p.kantenVersatz(e);
            double b = p.basis()[zellen + 2 * e];
            kWeite[e] = p.basis()[zellen + 2 * e + 1];
            for (int l = 0; l < S; l++) {
                kSt[e * S + l] = b + Float.float16ToFloat(p.tabelle()[o + l]);
                kHk[e * S + l] = Float.float16ToFloat(p.tabelle()[o + S + l]);
                kHa[e * S + l] = Float.float16ToFloat(p.tabelle()[o + 2 * S + l]);
            }
        }
        kWeite0 = kWeite.clone();
        eta = new double[zellen];
        vol = new double[zellen];
        u = new double[kanten];
        uNeu = new double[kanten];
        ha = new double[kanten];
        q = new double[kanten];
        fluss = new double[kanten];
        f = new double[ny];
        for (int j = 0; j < ny; j++) {
            double[] gr = gitter.grad(gitter.x(nx / 2), gitter.y(j));
            f[j] = 2 * 7.2921150e-5 * Math.sin(Math.toRadians(gr[1]));
        }
        for (int c = 0; c < zellen; c++) eta[c] = zSt[c * S];
    }

    // ------------------------------------------------------------------ Boden und Rauheit vom Bus (seit 0.2, P9c)

    /** Die Basis der Tabellen, wie die Karte sie braucht: die der Packung, mit Bodenänderung und Rauheit, wenn es sie gibt. */
    public float[] basis() { return basisJetzt != null ? basisJetzt : packung.basis(); }

    /** Bodenänderung der Zelle (m), die in den Stufen steckt. */
    public double bodenDz(int c) { return bodenDz != null ? bodenDz[c] : 0; }

    /**
     * Setzt die Bodenänderung je Zelle gegen die Packung (m; {@code bed.dz}, F81 A): Die Tabelle der Zelle wird als Ganzes
     * verschoben, die der Kante um die größere Änderung ihrer beiden Zellen (am Rand des Gitters um die der inneren) – so
     * bleibt die tiefste Schwelle jeder Kante wie in der Packung mindestens so hoch wie der Grund beider Zellen: Hebt sich
     * eine Zelle, wird sie zur Schwelle; vertieft sich eine allein, bleibt sie eine Mulde hinter ihren Kanten. (Geplant war
     * das Mittel, F81 A; damit läge eine Kante unter dem Grund der höheren Zelle, und ihr Film flösse durch einen Querschnitt,
     * den es nicht gibt – Befund aus dem ersten Lauf M1, P9c.) Das Volumen
     * des Wassers bleibt; der Wasserstand geht mit dem Boden (η + Δ), auch der trockenen Zelle. Bauwerke und Ränder
     * bleiben, wo sie sind. Gibt die Zahl der Zellen zurück, deren Boden sich geändert hat.
     */
    public int boden(double[] dz) {
        if (dz.length != zellen) throw new IllegalArgumentException("hydro: Bodenänderung mit " + dz.length + " Werten statt " + zellen);
        if (bodenDz == null) bodenDz = new double[zellen];
        if (basisJetzt == null) basisJetzt = packung.basis().clone();
        final int S = stufen;
        final float[] pb = packung.basis();
        final short[] tab = packung.tabelle();
        final double[] neu = new double[zellen], alt = bodenDz;
        for (int c = 0; c < zellen; c++) neu[c] = Double.isFinite(dz[c]) ? dz[c] : 0;
        int n = java.util.stream.IntStream.range(0, zellen).parallel().map(c -> {
            double d = neu[c] - alt[c];
            if (d == 0) return 0;
            int o = c * 3 * S;
            for (int l = 0; l < S; l++) zSt[c * S + l] = (double) pb[c] + Float.float16ToFloat(tab[o + l]) + neu[c];
            eta[c] += d;
            basisJetzt[c] = (float) (pb[c] + neu[c]);
            return 1;
        }).sum();
        if (n == 0) return 0;
        java.util.stream.IntStream.range(0, kanten).parallel().forEach(e -> {
            int a = links(e), b = rechts(e);
            double dn = a >= 0 && b >= 0 ? Math.max(neu[a], neu[b]) : neu[a >= 0 ? a : b], da = a >= 0 && b >= 0 ? Math.max(alt[a], alt[b]) : alt[a >= 0 ? a : b];
            if (dn == da) return;
            int o = packung.kantenVersatz(e);
            double b0 = pb[zellen + 2 * e];
            for (int l = 0; l < S; l++) kSt[e * S + l] = b0 + Float.float16ToFloat(tab[o + l]) + dn;
            basisJetzt[zellen + 2 * e] = (float) (b0 + dn);
        });
        bodenDz = neu;
        return n;
    }

    /**
     * Setzt die Rauheit aus dem Sediment (P9c): Manning-n je Zelle (s/m^(1/3); ≤ 0 oder NaN: die Rauheit der Packung).
     * Σ Δs/n der Kante wird Länge / n, gemittelt über ihre beiden Zellen; eine Zelle ohne Wert trägt den Wert der Packung
     * bei. Gibt die Zahl der geänderten Kanten zurück.
     */
    public int rauheit(double[] n) {
        if (n.length != zellen) throw new IllegalArgumentException("hydro: Rauheit mit " + n.length + " Werten statt " + zellen);
        if (basisJetzt == null) basisJetzt = packung.basis().clone();
        return java.util.stream.IntStream.range(0, kanten).parallel().map(e -> {
            int a = links(e), b = rechts(e), k = 0;
            double s = 0;
            if (a >= 0) { s += n[a] > 0 ? laenge(e) / n[a] : kWeite0[e]; k++; }
            if (b >= 0) { s += n[b] > 0 ? laenge(e) / n[b] : kWeite0[e]; k++; }
            double w = s / k;
            if (w == kWeite[e]) return 0;
            kWeite[e] = w;
            basisJetzt[zellen + 2 * e + 1] = (float) w;
            return 1;
        }).sum();
    }

    /** Σ Δs/n der Kante jetzt und aus der Packung (m^(4/3)/s). */
    public double weite(int e) { return kWeite[e]; }
    public double weitePackung(int e) { return kWeite0[e]; }

    // ------------------------------------------------------------------ Tabellen

    /** Tiefste Pixelhöhe der Zelle (m NHN). */
    public double grund(int c) { return zSt[c * stufen]; }

    /** Volumen der Zelle beim Wasserstand η (m³). */
    public double volumen(int c, double h) {
        int o = c * stufen, l = stufe(zSt, o, h);
        if (l < 0) return 0;
        if (l == stufen - 1) return zV[o + l] + zeilenFlaeche[c / nx] * (h - zSt[o + l]);
        return zV[o + l] + (zV[o + l + 1] - zV[o + l]) * (h - zSt[o + l]) / (zSt[o + l + 1] - zSt[o + l]);
    }

    /** Benetzte Fläche der Zelle beim Wasserstand η (m²). */
    public double benetzt(int c, double h) {
        int o = c * stufen, l = stufe(zSt, o, h);
        if (l < 0) return 0;
        if (l == stufen - 1) return zeilenFlaeche[c / nx];
        return zA[o + l] + (zA[o + l + 1] - zA[o + l]) * (h - zSt[o + l]) / (zSt[o + l + 1] - zSt[o + l]);
    }

    /**
     * Speicher dV/dη der Zelle beim Wasserstand η (m²): die Sehne der Volumentabelle, an einer Stufe die kleinere der
     * beiden (seit P5). Die benetzte Fläche aus {@link #benetzt} wird zwischen zwei Stufen linear gelegt; springt sie
     * dort (eine Rinne zwischen geschlossenem Land), sieht sie bis zu vielfach zu viel Speicher (Werkbuch 12.6, Befund 9).
     */
    public double speicher(int c, double h) {
        int o = c * stufen, l = stufe(zSt, o, h);
        if (l < 0) return 0;
        double s = l == stufen - 1 ? zeilenFlaeche[c / nx] : (zV[o + l + 1] - zV[o + l]) / (zSt[o + l + 1] - zSt[o + l]);
        if (l > 0 && h - zSt[o + l] < 1e-9) s = Math.min(s, (zV[o + l] - zV[o + l - 1]) / (zSt[o + l] - zSt[o + l - 1]));
        return s;
    }

    /** Wasserstand zum Volumen (Umkehrung von {@link #volumen}); trocken: tiefste Pixelhöhe. */
    public double etaAus(int c, double v) {
        int o = c * stufen;
        if (!(v > 0)) return zSt[o];
        int l = stufen - 1;
        while (l > 0 && zV[o + l] > v) l--;
        if (l == stufen - 1) return zSt[o + l] + (v - zV[o + l]) / zeilenFlaeche[c / nx];
        double dv = zV[o + l + 1] - zV[o + l];
        return dv > 0 ? zSt[o + l] + (v - zV[o + l]) / dv * (zSt[o + l + 1] - zSt[o + l]) : zSt[o + l];
    }

    /** Mittlere benetzte Tiefe h_A der Kante (m). */
    public double hA(int e, double h) { return kante(kHa, e, h); }

    /** Wirksame Tiefe h_K der Kante (m). */
    public double hK(int e, double h) { return kante(kHk, e, h); }

    private double kante(double[] t, int e, double h) {
        int o = e * stufen, l = stufe(kSt, o, h);
        if (l < 0) return 0;
        if (l == stufen - 1) return t[o + l] + (h - kSt[o + l]);
        return t[o + l] + (t[o + l + 1] - t[o + l]) * (h - kSt[o + l]) / (kSt[o + l + 1] - kSt[o + l]);
    }

    private int stufe(double[] st, int o, double h) {
        if (!(h > st[o])) return -1;
        int l = stufen - 1;
        while (st[o + l] > h) l--;
        return l;
    }

    // ------------------------------------------------------------------ Kanten

    public int kanteX(int i, int j) { return i + (nx + 1) * j; }
    public int kanteY(int i, int j) { return kantenX + i + nx * j; }
    /** Länge der Kante (m). */
    public double laenge(int e) { return e < kantenX ? zeilenHoehe[e / (nx + 1)] : kantenLaengeY[(e - kantenX) / nx]; }

    /** Abstand der Mitten der beiden Zellen einer Kante (m); am Rand der zur Geisterzelle (eine Zellweite). */
    public double abstand(int e) { return e < kantenX ? zeilenBreite[e / (nx + 1)] : kantenAbstandY[(e - kantenX) / nx]; }

    /** Fläche einer Zelle (m²). */
    public double flaeche(int c) { return zeilenFlaeche[c / nx]; }

    /** Zellen links und rechts (bzw. unten und oben) einer Kante; −1 außerhalb. */
    public int links(int e) {
        if (e < kantenX) { int i = e % (nx + 1), j = e / (nx + 1); return i > 0 ? (i - 1) + nx * j : -1; }
        int k = e - kantenX, i = k % nx, j = k / nx;
        return j > 0 ? i + nx * (j - 1) : -1;
    }

    public int rechts(int e) {
        if (e < kantenX) { int i = e % (nx + 1), j = e / (nx + 1); return i < nx ? i + nx * j : -1; }
        int k = e - kantenX, i = k % nx, j = k / nx;
        return j < ny ? i + nx * j : -1;
    }

    /** Seite des Rands, an dem eine Randkante liegt, −1 innen. */
    int seite(int e) {
        if (e < kantenX) { int i = e % (nx + 1); return i == 0 ? WEST : i == nx ? OST : -1; }
        int j = (e - kantenX) / nx;
        return j == 0 ? SUED : j == ny ? NORD : -1;
    }

    // ------------------------------------------------------------------ Zustand

    /** Ruhender See: jede Zelle mit Grund unter η bekommt η, alle Geschwindigkeiten null. */
    public void fuellen(double h) {
        for (int c = 0; c < zellen; c++) setze(c, Math.max(h, grund(c)));
        Arrays.fill(u, 0);
    }

    /** Setzt den Wasserstand einer Zelle (und ihr Volumen). */
    public void setze(int c, double h) {
        double hh = Math.max(h, grund(c));
        vol[c] = volumen(c, hh);
        eta[c] = vol[c] > 0 ? hh : grund(c);
    }

    /** Gesamtvolumen (m³). */
    public double volumen() {
        double s = 0;
        for (double v : vol) s += v;
        return s;
    }

    /** Wasserstand außen am Rand einer Randkante zur Zeit t. */
    double randEta(int e, int s, double t) {
        double x, y;
        if (e < kantenX) { int i = e % (nx + 1), j = e / (nx + 1); x = gitter.x0 + i * gitter.dx; y = gitter.y(j); }
        else { int k = e - kantenX, i = k % nx, j = k / nx; x = gitter.x(i); y = gitter.y0 + j * gitter.dy; }
        return randwert[s].wert(t, x, y);
    }

    /** Wert der Seite einer Randkante zur Zeit t (Wasserstand oder Durchfluss, wie {@link #randwert}); 0 an einer Wand. */
    public double randWert(int e, double t) {
        int s = seite(e);
        if (s < 0 || rand[s] == Rand.WAND || randwert[s] == null) return 0;
        return randEta(e, s, t);
    }

    /** Boden der Geisterzelle einer Randkante zur Zeit t; ohne {@link #randBoden} −1·10³⁰. */
    public double randBodenWert(int e, double t) {
        int s = seite(e);
        if (s < 0 || randBoden[s] == null) return -1e30;
        double x, y;
        if (e < kantenX) { int i = e % (nx + 1), j = e / (nx + 1); x = gitter.x0 + i * gitter.dx; y = gitter.y(j); }
        else { int k = e - kantenX, i = k % nx, j = k / nx; x = gitter.x(i); y = gitter.y0 + j * gitter.dy; }
        return randBoden[s].wert(t, x, y);
    }

    /** Ist die Geisterzelle einer Randkante trocken (Wasserstand nicht über {@link #randBoden})? */
    private boolean randTrocken(int e, int s, double t, double r) {
        if (randBoden[s] == null) return false;
        double x, y;
        if (e < kantenX) { int i = e % (nx + 1), j = e / (nx + 1); x = gitter.x0 + i * gitter.dx; y = gitter.y(j); }
        else { int k = e - kantenX, i = k % nx, j = k / nx; x = gitter.x(i); y = gitter.y0 + j * gitter.dy; }
        return !(r > randBoden[s].wert(t, x, y));
    }

    // ------------------------------------------------------------------ Bauwerke

    private int[] bauNr;                                    // je Kante: Nummer des Bauwerks oder −1
    private final List<Integer> bauKante = new ArrayList<>();
    private final List<float[]> bauProfil = new ArrayList<>();
    private final List<String> bauName = new ArrayList<>();
    private double[] sielSohle = new double[0], sielA = new double[0], sielMu = new double[0];
    private int[] sielBinnen = new int[0];
    private Ringe ringe;

    /** Eine Deichkante mit der Krone je Stelle (m NHN; NaN: Stelle zu). Gibt die Nummer des Bauwerks. */
    public int deich(int kante, float[] profil) { return bau(kante, profil, Double.NaN, 0, 0, "Deich"); }

    /** Ein Siel auf der Kante: Krone je Stelle (Tor zu), Sohle (m NHN), Querschnitt A (m²), μ. Binnen kommt aus {@link #binnen()}. */
    public int siel(int kante, float[] profil, double sohle, double a, double mu, String name) { return bau(kante, profil, sohle, a, mu, name); }

    private int bau(int kante, float[] profil, double sohle, double a, double mu, String name) {
        if (kante < 0 || kante >= kanten || seite(kante) >= 0) throw new IllegalArgumentException("hydro: Bauwerk auf Kante " + kante + " (Rand oder außerhalb)");
        if (bauNr == null) {
            bauNr = new int[kanten];
            Arrays.fill(bauNr, -1);
        }
        if (bauNr[kante] >= 0) throw new IllegalArgumentException("hydro: Kante " + kante + " trägt schon ein Bauwerk");
        int k = bauKante.size();
        bauNr[kante] = k;
        bauKante.add(kante);
        bauProfil.add(profil.clone());
        bauName.add(name);
        sielSohle = Arrays.copyOf(sielSohle, k + 1);
        sielA = Arrays.copyOf(sielA, k + 1);
        sielMu = Arrays.copyOf(sielMu, k + 1);
        sielBinnen = Arrays.copyOf(sielBinnen, k + 1);
        sielSohle[k] = sohle;
        sielA[k] = a;
        sielMu[k] = mu;
        ringe = null;
        return k;
    }

    /** Begrenzung der Randflüsse durch das Gitter außen (für {@link Kette}): nach der Begrenzung je Zelle, vor dem Volumen. */
    interface Randgrenze {
        void begrenze(double dt);
    }

    /** Setzt {@link Kette} für ein feines Gitter: Flüsse über den Rand hinein höchstens so viel, wie die Zellen außen haben (P4d). */
    Randgrenze randGrenze;

    /** Kanten aus {@link #bauwerke(Gebiet.Bauten)}, die auf dem Rand des Gitters liegen und darum nicht gesetzt sind. */
    public int bauwerkeAmRand;

    /**
     * Alle Bauwerke eines Gebiets (Deich- und Sielkanten aus {@code bauwerke.txt}), danach {@link #binnen()}. Die
     * Kanten müssen zu diesem Gitter gehören. Bauwerke gibt es nur auf inneren Kanten: Eine Kante auf dem Rand des
     * Gitters wird ausgelassen und in {@link #bauwerkeAmRand} gezählt (in G3 von Hooge eine am Ostrand bei O 478 km, N 6 056,7 km, P4d). Gibt die
     * Zahl der Siele ohne Binnenseite zurück.
     */
    public int bauwerke(Gebiet.Bauten b) {
        java.util.Map<Integer, Gebiet.Bauten.Siel> siele = new java.util.HashMap<>();
        for (Gebiet.Bauten.Siel s : b.siele()) siele.put(s.kante(), s);
        for (Gebiet.Bauten.Kante k : b.kanten()) {
            if (k.index() < 0 || k.index() >= kanten || seite(k.index()) >= 0) {
                bauwerkeAmRand++;
                continue;
            }
            Gebiet.Bauten.Siel s = siele.get(k.index());
            if (s != null) siel(k.index(), k.profil(), s.sohle(), s.a(), s.mu(), s.name());
            else deich(k.index(), k.profil());
        }
        return binnen();
    }

    /**
     * Die Bauwerke eines Gitters, dessen Fenster ab Zelle (i0, j0) dieses Gitter ist (G3 um Hooge im Lauf, P5d; aus
     * {@code HydroCheck}, P4b): Kanten innerhalb des Fensters, ohne die auf seinem Rand; danach {@link #binnen()}. Gibt
     * die Zahl der Deich- und der Sielkanten zurück.
     */
    public int[] bauwerke(Gebiet.Bauten b, int i0, int j0) {
        java.util.Map<Integer, Gebiet.Bauten.Siel> siele = new java.util.HashMap<>();
        for (Gebiet.Bauten.Siel s : b.siele()) siele.put(s.kante(), s);
        int[] n = new int[2];
        for (Gebiet.Bauten.Kante k : b.kanten()) {
            int i = k.i() - i0, j = k.j() - j0;
            boolean drin = k.querX() ? i > 0 && i < nx && j >= 0 && j < ny : i >= 0 && i < nx && j > 0 && j < ny;
            if (!drin) continue;
            int e = k.querX() ? kanteX(i, j) : kanteY(i, j);
            Gebiet.Bauten.Siel s = siele.get(k.index());
            if (s != null) { siel(e, k.profil(), s.sohle(), s.a(), s.mu(), s.name()); n[1]++; }
            else { deich(e, k.profil()); n[0]++; }
        }
        binnen();
        return n;
    }

    /** Bestimmt binnen je Siel aus den Ringen; gibt die Zahl der Siele zurück, deren Seiten gleich tief liegen (sie bleiben zu). */
    public int binnen() {
        boolean[] bw = new boolean[kanten];
        for (int e : bauKante) bw[e] = true;
        ringe = new Ringe(nx, ny, bw);
        int offen = 0;
        for (int k = 0; k < bauKante.size(); k++) {
            if (Double.isNaN(sielSohle[k])) continue;
            sielBinnen[k] = ringe.binnen(bauKante.get(k));
            if (sielBinnen[k] == 0) offen++;
        }
        return offen;
    }

    /** Trägt die Kante e ein Bauwerk? */
    public boolean istBauwerk(int e) { return bauNr != null && bauNr[e] >= 0; }

    /** Zahl der Bauwerke. */
    public int bauwerke() { return bauKante.size(); }
    /** Kante des Bauwerks k. */
    public int bauKante(int k) { return bauKante.get(k); }
    /** Name des Bauwerks k („Deich“ oder der Name des Siels). */
    public String bauName(int k) { return bauName.get(k); }
    /** Ist das Bauwerk k ein Siel? */
    public boolean istSiel(int k) { return !Double.isNaN(sielSohle[k]); }
    /** Binnenzelle des Siels k (nach {@link #binnen()}), −1 unbestimmt. */
    public int sielBinnenZelle(int k) {
        int e = bauKante.get(k);
        return sielBinnen[k] > 0 ? rechts(e) : sielBinnen[k] < 0 ? links(e) : -1;
    }
    /** Je Kante die Nummer des Bauwerks oder −1 (Kopie; null ohne Bauwerke). */
    public int[] bauwerkNummern() { return bauNr == null ? null : bauNr.clone(); }
    /** Krone je Stelle des Bauwerks k (NaN: zu). */
    public float[] bauProfil(int k) { return bauProfil.get(k).clone(); }
    /** Siel k: {Sohle, A, μ, binnen (+1 rechts/oben, −1 links/unten, 0 zu)}; beim Deich {NaN, 0, 0, 0}. */
    public double[] sielWerte(int k) { return new double[] {sielSohle[k], sielA[k], sielMu[k], sielBinnen[k]}; }

    /** Die Ringe aus dem letzten {@link #binnen()} (null vorher). */
    public Ringe ringe() { return ringe; }

    /**
     * Fluss über ein Bauwerk (m³/s, + von links nach rechts) bei den Wasserständen ea links und eb rechts: Überfall je
     * Stelle (Poleni, Villemonte) und beim Siel der Durchlass.
     */
    public double bauwerkFluss(int k, double ea, double eb) {
        int e = bauKante.get(k);
        float[] pr = bauProfil.get(k);
        double ho = Math.max(ea, eb), hu = Math.min(ea, eb), q = 0;
        double c = 2.0 / 3 * muPoleni * Math.sqrt(2 * g) * laenge(e) / pr.length;
        for (float z : pr) {
            if (Float.isNaN(z)) continue;
            double h = ho - z;
            if (!(h > 0)) continue;
            double r = Math.max(0, hu - z) / h;
            q += c * h * Math.sqrt(h) * (r > 0 ? Math.pow(1 - Math.pow(r, 1.5), 0.385) : 1);
        }
        q = ea >= eb ? q : -q;
        if (!Double.isNaN(sielSohle[k]) && sielBinnen[k] != 0) {
            double ein = sielBinnen[k] > 0 ? eb : ea, aus = sielBinnen[k] > 0 ? ea : eb;
            if (ein > aus && ein > sielSohle[k]) {
                double qs = sielMu[k] * sielA[k] * Math.sqrt(2 * g * (ein - Math.max(aus, sielSohle[k])));
                q += sielBinnen[k] > 0 ? -qs : qs;
            }
        }
        return q;
    }

    /** Fluss über das Bauwerk k auf der Kante e im Massenschritt: aus η am Anfang, begrenzt auf den Ausgleich beider Seiten. */
    private void bauwerk(int e, int k, double dt) {
        int a = links(e), b = rechts(e);
        double ea = eta[a], eb = eta[b];
        double q = bauwerkFluss(k, ea, eb);
        if (q != 0) {
            int o = q > 0 ? a : b, un = q > 0 ? b : a;
            double eo = eta[o], eu = eta[un];
            // Ausgleich, wo das Unterwasser über der niedrigsten Krone bzw. der Sohle steht
            double frei = Double.isNaN(sielSohle[k]) ? Double.POSITIVE_INFINITY : sielSohle[k];
            for (float z : bauProfil.get(k)) if (z < frei) frei = z;
            if (vol[un] > 0 && eu > frei) {
                double ao = benetzt(o, eo), au = benetzt(un, eu);
                if (ao > 0 && au > 0) {
                    double grenze = (eo - eu) * ao * au / (ao + au) / dt;
                    if (Math.abs(q) > grenze) q = Math.copySign(grenze, q);
                }
            }
        }
        fluss[e] = q;
        u[e] = 0;                       // der Strahl gibt keinen Impuls weiter: Poleni und Villemonte enthalten den Verlust
    }

    /** Durchfluss einer Kante im jetzigen Stand (m³/s, + von links nach rechts); bei Bauwerken der des letzten Schritts. */
    public double durchfluss(int e) {
        if (bauNr != null && bauNr[e] >= 0) return fluss[e];
        if (u[e] == 0) return 0;
        double h = obenEta(e, u[e], zeit), a = h == Double.NEGATIVE_INFINITY ? 0 : hA(e, h);
        return a < hTrocken ? 0 : u[e] * laenge(e) * a;
    }

    /** Fluss einer Kante im letzten Schritt (m³/s, + von links nach rechts), wie ihn die Masse genommen hat. */
    public double fluss(int e) { return fluss[e]; }

    /** Setzt die Flüsse des letzten Schritts (vom Abgleich mit der Karte). */
    public void setzeFluss(float[] f) { for (int e = 0; e < kanten; e++) fluss[e] = f[e]; }

    // ------------------------------------------------------------------ Schritt

    /**
     * Größter Schritt (s), das Kleinere aus zwei Bedingungen mal {@link #cfl} (seit P5, F32 A: beide für Wellen in zwei
     * Richtungen; für Masse und Impuls nacheinander gilt auf ebenem Grund Δt ≤ Δ / (√2 c), Werkbuch 12.6, Stand P5a):
     * <ul>
     * <li><b>Welle</b> je Zelle: 1 / √(r_x² + r_y²), r_x das Größte über ihre beiden Kanten in x von (|u| + √(g h)) /
     * Abstand, r_y ebenso in y; gezählt werden nasse Kanten ohne Bauwerk, am Rand nur an einer Seite mit Wasserstand.
     * h über der tiefsten Stelle der Kante. Fließt das Wasser nur in einer Richtung (eine Rinne), gilt die Grenze in
     * einer Richtung.</li>
     * <li><b>Speicher</b> je nasser Zelle: 2 / √λ mit λ = g Σ_Kanten (A_e / Δ_e) (1 / S_Zelle + 1 / S_Nachbar), A_e der
     * Querschnitt beim höheren Wasserstand, S der Speicher dV/dη ({@link #speicher}); an einer Randkante mit
     * Wasserstand ohne Nachbarn. Das ist die Grenze nach Gerschgorin für die Schwingung von Masse und Impuls
     * (gewichtet mit 1 / S): Kein Eigenwert liegt darüber, auf ebenem Grund ist sie genau. Eine Zelle mit wenig
     * Speicher an einer tiefen Kante reagiert stark auf ihren Fluss (Stand P4b).</li>
     * </ul>
     */
    public double dtCfl() {
        double[] t = dtTeile();
        return Math.min(t[0], t[1]);
    }

    /** Die beiden Grenzen von {@link #dtCfl()} einzeln, schon mal {@link #cfl}: {Welle, Speicher} (s). */
    public double[] dtTeile() {
        double r = IntStream.range(0, zellen).parallel().mapToDouble(c -> {
            int[] k = kantenVon(c);
            double rx = 0, ry = 0;
            for (int n = 0; n < 4; n++) {
                double q = welle(k[n]);
                if (n < 2) rx = Math.max(rx, q);
                else ry = Math.max(ry, q);
            }
            return Math.sqrt(rx * rx + ry * ry);
        }).max().orElse(0);
        double w = IntStream.range(0, zellen).parallel().mapToDouble(c -> {
            if (!(vol[c] > 0)) return 0;
            double ac = speicher(c, eta[c]);
            if (!(ac > 0)) return 0;
            double sum = 0;
            for (int e : kantenVon(c)) {
                if (bauNr != null && bauNr[e] >= 0) continue;
                int s = seite(e);
                if (s >= 0 && rand[s] != Rand.WASSERSTAND) continue;
                int n = links(e) == c ? rechts(e) : links(e);
                double hoch = n >= 0 ? Math.max(eta[c], eta[n]) : eta[c];
                double ae = laenge(e) * hA(e, hoch);
                if (!(ae > 0)) continue;
                double an = n >= 0 ? speicher(n, eta[n]) : Double.POSITIVE_INFINITY;
                sum += ae / abstand(e) * (1 / ac + (an > 0 ? 1 / an : 0));
            }
            return g * sum;
        }).max().orElse(0);
        return new double[] {r > 0 ? cfl / r : cfl * abstandMin, w > 0 ? cfl * 2 / Math.sqrt(w) : Double.POSITIVE_INFINITY};
    }

    /**
     * Rate der Welle an einer Kante für {@link #dtTeile}: (|u| + √(g h)) / Abstand (1/s); 0 für eine trockene Kante,
     * ein Bauwerk (Q aus η, begrenzt, kein Impuls) und eine Randkante an einer Seite ohne Wasserstand (dort ist der
     * Fluss vorgegeben).
     */
    private double welle(int e) {
        if (bauNr != null && bauNr[e] >= 0) return 0;
        int s = seite(e);
        if (s >= 0 && rand[s] != Rand.WASSERSTAND) return 0;
        int a = links(e), b = rechts(e);
        double h = Math.max(a >= 0 ? eta[a] : -1e30, b >= 0 ? eta[b] : -1e30) - kSt[e * stufen];
        if (!(h > 0)) return 0;
        return (Math.abs(u[e]) + Math.sqrt(g * h)) / abstand(e);
    }

    /** Ein Schritt mit dem Schritt dt (s): erst die Masse mit dem alten u, dann der Impuls aus dem neuen Stand. */
    public void schritt(double dt) {
        masse(dt, zeit);
        impuls(dt, zeit + dt);
        zeit += dt;
        schritte++;
    }

    /**
     * Masse (für {@link Kette}): Fluss je Kante mit dem jetzigen u über dt ab der Zeit t (Ränder zur Zeit t, Durchfluss
     * zur Mitte), Volumen und η neu; die Zeit bleibt. Gesperrte Kanten tragen keinen Fluss.
     */
    void masse(double dt, double t) {
        // Masse: Fluss je Kante (m³/s), stromauf nach dem alten u, begrenzt auf das Volumen stromauf
        IntStream.range(0, kanten).parallel().forEach(e -> {
            if (gesperrt != null && gesperrt[e]) {
                fluss[e] = 0;
                return;
            }
            int s = seite(e);
            if (s >= 0 && rand[s] == Rand.DURCHFLUSS) {
                fluss[e] = (links(e) < 0 ? 1 : -1) * durchflussRand(e, s, t + 0.5 * dt) * laenge(e);
                return;
            }
            if (bauNr != null && bauNr[e] >= 0 && s < 0) {
                bauwerk(e, bauNr[e], dt);
                return;
            }
            double h = obenEta(e, u[e], t);
            double a = h == Double.NEGATIVE_INFINITY ? 0 : hA(e, h);
            fluss[e] = a < hTrocken || u[e] == 0 ? 0 : u[e] * laenge(e) * a;
            if (fluss[e] == 0) u[e] = 0;
        });
        double[] faktor = new double[zellen];
        IntStream.range(0, zellen).parallel().forEach(c -> {
            double aus = 0;
            for (int e : kantenVon(c)) {
                double fl = fluss[e];
                if (links(e) == c && fl > 0 || rechts(e) == c && fl < 0) aus += Math.abs(fl);
            }
            faktor[c] = aus * dt > vol[c] ? vol[c] / (aus * dt) : 1;
        });
        IntStream.range(0, kanten).parallel().forEach(e -> {
            double fl = fluss[e];
            if (fl == 0) return;
            int von = fl > 0 ? links(e) : rechts(e);
            if (von >= 0 && faktor[von] < 1) {
                fluss[e] = fl * faktor[von];
                u[e] = u[e] * faktor[von];
            }
        });
        for (int e = 0; e < kanten; e++) {
            if (fluss[e] == 0 || seite(e) < 0) continue;
            double rein = links(e) < 0 ? fluss[e] : -fluss[e];
            if (rein > 0) zufluss += rein * dt;
            else abfluss -= rein * dt;
        }
        if (randGrenze != null) randGrenze.begrenze(dt);
        if (qSumme != null) for (int e = 0; e < kanten; e++) qSumme[e] += fluss[e] * dt;
        if (qBrutto != null) for (int e = 0; e < kanten; e++) qBrutto[e] += Math.abs(fluss[e]) * dt;
        double[] clip = new double[zellen];
        IntStream.range(0, zellen).parallel().forEach(c -> {
            double s = 0;
            for (int e : kantenVon(c)) s += links(e) == c ? -fluss[e] : fluss[e];
            if (s == 0) return;
            double v = vol[c] + dt * s;
            if (v < 0) { clip[c] = -v; v = 0; }
            vol[c] = v;
            eta[c] = etaAus(c, v);
        });
        for (double x : clip) abgeschnitten += x;
    }

    /**
     * Impuls (für {@link Kette}): u aus dem jetzigen Stand über dt, Ränder und Antrieb zur Zeit t1; der Fluss je Breite
     * für die Advektion aus dem Fluss der letzten Masse. Die Zeit bleibt. Gesperrte Kanten behalten ihr u.
     */
    void impuls(double dt, double t1) {
        // Querschnitt stromauf im neuen Stand, Fluss je Breite aus der Masse
        IntStream.range(0, kanten).parallel().forEach(e -> {
            double h = obenEta(e, u[e], t1);
            ha[e] = h == Double.NEGATIVE_INFINITY ? 0 : hA(e, h);
            q[e] = fluss[e] / laenge(e);
        });
        // Impuls aus dem neuen Stand
        IntStream.range(0, ny + 1).parallel().forEach(j -> {
            for (int i = 0; i <= nx; i++) {
                if (j < ny) uNeu[kanteX(i, j)] = impuls(kanteX(i, j), true, i, j, dt, t1);
                if (i < nx) uNeu[kanteY(i, j)] = impuls(kanteY(i, j), false, i, j, dt, t1);
            }
        });
        System.arraycopy(uNeu, 0, u, 0, kanten);
    }

    /**
     * Summe der Flüsse je Kante über alle Massen (m³, Σ Fluss · dt), für das Zeitmittel {@code water.q} (seit P5c,
     * {@link HydroModul}); null: wird nicht gesammelt. Gesperrte Kanten unter einem feineren Gitter tragen 0.
     */
    public double[] qSumme;
    /** Umsatz je Kante über alle Massen (m³, Σ |Fluss| · dt), zur Prüfung von {@code water.q}: Maßstab der Rundung (seit P5c); null: wird nicht gesammelt. */
    public double[] qBrutto;

    /** Je Kante: gesperrt (kein Fluss, kein Impuls; u setzt {@link Kette}), sonst null. */
    boolean[] gesperrt;

    /** Sperrt eine Kante (Kanten des groben Gitters unter und an einem feinen). */
    void sperren(int e) {
        if (gesperrt == null) gesperrt = new boolean[kanten];
        gesperrt[e] = true;
    }

    /** Ist die Kante gesperrt? */
    public boolean istGesperrt(int e) { return gesperrt != null && gesperrt[e]; }

    /**
     * Läuft bis zur Zeit {@code bis} mit Schritten nach CFL (höchstens {@code dtMax}). Gibt die Zahl der Schritte zurück.
     * Der Rest bis {@code bis} wird auf gleiche Schritte verteilt, statt am Ende einen kurzen anzuhängen: Springt der
     * Schritt von voll auf kurz und zurück, wird das Verfahren nahe seiner Grenze instabil (seit P5, Werkbuch 12.6).
     */
    public int lauf(double bis, double dtMax) {
        int n = 0;
        while (zeit < bis - 1e-9) {
            double rest = bis - zeit, dtc = Math.min(dtMax, dtCfl());
            int k = (int) Math.ceil(rest / dtc - 1e-9);
            schritt(k <= 1 ? rest : rest / k);
            n++;
        }
        return n;
    }

    /** Die vier Kanten einer Zelle: West, Ost, Süd, Nord. */
    int[] kantenVon(int c) {
        int i = c % nx, j = c / nx;
        return new int[] {kanteX(i, j), kanteX(i + 1, j), kanteY(i, j), kanteY(i, j + 1)};
    }

    /**
     * Wasserstand stromauf einer Kante: nach dem Vorzeichen von u, bei u = 0 die höhere Seite. Liegt stromauf eine
     * trockene Zelle oder eine Wand, ist es {@code −∞}.
     */
    double obenEta(int e, double ue, double t) {
        int a = links(e), b = rechts(e), s = seite(e);
        double ea = a >= 0 ? eta[a] : Double.NaN, eb = b >= 0 ? eta[b] : Double.NaN;
        if (s >= 0) {
            if (rand[s] == Rand.WAND) return Double.NEGATIVE_INFINITY;
            if (rand[s] == Rand.DURCHFLUSS) {
                int innen = a >= 0 ? a : b;
                return vol[innen] > 0 ? eta[innen] : Double.NEGATIVE_INFINITY;
            }
            double r = randEta(e, s, t);
            if (a < 0) ea = r;
            else eb = r;
        }
        boolean nachB = ue > 0 || ue == 0 && ea >= eb;            // Strömung von a nach b
        int von = nachB ? a : b;
        double h = nachB ? ea : eb;
        if (von >= 0 && !(vol[von] > 0)) return Double.NEGATIVE_INFINITY;
        if (von < 0 && randTrocken(e, s, t, h)) return Double.NEGATIVE_INFINITY;
        return h;
    }

    /** Neue Geschwindigkeit einer Kante. */
    private double impuls(int e, boolean querX, int i, int j, double dt, double t) {
        if (gesperrt != null && gesperrt[e]) return u[e];
        int s = seite(e);
        if (s >= 0 && rand[s] == Rand.WAND) return 0;
        if (s < 0 && bauNr != null && bauNr[e] >= 0) return 0;                  // Bauwerk: Fluss aus η, kein Impuls
        if (s >= 0 && rand[s] == Rand.DURCHFLUSS) {
            double h = ha[e], richtung = links(e) < 0 ? 1 : -1;     // + zeigt von links nach rechts
            return h > 0 ? richtung * durchflussRand(e, s, t) / h : 0;
        }
        int a = links(e), b = rechts(e);
        double ea = a >= 0 ? eta[a] : randEta(e, s, t), eb = b >= 0 ? eta[b] : randEta(e, s, t);
        double ue = u[e];
        double hOben = obenEta(e, ue, t);
        if (hOben == Double.NEGATIVE_INFINITY) return 0;
        double hA = hA(e, hOben), hK = hK(e, hOben);
        if (!(hA >= hTrocken) || !(hK > 0)) return 0;
        double d = abstand(e) * (s >= 0 ? randAbstand[s] : 1), dq = querX ? zeilenHoehe[j] : kantenLaengeY[j];
        double gefaelle = -g * (eb - ea) / d;
        if (gleich != null) gefaelle += g * ((b >= 0 ? gleich[b] : gleich[a]) - (a >= 0 ? gleich[a] : gleich[b])) / d;
        // stromauf bei u = 0 gegen die Richtung des Gefälles: dann fließt nichts
        if (ue == 0) {
            int von = gefaelle > 0 ? a : b;
            if (von >= 0 && !(vol[von] > 0)) return 0;
        }
        double adv = advektion(e, querX, i, j, ue, d, dq);
        double fq = coriolis(e, querX, i, j);
        double kraft = 0;
        if (hA >= hWind) {
            double wx = mittel(windX, a, b), wy = mittel(windY, a, b), w = querX ? wx : wy;
            if (w != 0 || wx != 0 || wy != 0) {
                double betrag = Math.hypot(wx, wy), cd = (0.8 + 0.065 * betrag) * 1e-3;
                kraft += rhoL * cd * betrag * w / (rhoW * hA);
            }
            double k = mittel(querX ? kraftX : kraftY, a, b);
            kraft += k / (rhoW * hA);
        }
        if (druck != null) {
            double pa = a >= 0 ? druck[a] : druck[b], pb = b >= 0 ? druck[b] : druck[a];
            kraft -= (pb - pa) / (rhoW * d);
        }
        double visk = smagorinsky > 0 ? viskositaet(e, querX, i, j, d, dq) : 0;
        double l = laenge(e), aQ = l * hA, kL = kWeite[e] * Math.pow(hK, 5.0 / 3);
        double reib = g * Math.abs(ue) * aQ * aQ / (kL * kL);
        return (ue + dt * (gefaelle - adv + fq + kraft + visk)) / (1 + dt * reib);
    }

    private double durchflussRand(int e, int s, double t) {
        double x, y;
        if (e < kantenX) { int i = e % (nx + 1), j = e / (nx + 1); x = gitter.x0 + i * gitter.dx; y = gitter.y(j); }
        else { int k = e - kantenX, i = k % nx, j = k / nx; x = gitter.x(i); y = gitter.y0 + j * gitter.dy; }
        return randwert[s].wert(t, x, y);
    }

    private static double mittel(double[] f, int a, int b) {
        if (f == null) return 0;
        if (a < 0) return f[b];
        if (b < 0) return f[a];
        return 0.5 * (f[a] + f[b]);
    }

    /** u einer Kante gleicher Richtung, versetzt um (di, dj); NaN außerhalb. */
    private double uBei(boolean querX, int i, int j, int di, int dj) {
        int a = i + di, b = j + dj;
        if (querX) return a < 0 || a > nx || b < 0 || b >= ny ? Double.NaN : u[kanteX(a, b)];
        return a < 0 || a >= nx || b < 0 || b > ny ? Double.NaN : u[kanteY(a, b)];
    }

    /** Wie {@link #uBei}, aber NaN auch an trockenen Kanten (Ufer ohne Haftung, wie der Rand). */
    private double uNass(boolean querX, int i, int j, int di, int dj) {
        double w = uBei(querX, i, j, di, dj);
        return Double.isNaN(w) || haBei(querX, i, j, di, dj) > 0 ? w : Double.NaN;
    }

    private double qBei(boolean querX, int i, int j, int di, int dj) {
        int a = i + di, b = j + dj;
        if (querX) return a < 0 || a > nx || b < 0 || b >= ny ? 0 : q[kanteX(a, b)];
        return a < 0 || a >= nx || b < 0 || b > ny ? 0 : q[kanteY(a, b)];
    }

    /** Mittel der vier Geschwindigkeiten der anderen Richtung um die Kante. */
    private double quer(boolean querX, int i, int j) {
        double s = 0;
        int n = 0;
        int[][] o = querX ? new int[][] {{i - 1, j}, {i - 1, j + 1}, {i, j}, {i, j + 1}} : new int[][] {{i, j - 1}, {i + 1, j - 1}, {i, j}, {i + 1, j}};
        for (int[] p : o) {
            if (querX ? p[0] < 0 || p[0] >= nx || p[1] < 0 || p[1] > ny : p[0] < 0 || p[0] > nx || p[1] < 0 || p[1] >= ny) continue;
            s += querX ? u[kanteY(p[0], p[1])] : u[kanteX(p[0], p[1])];
            n++;
        }
        return n == 0 ? 0 : s / 4;
    }

    /**
     * Coriolis, auf der Kugel mit dem metrischen Glied: (f + u tan φ / R) v an den Kanten quer zu x, −(f + ū tan φ / R) ū
     * an den Kanten quer zu y (ū das Mittel der vier u um die Kante).
     */
    private double coriolis(int e, boolean querX, int i, int j) {
        double v = quer(querX, i, j);
        if (v == 0) return 0;
        double fj = querX ? f[j] : 0.5 * (f[Math.max(0, j - 1)] + f[Math.min(ny - 1, j)]);
        if (kugel) fj += querX ? u[e] * zeilenTan[j] : v * kantenTanY[j];
        return querX ? fj * v : -fj * v;
    }

    /**
     * Advektion u ∂u/∂x + v ∂u/∂y (Stelling und Duinmeijer). Impulserhaltend (q̄_R u*_R − q̄_L u*_L − u (q̄_R − q̄_L)) / (h̄ Δ)
     * mit q aus der Masse dieses Schritts und h̄ Δ L = V̄ (Mittel der neuen Volumen beider Zellen), wo die Strömung
     * langsamer wird oder zusammenläuft (die Kante stromab ist nass) und an der Front (die Kante stromab ist trocken);
     * sonst energieerhaltend stromauf. Quer dazu stromauf.
     */
    private double advektion(int e, boolean querX, int i, int j, double ue, double d, double dq) {
        int di = querX ? 1 : 0, dj = querX ? 0 : 1;
        double adv = 0;
        double uv = uBei(querX, i, j, -di, -dj), un = uBei(querX, i, j, di, dj);
        if (Double.isNaN(uv)) uv = ue;
        if (Double.isNaN(un)) un = ue;
        double qe = q[e], qv = qBei(querX, i, j, -di, -dj), qn = qBei(querX, i, j, di, dj);
        double qL = 0.5 * (qv + qe), qR = 0.5 * (qe + qn);
        boolean vor = ue > 0 || ue == 0 && qL > 0;                 // Richtung: nach u, bei u = 0 nach dem Zufluss
        double uDown = vor ? un : uv, haDown = vor ? haBei(querX, i, j, di, dj) : haBei(querX, i, j, -di, -dj);
        boolean front = !(haDown > 0);
        boolean langsamer = Math.signum(uDown) != Math.signum(ue) || Math.abs(uDown) < Math.abs(ue);
        if (front || langsamer) {
            int a = links(e), b = rechts(e);
            double hq = 0.5 * ((a >= 0 ? vol[a] : 0) + (b >= 0 ? vol[b] : 0)) / (laenge(e) * d);
            if (hq > 0) {
                double uL = qL > 0 ? uv : ue, uR = qR > 0 ? ue : un;
                adv += (qR * uR - qL * uL - ue * (qR - qL)) / (hq * d);
            }
        } else if (ue != 0) {
            adv += ue > 0 ? ue * (ue - uv) / d : ue * (un - ue) / d;
        }
        double v = quer(querX, i, j);
        if (v != 0) {
            double us = uNass(querX, i, j, -dj, -di), uo = uNass(querX, i, j, dj, di);
            if (v > 0 && !Double.isNaN(us)) adv += v * (ue - us) / dq;
            else if (v < 0 && !Double.isNaN(uo)) adv += v * (uo - ue) / dq;
        }
        return adv;
    }

    private double haBei(boolean querX, int i, int j, int di, int dj) {
        int a = i + di, b = j + dj;
        if (querX) return a < 0 || a > nx || b < 0 || b >= ny ? 0 : ha[kanteX(a, b)];
        return a < 0 || a >= nx || b < 0 || b > ny ? 0 : ha[kanteY(a, b)];
    }

    /** Wirbelviskosität nach Smagorinsky: ν ∇²u mit ν = (C_s Δ)² |S|; trockene Nachbarkanten und der Rand ohne Haftung. */
    private double viskositaet(int e, boolean querX, int i, int j, double d, double dq) {
        int di = querX ? 1 : 0, dj = querX ? 0 : 1;
        double ue = u[e];
        double a = uNass(querX, i, j, -di, -dj), b = uNass(querX, i, j, di, dj), c = uNass(querX, i, j, -dj, -di), dd = uNass(querX, i, j, dj, di);
        if (Double.isNaN(a)) a = ue;
        if (Double.isNaN(b)) b = ue;
        if (Double.isNaN(c)) c = ue;
        if (Double.isNaN(dd)) dd = ue;
        if (a == ue && b == ue && c == ue && dd == ue) return 0;
        double ux = (b - a) / (2 * d), uy = (dd - c) / (2 * dq);
        double nu = Math.pow(smagorinsky * Math.sqrt(d * dq), 2) * Math.sqrt(2 * ux * ux + uy * uy);
        return nu * ((a - 2 * ue + b) / (d * d) + (c - 2 * ue + dd) / (dq * dq));
    }

    /** Geschwindigkeit in der Zelle {u, v} (m/s): Fluss der Kanten durch Querschnitt, gemittelt. */
    public double[] geschwindigkeit(int c) {
        int[] k = kantenVon(c);
        double h = vol[c] > 0 ? vol[c] / Math.max(benetzt(c, eta[c]), 1e-12) : 0;
        if (!(h > 0)) return new double[2];
        return new double[] {0.5 * (u[k[0]] + u[k[1]]), 0.5 * (u[k[2]] + u[k[3]])};
    }
}
