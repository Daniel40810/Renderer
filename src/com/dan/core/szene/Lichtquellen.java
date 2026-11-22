package com.dan.core.szene;

import com.dan.core.astro.Gestirne;
import com.dan.core.raum.Utm;
import com.dan.core.szene.spektrum.Spektrum;

import java.time.Instant;

/**
 * Sonne, Mond und Nachthimmel als Lichtquellen der Szene (P6c; seit P7a, F52 A, in der gemeinsamen Szene, vorher in
 * {@code render.air.Himmel}). Örter aus {@code core.astro} (wahre Richtung, ohne Brechung, mit Parallaxe), in die Szene
 * gedreht um die Meridiankonvergenz von UTM; die Scheiben stehen mit der Strahlenbrechung der Normalatmosphäre
 * (Sæmundsson) höher.
 * <p>
 * Licht in lx und cd/m² für die drei Kanäle des Echtzeitbilds: Die Sonne bringt außerhalb der Luft 127 500 lx je Kanal
 * in 1 AE (weiß, wie {@code sky}); der Mond die Helligkeit nach seinem Phasenwinkel mit der Farbe {@link #MONDFARBE}.
 * Die Referenz rechnet dieselben Örter und Stärken, verteilt sie aber über das Spektrum ({@code spektrum}); das sind
 * 133 100 lx in der Farbe der Sonne. Für direkt beleuchtete Flächen gleicht {@link #SONNE_FARBE} das aus (seit P7k).
 */
public final class Lichtquellen {
    private Lichtquellen() { }

    /** Beleuchtungsstärke der Sonne außerhalb der Luft in 1 AE (lx je Kanal), wie {@code sky} (127 500 lx). */
    public static final double SONNE_LX = 127_500;
    /**
     * Stärke und Farbe der direkten Sonne je Kanal als Faktor auf {@link #SONNE_LX} (seit P7k, F68 A): das Spektrum der
     * Sonne, mit dem die Referenz und der Himmel rechnen ({@link Spektrum#sonne}, 133 100 lx), nach linearem sRGB, durch
     * SONNE_LX; rund 1,145 / 1,020 / 0,984. Das Echtzeitbild nimmt ihn für alles, was Sonne und Mond direkt beleuchten
     * (Gelände, Wasser); die Luft rechnet weiter mit SONNE_LX je Kanal und der Himmel mit dem Spektrum.
     */
    public static final double[] SONNE_FARBE = sonnenfarbe();

    private static double[] sonnenfarbe() {
        double[] s = new double[3];
        for (int l = Spektrum.MIN; l <= Spektrum.MAX; l++) {
            double[] x = Spektrum.xyz(l);
            double e = Spektrum.sonne(l);
            for (int c = 0; c < 3; c++) s[c] += x[c] * e;
        }
        double[] rgb = Spektrum.nachRgb(s);
        double f = Spektrum.sonneLux() / (s[1] * SONNE_LX);
        return new double[] {rgb[0] * f, rgb[1] * f, rgb[2] * f};
    }

    // ------------------------------------------------------------------ seit P10d: Farbe der direkten Sonne nach der Luft

    /** Schritte bis zur Oberkante für die Säulen der Dichte (quadratisch wachsend, wie {@link Scattering#opticalDepthExact}; bei 3,2° auf 3 · 10⁻⁴ genau) [G]. */
    public static final int FARBE_SCHRITTE = 256;
    /** Höhe über dem Boden, für die der Faktor gilt (m): wie {@code gelaende.frag} und {@code wasser.frag}, h = max(0,5, …). */
    public static final double FARBE_HOEHE = 0.5;
    /** Je nm von {@link Spektrum#MIN} bis {@link Spektrum#MAX}: Gewichte nach linearem sRGB (lx je Einheit der Sonne in 1 AE), Rayleigh und Ozon je m. */
    private static double[][] nmGewicht;
    private static double[] nmRayleigh, nmOzon;

