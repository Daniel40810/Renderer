package com.dan.aurora.demo;

import com.dan.aurora.Atmosphaere;
import com.dan.aurora.Aurora;
import com.dan.aurora.Emission;
import com.dan.aurora.Magnetfeld;
import com.dan.aurora.Niederschlag;
import com.dan.aurora.Ort;
import com.dan.aurora.Oval;
import com.dan.aurora.Spektrum;
import com.dan.aurora.Vorhang;

import java.util.Locale;

/**
 * Prüfung ohne Fenster und ohne Grafikkarte, endet mit „Alles in Ordnung.“ Geprüft werden das Magnetfeld (Breiten,
 * Inklination, Mittelpunkt des exzentrischen Dipols), das Oval (Tromsø unter dem Oval bei Kp 2, Hamburg erst ab Kp 7),
 * der Niederschlag (Höhe der Maxima nach der Energie), die Emission (Verhältnisse der Linien, Helligkeit in kR und
 * IBC), das Spektrum (1 kR = 1,93·10⁻⁴ cd/m², Farben der Linien), die Geometrie der Strahlen (magnetischer Zenit) und
 * der Strahlengang (gleicher seed, gleiches Bild; Qualitätsstufen nah beieinander; keine NaN).
 */
public final class AuroraCheck {
    private static int bad;

