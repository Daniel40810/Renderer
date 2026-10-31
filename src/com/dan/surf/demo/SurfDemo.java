package com.dan.surf.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.surf.Surf;

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

/**
 * Vorführung der Brandung ohne Sylt-Code (Paketvertrag Punkt 3 und 10), Stand Hülle (Sylt Phase 1): drei Querschnitte
 * – flaches Riff 1:100, Strandhang 1:30, steiler Hang 1:8 – mit der Wellenhöhe nach Shoaling, dem Brechpunkt nach
 * McCowan und dem Brechertyp nach Battjes. {@code SurfDemo --bild datei.png [bft]} schreibt ein Standbild.
 */
public final class SurfDemo {
    private SurfDemo() { }

    static final double[] SLOPES = {1 / 100.0, 1 / 30.0, 1 / 8.0};
    static final String[] NAMES = {"Riff 1:100", "Strand 1:30", "steiler Hang 1:8"};

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            Surf s = args.length > 2 ? Surf.beaufort(Integer.parseInt(args[2])) : Surf.sylt();
            javax.imageio.ImageIO.write(render(s, 960, 600, 0), "png", new File(args[1]));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(SurfDemo::open);
    }

    static BufferedImage render(Surf base, int w, int h, double t) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(14, 20, 22));
        g.fillRect(0, 0, w, h);
        int rows = SLOPES.length, rh = h / rows;
        for (int r = 0; r < rows; r++) {
            Surf s = new Surf();
            s.hs = base.hs;
            s.tp = base.tp;
            s.seaFromDeg = 270;
            double slope = SLOPES[r], depth0 = Math.min(12, 6 + 300 * slope), len = depth0 / slope * 1.08;
            s.bed = (x, z) -> -depth0 + x * slope;
            int y0 = r * rh;
            double yTop = 2.0 + s.hs, yBot = -depth0 - 0.5;
            java.util.function.DoubleUnaryOperator px = x -> 10 + (w - 20) * x / len;
            java.util.function.DoubleUnaryOperator py = y -> y0 + 26 + (rh - 36) * (yTop - y) / (yTop - yBot);
            Path2D sand = new Path2D.Double(), water = new Path2D.Double();
            sand.moveTo(px.applyAsDouble(0), py.applyAsDouble(yBot));
            water.moveTo(px.applyAsDouble(0), py.applyAsDouble(-depth0));
            double xb = s.breakPointAlong(0, 0, len, 0, 0), phase = 0;
            for (int i = 0; i <= 600; i++) {
                double x = len * i / 600;
                phase += Surf.wavenumber(s.tp, Math.max(0.02, s.depthAt(x, 0, 0))) * len / 600;   // ∫k dx
                sand.lineTo(px.applyAsDouble(x), py.applyAsDouble(s.bed.height(x, 0)));
                double hh = s.heightAt(x, 0, 0);
                double eta = s.depthAt(x, 0, 0) > 0 ? 0.5 * hh * Math.sin(phase - 2 * Math.PI / s.tp * t) : s.bed.height(x, 0);
                water.lineTo(px.applyAsDouble(x), py.applyAsDouble(Math.max(eta, s.bed.height(x, 0))));
            }
            sand.lineTo(px.applyAsDouble(len), py.applyAsDouble(yBot));
            sand.closePath();
            water.lineTo(px.applyAsDouble(len), py.applyAsDouble(-depth0));
            water.closePath();
            g.setColor(new Color(36, 104, 98));
            g.fill(water);
            g.setColor(new Color(216, 195, 154));
            g.fill(sand);
            if (!Double.isNaN(xb)) {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2f));
                int bx = (int) px.applyAsDouble(xb);
                g.drawLine(bx, (int) py.applyAsDouble(yTop - 0.3), bx, (int) py.applyAsDouble(s.bed.height(xb, 0)));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(new Color(233, 236, 234));
            String info = Double.isNaN(xb) ? "bricht nicht" : String.format("bricht bei h = %.2f m, H = %.2f m: %s, ξb = %.2f",
                    s.depthAt(xb, 0, 0), s.heightAt(xb, 0, 0), s.typeAt(xb, 0, 0),
                    Surf.iribarren(slope, Math.max(1e-3, s.heightAt(xb, 0, 0)), s.tp));
            g.drawString(NAMES[r] + "   ·   Hs " + String.format("%.1f m, Tp %.0f s", s.hs, s.tp) + "   ·   " + info, 14, y0 + 18);
        }
        g.dispose();
        return im;
    }

    private static void open() {
        Surf s = Surf.sylt();
        FFrame f = new FFrame("Surf-Package · Demo (Hülle)");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(new Color(14, 20, 22));
        long t0 = System.nanoTime();
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.drawImage(render(s, Math.max(1, getWidth()), Math.max(1, getHeight()), (System.nanoTime() - t0) / 1e9), 0, 0, null);
            }
        };
        view.setPreferredSize(new Dimension(960, 600));
        FSlider bft = new FSlider(0, 12, 4);
        JLabel lbl = new JLabel("Sylt");
        lbl.setForeground(new Color(233, 236, 234));
        bft.addChangeListener(e -> {
            Surf p = Surf.beaufort(bft.getValue());
            s.hs = p.hs;
            s.tp = p.tp;
            lbl.setText("Beaufort " + bft.getValue());
        });
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(new Color(14, 20, 22));
        south.add(lbl, BorderLayout.WEST);
        south.add(bft, BorderLayout.CENTER);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1000, 700);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(40, e -> view.repaint()).start();
    }
}
