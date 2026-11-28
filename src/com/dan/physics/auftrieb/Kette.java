package com.dan.physics.auftrieb;

import java.util.Locale;

/**
 * Eine Kette aus Massenpunkten (Seegang Werkbuch, S7a; Kopie von `vessel.Chain` aus LituyaBay1958 Phase 15 nach SF3 A,
 * auf die Achsen und das {@link Wasser} dieses Packages umgestellt): Glieder zu rund {@link #segment} m, steife, leicht
 * dehnbare Abstände (XPBD mit kleinen Teilschritten, Nachgiebigkeit l / EA), Gewicht unter Wasser, Widerstand quer und
 * längs gegen Strom und Welle, Grund mit Reibung. Am einen Ende die Klüse (Tonne: das Auge am Fuß), am anderen der Anker
 * (Tonne: der Stein).
 * <ul>
 * <li><b>Zug</b> je Glied aus dem Lagrange-Faktor (T = λ / Δt²); die Kraft an der Klüse geht in den Körper.</li>
 * <li><b>Anker</b> hält bis {@link #haltekraft} waagerecht, dann slippt er; ein Stein ({@link #fest}) hält alles.</li>
 * <li><b>Kettenlinie</b> mit Grundberührung zum Vergleich: {@link #kettenlinie}.</li>
 * </ul>
 * Welt: x Ost, y Nord, z oben.
 */
public final class Kette implements Festmacher {
    public static final double G = 9.81;
    /** Länge, Gliedlänge (m), Masse je m in Luft (kg/m), Nenndurchmesser (m), Dehnsteifigkeit EA (N). */
    public final double laenge, segment, masseJeMeter, durchmesser, ea;
    /** Haltekraft des Ankers waagerecht (N), Masse des Ankers (kg); fest: hält alles (Stein der Tonne). */
    public double haltekraft = 2e4, ankerMasse = 35;
    public boolean fest;
    /** Dichte von Stahl, Widerstandsbeiwerte quer und längs, Reibung auf dem Grund [G]. */
    public double dichteStahl = 7850, cdQuer = 2.4, cdLaengs = 1.15, reibung = 0.7;
    /** Wo die Kette am Körper hängt (Achsen des Körpers). */
    public final double[] klause;

    final int n;
    final double[] x, y, z, px, py, pz, vx, vy, vz, im, lambda, zug;
    boolean slippt;
    double spitze;
    private final double knotenMasse;
    private final double[] buf = new double[6], st = new double[2];

    /** Eine Kette der Länge laenge aus Gliedern zu etwa segment m; EA aus dem Durchmesser (0,85 · 2 · π d²/4 · 210 GPa [G]). */
    public Kette(double laenge, double segment, double masseJeMeter, double durchmesser, double[] klause) {
        this.n = Math.max(2, (int) Math.round(laenge / segment));
        this.laenge = laenge;
        this.segment = laenge / n;
        this.masseJeMeter = masseJeMeter;
        this.durchmesser = durchmesser;
        this.ea = 0.85 * 2 * Math.PI * durchmesser * durchmesser / 4 * 210e9;
        this.klause = klause.clone();
        int m = n + 1;
        x = new double[m]; y = new double[m]; z = new double[m]; px = new double[m]; py = new double[m]; pz = new double[m];
        vx = new double[m]; vy = new double[m]; vz = new double[m]; im = new double[m];
        lambda = new double[n]; zug = new double[n];
        knotenMasse = masseJeMeter * this.segment;
        for (int i = 0; i < m; i++) im[i] = 1 / (knotenMasse * (i == 0 || i == n ? 0.5 : 1));
        im[n] = 1 / (ankerMasse + 0.5 * knotenMasse);
    }

    /** Stahlkette nach Nenndurchmesser d (m): 2 · 10⁴ d² kg/m (16 mm: 5,1 kg/m) [G], Glieder zu rund 0,5 m (Prüfstein 9). */
    public static Kette stahl(double laenge, double d, double[] klause) { return new Kette(laenge, 0.5, 2.0e4 * d * d, d, klause); }

    double nass() { return 1 - 1025 / dichteStahl; }

