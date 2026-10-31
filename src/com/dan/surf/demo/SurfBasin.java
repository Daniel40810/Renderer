package com.dan.surf.demo;

import com.dan.fbutton.FButton;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.surf.Bars;
import com.dan.surf.Surf;
import com.dan.surf.WaveField;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Das Testbecken des Wellenfelds (seit Sylt Phase 5, Paketvertrag Punkt 3 und 10): ein gerader Strand 1:75 mit einem
 * Riff 120 m vor der Wasserlinie, in Bögen, alle 300 m von einer Rinne durchbrochen. Oben die Draufsicht: Wellenhöhe
 * H_s als Farbe, Brandung (Q_b) hell, die Wellenrichtung als kurze Striche, die mittlere Strömung als Pfeile (Unterstrom,
 * Längsstrom, Rippströmung in den Rinnen). Unten zwei Querschnitte (über dem Riff und durch eine Rinne): Grund,
 * Wellenhöhe, Set-up und Strömung. Regler für H_s und Richtung; „Sturm“ verlagert Riffbögen und Rinnen.
 * {@code SurfBasin --bild datei.png [H_s [Richtung]]} schreibt ein Standbild.
 */
public final class SurfBasin {
    private SurfBasin() { }

    /** Das Becken: Wasserlinie bei x = 600, See im Westen, 900 m längs. */
    public static final class Basin {
        public final Surf surf = Surf.sylt();
        public final Bars bars = new Bars();
        public final Bars.Coast coast = Bars.Coast.straight(600, 0, 270);
        public final WaveField field;

        public Basin() {
            bars.crest = new double[]{120};
            bars.height = new double[]{1.2};
            bars.width = new double[]{30};
            bars.ripSpacing = 300;
            bars.ripJitter = 0.15;
            surf.hs = 1.2;
            surf.tp = 8;
            surf.seaFromDeg = 270;
            surf.bed = bars.apply((x, z) -> -8 + x / 75.0, coast);
            field = new WaveField(surf, 0, -450, 90, 700, 900, 4);
            surf.field = field;
        }

        public void storm() {
            bars.storm();
            field.bedVersion++;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            Basin b = new Basin();
            if (args.length > 2) b.surf.hs = Double.parseDouble(args[2]);
            if (args.length > 3) b.surf.seaFromDeg = Double.parseDouble(args[3]);
            b.field.compute(0);
            javax.imageio.ImageIO.write(render(b, 1500, 1000), "png", new File(args[1]));
            System.out.println(b.field.describe());
            System.out.println(b.bars.describe());
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(SurfBasin::open);
    }

    static Color ramp(double t) {
        t = Math.max(0, Math.min(1, t));
        double[][] c = {{20, 52, 74}, {36, 104, 98}, {120, 180, 150}, {232, 178, 92}, {224, 110, 60}};
        double s = t * (c.length - 1);
        int i = Math.min(c.length - 2, (int) s);
        double f = s - i;
        return new Color((int) (c[i][0] + (c[i + 1][0] - c[i][0]) * f), (int) (c[i][1] + (c[i + 1][1] - c[i][1]) * f), (int) (c[i][2] + (c[i + 1][2] - c[i][2]) * f));
    }

