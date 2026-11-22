package com.dan.lauf.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Das Bild im Fenster, als Demo der Bühne ohne Projekt (P6e; seit Teil B, B.3 auf {@link Buehne} umgestellt): Gelände,
 * Wasser und Himmel über einem Lauf, live oder aus dem Archiv, im FFrame. Gebiet, Ansichten und Szenarien kommen aus
 * einer Szenendatei ({@link Szenen}); in der Demo steht nichts mehr über ein Gebiet.
 * <ul>
 * <li><b>Kamera:</b> Ziehen mit der Maus dreht und neigt, das Rad ändert den Bildwinkel; W A S D bewegen (mit Umschalt
 * zehnmal schneller), Q und E heben und senken.</li>
 * <li><b>Zeit:</b> Zeitraffer 1 bis 3 000 (live begrenzt ihn die Physik), aus dem Archiv auch rückwärts.</li>
 * <li><b>Einblendung:</b> Szenenzeit, Pegel, Wind, Zeitraffer, Bilder/s.</li>
 * </ul>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.BildDemo --szenen ..\HalligHooge\szenen\Hooge.szenen [--szenario sturm] [--sekunden 20]
 * </pre>
 * Mit {@code --sekunden} endet die Demo nach dieser Zeit und gibt die Einblendung auch aus (für Proben).
 */
public final class BildDemo {
    static final Color BG = new Color(10, 14, 18), INK = new Color(220, 230, 235), MUTED = new Color(140, 160, 170);
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final Canvas leinwand = new Canvas();
    private final JLabel status = new JLabel(" ");
    private final FSlider raffer = new FSlider(0, 0, 350, 250);
    private final JLabel rafferText = new JLabel(" ");
    private volatile Buehne buehne;
    private volatile boolean zu;

