package com.dan.sail;

/**
 * Form eines Segelboots als Dreiecke (seit Timmendorf Phase 7) aus dem Zustand von {@link Sail.Boat}: Rumpf, Deck,
 * Kiel oder Schwert mit Ruder (beim Kat zwei Rümpfe und Trampolin), Mast, Baum, Groß, Fock und gesetzt der Gennaker.
 * Der Baum steht nach Lee, das Tuch hat Bauch nach der Flachheit und Verwindung nach oben; das ganze Boot krängt.
 * Für {@code gl/} und Bilder ohne Grafikkarte.
 * <p>
 * Seit Phase 8:
 * <ul>
 * <li>Krängung mit Vorzeichen ({@link Sail.Boat#rollDeg}) und Baum aus {@link Sail.Boat#boomDeg} (schlägt bei der Halse
 * über); gefierte Schoten lassen das Tuch schlagen ({@link #time}); gekenterte Boote liegen auf der Seite.</li>
 * <li>Die Crew sitzt auf der Luvkante, hängt aus, soweit das Moment es verlangt, oder steht im Trapez (Kat, 420er);
 * Kinder im Optimisten sind kleiner. Gekentert schwimmt sie am Rumpf.</li>
 * <li>Rahsegler (Passat): schwarzer Rumpf mit weißem Band, vier Masten, sechs Rahen je Rahmast, gebrasst nach dem Wind,
 * Klüverbaum mit Vorsegeln, Besan mit Gaffel; festgemacht ({@link Sail.Boat#furled}) liegen die Segel gepackt auf den
 * Rahen.</li>
 * <li>Gaffelschoner: zwei Masten mit Gaffelsegeln und Vorsegeln am Klüverbaum.</li>
 * <li>Kite: Brett, Fahrer in Rücklage und der Schirm an 22 m Leinen im Windfenster, der in Achten fliegt.</li>
 * <li>Farben je Boot ({@link Sail.Boat#hullColor}, {@link Sail.Boat#sailColor}).</li>
 * </ul>
 * Ausgabe je Ecke 9 Werte: Lage x, y, z (Szene), Normale, Farbe (linear). Die Maße folgen der Takelage, die Linien des
 * Rumpfs und der Schnitt der Segel sind gestaltet [G].
 */
public final class BoatShape {
    private float[] buf = new float[9 * 4096];
    private int n;
    // Lage des Boots
    private double cx, cy, cz, sh, ch, sr, cr;
    /** Zeit (s) für schlagendes Tuch und den fliegenden Kite (setzt der Aufrufer, sonst steht alles). */
    public double time;

    public static final float[] HULL = {0.80f, 0.80f, 0.78f}, DECK = {0.55f, 0.55f, 0.53f}, ANTIFOUL = {0.05f, 0.07f, 0.12f},
            SPAR = {0.65f, 0.66f, 0.68f}, CLOTH = {0.85f, 0.85f, 0.80f}, GENNAKER = {0.60f, 0.06f, 0.05f}, CREW = {0.05f, 0.12f, 0.35f},
            SKIN = {0.55f, 0.36f, 0.26f}, LIFEJACKET = {0.75f, 0.30f, 0.02f}, TRAMPOLINE = {0.06f, 0.07f, 0.09f},
            SHIP_BLACK = {0.025f, 0.026f, 0.03f}, SHIP_RED = {0.30f, 0.04f, 0.03f}, SHIP_WHITE = {0.80f, 0.80f, 0.77f},
            TEAK = {0.36f, 0.24f, 0.13f}, WOOD_SPAR = {0.40f, 0.28f, 0.15f}, CANVAS = {0.78f, 0.74f, 0.64f}, ROPE = {0.20f, 0.18f, 0.15f},
            KITE_A = {0.80f, 0.12f, 0.04f}, KITE_B = {0.05f, 0.45f, 0.55f}, BOARD = {0.85f, 0.75f, 0.10f};

    /** Dreiecke des Boots in der Welt bei Wasserhöhe y0; gibt die Werte (9 je Ecke). */
    public float[] build(Sail.Boat b, double y0) {
        n = 0;
        Sail.Rig r = b.rig;
        double hd = Math.toRadians(b.headingDeg);
        sh = Math.sin(hd);
        ch = Math.cos(hd);
        double side = b.windSide();
        // Krängung: + Steuerbord unten (rollDeg); ohne rollDeg (Aufrufer aus Phase 7) der Betrag nach Lee
        double roll = b.rollDeg != 0 || b.heelDeg == 0 ? b.rollDeg : -side * Math.abs(b.heelDeg);
        double heel = r.plan() == Sail.Plan.KITE ? 0 : Math.toRadians(-roll);
        sr = Math.sin(heel);
        cr = Math.cos(heel);
        cx = b.x;
        cy = y0;
        cz = b.z;
        switch (r.plan()) {
            case SQUARE -> squareRigger(b);
            case GAFF -> schooner(b);
            case KITE -> kite(b, side);
            default -> bermuda(b, side);
        }
        return java.util.Arrays.copyOf(buf, n * 9);
    }

