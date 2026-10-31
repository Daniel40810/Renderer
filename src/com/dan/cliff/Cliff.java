package com.dan.cliff;

import java.util.Locale;

/**
 * Eine Steilwand als Material und Feinform für jede Gebirgsszene, Kern ohne Grafikkarte. Dieselben Formeln stehen in
 * {@code gl/cliff.glsl}; der Kern rechnet sie für Prüfungen, Abfragen und den Selbsttest der Grafikkarte nach.
 * <ul>
 * <li><b>Gneis</b> in Bändern entlang der Schieferung (eine schwach geneigte Fläche, {@link #foliationDipDeg},
 * {@link #foliationDipDirDeg}), hell und dunkel in drei Maßstäben (1,6 m, 7 m, 33 m), dazu rötliche Feldspatlagen;
 * die Bänder sind mit 3D-Rauschen verbogen, darum laufen sie als Linien quer über jede Wand.</li>
 * <li><b>Klüfte</b>: zwei senkrechte Scharen (Streichen {@link #jointADeg}, {@link #jointBDeg}, Abstand
 * {@link #jointSpacing}) als dunkle Fugen.</li>
 * <li><b>Feinform</b>: Relief entlang der Normale in bis zu vier Maßstäben ({@link #reliefScale}, Höhe
 * {@link #reliefAmp}); der Scheitelshader verschiebt die Wand damit, der Bildpunkt neigt die Normale aus demselben
 * Relief.</li>
 * <li><b>Bewuchs und Wetter</b>: Flechten auf trockenem, steilem Fels, Moos auf nassen Absätzen, nasse Streifen, wo
 * Wasser sickert (dunkel), Schnee auf Absätzen, Geröll am Fuß.</li>
 * </ul>
 * Alles ist 3D-Rauschen am Ort, keine Projektion auf Ebenen: senkrechte Stellen ziehen keine Streifen. Feine Anteile
 * blenden mit der Bildpunktgröße aus ({@code footprint}), damit nichts flimmert.
 * <p>
 * Eingänge: Lage und Normale, die Wandkarte ({@link CliffMap}: Wand, Geröll, Nässe, Absatz), Nässe aus dem Regen,
 * Schnee. Ausgänge: Farbe, Anteil Fels, geneigte Normale, Nässe ({@link Sample}); „Wand hier?“ ({@link #isWall},
 * {@link #wallAt}). Paketvertrag: nur Java 21 im Kern, JOGL in {@code gl/}, FStyle nur in {@code demo/}; alle Felder
 * öffentlich; gleicher {@link #seed}, gleiches Bild.
 */
public final class Cliff {
    /** Qualitätsstufen (eigene Kopie, Paketvertrag Punkt 7): Oktaven der Feinform, Zusatzgeometrie, Reichweite. */
    public enum Quality {
        LOW(2, false, 600), MEDIUM(3, true, 1500), HIGH(3, true, 2500), ULTRA(4, true, 4000);
        /** Oktaven des Reliefs, ob Überhänge gezeichnet werden, Entfernung bis zu der die Feinform die Wand verschiebt (m). */
        public final int octaves;
        public final boolean overhangs;
        public final double reliefRange;
        Quality(int o, boolean ov, double r) { octaves = o; overhangs = ov; reliefRange = r; }
    }

    /** Stand des Packages (für App und Prüfung). */
    public static final String STAND = "Engine (Phase 5), Granit seit Brocken Phase 8, Lockergestein seit Sylt Phase 4, Brodtener Ufer seit Timmendorf Phase 9";

    /**
     * Gestein (seit Brocken Phase 8): Gneis in Bändern entlang der Schieferung (Geiranger) oder Granit ohne Bänder, grob
     * gekörnt, mit Wollsackverwitterung – drei Kluftscharen (zwei senkrecht, eine waagerechte Bankung), an denen die Blöcke
     * gerundet sind wie Matratzen (Brocken: Teufelskanzel, Hexenaltar, Blockmeere).
     */
    public enum Rock { GNEISS, GRANIT, LOCKER }

    public volatile Rock rock = Rock.GNEISS;
    /** Granit: Abstand der waagerechten Bankung (m) und wie weit die Blöcke an den Klüften gerundet sind (Anteil des Abstands). */
    public volatile double sheetSpacing = 1.4, blockRound = 0.15;
    /** Seit Brocken Phase 8: Moos auf allem, was nach oben schaut (0..1; Blöcke im feuchten Wald), unabhängig von Absätzen. */
    public volatile double topMoss = 0;

