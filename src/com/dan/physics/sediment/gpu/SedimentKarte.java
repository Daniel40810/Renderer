package com.dan.physics.sediment.gpu;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.physics.sediment.Sediment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Das Sediment auf der Karte (Werkbuch HalligHooge Teil A, 12.10, P9a) in float, mit denselben Formeln wie
 * {@link Sediment}: je Teilschritt {@code zelle.comp} (Schubspannung, Gleichgewicht, Geschiebe, Rippel),
 * {@code transport.comp} (Schwebstoff, Austausch mit dem Boden, Exner) und {@code rutsch.comp} (Böschungsrutsch,
 * {@link Sediment#rutschSchritte} Durchgänge). Die Bodenänderung läuft als Summe mit Ausgleich (Kahan), damit kleine
 * Zuwächse über viele Schritte nicht in der Rundung untergehen.
 * <p>
 * Der Eingang (Tiefe, Flüsse, Seegang, Grund, Korn) kommt aus {@link Sediment} mit {@link #eingang()}, der Zustand mit
 * {@link #hoch()}; {@link #runter()} holt Tiefe, Zustand und Schubspannung zurück. Schalter und gesetzte Größen liest
 * jeder Schritt aus {@link Sediment}. Den Fluss über den Rand bucht seit 0.2 auch die Karte (je Randzelle, Summe mit
 * Ausgleich); {@link #runter()} legt ihn seit {@link #hoch()} in {@link Sediment#aus}.
 */
public final class SedimentKarte implements AutoCloseable {
    public static final String PFAD = "/com/dan/physics/sediment/gpu/";
    private static final int GRUPPE = 64, PUSH = 8;
    /** Teilschritte je Abschicken. */
    public static final int ABSCHNITT = 8;

    public final Sediment s;
    private final Geraet g;
    private final boolean eigenesGeraet;
    private final Puffer werte, fest, qx, qy, hA, hB, cA, cB, bA, bB, rippel, abg, aus, wirk;
    private final Kernel kZelle, kTransport, kRutsch;
    private boolean wasserInB, bettInB;
    private byte[] wLetzt;
    /** Länge des laufenden Schritts (s) für den Deckel; 0: der Deckel gilt je Teilschritt. */
    private double intervall;

    public SedimentKarte(Geraet g, Konto konto, Sediment s, boolean eigenesGeraet) {
        this.g = g; this.s = s; this.eigenesGeraet = eigenesGeraet;
        long nc = s.nc;
        werte = Puffer.speicher(g, konto, "sediment Werte", 176);
        fest = Puffer.speicher(g, konto, "sediment Fest", 48 * nc);
        qx = Puffer.speicher(g, konto, "sediment qx", 4L * s.qx.length);
        qy = Puffer.speicher(g, konto, "sediment qy", 4L * s.qy.length);
        hA = Puffer.speicher(g, konto, "sediment h A", 4 * nc);
        hB = Puffer.speicher(g, konto, "sediment h B", 4 * nc);
        cA = Puffer.speicher(g, konto, "sediment c A", 8 * nc);
        cB = Puffer.speicher(g, konto, "sediment c B", 8 * nc);
        bA = Puffer.speicher(g, konto, "sediment Bett A", 32 * nc);
        bB = Puffer.speicher(g, konto, "sediment Bett B", 32 * nc);
        rippel = Puffer.speicher(g, konto, "sediment Rippel", 16 * nc);
        abg = Puffer.speicher(g, konto, "sediment Abgeleitet", 64 * nc);
        aus = Puffer.speicher(g, konto, "sediment Rand", 32 * nc);
        wirk = Puffer.speicher(g, konto, "sediment Wirklich", 16 * nc);
        Kernel.Bindung[] b = new Kernel.Bindung[13];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        kZelle = Kernel.lade(g, PFAD + "zelle.comp", PUSH, null, Arrays.copyOf(b, 8));
        kTransport = Kernel.lade(g, PFAD + "transport.comp", PUSH, null, b);
        kRutsch = Kernel.lade(g, PFAD + "rutsch.comp", 0, null, Arrays.copyOf(b, 5));
        hoch();
    }

    /** Mit eigenem Gerät (für Prüfungen); schließt es mit. */
    public static SedimentKarte mitGeraet(Sediment s) {
        Geraet g = new Geraet();
        return new SedimentKarte(g, g.konto("sediment", 1L << 30), s, true);
    }

    /** Die Schalter und gesetzten Größen aus {@link Sediment}; geschrieben nur, wenn sie sich geändert haben. */
    private void werte() {
        int schalter = (s.mitGeschiebe ? 1 : 0) | (s.mitSchweb ? 2 : 0) | (s.mitSchlick ? 4 : 0) | (s.mitRippel ? 8 : 0) | (s.mitDiffusion ? 16 : 0) | (s.zweiteOrdnung ? 32 : 0)
                | (Double.isNaN(s.randSand) ? 0 : 64) | (Double.isNaN(s.randSchlick) ? 0 : 128) | (s.ug != null ? 256 : 0) | (s.mitAuflage ? 512 : 0);
        ByteBuffer p = ByteBuffer.allocate(176).order(ByteOrder.nativeOrder());
        p.putInt(s.nx).putInt(s.ny).putInt(schalter).putInt(0);
        double[] fl = {s.dx, s.dy, s.rho, s.rhoS, s.nu, s.poros, s.hMin, s.epsHang, s.deltaAkt, s.mErosion, s.tauE0, s.tauE1, s.tKons, s.wsSchlick, s.tauD,
                s.rhoSchlick, s.hangSand, s.hangSchlick, s.rutschAnteil, s.rippelSchlick, s.courant, Double.isNaN(s.randSand) ? 0 : s.randSand, Double.isNaN(s.randSchlick) ? 0 : s.randSchlick, 0,
                s.faktor, s.deckel, s.deckelMin, intervall, s.tauA, s.mA1, s.mA0, s.anteilTief, s.eSpirale, 0, 0, 0};
        for (double d : fl) p.putFloat((float) d);
        byte[] neu = Arrays.copyOf(p.array(), p.position());
        if (Arrays.equals(neu, wLetzt)) return;
        ByteBuffer d = ByteBuffer.allocateDirect(neu.length).order(ByteOrder.nativeOrder());
        d.put(neu).flip();
        werte.schreibe(d, 0);
        wLetzt = neu;
    }

    /** Der Eingang aus {@link Sediment} auf die Karte: Tiefe, Flüsse, Seegang, Grund und Korn. */
    public void eingang() {
        int nc = s.nc;
        float[] f = new float[12 * nc], h = new float[nc], x = new float[s.qx.length], y = new float[s.qy.length];
        for (int c = 0; c < nc; c++) {
            int o = 12 * c;
            f[o] = (float) s.grund[c]; f[o + 1] = (float) s.d50[c]; f[o + 2] = (float) s.ubot[c]; f[o + 3] = (float) s.tp[c];
            f[o + 4] = (float) s.wcos[c]; f[o + 5] = (float) s.wsin[c]; f[o + 6] = (float) s.porositaet(c); f[o + 7] = s.tiefe != null ? (float) s.tiefe[c] : 0f;
            if (s.ug != null) { f[o + 8] = (float) s.ug[c]; f[o + 9] = (float) s.vg[c]; }
            if (s.spiral != null) f[o + 10] = (float) s.spiral[c];
            f[o + 11] = s.frei != null ? (float) s.frei[c] : 1f;
            h[c] = (float) s.h[c];
        }
        for (int e = 0; e < x.length; e++) x[e] = (float) s.qx[e];
        for (int e = 0; e < y.length; e++) y[e] = (float) s.qy[e];
        fest.schreibe(f); qx.schreibe(x); qy.schreibe(y);
        (wasserInB ? hB : hA).schreibe(h);
    }

    /** Bringt Eingang und Zustand der Referenz auf die Karte. */
    public void hoch() {
        int nc = s.nc;
        float[] k = new float[2 * nc], b = new float[8 * nc], r = new float[4 * nc], w = new float[4 * nc];
        for (int c = 0; c < nc; c++) {
            k[2 * c] = (float) s.cs[c]; k[2 * c + 1] = (float) s.cm[c];
            b[8 * c] = (float) s.dzS[c]; b[8 * c + 2] = (float) s.dzM[c]; b[8 * c + 4] = (float) s.mSchlick[c]; b[8 * c + 5] = (float) s.alter[c]; b[8 * c + 6] = (float) s.mAuflage[c];
            w[4 * c] = (float) s.sandWirklich[c]; w[4 * c + 2] = (float) s.schlickWirklich[c]; w[4 * c + 3] = (float) s.gedeckelt[c];
            r[4 * c] = (float) s.rH[c]; r[4 * c + 1] = (float) s.rL[c]; r[4 * c + 2] = (float) s.rX[c]; r[4 * c + 3] = (float) s.rY[c];
        }
        wasserInB = false; bettInB = false;
        cA.schreibe(k); bA.schreibe(b); rippel.schreibe(r); wirk.schreibe(w);
        aus.nullen();
        eingang();
    }

    /** Holt Tiefe, Zustand, Schubspannung und Rauheit von der Karte in die Referenz. */
    public void runter() {
        int nc = s.nc;
        float[] h = (wasserInB ? hB : hA).floats(nc), k = (wasserInB ? cB : cA).floats(2 * nc), b = (bettInB ? bB : bA).floats(8 * nc), r = rippel.floats(4 * nc), a = abg.floats(16 * nc), w = wirk.floats(4 * nc);
        for (int c = 0; c < nc; c++) {
            s.h[c] = h[c];
            s.cs[c] = k[2 * c]; s.cm[c] = k[2 * c + 1];
            s.dzS[c] = b[8 * c]; s.dzM[c] = b[8 * c + 2]; s.mSchlick[c] = b[8 * c + 4]; s.alter[c] = b[8 * c + 5]; s.mAuflage[c] = b[8 * c + 6];
            s.sandWirklich[c] = w[4 * c]; s.schlickWirklich[c] = w[4 * c + 2]; s.gedeckelt[c] = w[4 * c + 3];
            s.rH[c] = r[4 * c]; s.rL[c] = r[4 * c + 1]; s.rX[c] = r[4 * c + 2]; s.rY[c] = r[4 * c + 3];
            s.tauM[c] = a[16 * c + 14]; s.tauMax[c] = a[16 * c + 15];
            s.z0[c] = s.d50[c] / 12 + (s.rL[c] > 0 ? s.rH[c] * s.rH[c] / s.rL[c] : 0);
        }
        float[] r2 = aus.floats(8 * nc);
        double as = 0, am = 0, ag = 0;
        for (int c = 0; c < nc; c++) { as += r2[8 * c]; am += r2[8 * c + 2]; ag += r2[8 * c + 4]; }
        s.aus[0] = as; s.aus[1] = am; s.aus[2] = ag;
    }

    /** n Teilschritte zu je dt Sekunden; wartet, bis sie fertig sind. */
    public void teilschritte(int n, double dt) {
        werte();
        ByteBuffer push = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        push.putFloat(0, (float) dt).putFloat(4, 0);
        int gruppen = Kernel.gruppen(s.nc, GRUPPE);
        for (int m = 0; m < n; ) {
            int z = Math.min(ABSCHNITT, n - m);
            Ablauf ab = new Ablauf(g);
            for (int t = 0; t < z; t++) {
                Puffer h0 = wasserInB ? hB : hA, h1 = wasserInB ? hA : hB, c0 = wasserInB ? cB : cA, c1 = wasserInB ? cA : cB, b0 = bettInB ? bB : bA, b1 = bettInB ? bA : bB;
                ab.rechne("zelle", kZelle, kZelle.satz(werte, fest, qx, qy, h0, b0, rippel, abg), push, gruppen, 1, 1,
                        new Mittel[] {werte, fest, qx, qy, h0, b0, rippel}, new Mittel[] {rippel, abg});
                ab.rechne("transport", kTransport, kTransport.satz(werte, qx, qy, h0, h1, c0, c1, b0, b1, abg, fest, aus, wirk), push, gruppen, 1, 1,
                        new Mittel[] {werte, qx, qy, h0, c0, b0, abg, fest, aus, wirk}, new Mittel[] {h1, c1, b1, aus, wirk});
                wasserInB = !wasserInB; bettInB = !bettInB;
                if (s.mitRutsch) for (int k = 0; k < s.rutschSchritte; k++) {
                    Puffer r0 = bettInB ? bB : bA, r1 = bettInB ? bA : bB;
                    ab.rechne("rutsch", kRutsch, kRutsch.satz(werte, fest, abg, r0, r1), null, gruppen, 1, 1, new Mittel[] {werte, fest, abg, r0}, new Mittel[] {r1});
                    bettInB = !bettInB;
                }
            }
            g.rechnen.einmal(c -> ab.aufnehmen(c, null));
            m += z;
        }
        s.zeit += n * dt;
        s.teilschritte += n;
    }

    /**
     * Ein Schritt wie {@link Sediment#schritt}: so viele Teilschritte, wie die Courant-Zahl verlangt. Die Zahl folgt aus
     * Tiefe und Flüssen in {@link Sediment} (nach {@link #eingang()} dieselben wie auf der Karte).
     */
    public int schritt(double dt) {
        int n = s.teile(dt);
        intervall = dt;
        teilschritte(n, dt / n);
        return n;
    }

    @Override public void close() {
        g.ruhe();
        kZelle.close(); kTransport.close(); kRutsch.close();
        for (Puffer p : new Puffer[] {werte, fest, qx, qy, hA, hB, cA, cB, bA, bB, rippel, abg, aus, wirk}) p.close();
        if (eigenesGeraet) g.close();
    }
}
