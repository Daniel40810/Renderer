package com.dan.lauf;

import com.dan.core.astro.Gestirne;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Blick;

import java.time.Instant;

/**
 * Ein benannter Blick (Werkbuch HalligHooge Teil B, B.3; F70 A, F71 A): Ort, Höhe, Richtung, Neigung und Bildwinkel,
 * gelesen aus einer Szenendatei ({@link Szenen}). Nichts davon ist an ein Gebiet gebunden.
 * <ul>
 * <li><b>Höhe</b> über dem Boden am Ort ({@link #ueberBoden}) oder in m NHN.</li>
 * <li><b>Richtung</b> als Azimut (Grad von Nord über Ost) oder zur Sonne ({@link #zurSonne()}): dann gilt der Azimut
 * der Sonne zur Szenenzeit, in der der Blick gesetzt wird.</li>
 * </ul>
 */
public final class Ansicht {
    public final String schluessel, name;
    /** UTM Ost und Nord (m). */
    public final double ost, nord;
    /** Höhe in m: über dem Boden ({@link #ueberBoden}) oder NHN. */
    public final double hoehe;
    public final boolean ueberBoden;
    /** Azimut in Grad; NaN: zur Sonne. */
    public final double richtung;
    public final double neigung, bildwinkel;

    public Ansicht(String schluessel, String name, double ost, double nord, double hoehe, boolean ueberBoden, double richtung, double neigung, double bildwinkel) {
        this.schluessel = schluessel;
        this.name = name;
        this.ost = ost;
        this.nord = nord;
        this.hoehe = hoehe;
        this.ueberBoden = ueberBoden;
        this.richtung = richtung;
        this.neigung = neigung;
        this.bildwinkel = bildwinkel;
    }

    public boolean zurSonne() { return Double.isNaN(richtung); }

    /**
     * Setzt einen Blick auf diese Ansicht; die Bildgröße bleibt.
     *
     * @param gel  Gelände für die Höhe über dem Boden (null: der Boden gilt als 0 m NHN)
     * @param utm  ein Gitter in UTM des Gebiets, für Länge und Breite des Orts (nur für den Blick zur Sonne)
     * @param zeit Szenenzeit (nur für den Blick zur Sonne)
     */
    public void setze(Blick b, Gelaende gel, Gitter utm, Instant zeit) {
        b.ost = ost;
        b.nord = nord;
        b.hoehe = ueberBoden ? (gel != null ? gel.hoehe(ost, nord, 1) : 0) + hoehe : hoehe;
        b.neigung = neigung;
        b.bildwinkel = bildwinkel;
        if (!zurSonne()) b.richtung = richtung;
        else if (utm != null && zeit != null) {
            double[] ll = utm.grad(ost, nord);
            b.richtung = Gestirne.bei(zeit).sonne(ll[0], ll[1]).azimutGrad();
        }
    }

    @Override public String toString() { return name; }
}
