package com.dan.aufbereitung;

import com.dan.core.pruef.Windstation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Wind an Stationen des DWD aus dem Climate Data Center (Werkbuch HalligHooge Teil A, 12.6, P5e, F36 A): ZIP
 * {@code 10minutenwerte_wind_<Kennung>_akt.zip} mit {@code produkt_zehn_min_ff_*.txt} (Spalten {@code STATIONS_ID;
 * MESS_DATUM;QN;FF_10;DD_10;eor}, Zeit {@code JJJJMMTTHHmm} in UTC, −999 fehlt); die ZIP aus {@code recent} tragen nur
 * diese Datei (gemessen am 14.11.2026). Die Metadaten kommen aus {@code Meta_Daten_zehn_min_ff_<Kennung>.zip}
 * ({@code Metadaten_Geographie_*}: Lage; {@code Metadaten_Geraete_Windgeschwindigkeit_*}: Höhe des Gebers über Grund, die
 * Zeile, die bei der letzten Messung galt) und, für die Lage, aus der Stationsliste
 * {@code zehn_min_ff_Beschreibung_Stationen.txt}. Die Spalten werden über ihre Namen gesucht, nicht über ihre Stelle.
 */
public final class Dwd {
    private Dwd() { }

    static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** Kopf und Zeilen einer Textdatei mit Semikolon; Felder ohne Leerraum. */
    static List<String[]> tabelle(InputStream in) throws IOException {
        List<String[]> z = new ArrayList<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
        for (String s; (s = r.readLine()) != null; ) {
            if (s.isBlank() || s.startsWith("generiert") || s.startsWith("Legende")) continue;
            String[] f = s.split(";", -1);
            for (int i = 0; i < f.length; i++) f[i] = f[i].strip();
            if (f.length > 1) z.add(f);
        }
        return z;
    }

    /** Stelle der ersten Spalte, deren Name (ohne Groß- und Kleinschreibung) einen der Teile enthält; −1 sonst. */
    static int spalte(String[] kopf, String... teile) {
        for (int i = 0; i < kopf.length; i++)
            for (String t : teile)
                if (kopf[i].toLowerCase(Locale.ROOT).replace("ö", "oe").contains(t)) return i;
        return -1;
    }

