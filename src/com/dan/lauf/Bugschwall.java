package com.dan.lauf;

import com.dan.physics.auftrieb.Bugschlag;
import com.dan.physics.auftrieb.Form;
import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Wasser;
import com.dan.physics.gischt.Buendel;


import java.util.ArrayList;
import java.util.List;

/**
 * Gischt am Bug (Seegang Werkbuch, Datei 104, S8a, SF111 A, SF158 A): Jeder Bugschlag nach Ochi ({@link Bugschlag}) wirft
 * einen Schwall nach {@code com.dan.spray} – zwei Bündel Spritzwasser, an Backbord und Steuerbord vom Bug weg, und ab einem
 * kräftigen Schlag ein Bündel Sprühnebel, das mit dem Wind treibt. Der Kern führt nur die Bündel ({@link Buendel}); jeder
 * Tropfen ist eine Formel aus Bündel, Nummer und Alter, dieselbe auf dem Prozessor und im Shader
 * ({@code render.schwimmer.gischt.glsl}).
 * <ul>
 * <li><b>Wurf</b> [G]: Die Fahrt gegen das Wasser am Bug beim Eintauchen v (m/s, aus {@link Bugschlag#letzteV}) gibt die
 * Geschwindigkeit der Wurzel des Schwalls, v_j = {@link #JET} · v (nach Wagner wächst sie mit dem Kotangens der Aufkimmung;
 * der Faktor 2 gilt für einen Bug um 25° [G]). Das Wasser verlässt den Rumpf nach außen und um {@link #WINKEL} nach oben und
 * nimmt die Fahrt des Schiffs mit.</li>
 * <li><b>Menge:</b> Größe und Streuung des Bündels wachsen mit v_j; das Bündel lebt, bis seine Tropfen zurückgefallen sind.</li>
 * <li><b>Raum</b> der Bündel: x Ost, y Höhe (NHN), z = −Nord (wie com.dan.spray: z nach Süden).</li>
 * </ul>
 */
public final class Bugschwall {
    public static final String VERSION = "0.1";
    /** Wurzel des Schwalls: v_j = JET · v [G]; nach außen und um WINKEL Grad nach oben [G]. */
    public static final double JET = 2.0, WINKEL = 40;
    /** Ab dieser Wurfgeschwindigkeit (m/s) dazu ein Bündel Sprühnebel [G]. */
    public static final double NEBEL_AB = 4.0;
    /** Höchstens so viele Bündel [G] (die ältesten fallen weg). */
    public static final int MAX = 96;

    private final List<Buendel> buendel = new ArrayList<>();
    private int saat = 1885, schlaege;

    /** Zahl der Schläge mit Schwall seit dem Anfang. */
    public synchronized int schlaege() { return schlaege; }

    /** Die Bündel (Kopie), für Prüfungen. */
    public synchronized List<Buendel> liste() { return new ArrayList<>(buendel); }

    /** Alles weg (neues Szenario, Sprung der Zeit). */
    public synchronized void leeren() { buendel.clear(); }

