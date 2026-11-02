package com.dan.clipmap.demo;

import com.dan.clipmap.Clipmap;
import com.dan.clipmap.Source;
import com.dan.clipmap.TileFolder;
import com.dan.clipmap.TileStack;
import com.dan.clipmap.gl.ClipmapGl;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.jogamp.opengl.GL;
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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorschau des Packages: Gelände bis zum Horizont, mit Himmel, Dunst und Erdkrümmung, Licht nach Sonnenhöhe; Farbe nach
 * Höhe und Hang (Wasser, Ufer, Wald, Fels, Schnee). Steuerung: Maus ziehen dreht, W A S D fährt, Q/E sinkt/steigt,
 * Umschalt schneller, Rad ändert das Tempo. Rechts: Qualität, Sonnenhöhe, Krümmung, Stufen einfärben, Gitter.
 * <p>
 * Daten: ohne Angabe ein gerechnetes Gelände ({@link ClipmapCheck.Synth}). Mit {@code --daten <ordner> <ost> <nord>}
 * die Kachelordner darin (etwa {@code daten/gelaende} von LituyaBay1958 mit Ursprung 352000 6503000), mit
 * {@code --auge x z höhe richtung} ein Startpunkt (m, Grad von Nord im Uhrzeigersinn).
 * <p>
 * Ohne Fenster: {@code --bild datei.png} zeichnet vier Ansichten (am Boden, schräg, hoch bis zum Horizont, Stufen
 * eingefärbt) in 1280 × 720 und schreibt die Zeit je Bild auf der Grafikkarte darunter.
 */
public final class ClipmapDemo implements GLEventListener {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(200, 170, 120);

    final Source source;
    final double originX, originZ, cell0;
    volatile Clipmap clip;
    volatile Clipmap.Quality quality = Clipmap.Quality.HIGH;
    final ClipmapGl cgl = new ClipmapGl();
    volatile double sunElev = 25, sunAz = 250;
    volatile boolean curvature = true, showLevels, wire;
    final double[] eye = new double[3];
    volatile double yaw, pitch = -8, speed = 60;
    private int terrainProg, skyProg, emptyVao, timer;
    private boolean timerBusy;
    volatile double gpuMs, fps;
    private long last, frames, fpsT0;
    private final boolean[] keys = new boolean[256];
    private Clipmap built;

    ClipmapDemo(Source s, double ox, double oz, double c0) {
        source = s;
        originX = ox;
        originZ = oz;
        cell0 = c0;
        clip = make(quality);
    }

    Clipmap make(Clipmap.Quality q) {
        Clipmap c = new Clipmap(source, q, cell0, originX, originZ, 140_000);
        c.noData = -200;
        c.start();
        return c;
    }

    // ------------------------------------------------------------------ GL

    static final String TERRAIN_VERT = """
            uniform mat4 uVP;
            out vec3 vW;
            out vec3 vN;
            out float vH;
            out float vMorph;
            void main() {
                ClipVertex c = clipmapVertex();
                vW = c.world; vN = c.normal; vH = c.height; vMorph = c.morph;
                gl_Position = uVP * vec4(c.world, 1.0);
            }
            """;

