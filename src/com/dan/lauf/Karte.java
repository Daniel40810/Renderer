package com.dan.lauf;

import com.dan.core.gitter.Gelaende;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Bauten;
import com.dan.core.szene.Boden;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.stream.IntStream;

/**
 * Eine Karte von oben (seit X1, Werkbuch HalligHooge 145, F241 A): das Gelände eines Gebiets als Bild in einem
 * Rechteck in UTM, mit Schritt {@link #zelle} – Albedo des Bodens (Luftbild, {@link Boden}), Schummerung aus der Höhe,
 * Dächer und Körper der Gestalt ({@link Bauten}) mit Schlagschatten, und auf Wunsch das Wasser eines Stands.
 * Allgemein: kein Ort, keine Szene; das Rechteck und die Zeit gibt der Aufrufer.
 * <p>
 * <b>Raster:</b> Bildpunkt (i, j) hat die Mitte bei ost0 + (i + ½)·zelle, nord0 + (j + ½)·zelle; die Felder hier laufen
 * mit j von Süd nach Nord (wie {@link Gelaende#fuelle}), das Bild ({@link #bild}) zeilenweise von Nord.
 * <p>
 * <b>Licht [G]:</b> Sonne aus Nordwest (315°), 35° hoch, Höhe für die Schummerung mal {@link #UEBERHOEHUNG}; Belichtung
 * 1 − e^(−3,2·L), dann sRGB. Wasser: Tiefe d = η − Grund, ab 3 cm; Deckung 1 − e^(−d/0,5 m), Farbe vom flachen zum
 * tiefen Wasser über 1 − e^(−d/3 m).
 */
public final class Karte {
    public static final double UEBERHOEHUNG = 3, SONNE_AZIMUT = 315, SONNE_HOEHE = 35, BELICHTUNG = 3.2;
    /** Tiefe (m), ab der ein Bildpunkt als Wasser gilt (wie die Nässe im Bild: 5 cm; hier 3 cm für die Kante). */
    public static final double NASS = 0.03;

    public final double ost0, nord0, ost1, nord1, zelle;
    public final int nx, ny;
    /** Höhe (m NHN; NaN außerhalb), j von Süd. */
    private final float[] hoehe;
    /** Farbe des trockenen Bodens mit Schummerung und Dächern, linear, je Bildpunkt RGB; j von Süd. */
    private final float[] land;
    /** Höhe der Dächer und Körper (m NHN; NaN: keiner), j von Süd. */
    private final float[] dach;
    private final Bauten haeuser;

    private Karte(double ost0, double nord0, double zelle, int nx, int ny, float[] hoehe, float[] land, float[] dach, Bauten haeuser) {
        this.ost0 = ost0;
        this.nord0 = nord0;
        this.zelle = zelle;
        this.nx = nx;
        this.ny = ny;
        this.ost1 = ost0 + nx * zelle;
        this.nord1 = nord0 + ny * zelle;
        this.hoehe = hoehe;
        this.land = land;
        this.dach = dach;
        this.haeuser = haeuser;
    }

    /**
     * Liest Gelände ({@code gelaende}), Boden ({@code rauheit}, {@code luftbild}) und Bauten ({@code bau}) des Gebiets und
     * rechnet das Land im Rechteck {ost0, nord0, ost1, nord1} mit Schritt {@code zelle} (m). Parallel über die Zeilen.
     */
    public static Karte aus(File gebiet, double[] rechteck, double zelle) throws IOException {
        int nx = (int) Math.round((rechteck[2] - rechteck[0]) / zelle), ny = (int) Math.round((rechteck[3] - rechteck[1]) / zelle);
        if (nx < 2 || ny < 2 || (long) nx * ny > 60_000_000L) throw new IllegalArgumentException("Karte: " + nx + " × " + ny + " Bildpunkte");
        double o0 = rechteck[0], n0 = rechteck[1];
        Gelaende gel = Gelaende.ordner(new File(gebiet, "gelaende"));
        Boden boden = Boden.aus(gebiet, o0, n0 + ny * zelle);
        float[] h = new float[nx * ny];
        float[] alb = new float[3 * nx * ny];
        IntStream.range(0, ny).parallel().forEach(j -> {
            Gelaende.Abfrage q = gel.abfrage();
            double nord = n0 + (j + 0.5) * zelle, z = ny * zelle - (j + 0.5) * zelle;
            for (int i = 0; i < nx; i++) {
                double ost = o0 + (i + 0.5) * zelle;
                h[i + nx * j] = q.hoehe(ost, nord, zelle);
                float[] p = boden.punkt((i + 0.5) * zelle, z, zelle);
                int k = 3 * (i + nx * j);
                alb[k] = p[0];
                alb[k + 1] = p[1];
                alb[k + 2] = p[2];
            }
        });
        Bauten b = Bauten.lies(new File(gebiet, "bau")), k = Bauten.lies(new File(gebiet, "bau"), Bauten.KOERPER);
        Bauten alle = b == null ? k : k == null ? b : Bauten.zusammen(b, k);
        float[] dach = new float[nx * ny];
        java.util.Arrays.fill(dach, Float.NaN);
        float[] dachFarbe = new float[3 * nx * ny];
        Karte ka = new Karte(o0, n0, zelle, nx, ny, h, new float[3 * nx * ny], dach, b);
        if (alle != null) ka.daecher(alle, dachFarbe);
        ka.land(alb, dachFarbe);
        return ka;
    }