    public volatile Quality quality = Quality.MEDIUM;
    /** Zufall: gleicher Wert, gleiches Bild. */
    public volatile int seed = 1;
    /** Ab dieser Neigung (Grad) wird der Hang zu Fels, ab {@link #wallSlopeDeg} gilt er als Wand [G, nach den Fotos]. */
    public volatile double rockSlopeDeg = 38, wallSlopeDeg = 55;
    /** Schieferung: Fallen und Fallrichtung (Grad) [G: im Westnorwegischen Gneis flach, um 10° nach Nordwest]. */
    public volatile double foliationDipDeg = 10, foliationDipDirDeg = 315;
    /** Klüfte: Streichen der zwei Scharen (Grad von Nord), Abstand (m), Breite der Fuge (m). */
    public volatile double jointADeg = 30, jointBDeg = 115, jointSpacing = 9, jointWidth = 0.25;
    /** Feinform: Höhe (m) und größter Maßstab (m) des Reliefs; jede kleinere Oktave ein Drittel. */
    public volatile double reliefAmp = 4.0, reliefScale = 24;
    /** Farben (linear, Albedo): heller und dunkler Gneis, Feldspat, Flechten, Moos, Geröll, Schnee. */
    public volatile double[] gneissLight = {0.35, 0.34, 0.33}, gneissDark = {0.19, 0.19, 0.20}, feldspar = {0.44, 0.35, 0.31},
            lichen = {0.27, 0.29, 0.22}, moss = {0.10, 0.16, 0.05}, talus = {0.30, 0.30, 0.28}, snow = {0.82, 0.84, 0.88};
    /** Anteile 0..1: Flechten, Moos, Sickerstreifen, Feldspat. */
    public volatile double lichenAmount = 0.5, mossAmount = 0.5, seepAmount = 0.35, feldsparAmount = 0.3;
    /**
     * Wie nass die Streifen unter den Fällen gerade sind, 0..1 (seit Geiranger Phase 7): die Wandkarte hält die Lage der
     * Streifen ({@link CliffMap#addFall}, {@link CliffMap#paintWet}), dieser Wert ihre Stärke nach dem Abfluss. 1 = so nass,
     * wie die Karte sagt.
     */
    public volatile double fallWet = 1;

    // ------------------------------------------------------------------ Lockergestein (seit Sylt Phase 4)

    /**
     * Lockergestein: die Schichtkarte (Oberkante, untere Grenze, Decke, Hohlkehle je Ort); null: die Vorgaben
     * {@link #strataDefault}. Nur für {@link Rock#LOCKER}.
     */
    public volatile StrataMap strata;
    /** Ohne Schichtkarte (oder außerhalb): Oberkante (m), untere Grenze (m), Decke (m), Hohlkehle (m). */
    public volatile double[] strataDefault = {28, 4, 1.5, 0};
    /**
     * Mächtigkeiten der Deckschichten von oben (m): Grasnarbe; unter dem Flugsand (Decke aus der Karte) der Bleichsand
     * (Ae), der dunkle Ortstein des Podsols (Bh/Bs), die Steinsohle auf dem Geschiebelehm; dazu die Eisenschwarte über
     * der unteren Grenze.
     */
    public volatile double sodThick = 0.12, bleachThick = 0.18, podsolThick = 0.35, stoneThick = 0.15, ironThick = 0.25;
    /** Farben (linear, Albedo): Lehm hell und dunkel, Kaolinsand hell und dunkel, Eisenschwarte, Flugsand, Bleichsand, Podsol, Steine, Grasnarbe, Gras. */
    public volatile double[] lehm = {0.42, 0.16, 0.05}, lehmDark = {0.29, 0.10, 0.035}, kaolin = {0.62, 0.56, 0.45}, kaolinDark = {0.50, 0.44, 0.34},
            iron = {0.19, 0.045, 0.012}, sand = {0.52, 0.43, 0.28}, bleach = {0.34, 0.30, 0.25}, podsol = {0.07, 0.035, 0.018},
            stone = {0.26, 0.24, 0.22}, sod = {0.045, 0.028, 0.014}, grass = {0.24, 0.25, 0.09};
    /** Runsen vom Regen: Abstand quer (m), Tiefe (m), Anteil 0..1. */
    public volatile double gullySpacing = 3.5, gullyDepth = 1.4, gullyAmount = 0.7;
    /** Rieselsand in den Runsen und in Zungen über dem Lehm 0..1; Trockenrisse im trockenen Lehm 0..1; Findlinge im Lehm 0..1. */
    public volatile double trickleAmount = 0.4, crackAmount = 0.5, erraticAmount = 0.5;
    /** Sickerstreifen unter der Grenze Sand/Lehm: wie weit sie hinabreichen (m); Stärke ist {@link #seepAmount}. */
    public volatile double seepLength = 7;
    /** Hohlkehle: Unterkante (m) und Höhe des Bands (m), in dem die Wand um die Tiefe aus der Karte zurückweicht. */
    public volatile double notchBase = 1.0, notchHeight = 2.6;

