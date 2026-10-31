package com.dan.longship;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Ein Schiff in Fahrt: Ort, Kurs, Geschwindigkeit, Segel, Riemen, Befehle. Unter Segel hängt die Fahrt vom Wind ab
 * (Rahsegel: am besten raumschots, hart am Wind geht es nicht; näher als 70° an den Wind zieht das Segel nicht und das
 * Schiff rudert, wenn es Riemen hat). Unter Riemen fährt es mit der Fahrt seines Typs, die Riemen schlagen im Takt.
 * Befehle laufen der Reihe nach ab: Segel setzen und bergen, Kurs auf einen Punkt unter Segel oder Riemen, Anlegen an
 * einer Brücke, Warten. Tauchen, Stampfen und Rollen kommen von außen (etwa {@code buoyancy.Floater}) oder, ohne das,
 * aus einer einfachen Abfrage des Wassers ({@link Water}).
 * <p>
 * Koordinaten wie {@code buoyancy}: Kurs in Grad von Nord im Uhrzeigersinn, vorn = (sin h, −cos h) in (x, z); die
 * Modellmatrix setzt Netze in Schiffskoordinaten (x Steuerbord, y oben, z achteraus) in die Welt.
 */
public final class Longship {
    /** Was das Schiff gerade tut. */
    public enum Mode { TREIBEN, SEGELN, RUDERN, ANLEGEN, FEST }

    /** Ein Befehl. */
    public record Command(Kind kind, double x, double z, double value) {
        public enum Kind { SEGEL, KURS_SEGEL, KURS_RIEMEN, ANLEGEN, WARTEN, DURCH }
        /** Segel auf den Anteil 0 (geborgen) … 1 (voll gesetzt) bringen. */
        public static Command sail(double set) { return new Command(Kind.SEGEL, 0, 0, set); }
        /** Unter Segel auf (x, z), angekommen im Umkreis radius. */
        public static Command sailTo(double x, double z, double radius) { return new Command(Kind.KURS_SEGEL, x, z, radius); }
        /** Unter Riemen auf (x, z). */
        public static Command rowTo(double x, double z, double radius) { return new Command(Kind.KURS_RIEMEN, x, z, radius); }
        /** Anlegen: Liegeplatz (x, z), Kurs dort (Grad). */
        public static Command moor(double x, double z, double headingDeg) { return new Command(Kind.ANLEGEN, x, z, headingDeg); }
        public static Command waitFor(double seconds) { return new Command(Kind.WARTEN, 0, 0, seconds); }
        /**
         * Unter Riemen genau durch eine Engstelle (Lücke in einer Pfahlreihe) bei (x, z) mit Kurs headingDeg: erst zu
         * einem Punkt {@link #passApproach} davor, dann auf der Linie hindurch; fertig eine Schiffslänge dahinter.
         */
        public static Command passThrough(double x, double z, double headingDeg) { return new Command(Kind.DURCH, x, z, headingDeg); }
    }

    /** Stoß ins Wasser (Kielwasser, Riemenschlag): Ort und Stärke. */
    @FunctionalInterface public interface Wake { void at(double x, double z, double strength); }

    /** Ein Riemenschlag (für Klang): Ort, Lautstärke 0..1. */
    @FunctionalInterface public interface Stroke { void at(double x, double y, double z, double loudness); }

    /**
     * Seit Timmendorf Phase 18: ein Segelmodell von außen, etwa das Rahsegel aus {@code com.dan.sail} (das Paket kennt es
     * nicht; die App verbindet beide). Ohne Modell gilt die Tabelle {@link #sailEfficiency} wie bisher.
     */
    public interface Rig {
        /**
         * Mit vollem Segel auf dem Kurs courseDeg beim jetzigen Wind des Schiffs: {Fahrt (m/s), Krängung (Grad, Betrag,
         * nach Lee), Abdrift (Grad, Betrag, nach Lee)}; Fahrt 0, wo das Segel nicht zieht.
         */
        double[] sail(Longship s, double courseDeg);

