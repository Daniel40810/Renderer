package com.dan.core.gpu.demo;

import com.dan.core.Core;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Stoppuhr;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;

import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Status-Bild von {@code com.dan.core.gpu} ohne Fenster (Werkbuch HalligHooge Teil A, Phase P2): ein Feld wird auf der
 * Karte gerechnet und mit derselben Rechnung in Java verglichen.
 * <ol>
 * <li>das Anfangsfeld (360 × 240 Zellen),</li>
 * <li>dasselbe Feld nach 600 Schritten des Kernels {@code stufe.comp}, in einem Befehlspuffer auf der Karte,</li>
 * <li>die Abweichung zu Java in float-Schritten, Zelle für Zelle,</li>
 * <li>Gerät, Warteschlangen, Durchgänge, Sperren, Zeit und Speicher dieses Laufs.</li>
 * </ol>
 * Aufruf: {@code java com.dan.core.gpu.demo.GpuBild [datei.png]}; Vorgabe {@code docs/status/P2_Gpu.png}.
 */
public final class GpuBild {
    private static final Color GRUND = new Color(0x14181D), TAFEL = new Color(0x1B2128), TEXT = new Color(0xD8DEE6), MATT = new Color(0x7F8B99), LINIE = new Color(0x39424D),
            WASSER = new Color(0x3FA7D6), SAND = new Color(0xD9B26F), ROT = new Color(0xE0645C), GRUEN = new Color(0x6BC48A);
    private static final int NX = 360, NY = 240, SCHRITTE = 600;
    private static final float K = 0.2f;

    public static void main(String[] args) throws Exception {
        File ziel = new File(args.length > 0 ? args[0] : "docs/status/P2_Gpu.png");
        float[] anfang = new float[NX * NY];
        for (int j = 0; j < NY; j++)
            for (int i = 0; i < NX; i++) {
                double x = i / (double) NX, y = j / (double) NY, v = 0.15 * Math.sin(9 * x) * Math.cos(7 * y);
                if (Math.hypot(x - 0.3, (y - 0.45) * NY / NX) < 0.11) v += 1;                          // eine Scheibe
                if (Math.abs(x - 0.68) < 0.12 && Math.abs(y - 0.5) < 0.07 || Math.abs(x - 0.68) < 0.03 && Math.abs(y - 0.5) < 0.3) v += 0.8;   // ein Kreuz
                anfang[i + NX * j] = (float) v;
            }
        // Java, in float und in derselben Reihenfolge wie der Kernel
        float[] jv = anfang.clone(), jb = new float[anfang.length];
        for (int s = 0; s < SCHRITTE; s++) {
            for (int j = 0; j < NY; j++)
                for (int i = 0; i < NX; i++) {
                    float c = jv[i + NX * j];
                    float ost = jv[Math.min(i + 1, NX - 1) + NX * j] + jv[Math.max(i - 1, 0) + NX * j];
                    float nord = jv[i + NX * Math.min(j + 1, NY - 1)] + jv[i + NX * Math.max(j - 1, 0)];
                    float summe = ost + nord, vier = 4.0f * c, d = summe - vier, kd = K * d;
                    jb[i + NX * j] = c + kd;
                }
            float[] h = jv;
            jv = jb;
            jb = h;
        }
        // Karte
        float[] karte;
        String[] zeilen;
        try (Geraet g = new Geraet()) {
            Konto konto = g.konto("GpuBild", 16L << 20);
            try (Kernel k = Kernel.lade(g, "/com/dan/core/gpu/demo/stufe.comp", 12, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER); Stoppuhr uhr = new Stoppuhr(g, g.rechnen, 2);
                 Puffer a = Puffer.speicher(g, konto, "A", 4L * anfang.length); Puffer b = Puffer.speicher(g, konto, "B", 4L * anfang.length); MemoryStack s = stackPush()) {
                a.schreibe(anfang);
                ByteBuffer p = s.malloc(12).putInt(0, NX).putInt(4, NY).putFloat(8, K);
                Ablauf ab = new Ablauf(g);
                for (int st = 0; st < SCHRITTE; st++)
                    ab.rechne("stufe", k, st % 2 == 0 ? k.satz(a, b) : k.satz(b, a), p, Kernel.gruppen(NX * NY, 64), 1, 1, new Mittel[] {st % 2 == 0 ? a : b}, new Mittel[] {st % 2 == 0 ? b : a});
                long wand = g.rechnen.einmal(c -> {
                    uhr.runde(c);
                    uhr.start(c, "lauf");
                    ab.aufnehmen(c, null);
                    uhr.stopp(c, "lauf");
                });
                uhr.sammle(true);
                karte = (SCHRITTE % 2 == 0 ? a : b).floats(anfang.length);
                double ms = uhr.verfuegbar ? uhr.letzte("lauf") : wand * 1e-6;
                Geraet.Faehigkeiten f = g.kann;
                zeilen = new String[] {
                        g.name + ", Vulkan " + Geraet.version(g.apiVersion) + (f.software() ? " (rechnet auf dem Prozessor)" : ""),
                        "Warteschlangen: " + g.grafik + (f.eigeneRechenSchlange() ? ", " + g.rechnen : ", Rechnen auf derselben") + (f.eigeneUebertragung() ? ", " + g.uebertragung
                                : ", Übertragung auf derselben"),
                        "Strahlabfrage " + ja(f.strahlabfrage()) + " · float16 " + ja(f.float16()) + " · Untergruppen " + f.untergruppeMin() + "–" + f.untergruppeMax() + " · Zeitstempel "
                                + ja(f.zeitstempel()),
                        String.format(Locale.GERMAN, "Größte Zuteilung %s, größter Speicherpuffer %s", Geraet.mb(g.groessteZuteilung), Geraet.mb(g.groessterPuffer)),
                        "",
                        String.format(Locale.GERMAN, "%d Durchgänge in einem Befehlspuffer, %d Sperrbefehle (%d Puffer-Sperren)", ab.durchgaenge(), ab.sperren(), ab.gesperrt()),
                        String.format(Locale.GERMAN, "Zeit auf der Karte %.1f ms = %.2f ns je Zellschritt", ms, ms * 1e6 / ((double) NX * NY * SCHRITTE)),
                        "Speicherkonto: " + konto + ", Spitze " + Geraet.mb(konto.spitze()),
                        "Kernel: stufe.comp, " + (uhr.verfuegbar ? "Zeit aus Zeitstempeln der Karte" : "Zeit von der Wanduhr")
                };
            }
        }
        double groesste = 0;
        float[] abw = new float[karte.length];
        for (int i = 0; i < karte.length; i++) {
            abw[i] = Math.abs(karte[i] - jv[i]) / Math.ulp(jv[i]);
            groesste = Math.max(groesste, abw[i]);
        }

        int rand = 24, kopf = 40, tw = 2 * NX + 28, th = 2 * NY + 78, w = 2 * tw + 3 * rand, h = 2 * th + 3 * rand + kopf;
        BufferedImage bild = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = bild.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(GRUND);
        g2.fillRect(0, 0, w, h);
        g2.setColor(TEXT);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g2.drawString("com.dan.core.gpu " + Core.VERSION + " – Status P2: Gerät, Speicher, Kernel, Ablauf, Zeiten, Felder auf der Karte", rand, 34);
        int y0 = kopf + rand;
        feld(tafel(g2, rand, y0, tw, th, "Anfang: " + NX + " × " + NY + " Zellen"), anfang, -0.15f, 1.15f, false);
        feld(tafel(g2, 2 * rand + tw, y0, tw, th, "Nach " + SCHRITTE + " Schritten des Kernels auf der Karte (Ausgleich mit fünf Nachbarn, k = 0,2)"), karte, -0.15f, 1.15f, false);
        Graphics2D t3 = tafel(g2, rand, y0 + th + rand, tw, th, "Abweichung Karte – Java in float-Schritten, Zelle für Zelle");
        feld(t3, abw, 0, 1, true);
        t3.setColor(groesste == 0 ? GRUEN : ROT);
        t3.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        t3.drawString(String.format(Locale.GERMAN, "größte Abweichung: %.0f float-Schritte (Grenze 0: nur +, −, · mit „precise“)", groesste), 14, 2 * NY + 62);
        Graphics2D t4 = tafel(g2, 2 * rand + tw, y0 + th + rand, tw, th, "Dieser Lauf");
        t4.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (int i = 0; i < zeilen.length; i++) {
            t4.setColor(i < 4 ? TEXT : MATT);
            t4.drawString(zeilen[i], 14, 62 + 26 * i);
        }
        g2.dispose();
        if (ziel.getParentFile() != null) ziel.getParentFile().mkdirs();
        ImageIO.write(bild, "png", ziel);
        System.out.println("Status-Bild: " + ziel.getAbsolutePath());
        System.out.println(String.format(Locale.GERMAN, "Größte Abweichung Karte – Java: %.0f float-Schritte", groesste));
    }

