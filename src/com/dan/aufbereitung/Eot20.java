package com.dan.aufbereitung;

import com.dan.core.astro.Partialtide;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Tidenfeld;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * EOT20 lesen und ausschneiden (Werkbuch HalligHooge Teil A, 7.2 und 12.6, Befund 1–2, P5b): die 17 Partialtiden der
 * Meeresgezeiten (Hart-Davis u. a. 2021, CC BY 4.0) auf 1/8°, je Tide eine NetCDF-4-Datei mit {@code real} und
 * {@code imag} in cm. Die Quelle darf sein: {@code EOT20.zip} (darin {@code ocean_tides.zip}), {@code ocean_tides.zip}
 * selbst oder ein Ordner mit den Dateien {@code <Tide>_ocean_eot20.nc}. Gelesen mit {@link Hdf5}, ohne Entpacken auf
 * die Platte.
 * <p>
 * Ausgeschnitten wird ein {@link Gradraster} auf den Punkten von EOT20, das ein Rechteck in Grad umschließt (über 0°
 * hinweg: EOT20 zählt die Länge 0 … 360°). Werte in m; wo EOT20 die Amplitude 0 hat (Land), NaN.
 */
public final class Eot20 {
    static final Pattern DATEI = Pattern.compile("(?:^|/)([0-9A-Za-z]+)_ocean_eot20\\.nc$");
    /** Raster von EOT20: 1/8°, Breite −90 … 90, Länge 0 … 360. */
    static final double D = 0.125;

    private Eot20() { }

    /** Liest alle 17 Tiden und schneidet das Rechteck West Süd Ost Nord (Grad) aus, auf die Punkte von EOT20 erweitert. */
    public static Tidenfeld lies(Path quelle, double west, double sued, double ost, double nord, Consumer<String> log) throws IOException {
        int i0 = (int) Math.floor(west / D + 1e-9), i1 = (int) Math.ceil(ost / D - 1e-9), j0 = (int) Math.floor((sued + 90) / D + 1e-9), j1 = (int) Math.ceil((nord + 90) / D - 1e-9);
        Gradraster r = new Gradraster(i0 * D, j0 * D - 90, D, D, i1 - i0 + 1, j1 - j0 + 1);
        Map<Partialtide, float[]> gelesen = new TreeMap<>((a, b) -> Integer.compare(Partialtide.EOT20.indexOf(a), Partialtide.EOT20.indexOf(b)));
        Leser l = (name, daten) -> {
            Partialtide p = Partialtide.nach(name);
            if (p == null || !Partialtide.EOT20.contains(p)) {
                log.accept("  EOT20: " + name + " übergangen (keine der 17 Tiden)");
                return;
            }
            long t = System.nanoTime();
            Hdf5 h = Hdf5.lies(daten);
            double[] lat = h.doubles("lat"), lon = h.doubles("lon");
            if (lat.length != 1441 || lon.length != 2881 || Math.abs(lat[0] + 90) > 1e-9 || Math.abs(lon[1] - D) > 1e-9)
                throw new IOException("EOT20 " + name + ": Raster " + lat.length + " × " + lon.length + " statt 1441 × 2881 zu 1/8°");
            int ja = j0, je = j1 + 1;
            double[] re = h.doubles("real", ja, je, 0, 2881), im = h.doubles("imag", ja, je, 0, 2881), a = h.doubles("amplitude", ja, je, 0, 2881);
            float[] w = new float[2 * r.punkte()];
            int land = 0;
            for (int j = 0; j < r.ny(); j++)
                for (int i = 0; i < r.nx(); i++) {
                    int q = Math.floorMod(i0 + i, 2880), k = j * 2881 + q, o = 2 * (j * r.nx() + i);
                    if (!(a[k] > 0) || Double.isNaN(re[k]) || Double.isNaN(im[k])) {
                        w[o] = w[o + 1] = Float.NaN;
                        land++;
                    } else {
                        w[o] = (float) (re[k] / 100);
                        w[o + 1] = (float) (im[k] / 100);
                    }
                }
            synchronized (gelesen) {
                if (gelesen.put(p, w) != null) throw new IOException("EOT20: " + name + " doppelt in der Quelle");
            }
            log.accept(Aufbereitung.f("  EOT20 %-3s: %s, %d von %d Punkten an Land; %.1f s", p.name, h.datensatz("real").toString().replaceFirst("^real ", ""), land, r.punkte(),
                    (System.nanoTime() - t) / 1e9));
        };
        durchlaufe(quelle, l);
        List<Partialtide> fehlt = new ArrayList<>(Partialtide.EOT20);
        fehlt.removeAll(gelesen.keySet());
        if (!fehlt.isEmpty()) throw new IOException("EOT20: in " + quelle + " fehlen " + fehlt);
        return new Tidenfeld(r, new ArrayList<>(gelesen.keySet()), gelesen.values().toArray(new float[0][]));
    }

    interface Leser {
        void datei(String tide, byte[] daten) throws IOException;
    }

