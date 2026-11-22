package com.dan.core.szene;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Brandung an den Böschungen (seit P13c, Werkbuch HalligHooge Teil A, 12.14; F160 A, F162 A): je Bild aus den
 * {@link Kanten}, dem Wasserstand und dem Seegang die Brecher als Netz, der Auflauf als Wasserfilm auf dem Hang und die
 * Bündel der Gischt. Ohne Grafik und ohne Zustand: alles ist eine Funktion der Modellzeit (gleiche Zeit, gleiches Bild).
 * <p>
 * Je Abschnitt, der im Umkreis {@link #weite} um das Auge liegt, an dem Wasser steht (Fuß unter, Krone über dem Wasser)
 * und auf den die Wellen zulaufen:
 * <ul>
 * <li><b>Wellenfolge:</b> Periode Tp, Ankunft längs der Laufrichtung mit c = √(g h) am Fuß, so dass ein Kamm schräg an
 * der Kante entlangläuft. Höhe je Welle nach Rayleigh aus H_s am Ort (tiefenbegrenzt: H_s ≤ 0,5 h + 0,1 H_s [G]), längs
 * des Kamms in Blöcken von 25 m (wie {@code com.dan.surf.WaveTrain.crestLength}) linear überblendet.</li>
 * <li><b>Typ</b> nach Battjes aus ξ₀ = tan α / √(H/L₀) ({@link Brecherform#typ}); Form aus {@link Brecherform}: erst
 * ungebrochen, dann brechend, dann Walze (Anteile der Periode [G]).</li>
 * <li><b>Auflauf</b> nach EurOtop 2018 (Gl. 5.3/5.4): Ru2% = 1,65 γ_f ξ_m−1,0 H_s, höchstens γ_f (4 − 1,5/√ξ) H_s, mit
 * T_m−1,0 = Tp/1,1; je Welle Ru = Ru2% · H / H2% (H2% = 1,40 H_s nach Rayleigh). Über der Krone: Überlauf (nur gezeigt).</li>
 * <li><b>Gischt:</b> beim Aufschlag (Sturz) und am Hang (Reflexion, Brandung) ein Bündel Spritzwasser, ab Bft 8 dazu eine
 * Fahne vom Kamm; Tropfen wie {@code com.dan.spray.Parcel} (Schwerkraft, Reibung zum Wind), auf der Karte gerechnet.</li>
 * </ul>
 * Raum der Ausgabe: x = Ost − e0, y = Höhe (m NHN), z = n0 − Nord (wie {@code core.szene.Bauten}).
 */
public final class Brandung {
    /** Wasserstand und Seegang am Ort (UTM); {@link #KEIN}: kein Wasser. Seegang {Hs, Tp, cos, sin (wohin), mss, diss} oder null. */
    public interface Wasser {
        float eta(double ost, double nord);
        double[] seegang(double ost, double nord);
    }

    public static final float KEIN = -1.0e30f;
    /** Floats je Scheitel: x, y, z, Schaum, nx, ny, nz, Art (0 Brecher, 1 Film, sonst dünn = 2 + Anteil). */
    public static final int SCHEITEL = 8;
    /** Floats je Bündel der Gischt (wie {@code gischt.vert}). */
    public static final int BUENDEL = 16;
    public static final int SPRITZ = 0, FAHNE = 2;

    /** Umkreis um das Auge (m), Punkte je Form, Abschnitte und Bündel höchstens [G]. */
    public double weite = 1200;
    /** Blickfeld: Richtung des Blicks (Einheitsvektor Ost, Nord) und cos des halben Winkels; Abschnitte näher als 40 m zählen immer [G]. */
    public double blickOst = 0, blickNord = 1, blickCos = -1;
    public int punkte = 40, abschnitteMax = 500, buendelMax = 600;
    /** Blocklänge längs des Kamms (m), Anteile der Periode: ungebrochen bis, brechend bis, Walze bis [G]. */
    public double block = 25, phAnlauf = 0.5, phBrechen = 0.75, phWalze = 0.85;
    /** Gischt: Lebenszeit (s), Tropfen je Bündel, Wind ab dem Fahnen wehen (m/s) [G]. */
    public double gischtLeben = 2.5, fahneAb = 17;
    public int tropfen = 48;
    public final double e0, n0;

    // Ergebnis des letzten Bilds
    public float[] netz = new float[0];
    public int scheitel;
    public float[] buendel = new float[0];
    public int buendelZahl;
    /** Zuletzt: aktive Abschnitte, je Typ (Index wie {@link Brecherform#typ}), Rechenzeit (ms). */
    public int aktiv;
    public final int[] jeTyp = new int[5];
    public double ms;
    /** Zuletzt je aktivem Abschnitt {Kante, Typ, ξ₀, H, Ru2%, Ru dieser Welle, Hs am Ort, Tp} – für die Prüfung. */
    public final List<double[]> protokoll = new ArrayList<>();
    public boolean protokollieren;

    public Brandung(double e0, double n0) {
        this.e0 = e0;
        this.n0 = n0;
    }

    // ------------------------------------------------------------------ Formeln (öffentlich für die Prüfung)

    /** Zufall 0 … 1 aus zwei Zahlen (splitmix64). */
    public static double zufall(long a, long b) {
        long z = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L + 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return ((z >>> 11) + 0.5) / (double) (1L << 53);
    }

    /** Höhe nach Rayleigh mit P(H &gt; x) = exp(−2 x² / H_s²). */
    public static double rayleigh(double hs, double u) { return hs * Math.sqrt(-Math.log(u) / 2); }

    /** H_s am Fuß, tiefenbegrenzt [G]. */
    public static double hsFuss(double hs, double hFuss) { return Math.min(hs, 0.5 * Math.max(hFuss, 0) + 0.1 * hs); }

    /** Ru2% nach EurOtop 2018, Gl. 5.3 mit der Grenze 5.4 (γ_b = γ_β = 1): H_s, Tp, tan α, γ_f. */
    public static double ru2(double hs, double tp, double neigung, double rau) {
        if (!(hs > 0) || !(tp > 0)) return 0;
        double tm = tp / 1.1, l = 9.81 * tm * tm / (2 * Math.PI), xi = neigung / Math.sqrt(hs / l);
        return hs * Math.min(1.65 * rau * xi, rau * (4 - 1.5 / Math.sqrt(xi)));
    }

    /** Zwei unabhängige Normalverteilte (Box-Muller) für Welle n und Block k. */
    static double[] normal2(long n, long k) {
        double u1 = zufall(n, 2 * k), u2 = zufall(n, 2 * k + 1), r = Math.sqrt(-2 * Math.log(u1));
        return new double[] {r * Math.cos(2 * Math.PI * u2), r * Math.sin(2 * Math.PI * u2)};
    }

    /**
     * Wellenhöhe der Welle n am Ort längs des Kamms ψ (m) mit H_s: H = H_s/2 · √(X² + Y²) mit X, Y normalverteilt ist
     * Rayleigh mit P(H &gt; x) = exp(−2x²/H_s²). Zwischen den Blöcken werden X und Y überblendet und auf die Streuung 1
     * zurückgeführt, so bleibt die Verteilung an jedem Ort Rayleigh (eine lineare Überblendung der Höhen machte die
     * hohen Wellen seltener: Ru2% 11 % zu klein, Lauf in der Arbeitsumgebung 20.11.).
     */
    public double hoehe(double hs, long n, double psi) {
        double b = psi / block, f = b - Math.floor(b);
        long k = (long) Math.floor(b);
        double[] a = normal2(n, k), c = normal2(n, k + 1);
        double w = Math.sqrt((1 - f) * (1 - f) + f * f);
        double x = ((1 - f) * a[0] + f * c[0]) / w, y = ((1 - f) * a[1] + f * c[1]) / w;
        return 0.5 * hs * Math.sqrt(x * x + y * y);
    }

    // ------------------------------------------------------------------ je Bild

    private float[] nz = new float[1 << 16];
    private float[] bz = new float[1 << 12];
    private int nn, bn;

    /**
     * Rechnet das Bild zur Modellzeit t für das Auge (UTM, Höhe) mit dem Wind (U₁₀ Ost, Nord in m/s). Ergebnis in
     * {@link #netz}, {@link #scheitel}, {@link #buendel}, {@link #buendelZahl}.
     */
    public void rechne(Kanten kanten, Wasser w, double t, double augeOst, double augeNord, double windU, double windV) {
        long t0 = System.nanoTime();
        nn = 0;
        bn = 0;
        aktiv = 0;
        java.util.Arrays.fill(jeTyp, 0);
        protokoll.clear();
        if (kanten != null) {
            // die nächsten Abschnitte zuerst, höchstens abschnitteMax
            List<Kanten.Kante> l = kanten.kanten;
            int n = l.size();
            double[] ab = new double[n];
            Integer[] ix = new Integer[n];
            int m = 0;
            for (int i = 0; i < n; i++) {
                Kanten.Kante k = l.get(i);
                double d = Math.hypot(k.ost() - augeOst, k.nord() - augeNord);
                if (d > weite) continue;
                if (d > 40 && ((k.ost() - augeOst) * blickOst + (k.nord() - augeNord) * blickNord) / d < blickCos) continue;
                ab[i] = d;
                ix[m++] = i;
            }
            java.util.Arrays.sort(ix, 0, m, (a, b) -> Double.compare(ab[a], ab[b]));
            for (int q = 0; q < m && aktiv < abschnitteMax; q++) abschnitt(l.get(ix[q]), ix[q], w, t, windU, windV);
        }
        scheitel = nn / SCHEITEL;
        buendelZahl = bn / BUENDEL;
        netz = nz;
        buendel = bz;
        ms = (System.nanoTime() - t0) / 1e6;
    }

    private void abschnitt(Kanten.Kante k, int nr, Wasser w, double t, double wu, double wv) {
        double nx = k.nx(), ny = k.ny();
        double[] s = w.seegang(k.ost() + 15 * nx, k.nord() + 15 * ny);
        if (s == null || !(s[0] > 0.05) || !(s[1] > 0.5)) return;
        double dl = Math.hypot(s[2], s[3]);
        if (dl <= 0) return;
        double dx = s[2] / dl, dy = s[3] / dl;
        double zu = -(dx * nx + dy * ny);                              // läuft die See auf den Hang zu?
        if (zu < 0.15) return;
        float eta = w.eta(k.ost() + 10 * nx, k.nord() + 10 * ny);
        if (eta == KEIN || eta < k.fuss() + 0.05 || eta > k.krone() - 0.05) return;
        double hf = eta - k.fuss(), tan = k.neigung(), tp = s[1];
        double hs = hsFuss(s[0], hf) * Math.sqrt(zu);                  // schräg anlaufend: weniger Energie auf den Hang [G]
        if (hs < 0.05) return;
        aktiv++;
        // Wellenfolge: Ankunft längs der Laufrichtung, Block längs des Kamms
        double c = Math.sqrt(9.81 * Math.max(0.5, Math.min(5, hf)));
        double chi = (k.ost() * dx + k.nord() * dy) / c, psi = -k.ost() * dy + k.nord() * dx;
        double tau = t - chi, phase = tau / tp;
        long wn = (long) Math.floor(phase);
        double ph = phase - wn;
        double H = Math.min(hoehe(hs, wn, psi), 0.78 * hf + 0.05);
        double xi = Brecherform.iribarren(tan, Math.max(H, 0.02), tp);
        int typ = Brecherform.typ(xi);
        jeTyp[typ]++;
        double ru2 = ru2(hs, tp, tan, k.rau()), ru = ru2 * H / (1.398 * hs);
        if (protokollieren) protokoll.add(new double[] {nr, typ, xi, H, ru2, ru, hs, tp});
        // Wasserlinie: vom Fuß landwärts bis zur Höhe η
        double uw = hf / tan;
        double wlo = k.ost() - nx * uw, wln = k.nord() - ny * uw;
        double tx = -ny, ty = nx;
        // ---- Brecher
        double back = Math.min(30, 0.4 * c * tp), front = 0.5 * back;
        double db = typ <= Brecherform.STURZ ? Math.min(H / 0.78, hf) / tan : 0.3 * H / tan;   // Brechpunkt seewärts der Wasserlinie (m)
        Brecherform.Curve cu = null;
        double uc = 0;
        if (ph < phAnlauf) {
            double near = ph / phAnlauf;
            uc = -db - (1 - near) * 0.5 * c * tp * phAnlauf;
            cu = Brecherform.unbroken(H, Math.max(0.05, -uc * tan + 0.0), back, front, near);
        } else if (ph < phBrechen) {
            double p = (ph - phAnlauf) / (phBrechen - phAnlauf);
            uc = -db * (1 - p);
            double hl = Math.max(0.05, -uc * tan);
            cu = switch (typ) {
                case Brecherform.SCHWALL -> Brecherform.spilling(H, hl, back, front, p);
                case Brecherform.STURZ -> Brecherform.plunging(H, hl, back, front, p);
                default -> Brecherform.collapsing(H, hl, back, front, p);
            };
        } else if (ph < phWalze) {
            double p = (ph - phBrechen) / (phWalze - phBrechen);
            cu = Brecherform.bore(Math.max(0.05, 0.6 * H * (1 - p)), 0.1, back * 0.5, front);
        }
        if (cu != null) strip(cu, wlo, wln, nx, ny, tx, ty, eta, uc, tan, uw);
        // ---- Auflauf (Wasserfilm): nach dem Brechen hinauf, dann zurück [G]
        if (ph >= phBrechen - 0.05) {
            double fs = (ph - (phBrechen - 0.05)) / (1 - (phBrechen - 0.05));
            double r = fs < 0.4 ? ru * Math.sin(0.5 * Math.PI * fs / 0.4) : ru * (1 - Math.pow((fs - 0.4) / 0.6, 2));
            double rmax = k.krone() - eta;                              // darüber: Überlauf, der Film endet an der Krone
            film(wlo, wln, nx, ny, tx, ty, eta, tan, Math.min(r, rmax) / tan, fs);
        }
        // ---- Gischt: Bündel der Wellen, deren Aufschlag (bei ph = phBrechen) in der Lebenszeit liegt
        for (long q = wn; q >= wn - 2; q--) {
            double ta = chi + (q + phBrechen) * tp, alter = t - ta;
            if (alter < 0 || alter > gischtLeben) continue;
            double Hq = Math.min(hoehe(hs, q, psi), 0.78 * hf + 0.05);
            int tq = Brecherform.typ(Brecherform.iribarren(tan, Math.max(Hq, 0.02), tp));
            double hj = tq == Brecherform.SCHWALL ? 0.25 * Hq : tq == Brecherform.STURZ ? 0.8 * Hq : 1.2 * Hq;   // Höhe des Schwalls [G]
            if (hj > 0.15) buendel(SPRITZ, wlo, wln, eta + 0.1, -nx, -ny, Math.sqrt(2 * 9.81 * hj), alter, tx, ty, wu, wv, nr * 131L + q);
            double U = Math.hypot(wu, wv);
            if (U > fahneAb) buendel(FAHNE, wlo + nx * 0.5 * c * tp * 0.2, wln + ny * 0.5 * c * tp * 0.2, eta + 0.6 * Hq, 0, 0, 1.5, alter, tx, ty, wu, wv, nr * 131L + q + 7);
        }
    }

    /** Ein Querschnitt als Band längs der Kante (±2,6 m), Dreiecke direkt. */
    private void strip(Brecherform.Curve cu, double wlo, double wln, double nx, double ny, double tx, double ty, double eta, double uc, double tan, double uw) {
        int P = punkte;
        float[] px = new float[P], py = new float[P], pz = new float[P], fo = new float[P], th = new float[P], qn = new float[3 * P];
        double[] uu = new double[P], hh = new double[P];
        for (int a = 0; a < P; a++) {
            int kk = Math.min(Brecherform.K - 1, (int) Math.round(a * (Brecherform.K - 1) / (double) (P - 1)));
            double u = uc + cu.x[kk];                                    // landwärts von der Wasserlinie (m)
            double z = eta + cu.z[kk];
            double grund = u > -uw ? eta + u * tan : eta - uw * tan;     // Hang oder Fläche vor dem Fuß
            z = Math.max(z, grund + 0.01);
            uu[a] = u;
            hh[a] = z;
            fo[a] = (float) cu.foam[kk];
            th[a] = (float) cu.thin[kk];
        }
        for (int a = 0; a < P; a++) {
            int a0 = Math.max(0, a - 1), a1 = Math.min(P - 1, a + 1);
            double du = uu[a1] - uu[a0], dz = hh[a1] - hh[a0], l = Math.hypot(du, dz);
            double nu = l > 1e-9 ? -dz / l : 0, nzv = l > 1e-9 ? du / l : 1;  // Normale in (landwärts, oben)
            // landwärts ist −n: Normale in Ost/Nord = −n · nu
            qn[3 * a] = (float) (-nx * nu);
            qn[3 * a + 1] = (float) nzv;
            qn[3 * a + 2] = (float) (-ny * nu);
            px[a] = (float) (wlo - nx * uu[a] - e0);
            pz[a] = (float) (n0 - (wln - ny * uu[a]));
            py[a] = (float) hh[a];
        }
        double hl = Kanten.LAENGE / 2 + 0.1;
        for (int a = 0; a + 1 < P; a++) {
            int[] ia = {a, a + 1, a + 1, a, a + 1, a};
            double[] sa = {-hl, -hl, hl, -hl, hl, hl};
            for (int v = 0; v < 6; v++) {
                int b = ia[v];
                put((float) (px[b] + tx * sa[v]), py[b], (float) (pz[b] - ty * sa[v]), fo[b], qn[3 * b], qn[3 * b + 1], -qn[3 * b + 2], 2 + th[b]);
            }
        }
    }

    /** Der Wasserfilm des Auflaufs von der Wasserlinie (−0,5 m) bis u_r (m landwärts), 6 Reihen. */
    private void film(double wlo, double wln, double nx, double ny, double tx, double ty, double eta, double tan, double ur, double fs) {
        if (ur <= 0.05) return;
        int R = 6;
        double hl = Kanten.LAENGE / 2 + 0.1, nl = Math.hypot(tan, 1);
        float nxo = (float) (nx * tan / nl), nyv = (float) (1 / nl), nzs = (float) (-ny * tan / nl);   // hangab und oben; z = −Nord
        for (int r = 0; r < R; r++) {
            double u0 = -0.5 + (ur + 0.5) * r / R, u1 = -0.5 + (ur + 0.5) * (r + 1) / R;
            double[] us = {u0, u1, u1, u0, u1, u0};
            double[] sa = {-hl, -hl, hl, -hl, hl, hl};
            for (int v = 0; v < 6; v++) {
                double u = us[v], rel = Math.max(0, Math.min(1, (u + 0.5) / (ur + 0.5)));
                double y = eta + u * tan + 0.02 + 0.04 * (1 - rel);
                float schaum = (float) Math.min(1, (fs < 0.4 ? 0.9 : 0.6) * (0.3 + 0.7 * rel));
                put((float) (wlo - nx * u + tx * sa[v] - e0), (float) y, (float) (n0 - (wln - ny * u + ty * sa[v])), schaum, nxo, nyv, nzs, 1);
            }
        }
    }

    private void put(float x, float y, float z, float f, float a, float b, float c, float d) {
        if (nn + SCHEITEL > nz.length) nz = java.util.Arrays.copyOf(nz, nz.length * 2);
        nz[nn++] = x; nz[nn++] = y; nz[nn++] = z; nz[nn++] = f;
        nz[nn++] = a; nz[nn++] = b; nz[nn++] = c; nz[nn++] = d;
    }

    /**
     * Ein Bündel (Aufbau wie {@code gischt.vert}): Ort, Alter, Startgeschwindigkeit (hangauf und nach oben), Wind,
     * Reibung, Streuung, Größe, Art, Zahl der Tropfen, Samen.
     */
    private void buendel(int art, double o, double n, double y, double rx, double ry, double v0, double alter, double tx, double ty, double wu, double wv, long samen) {
        if (bn / BUENDEL >= buendelMax) return;
        if (bn + BUENDEL > bz.length) bz = java.util.Arrays.copyOf(bz, bz.length * 2);
        double k = art == SPRITZ ? 0.3 : 1.2, streu = art == SPRITZ ? 2.0 : 3.0;
        double vh = art == SPRITZ ? 0.35 * v0 : 0;
        float[] b = {(float) (o - e0), (float) y, (float) (n0 - n), (float) alter,
                (float) (rx * vh), (float) (art == SPRITZ ? v0 : v0), (float) (-ry * vh), (float) k,
                (float) wu, (float) -wv, (float) streu, (float) (art == SPRITZ ? 0.5 * streu : 1.0),
                (float) (tx), (float) (-ty), (float) (art == SPRITZ ? 0.06 : 0.10), (float) (art * 65536 + (int) (zufall(samen, 3) * 65535))};
        System.arraycopy(b, 0, bz, bn, BUENDEL);
        bn += BUENDEL;
    }
}
