package com.dan.tree.demo;

import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.tree.TreeAsset;
import com.dan.tree.TreeBaker;
import com.dan.tree.TreeLod;
import com.dan.tree.TreeMesh;
import com.dan.tree.TreeSeason;
import com.dan.tree.TreeSpecies;
import com.dan.tree.TreeWind;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Vorschau einer {@link TreeSpecies} aus {@code com.dan.tree} mit dem {@link SoftRenderer}: Einzelbaum,
 * Hain (alle Varianten, Stufe nach Entfernung) oder alle Stufen nebeneinander. Die Pakete
 * {@code com.dan.tree.<art>.demo} rufen nur {@link #run(TreeSpecies, String[])} auf.
 *
 * <pre>
 * Fenster:  …Demo [ansicht variante stufe tag]
 * Bild:     …Demo --bild datei.png [einzel|hain|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]
 * </pre>
 */
public final class TreeDemo {

    static final Color BG = new Color(18, 22, 26);
    static final Color INK = new Color(225, 232, 236);
    static final Color MUTED = new Color(140, 152, 160);
    static final String[] VIEWS = {"Einzelbaum", "Hain", "Stufen nebeneinander"};
    static final String[] GRASS_VIEWS = {"Büschel", "Wiese", "Stufen nebeneinander"};
    static final String[] GROUND_VIEWS = {"Gelände", "Gelände von oben", "Stufen nebeneinander"};
    static final String[] MONTHS = {"Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};
    static final int[] MONTH_START = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};

    final TreeSpecies sp;
    final SoftRenderer renderer = new SoftRenderer();
    final TreeWind wind = new TreeWind();
    final Map<Integer, TreeMesh> meshes = new HashMap<>();

    volatile int view, variant, lodChoice = -1, day = 180;
    volatile float snow;
    volatile boolean running = true;
    volatile double yaw = 0.6, pitch = 0.12, dist = -1;
    volatile String status = "";
    float time;

    /** Ein Baum (Büschel, Geländestück) der Szene; fixedLod −1 = nach Entfernung. */
    public record Placed(int variant, float x, float y, float z, float yaw, float scale, int fixedLod) {
        public Placed(int variant, float x, float z, float yaw, float scale, int fixedLod) {
            this(variant, x, 0, z, yaw, scale, fixedLod);
        }
    }

    /** Anordnung für die Ansicht „viele“ (Hain, Wiese), etwa aus {@code com.dan.tree.grass.GrassField}. */
    @FunctionalInterface
    public interface Layout {
        /** Alle Stücke in einem Kreis vom Radius r (m) um den Ursprung. */
        List<Placed> place(int variants, float radius);
    }

    /** Optional: Anordnung für Ansicht 1, Bodentextur und ihre Kachelgröße (m). */
    public Layout layout;
    public BufferedImage groundTexture;
    public float groundTileX = 2, groundTileZ = 2;

    public TreeDemo(TreeSpecies sp) {
        this.sp = sp;
        wind.speed = 6;
        wind.gustiness = 0.5f;
    }

    /** Vorschau auf dem Prozessor: {@code TreeDemo [art] [ansicht] [variante] [stufe|auto] [tag]} oder {@code TreeDemo [art] --bild …}. */
    public static void main(String[] args) throws Exception {
        com.dan.tree.Tree.Art a = art(args, "schwarzerle");
        if (a.grass()) GrassDemo.run(a.create(), a.field().get(), rest(args));
        else run(a.create(), rest(args));
    }

    /** Erstes Argument eine Art aus {@link com.dan.tree.Tree} (etwa „schwarzerle“, „kikuyu“, „fussweg“), der Rest wie {@link #run}. */
    static com.dan.tree.Tree.Art art(String[] args, String vorgabe) {
        com.dan.tree.Tree.Art a = args.length > 0 ? com.dan.tree.Tree.art(args[0]) : null;
        if (a == null && args.length > 0 && !args[0].startsWith("-") && !args[0].matches("[0-9.]+"))
            System.err.println("Art " + args[0] + " gibt es nicht: " + com.dan.tree.Tree.schluessel());
        return a != null ? a : com.dan.tree.Tree.art(vorgabe);
    }

    static String[] rest(String[] args) {
        return args.length > 0 && com.dan.tree.Tree.art(args[0]) != null ? java.util.Arrays.copyOfRange(args, 1, args.length) : args;
    }

    public static void run(TreeSpecies sp, String[] args) throws Exception {
        run(new TreeDemo(sp), args);
    }

    /** Wie {@link #run(TreeSpecies, String[])}, mit einer vorbereiteten Demo (Anordnung, Boden). */
    public static void run(TreeDemo d, String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--bild")) {
            image(d, args);
            return;
        }
        if (args.length > 0) d.view = Integer.parseInt(args[0]);
        d.yaw = defaultYaw(d.view);
        if (args.length > 1) d.variant = Integer.parseInt(args[1]);
        if (args.length > 2) d.lodChoice = args[2].equals("auto") ? -1 : Integer.parseInt(args[2]);
        if (args.length > 3) d.day = Integer.parseInt(args[3]);
        SwingUtilities.invokeLater(d::open);
    }

    // ------------------------------------------------------------------ Bild ohne Fenster

    static void image(TreeDemo d, String[] a) throws Exception {
        String file = a.length > 1 ? a[1] : "baum.png";
        String v = a.length > 2 ? a[2] : "einzel";
        d.view = v.startsWith("hain") || v.startsWith("wiese") || v.startsWith("oben") ? 1 : v.startsWith("stuf") ? 2 : 0;
        d.yaw = defaultYaw(d.view);
        d.variant = a.length > 3 ? Integer.parseInt(a[3]) : 0;
        d.lodChoice = a.length > 4 && !a[4].equals("auto") ? Integer.parseInt(a[4]) : -1;
        d.day = a.length > 5 ? Integer.parseInt(a[5]) : 180;
        d.wind.speed = a.length > 6 ? Float.parseFloat(a[6]) : 6;
        int w = a.length > 7 ? Integer.parseInt(a[7]) : 1280, h = a.length > 8 ? Integer.parseInt(a[8]) : 800;
        d.time = a.length > 9 ? Float.parseFloat(a[9]) : 3.5f;
        long t0 = System.nanoTime();
        BufferedImage img = d.frame(w, h);
        ImageIO.write(img, "png", new File(file));
        System.out.printf("%s geschrieben (%d × %d, %.0f ms): %s%n", file, w, h, (System.nanoTime() - t0) / 1e6, d.status);
    }

    // ------------------------------------------------------------------ Szene

    List<Placed> scene() {
        List<Placed> out = new ArrayList<>();
        switch (view) {
            case 1 -> {
                if (layout != null) return layout.place(sp.variantCount(), radiusFor(view));
                if (sp.form == TreeSpecies.Form.GROUND) {
                    out.add(new Placed(variant, 0, 0, 0, 1, lodChoice));
                    break;
                }
                Random r = new Random(42);
                List<float[]> pts = new ArrayList<>();
                pts.add(new float[]{0, 0});
                while (pts.size() < 14) {
                    float x = (r.nextFloat() - 0.5f) * 70, z = (r.nextFloat() - 0.5f) * 70;
                    boolean ok = true;
                    for (float[] p : pts) ok &= Math.hypot(p[0] - x, p[1] - z) > 7.5;
                    if (ok) pts.add(new float[]{x, z});
                }
                for (int i = 0; i < pts.size(); i++)
                    out.add(new Placed(i == 0 ? variant : i % sp.variantCount(), pts.get(i)[0], pts.get(i)[1],
                            r.nextFloat() * 6.283f, 0.85f + r.nextFloat() * 0.25f, -1));
            }
            case 2 -> {
                float gap = sp.form == TreeSpecies.Form.TREE ? Math.max(9, height(variant) * 0.75f) : size(variant) * 1.3f;
                for (int l = 0; l < TreeLod.LEVELS; l++)
                    out.add(new Placed(variant, (l - 1.5f) * gap, 0, 0, 1, l));
            }
            default -> out.add(new Placed(variant, 0, 0, 0, 1, lodChoice));
        }
        return out;
    }

    /** Blickrichtung je Ansicht: die Stufen stehen entlang x, also von +z aus ansehen. */
    static double defaultYaw(int view) {
        return view == 2 ? Math.PI / 2 : 0.6;
    }

    float height(int v) {
        return mesh(v, 3).height;
    }

    /** Maß für Kamera und Abstände: Höhe, bei Gelände der halbe Durchmesser. */
    float size(int v) {
        TreeMesh m = mesh(v, 3);
        return sp.form == TreeSpecies.Form.GROUND ? m.radius : Math.max(m.height, sp.form == TreeSpecies.Form.GRASS ? 0.05f : 1);
    }

    /** Radius der Ansicht „viele“: Wiese 30 Grashöhen (mindestens 4 m). */
    float radiusFor(int view) {
        return Math.max(4, size(variant) * 30);
    }

    String[] views() {
        return sp.form == TreeSpecies.Form.GRASS ? GRASS_VIEWS : sp.form == TreeSpecies.Form.GROUND ? GROUND_VIEWS : VIEWS;
    }

    String noun() {
        return sp.form == TreeSpecies.Form.GRASS ? "Büschel" : sp.form == TreeSpecies.Form.GROUND ? "Stücke" : "Bäume";
    }

    synchronized TreeMesh mesh(int v, int lod) {
        return meshes.computeIfAbsent(v * 10 + lod, k -> sp.bake(v, lod));
    }

    /** Stufe nach Entfernung in Höhen der Art ({@link TreeSpecies#lodDistance}). */
    int autoLod(double d, float h) {
        double r = d / Math.max(h, sp.form == TreeSpecies.Form.TREE ? 1 : 0.05f);
        float[] g = sp.lodDistance;
        return r < g[0] ? 0 : r < g[1] ? 1 : r < g[2] ? 2 : 3;
    }

    float[] eye(float h) {
        double r;
        float ty;
        switch (sp.form) {
            case GRASS -> {
                r = dist > 0 ? dist : view == 1 ? 16 * h : view == 2 ? 3.2 * h : 2.6 * h;
                ty = view == 1 ? h * 2 : h * 0.45f;
            }
            case GROUND -> {
                r = dist > 0 ? dist : view == 1 ? 2.0 * h : 1.6 * h;
                ty = 0;
            }
            default -> {
                r = dist > 0 ? dist : (view == 1 ? 3.2 * h : view == 2 ? 2.4 * h : 1.55 * h);
                ty = view == 1 ? h * 0.3f : h * 0.48f;
            }
        }
        return new float[]{(float) (Math.cos(yaw) * Math.cos(pitch) * r), (float) (ty + Math.sin(pitch) * r),
                (float) (Math.sin(yaw) * Math.cos(pitch) * r), ty};
    }

    BufferedImage frame(int w, int h) {
        renderer.resize(w, h);
        TreeSeason.Look look = sp.season.look(day, snow);
        float th = size(variant);
        if (sp.form == TreeSpecies.Form.GROUND && view == 1 && dist <= 0) pitch = Math.max(pitch, 0.9);
        float[] e = eye(th);
        renderer.camera(new float[]{e[0], e[1], e[2]}, new float[]{0, e[3], 0}, 45);
        List<Placed> trees = scene();
        boolean many = trees.size() > 60;              // Wiese: keine Schattenflecken, Ausdünnen in der Ferne
        float[] blobs = new float[many || sp.form == TreeSpecies.Form.GROUND ? 0 : trees.size() * 4];
        int tris = 0;
        int[] perLod = new int[TreeLod.LEVELS];
        List<Runnable> draws = new ArrayList<>();
        for (int i = 0; i < trees.size(); i++) {
            Placed p = trees.get(i);
            TreeAsset a = sp.load(p.variant);
            float hh = (sp.form == TreeSpecies.Form.GROUND ? size(p.variant) : height(p.variant)) * p.scale;
            double d = Math.hypot(Math.hypot(e[0] - p.x, e[2] - p.z), e[1] - p.y - hh * 0.5);
            double rel = d / Math.max(hh, 0.05f);
            if (many && rel > sp.maxDistance) continue;
            if (many && rel > sp.fadeDistance && TreeBaker.hash(i, 97) < (rel - sp.fadeDistance) / (sp.maxDistance - sp.fadeDistance)) continue;
            int lod = p.fixedLod >= 0 ? p.fixedLod : autoLod(d, hh);
            TreeMesh m = mesh(p.variant, lod);
            perLod[lod]++;
            tris += m.triangles;
            if (blobs.length > 0) {
                blobs[i * 4] = p.x;
                blobs[i * 4 + 1] = p.z;
                blobs[i * 4 + 2] = look.bare() ? m.radius * 0.35f * p.scale : m.radius * 0.75f * p.scale;
                blobs[i * 4 + 3] = m.height * 0.6f * p.scale;
            }
            float[] pos = new float[m.vertices * 3];
            wind.displace(m, sp, a.gustAttenuation, time, p.x, p.y, p.z, p.yaw, pos);
            draws.add(() -> renderer.tree(m, pos, sp, a, look, p.x, p.y, p.z, p.yaw, p.scale));
        }
        renderer.shadows(blobs);
        renderer.clear();
        renderer.groundTexture(groundTexture, groundTileX, groundTileZ);
        if (sp.form != TreeSpecies.Form.GROUND)
            renderer.ground(0, 0, sp.form == TreeSpecies.Form.GRASS ? Math.max(60, th * 400) : view == 1 ? 260 : 120,
                    new float[]{0.045f, 0.07f, 0.025f}, snow);
        for (Runnable r : draws) r.run();
        int count = perLod[0] + perLod[1] + perLod[2] + perLod[3];
        status = String.format("%s · %s · %d %s · %,d Dreiecke · Stufen %d/%d/%d/%d · Wind %.0f m/s",
                sp.name, sp.variantNames[variant], count, noun(), tris, perLod[0], perLod[1], perLod[2], perLod[3], wind.speed);
        return renderer.finish();
    }

    // ------------------------------------------------------------------ Fenster

    BufferedImage shown;
    JPanel panel;
    JLabel statusLbl;
    JPanel side;

    void open() {
        FFrame f = new FFrame(sp.name + " – Vorschau des Pakets " + sp.owner.getPackageName());
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (shown != null) g.drawImage(shown, 0, 0, null);
            }
        };
        panel.setBackground(BG);
        MouseAdapter mouse = new MouseAdapter() {
            Point last;

            @Override public void mousePressed(MouseEvent e) { last = e.getPoint(); }

            @Override public void mouseDragged(MouseEvent e) {
                yaw += (e.getX() - last.x) * 0.01;
                pitch = Math.max(-0.1, Math.min(1.3, pitch + (e.getY() - last.y) * 0.01));
                last = e.getPoint();
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                float h = height(variant);
                double d = dist > 0 ? dist : eyeDist(h);
                dist = Math.max(h * 0.3, Math.min(h * 30, d * Math.pow(1.1, e.getWheelRotation())));
            }
        };
        panel.addMouseListener(mouse);
        panel.addMouseMotionListener(mouse);
        panel.addMouseWheelListener(mouse);
        root.add(panel, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        statusLbl = new JLabel(" ");
        statusLbl.setForeground(MUTED);
        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLbl.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);
        south.add(statusLbl);
        root.add(south, BorderLayout.SOUTH);
        f.setPreferredFrameSize(new Dimension(1400, 860));
        f.setSize(1400, 860);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Thread t = new Thread(this::loop, "Baum-Vorschau");
        t.setDaemon(true);
        t.start();
    }

    double eyeDist(float h) {
        float[] e = eye(h);
        return Math.sqrt(e[0] * e[0] + (e[1] - e[3]) * (e[1] - e[3]) + e[2] * e[2]);
    }

    void loop() {
        long last = System.nanoTime();
        while (true) {
            long now = System.nanoTime();
            if (running) time += (now - last) / 1e9f;
            last = now;
            int w = Math.max(64, panel.getWidth()), h = Math.max(64, panel.getHeight());
            BufferedImage img = frame(w, h);
            BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            copy.getGraphics().drawImage(img, 0, 0, null);
            long ms = (System.nanoTime() - now) / 1_000_000;
            String s = status + " · " + ms + " ms";
            SwingUtilities.invokeLater(() -> {
                shown = copy;
                statusLbl.setText(s);
                panel.repaint();
            });
        }
    }

    JComponent controls() {
        side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(BG);
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        side.setPreferredSize(new Dimension(290, 10));

        head(sp.name + " (" + sp.latin + ")");
        FComboBox views = new FComboBox(views());
        views.setSelectedIndex(view);
        views.addActionListener(e -> { view = views.getSelectedIndex(); dist = -1; yaw = defaultYaw(view); });
        add(label("Ansicht"));
        add(views);

        FComboBox vars = new FComboBox(sp.variantNames);
        vars.setSelectedIndex(variant);
        vars.addActionListener(e -> { variant = vars.getSelectedIndex(); dist = -1; });
        add(label("Variante"));
        add(vars);

        String[] lods = {"Auto (Entfernung)", "Stufe 0 – Original", "Stufe 1 – mittel", "Stufe 2 – fern", "Stufe 3 – Hülle"};
        FComboBox lodBox = new FComboBox(lods);
        lodBox.setSelectedIndex(lodChoice + 1);
        lodBox.addActionListener(e -> lodChoice = lodBox.getSelectedIndex() - 1);
        add(label("Stufe (Einzelbaum)"));
        add(lodBox);

        gap();
        head("Wind");
        slider("Geschwindigkeit", 0, 30, (int) wind.speed, v -> { wind.speed = v; return v + " m/s"; });
        slider("Böigkeit", 0, 100, (int) (wind.gustiness * 100), v -> { wind.gustiness = v / 100f; return v + " %"; });
        slider("aus Richtung", 0, 359, (int) wind.fromDeg, v -> { wind.fromDeg = v; return v + "°"; });
        FCheckBox run = new FCheckBox("Zeit läuft");
        run.setSelected(true);
        run.addActionListener(e -> running = run.isSelected());
        add(run);

        gap();
        head("Jahreszeit");
        slider("Tag im Jahr", 0, 364, day, v -> { day = v; return dateLabel(v); });
        slider("Schnee", 0, 100, 0, v -> { snow = v / 100f; return v + " %"; });
        note("Maus ziehen: drehen · Rad: Abstand");
        side.add(Box.createVerticalGlue());
        return side;
    }

    interface Change { String set(int v); }

    void slider(String name, int min, int max, int value, Change c) {
        JLabel l = label(name + ": " + c.set(value));
        FSlider s = new FSlider(min, max, value);
        s.addChangeListener(e -> l.setText(name + ": " + c.set(s.getValue())));
        add(l);
        add(s);
    }

    static String dateLabel(int day) {
        int m = 11;
        while (m > 0 && MONTH_START[m] > day) m--;
        return (day - MONTH_START[m] + 1) + ". " + MONTHS[m] + " (Tag " + day + ")";
    }

    void head(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.BOLD, 14));
        add(l);
    }

    JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setBorder(BorderFactory.createEmptyBorder(8, 0, 3, 0));
        return l;
    }

    void note(String s) {
        gap();
        add(label(s));
    }

    void gap() {
        side.add(Box.createVerticalStrut(12));
    }

    void add(Component c) {
        if (c instanceof JComponent j) j.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(c);
    }
}
