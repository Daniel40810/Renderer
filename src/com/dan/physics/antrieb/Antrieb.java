package com.dan.physics.antrieb;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Wetterfeld;

import java.time.Instant;

/**
 * Der Antrieb aus ERA5 als Modul am Bus (Werkbuch HalligHooge Teil A, 7.2 und 12.6, P5c; F28 A): schreibt
 * {@code air.u10}, {@code air.v10} (m/s, nach Osten und Norden) und {@code air.p} (Pa, Luftdruck auf Meereshöhe) auf
 * dem Raster von ERA5 als Gitter: je Punkt eine Zelle mit dem Punkt in der Mitte ({@link #gitter(Gradraster)}). Wer
 * auf einem anderen Gitter liest, bekommt die Werte vom Bus bilinear umgerechnet; zwischen den Punkten ist das dasselbe
 * wie {@link Gradraster#gewichte} ohne NaN.
 * <ul>
 * <li><b>Zeit:</b> ERA5 stündlich, dazwischen linear ({@link Wetterfeld}). Modellzeit t ist Sekunden seit
 * {@link #start}. Liegt ein Zeitpunkt außerhalb der Datei, ist das ein Fehler, kein stiller Ersatz.</li>
 * <li><b>Anlauf:</b> Wind und Abweichung des Luftdrucks von {@link #p0} fahren über {@link #anlauf} ohne Knick von null
 * an, r(t) = ½ (1 − cos(π t / Anlauf)), wie die Tide ({@code physics.tide}). Am Anfang steht ein ruhender See.</li>
 * <li><b>Windschub</b> (seit 0.2, P5e, F34): mit {@link #schub} außer {@link Schub#WU} schreibt der Antrieb dazu
 * {@code air.taux}, {@code air.tauy} (N/m², auf das Wasser, nach Osten und Norden), und hydro nimmt diese statt seiner
 * Formel nach Wu. {@link Schub#ERA5}: {@code iews}, {@code inss} aus ERA5, mal r(t). {@link Schub#CHARNOCK}: τ = ρ_L C_d |U| U
 * auf den Wind r(t) · U aus ERA5 mit C_d = (κ / ln(10 m / z₀))², z₀ = α u*² / g + 0,11 ν / u* (Charnock 1955 mit
 * zähem Anteil), α je Punkt und Stunde aus ERA5 ({@code charnock}, über Land aus den Nachbarn über See), wie Özkan u. a.
 * (2025, GTSM mit veränderlichem Charnock); gerechnet an den Punkten von ERA5, der Bus rechnet auf die Gitter um.</li>
 * <li><b>Luft, Strahlung, Wassertemperatur</b> (seit 0.3, P10b, F90 A): mit {@link #mitLuft} schreibt der Antrieb aus einem
 * zweiten Wetterfeld ({@link Wetterfeld#LUFT}, eigenes Raster) dazu {@code air.t2} (K), {@code air.q2} (kg/kg, aus dem
 * Taupunkt und dem Luftdruck über Wasser: q = ε e / (p − (1 − ε) e) mit e nach IAPWS-IF97 wie in
 * {@code physics.atmos}), {@code air.sw}, {@code air.lw} (W/m², Sonnen- und Gegenstrahlung am Boden nach unten) und
 * {@code water.sst} (K). Die Strahlung liegt in der Datei als Mittel der Stunde vor der Zeit; sie gilt hier zur Mitte
 * dieser Stunde, dazwischen linear [G]. Ohne Anlauf: Diese Felder treiben hydro nicht.</li>
 * <li><b>Regler</b> (seit 0.5, Steuerhaus Y2, Werkbuch HalligHooge 153): {@link #windFaktorZiel} und
 * {@link #windDrehungZiel} (aus jedem Faden setzbar) machen aus dem Wind aus ERA5 einen anderen: mal Faktor, um den Winkel
 * im Uhrzeigersinn gedreht (der Wind „dreht rechts“). Der Antrieb fährt die Ziele in der Modellzeit an
 * ({@link #rampeFaktor}, {@link #rampeDrehung}); der Windschub folgt dem geänderten Wind (Charnock) oder wird mit dem
 * Quadrat des Faktors gedreht (ERA5). Luftdruck, Luft und Strahlung bleiben. Ohne Ziel bleibt alles bitgleich.</li>
 * </ul>
 * Kennt den Kern und kein anderes Modul (Schichtenvertrag, Werkbuch 4).
 */
public final class Antrieb implements Modul {
    public static final String VERSION = "0.5";

