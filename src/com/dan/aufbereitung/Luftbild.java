package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schritt luftbild (seit P6b, Werkbuch HalligHooge Teil A, 10.4 und 12.7; F41 A): Albedo je Rauheitsklasse und die
 * Struktur innerhalb der Klasse aus dem Luftbild DOP20.
 * <ol>
 * <li><b>Mitteln:</b> Je DOP-Kachel (1 km, 0,2 m, RGB und Infrarot, 8 Bit) das Mittel aus 5 × 5 Pixeln je 1-m-Pixel der
 * Stufe, einmal mit den Werten selbst (v / 255) und einmal mit sRGB entschlüsselt.</li>
 * <li><b>Eichen:</b> Die Werte des Luftbilds sind keine Reflexion; der Flug hat seine Sonne, sein Himmelslicht und eine
 * Bearbeitung. Gegen Sentinel-2 L2A (Reflexion am Boden, atmosphärisch berichtigt) wird je Kanal ein Faktor g bestimmt:
 * ρ = g · d. Paare sind die 10-m-Pixel von Sentinel-2, die ganz im Luftbild liegen, zu mindestens 90 % [G] einer
 * beständigen Klasse angehören (Fenne, Salzwiese, Deich; nicht Watt, Sand und Wasser, die mit der Tide wechseln, nicht
 * Warft mit den Dächern) und in der Szenenklassifikation Vegetation oder Boden sind. Seit F44 A aus den Bändern B04,
 * B03, B02 (Reflexion = Wert · Maßstab + Versatz, aus dem JSON der Szene); liegen sie nicht vor, aus dem TCI, das bei
 * 25 % Reflexion sättigt [S, Sen2Cor 2.11]: ρ_S2 = TCI / 255 · 0,25. Gerechnet werden beide Fassungen von d; es gilt
 * die mit dem kleineren Rest.</li>
 * <li><b>Albedo je Klasse:</b> das Mittel von g · d über alle Pixel der Klasse im Luftbild. Watt und Sand trägt das
 * Luftbild im Zustand des Flugs; trocken gilt A / (1 − 0,45 · n_Flug) mit n_Flug = 0,5 [G] und 0,45 wie
 * {@code mudflat.wetDarkening}.</li>
 * <li><b>Struktur:</b> je 1-m-Pixel S = Y(g · d) / Y(A der Klasse im Zustand des Flugs), auf 0,2 … 5 [G] begrenzt, NaN
 * ohne Luftbild oder Klasse. Y ist die Leuchtdichte (Rec. 709).</li>
 * </ol>
 * Ergebnis: {@code luftbild/1m} (Stufe wie {@code rauheit/1m}) und {@code luftbild/albedo.txt}.
 */
public final class Luftbild {
    /** Kantenlänge einer DOP-Kachel in Pixeln und Pixel je Meter. */
    static final int DOP_N = 5000, JE_M = 5, M = 1000;
    static final Pattern NAME = Pattern.compile("dop\\d+\\w*?_32_(\\d+)_(\\d+)_.*\\.tif", Pattern.CASE_INSENSITIVE);
    /** Beständige Klassen für die Eichung: Fenne, Salzwiese, Deich. */
    static final int[] BESTAENDIG = {3, 4, 5};
    static final double[] Y = {0.2126, 0.7152, 0.0722};

    public double saettigung = 0.25, nassFlug = 0.5, dunkel = 0.45, anteil = 0.9, sMin = 0.2, sMax = 5;

    final List<Path> dop;
    final Path tci, scl;
    /** Seit F44 A: die Bänder Rot, Grün, Blau (B04, B03, B02) mit Maßstab und Versatz; null: das TCI. */
    public Path[] baender;
    public double massstab = 1e-4, versatz = 0;
    final Gelaende.Stufe klassen;
    final String[] namen;

    /** Ergebnis. */
    public double[] gain = new double[3], rest = new double[3], r = new double[3];
    public boolean srgb;
    public int paare, kacheln;
    public double[][] albedo, albedoFlug;
    public long[] pixel;
    public String bericht = "";

    public Luftbild(List<Path> dop, Path tci, Path scl, Gelaende.Stufe klassen, String[] namen) {
        this.dop = dop;
        this.tci = tci;
        this.scl = scl;
        this.klassen = klassen;
        this.namen = namen;
    }

    /** Alle DOP-Kacheln in einem Ordner, nach Namen geordnet. */
    public static List<Path> kacheln(Path ordner) throws IOException {
        List<Path> l = new ArrayList<>();
        try (var s = Files.list(ordner)) {
            s.filter(p -> NAME.matcher(p.getFileName().toString()).matches()).sorted().forEach(l::add);
        }
        return l;
    }

    static double srgb(double v) { return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }

