import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Backt ein Sand-Asset (glTF 2.0 als Zip, z. B. {@code sand_dunes_gltf.zip} aus Fab) zu den Karten von
 * {@code com.dan.beach}: dem Relief und dem Korn, beide kachelbar, ohne den großen Hügel, auf mittlere Abweichung 1 gebracht.
 * <ol>
 * <li>Alle Dreiecke des Netzes (Höhe in der dritten Achse) werden auf ein Raster über das mittlere Quadrat von 4/3 ·
 * {@code kachel} m gelegt (am Rand flacht das Asset ab), mit Höhe und Texturkoordinate je Zelle.</li>
 * <li>Relief: Höhe minus ihr Mittel über 1,2 m (Gauß), so bleiben Mulden, Kämme und Rinnen, der Hügel geht.</li>
 * <li>Korn: Helligkeit der Grundfarbe an der Texturkoordinate, geteilt durch ihr Mittel über 15 cm.</li>
 * <li>Kachelbar durch Überlappen: ein Viertel am Rand wird mit der Gegenseite überblendet, so wird aus dem Raster eine
 * Kachel von {@code kachel} m zu {@code n} Zellen, mit erhaltener Varianz.</li>
 * </ol>
 * Schreibt {@code relief.png} und {@code grain.png} (16 Bit grau, Wert = 0,5 + v / 16) und {@code sand.txt} (Herkunft,
 * Maße, Abweichung in m). Aufruf: {@code java tools/beach/SandAsset.java asset.zip src/com/dan/beach [kachel] [n]}.
 */