    /** Woher der Windschub kommt (P5e, F34). */
    public enum Schub {
        /** hydro rechnet ihn selbst aus dem Wind nach Wu (1982); der Antrieb schreibt nur Wind und Luftdruck (bis P5d). */
        WU,
        /** {@code iews}, {@code inss} aus ERA5 (F34 A). */
        ERA5,
        /** Charnock mit dem Feld α aus ERA5 auf den Wind aus ERA5 (F34 B). */
        CHARNOCK
    }

    public final Wetterfeld wetter;
    /** Szenenzeit 0 (UTC). */
    public final Instant start;
    /** Das Raster von ERA5 als geographisches Gitter: Zellmitten auf den Punkten. */
    public final Gitter gitter;
    /** Kopplungsintervall (s) [G]: so oft legt der Antrieb ab; hydro liest im Intervall linear dazwischen. */
    public double kopplung = 300;
    /** Anlauf (s) [G]. */
    public double anlauf = 3 * 3600;
    /** Bezugsdruck (Pa) für den Anlauf und das umgekehrte Barometer. */
    public double p0 = 101_325;
    /** Woher der Windschub kommt; vor dem Anmelden setzen. */
    public Schub schub = Schub.WU;
    /** Dichte der Luft (kg/m³) für {@link Schub#CHARNOCK}, wie {@code Hydro.rhoL} [G]. */
    public double rhoL = 1.25;
    /** von Kármán (IFS, Özkan u. a. 2025), zähe Länge der Luft (m²/s), Schwere (m/s²). */
    public static final double KARMAN = 0.40, NU = 1.5e-5, G = 9.81;
    private final int iu, iv, ip;
    private int ix = -1, iy = -1, ia = -1;
    private double[] tx, ty;
    /** Seit 0.3: Luft, Strahlung und Wassertemperatur ({@link #mitLuft}); null ohne. */
    public Wetterfeld luft;
    /** Das Raster der Luft als Gitter (anderer Name als {@link #gitter}). */
    public Gitter luftGitter;
    private int[] il;
    /** Seit 0.5 (Y2): Ziele des Reglers – Faktor auf den Wind (1: wie ERA5) und Drehung (Grad, im Uhrzeigersinn). */
    public volatile double windFaktorZiel = 1, windDrehungZiel = 0;
    /** Seit 0.5: wie schnell der Regler folgt, je Stunde Modellzeit: Faktor und Grad [G]. */
    public double rampeFaktor = 2, rampeDrehung = 180;
    private volatile double windFaktor = 1, windDrehung = 0;
    private double tRegler = Double.NaN;

    /** Seit 0.4 (P10c, F97 A): Stelle des Niederschlags ({@code tp}, m je Stunde) im Wetterfeld der Luft; −1: Die Datei hat keinen, {@code air.rain} wird nicht geschrieben. */
    private int iRegen = -1;

    public Antrieb(Wetterfeld wetter, Instant start) {
        this.wetter = wetter;
        this.start = start;
        gitter = gitter(wetter.raster);
        iu = wetter.groesse("u10");
        iv = wetter.groesse("v10");
        ip = wetter.groesse("msl");
        if (iu < 0 || iv < 0 || ip < 0) throw new IllegalArgumentException("Antrieb: das Wetterfeld braucht u10, v10, msl, hat " + wetter.groessen);
    }

    /** Seit 0.3 (P10b): dazu Luft, Strahlung und Wassertemperatur aus diesem Wetterfeld; vor dem Anmelden. */
    public Antrieb mitLuft(Wetterfeld l) {
        String[] n = {"t2", "d2", "ssrd", "strd", "sst"};
        il = new int[n.length];
        for (int k = 0; k < n.length; k++) {
            il[k] = l.groesse(n[k]);
            if (il[k] < 0) throw new IllegalArgumentException("Antrieb: das Wetterfeld der Luft braucht t2, d2, ssrd, strd, sst, hat " + l.groessen);
        }
        iRegen = l.groesse("tp");
        luft = l;
        Gradraster r = l.raster;
        luftGitter = Gitter.geografisch("ERA5luft", r.laenge0() - 0.5 * r.dLaenge(), r.breite0() - 0.5 * r.dBreite(), r.dLaenge(), r.dBreite(), r.nx(), r.ny());
        return this;
    }

    /** Dampfdruck über Wasser (Pa) bei der Temperatur T (K): IAPWS-IF97, dieselbe Gleichung wie in {@code physics.atmos} (Prüfstein 2 von P10b). */
    public static double dampfdruck(double T) {
        double o = T - 2.38555575678e-1 / (T - 6.50175348448e2);
        double a = o * o + 1.16705214528e3 * o - 7.24213167032e5, b = -1.70738469401e1 * o * o + 1.20208247025e4 * o - 3.23255503223e6,
                c = 1.49151086135e1 * o * o - 4.82326573616e3 * o + 4.05113405421e5;
        double x = 2 * c / (-b + Math.sqrt(b * b - 4 * a * c));
        double x2 = x * x;
        return 1e6 * x2 * x2;
    }

