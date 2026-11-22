package com.dan.physics.hydro.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.hydro.Vorgaberand;
import com.dan.physics.hydro.gpu.HydroKarte;
import com.dan.physics.hydro.gpu.KetteKarte;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;
import static com.dan.physics.hydro.demo.HydroCheck.A;

/**
 * Fall 9 und 14 der Prüfung A (Werkbuch HalligHooge Teil A, 12.5, Plan P4d): der Lauf über die Gitterkette eines
 * Gebiets auf der Karte, G2 → G3 (mit Deichen und Sielen) → G4 (im Lauf aus dem Stapel gebacken), mit der Vorgabe der
 * Tide am Rand von G2 ({@code vorgabe.txt}).
 * <ul>
 * <li><b>Fall 9:</b> eine Periode der Tide der Vorgabe; Prüfsteine: G4 gegen G3 im Volumen über den Überlapp, die
 * Bilanz über die Periode, kein NaN am Ende. Zur Auskunft je Pegel Hub, Hochwasser und M2 gegen die Vorgabe.</li>
 * <li><b>Fall 14:</b> Zeiten (zur Auskunft): der Lauf aus Fall 9, G2–G3 ohne G4 über eine Stunde, G3 allein mit den
 * Feldern im Speicher der Karte und im geteilten Speicher.</li>
 * </ul>
 * Für Proben in der Arbeitsumgebung (Systemeigenschaften): {@code hydro.lauf.stunden} (Dauer statt einer Periode),
 * {@code hydro.lauf.g3 = i0,j0,ni,nj} (ein Fenster von G3 statt ganz G3, auf ganzen Zellen von G2),
 * {@code hydro.lauf.g4 = n} (G4 mit n Zellen je Seite), {@code hydro.lauf.anlauf} (Anlauf in Stunden).
 * <p>
 * Dazu Fall 10 über die Kette ({@link #fall10Kette}): drei Ebenen, ein grober Schritt auf dem Prozessor und auf der
 * Karte aus demselben Stand (Grenze A).
 */
final class HydroLauf {
    private HydroLauf() { }

    /** Anlauf der Tide am Rand (s) [G]; für Proben {@code hydro.lauf.anlauf} in Stunden. */
    static final double ANLAUF = 3600 * Double.parseDouble(System.getProperty("hydro.lauf.anlauf", "3"));
    /** Anlauf in Fall 10 über die Kette, fest (s): Der Prüfstein hängt nicht an den Eigenschaften für Proben von Fall 9. */
    static final double ANLAUF_FALL10 = 3 * 3600;
    /** Hochwasser am Bezugspegel (dem ersten der Vorgabe) nach der Vorgabe bei t = 5 h [G]. */
    static final double HW_BEZUG = 5 * 3600;
    /** Pegel werden alle 5 min von der Karte gelesen [G]. */
    static final double TAKT_PEGEL = 300;
    /** Ein Pegel liegt in der tiefsten Zelle im Umkreis von 300 m [G]. */
    static final double UMKREIS_PEGEL = 300;
    /** Wasserstände für den Vergleich G4 gegen G3 (m NHN), wie Prüfstein 9 in 12.4. */
    static final double[] ETA_G4 = {-1, 0, 1, 2, 3};
    static final double GRENZE_G4 = 0.005, GRENZE_VOLUMEN = 1e-5;
    /** Halbe Breite des Ausschnitts von G3 im Bild um die Mitte von G4 (m). */
    static final double BILD_G3 = 4000;

    /** Ein Pegel im Lauf. */
    static final class Pegel {
        final Gebiet.Vorgabe.Pegel soll;
        final int ebene, zelle;
        final double grund;
        final String gitter;
        double[] t = new double[256], eta = new double[256];
        int n;

        Pegel(Gebiet.Vorgabe.Pegel soll, String gitter, int ebene, int zelle, double grund) {
            this.soll = soll;
            this.gitter = gitter;
            this.ebene = ebene;
            this.zelle = zelle;
            this.grund = grund;
        }

        void dazu(double tt, double e) {
            if (n == t.length) {
                t = Arrays.copyOf(t, 2 * n);
                eta = Arrays.copyOf(eta, 2 * n);
            }
            t[n] = tt;
            eta[n++] = e;
        }
    }

    /** Ein Stand für das Bild: Ausschnitt von G3 und G4 je Zelle: benetzter Anteil, Tiefe über der benetzten Fläche, u, v. */
    static final class Stand {
        double t, eta;
        int ni, nj, n4;
        float[] anteil3, tiefe3, u3, v3, anteil4, tiefe4, u4, v4;
    }

