package com.dan.bear.demo;

import com.dan.bear.Bear;
import com.dan.bear.BearBody;
import com.dan.bear.Sleuth;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Demo des Packages (Phase 18a): links der Bär von nah (Gangarten und Haltungen, im Software-Rasterer {@link Soft},
 * ohne Grafikkarte), rechts die {@link DemoBay} von oben mit den Bären, dem Bach und dem Strand; ein Klick in die Karte
 * stellt einen Wanderer hin (Rechtsklick nimmt ihn weg), „Beben“ schickt alle bergauf.
 * <p>
 * Ohne Fenster: {@code --bild datei.png} schreibt den Bogen mit Gangarten und Haltungen und die Karte nach zwei
 * Stunden; {@code --glbild datei.png} zeichnet die Haltungen mit {@code gl/BearGl} auf der Grafikkarte
 * ({@link BearGlBild}).
 */
public final class BearDemo {
    private BearDemo() { }

    /** Die Haltungen für Bogen und Prüfung: Name, Haltung (stehend, Beine aus dem Gang gelöst). */
    public static final String[] POSE_NAMES = {"Stehen", "Gehen", "Traben", "Galopp", "Schnüffeln", "Aufrichten", "Fischen", "Fressen", "Graben", "Liegen"};

    public static float[][] poses() {
        float[][] r = new float[POSE_NAMES.length][];
        for (int i = 0; i < r.length; i++) r[i] = pose(i, 0.3)[0];
        return r;
    }

    /** Haltung i im Takt t: {Haltung, {Höhe des Grunds unter der Hüfte}}. */
    public static float[][] pose(int i, double t) {
        float[] p = new float[BearBody.N];
        double L = 2.3, lvl;
        switch (i) {
            case 1 -> lvl = Bear.legs(Bear.Gait.WALK, t, 1.25, L, p);
            case 2 -> lvl = Bear.legs(Bear.Gait.TROT, t, 3.2, L, p);
            case 3 -> lvl = Bear.legs(Bear.Gait.GALLOP, t, 11, L, p);
            case 4 -> { p[BearBody.HEAD_LIFT] = -0.45f; p[BearBody.HEAD_YAW] = 0.25f; lvl = Bear.legs(Bear.Gait.WALK, t, 0.45, L, p); }
            case 5 -> {
                p[BearBody.PITCH] = 1.12f; p[BearBody.HEAD_LIFT] = -0.75f;
                for (int l = 2; l < 4; l++) BearBody.solveLeg(l, -0.42, 0.06, p);
                lvl = Math.min(BearBody.sole(2, p)[1], BearBody.sole(3, p)[1]);
                for (int l = 2; l < 4; l++) BearBody.solveLeg(l, lvl, 0.06, p);
                p[BearBody.SWING] = p[BearBody.SWING + 1] = 0.55f; p[BearBody.BEND] = p[BearBody.BEND + 1] = 0.9f;
            }
            case 6 -> { p[BearBody.HEAD_LIFT] = -0.32f; lvl = Bear.legs(Bear.Gait.STAND, 0, 0, L, p); }
            case 7 -> { p[BearBody.HEAD_LIFT] = -0.55f; p[BearBody.JAW] = 0.4f; lvl = Bear.legs(Bear.Gait.STAND, 0, 0, L, p); }
            case 8 -> {
                p[BearBody.HEAD_LIFT] = -0.42f; p[BearBody.ARCH] = 0.06f;
                double ph = 2 * Math.PI * t;
                lvl = Bear.stand(new double[]{0.10 + 0.10 * Math.sin(ph), 0.10 - 0.10 * Math.sin(ph), 0, 0}, new double[]{0.03 * Math.max(0, Math.cos(ph)), 0.03 * Math.max(0, -Math.cos(ph)), 0, 0},
                        new boolean[]{false, false, true, true}, p);
            }
            case 9 -> {
                p[BearBody.HEAD_LIFT] = -0.18f; p[BearBody.ROLL] = 0.12f;
                float[] s = {1.25f, 1.25f, 1.05f, 1.05f}, b = {0.25f, 0.25f, 2.0f, 2.0f};
                for (int l = 0; l < 4; l++) { p[BearBody.SWING + l] = s[l]; p[BearBody.BEND + l] = b[l]; }
                lvl = Math.min(BearBody.lowest(p), BearBody.lowestFoot(p));
            }
            default -> { p[BearBody.HEAD_YAW] = 0.2f; lvl = Bear.legs(Bear.Gait.STAND, 0, 0, L, p); }
        }
        return new float[][]{p, {(float) lvl}};
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        if (a.length >= 2 && a[0].equals("--bild")) { bild(new File(a[1])); System.exit(0); }
        if (a.length >= 2 && a[0].equals("--glbild")) { BearGlBild.main(new String[]{a[1]}); return; }
        SwingUtilities.invokeLater(BearDemo::fenster);
    }

