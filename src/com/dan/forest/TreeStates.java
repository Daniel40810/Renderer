package com.dan.forest;

import java.util.Arrays;

/**
 * Der Zustand jedes Baums eines großen Walds (seit LituyaBay1958 Phase 6): stehend, gebrochen, entrindet, umgelegt (mit
 * Richtung) oder fort. Ein Byte je Platz des Pflanzgitters von {@link ForestCulture} (Platz (i, j) liegt bei
 * [i·Abstand, (i+1)·Abstand) × [j·Abstand, (j+1)·Abstand)); außerhalb des Rechtecks steht jeder Baum. Wer den Zustand
 * rechnet (etwa {@code com.dan.succession} oder die Welle), schreibt ihn hier hinein; {@code gl.ForestGpu} lädt das
 * Gitter als Textur und zeichnet jeden Baum in seiner Form.
 * <p>
 * Byte: Bit 0–2 Zustand, Bit 3–7 Fallrichtung in 32 Schritten zu 11,25° (Kompass: 0 = Nord = −z, 90 = Ost = +x).
 */
public final class TreeStates {
    public static final int STANDING = 0, BROKEN = 1, STRIPPED = 2, FELLED = 3, GONE = 4;
    public static final String[] NAMES = {"stehend", "gebrochen", "entrindet", "umgelegt", "fort"};

    public final double spacing;
    /** Erster Platz (i0, j0) und Größe des Gitters in Plätzen. */
    public final int i0, j0, w, h;
    private final byte[] data;
    private int version;

    public TreeStates(double spacing, int i0, int j0, int w, int h) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("leeres Gitter");
        this.spacing = spacing;
        this.i0 = i0;
        this.j0 = j0;
        this.w = w;
        this.h = h;
        data = new byte[w * h];
    }

    /** Ein Gitter, das das Rechteck (x0, z0) … (x1, z1) in Metern bedeckt. */
    public static TreeStates covering(double x0, double z0, double x1, double z1, double spacing) {
        int a = (int) Math.floor(Math.min(x0, x1) / spacing), b = (int) Math.floor(Math.min(z0, z1) / spacing);
        int c = (int) Math.floor(Math.max(x0, x1) / spacing), d = (int) Math.floor(Math.max(z0, z1) / spacing);
        return new TreeStates(spacing, a, b, c - a + 1, d - b + 1);
    }

    public int slotI(double x) { return (int) Math.floor(x / spacing); }
    public int slotJ(double z) { return (int) Math.floor(z / spacing); }

    public boolean contains(int i, int j) { return i >= i0 && j >= j0 && i < i0 + w && j < j0 + h; }

    /** Zustand (0..4) des Platzes; außerhalb {@link #STANDING}. */
    public int state(int i, int j) { return contains(i, j) ? data[(j - j0) * w + (i - i0)] & 7 : STANDING; }

    /** Fallrichtung (Kompassgrad) eines umgelegten Baums. */
    public double fallDeg(int i, int j) { return contains(i, j) ? ((data[(j - j0) * w + (i - i0)] & 0xff) >> 3) * 11.25 : 0; }

    /** Setzt den Zustand eines Platzes (außerhalb wird nichts gesetzt); fallDeg nur für {@link #FELLED}. */
    public void set(int i, int j, int state, double fallDeg) {
        if (!contains(i, j)) return;
        if (state < 0 || state > GONE) throw new IllegalArgumentException("Zustand " + state);
        int dir = (int) Math.round(((fallDeg % 360) + 360) % 360 / 11.25) & 31;
        data[(j - j0) * w + (i - i0)] = (byte) (state | (state == FELLED ? dir << 3 : 0));
        version++;
    }

    /** Wie {@link #set}, für den Platz unter (x, z). */
    public void setAt(double x, double z, int state, double fallDeg) { set(slotI(x), slotJ(z), state, fallDeg); }

    /** Alle Plätze stehend. */
    public void clear() { Arrays.fill(data, (byte) 0); version++; }

    /** Die Bytes, Zeile für Zeile (j), für die Grafikkarte; nicht ändern. */
    public byte[] data() { return data; }

    /** Zählt bei jeder Änderung hoch (die Grafikkarte lädt neu, wenn sich die Zahl ändert). */
    public int version() { return version; }

    /** Plätze je Zustand. */
    public int[] counts() {
        int[] n = new int[5];
        for (byte b : data) n[Math.min(4, b & 7)]++;
        return n;
    }
}
