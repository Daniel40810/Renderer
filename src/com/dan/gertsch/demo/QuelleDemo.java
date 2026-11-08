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
import com.dan.gertsch.Quelle;
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
 * Demo von com.dan.gertsch, Phase 5: eine Szene von außen über {@link Quelle}. Links das Bild (OpenGL, im Wind, wahlweise
 * als Holzschnitt), rechts das Kennblatt, auf „Messen“ gegen die Vorlage. Szenen:
 * <ul>
 * <li><b>Huflattich</b> ({@link Huflattich}): ein eigenes kleines Modell, das über seine Brücke als Quelle kommt, wie in
 * einer App Bäume aus {@code forest} und Gras aus {@code reed}. Vorbild ist die Vorlage „Blätter“.</li>
 * <li><b>Dickicht als Büschel</b>: das ganze Dickicht (17 Mio. Blätter) als 1,4 Mio. Büschel durch die Schnittstelle
 * ({@link Dickicht#alsBueschel()}, {@link Dickicht#aus}), so wie eine App Bäume aus {@code forest} hereinreicht,</li>
 * <li><b>Dickicht aus seed</b>: dasselbe Dickicht ohne die Schnittstelle, zum Vergleich.</li>
 * <li><b>Huflattich am Hang</b> (seit LituyaBay1958 W10): derselbe Huflattich auf einem {@link com.dan.gertsch.Gelaende},
 * 35 % nach hinten ansteigend mit Wellen ({@link Quelle#aufGelaende}); die Kamera steht 1,5 m über ihrem Grund.</li>
 * <li><b>Huflattich am Wasser</b> (seit W10b): derselbe Hang, vorn bis 0,45 m unter einem Wasserspiegel.</li>
 * </ul>
 * Ohne Fenster: {@code --bild datei.png [huflattich|bueschel|seed|hang|wasser] [vorlage] [LOW|MEDIUM|HIGH|ULTRA] [wind m/s]}; mit
 * {@code --cpu} malt der Prozessor ({@link Malweise}).
 */
public final class QuelleDemo implements GLEventListener {
    static final Color BG = new Color(11, 17, 24);
    static final String[] SZENEN = {"Huflattich (von außen)", "Dickicht als Büschel", "Dickicht aus seed", "Huflattich am Hang (Gelände)", "Huflattich am Wasser"};
    static final String[] KURZ = {"huflattich", "bueschel", "seed", "hang", "wasser"};

    /** Szenen mit dem Huflattich (Vorlage „Blätter“, Sonne 45°). */
    static boolean huflattich(int i) { return i == 0 || i == 3 || i == 4; }

    final GertschGl gg = new GertschGl();
    final StichGl sg = new StichGl();
    /** Neue Szene aus dem Hintergrund: Dickicht, Licht, Kamera, Seitenverhältnis. */
    final AtomicReference<Object[]> neu = new AtomicReference<>();
    volatile Kamera kamera = new Kamera();
    volatile boolean holzschnitt, messenGewuenscht;
    volatile BufferedImage gemessenBild;
    volatile double zeit = -1;
    volatile int blaetterVonAussen;
    long t0 = System.nanoTime(), letzteStatistik;
    int bilder;
    double bps;
    boolean geladen;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        if (args.length > 1 && args[0].equals("--bild")) { bild(args); return; }
        SwingUtilities.invokeLater(QuelleDemo::oeffnen);
    }

    /** Ein kleines Dickicht (gut 3 Mio. Blätter), das noch Blatt für Blatt durch die Schnittstelle passt. */
    static Dickicht klein(long seed) {
        Dickicht d = Dickicht.sommer();
        d.seed = seed;
        d.baeume = 4; d.jungbaeume = 25; d.straeucher = 70; d.wand = 60; d.kraut = 180; d.dach = 9; d.halme = 500; d.streu = 700;
        return d.bauen();
    }

    /** Szene i bauen (im aufrufenden Faden): Dickicht, Licht, Kamera, Seitenverhältnis, Vorlage. */
    static Object[] szene(int i, long seed, double hoehe, double richtung) {
        Dickicht d;
        Kamera k = new Kamera();
        double seite = 2000 / 1328.0;
        Vorlage v = Vorlagen.sommer();
        if (huflattich(i)) {
            Huflattich h = new Huflattich();
            h.seed = seed;
            h.bauen();
            if (i >= 3) {   // seit LituyaBay1958 W10: derselbe Huflattich auf einem Hang (35 % nach hinten, Wellen); W10b mit Wasser vorn
                com.dan.gertsch.Gelaende g = i == 3 ? h.hang(0.35) : h.hang(0.35).mitWasser(0.45f);
                d = Dickicht.aus(Quelle.aufGelaende(h.alsQuelle(), g));
                k = h.kamera(g);
            } else {
                d = Dickicht.aus(h.alsQuelle());
                k = h.kamera();
            }
            seite = 1465 / 1000.0;
            v = Vorlagen.blaetter();
        } else {
            Dickicht voll = Dickicht.sommer();
            voll.seed = seed;
            voll.bauen();
            d = i == 1 ? Dickicht.aus(voll.alsBueschel()) : voll;
        }
        Licht l = new Licht();
        l.sonneHoehe = hoehe;
        l.sonneRichtung = richtung;
        l.vorbereiten(d);
        return new Object[]{d, l, k, seite, v};
    }

    static double sonneVorgabe(int i) { return huflattich(i) ? 45 : new Licht().sonneHoehe; }

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        gg.init(gl);
        sg.init(gl);
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        Object[] s = neu.getAndSet(null);
        if (s != null) {
            Dickicht dk = (Dickicht) s[0];
            gg.seitenverhaeltnis = (Double) s[3];
            gg.laden(gl, dk, (Licht) s[1]);
            kamera = (Kamera) s[2];
            blaetterVonAussen = dk.blattDaten == null && !dk.zweige.isEmpty() && !dk.zweige.get(0).eigeneFarbe ? 0 : (int) Math.min(Integer.MAX_VALUE, dk.blattZahl());
            geladen = true;
            sg.neu();
        }
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (!geladen) {
            gl.glClearColor(BG.getRed() / 255f, BG.getGreen() / 255f, BG.getBlue() / 255f, 1);
            gl.glClear(GL4.GL_COLOR_BUFFER_BIT);
            return;
        }
        double t = zeit >= 0 ? zeit : (System.nanoTime() - t0) / 1e9;
        if (holzschnitt) {
            gg.zeichnen(gl, kamera, t, -1, 0, 0, 0, 0);
            sg.drucken(gl, gg.helligkeitTextur(), gg.bildBreite(), gg.bildHoehe(), StichGl.Eingang.HELLIGKEIT, Druck.BREITE, t);
            sg.zeigen(gl, gl.getDefaultDrawFramebuffer(), 0, 0, w, h, 0, 0.5, 0.5, BG.getRGB() & 0xFFFFFF);
        } else gg.zeichnen(gl, kamera, t, gl.getDefaultDrawFramebuffer(), 0, 0, w, h);
        bilder++;
        long jetzt = System.nanoTime();
        if (jetzt - letzteStatistik > 500_000_000L) {
            gg.statistik(gl);
            bps = bilder / ((jetzt - letzteStatistik) / 1e9);
            bilder = 0;
            letzteStatistik = jetzt;
        }
        if (messenGewuenscht) {
            messenGewuenscht = false;
            gemessenBild = holzschnitt ? sg.lesen(gl) : gg.lesen(gl);
        }
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        sg.dispose(gl);
        gg.dispose(gl);
    }

    // ------------------------------------------------------------------ ohne Fenster

    static void bild(String[] a) throws Exception {
        boolean cpu = false;
        java.util.List<String> r = new java.util.ArrayList<>();
        for (String s : a) if (s.equals("--cpu")) cpu = true; else r.add(s);
        String datei = r.get(1);
        int si = 0;
        if (r.size() > 2) for (int i = 0; i < KURZ.length; i++) if (KURZ[i].equalsIgnoreCase(r.get(2))) si = i;
        Object[] s = szene(si, 1, sonneVorgabe(si), new Licht().sonneRichtung);
        Vorlage soll = r.size() > 3 ? Vorlagen.nachName(r.get(3)) : (Vorlage) s[4];
        String qual = r.size() > 4 ? r.get(4).toUpperCase(Locale.ROOT) : "HIGH";
        double wind = r.size() > 5 ? Double.parseDouble(r.get(5)) : 0;
        Dickicht d = (Dickicht) s[0];
        BufferedImage im;
        long t0 = System.nanoTime();
        String wie;
        if (cpu) {
            Malweise m = new Malweise();
            m.vorlage = soll;
            m.quality = Malweise.Quality.valueOf(qual);
            m.seitenverhaeltnis = (Double) s[3];
            Malweise.Ergebnis e = m.malen(d, (Licht) s[1], (Kamera) s[2]);
            im = e.bild;
            wie = "Prozessor";
            System.out.printf(Locale.GERMAN, "Prozessor: %,d Flecken aus %,d Blättern in %.1f s%n", e.flecken, e.blaetter, e.sekunden);
        } else {
            QuelleDemo demo = new QuelleDemo();
            demo.gg.vorlage = soll;
            demo.gg.quality = GertschGl.Quality.valueOf(qual);
            demo.gg.wind.staerke = wind;
            demo.neu.set(s);
            GLOffscreenAutoDrawable dr = StichDemo.offscreen(demo);
            for (int i = 0; i < 6; i++) {
                demo.zeit = 2.0 - (5 - i) / 24.0;
                dr.display();
            }
            BufferedImage[] b = new BufferedImage[1];
            dr.invoke(true, x -> { GL4 gl = x.getGL().getGL4(); demo.gg.statistik(gl); b[0] = demo.gg.lesen(gl); return true; });
            im = b[0];
            wie = "OpenGL";
            System.out.println(demo.gg.info());
            dr.destroy();
        }
        Vorlage ist = new Messung().messen(im);
        ist.name = KURZ[si];
        ist.titel = SZENEN[si];
        ImageIO.write(tafel(im, ist, soll, String.format(Locale.GERMAN, "%s · %s · %,d Blätter%s · Vorlage „%s“", SZENEN[si], wie, d.blattZahl(),
                si == 0 ? " von außen" : si == 3 ? " von außen, am Hang" : si == 4 ? " von außen, am Wasser" : si == 1 ? " in Büscheln von außen" : " aus seed", soll.titel)), "png", new File(datei));
        System.out.print(ist.bericht());
        System.out.print(soll.bericht());
        System.out.printf(Locale.GERMAN, "zusammen %.1f s · Bild: %s%n", (System.nanoTime() - t0) / 1e9, datei);
        System.exit(0);
    }

    /** Standbild: das Bild (1000 px breit) und das Kennblatt mit dem Soll. */
    static BufferedImage tafel(BufferedImage bild, Vorlage ist, Vorlage soll, String titel) {
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
        g.drawString("Szene von außen nach der Malweise von Franz Gertsch · " + titel, rand, rand + 14);
        g.drawImage(bild, rand, rand + 24, vw, vh, null);
        Kennblatt.zeichnen(g, ist, rand + vw + rand, rand, soll);
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------------ Fenster

    private static void oeffnen() {
        QuelleDemo demo = new QuelleDemo();
        demo.gg.quality = GertschGl.Quality.MEDIUM;
        demo.gg.wind.staerke = 3;
        demo.gg.vorlage = Vorlagen.blaetter();
        FFrame f = new FFrame("Gertsch · Phase 5 · Szene von außen");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout(8, 6));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(demo);
        canvas.setPreferredSize(new Dimension(900, 560));

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

        FComboBox szene = new FComboBox(SZENEN), vorlage = new FComboBox(Vorlagen.TITEL);
        vorlage.setSelectedIndex(5);
        FCheckBox hyst = new FCheckBox("Hysterese"), stich = new FCheckBox("Holzschnitt");
        hyst.setSelected(true);
        FButton messen = new FButton("Messen"), wuerfeln = new FButton("Neu würfeln");
        FSlider wind = new FSlider(0, 150, 30), hoehe = new FSlider(3, 80, (int) sonneVorgabe(0)), richtung = new FSlider(-180, 180, (int) new Licht().sonneRichtung);
        JLabel lWind = label(""), lHoehe = label(""), lRichtung = label(""), status = label("baut die Szene …");
        status.setForeground(Kennblatt.LEISE);
        Runnable texte = () -> {
            lWind.setText(String.format(Locale.GERMAN, "Wind %.1f m/s", wind.getValue() / 10.0));
            lHoehe.setText("Sonne " + hoehe.getValue() + "°");
            lRichtung.setText("aus " + richtung.getValue() + "°");
        };
        texte.run();
        long[] seed = {1};
        Runnable neueSzene = () -> {
            status.setText("baut die Szene …");
            int i = szene.getSelectedIndex();
            long s = seed[0];
            double hh = hoehe.getValue(), rr = richtung.getValue();
            new Thread(() -> demo.neu.set(szene(i, s, hh, rr)), "gertsch-szene").start();
        };
        szene.addActionListener(e -> {
            int i = szene.getSelectedIndex();
            vorlage.setSelectedIndex(huflattich(i) ? 5 : 3);   // Huflattich nach „Blätter“, das Dickicht nach „Sommer“
            int ziel = (int) sonneVorgabe(i);
            if (hoehe.getValue() != ziel) hoehe.setValue(ziel);   // stößt eine neue Szene an
            else neueSzene.run();
        });
        vorlage.addActionListener(e -> { demo.gg.vorlage = Vorlagen.alle()[vorlage.getSelectedIndex()]; ist[0] = null; blatt.repaint(); });
        hyst.addActionListener(e -> demo.gg.hysterese = hyst.isSelected() ? 0.3 : 0);
        stich.addActionListener(e -> demo.holzschnitt = stich.isSelected());
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
                v.name = KURZ[szene.getSelectedIndex()];
                v.titel = SZENEN[szene.getSelectedIndex()];
                SwingUtilities.invokeLater(() -> { ist[0] = v; blatt.repaint(); });
            }, "gertsch-messen").start();
        });
        new javax.swing.Timer(500, e -> {
            if (!demo.geladen) return;
            double summe = 0;
            for (double v : demo.gg.zeiten()) summe += v;
            status.setText(String.format(Locale.GERMAN, "%.0f B/s · Grafikkarte %.0f ms%s · %,d Flecken · %s",
                    demo.bps, summe, demo.holzschnitt ? String.format(Locale.GERMAN, " + Druck %.1f ms", demo.sg.zeit()) : "", demo.gg.instanzen,
                    demo.blaetterVonAussen > 0 ? String.format(Locale.GERMAN, "%,d Blätter von außen", demo.blaetterVonAussen) : "Blätter aus seed"));
        }).start();

        JPanel zeile1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2)), zeile2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2)),
                zeile3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        for (JPanel z : new JPanel[]{zeile1, zeile2, zeile3}) z.setBackground(BG);
        zeile1.add(label("Szene"));
        zeile1.add(szene);
        zeile1.add(label("Vorlage"));
        zeile1.add(vorlage);
        zeile1.add(hyst);
        zeile1.add(stich);
        zeile1.add(messen);
        zeile1.add(wuerfeln);
        zeile2.add(lWind);
        zeile2.add(wind);
        zeile2.add(lHoehe);
        zeile2.add(hoehe);
        zeile2.add(lRichtung);
        zeile2.add(richtung);
        zeile3.add(status);
        JPanel south = new JPanel(new GridLayout(3, 1, 0, 2));
        south.setBackground(BG);
        south.add(zeile1);
        south.add(zeile2);
        south.add(zeile3);
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