    private static synchronized void nmTafeln() {
        if (nmGewicht != null) return;
        int n = Spektrum.MAX - Spektrum.MIN + 1;
        double[][] g = new double[3][n];
        double[] ra = new double[n], oz = new double[n];
        for (int q = 0; q < n; q++) {
            int l = Spektrum.MIN + q;
            double[] x = Spektrum.xyz(l);
            double e = Spektrum.sonne(l) * Spektrum.KM;
            double[] rgb = Spektrum.nachRgb(x);
            for (int c = 0; c < 3; c++) g[c][q] = rgb[c] * e;
            ra[q] = Spektrum.rayleigh(l, Atmosphere.RAYLEIGH_H);
            oz[q] = Spektrum.OZON_DICHTE * Spektrum.ozon(l);
        }
        nmRayleigh = ra;
        nmOzon = oz;
        nmGewicht = g;
    }

    /**
     * Säulen der Dichte von der Höhe h (m über dem Boden) in Richtung mu (Kosinus zur Senkrechten) bis zur Oberkante:
     * {∫ ρ_Rayleigh, ∫ ρ_Mie, ∫ ρ_Ozon} in m; null, wenn der Weg den Boden trifft.
     */
    public static double[] saeulen(double h, double mu, int n) {
        double R = Atmosphere.BOTTOM, r = R + Math.max(0, h);
        if (mu < 0 && r * r * mu * mu - (r * r - R * R) >= 0) return null;
        double RT = Atmosphere.TOP, d = -r * mu + Math.sqrt(Math.max(0, (RT - r) * (RT + r) + r * r * mu * mu));
        double[] s = new double[3];
        for (int k = 0; k < n; k++) {
            double a = (double) k / n, b = (k + 1.0) / n, t0 = d * a * a, t1 = d * b * b, t = 0.5 * (t0 + t1), dt = t1 - t0;
            double hh = Math.sqrt(r * r + t * t + 2 * r * mu * t) - R;
            s[0] += Atmosphere.rayleighDensity(hh) * dt;
            s[1] += Atmosphere.mieDensity(hh) * dt;
            s[2] += Atmosphere.ozoneDensity(hh) * dt;
        }
        return s;
    }

    /**
     * Direkte Sonne nach der Luft, spektral (360–830 nm je nm, Beiwerte wie die Referenz: Bodhaine, Ozon nach seinen
     * Querschnitten, Mie ohne Gang mit der Wellenlänge), nach linearem sRGB: Beleuchtungsstärke je Kanal (lx) einer Sonne,
     * die außerhalb der Luft {@link #SONNE_LX} je Kanal bringt (also das Spektrum nach ASTM mal SONNE_LX / 127 500). Am
     * Ort in der Höhe h, Richtung mu. null, wenn der Weg den Boden trifft.
     */
    public static double[] sonneSpektral(Atmosphere at, double h, double mu) {
        double[] s = saeulen(h, mu, FARBE_SCHRITTE);
        return s == null ? null : sonneSpektral(at, s);
    }

    /** Wie oben, aus den Säulen der Dichte. */
    public static double[] sonneSpektral(Atmosphere at, double[] s) {
        nmTafeln();
        double[] e = new double[3];
        double tm = at.mieExtinction * s[1];
        for (int q = 0; q < nmRayleigh.length; q++) {
            double t = Math.exp(-(nmRayleigh[q] * s[0] + tm + nmOzon[q] * s[2]));
            for (int c = 0; c < 3; c++) e[c] += nmGewicht[c][q] * t;
        }
        return e;
    }

    /** Durchlässigkeit der drei Kanäle des Echtzeitbilds (680 / 550 / 440 nm, Beiwerte wie {@link Atmosphere}) aus den Säulen. */
    public static double[] kanaele(Atmosphere at, double[] s) {
        double[] t = new double[3];
        for (int c = 0; c < 3; c++) t[c] = Math.exp(-(Atmosphere.RAYLEIGH[c] * s[0] + at.mieExtinction * s[1] + Atmosphere.OZONE[c] * s[2]));
        return t;
    }

