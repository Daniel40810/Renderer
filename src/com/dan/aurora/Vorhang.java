package com.dan.aurora;

/**
 * Ein Vorhang: die Fußlinie des Niederschlags auf der Schale in 100 km Höhe und sein Höhenprofil. Die Fußlinie läuft
 * entlang des Ovals (Koordinate u, magnetisch Ost) in der Entfernung {@link #position} polwärts vom Beobachter
 * (Koordinate v), gekrümmt wie das Oval und gefaltet in vier Größen nach Hallinan und Davis (1970): Spiralen um 300 km,
 * Falten um 100 und 30 km, Kräusel um 9 km. Quer dazu ist der Vorhang eine Gaußglocke der Breite {@link #breite}.
 * Entlang der Linie wechselt die Helligkeit langsam, und {@link #strahlen} teilt den Fluss in einen glatten Anteil und
 * Strahlen: schmale helle Streifen mit weicherem Spektrum, die darum höher hinaufreichen. Das Rot (630,0 nm) hinkt mit
 * seiner Lebensdauer von 110 s hinterher und wird breiter und ohne Kräusel gemalt.
 * <p>
 * Für ein Bild werden Fußlinie, Strahlen und Helligkeit zur Zeit t in Tabellen über u gelegt (Schritt 0,25 km, dazu
 * Stufen zu 0,5, 1, 2, … km, aus denen der Strahlengang die passend grobe nimmt), damit jede Abtastung billig bleibt.
 */
public final class Vorhang {
    /** Fußlinie am Meridian des Beobachters, km polwärts (negativ: äquatorwärts). */
    public double position = 50;
    /** Krümmungsradius der Fußlinie in km (das Oval biegt polwärts); 0 = gerade. */
    public double kruemmung = 2600;
    /** Spektrum und Fluss. */
    public double energie = 4, fluss = 8, weich = 0.15;
    /** Breite quer zum Vorhang in km (Standardabweichung). */
    public double breite = 3;
    /** Faltung 0..1, Strahlen 0..1, Schwankung der Helligkeit entlang der Linie 0..1. */
    public double faltung = 0.5, strahlen = 0.5, schwankung = 0.5;
    /** Drift entlang der Linie in km/s (positiv: magnetisch Ost) und Bewegung quer dazu in km/s (verbreitert das Rot). */
    public double drift = 0.5, bewegung = 0.1;
    /** Strahlen sind weicher: ihre Energie ist E₀ geteilt durch diesen Faktor. */
    public double strahlWeicher = 2.5;
    public long seed = 1;

    /** Halbe Länge der Tabellen in km und ihr feinster Schritt. */
    public static final double U = 2000, SCHRITT = 0.25;
    /** Zahl der Stützen je Tabelle: 2·U/SCHRITT + 1 = 16 001 (unter der größten Texturbreite 16 384 für gl/). */
    public static final int STUETZEN = (int) Math.round(2 * U / SCHRITT) + 1;
    /** Die Stufen (Mittel über 2, 4, 8 … Stützen) gleich mitbauen; gl/ braucht sie nicht (die Grafikkarte macht Mip-Stufen) und setzt false. */
    public boolean stufen = true;
    static final int STUFEN = 8;
    /** Tabellen über u je Stufe: Mitte v der Fußlinie, Mitte des Rots, Strahlfaktor (Mittel 1), Helligkeit. */
    private double[][] mitte, mitteRot, strahl, hell;
    private double[] feinMitte, feinMitteRot, feinStrahl, feinHell;
    private double sigmaRot, vMin, vMax;
    /** Höhenprofile [Linie][Höhe] für den glatten Anteil, die Strahlen und das Rot (beide zusammen), Photonen/cm³/s bei Dichte 1. */
    public double[][] profilGlatt, profilStrahl;
    public double[] profilRot;
    /** Höhen, zwischen denen der Vorhang leuchtet (km), bis wohin der feine Teil (Blau, Grün, Purpur) reicht, und die Höhe des Maximums in 557,7 nm. */
    public double hUnten, hOben, hFein, hMaximum;
    /** Senkrechte Säule durch die Mitte des Vorhangs je Linie in kR. */
    public double[] saeuleKr;

    static final double[] GAUSS = new double[2049];

    static {
        for (int i = 0; i < GAUSS.length; i++) {
            double x = 5.0 * i / (GAUSS.length - 1);
            GAUSS[i] = Math.exp(-0.5 * x * x);
        }
    }

    /** e^(−x²/2) aus der Tabelle, 0 jenseits von 5. */
    static double gauss(double x) {
        x = Math.abs(x) * ((GAUSS.length - 1) / 5.0);
        if (x >= GAUSS.length - 1) return 0;
        int i = (int) x;
        return GAUSS[i] + (x - i) * (GAUSS[i + 1] - GAUSS[i]);
    }

