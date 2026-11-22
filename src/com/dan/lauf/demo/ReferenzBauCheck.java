package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Foto;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.bau.Hausbild;
import com.dan.render.bewuchs.Bewuchsbild;
import com.dan.render.reference.Bauszene;
import com.dan.render.reference.Dreiecksbaum;
import com.dan.render.reference.Gelaendeszene;
import com.dan.render.reference.Gestaltfeld;
import com.dan.render.reference.Kronen;
import com.dan.render.reference.Pfadverfolger;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.Szene;
import com.dan.render.reference.Wellen;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static com.dan.core.pruef.Attest.f;

/**
 * Referenz und Foto mit Häusern, Körpern, Gestalt und Gehölzen (P12c3, Werkbuch HalligHooge Teil A, 12.13, Datei 81;
 * F138 A, F141 A, F142 A, F143 A) auf einer Bühne ohne Fenster.
 * <ul>
 * <li><b>1 Dreiecksbaum:</b> Schnitte durch den Hüllkörperbaum gegen alle Dreiecke, zufällige Strahlen – bitgleich.</li>
 * <li><b>2 Ofen:</b> Laub (Art Blatt) im weißen Ofen gibt genau 1; Spiegel (Glas) zur Auskunft.</li>
 * <li><b>Prüfstein 8:</b> Art des ersten Treffers durch die Mitte jedes Bildpunkts (Himmel, Bauten, Gelände, Wasser,
 * Gestaltwasser), Echtzeit gegen Referenz, ohne Bewuchs; im Echtzeitbild Bauten aus der Tiefe mit gegen ohne Häuser und
 * Körper, Wasser und Gestaltwasser aus denselben Regeln am Treffer. Bildpunkte am Rand zweier Arten im Echtzeitbild
 * zählen nicht [G] (Kanten fallen je nach Netz um einen halben Bildpunkt anders).</li>
 * <li><b>Prüfstein 19:</b> Gehölze im Sommer (Tag 200): Bildpunkte mit Gehölz im Echtzeitbild (Tiefe mit gegen ohne
 * Bewuchs, ohne Halme) gegen die Referenz (Anteil der Treffer im Laub aus 16 Strahlen im Bildpunkt ≥ ½), in beiden
 * Richtungen ≥ 70 % [G].</li>
 * <li><b>Prüfstein 9:</b> Alles aus (Häuser, Gestalt, Bewuchs): dieselbe Referenz wie ohne die Teile, ein kleines Bild
 * bitgleich.</li>
 * <li><b>Prüfstein 10:</b> Fotos (Hanswarft am Tag und nachts, Gehölze) neben dem Echtzeitbild,
 * {@code docs/status/P12c_Foto.png}; dazu die Leuchtdichte auf den Häusern (Auskunft).</li>
 * </ul>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzBauCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--sekunden 600]
 * </pre>
 */
public final class ReferenzBauCheck {
    private static final Attest A = new Attest("com.dan.render.reference (Bauten)", Referenz.VERSION);
    static final int B = HausCheck.BREITE, H = HausCheck.HOEHE;
    static final int HIMMEL = 0, BAUTEN = 1, GELAENDE = 2, WASSER = 3, GESTALTWASSER = 4, GEHOELZ = 5;
    static final String[] ARTEN = {"Himmel", "Bauten", "Gelände", "Wasser", "Gestaltwasser", "Gehölz"};

