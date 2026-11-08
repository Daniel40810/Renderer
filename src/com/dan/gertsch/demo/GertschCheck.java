package com.dan.gertsch.demo;

import com.dan.gertsch.Bild;
import com.dan.gertsch.Dickicht;
import com.dan.gertsch.Kamera;
import com.dan.gertsch.Lab;
import com.dan.gertsch.Licht;
import com.dan.gertsch.Malweise;
import com.dan.gertsch.Messung;
import com.dan.gertsch.Morphologie;
import com.dan.gertsch.Palette;
import com.dan.gertsch.Struktur;
import com.dan.gertsch.Vorlage;
import com.dan.gertsch.Vorlagen;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Prüfung von com.dan.gertsch ohne Fenster und ohne Grafikkarte. Endet mit „Alles in Ordnung.“ oder mit der Zahl der
 * Verstöße (Rückgabewert 1). Rechnet die Messung an künstlichen Bildern mit bekannten Antworten nach (Scheiben
 * bekannter Größe, Streifen bekannter Richtung, Farben bekannter Anteile) und prüft die gespeicherten Vorlagen. Liegen
 * die Bilder in {@code templates/} (oder im Ordner aus dem ersten Argument), misst sie sie neu und vergleicht.
 */
public final class GertschCheck {
    private static int bad;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File ordner = new File(args.length > 0 ? args[0] : "templates");

        // Lab
        double[] w = Lab.vonRgb(0xFFFFFF), s = Lab.vonRgb(0x000000), grau = Lab.vonRgb(0x777777);
        check(Math.abs(w[0] - 100) < 0.01 && Math.abs(w[1]) < 0.01 && Math.abs(w[2]) < 0.01, "Weiß ist L* 100, a* = b* = 0");
        check(Math.abs(s[0]) < 1e-9, "Schwarz ist L* 0");
        check(Math.abs(grau[0] - 50) < 0.2, "sRGB 119 ist L* 50 (%.2f)", grau[0]);
        SplittableRandom rnd = new SplittableRandom(7);
        int schlimmst = 0;
        for (int i = 0; i < 5000; i++) {
            int p = rnd.nextInt(1 << 24);
            double[] l = Lab.vonRgb(p);
            int q = Lab.zuRgb(l[0], l[1], l[2]);
            for (int sh = 0; sh <= 16; sh += 8) schlimmst = Math.max(schlimmst, Math.abs(((p >> sh) & 255) - ((q >> sh) & 255)));
        }
        check(schlimmst <= 1, "sRGB → Lab → sRGB trifft auf eine Stufe genau (größte Abweichung %d)", schlimmst);

        // Bild
        int[] flach = new int[800 * 400];
        Arrays.fill(flach, 0x5a7f3c);
        Bild b = Bild.aus(flach, 800, 400, 400);
        double[] soll = Lab.vonRgb(0x5a7f3c);
        boolean gleich = b.w == 400 && b.h == 200;
        for (int i = 0; i < b.w * b.h; i++) gleich &= Math.abs(b.L[i] - soll[0]) < 1e-3 && Math.abs(b.a[i] - soll[1]) < 1e-3;
        check(gleich, "Verkleinern auf die Arbeitsbreite: 800 × 400 → 400 × 200, eine Fläche bleibt eine Fläche");

