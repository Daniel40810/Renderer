package com.dan.render.seegang.demo;

import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.szene.Dispersion;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Phasenfeld;
import com.dan.core.szene.Profilpuffer;
import com.dan.core.szene.Reflexion;
import com.dan.core.szene.Seegangsoptik;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.seegang.SeegangKarte;

import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfsteine der Oberfläche auf der Karte (Seegang Werkbuch, S3b): {@link SeegangKarte} (float) gegen
 * {@link Oberflaeche} (double) in einer Szene mit fein, grob und Fernfeld, Rampe 20 → 6 m und Seegang aus Westsüdwest.
 * <ol>
 * <li>Phasenfeld der Szene mit dem Gewicht der Energie (Befund B33): Wellenzahl gegen k(h), wo Energie ist.</li>
 * <li>Punkte: Höhe und Verschiebung an 4 000 Orten zu drei Zeiten (auch nach drei Tagen), mit Form und ohne; dazu
 * tiefes, ruhiges Wasser (Befund B37).</li>
 * <li>Clipmap: 8 Stufen zu 128², je Stufe 1 500 Stellen gegen die Referenz mit der Auflösung der Stufe; Normale und J.</li>
 * <li>Stufen untereinander: nur lange Wellen; gemeinsame Stellen gleich, wo alle Wellen in beiden Stufen voll
 * aufgelöst sind (λ ≥ 8 d; im Flachen werden auch lange Wellen kurz).</li>
 * <li>Rechenzeit der Clipmap (auf der 680M Prüfstein, auf lavapipe Auskunft), je Stufe und in einer Abgabe, dazu
 * Messungen ohne Hülle, Phasenfeld oder Ecken der Energie, nur grob und nur fein (Befund B38); Speicher.</li>
 * </ol>
 * Attest {@code docs/status/S3b_SeegangKarte.md}. Aufruf: {@code SeegangKarteCheck [Teil …]}.
 */
public final class SeegangKarteCheck {
    static final Attest A = new Attest("com.dan.render.seegang (SeegangKarte)", Oberflaeche.VERSION);
    static final int NB = 8, ND = 32, STUFEN = 8, N = 128;
    static final double FMIN = 0.06, FMAX = 0.6, D0 = 0.25, HS = 1.4, TP = 6.5, WOHIN = 20;
    /** Abstand zur Grenze der vollen Auflösung (λ ≥ 8 d) in Teil 4, gegen Rundung in float. */
    static final double RESERVE = 0.05;
    static final double[] OM = new double[NB], UNTEN = new double[NB], OBEN = new double[NB];

    static {
        double st = Math.pow(FMAX / FMIN, 1.0 / NB);
        for (int b = 0; b < NB; b++) {
            double lo = FMIN * Math.pow(st, b), hi = lo * st;
            OM[b] = 2 * Math.PI * Math.sqrt(lo * hi); UNTEN[b] = 2 * Math.PI * lo; OBEN[b] = 2 * Math.PI * hi;
        }
    }

    static Profilpuffer puffer;
    static Oberflaeche ob;
    static SeegangKarte karte;
    static double sigma;
    /** Rechenzeit der gewichteten Phasenfelder der Szene (grob, fein; s). */
    static double[] zeitPhase;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        java.util.Set<String> nur = new java.util.HashSet<>(java.util.Arrays.asList(args));
        Runnable[] teile = {SeegangKarteCheck::phasenfeld, SeegangKarteCheck::punkte, SeegangKarteCheck::clipmap, SeegangKarteCheck::stufen, SeegangKarteCheck::zeit};
        try {
            puffer = new Profilpuffer(OM, UNTEN, OBEN, ND, 0x5EE6A3L, false);
            ob = szene(puffer);
            karte = SeegangKarte.mitGeraet(ob, STUFEN, N, D0);
            A.info("Gerät", karte.geraet().name + " (" + karte.geraet().treiber + ")");
            A.info("Reflexion (seit S5b-6)", reflexion.bericht() + "; Bauch an den Wänden grob: " + reflexion.bilanzGrob.text() + "; fein: " + reflexion.bilanzFein.text());
            karte.eingaben();
            for (int i = 0; i < teile.length; i++) {
                if (!nur.isEmpty() && !nur.contains(String.valueOf(i + 1))) continue;
                long ta = System.nanoTime();
                teile[i].run();
                System.out.printf("  [Teil %d: %.1f s]%n", i + 1, (System.nanoTime() - ta) * 1e-9);
            }
        } catch (Throwable e) {
            A.fehler(e);
        } finally {
            if (karte != null) karte.close();
        }
        A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", "S3b_SeegangKarte.md"), "S3b – Oberfläche des Seegangs auf der Karte (Oberflaeche " + Oberflaeche.VERSION + ")");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Szene

    /** JONSWAP (γ 3,3) mit cos²ˢ um wohin (Grad), wie Nahfeld.jonswap (hier ohne die Physik). */
    static double[] jonswap(double hs, double tp, double wohin, double sb) {
        double[] e = new double[NB * ND], ef = new double[NB];
        double fp = 1 / tp, ges = 0, st = Math.pow(FMAX / FMIN, 1.0 / NB);
        int teil = 4000;
        double lfa = Math.log(0.2 * fp), lfe = Math.log(6 * fp);
        for (int i = 0; i < teil; i++) {
            double l = lfa + (i + 0.5) * (lfe - lfa) / teil, ff = Math.exp(l), dff = ff * (lfe - lfa) / teil, sg = ff <= fp ? 0.07 : 0.09;
            double sp = Math.pow(ff, -5) * Math.exp(-1.25 * Math.pow(ff / fp, -4)) * Math.pow(3.3, Math.exp(-(ff - fp) * (ff - fp) / (2 * sg * sg * fp * fp)));
            ges += sp * dff;
            for (int b = 0; b < NB; b++) { double lo = FMIN * Math.pow(st, b); if (ff >= lo && ff < lo * st) ef[b] += sp * dff; }
        }
        double[] dq = new double[ND];
        double sd = 0, tm = Math.toRadians(wohin);
        for (int q = 0; q < ND; q++) { dq[q] = Math.pow(Math.cos(0.5 * Math.IEEEremainder(q * 2 * Math.PI / ND - tm, 2 * Math.PI)), 2 * sb); sd += dq[q]; }
        for (int b = 0; b < NB; b++) for (int q = 0; q < ND; q++) e[b * ND + q] = hs * hs / 16 * ef[b] / ges * dq[q] / sd;
        return e;
    }

