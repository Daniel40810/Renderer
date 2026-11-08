package com.dan.gertsch.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.gertsch.Bild;
import com.dan.gertsch.Lab;
import com.dan.gertsch.Messung;
import com.dan.gertsch.Struktur;
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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo von com.dan.gertsch, Phase 1: eine Vorlage aus {@code templates/} und ihre Messung. Links das Bild in einer von
 * sechs Ansichten (Original, Tonstufen, Lichtflecken, dunkle Lücken, Richtungen, Akzente), rechts das Kennblatt; unten
 * Vorlage, Ansicht, Zahl der Tonstufen und Qualität. Fehlt die Vorlage, zeigt das Kennblatt die gespeicherten Werte aus
 * {@link Vorlagen}.
 * <p>
 * Aufrufe ohne Fenster:
 * <ul>
 * <li>{@code --bild datei.png} – Kennblätter aller sechs gespeicherten Vorlagen (nur Zahlen, keine Bildpunkte der Werke)</li>
 * <li>{@code --bild datei.png sommer} – Vorlage „sommer“ frisch gemessen: vier Ansichten und Kennblatt (nur für den
 * eigenen Gebrauch, das Bild enthält das Werk)</li>
 * <li>{@code --bericht} – Kennwerte aller Vorlagen als Text</li>
 * <li>{@code --quelltext Vorlagen.java} – alle Vorlagen messen und die Presets als Java-Quelltext schreiben</li>
 * <li>{@code --vorlagen ordner} – wo die Vorlagen liegen (sonst {@code templates})</li>
 * </ul>
 */
public final class GertschDemo {
    private GertschDemo() { }

    enum Ansicht { ORIGINAL("Original"), TONSTUFEN("Tonstufen"), LICHTFLECKEN("Lichtflecken"), LUECKEN("Dunkle Lücken"),
        RICHTUNGEN("Richtungen"), AKZENTE("Akzente");
        final String titel;
        Ansicht(String t) { titel = t; }
        @Override public String toString() { return titel; }
    }

