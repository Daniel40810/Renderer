package com.dan.surface.demo;

import com.dan.fcheckbox.FCheckBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.surface.Katalog;
import com.dan.surface.Material;
import com.dan.surface.MaterialSet;
import com.dan.surface.gl.SurfaceGl;
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Kontaktbogen der Materialien (Werkbuch 5.10, 8): jedes Material als Boden und Wand unter tiefer Abendsonne, rechts
 * ein nasser Streifen; darunter „1 km Kiesstrand“ aus Augenhöhe, oben einfach gekachelt, unten hex-gekachelt. Regler
 * für Sonnenhöhe, Nässe und Schnee.
 * <p>
 * Daten: {@code --texturen <ordner>} (gebackene Materialien, etwa {@code daten/texturen} von LituyaBay1958); ohne Angabe
 * {@code daten/texturen}, wenn da, sonst gerechnete Probesätze ({@link Probe}). {@code --kante 1024} wählt die Größe auf
 * der Karte. Ohne Fenster: {@code --bild datei.png} schreibt den Bogen mit Herkunft, Lizenz und Zeiten.
 */
public final class SurfaceDemo implements GLEventListener {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(200, 170, 120);
    static final int COLS = 4;

    final List<Material> list = Katalog.fjord();
    final MaterialSet set;
    final int kante;
    final SurfaceGl sgl = new SurfaceGl();
    volatile double sunElev = 12, wet = 1, snow;
    volatile boolean hexOn = true;
    private int prog, vao, timer;
    volatile double gpuMs;
    private boolean loaded;

    SurfaceDemo(MaterialSet set, int kante) {
        this.set = set;
        this.kante = kante;
    }

    static final String VERT = "#version 430 core\nout vec2 vUV;\nvoid main(){ vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vUV = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";