    /**
     * Liest eine Station aus einem oder mehreren ZIP. {@code geber}: Höhe des Gebers über Grund aus dem Rezept (NaN: aus
     * den Metadaten des ZIP; fehlen beide, ein Fehler).
     */
    public static Windstation lies(String name, String kennung, List<Path> zips, Path stationsliste, double geber) throws IOException {
        TreeMap<Long, float[]> werte = new TreeMap<>();
        double laenge = Double.NaN, breite = Double.NaN, hoehe = Double.NaN;
        List<double[]> geraete = new ArrayList<>();                            // {von, bis, Höhe des Gebers}, Datum als JJJJMMTT
        List<String> quellen = new ArrayList<>();
        for (Path p : zips) {
            quellen.add(p.getFileName().toString());
            try (ZipFile z = new ZipFile(p.toFile())) {
                for (ZipEntry e : java.util.Collections.list(z.entries())) {
                    String n = e.getName().toLowerCase(Locale.ROOT);
                    if (n.startsWith("produkt_zehn_min_ff")) {
                        List<String[]> t = tabelle(z.getInputStream(e));
                        String[] k = t.get(0);
                        int iz = spalte(k, "mess_datum"), iff = spalte(k, "ff_10"), idd = spalte(k, "dd_10");
                        if (iz < 0 || iff < 0) throw new IOException("DWD " + p.getFileName() + ": Spalten MESS_DATUM, FF_10 fehlen im Kopf " + Arrays.toString(k));
                        for (String[] f : t.subList(1, t.size())) {
                            long s = LocalDateTime.parse(f[iz], ZEIT).toEpochSecond(ZoneOffset.UTC);
                            float v = wert(f[iff]), d = idd >= 0 ? wert(f[idd]) : Float.NaN;
                            werte.put(s, new float[] {v, d});
                        }
                    } else if (n.startsWith("metadaten_geographie")) {
                        List<String[]> t = tabelle(z.getInputStream(e));
                        String[] k = t.get(0), l = t.get(t.size() - 1);           // die jüngste Lage
                        int ib = spalte(k, "breite"), il = spalte(k, "laenge", "länge"), ih = spalte(k, "stationshoehe", "stationshöhe");
                        if (ib >= 0 && il >= 0) { breite = Double.parseDouble(l[ib]); laenge = Double.parseDouble(l[il]); }
                        if (ih >= 0 && !l[ih].isEmpty()) hoehe = Double.parseDouble(l[ih]);
                    } else if (n.startsWith("metadaten_geraete") && n.contains("geschwindigkeit")) {
                        List<String[]> t = tabelle(z.getInputStream(e));
                        int ig = spalte(t.get(0), "geberhoehe", "geberhöhe"), iv = spalte(t.get(0), "von_datum", "von datum"), ibis = spalte(t.get(0), "bis_datum", "bis datum");
                        if (ig >= 0) for (String[] l : t.subList(1, t.size()))
                            if (l.length > ig && !l[ig].isEmpty()) try {
                                double v = iv >= 0 && l.length > iv && !l[iv].isEmpty() ? Double.parseDouble(l[iv]) : 0;
                                double b = ibis >= 0 && l.length > ibis && !l[ibis].isEmpty() ? Double.parseDouble(l[ibis]) : 99999999;
                                geraete.add(new double[] {v, b, Double.parseDouble(l[ig].replace(',', '.'))});
                            } catch (NumberFormatException x) { /* Fußzeile */ }
                    }
                }
            }
        }
        if (werte.isEmpty()) throw new IOException("DWD " + name + ": keine Werte in " + quellen);
        if (Double.isNaN(laenge) && stationsliste != null && java.nio.file.Files.isRegularFile(stationsliste)) {
            // Stationsliste: Stations_id von_datum bis_datum Stationshoehe geoBreite geoLaenge Stationsname …
            for (String z : java.nio.file.Files.readAllLines(stationsliste, StandardCharsets.ISO_8859_1)) {
                String[] f = z.trim().split("\\s+");
                if (f.length >= 6 && f[0].equals(kennung)) {
                    hoehe = Double.parseDouble(f[3]);
                    breite = Double.parseDouble(f[4]);
                    laenge = Double.parseDouble(f[5]);
                    quellen.add(stationsliste.getFileName().toString());
                }
            }
        }
        if (Double.isNaN(laenge)) throw new IOException("DWD " + name + ": Lage weder in Metadaten_Geographie noch in der Stationsliste (" + quellen + ")");
        // Höhe des Gebers: die Zeile, die bei der letzten Messung galt
        double letzte = Double.parseDouble(LocalDateTime.ofEpochSecond(werte.lastKey(), 0, ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        double geberMeta = Double.NaN, von = -1;
        for (double[] r : geraete) if (r[0] <= letzte && r[0] >= von) { von = r[0]; geberMeta = r[2]; }
        double g = Double.isNaN(geber) ? geberMeta : geber;
        if (Double.isNaN(g)) throw new IOException("DWD " + name + ": Höhe des Gebers weder in den Metadaten noch im Rezept (wind.geber." + name + ")");
        long[] zt = new long[werte.size()];
        float[] ff = new float[zt.length], dd = new float[zt.length];
        int i = 0;
        for (Map.Entry<Long, float[]> e : werte.entrySet()) { zt[i] = e.getKey(); ff[i] = e.getValue()[0]; dd[i] = e.getValue()[1]; i++; }
        return new Windstation(name, kennung, laenge, breite, hoehe, g, String.join("+", quellen), zt, ff, dd);
    }

    static float wert(String s) {
        if (s.isEmpty()) return Float.NaN;
        float v = Float.parseFloat(s);
        return v <= -999 ? Float.NaN : v;
    }

}
