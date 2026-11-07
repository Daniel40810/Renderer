package com.dan.shutter;

import java.util.Locale;

/**
 * Der Verschluss einer Filmkamera: die Umlaufblende. Eine Scheibe mit einem Ausschnitt dreht sich einmal je Filmbild vor
 * dem Bildfenster; solange der Ausschnitt vor dem Fenster steht, wird belichtet, solange die Scheibe davor steht, schaltet
 * der Greifer das nächste Bild weiter. Bei 180° Öffnung und 24 Bildern je Sekunde belichtet jedes Bild
 * T = 180/360 · 1/24 = 1/48 s.
 * <p><b>Öffnungsgang</b>: Die Kante der Scheibe ist ein Halbmesser um die Achse. Sie überstreicht das Fenster nicht auf
 * einmal, sondern in der Zeit, in der sich die Scheibe um den Winkel dreht, unter dem das Fenster von der Achse aus
 * erscheint. Ein Punkt (x, y) des Fensters (mm, Achse bei (0, −R)) öffnet, wenn die Kante seinen Polarwinkel
 * φ = atan2(x, y + R) erreicht: t_auf = φ / (2π · Bildrate), und schließt T später. So beginnt die Belichtung am einen Rand
 * des Bilds einige Millisekunden früher als am anderen (bei 24 B/s und R = 38 mm für das Fenster 24,9 × 10,4 mm 4,8 ms); schnelle
 * Bewegungen quer dazu neigen sich leicht. Das Bild auf dem Film steht kopfüber und seitenverkehrt: Der Lauf im fertigen Bild
 * ist umgekehrt.
 * <p><b>Flanke</b>: Die Kante schneidet den Lichtkegel eines Bildpunkts nicht in einem Augenblick; der Kegel ist am Ort der
 * Scheibe (Abstand {@link #bladeGapMm} vor dem Film) d / N breit. Die Kante braucht dafür (d/N) / (2π · R · Bildrate) –
 * bei f/2 rund 0,26 ms, gut 1 % der Belichtung (der Rand des Fensters ist so weich).
 * <p>Werte der Scheibe [G]: Halbmesser des Fensters um die Achse 38 mm, Abstand vor dem Film 3 mm (wie bei den
 * Spiegelreflexkameras der Arri 35 und den Mitchell der 1950er der Größe nach).
 */
public final class Shutter {
    /** Bilder je Sekunde der Kamera (24 im Kino; mehr ist Zeitlupe, wenn mit 24 vorgeführt wird). */
    public volatile double fps = 24;
    /** Öffnung der Umlaufblende (Grad, 0 bis 360). */
    public volatile double angleDeg = 180;
    /** Öffnungsgang an (die Kante läuft über das Fenster) oder aus (alles öffnet zugleich). */
    public volatile boolean rolling = true;
    /** Abstand der Fenstermitte von der Achse (mm) [G]. */
    public volatile double gateRadiusMm = 38;
    /** Bildfenster (mm): Breite und Höhe (Super 35 für 2,39 : 1 als Vorgabe). */
    public volatile double gateWidthMm = 24.89, gateHeightMm = 10.41;
    /** Lage der Achse: Drehung (Grad) des Laufs gegen „Achse unter dem Fenster, Kante läuft nach rechts“ [G]. */
    public volatile double sweepDeg = 0;
    /** Abstand der Scheibe vor dem Film (mm) [G]. */
    public volatile double bladeGapMm = 3;

    public Shutter() { }

    public Shutter(double fps, double angleDeg) { this.fps = fps; this.angleDeg = angleDeg; }

    /** Belichtungszeit T (s). */
    public double openS() { return Math.max(0, Math.min(360, angleDeg)) / 360 / Math.max(1e-6, fps); }

    /** Zeit eines Bilds (s). */
    public double frameS() { return 1 / Math.max(1e-6, fps); }

    /** Winkel aus der Belichtungszeit bei dieser Bildrate (Grad). */
    public static double angleFor(double openS, double fps) { return Math.max(0, Math.min(360, openS * fps * 360)); }

    /**
     * Beginn der Belichtung (s) am Ort (x, y) im fertigen Bild, x und y von −0,5 bis 0,5 der Breite und Höhe (y nach oben);
     * 0 in der Bildmitte. Ohne Öffnungsgang überall 0.
     */
    public double openAt(double x, double y) {
        if (!rolling) return 0;
        double fx = -x * gateWidthMm, fy = -y * gateHeightMm;   // auf dem Film kopfüber und seitenverkehrt
        double a = Math.toRadians(sweepDeg), c = Math.cos(a), s = Math.sin(a);
        double rx = c * fx - s * fy, ry = s * fx + c * fy;
        return Math.atan2(rx, ry + gateRadiusMm) / (2 * Math.PI * fps);
    }

    /** Frühester und spätester Beginn über das Bild (s). */
    public double[] openRange() {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        int n = 16;
        for (int i = 0; i <= n; i++)
            for (int j = 0; j <= n; j++) {
                if (i != 0 && i != n && j != 0 && j != n) continue;   // die Ränder genügen (atan2 ist am Rand extrem)
                double t = openAt(i / (double) n - 0.5, j / (double) n - 0.5);
                lo = Math.min(lo, t);
                hi = Math.max(hi, t);
            }
        return new double[]{lo, hi};
    }

    /** Ganze Zeit, in der irgendwo im Bild belichtet wird (s): Öffnungsgang plus T. */
    public double windowS() { double[] r = openRange(); return r[1] - r[0] + openS(); }

    /** Dauer der Flanke für einen Bildpunkt bei Blende N (s). */
    public double rampS(double fNumber) { return (bladeGapMm / Math.max(0.5, fNumber)) / (2 * Math.PI * gateRadiusMm * Math.max(1e-6, fps)); }

    /**
     * Das Wagenrad: Ein Rad mit n gleichen Speichen, das sich mit revPerS Umdrehungen je Sekunde dreht, sieht gefilmt mit
     * fps Bildern je Sekunde so aus, als drehte es sich mit der zurückgegebenen Geschwindigkeit (Umdrehungen/s): Von Bild zu
     * Bild zählt nur die Drehung modulo eines Speichenabstands, und das Auge nimmt den kürzesten Weg.
     */
    public static double apparentRevPerS(double revPerS, int spokes, double fps) {
        double step = revPerS / fps * spokes;          // Speichenabstände je Bild
        double a = step - Math.rint(step);             // kürzester Weg: −0,5 … 0,5
        return a / spokes * fps;
    }

    /** Länge der Bewegungsunschärfe (Bildpunkte) einer Bewegung mit v Bildpunkten je Sekunde. */
    public double blurPx(double pxPerS) { return pxPerS * openS(); }

    /** „24 B/s · 180° (1/48 s) · Öffnungsgang 3,5 ms“ */
    public String describe() {
        double t = openS();
        String ts = t > 0 ? (1 / t >= 10 ? String.format(Locale.GERMAN, "1/%.0f s", 1 / t) : String.format(Locale.GERMAN, "%.3f s", t)) : "zu";
        double[] r = openRange();
        return String.format(Locale.GERMAN, "%.0f B/s · %.0f° (%s)%s", fps, angleDeg, ts,
                rolling ? String.format(Locale.GERMAN, " · Öffnungsgang %.1f ms", 1000 * (r[1] - r[0])) : "");
    }
}
