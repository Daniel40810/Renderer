package com.dan.core.szene;

/**
 * Katzenpfoten (Seegang Werkbuch, Datei 104, S8b, SF156 A): Böenflecken, die über das Wasser laufen. Der Wind am Ort ist
 * der Wind der Szene mal einem log-normalen Böenfeld; seine Flecken treiben mit {@link #DRIFT} des Winds und wandeln
 * sich. Wo der Wind am Ort unter {@link #U_EIN} fällt, entstehen keine Kräuselwellen (Kahma und Donelan 1988); bis
 * {@link #U_VOLL} wachsen sie zur vollen Rauigkeit nach Cox und Munk beim Wind am Ort. Über {@link #U_AUS} m/s Wind der
 * Szene ist die Fläche überall voll rau (das Feld wirkt nicht mehr).
 * <ul>
 * <li><b>Stärke aus der Böe:</b> ERA5 gibt mit {@code fg10} die stärkste Böe der Stunde. Böenfaktor G = fg10 / U₁₀,
 * Turbulenzgrad I = (G − 1) / {@link #SPITZE} (Spitzenfaktor nach Wieringa 1973, 3-s-Böe in der Stunde) [G], Streuung
 * des log-normalen Felds σ = √ln(1 + I²). Ohne Böe ({@code G} NaN) gilt {@link #G_SONST} [G].</li>
 * <li><b>Feld:</b> drei Oktaven Wertrauschen in Ort und Zeit (dreilinear mit weicher Kurve, Gitter mit der Periode
 * {@link #PERIODE} Zellen, damit Karte und Prozessor den Ursprung in double modulo der Periode übergeben können),
 * Flecken von {@link #FLECK} m, auf Mittel 0 und Streuung 1 gebracht ({@link #MITTEL}, {@link #STREUUNG}, gemessen). Das
 * Feld treibt mit {@link #DRIFT} · U in Windrichtung; es wandelt sich in {@link #WANDEL} Fleckzeiten L/U je Zelle der
 * Zeit.</li>
 * <li><b>Wind am Ort</b> u = U · exp(σ z − σ²/2) (der Mittelwert bleibt U).</li>
 * </ul>
 * {@code render/water/katzen.glsl} rechnet dasselbe auf der Karte (Ganzzahl-Hash, gleiche Kurven).
 */
public final class Katzenpfoten {
    public static final String VERSION = "0.1";
    /** Fleckgröße (m) [G, wie com.dan.ocean]. */
    public static final double FLECK = 45;
    /** Die Flecken treiben mit diesem Anteil des Winds [G, wie com.dan.ocean]. */
    public static final double DRIFT = 0.8;
    /** Kräusel setzen ein / sind voll beim Wind am Ort (m/s) [G nach Kahma und Donelan 1988, Schwelle um 1 m/s in Wassernähe, auf 10 m umgerechnet; seit L6a 1,5 und 4 statt 1 und 3: Befund 1, die Flecken waren kaum zu sehen]. */
    public static final double U_EIN = 1.5, U_VOLL = 4.0;
    /** Das Feld wirkt bis U_AB voll und ist ab U_AUS m/s Wind der Szene aus (volle Rauigkeit) [G, Prüfstein 37]. */
    public static final double U_AB = 4.0, U_AUS = 6.0;
    /** Spitzenfaktor der 3-s-Böe in der Stunde [G]. */
    public static final double SPITZE = 3.0;
    /** Böenfaktor ohne Daten [G]: offene See, schwacher Wind. */
    public static final double G_SONST = 1.5;
    /** Eine Zelle der Zeit dauert so viele Fleckzeiten L/U (mindestens {@link #WANDEL_MIN} s) [G]. */
    public static final double WANDEL = 6.0, WANDEL_MIN = 30;
    /** Periode des Gitters (Zellen) in Ort und Zeit. */
    public static final int PERIODE = 256;
    /** Mittel und Streuung der Summe der drei Oktaven (gemessen über 2·10⁶ Proben, {@link #main}). */
    public static final double MITTEL = 0.43753, STREUUNG = 0.10597;

    private Katzenpfoten() { }

    /** Streuung (ln) des Böenfelds aus dem Böenfaktor G = fg10 / U₁₀ (NaN: {@link #G_SONST}). */
    public static double sigma(double g) {
        if (Double.isNaN(g) || Double.isInfinite(g)) g = G_SONST;
        g = Math.max(1, Math.min(4, g));
        double i = (g - 1) / SPITZE;
        return Math.sqrt(Math.log(1 + i * i));
    }

    /** Gewicht des Felds beim Wind U der Szene: 1 bis {@link #U_AB}, 0 ab {@link #U_AUS}. */
    public static double gewicht(double u) { return 1 - glatt(U_AB, U_AUS, u); }

    /** Anteil der Kräusel beim Wind am Ort u. */
    public static double anteil(double u) { return glatt(U_EIN, U_VOLL, u); }

    /** Neigungsvarianz der Kräusel beim Wind am Ort u (Cox und Munk, mal dem Anteil). */
    public static double sigma2Ort(double u) { return anteil(u) * Wasseroptik.coxMunk(u); }

    /**
     * Wirksame Neigungsvarianz am Ort: zwischen Cox–Munk beim Wind U der Szene und der am Ort, nach {@link #gewicht}.
     */
    public static double sigma2(double uSzene, double uOrt) {
        double w = gewicht(uSzene);
        return (1 - w) * Wasseroptik.coxMunk(uSzene) + w * sigma2Ort(uOrt);
    }

