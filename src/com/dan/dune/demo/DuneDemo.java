package com.dan.dune.demo;

import com.dan.dune.Dune;
import com.dan.dune.FluxField;
import com.dan.dune.Grains;
import com.dan.dune.Ripples;
import com.dan.dune.SandBed;
import com.dan.dune.TimeLapse;
import com.dan.dune.Werner;
import com.dan.dune.gl.DuneGl;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorführung von Flugsand ohne Sylt-Code (Paketvertrag Punkt 3 und 10), Stand Sylt Phase 8: der Strand der
 * {@link Strand}-Bühne mit nassem Auflauf, trockenem oberem Strand und einer Vordüne mit Strandhafer und Windrissen.
 * Sandfahnen ziehen über den Strand, über dem Kamm weht die Kammfahne in den Windschatten, Windrippel wandern.
 * <ul>
 * <li>{@code DuneDemo}: Fenster; ziehen dreht den Blick, Rad nähert; Tasten 1 Strand, 2 Kamm, 3 Übersicht; Regler für
 * Wind, Richtung, Regen und Böen.</li>
 * <li>{@code DuneDemo --bild datei.png [breite]}: drei Blicke (Strand Bft 6, Kamm Bft 8, Übersicht Bft 7) und das Profil.</li>
 * <li>{@code DuneDemo --profil datei.png}: nur das Profil (ohne Grafikkarte): Boden, u* und Schwelle, Fluss und Ablagerung
 * durch einen Windriss, darunter der Fluss von oben.</li>
 * <li>Seit Phase 9: Taste Z startet den Zeitraffer (Plattenmodell im eigenen Faden, Regler „Jahre/s“), F setzt
 * Sandfangzäune, R legt das Sandbett neu an. {@code DuneDemo --zeitraffer datei.png [jahre]} zeichnet ihn ohne
 * Grafikkarte: Profile je Jahr durch Windriss und Strandhafer, darunter Gewinn und Verlust von oben.</li>
 * </ul>
 */
public final class DuneDemo implements GLEventListener {
    final Strand strand = new Strand();
    Dune dune;
    FluxField field;
    Ripples ripples;
    Grains grains;
    final DuneGl gl = new DuneGl();
    volatile boolean dirty = true;
    volatile double windSpeed = 12, windFrom = 270, rain = 0, gust = 0.22, speed = 1;
    /** Blick: Ziel, Gieren (Grad, 0 = nach Ost), Neigung, Abstand. */
    double tx = 60, ty = 1.6, tz = 4, yaw = 200, pitch = 14, dist = 9;
    int w = 1, h = 1;
    double t;
    long last;
    double cpuMs, gpuMs;
    private final int[] query = new int[2];

    /** Seit Phase 9: das Sandbett der Bühne, das Plattenmodell und der Zeitraffer im eigenen Faden (Wind aus West). */
    SandBed bed;
    Werner werner;
    TimeLapse lapse;
    volatile double yearsPerSecond = 0.3;
    private volatile boolean reflowing;
    private long lastReflow;

    DuneDemo() { bedInit(); rebuild(); }

    /** Sandbett neu: Bühne mit Strandhafer, Wind wie eingestellt (aus West, F12 A: der Zeitraffer rechnet in Windrichtung). */
    void bedInit() {
        if (lapse != null) lapse.stop();
        Dune d = strand.wire(Dune.sylt());
        d.windSpeed = Math.max(windSpeed, 9);
        bed = strand.bed(d, 1.0);
        werner = new Werner(bed);
        werner.relax();
        lapse = new TimeLapse(werner);
        lapse.yearsPerSecond = yearsPerSecond;
    }

    /** Wind des Zeitraffers neu (das Sandbett bleibt). */
    void lapseWind() {
        boolean on = lapse.running();
        lapse.stop();
        bed.dune.windSpeed = Math.max(windSpeed, 9);
        Werner w = new Werner(bed);
        w.relax();
        werner = w;
        lapse = new TimeLapse(w);
        lapse.yearsPerSecond = yearsPerSecond;
        if (on) lapse.start();
    }

