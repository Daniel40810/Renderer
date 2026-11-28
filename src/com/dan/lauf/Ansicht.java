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
    /**
     * Seit S3c (Seegang Werkbuch, SF27 A): Mitte des feinen Nahfelds (UTM Ost, Nord), wenn die Ansicht sie vorgibt
     * ({@code ansicht.<name>.fein = Ost Nord}); null: von selbst über der Brandungslinie im Blick.
     */
    public double[] fein;
    /**
     * Seit S3c-5 (SF29 A, Seegang Werkbuch 19/20): Mindesthöhe des Auges über dem Wasser am Ort (m), über dem Spiegel plus
     * Hs/2 ({@code ansicht.<name>.wasser}; Vorgabe {@link #UEBER_WASSER}). Gilt in Standbildern und in der Anwendung
     * ({@link #hebe}); der Blick unter Wasser bleibt Kür (SF12, S8).
     */
    public double ueberWasser = UEBER_WASSER;
    /** Vorgabe der Mindesthöhe über dem Wasser (m) [G]. */
    public static final double UEBER_WASSER = 1.5;
    /**
     * Seit S7c (Seegang Werkbuch 89, SF110 A): an Bord eines Schwimmers ({@code ansicht.<name>.bord = <schwimmer> [x y z]},
     * Auge im Körper in m; ohne Zahlen die Vorgabe der {@link Bordkamera}); null: an Land. Die Bühne setzt dann eine
     * {@link Bordkamera}; Richtung und Neigung gelten gegen den Bug.
     */
    public String bord;
    public double[] bordAuge;

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

    /**
     * Seit S3c-5 (SF29 A): hebt das Auge, wenn es tiefer liegt als {@code ueber} m über dem Wasser am Ort (Spiegel
     * {@code eta} m NHN plus Hs/2); ohne Wasser am Ort ({@code eta} NaN) bleibt es. Gibt zurück, um wie viel es gehoben wurde (m).
     */
    public static double hebe(Blick b, double eta, double hs, double ueber) {
        if (Double.isNaN(eta) || eta < -1e29) return 0;
        double min = eta + 0.5 * Math.max(0, Double.isNaN(hs) ? 0 : hs) + ueber;
        if (b.hoehe >= min) return 0;
        double d = min - b.hoehe;
        b.hoehe = min;
        return d;
    }

    @Override public String toString() { return name; }
}
