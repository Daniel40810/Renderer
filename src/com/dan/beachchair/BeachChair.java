package com.dan.beachchair;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Der Strandkorb (seit Timmendorf, nach dem Foto {@code Strandkorb.jpg}): ein Zweisitzer aus Korbgeflecht mit Haube,
 * Seitenwänden, Armlehnen aus Holz, einem Unterkasten mit zwei Fußstützen zum Herausziehen und dem Bezug aus
 * gestreiftem Markisenstoff. Zwei Bauarten:
 * <ul>
 * <li>{@link Type#OSTSEE}: Haube oben rund, Seitenwände nach vorn geschwungen (Timmendorf, Lübecker Bucht),</li>
 * <li>{@link Type#NORDSEE}: Haube eckig mit Knick, Seitenwände gerade (Sylt, Föhr, Nordseeküste).</li>
 * </ul>
 * Der Kern rechnet ohne Grafikkarte: das Netz in drei Detailstufen ({@link #mesh(Lod)}), die Stellung eines Korbs
 * (Rückenlehne, Fußstützen, abends verschlossen) und seine Farbe aus der Palette. Gezeichnet wird mit {@code gl/BeachChairGl}
 * (viele Körbe auf einmal, je Detailstufe ein Aufruf, dazu die Schatten auf dem Sand).
 * <p>
 * Koordinaten am Korb: Ursprung in der Mitte der Grundfläche auf dem Boden, x nach rechts (vom Sitzenden aus), y nach
 * oben, z nach vorn (wohin der Korb schaut). In der Szene steht ein Korb an (x, y, z) mit der Blickrichtung in Grad von
 * Nord im Uhrzeigersinn (x Ost, z Süd), wie in den übrigen Packages.
 * <p>
 * Maße [G nach dem Foto und gängigen Zweisitzern]: 1,24 m breit, 0,80 m tief, 1,60 m hoch, Sitz 0,42 m, Armlehne 0,66 m,
 * Streifen 6,5 cm. Werte mit [G] sind gestaltet, also begründet geschätzt und nicht gemessen.
 */
public final class BeachChair {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag): Rundung der Haube und Reichweite der Detailstufen. */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Bauart: Ostsee (runde Haube) oder Nordsee (eckig). */
    public enum Type { OSTSEE, NORDSEE }

    /** Detailstufen des Netzes: nah (alles, mit Griffen und Rahmen), mittel, fern (Umriss). */
    public enum Lod { NEAR, MID, FAR }

    // ------------------------------------------------------------------ Teile und Gruppen (gleiche Nummern in beachchair.frag/vert)

    /** Baustoffe der Teile. */
    public static final int WICKER = 0, WOOD = 1, CANVAS = 2, CUSHION = 3, ROPE = 4, SLATS = 5;
    public static final String[] PART_NAMES = {"Geflecht", "Holz", "Markisenstoff", "Polster", "Tau", "Verschluss"};
    /** Gruppen: fest, Oberteil (kippt mit der Rückenlehne), Fußstützen (gleiten nach vorn), Verschluss (nur abends). */
    public static final int FIXED = 0, BACK = 1, FOOT = 2, COVER = 3;

    /** Ecke im Netz: Ort (3), Normale (3), Musterkoordinaten u, v in m (2), Teil, Gruppe, innen (1 = im Korb). */
    public static final int STRIDE = 11;

    // ------------------------------------------------------------------ offene Werte

    public Type type = Type.OSTSEE;
    public Quality quality = Quality.HIGH;
    /** Startwert für Farben und Stellungen: gleicher seed, gleiche Körbe. */
    public long seed = 1;
    /** Breite, Tiefe des Unterkastens, Höhe bis zum Scheitel der Haube (m). */
    public double width = 1.24, depth = 0.80, height = 1.60;
    /** Oberkante des Sitzpolsters, Dicke des Polsters, Höhe der Armlehne (m). */
    public double seatHeight = 0.42, cushion = 0.06, armrestHeight = 0.66;
    /** Wandstärke des Geflechts (m). */
    public double wall = 0.05;
    /** Wie weit die Haube nach vorn reicht (Spitze, gemessen von der Mitte, m); vorderer Rand der Seitenwand unten. */
    public double hoodTip = 0.14, sideFront = 0.30;
    /** Halbmesser der Rundung oben hinten (Ostsee) bzw. Fase (Nordsee), m. */
    public double roundZ = 0.34, roundY = 0.36;
    /** Breite eines Streifens im Stoff (m). */
    public double stripeWidth = 0.065;
    /** Rückenlehne höchstens so weit nach hinten (Grad), Fußstützen höchstens so weit heraus (m). */
    public double maxReclineDeg = 32, footrestTravel = 0.42;
    /** Farben (linear): Geflecht, Holz, heller Streifen, Tau. */
    public double[] wicker = {0.74, 0.73, 0.68}, wood = {0.50, 0.32, 0.13}, stripeLight = {0.80, 0.80, 0.77}, rope = {0.82, 0.80, 0.74};
    /** Palette der dunklen Streifen mit Gewicht: {r, g, b, Gewicht}. */
    public double[][] palette = {{0.03, 0.12, 0.45, 1}};

    // ------------------------------------------------------------------ Presets

    /** Ostseekorb wie auf dem Foto: weißes Geflecht, Holz in Honig, Streifen blau-weiß. */
    public static BeachChair ostsee() {
        return new BeachChair();
    }

    /**
     * Timmendorfer Strand [G nach Strandkorb.jpg und den Fotos Strandkorb1–2]: Ostseekorb, meist blau-weiß, dazu dunkelblau,
     * grün, rot und gelb gestreift (wie der Strand der Vermieter).
     */
    public static BeachChair timmendorf() {
        BeachChair c = ostsee();
        c.seed = 1923;
        c.palette = new double[][]{{0.03, 0.12, 0.45, 0.62}, {0.02, 0.04, 0.15, 0.12}, {0.04, 0.30, 0.10, 0.10}, {0.55, 0.04, 0.04, 0.09},
                {0.75, 0.55, 0.03, 0.07}};
        return c;
    }

    /** Nordseekorb: eckige Haube, Geflecht naturfarben, breitere Streifen. */
    public static BeachChair nordsee() {
        BeachChair c = new BeachChair();
        c.type = Type.NORDSEE;
        c.width = 1.20;
        c.depth = 0.82;
        c.height = 1.58;
        c.hoodTip = 0.20;
        c.sideFront = 0.32;
        c.roundZ = 0.20;
        c.roundY = 0.16;
        c.stripeWidth = 0.09;
        c.wicker = new double[]{0.55, 0.43, 0.25};
        c.wood = new double[]{0.40, 0.26, 0.12};
        c.palette = new double[][]{{0.03, 0.12, 0.45, 0.4}, {0.04, 0.30, 0.10, 0.25}, {0.40, 0.42, 0.44, 0.2}, {0.55, 0.04, 0.04, 0.15}};
        return c;
    }

    /** Sylt (Westerland bis Kampen): Nordseekorb, blau-weiß, grün-weiß und grau [G]. */
    public static BeachChair sylt() {
        BeachChair c = nordsee();
        c.seed = 2026;
        return c;
    }

    public BeachChair copy() {
        BeachChair c = new BeachChair();
        c.type = type; c.quality = quality; c.seed = seed;
        c.width = width; c.depth = depth; c.height = height;
        c.seatHeight = seatHeight; c.cushion = cushion; c.armrestHeight = armrestHeight; c.wall = wall;
        c.hoodTip = hoodTip; c.sideFront = sideFront; c.roundZ = roundZ; c.roundY = roundY;
        c.stripeWidth = stripeWidth; c.maxReclineDeg = maxReclineDeg; c.footrestTravel = footrestTravel;
        c.wicker = wicker.clone(); c.wood = wood.clone(); c.stripeLight = stripeLight.clone(); c.rope = rope.clone();
        c.palette = new double[palette.length][];
        for (int i = 0; i < palette.length; i++) c.palette[i] = palette[i].clone();
        return c;
    }

    // ------------------------------------------------------------------ ein Korb in der Szene

    /**
     * Ein Korb in der Szene: Ort, Blickrichtung (Grad von Nord), Kennzahl; dazu seine Stellung und die Farbe der Streifen.
     * Die Stellung darf die App jederzeit ändern (danach {@code BeachChairGl.touch()}).
     */
    public static final class Chair {
        public double x, y, z, headingDeg;
        public long id;
        /** Rückenlehne 0 (aufrecht) … 1 (ganz hinten), Fußstützen 0 (drin) … 1 (ganz heraus). */
        public double recline, footrest;
        /** Abends verschlossen (Gitter vor der Öffnung, Lehne aufrecht, Fußstützen drin). */
        public boolean closed;
        /** Dunkler Streifen (linear). */
        public double[] stripe = {0.03, 0.12, 0.45};

        public Chair() { }

        public Chair(double x, double y, double z, double headingDeg, long id) {
            this.x = x; this.y = y; this.z = z; this.headingDeg = headingDeg; this.id = id;
        }
    }

    /** Ein Korb mit Farbe aus der Palette und einer Stellung nach {@link #pose(Chair, double, double)} (offen, tagsüber). */
    public Chair chair(double x, double y, double z, double headingDeg, long id) {
        Chair c = new Chair(x, y, z, headingDeg, id);
        c.stripe = stripeFor(id);
        pose(c, 1, 0.5);
        return c;
    }

    /** Farbe der Streifen für eine Kennzahl, nach den Gewichten der Palette (gleicher seed, gleiche Farbe). */
    public double[] stripeFor(long id) {
        double sum = 0;
        for (double[] p : palette) sum += Math.max(0, p[3]);
        double r = rnd(id, 1).nextDouble() * sum;
        for (double[] p : palette) {
            r -= Math.max(0, p[3]);
            if (r <= 0) return new double[]{p[0], p[1], p[2]};
        }
        double[] p = palette[palette.length - 1];
        return new double[]{p[0], p[1], p[2]};
    }

    /**
     * Stellung nach dem Tag [G]: {@code open} ist der Anteil offener Körbe (0 nachts, 1 mittags in der Saison), {@code lounge}
     * wie weit die Gäste es sich bequem machen (0..1). Offene Körbe haben die Lehne zum Teil hinten und die Fußstützen
     * zum Teil heraus; die übrigen sind verschlossen. Gleicher seed und gleiche Kennzahl ergeben dieselbe Stellung.
     */
    public void pose(Chair c, double open, double lounge) {
        SplittableRandom r = rnd(c.id, 2);
        double o = r.nextDouble(), a = r.nextDouble(), b = r.nextDouble(), d = r.nextDouble();
        c.closed = o >= open;
        if (c.closed) { c.recline = 0; c.footrest = 0; return; }
        double l = Math.max(0, Math.min(1, lounge));
        c.recline = a < 0.35 ? 0 : Math.min(1, b * (0.4 + 0.8 * l));
        c.footrest = d < 0.4 + 0.3 * (1 - l) ? 0 : Math.min(1, 0.5 + 0.5 * a);
    }

    /**
     * Wohin ein Korb schaut, wenn die Gäste ihn drehen [G]: zur See ({@code seaDeg}), bei frischem Wind (ab 8 m/s) mit dem
     * Rücken in den Wind, aber nie mehr als 60° von der See weg.
     */
    public static double facing(double seaDeg, double windFromDeg, double windSpeed, long id) {
        double jitter = (new SplittableRandom(id * 0x9E3779B97F4A7C15L + 7).nextDouble() - 0.5) * 16;
        if (windSpeed < 8) return seaDeg + jitter;
        double lee = windFromDeg + 180, d = ((lee - seaDeg) % 360 + 540) % 360 - 180;
        double k = Math.min(1, (windSpeed - 8) / 6);
        return seaDeg + Math.max(-60, Math.min(60, d)) * k + jitter * (1 - k * 0.5);
    }

    private SplittableRandom rnd(long id, int salt) {
        return new SplittableRandom(seed * 0x9E3779B97F4A7C15L + id * 0xBF58476D1CE4E5B9L + salt * 0x94D049BB133111EBL);
    }

    // ------------------------------------------------------------------ Maße für die App

    /** Oberkante des Unterkastens (unter dem Polster), m. */
    public double baseTop() { return seatHeight - cushion; }
    /** Gelenk der Rückenlehne {z, y} am Korb: hinten auf dem Unterkasten. */
    public double[] hinge() { return new double[]{-depth / 2, baseTop()}; }
    /** Wo jemand sitzt: {x, y, z} am Korb (Mitte des Polsters, y = Oberkante). */
    public double[] seat() { return new double[]{0, seatHeight, 0.02}; }
    /** Innere halbe Breite (zwischen den Seitenwänden), m. */
    public double innerHalf() { return width / 2 - wall; }
    /** Ebene der Öffnung vorn (z, m) für den Schatten im Korb, und wie hoch sie reicht (y, m). */
    public double[] opening() { return new double[]{(sideFront + hoodTip) / 2, height - 0.12}; }
    /** Winkel der Rückenlehne in Bogenmaß für eine Stellung 0..1. */
    public double reclineRad(double r) { return Math.toRadians(maxReclineDeg) * Math.max(0, Math.min(1, r)); }

    /**
     * Umriss der Seitenwand (y-z-Ebene, {z, y}) von hinten unten über die Haube bis vorn unten; die äußere Haut der Haube
     * folgt ihm von hinten bis zur Spitze. Ostsee: Viertelellipse, Nordsee: eine Fase.
     */
    public double[][] profile(int roundSegments) {
        double zb = -depth / 2, y0 = baseTop(), yArc = height - roundY, zc = zb + roundZ;
        List<double[]> p = new ArrayList<>();
        p.add(new double[]{zb, y0});
        int n = type == Type.NORDSEE ? 1 : Math.max(1, roundSegments);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI - (Math.PI / 2) * i / n;
            p.add(new double[]{zc + Math.cos(a) * roundZ, yArc + Math.sin(a) * roundY});
        }
        // oben nach vorn bis zur Spitze; Ostsee leicht fallend, Nordsee gerade
        double tipY = height - (type == Type.NORDSEE ? 0.06 : 0.03);
        p.add(new double[]{hoodTip, tipY});
        return p.toArray(new double[0][]);
    }

    /** Vorderkante der Seitenwand von der Spitze der Haube bis zur Armlehne (geschwungen bei Ostsee). */
    double[][] frontEdge(int segs) {
        double tipY = height - (type == Type.NORDSEE ? 0.06 : 0.03);
        int n = type == Type.NORDSEE ? 1 : Math.max(1, segs);
        double[][] e = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            // Ostsee: die Kante läuft oben steil, unten nach vorn aus (Viertelbogen) [G]
            double s = type == Type.NORDSEE ? t : 1 - Math.cos(t * Math.PI / 2);
            double z = hoodTip + (sideFront - hoodTip) * s;
            double y = tipY + (armrestHeight - tipY) * t;
            e[i] = new double[]{z, y};
        }
        return e;
    }

    // ------------------------------------------------------------------ Netz

    /** Ein Dreiecksnetz eines Korbs, {@link #STRIDE} floats je Ecke, ohne Indizes. */
    public static final class Mesh {
        public final float[] data;
        public final int vertices;
        public final Lod lod;

        Mesh(float[] data, int vertices, Lod lod) { this.data = data; this.vertices = vertices; this.lod = lod; }

        public int triangles() { return vertices / 3; }
        public double x(int i) { return data[i * STRIDE]; }
        public double y(int i) { return data[i * STRIDE + 1]; }
        public double z(int i) { return data[i * STRIDE + 2]; }
        public int part(int i) { return Math.round(data[i * STRIDE + 8]); }
        public int group(int i) { return Math.round(data[i * STRIDE + 9]); }
        public boolean inside(int i) { return data[i * STRIDE + 10] > 0.5f; }

        /** Umgebender Quader {x0, y0, z0, x1, y1, z1}. */
        public double[] bounds() {
            double[] b = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
            for (int i = 0; i < vertices; i++) {
                double[] p = {x(i), y(i), z(i)};
                for (int k = 0; k < 3; k++) { b[k] = Math.min(b[k], p[k]); b[k + 3] = Math.max(b[k + 3], p[k]); }
            }
            return b;
        }

        /** Dreiecke je Teil. */
        public int[] perPart() {
            int[] c = new int[PART_NAMES.length];
            for (int i = 0; i < vertices; i += 3) c[part(i)]++;
            return c;
        }
    }

    /** Zahl der Segmente der Rundung je Stufe. */
    int roundSegments(Lod lod) {
        if (lod == Lod.FAR) return 2;
        if (lod == Lod.MID) return 3;
        return switch (quality) { case LOW -> 4; case MEDIUM -> 6; case HIGH -> 8; case ULTRA -> 12; };
    }

    /** Das Netz in der Grundstellung (Lehne aufrecht, Fußstützen drin, Verschluss mit Gruppe {@link #COVER}). */
    public Mesh mesh(Lod lod) {
        Builder b = new Builder();
        int seg = roundSegments(lod);
        boolean near = lod == Lod.NEAR, far = lod == Lod.FAR;
        double w2 = width / 2, d2 = depth / 2, t = wall, base = baseTop(), arm = armrestHeight;
        double[][] prof = profile(seg);
        double[][] edge = frontEdge(near ? 4 : far ? 1 : 2);

        // --- Unterkasten: Seiten und Rücken Geflecht, vorn Holz; Kufen darunter
        double yk = far ? 0 : 0.05;
        b.box(-w2, w2, yk, base, -d2, d2, new int[]{WICKER, WICKER, -1, WOOD, WICKER, WOOD}, FIXED, false);
        if (!far) {
            // Kufen unter den Seiten
            for (int s = -1; s <= 1; s += 2) b.box(s * w2, s * (w2 - 0.07), 0, 0.05, -d2 - 0.02, d2 + 0.02, allWood(), FIXED, false);
        }
        if (near) {
            // Holzrahmen an den Seiten des Unterkastens: Pfosten vorn und hinten, Riegel oben
            for (int s = -1; s <= 1; s += 2) {
                double xo = s * (w2 + 0.012), xi = s * (w2 - 0.004);
                double xa = Math.min(xo, xi), xb = Math.max(xo, xi);
                b.box(xa, xb, 0.05, base, d2 - 0.07, d2, allWood(), FIXED, false);
                b.box(xa, xb, 0.05, base, -d2, -d2 + 0.06, allWood(), FIXED, false);
                b.box(xa, xb, base - 0.05, base, -d2, d2, allWood(), FIXED, false);
            }
        }

        // --- Fußstützen: zwei Kästen vorn im Unterkasten, gleiten nach vorn
        if (!far) {
            double fy0 = 0.09, fy1 = base - 0.04, fz0 = d2 - footrestTravel - 0.02, fz1 = d2 + 0.012;
            for (int s = -1; s <= 1; s += 2) {
                double xa = s < 0 ? -w2 + 0.07 : 0.025, xb = s < 0 ? -0.025 : w2 - 0.07;
                b.box(xa, xb, fy0, fy1, fz0, fz1, allWood(), FOOT, false);
                if (near) {
                    // Griff aus Tau: ein Bogen aus sechs Stücken vorn auf der Klappe
                    double cx = (xa + xb) / 2, cy = (fy0 + fy1) / 2 + 0.02, r = 0.035;
                    double[] prev = null;
                    for (int k = 0; k <= 6; k++) {
                        double a = Math.PI * k / 6;
                        double[] q = {cx + Math.cos(a) * r, cy - Math.sin(a) * r * 0.8, fz1 + 0.012};
                        if (prev != null) b.beam(prev, q, 0.012, ROPE, FOOT);
                        prev = q;
                    }
                }
            }
        }

        // --- Sitzpolster
        double ih = w2 - t;
        b.box(-ih, ih, base, seatHeight, -d2 + t, d2 - 0.01, new int[]{CUSHION, CUSHION, CUSHION, CUSHION, CUSHION, CUSHION}, FIXED, true);

        // --- untere Seitenteile (fest): vom Unterkasten bis zur Armlehne
        double lowFront = sideFront + 0.02;
        for (int s = -1; s <= 1; s += 2) {
            double xo = s * w2, xi = s * ih;
            b.wallPanel(xo, xi, new double[][]{{-d2, base}, {lowFront, base}, {lowFront, arm}, {-d2, arm}}, new boolean[]{false, true, false, false},
                    WICKER, CANVAS, WICKER, FIXED, far);
        }

        // --- Armlehnen aus Holz, vorn ein Pfosten bis zum Boden
        for (int s = -1; s <= 1; s += 2) {
            double xa = s < 0 ? -w2 - 0.025 : w2 - 0.075, xb = s < 0 ? -w2 + 0.075 : w2 + 0.025;
            b.box(xa, xb, arm, arm + 0.035, -d2 + 0.04, d2 + 0.05, allWood(), FIXED, false);
            if (!far) {
                double pa = s < 0 ? -w2 - 0.015 : w2 - 0.06, pb = s < 0 ? -w2 + 0.06 : w2 + 0.015;
                b.box(pa, pb, near ? 0.05 : 0, arm, d2 - 0.02, d2 + 0.045, allWood(), FIXED, false);
            }
        }

        // --- Oberteil (kippt): Seitenwände über der Armlehne, Haube und Rücken als Schale
        //     Seitenwand: Umriss aus Haube (hinten bis Spitze), Vorderkante bis zur Armlehne, Armlehne nach hinten
        List<double[]> side = new ArrayList<>();
        // von hinten in Höhe der Armlehne nach oben über die Haube
        side.add(new double[]{-d2, arm});
        for (int i = 1; i < prof.length; i++) side.add(prof[i]);
        for (int i = 1; i < edge.length; i++) side.add(edge[i]);
        double[][] sidePoly = side.toArray(new double[0][]);
        // Kanten nur vorn und unten (hinten und oben liegt die Schale darüber)
        boolean[] rims = new boolean[sidePoly.length];
        for (int i = prof.length - 1; i < sidePoly.length; i++) rims[i] = true;
        for (int s = -1; s <= 1; s += 2) b.wallPanel(s * w2, s * ih, sidePoly, rims, WICKER, CANVAS, WICKER, BACK, far);

        // Schale: außen Geflecht über die ganze Breite, innen Stoff zwischen den Seitenwänden
        b.shell(prof, w2, ih, t, far);

        // Volant: ein Streifen Stoff an der Spitze der Haube
        double tipY = prof[prof.length - 1][1];
        if (!far) {
            double vz = hoodTip + 0.004;
            b.quad(new double[]{-w2, tipY, vz}, new double[]{w2, tipY, vz}, new double[]{w2, tipY - 0.10, vz}, new double[]{-w2, tipY - 0.10, vz},
                    new double[]{0, 0, 1}, CANVAS, BACK, 0);
            b.quad(new double[]{w2, tipY, vz - 0.004}, new double[]{-w2, tipY, vz - 0.004}, new double[]{-w2, tipY - 0.10, vz - 0.004}, new double[]{w2, tipY - 0.10, vz - 0.004},
                    new double[]{0, 0, -1}, CANVAS, BACK, 1);
        }

        // Rückenpolster vor der Rückwand (kippt mit)
        if (!far) {
            double bz0 = -d2 + t, bz1 = bz0 + 0.07;
            b.box(-ih + 0.01, ih - 0.01, seatHeight + 0.02, seatHeight + 0.52, bz0, bz1, new int[]{CUSHION, CUSHION, CUSHION, CUSHION, CUSHION, CUSHION}, BACK, true);
        }

        // --- Verschluss (abends): Gitter aus Latten vor der Öffnung, unten senkrecht, oben längs der Vorderkante
        {
            double off = lowFront - sideFront + 0.012, cz = sideFront + off;
            double[] a0 = {-w2, base, cz}, a1 = {w2, base, cz}, a2 = {w2, arm, cz}, a3 = {-w2, arm, cz};
            b.quad(a0, a1, a2, a3, null, SLATS, COVER, 0);
            double[][] e = edge;
            for (int i = e.length - 1; i > 0; i--) {
                double[] lo = e[i], hi = e[i - 1];
                double zl = lo[0] + off, zh = hi[0] + off;
                b.quad(new double[]{-w2, lo[1], zl}, new double[]{w2, lo[1], zl}, new double[]{w2, hi[1], zh}, new double[]{-w2, hi[1], zh}, null, SLATS, COVER, 0);
            }
        }
        return b.build(lod);
    }

    private static int[] allWood() { return new int[]{WOOD, WOOD, WOOD, WOOD, WOOD, WOOD}; }

    /**
     * Ein Netz in einer Stellung (wie der Shader es macht): Lehne um das Gelenk gekippt, Fußstützen heraus, der Verschluss
     * nur bei {@code closed}. Für Prüfungen und Ausgaben ohne Grafikkarte.
     */
    public Mesh posed(Mesh m, Chair c) {
        float[] d = m.data.clone();
        double rec = c.closed ? 0 : reclineRad(c.recline), foot = c.closed ? 0 : footrestTravel * Math.max(0, Math.min(1, c.footrest));
        double[] h = hinge();
        double cr = Math.cos(rec), sr = Math.sin(rec);
        List<float[]> keep = new ArrayList<>();
        for (int i = 0; i < m.vertices; i += 3) {
            int g = Math.round(d[i * STRIDE + 9]);
            if (g == COVER && !c.closed) continue;
            float[] tri = new float[STRIDE * 3];
            for (int k = 0; k < 3; k++) {
                int o = (i + k) * STRIDE;
                System.arraycopy(d, o, tri, k * STRIDE, STRIDE);
                int q = k * STRIDE;
                if (g == BACK) {
                    double dz = tri[q + 2] - h[0], dy = tri[q + 1] - h[1];
                    tri[q + 2] = (float) (h[0] + dz * cr - dy * sr);
                    tri[q + 1] = (float) (h[1] + dz * sr + dy * cr);
                    double nz = tri[q + 5], ny = tri[q + 4];
                    tri[q + 5] = (float) (nz * cr - ny * sr);
                    tri[q + 4] = (float) (nz * sr + ny * cr);
                } else if (g == FOOT) tri[q + 2] += (float) foot;
            }
            keep.add(tri);
        }
        float[] out = new float[keep.size() * STRIDE * 3];
        for (int i = 0; i < keep.size(); i++) System.arraycopy(keep.get(i), 0, out, i * STRIDE * 3, STRIDE * 3);
        return new Mesh(out, keep.size() * 3, m.lod);
    }

    /**
     * Punkte des Umrisses für den Schatten am Korb ({x, y, z}): die Ecken des Unterkastens, der Umriss der Seitenwände
     * links und rechts (in der Stellung). Die Darstellung wirft sie mit der Sonne auf den Boden.
     */
    public double[][] silhouette(Chair c) {
        double[][] prof = profile(3);
        double[][] edge = frontEdge(2);
        double w2 = width / 2, d2 = depth / 2;
        double rec = c.closed ? 0 : reclineRad(c.recline), cr = Math.cos(rec), sr = Math.sin(rec);
        double[] h = hinge();
        List<double[]> l = new ArrayList<>();
        for (int s = -1; s <= 1; s += 2) {
            double x = s * w2;
            l.add(new double[]{x, 0, -d2});
            l.add(new double[]{x, 0, d2 + 0.04});
            l.add(new double[]{x, armrestHeight, d2 + 0.04});
            List<double[]> up = new ArrayList<>();
            for (int i = 1; i < prof.length; i++) up.add(prof[i]);
            for (int i = 1; i < edge.length; i++) up.add(edge[i]);
            for (double[] p : up) {
                double dz = p[0] - h[0], dy = p[1] - h[1];
                l.add(new double[]{x, h[1] + dz * sr + dy * cr, h[0] + dz * cr - dy * sr});
            }
        }
        return l.toArray(new double[0][]);
    }

    /** „Ostsee-Strandkorb 1,24 × 0,80 × 1,60 m · nah 1 512, mittel 380, fern 96 Dreiecke“ */
    public String describe() {
        return String.format(Locale.GERMAN, "%s-Strandkorb %.2f × %.2f × %.2f m, Sitz %.2f m · nah %,d, mittel %,d, fern %,d Dreiecke · %d Farben",
                type == Type.OSTSEE ? "Ostsee" : "Nordsee", width, depth, height, seatHeight,
                mesh(Lod.NEAR).triangles(), mesh(Lod.MID).triangles(), mesh(Lod.FAR).triangles(), palette.length);
    }

    // ------------------------------------------------------------------ Baukasten

    /** Sammelt Dreiecke mit Normalen und Musterkoordinaten. */
    private final class Builder {
        float[] d = new float[STRIDE * 3 * 256];
        int n;

        void vert(double[] p, double[] nrm, double u, double v, int part, int group, int inside) {
            if (n + STRIDE > d.length) d = java.util.Arrays.copyOf(d, d.length * 2);
            d[n++] = (float) p[0]; d[n++] = (float) p[1]; d[n++] = (float) p[2];
            d[n++] = (float) nrm[0]; d[n++] = (float) nrm[1]; d[n++] = (float) nrm[2];
            d[n++] = (float) u; d[n++] = (float) v;
            d[n++] = part; d[n++] = group; d[n++] = inside;
        }

        /** Musterkoordinaten aus der Lage der Fläche: Seitenflächen (z, y), Deckel (x, z), sonst (x, y). */
        double[] uv(double[] p, double[] nrm) {
            double ax = Math.abs(nrm[0]), ay = Math.abs(nrm[1]), az = Math.abs(nrm[2]);
            if (ax >= ay && ax >= az) return new double[]{p[2], p[1]};
            if (ay >= az) return new double[]{p[0], p[2]};
            return new double[]{p[0], p[1]};
        }

        void tri(double[] a, double[] b, double[] c, double[] nrm, int part, int group, int inside) {
            if (nrm == null) nrm = normal(a, b, c);
            double[] ua = uv(a, nrm), ub = uv(b, nrm), uc = uv(c, nrm);
            vert(a, nrm, ua[0], ua[1], part, group, inside);
            vert(b, nrm, ub[0], ub[1], part, group, inside);
            vert(c, nrm, uc[0], uc[1], part, group, inside);
        }

        void quad(double[] a, double[] b, double[] c, double[] e, double[] nrm, int part, int group, int inside) {
            if (nrm == null) nrm = normal(a, b, c);
            tri(a, b, c, nrm, part, group, inside);
            tri(a, c, e, nrm, part, group, inside);
        }

        double[] normal(double[] a, double[] b, double[] c) {
            double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            return l < 1e-12 ? new double[]{0, 1, 0} : new double[]{nx / l, ny / l, nz / l};
        }

        /**
         * Ein Quader; Teile je Fläche in der Reihenfolge links (−x), rechts (+x), unten, oben, hinten (−z), vorn (+z);
         * −1 lässt die Fläche weg.
         */
        void box(double x0, double x1, double y0, double y1, double z0, double z1, int[] parts, int group, boolean inside) {
            if (x1 < x0) { double s = x0; x0 = x1; x1 = s; }
            int in = inside ? 1 : 0;
            double[][] c = new double[8][];
            for (int k = 0; k < 8; k++) c[k] = new double[]{(k & 1) == 0 ? x0 : x1, (k & 2) == 0 ? y0 : y1, (k & 4) == 0 ? z0 : z1};
            if (parts[0] >= 0) quad(c[0], c[4], c[6], c[2], new double[]{-1, 0, 0}, parts[0], group, in);
            if (parts[1] >= 0) quad(c[1], c[3], c[7], c[5], new double[]{1, 0, 0}, parts[1], group, in);
            if (parts[2] >= 0) quad(c[0], c[1], c[5], c[4], new double[]{0, -1, 0}, parts[2], group, in);
            if (parts[3] >= 0) quad(c[2], c[6], c[7], c[3], new double[]{0, 1, 0}, parts[3], group, in);
            if (parts[4] >= 0) quad(c[0], c[2], c[3], c[1], new double[]{0, 0, -1}, parts[4], group, in);
            if (parts[5] >= 0) quad(c[4], c[5], c[7], c[6], new double[]{0, 0, 1}, parts[5], group, in);
        }

        /** Ein dünner Balken von a nach b mit quadratischem Querschnitt (Tau, Griffe). */
        void beam(double[] a, double[] b, double w, int part, int group) {
            double ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2], l = Math.sqrt(ax * ax + ay * ay + az * az);
            if (l < 1e-6) return;
            ax /= l; ay /= l; az /= l;
            double[] s = Math.abs(az) < 0.9 ? cross(new double[]{ax, ay, az}, new double[]{0, 0, 1}) : cross(new double[]{ax, ay, az}, new double[]{0, 1, 0});
            double[] u = cross(new double[]{ax, ay, az}, s);
            double h = w / 2;
            double[][] c = new double[8][];
            for (int k = 0; k < 8; k++) {
                double[] o = (k & 4) == 0 ? a : b;
                double ss = ((k & 1) == 0 ? -h : h), uu = ((k & 2) == 0 ? -h : h);
                c[k] = new double[]{o[0] + s[0] * ss + u[0] * uu, o[1] + s[1] * ss + u[1] * uu, o[2] + s[2] * ss + u[2] * uu};
            }
            int[][] f = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}};
            for (int[] q : f) quad(c[q[0]], c[q[1]], c[q[2]], c[q[3]], null, part, group, 0);
        }

        double[] cross(double[] a, double[] b) {
            double x = a[1] * b[2] - a[2] * b[1], y = a[2] * b[0] - a[0] * b[2], z = a[0] * b[1] - a[1] * b[0], l = Math.sqrt(x * x + y * y + z * z);
            return new double[]{x / l, y / l, z / l};
        }

        /**
         * Eine Wand mit dem Umriss {@code poly} ({z, y}, sternförmig um seinen Schwerpunkt) zwischen der Außenseite bei
         * {@code xo} und der Innenseite bei {@code xi}; Kanten, wo {@code rims} es sagt (Kante i von Punkt i nach i + 1).
         * Fern nur die Außenseite.
         */
        void wallPanel(double xo, double xi, double[][] poly, boolean[] rims, int outer, int inner, int rim, int group, boolean far) {
            double cz = 0, cy = 0;
            for (double[] p : poly) { cz += p[0]; cy += p[1]; }
            cz /= poly.length; cy /= poly.length;
            double so = Math.signum(xo - xi);
            int n = poly.length;
            for (int i = 0; i < n; i++) {
                double[] p = poly[i], q = poly[(i + 1) % n];
                double[] o0 = {xo, cy, cz}, o1 = {xo, p[1], p[0]}, o2 = {xo, q[1], q[0]};
                // Außenseite: Normale ±x
                double[] no = {so, 0, 0};
                orient(o0, o1, o2, no, outer, group, 0);
                if (far) continue;
                double[] i0 = {xi, cy, cz}, i1 = {xi, p[1], p[0]}, i2 = {xi, q[1], q[0]};
                orient(i0, i1, i2, new double[]{-so, 0, 0}, inner, group, 1);
                // Kante zwischen p und q
                if (!rims[i]) continue;
                double ez = q[0] - p[0], ey = q[1] - p[1], el = Math.hypot(ez, ey);
                if (el < 1e-6) continue;
                double[] en = {0, -ez / el, ey / el};
                // Kante nach außen (weg vom Schwerpunkt)
                double mz = (p[0] + q[0]) / 2 - cz, my = (p[1] + q[1]) / 2 - cy;
                if (en[2] * mz + en[1] * my < 0) { en[1] = -en[1]; en[2] = -en[2]; }
                quad(new double[]{xo, p[1], p[0]}, new double[]{xi, p[1], p[0]}, new double[]{xi, q[1], q[0]}, new double[]{xo, q[1], q[0]}, en, rim, group, 0);
            }
        }

        /** Ein Dreieck so herum, dass seine Normale zu {@code nrm} passt. */
        void orient(double[] a, double[] b, double[] c, double[] nrm, int part, int group, int inside) {
            double[] f = normal(a, b, c);
            if (f[0] * nrm[0] + f[1] * nrm[1] + f[2] * nrm[2] < 0) tri(a, c, b, nrm, part, group, inside);
            else tri(a, b, c, nrm, part, group, inside);
        }

        /**
         * Die Schale aus Rücken und Haube längs des Umrisses: außen Geflecht von −w2 bis w2, innen Stoff zwischen den
         * Seitenwänden (um die Wandstärke nach innen versetzt), vorn an der Spitze eine Kante. Musterkoordinaten u = x,
         * v = Weg längs des Umrisses (die Streifen laufen über die Haube nach hinten und am Rücken hinunter).
         */
        void shell(double[][] prof, double w2, double ih, double t, boolean far) {
            int n = prof.length;
            double[][] nrm = new double[n][];
            for (int i = 0; i < n; i++) {
                double[] a = prof[Math.max(0, i - 1)], b = prof[Math.min(n - 1, i + 1)];
                double tz = b[0] - a[0], ty = b[1] - a[1], l = Math.hypot(tz, ty);
                // nach außen: der Umriss läuft hinten hinauf und oben nach vorn, außen liegt links davon (−z, +y)
                nrm[i] = new double[]{0, tz / l, -ty / l};
            }
            double[] s = new double[n];
            for (int i = 1; i < n; i++) s[i] = s[i - 1] + Math.hypot(prof[i][0] - prof[i - 1][0], prof[i][1] - prof[i - 1][1]);
            double[][] in = new double[n][];
            for (int i = 0; i < n; i++) in[i] = new double[]{prof[i][0] - nrm[i][2] * t, prof[i][1] - nrm[i][1] * t};
            // unten am Rücken beginnt die Innenseite über dem Polster
            in[0][1] = Math.max(in[0][1], prof[0][1]);
            for (int i = 0; i + 1 < n; i++) {
                double[] p = prof[i], q = prof[i + 1];
                // außen
                double[] a = {-w2, p[1], p[0]}, b = {w2, p[1], p[0]}, c = {w2, q[1], q[0]}, e = {-w2, q[1], q[0]};
                smooth(a, b, c, e, nrm[i], nrm[i], nrm[i + 1], nrm[i + 1], s[i], s[i + 1], WICKER, BACK, 0, true);
                if (far) continue;
                double[] pi = in[i], qi = in[i + 1];
                double[] ai = {-ih, pi[1], pi[0]}, bi = {ih, pi[1], pi[0]}, ci = {ih, qi[1], qi[0]}, ei = {-ih, qi[1], qi[0]};
                double[] m0 = {0, -nrm[i][1], -nrm[i][2]}, m1 = {0, -nrm[i + 1][1], -nrm[i + 1][2]};
                smooth(ai, bi, ci, ei, m0, m0, m1, m1, s[i], s[i + 1], CANVAS, BACK, 1, false);
            }
            if (!far) {
                // Kante an der Spitze der Haube
                double[] p = prof[n - 1], q = in[n - 1];
                quad(new double[]{-w2, p[1], p[0]}, new double[]{w2, p[1], p[0]}, new double[]{w2, q[1], q[0]}, new double[]{-w2, q[1], q[0]},
                        new double[]{0, 0, 1}, WOOD, BACK, 0);
            }
        }

        /** Viereck mit Normalen je Ecke; u = x, v = Weg längs des Umrisses; Umlauf passend zur Normale. */
        void smooth(double[] a, double[] b, double[] c, double[] e, double[] na, double[] nb, double[] nc, double[] ne, double va, double vc,
                    int part, int group, int inside, boolean outward) {
            double[] f = normal(a, b, c);
            boolean flip = f[0] * na[0] + f[1] * na[1] + f[2] * na[2] < 0;
            if (!flip) {
                vert(a, na, a[0], va, part, group, inside); vert(b, nb, b[0], va, part, group, inside); vert(c, nc, c[0], vc, part, group, inside);
                vert(a, na, a[0], va, part, group, inside); vert(c, nc, c[0], vc, part, group, inside); vert(e, ne, e[0], vc, part, group, inside);
            } else {
                vert(a, na, a[0], va, part, group, inside); vert(c, nc, c[0], vc, part, group, inside); vert(b, nb, b[0], va, part, group, inside);
                vert(a, na, a[0], va, part, group, inside); vert(e, ne, e[0], vc, part, group, inside); vert(c, nc, c[0], vc, part, group, inside);
            }
        }

        Mesh build(Lod lod) { return new Mesh(java.util.Arrays.copyOf(d, n), n / STRIDE, lod); }
    }
}
