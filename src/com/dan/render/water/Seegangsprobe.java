package com.dan.render.water;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.szene.Seegangsnetz;
import com.dan.core.szene.Seegangsoptik;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Prüfstein 9 von P8b (Werkbuch HalligHooge Teil A, 12.9): die langen Wellen des Seegangs im Shader
 * ({@code seegang.glsl} über {@code seeprobe.comp}) gegen dieselben Formeln auf dem Prozessor
 * ({@link Seegangsoptik#neigung}, die Werte am Ort aus den {@link Seegangsnetz}en wie {@code seeWerte}). Die Karte
 * liest den Kopf, den {@link Wasserbild} für das letzte Bild geschrieben hat.
 */
public final class Seegangsprobe {
    private Seegangsprobe() { }

    /** Ergebnis: je Ort {Karte: Hs, Tp, cos, sin, mss, diss, aufgelöst, Rest, da} und dieselben auf dem Prozessor. */
    public record Ergebnis(float[] karte, double[] prozessor) { }

    /**
     * Rechnet an den Orten (x Ost, z Süd relativ zum Auge {@code ost, nord}) bei der Größe eines Bildpunkts {@code fuss}
     * (m). {@code w} hat eben ein Bild für dieses Auge zur Zeit {@code w.zeit} vorbereitet.
     */
    public static Ergebnis rechne(Geraet g, Konto k, Wasserbild w, double ost, double nord, float[] orte, float fuss) {
        int n = orte.length / 2;
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        try (Kernel kp = Kernel.lade(g, "/com/dan/render/water/seeprobe.comp", 16, null, P, P, P, P, X, X, P, P, P);
             Abtaster at = new Abtaster(g, false, false);
             Bild leer = new Bild(g, k, "Probe Seegang, leer", 4, 4, Bild.RGBA32F);
             Puffer werte = Puffer.speicher(g, k, "Probe Seegang, Werte", 60 * 4);
             Puffer po = Puffer.speicher(g, k, "Probe Seegang, Orte", 8L * n);
             Puffer aus = Puffer.speicher(g, k, "Probe Seegang, Aus", 36L * n)) {
            leer.schreibe(new float[4 * 4 * 4]);
            po.schreibe(orte);
            Ablauf a = new Ablauf(g, g.grafik);
            ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            p.putInt(0, n).putInt(4, Float.floatToRawIntBits(fuss));
            Puffer see = w.seegangPuffer();
            a.rechne("Probe Seegang", kp, kp.satz(w.kopf(), w.staende(), w.arten(), werte, at.an(leer), at.an(leer), aus, po, see), p, Kernel.gruppen(n, 64), 1, 1,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, w.kopf(), w.staende(), w.arten(), werte, po, see), Ablauf.textur(Ablauf.RECHNEN, leer, leer), Ablauf.schreibt(aus)));
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            float[] karte = aus.floats(9 * n);
            return new Ergebnis(karte, prozessor(w, ost, nord, orte, fuss));
        }
    }

    /** Dasselbe auf dem Prozessor in double. */
    public static double[] prozessor(Wasserbild w, double ost, double nord, float[] orte, double fuss) {
        int n = orte.length / 2;
        double[] r = new double[9 * n];
        List<Seegangsnetz> netze = w.seeNetze();
        double[][] lang = Seegangsoptik.wellen();
        double[] baender = Seegangsoptik.baender(), cdf = Seegangsoptik.cdf();
        for (int i = 0; i < n; i++) {
            double x = orte[2 * i], z = orte[2 * i + 1];
            double[] v = werte(netze, w.zeit, ost + x, nord - z);
            if (v == null) continue;
            System.arraycopy(v, 0, r, 9 * i, 6);
            double[] q = Seegangsoptik.neigung(v[0], v[1], Math.atan2(v[3], v[2]), fuss, lang, baender, cdf);
            r[9 * i + 6] = q[1];
            r[9 * i + 7] = Math.max(0, v[4] - q[1]);
            r[9 * i + 8] = 1;
        }
        return r;
    }

    /** Die sechs Werte am Ort (O, N in m) im feinsten Netz, bilinear zwischen den Zellmitten, linear in der Zeit; null ohne Netz. */
    static double[] werte(List<Seegangsnetz> netze, double zeit, double ost, double nord) { return Seegangsoptik.werte(netze, zeit, ost, nord); }
}
