package com.dan.render.terrain.demo;

import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.render.terrain.Gelaendebild;
import com.dan.render.terrain.Material;
import com.dan.render.terrain.clipmap.Clipmap;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung von render.terrain ohne Fenster (P6b, Werkbuch HalligHooge Teil A, 12.7, Prüfsteine 2 und 3). Teile:
 * 1 Material (Albedo aus dem Luftbild), 2 Gelände gleich Physik in Java (jeder Punkt jeder Stufe gegen
 * {@code Gelaende.hoehe}, Naht), 3 auf der Karte (Daten bitgleich, Scheitel gegen Java, auch beim Gehen und nach einem
 * Sprung), 4 dicht (kein Himmel unter der Kimm, kein NaN) in drei Ansichten, 5 Licht (Himmelslicht, Nässe), 6 Aufräumen.
 * Der Himmel ist hier ein Ersatz mit bekannten Werten (render.terrain kennt render.air nicht): Luft ohne Dämpfung,
 * Himmel gleichmäßig hell. {@code --gebiet <ordner>} (Vorgabe {@code D:\Daten\HalligHooge\gebiet}). Endet mit „Alles
 * in Ordnung.“ und schreibt das Attest nach {@code docs/status/P6_Gelaende.md}.
 */
public final class TerrainCheck {
    private static final Attest A = new Attest("com.dan.render.terrain", Gelaendebild.VERSION);
    /** Auge auf der Hanswarft (UTM 32). */
    static final double OST = 470_900, NORD = 6_046_615;
    static final double L_HIMMEL = 4000;          // Leuchtdichte des Ersatzhimmels (cd/m²)

