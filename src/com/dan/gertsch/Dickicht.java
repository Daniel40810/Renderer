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
 * <p>
 * Seit Phase 5 trägt ein Dickicht auch eine Szene von außen ({@link #aus(Quelle)}): einzelne Blätter liegen in
 * {@link #blattDaten}, und die Zweige fassen sie nur zusammen; Büschel werden Zweige aus seed mit eigener Farbe.
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
        /**
         * Blätter von außen ({@link Dickicht#aus}): die Werte aller Blätter und die Nummer des ersten dieses Zweigs; sonst
         * null und −1 (die Blätter entstehen aus dem seed). Für Blätter von außen ist der seed die Nummer des ersten.
         */
        public float[] daten;
        public int erstes = -1;
        /**
         * Eigene Farbe und Neigung (ein Büschel von außen): Die Grafikkarte nimmt dann {@link #farbe} und {@link #neigung}
         * dieses Zweigs statt der des Dickichts. Trockene Blätter gibt es darin nicht.
         */
        public boolean eigeneFarbe;

        /** Zufall 0..1 des Blatts i, ohne das Blatt zu bauen (für die flächentreue Auswahl). */
        public float zufall(int i) { return Zufall.zug(seed, i, 0); }

        /** Phase 0..1 des Zweigs im Wind. */
        public float phase() { return Zufall.eins(seed ^ 0x5BD1E995); }

        /** Baut Blatt i (0 ≤ i < {@link #blaetter}) in {@code o}. */
        public void blatt(int i, Blatt o) {
            o.zufall = zufall(i);
            if (daten != null) {   // von außen: wie es kam; Zufall und Phase aus dem seed wie sonst
                int q = STRIDE * (erstes + i);
                o.x = daten[q]; o.y = daten[q + 1]; o.z = daten[q + 2]; o.laenge = daten[q + 3];
                o.nx = daten[q + 4]; o.ny = daten[q + 5]; o.nz = daten[q + 6]; o.breite = daten[q + 7];
                o.ax = daten[q + 8]; o.ay = daten[q + 9]; o.az = daten[q + 10];
                o.r = daten[q + 12]; o.g = daten[q + 13]; o.b = daten[q + 14];
                o.trocken = false;
                o.phase = Zufall.zug(seed, i, 15);
                return;
            }
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
    // ------------------------------------------------------------------ Szene von außen (Phase 5)

    /** Höchstens so viele Blätter je Zweig (ein Byte je Blatt in der Liste der Grafikkarte). */
    public static final int JE_ZWEIG = 255;
    /** Ein Zweig aus Blättern von außen reicht höchstens so weit von seinem ersten Blatt (m). */
    public static final float ZWEIG_WEITE = 1.0f;
    /** Werte je Blatt von außen in {@link #blattDaten}: Mitte, Länge, Normale, Breite, Längsachse, frei, Albedo, frei. */
    public static final int STRIDE = 16;

    /** Blätter von außen ({@link #aus}), je {@link #STRIDE} Werte; null für ein Dickicht aus seed. */
    public float[] blattDaten;
    /** Rechteck des Bodens {x0, z0, x1, z1} (m); null: von −breite bis breite, bis zur Tiefe 1,3 · fern. */
    public float[] bodenBereich;
    /** Das Gelände unter der Szene (seit LituyaBay1958 W10, aus {@link Quelle#gelaende}); null: die Ebene y = 0. */
    public Gelaende gelaende;

    /** Höhe des Bodens bei (x, z): das Gelände oder 0. */
    public float bodenHoehe(double x, double z) { return gelaende == null ? 0 : gelaende.hoehe(x, z); }

    /** Tiefste und höchste Stelle des Bodens (0, 0 ohne Gelände). */
    public float bodenMin() { return gelaende == null ? 0 : gelaende.min(); }

    public float bodenMax() { return gelaende == null ? 0 : gelaende.max(); }

    /**
     * Die Szene aus einer {@link Quelle}: Glieder wie sie sind, Halme als Bögen durch fünf Punkte, Blätter in Zweige
     * gefasst. Ein Zweig nimmt aufeinanderfolgende Blätter derselben Gruppe auf, höchstens {@link #JE_ZWEIG} und nicht
     * weiter als {@link #ZWEIG_WEITE} vom ersten. Seine Mitte, sein Radius und die mittlere Größe seiner Blätter gelten
     * für Größenregel, Wind und Schatten wie bei einem Zweig aus seed; die Blätter selbst kommen aus {@link #blattDaten}.
     * Zufall und Phase je Blatt folgen aus dem seed des Zweigs (= Nummer seines ersten Blatts), also aus der
     * Reihenfolge: gleiche Quelle, gleiches Bild.
     * <p>
     * Das Ergebnis wird nicht gebaut ({@link #bauen} würde es durch ein Dickicht aus seed ersetzen).
     */
    public static Dickicht aus(Quelle q) {
        Dickicht d = new Dickicht();
        d.baeume = d.jungbaeume = d.straeucher = d.wand = d.kraut = d.dach = d.halme = d.streu = 0;
        if (q.boden() != null) d.boden = q.boden().clone();
        d.gelaende = q.gelaende();
        float xmin = Float.MAX_VALUE, xmax = -Float.MAX_VALUE, zmin = Float.MAX_VALUE, zmax = -Float.MAX_VALUE;
        // Glieder
        Quelle.Glied qg = new Quelle.Glied();
        for (int i = 0; i < q.glieder(); i++) {
            q.glied(i, qg);
            Glied g = new Glied();
            g.x0 = qg.x0; g.y0 = qg.y0; g.z0 = qg.z0; g.r0 = qg.r0; g.x1 = qg.x1; g.y1 = qg.y1; g.z1 = qg.z1; g.r1 = qg.r1;
            g.r = qg.r; g.g = qg.g; g.b = qg.b; g.stamm = qg.stamm;
            d.glieder.add(g);
            xmin = Math.min(xmin, Math.min(g.x0, g.x1)); xmax = Math.max(xmax, Math.max(g.x0, g.x1));
            zmin = Math.min(zmin, Math.min(g.z0, g.z1)); zmax = Math.max(zmax, Math.max(g.z0, g.z1));
        }
        // Halme: quadratischer Bogen von Fuß zu Spitze, die Mitte um die Biegung neben der Geraden
        Quelle.Halm qh = new Quelle.Halm();
        for (int i = 0; i < q.halme(); i++) {
            q.halm(i, qh);
            Halm hm = new Halm();
            float[] k = kontrolle(qh);
            for (int s = 0; s < 5; s++) {
                float t = s / 4f, a = (1 - t) * (1 - t), b = 2 * t * (1 - t), c = t * t;
                hm.p[3 * s] = a * qh.fx + b * k[0] + c * qh.sx;
                hm.p[3 * s + 1] = a * qh.fy + b * k[1] + c * qh.sy;
                hm.p[3 * s + 2] = a * qh.fz + b * k[2] + c * qh.sz;
            }
            hm.breite = qh.breite;
            hm.r = qh.r; hm.g = qh.g; hm.b = qh.b;
            d.halmListe.add(hm);
            xmin = Math.min(xmin, Math.min(qh.fx, qh.sx)); xmax = Math.max(xmax, Math.max(qh.fx, qh.sx));
            zmin = Math.min(zmin, Math.min(qh.fz, qh.sz)); zmax = Math.max(zmax, Math.max(qh.fz, qh.sz));
        }
        // Blätter
        int n = q.blaetter();
        float[] da = new float[STRIDE * Math.max(1, n)];
        int[] gruppe = new int[n];
        boolean[] liegt = new boolean[n];
        Quelle.Blatt qb = new Quelle.Blatt();
        for (int i = 0; i < n; i++) {
            q.blatt(i, qb);
            int o = STRIDE * i;
            da[o] = qb.x; da[o + 1] = qb.y; da[o + 2] = qb.z; da[o + 3] = qb.laenge;
            da[o + 4] = qb.nx; da[o + 5] = qb.ny; da[o + 6] = qb.nz; da[o + 7] = qb.breite;
            da[o + 8] = qb.ax; da[o + 9] = qb.ay; da[o + 10] = qb.az;
            da[o + 12] = qb.r; da[o + 13] = qb.g; da[o + 14] = qb.b;
            gruppe[i] = qb.gruppe;
            liegt[i] = qb.liegt;
            xmin = Math.min(xmin, qb.x); xmax = Math.max(xmax, qb.x);
            zmin = Math.min(zmin, qb.z); zmax = Math.max(zmax, qb.z);
        }
        d.blattDaten = da;
        for (int i = 0; i < n; ) {
            int j = i + 1;
            while (j < n && j - i < JE_ZWEIG && gruppe[j] == gruppe[i] && liegt[j] == liegt[i]
                    && abstand(da, i, j) <= ZWEIG_WEITE) j++;
            d.zweige.add(zweigAus(da, i, j - i, liegt[i], d.laub, d.laubTrocken));
            i = j;
        }
        // Büschel: Zweige aus seed mit eigener Farbe und Neigung; mehr als 255 Blätter auf mehrere Zweige
        Quelle.Bueschel qbu = new Quelle.Bueschel();
        for (int i = 0; i < q.bueschelZahl(); i++) {
            q.bueschel(i, qbu);
            for (int rest = qbu.blaetter, teil = 0; rest > 0; rest -= JE_ZWEIG, teil++) {
                Zweig zw = new Zweig();
                zw.x = qbu.x; zw.y = qbu.y; zw.z = qbu.z; zw.radius = qbu.radius;
                zw.blaetter = Math.min(JE_ZWEIG, rest);
                zw.laenge = halb(qbu.laenge);
                zw.schmal = halb(qbu.schmal);
                zw.neigung = qbu.liegt ? 8 : qbu.neigung;   // liegend wie die Streu des Dickichts (auch auf der Grafikkarte)
                zw.trocken = 0;
                zw.farbe = new float[]{qbu.r, qbu.g, qbu.b};
                zw.farbeTrocken = zw.farbe;
                zw.eigeneFarbe = true;
                zw.seed = Zufall.pcg(0x7A3B1C5D ^ (i * 64 + teil));
                zw.boden = qbu.liegt;
                d.zweige.add(zw);
            }
            xmin = Math.min(xmin, qbu.x - qbu.radius); xmax = Math.max(xmax, qbu.x + qbu.radius);
            zmin = Math.min(zmin, qbu.z - qbu.radius); zmax = Math.max(zmax, qbu.z + qbu.radius);
        }
        int platz = 0;
        for (Zweig zw : d.zweige) {
            zw.platz = platz;
            platz += (zw.blaetter + 3) & ~3;
        }
        d.plaetze = platz;
        // Ausdehnung: Boden unter allem, was da ist, mit Rand; Tiefe für die Schattenkarten
        if (xmin > xmax) { xmin = -1; xmax = 1; zmin = 0; zmax = 1; }
        d.bodenBereich = new float[]{xmin - 2, zmin - 2, xmax + 2, zmax + 2};
        d.breite = Math.max(2 * Math.max(Math.abs(xmin), Math.abs(xmax)), 1);
        d.nah = Math.max(0, zmin);
        d.fern = Math.max(zmax, 1);
        return d;
    }

    /** Abstand der Mitten der Blätter i und j von außen (m), für Prüfungen. */
    public static float abstandFuerPruefung(Dickicht d, int i, int j) { return abstand(d.blattDaten, i, j); }

    static float abstand(float[] da, int i, int j) {
        float dx = da[STRIDE * j] - da[STRIDE * i], dy = da[STRIDE * j + 1] - da[STRIDE * i + 1], dz = da[STRIDE * j + 2] - da[STRIDE * i + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static Zweig zweigAus(float[] da, int erstes, int zahl, boolean boden, float[] laub, float[] laubTrocken) {
        double mx = 0, my = 0, mz = 0, ml = 0, ms = 0;
        for (int i = erstes; i < erstes + zahl; i++) {
            int o = STRIDE * i;
            mx += da[o]; my += da[o + 1]; mz += da[o + 2];
            ml += da[o + 3];
            ms += da[o + 7] / Math.max(1e-6f, da[o + 3]);
        }
        Zweig zw = new Zweig();
        zw.x = (float) (mx / zahl);
        zw.y = (float) (my / zahl);
        zw.z = (float) (mz / zahl);
        float r = 0;
        for (int i = erstes; i < erstes + zahl; i++) {
            int o = STRIDE * i;
            float dx = da[o] - zw.x, dy = da[o + 1] - zw.y, dz = da[o + 2] - zw.z;
            r = Math.max(r, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.5f * da[o + 3]);
        }
        zw.radius = r;
        zw.blaetter = zahl;
        zw.laenge = halb(ml / zahl);
        zw.schmal = halb(ms / zahl);
        zw.neigung = 0;
        zw.trocken = 0;
        zw.farbe = laub;
        zw.farbeTrocken = laubTrocken;
        zw.seed = erstes;
        zw.boden = boden;
        zw.daten = da;
        zw.erstes = erstes;
        return zw;
    }

    /**
     * Kontrollpunkt des Bogens eines Halms: Mitte der Geraden, um 2 · Biegung · Länge senkrecht zu ihr versetzt (dann
     * liegt die Mitte des Bogens um Biegung · Länge daneben), in der senkrechten Ebene durch die Gerade, nach oben. Steht
     * der Halm senkrecht, gibt es keine Seite: dann gerade.
     */
    static float[] kontrolle(Quelle.Halm h) {
        float cx = h.sx - h.fx, cy = h.sy - h.fy, cz = h.sz - h.fz;
        float l = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        float mx = 0.5f * (h.fx + h.sx), my = 0.5f * (h.fy + h.sy), mz = 0.5f * (h.fz + h.sz);
        if (l < 1e-6f) return new float[]{mx, my, mz};
        cx /= l; cy /= l; cz /= l;
        // oben ohne den Anteil entlang des Halms
        float ux = -cy * cx, uy = 1 - cy * cy, uz = -cy * cz, lu = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (lu < 1e-4f) return new float[]{mx, my, mz};
        float s = 2 * h.biegung * l / lu;
        return new float[]{mx + s * ux, my + s * uy, mz + s * uz};
    }

    /**
     * Dieses Dickicht als {@link Quelle} (jedes Blatt einzeln, Gruppe = Nummer des Zweigs). Für Prüfungen und die Demo;
     * {@link #aus} legt alle Blätter in den Speicher (64 Byte je Blatt), also nur für kleine Dickichte.
     */
    public Quelle alsQuelle() { return als(false); }

    /**
     * Dieses Dickicht als {@link Quelle} aus Büscheln: jeder Zweig ein Büschel (Mitte, Radius, Zahl, Größe, Neigung,
     * Farbe). So passt auch das ganze Dickicht durch die Schnittstelle. Trockene Blätter in den Kronen gibt es dann nicht
     * (ein Büschel hat eine Farbe), und die Blätter fallen anders aus dem Zufall, gleichen sich aber im Mittel.
     */
    public Quelle alsBueschel() { return als(true); }

    private Quelle als(boolean bueschel) {
        long[] bis = new long[zweige.size()];
        long s = 0;
        for (int k = 0; k < zweige.size(); k++) { s += zweige.get(k).blaetter; bis[k] = s; }
        if (!bueschel && s > Integer.MAX_VALUE) throw new IllegalStateException("gertsch: zu viele Blätter für eine Quelle: " + s);
        int zahl = bueschel ? 0 : (int) s;
        Dickicht self = this;
        return new Quelle() {
            final Dickicht.Blatt b = new Dickicht.Blatt();

            @Override public int blaetter() { return zahl; }

            @Override public void blatt(int i, Quelle.Blatt o) {
                int k = java.util.Arrays.binarySearch(bis, i + 1L);
                k = k >= 0 ? k : -k - 1;
                while (k > 0 && bis[k - 1] > i) k--;
                Dickicht.Zweig z = zweige.get(k);
                int j = (int) (i - (k == 0 ? 0 : bis[k - 1]));
                synchronized (b) {
                    z.blatt(j, b);
                    o.x = b.x; o.y = b.y; o.z = b.z; o.nx = b.nx; o.ny = b.ny; o.nz = b.nz; o.ax = b.ax; o.ay = b.ay; o.az = b.az;
                    o.laenge = b.laenge; o.breite = b.breite; o.r = b.r; o.g = b.g; o.b = b.b;
                }
                o.gruppe = k;
                o.liegt = z.boden;
            }

            @Override public int bueschelZahl() { return bueschel ? zweige.size() : 0; }

            @Override public void bueschel(int i, Quelle.Bueschel o) {
                Dickicht.Zweig z = zweige.get(i);
                o.x = z.x; o.y = z.y; o.z = z.z; o.radius = z.radius; o.blaetter = z.blaetter; o.laenge = z.laenge; o.schmal = z.schmal;
                o.neigung = z.neigung; o.r = z.farbe[0]; o.g = z.farbe[1]; o.b = z.farbe[2]; o.liegt = z.boden;
            }

            @Override public int halme() { return halmListe.size(); }

            @Override public void halm(int i, Quelle.Halm o) { halmAlsQuelle(halmListe.get(i), o); }

            @Override public int glieder() { return glieder.size(); }

            @Override public void glied(int i, Quelle.Glied o) {
                Dickicht.Glied g = glieder.get(i);
                o.x0 = g.x0; o.y0 = g.y0; o.z0 = g.z0; o.r0 = g.r0; o.x1 = g.x1; o.y1 = g.y1; o.z1 = g.z1; o.r1 = g.r1;
                o.r = g.r; o.g = g.g; o.b = g.b; o.stamm = g.stamm;
            }

            @Override public float[] boden() { return self.boden.clone(); }

            @Override public Gelaende gelaende() { return self.gelaende; }
        };
    }

    /** Ein Halm des Dickichts als Halm von außen: Fuß, Spitze, Biegung aus der Mitte (nach oben positiv), Breite, Farbe. */
    static void halmAlsQuelle(Halm h, Quelle.Halm o) {
        float[] p = h.p;
        o.fx = p[0]; o.fy = p[1]; o.fz = p[2];
        o.sx = p[12]; o.sy = p[13]; o.sz = p[14];
        float cx = o.sx - o.fx, cy = o.sy - o.fy, cz = o.sz - o.fz, l = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        float vx = p[6] - 0.5f * (o.fx + o.sx), vy = p[7] - 0.5f * (o.fy + o.sy), vz = p[8] - 0.5f * (o.fz + o.sz);
        if (l > 1e-6f) {
            cx /= l; cy /= l; cz /= l;
            float ux = -cy * cx, uy = 1 - cy * cy, uz = -cy * cz, lu = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            o.biegung = lu < 1e-4f ? 0 : (vx * ux + vy * uy + vz * uz) / lu / l;
        } else o.biegung = 0;
        o.breite = h.breite; o.r = h.r; o.g = h.g; o.b = h.b;
    }
}
