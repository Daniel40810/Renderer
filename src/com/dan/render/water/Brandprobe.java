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
 * Prüfsteine 15 und 18 von P13c (Werkbuch HalligHooge 12.14): die Höhe der langen Wellen und die Schaumköpfe im Shader
 * ({@code seegang.glsl} über {@code brandprobe.comp}) gegen dieselben Formeln auf dem Prozessor ({@link Seegangsoptik}).
 * Die Karte liest den Kopf, den {@link Wasserbild} für das letzte Bild geschrieben hat (mit {@code brandung} an).
 */
public final class Brandprobe {
    private Brandprobe() { }

    /** Je Ort {ζ, ζ_s, ∂Ost, ∂Nord, m0, m0 aufgelöst, Schaum, da} auf der Karte und dieselben ohne Schaum auf dem Prozessor. */
    public record Ergebnis(float[] karte, double[] prozessor) { }

    public static Ergebnis rechne(Geraet g, Konto k, Wasserbild w, double ost, double nord, float[] orte, float fuss, float h) {
        int n = orte.length / 2;
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        try (Kernel kp = Kernel.lade(g, "/com/dan/render/water/brandprobe.comp", 16, null, P, P, P, P, X, X, P, P, P);
             Abtaster at = new Abtaster(g, false, false);
             Bild leer = new Bild(g, k, "Probe Brandung, leer", 4, 4, Bild.RGBA32F);
             Puffer werte = Puffer.speicher(g, k, "Probe Brandung, Werte", 60 * 4);
             Puffer po = Puffer.speicher(g, k, "Probe Brandung, Orte", 8L * n);
             Puffer aus = Puffer.speicher(g, k, "Probe Brandung, Aus", 32L * n)) {
            leer.schreibe(new float[4 * 4 * 4]);
            po.schreibe(orte);
            Ablauf a = new Ablauf(g, g.grafik);
            ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            p.putInt(0, n).putInt(4, Float.floatToRawIntBits(fuss)).putInt(8, Float.floatToRawIntBits(h));
            Puffer see = w.seegangPuffer();
            a.rechne("Probe Brandung", kp, kp.satz(w.kopf(), w.staende(), w.arten(), werte, at.an(leer), at.an(leer), aus, po, see), p, Kernel.gruppen(n, 64), 1, 1,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, w.kopf(), w.staende(), w.arten(), werte, po, see), Ablauf.textur(Ablauf.RECHNEN, leer, leer), Ablauf.schreibt(aus)));
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            return new Ergebnis(aus.floats(8 * n), prozessor(w, ost, nord, orte, fuss, h));
        }
    }

    /** Dasselbe auf dem Prozessor in double (ohne den Schaum, dessen Muster auf der Karte Zufall ist). */
    public static double[] prozessor(Wasserbild w, double ost, double nord, float[] orte, double fuss, double h) {
        int n = orte.length / 2;
        double[] r = new double[8 * n];
        List<Seegangsnetz> netze = w.seeNetze();
        double[][] lang = Seegangsoptik.wellen();
        double[] baender = Seegangsoptik.baender(), cdf = Seegangsoptik.cdf();
        for (int i = 0; i < n; i++) {
            double x = orte[2 * i], z = orte[2 * i + 1];
            double[] v = Seegangsoptik.werte(netze, w.zeit, ost + x, nord - z);
            if (v == null) continue;
            double[] a = Seegangsoptik.amplituden(v[0], v[1], Math.atan2(v[3], v[2]), fuss, lang, baender, cdf);
            double[] q = Seegangsoptik.hoehe(a, lang, ost + x, nord - z, w.zeit);
            r[8 * i] = q[0];
            r[8 * i + 1] = Seegangsoptik.kamm(q[0], Math.sqrt(a[Seegangsoptik.LANG + 1]), Seegangsoptik.beta(v[0], h))[0];
            r[8 * i + 2] = q[1];
            r[8 * i + 3] = q[2];
            r[8 * i + 4] = a[Seegangsoptik.LANG];
            r[8 * i + 5] = a[Seegangsoptik.LANG + 1];
            r[8 * i + 6] = Math.min(1, Math.max(0, v[5] / Seegangsoptik.D_KAL));
            r[8 * i + 7] = 1;
        }
        return r;
    }
}