    /**
     * Das Rote Kliff bei Kampen auf Sylt (seit Sylt Phase 4) [G nach den Fotos RotesKliff1–5 und den Beschreibungen in
     * {@code docs/QUELLEN.md}]: oben die Grasnarbe mit Strandhafer, Flugsand, ein Podsol (Bleichsand über dunklem
     * Ortstein), die Steinsohle; darunter der rostrote Geschiebelehm der Saale-Eiszeit mit Findlingen, Trockenrissen und
     * Runsen; unten, im Süden über den Strand reichend, heller Kaolinsand (Ober-Pliozän) mit einer Eisenschwarte an der
     * Grenze. Wand ab 50°, Sturzkegel und Sandfuß am Fuß (Wandkarte: Geröll schon unter 4 m Wand).
     */
    public static Cliff rotesKliff() {
        Cliff c = new Cliff();
        c.rock = Rock.LOCKER;
        c.rockSlopeDeg = 40;
        c.wallSlopeDeg = 50;
        c.reliefAmp = 0.35;
        c.reliefScale = 4;
        c.lichenAmount = 0;
        c.mossAmount = 0;
        c.seepAmount = 0.55;
        c.feldsparAmount = 0;
        c.talus = new double[]{0.46, 0.33, 0.20};
        return c;
    }

    /**
     * Das Brodtener Ufer zwischen Niendorf und Travemünde (seit Timmendorf Phase 9) [G nach den Fotos Brodten1–3 und den
     * Beschreibungen in {@code docs/QUELLEN.md} der Szene]: ein Kliff aus Geschiebemergel der Weichsel-Eiszeit, 10 bis
     * 20 m hoch, oben Acker und Buchen über einem braunen Waldboden, darunter der graubraune, fleckige Mergel mit
     * Findlingen, Runsen und Quellhorizonten; keine untere Schicht (die Schichtkarte bekommt keine untere Grenze). Weil
     * das Gelände dort nur als 10-m-Raster vorliegt, gilt der Hang schon ab 30° als Fels und ab 42° als Wand. Am Fuß
     * Geröll und Steine aus dem ausgewaschenen Mergel.
     */
    public static Cliff brodtenerUfer() {
        Cliff c = new Cliff();
        c.rock = Rock.LOCKER;
        c.seed = 1872;
        c.rockSlopeDeg = 30;
        c.wallSlopeDeg = 42;
        c.reliefAmp = 0.3;
        c.reliefScale = 5;
        c.lichenAmount = 0;
        c.mossAmount = 0.15;
        c.seepAmount = 0.4;
        c.feldsparAmount = 0;
        c.strataDefault = new double[]{18, -1000, 0.2, 0};
        c.lehm = new double[]{0.40, 0.31, 0.20};
        c.lehmDark = new double[]{0.27, 0.24, 0.19};
        c.sand = new double[]{0.45, 0.38, 0.26};
        c.bleachThick = 0;
        c.podsol = new double[]{0.10, 0.065, 0.035};
        c.podsolThick = 0.45;
        c.ironThick = 0;
        c.stoneThick = 0.1;
        c.crackAmount = 0.3;
        c.erraticAmount = 0.6;
        c.gullySpacing = 6;
        c.gullyDepth = 0.8;
        c.gullyAmount = 0.5;
        c.trickleAmount = 0.2;
        c.talus = new double[]{0.38, 0.32, 0.24};
        return c;
    }

    /**
     * Granit vom Brocken [G nach Fotos und Beschreibungen]: hellgrau mit rötlichem Feldspat und dunklem Glimmer, dicht mit
     * gelbgrünen und grauen Krustenflechten; Klüfte im Abstand von rund 2,4 m, Bankung 1,4 m, die Blöcke stark gerundet
     * (Wollsäcke). Fels schon ab 30° Neigung (Blockhänge), Wand ab 50°.
     */
    public static Cliff granit() {
        Cliff c = new Cliff();
        c.rock = Rock.GRANIT;
        c.rockSlopeDeg = 30;
        c.wallSlopeDeg = 50;
        c.jointADeg = 40;
        c.jointBDeg = 130;
        c.jointSpacing = 2.4;
        c.jointWidth = 0.12;
        c.sheetSpacing = 1.4;
        c.blockRound = 0.15;
        c.reliefAmp = 0.7;
        c.reliefScale = 7;
        c.gneissLight = new double[]{0.27, 0.255, 0.24};
        c.gneissDark = new double[]{0.15, 0.145, 0.14};
        c.feldspar = new double[]{0.34, 0.25, 0.21};
        c.lichen = new double[]{0.25, 0.26, 0.17};
        c.moss = new double[]{0.08, 0.12, 0.04};
        c.talus = new double[]{0.21, 0.20, 0.185};
        c.lichenAmount = 0.75;
        c.mossAmount = 0.35;
        c.seepAmount = 0.12;
        c.feldsparAmount = 0.45;
        return c;
    }

