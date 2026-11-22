package com.dan.lauf;

import com.dan.core.archiv.Archiv;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Verbund;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Kornfeld;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.takt.Takt;
import com.dan.physics.aeolian.AeolianModul;
import com.dan.physics.antrieb.Antrieb;
import com.dan.physics.atmos.AtmosModul;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rechnet atmos zu einem abgelegten Lauf nach (P10b, Werkbuch HalligHooge Teil A, 12.11, F88 A): Der Bus bekommt das
 * Wasser aus dem Archiv ({@link Archivspeiser}, als {@code hydro}) und das Wetter vom Antrieb (ERA5: Wind und Druck wie im
 * Lauf, dazu Luft, Strahlung und Wassertemperatur); {@link AtmosModul} rechnet, {@link AtmosArchiv} legt
 * {@code surf.wet}, {@code surf.T} und {@code air.n} in den Ordner des Archivs. hydro rechnet nicht neu: atmos wirkt
 * nicht auf das Wasser zurück.
 * <p>
 * Seit 0.2 (P10c): Mit {@link Einstellung#regen} liest atmos den Regen aus ERA5 (F97 A). Mit
 * {@link Einstellung#aeolian} rechnet {@link AeolianModul} im selben Zug den Flugsand aus {@code surf.wet}, dem Wasser
 * und dem Wind, vom ersten Stand an; der Kern bildet {@code bed.dz} aus seinem Anteil allein, und {@link AtmosArchiv}
 * legt {@code bed.dz} und {@code aeol.q} in {@code aeolian_<Gitter>} ab.
 * <p>
 * <b>Zeit:</b> Modellzeit ist die des Archivs (s seit dessen Start). Der Lauf beginnt {@link Einstellung#vorlauf}
 * Sekunden vor dem ersten Stand; im Vorlauf gilt das Wasser des ersten Tags ({@link Archivspeiser#archivzeit}) und das
 * Wetter der wahren Zeit. Abgelegt wird ab dem ersten Stand.
 */
public final class Nachlauf implements AutoCloseable {
    public static final String VERSION = "0.2";

    public static final class Einstellung {
        /** Ordner des Gebiets ({@code gebiet.txt}, {@code antrieb/era5.*}, {@code antrieb/luft.*}). */
        public File gebiet;
        /** Ordner des abgelegten Laufs; darin der Unterordner {@link #gitter}. */
        public Path archiv;
        /** Wohin atmos abgelegt wird; null: in {@link #archiv}. Ohne {@link #ablegen} wird nichts geschrieben. */
        public Path ziel;
        public boolean ablegen = true;
        /** Das Gitter des Wassers, auf dem atmos rechnet. */
        public String gitter = "G3";
        /** Zellen je Säule in jeder Richtung (200 m bei 20 m) [G]. */
        public int teiler = 10;
        /** Vorlauf vor dem ersten Stand (s) [G]; ganze Tage. */
        public double vorlauf = 86400;
        /** Zeilen von atmos auf mehreren Fäden. */
        public boolean parallel = true;
        /** Das Speisen prüfen ({@link AtmosArchiv#pruefe}). */
        public boolean pruefen = true;
        /** Seit 0.2: atmos mit dem Regen aus ERA5 (F97 A); ohne ihn rechnet atmos wie in P10b. Gilt nur, wenn die Datei der Luft {@code tp} hält. */
        public boolean regen = true;
        /** Seit 0.2: den Flugsand mitrechnen und ablegen (F96 A); der morphologische Faktor dazu (F98 A: 1). */
        public boolean aeolian = true;
        public double faktor = 1;
        /**
         * Seit P13b (F153 A): die Salzwiese aus {@code vegetation/zonen} des Gebiets: atmos rechnet über ihr mit der Rauheit
         * der Pflanzen (A9), aeolian mit ihrer Bedeckung.
         */
        public boolean vegetation;
    }

    public final Einstellung e;
    public final Bus bus;
    public final Takt takt;
    public final Antrieb antrieb;
    public final Archivspeiser speiser;
    public final AtmosModul atmos;
    /** Null ohne {@link Einstellung#aeolian}. */
    public final AeolianModul flugsand;
    /** Null ohne Ablage und ohne Prüfung. */
    public final AtmosArchiv ablage;
    public final List<String> hinweise;
    /** Beginn (mit Vorlauf), erster und letzter Stand (s seit dem Start des Archivs). */
    public final double beginn, erste, letzte;

    private Nachlauf(Einstellung e, Bus bus, Antrieb an, Archivspeiser sp, AtmosModul at, AeolianModul am, AtmosArchiv ab, List<String> hinweise) {
        this.e = e;
        this.bus = bus;
        antrieb = an;
        speiser = sp;
        atmos = at;
        flugsand = am;
        ablage = ab;
        this.hinweise = hinweise;
        erste = sp.erste;
        letzte = sp.letzte;
        beginn = erste - e.vorlauf;
        takt = new Takt(bus);
    }

    /** Baut die Module, verdrahtet den Bus und setzt alle auf den Beginn. Alte Ordner von atmos im Ziel werden ersetzt. */
    public static Nachlauf bauen(Einstellung e) throws IOException {
        Archivspeiser sp = new Archivspeiser(e.archiv.resolve(e.gitter));
        try {
            if (!Wetterfeld.da(e.gebiet, Wetterfeld.LUFT, sp.start))
                throw new IOException("Nachlauf: " + Wetterfeld.LUFT + ".txt mit " + sp.start + " fehlt unter " + new File(e.gebiet, Wetterfeld.ORDNER)
                        + " (" + Wetterfeld.zeiten(e.gebiet, Wetterfeld.LUFT) + ") – Aufbereitung mit --schritte luft");
            Wetterfeld luft = Wetterfeld.lies(e.gebiet, Wetterfeld.LUFT, sp.start);
            Antrieb an = new Antrieb(Wetterfeld.lies(e.gebiet, "era5", sp.start), sp.start).mitLuft(luft);
            an.kopplung = sp.kopplung;
            an.anlauf = 0;
            AtmosModul at = new AtmosModul(sp.gitter, e.teiler);
            at.kopplung = sp.kopplung;
            at.atmos.parallel = e.parallel;
            at.mitRegen = e.regen && luft.groesse("tp") >= 0;
            at.grund = grund(Gebiet.lies(e.gebiet), sp.gitter);
            at.rauheit = e.vegetation;   // vor dem Anmelden: atmos meldet surf.z0 in anmelden an
            Bus bus = new Bus(Katalog.grundstufe());
            bus.anmelden(an).anmelden(sp);
            // Salzwiese vor atmos: atmos liest surf.z0 schon in beginn, Takt.beginn folgt der Anmeldung
            if (e.vegetation) {
                com.dan.core.szene.Zonen zo = com.dan.core.szene.Zonen.lies(e.gebiet);
                if (zo == null) throw new IOException("Nachlauf: Salzwiese verlangt, aber " + new File(e.gebiet, com.dan.core.szene.Zonen.ORDNER) + " fehlt (Aufbereitung, Schritt zonen)");
                com.dan.physics.vegetation.VegetationModul vm = new com.dan.physics.vegetation.VegetationModul(List.of(sp.gitter), zo,
                        com.dan.core.szene.Gestalt.lies(new File(e.gebiet, "gestalt")), new com.dan.physics.vegetation.Salzwiese(), sp.kopplung, sp.start);
                bus.anmelden(vm).anmelden(new Verbund(bus.katalog, "surf.z0", sp.gitter, Feld.Zahl.FLOAT, sp.kopplung, "vegetation"));
            }
            bus.anmelden(at);
            AeolianModul am = null;
            if (e.aeolian) {
                am = new AeolianModul(sp.gitter, e.teiler, Kornfeld.vorhanden(e.gebiet) ? Kornfeld.lies(e.gebiet) : null);
                am.kopplung = sp.kopplung;
                am.ab = sp.erste;
                am.aeolian.faktor = e.faktor;
                am.vegetation = e.vegetation;
                bus.anmelden(am).anmelden(new Verbund(bus.katalog, "bed.dz", sp.gitter, Feld.Zahl.FLOAT, sp.kopplung, "aeolian"));
            }
            AtmosArchiv ab = null;
            if (e.ablegen || e.pruefen) {
                Path ziel = e.ziel != null ? e.ziel : e.archiv;
                if (!e.ablegen) ziel = Files.createTempDirectory("nachlauf");
                for (String n : new String[] {AtmosArchiv.ATMOS + sp.gitter.name, AtmosArchiv.LUFT + at.saeulen.name}) loesche(ziel.resolve(n));
                if (e.aeolian) loesche(ziel.resolve(AtmosArchiv.AEOLIAN + sp.gitter.name));
                Archiv.Lauf kopf = new Archiv.Lauf();
                kopf.name = "Nachlauf atmos zu " + sp.archiv.lauf.name;
                kopf.start = sp.start;
                kopf.seed = sp.archiv.lauf.seed;
                for (Map.Entry<String, String> x : sp.archiv.lauf.antrieb.entrySet()) kopf.antrieb.put(x.getKey(), x.getValue());
                kopf.antrieb.put("atmos", "Nachlauf " + VERSION + " aus dem Archiv, Luft und Strahlung aus ERA5, Vorlauf " + e.vorlauf / 3600 + " h, Säulen " + at.saeulen.dx + " m" + (at.mitRegen ? ", mit Regen" : ", ohne Regen"));
                if (am != null) kopf.antrieb.put("aeolian", "Nachlauf " + VERSION + ": Kawamura C " + am.aeolian.c + ", Bagnold A " + am.aeolian.a + ", Belly bis " + am.aeolian.wMax + " %, z0 " + am.aeolian.z0 + " m, Faktor " + e.faktor
                        + (Kornfeld.vorhanden(e.gebiet) ? ", Boden aus dem Kornfeld des Gebiets" : ", überall Sand 0,2 mm") + "; bed.dz ist hier allein der Anteil des Winds");
                ab = new AtmosArchiv(ziel, sp.gitter, at.saeulen, sp.kopplung, kopf);
                ab.ab = sp.erste;
                ab.mitAeolian = am != null;
                if (e.pruefen) ab.pruefe = Archiv.oeffnen(e.archiv.resolve(e.gitter));
                bus.anmelden(ab);
            }
            List<String> h = bus.verdrahten();
            Nachlauf n = new Nachlauf(e, bus, an, sp, at, am, ab, h);
            if (!an.deckt(n.beginn) || !an.deckt(n.letzte))
                throw new IOException("Nachlauf: das Wetter reicht nicht von " + an.zeit(n.beginn) + " bis " + an.zeit(n.letzte));
            n.takt.beginn(n.beginn);
            return n;
        } catch (IOException | RuntimeException x) {
            sp.close();
            throw x;
        }
    }

    /** Zellmittel des Geländes (m NHN) auf dem Gitter g aus dem Ordner des Gebiets. */
    public static float[] grundDesGebiets(File gebiet, Gitter g) throws IOException { return grund(Gebiet.lies(gebiet), g); }

    /** Zellmittel des Geländes (m NHN) auf dem Gitter g: der Ausschnitt aus dem Gitter gleichen Namens im Gebiet. */
    static float[] grund(Gebiet ge, Gitter g) throws IOException {
        Gitter voll = ge.gitter(g.name);
        float[] z = ge.groundZ(g.name);
        if (voll.gleich(g)) return z;
        int i0 = (int) Math.round((g.x0 - voll.x0) / voll.dx), j0 = (int) Math.round((g.y0 - voll.y0) / voll.dy);
        if (Math.abs(voll.dx - g.dx) > 1e-9 || i0 < 0 || j0 < 0 || i0 + g.nx > voll.nx || j0 + g.ny > voll.ny || Math.abs(voll.x0 + i0 * voll.dx - g.x0) > 1e-6
                || Math.abs(voll.y0 + j0 * voll.dy - g.y0) > 1e-6)
            throw new IOException("Nachlauf: " + g + " ist kein Fenster von " + voll);
        float[] a = new float[g.zellen()];
        for (int j = 0; j < g.ny; j++) System.arraycopy(z, i0 + voll.nx * (j0 + j), a, g.nx * j, g.nx);
        return a;
    }

    private static void loesche(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            for (Path q : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(q);
        }
    }

    /** Rechnet bis zum letzten Stand des Archivs. */
    public void rechne() { takt.bis(letzte); }

    /** Rechnet bis zur Modellzeit t (s seit dem Start des Archivs). */
    public void bis(double t) { takt.bis(Math.min(t, letzte)); }

    @Override public void close() throws IOException {
        speiser.close();
        if (ablage != null) ablage.close();
    }
}
