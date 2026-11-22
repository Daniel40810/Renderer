package com.dan.lauf;

import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.bus.Verbund;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Kornfeld;
import com.dan.core.gitter.Tidenfeld;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.takt.Takt;
import com.dan.physics.antrieb.Antrieb;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.hydro.HydroModul;
import com.dan.physics.hydro.Rechnung;
import com.dan.physics.hydro.gpu.KarteRechnung;
import com.dan.physics.sediment.SedimentModul;
import com.dan.physics.sediment.gpu.SedimentRechner;
import com.dan.physics.tide.Tide;
import com.dan.physics.waves.WavesModul;
import com.dan.physics.waves.gpu.WavesRechner;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ein Lauf über ein Gebiet (Werkbuch HalligHooge Teil A, 4 und 12.6, P5c; F29 A): verdrahtet die Module am Bus –
 * {@code antrieb} (ERA5), {@code tide} (EOT20 und Gleichgewichtstide) und {@code hydro} über die Gitterkette – und
 * führt sie im {@link Takt}. Generisch: Gebiet, Gitter und Zeit kommen aus der {@link Einstellung}, nichts davon ist
 * Hooge.
 * <p>
 * Die Schicht {@code com.dan.lauf} liegt über den Modulen: Sie kennt den Kern, {@code core.gpu}, alle Physikmodule und
 * seit P6e die Bildmodule; kein Modul kennt sie. Seit 0.3 (P6e) kann ein Lauf das Bild am Bus tragen
 * ({@link Einstellung#bild}, {@link BildModul}) und seine Stände ablegen ({@link Einstellung#archiv}, {@link ArchivModul}).
 * Seit 0.4 (P9b) rechnet auf Wunsch das Sediment mit ({@link Einstellung#sediment}). Seit 0.5 (P9c) wirkt sein Boden auf
 * hydro zurück ({@link Einstellung#bodenRueck}), mit morphologischem Faktor, lockerer Auflage, Spirale und auf Wunsch der
 * Rauheit aus dem Sediment; ein Lauf kann den Boden eines früheren übernehmen ({@link Einstellung#bodenAnfang}).
 */
public final class Lauf implements AutoCloseable {
    public static final String VERSION = "0.5";

    /** Was ein Lauf rechnet. */
    public static final class Einstellung {
        /** Ordner des Gebiets ({@code gebiet.txt}, Gitter, {@code tide/}, {@code antrieb/}). */
        public File gebiet;
        /** Szenenzeit 0 (UTC). */
        public Instant start;
        /**
         * Die Stufen: je Stufe die Namen der Gitter, gröbstes zuerst. Stufe 0 ist ein Gitter mit offenem Rand (G0), jede
         * weitere bekommt ihren Rand einseitig aus der vorigen; mehrere Gitter in einer Stufe sind eine Kette
         * (beidseitig). Vorgabe: G0 | G1 | G2.
         */
        public List<List<String>> stufen = List.of(List.of("G0"), List.of("G1"), List.of("G2"));
        /** Wind und Luftdruck aus ERA5 (sonst die Tide allein). */
        public boolean antrieb = true;
        /** Woher der Windschub kommt (P5e, F34): Wu in hydro (bis P5d), ERA5 oder Charnock mit α aus ERA5 (Vorgabe seit F34 B). */
        public Antrieb.Schub schub = Antrieb.Schub.CHARNOCK;
        /** Gleichgewichtstide auf den Gittern aus {@link #eq}. */
        public boolean gleichgewicht = true;
        /** Gitter mit Gleichgewichtstide (F33 A, P5d: alle Gitter der Kette; geplant waren nach F27 A G0 und G1). */
        public List<String> eq = List.of("G0", "G1", "G2", "G3", "G4");
        /** Auf der Karte (sonst die Referenz auf dem Prozessor). */
        public boolean karte;
        /** Kopplungsintervall aller Module (s) [G]. */
        public double kopplung = 300;
        /** Anlauf von Tide und Antrieb (s) [G]. */
        public double anlauf = 3 * 3600;
        /** Mittelwasser am offenen Rand und ruhender See am Anfang (m NHN) [G]. */
        public double mittel = 0;
        /** G4: Südwestecke (O, N in m) und Zellen je Seite; null bzw. 0: aus dem Rezept. */
        public double[] auge;
        public int augeZellen;
        /** G3 als Fenster: {i0, j0, ni, nj} in Zellen von G3, auf Zellgrenzen von G2 (Vielfache von 10); null: ganz G3. */
        public int[] fenster3;
        /** Das Bild am Bus (P6e): ein Leser {@code render} ohne Takt über den Gittern in UTM ({@link BildModul}). */
        public boolean bild;
        /** Ordner für das Archiv (P6e, F40 A; null: ohne): ein Modul {@code archiv} legt die Stände ab ({@link ArchivModul}). */
        public File archiv;
        /** Das Archiv legt ab dieser Modellzeit ab (s seit Start; 0: vom Start an). */
        public double archivAb;
        /** Gitter im Archiv, soweit im Lauf (F40: G2–G4). */
        public List<String> archivGitter = List.of("G2", "G3", "G4");
        /** Name des Laufs im Archiv. */
        public String name = "Lauf";
        /** Seegang (P8b): aus, auf der Karte oder auf dem Prozessor (Referenz). */
        public enum Seegang { AUS, KARTE, PROZESSOR }
        public Seegang seegang = Seegang.AUS;
        /** Stützstellen des Seegangs {Frequenzen, Richtungen} [G]: nach der Vorgabe aus P8a 28 × 36, Rückfall 24 × 24. */
        public int[] seegangStuetzen = {28, 36};
        /** Zellen der Gitter des Seegangs (m) [G, 7.3]: G2w über G2, G3w über G3 (im Lauf, also auch das Fenster). */
        public double seegangG2 = 500, seegangG3 = 100;
        /** Modellzeit (s), ab der der Seegang rechnet; davor legt er null ab (der Seegang braucht keinen Anlauf über Tage). */
        public double seegangAb;
        /** Kopplung des Seegangs (s) [G, 5.6]. */
        public double seegangKopplung = 1200;
        /** Rückwirkung (P8c): hydro liest {@code waves.force} auf den Gittern mit Seegang (G2, G3). */
        public boolean seegangRueckwirkung = true;

        /** Sediment (P9b): aus, auf der Karte oder auf dem Prozessor (Referenz); auf dem Gitter {@link #sedimentGitter}, wie hydro es rechnet. */
        public enum Sediment { AUS, KARTE, PROZESSOR }
        public Sediment sediment = Sediment.AUS;
        public String sedimentGitter = "G3";
        /** Schlick im Wasser am Anfang und im Zufluss am Rand des Gitters (kg/m³) [G]. */
        public double sedimentHintergrund = 0.02;
        /** Modellzeit (s), ab der das Sediment rechnet; davor bleibt sein Stand. */
        public double sedimentAb;
        /** Der Boden kommt alle so viele Sekunden ins Archiv (s) [G]. */
        public double bodenKopplung = 1200;
        /** Seit 0.5 (P9c, F81 A): Der Boden des Sediments wirkt zurück – hydro verschiebt die Tabellen von {@link #sedimentGitter} um {@code bed.dz}. */
        public boolean bodenRueck = true;
        /** Seit 0.5: Breite des Randstreifens (Zellen), in dem die Bodenänderung am Bus auf null geht [G]. */
        public int bodenRand = 10;
        /** Seit 0.5: Deckel der Bodenänderung je Intervall als Anteil der Tiefe (0: ohne) [G]. */
        public double bodenDeckel = 0.05;
        /** Seit 0.5: morphologischer Faktor (1: ohne). */
        public double morphFaktor = 1;
        /** Seit 0.5: lockere Auflage des Schlicks; Spiralströmung (hydro schreibt {@code water.spiral}); Rauheit von hydro auf losem Boden aus {@code surf.z0}. */
        public boolean auflage = true, spirale = true, rauheitSediment;
        /** Seit 0.5: Ordner eines Archivs, aus dessen letztem Stand der Boden beginnt (null: aus dem Kornfeld). */
        public File bodenAnfang;
        /**
         * Seit P13b (F153 A): die Salzwiese ({@code physics.vegetation}) auf G3 aus {@code vegetation/zonen} des
         * Gebiets: hydro liest {@code surf.z0} auf G3, das Sediment {@code veg.cover}.
         */
        public boolean vegetation;

        /** Weitere Module, die nach hydro angemeldet werden (Leser wie die Prüfung oder das Bild). */
        public List<Modul> weitere = new ArrayList<>();

        /** Die Stufen aus einem Text wie {@code "G0 | G1 | G2 G3 G4"}. */
        public Einstellung stufen(String text) {
            List<List<String>> l = new ArrayList<>();
            for (String s : text.split("\\|")) {
                List<String> st = new ArrayList<>();
                for (String n : s.trim().split("\\s+")) if (!n.isEmpty()) st.add(n);
                if (!st.isEmpty()) l.add(st);
            }
            stufen = l;
            return this;
        }

        public String stufenText() {
            List<String> t = new ArrayList<>();
            for (List<String> s : stufen) t.add(String.join(" ", s));
            return String.join(" | ", t);
        }
    }

    public final Einstellung e;
    public final Gebiet gebiet;
    public final Bus bus;
    public final Takt takt;
    public final Antrieb antrieb;
    public final Tide tide;
    public final HydroModul hydro;
    public final Rechnung rechnung;
    /** Das Bild am Bus (null ohne {@link Einstellung#bild}). */
    public final BildModul bild;
    /** Das Archiv (null ohne {@link Einstellung#archiv}). */
    public final ArchivModul archiv;
    /** Der Seegang (null ohne {@link Einstellung#seegang}). */
    public final WavesModul waves;
    /** Das Sediment (null ohne {@link Einstellung#sediment}). */
    public final SedimentModul sediment;
    /** Hinweise beim Verdrahten (Leser, die das Werkbuch nicht nennt). */
    public final List<String> hinweise;
    /** Zeit für den Aufbau (s): Lesen, Backen von G4, Einrichten, Verdrahten, Anfangsstand. */
    public double aufbau;

    private Lauf(Einstellung e, Gebiet gebiet, Bus bus, Antrieb antrieb, Tide tide, HydroModul hydro, Rechnung rechnung, List<String> hinweise, BildModul bild, ArchivModul archiv, WavesModul waves,
                 SedimentModul sediment) {
        this.waves = waves;
        this.sediment = sediment;
        this.bild = bild;
        this.archiv = archiv;
        this.e = e;
        this.gebiet = gebiet;
        this.bus = bus;
        this.antrieb = antrieb;
        this.tide = tide;
        this.hydro = hydro;
        this.rechnung = rechnung;
        this.hinweise = hinweise;
        takt = new Takt(bus);
    }

    /** Baut die Gitter und Module, verdrahtet den Bus und setzt alle auf die Zeit 0 ({@link Takt#beginn}). */
    public static Lauf bauen(Einstellung e) throws IOException {
        long n0 = System.nanoTime();
        Gebiet ge = Gebiet.lies(e.gebiet);
        List<Hydro[]> stufen = gitter(ge, e);
        Rechnung r = e.karte ? new KarteRechnung() : new Rechnung.Prozessor();
        Set<String> eq = new LinkedHashSet<>();
        List<com.dan.core.gitter.Gitter> eqGitter = new ArrayList<>();
        if (e.gleichgewicht)
            for (Hydro[] st : stufen)
                for (Hydro h : st)
                    if (e.eq.contains(h.gitter.name)) {
                        eq.add(h.gitter.name);
                        eqGitter.add(h.gitter);
                    }
        // Rückwirkung des Seegangs (P8c): die Gitter von hydro, über denen der Seegang ein eigenes Gitter hat
        Set<String> see = new LinkedHashSet<>();
        if (e.seegang != Einstellung.Seegang.AUS && e.seegangRueckwirkung && e.antrieb)
            for (Hydro[] st : stufen)
                for (Hydro x : st)
                    if (x.gitter.name.equals("G2") && e.seegangG2 > 0 || x.gitter.name.equals("G3") && e.seegangG3 > 0) see.add(x.gitter.name);
        HydroModul hy;
        try {
            hy = new HydroModul(stufen, r, e.kopplung, e.antrieb, e.schub != Antrieb.Schub.WU, eq, see);
            hy.seegangAb = e.seegangAb;
            if (e.sediment != Einstellung.Sediment.AUS) {                    // Rückwirkung des Bodens, Rauheit und Spirale (P9c)
                if (e.bodenRueck) hy.boden = Set.of(e.sedimentGitter);
                hy.rauheit = e.bodenRueck && e.rauheitSediment;
                if (e.spirale) hy.spirale = Set.of(e.sedimentGitter);
            }
        } catch (RuntimeException x) {
            r.close();
            throw x;
        }
        Hydro g0 = stufen.get(0)[0];
        String uk = ge.rezept("eot20.umkreis");
        Tide ti = new Tide(Tidenfeld.lies(e.gebiet), e.start, g0.gitter, ge.nass(g0.gitter.name), uk == null ? 2 : (int) Double.parseDouble(uk.split("\\s+")[0]), e.antrieb,
                eqGitter.toArray(new com.dan.core.gitter.Gitter[0]));
        ti.kopplung = e.kopplung;
        ti.anlauf = e.anlauf;
        ti.mittel = e.mittel;
        Antrieb an = null;
        if (e.antrieb) {
            an = new Antrieb(Wetterfeld.lies(e.gebiet, "era5", e.start), e.start);   // P13a: der Zeitraum, der den Start enthält
            an.kopplung = e.kopplung;
            an.anlauf = e.anlauf;
            an.schub = e.schub;
        }
        Bus bus = new Bus(Katalog.grundstufe());
        if (an != null) bus.anmelden(an);
        if (e.vegetation) {   // vor dem Anmelden: hydro meldet surf.z0 in anmelden an (P13b)
            Set<String> rg = new LinkedHashSet<>();
            for (Hydro[] st : stufen) for (Hydro x : st) if (x.gitter.name.equals("G3")) rg.add(x.gitter.name);
            hy.rauheitGitter = rg;
        }
        // Salzwiese (P13b): auf G3, wenn es im Lauf ist; hydro liest surf.z0 dort. Vor hydro angemeldet (Takt.beginn folgt der Anmeldung)
        com.dan.physics.vegetation.VegetationModul vm = null;
        if (e.vegetation) {
            com.dan.core.szene.Zonen zo = com.dan.core.szene.Zonen.lies(e.gebiet);
            if (zo == null) { r.close(); throw new IOException("Lauf: Salzwiese verlangt, aber " + new File(e.gebiet, com.dan.core.szene.Zonen.ORDNER) + " fehlt (Aufbereitung, Schritt zonen)"); }
            List<com.dan.core.gitter.Gitter> vg = new ArrayList<>();
            for (Hydro[] st : stufen) for (Hydro x : st) if (x.gitter.name.equals("G3")) vg.add(x.gitter);
            if (vg.isEmpty()) { r.close(); throw new IllegalArgumentException("Lauf: Salzwiese braucht G3 in den Stufen"); }
            vm = new com.dan.physics.vegetation.VegetationModul(vg, zo, com.dan.core.szene.Gestalt.lies(new File(e.gebiet, "gestalt")),
                    new com.dan.physics.vegetation.Salzwiese(), e.kopplung, e.start);
            bus.anmelden(vm);
        }
        bus.anmelden(ti).anmelden(hy);
        // Seegang (P8b): eigene Gitter über G2 und G3 (so weit im Lauf), einseitig
        WavesModul wm = null;
        List<com.dan.core.gitter.Gitter> seeGitter = new ArrayList<>();
        if (e.seegang != Einstellung.Seegang.AUS) {
            if (!e.antrieb) { r.close(); throw new IllegalArgumentException("Lauf: Seegang braucht den Antrieb (Wind)"); }
            for (Hydro[] st : stufen)
                for (Hydro x : st) {
                    com.dan.core.gitter.Gitter g = x.gitter;
                    double z = g.name.equals("G2") ? e.seegangG2 : g.name.equals("G3") ? e.seegangG3 : 0;
                    if (z <= 0) continue;
                    int nx = (int) Math.floor((g.x1() - g.x0) / z + 1e-9), ny = (int) Math.floor((g.y1() - g.y0) / z + 1e-9);
                    seeGitter.add(com.dan.core.gitter.Gitter.utm(g.name + "w", g.zone, g.x0, g.y0, z, nx, ny));
                }
            if (seeGitter.isEmpty()) { r.close(); throw new IllegalArgumentException("Lauf: Seegang braucht G2 oder G3 in den Stufen"); }
            WavesModul.Rechner wr = e.seegang == Einstellung.Seegang.KARTE ? new WavesRechner() : new WavesModul.Prozessor();
            wm = new WavesModul(seeGitter, e.seegangStuetzen[0], e.seegangStuetzen[1], Wetterfeld.lies(e.gebiet, Wetterfeld.SEEGANG, e.start), e.start, wr, e.seegangKopplung,
                    e.schub != Antrieb.Schub.WU);
            wm.ab = e.seegangAb;
            bus.anmelden(wm);
        }
        // Sediment (P9b): auf dem Gitter von hydro; der Kern bildet bed.dz aus seinem Beitrag
        SedimentModul sm = null;
        if (e.sediment != Einstellung.Sediment.AUS) {
            Hydro hs = null;
            for (Hydro[] st : stufen) for (Hydro x : st) if (x.gitter.name.equals(e.sedimentGitter)) hs = x;
            if (hs == null) { r.close(); if (wm != null) wm.rechner.close(); throw new IllegalArgumentException("Lauf: Sediment braucht " + e.sedimentGitter + " in den Stufen"); }
            boolean mitSee = false;
            for (com.dan.core.gitter.Gitter g : seeGitter) if (g.name.equals(e.sedimentGitter + "w")) mitSee = true;
            Kornfeld korn = Kornfeld.vorhanden(e.gebiet) ? Kornfeld.lies(e.gebiet) : null;
            SedimentModul.Rechner sr = e.sediment == Einstellung.Sediment.KARTE ? new SedimentRechner() : new SedimentModul.Prozessor();
            sm = new SedimentModul(hs.gitter, hs.packung, korn, sr, e.kopplung, mitSee, e.sedimentHintergrund);
            sm.ab = e.sedimentAb;
            sm.rueck = e.bodenRueck;
            sm.spirale = e.spirale;
            sm.rand = e.bodenRueck ? e.bodenRand : 0;
            sm.sediment.faktor = e.morphFaktor;
            sm.sediment.deckel = e.bodenDeckel;
            sm.sediment.mitAuflage = e.auflage;
            if (e.bodenAnfang != null) bodenAnfang(e.bodenAnfang, sm);
            sm.vegetation = vm != null && vm.gitter.contains(hs.gitter);   // vor dem Anmelden (liest dann veg.cover)
            bus.anmelden(sm);
            bus.anmelden(new Verbund(bus.katalog, "bed.dz", hs.gitter, Feld.Zahl.FLOAT, e.kopplung, "sediment"));
            if (hy.rauheit) bus.anmelden(sm.vegetation ? new Verbund(bus.katalog, "surf.z0", hs.gitter, Feld.Zahl.FLOAT, e.kopplung, "sediment", "vegetation")
                    : new Verbund(bus.katalog, "surf.z0", hs.gitter, Feld.Zahl.FLOAT, e.kopplung, "sediment"));
        }
        if (vm != null) for (com.dan.core.gitter.Gitter g : vm.gitter)
            if (sm == null || !hy.rauheit || !g.equals(sm.gitter)) bus.anmelden(new Verbund(bus.katalog, "surf.z0", g, Feld.Zahl.FLOAT, e.kopplung, "vegetation"));
        // Bild und Archiv (P6e): die Gitter in UTM, grob zuerst; der Wind auf G2, sonst dem gröbsten in UTM
        List<com.dan.core.gitter.Gitter> utm = new ArrayList<>();
        for (Hydro[] st : stufen) for (Hydro x : st) if (x.gitter.lage == com.dan.core.gitter.Gitter.Lage.UTM) utm.add(x.gitter);
        com.dan.core.gitter.Gitter wind = null;
        if (an != null) {
            for (com.dan.core.gitter.Gitter g : utm) if (g.name.equals("G2")) wind = g;
            if (wind == null && !utm.isEmpty()) wind = utm.get(0);
        }
        BildModul bm = null;
        ArchivModul am = null;
        if (e.bild) bus.anmelden(bm = new BildModul(utm, wind, sm != null ? sm.gitter : null, sm != null && e.bodenRueck));
        if (e.archiv != null) {
            List<com.dan.core.gitter.Gitter> ag = new ArrayList<>();
            for (com.dan.core.gitter.Gitter g : utm) if (e.archivGitter.contains(g.name)) ag.add(g);
            com.dan.core.archiv.Archiv.Lauf kopf = new com.dan.core.archiv.Archiv.Lauf();
            kopf.name = e.name;
            kopf.start = e.start;
            kopf.antrieb.put("stufen", e.stufenText());
            kopf.antrieb.put("tide", "EOT20 und Gleichgewichtstide, Anlauf " + e.anlauf / 3600 + " h, Mittel " + e.mittel + " m");
            kopf.antrieb.put("wetter", e.antrieb ? "ERA5, Windschub " + schubText(e.schub) : "ohne");
            if (vm != null) kopf.antrieb.put("vegetation", "Salzwiese " + com.dan.physics.vegetation.VegetationModul.VERSION + " auf " + vm.gitter.get(0).name + " aus vegetation/zonen (Rauheit an hydro, Bedeckung an sediment)");
            if (e.fenster3 != null) kopf.antrieb.put("fenster3", e.fenster3[0] + " " + e.fenster3[1] + " " + e.fenster3[2] + " " + e.fenster3[3]);
            if (wm != null) kopf.antrieb.put("seegang", e.seegangStuetzen[0] + " × " + e.seegangStuetzen[1] + " Stützstellen, Rand aus ERA5, ab " + e.seegangAb / 3600 + " h"
                    + (see.isEmpty() ? ", ohne Rückwirkung" : ", Rückwirkung auf hydro über " + String.join(", ", see) + " (Anlauf " + hy.seegangAnlauf / 3600 + " h)"));
            am = new ArchivModul(e.archiv.toPath(), ag, wind, e.kopplung, kopf);
            if (wm != null) am.seegang(seeGitter, e.seegangKopplung);
            if (sm != null) {
                kopf.antrieb.put("sediment", "auf " + e.sedimentGitter + (sm.seegang ? " mit Seegang" : " ohne Seegang") + ", Boden " + (korn(e) ? "aus dem Kornfeld des Gebiets" : "überall Sand 0,2 mm")
                        + ", Schlick am Rand " + e.sedimentHintergrund * 1000 + " g/m³"
                        + (e.bodenRueck ? ", Rückwirkung auf hydro (Randstreifen " + e.bodenRand + " Zellen, Deckel " + e.bodenDeckel + ")" : ", ohne Rückwirkung")
                        + ", Faktor " + e.morphFaktor + (e.auflage ? ", lockere Auflage" : "") + (e.spirale ? ", Spirale" : "") + (hy.rauheit ? ", Rauheit aus surf.z0" : "")
                        + (e.bodenAnfang != null ? ", Boden aus " + e.bodenAnfang : ""));
                am.sediment(sm.gitter, e.bodenKopplung);
            }
            bus.anmelden(am);
            am.ab = e.archivAb;
        }
        for (Modul m : e.weitere) bus.anmelden(m);
        List<String> h;
        try {
            h = bus.verdrahten();
        } catch (RuntimeException x) {
            r.close();
            if (wm != null) wm.rechner.close();
            if (sm != null) sm.rechner.close();
            throw x;
        }
        if (bm != null) bm.verbinde(bus);
        Lauf l = new Lauf(e, ge, bus, an, ti, hy, r, h, bm, am, wm, sm);
        try {
            l.takt.beginn(0);
        } catch (RuntimeException x) {
            r.close();
            if (wm != null) wm.rechner.close();
            if (sm != null) sm.rechner.close();
            throw x;
        }
        l.aufbau = (System.nanoTime() - n0) / 1e9;
        return l;
    }

    /** Die Gitter der Stufen aus dem Gebiet: Packung, Bauwerke (wo es sie gibt), G4 aus dem Stapel; ruhender See bei {@code mittel}. */
    static List<Hydro[]> gitter(Gebiet ge, Einstellung e) throws IOException {
        List<Hydro[]> stufen = new ArrayList<>();
        for (List<String> st : e.stufen) {
            Hydro[] h = new Hydro[st.size()];
            for (int k = 0; k < h.length; k++) {
                String n = st.get(k);
                if (n.equals("G4")) {
                    Gebiet.Auge au = e.auge == null ? ge.auge() : ge.auge(e.auge[0], e.auge[1], e.augeZellen);
                    h[k] = new Hydro(au.gitter(), au.packung());
                } else if (n.equals("G3") && e.fenster3 != null) {
                    int[] w = e.fenster3;
                    com.dan.core.gitter.Gitter g3 = ge.gitter("G3");
                    com.dan.core.gitter.Gitter gw = com.dan.core.gitter.Gitter.utm("G3", g3.zone, g3.x0 + w[0] * g3.dx, g3.y0 + w[1] * g3.dy, g3.dx, w[2], w[3]);
                    h[k] = new Hydro(gw, ge.packung("G3").fenster(g3, w[0], w[1], w[2], w[3]));
                    if (new File(ge.eintrag("G3").ordner(), "bauwerke.txt").isFile()) h[k].bauwerke(ge.bauwerke("G3"), w[0], w[1]);
                } else {
                    h[k] = new Hydro(ge.gitter(n), ge.packung(n));
                    if (new File(ge.eintrag(n).ordner(), "bauwerke.txt").isFile()) h[k].bauwerke(ge.bauwerke(n));
                }
                h[k].fuellen(e.mittel);
            }
            stufen.add(h);
        }
        return stufen;
    }

    private static boolean korn(Einstellung e) { return Kornfeld.vorhanden(e.gebiet); }

    /** Setzt den Boden des Sediments aus dem letzten Stand eines Archivs (P9c): Rippel, Schlickanteil, Bodenänderung, lockere Auflage. */
    private static void bodenAnfang(File archiv, SedimentModul sm) throws IOException {
        try (Archivquelle q = new Archivquelle(archiv.toPath())) {
            com.dan.core.gitter.Gitter g = q.sedimentGitter();
            double[] z = q.bodenZeiten();
            if (g == null || z.length == 0) throw new IllegalArgumentException("Lauf: " + archiv + " hält keinen Boden");
            if (g.nx != sm.gitter.nx || g.ny != sm.gitter.ny || Math.abs(g.x0 - sm.gitter.x0) > 1e-6 || Math.abs(g.y0 - sm.gitter.y0) > 1e-6)
                throw new IllegalArgumentException("Lauf: der Boden in " + archiv + " liegt auf " + g + ", das Sediment rechnet auf " + sm.gitter);
            int n = g.zellen();
            float[] rippel = new float[3 * n], schlick = new float[n], dz = new float[n], auflage = new float[n];
            double ts = z[z.length - 1];
            if (!q.boden(ts, rippel, schlick, dz)) throw new IllegalStateException("Lauf: Boden zum Stand " + ts + " in " + archiv + " nicht lesbar");
            sm.anfang(rippel, schlick, dz, q.auflage(ts, auflage) ? auflage : null);
        }
    }

    /** Die Module am Bus. */
    public List<Modul> module() { return bus.module(); }

    /** Rechnet, bis alle Module die Modellzeit t (s) erreicht haben. */
    public void bis(double t) { takt.bis(t); }

    /** Modellzeit, bis zu der alle Module gerechnet haben (s). */
    public double zeit() { return takt.zeit(); }

    /** Der Zeitpunkt zur Modellzeit t. */
    public Instant zeitpunkt(double t) { return e.start.plusMillis(Math.round(t * 1000)); }

    /** Bilanz der Stufe s: |V − V₀ − (Zufluss − Abfluss)| / Bezug, Abgeschnittenes zählt als Fehler (wie Fall 9); Bezug: größter Bestand oder Umsatz. */
    public double bilanz(int s) {
        double[] b = hydro.bilanz(s);
        double rest = b[1] - b[0] - (b[2] - b[3]);
        return Math.abs(rest) / Math.max(Math.max(b[0], b[1]), b[2] + b[3]);
    }

    @Override public String toString() {
        return String.format(Locale.ROOT, "Lauf %s über %s: Stufen %s, %s, Start %s, Kopplung %.0f s, Anlauf %.1f h, Mittel %+.2f m, %s", VERSION, gebiet.ordner, e.stufenText(),
                e.antrieb ? "mit Antrieb (ERA5, Windschub " + schubText(e.schub) + ")" : "Tide allein", e.start, e.kopplung, e.anlauf / 3600, e.mittel, rechnung.geraet());
    }

    /** Der Windschub in Worten. */
    public static String schubText(Antrieb.Schub s) {
        return switch (s) {
            case WU -> "nach Wu in hydro";
            case ERA5 -> "aus ERA5 (iews, inss)";
            case CHARNOCK -> "nach Charnock mit α aus ERA5";
        };
    }

    /** Schließt das Archiv (schreibt die Köpfe) und gibt die Rechnung frei. */
    @Override public void close() {
        try {
            if (archiv != null) archiv.close();
        } catch (java.io.IOException x) {
            throw new java.io.UncheckedIOException(x);
        } finally {
            rechnung.close();
            if (waves != null) waves.rechner.close();
            if (sediment != null) sediment.rechner.close();
        }
    }
}