    /** Eine DOP-Kachel als 1-m-Mittel: [Fassung 0 linear, 1 sRGB][Kanal][Pixel, Zeilen von Nord]. */
    record Kachel(int ostKm, int nordKm, float[][][] d) { }

    Kachel mittel(Path p) throws IOException {
        Matcher m = NAME.matcher(p.getFileName().toString());
        if (!m.matches()) throw new IOException("kein DOP-Name: " + p);
        int e = Integer.parseInt(m.group(1)), n = Integer.parseInt(m.group(2));
        float[][][] d = new float[2][3][M * M];
        double[] lut = new double[256];
        for (int i = 0; i < 256; i++) lut[i] = srgb(i / 255.0);
        try (GeoTiff g = GeoTiff.oeffne(p)) {
            if (g.breite != DOP_N || g.hoehe != DOP_N || g.proben < 3) throw new IOException(p + ": " + g.breite + " × " + g.hoehe + " × " + g.proben + " statt 5000² × 4");
            if (Math.abs(g.x0 - e * 1000.0) > 0.01 || Math.abs(g.y0 - (n + 1) * 1000.0) > 0.01 || Math.abs(g.dx - 0.2) > 1e-9)
                throw new IOException(String.format(Locale.ROOT, "%s: Lage %.2f %.2f Schritt %.3f passt nicht zum Namen", p, g.x0, g.y0, g.dx));
            int streifen = 200 * JE_M;
            for (int y0 = 0; y0 < DOP_N; y0 += streifen) {
                for (int c = 0; c < 3; c++) {
                    float[] v = g.lies(0, y0, DOP_N, streifen, c);
                    for (int j = 0; j < streifen / JE_M; j++)
                        for (int i = 0; i < M; i++) {
                            double a = 0, b = 0;
                            for (int dy = 0; dy < JE_M; dy++)
                                for (int dx = 0; dx < JE_M; dx++) {
                                    float x = v[(j * JE_M + dy) * DOP_N + i * JE_M + dx];
                                    int q = Float.isNaN(x) ? 0 : (int) x;
                                    a += q / 255.0;
                                    b += lut[q];
                                }
                            int o = (y0 / JE_M + j) * M + i;
                            d[0][c][o] = (float) (a / (JE_M * JE_M));
                            d[1][c][o] = (float) (b / (JE_M * JE_M));
                        }
                }
            }
        }
        return new Kachel(e, n, d);
    }

    /** Klasse des 1-m-Pixels (i, j) einer Kachel (Zeilen von Nord), 0 ohne. */
    int klasse(Gelaende.Stufe.Leser l, int ostKm, int nordKm, int i, int j) {
        float v = l.imPixel(ostKm * 1000.0 + i + 0.5, (nordKm + 1) * 1000.0 - j - 0.5);
        return Float.isNaN(v) ? 0 : Math.round(v);
    }