    public static void main(String[] args) throws Exception {
        String datei = "..\\HalligHooge\\szenen\\Hooge.szenen", wahl = null;
        double sekunden = 0;
        for (int i = 0; i + 1 < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> datei = args[i + 1];
                case "--szenario" -> wahl = args[i + 1];
                case "--sekunden" -> sekunden = Double.parseDouble(args[i + 1]);
                default -> { }
            }
        }
        Szenen szenen = Szenen.lies(new File(datei));
        Szenario s0 = wahl != null ? szenen.szenario(wahl) : szenen.szenarien().get(0);
        if (s0 == null) throw new IllegalArgumentException("BildDemo: Szenario " + wahl + " steht nicht in " + datei);
        new BildDemo().laufen(szenen, s0, sekunden);
    }

    void laufen(Szenen szenen, Szenario s0, double sekunden) throws Exception {
        FFrame[] fenster = new FFrame[1];
        SwingUtilities.invokeAndWait(() -> fenster[0] = oeffnen(szenen, s0));
        Thread.sleep(400);
        String pegel = szenen.zahlen("pegel.ort") != null ? szenen.text("pegel.name", "Pegel") : null;
        Timer uhr = new Timer(250, e -> {
            Buehne b = buehne;
            if (b == null) return;
            status.setText(b.zustand().text(ZONE, pegel));
            rafferText.setText(String.format(Locale.GERMAN, "Zeitraffer %.0f", rafferWert()));
        });
        try (Buehne b = Buehne.oeffnen(szenen.gebiet(), leinwand)) {
            b.steuerung.anbringen(leinwand);
            double[] p = szenen.zahlen("pegel.ort");
            if (p != null) b.pegel(pegel, p[0], p[1], szenen.zahl("pegel.umkreis", 300));
            waehle(b, szenen, s0);
            buehne = b;
            if (zu) b.beenden();
            uhr.start();
            if (sekunden > 0) {
                Thread t = new Thread(() -> {
                    try {
                        for (double s = 0; s < sekunden; s += 2) {
                            Thread.sleep(2000);
                            System.out.println(b.zustand().text(ZONE, pegel));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    b.beenden();
                }, "probe");
                t.setDaemon(true);
                t.start();
            }
            b.laufen();
        } finally {
            uhr.stop();
            SwingUtilities.invokeLater(() -> fenster[0].dispose());
        }
    }

    private void waehle(Buehne b, Szenen szenen, Szenario s) {
        b.szenario(s);
        if (s.ansicht != null) b.ansicht(szenen.ansicht(s.ansicht));
        SwingUtilities.invokeLater(() -> raffer.setValue((int) Math.round(100 * Math.log10(Math.max(1, s.zeitraffer)))));
    }

    /** Zeitraffer aus dem Regler: 10^(Wert / 100), 1 … 3 162. */
    double rafferWert() { return Math.pow(10, raffer.getValue() / 100.0); }

    private FFrame oeffnen(Szenen szenen, Szenario s0) {
        FFrame f = new FFrame("Bild am Lauf · " + szenen.datei.getName());
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        leinwand.setPreferredSize(new Dimension(1600, 900));
        leinwand.setBackground(Color.BLACK);
        root.add(leinwand, BorderLayout.CENTER);
        JPanel rechts = new JPanel();
        rechts.setLayout(new BoxLayout(rechts, BoxLayout.Y_AXIS));
        rechts.setBackground(BG);
        rechts.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        rechts.add(marke("Szenario"));
        FComboBox szenario = new FComboBox(szenen.szenarien().toArray());
        szenario.setSelectedItem(s0);
        szenario.addActionListener(e -> {
            Buehne b = buehne;
            if (b != null && szenario.getSelectedItem() != null) waehle(b, szenen, (Szenario) szenario.getSelectedItem());
        });
        szenario.setMaximumSize(new Dimension(260, 34));
        rechts.add(szenario);
        rechts.add(marke("Ansicht"));
        FComboBox ansicht = new FComboBox(szenen.ansichten().toArray());
        ansicht.addActionListener(e -> {
            Buehne b = buehne;
            if (b != null && ansicht.getSelectedItem() != null) b.ansicht((Ansicht) ansicht.getSelectedItem());
        });
        ansicht.setMaximumSize(new Dimension(260, 34));
        rechts.add(ansicht);
        rafferText.setForeground(MUTED);
        rechts.add(rafferText);
        raffer.setMaximumSize(new Dimension(260, 40));
        raffer.addChangeListener(e -> {
            Buehne b = buehne;
            if (b != null) b.zeitraffer(rafferWert());
        });
        rechts.add(raffer);
        FComboBox richtung = new FComboBox(new Object[] {"vorwärts", "rückwärts", "angehalten"});
        richtung.addActionListener(e -> {
            Buehne b = buehne;
            if (b == null) return;
            b.rueckwaerts(richtung.getSelectedIndex() == 1);
            b.pause(richtung.getSelectedIndex() == 2);
        });
        richtung.setMaximumSize(new Dimension(260, 34));
        rechts.add(richtung);
        JLabel hilfe = new JLabel("<html>Maus ziehen: drehen, neigen<br>Rad: Bildwinkel<br>W A S D: bewegen (Umschalt: schneller)<br>Q E: höher, tiefer</html>");
        hilfe.setForeground(MUTED);
        hilfe.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        rechts.add(hilfe);
        root.add(rechts, BorderLayout.EAST);
        status.setForeground(INK);
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        JPanel unten = new JPanel(new BorderLayout());
        unten.setBackground(new Color(8, 12, 15));
        unten.add(status);
        root.add(unten, BorderLayout.SOUTH);
        f.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { schliessen(); }

            @Override public void windowClosed(java.awt.event.WindowEvent e) { schliessen(); }
        });
        f.setPreferredFrameSize(new Dimension(1900, 1000));
        f.setSize(1900, 1000);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        leinwand.requestFocus();
        return f;
    }

    private void schliessen() {
        zu = true;
        Buehne b = buehne;
        if (b != null) b.beenden();
    }

    private static JLabel marke(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        return l;
    }
}
