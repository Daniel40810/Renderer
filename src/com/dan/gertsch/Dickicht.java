package com.dan.gertsch;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Ein Dickicht aus {@link #seed}: Bäume, Jungbäume, Sträucher und dahinter eine dichte Wand aus Gehölz, am Boden Halme
 * und Laubstreu. Kein Himmel: Gertsch malte frontal in den Bestand. Welt mit y nach oben, die Kamera steht vorn bei z = 0
 * und blickt nach +z.
 * <p>
 * Jedes Gehölz hat Stamm (oder mehrere Triebe), Äste und Zweige ({@link Glied}, {@link Zweig}). Die Blätter eines
 * Zweigs liegen nicht im Speicher, {@link Zweig#blatt} erzeugt sie bei Bedarf aus dem seed des Zweigs. Ihre Zahl folgt
 * aus der Blattflächendichte der Krone (m² Blatt je m³ Krone) und der Fläche eines Blatts.
 * <p>
 * Blätter in der Sonne stehen eher waagerecht (planophil, {@link #neigung}); von der Seite gesehen werden sie zu
 * waagerecht gestreckten Flecken. Das passt zur Messung an Gertschs „Sommer“ (Hauptrichtung 179°).
 */
public final class Dickicht {
    public long seed = 1;
    /** Bereich der Gehölze: Breite in x (m, mittig) und Tiefe von {@link #nah} bis {@link #fern} (m). */
    public double breite = 90, nah = 3.5, fern = 60;
    /** Zahl der Bäume, Jungbäume, Sträucher und der Gehölze in der Wand hinten (ab {@link #wandAb}). */
    public int baeume = 22, jungbaeume = 130, straeucher = 380, wand = 300;
    public double wandAb = 30;
    /** Krautschicht vorn: Zahl, bis zur Tiefe (m), Länge ihrer Blätter (m; größer als im Gehölz). */
    public int kraut = 900;
    public double krautBis = 14, krautBlatt = 0.075;
    /**
     * Kronendach über der Kamera und dem Vordergrund (z von −8 bis 16 m): hohe Bäume, deren Kronen meist über dem
     * Bild liegen. Sie werfen den Schatten, in dem die Krautschicht steht, und lassen nur Sonnenflecken durch.
     */
    public int dach = 45;
    /** Blattflächendichte der Kronen (m²/m³) [G, Buche 1–2]. */
    public double blattflaechenDichte = 1.2;
    /** Länge eines Blatts (m) und Breite durch Länge. */
    public double blattLaenge = 0.10, blattSchmal = 0.55;
    /** Streuung der Blattneigung um die Waagerechte (Grad) [G]: klein = planophil. */
    public double neigung = 20;
    /** Anteil trockener Blätter in den Kronen; Anteil heller Stämme (Birke, Buche im Licht). */
    public double trocken = 0.015, hell = 0.3;
    /** Halme am Boden: Zahl, bis zur Tiefe (m), Höhe von bis (m). */
    public int halme = 2200;
    public double halmeBis = 7.5, halmMin = 0.18, halmMax = 0.7;
    /** Laubstreu am Boden: Zahl, bis zur Tiefe (m). */
    public int streu = 3500;
    public double streuBis = 30;

    /** Albedo (linear) von Laub, trockenem Laub, Rinde, heller Rinde, Halm, Streu und Boden [G]. */
    public float[] laub = {0.050f, 0.105f, 0.032f}, laubTrocken = {0.26f, 0.12f, 0.03f}, rinde = {0.085f, 0.075f, 0.058f},
            rindeHell = {0.44f, 0.42f, 0.36f}, halm = {0.15f, 0.21f, 0.085f}, streuFarbe = {0.24f, 0.12f, 0.04f},
            boden = {0.034f, 0.027f, 0.019f};

    public final List<Glied> glieder = new ArrayList<>();
    public final List<Zweig> zweige = new ArrayList<>();
    public final List<Halm> halmListe = new ArrayList<>();

    /** Waldrand im Sommer (Vorbild: Gertschs „Sommer“). */
    public static Dickicht sommer() { return new Dickicht(); }

    /** Ein Stück Stamm oder Ast: Zylinderstumpf von (x0, y0, z0) mit Radius r0 nach (x1, y1, z1) mit r1. */
    public static final class Glied {
        public float x0, y0, z0, x1, y1, z1, r0, r1;
        public float r, g, b;
        public boolean stamm;
    }

    /** Ein Blatt: Mitte, Normale, Richtung der Längsachse, Länge, Breite, Albedo, Zufall 0..1, Phase des Flatterns, trocken. */
    public static final class Blatt {
        public float x, y, z, nx, ny, nz, ax, ay, az, laenge, breite, r, g, b, zufall, phase;
        public boolean trocken;
    }

    /** Ein Halm: fünf Punkte vom Fuß zur Spitze (x, y, z je Punkt), Breite am Fuß (m), Albedo. */
    public static final class Halm {
        public final float[] p = new float[15];
        public float breite, r, g, b;
    }

    /**
     * Eine Wolke von Blättern an einem Zweig. Die Blätter entstehen aus dem seed über {@link Zufall}, in Java und GLSL
     * gleich ({@code gl/gertsch.glsl}, {@code blatt}); gerechnet wird in float wie auf der Grafikkarte.
     */
    public static final class Zweig {
        public float x, y, z, radius;
        public int blaetter;
        public float laenge, schmal, neigung, trocken;
        public float[] farbe, farbeTrocken;
        /** seed (32 Bit, für Prozessor und Grafikkarte gleich). */
        public int seed;
        /** Am Boden (Laubstreu): ein liegendes Blatt, das sich im Wind nicht bewegt. */
        public boolean boden;
        /** Erster Platz des Zweigs in der Reihe aller Blätter (durch 4 teilbar): für die Hysterese auf der Grafikkarte. */
        public int platz;

        /** Zufall 0..1 des Blatts i, ohne das Blatt zu bauen (für die flächentreue Auswahl). */
        public float zufall(int i) { return Zufall.zug(seed, i, 0); }

        /** Phase 0..1 des Zweigs im Wind. */
        public float phase() { return Zufall.eins(seed ^ 0x5BD1E995); }

        /** Baut Blatt i (0 ≤ i < {@link #blaetter}) in {@code o}. */
        public void blatt(int i, Blatt o) {
            o.zufall = zufall(i);
            // gleichverteilt in der Kugel (Richtung, Radius ∝ ∛u), unten abgeflacht
            float cz = 2 * Zufall.zug(seed, i, 1) - 1, sxy = (float) Math.sqrt(Math.max(0, 1 - cz * cz));
            float ph = (float) (2 * Math.PI) * Zufall.zug(seed, i, 2), rr = (float) Math.cbrt(Zufall.zug(seed, i, 3));
            o.x = x + radius * rr * sxy * (float) Math.cos(ph);
            o.y = y + radius * 0.75f * rr * cz;
            o.z = z + radius * rr * sxy * (float) Math.sin(ph);
            // Neigung: |Gauß| · neigung (Box–Muller), höchstens 85°
            float gauss = (float) (Math.sqrt(-2 * Math.log(Math.max(Zufall.zug(seed, i, 4), 1e-7f))) * Math.cos(2 * Math.PI * Zufall.zug(seed, i, 5)));
            float th = (float) Math.toRadians(Math.min(85f, Math.abs(gauss) * neigung)), phn = (float) (2 * Math.PI) * Zufall.zug(seed, i, 6);
            o.nx = (float) (Math.sin(th) * Math.cos(phn));
            o.ny = (float) Math.cos(th);
            o.nz = (float) (Math.sin(th) * Math.sin(phn));
            float tx = 2 * Zufall.zug(seed, i, 7) - 1, ty = 0.6f * Zufall.zug(seed, i, 8) - 0.3f, tz = 2 * Zufall.zug(seed, i, 9) - 1;
            float d = tx * o.nx + ty * o.ny + tz * o.nz;
            tx -= d * o.nx;
            ty -= d * o.ny;
            tz -= d * o.nz;
            float l = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (l < 1e-4f) { tx = o.ny; ty = -o.nx; tz = 0; l = (float) Math.sqrt(tx * tx + ty * ty); }
            o.ax = tx / l;
            o.ay = ty / l;
            o.az = tz / l;
            o.laenge = laenge * (0.75f + 0.5f * Zufall.zug(seed, i, 10));
            o.breite = o.laenge * schmal * (0.85f + 0.3f * Zufall.zug(seed, i, 11));
            o.trocken = Zufall.zug(seed, i, 12) < trocken;
            float[] f = o.trocken ? farbeTrocken : farbe;
            float hellig = 0.75f + 0.5f * Zufall.zug(seed, i, 13), gelb = 0.3f * Zufall.zug(seed, i, 14) - 0.15f;
            o.r = f[0] * hellig * (1 + gelb);
            o.g = f[1] * hellig;
            o.b = f[2] * hellig * (1 - gelb);
            o.phase = Zufall.zug(seed, i, 15);
        }
    }

    /**
     * Auf halbe Genauigkeit (16 Bit) gerundet: So kommen Länge und Schmalheit der Blätter auf die Grafikkarte (zwei in
     * einem Wort), und Prozessor und Grafikkarte rechnen mit denselben Zahlen (Abweichung höchstens 0,05 %).
     */
    static float halb(double v) { return Float.float16ToFloat(Float.floatToFloat16((float) v)); }

    static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /** Zahl aller Blätter (Summe über die Zweige). */
    public long blattZahl() {
        long n = 0;
        for (Zweig z : zweige) n += z.blaetter;
        return n;
    }

    /** Erzeugt alles neu aus {@link #seed} und den offenen Werten. */
    public Dickicht bauen() {
        glieder.clear();
        zweige.clear();
        halmListe.clear();
        SplittableRandom r = new SplittableRandom(seed);
        double halbe = breite / 2;
        // Bäume: hoch, Krone ab der Hälfte; einige weiter vorn
        for (int i = 0; i < baeume; i++) {
            double z = nah + 4 + r.nextDouble() * (fern - nah - 4), x = r.nextDouble(-halbe, halbe), h = 12 + 12 * r.nextDouble();
            gehoelz(r, x, z, h, 0.14 + 0.2 * r.nextDouble(), 0.4 + 0.2 * r.nextDouble(), 2.5 + 2.5 * r.nextDouble(), 1, r.nextDouble() < hell, blattLaenge);
        }
        // Kronendach über dem Vordergrund
        for (int i = 0; i < dach; i++) {
            double z = -8 + 24 * r.nextDouble(), x = r.nextDouble(-25, 25), h = 11 + 9 * r.nextDouble();
            gehoelz(r, x, z, h, 0.15 + 0.2 * r.nextDouble(), 0.4 + 0.15 * r.nextDouble(), 3 + 2 * r.nextDouble(), 1, r.nextDouble() < hell * 0.2, blattLaenge);   // vorn meist dunkle Rinde
        }
        // Jungbäume: 3–9 m, Krone tief
        for (int i = 0; i < jungbaeume; i++) {
            double z = nah + 1 + r.nextDouble() * (fern - nah - 1), x = r.nextDouble(-halbe, halbe), h = 3 + 6 * r.nextDouble();
            gehoelz(r, x, z, h, 0.04 + 0.06 * r.nextDouble(), 0.15 + 0.2 * r.nextDouble(), 1.0 + 1.2 * r.nextDouble(), 1, r.nextDouble() < hell, blattLaenge);
        }
        // Sträucher: 0,6–3 m, mehrere Triebe, Krone fast vom Boden
        for (int i = 0; i < straeucher; i++) {
            double z = nah + r.nextDouble() * (fern - nah), x = r.nextDouble(-halbe, halbe), h = 0.6 + 2.4 * r.nextDouble();
            gehoelz(r, x, z, h, 0.012 + 0.02 * r.nextDouble(), 0.08 + 0.12 * r.nextDouble(), 0.5 + 0.9 * r.nextDouble(), 3 + r.nextInt(5), false, blattLaenge);
        }
        // Kraut vorn: niedrig, große Blätter, kaum Stängel
        for (int i = 0; i < kraut; i++) {
            double z = nah + Math.pow(r.nextDouble(), 0.9) * (krautBis - nah), x = r.nextDouble(-1, 1) * (z * Math.tan(Math.toRadians(30)) + 1);
            double h = 0.3 + 0.8 * r.nextDouble();
            gehoelz(r, x, z, h, 0.004, 0.05, 0.25 + 0.35 * r.nextDouble(), 1 + r.nextInt(3), false, krautBlatt);
        }
        // Wand: dichtes Gehölz hinten, damit kein Himmel durchscheint
        for (int i = 0; i < wand; i++) {
            double z = wandAb + r.nextDouble() * (fern - wandAb), x = r.nextDouble(-halbe * 1.2, halbe * 1.2), h = 4 + 10 * r.nextDouble();
            gehoelz(r, x, z, h, 0.05 + 0.12 * r.nextDouble(), 0.1 + 0.15 * r.nextDouble(), 1.5 + 2.0 * r.nextDouble(), 1 + r.nextInt(2), r.nextDouble() < hell * 0.5, blattLaenge);
        }
        // Halme vorn am Boden, in Büscheln
        double tan = Math.tan(Math.toRadians(30));
        for (int i = 0; i < halme; ) {
            double z = nah + Math.pow(r.nextDouble(), 0.8) * (halmeBis - nah), xb = r.nextDouble(-1, 1) * (z * tan + 1);
            int bueschel = 3 + r.nextInt(8);
            for (int j = 0; j < bueschel && i < halme; j++, i++) halm(r, xb + r.nextGaussian() * 0.06, z + r.nextGaussian() * 0.06);
        }
        // Streu am Boden: je ein liegendes, trockenes Blatt als Zweig ohne Ausdehnung (so behandeln Malweise und
        // Grafikkarte sie wie jedes Blatt)
        for (int i = 0; i < streu; i++) {
            double z = nah + Math.pow(r.nextDouble(), 0.7) * (streuBis - nah), x = r.nextDouble(-1, 1) * (z * tan + 1);
            Zweig zw = new Zweig();
            zw.x = (float) x;
            zw.y = 0.004f;
            zw.z = (float) z;
            zw.radius = 0;
            zw.blaetter = 1;
            zw.laenge = halb(blattLaenge * 1.05);
            zw.schmal = halb(blattSchmal);
            zw.neigung = 8;
            zw.trocken = 1;
            zw.farbe = streuFarbe;
            zw.farbeTrocken = streuFarbe;
            zw.seed = r.nextInt();
            zw.boden = true;
            zweige.add(zw);
        }
        // Plätze der Blätter (je Zweig auf 4 aufgerundet, damit sich auf der Grafikkarte keine zwei Zweige ein Wort teilen)
        int platz = 0;
        for (Zweig zw : zweige) {
            zw.platz = platz;
            platz += (zw.blaetter + 3) & ~3;
        }
        plaetze = platz;
        return this;
    }

    /** Zahl der Plätze aller Blätter (Summe der auf 4 aufgerundeten Zahlen je Zweig). */
    public int plaetze;

    /**
     * Ein Gehölz: {@code triebe} Stämme ab (x, 0, z), Höhe h, Stammradius rs, Krone ab dem Anteil {@code kroneAb} der
     * Höhe mit dem Radius {@code kroneR}. Äste laufen vom Stamm zur Hülle der Krone, an ihnen sitzen die Zweige.
     */
    void gehoelz(SplittableRandom r, double x, double z, double h, double rs, double kroneAb, double kroneR, int triebe, boolean hellRinde, double blattLaenge) {
        float[] rindeF = hellRinde ? rindeHell : rinde;
        double y0 = kroneAb * h, hh = (h - y0) / 2, cy = y0 + hh;
        double[][] spitzen = new double[triebe][];
        for (int s = 0; s < triebe; s++) {
            double bx = x + (triebe > 1 ? r.nextGaussian() * 0.15 : 0), bz = z + (triebe > 1 ? r.nextGaussian() * 0.15 : 0);
            double lx = r.nextGaussian() * (triebe > 1 ? 0.15 : 0.05), lz = r.nextGaussian() * (triebe > 1 ? 0.15 : 0.05);
            double hs = h * (triebe > 1 ? 0.75 + 0.25 * r.nextDouble() : 1), ph = r.nextDouble(2 * Math.PI);
            int n = Math.max(2, (int) Math.ceil(hs / 0.3));
            double hellig = 0.8 + 0.4 * r.nextDouble();
            for (int k = 0; k < n; k++) {
                double t0 = k / (double) n, t1 = (k + 1) / (double) n;
                Glied g = new Glied();
                g.x0 = (float) (bx + lx * t0 * hs + 0.04 * hs * Math.sin(ph + 5 * t0) * t0);
                g.z0 = (float) (bz + lz * t0 * hs + 0.04 * hs * Math.cos(ph + 4 * t0) * t0);
                g.y0 = (float) (t0 * hs);
                g.x1 = (float) (bx + lx * t1 * hs + 0.04 * hs * Math.sin(ph + 5 * t1) * t1);
                g.z1 = (float) (bz + lz * t1 * hs + 0.04 * hs * Math.cos(ph + 4 * t1) * t1);
                g.y1 = (float) (t1 * hs);
                g.r0 = (float) (rs * (1 - 0.85 * t0));
                g.r1 = (float) (rs * (1 - 0.85 * t1));
                g.r = (float) (rindeF[0] * hellig);
                g.g = (float) (rindeF[1] * hellig);
                g.b = (float) (rindeF[2] * hellig);
                g.stamm = true;
                glieder.add(g);
            }
            spitzen[s] = new double[]{bx, lx, bz, lz, hs};
        }
        // Blätter der Krone aus der Blattflächendichte
        double volumen = 4.0 / 3 * Math.PI * kroneR * kroneR * hh, blattFlaeche = 0.7 * blattLaenge * blattLaenge * blattSchmal;
        long blaetter = Math.round(blattflaechenDichte * volumen / blattFlaeche);
        int jeZweig = 12, zahlZweige = (int) Math.max(3, blaetter / jeZweig), aeste = Math.max(3, Math.min(48, zahlZweige / 10));
        int zweigeJeAst = Math.max(1, zahlZweige / aeste);
        for (int a = 0; a < aeste; a++) {
            double[] sp = spitzen[r.nextInt(triebe)];
            double ya = y0 + r.nextDouble() * (h - y0) * 0.9, t = ya / sp[4];
            double ax = sp[0] + sp[1] * t * sp[4], az = sp[2] + sp[3] * t * sp[4];
            // Ende auf der Hülle der Krone, eher nach außen und oben
            double ph = r.nextDouble(2 * Math.PI), el = Math.toRadians(-20 + 70 * r.nextDouble());
            double ex = x + Math.cos(ph) * Math.cos(el) * kroneR * (0.75 + 0.25 * r.nextDouble());
            double ez = z + Math.sin(ph) * Math.cos(el) * kroneR * (0.75 + 0.25 * r.nextDouble());
            double ey = cy + Math.sin(el) * hh * (0.75 + 0.25 * r.nextDouble());
            ey = Math.max(ey, ya - 0.3 * hh);
            double ra = Math.max(0.006, rs * 0.3);
            Glied g = new Glied();
            g.x0 = (float) ax;
            g.y0 = (float) ya;
            g.z0 = (float) az;
            g.x1 = (float) ex;
            g.y1 = (float) ey;
            g.z1 = (float) ez;
            g.r0 = (float) ra;
            g.r1 = (float) (ra * 0.2);
            g.r = rindeF[0];
            g.g = rindeF[1];
            g.b = rindeF[2];
            glieder.add(g);
            for (int k = 0; k < zweigeJeAst; k++) {
                double s = 0.25 + 0.8 * r.nextDouble();
                Zweig zw = new Zweig();
                zw.x = (float) (ax + (ex - ax) * s + r.nextGaussian() * 0.22 * kroneR);
                zw.y = (float) (ya + (ey - ya) * s + r.nextGaussian() * 0.15 * hh);
                zw.z = (float) (az + (ez - az) * s + r.nextGaussian() * 0.22 * kroneR);
                zw.y = Math.max(zw.y, 0.08f);
                zw.radius = (float) (0.12 + 0.1 * r.nextDouble());
                zw.blaetter = (int) Math.max(3, Math.round(blaetter / (double) (aeste * zweigeJeAst) * (0.7 + 0.6 * r.nextDouble())));
                zw.laenge = halb(blattLaenge);
                zw.schmal = halb(blattSchmal);
                zw.neigung = (float) neigung;
                zw.trocken = (float) trocken;
                zw.farbe = laub;
                zw.farbeTrocken = laubTrocken;
                zw.seed = r.nextInt();
                zweige.add(zw);
            }
        }
    }

    void halm(SplittableRandom r, double x, double z) {
        Halm hm = new Halm();
        double h = halmMin + (halmMax - halmMin) * r.nextDouble(), ph = r.nextDouble(2 * Math.PI), lean = (0.1 + 0.6 * r.nextDouble()) * h;
        for (int k = 0; k < 5; k++) {
            double t = k / 4.0;
            hm.p[3 * k] = (float) (x + Math.cos(ph) * lean * t * t);
            hm.p[3 * k + 1] = (float) (h * t * (1 - 0.25 * t * t * lean / h));
            hm.p[3 * k + 2] = (float) (z + Math.sin(ph) * lean * t * t);
        }
        hm.breite = (float) (0.003 + 0.004 * r.nextDouble());
        boolean duerr = r.nextDouble() < 0.12;
        double hg = 0.75 + 0.5 * r.nextDouble();
        hm.r = (float) (halm[0] * hg * (duerr ? 1.6 : 1));
        hm.g = (float) (halm[1] * hg * (duerr ? 1.1 : 1));
        hm.b = (float) (halm[2] * hg * (duerr ? 0.6 : 1));
        halmListe.add(hm);
    }
}
