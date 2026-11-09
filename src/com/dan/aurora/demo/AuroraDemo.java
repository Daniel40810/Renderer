package com.dan.aurora.demo;

import com.dan.aurora.Aurora;
import com.dan.aurora.Emission;
import com.dan.aurora.Niederschlag;
import com.dan.aurora.Ort;
import com.dan.aurora.Spektrum;
import com.dan.aurora.Vorhang;
import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
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
 * Demo von com.dan.aurora, Phase 1: Polarlicht über einer Winterbucht, auf dem Prozessor gerechnet. Links das Bild,
 * rechts das Kennblatt (Ort im Magnetfeld, Oval, Vorhänge, Höhenprofile der vier Linien, Helligkeit), unten Ort,
 * Preset, Kp, Qualität, Sicht, Energie, Fluss, Faltung, Strahlen, Blick, Weltzeit und Zeit.
 * <p>
 * Ohne Fenster: {@code --bild datei.png [preset|kp] [ort] [LOW|MEDIUM|HIGH|ULTRA] [seed] [--blatt]} schreibt ein
 * Standbild (Vorgabe: aktiv, Tromsø, HIGH, seed 1); {@code --blatt} hängt das Kennblatt rechts an. Presets sind
 * ruhig, aktiv, ausbruch und rot, oder eine Zahl als Kp.
 */
public final class AuroraDemo {
    private AuroraDemo() { }

    static final Color BG = new Color(11, 17, 24), TEXT = new Color(230, 234, 238), LEISE = new Color(150, 160, 172);
    static final String[] PRESETS = {"Ruhig", "Aktiv", "Aufbruch", "Rot", "Nach Kp"};

    /** Bildbreite je Qualität. */
    static int breite(Aurora.Quality q) {
        return switch (q) { case LOW -> 640; case MEDIUM -> 1000; case HIGH -> 1600; case ULTRA -> 2400; };
    }

