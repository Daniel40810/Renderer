package com.dan.aufbereitung;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.raum.Erde;
import com.dan.core.raum.Utm;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Backen der Gitter G0–G4 aus dem Stapel (Werkbuch HalligHooge Teil A, 10.4, Schritt 7). Das Feingelände eines
 * Gitters ist der Stapel ({@link Gelaende#feingelaende}); dazu beim Backen, ohne den Stapel zu ändern:
 * <ul>
 * <li>{@link Zusatz#anteil}: G1 – ein Pixel ist geschlossen, wenn der Anteil im 100-m-Pixel, in dem seine Mitte liegt,
 * mindestens ½ ist;</li>
 * <li>{@link Zusatz#maske}: G2 – geschlossen nach {@link Schliessen#zu(Gelaende.Stufe, double, double)};</li>
 * <li>{@link Zusatz#mindest}: G3 und G4 – das Gelände wird mindestens so hoch wie die Mindesthöhe.</li>
 * </ul>
 * Ein geschlossenes Pixel bekommt {@link Zusatz#hoehe} (F23, F24). {@code ground.z} ist das Zellmittel des gebackenen
 * Feingeländes, genau aus der Tabelle: höchste Stufe minus Volumen dort durch Zellfläche.
 */
public final class Backen {
    private Backen() { }

    /** Was beim Backen zum Stapel kommt; null heißt: nichts davon. */
    public record Zusatz(Gelaende.Stufe anteil, Gelaende.Stufe maske, Gelaende.Stufe mindest, double hoehe) {
        public static final Zusatz KEINER = new Zusatz(null, null, null, Double.NaN);
    }

    /** Ein gebackenes Gitter. */
    public record Gebacken(Gitter gitter, int fein, float[] groundZ, Subgrid.Packung packung, double sekunden, long geschlossen, long gehoben) { }

    /** Das Feingelände eines Gitters mit Zusatz; zählt geschlossene und gehobene Pixel. */
    public static Subgrid.Feingelaende hoehen(Gelaende gel, Gitter g, int fein, Zusatz z, AtomicLong geschlossen, AtomicLong gehoben) {
        Subgrid.Feingelaende basis = gel.feingelaende(g, fein);
        double s = g.dx / fein;
        if (z == null || z.anteil == null && z.maske == null && z.mindest == null) return basis;
        // nur Mindesthöhe (G3, G4): die Regel des Kerns, nach der auch der Lauf G4 backt (Gebiet.auge, seit P4d)
        if (z.anteil == null && z.maske == null) return Gelaende.mindestens(basis, z.mindest, g, fein, gehoben);
        return (i0, j0, breite, hoehe, ziel) -> {
            basis.lies(i0, j0, breite, hoehe, ziel);
            IntStream.range(0, hoehe).parallel().forEach(v -> {
                double y = g.y0 + (j0 + v + 0.5) * s;
                long zu = 0, hoch = 0;
                Gelaende.Stufe.Leser la = z.anteil == null ? null : z.anteil.leser(), lm = z.maske == null ? null : z.maske.leser(),
                        lh = z.mindest == null ? null : z.mindest.leser();
                for (int u = 0; u < breite; u++) {
                    double x = g.x0 + (i0 + u + 0.5) * s;
                    int p = u + breite * v;
                    if (la != null && la.imPixel(x, y) >= 0.5f) { ziel[p] = z.hoehe; zu++; continue; }
                    if (lm != null && Schliessen.zu(lm, z.maske, x, y)) { ziel[p] = z.hoehe; zu++; continue; }
                    if (lh != null) {
                        float m = lh.imPixel(x, y);
                        if (!Float.isNaN(m) && m > ziel[p]) { ziel[p] = m; hoch++; }
                    }
                }
                if (geschlossen != null) geschlossen.addAndGet(zu);
                if (gehoben != null) gehoben.addAndGet(hoch);
            });
        };
    }

    /** Manning-n je Feinpixel aus den Rauheitsklassen: die Regel des Kerns ({@link Gelaende#rauheit}, seit P4d dort). */
    public static Subgrid.Feingelaende rauheit(Gelaende.Stufe r1, Gelaende.Stufe r10, double[] manning, Gitter g, int fein) {
        return Gelaende.rauheit(r1, r10, manning, g, fein);
    }

    /**
     * Manning-n für G0 und G1, die keine Rauheitsklassen haben (P5d): {@code rauheit.grob} aus dem Rezept, ohne den
     * Schlüssel {@link Subgrid#MANNING} wie vorher.
     */
    public static double manningGrob(Rezept rz) { return rz.hat("rauheit.grob") ? rz.zahl("rauheit.grob") : Subgrid.MANNING; }

    /**
     * Manning-n je Pixel aus der Tiefe unter NHN für einen festen Widerstandsbeiwert C_d (P5e, F35): Mit
     * C_d = g n² / h^(1/3) ist n = d^(1/6) √(C_d / g), d = max(−z, d_min) die Tiefe des Pixels bei mittlerem Wasserstand;
     * Pixel ohne Höhe (NaN) wie d_min. So wirkt in jeder Tiefe derselbe Beiwert, statt eines n, das im Flachen
     * rauer ist als in der Tiefe (n = 0,030 gibt C_d 0,0024 in 50 m, aber 0,0045 in 5 m).
     */
    public static Subgrid.Feingelaende rauheitAusTiefe(Subgrid.Feingelaende hoehen, double cd, double dMin) {
        double k = Math.sqrt(cd / 9.81);
        return (i0, j0, breite, hoehe, ziel) -> {
            hoehen.lies(i0, j0, breite, hoehe, ziel);
            for (int p = 0; p < breite * hoehe; p++) {
                double z = ziel[p], d = Double.isNaN(z) ? dMin : Math.max(-z, dMin);
                ziel[p] = Math.pow(d, 1.0 / 6) * k;
            }
        };
    }

    /** Ein Manning-n für alle Pixel. */
    public static Subgrid.Feingelaende rauheit(double n) { return (i0, j0, breite, hoehe, ziel) -> java.util.Arrays.fill(ziel, 0, breite * hoehe, n); }

    /** Die Klasse im Pixel, in dem der Punkt liegt (NaN ohne). */
    static float klasse(Gelaende.Stufe st, double x, double y) {
        if (st == null) return Float.NaN;
        return st.wert((int) Math.floor((x - st.suedwestOst) / st.zelle), (int) Math.floor((st.suedwestNord + st.kachelnNord * st.kachel() - y) / st.zelle));
    }

    /** Backt ein Gitter mit Subgrid. */
    public static Gebacken backe(Gitter g, int fein, Subgrid.Feingelaende h, Subgrid.Feingelaende r, AtomicLong zu, AtomicLong hoch) {
        long t0 = System.nanoTime();
        Subgrid sg = Subgrid.bauen(g, fein, h, r, Subgrid.STUFEN);
        float[] gz = groundZ(sg);
        Subgrid.Packung p = sg.packen();
        return new Gebacken(g, fein, gz, p, (System.nanoTime() - t0) / 1e9, zu == null ? 0 : zu.get(), hoch == null ? 0 : hoch.get());
    }

    /** Zellmittel des Feingeländes aus der Tabelle. */
    public static float[] groundZ(Subgrid sg) {
        float[] z = new float[sg.gitter.zellen()];
        for (int c = 0; c < z.length; c++) {
            double oben = sg.hoechste(c);
            z[c] = (float) (oben - sg.volumen(c, oben) / sg.zellflaeche(c / sg.gitter.nx));
        }
        return z;
    }

    /** Legt ein gebackenes Gitter ab: {@code ground.z.f32}, {@code subgrid.bin}. */
    public static void schreibe(Gebacken b, File ordner) throws IOException {
        Gebiet.schreibeF32(new File(ordner, "ground.z.f32"), b.groundZ);
        Gebiet.schreibePackung(new File(ordner, "subgrid.bin"), b.packung, b.gitter, b.fein);
    }

    // ------------------------------------------------------------------ G0

    /** G0: {@code ground.z} (Mittel über den Meeranteil) und {@code nass} (Meeranteil). */
    public record G0(Gitter gitter, float[] groundZ, float[] nass, int teilung, long teileAusStapel, double sekunden) { }

    /**
     * Backt G0 aus GEBCO (Pixel in {@code t} × {@code t} Teilen), in der Hülle von G1 aus der Stufe 100 m (bilinear);
     * Land nach Natural Earth. Die Ränder von G0 müssen auf Teilgrenzen liegen.
     */
    public static G0 g0(Gitter g0, Grob.Grad gebco, int t, byte[] landTeile, Gitter g1, Gelaende.Stufe hundert) {
        long t0 = System.nanoTime();
        double d = gebco.d / t;
        int tu = (int) Math.rint(g0.dx / d), tv = (int) Math.rint(g0.dy / d);
        double cu = (g0.x0 - gebco.west) / d, cv = (gebco.nord - g0.y1()) / d;
        if (Math.abs(tu * d - g0.dx) > 1e-9 || Math.abs(tv * d - g0.dy) > 1e-9 || Math.abs(cu - Math.rint(cu)) > 1e-6 || Math.abs(cv - Math.rint(cv)) > 1e-6)
            throw new IllegalArgumentException("G0: Zellen liegen nicht auf Teilen von GEBCO (" + t + " × " + t + ")");
        int u0 = (int) Math.rint(cu), v0 = (int) Math.rint(cv), W = g0.nx * tu, H = g0.ny * tv;
        if (u0 < 0 || v0 < 0 || (u0 + W) > gebco.w * t || (v0 + H) > gebco.h * t) throw new IllegalArgumentException("G0 reicht über GEBCO hinaus");
        // Grenzen der Hülle von G1 in Grad
        double[] gb = {1e9, 1e9, -1e9, -1e9};
        for (int k = 0; k <= 40; k++) {
            double a = k / 40.0;
            double[][] p = {Utm.rueckwaerts(g1.x0 + a * (g1.x1() - g1.x0), g1.y0, g1.zone), Utm.rueckwaerts(g1.x0 + a * (g1.x1() - g1.x0), g1.y1(), g1.zone),
                    Utm.rueckwaerts(g1.x0, g1.y0 + a * (g1.y1() - g1.y0), g1.zone), Utm.rueckwaerts(g1.x1(), g1.y0 + a * (g1.y1() - g1.y0), g1.zone)};
            for (double[] q : p) {
                gb[0] = Math.min(gb[0], q[0]);
                gb[1] = Math.min(gb[1], q[1]);
                gb[2] = Math.max(gb[2], q[0]);
                gb[3] = Math.max(gb[3], q[1]);
            }
        }
        double[] sMeer = new double[g0.zellen()], aMeer = new double[g0.zellen()], sAlle = new double[g0.zellen()], aAlle = new double[g0.zellen()];
        AtomicLong stapel = new AtomicLong();
        // je Zeile von G0 (von Nord) ein Faden: die Teilzeilen gehören nur zu ihr
        IntStream.range(0, g0.ny).parallel().forEach(jn -> {
            int j = g0.ny - 1 - jn;
            long st = 0;
            for (int pv = jn * tv; pv < (jn + 1) * tv; pv++) {
                double lat = gebco.nord - (v0 + pv + 0.5) * d;
                double a = Erde.zonenflaeche(lat - d / 2, lat + d / 2, d);
                int gr = (v0 + pv) / t;
                for (int pu = 0; pu < W; pu++) {
                    double lon = gebco.west + (u0 + pu + 0.5) * d;
                    float z = Float.NaN;
                    if (hundert != null && lon >= gb[0] && lon <= gb[2] && lat >= gb[1] && lat <= gb[3]) {
                        double[] e = Utm.vorwaerts(lon, lat, g1.zone);
                        if (g1.enthaelt(e[0], e[1])) {
                            z = hundert.bilinear(e[0], e[1]);
                            if (!Float.isNaN(z)) st++;
                        }
                    }
                    if (Float.isNaN(z)) z = gebco.v[gr * gebco.w + (u0 + pu) / t];
                    int c = g0.index(pu / tu, j);
                    sAlle[c] += a * z;
                    aAlle[c] += a;
                    if (landTeile[pv * W + pu] == 0) {
                        sMeer[c] += a * z;
                        aMeer[c] += a;
                    }
                }
            }
            stapel.addAndGet(st);
        });
        float[] gz = new float[g0.zellen()], nass = new float[g0.zellen()];
        for (int c = 0; c < gz.length; c++) {
            gz[c] = (float) (aMeer[c] > 0 ? sMeer[c] / aMeer[c] : sAlle[c] / aAlle[c]);
            nass[c] = (float) (aMeer[c] / aAlle[c]);
        }
        return new G0(g0, gz, nass, t, stapel.get(), (System.nanoTime() - t0) / 1e9);
    }

    /**
     * Feingelände von G0 für das Subgrid (seit P5, Werkbuch 12.6): {@code fein} × {@code fein} Pixel je Zelle, jedes das
     * Mittel seiner Teile von GEBCO ({@code t} × {@code t} Teile je Pixel, wie {@link #g0}); in der Hülle von G1 aus
     * der Stufe 100 m (bilinear). Geschlossen ({@code hoehe}) ist ein Teil auf Land nach Natural Earth und in G1 ein Teil,
     * dessen 100-m-Pixel den Anteil {@code anteil} ab ½ hat (wie G1). Die Zelle muss in ganze Pixel aus Teilen zerfallen.
     */
    public static Subgrid.Feingelaende g0Fein(Gitter g0, int fein, Grob.Grad gebco, int t, byte[] landTeile, Gitter g1, Gelaende.Stufe hundert,
            Gelaende.Stufe anteil, double hoehe) {
        double d = gebco.d / t;
        int tu = (int) Math.rint(g0.dx / d), tv = (int) Math.rint(g0.dy / d);
        if (tu % fein != 0 || tv % fein != 0) throw new IllegalArgumentException("G0: " + tu + " × " + tv + " Teile je Zelle zerfallen nicht in " + fein + " Pixel");
        int pw = tu / fein, ph = tv / fein;
        int u0 = (int) Math.rint((g0.x0 - gebco.west) / d), v0 = (int) Math.rint((gebco.nord - g0.y1()) / d), W = g0.nx * tu, H = g0.ny * tv;
        double[] gb = huelle(g1);
        return (i0, j0, breite, hoehe0, ziel) -> IntStream.range(0, hoehe0).parallel().forEach(v -> {
            Gelaende.Stufe.Leser la = anteil == null ? null : anteil.leser(), lh = hundert == null ? null : hundert.leser();
            int J = j0 + v;
            for (int u = 0; u < breite; u++) {
                int I = i0 + u, p = u + breite * v;
                if (I < 0 || J < 0 || I >= g0.nx * fein || J >= g0.ny * fein) { ziel[p] = Double.NaN; continue; }
                double s = 0;
                for (int b = 0; b < ph; b++) {
                    int pv = H - 1 - (J * ph + b);
                    double lat = gebco.nord - (v0 + pv + 0.5) * d;
                    for (int a = 0; a < pw; a++) {
                        int pu = I * pw + a;
                        double lon = gebco.west + (u0 + pu + 0.5) * d;
                        if (landTeile[pv * W + pu] != 0) { s += hoehe; continue; }
                        float z = Float.NaN;
                        if (lon >= gb[0] && lon <= gb[2] && lat >= gb[1] && lat <= gb[3]) {
                            double[] e = Utm.vorwaerts(lon, lat, g1.zone);
                            if (g1.enthaelt(e[0], e[1])) {
                                if (la != null && la.imPixel(e[0], e[1]) >= 0.5f) { s += hoehe; continue; }
                                if (lh != null) z = lh.bilinear(e[0], e[1]);
                            }
                        }
                        if (Float.isNaN(z)) z = gebco.v[((v0 + pv) / t) * gebco.w + (u0 + pu) / t];
                        s += z;
                    }
                }
                ziel[p] = s / (pw * ph);
            }
        });
    }

    /** Grenzen der Hülle von G1 in Grad {West, Süd, Ost, Nord}. */
    static double[] huelle(Gitter g1) {
        double[] gb = {1e9, 1e9, -1e9, -1e9};
        for (int k = 0; k <= 40; k++) {
            double a = k / 40.0;
            double[][] p = {Utm.rueckwaerts(g1.x0 + a * (g1.x1() - g1.x0), g1.y0, g1.zone), Utm.rueckwaerts(g1.x0 + a * (g1.x1() - g1.x0), g1.y1(), g1.zone),
                    Utm.rueckwaerts(g1.x0, g1.y0 + a * (g1.y1() - g1.y0), g1.zone), Utm.rueckwaerts(g1.x1(), g1.y0 + a * (g1.y1() - g1.y0), g1.zone)};
            for (double[] q : p) {
                gb[0] = Math.min(gb[0], q[0]);
                gb[1] = Math.min(gb[1], q[1]);
                gb[2] = Math.max(gb[2], q[0]);
                gb[3] = Math.max(gb[3], q[1]);
            }
        }
        return gb;
    }

    static String f(String format, Object... w) { return String.format(Locale.GERMAN, format, w); }
}
