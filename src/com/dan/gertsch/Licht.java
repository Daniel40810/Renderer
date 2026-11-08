package com.dan.gertsch;

import java.util.Arrays;

/**
 * Licht im Dickicht, auf dem Prozessor:
 * <ul>
 * <li><b>Sonne:</b> Richtung aus Höhe und Richtung, Farbe aus der Luftmasse nach Kasten und Young (1989) mit einer
 * Auslöschung je Farbe [G].</li>
 * <li><b>Schatten:</b> eine Karte entlang der Sonne (orthografisch). Jedes Blatt, jeder Stamm und Ast trägt dort ein, wie
 * weit er zur Sonne hin liegt; ein Punkt ist besonnt, wenn nichts weiter zur Sonne hin über ihm liegt. Aus den Lücken
 * im Laub entstehen so die scharfen Lichtflecken, die Gertsch malte (3 × 3 Abtastungen, leicht weich).</li>
 * <li><b>Himmel:</b> diffuses Licht von oben, gedämpft nach Beer–Lambert mit der Blattfläche darüber:
 * exp(−k · LAI über dem Punkt), k = {@link #ausloeschung} [G, 0,5 für Laub ohne Vorzugsrichtung].</li>
 * </ul>
 */
public final class Licht {
    /** Sonnenhöhe (Grad) und Richtung zur Sonne (Grad: 0 = vor der Kamera, also Gegenlicht; 90 = rechts; ±180 = hinter ihr). */
    public double sonneHoehe = 28, sonneRichtung = -130;
    /** Stärke von Sonne und Himmel (relativ; die Belichtung gleicht das Ganze aus). */
    public double sonneStaerke = 1.0, himmelStaerke = 0.25;
    /** Auslöschung des Himmelslichts je Blattfläche (Beer–Lambert) [G]. */
    public double ausloeschung = 0.5;
    /** Kante der Schattenkarte (Texel). */
    public int schattenKante = 3072;

    /** Einheitsvektor zur Sonne, Farbe von Sonne und Himmel (linear). */
    public final double[] zurSonne = new double[3], sonneFarbe = new double[3], himmelFarbe = new double[3];

    // Schattenkarte
    private float[] karte;
    private int n;
    private double u0, v0, texel;
    private final double[] bu = new double[3], bv = new double[3];
    // Himmel: Blattfläche über jeder Zelle
    private float[] lai;
    private int gx, gz, gy;
    private double x0, z0;
    static final double ZELLE = 0.5, SCHICHT = 0.5;

    /** Richtung und Farben aus Höhe und Richtung. */
    public Licht richten() {
        double h = Math.toRadians(sonneHoehe), a = Math.toRadians(sonneRichtung);
        zurSonne[0] = Math.sin(a) * Math.cos(h);
        zurSonne[1] = Math.sin(h);
        zurSonne[2] = Math.cos(a) * Math.cos(h);
        double hg = Math.max(0.5, sonneHoehe), m = 1 / (Math.sin(Math.toRadians(hg)) + 0.50572 * Math.pow(hg + 6.07995, -1.6364));
        double[] tau = {0.09, 0.14, 0.27};   // [G]: Rayleigh und Dunst für Rot, Grün, Blau
        for (int i = 0; i < 3; i++) sonneFarbe[i] = sonneStaerke * Math.exp(-tau[i] * m) / Math.exp(-tau[i]);
        double hk = himmelStaerke * Math.sqrt(Math.max(0.05, Math.sin(h)));
        himmelFarbe[0] = 0.62 * hk;
        himmelFarbe[1] = 0.76 * hk;
        himmelFarbe[2] = 1.00 * hk;
        return this;
    }

    /** Richtet das Licht und rechnet Schattenkarte und Himmel für das Dickicht. */
    public Licht vorbereiten(Dickicht d) {
        richten();
        schatten(d);
        himmel(d);
        return this;
    }

    // ------------------------------------------------------------------ Schatten

