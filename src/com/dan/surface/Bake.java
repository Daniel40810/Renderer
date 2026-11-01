package com.dan.surface;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Das Backwerkzeug (Werkbuch 5.10, Verallgemeinerung von {@code tools/beach/SandAsset.java}): macht aus einem
 * PBR-Satz (Zip oder Ordner; Namen wie bei ambientCG {@code _Color}, {@code _NormalGL}/{@code _NormalDX},
 * {@code _Roughness}, {@code _AmbientOcclusion}, {@code _Displacement}, oder wie bei Poly Haven {@code diff},
 * {@code nor_gl}, {@code rough}, {@code ao}, {@code disp}) drei Karten in gleicher Größe:
 * <ul>
 * <li>{@code a.png}: Farbe (sRGB, getönt nach {@link Material#tint}) und im Alphakanal die Höhe (0 … 1),</li>
 * <li>{@code n.png}: Normale x und y (0,5 + n/2) in Rot und Grün, OpenGL-Richtung (y nach oben im Bild),</li>
 * <li>{@code r.png}: Rauheit in Rot, Verdeckung in Grün,</li>
 * </ul>
 * dazu {@code material.txt} mit Herkunft, Lizenz, Maßen und den Mitteln (Farbe linear, Rauheit, Höhe), die die
 * Grafikseite für die Ferne braucht. Fehlende Karten: Verdeckung 1, Höhe aus der Helligkeit, Rauheit 0,8 [G].
 * Abgeleitete Materialien ({@link Material#derivedFrom}) nehmen den gebackenen Ordner des Ausgangs und tönen nur die Farbe.
 */
public final class Bake {
    private Bake() { }

    /** Ergebnis: Kantenlänge, Mittel der Farbe (linear), der Rauheit und der Höhe, Abweichung der Höhe. */
    public record Result(int size, double[] meanColor, double meanRough, double meanHeight, double sdHeight, String sources) { }

    /** Backt den Satz in src (Zip oder Ordner) für Material m nach out (Ordner), Kantenlänge n (0 = wie die Quelle). */
    public static Result bake(Material m, File src, File out, int n) throws IOException {
        Map<String, byte[]> files = read(src);
        BufferedImage color = image(files, "_color", "basecolor", "base_color", "albedo", "_diff", "diffuse", "_col");
        if (color == null) throw new IOException("keine Farbkarte in " + src.getName() + " (" + String.join(", ", files.keySet()) + ")");
        BufferedImage normal = image(files, "_normalgl", "nor_gl", "normal_gl");
        boolean flipY = false;
        if (normal == null) {
            normal = image(files, "_normaldx", "nor_dx", "normal_dx", "_normal", "normal");
            flipY = normal != null && names(files, "_normaldx", "nor_dx", "normal_dx") != null;
        }
        BufferedImage rough = image(files, "_roughness", "rough");
        BufferedImage ao = image(files, "_ambientocclusion", "_ao_", "_ao.");
        BufferedImage disp = image(files, "_displacement", "disp", "height");
        int size = n > 0 ? n : color.getWidth();
        float[][] c = rgb(color, size);
        tint(c, m);
        float[] h = disp != null ? gray(disp, size) : luminance(c);
        float[][] nn = normal != null ? raw(normal, size) : null;
        float[] r = rough != null ? gray(rough, size) : filled(size, 0.8f);
        float[] o = ao != null ? gray(ao, size) : filled(size, 1f);
        return write(m, out, size, c, h, nn, flipY, r, o, String.format(Locale.ROOT, "%s (Farbe%s%s%s%s)", src.getName(), normal != null ? (flipY ? ", Normale DX→GL" : ", Normale") : "",
                rough != null ? ", Rauheit" : "", ao != null ? ", Verdeckung" : "", disp != null ? ", Höhe" : ""));
    }

    /** Leitet m aus dem gebackenen Ordner from ab (nur die Farbe wird getönt). */
    public static Result derive(Material m, File from, File out) throws IOException {
        BufferedImage a = ImageIO.read(new File(from, "a.png")), nI = ImageIO.read(new File(from, "n.png")), rI = ImageIO.read(new File(from, "r.png"));
        int size = a.getWidth();
        float[][] c = rgb(a, size);
        tint(c, m);
        float[] h = channel(a, 3), r = channel(rI, 0), o = channel(rI, 1);
        float[][] nn = raw(nI, size);
        return write(m, out, size, c, h, nn, false, r, o, "abgeleitet aus " + from.getName());
    }

    static Result write(Material m, File out, int size, float[][] c, float[] h, float[][] nn, boolean flipY, float[] r, float[] o, String sources) throws IOException {
        out.mkdirs();
        int px = size * size;
        BufferedImage a = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        BufferedImage ni = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BufferedImage ri = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        double[] mean = new double[3];
        double mr = 0, mh = 0, mh2 = 0;
        for (int i = 0; i < px; i++) {
            int x = i % size, y = i / size;
            int cr = srgb8(c[0][i]), cg = srgb8(c[1][i]), cb = srgb8(c[2][i]), ch = unit8(h[i]);
            a.setRGB(x, y, (ch << 24) | (cr << 16) | (cg << 8) | cb);
            double nx = 0, ny = 0;
            if (nn != null) {
                nx = nn[0][i] * 2 - 1;
                ny = nn[1][i] * 2 - 1;
                if (flipY) ny = -ny;
                // Länge auf höchstens 1 (für die Rekonstruktion z = √(1 − x² − y²))
                double l = Math.hypot(nx, ny);
                if (l > 0.999) { nx *= 0.999 / l; ny *= 0.999 / l; }
            }
            ni.setRGB(x, y, (unit8(0.5 + nx / 2) << 16) | (unit8(0.5 + ny / 2) << 8));
            ri.setRGB(x, y, (unit8(r[i]) << 16) | (unit8(o[i]) << 8));
            for (int k = 0; k < 3; k++) mean[k] += c[k][i];
            mr += r[i];
            mh += h[i];
            mh2 += h[i] * h[i];
        }
        for (int k = 0; k < 3; k++) mean[k] /= px;
        mr /= px;
        mh /= px;
        double sd = Math.sqrt(Math.max(0, mh2 / px - mh * mh));
        ImageIO.write(a, "png", new File(out, "a.png"));
        ImageIO.write(ni, "png", new File(out, "n.png"));
        ImageIO.write(ri, "png", new File(out, "r.png"));
        StringBuilder t = new StringBuilder("# com.dan.surface, gebacken " + java.time.LocalDate.now() + "\n");
        t.append("id=").append(m.id).append('\n');
        t.append("name=").append(m.name).append('\n');
        t.append(String.format(Locale.ROOT, "groesse_m=%s\nkante=%d\ndrehen=%b\ndreiseitig=%b\nhoehe_m=%s\nnaesse=%s\n", m.size, size, m.rotate, m.triplanar, m.heightScale, m.wetDarken));
        t.append(String.format(Locale.ROOT, "farbe_mittel=%.5f %.5f %.5f\nrauheit_mittel=%.4f\nhoehe_mittel=%.4f\nhoehe_abweichung=%.4f\n", mean[0], mean[1], mean[2], mr, mh, sd));
        t.append("quelle=").append(m.derivedFrom != null ? "abgeleitet aus " + m.derivedFrom : m.provider + " " + m.assetId).append('\n');
        t.append("adresse=").append(m.url == null ? "" : m.url).append('\n');
        t.append("lizenz=").append(m.license == null ? "" : m.license).append('\n');
        t.append("karten=").append(sources).append('\n');
        if (m.tinted()) t.append(String.format(Locale.ROOT, "toenung=%.3f %.3f %.3f, entsaettigt %.2f, aufgehellt %.2f [G]\n", m.tint[0], m.tint[1], m.tint[2], m.desaturate, m.brighten));
        Files.writeString(new File(out, "material.txt").toPath(), t.toString(), StandardCharsets.UTF_8);
        return new Result(size, mean, mr, mh, sd, sources);
    }

    // ------------------------------------------------------------------ Lesen

    static Map<String, byte[]> read(File src) throws IOException {
        Map<String, byte[]> m = new LinkedHashMap<>();
        if (src.isDirectory()) {
            File[] f = src.listFiles();
            if (f != null) for (File x : f) if (x.isFile()) m.put(x.getName(), Files.readAllBytes(x.toPath()));
            return m;
        }
        try (ZipFile z = new ZipFile(src)) {
            for (ZipEntry e : java.util.Collections.list(z.entries())) {
                if (e.isDirectory()) continue;
                String nm = e.getName().substring(e.getName().lastIndexOf('/') + 1);
                String low = nm.toLowerCase(Locale.ROOT);
                if (!(low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png"))) continue;
                try (InputStream in = z.getInputStream(e)) { m.put(nm, in.readAllBytes()); }
            }
        }
        return m;
    }

    static String names(Map<String, byte[]> files, String... keys) {
        for (String k : keys)
            for (String n : files.keySet()) {
                String low = n.toLowerCase(Locale.ROOT);
                if (low.contains(k) && !low.contains("preview")) return n;
            }
        return null;
    }

    static BufferedImage image(Map<String, byte[]> files, String... keys) throws IOException {
        String n = names(files, keys);
        if (n == null) return null;
        BufferedImage b = ImageIO.read(new ByteArrayInputStream(files.get(n)));
        if (b == null) throw new IOException(n + ": Bildformat nicht lesbar");
        return b;
    }

    /** Farbe einer Karte als linear 0 … 1 je Kanal, auf size² gemittelt (Fläche) oder bilinear vergrößert. */
    static float[][] rgb(BufferedImage im, int size) {
        float[][] o = new float[3][size * size];
        sample(im, size, (x, y, argb) -> {
            int i = y * size + x;
            o[0][i] = (float) lin((argb >> 16) & 255);
            o[1][i] = (float) lin((argb >> 8) & 255);
            o[2][i] = (float) lin(argb & 255);
        }, true);
        return o;
    }

    /** Drei Kanäle 0 … 1 ohne sRGB-Umrechnung (Normalen sind linear gespeichert). */
    static float[][] raw(BufferedImage im, int size) {
        float[][] o = new float[3][size * size];
        sample(im, size, (x, y, argb) -> {
            int i = y * size + x;
            o[0][i] = ((argb >> 16) & 255) / 255f;
            o[1][i] = ((argb >> 8) & 255) / 255f;
            o[2][i] = (argb & 255) / 255f;
        }, false);
        return o;
    }

    /** Ein Grauwert 0 … 1 (ohne sRGB-Umrechnung: Rauheit, Verdeckung und Höhe sind linear gespeichert). */
    static float[] gray(BufferedImage im, int size) {
        float[] o = new float[size * size];
        sample(im, size, (x, y, argb) -> o[y * size + x] = ((argb >> 16) & 255) / 255f, false);
        return o;
    }

    static float[] channel(BufferedImage im, int ch) {
        int s = im.getWidth();
        float[] o = new float[s * s];
        int shift = ch == 3 ? 24 : 16 - ch * 8;
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) o[y * s + x] = ((im.getRGB(x, y) >>> shift) & 255) / 255f;
        return o;
    }

    interface Px { void set(int x, int y, int argb); }

    /** Bild auf size² bringen: verkleinert als Mittel über die Fläche (gekachelt, die Ränder laufen um), sonst nächster Punkt. */
    static void sample(BufferedImage im, int size, Px out, boolean srgb) {
        int w = im.getWidth(), h = im.getHeight();
        if (w == size && h == size) {
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) out.set(x, y, im.getRGB(x, y));
            return;
        }
        int fx = Math.max(1, w / size), fy = Math.max(1, h / size);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int sx = (int) ((long) x * w / size), sy = (int) ((long) y * h / size);
                double r = 0, g = 0, b = 0, a = 0;
                for (int j = 0; j < fy; j++)
                    for (int i = 0; i < fx; i++) {
                        int p = im.getRGB((sx + i) % w, (sy + j) % h);
                        double cr = (p >> 16) & 255, cg = (p >> 8) & 255, cb = p & 255;
                        if (srgb) { cr = lin((int) cr); cg = lin((int) cg); cb = lin((int) cb); }
                        r += cr; g += cg; b += cb; a += (p >>> 24);
                    }
                int k = fx * fy;
                r /= k; g /= k; b /= k; a /= k;
                if (srgb) { r = srgb8(r); g = srgb8(g); b = srgb8(b); }
                out.set(x, y, ((int) Math.round(a) << 24) | ((int) Math.round(r) << 16) | ((int) Math.round(g) << 8) | (int) Math.round(b));
            }
    }

    static void tint(float[][] c, Material m) {
        if (!m.tinted()) return;
        for (int i = 0; i < c[0].length; i++) {
            double r = c[0][i] * m.tint[0], g = c[1][i] * m.tint[1], b = c[2][i] * m.tint[2];
            double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            r = (r + (l - r) * m.desaturate) * m.brighten;
            g = (g + (l - g) * m.desaturate) * m.brighten;
            b = (b + (l - b) * m.desaturate) * m.brighten;
            c[0][i] = (float) Math.min(1, r);
            c[1][i] = (float) Math.min(1, g);
            c[2][i] = (float) Math.min(1, b);
        }
    }

    static float[] luminance(float[][] c) {
        float[] o = new float[c[0].length];
        for (int i = 0; i < o.length; i++) o[i] = (float) Math.min(1, Math.sqrt(0.2126 * c[0][i] + 0.7152 * c[1][i] + 0.0722 * c[2][i]));
        return o;
    }

    static float[] filled(int size, float v) {
        float[] o = new float[size * size];
        java.util.Arrays.fill(o, v);
        return o;
    }

    static double lin(int c) { double v = c / 255.0; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }

    static int srgb8(double v) {
        v = Math.max(0, Math.min(1, v));
        double s = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(s * 255);
    }

    static int unit8(double v) { return (int) Math.round(Math.max(0, Math.min(1, v)) * 255); }
}
