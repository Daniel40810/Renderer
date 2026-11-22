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
 * Luft, Strahlung und Wassertemperatur aus ERA5 (Werkbuch HalligHooge Teil A, 12.11, P10b, F90 A): GRIB-Dateien der
 * Quelle {@code era5_cds_luft} zusammenstellen, je Stunde die Größen {@link #GROESSEN} auf einem gemeinsamen Raster
 * (0,25°). Das ist der Eingang von {@code physics.atmos}.
 * <ul>
 * <li><b>Summen:</b> ERA5 gibt Strahlung und Wärmeflüsse als Summe der Stunde vor der gültigen Zeit (J/m²);
 * {@link #mitteln} macht daraus das Mittel dieser Stunde in W/m². Es steht weiter bei der Zeit am Ende der Stunde; wer
 * es liest, nimmt es für die Mitte der Stunde ({@code physics.antrieb}). Der Niederschlag bleibt die Summe der Stunde
 * (m).</li>
 * <li><b>Vorzeichen:</b> {@code ssrd}, {@code strd} nach unten; {@code sshf}, {@code slhf} wie bei ECMWF nach unten
 * positiv (über warmem Wasser also negativ).</li>
 * <li><b>Wassertemperatur:</b> Über Land gibt ERA5 nichts (NaN); diese Punkte bekommen Ring um Ring das Mittel ihrer
 * Nachbarn über See [G], wie der Seegang ({@link Seegang#fuellen}).</li>
 * </ul>
 */
public final class Era5Luft {
    /**
     * Kurznamen: Temperatur und Taupunkt in 2 m (K), Temperatur der Meeresoberfläche (K), Sonnen- und Gegenstrahlung am
     * Boden (W/m²), fühlbare und latente Wärme (W/m², nach unten positiv), Bewölkung (0–1), Niederschlag (m je Stunde).
     */
    public static final List<String> GROESSEN = List.of("t2", "d2", "sst", "ssrd", "strd", "sshf", "slhf", "tcc", "tp");
    /** Summen über die Stunde (J/m²), die zu Mitteln (W/m²) werden. */
    public static final List<String> SUMMEN = List.of("ssrd", "strd", "sshf", "slhf");

    /** Die gelesenen Felder: Raster, je Zeit (s seit 1970) je Größe ein Feld; Zähler. */
    public static final class Daten {
        public Gradraster raster;
        public final TreeMap<Long, float[][]> felder = new TreeMap<>();
        public int nachrichten, ersetzt, andere;
        /** Punkte ohne Wassertemperatur (Land) zur ersten Zeit und gefüllte Werte. */
        public int land;
        public long gefuellt;

        public Instant start() { return Instant.ofEpochSecond(felder.firstKey()); }

        public Instant ende() { return Instant.ofEpochSecond(felder.lastKey()); }

        public int schritt() { return felder.size() < 2 ? 3600 : (int) (felder.higherKey(felder.firstKey()) - felder.firstKey()); }
    }

    private Era5Luft() { }

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
        if (d.felder.isEmpty()) throw new IOException("Luft: keine Felder " + GROESSEN + " in " + dateien);
        return d;
    }

    /** Lücken in der Zeit oder fehlende Größen als Text (leer: keine). */
    public static String luecken(Daten d) {
        StringBuilder b = new StringBuilder();
        long s = d.schritt(), t0 = d.felder.firstKey();
        int n = 0, gemeldet = 0;
        for (Map.Entry<Long, float[][]> e : d.felder.entrySet()) {
            if ((e.getKey() - t0) % s != 0 && gemeldet++ < 5) b.append(' ').append(Instant.ofEpochSecond(e.getKey())).append(" nicht im Schritt;");
            for (int k = 0; k < GROESSEN.size(); k++)
                if (e.getValue()[k] == null && gemeldet++ < 5) b.append(' ').append(GROESSEN.get(k)).append(" fehlt zu ").append(Instant.ofEpochSecond(e.getKey())).append(';');
            n++;
        }
        long soll = (d.felder.lastKey() - t0) / s + 1;
        if (soll != n) b.append(' ').append(soll - n).append(" Zeiten fehlen;");
        if (gemeldet > 5) b.append(" und ").append(gemeldet - 5).append(" weitere;");
        return b.toString().strip();
    }

    /** Macht aus den Summen der Stunde (J/m²) Mittel (W/m²). Einmal aufrufen. */
    public static void mitteln(Daten d) {
        double s = d.schritt();
        for (float[][] z : d.felder.values())
            for (String name : SUMMEN) {
                float[] a = z[GROESSEN.indexOf(name)];
                for (int i = 0; i < a.length; i++) a[i] = (float) (a[i] / s);
            }
    }

    /** Füllt die Wassertemperatur über Land Ring um Ring aus den acht Nachbarn mit Werten; gibt die Zahl der gefüllten Werte zurück. */
    public static long fuellen(Daten d) {
        int nx = d.raster.nx(), ny = d.raster.ny(), k = GROESSEN.indexOf("sst");
        for (float v : d.felder.firstEntry().getValue()[k]) if (Float.isNaN(v)) d.land++;
        long n = 0;
        for (float[][] z : d.felder.values()) {
            float[] a = z[k];
            boolean weiter = true;
            while (weiter) {
                weiter = false;
                float[] neu = a.clone();
                for (int j = 0; j < ny; j++)
                    for (int i = 0; i < nx; i++) {
                        int p = j * nx + i;
                        if (!Float.isNaN(a[p])) continue;
                        double s = 0;
                        int m = 0;
                        for (int dj = -1; dj <= 1; dj++)
                            for (int di = -1; di <= 1; di++) {
                                int ii = i + di, jj = j + dj;
                                if ((di | dj) == 0 || ii < 0 || jj < 0 || ii >= nx || jj >= ny) continue;
                                float v = a[jj * nx + ii];
                                if (Float.isNaN(v)) continue;
                                s += v;
                                m++;
                            }
                        if (m > 0) {
                            neu[p] = (float) (s / m);
                            n++;
                            weiter = true;
                        }
                    }
                System.arraycopy(neu, 0, a, 0, a.length);
            }
            for (float v : a) if (Float.isNaN(v)) throw new IllegalStateException("Luft: Wassertemperatur ganz ohne Werte");
        }
        // andere Größen dürfen keine Lücken haben
        for (Map.Entry<Long, float[][]> e : d.felder.entrySet())
            for (int q = 0; q < GROESSEN.size(); q++)
                for (float v : e.getValue()[q])
                    if (Float.isNaN(v)) throw new IllegalStateException("Luft: " + GROESSEN.get(q) + " hat Lücken zu " + Instant.ofEpochSecond(e.getKey()));
        return d.gefuellt = n;
    }

    /** Kleinster und größter Wert einer Größe über alle Zeiten und Punkte. */
    public static double[] spanne(Daten d, String name) {
        int k = GROESSEN.indexOf(name);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (float[][] z : d.felder.values())
            for (float v : z[k]) {
                if (Float.isNaN(v)) continue;
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
        return new double[] {lo, hi};
    }

    /** Für die Ausgabe: die Namen der Größen mit Leerzeichen. */
    public static String namen() { return String.join(" ", GROESSEN); }

    static { if (!GROESSEN.containsAll(SUMMEN)) throw new IllegalStateException(Arrays.toString(SUMMEN.toArray())); }
}
