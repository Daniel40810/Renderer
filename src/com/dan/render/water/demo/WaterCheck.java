package com.dan.render.water.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Zeichner;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Wasseroptik;
import com.dan.core.szene.Spiegel;
import com.dan.core.szene.Spiegelbild;
import com.dan.render.water.Wasserbild;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung von render.water ohne Fenster (P6d, Werkbuch HalligHooge Teil A, 12.7, Prüfsteine 5, 6, 7, dazu 3 und 9 für
 * das Wasser). Teile: 1 Daten (Stände aus dem Gelände der Physik), 2 Wasserlinie gleich Physik (Prüfstein 5, auf dem
 * Prozessor nach der Regel des Shaders, η_Bild auf der Karte gegen Java), 3 Zeit (Prüfstein 6), 4 Optik (Prüfstein 7),
 * 5 dicht (Prüfstein 3 mit Wasser), 6 Aufräumen.
 * <p>
 * <b>Stände.</b> render.water kennt hydro nicht; die Stände sind ruhende Wasserspiegel mit Gefälle über dem Gelände der
 * Physik: nass ist, was vom Rand von G3 über Kanten ohne Bauwerk und Zellen unter dem Spiegel erreicht wird und nach der
 * Tabelle der Zelle mehr als 0,1 mm im Mittel trägt (wie hydro); dort ist η der Spiegel, sonst die tiefste Pixelhöhe.
 * Gelände und Himmel sind ein Ersatz mit bekannten Werten (render.water kennt render.terrain und render.air nicht).
 * <p>
 * {@code --gebiet <ordner>} (Gelände, Vorgabe {@code D:\Daten\HalligHooge\gebiet}), {@code --wasser <ordner>} (Auszug von
 * {@link WasserAuszug}, Vorgabe {@code <gebiet>\..\_p6\wasser}). Endet mit „Alles in Ordnung.“ und schreibt das Attest
 * nach {@code docs/status/P6_Wasser.md}.
 */
public final class WaterCheck {
    private static final Attest A = new Attest("com.dan.render.water", Wasserbild.VERSION);
    /** Auge auf der Hanswarft (UTM 32), wie TerrainCheck. */
    static final double OST = 470_900, NORD = 6_046_615;
    /** Fenster der Wasserlinie: O 470–472 km, N 6 045–6 047 km (Süden von Hooge mit Sommerdeich und Watt). */
    static final double F_OST = 470_000, F_NORD = 6_045_000;
    static final int F_ZELLEN = 100;
    static final double L_HIMMEL = 4000;
    /** Gefälle des Spiegels nach Ost [G] (2 mm je Zelle von G3, Werkbuch 12.7, Befund 5). */
    static final double GEFAELLE = 1e-4;
    static final double H_NASS = 1e-4;

