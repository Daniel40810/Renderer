package com.dan.gertsch;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.TreeSet;

/**
 * Vermisst ein Bild nach der Malweise von Franz Gertsch und liefert eine {@link Vorlage}:
 * <ol>
 * <li><b>Helligkeit:</b> Verteilung von L*, Perzentile, Anteil des dunklen Grunds.</li>
 * <li><b>Tonstufen:</b> k-means in Lab ({@link Palette#kmeans}).</li>
 * <li><b>Farbe:</b> herrschender Farbton als Mittel der Farbvektoren (a*, b*), Akzente als bunte Bildpunkte, deren
 * Farbton mehr als {@link #akzentWinkel} davon abweicht (die orangen Punkte im Grün).</li>
 * <li><b>Flecken:</b> Granulometrie von L* mit Scheiben auf einer Leiter von Radien ({@link Morphologie}): Größen der
 * hellen Flecken aus Öffnungen, der dunklen Lücken aus Schließungen.</li>
 * <li><b>Richtung:</b> Strukturtensor ({@link Struktur}): wie viel der Zeichnung Linien sind und wohin sie laufen.</li>
 * </ol>
 * Alle Einstellungen sind offene Felder. Gleicher {@link #seed}, gleiche Messung.
 */
public final class Messung {
    /** Qualitätsstufen: Breite, auf der gemessen wird, und Stichprobe für k-means. */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Zahl der Tonstufen (k). */
    public int stufen = 6;
    /** L* darunter zählt als dunkler Grund [G]. */
    public double grundL = 20;
    /** Akzent: Buntheit C* mindestens und Abstand im Farbton mindestens (Grad) [G]. */
    public double akzentBuntheit = 15, akzentWinkel = 60;
    /** Größter Radius der Granulometrie als Anteil der Arbeitsbreite [G]; Schritt der Leiter (Faktor je Stufe). */
    public double radiusMax = 0.035, leiterSchritt = 1.25;
    /** Strukturtensor: Glättung vor den Gradienten und Mittelung der Produkte, in Bildpunkten der Arbeitsbreite [G]. */
    public double sigmaD = 1.0, sigmaI = 2.0;

    /** Breite, auf der gemessen wird. */
    public int arbeitsbreite() {
        return switch (quality) { case LOW -> 400; case MEDIUM -> 640; case HIGH -> 1000; case ULTRA -> 1600; };
    }

    /** Höchstens so viele Bildpunkte bestimmen die Tonstufen, bevor alle zwei Schritte mitrechnen. */
    public int stichprobe() {
        return switch (quality) { case LOW -> 10_000; case MEDIUM -> 25_000; case HIGH -> 50_000; case ULTRA -> 100_000; };
    }

