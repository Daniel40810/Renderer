package com.dan.gertsch.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.gertsch.Dickicht;
import com.dan.gertsch.Kamera;
import com.dan.gertsch.Licht;
import com.dan.gertsch.Malweise;
import com.dan.gertsch.Messung;
import com.dan.gertsch.Vorlage;
import com.dan.gertsch.Vorlagen;

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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo von com.dan.gertsch, Phase 2: ein {@link Dickicht} nach der {@link Malweise} gemalt und gegen eine Vorlage
 * gemessen. Links das Bild in vier Ansichten (gemalt; Licht ohne Stufen, wie ein Foto mit derselben Belichtung; die
 * Tonstufen in Falschfarben; wie viele Blätter jeder Fleck vertritt), rechts das Kennblatt des gemalten Bilds mit dem
 * Soll der Vorlage. Unten Vorlage, Ansicht, Qualität, Tonwerte, Saum, seed, Sonne und Fleckgröße.
 * <p>
 * Ohne Fenster: {@code --bild datei.png [vorlage] [LOW|MEDIUM|HIGH|ULTRA] [seed]} schreibt das gemalte Bild mit dem
 * Kennblatt (Vorgabe: sommer, HIGH, seed 1) und gibt Soll und Ist als Text aus. Das Bild enthält keine Bildpunkte der
 * Werke, nur das eigene Dickicht.
 */
public final class DickichtDemo {
    private DickichtDemo() { }

    enum Ansicht { GEMALT("Gemalt"), LICHT("Licht ohne Stufen"), STUFEN("Tonstufen"), ZUSAMMEN("Zusammengefasst");
        final String titel;
        Ansicht(String t) { titel = t; }
        @Override public String toString() { return titel; }
    }

    /** Was gemalt wird. */
    static final class Wahl {
        int vorlage = 3;
        Malweise.Quality quality = Malweise.Quality.MEDIUM;
        Malweise.Tonwerte tonwerte = Malweise.Tonwerte.VORLAGE;
        double hoehe = new Licht().sonneHoehe, richtung = new Licht().sonneRichtung, fleck = new Malweise().fleck;
        boolean saum, mitLicht;
        long seed = 1;

        Wahl kopie() {
            Wahl k = new Wahl();
            k.vorlage = vorlage; k.quality = quality; k.tonwerte = tonwerte; k.hoehe = hoehe; k.richtung = richtung;
            k.fleck = fleck; k.saum = saum; k.mitLicht = mitLicht; k.seed = seed;
            return k;
        }
    }

    /** Dickicht und Licht bleiben, solange seed und Sonne gleich sind (Bauen und Schattenkarte kosten Sekunden). */
    static final class Vorrat {
        Dickicht d;
        Licht l;
        long seed = Long.MIN_VALUE;
        double hoehe = Double.NaN, richtung = Double.NaN;
    }

    static Malweise.Ergebnis malen(Wahl w, Vorrat v) {
        if (v.d == null || v.seed != w.seed) {
            v.d = Dickicht.sommer();
            v.d.seed = w.seed;
            v.d.bauen();
            v.seed = w.seed;
            v.l = null;
        }
        if (v.l == null || v.hoehe != w.hoehe || v.richtung != w.richtung) {
            v.l = new Licht();
            v.l.sonneHoehe = w.hoehe;
            v.l.sonneRichtung = w.richtung;
            v.l.vorbereiten(v.d);
            v.hoehe = w.hoehe;
            v.richtung = w.richtung;
        }
        Malweise m = new Malweise();
        m.quality = w.quality;
        m.vorlage = Vorlagen.alle()[w.vorlage];
        m.fleck = w.fleck;
        m.saum = w.saum ? 1.2 : 0;
        m.tonwerte = w.tonwerte;
        m.mitLicht = w.mitLicht;
        return m.malen(v.d, v.l, new Kamera());
    }