    static final String TERRAIN_FRAG = """
            in vec3 vW;
            in vec3 vN;
            in float vH;
            in float vMorph;
            out vec4 frag;
            uniform vec3 uSun, uEye;
            uniform int uShowLevels;
            vec3 sunC() { float e = uSun.y; return mix(vec3(1.0, 0.55, 0.3), vec3(1.0, 0.95, 0.88), smoothstep(0.05, 0.5, e)) * 3.0 * smoothstep(-0.02, 0.08, e); }
            vec3 sky(vec3 d) {
                float up = clamp(d.y, 0.0, 1.0);
                return mix(vec3(0.66, 0.75, 0.86), vec3(0.20, 0.38, 0.74), pow(up, 0.45)) * 0.9;
            }
            void main() {
                if (clipmapHidden(vW.xz)) discard;
                vec3 n = normalize(vN);
                float slope = 1.0 - n.y;
                vec3 alb;
                if (vH < 0.0) alb = vec3(0.03, 0.08, 0.10);                                   // Wasser (flach gezeichnet)
                else if (vH < 4.0) alb = vec3(0.42, 0.40, 0.36);                              // Ufer
                else alb = mix(vec3(0.07, 0.14, 0.06), vec3(0.33, 0.32, 0.30), smoothstep(0.18, 0.35, slope));   // Wald, Fels
                alb = mix(alb, vec3(0.30, 0.30, 0.29), smoothstep(700.0, 1000.0, vH) * (1.0 - smoothstep(0.5, 0.7, slope)));
                alb = mix(alb, vec3(0.85, 0.88, 0.92), smoothstep(1100.0, 1500.0, vH) * (1.0 - smoothstep(0.45, 0.65, slope)));
                if (uShowLevels == 1) {
                    vec3 lc[6] = vec3[6](vec3(1, 0.3, 0.2), vec3(1, 0.8, 0.2), vec3(0.3, 0.9, 0.3), vec3(0.2, 0.8, 1), vec3(0.4, 0.4, 1), vec3(0.9, 0.4, 1));
                    alb = mix(alb, lc[uClipLevel % 6] * 0.5, 0.6);
                    alb = mix(alb, vec3(1.0), vMorph * 0.5);
                }
                vec3 v = normalize(uEye - vW);
                vec3 col = alb / 3.14159 * (sunC() * max(dot(n, uSun), 0.0) + sky(vec3(0, 1, 0)) * (0.5 + 0.5 * n.y));
                float d = length(uEye - vW);
                float hm = max(0.0, uEye.y - v.y * d * 0.5);
                float f = 1.0 - exp(-d * exp(-hm / 8000.0) / 90000.0);
                col = mix(col, sky(normalize(vec3(-v.x, 0.03, -v.z))), f);
                col = col * (2.51 * col + 0.03) / (col * (2.43 * col + 0.59) + 0.14);
                frag = vec4(pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2)), 1.0);
            }
            """;

    static final String SKY_VERT = "#version 430 core\nout vec2 vUV;\nvoid main(){ vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vUV = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";

