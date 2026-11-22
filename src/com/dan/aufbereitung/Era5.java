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
 * ERA5 aus GRIB-Dateien des Climate Data Store zusammenstellen (Werkbuch HalligHooge Teil A, 12.6, P5b, F28): je
 * Stunde die Felder {@code u10}, {@code v10}, {@code msl} auf einem gemeinsamen {@link Gradraster}; seit P5e (F34) dazu,
 * wenn eine der Dateien sie hat, {@link #SCHUB}: {@code iews}, {@code inss} (Schubspannung, N/m²) und {@code charnock}. Die Dateien dürfen
 * sich in der Zeit überlappen: Gelesen wird nach dem Namen, eine spätere Datei ersetzt ein Feld einer früheren (der
 * laufende Monat kommt als {@code JJJJMM_bis_TT.grib} und wächst; ERA5T wird nach zwei bis drei Monaten durch ERA5
 * ersetzt). Fehlt eine Stunde oder eine Größe, ist das ein Fehler.
 */
public final class Era5 {
    public static final List<String> GROESSEN = List.of("u10", "v10", "msl");
    /** Die Größen des Windschubs (P5e, F34): alle drei oder keine. */
    public static final List<String> SCHUB = List.of("iews", "inss", "charnock");
    /** Alle Größen, die gelesen werden, in der Folge der Felder je Zeit. */
    public static final List<String> ALLE = List.of("u10", "v10", "msl", "iews", "inss", "charnock");

    /** Die gelesenen Felder: Raster, je Zeit (Sekunden seit 1970) je Größe ein Feld; dazu Zähler. */
    public static final class Daten {
        public Gradraster raster;
        public final TreeMap<Long, float[][]> felder = new TreeMap<>();
        /** Nachrichten; Felder, die eine spätere Datei ersetzt hat, davon mit anderen Werten; andere Größen. */
        public int nachrichten, ersetzt, geaendert, andere;
        public final List<Integer> editionen = new ArrayList<>();
        /** Mit den Größen des Windschubs ({@link #SCHUB})? */
        public boolean schub;

        /** Die Größen der Felder: {@link #GROESSEN}, mit Schub {@link #ALLE}. */
        public List<String> groessen() { return schub ? ALLE : GROESSEN; }

        public Instant start() { return Instant.ofEpochSecond(felder.firstKey()); }

        public Instant ende() { return Instant.ofEpochSecond(felder.lastKey()); }

        /** Schritt (s) aus den beiden ersten Zeiten; 3600 bei einer. */
        public int schritt() { return felder.size() < 2 ? 3600 : (int) (felder.higherKey(felder.firstKey()) - felder.firstKey()); }
    }

    private Era5() { }

    /** Liest alle Dateien in der Folge ihrer Namen. */
    public static Daten lies(List<Path> dateien) throws IOException {
        dateien = new ArrayList<>(dateien);
        dateien.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        Daten d = new Daten();
        IOException[] fehler = new IOException[1];
        for (Path p : dateien) {
            Grib.lies(p, f -> {
                if (fehler[0] != null) return;
                d.nachrichten++;
                if (!d.editionen.contains(f.edition())) d.editionen.add(f.edition());
                int k = ALLE.indexOf(f.name());
                if (k < 0) {
                    d.andere++;
                    return;
                }
                if (d.raster == null) d.raster = f.raster();
                else if (!gleich(d.raster, f.raster())) {
                    fehler[0] = new IOException(p.getFileName() + ": Raster " + f.raster() + " statt " + d.raster);
                    return;
                }
                if (k >= GROESSEN.size()) d.schub = true;
                float[][] z = d.felder.computeIfAbsent(f.zeit().getEpochSecond(), x -> new float[ALLE.size()][]);
                if (z[k] != null) {
                    d.ersetzt++;
                    if (!java.util.Arrays.equals(z[k], f.werte())) d.geaendert++;
                }
                z[k] = f.werte();
            });
            if (fehler[0] != null) throw fehler[0];
        }
        if (d.felder.isEmpty()) throw new IOException("ERA5: keine Felder u10, v10, msl in " + dateien);
        return d;
    }

    /**
     * Füllt {@code charnock} über Land (NaN in ERA5, das Feld gibt es nur über See) aus den Nachbarn über See, Ring um
     * Ring: je Runde bekommt jeder NaN-Punkt mit gültigen Nachbarn (acht) deren Mittel [G]. Gibt die Zahl der gefüllten
     * Werte zurück. Nur dort wirkt es, wo ein Gitter von hydro Wasser über einem Landpunkt von ERA5 hat (Küste).
     */
    public static long charnockFuellen(Daten d) {
        if (!d.schub) return 0;
        int ia = ALLE.indexOf("charnock"), nx = d.raster.nx(), ny = d.raster.ny();
        long n = 0;
        for (float[][] z : d.felder.values()) {
            float[] a = z[ia];
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
                                if ((di | dj) == 0 || ii < 0 || jj < 0 || ii >= nx || jj >= ny || Float.isNaN(a[jj * nx + ii])) continue;
                                s += a[jj * nx + ii];
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
        }
        return n;
    }

    static boolean gleich(Gradraster a, Gradraster b) {
        return a.nx() == b.nx() && a.ny() == b.ny() && Math.abs(a.laenge0() - b.laenge0()) < 1e-6 && Math.abs(a.breite0() - b.breite0()) < 1e-6
                && Math.abs(a.dLaenge() - b.dLaenge()) < 1e-9 && Math.abs(a.dBreite() - b.dBreite()) < 1e-9;
    }

    /** Prüft feste Schritte und alle Größen je Zeit; gibt die Lücken als Text (leer: keine). */
    public static String luecken(Daten d) {
        StringBuilder b = new StringBuilder();
        long s = d.schritt(), t0 = d.felder.firstKey();
        int n = 0;
        for (Map.Entry<Long, float[][]> e : d.felder.entrySet()) {
            if ((e.getKey() - t0) % s != 0) b.append(" ").append(Instant.ofEpochSecond(e.getKey())).append(" nicht im Schritt;");
            List<String> gr = d.groessen();
            for (int k = 0; k < gr.size(); k++) if (e.getValue()[k] == null) b.append(" ").append(gr.get(k)).append(" fehlt zu ").append(Instant.ofEpochSecond(e.getKey())).append(';');
            n++;
        }
        long soll = (d.felder.lastKey() - t0) / s + 1;
        if (soll != n) b.append(" ").append(soll - n).append(" Zeiten fehlen zwischen ").append(d.start()).append(" und ").append(d.ende()).append(';');
        return b.toString().strip();
    }
}
