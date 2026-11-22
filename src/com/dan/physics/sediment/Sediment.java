package com.dan.physics.sediment;

/**
 * Sand, Schlick, Rippel und Boden (Werkbuch HalligHooge Teil A, 7.4 und 12.10, P9a): die Referenz in doppelter
 * Genauigkeit auf einem rechtwinkligen Gitter aus nx × ny Zellen, tiefengemittelt, mit einer Sand- und einer
 * Schlickfraktion [G].
 * <p>
 * <b>Eingang</b> (setzt der Aufrufer; am Bus mit P9b): Wassertiefe {@link #h} zu Beginn des Schritts, Fluss je Breite
 * auf den Kanten {@link #qx}, {@link #qy} (m²/s, Zeitmittel über den Schritt, wie {@code water.q}), Seegang
 * ({@link #ubot} quadratisches Mittel der Orbitalgeschwindigkeit am Boden, {@link #tp}, {@link #wcos}, {@link #wsin}),
 * Boden ({@link #grund}, {@link #d50}). Die Tiefe am Ende des Schritts folgt aus der Kontinuität; damit stimmt die
 * Volumenbilanz des Schwebstoffs auf Rundung.
 * <p>
 * <b>Schubspannung</b> (Soulsby 1997): Strom allein τ_c = ρ C_D U² mit C_D = [κ / (ln(h/z0) − 1)]²; Kornrauheit
 * z0 = d50/12, Formrauheit der Rippel z0 = η²/λ. Welle allein τ_w = ½ ρ f_w U_w², f_w = 1,39 (A/z0)^−0,52. Zusammen
 * τ_m = τ_c [1 + 1,2 (τ_w / (τ_c + τ_w))^3,2] und τ_max = [(τ_m + τ_w cos φ)² + (τ_w sin φ)²]^½. Das Sediment sieht die
 * Kornschubspannung.
 * <p>
 * <b>Sand:</b> Absinken und Schwelle nach Soulsby (1997), Geschiebe nach van Rijn (2007): q_b = 0,5 ρ_s d50 D*^−0,3
 * (τ_m/ρ)^½ (τ_max − τ_cr)/τ_cr in Richtung des Stroms [G: die Welle rührt, der Strom trägt], mit Hangneigung
 * q_b (û − ε ∇z_b). Schwebstoff: Bezugskonzentration nach van Rijn (2007), Tiefenmittel über den Formfaktor F aus
 * van Rijn (1984): c_gl = F c_a; Austausch mit dem Boden (c_gl − c) / T_s mit der Anpassungszeit T_s = (h/w_s) T_sd nach
 * Galappatti (1983), implizit; Advektion in Flussform, aufwind
 * mit der Korrektur zweiter Ordnung (van Leer), Diffusion nach Elder (5,93 u* h, gedeckelt auf ¼ (1 − Courant) Δ²/Δt, damit Advektion und Diffusion zusammen stabil bleiben).
 * <p>
 * <b>Schlick:</b> Erosion nach Partheniades (p_m M (τ_max/τ_e − 1)), Ablagerung nach Krone (w_s c (1 − τ_max/τ_d)); die
 * kritische Schubspannung wächst mit der Liegezeit von τ_e0 auf τ_e1 [G].
 * <p>
 * <b>Boden:</b> Exner mit Porosität; Böschungsrutsch über dem Ruhewinkel (Sand 30°, Schlick steiler [G]);
 * <b>Rippel</b> nach Soulsby und Whitehouse (2005) mit einer Anpassungszeit aus dem Geschiebe [G], unter der Schwelle
 * eingefroren.
 * <p>
 * Jeder Fluss über den Rand des Gebiets ist verbucht ({@link #aus}); {@link #masse()} gibt den Bestand. Kennt nur
 * java.* (Schichtenvertrag, Werkbuch 4). Dieselben Formeln in float auf der Karte: {@code gpu.SedimentKarte}.
 * <p>
 * Seit 0.2 (P9b) trennt es das Volumen der Zelle (Masse) von Tiefe und Geschwindigkeit des Stroms (Schubspannung), siehe
 * {@link #tiefe}; das braucht es am Bus, weil hydro mit Subgrid rechnet.
 * <p>
 * Seit 0.3 (P9c): <b>morphologischer Faktor</b> {@link #faktor} (Lesser u. a. 2004, Roelvink 2006) auf die Bodenänderung
 * aus Austausch und Geschiebe – Wasser, Transport und Schubspannung rechnen unverändert; {@link #sandWirklich} und
 * {@link #schlickWirklich} halten, was der Boden wirklich aufgenommen hat (für die Bilanz). <b>Deckel</b>
 * ({@link #deckel}): Der Sandboden einer Zelle ändert sich je Schritt höchstens um einen Anteil der Tiefe; darüber läuft
 * ihre Bodenzeit langsamer (örtlich kleinerer Faktor), gezählt in {@link #gedeckelt}. <b>Lockere Auflage</b>
 * ({@link #mitAuflage}; van Kessel u. a. 2011): Abgelagerter Schlick liegt bis auf den Anteil {@link #anteilTief} als
 * dünne Auflage auf dem Boden und wird über τ_a mit min(m M₁, M₀) (τ/τ_a − 1) wieder aufgenommen, unverdünnt; der Rest
 * geht in die Mischschicht wie bisher. Der Faktor wirkt auf die Mischschicht, nicht auf die Auflage. <b>Spirale</b>
 * ({@link #spiral}, {@code water.spiral}): Das Geschiebe dreht um tan δ = α_I I / |u| zur Innenkurve, α_I = 2/κ² E_s
 * (1 − ½ √c_D / κ) (Form wie im Handbuch von Delft3D, aus dem Gedächtnis [G]), |tan δ| ≤ 0,5.
 */
