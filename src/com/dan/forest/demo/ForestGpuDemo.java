package com.dan.forest.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.forest.ForestCulture;
import com.dan.forest.TreeStates;
import com.dan.forest.gl.ForestGl;
import com.dan.forest.gl.ForestGpu;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung von {@link ForestGpu} (seit LituyaBay1958 Phase 6): Millionen Bäume in einer gerechneten Fjordbucht
 * ({@link BayScene}), mit dem Band der Welle am Nordufer. Vier Ansichten: Waldrand an der Spurlinie, über der Bucht,
 * Weitblick, im Wald.
 * <ul>
 *   <li>{@code ForestGpuDemo} – Fenster mit Reglern (Qualität, Ansicht, Wind, Zustände an/aus);</li>
 *   <li>{@code --bild datei.png [qualität]} – die vier Ansichten als Bogen mit Zeiten;</li>
 *   <li>{@code --zeiten} – die fps-Tabelle: je Qualität und Ansicht Bäume, Dreiecke, Zeiten der Grafikkarte (Mittel
 *   über 30 Bilder nach 10 zum Einschwingen) und Speicher.</li>
 * </ul>
 */
public final class ForestGpuDemo implements GLEventListener {
    static final String[] VIEWS = {"Waldrand an der Spurlinie", "Über der Bucht", "Weitblick", "Im Wald"};
    static final Color BG = new Color(14, 20, 24), INK = new Color(220, 228, 232), MUTED = new Color(150, 165, 172), ACCENT = new Color(64, 200, 190);

    /** Auge und Ziel je Ansicht (Höhe des Auges über dem Boden, wenn Wert &lt; 0 … sonst über NN). */
    static double[][] view(int i) {
        return switch (i) {
            case 0 -> new double[][]{{2400, 6, -700}, {3000, 160, -1550}};
            case 1 -> new double[][]{{-1500, 320, 200}, {2600, 120, -1500}};
            case 2 -> new double[][]{{-7500, 900, 5500}, {1500, 100, -500}};
            default -> {
                double x = 900, z = 1900;
                double gy = BayScene.TERRAIN.height(x, z);
                yield new double[][]{{x, gy + 1.8, z}, {x + 300, gy + 30, z - 120}};
            }
        };
    }

    static ForestGl.View camera(int i, int w, int h) {
        double[][] e = view(i);
        ForestGl.View v = new ForestGl.View();
        v.eye = e[0];
        v.viewProj = BayScene.mul(BayScene.perspective(i == 3 ? 70 : 55, w / (double) h, 0.5, 40000), BayScene.lookAt(e[0], e[1]));
        v.tonemap = true;
        v.exposure = 1.0;
        return v;
    }

    final BayScene scene;
    volatile int viewIdx = 1;
    volatile ForestGpu.Quality quality;
    volatile double wind = 5;
    volatile boolean statesOn = true;
    double t0 = System.nanoTime() / 1e9;

    ForestGpuDemo(ForestGpu.Quality q) {
        quality = q;
        scene = new BayScene(q);
    }

