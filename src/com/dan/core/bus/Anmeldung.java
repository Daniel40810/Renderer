package com.dan.core.bus;

import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;

import java.util.ArrayList;
import java.util.List;

/**
 * Was ein Modul am Bus liest und schreibt und wie oft es koppelt (Werkbuch HalligHooge Teil A, 5.5). Name, Einheit
 * und Ort stehen bei jedem Anschluss noch einmal: Der Bus vergleicht sie mit dem Katalog, damit ein Modul, das eine
 * andere Einheit oder einen anderen Ort annimmt, nicht startet.
 * <p>
 * Seit P5c darf ein Modul dasselbe Feld auf mehreren Gittern schreiben und lesen (hydro rechnet die ganze Gitterkette,
 * F27 A): je Gitter ein Anschluss. Ein Antrieb (Rand, Wind) wird über das ganze Kopplungsintervall gelesen
 * ({@link #liestImIntervall}); sein Schreiber rechnet dann bis zum Ende des Intervalls voraus ({@code takt.Takt}).
 */
public final class Anmeldung {
    /**
     * Ein Anschluss: Feld, erwartete Einheit und Ort, Gitter des Moduls; beim Schreiben Zahlentyp und Schichten; beim
     * Lesen, ob über das ganze Intervall.
     */
    record Anschluss(String name, String einheit, Ort ort, Gitter gitter, Feld.Zahl zahl, int schichten, boolean intervall) { }

    final Modul modul;
    final List<Anschluss> liest = new ArrayList<>(), schreibt = new ArrayList<>();
    double kopplung = Double.NaN;

    Anmeldung(Modul modul) { this.modul = modul; }

    /** Kopplungsintervall in Sekunden Modellzeit: So oft legt das Modul seine Felder ab. */
    public Anmeldung kopplung(double sekunden) {
        kopplung = sekunden;
        return this;
    }

    /** Das Modul liest nur und wird nicht getaktet (das Bild): Es liest zu jeder Zeit, die der Bus hält. */
    public Anmeldung ohneTakt() {
        kopplung = Double.POSITIVE_INFINITY;
        return this;
    }

    /** Das Modul liest dieses Feld auf seinem Gitter (null bei einem Feld ohne Gitter), zur Startzeit seines Intervalls. */
    public Anmeldung liest(String name, String einheit, Ort ort, Gitter gitter) {
        liest.add(new Anschluss(name, einheit, ort, gitter, null, 0, false));
        return this;
    }

    /**
     * Das Modul liest dieses Feld zu jeder Zeit seines Intervalls [t0, t1] (ein Antrieb: Randwerte, Wind, Luftdruck).
     * Der Takt lässt den Schreiber dafür bis t1 vorausrechnen, und der Bus hält so viele Stände, dass das ganze
     * Intervall darin liegt (seit P5c).
     */
    public Anmeldung liestImIntervall(String name, String einheit, Ort ort, Gitter gitter) {
        liest.add(new Anschluss(name, einheit, ort, gitter, null, 0, true));
        return this;
    }

    /** Das Modul schreibt dieses Feld auf seinem Gitter; Schichten nach dem Katalog. */
    public Anmeldung schreibt(String name, String einheit, Ort ort, Gitter gitter, Feld.Zahl zahl) { return schreibt(name, einheit, ort, gitter, zahl, 0); }

    /** Das Modul schreibt dieses Feld mit eigener Zahl von Schichten (wo der Katalog sie offen lässt). */
    public Anmeldung schreibt(String name, String einheit, Ort ort, Gitter gitter, Feld.Zahl zahl, int schichten) {
        schreibt.add(new Anschluss(name, einheit, ort, gitter, zahl, schichten, false));
        return this;
    }
}
