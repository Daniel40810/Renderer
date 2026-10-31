package com.dan.rail.gl;

import com.dan.rail.Fahrzeug;
import com.dan.rail.Track;
import com.dan.rail.Train;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Seit Brocken Phase 13: Züge mit JOGL (Paketvertrag Punkt 3). Zwei Modelle aus Quadern und Zylindern, prozedural:
 * <ul>
 * <li><b>Dampflok</b> nach der Baureihe 99.23–24 (1′E1′h2t) [G in den Maßen]: Kessel und Rauchkammer schwarz, Schornstein,
 * Dampf- und Sanddom, Wasserkästen neben dem Kessel, Führerhaus mit Fenstern, Kohlenkasten; Rahmen, Räder und
 * Pufferbohlen rot; fünf gekuppelte Achsen (1 000 mm), Lauf­achsen vorn und hinten, Zylinder, Laternen.</li>
 * <li><b>Wagen</b> mit offenen Bühnen an den Enden, zwei Drehgestellen, Fensterband und gewölbtem Dach; Farben [G]
 * (die Farbgebung der HSB-Wagen war nicht zu belegen: dunkelgrün, Fensterband elfenbein, Dach grau).</li>
 * </ul>
 * Gesetzt werden die Fahrzeuge nach {@link Fahrzeug#lage} (zwischen den Drehpunkten, mit Überhöhung, auch im
 * Ausweichgleis). Licht wie {@link RailGl}. Shader:
 * {@code train.vert}, {@code train.frag}.
 */
public final class TrainGl {
    static final double SCHLOT_VOR = Fahrzeug.SCHLOT_VOR, SCHLOT_HOCH = Fahrzeug.SCHLOT_HOCH;

    private int program, vao, vbo;
    private int lokFirst, lokCount, wagenFirst, wagenCount;
    private int drawn;
    /** Bis so weit vom Auge werden Fahrzeuge gezeichnet (m). */
    public double range = 3000;

    // Farben (linear) [G]
    static final float[] SCHWARZ = {0.025f, 0.025f, 0.027f}, ROT = {0.30f, 0.025f, 0.02f}, RAUCH = {0.04f, 0.04f, 0.042f},
            GRAU = {0.12f, 0.12f, 0.12f}, MESSING = {0.35f, 0.25f, 0.08f}, GLAS = {0.02f, 0.025f, 0.03f}, LAMPE = {1.0f, 0.85f, 0.55f},
            GRUEN = {0.03f, 0.075f, 0.045f}, ELFENBEIN = {0.55f, 0.50f, 0.38f}, DACH = {0.10f, 0.10f, 0.10f}, KOHLE = {0.015f, 0.015f, 0.015f};
    /** Stoff: 0 matt, 1 Lack (glänzt), 2 Glas (spiegelt den Himmel), 3 leuchtet. */
    static final float MATT = 0, LACK = 1, SCHEIBE = 2, LEUCHTE = 3;