    /** Ob eine Neigung (Grad) als Wand zählt. */
    public boolean isWall(double slopeDeg) { return slopeDeg >= wallSlopeDeg; }

    /** Höhenfeld von außen (das Package kennt kein Gelände). */
    public interface Heights {
        double height(double x, double z);
    }

    /** Wand am Ort 0..1 aus der Neigung des Höhenfelds (Abstand der Proben e m), weich um {@link #wallSlopeDeg}. */
    public double wallAt(Heights h, double x, double z, double e) {
        double gx = (h.height(x + e, z) - h.height(x - e, z)) / (2 * e), gz = (h.height(x, z + e) - h.height(x, z - e)) / (2 * e);
        double slope = Math.toDegrees(Math.atan(Math.hypot(gx, gz)));
        return smooth(wallSlopeDeg - 5, wallSlopeDeg + 5, slope);
    }

    // ------------------------------------------------------------------ Feinform

    /** Eine feste Drehung (orthonormal, Zeilen zuerst) gegen die Kästchen des Gitters, wie {@code CLIFF_ROT} in GLSL. */
    static final double[] ROT = {0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64};

    static double[] rot(double x, double y, double z) {
        return new double[]{ROT[0] * x + ROT[1] * y + ROT[2] * z, ROT[3] * x + ROT[4] * y + ROT[5] * z, ROT[6] * x + ROT[7] * y + ROT[8] * z};
    }

    /** Relief (m) am Ort, um 0; footprint (m je Bildpunkt) blendet feine Oktaven aus. Wie {@code cliffRelief}. */
    public double relief(double x, double y, double z, double footprint) {
        double s = reliefScale, a = reliefAmp, r = 0, px = x, py = y, pz = z;
        if (rock == Rock.GRANIT) {
            // Wollsäcke: das Polster zwischen den Klüften hebt die Mitte der Blöcke, die Feinform darüber ist schwächer
            r = reliefAmp * (granitBlock(x, y, z, footprint) - PILL_MEAN);
            a *= 0.35;
        } else if (rock == Rock.LOCKER) {
            r = -gullyDepth * gully(x, y, z) * CNoise.band(gullySpacing, footprint);
        }
        for (int o = 0; o < quality.octaves; o++) {
            // je Oktave gedreht, damit das Gitter des Rauschens keine Kästchen zeichnet
            double qx = ROT[0] * px + ROT[1] * py + ROT[2] * pz, qy = ROT[3] * px + ROT[4] * py + ROT[5] * pz, qz = ROT[6] * px + ROT[7] * py + ROT[8] * pz;
            px = qx; py = qy; pz = qz;
            double w = CNoise.band(s, footprint);
            // gratig: 1 − |2n − 1| hebt Rippen heraus, wie verwitterte Bänke
            double n = CNoise.value(px / s, py / s, pz / s, seed + 11 + o);
            r += a * w * (0.5 - Math.abs(2 * n - 1));
            s /= 3;
            a /= 2.6;
        }
        return r;
    }

    /**
     * Granit: wie weit der Ort im Inneren eines Blocks liegt (0 an der Kluft, 1 in der Mitte), aus drei Scharen: zwei
     * senkrecht (Streichen {@link #jointADeg}, {@link #jointBDeg}, Abstand {@link #jointSpacing}), eine waagerecht
     * ({@link #sheetSpacing}); gerundet über {@link #blockRound} des Abstands. In der Ferne zum Mittel 0,79. Wie
     * {@code cliffGranitBlock}.
     */
    public double granitBlock(double x, double y, double z, double footprint) {
        double pill = 1;
        for (int k = 0; k < 3; k++) {
            double sp, u, open;
            if (k < 2) {
                double a = Math.toRadians((k == 0 ? jointADeg : jointBDeg) + 90);
                sp = jointSpacing;
                u = (x * Math.sin(a) - z * Math.cos(a)) / sp + (CNoise.value(x / 25, y / 25, z / 25, seed + 8 + k) - 0.5) * 0.8
                        + (CNoise.value(x / 6, y / 6, z / 6, seed + 14 + k) - 0.5) * 0.35;
                // eine Kluft reißt nicht durch: stückweise offen, in der Höhe kürzer als in der Breite (versetzte Lagen)
                open = smooth(0.38, 0.55, CNoise.value(x / 9, y / 4, z / 9, seed + 40 + k));
            } else {
                sp = sheetSpacing;
                u = y / sp + (CNoise.value(x / 8, y / 8, z / 8, seed + 30) - 0.5) * 1.6 + (CNoise.value(x / 5, y / 5, z / 5, seed + 31) - 0.5) * 0.3;
                open = smooth(0.48, 0.62, CNoise.value(x / 7, y / 30, z / 7, seed + 42));
            }
            double toJoint = (0.5 - Math.abs(u - Math.floor(u) - 0.5)) * sp;
            pill *= 1 - open * (1 - smooth(0, blockRound * sp, toJoint));
        }
        return PILL_MEAN + (pill - PILL_MEAN) * CNoise.band(blockRound * sheetSpacing * 2, footprint);
    }

