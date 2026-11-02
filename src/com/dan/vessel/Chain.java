package com.dan.vessel;

import java.util.Locale;

/**
 * Eine Ankerkette aus Massenpunkten (LituyaBay1958 Phase 15, Werkbuch 5.9): Glieder zu je {@link #segment} m, verbunden
 * durch steife, leicht dehnbare Abstände (XPBD mit kleinen Teilschritten, Nachgiebigkeit l / EA), Gewicht unter Wasser,
 * Widerstand quer und längs gegen die Strömung, Grund mit Reibung. Am einen Ende die Klüse des Boots, am anderen der Anker.
 * <ul>
 * <li><b>Zug</b> je Glied aus dem Lagrange-Faktor der Bedingung (T = λ / Δt²); die Kraft am Bug geht ins Boot.</li>
 * <li><b>Reißen:</b> übersteigt der Zug eines Glieds die Bruchlast {@link #breakingLoad}, trennt die Kette dort: das
 * Stück am Boot hängt weiter, der Rest fällt.</li>
 * <li><b>Anker:</b> hält bis {@link #holding} waagerecht am Anker, dann slippt er über den Grund (Reibung); festgehakt
 * ({@link #fouled}) hält er alles – wie der Anker der Edrie, der zwischen Steinen saß.</li>
 * <li><b>Kettenlinie:</b> In Ruhe liegt die Kette wie die Kettenlinie mit Grundberührung ({@link #catenaryTension});
 * die Prüfung vergleicht beide.</li>
 * </ul>
 * Welt: x Ost, y oben, z Süd.
 */
public final class Chain {
    public static final double G = 9.81;
    /** Länge, Gliedlänge (m), Masse je m in Luft (kg/m), Nenndurchmesser (m), Dehnsteifigkeit EA (N), Bruchlast (N). */
    public final double length, segment, massPerMeter, diameter, ea;
    public double breakingLoad;
    /** Erst nach so vielen Sekunden kann die Kette reißen (das Anlegen ruckt) [s]. */
    public double armAfter = 5;
    /** Haltekraft des Ankers waagerecht (N), Masse des Ankers (kg); festgehakt: hält alles. */
    public double holding, anchorMass = 35;
    public boolean fouled;
    /** Dichte von Stahl und Wasser (kg/m³), Widerstandsbeiwerte quer und längs, Reibung auf dem Grund [G]. */
    public double rhoSteel = 7850, rhoWater = 1025, cdNormal = 2.4, cdAxial = 1.15, friction = 0.7;

    final int n;           // Knoten 0 … n (0 an der Klüse, n am Anker)
    final double[] x, y, z, px, py, pz, vx, vy, vz, im, lambda, tension;
    final boolean[] cut;
    boolean attached = true, dragging;
    double peak, time, brokenTime = Double.NaN;
    int brokenAt = -1;
    private final double nodeMass, wet;

    /**
     * Eine Kette der Länge length (m) aus Gliedern zu etwa segment m, Masse je m, Nenndurchmesser, Bruchlast; Dehnsteifigkeit
     * EA aus dem Durchmesser (Stahlkette, wirksam rund 0,85 · 2 · π d²/4 · 210 GPa [G]).
     */
    public Chain(double length, double segment, double massPerMeter, double diameter, double breakingLoad) {
        this.n = Math.max(2, (int) Math.round(length / segment));
        this.length = length;
        this.segment = length / n;
        this.massPerMeter = massPerMeter;
        this.diameter = diameter;
        this.breakingLoad = breakingLoad;
        this.ea = 0.85 * 2 * Math.PI * diameter * diameter / 4 * 210e9;
        this.holding = 2e4;
        int m = n + 1;
        x = new double[m]; y = new double[m]; z = new double[m]; px = new double[m]; py = new double[m]; pz = new double[m];
        vx = new double[m]; vy = new double[m]; vz = new double[m]; im = new double[m];
        lambda = new double[n]; tension = new double[n]; cut = new boolean[n];
        nodeMass = massPerMeter * this.segment;
        wet = 1 - rhoWater / rhoSteel;
        for (int i = 0; i < m; i++) im[i] = 1 / (nodeMass * (i == 0 || i == n ? 0.5 : 1));
        im[n] = 1 / (anchorMass + 0.5 * nodeMass);
    }

