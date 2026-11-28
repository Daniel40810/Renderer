package com.dan.lauf;

import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Phasenfeld;
import com.dan.core.szene.Profilpuffer;
import com.dan.core.szene.Reflexion;
import com.dan.core.szene.Seegangsnetz;
import com.dan.physics.waves.gpu.NahfeldKarte;
import com.dan.physics.waves.nah.Nahfeld;
import com.dan.render.seegang.SeegangKarte;
import com.dan.render.seegang.Schaumfeld;
import com.dan.render.seegang.Seegangbild;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Die Oberfläche des Seegangs zu einem Bild (Seegang Werkbuch, S3c-2): Nahfeld grob und fein um den Blick bauen und auf
 * der Karte anlaufen lassen ({@link Nahfeldquelle}, {@link NahfeldKarte}), die Phasenfelder auf dem Prozessor rechnen,
 * daraus die {@link Oberflaeche} mit dem Fernfeld aus den Netzen des Seegangs des Archivs, die {@link SeegangKarte} und
 * den Bildteil {@link Seegangbild}. Für Standbilder (SF26 A); die Anwendung folgt in S3d.
 * <p>
 * <b>Lage (SF27 A):</b> das grobe Gitter (5 km) 500 m vor dem Auge längs des Blicks wie {@code NahfeldLauf}; das feine
 * (1 km) über der Brandungslinie im Blick: dort, wo {@code waves.diss} längs der Blickachse bis 1,5 km am größten ist
 * (Schritte zu 25 m), sonst 300 m vor dem Auge; {@code ansicht.<name>.fein} in der Szenendatei geht vor.
 */
public final class Seegangsflaeche implements AutoCloseable {
    public static final String VERSION = "0.2";
    /** Lage der Gitter vor dem Auge (m) [G], wie NahfeldLauf; Suche der Brandungslinie bis so weit (m) in Schritten [G]. */
    public static final double VOR_GROB = 500, VOR_FEIN = 300, SUCHE_WEIT = 1500, SUCHE_SCHRITT = 25;
    /** Clipmap: Stufen, Stellen je Seite, Abstand der feinsten (m) [G, S3b]. */
    public static final int STUFEN = 8, N = 128;
    public static final double D0 = 0.25;
    /**
     * Seit S3c-5 (B49b, Diagnose): nach dem Anlauf bis zur Toleranz noch einmal so viele Runden ohne Abbruch (doppelter
     * Anlauf). Zeigt, ob das Nahfeld eingeschwungen war. Vorgabe aus.
     */
    public static volatile boolean ANLAUF_DOPPELT = false;
    /**
     * Seit S4a (Seegang Werkbuch 35), zur Prüfung: nach dem Anlauf einen Schritt des Nahfelds von {@link #SENKEN_DT} s
     * rechnen und dessen Senken je Zelle ablegen ({@link #senkenFein}, {@link #senkenGrob}; im Anlauf sind sie 0). Die
     * Fläche selbst kommt aus dem Stand vor diesem Schritt. Vorgabe aus.
     */
    public static volatile boolean SENKEN = false;
    public static final double SENKEN_DT = 10;
    /**
     * Seit S5b-6 (Seegang Werkbuch 55): die reflektierten Stützstellen mit ihren Partnern koppeln (gemeinsame Hülle,
     * gespiegelte Richtung, Konstanten von Φ und T an den Wänden: ein Bauch vor Wand und Deckwerk), {@link Reflexion}.
     * Vorgabe an; {@code -Dseegang.reflexion=aus}: wie bis S5b-5.
     */
    public static volatile boolean REFLEXION = !"aus".equalsIgnoreCase(System.getProperty("seegang.reflexion", "an"));