    /**
     * Lockergestein: Runse am Ort 0..1 (1 in der Rinne), aus senkrecht gestrecktem Rauschen (quer {@link #gullySpacing},
     * hoch das Neunfache: die Rinnen folgen der Falllinie jeder Wand). Wie {@code cliffGully}.
     */
    public double gully(double x, double y, double z) {
        double g = CNoise.value(x / gullySpacing, y / (gullySpacing * 9), z / gullySpacing, seed + 55);
        return smooth(0.55, 0.85, g) * gullyAmount;
    }

    /** Lockergestein: Form der Hohlkehle über der Höhe 0..1 (1 am tiefsten), im Band {@link #notchBase} + {@link #notchHeight}. Wie {@code cliffNotchShape}. */
    public double notchShape(double y) {
        double t = (y - notchBase) / notchHeight;
        if (t <= 0 || t >= 1) return 0;
        return Math.sin(Math.PI * Math.pow(t, 0.7));
    }

    /** Lockergestein: die vier Werte der Schichtkarte am Ort (sonst {@link #strataDefault}). */
    public double[] strataAt(double x, double z) {
        StrataMap m = strata;
        return m != null && m.covers(x, z) ? m.valuesAt(x, z) : strataDefault.clone();
    }

    /** Mittel von {@link #granitBlock} (in der Ferne). */
    static final double PILL_MEAN = 0.79;

    // ------------------------------------------------------------------ Material

    /** Ergebnis je Punkt: Farbe (Albedo, linear), Anteil Fels, Normale, Nässe 0..1, Schnee 0..1. */
    public static final class Sample {
        public final double[] albedo = new double[3], normal = new double[3];
        public double rock, wet, snow;
    }

    /**
     * Material am Ort (x, y, z) mit der Normale des Geländes (Einheitsvektor), der Wandkarte {@code map} (Wand, Geröll,
     * Nässe, Absatz; null = aus der Neigung), Nässe aus dem Regen (0..1), Schnee (0..1) und der Bildpunktgröße
     * footprint (m). Wie {@code cliffMaterial} in {@code gl/cliff.glsl}.
     */
    public Sample material(double x, double y, double z, double nx, double ny, double nz, double[] map, double rainWet, double snowAmt, double footprint) {
        return material(x, y, z, nx, ny, nz, map, rainWet, snowAmt, footprint, 0);
    }

