package com.dan.lauf.demo;

import com.dan.core.gpu.Geraet;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Walze;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Seegangsflaeche;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.lauf.Buehne;
import com.dan.physics.waves.Waves;
import com.dan.render.seegang.SeegangKarte;
import com.dan.render.seegang.Schaumfeld;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S5a (Seegang Werkbuch, Datei 40): Brechen in der Tiefe und die Walze ({@link Walze}), ohne Zustand.
 * <ol>
 * <li>Sturm 25.10. und Xaver: Orte in der Brandung (Hs/h ≥ 0,3, Tiefe 0,5–6 m, dbr ≥ 1 W/m²) im feinen Nahfeld.</li>
 * <li>Referenz in double, je Ort 300 s zu 10 Hz: Anteil der brechenden Kämme gegen Q_b nach Battjes und Janssen (1978)
 * aus H_rms und H_m = γ h wie im Nahfeld; Verteilung von H/H_m am Kamm (zur Wahl von Q_VON); Dissipation der Walzen
 * β_r ρ g c ⟨t⟩ gegen dbr des Nahfelds (β_r 0,05–0,1, Stive und De Vriend 1994).</li>
 * <li>Karte gegen Referenz: Weißwasser w der Stufe 0 an 1024 Stellen.</li>
 * <li>Bild mit dem Schaumfeld (die Walze ist seine Quelle in der Tiefe).</li>
 * <li>Seit S5b-4 (Seegang Werkbuch 46/47): Blick auf die Kante mit dem höchsten Seegang am Fuß im ganzen Gebiet, mit
 * einer eigenen Fläche davor; die Brandung folgt dort den Kämmen der Fläche ({@code render.seegang.Fusssuche}, {@link
 * com.dan.core.szene.Fusswellen}); die festen Kämme der Folge gegen die Referenz in double.</li>
 * </ol>
 * Aufruf: {@code WalzeCheck --szenen <Hooge.szenen>}; Attest {@code docs/status/S5a_Walze.md}.
 */
public final class WalzeCheck {
    static final double HS_H = 0.3, H_VON = 0.5, H_BIS = 6, DBR_MIN = 1, ABSTAND = 40, DAUER = 900, TAKT = 10;
    static final int ORTE = 12;
    static final double QB_VON = 0.75, QB_BIS = 1.25, ENERGIE_VON = 0.75, ENERGIE_BIS = 1.25, KARTE_TOL = 0.02;
    static final double[] SCHWELLEN = {0.6, 0.7, 0.8, 0.85, 0.9, 1.0};

    private WalzeCheck() { }

