package com.dan.beach;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.SplittableRandom;

/**
 * Der Strand als Oberfläche aus einem Asset (seit Timmendorf): Relief und Korn des Sands in der Nähe des Auges.
 * <p>
 * Die Karten {@code relief.png} und {@code grain.png} sind aus einem Sand-Asset gebacken (glTF {@code sand_dunes_gltf.zip},
 * „Main sand dune“ aus Fab, mit {@code tools/beach/SandAsset.java}): das Relief des Netzes ohne den großen Hügel (Mulden,
 * Kämme, Rinnen) und die Helligkeit seiner Grundfarbe ohne ihr Mittel (Korn, Muschelgrus, dunkle Minerale), beide
 * kachelbar, auf mittlere Abweichung 1 gebracht. Das Asset selbst braucht das Package nicht.
 * <p>
 * Die Oberfläche legt zwei Oktaven des Reliefs übereinander (die zweite kleiner und gedreht, so fallen die Kacheln nicht
 * auf) und skaliert sie nach dem Ort: auf trockenem Sand voll, auf nassem geglättet (die Wellen ebnen ihn), wo viele gehen
 * (Strandkörbe, Wege zum Wasser) zertreten, sonst ruhiger. Höhe, Neigung und Helligkeit gibt es ohne Grafikkarte
 * ({@link #heightAt}, {@link #normalAt}, {@link #albedoAt}); auf der Grafikkarte dasselbe mit {@code gl/beach.glsl}.
 * <p>
 * Koordinaten wie in den übrigen Packages: x nach Osten, z nach Süden, Höhen in m. Werte mit [G] sind gestaltet.
 */
