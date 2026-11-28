package com.dan.lauf.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Zeichner;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Phasenfeld;
import com.dan.core.szene.Profilpuffer;
import com.dan.core.szene.Spiegel;
import com.dan.core.szene.Seegangsnetz;
import com.dan.render.seegang.SeegangKarte;
import com.dan.render.seegang.Seegangbild;
import com.dan.render.water.Wasserbild;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Netzes des Wassers (Seegang Werkbuch, S3c): {@code render.water} zeichnet die Clipmap aus
 * {@code render.seegang} ({@link Seegangbild}) als Netz, in einer Szene mit bekannten Werten – ein Spiegel 0,5 m NHN
 * über 8 km mit einem trockenen Viereck, das Gelände eine Ebene 20 m unter NHN, der Himmel ein Ersatz, der Seegang
 * aus Westsüdwest (Hs 1,4 m, Tp 6,5 s) über einer Rampe wie in {@code SeegangKarteCheck}.
 * <ol>
 * <li><b>N1:</b> die Scheitel des Netzes gegen Java aus denselben Daten (Verschiebung, Übergang zur gröberen Stufe, η_Bild).</li>
 * <li><b>N2:</b> keine Lücken: in drei Ansichten kein Bildpunkt im Bereich des Netzes, an dem der Strahl Wasser treffen
 * müsste und stattdessen Himmel oder Grund zeigt.</li>
 * <li><b>N3:</b> ohne Wellen ist die Wasserlinie dieselbe wie auf dem Weg ohne Netz.</li>
 * <li><b>N3b</b> (seit S3c-5, B47): dasselbe mit einem Teich auf +1,5 m NHN hinter einem trockenen Ring mit Bauwerken
 * (Spiegel ungleich Pegel): der Fuß des Netzes folgt η_Bild am Bildpunkt.</li>
 * <li><b>N6b</b> (seit S3c-5, B48): der Abbruch über die Tiefe des Geländes ändert nichts am Bild.</li>
 * <li><b>N6c</b> (seit S3c-6): der Vorlauf der Tiefe und die Zähler ändern nichts am Bild (mit Wellen und Wand, am
 * Teich mit B47); dazu die Zahl der Aufrufe je Bildpunkt mit und ohne Vorlauf.</li>
 * <li><b>N6d</b> (seit S3c-8, SF34 A): die Maske des Netzes für den alten Weg ändert nichts am Bild, und der alte Weg
 * sucht in keinem Bildpunkt des Netzes mehr.</li>
 * <li><b>N9</b> (seit S3c-6, SF31 A; seit S3c-7 SF33 A mit dem Übergang außen): am Rand eines feineren Netzes des
 * Seegangs gehen die Werte stetig in die des gröberen über, auf der Karte wie auf dem Prozessor; je Art des Übergangs.</li>
 * </ol>
 * Dazu ein Bild je Ansicht in {@code docs/status/seegang/S3c_Netz_<n>.png}. Attest {@code docs/status/S3c_Netz.md}.
 */
public final class SeegangNetzCheck {
    static final Attest A = new Attest("render.water mit render.seegang (Netz, S3c)", Wasserbild.VERSION);
    static final int NB = 8, ND = 32, STUFEN = 8, N = 128, B = 480, H = 270;
    static final double FMIN = 0.06, FMAX = 0.6, D0 = 0.25, HS = 1.4, TP = 6.5, WOHIN = 20, OST = 466000, NORD = 6046000, AUGE = 12, GRUND = -20, PEGEL = 0.5;
    static final double L_HIMMEL = 4000;
    static final double[] OM = new double[NB], UNTEN = new double[NB], OBEN = new double[NB];

    static {
        double st = Math.pow(FMAX / FMIN, 1.0 / NB);
        for (int b = 0; b < NB; b++) {
            double lo = FMIN * Math.pow(st, b), hi = lo * st;
            OM[b] = 2 * Math.PI * Math.sqrt(lo * hi); UNTEN[b] = 2 * Math.PI * lo; OBEN[b] = 2 * Math.PI * hi;
        }
    }

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        System.out.println("SeegangNetzCheck – S3c (Seegang Werkbuch)");
        Geraet g = null;
        try {
            g = new Geraet();
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            pruefen(g);
        } catch (Throwable e) {
            A.fehler(e);
        } finally {
            if (g != null) g.close();
        }
        A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", "S3c_Netz.md"), "S3c – das Netz des Wassers aus der Clipmap des Seegangs");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
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

    static double tiefe(double x, double y) { return Math.max(0.0, 20 - 0.0045 * x + 1.5 * Math.sin(y / 400)); }

