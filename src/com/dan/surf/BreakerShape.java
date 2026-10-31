package com.dan.surf;

/**
 * Die Form einer einzelnen Welle im Querschnitt (seit Sylt Phase 6, F9 A), im Bezugssystem des Kamms: x zur Küste
 * (vom Kamm aus), z über dem Ruhewasser. Eine Form ist eine Linie von hinten (Trog seeseits) nach vorn (Trog
 * landseits), die sich überschlagen darf: beim Sturzbrecher läuft sie über den Kamm auf die Lippe hinaus, an deren
 * Unterseite zurück in den Hohlraum und die Front hinab. Je Punkt dazu Schaum 0..1 und „dünn“ 0..1 (die Lippe, durch
 * die Licht scheint) [G: Formen nach Fotos und Beschreibungen von Battjes 1974, Peregrine 1983, Basco 1985].
 * <ul>
 * <li><b>Ungebrochen:</b> spitzer Kamm, flacher Trog; je näher am Brechen (H/(γ·h)), desto steiler die Front.</li>
 * <li><b>Schwallbrecher</b> (Fortschritt p): Schaum rollt von der Spitze die Front hinab, die Spitze wölbt sich wenig.</li>
 * <li><b>Sturzbrecher:</b> die Spitze wird als Lippe nach vorn geworfen und fällt im Bogen bis auf den Trog vor der
 * Front (p = 1, etwa 1,1·H vor dem Kamm); darunter der Hohlraum.</li>
 * <li><b>Reflexionsbrecher</b> (collapsing): der Fuß der Front wird steil, kippt vorn über und schäumt.</li>
 * <li><b>Walze</b> (nach dem Brechen): eine Stufe mit Schaumwalze auf der Front.</li>
 * </ul>
 * Alle Formen haben dieselbe Zahl Punkte ({@link #K}), gleichmäßig nach der Bogenlänge, damit sie ineinander übergehen.
 */
public final class BreakerShape {
    /** Punkte je Form. */
    public static final int K = 128;

    /** Eine Form: Lage (x, z), Schaum, dünn. */
    public static final class Curve {
        public final double[] x = new double[K], z = new double[K], foam = new double[K], thin = new double[K];
        /** Ob die Linie sich überschlägt (x läuft irgendwo rückwärts). */
        public boolean folds() {
            for (int i = 1; i < K; i++) if (x[i] < x[i - 1] - 1e-6) return true;
            return false;
        }
    }

    private BreakerShape() { }

    /** Kamm und Trog über dem Ruhewasser für H und h [G nach der Cnoidal-Welle: der Kamm wächst im Flachen auf bis 0,8·H]. */
    public static double crestShare(double H, double h) {
        double nl = Math.max(0, Math.min(1, (H / Math.max(0.05, h) - 0.1) / 0.6));
        return 0.5 + 0.3 * nl;
    }

    // ------------------------------------------------------------------ Bausteine

    /** Polylinie, frei wachsend. */
    static final class Poly {
        double[] x = new double[64], z = new double[64], f = new double[64], t = new double[64];
        int n;
        void add(double px, double pz, double foam, double thin) {
            if (n == x.length) { x = java.util.Arrays.copyOf(x, n * 2); z = java.util.Arrays.copyOf(z, n * 2); f = java.util.Arrays.copyOf(f, n * 2); t = java.util.Arrays.copyOf(t, n * 2); }
            x[n] = px; z[n] = pz; f[n] = foam; t[n] = thin; n++;
        }
        /** Kubische Bézier von (x0, z0) nach (x3, z3), ohne den Anfangspunkt. */
        void bezier(double x0, double z0, double x1, double z1, double x2, double z2, double x3, double z3, int m,
                    double foam0, double foam1, double thin0, double thin1) {
            for (int i = 1; i <= m; i++) {
                double t = (double) i / m, u = 1 - t;
                double bx = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
                double bz = u * u * u * z0 + 3 * u * u * t * z1 + 3 * u * t * t * z2 + t * t * t * z3;
                add(bx, bz, foam0 + (foam1 - foam0) * t, thin0 + (thin1 - thin0) * t);
            }
        }
        /** Nach der Bogenlänge auf K Punkte gebracht. */
        Curve resample() {
            Curve c = new Curve();
            double[] s = new double[n];
            for (int i = 1; i < n; i++) s[i] = s[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            double total = s[n - 1];
            int j = 0;
            for (int k = 0; k < K; k++) {
                double target = total * k / (K - 1);
                while (j < n - 2 && s[j + 1] < target) j++;
                double seg = s[j + 1] - s[j], f2 = seg > 1e-12 ? (target - s[j]) / seg : 0;
                f2 = Math.max(0, Math.min(1, f2));
                c.x[k] = x[j] + (x[j + 1] - x[j]) * f2;
                c.z[k] = z[j] + (z[j + 1] - z[j]) * f2;
                c.foam[k] = f[j] + (f[j + 1] - f[j]) * f2;
                c.thin[k] = t[j] + (t[j + 1] - t[j]) * f2;
            }
            return c;
        }
    }

