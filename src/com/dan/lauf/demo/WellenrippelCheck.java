package com.dan.lauf.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Kornfeld;
import com.dan.core.gpu.Geraet;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.lauf.Wellenrippelfeld;
import com.dan.physics.sediment.Wellenrippel;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * S6d-1 Wellenrippel (Seegang Werkbuch 84): Prüfsteine.
 * <ol>
 * <li>Formeln (ohne Daten): orbital, anorbital, suborbital, Grenzen, Steilheit, Dispersion.</li>
 * <li>Das Feld aus dem Archiv (Szenarien {@code sediment} und {@code ruhigsed}): Stand für Stand des Seegangs gegen eine
 * unabhängige Rechnung aus denselben Ständen (Länge und Höhe ± 1 %, Richtung, gleiche Regel); dazu Auskunft über die
 * Zustände und die Kosten je Stand.</li>
 * <li>Das Bild: von oben auf eine trockene Zelle mit Wellenrippeln bei Tag; Länge und Richtung aus dem Spektrum des
 * Unterschieds mit und ohne Wellenrippel (± 10 %, ± 10°); Status-Bild für die Sichtprüfung.</li>
 * </ol>
 * Attest {@code docs/status/S6d1_Wellenrippel.md}, Status-Bild {@code docs/status/S6d1_Bild.png}.
 */
public final class WellenrippelCheck {
    private WellenrippelCheck() { }

    static final Attest A = new Attest("Seegang, Wellenrippel (S6d-1)", "Seegang Werkbuch");
    static final int BREITE = 1920, HOEHE = 1080;
    /** Grenzen der Prüfsteine [G, vor der Messung]. */
    static final double FELD_REL = 0.01, BILD_REL = 0.10, BILD_GRAD = 10, HOCH_MS = 30;

    record Wahl(String szenario, File archiv, double t, int trockenMitRippeln) { }

