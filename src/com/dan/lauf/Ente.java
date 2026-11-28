package com.dan.lauf;

import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Wasser;

import java.util.Random;

/**
 * Seit S7e2 (Seegang Werkbuch 101, SF151 A): eine Eiderente in ihrem Trupp. Sie hält ihren Platz gegen Strom und Wind mit
 * einem kleinen Antrieb ({@link Koerper#antriebOst}, nach einem PI-Regler auf die Geschwindigkeit über Grund, höchstens
 * {@link #KRAFT_MAX}) und schwimmt mit dem Kopf voran; sie taucht alle {@link #TAUCH_AB}–{@link #TAUCH_BIS} s für
 * {@link #UNTER_AB}–{@link #UNTER_BIS} s (dann fehlt sie im Bild und wird nicht gerechnet) und taucht 0–3 m weiter auf.
 * Bei Hs über {@link #HS_MAX}, bei Wind ab {@link Lotse#WIND_MAX} und unter {@link #TIEFE_MIN} Wasser ist sie nicht da; treibt
 * sie weiter als {@link #ABSTAND_MAX} von ihrem Platz, fliegt sie zurück (fehlt {@link #FLUG_S} s). Im Zeitraffer taucht
 * sie nicht. Alle Werte [G].
 */
final class Ente {
    static final double HS_MAX = 1.0, TIEFE_MIN = 0.4, V_MAX = 0.6, KRAFT_MAX = 8, TAU_P = 8, TAU_V = 2, TAU_I = 10, ABSTAND_MAX = 40, FLUG_S = 15;
    static final double TAUCH_AB = 30, TAUCH_BIS = 60, UNTER_AB = 20, UNTER_BIS = 40;

    final double heimX, heimY;
    private final Random zufall;
    /** Grund am Platz (m NHN), im Faden der Flotte gesetzt; NaN: noch nicht gefragt. */
    double grund = Double.NaN;
    private double tauchtAb = Double.NaN, auftauchen = Double.NaN, ix, iy;
    private boolean da = true, heim;
    /** Warum sie fehlt (leer: sie ist da oder taucht). */
    String grundWeg = "";

    Ente(double heimX, double heimY, long saat) {
        this.heimX = heimX;
        this.heimY = heimY;
        zufall = new Random(saat * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);   // gestreut: nahe Saaten gäben fast gleiche erste Zahlen
    }

    private double zwischen(double a, double b) { return a + (b - a) * zufall.nextDouble(); }

    /**
     * Ob sie zur Zeit t (s der Szene) fehlt; echt: Zeit der Szene (sonst Zeitraffer, ohne Tauchen); hs (m), wind (m/s), pegel
     * (m NHN) an ihrem Platz; o: ihr Ort jetzt. Kommt sie wieder, setzt sie k ins Wasser wa (an den Platz oder nah beim Ort).
     */
    boolean weg(double t, boolean echt, double hs, double wind, double pegel, Koerper k, Wasser wa) {
        double tiefe = Double.isNaN(grund) ? Double.POSITIVE_INFINITY : pegel - grund;
        boolean geht = hs <= HS_MAX && wind < Lotse.WIND_MAX && tiefe >= TIEFE_MIN;
        if (!geht) {
            da = false;
            auftauchen = Double.NaN;
            grundWeg = hs > HS_MAX ? "Seegang" : wind >= Lotse.WIND_MAX ? "Sturm" : "trocken";
            return true;
        }
        grundWeg = "";
        double[] o = k.lage();
        if (!da) { da = true; heim = true; auftauchen = t; }                      // zurück an den Platz
        else if (Double.isNaN(auftauchen) && Math.hypot(o[0] - heimX, o[1] - heimY) > ABSTAND_MAX) { heim = true; auftauchen = t + FLUG_S; }
        // springt die Zeit (neues Szenario, Zeitraffer), beginnt der Takt neu
        if (Double.isNaN(tauchtAb) || t < tauchtAb - 2 * TAUCH_BIS || t > tauchtAb + 2 * UNTER_BIS) tauchtAb = t + zwischen(0, TAUCH_BIS);
        if (!echt && !heim) auftauchen = Double.NaN;
        if (!Double.isNaN(auftauchen)) {
            if (t < auftauchen) return true;
            // auf: am Platz oder 0–3 m vom Ort, still, mit neuem Kurs
            double r = zwischen(0, 3), a = zwischen(0, 2 * Math.PI);
            double x = heim ? heimX : o[0] + r * Math.sin(a), y = heim ? heimY : o[1] + r * Math.cos(a);
            k.einschwimmen(wa, x, y, zwischen(0, 360), 0, 0, t);
            k.geschwindigkeit(0, 0, 0);
            ix = 0; iy = 0;
            auftauchen = Double.NaN;
            heim = false;
            tauchtAb = t + zwischen(TAUCH_AB, TAUCH_BIS);
            return false;
        }
        if (echt && t >= tauchtAb) { auftauchen = t + zwischen(UNTER_AB, UNTER_BIS); return true; }
        return false;
    }

    /**
     * Seit S8b (Seegang Werkbuch 104, SF163 A): ein Schiff kommt – sie taucht ab, wenn sie da ist und nicht schon taucht, für
     * {@link #UNTER_AB}–{@link #UNTER_BIS} s, und taucht 0–3 m weiter wieder auf. Gibt zurück, ob sie eben abtauchte.
     */
    boolean abtauchen(double t) {
        if (!da || !Double.isNaN(auftauchen)) return false;
        auftauchen = t + zwischen(UNTER_AB, UNTER_BIS);
        return true;
    }

    /** Antrieb für einen Schritt von dt s: auf ihren Platz zu, höchstens {@link #V_MAX} über Grund, mit dem Kopf voran. */
    void antrieb(Koerper k, double dt) {
        double[] o = k.lage(), v = k.geschwindigkeit();
        double sx = (heimX - o[0]) / TAU_P, sy = (heimY - o[1]) / TAU_P, s = Math.hypot(sx, sy);
        if (s > V_MAX) { sx *= V_MAX / s; sy *= V_MAX / s; }
        double ex = sx - v[0], ey = sy - v[1];
        ix += k.masse * ex / TAU_I * dt;
        iy += k.masse * ey / TAU_I * dt;
        double il = Math.hypot(ix, iy);
        if (il > KRAFT_MAX) { ix *= KRAFT_MAX / il; iy *= KRAFT_MAX / il; }
        double fx = k.masse * ex / TAU_V + ix, fy = k.masse * ey / TAU_V + iy, f = Math.hypot(fx, fy);
        if (f > KRAFT_MAX) { fx *= KRAFT_MAX / f; fy *= KRAFT_MAX / f; f = KRAFT_MAX; }
        k.antriebOst = fx;
        k.antriebNord = fy;
        k.kursSoll = f > 0.3 ? (Math.toDegrees(Math.atan2(fx, fy)) + 360) % 360 : Double.NaN;
    }
}
