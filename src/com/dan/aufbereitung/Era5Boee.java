package com.dan.aufbereitung;

import com.dan.core.gitter.Gradraster;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Böe in 10 m aus ERA5 (Seegang Werkbuch, Datei 104, S8b, SF156 A): GRIB-Dateien der Quelle {@code era5_cds_boee}
 * zusammenstellen, je Stunde {@code fg10} (m/s) auf dem Raster der Deutschen Bucht (0,25°). ERA5 gibt die stärkste Böe
 * (3-s-Mittel nach WMO) der Stunde vor der gültigen Zeit; sie steht bei der Zeit am Ende der Stunde. Mit dem mittleren
 * Wind {@code u10}, {@code v10} aus {@code era5} ergibt sie den Böenfaktor G = fg10 / U10, der die Stärke der
 * Katzenpfoten setzt ({@code render.water.Katzenpfoten}).
 */
public final class Era5Boee {
    /** Kurzname: Böe in 10 m (m/s). */
    public static final List<String> GROESSEN = List.of("fg10");

    /** Die gelesenen Felder: Raster, je Zeit (s seit 1970) ein Feld; Zähler. */
    public static final class Daten {
        public Gradraster raster;
        public final TreeMap<Long, float[]> felder = new TreeMap<>();
        public int nachrichten, ersetzt, andere;

        public Instant start() { return Instant.ofEpochSecond(felder.firstKey()); }

        public Instant ende() { return Instant.ofEpochSecond(felder.lastKey()); }

        public int schritt() { return felder.size() < 2 ? 3600 : (int) (felder.higherKey(felder.firstKey()) - felder.firstKey()); }
    }

    private Era5Boee() { }

    /** Liest alle Dateien in der Folge ihrer Namen; eine spätere ersetzt ein Feld einer früheren. */
    public static Daten lies(List<Path> dateien) throws IOException {
        dateien = new ArrayList<>(dateien);
        dateien.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        Daten d = new Daten();
        IOException[] fehler = new IOException[1];
        for (Path p : dateien) {
            Grib.lies(p, f -> {
                if (fehler[0] != null) return;
                d.nachrichten++;
                if (!"fg10".equals(f.name())) { d.andere++; return; }
                if (d.raster == null) d.raster = f.raster();
                else if (!Era5.gleich(d.raster, f.raster())) {
                    fehler[0] = new IOException(p.getFileName() + ": Raster " + f.raster() + " statt " + d.raster);
                    return;
                }
                if (d.felder.put(f.zeit().getEpochSecond(), f.werte()) != null) d.ersetzt++;
            });
            if (fehler[0] != null) throw fehler[0];
        }
        if (d.felder.isEmpty()) throw new IOException("Böe: keine Felder fg10 in " + dateien);
        return d;
    }

    /** Lücken in der Zeit oder Werte ohne Zahl als Text (leer: keine). */
    public static String luecken(Daten d) {
        StringBuilder b = new StringBuilder();
        long s = d.schritt(), t0 = d.felder.firstKey();
        int n = 0, gemeldet = 0;
        for (Map.Entry<Long, float[]> e : d.felder.entrySet()) {
            if ((e.getKey() - t0) % s != 0 && gemeldet++ < 5) b.append(' ').append(Instant.ofEpochSecond(e.getKey())).append(" nicht im Schritt;");
            for (float v : e.getValue())
                if (!(v >= 0 && v < 100)) {
                    if (gemeldet++ < 5) b.append(" Wert ").append(v).append(" zu ").append(Instant.ofEpochSecond(e.getKey())).append(';');
                    break;
                }
            n++;
        }
        long soll = (d.felder.lastKey() - t0) / s + 1;
        if (soll != n) b.append(' ').append(soll - n).append(" Zeiten fehlen;");
        if (gemeldet > 5) b.append(" und ").append(gemeldet - 5).append(" weitere;");
        return b.toString().strip();
    }

    /** Kleinste, größte und mittlere Böe über alle Zeiten und Punkte. */
    public static double[] spanne(Daten d) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, sum = 0;
        long n = 0;
        for (float[] a : d.felder.values())
            for (float v : a) { lo = Math.min(lo, v); hi = Math.max(hi, v); sum += v; n++; }
        return new double[] {lo, hi, sum / Math.max(1, n)};
    }
}
