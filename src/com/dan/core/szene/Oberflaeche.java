package com.dan.core.szene;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Oberfläche des Seegangs, Referenz in double (Seegang Werkbuch, S3; Plan 6.3, SF1 A, SF7 A). Aus den Amplituden des
 * Nahfelds (Varianz je Zelle, Band und Richtung) baut sie die Fläche zu einer Zeit:
 * <pre>
 *   Z(x, t)  = Σ_bq √(2 E_bq(x)) · G_bq(T_bq(x) − t) · e^{i(Φ_bq(x) − ω_b t)}               (analytisches Signal, linear)
 *   ξ(x, t)  = −λ · Σ_bq coth(k h) · ∇Φ/|∇Φ| · Im Z_bq                                     (waagrecht, Gerstner)
 *   η(x, t)  = Re Z' + Re(c Z'²) + η_lw + λ Σ k coth(kh) E,   Z' = s · f(|Z|) · Z            (Form)
 * </pre>
 * G_bq ist die Hülle der Stützstelle ({@link Profilpuffer}), Φ und T kommen aus dem {@link Phasenfeld} (über Tiefe und
 * Strömung), f bildet die Höhen auf Battjes–Groenendijk ab, c gibt Schiefe und Vorneigung nach Ruessink, η_lw ist die
 * gebundene lange Welle ({@link Seegangsform}). Der letzte Summand hält das Mittel der Fläche am Ort (die Verschiebung
 * nach Gerstner senkt das Mittel, an einem festen Ort gemessen, um λ Σ k coth(kh) E).
 * <p>
 * <b>Stufen.</b> Bis zu zwei Stufen des Nahfelds (fein vor grob) und das Fernfeld (die {@link Seegangsoptik#LANG} Wellen
 * der Stufe 2 aus Hs, Tp, Richtung). Am Rand jeder Stufe wird über {@link Stufe#rand} Zellen übergeblendet: Die Energie
 * linear, die Phase von fein und grob ebenfalls linear (beide zeigen dieselben Stützstellen, das feine Phasenfeld hat
 * seinen Rand aus dem groben), das Fernfeld mit seinen eigenen Wellen inkohärent. So bleibt die Varianz stetig.
 * <p>
 * <b>Faltung.</b> Die Verschiebung wird am Ort auf λ_eff = min(λ, {@link #faltung} / S_ξ) begrenzt,
 * S_ξ² = Σ (k coth kh)² E, damit sich die Fläche nur selten faltet (J &lt; 0) [G].
 * <p>
 * Der Punkt ({@link #punkt}) ist ein Ort der ruhenden Fläche (Lagrange): Er liegt zur Zeit t bei x + ξ in der Höhe η.
 * {@link #euler} sucht den Punkt, der über einem festen Ort liegt.
 */
public final class Oberflaeche {
    public static final String VERSION = "0.1";
    /** Dauer eines Wechsels der Phasenfelder (s) [G]: die Frequenz verschiebt sich dabei um höchstens rund 3 %. */
    public static final double WECHSEL = 60;

    /** Eine Stufe des Nahfelds als Eingabe. */
    public static final class Stufe {
        public final String name;
        public final int nx, ny, nc, nb, nd;
        public final double dx, x0, y0;
        /** Varianz je Stützstelle (m²), Index (c · nb + b) · nd + q wie im Nahfeld. */
        public final double[] e;
        /** Tiefe (m) und Gefälle des Grunds |∇h| je Zelle. */
        public final double[] h, gefaelle;
        /** Zellen der Überblendung am Rand [G]. */
        public int rand = 6;
        /** Die Stände der Phase (a, b) und, während eines Wechsels, die alten. */
        public Phasenfeld pa, pb, altA, altB;
        double wechselVon = Double.NaN, wechselBis = Double.NaN;

        public Stufe(String name, int nx, int ny, double dx, double x0, double y0, int nb, int nd) {
            this.name = name; this.nx = nx; this.ny = ny; this.nc = nx * ny; this.dx = dx; this.x0 = x0; this.y0 = y0; this.nb = nb; this.nd = nd;
            e = new double[nc * nb * nd];
            h = new double[nc];
            gefaelle = new double[nc];
        }

        /**
         * Je Zelle vorbereitet ({@link Oberflaeche#vorbereiten}): die gezeichnete Varianz je Stützstelle (an die
         * versetzte Richtung gelegt, B31) und die Kennwerte der Form aus dem ganzen Spektrum der Zelle, je Zelle
         * {@link #KENN} Werte: m0, Σ (k coth kh)² E, dann je Band Σ E und Σ E k (k aus der Dispersion bei der Tiefe der
         * Zelle, ohne Strömung [G]). So liest die Karte je Ort nur bilinear, wie die Referenz.
         */
        public double[] eZ, kenn;
        public int kennWerte() { return 2 + 2 * nb; }

        /** Kennwerte am Ort, bilinear (Länge {@link #kennWerte}). */
        void kennwerte(int[] c, double[] a, double[] out) {
            int n = kennWerte();
            double w00 = (1 - a[0]) * (1 - a[1]), w10 = a[0] * (1 - a[1]), w01 = (1 - a[0]) * a[1], w11 = a[0] * a[1];
            for (int k = 0; k < n; k++) out[k] = w00 * kenn[c[0] * n + k] + w10 * kenn[c[1] * n + k] + w01 * kenn[c[2] * n + k] + w11 * kenn[c[3] * n + k];
        }

        /** Gefälle aus der Tiefe (zentral, am Rand einseitig). */
        public void gefaelleAusTiefe() {
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    int il = Math.max(0, i - 1), ir = Math.min(nx - 1, i + 1), jl = Math.max(0, j - 1), jr = Math.min(ny - 1, j + 1);
                    double gx = (h[j * nx + ir] - h[j * nx + il]) / ((ir - il) * dx), gy = (h[jr * nx + i] - h[jl * nx + i]) / ((jr - jl) * dx);
                    gefaelle[c] = Math.hypot(gx, gy);
                }
        }

        /** Zwei Stände der Phase (b darf gleich a sein). */
        public void phasen(Phasenfeld a, Phasenfeld b) { pa = a; pb = b; }

        /** Neue Stände der Phase (anderes Gitter, etwa nach einem Verschieben): in der Zeit [t, t + dauer] übergeblendet. */
        public void wechsel(Phasenfeld a, Phasenfeld b, double t, double dauer) {
            altA = pa; altB = pb; pa = a; pb = b; wechselVon = t; wechselBis = t + dauer;
        }

        /** Gewicht am Ort: 0 außerhalb und auf dem Rand, 1 ab {@link #rand} Zellen innen (glatt). */
        public double gewicht(double x, double y) {
            double fx = (x - x0) / dx, fy = (y - y0) / dx;
            double d = Math.min(Math.min(fx, nx - fx), Math.min(fy, ny - fy));
            if (d <= 0.5) return 0;
            double u = Math.min(1, (d - 0.5) / rand);
            return u * u * (3 - 2 * u);
        }

        /** Bilineare Gewichte der vier Zellmitten: {c00, c10, c01, c11} und {ax, ay}. */
        void zellen(double x, double y, int[] c, double[] a) {
            double fx = Math.max(0, Math.min(nx - 1.000001, (x - x0) / dx - 0.5)), fy = Math.max(0, Math.min(ny - 1.000001, (y - y0) / dx - 0.5));
            int i = (int) fx, j = (int) fy, i1 = Math.min(nx - 1, i + 1), j1 = Math.min(ny - 1, j + 1);
            c[0] = j * nx + i; c[1] = j * nx + i1; c[2] = j1 * nx + i; c[3] = j1 * nx + i1;
            a[0] = fx - i; a[1] = fy - j;
        }

        double bilinear(double[] f, int[] c, double[] a) {
            return (1 - a[1]) * ((1 - a[0]) * f[c[0]] + a[0] * f[c[1]]) + a[1] * ((1 - a[0]) * f[c[2]] + a[0] * f[c[3]]);
        }

        /** Gezeichnete Varianz je Stützstelle am Ort, bilinear. */
        void energie(int[] c, double[] a, double[] out) {
            int n = nb * nd;
            double[] e = eZ;
            double w00 = (1 - a[0]) * (1 - a[1]), w10 = a[0] * (1 - a[1]), w01 = (1 - a[0]) * a[1], w11 = a[0] * a[1];
            int o00 = c[0] * n, o10 = c[1] * n, o01 = c[2] * n, o11 = c[3] * n;
            for (int k = 0; k < n; k++) out[k] = w00 * e[o00 + k] + w10 * e[o10 + k] + w01 * e[o01 + k] + w11 * e[o11 + k];
        }

        /** Phase der Stützstelle zur Zeit t, über die Stände und einen Wechsel geblendet: {Φ, T, ∂Φ/∂x, ∂Φ/∂y, ∂T/∂x, ∂T/∂y}. */
        void phase(int bq, double x, double y, double t, double[] o, double[] tmp) {
            paar(pa, pb, bq, x, y, t, o, tmp);
            if (altA != null && t < wechselBis) {
                double u = Math.max(0, Math.min(1, (t - wechselVon) / (wechselBis - wechselVon))), w = u * u * (3 - 2 * u);
                double[] o2 = new double[6];
                paar(altA, altB, bq, x, y, t, o2, tmp);
                for (int k = 0; k < 6; k++) o[k] = w * o[k] + (1 - w) * o2[k];
            }
        }

        static void paar(Phasenfeld a, Phasenfeld b, int bq, double x, double y, double t, double[] o, double[] tmp) {
            a.wert(bq, x, y, o);
            if (b == null || b == a || b.zeit == a.zeit) return;
            double s = Math.max(0, Math.min(1, (t - a.zeit) / (b.zeit - a.zeit)));
            b.wert(bq, x, y, tmp);
            for (int k = 0; k < 6; k++) o[k] = (1 - s) * o[k] + s * tmp[k];
        }
    }

    /** Das Fernfeld: Hs, Tp, Richtung (wohin, rad, 0 = Ost) am Ort. */
    public interface Fernfeld { double[] werte(double x, double y); }

    /** Ein Punkt der Fläche und was zu ihm führte. */
    public static final class Punkt {
        /** Höhe über dem Spiegel (m) und waagrechte Verschiebung (m). */
        public double eta, xi, yi;
        /** Linear (ohne Form): Re Z, Im Z; Varianz m0 (m²), Tiefe, tragende Wellenzahl, Ursell, Ziel Sk und As. */
        public double etaLin, zRe, zIm, m0, tiefe, kp, ur, sk, as;
        /** λ_eff, Faktor der Hülle, Maßstab s, Beiwert c, gebundene lange Welle, Ausgleich des Mittels. */
        public double lambda, huelle, s, cRe, cIm, lw, mittel;
        /** Gewichte der Stufen (fein, grob, fern). */
        public double wFein, wGrob, wFern;
        /** Aufgelöste Neigungsvarianz Σ k² E g² und Varianz der Höhe Σ E g² der gezeichneten Wellen (linear, ohne Form; S3c). */
        public double neigung, gezeichnet;
        /** Seit S5a: Brechgrad q, Weißwasser w und Dicke der Walze t (m) nach {@link Walze}. */
        public double brechen, weiss, walze;
        /**
         * Seit S6b-2 (Seegang Werkbuch 67, SF61 A, SF62 A): der Antrieb des Auflaufs. Tiefenintegrierter Fluss (m²/s) je
         * Welle linear q = (ω/k) η in Laufrichtung, skaliert wie η (s f); die zweite Ordnung mit c_p und die gebundene lange
         * Welle mit c_g in der Richtung, in die die Energie läuft [G]. Stand des Antriebs: η mit der gebundenen langen
         * Welle, auch wenn die Fläche sie aus hat ({@link #mitLangwelle}); {@code lwAntrieb} ist dieser Anteil.
         */
        public double qx, qy, etaAntrieb, lwAntrieb;
    }

    public final Profilpuffer puffer;
    public final int nb, nd;
    public final double[] omega;
    /** fein (oder null), grob (oder null). */
    public Stufe fein, grob;
    public Fernfeld fernfeld;
    public double tiefeFern = 30;
    /** Schalter und Größen [G]. */
    public double lambda = 1, faltung = 1.0 / 3;
    /**
     * Die gebundene lange Welle ist bis S6 aus [G]: Sie senkt in 3 m Tiefe die Schiefe von 0,36 auf 0,19 (Befund B28) und
     * wird mit dem Auflauf (Brandungsschwebung, R2 % nach Stockdon) zusammen abgestimmt.
     */
    public boolean mitForm = true, mitHoehen = true, mitLangwelle = false, mitVerschiebung = true;

    private final double[][] wellenFern = Seegangsoptik.wellen();
    private final double[] grenzenFern = Seegangsoptik.baender(), tafelFern = Seegangsoptik.cdf();

    public Oberflaeche(Profilpuffer puffer, int nd) {
        this.puffer = puffer; this.nb = puffer.nb; this.nd = nd; this.omega = puffer.omega;
        if (puffer.nd != nd) throw new IllegalArgumentException("Hüllen für " + puffer.nd + " Richtungen, Oberfläche mit " + nd);
    }

    // ------------------------------------------------------------------ Punkt (Lagrange)

    /**
     * Die Stützstellen zeichnen ihre Wellen in einer je Band versetzten Richtung ({@link Phasenfeld#richtungen}). Damit
     * die mittlere Richtung bleibt, bekommt jede die Energie des Nahfelds an ihrer Richtung (linear zwischen den
     * Nachbarn), je Band auf dieselbe Summe gesetzt (Befund B31).
     */
    void anRichtung(double[] e, double[] richtung) {
        double dth = 2 * Math.PI / nd;
        double[] t = new double[nd];
        for (int b = 0; b < nb; b++) {
            int o = b * nd;
            double s0 = 0, s1 = 0;
            for (int q = 0; q < nd; q++) {
                double w = (richtung[o + q] - q * dth) / dth;
                int q2 = Math.floorMod(q + (w >= 0 ? 1 : -1), nd);
                double aw = Math.abs(w);
                t[q] = (1 - aw) * e[o + q] + aw * e[o + q2];
                s0 += e[o + q]; s1 += t[q];
            }
            double f = s1 > 0 ? s0 / s1 : 0;
            for (int q = 0; q < nd; q++) e[o + q] = t[q] * f;
        }
    }

    private final ThreadLocal<double[][]> lokal = ThreadLocal.withInitial(() -> new double[][] {
            new double[0], new double[0], new double[6], new double[6], new double[6], new double[4], new double[2], new double[2], new double[0], new double[0]});

    /**
     * Bereitet die Stufen vor (nach jeder Änderung ihrer Varianz oder Tiefe und nach dem ersten Phasenfeld): gezeichnete
     * Varianz je Zelle ({@link #anRichtung}) und die Kennwerte der Form je Zelle. {@link #punkt} ruft es selbst, solange
     * eine Stufe noch nicht vorbereitet ist.
     */
    public void vorbereiten() {
        for (Stufe st : stufen()) {
            int n = nb * nd, kw = st.kennWerte();
            double[] ez = new double[st.e.length], kn = new double[st.nc * kw], ec = new double[n];
            double[] ri = st.pa != null ? st.pa.richtung : Phasenfeld.richtungen(nb, nd);
            for (int c = 0; c < st.nc; c++) {
                System.arraycopy(st.e, c * n, ec, 0, n);
                anRichtung(ec, ri);
                System.arraycopy(ec, 0, ez, c * n, n);
                double hh = Math.max(Phasenfeld.H_MIN, st.h[c]);
                int o = c * kw;
                for (int bb = 0; bb < nb; bb++) {
                    double k = Dispersion.wellenzahl(omega[bb], hh), ct = 1 / Math.tanh(Math.min(30, k * hh)), eb = 0;
                    for (int q = 0; q < nd; q++) eb += ec[bb * nd + q];
                    kn[o] += eb;
                    kn[o + 1] += k * k * ct * ct * eb;
                    kn[o + 2 + bb] = eb;
                    kn[o + 2 + nb + bb] = eb * k;
                }
            }
            st.eZ = ez;
            st.kenn = kn;
        }
        aktiv = aktiv(E_REL, S_REL);
    }

    /**
     * Je Stützstelle: liegt sie in irgendeiner Zelle einer Stufe über einer der Schwellen eRel · m0 und sRel · S? Die
     * Varianzschwelle überträgt sich genau auf jeden Ort dazwischen (alles bilinear); die Neigungsschwelle nicht ganz,
     * denn k̄_b² ist ein Quotient — zwischen den Zellen kann eine Stützstelle knapp über ihr liegen, die in keiner Ecke
     * darüber liegt. Sie fehlt dann mit einem Faktor nahe 0 ({@link #faktor}).
     */
    public boolean[] aktiv(double eRel, double sRel) {
        if ((fein != null && fein.eZ == null) || (grob != null && grob.eZ == null)) vorbereiten();
        int m = nb * nd;
        boolean[] an = new boolean[m];
        double[] kq = new double[nb];
        for (Stufe st : stufen()) {
            int kw = st.kennWerte();
            for (int c = 0; c < st.nc; c++) {
                double es = Math.max(E_MIN, eRel * st.kenn[c * kw]), ss = sRel * neigung(st.kenn, c * kw, nb, kq);
                for (int bq = 0; bq < m; bq++) if (!an[bq] && faktor(st.eZ[c * m + bq], es, kq[bq / nd], ss) > 0) an[bq] = true;
            }
        }
        return an;
    }

    /**
     * Größe einer Stelle der Fläche (m), etwa der Abstand der Stellen einer Stufe der Clipmap; 0: alles. Eine Welle der
     * Länge L zählt mit g = clamp(L / (4 d) − 1, 0, 1) ihrer Amplitude in Höhe und Verschiebung [G]; die Kennwerte der
     * Form (m0, λ_eff, Ursell, Hülle) kommen immer aus dem ganzen Spektrum, damit sich die Form zwischen den Stufen
     * nicht ändert.
     */
    public double aufloesung = 0;
    /** Seit S5b-1: Hebt die Walze die Fläche ({@link Walze#HEBUNG})? Die Karte tut es immer. */
    public boolean mitWalze = true;

    /** Gewicht g einer Welle mit der Wellenzahl k bei der Größe d der Stelle. */
    /**
     * Je Band die kleinste Wellenzahl der Dispersion über die Phasenfelder aller Stufen und Stände ({@link
     * Phasenfeld#kMin}); unendlich ohne Feld. Die Auflösung einer Welle misst sich an max(|∇Φ|, kUnten) (Befund B34):
     * Wo das Feld nachgibt, wird eine Welle so nicht länger, als die Physik erlaubt; und ist 2π / kUnten kleiner als
     * 4 d, hat kein Ort eine Welle des Bands, und die Karte übergeht das Band.
     */
    public double[] kUnten() {
        double[] u = new double[nb];
        java.util.Arrays.fill(u, Double.POSITIVE_INFINITY);
        for (Stufe s : stufen())
            for (Phasenfeld p : new Phasenfeld[] {s.pa, s.pb, s.altA, s.altB})
                if (p != null && p.kMin != null) for (int b = 0; b < nb; b++) u[b] = Math.min(u[b], p.kMin[b]);
        return u;
    }

    public static double aufgeloest(double k, double d) {
        if (!(d > 0)) return 1;
        return Math.max(0, Math.min(1, 2 * Math.PI / k / (4 * d) - 1));
    }

    /** Der Punkt der ruhenden Fläche bei (x, y) zur Zeit t. */
    public void punkt(double x, double y, double t, Punkt p) { punkt(x, y, t, p, 1); }

    /**
     * Seit S5b-4 (Seegang Werkbuch 46): die mittlere Periode T_m01 = 2π Σ E / Σ E ω am Ort, über die Bänder von fein und
     * grob (gewichtet wie {@link #punkt}) und das Fernfeld (seine Energie mit ω = 2π / Tp), wie {@code see_fuss.comp}. So
     * schnell läuft die Phase von Z im Mittel um; der mittlere Abstand der Kämme. NaN ohne Energie.
     */
    public double tm01(double x, double y) {
        if (aktiv == null || (fein != null && fein.eZ == null) || (grob != null && grob.eZ == null)) vorbereiten();
        int kw = 2 + 2 * nb;
        double[] kf = new double[kw], kg = new double[kw], af = new double[2], ag = new double[2];
        int[] cf = new int[4], cg = new int[4];
        double wf = fein != null ? fein.gewicht(x, y) : 0;
        double wg = grob != null ? (1 - wf) * grob.gewicht(x, y) : 0;
        double wfern = Math.max(0, 1 - wf - wg);
        if (fernfeld == null) { double s = wf + wg; if (s > 0) { wf /= s; wg /= s; } wfern = 0; }
        if (wf > 0) { fein.zellen(x, y, cf, af); fein.kennwerte(cf, af, kf); }
        if (wg > 0) { grob.zellen(x, y, cg, ag); grob.kennwerte(cg, ag, kg); }
        double se = 0, sew = 0;
        for (int b = 0; b < nb; b++) {
            double e = (wf > 0 ? wf * kf[2 + b] : 0) + (wg > 0 ? wg * kg[2 + b] : 0);
            se += e;
            sew += e * omega[b];
        }
        if (wfern > 0 && fernfeld != null) {
            double[] w = fernfeld.werte(x, y);
            double[] amp = Seegangsoptik.amplituden(w[0], w[1], w[2], 1e-4, wellenFern, grenzenFern, tafelFern);
            double mf = 0;
            for (int j = 0; j < Seegangsoptik.LANG; j++) mf += 0.5 * amp[j] * amp[j] * wfern;
            se += mf;
            if (w[1] > 0) sew += mf * 2 * Math.PI / w[1];
        }
        return se > 0 && sew > 0 ? 2 * Math.PI * se / sew : Double.NaN;
    }

    /**
     * Wie {@link #punkt(double, double, double, Punkt)}, mit dem Anteil nah (0 … 1) von fein und grob: Der Rest geht ans
     * Fernfeld. Am Rand der Clipmap geht die Synthese so in die Wellen des Fernfelds über, die der Weg jenseits der
     * Clipmap zeichnet (S3c).
     */
    public void punkt(double x, double y, double t, Punkt p, double nah) { punkt(x, y, t, p, nah, null, null); }

    /**
     * Seit S7b-1 (Seegang Werkbuch 88): das Wasser für einen schwimmenden Körper am festen Ort (x, y) zur Zeit t, wie
     * {@code see_koerper.comp} auf der Karte: in aus {@link Wasserpunkte#KOPF} + {@link Wasserpunkte#JE_BAND} · nb Werte
     * (Aufbau dort) – η über dem Ort ({@link #euler}), Re Z, Tiefe, k_p, m0 und je Band die rohen Summen der linearen
     * Wellen am Ort; die Wellen des Fernfelds im Band mit dem nächsten ω. Alle Wellen zählen ({@link #aufloesung} gilt).
     */
    public void baender(double x, double y, double t, double[] aus) {
        java.util.Arrays.fill(aus, 0, Wasserpunkte.KOPF + Wasserpunkte.JE_BAND * nb, 0);
        Punkt p = new Punkt();
        punkt(x, y, t, p, 1, null, aus);
        double reZ = p.zRe;
        aus[0] = euler(x, y, t, p);
        aus[1] = reZ;
    }

    /**
     * Seit S6c-1b (Seegang Werkbuch 78, SF85 A): der Punkt wie {@link #punkt(double, double, double, Punkt)}, für den
     * Antrieb eines Lösers ohne Dispersion (Flachwasser) an einem geraden Rand durch den Bezugspunkt (xm, ym) mit der Tiefe
     * hR: Je Stützstelle werden Phase und Laufzeit der Hülle längs des Rands um den Bezugspunkt gestreckt,
     * Φ′ = Φ_m + s (Φ − Φ_m) mit s = (ω/√(g hR)) / |∇Φ_m| und T′ = T_m + (T − T_m) / (|∇T_m| √(g hR)); der Fluss je Welle mit
     * √(g hR) statt ω/k; die Wellen des Fernfelds ebenso mit s = (ω(k)/√(g hR)) / k. So hat jede Welle im Löser die
     * Richtung, die sie in der Synthese hat, und läuft hinein, statt am Rand abzuklingen (Datei 77). Energie und Hülle
     * bleiben. Liegt der Bezugspunkt außerhalb von fein und grob oder ist hR ≤ 0: wie {@link #punkt}. Wie
     * {@code see_antrieb.comp} (RANDRICHTUNG).
     */
    public void punktRand(double x, double y, double t, Punkt p, double xm, double ym, double hR) {
        punkt(x, y, t, p, 1, hR > 0 ? new double[] {xm, ym, hR} : null, null);
    }

    private void punkt(double x, double y, double t, Punkt p, double nah, double[] rand, double[] bd) {
        int n = nb * nd;
        if (aktiv == null || (fein != null && fein.eZ == null) || (grob != null && grob.eZ == null)) vorbereiten();
        double[][] L = lokal.get();
        int kw = 2 + 2 * nb;
        if (L[0].length != n) { L[0] = new double[n]; L[1] = new double[n]; L[8] = new double[kw]; L[9] = new double[nb]; }
        double[] ef = L[0], eg = L[1], of = L[2], og = L[3], tmp = L[4], gv = L[5], af = L[6], ag = L[7], kv = L[8];
        double[] kf = new double[kw], kg = new double[kw];
        int[] cf = new int[4], cg = new int[4];
        double wf = fein != null ? fein.gewicht(x, y) : 0;
        double wg = grob != null ? (1 - wf) * grob.gewicht(x, y) : 0;
        wf *= nah; wg *= nah;
        double wfern = Math.max(0, 1 - wf - wg);
        if (fernfeld == null) { double s = wf + wg; if (s > 0) { wf /= s; wg /= s; } wfern = 0; }
        p.wFein = wf; p.wGrob = wg; p.wFern = wfern;
        double hf = 0, hg = 0, tf = 0, tg = 0;
        if (wf > 0) { fein.zellen(x, y, cf, af); fein.energie(cf, af, ef); fein.kennwerte(cf, af, kf); hf = fein.bilinear(fein.h, cf, af); tf = fein.bilinear(fein.gefaelle, cf, af); }
        if (grob != null) { grob.zellen(x, y, cg, ag); if (wg > 0) { grob.energie(cg, ag, eg); grob.kennwerte(cg, ag, kg); } hg = grob.bilinear(grob.h, cg, ag); tg = grob.bilinear(grob.gefaelle, cg, ag); }
        double wn = wf + wg;
        double h = wn > 0 ? (wf * hf + wg * hg) / wn : (grob != null ? hg : tiefeFern);
        double tanA = wn > 0 ? (wf * tf + wg * tg) / wn : (grob != null ? tg : 0);
        double hh = Math.max(Phasenfeld.H_MIN, h);
        // Kennwerte der Form aus dem ganzen Spektrum (fein und grob gewichtet, dazu das Fernfeld)
        for (int k = 0; k < kw; k++) kv[k] = (wf > 0 ? wf * kf[k] : 0) + (wg > 0 ? wg * kg[k] : 0);
        double m0 = kv[0], sxi2 = kv[1], emax = 0, kp = 0;
        for (int b = 0; b < nb; b++) if (kv[2 + b] > emax) { emax = kv[2 + b]; kp = kv[2 + nb + b] / kv[2 + b]; }
        double[] amp = null;
        if (wfern > 0 && fernfeld != null) {
            double[] w = fernfeld.werte(x, y);
            amp = Seegangsoptik.amplituden(w[0], w[1], w[2], 1e-4, wellenFern, grenzenFern, tafelFern);
            double mf = 0;
            for (int j = 0; j < Seegangsoptik.LANG; j++) {
                double a2 = amp[j] * amp[j] * wfern, k = Math.hypot(wellenFern[j][0], wellenFern[j][1]);
                mf += 0.5 * a2;
                sxi2 += k * k * 0.5 * a2;
            }
            m0 += mf;
            if (mf > emax && w[1] > 0) kp = Math.pow(2 * Math.PI / w[1], 2) / Dispersion.G;
        }
        // die aufgelösten Wellen
        double zr = 0, zi = 0, xr = 0, yr = 0, mitt = 0, neig = 0, varG = 0, d = aufloesung, eSchwelle = Math.max(E_MIN, E_REL * m0);
        double qxr = 0, qyr = 0, ex = 0, ey = 0;                            // seit S6b-2: Fluss je Welle, Richtung der Energie
        if (bd != null) { bd[4] = hh; bd[5] = kp; bd[6] = m0; }             // seit S7b-1: Kopf für den Körper
        double[] kU = kUnten(), kq = L[9];
        double sSchwelle = S_REL * neigung(kv, 0, nb, kq);
        // seit S6c-1b: Gewichte von fein und grob am Bezugspunkt des Rands (punktRand; nur ihr Verhältnis zählt)
        double xm = 0, ym = 0, wfM = 0, wgM = 0, uM = 0, cR = 0;
        boolean rr = false;
        double[] oM = null, oM2 = null;
        if (rand != null) {
            xm = rand[0]; ym = rand[1];
            wfM = fein != null ? fein.gewicht(xm, ym) : 0;
            wgM = grob != null ? (1 - wfM) * grob.gewicht(xm, ym) : 0;
            rr = wfM + wgM > 0;
            uM = rr ? wfM / (wfM + wgM) : 0;
            cR = Math.sqrt(Dispersion.G * Math.max(Phasenfeld.H_MIN, rand[2]));
            oM = new double[6]; oM2 = new double[6];
        }
        for (int b = 0; b < nb; b++)
            for (int q = 0; q < nd; q++) {
                int bq = b * nd + q;
                if (!aktiv[bq]) continue;
                double e = wf * (wf > 0 ? ef[bq] : 0) + wg * (wg > 0 ? eg[bq] : 0);
                double fa = faktor(e, eSchwelle, kq[b], sSchwelle);
                if (!(fa > 0)) continue;
                double ph, tt, kx, ky;
                if (wf > 0 && wg > 0) {
                    fein.phase(bq, x, y, t, of, tmp);
                    grob.phase(bq, x, y, t, og, tmp);
                    double u = wf / wn;
                    ph = u * of[0] + (1 - u) * og[0]; tt = u * of[1] + (1 - u) * og[1];
                    kx = u * of[2] + (1 - u) * og[2]; ky = u * of[3] + (1 - u) * og[3];
                } else {
                    Stufe s = wf > 0 ? fein : grob;
                    s.phase(bq, x, y, t, of, tmp);
                    ph = of[0]; tt = of[1]; kx = of[2]; ky = of[3];
                }
                double k = Math.hypot(kx, ky), g = fa * aufgeloest(Math.max(k, kU[b]), d);
                if (!(g > 0)) continue;
                if (rr) {                                                     // seit S6c-1b: längs des Rands gestreckt
                    if (wfM > 0 && wgM > 0) {
                        fein.phase(bq, xm, ym, t, oM, tmp);
                        grob.phase(bq, xm, ym, t, oM2, tmp);
                        for (int c = 0; c < 6; c++) oM[c] = uM * oM[c] + (1 - uM) * oM2[c];
                    } else (wfM > 0 ? fein : grob).phase(bq, xm, ym, t, oM, tmp);
                    double lk = Math.hypot(oM[2], oM[3]), lt = Math.hypot(oM[4], oM[5]);
                    if (lk > 0 && lt > 0) {
                        ph = oM[0] + omega[b] / (cR * lk) * (ph - oM[0]);
                        tt = oM[1] + (tt - oM[1]) / (lt * cR);
                    }
                }
                puffer.wert(bq, tt - t, gv);
                double a = g * Math.sqrt(2 * e), phi = ph - omega[b] * t, co = Math.cos(phi), si = Math.sin(phi);
                double re = a * (gv[0] * co - gv[1] * si), im = a * (gv[0] * si + gv[1] * co);
                zr += re; zi += im;
                double ct = 1 / Math.tanh(Math.min(30, k * hh));
                if (bd != null) bandWelle(bd, Wasserpunkte.KOPF + Wasserpunkte.JE_BAND * b, re, im, kx, ky, k, omega[b], ct);   // seit S7b-1
                if (k > 0) {
                    xr -= ct * kx / k * im; yr -= ct * ky / k * im;
                    double c = rr ? cR : omega[b] / k;
                    qxr += c * re * kx / k; qyr += c * re * ky / k;
                    ex += e * g * g * kx / k; ey += e * g * g * ky / k;
                }
                mitt += k * ct * e * g * g;
                neig += k * k * e * g * g;
                varG += e * g * g;
            }
        if (amp != null) {
            double sw = Math.sqrt(wfern);
            for (int j = 0; j < Seegangsoptik.LANG; j++) {
                double[] q = wellenFern[j];
                double k = Math.hypot(q[0], q[1]), g = aufgeloest(k, d), a = amp[j] * sw * g;
                if (a <= 0) continue;
                double phi = (rr ? q[0] * xm + q[1] * ym + q[2] + Wasseroptik.omega(k) / (k * cR) * (q[0] * (x - xm) + q[1] * (y - ym)) : q[0] * x + q[1] * y + q[2])
                        - Wasseroptik.omega(k) * t, co = Math.cos(phi), si = Math.sin(phi);
                zr += a * si; zi -= a * co;                                 // η = a sin φ
                if (bd != null) {                                            // seit S7b-1: im Band mit dem nächsten ω
                    double om = Wasseroptik.omega(k);
                    int bb = 0;
                    for (int c = 1; c < nb; c++) if (Math.abs(omega[c] - om) < Math.abs(omega[bb] - om)) bb = c;
                    bandWelle(bd, Wasserpunkte.KOPF + Wasserpunkte.JE_BAND * bb, a * si, -a * co, q[0], q[1], k, om, 1 / Math.tanh(Math.min(30, k * hh)));
                }
                xr += q[0] / k * a * co; yr += q[1] / k * a * co;
                double c = rr ? cR : Wasseroptik.omega(k) / k;
                qxr += c * a * si * q[0] / k; qyr += c * a * si * q[1] / k;
                ex += 0.5 * a * a * q[0] / k; ey += 0.5 * a * a * q[1] / k;
                mitt += k * 0.5 * a * a;
                neig += k * k * 0.5 * a * a;
                varG += 0.5 * a * a;
            }
        }
        p.zRe = zr; p.zIm = zi; p.etaLin = zr; p.m0 = m0; p.tiefe = h; p.kp = kp; p.neigung = neig; p.gezeichnet = varG;
        p.brechen = p.weiss = p.walze = 0;
        if (!(m0 > 0)) { p.eta = p.xi = p.yi = 0; p.lambda = p.s = p.huelle = 1; p.cRe = p.cIm = p.lw = p.mittel = p.ur = p.sk = p.as = 0; p.qx = p.qy = p.etaAntrieb = p.lwAntrieb = 0; return; }
        double sigma = Math.sqrt(m0), zabs = Math.hypot(zr, zi);
        double f = mitHoehen ? Seegangsform.huelle(zabs, m0, hh, tanA) : 1;
        double lam = mitVerschiebung ? Math.min(lambda, faltung / Math.sqrt(Math.max(1e-30, sxi2))) : 0;
        double ur = kp > 0 ? Seegangsform.ursell(4 * sigma, kp, hh) : 0;
        double[] rs = mitForm ? Seegangsform.ruessink(ur) : new double[] {0, 0, 0, 0};
        double skLag = kp > 0 ? Seegangsform.schiefeVerschiebung(lam, kp, hh, sigma) : 0;
        double[] c = mitForm ? Seegangsform.beiwert(rs[2], rs[3], sigma, skLag) : new double[] {0, 0, 1};
        double s = c[2], zr2 = s * f * zr, zi2 = s * f * zi;
        double eta = zr2 + c[0] * (zr2 * zr2 - zi2 * zi2) - c[1] * (2 * zr2 * zi2);
        double lw = mitLangwelle && kp > 0 ? -Seegangsform.langeWelle(kp, hh) * (zr2 * zr2 + zi2 * zi2 - 2 * m0 * s * s) : 0;
        double mittel = lam * s * s * mitt;
        p.eta = eta + lw + mittel;
        p.xi = lam * s * f * xr; p.yi = lam * s * f * yr;
        p.lambda = lam; p.huelle = f; p.s = s; p.cRe = c[0]; p.cIm = c[1]; p.lw = lw; p.mittel = mittel; p.ur = ur; p.sk = rs[2]; p.as = rs[3];
        double[] wz = Walze.walze(zr2, zi2, hh, kp);                       // seit S5a
        p.brechen = wz[0]; p.weiss = wz[1]; p.walze = wz[2];
        if (mitWalze) p.eta += Walze.HEBUNG * p.walze;                     // seit S5b-1: die Walze hebt die Front
        // seit S6b-2: Antrieb des Auflaufs (Punkt.qx, .qy, .etaAntrieb)
        double lwA = kp > 0 ? -Seegangsform.langeWelle(kp, hh) * (zr2 * zr2 + zi2 * zi2 - 2 * m0 * s * s) : 0;
        double eta2 = c[0] * (zr2 * zr2 - zi2 * zi2) - c[1] * (2 * zr2 * zi2), el = Math.hypot(ex, ey);
        double dx = el > 0 ? ex / el : 0, dy = el > 0 ? ey / el : 0;
        double cp = kp > 0 ? Dispersion.sigma(kp, hh) / kp : 0, cgr = kp > 0 ? Dispersion.n(kp, hh) * cp : 0;
        p.qx = s * f * qxr + (eta2 * cp + lwA * cgr) * dx;
        p.qy = s * f * qyr + (eta2 * cp + lwA * cgr) * dy;
        p.lwAntrieb = lwA;
        p.etaAntrieb = p.eta - lw + lwA;
    }

    /**
     * Welche Stützstellen die Synthese zeichnet (SF23 A, Befunde B36, B41): eine Stützstelle des Bands b mit der Varianz e
     * am Ort zählt, wenn e > T_E = max(E_MIN, E_REL · m0) <b>oder</b> k̄_b² e > T_S = S_REL · S, mit k̄_b = Σ e k / Σ e des
     * Bands und S = Σ_b k̄_b² E_b aus den Kennwerten (ohne Fernfeld) [G]. So bleiben die kurzen Wellen, die wenig Varianz,
     * aber viel Neigung tragen. Zwischen der Schwelle und ihrem Doppelten blendet die Amplitude linear ein ({@link #faktor}),
     * damit keine Welle an einer Linie aufspringt; was nicht gezeichnet wird, geht ins Glitzern. Dazu zählt eine Stützstelle
     * nur, wenn sie in irgendeiner Zelle einer Stufe über einer der Schwellen liegt ({@link #aktiv}); die Karte geht nur
     * diese durch.
     */
    public static final double E_MIN = 1e-12, E_REL = 1e-3, S_REL = 1e-3;

    /** Je Stützstelle: liegt sie in irgendeiner Zelle einer Stufe über einer der Schwellen? Aus {@link #vorbereiten}. */
    public boolean[] aktiv;

    /** Aus Kennwerten kv (m0, sxi2, E je Band, E k je Band): k̄_b² nach kq, zurück S = Σ k̄_b² E_b. */
    public static double neigung(double[] kv, int off, int nb, double[] kq) {
        double sum = 0;
        for (int b = 0; b < nb; b++) {
            double eb = kv[off + 2 + b];
            double k = eb > 0 ? kv[off + 2 + nb + b] / eb : 0;
            kq[b] = k * k;
            sum += kq[b] * eb;
        }
        return sum;
    }

    /**
     * Anteil der Amplitude, den die Synthese von der Stützstelle mit der Varianz e zeichnet: 0 unter beiden Schwellen,
     * 1 über dem Doppelten einer von ihnen, dazwischen linear.
     */
    public static double faktor(double e, double eSchwelle, double kq, double sSchwelle) {
        double f = e > eSchwelle ? Math.min(1, e / eSchwelle - 1) : 0;
        if (e > E_MIN && sSchwelle > 0 && kq * e > sSchwelle) f = Math.max(f, Math.min(1, kq * e / sSchwelle - 1));
        return f;
    }

    /** Seit S7b-1: eine Welle Z = re + i im in die rohen Summen ihres Bands ab o (Aufbau in {@link Wasserpunkte}). */
    private static void bandWelle(double[] bd, int o, double re, double im, double kx, double ky, double k, double om, double ct) {
        double az = Math.hypot(re, im);
        bd[o] += az; bd[o + 1] += az * k;
        bd[o + 2] += re; bd[o + 3] += im;
        bd[o + 4] += k * re; bd[o + 5] += k * im;
        bd[o + 6] += kx * re; bd[o + 7] += kx * im;
        bd[o + 8] += ky * re; bd[o + 9] += ky * im;
        if (k > 0) {
            double c = om * ct / k;
            bd[o + 10] += c * kx * re; bd[o + 11] += c * kx * im;
            bd[o + 12] += c * ky * re; bd[o + 13] += c * ky * im;
        }
        bd[o + 14] += om * re; bd[o + 15] += om * im;
    }

    /** Höhe der Fläche über dem festen Ort (x, y) zur Zeit t (Euler): Punkt mit x₀ + ξ(x₀) = x, feste Punktiteration. */
    public double euler(double x, double y, double t, Punkt p) {
        double ax = x, ay = y;
        for (int it = 0; it < 12; it++) {
            punkt(ax, ay, t, p);
            double nx = x - p.xi, ny = y - p.yi;
            double d = Math.hypot(nx - ax, ny - ay);
            ax = nx; ay = ny;
            if (d < 1e-7) break;
        }
        punkt(ax, ay, t, p);
        return p.eta;
    }

    /**
     * Lage und Normale des Punkts: {X, Y, Z, n_x, n_y, n_z, J} aus zentralen Differenzen mit dem Abstand d (m);
     * J = det(∂(X, Y)/∂(x, y)) (unter 0: die Fläche faltet sich).
     */
    public double[] lage(double x, double y, double t, double d) {
        Punkt p = new Punkt();
        punkt(x, y, t, p);
        double X = x + p.xi, Y = y + p.yi, Z = p.eta;
        punkt(x + d, y, t, p); double xa = x + d + p.xi, ya = y + p.yi, za = p.eta;
        punkt(x - d, y, t, p); double xb = x - d + p.xi, yb = y + p.yi, zb = p.eta;
        punkt(x, y + d, t, p); double xc = x + p.xi, yc = y + d + p.yi, zc = p.eta;
        punkt(x, y - d, t, p); double xd = x + p.xi, yd = y - d + p.yi, zd = p.eta;
        double ux = (xa - xb) / (2 * d), uy = (ya - yb) / (2 * d), uz = (za - zb) / (2 * d);
        double vx = (xc - xd) / (2 * d), vy = (yc - yd) / (2 * d), vz = (zc - zd) / (2 * d);
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new double[] {X, Y, Z, nx / l, ny / l, nz / l, ux * vy - uy * vx};
    }

    // ------------------------------------------------------------------ Buchhaltung für das Bild

    /**
     * Erwartete Varianz der linearen Fläche am Ort und die Neigungsvarianz: {m0, mss gesamt, mss aufgelöst} für einen
     * Bildpunkt der Größe fuss (m). Je Stützstelle zählt (k + Δ ∂T/∂s)² über die Teilwellen der Hülle, also
     * k² + 2k ⟨Δ⟩ |∇T| + ⟨Δ²⟩ |∇T|². Eine Welle zählt aufgelöst mit g = clamp(λ / (2 fuss) − 1, 0, 1) ihrer Amplitude,
     * wie in Stufe 2 ({@link Seegangsoptik#amplituden}), λ wie in {@link #punkt} aus max(|∇Φ|, kUnten); Stützstellen
     * unter den Schwellen von punkt ({@link #E_REL}, {@link #S_REL}) zählen nur in mss gesamt. Der Rest geht ins Glitzern.
     */
    public double[] buch(double x, double y, double t, double fuss) {
        int n = nb * nd;
        if (aktiv == null || (fein != null && fein.eZ == null) || (grob != null && grob.eZ == null)) vorbereiten();
        double[] ef = new double[n], eg = new double[n], o = new double[6], tmp = new double[6], af = new double[2], ag = new double[2];
        int[] cf = new int[4], cg = new int[4];
        double wf = fein != null ? fein.gewicht(x, y) : 0;
        double wg = grob != null ? (1 - wf) * grob.gewicht(x, y) : 0;
        double wfern = Math.max(0, 1 - wf - wg);
        if (fernfeld == null) { double s = wf + wg; if (s > 0) { wf /= s; wg /= s; } wfern = 0; }
        int kw = 2 + 2 * nb;
        double[] kf = new double[kw], kg = new double[kw], kv = new double[kw], kq = new double[nb];
        if (wf > 0) { fein.zellen(x, y, cf, af); fein.energie(cf, af, ef); fein.kennwerte(cf, af, kf); }
        if (wg > 0) { grob.zellen(x, y, cg, ag); grob.energie(cg, ag, eg); grob.kennwerte(cg, ag, kg); }
        for (int k = 0; k < kw; k++) kv[k] = (wf > 0 ? wf * kf[k] : 0) + (wg > 0 ? wg * kg[k] : 0);
        double sSchwelle = S_REL * neigung(kv, 0, nb, kq);
        double m0 = 0, mss = 0, auf = 0;
        double[] kU = kUnten(), es = new double[n], amp = null;
        for (int bq = 0; bq < n; bq++) { es[bq] = (wf > 0 ? wf * ef[bq] : 0) + (wg > 0 ? wg * eg[bq] : 0); if (es[bq] > 0) m0 += es[bq]; }
        if (wfern > 0 && fernfeld != null) {
            double[] w = fernfeld.werte(x, y);
            amp = Seegangsoptik.amplituden(w[0], w[1], w[2], 1e-4, wellenFern, grenzenFern, tafelFern);
            for (int j = 0; j < Seegangsoptik.LANG; j++) m0 += 0.5 * amp[j] * amp[j] * wfern;
        }
        double eSchwelle = Math.max(E_MIN, E_REL * m0);                         // wie in punkt: darunter nicht gezeichnet
        for (int bq = 0; bq < n; bq++) {
            double e = es[bq];
            if (!(e > 0)) continue;
            Stufe s = wf > 0 ? fein : grob;
            s.phase(bq, x, y, t, o, tmp);
            double fa = aktiv[bq] ? faktor(e, eSchwelle, kq[bq / nd], sSchwelle) : 0;
            double k = Math.hypot(o[2], o[3]), it = Math.hypot(o[4], o[5]), g = fa > 0 ? fa * aufgeloest(Math.max(k, kU[bq / nd]), fuss / 2) : 0;
            double k2 = k * k + 2 * k * puffer.mittelD[bq] * it + puffer.quadD[bq] * it * it;      // Teilwellen: k + Δ ∂T/∂s
            mss += k2 * e; auf += k2 * e * g * g;
        }
        if (amp != null)
            for (int j = 0; j < Seegangsoptik.LANG; j++) {
                double a2 = amp[j] * amp[j] * wfern;
                if (a2 <= 0) continue;
                double k = Math.hypot(wellenFern[j][0], wellenFern[j][1]), g = aufgeloest(k, fuss / 2);
                mss += k * k * 0.5 * a2; auf += k * k * 0.5 * a2 * g * g;
            }
        return new double[] {m0, mss, auf};
    }

    /** Die Stufen in der Reihenfolge fein, grob (ohne null). */
    public List<Stufe> stufen() {
        List<Stufe> l = new ArrayList<>();
        if (fein != null) l.add(fein);
        if (grob != null) l.add(grob);
        return l;
    }
}
