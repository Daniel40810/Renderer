package com.dan.netview.demo;

import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.netview.NetLayer;
import com.dan.netview.Ramp;
import com.dan.netview.gl.NetviewGl;
import com.jogamp.common.nio.Buffers;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;

/**
 * Vorführung des Netzblicks (seit LituyaBay1958 Phase 7, Werkbuch 5.12): über einer gerechneten Bucht (12 × 5,2 km,
 * Insel, Gelände bis 420 m) zeigt {@link NetviewGl} alle Arten: laufende Isochronen (alle 10 s, die vorderste leuchtet),
 * Strahlen zu vier Booten, die mit der Front wachsen, Boote als Punkte in der Farbe ihrer Ankunftszeit, eine Messstrecke
 * am Nordufer mit Werten als Farbband, ein Graph über das Wasser und Pfeile in Laufrichtung. Die Laufzeit rechnet die
 * Vorführung selbst (Dijkstra über 16 Nachbarn, 40 m), damit das Package nichts außer JOGL braucht.
 * <p>
 * Fenster: Zeit, Abstand, Ebenen an und aus. {@code --bild datei.png}: vier Ansichten (nah und schräg, Überblick bei
 * 2 und 5 min, flach von weit weg mit ausgedünnten Stufen).
 */
public final class NetviewDemo implements GLEventListener {
    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), MUTED = new Color(140, 156, 170), ACCENT = new Color(240, 200, 110);
    static final int W = 300, H = 130;
    static final double CELL = 40, X0 = -6000, Z0 = -2600;

    // ------------------------------------------------------------------ Bucht und Laufzeit

    /** Tiefe (m, positiv) an (x, z); ≤ 0 Land (dann −Höhe). */
    static double depth(double x, double z) {
        double half = 1300 + 250 * Math.sin(x / 1500.0) + (x > 3800 ? -(x - 3800) * 0.55 : 0);
        double d = half - Math.abs(z - 150 * Math.sin(x / 1100.0));
        double isl = 1 - Math.hypot((x + 300) / 700, (z + 150) / 300);
        if (isl > 0) d = Math.min(d, -isl * 120);
        return d > 0 ? Math.min(140, d * 0.3 + 4) : d * 0.18;
    }

    static double height(double x, double z) {
        double d = depth(x, z);
        if (d > 0) return 0;
        double h = -d;
        return Math.min(420, h * (1 + 0.25 * Math.sin(x / 450.0) * Math.cos(z / 380.0)));
    }

    static double speed(double d) { return d > 0 ? Math.sqrt(9.81 * d) : 0; }

    float[] arrival;
    int[] parent;

    /** Dijkstra über 16 Nachbarn; Quelle: Wasser am Ostende. */
    void solve() {
        int n = W * H;
        double[] t = new double[n];
        Arrays.fill(t, Double.POSITIVE_INFINITY);
        parent = new int[n];
        Arrays.fill(parent, -1);
        float[] c = new float[n];
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) c[j * W + i] = (float) speed(depth(cx(i), cz(j)));
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++)
                if (c[j * W + i] > 0 && cx(i) > 5000 && Math.abs(cz(j) + 300) < 600) { t[j * W + i] = 0; q.add(new double[]{0, j * W + i}); }
        int[][] mv = new int[16][];
        int[][] base = {{1, 0}, {1, 1}, {2, 1}};
        int m = 0;
        for (int[] b : base)
            for (int s = 0; s < 8; s++) {
                int x = (s & 1) == 0 ? b[0] : b[1], y = (s & 1) == 0 ? b[1] : b[0];
                if ((s & 2) != 0) x = -x;
                if ((s & 4) != 0) y = -y;
                boolean dup = false;
                for (int k = 0; k < m; k++) dup |= mv[k][0] == x && mv[k][1] == y;
                if (!dup) mv[m++] = new int[]{x, y};
            }
        while (!q.isEmpty()) {
            double[] e = q.poll();
            int k = (int) e[1];
            if (e[0] > t[k]) continue;
            int i = k % W, j = k / W;
            for (int a = 0; a < m; a++) {
                int ii = i + mv[a][0], jj = j + mv[a][1];
                if (ii < 0 || jj < 0 || ii >= W || jj >= H) continue;
                int kk = jj * W + ii;
                int mi = (int) Math.floor(i + 0.5 + mv[a][0] * 0.5), mj = (int) Math.floor(j + 0.5 + mv[a][1] * 0.5);
                if (c[kk] <= 0 || c[mj * W + mi] <= 0) continue;
                double d = t[k] + Math.hypot(mv[a][0], mv[a][1]) * CELL * 0.5 * (1 / c[k] + 1 / c[kk]);
                if (d < t[kk]) { t[kk] = d; parent[kk] = k; q.add(new double[]{d, kk}); }
            }
        }
        arrival = new float[n];
        for (int k = 0; k < n; k++) arrival[k] = c[k] > 0 && Double.isFinite(t[k]) ? (float) t[k] : Float.NaN;
    }

    static double cx(int i) { return X0 + (i + 0.5) * CELL; }
    static double cz(int j) { return Z0 + (j + 0.5) * CELL; }

    static final double[][] BOATS = {{-5200, 200}, {-2500, -700}, {1100, 50}, {-1200, -150}};
    static final String[] BOAT_NAMES = {"Eingang", "Ankerplatz", "Mitte", "hinter der Insel"};

    final NetLayer iso = new NetLayer("Isochronen"), rays = new NetLayer("Strahlen"), boats = new NetLayer("Boote"),
            band = new NetLayer("Messstrecke"), net = new NetLayer("Netz"), arrows = new NetLayer("Laufrichtung");
    final NetviewGl view = new NetviewGl();
    double maxTime;

    NetviewDemo() {
        solve();
        for (float v : arrival) if (Float.isFinite(v)) maxTime = Math.max(maxTime, v);
        double[] all = new double[(int) (maxTime / 10) + 1], minutes;
        for (int k = 0; k < all.length; k++) all[k] = 10 * (k + 1);
        minutes = Arrays.stream(all).filter(v -> v % 60 == 0).toArray();
        double[] tens = Arrays.stream(all).filter(v -> v % 60 != 0).toArray();
        iso.isolines(arrival, W, H, X0 + CELL / 2, Z0 + CELL / 2, CELL, 1.5f, tens, 0xF0C870, 1.4f);
        iso.isolines(arrival, W, H, X0 + CELL / 2, Z0 + CELL / 2, CELL, 1.5f, minutes, 0xFFE6A0, 2.8f);
        float[] bp = new float[3 * BOATS.length], bv = new float[BOATS.length];
        for (int b = 0; b < BOATS.length; b++) {
            int i = (int) ((BOATS[b][0] - X0) / CELL), j = (int) ((BOATS[b][1] - Z0) / CELL);
            List<float[]> p = new ArrayList<>();
            for (int k = j * W + i; k >= 0; k = parent[k]) p.add(new float[]{(float) cx(k % W), 3, (float) cz(k / W), arrival[k]});
            float[] xyz = new float[3 * p.size()], tt = new float[p.size()];
            for (int s = 0; s < p.size(); s++) { float[] q = p.get(p.size() - 1 - s); xyz[3 * s] = q[0]; xyz[3 * s + 1] = q[1]; xyz[3 * s + 2] = q[2]; tt[s] = q[3]; }
            rays.ray(xyz, tt, 0xFF7860, 2.2f);
            bp[3 * b] = (float) BOATS[b][0]; bp[3 * b + 1] = 4; bp[3 * b + 2] = (float) BOATS[b][1];
            bv[b] = arrival[j * W + i];
        }
        boats.points(bp, bv, -1, 14);
        boats.ramp = Ramp.TIME.copy(0, maxTime);
        // Messstrecke am Nordufer: erste Wasserzelle von Norden je Spalte, Wert wie eine Spurlinie (abnehmend nach Westen)
        List<float[]> shore = new ArrayList<>();
        for (int i = 4; i < W - 4; i += 2) {
            for (int j = 0; j < H; j++) if (depth(cx(i), cz(j)) > 0) { shore.add(new float[]{(float) cx(i), (float) cz(j) - 10}); break; }
        }
        float[] sx = new float[3 * shore.size()], sv = new float[shore.size()];
        for (int s = 0; s < shore.size(); s++) {
            sx[3 * s] = shore.get(s)[0]; sx[3 * s + 1] = 6; sx[3 * s + 2] = shore.get(s)[1];
            sv[s] = (float) (12 + 180 * Math.exp(-(5600 - sx[3 * s]) / 2600.0) * (1 + 0.25 * Math.sin(sx[3 * s] / 300.0)));
        }
        band.band(sx, sv, 9);
        band.ramp = Ramp.HEIGHT.copy(0, 200);
        // Graph: jede zehnte Zelle über Wasser, Kanten zu den acht Nachbarn
        int step = 10, gw = W / step + 1, gh = H / step + 1;
        int[] id = new int[gw * gh];
        Arrays.fill(id, -1);
        List<Float> nodes = new ArrayList<>();
        for (int b = 0; b < gh; b++)
            for (int a = 0; a < gw; a++) {
                int i = Math.min(W - 1, a * step), j = Math.min(H - 1, b * step);
                if (depth(cx(i), cz(j)) > 3) { id[b * gw + a] = nodes.size() / 3; nodes.add((float) cx(i)); nodes.add(2f); nodes.add((float) cz(j)); }
            }
        List<Integer> edges = new ArrayList<>();
        for (int b = 0; b < gh; b++)
            for (int a = 0; a < gw; a++) {
                int p = id[b * gw + a];
                if (p < 0) continue;
                for (int[] d : new int[][]{{1, 0}, {0, 1}, {1, 1}, {1, -1}}) {
                    int aa = a + d[0], bb = b + d[1];
                    if (aa >= gw || bb < 0 || bb >= gh || id[bb * gw + aa] < 0) continue;
                    edges.add(p); edges.add(id[bb * gw + aa]);
                }
            }
        float[] nf = new float[nodes.size()];
        for (int k = 0; k < nf.length; k++) nf[k] = nodes.get(k);
        net.graph(nf, edges.stream().mapToInt(Integer::intValue).toArray(), 0x6E9CC8, 1.2f);
        net.visible = false;
        // Laufrichtung: Gefälle der Ankunftszeit, alle 12 Zellen
        for (int j = 6; j < H - 6; j += 12)
            for (int i = 6; i < W - 6; i += 12) {
                float t0 = arrival[j * W + i], tx = arrival[j * W + i + 1] - arrival[j * W + i - 1], tz = arrival[(j + 1) * W + i] - arrival[(j - 1) * W + i];
                if (!Float.isFinite(t0) || !Float.isFinite(tx) || !Float.isFinite(tz)) continue;
                double l = Math.hypot(tx, tz);
                if (l < 1e-6) continue;
                float x = (float) cx(i), z = (float) cz(j), dx = (float) (tx / l * 260), dz = (float) (tz / l * 260);
                arrows.arrow(new float[]{x - dx / 2, 2.5f, z - dz / 2, x + dx / 2, 2.5f, z + dz / 2}, 0x9FD8FF, 1.6f);
            }
        arrows.visible = false;
        for (NetLayer l : new NetLayer[]{net, arrows, band, iso, rays, boats}) view.add(l);
    }

    // ------------------------------------------------------------------ Gelände (nur für die Vorführung)

    int prog, vao, ibo, nIdx;

    void initGround(GL4 gl) {
        int n = W * H;
        FloatBuffer v = Buffers.newDirectFloatBuffer(n * 7);
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++) {
                double x = cx(i), z = cz(j), y = height(x, z);
                double hx = height(x + CELL, z) - height(x - CELL, z), hz = height(x, z + CELL) - height(x, z - CELL);
                double nx = -hx, ny = 2 * CELL, nz = -hz, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                v.put((float) x).put((float) y).put((float) z).put((float) (nx / nl)).put((float) (ny / nl)).put((float) (nz / nl)).put((float) depth(x, z));
            }
        v.flip();
        IntBuffer ix = Buffers.newDirectIntBuffer((W - 1) * (H - 1) * 6);
        for (int j = 0; j + 1 < H; j++)
            for (int i = 0; i + 1 < W; i++) {
                int a = j * W + i, b = a + 1, c = a + W, d = c + 1;
                ix.put(a).put(c).put(b).put(b).put(c).put(d);
            }
        ix.flip();
        nIdx = ix.limit();
        int[] o = new int[3];
        gl.glGenVertexArrays(1, o, 0);
        gl.glGenBuffers(2, o, 1);
        vao = o[0]; ibo = o[2];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, o[1]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) n * 28, v, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 28, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 28, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 1, GL.GL_FLOAT, false, 28, 24);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) nIdx * 4, ix, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        prog = program(gl, GROUND_VS, GROUND_FS);
    }

    static final String GROUND_VS = """
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNrm;
            layout(location = 2) in float aDepth;
            uniform mat4 uVP;
            out vec3 vN; out float vD, vH; out vec3 vW;
            void main() { vN = aNrm; vD = aDepth; vH = aPos.y; vW = aPos; gl_Position = uVP * vec4(aPos, 1.0); }
            """;
    static final String GROUND_FS = """
            in vec3 vN; in float vD, vH; in vec3 vW;
            uniform vec3 uEye;
            out vec4 frag;
            void main() {
                vec3 L = normalize(vec3(-0.5, 0.75, -0.35));
                float lam = max(0.0, dot(normalize(vN), L));
                vec3 c;
                if (vD > 0.0) {
                    float t = clamp(vD / 140.0, 0.0, 1.0);
                    c = mix(vec3(0.16, 0.34, 0.42), vec3(0.04, 0.12, 0.20), t);
                } else {
                    float t = clamp(vH / 420.0, 0.0, 1.0);
                    c = mix(vec3(0.20, 0.27, 0.18), vec3(0.42, 0.42, 0.40), smoothstep(0.35, 1.0, t)) * (0.35 + 0.75 * lam);
                }
                float fog = 1.0 - exp(-length(vW - uEye) / 26000.0);
                frag = vec4(mix(c, vec3(0.043, 0.067, 0.094), fog), 1.0);
            }
            """;

    static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (String[] s : new String[][]{{vs, "v"}, {fs, "f"}}) {
            int sh = gl.glCreateShader(s[1].equals("v") ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + s[0]}, null, 0);
            gl.glCompileShader(sh);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[8192];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("Shader: " + new String(b, 0, len[0], StandardCharsets.UTF_8));
            }
            gl.glAttachShader(p, sh);
            gl.glDeleteShader(sh);
        }
        gl.glLinkProgram(p);
        return p;
    }

    // ------------------------------------------------------------------ Kamera

    /** Auge und Ziel je Ansicht. */
    static final String[] VIEWS = {"Nah über der Insel", "Überblick", "Überblick, alles", "Flach von See (Stufen ausgedünnt)"};

    static double[][] camera(int v) {
        return switch (v) {
            case 0 -> new double[][]{{-300, 1500, 2600}, {-600, 0, -300}};
            case 1, 2 -> new double[][]{{0, 6600, 4600}, {0, 0, -250}};
            default -> new double[][]{{-12500, 1000, 1600}, {1500, 0, -300}};
        };
    }

    static float[] viewProj(double[] eye, double[] at, double aspect) {
        double fx = at[0] - eye[0], fy = at[1] - eye[1], fz = at[2] - eye[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = fy * 0 - fz * 1, sy = fz * 0 - fx * 0, sz = fx * 1 - fy * 0, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl; sy /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        double[] v = {sx, ux, -fx, 0, sy, uy, -fy, 0, sz, uz, -fz, 0,
                -(sx * eye[0] + sy * eye[1] + sz * eye[2]), -(ux * eye[0] + uy * eye[1] + uz * eye[2]), fx * eye[0] + fy * eye[1] + fz * eye[2], 1};
        double near = 20, far = 60000, f = 1 / Math.tan(Math.toRadians(45) / 2);
        double[] p = {f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) / (near - far), -1, 0, 0, 2 * far * near / (near - far), 0};
        float[] m = new float[16];
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += p[k * 4 + r] * v[c * 4 + k];
                m[c * 4 + r] = (float) s;
            }
        return m;
    }

    /** Zeichnet die Ansicht v in das Rechteck (x, y, w, h) zur Zeit t. */
    void frame(GL4 gl, int v, int x, int y, int w, int h, double t) {
        double[][] c = camera(v);
        if (v == 0 || v == 3) c[0] = scale(c[0], c[1], distance);
        float[] vp = viewProj(c[0], c[1], w / (double) h);
        gl.glViewport(x, y, w, h);
        gl.glEnable(GL.GL_SCISSOR_TEST);
        gl.glScissor(x, y, w, h);
        gl.glClearColor(0.043f, 0.067f, 0.094f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL.GL_SCISSOR_TEST);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, vp, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uEye"), (float) c[0][0], (float) c[0][1], (float) c[0][2]);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, nIdx, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
        view.draw(gl, vp, c[0], w, h, t);
    }

    double distance = 1;

    static double[] scale(double[] eye, double[] at, double s) {
        return new double[]{at[0] + (eye[0] - at[0]) * s, at[1] + (eye[1] - at[1]) * s, at[2] + (eye[2] - at[2]) * s};
    }

    // ------------------------------------------------------------------ GLEventListener

    int viewIdx = 1;
    double time = 0;
    boolean play = true;
    long last;

    @Override public void init(GLAutoDrawable d) { GL4 gl = d.getGL().getGL4(); initGround(gl); view.init(gl); last = System.nanoTime(); }
    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { view.dispose(d.getGL().getGL4()); }

    @Override public void display(GLAutoDrawable d) {
        long now = System.nanoTime();
        if (play) { time += (now - last) / 1e9 * 30; if (time > maxTime + 30) time = 0; }
        last = now;
        GL4 gl = d.getGL().getGL4();
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        frame(gl, viewIdx, 0, 0, d.getSurfaceWidth(), d.getSurfaceHeight(), time);
    }

    // ------------------------------------------------------------------ ohne Fenster

    public static void main(String[] a) throws Exception {
        List<String> l = List.of(a);
        int bi = l.indexOf("--bild");
        if (bi >= 0) { bild(new File(a[bi + 1])); return; }
        SwingUtilities.invokeLater(() -> new NetviewDemo().open());
    }

    static void bild(File out) throws Exception {
        int PW = 960, PH = 540, Wd = 2 * PW, Hd = 2 * PH;
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        caps.setDoubleBuffered(false);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, Wd, Hd);
        NetviewDemo d = new NetviewDemo();
        dr.addGLEventListener(new GLEventListener() {
            public void init(GLAutoDrawable x) { GL4 gl = x.getGL().getGL4(); d.initGround(gl); d.view.init(gl); }
            public void dispose(GLAutoDrawable x) { }
            public void reshape(GLAutoDrawable x, int a, int b, int c, int e) { }
            public void display(GLAutoDrawable x) { }
        });
        dr.display();
        double[] times = {150, 120, 1e9, 1e9};
        long[] drawn = new long[4];
        BufferedImage[] img = new BufferedImage[1];
        String[] info = new String[1];
        dr.invoke(true, x -> {
            GL4 gl = x.getGL().getGL4();
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, x.getContext().getDefaultDrawFramebuffer());
            for (int v = 0; v < 4; v++) {
                d.net.visible = v == 2;
                d.arrows.visible = v == 1;
                d.frame(gl, v, (v % 2) * PW, (1 - v / 2) * PH, PW, PH, times[v]);
                drawn[v] = d.view.drawn();
            }
            gl.glFinish();
            info[0] = d.view.info();
            img[0] = grab(gl, x.getContext().getDefaultDrawFramebuffer(), Wd, Hd);
            return true;
        });
        BufferedImage sheet = new BufferedImage(Wd, Hd + 70, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, Wd, Hd + 70);
        g.drawImage(img[0], 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String[] what = {"Front bei 150 s, Strahlen wachsen mit", "bei 120 s, mit Pfeilen in Laufrichtung", "alle Isochronen, Graph über das Wasser", "alle Isochronen, 11 bis 18 km entfernt"};
        for (int v = 0; v < 4; v++)
            label(g, (v % 2) * PW + 8, (v / 2) * PH + 8, VIEWS[v], String.format(Locale.GERMAN, "%s · %,d Instanzen gezeichnet", what[v], drawn[v]));
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(String.format(Locale.GERMAN, "com.dan.netview · NetviewGl: Strecken, Pfeile, Punkte, Isolinien, Strahlen, Bänder und Graphen als Vierecke in Bildpunkten, ein glMultiDrawArraysIndirect je Ebene · Laufzeit bis %.0f s (16 Nachbarn, 40 m)", d.maxTime), 12, Hd + 26);
        g.drawString(info[0] + " · Isochronen alle 10 s (Minute kräftig), Boote in der Farbe der Ankunft, Messstrecke am Nordufer als Band (Höhe 0 bis 200 m)", 12, Hd + 50);
        g.dispose();
        ImageIO.write(sheet, "png", out);
        for (int v = 0; v < 4; v++) System.out.printf(Locale.GERMAN, "%s: %,d Instanzen%n", VIEWS[v], drawn[v]);
        System.out.println(info[0]);
        System.out.println("Bild: " + out.getPath());
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
        FFrame f = new FFrame("Netzblick · com.dan.netview");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(canvas, BorderLayout.CENTER);
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel head = new JLabel("NETZBLICK");
        head.setForeground(ACCENT);
        head.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(head);
        FComboBox v = new FComboBox(VIEWS);
        v.setSelectedIndex(viewIdx);
        v.addActionListener(e -> viewIdx = v.getSelectedIndex());
        add(v);
        JLabel tl = new JLabel("Zeit 0 s");
        tl.setForeground(INK);
        add(tl);
        FSlider ts = new FSlider(0, (int) maxTime + 30, 0);
        ts.addChangeListener(e -> { if (!play) { time = ts.getValue(); } });
        add(ts);
        FCheckBox pl = box("Front läuft (30 ×)", true, b -> play = b);
        add(pl);
        JLabel dl = new JLabel("Abstand × 1,0");
        dl.setForeground(INK);
        add(dl);
        FSlider ds = new FSlider(3, 60, 10);
        ds.addChangeListener(e -> { distance = ds.getValue() / 10.0; dl.setText(String.format(Locale.GERMAN, "Abstand × %.1f", distance)); });
        add(ds);
        for (NetLayer l : view.layers()) add(box(l.name, l.visible, b -> l.visible = b));
        JLabel info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(info);
        new javax.swing.Timer(250, e -> {
            tl.setText(String.format(Locale.GERMAN, "Zeit %.0f s", time));
            if (play) ts.setValue((int) time);
            info.setText("<html><body style='width:220px'>" + view.info() + "</body></html>");
        }).start();
        panel.add(Box.createVerticalGlue());
        root.add(panel, BorderLayout.EAST);
        f.setSize(1500, 900);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
    }

    private FCheckBox box(String name, boolean on, java.util.function.Consumer<Boolean> s) {
        FCheckBox c = new FCheckBox(name);
        c.setSelected(on);
        c.setOpaque(false);
        c.setForeground(INK);
        c.addActionListener(e -> s.accept(c.isSelected()));
        return c;
    }

    private void add(Component c) {
        if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
        panel.add(Box.createVerticalStrut(6));
    }
}