    /**
     * Wie oben; {@code force} 0..1 macht den Ort zu Fels und Wand, unabhängig von der Neigung (seit Brocken Phase 8, für
     * Netze wie Felsburgen und Blöcke, deren Oberseiten flach sind). Wie {@code uCliffForce}.
     */
    public Sample material(double x, double y, double z, double nx, double ny, double nz, double[] map, double rainWet, double snowAmt, double footprint, double force) {
        Sample s = new Sample();
        double slope = 1 - ny;
        double rockFrom = 1 - Math.cos(Math.toRadians(rockSlopeDeg));
        double w0 = 1 - Math.cos(Math.toRadians(wallSlopeDeg - 5)), w1 = 1 - Math.cos(Math.toRadians(wallSlopeDeg + 5));
        double wall = Math.max(map != null ? map[0] : smooth(w0, w1, slope), force);
        double tal = map != null ? map[1] : 0, wetMap = map != null ? map[2] : 0, ledge = map != null ? map[3] : 0;
        s.rock = Math.max(Math.max(Math.max(smooth(rockFrom, rockFrom + 0.12, slope), wall), tal * 0.45), force);

        if (s.rock <= 0.003 && snowAmt <= 0) {   // ohne Fels und Schnee nichts weiter (wie cliff.glsl)
            System.arraycopy(talus, 0, s.albedo, 0, 3);
            s.normal[0] = nx; s.normal[1] = ny; s.normal[2] = nz;
            return s;
        }
        double[] col;
        double lehmShare = 0, topDepth = 0, lehmTop = 0;
        if (rock == Rock.LOCKER) {
            double[] st = strataAt(x, z);
            double[] q0 = rot(x, y, z);
            // Grenzen wellig (m): 3D-Rauschen hebt und senkt sie
            double wv = (CNoise.value(x / 9, y / 9, z / 9, seed + 51) - 0.5) * 0.8
                    + (CNoise.value(q0[0] / 2.5, q0[1] / 2.5, q0[2] / 2.5, seed + 52) - 0.5) * 0.3 * CNoise.band(2.5, footprint);
            double d = st[0] - y + wv, aa = Math.max(footprint, 0.04);
            double zN = sodThick, zF = zN + st[2], zB = zF + bleachThick, zP = zB + podsolThick, zS = zP + stoneThick;
            // Geschiebelehm: rostrot, fleckig, körnig
            double mot = 0.6 * CNoise.value(q0[0] / 4, q0[1] / 4, q0[2] / 4, seed + 58) + 0.4 * CNoise.value(q0[0] / 1.1, q0[1] / 1.1, q0[2] / 1.1, seed + 59);
            double[] lc = mix(lehmDark, lehm, smooth(0.25, 0.75, mot));
            lc = scale(lc, 1 + (CNoise.value(q0[0] / 0.3, q0[1] / 0.3, q0[2] / 0.3, seed + 60) - 0.5) * 0.15 * CNoise.band(0.3, footprint));
            // Findlinge im Lehm: Flint, Granit, Porphyr, grau bis rötlich
            double er = smooth(0.84, 0.87, CNoise.value(q0[0] / 0.5, q0[1] / 0.5, q0[2] / 0.5, seed + 53)) * erraticAmount * CNoise.band(0.5, footprint);
            lc = mix(lc, scale(stone, 0.7 + 0.6 * CNoise.value(q0[0] / 0.2, q0[1] / 0.2, q0[2] / 0.2, seed + 61)), er);
            // Steinsohle: Kies und Steine in einer dünnen Lage
            double[] sc = scale(stone, 0.6 + 0.8 * CNoise.value(q0[0] / 0.12, q0[1] / 0.12, q0[2] / 0.12, seed + 62) * CNoise.band(0.12, footprint)
                    + 0.4 * (1 - CNoise.band(0.12, footprint)));
            // Kaolinsand: hell, mit feinen waagerechten Lagen
            double lam = CNoise.value(x / 8, y / 0.35, z / 8, seed + 63) * CNoise.band(0.35, footprint) + 0.5 * (1 - CNoise.band(0.35, footprint));
            double[] kc = mix(kaolinDark, kaolin, smooth(0.2, 0.8, lam));
            // Schichten von oben: Grasnarbe, Flugsand, Bleichsand, Ortstein, Steinsohle, Lehm
            col = sod.clone();
            col = mix(col, sand, smooth(-aa, aa, d - zN));
            col = mix(col, bleach, smooth(-aa, aa, d - zF));
            col = mix(col, podsol, smooth(-aa, aa, d - zB));
            col = mix(col, sc, smooth(-aa, aa, d - zP));
            double inLehm = smooth(-aa, aa, d - zS);
            col = mix(col, lc, inLehm);
            // unten Kaolinsand, darüber die Eisenschwarte
            double yl = y - st[1] + wv * 0.5;
            double below = 1 - smooth(-aa, aa, yl);
            col = mix(col, iron, (1 - smooth(-aa, aa, yl - ironThick)) * (1 - below) * inLehm);
            col = mix(col, kc, below);
            lehmShare = inLehm * (1 - below);
            topDepth = d;
            lehmTop = d - zS;
            // Runsen und Rieselsand: Sand aus den Lagen darüber rieselt in die Rinnen und in Zungen über den Lehm
            double gu = gully(x, y, z) * wall;
            double tongue = smooth(0.7, 0.85, CNoise.value(x / 1.1, y / 25, z / 1.1, seed + 56));
            double tr = trickleAmount * Math.max(gu * 0.9, tongue * 0.6) * lehmShare;
            col = scale(col, 1 - 0.18 * gu * lehmShare);                 // in der Rinne Schatten
            col = mix(col, scale(sand, 0.95), tr);
            // Gras auf allem, was oben an der Kante nach oben schaut (Netz der Grasnarbe)
            double gr = smooth(0.55, 0.8, ny) * (1 - smooth(zN, zN + 0.4, d));
            col = mix(col, scale(grass, 0.8 + 0.4 * CNoise.value(q0[0] / 0.4, q0[1] / 0.4, q0[2] / 0.4, seed + 64)), gr);
        } else if (rock == Rock.GRANIT) {
            // Granit: keine Bänder, Flecken in zwei Maßstäben, rötlicher Feldspat und dunkler Glimmer als Korn
            double[] q0 = rot(x, y, z);
            double mo = 0.6 * CNoise.value(q0[0] / 3.0, q0[1] / 3.0, q0[2] / 3.0, seed + 3) + 0.4 * CNoise.value(q0[0] / 0.9, q0[1] / 0.9, q0[2] / 0.9, seed + 4);
            col = mix(gneissDark, gneissLight, smooth(0.2, 0.8, mo));
            double bf = CNoise.band(0.035, footprint), bb = CNoise.band(0.02, footprint);   // Korn: Feldspat 3,5 cm, Biotit 2 cm
            double fel = smooth(0.58, 0.88, CNoise.value(q0[0] / 0.035, q0[1] / 0.035, q0[2] / 0.035, seed + 6)) * feldsparAmount;
            col = mix(col, feldspar, fel * bf + feldsparAmount * 0.25 * (1 - bf));
            double bio = smooth(0.66, 0.92, CNoise.value(q0[0] / 0.02 + 17, q0[1] / 0.02, q0[2] / 0.02, seed + 5));
            col = scale(col, 1 - 0.6 * bio * bb - 0.06 * (1 - bb));
            // Wollsäcke: an den Klüften gerundet und dunkel (Schatten in der Fuge)
            double pill = granitBlock(x, y, z, footprint);
            col = scale(col, 1 - 0.45 * (1 - smooth(0.0, 0.35, pill)) * wall);
        } else {
            // Bänder entlang der Schieferung, verbogen
            double dip = Math.toRadians(foliationDipDeg), dd = Math.toRadians(foliationDipDirDeg);
            double fx = Math.sin(dip) * Math.sin(dd), fy = Math.cos(dip), fz = -Math.sin(dip) * Math.cos(dd);
            double warp = (CNoise.value(x / 40, y / 40, z / 40, seed + 1) - 0.5) * 14 + (CNoise.value(x / 6, y / 6, z / 6, seed + 2) - 0.5) * 1.6 * CNoise.band(6, footprint);
            double f = x * fx + y * fy + z * fz + warp;
            double b = 0.45 * CNoise.value1(f / 33, seed + 3) + 0.35 * CNoise.value1(f / 7, seed + 4)
                    + 0.20 * (CNoise.value1(f / 1.6, seed + 5) - 0.5) * CNoise.band(1.6, footprint) + 0.1;
            b = smooth(0.25, 0.75, b);
            col = mix(gneissDark, gneissLight, b);
            double fel = smooth(0.78, 0.9, CNoise.value1(f / 4.5, seed + 6)) * feldsparAmount;
            col = mix(col, feldspar, fel);
            double[] g0 = rot(x, y, z);
            double grain = (CNoise.value(g0[0] / 0.7, g0[1] / 0.7, g0[2] / 0.7, seed + 7) - 0.5) * 0.18 * CNoise.band(0.7, footprint);
            col = scale(col, 1 + grain);

            // Klüfte: zwei senkrechte Scharen, Fugen dunkel
            double joint = 0;
            double[] jd = {jointADeg, jointBDeg};
            for (int k = 0; k < 2; k++) {
                double a = Math.toRadians(jd[k] + 90);
                double u = (x * Math.sin(a) - z * Math.cos(a)) / jointSpacing + (CNoise.value(x / 25, y / 25, z / 25, seed + 8 + k) - 0.5) * 0.8;
                double toJoint = (0.5 - Math.abs(u - Math.floor(u) - 0.5)) * jointSpacing;
                double open = smooth(0.35, 0.65, CNoise.value(x / 12, y / 30, z / 12, seed + 20 + k));
                joint = Math.max(joint, (1 - smooth(jointWidth * 0.5, jointWidth * 1.5, toJoint)) * open);
            }
            joint *= CNoise.band(jointWidth * 4, footprint) * wall;
            col = scale(col, 1 - 0.55 * joint);
        }

        // Sickerstreifen: senkrecht gestrecktes Rauschen (gewollt), dazu die Nässe der Karte (unter Fällen) und der Regen
        double seep = rock == Rock.LOCKER
                // Lockergestein: Wasser staut auf dem Lehm und tritt an der Grenze aus, Streifen von dort hinab
                ? smooth(0.62, 0.76, CNoise.value(x / 2.2, y / 45, z / 2.2, seed + 57)) * seepAmount * wall * smooth(-0.3, 0.2, lehmTop) * (1 - smooth(1, seepLength, lehmTop))
                : smooth(0.66, 0.78, CNoise.value(x / 3.5, y / 70, z / 3.5, seed + 9)) * seepAmount * wall;
        s.wet = clamp(Math.max(Math.max(wetMap * fallWet, seep), rainWet * s.rock));
        if (rock == Rock.LOCKER) {
            // Trockenrisse im trockenen Lehm: ein Netz dünner Fugen
            double[] qc = rot(x, y, z);
            double cr = (1 - smooth(0.015, 0.045, Math.abs(CNoise.value(qc[0] / 0.45, qc[1] / 0.45, qc[2] / 0.45, seed + 54) - 0.5)))
                    * CNoise.band(0.15, footprint) * crackAmount * lehmShare * (1 - s.wet);
            col = scale(col, 1 - 0.5 * cr);
            // nasser Lehm: dunkler und satter
            col = new double[]{col[0], col[1] * (1 - 0.15 * s.wet * lehmShare), col[2] * (1 - 0.25 * s.wet * lehmShare)};
        }
        // Flechten auf trockenem, steilem Fels; Moos auf nassen Absätzen
        double[] q = rot(x, y, z);
        double li = smooth(0.5, 0.72, CNoise.value(q[0] / 2.2, q[1] / 2.2, q[2] / 2.2, seed + 10)) * lichenAmount * (1 - s.wet) * s.rock;
        col = mix(col, lichen, li * 0.7);
        double mo = clamp((ledge * 0.9 + s.wet * 0.45) * mossAmount * 2 + topMoss * smooth(0.45, 0.85, ny)) * smooth(0.4, 0.62, CNoise.value(q[0] / 3.5, q[1] / 3.5, q[2] / 3.5, seed + 12));
        col = mix(col, moss, mo);
        // Geröll am Fuß: Blöcke als steiles Rauschen im gedrehten Gitter (keine Würfel: die schnitten Dreiecke in den Hang)
        double blk = 0.5 + (smooth(0.3, 0.7, CNoise.value(q[0] / 1.3, q[1] / 1.3, q[2] / 1.3, seed + 13)) - 0.5) * CNoise.band(1.3, footprint);
        col = mix(col, scale(talus, rock == Rock.LOCKER ? 0.92 + 0.16 * blk : 0.75 + 0.5 * blk), tal * (1 - wall));
        // nass: dunkler
        col = scale(col, 1 - 0.45 * s.wet);
        // Schnee auf Absätzen und flachen Stellen
        double flat = Math.max(ledge, 1 - smooth(0.05, 0.15, slope));
        s.snow = clamp(snowAmt * flat * smooth(0.38, 0.55, CNoise.value(x / 5, y / 5, z / 5, seed + 14) + snowAmt * 0.3));
        col = mix(col, snow, s.snow);
        System.arraycopy(col, 0, s.albedo, 0, 3);

        // Normale aus dem Relief (gleiche Feinform wie der Scheitelshader)
        double e = Math.max(0.35, footprint);
        double r0 = shape(x, y, z, footprint);
        double gx = (shape(x + e, y, z, footprint) - r0) / e * s.rock, gy = (shape(x, y + e, z, footprint) - r0) / e * s.rock,
                gz = (shape(x, y, z + e, footprint) - r0) / e * s.rock;
        double gn = gx * nx + gy * ny + gz * nz;
        double mx = nx - (gx - gn * nx), my = ny - (gy - gn * ny), mz = nz - (gz - gn * nz);
        double ml = Math.sqrt(mx * mx + my * my + mz * mz);
        s.normal[0] = mx / ml;
        s.normal[1] = my / ml;
        s.normal[2] = mz / ml;
        return s;
    }