    static final String FRAG = """
            in vec2 vUV;
            out vec4 frag;
            uniform int uLayer, uMode;      // Modus 0 Bogen, 1 Strand einfach, 2 Strand hex
            uniform vec3 uSun;
            uniform float uWet, uSnow, uAspect, uResY;
            vec3 sunC() { float e = uSun.y; return mix(vec3(1.0, 0.55, 0.30), vec3(1.0, 0.95, 0.88), smoothstep(0.05, 0.5, e)) * 3.2 * smoothstep(-0.02, 0.06, e); }
            vec3 sky(vec3 d) { float up = clamp(d.y, 0.0, 1.0); return mix(vec3(0.66, 0.74, 0.86), vec3(0.22, 0.38, 0.72), pow(up, 0.5)) * 0.9; }
            vec3 shade(Surf s, vec3 d) {
                vec3 n = s.normal, v = -d, h = normalize(uSun + v);
                float nl = max(dot(n, uSun), 0.0);
                vec3 col = s.albedo / 3.14159 * (sunC() * nl + sky(vec3(0, 1, 0)) * 1.1 * s.ao);
                float F = 0.04 + 0.96 * pow(1.0 - max(dot(h, v), 0.0), 5.0);
                float a = max(s.rough * s.rough, 0.002), nh = max(dot(n, h), 0.0);
                float D = a * a / (3.14159 * pow(nh * nh * (a * a - 1.0) + 1.0, 2.0));
                col += sunC() * D * F * nl * 0.25;
                col += sky(reflect(d, n)) * F * (1.0 - s.rough) * 0.5;
                return col;
            }
            vec4 plainA(int L, vec2 uv) { return texture(uSurfA, vec3(uv, float(L))); }
            void main() {
                vec2 ndc = vUV * 2.0 - 1.0;
                vec3 cam, fwd;
                if (uMode == 0) { cam = vec3(0.0, 1.7, 3.6); fwd = normalize(vec3(0.0, -0.35, -1.0)); }
                else { cam = vec3(0.0, 4.0, 0.0); fwd = normalize(vec3(1.0, -0.24, 0.0)); }   // 4 m über dem Strand, Kimm im oberen Viertel
                vec3 right = normalize(cross(fwd, vec3(0, 1, 0))), up = cross(right, fwd);
                float tn = tan(radians(uMode == 0 ? 30.0 : 25.0));
                vec3 d = normalize(fwd + ndc.x * tn * uAspect * right + ndc.y * tn * up);
                float tg = d.y < 0.0 ? -cam.y / d.y : 1e9;
                float tw = (uMode == 0 && d.z < 0.0) ? (cam.z + 1.4) / -d.z : 1e9;
                vec3 p, gN;
                float t;
                if (tw < tg && (cam + d * tw).y < 3.5) { t = tw; gN = vec3(0, 0, 1); } else { t = tg; gN = vec3(0, 1, 0); }
                if (t > 1e8) { frag = vec4(pow(sky(d) * 0.9, vec3(1.0 / 2.2)), 1.0); return; }
                p = cam + d * t;
                float px = t * 2.0 * tn / uResY;
                vec3 col;
                if (uMode == 1) {
                    vec4 P = uSurfParam[uLayer];
                    Surf s;
                    vec4 A = plainA(uLayer, p.xz * P.x);
                    s.albedo = A.rgb; s.height = A.a; s.normal = gN; s.rough = uSurfMean[uLayer].a; s.ao = 1.0;
                    col = shade(s, d);
                } else {
                    Surf s = surfSample(uLayer, p, gN, px);
                    if (uMode == 0 && p.x > 0.75) surfWet(s, uLayer, uWet, gN);
                    if (uSnow > 0.0) surfSnow(s, uSnow, gN);
                    col = shade(s, d);
                }
                col = mix(col, sky(normalize(vec3(d.x, 0.02, d.z))), 1.0 - exp(-t / 6000.0));
                col *= 0.85;
                col = col * (2.51 * col + 0.03) / (col * (2.43 * col + 0.59) + 0.14);
                frag = vec4(pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2)), 1.0);
            }
            """;

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        prog = program(gl, VERT, "#version 430 core\n" + SurfaceGl.glsl() + FRAG);
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        gl.glGenQueries(1, v, 0);
        timer = v[0];
    }

    void ensureLoaded(GL4 gl) {
        if (loaded) return;
        loaded = true;
        try {
            sgl.load(gl, set, kante, true);
            System.out.println("surface: " + sgl.info());
        } catch (Exception e) {
            System.err.println("surface: Laden fehlgeschlagen: " + e);
        }
    }

    static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (String[] s : new String[][]{{vs, "v"}, {fs, "f"}}) {
            int sh = gl.glCreateShader(s[1].equals("v") ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{s[0]}, null, 0);
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

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    /** Eine Zelle: Schicht layer im Rechteck (x, y, w, h) des Ziels; mode 0 Bogen, 1/2 Strand. */
    void cell(GL4 gl, int layer, int mode, int x, int y, int w, int h) {
        gl.glViewport(x, y, w, h);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uLayer"), layer);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uMode"), mode);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uAspect"), w / (float) h);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uResY"), h);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
    }

    /** Der ganze Bogen in w × h: 4 Spalten, darunter zwei Streifen Strand. */
    void sheet(GL4 gl, int w, int h, int fbo) {
        ensureLoaded(gl);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(BG.getRed() / 255f, BG.getGreen() / 255f, BG.getBlue() / 255f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        if (!sgl.ready()) return;
        gl.glUseProgram(prog);
        sgl.bind(gl, prog, 0, 1, 2, 0.35);
        double se = Math.toRadians(sunElev), sa = Math.toRadians(235);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSun"), (float) (Math.cos(se) * Math.sin(sa)), (float) Math.sin(se), (float) (-Math.cos(se) * Math.cos(sa)));
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uWet"), (float) wet);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uSnow"), (float) snow);
        gl.glBindVertexArray(vao);
        int n = set.layers().size(), rows = (n + COLS - 1) / COLS;
        Layout lay = Layout.of(w, h, rows);
        gl.glBeginQuery(GL4.GL_TIME_ELAPSED, timer);
        for (int i = 0; i < n; i++) cell(gl, i, 0, (i % COLS) * lay.cw, h - (i / COLS + 1) * lay.ch, lay.cw, lay.ch);
        int kies = Math.max(0, set.layer("strandkies"));
        cell(gl, kies, 1, 0, lay.sh, w, lay.sh);
        cell(gl, kies, hexOn ? 2 : 1, 0, 0, w, lay.sh);
        gl.glEndQuery(GL4.GL_TIME_ELAPSED);
        long[] ns = new long[1];
        gl.glGetQueryObjecti64v(timer, GL4.GL_QUERY_RESULT, ns, 0);
        gpuMs = ns[0] / 1e6;
        gl.glBindVertexArray(0);
    }

    record Layout(int cw, int ch, int sh) {
        static Layout of(int w, int h, int rows) { return new Layout(w / COLS, (int) (h * 0.64 / rows), (int) (h * 0.15)); }
    }

    @Override public void display(GLAutoDrawable d) { sheet(d.getGL().getGL4(), d.getSurfaceWidth(), d.getSurfaceHeight(), d.getContext().getDefaultDrawFramebuffer()); }

    @Override public void dispose(GLAutoDrawable d) { sgl.dispose(d.getGL().getGL4()); }

    // ------------------------------------------------------------------ Start

    public static void main(String[] a) throws Exception {
        List<String> l = List.of(a);
        int ti = l.indexOf("--texturen"), ki = l.indexOf("--kante");
        File dir = ti >= 0 ? new File(a[ti + 1]) : new File("daten/texturen");
        int kante = ki >= 0 ? Integer.parseInt(a[ki + 1]) : 1024;
        MaterialSet set = new MaterialSet(dir, Katalog.fjord());
        String woher = dir.getPath();
        if (set.layers().isEmpty()) {
            File tmp = java.nio.file.Files.createTempDirectory("surface_probe").toFile();
            Probe.bakeAll(Katalog.fjord(), new File(tmp, "roh"), new File(tmp, "texturen"), 512);
            set = new MaterialSet(new File(tmp, "texturen"), Katalog.fjord());
            woher = "Probesätze (gerechnet, keine Daten in " + dir.getPath() + ")";
        }
        System.out.println("surface: " + set.describe() + " aus " + woher);
        SurfaceDemo d = new SurfaceDemo(set, kante);
        int bi = l.indexOf("--bild");
        if (bi >= 0) { d.bild(new File(a[bi + 1]), woher); return; }
        SwingUtilities.invokeLater(d::open);
    }

    void bild(File out, String woher) throws Exception {
        int w = 1600, h = 1500;
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        dr.addGLEventListener(this);
        long t0 = System.nanoTime();
        dr.display();
        double first = (System.nanoTime() - t0) / 1e6;
        for (int i = 0; i < 5; i++) dr.display();
        final BufferedImage[] img = new BufferedImage[1];
        dr.invoke(true, x -> { img[0] = grab(x, w, h); return true; });
        BufferedImage im = img[0];
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int n = set.layers().size(), rows = (n + COLS - 1) / COLS;
        Layout lay = Layout.of(w, h, rows);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (MaterialSet.Layer l : set.layers()) {
            int x = (l.index() % COLS) * lay.cw(), y = (l.index() / COLS) * lay.ch();
            Material m = l.material();
            label(g, x + 6, y + 6, m.name + " · " + Material_fmt(m.size) + " m", m.derivedFrom != null ? "abgeleitet aus " + m.derivedFrom : m.provider + " " + m.assetId + " · " + m.license);
        }
        int sy = h - 2 * lay.sh();
        label(g, 6, sy + 6, "1 km Kiesstrand, einfach gekachelt", "Wiederholung alle " + Material_fmt(set.layers().isEmpty() ? 0 : set.layers().get(Math.max(0, set.layer("strandkies"))).material().size) + " m");
        label(g, 6, sy + lay.sh() + 6, "1 km Kiesstrand, hex-gekachelt (com.dan.surface)", "SurfaceCheck: Autokorrelation < 0,3");
        g.setColor(BG);
        int fy = rows * lay.ch();
        g.fillRect(0, fy, w, sy - fy);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(String.format(Locale.GERMAN, "com.dan.surface · %s · %s", set.describe(), woher), 10, fy + 20);
        g.drawString(String.format(Locale.GERMAN, "%s · Bogen %.2f ms auf der Grafikkarte · erstes Bild mit Laden %.0f ms · rechts je Zelle nass", sgl.info(), gpuMs, first), 10, fy + 40);
        g.dispose();
        ImageIO.write(im, "png", out);
        System.out.println(sgl.info());
        System.out.printf(Locale.GERMAN, "Bogen: %.2f ms auf der Grafikkarte; erstes Bild mit Laden %.0f ms%n", gpuMs, first);
        System.out.println("Bild: " + out.getPath());
        dr.destroy();
        System.exit(0);
    }

    static String Material_fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.GERMAN, "%.1f", v); }

    static void label(Graphics2D g, int x, int y, String a, String b) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        int w = Math.max(g.getFontMetrics().stringWidth(a), g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 11)).stringWidth(b)) + 12;
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(x, y, w, 36);
        g.setColor(Color.WHITE);
        g.drawString(a, x + 6, y + 15);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(new Color(220, 220, 220));
        g.drawString(b, x + 6, y + 30);
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
        FFrame f = new FFrame("Materialien · Vorschau des Pakets com.dan.surface");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(canvas, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        f.setPreferredFrameSize(new java.awt.Dimension(1500, 950));
        f.setSize(1500, 950);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
    }

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel head = new JLabel("MATERIALIEN");
        head.setForeground(ACCENT);
        head.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(head);
        slider("Sonnenhöhe", -2, 60, 12, "°", v -> sunElev = v);
        slider("Nässe (rechter Streifen)", 0, 100, 100, "%", v -> wet = v / 100.0);
        slider("Schnee", 0, 100, 0, "%", v -> snow = v / 100.0);
        FCheckBox hx = new FCheckBox("Strand unten hex-gekachelt");
        hx.setSelected(true);
        hx.addActionListener(e -> hexOn = hx.isSelected());
        add(hx);
        JLabel info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(info);
        new javax.swing.Timer(400, e -> info.setText(String.format(Locale.GERMAN, "<html><body style='width:240px'>%.2f ms auf der Grafikkarte<br><br>%s<br><br>%s</body></html>",
                gpuMs, sgl.info(), set.describe()))).start();
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void slider(String name, int min, int max, int val, String unit, java.util.function.IntConsumer s) {
        JLabel l = new JLabel(name + "  " + val + unit);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
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
}