    /** Legt den Anker bei (ax, ay) auf den Grund und die Kette vom Anker über den Grund gerade zur Klüse (Welt). */
    public void legen(Wasser w, double ax, double ay, double[] f) {
        double gA = w.grund(ax, ay) + durchmesser / 2;
        double hx = f[0] - ax, hy = f[1] - ay, hd = Math.hypot(hx, hy);
        double ux = hd > 1e-9 ? hx / hd : 1, uy = hd > 1e-9 ? hy / hd : 0;
        double lo = 0, hi = Math.min(laenge, hd);
        for (int it = 0; it < 80; it++) {
            double b = 0.5 * (lo + hi), dx = hd - b, dz = f[2] - gA;
            if (b + Math.sqrt(dx * dx + dz * dz) > laenge) hi = b; else lo = b;
        }
        double b = lo, kx = ax + ux * b, ky = ay + uy * b;
        double hoch = Math.sqrt((f[0] - kx) * (f[0] - kx) + (f[1] - ky) * (f[1] - ky) + (f[2] - gA) * (f[2] - gA));
        double sc = (b + hoch) > laenge ? 1 : laenge / Math.max(1e-9, b + hoch);
        for (int i = 0; i <= n; i++) {
            double s = (n - i) * segment / sc;
            if (s <= b) { x[i] = ax + ux * s; y[i] = ay + uy * s; z[i] = w.grund(x[i], y[i]) + durchmesser / 2; }
            else {
                double t = Math.min(1, (s - b) / Math.max(1e-9, hoch));
                x[i] = kx + (f[0] - kx) * t; y[i] = ky + (f[1] - ky) * t; z[i] = gA + (f[2] - gA) * t;
            }
            vx[i] = vy[i] = vz[i] = 0;
        }
        x[0] = f[0]; y[0] = f[1]; z[0] = f[2];
    }

    @Override public boolean kraft(Koerper k, Wasser w, double t, double dt, double[] f, double[] punkt) {
        double[] p = k.punktWelt(klause[0], klause[1], klause[2]);
        punkt[0] = p[0]; punkt[1] = p[1]; punkt[2] = p[2];
        double[] r = schritt(w, t, dt, p);
        f[0] = r[0]; f[1] = r[1]; f[2] = r[2];
        return true;
    }

    /** Ein Teilschritt dt mit der Klüse am Ort fl (Welt); gibt die Kraft der Kette auf den Körper (Welt, N). */
    public double[] schritt(Wasser w, double t, double dt, double[] fl) {
        int m = n + 1;
        double wet = nass();
        for (int i = 0; i < m; i++) {
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
            if (i == 0) continue;
            if (i == n && !slippt) continue;
            double pg = w.pegel(x[i], y[i], t);
            w.welle(x[i], y[i], Math.min(0, z[i] - pg), t, buf);
            boolean imWasser = z[i] < pg + buf[0];
            double gEff = imWasser ? G * wet : G;
            double fx = 0, fy = 0, fz = -gEff / im[i];
            if (imWasser) {
                int a = Math.max(0, i - 1), b = Math.min(n, i + 1);
                double tx = x[b] - x[a], ty = y[b] - y[a], tz = z[b] - z[a], tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-9;
                tx /= tl; ty /= tl; tz /= tl;
                w.strom(x[i], y[i], t, st);
                double rx = st[0] + buf[3] - vx[i], ry = st[1] + buf[4] - vy[i], rz = buf[5] - vz[i];
                double ra = rx * tx + ry * ty + rz * tz;
                double nx = rx - ra * tx, ny = ry - ra * ty, nz = rz - ra * tz, ns = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double kn = 0.5 * 1025 * cdQuer * durchmesser * segment * ns, ka = 0.5 * 1025 * cdLaengs * Math.PI * durchmesser * segment * Math.abs(ra) * 0.1;
                double mi = 1 / im[i], lim = 0.8 * mi / dt;
                kn = Math.min(kn, lim); ka = Math.min(ka, lim);
                fx += kn * nx + ka * ra * tx; fy += kn * ny + ka * ra * ty; fz += kn * nz + ka * ra * tz;
            }
            vx[i] += fx * im[i] * dt; vy[i] += fy * im[i] * dt; vz[i] += fz * im[i] * dt;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
        }
        x[0] = fl[0]; y[0] = fl[1]; z[0] = fl[2];
        double alpha = segment / ea / (dt * dt);
        for (int k = 0; k < n; k++) lambda[k] = 0;
        for (int pass = 0; pass < 2; pass++)
            for (int kk = 0; kk < n; kk++) {
                int k = pass == 0 ? kk : n - 1 - kk, a = k, b = k + 1;
                double wa = a == 0 ? 0 : im[a], wb = (b == n && !slippt) ? 0 : im[b];
                if (wa + wb == 0) continue;
                double dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (l < 1e-9) continue;
                double c = l - segment;
                if (c < 0) c *= 0.05;                         // eine Kette drückt nicht
                double dl = (-c - alpha * lambda[k]) / (wa + wb + alpha);
                lambda[k] += dl;
                double gx = dx / l * dl, gy = dy / l * dl, gz = dz / l * dl;
                x[a] -= wa * gx; y[a] -= wa * gy; z[a] -= wa * gz;
                x[b] += wb * gx; y[b] += wb * gy; z[b] += wb * gz;
            }
        for (int i = 1; i < m; i++) {
            double gr = w.grund(x[i], y[i]) + durchmesser / 2;
            if (z[i] < gr) {
                z[i] = gr;
                double dx = x[i] - px[i], dy = y[i] - py[i], d = Math.hypot(dx, dy);
                double halt = reibung * G * wet * dt * dt * 4;
                if (d <= halt) { x[i] = px[i]; y[i] = py[i]; } else { x[i] -= dx / d * halt; y[i] -= dy / d * halt; }
            }
        }
        for (int i = 0; i < m; i++) { vx[i] = (x[i] - px[i]) / dt; vy[i] = (y[i] - py[i]) / dt; vz[i] = (z[i] - pz[i]) / dt; }
        double maxT = 0;
        for (int k = 0; k < n; k++) { zug[k] = Math.max(0, -lambda[k]) / (dt * dt); maxT = Math.max(maxT, zug[k]); }
        spitze = Math.max(spitze, maxT);
        if (!fest && !slippt) {
            double tx = x[n] - x[n - 1], ty = y[n] - y[n - 1], tl = Math.sqrt(tx * tx + ty * ty + (z[n] - z[n - 1]) * (z[n] - z[n - 1])) + 1e-9;
            if (zug[n - 1] * Math.hypot(tx, ty) / tl > haltekraft) slippt = true;
        }
        double dx = x[1] - x[0], dy = y[1] - y[0], dz = z[1] - z[0], l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-9;
        double t0 = zug[0];
        return new double[] {t0 * dx / l, t0 * dy / l, t0 * dz / l - G * wet * 0.5 * knotenMasse};
    }

