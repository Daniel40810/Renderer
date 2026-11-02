package com.dan.debris;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Treibgut auf dem Wasser (seit LituyaBay1958 Phase 12, Werkbuch 5.5): Stämme mit und ohne Krone, Äste und Eisbrocken
 * als liegende Zylinder in der Ebene (Ort, Drehung um die Senkrechte). Jeder schwimmt mit dem Tiefgang aus seiner Dichte
 * (Kreisabschnitt: der eingetauchte Anteil der Fläche ist ρ/ρ<sub>w</sub>), treibt mit der Strömung und mit dem Wind auf
 * dem Teil über Wasser (Widerstand quer und längs an drei Stellen des Stamms, daraus auch die Drehung), stößt an andere
 * (Kapseln, Stoßimpulse mit Reibung, so verkeilen sie sich zu Teppichen), läuft auf Grund, wo das Wasser flacher
 * ist als sein Tiefgang, und saugt sich über Monate voll, bis er sinkt. Der Widerstand wird halb implizit gerechnet, so
 * reißt auch eine Welle mit 30 m/s die Stämme ohne winzige Schritte mit.
 * <p>
 * Ohne Wellen gilt im Gleichgewicht: Ein Stamm treibt mit der Strömung plus α/(1 + α) des Windes,
 * α = √(ρ<sub>a</sub> f / (ρ<sub>w</sub> t)) mit Freibord f und Tiefgang t (Holz 450 kg/m³: 3,5 %). Für weit draußen und
 * für Tage gibt es den Holzteppich als Feld ({@link Carpet}); {@link #toCarpet} gibt Stämme an ihn ab.
 * <p>
 * Das Package kennt keine App und kein anderes Package: Wasser und Wind kommen über {@link Water} und {@link Wind}.
 */
public final class Debris {
    /** Das Wasser: Tiefe (m; 0 oder NaN: Land) und Strömung {u Ost, w Süd} (m/s). */
    public interface Water {
        double depth(double x, double z);

        double[] current(double x, double z);
    }

    /** Wind über dem Wasser {u, w} (m/s). */
    @FunctionalInterface public interface Wind { double[] at(double x, double z, double t); }

    /** Art: Dichte frisch (kg/m³), Fläche gegen Wind und Wasser im Verhältnis zum bloßen Zylinder [G]. */
    public enum Kind {
        /** entrindeter Stamm, wie im Band 1958 */
        TRUNK(450, 1, 1),
        /** Stamm mit Krone: Äste fangen Wind und Wasser */
        CROWN(500, 3, 1.6),
        /** Ast */
        BRANCH(450, 1, 1),
        /** Eisbrocken (Kalbeis), als Walze mit Länge = Durchmesser */
        ICE(917, 1, 1);

        public final double density, airArea, waterArea;

        Kind(double density, double airArea, double waterArea) { this.density = density; this.airArea = airArea; this.waterArea = waterArea; }
    }

    public enum State { FLOATING, GROUNDED, ASHORE, SUNK }

    /** Ein Stück Treibgut. Ort (x, z), Achse unter dem Winkel yaw (rad, von Ost gegen Süd), Geschwindigkeit, Drehrate. */
    public static final class Log {
        public final Kind kind;
        public final double length, diameter;
        public double x, z, yaw, vx, vz, spin;
        public double density;
        /** Zeit im Wasser (s), Alter seit dem Ereignis (s; für das Vergrauen). */
        public double wetTime, age;
        public State state = State.FLOATING;
        public final int id;

        Log(int id, Kind kind, double length, double diameter, double x, double z, double yaw) {
            this.id = id;
            this.kind = kind;
            this.length = length;
            this.diameter = diameter;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.density = kind.density;
        }

        public double mass() { return density * Math.PI * diameter * diameter / 4 * length; }

        /** Tiefgang (m) für die Dichte ρ in Wasser der Dichte ρ<sub>w</sub>. */
        public double draft(double rhoWater) { return Debris.draft(Math.min(1, density / rhoWater), diameter); }

        /** Grau des Holzes 0 (frisch) bis 1 (silbergrau) nach dem Alter [G: halbe Strecke nach einem Jahr]. */
        public double gray() { return kind == Kind.ICE ? 0 : 1 - Math.exp(-age / (365.25 * 86400) * Math.log(2)); }

        /** Enden {xa, za, xb, zb}. */
        public double[] ends() {
            double c = Math.cos(yaw) * length / 2, s = Math.sin(yaw) * length / 2;
            return new double[]{x - c, z - s, x + c, z + s};
        }
    }

    public double rhoWater = 1000, rhoAir = 1.2, gravity = 9.81;
    /** Widerstand quer (Zylinder), längs (Anteil davon) [G], Haftreibung am Grund, Reibung Holz auf Holz, Stoßzahl [G]. */
    public double cNormal = 1.0, cAxial = 0.1, groundFriction = 0.6, contactFriction = 0.5, restitution = 0.2;
    /** Vollsaugen: Sättigungsdichte (kg/m³) und Zeitkonstante (s) [G]. */
    public double soakDensity = 1100, soakTime = 150 * 86400.0;
    /** Längster Teilschritt (s). */
    public double maxSub = 0.05;
    /** Stöße rechnen (aus nur zum Vergleich). */
    public boolean contactsOn = true;
    public Water water;
    public Wind wind = (x, z, t) -> new double[2];
    public String name = "Treibgut";

    final List<Log> logs = new ArrayList<>();
    double time;
    long steps, contacts;
    double busyMs;
    int nextId;

    public Debris(Water water) { this.water = water; }

    public Log add(Kind k, double length, double diameter, double x, double z, double yaw) {
        Log l = new Log(nextId++, k, length, diameter, x, z, yaw);
        logs.add(l);
        return l;
    }

    /** n Stämme zufällig in einem Rechteck (Länge 10–30 m, Durchmesser 0,3–0,8 m, Sitka-Fichte und Hemlock) [G]. */
    public void scatter(int n, double xa, double za, double xb, double zb, Kind k, long seed) {
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            double len = k == Kind.ICE ? 2 + 4 * r.nextDouble() : k == Kind.BRANCH ? 2 + 4 * r.nextDouble() : 10 + 20 * r.nextDouble();
            double d = k == Kind.ICE ? len : k == Kind.BRANCH ? 0.1 + 0.15 * r.nextDouble() : 0.3 + 0.5 * r.nextDouble();
            add(k, len, d, xa + (xb - xa) * r.nextDouble(), za + (zb - za) * r.nextDouble(), Math.PI * r.nextDouble());
        }
    }

    /** Tiefgang eines liegenden Zylinders mit Durchmesser d, dessen Querschnitt zum Anteil f eingetaucht ist (Kreisabschnitt). */
    public static double draft(double f, double d) {
        if (f >= 1) return d;
        if (f <= 0) return 0;
        // Fläche des Abschnitts mit Mittelpunktswinkel θ: (θ − sin θ)/(2π) des Kreises
        double lo = 0, hi = 2 * Math.PI;
        for (int i = 0; i < 80; i++) { double m = 0.5 * (lo + hi); if ((m - Math.sin(m)) / (2 * Math.PI) < f) lo = m; else hi = m; }
        double th = 0.5 * (lo + hi);
        return d / 2 * (1 - Math.cos(th / 2));
    }

    /** Windanteil α = √(ρ<sub>a</sub> f · A<sub>Luft</sub> / (ρ<sub>w</sub> t · A<sub>Wasser</sub>)); Drift im Gleichgewicht α/(1 + α) des Windes. */
    public double windage(Log l) {
        double t = l.draft(rhoWater), f = l.diameter - t;
        return Math.sqrt(rhoAir * f * l.kind.airArea / (rhoWater * t * l.kind.waterArea));
    }

    // ------------------------------------------------------------------ Rechnung

    /** Bis zur Zeit tEnd in Teilschritten. */
    public synchronized void run(double tEnd) { while (time < tEnd - 1e-12) step(Math.min(tEnd - time, 1.0)); }

    /** Ein Schritt dt, in Teilschritte zu höchstens {@link #maxSub} geteilt. */
    public synchronized void step(double dt) {
        long t0 = System.nanoTime();
        int n = Math.max(1, (int) Math.ceil(dt / maxSub));
        double h = dt / n;
        for (int s = 0; s < n; s++) sub(h);
        busyMs += (System.nanoTime() - t0) / 1e6;
    }

    void sub(double dt) {
        for (Log l : logs) move(l, 0, 0, 0, dt);
        if (contactsOn) collide();
        time += dt;
        steps++;
    }

    static final double[] SEG = {-1.0 / 3, 0, 1.0 / 3};

    void move(Log l, double fx, double fz, double tq, double dt) {
        l.age += dt;
        if (l.state == State.SUNK) return;
        double ex = Math.cos(l.yaw), ez = Math.sin(l.yaw), nxv = -ez, nzv = ex;
        double depth = depth(l.x, l.z);
        boolean wet = depth > 0;
        if (wet) {
            l.wetTime += dt;
            l.density = l.kind == Kind.ICE ? l.kind.density : soakDensity - (soakDensity - l.kind.density) * Math.exp(-l.wetTime / soakTime);
            if (l.density >= rhoWater) { l.state = State.SUNK; l.vx = l.vz = l.spin = 0; return; }
        }
        double draft = l.draft(rhoWater), m = l.mass();
        double sub = wet ? Math.min(depth, draft) : 0, air = l.diameter - sub;
        double mEff = m + 0.5 * rhoWater * Math.PI * l.diameter * l.diameter / 4 * l.length * (sub / l.diameter);
        double inertia = mEff * l.length * l.length / 12;
        // halb implizit: (m I + dt ΣK) v′ = m v + dt (Σ K (c − ω×r) + F)
        double a11 = mEff, a12 = 0, a22 = mEff, b1 = mEff * l.vx + dt * fx, b2 = mEff * l.vz + dt * fz;
        double[] cw = new double[6], wd = new double[6];
        double kn = 0, tqc = 0;
        double seg = l.length / 3;
        double[] wn = wind.at(l.x, l.z, time);
        for (int i = 0; i < 3; i++) {
            double r = SEG[i] * l.length, px = l.x + ex * r, pz = l.z + ez * r;
            double[] c = wet ? water.current(px, pz) : new double[2];
            double vpx = l.vx + (-l.spin * r * ez), vpz = l.vz + (l.spin * r * ex);
            // Wasser
            double dwx = c[0] - vpx, dwz = c[1] - vpz, dwn = dwx * nxv + dwz * nzv, dwa = dwx * ex + dwz * ez;
            double kwn = 0.5 * rhoWater * cNormal * sub * seg * l.kind.waterArea * Math.abs(dwn), kwa = cAxial * 0.5 * rhoWater * sub * seg * l.kind.waterArea * Math.abs(dwa);
            // Luft
            double dax = wn[0] - vpx, daz = wn[1] - vpz, dan = dax * nxv + daz * nzv, daa = dax * ex + daz * ez;
            double kan = 0.5 * rhoAir * cNormal * air * seg * l.kind.airArea * Math.abs(dan), kaa = cAxial * 0.5 * rhoAir * air * seg * l.kind.airArea * Math.abs(daa);
            double knI = kwn + kan, kaI = kwa + kaa;
            // K = knI n nᵀ + kaI e eᵀ
            double k11 = knI * nxv * nxv + kaI * ex * ex, k12 = knI * nxv * nzv + kaI * ex * ez, k22 = knI * nzv * nzv + kaI * ez * ez;
            a11 += dt * k11; a12 += dt * k12; a22 += dt * k22;
            // Zielgeschwindigkeit je Teil: gewichtetes Mittel aus Wasser und Luft, ohne den Drehanteil
            double tnx = (kwn * c[0] + kan * wn[0]), tnz = (kwn * c[1] + kan * wn[1]);
            double tax = (kwa * c[0] + kaa * wn[0]), taz = (kwa * c[1] + kaa * wn[1]);
            double sn = (tnx * nxv + tnz * nzv), sa = (tax * ex + taz * ez);
            double rx = -l.spin * r * ez, rz = l.spin * r * ex; // ω × r
            double rn = rx * nxv + rz * nzv;
            b1 += dt * (sn * nxv + sa * ex - knI * rn * nxv);
            b2 += dt * (sn * nzv + sa * ez - knI * rn * nzv);
            cw[2 * i] = sn; cw[2 * i + 1] = knI;
            kn += knI * r * r;
            wd[2 * i] = r;
        }
        // Grund: Reibung, wo das Wasser flacher ist als der Tiefgang, an Land voll
        double load = wet ? Math.max(0, 1 - depth / Math.max(draft, 1e-6)) : 1;
        double kg = 0;
        if (load > 0) {
            double sp = Math.hypot(l.vx, l.vz);
            kg = groundFriction * load * m * gravity / Math.max(sp, 0.05);
            a11 += dt * kg; a22 += dt * kg;
        }
        double det = a11 * a22 - a12 * a12;
        double vx = (a22 * b1 - a12 * b2) / det, vz = (a11 * b2 - a12 * b1) / det;
        // Drehung: (I + dt Σ kn r² + dt kg L²/12) ω′ = I ω + dt (Σ r · kn (Ziel_n − v′_n) + τ)
        double vn = vx * nxv + vz * nzv;
        for (int i = 0; i < 3; i++) tqc += wd[2 * i] * (cw[2 * i] - cw[2 * i + 1] * vn);
        double spin = (inertia * l.spin + dt * (tqc + tq)) / (inertia + dt * kn + dt * kg * l.length * l.length / 12);
        l.vx = vx;
        l.vz = vz;
        l.spin = spin;
        l.x += dt * vx;
        l.z += dt * vz;
        l.yaw += dt * spin;
        l.state = !wet ? State.ASHORE : load > 0 ? State.GROUNDED : State.FLOATING;
    }

    double depth(double x, double z) {
        double d = water == null ? Double.NaN : water.depth(x, z);
        return Double.isNaN(d) ? 0 : d;
    }

    /** Stöße nach jedem Teilschritt: Kapseln (Strecke mit Halbmesser); Stoßimpulse längs der Normalen (Stoßzahl
     * {@link #restitution}) mit Reibung nach Coulomb quer, dazu die Lage auseinandergeschoben (nach Masse). Vier Durchgänge,
     * Nachbarn über ein Gitter. Impuls bleibt genau, weil jeder Stoß beiden gleich und entgegengesetzt gilt. */
    void collide() {
        int n = logs.size();
        if (n < 2) return;
        double cell = 0;
        for (Log l : logs) cell = Math.max(cell, l.length + l.diameter);
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int k = 0; k < n; k++) {
            Log l = logs.get(k);
            if (l.state == State.SUNK) continue;
            grid.computeIfAbsent(key((int) Math.floor(l.x / cell), (int) Math.floor(l.z / cell)), x -> new ArrayList<>()).add(k);
        }
        List<int[]> pairs = new ArrayList<>();
        for (int a = 0; a < n; a++) {
            Log la = logs.get(a);
            if (la.state == State.SUNK) continue;
            int ci = (int) Math.floor(la.x / cell), cj = (int) Math.floor(la.z / cell);
            for (int dj = -1; dj <= 1; dj++)
                for (int di = -1; di <= 1; di++) {
                    List<Integer> lst = grid.get(key(ci + di, cj + dj));
                    if (lst != null) for (int b : lst) if (b > a) pairs.add(new int[]{a, b});
                }
        }
        pairs.sort((p, q) -> p[0] != q[0] ? Integer.compare(p[0], q[0]) : Integer.compare(p[1], q[1]));
        for (int it = 0; it < 4; it++)
            for (int[] pr : pairs) {
                Log la = logs.get(pr[0]), lb = logs.get(pr[1]);
                double[] p = closest(la.ends(), lb.ends());
                double dxp = p[2] - p[0], dzp = p[3] - p[1], dist = Math.hypot(dxp, dzp), rr = (la.diameter + lb.diameter) / 2;
                if (dist >= rr) continue;
                if (it == 0) contacts++;
                double nxp, nzp;
                if (dist < 1e-9) { nxp = -Math.sin(la.yaw); nzp = Math.cos(la.yaw); } else { nxp = dxp / dist; nzp = dzp / dist; }
                double ma = la.mass(), mb = lb.mass(), ia = ma * la.length * la.length / 12, ib = mb * lb.length * lb.length / 12;
                double rax = p[0] - la.x, raz = p[1] - la.z, rbx = p[2] - lb.x, rbz = p[3] - lb.z;
                double vax = la.vx - la.spin * raz, vaz = la.vz + la.spin * rax, vbx = lb.vx - lb.spin * rbz, vbz = lb.vz + lb.spin * rbx;
                double rvx = vbx - vax, rvz = vbz - vaz, rvn = rvx * nxp + rvz * nzp;
                double can = rax * nzp - raz * nxp, cbn = rbx * nzp - rbz * nxp;
                if (rvn < 0) {
                    double jn = -(1 + restitution) * rvn / (1 / ma + 1 / mb + can * can / ia + cbn * cbn / ib);
                    double tx = -nzp, tz = nxp, rvt = rvx * tx + rvz * tz;
                    double cat = rax * tz - raz * tx, cbt = rbx * tz - rbz * tx;
                    double jt = -rvt / (1 / ma + 1 / mb + cat * cat / ia + cbt * cbt / ib);
                    jt = Math.max(-contactFriction * jn, Math.min(contactFriction * jn, jt));
                    double gx = jn * nxp + jt * tx, gz = jn * nzp + jt * tz;
                    lb.vx += gx / mb; lb.vz += gz / mb; lb.spin += (rbx * gz - rbz * gx) / ib;
                    la.vx -= gx / ma; la.vz -= gz / ma; la.spin -= (rax * gz - raz * gx) / ia;
                }
                // auseinanderschieben, nach Masse
                double pen = rr - Math.max(dist, 0), wa = mb / (ma + mb), wb = ma / (ma + mb), m = 0.8 * pen;
                la.x -= nxp * m * wa; la.z -= nzp * m * wa;
                lb.x += nxp * m * wb; lb.z += nzp * m * wb;
            }
    }

    static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    /** Nächste Punkte zweier Strecken {pax, paz, pbx, pbz}. */
    static double[] closest(double[] a, double[] b) {
        double d1x = a[2] - a[0], d1z = a[3] - a[1], d2x = b[2] - b[0], d2z = b[3] - b[1], rx = a[0] - b[0], rz = a[1] - b[1];
        double aa = d1x * d1x + d1z * d1z, ee = d2x * d2x + d2z * d2z, f = d2x * rx + d2z * rz, s, t;
        double c = d1x * rx + d1z * rz, bb = d1x * d2x + d1z * d2z, den = aa * ee - bb * bb;
        s = den > 1e-12 ? clamp((bb * f - c * ee) / den) : 0;
        t = (bb * s + f) / ee;
        if (t < 0) { t = 0; s = clamp(-c / aa); } else if (t > 1) { t = 1; s = clamp((bb - c) / aa); }
        return new double[]{a[0] + d1x * s, a[1] + d1z * s, b[0] + d2x * t, b[1] + d2z * t};
    }

    static double clamp(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    // ------------------------------------------------------------------ Teppich

    /** Gibt alle schwimmenden Stücke, auf die test zutrifft, an den Teppich ab (ihre Fläche von oben); gibt die Zahl zurück. */
    public synchronized int toCarpet(Carpet c, Predicate<Log> test) {
        int n = 0;
        for (int i = logs.size() - 1; i >= 0; i--) {
            Log l = logs.get(i);
            if (l.state == State.SUNK || !test.test(l)) continue;
            c.addArea(l.x, l.z, l.length * l.diameter);
            logs.remove(i);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ Abfragen

    public List<Log> logs() { return Collections.unmodifiableList(logs); }

    public double time() { return time; }

    public long steps() { return steps; }

    public long contacts() { return contacts; }

    public double busyMs() { return busyMs; }

    /** Zahl je Zustand {schwimmt, auf Grund, an Land, gesunken}. */
    public int[] census() {
        int[] c = new int[4];
        for (Log l : logs) c[l.state.ordinal()]++;
        return c;
    }

    /** Mittlere Geschwindigkeit der schwimmenden {u, w}. */
    public double[] meanVelocity() {
        double sx = 0, sz = 0;
        int n = 0;
        for (Log l : logs) if (l.state == State.FLOATING) { sx += l.vx; sz += l.vz; n++; }
        return n == 0 ? new double[2] : new double[]{sx / n, sz / n};
    }

    /** Fläche der konvexen Hülle aller nicht gesunkenen Stücke (Mittelpunkte, m²) – Maß für den Zusammenhalt eines Teppichs. */
    public double hullArea() {
        List<double[]> p = new ArrayList<>();
        for (Log l : logs) if (l.state != State.SUNK) { double[] e = l.ends(); p.add(new double[]{e[0], e[1]}); p.add(new double[]{e[2], e[3]}); }
        if (p.size() < 3) return 0;
        p.sort((u, v) -> u[0] != v[0] ? Double.compare(u[0], v[0]) : Double.compare(u[1], v[1]));
        double[][] h = new double[2 * p.size()][];
        int k = 0;
        for (double[] q : p) { while (k >= 2 && cross(h[k - 2], h[k - 1], q) <= 0) k--; h[k++] = q; }
        for (int i = p.size() - 2, t = k + 1; i >= 0; i--) { double[] q = p.get(i); while (k >= t && cross(h[k - 2], h[k - 1], q) <= 0) k--; h[k++] = q; }
        double a = 0;
        for (int i = 0; i < k - 1; i++) a += h[i][0] * h[i + 1][1] - h[i + 1][0] * h[i][1];
        return Math.abs(a) / 2;
    }

    static double cross(double[] o, double[] a, double[] b) { return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]); }

    /** Größte Überlappung zweier Stücke im Verhältnis zum Durchmesser. */
    public double worstOverlap() {
        double w = 0;
        for (int a = 0; a < logs.size(); a++)
            for (int b = a + 1; b < logs.size(); b++) {
                Log la = logs.get(a), lb = logs.get(b);
                if (la.state == State.SUNK || lb.state == State.SUNK) continue;
                double[] p = closest(la.ends(), lb.ends());
                double rr = (la.diameter + lb.diameter) / 2, d = Math.hypot(p[2] - p[0], p[3] - p[1]);
                if (d < rr) w = Math.max(w, (rr - d) / Math.min(la.diameter, lb.diameter));
            }
        return w;
    }

    public String describe() {
        int[] c = census();
        return String.format(Locale.GERMAN, "%s: %d Stücke (%d schwimmen, %d auf Grund, %d an Land, %d gesunken)", name, logs.size(), c[0], c[1], c[2], c[3]);
    }
}