    /** smoothstep wie GLSL. */
    public static double glatt(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Zeit einer Zelle der Wandlung (s) beim Wind U. */
    public static double zelleZeit(double u) { return Math.max(WANDEL_MIN, WANDEL * FLECK / Math.max(u, 0.3)); }

    static double mod(double x) { return x - PERIODE * Math.floor(x / PERIODE); }

    /** Normiertes Feld z (Mittel 0, Streuung 1) bei Gitterkoordinaten (qx, qy, τ). */
    public static double z(double qx, double qy, double tau) { return (summe(qx, qy, tau) - MITTEL) / STREUUNG; }

    /** Summe der drei Oktaven (0,5 / 0,25 / 0,125) in [0, 0,875). */
    public static double summe(double qx, double qy, double tau) {
        double s = 0, a = 0.5;
        int f = 1;
        for (int o = 0; o < 3; o++) {
            s += a * rauschen(qx * f, qy * f, tau * f, PERIODE * f, o);
            a *= 0.5;
            f *= 2;
        }
        return s;
    }

    /** Wind am Ort (m/s) bei Gitterkoordinaten q, Wind U der Szene und Streuung σ. */
    public static double windOrt(double qx, double qy, double tau, double u, double sigma) {
        return u * Math.exp(sigma * z(qx, qy, tau) - 0.5 * sigma * sigma);
    }

    /** Wertrauschen in [0, 1): dreilinear mit weicher Kurve, Gitter periodisch mit p, Oktave o im Hash. */
    static double rauschen(double x, double y, double t, int p, int o) {
        double fx = Math.floor(x), fy = Math.floor(y), ft = Math.floor(t);
        int ix = (int) fx, iy = (int) fy, it = (int) ft;
        double ux = kurve(x - fx), uy = kurve(y - fy), ut = kurve(t - ft);
        double r = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dt = (c >> 2) & 1;
            double w = (dx == 1 ? ux : 1 - ux) * (dy == 1 ? uy : 1 - uy) * (dt == 1 ? ut : 1 - ut);
            r += w * hash(Math.floorMod(ix + dx, p), Math.floorMod(iy + dy, p), Math.floorMod(it + dt, p), o);
        }
        return r;
    }

    static double kurve(double f) { return f * f * (3 - 2 * f); }

    /** Ganzzahl-Hash wie katzen.glsl (uint mit Überlauf), Ergebnis in [0, 1). */
    public static double hash(int x, int y, int t, int o) {
        int v = x * 1597334677 ^ y * -482951495 ^ t * -1640531535 ^ (o + 1) * 0x27d4eb2d;
        v ^= v >>> 16;
        v *= 0x7feb352d;
        v ^= v >>> 15;
        v *= 0x846ca68b;
        v ^= v >>> 16;
        return (v >>> 8) / 16777216.0;
    }

    /**
     * Der Stand des Felds über die Zeit: der Weg, den die Flecken mit dem Wind getrieben sind (m, Ost und Nord), und die
     * Zeit der Wandlung τ, Schritt für Schritt mit dem Wind des Bilds aufsummiert (der Wind ändert sich; ein Produkt
     * U · t sprünge bei jeder Änderung). Nach einem Sprung der Zeit (rückwärts oder über {@link #SPRUNG_S}) beginnt der
     * Stand neu aus der Zeit modulo einer Stunde, damit dieselbe Zeit dasselbe Feld gibt.
     */
    public static final class Feld {
        /** Weiter als so weit (s) gilt ein Schritt als Sprung [G]. */
        public static final double SPRUNG_S = 600;
        private double t = Double.NaN, dOst, dNord, tau;

        /** Schreibt den Stand auf die Zeit t (s) mit dem Wind (u, v) nach Ost und Nord (m/s) fort. */
        public void schritt(double tNeu, double u, double v) {
            if (Double.isNaN(u) || Double.isNaN(v)) { u = 0; v = 0; }
            double dt = tNeu - t;
            if (Double.isNaN(t) || dt < 0 || dt > SPRUNG_S) {
                double r = tNeu - 3600 * Math.floor(tNeu / 3600);
                dOst = DRIFT * u * r;
                dNord = DRIFT * v * r;
                tau = r / zelleZeit(Math.hypot(u, v));
            } else {
                dOst += DRIFT * u * dt;
                dNord += DRIFT * v * dt;
                tau += dt / zelleZeit(Math.hypot(u, v));
            }
            double p = PERIODE * FLECK;
            dOst -= p * Math.floor(dOst / p);
            dNord -= p * Math.floor(dNord / p);
            tau = mod(tau);
            t = tNeu;
        }

        /** Gitterkoordinaten {qx, qy, τ} des Felds am Ort (Ost, Nord in m). */
        public double[] ursprung(double ost, double nord) { return new double[] {mod((ost - dOst) / FLECK), mod((nord - dNord) / FLECK), tau}; }

        /** Weg der Flecken (m, Ost und Nord, modulo der Periode) und τ, für Prüfungen. */
        public double[] stand() { return new double[] {dOst, dNord, tau, t}; }
    }

    /** Misst Mittel und Streuung der Summe (für {@link #MITTEL}, {@link #STREUUNG}). */
    public static void main(String[] a) {
        java.util.Random r = new java.util.Random(1);
        double s = 0, s2 = 0;
        int n = 2_000_000;
        for (int i = 0; i < n; i++) {
            double v = summe(r.nextDouble() * PERIODE, r.nextDouble() * PERIODE, r.nextDouble() * PERIODE);
            s += v;
            s2 += v * v;
        }
        double m = s / n;
        System.out.printf(java.util.Locale.ROOT, "Mittel %.5f, Streuung %.5f%n", m, Math.sqrt(s2 / n - m * m));
    }
}
