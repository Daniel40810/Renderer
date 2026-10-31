package com.dan.surf.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.surf.BreakerMesh;
import com.dan.surf.Surf;
import com.dan.surf.Swash;
import com.dan.surf.WaveTrain;
import com.dan.surf.gl.BreakerGl;
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

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Drei Becken nebeneinander (seit Sylt Phase 6, F9 A): Schwallbrecher auf einem flachen Riff 1:100 (8 s), Sturzbrecher
 * auf einem Strand 1:20 (8 s), Reflexionsbrecher an einem steilen Hang 1:5 (10 s), je 1 m H_s, 60 m breit. Jedes Becken
 * hat seinen Wellenzug ({@link WaveTrain}), das Netz ({@link BreakerMesh}) und eine Kamera schräg von der Seite auf die
 * Brechzone; wo die Lippe eines Sturzbrechers aufschlägt, meldet {@link WaveTrain#onImpact} das Ereignis (die Gischt
 * daraus macht die Szene mit {@code com.dan.spray}; ein Package bindet kein anderes, Paketvertrag Punkt 9). Unten die Zeiten (Netz auf dem
 * Prozessor, Zeichnen auf der Grafikkarte).
 * <p>
 * Seit Sylt Phase 7 laufen die Walzen als Schwall ({@link Swash}) den Strand hinauf und zurück; der Sand zeigt Nässe,
 * Wasserfilm, Blasen und den Spülsaum. Taste S wechselt den Blick zwischen Brandung und Strand.
 * <p>
 * {@code SurfBreakers --bild datei.png [breite]}: je Becken ein Bild mitten im Brechen und eines nach anderthalb
 * Minuten vom Strand (Auflauf, nasser Sand, Spülsaum), sechs Ansichten.
 */
public final class SurfBreakers implements GLEventListener {
    /** Ein Becken. */
    public static final class Tank {
        public final String name;
        public final WaveTrain train;
        /** Auflauf und Rücklauf (seit Sylt Phase 7). */
        public final Swash swash;
        public final BreakerMesh mesh = new BreakerMesh();
        public final BreakerMesh bed;
        public final double xEnd, slope, flat, depth0, xBreak;
        /** Aufschläge der Lippe bisher (aus {@link WaveTrain#onImpact}). */
        public int impacts;
        public double yaw = -125, pitch = 9, dist = 26, fovy = 50;

        public Tank(String name, double slope, double flat, double depth0, double tp, long seed) {
            this.name = name;
            this.slope = slope;
            this.flat = flat;
            this.depth0 = depth0;
            // Strand hinter dem Ufer: hoch genug für den Auflauf bei H_s 2,5 m (höchstens 100 m lang)
            xEnd = flat + depth0 / slope + Math.max(12, Math.min(1.6 / slope, 100));
            train = new WaveTrain(48, 0, xEnd, 60);
            train.profile = x -> x < flat ? -depth0 : -depth0 + (x - flat) * slope;
            train.hs = 1.0;
            train.tp = tp;
            train.seed = seed;
            // Brechzone: wo γ·h die größeren Wellen trifft (H ≈ 1,2·H_s·Shoaling)
            double hb = 1.25 * train.hs / train.gamma;
            xBreak = flat + (depth0 - hb) / slope;
            // der Schwall beginnt, wo bei H_s 2,5 m der Rand läge (1,25·H_s tief)
            double xa = flat + Math.max(0, depth0 - 3.3) / slope;
            swash = new Swash(train, xa, xEnd, 0.5);
            swash.onSwash = (x, y, z, kind, strength) -> { if (kind == Swash.Kind.RUNUP) runups++; else if (kind == Swash.Kind.COLLISION) collisions++; };
            bed = BreakerMesh.bed(train, swash, 120);
            train.onImpact = (x, y, z, vol, speed) -> impacts++;
            H0 = train.hs / Surf.shoaling(tp, depth0);
        }

        /** Auflaufspitzen und Stöße bisher (aus {@link Swash#onSwash}); Höhe draußen (aufs Tiefe zurückgerechnet). */
        public int runups, collisions;
        public double H0;
        /** Blick auf die Brechzone (false) oder auf den Strand (true). */
        public boolean shore;

        public void step(double dt) {
            train.step(dt);
            swash.step(dt, train);
        }

        /** Stockdon für dieses Becken bei der jetzigen See. */
        public double stockdon() { return Swash.stockdon(train.hs / Surf.shoaling(train.tp, depth0), train.tp, slope); }

        /** Mitte des Blicks: die Brechzone, mitten im Becken. */
        public double[] target() {
            if (shore) return new double[]{flat + depth0 / slope + 0.15 / slope, 0.1, train.width * 0.5};
            return new double[]{xBreak + 2, 0.4, train.width * 0.5};
        }