    private static String ja(boolean b) { return b ? "ja" : "nein"; }

    /** Zeichnet den Rahmen einer Tafel und gibt eine Fläche zurück, deren Ursprung links oben in der Tafel liegt. */
    private static Graphics2D tafel(Graphics2D g, int x, int y, int w, int h, String titel) {
        g.setColor(TAFEL);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(LINIE);
        g.drawRoundRect(x, y, w, h, 10, 10);
        Graphics2D t = (Graphics2D) g.create(x, y, w, h);
        t.setColor(TEXT);
        t.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        t.drawString(titel, 14, 24);
        return t;
    }

    /** Das Feld, jede Zelle zwei Bildpunkte groß; {@code fehler}: null ist dunkel, alles darüber rot. */
    private static void feld(Graphics2D g, float[] f, float von, float bis, boolean fehler) {
        BufferedImage b = new BufferedImage(NX, NY, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < NY; j++)
            for (int i = 0; i < NX; i++) {
                double v = Math.max(0, Math.min(1, (f[i + NX * j] - von) / (bis - von)));
                Color c = fehler ? f[i + NX * j] == 0 ? new Color(0x20303A) : ROT : v < 0.5 ? misch(new Color(0x123A52), WASSER, v * 2) : misch(WASSER, SAND, v * 2 - 2 + 1);
                b.setRGB(i, j, c.getRGB());
            }
        g.drawImage(b, 14, 36, 2 * NX, 2 * NY, null);
        g.setColor(LINIE);
        g.drawRect(14, 36, 2 * NX, 2 * NY);
    }

    private static Color misch(Color a, Color b, double v) {
        v = Math.max(0, Math.min(1, v));
        return new Color((int) (a.getRed() + v * (b.getRed() - a.getRed())), (int) (a.getGreen() + v * (b.getGreen() - a.getGreen())), (int) (a.getBlue() + v * (b.getBlue() - a.getBlue())));
    }

    private GpuBild() { }
}
