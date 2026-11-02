package com.dan.understory.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.understory.Kind;
import com.dan.understory.PlantMesh;
import com.dan.understory.Understory;
import com.dan.understory.gl.UnderstoryGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung von {@code com.dan.understory}: ein Stück Küstenregenwald, 200 × 200 m, ohne Fremdcode. Alter Wald
 * (Blattflächenindex 5,5) mit einer Lichtung, ein Bach (nass) und ein Rücken (trocken); darauf, was dort wachsen will:
 * Moos und Teufelskeule am Bach, Farne im Halbschatten, Beeren in der Lichtung, Streu auf dem trockenen Rücken, Totholz
 * überall, Bartflechten an den Stämmen. Die Stämme sind nur graue Säulen, die Kronen fehlen: ihr Schatten kommt als Licht
 * unter dem Kronendach (Beer-Lambert) in den Shader.
 * <ul>
 *   <li>{@code UnderstoryDemo} – Fenster: Ansicht, Qualität, Sonne, Wind, Lichtung, Tag im Jahr;</li>
 *   <li>{@code --bild datei.png} – oben die sieben Arten, darunter zwei Ansichten des Waldbodens, mit Zeiten.</li>
 * </ul>
 */
public final class UnderstoryDemo implements GLEventListener {
    static final Color BG = new Color(14, 20, 24), INK = new Color(220, 228, 232), MUTED = new Color(150, 165, 172), ACCENT = new Color(64, 200, 190);
    static final double HALF = 100, GAP_X = 25, GAP_Z = 15, GAP_R = 16;
    static final String[] VIEWS = {"Waldboden am Bach", "Lichtung"};

    volatile double gapLai = 0.8, sunElev = 28, wind = 3;
    volatile int day = 200, viewIdx = 0;
    volatile Understory.Quality quality = Understory.Quality.HIGH;
    volatile boolean rebuildCanopy = true;

    static double noise(double x, double z) { return Math.sin(x / 13.0 + Math.cos(z / 17.0) * 1.7) * 0.5 + Math.sin(z / 9.0 - x / 23.0) * 0.3 + Math.sin((x + 2 * z) / 5.3) * 0.2; }
    static double stream(double x) { return -25 + 10 * Math.sin(x / 18.0); }
    static float ground(double x, double z) {
        double s = Math.abs(z - stream(x));
        return (float) (0.6 * Math.sin(x / 21.0) * Math.cos(z / 17.0) + 0.04 * (z + 100) * (z > 0 ? 1 : 0.4) - 0.6 * Math.exp(-s * s / 8) + 0.15 * noise(x * 2, z * 2));
    }
    double lai(double x, double z) {
        double d = Math.hypot(x - GAP_X, z - GAP_Z);
        double t = Math.max(0, Math.min(1, (d - GAP_R) / 8));
        return gapLai + (5.5 + 0.8 * noise(x * 0.5, z * 0.5) - gapLai) * t * t * (3 - 2 * t);
    }
    static double moisture(double x, double z) {
        double s = Math.abs(z - stream(x));
        double m = 0.55 + 0.12 * noise(x * 0.8, z * 0.8) + 0.43 * Math.exp(-s / 7.0);
        if (z > 40) m -= Math.min(0.35, (z - 40) * 0.012);
        return Math.max(0, Math.min(1, m));
    }

    final Understory field, lineup;
    final UnderstoryGl ugl, lgl;
    int groundProg, trunkProg, waterProg, groundVao, groundIdx, trunkVao, trunkIdx, waterVao, canopyTex, timer;
    double gpuMs;
    final List<float[]> trunks = new ArrayList<>();
    double t0 = System.nanoTime() / 1e9;

