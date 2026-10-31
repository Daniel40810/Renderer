package com.dan.rime.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.rime.Rime;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Eigene kleine Vorführung (Paketvertrag Punkt 3 und 10): fünf Stäbe von 5 mm bis 30 cm im Schnitt, an denen in einer
 * Raueislage die Fahnen gegen den Wind wachsen, mit Zeitraffer und Regler für Wind und Temperatur; rechts die Raten
 * (mm/h am Anfang) nach Durchmesser und Wind und der Fangkoeffizient α1 (seit Brocken Phase 10). Der sechste Stab ist
 * ein rauer Mast von 30 cm (Federn fangen wie dünne Zweige). Ohne Fenster schreibt
 * {@code RimeDemo --bild datei.png [stunden]} ein Standbild.
 */
public final class RimeDemo {
    private RimeDemo() { }

    static final double[] DM = {0.005, 0.02, 0.05, 0.12, 0.30, 0.30};
    /** Der letzte Stab ist rau (Mast mit Kanten, Fels). */
    static final boolean[] RAU = {false, false, false, false, false, true};
    static final double[] WINDE = {5, 10, 15, 20};

    static BufferedImage render(Rime.Ansatz[] a, Rime.Lage l, double stunden, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(15, 19, 21));
        g.fillRect(0, 0, w, h);
        int tw = Math.min(w / 2, 470), sw = w - tw;   // rechts die Tabelle
        double s = Math.min(sw / 7.0, h * 0.7) / 0.35;   // Pixel je Meter
        for (int i = 0; i < a.length; i++) {
            double cx = sw * (i + 1) / (a.length + 1.0), cy = h * 0.5, r = a[i].durchmesser / 2 * s;
            if (a[i].dicke > 0) {
                double t = a[i].dicke * s;
                g.setColor(new Color(220, 230, 242));
                if (a[i].art == Rime.Art.RAUEIS) {
                    // Fahne gegen den Wind: der Wind kommt von links (West), die Fahne zeigt nach links
                    Path2D p = new Path2D.Double();
                    p.moveTo(cx, cy - r);
                    p.lineTo(cx - r - t, cy - r * 0.35);
                    p.lineTo(cx - r - t, cy + r * 0.35);
                    p.lineTo(cx, cy + r);
                    p.closePath();
                    g.fill(p);
                } else g.fill(new Ellipse2D.Double(cx - r - t, cy - r - t, 2 * (r + t), 2 * (r + t)));
            }
            g.setColor(new Color(110, 92, 80));
            g.fill(new Ellipse2D.Double(cx - Math.max(1, r), cy - Math.max(1, r), 2 * Math.max(1, r), 2 * Math.max(1, r)));
            g.setColor(new Color(186, 194, 196));
            g.drawString(String.format(java.util.Locale.GERMAN, "%.0f mm%s: %.1f cm", DM[i] * 1000, RAU[i] ? " rau" : "", a[i].dicke * 100), (int) cx - 40, (int) (h * 0.85));
        }
        // Raten am Anfang (mm/h) bei der Temperatur und dem Flüssigwasser der Lage, nach Durchmesser und Wind
        int x0 = sw + 10, y0 = 46, rh = Math.max(16, (h - 80) / (DM.length + 3));
        g.setColor(new Color(147, 156, 159));
        g.drawString(String.format(java.util.Locale.GERMAN, "Raten (mm/h) bei %.0f °C, %.2f g/m³, Tropfen %.0f µm · α1 bei %.0f m/s", l.tempC, l.lwc, l.tropfenUm, l.wind), x0, y0 - 18);
        g.drawString("Objekt", x0, y0);
        for (int k = 0; k < WINDE.length; k++) g.drawString(String.format("%.0f m/s", WINDE[k]), x0 + 90 + k * 70, y0);
        g.drawString("α1", x0 + 90 + WINDE.length * 70, y0);
        for (int i = 0; i < DM.length; i++) {
            int y = y0 + (i + 1) * rh;
            g.setColor(new Color(186, 194, 196));
            g.drawString(String.format(java.util.Locale.GERMAN, "%.0f mm%s", DM[i] * 1000, RAU[i] ? " rau" : ""), x0, y);
            for (int k = 0; k < WINDE.length; k++) {
                Rime.Lage q = new Rime.Lage();
                q.tempC = l.tempC; q.lwc = l.lwc; q.tropfenUm = l.tropfenUm; q.wind = WINDE[k];
                double mm = Rime.mmJeStunde(DM[i], RAU[i], q);
                g.setColor(mm > 5 ? new Color(220, 230, 242) : mm > 1 ? new Color(159, 192, 206) : new Color(110, 120, 124));
                g.drawString(String.format(java.util.Locale.GERMAN, "%.1f", mm), x0 + 90 + k * 70, y);
            }
            g.setColor(new Color(230, 185, 94));
            g.drawString(String.format(java.util.Locale.GERMAN, "%.2f", Rime.alpha1(l.tropfenUm, l.wind, RAU[i] ? Math.min(DM[i], Rime.RAU) : DM[i])), x0 + 90 + WINDE.length * 70, y);
        }
        g.setColor(new Color(147, 156, 159));
        g.drawString("Makkonen (2000), α1 nach Finstad u. a. (1988), Dichte nach Macklin (1962)", x0, y0 + (DM.length + 2) * rh);
        g.setColor(new Color(232, 234, 233));
        g.drawString(String.format(java.util.Locale.GERMAN, "nach %.1f h · %s · Wind von links", stunden, l), 12, 20);
        g.dispose();
        return im;
    }

    static Rime.Ansatz[] staebe() {
        Rime.Ansatz[] a = new Rime.Ansatz[DM.length];
        for (int i = 0; i < a.length; i++) a[i] = new Rime.Ansatz(DM[i], RAU[i]);
        return a;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            double std = args.length > 2 ? Double.parseDouble(args[2]) : 8;
            Rime r = new Rime();
            Rime.Lage l = Rime.raueislage();
            Rime.Ansatz[] a = staebe();
            for (Rime.Ansatz x : a) r.step(x, l, std * 3600);
            javax.imageio.ImageIO.write(render(a, l, std, 1400, 420), "png", new File(args[1]));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(RimeDemo::open);
    }

    private static void open() {
        Rime r = new Rime();
        Rime.Lage l = Rime.raueislage();
        l.fromDeg = 270;
        Rime.Ansatz[][] a = {staebe()};
        double[] std = {0};
        FFrame f = new FFrame("Rime-Package · Demo");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(new Color(15, 19, 21));
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) { g.drawImage(render(a[0], l, std[0], Math.max(1, getWidth()), Math.max(1, getHeight())), 0, 0, null); }
        };
        view.setPreferredSize(new Dimension(1400, 420));
        FSlider wind = new FSlider(0, 25, 12), temp = new FSlider(-20, 5, -6);
        JLabel lbl = new JLabel("Wind 12 m/s · −6 °C");
        lbl.setForeground(new Color(232, 234, 233));
        Runnable neu = () -> {
            l.wind = wind.getValue();
            l.tempC = temp.getValue();
            lbl.setText("Wind " + wind.getValue() + " m/s · " + temp.getValue() + " °C");
        };
        wind.addChangeListener(e -> neu.run());
        temp.addChangeListener(e -> neu.run());
        JPanel south = new JPanel(new GridLayout(1, 3, 10, 0));
        south.setBackground(new Color(15, 19, 21));
        south.add(lbl);
        south.add(wind);
        south.add(temp);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1460, 560);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        // Zeitraffer: eine Stunde je Sekunde
        new Timer(50, e -> {
            for (Rime.Ansatz x : a[0]) r.step(x, l, 180);
            std[0] += 0.05;
            if (std[0] > 24) { a[0] = staebe(); std[0] = 0; }
            view.repaint();
        }).start();
    }
}