public final class SandAsset {
    public static void main(String[] a) throws Exception {
        if (a.length < 2) { System.err.println("Aufruf: java tools/beach/SandAsset.java asset.zip ausgabe/ [kachel_m=7] [n=1024]"); System.exit(2); }
        File zip = new File(a[0]), out = new File(a[1]);
        double kachel = a.length > 2 ? Double.parseDouble(a[2]) : 7;
        int n = a.length > 3 ? Integer.parseInt(a[3]) : 1024;
        Map<String, byte[]> files = new HashMap<>();
        try (ZipFile z = new ZipFile(zip)) {
            for (ZipEntry e : java.util.Collections.list(z.entries())) {
                if (e.isDirectory()) continue;
                try (InputStream in = z.getInputStream(e)) { files.put(e.getName(), in.readAllBytes()); }
            }
        }
        String gltfName = files.keySet().stream().filter(s -> s.endsWith(".gltf")).findFirst().orElseThrow(() -> new IllegalStateException("kein .gltf im Zip"));
        String base = gltfName.contains("/") ? gltfName.substring(0, gltfName.lastIndexOf('/') + 1) : "";
        @SuppressWarnings("unchecked") Map<String, Object> g = (Map<String, Object>) new Json(new String(files.get(gltfName), StandardCharsets.UTF_8)).value();
        List<Object> buffers = list(g.get("buffers")), views = list(g.get("bufferViews")), accs = list(g.get("accessors"));
        byte[] bin = files.get(base + str(map(buffers.get(0)).get("uri")));
        // Grundfarbe aus dem Material
        BufferedImage color = null;
        List<Object> mats = list(g.get("materials")), texs = list(g.get("textures")), imgs = list(g.get("images"));
        if (mats != null && !mats.isEmpty()) {
            Map<String, Object> pbr = map(map(mats.get(0)).get("pbrMetallicRoughness"));
            if (pbr != null && pbr.get("baseColorTexture") != null) {
                int ti = num(map(pbr.get("baseColorTexture")).get("index"));
                int ii = num(map(texs.get(ti)).get("source"));
                color = ImageIO.read(new ByteArrayInputStream(files.get(base + str(map(imgs.get(ii)).get("uri")))));
            }
        }
        // Raster: Höhe, u, v je Zelle über [−kachel/2, kachel/2]²
        // gerastert wird eine größere Fläche (n Zellen), die Überlappung macht daraus die Kachel (nk Zellen, kachel m)
        int nk = n;
        n = (int) Math.round(nk * 4.0 / 3);
        double cell = kachel / nk, x0 = -cell * n / 2;
        float[] h = new float[n * n], tu = new float[n * n], tv = new float[n * n];
        java.util.Arrays.fill(h, Float.NaN);
        long tris = 0;
        for (Object mo : list(g.get("meshes")))
            for (Object po : list(map(mo).get("primitives"))) {
                Map<String, Object> p = map(po), at = map(p.get("attributes"));
                float[] pos = floats(accs, views, bin, num(at.get("POSITION")));
                float[] uv = at.get("TEXCOORD_0") != null ? floats(accs, views, bin, num(at.get("TEXCOORD_0"))) : null;
                int[] idx = ints(accs, views, bin, num(p.get("indices")));
                for (int t = 0; t + 2 < idx.length; t += 3) {
                    int i0 = idx[t], i1 = idx[t + 1], i2 = idx[t + 2];
                    double ax = pos[i0 * 3], ay = pos[i0 * 3 + 1], bx = pos[i1 * 3], by = pos[i1 * 3 + 1], cx = pos[i2 * 3], cy = pos[i2 * 3 + 1];
                    double det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
                    if (Math.abs(det) < 1e-14) continue;
                    int ia = (int) Math.floor((Math.min(ax, Math.min(bx, cx)) - x0) / cell - 0.5), ib = (int) Math.ceil((Math.max(ax, Math.max(bx, cx)) - x0) / cell - 0.5);
                    int ja = (int) Math.floor((Math.min(ay, Math.min(by, cy)) - x0) / cell - 0.5), jb = (int) Math.ceil((Math.max(ay, Math.max(by, cy)) - x0) / cell - 0.5);
                    for (int j = Math.max(0, ja); j <= Math.min(n - 1, jb); j++)
                        for (int i = Math.max(0, ia); i <= Math.min(n - 1, ib); i++) {
                            double px = x0 + (i + 0.5) * cell, py = x0 + (j + 0.5) * cell;
                            double l0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / det, l1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / det, l2 = 1 - l0 - l1;
                            if (l0 < -1e-6 || l1 < -1e-6 || l2 < -1e-6) continue;
                            int k = j * n + i;
                            h[k] = (float) (l0 * pos[i0 * 3 + 2] + l1 * pos[i1 * 3 + 2] + l2 * pos[i2 * 3 + 2]);
                            if (uv != null) {
                                tu[k] = (float) (l0 * uv[i0 * 2] + l1 * uv[i1 * 2] + l2 * uv[i2 * 2]);
                                tv[k] = (float) (l0 * uv[i0 * 2 + 1] + l1 * uv[i1 * 2 + 1] + l2 * uv[i2 * 2 + 1]);
                            }
                        }
                    tris++;
                }
            }
        int leer = fill(h, n);
        fill(tu, n);
        fill(tv, n);
        // Relief ohne den Hügel
        float[] trend = blur(h, n, 1.2 / cell);
        float[] r = new float[n * n];
        for (int k = 0; k < r.length; k++) r[k] = h[k] - trend[k];
        double rms = rms(r);
        // Korn aus der Grundfarbe
        float[] gr = new float[n * n];
        if (color != null) {
            for (int k = 0; k < gr.length; k++) gr[k] = (float) luminance(color, tu[k], tv[k]);
            float[] m = blur(gr, n, 0.15 / cell);
            for (int k = 0; k < gr.length; k++) gr[k] = gr[k] / Math.max(1e-4f, m[k]) - 1;
        }
        double grms = rms(gr);
        float[] rt = tile(r, n, nk), gt = tile(gr, n, nk);
        scale(rt, 1 / Math.max(1e-9, rms(rt)));
        if (grms > 0) scale(gt, 1 / Math.max(1e-9, rms(gt)));
        out.mkdirs();
        write16(rt, nk, new File(out, "relief.png"));
        write16(gt, nk, new File(out, "grain.png"));
        String info = String.format(Locale.ROOT, """
                # com.dan.beach: Karten aus einem Sand-Asset (tools/beach/SandAsset.java)
                quelle=%s
                generator=%s
                kachel_m=%.3f
                n=%d
                relief_rms_m=%.5f
                grain_rms=%.5f
                dreiecke=%d
                leere_zellen=%d
                kodierung=wert = (grau16 / 65535 - 0.5) * 16
                """, zip.getName(), str(map(g.get("asset")).get("generator")), kachel, nk, rms, grms, tris, leer);
        Files.writeString(new File(out, "sand.txt").toPath(), info, StandardCharsets.UTF_8);
        System.out.print(info);
    }

    // ------------------------------------------------------------------ Raster

