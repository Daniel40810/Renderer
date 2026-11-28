package com.dan.render.seegang;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.szene.Kielwasser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Das Kielwasser auf der Karte (Seegang Werkbuch, S7d, Datei 89): zwei Kacheln um das Auge ({@link #NAH} m mit
 * {@link #N}² Zellen, {@link #WEIT} m ebenso) mit Höhe, Schaum und mittlerer Wellenzahl der fahrenden Schiffe
 * ({@code see_kiel.comp}, wie {@link Kielwasser#kachel} in double). Die Stufen der Clipmap nehmen die Höhe und den Schaum
 * dazu ({@code see_kiel_dazu.comp}, über {@link SeegangKarte#kiel}), die Körper die Höhe an ihren Punkten
 * ({@code see_koerper.comp}). Höchstens {@link #QUELLEN} Schiffe, die dem Auge am nächsten. Ohne Schiff in Fahrt rechnet
 * die Karte nichts ({@link #an} falsch).
 * <p>
 * Seit S7d2 (Datei 90) in Lagen: Lage 0 alle Quellen, dahinter je Quelle ihre eigene Lage (see_kiel.comp). An den Punkten
 * eines Schiffs mit Kielwasser lässt {@code see_koerper} dessen eigene Lage weg; welche Lage zu welcher Quelle gehört, sagt
 * {@link #lage} für das Bild, in dem die Kacheln gerechnet sind.
 * <p>
 * Seit S7d5 (Seegang Werkbuch 93, SF128 A, Befund L5d4 4): Die nahe Kachel ist {@link #NAH} = 512 m (2 m je Zelle) und liegt
 * {@link #VOR} m vor dem Auge längs des Blicks; sie trägt die Wellen bis 6 m Länge. Die weite (8 m je Zelle) trägt nur noch den
 * Schaum: Wellen unter 24 m schnitt sie ohnehin ab, und die Schiffe von Hooge machen höchstens rund 17 m (Kopf: vierter Wert
 * der Kachel 1 = nur Schaum). Körper außerhalb der nahen Kachel spüren damit kein Kielwasser.
 * <p>
 * Seit S7d6 (Seegang Werkbuch 94, SF133 A, Befunde L5d5 6 und 7): Die Körper lesen das Kielwasser nicht mehr aus den
 * Kacheln, {@code see_koerper} rechnet es an ihren Punkten direkt aus den Spuren im Puffer der Quellen ({@link #quellen},
 * {@code see_kiel_spur.glsl}): genau und vom Auge unabhängig. Die Lagen der Quellen in den Kacheln entfallen; {@link #lage}
 * ist jetzt die Nummer der Quelle in diesem Puffer. SF134 A: Die Kacheln rechnet sie im Wechsel, je Bild eine (nah, weit,
 * nah, …); nach einer Pause ohne Schiff in Fahrt und in {@link #sofort} beide. Die andere bleibt ein Bild alt, mit ihrem
 * Ursprung im Kopf.
 */
public final class Kielwasserkarte implements AutoCloseable {
    public static final String VERSION = "0.4";
    public static final int N = 256, QUELLEN = 6;
    /** Kante der nahen und der weiten Kachel (m) [G]; seit S7d5 die nahe 512 m und so weit vor dem Auge längs des Blicks [G]. */
    public static final double NAH = 512, WEIT = 2048, VOR = 200;
    private static final int KOPF_AUS = 48;

    private final Geraet g;
    private final Kernel kernel;
    private final Puffer ein, kacheln;
    private boolean an;
    /** Zuletzt: Schiffe in den Kacheln; Ursprung (relativ zu B) und Kante der Zellen je Kachel. */
    public volatile int zuletzt;
    public final double[] kachel = new double[6];

    public Kielwasserkarte(Geraet g, Konto konto) {
        this.g = g;
        ein = Puffer.speicher(g, konto, "Kielwasser, Quellen", 16L + 4L * Kielwasser.JE_QUELLE * QUELLEN);
        kacheln = Puffer.speicher(g, konto, "Kielwasser, Kacheln", KOPF_AUS + 16L * 2 * N * N);          // seit S7d6 ohne Lagen der Quellen
        kernel = Kernel.lade(g, SeegangKarte.PFAD + "see_kiel.comp", 16, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
    }

    /** Ein Puffer im Aufbau der Kacheln ohne Kielwasser (n = 0), für Kernels, die sonst keine haben. */
    static Puffer leer(Geraet g, Konto konto) {
        Puffer p = Puffer.speicher(g, konto, "Kielwasser, leer", KOPF_AUS + 16);
        ByteBuffer b = ByteBuffer.allocateDirect(KOPF_AUS + 16).order(ByteOrder.nativeOrder());
        p.schreibe(b, 0);
        return p;
    }

    /** Ob die Kacheln dieses Bilds Kielwasser haben. */
    public boolean an() { return an; }

    // seit S7d2: die Quellen in ihrer Reihe (dieses Bild)
    private final List<Kielwasser.Quelle> reihe = new ArrayList<>();
    // seit S7d6: Kacheln im Wechsel
    private int bildNr;
    private boolean beide = true;

    /** Nummer der Quelle q im Puffer der Quellen dieses Bilds (0, 1, …; −1: nicht dabei); seit S7d6 nicht mehr eine Lage. */
    public int lage(Kielwasser.Quelle q) { return q == null ? -1 : reihe.indexOf(q); }

    /**
     * Der Puffer der Kacheln (Kopf: n, an, –, –, je Kachel x0, y0, Zelle, nur Schaum; dann je Kachel und Zelle Höhe, Schaum,
     * K, Σ g). Seit S7d6 ohne Lagen der Quellen.
     */
    public Puffer kacheln() { return kacheln; }

    /** Seit S7d6: der Puffer der Quellen (Kopf: Zahl, –, –, –; dann je Quelle {@link Kielwasser#JE_QUELLE} Werte), für see_koerper. */
    public Puffer quellen() { return ein; }

    /**
     * Nimmt die Kacheln dieses Bilds in den Ablauf auf: Kielwasser kw, Bezug B (bx, by), Auge (ex, ey), UTM. Ohne Schiff in
     * Fahrt geschieht nichts.
     */
    public void rechne(Ablauf a, Kielwasser kw, double bx, double by, double ex, double ey) { rechne(a, kw, bx, by, ex, ey, Double.NaN); }

    /** Seit S7d5: wie oben, die nahe Kachel {@link #VOR} m vor dem Auge in der Richtung richtung (Grad; NaN: um das Auge). */
    public void rechne(Ablauf a, Kielwasser kw, double bx, double by, double ex, double ey, double richtung) { rechne(a, kw, bx, by, ex, ey, richtung, false); }

    /** Seit S7d6: wie oben; alle: beide Kacheln in diesem Bild (sonst im Wechsel, SF134 A). */
    public void rechne(Ablauf a, Kielwasser kw, double bx, double by, double ex, double ey, double richtung, boolean alle) {
        List<Kielwasser.Quelle> l = kw == null ? new ArrayList<>() : kw.inFahrt();
        l.sort(Comparator.comparingDouble(q -> Math.hypot(q.x - ex, q.y - ey)));
        int m = Math.min(QUELLEN, l.size());
        zuletzt = m;
        an = m > 0;
        reihe.clear();
        for (int i = 0; i < m; i++) reihe.add(l.get(i));
        if (!an) { beide = true; return; }
        boolean zwei = alle || beide;
        int erste = zwei ? 0 : bildNr++ & 1;
        beide = false;
        ByteBuffer e = ByteBuffer.allocateDirect(16 + 4 * Kielwasser.JE_QUELLE * m).order(ByteOrder.nativeOrder());
        e.putInt(m).putInt(0).putInt(0).putInt(0);
        for (int i = 0; i < m; i++) for (float f : kw.werte(l.get(i), bx, by)) e.putFloat(f);
        e.flip();
        double[] zellen = {NAH / N, WEIT / N};
        ByteBuffer k = ByteBuffer.allocateDirect(KOPF_AUS).order(ByteOrder.nativeOrder());
        k.putInt(N).putInt(1).putInt(0).putInt(0);                         // n, an; seit S7d6 keine Lagen der Quellen
        double vx = Double.isNaN(richtung) ? 0 : VOR * Math.sin(Math.toRadians(richtung)), vy = Double.isNaN(richtung) ? 0 : VOR * Math.cos(Math.toRadians(richtung));
        for (int t = 0; t < 2; t++) {
            double z = zellen[t], mx = ex + (t == 0 ? vx : 0), my = ey + (t == 0 ? vy : 0);
            double x0 = Math.floor((mx - bx) / z) * z - N / 2.0 * z, y0 = Math.floor((my - by) / z) * z - N / 2.0 * z;
            if (zwei || t == erste) { kachel[3 * t] = x0; kachel[3 * t + 1] = y0; kachel[3 * t + 2] = z; }   // seit S7d6: nur die gerechnete
            k.putFloat((float) x0).putFloat((float) y0).putFloat((float) z).putFloat(t == 1 ? 1 : 0);   // seit S7d5: die weite nur Schaum
        }
        k.flip();
        // seit S7d6: den Kopf der Kachel nur, wenn sie in diesem Bild gerechnet wird (die andere behält ihren Ursprung)
        ByteBuffer k0 = k.slice(0, 16), kt = k.slice(16 + 16 * erste, zwei ? 32 : 16);
        long offT = 16 + 16L * erste;
        a.durchgang("Kielwasser, Quellen", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(ein, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(kacheln, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> { vkCmdUpdateBuffer(c, ein.handle, 0, e); vkCmdUpdateBuffer(c, kacheln.handle, 0, k0); vkCmdUpdateBuffer(c, kacheln.handle, offT, kt); });
        ByteBuffer pu = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        pu.putInt(0, erste);
        int gx = (N + 7) / 8;
        a.rechne("see_kiel", kernel, kernel.satz(ein, kacheln), pu, gx, gx, zwei ? 2 : 1, new Mittel[] {ein, kacheln}, new Mittel[] {kacheln});
    }

    /** Wie {@link #rechne}, sofort abgeschickt und gewartet (Prüfungen); gibt Lage 0 der Kacheln zurück (4 · 2 · N² Werte) oder null. */
    public float[] sofort(Kielwasser kw, double bx, double by, double ex, double ey) {
        Ablauf a = new Ablauf(g);
        rechne(a, kw, bx, by, ex, ey, Double.NaN, true);                     // seit S7d6: beide Kacheln
        if (!an) return null;
        g.rechnen.einmal(c -> a.aufnehmen(c, null));
        ByteBuffer b = ByteBuffer.allocateDirect(16 * 2 * N * N).order(ByteOrder.nativeOrder());
        kacheln.lies(b, KOPF_AUS);
        float[] f = new float[4 * 2 * N * N];
        b.rewind();
        b.asFloatBuffer().get(f);
        return f;
    }

    @Override public void close() {
        g.ruhe();
        ein.close();
        kacheln.close();
        kernel.close();
    }
}