    // ------------------------------------------------------------------ Bogen ohne Fenster

    static void bild(File f) throws Exception {
        int cw = 480, ch = 320, cols = 5;
        BufferedImage im = new BufferedImage(cols * cw, 2 * ch + 40 + 760, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(12, 18, 24));
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString("com.dan.bear · Küstenbraunbär · Gangarten und Haltungen (Software-Rasterer)", 12, 27);
        for (int i = 0; i < POSE_NAMES.length; i++) {
            float[][] q = pose(i, 0.3);
            Soft s = new Soft(cw, ch);
            boolean side = i == 1 || i == 2 || i == 3 || i == 5 || i == 8;
            if (side) s.look(6.2, 1.0, -0.7, 0, 0.75, -0.7); else s.look(4.6, 2.0, -4.6, 0, 0.55, -0.6);
            s.ground(0, 4, 0xFF6A7A5E);
            s.bear(0, -q[1][0] * 2.3, 0, 2.3, 0, q[0], 0.35 + 0.08 * (i % 3), i == 6 ? 0.8 : 0);
            int x = (i % cols) * cw, y = 40 + (i / cols) * ch;
            g.drawImage(s.image(), x, y, null);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(x, y + ch - 26, cw, 26);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.drawString(POSE_NAMES[i], x + 10, y + ch - 8);
        }
        // Karte nach zwei Stunden
        DemoBay w = new DemoBay();
        w.tide = -0.6;
        Sleuth sl = BearCheck.sleuth(w, 3, 2, 8, 7);
        List<double[]> trail = new ArrayList<>();
        for (int i = 0; i < 7200; i++) { sl.step(1); if (i % 5 == 0) for (Sleuth.Animal an : sl.animals()) trail.add(new double[]{an.x, an.z, an.id}); }
        drawMap(g, 0, 2 * ch + 40, cols * cw, 760, w, sl, trail, Double.NaN, 0);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("DemoBay nach 2 h im August: " + sl.describe() + " · " + sl.pounces + " Sprünge, " + sl.digs + "-mal gegraben, " + sl.tracks + " Trittsiegel", 12, 2 * ch + 40 + 750);
        g.dispose();
        ImageIO.write(im, "png", f);
        System.out.println("geschrieben: " + f);
    }

    static final Color[] COL = {new Color(230, 160, 70), new Color(120, 190, 240), new Color(200, 120, 200), new Color(250, 230, 140), new Color(160, 230, 160), new Color(240, 120, 120), new Color(150, 150, 250), new Color(240, 200, 200)};

