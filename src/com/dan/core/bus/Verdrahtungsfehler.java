package com.dan.core.bus;

import java.util.List;

/** Der Bus lässt sich nicht verdrahten: Es startet nichts (Werkbuch HalligHooge Teil A, 5.5). */
public final class Verdrahtungsfehler extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** Alle Mängel, nicht nur der erste. */
    public final transient List<String> maengel;

    Verdrahtungsfehler(List<String> maengel) {
        super("Der Bus startet nicht:\n  " + String.join("\n  ", maengel));
        this.maengel = List.copyOf(maengel);
    }
}
