package com.dan.physics.auftrieb;

/**
 * Was einen Körper festhält oder abstützt (Seegang Werkbuch, S7a): Kette, Leine, Fender. Der Körper fragt in jedem
 * Teilschritt nach der Kraft; der Festmacher darf dabei seinen eigenen Zustand weiterrechnen (die Kette).
 */
public interface Festmacher {
    /**
     * Kraft auf den Körper k zur Zeit t nach dem Teilschritt dt: schreibt die Kraft (Welt, N) nach f und den Angriffspunkt
     * (Welt, m) nach punkt. Gibt false zurück, wenn keine Kraft wirkt.
     */
    boolean kraft(Koerper k, Wasser w, double t, double dt, double[] f, double[] punkt);

    /**
     * Eine Leine von einem Punkt am Körper (Klampe, Achsen des Körpers) zu einem festen Punkt (Poller, Welt): trägt nur auf
     * Zug, mit der Dehnsteifigkeit EA (N) über die Länge und einer Dämpfung als Anteil der kritischen [G].
     */
    final class Leine implements Festmacher {
        public final double[] klampe, poller;
        public double laenge, ea, zeta = 0.2;
        /** Zug zuletzt (N). */
        public double zug;

        public Leine(double[] klampe, double[] poller, double laenge, double ea) {
            this.klampe = klampe.clone();
            this.poller = poller.clone();
            this.laenge = laenge;
            this.ea = ea;
        }

        @Override public boolean kraft(Koerper k, Wasser w, double t, double dt, double[] f, double[] punkt) {
            double[] p = k.punktWelt(klampe[0], klampe[1], klampe[2]);
            double dx = poller[0] - p[0], dy = poller[1] - p[1], dz = poller[2] - p[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            punkt[0] = p[0]; punkt[1] = p[1]; punkt[2] = p[2];
            if (l <= laenge || l < 1e-9) { zug = 0; return false; }
            double ux = dx / l, uy = dy / l, uz = dz / l;
            double[] v = k.geschwindigkeitAn(p);
            double vl = -(v[0] * ux + v[1] * uy + v[2] * uz);                  // Dehnrate (m/s)
            double kk = ea / laenge, c = 2 * zeta * Math.sqrt(kk * k.masse);
            zug = Math.max(0, kk * (l - laenge) + c * vl);
            f[0] = zug * ux; f[1] = zug * uy; f[2] = zug * uz;
            return zug > 0;
        }
    }

    /**
     * Ein Fender an einem Punkt des Körpers mit Halbmesser r gegen eine feste Wand (Kaje): Punkt p0 der Wand, Normale n
     * (aus der Wand ins Wasser). Drückt nur, Federsteifigkeit k (N/m), Dämpfung als Anteil der kritischen, Reibung μ [G].
     */
    final class Fender implements Festmacher {
        public final double[] ort, p0, n;
        public double r, steif, zeta = 0.3, reibung = 0.3;
        /** Druck zuletzt (N). */
        public double druck;

        public Fender(double[] ort, double r, double[] p0, double[] n, double steif) {
            this.ort = ort.clone();
            this.r = r;
            this.p0 = p0.clone();
            double l = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            this.n = new double[] {n[0] / l, n[1] / l, n[2] / l};
            this.steif = steif;
        }

        @Override public boolean kraft(Koerper k, Wasser w, double t, double dt, double[] f, double[] punkt) {
            double[] p = k.punktWelt(ort[0], ort[1], ort[2]);
            punkt[0] = p[0]; punkt[1] = p[1]; punkt[2] = p[2];
            double dist = (p[0] - p0[0]) * n[0] + (p[1] - p0[1]) * n[1] + (p[2] - p0[2]) * n[2];
            double pen = r - dist;
            if (pen <= 0) { druck = 0; return false; }
            double[] v = k.geschwindigkeitAn(p);
            double vn = v[0] * n[0] + v[1] * n[1] + v[2] * n[2], c = 2 * zeta * Math.sqrt(steif * k.masse);
            druck = Math.max(0, steif * pen - c * vn);
            double tx = v[0] - vn * n[0], ty = v[1] - vn * n[1], tz = v[2] - vn * n[2], ts = Math.sqrt(tx * tx + ty * ty + tz * tz);
            double fr = ts > 1e-6 ? Math.min(reibung * druck, k.masse * ts / Math.max(dt, 1e-6) * 0.1) / ts : 0;
            f[0] = druck * n[0] - fr * tx; f[1] = druck * n[1] - fr * ty; f[2] = druck * n[2] - fr * tz;
            return true;
        }
    }

    /**
     * Seit S7d (Seegang Werkbuch 89): eine Führung quer zu einer Linie, wie ein Bug- oder Heckstrahlruder beim An- und
     * Ablegen [G]: zieht einen Punkt am Körper (Achsen des Körpers) waagrecht auf die Linie durch {@link #p0} mit der
     * Richtung {@link #e} (Feder mit Dämpfer, nur quer zur Linie), höchstens mit {@link #fMax}. Aus, solange {@link #an} falsch.
     */
    final class Fuehrung implements Festmacher {
        public final double[] punkt;
        public final double[] p0 = new double[2], e = {0, 1};
        /**
         * Seit S7d8 (Seegang Werkbuch 97, SF144 A): Geschwindigkeit, die der Punkt quer zur Linie haben soll (Welt, m/s); der
         * Dämpfer wirkt gegen die Abweichung davon. Für eine Linie, die sich dreht (Drehen auf der Stelle); sonst 0.
         */
        public final double[] vSoll = new double[2];
        public double steif, daempf, fMax;
        public boolean an;

        public Fuehrung(double[] punkt, double steif, double daempf, double fMax) {
            this.punkt = punkt.clone();
            this.steif = steif;
            this.daempf = daempf;
            this.fMax = fMax;
        }

        @Override public boolean kraft(Koerper k, Wasser w, double t, double dt, double[] f, double[] wo) {
            if (!an) return false;
            double[] p = k.punktWelt(punkt[0], punkt[1], punkt[2]);
            double dx = p0[0] - p[0], dy = p0[1] - p[1], a = dx * e[0] + dy * e[1];
            dx -= a * e[0];
            dy -= a * e[1];
            double[] v = k.geschwindigkeitAn(p);
            double vx = v[0] - vSoll[0], vy = v[1] - vSoll[1], b = vx * e[0] + vy * e[1];
            vx -= b * e[0];
            vy -= b * e[1];
            double fx = steif * dx - daempf * vx, fy = steif * dy - daempf * vy, fb = Math.hypot(fx, fy);
            if (fb > fMax) { fx *= fMax / fb; fy *= fMax / fb; }
            f[0] = fx; f[1] = fy; f[2] = 0;
            wo[0] = p[0]; wo[1] = p[1]; wo[2] = p[2];
            return true;
        }
    }
}
