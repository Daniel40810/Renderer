package com.dan.aurora;

import java.util.ArrayList;
import java.util.List;

/**
 * Polarlicht (Aurora borealis und australis) aus Ort, Zeit und Kp-Index: Das Oval legt fest, wo die Vorhänge stehen,
 * der exzentrische Dipol, wie die Strahlen liegen; Elektronen eines Maxwell-Spektrums geben ihre Energie in 95 bis
 * 300 km Höhe ab, und vier Linien machen daraus Licht: Grün (557,7 nm) um 110 km, Rot (630,0 nm) darüber, Blau und
 * Purpur (427,8 und 670 nm) am unteren Saum. {@link #strahlung} integriert die Emission entlang einer Blickrichtung und
 * gibt kR je Linie zurück, mit der Extinktion der Luft; {@link Spektrum} macht daraus cd/m² und Farben.
 * <p>
 * Der Kern braucht nur Java 21. Was von außen kommt – Ort, Datum, Kp, Blickrichtung – kommt als Zahl. Gleicher
 * {@code seed}, gleiches Bild.
 */
public final class Aurora {
    /** Qualitätsstufen: Schritt des Strahlengangs in der Höhe (5 · 2,5 · 1,25 · 0,625 km). */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    public long seed = 1;
    public Ort ort = Ort.tromsoe();
    /** Planetarischer Kp-Index 0..9. */
    public double kp = 3;
    /** Datum und Weltzeit in Stunden. */
    public int monat = 12, tag = 21;
    public double ut = 22;
    /** Hellster Bogen: charakteristische Energie E₀ in keV und Energiefluss Q in mW/m². */
    public double energie = 4, fluss = 8;
    /** Weicher Anteil des Spektrums (Rot oben), Faltung 0..1, Strahlen 0..1, Breite der Vorhänge in km. */
    public double weich = 0.15, faltung = 0.5, strahlen = 0.5, breite = 3;
    /** Drift entlang der Bögen und Bewegung quer dazu in km/s. */
    public double drift = 0.5, bewegung = 0.1;
    /** Zahl der diskreten Bögen (1..4) und ob die diffuse Aurora äquatorwärts davon mitgemalt wird. */
    public int boegen = 2;
    public boolean diffus = true;
    /** Optische Dicke der Luft im Zenit bei 557,7 nm (0,12 klare Polarluft, 0,3 Dunst). */
    public double extinktion = 0.15;
    public Quality quality = Quality.HIGH;
    /** Stufen der Tabellen gleich mitbauen (Prozessor); gl/ setzt false, die Grafikkarte macht Mip-Stufen selbst. */
    public boolean stufen = true;
    public final Emission emission = new Emission();
    public final Magnetfeld feld = new Magnetfeld();

    // ------------------------------------------------------------------ abgeleitet in vorbereiten()
    /** Magnetische Breite (Grad, mit Vorzeichen), Inklination (Betrag), Deklination, magnetische Ortszeit (h). */
    public double mlat, inklination, deklination, mlt;
    /** Grenzen des Ovals in magnetischer Breite (Betrag): diffus äquatorwärts, Bögen äquatorwärts, Polgrenze. */
    public double grenzeDiffus, grenzeBogen, grenzePol;
    /** Die Vorhänge; der erste ist der hellste Bogen, der letzte bei {@link #diffus} die diffuse Aurora. */
    public final List<Vorhang> vorhaenge = new ArrayList<>();
    /** Horizontale Einheitsvektoren (Ost, Nord): entlang der Bögen und polwärts. */
    private double eOst, eNord, pOst, pNord;
    /** cot |I|: um so viel km wandert der Fußpunkt je km Höhe polwärts. */
    private double cotI;
    private double hMin = 90, hMax = 320, hFein = 200;
    private double zeit;

    // ------------------------------------------------------------------ Presets
    /** Ruhiger Bogen am Abend: Kp 2, 3 keV, 5 mW/m², kaum Falten. */
    public static Aurora ruhig() {
        Aurora a = new Aurora();
        a.kp = 2; a.energie = 3; a.fluss = 5; a.faltung = 0.2; a.strahlen = 0.3; a.breite = 4; a.boegen = 1; a.drift = 0.3; a.bewegung = 0.05;
        return a;
    }

    /** Aktive Nacht: Kp 4, zwei gefaltete Vorhänge mit Strahlen, IBC II–III. */
    public static Aurora aktiv() {
        Aurora a = new Aurora();
        a.kp = 4; a.energie = 5; a.fluss = 20; a.faltung = 0.6; a.strahlen = 0.6; a.breite = 3; a.boegen = 2; a.drift = 0.6; a.bewegung = 0.15;
        return a;
    }

