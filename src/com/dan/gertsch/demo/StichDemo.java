package com.dan.gertsch.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.gertsch.Dickicht;
import com.dan.gertsch.Druck;
import com.dan.gertsch.Kamera;
import com.dan.gertsch.Licht;
import com.dan.gertsch.Malweise;
import com.dan.gertsch.Messung;
import com.dan.gertsch.Stich;
import com.dan.gertsch.Vorlage;
import com.dan.gertsch.Vorlagen;
import com.dan.gertsch.gl.GertschGl;
import com.dan.gertsch.gl.StichGl;
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
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Demo von com.dan.gertsch, Phase 4: der Holzschnitt ({@link Stich}, auf der Grafikkarte {@link StichGl}). Links der
 * Druck, ganz (wie aus der Entfernung) oder mit der Lupe (Klick ins Bild: dorthin); rechts das Druckblatt, auf „Messen“
 * mit dem Druck gegen den gemessenen Holzschnitt. Quellen:
 * <ul>
 * <li>das Dickicht im Wind ({@link GertschGl}): sein Licht vor den Tonstufen oder das gemalte Bild,</li>
 * <li>ein beliebiges Bild, etwa eine Szene eines anderen Pakets aus {@code docs/img} („Bild öffnen …“).</li>
 * </ul>
 * Unten Tonwerte, Farbe, Ansicht, „Ruhe“ (Hysterese und Trägheit der Punkte), Wind, Punktgröße, Ordnung der Punkte
 * und Wolken.
 * <p>
 * Ohne Fenster:
 * <ul>
 * <li>{@code --bild datei.png [dickicht|gemalt|bild.png] [vorlage|linear] [wind m/s]}: ein Druck mit Lupe und
 * Druckblatt (Grafikkarte; mit {@code --cpu} auf dem Prozessor).</li>
 * <li>{@code --flimmern [wind m/s]}: 48 Bilder mit 24 B/s im Wind, je ohne und mit Hysterese und Trägheit; gibt aus,
 * wie viele gestochene Punkte je Bild wechseln.</li>
 * </ul>
 */
public final class StichDemo implements GLEventListener {
    static final Color BG = new Color(11, 17, 24);
    static final String[] FARBEN = {"Blau (gemessen)", "Schwarz", "Grün", "Rotbraun"};
    static final double[][] FARBE_LAB = {null, {20, 0.5, 1.5}, {38, -22, 8}, {34, 24, 18}};

