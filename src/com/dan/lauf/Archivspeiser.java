package com.dan.lauf;

import com.dan.core.archiv.Archiv;
import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Speist den Bus aus einem abgelegten Lauf (P10b, Werkbuch HalligHooge Teil A, 12.11, F88 A): Das Modul steht für
 * hydro und heißt auch so – der Bus lässt {@code water.*} nur von {@code hydro} schreiben. Es legt
 * {@code water.eta}, {@code water.h} und {@code water.wetAge} eines Gitters zu jeder Zeit so ab, wie sie im Archiv
 * stehen (zwischen zwei Ständen linear; zu den Zeiten der Stände ohne Rechnung).
 * <p>
 * <b>Vorlauf:</b> Zeiten vor dem ersten Stand bekommen den Stand einen ganzen Tag (oder mehrere) später: Der erste Tag
 * des Archivs dient als Vorlauf für sich selbst [G]. Die Tide liegt dann rund 50 min neben der wahren; für das
 * Einschwingen des Bodens reicht das.
 */
public final class Archivspeiser implements Modul, Closeable {
    /** Die Felder, die das Modul ablegt. */
    public static final String[] FELDER = {"water.eta", "water.h", "water.wetAge"};
    private static final String[] EINHEIT = {"m NHN", "m", "s"};
    private static final double TAG = 86400;

    public final Path ordner;
    public final Archiv archiv;
    public final Gitter gitter;
    public final Instant start;
    /** Zeit des ersten und des letzten Stands (s seit {@link #start}) und ihr Abstand. */
    public final double erste, letzte, kopplung;
    private final Feld[] felder = new Feld[FELDER.length];

    /** @param ordner der Unterordner eines Gitters im Archiv, etwa {@code …/archiv_ruhig/G3} */
    public Archivspeiser(Path ordner) throws IOException {
        this.ordner = ordner;
        archiv = Archiv.oeffnen(ordner);
        if (archiv.staende() < 2) throw new IOException(ordner + ": weniger als zwei Stände");
        Katalog k = Katalog.grundstufe();
        for (int q = 0; q < FELDER.length; q++) felder[q] = archiv.feld(FELDER[q], k);
        gitter = felder[0].gitter;
        start = archiv.lauf.start;
        erste = archiv.zeit(0);
        letzte = archiv.zeit(archiv.staende() - 1);
        kopplung = archiv.zeit(1) - erste;
    }

    @Override public String name() { return "hydro"; }

    @Override public String version() { return "aus dem Archiv " + ordner.getParent().getFileName() + " (hydro " + archiv.lauf.module.getOrDefault("hydro", "?") + ")"; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung);
        for (int q = 0; q < FELDER.length; q++) a.schreibt(FELDER[q], EINHEIT[q], Ort.ZELLE, gitter, Feld.Zahl.FLOAT);
    }

    /** Die Zeit im Archiv, deren Stand zur Modellzeit t gilt: t selbst, vor dem ersten Stand ganze Tage später. */
    public double archivzeit(double t) {
        if (t >= erste) return Math.min(t, letzte);
        double ta = t + TAG * Math.ceil((erste - t) / TAG);
        if (ta > letzte) throw new IllegalStateException("Archivspeiser: der Vorlauf braucht einen ganzen Tag im Archiv, es hält " + (letzte - erste) + " s");
        return ta;
    }

    @Override public void beginn(double t0, Bus bus) { lege(bus, t0); }

    @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }

    private void lege(Bus bus, double t) {
        double ta = archivzeit(t);
        try {
            for (int q = 0; q < FELDER.length; q++) {
                archiv.lies(FELDER[q], ta, felder[q]);
                Feld f = bus.schreibfeld(this, FELDER[q]);
                System.arraycopy(felder[q].f, 0, f.f, 0, f.laenge);
                bus.lege(this, FELDER[q], t);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override public void close() throws IOException { archiv.close(); }
}
