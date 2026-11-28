package com.dan.core.szene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Kielwasser der fahrenden Schiffe (Seegang Werkbuch, S7d, Datei 89): das Muster aus {@code com.dan.wake} (Bug als
 * Druckpunkt, Heck als Sog, Spur in Kurven, Schaum hinter dem Heck), aber mit der Dispersion bei endlicher Tiefe
 * ω² = g k tanh(k h) statt der Formeln für tiefes Wasser. Raum: x Ost, y Nord (UTM, m); Kurs von Nord im Uhrzeigersinn.
 * <p>
 * <b>Stationäre Phase bei endlicher Tiefe.</b> Eine Quelle mit der Fahrt V über Wasser der Tiefe h hält die Wellen mit
 * c(k) = V cos θ fest (θ: Richtung der Welle gegen die Fahrt); je θ löst Newton g tanh(K h) = K V² cos² θ nach der
 * Wellenzahl K(θ). Ein Punkt im Abstand r hinter der Quelle unter dem Winkel α hat die Phase Φ(θ) = r K(θ) cos(θ + α);
 * stationär ist sie für α = atan(K'/K) − θ. Im tiefen Wasser ist das tan α = tan θ / (1 + 2 tan² θ) mit dem Rand des
 * Keils bei 19,47°; gegen Fr_h = V/√(g h) → 1 weitet sich der Keil, über 1 gibt es keine Querwellen mehr und der Keil
 * hat den halben Winkel arcsin(1/Fr_h) (Havelock 1908). Je Quelle gibt es darum Tafeln:
 * <ul>
 * <li>K, K', K'', K''' über θ von θ₀ (0, überkritisch arccos(1/Fr_h)) bis θ₁ (K so groß, dass die Welle nicht mehr
 * zählt), {@link #NT} Stellen;</li>
 * <li>θ über α für die beiden Äste (quer: α steigt mit θ bis zum Rand des Keils; schräg: α fällt wieder), je
 * {@link #NA} Stellen gleich in α von 0 bis α_max.</li>
 * </ul>
 * Höhe je Ast nach der stationären Phase: (K/K_ref)² · exp(−(K b)²/4) · min(√(2π/|Φ''|), 1,547 (|Φ'''|/6)^(−1/3)) ·
 * cos(Φ ± π/4), K_ref = g/V², b die halbe Breite; jenseits des Keils klingt es ab wie in {@code wake}. Die Höhe ist auf
 * 0,12 · T · Fr² / (0,08 + Fr²) in einer Länge Abstand quer gesetzt (wie {@code wake}, an Messungen angepasst [G]).
 * <p>
 * <b>Kacheln.</b> {@link #kachel} rechnet Höhe, Schaum und die mittlere Wellenzahl genau wie {@code see_kiel.comp}
 * (dieselben Tafeln, linear zwischen den Stellen), auf {@link #hoehe} geht es ohne Tafel für α (Prüfungen).
 * <p>
 * Nur für einen Faden (in der Anwendung der Faden der Bilder).
 */
public final class Kielwasser {
    public static final String VERSION = "0.2";
    public static final double G = 9.81;
    /** Stellen der Tafeln je Quelle: über θ und je Ast über α. */
    public static final int NT = 256, NA = 256;
    /** Punkte der ausgedünnten Spur je Quelle (für die Karte). */
    public static final int SPUR = 96;
    /** Werte je Quelle für die Karte: Kopf, Tafel über θ (4 je Stelle), zwei Äste (je NA), Spur (4 je Punkt). */
    public static final int KOPF = 24, JE_QUELLE = KOPF + 4 * NT + 2 * NA + 4 * SPUR;

    /** Eine Quelle (ein fahrendes Schiff). Lage, Kurs und Fahrt setzt der Lauf ({@link #setze}). */
    public static final class Quelle {
        public final String name;
        public final double laenge, breite, tiefgang;
        public final boolean motor;
        public double x, y, kurs, fahrt, tiefe = 20;
        // Spur des Bugs: x, y, Weg (m), Zeit (s); neuester zuletzt
        double[] tx = new double[64], ty = new double[64], td = new double[64], tt = new double[64];
        int zahl;
        double weg;
        // Tafeln
        double tafelV = -1, tafelH = -1, th0, th1, alphaMax, thetaC, kc, ampNorm;
        final double[] tK = new double[NT], tK1 = new double[NT], tK2 = new double[NT], tK3 = new double[NT];
        final double[] astA = new double[NA], astB = new double[NA];
        boolean querAst;

        Quelle(String name, double laenge, double breite, double tiefgang, boolean motor) {
            this.name = name; this.laenge = laenge; this.breite = breite; this.tiefgang = tiefgang; this.motor = motor;
        }

        /** Lage des Bugs (x, y). */
        public double[] bug() {
            double h = Math.toRadians(kurs);
            return new double[] {x + Math.sin(h) * laenge / 2, y + Math.cos(h) * laenge / 2};
        }

        public double froude() { return fahrt / Math.sqrt(G * laenge); }

        public double froudeTiefe() { return fahrt / Math.sqrt(G * Math.max(0.05, tiefe)); }

        /** Wie lange die Spur zurückreicht (s) [G]. */
        public double alterMax() { return laenge > 50 ? 3000 : 240; }

        double abstand() { return Math.max(0.3, Math.min(laenge / 4, 25)); }

        void merke(double t) {
            double[] b = bug();
            if (zahl > 0) {
                double d = Math.hypot(b[0] - tx[zahl - 1], b[1] - ty[zahl - 1]);
                if (d < abstand()) return;
                weg += d;
            }
            if (zahl == tx.length) {
                int weg0 = 0;
                while (weg0 < zahl - 2 && (t - tt[weg0] > alterMax() || zahl - weg0 > 4000)) weg0++;
                if (weg0 > 0) {
                    System.arraycopy(tx, weg0, tx, 0, zahl - weg0);
                    System.arraycopy(ty, weg0, ty, 0, zahl - weg0);
                    System.arraycopy(td, weg0, td, 0, zahl - weg0);
                    System.arraycopy(tt, weg0, tt, 0, zahl - weg0);
                    zahl -= weg0;
                }
                if (zahl == tx.length) {
                    tx = Arrays.copyOf(tx, zahl * 2); ty = Arrays.copyOf(ty, zahl * 2);
                    td = Arrays.copyOf(td, zahl * 2); tt = Arrays.copyOf(tt, zahl * 2);
                }
            }
            tx[zahl] = b[0]; ty[zahl] = b[1]; td[zahl] = weg; tt[zahl] = t;
            zahl++;
        }

        /** Spur vergessen (nach einem Sprung). */
        public void vergiss() { zahl = 0; weg = 0; }
    }

    private final List<Quelle> quellen = new ArrayList<>();
    private double zeit;
    /** Stärke des Musters und des Schaums [G]. */
    public double staerke = 1, schaumStaerke = 1;

    public Quelle neu(String name, double laenge, double breite, double tiefgang, boolean motor) {
        Quelle q = new Quelle(name, laenge, breite, tiefgang, motor);
        quellen.add(q);
        return q;
    }

    public List<Quelle> quellen() { return quellen; }

    public double zeit() { return zeit; }

    /** Lage, Kurs (Grad), Fahrt über Wasser (m/s) und Tiefe am Bug (m) einer Quelle zur Zeit t; merkt die Spur. */
    public void setze(Quelle q, double x, double y, double kurs, double fahrt, double tiefe, double t) {
        if (t < zeit - 1e-9 || t - zeit > 60) for (Quelle p : quellen) p.vergiss();   // Sprung der Zeit: Spuren neu
        zeit = t;
        q.x = x; q.y = y; q.kurs = kurs; q.fahrt = fahrt; q.tiefe = Math.max(0.3, tiefe);
        q.merke(t);
    }

    // ------------------------------------------------------------------ Dispersion

    /** Wellenzahl K mit g tanh(K h) = K (V cos θ)²; NaN, wenn V cos θ ≥ √(g h) (keine Lösung). */
    public static double wellenzahl(double c, double h) {
        if (c * c >= G * h * (1 - 1e-9)) return Double.NaN;
        double k = G / (c * c);                                              // tiefes Wasser
        if (k * h < 3) k = Math.max(k, Math.sqrt(3 * (1 - c * c / (G * h))) / h);
        for (int i = 0; i < 60; i++) {
            double t = Math.tanh(k * h), f = G * t - k * c * c, df = G * h * (1 - t * t) - c * c;
            double kn = k - f / df;
            if (!(kn > 0)) kn = 0.5 * k;
            if (Math.abs(kn - k) < 1e-12 * k) { k = kn; break; }
            k = kn;
        }
        return k;
    }

    /** Halber Winkel des Keils (Grad) für Fahrt V und Tiefe h, aus der Tafel (größtes α). */
    public static double keilwinkel(double v, double h) {
        Quelle q = new Quelle("Prüfung", 10, 3, 1, false);
        q.fahrt = v;
        q.tiefe = h;
        tafel(q, 1e9);
        return Math.toDegrees(q.alphaMax);
    }

    static double kMaxTafel(Quelle q) { return Math.max(200 * G / (q.fahrt * q.fahrt), 40); }

    /** Die Tafeln einer Quelle für ihre Fahrt und Tiefe (neu bei 2 % Änderung). */
    static void tafel(Quelle q, double kMax) {
        double v = Math.max(0.2, q.fahrt), h = q.tiefe;
        if (q.tafelV > 0 && Math.abs(v - q.tafelV) < 0.02 * q.tafelV && Math.abs(h - q.tafelH) < 0.02 * q.tafelH) return;
        q.tafelV = v;
        q.tafelH = h;
        double frh = v / Math.sqrt(G * h);
        double th0 = frh < 1 ? 0 : Math.acos(1 / frh) + 1e-4;
        // θ₁: K = kMaxTafel (kürzere Wellen zählen nicht)
        double km = Math.min(kMax, kMaxTafel(q)), th1 = th0 + 1e-3;
        double lo = th0, hi = Math.PI / 2 - 1e-6;
        for (int i = 0; i < 60; i++) {
            double m = 0.5 * (lo + hi), k = wellenzahl(v * Math.cos(m), h);
            if (Double.isNaN(k) || k < km) lo = m; else hi = m;
        }
        th1 = lo;
        q.th0 = th0;
        q.th1 = th1;
        double dth = (th1 - th0) / (NT - 1);
        for (int i = 0; i < NT; i++) {
            double th = th0 + i * dth;
            double k = wellenzahl(v * Math.cos(th), h);
            q.tK[i] = Double.isNaN(k) ? 0 : k;
        }
        // Ableitungen nach θ: zentrale Differenzen (am Rand einseitig); K''' aus K''
        for (int i = 0; i < NT; i++) {
            int a = Math.max(0, i - 1), b = Math.min(NT - 1, i + 1);
            q.tK1[i] = (q.tK[b] - q.tK[a]) / ((b - a) * dth);
        }
        if (frh < 1) q.tK1[0] = 0;                                          // K'(0) = 0 (symmetrisch in θ)
        for (int i = 0; i < NT; i++) {
            int a = Math.max(0, i - 1), b = Math.min(NT - 1, i + 1);
            q.tK2[i] = (q.tK1[b] - q.tK1[a]) / ((b - a) * dth);
        }
        for (int i = 0; i < NT; i++) {
            int a = Math.max(0, i - 1), b = Math.min(NT - 1, i + 1);
            q.tK3[i] = (q.tK2[b] - q.tK2[a]) / ((b - a) * dth);
        }
        // α(θ) und sein Größtes
        double[] al = new double[NT];
        int ic = 0;
        for (int i = 0; i < NT; i++) {
            al[i] = q.tK[i] > 0 ? Math.atan2(q.tK1[i], q.tK[i]) - (th0 + i * dth) : 0;
            if (al[i] > al[ic]) ic = i;
        }
        // überkritisch: am Anfang strebt α gegen arcsin(1/Fr_h); die Tafel beginnt knapp dahinter
        q.alphaMax = Math.max(1e-4, al[ic]);
        q.thetaC = th0 + ic * dth;
        q.kc = q.tK[ic];
        q.querAst = ic > 0;
        // Äste: θ über α (gleich in α); A: i ≤ ic (α steigt), B: i ≥ ic (α fällt)
        for (int j = 0; j < NA; j++) {
            double a = q.alphaMax * j / (NA - 1);
            q.astA[j] = q.querAst ? suche(al, 0, ic, a, th0, dth, true) : q.thetaC;
            q.astB[j] = suche(al, ic, NT - 1, a, th0, dth, false);
        }
        q.ampNorm = -1;
    }

    /** θ zu α im Bereich [i0, i1] der Tafel, wo α steigt (steigt) oder fällt; linear zwischen den Stellen. */
    static double suche(double[] al, int i0, int i1, double a, double th0, double dth, boolean steigt) {
        if (i1 <= i0) return th0 + i0 * dth;
        for (int i = i0; i < i1; i++) {
            double a0 = al[i], a1 = al[i + 1];
            boolean drin = steigt ? (a >= a0 && a <= a1) : (a <= a0 && a >= a1);
            if (drin) {
                double u = a1 != a0 ? (a - a0) / (a1 - a0) : 0;
                return th0 + (i + u) * dth;
            }
        }
        // außerhalb: das Ende, das α am nächsten ist
        return Math.abs(al[i0] - a) < Math.abs(al[i1] - a) ? th0 + i0 * dth : th0 + i1 * dth;
    }

    /** Linear aus einer Tafel über θ. */
    static double ausTafel(double[] t, double th, double th0, double th1) {
        double u = (th - th0) / (th1 - th0) * (NT - 1);
        if (!(u > 0)) return t[0];
        if (u >= NT - 1) return t[NT - 1];
        int i = (int) u;
        double f = u - i;
        return t[i] + f * (t[i + 1] - t[i]);
    }

    /** Linear aus einem Ast (gleich in α von 0 bis α_max). */
    static double ausAst(double[] t, double a, double aMax) {
        double u = a / aMax * (NA - 1);
        if (!(u > 0)) return t[0];
        if (u >= NA - 1) return t[NA - 1];
        int i = (int) u;
        double f = u - i;
        return t[i] + f * (t[i + 1] - t[i]);
    }

    // ------------------------------------------------------------------ Muster

    /**
     * Das Muster eines Druckpunkts (Einheitsstärke) im Punkt x hinter und y neben ihm, aus den Tafeln der Quelle; out[0]
     * Höhe, out[1] Σ K · Gewicht, out[2] Σ Gewicht (für die mittlere Wellenzahl). b: halbe Breite des Druckfelds; kMax:
     * größte Wellenzahl (darüber blendet sie aus).
     */
    static void punkt(Quelle q, double x, double y, double b, double kMax, double[] out) {
        out[0] = out[1] = out[2] = 0;
        if (x <= 1e-6) return;
        double ay = Math.abs(y), r = Math.hypot(x, ay), al = Math.atan2(ay, x), aus = 1;
        double kref = G / (q.tafelV * q.tafelV);
        if (al > q.alphaMax) {
            double ueber = al - q.alphaMax;
            aus = Math.exp(-3 * ueber * Math.pow(q.kc * r, 2.0 / 3));
            if (aus < 1e-4) return;
            al = q.alphaMax;
        }
        for (int ast = 0; ast < 2; ast++) {
            if (ast == 0 && !q.querAst) continue;
            double th = ausAst(ast == 0 ? q.astA : q.astB, al, q.alphaMax);
            double k = ausTafel(q.tK, th, q.th0, q.th1);
            if (!(k > 0) || k > 1.6 * kMax) continue;
            double k1 = ausTafel(q.tK1, th, q.th0, q.th1), k2 = ausTafel(q.tK2, th, q.th0, q.th1), k3 = ausTafel(q.tK3, th, q.th0, q.th1);
            double c = Math.cos(th + al), s = Math.sin(th + al);
            double phi = r * k * c;
            double p2 = r * (k2 * c - 2 * k1 * s - k * c), p3 = r * (k3 * c - 3 * k2 * s - 3 * k1 * c + k * s);
            double amp = Math.min(Math.sqrt(2 * Math.PI / (Math.abs(p2) + 1e-12)), 1.547 * Math.pow(Math.abs(p3) / 6 + 1e-12, -1.0 / 3));
            double w = Math.exp(-(k * b) * (k * b) / 4);
            if (k > kMax) w *= Math.max(0, 1 - (k - kMax) / (0.6 * kMax));
            double S = k / kref, g = S * S * w * amp * aus;
            out[0] += g * Math.cos(phi + (p2 > 0 ? Math.PI / 4 : -Math.PI / 4));
            out[1] += g * k;
            out[2] += g;
        }
    }

    /** Faktor vom Einheitsmuster zur Höhe in m [G] (neu, wenn sich die Tafel ändert). */
    static double amplitude(Quelle q) {
        if (q.fahrt < 0.15) return 0;
        if (q.ampNorm < 0) {
            double mx = 0, b = q.breite / 2;
            double[] p = new double[3], m = new double[3];
            for (int i = 1; i <= 96; i++) {
                double x = q.laenge * (0.2 + 4.0 * i / 96);
                punkt(q, x, q.laenge, b, 1e9, p);
                punkt(q, x - q.laenge, q.laenge, b, 1e9, m);
                mx = Math.max(mx, Math.abs(p[0] - m[0]));
            }
            double fr = q.froude(), ziel = 0.12 * q.tiefgang * fr * fr / (0.08 + fr * fr);
            q.ampNorm = mx < 1e-12 ? 0 : ziel / mx;
        }
        return q.ampNorm;
    }

    // ------------------------------------------------------------------ Spur

    /** Die Spur ausgedünnt (wie die Karte sie bekommt): je Punkt x, y, Weg hinter dem Bug, Alter; der erste ist der Bug. */
    public double[][] spur(Quelle q) {
        double[] b = q.bug();
        List<double[]> out = new ArrayList<>();
        out.add(new double[] {b[0], b[1], 0, 0});
        double ax = b[0], ay = b[1], ad = 0, naechst = q.abstand();
        for (int i = q.zahl - 1; i >= 0 && out.size() < SPUR; i--) {
            if (zeit - q.tt[i] > q.alterMax()) break;                       // ältere Spur zählt nicht (eine frühere Fahrt)
            ad += Math.hypot(q.tx[i] - ax, q.ty[i] - ay);
            ax = q.tx[i];
            ay = q.ty[i];
            if (ad >= naechst || i == 0) {
                out.add(new double[] {ax, ay, ad, zeit - q.tt[i]});
                naechst = ad + q.abstand() * Math.pow(2, Math.floor(out.size() / 16.0));
            }
        }
        // ohne Spur: gerade hinter dem Heck gedacht
        if (out.size() == 1) {
            double h = Math.toRadians(q.kurs), l = 20 * q.laenge;
            out.add(new double[] {b[0] - Math.sin(h) * l, b[1] - Math.cos(h) * l, l, l / Math.max(0.1, q.fahrt)});
        }
        return out.toArray(new double[0][]);
    }

    /** Spurkoordinaten über der ausgedünnten Spur: {Weg hinter dem Bug, Abstand quer, Alter}; false vor dem Bug. */
    public static boolean spurKoord(double[][] tr, int n, double px, double py, double[] out) {
        double best = Double.MAX_VALUE;
        boolean gefunden = false;
        for (int i = 0; i + 1 < n; i++) {
            double[] a = tr[i], b = tr[i + 1];
            double sx = b[0] - a[0], sy = b[1] - a[1], l2 = sx * sx + sy * sy;
            double u = l2 < 1e-12 ? 0 : ((px - a[0]) * sx + (py - a[1]) * sy) / l2;
            if (u < 0 || u > 1) continue;
            double qx = a[0] + sx * u - px, qy = a[1] + sy * u - py, d2 = qx * qx + qy * qy;
            if (d2 < best) {
                best = d2;
                out[0] = a[2] + (b[2] - a[2]) * u;
                out[1] = Math.sqrt(d2);
                out[2] = a[3] + (b[3] - a[3]) * u;
                gefunden = true;
            }
        }
        return gefunden && out[0] > 1e-6;
    }

    /** Reichweite des Musters um die Mitte der Quelle (m) [G]. */
    static double reichweite(Quelle q) { return q.laenge > 50 ? 1e9 : Math.max(60, 12 * q.laenge + 40 * q.fahrt); }

    static double glatt(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Schaum hinter dem Heck (0 … 1) aus Spurkoordinaten [G, wie wake]. */
    static double schaum(Quelle q, double f0, double tau, double[] o) {
        double weg = o[0] - q.laenge * 0.8, quer = o[1], alter = o[2];
        if (weg < -0.2 * q.laenge || f0 <= 0) return 0;
        double w = 0.55 * q.breite + 0.12 * Math.sqrt(Math.max(0, weg) * q.breite);
        return Math.min(1, f0 * Math.exp(-Math.max(0, alter) / tau) * (1 - glatt(0.5 * w, w, quer)));
    }

    // ------------------------------------------------------------------ Höhe und Kacheln

    /** Höhe aller Quellen am Ort (m) mit der größten Wellenzahl kMax; out (Länge 3) bekommt Höhe, Schaum, mittleres K. */
    public void am(double px, double py, double kMax, double[] out, double[][][] spuren) { am(px, py, kMax, out, spuren, null); }

    /**
     * Wie oben, ohne die Quelle ohne (null: alle; seit S7d2, wie {@code see_koerper} an den Punkten eines Schiffs mit
     * Kielwasser). Hat out Platz für 4 Werte, kommt Σ g dazu (wie die Kacheln der Karte im vierten Wert).
     */
    public void am(double px, double py, double kMax, double[] out, double[][][] spuren, Quelle ohne) { am(px, py, kMax, out, spuren, ohne, null, false); }

    /** ohne: diese Quelle nicht; nur: nur diese (null: alle); roh: out[2] = Σ K g statt des mittleren K (Lagen der Karte). */
    void am(double px, double py, double kMax, double[] out, double[][][] spuren, Quelle ohne, Quelle nur, boolean roh) {
        double h = 0, f = 0, sk = 0, sg = 0;
        double[] o = new double[3], p = new double[3], m = new double[3];
        for (int i = 0; i < quellen.size(); i++) {
            Quelle q = quellen.get(i);
            if (q.fahrt < 0.15 || q == ohne || nur != null && q != nur) continue;
            tafel(q, 1e9);
            if (Math.hypot(px - q.x, py - q.y) > reichweite(q) + 2 * q.laenge) continue;
            double[][] tr = spuren != null ? spuren[i] : spur(q);
            if (!spurKoord(tr, tr.length, px, py, o)) continue;
            double a = amplitude(q) * staerke, b = q.breite / 2;
            punkt(q, o[0], o[1], b, kMax, p);
            punkt(q, o[0] - q.laenge, o[1], b, kMax, m);
            h += a * (p[0] - m[0]);
            sk += a * (p[1] + m[1]);
            sg += a * (p[2] + m[2]);
            double f0 = schaumStaerke * (q.motor ? 0.9 : 0.35 * glatt(0.5, 3.0, q.fahrt)), tau = q.motor ? 150 : 25;
            f = Math.max(f, schaum(q, f0, tau, o));
        }
        out[0] = h;
        out[1] = f;
        out[2] = roh ? sk : sg > 0 ? sk / sg : 0;
        if (out.length > 3) out[3] = sg;
    }

    /** Höhe aller Quellen am Ort (m), ohne Grenze der Wellenzahl. */
    public double hoehe(double px, double py) {
        double[] o = new double[3];
        am(px, py, 1e9, o, null);
        return o[0];
    }

    /**
     * Eine Kachel wie {@code see_kiel.comp}: n × n Zellen ab (x0, y0) mit der Kante zelle; je Zelle Höhe, Schaum, mittleres
     * K, Σ g (seit S7d2; vorher 0) in out (4 · n · n), Zeile für Zeile von Süd nach Nord. Die größte Wellenzahl: 3 Zellen je
     * Welle.
     */
    public void kachel(double x0, double y0, double zelle, int n, double[] out) {
        double kMax = 2 * Math.PI / (3 * zelle);
        double[][][] sp = new double[quellen.size()][][];
        for (int i = 0; i < sp.length; i++) sp[i] = spur(quellen.get(i));
        double[] o = new double[4];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                am(x0 + (i + 0.5) * zelle, y0 + (j + 0.5) * zelle, kMax, o, sp, null);
                int k = 4 * (j * n + i);
                out[k] = o[0]; out[k + 1] = o[1]; out[k + 2] = o[2]; out[k + 3] = o[3];
            }
    }

    /**
     * Seit S7d2: die Lage einer Quelle allein wie in {@code see_kiel.comp} (Lage 1 + q): je Zelle Höhe, Schaum, Σ K g, Σ g.
     */
    public void kachel(double x0, double y0, double zelle, int n, double[] out, Quelle nur) {
        double kMax = 2 * Math.PI / (3 * zelle);
        double[][][] sp = spuren();
        double[] o = new double[4];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                am(x0 + (i + 0.5) * zelle, y0 + (j + 0.5) * zelle, kMax, o, sp, null, nur, true);
                int k = 4 * (j * n + i);
                out[k] = o[0]; out[k + 1] = o[1]; out[k + 2] = o[2]; out[k + 3] = o[3];
            }
    }

    /** Die Spuren aller Quellen (für {@link #am} mit vielen Punkten). */
    public double[][][] spuren() {
        double[][][] sp = new double[quellen.size()][][];
        for (int i = 0; i < sp.length; i++) sp[i] = spur(quellen.get(i));
        return sp;
    }

    // ------------------------------------------------------------------ für die Karte

    /** Quellen in Fahrt (über 0,15 m/s), die die Karte zeichnet. */
    public List<Quelle> inFahrt() {
        List<Quelle> l = new ArrayList<>();
        for (Quelle q : quellen) if (q.fahrt >= 0.15) l.add(q);
        return l;
    }

    /**
     * Die Werte einer Quelle für die Karte ({@link #JE_QUELLE}), Orte relativ zu (bx, by). Kopf: Bug x, y; Mitte x, y;
     * Länge, halbe Breite, Amplitude (m), α_max; θ₀, θ₁, K_c, K_ref; Schaum f₀, τ, Breite, Reichweite; Ast quer (1/0),
     * Punkte der Spur, Fahrt, –; dann K, K', K'', K''' je Stelle über θ, Ast A, Ast B, Spur (x, y, Weg, Alter).
     */
    public float[] werte(Quelle q, double bx, double by) {
        tafel(q, 1e9);
        float[] w = new float[JE_QUELLE];
        double[] bug = q.bug();
        double f0 = schaumStaerke * (q.motor ? 0.9 : 0.35 * glatt(0.5, 3.0, q.fahrt)), tau = q.motor ? 150 : 25;
        double[][] tr = spur(q);
        double[] k = {bug[0] - bx, bug[1] - by, q.x - bx, q.y - by, q.laenge, q.breite / 2, amplitude(q) * staerke, q.alphaMax,
                q.th0, q.th1, q.kc, G / (q.tafelV * q.tafelV), f0, tau, q.breite, reichweite(q), q.querAst ? 1 : 0, tr.length, q.fahrt, 0, 0, 0, 0, 0};
        for (int i = 0; i < KOPF; i++) w[i] = (float) k[i];
        int o = KOPF;
        for (int i = 0; i < NT; i++) { w[o++] = (float) q.tK[i]; w[o++] = (float) q.tK1[i]; w[o++] = (float) q.tK2[i]; w[o++] = (float) q.tK3[i]; }
        for (int j = 0; j < NA; j++) w[o++] = (float) q.astA[j];
        for (int j = 0; j < NA; j++) w[o++] = (float) q.astB[j];
        for (int i = 0; i < SPUR; i++) {
            if (i < tr.length) { w[o++] = (float) (tr[i][0] - bx); w[o++] = (float) (tr[i][1] - by); w[o++] = (float) tr[i][2]; w[o++] = (float) tr[i][3]; }
            else o += 4;
        }
        return w;
    }
}