    /** Rücken der Welle (seeseits) vom Trog bis vor den Kamm, ohne den Kamm. */
    static void back(Poly p, double H, double back, double ac, double at, double sharp) {
        int m = 36;
        for (int i = 0; i < m; i++) {
            double u = 1 - (double) i / m;              // 1 hinten … 0 am Kamm
            double g = Math.pow(0.5 * (1 + Math.cos(Math.PI * u)), sharp);
            p.add(-u * back, at + (ac - at) * g, 0, 0);
        }
    }

    /** Trog vor der Front bis zum vorderen Ende. */
    static void troughAhead(Poly p, double x0, double front, double at) {
        double end = Math.max(x0 + 0.01, front);
        int m = 10;
        for (int i = 1; i <= m; i++) p.add(x0 + (end - x0) * i / m, at, 0, 0);
    }

    // ------------------------------------------------------------------ Formen

    /** Ungebrochene Welle: H, Tiefe h, Länge hinter und vor dem Kamm (m), Nähe zum Brechen 0..1. */
    public static Curve unbroken(double H, double h, double back, double front, double near) {
        Poly p = new Poly();
        double cs = crestShare(H, h), ac = cs * H, at = ac - H;
        double sharp = 1.4 + 1.6 * near;
        back(p, H, back, ac, at, sharp);
        p.add(0, ac, 0, 0);
        // Front: je näher am Brechen, desto kürzer und steiler
        double wf = Math.min(front, back) * (0.9 - 0.62 * near);
        int m = 36;
        for (int i = 1; i <= m; i++) {
            double u = (double) i / m;
            double g = Math.pow(0.5 * (1 + Math.cos(Math.PI * u)), sharp * (1 + 0.5 * near));
            p.add(u * wf, at + (ac - at) * g, 0, 0);
        }
        troughAhead(p, wf, front, at);
        return p.resample();
    }

    /** Schwallbrecher im Fortschritt p (0..1, danach bleibt die Schaumwalze vorn oben). */
    public static Curve spilling(double H, double h, double back, double front, double p) {
        Poly q = new Poly();
        double cs = crestShare(H, h), ac = cs * H, at = ac - H;
        back(q, H, back, ac, at, 3.0);
        double pp = Math.min(1, p);
        double bulge = 0.10 * H * pp, wf = Math.min(front, back) * 0.3 + 0.3 * H;
        q.add(0, ac, 0.6 * pp, 0);
        // die Spitze wölbt sich ein wenig vor, Schaum rollt bis auf ein Drittel der Front hinab
        q.bezier(0, ac, bulge + 0.12 * H, ac - 0.02 * H, 0.25 * wf, ac - 0.35 * H, 0.45 * wf, at + 0.45 * H, 14, 0.9 * pp, 0.8 * pp, 0, 0);
        q.bezier(0.45 * wf, at + 0.45 * H, 0.6 * wf, at + 0.15 * H, 0.8 * wf, at, wf, at, 12, 0.8 * pp, 0.15 * pp, 0, 0);
        troughAhead(q, wf, front, at);
        return q.resample();
    }

    /** Lage der Lippe beim Sturzbrecher im Fortschritt p: Spitze (x, z) relativ zum Kamm. */
    public static double[] lipTip(double H, double h, double p) {
        double cs = crestShare(H, h), ac = cs * H, at = ac - H;
        double reach = 1.1 * H;
        double x = 0.05 * H + reach * Math.pow(Math.max(0, p), 0.8);
        double z = ac - (ac - at) * p * p;
        return new double[]{x, z};
    }

