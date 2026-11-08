package com.dan.gertsch;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Malt ein {@link Dickicht} nach der Malweise von Franz Gertsch, auf dem Prozessor:
 * <ol>
 * <li><b>Fleck statt Blatt:</b> jedes Blatt ein flacher Fleck mit harter Kante, in seiner Lage im Raum projiziert (ein
 * waagerechtes Blatt wird von der Seite ein waagerecht gestreckter Fleck).</li>
 * <li><b>Größenregel:</b> kein Fleck kleiner als {@link #fleck} der Bildbreite. Blätter, die kleiner erscheinen, werden
 * flächentreu zusammengefasst: Von k Blättern bleibt eines (nach seinem Zufall), √k-mal so groß. So bleibt das Korn bis
 * in die Tiefe gleich, und das Laub deckt dort so viel wie vorher.</li>
 * <li><b>Licht je Fleck:</b> Sonne mit Schatten (die Lichtflecken), durchscheinend von hinten, Himmel nach der
 * Blattfläche darüber ({@link Licht}); einmal in der Mitte des Flecks gerechnet.</li>
 * <li><b>Belichtung nach der Vorlage:</b> so, dass der Median von L* im Bild dem der Vorlage gleicht.</li>
 * <li><b>Tonstufen:</b> jeder Fleck in der Farbe seiner Stufe ({@link Palette}); bunte Flecken weit weg vom herrschenden
 * Farbton (besonnte Streu, trockenes Laub) als Akzent in der Farbe der Akzente der Vorlage.</li>
 * <li><b>Boden</b> je Bildpunkt, damit die Lichtflecken am Boden die Form der Lücken im Laub haben; darüber, wo nichts
 * steht, der dunkle Grund (Stufe 0).</li>
 * </ol>
 * Gezeichnet wird von hinten nach vorn mit Java2D, einmal ohne Glättung (Nummer je Bildpunkt, für die Belichtung und die
 * Ansichten), einmal mit (das Bild). Der Kern braucht nur Java.
 */
public final class Malweise {
    /** Qualitätsstufen: Breite des Bilds. */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    public Quality quality = Quality.HIGH;
    /** Die Vorlage: Tonstufen, Median der Helligkeit, Farbton und Akzente. */
    public Vorlage vorlage = Vorlagen.sommer();
    /** Kleinste Fleckgröße als Anteil der Bildbreite (Durchmesser des Blatts ohne Verkürzung). */
    public double fleck = 0.003;
    /** Heller Saum an den Blatträndern in Bildpunkten (0 = keiner). */
    public double saum = 0;
    /** Akzente zeigen; Belichtung nach dem Median der Vorlage (sonst {@link #belichtung} fest). */
    public boolean akzente = true, belichtungNachVorlage = true;
    public double belichtung = 1;

    /**
     * Wie die Helligkeiten der Szene auf die Tonwerte kommen: {@code MEDIAN} linear mit der Belichtung (Sonne und Schatten
     * bleiben so weit auseinander, wie sie sind: das Bild zerfällt in Hell und Dunkel), {@code VORLAGE} über eine
     * steigende Kurve, nach der das Bild die Verteilung von L* der Vorlage bekommt (Gertsch verteilte seine Tonwerte
     * gleichmäßiger, als das Licht es tut) [G].
     */
    public enum Tonwerte { MEDIAN, VORLAGE }

    public Tonwerte tonwerte = Tonwerte.VORLAGE;
    /** Gewicht der Farbe bei der Wahl der Stufe (ΔL² + g·(Δa² + Δb²)). */
    public double farbGewicht = 0.12;
    /**
     * Ein Fleck wird Akzent, wenn er so bunt ist (C*) und sein Farbton so weit vom herrschenden der Vorlage abweicht
     * (Grad) [G]. Weiter als in der {@link Messung} (60°): Besonnte Streu liegt um 60° bis 70°, gemalt bekommt sie die
     * Farbe der Akzente der Vorlage und zählt dann auch dort.
     */
    public double akzentBuntheit = 12, akzentWinkel = 40;
    /** Stämme und Äste: Länge eines Stücks im Bild (px) und Breite eines Streifens (px). */
    public double stueckPx = 9, streifenPx = 3;
    /** Anteil des Sonnenlichts, der durch ein Blatt kommt [G]; seine Farbe ist gelber. */
    public double durchscheinen = 0.5;
    /** Breite durch Höhe (wie die Vorlage: 2000 × 1328). */
    public double seitenverhaeltnis = 2000 / 1328.0;
    /** Auch das Bild ohne Stufen malen (Licht wie ein Foto mit derselben Belichtung). */
    public boolean mitLicht;

    public int breite() {
        return switch (quality) { case LOW -> 640; case MEDIUM -> 1000; case HIGH -> 1600; case ULTRA -> 2400; };
    }

    /** Ergebnis: Bilder, Nummer je Bildpunkt, Stufe und Faktor je Fleck, Kennzahlen. */
    public static final class Ergebnis {
        /** Gemalt (in Stufen) und, mit {@link #mitLicht}, ohne Stufen. */
        public BufferedImage bild, licht;
        /** Je Bildpunkt die Nummer des Flecks, −1 Boden, −2 nichts (Grund). */
        public int[] id;
        /** Je Fleck: Stufe (−1 Akzent), wie viele Blätter er vertritt, Art (0 Blatt, 1 Viereck, 2 Lichtfleck auf dem Stamm). */
        public int[] stufe;
        public float[] faktor;
        public byte[] art;
        public int breite, hoehe, flecken;
        /** Blätter im Bild vor dem Zusammenfassen, davon gezeichnet. */
        public long blaetter, gezeichnet;
        /** Belichtung (Faktor), kleinste Fleckgröße eines Blatts (px), Blattfläche vor und nach dem Zusammenfassen (px²), Sekunden. */
        public double belichtung, kleinsterFleck = Double.MAX_VALUE, flaecheVorher, flaecheNachher, sekunden;
    }

    /** Einheitsblatt: spitz an beiden Enden, Längsachse t von −1 bis 1, Breite s von −1 bis 1. */
    static final Path2D.Float BLATT = new Path2D.Float();
    /** Einheitskreis für die Lichtflecken auf den Stämmen. */
    static final Shape RUND = new java.awt.geom.Ellipse2D.Float(-1, -1, 2, 2);
    /** Fläche des Einheitsblatts. */
    static final double BLATT_FLAECHE;

    static {
        BLATT.moveTo(0, 1);
        BLATT.quadTo(2, 0.1, 0, -1);
        BLATT.quadTo(-2, 0.1, 0, 1);
        BLATT.closePath();
        // Fläche: zwei quadratische Bögen, x(t) = 4t(1 − t) je Seite, numerisch über y; genau 8/3
        double a = 0;
        int n = 2000;
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n, x = 4 * t * (1 - t);
            double dy = 2 * (1 - t) * (0.1 - 1) + 2 * t * (-1 - 0.1);   // dy/dt
            a += 2 * x * Math.abs(dy) / n;
        }
        BLATT_FLAECHE = a;
    }

    /** Wachsende Listen der Flecken. */
    private static final class Liste {
        int n;
        float[] tiefe = new float[1 << 16], geo = new float[8 << 16], rgb = new float[3 << 16], faktor = new float[1 << 16];
        byte[] art = new byte[1 << 16];

        int neu(byte a, float t) {
            if (n == tiefe.length) {
                int m = n * 2;
                tiefe = Arrays.copyOf(tiefe, m);
                geo = Arrays.copyOf(geo, 8 * m);
                rgb = Arrays.copyOf(rgb, 3 * m);
                faktor = Arrays.copyOf(faktor, m);
                art = Arrays.copyOf(art, m);
            }
            tiefe[n] = t;
            art[n] = a;
            faktor[n] = 1;
            return n++;
        }
    }

    private Kamera k;
    private Licht l;
    private Liste p;
    private final double[] c = new double[3], q = new double[3], s = new double[3];

    /** Malt das Dickicht. Die Kamera bekommt Breite und Höhe nach {@link #quality} und {@link #seitenverhaeltnis}. */
    public Ergebnis malen(Dickicht d, Licht licht, Kamera kamera) {
        long t0 = System.nanoTime();
        int w = breite(), h = (int) Math.round(w / seitenverhaeltnis);
        kamera.breite = w;
        kamera.hoehe = h;
        kamera.stellen();
        k = kamera;
        l = licht;
        p = new Liste();
        Ergebnis e = new Ergebnis();
        e.breite = w;
        e.hoehe = h;
        double f = k.brennweite(), ziel = fleck * w;

        // 1 Flecken sammeln
        Dickicht.Blatt b = new Dickicht.Blatt();
        for (Dickicht.Zweig z : d.zweige) {
            if (!k.projizieren(z.x, z.y, z.z, c)) continue;
            double rpx = f * z.radius / c[2] + 40;
            if (c[0] < -rpx || c[0] > w + rpx || c[1] < -rpx || c[1] > h + rpx) continue;
            double blattPx = f * z.laenge * Math.sqrt(z.schmal) / c[2];
            int kf = blattPx >= ziel ? 1 : (int) Math.ceil(Math.pow(ziel / blattPx, 2));
            e.blaetter += z.blaetter;
            e.flaecheVorher += z.blaetter * BLATT_FLAECHE * 0.25 * blattPx * blattPx;
            for (int i = 0; i < z.blaetter; i++) {
                if (kf > 1 && z.zufall(i) * kf >= 1) continue;
                z.blatt(i, b);
                blatt(b, kf, ziel, e);
            }
        }
        for (Dickicht.Glied g : d.glieder) glied(g);
        for (Dickicht.Halm hm : d.halmListe) halm(hm);
        int n = p.n;
        e.flecken = n;

        // 2 Boden je Bildpunkt: Licht (linear) und Helligkeit; −1 = kein Boden
        float[] boden = new float[3 * w * h];
        boolean[] hatBoden = new boolean[w * h];
        double[] r = new double[3];
        double lichtBoden = Math.max(0, l.zurSonne[1]);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                k.strahl(x + 0.5, y + 0.5, r);
                if (r[1] > -1e-4) continue;
                double t = -k.auge[1] / r[1], gx = k.auge[0] + r[0] * t, gz = k.auge[2] + r[2] * t;
                if (gz > d.fern * 1.3) continue;
                double vis = l.sonne(gx, 0.01, gz), him = l.himmel(gx, 0.05, gz);
                int o = y * w + x;
                hatBoden[o] = true;
                for (int i = 0; i < 3; i++) boden[3 * o + i] = (float) (d.boden[i] * (l.sonneFarbe[i] * lichtBoden * vis + l.himmelFarbe[i] * him));
            }

        // 3 von hinten nach vorn ordnen
        long[] ord = new long[n];
        for (int i = 0; i < n; i++) ord[i] = ((long) Float.floatToIntBits(p.tiefe[i]) << 32) | i;
        Arrays.sort(ord);

        // 4 Nummer je Bildpunkt (ohne Glättung)
        BufferedImage idBild = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D gi = idBild.createGraphics();
        gi.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        Path2D.Float form = new Path2D.Float();
        for (int j = n - 1; j >= 0; j--) {
            int i = (int) ord[j];
            gi.setColor(new Color(i + 1));
            gi.fill(form(i, form));
        }
        gi.dispose();
        int[] roh = ((DataBufferInt) idBild.getRaster().getDataBuffer()).getData();
        e.id = new int[w * h];
        for (int o = 0; o < w * h; o++) e.id[o] = (roh[o] & 0xFFFFFF) - 1 >= 0 ? (roh[o] & 0xFFFFFF) - 1 : hatBoden[o] ? -1 : -2;

        // 5 Belichtung: Median von Y im Bild auf das Y der Vorlage
        float[] yPix = new float[w * h];
        for (int o = 0; o < w * h; o++) {
            int i = e.id[o];
            yPix[o] = i >= 0 ? (float) lum(p.rgb, 3 * i) : i == -1 ? (float) lum(boden, 3 * o) : 0f;
        }
        Arrays.sort(yPix);
        double yMed = yPix[yPix.length / 2], l50 = vorlage.l50, yZiel = l50 > 8 ? Math.pow((l50 + 16) / 116, 3) : l50 / 903.3;
        double kb = belichtungNachVorlage && yMed > 1e-9 ? yZiel / yMed : belichtung;
        e.belichtung = kb;
        boolean angleich = tonwerte == Tonwerte.VORLAGE;

        // 6 Farben je Fleck
        Palette pal = vorlage.palette;
        int[] farbe = new int[n], saumFarbe = new int[n], lichtFarbe = mitLicht ? new int[n] : null;
        e.stufe = new int[n];
        float[] hell = new float[n];
        double[] lab = new double[3];
        for (int i = 0; i < n; i++) {
            Lab.vonLinear(kb * p.rgb[3 * i], kb * p.rgb[3 * i + 1], kb * p.rgb[3 * i + 2], lab);
            if (angleich) lab[0] = angleichen(yPix, lum(p.rgb, 3 * i));
            e.stufe[i] = stufe(lab, pal, true);
            hell[i] = (float) lab[0];
            if (lichtFarbe != null) lichtFarbe[i] = srgb(kb, p.rgb, 3 * i);
        }
        // Boden je Bildpunkt in Stufen (der Boden selbst ist kein Akzent, nur die Streu darauf); −1 = Grund (Stufe 0)
        int[] bodenStufe = new int[w * h];
        for (int o = 0; o < w * h; o++) {
            if (!hatBoden[o]) { bodenStufe[o] = 0; continue; }
            Lab.vonLinear(kb * boden[3 * o], kb * boden[3 * o + 1], kb * boden[3 * o + 2], lab);
            if (angleich) lab[0] = angleichen(yPix, lum(boden, 3 * o));
            bodenStufe[o] = stufe(lab, pal, false);
        }
        // herrschender Farbton des gemalten Bilds (ohne Akzente): der Abstand der Akzente richtet sich nach ihm
        double sa = 0, sb = 0;
        for (int o = 0; o < w * h; o++) {
            int i = e.id[o], st = i >= 0 ? e.stufe[i] : bodenStufe[o];
            if (st < 0) continue;
            sa += pal.lab[st][1];
            sb += pal.lab[st][2];
        }
        double herrschend = sa == 0 && sb == 0 ? vorlage.farbton : Lab.farbton(sa, sb);
        for (int i = 0; i < n; i++) {
            int st = e.stufe[i];
            lab[0] = hell[i];
            farbe[i] = st >= 0 ? pal.rgb(st) : akzent(lab, herrschend, 0);
            saumFarbe[i] = st >= 0 ? pal.rgb(Math.min(pal.size() - 1, st + 1)) : akzent(lab, herrschend, 12);
        }
        e.faktor = Arrays.copyOf(p.faktor, n);
        e.art = Arrays.copyOf(p.art, n);

        // 7 Bild: Boden und Grund je Bildpunkt, dann die Flecken mit Glättung
        e.bild = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] bildPix = ((DataBufferInt) e.bild.getRaster().getDataBuffer()).getData();
        int[] stufenRgb = new int[pal.size()];
        for (int i = 0; i < stufenRgb.length; i++) stufenRgb[i] = pal.rgb(i);
        for (int o = 0; o < w * h; o++) bildPix[o] = stufenRgb[bodenStufe[o]];
        flecken(e.bild, ord, farbe, saum > 0 ? saumFarbe : null, form);
        if (mitLicht) {
            e.licht = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            int[] lp = ((DataBufferInt) e.licht.getRaster().getDataBuffer()).getData();
            for (int o = 0; o < w * h; o++) lp[o] = hatBoden[o] ? srgb(kb, boden, 3 * o) : 0;
            flecken(e.licht, ord, lichtFarbe, null, form);
        }
        e.sekunden = (System.nanoTime() - t0) / 1e9;
        return e;
    }

    private void flecken(BufferedImage im, long[] ord, int[] farbe, int[] saumFarbe, Path2D.Float form) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        BasicStroke stroke = new BasicStroke((float) Math.max(0.1, saum)), fuge = new BasicStroke(0.8f);
        for (int j = ord.length - 1; j >= 0; j--) {
            int i = (int) ord[j];
            Shape sh = form(i, form);
            Color col = new Color(farbe[i]);
            g.setColor(col);
            g.fill(sh);
            if (p.art[i] == 1) {   // Stücke von Stämmen und Halmen: Fugen der Glättung zwischen Nachbarn schließen
                g.setStroke(fuge);
                g.draw(sh);
            } else if (saumFarbe != null && p.art[i] == 0) {
                g.setStroke(stroke);
                g.setColor(new Color(saumFarbe[i]));
                g.draw(sh);
            }
        }
        g.dispose();
    }

    /**
     * Angleich an die Tonstufen: L* für die Helligkeit Y so, dass jede Stufe der Vorlage so viel Fläche bekommt wie
     * dort ({@link Palette#anteil}, nach L* geordnet). Rang von Y unter allen Bildpunkten (Mitte bei Gleichstand), dann das
     * L* der Stufe, in deren Band der Rang fällt. Die Wahl der Stufe danach berücksichtigt noch die Farbe, so dass
     * Stufen ähnlicher Helligkeit (etwa grün und orange in den „Gräsern“) nach dem Farbton getrennt werden.
     */
    private double angleichen(float[] ySortiert, double y) {
        int lo = lower(ySortiert, (float) y), hi = upper(ySortiert, (float) y);
        double q = (lo + hi) / 2.0 / ySortiert.length;
        Palette pal = vorlage.palette;
        double[] an = Stufen.anteile(vorlage);   // Stufe 0 mit dem Anteil des dunklen Grunds (siehe dort)
        double c = 0;
        for (int j = 0; j < pal.size(); j++) {
            c += an[j];
            if (q <= c) return pal.lab[j][0];
        }
        return pal.lab[pal.size() - 1][0];
    }

    private static int lower(float[] a, float v) {
        int lo = 0, hi = a.length;
        while (lo < hi) { int m = (lo + hi) >>> 1; if (a[m] < v) lo = m + 1; else hi = m; }
        return lo;
    }

    private static int upper(float[] a, float v) {
        int lo = 0, hi = a.length;
        while (lo < hi) { int m = (lo + hi) >>> 1; if (a[m] <= v) lo = m + 1; else hi = m; }
        return lo;
    }

    /** Stufe der Farbe, −1 für einen Akzent (nur mit {@code mitAkzent}). */
    private int stufe(double[] lab, Palette pal, boolean mitAkzent) {
        if (akzente && mitAkzent) {
            double cb = Lab.buntheit(lab[1], lab[2]), dh = Math.abs(Lab.farbton(lab[1], lab[2]) - vorlage.farbton) % 360;
            if (cb >= akzentBuntheit && Math.min(dh, 360 - dh) > akzentWinkel) return -1;
        }
        return pal.naechste(lab[0], lab[1], lab[2], farbGewicht);
    }

    /**
     * Farbe eines Akzents: Buntheit und Richtung des Farbtons der Akzente der Vorlage, aber mindestens 68° vom
     * herrschenden Farbton des gemalten Bilds entfernt (die Messung zählt ab 60°; die mittlere Farbe der Akzente der
     * Vorlage liegt oft knapp daran). Helligkeit die des Flecks, höchstens 12 unter und 14 über der der Akzente, plus
     * {@code heller}.
     */
    private int akzent(double[] lab, double herrschend, double heller) {
        double[] a = vorlage.akzentLab;
        double ha = Lab.farbton(a[1], a[2]), d = ha - herrschend;
        d = ((d + 540) % 360) - 180;
        double h = Math.toRadians(herrschend + Math.signum(d == 0 ? -1 : d) * Math.max(Math.abs(d), 68));
        double cb = Math.max(20, Lab.buntheit(a[1], a[2]));
        double ll = Math.max(a[0] - 12, Math.min(a[0] + 14, lab[0])) + heller;
        return Lab.zuRgb(ll, cb * Math.cos(h), cb * Math.sin(h));
    }

    static double lum(float[] rgb, int o) { return 0.2126 * rgb[o] + 0.7152 * rgb[o + 1] + 0.0722 * rgb[o + 2]; }

    static int srgb(double kb, float[] rgb, int o) {
        return (Lab.srgb8(kb * rgb[o]) << 16) | (Lab.srgb8(kb * rgb[o + 1]) << 8) | Lab.srgb8(kb * rgb[o + 2]);
    }

    private Shape form(int i, Path2D.Float form) {
        float[] g = p.geo;
        int o = 8 * i;
        if (p.art[i] == 0) return new AffineTransform(g[o + 2], g[o + 3], g[o + 4], g[o + 5], g[o], g[o + 1]).createTransformedShape(BLATT);
        if (p.art[i] == 2) return new AffineTransform(g[o + 2], g[o + 3], g[o + 4], g[o + 5], g[o], g[o + 1]).createTransformedShape(RUND);
        form.reset();
        form.moveTo(g[o], g[o + 1]);
        form.lineTo(g[o + 2], g[o + 3]);
        form.lineTo(g[o + 4], g[o + 5]);
        form.lineTo(g[o + 6], g[o + 7]);
        form.closePath();
        return form;
    }

    // ------------------------------------------------------------------ Flecken bauen

    /** Ein Blatt als Fleck: Größenregel, Projektion seiner Achsen, Licht in der Mitte. */
    private void blatt(Dickicht.Blatt b, int kf, double ziel, Ergebnis e) {
        if (!k.projizieren(b.x, b.y, b.z, c)) return;
        double f = k.brennweite(), blattPx = f * Math.sqrt(b.laenge * b.breite) / c[2];
        double sk = Math.max(Math.sqrt(kf), ziel / blattPx);
        double hl = 0.5 * b.laenge * sk, hw = 0.5 * b.breite * sk;
        // Querachse = n × a
        double sx = b.ny * b.az - b.nz * b.ay, sy = b.nz * b.ax - b.nx * b.az, sz = b.nx * b.ay - b.ny * b.ax;
        if (!k.projizieren(b.x + b.ax * hl, b.y + b.ay * hl, b.z + b.az * hl, q)) return;
        if (!k.projizieren(b.x + sx * hw, b.y + sy * hw, b.z + sz * hw, s)) return;
        double m01 = q[0] - c[0], m11 = q[1] - c[1], m00 = s[0] - c[0], m10 = s[1] - c[1];
        double ext = Math.abs(m00) + Math.abs(m01) + Math.abs(m10) + Math.abs(m11);
        if (c[0] < -ext || c[0] > k.breite + ext || c[1] < -ext || c[1] > k.hoehe + ext) return;
        int i = p.neu((byte) 0, (float) c[2]);
        float[] g = p.geo;
        g[8 * i] = (float) c[0];
        g[8 * i + 1] = (float) c[1];
        g[8 * i + 2] = (float) m00;
        g[8 * i + 3] = (float) m10;
        g[8 * i + 4] = (float) m01;
        g[8 * i + 5] = (float) m11;
        p.faktor[i] = (float) (sk * sk);
        beleuchten(b.x, b.y, b.z, b.nx, b.ny, b.nz, b.r, b.g, b.b, true, i, Double.NaN);
        if (e != null) {
            e.gezeichnet++;
            e.kleinsterFleck = Math.min(e.kleinsterFleck, blattPx * sk);
            e.flaecheNachher += BLATT_FLAECHE * 0.25 * Math.pow(blattPx * sk, 2);
        }
    }

    /**
     * Stamm oder Ast: in Stücke von etwa {@link #stueckPx} Länge im Bild geteilt, Stämme dazu in Streifen von etwa
     * {@link #streifenPx} Breite, je mit der Normale des Zylinders in seiner Mitte. So zerfällt das Licht auf dem Stamm in
     * Flecken (Gertschs Lichtflecken auf den Stämmen), ohne dass die Stücke als Kästchen hervortreten.
     */
    private void glied(Dickicht.Glied g) {
        if (!k.projizieren(g.x0, g.y0, g.z0, q) || !k.projizieren(g.x1, g.y1, g.z1, s)) return;
        double lenPx = Math.hypot(s[0] - q[0], s[1] - q[1]), rpx = k.brennweite() * Math.max(g.r0, g.r1) / Math.min(q[2], s[2]);
        double minX = Math.min(q[0], s[0]) - rpx, maxX = Math.max(q[0], s[0]) + rpx, minY = Math.min(q[1], s[1]) - rpx, maxY = Math.max(q[1], s[1]) + rpx;
        if (maxX < 0 || minX > k.breite || maxY < 0 || minY > k.hoehe) return;
        int stuecke = Math.max(1, Math.min(60, (int) Math.ceil(lenPx / stueckPx)));
        int streifen = g.stamm ? Math.max(1, Math.min(7, (int) Math.round(2 * rpx / streifenPx))) : 1;
        for (int m = 0; m < stuecke; m++) {
            double t0 = m / (double) stuecke, t1 = (m + 1) / (double) stuecke;
            stueck(g, g.x0 + (g.x1 - g.x0) * t0, g.y0 + (g.y1 - g.y0) * t0, g.z0 + (g.z1 - g.z0) * t0, g.r0 + (g.r1 - g.r0) * t0,
                    g.x0 + (g.x1 - g.x0) * t1, g.y0 + (g.y1 - g.y0) * t1, g.z0 + (g.z1 - g.z0) * t1, g.r0 + (g.r1 - g.r0) * t1, streifen);
        }
    }

    private void stueck(Dickicht.Glied g, double x0, double y0, double z0, double r0, double x1, double y1, double z1, double r1, int streifen) {
        double ax = x1 - x0, ay = y1 - y0, az = z1 - z0, len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-6) return;
        ax /= len; ay /= len; az /= len;
        double mx = (x0 + x1) / 2, my = (y0 + y1) / 2, mz = (z0 + z1) / 2;
        double vx = k.auge[0] - mx, vy = k.auge[1] - my, vz = k.auge[2] - mz, vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= vl; vy /= vl; vz /= vl;
        double dot = vx * ax + vy * ay + vz * az, nx = vx - dot * ax, ny = vy - dot * ay, nz = vz - dot * az, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-6) return;
        nx /= nl; ny /= nl; nz /= nl;
        double sx = ay * nz - az * ny, sy = az * nx - ax * nz, sz = ax * ny - ay * nx;
        if (!k.projizieren(mx, my, mz, c)) return;
        double rmid = (r0 + r1) / 2;
        double[] p0 = new double[3], p1 = new double[3], p2 = new double[3], p3 = new double[3];
        for (int j = 0; j < streifen; j++) {
            // gleiche Winkel auf dem Zylinder: Rand bei sin(θ), Normale in der Mitte des Streifens
            double tha = -Math.PI / 2 + Math.PI * j / streifen, thb = -Math.PI / 2 + Math.PI * (j + 1) / streifen, thm = (tha + thb) / 2;
            double ea = Math.sin(tha), eb = Math.sin(thb), fm = Math.sin(thm), cn = Math.cos(thm);
            if (!k.projizieren(x0 + sx * r0 * ea, y0 + sy * r0 * ea, z0 + sz * r0 * ea, p0)) return;
            if (!k.projizieren(x0 + sx * r0 * eb, y0 + sy * r0 * eb, z0 + sz * r0 * eb, p1)) return;
            if (!k.projizieren(x1 + sx * r1 * eb, y1 + sy * r1 * eb, z1 + sz * r1 * eb, p2)) return;
            if (!k.projizieren(x1 + sx * r1 * ea, y1 + sy * r1 * ea, z1 + sz * r1 * ea, p3)) return;
            int i = p.neu((byte) 1, (float) c[2]);
            float[] geo = p.geo;
            geo[8 * i] = (float) p0[0]; geo[8 * i + 1] = (float) p0[1];
            geo[8 * i + 2] = (float) p1[0]; geo[8 * i + 3] = (float) p1[1];
            geo[8 * i + 4] = (float) p2[0]; geo[8 * i + 5] = (float) p2[1];
            geo[8 * i + 6] = (float) p3[0]; geo[8 * i + 7] = (float) p3[1];
            double nnx = cn * nx + fm * sx, nny = cn * ny + fm * sy, nnz = cn * nz + fm * sz;
            double px = mx + nnx * rmid, py = my + nny * rmid, pz = mz + nnz * rmid;
            if (!g.stamm) { beleuchten(px, py, pz, nnx, nny, nnz, g.r, g.g, g.b, false, i, Double.NaN); continue; }
            // Stamm: der Streifen im Schatten, das Sonnenlicht als runder Fleck darüber (überlappend, leicht unregelmäßig)
            double vis = l.sonne(px, py, pz);
            beleuchten(px, py, pz, nnx, nny, nnz, g.r, g.g, g.b, false, i, 0);
            if (vis < 0.3 || nnx * l.zurSonne[0] + nny * l.zurSonne[1] + nnz * l.zurSonne[2] <= 0.05) continue;
            int bl = p.neu((byte) 2, (float) (c[2] - 0.003));
            double hax = ((p2[0] + p3[0]) - (p0[0] + p1[0])) / 4, hay = ((p2[1] + p3[1]) - (p0[1] + p1[1])) / 4;
            double hbx = ((p1[0] + p2[0]) - (p0[0] + p3[0])) / 4, hby = ((p1[1] + p2[1]) - (p0[1] + p3[1])) / 4;
            long hsh = Dickicht.mix(Double.doubleToLongBits(px * 7.1 + py * 13.3 + pz * 3.7));
            // Größe 1,15–1,6 quer und 1,3–2,1 längs (überlappend, damit besonnte Streifen zu Flächen verschmelzen), Lage ±0,35
            double j1 = 1.15 + 0.45 * ((hsh >>> 11) & 1023) / 1023.0, j2 = 1.3 + 0.8 * ((hsh >>> 21) & 1023) / 1023.0;
            double ja = 0.7 * (((hsh >>> 31) & 1023) / 1023.0 - 0.5), jb = 0.7 * (((hsh >>> 41) & 1023) / 1023.0 - 0.5);
            geo[8 * bl] = (float) ((p0[0] + p1[0] + p2[0] + p3[0]) / 4 + hax * ja + hbx * jb);
            geo[8 * bl + 1] = (float) ((p0[1] + p1[1] + p2[1] + p3[1]) / 4 + hay * ja + hby * jb);
            geo[8 * bl + 2] = (float) (hbx * j1);
            geo[8 * bl + 3] = (float) (hby * j1);
            geo[8 * bl + 4] = (float) (hax * j2);
            geo[8 * bl + 5] = (float) (hay * j2);
            beleuchten(px, py, pz, nnx, nny, nnz, g.r, g.g, g.b, false, bl, vis);
        }
    }

    /** Ein Halm: vier Abschnitte als Band zum Blick, spitz zulaufend, durchscheinend. */
    private void halm(Dickicht.Halm hm) {
        float[] pt = hm.p;
        double[] a0 = new double[3], a1 = new double[3], b0 = new double[3], b1 = new double[3];
        for (int j = 0; j < 4; j++) {
            double x0 = pt[3 * j], y0 = pt[3 * j + 1], z0 = pt[3 * j + 2], x1 = pt[3 * j + 3], y1 = pt[3 * j + 4], z1 = pt[3 * j + 5];
            double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dl < 1e-6) continue;
            dx /= dl; dy /= dl; dz /= dl;
            double mx = (x0 + x1) / 2, my = (y0 + y1) / 2, mz = (z0 + z1) / 2;
            double vx = k.auge[0] - mx, vy = k.auge[1] - my, vz = k.auge[2] - mz, vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
            vx /= vl; vy /= vl; vz /= vl;
            double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6) continue;
            sx /= sl; sy /= sl; sz /= sl;
            double w0 = hm.breite * (1 - j / 4.0) / 2, w1 = hm.breite * (1 - (j + 1) / 4.0) / 2;
            if (!k.projizieren(x0 - sx * w0, y0 - sy * w0, z0 - sz * w0, a0)) return;
            if (!k.projizieren(x0 + sx * w0, y0 + sy * w0, z0 + sz * w0, a1)) return;
            if (!k.projizieren(x1 + sx * w1, y1 + sy * w1, z1 + sz * w1, b1)) return;
            if (!k.projizieren(x1 - sx * w1, y1 - sy * w1, z1 - sz * w1, b0)) return;
            if (a0[0] < -50 && b0[0] < -50 || a0[0] > k.breite + 50 && b0[0] > k.breite + 50 || a0[1] < -50 && b0[1] < -50) continue;
            if (!k.projizieren(mx, my, mz, c)) return;
            int i = p.neu((byte) 1, (float) c[2]);
            float[] g = p.geo;
            g[8 * i] = (float) a0[0]; g[8 * i + 1] = (float) a0[1];
            g[8 * i + 2] = (float) a1[0]; g[8 * i + 3] = (float) a1[1];
            g[8 * i + 4] = (float) b1[0]; g[8 * i + 5] = (float) b1[1];
            g[8 * i + 6] = (float) b0[0]; g[8 * i + 7] = (float) b0[1];
            // Normale: zum Blick, etwas nach oben
            double nx = vx - (vx * dx + vy * dy + vz * dz) * dx, ny = vy - (vx * dx + vy * dy + vz * dz) * dy + 0.3, nz = vz - (vx * dx + vy * dy + vz * dz) * dz;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            beleuchten(mx, my, mz, nx / nl, ny / nl, nz / nl, hm.r, hm.g, hm.b, true, i, Double.NaN);
        }
    }

    /**
     * Licht am Punkt mit Normale n (zweiseitig: die zum Auge gewandte Seite zählt) und Albedo; Blätter und Halme lassen
     * {@link #durchscheinen} des Sonnenlichts durch, gelber. Schreibt das Ergebnis (linear) zu Fleck i.
     */
    private void beleuchten(double x, double y, double z, double nx, double ny, double nz, double ar, double ag, double ab, boolean duenn, int i, double visVorgabe) {
        double vx = k.auge[0] - x, vy = k.auge[1] - y, vz = k.auge[2] - z;
        if (nx * vx + ny * vy + nz * vz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        double[] L = l.zurSonne;
        double vis = Double.isNaN(visVorgabe) ? l.sonne(x, y, z) : visVorgabe;
        double cos = nx * L[0] + ny * L[1] + nz * L[2], him = l.himmel(x, y, z) * (0.55 + 0.45 * Math.max(0, ny));
        double sr, sg, sb;
        if (cos >= 0) { sr = ar * cos; sg = ag * cos; sb = ab * cos; }
        else if (duenn) { double d = -cos * durchscheinen; sr = ar * 1.15 * d; sg = ag * d; sb = ab * 0.5 * d; }
        else { sr = sg = sb = 0; }
        p.rgb[3 * i] = (float) (l.sonneFarbe[0] * sr * vis + l.himmelFarbe[0] * ar * him);
        p.rgb[3 * i + 1] = (float) (l.sonneFarbe[1] * sg * vis + l.himmelFarbe[1] * ag * him);
        p.rgb[3 * i + 2] = (float) (l.sonneFarbe[2] * sb * vis + l.himmelFarbe[2] * ab * him);
    }
}