    /** Aufbruch (Substurm): Kp 6, hartes Spektrum mit purpurnem Saum, 80 mW/m², drei Vorhänge, stark gefaltet. */
    public static Aurora ausbruch() {
        Aurora a = new Aurora();
        a.kp = 6; a.energie = 9; a.fluss = 80; a.weich = 0.08; a.faltung = 1; a.strahlen = 0.8; a.breite = 2.5; a.boegen = 3; a.drift = 1; a.bewegung = 0.4;
        return a;
    }

    /** Rotes Polarlicht weit im Süden: weiches Spektrum (0,3 keV), das Licht entsteht in 200 bis 300 km Höhe. */
    public static Aurora rot() {
        Aurora a = new Aurora();
        a.kp = 7; a.energie = 0.3; a.fluss = 8; a.weich = 0; a.faltung = 0.4; a.strahlen = 0.3; a.breite = 12; a.boegen = 1; a.diffus = false;
        return a;
    }

    /** Preset nach Kp 0..9: zwischen ruhig (≤ 2), aktiv (4) und Aufbruch (≥ 6) gemischt, darüber heller. */
    public static Aurora kp(double kp) {
        kp = Math.max(0, Math.min(9, kp));
        Aurora a, b;
        double f;
        if (kp <= 4) { a = ruhig(); b = aktiv(); f = Math.max(0, (kp - 2) / 2); }
        else { a = aktiv(); b = ausbruch(); f = Math.min(1, (kp - 4) / 2); }
        Aurora r = new Aurora();
        r.kp = kp;
        r.energie = Math.exp(Math.log(a.energie) + f * (Math.log(b.energie) - Math.log(a.energie)));
        r.fluss = Math.exp(Math.log(a.fluss) + f * (Math.log(b.fluss) - Math.log(a.fluss))) * (kp > 6 ? 1 + (kp - 6) / 3 : 1);
        r.faltung = a.faltung + f * (b.faltung - a.faltung);
        r.strahlen = a.strahlen + f * (b.strahlen - a.strahlen);
        r.breite = a.breite + f * (b.breite - a.breite);
        r.boegen = (int) Math.round(a.boegen + f * (b.boegen - a.boegen));
        r.drift = a.drift + f * (b.drift - a.drift);
        r.bewegung = a.bewegung + f * (b.bewegung - a.bewegung);
        return r;
    }

    // ------------------------------------------------------------------ Aufbau
    /** Alles Abgeleitete aus den offenen Werten rechnen, die Vorhänge zur Zeit t (s) bauen. Vor jedem Bild rufen. */
    public void vorbereiten(double t) {
        zeit = t;
        mlat = feld.breite(ort.breite, ort.laenge);
        inklination = Math.abs(Magnetfeld.inklination(mlat));
        deklination = Double.isNaN(ort.deklination) ? feld.peilungPol(ort.breite, ort.laenge) : ort.deklination;
        mlt = feld.ortszeit(ort.breite, ort.laenge, ut, Magnetfeld.tagImJahr(monat, tag));
        grenzeDiffus = Oval.diffusAequator(kp, mlt);
        grenzeBogen = Oval.bogenAequator(kp, mlt);
        grenzePol = Oval.bogenPol(kp, mlt);
        // polwärts: auf der Nordhalbkugel die Peilung der Deklination, auf der Südhalbkugel entgegen
        double az = Math.toRadians(ort.breite >= 0 ? deklination : deklination + 180);
        pOst = Math.sin(az);
        pNord = Math.cos(az);
        eOst = pNord;
        eNord = -pOst;
        cotI = 1 / Math.tan(Math.toRadians(Math.max(20, inklination)));
        double lam = Math.abs(mlat);
        vorhaenge.clear();
        int n = Math.max(1, Math.min(4, boegen));
        for (int k = 0; k < n; k++) {
            Vorhang v = new Vorhang();
            v.seed = seed * 31 + k * 977;
            double f = (k + 0.5 + 0.6 * (Rauschen.hash(seed, k, 1, 1) - 0.5)) / n;
            double lamK = grenzeBogen + f * (grenzePol - grenzeBogen);
            v.position = (lamK - lam) * Oval.KM_JE_GRAD;
            v.kruemmung = Magnetfeld.ERDRADIUS * Math.tan(Math.toRadians(90 - lamK));
            double r1 = Rauschen.hash(seed, k, 2, 1), r2 = Rauschen.hash(seed, k, 3, 1), r3 = Rauschen.hash(seed, k, 4, 1);
            v.energie = energie * (k == 0 ? 1 : 0.7 + 0.6 * r1);
            v.fluss = fluss * (k == 0 ? 1 : 0.35 + 0.45 * r2);
            v.breite = breite * (0.7 + 0.6 * r3);
            v.weich = weich;
            v.faltung = faltung;
            v.strahlen = strahlen;
            v.drift = drift * (mlt < 12 ? 1 : -1);
            v.bewegung = bewegung;
            v.stufen = stufen;
            v.profileRechnen(emission);
            v.bauen(t);
            vorhaenge.add(v);
        }
        if (diffus) {
            Vorhang d = new Vorhang();
            d.seed = seed * 31 + 4999;
            d.position = (grenzeDiffus + 1.2 - lam) * Oval.KM_JE_GRAD;
            d.kruemmung = Magnetfeld.ERDRADIUS * Math.tan(Math.toRadians(90 - grenzeDiffus));
            d.energie = Math.max(energie, 6);
            d.fluss = 0.06 * fluss;
            d.breite = 70;
            d.weich = 0.05;
            d.faltung = 0;
            d.strahlen = 0;
            d.schwankung = 0.3;
            d.drift = 0;
            d.bewegung = 0;
            d.stufen = stufen;
            d.profileRechnen(emission);
            d.bauen(t);
            vorhaenge.add(d);
        }
        hMin = 400;
        hMax = 80;
        hFein = 80;
        for (Vorhang v : vorhaenge) {
            hMin = Math.min(hMin, v.hUnten);
            hMax = Math.max(hMax, v.hOben);
            hFein = Math.max(hFein, v.hFein);
        }
    }