    /** Radien der Granulometrie für eine Breite: 1, 2, 3 … wachsend mit {@link #leiterSchritt}, bis {@link #radiusMax}. */
    public int[] leiter(int breite) {
        int max = Math.max(3, (int) Math.round(radiusMax * breite));
        TreeSet<Integer> r = new TreeSet<>();
        for (double x = 1; x < max; x *= leiterSchritt) r.add((int) Math.round(x));
        r.add(max);
        return r.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Liest eine Bilddatei (JPEG, PNG …) und misst sie. */
    public Vorlage messen(File datei) throws IOException {
        BufferedImage im = ImageIO.read(datei);
        if (im == null) {
            byte[] kopf = new byte[12];
            try (java.io.InputStream in = new java.io.FileInputStream(datei)) { in.readNBytes(kopf, 0, 12); }
            boolean webp = new String(kopf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(kopf, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            throw new IOException(datei + (webp ? " ist ein WebP-Bild (gleich welche Endung); ImageIO liest es nicht, bitte als PNG speichern"
                    : ": kein lesbares Bild"));
        }
        Vorlage v = messen(im);
        v.quelle = datei.getName();
        return v;
    }

    public Vorlage messen(BufferedImage im) {
        int w = im.getWidth(), h = im.getHeight();
        return messen(im.getRGB(0, 0, w, h, null, 0, w), w, h);
    }

    /** Misst ARGB-Bildpunkte (zeilenweise, w × h). */
    public Vorlage messen(int[] argb, int w, int h) {
        long t0 = System.nanoTime();
        Bild b = Bild.aus(argb, w, h, arbeitsbreite());
        Vorlage v = new Vorlage();
        v.breite = w;
        v.hoehe = h;
        v.arbeitsbreite = b.w;
        v.qualitaet = quality.name();
        v.stufen = stufen;
        v.seed = seed;
        v.bild = b;
        int n = b.w * b.h;

        // 1 Helligkeit
        float[] sortiert = b.L.clone();
        Arrays.sort(sortiert);
        v.l5 = quantil(sortiert, 0.05);
        v.l50 = quantil(sortiert, 0.50);
        v.l95 = quantil(sortiert, 0.95);
        for (float l : b.L) {
            v.helligkeit[Math.max(0, Math.min(9, (int) (l / 10)))] += 1.0 / n;
            if (l < grundL) v.grund += 1.0 / n;
        }

        // 2 Tonstufen
        v.palette = Palette.kmeans(b, stufen, seed, stichprobe());

        // 3 Farbton und Akzente: Σ(a*, b*) ist die Summe der Einheitsvektoren des Farbtons, gewichtet mit der Buntheit
        double sa = 0, sb = 0, sc = 0;
        for (int i = 0; i < n; i++) {
            sa += b.a[i];
            sb += b.b[i];
            sc += Lab.buntheit(b.a[i], b.b[i]);
        }
        v.farbton = Lab.farbton(sa, sb);
        v.buntheit = sc / n;
        double[] ak = new double[3];
        int na = 0;
        for (int i = 0; i < n; i++) {
            if (Lab.buntheit(b.a[i], b.b[i]) < akzentBuntheit) continue;
            double d = Math.abs(Lab.farbton(b.a[i], b.b[i]) - v.farbton) % 360;
            if (Math.min(d, 360 - d) <= akzentWinkel) continue;
            ak[0] += b.L[i];
            ak[1] += b.a[i];
            ak[2] += b.b[i];
            na++;
        }
        v.akzent = na / (double) n;
        if (na > 0) v.akzentLab = new double[]{ak[0] / na, ak[1] / na, ak[2] / na};

        // 4 Flecken
        int[] r = leiter(b.w);
        double s0 = 0;
        for (float l : b.L) s0 += l;
        double[] auf = Morphologie.summen(b.L, b.w, b.h, r, true), zu = Morphologie.summen(b.L, b.w, b.h, r, false);
        int last = r.length - 1;
        v.durchmesser = new double[r.length];
        v.hell = new double[r.length];
        v.dunkel = new double[r.length];
        double dh = Math.max(1e-9, s0 - auf[last]), dd = Math.max(1e-9, zu[last] - s0);
        for (int i = 0; i < r.length; i++) {
            v.durchmesser[i] = 2.0 * r[i] / b.w;
            v.hell[i] = Math.max(0, Math.min(1, (s0 - auf[i]) / dh));
            v.dunkel[i] = Math.max(0, Math.min(1, (zu[i] - s0) / dd));
        }
        v.hellD50 = 2 * halbwert(r, v.hell) / b.w;
        v.dunkelD50 = 2 * halbwert(r, v.dunkel) / b.w;
        double unten = quantil(sortiert, 0.02), oben = quantil(sortiert, 0.98), sh = 0, sd = 0;
        for (float l : b.L) {
            sh += Math.max(0, l - unten);
            sd += Math.max(0, oben - l);
        }
        v.fleckHell = Math.min(1, (s0 - auf[last]) / Math.max(1e-9, sh));
        v.fleckDunkel = Math.min(1, (zu[last] - s0) / Math.max(1e-9, sd));

        // 5 Richtung
        float[][] t = Struktur.tensor(b.L, b.w, b.h, sigmaD, sigmaI);
        double sg = 0, sganz = 0, sv = 0, c2 = 0, s2 = 0;
        for (int i = 0; i < n; i++) {
            double wi = t[1][i], a = t[0][i];
            v.richtung[Math.min(11, (int) (a / 15))] += wi;
            if (a >= 60 && a <= 120) sv += wi;
            sg += wi;
            sganz += t[2][i];
            c2 += wi * Math.cos(Math.toRadians(2 * a));
            s2 += wi * Math.sin(Math.toRadians(2 * a));
        }
        for (int i = 0; i < 12; i++) v.richtung[i] /= Math.max(1e-12, sg);
        v.senkrecht = sv / Math.max(1e-12, sg);
        v.kohaerenz = sg / Math.max(1e-12, sganz);
        double hr = 0.5 * Math.toDegrees(Math.atan2(s2, c2));
        v.hauptrichtung = hr < 0 ? hr + 180 : hr;

        v.sekunden = (System.nanoTime() - t0) / 1e9;
        return v;
    }

    /** Wert beim Anteil q einer sortierten Reihe (linear zwischen Nachbarn). */
    static double quantil(float[] sortiert, double q) {
        double x = q * (sortiert.length - 1);
        int i = (int) Math.floor(x);
        if (i >= sortiert.length - 1) return sortiert[sortiert.length - 1];
        return sortiert[i] + (x - i) * (sortiert[i + 1] - sortiert[i]);
    }

    /** Radius, bei dem die Summenverteilung F (über die Leiter r, mit F(0) = 0) die Hälfte erreicht, linear dazwischen. */
    static double halbwert(int[] r, double[] f) {
        double r0 = 0, f0 = 0;
        for (int i = 0; i < r.length; i++) {
            if (f[i] >= 0.5) return f[i] - f0 < 1e-12 ? r[i] : r0 + (0.5 - f0) / (f[i] - f0) * (r[i] - r0);
            r0 = r[i];
            f0 = f[i];
        }
        return r[r.length - 1];
    }

    /** Weiße Zylinderhut-Transformation: L* minus Öffnung mit Radius r, die hellen Flecken bis zu dieser Größe. */
    public static float[] lichtflecken(Bild b, int r) {
        float[] o = Morphologie.oeffnen(b.L, b.w, b.h, r);
        for (int i = 0; i < o.length; i++) o[i] = b.L[i] - o[i];
        return o;
    }

    /** Schwarze Zylinderhut-Transformation: Schließung mit Radius r minus L*, die dunklen Lücken bis zu dieser Größe. */
    public static float[] luecken(Bild b, int r) {
        float[] o = Morphologie.schliessen(b.L, b.w, b.h, r);
        for (int i = 0; i < o.length; i++) o[i] -= b.L[i];
        return o;
    }
}