    public static void main(String[] args) {
        Locale.setDefault(Locale.GERMAN);
        Magnetfeld f = new Magnetfeld();
        double versatz = Math.sqrt(f.mitte[0] * f.mitte[0] + f.mitte[1] * f.mitte[1] + f.mitte[2] * f.mitte[2]);
        check(versatz > 500 && versatz < 650, String.format("exzentrischer Dipol %.0f km vom Erdmittelpunkt (570 ± 70 erwartet)", versatz));
        check(Math.abs(f.polBreite - 80.6) < 0.5 && Math.abs(f.polLaenge + 72.7) < 0.5, String.format("geomagnetischer Pol %.1f° N, %.1f° E", f.polBreite, f.polLaenge));
        Ort tromsoe = Ort.tromsoe(), hamburg = Ort.hamburg(), hobart = Ort.hobart();
        double lTr = f.breite(tromsoe.breite, tromsoe.laenge), lHh = f.breite(hamburg.breite, hamburg.laenge), lHo = f.breite(hobart.breite, hobart.laenge);
        check(lTr > 65 && lTr < 68, String.format("Tromsø magnetisch %.1f° (66,6 korrigiert geomagnetisch)", lTr));
        check(lHh > 49.5 && lHh < 52.5, String.format("Hamburg magnetisch %.1f° (50,5)", lHh));
        check(lHo < -52.5 && lHo > -56.5, String.format("Hobart magnetisch %.1f° (−54,6)", lHo));
        double iTr = Magnetfeld.inklination(lTr);
        check(iTr > 76 && iTr < 79, String.format("Inklination Tromsø %.1f° (78)", iTr));
        double mltTr = f.ortszeit(tromsoe.breite, tromsoe.laenge, 22, Magnetfeld.tagImJahr(12, 21));
        check(mltTr > 22.5 && mltTr < 24.5 || mltTr < 0.5, String.format("Tromsø 22 UT = %.1f MLT (magnetische Mitternacht um 22:30 UT)", mltTr));

        // Oval
        double eq2 = Oval.bogenAequator(2, 0), pol2 = Oval.bogenPol(2, 0);
        check(lTr > eq2 && lTr < pol2, String.format("Tromsø liegt bei Kp 2 unter dem Oval (%.1f..%.1f°)", eq2, pol2));
        double abstand3 = (Oval.bogenAequator(3, 23) - lHh) * Oval.KM_JE_GRAD, abstand8 = (Oval.bogenAequator(8, 23) - lHh) * Oval.KM_JE_GRAD;
        check(Oval.hoehenwinkel(abstand3, 110) < 3, String.format("Hamburg bei Kp 3: Bogen %.0f km entfernt, Unterkante %.1f° hoch – nichts zu sehen", abstand3, Oval.hoehenwinkel(abstand3, 110)));
        check(Oval.hoehenwinkel(abstand8, 110) > 12, String.format("Hamburg bei Kp 8: Bogen %.0f km entfernt, Unterkante %.1f° hoch", abstand8, Oval.hoehenwinkel(abstand8, 110)));
        check(Oval.hoehenwinkel(1300, 110) < 0 && Oval.hoehenwinkel(1300, 250) > 0, "in 1300 km ist die Unterkante hinter der Kimm, das Rot oben noch zu sehen");

        // Atmosphäre und Niederschlag
        check(Math.abs(Atmosphaere.saeule(100) / 3.3e-4 - 1) < 0.2 && Math.abs(Atmosphaere.saeule(150) / 4.8e-6 - 1) < 0.25,
                String.format("Massensäule über 100 km %.2e, über 150 km %.2e g/cm² (USSA 1976: 3,3e−4 und 4,8e−6)", Atmosphaere.saeule(100), Atmosphaere.saeule(150)));
        Emission em = new Emission();
        double[] h10 = maxima(em, 10), h1 = maxima(em, 1), h02 = maxima(em, 0.2);
        check(h10[Emission.GRUEN] >= 95 && h10[Emission.GRUEN] <= 110, String.format("557,7 nm bei 10 keV maximal in %.0f km (95..110)", h10[Emission.GRUEN]));
        check(h1[Emission.GRUEN] >= 120 && h1[Emission.GRUEN] <= 150, String.format("557,7 nm bei 1 keV maximal in %.0f km (120..150)", h1[Emission.GRUEN]));
        check(h02[Emission.GRUEN] >= 170, String.format("557,7 nm bei 0,2 keV maximal in %.0f km (≥ 170)", h02[Emission.GRUEN]));
        check(h10[Emission.ROT] >= 200 && h1[Emission.ROT] >= 200, String.format("630,0 nm maximal in %.0f km (10 keV) und %.0f km (1 keV), beide ≥ 200", h10[Emission.ROT], h1[Emission.ROT]));
        check(h10[Emission.BLAU] <= h10[Emission.GRUEN] && h10[Emission.PURPUR] <= h10[Emission.GRUEN], "Blau und Purpur liegen unter dem Grün (unterer Saum)");

        // Verhältnisse und Helligkeit
        Niederschlag hart = new Niederschlag(5, 10);
        double[] s = Emission.saeuleKr(em.profile(hart.ionisation()));
        check(s[Emission.GRUEN] > 4 && s[Emission.GRUEN] < 25, String.format("10 mW/m² bei 5 keV: %.1f kR in 557,7 nm senkrecht (4..25, IBC II)", s[Emission.GRUEN]));
        check(Emission.ibc(s[Emission.GRUEN]).equals("IBC II"), "das ist " + Emission.ibc(s[Emission.GRUEN]));
        double rg = s[Emission.ROT] / s[Emission.GRUEN], gb = s[Emission.GRUEN] / s[Emission.BLAU];
        check(rg > 0.1 && rg < 0.5, String.format("630,0/557,7 = %.2f bei 5 keV mit weichem Anteil (0,1..0,5)", rg));
        check(gb > 2 && gb < 6, String.format("557,7/427,8 = %.1f bei 5 keV (2..6)", gb));
        Niederschlag weichN = new Niederschlag(0.2, 10);
        weichN.weich = 0;
        double[] sw = Emission.saeuleKr(em.profile(weichN.ionisation()));
        check(sw[Emission.ROT] / sw[Emission.GRUEN] > 0.8, String.format("630,0/557,7 = %.2f bei 0,2 keV (rotes Polarlicht, > 0,8)", sw[Emission.ROT] / sw[Emission.GRUEN]));
        check(Emission.ibc(0.5).equals("IBC I") && Emission.ibc(50).equals("IBC III") && Emission.ibc(2000).equals("IBC IV"), "IBC-Stufen I bis IV");

        // Spektrum
        double[] kr = {0, 1, 0, 0};
        double l1 = Spektrum.leuchtdichte(kr);
        check(Math.abs(l1 / 1.93e-4 - 1) < 0.03, String.format("1 kR in 557,7 nm = %.3e cd/m² (1,93e−4)", l1));
        double[] gruen = Spektrum.farbe(Emission.GRUEN), blau = Spektrum.farbe(Emission.BLAU), rot = Spektrum.farbe(Emission.ROT);
        check(gruen[1] > gruen[0] && gruen[1] > gruen[2], "557,7 nm ist grün");
        check(blau[2] > blau[0] && blau[2] > blau[1], "427,8 nm ist blau");
        check(rot[0] > rot[1] && rot[0] > rot[2], "630,0 nm ist rot");
        double y = 0.2126 * gruen[0] + 0.7152 * gruen[1] + 0.0722 * gruen[2];
        check(Math.abs(y - 1) < 0.02, String.format("Leuchtdichte bleibt beim Ziehen in den Farbraum (Y = %.3f)", y));
        double[] krRot = {0, 0, 1, 0};
        check(Spektrum.leuchtdichteSkotopisch(krRot) / Spektrum.leuchtdichte(krRot) < 0.1, "für die Stäbchen ist das Rot fast unsichtbar");

        // Strahlengang
        Aurora a = Aurora.aktiv(), b = Aurora.aktiv();
        a.seed = b.seed = 5;
        a.vorbereiten(0);
        b.vorbereiten(0);
        double[] ka = new double[Emission.LINIEN], kb = new double[Emission.LINIEN];
        boolean gleich = true, endlich = true;
        double summeA = 0;
        for (int i = 0; i < 400; i++) {
            double az = i * 0.37, el = 0.05 + 0.9 * ((i * 0.618) % 1);
            double dx = Math.sin(az) * Math.cos(el), dy = Math.cos(az) * Math.cos(el), dz = Math.sin(el);
            a.strahlung(dx, dy, dz, ka);
            b.strahlung(dx, dy, dz, kb);
            for (int l = 0; l < Emission.LINIEN; l++) {
                if (ka[l] != kb[l]) gleich = false;
                if (Double.isNaN(ka[l]) || ka[l] < 0) endlich = false;
                summeA += ka[l];
            }
        }
        check(gleich, "gleicher seed, gleiche Strahlung");
        check(endlich && summeA > 0, "Strahlung endlich, nicht negativ, nicht überall null");
        b.seed = 6;
        b.vorbereiten(0);
        a.strahlung(0, 0.7, 0.714, ka);
        b.strahlung(0, 0.7, 0.714, kb);
        check(ka[Emission.GRUEN] != kb[Emission.GRUEN] || ka[Emission.ROT] != kb[Emission.ROT], "anderer seed, andere Strahlung");
        a.strahlung(0, 0.7, -0.714, ka);
        check(ka[Emission.GRUEN] == 0, "unter dem Horizont nichts");

        // Qualitätsstufen: Integral über den Himmel nah beieinander
        double[] integral = new double[Aurora.Quality.values().length];
        for (Aurora.Quality q : Aurora.Quality.values()) {
            a.quality = q;
            double sum = 0;
            for (int i = 0; i < 2000; i++) {
                double az = i * 0.0031416 * 7.1, el = Math.asin((i + 0.5) / 2000.0);
                a.strahlung(Math.sin(az) * Math.cos(el), Math.cos(az) * Math.cos(el), Math.sin(el), ka);
                sum += ka[Emission.GRUEN] + ka[Emission.ROT];
            }
            integral[q.ordinal()] = sum;
        }
        double ultra = integral[Aurora.Quality.ULTRA.ordinal()];
        check(Math.abs(integral[Aurora.Quality.LOW.ordinal()] / ultra - 1) < 0.15 && Math.abs(integral[Aurora.Quality.HIGH.ordinal()] / ultra - 1) < 0.05,
                String.format("LOW/ULTRA = %.3f, HIGH/ULTRA = %.3f (Integral über den Himmel)", integral[0] / ultra, integral[2] / ultra));
        a.quality = Aurora.Quality.HIGH;

        // Geometrie: Strahlen lehnen äquatorwärts, der magnetische Zenit liegt auf der Inklination
        check(Math.abs(a.hoehenwinkelMagnetischerZenit() - a.inklination) < 1e-9 && a.inklination > 70, String.format("magnetischer Zenit %.1f° hoch, äquatorwärts", a.inklination));
        Vorhang v = a.hauptbogen();
        double hw = a.hoehenwinkel(v);
        check(hw > -5 && hw < 90, String.format("Hauptbogen %.0f km polwärts, Maximum %.1f° hoch", v.mitteAmMeridian(), hw));
        Aurora au = Aurora.ruhig();
        au.ort = Ort.hobart();
        au.vorbereiten(0);
        check(au.mlat < 0 && au.azimutPolwaerts() > 90 && au.azimutPolwaerts() < 270, String.format("Hobart: Südlicht, polwärts ist Azimut %.0f°", au.azimutPolwaerts()));
        check(Aurora.ruhig().fluss < Aurora.aktiv().fluss && Aurora.aktiv().fluss < Aurora.ausbruch().fluss, "Presets steigen von ruhig bis Aufbruch");
        check(Aurora.kp(3).fluss > Aurora.kp(1).fluss && Aurora.kp(7).fluss > Aurora.kp(5).fluss && Aurora.kp(-2).kp == 0 && Aurora.kp(20).kp == 9, "kp(n) steigt und ist auf 0..9 begrenzt");
        check(Math.abs(a.durchlass(Emission.GRUEN, 90) - Math.exp(-0.15)) < 1e-3 && a.durchlass(Emission.GRUEN, 2) < 0.3 && a.durchlass(Emission.BLAU, 30) < a.durchlass(Emission.ROT, 30),
                "Extinktion: im Zenit e^−0,15, am Horizont unter 0,3, Blau stärker als Rot");

        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static double[] maxima(Emission em, double e0) {
        Niederschlag n = new Niederschlag(e0, 10);
        n.weich = 0;
        double[][] p = em.profile(n.ionisation());
        double[] h = new double[Emission.LINIEN];
        for (int l = 0; l < Emission.LINIEN; l++) h[l] = Niederschlag.hoeheMaximum(p[l]);
        return h;
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private AuroraCheck() { }
}