    public static void main(String[] args) throws Exception {
        String datei = "..\\HalligHooge\\src\\szenen\\Hooge.szenen";
        double sekunden = 600;
        int fb = 480, fh = 270;
        for (int i = 0; i + 1 < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> datei = args[++i];
                case "--sekunden" -> sekunden = Double.parseDouble(args[++i]);
                case "--foto" -> { fb = Integer.parseInt(args[++i]); fh = fb * 9 / 16; }
                default -> { }
            }
        }
        System.out.println("ReferenzBauCheck – Referenz mit Bauten und Gehölzen (Werkbuch HalligHooge Teil A, P12c3)");
        List<BufferedImage[]> reihen = new ArrayList<>();
        try {
            Szenen sz = Szenen.lies(new File(datei));
            Szenario s = null;
            for (Szenario x : sz.szenarien()) if (x.schluessel.equals("ruhig")) s = x;
            if (s == null) throw new IllegalStateException("Szenario ruhig fehlt");
            try (Buehne b = Buehne.ohneFenster(sz.gebiet(), B, H)) {
                A.pruefe(b.hatHaeuser() && b.hatBewuchs(), "Häuser und Gehölze im Gebiet", b.hatHaeuser() + ", " + b.hatBewuchs(), "true, true");
                b.oeffne(s);
                double tag = zeit(b, "2026-10-30T11:00:00Z"), nacht = zeit(b, "2026-10-30T18:00:00Z");
                dreiecksbaum(b, sz, tag);
                ofen();
                for (String an : new String[] {"hanswarft_haeuser", "lahnungen", "fething"}) pruefstein8(b, sz, an, tag);
                pruefstein19(b, sz, tag);
                pruefstein9(b, sz, tag);
                A.teil("Prüfstein 10: Fotos");
                reihen.add(foto(b, sz, "hanswarft_haeuser", tag, "Hanswarft am Tag", fb, fh, sekunden));
                reihen.add(foto(b, sz, "hanswarft_haeuser", nacht, "Hanswarft nachts (Fenster)", fb, fh, sekunden));
                reihen.add(foto(b, sz, "gehoelz", tag, "Gehölze an der Hanswarft", fb, fh, sekunden));
            }
            if (!reihen.isEmpty()) statusBild(reihen);
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P12c_ReferenzBau.md"), "P12c – ReferenzBauCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static double zeit(Buehne b, String iso) { return (Instant.parse(iso).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0; }

    static Blick blick(Buehne b, Szenen sz, String an, double t) {
        Ansicht a = sz.ansicht(an);
        if (a == null) throw new IllegalStateException("Ansicht " + an + " fehlt in der Szenendatei");
        Blick bl = new Blick();
        a.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
        return bl.groesse(B, H);
    }

    static void alles(Buehne b, boolean haeuser, boolean gestalt, boolean bewuchs) {
        b.haeuser(haeuser);
        b.gestalt(gestalt);
        b.bewuchs(bewuchs);
    }

    // ------------------------------------------------------------------ 1 Dreiecksbaum

    static void dreiecksbaum(Buehne b, Szenen sz, double t) {
        A.teil("1 Dreiecksbaum");
        Hausbild hb = b.hausbild();
        Blick bl = blick(b, sz, "hanswarft_haeuser", t);
        long t0 = System.nanoTime();
        List<int[]> alle = List.of(new int[] {0, hb.bauten.scheitelZahl()});
        Dreiecksbaum db = new Dreiecksbaum(hb.bauten, alle, hb.zufallJeHaus(), bl.ost, bl.nord, bl.hoehe, 1 / (2 * com.dan.core.raum.Erde.R_OPTISCH));
        double sek = (System.nanoTime() - t0) / 1e9;
        A.info("Baum", f("%d Dreiecke (Häuser und Körper), gebaut in %.2f s", db.dreiecke(), sek));
        // Strahlen vom Auge und von Punkten über der Warft in zufällige Richtungen, dazu flach über die Hallig
        SplittableRandom r = new SplittableRandom(12);
        int n = 4000;
        long anders = 0, treffer = 0;
        double sekB = 0, sekA = 0;
        for (int i = 0; i < n; i++) {
            // seit Lauf C3: die Hälfte der Strahlen auf die Mitte eines zufälligen Dreiecks (sonst treffen fast alle nichts)
            double[] o = {r.nextDouble(-300, 300), r.nextDouble(-2, 12), r.nextDouble(-300, 300)};
            if (i % 4 == 0) o = new double[] {0, 0, 0};
            double[] d;
            if (i % 2 == 0) {
                double[] m = db.mitte(r.nextInt(db.dreiecke()));
                d = new double[] {m[0] - o[0] + r.nextDouble(-0.5, 0.5), m[1] - o[1] + r.nextDouble(-0.5, 0.5), m[2] - o[2] + r.nextDouble(-0.5, 0.5)};
                double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                for (int a = 0; a < 3; a++) d[a] /= l;
            } else {
                double cz = r.nextDouble(-0.3, 0.6), ph = r.nextDouble(0, 2 * Math.PI), sr = Math.sqrt(1 - cz * cz);
                d = new double[] {sr * Math.cos(ph), cz, sr * Math.sin(ph)};
            }
            long a0 = System.nanoTime();
            double tb = db.weg(o, d, Double.POSITIVE_INFINITY);
            long a1 = System.nanoTime();
            double ta = db.wegAlle(o, d, Double.POSITIVE_INFINITY);
            long a2 = System.nanoTime();
            sekB += (a1 - a0) / 1e9;
            sekA += (a2 - a1) / 1e9;
            if (Double.doubleToLongBits(tb) != Double.doubleToLongBits(ta)) anders++;
            if (tb < Double.POSITIVE_INFINITY) treffer++;
        }
        A.pruefe(anders == 0 && treffer > n / 4, "Dreiecksbaum gegen alle Dreiecke (zufällige Strahlen)", f("%d von %d anders, %d mit Treffer; %.1f µs je Schnitt im Baum, %.0f µs gegen alle",
                anders, n, treffer, 1e6 * sekB / n, 1e6 * sekA / n), "0 anders");
    }

    // ------------------------------------------------------------------ 2 Ofen

    static void ofen() {
        A.teil("2 Ofen");
        // eine Krone (Esche, 12 m) ohne Boden im weißen Ofen
        float[][] k = {{0, -40, 0, 5, 4, 3, 12, 1.5f, 0.3f, 0.05f, 0.09f, 0.03f, 0.1f, 0.08f, 0.06f}};
        Kronen kr = new Kronen(k, 0, 0, 0, 0, 0, 0);
        Szene sz = new Szene() {
            @Override public boolean schneide(double[] p, double[] d, double tMax, Treffer aus) { return kr.schneide(p, d, tMax, aus); }
            @Override public double augeHoehe() { return 2; }
        };
        Wellen wl = new Wellen(false, new Wellen.Szenenwerte(com.dan.core.szene.Atmosphere.forVisibility(30, 0.08), new com.dan.core.szene.Wasseroptik()));
        Pfadverfolger pv = new Pfadverfolger(sz, wl, new ArrayList<>(), 0.01, 1.33, 5);
        pv.ofen();
        pv.ohneLuft();
        SplittableRandom r = new SplittableRandom(3);
        double s = 0, mx = 0;
        int n = 20000, imLaub = 0;
        Szene.Treffer tr = new Szene.Treffer();
        for (int i = 0; i < n; i++) {
            double[] o = {r.nextDouble(-2, 2), 0, -40 + r.nextDouble(-2, 2)}, d = {r.nextDouble(-0.2, 0.2), 1, r.nextDouble(-0.2, 0.2)};
            double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            for (int a = 0; a < 3; a++) d[a] /= l;
            o[1] = 0;
            if (sz.schneide(o, d, Double.POSITIVE_INFINITY, tr) && tr.art == Szene.BLATT) imLaub++;
            double[] L = pv.verfolge(o, d);
            double y = (L[0] + L[1] + L[2]) / 3;
            s += y;
            mx = Math.max(mx, Math.abs(y - 1));
        }
        A.pruefe(mx < 1e-9 && imLaub > n / 4, "Ofen: Laub (Blatt) im weißen Ofen, Strahlen von unten in die Krone", f("Mittel %.12f, größte Abweichung %.1e; %d von %d treffen zuerst Laub", s / n, mx, imLaub, n), "1, Abweichung < 10⁻⁹");
    }

    // ------------------------------------------------------------------ Prüfstein 8

    /** Arten des Echtzeitbilds aus der Tiefe mit und ohne Bauten. */
    static int[] artenEcht(Buehne b, Blick bl, float[] tMit, float[] tOhne, Gelaendeszene gs) {
        int[] art = new int[B * H];
        double[] v = bl.strahl(B / 2.0, H / 2.0);
        IntStream.range(0, H).parallel().forEach(py -> {
            com.dan.core.gitter.Gelaende.Abfrage ab = gs.gelaende.abfrage();
            for (int px = 0; px < B; px++) {
                int k = py * B + px;
                if (!(tMit[k] > 0)) { art[k] = HIMMEL; continue; }
                if (Math.abs(tMit[k] - tOhne[k]) > 1e-5 * Math.max(tMit[k], 1e-12) || !(tOhne[k] > 0)) { art[k] = BAUTEN; continue; }
                double[] d = bl.strahl(px + 0.5, py + 0.5);
                double cs = d[0] * v[0] + d[1] * v[1] + d[2] * v[2];
                double s = bl.nah / tMit[k] / cs;
                double x = s * d[0], z = s * d[2];
                double o = gs.ost + x, n = gs.nord - z;
                double g = gs.grund(ab, o, n), e = gs.eta(x, z);
                if (!Double.isNaN(e) && e > g + 1e-3) art[k] = WASSER;
                else if (gs.gestalt != null && gs.gestalt.wasser(o, n, g) >= 0.5) art[k] = GESTALTWASSER;
                else art[k] = GELAENDE;
            }
        });
        return art;
    }

    /** Arten der Referenz am Strahl durch die Mitte jedes Bildpunkts. */
    static int[] artenRef(Szene sz, Blick bl) {
        int[] art = new int[B * H];
        IntStream.range(0, H).parallel().forEach(py -> {
            Szene.Treffer tr = new Szene.Treffer();
            for (int px = 0; px < B; px++) {
                double[] d = bl.strahl(px + 0.5, py + 0.5);
                art[py * B + px] = art(sz, new double[3], d, tr);
            }
        });
        return art;
    }

    static int art(Szene sz, double[] o, double[] d, Szene.Treffer tr) {
        if (!sz.schneide(o, d, Double.POSITIVE_INFINITY, tr)) return HIMMEL;
        if (tr.aus == Szene.AUS_BAUTEN) return BAUTEN;
        if (tr.aus == Szene.AUS_GEHOELZ) return GEHOELZ;
        if (tr.art == Szene.WASSER) return WASSER;
        return tr.spiegel >= 0.5 ? GESTALTWASSER : GELAENDE;
    }

    static Gelaendeszene grund(Szene s) { return s instanceof Bauszene bs ? bs.grund : (Gelaendeszene) s; }

    static void pruefstein8(Buehne b, Szenen sz, String an, double t) {
        A.teil("Prüfstein 8: " + an);
        alles(b, true, true, false);
        Blick bl = blick(b, sz, an, t);
        b.standbild(bl, t);
        float[] tMit = b.tiefe();
        Hausbild hb = b.hausbild();
        hb.an = false;
        hb.koerperAn = false;
        b.standbild(bl, t);
        float[] tOhne = b.tiefe();
        hb.an = true;
        hb.koerperAn = true;
        Foto fo = b.foto(bl, t, an, B, H);
        Szene sc = fo.ref.szene;
        Gelaendeszene gs = grund(sc);
        int[] e = artenEcht(b, bl, tMit, tOhne, gs), r = artenRef(sc, bl);
        long n = 0, gleich = 0, rand = 0, alleGleich = 0;
        long[][] m = new long[6][6];
        int[] zahl = new int[6];
        for (int py = 0; py < H; py++)
            for (int px = 0; px < B; px++) {
                int k = py * B + px;
                zahl[e[k]]++;
                if (e[k] == r[k]) alleGleich++;
                boolean kante = false;
                for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int x = px + o[0], y = py + o[1];
                    if (x >= 0 && y >= 0 && x < B && y < H && e[y * B + x] != e[k]) kante = true;
                }
                if (kante) { rand++; continue; }
                n++;
                if (e[k] == r[k]) gleich++;
                else m[e[k]][r[k]]++;
            }
        StringBuilder sb = new StringBuilder();
        for (int a = 0; a < 6; a++) if (zahl[a] > 0) sb.append(f("%s %.1f %%, ", ARTEN[a], 100.0 * zahl[a] / (B * H)));
        A.info(an + ": Arten im Echtzeitbild", sb.toString() + f("Bauten in der Referenz: %d Dreiecke", sc instanceof Bauszene bs && bs.bauten != null ? bs.bauten.dreiecke() : 0));
        StringBuilder mm = new StringBuilder();
        for (int a = 0; a < 6; a++) for (int c = 0; c < 6; c++) if (m[a][c] > 0) mm.append(f("%s → %s %d; ", ARTEN[a], ARTEN[c], m[a][c]));
        A.info(an + ": abweichend (Echtzeit → Referenz, ohne Rand)", mm.length() == 0 ? "keine" : mm.toString());
        A.pruefe(n > 0 && gleich >= 0.999 * n, "Prüfstein 8: " + an + " – Art des ersten Treffers, Echtzeit gegen Referenz (ohne Rand zweier Arten)",
                f("%.3f %% von %d Bildpunkten gleich; am Rand %d (%.1f %%) ausgenommen, mit ihnen %.3f %%", 100.0 * gleich / Math.max(1, n), n, rand, 100.0 * rand / (B * H),
                        100.0 * alleGleich / (B * H)), "≥ 99,9 %");
    }

    // ------------------------------------------------------------------ Prüfstein 19

    static void pruefstein19(Buehne b, Szenen sz, double t) {
        A.teil("Prüfstein 19: Gehölze");
        Bewuchsbild bw = b.bewuchsbild();
        bw.tagFest = 200;
        bw.halmeAn = false;
        try {
            alles(b, true, true, true);
            Blick bl = blick(b, sz, "gehoelz", t);
            b.standbild(bl, t);
            float[] tMit = b.tiefe();
            b.bewuchs(false);
            b.standbild(bl, t);
            float[] tOhne = b.tiefe();
            b.bewuchs(true);
            Foto fo = b.foto(bl, t, "gehoelz", B, H);
            Szene sc = fo.ref.szene;
            long echt = 0, ref = 0, beide = 0;
            boolean[] baumR = new boolean[B * H];
            IntStream.range(0, H).parallel().forEach(py -> {
                Szene.Treffer tr = new Szene.Treffer();
                for (int px = 0; px < B; px++) {
                    int hit = 0;
                    for (int s = 0; s < 16; s++) {
                        double[] d = bl.strahl(px + (s % 4 + 0.5) / 4, py + (s / 4 + 0.5) / 4);
                        if (art(sc, new double[3], d, tr) == GEHOELZ) hit++;
                    }
                    baumR[py * B + px] = hit >= 8;
                }
            });
            for (int k = 0; k < B * H; k++) {
                boolean be = tMit[k] > tOhne[k] * 1.00001;
                if (be) echt++;
                if (baumR[k]) ref++;
                if (be && baumR[k]) beide++;
            }
            A.info("Gehölze", f("Tag 200 (Sommer); %d Kronen in der Referenz; Bildpunkte mit Gehölz: Echtzeit %d, Referenz %d, beide %d",
                    sc instanceof Bauszene bs && bs.kronen != null ? bs.kronen.zahl() : 0, echt, ref, beide));
            A.pruefe(echt > 500 && beide >= 0.7 * echt, "Prüfstein 19: Gehölz im Echtzeitbild, das auch in der Referenz Gehölz ist", f("%.1f %%", 100.0 * beide / Math.max(1, echt)), "≥ 70 % [G]");
            A.pruefe(ref > 500 && beide >= 0.7 * ref, "Prüfstein 19: Gehölz in der Referenz, das auch im Echtzeitbild Gehölz ist", f("%.1f %%", 100.0 * beide / Math.max(1, ref)), "≥ 70 % [G]");
        } finally {
            bw.tagFest = 0;
            bw.halmeAn = true;
        }
    }

    // ------------------------------------------------------------------ Prüfstein 9

    static void pruefstein9(Buehne b, Szenen sz, double t) {
        A.teil("Prüfstein 9: alles aus");
        alles(b, false, false, false);
        try {
            Blick bl = blick(b, sz, "hanswarft_haeuser", t);
            Foto fo = b.foto(bl, t, "aus", B, H);
            boolean grundSzene = fo.ref.szene instanceof Gelaendeszene gs && gs.gestalt == null;
            A.pruefe(grundSzene, "Prüfstein 9: Häuser, Gestalt und Bewuchs aus – die Szene der Referenz ist die vor P12c3 (Gelände und Wasser, dieselbe Referenz)",
                    grundSzene ? "Gelaendeszene ohne Gestalt, ohne Bauten und Kronen" : fo.ref.szene.getClass().getSimpleName(), "ja");
            // zwei kleine Bilder mit derselben Saat: wiederholbar (die Kronen ziehen ihren Zufall aus dem Strahl)
            alles(b, true, true, true);
            Foto mit = b.foto(bl, t, "mit", B, H);
            mit.ref.spektral = false;
            mit.ref.saat = 7;
            double[][][] a1 = mit.ref.bild(bl, 64, 36, 2), a2 = mit.ref.bild(bl, 64, 36, 2);
            long anders = 0, nicht = 0;
            for (int h = 0; h < 2; h++)
                for (int c = 0; c < 3; c++)
                    for (int i = 0; i < a1[h][c].length; i++) {
                        if (Double.doubleToLongBits(a1[h][c][i]) != Double.doubleToLongBits(a2[h][c][i])) anders++;
                        if (!Double.isFinite(a1[h][c][i])) nicht++;
                    }
            A.pruefe(anders == 0 && nicht == 0, "Referenz mit allem an: zwei Bilder mit derselben Saat (64 × 36, 2 Pfade)", f("%d Werte anders, %d nicht endlich", anders, nicht), "0, 0");
        } finally {
            alles(b, true, true, true);
        }
    }

    // ------------------------------------------------------------------ Prüfstein 10

    static BufferedImage[] foto(Buehne b, Szenen sz, String an, double t, String titel, int fb, int fh, double sekunden) throws Exception {
        alles(b, true, true, true);
        Blick bl = blick(b, sz, an, t);
        float[] echt = b.standbild(bl, t);
        float[] tMit = b.tiefe();
        Hausbild hb = b.hausbild();
        hb.an = false;
        hb.koerperAn = false;
        b.standbild(bl, t);
        float[] tOhne = b.tiefe();
        hb.an = true;
        hb.koerperAn = true;
        b.standbild(bl, t);
        Foto fo = b.foto(bl, t, titel, fb, fh);
        fo.zeitgrenze = sekunden;
        fo.rechne();
        BufferedImage iF = fo.bild();
        BufferedImage iE = new BufferedImage(B, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < B; x++) {
                int k = 4 * (y * B + x), rgb = 0;
                for (int c = 0; c < 3; c++) rgb = (rgb << 8) | (int) Math.round(255 * Math.max(0, Math.min(1, echt[k + c])));
                iE.setRGB(x, y, rgb);
            }
        BufferedImage iEk = new BufferedImage(fb, fh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = iEk.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(iE, 0, 0, fb, fh, null);
        g.dispose();
        // Auskunft: Häuser im Foto gegen das Echtzeitbild (Mittel der Helligkeit im Bild, über die Bildpunkte der Bauten)
        double sF = 0, sE = 0;
        long n = 0;
        for (int y = 0; y < fh; y++)
            for (int x = 0; x < fb; x++) {
                int ex = Math.min(B - 1, x * B / fb + B / fb / 2), ey = Math.min(H - 1, y * H / fh + H / fh / 2), k = ey * B + ex;
                if (!(tMit[k] > 0) || Math.abs(tMit[k] - tOhne[k]) <= 1e-5 * tMit[k]) continue;
                sF += hell(iF.getRGB(x, y));
                sE += hell(iEk.getRGB(x, y));
                n++;
            }
        A.info(titel, f("Foto %d × %d: %s; %d Runden, %.0f s, Rauschen in Blöcken %.2f", fb, fh, fo.ende(), fo.runden(), fo.sekunden(), fo.rauschenBlock()));
        A.info(titel + ": Häuser im Bild (Auskunft)", n == 0 ? "keine Bildpunkte" : f("mittlere Helligkeit (sRGB, 0 … 1) Foto %.3f, Echtzeit %.3f, Verhältnis %.3f über %d Bildpunkte", sF / n, sE / n, sF / Math.max(1e-9, sE), n));
        File d = new File("docs" + File.separator + "status");
        d.mkdirs();
        String name = "P12c_Foto_" + an + (t > zeit(b, "2026-10-30T16:00:00Z") ? "_nacht" : "") + ".png";
        ImageIO.write(iF, "png", new File(d, name));
        A.info(titel + ": Bild", new File(d, name).getPath());
        return new BufferedImage[] {beschrifte(iF, "Foto (Referenz) – " + titel), beschrifte(iEk, "Echtzeit")};
    }

    static double hell(int rgb) {
        double r = ((rgb >> 16) & 255) / 255.0, g = ((rgb >> 8) & 255) / 255.0, b = (rgb & 255) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    static BufferedImage beschrifte(BufferedImage im, String txt) {
        BufferedImage o = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(im, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int w = g.getFontMetrics().stringWidth(txt);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(4, 4, w + 8, 18);
        g.setColor(Color.WHITE);
        g.drawString(txt, 8, 17);
        g.dispose();
        return o;
    }

    static void statusBild(List<BufferedImage[]> reihen) throws Exception {
        int w = reihen.get(0)[0].getWidth(), h = reihen.get(0)[0].getHeight();
        BufferedImage o = new BufferedImage(2 * w, h * reihen.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = o.createGraphics();
        for (int i = 0; i < reihen.size(); i++) {
            g.drawImage(reihen.get(i)[0], 0, i * h, null);
            g.drawImage(reihen.get(i)[1], w, i * h, null);
        }
        g.dispose();
        File d = new File("docs" + File.separator + "status");
        Files.createDirectories(d.toPath());
        ImageIO.write(o, "png", new File(d, "P12c_Foto.png"));
        A.info("Bilder zur Sichtprüfung (Prüfstein 10)", new File(d, "P12c_Foto.png").getPath());
    }
}
