package com.dan.lauf.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Seegangsnetz;
import com.dan.core.szene.Seegangsoptik;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Seegangsflaeche;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.seegang.SeegangKarte;
import com.dan.render.seegang.Schaumfeld;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S4a (Seegang Werkbuch 35; SF42–SF45 A): das Schaumfeld ({@link Schaumfeld}) mit Flächen aus dem Archiv, ohne
 * den Wart (die Fläche wird hier selbst gebaut, mit den Senken des Nahfelds, {@link Seegangsflaeche#SENKEN}).
 * <ol>
 * <li>Sturm 25.10.2026 13:30 MEZ (Szenario „seegang“, Ansicht „brandung“): die Fläche; als Ort der Prüfung die Zelle des
 * feinen Gitters mit dem kleinsten Hs/h (am wenigsten tiefenbegrenzt), mindestens 8 Zellen vom Rand.</li>
 * <li>Energie: 240 Zeitpunkte über 120 s; auf der feinsten Stufe der Clipmap (32 m, 0,25 m) die brechenden Kämme (wie der
 * Kernel: (1 − J)/λ über der Schwelle nach Hulin u. a. 2025; Kamm: höchster Punkt längs der Laufrichtung), je Kamm
 * ε = b ρ c⁵ / g (Duncan 1981) mit b = 0,4 (S − 0,08)^{5/2} (Drazen u. a. 2008, S die lineare Steilheit am Kamm) und c aus
 * der Krümmung des Kamms (k² = −η''/|Z|); die Summe je Fläche gegen {@code dwc} des Nahfelds.</li>
 * <li>Feld: 60 s Modellzeit zu 30 Schritten je s am Ort der Prüfung; die Bedeckung, wo die Wellen nicht tiefenbegrenzt
 * sind (Hs/h &lt; 0,3, Tiefe über 3 m), gegen Monahan und O'Muircheartaigh (1980) und gegen die Statistik von heute
 * (diss / D_KAL, F161 A); Kosten der Schritte auf der Karte.</li>
 * <li>Masse: nur Transport mit der Drift der Fläche, ein Fleck in jedem Raster, 10 s.</li>
 * <li>Konvergenz: nur Transport mit der Prüfdrift u = −a (x − x_p), a = 0,05/s, gleichmäßig f₀: In der Mitte wächst f wie
 * f₀ e^{a t} (∂f/∂t + ∇·(u f) = 0), 10 s.</li>
 * <li>Bilder: die Ansicht mit Feld und mit der Statistik von heute (feste Belichtung), 8 s Feld vorher.</li>
 * <li>Xaver 5.12.2013 14:30 MEZ (dieselbe Ansicht): Bedeckung gegen Monahan wie in 3, ein Bild.</li>
 * </ol>
 * Seit S4a-2 (Befund L2q) ist die Energie nur Auskunft: dwc des Nahfelds ist bei Sturm fast 0, und die Fläche hat keine
 * Stelle über der Schwelle; die Köpfe der unaufgelösten Windsee ({@link Schaumfeld#WINDSEE}) tragen die Bedeckung.
 * <p>
 * Prüfsteine: Bedeckung gegen Monahan × 0,5 bis × 2 [G], je Szenario; Masse ± 1 % [G]
 * je Raster; Konvergenz gegen e^{a t} ± 5 % [G] je Raster; Kosten der Schritte ≤ 2 ms je Bild [G].
 * <pre>  SchaumCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--breite 1920 --hoehe 1080]</pre>
 */
public final class SchaumCheck {
    static final double RHO = 1025, G = 9.81, ENERGIE_TOL = 0.20, MONAHAN_VON = 0.5, MONAHAN_BIS = 2, MASSE_TOL = 0.01, KONV_TOL = 0.05, KOSTEN_MS = 2;
    static final double PRUEF_A = 0.05, HS_H = 0.3, H_TIEF = 3, KH_MIN = 2 * Math.PI / 6;
    static final int SCHRITTE_JE_S = 30;

    static Buehne b;
    static double t;

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
        Attest A = new Attest("Seegang, Schaumfeld (S4a)", "Seegang Werkbuch");
        System.out.println("SchaumCheck – S4a (Seegang Werkbuch)");
        long start = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            Szenario sturm = szenario(sz, "seegang"), xaver = szenario(sz, "xaver");
            Ansicht an = sz.ansicht("brandung");
            b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            Seegangsflaeche.SENKEN = true;
            Schaumfeld feld = null;
            try {
                Geraet g = b.geraet();
                feld = new Schaumfeld(g, b.konto());
                A.info("Schaumfeld", f("Version %s; fein %d² zu %.2f m, grob %d² zu %.1f m; Lebensdauer %.0f s offen, %.0f s im Watt (unter %.0f m); Winddrift %.0f %% U10; Faktor auf W_A der Köpfe %.2f",
                        Schaumfeld.VERSION, Schaumfeld.N_FEIN, Schaumfeld.D_FEIN, Schaumfeld.N_GROB, Schaumfeld.D_GROB, Schaumfeld.TAU_OFFEN, Schaumfeld.TAU_WATT,
                        Schaumfeld.WATT_BIS, 100 * Schaumfeld.WINDDRIFT, Schaumfeld.WINDSEE_FAKTOR));

                // ---------------------------------------------------------------- 1 Sturm: Fläche und Ort
                A.teil("1 Sturm 25.10., Fläche und Ort der Prüfung");
                b.oeffne(sturm);
                Blick bl = blick(an, "2026-10-25T12:30:00Z", breite, hoehe);
                b.wasserbild().seegang = true;
                b.brandung(true);
                b.standbild(bl, t);
                double tSturm = t;
                Seegangsflaeche sf = Seegangsflaeche.baue(g, b.konto(), sz.gebiet(), sturm.archiv.toPath(), b.seegangsnetze(), bl, t, null);
                try {
                    A.info("Fläche", sf.bericht);
                    double[] ort = ort(sf);
                    double ex = ort[0], ey = ort[1];
                    double u10 = Math.hypot(b.bild().windU, b.bild().windV);
                    A.info("Ort der Prüfung", f("(%.0f, %.0f), %.0f m vom Auge der Ansicht; Tiefe %.1f m, Hs %.2f m, Hs/h %.2f; dwc %.2f W/m², dbr %.2f W/m²; U10 %.1f m/s",
                            ex, ey, Math.hypot(ex - bl.ost, ey - bl.nord), ort[2], ort[3], ort[3] / ort[2], ort[4], ort[5], u10));
                    SeegangKarte k = sf.karte;
                    k.bezug(Math.floor(ex / 64) * 64, Math.floor(ey / 64) * 64);
                    k.fernOst = ex;
                    k.fernNord = ey;
                    A.info("Drift", f("%s, größter Betrag %.2f m/s; U10 des Nahfelds für die Köpfe der Windsee bis %.1f m/s", k.hatDrift() ? "geladen" : "fehlt", k.driftMax(), k.windMax()));

                    // ------------------------------------------------------------ 2 Energie
                    A.teil("2 Energie der brechenden Kämme gegen dwc");
                    energie(A, b, sf, ex, ey, tSturm);

                    // ------------------------------------------------------------ 3 Feld
                    A.teil("3 Feld: Bedeckung und Kosten (Sturm)");
                    // seit S4a-3: erst mit dem Faktor aus der Herleitung (Auskunft), dann, wenn das Feld mehr als 25 % neben W_Z
                    // liegt, mit dem Faktor W_Z / Feld (Bedeckung etwa linear im Anteil der Köpfe); der gilt auch für Xaver,
                    // das so die unabhängige Probe ist
                    feld.windseeFaktor = Schaumfeld.WINDSEE_FAKTOR;
                    double q = feldLauf(A, g, feld, sf, ex, ey, tSturm, u10, "Sturm, Faktor " + f("%.2f", feld.windseeFaktor), false);
                    double fk = feld.windseeFaktor;
                    if (Double.isFinite(q) && q > 0 && (q < KAL_VON || q > KAL_BIS)) fk = Math.max(0.05, Math.min(20, fk / q));
                    A.info("Kalibrierung der Köpfe (seit S4a-3)", f("Feld gegen W_Z %.2f mit dem Faktor %.2f; weiter mit dem Faktor %.2f auf W_A (Sturm und Xaver)", q, feld.windseeFaktor, fk));
                    feld.windseeFaktor = fk;
                    feldLauf(A, g, feld, sf, ex, ey, tSturm, u10, "Sturm", true);

                    // ------------------------------------------------------------ 4 Masse
                    A.teil("4 Masse: nur Transport, 10 s");
                    masse(A, g, feld, k, ex, ey, tSturm);

                    // ------------------------------------------------------------ 5 Konvergenz
                    A.teil("5 Konvergenz: Prüfdrift u = −a (x − x_p), 10 s");
                    konvergenz(A, g, feld, k, ex, ey, tSturm);
                    feld.schalter = Schaumfeld.QUELLE | Schaumfeld.ZERFALL | Schaumfeld.TRANSPORT | Schaumfeld.WINDSEE;

                    // ------------------------------------------------------------ 6 Bilder
                    A.teil("6 Bilder (Sturm)");
                    k.bezug(Math.floor(bl.ost / 64) * 64, Math.floor(bl.nord / 64) * 64);
                    k.fernOst = bl.ost;
                    k.fernNord = bl.nord;
                    bilder(A, sf, feld, bl, tSturm, breite, hoehe, "S4a_Schaum_Sturm", true);
                } finally {
                    b.seegang(null);
                    g.ruhe();
                    sf.close();
                }

                // ---------------------------------------------------------------- 7 Xaver
                A.teil("7 Xaver 5.12.2013");
                b.oeffne(xaver);
                Blick bx = blick(an, "2013-12-05T13:30:00Z", breite, hoehe);
                b.standbild(bx, t);
                double tX = t;
                Seegangsflaeche sx = Seegangsflaeche.baue(g, b.konto(), sz.gebiet(), xaver.archiv.toPath(), b.seegangsnetze(), bx, t, null);
                try {
                    A.info("Fläche", sx.bericht);
                    double[] ort = ort(sx);
                    double u10 = Math.hypot(b.bild().windU, b.bild().windV);
                    A.info("Ort der Prüfung", f("(%.0f, %.0f); Tiefe %.1f m, Hs %.2f m, Hs/h %.2f; dwc %.2f W/m²; U10 %.1f m/s", ort[0], ort[1], ort[2], ort[3], ort[3] / ort[2], ort[4], u10));
                    SeegangKarte k = sx.karte;
                    A.info("Drift", f("%s, größter Betrag %.2f m/s; U10 des Nahfelds für die Köpfe der Windsee bis %.1f m/s", k.hatDrift() ? "geladen" : "fehlt", k.driftMax(), k.windMax()));
                    k.bezug(Math.floor(ort[0] / 64) * 64, Math.floor(ort[1] / 64) * 64);
                    k.fernOst = ort[0];
                    k.fernNord = ort[1];
                    feldLauf(A, g, feld, sx, ort[0], ort[1], tX, u10, "Xaver", true);
                    k.bezug(Math.floor(bx.ost / 64) * 64, Math.floor(bx.nord / 64) * 64);
                    k.fernOst = bx.ost;
                    k.fernNord = bx.nord;
                    bilder(A, sx, feld, bx, tX, breite, hoehe, "S4a_Schaum_Xaver", false);
                } finally {
                    b.seegang(null);
                    g.ruhe();
                    sx.close();
                }
            } finally {
                Seegangsflaeche.SENKEN = false;
                if (feld != null) { b.geraet().ruhe(); feld.close(); }
                b.close();
            }
            A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - start) / 1e9));
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "S4a_Schaum.md"), "S4a – das Schaumfeld");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ==================================================================== Teile

    /**
     * Ort der Prüfung: die Zelle des feinen Gitters (mindestens 8 Zellen vom Rand, Tiefe über H_TIEF) mit dem kleinsten
     * Hs/h. Zurück {Ost, Nord, Tiefe, Hs, dwc, dbr} (dwc, dbr aus den Senken des Nahfelds, NaN ohne).
     */
    static double[] ort(Seegangsflaeche sf) {
        Oberflaeche.Stufe s = sf.oberflaeche.fein;
        int kw = s.kennWerte(), best = -1;
        double bq = Double.POSITIVE_INFINITY;
        for (int j = 8; j < s.ny - 8; j++)
            for (int i = 8; i < s.nx - 8; i++) {
                int c = j * s.nx + i;
                double h = s.h[c], hs = 4 * Math.sqrt(Math.max(0, s.kenn[c * kw]));
                if (!(h > H_TIEF) || !(hs > 0.1)) continue;
                if (hs / h < bq) { bq = hs / h; best = c; }
            }
        if (best < 0) throw new IllegalStateException("kein Ort im feinen Gitter mit Tiefe über " + H_TIEF + " m und Seegang");
        int i = best % s.nx, j = best / s.nx;
        double dwc = sf.senkenFein != null ? sf.senkenFein[4 * best] : Double.NaN, dbr = sf.senkenFein != null ? sf.senkenFein[4 * best + 1] : Double.NaN;
        return new double[] {s.x0 + (i + 0.5) * s.dx, s.y0 + (j + 0.5) * s.dx, s.h[best], 4 * Math.sqrt(Math.max(0, s.kenn[best * kw])), dwc, dbr};
    }

    /** Teil 2: die Energie der brechenden Kämme auf der feinsten Stufe gegen dwc. */
    static void energie(Attest A, Buehne b, Seegangsflaeche sf, double ex, double ey, double t0) {
        SeegangKarte k = sf.karte;
        Oberflaeche.Stufe s = sf.oberflaeche.fein;
        double[] w = Seegangsnetz.wertGemischt(b.seegangsnetze(), ex, ey, t0);
        double rx = 1, ry = 0;
        if (w != null && Math.hypot(w[2], w[3]) > 1e-6) { double l = Math.hypot(w[2], w[3]); rx = w[2] / l; ry = w[3] / l; }
        int n = k.n, rand = 3;
        double d0 = k.abstand(0), laenge = d0 / Math.max(Math.abs(rx), Math.abs(ry)), flaeche = Math.pow((n - 2 * rand) * d0, 2);
        int proben = 240;
        double dSumme = 0, dwcSumme = 0, aktivSumme = 0, kaemme = 0, cSumme = 0, sSumme = 0, bSumme = 0, khpSumme = 0, khESumme = 0;
        int khZahl = 0;
        List<Double> dJe = new ArrayList<>();
        for (int p = 0; p < proben; p++) {
            double ts = t0 + 0.5 * p;
            k.zeit(ts);
            k.stufen(ex, ey, null);
            float[] v = k.verschiebung(0), nn = k.normale(0), km = k.kamm(0), au = k.aufgeloest(0);
            double d = 0, dwcHier = 0;
            int aktiv = 0, zellen = 0;
            for (int j = rand; j < n - rand; j++)
                for (int i = rand; i < n - rand; i++) {
                    int ix = j * n + i;
                    double[] o = k.ort(0, i, j);
                    double h = wert(s, s.h, o[0], o[1]);
                    if (p == 0) { dwcHier += senken(sf, s, o[0], o[1]); zellen++; }
                    double lam = km[4 * ix], J = nn[4 * ix + 3], eta = v[4 * ix + 2], za = v[4 * ix + 3];
                    // wie der Kernel (seit dem Befund nach L2p): kh der Wellen, die die Stufe auflöst, nicht des Gipfels
                    double khE = au[2 * ix + 1] > 0 ? Math.sqrt(au[2 * ix] / au[2 * ix + 1]) * Math.max(h, 0.05) : 0;
                    if (p == 0) { khpSumme += km[4 * ix + 1]; khESumme += khE; khZahl++; }
                    if (!(lam > 0) || !(khE > KH_MIN)) continue;
                    double sl = (1 - J) / lam;
                    if (sl < schwelle(khE)) continue;
                    aktiv++;
                    double ep = eta(v, n, i + rx, j + ry), em = eta(v, n, i - rx, j - ry);
                    if (!(eta > 0) || eta < ep || eta < em) continue;
                    double ess = (ep - 2 * eta + em) / (d0 * d0);
                    if (!(ess < 0) || !(za > 1e-3)) continue;
                    double kl = Math.sqrt(-ess / za), c = Math.sqrt(G / kl * Math.tanh(kl * Math.max(h, 0.05)));
                    double bb = sl > 0.08 ? 0.4 * Math.pow(sl - 0.08, 2.5) : 0;
                    d += bb * RHO * Math.pow(c, 5) / G * laenge;
                    kaemme++;
                    cSumme += c;
                    sSumme += sl;
                    bSumme += bb;
                }
            if (p == 0) dwcSumme = dwcHier / Math.max(1, zellen);
            d /= flaeche;
            dJe.add(d);
            dSumme += d;
            aktivSumme += (double) aktiv / ((n - 2 * rand) * (n - 2 * rand));
        }
        double dm = dSumme / proben;
        A.info("Laufrichtung (Seegangsnetz) und Fläche der Auswertung", f("(%.2f, %.2f); %.0f m² auf der Stufe 0 (%.2f m), %d Zeitpunkte über %.0f s", rx, ry, flaeche, d0, proben, 0.5 * (proben - 1)));
        A.info("kh auf der Stufe 0", f("des Gipfels k_p h im Mittel %.2f; der aufgelösten Wellen k_eff h im Mittel %.2f (die Schwelle gilt ab %.2f)", khpSumme / Math.max(1, khZahl), khESumme / Math.max(1, khZahl), KH_MIN));
        A.info("brechende Kämme", f("%.0f Punkte auf Kämmen, im Mittel c = %.2f m/s, S = %.3f, b = %.4f; aktiv (über der Schwelle) im Mittel %.2f %% der Fläche",
                kaemme, kaemme > 0 ? cSumme / kaemme : Double.NaN, kaemme > 0 ? sSumme / kaemme : Double.NaN, kaemme > 0 ? bSumme / kaemme : Double.NaN, 100 * aktivSumme / proben));
        double[] dq = dJe.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        A.info("Dissipation der Kämme je Zeitpunkt", f("Median %.3f W/m², 90 %% %.3f W/m², größter %.3f W/m²", dq[dq.length / 2], dq[(int) (0.9 * (dq.length - 1))], dq[dq.length - 1]));
        double r = dm / dwcSumme;
        // seit S4a-2 (Befund L2q) nur Auskunft: dwc des Nahfelds war bei 16–22 m/s Wind fast 0 (0,002 und 0,02 W/m²), das
        // Nahfeld trägt die kurze Windsee nicht; die Fläche ebenso wenig (keine Stelle über der Schwelle)
        A.info(f("Dissipation der aufgelösten brechenden Kämme gegen dwc des Nahfelds (%.3f gegen %.3f W/m²; Auskunft seit S4a-2)", dm, dwcSumme), f("%.2f", r));
    }

    /** Gilt das Feld gegen W_Z in diesem Band, bleibt der Faktor auf W_A (seit S4a-3). */
    static final double KAL_VON = 0.8, KAL_BIS = 1.25;

    /**
     * Teil 3 und 7: 60 s Feld am Ort, Bedeckung gegen Monahan und die Statistik, Kosten. Ohne {@code pruefen} nur Auskunft
     * (seit S4a-3, der Lauf vor der Kalibrierung). Gibt Feld gegen W_Z.
     */
    static double feldLauf(Attest A, Geraet g, Schaumfeld feld, Seegangsflaeche sf, double ex, double ey, double t0, double u10, String name, boolean pruefen) {
        SeegangKarte k = sf.karte;
        feld.schalter = Schaumfeld.QUELLE | Schaumfeld.ZERFALL | Schaumfeld.TRANSPORT | Schaumfeld.WINDSEE;
        feld.an = true;
        feld.leeren();
        double dt = 1.0 / SCHRITTE_JE_S;
        int schritte = 60 * SCHRITTE_JE_S;
        List<Double> fein = new ArrayList<>(), grob = new ArrayList<>(), kopf = new ArrayList<>();
        double bedeckung = 0;
        int messungen = 0;
        int[] zellenTief = new int[1];
        int raster = -1;
        try (Stoppuhr su = new Stoppuhr(g, g.rechnen, 64)) {
            for (int s = 0; s <= schritte; s++) {
                double ts = t0 + s * dt;
                Ablauf ab = new Ablauf(g);
                k.zeit(ab, ts);
                k.bild(ab, ex, ey);
                feld.schritt(ab, k, ts, ex, ey);
                g.rechnen.einmal(c -> { su.runde(c); ab.aufnehmen(c, su); });
                su.sammle(true);
                if (s > 10 * SCHRITTE_JE_S) {
                    fein.add(su.letzte("see_schaum fein"));
                    grob.add(su.letzte("see_schaum grob"));
                    kopf.add(su.letzte("Schaumfeld, Kopf"));
                }
                if (s >= 40 * SCHRITTE_JE_S && s % (2 * SCHRITTE_JE_S) == 0) {
                    if (raster < 0) raster = tiefeZellen(sf, feld, 0) >= 1000 ? 0 : 1;
                    double[] bd = bedeckung(sf, feld, raster, zellenTief);
                    bedeckung += bd[0];
                    messungen++;
                }
            }
            if (!su.verfuegbar) A.info("Kosten", "die Warteschlange hat keine Zeitstempel");
        }
        double wf = bedeckung / Math.max(1, messungen), wm = 3.84e-6 * Math.pow(u10, 3.41), ws = statistik(sf, b.seegangsnetze(), feld, raster, t0);
        double wc = 4.82e-6 * Math.pow(u10 + 1.98, 3), wz = Math.sqrt(wc * wm);
        A.info("Ziel der Windsee (seit S4a-2)", f("W_Z = √(W_Callaghan · W_Monahan) = √(%.4f · %.4f) = %.4f; Feld gegen W_Z %.2f", wc, wm, wz, wf / wz));
        double[] z = feld.zuletzt();
        A.info("Schritt", f("Δt %.4f s, %d Teilschritte, Transport über %.4f s%s", z[1], (int) z[0], z[2], z[3] > 0 ? " (gekappt)" : ""));
        double[] al = raster >= 0 ? albedo(sf, feld, raster) : new double[] {Double.NaN, Double.NaN};
        if (raster >= 0)
            A.info("Optik (S4b), " + name, f("Bildanteil Schaum im Mittel %.4f über die tiefen Zellen (mit dem Muster, wie netz.frag), gegen das Feld %.2f; Albedo über die gedeckte Fläche %.3f; Licht des Schaums Bildanteil · Albedo %.5f gegen Monahan · 0,22 %.5f",
                    al[0], al[0] / (bedeckung / Math.max(1, messungen)), al[1], al[0] * al[1], 3.84e-6 * Math.pow(u10, 3.41) * 0.22));
        A.info("Bedeckung, wo die Wellen nicht tiefenbegrenzt sind (" + name + ")", f("Feld %.4f (Raster %s, %d Zellen mit Hs/h < %.1f und Tiefe über %.0f m, Mittel aus %d Ständen über die letzten 20 s); Statistik von heute (diss / %.1f) %.4f; Monahan bei U10 = %.1f m/s %.4f",
                wf, raster == 0 ? "fein" : "grob", zellenTief[0], HS_H, H_TIEF, messungen, Seegangsoptik.D_KAL, ws, u10, wm));
        double r = wf / wm;
        String pn = "Bedeckung des Felds gegen Monahan und O'Muircheartaigh (1980), " + name;
        if (pruefen) A.pruefe(Double.isFinite(r) && r >= MONAHAN_VON && r <= MONAHAN_BIS, pn, f("%.2f", r), f("%.1f bis %.1f", MONAHAN_VON, MONAHAN_BIS));
        // seit S4b-2: die Albedo über die gedeckte Fläche ist die wirksame Reflexion der Schaumköpfe nach Koepke (1984)
        if (pruefen) A.pruefe(Double.isFinite(al[1]) && al[1] >= KOEPKE_VON && al[1] <= KOEPKE_BIS, "wirksame Reflexion des Schaums über die gedeckte Fläche gegen Koepke (1984), " + name, f("%.3f", al[1]), f("%.2f bis %.2f", KOEPKE_VON, KOEPKE_BIS));
        else A.info(pn, f("%.2f (Soll %.1f bis %.1f; Auskunft)", r, MONAHAN_VON, MONAHAN_BIS));
        if (!fein.isEmpty()) {
            double mf = median(fein), mg = median(grob), mk = median(kopf);
            String kn = f("Kosten der Schritte je Bild auf der Karte, %s (Median aus %d Bildern: fein %.3f, grob %.3f, Kopf %.3f ms)", name, fein.size(), mf, mg, mk);
            if (pruefen) A.hoechstens(kn, mf + mg + mk, KOSTEN_MS, "ms");
            else A.info(kn, f("%.3f ms", mf + mg + mk));
        }
        return wf / wz;
    }

    /** Teil 4: nur Transport, ein Fleck je Raster, 10 s; die Masse am Ende gegen den Anfang. */
    static void masse(Attest A, Geraet g, Schaumfeld feld, SeegangKarte k, double ex, double ey, double t0) {
        feld.schalter = Schaumfeld.TRANSPORT;
        feld.leeren();
        schritt(g, feld, k, t0, ex, ey);
        for (int r = 0; r < 2; r++) {
            int n = Schaumfeld.zellen(r);
            double d = Schaumfeld.abstand(r), rad = r == 0 ? 20 : 100;
            float[] w = new float[n * n];
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    double x = (i + 0.5 - n / 2.0) * d, y = (j + 0.5 - n / 2.0) * d;
                    w[j * n + i] = (float) Math.exp(-(x * x + y * y) / (2 * rad * rad));
                }
            feld.setze(r, w);
        }
        double m0f = feld.masse(0), m0g = feld.masse(1);
        double dt = 1.0 / SCHRITTE_JE_S;
        for (int s = 1; s <= 10 * SCHRITTE_JE_S; s++) schritt(g, feld, k, t0 + s * dt, ex, ey);
        double m1f = feld.masse(0), m1g = feld.masse(1);
        double[] z = feld.zuletzt();
        A.info("Schritt", f("Δt %.4f s, %d Teilschritte, Transport über %.4f s%s; Drift höchstens %.2f m/s", z[1], (int) z[0], z[2], z[3] > 0 ? " (gekappt)" : "", k.driftMax()));
        A.hoechstens(f("Masse fein nach 10 s gegen den Anfang − 1 (%.1f gegen %.1f m²)", m1f, m0f), Math.abs(m1f / m0f - 1), MASSE_TOL, "");
        A.hoechstens(f("Masse grob nach 10 s gegen den Anfang − 1 (%.1f gegen %.1f m²)", m1g, m0g), Math.abs(m1g / m0g - 1), MASSE_TOL, "");
    }

    /** Teil 5: Prüfdrift u = −a (x − x_p) mit x_p am Auge, gleichmäßig f₀; in der Mitte f gegen f₀ e^{a t}. */
    static void konvergenz(Attest A, Geraet g, Schaumfeld feld, SeegangKarte k, double ex, double ey, double t0) {
        feld.schalter = Schaumfeld.TRANSPORT | Schaumfeld.PRUEFDRIFT;
        feld.pruefA = PRUEF_A;
        feld.pruefOst = ex;
        feld.leeren();
        schritt(g, feld, k, t0, ex, ey);
        double f0 = 0.1;
        for (int r = 0; r < 2; r++) {
            int n = Schaumfeld.zellen(r);
            float[] w = new float[n * n];
            Arrays.fill(w, (float) f0);
            feld.setze(r, w);
        }
        double dt = 1.0 / SCHRITTE_JE_S, weg = 0;
        for (int s = 1; s <= 10 * SCHRITTE_JE_S; s++) {
            schritt(g, feld, k, t0 + s * dt, ex, ey);
            weg += feld.zuletzt()[2];
        }
        double soll = f0 * Math.exp(PRUEF_A * weg);
        for (int r = 0; r < 2; r++) {
            int n = Schaumfeld.zellen(r);
            double d = Schaumfeld.abstand(r), breite = r == 0 ? 20 : 100;
            double[] e = feld.ecke(r);
            float[] w = feld.lies(r);
            double s = 0;
            int z = 0;
            for (int j = n / 4; j < 3 * n / 4; j++)
                for (int i = 0; i < n; i++) {
                    double x = e[0] + (i + 0.5) * d - ex;
                    if (Math.abs(x) <= breite) { s += w[j * n + i]; z++; }
                }
            double ist = s / Math.max(1, z);
            A.info("Konvergenz " + (r == 0 ? "fein" : "grob"), f("f in |x − x_p| ≤ %.0f m: %.5f; f₀ e^{a t} = %.5f (a = %.2f/s, Transport über %.3f s)", breite, ist, soll, PRUEF_A, weg));
            A.hoechstens(f("Schaum sammelt sich an der Konvergenz wie ∂f/∂t + ∇·(u f) = 0, %s: f gegen f₀ e^{a t} − 1", r == 0 ? "fein" : "grob"), Math.abs(ist / soll - 1), KONV_TOL, "");
        }
        feld.pruefA = 0;
    }

    /** Teil 6 und 7: 8 s Feld an der Ansicht, Bild mit Feld; mit {@code vergleich} dazu dasselbe Bild mit der Statistik. */
    static void bilder(Attest A, Seegangsflaeche sf, Schaumfeld feld, Blick bl, double t0, int breite, int hoehe, String name, boolean vergleich) throws Exception {
        feld.an = true;
        feld.schalter = Schaumfeld.QUELLE | Schaumfeld.ZERFALL | Schaumfeld.TRANSPORT | Schaumfeld.WINDSEE;   // seit S4a-3 (Befund L2r)
        feld.leeren();
        sf.bild.schaum = feld;
        sf.bild.zeitQuelle = null;
        b.seegang(sf.bild);
        var bel = b.belichtung();
        double evMin0 = bel.evMin, evMax0 = bel.evMax;
        bel.sofort();
        sf.bild.zeit = t0;
        b.standbild(bl, t0);
        double ev = bel.ev();
        bel.evMin = bel.evMax = ev;
        bel.setze(ev);
        float[] p = null;
        int bilder = 8 * SCHRITTE_JE_S;
        double tn = t0;
        for (int s = 1; s <= bilder; s++) {
            tn = t0 + (double) s / SCHRITTE_JE_S;
            sf.bild.zeit = tn;
            p = b.standbild(bl, tn);
        }
        ImageIO.write(SeegangBilder.bild(p, breite, hoehe), "png", Path.of("docs", "status", name + "_Feld.png").toFile());
        A.info("Bild mit Feld", f("docs/status/%s_Feld.png (nach %d Bildern zu 1/%d s, feste Belichtung %+.2f Blenden)", name, bilder, SCHRITTE_JE_S, ev));
        if (vergleich) {
            feld.an = false;
            float[] q = b.standbild(bl, tn).clone();
            ImageIO.write(SeegangBilder.bild(q, breite, hoehe), "png", Path.of("docs", "status", name + "_Statistik.png").toFile());
            A.info("Bild mit der Statistik von heute", f("docs/status/%s_Statistik.png (dieselbe Zeit, Feld aus)", name));
            feld.an = true;
        }
        bel.evMin = evMin0;
        bel.evMax = evMax0;
        bel.sofort();
        sf.bild.schaum = null;
    }

    // ==================================================================== Hilfen

    static void schritt(Geraet g, Schaumfeld feld, SeegangKarte k, double ts, double ex, double ey) {
        Ablauf ab = new Ablauf(g);
        k.zeit(ab, ts);
        k.bild(ab, ex, ey);
        feld.schritt(ab, k, ts, ex, ey);
        g.rechnen.einmal(c -> ab.aufnehmen(c, null));
    }

    /** Wie see_schaum.comp: B̊ₜ(kh) / 1,04 (Hulin u. a. 2025). */
    static double schwelle(double kh) {
        double e = Math.exp(-kh);
        return (0.3546 + 0.0113 * e - 0.3056 * e * e + 0.2298 * e * e * e) / 1.04;
    }

    /** η der Stufe bilinear an der Stelle (x, y) in Stellen. */
    static double eta(float[] v, int n, double x, double y) {
        int i = (int) Math.floor(x), j = (int) Math.floor(y);
        i = Math.max(0, Math.min(n - 2, i));
        j = Math.max(0, Math.min(n - 2, j));
        double a = x - i, c = y - j;
        return (1 - c) * ((1 - a) * v[4 * (j * n + i) + 2] + a * v[4 * (j * n + i + 1) + 2]) + c * ((1 - a) * v[4 * ((j + 1) * n + i) + 2] + a * v[4 * ((j + 1) * n + i + 1) + 2]);
    }

    /** Wert eines Felds je Zelle der Stufe an (x, y), die nächste Zelle; NaN außerhalb. */
    static double wert(Oberflaeche.Stufe s, double[] f, double x, double y) {
        int i = (int) Math.floor((x - s.x0) / s.dx), j = (int) Math.floor((y - s.y0) / s.dx);
        if (i < 0 || j < 0 || i >= s.nx || j >= s.ny) return Double.NaN;
        return f[j * s.nx + i];
    }

    /** dwc des Nahfelds an (x, y), die nächste Zelle des feinen Gitters; 0 ohne. */
    static double senken(Seegangsflaeche sf, Oberflaeche.Stufe s, double x, double y) {
        if (sf.senkenFein == null) return Double.NaN;
        int i = (int) Math.floor((x - s.x0) / s.dx), j = (int) Math.floor((y - s.y0) / s.dx);
        if (i < 0 || j < 0 || i >= s.nx || j >= s.ny) return 0;
        return sf.senkenFein[4 * (j * s.nx + i)];
    }

    /** Tiefe und Hs an (x, y): die nächste Zelle des feinen Gitters, sonst des groben; null außerhalb. */
    static double[] tiefeHs(Oberflaeche o, double x, double y) {
        for (Oberflaeche.Stufe s : new Oberflaeche.Stufe[] {o.fein, o.grob}) {
            if (s == null) continue;
            int i = (int) Math.floor((x - s.x0) / s.dx), j = (int) Math.floor((y - s.y0) / s.dx);
            if (i < 1 || j < 1 || i >= s.nx - 1 || j >= s.ny - 1) continue;
            int c = j * s.nx + i, kw = s.kennWerte();
            return new double[] {s.h[c], 4 * Math.sqrt(Math.max(0, s.kenn[c * kw]))};
        }
        return null;
    }

    static boolean tief(double[] th) { return th != null && th[0] > H_TIEF && th[1] > 0.1 && th[1] / th[0] < HS_H; }

    /** Zahl der Zellen des Rasters r, in denen die Wellen nicht tiefenbegrenzt sind (jede vierte Zeile und Spalte gezählt, × 16). */
    static int tiefeZellen(Seegangsflaeche sf, Schaumfeld feld, int r) {
        int n = Schaumfeld.zellen(r), z = 0;
        double d = Schaumfeld.abstand(r);
        double[] e = feld.ecke(r);
        for (int j = 0; j < n; j += 4) for (int i = 0; i < n; i += 4) if (tief(tiefeHs(sf.oberflaeche, e[0] + (i + 0.5) * d, e[1] + (j + 0.5) * d))) z++;
        return 16 * z;
    }

    /** Bedeckung min(1, f) im Mittel über die Zellen des Rasters r, in denen die Wellen nicht tiefenbegrenzt sind. */
    static double[] bedeckung(Seegangsflaeche sf, Schaumfeld feld, int r, int[] zellen) {
        int n = Schaumfeld.zellen(r), schritt = r == 0 ? 2 : 1;
        double d = Schaumfeld.abstand(r);
        double[] e = feld.ecke(r);
        float[] w = feld.lies(r);
        double s = 0;
        int z = 0;
        for (int j = 0; j < n; j += schritt)
            for (int i = 0; i < n; i += schritt) {
                if (!tief(tiefeHs(sf.oberflaeche, e[0] + (i + 0.5) * d, e[1] + (j + 0.5) * d))) continue;
                s += Math.min(1, w[j * n + i]);
                z++;
            }
        zellen[0] = z * schritt * schritt;
        return new double[] {z > 0 ? s / z : Double.NaN};
    }

    /**
     * Seit S4b, wie netz.frag im Mittel (ohne das Muster im Einzelnen): {Bildanteil Schaum, Albedo über die gedeckte Fläche}.
     * Seit S4b-2 über dieselben Zellen wie {@link #bedeckung} (L2s mittelte über das ganze Raster, das Feld nur über die
     * tiefen Zellen) und mit der Albedo 0,1 + 0,45 φ².
     */
    static double[] albedo(Seegangsflaeche sf, Schaumfeld feld, int r) {
        int n = Schaumfeld.zellen(r), schritt = r == 0 ? 2 : 1;
        double d = Schaumfeld.abstand(r);
        double[] ec = feld.ecke(r);
        float[] w = feld.lies(r);
        double cs = 0, ca = 0;
        int z = 0;
        for (int j = 0; j < n; j += schritt)
            for (int i = 0; i < n; i += schritt) {
                if (!tief(tiefeHs(sf.oberflaeche, ec[0] + (i + 0.5) * d, ec[1] + (j + 0.5) * d))) continue;
                double f = w[j * n + i];
                double phi = Math.min(1, Math.max(0, f)), e = 0.08 + (1.6 - 0.08) * phi * phi;
                double t = Math.min(1, Math.max(0, (f - 0.01) / 0.05)), pres = t * t * (3 - 2 * t);
                double cov = pres * Math.min(1, 1.5 * e), alb = ALB_ALT + (ALB_FRISCH - ALB_ALT) * phi * phi;
                cs += cov;
                ca += cov * alb;
                z++;
            }
        return new double[] {z > 0 ? cs / z : Double.NaN, cs > 0 ? ca / cs : Double.NaN};
    }

    /** Albedo des Schaums wie netz.frag (seit S4b-2); wirksame Reflexion nach Koepke (1984) 0,22, nach oben bis 0,33 (seine Unsicherheit). */
    static final double ALB_FRISCH = 0.55, ALB_ALT = 0.1, KOEPKE_VON = 0.15, KOEPKE_BIS = 0.33;

    /** Die Statistik von heute über dieselben Zellen: min(1, diss / D_KAL) aus dem Seegangsnetz (jede achte Zeile und Spalte). */
    static double statistik(Seegangsflaeche sf, List<Seegangsnetz> netze, Schaumfeld feld, int r, double t0) {
        if (r < 0) return Double.NaN;
        int n = Schaumfeld.zellen(r);
        double d = Schaumfeld.abstand(r);
        double[] e = feld.ecke(r);
        double s = 0;
        int z = 0;
        for (int j = 0; j < n; j += 8)
            for (int i = 0; i < n; i += 8) {
                double x = e[0] + (i + 0.5) * d, y = e[1] + (j + 0.5) * d;
                if (!tief(tiefeHs(sf.oberflaeche, x, y))) continue;
                double[] w = Seegangsnetz.wertGemischt(netze, x, y, t0);
                if (w == null) continue;
                s += Math.min(1, Math.max(0, w[5]) / Seegangsoptik.D_KAL);
                z++;
            }
        return z > 0 ? s / z : Double.NaN;
    }

    static Szenario szenario(Szenen sz, String schluessel) {
        for (Szenario x : sz.szenarien()) if (x.schluessel.equals(schluessel)) return x;
        throw new IllegalArgumentException("Szenario " + schluessel + " fehlt in der Szenendatei");
    }

    static Blick blick(Ansicht an, String zeit, int breite, int hoehe) {
        t = (Instant.parse(zeit).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        Blick bl = new Blick();
        an.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
        bl.groesse(breite, hoehe);
        double[] wa = b.wasserUndSeegang(bl.ost, bl.nord);
        Ansicht.hebe(bl, wa[0], wa[1], an.ueberWasser);
        return bl;
    }

    static double median(List<Double> l) {
        if (l.isEmpty()) return Double.NaN;
        double[] a = l.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(a);
        return a[a.length / 2];
    }
}