    /** Eine Stahlkette nach Nenndurchmesser d (m): Masse 2 · 10⁴ d² kg/m (10 mm: 2 kg/m), Bruchlast 4 × Arbeitslast (Güte 30, „proof coil“) [G]. */
    public static Chain proofCoil(double length, double d) {
        double mpm = 2.0e4 * d * d;            // 10 mm: 2,0 kg/m
        double mbl = 4.7e8 * d * d;            // 10 mm: 47 kN
        return new Chain(length, 2, mpm, d, mbl);
    }

    /** Legt den Anker bei (ax, az) auf den Grund und die Kette gerade bis zur Klüse an (nur zum Anlegen). */
    public void lay(Water water, double ax, double az, double[] fairlead) {
        // Glieder in voller Länge: vom Anker ein Stück b auf dem Grund zur Klüse hin, dann gerade hinauf (b so, dass die Länge aufgeht)
        double bedA = water.bed(ax, az) + diameter / 2;
        double hx = fairlead[0] - ax, hz = fairlead[2] - az, hd = Math.hypot(hx, hz);
        double ux = hd > 1e-9 ? hx / hd : 1, uz = hd > 1e-9 ? hz / hd : 0;
        double lo = 0, hi = Math.min(length, hd);
        for (int it = 0; it < 80; it++) {
            double b = 0.5 * (lo + hi), dx = hd - b, dy = fairlead[1] - bedA;
            if (b + Math.sqrt(dx * dx + dy * dy) > length) hi = b; else lo = b;
        }
        double b = lo;
        double kx = ax + ux * b, kz = az + uz * b;
        double up = Math.sqrt((fairlead[0] - kx) * (fairlead[0] - kx) + (fairlead[1] - bedA) * (fairlead[1] - bedA) + (fairlead[2] - kz) * (fairlead[2] - kz));
        double scale = (b + up) > length ? 1 : length / Math.max(1e-9, b + up);   // reicht die Kette nicht, wird sie gedehnt angelegt
        for (int i = 0; i <= n; i++) {
            double sArc = (n - i) * segment / scale;                       // vom Anker gemessen
            if (sArc <= b) { x[i] = ax + ux * sArc; z[i] = az + uz * sArc; y[i] = water.bed(x[i], z[i]) + diameter / 2; }
            else {
                double t = Math.min(1, (sArc - b) / Math.max(1e-9, up));
                x[i] = kx + (fairlead[0] - kx) * t; y[i] = bedA + (fairlead[1] - bedA) * t; z[i] = kz + (fairlead[2] - kz) * t;
            }
            vx[i] = vy[i] = vz[i] = 0;
        }
        x[0] = fairlead[0]; y[0] = fairlead[1]; z[0] = fairlead[2];
    }

    void attachTo(double[] f) { x[0] = f[0]; y[0] = f[1]; z[0] = f[2]; }