    // ------------------------------------------------------------------ Abfragen

    /** Höhe (m NHN) am nächsten Bildpunkt; NaN außerhalb. */
    public float hoehe(double ost, double nord) {
        int i = (int) Math.floor((ost - ost0) / zelle), j = (int) Math.floor((nord - nord0) / zelle);
        return i < 0 || j < 0 || i >= nx || j >= ny ? Float.NaN : hoehe[i + nx * j];
    }

    /** Größte Höhe des Geländes im Umkreis r (m) – die Kuppe einer Warft; NaN, wenn dort nichts ist. */
    public double kuppe(double ost, double nord, double r) {
        double m = Double.NaN;
        for (double[] p : umkreis(ost, nord, 0, r)) if (!(m >= p[2])) m = p[2];
        return m;
    }

    /**
     * Der Median der Höhen im Ring r0 … r1 (m) um den Ort – der Fuß einer Warft, wie die Fenne ringsum, ohne Gräben und
     * Priele (seit X2; vorher das untere Zehntel, das in den Gräben lag). NaN, wenn dort nichts ist.
     */
    public double fuss(double ost, double nord, double r0, double r1) {
        java.util.List<double[]> l = umkreis(ost, nord, r0, r1);
        if (l.isEmpty()) return Double.NaN;
        double[] w = l.stream().mapToDouble(p -> p[2]).sorted().toArray();
        return w[w.length / 2];
    }

    /** Zahl der Häuser (ohne Körper der Gestalt), deren Mitte im Umkreis r (m) liegt. */
    public int haeuser(double ost, double nord, double r) {
        if (haeuser == null) return 0;
        int n = 0;
        for (Bauten.Gebaeude g : haeuser.gebaeude) {
            if (g.huelle[0] > g.huelle[2]) continue;
            double x = haeuser.e0 + (g.huelle[0] + g.huelle[2]) / 2, y = haeuser.n0 - (g.huelle[1] + g.huelle[3]) / 2;
            if (Math.hypot(x - ost, y - nord) <= r) n++;
        }
        return n;
    }

    private java.util.List<double[]> umkreis(double ost, double nord, double r0, double r1) {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        int ri = (int) Math.ceil(r1 / zelle) + 1;
        int ci = (int) Math.floor((ost - ost0) / zelle), cj = (int) Math.floor((nord - nord0) / zelle);
        for (int j = cj - ri; j <= cj + ri; j++)
            for (int i = ci - ri; i <= ci + ri; i++) {
                if (i < 0 || j < 0 || i >= nx || j >= ny) continue;
                double x = ost0 + (i + 0.5) * zelle, y = nord0 + (j + 0.5) * zelle, d = Math.hypot(x - ost, y - nord);
                float v = hoehe[i + nx * j];
                if (d >= r0 && d <= r1 && !Float.isNaN(v)) l.add(new double[] {x, y, v});
            }
        return l;
    }

    // ------------------------------------------------------------------ Wasser eines Stands

    /**
     * Der Wasserstand η (m NHN; NaN ohne Wasser oder außerhalb der Gitter) zur Szenenzeit t (s seit dem Start der Quelle)
     * je Bildpunkt der Karte, j von Süd. Die Bühne (ohne Fenster, mit geöffnetem Szenario) zeichnet dazu ein kleines
     * Standbild, damit die Stände zur Zeit t gelesen sind, und gibt dann ihr Wasser ({@link Buehne#wasserRaster}).
     */
    public float[] wasser(Buehne b, double t) {
        Blick bl = new Blick();
        new Ansicht("karte", "Karte", (ost0 + ost1) / 2, (nord0 + nord1) / 2, 2000, false, 0, -60, 60).setze(bl, null, null, null);
        b.standbild(bl, t);
        return b.wasserRaster(ost0, nord0, ost1, nord1, nx, ny);
    }