    /** Rechnet alles und schreibt {@code ziel/1m} und {@code ziel/albedo.txt}. */
    public void bauen(File ziel) throws IOException {
        Map<Long, Kachel> alle = new TreeMap<>();
        for (Path p : dop) {
            Kachel k = mittel(p);
            alle.put(k.ostKm * 100_000L + k.nordKm, k);
        }
        kacheln = alle.size();
        Gelaende.Stufe.Leser kl = klassen.leser();
        // ---- Eichung gegen Sentinel-2
        double[][] sdd = new double[2][3], sds = new double[2][3], sss = new double[2][3];
        double[][][] sum = new double[2][3][3];          // für r: Σd, Σs (je Fassung, Kanal)
        GeoTiff[] bt = null;
        if (baender != null) {
            bt = new GeoTiff[3];
            for (int c = 0; c < 3; c++) bt[c] = GeoTiff.oeffne(baender[c]);
        }
        try (GeoTiff t = baender != null ? bt[0] : GeoTiff.oeffne(tci); GeoTiff s = GeoTiff.oeffne(scl)) {
            for (Kachel k : alle.values()) {
                double e0 = k.ostKm * 1000.0, n1 = (k.nordKm + 1) * 1000.0;
                int c0 = (int) Math.ceil((e0 - t.x0) / t.dx - 1e-9), r0 = (int) Math.ceil((n1 - t.y0) / t.dy - 1e-9);
                int c1 = (int) Math.floor((e0 + 1000 - t.x0) / t.dx + 1e-9), r1 = (int) Math.floor((n1 - 1000 - t.y0) / t.dy + 1e-9);
                int w = c1 - c0, h = r1 - r0;
                if (w <= 0 || h <= 0) continue;
                float[][] tv = new float[3][];
                for (int c = 0; c < 3; c++) tv[c] = bt != null ? bt[c].lies(c0, r0, w, h, 0) : t.lies(c0, r0, w, h, c);
                int sc0 = (int) Math.floor((e0 - s.x0) / s.dx), sr0 = (int) Math.floor((n1 - s.y0) / s.dy);
                int sw = (int) Math.ceil(1000 / s.dx) + 2, sh = (int) Math.ceil(1000 / -s.dy) + 2;
                float[] sv0 = s.lies(sc0, sr0, sw, sh, 0);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        double px = t.x0 + (c0 + x) * t.dx, py = t.y0 + (r0 + y) * t.dy;      // Nordwestecke
                        int i0 = (int) Math.round(px - e0), j0 = (int) Math.round(n1 - py);
                        int n10 = (int) Math.round(t.dx);
                        if (i0 < 0 || j0 < 0 || i0 + n10 > M || j0 + n10 > M) continue;
                        int[] zahl = new int[9];
                        for (int b = 0; b < n10; b++) for (int a = 0; a < n10; a++) zahl[klasse(kl, k.ostKm, k.nordKm, i0 + a, j0 + b)]++;
                        int best = 0;
                        for (int q : BESTAENDIG) if (zahl[q] > zahl[best]) best = q;
                        if (best == 0 || zahl[best] < anteil * n10 * n10) continue;
                        int sx = (int) Math.floor((px + t.dx / 2 - s.x0) / s.dx) - sc0, sy = (int) Math.floor((py + t.dy / 2 - s.y0) / s.dy) - sr0;
                        float sc = sx >= 0 && sy >= 0 && sx < sw && sy < sh ? sv0[sy * sw + sx] : Float.NaN;
                        if (sc != 4 && sc != 5) continue;
                        double[] sv = new double[3];
                        boolean gut = true;
                        for (int c = 0; c < 3; c++) {
                            float q = tv[c][y * w + x];
                            if (bt != null) {
                                sv[c] = q * massstab + versatz;
                                if (Float.isNaN(q) || q <= 0 || sv[c] <= 0 || sv[c] > 0.6) gut = false;
                            } else {
                                if (Float.isNaN(q) || q < 1 || q > 254) gut = false;
                                sv[c] = q / 255.0 * saettigung;
                            }
                        }
                        if (!gut) continue;
                        for (int f = 0; f < 2; f++)
                            for (int c = 0; c < 3; c++) {
                                double d = 0;
                                for (int b = 0; b < n10; b++) for (int a = 0; a < n10; a++) d += k.d[f][c][(j0 + b) * M + i0 + a];
                                d /= n10 * n10;
                                sdd[f][c] += d * d;
                                sds[f][c] += d * sv[c];
                                sss[f][c] += sv[c] * sv[c];
                                sum[f][c][0] += d;
                                sum[f][c][1] += sv[c];
                            }
                        paare++;
                    }
            }
        }
        if (bt != null) for (int c = 1; c < 3; c++) bt[c].close();
        if (paare < 100) throw new IOException("Eichung: nur " + paare + " Paare mit Sentinel-2");
        double[] restF = new double[2];
        double[][] g = new double[2][3], re = new double[2][3];
        for (int f = 0; f < 2; f++)
            for (int c = 0; c < 3; c++) {
                g[f][c] = sds[f][c] / sdd[f][c];
                // Rest relativ: Σ (s − g d)² / Σ s² = 1 − (Σ s d)² / (Σ d² Σ s²)
                re[f][c] = Math.sqrt(Math.max(0, 1 - sds[f][c] * sds[f][c] / (sdd[f][c] * sss[f][c])));
                restF[f] += re[f][c];
            }
        srgb = restF[1] < restF[0];
        int F = srgb ? 1 : 0;
        gain = g[F].clone();
        rest = re[F].clone();
        // Korrelation (mit Achsenabschnitt) über die Summen ist hier nicht genau ohne Σd²-Mittelwerte; Rest genügt
        bericht = String.format(Locale.GERMAN, (baender != null ? "Bänder B04, B03, B02; " : "TCI; ") + "%d Paare; d linear: g = %.3f / %.3f / %.3f, Rest %.1f / %.1f / %.1f %%; d sRGB: g = %.3f / %.3f / %.3f, Rest %.1f / %.1f / %.1f %%; gilt %s",
                paare, g[0][0], g[0][1], g[0][2], re[0][0] * 100, re[0][1] * 100, re[0][2] * 100, g[1][0], g[1][1], g[1][2], re[1][0] * 100, re[1][1] * 100, re[1][2] * 100,
                srgb ? "sRGB" : "linear");
        // ---- Albedo je Klasse
        int nk = namen.length;
        double[][] summe = new double[nk][3];
        pixel = new long[nk];
        for (Kachel k : alle.values())
            for (int j = 0; j < M; j++)
                for (int i = 0; i < M; i++) {
                    int q = klasse(kl, k.ostKm, k.nordKm, i, j);
                    if (q <= 0 || q >= nk) continue;
                    pixel[q]++;
                    for (int c = 0; c < 3; c++) summe[q][c] += gain[c] * k.d[F][c][j * M + i];
                }
        albedo = new double[nk][3];
        albedoFlug = new double[nk][3];
        for (int q = 1; q < nk; q++)
            for (int c = 0; c < 3; c++) {
                albedoFlug[q][c] = pixel[q] > 0 ? summe[q][c] / pixel[q] : Double.NaN;
                albedo[q][c] = q == 1 || q == 2 ? albedoFlug[q][c] / (1 - dunkel * nassFlug) : albedoFlug[q][c];
            }
        // ---- Struktur
        File stufe = new File(ziel, "1m");
        Gelaende.Stufe aus = Gelaende.Stufe.anlegen(stufe, klassen.zelle, klassen.kachelZellen, klassen.suedwestOst, klassen.suedwestNord, klassen.kachelnOst,
                klassen.kachelnNord, "Struktur aus DOP20: Leuchtdichte relativ zum Mittel der Klasse im Zustand des Flugs (Werkbuch HalligHooge Teil A, 10.4, Schritt luftbild; F41)");
        for (Kachel k : alle.values()) {
            int tx = (int) Math.round((k.ostKm * 1000.0 - klassen.suedwestOst) / klassen.kachel());
            int ty = (int) Math.round((klassen.suedwestNord + klassen.kachelnNord * klassen.kachel() - (k.nordKm + 1) * 1000.0) / klassen.kachel());
            float[] s = new float[M * M];
            for (int j = 0; j < M; j++)
                for (int i = 0; i < M; i++) {
                    int q = klasse(kl, k.ostKm, k.nordKm, i, j);
                    if (q <= 0 || q >= nk || pixel[q] == 0) { s[j * M + i] = Float.NaN; continue; }
                    double y = 0, yk = 0;
                    for (int c = 0; c < 3; c++) {
                        y += Y[c] * gain[c] * k.d[F][c][j * M + i];
                        yk += Y[c] * albedoFlug[q][c];
                    }
                    s[j * M + i] = (float) Math.max(sMin, Math.min(sMax, y / yk));
                }
            aus.schreibe(tx, ty, s);
        }
        // ---- Tafel
        StringBuilder b = new StringBuilder();
        b.append("# Albedo je Rauheitsklasse aus DOP20, geeicht gegen Sentinel-2 L2A (Werkbuch HalligHooge Teil A, 10.4, Schritt luftbild; F41)\n");
        b.append("# ").append(bericht).append('\n');
        b.append(baender != null ? String.format(Locale.ROOT, "# Sentinel-2: Bänder B04, B03, B02, Reflexion = Wert · %s + %s", massstab, versatz)
                : String.format(Locale.ROOT, "# Sentinel-2: TCI / 255 · %.2f", saettigung));
        b.append(String.format(Locale.ROOT, "; Watt und Sand trocken: A / (1 − %.2f · %.2f)%n", dunkel, nassFlug));
        b.append("# Klasse, Albedo R G B (linear, trocken), Albedo R G B im Zustand des Flugs, Pixel (1 m), Name\n");
        for (int q = 1; q < nk; q++)
            b.append(String.format(Locale.ROOT, "%d %.4f %.4f %.4f %.4f %.4f %.4f %d %s%n", q, albedo[q][0], albedo[q][1], albedo[q][2], albedoFlug[q][0], albedoFlug[q][1],
                    albedoFlug[q][2], pixel[q], namen[q]));
        Files.writeString(new File(ziel, "albedo.txt").toPath(), b.toString(), StandardCharsets.UTF_8);
    }

    /** Die Namen der Klassen aus {@code klassen.txt} (Index = Nummer). */
    public static String[] namen(Path klassen) throws IOException {
        List<String> z = Files.readAllLines(klassen, StandardCharsets.UTF_8);
        String[] n = new String[9];
        java.util.Arrays.fill(n, "");
        for (String s : z) {
            s = s.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] t = s.split("\\s+", 3);
            int q = Integer.parseInt(t[0]);
            if (q > 0 && q < n.length) n[q] = t.length > 2 ? t[2] : "";
        }
        return n;
    }

    /** Liest {@code albedo.txt}: [Klasse][R, G, B] trocken; fehlende Klassen NaN. */
    public static double[][] albedo(Path datei) throws IOException {
        double[][] a = new double[9][3];
        for (double[] x : a) java.util.Arrays.fill(x, Double.NaN);
        for (String s : Files.readAllLines(datei, StandardCharsets.UTF_8)) {
            s = s.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] t = s.split("\\s+");
            int q = Integer.parseInt(t[0]);
            for (int c = 0; c < 3; c++) a[q][c] = Double.parseDouble(t[1 + c]);
        }
        return a;
    }
}