    /**
     * Ein Teilschritt dt mit der Klüse am Ort f (Welt) und ihrer Geschwindigkeit fv; gibt die Kraft der Kette auf das Boot
     * (Welt, N) zurück, oder null, wenn die Kette nicht mehr am Boot hängt.
     */
    public double[] step(Water water, double dt, double[] f, double[] fv) {
        time += dt;
        int m = n + 1;
        // freie Bewegung: Gewicht unter Wasser, Widerstand, Vorhersage
        for (int i = 0; i < m; i++) {
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
            if (i == 0 && attached) continue;
            if (i == n && (fouled || !dragging) && anchorHolds()) continue;
            double hw = water.height(x[i], z[i]);
            boolean inWater = y[i] < hw;
            double gEff = inWater ? G * wet : G;
            double fxx = 0, fyy = -gEff / im[i] * (i == n ? 1 : 1), fzz = 0;
            if (inWater) {
                // Widerstand je Knoten nach der Richtung des Glieds davor (quer stark, längs schwach)
                int a = Math.max(0, i - 1), b = Math.min(n, i + 1);
                double tx = x[b] - x[a], ty = y[b] - y[a], tz = z[b] - z[a], tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-9;
                tx /= tl; ty /= tl; tz /= tl;
                double[] cur = water.velocity(x[i], z[i]);
                double rx = cur[0] - vx[i], ry = -vy[i], rz = cur[1] - vz[i];
                double ra = rx * tx + ry * ty + rz * tz;
                double nx = rx - ra * tx, ny = ry - ra * ty, nz = rz - ra * tz, ns = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double kn = 0.5 * rhoWater * cdNormal * diameter * segment * ns, ka = 0.5 * rhoWater * cdAxial * Math.PI * diameter * segment * Math.abs(ra) * 0.1;
                double mi = 1 / im[i], lim = 0.8 * mi / dt;
                kn = Math.min(kn, lim); ka = Math.min(ka, lim);
                fxx += kn * nx + ka * ra * tx; fyy += kn * ny + ka * ra * ty; fzz += kn * nz + ka * ra * tz;
            }
            vx[i] += fxx * im[i] * dt; vy[i] += fyy * im[i] * dt; vz[i] += fzz * im[i] * dt;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
        }
        if (attached) { x[0] = f[0]; y[0] = f[1]; z[0] = f[2]; }
        // Abstände (XPBD, eine Runde vom Boot zum Anker und zurück)
        double alpha = segment / ea / (dt * dt);
        for (int k = 0; k < n; k++) lambda[k] = 0;
        for (int pass = 0; pass < 2; pass++)
            for (int kk = 0; kk < n; kk++) {
                int k = pass == 0 ? kk : n - 1 - kk;
                if (cut[k]) continue;
                int a = k, b = k + 1;
                double wa = (a == 0 && attached) ? 0 : im[a], wb = (b == n && anchorFixed()) ? 0 : im[b];
                if (wa + wb == 0) continue;
                double dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (l < 1e-9) continue;
                double c = l - segment;
                if (c < 0) c *= 0.05;           // eine Kette drückt nicht: zusammengeschoben gibt sie fast ganz nach
                double dl = (-c - alpha * lambda[k]) / (wa + wb + alpha);
                lambda[k] += dl;
                double gx = dx / l * dl, gy = dy / l * dl, gz = dz / l * dl;
                x[a] -= wa * gx; y[a] -= wa * gy; z[a] -= wa * gz;
                x[b] += wb * gx; y[b] += wb * gy; z[b] += wb * gz;
            }
        // Grund und Reibung
        for (int i = 0; i < m; i++) {
            if (i == 0 && attached) continue;
            double bed = water.bed(x[i], z[i]) + diameter / 2;
            if (y[i] < bed) {
                y[i] = bed;
                double dx = x[i] - px[i], dz = z[i] - pz[i], d = Math.hypot(dx, dz);
                double hold = friction * G * wet / im[i] * dt * dt * im[i] * 4;   // Weg, den die Reibung in einem Schritt aufhält
                if (d <= hold) { x[i] = px[i]; z[i] = pz[i]; } else { x[i] -= dx / d * hold; z[i] -= dz / d * hold; }
            }
        }
        // Geschwindigkeiten, Zug
        double maxT = 0;
        int maxK = -1;
        for (int i = 0; i < m; i++) { vx[i] = (x[i] - px[i]) / dt; vy[i] = (y[i] - py[i]) / dt; vz[i] = (z[i] - pz[i]) / dt; }
        for (int k = 0; k < n; k++) {
            tension[k] = cut[k] ? 0 : Math.max(0, -lambda[k]) / (dt * dt);
            if (tension[k] > maxT) { maxT = tension[k]; maxK = k; }
        }
        if (time > armAfter) peak = Math.max(peak, maxT);
        // Anker: slippt, wenn der waagerechte Zug am Anker die Haltekraft übersteigt
        if (!fouled && !dragging && brokenAt < 0) {
            double tx = x[n] - x[n - 1], tz = z[n] - z[n - 1], tl = Math.sqrt(tx * tx + (y[n] - y[n - 1]) * (y[n] - y[n - 1]) + tz * tz) + 1e-9;
            if (tension[n - 1] * Math.hypot(tx, tz) / tl > holding) dragging = true;
        }
        // Reißen
        if (maxK >= 0 && maxT > breakingLoad && brokenAt < 0 && time > armAfter) {
            cut[maxK] = true;
            brokenAt = maxK;
            brokenTime = time;
        }
        if (!attached) return null;
        if (cut[0]) { attached = false; return null; }
        double dx = x[1] - x[0], dy = y[1] - y[0], dz = z[1] - z[0], l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-9;
        double t0 = tension[0];
        // dazu das halbe Gewicht des ersten Glieds an der Klüse
        return new double[]{t0 * dx / l, t0 * dy / l - G * wet * 0.5 * nodeMass, t0 * dz / l};
    }

