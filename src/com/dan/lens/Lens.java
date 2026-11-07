package com.dan.lens;

import java.util.List;
import java.util.Locale;

/**
 * Das Objektiv der Kamera: Brennweite, Blende, Fokus, Lamellen, Vergütung, und was daraus im Bild folgt. Die Werte sind
 * öffentliche Felder und zur Laufzeit änderbar; {@link #optics} rechnet daraus mit einem echten Linsenschnitt
 * ({@link LensSystem}, vorgegeben der Doppel-Gauß, auf die Brennweite gebracht) alles, was die Grafik braucht:
 * <ul>
 * <li><b>Tiefenschärfe</b> nach der dünnen Linse: Unschärfekreis c = D · f · (d − s) / (d · (s − f)) auf dem Sensor
 * (D = f / N, s Fokus, d Abstand), mit Vorzeichen (hinter dem Fokus positiv);</li>
 * <li><b>Bokeh</b>: die Form der Blende, das Katzenauge zum Rand (Kreise der Vorder- und Hinterfassung, aus der
 * paraxialen Rechnung, stimmt mit den echten Strahlen), die Helligkeit im Kreis aus der sphärischen Aberration (die
 * Querabweichung echter Randstrahlen über die ganze Pupille, je Unschärfe als Verteilung der Landepunkte: der Doppel-Gauß
 * hat eine unterkorrigierte Zone und einen überkorrigierten Rand, darum hinter dem Fokus helle Ränder, davor ein weicher
 * Kern), die Farbsäume aus dem Längsfarbfehler;</li>
 * <li><b>Fokusatmung</b>: Fokussieren schiebt das Objektiv vom Sensor weg, der Bildwinkel wird kleiner;</li>
 * <li><b>Verzeichnung, Farbquerfehler, Randabfall</b> aus echten Hauptstrahlen und dem Anteil der Pupille, der durch alle
 * Fassungen kommt, mal cos⁴;</li>
 * <li><b>Geisterbilder</b> der Sonne aus allen Doppelspiegelungen, je Farbkanal mit eigener Lage, Größe und Stärke;</li>
 * <li><b>Stern</b>: Beugung an den geraden Kanten der Blende (Fraunhofer: je Kante ein Strahl mit 1/x², der Energie
 * L·λ·f / (4π²·x²·A) je Länge), mit dem Bild der Sonnenscheibe gefaltet.</li>
 * </ul>
 * Alle Helligkeiten gelten in Einheiten der Szene: Ein Bildpunkt mit dem Wert L ist eine Fläche der Leuchtdichte L; die
 * Belichtung legt die App danach an. Die Blende ändert sie nicht (wie mit einem Graufilter ausgeglichen), wohl aber die
 * Stärke der Reflexe gegen die Szene: Abgeblendet werden sie kleiner und schärfer, nicht dunkler.
 */
public final class Lens {

    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Halbmesser der Sonnenscheibe (rad), im Mittel 0,2666°. */
    public static final double SUN_RADIUS = Math.toRadians(0.2666);
    /** Punkte der Tabellen über den Bildradius 0 … Halbdiagonale. */
    public static final int TABLE = 17;
    /** Tabelle der Helligkeit im Unschärfekreis: 16 Zeilen (8 Unschärfen vor, 8 hinter dem Fokus), 16 Ringe. */
    public static final int SA_ROWS = 16, SA_BINS = 16;
    /** Höchstens so viele Geisterbilder gehen an die Grafik (die hellsten). */
    public static final int MAX_GHOSTS = 48;