    /** Lücken mit dem Mittel der Nachbarn füllen (wiederholt, bis keine mehr da sind); gibt die Zahl der Lücken zurück. */
    static int fill(float[] a, int n) {
        int leer = 0;
        for (float v : a) if (Float.isNaN(v)) leer++;
        for (int pass = 0; pass < 4 * n; pass++) {
            boolean any = false;
            float[] b = a.clone();
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    int k = j * n + i;
                    if (!Float.isNaN(a[k])) continue;
                    double s = 0;
                    int c = 0;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int ii = i + d[0], jj = j + d[1];
                        if (ii < 0 || jj < 0 || ii >= n || jj >= n || Float.isNaN(a[jj * n + ii])) continue;
                        s += a[jj * n + ii];
                        c++;
                    }
                    if (c > 0) b[k] = (float) (s / c); else any = true;
                }
            System.arraycopy(b, 0, a, 0, a.length);
            if (!any) break;
        }
        return leer;
    }

    /** Gauß-ähnlich: dreimal Kastenfilter (Halbmesser aus sigma), am Rand gespiegelt. */
    static float[] blur(float[] a, int n, double sigma) {
        int r = Math.max(1, (int) Math.round(Math.sqrt(12 * sigma * sigma / 3 + 1) / 2));
        float[] b = a.clone();
        for (int pass = 0; pass < 3; pass++) { b = box(b, n, r, true); b = box(b, n, r, false); }
        return b;
    }

    static float[] box(float[] a, int n, int r, boolean x) {
        float[] o = new float[a.length];
        for (int line = 0; line < n; line++) {
            double s = 0;
            for (int t = -r; t <= r; t++) s += at(a, n, line, t, x);
            for (int t = 0; t < n; t++) {
                o[x ? line * n + t : t * n + line] = (float) (s / (2 * r + 1));
                s += at(a, n, line, t + r + 1, x) - at(a, n, line, t - r, x);
            }
        }
        return o;
    }

    static float at(float[] a, int n, int line, int t, boolean x) {
        if (t < 0) t = -t - 1;
        if (t >= n) t = 2 * n - t - 1;
        t = Math.max(0, Math.min(n - 1, t));
        return x ? a[line * n + t] : a[t * n + line];
    }

    /**
     * Kachelbar durch Überlappen: aus dem Raster der Seite m wird eine Kachel der Seite n (m = 4n/3); der Streifen der
     * Breite m − n am Anfang jeder Achse wird mit dem Streifen dahinter überblendet (Gewicht nach sin², Varianz erhalten),
     * so schließt das Ende der Kachel an ihren Anfang an. Je Achse nacheinander.
     */
    static float[] tile(float[] a, int m, int n) {
        double mean = 0;
        for (float v : a) mean += v;
        mean /= a.length;
        int o = m - n;
        float[] x = new float[n * m];   // erst in x: n Spalten, m Zeilen
        for (int j = 0; j < m; j++)
            for (int i = 0; i < n; i++) x[j * n + i] = blend(a[j * m + i] - mean, i < o ? a[j * m + i + n] - mean : 0, i, o);
        float[] y = new float[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) y[j * n + i] = blend(x[j * n + i], j < o ? x[(j + n) * n + i] : 0, j, o);
        return y;
    }

    /** Im Streifen (t < o) vom Stück hinter der Kachel (q) zum Stück am Anfang (p), sonst p. */
    static float blend(double p, double q, int t, int o) {
        if (t >= o) return (float) p;
        double s = Math.sin(Math.PI / 2 * (t + 0.5) / o), w = s * s;
        return (float) ((w * p + (1 - w) * q) / Math.sqrt(w * w + (1 - w) * (1 - w)));
    }

    static double rms(float[] a) {
        double m = 0, s = 0;
        for (float v : a) m += v;
        m /= a.length;
        for (float v : a) s += (v - m) * (v - m);
        return Math.sqrt(s / a.length);
    }

    static void scale(float[] a, double k) {
        double m = 0;
        for (float v : a) m += v;
        m /= a.length;
        for (int i = 0; i < a.length; i++) a[i] = (float) ((a[i] - m) * k);
    }

    static double luminance(BufferedImage im, double u, double v) {
        double x = (u - Math.floor(u)) * im.getWidth() - 0.5, y = (v - Math.floor(v)) * im.getHeight() - 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0, s = 0;
        for (int dy = 0; dy <= 1; dy++)
            for (int dx = 0; dx <= 1; dx++) {
                int xx = Math.floorMod(x0 + dx, im.getWidth()), yy = Math.floorMod(y0 + dy, im.getHeight());
                int c = im.getRGB(xx, yy);
                double l = 0.2126 * lin((c >> 16) & 255) + 0.7152 * lin((c >> 8) & 255) + 0.0722 * lin(c & 255);
                s += l * (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy);
            }
        return s;
    }

    static double lin(int c) { double v = c / 255.0; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }

    static void write16(float[] a, int n, File f) throws IOException {
        BufferedImage im = new BufferedImage(n, n, BufferedImage.TYPE_USHORT_GRAY);
        WritableRaster w = im.getRaster();
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) w.setSample(i, j, 0, (int) Math.round(Math.max(0, Math.min(1, 0.5 + a[j * n + i] / 16)) * 65535));
        ImageIO.write(im, "png", f);
    }

    // ------------------------------------------------------------------ glTF

    static float[] floats(List<Object> accs, List<Object> views, byte[] bin, int ai) {
        Map<String, Object> a = map(accs.get(ai));
        int comps = switch (str(a.get("type"))) { case "SCALAR" -> 1; case "VEC2" -> 2; case "VEC3" -> 3; default -> 4; };
        int count = num(a.get("count"));
        Map<String, Object> v = map(views.get(num(a.get("bufferView"))));
        int off = numOr(v.get("byteOffset"), 0) + numOr(a.get("byteOffset"), 0), stride = numOr(v.get("byteStride"), comps * 4);
        ByteBuffer b = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        float[] o = new float[count * comps];
        for (int i = 0; i < count; i++) for (int c = 0; c < comps; c++) o[i * comps + c] = b.getFloat(off + i * stride + c * 4);
        return o;
    }

    static int[] ints(List<Object> accs, List<Object> views, byte[] bin, int ai) {
        Map<String, Object> a = map(accs.get(ai));
        int count = num(a.get("count")), ct = num(a.get("componentType"));
        Map<String, Object> v = map(views.get(num(a.get("bufferView"))));
        int off = numOr(v.get("byteOffset"), 0) + numOr(a.get("byteOffset"), 0);
        ByteBuffer b = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        int[] o = new int[count];
        for (int i = 0; i < count; i++)
            o[i] = ct == 5125 ? b.getInt(off + i * 4) : ct == 5123 ? b.getShort(off + i * 2) & 0xFFFF : b.get(off + i) & 0xFF;
        return o;
    }

    @SuppressWarnings("unchecked") static Map<String, Object> map(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") static List<Object> list(Object o) { return (List<Object>) o; }
    static String str(Object o) { return o == null ? "" : o.toString(); }
    static int num(Object o) { return ((Number) o).intValue(); }
    static int numOr(Object o, int d) { return o == null ? d : ((Number) o).intValue(); }

    /** Kleiner JSON-Leser (Objekte, Listen, Zahlen, Texte, true/false/null). */
    static final class Json {
        final String s;
        int p;

        Json(String s) { this.s = s; }

        Object value() {
            ws();
            char c = s.charAt(p);
            if (c == '{') {
                p++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(p) == '}') { p++; return m; }
                while (true) {
                    ws();
                    String k = (String) value();
                    ws(); p++;   // :
                    m.put(k, value());
                    ws();
                    if (s.charAt(p++) == '}') return m;
                }
            }
            if (c == '[') {
                p++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(p) == ']') { p++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(p++) == ']') return l;
                }
            }
            if (c == '"') {
                StringBuilder b = new StringBuilder();
                p++;
                while (s.charAt(p) != '"') {
                    char d = s.charAt(p++);
                    if (d == '\\') {
                        char e = s.charAt(p++);
                        switch (e) {
                            case 'n' -> b.append('\n');
                            case 't' -> b.append('\t');
                            case 'u' -> { b.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; }
                            default -> b.append(e);
                        }
                    } else b.append(d);
                }
                p++;
                return b.toString();
            }
            if (s.startsWith("true", p)) { p += 4; return Boolean.TRUE; }
            if (s.startsWith("false", p)) { p += 5; return Boolean.FALSE; }
            if (s.startsWith("null", p)) { p += 4; return null; }
            int a = p;
            while (p < s.length() && "+-0123456789.eE".indexOf(s.charAt(p)) >= 0) p++;
            return Double.parseDouble(s.substring(a, p));
        }

        void ws() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
    }

    private SandAsset() { }
}