    static File ordner = new File("templates");

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        String bild = null, welche = null, quelltext = null;
        boolean bericht = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--vorlagen" -> ordner = new File(args[++i]);
                case "--bild" -> {
                    bild = args[++i];
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) welche = args[++i];
                }
                case "--quelltext" -> quelltext = args[++i];
                case "--bericht" -> bericht = true;
                default -> { }
            }
        }
        if (quelltext != null) { quelltextSchreiben(new File(quelltext)); return; }
        if (bericht) {
            for (int i = 0; i < Vorlagen.NAMEN.length; i++) System.out.println(vorlage(i, new Messung()).bericht());
            return;
        }
        if (bild != null) {
            BufferedImage im;
            if (welche == null || welche.equals("alle")) im = Kennblatt.tafel(Vorlagen.alle());
            else {
                int i = index(welche);
                if (!datei(i).isFile()) throw new IOException(datei(i) + " fehlt");
                im = blatt(vorlage(i, new Messung()));
            }
            ImageIO.write(im, "png", new File(bild));
            System.out.println("Bild: " + bild);
            return;
        }
        SwingUtilities.invokeLater(GertschDemo::oeffnen);
    }

    static int index(String name) {
        for (int i = 0; i < Vorlagen.NAMEN.length; i++) if (Vorlagen.NAMEN[i].equalsIgnoreCase(name)) return i;
        throw new IllegalArgumentException("keine Vorlage „" + name + "“, bekannt: " + String.join(", ", Vorlagen.NAMEN));
    }

    static File datei(int i) { return new File(ordner, Vorlagen.DATEIEN[i]); }

    /** Vorlage i frisch gemessen, wenn die Datei da ist; sonst die gespeicherten Werte. */
    static Vorlage vorlage(int i, Messung m) throws IOException {
        if (!datei(i).isFile()) return Vorlagen.alle()[i];
        Vorlage v = m.messen(datei(i));
        v.name = Vorlagen.NAMEN[i];
        v.titel = Vorlagen.TITEL[i];
        return v;
    }

    static void quelltextSchreiben(File ziel) throws IOException {
        List<Vorlage> vs = new ArrayList<>();
        Messung m = new Messung();
        for (int i = 0; i < Vorlagen.NAMEN.length; i++) {
            if (!datei(i).isFile()) throw new IOException(datei(i) + " fehlt");
            Vorlage v = vorlage(i, m);
            System.out.print(v.bericht());
            System.out.printf(Locale.GERMAN, "  (%.1f s)%n%n", v.sekunden);
            vs.add(v);
        }
        StringBuilder s = new StringBuilder();
        s.append("package com.dan.gertsch;\n\n");
        s.append("/**\n");
        s.append(" * Die sechs Vorlagen als Zahlen: Kennwerte der Bilder in {@code templates/}, gemessen mit {@link Messung} (")
                .append(m.quality).append(", ").append(m.stufen).append(" Stufen, seed ").append(m.seed).append(").\n");
        s.append(" * Erzeugt von {@code GertschDemo --quelltext} am ").append(LocalDate.now()).append("; nicht von Hand ändern, sondern neu messen.\n");
        s.append(" * <p>\n");
        s.append(" * Die Bilder selbst gehören nicht ins Repository: Es sind Werke von Franz Gertsch (1930–2022), urheberrechtlich\n");
        s.append(" * geschützt. Hier stehen nur die gemessenen Kennwerte.\n");
        s.append(" */\n");
        s.append("public final class Vorlagen {\n");
        s.append("    private Vorlagen() { }\n\n");
        s.append("    public static final String[] NAMEN = ").append(liste(Vorlagen.NAMEN)).append(";\n");
        s.append("    public static final String[] TITEL = ").append(liste(Vorlagen.TITEL)).append(";\n");
        s.append("    public static final String[] DATEIEN = ").append(liste(Vorlagen.DATEIEN)).append(";\n");
        s.append("    public static final String[] BESCHREIBUNG = {\n");
        for (int i = 0; i < Vorlagen.BESCHREIBUNG.length; i++)
            s.append("            \"").append(Vorlagen.BESCHREIBUNG[i]).append(i + 1 < Vorlagen.BESCHREIBUNG.length ? "\",\n" : "\"};\n\n");
        s.append("    /** Vorlage nach Kurzname (etwa „sommer“), groß oder klein geschrieben. */\n");
        s.append("    public static Vorlage nachName(String name) {\n");
        s.append("        return switch (name.toLowerCase(java.util.Locale.ROOT)) {\n");
        for (String n : Vorlagen.NAMEN) s.append("            case \"").append(n).append("\" -> ").append(n).append("();\n");
        s.append("            default -> throw new IllegalArgumentException(\"keine Vorlage \" + name);\n");
        s.append("        };\n    }\n\n");
        s.append("    /** Alle Vorlagen in der Reihenfolge von {@link #NAMEN}, jede eine frische Kopie. */\n");
        s.append("    public static Vorlage[] alle() {\n");
        s.append("        Vorlage[] v = new Vorlage[NAMEN.length];\n");
        s.append("        for (int i = 0; i < v.length; i++) v[i] = nachName(NAMEN[i]);\n");
        s.append("        return v;\n    }\n");
        for (int i = 0; i < vs.size(); i++) {
            s.append('\n');
            s.append(vs.get(i).alsJava(Vorlagen.NAMEN[i], Vorlagen.TITEL[i] + " (" + Vorlagen.DATEIEN[i] + "): " + Vorlagen.BESCHREIBUNG[i] + "."));
        }
        s.append("}\n");
        Files.writeString(ziel.toPath(), s.toString(), StandardCharsets.UTF_8);
        System.out.println("Quelltext: " + ziel);
    }

    static String liste(String[] a) {
        StringBuilder s = new StringBuilder("{");
        for (int i = 0; i < a.length; i++) s.append(i == 0 ? "\"" : ", \"").append(a[i]).append('"');
        return s.append('}').toString();
    }

    // ------------------------------------------------------------------ Ansichten

    static BufferedImage ansicht(Vorlage v, Ansicht a) {
        Bild b = v.bild;
        int n = b.w * b.h;
        int[] o = switch (a) {
            case ORIGINAL -> b.rgb();
            case TONSTUFEN -> v.palette.stufen(b);
            case LICHTFLECKEN -> {
                // die hellen Flecken bis zum doppelten Median, in ihrer Farbe auf Schwarz
                float[] t = Messung.lichtflecken(b, Math.max(1, (int) Math.round(v.hellD50 * b.w)));
                int[] rgb = b.rgb(), r = new int[n];
                for (int i = 0; i < n; i++) r[i] = mischen(0x000000, rgb[i], Math.min(1, t[i] / 25));
                yield r;
            }
            case LUECKEN -> {
                float[] t = Messung.luecken(b, Math.max(1, (int) Math.round(v.dunkelD50 * b.w)));
                int[] r = new int[n];
                for (int i = 0; i < n; i++) r[i] = mischen(0x10161c, 0x9fd0f0, Math.min(1, t[i] / 25));
                yield r;
            }
            case RICHTUNGEN -> {
                Messung m = new Messung();
                float[][] t = Struktur.tensor(b.L, b.w, b.h, m.sigmaD, m.sigmaI);
                float[] e = t[1].clone();
                java.util.Arrays.sort(e);
                double p95 = Math.max(1e-6, e[(int) (0.95 * (n - 1))]);
                int[] r = new int[n];
                for (int i = 0; i < n; i++)
                    r[i] = Color.HSBtoRGB(t[0][i] / 180f, 0.8f, (float) Math.min(1, Math.sqrt(t[1][i] / p95))) & 0xffffff;
                yield r;
            }
            case AKZENTE -> {
                Messung m = new Messung();
                int[] r = new int[n];
                for (int i = 0; i < n; i++) {
                    double c = Lab.buntheit(b.a[i], b.b[i]), d = Math.abs(Lab.farbton(b.a[i], b.b[i]) - v.farbton) % 360;
                    boolean akzent = c >= m.akzentBuntheit && Math.min(d, 360 - d) > m.akzentWinkel;
                    r[i] = akzent ? Lab.zuRgb(b.L[i], b.a[i], b.b[i]) : Lab.zuRgb(b.L[i] * 0.45, 0, 0);
                }
                yield r;
            }
        };
        BufferedImage im = new BufferedImage(b.w, b.h, BufferedImage.TYPE_INT_RGB);
        im.setRGB(0, 0, b.w, b.h, o, 0, b.w);
        return im;
    }

    static int mischen(int p, int q, double t) {
        int r = (int) Math.round(((p >> 16) & 255) * (1 - t) + ((q >> 16) & 255) * t);
        int g = (int) Math.round(((p >> 8) & 255) * (1 - t) + ((q >> 8) & 255) * t);
        int b = (int) Math.round((p & 255) * (1 - t) + (q & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Standbild einer gemessenen Vorlage: vier Ansichten (2 × 2) und das Kennblatt. */
    static BufferedImage blatt(Vorlage v) {
        int vw = 560, vh = (int) Math.round(vw * v.bild.h / (double) v.bild.w), rand = 20;
        int w = rand + 2 * (vw + rand) + Kennblatt.BREITE + rand, h = Math.max(rand + 2 * (vh + 30 + rand), Kennblatt.HOEHE + 2 * rand);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(Kennblatt.GRUND);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Ansicht[] as = {Ansicht.ORIGINAL, Ansicht.TONSTUFEN, Ansicht.LICHTFLECKEN, Ansicht.RICHTUNGEN};
        for (int i = 0; i < 4; i++) {
            int x = rand + (i % 2) * (vw + rand), y = rand + (i / 2) * (vh + 30 + rand);
            g.setColor(Kennblatt.TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString(as[i].titel, x, y + 14);
            g.drawImage(ansicht(v, as[i]), x, y + 24, vw, vh, null);
        }
        Kennblatt.zeichnen(g, v, rand + 2 * (vw + rand), rand);
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------------ Fenster

    static final Color BG = new Color(11, 17, 24);

    private static void oeffnen() {
        FFrame f = new FFrame("Gertsch · Phase 1 · Vorlagen vermessen");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout(12, 12));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Vorlage[] aktuell = {Vorlagen.alle()[3]};
        BufferedImage[] bild = {null};
        String[] hinweis = {""};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setColor(BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (bild[0] != null) {
                    double s = Math.min(getWidth() / (double) bild[0].getWidth(), getHeight() / (double) bild[0].getHeight());
                    int w = (int) (bild[0].getWidth() * s), h = (int) (bild[0].getHeight() * s);
                    g.drawImage(bild[0], (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
                } else {
                    g.setColor(Kennblatt.LEISE);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                    int y = getHeight() / 2 - 20;
                    for (String line : hinweis[0].split("\n")) {
                        g.drawString(line, Math.max(10, (getWidth() - g.getFontMetrics().stringWidth(line)) / 2), y);
                        y += 22;
                    }
                }
            }
        };
        view.setPreferredSize(new Dimension(900, 640));
        JPanel blatt = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                g0.setColor(BG);
                g0.fillRect(0, 0, getWidth(), getHeight());
                Kennblatt.zeichnen((Graphics2D) g0, aktuell[0], 0, 0);
            }
        };
        blatt.setPreferredSize(new Dimension(Kennblatt.BREITE, Kennblatt.HOEHE));
        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(BG);
        east.add(blatt, BorderLayout.NORTH);

        FComboBox wahl = new FComboBox(Vorlagen.TITEL);
        wahl.setSelectedIndex(3);
        FComboBox ansicht = new FComboBox(Ansicht.values());
        FComboBox qual = new FComboBox(new Object[]{"Niedrig (400)", "Mittel (640)", "Hoch (1000)", "Ultra (1600)"});
        qual.setSelectedIndex(2);
        FSlider stufen = new FSlider(3, 10, 6);
        JLabel lStufen = label("6 Tonstufen"), status = label("");
        status.setForeground(Kennblatt.LEISE);

        AtomicInteger lauf = new AtomicInteger();
        Runnable zeigen = () -> {
            Vorlage v = aktuell[0];
            bild[0] = v.bild == null ? null : ansicht(v, (Ansicht) ansicht.getSelectedItem());
            view.repaint();
            blatt.repaint();
        };
        Runnable messen = () -> {
            int i = wahl.getSelectedIndex(), nr = lauf.incrementAndGet();
            Messung m = new Messung();
            m.stufen = stufen.getValue();
            m.quality = Messung.Quality.values()[qual.getSelectedIndex()];
            if (!datei(i).isFile()) {
                aktuell[0] = Vorlagen.alle()[i];
                hinweis[0] = datei(i).getPath() + " fehlt.\nDas Kennblatt zeigt die gespeicherten Werte aus Vorlagen.java.";
                status.setText("gespeicherte Kennwerte");
                zeigen.run();
                return;
            }
            status.setText("misst " + datei(i).getName() + " …");
            new Thread(() -> {
                try {
                    Vorlage v = vorlage(i, m);
                    BufferedImage im = ansicht(v, (Ansicht) ansicht.getSelectedItem());
                    SwingUtilities.invokeLater(() -> {
                        if (nr != lauf.get()) return;
                        aktuell[0] = v;
                        bild[0] = im;
                        status.setText(String.format(Locale.GERMAN, "gemessen in %.1f s", v.sekunden));
                        view.repaint();
                        blatt.repaint();
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> status.setText(ex.getMessage()));
                }
            }, "gertsch-messung").start();
        };
        wahl.addActionListener(e -> messen.run());
        qual.addActionListener(e -> messen.run());
        ansicht.addActionListener(e -> zeigen.run());
        stufen.addChangeListener(e -> {
            lStufen.setText(stufen.getValue() + " Tonstufen");
            if (!stufen.getValueIsAdjusting()) messen.run();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        south.setBackground(BG);
        south.add(label("Vorlage"));
        south.add(wahl);
        south.add(label("Ansicht"));
        south.add(ansicht);
        south.add(lStufen);
        south.add(stufen);
        south.add(label("Qualität"));
        south.add(qual);
        south.add(status);
        root.add(view, BorderLayout.CENTER);
        root.add(east, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        f.setPreferredFrameSize(new Dimension(1440, 880));   // FFrame.setVisible packt, setSize hielte nicht
        f.setVisible(true);
        messen.run();
    }

    static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(Kennblatt.TEXT);
        return l;
    }
}
