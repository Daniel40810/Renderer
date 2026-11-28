package com.dan.render.seegang.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Profilpuffer;
import com.dan.render.seegang.Randwelle;
import com.dan.render.seegang.SeegangKarte;

import java.nio.file.Path;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Der Antrieb des Auflaufs aus der Synthese (Seegang Werkbuch, S6b-2; Grenzen [G] vor der Messung, Datei 67): die Szene von
 * {@link SeegangKarteCheck}.
 * <ol>
 * <li>Karte gegen Referenz ({@code Oberflaeche.Punkt.etaAntrieb}, {@code .qx}, {@code .qy}) an 2 000 Orten zu drei Zeiten:
 * |Δη| / σ ≤ 2e-3, |Δq| / (σ √(g h)) ≤ 2e-3 (der Fluss an Orten mit mindestens 0,5 m Wasser, nach der Messung geändert,
 * siehe dort); dazu ein Versatz der Zeit von 0,1 s gegen die Referenz zur selben Zeit.</li>
 * <li>Auskunft: Massenbilanz der Referenz, linear (ohne Form), ∂η/∂t + ∇·q gegen ∂η/∂t (die Hülle läuft mit c_g, der Fluss
 * je Welle mit c; im Flachen fast gleich).</li>
 * <li>Verteilen auf Ziele gegen dieselbe Rechnung in Java: ≤ 1e-5 relativ.</li>
 * <li>Kosten: 129 Punkte (256 m zu 2 m) zu einer Zeit auf der Karte ≤ 0,25 ms (auf einem Software-Gerät Auskunft).</li>
 * <li>Seit S6c-1b (Seegang Werkbuch 78, SF85 A): Rand mit erhaltener Richtung ({@code Randwelle.bezug},
 * {@code Oberflaeche.punktRand}). 129 Punkte zu 2 m auf einer Geraden durch die Mitte des feinen Gitters, deren Normale
 * 45° gegen die mittlere Richtung des Seegangs steht; Bezug in der Mitte, h_R die Tiefe dort (8,7 m). (a) Karte gegen
 * Referenz zu drei Zeiten, Grenzen wie Teil 1. (b) Referenz über 256 s: Anteil der Energie längs der Geraden mit
 * |k_y| &lt; ω/√(g h_R) + 2π/L (frei laufend im Löser, bis auf eine Zelle der Auflösung der Geraden) mit Bezug
 * ≥ {@link #FREI_MIN} [G, nach dem Versuch im Container, Datei 78: 99,3 %, ohne Bezug 80,6 %]; streng (ohne die Zelle)
 * und ohne Bezug Auskunft.</li>
 * </ol>
 * Attest {@code docs/status/S6b_Randwelle.md}.
 */
public final class RandwelleCheck {
    static final Attest A = new Attest("com.dan.render.seegang (Randwelle)", Oberflaeche.VERSION);

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        SeegangKarte karte = null;
        try {
            Profilpuffer puffer = new Profilpuffer(SeegangKarteCheck.OM, SeegangKarteCheck.UNTEN, SeegangKarteCheck.OBEN, SeegangKarteCheck.ND, 0x5EE6A3L, false);
            Oberflaeche ob = SeegangKarteCheck.szene(puffer);
            karte = SeegangKarte.mitGeraet(ob, SeegangKarteCheck.STUFEN, SeegangKarteCheck.N, SeegangKarteCheck.D0);
            A.info("Gerät", karte.geraet().name + " (" + karte.geraet().treiber + ")");
            karte.eingaben();
            karte.bezug(466000, 6046000);
            ob.aufloesung = 0;
            gegenReferenz(karte, ob, SeegangKarteCheck.sigma);
            bilanz(ob);
            verteilen(karte);
            kosten(karte);
            richtungErhalten(karte, ob, SeegangKarteCheck.sigma);
        } catch (Throwable e) {
            A.fehler(e);
        } finally {
            if (karte != null) karte.close();
        }
        A.info("Dauer der Prüfung", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", "S6b_Randwelle.md"), "S6b-2 – Antrieb des Auflaufs aus der Synthese (Randwelle)");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void gegenReferenz(SeegangKarte karte, Oberflaeche ob, double sigma) {
        A.teil("1 Antrieb an Punkten: Karte gegen Referenz");
        Geraet g = karte.geraet();
        Random r = new Random(7);
        int np = 2000;
        double[] xy = new double[2 * np];
        for (int i = 0; i < np; i++) {                                     // die Hälfte im feinen Gitter, der Rest über die Szene
            boolean nah = i % 2 == 0;
            xy[2 * i] = nah ? 465550 + 900 * r.nextDouble() : 463200 + 5600 * r.nextDouble();
            xy[2 * i + 1] = nah ? 6045550 + 900 * r.nextDouble() : 6043200 + 5600 * r.nextDouble();
        }
        // Fall nach der Messung geändert (Grenze nicht): der Fluss nur an Orten mit mindestens 0,5 m Wasser. Zuerst zählten
        // alle 2 000; die Verstöße (bis 0,028) lagen alle auf trockenem Land (Tiefe 0, nur Fernfeld), wo die gebundene
        // lange Welle bei der kleinsten Tiefe (5 cm) Stände von 5–6,5 m und Flüsse von 8 m²/s gibt und √(g · 0,05 m) als
        // Maßstab den Fehler aufbläht. Der Rand des Lösers liegt im Wasser (SF64 A); die trockenen Orte als Auskunft.
        double maxE = 0, maxQ = 0, maxEv = 0, maxQv = 0, rmsQ = 0, maxTrocken = 0, etaTrocken = 0;
        int nq = 0, nNass = 0;
        double[] lwSum = new double[3];
        int[] lwN = new int[3];
        try (Randwelle rw = new Randwelle(g, g.konto("Randwelle", 64L << 20), np, 1)) {
            rw.punkte(xy);
            Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
            for (double t : new double[] {120.0, 3600.37, 2 * 86400 + 55.5}) {
                karte.zeit(t);
                Ablauf ab = new Ablauf(g);
                rw.rechne(ab, karte, 0, 0.1);
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                float[] a = rw.lies();
                for (int i = 0; i < np; i++) {
                    for (int z = 0; z < 2; z++) {
                        ob.punkt(xy[2 * i], xy[2 * i + 1], t + 0.1 * z, pt);
                        boolean nass = pt.tiefe >= 0.5;
                        double cs = sigma * Math.sqrt(9.81 * Math.max(0.05, pt.tiefe));
                        double de = Math.abs(a[8 * i + 4 * z] - pt.etaAntrieb) / sigma, dq = Math.hypot(a[8 * i + 4 * z + 1] - pt.qx, a[8 * i + 4 * z + 2] - pt.qy) / cs;
                        if (z == 0) maxE = Math.max(maxE, de); else maxEv = Math.max(maxEv, de);
                        if (!nass) { maxTrocken = Math.max(maxTrocken, dq); etaTrocken = Math.max(etaTrocken, Math.abs(pt.etaAntrieb)); continue; }
                        if (z == 0) maxQ = Math.max(maxQ, dq); else maxQv = Math.max(maxQv, dq);
                        rmsQ += Math.hypot(pt.qx, pt.qy) / cs * Math.hypot(pt.qx, pt.qy) / cs;
                        nq++;
                        if (z == 0) nNass++;
                        int kl = pt.tiefe < 2 ? 0 : pt.tiefe < 5 ? 1 : 2;
                        lwSum[kl] += pt.lwAntrieb * pt.lwAntrieb;
                        lwN[kl]++;
                    }
                }
            }
        }
        A.info("Orte", f("%d je Zeit, davon mit mindestens 0,5 m Wasser %d (im Mittel über die Zeiten)", np, nNass / 3));
        A.info("Fluss der Referenz an nassen Orten", f("RMS |q| / (σ √(g h)) %.3f (Größe des Werts, gegen den gemessen wird)", Math.sqrt(rmsQ / Math.max(1, nq))));
        A.hoechstens("Stand des Antriebs (mit langer Welle): |Δη| / σ (alle 2 000 Orte, 3 Zeiten bis 2 Tage)", maxE, 2e-3, "");
        A.hoechstens("Fluss an Orten mit mindestens 0,5 m Wasser: |Δq| / (σ √(g h))", maxQ, 2e-3, "");
        A.hoechstens("mit Versatz der Zeit 0,1 s: |Δη| / σ", maxEv, 2e-3, "");
        A.hoechstens("mit Versatz der Zeit 0,1 s: |Δq| / (σ √(g h)), nasse Orte", maxQv, 2e-3, "");
        A.info("trockene Orte (Auskunft, früher mitgezählt)", f("|Δq| / (σ √(g · 0,05 m)) bis %.4f; |η des Antriebs| bis %.2f m (lange Welle bei der kleinsten Tiefe)", maxTrocken, etaTrocken));
        A.info("gebundene lange Welle im Antrieb, RMS / σ", f("Tiefe 0,5–2 m: %.3f (%d), 2–5 m: %.3f (%d), über 5 m: %.3f (%d)",
                Math.sqrt(lwSum[0] / Math.max(1, lwN[0])) / sigma, lwN[0], Math.sqrt(lwSum[1] / Math.max(1, lwN[1])) / sigma, lwN[1], Math.sqrt(lwSum[2] / Math.max(1, lwN[2])) / sigma, lwN[2]));
    }

    /** Auskunft: ∂η/∂t + ∇·q an Orten im feinen Gitter, linear (ohne Form, ohne lange Welle), zentrale Differenzen. */
    static void bilanz(Oberflaeche ob) {
        A.teil("2 Massenbilanz der Referenz (Auskunft)");
        boolean f0 = ob.mitForm, h0 = ob.mitHoehen, l0 = ob.mitLangwelle;
        ob.mitForm = false;
        ob.mitHoehen = false;
        ob.mitLangwelle = false;
        Random r = new Random(11);
        Oberflaeche.Punkt p = new Oberflaeche.Punkt();
        double dt = 0.02, dx = 0.1, sr = 0, se = 0, srL = 0, seL = 0;
        for (int i = 0; i < 400; i++) {
            double x = 465600 + 800 * r.nextDouble(), y = 6045600 + 800 * r.nextDouble(), t = 1000 + 50 * r.nextDouble();
            ob.punkt(x, y, t + dt, p); double e1 = p.zRe; ob.punkt(x, y, t - dt, p); double e0 = p.zRe;
            ob.punkt(x + dx, y, t, p); double qx1 = p.qx; ob.punkt(x - dx, y, t, p); double qx0 = p.qx;
            ob.punkt(x, y + dx, t, p); double qy1 = p.qy; ob.punkt(x, y - dx, t, p); double qy0 = p.qy;
            ob.punkt(x, y, t, p);
            double dedt = (e1 - e0) / (2 * dt), div = (qx1 - qx0) / (2 * dx) + (qy1 - qy0) / (2 * dx), res = dedt + div;
            sr += res * res; se += dedt * dedt;
            if (p.tiefe < 8) { srL += res * res; seL += dedt * dedt; }
        }
        A.info("RMS (∂η/∂t + ∇·q) / RMS ∂η/∂t im feinen Gitter (Tiefe 6,5–11 m)", f("%.3f; unter 8 m %.3f", Math.sqrt(sr / se), seL > 0 ? Math.sqrt(srL / seL) : Double.NaN));
        ob.mitForm = f0;
        ob.mitHoehen = h0;
        ob.mitLangwelle = l0;
    }

    static void verteilen(SeegangKarte karte) {
        A.teil("3 Verteilen auf die Randzellen");
        Geraet g = karte.geraet();
        int np = 64, nz = 300;
        double[] xy = new double[2 * np];
        for (int i = 0; i < np; i++) { xy[2 * i] = 466200 + 2 * i; xy[2 * i + 1] = 6046100; }
        Random r = new Random(5);
        float[] ziele = new float[8 * nz];
        for (int n = 0; n < nz; n++) {
            double a = 2 * Math.PI * r.nextDouble();
            ziele[8 * n] = n;
            ziele[8 * n + 1] = (float) ((np - 1) * r.nextDouble());
            ziele[8 * n + 2] = (float) (-2 + 1.5 * r.nextDouble());
            ziele[8 * n + 4] = (float) Math.cos(a); ziele[8 * n + 5] = (float) Math.sin(a);
            ziele[8 * n + 6] = (float) -Math.sin(a); ziele[8 * n + 7] = (float) Math.cos(a);
        }
        double wst = 0.42, bezug = 0.35;
        try (Randwelle rw = new Randwelle(g, g.konto("Randwelle V", 64L << 20), np, nz);
             Puffer ziel = Puffer.speicher(g, g.konto("Randwelle Z", 1L << 20), "Ziel", 16L * (nz + 10))) {
            rw.punkte(xy);
            rw.ziele(ziele);
            karte.zeit(777);
            Ablauf ab = new Ablauf(g);
            rw.rechne(ab, karte, 0, 0.05);
            rw.verteilen(ab, ziel, 10, 1, wst, bezug);
            g.rechnen.einmal(c -> ab.aufnehmen(c, null));
            float[] a = rw.lies(), z = ziel.floats(4 * (nz + 10));
            double m = 0;
            for (int n = 0; n < nz; n++) {
                float u = Math.max(0, Math.min(np - 1, ziele[8 * n + 1]));
                int i0 = Math.min((int) u, np - 2), i1 = i0 + 1;
                float w = u - i0;
                float[] v = new float[3];
                for (int k = 0; k < 3; k++) v[k] = a[8 * i0 + 4 + k] + (a[8 * i1 + 4 + k] - a[8 * i0 + 4 + k]) * w;
                double eta = wst + v[0], h = Math.max(0.05, eta - ziele[8 * n + 2]), ux = v[1] / h, uy = v[2] / h;
                double[] soll = {eta - bezug, ux * ziele[8 * n + 4] + uy * ziele[8 * n + 5], ux * ziele[8 * n + 6] + uy * ziele[8 * n + 7]};
                double gross = 1e-3 + Math.abs(soll[0]) + Math.hypot(soll[1], soll[2]);
                for (int k = 0; k < 3; k++) m = Math.max(m, Math.abs(z[4 * (10 + n) + k] - soll[k]) / gross);
            }
            A.hoechstens("Ziele der Karte gegen dieselbe Rechnung in Java, relativ", m, 1e-5, "");
        }
    }

    /** Seit S6c-1b: kleinster Anteil frei laufend mit Bezug (Teil 5b) [G]. */
    static final double FREI_MIN = 0.97;

    /** Teil 5: Rand mit erhaltener Richtung (seit S6c-1b). */
    static void richtungErhalten(SeegangKarte karte, Oberflaeche ob, double sigma) {
        A.teil("5 Rand mit erhaltener Richtung (S6c-1b)");
        int np = 129;
        double phiN = Math.toRadians(SeegangKarteCheck.WOHIN + 45), nx = Math.cos(phiN), ny = Math.sin(phiN), xm = 466000, ym = 6046000;
        double[] xy = new double[2 * np];
        for (int i = 0; i < np; i++) { double l = 2.0 * (i - np / 2); xy[2 * i] = xm - l * ny; xy[2 * i + 1] = ym + l * nx; }
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        ob.punkt(xm, ym, 0, pt);
        double hR = pt.tiefe;
        A.info("Gerade", f("%d Punkte zu 2 m durch (%.0f, %.0f), Normale %.0f° (Seegang nach %.0f°), Tiefe am Bezug %.2f m", np, xm, ym, Math.toDegrees(phiN), SeegangKarteCheck.WOHIN, hR));
        // (a) Karte gegen Referenz
        Geraet g = karte.geraet();
        double maxE = 0, maxQ = 0;
        try (Randwelle rw = new Randwelle(g, g.konto("Randwelle R", 64L << 20), np, 1)) {
            rw.punkte(xy);
            rw.bezug(xm, ym, hR);
            for (double t : new double[] {120.0, 3600.37, 2 * 86400 + 55.5}) {
                karte.zeit(t);
                Ablauf ab = new Ablauf(g);
                rw.rechne(ab, karte, 0, 0.1);
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                float[] a = rw.lies();
                for (int i = 0; i < np; i++)
                    for (int z = 0; z < 2; z++) {
                        ob.punktRand(xy[2 * i], xy[2 * i + 1], t + 0.1 * z, pt, xm, ym, hR);
                        double cs = sigma * Math.sqrt(9.81 * Math.max(0.05, pt.tiefe));
                        maxE = Math.max(maxE, Math.abs(a[8 * i + 4 * z] - pt.etaAntrieb) / sigma);
                        maxQ = Math.max(maxQ, Math.hypot(a[8 * i + 4 * z + 1] - pt.qx, a[8 * i + 4 * z + 2] - pt.qy) / cs);
                    }
            }
        }
        A.hoechstens("5a mit Bezug: Stand des Antriebs |Δη| / σ (129 Punkte, 3 Zeiten, mit Versatz 0,1 s)", maxE, 2e-3, "");
        A.hoechstens("5a mit Bezug: Fluss |Δq| / (σ √(g h))", maxQ, 2e-3, "");
        // (b) frei laufend, Referenz
        double[] mit = freiLaufend(ob, xy, xm, ym, hR), ohne = freiLaufend(ob, xy, xm, ym, 0);
        A.info("5b ohne Bezug (wie bis S6c-1, Auskunft)", f("frei laufend im Löser %.1f %% (bis auf eine Zelle %.1f %%; bis 0,2 Hz %.0f %%, 0,2–0,5 Hz %.0f %%, darüber %.0f %%); linear (|k_y| < k(h)) %.1f %%",
                100 * ohne[3], 100 * ohne[5], 100 * ohne[0], 100 * ohne[1], 100 * ohne[2], 100 * ohne[4]));
        A.info("5b mit Bezug, streng (Auskunft)", f("%.1f %% (bis 0,2 Hz %.1f %%, 0,2–0,5 Hz %.1f %%, darüber %.1f %%); linear %.1f %%", 100 * mit[3], 100 * mit[0], 100 * mit[1], 100 * mit[2], 100 * mit[4]));
        A.pruefe(mit[5] >= FREI_MIN, "5b mit Bezug: Anteil der Energie am Rand, der im Löser frei läuft (|k_y| < ω/√(g h_R) + 2π/L)", f("%.1f %%", 100 * mit[5]), f("≥ %.0f %%", 100 * FREI_MIN));
    }

    /**
     * Teil 5b: η des Antriebs der Referenz an den Punkten über 1 024 Zeiten zu 0,25 s (hR ≤ 0: ohne Bezug); Anteil der
     * Energie mit |k_y| &lt; ω/√(g hR) je Band (bis 0,2, 0,2–0,5 Hz, darüber), gesamt, gesamt mit |k_y| &lt; k(hR) und gesamt
 * mit |k_y| &lt; ω/√(g hR) + 2π/L (eine Zelle der Auflösung der Geraden der Länge L: Hann lässt Energie über die Grenze
 * laufen, an langen Wellen mit kleinem ω/√(g hR) am meisten); Hann in
     * Zeit und Ort, längs auf das Doppelte aufgefüllt (wie {@code AuflaufMassstabCheck.freiLaufend}).
     */
    static double[] freiLaufend(Oberflaeche ob, double[] xy, double xm, double ym, double hR) {
        int np = xy.length / 2, nt = 1024, m = 2 * Integer.highestOneBit(2 * np - 1);
        double dt = 0.25, dy = 2, h = hR > 0 ? hR : 0;
        Oberflaeche.Punkt pt = new Oberflaeche.Punkt();
        if (!(h > 0)) { ob.punkt(xm, ym, 0, pt); h = pt.tiefe; }
        double[][] re = new double[np][nt], im = new double[np][nt];
        for (int p = 0; p < np; p++) {
            double me = 0;
            for (int a = 0; a < nt; a++) {
                double t = 500 + a * dt;
                if (hR > 0) ob.punktRand(xy[2 * p], xy[2 * p + 1], t, pt, xm, ym, hR); else ob.punkt(xy[2 * p], xy[2 * p + 1], t, pt);
                re[p][a] = pt.etaAntrieb;
                me += pt.etaAntrieb / nt;
            }
            for (int a = 0; a < nt; a++) re[p][a] = (re[p][a] - me) * (0.5 - 0.5 * Math.cos(2 * Math.PI * a / (nt - 1)));
            fft(re[p], im[p]);
        }
        double[] frei = new double[3], alle = new double[3], xr = new double[m], xi = new double[m];
        double lin = 0, ges = 0, freiA = 0, dk = 2 * Math.PI / (np * dy);
        for (int kb = 1; kb < nt / 2; kb++) {
            double f = kb / (nt * dt);
            if (f < 0.04 || f > 2) continue;
            java.util.Arrays.fill(xr, 0);
            java.util.Arrays.fill(xi, 0);
            for (int p = 0; p < np; p++) { double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * p / (np - 1)); xr[p] = w * re[p][kb]; xi[p] = w * im[p][kb]; }
            fft(xr, xi);
            double om = 2 * Math.PI * f, kSw = om / Math.sqrt(9.81 * h), kLin = com.dan.core.szene.Dispersion.wellenzahl(om, h);
            int band = f < 0.2 ? 0 : f < 0.5 ? 1 : 2;
            for (int j = 0; j < m; j++) {
                double ky = 2 * Math.PI * (j <= m / 2 ? j : j - m) / (m * dy), e = xr[j] * xr[j] + xi[j] * xi[j];
                alle[band] += e; ges += e;
                if (Math.abs(ky) < kSw) frei[band] += e;
                if (Math.abs(ky) < kSw + dk) freiA += e;
                if (Math.abs(ky) < kLin) lin += e;
            }
        }
        return new double[] {frei[0] / alle[0], frei[1] / alle[1], frei[2] / alle[2], (frei[0] + frei[1] + frei[2]) / ges, lin / ges, freiA / ges};
    }

    /** FFT (Radix 2, an Ort und Stelle). */
    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    double ur = re[a], ui = im[a], vr = re[b] * cr - im[b] * ci, vi = re[b] * ci + im[b] * cr;
                    re[a] = ur + vr; im[a] = ui + vi; re[b] = ur - vr; im[b] = ui - vi;
                    double nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr;
                }
            }
        }
    }

    static void kosten(SeegangKarte karte) {
        A.teil("4 Kosten auf der Karte");
        Geraet g = karte.geraet();
        int np = 129;
        double[] xy = new double[2 * np];
        for (int i = 0; i < np; i++) { xy[2 * i] = 465800 + 2 * i * 0.6; xy[2 * i + 1] = 6045900 + 2 * i * 0.8; }
        try (Randwelle rw = new Randwelle(g, g.konto("Randwelle K", 64L << 20), np, 1); Stoppuhr uhr = new Stoppuhr(g, g.rechnen, 8)) {
            rw.punkte(xy);
            int runden = 0;
            for (int q = 0; q < 24; q++) {
                karte.zeit(300 + q / 60.0);
                Ablauf ab = new Ablauf(g);
                rw.rechne(ab, karte, 0, 0, 1);
                int qq = q;
                g.rechnen.einmal(c -> { if (qq >= 4) uhr.runde(c); ab.aufnehmen(c, qq >= 4 ? uhr : null); });
                if (q >= 4) { uhr.sammle(true); runden++; }
            }
            if (!uhr.verfuegbar) { A.info("Zeitstempel", "nicht verfügbar"); return; }
            double ms = uhr.summen().getOrDefault("see_antrieb", 0.0) / runden;
            String name = g.name.toLowerCase(java.util.Locale.ROOT);
            boolean software = name.contains("llvmpipe") || name.contains("lavapipe") || name.contains("swiftshader");
            if (software) A.info("129 Punkte zu einer Zeit (Software-Gerät, nur Auskunft)", f("%.3f ms", ms));
            else A.hoechstens("Kosten: 129 Punkte zu einer Zeit", ms, 0.25, "ms");
        }
    }
}