    public void init(GL4 gl) {
        program = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, "train.vert"), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, "train.frag");
        gl.glAttachShader(program, vs);
        gl.glAttachShader(program, fs);
        gl.glLinkProgram(program);
        int[] ok = new int[1];
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("rail: Zug-Programm lässt sich nicht binden");
        Mesh m = new Mesh();
        lokFirst = 0;
        lok(m);
        lokCount = m.verts();
        wagenFirst = lokCount;
        wagen(m);
        wagenCount = m.verts() - lokCount;
        FloatBuffer fb = Buffers.newDirectFloatBuffer(m.v.size());
        for (Float f : m.v) fb.put(f);
        fb.flip();
        int[] v = new int[2];
        gl.glGenVertexArrays(1, v, 0);
        gl.glGenBuffers(1, v, 1);
        vao = v[0]; vbo = v[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) fb.limit() * 4, fb, GL.GL_STATIC_DRAW);
        for (int a = 0; a < 3; a++) {
            gl.glEnableVertexAttribArray(a);
            gl.glVertexAttribPointer(a, 3, GL.GL_FLOAT, false, 40, a * 12L);
        }
        gl.glEnableVertexAttribArray(3);
        gl.glVertexAttribPointer(3, 1, GL.GL_FLOAT, false, 40, 36);
        gl.glBindVertexArray(0);
    }

    /** Dreiecke der Lok und eines Wagens. */
    public int[] triangles() { return new int[]{lokCount / 3, wagenCount / 3}; }

    // ------------------------------------------------------------------ Modelle (x rechts, y oben ab Schiene, z vorn)

    static void lok(Mesh m) {
        // Rahmen und Umlauf
        m.box(-0.62, 0.62, 0.55, 1.05, -5.6, 5.6, ROT, LACK);
        m.box(-1.25, 1.25, 1.05, 1.15, -5.0, 5.3, SCHWARZ, MATT);
        // Pufferbohlen mit Puffern und Kupplung (Mittelpuffer der Meterspur)
        for (int e = -1; e <= 1; e += 2) {
            double z = e * 6.0;
            m.box(-1.2, 1.2, 0.55, 1.05, Math.min(z, z - e * 0.3), Math.max(z, z - e * 0.3), ROT, LACK);
            m.box(-0.18, 0.18, 0.62, 0.92, Math.min(z, z + e * 0.3), Math.max(z, z + e * 0.3), GRAU, MATT);
        }
        // Kessel, Rauchkammer, Rauchkammertür
        m.cylZ(0, 2.25, 0.78, -2.4, 3.9, SCHWARZ, LACK, 20);
        m.cylZ(0, 2.25, 0.82, 3.9, 5.2, RAUCH, MATT, 20);
        m.cylZ(0, 2.25, 0.62, 5.2, 5.3, GRAU, MATT, 20);
        // Schornstein mit Kragen, Dampfdom, Sanddom, Pfeife
        m.cylY(0, SCHLOT_VOR, 0.24, 2.9, SCHLOT_HOCH - 0.12, SCHWARZ, MATT, 12);
        m.cylY(0, SCHLOT_VOR, 0.31, SCHLOT_HOCH - 0.12, SCHLOT_HOCH, SCHWARZ, MATT, 12);
        m.cylY(0, 1.3, 0.36, 2.9, 3.45, SCHWARZ, LACK, 14);
        m.cylY(0, 2.6, 0.30, 2.9, 3.3, SCHWARZ, LACK, 14);
        m.cylY(0.25, -2.0, 0.05, 3.0, 3.55, MESSING, LACK, 8);
        // Wasserkästen links und rechts
        for (int e = -1; e <= 1; e += 2) m.box(e > 0 ? 0.62 : -1.25, e > 0 ? 1.25 : -0.62, 1.15, 2.55, -2.4, 2.2, SCHWARZ, LACK);
        // Führerhaus mit Fenstern vorn und an den Seiten, Dach
        m.box(-1.25, 1.25, 1.15, 3.35, -4.7, -2.4, SCHWARZ, LACK);
        m.box(-1.32, 1.32, 3.35, 3.5, -4.85, -2.25, SCHWARZ, MATT);
        for (int e = -1; e <= 1; e += 2) {
            double x = e * 1.26;
            m.box(Math.min(x, x + e * 0.02), Math.max(x, x + e * 0.02), 2.3, 3.05, -4.2, -2.9, GLAS, SCHEIBE);
            m.box(e > 0 ? 0.35 : -0.95, e > 0 ? 0.95 : -0.35, 2.55, 3.1, -2.39, -2.37, GLAS, SCHEIBE);
        }
        // Kohlenkasten
        m.box(-1.2, 1.2, 1.15, 2.6, -5.9, -4.7, SCHWARZ, LACK);
        m.box(-1.0, 1.0, 2.6, 2.75, -5.8, -4.8, KOHLE, MATT);
        // Zylinder vorn
        for (int e = -1; e <= 1; e += 2) m.cylZ(e * 0.98, 0.95, 0.28, 3.3, 4.4, SCHWARZ, MATT, 12);
        // Räder: fünf gekuppelte Achsen (Treibrad 1 000 mm), Laufachsen vorn und hinten (680 mm) [G]
        for (int k = -2; k <= 2; k++) wheelPair(m, k * 1.1, 0.5);
        wheelPair(m, 4.6, 0.34);
        wheelPair(m, -5.1, 0.34);
        // Laternen vorn und hinten
        for (int e = -1; e <= 1; e += 2) {
            double z = e > 0 ? 6.02 : -6.02;
            for (int sx = -1; sx <= 1; sx += 2) m.box(sx * 0.7 - 0.09, sx * 0.7 + 0.09, 1.25, 1.45, Math.min(z, z + e * 0.05), Math.max(z, z + e * 0.05), LAMPE, LEUCHTE);
        }
        m.box(-0.09, 0.09, 3.05, 3.25, 5.32, 5.37, LAMPE, LEUCHTE);
    }

    static void wheelPair(Mesh m, double z, double r) {
        for (int e = -1; e <= 1; e += 2) {
            double x = e * 0.56;
            m.cylX(r, z, r, Math.min(x, x + e * 0.13), Math.max(x, x + e * 0.13), ROT, LACK, 16);
            m.cylX(r, z, r * 0.25, Math.min(x + e * 0.13, x + e * 0.2), Math.max(x + e * 0.13, x + e * 0.2), GRAU, MATT, 8);
        }
    }

    static void wagen(Mesh m) {
        double L = Train.WAGEN_LAENGE / 2, kasten = L - 0.9;
        // Untergestell, Bühnen, Kasten
        m.box(-0.9, 0.9, 0.7, 1.0, -L + 0.3, L - 0.3, SCHWARZ, MATT);
        for (int e = -1; e <= 1; e += 2) {
            double z0 = e > 0 ? kasten : -L + 0.3, z1 = e > 0 ? L - 0.3 : -kasten;
            m.box(-1.3, 1.3, 1.0, 1.1, z0, z1, SCHWARZ, MATT);
            // Geländer der Bühne
            for (int sx = -1; sx <= 1; sx += 2) m.box(sx * 1.25 - 0.03, sx * 1.25 + 0.03, 1.1, 2.1, z0, z1, SCHWARZ, MATT);
            double ze = e > 0 ? L - 0.35 : -L + 0.35;
            m.box(-1.28, -0.45, 2.05, 2.1, ze - 0.03, ze + 0.03, SCHWARZ, MATT);
            m.box(0.45, 1.28, 2.05, 2.1, ze - 0.03, ze + 0.03, SCHWARZ, MATT);
            // Puffer und Kupplung
            m.box(-0.18, 0.18, 0.62, 0.92, Math.min(e * L, e * (L - 0.3)), Math.max(e * L, e * (L - 0.3)), GRAU, MATT);
            // Dach über der Bühne
            m.box(-1.3, 1.3, 3.25, 3.32, z0, z1, DACH, MATT);
        }
        m.box(-1.3, 1.3, 1.0, 2.0, -kasten, kasten, GRUEN, LACK);
        m.box(-1.3, 1.3, 2.0, 2.75, -kasten, kasten, ELFENBEIN, LACK);
        m.box(-1.3, 1.3, 2.75, 3.15, -kasten, kasten, GRUEN, LACK);
        // Fenster an den Seiten (8 je Seite) und in den Stirnwänden (Türen)
        int n = 8;
        double w = (2 * kasten - 0.8) / n;
        for (int e = -1; e <= 1; e += 2)
            for (int k = 0; k < n; k++) {
                double z0 = -kasten + 0.4 + k * w + 0.12, x = e * 1.31;
                m.box(Math.min(x, x + e * 0.01), Math.max(x, x + e * 0.01), 2.08, 2.68, z0, z0 + w - 0.24, GLAS, SCHEIBE);
            }
        for (int e = -1; e <= 1; e += 2) {
            double z = e * (kasten + 0.005);
            m.box(-0.4, 0.4, 1.15, 2.85, Math.min(z, z + e * 0.01), Math.max(z, z + e * 0.01), GRUEN, LACK);
            m.box(-0.3, 0.3, 2.1, 2.7, Math.min(z, z + e * 0.02), Math.max(z, z + e * 0.02), GLAS, SCHEIBE);
        }
        // gewölbtes Dach
        m.roof(1.32, 3.15, 0.25, -kasten - 0.05, kasten + 0.05, DACH, 10);
        // zwei Drehgestelle mit je zwei Achsen (Raddurchmesser 650 mm) [G]
        for (int e = -1; e <= 1; e += 2) {
            double zc = e * (L * 0.62);
            m.box(-0.8, 0.8, 0.35, 0.65, zc - 1.1, zc + 1.1, SCHWARZ, MATT);
            for (int a = -1; a <= 1; a += 2) {
                double z = zc + a * 0.6;
                for (int sx = -1; sx <= 1; sx += 2) {
                    double x = sx * 0.56;
                    m.cylX(0.325, z, 0.325, Math.min(x, x + sx * 0.1), Math.max(x, x + sx * 0.1), SCHWARZ, MATT, 12);
                }
            }
        }
    }

    /** Ein Netz aus Dreiecken: Ort, Normale, Farbe, Stoff (10 Zahlen je Ecke). */
    static final class Mesh {
        final List<Float> v = new ArrayList<>(1 << 14);

        int verts() { return v.size() / 10; }

        void vert(double x, double y, double z, double nx, double ny, double nz, float[] c, float mat) {
            v.add((float) x); v.add((float) y); v.add((float) z);
            v.add((float) nx); v.add((float) ny); v.add((float) nz);
            v.add(c[0]); v.add(c[1]); v.add(c[2]); v.add(mat);
        }

        void quad(double[] a, double[] b, double[] c, double[] d, double[] n, float[] col, float mat) {
            for (double[] p : new double[][]{a, b, c, a, c, d}) vert(p[0], p[1], p[2], n[0], n[1], n[2], col, mat);
        }

        void box(double x0, double x1, double y0, double y1, double z0, double z1, float[] c, float mat) {
            quad(p(x0, y1, z0), p(x1, y1, z0), p(x1, y1, z1), p(x0, y1, z1), p(0, 1, 0), c, mat);
            quad(p(x0, y0, z0), p(x0, y0, z1), p(x1, y0, z1), p(x1, y0, z0), p(0, -1, 0), c, mat);
            quad(p(x1, y0, z0), p(x1, y0, z1), p(x1, y1, z1), p(x1, y1, z0), p(1, 0, 0), c, mat);
            quad(p(x0, y0, z0), p(x0, y1, z0), p(x0, y1, z1), p(x0, y0, z1), p(-1, 0, 0), c, mat);
            quad(p(x0, y0, z1), p(x0, y1, z1), p(x1, y1, z1), p(x1, y0, z1), p(0, 0, 1), c, mat);
            quad(p(x0, y0, z0), p(x1, y0, z0), p(x1, y1, z0), p(x0, y1, z0), p(0, 0, -1), c, mat);
        }

        /** Zylinder längs z (Kessel, Zylinder) mit Deckeln. */
        void cylZ(double cx, double cy, double r, double z0, double z1, float[] c, float mat, int n) {
            for (int k = 0; k < n; k++) {
                double a = 2 * Math.PI * k / n, b = 2 * Math.PI * (k + 1) / n, am = (a + b) / 2;
                double[] pa0 = p(cx + r * Math.cos(a), cy + r * Math.sin(a), z0), pb0 = p(cx + r * Math.cos(b), cy + r * Math.sin(b), z0);
                double[] pa1 = p(pa0[0], pa0[1], z1), pb1 = p(pb0[0], pb0[1], z1);
                quad(pa0, pb0, pb1, pa1, p(Math.cos(am), Math.sin(am), 0), c, mat);
                tri(p(cx, cy, z1), pa1, pb1, p(0, 0, 1), c, mat);
                tri(p(cx, cy, z0), pb0, pa0, p(0, 0, -1), c, mat);
            }
        }

        /** Zylinder längs x (Räder) mit Deckeln. */
        void cylX(double cy, double cz, double r, double x0, double x1, float[] c, float mat, int n) {
            for (int k = 0; k < n; k++) {
                double a = 2 * Math.PI * k / n, b = 2 * Math.PI * (k + 1) / n, am = (a + b) / 2;
                double[] pa0 = p(x0, cy + r * Math.cos(a), cz + r * Math.sin(a)), pb0 = p(x0, cy + r * Math.cos(b), cz + r * Math.sin(b));
                double[] pa1 = p(x1, pa0[1], pa0[2]), pb1 = p(x1, pb0[1], pb0[2]);
                quad(pa0, pb0, pb1, pa1, p(0, Math.cos(am), Math.sin(am)), c, mat);
                tri(p(x1, cy, cz), pa1, pb1, p(1, 0, 0), c, mat);
                tri(p(x0, cy, cz), pb0, pa0, p(-1, 0, 0), c, mat);
            }
        }

        /** Zylinder längs y (Schornstein, Dome) mit Deckel oben. */
        void cylY(double cx, double cz, double r, double y0, double y1, float[] c, float mat, int n) {
            for (int k = 0; k < n; k++) {
                double a = 2 * Math.PI * k / n, b = 2 * Math.PI * (k + 1) / n, am = (a + b) / 2;
                double[] pa0 = p(cx + r * Math.cos(a), y0, cz + r * Math.sin(a)), pb0 = p(cx + r * Math.cos(b), y0, cz + r * Math.sin(b));
                double[] pa1 = p(pa0[0], y1, pa0[2]), pb1 = p(pb0[0], y1, pb0[2]);
                quad(pa0, pa1, pb1, pb0, p(Math.cos(am), 0, Math.sin(am)), c, mat);
                tri(p(cx, y1, cz), pb1, pa1, p(0, 1, 0), c, mat);
            }
        }

        /** Gewölbtes Dach: Bogen der halben Breite w über der Traufe y, Stich h, längs z. */
        void roof(double w, double y, double h, double z0, double z1, float[] c, int n) {
            for (int k = 0; k < n; k++) {
                double u0 = -1 + 2.0 * k / n, u1 = -1 + 2.0 * (k + 1) / n;
                double[] a0 = p(u0 * w, y + h * (1 - u0 * u0), z0), b0 = p(u1 * w, y + h * (1 - u1 * u1), z0);
                double[] a1 = p(a0[0], a0[1], z1), b1 = p(b0[0], b0[1], z1);
                double um = (u0 + u1) / 2, nx = 2 * h * um / w, l = Math.hypot(nx, 1);
                quad(a0, a1, b1, b0, p(nx / l, 1 / l, 0), c, LACK);
                tri(p(0, y, z1), a1, b1, p(0, 0, 1), c, LACK);
                tri(p(0, y, z0), b0, a0, p(0, 0, -1), c, LACK);
            }
        }

        void tri(double[] a, double[] b, double[] c, double[] n, float[] col, float mat) {
            for (double[] q : new double[][]{a, b, c}) vert(q[0], q[1], q[2], n[0], n[1], n[2], col, mat);
        }

        static double[] p(double x, double y, double z) { return new double[]{x, y, z}; }
    }

    // ------------------------------------------------------------------ Zeichnen

    /** Zeichnet die Züge auf dem Gleis (Tiefentest an). */
    public void draw(GL4 gl, RailGl.View view, RailGl.Light l, Track t, List<Train> zuege) {
        drawn = 0;
        if (vao == 0 || zuege.isEmpty()) return;
        gl.glUseProgram(program);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, view.viewProj, 0);
        gl.glUniform3f(loc(gl, "uCam"), (float) view.eye[0], (float) view.eye[1], (float) view.eye[2]);
        set3(gl, "uSunDir", l.sunDir);
        set3(gl, "uSunColor", l.sunColor);
        set3(gl, "uSkyColor", l.skyColor);
        set3(gl, "uFogColor", l.fogColor);
        gl.glUniform1f(loc(gl, "uFogDist"), (float) l.fogDistance);
        gl.glUniform2f(loc(gl, "uSnowWet"), (float) l.snow, (float) l.wetness);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(view.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        int um = loc(gl, "uModel"), ul = loc(gl, "uLicht");
        float[] mf = new float[16];
        for (Train z : zuege) {
            double[][] fz = z.fahrzeuge();
            for (int k = 0; k < fz.length; k++) {
                double[] m = Fahrzeug.lage(t, fz[k][0], fz[k][1], z.versatz);
                double d = Math.hypot(Math.hypot(m[12] - view.eye[0], m[14] - view.eye[2]), m[13] - view.eye[1]);
                if (d > range) continue;
                for (int i = 0; i < 16; i++) mf[i] = (float) m[i];
                gl.glUniformMatrix4fv(um, 1, false, mf, 0);
                // die Lampen der Lok leuchten in Fahrtrichtung
                gl.glUniform1f(ul, k == 0 ? (float) z.richtung : 0f);
                if (k == 0) gl.glDrawArrays(GL.GL_TRIANGLES, lokFirst, lokCount);
                else gl.glDrawArrays(GL.GL_TRIANGLES, wagenFirst, wagenCount);
                drawn++;
            }
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** „Züge: 2 · 12 Fahrzeuge im Bild · Lok 2 000 Dreiecke, Wagen 1 200“ */
    public String info(int zuege) {
        return String.format(Locale.GERMAN, "Züge: %d · %d Fahrzeuge im Bild · Lok %,d Dreiecke, Wagen %,d", zuege, drawn, lokCount / 3, wagenCount / 3);
    }

    public int drawn() { return drawn; }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(1, new int[]{vbo}, 0);
        program = vao = vbo = 0;
    }

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(program, n); }
    private void set3(GL4 gl, String n, double[] v) { gl.glUniform3f(loc(gl, n), (float) v[0], (float) v[1], (float) v[2]); }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{RailGl.source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            int[] n = new int[1];
            gl.glGetShaderInfoLog(sh, b.length, n, 0, b, 0);
            throw new IllegalStateException("rail: " + file + " lässt sich nicht übersetzen: " + new String(b, 0, n[0], java.nio.charset.StandardCharsets.UTF_8));
        }
        return sh;
    }
}
