package com.dan.lauf.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Phasenfeld;
import com.dan.core.szene.Profilpuffer;
import com.dan.core.szene.Reflexion;
import com.dan.core.szene.Wasserpunkte;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Flotte;
import com.dan.lauf.Schwimmerangabe;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.auftrieb.Bandwasser;
import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Vorgaben;
import com.dan.render.seegang.Koerpersuche;
import com.dan.render.seegang.SeegangKarte;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S7b (Seegang Werkbuch 88): das Wasser von der Karte für die Schwimmer, die Flotte im eigenen Faden und ihr Bild.
 * <ol>
 * <li><b>Karte gegen Referenz</b> (Prüfstein 15): in der Szene von {@code SeegangKarteCheck} (fein, grob, Fernfeld, Rampe
 * 20 → 6 m, Hs 1,4 m aus Südsüdwest) an 1000 Punkten {@code see_koerper.comp} gegen {@link Oberflaeche#baender} in double:
 * η über dem festen Ort, die Summe der Bänder gegen Re Z, und aus den Bändern ({@link Bandwasser}) ψ in 0, −1, −3 m,
 * u und w.</li>
 * <li><b>Bahn</b> (Prüfstein 16): der Krabbenkutter quer zur See über 60 s, einmal mit dem Wasser der Karte wie in der
 * Anwendung (das Ergebnis drei Bilder später; 30 Bilder je s, also 0,1 s Verzug, doppelt so viel wie in der Anwendung bei
 * 60 Bildern je s), einmal mit dem der Referenz in jedem Bild ohne Verzug. Seit S7d8 (Seegang Werkbuch 97, SF143 A) gelten
 * die Grenzen in den ersten 20 s wie bei der Fähre; die 60 s bleiben Auskunft mit der Drift.</li>
 * <li><b>Faden</b> (Prüfstein 18): die Flotte von Hooge (Fähre, Kutter, 27 Tonnen) in derselben Szene, 10 s, Zeit des
 * Fadens je Teilschritt und des Fadens der Bilder.</li>
 * <li><b>In der Anwendung</b> (Prüfsteine 17–19): die Bühne ohne Fenster mit {@code Hooge.szenen}, Bild für Bild wie die
 * Bildschleife mit Zeitraffer 1; Lage im Bild gleich Lage der Physik; Kosten auf der Karte; Standbilder für die
 * Sichtprüfung (ruhiger Tag mit Seegang, Sturmflut mit Seegang, Xaver).</li>
 * </ol>
 * Seit S7b-4 (Seegang Werkbuch 89, Befunde L5c): Teil 1 auch mit Startwerten aus dem letzten Bild (wie die Anwendung);
 * Teil 2 auch für die Fähre mit Punkten der Karte im Raster von {@link Flotte#KARTE_SCHIFF} m (20 s); Teil 3 dazu die
 * Spitztonne im Strom über die Flotte (A25); Teil 4 mit den Ansichten {@code faehre} und {@code tonne} im Seegang, dem
 * Faden der Flotte als Prüfstein und dem Strom an Ho 7. Attest {@code docs/status/S7b4_Schwimmer.md}.
 * <p>
 * Seit S7d2 (Seegang Werkbuch 90, Befunde L5d): Teil 1 mit 1, 16 und 64 Fäden je Punkt (dasselbe Ergebnis, Zeit auf der
 * Karte); Teil 4 mit dem Pegel des Szenarios (Fahrplan der Fähre nach der Tide), ruhigsee um 13:30 UTC (die Fähre liegt) und
 * dem Prüfstein „kein Schiff gekentert“. Seit S7d3 (Datei 91): Vorgabe 16 Fäden, ein Schritt mit Startwert; in Teil 4 je
 * Ansicht und Schiff die Gewichte der Gitter der Fläche an seinen Punkten (Befund L5d2 2).
 * <p>
 * Seit S7d4 (Datei 92, SF121 A, Befund L5d3 1): die Körperfläche. Teil 1 prüft sie zweimal: dieselbe Fläche als Körperfläche
 * ändert an Punkten ganz im feinen oder ganz im groben Gitter nichts; eine Körperfläche mit einem Viertel der Energie gibt
 * an Punkten ganz im groben Gitter ein Viertel von m0 und lässt das feine Gitter, wie es ist. Teil 4 wartet, bis sie steht,
 * und prüft, dass das Wasser an Kutter und Fähre in jeder Ansicht aus einem feinen Gitter kommt (des Auges oder der
 * Körperfläche).
 * <pre>  SchwimmerCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--teil n] [--breite 1280 --hoehe 720]</pre>
 */
public final class SchwimmerCheck {
    static final Attest A = new Attest("Schwimmer: Wasser von der Karte, Flotte, Bild (S7b, S7b-4)", Koerper.VERSION);
    static final int NB = 8, ND = 32, STUFEN = 8, N = 128;
    static final double FMIN = 0.06, FMAX = 0.6, D0 = 0.25, HS = 1.4, TP = 6.5, WOHIN = 20;
    static final double[] OM = new double[NB], UNTEN = new double[NB], OBEN = new double[NB];
    static final double BX = 466000, BY = 6046000, T0 = 1000;

    static {
        double st = Math.pow(FMAX / FMIN, 1.0 / NB);
        for (int b = 0; b < NB; b++) {
            double lo = FMIN * Math.pow(st, b), hi = lo * st;
            OM[b] = 2 * Math.PI * Math.sqrt(lo * hi); UNTEN[b] = 2 * Math.PI * lo; OBEN[b] = 2 * Math.PI * hi;
        }
    }

    static Oberflaeche ob;
    static SeegangKarte karte;
    static Koerpersuche suche;

    public static void main(String[] args) {
        File szenen = null;
        int breite = 1280, hoehe = 720;
        java.util.Set<String> nur = new java.util.HashSet<>();
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                case "--teil" -> nur.add(args[++i]);
                case "--breite" -> breite = Integer.parseInt(args[++i]);
                case "--hoehe" -> hoehe = Integer.parseInt(args[++i]);
                default -> { }
            }
        System.out.println("SchwimmerCheck – S7b, S7b-4, S7d2, S7d4 (Seegang Werkbuch 89, 90, 92)");
        long t0 = System.nanoTime();
        try {
            if (nur.isEmpty() || nur.contains("1") || nur.contains("2") || nur.contains("3")) {
                Profilpuffer p = new Profilpuffer(OM, UNTEN, OBEN, ND, 0x5EE6A3L, false);
                ob = szene(p);
                karte = SeegangKarte.mitGeraet(ob, STUFEN, N, D0);
                A.info("Gerät", karte.geraet().name + " (" + karte.geraet().treiber + ")");
                karte.eingaben();
                karte.bezug(BX, BY);
                suche = new Koerpersuche(karte.geraet(), karte.geraet().konto("Körpersuche", 256L << 20), NB);
                try {
                    if (nur.isEmpty() || nur.contains("1")) zeit("1", SchwimmerCheck::karteGegenReferenz);
                    if (nur.isEmpty() || nur.contains("2")) zeit("2", SchwimmerCheck::bahn);
                    if (nur.isEmpty() || nur.contains("3")) zeit("3", SchwimmerCheck::faden);
                } finally {
                    suche.close();
                    karte.close();
                }
            }
            if (nur.isEmpty() || nur.contains("4")) {
                if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
                File sz = szenen;
                int br = breite, ho = hoehe;
                zeit("4", () -> anwendung(sz, br, ho));
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        // seit S7d8: mit --teil ein eigenes Attest je Auswahl, das volle Attest bleibt stehen
        String datei = nur.isEmpty() ? "S7b4_Schwimmer.md" : "S7b4_Schwimmer_teil" + String.join("", new java.util.TreeSet<>(nur)) + ".md";
        Path pa = A.schreibe(Path.of("docs", "status", datei), "S7b-4 – Wasser von der Karte, Flotte und Bild der Schwimmer (Auftrieb " + Koerper.VERSION + ", Körpersuche " + Koerpersuche.VERSION + ")"
                + (nur.isEmpty() ? "" : ", nur Teil " + String.join(", ", new java.util.TreeSet<>(nur))));
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void zeit(String teil, Runnable r) {
        long t = System.nanoTime();
        r.run();
        System.out.printf("  [Teil %s: %.1f s]%n", teil, (System.nanoTime() - t) * 1e-9);
    }

    // ------------------------------------------------------------------ Szene (wie SeegangKarteCheck)

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

    static double tiefe(double x, double y) { return Math.max(0.0, 20 - 0.0045 * (x - 463500) + 1.5 * Math.sin((y - 6043500) / 400)); }

    static Oberflaeche szene(Profilpuffer p) {
        double[] e = jonswap(HS, TP, WOHIN, 10);
        double ux = 466000, uy = 6046000;
        Oberflaeche.Stufe g = stufe("grob", 100, 100, 50, 463500, 6043500, e);
        Oberflaeche.Stufe fz = stufe("fein", 100, 100, 10, 465500, 6045500, e);
        Reflexion reflexion = Reflexion.partner(g, fz);
        double[] ri = Phasenfeld.richtungen(NB, ND);
        reflexion.richtungen(ri);
        Phasenfeld pg = Phasenfeld.rechne(g.nx, g.ny, g.dx, g.x0, g.y0, g.h, null, null, OM, ri, ND, ux, uy, null, 0, g.e);
        reflexion.wand(g, pg, false);
        g.phasen(pg, pg);
        Phasenfeld pf = Phasenfeld.rechne(fz.nx, fz.ny, fz.dx, fz.x0, fz.y0, fz.h, null, null, OM, ri, ND, ux, uy, pg, 0, fz.e);
        reflexion.wand(fz, pf, true);
        reflexion.teilen(p);
        fz.phasen(pf, pf);
        Oberflaeche o = new Oberflaeche(p, ND);
        o.grob = g; o.fein = fz;
        o.fernfeld = (x, y) -> new double[] {HS, TP, Math.toRadians(WOHIN)};
        o.vorbereiten();
        return o;
    }

    static Oberflaeche.Stufe stufe(String name, int nx, int ny, double dx, double x0, double y0, double[] e) {
        Oberflaeche.Stufe s = new Oberflaeche.Stufe(name, nx, ny, dx, x0, y0, NB, ND);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                double x = x0 + (i + 0.5) * dx, y = y0 + (j + 0.5) * dx;
                s.h[c] = tiefe(x, y);
                double w = s.h[c] > 0.05 ? Math.min(1, s.h[c] / 8) : 0;
                for (int k = 0; k < NB * ND; k++) s.e[c * NB * ND + k] = e[k] * w;
            }
        s.gefaelleAusTiefe();
        return s;
    }

    // ------------------------------------------------------------------ Hilfen

    /** Eine Anfrage mit einem Körper (Nummer 0) an den Punkten (x, y). */
    static Wasserpunkte.Anfrage anfrage(double[] x, double[] y, int n) {
        return new Wasserpunkte.Anfrage(1, x, y, n, new int[] {0}, new int[] {n});
    }

    /** Der Stand der Referenz (double, als float abgelegt wie die Karte) an den Punkten der Anfrage zur Zeit t. */
    static Wasserpunkte.Stand referenz(Wasserpunkte.Anfrage q, double t) {
        int je = Wasserpunkte.KOPF + Wasserpunkte.JE_BAND * ob.nb;
        float[] w = new float[je * q.zahl];
        double[] aus = new double[je];
        for (int i = 0; i < q.zahl; i++) {
            ob.baender(q.ost[i], q.nord[i], t, aus);
            for (int j = 0; j < je; j++) w[je * i + j] = (float) aus[j];
        }
        return new Wasserpunkte.Stand(q, t, ob.omega.clone(), w);
    }

    /** Der Stand der Karte sofort (ohne Verzug) zur Zeit t. */
    static Wasserpunkte.Stand karteSofort(Wasserpunkte.Anfrage q, double t) {
        karte.zeit(t);
        Wasserpunkte wp = new Wasserpunkte();
        suche.sofort(karte, t, q, wp);
        return wp.stand();
    }

    // ------------------------------------------------------------------ 1

    static void karteGegenReferenz() {
        A.teil("1 Karte gegen Referenz an 1000 Punkten (Prüfstein 15)");
        Random r = new Random(7);
        int n = 1000;
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            if (i < 600) { x[i] = 465520 + 960 * r.nextDouble(); y[i] = 6045520 + 960 * r.nextDouble(); }          // fein
            else if (i < 900) { x[i] = 463600 + 3000 * r.nextDouble(); y[i] = 6043600 + 4800 * r.nextDouble(); }   // grob (und fein)
            else { x[i] = 468600 + 900 * r.nextDouble(); y[i] = 6044000 + 4000 * r.nextDouble(); }                // Fernfeld
        }
        double t = T0 + 37.3;
        Wasserpunkte.Anfrage q = anfrage(x, y, n);
        long n0 = System.nanoTime();
        Wasserpunkte.Stand sk = karteSofort(q, t);
        double msKarte = (System.nanoTime() - n0) / 1e6;
        Wasserpunkte.Stand sr = referenz(q, t);
        int je = sk.je;
        double dEta = 0, dLin = 0, rmsEta = 0, iterMax = 0, schrittMax = 0;
        for (int i = 0; i < n; i++) {
            int o = je * i;
            dEta = Math.max(dEta, Math.abs(sk.werte[o] - sr.werte[o]));
            rmsEta += sr.werte[o] * sr.werte[o];
            double s = 0;
            for (int b = 0; b < sk.nb; b++) s += sk.werte[o + Wasserpunkte.KOPF + Wasserpunkte.JE_BAND * b + 2];
            dLin = Math.max(dLin, Math.abs(s - sk.werte[o + 1]));
            iterMax = Math.max(iterMax, sk.werte[o + 3]);
            schrittMax = Math.max(schrittMax, sk.werte[o + 2]);
        }
        double iterMittel = 0;
        for (int i = 0; i < n; i++) iterMittel += sk.werte[je * i + 3] / n;
        A.info("Fläche an den Punkten", f("RMS η %.3f m (Hs %.1f m); Suche über dem festen Ort ohne Startwert höchstens %.0f Schritte (im Mittel %.2f), letzter Schritt höchstens %.1f mm",
                Math.sqrt(rmsEta / n), HS, iterMax, iterMittel, 1000 * schrittMax));
        A.pruefe(dEta <= 0.01, "η über dem festen Ort, Karte gegen Referenz", f("größte Abweichung %.2f mm", 1000 * dEta), "± 1 cm");
        // seit S7b-4: mit Startwerten aus dem letzten Bild, zwölf Bilder zu 1/30 s, wie in der Anwendung
        {
            Wasserpunkte wpw = new Wasserpunkte();
            double tw = t;
            for (int k = 0; k < 12; k++) {
                tw = t + k / 30.0;
                karte.zeit(tw);
                suche.sofort(karte, tw, q, wpw, null, true);
            }
            Wasserpunkte.Stand sw = wpw.stand(), srw = referenz(q, tw);
            double dw = 0, im = 0, imx = 0;
            for (int i = 0; i < n; i++) {
                dw = Math.max(dw, Math.abs(sw.werte[je * i] - srw.werte[je * i]));
                im += sw.werte[je * i + 3] / n;
                imx = Math.max(imx, sw.werte[je * i + 3]);
            }
            A.pruefe(dw <= 0.01 && suche.zuletztWarm, "η mit Startwert (12. Bild), Karte gegen Referenz", f("größte Abweichung %.2f mm; Schritte im Mittel %.2f, höchstens %.0f (ohne Startwert %.2f)", 1000 * dw, im, imx, iterMittel), "± 1 cm");
        }
        A.pruefe(dLin <= 0.001, "Summe der Bänder gegen Re Z (Karte, in sich)", f("größte Abweichung %.3f mm", 1000 * dLin), "± 1 mm");
        Bandwasser bk = new Bandwasser(0), br = new Bandwasser(0);
        bk.stand(sk);
        br.stand(sr);
        double[] ok = new double[6], orr = new double[6];
        double[] zs = {0, -1, -3};
        for (double z : zs) {
            double dp = 0, du = 0, dw = 0, dg = 0, rp = 0;
            for (int i = 0; i < n; i++) {
                double zz = Math.max(z, -tiefe(x[i], y[i]) + 0.05);
                bk.welle(i, x[i], y[i], zz, t, ok);
                br.welle(i, x[i], y[i], zz, t, orr);
                dp = Math.max(dp, Math.abs(ok[0] - orr[0]));
                dg = Math.max(dg, Math.max(Math.abs(ok[1] - orr[1]), Math.abs(ok[2] - orr[2])));
                du = Math.max(du, Math.max(Math.abs(ok[3] - orr[3]), Math.abs(ok[4] - orr[4])));
                dw = Math.max(dw, Math.abs(ok[5] - orr[5]));
                rp += orr[0] * orr[0];
            }
            A.pruefe(dp <= 0.01, f("ψ in %.0f m (aus den Bändern), Karte gegen Referenz", z), f("größte Abweichung %.2f mm (RMS ψ %.3f m)", 1000 * dp, Math.sqrt(rp / n)), "± 1 cm");
            A.pruefe(du <= 0.01 && dw <= 0.01, f("u, v und w in %.0f m, Karte gegen Referenz", z), f("größte Abweichung %.2f und %.2f mm/s", 1000 * du, 1000 * dw), "± 1 cm/s");
            A.info(f("  Gefälle von ψ in %.0f m", z), f("größte Abweichung %.2e", dg));
        }
        // ψ(0) aus den Bändern gegen η: die Form der Fläche liegt im letzten Glied
        double d0 = 0;
        for (int i = 0; i < n; i++) {
            br.welle(i, x[i], y[i], 0, t, orr);
            d0 = Math.max(d0, Math.abs(orr[0] - sr.werte[je * i]));
        }
        A.info("ψ(0) aus den Bändern gegen η (Referenz)", f("größte Abweichung %.2f mm", 1000 * d0));
        // Kosten: 2000 Punkte sofort
        double[] x2 = new double[2000], y2 = new double[2000];
        for (int i = 0; i < 2000; i++) { x2[i] = x[i % n] + 0.3; y2[i] = y[i % n]; }
        Wasserpunkte.Anfrage q2 = anfrage(x2, y2, 2000);
        karteSofort(q2, t);
        long n1 = System.nanoTime();
        for (int k = 0; k < 5; k++) karteSofort(q2, t);
        A.info("Kosten auf der Karte, sofort abgeschickt und gewartet", f("1000 Punkte %.1f ms (erstes Mal), 2000 Punkte %.1f ms (mit Senden und Warten)", msKarte, (System.nanoTime() - n1) / 5e6));
        faeden(q, t, sk);
        koerperflaeche(q, t, sk);
    }

    /**
     * Seit S7d4 (SF121 A): die Körperfläche auf der Karte. (a) Dieselbe Fläche als Körperfläche: an Punkten ganz im feinen
     * oder ganz im groben Gitter dasselbe η wie ohne [G: ≤ 0,1 mm]. Seit S7d4b (Befund L5d4 1) nach dem Ort: im feinen Gitter
     * weiter als sein Rand (6 Zellen) vom Rand, im groben ebenso und außerhalb des feinen; in L5d4 zählten Punkte am Rand des
     * groben mit, an denen das Fernfeld bis 0,8 % hat – dort rechnet die Körperfläche es als ebene Wellen (so gewollt). (b) Eine Körperfläche mit einem Viertel der Energie (sonst gleich): an Punkten ganz im groben
     * Gitter m0 ein Viertel [G: ± 3 %], ganz im feinen unverändert [G: ± 1 %], im Fernfeld unverändert.
     */
    static void koerperflaeche(Wasserpunkte.Anfrage q, double t, Wasserpunkte.Stand ohne) {
        int je = ohne.je;
        Geraet g = karte.geraet();
        double dEta = 0;
        int nF = 0, nG = 0, nFern = 0;
        double rG = 0, rF = 0, rFern = 0, rGmin = 1e9, rGmax = -1e9, rFmin = 1e9, rFmax = -1e9, dFern = 0;
        boolean anteil = true;
        try {
            suche.koerperflaeche(karte, 1.0);
            Wasserpunkte.Stand mit = karteSofort(q, t);
            Oberflaeche.Stufe sf = ob.fein, sg = ob.grob;
            double rf = (sf.rand + 0.5) * sf.dx + 1, rg = (sg.rand + 0.5) * sg.dx + 1;
            int nf = 0, ng = 0;
            for (int i = 0; i < q.zahl; i++) {
                int o = je * i;
                double x = q.ost[i], y = q.nord[i];
                boolean imF = x > sf.x0 - 1 && x < sf.x0 + sf.nx * sf.dx + 1 && y > sf.y0 - 1 && y < sf.y0 + sf.ny * sf.dx + 1;
                boolean fein = x > sf.x0 + rf && x < sf.x0 + sf.nx * sf.dx - rf && y > sf.y0 + rf && y < sf.y0 + sf.ny * sf.dx - rf;
                boolean grob = !imF && x > sg.x0 + rg && x < sg.x0 + sg.nx * sg.dx - rg && y > sg.y0 + rg && y < sg.y0 + sg.ny * sg.dx - rg;
                double[] gk = Koerpersuche.gitter(mit.werte[o + 7]);
                if (fein || grob) dEta = Math.max(dEta, Math.abs(mit.werte[o] - ohne.werte[o]));
                if (fein) nf++;
                if (grob) { ng++; if (gk[4] < 1) anteil = false; }        // dort kommt alles aus ihrem groben Gitter
            }
            A.pruefe(dEta <= 1e-4 && anteil && nf > 0 && ng > 0, "Körperfläche gleich der Fläche (seit S7d4, S7d4b): η an Punkten ganz im feinen oder ganz im groben Gitter wie ohne",
                    f("größte Abweichung %.3f mm an %d Punkten im feinen und %d im groben; Anteil der Körperfläche im groben Gitter %s", 1000 * dEta, nf, ng, anteil ? "1,00" : "UNTER 1"), "≤ 0,1 mm [G]");
            // (b) ein Viertel der Energie
            Profilpuffer p2 = new Profilpuffer(OM, UNTEN, OBEN, ND, 0x5EE6A3L, false);
            Oberflaeche o2 = szene(p2);
            for (Oberflaeche.Stufe st : new Oberflaeche.Stufe[] {o2.grob, o2.fein}) for (int k = 0; k < st.e.length; k++) st.e[k] *= 0.25;
            o2.vorbereiten();
            try (SeegangKarte k2 = new SeegangKarte(g, g.konto("Körperfläche (Prüfung)", 256L << 20), o2, 1, 8, D0, false)) {
                k2.eingaben();
                k2.bezug(BX, BY);
                suche.koerperflaeche(k2, 1.0);
                Wasserpunkte.Stand v = karteSofort(q, t);
                for (int i = 0; i < q.zahl; i++) {
                    int o = je * i;
                    double[] gg = Koerpersuche.gitter(ohne.werte[o + 7]);
                    double m0 = ohne.werte[o + 6], m1 = v.werte[o + 6];
                    if (!(m0 > 1e-6)) continue;
                    double r = m1 / m0;
                    if (gg[0] >= 1) { nF++; rF += r; rFmin = Math.min(rFmin, r); rFmax = Math.max(rFmax, r); }
                    else if (gg[0] == 0 && gg[1] >= 1) { nG++; rG += r; rGmin = Math.min(rGmin, r); rGmax = Math.max(rGmax, r); }
                    else if (gg[0] == 0 && gg[1] == 0) { nFern++; rFern += r; dFern = Math.max(dFern, Math.abs(r - 1)); }
                }
                g.ruhe();
            }
        } finally {
            suche.koerperflaeche(null, 0);
        }
        boolean okG = nG > 0 && Math.abs(rGmin / 0.25 - 1) <= 0.03 && Math.abs(rGmax / 0.25 - 1) <= 0.03;
        boolean okF = nF > 0 && Math.abs(rFmin - 1) <= 0.01 && Math.abs(rFmax - 1) <= 0.01;
        A.pruefe(okG && okF && dFern <= 1e-4, "Körperfläche mit einem Viertel der Energie (seit S7d4): m0 im groben Gitter ein Viertel, im feinen und im Fernfeld wie ohne",
                f("grob (%d Punkte): %.3f … %.3f; fein (%d): %.4f … %.4f; Fernfeld (%d): größte Abweichung %.1e", nG, rGmin, rGmax, nF, rFmin, rFmax, nFern, dFern),
                "grob 0,25 ± 3 %, fein 1 ± 1 % [G]");
    }

    /**
     * Seit S7d2 (Befund L5d 1, R16): dieselben Punkte mit 1, 16 und 64 Fäden je Punkt; dasselbe Ergebnis (Reihenfolge der Summen
     * anders), und die Zeit von see_koerper auf der Karte (Zeitstempel) für 234 Punkte wie in der Anwendung und für 1000.
     */
    static void faeden(Wasserpunkte.Anfrage q, double t, Wasserpunkte.Stand vorgabe) {
        int je = vorgabe.je;
        double dEta = 0, dBand = 0, bandMax = 0;
        int vorher = suche.faeden;
        StringBuilder zeiten = new StringBuilder();
        double[] ms234 = new double[Koerpersuche.FAEDEN.length];
        Geraet g = karte.geraet();
        Wasserpunkte.Anfrage q234 = anfrage(Arrays.copyOf(q.ost, 234), Arrays.copyOf(q.nord, 234), 234);
        try (Stoppuhr su = new Stoppuhr(g, g.rechnen, 16)) {
            for (int v = 0; v < Koerpersuche.FAEDEN.length; v++) {
                int fz = Koerpersuche.FAEDEN[v];
                suche.faeden = fz;
                Wasserpunkte.Stand st = karteSofort(q, t);
                for (int i = 0; i < q.zahl; i++) {
                    int o = je * i;
                    dEta = Math.max(dEta, Math.abs(st.werte[o] - vorgabe.werte[o]));
                    for (int j = Wasserpunkte.KOPF; j < je; j++) {
                        dBand = Math.max(dBand, Math.abs(st.werte[o + j] - vorgabe.werte[o + j]));
                        bandMax = Math.max(bandMax, Math.abs(vorgabe.werte[o + j]));
                    }
                }
                double[] mz = new double[2];
                for (int w = 0; w < 2; w++) {
                    Wasserpunkte.Anfrage qq = w == 0 ? q234 : q;
                    List<Double> l = new ArrayList<>();
                    for (int k = 0; k < 12; k++) {
                        karte.zeit(t);
                        Ablauf ab = new Ablauf(g);
                        suche.suche(ab, karte, t, qq, null, false);
                        g.rechnen.einmal(c -> { su.runde(c); ab.aufnehmen(c, su); });
                        su.sammle(true);
                        if (k >= 2) l.add(su.letzte("see_koerper"));
                        suche.leeren();
                    }
                    java.util.Collections.sort(l);
                    mz[w] = l.isEmpty() ? Double.NaN : l.get(l.size() / 2);
                }
                ms234[v] = mz[0];
                zeiten.append(f("%s%d Fäden: 234 Punkte %.3f ms, %d Punkte %.3f ms", v > 0 ? "; " : "", fz, mz[0], q.zahl, mz[1]));
            }
            if (!su.verfuegbar) zeiten.append(" (die Warteschlange hat keine Zeitstempel)");
        } finally {
            suche.faeden = vorher;
        }
        A.pruefe(dEta <= 5e-5 && dBand <= 1e-4 * Math.max(1, bandMax), "Fäden je Punkt 1, 16 und 64 gegen die Vorgabe " + Koerpersuche.FAEDEN_VORGABE + " (seit S7d2): dasselbe Ergebnis",
                f("η größte Abweichung %.4f mm; Summen der Bänder %.2e (größte Summe %.3f)", 1000 * dEta, dBand, bandMax), "η ≤ 0,05 mm; Bänder ≤ 1e-4 der größten [G]");
        A.info("Zeit von see_koerper auf der Karte (Median von 10, Zeitstempel)", zeiten.toString());
        faedenMs234 = ms234;
    }

    /** Seit S7d2: Zeit von see_koerper für 234 Punkte je Zahl der Fäden (Teil 1, für das Attest). */
    static double[] faedenMs234;

    /** Seit S7d2: der Pegel des Szenarios aus der Szenendatei an die Bühne (Hochwasser für den Fahrplan nach der Tide). */
    static void pegel(Buehne b, Szenen sz) {
        try {
            double[] po = sz.zahlen("pegel.ort");
            if (po != null) b.pegel(sz.text("pegel.name", "Pegel"), po[0], po[1], sz.zahl("pegel.umkreis", 300));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ 2

    /** Seit S7d8 (SF143 A): Prüfstein 16 gilt für so viele s (Kutter und Fähre) [G]. */
    static final double PRUEF_S = 20;

    static void bahn() {
        A.teil("2 Bahn, Karte gegen Referenz (Prüfstein 16, seit S7d8 in den ersten 20 s): Kutter 60 s, Fähre mit gröberer Karte 20 s");
        bahn("krabbenkutter", "Kutter", 60, false);
        bahn("faehre", "Fähre", 20, true);
    }

    /**
     * Ein Körper quer zur See: mit dem Wasser der Karte wie in der Anwendung (Punkte der Karte nach {@link Flotte#raster},
     * Ergebnis drei Bilder später), gegen die Referenz an allen Säulen in jedem Bild ohne Verzug.
     */
    static void bahn(String art, String name, double dauer, boolean stampfen) {
        double dtBild = 1.0 / 30;                                               // 30 Bilder je s: drei Bilder Verzug sind 0,1 s
        Koerper ka = Flotte.vorgabe(art), kr = Flotte.vorgabe(art);
        double[] rast = Flotte.raster(art);
        ka.kartenRaster(rast[0], rast[1]);                                          // seit S7d6 längs und quer
        double x0 = 466000, y0 = 6046000, kurs = 290;
        Bandwasser wa = new Bandwasser(0), wr = new Bandwasser(0);
        wa.zuordnung(ka.kartenpunkt);
        for (Bandwasser w : new Bandwasser[] {wa, wr}) { w.setze(0, 0, 0, 0, 0); w.grundSonst(-tiefe(x0, y0)); }
        ka.einschwimmen(wa, x0, y0, kurs, 0, 0, T0);
        kr.einschwimmen(wr, x0, y0, kurs, 0, 0, T0);
        int ms = kr.proben.saeulen, mk = ka.kartenpunkte;
        A.info(name, f("%d Proben, %d Säulen zu höchstens %.1f m, %d Punkte der Karte (Raster %s); Kurs %.0f°, die See läuft nach %.0f°",
                ka.proben.n, ms, com.dan.physics.auftrieb.Proben.SAEULE, mk, rast[0] <= com.dan.physics.auftrieb.Proben.SAEULE ? "je Säule"
                        : f("%.1f × %.1f m", rast[0], rast[1]), kurs, WOHIN));
        Wasserpunkte wp = new Wasserpunkte();
        Geraet g = karte.geraet();
        double t = T0;
        suche.leeren();
        double dz = 0, dr = 0, dp = 0, sz = 0, sr = 0, zs = 0, rs = 0, dHor = 0, dz20 = 0, dr20 = 0, dp20 = 0, dHor20 = 0;
        int bilder = (int) Math.round(dauer / dtBild);
        double msRef = 0, msKarte = 0;
        for (int i = -Koerpersuche.RING; i < bilder; i++) {
            double tb = t + dtBild;
            double[] ox = new double[mk], oy = new double[mk];
            ka.kartenWelt(0, ox, oy, 0);
            karte.zeit(tb);
            Ablauf a = new Ablauf(g);
            long n0 = System.nanoTime();
            suche.lies(wp);
            suche.suche(a, karte, tb, anfrage(ox, oy, mk));
            g.rechnen.einmal(c -> a.aufnehmen(c, null));
            msKarte += (System.nanoTime() - n0) / 1e6;
            if (i < 0) continue;                                             // der Ring füllt sich, die Körper stehen
            double[] rx = new double[ms], ry = new double[ms];
            kr.saeulenWelt(0, rx, ry, 0);
            long n1 = System.nanoTime();
            Wasserpunkte.Stand st = referenz(anfrage(rx, ry, ms), t);
            msRef += (System.nanoTime() - n1) / 1e6;
            wr.stand(st);
            if (wa.stand() != wp.stand()) wa.stand(wp.stand());
            ka.schritt(wa, dtBild);
            kr.schritt(wr, dtBild);
            t += dtBild;
            double ha = ka.lage()[2], hr = kr.lage()[2], ra = ka.kraengung(), rr = kr.kraengung();
            dz = Math.max(dz, Math.abs(ha - hr));
            dr = Math.max(dr, Math.abs(ra - rr));
            dp = Math.max(dp, Math.abs(ka.trimm() - kr.trimm()));
            sz += hr; zs += hr * hr; sr += rr; rs += rr * rr;
            // seit S7d7: wie weit die beiden Körper auseinander treiben; Abweichung in den ersten 20 s
            dHor = Math.max(dHor, Math.hypot(ka.lage()[0] - kr.lage()[0], ka.lage()[1] - kr.lage()[1]));
            if (i < (int) Math.round(PRUEF_S / dtBild)) { dz20 = dz; dr20 = dr; dp20 = dp; dHor20 = dHor; }
        }
        A.info(name + ": Drift (seit S7d7)", f("die Referenz treibt %.1f m in %.0f s (%.2f m/s); Karte und Referenz bis %.2f m auseinander; in den ersten 20 s: Hub bis %.1f mm, Rollen bis %.3f°",
                Math.hypot(kr.lage()[0] - x0, kr.lage()[1] - y0), dauer, Math.hypot(kr.lage()[0] - x0, kr.lage()[1] - y0) / dauer, dHor, 1000 * dz20, dr20));
        double mz = sz / bilder, mr = sr / bilder;
        A.info(name + ": Bewegung (Referenz)", f("Hub RMS %.3f m, Rollen RMS %.2f°", Math.sqrt(Math.max(0, zs / bilder - mz * mz)), Math.sqrt(Math.max(0, rs / bilder - mr * mr))));
        // seit S7d8 (Seegang Werkbuch 97, SF143 A, Befund L5d7 3): die Grenzen gelten für die ersten PRUEF_S s, beim Kutter wie
        // bei der Fähre; danach stehen frei treibende Körper in verschiedenem Wasser, die ganze Dauer bleibt Auskunft mit der Drift
        String in = f(" in den ersten %.0f s", PRUEF_S);
        A.pruefe(dz20 <= 0.03, name + ": Hub, Karte (drei Bilder Verzug) gegen Referenz" + in, f("größte Abweichung %.1f mm (Karte und Referenz bis %.2f m auseinander)", 1000 * dz20, dHor20), "± 3 cm");
        A.pruefe(dr20 <= 0.5, name + ": Rollen, Karte gegen Referenz" + in, f("größte Abweichung %.3f°", dr20), "± 0,5°");
        if (stampfen) A.pruefe(dp20 <= 0.5, name + ": Stampfen, Karte gegen Referenz" + in, f("größte Abweichung %.3f°", dp20), "± 0,5° [G]");
        else A.info(name + ": Stampfen, Karte gegen Referenz" + in, f("größte Abweichung %.3f°", dp20));
        if (dauer > PRUEF_S + 1e-9)
            A.info(name + f(": über die ganzen %.0f s (seit S7d8 Auskunft)", dauer), f("Hub bis %.1f mm, Rollen bis %.3f°, Stampfen bis %.3f°; Karte und Referenz bis %.2f m auseinander",
                    1000 * dz, dr, dp, dHor));
        A.info(name + ": Rechenzeit", f("Karte je Bild %.2f ms (mit Senden und Warten), Referenz je Bild %.1f ms", msKarte / (bilder + Koerpersuche.RING), msRef / bilder));
    }

    // ------------------------------------------------------------------ 3

    static void faden() {
        A.teil("3 Die Flotte im eigenen Faden (Prüfstein 18, Faden)");
        List<Schwimmerangabe> l = new ArrayList<>();
        l.add(new Schwimmerangabe("faehre", "faehre", 465900, 6046100, 200, "frei", new double[0]));
        l.add(new Schwimmerangabe("kutter", "krabbenkutter", 466100, 6045900, 290, "frei", new double[0]));
        Random r = new Random(11);
        String[] arten = {"spierentonne", "spitztonne", "leuchttonne"};
        for (int i = 0; i < 27; i++) {
            Schwimmerangabe a = new Schwimmerangabe("tonne" + i, arten[i < 11 ? 0 : i < 24 ? 1 : 2], 465600 + 800 * r.nextDouble(), 6045600 + 800 * r.nextDouble(), 0, "kette", new double[] {16});
            a.farbe = i < 11 ? "rot" : "gruen";
            l.add(a);
        }
        Flotte fl = new Flotte(l, (x, y) -> -tiefe(x, y));
        Geraet g = karte.geraet();
        try {
            Flotte.Umwelt u = new Flotte.Umwelt() {
                @Override public double pegel(double ost, double nord) { return 0; }

                @Override public double[] wind() { return new double[] {8, 4}; }
            };
            double t = T0, dt = 1.0 / 60;
            double sumFaden = 0, sumBild = 0, maxBild = 0;
            int n = 0, punkte = 0;
            for (int i = 0; i < 600; i++) {
                karte.zeit(t);
                Ablauf a = new Ablauf(g);
                suche.lies(fl.wasserpunkte);
                suche.suche(a, karte, t, fl.wasserpunkte.anfrage());
                g.rechnen.einmal(c -> a.aufnehmen(c, null));
                long n0 = System.nanoTime();
                fl.vorBild(t, 1, dt, u);
                double ms = (System.nanoTime() - n0) / 1e6;
                fl.warte();
                if (i >= 300) {
                    sumFaden += fl.fadenMs;
                    sumBild += ms;
                    maxBild = Math.max(maxBild, ms);
                    n++;
                    punkte = suche.zuletzt;
                }
                t += dt;
            }
            if (fl.fehler != null) throw new IllegalStateException("Flotte: " + fl.fehler);
            double jeBild = sumFaden / n, jeTeil = jeBild / (dt / 0.004);
            A.info("Flotte", f("%d Körper, %d Punkte auf der Karte je Bild", fl.schwimmer.size(), punkte));
            A.pruefe(jeTeil <= 2.0, "Faden der Flotte je Teilschritt (4 ms)", f("%.2f ms (je Bild %.2f ms)", jeTeil, jeBild), "≤ 2 ms [G]");
            A.pruefe(sumBild / n <= 0.2, "Faden der Bilder: Lagen und Anfrage (vorBild)", f("im Mittel %.3f ms, höchstens %.3f ms", sumBild / n, maxBild), "≤ 0,2 ms");
            A.info("Lesen des Ergebnisses der Karte (im Faden der Bilder)", f("%.3f ms", suche.liesMs));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            fl.close();
        }
        stromTonne();
    }

    /**
     * Seit S7b-4 (A25): eine Spitztonne an der Kette in der Flotte, ruhiges Wasser, Strom 1 m/s nach Ost über die Umwelt;
     * nach 60 s die mittlere Neigung über 20 s gegen die Rechnung aus S7a (Teil 10: 4,46° bei 1 m/s), und ob sie mit dem
     * Strom geht.
     */
    static void stromTonne() {
        List<Schwimmerangabe> l = new ArrayList<>();
        l.add(new Schwimmerangabe("tonne", "spitztonne", 466000, 6046000, 0, "kette", new double[] {16}));
        Flotte fl = new Flotte(l, (x, y) -> -12.0);
        try {
            Flotte.Umwelt u = new Flotte.Umwelt() {
                @Override public double pegel(double ost, double nord) { return 0; }

                @Override public double[] wind() { return new double[] {0, 0}; }

                @Override public double[] strom(double ost, double nord) { return new double[] {1, 0}; }
            };
            double t = T0, dt = 1.0 / 30, neig = 0, ost = 0;
            int n = 0;
            for (int i = 0; i < 2400; i++) {
                fl.vorBild(t, 1, dt, u);
                fl.warte();
                if (i >= 1800) {
                    Koerper k = fl.schwimmer.get(0).koerper;
                    double[] z = k.richtungWelt(0, 0, 1);
                    neig += Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, z[2]))));
                    ost += z[0];
                    n++;
                }
                t += dt;
            }
            if (fl.fehler != null) throw new IllegalStateException("Flotte: " + fl.fehler);
            neig /= n;
            boolean mit = ost > 0;
            A.pruefe(Math.abs(neig / 4.46 - 1) <= 0.2 && mit, "Spitztonne im Strom 1 m/s über die Flotte: Neigung gegen die Rechnung (S7a)",
                    f("%.2f° gegen 4,46° (%+.1f %%), %s", neig, 100 * (neig / 4.46 - 1), mit ? "kippt mit dem Strom" : "kippt gegen den Strom"), "± 20 % [G]");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            fl.close();
        }
    }

    // ------------------------------------------------------------------ 4

    static Buehne b;
    static Flaechenwart w;

    static void anwendung(File szenen, int breite, int hoehe) {
        A.teil("4 In der Anwendung (Prüfsteine 17–19; seit S7b-4 Faden und Strom)");
        try {
            Szenen sz = Szenen.lies(szenen);
            b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            try {
                w = b.wart();
                String[][] faelle = {
                        {"ruhigsee", "2026-10-30T13:30:00Z", "hafen kutter tonne faehre"},     // seit S7d2 13:30: die Fähre liegt (Fahrplan nach der Tide)
                        {"seegang", "2026-10-25T13:30:00Z", "hafen kutter anleger_see tonne faehre"},
                        {"xaver", "2013-12-05T12:30:00Z", "hafen"}};
                double karteMax = 0, bildMax = 0, fadenMax = 0, kenterMax = 0, feinMin = 1;
                StringBuilder feinWo = new StringBuilder();                      // seit S7d4
                Map<String, List<Double>> kutterRoll = new java.util.LinkedHashMap<>();
                StringBuilder koerperText = new StringBuilder();
                List<String> kentern = new ArrayList<>();
                StringBuilder kenterText = new StringBuilder(), gitterText = new StringBuilder();
                for (String[] fa : faelle) {
                    Szenario s = sz.szenario(fa[0]);
                    if (s == null) throw new IllegalArgumentException("Szenario " + fa[0] + " fehlt");
                    pegel(b, sz);                                                   // seit S7d2
                    b.oeffne(s);
                    b.wasserbild().seegang = true;
                    Flotte fl = b.flotte();
                    if (fl == null) throw new IllegalStateException("keine Flotte im Szenario " + fa[0]);
                    boolean berichtet = false;
                    for (String an : fa[2].split(" ")) {
                        Ansicht ans = sz.ansicht(an);
                        double t = (Instant.parse(fa[1]).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
                        Blick bl = new Blick();
                        ans.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                        bl.groesse(breite, hoehe);
                        double[] wa = b.wasserUndSeegang(bl.ost, bl.nord);
                        Ansicht.hebe(bl, wa[0], wa[1], ans.ueberWasser);
                        b.standbild(bl, t);                                   // Stände geladen, Belichtung steht
                        fl.neuSetzen();
                        // Bilder wie die Bildschleife mit Zeitraffer 1, bis die Fläche steht, dann 20 s
                        long uhrNs = 0;
                        double dt = 1.0 / 30;
                        long w0 = System.nanoTime();
                        int bereitBild = -1;
                        double fadenSumme = 0;
                        int fadenZahl = 0;
                        double[] koerper = new double[3];
                        Map<String, Double> summe = new java.util.TreeMap<>();
                        int nSumme = 0;
                        for (int i = 0; i < 4500; i++) {
                            w.zeitraffer(1);
                            w.vorBild(bl, t, uhrNs);
                            b.flotteBild(t, 1, dt);
                            Buehne.Bildzeiten z = b.bildzeiten(bl, t, 1, true);
                            w.nachBild();
                            // seit S7d4: bereit erst mit der Körperfläche; die Krängung zählt ab da
                            if (bereitBild < 0 && w.bereit() && !w.wechselt() && (w.koerpermitte() == null || w.koerperBereit())) {
                                bereitBild = i;
                                fl.warte();
                                for (Flotte.Schwimmer x : fl.schwimmer) x.kraengungMax = 0;
                            }
                            if (bereitBild >= 0 && i > bereitBild + 60) {
                                for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) summe.merge(e.getKey(), e.getValue(), Double::sum);
                                nSumme++;
                                fl.warte();
                                fadenSumme += fl.fadenMs;
                                fadenZahl++;
                            }
                            t += dt;
                            uhrNs += Math.round(dt * 1e9);
                            if (bereitBild >= 0 && i >= bereitBild + 600) break;
                            if ((System.nanoTime() - w0) / 1e9 > 420) break;
                        }
                        fl.warte();
                        double sk = summe.getOrDefault("see_koerper", 0.0) / Math.max(1, nSumme);
                        double sb = 0;
                        for (Map.Entry<String, Double> e : summe.entrySet()) if (e.getKey().startsWith("Schwimmer")) sb += e.getValue() / Math.max(1, nSumme);
                        karteMax = Math.max(karteMax, sk);
                        bildMax = Math.max(bildMax, sb);
                        float[] px = b.standbild(bl, t);
                        String datei = "S7b4_" + fa[0] + "_" + an + ".png";
                        javax.imageio.ImageIO.write(SeegangBilder.bild(px, breite, hoehe), "png", Path.of("docs", "status", datei).toFile());
                        // Prüfstein 17: die Lagen im Bild sind die der Physik
                        double[] lp = fl.lagen(), lb = b.schwimmerbild().lagen();
                        boolean gleich = lb != null && lp.length == lb.length;
                        for (int i = 0; gleich && i < lp.length; i++) if (Double.doubleToRawLongBits(lp[i]) != Double.doubleToRawLongBits(lb[i])) gleich = false;
                        int gez = b.schwimmerbild().gezeichnet;
                        A.pruefe(gleich && gez == fl.schwimmer.size(), f("%s, %s: Lage im Bild gleich Lage der Physik", fa[0], an),
                                f("%s, %d von %d Körpern gezeichnet", gleich ? "bitgleich" : "ANDERS", gez, fl.schwimmer.size()), "bitgleich");
                        Flotte.Schwimmer fae = fl.schwimmer.get(0), ku = fl.schwimmer.get(1);
                        double[] of = fae.koerper.lage();
                        A.info(f("%s, %s", fa[0], an), f("Bild docs/status/%s; Fläche bereit nach %d Bildern; Fähre %.2f m vom Platz, Krängung %.1f°; Kutter Krängung %.1f°, Trimm %.1f°; Pegel %.2f m NHN",
                                datei, bereitBild, Math.hypot(of[0] - fae.angabe.ost, of[1] - fae.angabe.nord), fae.koerper.kraengung(), ku.koerper.kraengung(), ku.koerper.trimm(), b.spiegel(of[0], of[1])));
                        double fadenTeil = fadenSumme / Math.max(1, fadenZahl) / (1.0 / 30 / 0.004);
                        fadenMax = Math.max(fadenMax, fadenTeil);
                        A.info("  Karte und Bild je Bild (Mittel)", f("see_koerper %.3f ms, Schwimmer %.3f ms; Faden der Flotte %.2f ms je Bild, %.2f ms je Teilschritt; Punkte %d%s, %d Fäden je Punkt",
                                sk, sb, fadenSumme / Math.max(1, fadenZahl), fadenTeil, w.koerpersuche() != null ? w.koerpersuche().zuletzt : 0,
                                w.koerpersuche() != null && w.koerpersuche().zuletztWarm ? ", mit Startwerten" : "", w.koerpersuche() != null ? w.koerpersuche().zuletztFaeden : 0));
                        // seit S7d2: Fähre nach Fahrplan, Kentern
                        A.info("  Fähre", "Phase „" + fae.phase() + "“");
                        if (!berichtet) {                                       // seit S7d4: die Körperfläche des Szenarios
                            double[] km = w.koerpermitte();
                            A.info("  Körperfläche", km == null ? "keine" : f("Mitte %.0f / %.0f; %s%s", km[0], km[1], w.koerperBericht(),
                                    w.koerperFehler() != null ? "; FEHLER " + w.koerperFehler() : ""));
                            koerperText.append(f("%s%s: %s", koerperText.length() > 0 ? " | " : "", fa[0], w.koerperBereit() ? "steht" : "FEHLT"));
                            berichtet = true;
                        }
                        // seit S7d3 (Befund L5d2 2): welches Gitter der Fläche die Karte an den Punkten der Schiffe nahm
                        StringBuilder gt = new StringBuilder();
                        Wasserpunkte.Stand gst = fl.wasserpunkte.stand();
                        for (Flotte.Schwimmer x : fl.schwimmer) {
                            int kx = fl.schwimmer.indexOf(x);
                            if (x.kiel == null || gst == null || kx >= gst.anfrage.anfang.length || gst.anfrage.anfang[kx] < 0) continue;
                            double[] gm = new double[5];
                            int a0 = gst.anfrage.anfang[kx], mz = gst.anfrage.anzahl[kx];
                            for (int c = a0; c < a0 + mz; c++) {
                                double[] gg = Koerpersuche.gitter(gst.werte[c * gst.je + 7]);
                                for (int q = 0; q < 5; q++) gm[q] += gg[q] / mz;
                            }
                            // seit S7d4: Anteil der Energie aus einem feinen Gitter (des Auges oder der Körperfläche)
                            double feinAnteil = gm[0] + gm[3];
                            // seit S7e2: geprüft werden Kutter und Fähre (wie der Name des Prüfsteins sagt); der Adler-Express kommt
                            // auf seinem Weg von weit draußen und nimmt dort richtig das grobe Gitter (Auskunft, Befund L5e2)
                            boolean pruef = x.angabe.name.equals("kutter") || x.angabe.name.equals("hilligenlei");
                            if (w.koerpermitte() != null && pruef) {
                                feinMin = Math.min(feinMin, feinAnteil);
                                if (feinAnteil < 0.99) feinWo.append(f("%s%s, %s, %s %.2f", feinWo.length() > 0 ? "; " : "", fa[0], an, x.angabe.name, feinAnteil));
                            }
                            gt.append(f("%s%s: Auge fein %.2f, grob %.2f, fern %.2f; Körperfläche fein %.2f, grob %.2f", gt.length() > 0 ? "; " : "", x.angabe.name, gm[0], gm[1], gm[2], gm[3], gm[4]));
                            gitterText.append(f("%s%s, %s, %s: fein %.2f (Auge %.2f, Körperfläche %.2f), Krängung bis %.1f°", gitterText.length() > 0 ? " | " : "", fa[0], an, x.angabe.name,
                                    feinAnteil, gm[0], gm[3], x.kraengungMax));
                            if (x.angabe.name.toLowerCase(java.util.Locale.ROOT).contains("kutter")) kutterRoll.computeIfAbsent(fa[0], kk -> new ArrayList<>()).add(x.kraengungMax);
                        }
                        A.info("  Gitter der Fläche an den Schiffen", gt.length() > 0 ? gt.toString() : "kein Stand");
                        for (Flotte.Schwimmer x : fl.schwimmer)
                            if (x.kiel != null) {
                                kenterMax = Math.max(kenterMax, x.kraengungMax);
                                if (x.kentern != null) kentern.add(fa[0] + ", " + an + ", " + x.angabe.name + ": " + x.kentern);
                                kenterText.append(f("%s%s, %s, %s %.1f°", kenterText.length() > 0 ? "; " : "", fa[0], an, x.angabe.name, x.kraengungMax));
                            }
                        // seit S7b-4: der Strom an Ho 7 und ihre Neigung
                        for (Flotte.Schwimmer tw : fl.schwimmer)
                            if (tw.angabe.name.endsWith("Ho_7")) {
                                double[] z = tw.koerper.richtungWelt(0, 0, 1);
                                double[] so = b.stromAm(t, tw.angabe.ost, tw.angabe.nord);
                                A.info("  Ho 7 im Strom", so == null ? f("kein Strom am Ort; Neigung %.1f°", Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, z[2])))))
                                        : f("Strom %.2f m/s nach %.0f°; Neigung %.1f° nach %.0f°", Math.hypot(so[0], so[1]), (Math.toDegrees(Math.atan2(so[0], so[1])) + 360) % 360,
                                        Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, z[2])))), (Math.toDegrees(Math.atan2(z[0], z[1])) + 360) % 360));
                            }
                    }
                }
                A.pruefe(karteMax <= 0.75, "Kosten: Wasser unter den Körpern auf der Karte (see_koerper; seit S7d6 Grenze 0,75 ms, SF132)", f("höchstens %.3f ms je Bild", karteMax), "≤ 0,75 ms [G]");
                A.pruefe(bildMax <= 1.0, "Kosten: Bild der Schwimmer (Licht, Schatten, Netze)", f("höchstens %.3f ms je Bild", bildMax), "≤ 1,0 ms [G]");
                A.pruefe(fadenMax <= 2.5, "Kosten: Faden der Flotte je Teilschritt (4 ms) in der Anwendung (seit S7d7 Grenze 2,5 ms, SF140 A; für sich allein Teil 3, 2 ms)", f("höchstens %.2f ms", fadenMax), "≤ 2,5 ms [G]");
                A.pruefe(kentern.isEmpty(), "Kein Schiff gekentert, in keiner Ansicht (seit S7d2, Befund L5d 3)",
                        kentern.isEmpty() ? f("größte Krängung %.1f°", kenterMax) : String.join(" | ", kentern), "≤ " + (int) Flotte.KENTERN + "° [G]");
                A.info("  größte Krängung je Ansicht und Schiff", kenterText.toString());
                // seit S7d4 (SF121 A): das Wasser an den Schiffen kommt aus einem feinen Gitter, in jeder Ansicht
                A.pruefe(feinMin >= 0.99 && !koerperText.toString().contains("FEHLT"), "Wasser an Kutter und Fähre aus einem feinen Gitter (Auge oder Körperfläche), in jeder Ansicht (seit S7d4)",
                        f("kleinster Anteil %.2f%s; Körperfläche %s", feinMin, feinWo.length() > 0 ? " (" + feinWo + ")" : "", koerperText), "≥ 0,99 [G]");
                StringBuilder kr = new StringBuilder();
                for (Map.Entry<String, List<Double>> e : kutterRoll.entrySet()) {
                    double lo = 1e9, hi = -1e9;
                    for (double v : e.getValue()) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
                    kr.append(f("%s%s: %.1f … %.1f° über %d Ansichten", kr.length() > 0 ? "; " : "", e.getKey(), lo, hi, e.getValue().size()));
                }
                A.info("  Krängung des Kutters je Szenario über die Ansichten (seit S7d4; 20 s je Ansicht)", kr.toString());
                A.info("  Gitter und Krängung je Ansicht und Schiff (seit S7d3, seit S7d4 mit der Körperfläche)", gitterText.toString());
                A.info("Sichtprüfung (Prüfstein 19)", "Fähre am Anleger (Ansicht faehre), Boot vor Anker, Tonnen im Strom: Bilder S7b4_*.png");
            } finally {
                b.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