    void schatten(Dickicht d) {
        double[] w = zurSonne;
        // u waagerecht quer zur Sonne, v = w × u
        double ux = w[2], uz = -w[0], l = Math.hypot(ux, uz);
        if (l < 1e-6) { ux = 1; uz = 0; l = 1; }
        bu[0] = ux / l;
        bu[1] = 0;
        bu[2] = uz / l;
        bv[0] = w[1] * bu[2] - w[2] * bu[1];
        bv[1] = w[2] * bu[0] - w[0] * bu[2];
        bv[2] = w[0] * bu[1] - w[1] * bu[0];
        // Grenzen über alle Zweige und Glieder
        double mu = Double.MAX_VALUE, xu = -Double.MAX_VALUE, mv = Double.MAX_VALUE, xv = -Double.MAX_VALUE;
        for (Dickicht.Zweig z : d.zweige) {
            double u = z.x * bu[0] + z.y * bu[1] + z.z * bu[2], v = z.x * bv[0] + z.y * bv[1] + z.z * bv[2];
            mu = Math.min(mu, u - z.radius); xu = Math.max(xu, u + z.radius);
            mv = Math.min(mv, v - z.radius); xv = Math.max(xv, v + z.radius);
        }
        for (Dickicht.Glied g : d.glieder)
            for (int e = 0; e < 2; e++) {
                double x = e == 0 ? g.x0 : g.x1, y = e == 0 ? g.y0 : g.y1, z = e == 0 ? g.z0 : g.z1;
                double u = x * bu[0] + y * bu[1] + z * bu[2], v = x * bv[0] + y * bv[1] + z * bv[2];
                mu = Math.min(mu, u - 0.5); xu = Math.max(xu, u + 0.5);
                mv = Math.min(mv, v - 0.5); xv = Math.max(xv, v + 0.5);
            }
        if (mu > xu) { mu = -1; xu = 1; mv = -1; xv = 1; }
        n = schattenKante;
        texel = Math.max(xu - mu, xv - mv) / n;
        u0 = mu;
        v0 = mv;
        karte = new float[n * n];
        Arrays.fill(karte, Float.NEGATIVE_INFINITY);
        Dickicht.Blatt b = new Dickicht.Blatt();
        for (Dickicht.Zweig z : d.zweige)
            for (int i = 0; i < z.blaetter; i++) {
                z.blatt(i, b);
                double cos = Math.abs(b.nx * w[0] + b.ny * w[1] + b.nz * w[2]);
                double rad = 0.5 * Math.sqrt(b.laenge * b.breite * Math.max(0.2, cos));
                scheibe(b.x, b.y, b.z, rad);
            }
        for (Dickicht.Glied g : d.glieder) {
            double dx = g.x1 - g.x0, dy = g.y1 - g.y0, dz = g.z1 - g.z0, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int k = Math.max(1, (int) Math.ceil(len / (texel * 0.7)));
            for (int j = 0; j <= k; j++) {
                double t = j / (double) k;
                scheibe(g.x0 + dx * t, g.y0 + dy * t, g.z0 + dz * t, g.r0 + (g.r1 - g.r0) * t);
            }
        }
    }

    private void scheibe(double x, double y, double z, double rad) {
        double u = (x * bu[0] + y * bu[1] + z * bu[2] - u0) / texel, v = (x * bv[0] + y * bv[1] + z * bv[2] - v0) / texel;
        float dd = (float) (x * zurSonne[0] + y * zurSonne[1] + z * zurSonne[2]);
        double rt = Math.max(0.5, rad / texel);
        int i0 = (int) Math.floor(u - rt), i1 = (int) Math.ceil(u + rt), j0 = (int) Math.floor(v - rt), j1 = (int) Math.ceil(v + rt);
        double r2 = rt * rt;
        for (int j = Math.max(0, j0); j <= Math.min(n - 1, j1); j++) {
            double dv = j + 0.5 - v;
            for (int i = Math.max(0, i0); i <= Math.min(n - 1, i1); i++) {
                double du = i + 0.5 - u;
                if (du * du + dv * dv > r2) continue;
                int o = j * n + i;
                if (karte[o] < dd) karte[o] = dd;
            }
        }
    }

    /**
     * Sichtbarkeit der Sonne am Punkt, 0..1: 3 × 3 Abtastungen im Abstand eines Texels, jede bilinear aus vier Texeln
     * (sonst zeichnen sich die Texel am nahen Boden als Kästchen ab). Ohne Karte: 1.
     */
    public double sonne(double x, double y, double z) {
        if (karte == null) return 1;
        double u = (x * bu[0] + y * bu[1] + z * bu[2] - u0) / texel - 0.5, v = (x * bv[0] + y * bv[1] + z * bv[2] - v0) / texel - 0.5;
        double dd = x * zurSonne[0] + y * zurSonne[1] + z * zurSonne[2] + 1.5 * texel + 0.02;
        int ci = (int) Math.floor(u), cj = (int) Math.floor(v);
        double fu = u - ci, fv = v - cj;
        // frei je Texel im Feld 4 × 4 um (ci, cj)
        double[] f = new double[16];
        for (int j = 0; j < 4; j++)
            for (int i = 0; i < 4; i++) {
                int ii = ci - 1 + i, jj = cj - 1 + j;
                f[j * 4 + i] = ii < 0 || jj < 0 || ii >= n || jj >= n || karte[jj * n + ii] <= dd ? 1 : 0;
            }
        // 3 × 3 bilineare Abtastungen = Gewichte (1 − fu, 1, 1, fu) × (1 − fv, 1, 1, fv) / 9
        double[] wu = {1 - fu, 1, 1, fu}, wv = {1 - fv, 1, 1, fv};
        double s = 0;
        for (int j = 0; j < 4; j++)
            for (int i = 0; i < 4; i++) s += wu[i] * wv[j] * f[j * 4 + i];
        return s / 9;
    }

