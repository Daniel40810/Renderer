package com.dan.lauf.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.raum.Erde;
import com.dan.lauf.Archivquelle;
import com.dan.physics.atmos.Profil;
import com.dan.render.air.Himmel;
import com.dan.render.air.Kimmung;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Kimmung auf der Karte und im Gebiet (P10b Teil 2, Werkbuch HalligHooge Teil A, 12.11): Prüfsteine 9, 11, 12, 14.
 * <ul>
 * <li><b>1 Kernel gegen den Prozessor</b> an der ebenen See mit Insel ({@link KimmungBildCheck#see}): {@code kimmung.comp}
 * gegen {@link Kimmung#durchgang}; in der Normalatmosphäre bleibt das Bild bitgleich (Prüfstein 11), über warmem
 * Wasser liegt die Kimm am Grenzwinkel (Prüfstein 12).</li>
 * <li><b>2 Nässe</b> (Prüfstein 9): das Gelände mit {@code surf.wet} = 1 gegen dasselbe mit {@code water.wetAge} = 0.</li>
 * <li><b>3 Im Gebiet</b>: das Archiv mit atmos zur Zeit {@code --zeit}, Blick {@code --blick}: der Kernel gegen den
 * Prozessor am gezeichneten Bild; die Kimm gegen den Grenzwinkel des Profils im Blickfeld; Status-Bild
 * {@code docs/status/P10b_Kimmung.png} (gerade, mit Kimmung, der Streifen vergrößert).</li>
 * <li><b>4 Kosten</b> (Prüfstein 14): Zeit des Durchgangs je Bild; die Tafel; Bilder über wechselnde Stände.</li>
 * </ul>
 * {@code --gebiet}, {@code --archiv}, {@code --zeit}, {@code --blick Ost,Nord,Höhe,Richtung,Neigung,Bildwinkel},
 * {@code --breite}, {@code --hoehe}, {@code --name}. Attest {@code docs/status/<name>.md}.
 */
public final class KimmungKarteCheck {
    private static final Attest A = new Attest("com.dan.render.air (Kimmung auf der Karte)", Kimmung.VERSION);
    static final double MIN = 60 * 180 / Math.PI;
    static int breite = 1920, hoehe = 1080;

    public static void main(String[] args) {
        List<String> a = List.of(args);
        File gb = new File(AtmosLaufCheck.arg(a, "--gebiet", "D:\\Daten\\HalligHooge\\gebiet"));
        File ar = new File(AtmosLaufCheck.arg(a, "--archiv", "D:\\Daten\\HalligHooge\\_p9c\\archiv_m1b"));
        Instant zeit = Instant.parse(AtmosLaufCheck.arg(a, "--zeit", "2026-10-18T08:00:00Z"));
        String[] bt = AtmosLaufCheck.arg(a, "--blick", "462600,6046900,4,325,0,5.6").split(",");
        double[] bl = new double[bt.length];
        for (int i = 0; i < bl.length; i++) bl[i] = Double.parseDouble(bt[i].strip());
        breite = Integer.parseInt(AtmosLaufCheck.arg(a, "--breite", "1920"));
        hoehe = Integer.parseInt(AtmosLaufCheck.arg(a, "--hoehe", "1080"));
        String name = AtmosLaufCheck.arg(a, "--name", "P10b_KimmungKarte");
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("KimmungKarteCheck", 3L << 30);
            kernel(g, konto);
            gebiet(g, konto, gb, ar, zeit, new Blick(bl[0], bl[1], bl[2], bl[3], bl[4], bl[5]), name);
            A.teil("Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                String erste = g.validierungsFehler > 0 && !g.meldungen.isEmpty() ? ": " + g.meldungen.get(0) : "";
                A.pruefe(g.validierungsFehler == 0, "Prüfstein 15: Validierungsschicht " + g.schicht + " über Kernel und Bild der Kimmung", g.validierungsFehler + " Fehler" + erste, "0 Fehler");
            } else A.info("Validierung", "nicht verlangt (-Dcore.gpu.validierung=true)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", name + ".md"), "P10b – KimmungKarteCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1 Kernel gegen den Prozessor

    /** Lässt den Kernel über ein gerades Bild laufen; gibt {HDR vorher, HDR nachher}, wie die Karte sie hält (float16). */
    static float[][] karte(Geraet g, Konto konto, Himmel himmel, Kimmung km, KimmungBildCheck.Gerade gr) {
        try (Bild hdr = new Bild(g, konto, "Prüfbild", gr.breite(), gr.hoehe(), Bild.RGBA16F); Bild tiefe = new Bild(g, konto, "Prüftiefe", gr.breite(), gr.hoehe(), Bild.D32F);
             Puffer hw = Puffer.speicher(g, konto, "Prüfwerte", 4L * Himmel.WERTE_FLOATS)) {
            hdr.schreibe(gr.hdr());
            tiefe.schreibe(gr.tiefe());
            float[] vorher = hdr.lies();
            Bildziel z = new Bildziel(hdr, tiefe);
            z.blick = gr.blick().kopie().groesse(gr.breite(), gr.hoehe());
            z.zeit = Instant.parse("2026-10-18T10:00:00Z");
            z.vorbelichtung = 1;
            hw.schreibe(himmel.werte(z));
            z.puffer.put(Himmel.WERTE, hw);
            Ablauf a = new Ablauf(g, g.grafik);
            km.vorbereiten(a, z);
            km.zeichnen(a, z);
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            g.ruhe();
            return new float[][] {vorher, hdr.lies()};
        }
    }

    /** Anteil der Bildpunkte, in denen zwei HDR-Bilder um mehr als {@code grenze} (relativ, je Kanal) abweichen; dazu die größte Abweichung. */
    static double[] vergleich(float[] a, float[] b, double grenze) {
        long ueber = 0;
        double groesst = 0;
        for (int k = 0; k < a.length / 4; k++) {
            double d = 0;
            for (int c = 0; c < 3; c++) d = Math.max(d, Math.abs(a[4 * k + c] - b[4 * k + c]) / Math.max(1, Math.abs(b[4 * k + c])));
            if (d > grenze) ueber++;
            groesst = Math.max(groesst, d);
        }
        return new double[] {ueber / (a.length / 4.0), groesst};
    }

    static void kernel(Geraet g, Konto konto) {
        A.teil("1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°)");
        double auge = 4, sperr = 0.05;
        KimmungBildCheck.Gerade gr = KimmungBildCheck.see(auge, 5.6, 960, 1080, 8000, 6, -150, 150);
        try (Himmel himmel = new Himmel(g, konto); Kimmung km = new Kimmung(g, konto)) {
            km.sperr = sperr;
            // Normalatmosphäre des Bilds
            km.luft(KimmungBildCheck.hoehen(), KimmungBildCheck.normalprofil().r, 0);
            float[][] n = karte(g, konto, himmel, km, gr);
            A.pruefe(Arrays.equals(n[0], n[1]), "Prüfstein 11: Normalatmosphäre (k = 0,13), der Durchgang auf der Karte lässt das Bild, wie es ist", Arrays.equals(n[0], n[1]) ? "bitgleich"
                    : f("verschieden: %.2f %% der Bildpunkte, größte Abweichung %.2e", 100 * vergleich(n[1], n[0], 0)[0], vergleich(n[1], n[0], 0)[1]), "bitgleich");
            // über warmem Wasser (Profil aus P10a)
            Profil pr = KimmungCheck.profil(3, 280.15, 285.15, 0.8);
            km.luft(KimmungBildCheck.hoehen(), pr.r, 0);
            float[][] w = karte(g, konto, himmel, km, gr);
            float[] soll = Kimmung.durchgang(km.tafel(), gr.blick(), w[0], gr.tiefe(), gr.breite(), gr.hoehe());
            double[] v = vergleich(w[1], soll, 0.01), geaendert = vergleich(w[1], w[0], 0.01);
            A.hoechstens("Kernel gegen den Prozessor über warmem Wasser (5 K, 3 m/s): Bildpunkte, die um mehr als 1 % abweichen", 100 * v[0], 0.5, "%");
            A.info("Durchgang", f("ändert %.1f %% der Bildpunkte; größte Abweichung gegen den Prozessor %.3f", 100 * geaendert[0], v[1]));
            // Kimm neben der Insel
            Kimmung.Geometrie q = new Kimmung.Geometrie(gr.blick(), gr.breite(), gr.hoehe());
            int frei = 40, kimm = -1, gerade = -1;
            for (int py = 0; py < gr.hoehe(); py++) {
                int k = py * gr.breite() + frei;
                if (kimm < 0 && KimmungBildCheck.art(w[1][4 * k], w[1][4 * k + 1], w[1][4 * k + 2]) == 1) kimm = py;
                if (gerade < 0 && KimmungBildCheck.art(w[0][4 * k], w[0][4 * k + 1], w[0][4 * k + 2]) == 1) gerade = py;
            }
            double grenz = KimmungCheck.grenzwinkelFrei(pr, auge, sperr), sollZeile = q.zeile(frei, -Math.tan(grenz));
            A.hoechstens(f("Prüfstein 12: Kimm auf der Karte gegen den Grenzwinkel (%.2f′; gerade in Zeile %d, mit Kimmung in Zeile %d, Soll %.1f)", grenz * MIN, gerade, kimm, sollZeile + 0.5),
                    Math.abs(kimm - 0.5 - sollZeile), 1.0, "Zeilen");
            int mitte = gr.breite() / 2, aufrecht = 0, gespiegelt = 0;
            for (int py = 1; py < gr.hoehe(); py++) {
                int k = py * gr.breite() + mitte, kv = k - gr.breite();
                if (!KimmungBildCheck.insel(w[1], k) || !KimmungBildCheck.insel(w[1], kv)) continue;
                float dr = w[1][4 * k] - w[1][4 * kv];
                if (dr < -1) aufrecht++;
                else if (dr > 1) gespiegelt++;
            }
            A.pruefe(aufrecht >= 3 && gespiegelt >= 3, "Prüfstein 12: Unter der Insel steht ihr Spiegelbild (Zeilen aufrecht, Zeilen gespiegelt)", aufrecht + ", " + gespiegelt, "je ≥ 3");
        }
    }

    // ------------------------------------------------------------------ 2–4 im Gebiet

    /** Die Ausgabe (sRGB, 0 … 1 je Kanal) als Bild. */
    static BufferedImage ausgabe(float[] rgba, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < w * h; k++) {
            int r = Math.round(255 * rgba[4 * k]), gg = Math.round(255 * rgba[4 * k + 1]), b = Math.round(255 * rgba[4 * k + 2]);
            im.setRGB(k % w, k / w, r << 16 | gg << 8 | b);
        }
        return im;
    }

    /** HDR ohne die Vorbelichtung. */
    static float[] ohneVorbelichtung(float[] hdr, double vorb) {
        float[] o = new float[hdr.length];
        for (int i = 0; i < o.length; i++) o[i] = (float) (hdr[i] / vorb);
        return o;
    }

    static float[] bild(BildCheck.Szene sz, Blick b, Instant t0, double t) {
        for (int k = 0; k < 12; k++) {
            sz.rahmen.belichtung.sofort();
            sz.zeichne(b, t0, t, null);
        }
        return ohneVorbelichtung(sz.rahmen.ziel().hdr.lies(), sz.rahmen.ziel().vorbelichtung);
    }

    static void gebiet(Geraet g, Konto konto, File gb, File ar, Instant zeit, Blick blick, String name) throws Exception {
        Gebiet ge = Gebiet.lies(gb);
        try (Archivquelle q = new Archivquelle(ar.toPath()); BildCheck.Szene sz = new BildCheck.Szene(g, konto, gb, ge, q, breite, hoehe); Stoppuhr uhr = new Stoppuhr(g, g.grafik, 32)) {
            Instant t0 = q.start;
            double t = Duration.between(t0, zeit).toMillis() / 1000.0;
            Blick b = blick.kopie().groesse(breite, hoehe);
            A.teil("2 Nässe im Gelände aus surf.wet (" + ar.getName() + ", " + BildCheck.zeit(t0, t) + ")");
            if (q.atmosGitter() == null || q.luftGitter() == null) throw new IllegalStateException(ar + ": kein atmos im Archiv (atmos_<Gitter>, luft_<Gitter>s)");
            // ein Blick aufs Watt: von der Westspitze über den Japsand
            double westH = sz.gelaende.gelaende.hoehe(BildCheck.WEST_OST, BildCheck.WEST_NORD, 1) + 1.7;
            Blick bw = new Blick(BildCheck.WEST_OST, BildCheck.WEST_NORD, westH, BildCheck.WEST_RICHTUNG, -2, 55).groesse(breite, hoehe);
            float[] mitAtmos = bild(sz, bw, t0, t);
            float[] ausAtmos = sz.aus.lies();
            Gitter ng = sz.bild.atmosGitter();
            float[] wet = sz.bild.naesse(), eins = new float[ng.zellen()], null0 = new float[ng.zellen()];
            Arrays.fill(eins, 1f);
            int trocken = 0, land = 0;
            double summe = 0;
            for (float v : wet) if (v < 0.999f) {
                land++;
                summe += v;
                if (v < 0.5f) trocken++;
            }
            sz.gelaende.nassAlsSaettigung = true;
            sz.gelaende.naesse(ng, eins);
            float[] satt = bild(sz, bw, t0, t);
            sz.gelaende.nassAlsSaettigung = false;
            sz.gelaende.naesse(ng, null0);
            float[] alter = bild(sz, bw, t0, t);
            float[] ausAlt = sz.aus.lies();
            double[] v9 = vergleich(satt, alter, 1e-3), vAtm = vergleich(mitAtmos, alter, 0.02);
            A.pruefe(v9[1] <= 2e-3, "Prüfstein 9: Gelände mit surf.wet = 1 gegen dasselbe mit water.wetAge = 0, größter Unterschied je Kanal (HDR in float16)", f("%.2e", v9[1]), "≤ 2 · 10⁻³ (gleich)");
            A.info("surf.wet im Bild", f("%d von %d Zellen nicht satt (im Mittel %.2f), davon %d unter ½; im Blick von der Westspitze ändern sich %.1f %% der Bildpunkte um mehr als 2 %% gegen überall nass",
                    land, wet.length, land > 0 ? summe / land : 1, trocken, 100 * vAtm[0]));
            // zurück zur Nässe des Archivs
            sz.gelaende.nassAlsSaettigung = true;
            sz.gelaende.naesse(ng, wet);

            A.teil("3 Kimmung im Gebiet (" + b + ")");
            sz.kimmung.an = false;
            float[] gerade = bild(sz, b, t0, t);
            float[] tiefe = sz.rahmen.ziel().tiefe.lies();
            float[] ausGerade = sz.aus.lies();
            sz.kimmung.an = true;
            float[] mit = bild(sz, b, t0, t);
            float[] ausMit = sz.aus.lies();
            Kimmung.Tafel tf = sz.kimmung.tafel();
            if (tf == null) throw new IllegalStateException("Die Kimmung hat keine Tafel: " + sz.bild.kimmSaeulen + " Säulen, Fläche " + sz.bild.kimmFlaeche);
            double[] hoehen = sz.bild.kimmHoehen, n = sz.bild.kimmBrechung;
            Profil pr = new Profil();
            System.arraycopy(n, 0, pr.r, 0, n.length);
            double grenz = KimmungCheck.grenzwinkelFrei(pr, tf.auge, tf.sperr), normal = Math.sqrt(2 * tf.auge / Erde.R_OPTISCH);
            A.info("Luft im Blickfeld", f("%d Säulen über Wasser; Fläche %+.2f m NHN, Auge %.2f m darüber; Sperrhöhe %.2f m; Grenzwinkel %.2f′ gegen %.2f′ in der Normalatmosphäre des Bilds; Tafel in %.3f s, größtes δ %.2f′",
                    sz.bild.kimmSaeulen, sz.bild.kimmFlaeche, tf.auge, tf.sperr, grenz * MIN, normal * MIN, sz.kimmung.tafelSekunden, tf.groesstes() * MIN));
            float[] soll = Kimmung.durchgang(tf, b, gerade, tiefe, breite, hoehe);
            double[] v = vergleich(mit, soll, 0.02), geaendert = vergleich(mit, gerade, 0.02);
            A.hoechstens("Kernel gegen den Prozessor am gezeichneten Bild: Bildpunkte, die um mehr als 2 % abweichen", 100 * v[0], 1.0, "%");
            A.info("Durchgang", f("ändert %.1f %% der Bildpunkte um mehr als 2 %%", 100 * geaendert[0]));
            Kimmung.Geometrie geo = new Kimmung.Geometrie(b, breite, hoehe);
            int[] zeilen = geo.streifen(tf.tanHalb);
            double jeMinute = hoehe / (2 * Math.tan(Math.toRadians(b.bildwinkel) / 2)) / MIN;
            double[] km = kimm(gerade, mit, tiefe, tf, b, grenz, normal);
            A.info("Kimm in diesem Blick", km[0] == 0 ? "in keiner Spalte reicht freies Wasser bis zur Kimm" : f("%d Spalten mit freiem Wasser bis zur Kimm: gerade in Zeile %.1f (Soll %.1f), mit Kimmung in Zeile %.1f (Soll %.1f)",
                    (int) km[0], km[1], km[3], km[2], km[4]));
            // derselbe Ort, Blick in die Gegenrichtung über freies Wasser: dort misst sich die Kimm
            Blick bf = b.kopie();
            bf.richtung = (b.richtung + 180) % 360;
            sz.kimmung.an = false;
            float[] fGerade = bild(sz, bf, t0, t);
            float[] fTiefe = sz.rahmen.ziel().tiefe.lies();
            sz.kimmung.an = true;
            float[] fMit = bild(sz, bf, t0, t);
            Kimmung.Tafel tff = sz.kimmung.tafel();
            if (tff != null) {
                Profil prf = new Profil();
                System.arraycopy(sz.bild.kimmBrechung, 0, prf.r, 0, prf.r.length);
                double gf = KimmungCheck.grenzwinkelFrei(prf, tff.auge, tff.sperr), nf = Math.sqrt(2 * tff.auge / Erde.R_OPTISCH);
                double[] kf = kimm(fGerade, fMit, fTiefe, tff, bf, gf, nf);
                if (kf[0] > 0) {
                    A.hoechstens(f("Prüfstein 12 im Gebiet: Kimm über freiem Wasser (Blick nach %.0f°) gegen den Grenzwinkel %.2f′, Mittel über %d Spalten (%.1f Zeilen je ′)", bf.richtung, gf * MIN, (int) kf[0],
                            jeMinute), Math.abs(kf[2] - kf[4]), 1.0, "Zeilen");
                    A.info("Kimm über freiem Wasser", f("gerade in Zeile %.1f (Soll %.1f), mit Kimmung in Zeile %.1f (Soll %.1f): %.1f Zeilen = %.2f′ tiefer; in %d von %d Spalten auf 1,5 Zeilen am Soll",
                            kf[1], kf[3], kf[2], kf[4], kf[2] - kf[1], (kf[2] - kf[1]) / jeMinute, (int) kf[5], (int) kf[0]));
                } else A.info("Kimm über freiem Wasser", f("auch im Blick nach %.0f° reicht in keiner Spalte freies Wasser bis zur Kimm", bf.richtung));
            }
            // zurück zum Blick der Ansicht
            bild(sz, b, t0, t);
            statusBild(name, ausGerade, ausMit, ausAlt, ausAtmos, zeilen);

            A.teil("4 Kosten (Prüfstein 14)");
            // (a) der Durchgang je Bild bei stehendem Stand
            for (int i = 0; i < 40; i++) {
                sz.zeichne(b, t0, t, uhr);
                uhr.sammle(false);
            }
            uhr.sammle(true);
            double msK = uhr.ms("Kimmung") + uhr.ms("Kimmung, Kopie");
            StringBuilder d = new StringBuilder();
            for (String s : uhr.abschnitte()) if (uhr.ms(s) >= 0.05) d.append(f("%s %.2f; ", s, uhr.ms(s)));
            if (!uhr.verfuegbar) A.info("Prüfstein 14: Zeit des Durchgangs", "keine Stoppuhr auf diesem Gerät");
            else A.hoechstens(f("Prüfstein 14: Durchgang der Kimmung je Bild (%d × %d, Kopie und Kimmung)", breite, hoehe), msK, 2.0, "ms");
            A.info("Zeit je Durchgang (Stoppuhr, ms)", d.toString());
            // (b) die Tafel
            A.hoechstens("Prüfstein 14: die Tafel rechnen", sz.kimmung.tafelSekunden, 0.3, "s");
            // (c) über wechselnde Stände: je Bild 30 s Szenenzeit, abwechselnd ohne und mit Kimmung, zwei Runden. Die erste Runde
            // liest die Stände zum ersten Mal von der Platte; gewertet wird die zweite.
            double[][][] zeiten = new double[2][2][];
            int tafeln = 0;
            for (int runde = 0; runde < 2; runde++)
                for (int mitK = 0; mitK < 2; mitK++) {
                    sz.kimmung.an = mitK == 1;
                    int vorher = sz.kimmung.tafeln, bilder = 120;
                    double[] ms = new double[bilder];
                    for (int i = -10; i < bilder; i++) {
                        long n0 = System.nanoTime();
                        sz.zeichne(b, t0, t + 30.0 * Math.max(0, i), null);
                        if (i >= 0) ms[i] = (System.nanoTime() - n0) / 1e6;
                    }
                    zeiten[runde][mitK] = ms;
                    if (mitK == 1) tafeln = sz.kimmung.tafeln - vorher;
                }
            sz.kimmung.an = true;
            double[] st = statistik(zeiten[1][1]), so = statistik(zeiten[1][0]), st1 = statistik(zeiten[0][1]), so1 = statistik(zeiten[0][0]);
            A.pruefe(st[2] <= 2 * st[0] || st[2] <= so[2] * 1.2, "Prüfstein 14: Bilder über wechselnde Stände (120 Bilder über 1 h Szenenzeit, zweite Runde), längstes Bild gegen das Mittel",
                    f("mit Kimmung Mittel %.1f ms, Median %.1f, 95 %% ≤ %.1f, längstes %.1f (%d Tafeln neu); ohne Kimmung Mittel %.1f, Median %.1f, 95 %% ≤ %.1f, längstes %.1f", st[0], st[1], st[3], st[2], tafeln,
                            so[0], so[1], so[3], so[2]), "längstes ≤ 2 × Mittel, oder nicht länger als ohne Kimmung (+20 %) [G]");
            A.info("Erste Runde (die Stände kommen zum ersten Mal von der Platte)", f("ohne Kimmung Mittel %.1f ms, längstes %.1f; mit Kimmung Mittel %.1f ms, längstes %.1f", so1[0], so1[2], st1[0], st1[2]));
        }
    }

    /**
     * Die Kimm über freiem Wasser: je Spalte (jede 16.) im geraden Bild die oberste Zeile unter dem Himmel; gewertet, wo sie
     * an der Kimm der Normalatmosphäre liegt (sonst steht Gelände davor). Mit Kimmung: die oberste Zeile, ab der drei
     * Zeilen dunkler (oder heller) sind als die Mitte zwischen Himmel und Wasser des geraden Bilds. Gibt {Spalten, Zeile
     * gerade, Zeile mit Kimmung, Soll gerade, Soll mit Kimmung, Spalten auf 1,5 Zeilen am Soll}, Zeilen im Mittel.
     */
    static double[] kimm(float[] gerade, float[] mit, float[] tiefe, Kimmung.Tafel tf, Blick b, double grenz, double normal) {
        Kimmung.Geometrie geo = new Kimmung.Geometrie(b, breite, hoehe);
        int[] zeilen = geo.streifen(tf.tanHalb);
        int spalten = 0, innerhalb = 0;
        double sumGerade = 0, sumMit = 0, sollMit = 0, sollGerade = 0;
        for (int px = 8; px < breite; px += 16) {
            int kg = -1;
            for (int py = zeilen[0]; py <= zeilen[1]; py++) if (tiefe[py * breite + px] > 0) {
                kg = py;
                break;
            }
            if (kg < 4 || kg > hoehe - 4) continue;
            double zs = geo.zeile(px, -Math.tan(normal)) + 0.5;
            if (Math.abs(kg - zs) > 1.5) continue;
            double himmel = lum(gerade, (kg - 3) * breite + px), wasser = lum(gerade, (kg + 2) * breite + px), schwelle = 0.5 * (himmel + wasser), vz = Math.signum(wasser - himmel);
            if (vz == 0) continue;
            int km = -1;
            for (int py = kg - 2; py <= zeilen[1] - 3; py++) {
                boolean alle = true;
                for (int d = 0; d < 3 && alle; d++) if ((lum(mit, (py + d) * breite + px) - schwelle) * vz <= 0) alle = false;
                if (alle) {
                    km = py;
                    break;
                }
            }
            if (km < 0) continue;
            double s = geo.zeile(px, -Math.tan(grenz)) + 0.5;
            spalten++;
            sumGerade += kg;
            sumMit += km;
            sollMit += s;
            sollGerade += zs;
            if (Math.abs(km - s) <= 1.5) innerhalb++;
        }
        if (spalten == 0) return new double[6];
        return new double[] {spalten, sumGerade / spalten, sumMit / spalten, sollGerade / spalten, sollMit / spalten, innerhalb};
    }

    static double lum(float[] hdr, int k) { return 0.2126 * hdr[4 * k] + 0.7152 * hdr[4 * k + 1] + 0.0722 * hdr[4 * k + 2]; }

    /** Mittel, Median, Größtes, 95. Perzentil. */
    static double[] statistik(double[] ms) {
        double[] m = ms.clone();
        Arrays.sort(m);
        double s = 0;
        for (double v : m) s += v;
        return new double[] {s / m.length, m[m.length / 2], m[m.length - 1], m[(int) (0.95 * (m.length - 1))]};
    }

    /**
     * Status-Bild für die Sichtprüfung: oben das Bild mit Kimmung; darunter der Streifen um den Horizont aus der Bildmitte
     * dreifach vergrößert, links gerade, rechts mit Kimmung; unten der Blick von der Westspitze mit der Nässe aus
     * water.wetAge = 0 (überall nass) und aus surf.wet.
     */
    static void statusBild(String name, float[] gerade, float[] mit, float[] nassAlt, float[] nassAtmos, int[] zeilen) throws Exception {
        BufferedImage ig = ausgabe(gerade, breite, hoehe), im = ausgabe(mit, breite, hoehe), ia = ausgabe(nassAlt, breite, hoehe), in = ausgabe(nassAtmos, breite, hoehe);
        int W = breite / 2, H = hoehe / 2, z = 3;
        int y0 = Math.max(0, zeilen[0]), hs = Math.min(hoehe, zeilen[1] + 1) - y0;
        // aus dem Streifen ein Ausschnitt, der dreifach vergrößert in die halbe Breite passt
        int aw = W / z, ah = Math.min(hs, H / z), ax = breite / 2 - aw / 2, ay = y0 + (hs - ah) / 2;
        BufferedImage alle = new BufferedImage(2 * W, 3 * H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = alle.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(ig, 0, 0, W, H, null);
        g2.drawImage(im, W, 0, W, H, null);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(ig.getSubimage(ax, ay, aw, ah), 0, H, aw * z, ah * z, null);
        g2.drawImage(im.getSubimage(ax, ay, aw, ah), W, H, aw * z, ah * z, null);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(ia, 0, 2 * H, W, H, null);
        g2.drawImage(in, W, 2 * H, W, H, null);
        g2.setColor(Color.WHITE);
        String[] txt = {"gerade (ohne Kimmung)", "mit Kimmung", "Streifen um den Horizont, dreifach: gerade", "Streifen, dreifach: mit Kimmung", "Westspitze, überall nass (wetAge = 0)",
                "Westspitze, Nässe aus surf.wet"};
        for (int k = 0; k < 6; k++) g2.drawString(txt[k], (k % 2) * W + 8, (k / 2) * H + 16);
        g2.dispose();
        File png = new File("docs" + File.separator + "status" + File.separator + name + ".png");
        png.getParentFile().mkdirs();
        ImageIO.write(alle, "png", png);
        A.info("Status-Bild (Sichtprüfung)", png.getPath() + f(", %d × %d", alle.getWidth(), alle.getHeight()));
    }
}