    static double tiefe(double x, double y) { return Math.max(0.0, 20 - 0.0045 * x + 1.5 * Math.sin(y / 400)); }

    /** Grob 100 × 100 zu 50 m (5 km), fein 100 × 100 zu 10 m in der Mitte, Fernfeld außen; Rampe 20 → 6 m, eine trockene Kante. */
    /** Seit S5b-6: die Kopplung der Reflexion der Szene. */
    static Reflexion reflexion;

    static Oberflaeche szene(Profilpuffer p) {
        double[] e = jonswap(HS, TP, WOHIN, 10);
        double ux = 466000, uy = 6046000;                                      // Ursprung der Phase (Szene)
        Oberflaeche.Stufe g = stufe("grob", 100, 100, 50, 463500, 6043500, e);
        Oberflaeche.Stufe fz = stufe("fein", 100, 100, 10, 465500, 6045500, e);
        // seit S5b-6: wie die Anwendung mit der Kopplung der Reflexion (an der trockenen Kante); seit S5b-6b Partner aus
        // grob und fein, Bauch an den geraden Wandstücken
        reflexion = Reflexion.partner(g, fz);
        double[] ri = Phasenfeld.richtungen(NB, ND);
        reflexion.richtungen(ri);
        long t0 = System.nanoTime();
        Phasenfeld pg = Phasenfeld.rechne(g.nx, g.ny, g.dx, g.x0, g.y0, g.h, null, null, OM, ri, ND, ux, uy, null, 0, g.e);
        reflexion.wand(g, pg, false);
        g.phasen(pg, pg);
        long t1 = System.nanoTime();
        Phasenfeld pf = Phasenfeld.rechne(fz.nx, fz.ny, fz.dx, fz.x0, fz.y0, fz.h, null, null, OM, ri, ND, ux, uy, pg, 0, fz.e);
        reflexion.wand(fz, pf, true);
        reflexion.teilen(p);
        zeitPhase = new double[] {(t1 - t0) * 1e-9, (System.nanoTime() - t1) * 1e-9};
        fz.phasen(pf, pf);
        Oberflaeche o = new Oberflaeche(p, ND);
        o.grob = g; o.fein = fz;
        o.fernfeld = (x, y) -> new double[] {HS, TP, Math.toRadians(WOHIN)};
        o.vorbereiten();
        sigma = HS / 4;
        return o;
    }