    /** Zahl der Ecken des letzten Boots. */
    public int vertices() { return n; }

    // ------------------------------------------------------------------ Jollen, Kats, Kielboote

    private void bermuda(Sail.Boat b, double side) {
        Sail.Rig r = b.rig;
        double L = r.loa(), B = r.beam(), fb = Math.max(0.25, 0.09 * L);
        float[] hullC = b.hullColor != null ? b.hullColor : HULL, sailC = b.sailColor != null ? b.sailColor : CLOTH;
        if (r.kind() == Sail.Kind.CATAMARAN) {
            double hb = Math.max(0.3, B * 0.16);
            hull(L, hb, fb, -B / 2 + hb / 2, hullC, ANTIFOUL, null);
            hull(L, hb, fb, B / 2 - hb / 2, hullC, ANTIFOUL, null);
            box(-L * 0.35, L * 0.25, -B / 2 + hb, B / 2 - hb, fb * 0.95, fb * 0.98, TRAMPOLINE);
            box(L * 0.22, L * 0.26, -B / 2 + hb * 0.5, B / 2 - hb * 0.5, fb * 0.9, fb * 1.02, SPAR);
            box(-L * 0.36, -L * 0.32, -B / 2 + hb * 0.5, B / 2 - hb * 0.5, fb * 0.9, fb * 1.02, SPAR);
        } else {
            hull(L, B, fb, 0, hullC, ANTIFOUL, null);
            fin(r.finDepth(), Math.max(0.2, r.finArea() / r.finDepth() * 0.75), -0.02 * L);
            fin(r.finDepth() * 0.7, 0.18 + 0.03 * L, -0.46 * L);
        }
        // Rigg: Mast bei 40 % von vorn [G], Groß am Baum, Fock vom Vorstag
        double sm = 0.1 * L, mh = r.mastHeight(), boomH = fb + Math.max(0.6, 0.12 * mh);
        double boom = Math.toRadians(Math.max(-88, Math.min(88, b.boomDeg)));
        double lee = Math.signum(b.boomDeg == 0 ? -side : b.boomDeg);
        spar(sm, 0, fb, sm, 0, mh, 0.03 + 0.004 * mh, SPAR);
        double foot = Math.min(L * 0.55, 2 * r.mainArea() / Math.max(1, mh - boomH)), bx = -Math.cos(boom) * foot, bq = Math.sin(boom) * foot;
        spar(sm, 0, boomH, sm + bx, bq, boomH, 0.025 + 0.002 * mh, SPAR);
        double camber = 0.06 + 0.07 * b.flat, flog = flog(b);
        sail(new double[]{sm, 0, boomH}, new double[]{sm, 0, mh * 0.97}, new double[]{sm + bx, bq, boomH}, camber, lee, 10, sailC, flog);
        if (r.jibArea() > 0) {
            double bow = L * 0.48, jf = Math.min(L * 0.5, (bow - sm) * 1.05), jb = boom * 0.85;
            double[] tack = {bow, 0, fb + 0.1}, head = {sm, 0, mh * 0.78}, clew = {bow - Math.cos(jb) * jf, Math.sin(jb) * jf * 0.6, fb + 0.25};
            if (!(b.downwind && r.downwindSail())) sail(tack, head, clew, camber * 1.1, lee, 6, sailC, flog);
        }
        if (b.downwind && r.downwindSail() && !b.capsized) {
            double ga = Math.sqrt(r.downwindArea() * 2), pole = L * 0.5 + 0.12 * L;
            double[] tack = {pole, 0, fb + 0.2}, head = {sm + 0.2, 0, mh * 0.95}, clew = {sm - 0.1 * L, lee * ga * 0.55, fb + 1.2};
            sail(tack, head, clew, 0.22, lee, 14, b.sailColor != null && b.sailColor != sailC ? b.sailColor : GENNAKER, 0);
        }
        crew(b, side, L, B, fb);
    }

    /** Wie stark das Tuch schlägt (0..1): gefierte Schoten, zu hoch am Wind. */
    private static double flog(Sail.Boat b) {
        double luff = b.last[4] > 0 && b.last[4] < 20 ? (20 - b.last[4]) / 12 : 0;
        return Math.max(0, Math.min(1, Math.max(b.ease, luff)));
    }

