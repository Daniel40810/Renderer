package com.dan.render.reference;

import com.dan.core.szene.Gestalt;

import java.util.HashMap;
import java.util.Map;

/**
 * Die Gestaltkarte für die Referenz (seit P12c3, Werkbuch HalligHooge Teil A, 12.13, Datei 81; F138 A, F143 A): dieselbe
 * Regel wie {@code gelaende.frag}, aber ohne Muster (ihr Mittel ist 1) und ohne Ausblenden mit dem Fuß des Bildpunkts.
 * <ul>
 * <li>Vier Texel um den Ort, bilinear gewichtet (Texelmitten bei (i + ½) · Zelle).</li>
 * <li><b>Wasser:</b> Graben und Priel, wo das Gelände höchstens 5 cm über dem Stand liegt (F134 A), Fething immer. Sein
 * Gewicht ist der Anteil des Spiegels ({@link Szene.Treffer#spiegel}).</li>
 * <li><b>Wege:</b> die Albedo des Belags im Mittel, gemischt mit dem Gewicht der Wege (der Belag des stärksten Texels).</li>
 * <li>Gras und Deckwerk ändern das Mittel nicht.</li>
 * </ul>
 */
public final class Gestaltfeld {
    /** Spiegel des Gestaltwassers wie {@code gelaende.frag}: Fresnel f0 (n = 1,33), Beckmann m² und Albedo des Körpers [G]. */
    public static final double F0 = 0.02, ALPHA2 = 0.006;
    public static final double[] KOERPER = {0.03, 0.035, 0.025};

    public final Gestalt gestalt;
    private final Map<Long, Gestalt.Kachel> kacheln = new HashMap<>();
    private final double s;

    public Gestaltfeld(Gestalt g) {
        this.gestalt = g;
        this.s = g.kantenlaenge();
        for (Gestalt.Kachel k : g.kacheln) kacheln.put(schluessel(Math.floorDiv(Math.round(k.ost()), Math.round(s)), Math.floorDiv(Math.round(k.nord()), Math.round(s))), k);
    }

    private static long schluessel(long i, long j) { return (i << 32) ^ (j & 0xFFFFFFFFL); }

    /** Art und Byte des Wasserstands im Texel, dessen Mitte (ost, nord) ist; {0, 0} außerhalb. */
    int[] texel(double ost, double nord, int[] aus) {
        Gestalt.Kachel k = kacheln.get(schluessel((long) Math.floor(ost / s), (long) Math.floor(nord / s)));
        if (k == null) {
            aus[0] = 0;
            aus[1] = 0;
            return aus;
        }
        int n = gestalt.n;
        int i = (int) Math.floor((ost - k.ost()) / gestalt.zelle), j = (int) Math.floor((k.nord() + s - nord) / gestalt.zelle);
        i = Math.max(0, Math.min(n - 1, i));
        j = Math.max(0, Math.min(n - 1, j));
        int o = 2 * (j * n + i);
        aus[0] = k.daten()[o] & 0xFF;
        aus[1] = k.daten()[o + 1] & 0xFF;
        return aus;
    }

    /** Mittlere Albedo eines Belags (linear RGB) wie {@code belag()} in {@code gelaende.frag}; grund: das Material darunter. */
    static void belag(int art, double[] grund, double[] aus) {
        switch (art) {
            case Gestalt.ASPHALT -> set(aus, 0.075, 0.075, 0.08);
            case Gestalt.KLINKER -> set(aus, 0.20, 0.085, 0.06);
            case Gestalt.SCHOTTER -> set(aus, 0.24, 0.22, 0.19);
            case Gestalt.BETON -> set(aus, 0.30, 0.29, 0.27);
            case Gestalt.GRASWEG -> set(aus, grund[0] * 1.15, grund[1], grund[2] * 0.8);
            case Gestalt.HOLZ -> set(aus, 0.12, 0.10, 0.07);
            default -> set(aus, grund[0], grund[1], grund[2]);
        }
    }

    private static void set(double[] a, double r, double g, double b) {
        a[0] = r;
        a[1] = g;
        a[2] = b;
    }

    /**
     * Die Gestalt am Ort (UTM) mit der Höhe des Geländes hWelt (m NHN, ohne die Senkung): ändert die Albedo für Wege und
     * gibt den Anteil des Gestaltwassers zurück (0 … 1).
     */
    public double anwenden(double ost, double nord, double hWelt, double[] albedo) {
        double z = gestalt.zelle;
        double u = ost / z - 0.5, v = nord / z - 0.5;
        double i0 = Math.floor(u), j0 = Math.floor(v), fx = u - i0, fy = v - j0;
        double wW = 0, wB = 0, bestB = 0;
        int belagArt = 0;
        int[] t = new int[2];
        for (int k = 0; k < 4; k++) {
            int ox = k & 1, oy = k >> 1;
            texel((i0 + ox + 0.5) * z, (j0 + oy + 0.5) * z, t);
            double w = (ox == 0 ? 1 - fx : fx) * (oy == 0 ? 1 - fy : fy);
            int art = t[0];
            if (Gestalt.wasser(art)) {
                double stand = Gestalt.stand(t[1]);
                if (art == Gestalt.FETHING || hWelt <= stand + 0.05) wW += w;
            } else if (art >= Gestalt.ASPHALT) {
                wB += w;
                if (w > bestB) {
                    bestB = w;
                    belagArt = art;
                }
            }
        }
        if (wB > 0) {
            double[] b = new double[3];
            belag(belagArt, albedo, b);
            for (int c = 0; c < 3; c++) albedo[c] += (b[c] - albedo[c]) * wB;
        }
        return Math.min(1, wW);
    }

    /** Anteil des Gestaltwassers am Ort (für die Prüfung). */
    public double wasser(double ost, double nord, double hWelt) {
        return anwenden(ost, nord, hWelt, new double[3]);
    }
}