        public double[] eye() {
            double[] t = target();
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            // Blickrichtung: yaw 0 = zur Küste (+x), −90 = zur Seite (+z)
            double fx = Math.cos(yr) * Math.cos(pr), fz = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr);
            return new double[]{t[0] - fx * dist, t[1] - fy * dist, t[2] - fz * dist};
        }

        public float[] viewProj(double aspect, boolean reversed) {
            double[] e = eye(), t = target();
            return mul(perspective(fovy, aspect, 0.3, 4000, reversed), lookAt(e[0], e[1], e[2], t[0], t[1], t[2]));
        }

        /** Wie weit eine Welle in der Mitte des Beckens gerade bricht (Fortschritt, NaN: keine). */
        public double breakingNow() {
            int r = train.rows / 2;
            double best = Double.NaN;
            for (WaveTrain.Wave w : train.waves) {
                double p = train.progress(w, r);
                if (!Double.isNaN(p) && p < 1.2 && Math.abs(w.x - xBreak) < 25 && (Double.isNaN(best) || p > best)) best = p;
            }
            return best;
        }
    }

    public final Tank[] tanks = {
            new Tank("Schwallbrecher · Riff 1:100", 1 / 100.0, 0, 3.0, 8, 3),
            new Tank("Sturzbrecher · Strand 1:20", 1 / 20.0, 30, 3.0, 8, 5),
            new Tank("Reflexionsbrecher · Hang 1:5", 1 / 5.0, 60, 3.5, 10, 7)};
    private final BreakerGl gl = new BreakerGl();
    private boolean ready;
    private int w = 1, h = 1, frame;
    private final int[] query = new int[2];
    public volatile double gpuMs, cpuMs, speed = 1;
    private long last;
    /** Nur ein Becken (für Bilder), sonst −1. */
    public int only = -1;
    public boolean reversed;

    public SurfBreakers() {
        // vom Strand seitlich auf die anlaufende Front: die Lippe im Gegenlicht
        tanks[0].yaw = -125; tanks[0].dist = 26;
        tanks[1].yaw = -125; tanks[1].dist = 15; tanks[1].pitch = 7;
        tanks[2].yaw = -125; tanks[2].dist = 20;
    }

    static BreakerGl.Light light() {
        BreakerGl.Light l = new BreakerGl.Light();
        // Sonne tief über der See, hinter den Brechern (Licht durch die Lippe)
        l.sunDir = new double[]{-0.75, 0.45, 0.35};
        double ll = Math.sqrt(l.sunDir[0] * l.sunDir[0] + l.sunDir[1] * l.sunDir[1] + l.sunDir[2] * l.sunDir[2]);
        for (int i = 0; i < 3; i++) l.sunDir[i] /= ll;
        l.display = 1.6;
        return l;
    }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        for (int i = 0; i < tanks.length; i++) gl.uploadBed(g, i, tanks[i].bed);
        g.glGenQueries(2, query, 0);
        last = System.nanoTime();
        ready = true;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { w = Math.max(1, ww); h = Math.max(1, hh); }

    /** Alle Becken einen Schritt weiter (Szenenzeit). */
    public void step(double dt) {
        for (Tank t : tanks) t.step(dt);
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        if (!ready) return;
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - last) / 1e9) * speed;
        last = now;
        if (only < 0 && dt > 0) step(dt);
        w = Math.max(1, d.getSurfaceWidth());
        h = Math.max(1, d.getSurfaceHeight());
        long c0 = System.nanoTime();
        for (int i = 0; i < tanks.length; i++)
            if (only < 0 || only == i) {
                tanks[i].mesh.build(tanks[i].train, tanks[i].xEnd, tanks[i].swash);
                tanks[i].bed.updateBed(tanks[i].swash);
            }
        cpuMs = cpuMs * 0.9 + (System.nanoTime() - c0) / 1e6 * 0.1;
        BreakerGl.Light l = light();
        double[] sky = l.skyHorizon;
        g.glClearColor((float) disp(sky[0] * l.display), (float) disp(sky[1] * l.display), (float) disp(sky[2] * l.display), 1);
        g.glClearDepth(1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        if (frame > 2) {
            long[] t = new long[2];
            g.glGetQueryObjecti64v(query[0], GL4.GL_QUERY_RESULT, t, 0);
            g.glGetQueryObjecti64v(query[1], GL4.GL_QUERY_RESULT, t, 1);
            gpuMs = gpuMs * 0.9 + (t[1] - t[0]) / 1e6 * 0.1;
        }
        g.glQueryCounter(query[0], GL4.GL_TIMESTAMP);
        int n = only < 0 ? tanks.length : 1;
        for (int k = 0; k < n; k++) {
            int i = only < 0 ? k : only;
            Tank t = tanks[i];
            int vw = w / n;
            g.glViewport(k * vw, 0, vw, h);
            gl.upload(g, i, t.mesh);
            gl.refreshBed(g, i, t.bed);
            BreakerGl.View v = new BreakerGl.View();
            v.viewProj = t.viewProj(vw / (double) h, false);
            v.eye = t.eye();
            gl.draw(g, i, v, l, t.train.time(), Double.NaN);
        }
        g.glQueryCounter(query[1], GL4.GL_TIMESTAMP);
        frame++;
    }

    static double disp(double c) { return Math.pow(1 - Math.exp(-c), 1 / 2.2); }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.dispose(g);
        g.glDeleteQueries(2, query, 0);
    }

    // ------------------------------------------------------------------ Bild und Fenster

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        if (relaunch(args)) return;
        SwingUtilities.invokeLater(SurfBreakers::open);
    }

    /**
     * JOGL braucht für die GLCanvas unter Windows Zugriff auf {@code sun.awt} (ab Java 17 nur mit {@code --add-opens},
     * sonst „Unable to determine GraphicsConfiguration“). Fehlt er, startet die Vorführung sich selbst noch einmal mit
     * den Schaltern, wie sie Sylt in {@code run.jvmargs} setzt.
     */
    static boolean relaunch(String[] args) throws Exception {
        Module desk = ModuleLayer.boot().findModule("java.desktop").orElse(null);
        if (desk == null || desk.isOpen("sun.awt", SurfBreakers.class.getModule()) || Boolean.getBoolean("surf.relaunched")) return false;
        String exe = ProcessHandle.current().info().command().orElse("java");
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.Arrays.asList(exe, "-Dsurf.relaunched=true",
                "--enable-native-access=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.java2d=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"), SurfBreakers.class.getName()));
        cmd.addAll(java.util.Arrays.asList(args));
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        System.exit(p.waitFor());
        return true;
    }

    /** Läuft das Becken i vor, bis dort eine Welle mitten im Brechen steht (Fortschritt im Fenster), höchstens 120 s. */
    public static void advanceToBreaking(Tank t, double pLo, double pHi) {
        for (int i = 0; i < 120 * 60; i++) {
            t.step(1 / 60.0);
            double p = t.breakingNow();
            if (i > 600 && !Double.isNaN(p) && p >= pLo && p <= pHi) return;
        }
    }

    static void bild(File out, int width) throws Exception {
        int tw = width / 3, th = tw * 3 / 4;
        BufferedImage img = new BufferedImage(tw * 3, th * 2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        SurfBreakers s = new SurfBreakers();
        s.speed = 0;
        d.addGLEventListener(s);
        Graphics2D g = img.createGraphics();
        double[][] window = {{0.4, 0.7}, {0.55, 0.75}, {0.5, 0.8}};
        for (int row = 0; row < 2; row++)
            for (int i = 0; i < 3; i++) {
                Tank t = s.tanks[i];
                if (row == 0) advanceToBreaking(t, window[i][0], window[i][1]);
                else { for (int k = 0; k < 90 * 60; k++) t.step(1 / 60.0); t.shore = true; t.dist = 18; t.pitch = 24; t.yaw = -150; }
                s.only = i;
                for (int f = 0; f < 4; f++) d.display();
                final BufferedImage[] shot = new BufferedImage[1];
                d.invoke(true, a -> { shot[0] = CliffShot.grab(a, tw, th); return true; });
                g.drawImage(shot[0], i * tw, row * th, null);
                label(g, t.name + (row == 0 ? String.format(Locale.GERMAN, " · mitten im Brechen (p = %.2f)", t.breakingNow())
                        : String.format(Locale.GERMAN, " · Auflauf und nasser Sand: R_2%% %.2f m (Stockdon %.2f m)", t.swash.r2(), t.stockdon())), i * tw, row * th);
            }
        s.only = -1;
        StringBuilder info = new StringBuilder();
        for (Tank t : s.tanks) info.append(t.train.describe()).append(" · ").append(t.swash.describe(t.stockdon())).append("\n");
        double sw = 0;
        for (Tank t : s.tanks) sw += t.swash.ms;
        label(g, String.format(Locale.GERMAN, "Netz %.1f ms, Schwall %.1f ms (Prozessor), Zeichnen %.2f ms (Grafikkarte)", s.cpuMs, sw, s.gpuMs), 0, th * 2 - 34);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(info);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    static void label(Graphics2D g, String t, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRect(x + 6, y + 6, g.getFontMetrics().stringWidth(t) + 12, 22);
        g.setColor(Color.WHITE);
        g.drawString(t, x + 12, y + 22);
    }

    private static void open() {
        SurfBreakers s = new SurfBreakers();
        // wie die Szene in Sylt: Tiefe 24, doppelt gepuffert; die Fläche liegt in einer eigenen Tafel
        GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
        caps.setDoubleBuffered(true);
        caps.setDepthBits(24);
        caps.setHardwareAccelerated(true);
        GLCanvas canvas = new GLCanvas(caps);
        GLCanvas drawable = canvas;
        drawable.addGLEventListener(s);
        JPanel view = new JPanel(new BorderLayout());
        view.add(canvas, BorderLayout.CENTER);
        FFrame f = new FFrame("Surf · drei Brechertypen");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        Color bg = new Color(14, 20, 22), ink = new Color(233, 236, 234);
        // undurchsichtig, bevor die GLCanvas dazukommt: auf einem durchscheinenden Fenster findet WGL keine Konfiguration
        f.setBackground(bg);
        java.awt.event.ComponentAdapter shape = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { round(f); }
            @Override public void componentShown(java.awt.event.ComponentEvent e) { round(f); }
        };
        f.addComponentListener(shape);
        f.addWindowStateListener(e -> round(f));
        root.setBackground(bg);
        JLabel state = new JLabel(" ");
        state.setForeground(ink);
        FSlider hs = new FSlider(3, 25, 10), sp = new FSlider(0, 100, 100);
        hs.addChangeListener(e -> { for (Tank t : s.tanks) t.train.hs = hs.getValue() / 10.0; });
        sp.addChangeListener(e -> s.speed = sp.getValue() / 100.0);
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(bg);
        JPanel sl = new JPanel(new java.awt.GridLayout(1, 4, 8, 0));
        sl.setBackground(bg);
        JLabel l1 = new JLabel("H_s"), l2 = new JLabel("Zeitlupe");
        l1.setForeground(ink);
        l2.setForeground(ink);
        sl.add(l1); sl.add(hs); sl.add(l2); sl.add(sp);
        south.add(sl, BorderLayout.WEST);
        south.add(state, BorderLayout.CENTER);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1700, 700);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        canvas.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_S)
                    for (Tank t : s.tanks) {
                        t.shore = !t.shore;
                        t.pitch = t.shore ? 24 : 9;
                        t.yaw = t.shore ? -150 : -125;
                    }
            }
        });
        new Animator(drawable).start();
        new Timer(300, e -> {
            StringBuilder b = new StringBuilder("<html>");
            double sw = 0;
            for (Tank t : s.tanks) {
                b.append(t.name).append(": ").append(t.train.describe()).append(" · ").append(t.swash.describe(t.stockdon())).append("<br>");
                sw += t.swash.ms;
            }
            b.append(String.format(Locale.GERMAN, "Netz %.1f ms · Schwall %.1f ms (Prozessor) · Zeichnen %.2f ms (Grafikkarte) · Taste S: Blick Strand/Brandung</html>", s.cpuMs, sw, s.gpuMs));
            state.setText(b.toString());
        }).start();
    }

    /** Runde Ecken als Form statt Durchsichtigkeit (maximiert eckig). */
    private static void round(FFrame f) {
        try {
            boolean max = (f.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0;
            f.setShape(max ? null : new java.awt.geom.RoundRectangle2D.Double(0, 0, f.getWidth(), f.getHeight(), 20, 20));
        } catch (UnsupportedOperationException | java.awt.IllegalComponentStateException e) {
            // Form nicht möglich: eckig lassen
        }
    }

    // ------------------------------------------------------------------ Matrizen (Spalten zuerst)

    static float[] perspective(double fovy, double aspect, double n, double f, boolean reversed) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect);
        m[5] = (float) t;
        m[10] = (float) ((f + n) / (n - f));
        m[11] = -1;
        m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] lookAt(double ex, double ey, double ez, double cx, double cy, double cz) {
        double fx = cx - ex, fy = cy - ey, fz = cz - ez, fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sy = 0, sz = fx, sl = Math.sqrt(sx * sx + sz * sz);
        sx /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        float[] m = new float[16];
        m[0] = (float) sx; m[4] = (float) sy; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * ex + sy * ey + sz * ez);
        m[13] = (float) -(ux * ex + uy * ey + uz * ez);
        m[14] = (float) (fx * ex + fy * ey + fz * ez);
        m[15] = 1;
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        return r;
    }

    /** Bild aus dem Ziel lesen (wie CliffDemo). */
    static final class CliffShot {
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
    }
}
