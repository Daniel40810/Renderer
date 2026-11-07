package com.dan.forest.gl;

import com.dan.forest.Season;
import com.dan.forest.TreeFall;
import com.dan.forest.TreeMesh;
import com.dan.forest.TreeModel;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Fallende Bäume auf der Grafikkarte (aus LituyaBay1958, Werkbuch Welle W5, E11 „524 m“; {@link TreeFall}) [G]. Die App
 * reicht je Bild die Bäume, die gerade fallen ({@link #set}, je Baum {@link TreeFall#FL} Werte), gezeichnet wird jeder mit
 * dem Netz seines Typs aus {@link ForestGpu}: nah die mittlere Stufe mit gelichtetem Laub (wie Stufe 2 in forest), fern
 * die Silhouette (10 Seiten, 12 Ringe), über {@link #coneFrom} m ein Kegel (18 Dreiecke), liegende Stämme ohne Krone als
 * geschälter Stab. Instanziert je Typ und Stufe; höchstens {@link #NEAR_MAX} Bäume nah (Lituya W5: mit dem vollen Netz für
 * alle nahen Bäume bis 254 ms je Bild).
 * <p>
 * <b>Licht.</b> Die Shader rufen den Lichtvertrag aus {@code treefall_light.glsl} (lightSun, lightSky, lightAerial; uVP,
 * uCam, uSun). Vorgabe: {@link ForestGl.Light}. Ein Wirt mit eigenem Licht setzt vor {@link #init} {@link #hostLight}
 * (GLSL) und {@link #hostUniforms} (Programm gebunden); dann setzt dieses Package nur uEventTime und uCurvature.
 */
public final class TreeFallGl {
    /** Werte je Baum (wie {@link TreeFall#FL}). */
    static final int FL = TreeFall.FL;
    /**
     * Bis hierher (m) das Netz der mittleren Stufe, dahinter die Silhouette; höchstens {@link #NEAR_MAX} Bäume nah (LituyaBay1958
     * W5: mit 450 m und ohne Grenze fielen um die Aufnahmen am Sporn bis 9 Mio. Dreiecke, bis 250 ms je Bild; mit 700 Bäumen bis 220 m noch 16 Mio., das Netz der mittleren Stufe hat rund 20 000 Dreiecke).
     */
    public static final double NEAR = 150;
    public static final int NEAR_MAX = 120;
    /** Ab hier (m) ein Kegel statt der Silhouette (ein Baum deckt dort mit 50 mm rund 10–20 Bildpunkte). */
    public static final double CONE_FROM = 1500;
    /** Ab hier (m) Kegel, bis hier (m) das Netz der mittleren Stufe (Lituya W6 Runde 6: für Zeitproben änderbar). */
    public volatile double coneFrom = CONE_FROM, near = NEAR;
    /** Höchstens so viele fallende Bäume. */
    public static final int MAX = 150000;
    /** Weiter als hier (m) wird kein fallender Baum gezeichnet. */
    public static final double RANGE = 3000;
    /** Wirt: GLSL für treefall_light.glsl (null: Vorgabe) und wer dessen Uniforms setzt. */
    public String hostLight;
    public IntConsumer hostUniforms;

    private int prog;
    private int types;
    private int[] vao, vbo, ibo, nIdx, ibuf, icap;
    private boolean gebaut;
    private int tag = -1;
    private float[] baeume = new float[FL * 1024];
    private int n;
    private float[][] bin;
    private int[] binN;
    private FloatBuffer fb;
    public String info = "Bäume fallen: –";
    public int drawn;

    /** Programm bauen (vor dem ersten Zeichnen). */
    public void init(GL4 gl) { prog = program(gl, "treefall.vert", "treefall.frag"); }

    /** Neue Liste: b mit n Bäumen zu {@link TreeFall#FL} Werten ({@link TreeFall#put}). */
    public void set(float[] b, int n) { this.baeume = b; this.n = n; }

    /** Zahl der Bäume in der Liste. */
    public int count() { return n; }

    /** Die Netze der stehenden Typen aus forest (einmal, und neu bei einem anderen Tag der Jahreszeit). */
    private void bauen(GL4 gl, ForestGpu f, int day) {
        int T = f.species() * f.variants();   // stehende Typen
        if (!gebaut) {
            types = T;
            int B = 2 * T + 2;   // nah je Typ, fern je Typ, ein Stamm, ein Kegel (Runde 4: über 1,5 km)
            vao = new int[B]; vbo = new int[B]; ibo = new int[B]; nIdx = new int[B]; ibuf = new int[B]; icap = new int[B];
            gl.glGenVertexArrays(B, vao, 0);
            gl.glGenBuffers(B, vbo, 0);
            gl.glGenBuffers(B, ibo, 0);
            gl.glGenBuffers(B, ibuf, 0);
            bin = new float[B][FL * 256];
            binN = new int[B];
            netz(gl, 2 * T, stamm());
            netz(gl, 2 * T + 1, kegel());
        }
        for (int t = 0; t < T; t++) {
            TreeModel m = f.model(t);
            Season se = Season.of(m.species, day, 0f);
            float[][] nah = ForestGl.pack(TreeMesh.build(m, 1, 2), se, 1958L + t * 31L + 2, 0.15f, 2.0f);
            // fern: Silhouette mit 10 Seiten und 12 Ringen statt 6 × 7 (Runde 3: grobe Kegel in der Ferne)
            float[][] fern = ForestGl.pack(TreeMesh.silhouette(m, 10, 12), se, 1958L + t * 31L + 3, 1f, 1f);
            netz(gl, t, nah);
            netz(gl, T + t, fern);
        }
        gebaut = true;
        tag = day;
    }

    /**
     * Runde 3: ein Stamm ohne Krone (Höhe 1, Halbmesser 0,011 der Höhe, sechs Seiten, Holz geschält und nass), für
     * umgelegte und fortgerissene Bäume, sobald die Krone fort ist: liegt längs des Hangs, Maßstab = Höhe des Baums.
     */
    static float[][] stamm() {
        int S = 6;
        float[] v = new float[(S + 1) * 2 * 11];
        float[] tri = new float[S * 6];
        for (int k = 0; k <= S; k++) {
            double a = 2 * Math.PI * k / S;
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            for (int e = 0; e < 2; e++) {
                int o = ((k * 2) + e) * 11;
                float r = e == 0 ? 0.011f : 0.006f;
                v[o] = cx * r; v[o + 1] = e; v[o + 2] = cz * r;
                v[o + 3] = cx; v[o + 4] = 0; v[o + 5] = cz;
                v[o + 6] = 0.42f; v[o + 7] = 0.34f; v[o + 8] = 0.25f;
                v[o + 9] = e; v[o + 10] = 0;
            }
        }
        int q = 0;
        for (int k = 0; k < S; k++) {
            int a = k * 2, b = a + 1, c = a + 2, d = a + 3;
            // gegen den Uhrzeigersinn von außen gesehen
            tri[q++] = Float.intBitsToFloat(a); tri[q++] = Float.intBitsToFloat(b); tri[q++] = Float.intBitsToFloat(c);
            tri[q++] = Float.intBitsToFloat(b); tri[q++] = Float.intBitsToFloat(d); tri[q++] = Float.intBitsToFloat(c);
        }
        return new float[][]{v, tri};
    }

    /**
     * Runde 4: ein Nadelbaum aus der Ferne als Kegel (Höhe 1, Krone ab 0,22 der Höhe, Halbmesser 0,17 unten; sechs Seiten,
     * dazu der Stamm als Strich), 18 Dreiecke statt rund 240 der Silhouette. Laub = 1, damit Krone und Licht wie beim Baum.
     */
    static float[][] kegel() {
        int S = 6;
        java.util.List<float[]> v = new java.util.ArrayList<>();
        java.util.List<Integer> t = new java.util.ArrayList<>();
        float ru = 0.17f, y0 = 0.22f;
        for (int k = 0; k < S; k++) {
            double a0 = 2 * Math.PI * k / S, a1 = 2 * Math.PI * (k + 1) / S, am = (a0 + a1) / 2;
            float nx = (float) (Math.cos(am) * 0.86), ny = 0.5f, nz = (float) (Math.sin(am) * 0.86);
            int b = v.size();
            v.add(new float[]{(float) Math.cos(a0) * ru, y0, (float) Math.sin(a0) * ru, nx, ny, nz, 0.035f, 0.060f, 0.040f, y0, 1});
            v.add(new float[]{(float) Math.cos(a1) * ru, y0, (float) Math.sin(a1) * ru, nx, ny, nz, 0.035f, 0.060f, 0.040f, y0, 1});
            v.add(new float[]{0, 1, 0, nx, ny, nz, 0.045f, 0.075f, 0.048f, 1, 1});
            t.add(b); t.add(b + 2); t.add(b + 1);
            // Boden der Krone
            int c = v.size();
            v.add(new float[]{(float) Math.cos(a0) * ru, y0, (float) Math.sin(a0) * ru, 0, -1, 0, 0.02f, 0.035f, 0.024f, y0, 1});
            v.add(new float[]{(float) Math.cos(a1) * ru, y0, (float) Math.sin(a1) * ru, 0, -1, 0, 0.02f, 0.035f, 0.024f, y0, 1});
            v.add(new float[]{0, y0, 0, 0, -1, 0, 0.02f, 0.035f, 0.024f, y0, 1});
            t.add(c); t.add(c + 1); t.add(c + 2);
        }
        // Stamm als schmaler Keil
        int c = v.size();
        v.add(new float[]{-0.012f, 0, 0, 0, 0, 1, 0.12f, 0.09f, 0.07f, 0, 0});
        v.add(new float[]{0.012f, 0, 0, 0, 0, 1, 0.12f, 0.09f, 0.07f, 0, 0});
        v.add(new float[]{0, y0 + 0.02f, 0, 0, 0, 1, 0.12f, 0.09f, 0.07f, y0, 0});
        t.add(c); t.add(c + 1); t.add(c + 2);
        float[] vv = new float[v.size() * 11];
        for (int i = 0; i < v.size(); i++) System.arraycopy(v.get(i), 0, vv, i * 11, 11);
        float[] tt = new float[t.size()];
        for (int i = 0; i < t.size(); i++) tt[i] = Float.intBitsToFloat(t.get(i));
        return new float[][]{vv, tt};
    }

    private void netz(GL4 gl, int b, float[][] m) {
        float[] v = m[0], tf = m[1];
        int[] tri = new int[tf.length];
        for (int i = 0; i < tf.length; i++) tri[i] = Float.floatToRawIntBits(tf[i]);
        gl.glBindVertexArray(vao[b]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[b]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4L, v.length * 4L), Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        int st = 11 * 4;
        gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
        gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
        gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, st, 24);
        gl.glEnableVertexAttribArray(3); gl.glVertexAttribPointer(3, 2, GL.GL_FLOAT, false, st, 36);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[b]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, Math.max(4L, tri.length * 4L), Buffers.newDirectIntBuffer(tri), GL.GL_STATIC_DRAW);
        nIdx[b] = tri.length;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ibuf[b]);
        for (int a = 0; a < 4; a++) {
            gl.glEnableVertexAttribArray(4 + a);
            gl.glVertexAttribPointer(4 + a, 4, GL.GL_FLOAT, false, FL * 4, a * 16L);
            gl.glVertexAttribDivisor(4 + a, 1);
        }
        gl.glBindVertexArray(0);
    }

    /** Die Netze gleich bauen, wenn forest bereit ist (sonst im ersten Bild, in dem Bäume fallen: ein Ruck von rund 0,5 s). */
    public void prepare(GL4 gl, ForestGpu f, int day) {
        if (gebaut && day == tag) return;
        try { bauen(gl, f, day); } catch (RuntimeException e) { info = "Bäume fallen: " + e; }
    }

    /**
     * Zeichnet die fallenden Bäume (HDR-Ziel mit Tiefe gebunden). day: Tag im Jahr (Laub), eye: Auge, vp: Blick (Spalten
     * zuerst), t: Ereigniszeit (s), curvature: Erdkrümmung (Absenkung r² · curvature), light: Vorgabe ohne Wirt.
     */
    public void draw(GL4 gl, ForestGpu f, int day, double[] eye, float[] vp, double t, float curvature, ForestGl.Light light) {
        drawn = 0;
        if (n == 0 || f == null || !f.ready()) { info = "Bäume fallen: keine"; return; }
        try {
            if (!gebaut || day != tag) bauen(gl, f, day);
        } catch (RuntimeException e) {
            info = "Bäume fallen: " + e;
            n = 0;
            return;
        }
        java.util.Arrays.fill(binN, 0);
        int T = types, nah = 0;
        final double kegelM = coneFrom, nahM = this.near;
        double[] pl = ebenen(vp);
        for (int k = 0; k < n; k++) {
            int o = k * FL;
            int typ = (int) baeume[o + 15];
            if (typ < 0 || typ >= T) continue;
            double dx = baeume[o] - eye[0], dz = baeume[o + 2] - eye[2], d2 = dx * dx + dz * dz;
            if (d2 > RANGE * RANGE) continue;
            // Runde 3: Sichtkegel (Kugel um den Baum, Halbmesser seine Höhe)
            double H = baeume[o + 14], cy = baeume[o + 1] + 0.5 * H;
            boolean drin = true;
            for (int e = 0; e < 4 && drin; e++) drin = pl[4 * e] * baeume[o] + pl[4 * e + 1] * cy + pl[4 * e + 2] * baeume[o + 2] + pl[4 * e + 3] >= -H;
            if (!drin) continue;
            int folge = (int) baeume[o + 12];
            boolean liegt = (folge == 3 || folge == 4) && t > baeume[o + 6] + baeume[o + 11] + 2.6;
            boolean nh = !liegt && d2 < nahM * nahM && nah < NEAR_MAX;
            if (nh) nah++;
            boolean weit = !liegt && d2 > kegelM * kegelM;
            int b = liegt ? 2 * T : weit ? 2 * T + 1 : nh ? typ : T + typ;
            if (FL * (binN[b] + 1) > bin[b].length) bin[b] = java.util.Arrays.copyOf(bin[b], bin[b].length * 2);
            System.arraycopy(baeume, o, bin[b], FL * binN[b], FL);
            if (liegt || weit) bin[b][FL * binN[b] + 4] = (float) H;   // Stamm und Kegel haben die Höhe 1
            binN[b]++;
        }
        gl.glUseProgram(prog);
        if (hostUniforms != null) hostUniforms.accept(prog);
        else {
            ForestGl.Light l = light != null ? light : new ForestGl.Light();
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, vp, 0);
            gl.glUniform3f(gl.glGetUniformLocation(prog, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
            gl.glUniform3f(gl.glGetUniformLocation(prog, "uSun"), (float) l.sunDir[0], (float) l.sunDir[1], (float) l.sunDir[2]);
            gl.glUniform3f(gl.glGetUniformLocation(prog, "uLightSun"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
            gl.glUniform3f(gl.glGetUniformLocation(prog, "uLightSky"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
            gl.glUniform3f(gl.glGetUniformLocation(prog, "uLightFog"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
            gl.glUniform1f(gl.glGetUniformLocation(prog, "uLightFogDist"), (float) l.fogDistance);
        }
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uEventTime"), (float) t);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uCurvature"), curvature);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDepthMask(true);
        long tri = 0;
        for (int b = 0; b < 2 * T + 2; b++) {
            int m = binN[b];
            if (m == 0 || nIdx[b] == 0) continue;
            if (fb == null || fb.capacity() < m * FL) fb = Buffers.newDirectFloatBuffer(Math.max(m * FL, FL * 4096));
            fb.clear();
            fb.put(bin[b], 0, m * FL).flip();
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ibuf[b]);
            long bytes = (long) m * FL * 4;
            if (bytes > icap[b]) { gl.glBufferData(GL.GL_ARRAY_BUFFER, bytes * 2, null, GL.GL_DYNAMIC_DRAW); icap[b] = (int) (bytes * 2); }
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, bytes, fb);
            gl.glBindVertexArray(vao[b]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, nIdx[b], GL.GL_UNSIGNED_INT, 0, m);
            tri += (long) nIdx[b] / 3 * m;
            drawn += m;
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        info = String.format(Locale.GERMAN, "Bäume fallen: %,d, %.1f Mio. Dreiecke", drawn, tri / 1e6);
    }

    /** Die vier seitlichen Ebenen des Blicks (links, rechts, unten, oben) aus vp (Spalten zuerst), normiert. */
    static double[] ebenen(float[] m) {
        double[] p = new double[16];
        for (int e = 0; e < 4; e++) {
            int r = e / 2, sg = e % 2 == 0 ? 1 : -1;   // Zeile 0 (x) für links/rechts, Zeile 1 (y) für unten/oben
            double a = m[3] + sg * m[r], b = m[7] + sg * m[4 + r], c = m[11] + sg * m[8 + r], d = m[15] + sg * m[12 + r];
            double l = Math.sqrt(a * a + b * b + c * c);
            p[4 * e] = a / l; p[4 * e + 1] = b / l; p[4 * e + 2] = c / l; p[4 * e + 3] = d / l;
        }
        return p;
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        prog = 0;
        if (!gebaut) return;
        gl.glDeleteVertexArrays(vao.length, vao, 0);
        gl.glDeleteBuffers(vbo.length, vbo, 0);
        gl.glDeleteBuffers(ibo.length, ibo, 0);
        gl.glDeleteBuffers(ibuf.length, ibuf, 0);
        gebaut = false;
    }

    // ------------------------------------------------------------------ Shader laden

    /** Quelltext mit eingesetzten Bausteinen (aus diesem Package; treefall_light.glsl vom Wirt, wenn gesetzt). */
    public String source(String file) {
        StringBuilder sb = new StringBuilder("#version 430 core\n");
        expand(file, sb, new HashSet<>());
        return sb.toString();
    }

    private void expand(String file, StringBuilder sb, Set<String> seen) {
        if (!seen.add(file)) return;
        String text = file.equals("treefall_light.glsl") && hostLight != null ? hostLight : read(file);
        for (String l : text.split("\r?\n", -1)) {
            String t = l.trim();
            if (t.startsWith("#include")) expand(t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')), sb, seen);
            else if (!t.startsWith("#version")) sb.append(l).append('\n');
        }
    }

    private static String read(String file) {
        try (InputStream in = TreeFallGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("forest: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("forest: " + file + ": " + e.getMessage(), e);
        }
    }

    private int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int[] sh = new int[2];
        String[] f = {vert, frag};
        for (int i = 0; i < 2; i++) {
            sh[i] = gl.glCreateShader(i == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh[i], 1, new String[]{source(f[i])}, null, 0);
            gl.glCompileShader(sh[i]);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh[i], GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                int[] len = new int[1];
                gl.glGetShaderiv(sh[i], GL4.GL_INFO_LOG_LENGTH, len, 0);
                byte[] log = new byte[Math.max(1, len[0])];
                gl.glGetShaderInfoLog(sh[i], log.length, null, 0, log, 0);
                throw new IllegalStateException("forest: " + f[i] + " lässt sich nicht übersetzen:\n" + new String(log, StandardCharsets.UTF_8));
            }
            gl.glAttachShader(p, sh[i]);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        for (int x : sh) gl.glDeleteShader(x);
        if (ok[0] == 0) throw new IllegalStateException("forest: " + vert + " + " + frag + " lässt sich nicht binden");
        return p;
    }
}