    /**
     * Die Crew: in Luv auf der Kante, je nach nötigem Moment ausgehängt, im Trapez (Kat, 420er-Vorschoter) waagerecht
     * draußen; Kinder kleiner. Gekentert im Wasser am Rumpf.
     */
    private void crew(Sail.Boat b, double side, double L, double B, double fb) {
        Sail.Rig r = b.rig;
        int crew = Math.min(r.crew(), 6);
        double scale = r == Sail.OPTIMIST || r.crewMass() < 50 ? 0.78 : 1;
        double need = Sail.crewMoment(r, 0) > 0 ? Math.max(0, Math.min(1, b.last[2] / Sail.crewMoment(r, 0))) : 0;
        boolean dinghy = r.kind() == Sail.Kind.DINGHY || r.kind() == Sail.Kind.CATAMARAN;
        for (int k = 0; k < crew; k++) {
            double s = -L * 0.15 - k * 0.55 * scale;
            if (b.capsized) {
                // im Wasser an der Unterseite des liegenden Boots (am Schwert)
                double q = Math.signum(b.rollDeg) * (B / 2 + 0.4);
                box(s - 0.2, s + 0.2, q - 0.2, q + 0.2, -0.5, 0.25, LIFEJACKET);
                continue;
            }
            double edge = side * (r.kind() == Sail.Kind.CATAMARAN ? B / 2 : B / 2 * 0.92);
            boolean trapeze = (r.kind() == Sail.Kind.CATAMARAN || (r == Sail.DINGHY_420 && k == 1)) && need > 0.55;
            double angle;
            if (trapeze) angle = 88;
            else if (dinghy || r.kind() == Sail.Kind.KEELBOAT) angle = 8 + 72 * Math.min(1, need * 1.2);
            else angle = 5;
            if (r.kind() == Sail.Kind.KEELBOAT) angle = Math.min(angle, 35);
            double len = (trapeze ? 1.65 : 0.9) * scale;
            double baseQ = trapeze ? edge : edge - side * 0.15 * scale, baseY = fb + (trapeze ? 0.35 : 0.05);
            body(s, baseQ, baseY, side, angle, len, 0.38 * scale, 0.26 * scale, k == 0 && r.kind() == Sail.Kind.DINGHY ? LIFEJACKET : CREW);
            // Kopf
            double a = Math.toRadians(angle), hq = baseQ + side * Math.sin(a) * (len + 0.12 * scale), hy = baseY + Math.cos(a) * (len + 0.12 * scale);
            box(s - 0.11 * scale, s + 0.11 * scale, hq - 0.11 * scale, hq + 0.11 * scale, hy - 0.11 * scale, hy + 0.11 * scale, SKIN);
            if (trapeze) spar(s, hq - side * 0.3, hy, 0.1 * L, 0, r.mastHeight() * 0.72, 0.006, ROPE);
        }
    }