    public volatile Quality quality = Quality.MEDIUM;
    /** Brennweite (mm, Kleinbild 24 mm Bildhöhe wie {@link #sensorHeightMm}). */
    public volatile double focalMm = 50;
    /** Blendenzahl (f/N); nicht kleiner als die volle Öffnung des Schnitts. */
    public volatile double fNumber = 2.8;
    /** Fokus (m), wenn nicht {@link #autoFocus}. */
    public volatile double focusM = 10;
    /** Fokus auf die Bildmitte (die App misst die Tiefe dort). */
    public volatile boolean autoFocus = true;
    /** Bildhöhe des Sensors (mm) und Seitenverhältnis des Bilds (Breite / Höhe). */
    public volatile double sensorHeightMm = 24, aspect = 16 / 9.0;
    /** Anamorphotisch: Stauchung (1 = sphärisch, 2 = Cinemascope); macht das Bokeh oval [G]. */
    public volatile double squeeze = 1;
    public final Aperture aperture = new Aperture(8, 0.35, 0);
    public final FocusPuller puller = new FocusPuller();
    public volatile boolean depthOfField = true, ghosts = true, star = true, distortion = true, chromatic = true, vignetting = true, glare = true,
            breathing = true, sphericalAberration = true;
    /** Stärken (1 = nach Rechnung). */
    public volatile double ghostGain = 1, starGain = 1, vignettingGain = 1;
    /** Schleierlicht: Anteil, den Fassungen, Kanten und Staub über das ganze Bild streuen (Veiling Glare Index) [G]. */
    public volatile double glareFraction = 0.008;
    public volatile Coating coating = Coating.mgf2(550);
    /**
     * Bildkreis: 0 = die Fassungen so groß, dass der Schnitt das Bild bis in die Ecken deckt (der Doppel-Gauß ist für ±22°
     * gerechnet, das Bild der App reicht mit 24 mm Höhe und 16 : 9 bei 50 mm bis ±26°) [G]; 1 = wie gerechnet.
     */
    public volatile double coverage = 0;
    /**
     * Die Schnitte (WF41 A): Weitwinkel (Nakamura, ±38°) unter 35 mm, Doppel-Gauß (±22°) bis 130 mm, Tele (250 mm, ±6°)
     * darüber; je mit dem halben Feld, für das er gerechnet ist, und dem, bis zu dem er ehrlich rechnet.
     */
    public enum Design {
        WIDE("Weitwinkel (Nakamura)", 38, 40), DOUBLE_GAUSS("Doppel-Gauß (Tronnier)", 22, 26), TELE("Tele (250 mm)", 6, 10.5);
        public final String label;
        public final double designHalfField, maxHalfField;
        Design(String l, double d, double m) { label = l; designHalfField = Math.toRadians(d); maxHalfField = Math.toRadians(m); }
        LensSystem build() {
            return switch (this) {
                case WIDE -> LensSystem.wideAngle(Coating.NONE);
                case DOUBLE_GAUSS -> LensSystem.doubleGauss50(Coating.NONE);
                case TELE -> LensSystem.telephoto250(Coating.NONE);
            };
        }
    }

    /** Schnitt: null = nach der Brennweite (unter 35 mm Weitwinkel, ab 130 mm Tele). */
    public volatile Design design;

    public Design designJetzt() {
        Design d = design;
        if (d != null) return d;
        return focalMm < 35 ? Design.WIDE : focalMm >= 130 ? Design.TELE : Design.DOUBLE_GAUSS;
    }

    private final java.util.EnumMap<Design, LensSystem> bases = new java.util.EnumMap<>(Design.class);

    private LensSystem base() { return bases.computeIfAbsent(designJetzt(), Design::build); }

    /** Halber Bildwinkel, für den der Doppel-Gauß gerechnet ist (Smith: ±22°). */
    public static final double DESIGN_HALF_FIELD = Math.toRadians(22);
    /**
     * Weiter als bis ±26° (Halbdiagonale) rechnet der Doppel-Gauß nicht mehr ehrlich (über 30° reißen die Strahlen ab).
     * Bei kürzerer Brennweite überträgt er seine Fehler auf das größere Feld: Der Feldwinkel der Tabellen und des
     * Katzenauges wird mit {@link #fieldScale()} gestaucht, wie bei einem Weitwinkel derselben Bauart [G].
     */
    public static final double MAX_HALF_FIELD = Math.toRadians(26);

    /** tan des gerechneten Felds / tan des echten Felds an der Halbdiagonale (1 bis zur Grenze, darunter kleiner). */
    public double fieldScale() {
        double t = 0.5 * sensorHeightMm * Math.hypot(1, aspect) / focalMm;
        return Math.min(1, Math.tan(designJetzt().maxHalfField) / t);
    }