    final GertschGl gg = new GertschGl();
    final StichGl sg = new StichGl();
    final Kamera kamera = new Kamera();
    /** Neue Szene bzw. neues Bild aus dem Hintergrund, im GL-Faden zu laden. */
    final AtomicReference<Object[]> neu = new AtomicReference<>();
    final AtomicReference<BufferedImage> neuesBild = new AtomicReference<>();
    /** 0 Dickicht (Licht), 1 Dickicht (gemalt), 2 Bild. */
    volatile int quelle;
    volatile boolean frisch = true, messenGewuenscht, statistikJedesBild;
    volatile BufferedImage gemessenBild;
    volatile double zeit = -1, lupe, mu = 0.5, mv = 0.5;
    volatile int sw = 1, sh = 1;
    int bildTex, bw, bh, bilder;
    boolean geladen;
    double bps;
    long t0 = System.nanoTime(), letzteStatistik;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        if (args.length > 0 && args[0].equals("--flimmern")) { flimmern(args); return; }
        if (args.length > 1 && args[0].equals("--bild")) { bild(args); return; }
        SwingUtilities.invokeLater(StichDemo::oeffnen);
    }

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        gg.init(gl);
        sg.init(gl);
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        Object[] s = neu.getAndSet(null);
        if (s != null) {
            gg.laden(gl, (Dickicht) s[0], (Licht) s[1]);
            geladen = true;
            frisch = true;
        }
        BufferedImage b = neuesBild.getAndSet(null);
        if (b != null) {
            if (bildTex != 0) gl.glDeleteTextures(1, new int[]{bildTex}, 0);
            bildTex = StichGl.textur(gl, b);
            bw = b.getWidth();
            bh = b.getHeight();
            frisch = true;
        }
        if (frisch) { sg.neu(); frisch = false; }
        sw = d.getSurfaceWidth();
        sh = d.getSurfaceHeight();
        int q = quelle;
        boolean bereit = q < 2 ? geladen : bildTex != 0;
        if (!bereit) {
            gl.glBindFramebuffer(GL4.GL_FRAMEBUFFER, gl.getDefaultDrawFramebuffer());
            gl.glClearColor(BG.getRed() / 255f, BG.getGreen() / 255f, BG.getBlue() / 255f, 1);
            gl.glClear(GL4.GL_COLOR_BUFFER_BIT);
            return;
        }
        double t = zeit >= 0 ? zeit : (System.nanoTime() - t0) / 1e9;
        if (q < 2) {
            gg.zeichnen(gl, kamera, t, -1, 0, 0, 0, 0);
            sg.drucken(gl, q == 0 ? gg.helligkeitTextur() : gg.farbTextur(), gg.bildBreite(), gg.bildHoehe(),
                    q == 0 ? StichGl.Eingang.HELLIGKEIT : StichGl.Eingang.FARBE, Druck.BREITE, t);
        } else sg.drucken(gl, bildTex, bw, bh, StichGl.Eingang.FARBE, Druck.BREITE);
        sg.zeigen(gl, gl.getDefaultDrawFramebuffer(), 0, 0, sw, sh, lupe, mu, mv, BG.getRGB() & 0xFFFFFF);
        bilder++;
        long jetzt = System.nanoTime();
        if (statistikJedesBild) sg.statistik(gl);
        else if (jetzt - letzteStatistik > 500_000_000L) {
            sg.statistik(gl);
            bps = bilder / ((jetzt - letzteStatistik) / 1e9);
            bilder = 0;
            letzteStatistik = jetzt;
        }
        if (messenGewuenscht) {
            messenGewuenscht = false;
            gemessenBild = sg.lesen(gl);
        }
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (bildTex != 0) gl.glDeleteTextures(1, new int[]{bildTex}, 0);
        sg.dispose(gl);
        gg.dispose(gl);
    }

    /** Dickicht und Licht im aufrufenden Faden bauen (einige Sekunden). */
    static Object[] szene(long seed) {
        Dickicht d = Dickicht.sommer();
        d.seed = seed;
        d.bauen();
        Licht l = new Licht();
        l.vorbereiten(d);
        return new Object[]{d, l};
    }

    // ------------------------------------------------------------------ ohne Fenster

    static GLOffscreenAutoDrawable offscreen(GLEventListener l) {
        GLProfile p = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(p);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, 64, 64);
        dr.addGLEventListener(l);
        return dr;
    }

    static void bild(String[] a) throws Exception {
        boolean cpu = false;
        java.util.List<String> r = new java.util.ArrayList<>();
        for (String s : a) if (s.equals("--cpu")) cpu = true; else r.add(s);
        String datei = r.get(1), was = r.size() > 2 ? r.get(2) : "dickicht";
        boolean linear = r.size() > 3 && r.get(3).equalsIgnoreCase("linear");
        double wind = r.size() > 4 ? Double.parseDouble(r.get(4)) : 0;
        BufferedImage druck;
        String quelleText;
        long t0 = System.nanoTime();
        if (cpu) {
            Stich st = new Stich();
            if (linear) st.tonwerte = Stich.Tonwerte.LINEAR;
            Stich.Ergebnis e;
            if (was.equals("dickicht") || was.equals("gemalt")) {
                Object[] s = szene(1);
                Malweise m = new Malweise();
                m.mitLicht = true;
                Malweise.Ergebnis me = m.malen((Dickicht) s[0], (Licht) s[1], new Kamera());
                e = st.stechen(was.equals("gemalt") ? me.bild : me.licht, Druck.BREITE);
                quelleText = was.equals("gemalt") ? "Dickicht gemalt (Prozessor)" : "Dickicht, Licht (Prozessor)";
            } else {
                e = st.stechen(ImageIO.read(new File(was)), Druck.BREITE);
                quelleText = new File(was).getName() + " (Prozessor)";
            }
            druck = e.bild;
            System.out.printf(Locale.GERMAN, "Prozessor: %d × %d in %.2f s, Punkt %.2f px, %,d von %,d gestochen%n", e.breite, e.hoehe, e.sekunden, e.punktPx, e.gestochen, e.punkte);
        } else {
            StichDemo demo = new StichDemo();
            if (linear) demo.sg.stich.tonwerte = Stich.Tonwerte.LINEAR;
            demo.gg.quality = GertschGl.Quality.HIGH;
            demo.gg.wind.staerke = wind;
            if (wind == 0) { demo.sg.hysterese = 0; demo.sg.traegheit = 0; }   // ein Standbild braucht keine Ruhe: gleich dem Prozessor
            if (was.equals("dickicht") || was.equals("gemalt")) {
                demo.quelle = was.equals("gemalt") ? 1 : 0;
                demo.neu.set(szene(1));
                quelleText = was.equals("gemalt") ? "Dickicht gemalt" : "Dickicht, Licht vor den Tonstufen";
            } else {
                demo.quelle = 2;
                demo.neuesBild.set(ImageIO.read(new File(was)));
                quelleText = new File(was).getName();
            }
            GLOffscreenAutoDrawable dr = offscreen(demo);
            for (int i = 0; i < 6; i++) {
                demo.zeit = 2.0 - (5 - i) / 24.0;
                dr.display();
            }
            BufferedImage[] im = new BufferedImage[1];
            dr.invoke(true, x -> { GL4 gl = x.getGL().getGL4(); demo.sg.statistik(gl); im[0] = demo.sg.lesen(gl); return true; });
            druck = im[0];
            System.out.printf(Locale.GERMAN, "Grafikkarte: Druck %d × %d in %.1f ms, %,d von %,d Punkten gestochen%n", druck.getWidth(), druck.getHeight(),
                    demo.sg.zeit(), demo.sg.gestochen, demo.sg.zellen);
            dr.destroy();
        }
        double sekunden = (System.nanoTime() - t0) / 1e9;
        Druck ist = Druck.messen(druck), soll = Vorlagen.druck();
        ist.name = "druck";
        ist.titel = "Holzschnitt " + (linear ? "(wie die Szene)" : "(Ton nach der Vorlage)");
        ist.quelle = quelleText;
        ImageIO.write(tafel(druck, ist, soll, quelleText + " · " + (linear ? "Ton wie die Szene" : "Ton nach der Vorlage")), "png", new File(datei));
        System.out.print(ist.bericht());
        System.out.print(soll.bericht());
        Vorlage v = new Messung().messen(druck), h = Vorlagen.holzschnitt();
        System.out.printf(Locale.GERMAN, "Messung wie Phase 1: Median L* %.1f (Holzschnitt %.1f), L* 5 %% %.1f (%.1f), 95 %% %.1f (%.1f), Farbton %.0f° (%.0f°), Buntheit %.1f (%.1f)%n",
                v.l50, h.l50, v.l5, h.l5, v.l95, h.l95, v.farbton, h.farbton, v.buntheit, h.buntheit);
        System.out.printf(Locale.GERMAN, "zusammen %.1f s · Bild: %s%n", sekunden, datei);
        System.exit(0);
    }

    /** Standbild: der Druck (1000 px breit), darunter zwei Ausschnitte mit der Lupe (4-fach), rechts das Druckblatt. */
    static BufferedImage tafel(BufferedImage druck, Druck ist, Druck soll, String titel) {
        int vw = 1000, vh = (int) Math.round(vw * druck.getHeight() / (double) druck.getWidth()), rand = 20, lh = 210, lw = (vw - rand) / 2;
        int w = rand + vw + rand + Druckblatt.BREITE + rand, h = Math.max(rand + 24 + vh + rand + lh + 18 + rand, Druckblatt.HOEHE + 2 * rand);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(Kennblatt.GRUND);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Kennblatt.TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("Holzschnitt nach Franz Gertsch · " + titel, rand, rand + 14);
        g.drawImage(verkleinert(druck, vw, vh), rand, rand + 24, null);
        // Ausschnitte: 4-fach, ohne Glätten, wo am meisten Papier und Farbe wechseln
        double[][] mitte = ausschnitte(druck);
        int ly = rand + 24 + vh + rand;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int k = 0; k < 2; k++) {
            int qw = lw / 4, qh = lh / 4;
            int qx = (int) Math.max(0, Math.min(druck.getWidth() - qw, mitte[k][0] * druck.getWidth() - qw / 2.0));
            int qy = (int) Math.max(0, Math.min(druck.getHeight() - qh, mitte[k][1] * druck.getHeight() - qh / 2.0));
            BufferedImage aus = druck.getSubimage(qx, qy, qw, qh);
            int lx = rand + k * (lw + rand);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(aus, lx, ly, lw, lh, null);
            g.setColor(Kennblatt.LEISE);
            g.drawString(String.format(Locale.GERMAN, "Lupe 4:1 bei %.0f %% / %.0f %%", 100 * mitte[k][0], 100 * mitte[k][1]), lx, ly + lh + 14);
        }
        Druckblatt.zeichnen(g, ist, rand + vw + rand, rand, soll);
        g.dispose();
        return im;
    }

    /**
     * Zwei Mitten für die Lupe (Anteile): die Felder eines Gitters von 12 × 8 mit der größten Streuung der Helligkeit
     * (dort wechseln Papier und Farbe am meisten), das zweite in der anderen Hälfte des Bilds.
     */
    static double[][] ausschnitte(BufferedImage b) {
        int gx = 12, gy = 8, w = b.getWidth(), h = b.getHeight();
        double[] s = new double[gx * gy], s2 = new double[gx * gy];
        int[] n = new int[gx * gy];
        for (int y = 0; y < h; y += 2)
            for (int x = 0; x < w; x += 2) {
                int c = b.getRGB(x, y), k = Math.min(gy - 1, y * gy / h) * gx + Math.min(gx - 1, x * gx / w);
                double l = ((c >> 8) & 255) / 255.0;
                s[k] += l;
                s2[k] += l * l;
                n[k]++;
            }
        int[] best = {-1, -1};
        double[] wert = {-1, -1};
        for (int k = 0; k < gx * gy; k++) {
            if (k % gx == 0 || k % gx == gx - 1 || k / gx == 0 || k / gx == gy - 1) continue;   // nicht am Rand
            double v = s2[k] / n[k] - (s[k] / n[k]) * (s[k] / n[k]);
            int haelfte = k % gx < gx / 2 ? 0 : 1;
            if (v > wert[haelfte]) { wert[haelfte] = v; best[haelfte] = k; }
        }
        double[][] m = new double[2][];
        for (int i = 0; i < 2; i++) m[i] = new double[]{(best[i] % gx + 0.5) / gx, (best[i] / gx + 0.5) / gy};
        return m;
    }

    /** Verkleinern im linearen Licht (Kastenmittel), wie man einen Druck aus der Entfernung sieht. */
    static BufferedImage verkleinert(BufferedImage b, int w, int h) {
        int bw = b.getWidth(), bh = b.getHeight();
        double[] s = new double[w * h * 3];
        int[] n = new int[w * h];
        for (int y = 0; y < bh; y++)
            for (int x = 0; x < bw; x++) {
                int t = (int) ((long) y * h / bh) * w + (int) ((long) x * w / bw), c = b.getRGB(x, y);
                s[3 * t] += com.dan.gertsch.Lab.linear(c >> 16);
                s[3 * t + 1] += com.dan.gertsch.Lab.linear(c >> 8);
                s[3 * t + 2] += com.dan.gertsch.Lab.linear(c);
                n[t]++;
            }
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int t = 0; t < w * h; t++) {
            double k = 1.0 / Math.max(1, n[t]);
            o.setRGB(t % w, t / w, (com.dan.gertsch.Lab.srgb8(s[3 * t] * k) << 16) | (com.dan.gertsch.Lab.srgb8(s[3 * t + 1] * k) << 8) | com.dan.gertsch.Lab.srgb8(s[3 * t + 2] * k));
        }
        return o;
    }

    static void flimmern(String[] a) throws Exception {
        StichDemo demo = new StichDemo();
        demo.gg.wind.staerke = a.length > 1 ? Double.parseDouble(a[1]) : 6;
        demo.gg.quality = GertschGl.Quality.MEDIUM;
        demo.quelle = 0;
        demo.neu.set(szene(1));
        GLOffscreenAutoDrawable dr = offscreen(demo);
        demo.statistikJedesBild = true;
        for (double[] ht : new double[][]{{0, 0}, {0.3, 0}, {0, 0.15}, {0.3, 0.15}, {0.3, 0.3}}) {
            double h = ht[0];
            demo.sg.hysterese = h;
            demo.sg.traegheit = ht[1];
            demo.frisch = true;
            double summe = 0, zeit = 0;
            int n = 0;
            for (int i = 0; i < 48; i++) {
                demo.zeit = 10 + i / 24.0;
                dr.display();   // liest die Zähler dieses Bilds in display()
                if (i >= 8) { summe += demo.sg.flimmern(); zeit += demo.sg.zeit(); n++; }
            }
            System.out.printf(Locale.GERMAN, "Hysterese %.2f Blenden, Trägheit %.2f s: %.2f %% der gestochenen Punkte wechseln je Bild (Wind %.0f m/s, %,d Punkte, Druck %.1f ms)%n",
                    h, ht[1], 100 * summe / n, demo.gg.wind.staerke, demo.sg.gestochen, zeit / n);
        }
        dr.destroy();
        System.exit(0);
    }

    // ------------------------------------------------------------------ Fenster

    private static void oeffnen() {
        StichDemo demo = new StichDemo();
        demo.gg.quality = GertschGl.Quality.MEDIUM;
        demo.gg.wind.staerke = 4;
        FFrame f = new FFrame("Gertsch · Phase 4 · Holzschnitt");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout(8, 6));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(demo);
        canvas.setPreferredSize(new Dimension(900, 560));

        Druck soll = Vorlagen.druck();
        Druck[] ist = {null};
        JPanel blatt = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                g0.setColor(BG);
                g0.fillRect(0, 0, getWidth(), getHeight());
                Druckblatt.zeichnen((Graphics2D) g0, ist[0] != null ? ist[0] : soll, 0, 0, ist[0] != null ? soll : null);
            }
        };
        blatt.setPreferredSize(new Dimension(Druckblatt.BREITE, Druckblatt.HOEHE));
        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(BG);
        east.add(blatt, BorderLayout.NORTH);

        FComboBox quelle = new FComboBox(new Object[]{"Dickicht im Wind: Licht", "Dickicht im Wind: gemalt", "Bild: forest.png", "Bild öffnen …"});
        FComboBox ton = new FComboBox(new Object[]{"Ton nach der Vorlage", "Ton wie die Szene"});
        FComboBox farbe = new FComboBox(FARBEN);
        FComboBox ansicht = new FComboBox(new Object[]{"ganz", "Lupe 1:1", "Lupe 2:1", "Lupe 4:1", "Lupe 8:1"});
        FCheckBox hyst = new FCheckBox("Ruhe");   // Hysterese und Trägheit der Punkte
        hyst.setSelected(true);
        FButton messen = new FButton("Messen"), wuerfeln = new FButton("Neu würfeln");
        FSlider wind = new FSlider(0, 150, 40), punkt = new FSlider(50, 400, 100), ordnung = new FSlider(0, 100, (int) Math.round(100 * demo.sg.stich.punkte.ordnung)),
                wolken = new FSlider(0, 300, 100);
        JLabel lWind = label(""), lPunkt = label(""), lOrdnung = label(""), lWolken = label(""), status = label("baut das Dickicht …");
        status.setForeground(Kennblatt.LEISE);
        double wolken0 = demo.sg.stich.wolken;
        Runnable texte = () -> {
            lWind.setText(String.format(Locale.GERMAN, "Wind %.1f m/s", wind.getValue() / 10.0));
            lPunkt.setText(String.format(Locale.GERMAN, "Punkt %.2f px", soll.punkt * Druck.BREITE * punkt.getValue() / 100.0));
            lOrdnung.setText("Ordnung " + ordnung.getValue() + " %");
            lWolken.setText("Wolken " + wolken.getValue() + " %");
        };
        texte.run();
        long[] seed = {1};
        Runnable neueSzene = () -> {
            status.setText("baut das Dickicht …");
            long s = seed[0];
            new Thread(() -> demo.neu.set(szene(s)), "gertsch-szene").start();
        };
        File[] letzterOrdner = {new File("docs/img")};
        quelle.addActionListener(e -> {
            int i = quelle.getSelectedIndex();
            if (i < 2) { demo.quelle = i; demo.frisch = true; return; }
            File d = i == 2 ? new File(letzterOrdner[0], "forest.png") : null;
            if (i == 3) {
                JFileChooser fc = new JFileChooser(letzterOrdner[0]);
                fc.setFileFilter(new FileNameExtensionFilter("Bilder (PNG, JPEG)", "png", "jpg", "jpeg"));
                if (fc.showOpenDialog(f) != JFileChooser.APPROVE_OPTION) return;
                d = fc.getSelectedFile();
                letzterOrdner[0] = d.getParentFile();
            }
            try {
                BufferedImage b = ImageIO.read(d);
                if (b == null) throw new IOException("kein Bild");
                demo.neuesBild.set(b);
                demo.quelle = 2;
                ton.setSelectedIndex(1);   // eine fremde Szene mit ihrem eigenen Licht
            } catch (IOException ex) {
                status.setText(d + ": " + ex.getMessage());
            }
        });
        ton.addActionListener(e -> { demo.sg.stich.tonwerte = ton.getSelectedIndex() == 0 ? Stich.Tonwerte.VORLAGE : Stich.Tonwerte.LINEAR; demo.frisch = true; });
        farbe.addActionListener(e -> demo.sg.stich.farbe = FARBE_LAB[farbe.getSelectedIndex()]);
        ansicht.addActionListener(e -> demo.lupe = new double[]{0, 1, 2, 4, 8}[ansicht.getSelectedIndex()]);
        hyst.addActionListener(e -> { demo.sg.hysterese = hyst.isSelected() ? 0.3 : 0; demo.sg.traegheit = hyst.isSelected() ? 0.15 : 0; });
        wind.addChangeListener(e -> { texte.run(); demo.gg.wind.staerke = wind.getValue() / 10.0; });
        punkt.addChangeListener(e -> { texte.run(); demo.sg.stich.punkt = soll.punkt * punkt.getValue() / 100.0; });
        ordnung.addChangeListener(e -> { texte.run(); demo.sg.stich.punkte.ordnung = ordnung.getValue() / 100.0; demo.frisch = true; });
        wolken.addChangeListener(e -> { texte.run(); demo.sg.stich.wolken = wolken0 * wolken.getValue() / 100.0; });
        wuerfeln.addActionListener(e -> {
            seed[0]++;
            demo.sg.stich.seed = (int) seed[0];
            demo.sg.stich.punkte.seed = (int) seed[0];
            demo.frisch = true;
            if (demo.quelle < 2) neueSzene.run();
        });
        messen.addActionListener(e -> {
            demo.gemessenBild = null;
            demo.messenGewuenscht = true;
            new Thread(() -> {
                while (demo.gemessenBild == null) Thread.onSpinWait();
                Druck d = Druck.messen(demo.gemessenBild);
                d.name = "druck";
                d.titel = "Druck (" + quelle.getSelectedItem() + ")";
                SwingUtilities.invokeLater(() -> { ist[0] = d; blatt.repaint(); });
            }, "gertsch-messen").start();
        });
        // Klick ins Bild: im Ganzen dorthin mit der Lupe 4:1, mit der Lupe den Ausschnitt dorthin schieben
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                double k = demo.sw / (double) Math.max(1, canvas.getWidth());
                double mx = e.getX() * k, my = e.getY() * k;
                int pw = demo.sg.druckBreite(), ph = demo.sg.druckHoehe();
                if (pw == 0) return;
                if (demo.lupe <= 0) {
                    double s = Math.min(demo.sw / (double) pw, demo.sh / (double) ph), dw = pw * s, dh = ph * s;
                    demo.mu = (mx - (demo.sw - dw) / 2) / dw;
                    demo.mv = (my - (demo.sh - dh) / 2) / dh;
                    ansicht.setSelectedIndex(3);
                } else {
                    double l = demo.lupe;
                    demo.mu = Math.max(0, Math.min(1, demo.mu + (mx - demo.sw / 2.0) / (l * pw)));
                    demo.mv = Math.max(0, Math.min(1, demo.mv + (my - demo.sh / 2.0) / (l * ph)));
                }
            }
        });
        new javax.swing.Timer(500, e -> {
            if (demo.quelle < 2 && !demo.geladen) return;
            double[] z = demo.gg.zeiten();
            double dick = 0;
            for (double v : z) dick += v;
            status.setText(String.format(Locale.GERMAN, "%.0f B/s · %s Druck %.1f ms · %,d Punkte gestochen · %.1f %% wechseln je Bild",
                    demo.bps, demo.quelle < 2 ? String.format(Locale.GERMAN, "Dickicht %.0f ms ·", dick) : "", demo.sg.zeit(), demo.sg.gestochen, 100 * demo.sg.flimmern()));
        }).start();

        JPanel zeile1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2)), zeile2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        zeile1.setBackground(BG);
        zeile2.setBackground(BG);
        zeile1.add(label("Quelle"));
        zeile1.add(quelle);
        zeile1.add(ton);
        zeile1.add(label("Farbe"));
        zeile1.add(farbe);
        zeile1.add(ansicht);
        zeile1.add(hyst);
        zeile1.add(messen);
        zeile1.add(wuerfeln);
        zeile2.add(lWind);
        zeile2.add(wind);
        zeile2.add(lPunkt);
        zeile2.add(punkt);
        zeile2.add(lOrdnung);
        zeile2.add(ordnung);
        zeile2.add(lWolken);
        zeile2.add(wolken);
        JPanel zeile3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        zeile3.setBackground(BG);
        zeile3.add(status);   // Bilder je Sekunde, Zeit der Grafikkarte, gestochene Punkte, Wechsel
        JPanel south = new JPanel(new GridLayout(3, 1, 0, 2));
        south.setBackground(BG);
        south.add(zeile1);
        south.add(zeile2);
        south.add(zeile3);
        for (FSlider s : new FSlider[]{wind, punkt, ordnung, wolken}) s.setPreferredSize(new Dimension(150, s.getPreferredSize().height));
        root.add(canvas, BorderLayout.CENTER);
        root.add(east, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        // deckend: ein GLCanvas in einem durchsichtigen Fenster bleibt unter Windows leer (wie in den anderen Demos)
        f.setBackground(BG);
        f.setPreferredFrameSize(new Dimension(1440, 800));
        f.setVisible(true);
        new Animator(canvas).start();
        neueSzene.run();
    }

    static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(Kennblatt.TEXT);
        return l;
    }
}