    /** Das gemalte Bild gemessen, mit Namen. */
    static Vorlage messen(Malweise.Ergebnis r) {
        Vorlage ist = new Messung().messen(r.bild);
        ist.name = "dickicht";
        ist.titel = "Dickicht";
        return ist;
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        if (args.length > 1 && args[0].equals("--bild")) {
            Wahl w = new Wahl();
            w.quality = Malweise.Quality.HIGH;
            if (args.length > 2) w.vorlage = GertschDemo.index(args[2]);
            if (args.length > 3) w.quality = Malweise.Quality.valueOf(args[3].toUpperCase(Locale.ROOT));
            if (args.length > 4) w.seed = Long.parseLong(args[4]);
            Malweise.Ergebnis r = malen(w, new Vorrat());
            Vorlage ist = messen(r), soll = Vorlagen.alle()[w.vorlage];
            ImageIO.write(blatt(r, ist, soll), "png", new File(args[1]));
            System.out.printf("Gemalt in %.1f s: %d Flecken aus %d Blättern im Bild (%d gezeichnet), Fläche %.2f, kleinster Fleck %.1f px%n",
                    r.sekunden, r.flecken, r.blaetter, r.gezeichnet, r.flaecheNachher / Math.max(1, r.flaecheVorher), r.kleinsterFleck);
            System.out.print(ist.bericht());
            System.out.print(soll.bericht());
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(DickichtDemo::oeffnen);
    }

    // ------------------------------------------------------------------ Ansichten

    static BufferedImage ansicht(Malweise.Ergebnis r, Ansicht a, Vorlage soll) {
        if (a == Ansicht.GEMALT || a == Ansicht.LICHT && r.licht == null) return r.bild;
        if (a == Ansicht.LICHT) return r.licht;
        int w = r.breite, h = r.hoehe;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] o = new int[w * h], bild = r.bild.getRGB(0, 0, w, h, null, 0, w);
        int k = soll.palette.size();
        int[] pal = new int[k];
        for (int i = 0; i < k; i++) pal[i] = soll.palette.rgb(i);
        for (int p = 0; p < w * h; p++) {
            int id = r.id[p];
            if (a == Ansicht.STUFEN) {
                int st = id >= 0 ? r.stufe[id] : naechste(bild[p], pal);
                o[p] = st < 0 ? 0xE0569A : Color.HSBtoRGB(0.62f - 0.5f * st / Math.max(1, k - 1), 0.75f, 0.25f + 0.75f * st / Math.max(1, k - 1));
            } else {
                if (id < 0) { o[p] = id == -1 ? 0x2A2620 : 0x101418; continue; }
                double f = r.faktor[id];
                o[p] = f <= 1.01 ? 0x2F6B5E : Color.HSBtoRGB((float) Math.max(0, 0.16 - 0.04 * Math.log(f) / Math.log(2)), 0.85f, 0.95f);
            }
        }
        im.setRGB(0, 0, w, h, o, 0, w);
        return im;
    }