    public static void main(String[] args) throws Exception {
        boolean attest = !List.of(args).contains("--ohne-attest");
        String gebiet = "D:\\Daten\\HalligHooge\\gebiet";
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--gebiet")) gebiet = args[i + 1];
        System.out.println("TerrainCheck – com.dan.render.terrain " + Gelaendebild.VERSION + " (Werkbuch HalligHooge Teil A, P6b)");
        File gb = new File(gebiet);
        A.info("Gebiet", gb.getPath());
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("TerrainCheck", 512L << 20);
            pruefen(g, konto, gb);
            A.teil("6 Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                String erste = g.meldungen.isEmpty() ? "" : "; die erste Meldung: " + g.meldungen.get(0).replace('\n', ' ');
                A.pruefe(g.validierungsFehler == 0, "Validierungsschicht " + g.schicht, g.validierungsFehler + " Fehler" + erste, "0 Fehler");
            } else A.info("Validierungsschicht", "nicht eingeschaltet (-Dcore.gpu.validierung=true)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P6_Gelaende.md"), "P6b – TerrainCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Ersatz für den Himmel: Bilder und Werte unter den Namen von render.air. */
    static final class Ersatz implements AutoCloseable {
        final Bild durch, ansicht;
        final Puffer werte;
        final float[] w = new float[60];

        Ersatz(Geraet g, Konto k) {
            durch = new Bild(g, k, "Ersatz Durchlässigkeit", 256, 64, Bild.RGBA32F);
            float[] eins = new float[256 * 64 * 4];
            Arrays.fill(eins, 1f);
            durch.schreibe(eins);
            ansicht = new Bild(g, k, "Ersatz Ansicht", 256, 128, Bild.RGBA32F);
            werte = Puffer.speicher(g, k, "Ersatz Werte", 60 * 4);
            himmel(L_HIMMEL);
        }

        void himmel(double l) {
            float[] a = new float[256 * 128 * 4];
            for (int i = 0; i < 256 * 128; i++) {
                a[4 * i] = a[4 * i + 1] = a[4 * i + 2] = (float) l;
                a[4 * i + 3] = 1;
            }
            ansicht.schreibe(a);
        }

        /** Sonne aus Höhe und Azimut (Grad, im Gitter), E (lx), Vorbelichtung, Augenhöhe. */
        void sonne(double hGrad, double azGrad, double e, double vb, double auge) {
            double h = Math.toRadians(hGrad), az = Math.toRadians(azGrad);
            Arrays.fill(w, 0);
            w[0] = 1e-5f; w[1] = 1e-5f; w[2] = 0.08f; w[3] = 0.85f;
            w[4] = (float) (Math.cos(h) * Math.sin(az)); w[5] = (float) Math.sin(h); w[6] = (float) (-Math.cos(h) * Math.cos(az)); w[7] = (float) Math.max(1, auge);
            w[8] = w[9] = w[10] = (float) e; w[11] = (float) vb;
            w[12] = 0; w[13] = -1; w[14] = 0;
            w[52] = w[53] = w[54] = w[56] = w[57] = w[58] = 1;                                         // Farbe der Sonne: weiß (die Prüfung gibt E je Kanal vor)
            werte.schreibe(w);
        }

        void ablegen(Bildziel z) {
            z.geteilt.put("Himmel/Durchlaessigkeit", durch);
            z.geteilt.put("Himmel/Ansicht", ansicht);
            z.puffer.put("Himmel/Werte", werte);
        }

        @Override public void close() {
            durch.close();
            ansicht.close();
            werte.close();
        }
    }

    static void bild(Geraet g, Gelaendebild t, Bildziel z) {
        Ablauf a = new Ablauf(g, g.grafik);
        a.zeichne("löschen", new Bild[] {z.hdr}, new float[][] {{0, 0, 0, 0}}, z.tiefe, 0f, null, c -> { });
        t.vorbereiten(a, z);
        t.zeichnen(a, z);
        g.grafik.einmal(c -> a.aufnehmen(c, null));
    }

    static void pruefen(Geraet g, Konto konto, File gebiet) throws Exception {
        // ---------------------------------------------------------------- 1 Material
        A.teil("1 Material");
        double[][] alb = Material.albedo(new File(gebiet, "luftbild/albedo.txt"));
        String[] namen = {"", "Watt", "Sand", "Fenne", "Salzwiese", "Deich", "Warft", "Wasser", "Deckwerk"};
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        for (int q = 1; q < alb.length; q++) {
            if (Double.isNaN(alb[q][0])) continue;
            sb.append(f("%s %.3f/%.3f/%.3f; ", namen[q], alb[q][0], alb[q][1], alb[q][2]));
            for (double v : alb[q]) ok &= v > 0.02 && v < 0.5;
        }
        A.info("Albedo trocken (linear, aus DOP20 geeicht gegen Sentinel-2)", sb.toString());
        A.pruefe(ok, "Albedo aller Klassen im Bereich natürlicher Böden", "siehe oben", "0,02 … 0,5 [G]");

        try (Gelaendebild t = Gelaendebild.aus(g, konto, gebiet); Ersatz ers = new Ersatz(g, konto)) {
            t.flugZeit = 0;                    // seit P12b: feste Zeit, sonst kräuselt das Wasser der Gestalt zwischen zwei Bildern
            Gelaende gel = t.gelaende;
            A.info("Stapel", gel.beschreibe());
            Clipmap hc = t.hoehe;
            A.info("Clipmap", f("%d Stufen à %d², %.0f m bis %.0f m, reicht %.0f km; Ring %d²", hc.levels(), hc.grid, hc.cell(0), hc.cell(hc.levels() - 1),
                    hc.half() * hc.cell(hc.levels() - 1) / 1000, hc.texSize));
            float boden = gel.hoehe(OST, NORD, 1);
            double augeH = boden + 1.7;
            A.info("Auge", f("Hanswarft O %.0f N %.0f, Boden %.2f m NHN, Auge %.2f m NHN", OST, NORD, boden, augeH));

            // ------------------------------------------------------------ 2 Java
            A.teil("2 Gelände gleich Physik (Java)");
            Blick b = new Blick(OST, NORD, augeH, 270, 0, 50).groesse(480, 270);
            double[] xz = t.raum(b);
            t.ziel(xz[0], xz[1]);
            long t0 = System.nanoTime();
            t.ladeJetzt();
            A.info("Laden aller fünf Clipmaps", f("%.1f s (Höhe und Material, alle Stufen)", (System.nanoTime() - t0) / 1e9));
            long[] jp = java(t);
            A.pruefe(jp[1] == 0, "Prüfstein 2: Höhe jedes Punkts jeder Stufe gleich Gelaende.hoehe am selben Ort mit derselben Zellweite",
                    f("%d Punkte, %d anders", jp[0], jp[1]), "bitgleich");
            double[] naht = naht(t, null, null);
            A.pruefe(naht[1] <= 1e-5 && naht[2] == 0, "Prüfstein 2, Naht (Java): jeder Randscheitel auf der Kante der gröberen Stufe, Rand auf ihren Gitterlinien",
                    f("%.0f Randscheitel, größte Abweichung %.2e m, %.0f Ränder nicht auf Gitterlinien", naht[0], naht[1], naht[2]), "≤ 1e-5 m [G], 0");

            // ------------------------------------------------------------ 3 Karte
            A.teil("3 Auf der Karte");
            Bild hdr = new Bild(g, konto, "HDR", 480, 270, Bild.RGBA16F), tiefe = new Bild(g, konto, "Tiefe", 480, 270, Bild.D32F);
            Kernel ks = Kernel.lade(g, "/com/dan/render/terrain/demo/scheitel.comp", 0, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER,
                    Kernel.Bindung.PUFFER, Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER, Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER);
            Abtaster at = new Abtaster(g, false, false);
            int L = hc.levels(), M = hc.grid;
            Puffer aus = Puffer.speicher(g, konto, "Scheitel", 4L * L * M * M);
            try {
                Bildziel z = new Bildziel(hdr, tiefe);
                ers.ablegen(z);
                z.vorbelichtung = 1e-3;
                z.zeit = Instant.parse("2026-10-25T12:00:00Z");
                ers.sonne(25, 190, 80_000, z.vorbelichtung, augeH);
                double[] schlimmst = new double[4];
                long gesamt = 0;
                // Gehen und Springen
                double[][] weg = new double[13][];
                for (int i = 0; i < 10; i++) weg[i] = new double[] {OST + 37 * i, NORD - 23 * i};
                weg[10] = new double[] {OST + 5000, NORD - 3000};
                weg[11] = new double[] {OST + 5000 + 300, NORD - 3000 + 1};
                weg[12] = new double[] {OST, NORD};
                long hochMin = Long.MAX_VALUE, hochMax = 0;
                for (int s = 0; s < weg.length; s++) {
                    z.blick = new Blick(weg[s][0], weg[s][1], augeH, 270, 0, 50).groesse(480, 270);
                    bild(g, t, z);
                    if (s > 0) {
                        hochMin = Math.min(hochMin, t.hochgeladen);
                        hochMax = Math.max(hochMax, t.hochgeladen);
                    }
                    long[] kv = karteDaten(t);
                    gesamt += kv[0];
                    schlimmst[0] += kv[1];
                    double[] sv = karteScheitel(g, t, ks, at, aus, ers);
                    schlimmst[1] = Math.max(schlimmst[1], sv[0]);
                    double[] nk = naht(t, sv[1] > 0 ? letzte : null, z.blick);
                    schlimmst[2] = Math.max(schlimmst[2], nk[1]);
                    schlimmst[3] += nk[2];
                }
                A.info("Gehen und Springen", f("%d Bilder: 10 Schritte zu 44 m, ein Sprung um 5,8 km, 300 m, zurück; je Bild %d bis %d Werte hochgeladen", weg.length, hochMin, hochMax));
                A.pruefe(schlimmst[0] == 0, "Daten auf der Karte gleich den Ringen der fünf Clipmaps (nach jedem Bild)", f("%d Werte, %.0f anders", gesamt, schlimmst[0]), "bitgleich");
                A.pruefe(schlimmst[1] <= 1e-4, "Prüfstein 2: Höhe jedes Scheitels auf der Karte (mit Übergang) gegen Clipmap.vertexHeight",
                        f("größte Abweichung %.2e m (%d Scheitel je Bild)", schlimmst[1], (long) L * M * M), "±1e-4 m [G]");
                A.pruefe(schlimmst[2] <= 1e-4 && schlimmst[3] == 0, "Prüfstein 2, Naht auf der Karte: Randscheitel auf der Kante der gröberen Stufe",
                        f("größte Abweichung %.2e m", schlimmst[2]), "±1e-4 m [G]");

                // ------------------------------------------------------------ 4 dicht
                A.teil("4 Dicht");
                Object[][] ansichten = {
                        {"Hanswarft nach Westen", new Blick(OST, NORD, augeH, 270, 0, 50)},
                        {"Hanswarft nach Osten, Sonne im Rücken", new Blick(OST, NORD, augeH, 90, -2, 50)},
                        {"Vogelblick aus 300 m", new Blick(OST - 500, NORD - 1500, 300, 20, -30, 60)}};
                for (Object[] v : ansichten) {
                    Blick bv = ((Blick) v[1]).groesse(480, 270);
                    z.blick = bv;
                    bild(g, t, z);
                    long[] d = dicht(bv, hdr, tiefe, t);
                    A.pruefe(d[0] == 0 && d[1] == 0, "Prüfstein 3: " + v[0] + " – Himmel unter der Kimm; NaN oder unendlich",
                            f("%d Bildpunkte; %d (Gelände auf %d von %d Bildpunkten; %d von %d Blöcken gezeichnet, %d außer Sicht, %d bedeckt)", d[0], d[1], d[2], 480 * 270,
                                    t.bloecke, t.bloecke + t.bloeckeAusser + t.bloeckeVerdeckt, t.bloeckeAusser, t.bloeckeVerdeckt), "0; 0");
                }

                // ------------------------------------------------------------ 5 Licht
                A.teil("5 Licht");
                z.blick = new Blick(OST, NORD, augeH, 270, -5, 50).groesse(480, 270);
                bild(g, t, z);
                float[] li = t.licht().floats(8);
                double soll = Math.PI * L_HIMMEL, dl = 0;
                for (int c = 0; c < 3; c++) dl = Math.max(dl, Math.max(Math.abs(li[c] - soll), Math.abs(li[4 + c] - soll)) / soll);
                A.pruefe(dl <= 1e-3, "Himmelslicht auf die waagerechte Fläche bei gleichmäßigem Himmel: π · L oben und unten",
                        f("%.1f / %.1f lx gegen %.1f lx (± %.1e)", li[1], li[5], soll, dl), "relativ ≤ 1e-3 [G]");
                // Nässe: alles nass gegen alles trocken; auf reinem Watt wird die Albedo um 0,45 dunkler
                ers.himmel(0);
                ers.sonne(90, 0, 80_000, z.vorbelichtung, augeH);
                bild(g, t, z);
                float[] trocken = hdr.lies();
                Gitter gg = Gitter.utm("Nässe", 32, OST - 3000, NORD - 3000, 20, 300, 300);
                float[] age = new float[300 * 300];
                t.naesse(gg, age);
                bild(g, t, z);
                float[] nass = hdr.lies();
                double[] r = new double[480 * 270];
                int n = 0;
                for (int i = 0; i < 480 * 270; i++)
                    if (trocken[4 * i + 1] > 0 && nass[4 * i + 1] < trocken[4 * i + 1] * 0.999) r[n++] = nass[4 * i + 1] / trocken[4 * i + 1];
                Arrays.sort(r, 0, n);
                double minR = n > 0 ? r[0] : 1, med = n > 0 ? r[n / 2] : 1;
                A.pruefe(n > 0 && Math.abs(minR - (1 - t.dunkel)) <= 0.01, "Nässe 1 (water.wetAge = 0) auf reinem Watt: Albedo mal (1 − 0,45)",
                        f("%d Bildpunkte dunkler, kleinstes Verhältnis %.3f, Median %.3f", n, minR, med), "0,55 ± 0,01 [G]");
                Arrays.fill(age, (float) t.halbwert);
                t.naesse(gg, age);
                bild(g, t, z);
                float[] halb = hdr.lies();
                n = 0;
                for (int i = 0; i < 480 * 270; i++)
                    if (trocken[4 * i + 1] > 0 && nass[4 * i + 1] < trocken[4 * i + 1] * 0.999) r[n++] = halb[4 * i + 1] / trocken[4 * i + 1];
                Arrays.sort(r, 0, n);
                double erw = 1 - t.dunkel * (t.rest + (1 - t.rest) * 0.5);
                A.pruefe(n > 0 && Math.abs(r[0] - erw) <= 0.01, "Nässe nach einer Halbwertszeit (2 h): Restnässe + halber Rest",
                        f("kleinstes Verhältnis %.3f", n > 0 ? r[0] : 1), f("%.3f ± 0,01 [G]", erw));
                t.naesse(null, null);
                // Schatten bei tiefer Sonne
                ers.sonne(3, 250, 80_000, z.vorbelichtung, augeH);
                bild(g, t, z);
                float[] sv = t.sicht().floats(hc.texSize * hc.texSize * Math.min(6, hc.levels()));
                long im = 0, schatten = 0;
                for (float v : sv) { im++; if (v < 0.5f) schatten++; }
                A.info("Schatten bei 3° Sonne aus WSW", f("%.1f %% der Ringplätze der Stufen 0–5 im Schatten", 100.0 * schatten / im));
            } finally {
                aus.close();
                ks.close();
                at.close();
                hdr.close();
                tiefe.close();
            }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    /** Prüfstein 2 in Java: {Punkte, anders}. */
    static long[] java(Gelaendebild t) {
        Clipmap c = t.hoehe;
        long n = 0, falsch = 0;
        int h = c.half();
        for (Clipmap.Window w : c.windows())
            for (int j = w.cj() - h; j <= w.cj() + h; j++)
                for (int i = w.ci() - h; i <= w.ci() + h; i++) {
                    double x = c.originX + i * w.cell(), z = c.originZ + j * w.cell();
                    float soll = t.gelaende.hoehe(t.e0 + x, t.n0 - z, w.cell());
                    if (Float.isNaN(soll)) soll = c.noData;
                    float ist = c.gridHeight(w.level(), i, j);
                    n++;
                    if (Float.floatToIntBits(soll) != Float.floatToIntBits(ist)) falsch++;
                }
        return new long[] {n, falsch};
    }

    /** Werte des letzten Laufs von scheitel.comp (für die Naht auf der Karte). */
    static float[] letzte;

    /**
     * Naht: Randscheitel einer Stufe (k oder l = 0 oder M − 1) gegen die Kante der gröberen Stufe; mit {@code karte}
     * die Höhen der Karte statt Clipmap.vertexHeight. {Randscheitel, größte Abweichung, Ränder nicht auf Gitterlinien}.
     */
    static double[] naht(Gelaendebild t, float[] karte, Blick b) {
        Clipmap c = t.hoehe;
        int M = c.grid, h = c.half(), L = c.levels();
        Clipmap.Window[] w = c.windows();
        double n = 0, max = 0, schief = 0;
        for (int l = 0; l + 1 < L; l++) {
            int i0 = w[l].ci() - h, j0 = w[l].cj() - h;
            if ((i0 & 1) != 0 || (j0 & 1) != 0) schief++;
            for (int s = 0; s < M; s++)
                for (int e = 0; e < 4; e++) {
                    int k = e == 0 ? 0 : e == 1 ? M - 1 : s, q = e < 2 ? s : e == 2 ? 0 : M - 1;
                    int i = i0 + k, j = j0 + q;
                    // Kante der gröberen Stufe: Gitterpunkt oder Mitte zweier Punkte
                    int ci = Math.floorDiv(i, 2), cj = Math.floorDiv(j, 2);
                    double soll;
                    if ((i & 1) == 0 && (j & 1) == 0) soll = c.gridHeight(l + 1, ci, cj);
                    else if ((i & 1) == 0) soll = 0.5 * (c.gridHeight(l + 1, ci, cj) + c.gridHeight(l + 1, ci, cj + 1));
                    else if ((j & 1) == 0) soll = 0.5 * (c.gridHeight(l + 1, ci, cj) + c.gridHeight(l + 1, ci + 1, cj));
                    else { schief++; continue; }
                    double ist = karte != null ? karte[(l * M + q) * M + k] : c.vertexHeight(l, k, q);
                    max = Math.max(max, Math.abs(ist - soll));
                    n++;
                }
        }
        return new double[] {n, max, schief};
    }

    /** Daten auf der Karte gegen die Ringe: {Werte, anders}. */
    static long[] karteDaten(Gelaendebild t) {
        Clipmap[] cm = t.clipmaps();
        int T = t.T, L = t.stufen, h = cm[0].half();
        Puffer d = t.daten();
        float[] k = d.floats(T * T * L * Gelaendebild.EBENEN);
        Clipmap.Window[] w = t.gezeigt();
        long n = 0, falsch = 0;
        for (int e = 0; e < Gelaendebild.EBENEN; e++)
            for (int l = 0; l < L; l++)
                for (int j = w[l].cj() - h; j <= w[l].cj() + h; j++)
                    for (int i = w[l].ci() - h; i <= w[l].ci() + h; i++) {
                        float soll = cm[e].gridHeight(l, i, j);
                        float ist = k[((e * L + l) * T + Math.floorMod(j, T)) * T + Math.floorMod(i, T)];
                        n++;
                        if (Float.floatToIntBits(soll) != Float.floatToIntBits(ist)) falsch++;
                    }
        return new long[] {n, falsch};
    }

    /** Scheitel auf der Karte gegen vertexHeight: {größte Abweichung, 1}. */
    static double[] karteScheitel(Geraet g, Gelaendebild t, Kernel ks, Abtaster at, Puffer aus, Ersatz ers) {
        Clipmap c = t.hoehe;
        int L = c.levels(), M = c.grid;
        Ablauf a = new Ablauf(g, g.grafik);
        a.rechne("Scheitel", ks, ks.satz(t.daten(), t.sicht(), t.stufenPuffer(), ers.werte, at.an(ers.durch), t.licht(), at.an(ers.durch), aus), null,
                Kernel.gruppen(M, 8), Kernel.gruppen(M, 8), L,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, t.daten(), t.stufenPuffer()), Ablauf.textur(Ablauf.RECHNEN, ers.durch), Ablauf.schreibt(aus)));
        g.grafik.einmal(cb -> a.aufnehmen(cb, null));
        float[] v = aus.floats(L * M * M);
        letzte = v;
        double max = 0;
        for (int l = 0; l < L; l++)
            for (int q = 0; q < M; q++)
                for (int k = 0; k < M; k++) max = Math.max(max, Math.abs(v[(l * M + q) * M + k] - c.vertexHeight(l, k, q)));
        return new double[] {max, 1};
    }

    /**
     * Prüfstein 3: {Himmel unter der Kimm, NaN, Bildpunkte mit Gelände}. Unter der Kimm liegt, was tiefer zeigt als
     * die Kimm über dem tiefsten möglichen Grund (Ersatzwert der Clipmap) mit Erdkrümmung, zwei Bildpunkte Rand.
     */
    static long[] dicht(Blick b, Bild hdr, Bild tiefe, Gelaendebild t) {
        float[] d = tiefe.lies(), h = hdr.lies();
        double R = t.hoehe.curvatureRadius, tief = b.hoehe - t.hoehe.noData;
        double kimm = -Math.sqrt(2 * tief / R), px = Math.toRadians(b.bildwinkel) / b.hoehe_px;
        long unter = 0, nan = 0, gel = 0;
        for (int y = 0; y < b.hoehe_px; y++)
            for (int x = 0; x < b.breite; x++) {
                int i = y * b.breite + x;
                for (int c = 0; c < 4; c++) if (!Float.isFinite(h[4 * i + c])) { nan++; break; }
                if (d[i] > 0) { gel++; continue; }
                double[] s = b.strahl(x + 0.5, y + 0.5);
                if (Math.asin(s[1]) < kimm - 2 * px) unter++;
            }
        return new long[] {unter, nan, gel};
    }
}