    /** Nur die Zeit weiterstellen (Drift und Falten), ohne Profile neu zu rechnen. */
    public void zeit(double t) {
        zeit = t;
        for (Vorhang v : vorhaenge) v.bauen(t);
    }

    public double zeit() { return zeit; }

    /** Für gl/: horizontale Einheitsvektoren (Ost, Nord) entlang der Bögen und polwärts, dann cot I: {eOst, eNord, pOst, pNord, cotI}. */
    public double[] rahmen() { return new double[]{eOst, eNord, pOst, pNord, cotI}; }

    /** Für gl/: Höhen des Strahlengangs (km): unten, oben, Ende des feinen Teils. */
    public double[] hoehen() { return new double[]{hMin, hMax, hFein}; }

    // ------------------------------------------------------------------ Strahlengang
    /** Weg vom Beobachter bis zur Höhe h (km) entlang einer Richtung mit dem Anteil dz nach oben, mit Erdkrümmung. */
    static double weg(double dz, double h) {
        double r = Magnetfeld.ERDRADIUS;
        return -r * dz + Math.sqrt(r * r * dz * dz + 2 * r * h + h * h);
    }

    /** Schritt in der Höhe nach Qualität, km. */
    public double schritt() {
        return switch (quality) { case LOW -> 5; case MEDIUM -> 2.5; case HIGH -> 1.25; case ULTRA -> 0.625; };
    }

