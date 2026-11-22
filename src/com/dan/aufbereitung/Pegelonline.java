package com.dan.aufbereitung;

import com.dan.core.astro.Partialtide;
import com.dan.core.pruef.Harmonik;
import com.dan.core.pruef.Pegel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Pegel aus PEGELONLINE (WSV, DL-DE Zero 2.0) als Maßstab aufbereiten (Werkbuch HalligHooge Teil A, 12.6, Befund 3,
 * P5b). Je Pegel ein Ordner mit {@code station.json} (Stammdaten mit Pegelnull und Kennwerten) und einer oder mehreren
 * Dateien {@code W_bis_<Datum>_30_tage.json} (Wasserstand in cm über Pegelnull, je Minute; das Kartenwerkzeug legt bei
 * jedem Lauf eine neue an, die Reihe wächst). Daraus:
 * <ol>
 * <li>die Reihe auf festem Schritt (60 s), alle Dateien zusammen; fehlende Minuten NaN;</li>
 * <li>in m NHN: Wert / 100 + Pegelnull;</li>
 * <li>Ausreißer verworfen (NaN): außerhalb der Grenzen [G], oder mehr als {@code spitze} [G] neben dem Median der
 * gültigen Werte im Fenster ±{@code fenster} Minuten. Für die Tide ist der Median eines glatten Verlaufs der Wert
 * selbst (auf mm), eine Spitze von wenigen Minuten fällt heraus;</li>
 * <li>die harmonische Analyse ({@link Harmonik}, Rayleigh 1) über die ganze Reihe, wie im Befund;</li>
 * <li>die Kennwerte der Stammdaten in m NHN.</li>
 * </ol>
 */
public final class Pegelonline {
    /** Grenzen (m NHN) [G], Spitze (m) [G], Fenster (Minuten, halbe Breite) [G]. */
    public double unten = -6, oben = 8, spitze = 0.3;
    public int fenster = 15;
    /** Kandidaten der Analyse [G]: halb- und eintägige, Flachwasser; langperiodische nicht (in 30 Tagen Windstau). */
    public List<Partialtide> tiden = List.of(Partialtide.M2, Partialtide.S2, Partialtide.N2, Partialtide.K2, Partialtide.ZWEI_N2, Partialtide.MU2, Partialtide.NU2,
            Partialtide.K1, Partialtide.O1, Partialtide.P1, Partialtide.Q1, Partialtide.M4, Partialtide.MS4, Partialtide.MN4, Partialtide.M6);

    /** Ein gelesener Pegel und was beim Lesen auffiel. */
    public record Gelesen(Pegel pegel, int dateien, int doppelt, int spitzen, int grenzen, String hoechster) { }