    // ------------------------------------------------------------------ Himmel

    void himmel(Dickicht d) {
        double xmin = Double.MAX_VALUE, xmax = -Double.MAX_VALUE, zmin = Double.MAX_VALUE, zmax = -Double.MAX_VALUE, ymax = 1;
        for (Dickicht.Zweig z : d.zweige) {
            xmin = Math.min(xmin, z.x); xmax = Math.max(xmax, z.x);
            zmin = Math.min(zmin, z.z); zmax = Math.max(zmax, z.z);
            ymax = Math.max(ymax, z.y);
        }
        if (xmin > xmax) { lai = null; return; }
        x0 = xmin - 2;
        z0 = zmin - 2;
        gx = (int) Math.ceil((xmax - xmin + 4) / ZELLE);
        gz = (int) Math.ceil((zmax - zmin + 4) / ZELLE);
        gy = (int) Math.ceil((ymax + 1) / SCHICHT);
        float[] dichte = new float[gx * gz * gy];
        double flaeche = ZELLE * ZELLE;
        for (Dickicht.Zweig z : d.zweige) {
            int i = (int) ((z.x - x0) / ZELLE), k = (int) ((z.z - z0) / ZELLE), j = Math.min(gy - 1, Math.max(0, (int) (z.y / SCHICHT)));
            double a = z.blaetter * 0.7 * z.laenge * z.laenge * z.schmal / flaeche;
            dichte[(k * gx + i) * gy + j] += (float) a;
        }
        // weich in x und z (Licht kommt aus dem ganzen oberen Halbraum), dann von oben aufsummieren
        float[] weich = new float[dichte.length];
        int r = 3;
        for (int k = 0; k < gz; k++)
            for (int i = 0; i < gx; i++)
                for (int j = 0; j < gy; j++) {
                    double s = 0;
                    int c = 0;
                    for (int dk = -r; dk <= r; dk++)
                        for (int di = -r; di <= r; di++) {
                            int ii = i + di, kk = k + dk;
                            if (ii < 0 || kk < 0 || ii >= gx || kk >= gz) continue;
                            s += dichte[(kk * gx + ii) * gy + j];
                            c++;
                        }
                    weich[(k * gx + i) * gy + j] = (float) (s / Math.max(1, c));
                }
        lai = new float[weich.length];
        for (int k = 0; k < gz; k++)
            for (int i = 0; i < gx; i++) {
                float s = 0;
                for (int j = gy - 1; j >= 0; j--) {
                    int o = (k * gx + i) * gy + j;
                    lai[o] = s;   // Blattfläche über dieser Schicht
                    s += weich[o];
                }
            }
    }

    /** Anteil des Himmelslichts am Punkt, 0..1 (Blattfläche darüber trilinear zwischen den Zellmitten). */
    public double himmel(double x, double y, double z) {
        if (lai == null) return 1;
        double fx = (x - x0) / ZELLE - 0.5, fz = (z - z0) / ZELLE - 0.5, fy = y / SCHICHT - 0.5;
        int i = (int) Math.floor(fx), k = (int) Math.floor(fz), j = (int) Math.floor(fy);
        double ax = fx - i, az = fz - k, ay = fy - j, s = 0;
        for (int dk = 0; dk < 2; dk++)
            for (int di = 0; di < 2; di++)
                for (int dj = 0; dj < 2; dj++) {
                    int ii = Math.max(0, Math.min(gx - 1, i + di)), kk = Math.max(0, Math.min(gz - 1, k + dk)), jj = Math.max(0, Math.min(gy - 1, j + dj));
                    double wgt = (di == 0 ? 1 - ax : ax) * (dk == 0 ? 1 - az : az) * (dj == 0 ? 1 - ay : ay);
                    s += wgt * lai[(kk * gx + ii) * gy + jj];
                }
        if (fx < -1 || fz < -1 || fx > gx || fz > gz) return 1;
        return Math.exp(-ausloeschung * s);
    }

    /** Größe eines Texels der Schattenkarte (m). */
    public double texel() { return texel; }
}