    public static void main(String[] args) {
        File szenen = null;
        int breite = 1920, hoehe = 1080;
        for (int i = 0; i + 1 < args.length; i += 2)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--breite" -> breite = Integer.parseInt(args[i + 1]);
                case "--hoehe" -> hoehe = Integer.parseInt(args[i + 1]);
                default -> { }
            }
        Attest A = new Attest("Seegang, Brechen und Walze (S5a)", "Seegang Werkbuch");
        System.out.println("WalzeCheck – S5a (Seegang Werkbuch)");
        long start = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            Ansicht an = sz.ansicht("brandung");
            Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            SchaumCheck.b = b;
            Seegangsflaeche.SENKEN = true;
            Schaumfeld feld = null;
            com.dan.render.seegang.Gischtsuche suche = null;
            com.dan.render.seegang.Fusssuche fuss = null;
            try {
                Geraet g = b.geraet();
                feld = new Schaumfeld(g, b.konto());
                suche = new com.dan.render.seegang.Gischtsuche(g, b.konto());           // seit S5b-2
                fuss = new com.dan.render.seegang.Fusssuche(g, b.konto());              // seit S5b-4
                com.dan.core.szene.Kammgischt kg = new com.dan.core.szene.Kammgischt();
                A.info("Walze", f("Version %s; γ %.2f, α %.1f, Brechen von %.2f bis %.2f H/H_m (q = 0,5 bei %.2f), Fläche A = α H² / (4 β_r) = %.2f H² mit β_r %.2f (Svendsen 1984: %.1f H²), Dicke am Kamm %.1f H, Weißwasser %.2f rad hinter dem Kamm",
                        Walze.VERSION, Walze.GAMMA, Walze.ALPHA, Walze.Q_VON, Walze.Q_BIS, 0.5 * (Walze.Q_VON + Walze.Q_BIS), Walze.FLAECHE, Walze.BETA, Walze.SVENDSEN, Walze.DICKE, Walze.HINTEN));
                ueberlauf(A);                                                  // seit S5b-3, nur Prozessor
                String[][] faelle = {{"seegang", "2026-10-25T12:30:00Z", "Sturm"}, {"xaver", "2013-12-05T13:30:00Z", "Xaver"}};
                for (String[] fa : faelle) {
                    Szenario sc = SchaumCheck.szenario(sz, fa[0]);
                    A.teil(fa[2]);
                    b.oeffne(sc);
                    Blick bl = SchaumCheck.blick(an, fa[1], breite, hoehe);
                    b.wasserbild().seegang = true;
                    b.brandung(true);
                    b.standbild(bl, SchaumCheck.t);
                    double t0 = SchaumCheck.t;
                    Seegangsflaeche sf = Seegangsflaeche.baue(g, b.konto(), sz.gebiet(), sc.archiv.toPath(), b.seegangsnetze(), bl, t0, null);
                    try {
                        A.info("Fläche", sf.bericht);
                        paare(A, sf, fa[2]);                                         // seit S5b-6b
                        List<double[]> orte = orte(sf);
                        A.info("Orte in der Brandung", f("%d (Hs/h ≥ %.1f, Tiefe %.1f–%.0f m, dbr ≥ %.0f W/m², mindestens %.0f m auseinander)", orte.size(), HS_H, H_VON, H_BIS, DBR_MIN, ABSTAND));
                        if (orte.isEmpty()) {
                            A.pruefe(false, "Orte in der Brandung, " + fa[2], "keine", "mindestens einer");
                            continue;
                        }
                        int best = reihen(A, sf, orte, t0, fa[2]);
                        karte(A, sf, orte.get(best), t0, fa[2]);
                        Blick nah = nah(b, sf, orte.get(best), breite, hoehe);
                        A.info("Blick in die Brandung, " + fa[2], f("Auge (%.0f, %.0f) in %.1f m NHN, %.0f m seewärts des Orts mit dem größten Q_b, Richtung %.0f°, Neigung %.0f°", nah.ost, nah.nord, nah.hoehe, NAH_ABSTAND, nah.richtung, nah.neigung));
                        sf.karte.bezug(Math.floor(nah.ost / 64) * 64, Math.floor(nah.nord / 64) * 64);
                        sf.karte.fernOst = nah.ost;
                        sf.karte.fernNord = nah.nord;
                        // seit S5b-2: mit der Gischt aus den Walzen
                        kg.leeren();
                        sf.bild.suche = suche;
                        sf.bild.gischt = kg;
                        if (b.brandungsbild() != null) b.brandungsbild().kammgischt = kg;
                        SchaumCheck.bilder(A, sf, feld, nah, t0, breite, hoehe, "S5a_Walze_" + fa[2] + "_nah", false);
                        A.info("Gischt aus den Walzen, " + fa[2], f("Kammgischt %s, im letzten Bild %d Orte von der Karte, %d neue Bündel, %d lebende Bündel (Rate %.1f je Ort und s, Leben %.1f s)",
                                com.dan.core.szene.Kammgischt.VERSION, kg.orteZuletzt, kg.neuZuletzt, kg.aktivZuletzt, com.dan.core.szene.Kammgischt.RATE, com.dan.core.szene.Kammgischt.LEBEN));
                        A.pruefe(kg.aktivZuletzt > 0, "Gischt aus den Walzen im Blick in die Brandung, " + fa[2], f("%d Bündel", kg.aktivZuletzt), "mehr als 0");
                        sf.bild.suche = null;
                        sf.bild.gischt = null;
                        if (b.brandungsbild() != null) b.brandungsbild().kammgischt = null;
                        final double tb = t0;
                        fussTeil(A, b, feld, fuss, bk -> Seegangsflaeche.baue(g, b.konto(), sz.gebiet(), sc.archiv.toPath(), b.seegangsnetze(), bk, tb, null), t0, breite, hoehe, fa[2]);   // seit S5b-4
                    } finally {
                        b.seegang(null);
                        g.ruhe();
                        sf.close();
                    }
                }
            } finally {
                Seegangsflaeche.SENKEN = false;
                if (feld != null) { b.geraet().ruhe(); feld.close(); }
                if (suche != null) suche.close();
                if (fuss != null) fuss.close();
                b.close();
            }
            A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - start) / 1e9));
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "S5a_Walze.md"), "S5a – Brechen und Walze");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Seit S5b-3: Überlauf je Welle (Brandung.volumen) gegen EurOtop 2018 über 200 000 Wellen der Folge der Brandung, vier Fälle. */
    static void ueberlauf(Attest A) {
        A.teil("Überlauf je Welle (S5b-3)");
        com.dan.core.szene.Brandung br = new com.dan.core.szene.Brandung(0, 0);
        double[][] faelle = {{1.0, 5, 0.25, 1.0, 1.5}, {1.5, 6, 0.33, 0.55, 2.0}, {0.8, 4.5, 0.1, 1.0, 0.6}, {2.0, 7, 0.4, 0.55, 3.0}};
        double qMax = 0, pMax = 0;
        for (double[] fa : faelle) {
            double hs = fa[0], tp = fa[1], tan = fa[2], rau = fa[3], rc = fa[4];
            double ru2 = com.dan.core.szene.Brandung.ru2(hs, tp, tan, rau), q = com.dan.core.szene.Brandung.ueberlauf(hs, tp, tan, rau, rc);
            double pov = com.dan.core.szene.Brandung.anteilUeberlauf(rc, ru2);
            int n = 200000, ov = 0;
            double sv = 0, vMax = 0;
            for (int w = 0; w < n; w++) {
                double ru = ru2 * br.hoehe(hs, w, 0) / (1.398 * hs), v = com.dan.core.szene.Brandung.volumen(ru, rc, ru2, hs, tp, tan, rau);
                if (v > 0) { ov++; sv += v; vMax = Math.max(vMax, v); }
            }
            double rq = sv / (n * tp) / q, rp = ov / (n * pov);
            qMax = Math.max(qMax, Math.abs(rq - 1));
            pMax = Math.max(pMax, Math.abs(rp - 1));
            A.info(f("Hs %.1f m, Tp %.1f s, tan α %.2f, γ_f %.2f, R_c %.1f m", hs, tp, tan, rau, rc), f("Ru2%% %.2f m; P_ov %.4f gegen %.4f; q %.3g gegen %.3g m³/s je m (EurOtop 2018, Gl. 5.10/5.11); größtes Volumen %.2f m³ je m", ru2, (double) ov / n, pov, sv / (n * tp), q, vMax));
        }
        A.hoechstens("mittlerer Überlauf der Wellen gegen EurOtop 2018, größte Abweichung über vier Fälle", qMax, 0.1, "");
        A.hoechstens("Anteil der Wellen mit Überlauf gegen P_ov nach EurOtop 2018, größte Abweichung über vier Fälle", pMax, 0.1, "");
    }

    static final double NAH_ABSTAND = 60, NAH_HOEHE = 6, NAH_NEIGUNG = -8;

    /** Blick aus {@link #NAH_ABSTAND} m seewärts (zum tieferen Wasser) auf den Ort, {@link #NAH_HOEHE} m über dem Wasser. */
    static Blick nah(Buehne b, Seegangsflaeche sf, double[] o, int breite, int hoehe) {
        Oberflaeche.Stufe s = sf.oberflaeche.fein;
        double gx = tiefe(s, o[0] + 20, o[1]) - tiefe(s, o[0] - 20, o[1]), gy = tiefe(s, o[0], o[1] + 20) - tiefe(s, o[0], o[1] - 20), l = Math.hypot(gx, gy);
        double ux = l > 1e-9 ? gx / l : 0, uy = l > 1e-9 ? gy / l : 1;
        double ost = o[0] + NAH_ABSTAND * ux, nord = o[1] + NAH_ABSTAND * uy;
        double[] wa = b.wasserUndSeegang(ost, nord);
        double eta = wa != null && Double.isFinite(wa[0]) ? wa[0] : 0;
        Blick bl = new Blick(ost, nord, eta + NAH_HOEHE, Math.toDegrees(Math.atan2(-ux, -uy)), NAH_NEIGUNG, 60);
        bl.groesse(breite, hoehe);
        return bl;
    }

    static double tiefe(Oberflaeche.Stufe s, double x, double y) {
        int i = (int) Math.floor((x - s.x0) / s.dx), j = (int) Math.floor((y - s.y0) / s.dx);
        i = Math.max(0, Math.min(s.nx - 1, i));
        j = Math.max(0, Math.min(s.ny - 1, j));
        return s.h[j * s.nx + i];
    }

    /** Orte in der Brandung im feinen Gitter: {Ost, Nord, Tiefe, Hs, dbr}, nach dbr absteigend, höchstens {@link #ORTE}. */
    static List<double[]> orte(Seegangsflaeche sf) {
        Oberflaeche.Stufe s = sf.oberflaeche.fein;
        List<double[]> alle = new ArrayList<>();
        if (s == null || sf.senkenFein == null) return alle;
        int kw = s.kennWerte();
        for (int j = 8; j < s.ny - 8; j++)
            for (int i = 8; i < s.nx - 8; i++) {
                int c = j * s.nx + i;
                double h = s.h[c], hs = 4 * Math.sqrt(Math.max(0, s.kenn[c * kw])), dbr = sf.senkenFein[4 * c + 1];
                if (h < H_VON || h > H_BIS || hs / h < HS_H || !(dbr >= DBR_MIN)) continue;
                alle.add(new double[] {s.x0 + (i + 0.5) * s.dx, s.y0 + (j + 0.5) * s.dx, h, hs, dbr});
            }
        alle.sort((a, b) -> Double.compare(b[4], a[4]));
        List<double[]> orte = new ArrayList<>();
        for (double[] o : alle) {
            boolean frei = true;
            for (double[] q : orte) if (Math.hypot(o[0] - q[0], o[1] - q[1]) < ABSTAND) { frei = false; break; }
            if (frei) orte.add(o);
            if (orte.size() >= ORTE) break;
        }
        return orte;
    }

    /** Teil Referenz: Zeitreihen an den Orten; Anteil brechender Kämme gegen Q_b, Verteilung H/H_m, Walzen gegen dbr. */
    static int reihen(Attest A, Seegangsflaeche sf, List<double[]> orte, double t0, String name) {
        int best = 0;
        double qbBest = -1;
        Oberflaeche ob = sf.oberflaeche;
        double aufl = ob.aufloesung;
        ob.aufloesung = 0;
        Oberflaeche.Punkt p = new Oberflaeche.Punkt();
        int schritte = (int) Math.round(DAUER * TAKT);
        long kaemmeSumme = 0, brechendSumme = 0;
        double qbSumme = 0, dbrSumme = 0, dSumme = 0, rBrechSumme = 0;
        long[] ueber = new long[SCHWELLEN.length];
        for (double[] o : orte) {
            double h = o[2];
            int kaemme = 0, brechend = 0;
            double vorIm = Double.NaN, tSumme = 0, m0 = 0, kp = 0;
            for (int s = 0; s < schritte; s++) {
                ob.punkt(o[0], o[1], t0 + s / TAKT, p);
                double fak = p.s * p.huelle, zr = fak * p.zRe, zi = fak * p.zIm;
                tSumme += p.walze;
                m0 = p.m0;
                kp = p.kp;
                // Kamm am Ort: Im Z' wechselt von + nach − bei Re Z' > 0 (die Front ist vorbei, der Kamm ist da)
                if (vorIm > 0 && zi <= 0 && zr > 0) {
                    kaemme++;
                    double r = 2 * Math.hypot(zr, zi) / (Walze.GAMMA * h);
                    for (int k = 0; k < SCHWELLEN.length; k++) if (r >= SCHWELLEN[k]) ueber[k]++;
                    if (p.brechen >= 0.5) { brechend++; rBrechSumme += r; }
                }
                vorIm = zi;
            }
            double qb = Waves.brecheranteil(Math.sqrt(8 * m0) / (Walze.GAMMA * h)), tm = tSumme / schritte, c = Walze.c(kp, h);
            double d = Walze.dissipation(Walze.BETA, c, tm);
            if (qb > qbBest) { qbBest = qb; best = orte.indexOf(o); }
            A.info(f("Ort (%.0f, %.0f), %s", o[0], o[1], name), f("Tiefe %.2f m, Hs %.2f m, Hs/h %.2f; %d Kämme, brechend %.3f, Q_b nach Battjes–Janssen %.3f; Dicke der Walze im Mittel %.4f m, c %.2f m/s; Walzen %.1f W/m² gegen dbr %.1f W/m²",
                    h, o[3], o[3] / h, kaemme, kaemme > 0 ? (double) brechend / kaemme : Double.NaN, qb, tm, c, d, o[4]));
            kaemmeSumme += kaemme;
            brechendSumme += brechend;
            qbSumme += qb * kaemme;
            dbrSumme += o[4];
            dSumme += d;
        }
        ob.aufloesung = aufl;
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < SCHWELLEN.length; k++) sb.append(f("%s≥ %.2f: %.3f", k > 0 ? "; " : "", SCHWELLEN[k], kaemmeSumme > 0 ? (double) ueber[k] / kaemmeSumme : Double.NaN));
        A.info("Anteil der Kämme mit H/H_m über der Schwelle, " + name, sb + f(" (Q_b nach Battjes–Janssen im Mittel %.3f; Brechen ab %.2f)", kaemmeSumme > 0 ? qbSumme / kaemmeSumme : Double.NaN, Walze.Q_VON));
        A.info("H/H_m der brechenden Kämme im Mittel, " + name, f("%.2f", brechendSumme > 0 ? rBrechSumme / brechendSumme : Double.NaN));
        double qSim = kaemmeSumme > 0 ? (double) brechendSumme / kaemmeSumme : Double.NaN, qBJ = kaemmeSumme > 0 ? qbSumme / kaemmeSumme : Double.NaN, r = qSim / qBJ;
        A.pruefe(Double.isFinite(r) && r >= QB_VON && r <= QB_BIS, f("Anteil brechender Kämme gegen Q_b nach Battjes und Janssen (1978), %s (%.3f gegen %.3f, %d Kämme)", name, qSim, qBJ, kaemmeSumme),
                f("%.2f", r), f("%.2f bis %.2f", QB_VON, QB_BIS));
        double re = dSumme / dbrSumme;
        A.pruefe(Double.isFinite(re) && re >= ENERGIE_VON && re <= ENERGIE_BIS, f("Dissipation der Walzen (Stive und De Vriend 1994, β_r %.2f) gegen dbr des Nahfelds, %s (%.1f gegen %.1f W/m², Summe über %d Orte)", Walze.BETA, name, dSumme, dbrSumme, orte.size()),
                f("%.2f", re), f("%.2f bis %.2f", ENERGIE_VON, ENERGIE_BIS));
        return best;
    }

    /** Teil Karte gegen Referenz: Weißwasser w der Stufe 0 an 32 × 32 Stellen um den Ort (den mit dem größten Q_b), zu 12 Zeiten. */
    static void karte(Attest A, Seegangsflaeche sf, double[] o, double t0, String name) {
        SeegangKarte k = sf.karte;
        Oberflaeche ob = sf.oberflaeche;
        k.bezug(Math.floor(o[0] / 64) * 64, Math.floor(o[1] / 64) * 64);
        k.fernOst = o[0];
        k.fernNord = o[1];
        int n = k.n, schritt = Math.max(1, n / 32);
        double aufl = ob.aufloesung;
        ob.aufloesung = k.abstand(0);
        Oberflaeche.Punkt p = new Oberflaeche.Punkt();
        double dSumme = 0, dMax = 0, wK = 0, wR = 0;
        int z = 0;
        for (int m = 0; m < 12; m++) {
            double tm = t0 + 3.1 * m;
            k.zeit(tm);
            k.stufen(o[0], o[1], null);
            float[] km = k.kamm(0);
        for (int j = schritt / 2; j < n; j += schritt)
            for (int i = schritt / 2; i < n; i += schritt) {
                double[] x = k.ort(0, i, j);
                ob.punkt(x[0], x[1], tm, p);
                double w = km[4 * (j * n + i) + 2], d = Math.abs(w - p.weiss);
                dSumme += d;
                dMax = Math.max(dMax, d);
                wK += w;
                wR += p.weiss;
                z++;
            }
        }
        ob.aufloesung = aufl;
        A.info("Karte gegen Referenz, " + name, f("%d Stellen der Stufe 0 zu 12 Zeiten (%.2f m) um (%.0f, %.0f): Weißwasser im Mittel %.4f (Karte) gegen %.4f (Referenz), größte Abweichung %.3f", z, k.abstand(0), o[0], o[1], wK / z, wR / z, dMax));
        A.hoechstens("Weißwasser der Karte gegen die Referenz, mittlere Abweichung, " + name, dSumme / Math.max(1, z), KARTE_TOL, "");
    }
    // ------------------------------------------------------------------ seit S5b-4: Kämme am Fuß der Kanten

    /** Grenzen [G], vor der Messung: Anteil gekoppelter Abschnitte im Netz, Anteil passender Kämme, Zeit, Höhe. */
    static final double FUSS_GEKOPPELT = 0.9, FUSS_ANTEIL = 0.95, FUSS_T = 0.05, FUSS_H_REL = 0.03, FUSS_H_ABS = 0.02, FUSS_KLEIN = 0.1;
    /** Seit S5b-5b (Befund am Bild 24.11., „wieder kachelig“): sichtbare Kante der Lippen, 95 % höchstens (m) [G]. */
    static final double LIPPE_KANTE = 0.15;
    /** Seit S5b-5d (Datei 52, vor der Messung): Zerfall – Anteil der sichtbaren Länge in Stücken unter 10 m höchstens, Median der Stücklänge mindestens (m) [G]. */
    static final double LIPPE_KURZ = 0.10, LIPPE_MEDIAN = 20;
    static final double KANTE_ABSTAND = 80;
    /** Seit S5b-5b: Bild von oben auf die Kante – Auge seewärts des Fußes (m), über Wasser (m), Neigung (°). */
    static final double OBEN_ABSTAND = 25, OBEN_HOEHE = 30, OBEN_NEIGUNG = -45;

    /**
     * Teil S5b-4: Blick auf die Kante mit dem höchsten Seegang am Fuß unter allen aktiven des Gebiets (Krone über dem
     * Wasser, Fuß darunter, See läuft auf den Hang zu; Befund L3a: im Umkreis der Orte liegen nur Kanten des Watts, die
     * im Sturm unter Wasser stehen), {@link #KANTE_ABSTAND} m seewärts längs ihrer Normale, {@link #NAH_HOEHE} m über dem
     * Wasser; davor eine eigene Fläche (bau), 240 Bilder mit Fußsuche; dann je bis zu 8 gekoppelte Abschnitte die festen
     * Kämme der Folge gegen die Referenz am Fuß.
     */
    /** Baut eine Fläche für einen Blick (Seegangsflaeche.baue mit Gebiet, Archiv und Zeit des Falls). */
    interface Bau { Seegangsflaeche fuer(Blick b) throws Exception; }

    static void fussTeil(Attest A, Buehne b, Schaumfeld feld, com.dan.render.seegang.Fusssuche fs, Bau bau, double t0, int breite, int hoehe, String name) throws Exception {
        com.dan.render.brandung.Brandungsbild bb = b.brandungsbild();
        if (bb == null || bb.quelle == null) { A.info("Kämme am Fuß, " + name, "keine Brandung im Gebiet"); return; }
        // die Kante: aktiv in einer Brandung ohne Blickfeld und ohne Umkreis
        com.dan.core.szene.Brandung such = new com.dan.core.szene.Brandung(bb.brandung.e0, bb.brandung.n0);
        such.weite = 1e9;
        such.abschnitteMax = Integer.MAX_VALUE;
        such.protokollieren = true;
        double[] w = bb.quelle.wind();
        such.rechne(bb.kanten, bb.quelle.wasser(), t0, bb.brandung.e0, bb.brandung.n0, w[0], w[1]);
        double[] wahl = null;
        int[] jeArt = new int[6];
        for (double[] q : such.protokoll) {
            if (wahl == null || q[6] > wahl[6]) wahl = q;
            jeArt[Math.min(5, bb.kanten.kanten.get((int) q[0]).art())]++;
        }
        A.info("Aktive Kanten im Gebiet, " + name, f("%d (Deckwerk %d, Warft %d, Deich %d, Kante %d)", such.protokoll.size(), jeArt[5], jeArt[4], jeArt[3], jeArt[0] + jeArt[1] + jeArt[2]));
        if (wahl == null) { A.pruefe(false, "aktive Kante im Gebiet, " + name, "keine", "mindestens eine"); return; }
        com.dan.core.szene.Kanten.Kante ka = bb.kanten.kanten.get((int) wahl[0]);
        double ost = ka.ost() + KANTE_ABSTAND * ka.nx(), nord = ka.nord() + KANTE_ABSTAND * ka.ny();
        double[] wa = b.wasserUndSeegang(ost, nord);
        double eta = wa != null && Double.isFinite(wa[0]) ? wa[0] : 0;
        Blick bl = new Blick(ost, nord, eta + NAH_HOEHE, Math.toDegrees(Math.atan2(-ka.nx(), -ka.ny())), NAH_NEIGUNG, 60);
        bl.groesse(breite, hoehe);
        A.info("Blick auf eine Kante, " + name, f("Kante %d (Art %d, Fuß %.2f m, Krone %.2f m NHN, 1:%.1f) bei (%.0f, %.0f) mit Hs am Fuß %.2f m und Tp %.1f s (die höchste von %d aktiven); Auge %.0f m seewärts in %.1f m NHN, Richtung %.0f°",
                (int) wahl[0], ka.art(), ka.fuss(), ka.krone(), 1 / ka.neigung(), ka.ost(), ka.nord(), wahl[6], wahl[7], such.protokoll.size(), KANTE_ABSTAND, bl.hoehe, bl.richtung));
        Seegangsflaeche sf = bau.fuer(bl);
        try {
            A.info("Fläche vor der Kante, " + name, sf.bericht);
            paare(A, sf, "vor der Kante, " + name);                          // seit S5b-6b
            com.dan.core.szene.Reflexion.Bilanz bf = sf.reflexion != null ? sf.reflexion.bilanzFein : null;
            // seit S6 (SF59 A): Auskunft mit Ziel statt Prüfstein; bekannte Abweichungen A2 (Spiegelung an den Achsen, Ziel S5b-6c) und A3
            A.info("Bauch an den Wänden des feinen Gitters: Kohärenz nachher (Gewicht √(E_e E_r) · cos, alle Wandflächen) (Auskunft, früher Prüfstein; A2), " + name,
                    bf == null ? "–" : f("%.2f (vorher %.2f; %.0f %% des Gewichts in geraden Stücken, dort %.2f) (Ziel ≥ %.2f)", bf.nachher, bf.vorher, 100 * bf.anteilStuecke, bf.nachherStuecke, KOHAERENZ_FEIN));
            A.info("Bauch an den Wänden des feinen Gitters, in den geraden Stücken (seit S5b-6b2) (Auskunft, früher Prüfstein; A3), " + name,
                    bf == null ? "–" : f("%.2f (%d Stücke, %.0f %% des Gewichts) (Ziel ≥ %.2f)", bf.nachherStuecke, bf.stuecke, 100 * bf.anteilStuecke, KOHAERENZ_STUECKE));
            A.pruefe(bf != null, "Bilanz der Reflexion im feinen Gitter liegt vor, " + name, bf == null ? "fehlt" : "ja", "vorhanden");
            kanteBilder(A, b, bb, sf, feld, fs, ka, bl, t0, breite, hoehe, name);
        } finally {
            b.seegang(null);
            b.geraet().ruhe();
            sf.close();
        }
    }

    /** Seit S5b-6b: Kohärenz an den Wänden des feinen Gitters vor der Kante, mindestens [G] (Datei 56). */
    static final double KOHAERENZ_FEIN = 0.7;
    /** Seit S5b-6b2: dasselbe nur über die Flächen gerader Stücke, mindestens [G] (Datei 58). */
    static final double KOHAERENZ_STUECKE = 0.8;

    /** Seit S5b-6b: Hat das feine Gitter Wände, hat die Fläche Paare der Reflexion (Datei 56, U3). */
    static void paare(Attest A, Seegangsflaeche sf, String name) {
        com.dan.core.szene.Reflexion r = sf.reflexion;
        if (r == null || !com.dan.lauf.Seegangsflaeche.REFLEXION) return;
        if (r.flaechenFein == 0) { A.info("Paare der Reflexion, " + name, "das feine Gitter hat keine Wand"); return; }
        A.pruefe(r.paare > 0, "Paare der Reflexion, wo das feine Gitter Wände hat, " + name, f("%d Paare bei %d Wandflächen im feinen Gitter", r.paare, r.flaechenFein), "mehr als 0");
    }

    static void kanteBilder(Attest A, Buehne b, com.dan.render.brandung.Brandungsbild bb, Seegangsflaeche sf, Schaumfeld feld, com.dan.render.seegang.Fusssuche fs, com.dan.core.szene.Kanten.Kante ka, Blick bl, double t0, int breite, int hoehe, String name) throws Exception {
        double ost = bl.ost, nord = bl.nord;
        sf.karte.bezug(Math.floor(ost / 64) * 64, Math.floor(nord / 64) * 64);
        sf.karte.fernOst = ost;
        sf.karte.fernNord = nord;
        com.dan.core.szene.Fusswellen fw = new com.dan.core.szene.Fusswellen();
        fs.leeren();
        sf.bild.fuss = fs;
        sf.bild.fuesse = fw;
        bb.fuesse = fw;
        bb.brandung.protokollieren = true;
        bb.brandung.lippenSumme = 0;
        boolean lippenVor = bb.brandung.lippen;
        // seit S5b-5 abgeschlossen ohne Lippe (Datei 55): wie die Anwendung, die Bilder zeigen ihren Stand
        com.dan.core.szene.Lippenpruefung lp = new com.dan.core.szene.Lippenpruefung();
        bb.brandung.nachBild = br -> lp.bild(br.lippenProtokoll);       // seit S5b-5b: jedes Bild an den Kanten des Gebiets
        try {
            SchaumCheck.bilder(A, sf, feld, bl, t0, breite, hoehe, "S5b4_Kante_" + name, false);
        } finally {
            sf.bild.fuss = null;
            sf.bild.fuesse = null;
            bb.fuesse = null;
            bb.brandung.nachBild = null;
        }
        com.dan.core.szene.Brandung br = bb.brandung;
        double tEnde = bb.quelle.zeit();
        A.info("Brandung im Blick auf die Kante, " + name, f("aktiv %d, im Netz %d, gefragt %d, den Kämmen der Fläche gefolgt %d; Überläufe im letzten Bild %d; Fusswellen: %d Suchen, im letzten Ergebnis %d Füße, %d in der Clipmap, %d Folgen",
                br.aktiv, br.imNetz, br.angefragt, br.gekoppelt, br.ueberlaeufe, fw.suchen, fw.fuesseZuletzt, fw.gueltigZuletzt, fw.folgenZahl));
        if (br.angefragt == 0) { A.pruefe(false, "aktive Abschnitte im Netz im Blick auf die Kante, " + name, "keiner", "mindestens einer"); br.protokollieren = false; br.lippen = lippenVor; return; }
        // seit S5b-4c: nur Abschnitte, an deren Fuß die Stufe der Clipmap Wellen zeichnet (sonst bleibt die eigene Folge)
        int imNetz = 0, mitWellen = 0, gekoppelt = 0;
        for (double[] q : br.protokoll) {
            double[] st = fw.stand((int) q[0]);
            if (st == null) continue;
            imNetz++;
            if (!(st[0] >= com.dan.core.szene.Fusswellen.GEZEICHNET_MIN * st[1]) || st[2] < 1) continue;
            mitWellen++;
            if (q.length >= 9 && q[8] == 1) gekoppelt++;
        }
        int[] lt = new int[5];
        for (double[] q : br.lippenProtokoll) lt[(int) q[5]]++;
        A.info("Lippen im Netz (seit S5b-5), " + name, f("in allen Bildern %d, im letzten %d (Typ nach ξ: Sturz %d, Reflexion %d, sonst %d); Brecher der Abschnitte im letzten Bild: Schwall %d, Sturz %d, Reflexion %d",
                br.lippenSumme, br.lippenZahl, lt[com.dan.core.szene.Brecherform.STURZ], lt[com.dan.core.szene.Brecherform.REFLEXION], br.lippenZahl - lt[com.dan.core.szene.Brecherform.STURZ] - lt[com.dan.core.szene.Brecherform.REFLEXION],
                br.jeTyp[com.dan.core.szene.Brecherform.SCHWALL], br.jeTyp[com.dan.core.szene.Brecherform.STURZ], br.jeTyp[com.dan.core.szene.Brecherform.REFLEXION]));
        A.info("Lippen über alle Bilder (seit S5b-5b), " + name, lp.bericht());
        if (lp.enden > 0)
            A.info("Lippe (Auskunft, früher Prüfsteine), " + name, f("Zerfall %.0f %% kurz, Median %.1f m (Ziel ≤ %.0f %%, ≥ %.0f m); sichtbare Kante 95 %% %.3f m (Ziel ≤ %.2f m)",
                    100 * lp.anteilKurz(10), com.dan.core.szene.Lippenpruefung.q(lp.stuecke, 0.5), 100 * LIPPE_KURZ, LIPPE_MEDIAN, com.dan.core.szene.Lippenpruefung.q(lp.kante, 0.95), LIPPE_KANTE));
        A.info("Abschnitte im Netz mit Suche, " + name, f("%d; davon zeichnet die Stufe am Fuß mindestens %.2f H_s und hat Kämme: %d", imNetz, com.dan.core.szene.Fusswellen.GEZEICHNET_MIN, mitWellen));
        double ag = mitWellen > 0 ? (double) gekoppelt / mitWellen : 0;
        A.pruefe(mitWellen > 0 && ag >= FUSS_GEKOPPELT, "Abschnitte mit gezeichneten Wellen am Fuß, die den Kämmen der Fläche folgen (letztes Bild), " + name, f("%.3f (%d von %d)", ag, gekoppelt, mitWellen), f("≥ %.2f", FUSS_GEKOPPELT));
        // feste Kämme der Folge gegen die Referenz (bis zu 8 gekoppelte Abschnitte, die nächsten zuerst)
        Oberflaeche ob = sf.oberflaeche;
        double aufl = ob.aufloesung;
        Oberflaeche.Punkt p = new Oberflaeche.Punkt();
        int kaemme = 0, gut = 0, abschnitte = 0;
        double dtMax = 0, dhMax = 0;
        for (double[] q : br.protokoll) {
            if (q.length < 9 || q[8] != 1 || abschnitte >= 8) continue;
            int nr = (int) q[0];
            com.dan.core.szene.Fusswellen.Folge fo = fw.folge(nr, tEnde);
            if (fo == null || fo.stufe == 0) continue;
            abschnitte++;
            com.dan.core.szene.Kanten.Kante k = bb.kanten.kanten.get(nr);
            ob.aufloesung = sf.karte.abstand(Math.abs(fo.stufe) - 1);
            double nahW = fo.stufe < 0 ? Double.NaN : 1;
            for (com.dan.core.szene.Fusswellen.Kamm c : fo.kaemme) {
                if (!c.fest || c.linear < FUSS_KLEIN * fo.hs || Double.isNaN(nahW)) continue;
                // Referenz: θ fällt durch 0 nahe c.zeit (± ein Viertel von T_m01, 100 Hz)
                double best = Double.NaN, hBest = Double.NaN, thV = Double.NaN, tV = 0, hV = 0;
                for (int s = -25; s <= 25; s++) {
                    double t = c.zeit + s * 0.01 * Math.max(1, fo.tm01) / 2.5;
                    ob.punkt(k.ost(), k.nord(), t, p, 1);
                    double za = Math.hypot(p.zRe, p.zIm), th = Math.atan2(p.zIm, p.zRe), hl = 2 * za;
                    if (!Double.isNaN(thV)) {
                        double dth = th - thV;
                        dth -= 2 * Math.PI * Math.rint(dth / (2 * Math.PI));
                        if (thV > 0 && dth < 0 && thV + dth <= 0) {
                            double a = thV / -dth, tc = tV + a * (t - tV);
                            if (Double.isNaN(best) || Math.abs(tc - c.zeit) < Math.abs(best - c.zeit)) { best = tc; hBest = hV + a * (hl - hV); }
                        }
                    }
                    thV = th; tV = t; hV = hl;
                }
                kaemme++;
                double dt = Math.abs(best - c.zeit), dh = Math.abs(hBest - c.linear);
                if (Double.isFinite(dt)) { dtMax = Math.max(dtMax, dt); dhMax = Math.max(dhMax, dh); }
                if (dt <= FUSS_T && dh <= Math.max(FUSS_H_ABS, FUSS_H_REL * hBest)) gut++;
            }
        }
        ob.aufloesung = aufl;
        br.protokollieren = false;
        A.info("Feste Kämme der Folge gegen die Referenz, " + name, f("%d Kämme an %d Abschnitten (ohne die unter %.1f H_s); größte Abweichung %.3f s, 2|Z| %.3f m", kaemme, abschnitte, FUSS_KLEIN, dtMax, dhMax));
        double an = kaemme > 0 ? (double) gut / kaemme : 0;
        A.pruefe(kaemme > 0 && an >= FUSS_ANTEIL, f("feste Kämme am Fuß gegen die Referenz in double (Zeit ≤ %.2f s, 2|Z| ≤ %.0f %% oder %.0f cm), %s", FUSS_T, 100 * FUSS_H_REL, 100 * FUSS_H_ABS, name),
                f("%.3f (%d von %d)", an, gut, kaemme), f("≥ %.2f", FUSS_ANTEIL));
        // seit S5b-5b: dazu von oben auf die Kante, wie im Bild der Anwendung vom 24.11. (eine halbe Sekunde weiter); erst
        // nach dem Vergleich mit der Referenz, weil das nahe Auge die Stufen am Fuß ändert (Befund L3e)
        sf.bild.fuss = fs;
        sf.bild.fuesse = fw;
        bb.fuesse = fw;
        try {
            double eta = bl.hoehe - NAH_HOEHE;
            Blick oben = new Blick(ka.ost() + OBEN_ABSTAND * ka.nx(), ka.nord() + OBEN_ABSTAND * ka.ny(), eta + OBEN_HOEHE, bl.richtung, OBEN_NEIGUNG, 60);
            oben.groesse(breite, hoehe);
            var bel = b.belichtung();
            double evMin0 = bel.evMin, evMax0 = bel.evMax, ev = bel.ev();
            bel.evMin = bel.evMax = ev;
            bel.setze(ev);
            sf.bild.schaum = feld;
            float[] px = null;
            double tz = sf.bild.zeit;
            for (int s = 1; s <= 15; s++) {
                sf.bild.zeit = tz + s / 30.0;
                px = b.standbild(oben, sf.bild.zeit);
            }
            javax.imageio.ImageIO.write(SeegangBilder.bild(px, breite, hoehe), "png", Path.of("docs", "status", "S5b5_Lippen_" + name + "_oben.png").toFile());
            A.info("Bild von oben (seit S5b-5b), " + name, f("docs/status/S5b5_Lippen_%s_oben.png (Auge %.0f m seewärts des Fußes, %.0f m über Wasser, %.0f° geneigt)", name, OBEN_ABSTAND, OBEN_HOEHE, OBEN_NEIGUNG));
            sf.bild.schaum = null;
            bel.evMin = evMin0;
            bel.evMax = evMax0;
            bel.sofort();
        } finally {
            sf.bild.fuss = null;
            sf.bild.fuesse = null;
            bb.fuesse = null;
            bb.brandung.lippen = lippenVor;
        }
    }
}
