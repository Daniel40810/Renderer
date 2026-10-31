package com.dan.beachchair.gl;

import com.dan.beachchair.BeachChair;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Darstellung der Strandkörbe mit JOGL (Paketvertrag Punkt 3), seit Timmendorf:
 * <ul>
 * <li><b>Körbe</b> ({@code beachchair.vert}, {@code beachchair.frag}): je Detailstufe ein Aufruf mit allen Körben dieser
 * Stufe (nah, mittel, fern nach dem Abstand vom Auge). Stellung und Farbe je Korb kommen als Werte je Instanz; die
 * Lehne kippt, die Fußstützen gleiten und der Verschluss erscheint im Shader.</li>
 * <li><b>Schatten</b> ({@code shadow.vert}, {@code shadow.frag}): der Umriss jedes nahen Korbs mit der Sonne auf den
 * Boden geworfen (konvexe Hülle, weicher Rand), multiplikativ: dem Sand fehlt dort das Sonnenlicht.</li>
 * <li><b>Bühne</b> ({@code stage.vert}, {@code stage.frag}): Sand und See nur für die Vorführung.</li>
 * </ul>
 * Licht und Blick kommen als Zahlen; die Ausgabe ist linear und vorbelichtet, mit {@link Light#display} &gt; 0 gleich fertig
 * für den Bildschirm (Filmkurve und Gamma, für die Vorführung). Die Tiefenprüfung stellt der Aufrufer ein (auch
 * umgekehrtes z).
 */
public final class BeachChairGl {
    /** Licht: Richtung zur Sonne und zum Mond, ihre Farben, Himmel oben und am Horizont (linear, vorbelichtet), Dunst. */
    public static final class Light {
        public double[] sunDir = {0.35, 0.62, -0.70}, sunColor = {3.0, 2.85, 2.6}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyTop = {0.30, 0.42, 0.62}, skyHorizon = {0.55, 0.65, 0.78}, fogColor = {0.62, 0.70, 0.80};
        public double fogDistance = 6000, display;
    }

    /** Blick: Matrix (Spalten zuerst), Auge, Erdhalbmesser für die Krümmung (0 = flach). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = {0, 1.7, 5};
        public double earthRadius;
    }

    /** Höhe des Bodens für die Schatten (eigene Schnittstelle, Paketvertrag Punkt 4). */
    @FunctionalInterface
    public interface Ground { double height(double x, double z); }

    /** Grenzen der Detailstufen und Reichweite (m); aus der Qualitätsstufe des Korbs, offen änderbar. */
    public double nearDistance, midDistance, maxDistance, shadowDistance;
    /** Schatten werfen (nur nahe Körbe, bis {@link #shadowDistance}). */
    public boolean shadows = true;
    /** Neu sortieren, wenn das Auge so weit gewandert ist (m). */
    public double rebinStep = 2;

    private BeachChair chair;
    private final BeachChair.Mesh[] meshes = new BeachChair.Mesh[3];
    private List<BeachChair.Chair> chairs = List.of();
    private boolean dirty = true;
    private double ex = Double.NaN, ez, sunKey = Double.NaN;
    private int prog, shadowProg, stageProg, instVbo, instCap, shVao, shVbo, shCap, shCount, stVao, stVbo;
    private final int[] vao = new int[3], vbo = new int[3];
    private final int[] count = new int[3], base = new int[3];
    private float[] inst = new float[0], sh = new float[0];
    private final List<BeachChair.Chair> nahe = new ArrayList<>();
    private String info = "";

    public String info() { return info; }

    public BeachChair chair() { return chair; }

    /** Programme und Netze anlegen. */
    public void init(GL4 gl, BeachChair c) {
        prog = link(gl, "beachchair.vert", "beachchair.frag");
        shadowProg = link(gl, "shadow.vert", "shadow.frag");
        gl.glGenVertexArrays(3, vao, 0);
        gl.glGenBuffers(3, vbo, 0);
        int[] b = new int[1];
        gl.glGenBuffers(1, b, 0);
        instVbo = b[0];
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        shVao = v[0];
        gl.glGenBuffers(1, b, 0);
        shVbo = b[0];
        gl.glBindVertexArray(shVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, shVbo);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 16, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 1, GL.GL_FLOAT, false, 16, 12);
        gl.glBindVertexArray(0);
        setChair(gl, c);
    }

    /** Anderer Korb (Bauart, Maße, Qualität): Netze neu, Reichweiten aus der Qualität. */
    public void setChair(GL4 gl, BeachChair c) {
        chair = c;
        switch (c.quality) {
            case LOW -> { nearDistance = 20; midDistance = 120; maxDistance = 2500; shadowDistance = 60; }
            case MEDIUM -> { nearDistance = 35; midDistance = 220; maxDistance = 4000; shadowDistance = 100; }
            case HIGH -> { nearDistance = 50; midDistance = 350; maxDistance = 6000; shadowDistance = 150; }
            case ULTRA -> { nearDistance = 80; midDistance = 500; maxDistance = 9000; shadowDistance = 250; }
        }
        BeachChair.Lod[] lods = BeachChair.Lod.values();
        int stride = BeachChair.STRIDE * 4;
        for (int i = 0; i < 3; i++) {
            BeachChair.Mesh m = c.mesh(lods[i]);
            meshes[i] = m;
            gl.glBindVertexArray(vao[i]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[i]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) m.data.length * 4, FloatBuffer.wrap(m.data), GL.GL_STATIC_DRAW);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride, 0);
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, stride, 12);
            gl.glEnableVertexAttribArray(2);
            gl.glVertexAttribPointer(2, 2, GL.GL_FLOAT, false, stride, 24);
            gl.glEnableVertexAttribArray(3);
            gl.glVertexAttribPointer(3, 3, GL.GL_FLOAT, false, stride, 32);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instVbo);
            for (int a = 0; a < 3; a++) {
                gl.glEnableVertexAttribArray(4 + a);
                gl.glVertexAttribPointer(4 + a, 4, GL.GL_FLOAT, false, 48, a * 16L);
                gl.glVertexAttribDivisor(4 + a, 1);
            }
        }
        gl.glBindVertexArray(0);
        dirty = true;
        info = String.format(Locale.GERMAN, "Strandkörbe: %s (beachchair.vert/frag, Schatten shadow.vert/frag)", c.describe());
    }

    /** Die Körbe der Szene (die Liste wird behalten; nach Änderungen an Stellungen {@link #touch()}). */
    public void setChairs(List<BeachChair.Chair> list) {
        chairs = list == null ? List.of() : list;
        dirty = true;
    }

    /** Stellungen oder Farben haben sich geändert: beim nächsten Bild neu hochladen. */
    public void touch() { dirty = true; }

    /** Körbe nach dem Abstand vom Auge auf die drei Stufen verteilen und hochladen (nur wenn nötig). */
    private void rebin(GL4 gl, double[] eye) {
        if (!dirty && Math.hypot(eye[0] - ex, eye[2] - ez) < rebinStep) return;
        dirty = false;
        ex = eye[0];
        ez = eye[2];
        sunKey = Double.NaN;
        int n = chairs.size();
        if (inst.length < n * 12) inst = new float[Math.max(n, 64) * 12];
        int[] lod = new int[n];
        Arrays.fill(count, 0);
        double n2 = nearDistance * nearDistance, m2 = midDistance * midDistance, x2 = maxDistance * maxDistance;
        nahe.clear();
        for (int i = 0; i < n; i++) {
            BeachChair.Chair c = chairs.get(i);
            double dx = c.x - eye[0], dy = c.y - eye[1], dz = c.z - eye[2], d2 = dx * dx + dy * dy + dz * dz;
            lod[i] = d2 < n2 ? 0 : d2 < m2 ? 1 : d2 < x2 ? 2 : -1;
            if (lod[i] >= 0) count[lod[i]]++;
            if (d2 < shadowDistance * shadowDistance) nahe.add(c);
        }
        base[0] = 0;
        base[1] = count[0];
        base[2] = count[0] + count[1];
        int[] at = base.clone();
        for (int i = 0; i < n; i++) {
            if (lod[i] < 0) continue;
            BeachChair.Chair c = chairs.get(i);
            int o = at[lod[i]]++ * 12;
            inst[o] = (float) c.x; inst[o + 1] = (float) c.y; inst[o + 2] = (float) c.z; inst[o + 3] = (float) Math.toRadians(c.headingDeg);
            inst[o + 4] = (float) c.stripe[0]; inst[o + 5] = (float) c.stripe[1]; inst[o + 6] = (float) c.stripe[2];
            inst[o + 7] = (float) ((c.id * 0.6180339887) % 1.0);
            inst[o + 8] = (float) chair.reclineRad(c.recline);
            inst[o + 9] = (float) (chair.footrestTravel * Math.max(0, Math.min(1, c.footrest)));
            inst[o + 10] = c.closed ? 1 : 0;
            inst[o + 11] = 0;
        }
        int total = count[0] + count[1] + count[2];
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instVbo);
        if (instCap < total * 12 || instCap == 0) {
            instCap = Math.max(total, 64) * 12;
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) instCap * 4, null, GL.GL_DYNAMIC_DRAW);
        }
        if (total > 0) gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) total * 12 * 4, FloatBuffer.wrap(inst, 0, total * 12));
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /** Die Körbe zeichnen (undurchsichtig, mit Tiefe). */
    public void draw(GL4 gl, View v, Light l) {
        if (chairs.isEmpty()) return;
        rebin(gl, v.eye);
        if (count[0] + count[1] + count[2] == 0) return;
        gl.glUseProgram(prog);
        common(gl, prog, v, l);
        double[] h = chair.hinge(), op = chair.opening();
        gl.glUniform2f(loc(gl, prog, "uHinge"), (float) h[0], (float) h[1]);
        gl.glUniform3f(loc(gl, prog, "uWicker"), (float) chair.wicker[0], (float) chair.wicker[1], (float) chair.wicker[2]);
        gl.glUniform3f(loc(gl, prog, "uWood"), (float) chair.wood[0], (float) chair.wood[1], (float) chair.wood[2]);
        gl.glUniform3f(loc(gl, prog, "uStripeLight"), (float) chair.stripeLight[0], (float) chair.stripeLight[1], (float) chair.stripeLight[2]);
        gl.glUniform3f(loc(gl, prog, "uRope"), (float) chair.rope[0], (float) chair.rope[1], (float) chair.rope[2]);
        gl.glUniform1f(loc(gl, prog, "uStripe"), (float) chair.stripeWidth);
        gl.glUniform4f(loc(gl, prog, "uOpening"), (float) chair.innerHalf(), (float) chair.baseTop(), (float) op[0], (float) op[1]);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_CULL_FACE);
        for (int i = 0; i < 3; i++) {
            if (count[i] == 0) continue;
            gl.glBindVertexArray(vao[i]);
            gl.glDrawArraysInstancedBaseInstance(GL.GL_TRIANGLES, 0, meshes[i].vertices, count[i], base[i]);
        }
        gl.glBindVertexArray(0);
    }

    /**
     * Die Schatten der nahen Körbe auf den Boden (vor {@link #draw}, nach dem Boden): Umriss mit der Sonne geworfen,
     * multiplikativ, ohne Tiefe zu schreiben. {@code ground} darf null sein (dann eben in Höhe des Korbs).
     */
    public void drawShadows(GL4 gl, View v, Light l, Ground ground) {
        if (!shadows || chairs.isEmpty()) return;
        rebin(gl, v.eye);
        double[] s = l.sunDir;
        double sl = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
        if (sl < 1e-9 || s[1] / sl < 0.03 || nahe.isEmpty()) return;
        double sx = s[0] / sl, sy = s[1] / sl, sz = s[2] / sl;
        double key = sx * 1000 + sy * 100 + sz;
        if (Double.isNaN(sunKey) || Math.abs(key - sunKey) > 0.02) {
            sunKey = key;
            buildShadows(gl, sx, Math.max(sy, 0.06), sz, ground);
        }
        if (shCount == 0) return;
        // Faktor je Farbe: der Himmel allein gegen Himmel und Sonne auf ebenem Boden
        double[] f = new double[3];
        for (int k = 0; k < 3; k++) {
            double sky = l.skyTop[k], sun = l.sunColor[k] * sy / Math.PI;
            f[k] = sky + sun < 1e-9 ? 1 : Math.max(0.12, sky / (sky + sun));
        }
        gl.glUseProgram(shadowProg);
        gl.glUniformMatrix4fv(loc(gl, shadowProg, "uViewProj"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, shadowProg, "uEye"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1f(loc(gl, shadowProg, "uEarthR"), (float) v.earthRadius);
        gl.glUniform3f(loc(gl, shadowProg, "uFactor"), (float) f[0], (float) f[1], (float) f[2]);
        gl.glUniform1f(loc(gl, shadowProg, "uFar"), (float) shadowDistance);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_DST_COLOR, GL.GL_ZERO);
        gl.glBindVertexArray(shVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, shCount);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
    }

    /** Je naher Korb: Umriss werfen, konvexe Hülle, Fächer innen (voll) und ein Saum nach außen (weich). */
    private void buildShadows(GL4 gl, double sx, double sy, double sz, Ground ground) {
        int verts = 0;
        float[] buf = sh;
        int o = 0;
        for (BeachChair.Chair c : nahe) {
            double[][] loc = chair.silhouette(c);
            double a = Math.toRadians(c.headingDeg), fx = Math.sin(a), fz = -Math.cos(a), rx = Math.cos(a), rz = Math.sin(a);
            double[][] pts = new double[loc.length][];
            for (int i = 0; i < loc.length; i++) {
                double[] p = loc[i];
                double wx = c.x + p[0] * rx + p[2] * fx, wy = c.y + p[1], wz = c.z + p[0] * rz + p[2] * fz;
                double g = c.y, t = (wy - g) / sy;
                double qx = wx - sx * t, qz = wz - sz * t;
                if (ground != null) {
                    g = ground.height(qx, qz);
                    t = Math.max(0, (wy - g) / sy);
                    qx = wx - sx * t;
                    qz = wz - sz * t;
                }
                pts[i] = new double[]{qx, qz};
            }
            double[][] hull = hull(pts);
            if (hull.length < 3) continue;
            double cx = 0, cz = 0;
            for (double[] p : hull) { cx += p[0]; cz += p[1]; }
            cx /= hull.length; cz /= hull.length;
            int need = hull.length * 18 * 4;
            if (buf.length < o + need) buf = Arrays.copyOf(buf, Math.max(buf.length * 2, o + need + 4096));
            double cy = (ground != null ? ground.height(cx, cz) : c.y) + 0.025;
            for (int i = 0; i < hull.length; i++) {
                double[] p = hull[i], q = hull[(i + 1) % hull.length];
                double py = (ground != null ? ground.height(p[0], p[1]) : c.y) + 0.025, qy = (ground != null ? ground.height(q[0], q[1]) : c.y) + 0.025;
                // Saum: 6 cm nach außen, vom Mittelpunkt weg
                double[] po = grow(p, cx, cz, 0.06), qo = grow(q, cx, cz, 0.06);
                o = put(buf, o, cx, cy, cz, 1); o = put(buf, o, p[0], py, p[1], 1); o = put(buf, o, q[0], qy, q[1], 1);
                o = put(buf, o, p[0], py, p[1], 1); o = put(buf, o, po[0], py, po[1], 0); o = put(buf, o, qo[0], qy, qo[1], 0);
                o = put(buf, o, p[0], py, p[1], 1); o = put(buf, o, qo[0], qy, qo[1], 0); o = put(buf, o, q[0], qy, q[1], 1);
                verts += 9;
            }
        }
        sh = buf;
        shCount = verts;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, shVbo);
        if (shCap < o) {
            shCap = Math.max(o, 4096);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) shCap * 4, null, GL.GL_DYNAMIC_DRAW);
        }
        if (o > 0) gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) o * 4, FloatBuffer.wrap(buf, 0, o));
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    private static int put(float[] b, int o, double x, double y, double z, double fade) {
        b[o] = (float) x; b[o + 1] = (float) y; b[o + 2] = (float) z; b[o + 3] = (float) fade;
        return o + 4;
    }

    private static double[] grow(double[] p, double cx, double cz, double d) {
        double dx = p[0] - cx, dz = p[1] - cz, l = Math.hypot(dx, dz);
        return l < 1e-9 ? p : new double[]{p[0] + dx / l * d, p[1] + dz / l * d};
    }

    /** Konvexe Hülle in der Ebene (Andrew), gegen den Uhrzeigersinn. */
    static double[][] hull(double[][] p) {
        double[][] s = p.clone();
        Arrays.sort(s, (a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        int n = s.length, k = 0;
        if (n < 3) return s;
        double[][] h = new double[2 * n][];
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], s[i]) <= 0) k--;
            h[k++] = s[i];
        }
        for (int i = n - 2, t = k + 1; i >= 0; i--) {
            while (k >= t && cross(h[k - 2], h[k - 1], s[i]) <= 0) k--;
            h[k++] = s[i];
        }
        return Arrays.copyOf(h, Math.max(0, k - 1));
    }

    private static double cross(double[] o, double[] a, double[] b) { return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]); }

    // ------------------------------------------------------------------ Bühne (Vorführung)

    /** Sand von der Wasserlinie (z = −shore) landwärts, dahinter die See: nur für Vorführung und Standbilder. */
    public void drawStage(GL4 gl, View v, Light l, double shore) {
        if (stageProg == 0) {
            stageProg = link(gl, "stage.vert", "stage.frag");
            int[] a = new int[1];
            gl.glGenVertexArrays(1, a, 0);
            stVao = a[0];
            gl.glGenBuffers(1, a, 0);
            stVbo = a[0];
            float s = (float) -shore, far = 30000;
            float[] q = {-far, 0, s, far, 0, s, far, 0, far, -far, 0, s, far, 0, far, -far, 0, far,
                    -far, -0.02f, -far, far, -0.02f, -far, far, -0.02f, s, -far, -0.02f, -far, far, -0.02f, s, -far, -0.02f, s};
            gl.glBindVertexArray(stVao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, stVbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) q.length * 4, FloatBuffer.wrap(q), GL.GL_STATIC_DRAW);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
            gl.glBindVertexArray(0);
        }
        gl.glUseProgram(stageProg);
        common(gl, stageProg, v, l);
        gl.glUniform1f(loc(gl, stageProg, "uShore"), (float) shore);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(stVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 12);
        gl.glBindVertexArray(0);
    }

    private void common(GL4 gl, int p, View v, Light l) {
        gl.glUniformMatrix4fv(loc(gl, p, "uViewProj"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, p, "uEye"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1f(loc(gl, p, "uEarthR"), (float) v.earthRadius);
        double[] s = l.sunDir, m = l.moonDir;
        double sl = Math.max(1e-9, Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2])), ml = Math.max(1e-9, Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]));
        gl.glUniform3f(loc(gl, p, "uSunDir"), (float) (s[0] / sl), (float) (s[1] / sl), (float) (s[2] / sl));
        gl.glUniform3f(loc(gl, p, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(loc(gl, p, "uMoonDir"), (float) (m[0] / ml), (float) (m[1] / ml), (float) (m[2] / ml));
        gl.glUniform3f(loc(gl, p, "uMoonColor"), (float) l.moonColor[0], (float) l.moonColor[1], (float) l.moonColor[2]);
        gl.glUniform3f(loc(gl, p, "uSkyTop"), (float) l.skyTop[0], (float) l.skyTop[1], (float) l.skyTop[2]);
        gl.glUniform3f(loc(gl, p, "uSkyHorizon"), (float) l.skyHorizon[0], (float) l.skyHorizon[1], (float) l.skyHorizon[2]);
        gl.glUniform3f(loc(gl, p, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform1f(loc(gl, p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, p, "uDisplay"), (float) l.display);
    }

    private static int loc(GL4 gl, int p, String name) { return gl.glGetUniformLocation(p, name); }

    /** „Strandkörbe: 8 412 (nah 37, mittel 640, fern 7 735), 182 Schatten“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Strandkörbe: %,d (nah %,d, mittel %,d, fern %,d), %,d Schatten", chairs.size(), count[0], count[1], count[2],
                shadows ? nahe.size() : 0);
    }

    /** Zahl der Körbe je Stufe beim letzten Bild {nah, mittel, fern}. */
    public int[] counts() { return count.clone(); }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (shadowProg != 0) gl.glDeleteProgram(shadowProg);
        if (stageProg != 0) gl.glDeleteProgram(stageProg);
        gl.glDeleteVertexArrays(3, vao, 0);
        gl.glDeleteBuffers(3, vbo, 0);
        gl.glDeleteBuffers(2, new int[]{instVbo, shVbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{shVao}, 0);
        if (stVao != 0) { gl.glDeleteVertexArrays(1, new int[]{stVao}, 0); gl.glDeleteBuffers(1, new int[]{stVbo}, 0); }
        prog = shadowProg = stageProg = 0;
    }

    // ------------------------------------------------------------------ Shader

    /** Liest eine Shader-Datei aus diesem Package (Ressource neben der Klasse). */
    public static String source(String file) {
        try (InputStream in = BeachChairGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("beachchair: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("beachchair: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl, String vsFile, String fsFile) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, vsFile), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, fsFile);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("beachchair: " + vsFile + "/" + fsFile + " lässt sich nicht binden: " + log(gl, p, true));
        return p;
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("beachchair: " + file + " lässt sich nicht übersetzen: " + log(gl, sh, false));
        return sh;
    }

    private static String log(GL4 gl, int o, boolean program) {
        int[] len = new int[1];
        if (program) gl.glGetProgramiv(o, GL4.GL_INFO_LOG_LENGTH, len, 0); else gl.glGetShaderiv(o, GL4.GL_INFO_LOG_LENGTH, len, 0);
        if (len[0] <= 1) return "";
        byte[] b = new byte[len[0]];
        if (program) gl.glGetProgramInfoLog(o, len[0], null, 0, b, 0); else gl.glGetShaderInfoLog(o, len[0], null, 0, b, 0);
        return new String(b, StandardCharsets.UTF_8).trim();
    }
}