        /** Größter Winkel zum Wind (wie {@link #windAngle}: 0 vor dem Wind, 180 dagegen), unter dem das Segel noch zieht. */
        double limit(Longship s);
    }

    public final ShipType type;
    public final Hull hull;
    public String name;
    /** Ort (Mitte der Wasserlinie), Kurs (Grad), Fahrt voraus (m/s), Drehrate (Grad/s). */
    public double x, z, heading, speed, yawRate;
    /** Lage im Wasser: Höhe, Stampfen und Rollen (rad); von außen gesetzt, wenn {@link #externalPose}. */
    public double y, pitch, roll;
    public boolean externalPose;
    /** Wind: woher (Grad) und wie stark (m/s). */
    public double windFromDeg = 255, windSpeed = 5;
    /** Segel: gesetzt 0..1, Ziel, Brassung der Rah gegen die Mittschiffslinie (Grad). */
    public double sailSet, sailTarget, yardAngle;
    /** Takt der Riemen (Schläge je Sekunde) und Phase 0..1. */
    public double strokeRate = 0.42, strokePhase;
    public Mode mode = Mode.TREIBEN;
    /** Segel zieht gerade nicht (zu hoch am Wind) und das Schiff rudert statt zu segeln. */
    public boolean sailStalled;
    /** Seit Timmendorf Phase 18: Segelmodell von außen (null = Tabelle) und was es gibt: Krängung (Grad, + Steuerbord unten), Abdrift (Grad, + nach Steuerbord). */
    public volatile Rig rig;
    public double heelDeg, leewayDeg;
    public final Deque<Command> commands = new ArrayDeque<>();
    public Water water;
    public Wake onWake;
    public Stroke onStroke;
    public double time;
    private double wakeDist, waitLeft = -1;
    private int moorPhase;
    private double[] berth;

    public Longship(ShipType type, String name) {
        this.type = type;
        this.hull = new Hull(type);
        this.name = name;
    }

    // ------------------------------------------------------------------ Lage und Richtungen

    public double[] forward() { double h = Math.toRadians(heading); return new double[]{Math.sin(h), -Math.cos(h)}; }
    public double[] right() { double h = Math.toRadians(heading); return new double[]{Math.cos(h), Math.sin(h)}; }