    UnderstoryDemo() {
        Understory.Site site = new Understory.Site() {
            public float ground(double x, double z) { return UnderstoryDemo.ground(x, z); }
            public double lai(double x, double z) { return UnderstoryDemo.this.lai(x, z); }
            public double moisture(double x, double z) { return UnderstoryDemo.moisture(x, z); }
        };
        field = new Understory(List.of(Kind.values()), site, 1958);
        // Stämme: Gitter mit 7 m und Zufall, nicht in der Lichtung und nicht im Bach
        java.util.Random r = new java.util.Random(5);
        List<Float> an = new ArrayList<>();
        for (double z = -HALF + 4; z < HALF; z += 7)
            for (double x = -HALF + 4; x < HALF; x += 7) {
                double px = x + (r.nextDouble() - 0.5) * 5, pz = z + (r.nextDouble() - 0.5) * 5;
                if (Math.hypot(px - GAP_X, pz - GAP_Z) < GAP_R + 2 || Math.abs(pz - stream(px)) < 3.5 || r.nextDouble() < 0.35) continue;
                float rad = (float) (0.22 + 0.35 * r.nextDouble());
                trunks.add(new float[]{(float) px, ground(px, pz), (float) pz, rad});
                for (int k = 0; k < 3; k++) {
                    double a = r.nextDouble() * 2 * Math.PI, h = 3 + 10 * r.nextDouble();
                    an.add((float) (px + Math.cos(a) * (rad + 0.05))); an.add((float) (ground(px, pz) + h)); an.add((float) (pz + Math.sin(a) * (rad + 0.05)));
                }
            }
        float[] anchors = new float[an.size()];
        for (int i = 0; i < anchors.length; i++) anchors[i] = an.get(i);
        field.setAnchors(anchors);
        ugl = new UnderstoryGl(field);
        lineup = new Understory(List.of(Kind.values()), new Understory.Site() {
            public float ground(double x, double z) { return 0; }
            public double lai(double x, double z) { return 0; }
            public double moisture(double x, double z) { return 0.5; }
        }, 1);
        lgl = new UnderstoryGl(lineup);
    }

    // ------------------------------------------------------------------ GL

    @Override public void init(GLAutoDrawable d) { init(d.getGL().getGL4()); }