    /** Anteil der Bildpunkte mit Wasser (Tiefe über {@link #NASS}) bei diesem Wasserstand. */
    public double wasserAnteil(float[] eta) {
        long n = 0, a = 0;
        for (int k = 0; k < hoehe.length; k++) {
            if (Float.isNaN(hoehe[k])) continue;
            a++;
            if (tiefe(eta, k) > NASS) n++;
        }
        return a == 0 ? Double.NaN : (double) n / a;
    }

    private double tiefe(float[] eta, int k) {
        float h = hoehe[k];
        if (!Float.isNaN(dach[k])) return 0;
        float e = eta == null ? Float.NaN : eta[k];
        // außerhalb der Gitter des Wassers: tiefer Grund (unter −1 m NHN) gilt als See um das Mittelwasser
        if (Float.isNaN(e)) return Float.isNaN(h) ? 20 : h < -1 ? -h : 0;
        return Float.isNaN(h) ? 20 : e - h;
    }

    // ------------------------------------------------------------------ Bild

    /** Das Bild (sRGB, Zeilen von Nord), mit dem Wasser {@code eta} (null: ohne Wasser, nur Land und Watt). */
    public BufferedImage bild(float[] eta) {
        BufferedImage img = new BufferedImage(nx, ny, BufferedImage.TYPE_INT_RGB);
        int[] zeile = new int[nx];
        float[] flach = {0.050f, 0.115f, 0.115f}, tief = {0.010f, 0.034f, 0.050f}, himmel = {0.030f, 0.040f, 0.050f};
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                int k = i + nx * j;
                float r = land[3 * k], g = land[3 * k + 1], bl = land[3 * k + 2];
                double d = tiefe(eta, k);
                if (d > NASS) {
                    double deck = 1 - Math.exp(-d / 0.5), s = 1 - Math.exp(-d / 3);
                    double wr = flach[0] + s * (tief[0] - flach[0]) + himmel[0], wg = flach[1] + s * (tief[1] - flach[1]) + himmel[1],
                            wb = flach[2] + s * (tief[2] - flach[2]) + himmel[2];
                    // der Grund unter flachem Wasser: nass und dunkler
                    r = (float) (0.55 * r * (1 - deck) + wr * deck);
                    g = (float) (0.55 * g * (1 - deck) + wg * deck);
                    bl = (float) (0.60 * bl * (1 - deck) + wb * deck);
                }
                zeile[i] = (srgb(r) << 16) | (srgb(g) << 8) | srgb(bl);
            }
            img.setRGB(0, ny - 1 - j, nx, 1, zeile, 0, nx);
        }
        return img;
    }

    private static int srgb(double lin) {
        double v = 1 - Math.exp(-BELICHTUNG * Math.max(0, lin));
        double s = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, s)) * 255);
    }

    /** Schummerung und Schlagschatten auf den Boden, Dächer darüber: füllt {@link #land}. */
    private void land(float[] alb, float[] dachFarbe) {
        double az = Math.toRadians(SONNE_AZIMUT), el = Math.toRadians(SONNE_HOEHE);
        double lx = Math.sin(az) * Math.cos(el), ly = Math.cos(az) * Math.cos(el), lz = Math.sin(el);
        float[] schatten = schatten(lx, ly, Math.tan(el));
        IntStream.range(0, ny).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int k = i + nx * j;
                float h = hoehe[k];
                double s = 1;
                if (!Float.isNaN(h)) {
                    double hw = wert(i - 1, j, h), ho = wert(i + 1, j, h), hs = wert(i, j - 1, h), hn = wert(i, j + 1, h);
                    double dx = (ho - hw) / (2 * zelle) * UEBERHOEHUNG, dy = (hn - hs) / (2 * zelle) * UEBERHOEHUNG;
                    double n = Math.sqrt(dx * dx + dy * dy + 1);
                    double cos = (-dx * lx - dy * ly + lz) / n;
                    s = (0.40 + 0.60 * Math.max(0, cos) / lz);
                }
                s *= 1 - 0.45 * schatten[k];
                boolean d = !Float.isNaN(dach[k]);
                for (int c = 0; c < 3; c++) land[3 * k + c] = (float) ((d ? dachFarbe[3 * k + c] : alb[3 * k + c]) * (d ? 1 : s));
            }
        });
    }

    private double wert(int i, int j, double sonst) {
        if (i < 0 || j < 0 || i >= nx || j >= ny) return sonst;
        float v = hoehe[i + nx * j];
        return Float.isNaN(v) ? sonst : v;
    }

    /** Schlagschatten der Dächer und Körper auf den Boden (0 … 1), von der Sonne weg. */
    private float[] schatten(double lx, double ly, double tanEl) {
        float[] s = new float[nx * ny];
        // Richtung vom Licht weg, in Bildpunkten je Meter Höhe
        double sx = -lx / Math.hypot(lx, ly), sy = -ly / Math.hypot(lx, ly);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                float d = dach[i + nx * j];
                if (Float.isNaN(d)) continue;
                double ueber = d - wert(i, j, d);
                if (!(ueber > 0.3)) continue;
                double laenge = ueber / tanEl / zelle;
                for (double t = 1; t <= laenge; t += 0.7) {
                    int a = (int) Math.round(i + sx * t), b = (int) Math.round(j + sy * t);
                    if (a < 0 || b < 0 || a >= nx || b >= ny) break;
                    int k = a + nx * b;
                    if (!Float.isNaN(dach[k]) && dach[k] >= d - ueber * (t / laenge)) continue;
                    s[k] = 1;
                }
            }
        return s;
    }

    /** Dächer und Körper der Gestalt: je Dreieck mit Normale nach oben, der höchste je Bildpunkt; Farbe mit Licht. */
    private void daecher(Bauten alle, float[] farbe) {
        double az = Math.toRadians(SONNE_AZIMUT), el = Math.toRadians(SONNE_HOEHE);
        double lx = Math.sin(az) * Math.cos(el), ly = Math.cos(az) * Math.cos(el), lz = Math.sin(el);
        float[] v = alle.scheitel;
        int F = Bauten.FLOATS;
        for (int t = 0; t + 3 * F <= v.length; t += 3 * F) {
            int art = Math.round(v[t + 3]);
            // Normale im Raum: x Ost, y oben, z Süd
            double nxr = v[t + 4], nyr = v[t + 5], nzr = v[t + 6];
            if (nyr < 0.15) continue;                                     // Wände und Unterseiten sieht man von oben nicht
            float[] alb = albedo(alle, art, Math.round(v[t + 7]));
            if (alb == null) continue;
            double cos = (nxr * lx - nzr * ly + nyr * lz) / Math.sqrt(nxr * nxr + nyr * nyr + nzr * nzr);
            double s = 0.40 + 0.60 * Math.max(0, cos) / lz;
            double[] px = new double[3], py = new double[3], pz = new double[3];
            for (int e = 0; e < 3; e++) {
                int o = t + e * F;
                px[e] = (alle.e0 + v[o] - ost0) / zelle - 0.5;
                py[e] = (alle.n0 - v[o + 2] - nord0) / zelle - 0.5;
                pz[e] = v[o + 1];
            }
            int i0 = (int) Math.floor(Math.min(px[0], Math.min(px[1], px[2]))), i1 = (int) Math.ceil(Math.max(px[0], Math.max(px[1], px[2])));
            int j0 = (int) Math.floor(Math.min(py[0], Math.min(py[1], py[2]))), j1 = (int) Math.ceil(Math.max(py[0], Math.max(py[1], py[2])));
            if (i1 < 0 || j1 < 0 || i0 >= nx || j0 >= ny) continue;
            double den = (py[1] - py[2]) * (px[0] - px[2]) + (px[2] - px[1]) * (py[0] - py[2]);
            if (Math.abs(den) < 1e-9) continue;
            for (int j = Math.max(0, j0); j <= Math.min(ny - 1, j1); j++)
                for (int i = Math.max(0, i0); i <= Math.min(nx - 1, i1); i++) {
                    double a = ((py[1] - py[2]) * (i - px[2]) + (px[2] - px[1]) * (j - py[2])) / den;
                    double b = ((py[2] - py[0]) * (i - px[2]) + (px[0] - px[2]) * (j - py[2])) / den;
                    double c = 1 - a - b;
                    if (a < -1e-6 || b < -1e-6 || c < -1e-6) continue;
                    double z = a * pz[0] + b * pz[1] + c * pz[2];
                    int k = i + nx * j;
                    if (!Float.isNaN(dach[k]) && dach[k] >= z) continue;
                    dach[k] = (float) z;
                    for (int q = 0; q < 3; q++) farbe[3 * k + q] = (float) (alb[q] * s);
                }
        }
    }

    /** Albedo (linear) eines Dreiecks nach seiner Art; null: nicht zeigen. */
    private static float[] albedo(Bauten alle, int art, int gebaeude) {
        return switch (art) {
            case Bauten.DACH -> gebaeude >= 0 && gebaeude < alle.gebaeude.size() ? alle.gebaeude.get(gebaeude).dachAlbedo : new float[] {0.12f, 0.10f, 0.08f};
            case Bauten.HOLZ -> new float[] {0.16f, 0.11f, 0.07f};
            case Bauten.STEIN -> new float[] {0.20f, 0.20f, 0.19f};
            case Bauten.BETON -> new float[] {0.32f, 0.31f, 0.29f};
            case Bauten.BUSCH -> new float[] {0.08f, 0.09f, 0.05f};
            default -> null;
        };
    }
}