    static Oberflaeche.Stufe stufe(String name, int nx, int ny, double dx, double x0, double y0, double[] e) {
        Oberflaeche.Stufe s = new Oberflaeche.Stufe(name, nx, ny, dx, x0, y0, NB, ND);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                double x = x0 + (i + 0.5) * dx - 463500, y = y0 + (j + 0.5) * dx - 6043500;
                s.h[c] = tiefe(x, y);
                double w = s.h[c] > 0.05 ? Math.min(1, s.h[c] / 8) : 0;      // im Flachen weniger Energie, trocken keine
                for (int k = 0; k < NB * ND; k++) s.e[c * NB * ND + k] = e[k] * w;
            }
        s.gefaelleAusTiefe();
        return s;
    }

    // ------------------------------------------------------------------ 1 Phasenfeld

    /**
     * Energiegewichtetes Mittel und Größtes von |∇Φ| / k(h) − 1 über die Zellen mit hmin ≤ h < hmax (ohne 5 Zellen
     * am Rand), je Stützstelle mit mehr als 1e-3 der größten Energie der Zelle.
     */
    static double[] wellenzahlFehler(Oberflaeche.Stufe s, Phasenfeld pf, double hmin, double hmax) {
        double sw = 0, se = 0, mx = 0;
        double[] ph = new double[6];
        int nq = NB * ND;
        for (int j = 5; j < s.ny - 5; j++)
            for (int i = 5; i < s.nx - 5; i++) {
                int c = j * s.nx + i;
                double h = s.h[c];
                if (h < hmin || h >= hmax) continue;
                double em = 0;
                for (int bq = 0; bq < nq; bq++) em = Math.max(em, s.e[c * nq + bq]);
                for (int bq = 0; bq < nq; bq++) {
                    double e = s.e[c * nq + bq];
                    if (!(e > 1e-3 * em)) continue;
                    pf.wert(bq, s.x0 + (i + 0.5) * s.dx, s.y0 + (j + 0.5) * s.dx, ph);
                    double err = Math.abs(Math.hypot(ph[2], ph[3]) / Dispersion.wellenzahl(OM[bq / ND], h) - 1);
                    sw += e * err; se += e; mx = Math.max(mx, err);
                }
            }
        return new double[] {se > 0 ? sw / se : 0, mx};
    }

    /** Anteil der Energie (Zellen mit h ≥ hmin) in Stützstellen, deren ∇Φ längs e_θ unter k(h) / 2 liegt (Befund B35). */
    static double rueckwaerts(Oberflaeche.Stufe s, Phasenfeld pf, double hmin) {
        double sr = 0, se = 0;
        double[] ph = new double[6];
        int nq = NB * ND;
        for (int j = 0; j < s.ny; j++)
            for (int i = 0; i < s.nx; i++) {
                int c = j * s.nx + i;
                double h = s.h[c];
                if (h < hmin) continue;
                for (int bq = 0; bq < nq; bq++) {
                    double e = s.e[c * nq + bq];
                    if (!(e > 0)) continue;
                    pf.wert(bq, s.x0 + (i + 0.5) * s.dx, s.y0 + (j + 0.5) * s.dx, ph);
                    double th = pf.richtung[bq], l = ph[2] * Math.cos(th) + ph[3] * Math.sin(th);
                    se += e;
                    if (l < 0.5 * Dispersion.wellenzahl(OM[bq / ND], h)) sr += e;
                }
            }
        return se > 0 ? sr / se : 0;
    }

    static void phasenfeld() {
        A.teil("1 Phasenfeld der Szene, mit dem Gewicht der Energie");
        Oberflaeche.Stufe g = ob.grob, fz = ob.fein;
        A.pruefe(g.pa.schritte < Phasenfeld.SCHRITTE && fz.pa.schritte < Phasenfeld.SCHRITTE, "gewichtetes Lösen ist konvergiert (Rest ≤ " + f("%.0e", Phasenfeld.TOLERANZ) + ")",
                 f("grob %d, fein %d Schritte", g.pa.schritte, fz.pa.schritte), "< " + Phasenfeld.SCHRITTE);
        double[] wf = wellenzahlFehler(fz, fz.pa, 0.3, 1e9), wg = wellenzahlFehler(g, g.pa, 8, 1e9);
        A.hoechstens("fein: |∇Φ| / k(h) − 1, energiegewichtet", wf[0], 0.05, "");
        A.hoechstens("grob, h ≥ 8 m: |∇Φ| / k(h) − 1, energiegewichtet", wg[0], 0.05, "");
        A.info("Größtes (fein; grob h ≥ 8 m)", f("%.3f; %.3f", wf[1], wg[1]));
        StringBuilder sb = new StringBuilder();
        double[] gr = {0.3, 1, 2, 4, 8};
        for (int k = 0; k + 1 < gr.length; k++) { double[] w = wellenzahlFehler(g, g.pa, gr[k], gr[k + 1]); sb.append(f("%.1f–%.0f m: %.3f (größtes %.2f); ", gr[k], gr[k + 1], w[0], w[1])); }
        A.info("grob im Flachen (50 m sind dort zu grob, das feine Gitter liegt in S3c über der Brandungslinie)", sb.toString());
        long tu = System.nanoTime();
        Phasenfeld og = Phasenfeld.rechne(g.nx, g.ny, g.dx, g.x0, g.y0, g.h, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, 466000, 6046000, null, 0);
        Phasenfeld of = Phasenfeld.rechne(fz.nx, fz.ny, fz.dx, fz.x0, fz.y0, fz.h, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, 466000, 6046000, og, 0);
        double ohne = (System.nanoTime() - tu) * 1e-9;
        A.info(f("Rechenzeit der Phasenfelder auf dem Prozessor (%d Kerne): mit Gewicht grob; fein; ohne Gewicht beide", Runtime.getRuntime().availableProcessors()),
               f("%.1f s; %.1f s; %.1f s", zeitPhase[0], zeitPhase[1], ohne));
        A.info("zum Vergleich ohne Gewicht (S3a): fein; grob h ≥ 8 m", f("%.3f; %.3f", wellenzahlFehler(fz, of, 0.3, 1e9)[0], wellenzahlFehler(g, og, 8, 1e9)[0]));
        A.info("grob: Anteil der Energie mit ∇Φ · e_θ < k(h) / 2 (am trockenen Rand), mit Gewicht; ohne",
               f("%.2e; %.2e", rueckwaerts(g, g.pa, Phasenfeld.H_MIN), rueckwaerts(g, og, Phasenfeld.H_MIN)));
        StringBuilder sk = new StringBuilder();
        for (int b = 0; b < NB; b++) sk.append(f("%.0f; ", 2 * Math.PI / Math.min(g.pa.kMin[b], fz.pa.kMin[b])));
        A.info("je Band größte Wellenlänge der Dispersion im Feld, 2π / kMin (m)", sk.toString());
    }

    // ------------------------------------------------------------------ 1 Punkte

    static void punkte() {
        A.teil("2 Punkte: Karte gegen Referenz");
        double[][] zeiten = {{0}, {3600.37}, {3 * 86400 + 123.4}};
        Random r = new Random(1);
        int np = 4000;
        double[] xy = new double[2 * np];
        for (int i = 0; i < np; i++) {                                         // gleich verteilt über 6 km, dichter um das feine Gitter
            boolean nah = i % 2 == 0;
            xy[2 * i] = nah ? 465300 + 1400 * r.nextDouble() : 463000 + 6000 * r.nextDouble();
            xy[2 * i + 1] = nah ? 6045300 + 1400 * r.nextDouble() : 6043000 + 6000 * r.nextDouble();
        }
        karte.bezug(466000, 6046000);
        for (int fall = 0; fall < 2; fall++) {
            boolean form = fall == 0;
            ob.mitForm = ob.mitHoehen = form;
            ob.mitLangwelle = form;
            double maxE = 0, maxX = 0;
            for (double[] tz : zeiten) {
                double t = tz[0];
                karte.zeit(t);
                float[] a = karte.punkte(xy, 0);
                Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
                ob.aufloesung = 0;
                for (int i = 0; i < np; i++) {
                    ob.punkt(xy[2 * i], xy[2 * i + 1], t, pt);
                    maxE = Math.max(maxE, Math.abs(a[8 * i] - pt.eta) / sigma);
                    maxX = Math.max(maxX, Math.hypot(a[8 * i + 1] - pt.xi, a[8 * i + 2] - pt.yi) / sigma);
                }
            }
            String w = form ? "mit Form und langer Welle" : "linear mit Verschiebung";
            A.hoechstens(f("%s: |Δη| / σ (4 000 Orte, 3 Zeiten bis 3 Tage)", w), maxE, 2e-3, "");
            A.hoechstens(f("%s: |Δξ| / σ", w), maxX, 2e-3, "");
        }
        ob.mitForm = ob.mitHoehen = true;
        ob.mitLangwelle = false;
        // mit der Auflösung einer Stufe (d = 2 m)
        karte.zeit(500);
        float[] a = karte.punkte(xy, 2);
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        ob.aufloesung = 2;
        double maxE = 0;
        for (int i = 0; i < np; i++) { ob.punkt(xy[2 * i], xy[2 * i + 1], 500, pt); maxE = Math.max(maxE, Math.abs(a[8 * i] - pt.eta) / sigma); }
        ob.aufloesung = 0;
        A.hoechstens("aufgelöst wie eine Stufe mit d = 2 m: |Δη| / σ", maxE, 2e-3, "");
        tiefRuhig();
    }

    /**
     * Tiefes, ruhiges Wasser (Befund B37): 30 m, Hs 0,3 m, Tp 8 s. Die Ursell-Zahl ist klein, das Argument von tanh in
     * ψ nach Ruessink groß; ein tanh über exp(2x) gäbe dort NaN.
     */
    static void tiefRuhig() {
        double hs = 0.3, s3 = hs / 4;
        double[] e = jonswap(hs, 8, WOHIN, 10);
        Oberflaeche.Stufe g = new Oberflaeche.Stufe("grob", 60, 60, 50, 463500, 6043500, NB, ND);
        for (int c = 0; c < g.nc; c++) { g.h[c] = 30; System.arraycopy(e, 0, g.e, c * NB * ND, NB * ND); }
        g.gefaelleAusTiefe();
        Phasenfeld pg = Phasenfeld.rechne(g.nx, g.ny, g.dx, g.x0, g.y0, g.h, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, 465000, 6045000, null, 0, g.e);
        g.phasen(pg, pg);
        Oberflaeche o3 = new Oberflaeche(puffer, ND);
        o3.grob = g;
        Random r = new Random(3);
        int np = 1000;
        double[] xy = new double[2 * np];
        for (int i = 0; i < np; i++) { xy[2 * i] = 464200 + 1600 * r.nextDouble(); xy[2 * i + 1] = 6044200 + 1600 * r.nextDouble(); }
        try (SeegangKarte k = SeegangKarte.mitGeraet(o3, STUFEN, N, D0)) {
            k.eingaben();
            k.bezug(465000, 6045000);
            k.zeit(1234.5);
            float[] a = k.punkte(xy, 0);
            Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
            double maxE = 0, arg = 0;
            int nan = 0;
            for (int i = 0; i < np; i++) {
                o3.punkt(xy[2 * i], xy[2 * i + 1], 1234.5, pt);
                if (!Float.isFinite(a[8 * i]) || !Float.isFinite(a[8 * i + 1])) { nan++; continue; }
                maxE = Math.max(maxE, Math.abs(a[8 * i] - pt.eta) / s3);
                if (pt.ur > 0) arg = Math.max(arg, 0.815 / Math.pow(pt.ur, 0.672));
            }
            A.pruefe(nan == 0, "tiefes, ruhiges Wasser (30 m, Hs 0,3 m): Orte ohne Zahl (NaN)", String.valueOf(nan), "0");
            A.hoechstens("tiefes, ruhiges Wasser: |Δη| / σ (1 000 Orte, mit Form)", maxE, 2e-3, "");
            A.info("größtes Argument von tanh in ψ nach Ruessink (ab rund 44 NaN, wo tanh über exp(2x) läuft)", f("%.0f", arg));
        }
    }

    // ------------------------------------------------------------------ 2 Clipmap

    static void clipmap() {
        A.teil("3 Clipmap: 8 Stufen zu 128² gegen die Referenz");
        double ex = 466010, ey = 6046020, t = 1234.5;
        karte.bezug(466000, 6046000);
        karte.zeit(t);
        karte.stufen(ex, ey, null);
        Random r = new Random(2);
        double maxE = 0, maxW = 0, maxJ = 0, minJ = 1e9, maxA = 0, maxV = 0;
        StringBuilder sb = new StringBuilder();
        for (int l = 0; l < STUFEN; l++) {
            float[] v = karte.verschiebung(l), nn = karte.normale(l), au = karte.aufgeloest(l);
            double dl = karte.abstand(l), mE = 0, mA = 0, sA = 0, mV = 0;
            ob.aufloesung = dl;
            Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
            for (int k = 0; k < 1500; k++) {
                int i = 1 + r.nextInt(N - 2), j = 1 + r.nextInt(N - 2), id = 4 * (j * N + i);
                double[] p = karte.ort(l, i, j);
                double nah = karte.nah(l, i, j);
                ob.punkt(p[0], p[1], t, pt, nah);
                double e = Math.max(Math.abs(v[id + 2] - pt.eta), Math.hypot(v[id] - pt.xi, v[id + 1] - pt.yi)) / sigma;
                mE = Math.max(mE, e);
                mA = Math.max(mA, Math.abs(au[2 * (j * N + i)] - pt.neigung));
                mV = Math.max(mV, Math.abs(au[2 * (j * N + i) + 1] - pt.gezeichnet) / (sigma * sigma));
                sA += pt.neigung;
                if (k % 10 == 0 && nah >= 1) {
                    double[] lg = ob.lage(p[0], p[1], t, dl);
                    double cos = nn[id] * lg[3] + nn[id + 1] * lg[4] + nn[id + 2] * lg[5];
                    maxW = Math.max(maxW, Math.toDegrees(Math.acos(Math.min(1, cos))));
                    maxJ = Math.max(maxJ, Math.abs(nn[id + 3] - lg[6]));
                }
                minJ = Math.min(minJ, nn[id + 3]);
            }
            maxE = Math.max(maxE, mE);
            if (sA > 0) maxA = Math.max(maxA, mA / (sA / 1500));
            maxV = Math.max(maxV, mV);
            sb.append(f("%d: %.2e; ", l, mE));
        }
        ob.aufloesung = 0;
        A.info("je Stufe |Δ| / σ", sb.toString());
        A.hoechstens("Verschiebung und Höhe aller Stufen gegen die Referenz (am Rand der Stufe 7 mit dem Übergang ins Fernfeld), / σ", maxE, 2e-3, "");
        A.hoechstens("aufgelöste Neigungsvarianz je Stelle gegen die Referenz, / Mittel der Stufe", maxA, 2e-3, "");
        A.hoechstens("gezeichnete Varianz der Höhe je Stelle gegen die Referenz, / σ²", maxV, 2e-3, "");
        randFern(t);
        A.hoechstens("Normale gegen die Referenz (zentrale Differenz mit d_L)", maxW, 0.5, "°");
        A.hoechstens("J gegen die Referenz", maxJ, 1e-2, "");
        A.info("kleinstes J in den Stichproben", f("%.3f", minJ));
    }

    /**
     * N4 (S3c): Am äußersten Rand der Stufe 7 zeichnet die Synthese nur das Fernfeld. Dort soll sie die Wellen treffen,
     * die der Weg jenseits der Clipmap zeichnet ({@code render.water}: ζ = Σ a sin φ, derselbe Satz und dieselbe Phase, ohne
     * Verschiebung): η der Stelle x₀ gegen ζ am verschobenen Ort x₀ + ξ. Der Rest sind die Form, die Hülle und die
     * Verschiebung zweiter Ordnung.
     */
    static void randFern(double t) {
        int l = STUFEN - 1;
        float[] v = karte.verschiebung(l);
        double dl = karte.abstand(l);
        double[] w = ob.fernfeld.werte(karte.bx, karte.by);
        double[][] wl = Seegangsoptik.wellen();
        double[] amp = Seegangsoptik.amplituden(w[0], w[1], w[2], 1e-4, wl, Seegangsoptik.baender(), Seegangsoptik.cdf());
        double sum2 = 0, max = 0, m0 = 0;
        int z = 0;
        for (int j = 0; j < Seegangsoptik.LANG; j++) m0 += 0.5 * amp[j] * amp[j];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++) {
                if (karte.nah(l, i, j) > 0) continue;
                double[] p = karte.ort(l, i, j);
                int id = 4 * (j * N + i);
                double x = p[0] + v[id], y = p[1] + v[id + 1], zeta = 0;
                for (int q = 0; q < Seegangsoptik.LANG; q++) {
                    double k = Math.hypot(wl[q][0], wl[q][1]), g = Oberflaeche.aufgeloest(k, dl), a = amp[q] * g;
                    if (a <= 0) continue;
                    zeta += a * Math.sin(wl[q][0] * x + wl[q][1] * y + wl[q][2] - Wasseroptik.omega(k) * t);
                }
                double d = Math.abs(v[id + 2] - zeta);
                sum2 += d * d; max = Math.max(max, d); z++;
            }
        double sg = Math.sqrt(m0);
        A.hoechstens(f("N4: am Rand der Clipmap gegen die Wellen jenseits (%d Stellen), RMS |Δη| / σ", z), Math.sqrt(sum2 / Math.max(1, z)) / sg, 0.05, "");
        A.info("N4: größtes |Δη| / σ am Rand", f("%.3f", max / sg));
    }

    // ------------------------------------------------------------------ 3 Stufen untereinander

    static void stufen() {
        A.teil("4 Stufen untereinander (lange Wellen, wo voll aufgelöst)");
        double[] e = jonswap(0.8, 12, WOHIN, 20);
        for (int k = 0; k < NB * ND; k++) if (OM[k / ND] > 2 * Math.PI * 0.11) e[k] = 0;   // nur bis 0,11 Hz
        Oberflaeche o2 = new Oberflaeche(puffer, ND);
        Oberflaeche.Stufe g = stufe("grob", 100, 100, 50, 463500, 6043500, e);
        Phasenfeld pg = Phasenfeld.rechne(g.nx, g.ny, g.dx, g.x0, g.y0, g.h, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, 466000, 6046000, null, 0, g.e);
        g.phasen(pg, pg);
        o2.grob = g;
        o2.mitHoehen = false;
        boolean[] aktiv = new boolean[NB * ND];
        for (int k = 0; k < NB * ND; k++) aktiv[k] = e[k] > 0;
        double[] ph = new double[6];
        try (SeegangKarte k = SeegangKarte.mitGeraet(o2, STUFEN, N, D0)) {
            k.eingaben();
            k.bezug(466000, 6046000);
            k.zeit(77);
            k.stufen(466000, 6046000, null);
            double max = 0, maxAlle = 0;
            int paare = 0;
            StringBuilder sb = new StringBuilder(), sa = new StringBuilder();
            for (int l = 0; l + 1 < STUFEN; l++) {
                double mp = 0, ma = 0;
                int gemeinsam = 0, voll = 0;
                float[] a = k.verschiebung(l), b = k.verschiebung(l + 1);
                double dl = k.abstand(l + 1), lmin = 8 * dl * (1 + RESERVE);
                double[] m = k.mitte(l + 1);
                for (int j = 0; j < N; j++)
                    for (int i = 0; i < N; i++) {
                        double[] p = k.ort(l, i, j);
                        double fi = (p[0] - m[0]) / dl + N / 2, fj = (p[1] - m[1]) / dl + N / 2;
                        int i2 = (int) Math.round(fi), j2 = (int) Math.round(fj);
                        if (Math.abs(fi - i2) > 1e-6 || Math.abs(fj - j2) > 1e-6 || i2 < 0 || j2 < 0 || i2 >= N || j2 >= N) continue;
                        gemeinsam++;
                        int ia = 4 * (j * N + i), ib = 4 * (j2 * N + i2);
                        double d = 0;
                        for (int c = 0; c < 3; c++) d = Math.max(d, Math.abs(a[ia + c] - b[ib + c]));
                        ma = Math.max(ma, d);
                        boolean ganz = true;                                  // alle Wellen mit Energie über 8 d der gröberen Stufe?
                        for (int bq = 0; bq < NB * ND && ganz; bq++) {
                            if (!aktiv[bq]) continue;
                            pg.wert(bq, p[0], p[1], ph);
                            double kk = Math.hypot(ph[2], ph[3]);
                            if (kk > 0 && 2 * Math.PI / kk < lmin) ganz = false;
                        }
                        if (!ganz) continue;
                        voll++;
                        mp = Math.max(mp, d);
                    }
                sa.append(f("%d/%d: %.1e; ", l, l + 1, ma / (0.8 / 4)));
                if (voll > 0) { sb.append(f("%d/%d: %.1e (%.0f %%); ", l, l + 1, mp / (0.8 / 4), 100.0 * voll / gemeinsam)); max = Math.max(max, mp); paare++; }
                else sb.append(f("%d/%d: keine; ", l, l + 1));
                maxAlle = Math.max(maxAlle, ma);
            }
            A.info("je Paar |Δ| / σ an voll aufgelösten Stellen (Anteil der gemeinsamen Stellen)", sb.toString());
            A.info("je Paar |Δ| / σ an allen gemeinsamen Stellen (mit teils aufgelösten Wellen, Auskunft)", sa.toString());
            A.pruefe(paare >= 4, "Paare von Stufen mit voll aufgelösten Stellen", String.valueOf(paare), "≥ 4");
            A.hoechstens("gleiche Stelle in Stufe L und L + 1, wo voll aufgelöst, |Δ| / σ", max / (0.8 / 4), 1e-4, "");
        }
    }

    // ------------------------------------------------------------------ 4 Zeit

    /**
     * Eine Messung der Rechenzeit: Abgabe, Bits der Diagnose, ohne fein, ohne grob, Ablage, Schwellen, Liste, Stufen, und
     * ob wie im Lauf ({@link SeegangKarte#bild}: die fernen Stufen jedes zweite Bild).
     */
    record Variante(String name, boolean eine, int diagnose, boolean ohneFein, boolean ohneGrob, boolean jeZelle, boolean eJeZelle, double schwelle,
                    double neigung, boolean alle, int von, int bis, boolean bild) {
        Variante(String name, boolean eine, int diagnose, boolean ohneFein, boolean ohneGrob) { this(name, eine, diagnose, ohneFein, ohneGrob, false, false, Oberflaeche.E_REL, Oberflaeche.S_REL, false, 0, STUFEN, false); }
        Variante(String name, boolean eine, int diagnose, boolean ohneFein, boolean ohneGrob, boolean jeZelle, boolean eJeZelle) { this(name, eine, diagnose, ohneFein, ohneGrob, jeZelle, eJeZelle, Oberflaeche.E_REL, Oberflaeche.S_REL, false, 0, STUFEN, false); }
        Variante(String name, double schwelle, double neigung) { this(name, true, 0, false, false, false, false, schwelle, neigung, false, 0, STUFEN, false); }
        Variante(String name, boolean alle, int von, int bis) { this(name, true, 0, false, false, false, false, Oberflaeche.E_REL, Oberflaeche.S_REL, alle, von, bis, false); }
        Variante(String name) { this(name, true, 0, false, false, false, false, Oberflaeche.E_REL, Oberflaeche.S_REL, false, 0, STUFEN, true); }
        boolean vorgabe() { return eine && diagnose == 0 && !ohneFein && !ohneGrob && !jeZelle && !eJeZelle && schwelle == Oberflaeche.E_REL && neigung == Oberflaeche.S_REL && !alle && von == 0 && bis == STUFEN; }
    }

    /** Budget der Synthese je Bild im Lauf (SF23 A) [G]. */
    static final double BUDGET_MS = 2.2;

    static void zeit() {
        A.teil("5 Rechenzeit der Clipmap");
        boolean software = karte.geraet().name.toLowerCase().contains("llvmpipe") || karte.geraet().name.toLowerCase().contains("lavapipe");
        karte.bezug(466000, 6046000);
        Variante[] varianten = {
            new Variante("im Lauf: Stufen 5–7 jedes zweite Bild (Vorgabe, SF23 A)"),
            new Variante("alle Stufen jedes Bild, eine Abgabe", true, 0, false, false),
            new Variante("je Stufe eine Abgabe (wie L2)", false, 0, false, false),
            new Variante("eine Abgabe, ohne Hülle", true, SeegangKarte.D_HUELLE, false, false),
            new Variante("eine Abgabe, ohne Phasenfeld", true, SeegangKarte.D_PHASE, false, false),
            new Variante("eine Abgabe, Energie aus einer Ecke", true, SeegangKarte.D_ENERGIE, false, false),
            new Variante("eine Abgabe, ohne Hülle, Phasenfeld und mit einer Ecke", true, SeegangKarte.D_HUELLE | SeegangKarte.D_PHASE | SeegangKarte.D_ENERGIE, false, false),
            new Variante("eine Abgabe, nur grob", true, 0, true, false),
            new Variante("eine Abgabe, nur fein (außen das Fernfeld)", true, 0, false, true),
            new Variante("eine Abgabe, Phase je Zelle abgelegt", true, 0, false, false, true, false),
            new Variante("eine Abgabe, Varianz und Kennwerte je Zelle abgelegt (wie L2c)", true, 0, false, false, false, true),
            new Variante("eine Abgabe, Schwelle nur Varianz 10⁻⁴ m0 (wie L2f)", 1e-4, 0),
            new Variante("eine Abgabe, Schwelle nur Varianz 10⁻³ m0", 1e-3, 0),
            new Variante("eine Abgabe, Schwelle Varianz oder Neigung 3 · 10⁻³", 3e-3, 3e-3),
            new Variante("eine Abgabe, ohne die Liste der aktiven Stützstellen", true, 0, STUFEN),
            new Variante("eine Abgabe, nur die Stufen 0–4", false, 0, 5),
            new Variante("eine Abgabe, nur die Stufen 5–7", false, 5, STUFEN),
            new Variante("im Lauf, noch einmal am Ende (Prüfstein; B42)")};
        int runden = software ? 2 : 30, vorlauf = software ? 1 : 4;
        // B42: rund 1 s Vorlauf, damit die Karte hochgetaktet ist, bevor die erste Messung beginnt
        long t0 = System.nanoTime();
        int warm = 0;
        while (!software && System.nanoTime() - t0 < 1_000_000_000L && warm < 2000) { karte.zeit(90 + warm / 60.0); karte.stufen(466010, 6046020, null); warm++; }
        StringBuilder sv = new StringBuilder();
        double ersteLauf = Double.NaN;
        for (int vi = 0; vi < varianten.length; vi++) {
            Variante v = varianten[vi];
            boolean letzte = vi == varianten.length - 1;
            karte.eineAbgabe = v.eine(); karte.diagnose = v.diagnose(); karte.ohneFein = v.ohneFein(); karte.ohneGrob = v.ohneGrob();
            karte.stufenVon = v.von(); karte.stufenBis = v.bis();
            if (karte.phaseJeZelle != v.jeZelle() || karte.energieJeZelle != v.eJeZelle() || karte.alleStuetzstellen != v.alle()
                    || karte.schwelle != v.schwelle() || karte.neigung != v.neigung()) {
                karte.phaseJeZelle = v.jeZelle(); karte.energieJeZelle = v.eJeZelle(); karte.alleStuetzstellen = v.alle();
                karte.schwelle = v.schwelle(); karte.neigung = v.neigung();
                karte.eingaben();
            }
            try (Stoppuhr uhr = new Stoppuhr(karte.geraet(), karte.geraet().rechnen, 32)) {
                int gelesen = 0;
                for (int k = 0; k < vorlauf + runden; k++) {
                    if (k == vorlauf) { uhr.sammle(true); uhr.summenLeeren(); gelesen = 0; }
                    karte.zeit(100 + k / 60.0);
                    if (v.bild()) karte.bild(466010 + k * 0.1, 6046020, uhr);
                    else karte.stufen(466010 + k * 0.1, 6046020, uhr);
                    gelesen += uhr.sammle(false);
                }
                gelesen += uhr.sammle(true);
                if (!uhr.verfuegbar) { A.info("Zeitstempel", "nicht verfügbar"); break; }
                Map<String, Double> su = uhr.summen();
                int nr = Math.max(1, gelesen);
                double s = 0, sn = 0;
                if (v.eine()) { s = su.getOrDefault("see_stufen", 0.0) / nr; sn = su.getOrDefault("see_normalen", 0.0) / nr; }
                else {
                    StringBuilder sb = new StringBuilder();
                    for (int l = 0; l < STUFEN; l++) {
                        double m = su.getOrDefault("see_stufe " + l, 0.0) / nr;
                        s += m; sb.append(f("%d: %.2f; ", l, m)); sn += su.getOrDefault("see_normale " + l, 0.0) / nr;
                    }
                    A.info("je Stufe eine Abgabe: je Stufe (ms)", sb.toString());
                }
                if (v.vorgabe() && v.bild() && !letzte) ersteLauf = s + sn;
                if (v.vorgabe() && v.bild() && letzte) {
                    if (software) A.info(f("Synthese mit Normale je Bild im Lauf auf %s (sagt über die 680M nichts)", karte.geraet().name), f("%.1f ms", s + sn));
                    else {
                        A.hoechstens(f("Synthese mit Normale je Bild im Lauf, Mittel über %d Bilder (Budget SF23 A %.1f ms)", nr, BUDGET_MS), s + sn, BUDGET_MS, "ms");
                        A.info("im Lauf am Anfang und am Ende der Reihe (B42)", f("%.2f und %.2f ms (Vorlauf %d Bilder)", ersteLauf, s + sn, warm));
                    }
                }
                sv.append(f("%s: %.2f ms (Normale %.2f); ", v.name(), s + sn, sn));
            }
        }
        // dasselbe Bild mit jeder Ablage; ohne die Liste fast dasselbe (Oberflaeche.aktiv: die Neigungsschwelle zwischen den Zellen)
        karte.eineAbgabe = true; karte.diagnose = 0; karte.ohneFein = karte.ohneGrob = false; karte.schwelle = Oberflaeche.E_REL; karte.neigung = Oberflaeche.S_REL;
        karte.stufenVon = 0; karte.stufenBis = STUFEN;
        double dmax = 0, dListe = 0;
        float[] vor = null, erste = null;
        for (int a = 0; a < 4; a++) {
            karte.phaseJeZelle = a == 1; karte.energieJeZelle = a == 2; karte.alleStuetzstellen = a == 3;
            karte.eingaben();
            karte.zeit(100);
            karte.stufen(466010, 6046020, null);
            float[] v = karte.verschiebung(STUFEN - 3).clone();
            if (a == 0) erste = v;
            if (a == 3) { for (int i = 0; i < v.length; i++) dListe = Math.max(dListe, Math.abs(v[i] - erste[i])); }
            else if (vor != null) for (int i = 0; i < v.length; i++) dmax = Math.max(dmax, Math.abs(v[i] - vor[i]));
            if (a < 3) vor = v;
        }
        karte.phaseJeZelle = karte.energieJeZelle = karte.alleStuetzstellen = false;
        karte.eingaben();
        karte.zeit(100);
        A.hoechstens("Ablage je Zelle (Phase; Varianz und Kennwerte): dasselbe Bild (Stufe 5), |Δ| / σ", dmax / sigma, 1e-6, "");
        A.hoechstens("ohne die Liste der aktiven Stützstellen: fast dasselbe Bild (Stufe 5), |Δ| / σ", dListe / sigma, 1e-3, "");
        // im Lauf: Stufe 7 steht in jedem zweiten Bild, Stufe 0 nie
        {
            karte.zeit(100);
            karte.neuerLauf();
            karte.bild(466010, 6046020, null);
            float[] f7 = karte.verschiebung(STUFEN - 1).clone(), f0 = karte.verschiebung(0).clone();
            double[] m7 = karte.mitte(STUFEN - 1);
            karte.zeit(100.05);
            karte.bild(466200, 6046020, null);
            float[] g7 = karte.verschiebung(STUFEN - 1), g0 = karte.verschiebung(0);
            double[] n7 = karte.mitte(STUFEN - 1);
            double d7 = 0, d0 = 0;
            for (int i = 0; i < f7.length; i++) { d7 = Math.max(d7, Math.abs(g7[i] - f7[i])); d0 = Math.max(d0, Math.abs(g0[i] - f0[i])); }
            boolean steht = d7 == 0 && m7[0] == n7[0] && m7[1] == n7[1];
            A.pruefe(steht && d0 > 0, "im Lauf: Stufe 7 mit Mitte steht im zweiten Bild, Stufe 0 bewegt sich", f("Δ7 %.1e, Δ0 %.1e", d7, d0), "Δ7 = 0, Δ0 > 0");
        }
        A.info("Liste der aktiven Stützstellen (B41): in irgendeiner Zelle über der Schwelle", karte.aktivZahl() + " von " + NB * ND);
        A.info("Rechenzeit je Messung (Diagnose B38; ohne Hülle, Phase oder Ecken ist das Bild falsch)", sv.toString());
        A.info("Speicher auf der Karte", f("%.1f MB (Hüllen %.1f MB)", karte.bytes() / 1e6, puffer.bytes() / 1e6));
        {
            int aktiv = 0, c = 50 * 100 + 50, kw = ob.grob.kennWerte();
            double[] kq = new double[NB];
            double sS = Oberflaeche.S_REL * Oberflaeche.neigung(ob.grob.kenn, c * kw, NB, kq), eS = Math.max(Oberflaeche.E_MIN, Oberflaeche.E_REL * ob.grob.kenn[c * kw]);
            for (int bq = 0; bq < NB * ND; bq++) if (Oberflaeche.faktor(ob.grob.eZ[c * NB * ND + bq], eS, kq[bq / ND], sS) > 0) aktiv++;
            A.info("Stützstellen über einer der Schwellen (Mitte des groben Gitters)", aktiv + " von " + NB * ND);
        }
        // was eine höhere Schwelle weglässt, in der Mitte des feinen Gitters (zur Frage SF23)
        {
            Oberflaeche.Stufe fz = ob.fein;
            int c = 50 * fz.nx + 50, m = NB * ND;
            double[] ph = new double[6];
            double m0 = 0, s2 = 0;
            double[] e = new double[m], k2 = new double[m];
            for (int bq = 0; bq < m; bq++) {
                e[bq] = fz.eZ[c * m + bq];
                fz.pa.wert(bq, fz.x0 + 50.5 * fz.dx, fz.y0 + 50.5 * fz.dx, ph);
                k2[bq] = ph[2] * ph[2] + ph[3] * ph[3];
                m0 += e[bq]; s2 += k2[bq] * e[bq];
            }
            StringBuilder sw = new StringBuilder();
            for (double sch : new double[] {1e-4, 1e-3, 3e-3, 1e-2}) {
                int n = 0;
                double em = 0, sm = 0;
                for (int bq = 0; bq < m; bq++) if (e[bq] > sch * m0) n++; else { em += e[bq]; sm += k2[bq] * e[bq]; }
                sw.append(f("%.0e: %d Stützstellen, weg %.2f %% von m0 und %.1f %% der Neigung; ", sch, n, 100 * em / m0, 100 * sm / s2));
            }
            A.info("Schwelle in der Mitte des feinen Gitters (was darunter liegt, geht ins Glitzern)", sw.toString());
            StringBuilder sz = new StringBuilder();
            for (double sch : new double[] {1e-3, 3e-3, 1e-2}) {
                int n = 0;
                double em = 0, sm = 0;
                for (int bq = 0; bq < m; bq++) if (e[bq] > sch * m0 || k2[bq] * e[bq] > sch * s2) n++; else { em += e[bq]; sm += k2[bq] * e[bq]; }
                sz.append(f("%.0e: %d Stützstellen, weg %.2f %% von m0 und %.1f %% der Neigung; ", sch, n, 100 * em / m0, 100 * sm / s2));
            }
            A.info("Schwelle für Varianz oder Neigung (eine Stützstelle bleibt, wenn sie über einer der beiden liegt)", sz.toString());
            {
                double[] kq = new double[NB];
                int kw = fz.kennWerte(), n = 0, voll = 0;
                double sS = Oberflaeche.S_REL * Oberflaeche.neigung(fz.kenn, c * kw, NB, kq), eS = Math.max(Oberflaeche.E_MIN, Oberflaeche.E_REL * fz.kenn[c * kw]);
                double em = 0, sm = 0;
                for (int bq = 0; bq < m; bq++) {
                    double fa = Oberflaeche.faktor(e[bq], eS, kq[bq / ND], sS);
                    if (fa > 0) n++;
                    if (fa >= 1) voll++;
                    em += (1 - fa * fa) * e[bq]; sm += (1 - fa * fa) * k2[bq] * e[bq];
                }
                A.info("Vorgabe (SF23 A: 10⁻³ für Varianz oder Neigung, eingeblendet bis zum Doppelten)",
                        f("%d Stützstellen gezeichnet, davon %d ganz; weg %.2f %% von m0 und %.1f %% der Neigung", n, voll, 100 * em / m0, 100 * sm / s2));
            }
        }
        A.info("Wellenlänge der Bänder im tiefen Wasser", f("%.1f bis %.0f m", 2 * Math.PI * Dispersion.G / (OM[NB - 1] * OM[NB - 1]), 2 * Math.PI * Dispersion.G / (OM[0] * OM[0])));
        StringBuilder sl = new StringBuilder();
        for (int b = 0; b < NB; b++) sl.append(f("%.0f/%.0f; ", 2 * Math.PI / karte.kUnten(b), 2 * Math.PI * Dispersion.G / (OM[b] * OM[b])));
        A.info("je Band 2π / kUnten auf der Karte / im tiefen Wasser (m); das Band übergangen, wo 4 d darüber", sl.toString());
    }
}