    static void drawMap(Graphics2D g, int ox, int oy, int w, int h, DemoBay bay, Sleuth s, List<double[]> trail, double hx, double hz) {
        double x0 = -300, x1 = 300, z0 = -320, z1 = 120, sc = Math.min(w / (x1 - x0), h / (z1 - z0));
        int cell = 4;
        for (int py = 0; py < h; py += cell)
            for (int px = 0; px < w; px += cell) {
                double x = x0 + px / sc, z = z0 + py / sc;
                if (z > z1 || x > x1) continue;
                double gr = bay.ground(x, z), wa = bay.water(x, z);
                Color c;
                if (!Double.isNaN(wa) && wa > gr) c = new Color(40, 80, 120);
                else if (gr < 1.5) c = new Color(170, 160, 120);
                else { int v = (int) Math.max(40, Math.min(120, 60 + gr * 1.2)); c = new Color(v / 2, v, v / 2); }
                g.setColor(c);
                g.fillRect(ox + px, oy + py, cell, cell);
            }
        java.util.function.DoubleUnaryOperator X = x -> ox + (x - x0) * sc, Z = z -> oy + (z - z0) * sc;
        for (Sleuth.Beach b : s.beaches) { g.setColor(new Color(255, 255, 255, 120)); g.setStroke(new BasicStroke(1.5f)); g.draw(new Ellipse2D.Double(X.applyAsDouble(b.x() - b.radius()), Z.applyAsDouble(b.z() - b.radius()), 2 * b.radius() * sc, 2 * b.radius() * sc)); }
        for (Sleuth.Stream st : s.streams) for (double[] q : st.spots()) { g.setColor(new Color(255, 120, 90)); g.fill(new Ellipse2D.Double(X.applyAsDouble(q[0]) - 3, Z.applyAsDouble(q[1]) - 3, 6, 6)); }
        for (double[] t : trail) { Color c = COL[(int) t[2] % COL.length]; g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90)); g.fillRect((int) X.applyAsDouble(t[0]), (int) Z.applyAsDouble(t[1]), 2, 2); }
        for (Sleuth.Animal a : s.animals()) {
            double x = X.applyAsDouble(a.x), z = Z.applyAsDouble(a.z), r = Math.max(4, a.length * sc * 0.5), hd = Math.toRadians(a.heading);
            g.setColor(COL[a.id % COL.length]);
            g.fill(new Ellipse2D.Double(x - r, z - r, 2 * r, 2 * r));
            g.setColor(Color.BLACK);
            g.drawLine((int) x, (int) z, (int) (x + Math.sin(hd) * r * 2), (int) (z - Math.cos(hd) * r * 2));
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString(a.act.label, (int) (x + r + 3), (int) z + 4);
        }
        if (!Double.isNaN(hx)) {
            g.setColor(Color.RED);
            double x = X.applyAsDouble(hx), z = Z.applyAsDouble(hz);
            g.fill(new Ellipse2D.Double(x - 5, z - 5, 10, 10));
            g.setColor(new Color(255, 80, 80, 120));
            double ar = s.bear.alertDistance * sc, fr = s.bear.flightDistance * sc;
            g.draw(new Ellipse2D.Double(x - ar, z - ar, 2 * ar, 2 * ar));
            g.draw(new Ellipse2D.Double(x - fr, z - fr, 2 * fr, 2 * fr));
        }
    }

    // ------------------------------------------------------------------ Fenster

    static void fenster() {
        FFrame fr = new FFrame("com.dan.bear · Küstenbraunbär");
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(12, 18, 24));
        DemoBay bay = new DemoBay();
        bay.tide = -0.6;
        Sleuth s = BearCheck.sleuth(bay, 3, 2, 8, 7);
        List<double[]> trail = new ArrayList<>();
        double[] human = {Double.NaN, 0};
        int[] which = {1};
        double[] clock = {0};
        JLabel info = new JLabel(" ");
        info.setForeground(Color.WHITE);
        JComponent nah = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                int w = Math.max(64, getWidth()), h = Math.max(64, getHeight());
                Soft so = new Soft(w, h);
                float[][] q = pose(which[0], clock[0] * (which[0] == 3 ? 2.4 : which[0] == 2 ? 1.3 : 0.9) % 1);
                so.look(5.8, 1.3, -2.4, 0, 0.6, -0.6);
                so.ground(0, 4, 0xFF6A7A5E);
                so.bear(0, -q[1][0] * 2.3, 0, 2.3, 0, q[0], 0.4, which[0] == 6 ? 0.8 : 0);
                g0.drawImage(so.image(), 0, 0, null);
            }
        };
        nah.setPreferredSize(new Dimension(640, 440));
        JComponent karte = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawMap(g, 0, 0, getWidth(), getHeight(), bay, s, trail, human[0], human[1]);
            }
        };
        karte.setPreferredSize(new Dimension(640, 440));
        karte.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                double sc = Math.min(karte.getWidth() / 600.0, karte.getHeight() / 440.0);
                if (SwingUtilities.isRightMouseButton(e)) { human[0] = Double.NaN; s.disturbers.clear(); }
                else { human[0] = -300 + e.getX() / sc; human[1] = -320 + e.getY() / sc; }
            }
        });
        FComboBox poseBox = new FComboBox(POSE_NAMES);
        poseBox.setSelectedIndex(1);
        poseBox.addActionListener(e -> which[0] = poseBox.getSelectedIndex());
        FSlider tempo = new FSlider(1, 60, 10);
        com.dan.fbutton.FButton beben = new com.dan.fbutton.FButton("Beben");
        beben.addActionListener(e -> s.quake(1));
        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (Component c : new Component[]{label("Haltung (links)"), poseBox, Box.createVerticalStrut(10), label("Zeitraffer der Karte"), tempo, Box.createVerticalStrut(10), beben,
                Box.createVerticalStrut(10), label("<html>Klick in die Karte: ein Wanderer<br>(Rechtsklick nimmt ihn weg)</html>")}) {
            if (c instanceof JComponent j) j.setAlignmentX(0);
            side.add(c);
        }
        JPanel mid = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
        mid.setOpaque(false);
        mid.add(nah);
        mid.add(karte);
        root.add(mid, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        root.add(info, BorderLayout.SOUTH);
        fr.setContentPane(root);
        fr.pack();
        fr.setLocationRelativeTo(null);
        fr.setVisible(true);
        long[] last = {System.nanoTime()};
        new Timer(40, e -> {
            long now = System.nanoTime();
            double dt = Math.min(0.1, (now - last[0]) / 1e9);
            last[0] = now;
            clock[0] += dt;
            s.disturbers.clear();
            if (!Double.isNaN(human[0])) s.disturbers.add(new Sleuth.Disturber(human[0], human[1], 1, false));
            s.step(dt * tempo.getValue());
            if (trail.size() > 20000) trail.subList(0, 5000).clear();
            for (Sleuth.Animal an : s.animals()) trail.add(new double[]{an.x, an.z, an.id});
            info.setText(" " + s.describe() + " · " + s.bear.describe());
            nah.repaint();
            karte.repaint();
        }).start();
    }

    static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(new Color(220, 230, 235));
        return l;
    }
}