    /** Liest einen Pegel aus seinem Ordner. */
    public Gelesen lies(Path ordner) throws IOException {
        Map<String, Object> st = Json.objekt(Json.lies(ordner.resolve("station.json")));
        String name = ordner.getFileName().toString();
        Map<String, Object> w = null;
        for (Object t : Json.feld(st.get("timeseries"))) if ("W".equals(Json.objekt(t).get("shortname"))) w = Json.objekt(t);
        if (w == null) throw new IOException(ordner + ": keine Reihe W in station.json");
        if (!"cm".equals(w.get("unit"))) throw new IOException(ordner + ": W in " + w.get("unit") + " statt cm");
        Map<String, Object> gz = Json.objekt(w.get("gaugeZero"));
        String einheit = String.valueOf(gz.get("unit"));
        if (!einheit.contains("NHN")) throw new IOException(ordner + ": Pegelnull in " + einheit + " statt m NHN");
        double pnp = Json.zahl(gz.get("value"));
        // Reihe: alle Dateien, je Minute
        TreeMap<Long, Double> roh = new TreeMap<>();
        int dateien = 0, doppelt = 0;
        List<Path> ws;
        try (Stream<Path> s = Files.list(ordner)) {
            ws = s.filter(p -> p.getFileName().toString().matches("W_.*\\.json")).sorted().toList();
        }
        for (Path p : ws) {
            dateien++;
            for (Object o : Json.feld(Json.lies(p))) {
                Map<String, Object> m = Json.objekt(o);
                long t = OffsetDateTime.parse(String.valueOf(m.get("timestamp"))).toInstant().getEpochSecond();
                double v = Json.zahl(m.get("value"));
                if (roh.put(t, v) != null) doppelt++;
            }
        }
        if (roh.isEmpty()) throw new IOException(ordner + ": keine Werte");
        long t0 = roh.firstKey(), t1 = roh.lastKey();
        int schritt = 60;
        if (t0 % schritt != 0) throw new IOException(ordner + ": Zeiten nicht auf volle Minuten");
        int n = (int) ((t1 - t0) / schritt) + 1;
        float[] x = new float[n];
        Arrays.fill(x, Float.NaN);
        int luecken = n, aussen = 0;
        for (Map.Entry<Long, Double> e : roh.entrySet()) {
            long d = e.getKey() - t0;
            if (d % schritt != 0) throw new IOException(ordner + ": Zeit " + Instant.ofEpochSecond(e.getKey()) + " nicht auf dem Schritt");
            if (Double.isNaN(e.getValue())) continue;
            x[(int) (d / schritt)] = (float) (e.getValue() / 100 + pnp);
            luecken--;
        }
        // Ausreißer
        for (int k = 0; k < n; k++)
            if (!Float.isNaN(x[k]) && (x[k] < unten || x[k] > oben)) {
                x[k] = Float.NaN;
                aussen++;
            }
        float[] y = x.clone();
        int spitzen = 0;
        double[] f = new double[2 * fenster + 1];
        StringBuilder bericht = new StringBuilder();
        for (int k = 0; k < n; k++) {
            if (Float.isNaN(x[k])) continue;
            int m = 0;
            for (int q = Math.max(0, k - fenster); q <= Math.min(n - 1, k + fenster); q++) if (!Float.isNaN(x[q])) f[m++] = x[q];
            Arrays.sort(f, 0, m);
            double med = m % 2 == 1 ? f[m / 2] : 0.5 * (f[m / 2 - 1] + f[m / 2]);
            if (Math.abs(x[k] - med) > spitze) {
                y[k] = Float.NaN;
                spitzen++;
                if (spitzen <= 3) bericht.append(Aufbereitung.f(" %s %.2f m (Median %.2f)", Instant.ofEpochSecond(t0 + (long) k * schritt), x[k], med));
            }
        }
        // Kennwerte
        Map<String, Pegel.Kennwert> kw = new LinkedHashMap<>();
        for (Object o : Json.feld(w.get("characteristicValues"))) {
            Map<String, Object> c = Json.objekt(o);
            String k = String.valueOf(c.get("shortname"));
            double v = Json.zahl(c.get("value"));
            if (!"cm".equals(c.get("unit")) || Double.isNaN(v)) continue;
            List<Object> wann = Json.feld(c.get("occurrences"));
            kw.put(k, new Pegel.Kennwert(k, v / 100 + pnp, String.valueOf(c.get("timespanStart")), String.valueOf(c.get("timespanEnd")), wann.isEmpty() ? null : String.valueOf(wann.get(0))));
        }
        Harmonik.Ergebnis h = Harmonik.analyse(Instant.ofEpochSecond(t0), schritt, toDouble(y), tiden, 1);
        int hk = -1;
        for (int k = 0; k < n; k++) if (!Float.isNaN(y[k]) && (hk < 0 || y[k] > y[hk])) hk = k;
        String hoechster = hk < 0 ? "–" : Aufbereitung.f("%.2f m NHN am %s", y[hk], Instant.ofEpochSecond(t0 + (long) hk * schritt));
        Pegel p = new Pegel(name, String.valueOf(st.get("number")), String.valueOf(Json.objekt(st.get("water")).get("shortname")), Json.zahl(st.get("longitude")),
                Json.zahl(st.get("latitude")), pnp, Instant.ofEpochSecond(t0), schritt, y, spitzen + aussen, luecken, kw, h);
        return new Gelesen(p, dateien, doppelt, spitzen, aussen, hoechster + (bericht.length() > 0 ? "; Spitzen:" + bericht : ""));
    }

    static double[] toDouble(float[] f) {
        double[] d = new double[f.length];
        for (int k = 0; k < f.length; k++) d[k] = f[k];
        return d;
    }

    /** Alle Ordner mit {@code station.json} unter {@code wurzel}, nach Namen. */
    public static List<Path> ordner(Path wurzel) throws IOException {
        List<Path> l = new ArrayList<>();
        try (Stream<Path> s = Files.list(wurzel)) {
            for (Path p : s.sorted().toList()) if (Files.isRegularFile(p.resolve("station.json"))) l.add(p);
        }
        return l;
    }
}