    /** Spezifische Feuchte (kg/kg) aus dem Taupunkt (K) und dem Druck (Pa). */
    public static double feuchte(double taupunkt, double p) {
        double e = dampfdruck(taupunkt), eps = 0.018015 / 0.0289635;
        return eps * e / (p - (1 - eps) * e);
    }

    /** Das Gitter zu einem Gradraster: je Punkt eine Zelle mit dem Punkt in der Mitte. */
    public static Gitter gitter(Gradraster r) {
        return Gitter.geografisch("ERA5", r.laenge0() - 0.5 * r.dLaenge(), r.breite0() - 0.5 * r.dBreite(), r.dLaenge(), r.dBreite(), r.nx(), r.ny());
    }

    /** Anteil des Antriebs zur Zeit t beim Anlauf über {@code anlauf} Sekunden (wie {@code hydro.Vorgaberand.anteil}). */
    public static double anteil(double t, double anlauf) { return anlauf <= 0 || t >= anlauf ? 1 : t <= 0 ? 0 : 0.5 * (1 - Math.cos(Math.PI * t / anlauf)); }

    /** Der Zeitpunkt zur Modellzeit t (s). */
    public Instant zeit(double t) {
        long s = (long) Math.floor(t);
        return start.plusSeconds(s).plusNanos(Math.round((t - s) * 1e9));
    }

    /** Liegt die Modellzeit t in der Datei? */
    public boolean deckt(double t) {
        Instant z = zeit(t);
        return !z.isBefore(wetter.start) && !z.isAfter(wetter.ende()) && (luft == null || !z.isBefore(luft.start) && !z.isAfter(luft.ende()));
    }

    @Override public String name() { return "antrieb"; }

