package com.dan.core.feld;

import java.util.List;

/**
 * Ein Eintrag im Feldkatalog (Werkbuch HalligHooge Teil A, 5.3): Name, Einheit und Ort im Gitter sind Vertrag
 * zwischen den Modulen.
 *
 * @param name        Name im Katalog, etwa {@code water.eta}
 * @param einheit     Einheit; bei mehreren Komponenten durch Komma getrennt, etwa {@code m, m, °}
 * @param ort         Ort der Werte im Gitter
 * @param raster      welches Gitter der Kette gemeint ist
 * @param komponenten Werte je Ort (etwa 3 bei {@code bed.ripple}: Höhe, Länge, Richtung)
 * @param schichten   Tiefe je Ort: 64 Höhen bei {@code air.n}, Frequenzen × Richtungen bei {@code waves.spec}; sonst 1.
 *                    0 heißt: legt das schreibende Modul fest
 * @param art         Zustand (zwischen zwei Ständen linear gelesen) oder Fluss (als Zeitmittel gelesen)
 * @param verbund     wie der Kern das Feld aus den Beiträgen mehrerer Module bildet (F18); sonst {@link Verbund#KEINER}.
 *                    Die Beiträge heißen {@code <name>.<modul>}, etwa {@code bed.dz.sediment}
 * @param schreiber   wer das Feld schreiben darf; im Lauf schreibt genau einer (Schichtenvertrag)
 * @param anfang      woher der Anfangswert kommt, etwa „Aufbereitung“; leer, wenn der Schreiber ihn setzt
 * @param leser       wer es laut Werkbuch liest („alle“ oder Namen; ein Name gilt auch für alles darunter)
 */
public record Eintrag(String name, String einheit, Ort ort, Raster raster, int komponenten, int schichten, Art art, Verbund verbund,
                      List<String> schreiber, String anfang, List<String> leser) {

    /** Wie ein Feld aus Beiträgen entsteht (Werkbuch 13.1, F18 A): Jeder Schreiber legt seinen Teil ab, der Kern bildet das Feld. */
    public enum Verbund { KEINER, SUMME, MAXIMUM }

    /** Zustand oder Fluss (Werkbuch 5.5). */
    public enum Art { ZUSTAND, FLUSS }

    /** Welches Gitter der Kette ein Feld trägt. */
    public enum Raster {
        /** das Rechengitter des schreibenden Moduls */
        ZELLE,
        /** das feine Gelände unter dem Rechengitter (Subgrid, etwa 1 m) */
        FEIN,
        /** ein grobes Gitter des Antriebs (ERA5, Wetterregie) oder der Grenzschicht (200 m) */
        GROB,
        /** kein Gitter: ein Wert für die Szene */
        GLOBAL
    }

    /** Darf dieses Modul das Feld schreiben? */
    public boolean darfSchreiben(String modul) { return passt(schreiber, modul); }

    /** Steht dieses Modul im Werkbuch als Leser? */
    public boolean alsLeserGenannt(String modul) { return leser.contains("alle") || passt(leser, modul); }

    private static boolean passt(List<String> namen, String modul) {
        for (String n : namen) if (modul.equals(n) || modul.startsWith(n + ".")) return true;
        return false;
    }

    /** Ist das Feld der Beitrag eines Moduls zu einem Verbund (drei Teile: {@code bed.dz.sediment})? */
    public boolean beitrag() { return name.indexOf('.') != name.lastIndexOf('.'); }

    /** Bei einem Beitrag der Name des Verbunds, etwa {@code bed.dz}; sonst der eigene Name. */
    public String ganzes() { return beitrag() ? name.substring(0, name.lastIndexOf('.')) : name; }

    /** Die Gruppe vor dem Punkt, etwa {@code water}. */
    public String gruppe() { return name.substring(0, name.indexOf('.')); }
}