    /**
     * Strahlung in kR je Linie ({@link Emission#BLAU} …) aus der Blickrichtung (Ost, Nord, oben; Einheitsvektor), mit
     * der Extinktion der Luft; unter dem Horizont 0. Darf von vielen Fäden zugleich gerufen werden.
     */
    public void strahlung(double dOst, double dNord, double dOben, double[] kr) {
        for (int l = 0; l < Emission.LINIEN; l++) kr[l] = 0;
        if (dOben <= 0.0005 || vorhaenge.isEmpty()) return;
        double dh = schritt();
        // fein bis hFein (Grün, Blau, Purpur mit Kräuseln), darüber nur noch das glatte Rot in vierfachen Schritten
        int nFein = (int) Math.ceil((hFein - hMin) / dh), n = nFein + (int) Math.ceil((hMax - hFein) / (4 * dh));
        double[] d = new double[3];
        double dU = dOst * eOst + dNord * eNord, dP = dOst * pOst + dNord * pNord;
        double tVor = weg(dOben, hMin);
        double uVor = tVor * dU, vVor = tVor * dP + (hMin - 100) * cotI;
        for (int i = 0; i < n; i++) {
            double h0 = i < nFein ? hMin + i * dh : hFein + (i - nFein) * 4 * dh;
            double h1 = Math.min(i < nFein ? hFein : hMax, h0 + (i < nFein ? dh : 4 * dh)), hm = 0.5 * (h0 + h1);
            if (h1 <= h0) continue;
            double t1 = weg(dOben, h1), tm = weg(dOben, hm);
            double u = tm * dU, v = tm * dP + (hm - 100) * cotI;
            double du = Math.abs(t1 * dU - uVor), dv = Math.abs(t1 * dP + (h1 - 100) * cotI - vVor);
            uVor = t1 * dU;
            vVor = t1 * dP + (h1 - 100) * cotI;
            double dl = (t1 - tVor) * 1e5;
            tVor = t1;
            double x = (hm - Niederschlag.H0);
            int ih = (int) x;
            if (ih < 0 || ih >= Niederschlag.HOEHEN.length - 1) continue;
            double fh = x - ih;
            for (Vorhang vh : vorhaenge) {
                if (hm < vh.hUnten || hm > vh.hOben || vh.ausserhalb(v)) continue;
                vh.dichte(u, v, du, dv, d);
                if (d[0] + d[1] + d[2] <= 0) continue;
                for (int l = 0; l < Emission.LINIEN; l++) {
                    double e;
                    if (l == Emission.ROT) e = d[2] * (vh.profilRot[ih] + fh * (vh.profilRot[ih + 1] - vh.profilRot[ih]));
                    else {
                        double[] pg = vh.profilGlatt[l], ps = vh.profilStrahl[l];
                        e = d[0] * (pg[ih] + fh * (pg[ih + 1] - pg[ih])) + d[1] * (ps[ih] + fh * (ps[ih + 1] - ps[ih]));
                    }
                    kr[l] += e * dl;
                }
            }
        }
        double luft = luftmasse(Math.toDegrees(Math.asin(Math.min(1, dOben))));
        for (int l = 0; l < Emission.LINIEN; l++) kr[l] = kr[l] / 1e9 * Math.exp(-optischeDicke(l) * luft);
    }

    /** Relative Luftmasse nach Kasten und Young (1989) für den Höhenwinkel in Grad. */
    public static double luftmasse(double hoeheGrad) {
        double h = Math.max(0, hoeheGrad);
        return 1 / (Math.sin(Math.toRadians(h)) + 0.50572 * Math.pow(h + 6.07995, -1.6364));
    }

    /** Optische Dicke im Zenit je Linie: 80 % Rayleigh (λ⁻⁴), 20 % Dunst (λ⁻¹), bezogen auf 557,7 nm. */
    public double optischeDicke(int linie) {
        double q = 557.7 / Emission.WELLENLAENGE[linie];
        return extinktion * (0.8 * q * q * q * q + 0.2 * q);
    }

    /** Durchlässigkeit der Luft für eine Linie unter dem Höhenwinkel (Grad). */
    public double durchlass(int linie, double hoeheGrad) {
        return Math.exp(-optischeDicke(linie) * luftmasse(hoeheGrad));
    }

    // ------------------------------------------------------------------ Auskunft
    /** Der hellste Bogen (nach vorbereiten). */
    public Vorhang hauptbogen() { return vorhaenge.get(0); }

    /** Azimut (Grad, 0 = Nord, 90 = Ost) der Richtung polwärts entlang des magnetischen Meridians. */
    public double azimutPolwaerts() {
        double a = Math.toDegrees(Math.atan2(pOst, pNord));
        return a < 0 ? a + 360 : a;
    }

    /** Azimut, in dem der Hauptbogen steht: polwärts, wenn er polwärts liegt, sonst äquatorwärts. */
    public double azimutHauptbogen() {
        double a = azimutPolwaerts();
        if (hauptbogen().mitteAmMeridian() < 0) a += 180;
        a %= 360;
        return a < 0 ? a + 360 : a;
    }

    /** Höhenwinkel in Grad, unter dem das Maximum eines Vorhangs am Meridian des Beobachters erscheint (negativ: unter der Kimm). */
    public double hoehenwinkel(Vorhang v) {
        double dist = v.mitteAmMeridian() - (v.hMaximum - 100) * cotI;
        return Oval.hoehenwinkel(dist, v.hMaximum);
    }

    /** Höhenwinkel des magnetischen Zenits (dort laufen die Strahlen zusammen): die Inklination, äquatorwärts. */
    public double hoehenwinkelMagnetischerZenit() { return inklination; }

    /** Leuchtdichte in cd/m² und Internationale Helligkeitsklasse der senkrechten Säule durch den Hauptbogen. */
    public String helligkeit() {
        double[] s = hauptbogen().saeuleKr;
        return String.format(java.util.Locale.GERMAN, "%.1f kR in 557,7 nm (%s), %.2f mcd/m² senkrecht", s[Emission.GRUEN], Emission.ibc(s[Emission.GRUEN]), 1000 * Spektrum.leuchtdichte(s));
    }
}