    // ------------------------------------------------------------------ Abfragen

    public int knoten() { return n + 1; }

    /** Knoten i in der Welt. */
    public double[] knoten(int i) { return new double[] {x[i], y[i], z[i]}; }

    /** Zug an der Klüse (N). */
    public double zugKlause() { return zug[0]; }

    /** Waagerechter Zug am Anker (N). */
    public double zugAnker() {
        double tx = x[n] - x[n - 1], ty = y[n] - y[n - 1], tl = Math.sqrt(tx * tx + ty * ty + (z[n] - z[n - 1]) * (z[n] - z[n - 1])) + 1e-9;
        return zug[n - 1] * Math.hypot(tx, ty) / tl;
    }

    public boolean slippt() { return slippt; }

    /** Gewicht je m unter Wasser (N/m). */
    public double gewichtNass() { return masseJeMeter * G * nass(); }

    /** Länge auf dem Grund (m; beide Knoten höchstens 5 cm über dem Grund). */
    public double aufGrund(Wasser w) {
        double s = 0;
        for (int k = 0; k < n; k++)
            if (z[k] - w.grund(x[k], y[k]) < 0.05 + durchmesser && z[k + 1] - w.grund(x[k + 1], y[k + 1]) < 0.05 + durchmesser) s += segment;
        return s;
    }

    /**
     * Kettenlinie mit Grundberührung (unelastisch): Klüse h über dem Grund, waagerechter Abstand X zum Anker, Länge L,
     * Gewicht w je m. Gibt {H, hängende Länge s, Zug an der Klüse T} oder null, wenn die Kette straff wäre.
     * s = √(h² + 2 h a), X = L − s + a asinh(s / a), a = H / w, T = H + w h.
     */
    public static double[] kettenlinie(double h, double X, double L, double w) {
        if (X >= Math.sqrt(Math.max(0, L * L - h * h)) - 1e-9) return null;
        double lo = 1e-3, hi = 1e9;
        for (int it = 0; it < 200; it++) {
            double a = Math.sqrt(lo * hi), s = Math.sqrt(h * h + 2 * h * a);
            if (s > L) { hi = a; continue; }
            double xx = L - s + a * Math.log(s / a + Math.sqrt(s * s / (a * a) + 1));
            if (xx > X) hi = a; else lo = a;
        }
        double a = Math.sqrt(lo * hi), s = Math.sqrt(h * h + 2 * h * a);
        return new double[] {a * w, s, a * w + w * h};
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "Kette %.0f m (%d Glieder zu %.2f m), %.1f kg/m, Zug höchstens %.1f kN%s", laenge, n, segment, masseJeMeter, spitze / 1000,
                fest ? ", Stein" : slippt ? ", Anker slippt" : "");
    }
}
