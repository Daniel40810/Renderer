package com.dan.gertsch.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.gertsch.Dickicht;
import com.dan.gertsch.Kamera;
import com.dan.gertsch.Licht;
import com.dan.gertsch.Messung;
import com.dan.gertsch.Vorlage;
import com.dan.gertsch.Vorlagen;
import com.dan.gertsch.gl.GertschGl;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Demo von com.dan.gertsch, Phase 3: das Dickicht nach der Malweise auf der Grafikkarte ({@link GertschGl}), im Wind.
 * Links das Bild (OpenGL), rechts das Kennblatt des Bilds gegen die Vorlage (auf „Messen“). Unten Vorlage, Qualität,
 * Wind, Hysterese, Sonne und „Neu würfeln“; dazu Bilder je Sekunde und wie viele Flecken je Bild das Band wechseln.
 * <p>
 * Ohne Fenster:
 * <ul>
 * <li>{@code --bild datei.png [vorlage] [LOW|MEDIUM|HIGH|ULTRA] [wind m/s] [zeit s]}: ein Bild mit Kennblatt (Vorgabe
 * sommer, HIGH, Windstille).</li>
 * <li>{@code --flimmern [wind m/s] [LOW|MEDIUM|HIGH]}: 48 Bilder mit 24 B/s im Wind, je ohne und mit Hysterese; gibt
 * aus, wie viele Flecken je Bild das Band wechseln.</li>
 * </ul>
 */
