package com.dan.surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vorgaben für Materiallisten (Orts-Presets, Paketvertrag Punkt 5). {@link #fjord()}: Fjord an der Küste Südostalaskas
 * mit Regenwald, Fels, Gletschern und Kuttern (LituyaBay1958, Werkbuch 8), 14 Materialien aus 13 CC0-Sätzen von
 * ambientCG und einem abgeleiteten (Seetang: kein CC0-Satz gefunden, aus dem Moos getönt). Kachelgrößen und Tönungen
 * sind gesetzt [G]; ambientCG nennt für die meisten Sätze keine Maße.
 */
public final class Katalog {
    private Katalog() { }

    public static List<Material> fjord() {
        List<Material> l = new ArrayList<>();
        l.add(new Material("strandkies", "Strandkies und Gerölle", 1.6).ambientCG("Gravel041").height(0.05).wet(0.5));
        l.add(new Material("sand", "Sand und Gletschermehl", 3.5).ambientCG("Ground054").height(0.03).wet(0.55)
                .tint(0.92, 0.95, 1.0).desaturate(0.25));
        l.add(new Material("schiefer_verwittert", "Schiefer, verwittert", 4).ambientCG("Rock063").triplanar().height(0.25).wet(0.35));
        l.add(new Material("schiefer_frisch", "Schiefer, frischer Bruch", 4).ambientCG("Rock058").triplanar().height(0.2).wet(0.3)
                .desaturate(0.3).brighten(1.15));
        l.add(new Material("moraene", "Moräne, Schutt, Geröll", 2.2).ambientCG("Ground108").height(0.12).wet(0.45).desaturate(0.2));
        l.add(new Material("waldboden", "Waldboden mit Nadeln und Moos", 2.1).ambientCG("Ground037").height(0.04).wet(0.4));
        l.add(new Material("moos", "Moos und Bartflechte", 1.5).ambientCG("Moss002").height(0.03).wet(0.3));
        l.add(new Material("firn", "Firn und Schnee", 3).ambientCG("Snow014").height(0.04).wet(0.1));
        l.add(new Material("eis", "Gletschereis", 5).ambientCG("Ice003").triplanar().height(0.1).wet(0.05)
                .tint(0.80, 0.93, 1.08).desaturate(0.4).brighten(1.1));
        l.add(new Material("rinde", "Rinde Sitka-Fichte und Hemlock", 1.2).ambientCG("Bark014").fixed().triplanar().height(0.03).wet(0.4));
        l.add(new Material("holz_entrindet", "Entrindetes Holz, Treibholz silbergrau", 1).ambientCG("Wood035").fixed().height(0.01).wet(0.4)
                .desaturate(0.75).brighten(1.9));
        l.add(new Material("seetang", "Seetang und Kelp", 1.5).derived("moos").height(0.02).wet(0.1)
                .tint(1.05, 0.72, 0.30).desaturate(0.1).brighten(0.7));
        l.add(new Material("kutter_holz", "Kutter: lackiertes Holz", 1).ambientCG("PaintedWood009C").fixed().height(0.005).wet(0.2));
        l.add(new Material("kutter_tau", "Kutter: Tau", 0.5).ambientCG("Rope001").fixed().height(0.01).wet(0.5));
        return Collections.unmodifiableList(l);
    }

    /** Das Material mit Kurzname id oder null. */
    public static Material find(List<Material> l, String id) {
        for (Material m : l) if (m.id.equals(id)) return m;
        return null;
    }
}
