package com.dan.aufbereitung;

import com.dan.core.gitter.Gradraster;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Seegang aus ERA5 (Werkbuch HalligHooge Teil A, 12.9, P8b, F74 A): GRIB-Dateien der Quelle {@code era5_cds_seegang}
 * zusammenstellen, je Stunde die Größen {@link #GROESSEN} auf dem Raster des Seegangsmodells (0,5°). Über Land gibt
 * ERA5 nichts (NaN); diese Punkte bekommen Ring um Ring das Mittel ihrer Nachbarn über See [G], Richtungen als Mittel
 * der Einheitsvektoren. So kann der Rand eines Gitters an der Küste bilinear lesen.
 */
public final class Seegang {
    /**
     * Die Größen, Kurznamen nach ECMWF: Wellenhöhe gesamt, Richtung (woher, °), Spitzenperiode, mittlere Periode
     * (T_m−1,0); Windsee Höhe, Richtung, Periode; Dünung Höhe, Richtung, Periode; Richtungsbreite der Windsee und der
     * Dünung (0 … √2).
     */
    public static final List<String> GROESSEN = List.of("swh", "mwd", "pp1d", "mwp", "shww", "mdww", "mpww", "shts", "mdts", "mpts", "dwww", "dwps");
    /** Richtungen unter den Größen (werden als Vektoren gemittelt). */
    public static final List<String> RICHTUNGEN = List.of("mwd", "mdww", "mdts");

    /** Die gelesenen Felder: Raster, je Zeit (s seit 1970) je Größe ein Feld; Zähler. */
    public static final class Daten {
        public Gradraster raster;
        public final TreeMap<Long, float[][]> felder = new TreeMap<>();
        public int nachrichten, ersetzt, andere;
        /** Punkte über Land (NaN in der ersten Größe zur ersten Zeit) und gefüllte Werte. */
        public int land;
        public long gefuellt;

        public Instant start() { return Instant.ofEpochSecond(felder.firstKey()); }

        public Instant ende() { return Instant.ofEpochSecond(felder.lastKey()); }

        public int schritt() { return felder.size() < 2 ? 3600 : (int) (felder.higherKey(felder.firstKey()) - felder.firstKey()); }
    }

    private Seegang() { }

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
                int k = GROESSEN.indexOf(f.name());
                if (k < 0) { d.andere++; return; }
                if (d.raster == null) d.raster = f.raster();
                else if (!Era5.gleich(d.raster, f.raster())) {
                    fehler[0] = new IOException(p.getFileName() + ": Raster " + f.raster() + " statt " + d.raster);
                    return;
                }
                float[][] z = d.felder.computeIfAbsent(f.zeit().getEpochSecond(), x -> new float[GROESSEN.size()][]);
                if (z[k] != null) d.ersetzt++;
                z[k] = f.werte();
            });
            if (fehler[0] != null) throw fehler[0];
        }
        if (d.felder.isEmpty()) throw new IOException("Seegang: keine Felder " + GROESSEN + " in " + dateien);
        return d;
    }

    /** Lücken in der Zeit oder fehlende Größen als Text (leer: keine). */
    public static String luecken(Daten d) {
        StringBuilder b = new StringBuilder();
        long s = d.schritt(), t0 = d.felder.firstKey();
        int n = 0;
        for (Map.Entry<Long, float[][]> e : d.felder.entrySet()) {
            if ((e.getKey() - t0) % s != 0) b.append(' ').append(Instant.ofEpochSecond(e.getKey())).append(" nicht im Schritt;");
            for (int k = 0; k < GROESSEN.size(); k++) if (e.getValue()[k] == null) b.append(' ').append(GROESSEN.get(k)).append(" fehlt zu ").append(Instant.ofEpochSecond(e.getKey())).append(';');
            n++;
        }
        long soll = (d.felder.lastKey() - t0) / s + 1;
        if (soll != n) b.append(' ').append(soll - n).append(" Zeiten fehlen;");
        return b.toString().strip();
    }

    /**
     * Füllt NaN (Land, und an der Küste Punkte, an denen eine Teilgröße fehlt) Ring um Ring aus den acht Nachbarn mit
     * Werten; Richtungen über die Einheitsvektoren. Gibt die Zahl der gefüllten Werte zurück.
     */
    public static long fuellen(Daten d) {
        int nx = d.raster.nx(), ny = d.raster.ny();
        float[] erste = d.felder.firstEntry().getValue()[0];
        for (float v : erste) if (Float.isNaN(v)) d.land++;
        long n = 0;
        for (float[][] z : d.felder.values())
            for (int k = 0; k < GROESSEN.size(); k++) {
                float[] a = z[k];
                boolean richtung = RICHTUNGEN.contains(GROESSEN.get(k));
                boolean weiter = true;
                while (weiter) {
                    weiter = false;
                    float[] neu = a.clone();
                    for (int j = 0; j < ny; j++)
                        for (int i = 0; i < nx; i++) {
                            int p = j * nx + i;
                            if (!Float.isNaN(a[p])) continue;
                            double s = 0, sx = 0, sy = 0;
                            int m = 0;
                            for (int dj = -1; dj <= 1; dj++)
                                for (int di = -1; di <= 1; di++) {
                                    int ii = i + di, jj = j + dj;
                                    if ((di | dj) == 0 || ii < 0 || jj < 0 || ii >= nx || jj >= ny) continue;
                                    float v = a[jj * nx + ii];
                                    if (Float.isNaN(v)) continue;
                                    if (richtung) { sx += Math.sin(Math.toRadians(v)); sy += Math.cos(Math.toRadians(v)); }
                                    else s += v;
                                    m++;
                                }
                            if (m > 0) {
                                neu[p] = richtung ? (float) ((Math.toDegrees(Math.atan2(sx, sy)) + 360) % 360) : (float) (s / m);
                                n++;
                                weiter = true;
                            }
                        }
                    System.arraycopy(neu, 0, a, 0, a.length);
                    if (!weiter) for (float v : a) if (Float.isNaN(v)) { Arrays.fill(a, 0f); break; }   // ganz ohne See: 0
                }
            }
        return d.gefuellt = n;
    }
}
