package com.dan.lauf;

import com.dan.physics.gischt.Buendel;
import com.dan.physics.scheibe.Scheibe;

import java.util.List;

/**
 * Die Scheibe vor der Kamera an Bord und im Schwimmerblick (Seegang Werkbuch, Datei 104, S8a, SF111 A, SF158 A): Tropfen nach
 * {@code com.dan.wetglass} – Kugelkappen mit 60° Randwinkel, die haften, ab {@link Scheibe#A_CRIT} laufen, verschmelzen und
 * eine Spur ziehen. Was sie trifft:
 * <ul>
 * <li><b>Gischt:</b> Jeder Tropfen der Gischt am Bug ({@link Bugschwall}), dessen Weg im letzten Bild näher als
 * {@link #TREFFER} am Auge vorbeiführt, setzt einen Tropfen Seewasser auf die Scheibe [G: ein gezeichneter Tropfen steht für
 * eine Wolke kleiner]. Ort auf der Scheibe und Größe (0,8–2,5 mm) aus seinem Zufall [G].</li>
 * <li><b>Überspülung</b> (seit S8d): {@link #ueberspuelen} legt eine Haut über die Scheibe (wie {@code wetglass.flood}; auf
 * einer senkrechten Scheibe läuft eine Haut von 0,25 mm rund 9 s, bevor sie zerreißt). Für die kleine Linse des
 * Schwimmerblicks {@link #auftauchen}: Die Haut ist in einem Augenblick abgelaufen, zurück bleiben Tropfen [G].</li>
 * </ul>
 * Die Schwerkraft zieht längs der Scheibe nach unten im Raum, also mit dem Rollen des Rumpfs gedreht.
 */
public final class Linsenglas {
    public static final String VERSION = "0.1";
    /** Scheibe vor der Kamera (m) [G]: 16 : 9, so breit wie die Frontlinse einer Kamera an Bord mit Gehäuse. */
    public static final double BREITE = 0.064, HOEHE = 0.036;
    /** Ein Tropfen der Gischt trifft, wenn sein Weg so nah (m) am Auge vorbeiführt [G]. */
    public static final double TREFFER = 0.15;
    /** Nur Bündel mit ihrer Mitte so nah (m) am Auge werden Tropfen für Tropfen geprüft [G]. */
    public static final double NAH = 6;

    public final Scheibe glas = new Scheibe();
    private int treffer;

    public Linsenglas(int saat) {
        glas.width = BREITE;
        glas.height = HOEHE;
        glas.periodic = false;
        glas.drainLength = HOEHE;
        glas.quality = Scheibe.Quality.LOW;
        glas.seed = saat;
        glas.stepRate = 60;
        glas.reset();
    }

    /** Treffer seit dem Anfang (für Prüfungen). */
    public int treffer() { return treffer; }

    /**
     * Ein Bild weiter: dt (s, Zeit der Szene; 0 im Standbild), ts (s der Szene), das Auge (Ost, Nord, Höhe), Rollen des
     * Rumpfs (Grad, Backbord hoch positiv), die Gischt (null: keine).
     */
    public void schritt(double dt, double ts, double[] auge, double rollen, Bugschwall gischt) {
        if (gischt != null && dt > 0 && dt < 1) treffen(ts - dt, ts, auge, gischt.liste());
        double r = Math.toRadians(rollen);
        glas.slopeX = Math.sin(r);
        glas.slopeY = -Math.cos(r);
        if (dt > 0 && dt < 1) glas.step(dt);
    }

    /** Tropfen der Gischt, die zwischen t0 und t1 nah am Auge vorbeifliegen, setzen Tropfen auf die Scheibe. */
    void treffen(double t0, double t1, double[] auge, List<Buendel> bu) {
        double ex = auge[0], ey = auge[2], ez = -auge[1];                   // im Raum der Bündel: Ost, Höhe, −Nord
        for (Buendel p : bu) {
            if (p.kind() != Buendel.SPLASH || !p.alive(t1) || t1 < p.t0()) continue;
            double[] c = p.center(t1);
            double weit = p.spreadPos() + p.spreadVel() * Math.max(0, t1 - p.t0()) + NAH;
            if (Math.hypot(Math.hypot(c[0] - ex, c[1] - ey), c[2] - ez) > weit) continue;
            int n = com.dan.render.schwimmer.Gischtbild.TROPFEN;
            for (int i = 0; i < n; i++) {
                double[] a = p.particle(i, t0), b = p.particle(i, t1);
                if (b[4] < 0.5 && a[4] < 0.5) continue;
                if (abstand(ex, ey, ez, a, b) > TREFFER) continue;
                double u = Buendel.rnd(p.seed(), i, 12), v = Buendel.rnd(p.seed(), i, 13), d = 0.8 + 1.7 * Buendel.rnd(p.seed(), i, 14);
                glas.spray(u * BREITE, v * HOEHE, d);
                treffer++;
            }
        }
    }

    /** Abstand des Punkts e von der Strecke a–b (je {x, y, z, …}). */
    static double abstand(double ex, double ey, double ez, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double l2 = dx * dx + dy * dy + dz * dz;
        double s = l2 > 1e-12 ? Math.max(0, Math.min(1, ((ex - a[0]) * dx + (ey - a[1]) * dy + (ez - a[2]) * dz) / l2)) : 0;
        double qx = a[0] + s * dx - ex, qy = a[1] + s * dy - ey, qz = a[2] + s * dz - ez;
        return Math.sqrt(qx * qx + qy * qy + qz * qz);
    }

    /** Seit S8d: eine Haut Seewasser über die ganze Scheibe (das Auge war unter Wasser), mm. */
    public void ueberspuelen(double mm) { glas.flood(mm, true); }

    /**
     * Seit S8d (Befund L6b: mit der Haut kamen in 60 s keine Tropfen zustande, weil der Kopf alle 3,2 s wieder untertaucht): die
     * Scheibe taucht auf; die Haut ist abgelaufen, n Tropfen Seewasser bleiben zurück, 0,6–2,4 mm, gleichmäßig verteilt [G].
     */
    public void auftauchen(int n, long saat) {
        java.util.Random r = new java.util.Random(saat);
        for (int i = 0; i < n; i++) {
            double u = r.nextDouble();
            glas.spray(r.nextDouble() * BREITE, r.nextDouble() * HOEHE, 0.6 + 1.8 * u * u);
        }
    }

    /**
     * Seit S8d: die Scheibe liegt zum Teil unter Wasser – Tiefe ihrer Mitte unter der Fläche (m) und Zunahme je m nach rechts
     * und oben; Tropfen mit der Mitte darunter nimmt das Wasser auf.
     */
    public void unterWasser(double tiefe, double jeRechts, double jeOben) {
        glas.entferne((x, y) -> tiefe + jeRechts * (x - 0.5 * BREITE) + jeOben * (y - 0.5 * HOEHE) > 0);
    }

    /** Die Tropfen für {@code render.frame.Linse}: je Tropfen 8 float, ihre Zahl, die Haut (m). */
    public Object[] fuerBild() {
        Scheibe.Snapshot s = glas.snapshot();
        return new Object[] {s.drops(), s.count(), s.sheetMm() * 1e-3};
    }
}
