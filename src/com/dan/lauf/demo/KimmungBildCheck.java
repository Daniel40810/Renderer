package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.raum.Erde;
import com.dan.physics.atmos.Profil;
import com.dan.render.air.Kimmung;
import com.dan.render.reference.Luftstrahl;
import com.dan.render.reference.Strahlgleichung;

import java.nio.file.Path;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Kimmung im Bild (P10b Teil 2, Werkbuch HalligHooge Teil A, 12.11): die Prüfsteine 10–12 ohne Karte und ohne
 * Gebiet.
 * <ul>
 * <li><b>10 Tafel gegen die Strahlgleichung</b> (Profil aus P10a: Wasser 12 °C, Luft 7 °C, 3 m/s; Auge 4 m): wo der
 * Strahl die Fläche erreicht, je Sehwinkel; der Grenzwinkel glatt und mit Sperrhöhe; die Höhe eines Strahls, der
 * umkehrt, in 5, 10 und 20 km.</li>
 * <li><b>11 Normalatmosphäre:</b> Mit dem Gefälle k = 0,13 sind die Strahlen im gesenkten Bild gerade; der Durchgang
 * (auf dem Prozessor, {@link Kimmung#durchgang}) lässt ein Bild, wie es ist.</li>
 * <li><b>12 Untere Spiegelung</b> im gerechneten Bild über ebener See mit einer Insel in 8 km: Die Kimm liegt am
 * Grenzwinkel, darunter steht das Spiegelbild der Insel.</li>
 * </ul>
 * Was die Karte braucht (Kernel gegen den Prozessor, Zeiten, das Bild im Gebiet), prüft {@code KimmungKarteCheck}.
 * Attest {@code docs/status/P10b_Kimmung.md}.
 */
public final class KimmungBildCheck {
    private static final Attest A = new Attest("com.dan.render.air (Kimmung)", Kimmung.VERSION);
    static final double R = Erde.R, MIN = 60 * 180 / Math.PI;

    public static void main(String[] args) {
        List<String> a = List.of(args);
        try {
            tafel();
            normal();
            spiegelung();
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", AtmosLaufCheck.arg(a, "--name", "P10b_Kimmung") + ".md"), "P10b – KimmungBildCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Die Höhen des Profils über der Fläche. */
    static double[] hoehen() {
        double[] z = new double[Profil.STUFEN];
        for (int k = 0; k < z.length; k++) z[k] = Profil.hoehe(k);
        return z;
    }

    static Kimmung.Tafel tafel(Profil pr, double auge, double sperr) { return Kimmung.tafel(hoehen(), pr.r, auge, sperr, 0.5, 50, 100_000, Kimmung.NB, Kimmung.ND); }

    /** Ein Profil mit dem Gefälle der Normalatmosphäre des Bilds (k = 0,13) von der Fläche an. */
    static Profil normalprofil() { return Profil.linear(1.00028, -Erde.REFRAKTION * 1.00028 / R); }

    /** Die Strahlgleichung auf der Kugel: Höhe (m) des Strahls unter dem Winkel beta (rad, nach unten positiv) im Bogen d (m) vom Auge; NaN, wenn er vorher die Fläche trifft. */
    static double hoeheIn(Strahlgleichung sg, double auge, double beta, double d) {
        double[] z = sg.start(new double[] {0, R + auge, 0}, new double[] {Math.cos(beta), -Math.sin(beta), 0});
        double h = auge;
        for (int i = 0; i < 4_000_000; i++) {
            double bogen = R * Math.atan2(z[0], z[1]);
            if (bogen >= d) return h;
            z = sg.schritt(z, Math.min(h < 1 ? 0.25 : 1.0, Math.max(1e-3, d - bogen)));
            h = Math.hypot(z[0], z[1]) - R;
            if (h <= 0) return Double.NaN;
        }
        throw new IllegalStateException("Strahl endet nicht");
    }

    /** Grenzwinkel der Tafel (rad unter der Waagerechten): die Mitte zwischen der flachsten Zeile, die die Fläche erreicht, und der nächsten darüber. */
    static double grenzwinkel(Kimmung.Tafel t) {
        for (int i = t.nb - 1; i >= 0; i--)
            if (t.wasser(i) >= 0) return -Math.atan(0.5 * (t.tan(i) + t.tan(Math.min(t.nb - 1, i + 1))));
        return Double.NaN;
    }

    // ------------------------------------------------------------------ 10 Tafel

    static void tafel() {
        A.teil("10 Tafel gegen die Strahlgleichung");
        double auge = 4;
        Profil pr = KimmungCheck.profil(3, 280.15, 285.15, 0.8);
        Strahlgleichung sg = KimmungCheck.kugel(pr);
        long n0 = System.nanoTime();
        Kimmung.Tafel t = tafel(pr, auge, 0);
        double sek = (System.nanoTime() - n0) * 1e-9;
        // (a) wo der Strahl die Fläche erreicht
        double groesst = 0;
        int zahl = 0;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.nb; i += 16) {
            if (t.wasser(i) < 0) continue;
            double beta = -Math.atan(t.tan(i));
            double[] s = KimmungCheck.strahl(sg, auge, beta);
            if (s[0] > 0) continue;                                       // die Strahlgleichung sieht ihn umkehren: am Grenzwinkel, s. (b)
            double ab = Math.abs(t.wasser(i) / s[1] - 1);
            groesst = Math.max(groesst, ab);
            zahl++;
            if (zahl % 6 == 1) b.append(f("%.1f′: %.0f m (%.0f m); ", beta * MIN, t.wasser(i), s[1]));
        }
        A.hoechstens(f("Prüfstein 10: Entfernung, in der der Strahl die Fläche erreicht, Tafel gegen die Strahlgleichung, %d Sehwinkel", zahl), groesst * 100, 1.0, "%");
        A.info("Treffpunkte", b.toString());
        // (b) Grenzwinkel glatt und mit Sperrhöhe
        double g0 = grenzwinkel(t), s0 = KimmungCheck.grenzwinkelFrei(pr, auge, 0);
        A.hoechstens("Prüfstein 10: Grenzwinkel über glatter Fläche, Tafel gegen die Erhaltungsgröße", Math.abs(g0 - s0) * MIN, 0.05, "′");
        StringBuilder bg = new StringBuilder(f("glatt %.2f′ (%.2f′); ", g0 * MIN, s0 * MIN));
        double gs = 0;
        for (double sperr : new double[] {0.02, 0.05, 0.12, 0.5}) {
            double gt = grenzwinkel(tafel(pr, auge, sperr)), ge = KimmungCheck.grenzwinkelFrei(pr, auge, sperr);
            gs = Math.max(gs, Math.abs(gt - ge) * MIN);
            bg.append(f("ab %.2f m: %.2f′ (%.2f′); ", sperr, gt * MIN, ge * MIN));
        }
        A.hoechstens("Prüfstein 10: Grenzwinkel mit Sperrhöhe (2, 5, 12, 50 cm), größte Abweichung", gs, 0.05, "′");
        A.info("Grenzwinkel, Tafel (Erhaltungsgröße)", bg.toString());
        // (c) Höhe eines Strahls, der umkehrt
        double hg = 0;
        StringBuilder bh = new StringBuilder();
        for (double minuten : new double[] {4, 8, 11}) {
            double beta = minuten / MIN, tan = -Math.tan(beta);
            for (double d : new double[] {5000, 10_000, 20_000}) {
                double soll = hoeheIn(sg, auge, beta, d);
                double ist = (tan + t.delta(tan, d)) * d + auge + d * d / (2 * Erde.R_OPTISCH);
                if (Double.isNaN(soll)) continue;
                hg = Math.max(hg, Math.abs(ist - soll) / d * MIN);
                bh.append(f("%.0f′ in %.0f km: %.2f m (%.2f m); ", minuten, d / 1000, ist, soll));
            }
        }
        A.hoechstens("Prüfstein 10: Höhe umkehrender Strahlen in 5, 10, 20 km, als Winkel vom Auge", hg, 0.05, "′");
        A.info("Höhen, Tafel (Strahlgleichung)", bh.toString());
        A.info("Tafel", f("%d Sehwinkel × %d Entfernungen in %.3f s; größtes δ %.2f′", t.nb, t.nd, sek, t.groesstes() * MIN));
    }

    // ------------------------------------------------------------------ das gerade Bild einer ebenen See

    /** Ein gerades Bild: HDR (vier Werte je Bildpunkt) und Tiefe. */
    record Gerade(float[] hdr, float[] tiefe, Blick blick, int breite, int hoehe) { }

    /**
     * Rechnet das gerade Bild einer ebenen See (Fläche in der Höhe 0, das Auge in {@code auge} darüber, gesenkt um
     * d² / 2 R′) mit einer Insel als Wand quer zum Blick: von {@code inselAb} bis {@code inselBis} m rechts der Achse, in
     * {@code inselWeit} m, {@code inselHoch} m hoch. Farben: See dunkel nach der Entfernung, Insel hell nach der Höhe,
     * Himmel nach dem Sehwinkel – jede Zeile ist an ihrer Farbe zu erkennen.
     */
    static Gerade see(double auge, double bildwinkel, int breite, int hoehe, double inselWeit, double inselHoch, double inselAb, double inselBis) {
        Blick b = new Blick(0, 0, auge, 0, 0, bildwinkel);
        b.groesse(breite, hoehe);
        float[] hdr = new float[4 * breite * hoehe], tiefe = new float[breite * hoehe];
        double[][] bas = b.basis();
        double[] farbe = new double[4];
        for (int py = 0; py < hoehe; py++)
            for (int px = 0; px < breite; px++) {
                double[] v = b.strahl(px + 0.5, py + 0.5);
                double weg = farbe(v, auge, inselWeit, inselHoch, inselAb, inselBis, farbe);
                int k = py * breite + px;
                for (int c = 0; c < 4; c++) hdr[4 * k + c] = (float) farbe[c];
                if (weg > 0) tiefe[k] = (float) (b.nah / (weg * (v[0] * bas[2][0] + v[1] * bas[2][1] + v[2] * bas[2][2])));
            }
        return new Gerade(hdr, tiefe, b, breite, hoehe);
    }

    /** Was die Gerade in der Richtung v (Einheitsvektor) im Bild von {@link #see} zeigt: Farbe nach {@code aus}; gibt den Weg (m), 0 für den Himmel. */
    static double farbe(double[] v, double auge, double inselWeit, double inselHoch, double inselAb, double inselBis, double[] aus) {
        double rOpt2 = 2 * Erde.R_OPTISCH, wag = Math.hypot(v[0], v[2]), tan = v[1] / wag;
        // See: y = −auge − d²/2R′ = tan d
        double dSee = Double.NaN, D = tan * tan - 4 * auge / rOpt2;
        if (tan < 0 && D >= 0) dSee = 2 * auge / (-tan + Math.sqrt(D));
        // Insel: Wand in der Entfernung inselWeit längs der Achse (Nord)
        double dInsel = Double.NaN, hInsel = 0;
        if (inselWeit > 0 && -v[2] > 0) {
            double weg = inselWeit / -v[2], x = v[0] * weg, d = wag * weg, y = v[1] * weg + auge + d * d / rOpt2;
            if (x >= inselAb && x <= inselBis && y >= 0 && y <= inselHoch && (Double.isNaN(dSee) || d < dSee)) {
                dInsel = d;
                hInsel = y;
            }
        }
        aus[3] = 1;
        double d = !Double.isNaN(dInsel) ? dInsel : dSee;
        if (Double.isNaN(d)) {
            aus[0] = aus[1] = 2000 + 4e5 * tan;
            aus[2] = 3000;
            return 0;
        }
        if (!Double.isNaN(dInsel)) {
            aus[0] = 5000 + 1000 * hInsel;
            aus[1] = 4000;
            aus[2] = 500;
        } else {
            aus[0] = 100;
            aus[1] = 200 + d / 50;
            aus[2] = 400;
        }
        return d / wag;
    }

    /** Dieselbe See mit Insel als Szene der Referenz (im gesenkten Raum, relativ zum Auge). */
    static com.dan.render.reference.Szene szene(double auge, double inselWeit, double inselHoch, double inselAb, double inselBis) {
        double k = 1 / (2 * Erde.R_OPTISCH);
        return new com.dan.render.reference.Szene() {
            @Override public boolean schneide(double[] p, double[] d, double tMax, Treffer aus) {
                double best = Double.POSITIVE_INFINITY;
                // See: p.y + t d.y + auge + k |p.xz + t d.xz|² = 0
                double a = k * (d[0] * d[0] + d[2] * d[2]), b = d[1] + 2 * k * (p[0] * d[0] + p[2] * d[2]), c = p[1] + auge + k * (p[0] * p[0] + p[2] * p[2]);
                if (c > 0) {
                    double D = b * b - 4 * a * c;
                    if (D >= 0) {
                        double t = a > 0 ? 2 * c / (-b + Math.sqrt(D)) : -c / b;
                        if (t > 0 && t < tMax) best = t;
                    }
                }
                if (inselWeit > 0 && d[2] < 0) {
                    double t = (-inselWeit - p[2]) / d[2];
                    if (t > 0 && t < tMax && t < best) {
                        double x = p[0] + t * d[0], y = p[1] + t * d[1] + auge + k * (x * x + inselWeit * inselWeit);
                        if (x >= inselAb && x <= inselBis && y >= 0 && y <= inselHoch) best = t;
                    }
                }
                if (best == Double.POSITIVE_INFINITY) return false;
                aus.t = best;
                return true;
            }

            @Override public double augeHoehe() { return auge; }
        };
    }

    // ------------------------------------------------------------------ 11 Normalatmosphäre

    static void normal() {
        A.teil("11 Normalatmosphäre");
        Kimmung.Tafel t = tafel(normalprofil(), 4, 0.02);
        A.hoechstens("Größtes δ der Tafel im Gefälle k = 0,13", t.groesstes() * MIN, 0.01, "′");
        Gerade g = see(4, 5.6, 240, 1080, 8000, 6, -150, 150);
        float[] neu = Kimmung.durchgang(t, g.blick, g.hdr, g.tiefe, g.breite, g.hoehe);
        double groesst = 0;
        for (int i = 0; i < neu.length; i++) groesst = Math.max(groesst, Math.abs(neu[i] - g.hdr[i]) / Math.max(1, Math.abs(g.hdr[i])));
        A.hoechstens("Prüfstein 11: Der Durchgang lässt das Bild der ebenen See mit Insel, wie es ist (größter Unterschied, relativ)", groesst, 1e-6, "");
        // dasselbe mit dem Profil der Normalatmosphäre aus der Temperatur (−6,5 K/km): die Luft ist dichter geschichtet als k = 0,13
        Kimmung.Tafel tn = tafel(Profil.ausGefaelle(283.15, -0.0065, 101325, 0.55, 430), 4, 0.02);
        A.info("Zum Vergleich −6,5 K je km von der Fläche an", f("größtes δ %.2f′; Kimm %.2f′ gegen %.2f′ im Bild", tn.groesstes() * MIN, grenzwinkel(tn) * MIN, grenzwinkel(t) * MIN));
    }

    // ------------------------------------------------------------------ 12 untere Spiegelung im Bild

    /** Zeigt der Bildpunkt k die Insel (Grün 4000, Blau 500)? */
    static boolean insel(float[] hdr, int k) { return Math.abs(hdr[4 * k + 1] - 4000f) < 1 && Math.abs(hdr[4 * k + 2] - 500f) < 1; }

    static void spiegelung() {
        A.teil("12 Untere Spiegelung im Bild");
        double auge = 4, sperr = 0.05;
        Profil pr = KimmungCheck.profil(3, 280.15, 285.15, 0.8);
        Kimmung.Tafel t = tafel(pr, auge, sperr);
        int breite = 240, hoehe = 1080;
        Gerade g = see(auge, 5.6, breite, hoehe, 8000, 6, -150, 150);
        long n0 = System.nanoTime();
        float[] neu = Kimmung.durchgang(t, g.blick, g.hdr, g.tiefe, g.breite, g.hoehe);
        double sek = (System.nanoTime() - n0) * 1e-9;
        Kimmung.Geometrie q = new Kimmung.Geometrie(g.blick, breite, hoehe);
        // Kimm neben der Insel: die oberste Zeile, die noch See zeigt (Blau 400)
        int frei = 20, mitte = breite / 2;
        int kimmGerade = -1, kimmNeu = -1;
        for (int py = 0; py < hoehe; py++) {
            if (kimmGerade < 0 && g.hdr[4 * (py * breite + frei) + 2] == 400f) kimmGerade = py;
            if (kimmNeu < 0 && Math.abs(neu[4 * (py * breite + frei) + 2] - 400f) < 1 && neu[4 * (py * breite + frei)] < 150) kimmNeu = py;
        }
        double grenz = KimmungCheck.grenzwinkelFrei(pr, auge, sperr), soll = q.zeile(frei, -Math.tan(grenz));
        double jeMinute = hoehe / (2 * Math.tan(Math.toRadians(5.6) / 2)) / MIN;
        A.hoechstens(f("Prüfstein 12: Kimm im Bild mit Kimmung gegen den Grenzwinkel (%.2f′ unter der Waagerechten; %.1f Zeilen je ′)", grenz * MIN, jeMinute),
                Math.abs(kimmNeu - 0.5 - soll), 1.0, "Zeilen");
        A.info("Kimm", f("gerade in Zeile %d, mit Kimmung in Zeile %d (Soll %.1f): %d Zeilen tiefer", kimmGerade, kimmNeu, soll + 0.5, kimmNeu - kimmGerade));
        // Insel: Zeilen in der Mitte, die die Insel zeigen (Grün 4000); aufrecht wächst das Rot (die Höhe) nach oben, im Spiegelbild nach unten
        int erste = -1, letzte = -1, aufrecht = 0, gespiegelt = 0;
        for (int py = 1; py < hoehe; py++) {
            boolean insel = insel(neu, py * breite + mitte), vorher = insel(neu, (py - 1) * breite + mitte);
            if (!insel) continue;
            if (erste < 0) erste = py;
            letzte = py;
            if (!vorher) continue;
            float dr = neu[4 * (py * breite + mitte)] - neu[4 * ((py - 1) * breite + mitte)];
            if (dr < -1) aufrecht++;
            else if (dr > 1) gespiegelt++;
        }
        int inselGerade = 0;
        for (int py = 0; py < hoehe; py++) if (insel(g.hdr, py * breite + mitte)) inselGerade++;
        A.pruefe(gespiegelt >= 3 && aufrecht >= 3, "Prüfstein 12: Unter der Insel in 8 km steht ihr Spiegelbild (Zeilen, in denen die Höhe nach oben wächst; Zeilen, in denen sie nach unten wächst)",
                f("%d aufrecht, %d gespiegelt; Insel in den Zeilen %d bis %d (im geraden Bild %d Zeilen)", aufrecht, gespiegelt, erste, letzte, inselGerade), "je ≥ 3");
        A.info("Durchgang auf dem Prozessor", f("%d × %d Bildpunkte in %.2f s", breite, hoehe, sek));
        referenz(pr, auge, sperr, g, neu);
    }

    // ------------------------------------------------------------------ 13 (ohne Gebiet) Referenz gegen den Durchgang

    /** Art eines Bildpunkts nach der Farbe: 0 Himmel, 1 See, 2 Insel. */
    static int art(double r, double g, double b) { return Math.abs(b - 500) < 1 ? 2 : Math.abs(b - 400) < 1 && r < 150 ? 1 : 0; }

    /**
     * Dasselbe Bild auf dem Weg der Referenz ({@link Luftstrahl}: Strahlgleichung über der Kugel, Schnitt mit den Flächen)
     * gegen den Durchgang im Bild (Tafel aus Parabeln, Suche in der Spalte): Art je Bildpunkt und die Zeile der Kimm.
     */
    static void referenz(Profil pr, double auge, double sperr, Gerade g, float[] neu) {
        A.teil("13 Referenz gegen den Durchgang (ebene See mit Insel, ohne Gebiet)");
        Luftstrahl ls = new Luftstrahl(szene(auge, 8000, 6, -150, 150), hoehen(), pr.r, auge, sperr, 0.5, 40_000, 6);
        Kimmung.Geometrie q = new Kimmung.Geometrie(g.blick, g.breite, g.hoehe);
        int[] zeilen = q.streifen(Math.tan(Math.toRadians(0.5)));
        double[] farbe = new double[4];
        long gleich = 0, alle = 0, rand = 0;
        int kimmRef = -1, kimmNeu = -1, frei = 20;
        double groesst = 0, summe = 0;
        long hell = 0;
        for (int py = zeilen[0] + 2; py <= zeilen[1] - 2; py++)
            for (int px = 0; px < g.breite; px++) {
                double[] v = g.blick.strahl(px + 0.5, py + 0.5);
                farbe(ls.gerade(v), auge, 8000, 6, -150, 150, farbe);
                int k = py * g.breite + px, aR = art(farbe[0], farbe[1], farbe[2]), aN = art(neu[4 * k], neu[4 * k + 1], neu[4 * k + 2]);
                alle++;
                if (aR == aN) {
                    gleich++;
                    double ab = Math.abs(neu[4 * k] - farbe[0]) / Math.max(1, farbe[0]);
                    groesst = Math.max(groesst, ab);
                    summe += ab;
                    hell++;
                } else {
                    // ungleich nur am Rand zweier Arten? Dann zeigt eine Nachbarzeile des Durchgangs die Art der Referenz
                    boolean nachbar = false;
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                        int kk = (py + dy) * g.breite + Math.max(0, Math.min(g.breite - 1, px + dx));
                        if (art(neu[4 * kk], neu[4 * kk + 1], neu[4 * kk + 2]) == aR) nachbar = true;
                    }
                    if (nachbar) rand++;
                }
                if (px == frei) {
                    if (kimmRef < 0 && aR == 1) kimmRef = py;
                    if (kimmNeu < 0 && aN == 1) kimmNeu = py;
                }
            }
        A.hoechstens("Prüfstein 13 (ohne Gebiet): Zeile der Kimm, Durchgang gegen Referenz", Math.abs(kimmNeu - kimmRef), 1.0, "Zeilen");
        A.pruefe(gleich + rand == alle && rand <= 0.03 * alle, "Prüfstein 13 (ohne Gebiet): Art je Bildpunkt (Himmel, See, Insel), Durchgang gegen Referenz",
                f("%d von %d gleich, %d am Rand zweier Arten um einen Bildpunkt versetzt, %d sonst", gleich, alle, rand, alle - gleich - rand), "gleich bis auf Ränder (≤ 3 %) [G]");
        A.info("Farbe, wo die Art gleich ist (Rot: Höhe an der Insel, Sehwinkel im Himmel)", f("im Mittel %.2f %%, größte Abweichung %.1f %%", 100 * summe / Math.max(1, hell), 100 * groesst));
        int flaeche = 0, eckenSumme = 0;
        for (int i = 0; i < Luftstrahl.NB; i++) {
            if (ls.erreichtFlaeche(i)) flaeche++;
            eckenSumme += ls.ecken(i);
        }
        A.info("Wege der Referenz", f("%d Sehwinkel in %.1f s, im Mittel %d Ecken; %d erreichen die Fläche; %d Strahlen im Streifen, %d mit Treffer, %d zum Himmel", Luftstrahl.NB, ls.sekunden,
                eckenSumme / Luftstrahl.NB, flaeche, ls.strahlen.sum(), ls.treffer.sum(), ls.himmel.sum()));
    }
}