    private boolean anchorHolds() { return true; }

    private boolean anchorFixed() { return fouled || !dragging; }

    /** Löst die Kette vom Boot (geslippt). */
    public void slip() { attached = false; }

    // ------------------------------------------------------------------ Abfragen

    public int nodes() { return n + 1; }

    /** Knoten i in der Welt. */
    public double[] node(int i) { return new double[]{x[i], y[i], z[i]}; }

    /** Zug im Glied k (N, letzter Teilschritt). */
    public double tension(int k) { return tension[k]; }

    /** Zug an der Klüse (N). */
    public double fairleadTension() { return attached ? tension[0] : 0; }

    /** Größter Zug bisher (N). */
    public double peak() { return peak; }

    public boolean broken() { return brokenAt >= 0; }

    /** Glied, an dem die Kette riss (−1: ganz), und wann (s seit dem Anlegen). */
    public int brokenAt() { return brokenAt; }

    public double brokenTime() { return brokenTime; }

    public boolean attached() { return attached; }

    public boolean dragging() { return dragging; }

    /** Gewicht je m unter Wasser (N/m). */
    public double wetWeight() { return massPerMeter * G * wet; }

    /** Länge der Kette, die auf dem Grund liegt (m; Knoten höchstens 5 cm über dem Grund). */
    public double onBed(Water water) {
        double s = 0;
        for (int k = 0; k < n; k++) if (y[k] - water.bed(x[k], z[k]) < 0.05 + diameter && y[k + 1] - water.bed(x[k + 1], z[k + 1]) < 0.05 + diameter) s += segment;
        return s;
    }

    /**
     * Kettenlinie mit Grundberührung (unelastisch): Klüse h über dem Grund, waagerechter Abstand X zum Anker, Länge L,
     * Gewicht w je m. Gibt {waagerechter Zug H, hängende Länge s, Zug an der Klüse T} oder null, wenn die Kette nicht
     * reicht (straff). s = √(h² + 2 h a), X = L − s + a·asinh(s / a), a = H / w, T = H + w h.
     */
    public static double[] catenaryTension(double h, double X, double L, double w) {
        double lo = 1e-3, hi = 1e9;
        if (X >= Math.sqrt(Math.max(0, L * L - h * h)) - 1e-9) return null;
        for (int it = 0; it < 200; it++) {
            double a = Math.sqrt(lo * hi), s = Math.sqrt(h * h + 2 * h * a);
            if (s > L) { hi = a; continue; }
            double xx = L - s + a * asinh(s / a);
            if (xx > X) hi = a; else lo = a;
        }
        double a = Math.sqrt(lo * hi), s = Math.sqrt(h * h + 2 * h * a);
        return new double[]{a * w, s, a * w + w * h};
    }

    static double asinh(double v) { return Math.log(v + Math.sqrt(v * v + 1)); }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "Kette %.0f m (%d Glieder zu %.2f m), %.1f kg/m, Bruchlast %.0f kN, Zug höchstens %.1f kN%s%s",
                length, n, segment, massPerMeter, breakingLoad / 1000, peak / 1000, broken() ? String.format(Locale.GERMAN, ", gerissen am Glied %d nach %.1f s", brokenAt, brokenTime) : "",
                fouled ? ", Anker festgehakt" : dragging ? ", Anker slippt" : "");
    }
}
