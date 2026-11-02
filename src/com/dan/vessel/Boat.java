package com.dan.vessel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ein Boot als Starrkörper mit sechs Freiheiten (LituyaBay1958 Phase 15, Werkbuch 5.9): die Proben des Rumpfs
 * ({@link Hull}) tragen je nach dem Anteil unter Wasser, die Masse liegt im Schwerpunkt. Daraus folgen Schwimmlage, Tauchen,
 * Stampfen, Rollen und Kentern von selbst, auch in einer Welle, die das Boot ganz anhebt.
 * <ul>
 * <li><b>Auftrieb</b> ρ g V je Probe nach dem Anteil unter der Oberfläche; das Steuerhaus trägt mit
 * {@link Hull#houseBuoyancy}.</li>
 * <li><b>Widerstand</b> gegen das bewegte Wasser: linear je Volumen (Wellenbildung, senkrecht stärker) und quadratisch auf
 * der Außenhaut, längs des Rumpfs nur zu {@link #axialDrag} (der Rumpf ist schlank). Die senkrechte Geschwindigkeit des
 * Wassers kommt aus der Änderung der Oberfläche je Probe. So treibt eine Welle das Boot mit ihrer Strömung.</li>
 * <li><b>Volllaufen:</b> Liegt das Deck unter Wasser, strömt durch Öffnungen ({@link #openings} m² je m² Deck) Wasser
 * ein, Q = C<sub>d</sub> A √(2 g h); gekentert durch das Steuerhaus ({@link #doorArea}). Das Wasser im Rumpf sammelt sich
 * in den tiefsten Proben (freie Oberfläche: es läuft zur tiefen Seite) und zieht dort. Lenzpumpe {@link #pump} kg/s.</li>
 * <li><b>Sinken:</b> wenn der Rumpf trotz allem nicht mehr trägt; auf dem Grund liegt er auf.</li>
 * <li><b>Grund:</b> Proben unter dem Grund werden zurückgedrückt, mit Reibung (Auflaufen, über eine Nehrung getragen).</li>
 * <li><b>Maschine und Ruder:</b> Schub an der Schraube, wenn sie im Wasser ist; Seitenkraft am Ruder nach Fahrt und
 * Schraubenstrahl.</li>
 * <li><b>Ankerketten</b> ({@link Chain}) hängen an der Klüse; ihre Kraft geht in jedem Teilschritt in das Boot.</li>
 * </ul>
 * Welt: x Ost, y oben, z Süd. Kurs in Grad von Nord über Ost. {@link #model()} gibt die Modellmatrix vom Achsenkreuz des
 * Rumpfs (s vorn, y oben, q Steuerbord) in die Welt.
 */
public final class Boat {
    public static final double G = 9.81;
    public final Hull hull;
    public final String name;
    /** Dichte des Wassers (kg/m³): Seewasser; Gletschermilch der Bucht etwas weniger [G]. */
    public double rho = 1025;
    /** Masse ohne Wasser im Rumpf (kg) und Schwerpunkt im Achsenkreuz des Rumpfs (s, y über Kiel, q). */
    public final double mass;
    public final double[] cg;
    /** Trägheitsradien um Längs-, Hoch- und Querachse (m). */
    public final double kRoll, kYaw, kPitch;
    /** Widerstand: linear je m³ (1/s) senkrecht und waagerecht, Formbeiwert, Anteil längs [G]. */
    public double dampVertical = 1.2, dampHorizontal = 0.15, dragCoef = 1.0, axialDrag = 0.08;
    /** Wellenwiderstand bei Froude-Zahl 0,4 als Anteil des Gewichts (Verdränger) [G]. */
    public double waveResistance = 0.02;
    /** Zusätzliche Masse des mitbewegten Wassers (Anteil der verdrängten Masse) [G]. */
    public double addedMass = 0.25;
    /** Volllaufen: Öffnungen je m² Deck (m²/m²; gedeckt, Luken zu, Speigatten), Tür des Steuerhauses (m²), Ausflussbeiwert, Lenzpumpe (kg/s) [G]. */
    public double openings = 0.004, doorArea = 0.6, dischargeCoef = 0.6, pump = 2;
    /** Maschine: größter Schub (N), Hebel 0 … 1 (rückwärts negativ); Ruder −1 … 1 (Steuerbord positiv), Ruderfläche (m²). */
    public double maxThrust = 9000, throttle, rudder, rudderArea = 0.6;
    /** Teilschritt (s). */
    public double maxStep = 0.004;

    // Lage: Schwerpunkt in der Welt, Drehung (Quaternion w, x, y, z), Geschwindigkeiten (Welt)
    final double[] o = new double[3], q = {1, 0, 0, 0}, R = new double[9], v = new double[3], w = new double[3];
    private final double[] hPrev;
    private boolean first = true;
    private final List<Chain> chains = new ArrayList<>();
    private final Integer[] order;
    private final double[] wy;
    private int sortTick;

    // Zustand
    double flood, floodCap, time, submerged, grounded, lastFlowIn, peakChain;
    boolean capsized, sunk;
    private double floodCx, floodCy, floodCz;
    private double maxBuoyancy;

    /** Ein Boot aus einem Rumpf mit Masse (kg; ≤ 0: die Konstruktionsmasse) und Schwerpunkt in der Höhe kg über dem Kiel. */
    public Boat(Hull hull, double mass, double kg) {
        this.hull = hull;
        this.name = hull.name;
        hull.build();
        this.mass = mass > 0 ? mass : hull.massDesign();
        this.cg = new double[]{hull.lcb(), kg, 0};
        kRoll = 0.38 * hull.beam;
        kYaw = 0.26 * hull.loa;
        kPitch = 0.25 * hull.loa;
        hPrev = new double[hull.n];
        order = new Integer[hull.n];
        for (int p = 0; p < hull.n; p++) order[p] = p;
        wy = new double[hull.n];
        double c3 = hull.cell * hull.cell * hull.cell, vh = 0;
        for (int p = 0; p < hull.n; p++) if (!hull.house[p]) vh += c3;
        floodCap = 0.8 * vh * rho;
        maxBuoyancy = 0;
        for (int p = 0; p < hull.n; p++) maxBuoyancy += rho * G * c3 * (hull.house[p] ? hull.houseBuoyancy : 1);
        setRotation(1, 0, 0, 0);
    }

    // ------------------------------------------------------------------ Lage

    /** Setzt das Boot aufrecht auf den Ort (x, z) mit Kurs heading und den Schwerpunkt in der Höhe y. */
    public void place(double x, double y, double z, double heading) { place(x, y, z, heading, 0); }

    /** Wie {@link #place(double, double, double, double)} mit Krängung roll (Grad, Steuerbord tief positiv). */
    public void place(double x, double y, double z, double heading, double roll) {
        double a = Math.toRadians(-heading) / 2;   // Drehung um y: Kurs im Uhrzeigersinn von oben = negative Drehung um +y
        double h0 = Math.toRadians(90) / 2;
        // Achsenkreuz: Rumpf-s auf Welt −z bei Kurs 0 (Nord). Grunddrehung: s (x im Rumpf) → −z: +90° um y
        double[] qa = {Math.cos(h0), 0, Math.sin(h0), 0}, qb = {Math.cos(a), 0, Math.sin(a), 0};
        double r2 = Math.toRadians(roll) / 2;
        double[] qr = {Math.cos(r2), Math.sin(r2), 0, 0};
        double[] qq = qmul(qmul(qb, qa), qr);
        setRotation(qq[0], qq[1], qq[2], qq[3]);
        o[0] = x; o[1] = y; o[2] = z;
        Arrays.fill(v, 0);
        Arrays.fill(w, 0);
        first = true;
        flood = 0;
        capsized = sunk = false;
    }

    /** Wie {@link #place}, dann senkrecht in die Schwimmlage auf dem Wasser (ruhig, nur Tauchen). */
    public void settle(Water water, double x, double z, double heading) {
        double lo = water.height(x, z) - hull.depth - 3, hi = water.height(x, z) + hull.depth + 3;
        for (int it = 0; it < 60; it++) {
            double m = 0.5 * (lo + hi);
            place(x, m, z, heading);
            if (buoyancy(water) > mass * G) lo = m; else hi = m;
        }
        place(x, 0.5 * (lo + hi), z, heading);
    }

    /**
     * Aufrichtender Hebel GZ (m) bei der Krängung heel (Grad) in ruhigem Wasser: das Boot wird gekrängt in die Lage gesetzt,
     * in der Auftrieb und Gewicht gleich sind, dann das Moment des Auftriebs um den Schwerpunkt geteilt durch das Gewicht.
     */
    public double gz(Water water, double x, double z, double heading, double heel) {
        double lo = water.height(x, z) - hull.depth - 3, hi = water.height(x, z) + hull.depth + 3;
        for (int it = 0; it < 60; it++) {
            double m = 0.5 * (lo + hi);
            place(x, m, z, heading, heel);
            if (buoyancy(water) > mass * G) lo = m; else hi = m;
        }
        place(x, 0.5 * (lo + hi), z, heading, heel);
        double c3 = hull.cell * hull.cell * hull.cell, mx = 0, my = 0, mz = 0;
        for (int p = 0; p < hull.n; p++) {
            double[] pw = probeWorld(p);
            double f = rho * G * c3 * Hull.frac(water.height(pw[0], pw[2]) - (pw[1] - hull.cell / 2), hull.cell) * (hull.house[p] ? hull.houseBuoyancy : 1);
            double rx = pw[0] - o[0], rz = pw[2] - o[2];
            mx += -rz * f; mz += rx * f;
        }
        double[] fw = dirWorld(1, 0, 0);
        double gzv = -(mx * fw[0] + mz * fw[2]) / (mass * G);
        place(x, 0.5 * (lo + hi), z, heading, 0);
        return gzv;
    }

    private double buoyancy(Water water) {
        double f = 0, c3 = hull.cell * hull.cell * hull.cell;
        for (int p = 0; p < hull.n; p++) {
            double[] pw = probeWorld(p);
            f += rho * G * c3 * Hull.frac(water.height(pw[0], pw[2]) - (pw[1] - hull.cell / 2), hull.cell) * (hull.house[p] ? hull.houseBuoyancy : 1);
        }
        return f;
    }

    void setRotation(double a, double b, double c, double d) {
        q[0] = a; q[1] = b; q[2] = c; q[3] = d;
        R[0] = 1 - 2 * (c * c + d * d); R[1] = 2 * (b * c - a * d); R[2] = 2 * (b * d + a * c);
        R[3] = 2 * (b * c + a * d); R[4] = 1 - 2 * (b * b + d * d); R[5] = 2 * (c * d - a * b);
        R[6] = 2 * (b * d - a * c); R[7] = 2 * (c * d + a * b); R[8] = 1 - 2 * (b * b + c * c);
    }

    static double[] qmul(double[] a, double[] b) {
        return new double[]{a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3], a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
                a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1], a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]};
    }

    /** Punkt (s, y, q) des Rumpfs in der Welt. */
    public double[] toWorld(double s, double y, double qq) {
        double ls = s - cg[0], ly = y - cg[1], lq = qq - cg[2];
        // Rumpf-Achsenkreuz → Rechenachsen: s → x, y → y, q → z (das Quaternion dreht s nach vorn, q nach Steuerbord)
        return new double[]{R[0] * ls + R[1] * ly + R[2] * lq + o[0], R[3] * ls + R[4] * ly + R[5] * lq + o[1], R[6] * ls + R[7] * ly + R[8] * lq + o[2]};
    }

    /** Richtung (s, y, q) des Rumpfs in der Welt. */
    public double[] dirWorld(double s, double y, double qq) {
        return new double[]{R[0] * s + R[1] * y + R[2] * qq, R[3] * s + R[4] * y + R[5] * qq, R[6] * s + R[7] * y + R[8] * qq};
    }

    double[] probeWorld(int p) { return toWorld(hull.ps[p], hull.py[p], hull.pq[p]); }

    /** Geschwindigkeit eines Weltpunkts, der fest am Rumpf sitzt. */
    public double[] velocityAt(double[] pw) {
        double rx = pw[0] - o[0], ry = pw[1] - o[1], rz = pw[2] - o[2];
        return new double[]{v[0] + w[1] * rz - w[2] * ry, v[1] + w[2] * rx - w[0] * rz, v[2] + w[0] * ry - w[1] * rx};
    }

    /** Hängt eine Kette an die Klüse; sie wird mit dem Boot gerechnet. */
    public void attach(Chain c) {
        chains.add(c);
        c.attachTo(toWorld(hull.fairlead()[0], hull.fairlead()[1], hull.fairlead()[2]));
    }

    public List<Chain> chains() { return chains; }

    // ------------------------------------------------------------------ Schritt

    /** Rechnet dt (s) in Teilschritten; die Ketten laufen mit. */
    public void step(Water water, double dt) {
        int n = Math.max(1, (int) Math.ceil(dt / maxStep));
        double h = dt / n;
        for (int i = 0; i < n; i++) sub(water, h);
    }

    private void sub(Water water, double dt) {
        Hull H = hull;
        double c3 = H.cell * H.cell * H.cell, area = H.cell * H.cell;
        double mTot = mass + flood;
        // Wasser im Rumpf: in den tiefsten Proben (alle 10 Teilschritte neu sortiert)
        if (flood > 0 && sortTick++ % 10 == 0) floodCentroid(c3);
        double fx = 0, fy = -mass * G, fz = 0, tx = 0, ty = 0, tz = 0;
        if (flood > 0) {
            fy -= flood * G;
            double rx = floodCx - o[0], ry = floodCy - o[1], rz = floodCz - o[2], gy = -flood * G;
            tx += -rz * gy; tz += rx * gy;
        }
        double sub = 0, inflow = 0;
        grounded = 0;
        // Achsen des Rumpfs in der Welt (für den schlanken Widerstand)
        double ax0 = R[0], ax1 = R[3], ax2 = R[6];
        for (int p = 0; p < H.n; p++) {
            double[] pw = probeWorld(p);
            double rx = pw[0] - o[0], ry = pw[1] - o[1], rz = pw[2] - o[2];
            double ux = v[0] + w[1] * rz - w[2] * ry, uy = v[1] + w[2] * rx - w[0] * rz, uz = v[2] + w[0] * ry - w[1] * rx;
            double hw = water.height(pw[0], pw[2]);
            double wv = first ? 0 : Math.max(-20, Math.min(20, (hw - hPrev[p]) / dt));
            hPrev[p] = hw;
            double f = Hull.frac(hw - (pw[1] - H.cell / 2), H.cell);
            double gx = 0, gy = 0, gz = 0;
            if (f > 0) {
                double vol = c3 * f * (H.house[p] ? H.houseBuoyancy : 1);
                sub += vol;
                gy += rho * G * vol;
                double[] cur = water.velocity(pw[0], pw[2]);
                double dx = cur[0] - ux, dy = wv - uy, dzv = cur[1] - uz;
                // linear (Wellenbildung): senkrecht stark, waagerecht schwach
                double kv = rho * c3 * f * dampVertical, kh = rho * c3 * f * dampHorizontal;
                double alH = (dx * ax0 + dzv * ax2) * (1 - axialDrag);        // längs des Rumpfs kaum
                gx += kh * (dx - alH * ax0); gy += kv * dy; gz += kh * (dzv - alH * ax2);
                if (H.surface[p]) {
                    // quadratisch auf der Außenhaut; längs nur ein Teil
                    double along = dx * ax0 + dy * ax1 + dzv * ax2;
                    double qx = dx - (1 - axialDrag) * along * ax0, qy = dy - (1 - axialDrag) * along * ax1, qz = dzv - (1 - axialDrag) * along * ax2;
                    double sp = Math.sqrt(qx * qx + qy * qy + qz * qz);
                    double k = 0.5 * rho * dragCoef * area * f * sp;
                    // nicht über die Geschwindigkeit des Wassers hinaus beschleunigen (stabil bei großem dt)
                    k = Math.min(k, 0.5 * mTot / H.n / dt);
                    gx += k * qx; gy += k * qy; gz += k * qz;
                }
                // Volllaufen über das Deck
                if (H.deckTop[p] && !capsized) {
                    double d = hw - (pw[1] + H.cell / 2);
                    if (d > 0) inflow += rho * dischargeCoef * openings * area * Math.sqrt(2 * G * d);
                }
                if (H.house[p] && capsized) {
                    double d = hw - pw[1];
                    if (d > 0) inflow += rho * dischargeCoef * doorArea / Math.max(1, H.n / 40.0) * Math.sqrt(2 * G * d);
                }
            } else if (H.surface[p]) {
                double[] wd = water.wind(pw[0], pw[2]);
                double dx = wd[0] - ux, dzv = wd[1] - uz, sp = Math.hypot(dx, dzv), k = 0.5 * 1.2 * 1.0 * area * sp;
                gx += k * dx; gz += k * dzv;
            }
            // Grund
            double bed = water.bed(pw[0], pw[2]);
            double pen = Math.min(H.cell, bed - (pw[1] - H.cell / 2));
            if (pen > 0) {
                double kk = mTot * G / Math.max(1, H.n * 0.05) / H.cell;
                double nfor = Math.max(0, kk * pen - 2 * Math.sqrt(kk * mTot / H.n) * Math.min(0, uy));
                gy += nfor;
                double t = Math.hypot(ux, uz);
                if (t > 1e-4) { double fr = Math.min(0.5 * nfor, mTot / H.n * t / dt) / t; gx -= fr * ux; gz -= fr * uz; }
                grounded += pen;
            }
            fx += gx; fy += gy; fz += gz;
            tx += ry * gz - rz * gy; ty += rz * gx - rx * gz; tz += rx * gy - ry * gx;
        }
        first = false;
        submerged = sub;
        // Wellenwiderstand längs (am Schwerpunkt): steigt über der Rumpfgeschwindigkeit steil, R = Δ g · c (Fn / 0,4)⁶ [G]
        if (sub > 0) {
            double[] cur = water.velocity(o[0], o[2]);
            double ux = v[0] - cur[0], uz = v[2] - cur[1], ua = ux * ax0 + uz * ax2;
            double fn = Math.abs(ua) / Math.sqrt(G * hull.loa * 0.92);
            double rw = mTot * G * waveResistance * Math.pow(fn / 0.4, 6) * Math.min(1, sub / (mass / rho));
            rw = Math.min(rw, 0.5 * mTot * Math.abs(ua) / dt);
            fx -= Math.signum(ua) * rw * ax0; fz -= Math.signum(ua) * rw * ax2;
        }
        // Maschine und Ruder
        double[] pr = H.propeller();
        double[] prw = toWorld(pr[0], pr[1], pr[2]);
        if (water.height(prw[0], prw[2]) > prw[1]) {
            double[] fw = dirWorld(1, 0, 0), sw = dirWorld(0, 0, 1);
            double[] up = velocityAt(prw);
            double uf = up[0] * fw[0] + up[1] * fw[1] + up[2] * fw[2];
            double thrust = maxThrust * throttle;
            double wash = Math.max(0, thrust) / (rho * 0.5);   // Schraubenstrahl (m²/s²) [G]
            // Ruder nach Steuerbord: Kraft am Heck nach Backbord, der Bug dreht nach Steuerbord (Kurs wächst)
            double side = -0.5 * rho * rudderArea * (uf * uf + wash * 0.02) * 2 * Math.PI * Math.toRadians(35) * Math.max(-1, Math.min(1, rudder));
            double gx = thrust * fw[0] + side * sw[0], gy = thrust * fw[1] + side * sw[1], gz = thrust * fw[2] + side * sw[2];
            double rx = prw[0] - o[0], ry = prw[1] - o[1], rz = prw[2] - o[2];
            fx += gx; fy += gy; fz += gz;
            tx += ry * gz - rz * gy; ty += rz * gx - rx * gz; tz += rx * gy - ry * gx;
        }
        // Ketten
        if (!chains.isEmpty()) {
            double[] fl = H.fairlead();
            double[] flw = toWorld(fl[0], fl[1], fl[2]);
            double[] flv = velocityAt(flw);
            for (Chain c : chains) {
                double[] cf = c.step(water, dt, flw, flv);
                if (cf == null) continue;
                double rx = flw[0] - o[0], ry = flw[1] - o[1], rz = flw[2] - o[2];
                fx += cf[0]; fy += cf[1]; fz += cf[2];
                tx += ry * cf[2] - rz * cf[1]; ty += rz * cf[0] - rx * cf[2]; tz += rx * cf[1] - ry * cf[0];
                peakChain = Math.max(peakChain, Math.sqrt(cf[0] * cf[0] + cf[1] * cf[1] + cf[2] * cf[2]));
            }
        }
        // Wasser im Rumpf
        lastFlowIn = inflow;
        flood = Math.max(0, Math.min(floodCap, flood + (inflow - pump) * dt));
        // Integration (halb implizit)
        double madd = mTot + addedMass * rho * sub;
        v[0] += fx / madd * dt; v[1] += fy / madd * dt; v[2] += fz / madd * dt;
        double[] Iw = worldInvInertia(mTot * (1 + addedMass * Math.min(1, rho * sub / Math.max(1, mTot))));
        w[0] += (Iw[0] * tx + Iw[1] * ty + Iw[2] * tz) * dt;
        w[1] += (Iw[3] * tx + Iw[4] * ty + Iw[5] * tz) * dt;
        w[2] += (Iw[6] * tx + Iw[7] * ty + Iw[8] * tz) * dt;
        double ws = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]);
        if (ws > 6) { w[0] *= 6 / ws; w[1] *= 6 / ws; w[2] *= 6 / ws; }
        o[0] += v[0] * dt; o[1] += v[1] * dt; o[2] += v[2] * dt;
        double[] qd = qmul(new double[]{0, w[0], w[1], w[2]}, q);
        double a = q[0] + 0.5 * dt * qd[0], b = q[1] + 0.5 * dt * qd[1], c = q[2] + 0.5 * dt * qd[2], d = q[3] + 0.5 * dt * qd[3];
        double ql = Math.sqrt(a * a + b * b + c * c + d * d);
        setRotation(a / ql, b / ql, c / ql, d / ql);
        time += dt;
        // Kentern und Sinken
        if (R[4] < 0) capsized = true;
        if (!sunk && flood > 0 && (mass + flood) * G > maxBuoyancy && immersion() > 0.97) sunk = true;
    }

    private void floodCentroid(double c3) {
        Hull H = hull;
        int m = 0;
        for (int p = 0; p < H.n; p++) {
            if (H.house[p]) { wy[p] = Double.MAX_VALUE; continue; }
            wy[p] = probeWorld(p)[1];
        }
        Arrays.sort(order, (a, b) -> Double.compare(wy[a], wy[b]));
        double need = flood / rho, sx = 0, sy = 0, sz = 0, got = 0;
        for (Integer p : order) {
            if (wy[p] == Double.MAX_VALUE) break;
            double take = Math.min(c3, need - got);
            if (take <= 0) break;
            double[] pw = probeWorld(p);
            sx += pw[0] * take; sy += pw[1] * take; sz += pw[2] * take;
            got += take;
            m++;
        }
        if (got > 0) { floodCx = sx / got; floodCy = sy / got; floodCz = sz / got; }
    }

    private double[] worldInvInertia(double m) {
        // Trägheit im Rumpf (Achsen s, y, q): Rollen um s, Gieren um y, Stampfen um q
        double is = 1 / (m * kRoll * kRoll), iy = 1 / (m * kYaw * kYaw), iq = 1 / (m * kPitch * kPitch);
        double[] out = new double[9];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                out[i * 3 + j] = R[i * 3] * is * R[j * 3] + R[i * 3 + 1] * iy * R[j * 3 + 1] + R[i * 3 + 2] * iq * R[j * 3 + 2];
        return out;
    }

    // ------------------------------------------------------------------ Abfragen

    /** Modellmatrix (Spalten, float) vom Achsenkreuz des Rumpfs (s, y, q) in die Welt. */
    public float[] model() {
        double[] t = toWorld(0, 0, 0);
        return new float[]{(float) R[0], (float) R[3], (float) R[6], 0, (float) R[1], (float) R[4], (float) R[7], 0,
                (float) R[2], (float) R[5], (float) R[8], 0, (float) t[0], (float) t[1], (float) t[2], 1};
    }

    /** Schwerpunkt in der Welt. */
    public double[] position() { return o.clone(); }

    /** Drehung als Quaternion {w, x, y, z} (Achsenkreuz des Rumpfs → Welt, mit {@link #cg} um den Schwerpunkt). */
    public double[] orientation() { return q.clone(); }

    /**
     * Setzt Lage und Drehung von außen (zum Abspielen einer vorgerechneten Bahn): Schwerpunkt o, Quaternion q {w, x, y, z};
     * Geschwindigkeiten bleiben.
     */
    public void pose(double[] pos, double[] quat) {
        double l = Math.sqrt(quat[0] * quat[0] + quat[1] * quat[1] + quat[2] * quat[2] + quat[3] * quat[3]);
        setRotation(quat[0] / l, quat[1] / l, quat[2] / l, quat[3] / l);
        o[0] = pos[0]; o[1] = pos[1]; o[2] = pos[2];
    }

    /** Autopilot: Ruder auf den Kurs zum Punkt (x, z), voll bei 30° Abweichung; gibt den Abstand zurück (m). */
    public double steuern(double x, double z) {
        double h = Math.toDegrees(Math.atan2(x - o[0], -(z - o[2])));
        double d = ((h - heading()) + 540) % 360 - 180;
        rudder = Math.max(-1, Math.min(1, d / 30));
        return Math.hypot(x - o[0], z - o[2]);
    }

    public double[] velocity() { return v.clone(); }

    /** Kurs (Grad von Nord), Trimm (Bug hoch positiv) und Krängung (Steuerbord tief positiv) in Grad. */
    public double heading() { double[] f = dirWorld(1, 0, 0); return (Math.toDegrees(Math.atan2(f[0], -f[2])) + 360) % 360; }

    public double pitch() { double[] f = dirWorld(1, 0, 0); return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, f[1])))); }

    public double roll() { double[] s = dirWorld(0, 0, 1); return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -s[1])))); }

    /** Wie weit die Hochachse des Rumpfs nach oben zeigt (1 aufrecht, &lt; 0 gekentert). */
    public double upright() { return R[4]; }

    /** Tiefgang am Schwerpunkt über dem Kiel bis zur Wasserlinie bei aufrechter Lage (m). */
    public double draftNow(Water water) { double[] k = toWorld(cg[0], 0, 0); return water.height(k[0], k[2]) - k[1]; }

    /** Anteil des Rumpfvolumens unter Wasser. */
    public double immersion() { double v0 = hull.volume() + 1e-9; return Math.min(1, submerged / v0); }

    public double flood() { return flood; }

    /** Setzt das Wasser im Rumpf (kg, höchstens 80 % des Rumpfvolumens). */
    public void fill(double kg) { flood = Math.max(0, Math.min(floodCap, kg)); sortTick = 0; }

    /** Größte Wassermenge im Rumpf (kg). */
    public double floodCapacity() { return floodCap; }

    /** Senkrechte Eigenperiode nach der Handrechnung T = 2π √(m (1 + a) / (ρ g A<sub>W</sub>)) (s). */
    public double heavePeriodHand() { return 2 * Math.PI * Math.sqrt(mass * (1 + addedMass) / (rho * G * hull.waterplane())); }

    public boolean capsized() { return capsized; }

    public boolean sunk() { return sunk; }

    public boolean grounded() { return grounded > 0; }

    public double time() { return time; }

    /** Größte Kraft einer Kette am Bug bisher (N). */
    public double peakChain() { return peakChain; }

    /**
     * Metazentrische Höhe aus der Form (GM = KB + BM − KG, BM = I<sub>T</sub>/V) aufrecht auf dem Konstruktionstiefgang (m).
     */
    public double gmDesign() { return hull.kb() + hull.waterplaneInertia() / (mass / rho) - cg[1]; }

    /** Rollperiode nach der Handrechnung T = 2π k / √(g GM) mit der zusätzlichen Masse (s). */
    public double rollPeriodHand() {
        double gm = gmDesign();
        return gm > 0 ? 2 * Math.PI * kRoll * Math.sqrt(1 + addedMass) / Math.sqrt(G * gm) : Double.NaN;
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s · %.1f t, KG %.2f m, GM %.2f m · %s%s%s", hull, mass / 1000, cg[1], gmDesign(),
                capsized ? "gekentert, " : "", sunk ? "gesunken, " : "", flood > 1 ? String.format(Locale.GERMAN, "%.1f t Wasser im Rumpf", flood / 1000) : "trocken");
    }
}