    public static void main(String[] args) {
        File szenen = null;
        String[] namen = {"sediment", "ruhigsed"};
        for (int i = 0; i + 1 < args.length; i += 2)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--szenarien" -> namen = args[i + 1].split(",");
                default -> { }
            }
        System.out.println("WellenrippelCheck – S6d-1 (Seegang Werkbuch 84)");
        long start = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            File gb = sz.gebiet();
            teil1();
            Wahl beste = null;
            for (String nm : namen) {
                Szenario sc = null;
                for (Szenario x : sz.szenarien()) if (x.schluessel.equals(nm)) sc = x;
                if (sc == null || sc.archiv == null) {
                    A.info("Szenario " + nm, "fehlt oder ohne Archiv");
                    continue;
                }
                Wahl w = teil2(nm, sc.archiv, gb);
                if (w != null && (beste == null || w.trockenMitRippeln > beste.trockenMitRippeln)) beste = w;
            }
            teil3(sz, gb, beste);
        } catch (Throwable e) {
            e.printStackTrace();
            A.pruefe(false, "Lauf ohne Abbruch", e.toString(), "ohne");
        }
        A.info("Dauer", f("%.0f s", (System.nanoTime() - start) / 1e9));
        Path p = A.schreibe(Path.of("docs", "status", "S6d1_Wellenrippel.md"), "S6d-1 – WellenrippelCheck (Wellenrippel nach Wiberg und Harris 1994)");
        System.out.println("Attest: " + p);
        System.exit(0);
    }

    // ------------------------------------------------------------------ Teil 1: Formeln

    static void teil1() {
        A.teil("1 Formeln");
        double d50 = 2e-4;
        // orbital
        double d0 = 1000 * d50;
        double[] g = Wellenrippel.geometrie(d0, d50);
        double e1 = Math.max(Math.abs(g[1] / (0.62 * d0) - 1), Math.abs(g[0] / (0.17 * g[1]) - 1));
        A.pruefe(e1 <= 1e-12 && g[2] == 0, "orbital (d₀/D50 = 1000): λ = 0,62 d₀, η = 0,17 λ", f("λ %.4f m, η %.4f m, Fehler %.1e", g[1], g[0], e1), "≤ 1e-12");
        // anorbital: Länge und Steilheit
        d0 = 8000 * d50;
        g = Wellenrippel.geometrie(d0, d50);
        double st = g[0] / g[1], soll = Wellenrippel.steilheit(d0 / g[0]);
        double e2 = Math.max(Math.abs(g[1] / (535 * d50) - 1), Math.abs(st / soll - 1));
        A.pruefe(e2 <= 1e-9 && g[2] == 2, "anorbital (d₀/D50 = 8000): λ = 535 D50, η/λ nach der Steilheit", f("λ %.4f m, η %.4f m (η/λ %.3f, d₀/η %.0f), Fehler %.1e", g[1], g[0], st, d0 / g[0], e2), "≤ 1e-9");
        // suborbital in der Mitte (logarithmisch): λ = √(λ_orb λ_ano)
        double r = Math.sqrt(Wellenrippel.ORBITAL_BIS * Wellenrippel.ANORBITAL_AB);
        d0 = r * d50;
        g = Wellenrippel.geometrie(d0, d50);
        double lm = Math.sqrt(0.62 * d0 * 535 * d50), e3 = Math.max(Math.abs(g[1] / lm - 1), Math.abs(g[0] / g[1] / Wellenrippel.steilheit(d0 / g[0]) - 1));
        A.pruefe(e3 <= 1e-9 && g[2] == 1, f("suborbital in der Mitte (d₀/D50 = %.0f): λ = √(λ_orb λ_ano), Steilheit", r), f("λ %.4f m, η %.4f m, Fehler %.1e", g[1], g[0], e3), "≤ 1e-9");
        // Grenzen: Länge stetig, Höhe springt wenig
        double[] a = Wellenrippel.geometrie((Wellenrippel.ORBITAL_BIS - 1e-6) * d50, d50), b = Wellenrippel.geometrie((Wellenrippel.ORBITAL_BIS + 1e-6) * d50, d50);
        double[] c = Wellenrippel.geometrie((Wellenrippel.ANORBITAL_AB - 1e-6) * d50, d50), d = Wellenrippel.geometrie((Wellenrippel.ANORBITAL_AB + 1e-6) * d50, d50);
        double sl = Math.max(Math.abs(b[1] / a[1] - 1), Math.abs(d[1] / c[1] - 1)), sh = Math.max(Math.abs(b[0] / a[0] - 1), Math.abs(d[0] / c[0] - 1));
        A.pruefe(sl <= 1e-6 && sh <= 0.01, "an den Grenzen (1754, 5587): Sprung der Länge, der Höhe", f("%.1e, %.2f %%", sl, 100 * sh), "≤ 1e-6, ≤ 1 %");
        // Dispersion gegen Bisektion, U_w gegen die lineare Theorie
        double ek = 0, eu = 0;
        for (double t : new double[] {1.5, 2.5, 4, 6, 9, 14})
            for (double h : new double[] {0.05, 0.2, 0.7, 2, 6, 20}) {
                double om = 2 * Math.PI / t, k = Wellenrippel.wellenzahl(om, h), kb = bisektion(om, h);
                ek = Math.max(ek, Math.abs(k / kb - 1));
                double u = Wellenrippel.uw(1, t, h), ub = Math.PI / (t * Math.sinh(kb * h));
                if (kb * h < 20) eu = Math.max(eu, Math.abs(u / ub - 1));
            }
        A.pruefe(ek <= 1e-10 && eu <= 1e-9, "Dispersion (Guo und Newton) gegen Bisektion; U_w = π H/(T sinh kh)", f("%.1e; %.1e", ek, eu), "≤ 1e-10; ≤ 1e-9");
        A.info("θ_cr bei D50 0,1 / 0,2 / 0,4 mm", f("%.4f / %.4f / %.4f", Wellenrippel.thetaKrit(1e-4), Wellenrippel.thetaKrit(2e-4), Wellenrippel.thetaKrit(4e-4)));
        double[] bsp = Wellenrippel.geometrie(Wellenrippel.d0(Wellenrippel.uw(0.3, 3, 1), 3), 1.5e-4);
        A.info("Beispiel Watt: H_s 0,3 m, T_p 3 s, h 1 m, D50 0,15 mm", f("U_w %.3f m/s, d₀ %.3f m, λ %.3f m, η %.1f mm, Klasse %.0f, θ_w %.3f", Wellenrippel.uw(0.3, 3, 1),
                Wellenrippel.d0(Wellenrippel.uw(0.3, 3, 1), 3), bsp[1], 1000 * bsp[0], bsp[2], Wellenrippel.thetaWelle(Wellenrippel.uw(0.3, 3, 1), 3, 1.5e-4)));
    }

    static double bisektion(double om, double h) {
        double lo = 1e-9, hi = 1e4, y = om * om / Wellenrippel.G;
        for (int n = 0; n < 300; n++) {
            double m = 0.5 * (lo + hi);
            if (m * Math.tanh(m * h) < y) lo = m;
            else hi = m;
        }
        return 0.5 * (lo + hi);
    }

    // ------------------------------------------------------------------ Teil 2: das Feld aus dem Archiv

    static Wahl teil2(String name, File archiv, File gb) throws Exception {
        A.teil("2 Feld aus dem Archiv: " + name);
        try (Archivquelle q = new Archivquelle(archiv.toPath())) {
            Gitter sg = q.sedimentGitter();
            if (sg == null || q.seegangGitter().isEmpty()) {
                A.info("Archiv " + archiv, "ohne Boden oder ohne Seegang – übersprungen");
                return null;
            }
            Kornfeld korn = Kornfeld.vorhanden(gb) ? Kornfeld.lies(gb) : null;
            Wellenrippelfeld w = new Wellenrippelfeld(sg, korn, q);
            double[] zs = q.seegangZeiten();
            Instant t0 = q.start;
            A.info("Archiv", f("%s: Boden %s, Seegang %s, %d Stände des Seegangs (%s … %s), Strömung %s, Korn %s", archiv.getName(), sg, q.seegangGitter(), zs.length,
                    BildCheck.zeit(t0, zs[0]), BildCheck.zeit(t0, zs[zs.length - 1]), q.stroemungGitter() != null ? q.stroemungGitter().name : "keine", korn != null ? "aus dem Kornfeld" : "Sand 0,2 mm"));
            // unabhängige Rechnung: eigene Felder
            List<Gitter> seeG = q.seegangGitter();
            float[][][] see = new float[seeG.size()][][];
            for (int k = 0; k < see.length; k++) see[k] = new float[5][seeG.get(k).zellen()];
            int kw = -1;
            List<Gitter> alle = q.gitter();
            for (int k = 0; k < alle.size(); k++) {
                Gitter g = alle.get(k);
                if (g.lage == Gitter.Lage.UTM && g.x0 <= sg.x0 + 1e-6 && g.y0 <= sg.y0 + 1e-6 && g.x0 + g.nx * g.dx >= sg.x0 + sg.nx * sg.dx - 1e-6
                        && g.y0 + g.ny * g.dy >= sg.y0 + sg.ny * sg.dy - 1e-6 && (kw < 0 || g.dx < alle.get(kw).dx)) kw = k;
            }
            Gitter gw = alle.get(kw);
            float[] eta = new float[gw.zellen()], h = new float[gw.zellen()], age = new float[gw.zellen()];
            Gitter gs = q.stroemungGitter();
            float[] u = gs != null ? new float[gs.zellen()] : null, v = gs != null ? new float[gs.zellen()] : null;
            double eL = 0, eH = 0, eR = 0, dauer = 0, dauerMax = 0;
            long verglichen = 0, regelAnders = 0, ohneSchritt = 0;
            double tWahl = Double.NaN;
            int wahl = -1;
            int[] summe = new int[5];
            StringBuilder verlauf = new StringBuilder();
            for (int s = 0; s < zs.length; s++) {
                double ts = zs[s];
                if (!w.schritt(q, ts)) {
                    ohneSchritt++;
                    continue;
                }
                dauer += w.letzteDauer;
                if (s > 0) dauerMax = Math.max(dauerMax, w.letzteDauer);
                for (int k = 0; k < 5; k++) summe[k] += w.zahl[k];
                // unabhängig: dieselben Stände selbst gelesen
                for (int k = 0; k < see.length; k++) q.seegang(ts, k, see[k]);
                double tw = Double.NaN;
                for (double x : q.zeiten()) if (x <= ts) tw = x;
                q.stand(tw, kw, eta, h, age);
                boolean mitStrom = false;
                if (gs != null) {
                    double tst = Double.NaN;
                    for (double x : q.stroemungZeiten()) if (x <= ts) tst = x;
                    mitStrom = !Double.isNaN(tst) && ts - tst <= 3600 && q.stroemung(tst, u, v);
                }
                for (int c = 0; c < w.n; c++) {
                    double x = sg.x(c % sg.nx), y = sg.y(c / sg.nx);
                    int iw = BildCheck.zelle(gw, x, y);
                    double hh = h[iw];
                    if (w.fest[c] || !(hh >= Wellenrippelfeld.H_MIN)) continue;
                    double[] sv = seegangUnabhaengig(seeG, see, x, y);
                    double hs = Math.min(sv[0], Wellenrippelfeld.BRECHEN * hh), tp = sv[1];
                    double uw = Wellenrippel.uw(hs, tp, hh), thw = Wellenrippel.thetaWelle(uw, tp, w.d50[c]);
                    double us = 0;
                    if (mitStrom) {
                        int ic = BildCheck.zelle(gs, x, y);
                        us = Math.hypot(u[ic], v[ic]);
                    }
                    double thc = Wellenrippel.thetaStrom(us, hh, w.d50[c]);
                    int r = Wellenrippel.regel(thw, thc, Wellenrippel.thetaKrit(w.d50[c]));
                    verglichen++;
                    if (r != w.zustand[c]) {
                        regelAnders++;
                        continue;
                    }
                    if (r != Wellenrippel.FORMT) continue;
                    double[] geo = Wellenrippel.geometrie(uw * tp / Math.PI, w.d50[c]);
                    double hoch = geo[0] * Math.max(0, 1 - w.schlick[c] / Wellenrippelfeld.SCHLICK_GRENZE);
                    eL = Math.max(eL, Math.abs(w.laenge[c] / geo[1] - 1));
                    if (hoch > 1e-6) eH = Math.max(eH, Math.abs(w.eta[c] / hoch - 1));
                    double dr = Math.abs(Math.toDegrees(w.richtung[c] - sv[2]));
                    dr = Math.min(dr % 360, 360 - dr % 360);
                    eR = Math.max(eR, dr);
                }
                // trockene Zellen mit Rippeln bei Tag (für das Bild)
                int std = t0.plusMillis(Math.round(ts * 1000)).atZone(ZoneOffset.UTC).getHour();
                int tr = 0;
                for (int c = 0; c < w.n; c++) if (w.zustand[c] == -1 && !w.fest[c] && w.eta[c] > 0.003f) tr++;
                if (std >= 9 && std <= 13 && tr > wahl) {
                    wahl = tr;
                    tWahl = ts;
                }
                if (s % 6 == 0 || s == zs.length - 1)
                    verlauf.append(f("%s: trocken %d, bleibt %d, formt %d, Schichtfluss %d, Strom %d; mit Rippeln %d (trocken %d)%n", BildCheck.zeit(t0, ts), w.zahl[0], w.zahl[1], w.zahl[2], w.zahl[3], w.zahl[4], w.mitRippeln(), tr));
            }
            int schritte = zs.length - (int) ohneSchritt;
            A.pruefe(ohneSchritt == 0, "Stände des Seegangs mit Wasser gerechnet", f("%d von %d", schritte, zs.length), "alle");
            A.pruefe(regelAnders <= verglichen / 1000, "Regel (formt, bleibt, Schichtfluss, Strom) gegen die unabhängige Rechnung", f("%d von %d nassen Zellen anders", regelAnders, verglichen), "≤ 0,1 %");
            A.pruefe(eL <= FELD_REL && eH <= FELD_REL, "Prüfstein 1: Feld gegen die Formel (Länge, Höhe), über alle Stände und formenden Zellen", f("%.2e, %.2e", eL, eH), "≤ 1 %");
            A.pruefe(eR <= 0.5, "Richtung des Felds gegen die unabhängige Rechnung", f("%.3f°", eR), "≤ 0,5°");
            A.info("Rechnung eines Stands des Gedächtnisses (seit L4u nebenher in einem eigenen Faden; ohne den ersten)", f("im Mittel %.1f ms, höchstens %.1f ms", 1000 * dauer / Math.max(1, schritte), 1000 * dauerMax));
            A.info("Zustände je Stand im Mittel", f("trocken %d, bleibt %d, formt %d, Schichtfluss %d, Strom %d", summe[0] / Math.max(1, schritte), summe[1] / Math.max(1, schritte), summe[2] / Math.max(1, schritte),
                    summe[3] / Math.max(1, schritte), summe[4] / Math.max(1, schritte)));
            A.info("Verlauf (jeder 6. Stand)", verlauf.toString().trim().replace("\n", "<br>"));
            // Verteilung am Ende
            List<Float> ls = new ArrayList<>(), hsl = new ArrayList<>();
            int[] kl = new int[3];
            for (int c = 0; c < w.n; c++)
                if (w.eta[c] > 0 && w.laenge[c] > 0) {
                    ls.add(w.laenge[c]);
                    hsl.add(w.eta[c]);
                    if (w.klasse[c] >= 0) kl[w.klasse[c]]++;
                }
            if (!ls.isEmpty()) {
                float[] la = toArr(ls), ha = toArr(hsl);
                A.info("Rippel am Ende: Länge (m) 5 / 50 / 95 %, Höhe (mm) 5 / 50 / 95 %, Klassen orbital / suborbital / anorbital",
                        f("%.3f / %.3f / %.3f; %.1f / %.1f / %.1f; %d / %d / %d", q(la, 0.05), q(la, 0.5), q(la, 0.95), 1000 * q(ha, 0.05), 1000 * q(ha, 0.5), 1000 * q(ha, 0.95), kl[0], kl[1], kl[2]));
            }
            if (Double.isNaN(tWahl)) {
                A.info("Bild", "keine trockene Zelle mit Wellenrippeln bei Tag (9–13 UTC)");
                return null;
            }
            A.info("Bild: Niedrigwasser bei Tag mit den meisten trockenen Zellen mit Rippeln", f("%s: %d Zellen", BildCheck.zeit(t0, tWahl), wahl));
            return new Wahl(name, archiv, tWahl, wahl);
        }
    }

    /** H_s, T_p, Richtung (rad, wohin) am Ort: das feinste Gitter, dessen Mitten den Ort umschließen; bilinear über die nassen Zellen. */
    static double[] seegangUnabhaengig(List<Gitter> gl, float[][][] see, double x, double y) {
        int best = -1;
        for (int k = 0; k < gl.size(); k++) {
            Gitter g = gl.get(k);
            double a = (x - g.x0) / g.dx - 0.5, b = (y - g.y0) / g.dy - 0.5;
            if (a >= 0 && b >= 0 && a <= g.nx - 1 && b <= g.ny - 1 && (best < 0 || g.dx < gl.get(best).dx)) best = k;
        }
        if (best < 0) return new double[] {0, 0, 0};
        Gitter g = gl.get(best);
        double a = (x - g.x0) / g.dx - 0.5, b = (y - g.y0) / g.dy - 0.5;
        int i = Math.min((int) a, g.nx - 2), j = Math.min((int) b, g.ny - 2);
        double fa = a - i, fb = b - j, sw = 0, sh = 0, st = 0, sx = 0, sy = 0;
        for (int dj = 0; dj < 2; dj++)
            for (int di = 0; di < 2; di++) {
                double wq = (di == 0 ? 1 - fa : fa) * (dj == 0 ? 1 - fb : fb);
                int z = (i + di) + g.nx * (j + dj);
                float hs = see[best][0][z], tp = see[best][1][z];
                if (wq <= 0 || !(hs > 0) || !(tp > 0)) continue;
                double th = Math.toRadians(270 - see[best][2][z]);
                sw += wq;
                sh += wq * hs;
                st += wq * tp;
                sx += wq * Math.cos(th);
                sy += wq * Math.sin(th);
            }
        if (sw <= 0) return new double[] {0, 0, 0};
        return new double[] {sh / sw, st / sw, Math.atan2(sy, sx)};
    }

    static float[] toArr(List<Float> l) {
        float[] a = new float[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        Arrays.sort(a);
        return a;
    }

    static double q(float[] a, double p) { return a[Math.min(a.length - 1, (int) Math.floor(p * a.length))]; }

    // ------------------------------------------------------------------ Teil 3: das Bild

    static void teil3(Szenen szn, File gb, Wahl wahl) throws Exception {
        A.teil("3 Bild");
        if (wahl == null) {
            A.pruefe(false, "Prüfstein 2/3: trockene Zelle mit Wellenrippeln bei Tag", "keine", "vorhanden");
            return;
        }
        Gebiet ge = Gebiet.lies(gb);
        try (Geraet g = new Geraet(); Archivquelle q = new Archivquelle(wahl.archiv.toPath());
             BildCheck.Szene sz = new BildCheck.Szene(g, g.konto("WellenrippelCheck", 3L << 30), gb, ge, q, BREITE, HOEHE)) {
            Instant t0 = q.start;
            double t = wahl.t;
            var gel = sz.gelaende.gelaende;
            // erst das Bild an den Ort bringen (rechnet das Gedächtnis mit Vorlauf)
            Ansicht japs = szn.ansicht("japsand");
            sz.bild.wellenSofort = true;
            long n0 = System.nanoTime();
            sz.bild.zu(t, japs != null ? japs.ost : 468000, japs != null ? japs.nord : 6046000);
            double erst = (System.nanoTime() - n0) * 1e-9;
            Wellenrippelfeld w = sz.bild.wellenrippel();
            A.info("Erster Aufruf des Bilds zur Zeit (mit Vorlauf des Gedächtnisses)", f("%.2f s", erst));
            A.pruefe(w != null, "Gedächtnis der Wellenrippel im Bild", w == null ? "keins" : f("%s, Vorlauf %.0f h, %d Stände, letzter Stand %.0f ms", w.gitter, Wellenrippelfeld.VORLAUF / 3600, w.schritte, 1000 * w.letzteDauer), "vorhanden");
            if (w == null) return;
            Gitter sg = w.gitter;
            int kw = -1;
            for (int k = 0; k < q.gitter().size(); k++) if (q.gitter().get(k).name.equals("G3")) kw = k;
            Gitter gw = q.gitter().get(kw);
            float[] eta = new float[gw.zellen()], h = new float[gw.zellen()], age = new float[gw.zellen()];
            double tw = Double.NaN;
            for (double x : q.zeiten()) if (x <= t) tw = x;
            q.stand(tw, kw, eta, h, age);
            // Zelle: trocken, im Watt eben gefallen, Rippel mit Nachbarn ähnlicher Länge und Richtung; die höchsten
            int best = -1;
            double bestWert = 0;
            int rand = 5;
            for (int j = rand; j < sg.ny - rand; j++)
                for (int i = rand; i < sg.nx - rand; i++) {
                    int c = sg.index(i, j);
                    if (w.fest[c] || !(w.eta[c] > 0.003f) || w.laenge[c] < 0.05f || w.laenge[c] > 0.6f) continue;
                    int iw = BildCheck.zelle(gw, sg.x(i), sg.y(j));
                    if (!(h[iw] < 0.02f) || !(age[iw] < 0) || age[iw] < -8 * 3600) continue;
                    boolean gut = true;
                    for (int dj = -1; dj <= 1 && gut; dj++)
                        for (int di = -1; di <= 1 && gut; di++) {
                            int n = sg.index(i + di, j + dj);
                            double dr = Math.abs(Math.toDegrees(w.richtung[n] - w.richtung[c])) % 180;
                            dr = Math.min(dr, 180 - dr);
                            gut = w.eta[n] > 0 && Math.abs(w.laenge[n] / w.laenge[c] - 1) < 0.1 && dr < 10;
                        }
                    if (gut && w.eta[c] > bestWert) {
                        bestWert = w.eta[c];
                        best = c;
                    }
                }
            A.pruefe(best >= 0, "Prüfstein 2/3: trockene Zelle mit Wellenrippeln bei Tag, Nachbarn ähnlich", best < 0 ? "keine" : f("%s, %s", wahl.szenario, BildCheck.zeit(t0, t)), "vorhanden");
            if (best < 0) return;
            double x = sg.x(best % sg.nx), y = sg.y(best / sg.nx), lam = w.laenge[best], dirW = Math.toDegrees(w.richtung[best]);
            A.info("Zelle", f("O %.0f, N %.0f: λ %.3f m, η %.1f mm, Klasse %d, Richtung der Welle %.1f° (math.), geformt %s bei H_s %.2f m, T_p %.1f s, h %.2f m, θ_w %.3f, D50 %.2f mm",
                    x, y, lam, 1000 * w.eta[best], w.klasse[best], dirW, BildCheck.zeit(t0, w.zeit[best]), w.hs[best], w.tp[best], w.tiefe[best], w.thetaW[best], 1000 * w.d50[best]));
            double z = gel.hoehe(x, y, 1);
            double H = Math.max(1.0, 16.8 * lam);
            Blick oben = new Blick(x, y, z + H, 0, -90, 50).groesse(BREITE, HOEHE);
            sz.gelaende.wellenrippel = true;
            float[] mit = bild(sz, oben, t0, t);
            sz.gelaende.wellenrippel = false;
            float[] ohne = bild(sz, oben, t0, t);
            sz.gelaende.wellenrippel = true;
            // Spektrum des Unterschieds auf einem Feld von 12 λ, Schritt λ/16, mit Hann-Fenster; null aufgefüllt auf 1024
            int m = 192, nf = 1024;
            double d = lam / 16, half = m * d / 2;
            double[] re = new double[nf * nf], im = new double[nf * nf];
            double mittel = 0;
            double[] sp = new double[m * m];
            int aussen = 0;
            for (int b = 0; b < m; b++)
                for (int a = 0; a < m; a++) {
                    double ox = -half + (a + 0.5) * d, oy = -half + (b + 0.5) * d;
                    double[] px = oben.bildpunkt(oben.relativ(x + ox, y + oy, gel.hoehe(x + ox, y + oy, 1)));
                    double lu = px == null ? Double.NaN : hell(mit, px[0], px[1]) - hell(ohne, px[0], px[1]);
                    if (Double.isNaN(lu)) { aussen++; lu = 0; }
                    sp[a + m * b] = lu;
                    mittel += lu;
                }
            mittel /= m * m;
            for (int b = 0; b < m; b++)
                for (int a = 0; a < m; a++) {
                    double wa = 0.5 - 0.5 * Math.cos(2 * Math.PI * (a + 0.5) / m), wb = 0.5 - 0.5 * Math.cos(2 * Math.PI * (b + 0.5) / m);
                    re[a + nf * b] = (sp[a + m * b] - mittel) * wa * wb;
                }
            fft2(re, im, nf);
            // Gipfel außerhalb der tiefen Frequenzen (|k| > 0,4/λ), eine Halbebene
            double kMin = 0.4 / lam, kMax = 3.0 / lam, df = 1 / (nf * d), pmax = -1;
            int pa = 0, pb = 0;
            for (int b = 0; b < nf; b++)
                for (int a = 0; a < nf / 2; a++) {
                    int kb = b < nf / 2 ? b : b - nf;
                    double kx = a * df, ky = kb * df, kk = Math.hypot(kx, ky);
                    if (kk < kMin || kk > kMax) continue;
                    double p = re[a + nf * b] * re[a + nf * b] + im[a + nf * b] * im[a + nf * b];
                    if (p > pmax) { pmax = p; pa = a; pb = kb; }
                }
            double ka = pa * df + parabel(re, im, nf, pa, pb, 1, 0) * df, kbb = pb * df + parabel(re, im, nf, pa, pb, 0, 1) * df;
            double lamB = 1 / Math.hypot(ka, kbb), dirB = Math.toDegrees(Math.atan2(kbb, ka));
            double dd = Math.abs(dirB - dirW) % 180;
            dd = Math.min(dd, 180 - dd);
            double eLam = lamB / lam - 1;
            A.info("Unterschied mit/ohne Wellenrippel von oben", f("Auge %.2f m über dem Boden, Feld %.2f × %.2f m, %d Punkte außerhalb des Bilds; Kontrast (Std.-Abw. der Helligkeit) %.4f", H, m * d, m * d, aussen, std(sp)));
            A.pruefe(Math.abs(eLam) <= BILD_REL, "Prüfstein 2: Länge im Bild aus dem Spektrum gegen das Feld", f("%.3f m gegen %.3f m (%+.1f %%)", lamB, lam, 100 * eLam), "± 10 %");
            A.pruefe(dd <= BILD_GRAD, "Prüfstein 3: Richtung quer zu den Kämmen im Bild gegen die Welle (mod 180°)", f("%.1f° gegen %.1f° (Δ %.1f°)", ((dirB % 180) + 180) % 180, ((dirW % 180) + 180) % 180, dd), "± 10°");
            // Sichtprüfung: von oben mit und ohne, schräg über die Kämme, und weiter
            double az = 90 - dirW;                                  // Azimut längs der Welle
            Blick schraeg = new Blick(x - 1.5 * Math.sin(Math.toRadians(az)), y - 1.5 * Math.cos(Math.toRadians(az)), z + 1.2, az, -22, 50).groesse(BREITE, HOEHE);
            float[] bS = bild(sz, schraeg, t0, t);
            Blick weit = new Blick(x - 6 * Math.sin(Math.toRadians(az + 30)), y - 6 * Math.cos(Math.toRadians(az + 30)), z + 1.7, az + 30, -12, 55).groesse(BREITE, HOEHE);
            float[] bW = bild(sz, weit, t0, t);
            String zt = BildCheck.zeit(t0, t);
            nebenher(sz, q, t, x, y);
            statusbild(new float[][] {mit, ohne, bS, bW}, new String[] {
                    f("von oben, %.1f m, mit Wellenrippeln (λ %.0f mm) · %s", H, 1000 * lam, zt), "dieselbe Sicht ohne Wellenrippel",
                    "1,2 m über dem Boden längs der Welle", "1,7 m, schräg zu den Kämmen"});
        }
    }

    /**
     * Seit L4u: das Gedächtnis nebenher (wie in der Anwendung): die nächsten Stände des Seegangs nach t, je Stand Aufrufe
     * von {@code Bild.zu} im Abstand von 5 ms, bis das Ergebnis im Gelände ist. Prüfstein: das Hochladen im Faden des
     * Bilds; Auskunft: der längste Aufruf von zu und wie viele Aufrufe ein Stand dauerte.
     */
    static void nebenher(BildCheck.Szene sz, Archivquelle q, double t, double x, double y) throws Exception {
        sz.bild.wellenSofort = false;
        double[] zs = q.seegangZeiten();
        int i0 = 0;
        while (i0 < zs.length && zs[i0] <= t) i0++;
        double zuMax = 0;
        int stände = 0, aufrufeMax = 0;
        long h0 = sz.bild.wellenHoch;
        sz.bild.wellenHochMaxS = 0;
        for (int i = i0; i < Math.min(zs.length, i0 + 6); i++) {
            long h = sz.bild.wellenHoch;
            int k = 0;
            for (; k < 400 && sz.bild.wellenHoch == h; k++) {
                long n0 = System.nanoTime();
                sz.bild.zu(zs[i] + 1, x, y);
                zuMax = Math.max(zuMax, (System.nanoTime() - n0) * 1e-9);
                Thread.sleep(5);
            }
            if (sz.bild.wellenHoch > h) stände++;
            aufrufeMax = Math.max(aufrufeMax, k);
        }
        sz.bild.wellenSofort = true;
        A.pruefe(stände > 0 && sz.bild.wellenHochMaxS * 1000 <= HOCH_MS, "Wellenrippel nebenher: Hochladen ins Gelände im Faden des Bilds, höchstens",
                f("%.1f ms (%d Stände, %d hochgeladen)", 1000 * sz.bild.wellenHochMaxS, stände, sz.bild.wellenHoch - h0), f("≤ %.0f ms", HOCH_MS));
        A.info("Wellenrippel nebenher: längster Aufruf von Bild.zu (mit den Ständen des Wassers), Aufrufe je Stand höchstens", f("%.1f ms, %d (je 5 ms Pause)", 1000 * zuMax, aufrufeMax));
    }

    static float[] bild(BildCheck.Szene sz, Blick b, Instant t0, double t) {
        for (int k = 0; k < 12; k++) {
            sz.rahmen.belichtung.sofort();
            sz.zeichne(b, t0, t, null);
        }
        return sz.aus.lies();
    }

    /** Helligkeit (Rec. 709 aus den 8-Bit-Werten) bilinear am Bildpunkt; NaN außerhalb. */
    static double hell(float[] p, double px, double py) {
        double u = px - 0.5, v = py - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        if (i < 0 || j < 0 || i + 1 >= BREITE || j + 1 >= HOEHE) return Double.NaN;
        double fu = u - i, fv = v - j;
        return (1 - fu) * (1 - fv) * l(p, i, j) + fu * (1 - fv) * l(p, i + 1, j) + (1 - fu) * fv * l(p, i, j + 1) + fu * fv * l(p, i + 1, j + 1);
    }

    static double l(float[] p, int i, int j) {
        int o = 4 * (j * BREITE + i);
        return 0.2126 * p[o] + 0.7152 * p[o + 1] + 0.0722 * p[o + 2];
    }

    static double std(double[] a) {
        double s = 0, s2 = 0;
        for (double v : a) { s += v; s2 += v * v; }
        double m = s / a.length;
        return Math.sqrt(Math.max(0, s2 / a.length - m * m));
    }

    /** Verschiebung des Gipfels (in Bins) längs (da, db) aus der Parabel durch drei Leistungen. */
    static double parabel(double[] re, double[] im, int nf, int a, int b, int da, int db) {
        double pm = leistung(re, im, nf, a - da, b - db), p0 = leistung(re, im, nf, a, b), pp = leistung(re, im, nf, a + da, b + db);
        double nenner = pm - 2 * p0 + pp;
        return nenner >= 0 ? 0 : 0.5 * (pm - pp) / nenner;
    }

    static double leistung(double[] re, double[] im, int nf, int a, int b) {
        int ia = ((a % nf) + nf) % nf, ib = ((b % nf) + nf) % nf;
        int o = ia + nf * ib;
        return re[o] * re[o] + im[o] * im[o];
    }

    /** 2D-FFT (Radix 2) in place, nf × nf, Zeilen dann Spalten. */
    static void fft2(double[] re, double[] im, int nf) {
        double[] r = new double[nf], i = new double[nf];
        for (int b = 0; b < nf; b++) {
            System.arraycopy(re, b * nf, r, 0, nf);
            System.arraycopy(im, b * nf, i, 0, nf);
            fft(r, i);
            System.arraycopy(r, 0, re, b * nf, nf);
            System.arraycopy(i, 0, im, b * nf, nf);
        }
        for (int a = 0; a < nf; a++) {
            for (int b = 0; b < nf; b++) { r[b] = re[a + nf * b]; i[b] = im[a + nf * b]; }
            fft(r, i);
            for (int b = 0; b < nf; b++) { re[a + nf * b] = r[b]; im[a + nf * b] = i[b]; }
        }
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }

    static void statusbild(float[][] bilder, String[] namen) throws Exception {
        int w = BREITE / 2, h = HOEHE / 2;
        BufferedImage alle = new BufferedImage(2 * w, 2 * h, BufferedImage.TYPE_INT_RGB);
        for (int n = 0; n < bilder.length; n++) {
            float[] p = bilder[n];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int i = 4 * ((2 * y) * BREITE + 2 * x);
                    int rr = Math.round(p[i] * 255), gg = Math.round(p[i + 1] * 255), bb = Math.round(p[i + 2] * 255);
                    alle.setRGB(x + (n % 2) * w, y + (n / 2) * h, (rr << 16) | (gg << 8) | bb);
                }
            Graphics2D g2 = alle.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, h / 36)));
            int x0 = (n % 2) * w + 8, y0 = (n / 2) * h + h - 10;
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRect(x0 - 4, y0 - g2.getFontMetrics().getAscent() - 2, g2.getFontMetrics().stringWidth(namen[n]) + 8, g2.getFontMetrics().getHeight() + 2);
            g2.setColor(Color.WHITE);
            g2.drawString(namen[n], x0, y0);
            g2.dispose();
        }
        File png = new File("docs" + File.separator + "status" + File.separator + "S6d1_Bild.png");
        png.getParentFile().mkdirs();
        ImageIO.write(alle, "png", png);
        // dazu die beiden Bilder von oben in voller Größe
        for (int n = 0; n < 2; n++) {
            BufferedImage e = new BufferedImage(BREITE, HOEHE, BufferedImage.TYPE_INT_RGB);
            float[] p = bilder[n];
            for (int y = 0; y < HOEHE; y++)
                for (int x = 0; x < BREITE; x++) {
                    int i = 4 * (y * BREITE + x);
                    e.setRGB(x, y, (Math.round(p[i] * 255) << 16) | (Math.round(p[i + 1] * 255) << 8) | Math.round(p[i + 2] * 255));
                }
            ImageIO.write(e, "png", new File(png.getParentFile(), n == 0 ? "S6d1_oben_mit.png" : "S6d1_oben_ohne.png"));
        }
        A.info("Status-Bild (Sichtprüfung)", png.getPath());
    }
}
