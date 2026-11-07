package com.dan.film.demo;

import com.dan.film.Film;
import com.dan.film.Format;
import com.dan.film.Grade;
import com.dan.film.Stock;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.Random;

/**
 * Demo von com.dan.film ohne Grafikkarte (dieselbe Rechnung wie {@code gl/film.frag}, auf dem Prozessor): eine Tafel mit
 * Graukeil (−8 bis +6 Blenden), den 24 Feldern einer Farbtafel und einem Abendhimmel mit Sonne und Laterne, je durch
 * die Filmkurve der App (ACES), Kodak Vision3 250D auf 2383 (Super 35) und Eastman Color 5248 auf 5382 (CinemaScope, Kopierlicht
 * „Abend 1958“) und dieselbe Kopie 68 Jahre später (Gelb und Cyan verblasst), mit Korn und Halation für ein Bild von 1920
 * Bildpunkten Breite. {@code --bild datei.png}; ohne
 * Argument schreibt sie {@code film_demo.png}.
 */
public final class FilmDemo {
    /** Farbtafel (sRGB 8 bit, die üblichen Werte der 24 Felder). */
    static final int[][] CHECKER = {{115, 82, 68}, {194, 150, 130}, {98, 122, 157}, {87, 108, 67}, {133, 128, 177}, {103, 189, 170},
            {214, 126, 44}, {80, 91, 166}, {193, 90, 99}, {94, 60, 108}, {157, 188, 64}, {224, 163, 46},
            {56, 61, 150}, {70, 148, 73}, {175, 54, 60}, {231, 199, 31}, {187, 86, 149}, {8, 133, 161},
            {243, 243, 242}, {200, 200, 200}, {160, 160, 160}, {122, 122, 121}, {85, 85, 85}, {52, 52, 52}};

    static double lin(int v) { double c = v / 255.0; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }

    static double aces(double x) { return Math.max(0, Math.min(1, (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14))); }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        String out = "film_demo.png";
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals("--bild")) out = a[i + 1];
        int cw = 600, ch = 760;
        BufferedImage img = new BufferedImage(4 * cw + 50, ch + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(12, 18, 24));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        // die Szene, linear (vor der Belichtung), cw × ch
        float[][] scene = new float[3][cw * ch];
        for (int y = 0; y < ch; y++)
            for (int x = 0; x < cw; x++) {
                int i = y * cw + x;
                double r, gg, b;
                if (y < 80) {               // Graukeil −8 … +6 Blenden
                    double st = -8 + 14.0 * x / cw;
                    r = gg = b = 0.18 * Math.pow(2, st);
                } else if (y < 380) {       // Farbtafel 6 × 4
                    int cx = x * 6 / cw, cy = (y - 80) * 4 / 300, k = cy * 6 + cx;
                    boolean rand = x % (cw / 6) < 4 || (y - 80) % 75 < 4;
                    if (rand) { r = gg = b = 0.02; } else { r = lin(CHECKER[k][0]); gg = lin(CHECKER[k][1]); b = lin(CHECKER[k][2]); }
                } else {                    // Abendhimmel, Wasser, Sonne und Laterne
                    double v = (y - 380) / 380.0;
                    if (v < 0.55) { double t = v / 0.55; r = 2.2 * (1 - t) + 0.35 * t; gg = 1.0 * (1 - t) + 0.30 * t; b = 0.25 * (1 - t) + 0.45 * t; r *= 0.9; }
                    else { double t = (v - 0.55) / 0.45; r = 0.25 * (1 - t) + 0.02 * t; gg = 0.16 * (1 - t) + 0.025 * t; b = 0.12 * (1 - t) + 0.04 * t; }
                    double ds = Math.hypot(x - 0.62 * cw, y - (380 + 0.50 * 380));
                    if (ds < 9) { r = 4000; gg = 2400; b = 900; }
                    else if (ds < 60) { double k = Math.exp(-ds / 18); r += 18 * k; gg += 9 * k; b += 2 * k; }
                    double dl = Math.hypot(x - 0.2 * cw, y - (380 + 0.75 * 380));
                    if (dl < 3) { r = 900; gg = 600; b = 250; }
                }
                scene[0][i] = (float) r; scene[1][i] = (float) gg; scene[2][i] = (float) b;
            }
        // ACES wie die App
        BufferedImage col0 = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < cw * ch; i++) {
            int r = (int) Math.round(255 * Math.pow(aces(scene[0][i]), 1 / 2.2)), gg = (int) Math.round(255 * Math.pow(aces(scene[1][i]), 1 / 2.2)), b = (int) Math.round(255 * Math.pow(aces(scene[2][i]), 1 / 2.2));
            col0.setRGB(i % cw, i / cw, r << 16 | gg << 8 | b);
        }
        Film v3 = new Film();
        Film ec = new Film();
        ec.negative = Stock.eastmanColor5248();
        ec.print = Stock.print5382();
        ec.format = Format.CINEMASCOPE;
        ec.grade = Grade.ABEND_1958;
        Film alt = new Film();
        alt.negative = ec.negative;
        alt.print = ec.print;
        alt.format = ec.format;
        alt.grade = ec.grade;
        alt.printAgeYears = 68;
        BufferedImage col1 = develop(v3, scene, cw, ch, 1), col2 = develop(ec, scene, cw, ch, 2), col3 = develop(alt, scene, cw, ch, 3);
        BufferedImage[] cols = {col0, col1, col2, col3};
        String[] titles = {"Filmkurve der App (ACES)", "Vision3 250D → 2383 · Super 35", "Eastman Color 5248 → 5382 · CinemaScope · Abend 1958",
                "dieselbe Kopie im Jahr 2026 (68 Jahre, verblasst)"};
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (int k = 0; k < cols.length; k++) {
            g.drawImage(cols[k], 10 + k * (cw + 10), 40, null);
            g.setColor(new Color(230, 236, 240));
            g.drawString(titles[k], 14 + k * (cw + 10), 26);
        }
        g.setColor(new Color(140, 156, 166));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("Graukeil −8 bis +6 Blenden · Farbtafel · Abendhimmel mit Sonne und Laterne (Halation: 5248 mit Ring, anamorphotisch eine Ellipse; Vision3 mit Rückschicht nur ein Schein) · Korn für 1920 Bildpunkte Breite", 14, ch + 56);
        g.dispose();
        File f = new File(out);
        if (f.getAbsoluteFile().getParentFile() != null) f.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("Bild: " + f.getPath() + " · " + v3.describe() + " · " + ec.describe());
    }

    /** Wie film.frag: Halation (Film.halate, wie halation_down.frag und halation.frag), Korn nach Selwyn je Schicht, Kopie, Leinwand. */
    static BufferedImage develop(Film f, float[][] s, int w, int h, long seed) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Stock n = f.negative;
        int outW = 1920;   // Korn und Halation wie in einem Bild von 1920 Bildpunkten Breite (die Spalte ist ein Ausschnitt)
        double[] m = n.matrix();
        double k0 = Math.pow(2, f.exposureStops) / Film.GRAY;
        float[] red = new float[w * h];
        for (int i = 0; i < w * h; i++) red[i] = (float) ((m[0] * s[0][i] + m[1] * s[1][i] + m[2] * s[2][i]) * k0);
        double[] rr = f.halationRadiusPx2(outW);
        float[] hal = f.halation ? Film.halate(red, w, h, rr[0], rr[1], Film.halationTable(n)) : new float[w * h];
        double hs = n.halation() * f.halationGain;
        double ks = Math.sqrt(Film.A48 / f.grainArea(outW)) * f.grainGain;
        Random rnd = new Random(seed);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                double[] hh = f.layerExposure(s[0][i], s[1][i], s[2][i]);
                for (int c = 0; c < 3; c++) hh[c] += hs * n.halationRgb()[c] * hal[i];
                double[] dn = f.negativeDensity(hh);
                double common = rnd.nextGaussian();
                if (f.grain)
                    for (int c = 0; c < 3; c++) {
                        double net = dn[c] - n.curves()[c].dmin();
                        dn[c] += n.rms() / 1000 * n.layerGrain()[c] * ks * Math.sqrt(Math.max(0.05, net)) * (0.88 * rnd.nextGaussian() + 0.47 * common);
                    }
                double[] lin = f.project(f.printDensity(dn));
                int r = (int) Math.round(255 * Film.srgb(lin[0])), gg = (int) Math.round(255 * Film.srgb(lin[1])), bb = (int) Math.round(255 * Film.srgb(lin[2]));
                im.setRGB(x, y, r << 16 | gg << 8 | bb);
            }
        return im;
    }

    private FilmDemo() { }
}
