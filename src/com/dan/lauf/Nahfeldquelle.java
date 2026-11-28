package com.dan.lauf;

import com.dan.core.archiv.Archiv;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Utm;
import com.dan.core.szene.Zonen;
import com.dan.physics.vegetation.Salzwiese;
import com.dan.physics.waves.nah.Nahfeld;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Das Nahfeld aus den Daten eines Szenarios (Seegang Werkbuch, S2b Teil 3): baut ein {@link Nahfeld} um einen Ort und
 * füllt seine Eingaben zur Szenenzeit t (s ab dem Start des Archivs).
 * <ul>
 * <li><b>Grund</b> aus dem Feingelände des Gebiets ({@code gebiet/gelaende}), mit der Zellgröße des Nahfelds abgetastet
 * (das Mittel der passenden Stufe).</li>
 * <li><b>Spiegel und Strömung</b> aus dem Archiv: {@code water.eta} von G3 (sonst G2), {@code water.u}, {@code .v} aus
 * {@code hydro_G3}; bilinear über die nassen Zellen (h > 2 cm), in der Zeit linear. Wo keine nasse Zelle trägt, ist das
 * Nahfeld trocken.</li>
 * <li><b>Wind</b> aus {@code wind} (G2) an der Mitte, um die Meridiankonvergenz ins Gitter gedreht.</li>
 * <li><b>Rand und Bindung</b> aus {@code seegang_G3w} (außerhalb aus {@code seegang_G2w}): je Zelle die Partitionen
 * Windsee und Dünung als zwei JONSWAP ({@link Nahfeld#partitionen}); an einem Ort bilinear über die vier Zellen, in der
 * Zeit linear zwischen den Ständen. Die Bindung führt je Stützstelle auf dieses Spektrum ({@link #spektralBinden}, B20;
 * sonst auf sein m₀), aber nur im tiefen, gleichmäßigen Wasser (SF17 A, {@link Nahfeld#bindungTiefe},
 * {@link Nahfeld#bindungStreuung}); auf dem Watt, in Prielen und an Kanten rechnet das Nahfeld frei.</li>
 * <li><b>Pflanzen</b> aus den Zonen der Salzwiese ({@code vegetation/zonen}) mit Höhe und Bedeckung nach
 * {@link Salzwiese} zum Tag des Jahres; C_D b_v N_v = {@link #pflanzenDichte} · Bedeckung [G].</li>
 * </ul>
 * Das Gitter liegt auf den Linien von G3w (100 m), damit ein Block der Bindung eine Zelle von G3w ist.
 */
public final class Nahfeldquelle implements AutoCloseable {
    public static final String VERSION = "0.1";

    /** Eine Stufe des Nahfelds: Zellgröße (m), Zellen je Seite. */
    public record Stufe(String name, double zelle, int n) { }

    /** Stufen [G]: grob 50 m × 100 (5 km), fein 10 m × 100 (1 km). */
    public static final Stufe GROB = new Stufe("grob", 50, 100), FEIN = new Stufe("fein", 10, 100);

    /** Bänder und Richtungen [G, SF15 A]. */
    public int nb = 8, nd = 32;
    public double fMin = 0.06, fMax = 0.6;
    /** C_D b_v N_v bei voller Bedeckung (1/m) [G]: Halme von rund 2–3 mm, rund 2 000 je m², C_D rund 1. */
    public double pflanzenDichte = 5;
    /**
     * Zeit der Bindung (s) [G, SF18 A]: kurz genug, dass im tiefen Wasser die Form des Spektrums von G3w gilt; ohne
     * Vierwellen-Wechselwirkung staute sich sonst die Energie im höchsten Band (B24).
     */
    public double bindungZeit = 120;
    /** Bindung an das ganze Spektrum von G3w (B20), sonst nur an m₀. */
    public boolean spektralBinden = true;
    /** Linien des groben Felds (m), auf die das Gitter gelegt wird. */
    public static final double RASTER = 100;

    public final Path archiv;
    public final Instant start;
    private final Gelaende gelaende;
    private final Zonen zonen;
    private final Salzwiese salzwiese = new Salzwiese();
    private final Katalog kat = Katalog.grundstufe();
    private final Archiv g2, g3, strom, wind, see2, see3;
    private final Feld eta2, h2, eta3, h3, u3, v3, u10, v10;
    private double zeitWasser = Double.NaN;

    public Nahfeldquelle(File gebiet, Path archiv) throws IOException {
        this.archiv = archiv;
        gelaende = Gelaende.ordner(new File(gebiet, "gelaende"));
        Zonen z = null;
        try { z = Zonen.lies(gebiet); } catch (IOException e) { z = null; }
        zonen = z;
        g2 = oeffne(archiv.resolve("G2"));
        g3 = oeffne(archiv.resolve("G3"));
        strom = oeffne(archiv.resolve("hydro_G3"));
        wind = oeffne(archiv.resolve("wind"));
        see2 = oeffne(archiv.resolve("seegang_G2w"));
        see3 = oeffne(archiv.resolve("seegang_G3w"));
        if (g2 == null && g3 == null) throw new IOException(archiv + ": weder G2 noch G3");
        if (see2 == null && see3 == null) throw new IOException(archiv + ": kein Nachlauf des Seegangs (seegang_G2w, seegang_G3w; SeegangNachlauf)");
        start = (g3 != null ? g3 : g2).lauf.start;
        eta2 = g2 != null ? g2.feld("water.eta", kat) : null;
        h2 = g2 != null ? g2.feld("water.h", kat) : null;
        eta3 = g3 != null ? g3.feld("water.eta", kat) : null;
        h3 = g3 != null ? g3.feld("water.h", kat) : null;
        u3 = strom != null ? strom.feld("water.u", kat) : null;
        v3 = strom != null ? strom.feld("water.v", kat) : null;
        u10 = wind != null ? wind.feld("air.u10", kat) : null;
        v10 = wind != null ? wind.feld("air.v10", kat) : null;
    }

    private static Archiv oeffne(Path p) throws IOException { return Files.isRegularFile(p.resolve("lauf.txt")) ? Archiv.oeffnen(p) : null; }

    /** Das Gitter des groben Seegangs (G3w, sonst G2w). */
    public Gitter seegangGitter() { return (see3 != null ? see3 : see2).lauf.gitter.values().iterator().next(); }

    // ------------------------------------------------------------------ Bauen

    /** Ein Nahfeld der Stufe s mit der Mitte nahe (ost, nord), auf die Linien von {@link #RASTER} gelegt, mit allen Eingaben zur Zeit t. */
    public Nahfeld baue(Stufe s, double ost, double nord, double t) throws IOException {
        Nahfeld n = new Nahfeld(s.n, s.n, s.zelle, nb, fMin, fMax, nd);
        n.x0 = Math.floor((ost - 0.5 * s.n * s.zelle) / RASTER) * RASTER;
        n.y0 = Math.floor((nord - 0.5 * s.n * s.zelle) / RASTER) * RASTER;
        n.bindungBlock = Math.max(1, (int) Math.round(RASTER / s.zelle));
        n.bindungZeit = bindungZeit;
        grund(n);
        eingaben(n, t);
        return n;
    }

    /** Der Grund aus dem Feingelände (nach einem Verschieben neu). */
    public void grund(Nahfeld n) {
        float[] z = new float[n.nc];
        gelaende.fuelle(n.x(0), n.y(0), n.dx, n.nx, n.ny, z);
        for (int c = 0; c < n.nc; c++) n.grund[c] = Float.isFinite(z[c]) ? z[c] : -50;
    }

    /** Spiegel, Strömung, Wind, Pflanzen, Rand und Bindung zur Zeit t; danach {@link Nahfeld#vorbereiten()}. */
    public void eingaben(Nahfeld n, double t) throws IOException {
        wasser(t);
        for (int j = 0; j < n.ny; j++)
            for (int i = 0; i < n.nx; i++) {
                int c = j * n.nx + i;
                double[] w = wasserBei(n.x(i), n.y(j));
                n.spiegel[c] = Double.isNaN(w[0]) ? n.grund[c] - 1 : w[0];
                n.u[c] = w[1];
                n.v[c] = w[2];
            }
        // Wind an der Mitte, gedreht ins Gitter
        java.util.Arrays.fill(n.windU, 0);
        java.util.Arrays.fill(n.windV, 0);
        if (wind != null) {
            wind.lies("air.u10", t, u10);
            wind.lies("air.v10", t, v10);
            double xm = n.x0 + 0.5 * n.nx * n.dx, ym = n.y0 + 0.5 * n.ny * n.dx;
            double ue = bilinear(u10, null, xm, ym)[0], vn = bilinear(v10, null, xm, ym)[0];
            if (Double.isFinite(ue) && Double.isFinite(vn)) {
                Gitter g = u10.gitter;
                double[] ll = g.grad(xm, ym);
                double gam = Math.toRadians(Utm.faktoren(ll[0], ll[1], g.zone)[0]);
                double co = Math.cos(gam), si = Math.sin(gam);
                java.util.Arrays.fill(n.windU, ue * co - vn * si);
                java.util.Arrays.fill(n.windV, vn * co + ue * si);
            }
        }
        // Pflanzen
        if (zonen != null) {
            int tag = start.plusMillis(Math.round(t * 1000)).atZone(ZoneOffset.UTC).getDayOfYear();
            n.pflanzen = new double[n.nc];
            n.halm = new double[n.nc];
            boolean irgend = false;
            for (int c = 0; c < n.nc; c++) {
                int zone = zonen.bei(n.x(c % n.nx), n.y(c / n.nx))[0];
                if (zone <= 0) continue;
                boolean weide = zone == Zonen.FENNE;
                int z = Math.min(3, zone);
                n.pflanzen[c] = pflanzenDichte * salzwiese.deckung(z, tag);
                n.halm[c] = salzwiese.hoehe(z, weide, tag);
                irgend |= n.pflanzen[c] > 0;
            }
            if (!irgend) { n.pflanzen = null; n.halm = null; }
        }
        n.vorbereiten();
        // Rand und Bindung
        Nahfeld.Ziel z = ziel(n, t);
        n.rand = n.randAus(z);
        int m = n.nb * n.nd;
        n.bindungM0 = new double[n.nc];
        n.bindungSpektrum = spektralBinden ? new double[n.nn] : null;
        double[] e = new double[m];
        for (int c = 0; c < n.nc; c++) {
            z.varianz(n.x(c % n.nx), n.y(c / n.nx), e);
            double s = 0;
            for (double x : e) s += x;
            n.bindungM0[c] = s;
            if (spektralBinden) System.arraycopy(e, 0, n.bindungSpektrum, c * m, m);
        }
    }

    // ------------------------------------------------------------------ Wasser

    private void wasser(double t) throws IOException {
        if (t == zeitWasser) return;
        if (g3 != null) { g3.lies("water.eta", t, eta3); g3.lies("water.h", t, h3); }
        if (g2 != null) { g2.lies("water.eta", t, eta2); g2.lies("water.h", t, h2); }
        if (strom != null) { strom.lies("water.u", t, u3); strom.lies("water.v", t, v3); }
        zeitWasser = t;
    }

    /** {Spiegel, U, V} am Ort: aus G3, wo es den Ort deckt, sonst aus G2 (ohne Strömung); NaN, wo keine nasse Zelle trägt. */
    double[] wasserBei(double x, double y) {
        if (eta3 != null && innen(eta3.gitter, x, y)) {
            double[] e = bilinear(eta3, h3, x, y);
            if (Double.isNaN(e[0])) return new double[] {Double.NaN, 0, 0};
            double u = u3 != null ? bilinear(u3, h3, x, y)[0] : 0, v = v3 != null ? bilinear(v3, h3, x, y)[0] : 0;
            return new double[] {e[0], Double.isFinite(u) ? u : 0, Double.isFinite(v) ? v : 0};
        }
        if (eta2 != null && innen(eta2.gitter, x, y)) return new double[] {bilinear(eta2, h2, x, y)[0], 0, 0};
        return new double[] {Double.NaN, 0, 0};
    }

    private static boolean innen(Gitter g, double x, double y) { return x >= g.x(0) && x <= g.x(g.nx - 1) && y >= g.y(0) && y <= g.y(g.ny - 1); }

    /**
     * Bilinear über die vier Zellmitten um (x, y); mit {@code tiefe} nur über nasse Zellen (h > 2 cm), die mindestens ein
     * Viertel des Gewichts tragen müssen (wie das Feingelände). {Wert}, NaN ohne Träger.
     */
    static double[] bilinear(Feld f, Feld tiefe, double x, double y) {
        Gitter g = f.gitter;
        double fi = (x - g.x0) / g.dx - 0.5, fj = (y - g.y0) / g.dy - 0.5;
        int i0 = (int) Math.floor(fi), j0 = (int) Math.floor(fj);
        double a = fi - i0, b = fj - j0, s = 0, w = 0;
        for (int dj = 0; dj < 2; dj++)
            for (int di = 0; di < 2; di++) {
                int i = Math.max(0, Math.min(g.nx - 1, i0 + di)), j = Math.max(0, Math.min(g.ny - 1, j0 + dj));
                int c = i + g.nx * j;
                double gw = (di == 0 ? 1 - a : a) * (dj == 0 ? 1 - b : b);
                if (gw <= 0) continue;
                if (tiefe != null && !(tiefe.hol(c) > 0.02)) continue;
                double v = f.hol(c * f.schichten * f.komponenten);
                if (!Double.isFinite(v)) continue;
                s += gw * v;
                w += gw;
            }
        return new double[] {w >= 0.25 ? s / w : Double.NaN};
    }

    // ------------------------------------------------------------------ Seegang

    /**
     * Das Spektrum je Ort zur Zeit t (Varianz je Band und Richtung des Musters): aus G3w, wo es den Ort deckt, sonst
     * aus G2w; bilinear über die vier Zellen, in der Zeit linear zwischen den Ständen. Seit S3c-6 (SF31 A, Seegang
     * Werkbuch 21/22) mit einem Übergang am Rand von G3w über {@link com.dan.core.szene.Seegangsnetz#RAND_MISCHUNG}, vorher
     * sprangen Rand und Bindung dort (B49b); seit S3c-7 (SF33 A, Werkbuch 23/24) nach
     * {@link com.dan.core.szene.Seegangsnetz#RAND_ART}: außen (Vorgabe) in G3w nur G3w, außerhalb von dessen Randwert in
     * G2w; innen (S3c-6) im Streifen am Rand von G3w mit G2w gemischt; aus ohne Übergang. Die Spektren der Zellen werden
     * je Ziel einmal gerechnet und gehalten.
     */
    public Nahfeld.Ziel ziel(Nahfeld muster, double t) throws IOException {
        Seegang s3 = see3 != null ? new Seegang(see3, muster, t) : null, s2 = see2 != null ? new Seegang(see2, muster, t) : null;
        final double rand = com.dan.core.szene.Seegangsnetz.RAND_MISCHUNG;
        final int art = com.dan.core.szene.Seegangsnetz.RAND_ART;
        return (x, y, e) -> {
            if (s3 != null && s3.deckt(x, y)) {
                s3.varianz(x, y, e);
                double r = s3.randAbstand(x, y);
                if (art == com.dan.core.szene.Seegangsnetz.RAND_INNEN && r < rand && s2 != null && s2.deckt(x, y)) {
                    double[] e2 = new double[e.length];
                    s2.varianz(x, y, e2);
                    double a = r / rand;
                    for (int k = 0; k < e.length; k++) e[k] = e2[k] + a * (e[k] - e2[k]);
                }
                return;
            }
            (s2 != null ? s2 : s3).varianz(x, y, e);
            if (art == com.dan.core.szene.Seegangsnetz.RAND_AUSSEN && s3 != null && s2 != null) {
                double d = s3.aussenAbstand(x, y);
                if (d < rand) {
                    double[] e3 = new double[e.length];
                    s3.varianz(x, y, e3);                                  // außerhalb: die Randwerte von G3w
                    double a = d / rand;
                    for (int k = 0; k < e.length; k++) e[k] = e3[k] + a * (e[k] - e3[k]);
                }
            }
        };
    }

    /** Ein Gitter des Seegangs zu einer Zeit: Partitionen der zwei Stände um t, Spektren je Zelle auf Abruf. */
    private final class Seegang {
        final Archiv a;
        final Nahfeld m;
        final Gitter g;
        final int lo, hi;
        final double w;
        final Feld[] sea = new Feld[2], swell = new Feld[2];
        final Map<Long, double[]> spektren = new java.util.concurrent.ConcurrentHashMap<>();

        Seegang(Archiv a, Nahfeld m, double t) throws IOException {
            this.a = a; this.m = m;
            int n = a.staende(), l = 0, h = n - 1;
            if (t <= a.zeit(0)) h = 0;
            else if (t >= a.zeit(n - 1)) l = n - 1;
            else while (h - l > 1) { int k = (l + h) >>> 1; if (a.zeit(k) <= t) l = k; else h = k; }
            lo = l; hi = h;
            w = lo == hi ? 0 : (t - a.zeit(lo)) / (a.zeit(hi) - a.zeit(lo));
            for (int k = 0; k < 2; k++) {
                sea[k] = a.feld("waves.sea", kat);
                swell[k] = a.feld("waves.swell", kat);
                a.lies("waves.sea", k == 0 ? lo : hi, sea[k]);
                a.lies("waves.swell", k == 0 ? lo : hi, swell[k]);
            }
            g = sea[0].gitter;
        }

        boolean deckt(double x, double y) { return innen(g, x, y); }

        /** Seit S3c-7: Abstand des Orts von dem, was das Gitter deckt (m, euklidisch, 0 innen). */
        double aussenAbstand(double x, double y) {
            double ax = Math.max(0, Math.max(g.x(0) - x, x - g.x(g.nx - 1))), ay = Math.max(0, Math.max(g.y(0) - y, y - g.y(g.ny - 1)));
            return Math.hypot(ax, ay);
        }

        /** Abstand des Orts vom Rand dessen, was das Gitter deckt (die äußeren Zellmitten, {@link #deckt}), in m. */
        double randAbstand(double x, double y) {
            return Math.min(Math.min(x - g.x(0), g.x(g.nx - 1) - x), Math.min(y - g.y(0), g.y(g.ny - 1) - y));
        }

        double[] spektrum(int k, int c) {
            return spektren.computeIfAbsent(((long) k << 32) | c, key -> {
                double[] p1 = new double[4], p2 = new double[4];
                for (int q = 0; q < 4; q++) { p1[q] = sea[k].f[4 * c + q]; p2[q] = swell[k].f[4 * c + q]; }
                return m.partitionen(p1, p2);
            });
        }

        void varianz(double x, double y, double[] e) {
            java.util.Arrays.fill(e, 0);
            double fi = (x - g.x0) / g.dx - 0.5, fj = (y - g.y0) / g.dy - 0.5;
            int i0 = (int) Math.floor(fi), j0 = (int) Math.floor(fj);
            double ax = fi - i0, by = fj - j0;
            for (int dj = 0; dj < 2; dj++)
                for (int di = 0; di < 2; di++) {
                    int i = Math.max(0, Math.min(g.nx - 1, i0 + di)), j = Math.max(0, Math.min(g.ny - 1, j0 + dj));
                    double gw = (di == 0 ? 1 - ax : ax) * (dj == 0 ? 1 - by : by);
                    if (gw <= 0) continue;
                    int c = i + g.nx * j;
                    for (int k = 0; k < 2; k++) {
                        double gk = gw * (k == 0 ? 1 - w : w);
                        if (gk <= 0) continue;
                        double[] s = spektrum(k, c);
                        for (int x2 = 0; x2 < e.length; x2++) e[x2] += gk * s[x2];
                    }
                }
        }
    }

    /** Die Wassertiefe von G3 (sonst G2) in der Zelle, die (x, y) enthält, zur Zeit t (m); NaN außerhalb oder trocken. */
    public double tiefeGrob(double x, double y, double t) throws IOException {
        wasser(t);
        for (Feld h : new Feld[] {h3, h2}) {
            if (h == null || !innen(h.gitter, x, y)) continue;
            Gitter g = h.gitter;
            int i = (int) Math.floor((x - g.x0) / g.dx), j = (int) Math.floor((y - g.y0) / g.dy);
            if (i < 0 || j < 0 || i >= g.nx || j >= g.ny) continue;
            double v = h.hol(i + g.nx * j);
            return v > 0.02 ? v : Double.NaN;
        }
        return Double.NaN;
    }

    /** m₀ des Spektrums von G3w (in den Bändern des Musters) im Mittel über die Zelle von G3w, die (x, y) enthält; NaN außerhalb. */
    public double m0Grob(Nahfeld muster, double x, double y, double t) throws IOException {
        if (see3 == null) return Double.NaN;
        Seegang s = new Seegang(see3, muster, t);
        if (!s.g.enthaelt(x, y)) return Double.NaN;
        int c = s.g.i(x) + s.g.nx * s.g.j(y);
        double m = 0;
        for (int k = 0; k < 2; k++) {
            double gk = k == 0 ? 1 - s.w : s.w;
            if (gk <= 0) continue;
            for (double v : s.spektrum(k, c)) m += gk * v;
        }
        return m;
    }

    /**
     * Seit Seegang Werkbuch 75 (SF78 A): Wind 10 m über dem Wasser am Ort (UTM) zur Zeit t, ins Gitter gedreht wie in
     * {@link #eingaben}: {u (Ost im Gitter), v (Nord im Gitter)} in m/s; null ohne Wind.
     */
    public double[] windBei(double ost, double nord, double t) throws IOException {
        if (wind == null) return null;
        wind.lies("air.u10", t, u10);
        wind.lies("air.v10", t, v10);
        double ue = bilinear(u10, null, ost, nord)[0], vn = bilinear(v10, null, ost, nord)[0];
        if (!Double.isFinite(ue) || !Double.isFinite(vn)) return null;
        double[] ll = u10.gitter.grad(ost, nord);
        double gam = Math.toRadians(Utm.faktoren(ll[0], ll[1], u10.gitter.zone)[0]), co = Math.cos(gam), si = Math.sin(gam);
        return new double[] {ue * co - vn * si, vn * co + ue * si};
    }

    /**
     * Seit Seegang Werkbuch 75 (SF78 A): der Weg über Wasser vom Ort gegen die Richtung (ex, ey) (Einheitsvektor, also
     * luvwärts: gegen den Wind) bis zum ersten Ort mit weniger als hMin Tiefe oder bis {@code bis}, in Schritten von
     * {@code schritt} (m): {Fetch (m), mittlere Tiefe auf dem Weg (m), ob das Ende trocken war (1) oder die Grenze (0)}.
     * Spiegel aus G3/G2 (wie das Nahfeld), Grund aus dem Feingelände.
     */
    public double[] fetch(double ost, double nord, double ex, double ey, double t, double schritt, double bis, double hMin) throws IOException {
        wasser(t);
        float[] z = new float[1];
        double summe = 0;
        int n = 0;
        for (double d = 0; d <= bis; d += schritt) {
            double x = ost + d * ex, y = nord + d * ey;
            double sp = wasserBei(x, y)[0];
            gelaende.fuelle(x, y, schritt, 1, 1, z);
            double h = Double.isNaN(sp) || !Float.isFinite(z[0]) ? 0 : sp - z[0];
            if (!(h >= hMin)) return new double[] {d, n > 0 ? summe / n : 0, 1};
            summe += h;
            n++;
        }
        return new double[] {bis, n > 0 ? summe / n : 0, 0};
    }

    /**
     * Seit Seegang Werkbuch 75 (SF78 A): Wachstum der Windsee in Wasser endlicher Tiefe nach Young und Verhagen (1996, Lake
     * George): ε = 0,00364 [a tanh(0,00313 χ^0,57 / a)]^1,74 mit a = tanh(0,493 δ^0,75); ν = 0,133 [b tanh(0,0005215 χ^0,73 /
     * b)]^−0,37 mit b = tanh(0,331 δ^1,01); ε = g² E / U⁴, ν = f_p U / g, χ = g F / U², δ = g d / U². {H_m0 (m), Tp (s)};
     * dazu der Grenzwert für unbegrenzten Fetch über {@code grenze}.
     */
    public static double[] youngVerhagen(double u10, double fetch, double tiefe) {
        double g = 9.81, u2 = u10 * u10, chi = g * fetch / u2, del = g * tiefe / u2;
        double a = Math.tanh(0.493 * Math.pow(del, 0.75)), b = Math.tanh(0.331 * Math.pow(del, 1.01));
        double eps = 0.00364 * Math.pow(a * Math.tanh(0.00313 * Math.pow(chi, 0.57) / a), 1.74);
        double nu = 0.133 * Math.pow(b * Math.tanh(0.0005215 * Math.pow(chi, 0.73) / b), -0.37);
        double e = eps * u2 * u2 / (g * g);
        return new double[] {4 * Math.sqrt(e), u10 / (g * nu)};
    }

    @Override public void close() throws IOException {
        for (Archiv a : new Archiv[] {g2, g3, strom, wind, see2, see3}) if (a != null) a.close();
    }

    /** Für Lambdas: IOException als ungeprüft. */
    static <T> T ohne(java.util.concurrent.Callable<T> c) {
        try { return c.call(); } catch (IOException e) { throw new UncheckedIOException(e); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