    static final String SKY_FRAG = """
            #version 430 core
            in vec2 vUV;
            out vec4 frag;
            uniform mat4 uInv;
            uniform vec3 uSun;
            void main() {
                vec4 a = uInv * vec4(vUV * 2.0 - 1.0, -1.0, 1.0), b = uInv * vec4(vUV * 2.0 - 1.0, 1.0, 1.0);
                vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
                float up = clamp(d.y, 0.0, 1.0);
                vec3 col = mix(vec3(0.66, 0.75, 0.86), vec3(0.20, 0.38, 0.74), pow(up, 0.45)) * 0.9;
                col += vec3(1.0, 0.85, 0.6) * pow(max(dot(d, uSun), 0.0), 80.0) * 0.6;
                col = col * (2.51 * col + 0.03) / (col * (2.43 * col + 0.59) + 0.14);
                frag = vec4(pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2)), 1.0);
            }
            """;

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        String lib = ClipmapGl.glsl();
        terrainProg = program(gl, "#version 430 core\n#define CLIPMAP_VERTEX\n" + lib + TERRAIN_VERT, "#version 430 core\n" + lib + TERRAIN_FRAG);
        gl.glBindAttribLocation(terrainProg, ClipmapGl.ATTRIB, "aClipGrid");
        gl.glLinkProgram(terrainProg);
        skyProg = program(gl, SKY_VERT, SKY_FRAG);
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        gl.glGenQueries(1, v, 0);
        timer = v[0];
        last = System.nanoTime();
        fpsT0 = last;
    }

    static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, compile(gl, GL4.GL_VERTEX_SHADER, vs));
        gl.glAttachShader(p, compile(gl, GL4.GL_FRAGMENT_SHADER, fs));
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            int[] len = new int[1];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("Programm: " + new String(b, 0, len[0]));
        }
        return p;
    }

    static int compile(GL4 gl, int type, String src) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[8192];
            int[] len = new int[1];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            throw new IllegalStateException("Shader: " + new String(b, 0, len[0]));
        }
        return s;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    /** Kamera fahren (Tasten) und auf dem Boden halten. */
    void move(double dt) {
        double f = speed * (keys[KeyEvent.VK_SHIFT] ? 5 : 1) * dt, yr = Math.toRadians(yaw);
        double fx = Math.sin(yr), fz = -Math.cos(yr);
        if (keys[KeyEvent.VK_W]) { eye[0] += fx * f; eye[2] += fz * f; }
        if (keys[KeyEvent.VK_S]) { eye[0] -= fx * f; eye[2] -= fz * f; }
        if (keys[KeyEvent.VK_A]) { eye[0] += fz * f; eye[2] -= fx * f; }
        if (keys[KeyEvent.VK_D]) { eye[0] -= fz * f; eye[2] += fx * f; }
        if (keys[KeyEvent.VK_E]) eye[1] += f;
        if (keys[KeyEvent.VK_Q]) eye[1] -= f;
        float g = clip.height(eye[0], eye[2]);
        if (!Float.isNaN(g)) eye[1] = Math.max(eye[1], Math.max(g, 0) + 1.7);
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        move(dt);
        render(gl, d.getSurfaceWidth(), d.getSurfaceHeight(), d.getContext().getDefaultDrawFramebuffer());
        frames++;
        if (now - fpsT0 > 500_000_000L) { fps = frames / ((now - fpsT0) / 1e9); frames = 0; fpsT0 = now; }
    }

    void render(GL4 gl, int w, int h, int fbo) {
        Clipmap c = clip;
        if (c != built) { cgl.init(gl, c); built = c; }
        c.curvatureRadius = curvature ? 6_371_000 / (1 - 0.13) : 0;
        c.update(eye[0], eye[2]);
        cgl.upload(gl, c);
        if (timerBusy) {
            int[] av = new int[1];
            gl.glGetQueryObjectiv(timer, GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
            if (av[0] != 0) {
                long[] ns = new long[1];
                gl.glGetQueryObjecti64v(timer, GL4.GL_QUERY_RESULT, ns, 0);
                gpuMs = gpuMs * 0.8 + ns[0] / 1e6 * 0.2;
                timerBusy = false;
            }
        }
        boolean measure = !timerBusy;
        if (measure) gl.glBeginQuery(GL4.GL_TIME_ELAPSED, timer);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 1);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        float g = c.height(eye[0], eye[2]);
        double agl = Float.isNaN(g) ? eye[1] : eye[1] - Math.max(g, 0);
        double near = Math.max(0.5, Math.min(200, agl * 0.4)), far = 450_000;
        float[] vp = mul(perspective(55, w / (double) h, near, far), view());
        double se = Math.toRadians(sunElev), sa = Math.toRadians(sunAz);
        float[] sun = {(float) (Math.cos(se) * Math.sin(sa)), (float) Math.sin(se), (float) (-Math.cos(se) * Math.cos(sa))};
        // Himmel
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(skyProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(skyProg, "uInv"), 1, false, invert(vp), 0);
        gl.glUniform3fv(gl.glGetUniformLocation(skyProg, "uSun"), 1, sun, 0);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // Gelände
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LEQUAL);
        gl.glEnable(GL.GL_CULL_FACE);
        gl.glCullFace(GL.GL_BACK);
        gl.glFrontFace(GL.GL_CCW);
        gl.glUseProgram(terrainProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(terrainProg, "uVP"), 1, false, vp, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(terrainProg, "uSun"), 1, sun, 0);
        gl.glUniform3f(gl.glGetUniformLocation(terrainProg, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform1i(gl.glGetUniformLocation(terrainProg, "uShowLevels"), showLevels ? 1 : 0);
        if (wire) gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL4.GL_LINE);
        cgl.draw(gl, terrainProg, c, vp, eye, 0);
        gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL4.GL_FILL);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glUseProgram(0);
        if (measure) { gl.glEndQuery(GL4.GL_TIME_ELAPSED); timerBusy = true; }
    }

    // ------------------------------------------------------------------ Mathe (Spalten zuerst)

    float[] view() {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double[] f = {Math.sin(y) * Math.cos(p), Math.sin(p), -Math.cos(y) * Math.cos(p)};
        double[] r = norm(cross(f, new double[]{0, 1, 0})), u = cross(r, f);
        float[] m = new float[16];
        m[0] = (float) r[0]; m[4] = (float) r[1]; m[8] = (float) r[2];
        m[1] = (float) u[0]; m[5] = (float) u[1]; m[9] = (float) u[2];
        m[2] = (float) -f[0]; m[6] = (float) -f[1]; m[10] = (float) -f[2];
        m[12] = (float) -(r[0] * eye[0] + r[1] * eye[1] + r[2] * eye[2]);
        m[13] = (float) -(u[0] * eye[0] + u[1] * eye[1] + u[2] * eye[2]);
        m[14] = (float) (f[0] * eye[0] + f[1] * eye[1] + f[2] * eye[2]);
        m[15] = 1;
        return m;
    }

    static float[] perspective(double fovy, double aspect, double n, double f) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect);
        m[5] = (float) t;
        m[10] = (float) ((f + n) / (n - f));
        m[11] = -1;
        m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] m = new float[16];
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += (double) a[k * 4 + r] * b[c * 4 + k];
                m[c * 4 + r] = (float) s;
            }
        return m;
    }

    static float[] invert(float[] mf) {
        double[] m = new double[16], inv = new double[16];
        for (int i = 0; i < 16; i++) m[i] = mf[i];
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
        double det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        float[] o = new float[16];
        for (int i = 0; i < 16; i++) o[i] = (float) (inv[i] / det);
        return o;
    }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

    @Override public void dispose(GLAutoDrawable d) { cgl.dispose(d.getGL().getGL4()); clip.stop(); }

    // ------------------------------------------------------------------ Start

    public static void main(String[] a) throws Exception {
        List<String> l = List.of(a);
        Source s;
        double ox = 0, oz = 0, c0 = 0.5;
        double[] start = null;
        int di = l.indexOf("--daten");
        if (di >= 0) {
            TileStack st = TileStack.folders(new File(a[di + 1]), Double.parseDouble(a[di + 2]), Double.parseDouble(a[di + 3]));
            TileFolder f = (TileFolder) st.sources()[0];
            ox = f.xOfCol(0);
            oz = f.zOfRow(0);
            c0 = f.cell;
            double[] e = f.extent();
            start = new double[]{(e[0] + e[2]) / 2, (e[1] + e[3]) / 2, 0, 0};
            s = st;
            System.out.println("clipmap: " + st.describe());
        } else s = new ClipmapCheck.Synth(1958);
        int ai = l.indexOf("--auge");
        if (ai >= 0) start = new double[]{Double.parseDouble(a[ai + 1]), Double.parseDouble(a[ai + 2]), Double.parseDouble(a[ai + 3]), Double.parseDouble(a[ai + 4])};
        ClipmapDemo d = new ClipmapDemo(s, ox, oz, c0);
        if (start != null) { d.eye[0] = start[0]; d.eye[2] = start[1]; d.eye[1] = start[2]; d.yaw = start[3]; }
        else { d.eye[0] = 0; d.eye[2] = 0; d.eye[1] = 300; d.yaw = 60; }
        int bi = l.indexOf("--bild");
        if (bi >= 0) { d.bild(new File(a[bi + 1])); return; }
        SwingUtilities.invokeLater(d::open);
    }

    /** Vier Ansichten nebeneinander mit der Zeit je Bild (Mittel über 30 Bilder nach dem Laden). */
    void bild(File out) throws Exception {
        int w = 1280, h = 720;
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        dr.addGLEventListener(this);
        double gx = eye[0], gz = eye[2], dir = yaw;
        double[][] views = {{gx, gz, 2, dir, -3, 0}, {gx, gz, 1500, dir, -12, 0}, {gx, gz, 6000, dir, -4, 0}, {gx, gz, 1500, dir, -25, 1}};
        String[] names = {"am Boden", "schräg aus 1 500 m", "aus 6 000 m bis zum Horizont", "Stufen eingefärbt"};
        BufferedImage sheet = new BufferedImage(w, h * 4 / 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        List<String> lines = new ArrayList<>();
        for (int v = 0; v < views.length; v++) {
            eye[0] = views[v][0];
            eye[2] = views[v][1];
            float gh = clipHeightAfterLoad(dr);
            eye[1] = (Float.isNaN(gh) ? 0 : Math.max(gh, 0)) + views[v][2];
            yaw = views[v][3];
            pitch = views[v][4];
            showLevels = views[v][5] > 0;
            clip.update(eye[0], eye[2]);
            clip.await(120_000);
            for (int i = 0; i < 40; i++) dr.display();
            final BufferedImage[] img = new BufferedImage[1];
            dr.invoke(true, x -> { img[0] = grab(x, w, h); return true; });
            int col = v % 2, row = v / 2;
            g.drawImage(img[0], col * w / 2, row * h / 2, w / 2, h / 2, null);
            String t = String.format(Locale.GERMAN, "%s · %.2f ms je Bild (GPU) · %s", names[v], gpuMs, cgl.info());
            lines.add(t);
            g.setColor(new Color(0, 0, 0, 140));
            g.fillRect(col * w / 2 + 6, row * h / 2 + 6, 300, 22);
            g.setColor(Color.WHITE);
            g.drawString(names[v] + String.format(Locale.GERMAN, " · %.2f ms", gpuMs), col * w / 2 + 12, row * h / 2 + 22);
        }
        g.setColor(BG);
        g.fillRect(0, h, w, h);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        int y = h + 26;
        g.drawString("com.dan.clipmap · " + clip.describe(), 12, y);
        g.drawString(clip.source.describe(), 12, y += 20);
        for (String s : lines) g.drawString(s, 12, y += 20);
        g.dispose();
        ImageIO.write(sheet, "png", out);
        for (String s : lines) System.out.println(s);
        System.out.println("Bild: " + out.getPath());
        dr.destroy();
        clip.stop();
        System.exit(0);
    }

    private float clipHeightAfterLoad(GLAutoDrawable dr) {
        clip.update(eye[0], eye[2]);
        clip.await(120_000);
        return clip.height(eye[0], eye[2]);
    }

    static BufferedImage grab(GLAutoDrawable a, int w, int h) {
        GL4 gl = a.getGL().getGL4();
        java.nio.ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
        int fb = a.getContext().getDefaultDrawFramebuffer();
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fb);
        gl.glReadBuffer(fb == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
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

    // ------------------------------------------------------------------ Fenster

    private JPanel panel;

    private void open() {
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(this);
        MouseAdapter m = new MouseAdapter() {
            int px, py;
            @Override public void mousePressed(MouseEvent e) { px = e.getX(); py = e.getY(); canvas.requestFocusInWindow(); }
            @Override public void mouseDragged(MouseEvent e) {
                yaw += (e.getX() - px) * 0.2;
                pitch = Math.max(-89, Math.min(89, pitch - (e.getY() - py) * 0.2));
                px = e.getX();
                py = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) { speed = Math.max(2, Math.min(20_000, speed * Math.pow(1.25, -e.getWheelRotation()))); }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() < 256) keys[e.getKeyCode()] = true; }
            @Override public void keyReleased(KeyEvent e) { if (e.getKeyCode() < 256) keys[e.getKeyCode()] = false; }
        });
        FFrame f = new FFrame("Clipmap · Vorschau des Pakets com.dan.clipmap");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(canvas, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        f.setPreferredFrameSize(new java.awt.Dimension(1500, 880));
        f.setSize(1500, 880);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
    }

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel head = new JLabel("CLIPMAP");
        head.setForeground(ACCENT);
        head.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(head);
        FComboBox qb = new FComboBox(new String[]{"LOW (129²)", "MEDIUM (193²)", "HIGH (257²)", "ULTRA (385²)"});
        qb.setSelectedIndex(2);
        qb.addActionListener(e -> {
            Clipmap.Quality q = Clipmap.Quality.values()[qb.getSelectedIndex()];
            if (q == quality) return;
            Clipmap old = clip;
            quality = q;
            clip = make(q);
            old.stop();
        });
        add(qb);
        slider("Sonnenhöhe", -5, 70, 25, "°", v -> sunElev = v);
        slider("Sonne aus", 0, 359, 250, "°", v -> sunAz = v);
        FCheckBox cv = new FCheckBox("Erdkrümmung mit Refraktion");
        cv.setSelected(true);
        cv.addActionListener(e -> curvature = cv.isSelected());
        add(cv);
        FCheckBox lv = new FCheckBox("Stufen einfärben (hell: Übergang)");
        lv.addActionListener(e -> showLevels = lv.isSelected());
        add(lv);
        FCheckBox wf = new FCheckBox("Gitter");
        wf.addActionListener(e -> wire = wf.isSelected());
        add(wf);
        JLabel info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(info);
        new javax.swing.Timer(300, e -> info.setText(String.format(Locale.GERMAN,
                "<html><body style='width:250px'>%.0f Bilder/s · %.2f ms je Bild (GPU)<br>Auge %.0f / %.0f, %.0f m · Tempo %.0f m/s<br><br>%s<br><br>%s<br><br>%s</body></html>",
                fps, gpuMs, eye[0], eye[2], eye[1], speed, cgl.info(), clip.describe(), clip.source.describe()))).start();
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void slider(String name, int min, int max, int val, String unit, java.util.function.IntConsumer set) {
        JLabel l = new JLabel(name + "  " + val + unit);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        add(l);
        FSlider s = new FSlider(min, max, val);
        s.addChangeListener(e -> { l.setText(name + "  " + s.getValue() + unit); set.accept(s.getValue()); });
        add(s);
    }

    private void add(Component c) {
        if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
        panel.add(Box.createVerticalStrut(6));
    }
}