    static BufferedImage render(Basin b, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(14, 20, 22));
        g.fillRect(0, 0, w, h);
        WaveField f = b.field;
        WaveField.Result r = f.result();
        int planH = (int) (h * 0.58);
        // Draufsicht: x (0..700) nach rechts, z (−450..450) nach unten
        double x0 = 0, x1 = 700, z0 = -420, z1 = 420;
        int pl = 10, pr = w - 170, pt = 30, pb = planH - 10;
        java.util.function.DoubleUnaryOperator PX = x -> pl + (pr - pl) * (x - x0) / (x1 - x0);
        java.util.function.DoubleUnaryOperator PY = z -> pt + (pb - pt) * (z - z0) / (z1 - z0);
        int cw = 4;
        double hsMax = b.surf.hs * 1.15;
        for (int py = pt; py < pb; py += cw)
            for (int px = pl; px < pr; px += cw) {
                double x = x0 + (px - pl + cw / 2.0) / (pr - pl) * (x1 - x0), z = z0 + (py - pt + cw / 2.0) / (pb - pt) * (z1 - z0);
                double bed = b.surf.bed.height(x, z), depth = b.surf.level.level(0) - bed + f.setupAt(x, z);
                Color c;
                if (depth <= 0.05) {
                    double l = Math.max(0, Math.min(1, bed / 3));
                    c = new Color((int) (200 + 30 * l), (int) (178 + 25 * l), (int) (135 + 25 * l));
                } else {
                    double hs = f.hsAt(x, z);
                    c = ramp(Double.isNaN(hs) ? 0 : hs / hsMax);
                    double qb = f.breakingAt(x, z);
                    int k = (int) (160 * Math.min(1, qb * 1.6));
                    c = new Color(Math.min(255, c.getRed() + k), Math.min(255, c.getGreen() + k), Math.min(255, c.getBlue() + k));
                }
                g.setColor(c);
                g.fillRect(px, py, cw, cw);
            }
        // Tiefenlinien (−6 bis 0 m)
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 60));
        for (double lv = -6; lv <= 0; lv += 1) {
            for (int py = pt; py < pb; py += 2) {
                double z = z0 + (py - pt) / (double) (pb - pt) * (z1 - z0);
                double prev = Double.NaN;
                for (double x = x0; x < x1; x += 2) {
                    double v = b.surf.bed.height(x, z) - lv;
                    if (!Double.isNaN(prev) && prev < 0 && v >= 0) g.fillRect((int) PX.applyAsDouble(x), py, 1, 1);
                    prev = v;
                }
            }
        }
        // Wellenrichtung (Striche) und Strömung (Pfeile)
        for (double z = z0 + 30; z < z1; z += 45)
            for (double x = 40; x < 620; x += 40) {
                if (b.surf.bed.height(x, z) > b.surf.level.level(0) - 0.1) continue;
                double[] d = f.directionAt(x, z);
                int cx = (int) PX.applyAsDouble(x), cy = (int) PY.applyAsDouble(z);
                g.setColor(new Color(255, 255, 255, 110));
                g.drawLine(cx - (int) (d[0] * 7), cy - (int) (d[1] * 7), cx + (int) (d[0] * 7), cy + (int) (d[1] * 7));
            }
        double scale = 60;   // Pixel je m/s
        g.setStroke(new BasicStroke(1.6f));
        for (double z = z0 + 15; z < z1; z += 22)
            for (double x = 380; x < 600; x += 18) {
                if (b.surf.bed.height(x, z) > b.surf.level.level(0) - 0.15) continue;
                double[] u = f.currentAt(x, z);
                double sp = Math.hypot(u[0], u[1]);
                if (sp < 0.03) continue;
                int cx = (int) PX.applyAsDouble(x), cy = (int) PY.applyAsDouble(z);
                int ex = cx + (int) (u[0] * scale), ey = cy + (int) (u[1] * scale);
                g.setColor(u[0] < -0.15 ? new Color(255, 90, 90) : new Color(250, 250, 250));
                g.drawLine(cx, cy, ex, ey);
                double a = Math.atan2(ey - cy, ex - cx);
                g.drawLine(ex, ey, ex - (int) (6 * Math.cos(a - 0.5)), ey - (int) (6 * Math.sin(a - 0.5)));
                g.drawLine(ex, ey, ex - (int) (6 * Math.cos(a + 0.5)), ey - (int) (6 * Math.sin(a + 0.5)));
            }
        // Legende
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < 160; i++) {
            g.setColor(ramp(i / 160.0));
            g.fillRect(pr + 20, pb - 20 - i * 2, 16, 2);
        }
        g.setColor(new Color(233, 236, 234));
        g.drawString(String.format(Locale.GERMAN, "H_s %.1f m", hsMax), pr + 42, pb - 330);
        g.drawString("0 m", pr + 42, pb - 20);
        g.drawString("hell: Brandung (Q_b)", pr + 20, pt + 20);
        g.drawString("Pfeile: Strömung", pr + 20, pt + 40);
        g.setColor(new Color(255, 90, 90));
        g.drawString("rot: seewärts > 0,15 m/s", pr + 20, pt + 58);
        g.setColor(new Color(233, 236, 234));
        g.drawString(String.format(Locale.GERMAN, "Pfeil 1 m/s = %.0f px", scale), pr + 20, pt + 76);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("Testbecken: Strand 1:75, Riff 120 m vor der Wasserlinie, Rinnen etwa alle 300 m · Draufsicht (See links)", pl, 20);
        // Querschnitte: über dem Riff (zwischen zwei Rinnen) und durch eine Rinne
        double[] rips = b.bars.ripsBetween(-300, 300);
        double vRip = rips.length > 0 ? rips[0] : 0, vBar = vRip + b.bars.ripSpacing / 2;
        double zRip = -vRip, zBar = -vBar;   // längs: v = −z (See im Westen)
        g.setColor(new Color(255, 255, 255, 140));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 5}, 0));
        g.drawLine(pl, (int) PY.applyAsDouble(zBar), pr, (int) PY.applyAsDouble(zBar));
        g.drawLine(pl, (int) PY.applyAsDouble(zRip), pr, (int) PY.applyAsDouble(zRip));
        int sh = (h - planH - 20) / 2;
        section(g, b, zBar, "über dem Riff", 10, planH + 10, w - 20, sh - 6);
        section(g, b, zRip, "durch eine Rinne", 10, planH + 10 + sh, w - 20, sh - 6);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(new Color(147, 160, 162));
        g.drawString(f.describe() + "  ·  " + b.bars.describe(), 14, h - 8);
        g.dispose();
        return im;
    }

    static void section(Graphics2D g, Basin b, double z, String name, int x0, int y0, int w, int h) {
        WaveField f = b.field;
        double xa = 300, xb = 640, top = 1.8, bot = -4.5;
        java.util.function.DoubleUnaryOperator PX = x -> x0 + w * (x - xa) / (xb - xa);
        java.util.function.DoubleUnaryOperator PY = y -> y0 + 18 + (h - 24) * (top - y) / (top - bot);
        Path2D sand = new Path2D.Double(), water = new Path2D.Double(), hs = new Path2D.Double();
        sand.moveTo(PX.applyAsDouble(xa), PY.applyAsDouble(bot));
        water.moveTo(PX.applyAsDouble(xa), PY.applyAsDouble(bot));
        boolean hsStart = false;
        for (double x = xa; x <= xb; x += 1) {
            double bed = b.surf.bed.height(x, z), eta = f.setupAt(x, z), lev = b.surf.level.level(0) + eta;
            sand.lineTo(PX.applyAsDouble(x), PY.applyAsDouble(bed));
            water.lineTo(PX.applyAsDouble(x), PY.applyAsDouble(Math.max(bed, lev)));
            double hv = f.hsAt(x, z);
            if (!Double.isNaN(hv) && lev > bed + 0.05) {
                double y = lev + hv / 2;
                if (!hsStart) { hs.moveTo(PX.applyAsDouble(x), PY.applyAsDouble(y)); hsStart = true; } else hs.lineTo(PX.applyAsDouble(x), PY.applyAsDouble(y));
            }
        }
        sand.lineTo(PX.applyAsDouble(xb), PY.applyAsDouble(bot));
        sand.closePath();
        water.lineTo(PX.applyAsDouble(xb), PY.applyAsDouble(bot));
        water.closePath();
        g.setColor(new Color(36, 104, 98));
        g.fill(water);
        g.setColor(new Color(216, 195, 154));
        g.fill(sand);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 255, 255, 200));
        g.draw(hs);
        // Set-up überhöht (×10) und Strömung quer als Balken
        Path2D se = new Path2D.Double();
        boolean st = false;
        for (double x = xa; x <= xb; x += 2) {
            double bed = b.surf.bed.height(x, z), eta = f.setupAt(x, z);
            if (b.surf.level.level(0) + eta <= bed + 0.05) continue;
            double y = PY.applyAsDouble(0) - eta * 10 * (h - 24) / (top - bot);
            if (!st) { se.moveTo(PX.applyAsDouble(x), y); st = true; } else se.lineTo(PX.applyAsDouble(x), y);
        }
        g.setColor(new Color(232, 178, 92));
        g.draw(se);
        for (double x = xa + 5; x < xb; x += 10) {
            double bed = b.surf.bed.height(x, z), eta = f.setupAt(x, z);
            if (b.surf.level.level(0) + eta <= bed + 0.1) continue;
            double u = f.currentAt(x, z)[0];
            int bx = (int) PX.applyAsDouble(x), by = (int) PY.applyAsDouble(bot) - 4;
            g.setColor(u < 0 ? new Color(255, 90, 90) : new Color(121, 163, 214));
            g.fillRect(bx - 3, by - (int) Math.max(1, Math.abs(u) * 60), 6, (int) Math.max(1, Math.abs(u) * 60));
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(233, 236, 234));
        double xs = 470;
        g.drawString(String.format(Locale.GERMAN, "Querschnitt %s · weiß: Wellenhöhe H_s über dem Spiegel · gold: Set-up ×10 · Balken: Strömung quer (rot seewärts), bei x = %.0f m: %.2f m/s",
                name, xs, f.currentAt(xs, z)[0]), x0 + 6, y0 + 14);
    }

    private static void open() {
        Basin b = new Basin();
        b.field.compute(0);
        FFrame fr = new FFrame("Surf · Testbecken (Wellenfeld)");
        JPanel root = fr.getComponentPane();
        root.setLayout(new BorderLayout());
        Color bg = new Color(14, 20, 22), ink = new Color(233, 236, 234);
        root.setBackground(bg);
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.drawImage(render(b, Math.max(1, getWidth()), Math.max(1, getHeight())), 0, 0, null);
            }
        };
        view.setPreferredSize(new Dimension(1300, 860));
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel hsL = new JLabel(), dirL = new JLabel();
        hsL.setForeground(ink);
        dirL.setForeground(ink);
        FSlider hs = new FSlider(2, 40, 12), dir = new FSlider(220, 320, 270);
        hs.addChangeListener(e -> b.surf.hs = hs.getValue() / 10.0);
        dir.addChangeListener(e -> b.surf.seaFromDeg = dir.getValue());
        FButton storm = new FButton("Sturm: Rinnen verlagern");
        storm.addActionListener(e -> b.storm());
        for (java.awt.Component c : new java.awt.Component[]{hsL, hs, dirL, dir, storm}) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(8));
        }
        side.setPreferredSize(new Dimension(240, 10));
        root.add(view, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        fr.setSize(1560, 920);
        fr.setLocationRelativeTo(null);
        fr.setVisible(true);
        new Timer(200, e -> {
            hsL.setText(String.format(Locale.GERMAN, "H_s draußen %.1f m", b.surf.hs));
            dirL.setText(String.format(Locale.GERMAN, "See aus %03.0f°", b.surf.seaFromDeg));
            if (b.field.update(0) || !b.field.busy()) view.repaint();
        }).start();
    }
}