public final class Sediment {
    public static final String VERSION = "0.3";
    public static final double G = 9.81, KAPPA = 0.40;

    public final int nx, ny, nc;
    public final double dx, dy;

    // ------------------------------------------------------------------ Stoffwerte und gesetzte Größen
    /** Dichte des Wassers und des Korns (kg/m³), Zähigkeit (m²/s; Seewasser 10 °C), Porosität des Sandbodens. */
    public double rho = 1025, rhoS = 2650, nu = 1.36e-6, poros = 0.40;
    /** Unter dieser Tiefe (m) gilt die Zelle für das Sediment als trocken [G]. */
    public double hMin = 0.05;
    /** Hangneigung im Geschiebe: q_b (û − ε ∇z_b) [G]. */
    public double epsHang = 1.5;
    /** Dicke der Mischschicht (m) [G]: Schlickanteil p_m = m_Schlick / (m_Schlick + ρ_s (1 − n) δ). */
    public double deltaAkt = 0.05;
    /** Schlick [G]: Erosionsrate M (kg/(m² s)), τ_e frisch und konsolidiert (N/m²), Zeit der Konsolidierung (s), Absinken (m/s), τ_d (N/m²), Trockendichte (kg/m³). */
    public double mErosion = 1e-4, tauE0 = 0.15, tauE1 = 0.50, tKons = 7 * 86400.0, wsSchlick = 5e-4, tauD = 0.10, rhoSchlick = 500;
    /** Ruhewinkel als Steigung: Sand 30°, reiner Schlick 45° [G]. */
    public double hangSand = Math.tan(Math.toRadians(30)), hangSchlick = 1.0;
    /** Durchgänge des Böschungsrutsches je Teilschritt und Anteil des Überschusses je Durchgang [G]. */
    public int rutschSchritte = 2;
    public double rutschAnteil = 0.25;
    /** Über diesem Schlickanteil bilden sich keine Rippel [G]. */
    public double rippelSchlick = 0.3;
    /** Größte Courant-Zahl der Advektion je Teilschritt. */
    public double courant = 0.8;
    public boolean mitGeschiebe = true, mitSchweb = true, mitSchlick = true, mitRutsch = true, mitRippel = true, mitDiffusion = true, zweiteOrdnung = true;
    /** Konzentration im Zufluss am Rand (kg/m³); NaN: Sand mit der Gleichgewichtskonzentration der Randzelle [G], Schlick mit der Konzentration der Randzelle. */
    public double randSand = Double.NaN, randSchlick = Double.NaN;

    // ------------------------------------------------------------------ Eingang
    /** Je Zelle: Wassertiefe (m), Boden zu Beginn (m), Korngröße (m), Orbitalgeschwindigkeit (m/s, quadratisches Mittel), Periode (s), Richtung der Wellen (cos, sin). */
    public final double[] h, grund, d50, ubot, tp, wcos, wsin;
    /** Fluss je Breite (m²/s): {@code qx[i + (nx + 1) j]} über die Westkante der Zelle (i, j), {@code qy[i + nx j]} über die Südkante. */
    public final double[] qx, qy;
    /**
     * Seit 0.2 (P9b), wahlweise je Zelle (null: wie 0.1): {@link #tiefe} mittlere Tiefe über der benetzten Fläche (m, wie
     * {@code water.h}) und {@link #ug}, {@link #vg} Geschwindigkeit von hydro (m/s) für die Schubspannung – {@link #h} ist
     * dann Volumen durch Zellfläche und trägt die Masse; der benetzte Anteil h/tiefe mindert Geschiebe und Erosion der
     * Zelle. {@link #porosZ}: Porosität des Sandbodens je Zelle (null: {@link #poros}).
     */
    public double[] tiefe, ug, vg, porosZ;
    /**
     * Seit 0.2, wahlweise je Zelle (null: überall 1): Anteil der Zelle mit losem Boden (0–1). Fester Boden (Salzwiese, Fenne,
     * Deich, Warft, Deckwerk) gibt weder Sand noch Schlick ab und trägt keine Rippel; ablagern kann sich überall etwas.
     */
    public double[] frei;
    /** Größte Zahl der Teilschritte je {@link #schritt} (0: ohne Grenze); darüber begrenzt der Abfluss je Zelle den Transport. */
    public int teileMax;
    /** Seit 0.3: morphologischer Faktor auf die Bodenänderung (1: ohne). */
    public double faktor = 1;
    /** Seit 0.3, Deckel [G]: größte Änderung des Sandbodens je {@link #schritt} als Anteil der Tiefe (0: ohne) und ihr Mindestwert (m). */
    public double deckel, deckelMin = 0.001;
    /** Seit 0.3, lockere Auflage [G]: an; Schwelle τ_a (N/m²), Rate erster Ordnung M₁ (1/s), Höchstrate M₀ (kg/(m² s)), Anteil der Ablagerung, der gleich in die Mischschicht geht. */
    public boolean mitAuflage;
    public double tauA = 0.10, mA1 = 1e-4, mA0 = 1e-4, anteilTief = 0.1;
    /** Seit 0.3: {@code water.spiral} je Zelle (m/s; null: ohne) und der Beiwert E_s [G]. */
    public double[] spiral;
    public double eSpirale = 1;
    /** Tiefe (m), ab der eine Zelle die Zahl der Teilschritte bestimmt (0: ab {@link #hMin}); flachere schützt die Begrenzung des Abflusses [G]. */
    public double hRate;

