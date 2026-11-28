package com.dan.physics.auftrieb;

/**
 * Gleiten nach Savitsky (1964) für einen Rumpf mit Knickspant (Seegang Werkbuch, S7e2, Datei 101; SF150 A).
 * <ul>
 * <li>Trimm τ: Winkel des Kiels gegen die Waagerechte (Grad, Bug oben positiv). Tiefe des Kiels am Spiegel d unter der
 * Fläche; benetzte Länge am Kiel L_K = d / sin τ, an der Kimm L_C = L_K − b tan β / (π tan τ) (Sprühwurzel nach Savitsky),
 * mittlere benetzte Länge in Breiten λ = (L_K + L_C) / (2 b).</li>
 * <li>Auftrieb C_L0 = τ^1,1 (0,0120 λ^½ + 0,0055 λ^5/2 / C_V²), C_V = U / √(g b); mit Aufkimmung C_Lβ = C_L0 − 0,0065 β
 * C_L0^0,6; L = ½ ρ U² b² C_Lβ senkrecht zum Kiel im Druckpunkt l_p = λ b (0,75 − 1 / (5,21 C_V² / λ² + 2,39)) vor dem
 * Spiegel. Mit der Neigung des Kiels bremst er mit L sin τ, wie Savitskys Druckwiderstand Δ tan τ.</li>
 * <li><b>Übergang</b> [G]: Mit Fahrt reißt das Wasser am Spiegel ab, und der Auftrieb der Verdrängung fällt hinten weg;
 * was bleibt, steckt in Savitskys zweitem Glied. Der Körper nimmt deshalb mit dem Anteil w = glatt(C_V − 0,5) den Auftrieb
 * der Proben zurück (Kraft, Stampfen und Gieren; das Rollen bleibt) und setzt w · L dafür. Unter C_V 0,5 schwimmt er wie
 * ein Verdränger, ab C_V 1,5 gleitet er ganz nach Savitsky.</li>
 * <li>Der Winkel in C_L ist der Anstellwinkel des Bodens gegen die Anströmung im Druckpunkt (in ruhiger Fahrt τ; hebt sich
 * der Boden mit w, τ − w / U, wie bei einer Tragfläche): das dämpft Tauchen und Stampfen [G].</li>
 * <li>L rechnet mit mindestens {@link #TAU_MIN}° Trimm, und λ hat keine Grenze an der Länge [G]: Liegt der Körper flach
 * und tief, wird λ groß, das zweite Glied trägt wie die Verdrängung, und der Druckpunkt liegt vorn; so hebt sich der Bug,
 * und der Körper findet aus der Ruhe in Savitskys Gleichgewicht.</li>
 * </ul>
 * Savitsky gilt für 0,6 ≤ C_V ≤ 13, 2° ≤ τ ≤ 15°, λ ≤ 4; der Adler-Express liegt bei 24 kn mit λ um 6 darüber (Halbgleiten),
 * dort ist L eine Fortsetzung der Formel [G]. Der Wellenwiderstand des Verdrängers ({@link Koerper#wellenwiderstand}) läuft
 * für einen gleitenden Körper zwischen F_n 0,4 und 0,7 aus [G] ({@link #wellenAnteil}).
 */
public final class Gleiten {
    public static final String VERSION = "0.1";
    /** Breite über die Kimm (m), Aufkimmung (Grad), Lage des Spiegels längs (Körper, m) und Höhe des Kiels dort (m). */
    public final double b, beta, xSpiegel, zKiel;
    /** Kleinster Trimm (Grad), mit dem L rechnet [G]: bei τ = 0 gäbe Savitsky keinen Auftrieb. */
    public static final double TAU_MIN = 1.0;
    /** Zuletzt gerechnet (für Prüfung und Auskunft): Trimm (Grad), λ, C_V, Auftrieb L (N), l_p (m), Tiefe am Spiegel (m), Anteil w, Fahrt (m/s). */
    public double tau, lambda, cv, auftrieb, lp, tiefe, anteil, fahrt;
    private double lp0;

    public Gleiten(double b, double betaGrad, double xSpiegel, double zKiel) {
        this.b = b;
        this.beta = betaGrad;
        this.xSpiegel = xSpiegel;
        this.zKiel = zKiel;
    }

    /** Für einen Rumpf aus {@link Form.Rumpf}: Breite, Aufkimmung, Spiegel am hinteren Ende, Kiel auf dem Boden dort. */
    public static Gleiten fuer(Form.Rumpf r) { return new Gleiten(r.breite, r.aufkimmung, -r.laenge / 2, r.boden(-r.laenge / 2 + 1e-6)); }

    /** C_L0 der ebenen Platte (τ in Grad). */
    public static double cl0(double tau, double lambda, double cv) {
        return Math.pow(tau, 1.1) * (0.0120 * Math.sqrt(lambda) + 0.0055 * Math.pow(lambda, 2.5) / (cv * cv));
    }

    /** C_Lβ aus C_L0 (β in Grad). */
    public static double clb(double cl0, double beta) { return Math.max(0, cl0 - 0.0065 * beta * Math.pow(cl0, 0.6)); }

    /** Druckpunkt vom Spiegel in Breiten: λ (0,75 − 1 / (5,21 C_V² / λ² + 2,39)). */
    public static double druckpunkt(double lambda, double cv) { return lambda * (0.75 - 1 / (5.21 * cv * cv / (lambda * lambda) + 2.39)); }