    /** Modellmatrix (Spalten zuerst): Verschiebung · Kurs · Stampfen · Rollen, wie {@code buoyancy.Floater#model}. */
    public double[] model() {
        double h = Math.toRadians(-heading), p = pitch, r = -roll;
        double ch = Math.cos(h), sh = Math.sin(h), cp = Math.cos(p), sp = Math.sin(p), cr = Math.cos(r), sr = Math.sin(r);
        double[] ry = {ch, 0, -sh, 0, 0, 1, 0, 0, sh, 0, ch, 0, 0, 0, 0, 1};
        double[] rx = {1, 0, 0, 0, 0, cp, sp, 0, 0, -sp, cp, 0, 0, 0, 0, 1};
        double[] rz = {cr, sr, 0, 0, -sr, cr, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        double[] mm = mul(ry, mul(rx, rz));
        mm[12] = x; mm[13] = y; mm[14] = z;
        return mm;
    }

    /** Punkt in Schiffskoordinaten (s vorn, q Steuerbord, y oben) in der Welt. */
    public double[] toWorld(double s, double q, double yy) {
        double[] mm = model();
        double lx = q, ly = yy, lz = -s;
        return new double[]{mm[0] * lx + mm[4] * ly + mm[8] * lz + mm[12], mm[1] * lx + mm[5] * ly + mm[9] * lz + mm[13], mm[2] * lx + mm[6] * ly + mm[10] * lz + mm[14]};
    }

    static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int c = 0; c < 4; c++) for (int rr = 0; rr < 4; rr++) { double s = 0; for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k]; r[c * 4 + rr] = s; }
        return r;
    }

    // ------------------------------------------------------------------ Segel

    /** Winkel zwischen Kurs und der Richtung, in die der Wind weht (0 = vor dem Wind, 180 = genau dagegen). */
    public double windAngle(double courseDeg) {
        double to = windFromDeg + 180;
        double d = Math.abs(((courseDeg - to) % 360 + 540) % 360 - 180);
        return d;
    }

    /** Ob ein Rahsegel auf diesem Kurs zieht (nicht näher als 70° an den Wind). */
    public boolean canSail(double courseDeg) {
        Rig r = rig;
        return type.hasSail() && windAngle(courseDeg) <= (r != null ? r.limit(this) : 110);
    }

    /** Wirkungsgrad des Rahsegels über dem Winkel zum Wind [G]: vor dem Wind 0,45, raumschots 0,55, halber Wind 0,5, 110° 0,22. */
    public static double sailEfficiency(double windAngleDeg) {
        double a = windAngleDeg;
        if (a > 110) return 0;
        double[][] tab = {{0, 0.45}, {45, 0.55}, {90, 0.5}, {110, 0.22}};
        for (int i = 0; i + 1 < tab.length; i++)
            if (a <= tab[i + 1][0]) { double f = (a - tab[i][0]) / (tab[i + 1][0] - tab[i][0]); return tab[i][1] + (tab[i + 1][1] - tab[i][1]) * f; }
        return 0.22;
    }

    /** Fahrt unter Segel auf diesem Kurs (m/s), begrenzt durch die Rumpfgeschwindigkeit. */
    public double sailSpeed(double courseDeg) {
        if (!type.hasSail()) return 0;
        Rig r = rig;
        if (r != null) return r.sail(this, courseDeg)[0] * sailSet;
        double v = sailEfficiency(windAngle(courseDeg)) * windSpeed * Math.sqrt(type.sailArea() / 100) * sailSet;
        return Math.min(v, 1.0 * Math.sqrt(9.81 * type.length) * 0.42);
    }

    // ------------------------------------------------------------------ Schritt

    /** Läuft um dt Sekunden weiter: Befehle, Fahrt, Kurs, Segel, Riemen, Kielwasser, Lage. */
    public void step(double dt) {
        if (dt <= 0) return;
        time += dt;
        double targetSpeed = 0, targetHeading = heading;
        boolean rowing = false, braking = false;
        Command c = commands.peek();
        sailStalled = false;
        if (mode == Mode.FEST) {
            // festgemacht: sacht in den Liegeplatz ziehen
            if (berth != null) {
                double k = 1 - Math.exp(-dt * 0.8);
                x += (berth[0] - x) * k;
                z += (berth[1] - z) * k;
                heading += wrap(berth[2] - heading) * k;
            }
            speed = 0;
            yawRate = 0;
            if (c != null && c.kind() != Command.Kind.ANLEGEN) { mode = Mode.TREIBEN; berth = null; }
        }
        if (c != null && mode != Mode.FEST) {
            switch (c.kind()) {
                case SEGEL -> {
                    sailTarget = c.value();
                    if (Math.abs(sailSet - sailTarget) < 0.02) { sailSet = sailTarget; commands.poll(); }
                    targetSpeed = speed * 0.98;
                    rowing = mode == Mode.RUDERN;
                }
                case WARTEN -> {
                    if (waitLeft < 0) waitLeft = c.value();
                    waitLeft -= dt;
                    if (waitLeft <= 0) { waitLeft = -1; commands.poll(); }
                }
                case KURS_SEGEL, KURS_RIEMEN -> {
                    double dx = c.x() - x, dz = c.z() - z, d = Math.hypot(dx, dz);
                    targetHeading = bearing(dx, dz);
                    boolean sail = c.kind() == Command.Kind.KURS_SEGEL && type.hasSail();
                    if (sail && (!canSail(targetHeading) || sailSet < 0.3)) {
                        sailStalled = !canSail(targetHeading);
                        // Segel zieht nicht oder ist (noch) nicht gesetzt: an die Riemen
                        if (type.oarPairs > 0 && (sailStalled || sailSet < 0.3)) sail = false;
                    }
                    if (sail) { targetSpeed = sailSpeed(heading); mode = Mode.SEGELN; }
                    else { targetSpeed = type.rowSpeed; rowing = true; mode = Mode.RUDERN; }
                    if (d < c.value()) commands.poll();
                }
                case DURCH -> {
                    double gx = c.x(), gz = c.z(), gh = c.value();
                    double fx = Math.sin(Math.toRadians(gh)), fz = -Math.cos(Math.toRadians(gh));
                    double approach = passApproach();
                    double ax = gx - fx * approach, az = gz - fz * approach;
                    if (moorPhase == 0) {
                        double dx = ax - x, dz = az - z, d = Math.hypot(dx, dz);
                        targetHeading = bearing(dx, dz);
                        // langsam, wenn der Kurs noch weit von der Linie ist: enger Drehkreis
                        double turn = Math.abs(wrap(gh - heading));
                        targetSpeed = Math.min(type.rowSpeed, 0.5 + d * 0.06) * (d < 15 && turn > 60 ? 0.5 : 1);
                        if (d < Math.max(4, type.beam)) moorPhase = 1;
                    } else {
                        double proj = (x - ax) * fx + (z - az) * fz;
                        double ahead = proj + 10;
                        targetHeading = bearing(ax + fx * ahead - x, az + fz * ahead - z);
                        targetSpeed = type.rowSpeed * 0.8;
                        if (proj > approach + type.length) { moorPhase = 0; commands.poll(); }
                    }
                    rowing = true;
                    mode = Mode.RUDERN;
                }
                case ANLEGEN -> {
                    double bx = c.x(), bz = c.z(), bh = c.value();
                    double fx = Math.sin(Math.toRadians(bh)), fz = -Math.cos(Math.toRadians(bh));
                    double approach = Math.max(12, type.length * 1.5);
                    double ax = bx - fx * approach, az = bz - fz * approach;
                    if (moorPhase == 0) {
                        double dx = ax - x, dz = az - z, d = Math.hypot(dx, dz);
                        targetHeading = bearing(dx, dz);
                        targetSpeed = Math.min(type.rowSpeed, 0.6 + d * 0.08);
                        if (d < Math.max(4, type.beam)) moorPhase = 1;
                    } else {
                        // der Linie zum Liegeplatz folgen (Zielpunkt 8 m voraus auf der Linie), vor dem Platz abstoppen
                        double proj = (x - ax) * fx + (z - az) * fz, lat = -(x - ax) * fz + (z - az) * fx;
                        double ahead = Math.min(approach, proj + 8);
                        double tx = ax + fx * ahead, tz = az + fz * ahead;
                        double remaining = approach - proj;
                        targetHeading = remaining > 3 ? bearing(tx - x, tz - z) : bh;
                        targetSpeed = remaining > 0.4 ? Math.max(0.25, Math.min(type.rowSpeed * 0.6, remaining * 0.1)) : 0;
                        braking = true;
                        if (Math.abs(remaining) < 1.2 && Math.abs(lat) < 1.5 && speed < 0.45) {
                            mode = Mode.FEST;
                            berth = new double[]{bx, bz, bh};
                            moorPhase = 0;
                            commands.poll();
                        }
                    }
                    if (mode != Mode.FEST) { rowing = true; mode = Mode.ANLEGEN; }
                }
            }
        } else if (mode != Mode.FEST) mode = Mode.TREIBEN;
        // Segel setzen und bergen (rund zehn Sekunden)
        if (!type.hasSail()) sailTarget = 0;
        sailSet += Math.max(-0.1 * dt, Math.min(0.1 * dt, sailTarget - sailSet));
        if (mode == Mode.TREIBEN && sailSet > 0.3) targetSpeed = sailSpeed(heading);
        // Fahrt: träge nach der Wasserverdrängung
        double tau = 4 + type.displacement / 8000;
        if (braking && targetSpeed < speed) tau = Math.min(tau, 3);       // Riemen stoppen das Schiff
        if (mode != Mode.FEST) speed += (targetSpeed - speed) * (1 - Math.exp(-dt / tau));
        // Kurs: Ruder wirkt mit der Fahrt, Riemen drehen auch langsam
        if (mode != Mode.FEST && mode != Mode.TREIBEN) {
            double err = wrap(targetHeading - heading);
            double maxRate = (rowing ? 5.0 : 3.5) * Math.min(1, (rowing ? 0.5 : 0.25) + speed / 1.5) * (type.length < 10 ? 2 : 1);
            double want = Math.max(-maxRate, Math.min(maxRate, err * 0.35));
            yawRate += (want - yawRate) * (1 - Math.exp(-dt / 1.5));
        } else if (mode == Mode.TREIBEN) yawRate *= Math.exp(-dt / 2);
        heading = (heading + yawRate * dt + 360) % 360;
        double[] f = forward();
        // seit Timmendorf Phase 18: Krängung und Abdrift aus dem Segelmodell (nur unter Segel, nach Lee)
        Rig rg = rig;
        double heelT = 0, leeT = 0;
        if (rg != null && type.hasSail() && sailSet > 0.05 && (mode == Mode.SEGELN || mode == Mode.TREIBEN)) {
            double[] q = rg.sail(this, heading);
            double side = Math.signum(wrap(windFromDeg - heading));        // + Wind von Steuerbord: Lee ist Backbord
            if (side == 0) side = 1;
            heelT = -side * q[1] * sailSet;
            leeT = -side * q[2] * Math.min(1, speed / 0.5);
        }
        double kh = 1 - Math.exp(-dt / 4);
        heelDeg += (heelT - heelDeg) * kh;
        leewayDeg += (leeT - leewayDeg) * kh;
        if (mode != Mode.FEST) {
            double[] q = right();
            double lat = speed * Math.tan(Math.toRadians(leewayDeg));
            x += (f[0] * speed + q[0] * lat) * dt;
            z += (f[1] * speed + q[1] * lat) * dt;
        }
        // Rah quer zum scheinbaren Wind
        if (type.hasSail()) {
            double wto = Math.toRadians(windFromDeg + 180);
            double awx = Math.sin(wto) * windSpeed - f[0] * speed, awz = -Math.cos(wto) * windSpeed - f[1] * speed;
            double rel = wrap(bearing(awx, awz) - heading);
            double target = Math.max(-55, Math.min(55, rel > 90 ? rel - 180 : rel < -90 ? rel + 180 : rel));
            yardAngle += (target * 0.8 - yardAngle) * (1 - Math.exp(-dt / 3));
        }
        // Riemen
        boolean wasRowing = rowing;
        if (wasRowing) {
            double before = strokePhase;
            strokePhase = (strokePhase + strokeRate * dt) % 1;
            if (strokePhase < before) stroke();
        }
        this.rowingNow = wasRowing;
        // Kielwasser je 4 m Fahrt
        wakeDist += Math.abs(speed) * dt;
        if (wakeDist > 4 && onWake != null) {
            wakeDist = 0;
            double hl = hull.halfLength;
            double[] bow = toWorld(hl * 0.9, 0, 0), st1 = toWorld(-hl * 0.7, type.beam * 0.35, 0), st2 = toWorld(-hl * 0.7, -type.beam * 0.35, 0);
            double s = Math.min(1, 0.15 * speed * type.beam / 2.5);
            onWake.at(bow[0], bow[2], s);
            onWake.at(st1[0], st1[2], s * 0.6);
            onWake.at(st2[0], st2[2], s * 0.6);
        }
        float_(dt);
    }

    private boolean rowingNow;

    /** Wird gerade gerudert? */
    public boolean rowing() { return rowingNow; }

    private void stroke() {
        double[] st = type.oarStations();
        if (st.length == 0) return;
        double s = st[st.length / 2];
        for (int side = -1; side <= 1; side += 2) {
            double[] blade = oarBlade(s, side, 0.25);
            if (onWake != null) onWake.at(blade[0], blade[2], 0.06 + 0.002 * type.oarPairs);
        }
        if (onStroke != null) {
            double[] p = toWorld(s, 0, type.freeboard);
            onStroke.at(p[0], p[1], p[2], Math.min(1, 0.3 + type.oarPairs / 30.0));
        }
    }

    /**
     * Blätter aller Riemen in der Welt {x, y, z, Seite} (Seite +1 Steuerbord), etwa für Spritzer beim Einsetzen; leer,
     * wenn nicht gerudert wird.
     */
    public double[][] oarBlades() {
        if (!rowingNow) return new double[0][];
        double[] st = type.oarStations();
        double[][] out = new double[st.length * 2][];
        int k = 0;
        for (double s : st)
            for (int side = -1; side <= 1; side += 2) {
                double[] b = oarBlade(s, side, 0.12);
                out[k++] = new double[]{b[0], b[1], b[2], side};
            }
        return out;
    }

    /** Ohne Lage von außen: Höhe, Stampfen und Rollen aus dem Wasser an Bug, Heck und Seiten, weich nachgeführt. */
    private void float_(double dt) {
        if (externalPose) return;
        double k = 1 - Math.exp(-dt / 0.8);
        double heel = Math.toRadians(heelDeg);
        if (water == null) { y += (0 - y) * k; pitch *= 1 - k; if (heel == 0) roll *= 1 - k; else roll += (heel - roll) * k; return; }
        double hl = hull.halfLength * 0.7, hb = type.beam * 0.4;
        double[] f = forward(), r = right();
        double hc = water.height(x, z), hf = water.height(x + f[0] * hl, z + f[1] * hl), ha = water.height(x - f[0] * hl, z - f[1] * hl);
        double hs = water.height(x + r[0] * hb, z + r[1] * hb), hp = water.height(x - r[0] * hb, z - r[1] * hb);
        double ty = (hc * 2 + hf + ha + hs + hp) / 6;
        double tp = Math.atan2(hf - ha, 2 * hl) * 0.6, tr = Math.atan2(hs - hp, 2 * hb) * -0.5 + heel;
        y += (ty - y) * k;
        pitch += (tp - pitch) * k;
        roll += (tr - roll) * k;
    }

    // ------------------------------------------------------------------ Riemen und bewegliche Teile

    /** Blatt eines Riemens in der Welt (Riemen an Stelle s, Seite side) bei Anteil along vom Ende. */
    double[] oarBlade(double s, int side, double along) {
        double[] o = oarPose(s, side);
        return toWorld(o[0] + (o[3] - o[0]) * (1 - along), o[1] + (o[4] - o[1]) * (1 - along), o[2] + (o[5] - o[2]) * (1 - along));
    }

    /**
     * Ein Riemen in Schiffskoordinaten: Dolle {s, q, y} und Blattende {s, q, y}. Beim Rudern schwingt er um 22° vor und
     * zurück, taucht im Durchzug ein und hebt sich beim Vorholen; sonst liegt er eingezogen längs auf den Querbalken.
     */
    double[] oarPose(double s, int side) {
        double yp = hull.sheerY(s) - 0.2, qp = side * Math.max(0.2, hull.halfBreadthAt(s, yp));
        double len = type.oarLength * 0.78;
        if (!rowingNow) {
            double yy = type.hold ? type.freeboard * 0.35 + 0.15 : hull.keel(0) + type.draft * 0.45 + 0.12;
            double q = side * Math.max(0.15, hull.halfBreadthAt(s, yy) - 0.25);
            return new double[]{s - len / 2, q, yy, s + len / 2, q, yy};
        }
        double ph = strokePhase * 2 * Math.PI;
        double sweep = Math.toRadians(22) * Math.cos(ph);            // + = Blatt nach vorn (Auslage)
        double inWater = Math.sin(ph) > 0 ? Math.sin(ph) : 0;        // Durchzug: Blatt im Wasser
        double reach = Math.sqrt(Math.max(0.1, len * len - Math.pow(yp + 0.05, 2)));
        double drop = (yp + 0.05) * (0.6 + 0.4 * inWater) - 0.35 * (1 - inWater);
        double es = s + Math.sin(sweep) * reach, eq = qp + side * Math.cos(sweep) * reach, ey = yp - drop;
        return new double[]{s, qp, yp, es, eq, ey};
    }

    /**
     * Bewegliche Teile in Schiffskoordinaten, je Bild neu: Riemen, Rah und Segel (gesetzt mit Bauch im Wind oder
     * aufgetucht an der Rah), Schoten.
     */
    public Mesh dynamicMesh(Mesh m) {
        m.clear();
        double[] st = type.oarStations();
        for (double s : st)
            for (int side = -1; side <= 1; side += 2) {
                double[] o = oarPose(s, side);
                double[] a = {o[0], o[1], o[2]}, b = {o[3], o[4], o[5]};
                double[] dir = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
                double l = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
                double[] in = {a[0] - dir[0] / l * type.oarLength * 0.22, a[1] - dir[1] / l * type.oarLength * 0.22, a[2] - dir[2] / l * type.oarLength * 0.22};
                double[] bladeStart = {a[0] + dir[0] * 0.8, a[1] + dir[1] * 0.8, a[2] + dir[2] * 0.8};
                m.beam(Hull.m(in), Hull.m(bladeStart), 0.04, 0.035, 5, Material.WOOD);
                // Blatt: flach, senkrecht zum Schaft
                double[] w = Hull.m(rowingNow ? new double[]{0, 0, 1} : new double[]{0, 1, 0});
                double bw = 0.08 + type.beam * 0.004;
                double[] p0 = Hull.m(bladeStart), p1 = Hull.m(b);
                double[] q0 = {p0[0] + w[0] * bw, p0[1] + w[1] * bw, p0[2] + w[2] * bw}, q1 = {p1[0] + w[0] * bw, p1[1] + w[1] * bw, p1[2] + w[2] * bw};
                double[] r0 = {p0[0] - w[0] * bw, p0[1] - w[1] * bw, p0[2] - w[2] * bw}, r1 = {p1[0] - w[0] * bw, p1[1] - w[1] * bw, p1[2] - w[2] * bw};
                m.quad2(r0, r1, q1, q0, Material.WOOD);
            }
        if (type.hasSail()) sail(m);
        return m;
    }

    private void sail(Mesh m) {
        double s0 = type.mastStation(), yy = type.yardHeight, hw = type.sailWidth / 2;
        double ya = Math.toRadians(yardAngle);
        // Rah: quer, um den Mast gedreht (Brassen)
        double ex = Math.cos(ya), es = -Math.sin(ya);           // Richtung der Rah in (q, s)
        double[] pl = Hull.m(s0 + es * -hw, ex * -hw, yy), pr = Hull.m(s0 + es * hw, ex * hw, yy);
        m.beam(pl, pr, 0.09, 0.07, 6, Material.WOOD);
        if (sailSet < 0.04) {
            m.beam(pl, pr, 0.22, 0.22, 8, Material.SAIL);
            return;
        }
        // Bauch nach Lee: der Wind drückt das Tuch in seine Richtung (in Schiffskoordinaten)
        double wto = Math.toRadians(windFromDeg + 180 - heading);
        double ws = -Math.cos(wto) * -1, wq = Math.sin(wto);    // Wind nach vorn (s) und Steuerbord (q)
        double ns = ex, nq = -es;                               // Normale der Segelfläche in (s, q)
        double side = Math.signum(ws * ns + wq * nq);
        if (side == 0) side = 1;
        double depth = 0.11 * type.sailHeight * Math.min(1, windSpeed / 8 + 0.25) * sailSet;
        double h = type.sailHeight * sailSet;
        int nu = 10, nv = Math.max(2, (int) Math.round(8 * sailSet));
        double[][][] p = new double[nu + 1][nv + 1][];
        for (int i = 0; i <= nu; i++)
            for (int j = 0; j <= nv; j++) {
                double a = -hw + 2 * hw * i / nu, b = j / (double) nv;
                double bulge = depth * Math.sin(Math.PI * i / nu) * Math.sin(Math.PI * (0.15 + 0.85 * b)) * side;
                double sl = s0 + es * a + ns * bulge, ql = ex * a + nq * bulge;
                p[i][j] = Hull.m(sl, ql, yy - 0.1 - h * b);
            }
        for (int i = 0; i < nu; i++)
            for (int j = 0; j < nv; j++) {
                double u0 = 2 * hw * i / nu, u1 = 2 * hw * (i + 1) / nu, v0 = h * j / nv, v1 = h * (j + 1) / nv;
                m.quad(p[i][j], p[i + 1][j], p[i + 1][j + 1], p[i][j + 1], Material.SAIL, new double[]{u0, v0, u1, v0, u1, v1, u0, v1});
            }
        // Schoten von den unteren Ecken zur Bordkante achtern
        for (int sideQ = -1; sideQ <= 1; sideQ += 2) {
            double[] corner = p[sideQ < 0 ? 0 : nu][nv];
            double sa = s0 - type.length * 0.22;
            m.beam(corner, Hull.m(sa, sideQ * (hull.halfBeam(sa) - 0.1), hull.sheerY(sa)), 0.012, 3, Material.ROPE);
        }
    }

    // ------------------------------------------------------------------ Hilfen

    /** Kurs von (0, 0) nach (dx, dz) in Grad von Nord. */
    public static double bearing(double dx, double dz) { double h = Math.toDegrees(Math.atan2(dx, -dz)); return h < 0 ? h + 360 : h; }

    /** Länge der Linie vor einer Engstelle ({@link Command#passThrough}), m. */
    public double passApproach() { return Math.max(30, type.length * 2); }

        static double wrap(double d) { return ((d % 360) + 540) % 360 - 180; }

    /** Vor Anker gehen, Befehle verwerfen, Lage setzen (für Szenen und Prüfung). */
    public void place(double px, double pz, double headingDeg) {
        x = px; z = pz; heading = headingDeg; speed = 0; yawRate = 0; mode = Mode.TREIBEN; moorPhase = 0; berth = null;
        commands.clear();
    }

    /** Sofort festmachen am Liegeplatz. */
    public void mooredAt(double px, double pz, double headingDeg) {
        place(px, pz, headingDeg);
        mode = Mode.FEST;
        berth = new double[]{px, pz, headingDeg};
        sailSet = sailTarget = 0;
    }

    /** „Knorr · rudert · 1,3 m/s · Kurs 213° · Segel 0 %“ */
    public String describe() {
        String what = switch (mode) {
            case SEGELN -> "segelt";
            case RUDERN -> sailStalled ? "rudert (Segel zieht nicht)" : "rudert";
            case ANLEGEN -> "legt an";
            case FEST -> "fest";
            default -> "treibt";
        };
        String extra = rig != null && (Math.abs(heelDeg) > 0.5 || Math.abs(leewayDeg) > 0.5) ? String.format(Locale.GERMAN, " · Krängung %.0f°, Abdrift %.0f°", Math.abs(heelDeg), Math.abs(leewayDeg)) : "";
        return String.format(Locale.GERMAN, "%s · %s · %.1f m/s · Kurs %03.0f° · Segel %.0f %%%s", name, what, speed, heading, sailSet * 100, extra);
    }
}