public final class Beach {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag): Reichweite des Reliefs um das Auge. */
    public enum Quality {
        LOW(60), MEDIUM(120), HIGH(200), ULTRA(350);
        /** Bis zu diesem Abstand vom Auge (m) gibt es Relief und Korn. */
        public final double range;

        Quality(double range) { this.range = range; }
    }

    /** Die gebackenen Karten: Relief und Korn (Abweichung 1), Neigung des Reliefs je Kachel, Maße der Quelle. */
    public static final class Maps {
        public final int n;
        /** Seite der Kachel in der Quelle (m) und Abweichung des Reliefs dort (m). */
        public final double sourceTile, sourceReliefRms;
        public final String source;
        public final float[] relief, grain, du, dv;
        /** Mittlere Neigung des Reliefs je Achse (dR/du, Abweichung 1, je Kachel). */
        public final double slopeRms;

        Maps(int n, double tile, double rms, String source, float[] relief, float[] grain) {
            this.n = n;
            this.sourceTile = tile;
            this.sourceReliefRms = rms;
            this.source = source;
            this.relief = relief;
            this.grain = grain;
            du = new float[n * n];
            dv = new float[n * n];
            // Neigung je Kachel (dR/du mit u = 0..1 über die Kachel), zentrale Differenzen über den Rand hinweg
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    du[j * n + i] = (relief[j * n + (i + 1) % n] - relief[j * n + (i + n - 1) % n]) * n / 2f;
                    dv[j * n + i] = (relief[((j + 1) % n) * n + i] - relief[((j + n - 1) % n) * n + i]) * n / 2f;
                }
            double q = 0;
            for (int k = 0; k < n * n; k++) q += du[k] * du[k] + dv[k] * dv[k];
            slopeRms = Math.sqrt(q / (2.0 * n * n));
        }

        /** Bilinear mit Umlauf; u, v in Kacheln. */
        public double sample(float[] a, double u, double v) {
            double x = (u - Math.floor(u)) * n - 0.5, y = (v - Math.floor(v)) * n - 0.5;
            int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
            double fx = x - x0, fy = y - y0;
            int xa = Math.floorMod(x0, n), xb = Math.floorMod(x0 + 1, n), ya = Math.floorMod(y0, n), yb = Math.floorMod(y0 + 1, n);
            return (a[ya * n + xa] * (1 - fx) + a[ya * n + xb] * fx) * (1 - fy) + (a[yb * n + xa] * (1 - fx) + a[yb * n + xb] * fx) * fy;
        }
    }

    private static volatile Maps maps;

    /** Die Karten aus dem Package (einmal gelesen, geteilt). */
    public static Maps maps() {
        Maps m = maps;
        if (m != null) return m;
        synchronized (Beach.class) {
            if (maps == null) maps = load();
            return maps;
        }
    }

    private static Maps load() {
        try {
            Properties p = new Properties();
            try (InputStream in = Beach.class.getResourceAsStream("sand.txt")) {
                if (in == null) throw new IllegalStateException("beach: sand.txt fehlt im Package");
                p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            float[] r = read16("relief.png"), g = read16("grain.png");
            int n = (int) Math.round(Math.sqrt(r.length));
            return new Maps(n, Double.parseDouble(p.getProperty("kachel_m", "6")), Double.parseDouble(p.getProperty("relief_rms_m", "0.1")),
                    p.getProperty("quelle", "?"), r, g);
        } catch (IOException e) {
            throw new IllegalStateException("beach: Karten lassen sich nicht lesen: " + e.getMessage(), e);
        }
    }

    /** 16-Bit-Grau aus dem Package: Wert = (grau / 65535 − 0,5) · 16. */
    static float[] read16(String file) throws IOException {
        try (InputStream in = Beach.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("beach: " + file + " fehlt im Package");
            BufferedImage im = ImageIO.read(in);
            Raster r = im.getRaster();
            int w = im.getWidth(), h = im.getHeight();
            float[] a = new float[w * h];
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) a[j * w + i] = (float) ((r.getSample(i, j, 0) / 65535.0 - 0.5) * 16);
            return a;
        }
    }

    // ------------------------------------------------------------------ offene Werte

    public Quality quality = Quality.HIGH;
    /** Startwert für die Lage der Oktaven: gleicher seed, gleicher Strand. */
    public long seed = 1;
    /** Seite der Kachel des Reliefs (m) und seine Abweichung auf trockenem, zertretenem Sand (m). */
    public double tile = 2.4, relief = 0.022;
    /** Zweite Oktave: Größe als Anteil der ersten, Drehung (Grad), Gewicht. */
    public double octave2 = 0.43, octave2Deg = 37, octave2Weight = 0.55;
    /** Seite der Kachel des Korns (m) und wie stark es die Helligkeit ändert (Anteil je Abweichung). */
    public double grainTile = 3.0, grainContrast = 0.10;
    /** Anteil des Reliefs auf nassem Sand und dort, wo niemand geht. */
    public double wetRelief = 0.25, calmRelief = 0.45;
    /** Mulden dunkler, Kämme heller (Anteil je Abweichung). */
    public double cavity = 0.06;

    // ------------------------------------------------------------------ Presets

    /**
     * Ostseestrand wie Timmendorf [G]: feiner heller Sand, zwischen den Körben zertreten (Mulden und Tritte von 15 bis
     * 40 cm, 2 cm tief), am Wasser glatt.
     */
    public static Beach ostsee() {
        Beach b = new Beach();
        b.seed = 1923;
        return b;
    }

    /** Nordseestrand [G]: gröber, vom Wind geformt, weniger zertreten, Kacheln größer. */
    public static Beach nordsee() {
        Beach b = new Beach();
        b.seed = 2026;
        b.tile = 3.6;
        b.relief = 0.03;
        b.calmRelief = 0.7;
        b.grainTile = 4.0;
        b.grainContrast = 0.08;
        return b;
    }

    /** Wie das Asset: die Düne in ihrer eigenen Größe (Kachel und Relief der Quelle), überall gleich. */
    public static Beach duene() {
        Beach b = new Beach();
        Maps m = maps();
        b.seed = 7;
        b.tile = m.sourceTile;
        b.relief = m.sourceReliefRms;
        b.octave2 = 0.37;
        b.octave2Weight = 0.35;
        b.calmRelief = 1;
        b.wetRelief = 1;
        b.grainTile = m.sourceTile;
        b.grainContrast = 0.14;
        return b;
    }

    public Beach copy() {
        Beach b = new Beach();
        b.quality = quality; b.seed = seed; b.tile = tile; b.relief = relief;
        b.octave2 = octave2; b.octave2Deg = octave2Deg; b.octave2Weight = octave2Weight;
        b.grainTile = grainTile; b.grainContrast = grainContrast; b.wetRelief = wetRelief; b.calmRelief = calmRelief; b.cavity = cavity;
        return b;
    }

    /** Verschiebung der Oktaven in Kacheln {Relief 1 u, v, Relief 2 u, v} aus dem seed. */
    public double[] offsets() {
        SplittableRandom r = new SplittableRandom(seed * 0x9E3779B97F4A7C15L + 11);
        return new double[]{r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble()};
    }

    // ------------------------------------------------------------------ Abfragen ohne Grafikkarte

    /** Abweichung an diesem Ort: trocken 0..1 (0 nass), zertreten 0..1 (0 niemand geht dort). */
    public double amplitude(double trocken, double zertreten) {
        double t = clamp01(trocken), z = clamp01(zertreten);
        return relief * (wetRelief + (1 - wetRelief) * t) * (calmRelief + (1 - calmRelief) * z);
    }

    /** {Höhe in Abweichungen, dR/dx, dR/dz in Abweichungen je m} aus beiden Oktaven. */
    double[] field(double x, double z) {
        Maps m = maps();
        double[] o = offsets();
        double u1 = x / tile + o[0], v1 = z / tile + o[1];
        double h = m.sample(m.relief, u1, v1), gx = m.sample(m.du, u1, v1) / tile, gz = m.sample(m.dv, u1, v1) / tile;
        double a = Math.toRadians(octave2Deg), c = Math.cos(a), s = Math.sin(a), t2 = tile * octave2;
        double qx = c * x + s * z, qz = -s * x + c * z, u2 = qx / t2 + o[2], v2 = qz / t2 + o[3];
        double h2 = m.sample(m.relief, u2, v2), ax = m.sample(m.du, u2, v2) / t2, az = m.sample(m.dv, u2, v2) / t2;
        // zurück in die Welt: grad_p = Rᵀ grad_q
        h += octave2Weight * h2;
        gx += octave2Weight * (c * ax - s * az);
        gz += octave2Weight * (s * ax + c * az);
        return new double[]{h, gx, gz};
    }

    /** Höhe des Reliefs über dem Gelände (m). */
    public double heightAt(double x, double z, double trocken, double zertreten) {
        return field(x, z)[0] * amplitude(trocken, zertreten);
    }

    /** Normale des Reliefs auf ebenem Grund {x, y, z}. */
    public double[] normalAt(double x, double z, double trocken, double zertreten) {
        double[] f = field(x, z);
        double k = amplitude(trocken, zertreten), gx = f[1] * k, gz = f[2] * k, l = Math.sqrt(gx * gx + 1 + gz * gz);
        return new double[]{-gx / l, 1 / l, -gz / l};
    }

    /** Faktor auf die Helligkeit des Sands (Korn und Mulden), um 1. */
    public double albedoAt(double x, double z, double trocken, double zertreten) {
        Maps m = maps();
        double[] o = offsets();
        double a = Math.toRadians(octave2Deg), c = Math.cos(a), s = Math.sin(a);
        double k = 0.6 * m.sample(m.grain, x / grainTile + o[1], z / grainTile + o[0])
                + 0.4 * m.sample(m.grain, (c * x - s * z) / (grainTile * 0.37) + o[3], (s * x + c * z) / (grainTile * 0.37) + o[2]);
        double h = Math.max(-2, Math.min(2, field(x, z)[0])) * (amplitude(trocken, zertreten) / Math.max(1e-9, relief));
        return (1 + grainContrast * k) * (1 + cavity * h * 0.5);
    }

    /** Mittlere Neigung je Achse an diesem Ort (beide Oktaven, m/m). */
    public double slopeRms(double trocken, double zertreten) {
        double s = maps().slopeRms, a = s / tile, b = octave2Weight * s / (tile * octave2);
        return amplitude(trocken, zertreten) * Math.sqrt(a * a + b * b);
    }

    /**
     * Ausgleich des Sonnenlichts im Relief [G]: über einer rauen Fläche mit der Neigung σ (je Achse, normalverteilt) ist
     * das mittlere max(0, n·l) bei tiefer Sonne größer als auf ebenem Grund, σ cos e (φ(a) + a Φ(a)) mit a = tan e / σ;
     * die Mulden werfen aber Schatten aufeinander. Der Faktor sin e / Mittel macht das Relief im Mittel so hell wie ebenen
     * Sand, so gibt es keine Kante, wo es in der Ferne ausgeht. sinE: Sinus der Sonnenhöhe über dem Gelände.
     */
    public static double sunFactor(double sigma, double sinE) {
        if (sinE <= 0) return 0;
        if (sigma < 1e-5) return 1;
        double cosE = Math.sqrt(Math.max(0, 1 - sinE * sinE)), a = sinE / Math.max(1e-9, cosE * sigma);
        double phi = Math.exp(-a * a / 2) / Math.sqrt(2 * Math.PI), cdf = 0.5 * (1 + erf(a / Math.sqrt(2)));
        double mean = cosE * sigma * (phi + a * cdf);
        return Math.max(0.2, Math.min(1, sinE / Math.max(1e-9, mean)));
    }

    /** Fehlerfunktion (Abramowitz und Stegun 7.1.26, Fehler unter 1,5·10⁻⁷). */
    static double erf(double x) {
        double t = 1 / (1 + 0.3275911 * Math.abs(x));
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return x >= 0 ? y : -y;
    }

    static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    /** „Strand aus sand_dunes_gltf.zip: Kachel 2,4 m, Relief 2,2 cm (nass 0,6, ruhig 1,0 cm), Korn 3,0 m ±10 % · bis 200 m“ */
    public String describe() {
        Maps m = maps();
        return String.format(Locale.GERMAN, "Strand aus %s (%d², Quelle %.0f m, %.1f cm): Kachel %.1f m, Relief %.1f cm (nass %.1f, ruhig %.1f cm), Korn %.1f m ±%.0f %% · bis %.0f m",
                m.source, m.n, m.sourceTile, m.sourceReliefRms * 100, tile, relief * 100, amplitude(0, 1) * 100, amplitude(1, 0) * 100, grainTile,
                grainContrast * 100, quality.range);
    }
}
