package com.dan.lauf;

import com.dan.core.archiv.Archiv;
import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Das Archiv eines Laufs für das Bild (P6e, Werkbuch HalligHooge Teil A, 12.7 und 5.7; F40 A): ein Modul
 * {@code archiv} am Bus, getaktet wie hydro. Es legt am Ende jedes Intervalls {@code water.eta} und {@code water.h}
 * (float16) und {@code water.wetAge} (float32) der genannten Gitter ab, dazu {@code air.u10} und {@code air.v10}
 * (float16) auf dem Gitter des Winds.
 * <p>
 * <b>Ablage:</b> je Gitter ein Lauf nach {@code core.archiv} in einem Unterordner ({@code <ordner>/G3/lauf.txt},
 * {@code water.eta.f16}, …), der Wind in {@code <ordner>/wind/}. Ein Lauf von {@code core.archiv} hält je Feld eine Spur;
 * so bleiben die Dateien so, wie 5.7 sie beschreibt. {@link Archivquelle} spielt ihn ab.
 * <p>
 * <b>Wann gelesen wird:</b> Die Felder sind im Intervall angemeldet ({@link Anmeldung#liestImIntervall}): Der Takt lässt
 * hydro bis zum Ende des Intervalls rechnen, bevor das Archiv ablegt; abgelegt wird der Stand zur Zeit t1 (und beim
 * Beginn der zur Zeit t0). Der Bus kopiert ihn ohne zu mitteln.
 * <p>
 * <b>Seegang (seit 0.2, P8b):</b> mit {@link #seegang} dazu {@code waves.hs}, {@code .tp}, {@code .dir}, {@code .mss},
 * {@code .diss} (float16) der Gitter des Seegangs in {@code <ordner>/waves_<Gitter>/}, nur zu den Zeiten, die ein
 * Vielfaches der Kopplung des Seegangs sind (dann liegt dort ein Stand des Seegangs, nichts ist gemittelt).
 * <p>
 * <b>Sediment (seit 0.3, P9b):</b> mit {@link #sediment} dazu {@code sed.c} (Sand und Schlick, float16) zu jedem Stand
 * in {@code <ordner>/sediment_<Gitter>/} und der Boden – {@code bed.ripple} (Höhe, Länge, Richtung) und {@code bed.mud}
 * (float16), {@code bed.dz} (float32) – in {@code <ordner>/boden_<Gitter>/} zu den Zeiten, die ein Vielfaches von
 * dessen Kopplung sind (der Boden ändert sich langsam).
 */
public final class ArchivModul implements Modul, AutoCloseable {
    public static final String VERSION = "0.4";
    /** Die Felder des Seegangs im Archiv, in dieser Folge (P8b). */
    public static final String[] SEEGANG = {"waves.hs", "waves.tp", "waves.dir", "waves.mss", "waves.diss"};
    private static final String[] SEE_EINHEIT = {"m", "s", "°", "–", "W/m²"};
    private List<Gitter> seeGitter = List.of();
    private double seeKopplung;
    private final List<Archiv> seeArchive = new ArrayList<>();
    private Feld[][] seeFelder;
    /** Abgelegte Stände des Seegangs. */
    public long seeStaende;
    /** Die Felder des Bodens im Archiv, in dieser Folge (P9b). */
    public static final String[] BODEN = {"bed.ripple", "bed.mud", "bed.dz"};
    /** Was in {@code boden_<Gitter>} liegt: die Felder für das Bild und seit 0.4 (P9c) die lockere Auflage {@code bed.fluff} (float32), damit ein Lauf den Boden eines früheren übernehmen kann. */
    public static final String[] BODEN_ABLAGE = {"bed.ripple", "bed.mud", "bed.dz", "bed.fluff"};
    private static final String[] BODEN_EINHEIT = {"m, m, °", "–", "m", "kg/m²"};
    private Gitter sedGitter;
    private double bodenKopplung;
    private Archiv sedArchiv, bodenArchiv;
    private Feld sedFeld;
    private Feld[] bodenFelder;
    /** Abgelegte Stände des Schwebstoffs und des Bodens. */
    public long sedStaende, bodenStaende;
    private final Path ordner;
    private final List<Gitter> gitter;
    private final Gitter wind;
    private final double kopplung;
    private final Archiv.Lauf kopf;
    private final List<Archiv> archive = new ArrayList<>();
    private Archiv windArchiv;
    private Feld[][] felder;
    private Feld[] windFelder;
    /** Abgelegt wird ab dieser Modellzeit (s seit Start); davor rechnet der Lauf nur an (etwa die Tage vor einer Sturmflut). */
    public double ab;
    /** Abgelegte Stände und Byte. */
    public long staende, bytes;
    /** Zeit für das Ablegen (s). */
    public double sekunden;

    /**
     * @param kopf Name, Start und Antrieb des Laufs (die Module trägt das Archiv beim Beginn nach)
     */
    public ArchivModul(Path ordner, List<Gitter> gitter, Gitter wind, double kopplung, Archiv.Lauf kopf) {
        this.ordner = ordner;
        this.gitter = List.copyOf(gitter);
        this.wind = wind;
        this.kopplung = kopplung;
        this.kopf = kopf;
    }

    @Override public String name() { return "archiv"; }

    @Override public String version() { return VERSION; }

    public Path ordner() { return ordner; }

    /** Legt dazu den Seegang dieser Gitter ab, alle {@code kopplung} Sekunden (vor {@code Bus.verdrahten}). */
    public void seegang(List<Gitter> gitter, double kopplung) {
        seeGitter = List.copyOf(gitter);
        seeKopplung = kopplung;
    }

    /** Legt dazu Schwebstoff (jeden Stand) und Boden (alle {@code bodenKopplung} Sekunden) dieses Gitters ab (vor {@code Bus.verdrahten}). */
    public void sediment(Gitter gitter, double bodenKopplung) {
        sedGitter = gitter;
        this.bodenKopplung = bodenKopplung;
    }

    public List<Gitter> gitter() { return gitter; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung);
        for (Gitter g : gitter)
            a.liestImIntervall("water.eta", "m NHN", Ort.ZELLE, g).liestImIntervall("water.h", "m", Ort.ZELLE, g).liestImIntervall("water.wetAge", "s", Ort.ZELLE, g);
        if (wind != null) a.liestImIntervall("air.u10", "m/s", Ort.ZELLE, wind).liestImIntervall("air.v10", "m/s", Ort.ZELLE, wind);
        for (Gitter g : seeGitter) for (int q = 0; q < SEEGANG.length; q++) a.liestImIntervall(SEEGANG[q], SEE_EINHEIT[q], Ort.ZELLE, g);
        if (sedGitter != null) {
            a.liestImIntervall("sed.c", "kg/m³", Ort.ZELLE, sedGitter);
            for (int q = 0; q < BODEN_ABLAGE.length; q++) a.liestImIntervall(BODEN_ABLAGE[q], BODEN_EINHEIT[q], Ort.ZELLE, sedGitter);
        }
    }

    @Override public void beginn(double t0, Bus bus) {
        try {
            for (Modul m : bus.module()) kopf.module.put(m.name(), m.version());
            felder = new Feld[gitter.size()][];
            for (int k = 0; k < gitter.size(); k++) {
                Gitter g = gitter.get(k);
                Feld[] f = {bus.lesefeld(this, "water.eta", g), bus.lesefeld(this, "water.h", g), bus.lesefeld(this, "water.wetAge", g)};
                Archiv a = Archiv.anlegen(ordner.resolve(g.name), kopie(kopf, g.name));
                a.spur(f[0], Archiv.Ablage.HALB).spur(f[1], Archiv.Ablage.HALB).spur(f[2], Archiv.Ablage.FLOAT);
                archive.add(a);
                felder[k] = f;
            }
            if (wind != null) {
                windFelder = new Feld[] {bus.lesefeld(this, "air.u10", wind), bus.lesefeld(this, "air.v10", wind)};
                windArchiv = Archiv.anlegen(ordner.resolve("wind"), kopie(kopf, "wind"));
                windArchiv.spur(windFelder[0], Archiv.Ablage.HALB).spur(windFelder[1], Archiv.Ablage.HALB);
            }
            seeFelder = new Feld[seeGitter.size()][];
            for (int k = 0; k < seeGitter.size(); k++) {
                Gitter g = seeGitter.get(k);
                Feld[] f = new Feld[SEEGANG.length];
                Archiv a = Archiv.anlegen(ordner.resolve("waves_" + g.name), kopie(kopf, "waves_" + g.name));
                for (int q = 0; q < f.length; q++) {
                    f[q] = bus.lesefeld(this, SEEGANG[q], g);
                    a.spur(f[q], Archiv.Ablage.HALB);
                }
                seeArchive.add(a);
                seeFelder[k] = f;
            }
            if (sedGitter != null) {
                sedFeld = bus.lesefeld(this, "sed.c", sedGitter);
                sedArchiv = Archiv.anlegen(ordner.resolve("sediment_" + sedGitter.name), kopie(kopf, "sediment_" + sedGitter.name));
                sedArchiv.spur(sedFeld, Archiv.Ablage.HALB);
                bodenFelder = new Feld[BODEN_ABLAGE.length];
                bodenArchiv = Archiv.anlegen(ordner.resolve("boden_" + sedGitter.name), kopie(kopf, "boden_" + sedGitter.name));
                for (int q = 0; q < BODEN_ABLAGE.length; q++) {
                    bodenFelder[q] = bus.lesefeld(this, BODEN_ABLAGE[q], sedGitter);
                    bodenArchiv.spur(bodenFelder[q], BODEN_ABLAGE[q].equals("bed.dz") || BODEN_ABLAGE[q].equals("bed.fluff") ? Archiv.Ablage.FLOAT : Archiv.Ablage.HALB);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        lege(t0, bus);
    }

    @Override public void schritt(double t0, double t1, Bus bus) { lege(t1, bus); }

    private void lege(double t, Bus bus) {
        if (t < ab - 1e-9 * Math.max(1, Math.abs(ab))) return;
        long n0 = System.nanoTime();
        try {
            for (int k = 0; k < gitter.size(); k++) {
                Feld[] f = felder[k];
                for (int q = 0; q < f.length; q++) {
                    double z = bus.lies(this, f[q].eintrag.name(), t, f[q]);
                    if (Math.abs(z - t) > 1e-9 * Math.max(1, Math.abs(t))) throw new IllegalStateException("archiv: " + f[q].eintrag.name() + " auf " + gitter.get(k).name + " liegt bei " + z + " statt " + t);
                }
                archive.get(k).stand(t, f);
                bytes += archive.get(k).bytesJeStand();
            }
            if (windArchiv != null) {
                for (Feld f : windFelder) bus.lies(this, f.eintrag.name(), t, f);
                windArchiv.stand(t, windFelder);
                bytes += windArchiv.bytesJeStand();
            }
            double v = seeKopplung > 0 ? t / seeKopplung : 0.5;
            if (!seeGitter.isEmpty() && Math.abs(v - Math.rint(v)) < 1e-9 * Math.max(1, Math.abs(v))) {
                for (int k = 0; k < seeGitter.size(); k++) {
                    for (Feld f : seeFelder[k]) {
                        double z = bus.lies(this, f.eintrag.name(), t, f);
                        if (Math.abs(z - t) > 1e-6 * Math.max(1, Math.abs(t))) throw new IllegalStateException("archiv: " + f.eintrag.name() + " auf " + seeGitter.get(k).name + " liegt bei " + z + " statt " + t);
                    }
                    seeArchive.get(k).stand(t, seeFelder[k]);
                    bytes += seeArchive.get(k).bytesJeStand();
                }
                seeStaende++;
            }
            if (sedGitter != null) {
                double z = bus.lies(this, "sed.c", t, sedFeld);
                if (Math.abs(z - t) > 1e-6 * Math.max(1, Math.abs(t))) throw new IllegalStateException("archiv: sed.c auf " + sedGitter.name + " liegt bei " + z + " statt " + t);
                sedArchiv.stand(t, sedFeld);
                bytes += sedArchiv.bytesJeStand();
                sedStaende++;
                double w = bodenKopplung > 0 ? t / bodenKopplung : 0.5;
                if (Math.abs(w - Math.rint(w)) < 1e-9 * Math.max(1, Math.abs(w))) {
                    for (Feld f : bodenFelder) bus.lies(this, f.eintrag.name(), t, f);
                    bodenArchiv.stand(t, bodenFelder);
                    bytes += bodenArchiv.bytesJeStand();
                    bodenStaende++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        staende++;
        sekunden += (System.nanoTime() - n0) / 1e9;
    }

    private static Archiv.Lauf kopie(Archiv.Lauf k, String teil) {
        Archiv.Lauf l = new Archiv.Lauf();
        l.name = k.name + " / " + teil;
        l.start = k.start;
        l.seed = k.seed;
        for (Map.Entry<String, String> e : k.antrieb.entrySet()) l.antrieb.put(e.getKey(), e.getValue());
        l.module.putAll(k.module);
        return l;
    }

    /** Schreibt die Köpfe ({@code lauf.txt}) und schließt die Dateien. */
    @Override public void close() throws IOException {
        for (Archiv a : archive) a.close();
        archive.clear();
        if (windArchiv != null) windArchiv.close();
        windArchiv = null;
        for (Archiv a : seeArchive) a.close();
        seeArchive.clear();
        if (sedArchiv != null) sedArchiv.close();
        if (bodenArchiv != null) bodenArchiv.close();
        sedArchiv = bodenArchiv = null;
    }
}