    static Aurora preset(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "ruhig": return Aurora.ruhig();
            case "aktiv": return Aurora.aktiv();
            case "ausbruch": case "aufbruch": return Aurora.ausbruch();
            case "rot": return Aurora.rot();
            default: return Aurora.kp(Double.parseDouble(name.replace(',', '.')));
        }
    }

    static Ort ort(String name) {
        String n = schlicht(name);
        for (Ort o : Ort.alle()) if (schlicht(o.name).startsWith(n)) return o;
        throw new IllegalArgumentException("Unbekannter Ort: " + name);
    }

    /** Kleinbuchstaben ohne Akzente, ø → o, damit „tromso“ Tromsø findet. */
    static String schlicht(String s) {
        String t = java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder();
        for (char c : t.toCharArray()) if (Character.getType(c) != Character.NON_SPACING_MARK) b.append(c == 'ø' ? 'o' : c == 'ß' ? 's' : c);
        return b.toString();
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        if (args.length > 1 && args[0].equals("--bild")) {
            Aurora a = args.length > 2 ? preset(args[2]) : Aurora.aktiv();
            if (args.length > 3) a.ort = ort(args[3]);
            if (args.length > 4) a.quality = Aurora.Quality.valueOf(args[4].toUpperCase(Locale.ROOT));
            if (args.length > 5) a.seed = Long.parseLong(args[5]);
            boolean blatt = false;
            for (String s : args) if (s.equals("--blatt")) blatt = true;
            a.vorbereiten(0);
            AuroraSzene sz = new AuroraSzene();
            sz.seed = a.seed * 7 + 3;
            zielen(sz, a);
            AuroraSzene.Ergebnis e = sz.malen(a, breite(a.quality));
            BufferedImage out = blatt ? mitBlatt(e.bild, a, e) : e.bild;
            ImageIO.write(out, "png", new File(args[1]));
            System.out.printf("Gemalt in %.1f s, %d × %d%n", e.sekunden, e.bild.getWidth(), e.bild.getHeight());
            System.out.print(bericht(a, e));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(AuroraDemo::oeffnen);
    }

    /** Blick auf den Hauptbogen: Azimut des Bogens, Höhe so, dass sein Maximum im oberen Bilddrittel liegt. */
    static void zielen(AuroraSzene sz, Aurora a) {
        sz.azimut = a.azimutHauptbogen();
        double hw = a.hoehenwinkel(a.hauptbogen());
        sz.hoehe = Math.max(12, Math.min(45, hw * 0.55 + 6));
    }

    /** Kennzahlen als Text (für --bild und das Fenster). */
    static String bericht(Aurora a, AuroraSzene.Ergebnis e) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s: %.2f° N %.2f° E, magnetisch %.1f°, Inklination %.1f°, Deklination %.1f°, %.1f MLT%n",
                a.ort.name, a.ort.breite, a.ort.laenge, a.mlat, a.inklination, a.deklination, a.mlt));
        sb.append(String.format("Kp %.1f: Oval diffus ab %.1f°, Bögen %.1f° bis %.1f° magnetischer Breite%n", a.kp, a.grenzeDiffus, a.grenzeBogen, a.grenzePol));
        int i = 1;
        for (Vorhang v : a.vorhaenge) {
            boolean diffus = v.breite > 30;
            sb.append(String.format("%s: %.0f km %s, Maximum %.0f km hoch, %.1f° über dem Horizont, E₀ %.1f keV, Q %.1f mW/m², senkrecht %.1f kR (%s)%n",
                    diffus ? "Diffus" : "Bogen " + i++, Math.abs(v.mitteAmMeridian()), v.mitteAmMeridian() >= 0 ? "polwärts" : "äquatorwärts", v.hMaximum,
                    a.hoehenwinkel(v), v.energie, v.fluss, v.saeuleKr[Emission.GRUEN], Emission.ibc(v.saeuleKr[Emission.GRUEN])));
        }
        if (e != null)
            sb.append(String.format("Hellste Stelle im Bild: %.1f kR in 557,7 nm, %.1f kR in 630,0 nm, %.2f mcd/m²; Polarlicht auf dem Boden %.2f mlx%n",
                    e.maxKr[Emission.GRUEN], e.maxKr[Emission.ROT], 1000 * e.maxLeuchtdichte, 1000 * e.bestrahlung));
        return sb.toString();
    }

    // ------------------------------------------------------------------ Kennblatt

    static final int BLATT_B = 340, BLATT_H = 520;

    static BufferedImage mitBlatt(BufferedImage bild, Aurora a, AuroraSzene.Ergebnis e) {
        int rand = 16, h = Math.max(bild.getHeight(), BLATT_H) + 2 * rand, w = bild.getWidth() + BLATT_B + 3 * rand;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.drawImage(bild, rand, rand, null);
        blatt(g, a, e, bild.getWidth() + 2 * rand, rand);
        g.dispose();
        return im;
    }

    /** Zeichnet das Kennblatt: Zahlen und die Höhenprofile des Hauptbogens. */
    static void blatt(Graphics2D g, Aurora a, AuroraSzene.Ergebnis e, int x0, int y0) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(16, 24, 33));
        g.fillRoundRect(x0, y0, BLATT_B, BLATT_H, 14, 14);
        Font fett = new Font(Font.SANS_SERIF, Font.BOLD, 13), normal = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        int x = x0 + 14, y = y0 + 22;
        g.setFont(fett);
        g.setColor(TEXT);
        g.drawString("Polarlicht · Kennblatt", x, y);
        g.setFont(normal);
        y += 20;
        g.setColor(LEISE);
        g.drawString(String.format("%s · %.2f° N · %.2f° E", a.ort.name, a.ort.breite, a.ort.laenge), x, y);
        y += 16;
        g.drawString(String.format("magnetisch %.1f° · Inklination %.1f° · Deklination %.0f°", a.mlat, a.inklination, a.deklination), x, y);
        y += 16;
        g.drawString(String.format("%02d.%02d., %04.1f UT = %.1f MLT · Kp %.1f", a.tag, a.monat, a.ut, a.mlt, a.kp), x, y);
        y += 20;
        g.setColor(TEXT);
        g.drawString(String.format("Oval: diffus ab %.1f°, Bögen %.1f° – %.1f°", a.grenzeDiffus, a.grenzeBogen, a.grenzePol), x, y);
        y += 16;
        g.setColor(LEISE);
        g.drawString(String.format("Beobachter %.0f km %s der Bogengrenze", Math.abs(a.grenzeBogen - Math.abs(a.mlat)) * com.dan.aurora.Oval.KM_JE_GRAD,
                Math.abs(a.mlat) >= a.grenzeBogen ? "polwärts" : "äquatorwärts"), x, y);
        y += 20;
        int i = 1;
        for (Vorhang v : a.vorhaenge) {
            boolean diffus = v.breite > 30;
            g.setColor(TEXT);
            g.drawString(String.format("%s · %.0f km %s · %.1f° hoch", diffus ? "Diffus" : "Bogen " + i++, Math.abs(v.mitteAmMeridian()),
                    v.mitteAmMeridian() >= 0 ? "polw." : "äquatorw.", a.hoehenwinkel(v)), x, y);
            y += 15;
            g.setColor(LEISE);
            g.drawString(String.format("   E₀ %.1f keV · Q %.1f mW/m² · %.1f kR · %s", v.energie, v.fluss, v.saeuleKr[Emission.GRUEN], Emission.ibc(v.saeuleKr[Emission.GRUEN])), x, y);
            y += 17;
        }
        if (e != null) {
            y += 4;
            g.setColor(TEXT);
            g.drawString(String.format("Hellste Stelle: %.1f kR grün · %.1f kR rot", e.maxKr[Emission.GRUEN], e.maxKr[Emission.ROT]), x, y);
            y += 15;
            g.setColor(LEISE);
            g.drawString(String.format("   %.2f mcd/m² · Boden %.2f mlx · %.1f s", 1000 * e.maxLeuchtdichte, 1000 * e.bestrahlung, e.sekunden), x, y);
            y += 17;
        }
        // Höhenprofile
        y += 8;
        int px = x, py = y, pw = BLATT_B - 28, ph = Math.max(80, y0 + BLATT_H - 16 - py - 18);
        g.setColor(new Color(24, 34, 46));
        g.fillRect(px, py, pw, ph);
        Vorhang v = a.hauptbogen();
        double hMin = 80, hMax = 350;
        g.setColor(new Color(50, 62, 78));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (int hk = 100; hk <= 300; hk += 50) {
            int yy = py + (int) Math.round(ph * (1 - (hk - hMin) / (hMax - hMin)));
            g.drawLine(px, yy, px + pw, yy);
            g.setColor(LEISE);
            g.drawString(hk + " km", px + 3, yy - 2);
            g.setColor(new Color(50, 62, 78));
        }
        Color[] farben = {new Color(120, 120, 255), new Color(90, 230, 120), new Color(240, 80, 80), new Color(230, 90, 200)};
        g.setStroke(new BasicStroke(1.6f));
        double maxAll = 0;
        double[][] p = new double[Emission.LINIEN][];
        for (int l = 0; l < Emission.LINIEN; l++) {
            p[l] = new double[Niederschlag.HOEHEN.length];
            for (int k = 0; k < p[l].length; k++)
                p[l][k] = l == Emission.ROT ? v.profilRot[k] : (1 - v.strahlen) * v.profilGlatt[l][k] + v.strahlen * v.profilStrahl[l][k];
            for (double d : p[l]) maxAll = Math.max(maxAll, d);
        }
        for (int l = 0; l < Emission.LINIEN; l++) {
            g.setColor(farben[l]);
            int lx = -1, ly = -1;
            for (int k = 0; k < p[l].length; k++) {
                double hk = Niederschlag.HOEHEN[k];
                if (hk < hMin || hk > hMax) continue;
                // Wurzel, damit das schwache Rot sichtbar bleibt
                int xx = px + 60 + (int) Math.round((pw - 70) * Math.sqrt(p[l][k] / maxAll));
                int yy = py + (int) Math.round(ph * (1 - (hk - hMin) / (hMax - hMin)));
                if (lx >= 0) g.drawLine(lx, ly, xx, yy);
                lx = xx;
                ly = yy;
            }
            g.drawString(Emission.NAME[l], px + 60 + l * 68 - (l == 0 ? 0 : 0), py + ph - 4 - (l % 2) * 12);
        }
        g.setStroke(new BasicStroke(1));
        g.setColor(LEISE);
        g.drawString("Volumenemission des Hauptbogens (Wurzel) über der Höhe", px, py + ph + 13);
    }

    // ------------------------------------------------------------------ Fenster

    static final class Wahl {
        Aurora aurora = Aurora.aktiv();
        AuroraSzene.Sicht sicht = AuroraSzene.Sicht.KAMERA_8;
        double blick = Double.NaN;
        double zeit = 0;
    }

    private static void oeffnen() {
        FFrame f = new FFrame("Aurora · Phase 1 · Polarlicht über der Bucht");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout(12, 12));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Wahl wahl = new Wahl();
        AuroraSzene szene = new AuroraSzene();
        AuroraSzene.Ergebnis[] letztes = {null};
        Aurora[] gezeigt = {null};

        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setColor(BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (letztes[0] == null) return;
                BufferedImage b = letztes[0].bild;
                double s = Math.min(getWidth() / (double) b.getWidth(), getHeight() / (double) b.getHeight());
                int w = (int) (b.getWidth() * s), h = (int) (b.getHeight() * s);
                g.drawImage(b, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            }
        };
        view.setPreferredSize(new Dimension(880, 480));
        JPanel blatt = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                g0.setColor(BG);
                g0.fillRect(0, 0, getWidth(), getHeight());
                if (gezeigt[0] != null) blatt((Graphics2D) g0, gezeigt[0], letztes[0], 0, 0);
            }
        };
        blatt.setPreferredSize(new Dimension(BLATT_B, BLATT_H));
        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(BG);
        east.add(blatt, BorderLayout.NORTH);

        FComboBox ort = new FComboBox(Ort.alle());
        FComboBox preset = new FComboBox(PRESETS);
        preset.setSelectedIndex(1);
        FSlider kp = new FSlider(0, 90, 40);
        FComboBox qual = new FComboBox(new Object[]{"Niedrig (640)", "Mittel (1000)", "Hoch (1600)", "Ultra (2400)"});
        qual.setSelectedIndex(1);
        FComboBox sicht = new FComboBox(AuroraSzene.Sicht.values());
        sicht.setSelectedItem(wahl.sicht);
        FButton wuerfeln = new FButton("Neu würfeln");
        FSlider energie = new FSlider(0, 100, 0), fluss = new FSlider(0, 100, 0), faltung = new FSlider(0, 100, 0), strahlen = new FSlider(0, 100, 0);
        FSlider blick = new FSlider(5, 60, 18), ut = new FSlider(0, 240, 220), zeit = new FSlider(0, 600, 0);
        JLabel lKp = label(""), lEnergie = label(""), lFluss = label(""), lFaltung = label(""), lStrahlen = label(""), lBlick = label(""), lUt = label(""), lZeit = label(""), status = label("");
        status.setForeground(LEISE);
        boolean[] still = {false};

        Runnable sliderVonAurora = () -> {
            still[0] = true;
            Aurora a = wahl.aurora;
            kp.setValue((int) Math.round(a.kp * 10));
            energie.setValue((int) Math.round(100 * Math.log(a.energie / 0.2) / Math.log(75)));
            fluss.setValue((int) Math.round(100 * Math.log(a.fluss) / Math.log(100)));
            faltung.setValue((int) Math.round(100 * a.faltung));
            strahlen.setValue((int) Math.round(100 * a.strahlen));
            ut.setValue((int) Math.round(a.ut * 10));
            still[0] = false;
        };
        Runnable texte = () -> {
            lKp.setText(String.format("Kp %.1f", kp.getValue() / 10.0));
            lEnergie.setText(String.format("E₀ %.1f keV", 0.2 * Math.pow(75, energie.getValue() / 100.0)));
            lFluss.setText(String.format("Q %.0f mW/m²", Math.pow(100, fluss.getValue() / 100.0)));
            lFaltung.setText("Faltung " + faltung.getValue() + " %");
            lStrahlen.setText("Strahlen " + strahlen.getValue() + " %");
            lBlick.setText("Blick " + blick.getValue() + "° hoch");
            lUt.setText(String.format("%04.1f UT", ut.getValue() / 10.0));
            lZeit.setText("t " + zeit.getValue() + " s");
        };
        sliderVonAurora.run();
        texte.run();

        AtomicInteger lauf = new AtomicInteger();
        Runnable malen = () -> {
            int nr = lauf.incrementAndGet();
            Aurora a = wahl.aurora;
            a.ort = ((Ort) ort.getSelectedItem()).kopie();
            a.kp = kp.getValue() / 10.0;
            a.energie = 0.2 * Math.pow(75, energie.getValue() / 100.0);
            a.fluss = Math.pow(100, fluss.getValue() / 100.0);
            a.faltung = faltung.getValue() / 100.0;
            a.strahlen = strahlen.getValue() / 100.0;
            a.ut = ut.getValue() / 10.0;
            a.quality = Aurora.Quality.values()[qual.getSelectedIndex()];
            wahl.sicht = (AuroraSzene.Sicht) sicht.getSelectedItem();
            wahl.zeit = zeit.getValue();
            status.setText("malt …");
            new Thread(() -> {
                Aurora kopie = kopie(a);
                kopie.vorbereiten(wahl.zeit);
                AuroraSzene sz = new AuroraSzene();
                sz.seed = kopie.seed * 7 + 3;
                sz.sicht = wahl.sicht;
                zielen(sz, kopie);
                sz.hoehe = blick.getValue();
                // erst grob, dann in der gewählten Qualität
                for (Aurora.Quality q : kopie.quality == Aurora.Quality.LOW ? new Aurora.Quality[]{Aurora.Quality.LOW} : new Aurora.Quality[]{Aurora.Quality.LOW, kopie.quality}) {
                    if (nr != lauf.get()) return;
                    Aurora.Quality ziel = kopie.quality;
                    kopie.quality = q;
                    AuroraSzene.Ergebnis e = sz.malen(kopie, breite(q));
                    kopie.quality = ziel;
                    if (nr != lauf.get()) return;
                    SwingUtilities.invokeLater(() -> {
                        if (nr != lauf.get()) return;
                        letztes[0] = e;
                        gezeigt[0] = kopie;
                        status.setText(String.format("%s · %.1f s · %s", q == ziel ? "fertig" : "Vorschau", e.sekunden, kopie.helligkeit()));
                        view.repaint();
                        blatt.repaint();
                    });
                }
            }, "aurora-malen").start();
        };
        ort.addActionListener(e -> malen.run());
        qual.addActionListener(e -> malen.run());
        sicht.addActionListener(e -> malen.run());
        preset.addActionListener(e -> {
            int i = preset.getSelectedIndex();
            long seed = wahl.aurora.seed;
            Aurora a = switch (i) { case 0 -> Aurora.ruhig(); case 1 -> Aurora.aktiv(); case 2 -> Aurora.ausbruch(); case 3 -> Aurora.rot(); default -> Aurora.kp(kp.getValue() / 10.0); };
            a.seed = seed;
            a.ut = wahl.aurora.ut;
            wahl.aurora = a;
            sliderVonAurora.run();
            texte.run();
            malen.run();
        });
        wuerfeln.addActionListener(e -> { wahl.aurora.seed++; malen.run(); });
        for (FSlider s : new FSlider[]{kp, energie, fluss, faltung, strahlen, blick, ut, zeit})
            s.addChangeListener(e -> {
                texte.run();
                if (still[0]) return;
                if (s == kp && preset.getSelectedIndex() == 4 && !s.getValueIsAdjusting()) {
                    Aurora a = Aurora.kp(kp.getValue() / 10.0);
                    a.seed = wahl.aurora.seed;
                    a.ut = wahl.aurora.ut;
                    wahl.aurora = a;
                    sliderVonAurora.run();
                    texte.run();
                }
                if (!s.getValueIsAdjusting()) malen.run();
            });

        JPanel zeile1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4)), zeile2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4)),
                zeile3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        zeile1.setBackground(BG);
        zeile2.setBackground(BG);
        zeile3.setBackground(BG);
        zeile1.add(label("Ort"));
        zeile1.add(ort);
        zeile1.add(label("Preset"));
        zeile1.add(preset);
        zeile1.add(lKp);
        zeile1.add(kp);
        zeile1.add(label("Qualität"));
        zeile1.add(qual);
        zeile1.add(sicht);
        zeile1.add(wuerfeln);
        zeile2.add(lEnergie);
        zeile2.add(energie);
        zeile2.add(lFluss);
        zeile2.add(fluss);
        zeile2.add(lFaltung);
        zeile2.add(faltung);
        zeile2.add(lStrahlen);
        zeile2.add(strahlen);
        zeile3.add(lBlick);
        zeile3.add(blick);
        zeile3.add(lUt);
        zeile3.add(ut);
        zeile3.add(lZeit);
        zeile3.add(zeit);
        zeile3.add(status);
        JPanel south = new JPanel(new GridLayout(3, 1, 0, 4));
        south.setBackground(BG);
        south.add(zeile1);
        south.add(zeile2);
        south.add(zeile3);
        root.add(view, BorderLayout.CENTER);
        root.add(east, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        // Bildschirm 1536 × 864: über 780 px Höhe verschwindet die untere Reihe hinter der Taskleiste
        f.setPreferredFrameSize(new Dimension(1440, 760));
        f.setVisible(true);
        malen.run();
    }

    /** Kopie der offenen Werte, damit der Malfaden in Ruhe rechnet, während die Regler sich ändern. */
    static Aurora kopie(Aurora a) {
        Aurora k = new Aurora();
        k.seed = a.seed; k.ort = a.ort.kopie(); k.kp = a.kp; k.monat = a.monat; k.tag = a.tag; k.ut = a.ut;
        k.energie = a.energie; k.fluss = a.fluss; k.weich = a.weich; k.faltung = a.faltung; k.strahlen = a.strahlen; k.breite = a.breite;
        k.drift = a.drift; k.bewegung = a.bewegung; k.boegen = a.boegen; k.diffus = a.diffus; k.extinktion = a.extinktion; k.quality = a.quality;
        return k;
    }

    static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(TEXT);
        return l;
    }
}
