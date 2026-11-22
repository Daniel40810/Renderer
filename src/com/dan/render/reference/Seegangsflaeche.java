package com.dan.render.reference;

import com.dan.core.szene.Seegangsnetz;
import com.dan.core.szene.Seegangsoptik;

import java.util.List;

/**
 * Der örtliche Seegang für die Referenz (P8c, Werkbuch HalligHooge Teil A, 12.9, F77 A): dieselben Netze und dieselben
 * langen Wellen wie das Bild (Stufe 2, {@link Seegangsoptik}), in double und ohne Filter nach Bildpunkten. Am Ort auf dem
 * Wasser die Neigung aller langen Wellen (die Referenz zeigt die Fläche, wie sie ist) und als zusätzliche Neigungsvarianz
 * der Facetten der Rest max(0, mss − aufgelöst), wie im Bild; Cox und Munk aus dem Wind trägt die Referenz schon selbst.
 */
public final class Seegangsflaeche {
    /** Kleiner als jede Welle: alle langen Wellen zählen (g = 1). */
    static final double FUSS = 1e-6;
    final List<Seegangsnetz> netze;
    final double zeit;
    final double[][] wellen = Seegangsoptik.wellen();
    final double[] grenzen = Seegangsoptik.baender(), tafel = Seegangsoptik.cdf();

    /** {@code netze}: fein zuerst (wie {@code Wasserbild.seeNetze}); {@code zeit}: Modellzeit des Bilds (s). */
    public Seegangsflaeche(List<Seegangsnetz> netze, double zeit) {
        this.netze = List.copyOf(netze);
        this.zeit = zeit;
    }

    /**
     * Seit P10d (F103 A, Messung f): die langen Wellen als Facetten statt als gekippte Normale – ihre Neigungsvarianz geht
     * ganz in die Facetten (mit Verdeckung), die Fläche bleibt eben. Eine gekippte Normale auf ebener Fläche spiegelt nah am
     * Auge zu hell (im Sturm 10–14 %, Datei 44). false: wie bis P10c.
     */
    public boolean facetten = true;

    /** {∂η/∂Ost, ∂η/∂Nord, zusätzliche Neigungsvarianz} am Ort (UTM); null, wo kein Netz liegt. */
    public double[] am(double ost, double nord) {
        double[] v = Seegangsoptik.werte(netze, zeit, ost, nord);
        if (v == null) return null;
        if (!(v[0] > 0) || !(v[1] > 0)) return new double[] {0, 0, Math.max(0, v[4])};
        double[] q = Seegangsoptik.neigungAm(v[0], v[1], Math.atan2(v[3], v[2]), FUSS, ost, nord, zeit, wellen, grenzen, tafel);
        if (facetten) return new double[] {0, 0, Math.max(v[4], q[2])};
        return new double[] {q[0], q[1], Math.max(0, v[4] - q[2])};
    }
}