    /** Sturzbrecher: die Lippe fliegt im Bogen nach vorn und trifft bei p = 1 den Trog; darunter der Hohlraum. */
    public static Curve plunging(double H, double h, double back, double front, double p) {
        p = Math.max(0, Math.min(1, p));
        Poly q = new Poly();
        double cs = crestShare(H, h), ac = cs * H, at = ac - H;
        back(q, H, back, ac, at, 3.4);
        double[] tip = lipTip(H, h, p);
        double wf = 0.35 * H + 0.12 * Math.min(front, back);   // Fuß der Front unter der Lippe
        double tx = 0.04 * H * p, tz = ac;                       // die Spitze des Kamms rückt ein wenig vor
        q.add(tx, tz, 0.3 * p, 0);
        // Oberseite der Lippe: vom Kamm in einem Bogen hinaus und hinab bis zur Spitze
        q.bezier(tx, tz, tx + 0.55 * (tip[0] - tx), tz + 0.10 * H * (1 - p),
                tip[0] + 0.05 * H * (1 - p), tip[1] + 0.40 * H * Math.min(1, 2 * p), tip[0], tip[1], 26,
                0.3 * p, 0.6 * p, 0, 1);
        // Unterseite der Lippe zurück zur Wurzel unter dem Kamm (Dicke der Lippe wächst zur Wurzel)
        double ux = Math.min(0.18 * H + 0.15 * H * p, tip[0] - 0.03 * H), uz = ac - (0.22 + 0.30 * Math.min(1, 1.6 * p)) * H;
        q.bezier(tip[0], tip[1], tip[0] - 0.06 * H, tip[1] + 0.22 * H * Math.min(1, 2 * p),
                ux + 0.45 * (tip[0] - ux), uz + 0.05 * H, ux, uz, 18, 0.6 * p, 0.1 * p, 1, 0.2);
        // Dach des Hohlraums und die Front hinab zum Fuß (hohl, darum unter der Lippe zurückgebogen)
        q.bezier(ux, uz, ux - 0.10 * H * p, uz - 0.25 * H, wf - 0.10 * H, at + 0.35 * H, wf, at, 20, 0.1 * p, 0.05 * p, 0.2, 0);
        troughAhead(q, wf, Math.max(front, tip[0] + 0.3 * H), at);
        return q.resample();
    }

    /** Reflexionsbrecher (collapsing): der Fuß der Front wird steil und kippt vorn über, Schaum am Fuß. */
    public static Curve collapsing(double H, double h, double back, double front, double p) {
        p = Math.max(0, Math.min(1, p));
        Poly q = new Poly();
        double cs = crestShare(H, h), ac = cs * H, at = ac - H;
        back(q, H, back, ac, at, 2.6);
        double wf = 0.55 * H + 0.15 * Math.min(front, back) * (1 - 0.5 * p);
        q.add(0, ac, 0, 0);
        // Front: oben glatt und schräg, unten steil; der Fuß schiebt sich als Zehe nach vorn über
        double kx = wf * (0.75 + 0.35 * p), kz = at + (0.35 - 0.2 * p) * H;
        q.bezier(0, ac, 0.25 * wf, ac - 0.05 * H, 0.55 * wf, ac - 0.45 * H, kx, kz, 22, 0, 0.5 * p, 0, 0.3 * p);
        q.bezier(kx, kz, kx + 0.05 * H * p, kz - 0.1 * H, wf + 0.05 * H * (1 - p), at + 0.1 * H, wf, at, 14, 0.5 * p, 0.9 * p, 0.3 * p, 0);
        troughAhead(q, wf, front, at);
        return q.resample();
    }

    /** Walze (Weißwasser nach dem Brechen): Stufe der Höhe Hb über dem Trog vorn, Schaumwalze auf der Front. */
    public static Curve bore(double Hb, double h, double back, double front) {
        Poly q = new Poly();
        double ac = 0.6 * Hb, at = ac - Hb;
        // Rücken: flach abfallend
        int m = 30;
        for (int i = 0; i < m; i++) {
            double u = 1 - (double) i / m;
            double g = Math.pow(1 - u, 0.7);
            q.add(-u * back, at + (ac - at) * g, 0.25 * (1 - u) * (1 - u), 0);
        }
        double rl = Math.max(0.4, 1.5 * Hb);
        q.add(0, ac, 1, 0);
        // Walze: gewölbt, schaumig, die Front steil
        q.bezier(0, ac, 0.35 * rl, ac + 0.12 * Hb, 0.75 * rl, ac - 0.1 * Hb, rl, at + 0.35 * Hb, 16, 1, 1, 0, 0);
        q.bezier(rl, at + 0.35 * Hb, rl + 0.12 * Hb, at + 0.15 * Hb, rl + 0.2 * Hb, at + 0.02 * Hb, rl + 0.35 * Hb, at, 8, 1, 0.5, 0, 0);
        troughAhead(q, rl + 0.35 * Hb, front, at);
        return q.resample();
    }

    /** Übergang zwischen zwei Formen (gleich viele Punkte), f = 0 … 1. */
    public static Curve blend(Curve a, Curve b, double f) {
        Curve c = new Curve();
        f = Math.max(0, Math.min(1, f));
        for (int k = 0; k < K; k++) {
            c.x[k] = a.x[k] + (b.x[k] - a.x[k]) * f;
            c.z[k] = a.z[k] + (b.z[k] - a.z[k]) * f;
            c.foam[k] = a.foam[k] + (b.foam[k] - a.foam[k]) * f;
            c.thin[k] = a.thin[k] + (b.thin[k] - a.thin[k]) * f;
        }
        return c;
    }
}
