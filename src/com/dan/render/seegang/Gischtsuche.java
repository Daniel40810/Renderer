package com.dan.render.seegang;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Sucht je Bild auf der Karte die Orte, an denen eine Walze am Kamm liegt ({@code see_gischt.comp}; Seegang Werkbuch,
 * S5b-2, SF50 A), für die Gischt aus den Walzen ({@code core.szene.Kammgischt}). Der Prozessor liest die Liste
 * {@link #RING} − 1 Bilder später aus einem geteilten Puffer, ohne auf die Karte zu warten.
 */
public final class Gischtsuche implements AutoCloseable {
    /** Plätze im Ring, Orte je Bild höchstens, kleinste Höhe (m), Abstand der Orte (m) [G]. */
    public static final int RING = 4, MAX_ORTE = 512;
    public static final double H_MIN = 0.3, ABSTAND = 2.0;
    private static final int KOPF = 16, PUSH = 16, GRUPPE = 8;

    private final Geraet g;
    private final Kernel kernel;
    private final Puffer[] ring = new Puffer[RING];
    private final double[] bx = new double[RING], by = new double[RING];
    private final boolean[] gesetzt = new boolean[RING];
    private int naechster;

    public Gischtsuche(Geraet g, Konto konto) {
        this.g = g;
        for (int i = 0; i < RING; i++) ring[i] = Puffer.geteilt(g, konto, "Gischtsuche " + i, KOPF + 16L * MAX_ORTE);
        Kernel.Bindung[] b = new Kernel.Bindung[23];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        kernel = Kernel.lade(g, SeegangKarte.PFAD + "see_gischt.comp", PUSH, null, b);
    }

    /** Nimmt die Suche in den Ablauf auf, nach den Stufen der Karte ({@link SeegangKarte#bild}). */
    public void suche(Ablauf a, SeegangKarte k) {
        int r = naechster;
        naechster = (naechster + 1) % RING;
        Puffer p = ring[r];
        bx[r] = k.bx;
        by[r] = k.by;
        gesetzt[r] = true;
        a.durchgang("Gischtsuche leeren", new Ablauf.Zugriff[] {new Ablauf.Zugriff(p, VK_PIPELINE_STAGE_2_CLEAR_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdFillBuffer(c, p.handle, 0, KOPF, 0));
        ByteBuffer pu = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        pu.putInt(0, k.stufen).putInt(4, MAX_ORTE).putFloat(8, (float) H_MIN).putFloat(12, (float) ABSTAND);
        int gr = Kernel.gruppen(k.n, GRUPPE);
        Mittel[] lesen = k.lesenSchaum();
        a.rechne("see_gischt", kernel, kernel.satz(k.belegungSchaum(p)), pu, gr, gr, k.stufen, lesen, new Mittel[] {p});
    }

    /**
     * Die Orte der ältesten Suche im Ring (vor {@link #RING} − 1 Bildern): je Ort {Ost, Nord, η, H} (UTM, m), in ziel
     * (wird bei Bedarf größer). Zurück die Zahl der Orte, 0 ohne.
     */
    public float[] lies(int[] zahl) {
        int r = naechster;                                                  // der nächste zu schreibende ist der älteste
        zahl[0] = 0;
        if (!gesetzt[r]) return new float[0];
        int m = Math.min(MAX_ORTE, ring[r].ints(1)[0]);
        if (m <= 0) return new float[0];
        float[] f = ring[r].floats(KOPF / 4 + 4 * m);
        float[] o = new float[4 * m];
        for (int i = 0; i < m; i++) {
            o[4 * i] = (float) (bx[r] + f[KOPF / 4 + 4 * i]);
            o[4 * i + 1] = (float) (by[r] + f[KOPF / 4 + 4 * i + 1]);
            o[4 * i + 2] = f[KOPF / 4 + 4 * i + 2];
            o[4 * i + 3] = f[KOPF / 4 + 4 * i + 3];
        }
        zahl[0] = m;
        return o;
    }

    @Override public void close() {
        g.ruhe();
        for (Puffer p : ring) if (p != null) p.close();
        kernel.close();
    }
}
