package com.dan.core.szene;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Gischt aus den Walzen (Seegang Werkbuch, S5b-2; SF50 A): Bündel von Tropfen, die von brechenden Kämmen
 * aufsteigen und mit dem Wind treiben. Die Orte kommen je Bild von der Karte ({@code render.seegang.Gischtsuche}: eine
 * Walze am Kamm, H ≥ 0,3 m); jeder Ort gibt je Sekunde mit der Rate {@link #RATE} ein Bündel [G], entschieden nach
 * einem Zufall aus Ort und Zeit (gleiche Zeit, gleiche Gischt). Ein Bündel lebt {@link #LEBEN} s wie die Spritzer von
 * {@link Brandung} und hat deren Aufbau ({@code gischt.vert}). Steht die Zeit, entsteht nichts und nichts altert.
 * <p>
 * Startgeschwindigkeit nach oben √(2 g · 0,3 H), waagrecht 0,35 davon mit dem Wind [G]; Breite der Streuung längs des
 * Kamms H, höchstens 2 m. Seit 0.2 (Befund L2y: einzelne Tropfen von 5 cm sind ab rund 50 m kleiner als ein
 * Bildpunkt und kaum zu sehen) ab {@link #FAHNE_AB} m/s Wind als Fahne wie die der Brandung: feiner Sprühnebel, der
 * mit dem Wind treibt und wächst ({@code gischt.vert}, Art 2).
 */
public final class Kammgischt {
    public static final String VERSION = "0.2";
    /** Bündel je Ort und Sekunde, Lebensdauer (s), Bündel höchstens, Sprung der Zeit, ab dem neu angefangen wird (s) [G]. */
    public static final double RATE = 0.4, LEBEN = 2.5, SPRUNG = 5;
    public static final int MAX = 400;
    /** Wind, ab dem der Kamm Fahnen gibt (m/s) [G]; Brandung gibt sie an den Kanten ab 17 m/s. */
    public static final double FAHNE_AB = 12;

    private record Buendel(double ost, double nord, double eta, double hoehe, double t0, int samen, double[] pegel) { }

    private final List<Buendel> aktiv = new ArrayList<>();
    private double tLetzt = Double.NaN;
    /** Zuletzt: Orte von der Karte, neue Bündel, lebende Bündel. */
    public volatile int orteZuletzt, neuZuletzt, aktivZuletzt;
    /** Ein (Prüfungen, Schalter der Anwendung). */
    public volatile boolean an = true;

    /** Alles vergessen (neues Szenario, Prüfungen). */
    public synchronized void leeren() {
        aktiv.clear();
        tLetzt = Double.NaN;
    }

    /** Neue Orte zur Modellzeit t: je Ort {Ost, Nord, η, H} (UTM, m), zahl Orte. */
    public synchronized void orte(double t, float[] o, int zahl) {
        double dt = Double.isNaN(tLetzt) ? 0 : t - tLetzt;
        if (!(dt >= 0) || dt > SPRUNG) { aktiv.clear(); dt = 0; }
        tLetzt = t;
        aktiv.removeIf(b -> t - b.t0 > LEBEN || b.t0 > t);
        orteZuletzt = zahl;
        int neu = 0;
        if (an && dt > 0)
            for (int i = 0; i < zahl; i++) {
                double ost = o[4 * i], nord = o[4 * i + 1];
                long h = mische(Math.round(ost / 2), Math.round(nord / 2), (long) Math.floor(t * 4));
                double u = (h >>> 11) * 0x1.0p-53;
                if (u >= Math.min(1, RATE * dt)) continue;
                if (aktiv.size() >= MAX) aktiv.remove(0);
                aktiv.add(new Buendel(ost, nord, o[4 * i + 2], o[4 * i + 3], t, (int) (h & 0xFFFF), new double[] {Double.NaN}));
                neu++;
            }
        neuZuletzt = neu;
        aktivZuletzt = aktiv.size();
    }

    /**
     * Die lebenden Bündel zur Zeit t im Raum der Brandung (x = Ost − e0, y = Höhe, z = n0 − Nord), Aufbau wie
     * {@code gischt.vert} ({@link Brandung#BUENDEL} Werte je Bündel); den Pegel liefert w beim ersten Mal. Zurück die Werte.
     */
    public synchronized float[] buendel(double t, double e0, double n0, Brandung.Wasser w, double windU, double windV) {
        float[] f = new float[Brandung.BUENDEL * aktiv.size()];
        double u = Math.hypot(windU, windV), rx = u > 0.1 ? windU / u : 1, ry = u > 0.1 ? windV / u : 0;
        int m = 0;
        for (Buendel b : aktiv) {
            double alter = t - b.t0;
            if (alter < 0 || alter > LEBEN) continue;
            if (Double.isNaN(b.pegel[0])) {
                float e = w != null ? w.eta(b.ost, b.nord) : Brandung.KEIN;
                b.pegel[0] = e == Brandung.KEIN || Float.isNaN(e) ? Double.NEGATIVE_INFINITY : e;
            }
            if (Double.isInfinite(b.pegel[0])) continue;
            boolean fahne = u >= FAHNE_AB;
            double v0 = fahne ? 1.5 : Math.sqrt(2 * 9.81 * 0.3 * b.hoehe), vh = fahne ? 0 : 0.35 * v0, streu = fahne ? 3 : Math.min(2, b.hoehe);
            int o = Brandung.BUENDEL * m++;
            f[o] = (float) (b.ost - e0); f[o + 1] = (float) (b.pegel[0] + b.eta + (fahne ? 0.3 * b.hoehe : 0)); f[o + 2] = (float) (n0 - b.nord); f[o + 3] = (float) alter;
            f[o + 4] = (float) (rx * vh); f[o + 5] = (float) v0; f[o + 6] = (float) (-ry * vh); f[o + 7] = fahne ? 1.2f : 0.3f;
            f[o + 8] = (float) windU; f[o + 9] = (float) -windV; f[o + 10] = (float) streu; f[o + 11] = (float) (fahne ? 1.0 : 0.5 * streu);
            f[o + 12] = (float) -ry; f[o + 13] = (float) -rx; f[o + 14] = fahne ? 0.10f : 0.05f;
            f[o + 15] = (float) ((fahne ? Brandung.FAHNE : Brandung.SPRITZ) * 65536 + b.samen);
        }
        return m == aktiv.size() ? f : java.util.Arrays.copyOf(f, Brandung.BUENDEL * m);
    }

    private static long mische(long a, long b, long c) {
        long x = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        x ^= x >>> 33; x *= 0xFF51AFD7ED558CCDL; x ^= x >>> 33; x *= 0xC4CEB9FE1A85EC53L; x ^= x >>> 33;
        return x;
    }
}