    static Oberflaeche.Stufe stufe(String name, int nx, int ny, double dx, double x0, double y0, double[] e, double faktor) {
        Oberflaeche.Stufe s = new Oberflaeche.Stufe(name, nx, ny, dx, x0, y0, NB, ND);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                double x = x0 + (i + 0.5) * dx - 463500, y = y0 + (j + 0.5) * dx - 6043500;
                s.h[c] = tiefe(x, y);
                double w = s.h[c] > 0.05 ? Math.min(1, s.h[c] / 8) : 0;
                for (int k = 0; k < NB * ND; k++) s.e[c * NB * ND + k] = e[k] * w * faktor;
            }
        s.gefaelleAusTiefe();
        return s;
    }

    /** Die Szene mit Seegang und dieselbe ohne (Energie 0, dieselben Phasenfelder). */
    static Oberflaeche[] szenen(Profilpuffer p) {
        double[] e = jonswap(HS, TP, WOHIN, 10);
        double ux = 466000, uy = 6046000;
        Oberflaeche.Stufe g = stufe("grob", 100, 100, 50, 463500, 6043500, e, 1);
        Phasenfeld pg = Phasenfeld.rechne(g.nx, g.ny, g.dx, g.x0, g.y0, g.h, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, ux, uy, null, 0, g.e);
        g.phasen(pg, pg);
        Oberflaeche.Stufe fz = stufe("fein", 100, 100, 10, 465500, 6045500, e, 1);
        Phasenfeld pf = Phasenfeld.rechne(fz.nx, fz.ny, fz.dx, fz.x0, fz.y0, fz.h, null, null, OM, Phasenfeld.richtungen(NB, ND), ND, ux, uy, pg, 0, fz.e);
        fz.phasen(pf, pf);
        Oberflaeche o = new Oberflaeche(p, ND);
        o.grob = g; o.fein = fz;
        o.fernfeld = (x, y) -> new double[] {HS, TP, Math.toRadians(WOHIN)};
        o.vorbereiten();
        Oberflaeche.Stufe g0 = stufe("grob", 100, 100, 50, 463500, 6043500, e, 0), f0 = stufe("fein", 100, 100, 10, 465500, 6045500, e, 0);
        g0.phasen(pg, pg);
        f0.phasen(pf, pf);
        Oberflaeche o0 = new Oberflaeche(p, ND);
        o0.grob = g0; o0.fein = f0;
        o0.fernfeld = (x, y) -> new double[] {0, TP, Math.toRadians(WOHIN)};
        o0.vorbereiten();
        return new Oberflaeche[] {o, o0};
    }

    // ------------------------------------------------------------------ Ersatz für Himmel und Gelände

    static final class Ersatz implements AutoCloseable {
        final Bild durch, ansicht;
        final Puffer werte;

        Ersatz(Geraet g, Konto k) {
            durch = new Bild(g, k, "Ersatz Durchlässigkeit", 256, 64, Bild.RGBA32F);
            float[] eins = new float[256 * 64 * 4];
            Arrays.fill(eins, 1f);
            durch.schreibe(eins);
            ansicht = new Bild(g, k, "Ersatz Ansicht", 256, 128, Bild.RGBA32F);
            float[] a = new float[256 * 128 * 4];
            for (int i = 0; i < 256 * 128; i++) {
                double hoehe = (i / 256) / 127.0;                              // etwas Verlauf, damit die Spiegelung Zeichnung hat
                a[4 * i] = (float) (L_HIMMEL * (0.6 + 0.4 * hoehe)); a[4 * i + 1] = (float) (L_HIMMEL * (0.7 + 0.3 * hoehe)); a[4 * i + 2] = (float) L_HIMMEL;
                a[4 * i + 3] = 1;
            }
            ansicht.schreibe(a);
            werte = Puffer.speicher(g, k, "Ersatz Werte", 60 * 4);
            float[] w = new float[60];
            double h = Math.toRadians(25), az = Math.toRadians(200);
            w[0] = 1e-5f; w[1] = 1e-5f; w[2] = 0.08f; w[3] = 0.85f;
            w[4] = (float) (Math.cos(h) * Math.sin(az)); w[5] = (float) Math.sin(h); w[6] = (float) (-Math.cos(h) * Math.cos(az)); w[7] = (float) AUGE;
            w[8] = w[9] = w[10] = 80000; w[11] = 1;
            w[12] = 0; w[13] = -1; w[14] = 0;
            w[52] = w[53] = w[54] = w[56] = w[57] = w[58] = 1;
            werte.schreibe(w);
        }

        void ablegen(Bildziel z) {
            z.geteilt.put("Himmel/Durchlaessigkeit", durch);
            z.geteilt.put("Himmel/Ansicht", ansicht);
            z.puffer.put("Himmel/Werte", werte);
        }

        @Override public void close() { durch.close(); ansicht.close(); werte.close(); }
    }

    // ------------------------------------------------------------------ Prüfung

    static Wasserbild w;
    static Seegangbild sb;
    static Ersatz ers;
    static Zeichner boden;
    static Abtaster at;
    static Bildziel z;
    static Gitter gp;

    /** Seit S9b: float aus der geordneten uint von tiefe_grob.comp. */
    static float ordnung(int u) { return Float.intBitsToFloat((u & 0x80000000) != 0 ? (u & 0x7fffffff) : ~u); }

    static void pruefen(Geraet g) throws Exception {
        Konto konto = g.konto("netz", 3L << 30);
        Profilpuffer pp = new Profilpuffer(OM, UNTEN, OBEN, ND, 0x5EE6A3L, false);
        Oberflaeche[] sz = szenen(pp);
        Bild hdr = new Bild(g, konto, "hdr", B, H, Bild.RGBA16F), tf = new Bild(g, konto, "tiefe", B, H, Bild.D32F);
        z = new Bildziel(hdr, tf);
        ers = new Ersatz(g, konto);
        w = new Wasserbild(g, konto);
        w.spiegelHalb = false;                                                // seit S9c-1: die Prüfungen hier am vollen Weg (Abdruck)
        at = new Abtaster(g, false, false);
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {Bild.RGBA16F};
        boden = Zeichner.lade(g, "/com/dan/render/water/wasser.vert", "/com/dan/lauf/demo/boden.frag", 16, null, art,
                Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR);
        // Spiegel 0,5 m NHN über 8 km, trocken ein Viereck 200 m × 200 m rund 600 m westlich des Auges
        gp = Gitter.utm("Probe", 32, OST - 4_000, NORD - 4_000, 20, 400, 400);
        Spiegel sp = new Spiegel(gp);
        float[] eta = new float[gp.zellen()];
        boolean[] nass = new boolean[gp.zellen()];
        for (int c = 0; c < eta.length; c++) {
            int i = c % gp.nx, j = c / gp.nx;
            double x = gp.x0 + (i + 0.5) * gp.dx - OST, y = gp.y0 + (j + 0.5) * gp.dx - NORD;
            boolean tr = x > -700 && x < -500 && y > -100 && y < 100;
            nass[c] = !tr;
            eta[c] = tr ? (float) GRUND : (float) PEGEL;
        }
        sp.stand(0, eta, nass);
        w.spiegel(sp);
        SeegangKarte karte = new SeegangKarte(g, konto, sz[0], STUFEN, N, D0, false), still = new SeegangKarte(g, konto, sz[1], STUFEN, N, D0, false);
        try {
            karte.eingaben();
            still.eingaben();
            sb = new Seegangbild(g, konto, karte);
            Blick[] blicke = {
                    new Blick(OST, NORD, AUGE, 270, -3, 50).groesse(B, H),
                    new Blick(OST, NORD, AUGE, 200, -6, 50).groesse(B, H),
                    new Blick(OST - 300, NORD - 900, 300, 330, -30, 60).groesse(B, H)};
            A.teil("N1 Scheitel des Netzes gegen Java");
            scheitel(g, karte, blicke[0]);
            A.teil("N2 keine Lücken (drei Ansichten)");
            Files.createDirectories(Path.of("docs", "status", "seegang"));
            for (int k = 0; k < blicke.length; k++) luecken(g, blicke[k], k + 1);
            A.teil("N6b Abbruch über die Tiefe des Geländes (B48)");
            abbruch(g, blicke[0]);
            A.teil("N6c Vorlauf der Tiefe und Zähler (S3c-6), mit Wellen");
            for (int k = 0; k < blicke.length; k++) vorlaufPruefen(g, blicke[k], "mit Wellen, Ansicht " + (k + 1) + (k == 0 ? " mit Wand" : ""), k == 0 ? -300 : 0);
            A.teil("N3 ohne Wellen: Wasserlinie wie ohne Netz");
            sb.close();
            sb = new Seegangbild(g, konto, still);
            for (int k = 0; k < blicke.length; k++) ohneWellen(g, blicke[k], k + 1);
            A.teil("N3b ohne Wellen, Teich hinter einem Deich: Spiegel ungleich Pegel (B47)");
            w.spiegel(teich());
            teichAn = true;
            Blick[] zumTeich = {
                    new Blick(OST, NORD, AUGE, 270, -3, 50).groesse(B, H),
                    new Blick(OST - 800, NORD + 450, 40, 225, -12, 50).groesse(B, H),
                    new Blick(OST - 900, NORD + 350, 150, 225, -30, 50).groesse(B, H)};
            for (int k = 0; k < zumTeich.length; k++) teichPruefen(g, zumTeich[k], 10 + k + 1);
            luecken(g, zumTeich[1], 12);
            A.teil("N6c Vorlauf der Tiefe und Zähler (S3c-6), am Teich (B47)");
            for (int k = 0; k < zumTeich.length; k++) vorlaufPruefen(g, zumTeich[k], "Teich, Ansicht " + (k + 1), 0);
            A.teil("N9 Übergang am Rand eines feineren Netzes des Seegangs (SF31 A, SF33 A)");
            uebergang(g, konto, blicke[0]);
        } finally {
            if (sb != null) sb.close();
            karte.close();
            still.close();
            boden.close();
            at.close();
            w.close();
            ers.close();
            hdr.close();
            tf.close();
        }
    }

    /** Teich: Ost −1 300 … −1 000 m, Nord −150 … +150 m vom Auge, Spiegel +1,5 m NHN [G]. */
    static final double TEICH_W = -1300, TEICH_O = -1000, TEICH_S = -150, TEICH_N = 150, TEICH = 1.5;

    static boolean imTeich(double ost, double nord, double rand) {
        double x = ost - OST, y = nord - NORD;
        return x > TEICH_W + rand && x < TEICH_O - rand && y > TEICH_S + rand && y < TEICH_N - rand;
    }

    /**
     * N3b (B47): der Spiegel von N3 mit einem Teich auf {@link #TEICH} m NHN; um den Teich ein trockener Ring von einer Zelle,
     * dessen Kanten Bauwerke tragen (kein Saum, kein Weg zwischen Teich und Watt). Der Pegel des Kopfs bleibt 0,5 m.
     */
    static Spiegel teich() {
        Spiegel sp = new Spiegel(gp);
        float[] eta = new float[gp.zellen()];
        boolean[] nass = new boolean[gp.zellen()];
        boolean[] ring = new boolean[gp.zellen()];
        for (int c = 0; c < eta.length; c++) {
            int i = c % gp.nx, j = c / gp.nx;
            double ost = gp.x0 + (i + 0.5) * gp.dx, nord = gp.y0 + (j + 0.5) * gp.dx;
            boolean innen = imTeich(ost, nord, 0), aussenRing = imTeich(ost, nord, -gp.dx);
            boolean tr = trocken(ost, nord, 0);
            ring[c] = aussenRing && !innen;
            nass[c] = !tr && !ring[c];
            eta[c] = innen ? (float) TEICH : tr || ring[c] ? (float) GRUND : (float) PEGEL;
        }
        int n = 0;
        for (int c = 0; c < eta.length; c++) {
            if (!ring[c]) continue;
            int i = c % gp.nx, j = c / gp.nx;
            sp.art[c] |= Spiegel.WEST | Spiegel.SUED;
            if (i + 1 < gp.nx) sp.art[c + 1] |= Spiegel.WEST;
            if (j + 1 < gp.ny) sp.art[c + gp.nx] |= Spiegel.SUED;
            n += 4;
        }
        sp.bauwerke = n;
        sp.stand(0, eta, nass);
        return sp;
    }

    /** N3b: der Teich ist gesetzt (N2 lässt Teich und Ring aus). */
    static boolean teichAn;

    /**
     * N6c: dasselbe Bild ohne Vorlauf und ohne Zähler, ohne Vorlauf mit Zählern, mit Vorlauf und Zählern; Farbe und Tiefe
     * bitgleich. Die Zähler: Aufrufe von netz.frag bis zur Optik je Bildpunkt des Netzes (ohne Vorlauf die Überzeichnung,
     * mit Vorlauf genau 1).
     */
    static void vorlaufPruefen(Geraet g, Blick b, String wo, double wandOst) {
        wand = wandOst;
        try {
            w.vorlauf = false;
            w.zaehlen = false;
            // ein Bild zum Einschwingen: die fernen Stufen der Clipmap rechnet Seegangbild jedes zweite Bild; das erste Bild
            // nach einem Wechsel des Auges kann sie noch vom alten haben (Tiefe in den letzten Stellen anders)
            bild(g, b, true, 1234.5);
            bild(g, b, true, 1234.5);
            float[] h0 = z.hdr.lies(), t0 = z.tiefe.lies();
            // seit S9a (Seegang Werkbuch 106, Prüfstein 43): ein Abdruck von Farbe und Tiefe, zum Vergleich über zwei Stände
            // der Shader (Messschalter 0 gegen den Stand vor S9a)
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            for (float x : h0) { int v = Float.floatToRawIntBits(x); crc.update(v); crc.update(v >>> 8); crc.update(v >>> 16); crc.update(v >>> 24); }
            for (float x : t0) { int v = Float.floatToRawIntBits(x); crc.update(v); crc.update(v >>> 8); crc.update(v >>> 16); crc.update(v >>> 24); }
            A.info("Abdruck " + wo, f("%08x (%d Werte)", crc.getValue(), h0.length + t0.length));
            System.out.printf("ABDRUCK %s %08x%n", wo, crc.getValue());
            for (int bit = 1; bit <= 512 && "an".equals(System.getProperty("netz.messbits")); bit <<= 1) {   // jedes Bit einmal: läuft es?
                w.mess = bit;
                bild(g, b, true, 1234.5);
                float[] hb = z.hdr.lies();
                long an = 0;
                for (int i = 0; i < hb.length; i++) if (Float.floatToRawIntBits(hb[i]) != Float.floatToRawIntBits(h0[i])) an++;
                System.out.printf("MESSBIT %s %d: %d Werte anders%n", wo, bit, an);
                w.mess = 0;
            }
            // seit S9b (Seegang Werkbuch 107, Prüfstein 45/46): die alten Wege der Spiegelung (ohne grobe Tiefe) und der Kräusel
            // (ohne Abbruch) gegen die neuen; dieselben Treffer und dieselbe Summe geben dasselbe Bild
            int[] gk = w.grobKopf();
            if (gk != null) System.out.printf("S9B %s grobe Tiefe: Kachel %d, %d × %d Kacheln, gesetzt %d, H %.2f m, G %.4f%n", wo, gk[0], gk[1], gk[2], gk[3], ordnung(gk[4]), ordnung(gk[5]));
            {   // seit S9c-1 (Seegang Werkbuch 108, SF175 A): die Spiegelung in halber Auflösung gegen voll
                w.spiegelHalb = true;
                bild(g, b, true, 1234.5);
                float[] hb = z.hdr.lies();
                w.spiegelHalb = false;
                long an = 0, ueber = 0, n = hb.length / 4;
                double summe = 0;
                for (int i = 0; i < hb.length; i += 4) {
                    boolean anders = false;
                    double ya = 0.2126 * h0[i] + 0.7152 * h0[i + 1] + 0.0722 * h0[i + 2], yb = 0.2126 * hb[i] + 0.7152 * hb[i + 1] + 0.0722 * hb[i + 2];
                    for (int k = 0; k < 3; k++) if (Float.floatToRawIntBits(hb[i + k]) != Float.floatToRawIntBits(h0[i + k])) anders = true;
                    if (anders) an++;
                    double rel = Math.abs(yb - ya) / Math.max(1e-6, ya);
                    summe += rel;
                    if (rel > 0.02) ueber++;
                }
                if (System.getProperty("netz.bilder") != null) try {
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(8 * hb.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    for (float x : h0) bb.putFloat(x);
                    for (float x : hb) bb.putFloat(x);
                    java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("netz.bilder"), wo.replaceAll("[^A-Za-z0-9]", "_") + ".f32"), bb.array());
                } catch (java.io.IOException e) { throw new RuntimeException(e); }
                System.out.printf("S9C %s Spiegelung halb gegen voll: %d Bildpunkte anders, über 2 %% Leuchtdichte %d (%.3f %%), im Mittel %.4f relativ%n", wo, an, ueber, 100.0 * ueber / n, summe / n);
                A.info("S9c-1: Spiegelung halb gegen voll (" + wo + ")", f("%d Bildpunkte anders; über 2 %% Leuchtdichte %d (%.3f %%); im Mittel %.4f relativ", an, ueber, 100.0 * ueber / n, summe / n));
            }
            for (int alt = 0; alt < 2; alt++) {
                if (alt == 0) w.spiegelGrob = false; else w.kraeuselAbbruch = false;
                bild(g, b, true, 1234.5);
                float[] hb = z.hdr.lies();
                long an = 0;
                double groesst = 0;
                for (int i = 0; i < hb.length; i++)
                    if (Float.floatToRawIntBits(hb[i]) != Float.floatToRawIntBits(h0[i])) { an++; groesst = Math.max(groesst, Math.abs(hb[i] - h0[i]) / Math.max(1e-6, Math.abs(h0[i]))); }
                System.out.printf("S9B %s %s: %d Werte anders (größte Abweichung %.2e relativ)%n", wo, alt == 0 ? "Spiegelung alt" : "Kräusel alt", an, groesst);
                A.pruefe(an == 0, "S9b: " + (alt == 0 ? "Spiegelung mit grober Tiefe gegen den alten Weg" : "Kräusel mit Abbruch gegen weiterzählen") + " (" + wo + ")",
                        f("%d Werte anders", an), "0 (bitgleich)");
                w.spiegelGrob = true;
                w.kraeuselAbbruch = true;
            }
            w.zaehlen = true;
            bild(g, b, true, 1234.5);
            float[] h1 = z.hdr.lies(), t1 = z.tiefe.lies();
            long[] ohne = w.zaehlung();
            w.vorlauf = true;
            bild(g, b, true, 1234.5);
            float[] h2 = z.hdr.lies(), t2 = z.tiefe.lies();
            long[] mit = w.zaehlung();
            w.vorlauf = false;
            w.maske = false;                                                    // seit S3c-8 (N6d): der alte Weg ohne Maske
            bild(g, b, true, 1234.5);
            float[] h3 = z.hdr.lies(), t3 = z.tiefe.lies();
            long[] ohneMaske = w.zaehlung();
            long anders3 = 0;
            for (int i = 0; i < t0.length; i++) {
                boolean a3 = Float.floatToRawIntBits(t0[i]) != Float.floatToRawIntBits(t3[i]);
                for (int c = 0; c < 4; c++) a3 |= Float.floatToRawIntBits(h0[4 * i + c]) != Float.floatToRawIntBits(h3[4 * i + c]);
                if (a3) anders3++;
            }
            A.pruefe(anders3 == 0, "N6d: die Maske des Netzes für den alten Weg ändert das Bild nicht (" + wo + ")", f("%d Bildpunkte anders", anders3), "0 (bitgleich)");
            // seit S3d (B57, Werkbuch 27): höchstens 0,1 % der Bildpunkte des Netzes [G]; an der Grenze des inneren Bereichs
            // können die Treffer des Netzes und des alten Wegs auf verschiedenen Seiten liegen (680M: 1 von 107 382)
            A.pruefe(ohne[9] <= 0.001 * ohne[0], "N6d: mit Maske sucht der alte Weg fast in keinem Bildpunkt des Netzes mehr (" + wo + ")",
                    f("im Netz nach der Suche verworfen: mit Maske %d, ohne %d (Netz an %d Bildpunkten)", ohne[9], ohneMaske[9], ohne[0]), "≤ 0,1 % der Bildpunkte des Netzes");
            long anders1 = 0, anders2 = 0;
            for (int i = 0; i < t0.length; i++) {
                boolean a1 = Float.floatToRawIntBits(t0[i]) != Float.floatToRawIntBits(t1[i]), a2 = Float.floatToRawIntBits(t0[i]) != Float.floatToRawIntBits(t2[i]);
                for (int c = 0; c < 4; c++) {
                    a1 |= Float.floatToRawIntBits(h0[4 * i + c]) != Float.floatToRawIntBits(h1[4 * i + c]);
                    a2 |= Float.floatToRawIntBits(h0[4 * i + c]) != Float.floatToRawIntBits(h2[4 * i + c]);
                }
                if (a1) anders1++;
                if (a2) anders2++;
            }
            A.info("N6c " + wo, f("ohne Vorlauf: %d Bildpunkte mit Optik, %.3f Aufrufe bis zur Optik je Bildpunkt (am meisten %d, mehr als einer an %d), "
                    + "%.3f Aufrufe von netz.frag, %.3f mit Fuß; mit Vorlauf: %.3f Aufrufe im Vorlauf, %.3f in netz.frag, %.3f bis zur Optik",
                    ohne[0], (double) ohne[1] / Math.max(1, ohne[0]), ohne[2], ohne[3], (double) ohne[4] / Math.max(1, ohne[0]), (double) ohne[5] / Math.max(1, ohne[0]),
                    (double) mit[6] / Math.max(1, ohne[0]), (double) mit[4] / Math.max(1, ohne[0]), (double) mit[1] / Math.max(1, ohne[0]))
                    + f("; B47 an %d Bildpunkten; alter Weg: %d Aufrufe, im Netz nach der Suche verworfen %d, bis zur Optik %d", ohne[8], ohne[10], ohne[9], ohne[11]));
            A.pruefe(anders1 == 0 && anders2 == 0, "N6c: Zähler und Vorlauf der Tiefe ändern das Bild nicht (" + wo + ")",
                    f("mit Zählern %d, mit Vorlauf %d Bildpunkte anders", anders1, anders2), "0 und 0 (bitgleich)");
            A.pruefe(mit[1] == mit[0] && mit[2] <= 1 && mit[0] == ohne[0], "N6c: mit Vorlauf je Bildpunkt des Netzes genau ein Aufruf bis zur Optik (" + wo + ")",
                    f("%d Aufrufe an %d Bildpunkten, am meisten %d; ohne Vorlauf %d Bildpunkte", mit[1], mit[0], mit[2], ohne[0]), "gleich viele, höchstens 1");
        } finally {
            wand = 0;
            w.vorlauf = false;
            w.zaehlen = false;
            w.maske = true;
        }
    }

    /** Ein Netz des Seegangs mit überall gleichen Werten (für N9). */
    static Seegangsnetz netz(String name, double d, double x0, double y0, int nx, int ny, float hs, float tp, float dir, float mss, float diss) {
        Seegangsnetz n = new Seegangsnetz(Gitter.utm(name, 32, x0, y0, d, nx, ny));
        int c = n.zellen;
        float[][] v = {new float[c], new float[c], new float[c], new float[c], new float[c]};
        java.util.Arrays.fill(v[0], hs);
        java.util.Arrays.fill(v[1], tp);
        java.util.Arrays.fill(v[2], dir);
        java.util.Arrays.fill(v[3], mss);
        java.util.Arrays.fill(v[4], diss);
        n.stand(0, v[0], v[1], v[2], v[3], v[4]);
        return n;
    }

    /**
     * N9 (SF31 A, seit S3c-7 SF33 A): ein grobes Netz (500 m, Hs 2 m) und darin ein feines (100 m, Hs 1 m, Westrand
     * 1 500 m westlich des Auges). Auf einer Linie von 2 000 bis 500 m westlich des Auges alle 10 m die Werte auf der
     * Karte (seegang.glsl über Seegangsprobe) und auf dem Prozessor (Seegangsoptik.werte), je Art des Übergangs: gleich;
     * außen (Vorgabe) und innen stetig (größter Schritt höchstens 5 % des Unterschieds der Netze [G]); außen im feinen Netz
     * genau das feine, 500 m außerhalb das grobe; innen am Rand das grobe, 500 m innen das feine; aus mit dem Sprung.
     */
    static void uebergang(Geraet g, Konto konto, Blick b) {
        w.seegang(netz("Probe G2w", 500, OST - 6000, NORD - 6000, 24, 24, 2.0f, 8f, 250f, 0.02f, 3f));
        w.seegang(netz("Probe G3w", 100, OST - 1500, NORD - 1000, 30, 20, 1.0f, 6f, 300f, 0.01f, 1f));
        w.seegang = true;
        int vorher = Seegangsnetz.RAND_ART;
        try {
            for (int art : new int[] {Seegangsnetz.RAND_AUSSEN, Seegangsnetz.RAND_INNEN, Seegangsnetz.RAND_AUS}) {
                Seegangsnetz.RAND_ART = art;
                String wie = art == Seegangsnetz.RAND_AUSSEN ? "außen" : art == Seegangsnetz.RAND_INNEN ? "innen" : "aus";
                bild(g, b, false, 1234.5);
                int n = 151;
                float[] orte = new float[2 * n];
                for (int i = 0; i < n; i++) { orte[2 * i] = -2000f + 10f * i; orte[2 * i + 1] = 0f; }
                com.dan.render.water.Seegangsprobe.Ergebnis e = com.dan.render.water.Seegangsprobe.rechne(g, konto, w, OST, NORD, orte, 0.5f);
                double gr = 0, schritt = 0, imFeinen = 0;
                for (int i = 0; i < n; i++) {
                    for (int f = 0; f < 6; f++) {
                        double kv = e.karte()[9 * i + f], pv = e.prozessor()[9 * i + f];
                        gr = Math.max(gr, Math.abs(kv - pv) / Math.max(1e-3, Math.abs(pv)));
                    }
                    if (i > 0) schritt = Math.max(schritt, Math.abs(e.karte()[9 * i] - e.karte()[9 * (i - 1)]));
                    if (orte[2 * i] >= -1500f) imFeinen = Math.max(imFeinen, Math.abs(e.karte()[9 * i] - 1.0));
                }
                double aussen = e.karte()[0], rand = e.karte()[9 * 50], innen = e.karte()[9 * 150];
                A.hoechstens("N9 " + wie + ": Werte des Seegangs auf der Karte gegen den Prozessor, 151 Orte über den Rand, relativ", gr, 1e-4, "");
                if (art == Seegangsnetz.RAND_AUS) {
                    A.pruefe(Math.abs(schritt - 1.0) < 1e-3, "N9 aus: am Rand der Sprung wie bis S3c-5", f("größter Schritt %.4f m", schritt), "1 m");
                    continue;
                }
                A.hoechstens(f("N9 %s: größter Schritt von Hs zwischen Orten im Abstand von 10 m, Anteil am Unterschied der Netze (500 m außerhalb %.3f m, am Rand %.3f m, 1 000 m innen %.3f m)",
                        wie, aussen, rand, innen), schritt / 1.0, 0.05, "");
                if (art == Seegangsnetz.RAND_AUSSEN)
                    A.pruefe(Math.abs(aussen - 2.0) < 1e-4 && imFeinen < 1e-4, "N9 außen: im feinen Netz genau das feine, 500 m außerhalb das grobe",
                            f("im feinen höchstens %.1e m daneben; außen %.4f m", imFeinen, aussen), "0 und 2 m");
                else
                    A.pruefe(Math.abs(rand - 2.0) < 1e-3 && Math.abs(innen - 1.0) < 1e-4, "N9 innen: am Rand das grobe, 1 000 m innen das feine",
                            f("%.4f und %.4f m", rand, innen), "2 und 1 m");
            }
        } finally {
            Seegangsnetz.RAND_ART = vorher;
        }
    }

    /** Lage einer Wand im Ersatzgelände (Ost relativ zum Auge; 0 keine), für N6b. */
    static double wand;

    /**
     * N6b (seit S3c-5, B48): mit Wellen und einer Wand bis 5 m NHN vor dem Wasser: das Netz mit und ohne Abbruch über die
     * Tiefe des Geländes gibt dasselbe Bild und dieselbe Tiefe (bitgleich).
     */
    static void abbruch(Geraet g, Blick b) {
        wand = -300;
        try {
            w.abbruch = false;
            bild(g, b, true, 1234.5);
            float[] h0 = z.hdr.lies(), t0 = z.tiefe.lies();
            w.abbruch = true;
            bild(g, b, true, 1234.5);
            float[] h1 = z.hdr.lies(), t1 = z.tiefe.lies();
            long anders = 0, wandPx = 0;
            double[] fv = b.basis()[2];
            for (int i = 0; i < t0.length; i++) {
                if (Float.floatToRawIntBits(t0[i]) != Float.floatToRawIntBits(t1[i])) anders++;
                for (int c = 0; c < 4; c++) if (Float.floatToRawIntBits(h0[4 * i + c]) != Float.floatToRawIntBits(h1[4 * i + c])) { anders++; break; }
                double[] sr = b.strahl(i % b.breite + 0.5, i / b.breite + 0.5);
                if (sr[0] < 0 && t1[i] > 0 && Math.abs(b.nah / t1[i] / (sr[0] * fv[0] + sr[1] * fv[1] + sr[2] * fv[2]) - wand / sr[0]) < 1) wandPx++;
            }
            A.pruefe(anders == 0, f("N6b: Abbruch über die Tiefe an gegen aus, mit Wellen und einer Wand (%d Bildpunkte zeigen die Wand)", wandPx),
                    f("%d Bildpunkte anders", anders), "0 (bitgleich)");
        } finally {
            wand = 0;
            w.abbruch = true;
        }
    }

    /** Ein Bild: löschen, Seegang (wenn an), Wasser vorbereiten, Ersatzgelände, Wasser zeichnen. */
    static void bild(Geraet g, Blick b, boolean netz, double zeit) {
        z.blick = b;
        z.puffer.clear();
        z.geteilt.clear();
        z.werte.clear();
        ers.ablegen(z);
        w.netz = netz;
        sb.an = netz;
        sb.zeit = zeit;
        w.zeit = 0;
        Ablauf a = new Ablauf(g, g.grafik);
        a.zeichne("löschen", new Bild[] {z.hdr}, new float[][] {{0, 0, 0, 0}}, z.tiefe, 0f, null, c -> { });
        sb.vorbereiten(a, z);
        w.vorbereiten(a, z);
        ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        p.putFloat(0, (float) GRUND).putFloat(4, 0f).putFloat(8, 300f).putFloat(12, (float) wand);
        long satz = boden.satz(w.kopf(), w.staende(), w.arten(), ers.werte, at.an(ers.durch), at.an(ers.ansicht));
        a.zeichne("Ersatzgelände", new Bild[] {z.hdr}, null, z.tiefe, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, w.kopf(), w.staende(), w.arten(), ers.werte), Ablauf.textur(Ablauf.ZEICHNEN, ers.durch, ers.ansicht)),
                c -> boden.zeichne(c, satz, p, 3, 1));
        w.zeichnen(a, z);
        g.grafik.einmal(c -> a.aufnehmen(c, null));
    }

    /** N1: die Scheitel aus netz.comp gegen dieselbe Rechnung in Java (Verschiebung, Übergang, η_Bild in float). */
    static void scheitel(Geraet g, SeegangKarte karte, Blick b) {
        bild(g, b, true, 1234.5);
        Puffer sc = w.scheitel();
        A.pruefe(sc != null, "das Wasser zeichnet ein Netz, wenn der Seegang eine Clipmap ablegt", sc != null ? "ja" : "nein", "ja");
        if (sc == null) return;
        float[] s = sc.floats(8 * STUFEN * N * N);
        float[][] v = new float[STUFEN][];
        for (int l = 0; l < STUFEN; l++) v[l] = karte.verschiebung(l);
        double[][] lage = new double[STUFEN][];
        for (int l = 0; l < STUFEN; l++) {
            double[] m = karte.mitte(l);
            double dl = karte.abstand(l);
            lage[l] = new double[] {(float) (m[0] - N / 2.0 * dl - b.ost), (float) (-(m[1] - N / 2.0 * dl - b.nord)), (float) dl};
        }
        Random r = new Random(5);
        double max = 0, maxEta = 0;
        int morph = 0;
        // 4 000 Stellen zufällig, dazu alle Stellen der Stufen 5–7 am trockenen Viereck (B47: trockene Scheitel)
        java.util.List<int[]> stellen = new java.util.ArrayList<>();
        for (int k = 0; k < 4000; k++) stellen.add(new int[] {r.nextInt(STUFEN), r.nextInt(N), r.nextInt(N)});
        for (int l = 5; l < STUFEN; l++)
            for (int j = 0; j < N; j++)
                for (int i = 0; i < N; i++) {
                    double ost = b.ost + lage[l][0] + i * lage[l][2], nord = b.nord - (lage[l][1] - j * lage[l][2]);
                    if (trocken(ost, nord, 2 * lage[l][2])) stellen.add(new int[] {l, i, j});
                }
        int trockene = 0;
        for (int[] st : stellen) {
            int l = st[0], i = st[1], j = st[2];
            float x0 = (float) lage[l][0] + (float) i * (float) lage[l][2], z0 = (float) lage[l][1] - (float) j * (float) lage[l][2];
            int id = 4 * (j * N + i);
            double wx = v[l][id], wy = v[l][id + 1], wz = v[l][id + 2];
            if (Float.isNaN(w.etaBild(x0, z0, null))) trockene++;
            double e = w.etaScheitel(x0, z0, (float) lage[l][2]);
            if (l < STUFEN - 1) {
                int m = N - 2;
                double rnd = Math.min(Math.min(i, j), Math.min(m - i, m - j)), a = Math.max(0, Math.min(1, (12 - rnd) / 12));
                if (a > 0) {
                    morph++;
                    double[] lc = lage[l + 1];
                    double ux = (x0 - lc[0]) / lc[2], uy = (lc[1] - z0) / lc[2];
                    int cx = (int) Math.floor(ux + 1e-3), cy = (int) Math.floor(uy + 1e-3);
                    double fx = Math.max(0, Math.min(1, ux - cx)), fy = Math.max(0, Math.min(1, uy - cy));
                    double cwx = 0, cwy = 0, cwz = 0, ce = 0;
                    for (int q = 0; q < 4; q++) {
                        int ci = Math.max(0, Math.min(N - 1, cx + (q & 1))), cj = Math.max(0, Math.min(N - 1, cy + (q >> 1)));
                        double gw = ((q & 1) == 0 ? 1 - fx : fx) * ((q >> 1) == 0 ? 1 - fy : fy);
                        if (gw <= 0) continue;
                        int cid = 4 * (cj * N + ci);
                        cwx += gw * v[l + 1][cid]; cwy += gw * v[l + 1][cid + 1]; cwz += gw * v[l + 1][cid + 2];
                        ce += gw * w.etaScheitel((float) lc[0] + (float) ci * (float) lc[2], (float) lc[1] - (float) cj * (float) lc[2], (float) lc[2]);
                    }
                    wx += a * (cwx - wx); wy += a * (cwy - wy); wz += a * (cwz - wz); e += a * (ce - e);
                }
            }
            double qx = x0 + wx, qz = z0 - wy, qy = e + wz - b.hoehe - (w.kruemmung > 0 ? 1 / (2 * w.kruemmung) : 0) * (qx * qx + qz * qz);
            int o = 8 * ((l * N + j) * N + i);
            max = Math.max(max, Math.max(Math.abs(s[o] - qx), Math.max(Math.abs(s[o + 1] - qy), Math.abs(s[o + 2] - qz))));
            maxEta = Math.max(maxEta, Math.abs(s[o + 3] - e));
        }
        A.hoechstens(f("N1: Scheitel (x, y, z) gegen Java, %d Stellen (%d im Übergang zur gröberen Stufe, %d trocken)", stellen.size(), morph, trockene), max, 2e-3, "m");
        A.hoechstens("N1: η_Bild der Scheitel gegen Java", maxEta, 1e-4, "m");
    }

    /** Ebene η (m NHN) mit Erdkrümmung: Weg längs des Strahls s (Einheit), −1 ohne Treffer (wie wasser.glsl treffen). */
    static double treffen(Blick b, double[] s, double eta) {
        double k = w.kruemmung > 0 ? 1 / (2 * w.kruemmung) : 0;
        double c = eta - b.hoehe, a = k * (s[0] * s[0] + s[2] * s[2]), bq = s[1], D = bq * bq + 4 * a * c;
        if (c >= 0 || D < 0) return -1;
        double nn = -bq + Math.sqrt(D);
        return nn <= 0 ? -1 : -2 * c / nn;
    }

    /** Innerer Bereich des Netzes in Ost, Nord (absolut): x von, x bis, y von, y bis, mit rand m Abstand. */
    static double[] bereich(double rand) {
        SeegangKarte k = sb.karte;
        double[] m = k.mitte(STUFEN - 1);
        double d = k.abstand(STUFEN - 1), x0 = m[0] - N / 2.0 * d, y0 = m[1] - N / 2.0 * d;
        return new double[] {x0 + d + rand, x0 + (N - 2) * d - rand, y0 + d + rand, y0 + (N - 2) * d - rand};
    }

    static boolean trocken(double ost, double nord, double rand) {
        double x = ost - OST, y = nord - NORD;
        return x > -700 - rand && x < -500 + rand && y > -100 - rand && y < 100 + rand;
    }

    /** N2: Bildpunkte im Bereich des Netzes, deren Strahl Wasser treffen müsste, die aber Himmel oder Grund zeigen. */
    static void luecken(Geraet g, Blick b, int nr) throws Exception {
        bild(g, b, true, 1234.5 + 7 * nr);
        float[] dd = z.tiefe.lies(), h = z.hdr.lies();
        double[] fv = b.basis()[2], ber = bereich(64);
        long loch = 0, wasser = 0, nan = 0;
        StringBuilder wo = new StringBuilder();
        for (int y = 0; y < b.hoehe_px; y++)
            for (int x = 0; x < b.breite; x++) {
                int i = y * b.breite + x;
                for (int c = 0; c < 4; c++) if (!Float.isFinite(h[4 * i + c])) { nan++; break; }
                double[] s = b.strahl(x + 0.5, y + 0.5);
                double tw = treffen(b, s, PEGEL), tgr = treffen(b, s, GRUND);
                if (tw <= 0 || tgr <= 0) continue;
                double ost = b.ost + s[0] * tw, nord = b.nord - s[2] * tw;
                if (ost < ber[0] || ost > ber[1] || nord < ber[2] || nord > ber[3] || trocken(ost, nord, 30) || teichAn && imTeich(ost, nord, -60)) continue;
                double cz = s[0] * fv[0] + s[1] * fv[1] + s[2] * fv[2], zw = tw * cz, zg = tgr * cz;
                double zp = dd[i] > 0 ? b.nah / dd[i] : Double.POSITIVE_INFINITY;
                wasser++;
                if (zp - zw > 0.5 * (zg - zw)) {
                    if (loch++ < 3) wo.append(f("(%d, %d) %.0f m vom Auge, %s; ", x, y, tw, dd[i] > 0 ? "Grund" : "Himmel"));
                }
            }
        A.pruefe(loch == 0 && nan == 0, "N2: Ansicht " + nr + " – Lücken im Netz (Himmel oder Grund statt Wasser); NaN",
                f("%d; %d Bildpunkte (Wasser im Bereich des Netzes auf %d)", loch, nan, wasser), "0; 0");
        if (loch > 0) A.info("N2: Ansicht " + nr + " – erste Lücken", wo.toString());
        speichere(h, b, Path.of("docs", "status", "seegang", "S3c_Netz_" + nr + ".png"));
    }

    /** N3: ohne Wellen gegen den Weg ohne Netz: Tiefe gleich, Wasser an denselben Bildpunkten. */
    static void ohneWellen(Geraet g, Blick b, int nr) {
        bild(g, b, true, 1234.5);
        float[] mit = z.tiefe.lies();
        bild(g, b, false, 1234.5);
        float[] ohne = z.tiefe.lies();
        double[] ber = bereich(64);
        long n = 0, anders = 0;
        double groesste = 0;
        for (int y = 0; y < b.hoehe_px; y++)
            for (int x = 0; x < b.breite; x++) {
                int i = y * b.breite + x;
                double[] s = b.strahl(x + 0.5, y + 0.5);
                double tw = treffen(b, s, PEGEL);
                if (tw <= 0) continue;
                double ost = b.ost + s[0] * tw, nord = b.nord - s[2] * tw;
                if (ost < ber[0] || ost > ber[1] || nord < ber[2] || nord > ber[3]) continue;
                n++;
                double rel = Math.abs(mit[i] - ohne[i]) / Math.max(1e-12, Math.max(mit[i], ohne[i]));
                if (rel > 1e-3) anders++;
                groesste = Math.max(groesste, rel);
            }
        A.hoechstens(f("N3: Ansicht %d – Bildpunkte mit anderer Tiefe als ohne Netz (> 10⁻³), Anteil von %d", nr, n), n > 0 ? (double) anders / n : 0, 1e-3, "");
        A.info("N3: Ansicht " + nr + " – größter relativer Unterschied der Tiefe", f("%.1e", groesste));
    }

    /**
     * N3b (B47): ohne Wellen am Teich gegen die Wahrheit. Je Bildpunkt läuft der Strahl in Schritten von 5 cm von der Höhe
     * Teich + 1 m bis Watt − 1 m; getroffen ist die erste Stelle, an der er unter η_Bild liegt. Gilt nur, wo er die Fläche
     * von oben trifft (an der Stelle davor lag er über demselben Spiegel, und der Treffer liegt auf ihm); trifft er eine Kante von der Seite (eine
     * senkrechte Wand aus Wasser, in dieser Szene ohne Deich im Gelände), ist die Wahrheit offen und zählt nicht. Der Weg
     * ohne Netz (feste Stelle ab dem Pegel) steht zur Auskunft daneben.
     */
    static void teichPruefen(Geraet g, Blick b, int nr) throws Exception {
        bild(g, b, true, 1234.5);
        float[] mit = z.tiefe.lies(), h = z.hdr.lies();
        bild(g, b, false, 1234.5);
        float[] ohne = z.tiefe.lies();
        double[] fv = b.basis()[2];
        double k = w.kruemmung > 0 ? 1 / (2 * w.kruemmung) : 0;
        long oben = 0, wand = 0, netzAnders = 0, ohneAnders = 0;
        StringBuilder wo = new StringBuilder();
        for (int y = 0; y < b.hoehe_px; y++)
            for (int x = 0; x < b.breite; x++) {
                int i = y * b.breite + x;
                double[] s = b.strahl(x + 0.5, y + 0.5);
                double t1 = treffen(b, s, TEICH + 1), t2 = treffen(b, s, PEGEL - 1);
                if (t1 <= 0 || t2 <= t1) continue;
                double o1 = b.ost + s[0] * t1, n1 = b.nord - s[2] * t1, o2 = b.ost + s[0] * t2, n2 = b.nord - s[2] * t2;
                if (!imTeich(o1, n1, -60) && !imTeich(o2, n2, -60)) continue;
                int schritte = (int) Math.min(20000, Math.ceil((t2 - t1) / 0.05));
                double tp = t1, hp = Double.NaN, ep = Double.NaN, wahr = -1;
                boolean vonOben = false;
                for (int q = 0; q <= schritte; q++) {
                    double t = t1 + (t2 - t1) * q / schritte;
                    double hh = b.hoehe + s[1] * t - k * t * t * (s[0] * s[0] + s[2] * s[2]);
                    float e = w.etaBild((float) (s[0] * t), (float) (s[2] * t), null);
                    if (!Float.isNaN(e) && hh <= e) {
                        vonOben = !Double.isNaN(ep) && Math.abs(ep - e) < 0.05 && hp > ep;
                        double ee = e, tt = t;
                        for (int it = 0; it < 8 && vonOben; it++) {
                            tt = treffen(b, s, ee);
                            float e2 = w.etaBild((float) (s[0] * tt), (float) (s[2] * tt), null);
                            if (Float.isNaN(e2)) break;
                            if (Math.abs(e2 - ee) < 1e-4) { ee = e2; break; }
                            ee = e2;
                        }
                        wahr = vonOben ? treffen(b, s, ee) : t;
                        if (vonOben) {
                            // der Treffer selbst muss auf demselben Spiegel liegen; sonst streift der Strahl die Kante (offen)
                            float ew = w.etaBild((float) (s[0] * wahr), (float) (s[2] * wahr), null);
                            vonOben = !Float.isNaN(ew) && Math.abs(ew - ee) < 0.05;
                        }
                        break;
                    }
                    tp = t;
                    hp = hh;
                    ep = Float.isNaN(e) ? Double.NaN : e;
                }
                if (wahr <= 0) continue;
                if (!vonOben) { wand++; continue; }
                oben++;
                double tief = b.nah / (wahr * (s[0] * fv[0] + s[1] * fv[1] + s[2] * fv[2]));
                boolean na = Math.abs(mit[i] - tief) / tief > 1e-3, oa = Math.abs(ohne[i] - tief) / tief > 1e-3;
                if (na) {
                    netzAnders++;
                    if (netzAnders <= 3) wo.append(f("(%d, %d) %.0f m, Treffer %.0f/%.0f; ", x, y, wahr, b.ost + s[0] * wahr - OST, b.nord - s[2] * wahr - NORD));
                }
                if (oa) ohneAnders++;
            }
        A.hoechstens(f("N3b: Ansicht %d – Bildpunkte, an denen das Netz eine andere Tiefe zeigt als der Strahl von oben trifft (> 10⁻³), Anteil von %d", nr, oben),
                oben > 0 ? (double) netzAnders / oben : 1, 2e-3, "");
        if (netzAnders > 0) A.info("N3b: Ansicht " + nr + " – erste Abweichungen", wo.toString());
        A.info("N3b: Ansicht " + nr + " – Weg ohne Netz (feste Stelle ab dem Pegel) anders als die Wahrheit; Bildpunkte mit Treffer von der Seite (offen)",
                f("%d von %d; %d", ohneAnders, oben, wand));
        speichere(h, b, Path.of("docs", "status", "seegang", "S3c_Netz_Teich_" + (nr - 10) + ".png"));
    }

    /** HDR (cd/m²) als PNG: Reinhard mit Weiß bei 8 000 cd/m², Gamma 2,2. */
    static void speichere(float[] h, Blick b, Path p) throws Exception {
        BufferedImage im = new BufferedImage(b.breite, b.hoehe_px, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < b.hoehe_px; y++)
            for (int x = 0; x < b.breite; x++) {
                int i = 4 * (y * b.breite + x), rgb = 0;
                for (int c = 0; c < 3; c++) {
                    double v = Math.max(0, h[i + c]) / 4000.0;
                    v = v / (1 + v) * 1.5;
                    int q = (int) Math.round(255 * Math.pow(Math.min(1, v), 1 / 2.2));
                    rgb = rgb << 8 | q;
                }
                im.setRGB(x, y, rgb);
            }
        ImageIO.write(im, "png", p.toFile());
    }
}
