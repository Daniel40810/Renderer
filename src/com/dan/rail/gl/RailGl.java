package com.dan.rail.gl;

import com.dan.rail.Track;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Darstellung eines Gleises mit JOGL (Paketvertrag Punkt 3). Seit Brocken Phase 12 als Netz: Schotterbett mit
 * Böschung bis zum Planum, zwei Schienen (Kopf blank, Flanken rostig), Schwellen als Exemplare eines Quaders, in
 * Bögen überhöht; dazu Bahnübergänge (Belag zwischen und neben den Schienen) und flache Bahnsteige an den Bahnhöfen.
 * Das Gleis ist in Stücke zu {@link #CHUNK} m geteilt; gezeichnet werden nur Stücke bis {@link #range} m vom Auge.
 * Licht wie in den anderen Packages: Sonne als Bestrahlung (vorbelichtet), Himmel als Bestrahlung/π, Dunst; Schnee
 * legt sich auf alles, was nach oben schaut. Shader: {@code rail.vert}, {@code rail.frag}.
 */
public final class RailGl {
    /** Länge eines Stücks (m). */
    public static final double CHUNK = 250;

    /** Blick: Matrix (Spalten), Auge, umgekehrte Tiefe. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
    }

    /** Licht: Sonne (Richtung, Bestrahlung), Himmel (Bestrahlung/π), Dunst; Schnee 0..1, Nässe 0..1. */
    public static final class Light {
        public double[] sunDir = {0.4, 0.7, 0.3}, sunColor = {2.5, 2.4, 2.2}, skyColor = {0.25, 0.3, 0.4}, fogColor = {0.6, 0.65, 0.7};
        public double fogDistance = 25_000, snow, wetness;
    }

    /** Schwellen aus Holz (Vorgabe) oder Beton. */
    public boolean concrete;
    /** Bis so weit vom Auge werden Stücke gezeichnet (m). */
    public double range = 1600;

    // Querschnitt (m, relativ zur Schienenoberkante, quer zur Achse): Schotter, Schienen, Schwellen [G nach Meterspur]
    static final double SCHOTTER_OBEN = -0.14, PLANUM = -0.55, SCHOTTER_BREIT = 1.3, PLANUM_BREIT = 2.0;
    static final double SCHIENE_KOPF = 0.06, SCHIENE_HOCH = 0.12, SCHWELLE_LANG = 1.8, SCHWELLE_BREIT = 0.22, SCHWELLE_HOCH = 0.14;

    private int program, vao, vbo, boxVao, boxVbo, boxIbo, instVbo;
    private int verts, instances;
    private final List<int[]> chunks = new ArrayList<>();   // {erste Ecke, Zahl, erste Schwelle, Zahl}
    private final List<double[]> boxes = new ArrayList<>();  // {x, y, z, Halbmesser}
    private String info = "";
    private int drawnChunks;

    public void init(GL4 gl) {
        program = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, "rail.vert"), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, "rail.frag");
        gl.glAttachShader(program, vs);
        gl.glAttachShader(program, fs);
        gl.glLinkProgram(program);
        int[] ok = new int[1];
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("rail: Programm lässt sich nicht binden: " + log(gl, program, true));
        int[] v = new int[7];
        gl.glGenVertexArrays(2, v, 0);
        gl.glGenBuffers(4, v, 2);
        vao = v[0]; boxVao = v[1]; vbo = v[2]; boxVbo = v[3]; boxIbo = v[4]; instVbo = v[5];
        // Quader der Schwelle: Einheitswürfel −0,5..0,5 mit Normalen (24 Ecken, 36 Indizes)
        float[] bx = new float[24 * 6];
        int[] ix = new int[36];
        int[][] faces = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        int o = 0, q = 0;
        for (int f = 0; f < 6; f++) {
            int[] n = faces[f];
            double[][] c = faceCorners(n);
            for (int k = 0; k < 4; k++) {
                bx[o++] = (float) c[k][0]; bx[o++] = (float) c[k][1]; bx[o++] = (float) c[k][2];
                bx[o++] = n[0]; bx[o++] = n[1]; bx[o++] = n[2];
            }
            int b = f * 4;
            ix[q++] = b; ix[q++] = b + 1; ix[q++] = b + 2; ix[q++] = b; ix[q++] = b + 2; ix[q++] = b + 3;
        }
        gl.glBindVertexArray(boxVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boxVbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, bx.length * 4L, Buffers.newDirectFloatBuffer(bx), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12);
        gl.glVertexAttrib3f(2, 0, 0, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, boxIbo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ix.length * 4L, Buffers.newDirectIntBuffer(ix), GL.GL_STATIC_DRAW);
        // je Schwelle: Ort (x, y, z), Richtung (rad), Überhöhung (rad), Farbe (Ton)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instVbo);
        for (int a = 0; a < 2; a++) {
            gl.glEnableVertexAttribArray(3 + a);
            gl.glVertexAttribPointer(3 + a, a == 0 ? 3 : 3, GL.GL_FLOAT, false, 24, a * 12L);
            gl.glVertexAttribDivisor(3 + a, 1);
        }
        gl.glBindVertexArray(0);
    }

    private static double[][] faceCorners(int[] n) {
        // vier Ecken der Seite mit Normale n, gegen den Uhrzeigersinn von außen gesehen
        double[] u = n[1] != 0 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] w = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
        double[][] c = new double[4][3];
        double[][] sg = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int k = 0; k < 4; k++)
            for (int a = 0; a < 3; a++) c[k][a] = 0.5 * n[a] + 0.5 * sg[k][0] * u[a] + 0.5 * sg[k][1] * w[a];
        return c;
    }

    /**
     * Netz eines Gleises hochladen: Schotter, Schienen, Schwellen; crossings je Bahnübergang {s0, s1} (m), dort liegt ein
     * Belag; an den Bahnhöfen des Gleises ein flacher Bahnsteig auf ihrer Seite ({@link Track.Station#side()}).
     */
    public void upload(GL4 gl, Track t, List<double[]> crossings) {
        chunks.clear();
        boxes.clear();
        List<Float> v = new ArrayList<>(1 << 16);
        List<Float> inst = new ArrayList<>(1 << 14);
        double step = 2;
        int nSchw = t.schwellen(), schw = 0;
        for (double c0 = 0; c0 < t.length(); c0 += CHUNK) {
            double c1 = Math.min(t.length(), c0 + CHUNK);
            int v0 = v.size() / 9, i0 = inst.size() / 6;
            for (double s = c0; s < c1 - 1e-6; s += step) {
                double s1 = Math.min(c1, s + step);
                Frame a = frame(t, s), b = frame(t, s1);
                // Schotter: Planum links, Schulter links, Schulter rechts, Planum rechts
                double[][] sec = {{-PLANUM_BREIT, PLANUM}, {-SCHOTTER_BREIT, SCHOTTER_OBEN}, {SCHOTTER_BREIT, SCHOTTER_OBEN}, {PLANUM_BREIT, PLANUM}};
                float[] schotter = {0.17f, 0.155f, 0.14f};
                for (int k = 0; k < 3; k++) quad(v, a, b, sec[k], sec[k + 1], schotter);
                // Schienen: Kopf oben, Flanken innen und außen
                for (int side = -1; side <= 1; side += 2) {
                    double m = side * (t.gauge / 2 + SCHIENE_KOPF / 2), hw = SCHIENE_KOPF / 2;
                    float[] kopf = {0.42f, 0.41f, 0.40f}, flanke = {0.13f, 0.075f, 0.045f};
                    quad(v, a, b, new double[]{m - hw, 0}, new double[]{m + hw, 0}, kopf);
                    quad(v, a, b, new double[]{m + hw, 0}, new double[]{m + hw, -SCHIENE_HOCH}, flanke);
                    quad(v, a, b, new double[]{m - hw, -SCHIENE_HOCH}, new double[]{m - hw, 0}, flanke);
                }
                // Bahnübergang: Belag von 3 m je Seite in Höhe der Schienen
                for (double[] cr : crossings)
                    if (s1 > cr[0] - 1 && s < cr[1] + 1) {
                        float[] belag = {0.09f, 0.09f, 0.095f};
                        quad(v, a, b, new double[]{-4.0, -0.01}, new double[]{4.0, -0.01}, belag);
                    }
                // Bahnsteig auf der Seite des Bahnhofs (seit Brocken Phase 14, vorher immer rechts): Kante 1,4 m von der
                // Achse, 3 m breit, 0,2 m über der Schiene
                Track.Station st = t.stationAt(s);
                if (st != null && st.side() != 0 && t.stationAt(s1) != null) {
                    float[] pflaster = {0.22f, 0.21f, 0.2f};
                    double sd = st.side();
                    quad(v, a, b, new double[]{1.4 * sd, 0.2}, new double[]{4.4 * sd, 0.2}, pflaster);
                    quad(v, a, b, new double[]{1.4 * sd, -0.3}, new double[]{1.4 * sd, 0.2}, pflaster);
                }
            }
            while (schw < nSchw && t.schwelle(schw) < c1) {
                double s = t.schwelle(schw++);
                Frame f = frame(t, s);
                inst.add((float) (f.x + f.ux * (-0.12 - SCHWELLE_HOCH / 2)));
                inst.add((float) (f.y + f.uy * (-0.12 - SCHWELLE_HOCH / 2)));
                inst.add((float) (f.z + f.uz * (-0.12 - SCHWELLE_HOCH / 2)));
                inst.add((float) f.heading);
                inst.add((float) f.cant);
                inst.add((float) (((schw * 2654435761L) & 1023) / 1023.0));
            }
            chunks.add(new int[]{v0, v.size() / 9 - v0, i0, inst.size() / 6 - i0});
            Track.Pose m = t.at((c0 + c1) / 2);
            boxes.add(new double[]{m.x(), m.y(), m.z(), (c1 - c0) / 2 + 10});
        }
        FloatBuffer fb = Buffers.newDirectFloatBuffer(v.size());
        for (Float f : v) fb.put(f);
        fb.flip();
        verts = v.size() / 9;
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) fb.limit() * 4, fb, GL.GL_STATIC_DRAW);
        for (int a = 0; a < 3; a++) {
            gl.glEnableVertexAttribArray(a);
            gl.glVertexAttribPointer(a, 3, GL.GL_FLOAT, false, 36, a * 12L);
        }
        gl.glVertexAttrib3f(3, 0, 0, 0);
        gl.glVertexAttrib3f(4, 0, 0, -1);    // Kennung: kein Exemplar
        gl.glBindVertexArray(0);
        FloatBuffer ib = Buffers.newDirectFloatBuffer(Math.max(6, inst.size()));
        for (Float f : inst) ib.put(f);
        ib.flip();
        instances = inst.size() / 6;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instVbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) Math.max(6, ib.limit()) * 4, ib, GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        info = String.format(Locale.GERMAN, "Gleis %s: %,d Dreiecke, %,d Schwellen in %d Stücken", t.name, verts / 3, instances, chunks.size());
    }

    /** Lage und Querrichtung an einer Stelle, mit der Überhöhung gekippt. */
    record Frame(double x, double y, double z, double heading, double cant, double rx, double ry, double rz, double ux, double uy, double uz) { }

    static Frame frame(Track t, double s) {
        Track.Pose p = t.at(s);
        double h = Math.toRadians(p.heading());
        double c = Math.atan2(t.cant(s), t.gauge + 0.067);   // Winkel der Überhöhung; + hebt die äußere (linke im Rechtsbogen) Schiene
        // rechts der Fahrtrichtung (cos h, 0, sin h); oben (0, 1, 0); gekippt um die Achse
        double rx = Math.cos(h), rz = Math.sin(h);
        double cr = Math.cos(c), sr = Math.sin(c);
        return new Frame(p.x(), p.y(), p.z(), h, c, rx * cr, -sr, rz * cr, rx * sr, cr, rz * sr);
    }

    private static void quad(List<Float> v, Frame a, Frame b, double[] p, double[] q, float[] col) {
        double[] a0 = at(a, p), a1 = at(a, q), b0 = at(b, p), b1 = at(b, q);
        // Normale aus den Kanten
        double ex = a1[0] - a0[0], ey = a1[1] - a0[1], ez = a1[2] - a0[2], fx = b0[0] - a0[0], fy = b0[1] - a0[1], fz = b0[2] - a0[2];
        double nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-12) return;
        nx /= l; ny /= l; nz /= l;
        if (ny < 0 && Math.abs(ny) > 0.3) { nx = -nx; ny = -ny; nz = -nz; }
        double[][] tri = {a0, a1, b1, a0, b1, b0};
        for (double[] c : tri) {
            v.add((float) c[0]); v.add((float) c[1]); v.add((float) c[2]);
            v.add((float) nx); v.add((float) ny); v.add((float) nz);
            v.add(col[0]); v.add(col[1]); v.add(col[2]);
        }
    }

    private static double[] at(Frame f, double[] o) {
        return new double[]{f.x + f.rx * o[0] + f.ux * o[1], f.y + f.ry * o[0] + f.uy * o[1], f.z + f.rz * o[0] + f.uz * o[1]};
    }

    /** Zeichnet in den gebundenen Bildspeicher (Tiefentest an). */
    public void draw(GL4 gl, View view, Light l) {
        if (verts == 0) return;
        gl.glUseProgram(program);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, view.viewProj, 0);
        gl.glUniform3f(loc(gl, "uCam"), (float) view.eye[0], (float) view.eye[1], (float) view.eye[2]);
        set3(gl, "uSunDir", l.sunDir);
        set3(gl, "uSunColor", l.sunColor);
        set3(gl, "uSkyColor", l.skyColor);
        set3(gl, "uFogColor", l.fogColor);
        gl.glUniform1f(loc(gl, "uFogDist"), (float) l.fogDistance);
        gl.glUniform2f(loc(gl, "uSnowWet"), (float) l.snow, (float) l.wetness);
        gl.glUniform3f(loc(gl, "uSchwelle"), (float) SCHWELLE_LANG, (float) SCHWELLE_HOCH, (float) SCHWELLE_BREIT);
        gl.glUniform1i(loc(gl, "uBeton"), concrete ? 1 : 0);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(view.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        drawnChunks = 0;
        for (int c = 0; c < chunks.size(); c++) {
            double[] b = boxes.get(c);
            double d = Math.hypot(Math.hypot(b[0] - view.eye[0], b[2] - view.eye[2]), b[1] - view.eye[1]) - b[3];
            if (d > range) continue;
            int[] k = chunks.get(c);
            drawnChunks++;
            gl.glUniform1i(loc(gl, "uInst"), 0);
            gl.glBindVertexArray(vao);
            gl.glDrawArrays(GL.GL_TRIANGLES, k[0], k[1]);
            // Schwellen nur nah (bis 500 m): weiter weg sind sie kleiner als ein Bildpunkt
            if (k[3] > 0 && d < 500) {
                gl.glUniform1i(loc(gl, "uInst"), 1);
                gl.glBindVertexArray(boxVao);
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instVbo);
                for (int a = 0; a < 2; a++) gl.glVertexAttribPointer(3 + a, 3, GL.GL_FLOAT, false, 24, (long) k[2] * 24 + a * 12L);
                gl.glDrawElementsInstanced(GL.GL_TRIANGLES, 36, GL.GL_UNSIGNED_INT, 0, k[3]);
            }
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    public String info() { return info + String.format(Locale.GERMAN, " · %d Stücke im Bild", drawnChunks); }
    public int triangles() { return verts / 3 + instances * 12; }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        gl.glDeleteVertexArrays(2, new int[]{vao, boxVao}, 0);
        gl.glDeleteBuffers(4, new int[]{vbo, boxVbo, boxIbo, instVbo}, 0);
        program = vao = vbo = verts = 0;
    }

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(program, n); }
    private void set3(GL4 gl, String n, double[] v) { gl.glUniform3f(loc(gl, n), (float) v[0], (float) v[1], (float) v[2]); }

    /** Liest eine Shader-Datei aus diesem Package (Ressource neben der Klasse). */
    public static String source(String file) {
        try (InputStream in = RailGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("rail: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("rail: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("rail: " + file + " lässt sich nicht übersetzen: " + log(gl, sh, false));
        return sh;
    }

    private static String log(GL4 gl, int o, boolean prog) {
        byte[] b = new byte[4096];
        int[] n = new int[1];
        if (prog) gl.glGetProgramInfoLog(o, b.length, n, 0, b, 0);
        else gl.glGetShaderInfoLog(o, b.length, n, 0, b, 0);
        return new String(b, 0, n[0], StandardCharsets.UTF_8);
    }
}