    /**
     * Stärke und Farbe der direkten Sonne je Kanal nach der Luft, als Faktor auf {@link #SONNE_LX} mal die Durchlässigkeit
     * der drei Kanäle (seit P10d; ersetzt für Gelände und Wasser {@link #SONNE_FARBE}, das außerhalb der Luft gilt): das
     * Spektrum durch die Luft ({@link #sonneSpektral}) durch das, was die Karte für die drei Kanäle aus ihrer Tabelle liest
     * ({@link Scattering#durchWieTabelle}; so gleicht der Faktor auch die Tabelle aus, die bei 3,2° um 1 % zu viel
     * durchlässt). In {@link #FARBE_HOEHE} für die Richtung mu; trifft der Weg den Boden, {@link #SONNE_FARBE}. Nicht
     * unter 0 (unter 2° Höhe liegt die Farbe außerhalb von sRGB, das Blau würde negativ).
     */
    public static double[] farbeNachLuft(Atmosphere at, double mu) {
        double[] s = saeulen(FARBE_HOEHE, mu, FARBE_SCHRITTE);
        if (s == null) return SONNE_FARBE.clone();
        double[] e = sonneSpektral(at, s), t = Scattering.durchWieTabelle(at, Atmosphere.BOTTOM + FARBE_HOEHE, mu), f = new double[3];
        for (int c = 0; c < 3; c++) f[c] = t[c] > 1e-30 ? Math.max(0, e[c] / (SONNE_LX * t[c])) : SONNE_FARBE[c];
        return f;
    }

    /** Leuchtdichte des natürlichen Nachthimmels im Zenit (cd/m²): 22 mag je Quadratbogensekunde, wie {@code sky}. */
    public static final double NACHT = 1.08e5 * Math.pow(10, -0.4 * 22.0);
    /** Helligkeit der Sonne (mag), Radius der Sonne (m), Randverdunklung u [G]. */
    public static final double SONNE_MAG = -26.74, SONNE_RADIUS = 695_700_000, RAND_U = 0.6;
    /** Farbe des Monds je Kanal [G]. */
    public static final double[] MONDFARBE = {1.06, 0.98, 0.88};
    /** Radius des Monds (m). */
    public static final double MOND_RADIUS = 1_737_400;

    /**
     * Sonne und Mond für ein Auge zu einer Zeit. Richtungen in der Szene (x Ost im Gitter, y oben, z Süd im Gitter):
     * wahr ({@code sonne}, {@code mond}) und mit Brechung gehoben ({@code sonneSicht}, {@code mondSicht}).
     *
     * @param eSonne     Beleuchtungsstärke der Sonne außerhalb der Luft (lx je Kanal)
     * @param sinSonne   Sinus des Winkelradius der Sonne
     * @param lSonne     Leuchtdichte der Scheibe in der Mitte (cd/m², je Kanal), mit Randverdunklung u
     * @param eMond      Beleuchtungsstärke des Monds außerhalb der Luft, je Kanal (lx)
     * @param sinMond    Sinus des Winkelradius des Monds
     * @param lMond      Faktor der Leuchtdichte der Mondscheibe je Kanal (cd/m²; die Scheibe nach {@code himmel.comp})
     * @param phase      Phasenwinkel des Monds (Grad)
     */
    public record Stand(double[] sonne, double[] sonneSicht, double sonneHoehe, double sonneAzimut, double eSonne, double sinSonne, double lSonne,
                        double[] mond, double[] mondSicht, double mondHoehe, double mondAzimut, double[] eMond, double sinMond, double[] lMond, double phase) {
    }