    /** Alles, was die Grafik für ein Bild braucht. Längen in mm auf dem Sensor, Winkel in rad. */
    public record Optics(
            double efl, double fNumber, double tStop, double pupilMm, double focusMm, double breathing, double halfDiagMm,
            double[] relIllum, double[] distortion, double[] latR, double[] latB,
            double[] catEye, double[] saProfile, double saScale, double dzR, double dzB,
            int ghostCount, float[] ghostData, double[] spikeAngles, double[] spikeCoef, double sunRadiusMm, double areaFactor, double fieldScale,
            double[] transverseMm, double pupilBehindFrontMm, double frontRadiusMm) {
        // pupilBehindFrontMm, frontRadiusMm (seit W9c Runde 2): wie weit die Eintrittspupille hinter dem Scheitel der Frontlinse
        // liegt und wie groß diese ist (mm), für Tropfen auf der Frontlinse (FrontDrops)
        // transverseMm (seit LituyaBay1958 W9c Runde 2): TA(ρ) echter Randstrahlen in Grün an ρ = i/32 (mm in der Bildebene
        // für Unendlich, positiv auf der Seite des Pupillenorts), für die Kino-Stufe (com.dan.shutter.Aberration)

        /** Unschärfekreis (Durchmesser mm, mit Vorzeichen) für den Abstand d (m). */
        public double cocMm(double dM) {
            double d = dM * 1000, s = focusMm, f = efl;
            if (d <= f || s <= f) return 0;
            return pupilMm * f * (d - s) / (d * (s - f));
        }

        /** Helligkeit im Unschärfekreis (Mittel 1) bei s = r/R für den Kreis c (mm, mit Vorzeichen); wie saWeight im Shader. */
        public double saWeight(double s, double cMm) {
            if (saScale <= 0) return 1;
            double m = Math.log(Math.max(0.5 * Math.abs(cMm) / saScale, 1e-4)) / Math.log(2) + 2;
            double far = 1 - smooth((m - 7));
            m = Math.max(0, Math.min(7, m));
            int base = cMm < 0 ? 0 : 8, m0 = (int) Math.floor(m), m1 = Math.min(m0 + 1, 7);
            double fm = m - m0, x = Math.max(0, Math.min(1, s)) * 16 - 0.5;
            int b0 = (int) Math.max(0, Math.min(15, Math.floor(x))), b1 = Math.min(b0 + 1, 15);
            double fb = Math.max(0, Math.min(1, x - b0));
            double v0 = saProfile[(base + m0) * 16 + b0] * (1 - fb) + saProfile[(base + m0) * 16 + b1] * fb;
            double v1 = saProfile[(base + m1) * 16 + b0] * (1 - fb) + saProfile[(base + m1) * 16 + b1] * fb;
            return 1 + ((v0 * (1 - fm) + v1 * fm) - 1) * far;
        }

        private static double smooth(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

        /** Hyperfokale Entfernung (m) für den zulässigen Kreis c (mm). */
        public double hyperfocalM(double cMm) { return (efl * efl / (fNumber * cMm) + efl) / 1000; }

        /** Nahgrenze und Ferngrenze der Schärfe (m) für c (mm); Ferngrenze ∞ jenseits der hyperfokalen Entfernung. */
        public double[] dofM(double cMm) {
            double h = hyperfocalM(cMm) * 1000, s = focusMm, f = efl;
            double near = s * (h - f) / (h + s - 2 * f), far = s >= h ? Double.POSITIVE_INFINITY : s * (h - f) / (h - s);
            return new double[]{near / 1000, far / 1000};
        }
    }

    private volatile String cacheKey, pendingKey;
    private String ghostKey;
    /** Tabellen im Hintergrund nachrechnen (false: sofort, für Prüfungen und Standbilder). */
    public volatile boolean background = true;
    private volatile double[][] cache;
    private float[] ghostCache;
    private int ghostCnt;

    /** Der Schnitt auf die Brennweite gebracht und vergütet. */
    public LensSystem system() {
        LensSystem b = base();
        double k = focalMm / b.efl(Glass.LAMBDA_D);
        LensSystem s = b.scaled(k).coated(coating);
        double halfDiag = 0.5 * sensorHeightMm * Math.hypot(1, aspect);
        double cover = coverage > 0 ? coverage : Math.max(1, halfDiag * fieldScale() / (focalMm * Math.tan(designJetzt().designHalfField)));
        return cover > 1.0001 ? s.covering(cover) : s;
    }

    /** Volle Öffnung (kleinste Blendenzahl) des Schnitts. */
    public double fNumberMin() { return base().fNumberMin(Glass.LAMBDA_D); }

    /** Rechnet alles für den Fokus focusNowM (m; der Schärfezieher oder die App bestimmt ihn). */
    public Optics optics(double focusNowM) {
        LensSystem s = system();
        double lg = Spectrum.RGB_NM[1];
        double f = s.efl(lg);
        double n = Math.max(fNumberMin(), Math.min(64, fNumber));
        double rStopMax = s.surfaces.get(s.stop).semiAperture();
        double a0 = Math.abs(s.stopAB(lg)[0]);
        double rep = f / (2 * n);
        double rStop = Math.min(rStopMax, rep * a0);
        double focusMm = Math.max(f * 1.05, focusNowM * 1000);
        double zInf = s.back() + s.bfl(lg);
        double zSensor = s.imageZ(focusMm - 0.0, lg);
        double k0 = s.imageScale(zSensor, lg);
        double breath = breathing ? k0 / s.imageScale(zInf, lg) : 1;
        double halfDiag = 0.5 * sensorHeightMm * Math.hypot(1, aspect);
        double trans = s.primaryTransmission(lg);

        // ---- Tabellen über den Bildradius (vom Fokus kaum abhängig: bei Unendlich gerechnet, je Brennweite und Blende gemerkt)
        String key = String.format(Locale.ROOT, "%.4f|%.4f|%.4f|%s|%.3f|%s|%s", focalMm, n, halfDiag, coating.name(), coverage, quality, designJetzt());
        // Die Tabellen (echte Strahlen, rund 25 ms) rechnet ein eigener Faden, wenn es schon welche gibt; bis dahin gelten die alten
        if (!key.equals(cacheKey) && cache != null && background) {
            if (!key.equals(pendingKey)) {
                pendingKey = key;
                final double fF = f, nF = n, rS = rStop, rE = rep, hD = halfDiag, zI = zInf;
                final LensSystem sF = s;
                Thread t = new Thread(() -> {
                    double[][] c2 = tables(sF, fF, nF, rS, rE, hD, zI, lg);
                    synchronized (this) { if (key.equals(pendingKey)) { cache = c2; cacheKey = key; pendingKey = null; } }
                }, "com.dan.lens Tabellen");
                t.setDaemon(true);
                t.start();
            }
        } else if (!key.equals(cacheKey)) {
            cache = tables(s, f, n, rStop, rep, halfDiag, zInf, lg);
            cacheKey = key;
        }
        double[][] c = cache;


        // ---- Geisterbilder je Farbkanal (gleiche Reihenfolge i, j in allen drei Listen); gemerkt, solange sich Sensor und Blende kaum ändern
        String gkey = key + String.format(Locale.ROOT, "|%.3f", zSensor);
        if (!gkey.equals(ghostKey)) {
            List<LensSystem.Ghost>[] gl = new List[3];
            double[] k0c = new double[3];
            for (int ch = 0; ch < 3; ch++) {
                double l = Spectrum.RGB_NM[ch];
                gl[ch] = s.ghosts(l, zSensor);
                k0c[ch] = s.imageScale(zSensor, l);
            }
            int ng = gl[1].size();
            double[] score = new double[ng];
            double[][] gi = new double[ng][12];
            for (int g = 0; g < ng; g++) {
                for (int ch = 0; ch < 3; ch++) {
                    LensSystem.Ghost gh = gl[ch].get(g);
                    double rad = Math.abs(gh.scale()) * rStop;
                    double sunR = Math.abs(gh.ratio() * k0c[ch]) * SUN_RADIUS;
                    double eff = Math.max(Math.max(rad, sunR), 0.01);
                    double entry = Math.min(rStop * gh.entry(), s.surfaces.get(0).semiAperture());
                    // Fluss E·π·r_ein²·RT auf die Fläche π·ρ², als Leuchtdichte im Bild: mal 4N²/π
                    double inten = entry * entry * gh.rt() * 4 * n * n / (Math.PI * eff * eff);
                    gi[g][ch] = gh.ratio();
                    gi[g][4 + ch] = eff;
                    gi[g][8 + ch] = inten;
                }
                gi[g][3] = Math.signum(gl[1].get(g).scale());
                score[g] = Math.max(gi[g][8], Math.max(gi[g][9], gi[g][10])) * Math.min(1, gi[g][5] * gi[g][5] / 0.05);
            }
            Integer[] idx = new Integer[ng];
            for (int i = 0; i < ng; i++) idx[i] = i;
            java.util.Arrays.sort(idx, (x, y) -> Double.compare(score[y], score[x]));
            int cnt = Math.min(MAX_GHOSTS, ng);
            float[] gd = new float[cnt * 12];
            for (int k = 0; k < cnt; k++) for (int q = 0; q < 12; q++) gd[k * 12 + q] = (float) gi[idx[k]][q];
            ghostKey = gkey;
            ghostCache = gd;
            ghostCnt = cnt;
        }
        float[] gd = ghostCache;
        int cnt = ghostCnt;

        // ---- Stern: je Strahl Koeffizient der Leuchtdichte P(x, y) = E · coef / x² · Profil(y) (x, y in mm)
        double af = aperture.areaFactor();
        double rp = (rep) / Math.sqrt(af);   // Umkreis der Pupille (mm) bei gleicher Fläche wie der Kreis f/N
        double edge = aperture.straightEdge() * rp;
        double[] ang = aperture.spikeAngles();
        double[] coef = new double[3];
        for (int ch = 0; ch < 3; ch++) {
            double lam = Spectrum.RGB_NM[ch] * 1e-6;
            coef[ch] = 4 * n * n / Math.PI * aperture.edgesPerSpike() * edge * lam * f / (4 * Math.PI * Math.PI);
        }
        double sunMm = k0 * SUN_RADIUS;
        double fieldScale = fieldScale();

        return new Optics(f, n, n / Math.sqrt(trans), 2 * rep, focusMm, breath, halfDiag, c[0], c[1], c[2], c[3], c[4],
                c[6], c[5][0], c[5][2], c[5][3], cnt, gd, ang, coef, sunMm, af, fieldScale, c[7],
                s.entrancePupilZ(lg) - s.front(), s.surfaces.get(0).semiAperture());
    }


    /** Tabellen über den Bildradius aus echten Strahlen: Randabfall, Verzeichnung, Farbquerfehler, Katzenauge, Bokeh, Längsfarbfehler. */
    private double[][] tables(LensSystem s, double f, double n, double rStop, double rep, double halfDiag, double zInf, double lg) {
        double[] ri = new double[TABLE], di = new double[TABLE], lr = new double[TABLE], lb = new double[TABLE];
        double fs = fieldScale();
        int grid = quality == Quality.LOW || quality == Quality.MEDIUM ? 20 : 28;
        for (int i = 0; i < TABLE; i++) {
            double r = i / (TABLE - 1.0);
            double th = Math.atan(r * halfDiag / f * fs);
            if (i == 0) { ri[i] = 1; di[i] = 1; lr[i] = 1; lb[i] = 1; continue; }
            double v = s.vignetting(th, rStop, lg, zInf, grid);
            double c = Math.cos(th);
            ri[i] = v * c * c * c * c;
            double hg = s.chiefHeight(th, lg, zInf), hr = s.chiefHeight(th, Spectrum.RGB_NM[0], s.back() + s.bfl(Spectrum.RGB_NM[0])),
                    hb = s.chiefHeight(th, Spectrum.RGB_NM[2], s.back() + s.bfl(Spectrum.RGB_NM[2]));
            double ideal = f * Math.tan(th);
            di[i] = Double.isNaN(hg) ? di[i - 1] : hg / ideal;
            lr[i] = Double.isNaN(hr) || Double.isNaN(hg) ? lr[i - 1] : hr / hg;
            lb[i] = Double.isNaN(hb) || Double.isNaN(hg) ? lb[i - 1] : hb / hg;
        }
        // Katzenauge: die Fassungen, die am Rand (Halbdiagonale) am meisten wegschneiden, je eine nach innen und außen
        double thMax = Math.atan(halfDiag / f * fs);
        double[][] mc = s.mountCircles(rStop, lg);
        double[] cat = {99, 0, 99, 0};
        double best1 = 1e9, best2 = 1e9;
        for (double[] c : mc) {
            if (c == null) continue;
            double clip = c[0] - Math.abs(c[1]) * thMax;
            if (c[1] >= 0 && clip < best1) { best1 = clip; cat[0] = c[0]; cat[1] = c[1]; }
            if (c[1] < 0 && clip < best2) { best2 = clip; cat[2] = c[0]; cat[3] = c[1]; }
        }
        // sphärische Aberration: Querabweichung TA(ρ) echter Randstrahlen über die Pupille; daraus die Helligkeit im
        // Unschärfekreis für 8 Unschärfen je Seite des Fokus (Landung L(ρ) = αρ + TA(ρ), α = −c/2, Pupille gleichmäßig)
        double[] sap = new double[SA_ROWS * SA_BINS];
        double saMax = 0;
        double[] taR = new double[33];
        for (int i = 1; i <= 32; i++) {
            double tv = s.transverse(Math.min(0.999, i / 32.0) * rep, lg, zInf);
            taR[i] = Double.isNaN(tv) ? taR[i - 1] : tv;
            saMax = Math.max(saMax, Math.abs(taR[i]));
        }
        for (int row = 0; row < SA_ROWS; row++) {
            int side = row / (SA_ROWS / 2), mag = row % (SA_ROWS / 2);
            double alpha = (side == 0 ? 1 : -1) * Math.max(saMax, 1e-6) * Math.pow(2, mag - 2);   // Zeile 0–7: vor dem Fokus (c < 0), 8–15: dahinter
            double[] hist = new double[SA_BINS];
            int nr = 4000;
            double[] lv = new double[nr];
            double lmax = 0;
            for (int k = 0; k < nr; k++) {
                double rho = (k + 0.5) / nr, x = rho * 32, fr = x - Math.floor(x);
                int ix = (int) Math.min(31, Math.floor(x));
                double ta = taR[ix] + (taR[ix + 1] - taR[ix]) * fr;
                lv[k] = Math.abs(alpha * rho + ta);
                lmax = Math.max(lmax, lv[k]);
            }
            for (int k = 0; k < nr; k++) {
                double rho = (k + 0.5) / nr;
                int b = (int) Math.min(SA_BINS - 1, Math.floor(lv[k] / lmax * SA_BINS));
                hist[b] += 2 * rho / nr;
            }
            for (int b = 0; b < SA_BINS; b++) {
                double s1 = b / (double) SA_BINS, s2 = (b + 1) / (double) SA_BINS;
                sap[row * SA_BINS + b] = hist[b] / (s2 * s2 - s1 * s1);
            }
        }
        double dzr = s.bfl(Spectrum.RGB_NM[0]) - s.bfl(lg), dzb = s.bfl(Spectrum.RGB_NM[2]) - s.bfl(lg);
        return new double[][]{ri, di, lr, lb, cat, {saMax, 0, dzr, dzb}, sap, taR};
    }

    /** „50 mm · f/2,8 · Fokus 12,0 m · 8 Lamellen“ */
    public String describe(double focusNowM) {
        return String.format(Locale.GERMAN, "%s %.0f mm · f/%.1f · Fokus %s · %s · %s", designJetzt().label, focalMm, Math.max(fNumberMin(), fNumber),
                focusNowM > 5000 ? "∞" : String.format(Locale.GERMAN, "%.1f m", focusNowM), aperture, coating);
    }
}