    // ------------------------------------------------------------------ Zustand
    /** {@code sed.c} in zwei Teilen: Sand und Schlick im Wasser (kg/m³). */
    public final double[] cs, cm;
    /** {@code bed.dz.sediment} in zwei Teilen: Bodenänderung aus Sand und aus Schlick (m). */
    public final double[] dzS, dzM;
    /** Schlick in der Mischschicht (kg/m²) und seine Liegezeit (s). */
    public final double[] mSchlick, alter;
    /** {@code bed.ripple}: Höhe und Länge (m), Richtung als Vektor (Länge 0–1; in die der formende Strom lief). */
    public final double[] rH, rL, rX, rY;
    /** Seit 0.3: lockere Auflage (kg/m²); wirklich vom Boden aufgenommen seit Beginn (kg/m²): Sand, Schlick der Mischschicht; Teilschritte am Deckel je Zelle. */
    public final double[] mAuflage, sandWirklich, schlickWirklich, gedeckelt;
    private double intervall;
    private final double[] eAuf;

    // ------------------------------------------------------------------ abgeleitet, je Teilschritt
    /** Kornschubspannung im Mittel und größte (N/m²), Rauheit {@code surf.z0.sediment} (m). */
    public final double[] tauM, tauMax, z0;
    private final boolean[] nass;
    private final double[] fak, ceq, ks, kd, qbx, qby, qbm, eSchlick, kdS, pm, hang, hNeu, zb, zb2, fxS, fyS, fxM, fyM, gx, gy;

    /** Über den Rand hinaus (kg): Sand im Wasser, Schlick im Wasser, Geschiebe. */
    public final double[] aus = new double[3];
    public double zeit;
    public long teilschritte;

    public Sediment(int nx, int ny, double dx, double dy) {
        this.nx = nx; this.ny = ny; this.dx = dx; this.dy = dy;
        nc = nx * ny;
        h = new double[nc]; grund = new double[nc]; d50 = new double[nc]; ubot = new double[nc]; tp = new double[nc]; wcos = new double[nc]; wsin = new double[nc];
        qx = new double[(nx + 1) * ny]; qy = new double[nx * (ny + 1)];
        cs = new double[nc]; cm = new double[nc]; dzS = new double[nc]; dzM = new double[nc]; mSchlick = new double[nc]; alter = new double[nc];
        rH = new double[nc]; rL = new double[nc]; rX = new double[nc]; rY = new double[nc];
        mAuflage = new double[nc]; sandWirklich = new double[nc]; schlickWirklich = new double[nc]; gedeckelt = new double[nc]; eAuf = new double[nc];
        tauM = new double[nc]; tauMax = new double[nc]; z0 = new double[nc];
        nass = new boolean[nc];
        fak = new double[nc]; ceq = new double[nc]; ks = new double[nc]; kd = new double[nc]; qbx = new double[nc]; qby = new double[nc]; qbm = new double[nc];
        eSchlick = new double[nc]; kdS = new double[nc]; pm = new double[nc]; hang = new double[nc]; hNeu = new double[nc]; zb = new double[nc]; zb2 = new double[nc];
        fxS = new double[qx.length]; fyS = new double[qy.length]; fxM = new double[qx.length]; fyM = new double[qy.length]; gx = new double[qx.length]; gy = new double[qy.length];
        java.util.Arrays.fill(d50, 2e-4);
        java.util.Arrays.fill(wcos, 1);
    }

    // ------------------------------------------------------------------ Formeln (auch für die Prüfung)

    /** Dimensionslose Korngröße D* = d [g (s − 1) / ν²]^⅓. */
    public static double dStern(double d, double s, double nu) { return d * Math.cbrt(G * (s - 1) / (nu * nu)); }

    /** Absinkgeschwindigkeit nach Soulsby (1997): w_s = ν/d [(10,36² + 1,049 D*³)^½ − 10,36]. */
    public static double sink(double d, double s, double nu) {
        double ds = dStern(d, s, nu);
        return nu / d * (Math.sqrt(10.36 * 10.36 + 1.049 * ds * ds * ds) - 10.36);
    }

    /** Kritischer Shields-Wert nach Soulsby und Whitehouse (1997): 0,30 / (1 + 1,2 D*) + 0,055 (1 − e^(−0,020 D*)). */
    public static double thetaKrit(double ds) { return 0.30 / (1 + 1.2 * ds) + 0.055 * (1 - Math.exp(-0.020 * ds)); }