    /** Zäune setzen: zwei Reihen aus Busch am Dünenfuß vor dem Windriss (1 m hoch, halb durchlässig). */
    void fences() {
        synchronized (werner) {
            bed.fence(new SandBed.Fence(80, 8, 80, 50, 1.0, 0.5));
            bed.fence(new SandBed.Fence(87, 8, 87, 50, 1.0, 0.5));
        }
    }

    /** Feld neu, wenn Wind oder Regen sich ändern; Rippel wachsen mit (alte Rippel bleiben liegen). */
    void rebuild() {
        Dune d = Dune.sylt();
        d.windSpeed = windSpeed;
        d.windFromDeg = windFrom;
        d.gustiness = gust;
        strand.rain = rain;
        strand.wire(d);
        // seit Phase 9: Boden und Strandhafer aus dem Sandbett (letzte Kopie des Zeitraffers)
        d.ground = lapse.ground();
        d.cover = lapse.cover();
        FluxField f = strand.field(d, 0.5);
        Ripples r = new Ripples(f);
        if (ripples != null && ripples.lambda.length == r.lambda.length) {
            System.arraycopy(ripples.lambda, 0, r.lambda, 0, r.lambda.length);
            System.arraycopy(ripples.height, 0, r.height, 0, r.height.length);
        } else r.settle();
        Grains g = new Grains(d);
        if (grains != null) { g.weight = grains.weight; }
        g.radius = 30;
        dune = d;
        field = f;
        ripples = r;
        grains = g;
        dirty = false;
    }

    /** Rippel und Körner vorlaufen lassen (für Standbilder). */
    void advance(double seconds) {
        ripples.settle();
        grains.radius = Math.max(8, Math.min(30, dist * 1.2));
        for (double s = 0; s < seconds; s += 1 / 60.0) { t += 1 / 60.0; grains.step(1 / 60.0, field, tx, tz); }
    }

    void view(int k) {
        switch (k) {
            case 1 -> { tx = 60; ty = 1.6; tz = 4; yaw = 200; pitch = 14; dist = 9; }
            case 2 -> { tx = strand.crest(24) + 4; tz = 24; ty = dune.ground.height(tx - 4, tz) + 0.3; yaw = 95; pitch = 6; dist = 13; }
            default -> { tx = 95; tz = 10; ty = 3; yaw = 215; pitch = 20; dist = 48; }
        }
    }

