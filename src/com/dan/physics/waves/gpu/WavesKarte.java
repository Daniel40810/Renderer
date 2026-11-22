package com.dan.physics.waves.gpu;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.physics.waves.Waves;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Der Seegang auf der Karte (Werkbuch HalligHooge Teil A, 12.9, P8a) in float, mit denselben Formeln wie
 * {@link Waves}: die Ausbreitung mit Strömung (Transport mit c_g + U, Drehung an Tiefe und Strom, Verschiebung der
 * Frequenz; {@code ausbreitung.comp}, ein Faden je Zelle und Frequenz) und die Quellen (Wind, Schaumkronen,
 * Vierwellen, Bodenreibung, Brechen; {@code quellen.comp}, eine Gruppe je Zelle). Die Schalter ({@code mitWind} …)
 * und Größen der Quellen liest jeder Schritt aus {@link Waves}; Tiefe, Strömung und Wind nach einer Änderung mit
 * {@link Waves#vorbereiten()} und dann {@link #felder()}. Die Bilanz bucht nur die Referenz. Das einlaufende Spektrum
 * steht je Randzelle ({@link #rand(double[])}, seit P8b; der Konstruktor mit einem Spektrum setzt es an jede).
 * Mit den Quellen kommt {@code waves.diss} je Zelle ({@link #diss()}); u* je Zelle aus {@link Waves#uStern}.
 */
public final class WavesKarte implements AutoCloseable {
    public static final String PFAD = "/com/dan/physics/waves/gpu/";
    private static final int GRUPPE = 64, PUSH = 8;
    /** Grenzen der Karte (Größe der Felder im Kernel). */
    public static final int ND_MAX = 72, NF_MAX = 32, QO_MAX = 4;
    /** Teilschritte je Abschicken. */
    public static final int ABSCHNITT = 16;

    public final Waves w;
    private final Geraet g;
    private final boolean eigenesGeraet;
    private final Puffer werte, qwerte, zelle, freq, winkel, eA, eB, rand, strom, stufen, diss;
    private final Kernel kAus, kQuell;
    private boolean inB;
    private byte[] qLetzt;
    public final long anzahl;

    /**
     * {@code raender}: Bits 1 West, 2 Ost, 4 Süd, 8 Nord, an denen {@code spektrum} einläuft: nf · nd Werte (E in
     * m²/(Hz·rad)) für alle Randzellen gleich, oder {@link Waves#randZellen()} · nf · nd je Randzelle; null: nichts.
     */
    public WavesKarte(Geraet g, Konto konto, Waves w, double[] spektrum, int raender, boolean eigenesGeraet) {
        if (w.nd > ND_MAX) throw new IllegalArgumentException("WavesKarte: höchstens " + ND_MAX + " Richtungen");
        if (w.nf > NF_MAX || w.diaStufen()[12] > QO_MAX) throw new IllegalArgumentException("WavesKarte: höchstens " + NF_MAX + " Frequenzen, DIA höchstens " + QO_MAX + " Stufen über der höchsten");
        if (!w.zweiteOrdnung) throw new IllegalArgumentException("WavesKarte: die Karte rechnet in Richtung und Frequenz immer zweiter Ordnung");
        this.g = g; this.w = w; this.eigenesGeraet = eigenesGeraet;
        anzahl = (long) w.nc * w.nf * w.nd;
        werte = Puffer.speicher(g, konto, "waves Werte", 64);
        qwerte = Puffer.speicher(g, konto, "waves Quellwerte", 256);
        zelle = Puffer.speicher(g, konto, "waves Zelle", 16L * w.nc);
        freq = Puffer.speicher(g, konto, "waves Frequenz", 16L * w.nc * w.nf);
        winkel = Puffer.speicher(g, konto, "waves Winkel", 8L * w.nd);
        eA = Puffer.speicher(g, konto, "waves E A", 4L * anzahl);
        eB = Puffer.speicher(g, konto, "waves E B", 4L * anzahl);
        rand = Puffer.speicher(g, konto, "waves Rand", 4L * w.randZellen() * w.nf * w.nd);
        strom = Puffer.speicher(g, konto, "waves Strom", 48L * w.nc);
        stufen = Puffer.speicher(g, konto, "waves Stufen", 16L * w.nf);
        diss = Puffer.speicher(g, konto, "waves Diss", 4L * w.nc);
        Kernel.Bindung[] b = new Kernel.Bindung[9];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        kAus = Kernel.lade(g, PFAD + "ausbreitung.comp", PUSH, null, b);
        kQuell = Kernel.lade(g, PFAD + "quellen.comp", PUSH, null, Arrays.copyOf(b, 8));
        float[] wi = new float[2 * w.nd], st = new float[4 * w.nf];
        for (int q = 0; q < w.nd; q++) { wi[2 * q] = (float) w.cosT[q]; wi[2 * q + 1] = (float) w.sinT[q]; }
        for (int m = 0; m < w.nf; m++) { st[4 * m] = (float) w.sig[m]; st[4 * m + 1] = (float) w.df[m]; st[4 * m + 2] = (float) w.f[m]; }
        winkel.schreibe(wi); stufen.schreibe(st);
        int nfd = w.nf * w.nd;
        if (spektrum != null && spektrum.length == nfd) {
            double[] alle = new double[w.randZellen() * nfd];
            for (int r = 0; r < w.randZellen(); r++) System.arraycopy(spektrum, 0, alle, r * nfd, nfd);
            rand(alle);
        } else rand(spektrum == null ? new double[w.randZellen() * nfd] : spektrum);
        felder(raender);
        hoch();
    }

    private int raender;

    /** Nach {@link Waves#vorbereiten()}: Tiefe, Gefälle, Strömung, Wind und die Größen je Frequenz neu auf die Karte. */
    public void felder() { felder(raender); }

    private void felder(int raender) {
        this.raender = raender;
        ByteBuffer p = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        p.putInt(w.nx).putInt(w.ny).putInt(w.nf).putInt(w.nd).putInt(w.periodischY ? 1 : 0).putInt(raender)
                .putInt(w.strom() ? 1 : 0).putInt(w.strom() && w.mitVerschiebung ? 1 : 0);
        p.putFloat((float) w.dx).putFloat((float) w.dy).putFloat((float) w.dTheta).putFloat((float) w.drehGrenze)
                .putFloat((float) Math.pow(w.stufe, -w.schweif - 1)).putFloat(0).putFloat(0).putFloat(0);
        p.flip();
        werte.schreibe(p, 0);
        float[] z = new float[4 * w.nc], fq = new float[4 * w.nc * w.nf], sr = new float[12 * w.nc];
        for (int c = 0; c < w.nc; c++) {
            boolean nass = w.nass(c);
            z[4 * c] = nass ? 1 : 0; z[4 * c + 1] = (float) w.gefaelleX(c); z[4 * c + 2] = (float) w.gefaelleY(c); z[4 * c + 3] = (float) w.tiefe[c];
            double[] gs = w.stromGefaelle(c);
            int o = 12 * c;
            sr[o] = (float) w.u[c]; sr[o + 1] = (float) w.v[c];
            sr[o + 2] = (float) gs[0]; sr[o + 3] = (float) gs[1]; sr[o + 4] = (float) gs[2]; sr[o + 5] = (float) gs[3];
            sr[o + 6] = (float) w.windU[c]; sr[o + 7] = (float) w.windV[c];
            sr[o + 8] = w.uStern != null && w.uStern[c] > 0 ? (float) w.uStern[c] : 0f;
            if (!nass) continue;
            for (int m = 0; m < w.nf; m++) {
                int of = 4 * (c * w.nf + m);
                fq[of] = (float) w.gruppe(c, m);
                fq[of + 1] = w.mitRefraktion ? (float) w.refraktion(c, m) : 0;
                fq[of + 2] = (float) w.wellenzahl(c, m);
            }
        }
        zelle.schreibe(z); freq.schreibe(fq); strom.schreibe(sr);
    }

    /** Die Schalter und Größen der Quellen aus {@link Waves}; geschrieben nur, wenn sie sich geändert haben. */
    private void quellwerte() {
        int[] qs = w.diaStufen();
        double[] qg = w.diaGewichte();
        double l1 = 1 + w.lambda, l2 = 1 - w.lambda;
        ByteBuffer p = ByteBuffer.allocate(256).order(ByteOrder.nativeOrder());
        p.putInt(w.nx).putInt(w.ny).putInt(w.nf).putInt(w.nd).putInt(qs[12])
                .putInt(w.mitWind ? 1 : 0).putInt(w.mitSchaum ? 1 : 0).putInt(w.mitQuad ? 1 : 0).putInt(w.mitReibung ? 1 : 0).putInt(w.mitBrechen ? 1 : 0);
        for (int n = 0; n < 12; n++) p.putInt(qs[n]);                           // qmP0, qmP1, qmM0, qmM1, qkP[4], qkM[4]
        double[] fl = {w.cds, w.steilheitPM, w.cReibung, w.alphaBJ, w.gammaBJ, w.cQuad, w.begrenzer, w.schweif,
                w.stufe, w.f[0], w.dTheta, 1 / Math.pow(l1, 4), 1 / Math.pow(l2, 4), 2 / Math.pow(1 - w.lambda * w.lambda, 4), Math.pow(w.stufe, -w.schweif),
                l1 * Math.pow(w.stufe, -qs[0]), l1 * Math.pow(w.stufe, -qs[1]), l2 * Math.pow(w.stufe, -qs[2]), l2 * Math.pow(w.stufe, -qs[3])};
        for (double d : fl) p.putFloat((float) d);
        for (double d : qg) p.putFloat((float) d);                              // qwP0, qwP1, qwM0, qwM1, qvP[4], qvM[4]
        byte[] neu = Arrays.copyOf(p.array(), p.position());
        if (Arrays.equals(neu, qLetzt)) return;
        ByteBuffer d = ByteBuffer.allocateDirect(neu.length).order(ByteOrder.nativeOrder());
        d.put(neu).flip();
        qwerte.schreibe(d, 0);
        qLetzt = neu;
    }

    /** Das einlaufende Spektrum je Randzelle ({@link Waves#randZellen()} · nf · nd, Index (r · nf + m) · nd + q) auf die Karte. */
    public void rand(double[] feld) {
        if (feld.length != w.randZellen() * w.nf * w.nd) throw new IllegalArgumentException("WavesKarte.rand: " + feld.length + " Werte statt " + w.randZellen() * w.nf * w.nd);
        float[] r = new float[feld.length];
        for (int n = 0; n < r.length; n++) r[n] = (float) feld[n];
        rand.schreibe(r);
    }

    /** {@code waves.diss} je Zelle aus dem letzten Schritt der Quellen (W/m²) in {@link Waves#diss}. */
    public void diss() {
        float[] f = diss.floats(w.nc);
        for (int c = 0; c < w.nc; c++) w.diss[c] = f[c];
    }

    /** Mit eigenem Gerät (für Prüfungen); schließt es mit. */
    public static WavesKarte mitGeraet(Waves w, double[] spektrum, int raender) {
        Geraet g = new Geraet();
        return new WavesKarte(g, g.konto("waves", 4L << 30), w, spektrum, raender, true);
    }

    /** Bringt das Spektrum der Referenz auf die Karte. */
    public void hoch() {
        float[] f = new float[(int) anzahl];
        for (int n = 0; n < f.length; n++) f[n] = (float) w.e[n];
        eA.schreibe(f);
        inB = false;
    }

    /** Holt das Spektrum von der Karte in die Referenz. */
    public void runter() {
        float[] f = (inB ? eB : eA).floats((int) anzahl);
        for (int n = 0; n < f.length; n++) w.e[n] = f[n];
    }

    /** n Teilschritte der Ausbreitung zu je h Sekunden; wartet, bis sie fertig sind. */
    public void teilschritte(int n, double h) {
        ByteBuffer push = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        push.putFloat(0, (float) h).putFloat(4, 0);
        int gruppen = Kernel.gruppen(w.nc * w.nf, GRUPPE);
        for (int m = 0; m < n; ) {
            int z = Math.min(ABSCHNITT, n - m);
            Ablauf ab = new Ablauf(g);
            for (int s = 0; s < z; s++) {
                Puffer von = inB ? eB : eA, nach = inB ? eA : eB;
                ab.rechne("ausbreitung", kAus, kAus.satz(werte, zelle, freq, winkel, von, nach, rand, strom, stufen), push, gruppen, 1, 1,
                        new Mittel[] {werte, zelle, freq, winkel, von, rand, strom, stufen}, new Mittel[] {nach});
                inB = !inB;
            }
            g.rechnen.einmal(c -> ab.aufnehmen(c, null));
            m += z;
        }
    }

    /** Ein Schritt der Quellen über dt Sekunden (an Ort und Stelle); wartet, bis er fertig ist. */
    public void quellen(double dt) {
        quellwerte();
        ByteBuffer push = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        push.putFloat(0, (float) dt).putFloat(4, w.kappung ? 1 : 0);
        Puffer e = inB ? eB : eA;
        Ablauf ab = new Ablauf(g);
        ab.rechne("quellen", kQuell, kQuell.satz(qwerte, zelle, freq, winkel, e, strom, stufen, diss), push, w.nx, w.ny, 1,
                new Mittel[] {qwerte, zelle, freq, winkel, e, strom, stufen}, new Mittel[] {e, diss});
        g.rechnen.einmal(c -> ab.aufnehmen(c, null));
    }

    /** Ein Schritt wie {@link Waves#schritt}: Ausbreitung in Teilschritten nach der Courant-Zahl, dann die Quellen. */
    public int schritt(double dt) {
        long n0 = System.nanoTime();
        int n = Math.max(1, (int) Math.ceil(dt * w.rate() / w.courant));
        teilschritte(n, dt / n);
        long n1 = System.nanoTime();
        if (w.mitWind || w.mitSchaum || w.mitQuad || w.mitReibung || w.mitBrechen) quellen(dt);
        sAusbreitung += (n1 - n0) * 1e-9;
        sQuellen += (System.nanoTime() - n1) * 1e-9;
        nTeil += n;
        nSchritte++;
        return n;
    }

    /** Stoppuhr (P13d, A33): Sekunden in Ausbreitung (mit Warten) und Quellen, Zahl der Teilschritte und Schritte. */
    public double sAusbreitung, sQuellen;
    public long nTeil, nSchritte;

    @Override public void close() {
        g.ruhe();
        kAus.close();
        kQuell.close();
        for (Puffer p : new Puffer[] {werte, qwerte, zelle, freq, winkel, eA, eB, rand, strom, stufen, diss}) p.close();
        if (eigenesGeraet) g.close();
    }
}
