package com.dan.aufbereitung;

import com.dan.core.raum.Utm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Land nach Natural Earth (Länder 1:10 Mio., {@code ne_10m_admin_0_countries}) aus einem Shapefile in einer
 * ZIP-Datei (Werkbuch HalligHooge Teil A, 10.4, Schritte 7a und 7b): Polygone (Typ 5) in Länge und Breite. Jedes
 * Land wird gerade-ungerade über seine Ringe gefüllt (Löcher wie Enklaven bleiben frei); Länder überlappen nicht.
 * Fjorde und Seen, die die Länder einschließen, zählen als Land.
 */
public final class NaturalEarth {
    /** Ein Land: Ringe {Länge[], Breite[]} und Grenzen {West, Süd, Ost, Nord}. */
    public record Land(int nummer, List<double[][]> ringe, double[] grenzen) { }

    public final List<Land> laender = new ArrayList<>();
    public final Path datei;

    private NaturalEarth(Path datei) { this.datei = datei; }

    /** Liest das erste {@code .shp} der ZIP-Datei. */
    public static NaturalEarth lies(Path zip) throws IOException {
        NaturalEarth ne = new NaturalEarth(zip);
        try (ZipFile z = new ZipFile(zip.toFile())) {
            ZipEntry shp = null;
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry x = e.nextElement();
                if (x.getName().toLowerCase().endsWith(".shp")) { shp = x; break; }
            }
            if (shp == null) throw new IOException(zip + ": kein .shp");
            byte[] b;
            try (InputStream in = z.getInputStream(shp)) { b = in.readAllBytes(); }
            ne.lies(ByteBuffer.wrap(b));
        }
        return ne;
    }

    private void lies(ByteBuffer b) throws IOException {
        b.order(ByteOrder.BIG_ENDIAN);
        if (b.getInt(0) != 9994) throw new IOException(datei + ": kein Shapefile");
        long laenge = 2L * b.getInt(24);
        int p = 100;
        while (p + 8 <= Math.min(laenge, b.capacity())) {
            b.order(ByteOrder.BIG_ENDIAN);
            int nummer = b.getInt(p), inhalt = 2 * b.getInt(p + 4);
            int q = p + 8;
            p = q + inhalt;
            b.order(ByteOrder.LITTLE_ENDIAN);
            int typ = b.getInt(q);
            if (typ == 0) continue;
            if (typ != 5 && typ != 15 && typ != 25) throw new IOException(datei + ": Satz " + nummer + " hat den Typ " + typ + " statt Polygon");
            double[] g = {b.getDouble(q + 4), b.getDouble(q + 12), b.getDouble(q + 20), b.getDouble(q + 28)};
            int teile = b.getInt(q + 36), punkte = b.getInt(q + 40);
            int[] start = new int[teile + 1];
            for (int i = 0; i < teile; i++) start[i] = b.getInt(q + 44 + 4 * i);
            start[teile] = punkte;
            int xy = q + 44 + 4 * teile;
            List<double[][]> ringe = new ArrayList<>();
            for (int t = 0; t < teile; t++) {
                int n = start[t + 1] - start[t];
                if (n < 3) continue;
                double[][] r = new double[2][n];
                for (int i = 0; i < n; i++) {
                    r[0][i] = b.getDouble(xy + 16 * (start[t] + i));
                    r[1][i] = b.getDouble(xy + 16 * (start[t] + i) + 8);
                }
                ringe.add(r);
            }
            laender.add(new Land(nummer, ringe, g));
        }
    }

    /**
     * Land als Maske in geographischer Lage: Fenster mit der Nordwestecke (west, nord), Pixel {@code zelle} Grad,
     * w × h, Zeilen von Nord; 1 = Land. Ein Pixel gehört dazu, wenn seine Mitte im Land liegt.
     */
    public byte[] maskeGrad(double west, double nord, double zelle, int w, int h) {
        byte[] m = new byte[w * h];
        double ost = west + w * zelle, sued = nord - h * zelle;
        for (Land l : laender) {
            double[] g = l.grenzen;
            if (g[2] < west || g[0] > ost || g[3] < sued || g[1] > nord) continue;
            Raster.flaeche(new Osm.Flaeche(l.nummer, true, Map.of(), l.ringe), west, nord, zelle, w, h, m, (byte) 1, null);
        }
        return m;
    }

    /**
     * Land als Maske in UTM: Fenster mit der Nordwestecke (ost0, nord0) in m, Pixel {@code zelle} m, w × h, Zeilen von
     * Nord; 1 = Land. Die Ringe werden Punkt für Punkt nach UTM gerechnet (bei 1:10 Mio. liegen die Punkte dicht genug).
     */
    public byte[] maskeUtm(int zone, double ost0, double nord0, double zelle, int w, int h) {
        byte[] m = new byte[w * h];
        // Grenzen des Fensters in Grad (über den Rand abgetastet, mit Zuschlag)
        double w0 = 1e9, s0 = 1e9, o0 = -1e9, n0 = -1e9;
        for (int k = 0; k <= 20; k++) {
            double a = k / 20.0;
            double[][] p = {Utm.rueckwaerts(ost0 + a * w * zelle, nord0, zone), Utm.rueckwaerts(ost0 + a * w * zelle, nord0 - h * zelle, zone),
                    Utm.rueckwaerts(ost0, nord0 - a * h * zelle, zone), Utm.rueckwaerts(ost0 + w * zelle, nord0 - a * h * zelle, zone)};
            for (double[] q : p) {
                w0 = Math.min(w0, q[0]);
                o0 = Math.max(o0, q[0]);
                s0 = Math.min(s0, q[1]);
                n0 = Math.max(n0, q[1]);
            }
        }
        w0 -= 0.1; s0 -= 0.1; o0 += 0.1; n0 += 0.1;
        for (Land l : laender) {
            double[] g = l.grenzen;
            if (g[2] < w0 || g[0] > o0 || g[3] < s0 || g[1] > n0) continue;
            List<double[][]> ru = new ArrayList<>();
            for (double[][] r : l.ringe) {
                double[][] u = new double[2][r[0].length];
                for (int i = 0; i < r[0].length; i++) {
                    double[] p = Utm.vorwaerts(r[0][i], r[1][i], zone);
                    u[0][i] = p[0];
                    u[1][i] = p[1];
                }
                ru.add(u);
            }
            Raster.flaeche(new Osm.Flaeche(l.nummer, true, Map.of(), ru), ost0, nord0, zelle, w, h, m, (byte) 1, null);
        }
        return m;
    }
}