    @Override public String version() { return VERSION; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung).schreibt("air.u10", "m/s", Ort.ZELLE, gitter, Feld.Zahl.DOUBLE).schreibt("air.v10", "m/s", Ort.ZELLE, gitter, Feld.Zahl.DOUBLE)
                .schreibt("air.p", "Pa", Ort.ZELLE, gitter, Feld.Zahl.DOUBLE);
        if (schub != Schub.WU) {
            ix = wetter.groesse("iews");
            iy = wetter.groesse("inss");
            ia = wetter.groesse("charnock");
            if (schub == Schub.ERA5 && (ix < 0 || iy < 0) || schub == Schub.CHARNOCK && ia < 0)
                throw new IllegalArgumentException("Antrieb: Windschub " + schub + " braucht im Wetterfeld " + (schub == Schub.ERA5 ? "iews, inss" : "charnock") + ", es hat "
                        + wetter.groessen + " (Aufbereitung mit antrieb.schub, P5e)");
            tx = new double[wetter.raster.punkte()];
            ty = new double[wetter.raster.punkte()];
            a.schreibt("air.taux", "N/m²", Ort.ZELLE, gitter, Feld.Zahl.DOUBLE).schreibt("air.tauy", "N/m²", Ort.ZELLE, gitter, Feld.Zahl.DOUBLE);
        }
        if (luft != null)
            a.schreibt("air.t2", "K", Ort.ZELLE, luftGitter, Feld.Zahl.DOUBLE).schreibt("air.q2", "kg/kg", Ort.ZELLE, luftGitter, Feld.Zahl.DOUBLE)
                    .schreibt("air.sw", "W/m²", Ort.ZELLE, luftGitter, Feld.Zahl.DOUBLE).schreibt("air.lw", "W/m²", Ort.ZELLE, luftGitter, Feld.Zahl.DOUBLE)
                    .schreibt("water.sst", "K", Ort.ZELLE, luftGitter, Feld.Zahl.DOUBLE);
        if (luft != null && iRegen >= 0) a.schreibt("air.rain", "kg/(m² s)", Ort.ZELLE, luftGitter, Feld.Zahl.DOUBLE);
    }

    /** Zeitindex und Gewicht im Wetterfeld w zum Zeitpunkt z, um {@code versatz} Sekunden verschoben (Mittel der Stunde: +½ Schritt). */
    private static double[] stelle(Wetterfeld w, Instant z, double versatz) {
        double s = (z.getEpochSecond() - w.start.getEpochSecond() + z.getNano() * 1e-9 + versatz) / w.schritt;
        int k = Math.min(w.zeiten - 2, Math.max(0, (int) Math.floor(s)));
        return new double[] {k, w.zeiten == 1 ? 0 : Math.max(0, Math.min(1, s - k))};
    }

    private void legeLuft(Bus bus, double t) {
        Instant z = zeit(t);
        double[] jetzt = stelle(luft, z, 0), mitte = stelle(luft, z, 0.5 * luft.schritt), druck = stelle(wetter, z, 0);
        int n = luft.raster.punkte();
        String[] name = {"air.t2", "air.q2", "air.sw", "air.lw", "water.sst"};
        double[][] w = new double[5][];
        for (int q = 0; q < 5; q++) {
            double[] st = q == 2 || q == 3 ? mitte : jetzt;
            int k = (int) st[0], k1 = luft.zeiten == 1 ? k : k + 1;
            float[] a = luft.feld(k, il[q]), b = luft.feld(k1, il[q]);
            w[q] = new double[n];
            for (int i = 0; i < n; i++) w[q][i] = (1 - st[1]) * a[i] + st[1] * b[i];
        }
        // Luftdruck am Ort der Luft: aus dem Feld des Winds, bilinear (für die Feuchte reicht der Druck auf Meereshöhe)
        int kp = (int) druck[0], kp1 = wetter.zeiten == 1 ? kp : kp + 1;
        float[] pa = wetter.feld(kp, ip), pb = wetter.feld(kp1, ip);
        if (luftGewichte == null) {
            luftGewichte = new Gradraster.Gewichte[n];
            for (int j = 0; j < luft.raster.ny(); j++)
                for (int i = 0; i < luft.raster.nx(); i++)
                    luftGewichte[j * luft.raster.nx() + i] = wetter.raster.gewichte(luft.raster.laenge(i), luft.raster.breite(j), null, 0, 1, 0);
        }
        for (int i = 0; i < n; i++) {
            double p = (1 - druck[1]) * luftGewichte[i].wert(pa, 0, 1) + druck[1] * luftGewichte[i].wert(pb, 0, 1);
            w[1][i] = feuchte(w[1][i], p);
            w[2][i] = Math.max(0, w[2][i]);
        }
        for (int q = 0; q < 5; q++) {
            Feld f = bus.schreibfeld(this, name[q]);
            System.arraycopy(w[q], 0, f.d, 0, n);
            bus.lege(this, name[q], t);
        }
        if (iRegen >= 0) {
            // Regen: tp ist die Summe der Stunde vor der Zeit (m); als Rate in der Mitte dieser Stunde genommen, dazwischen linear
            int k = (int) mitte[0], k1 = luft.zeiten == 1 ? k : k + 1;
            float[] a = luft.feld(k, iRegen), b = luft.feld(k1, iRegen);
            Feld f = bus.schreibfeld(this, "air.rain");
            for (int i = 0; i < n; i++) f.d[i] = Math.max(0, (1 - mitte[1]) * a[i] + mitte[1] * b[i]) * 1000 / luft.schritt;
            bus.lege(this, "air.rain", t);
        }
    }

    private Gradraster.Gewichte[] luftGewichte;

    /**
     * Widerstandsbeiwert nach Charnock mit zähem Anteil für den Wind U (m/s) in 10 m und den Parameter α:
     * u* = κ U / ln(10 / z₀), z₀ = α u*² / g + 0,11 ν / u*, durch Einsetzen bis auf 1e-12 relativ (höchstens 50 Runden).
     */
    public static double cdCharnock(double u, double alpha) {
        if (!(u > 0)) return 0;
        double us = 0.04 * u;
        for (int k = 0; k < 50; k++) {
            double z0 = alpha * us * us / G + 0.11 * NU / us, neu = KARMAN * u / Math.log(10 / z0);
            if (Math.abs(neu - us) <= 1e-12 * neu) { us = neu; break; }
            us = neu;
        }
        return (us / u) * (us / u);
    }

    /** Seit 0.5: der wirksame Faktor auf den Wind. */
    public double windFaktor() { return windFaktor; }

    /** Seit 0.5: die wirksame Drehung des Winds (Grad, im Uhrzeigersinn). */
    public double windDrehung() { return windDrehung; }

    /** Seit 0.5: ein Wert, der dem Ziel mit höchstens {@code rate} je Sekunde über {@code dt} Sekunden folgt. */
    public static double rampe(double ist, double ziel, double rate, double dt) {
        double d = ziel - ist, m = Math.max(0, rate * dt);
        return Math.abs(d) <= m ? ziel : ist + Math.copySign(m, d);
    }

    /**
     * Seit 0.5: ein Vektor (nach Osten, nach Norden) mal Faktor, um {@code grad} im Uhrzeigersinn gedreht:
     * {u cos θ + v sin θ, −u sin θ + v cos θ} · Faktor. Ein Wind nach Norden, um 90° gedreht, weht nach Osten.
     */
    public static double[] drehe(double u, double v, double faktor, double grad) {
        double a = Math.toRadians(grad), c = Math.cos(a), s = Math.sin(a);
        return new double[] {faktor * (u * c + v * s), faktor * (-u * s + v * c)};
    }

    /** Die Regler zur Zeit t nachführen (beim ersten Mal ohne Weg: Sie beginnen bei 1 und 0). */
    private void regle(double t) {
        double dt = Double.isNaN(tRegler) ? 0 : Math.max(0, t - tRegler);
        tRegler = t;
        windFaktor = rampe(windFaktor, windFaktorZiel, rampeFaktor / 3600, dt);
        windDrehung = rampe(windDrehung, windDrehungZiel, rampeDrehung / 3600, dt);
    }

    @Override public void beginn(double t0, Bus bus) { lege(bus, t0); }

    @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }

    private void lege(Bus bus, double t) {
        if (!deckt(t)) throw new IllegalStateException("Antrieb: ERA5 reicht von " + wetter.start + " bis " + wetter.ende() + ", gebraucht wird " + zeit(t));
        Instant z = zeit(t);
        double s = (z.getEpochSecond() - wetter.start.getEpochSecond() + z.getNano() * 1e-9) / wetter.schritt;
        int k = Math.min(wetter.zeiten - 2, Math.max(0, (int) Math.floor(s)));
        double w = wetter.zeiten == 1 ? 0 : Math.max(0, Math.min(1, s - k)), r = anteil(t, anlauf);
        int k1 = wetter.zeiten == 1 ? k : k + 1;
        int[] gr = {iu, iv, ip};
        String[] name = {"air.u10", "air.v10", "air.p"};
        double[][] wind = new double[2][];
        for (int q = 0; q < 3; q++) {
            float[] a = wetter.feld(k, gr[q]), b = wetter.feld(k1, gr[q]);
            Feld f = bus.schreibfeld(this, name[q]);
            for (int i = 0; i < a.length; i++) {
                double v = (1 - w) * a[i] + w * b[i];
                f.d[i] = q < 2 ? r * v : p0 + r * (v - p0);
            }
            if (q < 2) wind[q] = f.d;
        }
        // seit 0.5 (Y2): der Regler – Faktor und Drehung auf den Wind (erst, wenn er etwas tut: sonst bitgleich)
        regle(t);
        double fa = windFaktor, dr = windDrehung;
        boolean geregelt = fa != 1 || dr != 0;
        if (geregelt)
            for (int i = 0; i < wind[0].length; i++) {
                double[] uv = drehe(wind[0][i], wind[1][i], fa, dr);
                wind[0][i] = uv[0];
                wind[1][i] = uv[1];
            }
        for (int q = 0; q < 3; q++) bus.lege(this, name[q], t);
        if (luft != null) legeLuft(bus, t);
        if (schub == Schub.WU) return;
        if (schub == Schub.ERA5) {
            float[] xa = wetter.feld(k, ix), xb = wetter.feld(k1, ix), ya = wetter.feld(k, iy), yb = wetter.feld(k1, iy);
            for (int i = 0; i < tx.length; i++) {
                tx[i] = r * ((1 - w) * xa[i] + w * xb[i]);
                ty[i] = r * ((1 - w) * ya[i] + w * yb[i]);
                if (geregelt) {
                    double[] t2 = drehe(tx[i], ty[i], fa * fa, dr);         // Schub ~ Wind²
                    tx[i] = t2[0];
                    ty[i] = t2[1];
                }
            }
        } else {
            float[] aa = wetter.feld(k, ia), ab = wetter.feld(k1, ia);
            for (int i = 0; i < tx.length; i++) {
                double ux = wind[0][i], uy = wind[1][i], u = Math.hypot(ux, uy), al = (1 - w) * aa[i] + w * ab[i];
                double c = rhoL * cdCharnock(u, al) * u;
                tx[i] = c * ux;
                ty[i] = c * uy;
            }
        }
        Feld fx = bus.schreibfeld(this, "air.taux"), fy = bus.schreibfeld(this, "air.tauy");
        System.arraycopy(tx, 0, fx.d, 0, tx.length);
        System.arraycopy(ty, 0, fy.d, 0, ty.length);
        bus.lege(this, "air.taux", t);
        bus.lege(this, "air.tauy", t);
    }
}