    /** Sonne und Mond am Ort (UTM) zur Zeit t. */
    public static Stand bei(Instant t, double ost, double nord, int zone) {
        double[] ll = Utm.rueckwaerts(ost, nord, zone);
        Gestirne.Stand st = Gestirne.bei(t);
        Gestirne.Richtung rs = st.sonne(ll[0], ll[1]), rm = st.mond(ll[0], ll[1]);
        double[] s = inSzene(rs.ost(), rs.nord(), rs.oben(), ll, zone), m = inSzene(rm.ost(), rm.nord(), rm.oben(), ll, zone);
        double eS = SONNE_LX * Math.pow(Gestirne.AE / st.sonneAbstand, 2);
        double psi = phasenwinkel(st), ratio = Math.pow(10, -0.4 * (mondMag(psi, st.mondAbstand) - SONNE_MAG)), full = Math.pow(10, -0.4 * (mondMag(0, st.mondAbstand) - SONNE_MAG));
        double sinS = SONNE_RADIUS / rs.abstand(), sinM = MOND_RADIUS / rm.abstand();
        double omS = Math.PI * sinS * sinS, omM = Math.PI * sinM * sinM;
        double eM = SONNE_LX * ratio, k = SONNE_LX * full / (omM * 2.0 / 3.0);
        return new Stand(s, gehoben(s, rs.hoeheGrad()), rs.hoeheGrad(), rs.azimutGrad(), eS, sinS, eS / omS / (1 - RAND_U / 3),
                m, gehoben(m, rm.hoeheGrad()), rm.hoeheGrad(), rm.azimutGrad(),
                new double[] {eM * MONDFARBE[0], eM * MONDFARBE[1], eM * MONDFARBE[2]}, sinM,
                new double[] {k * MONDFARBE[0], k * MONDFARBE[1], k * MONDFARBE[2]}, psi);
    }

    /**
     * Richtung in der Szene (x Ost im Gitter, y oben, z Süd im Gitter) aus Ost, Nord, oben am Ort: um die
     * Meridiankonvergenz gedreht (Azimut im Gitter = wahrer Azimut − Konvergenz).
     */
    public static double[] inSzene(double ost, double nord, double oben, double[] lonLat, int zone) {
        double gam = Math.toRadians(Utm.faktoren(lonLat[0], lonLat[1], zone)[0]), c = Math.cos(gam), s = Math.sin(gam);
        return new double[] {ost * c - nord * s, oben, -(nord * c + ost * s)};
    }

    /** Strahlenbrechung (Grad) für die wahre Höhe h (Grad), Normalatmosphäre (Sæmundsson 1986), unter −2° wie bei −2°. */
    public static double brechung(double hGrad) {
        double h = Math.max(-2, hGrad);
        return 1.02 / Math.tan(Math.toRadians(h + 10.3 / (h + 5.11))) / 60.0;
    }

    /** Richtung um die Brechung gehoben (die Richtung bleibt im Azimut). */
    public static double[] gehoben(double[] d, double hGrad) {
        double hs = Math.toRadians(hGrad + brechung(hGrad)), n = Math.hypot(d[0], d[2]);
        if (n < 1e-12) return d.clone();
        return new double[] {d[0] / n * Math.cos(hs), Math.sin(hs), d[2] / n * Math.cos(hs)};
    }

    /** Phasenwinkel des Monds (Grad) aus den geozentrischen Örtern (Meeus 48.2, 48.3). */
    public static double phasenwinkel(Gestirne.Stand st) {
        double cosE = Math.sin(st.sonneDek) * Math.sin(st.mondDek) + Math.cos(st.sonneDek) * Math.cos(st.mondDek) * Math.cos(st.sonneRa - st.mondRa);
        double e = Math.acos(Math.max(-1, Math.min(1, cosE)));
        return Math.toDegrees(Math.atan2(st.sonneAbstand * Math.sin(e), st.mondAbstand - st.sonneAbstand * Math.cos(e)));
    }

    /** Helligkeit des Monds (mag) zum Phasenwinkel ψ (Grad) und Abstand (m), wie {@code sky}: −12,73 + 0,026 |ψ| + 4 · 10⁻⁹ ψ⁴ + 5 lg(d / 384 400 km). */
    public static double mondMag(double psi, double abstand) {
        double a = Math.abs(psi);
        return -12.73 + 0.026 * a + 4e-9 * a * a * a * a + 5 * Math.log10(abstand / 384_400_000.0);
    }
}