    /** Widerstandsbeiwert des Stroms über der Tiefe h bei der Rauheit z0 (Soulsby 1997); h/z0 mindestens 20. */
    public static double cdStrom(double h, double z0) {
        double l = Math.log(Math.max(h / z0, 20)) - 1;
        return KAPPA * KAPPA / (l * l);
    }

    /** Reibungsbeiwert der Welle auf rauem Boden (Soulsby 1997): 1,39 (A/z0)^−0,52, höchstens 0,3. */
    public static double fwRau(double a, double z0) { return Math.min(0.3, 1.39 * Math.pow(a / z0, -0.52)); }

    /** Mittlere Schubspannung aus Strom und Welle (Soulsby 1997, DATA2). */
    public static double tauMittel(double tc, double tw) {
        double sum = tc + tw;
        return sum <= 0 ? 0 : tc * (1 + 1.2 * Math.pow(tw / sum, 3.2));
    }

    /** Größte Schubspannung aus Strom und Welle; cosPhi, sinPhi: Winkel zwischen Strom und Welle. */
    public static double tauGroesste(double tm, double tw, double cosPhi, double sinPhi) {
        double a = tm + tw * cosPhi, b = tw * sinPhi;
        return Math.sqrt(a * a + b * b);
    }

    /** Geschiebe nach van Rijn (2007) in kg/(s m): 0,5 ρ_s d D*^−0,3 (τ_m/ρ)^½ (τ_max − τ_cr)/τ_cr. */
    public static double geschiebe(double d, double ds, double rho, double rhoS, double tm, double tx, double tcr) {
        return tx <= tcr ? 0 : 0.5 * rhoS * d * Math.pow(ds, -0.3) * Math.sqrt(tm / rho) * (tx - tcr) / tcr;
    }

    /** Bezugskonzentration nach van Rijn (2007) in kg/m³ in der Höhe a: 0,015 ρ_s (d/a) T^1,5 / D*^0,3, höchstens 0,05 ρ_s. */
    public static double bezug(double d, double ds, double rhoS, double a, double t) {
        return t <= 0 ? 0 : Math.min(0.05 * rhoS, 0.015 * rhoS * d / a * Math.pow(t, 1.5) * Math.pow(ds, -0.3));
    }

    /** Formfaktor nach van Rijn (1984): Tiefenmittel der Konzentration durch die Bezugskonzentration; ah = a/h, z = Rouse-Zahl. Zwischen ah und 1. */
    public static double formfaktor(double ah, double z) {
        double f;
        if (Math.abs(z - 1.2) < 1e-3) f = -Math.pow(ah, 1.2) * Math.log(ah) / Math.pow(1 - ah, 1.2);
        else f = (Math.pow(ah, z) - Math.pow(ah, 1.2)) / (Math.pow(1 - ah, z) * (1.2 - z));
        return Math.min(1, Math.max(ah, f));
    }

    /**
     * Dimensionslose Anpassungszeit des Schwebstoffs im Tiefenmittel nach Galappatti (1983), wie bei Lesser (2009, Gl. 2.38):
     * T_sd = w* exp[(1,547 − 20,12 u_r) w*³ + (326,832 u_r^2,2047 − 0,2) w*² + (0,1385 ln u_r − 6,4061) w* + (0,5467 u_r + 2,1963)]
     * mit u_r = u*&#47;U und w* = w_s/u*; die Anpassungszeit ist T_s = (h/w_s) T_sd. Eingänge auf den Bereich der Formel
     * begrenzt (u_r 0,02–0,15, w* 0,005–1), das Ergebnis auf 0,01–1 (1: Absinken in ruhendem Wasser).
     */
    public static double anpassung(double ur, double w) {
        ur = Math.min(0.15, Math.max(0.02, ur));
        w = Math.min(1, Math.max(0.005, w));
        double t = w * Math.exp((1.547 - 20.12 * ur) * w * w * w + (326.832 * Math.pow(ur, 2.2047) - 0.2) * w * w + (0.1385 * Math.log(ur) - 6.4061) * w + (0.5467 * ur + 2.1963));
        return Math.min(1, Math.max(0.01, t));
    }

    /** Rippel im Strom nach Soulsby und Whitehouse (2005): {Höhe, Länge} im Gleichgewicht (m) beim Shields-Wert theta; Länge 0 heißt keine Rippel. */
    public static double[] rippel(double d, double ds, double theta) {
        if (ds < 1.2 || ds > 16) return new double[] {0, 0};
        double hMax = d * 202 * Math.pow(ds, -0.554), lMax = d * (500 + 1881 * Math.pow(ds, -1.5));
        double two = ds <= 1.58 ? 0.916 : 1.66 * Math.pow(ds, -1.3), tsf = ds <= 1.58 ? 2.26 : 4.09 * Math.pow(ds, -1.3);
        if (theta <= two) return new double[] {hMax, lMax};
        if (theta < tsf) return new double[] {hMax * (tsf - theta) / (tsf - two), lMax};
        return new double[] {0, lMax};
    }

    /** Beiwert der Spirale α_I = 2/κ² E_s (1 − ½ √c_D / κ); √c_D / κ = √g / (κ C) mit dem Chézy-Wert C. */
    public static double alphaSpirale(double cd, double es) { return 2 / (KAPPA * KAPPA) * es * (1 - 0.5 * Math.sqrt(cd) / KAPPA); }