    void init(GL4 gl) {
        ugl.init(gl);
        lgl.init(gl);
        groundProg = prog(gl, GROUND_V, GROUND_F);
        trunkProg = prog(gl, TRUNK_V, TRUNK_F);
        waterProg = prog(gl, GROUND_V, WATER_F);
        int n = 200;
        FloatBuffer vb = Buffers.newDirectFloatBuffer(n * n * 3);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = -HALF + 2 * HALF * i / (n - 1), z = -HALF + 2 * HALF * j / (n - 1);
                vb.put((float) x).put(ground(x, z)).put((float) z);
            }
        vb.flip();
        IntBuffer ib = Buffers.newDirectIntBuffer((n - 1) * (n - 1) * 6);
        for (int j = 0; j < n - 1; j++) for (int i = 0; i < n - 1; i++) { int a = j * n + i; ib.put(a).put(a + n).put(a + 1).put(a + 1).put(a + n).put(a + n + 1); }
        ib.flip();
        groundIdx = (n - 1) * (n - 1) * 6;
        groundVao = mesh(gl, vb, ib, 3);
        // Bach: ein Band entlang stream(x), knapp über dem tiefsten Boden
        FloatBuffer wb = Buffers.newDirectFloatBuffer(2 * 201 * 3);
        for (int i = 0; i <= 200; i++) {
            double x = -HALF + i;
            for (int s = -1; s <= 1; s += 2) { double z = stream(x) + s * 1.6; wb.put((float) x).put(ground(x, stream(x)) + 0.12f).put((float) z); }
        }
        wb.flip();
        IntBuffer wi = Buffers.newDirectIntBuffer(200 * 6);
        for (int i = 0; i < 200; i++) { int a = 2 * i; wi.put(a).put(a + 1).put(a + 2).put(a + 1).put(a + 3).put(a + 2); }
        wi.flip();
        waterVao = mesh(gl, wb, wi, 3);
        // Stämme: Säulen mit 10 Seiten, 40 m hoch (die Kronen fehlen)
        int sides = 10;
        FloatBuffer tb = Buffers.newDirectFloatBuffer(trunks.size() * sides * 2 * 6);
        IntBuffer ti = Buffers.newDirectIntBuffer(trunks.size() * sides * 6);
        int base = 0;
        for (float[] t : trunks) {
            for (int k = 0; k < 2; k++)
                for (int s = 0; s < sides; s++) {
                    double a = 2 * Math.PI * s / sides;
                    float rr = t[3] * (k == 0 ? 1.25f : 0.7f), y = t[1] - 0.3f + k * 40;
                    tb.put(t[0] + (float) Math.cos(a) * rr).put(y).put(t[2] + (float) Math.sin(a) * rr).put((float) Math.cos(a)).put(0).put((float) Math.sin(a));
                }
            for (int s = 0; s < sides; s++) { int a = base + s, b = base + (s + 1) % sides; ti.put(a).put(a + sides).put(b).put(b).put(a + sides).put(b + sides); }
            base += 2 * sides;
        }
        tb.flip();
        ti.flip();
        trunkIdx = trunks.size() * sides * 6;
        trunkVao = mesh(gl, tb, ti, 6);
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        canopyTex = t[0];
        gl.glGenQueries(1, t, 0);
        timer = t[0];
    }

    static int mesh(GL4 gl, FloatBuffer vb, IntBuffer ib, int stride) {
        int[] a = new int[1], b = new int[2];
        gl.glGenVertexArrays(1, a, 0);
        gl.glGenBuffers(2, b, 0);
        gl.glBindVertexArray(a[0]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b[0]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, vb.capacity() * 4L, vb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride * 4, 0);
        if (stride == 6) { gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12); }
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, b[1]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ib.capacity() * 4L, ib, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        return a[0];
    }

    /** Licht unter dem Kronendach als Textur (256², Beer-Lambert aus dem Blattflächenindex). */
    void canopy(GL4 gl) {
        int n = 256;
        ByteBuffer bb = Buffers.newDirectByteBuffer(n * n);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = -HALF + 2 * HALF * (i + 0.5) / n, z = -HALF + 2 * HALF * (j + 0.5) / n;
                bb.put((byte) Math.round(Math.min(1, Kind.light(lai(x, z), Kind.K_CONIFER)) * 255));
            }
        bb.flip();
        gl.glBindTexture(GL.GL_TEXTURE_2D, canopyTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL2ES2.GL_R8, n, n, 0, GL2ES2.GL_RED, GL.GL_UNSIGNED_BYTE, bb);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
    }

    UnderstoryGl.Light light() {
        UnderstoryGl.Light l = new UnderstoryGl.Light();
        double el = Math.toRadians(sunElev), az = Math.toRadians(215);
        l.sunDir = new double[]{Math.cos(el) * Math.sin(az), Math.sin(el), -Math.cos(el) * Math.cos(az)};
        l.sunColor = new double[]{2.6, 2.35, 2.0};
        l.skyColor = new double[]{0.22, 0.28, 0.36};
        l.fogColor = new double[]{0.30, 0.36, 0.40};
        l.fogDistance = 260;
        l.canopyTexture = canopyTex;
        l.canopyRect = new double[]{-HALF, -HALF, 2 * HALF};
        l.wetness = 0.3;
        return l;
    }

    static double[][] view(int i) {
        return i == 0 ? new double[][]{{-30, ground(-30, -14) + 1.3, -14}, {6, ground(6, -6) + 0.3, -6}}
                : new double[][]{{GAP_X - 10, ground(GAP_X - 10, GAP_Z + 9) + 4, GAP_Z + 9}, {GAP_X + 22, ground(GAP_X + 22, GAP_Z - 14), GAP_Z - 14}};
    }

    UnderstoryGl.View camera(int i, int w, int h) {
        double[][] e = view(i);
        UnderstoryGl.View v = new UnderstoryGl.View();
        v.eye = e[0];
        v.viewProj = mul(perspective(i == 0 ? 62 : 50, w / (double) h, 0.1, 2000), lookAt(e[0], e[1]));
        v.tonemap = true;
        v.exposure = 1.7;
        return v;
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        floor(gl, 0, 0, d.getSurfaceWidth(), d.getSurfaceHeight(), viewIdx, true);
    }

    /** Waldboden in das Rechteck (x, y, w, h) des gebundenen Bildspeichers. */
    void floor(GL4 gl, int x, int y, int w, int h, int view, boolean clear) {
        if (rebuildCanopy) { canopy(gl); rebuildCanopy = false; field.clear(); }
        field.apply(quality);
        UnderstoryGl.View v = camera(view, w, h);
        UnderstoryGl.Light l = light();
        double t = System.nanoTime() / 1e9 - t0;
        field.update(v.eye, v.viewProj, 16);
        ugl.setWind(0.7, 0.7, wind, 0.5);
        ugl.setSeason(day);
        ugl.update(gl);
        gl.glViewport(x, y, w, h);
        gl.glEnable(GL.GL_SCISSOR_TEST);
        gl.glScissor(x, y, w, h);
        gl.glClearColor(0.30f, 0.36f, 0.40f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL.GL_SCISSOR_TEST);
        gl.glBeginQuery(GL4.GL_TIME_ELAPSED, timer);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        for (int[] pv : new int[][]{{groundProg, groundVao, groundIdx}, {waterProg, waterVao, 200 * 6}, {trunkProg, trunkVao, trunkIdx}}) {
            gl.glUseProgram(pv[0]);
            uniforms(gl, pv[0], v, l);
            gl.glBindVertexArray(pv[1]);
            gl.glDrawElements(GL.GL_TRIANGLES, pv[2], GL.GL_UNSIGNED_INT, 0);
        }
        gl.glBindVertexArray(0);
        ugl.draw(gl, v, l, t);
        gl.glEndQuery(GL4.GL_TIME_ELAPSED);
        long[] ns = new long[1];
        gl.glGetQueryObjecti64v(timer, GL4.GL_QUERY_RESULT, ns, 0);
        gpuMs = ns[0] / 1e6;
    }

    void uniforms(GL4 gl, int p, UnderstoryGl.View v, UnderstoryGl.Light l) {
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunDir"), (float) l.sunDir[0], (float) l.sunDir[1], (float) l.sunDir[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSkyColor"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uGapR"), (float) GAP_R);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, canopyTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uCanopy"), 5);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uHalf"), (float) HALF);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uExposure"), (float) v.exposure);
    }

    /** Die sieben Arten nebeneinander, je eine Zelle (x, y, w, h). */
    void lineupCell(GL4 gl, Kind k, int x, int y, int w, int h) {
        java.util.Arrays.fill(lineup.count, 0);
        PlantMesh m = PlantMesh.build(k, 0, 0);
        int b = lineup.bucketOf(k.ordinal(), 0, 0);
        float y0 = k == Kind.LICHEN ? m.height + 0.05f : 0;
        lineup.bucket[b][0] = 0; lineup.bucket[b][1] = y0; lineup.bucket[b][2] = 0; lineup.bucket[b][3] = k == Kind.DEADWOOD ? 0 : 0.6f;
        lineup.bucket[b][4] = 1; lineup.bucket[b][5] = 0.5f; lineup.bucket[b][6] = 0; lineup.bucket[b][7] = 1;
        lineup.count[b] = 1;
        lgl.setWind(1, 0, 0, 0);
        lgl.setSeason(day);
        lgl.update(gl);
        float ext = Math.max(m.height, k == Kind.DEADWOOD ? m.size * 0.5f : m.radius * 1.4f);
        double cx = k == Kind.DEADWOOD ? m.size / 2 : 0, cy = m.height / 2;
        double dist = ext * 2.3 + 0.2;
        double[] eye = {cx + dist * 0.35, cy + dist * 0.45, dist * 0.85}, at = {cx, cy, 0};
        UnderstoryGl.View v = new UnderstoryGl.View();
        v.eye = eye;
        v.viewProj = mul(perspective(40, w / (double) h, 0.05, 200), lookAt(eye, at));
        v.tonemap = true;
        v.exposure = 1.2;
        UnderstoryGl.Light l = light();
        l.canopyTexture = 0;
        l.canopy = 0.8;
        l.fogDistance = 1e5;
        gl.glViewport(x, y, w, h);
        gl.glEnable(GL.GL_SCISSOR_TEST);
        gl.glScissor(x, y, w, h);
        gl.glClearColor(BG.getRed() / 255f * 1.4f, BG.getGreen() / 255f * 1.4f, BG.getBlue() / 255f * 1.4f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL.GL_SCISSOR_TEST);
        lgl.draw(gl, v, l, 0);
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { ugl.dispose(d.getGL().getGL4()); lgl.dispose(d.getGL().getGL4()); }

    // ------------------------------------------------------------------ ohne Fenster

    public static void main(String[] a) throws Exception {
        List<String> l = List.of(a);
        int bi = l.indexOf("--bild");
        if (bi >= 0) { bild(new File(a[bi + 1])); return; }
        SwingUtilities.invokeLater(() -> new UnderstoryDemo().open());
    }

    static void bild(File out) throws Exception {
        int W = 1920, H = 300 + 540;
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        // einfach gepuffert: gezeichnet wird ohne Tausch (invoke statt display), gelesen wird derselbe Speicher
        caps.setDoubleBuffered(false);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, W, H);
        UnderstoryDemo d = new UnderstoryDemo();
        dr.addGLEventListener(new GLEventListener() {
            public void init(GLAutoDrawable x) { d.init(x.getGL().getGL4()); }
            public void dispose(GLAutoDrawable x) { }
            public void reshape(GLAutoDrawable x, int a, int b, int c, int e) { }
            public void display(GLAutoDrawable x) { }
        });
        dr.display();
        BufferedImage[] img = new BufferedImage[1];
        double[] ms = new double[2];
        String[] info = new String[2];
        dr.invoke(true, x -> {
            GL4 gl = x.getGL().getGL4();
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, x.getContext().getDefaultDrawFramebuffer());
            Kind[] ks = Kind.values();
            int cw = W / ks.length;
            for (int i = 0; i < ks.length; i++) d.lineupCell(gl, ks[i], i * cw, H - 300, cw, 300);
            for (int v = 0; v < 2; v++) {
                d.floor(gl, v * W / 2, 0, W / 2, 540, v, false);
                d.field.completeNow(view(v)[0]);
                for (int k = 0; k < 4; k++) d.floor(gl, v * W / 2, 0, W / 2, 540, v, false);
                ms[v] = d.gpuMs;
                info[v] = d.ugl.info();
            }
            gl.glFinish();
            img[0] = grab(gl, x.getContext().getDefaultDrawFramebuffer(), W, H);
            return true;
        });
        BufferedImage sheet = new BufferedImage(W, H + 70, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, W, H + 70);
        g.drawImage(img[0], 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Kind[] ks = Kind.values();
        int cw = W / ks.length;
        for (int i = 0; i < ks.length; i++) {
            PlantMesh m = PlantMesh.build(ks[i], 0, 0), m1 = PlantMesh.build(ks[i], 0, 1);
            label(g, i * cw + 6, 6, ks[i].name, String.format(Locale.GERMAN, "%.1f m · %,d / %,d Dreiecke", m.size, m.nt, m1.nt));
        }
        for (int v = 0; v < 2; v++) label(g, v * W / 2 + 8, 308, VIEWS[v], String.format(Locale.GERMAN, "%.2f ms auf der Grafikkarte (Boden, Stämme, Unterwuchs)", ms[v]));
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("com.dan.understory · Küstenregenwald 200 × 200 m: Blattflächenindex 5,5 (Licht am Boden 8 %), Lichtung mit 0,8 (70 %), Bach nass, Rücken trocken · " + info[0], 12, H + 26);
        g.drawString(info[1] + " · Stämme ohne Kronen; ihr Schatten kommt als Licht unter dem Kronendach (Beer-Lambert, k = 0,45) in den Shader", 12, H + 50);
        g.dispose();
        ImageIO.write(sheet, "png", out);
        System.out.println(info[0]);
        System.out.println(info[1]);
        System.out.printf(Locale.GERMAN, "Waldboden %.2f ms, Überblick %.2f ms auf der Grafikkarte%nBild: %s%n", ms[0], ms[1], out.getPath());
        dr.destroy();
        System.exit(0);
    }

    static BufferedImage grab(GL4 gl, int fb, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fb);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return img;
    }

    static void label(Graphics2D g, int x, int y, String a, String b) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        int w = Math.max(g.getFontMetrics().stringWidth(a), g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 11)).stringWidth(b)) + 12;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(x, y, w, 36);
        g.setColor(Color.WHITE);
        g.drawString(a, x + 6, y + 15);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(new Color(220, 220, 220));
        g.drawString(b, x + 6, y + 30);
    }

    // ------------------------------------------------------------------ Fenster

    private JPanel panel;

    private void open() {
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(this);
        FFrame f = new FFrame("Unterwuchs · com.dan.understory");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(canvas, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        f.setPreferredFrameSize(new java.awt.Dimension(1500, 900));
        f.setSize(1500, 900);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
    }

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel head = new JLabel("UNTERWUCHS");
        head.setForeground(ACCENT);
        head.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(head);
        FComboBox v = new FComboBox(VIEWS);
        v.addActionListener(e -> viewIdx = v.getSelectedIndex());
        add(v);
        String[] qs = {"LOW", "MEDIUM", "HIGH", "ULTRA"};
        FComboBox q = new FComboBox(qs);
        q.setSelectedIndex(2);
        q.addActionListener(e -> quality = Understory.Quality.values()[q.getSelectedIndex()]);
        add(q);
        slider("Sonnenhöhe", 2, 60, 28, "°", x -> sunElev = x);
        slider("Wind", 0, 20, 3, " m/s", x -> wind = x);
        slider("Lichtung: Blattflächenindex ×10", 0, 75, 8, "", x -> { gapLai = x / 10.0; rebuildCanopy = true; });
        slider("Tag im Jahr", 1, 365, 200, "", x -> day = x);
        JLabel info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(info);
        new javax.swing.Timer(500, e -> info.setText(String.format(Locale.GERMAN, "<html><body style='width:240px'>%.2f ms auf der Grafikkarte<br><br>%s</body></html>", gpuMs, ugl.info()))).start();
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void slider(String name, int min, int max, int val, String unit, java.util.function.IntConsumer s) {
        JLabel l = new JLabel(name + "  " + val + unit);
        l.setForeground(INK);
        add(l);
        FSlider sl = new FSlider(min, max, val);
        sl.addChangeListener(e -> { l.setText(name + "  " + sl.getValue() + unit); s.accept(sl.getValue()); });
        add(sl);
    }

    private void add(Component c) {
        if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
        panel.add(Box.createVerticalStrut(6));
    }

    // ------------------------------------------------------------------ Hilfen und Shader

    static int prog(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (String[] s : new String[][]{{vs, "v"}, {COMMON + fs, "f"}}) {
            int sh = gl.glCreateShader(s[1].equals("v") ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + s[0]}, null, 0);
            gl.glCompileShader(sh);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[8192];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("Shader: " + new String(b, 0, len[0]));
            }
            gl.glAttachShader(p, sh);
        }
        gl.glLinkProgram(p);
        return p;
    }

    static float[] perspective(double fovy, double aspect, double near, double far) {
        float t = (float) (1 / Math.tan(Math.toRadians(fovy) / 2));
        return new float[]{t / (float) aspect, 0, 0, 0, 0, t, 0, 0, 0, 0, (float) ((far + near) / (near - far)), -1, 0, 0, (float) (2 * far * near / (near - far)), 0};
    }

    static float[] lookAt(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= l; fy /= l; fz /= l;
        double sx = -fz, sz = fx;
        l = Math.hypot(sx, sz);
        sx /= l; sz /= l;
        double ux = -sz * fy, uy = sz * fx - sx * fz, uz = sx * fy;
        return new float[]{(float) sx, (float) ux, (float) -fx, 0, 0, (float) uy, (float) -fy, 0, (float) sz, (float) uz, (float) -fz, 0,
                (float) -(sx * e[0] + sz * e[2]), (float) -(ux * e[0] + uy * e[1] + uz * e[2]), (float) (fx * e[0] + fy * e[1] + fz * e[2]), 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) for (int rr = 0; rr < 4; rr++) { float s = 0; for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k]; r[c * 4 + rr] = s; }
        return r;
    }

    static final String COMMON = """
            uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uFogColor;
            uniform float uFogDist, uHalf, uExposure, uGapR;
            uniform sampler2D uCanopy;
            vec3 tone(vec3 c) { vec3 x = c * uExposure; return pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2)); }
            float canopyAt(vec3 w) { return textureLod(uCanopy, (w.xz + uHalf) / (2.0 * uHalf), 0.0).r; }
            vec3 lit(vec3 alb, vec3 n, vec3 w) {
                float c = canopyAt(w);
                vec3 col = alb * (uSunColor * max(dot(n, uSunDir), 0.0) * c + uSkyColor * (0.35 + 0.65 * sqrt(c)) * (0.6 + 0.4 * n.y));
                return mix(col, uFogColor, 1.0 - exp(-length(w - uCam) / uFogDist));
            }
            """;
    static final String GROUND_V = "layout(location = 0) in vec3 aPos;\nuniform mat4 uVP;\nout vec3 vW;\nvoid main(){ vW = aPos; gl_Position = uVP * vec4(aPos, 1.0); }\n";
    static final String GROUND_F = """
            in vec3 vW;
            out vec4 frag;
            float h(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
            void main() {
                vec3 n = normalize(cross(dFdx(vW), dFdy(vW)));
                if (n.y < 0.0) n = -n;
                float s = abs(vW.z - (-25.0 + 10.0 * sin(vW.x / 18.0)));
                float wet = clamp(0.55 + 0.43 * exp(-s / 7.0) - max(0.0, vW.z - 40.0) * 0.012, 0.0, 1.0);
                vec3 moss = vec3(0.04, 0.085, 0.015), litter = vec3(0.09, 0.05, 0.022), mud = vec3(0.05, 0.04, 0.03);
                float g = h(floor(vW.xz * 3.0)) * 0.3 + 0.85;
                vec3 alb = mix(litter, moss, smoothstep(0.45, 0.7, wet)) * g;
                alb = mix(mud, alb, smoothstep(1.6, 2.6, s));
                frag = vec4(tone(lit(alb, n, vW)), 1.0);
            }
            """;
    static final String WATER_F = """
            in vec3 vW;
            out vec4 frag;
            void main() {
                vec3 v = normalize(vW - uCam);
                float f = 0.03 + 0.97 * pow(1.0 - max(-v.y, 0.0), 5.0);
                vec3 col = mix(vec3(0.02, 0.03, 0.025), uSkyColor * 1.2, f);
                frag = vec4(tone(mix(col, uFogColor, 1.0 - exp(-length(vW - uCam) / uFogDist))), 1.0);
            }
            """;
    static final String TRUNK_V = "layout(location = 0) in vec3 aPos;\nlayout(location = 1) in vec3 aNrm;\nuniform mat4 uVP;\nout vec3 vW;\nout vec3 vN;\nvoid main(){ vW = aPos; vN = aNrm; gl_Position = uVP * vec4(aPos, 1.0); }\n";
    static final String TRUNK_F = """
            in vec3 vW;
            in vec3 vN;
            out vec4 frag;
            void main() {
                vec3 alb = vec3(0.11, 0.075, 0.055) * (0.85 + 0.3 * fract(sin(floor(vW.y * 2.0) * 3.1 + vW.x) * 43758.5));
                frag = vec4(tone(lit(alb, normalize(vN), vW)), 1.0);
            }
            """;
}