    @Override public void init(GLAutoDrawable d) { scene.init(d.getGL().getGL4()); }
    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { scene.dispose(d.getGL().getGL4()); }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        scene.forest.apply(quality);
        scene.forest.setStates(statesOn ? scene.states : null);
        BayScene.trimOn = statesOn;
        scene.forest.setWind(0.6, 0.8, wind, 0.5);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        scene.render(gl, w, h, camera(viewIdx, w, h), BayScene.evening(), System.nanoTime() / 1e9 - t0);
    }

    // ------------------------------------------------------------------ ohne Fenster

    public static void main(String[] a) throws Exception {
        List<String> l = List.of(a);
        int bi = l.indexOf("--bild");
        if (bi >= 0) {
            ForestGpu.Quality q = a.length > bi + 2 ? ForestGpu.Quality.valueOf(a[bi + 2].toUpperCase(Locale.ROOT)) : ForestGpu.Quality.HIGH;
            offscreen(1920, 1080, q, d -> bild(d, new File(a[bi + 1])));
            return;
        }
        if (l.contains("--zeiten")) {
            offscreen(1920, 1080, ForestGpu.Quality.LOW, ForestGpuDemo::zeiten);
            return;
        }
        SwingUtilities.invokeLater(() -> new ForestGpuDemo(ForestGpu.Quality.MEDIUM).open());
    }

    interface Job { void run(Off d) throws Exception; }

    /** Ein Bildspeicher außerhalb des Fensters mit der Szene. */
    static final class Off {
        final GLOffscreenAutoDrawable dr;
        final ForestGpuDemo demo;
        final int w, h;
        Off(GLOffscreenAutoDrawable dr, ForestGpuDemo demo, int w, int h) { this.dr = dr; this.demo = demo; this.w = w; this.h = h; }

        /** Zeichnet n Bilder der Ansicht und gibt das letzte zurück (oder null). */
        BufferedImage frames(int view, int n, boolean grab) {
            demo.viewIdx = view;
            for (int i = 0; i < n; i++) dr.display();
            // Zahlen dieser Ansicht (sonst gilt die letzte Lesung alle 30 Bilder, oft noch von der vorigen Ansicht)
            dr.invoke(true, x -> { demo.scene.forest.readStats(x.getGL().getGL4()); return true; });
            if (!grab) return null;
            BufferedImage[] img = new BufferedImage[1];
            dr.invoke(true, x -> { img[0] = grab(x.getGL().getGL4(), x.getContext().getDefaultReadFramebuffer(), w, h); return true; });
            return img[0];
        }
    }

    static void offscreen(int w, int h, ForestGpu.Quality q, Job job) throws Exception {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        ForestGpuDemo demo = new ForestGpuDemo(q);
        dr.addGLEventListener(demo);
        dr.display();
        demo.scene.forest.awaitBake();
        dr.display();
        job.run(new Off(dr, demo, w, h));
        dr.destroy();
        System.exit(0);
    }

    static void bild(Off d, File out) throws Exception {
        int W = d.w / 2, H = d.h / 2;
        BufferedImage sheet = new BufferedImage(d.w, d.h + 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        ForestGpu f = d.demo.scene.forest;
        for (int i = 0; i < 4; i++) {
            BufferedImage im = d.frames(i, 12, true);
            g.drawImage(im, (i % 2) * W, (i / 2) * H, W, H, null);
            String stat = String.format(Locale.GERMAN, "%,d Bäume · %,d Impostors · %,d Dreiecke · Kultur %.2f ms · Wald %.2f ms · Rest %.2f ms",
                    f.visible(), f.impostors(), f.triangles(), d.demo.scene.cullMs, d.demo.scene.forestMs, d.demo.scene.restMs);
            label(g, (i % 2) * W + 8, (i / 2) * H + 8, VIEWS[i], stat);
            System.out.println(VIEWS[i] + ": " + f.info());
        }
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        long[] n = d.demo.scene.forest.culture().count(BayScene.X0, BayScene.Z0, BayScene.X1, BayScene.Z1);
        long total = 0, standing = 0;
        for (int t = 0; t < n.length; t++) { total += n[t]; if (t < f.species() * f.variants()) standing += n[t]; }
        int[] sc = d.demo.scene.states.counts();
        g.drawString(String.format(Locale.GERMAN, "com.dan.forest · ForestGpu · Qualität %s · Bucht 16 × 12 km: %,d Bäume (%,d stehend) aus Dichte, Startwert und Zustand, auf der Grafikkarte je Bild ausgewürfelt",
                d.demo.quality, total, standing), 14, d.h + 30);
        g.drawString(String.format(Locale.GERMAN, "Band der Welle: %,d Plätze fort, %,d umgelegt, %,d gebrochen, %,d entrindet · Sitka-Fichte, Westliche Hemlocktanne, Roterle, je 3 Varianten · %s",
                sc[4], sc[3], sc[1], sc[2], gpuName(d)), 14, d.h + 56);
        g.drawString(String.format(Locale.GERMAN, "Impostors: Halb-Oktaeder %d × %d Ansichten zu %d Bildpunkten · Grafikspeicher des Walds %d MB · Zeiten: Mittel nach Einschwingen, Grafikkarte (Abfragen)",
                ForestGpu.FRAMES, ForestGpu.FRAMES, f.framePx(), f.vramBytes() >> 20), 14, d.h + 82);
        g.dispose();
        ImageIO.write(sheet, "png", out);
        System.out.println("Bild: " + out.getPath());
    }

    static String gpuName(Off d) {
        String[] s = new String[1];
        d.dr.invoke(true, x -> { s[0] = x.getGL().glGetString(GL.GL_RENDERER); return true; });
        return s[0];
    }

    /** Die fps-Tabelle: je Qualität und Ansicht. */
    static void zeiten(Off d) {
        System.out.println("Wald auf der Grafikkarte (com.dan.forest.gl.ForestGpu), Bucht 16 × 12 km, 1920 × 1080, " + gpuName(d));
        System.out.println();
        System.out.printf(Locale.GERMAN, "%-8s %-28s %11s %11s %13s %9s %9s %9s %9s %8s%n", "Qualität", "Ansicht", "Bäume", "Impostors", "Dreiecke", "Kultur", "Wald", "Rest", "Bild", "fps");
        List<String> rows = new ArrayList<>();
        for (ForestGpu.Quality q : ForestGpu.Quality.values()) {
            d.demo.quality = q;
            for (int i = 0; i < 4; i++) {
                d.frames(i, 10, false);
                double c = 0, f = 0, r = 0;
                for (int k = 0; k < 30; k++) {
                    d.frames(i, 1, false);
                    c += d.demo.scene.cullMs; f += d.demo.scene.forestMs; r += d.demo.scene.restMs;
                }
                c /= 30; f /= 30; r /= 30;
                ForestGpu fo = d.demo.scene.forest;
                double frame = c + f + r;
                System.out.printf(Locale.GERMAN, "%-8s %-28s %,11d %,11d %,13d %7.2f ms %7.2f ms %7.2f ms %7.2f ms %8.0f%n",
                        q, VIEWS[i], fo.visible(), fo.impostors(), fo.triangles(), c, f, r, frame, 1000 / frame);
            }
            System.out.printf(Locale.GERMAN, "%-8s Grafikspeicher des Walds %d MB (Impostors %d px je Ansicht)%n", "", d.demo.scene.forest.vramBytes() >> 20, d.demo.scene.forest.framePx());
        }
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
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        int w = Math.max(g.getFontMetrics().stringWidth(a), g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12)).stringWidth(b)) + 14;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(x, y, w, 40);
        g.setColor(Color.WHITE);
        g.drawString(a, x + 7, y + 17);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(new Color(220, 220, 220));
        g.drawString(b, x + 7, y + 33);
    }

    // ------------------------------------------------------------------ Fenster

    private JPanel panel;

    private void open() {
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(this);
        FFrame f = new FFrame("Wald auf der Grafikkarte · com.dan.forest.gl.ForestGpu");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(canvas, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        f.setPreferredFrameSize(new java.awt.Dimension(1600, 950));
        f.setSize(1600, 950);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
    }

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel head = new JLabel("WALD (GPU)");
        head.setForeground(ACCENT);
        head.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(head);
        FComboBox v = new FComboBox(VIEWS);
        v.setSelectedIndex(viewIdx);
        v.addActionListener(e -> viewIdx = v.getSelectedIndex());
        add(v);
        String[] qs = new String[ForestGpu.Quality.values().length];
        for (int i = 0; i < qs.length; i++) qs[i] = ForestGpu.Quality.values()[i].name();
        FComboBox qb = new FComboBox(qs);
        qb.setSelectedIndex(quality.ordinal());
        qb.addActionListener(e -> quality = ForestGpu.Quality.values()[qb.getSelectedIndex()]);
        add(qb);
        JLabel wl = new JLabel("Wind  5 m/s");
        wl.setForeground(INK);
        add(wl);
        FSlider ws = new FSlider(0, 25, 5);
        ws.addChangeListener(e -> { wind = ws.getValue(); wl.setText("Wind  " + ws.getValue() + " m/s"); });
        add(ws);
        com.dan.fcheckbox.FCheckBox sb = new com.dan.fcheckbox.FCheckBox("Band der Welle (Zustände)");
        sb.setSelected(true);
        sb.addActionListener(e -> statesOn = sb.isSelected());
        add(sb);
        JLabel info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(info);
        new javax.swing.Timer(500, e -> info.setText(String.format(Locale.GERMAN,
                "<html><body style='width:260px'>Kultur %.2f ms · Wald %.2f ms · Rest %.2f ms<br><br>%s</body></html>",
                scene.cullMs, scene.forestMs, scene.restMs, scene.forest.info()))).start();
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void add(Component c) {
        if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
        panel.add(Box.createVerticalStrut(8));
    }
}
