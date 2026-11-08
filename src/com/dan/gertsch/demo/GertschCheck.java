package com.dan.gertsch.demo;

import com.dan.gertsch.Bild;
import com.dan.gertsch.Dickicht;
import com.dan.gertsch.Druck;
import com.dan.gertsch.Kamera;
import com.dan.gertsch.Lab;
import com.dan.gertsch.Licht;
import com.dan.gertsch.Malweise;
import com.dan.gertsch.Messung;
import com.dan.gertsch.Morphologie;
import com.dan.gertsch.Palette;
import com.dan.gertsch.Punkte;
import com.dan.gertsch.Quelle;
import com.dan.gertsch.Stich;
import com.dan.gertsch.Struktur;
import com.dan.gertsch.Stufen;
import com.dan.gertsch.Vorlage;
import com.dan.gertsch.Vorlagen;
import com.dan.gertsch.Wind;
import com.dan.gertsch.Zufall;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Prüfung von com.dan.gertsch ohne Fenster und ohne Grafikkarte (Phasen 1 bis 5). Endet mit „Alles in Ordnung.“ oder mit der Zahl der
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
        malHoch.mitLicht = true;   // das Licht ohne Stufen braucht Phase 4
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

        // ------------------------------------------------------------------ Phase 3: was Prozessor und Grafikkarte teilen

        // Zufall: feste Werte (gertsch.glsl rechnet dieselbe Formel; GertschGlCheck vergleicht dort) und gleichverteilt
        boolean fest = Zufall.pcg(0) == 129708002 && Zufall.pcg(1) == -1463883204 && Zufall.pcg(0x12345678) == -1722608927;
        int[] fach = new int[10];
        for (int i = 0; i < 200_000; i++) fach[(int) (Zufall.zug(77, i, 3) * 10)]++;
        int fachMin = Arrays.stream(fach).min().getAsInt(), fachMax = Arrays.stream(fach).max().getAsInt();
        check(fest && fachMin > 19_400 && fachMax < 20_600, "Zufall (PCG): feste Werte stimmen, gleichverteilt (Fächer %d bis %d von 20 000)", fachMin, fachMax);

        // Wind: still ohne Wind, Auslenkung ∝ U², Flattern höchstens 0,45 rad
        Wind wi = new Wind();
        wi.boeen = 0;
        double[] v5 = new double[2], v10 = new double[2], v0 = new double[2];
        wi.staerke = 0;
        wi.versatz(3, 5, 8, 0.3, 1.7, v0);
        boolean still = v0[0] == 0 && v0[1] == 0 && wi.flattern(0.4, 2.1) == 0;
        wi.staerke = 5;
        wi.versatz(3, 5, 8, 0.3, 1.7, v5);
        wi.staerke = 10;
        wi.versatz(3, 5, 8, 0.3, 1.7, v10);
        double flMax = 0;
        for (int i = 0; i < 1000; i++) flMax = Math.max(flMax, Math.abs(wi.flattern(i / 1000.0, i * 0.013)));
        check(still && Math.abs(Math.hypot(v10[0], v10[1]) / Math.hypot(v5[0], v5[1]) - 4) < 1e-9 && flMax <= Wind.FLATTERN + 1e-12 && flMax > 0.4,
                "Wind: still bei 0 m/s, Auslenkung bei 10 m/s viermal die bei 5 m/s, Flattern bis %.2f rad", flMax);

        // Stufen: Anteile mit dem Grund, Grenzen aus einem Histogramm, Hysterese
        double[] anS = Stufen.anteile(Vorlagen.sommer());
        double summeAn = 0;
        for (double a : anS) summeAn += a;
        long[] flachHist = new long[256];
        Arrays.fill(flachHist, 9000 / 256);
        long nullen = 1000, alleBp = nullen + 256 * (9000 / 256);
        double[] gr = Stufen.grenzen(flachHist, nullen, -24, 4, new double[]{0.25, 0.25, 0.25, 0.25});
        double erwartet = -24 + 28 * (0.25 * alleBp - nullen) / (alleBp - nullen);
        check(Math.abs(summeAn - 1) < 1e-9 && Math.abs(anS[0] - Vorlagen.sommer().grund) < 1e-9 && Math.abs(gr[0] - erwartet) < 0.15,
                "Stufen: Anteile mit Summe 1, Stufe 0 = Grund der Vorlage, erste Grenze %.2f (erwartet %.2f)", gr[0], erwartet);
        double[] g2 = {0, 1};
        check(Stufen.band(0.05, g2, 0, 0.3) == 0 && Stufen.band(0.35, g2, 0, 0.3) == 1 && Stufen.band(-0.2, g2, 1, 0.3) == 1
                        && Stufen.band(-0.4, g2, 1, 0.3) == 0 && Stufen.band(0.5, g2, -1, 0.3) == 1,
                "Hysterese: das alte Band hält bis 0,3 Blenden über seine Grenzen, danach das neue");

        // Plätze der Hysterese auf der Grafikkarte: durch 4 teilbar, steigend, höchstens 255 Blätter je Zweig
        boolean plaetzeOk = true;
        int maxBl = 0, vorherPl = -1;
        for (Dickicht.Zweig z : dd.zweige) {
            plaetzeOk &= z.platz % 4 == 0 && z.platz > vorherPl;
            vorherPl = z.platz;
            maxBl = Math.max(maxBl, z.blaetter);
        }
        check(plaetzeOk && maxBl <= 255 && dd.plaetze >= dd.blattZahl(), "Plätze: je Zweig durch 4 teilbar, %,d Plätze für %,d Blätter, höchstens %d Blätter je Zweig",
                dd.plaetze, dd.blattZahl(), maxBl);

        // Kamera: die Matrix für OpenGL trifft dieselben Bildpunkte wie projizieren
        Kamera km = new Kamera();
        km.gier = 12;
        km.neigung = -5;
        km.stellen();
        float[] mvp = km.blickProjektion(0.3, 250);
        double fehlerPx = 0;
        for (int i = 0; i < 1000; i++) {
            double x = rnd.nextDouble(-20, 20), y = rnd.nextDouble(0, 15), z = rnd.nextDouble(2, 60);
            if (!km.projizieren(x, y, z, pr)) continue;
            double cx = mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12], cy = mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13], cw = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15];
            double sx = (cx / cw + 1) / 2 * km.breite, sy = (1 - cy / cw) / 2 * km.hoehe;
            fehlerPx = Math.max(fehlerPx, Math.hypot(sx - pr[0], sy - pr[1]));
        }
        check(fehlerPx < 0.02, "Kamera: Matrix für OpenGL (float) und projizieren treffen dieselben Bildpunkte (höchstens %.3f px daneben)", fehlerPx);


        // ------------------------------------------------------------------ Phase 4: Holzschnitt

        // Void and Cluster: jeder Rang einmal; die kleinsten 10 % gleichmäßig (Clark–Evans auf dem Torus)
        float[] kachel = Punkte.kachel();
        int nk = Punkte.N * Punkte.N;
        boolean[] gesehen = new boolean[nk];
        boolean permutation = true;
        for (float r : kachel) {
            int k = (int) Math.floor(r * nk);
            permutation &= k >= 0 && k < nk && !gesehen[k];
            if (k >= 0 && k < nk) gesehen[k] = true;
        }
        double rBlau = ordnungTorus(kachel, 0.1);
        Punkte zufall = new Punkte();
        zufall.ordnung = 0;
        float[] weiss = new float[nk];
        for (int j = 0; j < Punkte.N; j++) for (int i = 0; i < Punkte.N; i++) weiss[j * Punkte.N + i] = zufall.rang(i, j);
        double rWeiss = ordnungTorus(weiss, 0.1);
        check(permutation && rBlau > 1.5 && rWeiss > 0.9 && rWeiss < 1.12,
                "Ränge: Void and Cluster vergibt jeden Rang einmal; die kleinsten 10 %% gleichmäßig (R %.2f), mit weißem Rang zufällig (R %.2f)", rBlau, rWeiss);

        // Mischen bleibt gleichverteilt
        int[] mf = new int[10];
        for (int i = 0; i < 200_000; i++) mf[Math.min(9, (int) (10 * Punkte.mischen((float) rnd.nextDouble(), (float) rnd.nextDouble(), 0.75f)))]++;
        int mfMin = Arrays.stream(mf).min().getAsInt(), mfMax = Arrays.stream(mf).max().getAsInt();
        check(mfMin > 19_400 && mfMax < 20_600, "Rang gemischt aus Blue Noise und Zufall (0,75): gleichverteilt (Fächer %d bis %d von 20 000)", mfMin, mfMax);

        // Eichkurve und Anteil Papier auf flachen Tönen (ohne Wolken und Korn)
        Stich flachStich = new Stich();
        flachStich.wolken = 0;
        flachStich.korn = 0;
        float[] kurve = flachStich.punkte.kurve();
        boolean kurveSteigt = kurve[0] == 0 && kurve[Punkte.KURVE] > 0.95;
        for (int k = 1; k <= Punkte.KURVE; k++) kurveSteigt &= kurve[k] >= kurve[k - 1];
        double schlimmstTon = 0;
        StringBuilder toene = new StringBuilder();
        float[] hellFlach = new float[960 * 640];
        Arrays.fill(hellFlach, 0.5f);
        for (double t : new double[]{0.03, 0.1, 0.3, 0.6}) {
            float[] tk = new float[Stich.FAECHER + 2];
            Arrays.fill(tk, (float) t);
            double p = papierAnteil(flachStich, flachStich.stechen(hellFlach, 960, 640, 960, tk).bild);
            schlimmstTon = Math.max(schlimmstTon, Math.abs(p / t - 1));
            toene.append(String.format(Locale.GERMAN, " %.0f→%.1f", 100 * t, 100 * p));
        }
        check(kurveSteigt && schlimmstTon < 0.12, "Eichkurve steigt bis %.2f; Ton → Anteil Papier (%%):%s, höchstens %.0f %% daneben", kurve[Punkte.KURVE], toene, 100 * schlimmstTon);

        // Wolken: senkrecht länger als waagerecht; die Farbe bleibt im Mittel die des Drucks; gleicher seed, gleicher Druck
        Stich wst = new Stich();
        double[] aw = wolkenKorrelation(wst, 0.08);
        float[] dunkel = new float[960 * 640];
        float[] tk0 = new float[Stich.FAECHER + 2];
        BufferedImage nurFarbe = wst.stechen(dunkel, 960, 640, 960, tk0).bild;
        double[] mittel = mittelLab(nurFarbe);
        Stich.Ergebnis s1 = wst.stechen(hellFlach, 960, 640, 960), s2 = wst.stechen(hellFlach, 960, 640, 960);
        check(aw[1] > aw[0] + 0.3 && Lab.deltaE(mittel, wst.druck.farbe) < 0.6
                        && Arrays.equals(s1.bild.getRGB(0, 0, 960, s1.hoehe, null, 0, 960), s2.bild.getRGB(0, 0, 960, s2.hoehe, null, 0, 960)),
                "Wolken: Autokorrelation bei 8 %% der Breite waagerecht %.2f, senkrecht %.2f; Farbe im Mittel ΔE %.2f vom Druck; gleicher seed, gleicher Druck",
                aw[0], aw[1], Lab.deltaE(mittel, wst.druck.farbe));

        // Messung des Drucks an einem Druck bekannter Größe: flacher Ton 4 %, Punkte 2 px (ohne Wolken und Korn)
        Stich bekannt = new Stich();
        bekannt.wolken = 0;
        bekannt.korn = 0;
        bekannt.punkt = 2.0 / Druck.BREITE;
        float[] tk4 = new float[Stich.FAECHER + 2];
        Arrays.fill(tk4, 0.04f);
        Druck dk = Druck.messen(bekannt.stechen(new float[Druck.BREITE * 900], Druck.BREITE, 900, Druck.BREITE, tk4).bild);
        check(Math.abs(dk.punkt * Druck.BREITE / 2 - 1) < 0.1 && Math.abs(dk.tonMittel - 0.04) < 0.006 && Lab.deltaE(dk.papier, bekannt.druck.papier) < 1
                        && Lab.deltaE(dk.farbe, bekannt.druck.farbe) < 0.5 && dk.ordnung > 0.95 && dk.ordnung < 1.3,
                "Messung des Drucks: Punkte 2 px → %.2f px, Ton 4 %% → %.1f %%, Papier ΔE %.1f, Farbe ΔE %.1f, Ordnung R %.2f", dk.punkt * Druck.BREITE,
                100 * dk.tonMittel, Lab.deltaE(dk.papier, bekannt.druck.papier), Lab.deltaE(dk.farbe, bekannt.druck.farbe), dk.ordnung);

        // das Preset
        Druck sollD = Vorlagen.druck();
        boolean dform = sollD.ton.length == Druck.TON && sollD.wolkenX.length == Druck.LAG.length && sollD.punkt > 0 && sollD.punkt < 0.01;
        for (int q = 1; q < Druck.TON; q++) dform &= sollD.ton[q] >= sollD.ton[q - 1];
        check(dform && sollD.wolkenY[3] > sollD.wolkenX[3] + 0.3 && sollD.ordnung < 1.2 && Lab.farbton(sollD.farbe[1], sollD.farbe[2]) > 180,
                "Holzschnitt als Druck: Ton steigend, Wolken senkrecht länger (16 %%: %.2f gegen %.2f), Punkte fast zufällig (R %.2f), Farbe blau (%.0f°)",
                sollD.wolkenY[3], sollD.wolkenX[3], sollD.ordnung, Lab.farbton(sollD.farbe[1], sollD.farbe[2]));

        // Abnahme: das Licht des Dickichts (Prozessor, HIGH) als Holzschnitt, gemessen gegen den Holzschnitt
        Stich.Ergebnis se = new Stich().stechen(eh.licht, Druck.BREITE);
        Druck dist = Druck.messen(se.bild);
        abnahmeDruck("Holzschnitt (Prozessor)", dist, sollD, 0.15);
        Vorlage vh = new Messung().messen(se.bild), sollH = Vorlagen.holzschnitt();
        check(Math.abs(vh.l50 - sollH.l50) < 3 && Math.abs(vh.farbton - sollH.farbton) < 10,
                "Holzschnitt (Prozessor), Messung wie Phase 1: Median L* %.1f (Soll %.1f ± 3), Farbton %.0f° (Soll %.0f° ± 10)", vh.l50, sollH.l50, vh.farbton, sollH.farbton);

        // ------------------------------------------------------------------ Phase 5: Szene von außen

        // Richtungen eines Blatts: Normale und Längsachse Einheit und senkrecht
        Quelle.Blatt rb = new Quelle.Blatt().richtung(0.3, 2, -0.4, 1, 0.7, 0.2);
        double rn = Math.sqrt(rb.nx * rb.nx + rb.ny * rb.ny + rb.nz * rb.nz), ra = Math.sqrt(rb.ax * rb.ax + rb.ay * rb.ay + rb.az * rb.az),
                rd = rb.nx * rb.ax + rb.ny * rb.ay + rb.nz * rb.az;
        // Halm: Fuß und Spitze genau, die Mitte um Biegung · Länge über der Geraden
        Quelle.Sammlung qs = new Quelle.Sammlung();
        Quelle.Halm qh = qs.halm();
        qh.fx = 1; qh.fy = 0; qh.fz = 2; qh.sx = 1.3f; qh.sy = 0.4f; qh.sz = 2; qh.breite = 0.004f; qh.biegung = 0.1f;
        Dickicht.Halm dh = Dickicht.aus(qs).halmListe.get(0);
        double mx = dh.p[6] - 1.15, my = dh.p[7] - 0.2, mz = dh.p[8] - 2;   // Mitte des Bogens gegen Mitte der Geraden
        double quer = Math.sqrt(mx * mx + my * my + mz * mz), entlang = (mx * 0.3 + my * 0.4) / 0.5;
        check(Math.abs(rn - 1) < 1e-6 && Math.abs(ra - 1) < 1e-6 && Math.abs(rd) < 1e-6 && dh.p[0] == 1 && dh.p[2] == 2 && dh.p[12] == 1.3f
                        && dh.p[13] == 0.4f && Math.abs(quer - 0.05) < 1e-6 && Math.abs(entlang) < 1e-6 && my > 0,
                "Quelle: Blatt mit Normale ⟂ Längsachse; Halm vom Fuß zur Spitze, die Mitte %.4f m über der Geraden (Biegung 0,1 · 0,5 m), nach oben", quer);

        // Zweige: höchstens 255 Blätter, keine zwei Gruppen, nicht weiter als 1 m; jedes Blatt einmal und genau so
        Quelle.Sammlung qz = new Quelle.Sammlung();
        SplittableRandom zr = new SplittableRandom(11);
        for (int i = 0; i < 2000; i++) {
            Quelle.Blatt zb = qz.blatt().richtung(zr.nextDouble(-1, 1), 1, zr.nextDouble(-1, 1), 1, 0, 0);
            zb.x = (float) (i / 300 + zr.nextDouble(0.3)); zb.y = 1 + (float) zr.nextDouble(0.5); zb.z = 5 + (float) zr.nextDouble(i % 7 == 0 ? 3 : 0.5);
            zb.laenge = 0.05f + (float) zr.nextDouble(0.05); zb.breite = 0.6f * zb.laenge;
            zb.r = (float) zr.nextDouble(0.2); zb.g = 0.1f; zb.b = 0.05f;
            zb.gruppe = i / 300;
            zb.liegt = i >= 1900;
        }
        Dickicht dz = Dickicht.aus(qz);
        boolean zweigeOk = dz.blattZahl() == 2000;
        int naechstes = 0;
        Dickicht.Blatt db = new Dickicht.Blatt();
        Quelle.Blatt qb = new Quelle.Blatt();
        for (Dickicht.Zweig z : dz.zweige) {
            zweigeOk &= z.blaetter <= Dickicht.JE_ZWEIG && z.erstes == naechstes && z.seed == z.erstes && z.platz % 4 == 0;
            for (int i = 0; i < z.blaetter; i++) {
                qz.blatt(z.erstes + i, qb);
                z.blatt(i, db);
                zweigeOk &= db.x == qb.x && db.y == qb.y && db.z == qb.z && db.nx == qb.nx && db.az == qb.az && db.laenge == qb.laenge
                        && db.breite == qb.breite && db.r == qb.r && db.zufall == z.zufall(i);
                zweigeOk &= qb.gruppe == qz.blattListe.get(z.erstes).gruppe && qb.liegt == z.boden;
                zweigeOk &= Dickicht.abstandFuerPruefung(dz, z.erstes, z.erstes + i) <= Dickicht.ZWEIG_WEITE;
            }
            naechstes += z.blaetter;
        }
        check(zweigeOk && naechstes == 2000, "Quelle: 2.000 Blätter in %d Zweigen, jeder höchstens 255 Blätter einer Gruppe in 1 m, Blätter genau wie gegeben", dz.zweige.size());

        // Büschel: Zweige aus seed mit eigener Farbe und Neigung; mehr als 255 Blätter auf mehrere Zweige
        Quelle.Sammlung qbs = new Quelle.Sammlung();
        Quelle.Bueschel bu = qbs.bueschel();
        bu.x = 1; bu.y = 2; bu.z = 6; bu.radius = 0.3f; bu.blaetter = 600; bu.laenge = 0.08f; bu.schmal = 0.5f; bu.neigung = 5;
        bu.r = 0.2f; bu.g = 0.05f; bu.b = 0.01f;
        Dickicht dbu = Dickicht.aus(qbs);
        boolean buOk = dbu.zweige.size() == 3 && dbu.blattZahl() == 600 && dbu.zweige.get(2).blaetter == 90;
        double steilst = 0;
        for (Dickicht.Zweig z : dbu.zweige)
            for (int i = 0; i < z.blaetter; i++) {
                z.blatt(i, db);
                buOk &= z.eigeneFarbe && db.r > db.g && Math.abs(db.x - 1) <= 0.3f + 1e-6f;
                steilst = Math.max(steilst, Math.toDegrees(Math.acos(db.ny)));
            }
        check(buOk && steilst < 5 * 4.5, "Büschel: 600 Blätter auf 3 Zweige (255, 255, 90), in der eigenen Farbe, um die Mitte, steilstes Blatt %.0f° (Neigung 5°)", steilst);

        // das ganze Dickicht als Büschel malt wie das Dickicht aus seed (ohne die trockenen Blätter der Kronen)
        Dickicht dbs = Dickicht.aus(dd.alsBueschel());
        Licht lbs = new Licht();
        lbs.schattenKante = 1536;
        lbs.vorbereiten(dbs);
        Malweise.Ergebnis ebs = mal.malen(dbs, lbs, new Kamera());
        Vorlage vb1 = new Messung().messen(e1.bild), vb2 = new Messung().messen(ebs.bild);
        check(dbs.blattZahl() == dd.blattZahl() && Math.abs(vb1.l50 - vb2.l50) < 1.5 && Math.abs(vb1.grund - vb2.grund) < 0.015
                        && Math.abs(vb2.hellD50 / vb1.hellD50 - 1) < 0.12 && Math.abs(vb2.dunkelD50 / vb1.dunkelD50 - 1) < 0.12,
                "Rundweg mit Büscheln: %,d Blätter in %,d Büscheln malen wie aus seed: Median %.1f / %.1f, Grund %.1f / %.1f %%, hell %.2f / %.2f %%, dunkel %.2f / %.2f %%",
                dbs.blattZahl(), dd.zweige.size(), vb1.l50, vb2.l50, 100 * vb1.grund, 100 * vb2.grund, 100 * vb1.hellD50, 100 * vb2.hellD50,
                100 * vb1.dunkelD50, 100 * vb2.dunkelD50);

        // Rundweg: ein kleines Dickicht Blatt für Blatt durch die Schnittstelle malt wie das Dickicht aus seed
        Dickicht dkl = QuelleDemo.klein(1), dq = Dickicht.aus(dkl.alsQuelle());
        Malweise mr = new Malweise();
        mr.quality = Malweise.Quality.MEDIUM;
        Malweise.Ergebnis er1 = mr.malen(dkl, new Licht().vorbereiten(dkl), new Kamera()), er2 = mr.malen(dq, new Licht().vorbereiten(dq), new Kamera());
        Vorlage vr1 = new Messung().messen(er1.bild), vr2 = new Messung().messen(er2.bild);
        check(dq.blattZahl() == dkl.blattZahl() && dq.glieder.size() == dkl.glieder.size() && dq.halmListe.size() == dkl.halmListe.size()
                        && Math.abs(vr1.l50 - vr2.l50) < 1 && Math.abs(vr1.grund - vr2.grund) < 0.01
                        && Math.abs(vr2.hellD50 / vr1.hellD50 - 1) < 0.1 && Math.abs(vr2.dunkelD50 / vr1.dunkelD50 - 1) < 0.1,
                "Rundweg: %,d Blätter aus seed und als Quelle malen gleich: Median %.1f / %.1f, Grund %.1f / %.1f %%, hell %.2f / %.2f %%, dunkel %.2f / %.2f %%",
                dkl.blattZahl(), vr1.l50, vr2.l50, 100 * vr1.grund, 100 * vr2.grund, 100 * vr1.hellD50, 100 * vr2.hellD50, 100 * vr1.dunkelD50, 100 * vr2.dunkelD50);
        Malweise.Ergebnis er3 = mr.malen(Dickicht.aus(dkl.alsQuelle()), new Licht().vorbereiten(dq), new Kamera());
        check(Arrays.equals(er2.bild.getRGB(0, 0, er2.breite, er2.hoehe, null, 0, er2.breite), er3.bild.getRGB(0, 0, er3.breite, er3.hoehe, null, 0, er3.breite)),
                "gleiche Quelle, gleiches Bild");

        // Huflattich von außen: die Kennzeichen von „Blätter“
        Huflattich huf = new Huflattich().bauen();
        Dickicht dhuf = Dickicht.aus(huf.alsQuelle());
        Licht lhuf = new Licht();
        lhuf.sonneHoehe = 45;
        lhuf.vorbereiten(dhuf);
        Malweise mhuf = new Malweise();
        mhuf.vorlage = Vorlagen.blaetter();
        mhuf.seitenverhaeltnis = 1465 / 1000.0;
        Vorlage vh5 = new Messung().messen(mhuf.malen(dhuf, lhuf, huf.kamera()).bild);
        abnahmeHuflattich("Huflattich (Prozessor)", vh5, Vorlagen.blaetter(), ist.hellD50, GertschCheck::check);
        gelaende(huf, mhuf, vh5);

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
            if (g.name.equals("holzschnitt")) {
                Druck dn = Druck.messen(javax.imageio.ImageIO.read(datei));
                check(passt(Vorlagen.druck(), dn), "Holzschnitt als Druck: gespeicherte Kennwerte gleich der neuen Messung von %s in voller Auflösung", g.quelle);
            }
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

    /**
     * Abnahme eines Drucks gegen den Holzschnitt (Toleranzen siehe README, Phase 4). {@code lag}: Toleranz der
     * Autokorrelation bei 2 und 4 % der Breite.
     */
    static void abnahmeDruck(String name, Druck ist, Druck soll, double lag) { abnahmeDruck(name, ist, soll, lag, GertschCheck::check); }

    /** Eine Prüfung: Bedingung, Text mit Werten (für GertschGlCheck). */
    interface Pruefer { void check(boolean c, String what, Object... args); }

    static void abnahmeDruck(String name, Druck ist, Druck soll, double lag, Pruefer p) {
        double rp = ist.punkt / soll.punkt, ra = ist.abstand / soll.abstand;
        p.check(Lab.deltaE(ist.papier, soll.papier) < 2 && Lab.deltaE(ist.farbe, soll.farbe) < 2, "%s: Papier ΔE %.1f, Farbe ΔE %.1f (höchstens 2)", name,
                Lab.deltaE(ist.papier, soll.papier), Lab.deltaE(ist.farbe, soll.farbe));
        p.check(rp > 0.88 && rp < 1.12 && ra > 0.8 && ra < 1.2 && Math.abs(ist.ordnung - soll.ordnung) < 0.15,
                "%s, Punkte: %.2f px (Soll %.2f ± 12 %%), nächster %.1f px (Soll %.1f ± 20 %%), Ordnung R %.2f (Soll %.2f ± 0,15)", name,
                ist.punkt * Druck.BREITE, soll.punkt * Druck.BREITE, ist.abstand * Druck.BREITE, soll.abstand * Druck.BREITE, ist.ordnung, soll.ordnung);
        p.check(Math.abs(ist.tonMittel - soll.tonMittel) < 0.015 && Math.abs(ist.ton[10] - soll.ton[10]) < 0.02 && Math.abs(ist.ton[19] - soll.ton[19]) < 0.05,
                "%s, Ton: Mittel %.1f %% (Soll %.1f ± 1,5), Median %.1f %% (Soll %.1f ± 2), 95 %% %.1f %% (Soll %.1f ± 5)", name,
                100 * ist.tonMittel, 100 * soll.tonMittel, 100 * ist.ton[10], 100 * soll.ton[10], 100 * ist.ton[19], 100 * soll.ton[19]);
        boolean nah = Math.abs(ist.wolken - soll.wolken) < 0.6;
        for (int l = 0; l < 2; l++) nah &= Math.abs(ist.wolkenX[l] - soll.wolkenX[l]) < lag && Math.abs(ist.wolkenY[l] - soll.wolkenY[l]) < lag;
        double quer = ist.wolkenY[2] + ist.wolkenY[3] - ist.wolkenX[2] - ist.wolkenX[3];
        p.check(nah && quer > 0.5, "%s, Wolken: Streuung L* %.2f (Soll %.2f ± 0,6); bei 2 und 4 %%: waagerecht %.2f/%.2f (Soll %.2f/%.2f), senkrecht %.2f/%.2f (Soll %.2f/%.2f); "
                        + "senkrecht länger (8 und 16 %%: %+.2f, Soll %+.2f)", name, ist.wolken, soll.wolken, ist.wolkenX[0], ist.wolkenX[1], soll.wolkenX[0], soll.wolkenX[1],
                ist.wolkenY[0], ist.wolkenY[1], soll.wolkenY[0], soll.wolkenY[1], quer, soll.wolkenY[2] + soll.wolkenY[3] - soll.wolkenX[2] - soll.wolkenX[3]);
        p.check(Math.abs(ist.korn - soll.korn) < 0.25, "%s, Korn: L* %.2f (Soll %.2f ± 0,25)", name, ist.korn, soll.korn);
    }

    /**
     * Kennzeichen der Vorlage „Blätter“ an einer Szene von außen (Toleranzen siehe README, Phase 5). {@code hellSommer}:
     * die hellen Flecken des Dickichts im Sommer; die großen Blätter müssen deutlich größere Flecken geben.
     */
    static void abnahmeHuflattich(String name, Vorlage ist, Vorlage soll, double hellSommer, Pruefer p) {
        double rd = ist.dunkelD50 / soll.dunkelD50;
        p.check(Math.abs(ist.grund - soll.grund) < 0.06 && Math.abs(ist.l50 - soll.l50) < 7 && Math.abs(ist.farbton - soll.farbton) < 8,
                "%s, Helligkeit und Farbe: Grund %.1f %% (Soll %.1f ± 6), Median %.1f (Soll %.1f ± 7), Farbton %.0f° (Soll %.0f° ± 8)", name,
                100 * ist.grund, 100 * soll.grund, ist.l50, soll.l50, ist.farbton, soll.farbton);
        p.check(ist.akzent > 0.01 && ist.akzent < 0.035 && rd > 0.7 && rd < 1.5 && ist.hellD50 > 1.5 * hellSommer && Math.abs(ist.senkrecht - soll.senkrecht) < 0.1,
                "%s, Formen: Akzente %.1f %% (Soll %.1f, 1 bis 3,5), dunkle Lücken %.2f %% (Soll %.2f, × 0,7 bis 1,5), helle Flecken %.2f %% (über 1,5 × Sommer %.2f), senkrecht %.0f %% (Soll %.0f ± 10)",
                name, 100 * ist.akzent, 100 * soll.akzent, 100 * ist.dunkelD50, 100 * soll.dunkelD50, 100 * ist.hellD50, 100 * hellSommer, 100 * ist.senkrecht, 100 * soll.senkrecht);
    }

    /** Clark–Evans auf dem Torus für die Zellen mit Rang unter f (Mitten der Zellen). */
    static double ordnungTorus(float[] rang, double f) {
        int n = Punkte.N;
        java.util.List<int[]> p = new java.util.ArrayList<>();
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) if (rang[j * n + i] < f) p.add(new int[]{i, j});
        double summe = 0;
        int reich = 8;
        boolean[] belegt = new boolean[n * n];
        for (int[] q : p) belegt[q[1] * n + q[0]] = true;
        for (int[] q : p) {
            double best = Double.MAX_VALUE;
            for (int dy = -reich; dy <= reich; dy++)
                for (int dx = -reich; dx <= reich; dx++) {
                    if ((dx == 0 && dy == 0) || !belegt[Math.floorMod(q[1] + dy, n) * n + Math.floorMod(q[0] + dx, n)]) continue;
                    best = Math.min(best, Math.hypot(dx, dy));
                }
            summe += best;
        }
        double dichte = p.size() / (double) (n * n);
        return summe / p.size() / (0.5 / Math.sqrt(dichte));
    }

    /** Anteil Papier eines Drucks aus der Helligkeit (Farbe ohne Wolken und Korn, also überall gleich). */
    static double papierAnteil(Stich st, BufferedImage b) {
        double yp = yVon(st.druck.papier), yf = yVon(st.druck.farbe), s = 0;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = b.getRGB(0, 0, w, h, null, 0, w);
        for (int c : px) s += (0.2126 * Lab.linear(c >> 16) + 0.7152 * Lab.linear(c >> 8) + 0.0722 * Lab.linear(c) - yf) / (yp - yf);
        return s / px.length;
    }

    static double yVon(double[] lab) {
        double[] o = new double[3];
        Lab.zuLinear(lab[0], lab[1], lab[2], o);
        return 0.2126 * o[0] + 0.7152 * o[1] + 0.0722 * o[2];
    }

    /** Mittlere Farbe (im linearen Licht) eines Bilds in Lab. */
    static double[] mittelLab(BufferedImage b) {
        double r = 0, g = 0, bl = 0;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = b.getRGB(0, 0, w, h, null, 0, w);
        for (int c : px) { r += Lab.linear(c >> 16); g += Lab.linear(c >> 8); bl += Lab.linear(c); }
        double[] o = new double[3];
        Lab.vonLinear(r / px.length, g / px.length, bl / px.length, o);
        return o;
    }

    /** Autokorrelation der Wolken (das Rauschen selbst) waagerecht und senkrecht beim Abstand lag (Anteil der Breite). */
    static double[] wolkenKorrelation(Stich st, double lag) {
        int n = 400;
        double[] v = new double[n * n];
        double mu = 0;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) mu += v[j * n + i] = st.wolken((float) (i / (double) n), (float) (j / (double) n));
        mu /= v.length;
        double var = 0, sx = 0, sy = 0;
        int l = (int) Math.round(lag * n), nx = 0, ny = 0;
        for (double x : v) var += (x - mu) * (x - mu);
        var /= v.length;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double d = v[j * n + i] - mu;
                if (i + l < n) { sx += d * (v[j * n + i + l] - mu); nx++; }
                if (j + l < n) { sy += d * (v[(j + l) * n + i] - mu); ny++; }
            }
        return new double[]{sx / nx / var, sy / ny / var};
    }

    /** Druck: gespeichert und neu gemessen gleich bis auf die Rundung im Quelltext. */
    static boolean passt(Druck g, Druck n) {
        boolean ok = Lab.deltaE(g.papier, n.papier) < 0.02 && Lab.deltaE(g.farbe, n.farbe) < 0.02 && Math.abs(g.punkt - n.punkt) < 1e-6
                && Math.abs(g.abstand - n.abstand) < 1e-6 && Math.abs(g.ordnung - n.ordnung) < 1e-4 && g.punkte == n.punkte
                && Math.abs(g.tonMittel - n.tonMittel) < 1e-4 && Math.abs(g.wolken - n.wolken) < 1e-3 && Math.abs(g.korn - n.korn) < 1e-3;
        for (int q = 0; q < Druck.TON; q++) ok &= Math.abs(g.ton[q] - n.ton[q]) < 1e-4;
        for (int l = 0; l < Druck.LAG.length; l++) ok &= Math.abs(g.wolkenX[l] - n.wolkenX[l]) < 1e-3 && Math.abs(g.wolkenY[l] - n.wolkenY[l]) < 1e-3;
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

    /**
     * Seit LituyaBay1958 W10: das Gelände. Auf einer schiefen Ebene sind Höhe, Normale und Strahl genau; ein ebenes
     * Gelände malt wie die Ebene ohne Gelände; {@link Quelle#aufGelaende} hebt jedes Element genau um die Höhe darunter.
     */
    static void gelaende(Huflattich huf, Malweise mhuf, Vorlage ohne) {
        com.dan.gertsch.Gelaende schief = com.dan.gertsch.Gelaende.aus(-10, -5, 10, 20, 0.5, (x, z) -> 0.2 * x + 0.1 * z + 1);
        double dh = 0, dn = 0, nl = Math.sqrt(0.04 + 1 + 0.01);
        double[] nn = new double[3];
        java.util.Random zr = new java.util.Random(7);
        for (int i = 0; i < 1000; i++) {
            double x = -9 + 18 * zr.nextDouble(), z = -4 + 23 * zr.nextDouble();
            dh = Math.max(dh, Math.abs(schief.hoehe(x, z) - (0.2 * x + 0.1 * z + 1)));
            schief.normale(x, z, nn);
            dn = Math.max(dn, Math.abs(nn[0] + 0.2 / nl) + Math.abs(nn[1] - 1 / nl) + Math.abs(nn[2] + 0.1 / nl));
        }
        double[] auge = {0, 6, -3}, r = {0.3, -1, 0.5};
        double rl = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        for (int i = 0; i < 3; i++) r[i] /= rl;
        double t = schief.strahl(auge, r, 100), tSoll = (auge[1] - 0.2 * auge[0] - 0.1 * auge[2] - 1) / (0.2 * r[0] + 0.1 * r[2] - r[1]);
        check(dh < 1e-4 && dn < 1e-4 && Math.abs(t - tSoll) < 1e-3, "Gelände: schiefe Ebene bilinear genau (%.1g m), Normale (%.1g), Strahl trifft auf %.1g m",
                dh, dn, Math.abs(t - tSoll));

        // ein ebenes Gelände malt wie die Ebene ohne Gelände
        com.dan.gertsch.Gelaende eben = com.dan.gertsch.Gelaende.aus(-3, -1, 3, 5, 0.25, (x, z) -> 0);
        Dickicht de = Dickicht.aus(Quelle.aufGelaende(huf.alsQuelle(), eben));
        Licht le = new Licht();
        le.sonneHoehe = 45;
        le.vorbereiten(de);
        Malweise.Ergebnis ee = mhuf.malen(de, le, huf.kamera());
        Vorlage ve = new Messung().messen(ee.bild);
        check(Math.abs(ve.l50 - ohne.l50) < 0.5 && Math.abs(ve.grund - ohne.grund) < 0.005 && Math.abs(ve.hellD50 / ohne.hellD50 - 1) < 0.05,
                "Gelände eben wie die Ebene (Prozessor): Median %.1f / %.1f, Grund %.1f / %.1f %%, hell %.2f / %.2f %%",
                ve.l50, ohne.l50, 100 * ve.grund, 100 * ohne.grund, 100 * ve.hellD50, 100 * ohne.hellD50);

        // auf den Hang gestellt: jedes Element genau um die Höhe darunter
        com.dan.gertsch.Gelaende hang = huf.hang(0.35);
        Quelle q = huf.alsQuelle(), qa = Quelle.aufGelaende(q, hang);
        Quelle.Blatt b = new Quelle.Blatt(), ba = new Quelle.Blatt();
        Quelle.Halm h = new Quelle.Halm(), ha = new Quelle.Halm();
        double fehler = 0;
        for (int i = 0; i < q.blaetter(); i++) {
            q.blatt(i, b);
            qa.blatt(i, ba);
            fehler = Math.max(fehler, Math.abs(ba.y - b.y - hang.hoehe(b.x, b.z)) + Math.abs(ba.x - b.x) + Math.abs(ba.z - b.z));
        }
        for (int i = 0; i < q.halme(); i++) {
            q.halm(i, h);
            qa.halm(i, ha);
            float dy = hang.hoehe(h.fx, h.fz);
            fehler = Math.max(fehler, Math.abs(ha.fy - h.fy - dy) + Math.abs(ha.sy - h.sy - dy));
        }
        Dickicht dh2 = Dickicht.aus(qa);
        Licht lh = new Licht();
        lh.sonneHoehe = 45;
        lh.vorbereiten(dh2);
        Malweise.Ergebnis eh = mhuf.malen(dh2, lh, huf.kamera(hang));
        int boden = 0;
        for (int id : eh.id) if (id == -1) boden++;
        check(fehler < 1e-5 && dh2.gelaende == hang && boden > 0, "Gelände am Hang: %,d Blätter und %,d Halme genau gehoben (%.1g m), %.1f %% Boden im Bild",
                q.blaetter(), q.halme(), fehler, 100.0 * boden / eh.id.length);
    }

    static void check(boolean c, String what, Object... args) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + String.format(Locale.GERMAN, what, args));
    }

    private GertschCheck() { }
}
