package com.dan.render.water;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.szene.Katzenpfoten;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Prüfstein 37 von S8b (Seegang Werkbuch, Datei 104): die Katzenpfoten im Shader ({@code katzen.glsl} über
 * {@code katzenprobe.comp}) gegen {@link Katzenpfoten} auf dem Prozessor, mit dem Kopf, den {@link Wasserbild} für das
 * letzte Bild geschrieben hat ({@link Wasserbild#katzenKopf}).
 */
public final class Katzenprobe {
    private Katzenprobe() { }

    /** Ergebnis: je Ort {Wind am Ort, Neigungsvarianz} auf der Karte und auf dem Prozessor. */
    public record Ergebnis(float[] karte, double[] prozessor) { }

    /** Rechnet an den Orten (x Ost, z Süd relativ zum Auge des letzten Bilds). */
    public static Ergebnis rechne(Geraet g, Konto k, Wasserbild w, float[] orte) {
        int n = orte.length / 2;
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        try (Kernel kp = Kernel.lade(g, "/com/dan/render/water/katzenprobe.comp", 16, null, P, P, P, P, X, X, P, P);
             Abtaster at = new Abtaster(g, false, false);
             Bild leer = new Bild(g, k, "Probe Katzen, leer", 4, 4, Bild.RGBA32F);
             Puffer werte = Puffer.speicher(g, k, "Probe Katzen, Werte", 60 * 4);
             Puffer po = Puffer.speicher(g, k, "Probe Katzen, Orte", 8L * n);
             Puffer aus = Puffer.speicher(g, k, "Probe Katzen, Aus", 8L * n)) {
            leer.schreibe(new float[4 * 4 * 4]);
            po.schreibe(orte);
            Ablauf a = new Ablauf(g, g.grafik);
            ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            p.putInt(0, n);
            a.rechne("Probe Katzen", kp, kp.satz(w.kopf(), w.staende(), w.arten(), werte, at.an(leer), at.an(leer), aus, po), p, Kernel.gruppen(n, 64), 1, 1,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, w.kopf(), w.staende(), w.arten(), werte, po), Ablauf.textur(Ablauf.RECHNEN, leer, leer), Ablauf.schreibt(aus)));
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            return new Ergebnis(aus.floats(2 * n), prozessor(w.katzenKopf, orte));
        }
    }

    /** Dasselbe auf dem Prozessor in double aus dem Kopf {qx, qy, τ, σ, U, 1/L, Gewicht}. */
    public static double[] prozessor(double[] kk, float[] orte) {
        int n = orte.length / 2;
        double[] r = new double[2 * n];
        for (int i = 0; i < n; i++) {
            double x = orte[2 * i], z = orte[2 * i + 1];
            double u = kk[3] <= 0 ? kk[4] : Katzenpfoten.windOrt(kk[0] + x * kk[5], kk[1] - z * kk[5], kk[2], kk[4], kk[3]);
            double cm = com.dan.core.szene.Wasseroptik.coxMunk(kk[4]);
            double ort = Katzenpfoten.sigma2Ort(u);
            r[2 * i] = u;
            r[2 * i + 1] = kk[3] <= 0 || kk[6] <= 0 ? cm : (1 - kk[6]) * cm + kk[6] * ort;
        }
        return r;
    }
}