    /** Ruft {@code l} für jede Datei {@code <Tide>_ocean_eot20.nc} der Quelle auf (ZIP in ZIP, ZIP oder Ordner). */
    static void durchlaufe(Path quelle, Leser l) throws IOException {
        if (Files.isDirectory(quelle)) {
            try (Stream<Path> s = Files.list(quelle)) {
                for (Path p : s.sorted().toList()) {
                    Matcher m = DATEI.matcher(p.getFileName().toString());
                    if (m.find()) l.datei(m.group(1), Files.readAllBytes(p));
                }
            }
            return;
        }
        try (ZipFile z = new ZipFile(quelle.toFile())) {
            boolean innen = false;
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry x = e.nextElement();
                if (x.getName().startsWith("__MACOSX")) continue;
                Matcher m = DATEI.matcher(x.getName());
                if (m.find()) {
                    try (InputStream in = z.getInputStream(x)) {
                        l.datei(m.group(1), in.readAllBytes());
                    }
                } else if (x.getName().endsWith("ocean_tides.zip")) {
                    innen = true;
                    try (ZipInputStream zi = new ZipInputStream(z.getInputStream(x))) {
                        for (ZipEntry y; (y = zi.getNextEntry()) != null; ) {
                            if (y.getName().startsWith("__MACOSX")) continue;
                            Matcher n = DATEI.matcher(y.getName());
                            if (n.find()) l.datei(n.group(1), zi.readAllBytes());
                        }
                    }
                }
            }
            if (!innen && z.stream().noneMatch(x -> DATEI.matcher(x.getName()).find())) throw new IOException(quelle + ": weder ocean_tides.zip noch *_ocean_eot20.nc darin");
        }
    }

    /**
     * Örtliche Minima der Amplitude einer Tide wie im Befund (12.6, Befund 2): Punkt mit Wasser, kleinste Amplitude
     * über Wasser im Fenster ±{@code umkreis} Punkte, Amplitude unter {@code hoechstens} (m), im Kasten West Süd Ost Nord.
     * Je Minimum {Länge, Breite, Amplitude (m), Umlauf der Phase um das Quadrat mit dem Punkt (−1, 0, +1; 0 auch, wo ein
     * Nachbar Land ist)}.
     */
    public static List<double[]> minima(Tidenfeld f, Partialtide p, double[] kasten, double hoechstens, int umkreis) {
        int t = f.index(p);
        float[] w = f.werte(t);
        Gradraster r = f.raster;
        List<double[]> out = new ArrayList<>();
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++) {
                double lo = r.laenge(i), la = r.breite(j);
                if (lo <= kasten[0] || la <= kasten[1] || lo >= kasten[2] || la >= kasten[3]) continue;
                double a = amp(w, r, i, j);
                if (Double.isNaN(a) || a >= hoechstens) continue;
                boolean min = true;
                for (int dj = -umkreis; dj <= umkreis && min; dj++)
                    for (int di = -umkreis; di <= umkreis; di++) {
                        int ii = i + di, jj = j + dj;
                        if (ii < 0 || jj < 0 || ii >= r.nx() || jj >= r.ny()) continue;
                        double b = amp(w, r, ii, jj);
                        if (b < a) {
                            min = false;
                            break;
                        }
                    }
                if (min) out.add(new double[] {lo, la, a, umlauf(w, r, i, j)});
            }
        return out;
    }

    private static double amp(float[] w, Gradraster r, int i, int j) {
        int o = 2 * (j * r.nx() + i);
        return Float.isNaN(w[o]) ? Double.NaN : Math.hypot(w[o], w[o + 1]);
    }

    /** Umlauf der Phase um die vier Quadrate mit dem Punkt als Ecke: der größte Betrag (−1, 0, +1). */
    static int umlauf(float[] w, Gradraster r, int i, int j) {
        int best = 0;
        for (int dj = -1; dj <= 0; dj++)
            for (int di = -1; di <= 0; di++) {
                int a = i + di, b = j + dj;
                if (a < 0 || b < 0 || a + 1 >= r.nx() || b + 1 >= r.ny()) continue;
                int[][] ecke = {{a, b}, {a + 1, b}, {a + 1, b + 1}, {a, b + 1}};
                double s = 0;
                boolean ok = true;
                for (int k = 0; k < 4 && ok; k++) {
                    int o1 = 2 * (ecke[k][1] * r.nx() + ecke[k][0]), o2 = 2 * (ecke[(k + 1) % 4][1] * r.nx() + ecke[(k + 1) % 4][0]);
                    if (Float.isNaN(w[o1]) || Float.isNaN(w[o2])) ok = false;
                    else {
                        double d = Math.atan2(w[o2 + 1], w[o2]) - Math.atan2(w[o1 + 1], w[o1]);
                        d = d - 2 * Math.PI * Math.floor((d + Math.PI) / (2 * Math.PI));
                        s += d;
                    }
                }
                if (ok) {
                    int u = (int) Math.round(s / (2 * Math.PI));
                    if (Math.abs(u) > Math.abs(best)) best = u;
                }
            }
        return best;
    }
}