    // ------------------------------------------------------------------ Lage

    public int zelle(int i, int j) { return i + nx * j; }
    public int kx(int i, int j) { return i + (nx + 1) * j; }
    public int ky(int i, int j) { return i + nx * j; }

    /** Boden jetzt (m): Grund plus Änderung aus Sand und Schlick. */
    public double boden(int c) { return grund[c] + dzS[c] + dzM[c]; }

    /** Porosität des Sandbodens der Zelle. */
    public double porositaet(int c) { return porosZ != null ? porosZ[c] : poros; }

    /** Schlickanteil der Mischschicht {@code bed.mud} (0–1). */
    public double schlickanteil(int c) { return mSchlick[c] / (mSchlick[c] + rhoS * (1 - porositaet(c)) * deltaAkt); }

    /** {@code sed.c}: Schwebstoff im Wasser (kg/m³), Sand und Schlick zusammen. */
    public double schweb(int c) { return cs[c] + cm[c]; }

    /** Gleichgewichtskonzentration des Sands im Tiefenmittel (kg/m³) und Rate des Austauschs mit dem Boden (1/s) aus dem letzten Teilschritt. */
    public double gleichgewicht(int c) { return ceq[c]; }
    public double austausch(int c) { return ks[c]; }

    /** Geschiebe aus dem letzten Teilschritt (kg/(s m)). */
    public double geschiebe(int c) { return qbm[c]; }

    /** Geschiebe aus dem letzten Teilschritt als Vektor {x, y} (kg/(s m)); seit 0.3 mit der Ablenkung durch die Spirale. */
    public double[] geschiebeVektor(int c) { return new double[] {qbx[c], qby[c]}; }

    /** Richtung der Rippel (rad, 0 = +x, gegen den Uhrzeigersinn). */
    public double rippelRichtung(int c) { return Math.atan2(rY[c], rX[c]); }

    /** Legt überall diese Schlickmasse in die Mischschicht, die dem Anteil p entspricht. */
    public void schlick(double p) { for (int c = 0; c < nc; c++) schlick(c, p); }

    /** Legt in die Zelle die Schlickmasse, die dem Anteil p entspricht. */
    public void schlick(int c, double p) { mSchlick[c] = p >= 1 ? 1e9 : p / (1 - p) * rhoS * (1 - porositaet(c)) * deltaAkt; }

    /** Bestand (kg): {Sand im Wasser, Sand im Boden (aus dzS), Schlick im Wasser, Schlick im Boden (Mischschicht)}. */
    public double[] masse() {
        double[] m = new double[4];
        double a = dx * dy;
        for (int c = 0; c < nc; c++) {
            m[0] += h[c] * cs[c] * a;
            m[1] += dzS[c] * rhoS * (1 - porositaet(c)) * a;
            m[2] += h[c] * cm[c] * a;
            m[3] += mSchlick[c] * a;
        }
        return m;
    }

    /** Seit 0.3: {wirklich vom Boden aufgenommener Sand (kg), ebenso Schlick der Mischschicht (kg), lockere Auflage (kg), Teilschritte am Deckel}. */
    public double[] wirklich() {
        double[] m = new double[4];
        double a = dx * dy;
        for (int c = 0; c < nc; c++) {
            m[0] += sandWirklich[c] * a;
            m[1] += schlickWirklich[c] * a;
            m[2] += mAuflage[c] * a;
            m[3] += gedeckelt[c];
        }
        return m;
    }

    // ------------------------------------------------------------------ Schritt

