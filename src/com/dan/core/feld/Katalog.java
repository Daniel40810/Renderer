package com.dan.core.feld;

import com.dan.core.feld.Eintrag.Art;
import com.dan.core.feld.Eintrag.Raster;
import com.dan.core.feld.Eintrag.Verbund;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Der Feldkatalog (Werkbuch HalligHooge Teil A, 5.3): die Felder, über die Module miteinander sprechen. Ein Modul
 * liest und schreibt nur Felder aus dem Katalog; Erweiterungen kommen nur über das Werkbuch. {@code tools/PaketPruefung}
 * prüft die Feldnamen in den Quellen der Physik- und Grafikmodule gegen {@link #grundstufe()}.
 */
public final class Katalog {
    private final Map<String, Eintrag> eintraege = new LinkedHashMap<>();

    /** Der Katalog der Grundstufe, Zeile für Zeile wie im Werkbuch. */
    public static Katalog grundstufe() {
        Katalog k = new Katalog();
        k.zu("ground.z", "m NHN", Ort.ZELLE, Raster.FEIN, 1, 1, "core", "Aufbereitung", "alle");
        k.zu("ground.hyps", "m³, m², m³/s", Ort.TABELLE, Raster.ZELLE, 3, 0, "core", "", "hydro, sediment");                   // sediment seit P9b: Volumen der Zelle aus der Tabelle
        k.zu("water.eta", "m NHN", Ort.ZELLE, Raster.ZELLE, 1, 1, "hydro", "", "sediment, atmos, render, analysis, archiv, vegetation");       // vegetation seit P13b; archiv seit P6e (F40), sediment seit P9b, atmos seit P10b (Wasserstand unter dem Sand)
        k.zu("water.h", "m", Ort.ZELLE, Raster.ZELLE, 1, 1, "hydro", "", "sediment, waves, atmos, aeolian, render, archiv, vegetation");            // aeolian seit P10c, vegetation seit P13b
        k.zu("water.q", "m²/s", Ort.KANTE, Raster.ZELLE, 1, 1, Art.FLUSS, "hydro", "", "sediment, core");
        k.zu("water.u", "m/s", Ort.ZELLE, Raster.ZELLE, 1, 1, "hydro", "", "waves, sediment, render");
        k.zu("water.v", "m/s", Ort.ZELLE, Raster.ZELLE, 1, 1, "hydro", "", "waves, sediment, render");
        k.zu("water.wetAge", "s", Ort.ZELLE, Raster.ZELLE, 1, 1, "hydro", "", "atmos, aeolian, render, archiv");                       // aeolian seit P10c
        k.zu("water.spiral", "m/s", Ort.ZELLE, Raster.ZELLE, 1, 1, "hydro", "", "sediment");
        k.zu("tide.bc", "m, m²/s", Ort.RAND, Raster.ZELLE, 2, 1, "tide", "", "hydro");
        k.zu("tide.eq", "m", Ort.ZELLE, Raster.ZELLE, 1, 1, "tide", "", "hydro");                    // seit P5c (F27 A): Gleichgewichtstide, mal 0,69
        k.zu("air.u10", "m/s", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "hydro, waves, aeolian, atmos, render, archiv");   // render und archiv seit P6e
        k.zu("air.v10", "m/s", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "hydro, waves, aeolian, atmos, render, archiv");
        k.zu("air.p", "Pa", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "hydro, tide, atmos");      // tide seit P5c: umgekehrtes Barometer am Rand; atmos seit P10b
        k.zu("air.taux", "N/m²", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "hydro, waves");        // seit P5e (F34): Windschub nach Osten; waves seit P8b (u*, W1)
        k.zu("air.tauy", "N/m²", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "hydro, waves");        // seit P5e (F34): Windschub nach Norden
        k.zu("air.t2", "K", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "atmos");
        k.zu("air.q2", "kg/kg", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "atmos");
        k.zu("air.sw", "W/m²", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "atmos");                 // seit P10b (F90 A): Sonnenstrahlung am Boden nach unten, mit Wolken
        k.zu("air.lw", "W/m²", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "atmos");                 // seit P10b: Gegenstrahlung am Boden nach unten
        k.zu("air.rain", "kg/(m² s)", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "atmos");          // seit P10c (F97 A): Regen (ERA5, Mittel der Stunde)
        k.zu("water.sst", "K", Ort.ZELLE, Raster.GROB, 1, 1, "antrieb", "", "atmos");                 // seit P10b: Temperatur des Meerwassers (ERA5), über Land aus den Nachbarn über See
        k.zu("sun.dir", "–", Ort.GLOBAL, Raster.GLOBAL, 3, 1, "core.astro", "", "atmos, render");
        k.zu("sun.E", "W/m²", Ort.GLOBAL, Raster.GLOBAL, 1, 1, "core.astro", "", "atmos, render");
        k.zu("waves.spec", "m²/(Hz·rad)", Ort.SAEULE, Raster.ZELLE, 1, 0, "waves", "", "render.water");
        k.zu("waves.hs", "m", Ort.ZELLE, Raster.ZELLE, 1, 1, "waves", "", "sediment, render, analysis, archiv");     // archiv seit P8b
        k.zu("waves.tp", "s", Ort.ZELLE, Raster.ZELLE, 1, 1, "waves", "", "sediment, render, analysis, archiv");
        k.zu("waves.dir", "°", Ort.ZELLE, Raster.ZELLE, 1, 1, "waves", "", "sediment, render, analysis, archiv");     // seit P8b: woher, von Gitternord im Uhrzeigersinn
        k.zu("waves.ubot", "m/s", Ort.ZELLE, Raster.ZELLE, 1, 1, "waves", "", "sediment");
        k.zu("waves.force", "N/m²", Ort.ZELLE, Raster.ZELLE, 2, 1, "waves", "", "hydro");
        k.zu("waves.diss", "W/m²", Ort.ZELLE, Raster.ZELLE, 1, 1, "waves", "", "render.water, archiv");
        k.zu("waves.mss", "–", Ort.ZELLE, Raster.ZELLE, 1, 1, "waves", "", "render.water, archiv");
        // seit S1 (Seegang Werkbuch, SF6 A): Windsee und Dünung getrennt (Hs, Tp, woher von Gitternord, Richtungsbreite), die
        // Dissipation nach Quellen getrennt; geschrieben vom Nachlauf des Seegangs (lauf.SeegangNachlauf), gelesen vom Nahfeld
        k.zu("waves.sea", "m, s, °, °", Ort.ZELLE, Raster.ZELLE, 4, 1, "waves", "", "render, archiv");
        k.zu("waves.swell", "m, s, °, °", Ort.ZELLE, Raster.ZELLE, 4, 1, "waves", "", "render, archiv");
        k.zu("waves.dwc", "W/m²", Ort.ZELLE, Raster.ZELLE, 1, 1, "waves", "", "render, archiv");     // Schaumkronen
        k.zu("waves.dbr", "W/m²", Ort.ZELLE, Raster.ZELLE, 1, 1, "waves", "", "render, archiv");     // Brechen an der Tiefe
        k.zu("waves.dbf", "W/m²", Ort.ZELLE, Raster.ZELLE, 1, 1, "waves", "", "render, archiv");     // Bodenreibung
        k.zu("bed.d50", "m", Ort.ZELLE, Raster.ZELLE, 1, 1, "sediment", "Aufbereitung", "sediment, render");
        k.zu("bed.mud", "–", Ort.ZELLE, Raster.ZELLE, 1, 1, "sediment", "", "hydro, render.terrain, render, archiv");           // archiv seit P9b, render (live) seit P9c
        k.zu("bed.ripple", "m, m, °", Ort.ZELLE, Raster.ZELLE, 3, 1, "sediment", "", "hydro, render.terrain, render, archiv");  // Höhe, Länge, Richtung (wohin der formende Strom lief, von Gitternord im Uhrzeigersinn)
        k.zu("bed.fluff", "kg/m²", Ort.ZELLE, Raster.ZELLE, 1, 1, "sediment", "", "archiv");                                   // seit P9c: lockere Auflage des Schlicks
        k.verbund("bed.dz", "m", Verbund.SUMME, "core, render, archiv, hydro, sediment", "sediment", "aeolian");                 // hydro, sediment seit P9c (F81 A)
        k.zu("aeol.q", "kg/(m s)", Ort.ZELLE, Raster.ZELLE, 2, 1, "aeolian", "", "render.terrain, archiv");                      // seit P10c (F96 A): Flugsand, Fluss nach Ost und nach Nord
        k.zu("sed.c", "kg/m³", Ort.ZELLE, Raster.ZELLE, 2, 1, "sediment", "", "render.water, render, archiv");                       // seit P9b zwei Teile: Sand, Schlick
        k.zu("surf.wet", "–", Ort.ZELLE, Raster.ZELLE, 1, 1, "atmos", "", "aeolian, render.terrain, archiv");   // archiv seit P10b
        k.zu("surf.T", "K", Ort.ZELLE, Raster.ZELLE, 1, 1, "atmos", "", "render.air, archiv");                                // archiv seit P10b
        k.verbund("surf.z0", "m", Verbund.MAXIMUM, "hydro, atmos", "sediment", "vegetation");
        k.zu("air.n", "–", Ort.SAEULE, Raster.GROB, 1, 64, "atmos", "", "render.air, archiv");               // seit P10b: n − 1 (float trägt n selbst nicht)
        k.zu("veg.cover", "–", Ort.ZELLE, Raster.ZELLE, 1, 1, "vegetation", "", "hydro, sediment, aeolian, render");
        return k;
    }

    private void zu(String name, String einheit, Ort ort, Raster raster, int komponenten, int schichten, String schreiber, String anfang, String leser) {
        zu(name, einheit, ort, raster, komponenten, schichten, Art.ZUSTAND, schreiber, anfang, leser);
    }

    private void zu(String name, String einheit, Ort ort, Raster raster, int komponenten, int schichten, Art art, String schreiber, String anfang,
                    String leser) {
        zu(new Eintrag(name, einheit, ort, raster, komponenten, schichten, art, Verbund.KEINER, List.of(schreiber.split(",\\s*")), anfang, List.of(leser.split(",\\s*"))));
    }

    /**
     * Ein Feld, das der Kern aus den Beiträgen mehrerer Module bildet (F18 A): das Feld selbst mit dem Kern als Schreiber
     * und je Modul ein Beitrag {@code <name>.<modul>}, den nur dieses Modul schreibt und nur der Kern liest.
     */
    private void verbund(String name, String einheit, Verbund regel, String leser, String... module) {
        zu(new Eintrag(name, einheit, Ort.ZELLE, Raster.ZELLE, 1, 1, Art.ZUSTAND, regel, List.of("core"), "", List.of(leser.split(",\\s*"))));
        for (String m : module)
            zu(new Eintrag(name + "." + m, einheit, Ort.ZELLE, Raster.ZELLE, 1, 1, Art.ZUSTAND, Verbund.KEINER, List.of(m), "", List.of("core")));
    }

    /** Nimmt einen Eintrag auf; ein Name darf nur einmal vorkommen. */
    public Katalog zu(Eintrag e) {
        if (eintraege.putIfAbsent(e.name(), e) != null) throw new IllegalArgumentException("Feld steht schon im Katalog: " + e.name());
        return this;
    }

    /** Der Eintrag zu einem Namen oder null. */
    public Eintrag eintrag(String name) { return eintraege.get(name); }

    /** Der Eintrag zu einem Namen; fehlt er, ist das ein Fehler. */
    public Eintrag verlange(String name) {
        Eintrag e = eintraege.get(name);
        if (e == null) throw new IllegalArgumentException("Feld steht nicht im Katalog: " + name);
        return e;
    }

    public boolean kennt(String name) { return eintraege.containsKey(name); }

    /** Alle Namen in der Reihenfolge des Werkbuchs. */
    public List<String> namen() { return Collections.unmodifiableList(new ArrayList<>(eintraege.keySet())); }

    /** Alle Gruppen (der Teil vor dem Punkt). */
    public Set<String> gruppen() {
        Set<String> g = new TreeSet<>();
        for (Eintrag e : eintraege.values()) g.add(e.gruppe());
        return g;
    }

    public List<Eintrag> eintraege() { return Collections.unmodifiableList(new ArrayList<>(eintraege.values())); }

    /** Die Beiträge zu einem Verbund, etwa {@code bed.dz.sediment} und {@code bed.dz.aeolian} zu {@code bed.dz}. */
    public List<Eintrag> beitraege(String verbund) {
        List<Eintrag> l = new ArrayList<>();
        for (Eintrag e : eintraege.values()) if (e.beitrag() && e.ganzes().equals(verbund)) l.add(e);
        return l;
    }

    /** Felder, die mehr als einen Schreiber haben; seit F18 keines mehr. */
    public List<Eintrag> mehrfachGeschrieben() {
        List<Eintrag> l = new ArrayList<>();
        for (Eintrag e : eintraege.values()) if (e.schreiber().size() > 1) l.add(e);
        return l;
    }
}