    /** Höhenprofile aus Spektrum und Fluss rechnen (ändert sich nur mit energie, fluss, weich, strahlen). */
    public void profileRechnen(Emission emission) {
        Niederschlag glatt = new Niederschlag(energie, fluss);
        glatt.weich = weich;
        Niederschlag strahlN = new Niederschlag(energie / strahlWeicher, fluss);
        strahlN.weich = weich;
        profilGlatt = emission.profile(glatt.ionisation());
        profilStrahl = emission.profile(strahlN.ionisation());
        int n = Niederschlag.HOEHEN.length;
        profilRot = new double[n];
        double[] gesamt = new double[n];
        double max = 0;
        for (int i = 0; i < n; i++) {
            profilRot[i] = (1 - strahlen) * profilGlatt[Emission.ROT][i] + strahlen * profilStrahl[Emission.ROT][i];
            for (int l = 0; l < Emission.LINIEN; l++)
                gesamt[i] += (1 - strahlen) * profilGlatt[l][i] + strahlen * profilStrahl[l][i];
            max = Math.max(max, gesamt[i]);
        }
        // feiner Teil: Blau, Grün und Purpur (scharf, mit Kräuseln); das Rot allein reicht weiter hinauf und ist glatt
        double[] fein = new double[n];
        double maxFein = 0, maxRot = 0;
        for (int i = 0; i < n; i++) {
            fein[i] = gesamt[i] - profilRot[i];
            maxFein = Math.max(maxFein, fein[i]);
            maxRot = Math.max(maxRot, profilRot[i]);
        }
        int lo = 0, hi = n - 1, hiFein = n - 1;
        while (lo < n - 1 && gesamt[lo] < 1e-3 * max) lo++;
        while (hiFein > 0 && fein[hiFein] < 5e-3 * maxFein) hiFein--;
        while (hi > 0 && profilRot[hi] < 1e-2 * maxRot && fein[hi] < 5e-3 * maxFein) hi--;
        hUnten = Niederschlag.HOEHEN[Math.max(0, lo - 1)];
        hFein = Niederschlag.HOEHEN[Math.min(n - 1, hiFein + 1)];
        hOben = Math.max(hFein, Niederschlag.HOEHEN[Math.min(n - 1, hi + 1)]);
        double[] gruen = new double[n];
        for (int i = 0; i < n; i++) gruen[i] = (1 - strahlen) * profilGlatt[Emission.GRUEN][i] + strahlen * profilStrahl[Emission.GRUEN][i];
        hMaximum = Niederschlag.hoeheMaximum(gruen);
        saeuleKr = new double[Emission.LINIEN];
        for (int l = 0; l < Emission.LINIEN; l++) {
            double[] p = new double[n];
            for (int i = 0; i < n; i++) p[i] = l == Emission.ROT ? profilRot[i] : (1 - strahlen) * profilGlatt[l][i] + strahlen * profilStrahl[l][i];
            saeuleKr[l] = Niederschlag.saeule(p) / 1e9;
        }
    }