    double[] eye() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double ex = tx + dist * Math.cos(pr) * Math.cos(yr), ez = tz - dist * Math.cos(pr) * Math.sin(yr), ey = ty + dist * Math.sin(pr);
        ey = Math.max(ey, dune.ground.height(ex, ez) + 0.3);
        return new double[]{ex, ey, ez};
    }

    @Override public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        g.glGenQueries(2, query, 0);
        last = System.nanoTime();
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { w = Math.max(1, ww); h = Math.max(1, hh); }

    @Override public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - last) / 1e9) * speed;
        last = now;
        if (dirty) rebuild();
        long c0 = System.nanoTime();
        if (dt > 0) {
            t += dt;
            // Zeitraffer: Feld alle 0,8 s im Hintergrund auf die neue Oberfläche rechnen
            if (lapse.running() && !reflowing && now - lastReflow > 800_000_000L) {
                reflowing = true;
                lastReflow = now;
                Thread th = new Thread(() -> { try { rebuild(); } finally { reflowing = false; } }, "dune-feld");
                th.setDaemon(true);
                th.start();
            }
            grains.radius = Math.max(8, Math.min(30, dist * 1.2));
            grains.step(dt, field, tx, tz);
            ripples.step(dt);
        }
        cpuMs = (System.nanoTime() - c0) / 1e6;
        w = Math.max(1, d.getSurfaceWidth());
        h = Math.max(1, d.getSurfaceHeight());
        g.glViewport(0, 0, w, h);
        DuneGl.Light l = light();
        g.glClearColor((float) disp(l.fogColor[0]), (float) disp(l.fogColor[1]), (float) disp(l.fogColor[2]), 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_LEQUAL);
        DuneGl.View v = new DuneGl.View();
        double[] e = eye();
        v.eye = e;
        v.viewProj = mul(perspective(55, w / (double) h, 0.05, 2000), lookAt(e[0], e[1], e[2], tx, ty, tz));
        g.glBeginQuery(GL4.GL_TIME_ELAPSED, query[0]);
        gl.draw(g, v, l, field, ripples, grains, t);
        gl.drawFences(g, v, l, bed);
        g.glEndQuery(GL4.GL_TIME_ELAPSED);
        int[] r = new int[1];
        g.glGetQueryObjectiv(query[0], GL4.GL_QUERY_RESULT, r, 0);
        gpuMs = r[0] / 1e6;
    }

    static double disp(double c) { return Math.pow(1 - Math.exp(-c * 0.55), 1 / 2.2); }

    static DuneGl.Light light() {
        DuneGl.Light l = new DuneGl.Light();
        l.sunDir = new double[]{-0.45, 0.42, 0.55};   // Nachmittag, Sonne im Südwesten: Rippel im Streiflicht
        l.display = 0.55;
        l.fogDistance = 900;
        return l;
    }

    @Override public void dispose(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.dispose(g);
        g.glDeleteQueries(2, query, 0);
    }

    String status() {
        return String.format(Locale.GERMAN, "%s · %s · %s · Körner %.1f ms (Prozessor), Zeichnen %.2f ms (Grafikkarte)<br>%s · %s · %s",
                field.describe(), grains.describe(), ripples.describe(), cpuMs, gpuMs, lapse.describe(), bed.describe(), werner.describe());
    }

    // ------------------------------------------------------------------ Bild

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--profil")) {
            DuneDemo s = new DuneDemo();
            s.windSpeed = 14;
            s.rebuild();
            javax.imageio.ImageIO.write(profile(s, 1600, 900), "png", new File(args[1]));
            System.out.println(s.field.describe());
            System.out.println("Bild: " + args[1]);
            return;
        }
        if (args.length > 1 && args[0].equals("--zeitraffer")) {
            DuneDemo s = new DuneDemo();
            s.windSpeed = 14;
            s.bedInit();
            BufferedImage im = zeitraffer(s, 1600, 1100, args.length > 2 ? Double.parseDouble(args[2]) : 10);
            javax.imageio.ImageIO.write(im, "png", new File(args[1]));
            System.out.println(s.bed.describe());
            System.out.println(s.werner.describe());
            System.out.println("Bild: " + args[1]);
            return;
        }
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        if (relaunch(args)) return;
        SwingUtilities.invokeLater(DuneDemo::open);
    }

    static void bild(File out, int width) throws Exception {
        int tw = width / 2, th = tw * 9 / 16;
        BufferedImage img = new BufferedImage(tw * 2, th * 2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        DuneDemo s = new DuneDemo();
        s.speed = 0;
        d.addGLEventListener(s);
        Graphics2D g = img.createGraphics();
        double[] bft = {11.5, 19.5, 15};
        String[] name = {"Strand, Bft 6: Sandfahnen über trockenem Sand, Rippel", "Kamm im Windriss, Bft 8: Kammfahne ins Lee", "Nach 8 Jahren Zeitraffer, mit Zäunen, Bft 7"};
        StringBuilder info = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            s.windSpeed = bft[i];
            if (i == 2) { s.fences(); s.lapse.advance(8); }
            s.ripples = null;
            s.rebuild();
            s.view(i + 1);
            s.advance(4);
            for (int f = 0; f < 3; f++) d.display();
            final BufferedImage[] shot = new BufferedImage[1];
            d.invoke(true, a -> { shot[0] = grab(a, tw, th); return true; });
            int x = (i % 2) * tw, y = (i / 2) * th;
            g.drawImage(shot[0], x, y, null);
            label(g, name[i] + String.format(Locale.GERMAN, " · %.1f m/s in 10 m", bft[i]), x, y);
            info.append(s.status().replace("<br>", "\n")).append('\n');
        }
        s.windSpeed = 14;
        s.ripples = null;
        s.rebuild();
        g.drawImage(profile(s, tw, th), tw, th, null);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.print(info);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    /**
     * Seit Phase 9: der Zeitraffer ohne Grafikkarte. Oben zwei Profile in Windrichtung, je Jahr eine Linie (hell = spät):
     * durch den Windriss (z = 28: die kahle Düne wandert ins Land) und durch den Strandhafer (z = −28: die Vordüne wächst
     * in die Höhe, die Halme wachsen durch, grün nach ihrer Bedeckung und so hoch, wie sie herausragen). Unten von oben:
     * Sand gewonnen (rot) und verloren (blau) nach den Jahren, Strandhafer grün, Zäune schwarz.
     */
    static BufferedImage zeitraffer(DuneDemo s, int w, int h, double years) {
        s.fences();
        SandBed b = s.bed;
        long s0 = b.total();
        int[] start = b.sand.clone();
        int n = (int) Math.ceil(years);
        double[][] profA = new double[n + 1][], profB = new double[n + 1][];
        float[][] vegB = new float[n + 1][], burB = new float[n + 1][];
        double x0 = 20, x1 = 165, step = 0.5;
        int np = (int) ((x1 - x0) / step) + 1;
        for (int y = 0; y <= n; y++) {
            if (y > 0) s.lapse.advance(years / n);
            profA[y] = new double[np];
            profB[y] = new double[np];
            vegB[y] = new float[np];
            burB[y] = new float[np];
            for (int q = 0; q < np; q++) {
                double x = x0 + q * step;
                profA[y][q] = b.height(x, 28);
                profB[y][q] = b.height(x, -28);
                vegB[y][q] = (float) b.vegAt(x, -28);
                burB[y][q] = (float) b.burialAt(x, -28);
            }
        }
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(14, 20, 22));
        g.fillRect(0, 0, w, h);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        int l = 60, r = w - 20, ph = (int) (h * 0.27);
        String[] title = {"Windriss (z = 28 m): die kahle Düne wandert ins Land", "Strandhafer (z = −28 m): die Vordüne wächst, die Halme wachsen durch"};
        for (int pan = 0; pan < 2; pan++) {
            double[][] pr = pan == 0 ? profA : profB;
            int top = 40 + pan * (ph + 50), bot = top + ph;
            double hMin = -1, hMax = 16;
            g.setColor(new Color(233, 236, 234));
            g.drawString(title[pan] + String.format(Locale.GERMAN, " · je Jahr eine Linie, %.0f Jahre", years), l, top - 10);
            g.setColor(new Color(40, 50, 54));
            g.drawRect(l, top, r - l, ph);
            // Sand des letzten Jahrs gefüllt
            Path2D fill = new Path2D.Double();
            fill.moveTo(l, bot);
            for (int q = 0; q < np; q++) fill.lineTo(l + (r - l) * q / (double) (np - 1), bot - ph * (pr[n][q] - hMin) / (hMax - hMin));
            fill.lineTo(r, bot);
            fill.closePath();
            g.setColor(new Color(70, 60, 44));
            g.fill(fill);
            for (int y = 0; y <= n; y++) {
                Path2D p = new Path2D.Double();
                for (int q = 0; q < np; q++) {
                    double xx = l + (r - l) * q / (double) (np - 1), yy = bot - ph * (pr[y][q] - hMin) / (hMax - hMin);
                    if (q == 0) p.moveTo(xx, yy); else p.lineTo(xx, yy);
                }
                float f = y / (float) n;
                g.setColor(new Color(0.35f + 0.6f * f, 0.30f + 0.5f * f, 0.20f + 0.35f * f));
                g.setStroke(new BasicStroke(y == 0 || y == n ? 2f : 1f));
                g.draw(p);
            }
            if (pan == 1)
                for (int q = 0; q < np; q += 2) {
                    if (vegB[n][q] < 0.1) continue;
                    double xx = l + (r - l) * q / (double) (np - 1), yy = bot - ph * (pr[n][q] - hMin) / (hMax - hMin);
                    double ex = Math.max(0, 0.8 - burB[n][q]) * 2.5;   // Halme 2,5-fach überhöht
                    g.setColor(new Color(0.35f, 0.55f + 0.3f * vegB[n][q], 0.30f));
                    g.setStroke(new BasicStroke(1.4f));
                    g.drawLine((int) xx, (int) yy, (int) (xx + 2), (int) (yy - ph * ex / (hMax - hMin)));
                }
            g.setStroke(new BasicStroke(1));
            g.setColor(new Color(147, 160, 162));
            g.drawString("Wasser", l + 4, bot - 6);
            g.drawString("Wind aus West →", r - 120, top + 16);
        }
        // von oben
        int top = 40 + 2 * (ph + 50), bot = h - 30;
        int mh = bot - top;
        for (int j = 0; j < mh; j++)
            for (int i = l; i < r; i++) {
                double x = x0 + (x1 - x0) * (i - l) / (double) (r - l), z = -60 + 120.0 * j / mh;
                int k = b.nearest(x, z);
                if (k < 0) continue;
                double dz = (b.sand[k] - start[k]) * b.slab, v = b.veg[k];
                double red = Math.max(0, Math.min(1, dz / 3)), blue = Math.max(0, Math.min(1, -dz / 3));
                int rr = (int) (60 + 180 * red), gg = (int) (55 + 60 * red + 40 * blue), bb = (int) (50 + 190 * blue);
                if (v > 0.1) { gg = Math.min(255, gg + (int) (90 * v)); rr = (int) (rr * 0.8); }
                if (!Float.isNaN(b.fenceTop[k])) rr = gg = bb = 10;
                im.setRGB(i, top + j, (rr << 16) | (gg << 8) | bb);
            }
        g.setColor(new Color(233, 236, 234));
        g.drawString(String.format(Locale.GERMAN, "Von oben nach %.0f Jahren: Sand gewonnen rot, verloren blau (bis 3 m), Strandhafer grün, Zäune schwarz · Sand erhalten: Abweichung %d Platten (%,d herein, %,d hinaus)",
                years, b.balance(s0), b.inflow, b.outflow), l, top - 10);
        g.dispose();
        return im;
    }

    /** Profil durch den Windriss (z = 28) und der Fluss von oben, ohne Grafikkarte. */
    static BufferedImage profile(DuneDemo s, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(14, 20, 22));
        g.fillRect(0, 0, w, h);
        FluxField f = s.field;
        Dune d = s.dune;
        double zc = 28, x0 = -5, x1 = 165;
        int l = 60, r = w - 20, top = 40, mid = (int) (h * 0.62), bot = h - 30;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, w / 110)));
        // oben: Boden (Sand), u* (weiß) und Schwelle (rot), Fluss (gold) und Sättigung (gestrichelt), Ablagerung (blau/rot)
        int pb = (top + mid) / 2;
        double hMax = 10, uMax = 1.0, qMax = 0.08;
        Path2D sand = new Path2D.Double();
        sand.moveTo(l, mid);
        for (double x = x0; x <= x1; x += 0.25) sand.lineTo(px(x, x0, x1, l, r), mid - (mid - top) * (d.ground.height(x, zc) + 0.5) / (hMax + 0.5));
        sand.lineTo(r, mid);
        sand.closePath();
        g.setColor(new Color(92, 80, 60));
        g.fill(sand);
        curve(g, s, x -> f.thresholdAt(x, zc) / uMax, new Color(230, 90, 70), x0, x1, l, r, top, mid, false);
        curve(g, s, x -> f.ustarAt(x, zc) / uMax, new Color(235, 238, 236), x0, x1, l, r, top, mid, false);
        curve(g, s, x -> f.saturatedAt(x, zc) / qMax, new Color(232, 178, 92, 150), x0, x1, l, r, top, mid, true);
        curve(g, s, x -> f.fluxAt(x, zc) / qMax, new Color(232, 178, 92), x0, x1, l, r, top, mid, false);
        g.setColor(new Color(233, 236, 234));
        g.drawString(String.format(Locale.GERMAN, "Profil durch den Windriss bei z = 28 m, Wind %.0f m/s aus %03.0f°: Boden, u* (weiß) und Schwelle u*t (rot) bis %.1f m/s, Fluss q (gold) und Sättigung (blass) bis %.2f kg/(m·s)",
                d.windSpeed, d.windFromDeg, uMax, qMax), l, top - 14);
        g.setColor(new Color(147, 160, 162));
        g.drawString("Wasser", l + 2, mid - 6);
        g.drawString("nasser Auflauf", px(8, x0, x1, l, r), mid - 22);
        g.drawString("Vordüne mit Windschatten", px(s.strand.crest(zc) - 10, x0, x1, l, r), top + 16);
        // unten: von oben, Fluss als Helligkeit, Windschatten dunkel, Bewuchs grün, Nässe blau
        int ny = bot - mid - 20;
        for (int j = 0; j < ny; j++)
            for (int i = l; i < r; i++) {
                double x = x0 + (x1 - x0) * (i - l) / (double) (r - l), z = -60 + 120.0 * j / ny;
                double qq = f.fluxAt(x, z), dep = f.depositRateAt(x, z) * 3.6e6;
                if (Double.isNaN(qq)) continue;
                double m = d.moisture.moisture(x, z), cov = d.cover.lambda(x, z);
                double v = Math.min(1, qq / qMax);
                int rr = (int) (40 + 190 * v), gg = (int) (34 + 150 * v), bb = (int) (28 + 70 * v);
                if (f.shadowAt(x, z)) { rr = 30; gg = 34; bb = 46; }
                if (m > 0.05) { rr = 40; gg = 60; bb = 80; }
                if (cov > 0.01) { gg = Math.min(255, gg + 50); rr = Math.max(0, rr - 10); }
                if (dep > 20) { rr = Math.min(255, rr + 60); }
                im.setRGB(i, mid + 20 + j, (rr << 16) | (gg << 8) | bb);
            }
        g.setColor(new Color(233, 236, 234));
        g.drawString("Von oben (120 m quer): hell = Fluss, rötlich = Ablagerung über 20 mm/h, dunkel = Windschatten, blau = nass, grün = Strandhafer", l, mid + 14);
        g.setColor(new Color(60, 70, 72));
        g.drawRect(l, top, r - l, mid - top);
        g.dispose();
        return im;
    }

    interface Fn { double at(double x); }

    static int px(double x, double x0, double x1, int l, int r) { return (int) (l + (r - l) * (x - x0) / (x1 - x0)); }

    static void curve(Graphics2D g, DuneDemo s, Fn fn, Color c, double x0, double x1, int l, int r, int top, int bot, boolean dashed) {
        Path2D p = new Path2D.Double();
        boolean first = true;
        for (double x = x0; x <= x1; x += 0.25) {
            double v = fn.at(x);
            if (Double.isNaN(v)) continue;
            double y = bot - (bot - top) * Math.min(1.2, v);
            if (first) p.moveTo(px(x, x0, x1, l, r), y); else p.lineTo(px(x, x0, x1, l, r), y);
            first = false;
        }
        g.setColor(c);
        g.setStroke(dashed ? new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{6, 5}, 0) : new BasicStroke(2f));
        g.draw(p);
        g.setStroke(new BasicStroke(1));
    }

    static void label(Graphics2D g, String t, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRect(x + 6, y + 6, g.getFontMetrics().stringWidth(t) + 12, 22);
        g.setColor(Color.WHITE);
        g.drawString(t, x + 12, y + 22);
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

    /** Wie SurfBreakers: unter Windows braucht die GLCanvas {@code --add-opens} für sun.awt; fehlt es, Neustart mit den Schaltern. */
    static boolean relaunch(String[] args) throws Exception {
        Module desk = ModuleLayer.boot().findModule("java.desktop").orElse(null);
        if (desk == null || desk.isOpen("sun.awt", DuneDemo.class.getModule()) || Boolean.getBoolean("dune.relaunched")) return false;
        String exe = ProcessHandle.current().info().command().orElse("java");
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.Arrays.asList(exe, "-Ddune.relaunched=true",
                "--enable-native-access=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.java2d=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"), DuneDemo.class.getName()));
        cmd.addAll(java.util.Arrays.asList(args));
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        System.exit(p.waitFor());
        return true;
    }

    private static void open() {
        DuneDemo s = new DuneDemo();
        GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
        caps.setDoubleBuffered(true);
        caps.setDepthBits(24);
        caps.setHardwareAccelerated(true);
        GLCanvas canvas = new GLCanvas(caps);
        canvas.addGLEventListener(s);
        JPanel view = new JPanel(new BorderLayout());
        view.add(canvas, BorderLayout.CENTER);
        FFrame f = new FFrame("Dune · Flugsand am Strand");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        Color bg = new Color(14, 20, 22), ink = new Color(233, 236, 234);
        f.setBackground(bg);
        root.setBackground(bg);
        JLabel state = new JLabel(" ");
        state.setForeground(ink);
        FSlider wind = new FSlider(0, 30, 12), dir = new FSlider(200, 340, 270), rain = new FSlider(0, 60, 0), gust = new FSlider(0, 50, 22), sp = new FSlider(0, 100, 100),
                yrs = new FSlider(1, 30, 3);
        wind.addChangeListener(e -> { s.windSpeed = wind.getValue(); s.dirty = true; if (!wind.getValueIsAdjusting()) s.lapseWind(); });
        yrs.addChangeListener(e -> { s.yearsPerSecond = yrs.getValue() / 10.0; s.lapse.yearsPerSecond = s.yearsPerSecond; });
        dir.addChangeListener(e -> { s.windFrom = dir.getValue(); s.ripples = null; s.dirty = true; });
        rain.addChangeListener(e -> { s.rain = rain.getValue() / 1000.0; s.dirty = true; });
        gust.addChangeListener(e -> { s.gust = gust.getValue() / 100.0; s.dirty = true; });
        sp.addChangeListener(e -> s.speed = sp.getValue() / 100.0);
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(bg);
        JPanel sl = new JPanel(new java.awt.GridLayout(1, 12, 6, 0));
        sl.setBackground(bg);
        String[] names = {"Wind m/s", "aus Grad", "Regen ‰", "Böen %", "Zeitlupe", "Jahre/s ×10"};
        FSlider[] all = {wind, dir, rain, gust, sp, yrs};
        for (int i = 0; i < all.length; i++) {
            JLabel lb = new JLabel(names[i]);
            lb.setForeground(ink);
            sl.add(lb);
            sl.add(all[i]);
        }
        south.add(sl, BorderLayout.NORTH);
        south.add(state, BorderLayout.CENTER);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1500, 900);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        int[] drag = new int[2];
        canvas.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { drag[0] = e.getX(); drag[1] = e.getY(); canvas.requestFocusInWindow(); }
        });
        canvas.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                s.yaw -= (e.getX() - drag[0]) * 0.3;
                s.pitch = Math.max(2, Math.min(85, s.pitch + (e.getY() - drag[1]) * 0.3));
                drag[0] = e.getX();
                drag[1] = e.getY();
            }
        });
        canvas.addMouseWheelListener(e -> s.dist = Math.max(1.5, Math.min(200, s.dist * Math.pow(1.12, e.getWheelRotation()))));
        canvas.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                char c = e.getKeyChar();
                if (c >= '1' && c <= '3') s.view(c - '0');
                // seit Phase 9: Zeitraffer an und aus, Zäune setzen, Sandbett neu
                if (c == 'z' || c == 'Z') { if (s.lapse.running()) s.lapse.stop(); else s.lapse.start(); }
                if (c == 'f' || c == 'F') s.fences();
                if (c == 'r' || c == 'R') { s.bedInit(); s.dirty = true; }
            }
        });
        new Animator(canvas).start();
        new Timer(300, e -> state.setText("<html>" + s.status() + "<br>Ziehen: Blick · Rad: Abstand · 1 Strand · 2 Kamm im Windriss · 3 Übersicht · Z Zeitraffer · F Zäune · R Sandbett neu</html>")).start();
    }

    // ------------------------------------------------------------------ Matrizen (Spalten zuerst)

    static float[] perspective(double fovy, double aspect, double n, double f) {
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
}