public final class GertschGlDemo implements GLEventListener {
    final GertschGl gg = new GertschGl();
    final Kamera kamera = new Kamera();
    /** Neue Szene aus dem Hintergrund, im GL-Faden zu laden. */
    final AtomicReference<Object[]> neu = new AtomicReference<>();
    volatile boolean messenGewuenscht;
    volatile BufferedImage gemessenBild;
    volatile double zeit = -1;
    long t0 = System.nanoTime(), letzteStatistik;
    int bilder;
    double bps;
    boolean geladen;
    /**
     * Zähler in jedem Bild lesen (für Messungen ohne Fenster). Nicht über {@code invoke}: Ohne Animator zeichnet JOGL dabei
     * ein weiteres Bild zur selben Zeit, und gezählt würde dessen Wechsel (keine).
     */
    volatile boolean statistikJedesBild;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        if (args.length > 0 && args[0].equals("--flimmern")) { flimmern(args); return; }
        if (args.length > 1 && args[0].equals("--bild")) { bild(args); return; }
        SwingUtilities.invokeLater(GertschGlDemo::oeffnen);
    }

    /** Dickicht und Licht im aufrufenden Faden bauen (einige Sekunden). */
    static Object[] szene(long seed, double hoehe, double richtung) {
        Dickicht d = Dickicht.sommer();
        d.seed = seed;
        d.bauen();
        Licht l = new Licht();
        l.sonneHoehe = hoehe;
        l.sonneRichtung = richtung;
        l.vorbereiten(d);
        return new Object[]{d, l};
    }

    @Override public void init(GLAutoDrawable d) { gg.init(d.getGL().getGL4()); }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        Object[] s = neu.getAndSet(null);
        if (s != null) {
            gg.laden(gl, (Dickicht) s[0], (Licht) s[1]);
            geladen = true;
        }
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (!geladen) {
            gl.glClearColor(0.05f, 0.06f, 0.05f, 1);
            gl.glClear(GL4.GL_COLOR_BUFFER_BIT);
            return;
        }
        double t = zeit >= 0 ? zeit : (System.nanoTime() - t0) / 1e9;
        gg.zeichnen(gl, kamera, t, gl.getDefaultDrawFramebuffer(), 0, 0, w, h);
        bilder++;
        long jetzt = System.nanoTime();
        if (statistikJedesBild) gg.statistik(gl);
        else if (jetzt - letzteStatistik > 500_000_000L) {
            gg.statistik(gl);
            bps = bilder / ((jetzt - letzteStatistik) / 1e9);
            bilder = 0;
            letzteStatistik = jetzt;
        }
        if (messenGewuenscht) {
            messenGewuenscht = false;
            gemessenBild = gg.lesen(gl);
        }
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { gg.dispose(d.getGL().getGL4()); }

    // ------------------------------------------------------------------ ohne Fenster

    static GLOffscreenAutoDrawable offscreen(GertschGlDemo demo) {
        GLProfile p = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(p);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, 64, 64);
        dr.addGLEventListener(demo);
        return dr;
    }

    static void bild(String[] a) throws Exception {
        GertschGlDemo demo = new GertschGlDemo();
        int vi = a.length > 2 ? GertschDemo.index(a[2]) : 3;
        demo.gg.vorlage = Vorlagen.alle()[vi];
        if (a.length > 3) demo.gg.quality = GertschGl.Quality.valueOf(a[3].toUpperCase(Locale.ROOT));
        if (a.length > 4) demo.gg.wind.staerke = Double.parseDouble(a[4]);
        double t = a.length > 5 ? Double.parseDouble(a[5]) : 2.0;
        Licht vorgabe = new Licht();
        demo.neu.set(szene(1, vorgabe.sonneHoehe, vorgabe.sonneRichtung));
        GLOffscreenAutoDrawable dr = offscreen(demo);
        // einige Bilder, bis die Tonwerte aus dem Histogramm stehen und die Hysterese ihre Bänder hat
        long t0 = System.nanoTime();
        for (int i = 0; i < 6; i++) {
            demo.zeit = t - (5 - i) / 24.0;
            dr.display();
        }
        double sekunden = (System.nanoTime() - t0) / 1e9;
        BufferedImage[] im = new BufferedImage[1];
        dr.invoke(true, x -> {
            GL4 gl = x.getGL().getGL4();
            demo.gg.statistik(gl);
            im[0] = demo.gg.lesen(gl);
            return true;
        });
        Vorlage ist = new Messung().messen(im[0]), soll = demo.gg.vorlage;
        ist.name = "dickicht-gl";
        ist.titel = "Dickicht (OpenGL)";
        ImageIO.write(blatt(im[0], ist, soll, demo.gg.wind.staerke), "png", new File(a[1]));
        System.out.printf("6 Bilder in %.1f s (mit dem Laden) · %s%n", sekunden, demo.gg.info());
        System.out.print(ist.bericht());
        System.out.print(soll.bericht());
        System.out.println("Bild: " + a[1]);
        dr.destroy();
        System.exit(0);
    }

    static void flimmern(String[] a) throws Exception {
        GertschGlDemo demo = new GertschGlDemo();
        demo.gg.wind.staerke = a.length > 1 ? Double.parseDouble(a[1]) : 6;
        demo.gg.quality = a.length > 2 ? GertschGl.Quality.valueOf(a[2].toUpperCase(Locale.ROOT)) : GertschGl.Quality.MEDIUM;
        Licht vorgabe = new Licht();
        demo.neu.set(szene(1, vorgabe.sonneHoehe, vorgabe.sonneRichtung));
        GLOffscreenAutoDrawable dr = offscreen(demo);
        demo.statistikJedesBild = true;
        for (double h : new double[]{0, 0.15, 0.3}) {
            demo.gg.hysterese = h;
            double summe = 0;
            int n = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < 48; i++) {
                demo.zeit = 10 + i / 24.0;
                dr.display();   // liest die Zähler dieses Bilds in display()
                if (i >= 8) {   // die ersten Bilder füllen die Bänder
                    summe += demo.gg.flimmern();
                    n++;
                }
            }
            double ms = (System.nanoTime() - t0) / 1e6 / 48;
            double[] z = demo.gg.zeiten();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < z.length; i++) sb.append(String.format(Locale.GERMAN, "%s %.1f · ", GertschGl.DURCHGAENGE[i], z[i]));
            System.out.printf(Locale.GERMAN, "Hysterese %.2f Blenden: %.2f %% der Flecken wechseln je Bild das Band (Wind %.0f m/s, %s, %.0f ms je Bild mit Lesen)%n  Grafikkarte (ms): %s%n",
                    h, 100 * summe / n, demo.gg.wind.staerke, demo.gg.quality, ms, sb);
        }
        dr.destroy();
        System.exit(0);
    }

    /** Standbild: das Bild (1000 px breit) und das Kennblatt mit dem Soll. */
    static BufferedImage blatt(BufferedImage bild, Vorlage ist, Vorlage soll, double wind) {
        int vw = 1000, vh = (int) Math.round(vw * bild.getHeight() / (double) bild.getWidth()), rand = 20;
        int w = rand + vw + rand + Kennblatt.BREITE + rand, h = Math.max(vh + 2 * rand + 24, Kennblatt.HOEHE + 2 * rand);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(Kennblatt.GRUND);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Kennblatt.TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(String.format(Locale.GERMAN, "Dickicht nach der Malweise von Franz Gertsch · OpenGL · Vorlage „%s“ · Wind %.0f m/s", soll.titel, wind), rand, rand + 14);
        g.drawImage(bild, rand, rand + 24, vw, vh, null);
        Kennblatt.zeichnen(g, ist, rand + vw + rand, rand, soll);
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------------ Fenster

    static final Color BG = new Color(11, 17, 24);

    private static void oeffnen() {
        GertschGlDemo demo = new GertschGlDemo();
        demo.gg.quality = GertschGl.Quality.MEDIUM;
        demo.gg.wind.staerke = 4;
        FFrame f = new FFrame("Gertsch · Phase 3 · Dickicht auf der Grafikkarte, im Wind");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout(8, 6));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
        GLCanvas canvas = new GLCanvas(caps);
        canvas.addGLEventListener(demo);
        canvas.setPreferredSize(new Dimension(880, 560));

        Vorlage[] ist = {null};
        JPanel blatt = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                g0.setColor(BG);
                g0.fillRect(0, 0, getWidth(), getHeight());
                Vorlage soll = demo.gg.vorlage;
                Kennblatt.zeichnen((Graphics2D) g0, ist[0] != null ? ist[0] : soll, 0, 0, ist[0] != null ? soll : null);
            }
        };
        blatt.setPreferredSize(new Dimension(Kennblatt.BREITE, Kennblatt.HOEHE));
        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(BG);
        east.add(blatt, BorderLayout.NORTH);

        FComboBox vorlage = new FComboBox(Vorlagen.TITEL);
        vorlage.setSelectedIndex(3);
        FComboBox qual = new FComboBox(new Object[]{"Niedrig (640)", "Mittel (1000)", "Hoch (1600)", "Ultra (2400)"});
        qual.setSelectedIndex(1);
        FCheckBox hyst = new FCheckBox("Hysterese");
        hyst.setSelected(true);
        FButton messen = new FButton("Messen"), wuerfeln = new FButton("Neu würfeln");
        FSlider wind = new FSlider(0, 150, 40), hoehe = new FSlider(3, 70, (int) new Licht().sonneHoehe), richtung = new FSlider(-180, 180, (int) new Licht().sonneRichtung);
        JLabel lWind = label(""), lHoehe = label(""), lRichtung = label(""), status = label("baut das Dickicht …");
        status.setForeground(Kennblatt.LEISE);
        Runnable texte = () -> {
            lWind.setText(String.format(Locale.GERMAN, "Wind %.1f m/s", wind.getValue() / 10.0));
            lHoehe.setText("Sonne " + hoehe.getValue() + "°");
            lRichtung.setText("aus " + richtung.getValue() + "°");
        };
        texte.run();
        long[] seed = {1};
        Runnable neueSzene = () -> {
            status.setText("baut das Dickicht …");
            long s = seed[0];
            double hh = hoehe.getValue(), rr = richtung.getValue();
            new Thread(() -> demo.neu.set(szene(s, hh, rr)), "gertsch-szene").start();
        };
        vorlage.addActionListener(e -> { demo.gg.vorlage = Vorlagen.alle()[vorlage.getSelectedIndex()]; ist[0] = null; blatt.repaint(); });
        qual.addActionListener(e -> demo.gg.quality = GertschGl.Quality.values()[qual.getSelectedIndex()]);
        hyst.addActionListener(e -> demo.gg.hysterese = hyst.isSelected() ? 0.3 : 0);
        wind.addChangeListener(e -> { texte.run(); demo.gg.wind.staerke = wind.getValue() / 10.0; });
        for (FSlider s : new FSlider[]{hoehe, richtung})
            s.addChangeListener(e -> { texte.run(); if (!s.getValueIsAdjusting()) neueSzene.run(); });
        wuerfeln.addActionListener(e -> { seed[0]++; neueSzene.run(); });
        messen.addActionListener(e -> {
            demo.gemessenBild = null;
            demo.messenGewuenscht = true;
            new Thread(() -> {
                while (demo.gemessenBild == null) Thread.onSpinWait();
                Vorlage v = new Messung().messen(demo.gemessenBild);
                v.name = "dickicht-gl";
                v.titel = "Dickicht (OpenGL)";
                SwingUtilities.invokeLater(() -> { ist[0] = v; blatt.repaint(); });
            }, "gertsch-messen").start();
        });
        new javax.swing.Timer(500, e -> {
            if (!demo.geladen) return;
            double[] z = demo.gg.zeiten();
            double summe = 0;
            StringBuilder sb = new StringBuilder("<html>Grafikkarte je Durchgang (ms)");
            for (int i = 0; i < z.length; i++) {
                summe += z[i];
                sb.append(String.format(Locale.GERMAN, "<br>%s: %.1f", GertschGl.DURCHGAENGE[i], z[i]));
            }
            sb.append(String.format(Locale.GERMAN, "<br>%,d Flecken, %,d Werfer im Schatten</html>", demo.gg.instanzen, demo.gg.schattenInstanzen));
            status.setText(String.format(Locale.GERMAN, "%.0f B/s · Grafikkarte %.0f ms · %.1f %% wechseln je Bild das Band", demo.bps, summe, 100 * demo.gg.flimmern()));
            status.setToolTipText(sb.toString());
        }).start();

        JPanel zeile1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2)), zeile2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        zeile1.setBackground(BG);
        zeile2.setBackground(BG);
        zeile1.add(label("Vorlage"));
        zeile1.add(vorlage);
        zeile1.add(label("Qualität"));
        zeile1.add(qual);
        zeile1.add(hyst);
        zeile1.add(messen);
        zeile1.add(wuerfeln);
        zeile2.add(lWind);
        zeile2.add(wind);
        zeile2.add(lHoehe);
        zeile2.add(hoehe);
        zeile2.add(lRichtung);
        zeile2.add(richtung);
        JPanel south = new JPanel(new GridLayout(2, 1, 0, 2));
        south.setBackground(BG);
        south.add(zeile1);
        south.add(zeile2);
        zeile2.add(status);   // Bilder je Sekunde, Zeit der Grafikkarte, Wechsel der Bänder (Durchgänge im Tooltip)
        root.add(canvas, BorderLayout.CENTER);
        root.add(east, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        // deckend: ein GLCanvas in einem durchsichtigen Fenster bleibt unter Windows leer (wie in den anderen Demos)
        f.setBackground(BG);
        f.setPreferredFrameSize(new Dimension(1440, 810));
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