    /** Fußlinie, Strahlen und Helligkeit zur Zeit t (s) in die Tabellen legen. */
    public void bauen(double t) {
        int n = (int) Math.round(2 * U / SCHRITT) + 1;
        double[] m = new double[n], mr = new double[n], s = new double[n], h = new double[n];
        double summeStrahl = 0;
        for (int i = 0; i < n; i++) {
            double u = -U + i * SCHRITT, ud = u - drift * t;
            double bogen = kruemmung > 0 ? u * u / (2 * kruemmung) : 0;
            double f1 = 45 * (2 * Rauschen.wert1(seed, ud / 320 + t / 600 + 0.37, 1) - 1);
            double f2 = 14 * (2 * Rauschen.wert1(seed, ud / 100 + t / 200 + 0.11, 2) - 1);
            double f3 = 4.5 * (2 * Rauschen.wert1(seed, ud / 30 + t / 60 + 0.73, 3) - 1);
            double f4 = 1.4 * (2 * Rauschen.wert1(seed, ud / 9 + t / 20 + 0.52, 4) - 1);
            m[i] = position + bogen + faltung * (f1 + f2 + f3 + f4);
            mr[i] = position + bogen + faltung * (f1 + f2);
            // Strahlen: schmale Streifen (2,5 km) mit feiner Körnung (0,8 km), in Bündeln von etwa 35 km
            double r1 = Rauschen.wert1(seed, ud / 2.5 + t / 30, 10), r2 = Rauschen.wert1(seed, ud / 0.8 - t / 12, 11);
            double r3 = Rauschen.wert1(seed, ud / 35 + t / 90 + 0.2, 13);
            double r = Math.max(0, (r1 - 0.35) / 0.45);
            s[i] = Math.pow(r, 1.5) * (0.6 + 0.8 * r2) * (0.35 + 1.3 * r3);
            summeStrahl += s[i];
            h[i] = Math.max(0.1, 1 + schwankung * Rauschen.fbm1(seed, ud / 200, 2, 0.5, 12));
        }
        double k = summeStrahl > 0 ? n / summeStrahl : 1;
        for (int i = 0; i < n; i++) s[i] *= k;
        feinMitte = m;
        feinMitteRot = mr;
        feinStrahl = s;
        feinHell = h;
        if (stufen) stufenBauen();
        else { mitte = null; mitteRot = null; strahl = null; hell = null; }
        sigmaRot = Math.sqrt(breite * breite + 55 * bewegung * (55 * bewegung) + 64);
        vMin = Double.MAX_VALUE;
        vMax = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            vMin = Math.min(vMin, Math.min(m[i] - 5 * breite, mr[i] - 5 * sigmaRot));
            vMax = Math.max(vMax, Math.max(m[i] + 5 * breite, mr[i] + 5 * sigmaRot));
        }
    }

    /** Die Stufen aus den feinen Tabellen (für den Strahlengang auf dem Prozessor). */
    private synchronized void stufenBauen() {
        if (mitte != null) return;
        double[][] a = stufen(feinMitte), b = stufen(feinMitteRot), c = stufen(feinStrahl), d = stufen(feinHell);
        mitteRot = b;
        strahl = c;
        hell = d;
        mitte = a;
    }

    /** Die feinen Tabellen über u (−U..U, Schritt SCHRITT) für gl/: 0 Mitte, 1 Mitte des Rots, 2 Strahlfaktor, 3 Helligkeit. */
    public double[] tabelle(int welche) {
        return switch (welche) { case 0 -> feinMitte; case 1 -> feinMitteRot; case 2 -> feinStrahl; default -> feinHell; };
    }

    /** Breite des Rots (km), Band in v, in dem der Vorhang leuchten kann (für gl/). */
    public double sigmaRot() { return sigmaRot; }
    public double vMin() { return vMin; }
    public double vMax() { return vMax; }

    /** Stufen: jede nächste mittelt zwei Nachbarn. */
    static double[][] stufen(double[] fein) {
        double[][] s = new double[STUFEN][];
        s[0] = fein;
        for (int k = 1; k < STUFEN; k++) {
            double[] vor = s[k - 1];
            int n = (vor.length + 1) / 2;
            double[] g = new double[n];
            for (int i = 0; i < n; i++) {
                int a = Math.min(vor.length - 1, 2 * i), b = Math.min(vor.length - 1, 2 * i + 1);
                g[i] = 0.5 * (vor[a] + vor[b]);
            }
            s[k] = g;
        }
        return s;
    }

    /** Liegt v außerhalb des Bands, in dem der Vorhang leuchten kann? */
    public boolean ausserhalb(double v) {
        return v < vMin || v > vMax;
    }

    /**
     * Dichte der Fußlinie am Punkt (u, v) der Schale, für eine Abtastung, die sich um du entlang und dv quer bewegt:
     * out[0] glatter Anteil, out[1] Strahlen, out[2] Rot (breiter, ohne Kräusel). Die Glocke wird um dv verbreitert
     * (Kastenfilter), damit dünne Vorhänge bei groben Schritten nicht flimmern; ihr Integral bleibt.
     */
    public void dichte(double u, double v, double du, double dv, double[] out) {
        if (mitte == null) stufenBauen();
        int st = 0;
        double schritt = SCHRITT;
        while (st < STUFEN - 1 && du > schritt * 1.5) {
            st++;
            schritt *= 2;
        }
        double x = (u + U) / schritt;
        double[] m = mitte[st];
        if (x < 0 || x >= m.length - 1) {
            out[0] = out[1] = out[2] = 0;
            return;
        }
        int i = (int) x;
        double f = x - i;
        double vc = m[i] + f * (m[i + 1] - m[i]);
        double[] r = strahl[st], hl = hell[st], mr = mitteRot[st];
        double ray = r[i] + f * (r[i + 1] - r[i]), he = hl[i] + f * (hl[i + 1] - hl[i]), vr = mr[i] + f * (mr[i + 1] - mr[i]);
        double s2 = breite * breite + dv * dv / 12, s = Math.sqrt(s2);
        double g = breite / s * gauss((v - vc) / s) * he;
        out[0] = (1 - strahlen) * g;
        out[1] = strahlen * ray * g;
        double sr = Math.sqrt(sigmaRot * sigmaRot + dv * dv / 12);
        out[2] = sigmaRot / sr * gauss((v - vr) / sr) * he;
    }

    /** Mitte der Fußlinie am Meridian des Beobachters (u = 0) zur gebauten Zeit, km polwärts. */
    public double mitteAmMeridian() {
        double[] m = feinMitte;
        return m[m.length / 2];
    }
}