    /** Ein Körper als schräger Quader: Fuß bei (s, q, y), nach außen (side) um angle vom Lot geneigt. */
    private void body(double s, double q, double y, double side, double angleDeg, double len, double w, double t, float[] col) {
        double a = Math.toRadians(angleDeg);
        double dq = side * Math.sin(a), dy = Math.cos(a);
        // quer zur Längsachse des Körpers in der Ebene (q, y)
        double nq = -dy * side, ny = Math.abs(dq);
        double[] p0 = {s, q, y}, p1 = {s, q + dq * len, y + dy * len};
        double[][] v = new double[8][];
        int i = 0;
        for (double[] p : new double[][]{p0, p1})
            for (double[] o : new double[][]{{-w / 2, -t / 2}, {w / 2, -t / 2}, {w / 2, t / 2}, {-w / 2, t / 2}})
                v[i++] = new double[]{p[0] + o[0], p[1] + nq * o[1], p[2] + ny * o[1]};
        int[][] f = {{0, 1, 2, 3}, {4, 7, 6, 5}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}};
        for (int[] qd : f) quad(v[qd[0]], v[qd[1]], v[qd[2]], v[qd[3]], col);
    }

    // ------------------------------------------------------------------ Rahsegler

    /** Viermastbark: Rumpf, Masten, Rahen, Rahsegel (gesetzt oder gepackt), Vorsegel, Besan mit Gaffel. */
    private void squareRigger(Sail.Boat b) {
        Sail.Rig r = b.rig;
        double L = r.loa(), B = r.beam(), fb = Math.min(0.09 * L, 2 + 0.02 * L);
        hull(L, B, fb, 0, SHIP_BLACK, SHIP_RED, SHIP_WHITE);
        // Aufbauten: Back, Mittschiffshaus, Poop [G]
        box(L * 0.36, L * 0.47, -B * 0.42, B * 0.42, fb, fb + 2.2, SHIP_WHITE);
        box(-L * 0.06, L * 0.06, -B * 0.32, B * 0.32, fb, fb + 2.4, SHIP_WHITE);
        box(-L * 0.47, -L * 0.33, -B * 0.42, B * 0.42, fb, fb + 2.4, TEAK);
        double mh = r.mastHeight(), bow = L * 0.5;
        // Klüverbaum
        spar(bow - 1, 0, fb + 1.5, bow + 0.13 * L, 0, fb + 5.5, 0.35, SPAR);
        double[] masts = {0.30 * L, 0.08 * L, -0.14 * L, -0.33 * L};
        double brace = Math.toRadians(b.furled ? 0 : Math.max(-60, Math.min(60, b.boomDeg * 0.7)));
        double lee = Math.signum(b.boomDeg == 0 ? -b.windSide() : b.boomDeg);
        double[] fr = {0.30, 0.43, 0.55, 0.67, 0.77, 0.86}, wid = {1.0, 0.92, 0.84, 0.70, 0.62, 0.50};
        for (int m = 0; m < 4; m++) {
            double s = masts[m], h = m == 3 ? mh * 0.82 : mh;
            spar(s, 0, fb - 2, s, 0, h, m == 3 ? 0.35 : 0.45, SPAR);
            if (m == 3) {
                // Besan: Gaffelsegel nach achtern
                double boomH = fb + 4, gaffH = h * 0.72;
                double[] tack = {s, 0, boomH}, throat = {s, 0, gaffH}, peak = {s - 0.16 * L, 0, gaffH + 5}, clew = {s - 0.17 * L, 0, boomH};
                spar(s, 0, boomH, clew[0], clew[1], clew[2], 0.2, WOOD_SPAR);
                spar(s, 0, gaffH, peak[0], peak[1], peak[2], 0.18, WOOD_SPAR);
                if (b.furled) spar(s, 0, boomH + 0.4, clew[0] + 1, 0, boomH + 0.4, 0.45, CANVAS);
                else gaffSail(tack, throat, peak, clew, 0.08, lee, CANVAS, flog(b));
                continue;
            }
            double base = fb + 3;
            double[][] yard = new double[6][];
            for (int k = 0; k < 6; k++) {
                double y = fb + fr[k] * (h - fb), half = 0.12 * L * wid[k];
                double dq = Math.cos(brace) * half, ds = Math.sin(brace) * half;
                yard[k] = new double[]{s, y, ds, dq};
                spar(s + ds, -dq, y, s - ds, dq, y, 0.18 + 0.06 * (5 - k) / 5.0, WOOD_SPAR);
                if (b.furled) spar(s + ds * 0.95, -dq * 0.95, y + 0.35, s - ds * 0.95, dq * 0.95, y + 0.35, 0.32, CANVAS);
            }
            if (!b.furled) {
                for (int k = 0; k < 6; k++) {
                    double[] top = yard[k];
                    double botY = k == 0 ? base : yard[k - 1][1] + 0.6, botS = k == 0 ? 0.85 : 1.0;
                    double[] bot = k == 0 ? new double[]{s, botY, top[2] * botS, top[3] * botS} : new double[]{s, botY, yard[k - 1][2], yard[k - 1][3]};
                    double[] a = {top[0] + top[2], -top[3], top[1]}, c = {top[0] - top[2], top[3], top[1]};
                    double[] d = {bot[0] + bot[2], -bot[3], bot[1]}, e = {bot[0] - bot[2], bot[3], bot[1]};
                    squareSail(a, c, e, d, 0.10, lee, CANVAS, flog(b));
                }
            }
            // Wanten als dünne Leinen zum Deck [G]
            for (int sgn = -1; sgn <= 1; sgn += 2) spar(s - 1.5, sgn * B * 0.48, fb + 0.5, s, 0, h * 0.62, 0.05, ROPE);
        }
        // Vorsegel vom Klüverbaum zum Fockmast
        double fm = masts[0];
        for (int k = 0; k < 3; k++) {
            double tipS = bow + 0.13 * L * (0.45 + 0.27 * k), tipY = fb + 2 + 3.5 * (0.45 + 0.27 * k);
            double headY = fb + (0.55 + 0.1 * k) * (mh - fb);
            spar(tipS, 0, tipY, fm, 0, headY, 0.05, ROPE);
            if (!b.furled) {
                double[] tack = {tipS, 0, tipY}, head = {fm + 1, 0, headY}, clew = {(tipS + fm) / 2 - 4, lee * 4, fb + 4 + 5 * k};
                sail(tack, head, clew, 0.08, lee, 6, CANVAS, flog(b));
            }
        }
    }

    // ------------------------------------------------------------------ Gaffelschoner

    private void schooner(Sail.Boat b) {
        Sail.Rig r = b.rig;
        double L = r.loa(), B = r.beam(), fb = Math.min(0.09 * L, 1.2 + 0.03 * L);
        float[] hullC = b.hullColor != null ? b.hullColor : new float[]{0.07f, 0.16f, 0.12f};
        hull(L, B, fb, 0, hullC, SHIP_RED, SHIP_WHITE);
        box(-L * 0.2, L * 0.05, -B * 0.25, B * 0.25, fb, fb + 1.2, TEAK);
        double mh = r.mastHeight(), bow = L * 0.5;
        spar(bow - 0.5, 0, fb + 0.6, bow + 0.22 * L, 0, fb + 1.6, 0.18, WOOD_SPAR);
        double boom = Math.toRadians(Math.max(-80, Math.min(80, b.boomDeg)));
        double lee = Math.signum(b.boomDeg == 0 ? -b.windSide() : b.boomDeg);
        double[] masts = {0.18 * L, -0.08 * L};
        double[] heights = {mh * 0.9, mh};
        for (int m = 0; m < 2; m++) {
            double s = masts[m], h = heights[m];
            spar(s, 0, fb - 1, s, 0, h, 0.22, WOOD_SPAR);
            double boomH = fb + 1.2, foot = m == 1 ? 0.42 * L : 0.24 * L, gaffH = h * 0.66;
            double[] tack = {s, 0, boomH}, throat = {s, 0, gaffH};
            double[] clew = {s - Math.cos(boom) * foot, Math.sin(boom) * foot, boomH + 0.5};
            double[] peak = {s - Math.cos(boom * 0.85) * foot * 0.8, Math.sin(boom * 0.85) * foot * 0.8, gaffH + 0.35 * (h - gaffH)};
            spar(tack[0], tack[1], tack[2], clew[0], clew[1], clew[2], 0.12, WOOD_SPAR);
            spar(throat[0], throat[1], throat[2], peak[0], peak[1], peak[2], 0.1, WOOD_SPAR);
            if (b.furled) spar(s, 0, boomH + 0.3, clew[0], clew[1], boomH + 0.3, 0.3, CANVAS);
            else gaffSail(tack, throat, peak, clew, 0.09, lee, CANVAS, flog(b));
        }
        for (int k = 0; k < 2 && !b.furled; k++) {
            double tipS = bow + 0.22 * L * (0.5 + 0.5 * k);
            double[] tack = {tipS, 0, fb + 1.2}, head = {masts[0], 0, heights[0] * (0.75 + 0.1 * k)}, clew = {masts[0] + 0.12 * L * (1 + k * 0.3), lee * 2.5, fb + 2 + k};
            sail(tack, head, clew, 0.08, lee, 6, CANVAS, flog(b));
        }
        crewOnDeck(r.crew(), L, B, fb);
    }

    private void crewOnDeck(int crew, double L, double B, double fb) {
        java.util.Random rnd = new java.util.Random(7);
        for (int k = 0; k < Math.min(crew, 10); k++) {
            double s = (rnd.nextDouble() - 0.5) * L * 0.7, q = (rnd.nextDouble() - 0.5) * B * 0.6;
            box(s - 0.2, s + 0.2, q - 0.15, q + 0.15, fb, fb + 1.7, k % 3 == 0 ? LIFEJACKET : CREW);
        }
    }

    // ------------------------------------------------------------------ Kite

    /**
     * Kite: Brett auf dem Wasser, Fahrer in Rücklage zum Schirm, Schirm an 22 m Leinen (mastHeight) im Windfenster:
     * gegenüber dem scheinbaren Wind, nach vorn an den Rand des Fensters gezogen, in Achten fliegend [G].
     */
    private void kite(Sail.Boat b, double side) {
        Sail.Rig r = b.rig;
        double L = r.loa(), B = r.beam();
        box(-L / 2, L / 2, -B / 2, B / 2, -0.03, 0.03, b.hullColor != null ? b.hullColor : BOARD);
        double beta = b.last[4] > 0 ? b.last[4] : 90;
        // Richtung, aus der der scheinbare Wind kommt, im Körper (0 = vorn, + Steuerbord): side · β
        double from = Math.toRadians(side * beta);
        double power = b.capsized ? 0 : 1 - b.ease;
        double fig = Math.sin(time * 0.9 + b.x * 0.01) * power, fig2 = Math.sin(time * 1.8 + b.x * 0.01) * power;
        double az = from + Math.PI - side * Math.toRadians(55 + 12 * fig), el = Math.toRadians(32 + 10 * fig2 + 20 * (1 - power));
        double line = r.mastHeight();
        double ks = Math.cos(az) * Math.cos(el) * line, kq = Math.sin(az) * Math.cos(el) * line, ky = 1.0 + Math.sin(el) * line;
        // Fahrer: Füße auf dem Brett, Körper zum Schirm geneigt
        double lean = Math.toDegrees(Math.atan2(Math.hypot(ks, kq), ky)) * 0.55;
        double dirQ = Math.signum(kq == 0 ? 1 : kq);
        body(0, 0, 0.05, dirQ, Math.min(45, lean), 1.6, 0.36, 0.26, CREW);
        double a = Math.toRadians(Math.min(45, lean)), hq = dirQ * Math.sin(a) * 1.0, hy = 0.05 + Math.cos(a) * 1.0;
        // Schirm: Bogen aus 12 Zellen quer zur Leine
        double[] kc = {ks, kq, ky};
        double[] dl = BoatShape.norm(kc);
        double[] up = {0, 0, 1};
        double[] span = BoatShape.norm(BoatShape.cross(dl, up));
        if (Double.isNaN(span[0]) || Math.abs(dl[2]) > 0.98) span = new double[]{0, 1, 0};
        double[] chord = BoatShape.norm(BoatShape.cross(span, dl));
        float[] ca = b.sailColor != null ? b.sailColor : KITE_A;
        int cells = 12;
        double halfSpan = 4.6, radius = 4.2, c0 = 2.6;
        double[][] lead = new double[cells + 1][], trail = new double[cells + 1][];
        for (int i = 0; i <= cells; i++) {
            double u = (i / (double) cells - 0.5) * 2;             // −1 … 1
            double ang = u * (halfSpan / radius);
            double sp = Math.sin(ang) * radius, back = (1 - Math.cos(ang)) * radius;   // Spitzen biegen zum Fahrer
            double c = c0 * (1 - 0.45 * u * u);
            double[] p = {kc[0] + span[0] * sp - dl[0] * back, kc[1] + span[1] * sp - dl[1] * back, kc[2] + span[2] * sp - dl[2] * back};
            lead[i] = new double[]{p[0] + chord[0] * c * 0.4, p[1] + chord[1] * c * 0.4, p[2] + chord[2] * c * 0.4};
            trail[i] = new double[]{p[0] - chord[0] * c * 0.6, p[1] - chord[1] * c * 0.6, p[2] - chord[2] * c * 0.6};
        }
        for (int i = 0; i < cells; i++) quad(lead[i], lead[i + 1], trail[i + 1], trail[i], i % 4 == 1 ? KITE_B : ca);
        // Leinen von den Händen zu den Spitzen
        spar(0.1, hq, hy, lead[0][0], lead[0][1], lead[0][2], 0.015, ROPE);
        spar(0.1, hq, hy, lead[cells][0], lead[cells][1], lead[cells][2], 0.015, ROPE);
    }

    // ------------------------------------------------------------------ Bausteine (Körper: s vorn, q Steuerbord, y oben)

    /** Rumpf: Spantengitter, Farbe über Wasser, unter Wasser und (falls gesetzt) ein Band unter dem Deck. */
    private void hull(double L, double B, double fb, double q0, float[] top, float[] bottom, float[] band) {
        int ns = 16, nq = 8;
        double draft = Math.max(0.12, 0.05 * L);
        double[][][] p = new double[ns + 1][nq + 1][];
        for (int i = 0; i <= ns; i++) {
            double t = i / (double) ns, s = -L / 2 + t * L;
            double w = B / 2 * Math.pow(Math.max(0, Math.sin(Math.PI * Math.min(1, 0.15 + 0.85 * t))), 0.6);
            w *= t > 0.85 ? Math.max(0.05, (1 - t) / 0.15) : 1;
            if (t < 0.05) w *= 0.92;
            double d = draft * Math.sin(Math.PI * Math.min(1, 0.2 + 0.8 * t));
            for (int j = 0; j <= nq; j++) {
                double a = Math.PI * j / nq;      // 0 Backbord oben, π Steuerbord oben
                double q = -Math.cos(a) * w, y = Math.cos(a) * Math.cos(a) * fb - Math.sin(a) * d;
                p[i][j] = new double[]{s, q0 + q, y};
            }
        }
        for (int i = 0; i < ns; i++)
            for (int j = 0; j < nq; j++) {
                double[] a = p[i][j], b = p[i + 1][j], c = p[i + 1][j + 1], d = p[i][j + 1];
                double ym = (a[2] + b[2] + c[2] + d[2]) / 4;
                float[] col = ym < -0.03 ? bottom : band != null && ym > 0.55 * fb ? band : top;
                quad(a, b, c, d, col);
            }
        // Deck
        for (int i = 0; i < ns; i++) quad(p[i][nq], p[i + 1][nq], p[i + 1][0], p[i][0], band != null ? TEAK : DECK);
    }

    private void fin(double depth, double chord, double s0) {
        double t = Math.max(0.02, chord * 0.1);
        double[] a = {s0 + chord / 2, -t, 0}, b = {s0 - chord / 2, -t, 0}, c = {s0 - chord * 0.35, -t * 0.5, -depth}, d = {s0 + chord * 0.25, -t * 0.5, -depth};
        double[] a2 = {a[0], t, 0}, b2 = {b[0], t, 0}, c2 = {c[0], t * 0.5, -depth}, d2 = {d[0], t * 0.5, -depth};
        quad(a, b, c, d, ANTIFOUL);
        quad(b2, a2, d2, c2, ANTIFOUL);
    }

    private void spar(double s0, double q0, double y0, double s1, double q1, double y1, double r, float[] col) {
        int seg = r < 0.03 ? 3 : 6;
        double dx = s1 - s0, dq = q1 - q0, dy = y1 - y0, l = Math.sqrt(dx * dx + dq * dq + dy * dy);
        if (l < 1e-9) return;
        double[] u = Math.abs(dy) > 0.9 * l ? new double[]{1, 0, 0} : new double[]{0, 0, 1};
        double[] ax = {dx / l, dq / l, dy / l}, v1 = norm(cross(ax, u)), v2 = cross(ax, v1);
        for (int k = 0; k < seg; k++) {
            double a0 = 2 * Math.PI * k / seg, a1 = 2 * Math.PI * (k + 1) / seg;
            double[] o0 = {r * (Math.cos(a0) * v1[0] + Math.sin(a0) * v2[0]), r * (Math.cos(a0) * v1[1] + Math.sin(a0) * v2[1]), r * (Math.cos(a0) * v1[2] + Math.sin(a0) * v2[2])};
            double[] o1 = {r * (Math.cos(a1) * v1[0] + Math.sin(a1) * v2[0]), r * (Math.cos(a1) * v1[1] + Math.sin(a1) * v2[1]), r * (Math.cos(a1) * v1[2] + Math.sin(a1) * v2[2])};
            quad(new double[]{s0 + o0[0], q0 + o0[1], y0 + o0[2]}, new double[]{s1 + o0[0], q1 + o0[1], y1 + o0[2]},
                    new double[]{s1 + o1[0], q1 + o1[1], y1 + o1[2]}, new double[]{s0 + o1[0], q0 + o1[1], y0 + o1[2]}, col);
        }
    }

    /**
     * Ein Segel als Fläche: Vorliek von tack nach head, Unterliek nach clew; Bauch nach Lee (lee = ±1 in q), Verwindung
     * oben. Schlagendes Tuch ({@code flog} 0..1): Wellen laufen zum Achterliek.
     */
    private void sail(double[] tack, double[] head, double[] clew, double camber, double lee, int rows, float[] col, double flog) {
        int cols = 6;
        double[][][] p = new double[rows + 1][cols + 1][];
        for (int i = 0; i <= rows; i++) {
            double t = i / (double) rows;
            double[] luff = lerp(tack, head, t), leech = lerp(clew, head, t);
            surfaceRow(p[i], luff, leech, camber * (1 - 0.6 * t * t), lee, cols, flog, t);
        }
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) quad(p[i][j], p[i][j + 1], p[i + 1][j + 1], p[i + 1][j], col);
    }

    /** Ein Rahsegel: oben von a nach c (Rah), unten von d nach e; Bauch nach Lee. */
    private void squareSail(double[] a, double[] c, double[] e, double[] d, double camber, double lee, float[] col, double flog) {
        int rows = 4, cols = 8;
        double[][][] p = new double[rows + 1][cols + 1][];
        for (int i = 0; i <= rows; i++) {
            double t = i / (double) rows;
            double[] l = lerp(d, a, t), r = lerp(e, c, t);
            surfaceRow(p[i], l, r, camber * (0.6 + 0.4 * Math.sin(Math.PI * Math.min(1, t * 1.2))), lee, cols, flog, t);
        }
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) quad(p[i][j], p[i][j + 1], p[i + 1][j + 1], p[i + 1][j], col);
    }

    /** Ein Gaffelsegel: Vorliek tack–throat am Mast, Gaffel throat–peak, Achterliek peak–clew, Unterliek clew–tack. */
    private void gaffSail(double[] tack, double[] throat, double[] peak, double[] clew, double camber, double lee, float[] col, double flog) {
        int rows = 8, cols = 6;
        double[][][] p = new double[rows + 1][cols + 1][];
        for (int i = 0; i <= rows; i++) {
            double t = i / (double) rows;
            double[] luff = lerp(tack, throat, t), leech = lerp(clew, peak, t);
            surfaceRow(p[i], luff, leech, camber * (1 - 0.3 * t), lee, cols, flog, t);
        }
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) quad(p[i][j], p[i][j + 1], p[i + 1][j + 1], p[i + 1][j], col);
    }

    /** Eine Reihe des Tuchs zwischen zwei Punkten: Parabel mit größter Tiefe bei 40 % der Sehne, quer zur Sehne nach Lee. */
    private void surfaceRow(double[][] row, double[] luff, double[] leech, double camber, double lee, int cols, double flog, double t) {
        double chordX = leech[0] - luff[0], chordQ = leech[1] - luff[1], chord = Math.hypot(chordX, chordQ);
        double nx = chord < 1e-6 ? 0 : -chordQ / chord, nq = chord < 1e-6 ? 0 : chordX / chord;
        double sgn = Math.signum(nq * lee) >= 0 ? 1 : -1;
        for (int j = 0; j <= cols; j++) {
            double u = j / (double) cols;
            double[] pt = lerp(luff, leech, u);
            double depth = camber * chord * 4.2 * u * (1 - u) * (1 - 0.3 * u) * (1 - flog * 0.7);
            if (flog > 0) depth += flog * chord * 0.06 * u * Math.sin(u * 9 - time * 14 + t * 3);
            pt[0] += sgn * depth * nx;
            pt[1] += sgn * depth * nq;
            row[j] = pt;
        }
    }

    private void box(double s0, double s1, double q0, double q1, double y0, double y1, float[] col) {
        double[][] v = {{s0, q0, y0}, {s1, q0, y0}, {s1, q1, y0}, {s0, q1, y0}, {s0, q0, y1}, {s1, q0, y1}, {s1, q1, y1}, {s0, q1, y1}};
        int[][] f = {{0, 1, 2, 3}, {4, 7, 6, 5}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}};
        for (int[] q : f) quad(v[q[0]], v[q[1]], v[q[2]], v[q[3]], col);
    }

    private void quad(double[] a, double[] b, double[] c, double[] d, float[] col) {
        tri(a, b, c, col);
        tri(a, c, d, col);
    }

    private void tri(double[] a, double[] b, double[] c, float[] col) {
        double[] wa = world(a), wb = world(b), wc = world(c);
        double[] nn = norm(cross(new double[]{wb[0] - wa[0], wb[1] - wa[1], wb[2] - wa[2]}, new double[]{wc[0] - wa[0], wc[1] - wa[1], wc[2] - wa[2]}));
        if (buf.length < (n + 3) * 9) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
        for (double[] w : new double[][]{wa, wb, wc}) {
            int o = n * 9;
            buf[o] = (float) w[0];
            buf[o + 1] = (float) w[1];
            buf[o + 2] = (float) w[2];
            buf[o + 3] = (float) nn[0];
            buf[o + 4] = (float) nn[1];
            buf[o + 5] = (float) nn[2];
            buf[o + 6] = col[0];
            buf[o + 7] = col[1];
            buf[o + 8] = col[2];
            n++;
        }
    }

    /** Körper (s, q, y) → Welt: Krängung um die Längsachse, Kurs, Lage. */
    private double[] world(double[] p) {
        double s = p[0], q = p[1] * cr - p[2] * sr, y = p[1] * sr + p[2] * cr;
        // vorn = (sin h, −cos h), Steuerbord = (cos h, sin h) in (x, z)
        return new double[]{cx + s * sh + q * ch, cy + y, cz - s * ch + q * sh};
    }

    static double[] lerp(double[] a, double[] b, double t) { return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    static double[] norm(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-12 ? new double[]{0, 1, 0} : new double[]{a[0] / l, a[1] / l, a[2] / l};
    }
}