    static int naechste(int rgb, int[] pal) {
        int best = 0;
        long bd = Long.MAX_VALUE;
        for (int i = 0; i < pal.length; i++) {
            long dr = ((rgb >> 16) & 255) - ((pal[i] >> 16) & 255), dg = ((rgb >> 8) & 255) - ((pal[i] >> 8) & 255), db = (rgb & 255) - (pal[i] & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /** Standbild: das gemalte Bild (1000 px breit) und das Kennblatt mit dem Soll. */
    static BufferedImage blatt(Malweise.Ergebnis r, Vorlage ist, Vorlage soll) {
        int vw = 1000, vh = (int) Math.round(vw * r.hoehe / (double) r.breite), rand = 20;
        int w = rand + vw + rand + Kennblatt.BREITE + rand, h = Math.max(vh + 2 * rand + 24, Kennblatt.HOEHE + 2 * rand);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(Kennblatt.GRUND);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Kennblatt.TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("Dickicht nach der Malweise von Franz Gertsch · Vorlage „" + soll.titel + "“", rand, rand + 14);
        g.drawImage(r.bild, rand, rand + 24, vw, vh, null);
        Kennblatt.zeichnen(g, ist, rand + vw + rand, rand, soll);
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------------ Fenster

    static final Color BG = new Color(11, 17, 24);

    private static void oeffnen() {
        FFrame f = new FFrame("Gertsch · Phase 2 · Dickicht nach der Malweise");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout(12, 12));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Wahl wahl = new Wahl();
        Vorrat vorrat = new Vorrat();
        Malweise.Ergebnis[] letztes = {null};
        Vorlage[] ist = {null};
        BufferedImage[] bild = {null};
        FComboBox ansicht = new FComboBox(Ansicht.values());

        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setColor(BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (bild[0] == null) return;
                double s = Math.min(getWidth() / (double) bild[0].getWidth(), getHeight() / (double) bild[0].getHeight());
                int w = (int) (bild[0].getWidth() * s), h = (int) (bild[0].getHeight() * s);
                g.drawImage(bild[0], (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            }
        };
        view.setPreferredSize(new Dimension(880, 560));
        JPanel blatt = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                g0.setColor(BG);
                g0.fillRect(0, 0, getWidth(), getHeight());
                Vorlage soll = Vorlagen.alle()[wahl.vorlage];
                Kennblatt.zeichnen((Graphics2D) g0, ist[0] != null ? ist[0] : soll, 0, 0, ist[0] != null ? soll : null);
            }
        };
        blatt.setPreferredSize(new Dimension(Kennblatt.BREITE, Kennblatt.HOEHE));
        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(BG);
        east.add(blatt, BorderLayout.NORTH);

        FComboBox vorlage = new FComboBox(Vorlagen.TITEL);
        vorlage.setSelectedIndex(wahl.vorlage);
        FComboBox qual = new FComboBox(new Object[]{"Niedrig (640)", "Mittel (1000)", "Hoch (1600)", "Ultra (2400)"});
        qual.setSelectedIndex(wahl.quality.ordinal());
        FComboBox ton = new FComboBox(new Object[]{"Tonwerte nach der Vorlage", "Tonwerte linear (Median)"});
        FCheckBox saum = new FCheckBox("Heller Saum");
        FButton wuerfeln = new FButton("Neu würfeln");
        FSlider hoehe = new FSlider(3, 70, (int) Math.round(wahl.hoehe)), richtung = new FSlider(-180, 180, (int) Math.round(wahl.richtung));
        FSlider fleck = new FSlider(10, 80, (int) Math.round(wahl.fleck * 10000));
        JLabel lHoehe = label(""), lRichtung = label(""), lFleck = label(""), status = label("");
        status.setForeground(Kennblatt.LEISE);
        Runnable texte = () -> {
            lHoehe.setText("Sonne " + hoehe.getValue() + "°");
            lRichtung.setText("aus " + richtung.getValue() + "°");
            lFleck.setText(String.format(Locale.GERMAN, "Fleck %.2f %%", fleck.getValue() / 100.0));
        };
        texte.run();

        AtomicInteger lauf = new AtomicInteger();
        Runnable zeigen = () -> {
            if (letztes[0] != null) bild[0] = ansicht(letztes[0], (Ansicht) ansicht.getSelectedItem(), Vorlagen.alle()[wahl.vorlage]);
            view.repaint();
            blatt.repaint();
        };
        Runnable malen = () -> {
            int nr = lauf.incrementAndGet();
            wahl.vorlage = vorlage.getSelectedIndex();
            wahl.quality = Malweise.Quality.values()[qual.getSelectedIndex()];
            wahl.tonwerte = ton.getSelectedIndex() == 0 ? Malweise.Tonwerte.VORLAGE : Malweise.Tonwerte.MEDIAN;
            wahl.saum = saum.isSelected();
            wahl.hoehe = hoehe.getValue();
            wahl.richtung = richtung.getValue();
            wahl.fleck = fleck.getValue() / 10000.0;
            wahl.mitLicht = ansicht.getSelectedItem() == Ansicht.LICHT;
            status.setText("malt …");
            blatt.repaint();
            Wahl jetzt = wahl.kopie();
            new Thread(() -> {
                Malweise.Ergebnis r;
                Vorlage gemessen;
                synchronized (vorrat) {
                    if (nr != lauf.get()) return;
                    r = malen(jetzt, vorrat);
                    gemessen = messen(r);
                }
                SwingUtilities.invokeLater(() -> {
                    if (nr != lauf.get()) return;
                    letztes[0] = r;
                    ist[0] = gemessen;
                    status.setText(String.format(Locale.GERMAN, "gemalt in %.1f s · %.2f Mio. Flecken", r.sekunden, r.flecken / 1e6));
                    zeigen.run();
                });
            }, "gertsch-malen").start();
        };
        vorlage.addActionListener(e -> malen.run());
        qual.addActionListener(e -> malen.run());
        ton.addActionListener(e -> malen.run());
        saum.addActionListener(e -> malen.run());
        wuerfeln.addActionListener(e -> { wahl.seed++; malen.run(); });
        ansicht.addActionListener(e -> {
            if (ansicht.getSelectedItem() == Ansicht.LICHT && letztes[0] != null && letztes[0].licht == null) malen.run();
            else zeigen.run();
        });
        for (FSlider s : new FSlider[]{hoehe, richtung, fleck})
            s.addChangeListener(e -> {
                texte.run();
                if (!s.getValueIsAdjusting()) malen.run();
            });

        JPanel zeile1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4)), zeile2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        zeile1.setBackground(BG);
        zeile2.setBackground(BG);
        zeile1.add(label("Vorlage"));
        zeile1.add(vorlage);
        zeile1.add(label("Ansicht"));
        zeile1.add(ansicht);
        zeile1.add(label("Qualität"));
        zeile1.add(qual);
        zeile1.add(ton);
        zeile1.add(saum);
        zeile1.add(wuerfeln);
        zeile2.add(lHoehe);
        zeile2.add(hoehe);
        zeile2.add(lRichtung);
        zeile2.add(richtung);
        zeile2.add(lFleck);
        zeile2.add(fleck);
        zeile2.add(status);
        JPanel south = new JPanel(new GridLayout(2, 1, 0, 4));
        south.setBackground(BG);
        south.add(zeile1);
        south.add(zeile2);
        root.add(view, BorderLayout.CENTER);
        root.add(east, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        f.setPreferredFrameSize(new Dimension(1440, 810));
        f.setVisible(true);
        malen.run();
    }

    static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(Kennblatt.TEXT);
        return l;
    }
}