        // Morphologie: gegen die Rechnung Punkt für Punkt
        int mw = 37, mh = 23;
        float[] f = new float[mw * mh];
        for (int i = 0; i < f.length; i++) f[i] = (float) rnd.nextDouble(100);
        boolean exakt = true;
        for (int r = 1; r <= 6; r++) {
            float[] e = Morphologie.erodieren(f, mw, mh, r), d = Morphologie.dilatieren(f, mw, mh, r);
            for (int y = 0; y < mh; y++)
                for (int x = 0; x < mw; x++) {
                    float mn = Float.POSITIVE_INFINITY, mx = Float.NEGATIVE_INFINITY;
                    for (int dy = -r; dy <= r; dy++) {
                        int hw = Morphologie.halb(r, Math.abs(dy));
                        for (int dx = -hw; dx <= hw; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || yy < 0 || xx >= mw || yy >= mh) continue;
                            mn = Math.min(mn, f[yy * mw + xx]);
                            mx = Math.max(mx, f[yy * mw + xx]);
                        }
                    }
                    exakt &= e[y * mw + x] == mn && d[y * mw + x] == mx;
                }
        }
        check(exakt, "Erosion und Dilatation mit der Scheibe (Radius 1 bis 6) gleich der Rechnung Punkt für Punkt");
        float[] o = Morphologie.oeffnen(f, mw, mh, 3), c = Morphologie.schliessen(f, mw, mh, 3), oo = Morphologie.oeffnen(o, mw, mh, 3);
        boolean ordnung = Arrays.equals(o, oo);
        for (int i = 0; i < f.length; i++) ordnung &= o[i] <= f[i] && f[i] <= c[i];
        check(ordnung, "Öffnung ≤ Bild ≤ Schließung, zweimal öffnen ändert nichts");

        // Granulometrie: Scheiben bekannter Größe
        Messung m = new Messung();
        m.quality = Messung.Quality.LOW;
        for (int r : new int[]{2, 6}) {
            Vorlage v = m.messen(scheiben(400, 300, r, 10, 80), 400, 300);
            double d = v.hellD50 * 400;
            check(Math.abs(d - (2 * r + 1)) <= (r < 4 ? 1.0 : 1.5), "helle Scheiben mit %d Bildpunkten Durchmesser: gemessen %.1f", 2 * r + 1, d);
        }
        Vorlage loecher = m.messen(scheiben(400, 300, 4, 85, 15), 400, 300);
        check(Math.abs(loecher.dunkelD50 * 400 - 9) <= 1.5, "dunkle Löcher mit 9 Bildpunkten Durchmesser: gemessen %.1f", loecher.dunkelD50 * 400);

        // Richtung: Streifen
        Vorlage senk = m.messen(streifen(400, 300, 1, 0, 8), 400, 300), waag = m.messen(streifen(400, 300, 0, 1, 8), 400, 300),
                schraeg = m.messen(streifen(400, 300, 1, 1, 10), 400, 300);
        check(senk.senkrecht > 0.95 && Math.abs(senk.hauptrichtung - 90) < 2 && senk.kohaerenz > 0.9,
                "senkrechte Streifen: %.0f %% senkrecht, Hauptrichtung %.1f°, Kohärenz %.2f", 100 * senk.senkrecht, senk.hauptrichtung, senk.kohaerenz);
        check(waag.senkrecht < 0.05 && Math.min(waag.hauptrichtung, 180 - waag.hauptrichtung) < 2,
                "waagerechte Streifen: %.0f %% senkrecht, Hauptrichtung %.1f°", 100 * waag.senkrecht, waag.hauptrichtung);
        check(Math.abs(schraeg.hauptrichtung - 45) < 3, "Streifen von links unten nach rechts oben: Hauptrichtung %.1f°", schraeg.hauptrichtung);
        int[] rauschen = new int[400 * 300];
        for (int i = 0; i < rauschen.length; i++) rauschen[i] = Lab.zuRgb(20 + rnd.nextDouble(60), 0, 0);
        Vorlage rau = m.messen(rauschen, 400, 300);
        check(rau.kohaerenz < 0.45, "Rauschen ohne Richtung: Kohärenz %.2f", rau.kohaerenz);

        // Tonstufen: vier Farben in vier Vierteln
        double[][] vier = {{80, 0, 40}, {20, 10, 10}, {60, 30, -20}, {40, -20, 30}};
        int[] viertel = new int[400 * 300];
        for (int y = 0; y < 300; y++)
            for (int x = 0; x < 400; x++) {
                double[] q = vier[(y < 150 ? 0 : 2) + (x < 200 ? 0 : 1)];
                viertel[y * 400 + x] = Lab.zuRgb(q[0], q[1], q[2]);
            }
        m.stufen = 4;
        Vorlage v4 = m.messen(viertel, 400, 300), v4b = m.messen(viertel, 400, 300);
        double[][] ziel = new double[4][];
        for (int i = 0; i < 4; i++) ziel[i] = Lab.vonRgb(Lab.zuRgb(vier[i][0], vier[i][1], vier[i][2]));
        Arrays.sort(ziel, (p, q) -> Double.compare(p[0], q[0]));
        double dE = 0, dA = 0;
        for (int i = 0; i < 4; i++) {
            dE = Math.max(dE, Lab.deltaE(v4.palette.lab[i], ziel[i]));
            dA = Math.max(dA, Math.abs(v4.palette.anteil[i] - 0.25));
        }
        check(dE < 0.5 && dA < 0.005, "k-means findet vier Farben (ΔE höchstens %.2f) und ihre Anteile (je 25 %%)", dE);
        check(Arrays.deepEquals(v4.palette.lab, v4b.palette.lab), "gleicher seed, gleiche Tonstufen");
        m.stufen = 6;

        // Akzente und Grund: Grün mit 5 % Orange und 20 % Dunkel
        int[] gruen = new int[400 * 300];
        int nOrange = 0, nDunkel = 0;
        for (int i = 0; i < gruen.length; i++) {
            double z = rnd.nextDouble();
            if (z < 0.05) { gruen[i] = Lab.zuRgb(60, 30, 60); nOrange++; }
            else if (z < 0.25) { gruen[i] = Lab.zuRgb(10, 0, 0); nDunkel++; }
            else gruen[i] = Lab.zuRgb(50, -40, 30);
        }
        Vorlage ak = m.messen(gruen, 400, 300);
        check(Math.abs(ak.akzent - nOrange / (double) gruen.length) < 1e-9 && Lab.deltaE(ak.akzentLab, new double[]{60, 30, 60}) < 2,
                "Akzente: %.2f %% orange Punkte im Grün gefunden (gesetzt %.2f %%)", 100 * ak.akzent, 100.0 * nOrange / gruen.length);
        check(Math.abs(ak.grund - nDunkel / (double) gruen.length) < 1e-9, "dunkler Grund: %.1f %% (gesetzt %.1f %%)", 100 * ak.grund,
                100.0 * nDunkel / gruen.length);

        // Qualitätsstufen und Leiter
        int vorher = 0;
        boolean steigt = true;
        for (Messung.Quality q : Messung.Quality.values()) {
            m.quality = q;
            steigt &= m.arbeitsbreite() > vorher;
            vorher = m.arbeitsbreite();
        }
        int[] leiter = new Messung().leiter(1000);
        boolean lt = leiter[0] == 1 && leiter[leiter.length - 1] == 35;
        for (int i = 1; i < leiter.length; i++) lt &= leiter[i] > leiter[i - 1];
        check(steigt && lt, "Qualität LOW bis ULTRA misst breiter; Leiter %s", Arrays.toString(leiter));

        // gespeicherte Vorlagen
        Vorlage[] alle = Vorlagen.alle();
        boolean form = alle.length == 6 && Vorlagen.nachName("Sommer").name.equals("sommer");
        for (int i = 0; i < alle.length; i++) form &= gueltig(alle[i]) && alle[i].name.equals(Vorlagen.NAMEN[i]);
        check(form, "sechs Vorlagen, Stufen aufsteigend, Anteile mit Summe 1, Verteilungen steigend bis 1");
        Vorlage herbst = Vorlagen.herbst(), winter = Vorlagen.winter(), graeser = Vorlagen.graeser(), sommer = Vorlagen.sommer(),
                holz = Vorlagen.holzschnitt(), blaetter = Vorlagen.blaetter();
        boolean linien = true;
        for (Vorlage v : alle) if (v != alle[2]) linien &= graeser.kohaerenz > v.kohaerenz + 0.2;
        check(linien && graeser.kohaerenz > 0.9, "Gräser sind Linien: Kohärenz %.2f, deutlich über allen anderen", graeser.kohaerenz);
        double hs = Lab.farbton(sommer.akzentLab[1], sommer.akzentLab[2]);
        check(sommer.akzent > 0.003 && hs > 30 && hs < 90, "Sommer hat orange Akzente im Grün: %.1f %%, Farbton %.0f°", 100 * sommer.akzent, hs);
        check(sommer.grund > 0.2 && graeser.grund > 0.3 && holz.grund < 0.01, "dunkler Grund: Sommer %.0f %%, Gräser %.0f %%, Holzschnitt %.0f %%",
                100 * sommer.grund, 100 * graeser.grund, 100 * holz.grund);
        check(Math.abs(herbst.hauptrichtung - 90) < 10 && herbst.senkrecht > 0.4, "Herbst: Zweige senkrecht (%.0f°, %.0f %%)", herbst.hauptrichtung,
                100 * herbst.senkrecht);
        check(winter.buntheit < 5 && holz.farbton > 180 && holz.farbton < 270 && blaetter.akzent > sommer.akzent,
                "Winter fast unbunt (C* %.1f), Holzschnitt blau (%.0f°), Blätter mit mehr Akzenten als Sommer", winter.buntheit, holz.farbton);
        check(herbst.hellD50 < graeser.hellD50 / 3 && sommer.hellD50 < graeser.hellD50 / 3,
                "Laub fein, Halme breit: Herbst %.2f %%, Sommer %.2f %%, Gräser %.2f %% der Breite", 100 * herbst.hellD50, 100 * sommer.hellD50,
                100 * graeser.hellD50);

        // ------------------------------------------------------------------ Phase 2: Dickicht und Malweise

        // Kamera: Mitte des Bilds auf der Achse, 1 m rechts in 10 m Tiefe um f/10 Bildpunkte daneben
        Kamera kam = new Kamera();
        kam.neigung = 0;
        kam.stellen();
        double[] pr = new double[3];
        kam.projizieren(0, kam.auge[1], 10, pr);
        boolean mitte = Math.abs(pr[0] - kam.breite / 2.0) < 1e-6 && Math.abs(pr[1] - kam.hoehe / 2.0) < 1e-6 && Math.abs(pr[2] - 10) < 1e-9;
        kam.projizieren(1, kam.auge[1], 10, pr);
        check(mitte && Math.abs(pr[0] - (kam.breite / 2.0 + kam.brennweite() / 10)) < 1e-6, "Kamera: Achse in der Bildmitte, 1 m in 10 m Tiefe = f/10 daneben");

        // Schatten: ein Stamm (Radius 0,5 m) bei x = 0, z = 10; Sonne von links (−90°), 20° hoch
        Dickicht leer = new Dickicht();
        Dickicht.Glied stamm = new Dickicht.Glied();
        stamm.x0 = 0; stamm.y0 = 0; stamm.z0 = 10; stamm.x1 = 0; stamm.y1 = 10; stamm.z1 = 10; stamm.r0 = 0.5f; stamm.r1 = 0.5f;
        stamm.stamm = true;
        leer.glieder.add(stamm);
        Licht li = new Licht();
        li.sonneRichtung = -90;
        li.sonneHoehe = 20;
        li.schattenKante = 1024;
        li.vorbereiten(leer);
        double hinten = li.sonne(1.5, 1, 10), vorne = li.sonne(-1.5, 1, 10);
        check(hinten < 0.2 && vorne > 0.8 && li.himmel(3, 1, 10) == 1, "Schatten: hinter dem Stamm %.2f, auf der Sonnenseite %.2f, Himmel ohne Laub 1", hinten, vorne);

        // Blätter aus dem seed des Zweigs
        Dickicht dd = Dickicht.sommer();
        dd.bauen();
        Dickicht.Zweig zw = dd.zweige.get(dd.zweige.size() / 2);
        Dickicht.Blatt b1 = new Dickicht.Blatt(), b2 = new Dickicht.Blatt();
        zw.blatt(3, b1);
        zw.blatt(3, b2);
        double nn = b1.nx * b1.nx + b1.ny * b1.ny + b1.nz * b1.nz, na = b1.nx * b1.ax + b1.ny * b1.ay + b1.nz * b1.az;
        check(b1.x == b2.x && b1.nz == b2.nz && b1.zufall == zw.zufall(3) && Math.abs(nn - 1) < 1e-5 && Math.abs(na) < 1e-5,
                "Blätter aus dem seed: gleich oft gleich, Normale Einheit, Längsachse in der Blattebene");
        Dickicht dd2 = Dickicht.sommer();
        dd2.bauen();
        check(dd.zweige.size() == dd2.zweige.size() && dd.blattZahl() == dd2.blattZahl() && dd.glieder.get(100).x1 == dd2.glieder.get(100).x1,
                "Dickicht aus dem seed: %d Zweige, %,d Blätter, gleich oft gleich", dd.zweige.size(), dd.blattZahl());

        // Größenregel, Flächentreue, gleiches Bild bei gleichem seed (niedrige Qualität)
        Licht ls = new Licht();
        ls.schattenKante = 1536;
        ls.vorbereiten(dd);
        Malweise mal = new Malweise();
        mal.quality = Malweise.Quality.LOW;
        Malweise.Ergebnis e1 = mal.malen(dd, ls, new Kamera()), e2 = mal.malen(dd, ls, new Kamera());
        double zielPx = mal.fleck * e1.breite, flaeche = e1.flaecheNachher / e1.flaecheVorher;
        check(e1.kleinsterFleck >= zielPx * 0.999, "Größenregel: kein Blatt kleiner als %.1f px (kleinstes %.2f px)", zielPx, e1.kleinsterFleck);
        check(flaeche > 0.85 && flaeche < 1.15 && e1.gezeichnet < e1.blaetter,
                "flächentreu zusammengefasst: %,d von %,d Blättern gezeichnet, Fläche nachher/vorher %.2f", e1.gezeichnet, e1.blaetter, flaeche);
        int[] px1 = e1.bild.getRGB(0, 0, e1.breite, e1.hoehe, null, 0, e1.breite), px2 = e2.bild.getRGB(0, 0, e2.breite, e2.hoehe, null, 0, e2.breite);
        check(Arrays.equals(px1, px2), "gleicher seed, gleiches Bild (%d × %d)", e1.breite, e1.hoehe);
        boolean nurStufen = true;
        int akz = 0;
        for (int st : e1.stufe) {
            if (st < -1 || st >= mal.vorlage.palette.size()) nurStufen = false;
            if (st == -1) akz++;
        }
        check(nurStufen && akz > 0, "jeder Fleck in einer Tonstufe der Vorlage oder Akzent (%d Akzente)", akz);

        // Abnahme: das Dickicht in Qualität HIGH gemalt und gemessen, gegen „Sommer“
        Licht lh = new Licht().vorbereiten(dd);
        Malweise malHoch = new Malweise();
        Malweise.Ergebnis eh = malHoch.malen(dd, lh, new Kamera());
        Vorlage ist = new Messung().messen(eh.bild), sollS = Vorlagen.sommer();
        double rel = ist.hellD50 / sollS.hellD50, reld = ist.dunkelD50 / sollS.dunkelD50, haupt = Math.min(ist.hauptrichtung, 180 - ist.hauptrichtung);
        double abwAnteil = 0;
        for (int i = 0; i < sollS.palette.size(); i++) abwAnteil = Math.max(abwAnteil, Math.abs(ist.palette.anteil[i] - sollS.palette.anteil[i]));
        check(Math.abs(ist.l50 - sollS.l50) < 3 && Math.abs(ist.grund - sollS.grund) < 0.08,
                "Sommer, Helligkeit: Median %.1f (Soll %.1f ± 3), Grund %.1f %% (Soll %.1f %% ± 8)", ist.l50, sollS.l50, 100 * ist.grund, 100 * sollS.grund);
        check(abwAnteil < 0.05, "Sommer, Tonstufen: Anteile höchstens %.1f Punkte neben dem Soll (± 5)", 100 * abwAnteil);
        check(rel > 0.75 && rel < 1.3 && reld > 0.75 && reld < 1.35,
                "Sommer, Flecken: hell %.2f %% (Soll %.2f %%, %+.0f %%), dunkel %.2f %% (Soll %.2f %%, %+.0f %%)", 100 * ist.hellD50, 100 * sollS.hellD50,
                100 * (rel - 1), 100 * ist.dunkelD50, 100 * sollS.dunkelD50, 100 * (reld - 1));
        check(haupt < 20 && Math.abs(ist.senkrecht - sollS.senkrecht) < 0.1 && Math.abs(ist.kohaerenz - sollS.kohaerenz) < 0.15,
                "Sommer, Richtung: waagerecht (%.0f°), senkrecht %.0f %% (Soll %.0f %% ± 10), Kohärenz %.2f (Soll %.2f ± 0,15)", ist.hauptrichtung,
                100 * ist.senkrecht, 100 * sollS.senkrecht, ist.kohaerenz, sollS.kohaerenz);
        check(ist.akzent > 0.002 && ist.akzent < 0.015 && Math.abs(ist.farbton - sollS.farbton) < 12,
                "Sommer, Farbe: Akzente %.2f %% (Soll %.2f %%), Farbton %.0f° (Soll %.0f° ± 12)", 100 * ist.akzent, 100 * sollS.akzent, ist.farbton, sollS.farbton);

        // mit den Bildern, wenn sie da sind
        if (!ordner.isDirectory()) System.out.println("  –      " + ordner + " fehlt: Vergleich mit den Bildern übersprungen");
        else for (Vorlage g : alle) {
            File datei = new File(ordner, g.quelle);
            if (!datei.isFile()) {
                System.out.println("  –      " + datei + " fehlt: übersprungen");
                continue;
            }
            Messung mm = new Messung();
            mm.quality = Messung.Quality.valueOf(g.qualitaet);
            mm.stufen = g.stufen;
            mm.seed = g.seed;
            Vorlage n = mm.messen(datei);
            check(passt(g, n), "%s: gespeicherte Kennwerte gleich der neuen Messung von %s", g.titel, g.quelle);
        }

        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Helle (oder dunkle) Scheiben mit Radius r auf einem Gitter, Abstand 6r + 10, Grund L* und Scheibe L*. */
    static int[] scheiben(int w, int h, int r, double grund, double scheibe) {
        int[] o = new int[w * h];
        int pg = Lab.zuRgb(grund, 0, 0), ps = Lab.zuRgb(scheibe, 0, 0), ab = 6 * r + 10;
        Arrays.fill(o, pg);
        for (int cy = ab / 2; cy < h - r; cy += ab)
            for (int cx = ab / 2; cx < w - r; cx += ab)
                for (int dy = -r; dy <= r; dy++)
                    for (int dx = -Morphologie.halb(r, Math.abs(dy)); dx <= Morphologie.halb(r, Math.abs(dy)); dx++)
                        o[(cy + dy) * w + cx + dx] = ps;
        return o;
    }

    /** Streifen L* = 50 + 30·sin(2π(kx·x + ky·y)/Periode); kx = 1, ky = 0 gibt senkrechte Streifen. */
    static int[] streifen(int w, int h, int kx, int ky, double periode) {
        int[] o = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) o[y * w + x] = Lab.zuRgb(50 + 30 * Math.sin(2 * Math.PI * (kx * x + ky * y) / periode), 0, 0);
        return o;
    }

    static boolean gueltig(Vorlage v) {
        Palette p = v.palette;
        double summe = 0;
        boolean ok = p.size() == v.stufen && v.hell.length == v.durchmesser.length && v.dunkel.length == v.durchmesser.length;
        for (int i = 0; i < p.size(); i++) {
            summe += p.anteil[i];
            ok &= p.anteil[i] >= 0 && (i == 0 || p.lab[i][0] >= p.lab[i - 1][0]);
        }
        ok &= Math.abs(summe - 1) < 0.002;
        for (int i = 1; i < v.durchmesser.length; i++)
            ok &= v.durchmesser[i] > v.durchmesser[i - 1] && v.hell[i] >= v.hell[i - 1] && v.dunkel[i] >= v.dunkel[i - 1];
        ok &= v.hell.length > 0 && v.hell[v.hell.length - 1] == 1 && v.dunkel[v.dunkel.length - 1] == 1;
        double r = 0, hl = 0;
        for (double x : v.richtung) r += x;
        for (double x : v.helligkeit) hl += x;
        ok &= Math.abs(r - 1) < 0.002 && Math.abs(hl - 1) < 0.002;
        ok &= v.hellD50 > 0 && v.hellD50 < 0.1 && v.dunkelD50 > 0 && v.dunkelD50 < 0.1;
        ok &= v.senkrecht >= 0 && v.senkrecht <= 1 && v.kohaerenz >= 0 && v.kohaerenz <= 1 && v.grund >= 0 && v.grund <= 1;
        return ok;
    }

    /** Gespeichert und neu gemessen gleich bis auf die Rundung im Quelltext. */
    static boolean passt(Vorlage g, Vorlage n) {
        boolean ok = g.palette.size() == n.palette.size() && g.arbeitsbreite == n.arbeitsbreite;
        for (int i = 0; ok && i < g.palette.size(); i++)
            ok = Lab.deltaE(g.palette.lab[i], n.palette.lab[i]) < 0.05 && Math.abs(g.palette.anteil[i] - n.palette.anteil[i]) < 1e-3;
        ok &= Math.abs(g.l50 - n.l50) < 0.01 && Math.abs(g.grund - n.grund) < 1e-3 && Math.abs(g.akzent - n.akzent) < 1e-3;
        ok &= Math.abs(g.hellD50 - n.hellD50) < 1e-4 && Math.abs(g.dunkelD50 - n.dunkelD50) < 1e-4;
        ok &= Math.abs(g.senkrecht - n.senkrecht) < 1e-3 && Math.abs(g.kohaerenz - n.kohaerenz) < 1e-3;
        return ok;
    }

    static void check(boolean c, String what, Object... args) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + String.format(Locale.GERMAN, what, args));
    }

    private GertschCheck() { }
}