    public static void main(String[] args) throws Exception {
        boolean attest = !List.of(args).contains("--ohne-attest");
        String gebiet = "D:\\Daten\\HalligHooge\\gebiet", wasser = null;
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals("--gebiet")) gebiet = args[i + 1];
            if (args[i].equals("--wasser")) wasser = args[i + 1];
        }
        File gb = new File(gebiet), wa = wasser != null ? new File(wasser) : new File(gb.getAbsoluteFile().getParentFile(), "_p6" + File.separator + "wasser");
        System.out.println("WaterCheck – com.dan.render.water " + Wasserbild.VERSION + " (Werkbuch HalligHooge Teil A, P6d)");
        A.info("Gebiet", gb.getPath() + "; Auszug des Wassers " + wa.getPath());
        // seit P7g: die Formeln hier gelten an der Normalen der aufgelösten Kräuselung; die wirksame Fläche prüfen FlaecheCheck und ReferenzCheck
        Wasserbild.WIRKSAM = false;
        A.info("Wirksame Fläche (P7g)", "aus: geprüft werden Fresnel und Spiegel an der Normalen; die wirksame Fläche prüfen FlaecheCheck (Tafel) und ReferenzCheck (Bild)");
        saumProbe();
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("WaterCheck", 768L << 20);
            pruefen(g, konto, gb, wa);
            A.teil("6 Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                String erste = g.meldungen.isEmpty() ? "" : "; die erste Meldung: " + g.meldungen.get(0).replace('\n', ' ');
                A.pruefe(g.validierungsFehler == 0, "Prüfstein 9 (Wasser): Validierungsschicht " + g.schicht, g.validierungsFehler + " Fehler" + erste, "0 Fehler");
            } else A.info("Validierungsschicht", "nicht eingeschaltet (-Dcore.gpu.validierung=true)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P6_Wasser.md"), "P6d – WaterCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Daten

    /** G3 ganz: tiefste Pixelhöhe und Bauwerke; das Fenster mit seiner Packung. */
    static final class Daten {
        Gitter g3, gw, g2;
        float[] zmin3, zmin2;
        boolean[] west, sued, ring;
        Gebiet.Bauten bauten;
        Subgrid.Packung pw;
        int i0, j0, fein;
        long ringZellen;
    }

    static Daten daten(File wa) throws Exception {
        Daten d = new Daten();
        Gebiet ge = Gebiet.lies(new File(wa, "fenster"));
        d.gw = ge.gitter("G3");
        d.fein = ge.eintrag("G3").fein();
        d.pw = ge.packung("G3");
        d.g3 = Gitter.utm("G3", 32, 454_000, 6_025_000, 20, 1200, 1600);
        d.g2 = Gitter.utm("G2", 32, 428_000, 6_011_000, 200, 370, 430);
        d.zmin3 = Gebiet.liesF32(new File(wa, "G3.zmin.f32"), d.g3.zellen());
        d.zmin2 = Gebiet.liesF32(new File(wa, "G2.zmin.f32"), d.g2.zellen());
        d.bauten = Gebiet.Bauten.lies(new File(wa, "G3.bauwerke.txt"));
        d.i0 = (int) Math.round((d.gw.x0 - d.g3.x0) / d.g3.dx);
        d.j0 = (int) Math.round((d.gw.y0 - d.g3.y0) / d.g3.dy);
        int n = d.g3.zellen(), nx = d.g3.nx, ny = d.g3.ny;
        d.west = new boolean[n];
        d.sued = new boolean[n];
        for (Gebiet.Bauten.Kante k : d.bauten.kanten()) {
            if (k.querX() && k.i() > 0 && k.i() < nx && k.j() >= 0 && k.j() < ny) d.west[k.i() + nx * k.j()] = true;
            if (!k.querX() && k.j() > 0 && k.j() < ny && k.i() >= 0 && k.i() < nx) d.sued[k.i() + nx * k.j()] = true;
        }
        // Ringe: vom Rand über Kanten ohne Bauwerk nicht erreichbar
        boolean[] drin = flut(d, null);
        d.ring = new boolean[n];
        for (int c = 0; c < n; c++) if (!drin[c]) { d.ring[c] = true; d.ringZellen++; }
        return d;
    }

    /** Vom Rand von G3 über Kanten ohne Bauwerk erreichbar; mit Spiegel nur über Zellen, deren tiefste Pixelhöhe darunter liegt. */
    static boolean[] flut(Daten d, double[] spiegel) {
        int nx = d.g3.nx, ny = d.g3.ny;
        boolean[] e = new boolean[nx * ny];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < nx; i++) for (int j : new int[] {0, ny - 1}) start(d, spiegel, e, q, i + nx * j);
        for (int j = 0; j < ny; j++) for (int i : new int[] {0, nx - 1}) start(d, spiegel, e, q, i + nx * j);
        while (!q.isEmpty()) {
            int c = q.poll(), i = c % nx, j = c / nx;
            if (i > 0 && !d.west[c]) start(d, spiegel, e, q, c - 1);
            if (i + 1 < nx && !d.west[c + 1]) start(d, spiegel, e, q, c + 1);
            if (j > 0 && !d.sued[c]) start(d, spiegel, e, q, c - nx);
            if (j + 1 < ny && !d.sued[c + nx]) start(d, spiegel, e, q, c + nx);
        }
        return e;
    }

    private static void start(Daten d, double[] spiegel, boolean[] e, ArrayDeque<Integer> q, int c) {
        if (e[c] || spiegel != null && !(d.zmin3[c] < spiegel[c])) return;
        e[c] = true;
        q.add(c);
    }

    /** Ein Stand: Spiegel η0 + Gefälle · (Ost − Auge) über G3; {eta3, nass3, eta2, nass2} als Objekte. */
    static Object[] stand(Daten d, double eta0) {
        int nx = d.g3.nx, n = d.g3.zellen();
        double[] sp = new double[n];
        for (int c = 0; c < n; c++) sp[c] = eta0 + GEFAELLE * (d.g3.x0 + (c % nx + 0.5) * d.g3.dx - OST);
        boolean[] erreicht = flut(d, sp);
        float[] eta = new float[n];
        boolean[] nass = new boolean[n];
        for (int c = 0; c < n; c++) {
            int i = c % nx - d.i0, j = c / nx - d.j0;
            boolean w = erreicht[c];
            if (w && i >= 0 && j >= 0 && i < d.gw.nx && j < d.gw.ny) w = d.pw.volumen(i + d.gw.nx * j, (float) sp[c]) > H_NASS * d.pw.zellflaeche();
            nass[c] = w;
            eta[c] = w ? (float) sp[c] : d.zmin3[c];
        }
        int n2 = d.g2.zellen();
        float[] eta2 = new float[n2];
        boolean[] nass2 = new boolean[n2];
        for (int c = 0; c < n2; c++) {
            double s = eta0 + GEFAELLE * (d.g2.x0 + (c % d.g2.nx + 0.5) * d.g2.dx - OST);
            nass2[c] = d.zmin2[c] < s;
            eta2[c] = nass2[c] ? (float) s : d.zmin2[c];
        }
        return new Object[] {eta, nass, eta2, nass2};
    }

    /** Der Stand im Fenster (Zellen des Fensters). */
    static float[] fensterF(Daten d, float[] voll) {
        float[] w = new float[d.gw.zellen()];
        for (int j = 0; j < d.gw.ny; j++) for (int i = 0; i < d.gw.nx; i++) w[i + d.gw.nx * j] = voll[d.i0 + i + d.g3.nx * (d.j0 + j)];
        return w;
    }

    static boolean[] fensterB(Daten d, boolean[] voll) {
        boolean[] w = new boolean[d.gw.zellen()];
        for (int j = 0; j < d.gw.ny; j++) for (int i = 0; i < d.gw.nx; i++) w[i + d.gw.nx * j] = voll[d.i0 + i + d.g3.nx * (d.j0 + j)];
        return w;
    }

    static void setze(Daten d, Spiegel s3, Spiegel s2, double t, Object[] st) {
        s3.stand(t, fensterF(d, (float[]) st[0]), fensterB(d, (boolean[]) st[1]));
        s2.stand(t, (float[]) st[2], (boolean[]) st[3]);
    }

    /** Benetzter Anteil der Zelle beim Wasserstand η aus der Packung, wie Hydro.benetzt. */
    static double anteil(Subgrid.Packung p, int c, double eta) {
        int S = p.stufen(), o = c * 3 * S;
        double b = p.basis()[c];
        if (!(eta > b + Float.float16ToFloat(p.tabelle()[o]))) return 0;
        int l = S - 1;
        while (b + Float.float16ToFloat(p.tabelle()[o + l]) > eta) l--;
        if (l == S - 1) return 1;
        double z0 = b + Float.float16ToFloat(p.tabelle()[o + l]), z1 = b + Float.float16ToFloat(p.tabelle()[o + l + 1]);
        double a0 = Float.float16ToFloat(p.tabelle()[o + 2 * S + l]), a1 = Float.float16ToFloat(p.tabelle()[o + 2 * S + l + 1]);
        return a0 + (a1 - a0) * (eta - z0) / (z1 - z0);
    }

    // ------------------------------------------------------------------ Ersatz

    /** Ersatz für den Himmel unter den Namen von render.air (wie TerrainCheck). */
    static final class Ersatz implements AutoCloseable {
        final Bild durch, ansicht;
        final Puffer werte;
        final float[] w = new float[60];

        Ersatz(Geraet g, Konto k) {
            durch = new Bild(g, k, "Ersatz Durchlässigkeit", 256, 64, Bild.RGBA32F);
            float[] eins = new float[256 * 64 * 4];
            Arrays.fill(eins, 1f);
            durch.schreibe(eins);
            ansicht = new Bild(g, k, "Ersatz Ansicht", 256, 128, Bild.RGBA32F);
            float[] a = new float[256 * 128 * 4];
            for (int i = 0; i < 256 * 128; i++) {
                a[4 * i] = a[4 * i + 1] = a[4 * i + 2] = (float) L_HIMMEL;
                a[4 * i + 3] = 1;
            }
            ansicht.schreibe(a);
            werte = Puffer.speicher(g, k, "Ersatz Werte", 60 * 4);
        }

        void sonne(double hGrad, double azGrad, double e, double vb, double auge) {
            double h = Math.toRadians(hGrad), az = Math.toRadians(azGrad);
            Arrays.fill(w, 0);
            w[0] = 1e-5f; w[1] = 1e-5f; w[2] = 0.08f; w[3] = 0.85f;
            w[4] = (float) (Math.cos(h) * Math.sin(az)); w[5] = (float) Math.sin(h); w[6] = (float) (-Math.cos(h) * Math.cos(az)); w[7] = (float) Math.max(1, auge);
            w[8] = w[9] = w[10] = (float) e; w[11] = (float) vb;
            w[12] = 0; w[13] = -1; w[14] = 0;
            w[52] = w[53] = w[54] = w[56] = w[57] = w[58] = 1;                                         // Farbe der Sonne: weiß (die Prüfung gibt E je Kanal vor)
            werte.schreibe(w);
        }

        void ablegen(Bildziel z) {
            z.geteilt.put("Himmel/Durchlaessigkeit", durch);
            z.geteilt.put("Himmel/Ansicht", ansicht);
            z.puffer.put("Himmel/Werte", werte);
        }

        @Override public void close() {
            durch.close();
            ansicht.close();
            werte.close();
        }
    }

    // ------------------------------------------------------------------ Prüfung

    static void pruefen(Geraet g, Konto konto, File gebiet, File wa) throws Exception {
        A.teil("1 Daten");
        long t0 = System.nanoTime();
        Daten d = daten(wa);
        Gelaende gel = Gelaende.ordner(new File(gebiet, "gelaende"));
        Gelaende.Stufe mh = Gebiet.mindesthoehe(gebiet);          // seit P6e: derselbe Stapel wie hydro, mit der Mindesthöhe der Deiche
        if (mh != null) gel = gel.mitMindesthoehe(mh);
        A.info("Gitter", f("%s; Fenster %s (ab Zelle %d, %d), fein %d; %s", d.g3, d.gw, d.i0, d.j0, d.fein, d.g2));
        A.info("Bauwerke von G3", f("%d Kanten, %d Ringe; %d Zellen in Ringen (vom Rand ohne Bauwerk nicht erreichbar)", d.bauten.kanten().size(), d.bauten.ringe().size(), d.ringZellen));
        double[] pegel = {0.0, 0.4, 1.9};
        Object[][] st = new Object[pegel.length][];
        for (int k = 0; k < pegel.length; k++) st[k] = stand(d, pegel[k]);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < pegel.length; k++) {
            boolean[] n3 = (boolean[]) st[k][1], nw = fensterB(d, n3);
            int a = 0, b = 0;
            for (boolean x : n3) if (x) a++;
            for (boolean x : nw) if (x) b++;
            sb.append(f("η₀ %.1f m: %d Zellen von G3 nass, im Fenster %d; ", pegel[k], a, b));
        }
        A.info("Stände (ruhender Spiegel mit Gefälle 1e-4 nach Ost)", sb + f("gebaut in %.1f s", (System.nanoTime() - t0) / 1e9));

        Spiegel s3 = new Spiegel(d.gw), s2 = new Spiegel(d.g2);
        int bw = s3.bauwerke(d.bauten, d.i0, d.j0);
        int sw = s3.schwellen(d.pw, d.gw, 0, 0);
        A.info("Bauwerke im Fenster", bw + " Kanten; Schwellen aus der Packung (F47 A) an " + sw + " Kanten");

        try (Wasserbild w = new Wasserbild(g, konto); Ersatz ers = new Ersatz(g, konto)) {
            w.spiegel(s3);
            w.spiegel(s2);
            w.kraeuseln = false;
            Bild hdr = new Bild(g, konto, "HDR", 480, 270, Bild.RGBA16F), tiefe = new Bild(g, konto, "Tiefe", 480, 270, Bild.D32F);
            Kernel kp = Kernel.lade(g, "/com/dan/render/water/demo/probe.comp", 16, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER,
                    Kernel.Bindung.PUFFER, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
            Abtaster at = new Abtaster(g, false, false);
            int np = (F_ZELLEN * 20) * (F_ZELLEN * 20);
            Puffer orte = Puffer.speicher(g, konto, "Orte", 8L * np), aus = Puffer.speicher(g, konto, "Aus", 8L * np);
            try {
                Bildziel z = new Bildziel(hdr, tiefe);
                ers.ablegen(z);
                z.vorbelichtung = 1e-3;
                z.zeit = Instant.parse("2026-10-25T12:00:00Z");
                double augeH = gel.hoehe(OST, NORD, 1) + 1.7;
                z.blick = new Blick(OST, NORD, augeH, 270, -3, 50).groesse(480, 270);
                ers.sonne(25, 190, 80_000, z.vorbelichtung, augeH);
                // Orte: alle Pixelmitten des Fensters relativ zum Auge (in float wie die Karte)
                float[] o = new float[2 * np];
                int W = F_ZELLEN * 20;
                for (int r = 0; r < W; r++)
                    for (int c = 0; c < W; c++) {
                        o[2 * (r * W + c)] = (float) (F_OST + c + 0.5 - OST);
                        o[2 * (r * W + c) + 1] = (float) (NORD - (F_NORD + r + 0.5));
                    }
                orte.schreibe(o);
                float[] zf = new float[np], zeile = new float[W];
                for (int r = 0; r < W; r++) {
                    gel.fuelle(F_OST + 0.5, F_NORD + r + 0.5, 1, W, 1, zeile);
                    System.arraycopy(zeile, 0, zf, r * W, W);
                }

                // -------------------------------------------------------- 2 Wasserlinie
                A.teil("2 Wasserlinie gleich Physik");
                for (int k = 0; k < 2; k++) {
                    setze(d, s3, s2, 0, st[k]);
                    w.zeit = 0;
                    bild(g, w, z, false);
                    float[] karte = probe(g, w, kp, at, ers, orte, aus, np);
                    long anders = 0;
                    double maxD = 0;
                    float[] cpu = new float[np];
                    for (int i = 0; i < np; i++) {
                        cpu[i] = w.etaBild(o[2 * i], o[2 * i + 1], null);
                        float gk = karte[2 * i] == Wasserbild.kein() ? Float.NaN : karte[2 * i];
                        if (Float.floatToIntBits(gk) != Float.floatToIntBits(cpu[i])) {
                            anders++;
                            if (!Float.isNaN(gk) && !Float.isNaN(cpu[i])) maxD = Math.max(maxD, Math.abs(gk - cpu[i]));
                            else maxD = Double.POSITIVE_INFINITY;
                        }
                    }
                    A.pruefe(anders == 0, f("η_Bild auf der Karte gegen Java, η₀ %.1f m: alle Pixelmitten des Fensters", pegel[k]),
                            f("%d Orte, %d anders, größte Abweichung %.1e m", np, anders, maxD), "bitgleich [G]");
                    double[] r = wasserlinie(d, (float[]) st[k][0], (boolean[]) st[k][1], cpu, zf);
                    A.pruefe(r[1] <= 0.01 && r[2] <= 0.05, f("Prüfstein 5, η₀ %.1f m: benetzter Anteil im Bild gegen die Pixel unter η der Zelle je teilweise nasser Zelle (F46 A)", pegel[k]),
                            f("%.0f Zellen; im Mittel %.2f %%, 95 %% der Zellen ≤ %.2f %%, größte %.1f %%; Bild %.1f %% gegen %.1f %% der Fläche; Tabelle gegen dieselben Pixel im Mittel %.2f %%",
                                    r[0], 100 * r[1], 100 * r[2], 100 * r[3], 100 * r[4], 100 * r[5], 100 * r[6]), "≤ 1 %, ≤ 5 % [G]");
                }
                // Ring: Landunter außen, der Ring trocken
                setze(d, s3, s2, 0, st[2]);
                w.zeit = 0;
                bild(g, w, z, false);
                long[] rg = ring(d, w, o, zf, np, (boolean[]) st[2][1]);
                A.pruefe(rg[0] == 0, "Prüfstein 5, η₀ 1,9 m (Landunter): Wasser über die Deichkante in einen trockenen Ring",
                        f("%d Pixel (Ringzellen im Fenster: %d, davon trocken %d; Pixel außen nass %d)", rg[0], rg[1], rg[2], rg[3]), "0 Pixel");

                // -------------------------------------------------------- 3 Zeit
                A.teil("3 Zeit");
                setze(d, s3, s2, 0, st[0]);
                setze(d, s3, s2, 300, st[1]);
                double[] tz = {0, 37.5, 150, 299.9, 300};
                double schritte = 0;
                long verglichen = 0;
                for (double tt : tz) {
                    w.zeit = tt;
                    bild(g, w, z, false);
                    float[] karte = probe(g, w, kp, at, ers, orte, aus, np);
                    for (int i = 0; i < np; i += 7) {
                        float c = w.etaBild(o[2 * i], o[2 * i + 1], null), gk = karte[2 * i];
                        if (Float.isNaN(c) || gk == Wasserbild.kein()) continue;
                        schritte = Math.max(schritte, Math.abs(gk - c) / Math.ulp(c));
                        verglichen++;
                    }
                }
                A.pruefe(schritte <= 1, "Prüfstein 6: η_Bild zur Zeit t auf der Karte gegen die lineare Interpolation der zwei Stände (Java, Bus.lies)",
                        f("%d Orte zu 5 Zeiten zwischen den Ständen 0 und 300 s; größte Abweichung %.0f float-Schritte", verglichen, schritte), "±1 float-Schritt");
                // Linear in der Zeit: Mitte gegen das Mittel der Enden, wo alle vier Nachbarn in beiden Ständen nass sind
                w.zeit = 0;
                bild(g, w, z, false);
                float[] ea = probe(g, w, kp, at, ers, orte, aus, np);
                w.zeit = 300;
                bild(g, w, z, false);
                float[] eb = probe(g, w, kp, at, ers, orte, aus, np);
                w.zeit = 150;
                bild(g, w, z, false);
                float[] em = probe(g, w, kp, at, ers, orte, aus, np);
                double lin = 0;
                for (int i = 0; i < np; i += 3) {
                    if (ea[2 * i] == Wasserbild.kein() || eb[2 * i] == Wasserbild.kein() || em[2 * i] == Wasserbild.kein()) continue;
                    lin = Math.max(lin, Math.abs(em[2 * i] - 0.5 * (ea[2 * i] + eb[2 * i])));
                }
                A.info("Mitte der Zeit gegen das Mittel der Stände (Orte, die in beiden Ständen nass sind)", f("größte Abweichung %.2e m (Nachbarn, die nur in einem Stand nass sind, dürfen abweichen)", lin));
                // Wechsel des Intervalls: kurz vor dem neuen Stand gegen kurz danach
                w.zeit = 300;
                bild(g, w, z, false);
                float[] vor = probe(g, w, kp, at, ers, orte, aus, np);
                setze(d, s3, s2, 600, st[0]);
                bild(g, w, z, false);
                float[] nach = probe(g, w, kp, at, ers, orte, aus, np);
                double sprung = 0;
                long nv = 0;
                for (int i = 0; i < np; i++) {
                    boolean a = vor[2 * i] == Wasserbild.kein(), b = nach[2 * i] == Wasserbild.kein();
                    if (a != b) { sprung = Double.POSITIVE_INFINITY; continue; }
                    if (a) continue;
                    sprung = Math.max(sprung, Math.abs(vor[2 * i] - nach[2 * i]));
                    nv++;
                }
                A.pruefe(sprung <= 1e-6, "Prüfstein 6: Wechsel des Intervalls (Stand bei 600 s kommt an, t = 300 s)",
                        f("%d Orte, Sprung höchstens %.1e m", nv, sprung), "≤ 1e-6 m");

                // -------------------------------------------------------- 4 Optik
                A.teil("4 Optik");
                double[] winkel = {0, 53, 85};
                float[] cs = new float[2 * winkel.length];
                for (int i = 0; i < winkel.length; i++) cs[2 * i] = (float) Math.cos(Math.toRadians(winkel[i]));
                orte.schreibe(cs);
                float[] op = probeOptik(g, w, kp, at, ers, orte, aus, winkel.length);
                orte.schreibe(o);
                double fmax = 0;
                StringBuilder fs = new StringBuilder();
                for (int i = 0; i < winkel.length; i++) {
                    double soll = Wasseroptik.fresnel(cs[2 * i], w.optik.n);
                    fmax = Math.max(fmax, Math.abs(op[8 * i] - soll));
                    fs.append(f("%.0f°: %.6f (Formel %.6f); ", winkel[i], op[8 * i], soll));
                }
                A.pruefe(fmax <= 1e-4, "Prüfstein 7: Fresnel bei 0°, 53° und 85° gegen die Formel (n = 1,34, unpolarisiert)", fs + f("größte Abweichung %.1e", fmax), "≤ 1e-4");
                double tmax = 0;
                StringBuilder ts = new StringBuilder();
                for (int c = 0; c < 3; c++) {
                    double soll = Math.exp(-Wasseroptik.POPE_FRY[c]);
                    tmax = Math.max(tmax, Math.abs(op[1 + c] - soll) / soll);
                    ts.append(f("%.0f nm %.5f; ", Wasseroptik.LAMBDA[c], op[1 + c]));
                }
                A.pruefe(tmax <= 1e-3, "Prüfstein 7: Transmission durch 1 m reines Wasser gegen Pope und Fry (1997)", ts + f("relativ %.1e", tmax), "relativ ≤ 1e-3");
                double[] K = w.optik.daempfung();
                A.info("Dämpfung a + b_b bei 30 mg/l Schwebstoff", f("R %.3f, G %.3f, B %.3f je m (Grund in 1 m Tiefe senkrecht: %.0f %%, %.0f %%, %.0f %%)", K[0], K[1], K[2],
                        100 * Math.exp(-2 * K[0]), 100 * Math.exp(-2 * K[1]), 100 * Math.exp(-2 * K[2])));
                double[] bb = w.optik.bb();
                double[] rr = new double[3];
                for (int c = 0; c < 3; c++) rr[c] = bb[c] / (4 * K[c]) * (1 - Wasseroptik.fresnel(1, w.optik.n)) / (w.optik.n * w.optik.n);
                A.info("Tiefes Wasser, Sonne im Zenit, senkrecht gesehen: Reflexion der Säule π L / E", f("R %.3f, G %.3f, B %.3f (Einfachstreuung)", rr[0], rr[1], rr[2]));
                double sigma2 = Wasseroptik.coxMunk(5), aufg = 0;
                for (double[] x : Wasseroptik.wellen(5, 0)) aufg += 0.5 * x[3] * x[3];
                A.pruefe(Math.abs(aufg - sigma2) <= 1e-12, "Kräuselung: Neigungsvarianz der 48 Wellen gleich Cox und Munk (U₁₀ = 5 m/s)", f("%.6f gegen %.6f", aufg, sigma2), "gleich");

                // -------------------------------------------------------- 5 dicht
                A.teil("5 Dicht");
                dicht(g, konto, w, ers, z, augeH);
            } finally {
                orte.close();
                aus.close();
                kp.close();
                at.close();
                hdr.close();
                tiefe.close();
            }
        }
    }

    /** Ein Bild: löschen, (Ersatzgelände), Wasser vorbereiten und zeichnen. */
    static void bild(Geraet g, Wasserbild w, Bildziel z, boolean zeichnen) {
        Ablauf a = new Ablauf(g, g.grafik);
        a.zeichne("löschen", new Bild[] {z.hdr}, new float[][] {{0, 0, 0, 0}}, z.tiefe, 0f, null, c -> { });
        w.vorbereiten(a, z);
        if (zeichnen) w.zeichnen(a, z);
        g.grafik.einmal(c -> a.aufnehmen(c, null));
    }

    static float[] probe(Geraet g, Wasserbild w, Kernel kp, Abtaster at, Ersatz ers, Puffer orte, Puffer aus, int n) {
        Ablauf a = new Ablauf(g, g.grafik);
        ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        p.putInt(0, 0).putInt(4, n);
        a.rechne("Probe", kp, kp.satz(w.kopf(), w.staende(), w.arten(), ers.werte, at.an(ers.durch), at.an(ers.ansicht), aus, orte), p, Kernel.gruppen(n, 64), 1, 1,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, w.kopf(), w.staende(), w.arten(), ers.werte, orte), Ablauf.textur(Ablauf.RECHNEN, ers.durch, ers.ansicht), Ablauf.schreibt(aus)));
        g.grafik.einmal(c -> a.aufnehmen(c, null));
        return aus.floats(2 * n);
    }

    static float[] probeOptik(Geraet g, Wasserbild w, Kernel kp, Abtaster at, Ersatz ers, Puffer orte, Puffer aus, int n) {
        Ablauf a = new Ablauf(g, g.grafik);
        ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        p.putInt(0, 1).putInt(4, n);
        a.rechne("Probe Optik", kp, kp.satz(w.kopf(), w.staende(), w.arten(), ers.werte, at.an(ers.durch), at.an(ers.ansicht), aus, orte), p, 1, 1, 1,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, w.kopf(), w.staende(), w.arten(), ers.werte, orte), Ablauf.textur(Ablauf.RECHNEN, ers.durch, ers.ansicht), Ablauf.schreibt(aus)));
        g.grafik.einmal(c -> a.aufnehmen(c, null));
        return aus.floats(8 * n);
    }

    /**
     * Prüfstein 5: je teilweise nasser Zelle des Fensters der Anteil der Pixel mit η_Bild über dem Feingelände gegen
     * die Pixel unter η der Zelle (F46 A). {Zellen, Mittel, 95 %, größte, Bild gesamt, Pixel gesamt, Tabelle gegen die Pixel im Mittel} (Anteile).
     */
    static double[] wasserlinie(Daten d, float[] eta3, boolean[] nass3, float[] etaBild, float[] zf) {
        int W = F_ZELLEN * 20, nz = d.gw.zellen();
        double[] ab = new double[nz];
        int n = 0;
        double summe = 0, bildG = 0, tabG = 0, tabAb = 0;
        for (int j = 0; j < d.gw.ny; j++)
            for (int i = 0; i < d.gw.nx; i++) {
                int cv = d.i0 + i + d.g3.nx * (d.j0 + j), cw = i + d.gw.nx * j;
                if (!nass3[cv]) continue;
                // seit F46 A: Maßstab sind dieselben Pixel unter η der Zelle (ebener Spiegel in der Zelle); die Tabelle zur Auskunft
                int nassPx = 0, eben = 0;
                for (int r = 0; r < 20; r++)
                    for (int c = 0; c < 20; c++) {
                        int k = (j * 20 + r) * W + i * 20 + c;
                        if (!Float.isNaN(etaBild[k]) && etaBild[k] > zf[k]) nassPx++;
                        if (zf[k] < eta3[cv]) eben++;
                    }
                double soll = eben / 400.0;
                if (!(soll > 0 && soll < 1)) continue;
                double ist = nassPx / 400.0;
                ab[n++] = Math.abs(ist - soll);
                tabAb += Math.abs(anteil(d.pw, cw, eta3[cv]) - soll);
                summe += Math.abs(ist - soll);
                bildG += ist;
                tabG += soll;
            }
        if (n == 0) return new double[] {0, 0, 0, 0, 0, 0, 0};
        double[] s = Arrays.copyOf(ab, n);
        Arrays.sort(s);
        return new double[] {n, summe / n, s[(int) Math.min(n - 1, Math.ceil(0.95 * n) - 1)], s[n - 1], bildG / n, tabG / n, tabAb / n};
    }

    /** Wasser in Ringzellen des Fensters: {Pixel mit Wasser in trockenen Ringzellen, Ringzellen, davon trocken, nasse Pixel außen}. */
    static long[] ring(Daten d, Wasserbild w, float[] o, float[] zf, int np, boolean[] nass3) {
        int W = F_ZELLEN * 20;
        long wasser = 0, rz = 0, tr = 0, aussen = 0;
        boolean[] gezaehlt = new boolean[d.gw.zellen()];
        for (int k = 0; k < np; k++) {
            int r = k / W, c = k % W, i = c / 20, j = r / 20, cv = d.i0 + i + d.g3.nx * (d.j0 + j), cw = i + d.gw.nx * j;
            float e = w.etaBild(o[2 * k], o[2 * k + 1], null);
            boolean nassPx = !Float.isNaN(e) && e > zf[k];
            if (d.ring[cv]) {
                if (!gezaehlt[cw]) {
                    gezaehlt[cw] = true;
                    rz++;
                    if (!nass3[cv]) tr++;
                }
                if (nassPx && !nass3[cv]) wasser++;
            } else if (nassPx) aussen++;
        }
        return new long[] {wasser, rz, tr, aussen};
    }

    /**
     * Seit C1b (Werkbuch HalligHooge 137, A52): der Saum einer trockenen Zelle, auf dem Prozessor (dieselbe Regel rechnet
     * {@code wasser.glsl}; die Karte gegen Java prüft Teil 2 bitgleich über das Fenster). Ein Gitter 10 × 10 zu 100 m,
     * überall nass (0,5 m) bis auf die Zelle (5, 5) mit −3 m; das Auge in deren Mitte.
     */
    static void saumProbe() {
        A.teil("0 Saum (C1b, A52)");
        Gitter gp = Gitter.utm("Saum", 32, OST - 550, NORD - 550, 100, 10, 10);
        int c0 = 5 + 10 * 5;
        float[][] orte = {{40, 0}, {-40, 0}, {0, -40}, {0, 40}, {0, 0}, {45, -45}};
        String[] fall = {"offen", "Kanten über dem Spiegel", "Bauwerke", "Saum aus"};
        boolean alt = Spiegelbild.SAUM;
        try {
            for (int f = 0; f < fall.length; f++) {
                Spiegel sp = new Spiegel(gp);
                float[] eta = new float[gp.zellen()];
                boolean[] nass = new boolean[gp.zellen()];
                Arrays.fill(eta, 0.5f);
                Arrays.fill(nass, true);
                eta[c0] = -3f;
                nass[c0] = false;
                sp.stand(0, eta, nass);
                synchronized (sp) {
                    if (f == 1) {
                        sp.schwelleW[c0] = sp.schwelleW[c0 + 1] = sp.schwelleS[c0] = sp.schwelleS[c0 + 10] = 1.0f;
                    } else if (f == 2) {
                        sp.art[c0] |= Spiegel.WEST | Spiegel.SUED;
                        sp.art[c0 + 1] |= Spiegel.WEST;
                        sp.art[c0 + 10] |= Spiegel.SUED;
                    }
                }
                Spiegelbild.SAUM = f != 3;
                Spiegelbild sb = new Spiegelbild(List.of(sp), OST, NORD, 0);
                int wasser = 0;
                StringBuilder w = new StringBuilder();
                for (float[] o : orte) {
                    float e = sb.etaBild(o[0], o[1], null);
                    if (!Float.isNaN(e)) wasser++;
                    w.append(Float.isNaN(e) ? "–" : f("%.2f", e)).append(' ');
                }
                // außerhalb der trockenen Zelle bleibt alles wie vorher
                float aussen = sb.etaBild(140, 0, null);
                boolean ok = f == 0 ? wasser == orte.length : wasser == 0;
                A.pruefe(ok && aussen == 0.5f, "Saum: trockene Zelle in nassem Wasser, " + fall[f], f("η an 6 Orten: %s; 140 m östlich %.2f", w.toString().strip(), aussen),
                        f == 0 ? "6 mal 0,50; außen 0,50" : "kein Wasser; außen 0,50");
            }
        } finally {
            Spiegelbild.SAUM = alt;
        }
    }

    /**
     * Prüfstein 3 mit Wasser: Ersatzgelände 3 m unter NHN, ein Gitter überall nass bis auf ein trockenes Viereck. Seit C1b
     * ohne Saum (A52): Das Viereck liegt 3 m unter dem Wasser ohne Kanten; mit Saum zeigte es Wasser, wie es soll.
     */
    static void dicht(Geraet g, Konto konto, Wasserbild w0, Ersatz ers, Bildziel z0, double augeH) throws Exception {
        boolean saumAlt = Spiegelbild.SAUM;
        Spiegelbild.SAUM = false;
        try {
            dichtOhneSaum(g, konto, w0, ers, z0, augeH);
        } finally {
            Spiegelbild.SAUM = saumAlt;
        }
    }

    static void dichtOhneSaum(Geraet g, Konto konto, Wasserbild w0, Ersatz ers, Bildziel z0, double augeH) throws Exception {
        try (Wasserbild w = new Wasserbild(g, konto)) {
            Gitter gp = Gitter.utm("Probe", 32, OST - 20_000, NORD - 20_000, 100, 400, 400);
            Spiegel sp = new Spiegel(gp);
            float[] eta = new float[gp.zellen()];
            boolean[] nass = new boolean[gp.zellen()];
            // trocken: Zellen 190..199 × 230..239 (1 km nördlich, 1 km westlich des Auges), dort η die Pixelhöhe −3 m
            for (int c = 0; c < eta.length; c++) {
                int i = c % 400, j = c / 400;
                boolean tr = i >= 190 && i < 200 && j >= 205 && j < 215;
                nass[c] = !tr;
                eta[c] = tr ? -3f : 0.5f;
            }
            sp.stand(0, eta, nass);
            w.spiegel(sp);
            Zeichner.Art art = new Zeichner.Art();
            art.farben = new int[] {Bild.RGBA16F};
            Zeichner boden = Zeichner.lade(g, "/com/dan/render/water/wasser.vert", "/com/dan/render/water/demo/boden.frag", 16, null, art,
                    Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR);
            Abtaster at = new Abtaster(g, false, false);
            try {
                Object[][] ansichten = {
                        {"Hanswarft nach Westen", new Blick(OST, NORD, augeH, 270, -1, 50)},
                        {"Hanswarft nach Nordwesten über das trockene Viereck", new Blick(OST, NORD, augeH, 315, -2, 50)},
                        {"Vogelblick aus 300 m", new Blick(OST - 500, NORD - 1500, 300, 330, -30, 60)}};
                for (Object[] v : ansichten) {
                    Blick b = ((Blick) v[1]).groesse(480, 270);
                    z0.blick = b;
                    Ablauf a = new Ablauf(g, g.grafik);
                    a.zeichne("löschen", new Bild[] {z0.hdr}, new float[][] {{0, 0, 0, 0}}, z0.tiefe, 0f, null, c -> { });
                    w.vorbereiten(a, z0);
                    ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
                    p.putFloat(0, -3f).putFloat(4, 0f).putFloat(8, 1000f * (float) z0.vorbelichtung).putFloat(12, 0);
                    long satz = boden.satz(w.kopf(), w.staende(), w.arten(), ers.werte, at.an(ers.durch), at.an(ers.ansicht));
                    a.zeichne("Ersatzgelände", new Bild[] {z0.hdr}, null, z0.tiefe, null,
                            Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, w.kopf(), w.staende(), w.arten(), ers.werte), Ablauf.textur(Ablauf.ZEICHNEN, ers.durch, ers.ansicht)),
                            c -> boden.zeichne(c, satz, p, 3, 1));
                    w.zeichnen(a, z0);
                    g.grafik.einmal(c -> a.aufnehmen(c, null));
                    long[] r = dichtZaehlen(b, z0.hdr, z0.tiefe, w.kruemmung, gp);
                    A.pruefe(r[0] == 0 && r[1] == 0 && r[2] == 0, "Prüfstein 3 (Wasser): " + v[0] + " – Himmel unter der Kimm des Wassers; NaN; Wasser im trockenen Viereck",
                            f("%d; %d; %d Bildpunkte (Wasser auf %d, Grund im Viereck auf %d von %d)", r[0], r[1], r[2], r[3], r[4], 480 * 270), "0; 0; 0");
                }
                // Spiegelung des Geländes (F45 A): eine dunkle Wand 200 m westlich im glatten Wasser ohne Erdkrümmung
                w.kraeuseln = false;
                w.kruemmung = 0;
                Blick b = new Blick(OST, NORD, augeH, 270, -8, 50).groesse(480, 270);
                z0.blick = b;
                Ablauf a = new Ablauf(g, g.grafik);
                a.zeichne("löschen", new Bild[] {z0.hdr}, new float[][] {{0, 0, 0, 0}}, z0.tiefe, 0f, null, c -> { });
                w.vorbereiten(a, z0);
                ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
                float lb = 1000f * (float) z0.vorbelichtung;
                p.putFloat(0, -3f).putFloat(4, 0f).putFloat(8, lb).putFloat(12, -200f);
                long satz = boden.satz(w.kopf(), w.staende(), w.arten(), ers.werte, at.an(ers.durch), at.an(ers.ansicht));
                a.zeichne("Ersatzgelände mit Wand", new Bild[] {z0.hdr}, null, z0.tiefe, null,
                        Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, w.kopf(), w.staende(), w.arten(), ers.werte), Ablauf.textur(Ablauf.ZEICHNEN, ers.durch, ers.ansicht)),
                        c -> boden.zeichne(c, satz, p, 3, 1));
                w.zeichnen(a, z0);
                g.grafik.einmal(c -> a.aufnehmen(c, null));
                float[] mit = z0.hdr.lies();
                w.spiegeln = false;
                Ablauf a2 = new Ablauf(g, g.grafik);
                a2.zeichne("löschen", new Bild[] {z0.hdr}, new float[][] {{0, 0, 0, 0}}, z0.tiefe, 0f, null, c -> { });
                w.vorbereiten(a2, z0);
                long satz2 = boden.satz(w.kopf(), w.staende(), w.arten(), ers.werte, at.an(ers.durch), at.an(ers.ansicht));
                a2.zeichne("Ersatzgelände mit Wand", new Bild[] {z0.hdr}, null, z0.tiefe, null,
                        Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, w.kopf(), w.staende(), w.arten(), ers.werte), Ablauf.textur(Ablauf.ZEICHNEN, ers.durch, ers.ansicht)),
                        c -> boden.zeichne(c, satz2, p, 3, 1));
                w.zeichnen(a2, z0);
                g.grafik.einmal(c -> a2.aufnehmen(c, null));
                double[] spw = spiegelung(b, mit, z0.hdr.lies(), 0.5, -200, 5);
                A.pruefe(Math.abs(spw[0] - spw[1]) <= 2 && Math.abs(spw[2] - spw[3]) <= 2, "Spiegelung des Geländes (F45 A): Spiegelbild einer dunklen Wand (200 m, bis 5 m NHN) im glatten Wasser, mittlere Spalte",
                        f("Unterkante Zeile %.0f gegen %.0f nach der Spiegelebene; %.0f gegen %.0f Zeilen", spw[0], spw[1], spw[2], spw[3]), "±2 Bildpunkte [G]");
                w.spiegeln = true;
            } finally {
                boden.close();
                at.close();
            }
        }
    }

    /**
     * Mittlere Spalte, mit und ohne Spiegelung des Geländes: {letzte Zeile mit dunklerem Spiegelbild + 1, Zeile, in der der
     * gespiegelte Strahl über die Wand geht, Zeilen mit dunklerem Spiegelbild, Zeilen nach der Spiegelebene}.
     */
    static double[] spiegelung(Blick b, float[] mit, float[] ohne, double eta, double x, double oben) {
        int c = b.breite / 2, letzte = -1, n = 0, soll = -1, nSoll = 0;
        for (int y = 0; y < b.hoehe_px; y++) {
            double[] s = b.strahl(c + 0.5, y + 0.5);
            if (s[0] >= 0) continue;
            double yl = b.hoehe + s[1] * (x / s[0]);
            if (yl >= eta) continue;                          // trifft die Wand über dem Wasser
            if (2 * eta - yl <= oben) nSoll++;
            else if (soll < 0) soll = y;
            int i = 4 * (y * b.breite + c) + 1;
            if (mit[i] < 0.9f * ohne[i]) { letzte = y; n++; }
        }
        return new double[] {letzte + 1, soll, n, nSoll};
    }

    /**
     * {Himmel unter der Kimm des Spiegels 0,5 m im Gitter, NaN, Wasser im trockenen Viereck (eine Zelle Rand), Wasser,
     * Grund im Viereck}. Wasser ist ein Bildpunkt, dessen Tiefe zum Spiegel passt; Treffer auf den Spiegel in double.
     */
    static long[] dichtZaehlen(Blick b, Bild hdr, Bild tiefe, double R, Gitter gp) {
        float[] dd = tiefe.lies(), h = hdr.lies();
        double k = 1 / (2 * R), px = Math.toRadians(b.bildwinkel) / b.hoehe_px;
        long himmel = 0, nan = 0, falsch = 0, wasser = 0, grund = 0;
        double[] fv = b.basis()[2];
        for (int y = 0; y < b.hoehe_px; y++)
            for (int x = 0; x < b.breite; x++) {
                int i = y * b.breite + x;
                for (int c = 0; c < 4; c++) if (!Float.isFinite(h[4 * i + c])) { nan++; break; }
                double[] s = b.strahl(x + 0.5, y + 0.5);
                double cc = 0.5 - b.hoehe, aa = k * (s[0] * s[0] + s[2] * s[2]), bq = s[1], D = bq * bq + 4 * aa * cc;
                double t = D >= 0 && -bq + Math.sqrt(D) > 0 ? -2 * cc / (-bq + Math.sqrt(D)) : -1;
                if (t <= 0) continue;
                double ost = b.ost + s[0] * t, nord = b.nord - s[2] * t;
                int ci = (int) Math.floor((ost - gp.x0) / gp.dx), cj = (int) Math.floor((nord - gp.y0) / gp.dy);
                if (ci < 0 || cj < 0 || ci >= gp.nx || cj >= gp.ny) continue;
                double soll = b.nah / (t * (s[0] * fv[0] + s[1] * fv[1] + s[2] * fv[2]));
                boolean istWasser = Math.abs(dd[i] - soll) <= 1e-3 * soll;
                boolean tief = ci >= 191 && ci < 199 && cj >= 206 && cj < 214;
                if (istWasser) wasser++;
                if (dd[i] <= 0 && Math.asin(-s[1]) > 2 * px + Math.sqrt(4 * k * (b.hoehe - 0.5))) himmel++;
                if (tief && istWasser) falsch++;
                if (tief && !istWasser && dd[i] > 0) grund++;
            }
        return new long[] {himmel, nan, falsch, wasser, grund};
    }
}