    /** Anteil des Wellenwiderstands des Verdrängers bei der Froude-Zahl der Länge [G]. */
    public static double wellenAnteil(double fn) { return fn <= 0.4 ? 1 : Math.max(0, 1 - (fn - 0.4) / 0.3); }

    /**
     * Gleichgewicht nach Savitsky für einen prismatischen Rumpf (Kräfte durch den Schwerpunkt): Trimm (Grad), λ, Tiefe des
     * Kiels am Spiegel (m) und C_V. masse in kg, lcg vom Spiegel (m), u in m/s. null, wenn kein λ den Druckpunkt trifft.
     */
    public static double[] gleichgewicht(double masse, double lcg, double b, double beta, double u, double rho) {
        double cv = u / Math.sqrt(Koerper.G * b), ziel = lcg / b;
        double lo = 0.05, hi = 20;
        if (druckpunkt(hi, cv) < ziel) return null;
        for (int i = 0; i < 100; i++) {
            double m = 0.5 * (lo + hi);
            if (druckpunkt(m, cv) < ziel) lo = m; else hi = m;
        }
        double lam = 0.5 * (lo + hi);
        double clbSoll = masse * Koerper.G / (0.5 * rho * u * u * b * b);
        double tl = 0.01, th = 30;
        for (int i = 0; i < 100; i++) {
            double t = 0.5 * (tl + th);
            if (clb(cl0(t, lam, cv), beta) < clbSoll) tl = t; else th = t;
        }
        double tau = 0.5 * (tl + th), tr = Math.toRadians(tau);
        double lk = lam * b + b * Math.tan(Math.toRadians(beta)) / (2 * Math.PI * Math.tan(tr));
        return new double[] {tau, lam, lk * Math.sin(tr), cv};
    }

    /** Anteil w des Gleitens bei C_V [G]: glatt von 0,5 bis 1,5. */
    public static double anteil(double cv) { double x = Math.max(0, Math.min(1, cv - 0.5)); return x * x * (3 - 2 * x); }

    /**
     * w · L (Welt, Kraft) und der Druckpunkt (Welt) für den Körper k im Wasser wa zur Zeit t, dazu {@link #anteil}; buf, st: 6
     * und 2 double. Gibt false ohne Gleiten (keine Fahrt voraus, Spiegel trocken, w = 0).
     */
    boolean kraft(Koerper k, Wasser wa, double t, double[] fOut, double[] pOut, double[] buf, double[] st) {
        auftrieb = 0; anteil = 0; lambda = 0; lp = 0;
        double[] R = k.R;
        double fx = R[0], fy = R[3], fz = R[6], fh = Math.hypot(fx, fy);
        tau = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, fz))));
        if (fh < 1e-9) return false;
        wa.strom(k.o[0], k.o[1], t, st);
        fahrt = ((k.v[0] - st[0]) * fx + (k.v[1] - st[1]) * fy) / fh;
        cv = fahrt / Math.sqrt(Koerper.G * b);
        double[] sp = k.punktWelt(xSpiegel, 0, zKiel);
        wa.welle(sp[0], sp[1], 0, t, buf);
        tiefe = wa.pegel(sp[0], sp[1], t) + buf[0] - sp[2];
        anteil = anteil(cv);
        if (fahrt <= 0 || tiefe <= 0 || anteil <= 0) { anteil = 0; return false; }
        // λ ohne Grenze an der Länge: ist (rechnerisch) mehr als der Rumpf benetzt, trägt das zweite Glied wie die Verdrängung [G]
        // seit S7e2: der Anstellwinkel nimmt die Geschwindigkeit des Bodens senkrecht zum Kiel im Druckpunkt (des letzten
        // Schritts) mit, wie bei einer Tragfläche – das dämpft Tauchen und Stampfen (ohne schaukelte sich das Gleitprisma auf)
        double[] pd = k.punktWelt(xSpiegel + (lp0 > 0 ? lp0 : 0.5 * -2 * xSpiegel), 0, zKiel), vd = k.geschwindigkeitAn(pd);
        double rx = vd[0] - st[0], ry = vd[1] - st[1], rz = vd[2];
        // seit S7e2 (nach L5e2-Vorlauf): die Normale steht senkrecht zum Kiel in der lotrechten Ebene durch den Kiel, nicht
        // längs der Hochachse – sonst kippte sie mit der Krängung und schob das Heck seitlich (Gieren, das die Drehung verschärft)
        double nx = -fz * fx / fh, ny = -fz * fy / fh, nz = fh;
        double vn = rx * nx + ry * ny + rz * nz, vf = rx * R[0] + ry * R[3] + rz * R[6];
        double te = Math.max(TAU_MIN, Math.toDegrees(Math.atan2(-vn, Math.max(1e-6, vf)))), tr = Math.toRadians(Math.max(TAU_MIN, tau));
        double lk = tiefe / Math.sin(tr), lc = Math.max(0, lk - b * Math.tan(Math.toRadians(beta)) / (Math.PI * Math.tan(tr)));
        lambda = (lk + lc) / (2 * b);
        auftrieb = 0.5 * wa.dichte() * fahrt * fahrt * b * b * clb(cl0(te, lambda, cv), beta);
        lp = Math.min(-2 * xSpiegel, druckpunkt(lambda, cv) * b);
        lp0 = lp;
        double f = anteil * auftrieb;
        fOut[0] = f * nx;
        fOut[1] = f * ny;
        fOut[2] = f * nz;
        double[] p = k.punktWelt(xSpiegel + lp, 0, zKiel);
        pOut[0] = p[0]; pOut[1] = p[1]; pOut[2] = p[2];
        return true;
    }
}
