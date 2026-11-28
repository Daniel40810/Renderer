package com.dan.physics.waves.gpu;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.physics.waves.nah.Nahfeld;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Das Nahfeld auf der Karte (Seegang Werkbuch, S2b) in float, mit denselben Formeln wie {@link Nahfeld}: Stützstellen
 * aus Tiefe und Strömung ({@code nah_vorbereiten}), Beugung ({@code nah_beugung_a}, {@code nah_glaetten},
 * {@code nah_beugung_s}), Geschwindigkeiten und Rate ({@code nah_tempo}), Ausbreitung mit Rand, Kanten und Reflexion
 * ({@code nah_ausbreitung}), Quellen ({@code nah_quellen}, eine Gruppe je Zelle), Bindung ({@code nah_binden}, eine
 * Gruppe je Block), Verschieben und Einsetzen. Die Schalter und Größen liest jeder Schritt aus der Referenz; nach einer
 * Änderung von Grund, Spiegel, Strömung, Wind, Pflanzen, Wänden oder Bindung: {@link Nahfeld#zellen()}, dann
 * {@link #felder()}. Die Bilanz bucht nur die Referenz. Der Rand ist die Varianz je Geisterzelle
 * ({@link Nahfeld#randVarianz(Nahfeld.Ziel)}).
 */
public final class NahfeldKarte implements AutoCloseable {
    public static final String PFAD = "/com/dan/physics/waves/gpu/";
    private static final int GRUPPE = 64, PUSH = 16, GRUPPE_ZELLE = 256;
    public static final int ND_MAX = 72;
    /** Teilschritte (oder Runden des Anlaufs) je Abschicken. */
    public static final int ABSCHNITT = 8;

    public final Nahfeld n;
    private final Geraet g;
    private final Konto konto;
    private final boolean eigenesGeraet;
    private final Puffer werte, zelle, band, winkel, bin, tempo, beug, glatt, eA, eB, rand, aus, rate;
    private Puffer neue, ziel, nachQ;
    private final Kernel kVor, kBeugA, kGlatt, kBeugS, kTempo, kAus, kQuell, kBind, kVersch, kEins;
    private boolean inB, bindung;
    private boolean[] alt;
    public final long anzahl;

    /** Stoppuhr: Sekunden in Beugung und Tempo, Ausbreitung, Quellen und Bindung; Teilschritte und Schritte. */
    public double sTempo, sAusbreitung, sQuellen;
    public long nTeil, nSchritte;
    /**
     * Zur Messung (Befund B39): Mit einer Stoppuhr misst {@link #schritt} jeden Kernel auf der Karte und wartet nach
     * jedem Abschicken auf die Zeiten ({@link Stoppuhr#summen}). Ohne (Vorgabe) misst nur die Uhr des Prozessors.
     */
    public Stoppuhr uhr;

    public NahfeldKarte(Geraet g, Konto konto, Nahfeld n, boolean eigenesGeraet) {
        if (n.nd > ND_MAX) throw new IllegalArgumentException("NahfeldKarte: höchstens " + ND_MAX + " Richtungen");
        this.g = g; this.konto = konto; this.n = n; this.eigenesGeraet = eigenesGeraet;
        anzahl = (long) n.nn;
        long ncb = (long) n.nc * n.nb;
        werte = Puffer.speicher(g, konto, "nah Werte", 128);
        zelle = Puffer.speicher(g, konto, "nah Zelle", 80L * n.nc);
        band = Puffer.speicher(g, konto, "nah Band", 16L * n.nb);
        winkel = Puffer.speicher(g, konto, "nah Winkel", 8L * n.nd);
        bin = Puffer.speicher(g, konto, "nah Stützstellen", 16L * anzahl);
        tempo = Puffer.speicher(g, konto, "nah Tempo", 16L * anzahl);
        beug = Puffer.speicher(g, konto, "nah Beugung", 16L * ncb);
        glatt = Puffer.speicher(g, konto, "nah Glätten", 8L * ncb);
        eA = Puffer.speicher(g, konto, "nah N A", 4L * anzahl);
        eB = Puffer.speicher(g, konto, "nah N B", 4L * anzahl);
        nachQ = Puffer.speicher(g, konto, "nah nach Quellen", 4L * anzahl);
        rand = Puffer.speicher(g, konto, "nah Rand", 4L * n.randZellen() * n.nb * n.nd);
        aus = Puffer.speicher(g, konto, "nah Senken", 16L * n.nc);
        rate = Puffer.speicher(g, konto, "nah Rate", 16);
        neue = Puffer.speicher(g, konto, "nah Neue", 16);
        ziel = Puffer.speicher(g, konto, "nah Ziel", 16);
        Kernel.Bindung[] b = new Kernel.Bindung[16];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        kVor = Kernel.lade(g, PFAD + "nah_vorbereiten.comp", PUSH, null, b);
        kBeugA = Kernel.lade(g, PFAD + "nah_beugung_a.comp", PUSH, null, b);
        kGlatt = Kernel.lade(g, PFAD + "nah_glaetten.comp", PUSH, null, b);
        kBeugS = Kernel.lade(g, PFAD + "nah_beugung_s.comp", PUSH, null, b);
        kTempo = Kernel.lade(g, PFAD + "nah_tempo.comp", PUSH, null, b);
        kAus = Kernel.lade(g, PFAD + "nah_ausbreitung.comp", PUSH, null, b);
        kQuell = Kernel.lade(g, PFAD + "nah_quellen.comp", PUSH, null, b);
        kBind = Kernel.lade(g, PFAD + "nah_binden.comp", PUSH, null, b);
        kVersch = Kernel.lade(g, PFAD + "nah_verschieben.comp", PUSH, null, b);
        kEins = Kernel.lade(g, PFAD + "nah_einsetzen.comp", PUSH, null, b);
        float[] wi = new float[2 * n.nd], ba = new float[4 * n.nb];
        for (int q = 0; q < n.nd; q++) { wi[2 * q] = (float) n.cosT[q]; wi[2 * q + 1] = (float) n.sinT[q]; }
        for (int k = 0; k < n.nb; k++) { ba[4 * k] = (float) n.omega[k]; ba[4 * k + 1] = (float) n.f[k]; ba[4 * k + 2] = (float) n.df[k]; }
        winkel.schreibe(wi);
        band.schreibe(ba);
        rand.nullen();
        felder();
        hoch();
    }

    /** Mit eigenem Gerät (für Prüfungen); schließt es mit. */
    public static NahfeldKarte mitGeraet(Nahfeld n) {
        Geraet g = new Geraet();
        return new NahfeldKarte(g, g.konto("nahfeld", 4L << 30), n, true);
    }

    // ------------------------------------------------------------------ Daten

    private void werte() {
        ByteBuffer p = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder());
        p.putInt(n.nx).putInt(n.ny).putInt(n.nb).putInt(n.nd).putInt(n.periodischY ? 1 : 0).putInt(n.mitBeugung ? 1 : 0)
                .putInt(n.mitRefraktion ? 1 : 0).putInt(n.mitReflexion ? 1 : 0);
        p.putInt(n.mitWind ? 1 : 0).putInt(n.mitSchaum ? 1 : 0).putInt(n.mitBrechen ? 1 : 0).putInt(n.mitReibung ? 1 : 0)
                .putInt(n.mitPflanzen && n.pflanzen != null ? 1 : 0).putInt(n.kappung ? 1 : 0).putInt(n.abstandMax).putInt(n.bindungBlock);
        double[] fl = {n.dx, n.dTheta, n.drehGrenze, n.courant, n.minTiefe, n.stencil, n.deltaMin, n.deltaMax,
                n.cds, n.steilheitPM, n.cReibung, n.alphaBJ, n.gammaBJ, n.bindungZeit, n.bindungSpektrum != null ? 1 : 0, 0};
        for (double d : fl) p.putFloat((float) d);
        p.flip();
        werte.schreibe(p, 0);
    }

    /**
     * Nach {@link Nahfeld#zellen()} (oder vorbereiten()): Zellen (nass, Tiefe, Grund, Wand, Strömung, Wind, u*,
     * Pflanzen, Bindung, Gefälle) auf die Karte, dann die Stützstellen auf der Karte; blockierte und trockene verlieren
     * ihre Wirkung.
     */
    public void felder() {
        werte();
        float[] z = new float[20 * n.nc];
        boolean[] frei = n.bindungM0 != null ? n.freiVonBindung() : null;
        bindung = n.bindungM0 != null;
        for (int c = 0; c < n.nc; c++) {
            int o = 20 * c;
            double[] gf = n.gefaelle(c);
            z[o] = n.nass(c) ? 1 : 0; z[o + 1] = (float) n.tiefe(c); z[o + 2] = (float) n.grund[c]; z[o + 3] = n.wand != null && n.wand[c] ? 1 : 0;
            z[o + 4] = (float) n.u[c]; z[o + 5] = (float) n.v[c]; z[o + 6] = (float) n.windU[c]; z[o + 7] = (float) n.windV[c];
            z[o + 8] = n.uStern != null && n.uStern[c] > 0 ? (float) n.uStern[c] : 0f;
            z[o + 9] = n.pflanzen != null ? (float) n.pflanzen[c] : 0f;
            z[o + 10] = n.halm != null ? (float) n.halm[c] : 0f;
            z[o + 11] = frei != null && frei[c] ? 1 : 0;
            z[o + 12] = (float) gf[0]; z[o + 13] = (float) gf[1]; z[o + 14] = (float) gf[2]; z[o + 15] = (float) gf[3];
            z[o + 16] = (float) gf[4]; z[o + 17] = (float) gf[5]; z[o + 18] = bindung ? (float) n.bindungM0[c] : 0f;
        }
        zelle.schreibe(z);
        if (n.bindungSpektrum != null) {                                       // Spektrum des Ziels je Stützstelle (B20)
            if (ziel.groesse < 4L * anzahl) {
                ziel.close();
                ziel = Puffer.speicher(g, konto, "nah Ziel", 4L * anzahl);
            }
            float[] zf = new float[(int) anzahl];
            for (int i = 0; i < zf.length; i++) zf[i] = (float) n.bindungSpektrum[i];
            ziel.schreibe(zf);
        }
        Ablauf ab = new Ablauf(g);
        Puffer e = aktuell();
        ab.rechne("nah_vorbereiten", kVor, satz(kVor, e, e), push(0, 0, 0, 0), Kernel.gruppen(n.nc * n.nb, GRUPPE), 1, 1,
                new Mittel[] {werte, zelle, band, winkel}, new Mittel[] {bin, e});
        g.rechnen.einmal(c -> ab.aufnehmen(c, null));
    }

    /** Die einlaufende Varianz je Geisterzelle, Band und Richtung ({@link Nahfeld#randVarianz(Nahfeld.Ziel)}). */
    public void rand(double[] varianz) {
        int soll = n.randZellen() * n.nb * n.nd;
        if (varianz.length != soll) throw new IllegalArgumentException("NahfeldKarte.rand: " + varianz.length + " Werte statt " + soll);
        float[] r = new float[soll];
        for (int i = 0; i < soll; i++) r[i] = (float) varianz[i];
        rand.schreibe(r);
    }

    private Puffer aktuell() { return inB ? eB : eA; }

    /** Die Wirkung der Referenz auf die Karte. */
    public void hoch() {
        float[] f = new float[(int) anzahl];
        for (int i = 0; i < f.length; i++) f[i] = (float) n.n[i];
        eA.schreibe(f);
        inB = false;
    }

    /** Die Wirkung von der Karte in die Referenz. */
    public void runter() {
        float[] f = aktuell().floats((int) anzahl);
        for (int i = 0; i < f.length; i++) n.n[i] = f[i];
    }

    /** Leistung der Senken je Zelle aus dem letzten Schritt der Quellen: dwc, dbr, dbf, dveg (W/m²), je vier. */
    public float[] senken() { return aus.floats(4 * n.nc); }

    /** Hs je Zelle aus der Wirkung auf der Karte (mit σ der Karte). */
    public double[] hs() {
        float[] e = aktuell().floats((int) anzahl), bi = bin.floats(4 * (int) anzahl);
        double[] h = new double[n.nc];
        int m = n.nb * n.nd;
        for (int c = 0; c < n.nc; c++) {
            double s = 0;
            for (int x = c * m; x < (c + 1) * m; x++) s += bi[4 * x + 1] * e[x];
            h[c] = 4 * Math.sqrt(Math.max(0, s));
        }
        return h;
    }

    // ------------------------------------------------------------------ Schritt

    private long satz(Kernel k, Puffer alt, Puffer neu) {
        return k.satz(werte, zelle, band, winkel, bin, tempo, beug, glatt, alt, neu, rand, aus, rate, neue, ziel, nachQ);
    }

    private static ByteBuffer push(double h, int schalter, int pass, double dtMax) {
        ByteBuffer p = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        p.putFloat(0, (float) h).putInt(4, schalter).putInt(8, pass).putFloat(12, (float) dtMax);
        return p;
    }

    /** Beugung (wenn an) und Geschwindigkeiten; lokal: mit dem Schritt je Stützstelle für den Anlauf. */
    private void tempo(Ablauf ab, boolean lokal, double dtMax) {
        Puffer e = aktuell();
        int gr = Kernel.gruppen(n.nc * n.nb, GRUPPE);
        if (n.mitBeugung) {
            int[] ga = gruppen2((long) n.nc * n.nb, GRUPPE_S / n.nd);                // 256 / nd Paare (Zelle, Band) je Gruppe
            ab.rechne("nah_beugung_a", kBeugA, satz(kBeugA, e, e), push(0, 0, 0, 0), ga[0], ga[1], 1, new Mittel[] {werte, zelle, bin, e}, new Mittel[] {beug, glatt});
            for (int p = 0; p < n.glaetten; p++)
                ab.rechne("nah_glaetten", kGlatt, satz(kGlatt, e, e), push(0, 0, p, 0), gr, 1, 1, new Mittel[] {werte, zelle, glatt}, new Mittel[] {glatt});
            ab.rechne("nah_beugung_s", kBeugS, satz(kBeugS, e, e), push(0, 0, n.glaetten, 0), gr, 1, 1, new Mittel[] {werte, zelle, glatt, beug}, new Mittel[] {beug});
        }
        int[] gs = gruppen2(anzahl, GRUPPE_S);
        ab.rechne("nah_tempo", kTempo, satz(kTempo, e, e), push(0, lokal ? 1 : 0, 0, dtMax), gs[0], gs[1], 1, new Mittel[] {werte, zelle, winkel, bin, beug, rate},
                new Mittel[] {tempo, rate});
    }

    /**
     * Ein Teilschritt: Ausbreitung von der aktuellen Wirkung in die andere; dann Quellen und Bindung, wenn verlangt. Im
     * Anlauf ({@code lokal}) sind die Senken um den Zustand nach den Quellen der Runde davor linearisiert (B22);
     * {@code erste}: die erste Runde, dann gilt der Zustand vor der Ausbreitung.
     */
    private void teilschritt(Ablauf ab, double h, boolean lokal, boolean quellen, boolean binden, double dtMax, boolean erste) {
        Puffer von = aktuell(), nach = inB ? eA : eB;
        int sch = lokal ? 1 : 0;
        int[] gs = gruppen2(anzahl, GRUPPE_S);
        ab.rechne("nah_ausbreitung", kAus, satz(kAus, von, nach), push(h, sch, 0, dtMax), gs[0], gs[1], 1,
                new Mittel[] {werte, zelle, band, winkel, bin, tempo, von, rand}, new Mittel[] {nach});
        inB = !inB;
        if (quellen)
            ab.rechne("nah_quellen", kQuell, satz(kQuell, von, nach), push(h, sch | 2 | (lokal && erste ? 4 : 0), 0, dtMax), n.nx, n.ny, 1,
                    new Mittel[] {werte, zelle, band, bin, tempo, von, nach, nachQ}, new Mittel[] {nach, aus, nachQ});
        if (binden) {
            int bs = Math.max(1, n.bindungBlock);
            ab.rechne("nah_binden", kBind, satz(kBind, nach, nach), push(h, sch, 0, dtMax), (n.nx + bs - 1) / bs, (n.ny + bs - 1) / bs, 1,
                    new Mittel[] {werte, zelle, bin, tempo, nach, ziel}, new Mittel[] {nach});
        }
    }

    /** Gruppe der Kernel mit einem Faden je Stützstelle (Befund B39). */
    static final int GRUPPE_S = 256;

    /** Zahl der Gruppen für so viele Einheiten zu je gruppe, als (x, y) mit x höchstens 65 535. */
    static int[] gruppen2(long einheiten, int gruppe) {
        long g = (einheiten + gruppe - 1) / gruppe;
        int gx = (int) Math.min(65535, Math.max(1, g)), gy = (int) ((g + gx - 1) / gx);
        return new int[] {gx, gy};
    }

    /** Das Gerät, auf dem die Karte rechnet. */
    public Geraet geraet() { return g; }

    /** Schickt ab und wartet; mit {@link #uhr} je Kernel gemessen. */
    private void abschicken(Ablauf ab) {
        Stoppuhr u = uhr;
        g.rechnen.einmal(c -> { if (u != null) u.runde(c); ab.aufnehmen(c, u); });
        if (u != null) u.sammle(true);
    }

    private boolean quellenAn() { return n.mitWind || n.mitSchaum || n.mitBrechen || n.mitReibung || (n.mitPflanzen && n.pflanzen != null); }

    /** Ein Schritt wie {@link Nahfeld#schritt}: Beugung, Ausbreitung in Teilschritten nach der Rate, Quellen und Bindung. */
    public int schritt(double dt) {
        long t0 = System.nanoTime();
        werte();
        rate.nullen();
        Ablauf a0 = new Ablauf(g);
        tempo(a0, false, 0);
        abschicken(a0);
        double r = Float.intBitsToFloat(rate.ints(1)[0]);
        int ts = Math.max(1, (int) Math.ceil(dt * r / n.courant));
        double h = dt / ts;
        long t1 = System.nanoTime();
        boolean q = quellenAn(), je = n.quellenJeTeilschritt, bi = bindung && n.bindungM0 != null;
        for (int m = 0; m < ts; ) {
            int z = Math.min(ABSCHNITT, ts - m);
            Ablauf ab = new Ablauf(g);
            for (int s = 0; s < z; s++) teilschritt(ab, h, false, q && je, bi && je, 0, false);
            abschicken(ab);
            m += z;
        }
        long t2 = System.nanoTime();
        if ((q || bi) && !je) {
            Puffer e = aktuell();
            Ablauf ab = new Ablauf(g);
            if (q) ab.rechne("nah_quellen", kQuell, satz(kQuell, e, e), push(dt, 0, 0, 0), n.nx, n.ny, 1, new Mittel[] {werte, zelle, band, bin, tempo, e}, new Mittel[] {e, aus});
            if (bi) {
                int bs = Math.max(1, n.bindungBlock);
                ab.rechne("nah_binden", kBind, satz(kBind, e, e), push(dt, 0, 0, 0), (n.nx + bs - 1) / bs, (n.ny + bs - 1) / bs, 1,
                        new Mittel[] {werte, zelle, bin, tempo, e, ziel}, new Mittel[] {e});
            }
            abschicken(ab);
        }
        sTempo += (t1 - t0) * 1e-9;
        sAusbreitung += (t2 - t1) * 1e-9;
        sQuellen += (System.nanoTime() - t2) * 1e-9;
        nTeil += ts;
        nSchritte++;
        return ts;
    }

    /**
     * Anlauf wie {@link Nahfeld#anlauf}: Runden in Pseudozeit mit dem Schritt je Stützstelle; je {@link #ABSCHNITT}
     * Runden abgeschickt, alle 16 Runden die Änderung von Hs geprüft. Gibt die Runden zurück.
     */
    public int anlauf(int hoechstens, double toleranz, double dtMax) {
        werte();
        boolean q = quellenAn(), bi = bindung && n.bindungM0 != null;
        double[] vorher = hs();
        int runden = 0;
        while (runden < hoechstens) {
            int z = Math.min(ABSCHNITT, hoechstens - runden);
            Ablauf ab = new Ablauf(g);
            for (int s = 0; s < z; s++) {
                tempo(ab, true, dtMax);
                teilschritt(ab, 0, true, q, bi, dtMax, runden + s == 0);
            }
            g.rechnen.einmal(c -> ab.aufnehmen(c, null));
            runden += z;
            if (runden % 16 == 0 || runden >= hoechstens) {
                double[] jetzt = hs();
                double gr = 0, hmax = 0;
                for (int c = 0; c < n.nc; c++) { gr = Math.max(gr, Math.abs(jetzt[c] - vorher[c])); hmax = Math.max(hmax, jetzt[c]); }
                vorher = jetzt;
                if (gr <= toleranz * 16 * Math.max(1e-9, hmax)) return runden;
            }
        }
        return runden;
    }

    /**
     * Seit S3d (Seegang Werkbuch 28): der Anlauf in Stücken, für die Anwendung zwischen den Bildern. Dieselben Runden und
     * Prüfungen wie {@link #anlauf} (ein Stück endet spätestens an der nächsten Prüfung, alle 16 Runden), darum dasselbe
     * Ergebnis; nur öfter abgeschickt.
     */
    public final class Anlauf {
        private final int hoechstens;
        private final double toleranz, dtMax;
        private final boolean q, bi;
        private double[] vorher;
        private int runden;
        private boolean fertig;
        private long zeitNs;

        private Anlauf(int hoechstens, double toleranz, double dtMax) {
            this.hoechstens = hoechstens;
            this.toleranz = toleranz;
            this.dtMax = dtMax;
            werte();
            q = quellenAn();
            bi = bindung && n.bindungM0 != null;
            vorher = hs();
            fertig = hoechstens <= 0;
        }

        /** Rechnet höchstens z Runden (mindestens 1) und gibt zurück, ob der Anlauf fertig ist. */
        public boolean weiter(int z) {
            if (fertig) return true;
            int r = Math.max(1, Math.min(z, Math.min(hoechstens - runden, 16 - runden % 16)));
            while (r > 0) {
                int zz = Math.min(ABSCHNITT, r);
                Ablauf ab = new Ablauf(g);
                for (int s = 0; s < zz; s++) {
                    tempo(ab, true, dtMax);
                    teilschritt(ab, 0, true, q, bi, dtMax, runden + s == 0);
                }
                zeitNs = g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                runden += zz;
                r -= zz;
            }
            if (runden % 16 == 0 || runden >= hoechstens) {
                double[] jetzt = hs();
                double gr = 0, hmax = 0;
                for (int c = 0; c < n.nc; c++) { gr = Math.max(gr, Math.abs(jetzt[c] - vorher[c])); hmax = Math.max(hmax, jetzt[c]); }
                vorher = jetzt;
                if (gr <= toleranz * 16 * Math.max(1e-9, hmax) || runden >= hoechstens) fertig = true;
            }
            return fertig;
        }

        /** Gerechnete Runden. */
        public int runden() { return runden; }

        /** Zeit des letzten Abschickens bis fertig auf der Karte (ns). */
        public long zeitNs() { return zeitNs; }
    }

    /** Seit S3d: beginnt einen {@link Anlauf} in Stücken (wie {@link #anlauf(int, double, double)}). */
    public Anlauf anlaufInStuecken(int hoechstens, double toleranz, double dtMax) { return new Anlauf(hoechstens, toleranz, dtMax); }

    // ------------------------------------------------------------------ Verschieben

    /**
     * Das Gitter zieht um (di, dj) Zellen mit; die Ecke der Referenz wandert mit ({@link Nahfeld#verschieben} ohne deren
     * Wirkung). Danach setzt der Aufrufer die Eingaben für das neue Gitter, ruft {@link Nahfeld#zellen()},
     * {@link #felder()} und {@link #einsetzen(Nahfeld.Ziel)}.
     */
    public void verschieben(int di, int dj) {
        Puffer von = aktuell(), nach = inB ? eA : eB;
        Ablauf ab = new Ablauf(g);
        ab.rechne("nah_verschieben", kVersch, satz(kVersch, von, nach), push(0, di, dj, 0), Kernel.gruppen((int) anzahl, GRUPPE), 1, 1,
                new Mittel[] {werte, von}, new Mittel[] {nach});
        g.rechnen.einmal(c -> ab.aufnehmen(c, null));
        inB = !inB;
        alt = new boolean[n.nc];
        for (int j = 0; j < n.ny; j++)
            for (int i = 0; i < n.nx; i++) {
                int is = i + di, js = j + dj;
                alt[j * n.nx + i] = is >= 0 && is < n.nx && js >= 0 && js < n.ny;
            }
        n.x0 += di * n.dx;
        n.y0 += dj * n.dx;
    }

    /** Nach {@link #verschieben} und {@link #felder()}: die neuen Zellen bekommen das Ziel. */
    public void einsetzen(Nahfeld.Ziel ziel) {
        if (alt == null) return;
        int m = n.nb * n.nd, k = 0;
        for (int c = 0; c < n.nc; c++) if (!alt[c]) k++;
        if (k > 0) {
            float[] l = new float[k * (m + 1)];
            double[] e = new double[m];
            int z = 0;
            for (int c = 0; c < n.nc; c++) {
                if (alt[c]) continue;
                ziel.varianz(n.x(c % n.nx), n.y(c / n.nx), e);
                l[z * (m + 1)] = c;
                for (int x = 0; x < m; x++) l[z * (m + 1) + 1 + x] = (float) e[x];
                z++;
            }
            if (neue.groesse < 4L * l.length) {
                neue.close();
                neue = Puffer.speicher(g, konto, "nah Neue", 4L * l.length);
            }
            neue.schreibe(l);
            Puffer e2 = aktuell();
            final int kk = k;
            Ablauf ab = new Ablauf(g);
            ab.rechne("nah_einsetzen", kEins, satz(kEins, e2, e2), push(0, 0, kk, 0), Kernel.gruppen(kk * m, GRUPPE), 1, 1,
                    new Mittel[] {werte, zelle, bin, neue}, new Mittel[] {e2});
            g.rechnen.einmal(c -> ab.aufnehmen(c, null));
        }
        alt = null;
    }

    @Override public void close() {
        g.ruhe();
        for (Kernel k : new Kernel[] {kVor, kBeugA, kGlatt, kBeugS, kTempo, kAus, kQuell, kBind, kVersch, kEins}) k.close();
        for (Puffer p : new Puffer[] {werte, zelle, band, winkel, bin, tempo, beug, glatt, eA, eB, rand, aus, rate, neue, ziel, nachQ}) p.close();
        if (eigenesGeraet) g.close();
    }
}
