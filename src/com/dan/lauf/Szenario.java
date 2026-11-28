package com.dan.lauf;

import java.io.File;
import java.time.Instant;

/**
 * Eine benannte Quelle für das Bild (Werkbuch HalligHooge Teil B, B.3; F70 A, F71 A): ein abgelegter Lauf
 * ({@link #archiv}) oder ein Lauf, der neben dem Bild rechnet ({@link #live()}), dazu womit die Bühne beginnt.
 * Gelesen aus einer Szenendatei ({@link Szenen}).
 */
public final class Szenario {
    public final String schluessel, name;
    /** Ordner eines abgelegten Laufs; null: live. */
    public final File archiv;
    /** Live: Start des Laufs (UTC), Stufen wie in {@link Lauf.Einstellung#stufen(String)}, G3 als Fenster (null: ganz). */
    public final Instant start;
    public final String stufen;
    public final int[] fenster3;
    /** Szenenzeit beim Öffnen (null: der erste Stand des Archivs bzw. der Start des Laufs). */
    public final Instant beginn;
    /** Zeitraffer beim Öffnen. */
    public final double zeitraffer;
    /** Schlüssel der ersten Ansicht (null: der Blick bleibt). */
    public final String ansicht;
    /** Schwebstoff der Szene (g/m³; seit P9b): gilt, wo kein Netz des Sediments die Trübung gibt; NaN: die Vorgabe der Optik. */
    public double schwebstoff = Double.NaN;
    /** Seit P9c, nur live: Der Lauf rechnet das Sediment mit (Trübung am Ort, Rippel, Boden mit Rückwirkung). */
    public boolean sediment;
    /** Seit S7b-2 (Seegang Werkbuch 88): die Schwimmer des Szenarios (leer: keine). */
    public java.util.List<Schwimmerangabe> schwimmer = java.util.List.of();
    /**
     * Seit S7d4 (Seegang Werkbuch 92, SF121 A): Mitte der Körperfläche (UTM Ost, Nord) aus {@code seegang.koerper} der
     * Szenendatei oder {@code szenario.<name>.koerper}; null: aus den Plätzen der Schwimmer (Flotte.koerpermitte).
     */
    public double[] koerper;

    public Szenario(String schluessel, String name, File archiv, Instant start, String stufen, int[] fenster3, Instant beginn, double zeitraffer, String ansicht) {
        this.schluessel = schluessel;
        this.name = name;
        this.archiv = archiv;
        this.start = start;
        this.stufen = stufen;
        this.fenster3 = fenster3;
        this.beginn = beginn;
        this.zeitraffer = zeitraffer;
        this.ansicht = ansicht;
    }

    public boolean live() { return archiv == null; }

    @Override public String toString() { return name; }
}