    /** Größte Rate (1/s), mit der eine nasse Zelle ihr Wasser abgibt: Σ Abfluss / (Breite · h). */
    public double rate() {
        double r = 0;
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            int c = zelle(i, j);
            if (h[c] < Math.max(hMin, hRate)) continue;
            double a = abfluss(i, j) / h[c];
            if (a > r) r = a;
        }
        return r;
    }

    private double abfluss(int i, int j) {
        return (Math.max(0, -qx[kx(i, j)]) + Math.max(0, qx[kx(i + 1, j)])) / dx + (Math.max(0, -qy[ky(i, j)]) + Math.max(0, qy[ky(i, j + 1)])) / dy;
    }

    /** Ein Schritt über dt Sekunden in so vielen Teilschritten, wie die Courant-Zahl verlangt; gibt ihre Zahl zurück. */
    public int schritt(double dt) {
        int n = teile(dt);
        intervall = dt;
        for (int k = 0; k < n; k++) teil(dt / n);
        return n;
    }

    /** Zahl der Teilschritte für einen Schritt über dt Sekunden: nach der Courant-Zahl, höchstens {@link #teileMax}. */
    public int teile(double dt) {
        int n = Math.max(1, (int) Math.ceil(dt * rate() / courant));
        return teileMax > 0 ? Math.min(n, teileMax) : n;
    }

    /** Ein Teilschritt: abgeleitete Größen, Transport und Austausch, Boden, Böschung, Rippel. */
    public void teil(double dt) {
        ableiten(dt);
        transport(dt);
        if (mitRutsch) for (int k = 0; k < rutschSchritte; k++) rutsch();
        System.arraycopy(hNeu, 0, h, 0, nc);
        zeit += dt;
        teilschritte++;
    }

    /** Je Zelle: Schubspannung, Gleichgewicht und Raten des Austauschs, Geschiebe, Rippel. */
    private void ableiten(double dt) {
        double s = rhoS / rho, gewicht = G * (rhoS - rho);
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            int c = zelle(i, j);
            double hc = h[c];
            zb[c] = boden(c);
            pm[c] = mSchlick[c] / (mSchlick[c] + rhoS * (1 - porositaet(c)) * deltaAkt);
            hang[c] = hangSand + pm[c] * (hangSchlick - hangSand);
            hNeu[c] = Math.max(0, hc - dt * ((qx[kx(i + 1, j)] - qx[kx(i, j)]) / dx + (qy[ky(i, j + 1)] - qy[ky(i, j)]) / dy));
            nass[c] = hc >= hMin;
            fak[c] = 1; ceq[c] = 0; ks[c] = 0; kd[c] = 0; qbx[c] = 0; qby[c] = 0; qbm[c] = 0; eSchlick[c] = 0; kdS[c] = 0; tauM[c] = 0; tauMax[c] = 0; eAuf[c] = 0;
            double d = d50[c], z0s = d / 12, z0f = rL[c] > 0 ? rH[c] * rH[c] / rL[c] : 0;
            z0[c] = z0s + z0f;
            if (!nass[c]) continue;
            double ab = abfluss(i, j) * dt;
            if (ab > hc) fak[c] = hc / ab;
            double ht = tiefe != null ? Math.max(tiefe[c], hc) : hc, fr = frei != null ? frei[c] : 1, anteil = fr * hc / ht;
            double u = ug != null ? ug[c] : 0.5 * (qx[kx(i, j)] + qx[kx(i + 1, j)]) / hc, v = vg != null ? vg[c] : 0.5 * (qy[ky(i, j)] + qy[ky(i, j + 1)]) / hc, uu = Math.hypot(u, v);
            double tcs = rho * cdStrom(ht, z0s) * uu * uu, tct = rho * cdStrom(ht, z0[c]) * uu * uu;
            double tw = 0, cosPhi = 1, sinPhi = 0, uw = Math.sqrt(2) * ubot[c];
            if (uw > 1e-4 && tp[c] > 0) {
                tw = 0.5 * rho * fwRau(uw * tp[c] / (2 * Math.PI), z0s) * uw * uw;
                if (uu > 1e-9) { cosPhi = (u * wcos[c] + v * wsin[c]) / uu; sinPhi = (v * wcos[c] - u * wsin[c]) / uu; }
            }
            double tm = tauMittel(tcs, tw), tx = tauGroesste(tm, tw, cosPhi, sinPhi);
            tauM[c] = tm; tauMax[c] = tx;
            double ds = dStern(d, s, nu), ws = sink(d, s, nu), thCr = thetaKrit(ds), tcr = thCr * gewicht * d, t = Math.max(0, (tx - tcr) / tcr);
            double qb = geschiebe(d, ds, rho, rhoS, tm, tx, tcr);
            if (mitGeschiebe && uu > 1e-9) {
                qbm[c] = anteil * (1 - pm[c]) * qb;
                double ex = u / uu, ey = v / uu;
                if (spiral != null && spiral[c] != 0) {                      // Spirale: zur Innenkurve (links bei positiver Krümmung)
                    double td = Math.max(-0.5, Math.min(0.5, alphaSpirale(cdStrom(ht, z0[c]), eSpirale) * spiral[c] / uu)), nd = Math.sqrt(1 + td * td);
                    double x = (ex - td * ey) / nd;
                    ey = (ey + td * ex) / nd;
                    ex = x;
                }
                qbx[c] = qbm[c] * ex; qby[c] = qbm[c] * ey;
            }
            if (mitSchweb) {
                double a = Math.min(0.2 * ht, Math.max(0.01, 0.5 * rH[c])), us = Math.sqrt(Math.max(tct, tx) / rho);
                double f = a / ht, tsd = 1;
                if (us > 1e-9 && uu > 1e-9) {
                    double beta = Math.min(1.5, 1 + 2 * (ws / us) * (ws / us));
                    f = formfaktor(a / ht, ws / (beta * KAPPA * us));
                    tsd = anpassung(us / uu, ws / us);
                }
                ceq[c] = fr * (1 - pm[c]) * f * bezug(d, ds, rhoS, a, t);
                ks[c] = ws / (tsd * ht);
            }
            if (mitDiffusion) kd[c] = Math.min(5.93 * Math.sqrt(tct / rho) * ht, 0.25 * (1 - courant) * Math.min(dx, dy) * Math.min(dx, dy) / dt);   // Elder; gedeckelt: Advektion (≤ courant) und Diffusion über vier Kanten zusammen ≤ 1
            if (mitSchlick) {
                double te = tauE0 + (tauE1 - tauE0) * (1 - Math.exp(-alter[c] / tKons));
                if (tx > te) eSchlick[c] = anteil * pm[c] * mErosion * (tx / te - 1);
                if (tx < tauD) kdS[c] = wsSchlick * (1 - tx / tauD) / ht;
                if (mitAuflage && tx > tauA && mAuflage[c] > 0) eAuf[c] = hc / ht * Math.min(mAuflage[c] * mA1, mA0) * (tx / tauA - 1);
            }
            if (mitRippel && qb > 0) {
                double[] gl = rippel(d, ds, tm / (gewicht * d));
                double[] mx = rippel(d, ds, 0);
                if (mx[1] > 0) {
                    double zeitR = rhoS * (1 - porositaet(c)) * 0.5 * mx[0] * mx[1] / qb, an = 1 - Math.exp(-dt / zeitR);
                    double ohne = fr * Math.max(0, 1 - pm[c] / rippelSchlick);
                    rH[c] += an * (gl[0] * ohne - rH[c]);
                    rL[c] += an * (gl[1] - rL[c]);
                    if (uu > 1e-9) { rX[c] += an * (u / uu - rX[c]); rY[c] += an * (v / uu - rY[c]); }
                }
            }
        }
    }

    /** Fluss des Schwebstoffs über eine innere Kante (kg/(s m)): aufwind mit Korrektur zweiter Ordnung, dazu Diffusion. l, r: Zellen beiderseits; ll, rr: dahinter (−1: keine). */
    private double kante(double q, int l, int r, int ll, int rr, double delta, double dt, double[] c) {
        double f = 0;
        int auf = q > 0 ? l : r, ab = q > 0 ? r : l, hinter = q > 0 ? ll : rr;
        if (q != 0 && nass[auf]) {
            double ca = c[auf], wert = ca;
            if (zweiteOrdnung && nass[ab] && hinter >= 0 && nass[hinter]) {
                double d = c[ab] - ca, x = ca - c[hinter];
                if (d != 0 && x != 0 && (x > 0) == (d > 0)) {
                    double phi = 2 * x / (x + d), cr = Math.min(1, Math.abs(q) * dt / (delta * h[auf]));
                    wert = ca + 0.5 * phi * (1 - cr) * d;
                }
            }
            f = q * wert * fak[auf];
        }
        if (nass[l] && nass[r]) f -= 0.5 * (kd[l] + kd[r]) * Math.min(h[l], h[r]) * (c[r] - c[l]) / delta;
        return f;
    }

    /** Fluss über eine Randkante (kg/(s m)), positiv in Richtung +x bzw. +y; innen: die Zelle am Rand, aussenPlus: liegt der Rand auf ihrer +Seite. */
    private double randkante(double q, int innen, boolean aussenPlus, double cInnen, double cRand) {
        boolean hinaus = aussenPlus ? q > 0 : q < 0;
        if (hinaus) return nass[innen] ? q * cInnen * fak[innen] : 0;
        return q * cRand;
    }

    /** Geschiebe über eine innere Kante (kg/(s m)): Spenderzelle je Vorzeichen, dazu der Hang. */
    private double geschiebeKante(double[] qb, int l, int r, double delta) {
        return Math.max(0, qb[l]) + Math.min(0, qb[r]) - epsHang * 0.5 * (qbm[l] + qbm[r]) * (zb[r] - zb[l]) / delta;
    }

    private void transport(double dt) {
        // Flüsse über die Kanten in x
        for (int j = 0; j < ny; j++) for (int i = 0; i <= nx; i++) {
            int e = kx(i, j);
            double q = qx[e];
            if (i == 0 || i == nx) {
                int c = zelle(i == 0 ? 0 : nx - 1, j);
                boolean plus = i == nx;
                fxS[e] = randkante(q, c, plus, cs[c], Double.isNaN(randSand) ? ceq[c] : randSand);
                fxM[e] = randkante(q, c, plus, cm[c], Double.isNaN(randSchlick) ? cm[c] : randSchlick);
                gx[e] = qbx[c];
                double nachAussen = plus ? 1 : -1;
                aus[0] += nachAussen * fxS[e] * dt * dy; aus[1] += nachAussen * fxM[e] * dt * dy; aus[2] += nachAussen * gx[e] * dt * dy;
            } else {
                int l = zelle(i - 1, j), r = zelle(i, j), ll = i >= 2 ? l - 1 : -1, rr = i <= nx - 2 ? r + 1 : -1;
                fxS[e] = kante(q, l, r, ll, rr, dx, dt, cs);
                fxM[e] = kante(q, l, r, ll, rr, dx, dt, cm);
                gx[e] = geschiebeKante(qbx, l, r, dx);
            }
        }
        for (int j = 0; j <= ny; j++) for (int i = 0; i < nx; i++) {
            int e = ky(i, j);
            double q = qy[e];
            if (j == 0 || j == ny) {
                int c = zelle(i, j == 0 ? 0 : ny - 1);
                boolean plus = j == ny;
                fyS[e] = randkante(q, c, plus, cs[c], Double.isNaN(randSand) ? ceq[c] : randSand);
                fyM[e] = randkante(q, c, plus, cm[c], Double.isNaN(randSchlick) ? cm[c] : randSchlick);
                gy[e] = qby[c];
                double nachAussen = plus ? 1 : -1;
                aus[0] += nachAussen * fyS[e] * dt * dx; aus[1] += nachAussen * fyM[e] * dt * dx; aus[2] += nachAussen * gy[e] * dt * dx;
            } else {
                int l = zelle(i, j - 1), r = zelle(i, j), ll = j >= 2 ? l - nx : -1, rr = j <= ny - 2 ? r + nx : -1;
                fyS[e] = kante(q, l, r, ll, rr, dy, dt, cs);
                fyM[e] = kante(q, l, r, ll, rr, dy, dt, cm);
                gy[e] = geschiebeKante(qby, l, r, dy);
            }
        }
        // Zellen: Schwebstoff, Austausch mit dem Boden, Exner
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            int c = zelle(i, j);
            double hn = hNeu[c], sandboden = rhoS * (1 - porositaet(c));
            double ms = h[c] * cs[c] - dt * ((fxS[kx(i + 1, j)] - fxS[kx(i, j)]) / dx + (fyS[ky(i, j + 1)] - fyS[ky(i, j)]) / dy);
            double mm = h[c] * cm[c] - dt * ((fxM[kx(i + 1, j)] - fxM[kx(i, j)]) / dx + (fyM[ky(i, j + 1)] - fyM[ky(i, j)]) / dy);
            double zuS, zuM, zuA = 0;                                                     // Zuwachs des Bodens und der Auflage (kg/m²), wirklich
            if (hn >= hMin && nass[c]) {
                // Zuwachs als Ablagerung minus Aufnahme (ohne Austausch genau null); ein negativer Rest wird aus dem Boden gedeckt
                double zs = ms + dt * ks[c] * hn * ceq[c], cn = zs > 0 ? zs / (hn * (1 + dt * ks[c])) : 0;
                zuS = zs > 0 ? dt * ks[c] * hn * (cn - ceq[c]) : ms;
                cs[c] = cn;
                double ero = Math.min(eSchlick[c] * dt, mSchlick[c] / faktor), eroA = mitAuflage ? Math.min(eAuf[c] * dt, mAuflage[c]) : 0;
                double zm = mm + ero + eroA, cmn = zm > 0 ? zm / (hn * (1 + dt * kdS[c])) : 0;
                if (zm > 0) {
                    double ab = dt * kdS[c] * hn * cmn, tief = mitAuflage ? anteilTief * ab : ab;
                    zuM = tief - ero;
                    zuA = ab - tief - eroA;
                } else {
                    zuM = mm + eroA;
                    zuA = -eroA;
                }
                cm[c] = cmn;
            } else if (hn >= hMin) {                                                      // eben benetzt: noch kein Austausch
                cs[c] = ms > 0 ? ms / hn : 0; zuS = ms > 0 ? 0 : ms;
                cm[c] = mm > 0 ? mm / hn : 0; zuM = mm > 0 ? 0 : mm;
            } else {                                                                      // fällt trocken: alles setzt sich ab
                zuS = ms;
                boolean auf = mitAuflage && mm > 0;                                       // Schlick bleibt als Auflage liegen
                zuM = auf ? 0 : mm;
                if (auf) zuA = mm;
                cs[c] = 0; cm[c] = 0;
            }
            double div = (gx[kx(i + 1, j)] - gx[kx(i, j)]) / dx + (gy[ky(i, j + 1)] - gy[ky(i, j)]) / dy;
            double einheit = zuS - dt * div, zu = faktor * einheit / sandboden;
            if (deckel > 0) {
                double grenze = Math.max(deckelMin, deckel * (tiefe != null ? Math.max(tiefe[c], h[c]) : h[c])) * (intervall > 0 ? dt / intervall : 1);
                if (Math.abs(zu) > grenze) { zu = Math.signum(zu) * grenze; gedeckelt[c]++; }
            }
            dzS[c] += zu;
            sandWirklich[c] += einheit;
            double alt = mSchlick[c];
            mSchlick[c] = Math.max(0, alt + faktor * zuM);
            dzM[c] += (mSchlick[c] - alt) / rhoSchlick;
            schlickWirklich[c] += (mSchlick[c] - alt) / faktor;
            mAuflage[c] = Math.max(0, mAuflage[c] + zuA);
            if (mSchlick[c] > alt && mSchlick[c] > 0) alter[c] = (alter[c] + dt) * alt / mSchlick[c];   // frische Ablagerung ist jung
            else alter[c] += dt;
        }
    }

    /** Ein Durchgang des Böschungsrutsches: über jede Kante geht ein Anteil dessen, was über dem Ruhewinkel liegt, bergab (Volumen bleibt). */
    private void rutsch() {
        for (int c = 0; c < nc; c++) { zb[c] = boden(c); zb2[c] = 0; }
        boolean etwas = false;
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            int c = zelle(i, j);
            if (i + 1 < nx) etwas |= rutschKante(c, c + 1, dx);
            if (j + 1 < ny) etwas |= rutschKante(c, c + nx, dy);
        }
        if (etwas) for (int c = 0; c < nc; c++) dzS[c] += zb2[c];
    }

    private boolean rutschKante(int a, int b, double delta) {
        double d = zb[b] - zb[a], grenze = (d > 0 ? hang[b] : hang[a]) * delta, ueber = Math.abs(d) - grenze;
        if (ueber <= 0) return false;
        double t = rutschAnteil * 0.5 * ueber * Math.signum(d);
        zb2[a] += t; zb2[b] -= t;
        return true;
    }
}