    /**
     * Ein Schlag des Körpers k zur Zeit t (s der Szene): wirft den Schwall. wa: das Wasser (Höhe der Fläche am Bug); wu, wv:
     * Wind (m/s, nach Ost und Nord). Nur nach einem Schlag ({@link Bugschlag#pruefe} gab true) aufrufen.
     */
    public synchronized void schlag(Koerper k, Bugschlag b, Wasser wa, double t, double wu, double wv) {
        double v = b.letzteV;
        if (!(v > 0)) return;
        double vj = JET * v;
        double halb = k.form instanceof Form.Rumpf r ? r.halbeBreite(b.x, Math.max(b.z, r.tiefgang)) : 0.5 * (k.form.kasten()[3] - k.form.kasten()[2]);
        double[] ka = k.form.kasten();
        double laenge = ka[1] - ka[0];
        double[] vs = k.geschwindigkeit();
        double kurs = Math.toRadians(k.kurs());
        double fo = Math.sin(kurs), fn = Math.cos(kurs);                     // vorn nach Ost, Nord
        double wi = Math.toRadians(WINKEL), ca = Math.cos(wi), sa = Math.sin(wi);
        double life = 2 * vj * sa / Buendel.G + 1.2;
        double groesse = 0.035 + 0.006 * vj, streuOrt = 0.06 * laenge, streuV = 0.25 * vj;
        for (int seite = -1; seite <= 1; seite += 2) {
            // nach außen: Backbord (y > 0) ist links vom Kurs; quer nach Backbord (Ost, Nord) = (−cos, sin)
            double qo, qn;                                                     // seite −1: Backbord (links vom Kurs), +1: Steuerbord
            if (seite < 0) { qo = -fn; qn = fo; } else { qo = fn; qn = -fo; }
            double[] p = k.punktWelt(b.x, seite < 0 ? halb : -halb, b.z);
            double eta = wa.pegel(p[0], p[1], t) + welle(wa, p[0], p[1], t);
            double vo = vs[0] + vj * ca * qo, vn = vs[1] + vj * ca * qn, vu = vs[2] + vj * sa;
            neu(new Buendel(Buendel.SPLASH, p[0], eta, -p[1], t, vo, vu, -vn, life, Buendel.SPLASH_K, streuOrt, streuV, 1, ++saat * 0x9E3779B1,
                    groesse, eta - 0.05, vj * halb, fo, -fn, qo, -qn, wu, -wv, 0.15));
            if (vj >= NEBEL_AB) {
                double[] pm = k.punktWelt(b.x, (seite < 0 ? 1 : -1) * (halb + 1.0), b.z);
                neu(new Buendel(Buendel.MIST, pm[0], eta + 0.6 * vj * sa * 0.5, -pm[1], t + 0.15, vs[0] + 1.0 * qo + 0.2 * wu, 0.15 * vj * sa, -(vs[1] + 1.0 * qn + 0.2 * wv),
                        4 + 0.3 * vj, 0.9, Math.min(3, 0.6 + 0.12 * vj), 0.5 + 0.05 * vj, 0.03, ++saat * 0x9E3779B1, 0.8 + 0.06 * vj, -1e9,
                        0.3 * vj * halb, fo, -fn, qo, -qn, wu, -wv, 0.8));
            }
        }
        schlaege++;
    }

    private static double welle(Wasser wa, double o, double n, double t) {
        double[] w = new double[6];
        wa.welle(o, n, 0, t, w);
        return w[0];
    }

    private void neu(Buendel p) {
        buendel.add(p);
        while (buendel.size() > MAX) buendel.remove(0);
    }

    /**
     * Die lebenden Bündel zur Zeit ts (s der Szene) für {@code render.schwimmer.Gischtbild}: je Bündel 24 double (Ort beim
     * Wurf Ost, Höhe, −Nord; Alter; Geschwindigkeit; Lebensdauer; k, Streuung Ort, Geschwindigkeit, Schwerkraft; Saat, Größe,
     * Höhe des Wassers, Masse; quer, nach außen; Wind, Dauer des Wurfs, Art). Tote fallen weg; vor dem Wurf liegende auch
     * (die Zeit lief zurück).
     */
    public synchronized double[] buendel(double ts) {
        buendel.removeIf(p -> !p.alive(ts) || ts < p.t0() - 1);
        double[] r = new double[24 * buendel.size()];
        int o = 0;
        for (Buendel p : buendel) {
            double[] v = {p.x(), p.y(), p.z(), ts - p.t0(), p.vx(), p.vy(), p.vz(), p.life(), p.k(), p.spreadPos(), p.spreadVel(), p.grav(),
                    p.seed(), p.size(), p.waterY(), p.mass(), p.tx(), p.tz(), p.nx(), p.nz(), p.wx(), p.wz(), p.burst(), p.kind()};
            System.arraycopy(v, 0, r, o, 24);
            o += 24;
        }
        return r;
    }
}
