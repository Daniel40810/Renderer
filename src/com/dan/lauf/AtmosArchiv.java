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
import java.util.Map;

/**
 * Legt die Felder von atmos in den Ordner eines abgelegten Laufs (P10b, Werkbuch HalligHooge Teil A, 12.11): ein Modul
 * {@code archiv} am Bus, getaktet wie atmos. {@code surf.wet} (float16) und {@code surf.T} (float32: float16 löst bei
 * 285 K nur 0,25 K auf) in {@code <ordner>/atmos_<Gitter>/}, {@code air.n} (n − 1 in 64 Höhen, float32) in
 * {@code <ordner>/luft_<Gitter der Säulen>/}. {@link Archivquelle} liest beide neben dem Wasser.
 * <p>
 * Seit 0.2 (P10c) mit {@link #mitAeolian} dazu der Flugsand in {@code <ordner>/aeolian_<Gitter>/}: {@code bed.dz}
 * (float32; im Nachlauf allein der Anteil des Winds, vom Kern aus {@code bed.dz.aeolian} gebildet und darum ein
 * Kopplungsintervall hinter dem Modul) und {@code aeol.q} (float16), alle {@link #aeolianAbstand} Sekunden.
 * <p>
 * Nebenbei prüft es das Speisen aus dem Archiv ({@link #pruefe}): {@code water.h} am Bus gegen den Stand desselben
 * Archivs, über eine eigene Öffnung gelesen.
 */
public final class AtmosArchiv implements Modul, AutoCloseable {
    public static final String VERSION = "0.2";
    public static final String ATMOS = "atmos_", LUFT = "luft_", AEOLIAN = "aeolian_";

    private final Path ordner;
    private final Gitter zellen, saeulen;
    private final double kopplung;
    private final Archiv.Lauf kopf;
    private Archiv atmos, luft, wind;
    private Feld wet, ts, n, h, soll, dz, q;
    /** Seit 0.2, vor dem Anmelden: den Flugsand mit ablegen, alle so viele Sekunden (Vielfache seit dem Start) [G: wie der Boden]. */
    public boolean mitAeolian;
    public double aeolianAbstand = 1200;
    /** Abgelegte Stände des Flugsands. */
    public long staendeAeolian;
    /** Abgelegt wird ab dieser Modellzeit (s seit Start); davor läuft der Vorlauf. */
    public double ab;
    /** Abgelegte Stände und Byte; Zeit für das Ablegen (s). */
    public long staende, bytes;
    public double sekunden;
    /** Zum Prüfen des Speisens: das Archiv des Wassers, ein zweites Mal geöffnet; null ohne Prüfung. */
    public Archiv pruefe;
    /** Größter Unterschied zwischen {@code water.h} am Bus und im Archiv (m) und Zahl der verglichenen Stände. */
    public double groessterUnterschied;
    public long verglichen;

    public AtmosArchiv(Path ordner, Gitter zellen, Gitter saeulen, double kopplung, Archiv.Lauf kopf) {
        this.ordner = ordner;
        this.zellen = zellen;
        this.saeulen = saeulen;
        this.kopplung = kopplung;
        this.kopf = kopf;
    }

    @Override public String name() { return "archiv"; }

    @Override public String version() { return VERSION; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung).liestImIntervall("surf.wet", "–", Ort.ZELLE, zellen).liestImIntervall("surf.T", "K", Ort.ZELLE, zellen).liestImIntervall("air.n", "–", Ort.SAEULE, saeulen);
        if (pruefe != null) a.liestImIntervall("water.h", "m", Ort.ZELLE, zellen);
        if (mitAeolian) a.liestImIntervall("bed.dz", "m", Ort.ZELLE, zellen).liestImIntervall("aeol.q", "kg/(m s)", Ort.ZELLE, zellen);
    }

    @Override public void beginn(double t0, Bus bus) {
        try {
            for (Modul m : bus.module()) kopf.module.put(m.name(), m.version());
            wet = bus.lesefeld(this, "surf.wet", zellen);
            ts = bus.lesefeld(this, "surf.T", zellen);
            n = bus.lesefeld(this, "air.n", saeulen);
            atmos = Archiv.anlegen(ordner.resolve(ATMOS + zellen.name), kopie(kopf, ATMOS + zellen.name));
            atmos.spur(wet, Archiv.Ablage.HALB).spur(ts, Archiv.Ablage.FLOAT);
            luft = Archiv.anlegen(ordner.resolve(LUFT + saeulen.name), kopie(kopf, LUFT + saeulen.name));
            luft.spur(n, Archiv.Ablage.FLOAT);
            if (pruefe != null) {
                h = bus.lesefeld(this, "water.h", zellen);
                soll = h.gleiches();
            }
            if (mitAeolian) {
                dz = bus.lesefeld(this, "bed.dz", zellen);
                q = bus.lesefeld(this, "aeol.q", zellen);
                wind = Archiv.anlegen(ordner.resolve(AEOLIAN + zellen.name), kopie(kopf, AEOLIAN + zellen.name));
                wind.spur(dz, Archiv.Ablage.FLOAT).spur(q, Archiv.Ablage.HALB);
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
            for (Feld f : new Feld[] {wet, ts, n}) {
                double z = bus.lies(this, f.eintrag.name(), t, f);
                if (Math.abs(z - t) > 1e-9 * Math.max(1, Math.abs(t))) throw new IllegalStateException("archiv: " + f.eintrag.name() + " liegt bei " + z + " statt " + t);
            }
            atmos.stand(t, wet, ts);
            luft.stand(t, n);
            bytes += atmos.bytesJeStand() + luft.bytesJeStand();
            if (mitAeolian && Math.abs(t / aeolianAbstand - Math.rint(t / aeolianAbstand)) < 1e-9) {
                bus.lies(this, "bed.dz", t, dz);
                bus.lies(this, "aeol.q", t, q);
                wind.stand(t, dz, q);
                bytes += wind.bytesJeStand();
                staendeAeolian++;
            }
            if (pruefe != null) {
                bus.lies(this, "water.h", t, h);
                pruefe.lies("water.h", t, soll);
                for (int i = 0; i < h.laenge; i++) groessterUnterschied = Math.max(groessterUnterschied, Math.abs(h.hol(i) - soll.hol(i)));
                verglichen++;
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
        if (atmos != null) atmos.close();
        if (luft != null) luft.close();
        if (wind != null) wind.close();
        atmos = luft = wind = null;
        if (pruefe != null) pruefe.close();
        pruefe = null;
    }
}
