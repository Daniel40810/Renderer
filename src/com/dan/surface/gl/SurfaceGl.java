package com.dan.surface.gl;

import com.dan.surface.MaterialSet;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Grafikseite der Materialien: drei Texturfelder (Farbe+Höhe, Normale, Rauheit+Verdeckung) mit einer Schicht je
 * Material und allen Mip-Stufen, dazu die Werte des Bausteins {@code surface.glsl}.
 * <p>
 * <b>Packen (Entscheidung LituyaBay1958 Phase 5: der Treiber packt, das Ergebnis wird gemerkt):</b> Farbe+Höhe als BC7
 * (BPTC, sRGB), Normale und Rauheit+Verdeckung als BC5 (RGTC2), zusammen 3 Byte je Bildpunkt statt 8. Beim ersten Laden
 * gibt jede Stufe ein ungepacktes Bild an den Treiber, der es packt; das Gepackte wird zurückgelesen und neben die Karten
 * gelegt ({@code gpu_a_1024.bin} usw.), ab dann direkt geladen. Packt der Treiber nicht (etwa ohne BPTC), bleiben die
 * Felder ungepackt (RGBA8/RG8) und {@link #info()} sagt es. Die Mip-Stufen rechnet der Prozessor: Farbe linear gemittelt,
 * Normalen als Vektoren gemittelt und wieder auf Länge 1 gebracht.
 */
public final class SurfaceGl {
    /** Der Baustein als Text. */
    public static String glsl() {
        try (InputStream in = SurfaceGl.class.getResourceAsStream("surface.glsl")) {
            if (in == null) throw new IllegalStateException("surface.glsl fehlt im Klassenpfad");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static final int BC7_SRGB = GL4.GL_COMPRESSED_SRGB_ALPHA_BPTC_UNORM, BC5 = GL4.GL_COMPRESSED_RG_RGTC2;
    private final int[] tex = new int[3];
    private int edge, levels, layers;
    private boolean compressed;
    private long bytes;
    private double loadMs;
    private int fromCache, packed;
    private String note = "";
    private MaterialSet set;

    public boolean ready() { return tex[0] != 0; }
    public int edge() { return edge; }
    public boolean compressed() { return compressed; }

    /**
     * Lädt alle Schichten von set in Kantenlänge edgeWanted (höchstens die gebackene). pack: packen (BC7/BC5), sonst
     * ungepackt. Läuft im GL-Faden; beim ersten Mal mit Packen dauert es (Treiber), danach liest es die gemerkten Dateien.
     */
    public void load(GL4 gl, MaterialSet set, int edgeWanted, boolean pack) throws IOException {
        dispose(gl);
        this.set = set;
        long t0 = System.nanoTime();
        layers = set.layers().size();
        if (layers == 0) { note = "keine gebackenen Materialien"; return; }
        int e = edgeWanted;
        for (MaterialSet.Layer l : set.layers()) e = Math.min(e, l.size());
        edge = Integer.highestOneBit(Math.max(4, e));
        levels = Integer.numberOfTrailingZeros(edge) + 1;
        compressed = pack && probe(gl);
        if (pack && !compressed) note = "der Treiber packt nicht: ungepackt";
        int[] fmt = compressed ? new int[]{BC7_SRGB, BC5, BC5} : new int[]{GL.GL_SRGB8_ALPHA8, GL4.GL_RG8, GL4.GL_RG8};
        gl.glGenTextures(3, tex, 0);
        float aniso = anisotropy(gl);
        for (int g = 0; g < 3; g++) {
            gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, tex[g]);
            gl.glTexStorage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, levels, fmt[g], edge, edge, layers);
            gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
            gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
            gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
            if (aniso > 1) gl.glTexParameterf(GL2ES3.GL_TEXTURE_2D_ARRAY, GL4.GL_TEXTURE_MAX_ANISOTROPY, aniso);
        }
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        fromCache = 0;
        packed = 0;
        bytes = 0;
        for (MaterialSet.Layer l : set.layers()) {
            List<byte[]>[] mips = null;
            for (int g = 0; g < 3; g++) {
                File cache = new File(l.dir(), String.format(Locale.ROOT, "gpu_%s_%d.bin", "anr".charAt(g), edge));
                File src = new File(l.dir(), "anr".charAt(g) + ".png");
                List<byte[]> c = compressed ? readCache(cache, fmt[g], src.lastModified()) : null;
                if (c != null) fromCache++;
                else {
                    if (mips == null) mips = mips(l.dir(), edge);
                    if (compressed) {
                        c = new ArrayList<>();
                        for (int lv = 0; lv < levels; lv++) c.add(pack(gl, fmt[g], g == 0 ? GL.GL_RGBA : GL4.GL_RG, mips[g].get(lv), edge >> lv));
                        writeCache(cache, fmt[g], src.lastModified(), c);
                        packed++;
                    } else c = mips[g];
                }
                gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, tex[g]);
                for (int lv = 0; lv < levels; lv++) {
                    int s = Math.max(1, edge >> lv);
                    ByteBuffer b = Buffers.newDirectByteBuffer(c.get(lv).length);
                    b.put(c.get(lv)).flip();
                    if (compressed) gl.glCompressedTexSubImage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, lv, 0, 0, l.index(), s, s, 1, fmt[g], b.capacity(), b);
                    else gl.glTexSubImage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, lv, 0, 0, l.index(), s, s, 1, g == 0 ? GL.GL_RGBA : GL4.GL_RG, GL.GL_UNSIGNED_BYTE, b);
                    bytes += c.get(lv).length;
                }
            }
        }
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, 0);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 4);
        loadMs = (System.nanoTime() - t0) / 1e6;
        int err = gl.glGetError();
        if (err != 0) note = (note.isEmpty() ? "" : note + "; ") + String.format("GL-Fehler 0x%X beim Laden", err);
    }

    /** Packt der Treiber BC7 und BC5? (ein 8 × 8 großes Probebild) */
    static boolean probe(GL4 gl) {
        try {
            byte[] a = new byte[8 * 8 * 4], b = new byte[8 * 8 * 2];
            return pack(gl, BC7_SRGB, GL.GL_RGBA, a, 8).length == 64 && pack(gl, BC5, GL4.GL_RG, b, 8).length == 64;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Ein Bild durch den Treiber packen und das Gepackte zurücklesen (2D-Hilfstextur). */
    static byte[] pack(GL4 gl, int fmt, int srcFmt, byte[] data, int size) {
        int s = Math.max(1, size);
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        ByteBuffer in = Buffers.newDirectByteBuffer(data.length);
        in.put(data).flip();
        while (gl.glGetError() != 0) { /* alte Fehler weg */ }
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, fmt, s, s, 0, srcFmt, GL.GL_UNSIGNED_BYTE, in);
        int[] v = new int[1];
        gl.glGetTexLevelParameteriv(GL.GL_TEXTURE_2D, 0, GL2ES3.GL_TEXTURE_COMPRESSED, v, 0);
        if (v[0] == 0 || gl.glGetError() != 0) { gl.glDeleteTextures(1, t, 0); throw new IllegalStateException("nicht gepackt"); }
        gl.glGetTexLevelParameteriv(GL.GL_TEXTURE_2D, 0, com.jogamp.opengl.GL2GL3.GL_TEXTURE_COMPRESSED_IMAGE_SIZE, v, 0);
        ByteBuffer out = Buffers.newDirectByteBuffer(v[0]);
        gl.glGetCompressedTexImage(GL.GL_TEXTURE_2D, 0, out);
        byte[] r = new byte[v[0]];
        out.get(r);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glDeleteTextures(1, t, 0);
        return r;
    }

    static float anisotropy(GL4 gl) {
        float[] m = new float[1];
        gl.glGetFloatv(GL4.GL_MAX_TEXTURE_MAX_ANISOTROPY, m, 0);
        gl.glGetError();
        return Math.min(8, m[0]);
    }

    // ------------------------------------------------------------------ Mip-Stufen

    /** Die drei Gruppen eines Ordners in Kantenlänge edge mit allen Mip-Stufen: [a: RGBA][n: RG][r: RG]. */
    @SuppressWarnings("unchecked")
    public static List<byte[]>[] mips(File dir, int edge) throws IOException {
        BufferedImage a = ImageIO.read(new File(dir, "a.png")), n = ImageIO.read(new File(dir, "n.png")), r = ImageIO.read(new File(dir, "r.png"));
        if (a == null || n == null || r == null) throw new IOException("Karten in " + dir + " unvollständig");
        // Farbe linear (für das Mitteln), Höhe, Normale als Vektor, Rauheit, Verdeckung
        int s0 = a.getWidth();
        float[][] f = new float[9][];
        load(a, s0, f, 0, true);
        load(n, s0, f, 4, false);
        load(r, s0, f, 7, false);
        // Normalen als Vektor (x, y, z)
        for (int i = 0; i < f[4].length; i++) {
            double x = f[4][i] * 2 - 1, y = f[5][i] * 2 - 1, z = Math.sqrt(Math.max(0, 1 - x * x - y * y));
            f[4][i] = (float) x;
            f[5][i] = (float) y;
            f[6][i] = (float) z;
        }
        int s = s0;
        while (s > edge) { f = half(f, s); s /= 2; }
        List<byte[]>[] out = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
        while (true) {
            int px = s * s;
            byte[] ba = new byte[px * 4], bn = new byte[px * 2], br = new byte[px * 2];
            for (int i = 0; i < px; i++) {
                ba[i * 4] = (byte) srgb8(f[0][i]);
                ba[i * 4 + 1] = (byte) srgb8(f[1][i]);
                ba[i * 4 + 2] = (byte) srgb8(f[2][i]);
                ba[i * 4 + 3] = (byte) unit8(f[3][i]);
                double x = f[4][i], y = f[5][i], z = f[6][i], len = Math.sqrt(x * x + y * y + z * z);
                if (len < 1e-6) { x = 0; y = 0; len = 1; }
                bn[i * 2] = (byte) unit8(0.5 + x / len / 2);
                bn[i * 2 + 1] = (byte) unit8(0.5 + y / len / 2);
                br[i * 2] = (byte) unit8(f[7][i]);
                br[i * 2 + 1] = (byte) unit8(f[8][i]);
            }
            out[0].add(ba);
            out[1].add(bn);
            out[2].add(br);
            if (s == 1) break;
            f = half(f, s);
            s /= 2;
        }
        return out;
    }

    static void load(BufferedImage im, int s, float[][] f, int at, boolean srgb) {
        int w = im.getWidth();
        int k = at == 0 ? 4 : at == 4 ? 3 : 2;
        for (int c = 0; c < k; c++) f[at + c] = new float[s * s];
        int[] row = new int[w];
        for (int y = 0; y < s; y++) {
            im.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < s; x++) {
                int p = row[x], i = y * s + x;
                if (at == 0) {
                    f[0][i] = (float) lin((p >> 16) & 255);
                    f[1][i] = (float) lin((p >> 8) & 255);
                    f[2][i] = (float) lin(p & 255);
                    f[3][i] = ((p >>> 24) & 255) / 255f;
                } else {
                    f[at][i] = ((p >> 16) & 255) / 255f;
                    f[at + 1][i] = ((p >> 8) & 255) / 255f;
                    if (k == 3) f[at + 2][i] = 0;
                }
            }
        }
    }

    /** Halbe Größe (2 × 2 gemittelt). */
    static float[][] half(float[][] f, int s) {
        int h = Math.max(1, s / 2);
        float[][] o = new float[f.length][h * h];
        for (int c = 0; c < f.length; c++)
            for (int y = 0; y < h; y++)
                for (int x = 0; x < h; x++) {
                    int a = (2 * y) * s + 2 * x;
                    o[c][y * h + x] = s == 1 ? f[c][0] : (f[c][a] + f[c][a + 1] + f[c][a + s] + f[c][a + s + 1]) / 4f;
                }
        return o;
    }

    static double lin(int c) { double v = c / 255.0; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }

    static int srgb8(double v) {
        v = Math.max(0, Math.min(1, v));
        double s = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(s * 255);
    }

    static int unit8(double v) { return (int) Math.round(Math.max(0, Math.min(1, v)) * 255); }

    // ------------------------------------------------------------------ gemerktes Gepacktes

    static List<byte[]> readCache(File f, int fmt, long srcTime) {
        if (!f.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(f.toPath())))) {
            if (in.readInt() != 0x53524631 || in.readInt() != fmt || in.readLong() != srcTime) return null;
            int n = in.readInt();
            List<byte[]> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                l.add(b);
            }
            return l;
        } catch (IOException e) {
            return null;
        }
    }

    static void writeCache(File f, int fmt, long srcTime, List<byte[]> l) {
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(f.toPath())))) {
            out.writeInt(0x53524631);   // "SRF1"
            out.writeInt(fmt);
            out.writeLong(srcTime);
            out.writeInt(l.size());
            for (byte[] b : l) { out.writeInt(b.length); out.write(b); }
        } catch (IOException e) {
            System.err.println("surface: " + f + " nicht geschrieben: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Werte

    /**
     * Texturen auf die Stellen unitA, unitN, unitR und die Werte je Schicht setzen (das Programm muss benutzt sein).
     * far: ab welcher Bildpunktgröße (Anteil der Kachel) nur noch das Mittel gilt (Vorgabe 0,35).
     */
    public void bind(GL4 gl, int prog, int unitA, int unitN, int unitR, double far) {
        int[] units = {unitA, unitN, unitR};
        String[] names = {"uSurfA", "uSurfN", "uSurfR"};
        for (int g = 0; g < 3; g++) {
            gl.glActiveTexture(GL.GL_TEXTURE0 + units[g]);
            gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, tex[g]);
            gl.glUniform1i(gl.glGetUniformLocation(prog, names[g]), units[g]);
        }
        gl.glActiveTexture(GL.GL_TEXTURE0);
        if (set == null) return;
        int n = Math.min(16, set.layers().size());
        float[] param = new float[16 * 4], mean = new float[16 * 4], wet = new float[16];
        for (int i = 0; i < n; i++) {
            MaterialSet.Layer l = set.layers().get(i);
            param[i * 4] = (float) (1 / l.material().size);
            param[i * 4 + 1] = (float) l.material().heightScale;
            param[i * 4 + 2] = l.material().rotate ? 1 : 0;
            param[i * 4 + 3] = l.material().triplanar ? 1 : 0;
            mean[i * 4] = (float) l.meanColor()[0];
            mean[i * 4 + 1] = (float) l.meanColor()[1];
            mean[i * 4 + 2] = (float) l.meanColor()[2];
            mean[i * 4 + 3] = (float) l.meanRough();
            wet[i] = (float) l.material().wetDarken;
        }
        gl.glUniform4fv(gl.glGetUniformLocation(prog, "uSurfParam[0]"), 16, param, 0);
        gl.glUniform4fv(gl.glGetUniformLocation(prog, "uSurfMean[0]"), 16, mean, 0);
        gl.glUniform1fv(gl.glGetUniformLocation(prog, "uSurfWet[0]"), 16, wet, 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uSurfCount"), n);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uSurfFar"), (float) far);
    }

    /** „14 Materialien à 1024², gepackt BC7/BC5, 59 MB, 14 aus dem Speicher, 0 neu gepackt, 312 ms“ */
    public String info() {
        if (tex[0] == 0) return "Materialien: noch nicht geladen" + (note.isEmpty() ? "" : " (" + note + ")");
        return String.format(Locale.GERMAN, "%d Materialien à %d², %s, %.0f MB, %d Karten aus dem Speicher, %d neu gepackt, %.0f ms%s", layers, edge,
                compressed ? "gepackt BC7/BC5" : "ungepackt", bytes / 1e6, fromCache, packed, loadMs, note.isEmpty() ? "" : " (" + note + ")");
    }

    public long bytes() { return bytes; }

    public void dispose(GL4 gl) {
        if (tex[0] != 0) gl.glDeleteTextures(3, tex, 0);
        tex[0] = tex[1] = tex[2] = 0;
    }
}