    /** Der Bildteil; seit S7d4 null für die Körperfläche ({@link #baueKoerper}). */
    public final Seegangbild bild;
    public final SeegangKarte karte;
    public final Oberflaeche oberflaeche;
    /** Was der Bau gekostet hat und wo die Gitter liegen (zur Auskunft). */
    public final String bericht;
    /** Mitte des feinen Gitters (UTM) und ob sie aus der Suche über der Brandungslinie kam. */
    public final double feinOst, feinNord;
    public final String feinWoher;
    /** Seit S4a, nur mit {@link #SENKEN}: je Zelle des Nahfelds dwc, dbr, dbf, dveg (W/m², je vier), sonst null. */
    public float[] senkenFein, senkenGrob;
    /** Seit S5b-6b: die Paare der Reflexion dieser Fläche mit der Bilanz der Wände (grob, fein). */
    public Reflexion reflexion;

    private Seegangsflaeche(Seegangbild bild, SeegangKarte karte, Oberflaeche o, String bericht, double fo, double fn, String fw) {
        this.bild = bild; this.karte = karte; this.oberflaeche = o; this.bericht = bericht; this.feinOst = fo; this.feinNord = fn; this.feinWoher = fw;
    }

    /**
     * Baut die Fläche für den Blick b zur Szenenzeit t (s ab dem Start des Archivs).
     *
     * @param netze die Netze des Seegangs des Archivs (für das Fernfeld und die Brandungslinie), zur Zeit t gesetzt
     * @param fein  Mitte des feinen Gitters aus der Ansicht (null: von selbst)
     */
    public static Seegangsflaeche baue(Geraet g, Konto konto, File gebiet, Path archiv, List<Seegangsnetz> netze, Blick b, double t, double[] fein) throws IOException {
        try {
            return baue(g, konto, gebiet, archiv, netze, b, t, fein, null);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);                               // ohne Takt wartet nichts
        }
    }

    /**
     * Seit S3d (Seegang Werkbuch 28): wie der Anlauf läuft. In der Anwendung rechnet ihn der {@link Flaechenwart} in Stücken
     * zwischen den Bildern; ohne Takt in einem Zug (Standbild).
     */
    public interface Takt {
        /** Rechnet den Anlauf zu Ende ({@code a.weiter} bis true); darf mit InterruptedException abbrechen. */
        void anlauf(com.dan.physics.waves.gpu.NahfeldKarte.Anlauf a) throws InterruptedException;
    }

    /** Wie {@link #baue(Geraet, Konto, File, Path, List, Blick, double, double[])}, mit dem Takt des Anlaufs (null: in einem Zug). */
    public static Seegangsflaeche baue(Geraet g, Konto konto, File gebiet, Path archiv, List<Seegangsnetz> netze, Blick b, double t, double[] fein, Takt takt)
            throws IOException, InterruptedException {
        return baue(g, konto, gebiet, archiv, netze, b, t, fein, takt, true);
    }

    /**
     * Seit S7d4 (Seegang Werkbuch 92, SF121 A): die Körperfläche – Nahfeld grob (5 km) und fein (1 km) wie für ein Bild, aber
     * fest um die Mitte m (UTM; beide Gitter auf ihr, auf den Linien von G3w), ohne Bildteil und mit einer Karte aus nur einer
     * kleinen Stufe: Sie dient nur der Körpersuche ({@code Koerpersuche#koerperflaeche}). {@link #bild} ist null.
     */
    public static Seegangsflaeche baueKoerper(Geraet g, Konto konto, File gebiet, Path archiv, List<Seegangsnetz> netze, double[] m, double t, Takt takt)
            throws IOException, InterruptedException {
        Blick b = new Blick();
        b.ost = m[0];
        b.nord = m[1] - VOR_GROB;                                           // das grobe Gitter liegt VOR_GROB vor dem Auge längs des Blicks
        b.richtung = 0;
        return baue(g, konto, gebiet, archiv, netze, b, t, new double[] {m[0], m[1]}, takt, false);
    }

    private static Seegangsflaeche baue(Geraet g, Konto konto, File gebiet, Path archiv, List<Seegangsnetz> netze, Blick b, double t, double[] fein, Takt takt,
                                        boolean mitBild) throws IOException, InterruptedException {
        long t0 = System.nanoTime();
        List<Seegangsnetz> see = new ArrayList<>(netze);
        see.sort(Comparator.comparingDouble(s -> s.gitter.dx));
        double r = Math.toRadians(b.richtung), ex = Math.sin(r), ey = Math.cos(r);
        double fo, fn;
        String fw;
        if (fein != null) { fo = fein[0]; fn = fein[1]; fw = "aus der Ansicht"; }
        else {
            double best = 0, bs = VOR_FEIN;
            for (double s = SUCHE_SCHRITT; s <= SUCHE_WEIT; s += SUCHE_SCHRITT) {
                double[] w = wert(see, b.ost + s * ex, b.nord + s * ey, t);
                if (w != null && w[5] > best) { best = w[5]; bs = s; }
            }
            fo = b.ost + bs * ex;
            fn = b.nord + bs * ey;
            fw = best > 0 ? String.format(java.util.Locale.GERMAN, "über der Brandungslinie, %.0f m vor dem Auge (diss %.1f W/m²)", bs, best) : "300 m vor dem Auge (keine Brandung im Blick)";
        }
        Nahfeld grob, nf;
        int[] runden = new int[2];
        float[][] senkenF = new float[1][], senkenG = new float[1][];
        double tBau, tAnlauf, tPhase;
        java.util.concurrent.FutureTask<Object[]> grobPhase;
        double ox, oy;
        double[] om, ri;
        Reflexion rf;
        try (Nahfeldquelle q = new Nahfeldquelle(gebiet, archiv)) {
            long a0 = System.nanoTime();
            grob = q.baue(Nahfeldquelle.GROB, b.ost + VOR_GROB * ex, b.nord + VOR_GROB * ey, t);
            Nahfeld.Ziel zg = q.ziel(grob, t);
            // das feine Gitter ganz im groben, mit Abstand für dessen Rand
            Nahfeldquelle.Stufe sf = Nahfeldquelle.FEIN;
            double halb = 0.5 * sf.n() * sf.zelle(), rand = 10 * Nahfeldquelle.GROB.zelle();
            fo = Math.max(grob.x0 + rand + halb, Math.min(grob.x0 + grob.nx * grob.dx - rand - halb, fo));
            fn = Math.max(grob.y0 + rand + halb, Math.min(grob.y0 + grob.ny * grob.dx - rand - halb, fn));
            nf = q.baue(sf, fo, fn, t);
            tBau = (System.nanoTime() - a0) * 1e-9;
            long a1 = System.nanoTime();
            try (NahfeldKarte k = new NahfeldKarte(g, konto, grob, false)) {
                k.rand(grob.randVarianz(zg));
                runden[0] = anlauf(k, takt);
                if (ANLAUF_DOPPELT) runden[0] += k.anlauf(runden[0], 0, 600);
                k.runter();
                if (SENKEN) { k.schritt(SENKEN_DT); senkenG[0] = k.senken(); }   // seit S4a, nur zur Prüfung
            }
            // seit S3d-2 (SF38 A): das grobe Phasenfeld braucht nur den groben Anlauf; der Prozessor rechnet es, während
            // die Karte den feinen Anlauf rechnet (dieselben Eingaben, darum dasselbe Ergebnis)
            final Nahfeld gr = grob;
            om = grob.omega.clone();
            ri = Phasenfeld.richtungen(grob.nb, grob.nd);
            // seit S5b-6: die Partner der reflektierten Stützstellen aus den Wänden der groben Stufe, fest für diese Fläche;
            // ihre Richtungen genau gespiegelt, bevor die Phasenfelder rechnen. Seit S5b-6b auch aus den Wänden des feinen
            // Gitters (seine Tiefe steht vor dem Anlauf fest; die Energie davor aus der groben Stufe)
            final Oberflaeche.Stufe sgVor = stufe("grob", gr);
            rf = REFLEXION ? Reflexion.partner(sgVor, geometrie("fein", nf)) : Reflexion.keine(grob.nb, grob.nd);
            rf.richtungen(ri);
            final Reflexion rfF = rf;
            ox = Math.floor((grob.x0 + 0.5 * grob.nx * grob.dx) / 100) * 100;
            oy = Math.floor((grob.y0 + 0.5 * grob.ny * grob.dx) / 100) * 100;
            final double[] omF = om, riF = ri;
            final double oxF = ox, oyF = oy;
            grobPhase = new java.util.concurrent.FutureTask<>(() -> {
                long q0 = System.nanoTime();
                Oberflaeche.Stufe sg = sgVor;
                Phasenfeld pg = Phasenfeld.rechne(sg.nx, sg.ny, sg.dx, sg.x0, sg.y0, sg.h, gr.u, gr.v, omF, riF, gr.nd, oxF, oyF, null, t, sg.e);
                rfF.wand(sg, pg, false);                                     // seit S5b-6b: Bauch an den geraden Wandstücken der groben Stufe
                sg.phasen(pg, pg);
                return new Object[] {sg, pg, (System.nanoTime() - q0) * 1e-9};
            });
            Thread pt = new Thread(grobPhase, "Phasenfeld grob");
            pt.setDaemon(true);
            pt.start();
            nf.rand = Nahfeld.schachtel(grob, nf);
            try (NahfeldKarte k = new NahfeldKarte(g, konto, nf, false)) {
                k.rand(nf.randVarianz());
                runden[1] = anlauf(k, takt);
                if (ANLAUF_DOPPELT) runden[1] += k.anlauf(runden[1], 0, 600);
                k.runter();
                if (SENKEN) { k.schritt(SENKEN_DT); senkenF[0] = k.senken(); }   // seit S4a, nur zur Prüfung
            }
            tAnlauf = (System.nanoTime() - a1) * 1e-9;
        }
        // Oberfläche: Stufen aus dem Nahfeld, Phasenfelder auf dem Prozessor
        long p0 = System.nanoTime();
        int nb = grob.nb, nd = grob.nd;
        double[] unten = new double[nb], oben = new double[nb];
        double w = Math.sqrt(grob.stufe);
        for (int i = 0; i < nb; i++) { unten[i] = om[i] / w; oben[i] = om[i] * w; }
        Profilpuffer pp = new Profilpuffer(om, unten, oben, nd, 0x5EE6A3L, false);
        rf.teilen(pp);                                                       // seit S5b-6: die reflektierten lesen die Hülle ihres Partners
        Object[] gp;
        try {
            gp = grobPhase.get();
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Seegangsfläche: Phasenfeld grob", e.getCause());
        }
        Oberflaeche.Stufe sg = (Oberflaeche.Stufe) gp[0], sfz = stufe("fein", nf);
        Phasenfeld pg = (Phasenfeld) gp[1];
        double tGrob = (double) gp[2];
        Phasenfeld pf = Phasenfeld.rechne(sfz.nx, sfz.ny, sfz.dx, sfz.x0, sfz.y0, sfz.h, nf.u, nf.v, om, ri, nd, ox, oy, pg, t, sfz.e);
        long w0 = System.nanoTime();
        rf.wand(sfz, pf, true);                                              // seit S5b-6b: im feinen der Rest (der Rand kommt vom groben)
        double tWand = (System.nanoTime() - w0) * 1e-9;
        sfz.phasen(pf, pf);
        Oberflaeche o = new Oberflaeche(pp, nd);
        o.grob = sg;
        o.fein = sfz;
        // seit S3d: das Fernfeld zur Zeit des Bilds (in der Anwendung läuft sie; im Standbild ist sie t)
        final Seegangbild[] sbH = new Seegangbild[1];
        o.fernfeld = (x, y) -> {
            double tt = sbH[0] != null ? sbH[0].zeit : t;
            double[] v = wert(see, x, y, tt);
            return v == null || !(v[0] > 0) ? new double[] {0, 1, 0} : new double[] {v[0], v[1], Math.atan2(v[3], v[2])};
        };
        o.vorbereiten();
        tPhase = (System.nanoTime() - p0) * 1e-9;
        if (!mitBild) {                                                      // seit S7d4: die Körperfläche
            SeegangKarte kk = new SeegangKarte(g, konto, o, 1, 8, D0, false);
            kk.bezug(Math.floor(fo / 64) * 64, Math.floor(fn / 64) * 64);
            kk.eingaben();
            String bk = String.format(java.util.Locale.GERMAN,
                    "Körperfläche: grob %d × %d zu %.0f m ab (%.0f, %.0f), fein %d × %d zu %.0f m ab (%.0f, %.0f); Bau %.1f s, Anlauf %d und %d Runden in %.1f s, Phasenfelder %.1f s, gesamt %.1f s; %d Stützstellen aktiv",
                    grob.nx, grob.ny, grob.dx, grob.x0, grob.y0, nf.nx, nf.ny, nf.dx, nf.x0, nf.y0, tBau, runden[0], runden[1], tAnlauf, tPhase, (System.nanoTime() - t0) * 1e-9, kk.aktivZahl());
            Seegangsflaeche fk = new Seegangsflaeche(null, kk, o, bk, fo, fn, fw);
            fk.reflexion = rf;
            return fk;
        }
        SeegangKarte karte = new SeegangKarte(g, konto, o, STUFEN, N, D0, false);
        karte.bezug(Math.floor(b.ost / 64) * 64, Math.floor(b.nord / 64) * 64);   // B am Auge, bevor etwas relativ zu B rechnet
        karte.fernOst = b.ost;                                               // seit S3d: das Fernfeld am Auge (die Anwendung führt es mit)
        karte.fernNord = b.nord;
        karte.eingaben();
        karte.drift(drift(nf), drift(grob));                                 // seit S4a: die Drift des Schaums je Zelle
        Seegangbild sb = new Seegangbild(g, konto, karte);
        sb.zeit = t;
        sbH[0] = sb;
        String bericht = String.format(java.util.Locale.GERMAN,
                "grob %d × %d zu %.0f m ab (%.0f, %.0f), fein %d × %d zu %.0f m ab (%.0f, %.0f), %s; Bau %.1f s, Anlauf %d und %d Runden in %.1f s, Phasenfelder %.1f s nach dem Anlauf (grob %.1f s neben dem feinen Anlauf), gesamt %.1f s",
                grob.nx, grob.ny, grob.dx, grob.x0, grob.y0, nf.nx, nf.ny, nf.dx, nf.x0, nf.y0, fw, tBau, runden[0], runden[1], tAnlauf, tPhase, tGrob, (System.nanoTime() - t0) * 1e-9)
                + "; Reflexion: " + rf.bericht() + "; Bauch an den Wänden grob: " + (rf.bilanzGrob != null ? rf.bilanzGrob.text() : "–")
                + "; fein: " + (rf.bilanzFein != null ? rf.bilanzFein.text() : "–") + String.format(java.util.Locale.GERMAN, " (%.1f s)", tWand);
        Seegangsflaeche fl = new Seegangsflaeche(sb, karte, o, bericht, fo, fn, fw);
        fl.reflexion = rf;
        fl.senkenFein = senkenF[0];
        fl.senkenGrob = senkenG[0];
        return fl;
    }

    /**
     * Seit S4a (Seegang Werkbuch 35, Plan 6.4): die Drift des Schaums je Zelle des Nahfelds (m/s, je Zelle Ost, Nord):
     * die Strömung, die Stokes-Drift an der Oberfläche aus dem Spektrum, u_s = Σ σ k E cosh(2kh) / sinh²(kh) · e (E die
     * Varianz der Stützstelle; im Tiefen 2 σ k E), und {@link Schaumfeld#WINDDRIFT} U10 [G]. Trocken 0. Seit S4a-2 je Zelle
     * vier Werte: Drift Ost, Nord, dazu U10 Ost, Nord (für die Schaumköpfe der unaufgelösten Windsee).
     */
    static float[] drift(Nahfeld n) {
        float[] d = new float[4 * n.nc];
        for (int c = 0; c < n.nc; c++) {
            double h = n.tiefe(c);
            if (!(h > 0.05) || !n.nass(c)) continue;
            double sx = 0, sy = 0;
            for (int b = 0; b < n.nb; b++)
                for (int q = 0; q < n.nd; q++) {
                    double e = n.energie(c, b, q);
                    if (!(e > 0)) continue;
                    double k = n.wellenzahl(c, b, q), sg = n.sigma(c, b, q), kh = k * h;
                    if (!(k > 0) || !(sg > 0)) continue;
                    double f = kh > 10 ? 2 : Math.cosh(2 * kh) / Math.pow(Math.sinh(kh), 2);
                    double us = sg * k * e * f;
                    sx += us * n.cosT[q];
                    sy += us * n.sinT[q];
                }
            double ux = n.u[c] + Schaumfeld.WINDDRIFT * n.windU[c] + sx, uy = n.v[c] + Schaumfeld.WINDDRIFT * n.windV[c] + sy;
            d[4 * c] = Double.isFinite(ux) ? (float) ux : 0;
            d[4 * c + 1] = Double.isFinite(uy) ? (float) uy : 0;
            d[4 * c + 2] = Double.isFinite(n.windU[c]) ? (float) n.windU[c] : 0;
            d[4 * c + 3] = Double.isFinite(n.windV[c]) ? (float) n.windV[c] : 0;
        }
        return d;
    }

    /** Der Anlauf wie im Standbild (3000 Runden höchstens, Toleranz 1e-5, dt höchstens 600 s), mit Takt in Stücken. */
    private static int anlauf(NahfeldKarte k, Takt takt) throws InterruptedException {
        if (takt == null) return k.anlauf(3000, 1e-5, 600);
        NahfeldKarte.Anlauf a = k.anlaufInStuecken(3000, 1e-5, 600);
        takt.anlauf(a);
        return a.runden();
    }

    /** Eine Stufe der Oberfläche aus dem angelaufenen Nahfeld: Varianz je Stützstelle, Tiefe, Gefälle. */
    static Oberflaeche.Stufe stufe(String name, Nahfeld n) {
        Oberflaeche.Stufe s = new Oberflaeche.Stufe(name, n.nx, n.ny, n.dx, n.x0, n.y0, n.nb, n.nd);
        for (int c = 0; c < n.nc; c++) {
            s.h[c] = n.tiefe(c);
            for (int b = 0; b < n.nb; b++)
                for (int q = 0; q < n.nd; q++) {
                    double e = n.energie(c, b, q);
                    s.e[n.ix(c, b, q)] = e > 0 && Double.isFinite(e) ? e : 0;
                }
        }
        s.gefaelleAusTiefe();
        return s;
    }

    /** Seit S5b-6b: nur die Tiefe des Gitters (vor dem Anlauf), für die Wände. */
    static Oberflaeche.Stufe geometrie(String name, Nahfeld n) {
        Oberflaeche.Stufe s = new Oberflaeche.Stufe(name, n.nx, n.ny, n.dx, n.x0, n.y0, 1, 1);
        for (int c = 0; c < n.nc; c++) s.h[c] = n.tiefe(c);
        return s;
    }

    /** Die Werte des feinsten Netzes, das den Ort hat, am Rand gemischt mit dem gröberen (seit S3c-6, SF31 A; Seegangsnetz.wertGemischt); null ohne. */
    static double[] wert(List<Seegangsnetz> see, double x, double y, double t) {
        return Seegangsnetz.wertGemischt(see, x, y, t);
    }

    @Override public void close() {
        if (bild != null) bild.close();                                    // seit S7d4: die Körperfläche hat keins
        karte.close();
    }
}
