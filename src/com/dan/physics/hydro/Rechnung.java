package com.dan.physics.hydro;

import java.util.List;

/**
 * Wo {@link HydroModul} rechnet (Werkbuch HalligHooge Teil A, 12.6, P5c): auf dem Prozessor in double
 * ({@link Prozessor}, die Referenz) oder auf der Karte in float ({@code gpu.KarteRechnung}, der Betrieb). Das Modul
 * gibt die Stufen vor – je Stufe ein Gitter, das seinen Rand einseitig aus der vorigen bekommt, oder eine Kette von
 * Gittern, beidseitig geschachtelt – und stellt Ränder und Antrieb in den {@code Hydro}; die Rechnung bringt sie auf das
 * Gerät, rechnet in gleichen Schritten und holt den Stand zurück.
 */
public interface Rechnung extends AutoCloseable {
    /** Richtet die Stufen ein (Ketten, Gerät) und bringt Einrichtung und Stand dorthin; einmal, vor dem ersten Schritt. */
    void einrichten(List<Hydro[]> stufen);

    /**
     * Rechnet die Stufe s bis zur Zeit t1 in gleichen Schritten (so viele, wie die Grenze des Schritts verlangt; bei
     * einer Kette die gröbste Ebene, abgeglichen wie {@link Kette#abgleich}). Springt der Schritt, wird das Verfahren
     * nahe seiner Grenze instabil (Werkbuch 12.6, Stand P5a).
     */
    void bis(int s, double t1);

    /** Holt den Stand aller Gitter der Stufe in ihre {@code Hydro}: η, V, u, Fluss, Bilanz, Zeit und die Summen der Flüsse. */
    void herunter(int s);

    /** Der Stand des gröbsten Gitters der Stufe für die einseitige Kopplung (ohne die übrigen Felder zu holen). */
    Einseitig.Stand stand(int s);

    /** Der Antrieb der Gitter der Stufe (Wind, Luftdruck, Gleichgewichtstide) hat sich geändert. */
    void antrieb(int s);

    /** Boden oder Rauheit der Gitter der Stufe haben sich geändert (seit P9c); der Stand ist vorher heruntergeholt ({@link #herunter}). */
    default void boden(int s) { }

    /** Setzt die Summen der Flüsse aller Gitter der Stufe auf null (Anfang eines Kopplungsintervalls). */
    void summenNull(int s);

    /** Volumen der Stufe (m³) nach {@link #herunter}: bei einer Kette ohne die Zellen unter einem feineren Gitter. */
    double volumen(int s);

    /** Zellschritte aller Stufen bisher. */
    long zellschritte();

    /** Wo gerechnet wird, für das Protokoll. */
    String geraet();

    @Override void close();

    /** Die Referenz auf dem Prozessor (double, Zeilen parallel). */
    final class Prozessor implements Rechnung {
        private List<Hydro[]> stufen;
        private Kette[] ketten;
        private long zellschritte;

        @Override public void einrichten(List<Hydro[]> stufen) {
            this.stufen = stufen;
            ketten = new Kette[stufen.size()];
            for (int s = 0; s < ketten.length; s++) if (stufen.get(s).length > 1) ketten[s] = new Kette(stufen.get(s));
        }

        @Override public void bis(int s, double t1) {
            Hydro[] st = stufen.get(s);
            if (ketten[s] == null) {
                long n0 = st[0].schritte;
                st[0].lauf(t1, Double.POSITIVE_INFINITY);
                st[0].zeit = t1;
                zellschritte += (st[0].schritte - n0) * (long) st[0].zellen;
                return;
            }
            Kette k = ketten[s];
            long[] n0 = k.schritte.clone();
            while (st[0].zeit < t1 - 1e-9) {
                double rest = t1 - st[0].zeit, d = k.dtKette(rest);
                int n = (int) Math.ceil(rest / d - 1e-9);
                k.schritt(n <= 1 ? rest : rest / n);
            }
            for (Hydro h : st) h.zeit = t1;
            for (int e = 0; e < st.length; e++) zellschritte += (k.schritte[e] - n0[e]) * (long) st[e].zellen;
        }

        @Override public void herunter(int s) { }

        @Override public Einseitig.Stand stand(int s) { return Einseitig.Stand.von(stufen.get(s)[0]); }

        @Override public void antrieb(int s) { }

        @Override public void summenNull(int s) {
            for (Hydro h : stufen.get(s)) {
                if (h.qSumme != null) java.util.Arrays.fill(h.qSumme, 0);
                if (h.qBrutto != null) java.util.Arrays.fill(h.qBrutto, 0);
            }
        }

        @Override public double volumen(int s) { return ketten[s] != null ? ketten[s].volumen() : stufen.get(s)[0].volumen(); }

        @Override public long zellschritte() { return zellschritte; }

        @Override public String geraet() { return "Prozessor (double, " + Runtime.getRuntime().availableProcessors() + " Kerne)"; }

        @Override public void close() { }
    }
}
