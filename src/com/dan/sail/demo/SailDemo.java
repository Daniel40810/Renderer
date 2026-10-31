package com.dan.sail.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.sail.BoatShape;
import com.dan.sail.Regatta;
import com.dan.sail.Sail;

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
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Vorführung von {@code sail} auf dem Prozessor (Paketvertrag Punkt 3 und 10; seit Timmendorf Phase 7): links ein Boot
 * am Wind in 3D (Java2D, Dreiecke von hinten nach vorn), das mit {@link Sail#step} segelt, rechts die Polare der Klasse
 * mit den besten Kreuz- und Vorwindwinkeln, für die J/70 dazu die Punkte aus dem ORC-Zertifikat. Nur die Demo nutzt FStyle.
 *
 * <pre>
 * Fenster: SailDemo [klasse 0..8] [wind kn] [twa]
 * Bild:    SailDemo --bild datei.png [klasse] [wind kn] [twa]
 * Regatta: SailDemo --regatta datei.png [boote] [wind kn]   (seit Phase 8, ohne Fenster: ein Feld segelt die Bahn)
 * </pre>
 */
public final class SailDemo {
    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(120, 136, 150), ACC = new Color(127, 182, 198),
            ORC = new Color(240, 160, 75), SEA = new Color(28, 62, 88);
    /** Alle Takelagen (seit Phase 8 mit Schoner, Passat und Kite). */
    static final Sail.Rig[] CLASSES = Sail.RIGS.toArray(new Sail.Rig[0]);
    /** ORC One Design J/70 (data.orc.org, j70.od 2019): {TWS, TWA, Fahrt kn} für 8, 12, 16 kn. */
    static final double[][] ORC_J70 = {
            {8, 41.8, 3.70 / Math.cos(Math.toRadians(41.8))}, {8, 52, 5.54}, {8, 60, 5.74}, {8, 75, 5.95}, {8, 90, 6.22}, {8, 110, 6.32}, {8, 120, 6.21}, {8, 135, 5.85}, {8, 150, 5.15},
            {12, 37.6, 4.53 / Math.cos(Math.toRadians(37.6))}, {12, 52, 6.31}, {12, 60, 6.53}, {12, 75, 6.78}, {12, 90, 7.06}, {12, 110, 7.82}, {12, 120, 7.77}, {12, 135, 7.14}, {12, 150, 6.37},
            {16, 36.8, 4.66 / Math.cos(Math.toRadians(36.8))}, {16, 52, 6.59}, {16, 60, 6.98}, {16, 75, 7.67}, {16, 90, 8.01}, {16, 110, 8.76}, {16, 120, 9.46}, {16, 135, 9.03}, {16, 150, 7.70}};

    /** Zustand: Klasse, Wind, Kurs zum Wind, das segelnde Boot und die Polare. */
    static final class State {
        final Sail sail = new Sail();
        int cls;
        double twsKn, twa;
        Sail.Boat boat;
        double[][] polar;
        double[] vmg;
        double t, yaw = 0.9;

        State(int cls, double twsKn, double twa) {
            sail.quality = Sail.Quality.MEDIUM;
            this.cls = cls;
            this.twsKn = twsKn;
            this.twa = twa;
            reset();
        }

        void reset() {
            sail.windSpeed = twsKn * Sail.KN;
            sail.windFromDeg = 0;
            boat = new Sail.Boat(CLASSES[cls]);
            boat.headingDeg = twa;
            boat.speed = sail.speed(boat.rig, sail.windSpeed, twa)[0] * 0.7;
            polar = sail.polar(boat.rig, sail.windSpeed);
            vmg = sail.vmg(boat.rig, sail.windSpeed);
        }

        void step(double dt) {
            t += dt;
            boat.headingDeg = twa;
            sail.step(boat, dt);
            boat.x = boat.z = 0;           // die Kamera fährt mit
            yaw += dt * 0.05;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--regatta")) { regatta(args); return; }
        boolean img = args.length > 1 && args[0].equals("--bild");
        int o = img ? 2 : 0;
        int cls = args.length > o ? Integer.parseInt(args[o]) : 4;
        double kn = args.length > o + 1 ? Double.parseDouble(args[o + 1]) : 12;
        double twa = args.length > o + 2 ? Double.parseDouble(args[o + 2]) : -1;
        State s = new State(cls, kn, 45);
        if (twa < 0) s.twa = s.vmg[0];
        else s.twa = twa;
        s.reset();
        if (img) {
            for (int i = 0; i < 600; i++) s.step(1 / 30.0);
            BufferedImage im = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, s, 1280, 720);
            g.dispose();
            javax.imageio.ImageIO.write(im, "png", new File(args[1]));
            System.out.println("Bild: " + args[1] + " · " + s.sail.describe(s.boat));
            return;
        }
        SwingUtilities.invokeLater(() -> open(s));
    }

    static void paint(Graphics2D g, State s, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        int split = (int) (w * 0.58);
        boat3d(g, s, 0, 0, split, h);
        polar(g, s, split, 0, w - split, h);
    }

    /** Das Boot in 3D: Dreiecke nach Tiefe sortiert, flach beleuchtet. */
    static void boat3d(Graphics2D g, State s, int x0, int y0, int w, int h) {
        g.setColor(SEA);
        g.fillRect(x0, y0 + h / 2 - 20, w, h / 2 + 20);
        g.setPaint(new java.awt.GradientPaint(0, y0, new Color(120, 150, 180), 0, y0 + h / 2f, new Color(190, 205, 215)));
        g.fillRect(x0, y0, w, h / 2 - 20);
        BoatShape shape = new BoatShape();
        shape.time = s.t;
        float[] v = shape.build(s.boat, 0);
        int nt = v.length / 27;
        double L = s.boat.rig.loa(), dist = s.boat.rig.plan() == Sail.Plan.KITE ? 48 : Math.max(6, L * 2.4 + s.boat.rig.mastHeight() * 0.6);
        double yaw = s.yaw + Math.toRadians(s.boat.headingDeg), pitch = 0.18;
        double ex = Math.sin(yaw) * Math.cos(pitch) * dist, ez = -Math.cos(yaw) * Math.cos(pitch) * dist, ey = Math.sin(pitch) * dist + s.boat.rig.mastHeight() * 0.35;
        double tx = 0, ty = s.boat.rig.mastHeight() * 0.35, tz = 0;
        if (s.boat.rig.plan() == Sail.Plan.KITE) { ty = 7; ey = 10; }
        double fx = tx - ex, fy = ty - ey, fz = tz - ez, fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double rx = -fz, rz = fx, rl = Math.hypot(rx, rz);
        rx /= rl; rz /= rl;
        double ux = -fy * rz, uy = rz * fx - rx * fz, uz = rx * fy;
        double focal = h * 1.1;
        double[] depth = new double[nt];
        Integer[] idx = new Integer[nt];
        for (int i = 0; i < nt; i++) {
            idx[i] = i;
            double cx = 0, cy = 0, cz = 0;
            for (int k = 0; k < 3; k++) { cx += v[i * 27 + k * 9]; cy += v[i * 27 + k * 9 + 1]; cz += v[i * 27 + k * 9 + 2]; }
            depth[i] = (cx / 3 - ex) * fx + (cy / 3 - ey) * fy + (cz / 3 - ez) * fz;
        }
        Arrays.sort(idx, (a, b) -> Double.compare(depth[b], depth[a]));
        double[] sun = {-0.4, 0.75, -0.5};
        double sl = Math.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]);
        for (int i : idx) {
            if (depth[i] < 0.2) continue;
            Polygon p = new Polygon();
            boolean below = true;
            for (int k = 0; k < 3; k++) {
                int o = i * 27 + k * 9;
                double px = v[o] - ex, py = v[o + 1] - ey, pz = v[o + 2] - ez;
                double zc = px * fx + py * fy + pz * fz, xc = px * rx + pz * rz, yc = px * ux + py * uy + pz * uz;
                p.addPoint((int) (x0 + w / 2.0 + xc / zc * focal), (int) (y0 + h / 2.0 - yc / zc * focal));
                if (v[o + 1] > -0.01) below = false;
            }
            int o = i * 27;
            double nx = v[o + 3], ny = v[o + 4], nz = v[o + 5];
            double view = nx * -fx + ny * -fy + nz * -fz;
            if (view < 0) { nx = -nx; ny = -ny; nz = -nz; }
            double lam = Math.max(0, (nx * sun[0] + ny * sun[1] + nz * sun[2]) / sl);
            double lit = 0.35 + 0.75 * lam;
            float r = v[o + 6], gg = v[o + 7], b = v[o + 8];
            Color c = new Color(tone(r * lit), tone(gg * lit), tone(b * lit));
            if (below) c = mix(c, SEA, 0.65);
            g.setColor(c);
            g.fillPolygon(p);
        }
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString(String.format(Locale.GERMAN, "%s · %.0f kn aus Nord · Kurs %03.0f°", s.boat.rig.name(), s.twsKn, s.twa), x0 + 16, y0 + 26);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(DIM);
        Sail.Boat b = s.boat;
        g.drawString(String.format(Locale.GERMAN, "Fahrt %.2f kn (Polare %.2f) · Krängung %.0f° · Abdrift %.1f° · β %.0f° · scheinbarer Wind %.1f kn · Flachheit %.0f %%%s",
                b.speed / Sail.KN, s.sail.speed(b.rig, s.sail.windSpeed, s.twa)[0] / Sail.KN, b.heelDeg, b.leewayDeg, b.last[4], b.last[3] / Sail.KN, b.flat * 100,
                b.downwind ? " · Gennaker" : ""), x0 + 16, y0 + h - 16);
    }

    static int tone(double c) { c = c / (1 + c * 0.25); return (int) Math.max(0, Math.min(255, Math.pow(Math.max(0, c), 1 / 2.2) * 255)); }

    static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t), (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    /** Die Polare: Halbkreis, Wind von oben, Fahrt in kn; beste Winkel; ORC-Punkte der J/70. */
    static void polar(Graphics2D g, State s, int x0, int y0, int w, int h) {
        double max = 0;
        for (double[] r : s.polar) max = Math.max(max, r[1] / Sail.KN);
        max = Math.ceil(max + 1);
        int cx = x0 + 40, cy = y0 + h / 2, rad = Math.min(w - 80, h / 2 - 50);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(40, 52, 66));
        for (int k = 1; k <= max; k++) {
            int r = (int) (rad * k / max);
            g.drawArc(cx - r, cy - r, 2 * r, 2 * r, -90, 180);
            if (k % 2 == 0) { g.setColor(DIM); g.drawString(k + " kn", cx + 3, cy - r - 2); g.setColor(new Color(40, 52, 66)); }
        }
        for (int a = 0; a <= 180; a += 30) {
            double ra = Math.toRadians(a);
            g.drawLine(cx, cy, (int) (cx + Math.sin(ra) * rad), (int) (cy - Math.cos(ra) * rad));
        }
        Path2D p = new Path2D.Double();
        boolean first = true;
        for (double[] r : s.polar) {
            double ra = Math.toRadians(r[0]), v = r[1] / Sail.KN / max * rad;
            double px = cx + Math.sin(ra) * v, py = cy - Math.cos(ra) * v;
            if (first) p.moveTo(px, py); else p.lineTo(px, py);
            first = false;
        }
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(ACC);
        g.draw(p);
        for (double[] r : s.polar)
            if (r[5] > 0.5) {
                double ra = Math.toRadians(r[0]), v = r[1] / Sail.KN / max * rad;
                g.setColor(new Color(200, 60, 50));
                g.fillOval((int) (cx + Math.sin(ra) * v) - 3, (int) (cy - Math.cos(ra) * v) - 3, 6, 6);
            }
        if (s.boat.rig == Sail.J70)
            for (double[] o : ORC_J70)
                if (Math.abs(o[0] - s.twsKn) < 0.01) {
                    double ra = Math.toRadians(o[1]), v = o[2] / max * rad;
                    g.setColor(ORC);
                    g.drawRect((int) (cx + Math.sin(ra) * v) - 4, (int) (cy - Math.cos(ra) * v) - 4, 8, 8);
                }
        g.setColor(INK);
        for (double a : new double[]{s.vmg[0], s.vmg[3]}) {
            double ra = Math.toRadians(a);
            g.drawLine(cx, cy, (int) (cx + Math.sin(ra) * rad), (int) (cy - Math.cos(ra) * rad));
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString(String.format(Locale.GERMAN, "Polare %s bei %.0f kn", s.boat.rig.name(), s.twsKn), x0 + 20, y0 + 26);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(DIM);
        g.drawString(String.format(Locale.GERMAN, "Kreuz %.0f°: VMG %.2f kn · Vorwind %.0f°: VMG %.2f kn", s.vmg[0], s.vmg[1] / Sail.KN, s.vmg[3], s.vmg[4] / Sail.KN), x0 + 20, y0 + h - 40);
        g.drawString(s.boat.rig == Sail.J70 ? "□ ORC-Zertifikat J/70 (8, 12, 16 kn) · ● mit Gennaker" : s.boat.rig.downwindSail() ? "● mit Gennaker oder Spinnaker" : "", x0 + 20, y0 + h - 20);
    }

    /**
     * Seit Phase 8: ein Feld segelt die Bahn (ohne Fenster) und das Bild zeigt die Bahn von oben mit den Kursen aller
     * Boote, Tonnen, Startlinie und der Reihenfolge im Ziel.
     */
    static void regatta(String[] args) throws Exception {
        int boats = args.length > 2 ? Integer.parseInt(args[2]) : 16;
        double kn = args.length > 3 ? Double.parseDouble(args[3]) : 12;
        Sail s = new Sail();
        s.quality = Sail.Quality.MEDIUM;
        s.windSpeed = kn * Sail.KN;
        s.windFromDeg = 0;
        Regatta r = new Regatta(s, new Regatta.Course(0, 0, 0, 900, Math.max(80, boats * 8), 2), 2026);
        r.add(Sail.J70, boats);
        java.util.List<java.util.List<double[]>> tracks = new java.util.ArrayList<>();
        for (int i = 0; i < boats; i++) tracks.add(new java.util.ArrayList<>());
        java.util.List<String> log = new java.util.ArrayList<>();
        r.listener = new Regatta.Listener() {
            @Override public void onSignal(String what, double t) { log.add(String.format(Locale.GERMAN, "%4.0f s  %s", t, what)); }
            @Override public void onFinish(Regatta.Entry e, int place, double t) {
                if (place <= 5) log.add(String.format(Locale.GERMAN, "%4.0f s  %d. Nr. %d (%d:%02d, %d Wenden, %d Halsen)", t, place, e.number, (int) e.finishTime / 60, (int) e.finishTime % 60, e.tacks, e.gybes));
            }
        };
        r.start(180);
        double dt = 0.05;
        for (int i = 0; i < 4000 / dt && !r.over(); i++) {
            r.step(dt);
            if (i % 40 == 0) for (int k = 0; k < boats; k++) { Sail.Boat b = r.entries.get(k).boat; tracks.get(k).add(new double[]{b.x, b.z, r.toGun()}); }
        }
        int w = 1280, h = 900;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(16, 38, 56));
        g.fillRect(0, 0, w, h);
        double sc = (h - 120) / 1150.0, ox = 430, oz = h - 90;
        java.util.function.DoubleUnaryOperator px = x -> ox + x * sc, pz = z -> oz + z * sc;
        for (int k = 0; k < boats; k++) {
            Color c = Color.getHSBColor(k / (float) boats, 0.55f, 0.95f);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 170));
            Path2D p = new Path2D.Double();
            boolean first = true;
            for (double[] q : tracks.get(k)) {
                if (q[2] > 30) continue;
                if (first) p.moveTo(px.applyAsDouble(q[0]), pz.applyAsDouble(q[1])); else p.lineTo(px.applyAsDouble(q[0]), pz.applyAsDouble(q[1]));
                first = false;
            }
            g.setStroke(new BasicStroke(1.3f));
            g.draw(p);
        }
        g.setColor(ORC);
        String[] names = Regatta.Course.MARK_NAMES;
        java.util.List<double[]> marks = r.course.marks();
        for (int i = 0; i < marks.size(); i++) {
            double[] m = marks.get(i);
            int x = (int) px.applyAsDouble(m[0]), y = (int) pz.applyAsDouble(m[1]);
            g.fillOval(x - 5, y - 5, 10, 10);
            g.drawString(names[i], x + 8, y + 4);
        }
        double[] a = r.course.pin(), b = r.course.committee();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{5, 4}, 0));
        g.drawLine((int) px.applyAsDouble(a[0]), (int) pz.applyAsDouble(a[1]), (int) px.applyAsDouble(b[0]), (int) pz.applyAsDouble(b[1]));
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.drawString(String.format(Locale.GERMAN, "Regatta J/70 · %d Boote · %.0f kn aus Nord · Bahn 900 m auf und ab, 2 Runden", boats, kn), 20, 30);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(DIM);
        int y = 60;
        for (String l : log) { g.drawString(l, w - 430, y); y += 18; }
        y += 10;
        g.drawString(String.format(Locale.GERMAN, "%d Ausweichmanöver (Regeln 10–12), %d Berührungen", r.giveWays(), r.contacts()), w - 430, y);
        g.drawString("Wind ↓ von oben · Kurse ab 30 s vor dem Start", 20, h - 20);
        g.dispose();
        javax.imageio.ImageIO.write(im, "png", new File(args[1]));
        System.out.println("Bild: " + args[1] + " · " + r.describe());
    }

    private static void open(State s) {
        FFrame f = new FFrame("Segelboote · Polaren und Bewegung (com.dan.sail)");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) { SailDemo.paint((Graphics2D) g, s, getWidth(), getHeight()); }
        };
        view.setPreferredSize(new Dimension(1280, 720));
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(BG);
        side.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        slider(side, "Klasse", 0, CLASSES.length - 1, s.cls, v -> { s.cls = v; s.reset(); s.twa = Math.max(s.twa, 25); return CLASSES[v].name(); });
        slider(side, "Wind", 2, 25, (int) s.twsKn, v -> { s.twsKn = v; s.reset(); return v + " kn"; });
        slider(side, "Kurs zum Wind", 0, 180, (int) s.twa, v -> { s.twa = v; return v + "°"; });
        root.add(view, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setSize(1560, 800);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(33, e -> { s.step(1 / 30.0); view.repaint(); }).start();
    }

    static void slider(JPanel side, String name, int min, int max, int value, java.util.function.IntFunction<String> c) {
        JLabel l = new JLabel(name + ": " + c.apply(value));
        l.setForeground(DIM);
        FSlider sl = new FSlider(min, max, value);
        sl.addChangeListener(e -> l.setText(name + ": " + c.apply(sl.getValue())));
        side.add(l);
        side.add(sl);
    }

    private SailDemo() { }
}
