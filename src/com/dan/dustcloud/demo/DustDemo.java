package com.dan.dustcloud.demo;

import com.dan.dustcloud.DustCloud;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung von {@link DustCloud} (seit LituyaBay1958 Phase 12): ein Felssturz an einer Bergflanke über einem Fjord
 * wirft 30 s lang Staub auf; die Wolke fließt die Rinne hinab, über das Wasser, wird verdünnt, vom Westwind genommen und
 * setzt sich ab. Links von oben (Gelände, Wasser, optische Dicke der Wolke, Ablagerung), rechts der Schnitt durch die
 * Rinne (Oberkante der Wolke), die Bilanz und die Sichtweite. Fenster mit Zeitregler; {@code --bild datei.png [t]}.
 */
public final class DustDemo {
    private DustDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170), ACC = new Color(240, 200, 110);
    static final double T_END = 600, X0 = 0, Z0 = 0, W = 8000, H = 5000;

    /** Fjord von West nach Ost ab z 3 500 m; südlich davon die Flanke bis 1 200 m mit einer Rinne bei x 3 000 m. */
    static double ground(double x, double z) {
        if (z > 3500) return 0;
        double d = 3500 - z, rinne = 120 * Math.exp(-((x - 3000) * (x - 3000)) / (2 * 300 * 300.0));
        return Math.min(1200, d * 0.45 + 60 * Math.sin(x / 700) * Math.min(1, d / 800)) - rinne * Math.min(1, d / 300);
    }

    static DustCloud cloud() {
        DustCloud c = new DustCloud(X0, Z0, 160, 100, 50, DustCloud.rockDust());
        c.name = "Felssturz an der Fjordflanke";
        c.wind = (x, z, t) -> new double[]{6, 0};
        c.build(new DustCloud.Ground() {
            public double height(double x, double z) { return ground(x, z); }

            public boolean water(double x, double z) { return z > 3500; }
        });
        // 30 s Staub an der Abrisskante: 2 000 m³/s Feststoff in 3e7 m³/s Wolke (Anteil 7e-5), hangab 25 m/s [G]
        c.source(new DustCloud.Source(3000, 1600, 200, 0, 30, 2000, 3e7, 0, 25));
        return c;
    }

    /** Ein Augenblick: optische Dicke, Mächtigkeit, Schnitt, Bilanz. */
    public record Frame(double t, float[] tau, float[] thick, float[] dep, double[] budget, double vis) { }

    public static final class Film {
        public final DustCloud cloud = cloud();
        public final List<Frame> frames = new ArrayList<>();

        public Film(double tEnd) {
            frames.add(frame());
            for (double t = 10; t <= tEnd + 1e-9; t += 10) { cloud.run(t); frames.add(frame()); }
        }

        Frame frame() {
            float[][] s = cloud.snapshot();
            return new Frame(cloud.time(), s[1], s[0], s[2], cloud.budget(), cloud.visibility(3000, 3600));
        }

        public Frame at(double t) { return frames.get((int) Math.max(0, Math.min(frames.size() - 1, Math.round(t / 10)))); }
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            double t = a.length > 2 ? Double.parseDouble(a[2]) : 120;
            Film f = new Film(Math.max(t, 300));
            BufferedImage im = new BufferedImage(1800, 1000, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, f, t, 1800, 1000);
            g.dispose();
            ImageIO.write(im, "png", new File(a[1]));
            System.out.println(text(f));
            System.out.println("Bild: " + a[1] + " (t = " + fmt("%.0f", f.at(t).t()) + " s)");
            return;
        }
        SwingUtilities.invokeLater(DustDemo::open);
    }

    public static String text(Film f) {
        DustCloud c = f.cloud;
        StringBuilder b = new StringBuilder(c.describe()).append('\n');
        for (int k = 0; k < f.frames.size(); k += 6) {
            Frame fr = f.frames.get(k);
            double[] u = fr.budget();
            b.append(fmt("  t %4.0f s: Staub in der Luft %7.0f m³, abgesetzt an Land %7.0f m³, im Wasser %7.0f m³, hinaus %6.0f m³; Sicht über dem Wasser vor der Rinne %s%n",
                    fr.t(), u[0], u[1], u[2], u[3], Double.isInfinite(fr.vis()) ? "frei" : fmt("%.1f m", fr.vis())));
        }
        b.append(fmt("  %d Schritte, %.1f s Rechnung für %.0f s", c.steps(), c.busyMs() / 1000, c.time()));
        return b.toString();
    }

    static String fmt(String f, Object... o) { return String.format(Locale.GERMAN, f, o); }

    public static void paint(Graphics2D g, Film f, double t, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        Frame fr = f.at(t);
        DustCloud c = f.cloud;
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString("Staubwolke · com.dan.dustcloud · " + fmt("t = %.0f s", fr.t()), 20, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(DIM);
        g.drawString(c.describe() + " · Westwind 6 m/s · Quelle 30 s an der Abrisskante", 20, 52);
        // Draufsicht
        int mx = 20, my = 70, mw = (int) (w * 0.58), mh = (int) (mw * H / W);
        BufferedImage im = new BufferedImage(c.nx, c.nz, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < c.nz; j++)
            for (int i = 0; i < c.nx; i++) {
                double x = c.cx(i), z = c.cz(j), gr = ground(x, z);
                Color base;
                if (z > 3500) base = new Color(28, 62, 88);
                else {
                    double sh = 0.5 + 0.5 * Math.tanh((ground(x - 50, z) - ground(x + 50, z) + ground(x, z - 50) - ground(x, z + 50)) / 80);
                    double k = Math.min(1, gr / 1200);
                    base = new Color((int) (50 + 70 * k * sh + 40 * sh), (int) (70 + 50 * k * sh + 30 * sh), (int) (50 + 50 * k));
                }
                double tau = fr.tau()[j * c.nx + i], a = 0.9 * (1 - Math.exp(-tau / 400));
                double dep = fr.dep()[j * c.nx + i];
                Color dust = new Color(205, 190, 165);
                int rr = (int) (base.getRed() * (1 - a) + dust.getRed() * a), gg = (int) (base.getGreen() * (1 - a) + dust.getGreen() * a), bb = (int) (base.getBlue() * (1 - a) + dust.getBlue() * a);
                double dd = Math.min(1, dep * 2000);
                rr = (int) (rr * (1 - 0.3 * dd) + 160 * 0.3 * dd);
                im.setRGB(i, c.nz - 1 - j, new Color(clamp(rr), clamp(gg), clamp(bb)).getRGB());
            }
        g.drawImage(im, mx, my, mw, mh, null);
        g.setColor(DIM);
        g.drawRect(mx, my, mw, mh);
        g.drawString("von oben: optische Dicke der Wolke (hell), Fjord ab z 3 500 m, Rinne bei x 3 000 m", mx, my + mh + 18);
        // Schnitt längs der Rinne
        int sx = mx + mw + 30, sy = my, sw = w - sx - 20, sh = (int) (mh * 0.55);
        g.setColor(new Color(20, 28, 38));
        g.fillRect(sx, sy, sw, sh);
        double zMax = H, yMax = 1400;
        Path2D gp = new Path2D.Double(), cp = new Path2D.Double();
        for (int j = 0; j < c.nz; j++) {
            double z = c.cz(j), px = sx + sw * z / zMax, gy = ground(3000, z);
            double top = gy + fr.thick()[j * c.nx + (int) ((3000 - X0) / c.dx)];
            if (j == 0) { gp.moveTo(px, sy + sh - sh * gy / yMax); cp.moveTo(px, sy + sh - sh * top / yMax); } else { gp.lineTo(px, sy + sh - sh * gy / yMax); cp.lineTo(px, sy + sh - sh * top / yMax); }
        }
        g.setColor(new Color(205, 190, 165, 140));
        Path2D fill = new Path2D.Double(cp);
        for (int j = c.nz - 1; j >= 0; j--) { double z = c.cz(j); fill.lineTo(sx + sw * z / zMax, sy + sh - sh * ground(3000, z) / yMax); }
        fill.closePath();
        g.fill(fill);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(150, 170, 120));
        g.draw(gp);
        g.setColor(ACC);
        g.draw(cp);
        g.setStroke(new BasicStroke(1f));
        g.setColor(DIM);
        g.drawRect(sx, sy, sw, sh);
        g.drawString("Schnitt durch die Rinne (x 3 000 m): Grund und Oberkante der Wolke, 0–1 400 m hoch, Süd → Nord", sx, sy + sh + 18);
        // Bilanz
        int ty = sy + sh + 50;
        double[] u = fr.budget();
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("Staub (m³ Feststoff)", sx, ty);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        String[] lab = {"in der Luft", "abgesetzt an Land", "im Wasser", "über den Rand", "eingebracht"};
        double[] val = {u[0], u[1], u[2], u[3], u[5]};
        double tot = Math.max(1, u[5]);
        for (int k = 0; k < lab.length; k++) {
            int y = ty + 24 + 24 * k;
            g.setColor(DIM);
            g.drawString(lab[k], sx, y);
            g.setColor(k == 0 ? new Color(205, 190, 165) : k == 2 ? new Color(90, 150, 200) : k == 1 ? new Color(160, 140, 110) : DIM);
            g.fillRect(sx + 150, y - 12, (int) ((sw - 260) * val[k] / tot), 14);
            g.setColor(INK);
            g.drawString(fmt("%,.0f", val[k]), sx + sw - 100, y);
        }
        g.setColor(DIM);
        g.drawString(Double.isInfinite(fr.vis()) ? "Sicht über dem Wasser vor der Rinne: frei" : (fr.vis() < 1 ? "Sicht über dem Wasser vor der Rinne: unter 1 m" : fmt("Sicht über dem Wasser vor der Rinne: %.0f m", fr.vis())), sx, ty + 24 * 7);
        g.drawString("Körner 2 / 10 / 50 / 200 µm sinken mit 0,3 mm/s bis 1,7 m/s:", sx, ty + 24 * 8);
        g.drawString("die groben fallen in Minuten, die feinen bleiben Stunden in der Luft", sx, ty + 24 * 9);
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    static void open() {
        Film[] film = {null};
        double[] time = {60};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics gr) {
                if (film[0] == null) { gr.setColor(BG); gr.fillRect(0, 0, getWidth(), getHeight()); gr.setColor(INK); gr.drawString("rechnet …", 40, 40); return; }
                DustDemo.paint((Graphics2D) gr, film[0], time[0], getWidth(), getHeight());
            }
        };
        view.setPreferredSize(new Dimension(1700, 950));
        FFrame f = new FFrame("Staubwolke · com.dan.dustcloud");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT));
        west.setBackground(BG);
        JLabel l = new JLabel("  Zeit (s)  ");
        l.setForeground(INK);
        west.add(l);
        south.add(west, BorderLayout.WEST);
        FSlider s = new FSlider(0, (int) T_END, 60);
        s.addChangeListener(e -> { time[0] = s.getValue(); view.repaint(); });
        south.add(s, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1760, 1050);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Thread(() -> { Film fm = new Film(T_END); SwingUtilities.invokeLater(() -> { film[0] = fm; view.repaint(); }); }, "staub").start();
    }
}