    /** Relief mit der Hohlkehle (Lockergestein): so weit weicht die Wand am Ort entlang der Normale aus (m). */
    public double shape(double x, double y, double z, double footprint) {
        double r = relief(x, y, z, footprint);
        if (rock == Rock.LOCKER) {
            double nd = strataAt(x, z)[3];
            if (nd > 0) r -= nd * notchShape(y);
        }
        return r;
    }

    /** „Cliff · Engine (Phase 5) · MEDIUM · Fels ab 38°, Wand ab 55° · Relief 4 m bis 1500 m · Klüfte 9 m“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Cliff · %s · %s · %s · Fels ab %.0f°, Wand ab %.0f° · Relief %.1f m bis %.0f m · Klüfte %.1f m%s", STAND,
                rock == Rock.GRANIT ? "Granit" : rock == Rock.LOCKER ? "Lockergestein" : "Gneis", quality, rockSlopeDeg, wallSlopeDeg, reliefAmp, quality.reliefRange, jointSpacing,
                rock == Rock.GRANIT ? String.format(Locale.GERMAN, ", Bankung %.1f m", sheetSpacing)
                        : rock == Rock.LOCKER ? String.format(Locale.GERMAN, ", Runsen alle %.1f m, %.1f m tief", gullySpacing, gullyDepth) : "");
    }

    // ------------------------------------------------------------------ Hilfen

    public static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    static double[] mix(double[] a, double[] b, double t) { return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }

    static double[] scale(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
}