    /** Ergebnis des Laufs (für Fall 14 und das Status-Bild). */
    static final class Ergebnis {
        String name;
        double dauer, anlauf, versatz, omega, eta0, wand, rechnen, rest, bezug;
        long zellschritte;
        long[] schritte;
        int[] zellen;
        Gitter g3, g4;
        int[] fenster;                            // Ausschnitt von G3 im Bild: i0, j0, ni, nj
        float[] grund3, grund4;                   // ground.z im Ausschnitt und in G4
        Gebiet.Bauten bauten;
        int bauI0, bauJ0;                         // Lage des Gitters G3 im Lauf (Fenster) in G3
        final List<Pegel> pegel = new ArrayList<>();
        Stand hw, nw;
        double backen;
        String geraet;
    }

    static Ergebnis lauf;

    // ------------------------------------------------------------------ Aufbau

    static int[] g3Fenster() {
        String s = System.getProperty("hydro.lauf.g3");
        if (s == null || s.isBlank()) return null;
        String[] t = s.split("[,\\s]+");
        return new int[] {Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3])};
    }

    /** G3 ganz (mit allen Bauwerken) oder ein Fenster; {@code lage} bekommt i0, j0. */
    static Hydro g3(Gebiet ge, Gebiet.Bauten bt, int[] lage) throws Exception {
        Gitter g3 = ge.gitter("G3");
        Subgrid.Packung p = ge.packung("G3");
        if (p.kantenWerte() < 3) throw new IllegalStateException("Packung von G3 ist Version 1: Aufbereitung --schritte backen");
        int[] w = g3Fenster();
        if (w == null) {
            Hydro h = new Hydro(g3, p);
            h.bauwerke(bt);
            lage[0] = 0;
            lage[1] = 0;
            return h;
        }
        Gitter gw = Gitter.utm("G3", g3.zone, g3.x0 + w[0] * g3.dx, g3.y0 + w[1] * g3.dy, g3.dx, w[2], w[3]);
        Hydro h = new Hydro(gw, HydroCheck.fenster(p, g3, w[0], w[1], w[2], w[3]));
        HydroCheck.bauwerkeImFenster(h, bt, w[0], w[1]);
        lage[0] = w[0];
        lage[1] = w[1];
        return h;
    }

    static Gebiet.Auge g4(Gebiet ge) throws Exception {
        String n = System.getProperty("hydro.lauf.g4");
        double[] s = zahlen(ge.rezept("g4.suedwest"));
        return ge.auge(s[0], s[1], n == null ? 0 : Integer.parseInt(n.strip()));
    }

    static double[] zahlen(String v) {
        if (v == null) throw new IllegalStateException("g4.suedwest fehlt im Rezept in gebiet.txt");
        String[] t = v.split("[,\\s]+");
        return new double[] {Double.parseDouble(t[0]), Double.parseDouble(t[1])};
    }

    /** Der Pegel in der Kette: feinste Ebene mit dem Ort, darin die tiefste Zelle im Umkreis, die unter keiner feineren liegt. */
    static Pegel pegel(KetteKarte k, Gebiet.Vorgabe.Pegel p) {
        Hydro[] e = k.kette.ebene;
        for (int q = e.length - 1; q >= 0; q--) {
            Gitter g = e[q].gitter;
            if (!g.enthaelt(p.ost(), p.nord())) continue;
            int i0 = g.i(p.ost()), j0 = g.j(p.nord()), r = (int) Math.ceil(UMKREIS_PEGEL / g.dx);
            if (k.kette.unter(q, g.index(i0, j0))) continue;
            int best = -1;
            double tief = Double.POSITIVE_INFINITY;
            for (int j = Math.max(0, j0 - r); j <= Math.min(g.ny - 1, j0 + r); j++)
                for (int i = Math.max(0, i0 - r); i <= Math.min(g.nx - 1, i0 + r); i++) {
                    int c = g.index(i, j);
                    if (Math.hypot(g.x(i) - p.ost(), g.y(j) - p.nord()) > UMKREIS_PEGEL || k.kette.unter(q, c)) continue;
                    if (e[q].grund(c) < tief) { tief = e[q].grund(c); best = c; }
                }
            if (best >= 0) return new Pegel(p, g.name, q, best, tief);
        }
        return null;
    }

    // ------------------------------------------------------------------ Fall 9

    static void fall9(String gebiet) throws Exception {
        KarteRechner kr = (KarteRechner) HydroCheck.R;
        Gebiet ge = Gebiet.lies(new File(gebiet));
        if (!ge.hatVorgabe()) {
            A.pruefe(false, "Fall 9: Vorgabe der Tide am Rand", "vorgabe.txt fehlt: Aufbereitung --schritte vorgabe", "vorhanden");
            return;
        }
        Gebiet.Vorgabe v = ge.vorgabe();
        Ergebnis er = new Ergebnis();
        er.geraet = kr.geraet();
        // G4 aus dem Stapel
        Gebiet.Auge au = g4(ge);
        er.backen = au.sekunden();
        Gebiet.Bauten bt = ge.bauwerke("G3");
        int[] lage3 = new int[2];
        Hydro h3 = g3(ge, bt, lage3);
        Hydro h4 = new Hydro(au.gitter(), au.packung());
        int siele3 = 0;
        for (int q = 0; q < h3.bauwerke(); q++) if (h3.istSiel(q)) siele3++;
        A.info(f("Fall 9: Bauwerke in G3 (%d × %d)", h3.nx, h3.ny), f("%d Deich- und %d Sielkanten; %d auf dem Rand von G3 ausgelassen (dort sieht nur G2 das Gelände)",
                h3.bauwerke() - siele3, siele3, h3.bauwerkeAmRand));
        A.info(f("Fall 9: G4 aus dem Stapel (Gebiet.auge): %s", au.gitter()), f("gebacken in %.2f s, Packung %.0f MB, ground.z %.2f … %.2f m NHN", au.sekunden(), au.packung().bytes() / 1e6,
                min(au.groundZ()), max(au.groundZ())));
        // Prüfstein: G4 gegen G3 über den Überlapp
        {
            Gitter g3 = h3.gitter, g4 = h4.gitter;
            int r = g4.passtIn(g3), i0 = (int) Math.round((g4.x0 - g3.x0) / g3.dx), j0 = (int) Math.round((g4.y0 - g3.y0) / g3.dy);
            if (r == 0) {
                A.pruefe(false, "Prüfung A, Fall 9: G4 liegt auf ganzen Zellen von G3", g4 + " in " + g3, "ja");
                return;
            }
            double groesst = 0;
            StringBuilder b = new StringBuilder();
            for (double eta : ETA_G4) {
                double v3 = 0, v4 = 0;
                for (int j = 0; j < g4.ny / r; j++)
                    for (int i = 0; i < g4.nx / r; i++) v3 += h3.volumen(g3.index(i0 + i, j0 + j), eta);
                for (int c = 0; c < h4.zellen; c++) v4 += h4.volumen(c, eta);
                double d = v3 > 0 ? (v4 - v3) / v3 : 0;
                if (v3 > 0.001 * g4.nx * g4.dx * g4.ny * g4.dy) groesst = Math.max(groesst, Math.abs(d));    // ab 1 mm mittlerer Tiefe
                b.append(f("%s%+.0f m: %+.3f %%", b.length() > 0 ? ", " : "", eta, 100 * d));
            }
            A.pruefe(groesst <= GRENZE_G4, f("Prüfung A, Fall 9: G4 gegen G3 im Volumen über den Überlapp (%d × %d Zellen von G3), η = −1 … +3 m", g4.nx / r, g4.ny / r),
                    b.toString(), "≤ 0,5 % [G] (wie 12.4, Prüfstein 9)");
        }
        // G2 mit der Vorgabe am Rand
        Hydro h2 = new Hydro(ge.gitter("G2"), ge.packung("G2"));
        double om = v.omega(), periode = 2 * Math.PI / om;
        Gebiet.Vorgabe.Pegel bezug = v.pegel().isEmpty() ? null : v.pegel().get(0);
        double versatz = bezug == null ? 0 : Math.floorMod(Math.round(1000 * (Math.toRadians(bezug.phase()) / om - HW_BEZUG)), Math.round(1000 * periode)) / 1000.0;
        double eta0 = Vorgaberand.setze(h2, v, ANLAUF, versatz);
        for (Hydro h : new Hydro[] {h2, h3, h4}) h.fuellen(eta0);
        String st = System.getProperty("hydro.lauf.stunden");
        double dauer = st == null ? periode : 3600 * Double.parseDouble(st);
        er.name = f("G2 (%d × %d) → G3 (%d × %d%s) → G4 (%d × %d, Südwestecke O %.0f, N %.0f)", h2.nx, h2.ny, h3.nx, h3.ny, g3Fenster() == null ? "" : ", Fenster", h4.nx, h4.ny,
                h4.gitter.x0, h4.gitter.y0);
        er.dauer = dauer;
        er.anlauf = ANLAUF;
        er.versatz = versatz;
        er.omega = om;
        er.eta0 = eta0;
        er.g3 = h3.gitter;
        er.g4 = h4.gitter;
        er.bauten = bt;
        er.bauI0 = lage3[0];
        er.bauJ0 = lage3[1];
        er.zellen = new int[] {h2.zellen, h3.zellen, h4.zellen};
        // Ausschnitt von G3 für das Bild: um die Mitte von G4
        {
            Gitter g = h3.gitter;
            double xm = h4.gitter.x0 + 0.5 * h4.nx * h4.dx, ym = h4.gitter.y0 + 0.5 * h4.ny * h4.dy;
            int i0 = Math.max(0, g.i(xm - BILD_G3)), j0 = Math.max(0, g.j(ym - BILD_G3)), i1 = Math.min(g.nx, g.i(xm + BILD_G3)), j1 = Math.min(g.ny, g.j(ym + BILD_G3));
            er.fenster = new int[] {i0, j0, i1 - i0, j1 - j0};
            float[] gz3 = ge.groundZ("G3");
            Gitter gg = ge.gitter("G3");
            er.grund3 = new float[er.fenster[2] * er.fenster[3]];
            for (int j = 0; j < er.fenster[3]; j++)
                for (int i = 0; i < er.fenster[2]; i++) er.grund3[i + er.fenster[2] * j] = gz3[gg.index(lage3[0] + i0 + i, lage3[1] + j0 + j)];
            er.grund4 = au.groundZ();
        }
        A.info("Fall 9: Lauf", f("%s; Tide %s der Vorgabe (ω = %.4f °/h), η₀ = %+.3f m, Anlauf %.0f h, Hochwasser am Pegel %s nach der Vorgabe bei %.1f h; Dauer %.2f h",
                er.name, v.tide(), Math.toDegrees(om) * 3600, eta0, ANLAUF / 3600, bezug == null ? "–" : bezug.name(), HW_BEZUG / 3600, dauer / 3600));
        System.out.println(f("         Lauf über %.2f h Szenenzeit; jede Stunde eine Zeile.", dauer / 3600));
        try (KetteKarte k = new KetteKarte(kr.g, kr.konto(), h2, h3, h4)) {
            for (Gebiet.Vorgabe.Pegel p : v.pegel()) {
                Pegel pg = pegel(k, p);
                if (pg != null) er.pegel.add(pg);
                else A.info(f("Fall 9: Pegel %s", p.name()), "liegt in keinem Gitter der Kette");
            }
            k.herunter();
            double v0 = k.volumen(), vmax = v0;
            long w0 = System.nanoTime(), rechnen = 0;
            double naechsterPegel = 0, naechsteStunde = 3600;
            Pegel bz = er.pegel.isEmpty() ? null : er.pegel.get(0);
            for (Pegel pg : er.pegel) pg.dazu(0, k.ebene[pg.ebene].etaZelle(pg.zelle));
            naechsterPegel = TAKT_PEGEL;
            while (k.ebene[0].zeit < dauer - 1e-6) {
                double t = k.ebene[0].zeit, dt = k.dtKette(dauer - t);
                long a = System.nanoTime();
                k.schritt(dt);
                rechnen += System.nanoTime() - a;
                t = k.ebene[0].zeit;
                if (t >= naechsterPegel - 1e-6 || t >= dauer - 1e-6) {
                    naechsterPegel += TAKT_PEGEL;
                    for (Pegel pg : er.pegel) pg.dazu(t, k.ebene[pg.ebene].etaZelle(pg.zelle));
                    if (bz != null && t >= ANLAUF) {
                        double e = bz.eta[bz.n - 1];
                        if (er.hw == null || e > er.hw.eta) er.hw = stand(k, er, t, e);
                        if (er.nw == null || e < er.nw.eta) er.nw = stand(k, er, t, e);
                    }
                }
                if (t >= naechsteStunde - 1e-6) {
                    naechsteStunde += 3600;
                    k.herunter();
                    double vv = k.volumen();
                    vmax = Math.max(vmax, vv);
                    double rest = rest(k, v0), bez = Math.max(vmax, k.ebene[0].h.zufluss + k.ebene[0].h.abfluss);
                    double wand = (System.nanoTime() - w0) / 1e9, raffer = t / wand;
                    System.out.println(f("         t = %5.2f h: Zeitraffer %5.1f, Bilanz %.1e (abgeschnitten %.0f m³), Schritte %d / %d / %d, noch rund %.0f min", t / 3600, raffer,
                            Math.abs(rest) / bez, k.abgeschnitten(), k.schritte[0], k.schritte[1], k.schritte[2], (dauer - t) / raffer / 60));
                }
            }
            er.wand = (System.nanoTime() - w0) / 1e9;
            er.rechnen = rechnen / 1e9;
            er.zellschritte = k.zellschritte();
            er.schritte = k.schritte.clone();
            k.herunter();
            double v1 = k.volumen();
            vmax = Math.max(vmax, v1);
            Hydro g2 = k.ebene[0].h;
            er.rest = rest(k, v0);
            er.bezug = Math.max(vmax, g2.zufluss + g2.abfluss);
            A.pruefe(Math.abs(er.rest) / er.bezug < GRENZE_VOLUMEN,
                    f("Prüfung A, Fall 9: Volumen über den Lauf (%.2f h) auf der Karte, alle Ebenen: |V_Ende − V_Anfang − (Zufluss − Abfluss)| / Bezug; Abgeschnittenes zählt als Fehler", dauer / 3600),
                    f("%.1e (Rest %.0f m³, davon abgeschnitten %.1f m³; Zufluss %.4e, Abfluss %.4e; Bestand %.4e … %.4e m³)", Math.abs(er.rest) / er.bezug, er.rest, k.abgeschnitten(),
                            g2.zufluss, g2.abfluss, v0, vmax), "< 1e-5 (7.1)");
            long nan = 0;
            for (Hydro h : k.kette.ebene) {
                for (double x : h.eta) if (!Double.isFinite(x)) nan++;
                for (double x : h.vol) if (!Double.isFinite(x)) nan++;
                for (double x : h.u) if (!Double.isFinite(x)) nan++;
            }
            A.pruefe(nan == 0, "Prüfung A, Fall 9: kein NaN in η, V und u am Ende, alle Ebenen", f("%d Werte nicht endlich", nan), "keins");
            A.info("Fall 9: Schritte je Ebene", f("G2 %d, G3 %d, G4 %d; Unterschritte im Mittel %.1f (G3 je G2) und %.1f (G4 je G3)", er.schritte[0], er.schritte[1], er.schritte[2],
                    (double) er.schritte[1] / er.schritte[0], (double) er.schritte[2] / er.schritte[1]));
            for (Pegel pg : er.pegel) A.info(f("Fall 9: Pegel %s (%s, Zelle mit Grund %+.2f m NHN)", pg.soll.name(), pg.gitter, pg.grund), pegelText(pg, er));
        }
        lauf = er;
    }

    /**
     * Rest der Bilanz nach {@link KetteKarte#herunter()}: V − V0 − (Zufluss − Abfluss). Abgeschnittenes (negatives
     * Volumen, auf null gesetzt) ist entstandenes Wasser und zählt hier als Fehler (wie in Fall 13).
     */
    static double rest(KetteKarte k, double v0) {
        Hydro g2 = k.ebene[0].h;
        return k.volumen() - v0 - (g2.zufluss - g2.abfluss);
    }

    /** Hub, Hochwasser und M2 nach dem Anlauf gegen die Vorgabe. */
    static String pegelText(Pegel p, Ergebnis er) {
        double hw = Double.NEGATIVE_INFINITY, nw = Double.POSITIVE_INFINITY, thw = 0;
        int m = 0;
        double[][] a = new double[3][3];
        double[] r = new double[3];
        for (int q = 0; q < p.n; q++) {
            if (p.t[q] < er.anlauf) continue;
            if (p.eta[q] > hw) { hw = p.eta[q]; thw = p.t[q]; }
            nw = Math.min(nw, p.eta[q]);
            double tau = er.omega * (p.t[q] + er.versatz);
            double[] b = {1, Math.cos(tau), Math.sin(tau)};
            for (int i = 0; i < 3; i++) {
                r[i] += b[i] * p.eta[q];
                for (int j = 0; j < 3; j++) a[i][j] += b[i] * b[j];
            }
            m++;
        }
        if (m < 10) return "zu wenige Werte nach dem Anlauf";
        double[] x = HydroCheck.loese(a, r);
        double amp = Math.hypot(x[1], x[2]), ph = Math.toDegrees(Math.atan2(x[2], x[1]));
        if (ph < 0) ph += 360;
        // Hochwasser nach der Vorgabe nach dem Anlauf
        double per = 2 * Math.PI / er.omega, tSoll = Math.toRadians(p.soll.phase()) / er.omega - er.versatz;
        while (tSoll < er.anlauf) tSoll += per;
        while (tSoll - per >= er.anlauf) tSoll -= per;
        double dph = Math.IEEEremainder(ph - p.soll.phase(), 360);
        return f("Hub nach dem Anlauf %.2f m gegen 2A = %.2f m; Hochwasser %+.2f m um %.2f h, nach der Vorgabe %+.2f m um %.2f h (%+.0f min); M2 angepasst über %.1f–%.1f h: %.3f m, %.1f° gegen %.3f m, %.1f° (%+.1f°)",
                hw - nw, 2 * p.soll.amplitude(), hw, thw / 3600, p.soll.mittel() + p.soll.amplitude(), tSoll / 3600, (thw - tSoll) / 60, er.anlauf / 3600, er.dauer / 3600, amp, ph,
                p.soll.amplitude(), p.soll.phase(), dph);
    }

    /** Holt G3 und G4 herunter und hält den Ausschnitt fest. */
    static Stand stand(KetteKarte k, Ergebnis er, double t, double eta) {
        Stand s = new Stand();
        s.t = t;
        s.eta = eta;
        int last = k.ebene.length - 1;
        k.ebene[1].herunter();
        k.ebene[last].herunter();
        Hydro h3 = k.ebene[1].h, h4 = k.ebene[last].h;
        int[] w = er.fenster;
        s.ni = w[2];
        s.nj = w[3];
        int n = w[2] * w[3];
        s.anteil3 = new float[n];
        s.tiefe3 = new float[n];
        s.u3 = new float[n];
        s.v3 = new float[n];
        for (int j = 0; j < w[3]; j++)
            for (int i = 0; i < w[2]; i++) zelle(h3, h3.gitter.index(w[0] + i, w[1] + j), i + w[2] * j, s.anteil3, s.tiefe3, s.u3, s.v3);
        s.n4 = h4.nx;
        s.anteil4 = new float[h4.zellen];
        s.tiefe4 = new float[h4.zellen];
        s.u4 = new float[h4.zellen];
        s.v4 = new float[h4.zellen];
        for (int c = 0; c < h4.zellen; c++) zelle(h4, c, c, s.anteil4, s.tiefe4, s.u4, s.v4);
        return s;
    }

    static void zelle(Hydro h, int c, int o, float[] anteil, float[] tiefe, float[] u, float[] v) {
        if (!(h.vol[c] > 0)) return;
        double a = h.benetzt(c, h.eta[c]);
        if (!(a > 0)) return;
        anteil[o] = (float) (a / h.zellflaeche);
        tiefe[o] = (float) (h.vol[c] / a);
        double[] g = h.geschwindigkeit(c);
        u[o] = (float) g[0];
        v[o] = (float) g[1];
    }

    static float min(float[] a) {
        float m = Float.POSITIVE_INFINITY;
        for (float x : a) m = Math.min(m, x);
        return m;
    }

    static float max(float[] a) {
        float m = Float.NEGATIVE_INFINITY;
        for (float x : a) m = Math.max(m, x);
        return m;
    }

    // ------------------------------------------------------------------ Fall 10 über die Kette

    /**
     * Fall 10 über die Kette (Grenze A, seit P4d): G2 ganz, ein Fenster von G3 um Hooge (160 × 160 Zellen) mit seinen
     * Bauwerken, darin G4 mit 200 × 200 Zellen an der Südwestecke aus dem Rezept; die Vorgabe am Rand von G2. Ein grober
     * Schritt auf der Karte und auf dem Prozessor (double) aus demselben Stand; verglichen werden η (nasse Zellen) und u
     * je Ebene. Der Anlauf ist fest 3 h ({@link #ANLAUF_FALL10}), unabhängig von {@code hydro.lauf.anlauf}.
     * <ul>
     * <li><b>Prüfstein:</b> überflutet – ruhender See bei +3 m NHN, die Vorgabe um dasselbe angehoben, nach 1 h Tide.</li>
     * <li><b>Zur Auskunft:</b> nach 2 h Tide aus dem ruhenden See bei η₀, mit Trockenfallen an den Rändern der Schachtel.
     * Dort greift die Begrenzung der Flüsse aus den Zellen außen (seit P4d), und u hängt an der Menge in einer fast
     * leeren Zelle: Grenze A gilt da nicht (wie an jeder Nass-Trocken-Grenze nach vielen Schritten, 12.3).</li>
     * </ul>
     */
    static void fall10Kette(String gebiet) throws Exception {
        KarteRechner kr = (KarteRechner) HydroCheck.R;
        Gebiet ge = Gebiet.lies(new File(gebiet));
        if (!ge.hatVorgabe()) {
            A.pruefe(false, "Prüfung A, Fall 10 über die Kette: Vorgabe der Tide am Rand", "vorgabe.txt fehlt: Aufbereitung --schritte vorgabe", "vorhanden");
            return;
        }
        Gebiet.Vorgabe v = ge.vorgabe();
        double[] a = kettenSchritt(kr, ge, v, 3.0 - Vorgaberand.eta0(v), 3600);
        String[] q = kettenText(a);
        A.pruefe(a[0] <= 1e-5 && a[1] <= 1e-5, f("Prüfung A, Fall 10 über die Kette: ein Schritt von G2 (Δt = %.2f s) mit G3 (Fenster 160 × 160 ab Zelle %.0f, %.0f; %.0f Deich- und %.0f Sielkanten) und G4 (200 × 200), überflutet: ruhender See bei +3 m, Vorgabe angehoben, nach 1 h",
                a[2], a[3], a[4], a[5], a[6]), q[0], "≤ 1e-5 (Grenze A)");
        double[] b = kettenSchritt(kr, ge, v, 0, 7200);
        A.info("Fall 10 über die Kette: derselbe Schritt nach 2 h Tide aus dem ruhenden See bei η₀ (Trockenfallen an den Rändern der Schachtel, Begrenzung außen)", kettenText(b)[0]);
    }

    /** {max |Δη|, max |Δu|, Δt, i0, j0, Deiche, Siele, dann je Ebene |Δη|, |Δu|, Schritte}. */
    static double[] kettenSchritt(KarteRechner kr, Gebiet ge, Gebiet.Vorgabe v, double anheben, double vorlauf) throws Exception {
        Gitter g3 = ge.gitter("G3");
        double[] sw = zahlen(ge.rezept("g4.suedwest"));
        Gebiet.Auge au = ge.auge(sw[0], sw[1], 200);
        int i4 = (int) Math.round((au.gitter().x0 - g3.x0) / g3.dx), j4 = (int) Math.round((au.gitter().y0 - g3.y0) / g3.dy);
        int i0 = Math.max(0, (i4 - 30) / 10 * 10), j0 = Math.max(0, (j4 - 30) / 10 * 10);
        Gitter gw = Gitter.utm("G3", g3.zone, g3.x0 + i0 * g3.dx, g3.y0 + j0 * g3.dy, g3.dx, 160, 160);
        Hydro h3 = new Hydro(gw, HydroCheck.fenster(ge.packung("G3"), g3, i0, j0, 160, 160));
        int[] bw = HydroCheck.bauwerkeImFenster(h3, ge.bauwerke("G3"), i0, j0);
        Hydro h4 = new Hydro(au.gitter(), au.packung()), h2 = new Hydro(ge.gitter("G2"), ge.packung("G2"));
        double e0 = Vorgaberand.setze(h2, v, ANLAUF_FALL10, 0, anheben);
        for (Hydro h : new Hydro[] {h2, h3, h4}) h.fuellen(e0);
        double[] r = new double[7 + 9];
        try (KetteKarte k = new KetteKarte(kr.g, kr.konto(), h2, h3, h4)) {
            k.lauf(vorlauf, 1e9);
            k.herunter();
            double dt = k.ebene[0].dtCfl();
            k.schritt(dt);
            float[][] ek = new float[3][], uk = new float[3][];
            for (int q = 0; q < 3; q++) {
                ek[q] = k.ebene[q].etaKarte();
                uk[q] = k.ebene[q].uKarte();
            }
            com.dan.physics.hydro.Kette kc = new com.dan.physics.hydro.Kette(h2, h3, h4);
            kc.schritt(dt);
            for (int q = 0; q < 3; q++) {
                Hydro h = kc.ebene[q];
                double e = 0, u = 0;
                for (int c = 0; c < h.zellen; c++) if (h.vol[c] > 0) e = Math.max(e, Math.abs(ek[q][c] - h.eta[c]));
                for (int x = 0; x < h.kanten; x++) u = Math.max(u, Math.abs(uk[q][x] - h.u[x]));
                r[0] = Math.max(r[0], e);
                r[1] = Math.max(r[1], u);
                r[7 + 3 * q] = e;
                r[8 + 3 * q] = u;
                r[9 + 3 * q] = kc.schritte[q];
            }
            r[2] = dt;
        }
        r[3] = i0;
        r[4] = j0;
        r[5] = bw[0];
        r[6] = bw[1];
        return r;
    }

    static String[] kettenText(double[] r) {
        String[] n = {"G2", "G3", "G4"};
        StringBuilder b = new StringBuilder();
        for (int q = 0; q < 3; q++)
            b.append(f("%s%s: |Δη| %.1e m, |Δu| %.1e m/s (%.0f Schritte)", q > 0 ? "; " : "", n[q], r[7 + 3 * q], r[8 + 3 * q], r[9 + 3 * q]));
        return new String[] {b.toString()};
    }

    // ------------------------------------------------------------------ Fall 14

    static void fall14(String gebiet) throws Exception {
        KarteRechner kr = (KarteRechner) HydroCheck.R;
        String g = kr.geraet(), l = g.toLowerCase();
        String sw = l.contains("llvmpipe") || l.contains("lavapipe") || l.contains("swiftshader") ? " – Software-Gerät: ohne Aussage über die Karte" : "";
        Ergebnis er = lauf;
        if (er != null && er.zellschritte > 0) {
            A.info(f("Fall 14: Lauf aus Fall 9 auf %s, %s, %.2f h Szenenzeit", g, er.name, er.dauer / 3600),
                    f("%.1f ns je Zellschritt (Rechnen %.0f s für %.3e Zellschritte); Zeitraffer %.1f (Wanduhr %.1f min mit Pegeln und Stunden-Bilanz)%s",
                            1e9 * er.rechnen / er.zellschritte, er.rechnen, (double) er.zellschritte, er.dauer / er.wand, er.wand / 60, sw));
        }
        Gebiet ge = Gebiet.lies(new File(gebiet));
        if (!ge.hatVorgabe()) return;
        Gebiet.Vorgabe v = ge.vorgabe();
        Gebiet.Bauten bt = ge.bauwerke("G3");
        // G2–G3 ohne G4 über eine Stunde
        {
            Hydro h2 = new Hydro(ge.gitter("G2"), ge.packung("G2"));
            Hydro h3 = g3(ge, bt, new int[2]);
            double e0 = Vorgaberand.setze(h2, v, ANLAUF, er != null ? er.versatz : 0);
            h2.fuellen(e0);
            h3.fuellen(e0);
            try (KetteKarte k = new KetteKarte(kr.g, kr.konto(), h2, h3)) {
                long a = System.nanoTime();
                k.lauf(3600, 1e9);
                double s = (System.nanoTime() - a) / 1e9;
                A.info(f("Fall 14: G2–G3 ohne G4 auf %s (%d × %d und %d × %d Zellen), erste Stunde der Tide", g, h2.nx, h2.ny, h3.nx, h3.ny),
                        f("%.1f ns je Zellschritt, Zeitraffer %.1f (%.1f s; Schritte %d / %d)%s", 1e9 * s / k.zellschritte(), 3600 / s, s, k.schritte[0], k.schritte[1], sw));
            }
        }
        // G3 allein: Felder im Speicher der Karte und im geteilten Speicher
        {
            Hydro h3 = g3(ge, bt, new int[2]);
            double x0 = h3.gitter.x0, lx = h3.nx * h3.dx;
            for (int c = 0; c < h3.zellen; c++) h3.setze(c, 0.5 + 0.5 * (h3.gitter.x(c % h3.nx) - x0) / lx);     // geneigter Spiegel: es fließt
            StringBuilder b = new StringBuilder();
            for (boolean geteilt : new boolean[] {false, true}) {
                HydroKarte.speicherGeteilt = geteilt;
                try (HydroKarte k = new HydroKarte(kr.g, kr.konto(), h3, 20)) {
                    double dt = k.dtCfl();
                    k.schritte(2, dt);
                    long a = System.nanoTime();
                    k.schritte(20, dt);
                    double ns = (System.nanoTime() - a) / (20.0 * h3.zellen);
                    b.append(f("%s%s %.1f ns", b.length() > 0 ? ", " : "", geteilt ? "geteilt" : "Karte", ns));
                } catch (RuntimeException e) {                              // zur Auskunft: ein Gerät ohne so viel geteilten Speicher ist kein Verstoß
                    b.append(f("%s%s nicht möglich (%s)", b.length() > 0 ? ", " : "", geteilt ? "geteilt" : "Karte", e.getMessage()));
                } finally {
                    HydroKarte.speicherGeteilt = false;
                }
            }
            A.info(f("Fall 14: G3 allein auf %s (%d × %d Zellen), 20 Schritte in einem Befehlspuffer, Felder im Speicher der Karte bzw. im geteilten Speicher", g, h3.nx, h3.ny),
                    b + " je Zellschritt" + sw);
        }
    }
}
