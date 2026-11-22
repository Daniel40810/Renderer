package com.dan.lauf.demo;

import com.dan.core.astro.Gestirne;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.pruef.Attest;
import com.dan.core.pruef.Bildvergleich;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Scattering;
import com.dan.core.szene.Spiegelbild;
import com.dan.lauf.Archivquelle;
import com.dan.render.frame.Belichtung;
import com.dan.render.frame.Filmkurve;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Gelaendeszene;
import com.dan.render.reference.Luftstrahl;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.Schranke;
import com.dan.render.reference.Szene;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Referenz gegen das Echtzeitbild (P7c, Werkbuch HalligHooge Teil A, 12.8): fünf Ansichten aus dem Archiv, je
 * Ansicht das Echtzeitbild ({@link BildCheck.Szene}, {@code --faktor} mal so groß, auf die Größe der Referenz gemittelt)
 * und die Referenz ({@link Referenz} über einer {@link Gelaendeszene} derselben Stände), beide durch dieselbe Belichtung,
 * dieselben Stäbchen und dieselbe Filmkurve. Prüfsteine 6–10 von P7.
 * <p>
 * Ansichten: Westspitze bei Niedrigwasser (ruhiger Tag) und bei Hochwasser (Sturm), Vogelblick im Landunter, Abendsonne
 * (ruhiger Tag) und nah auf das Wasser bei Hochwasser (Sturm). Die Zeiten wie {@link BildCheck}; ein Archiv mit nur zwei
 * Ständen (der Auszug für P7c) gibt sie der Reihe nach: Sturm Hochwasser, Landunter; ruhiger Tag Niedrigwasser, Abend.
 * <p>
 * Je Ansicht dazu zur Auskunft: die Zerlegung nach Himmel, Gelände und Wasser (ΔE₀₀, Rauschen, Leuchtdichte und Kanäle
 * Echtzeit / Referenz), dieselben Maße in Blöcken zu {@link #BLOCK} × {@link #BLOCK} Bildpunkten und, wo Wasser im Bild
 * ist, die Wassersäule für F55 (ein Viertel der Pfade ohne Säule).
 * <p>
 * Optionen: {@code --gebiet} (Gelände), {@code --gitter} (Gebiet der Gitter, Vorgabe wie --gebiet), {@code --archiv},
 * {@code --archiv-ruhig} (Vorgabe {@code _p7/archiv} und {@code _p7/archiv_ruhig} neben dem Gebiet), {@code --breite},
 * {@code --hoehe} (Referenz, Vorgabe 480 × 270), {@code --faktor} (Echtzeit, Vorgabe 4), {@code --pfade} (je Bildpunkt,
 * Vorgabe 256; mit {@code --bis-rauschen x} in Runden dieser Größe, bis das Rauschen in Blöcken höchstens x ist, höchstens
 * {@code --pfade-max}, Vorgabe 8 192), {@code --bahnen} (Wellenlängen je Pfad, Vorgabe 16), {@code --dichte}, {@code --keule}, {@code --nur}
 * (Ansichten, z. B. 125), {@code --rgb} (drei Kanäle statt spektral), {@code --ohne-attest}. Braucht rund 2 GB [S]
 * Speicher (-Xmx4g).
 */
public final class ReferenzCheck {
    private static final Attest A = new Attest("com.dan.render.reference", Referenz.VERSION);
    static String gebiet = "D:\\Daten\\HalligHooge\\gebiet", gitter = null, archiv = null, archivRuhig = null, nur = "12345", name = "P7_Referenz";
    static int breite = 480, hoehe = 270, faktor = 4, pfade = 256, bahnen = 16;
    static double dichte = 1, keule = 0.25;
    /** Mit --bis-rauschen: Runden zu --pfade, bis das Rauschen in Blöcken die Grenze hält, höchstens --pfade-max je Bildpunkt. */
    static double bisRauschen = 0;
    static int pfadeMax = 8192;
    static boolean spektral = true;
    /**
     * Seit P10b (Prüfstein 13): mit {@code --kimmung <archiv>} nur eine Ansicht aus diesem Archiv (mit atmos), zur Zeit
     * {@code --kimmung-zeit} und mit dem Blick {@code --kimmung-blick Ost,Nord,Höhe m NHN,Richtung,Neigung,Bildwinkel}:
     * das Echtzeitbild mit der Kimmung gegen die Referenz mit gekrümmten Sehstrahlen ({@link Luftstrahl}).
     */
    static String kimmungArchiv;
    static Instant kimmungZeit = Instant.parse("2026-10-18T08:00:00Z");
    static double[] kimmungBlick = {462_600, 6_046_900, 4, 325, 0, 5.6};
    /**
     * Seit P10d (S8, F102 A): mit {@code --sediment <archiv>} nur die Ansicht mit Sediment („Prielmündung im Ebbstrom“,
     * {@code --sediment-zeit}, {@code --sediment-blick}, Schwebstoff der Szene {@code --sediment-schwebstoff}), zweimal:
     * mit Trübung am Ort (Echtzeit und Referenz) und mit überall gleicher Trübung (ein Viertel der Pfade); Prüfstein 17.
     */
    static String sedimentArchiv;
    static Instant sedimentZeit = Instant.parse("2026-10-25T15:00:00Z");
    static double[] sedimentBlick = {466_600, 6_043_700, 500, 350, -30, 60};
    static double sedimentSchwebstoff = 20;
    /** Seit P10d: „Säule und Grund, Echtzeit durch Referenz“ (P7g) der letzten Ansicht mit Wasser; NaN ohne. */
    static double letzteSaeule = Double.NaN;
    /** Suchweite unter Wasser (m) [G]. */
    static double wasserWeite = 5;
    /** ΔE₀₀, ab dem ein Bildpunkt als abweichend zählt (Auskunft), und Grenze des Mittels (Prüfstein 8). */
    static final double GRENZE = 3, RAUSCHEN = 1;
    /** Blöcke für die Auskunft (Bildpunkte je Seite) [G]: Mittel in linearem Licht, dann dieselbe Abbildung. */
    static final int BLOCK = 8;

    /** Eine Ansicht: Name, Szene, Start des Archivs, Zeit, Blick. */
    record Ansicht(String name, BildCheck.Szene sz, Instant t0, double t, Blick blick) { }

    public static void main(String[] args) throws Exception {
        boolean attest = !List.of(args).contains("--ohne-attest");
        for (int i = 0; i < args.length; i++) {
            String a = args[i], v = i + 1 < args.length ? args[i + 1] : null;
            switch (a) {
                case "--gebiet" -> gebiet = v;
                case "--gitter" -> gitter = v;
                case "--archiv" -> archiv = v;
                case "--archiv-ruhig" -> archivRuhig = v;
                case "--breite" -> breite = Integer.parseInt(v);
                case "--hoehe" -> hoehe = Integer.parseInt(v);
                case "--faktor" -> faktor = Integer.parseInt(v);
                case "--pfade" -> pfade = Integer.parseInt(v);
                case "--nur" -> nur = v;
                case "--name" -> name = v;
                case "--rgb" -> spektral = false;
                case "--bahnen" -> bahnen = Integer.parseInt(v);
                case "--dichte" -> dichte = Double.parseDouble(v);
                case "--keule" -> keule = Double.parseDouble(v);
                case "--bis-rauschen" -> bisRauschen = Double.parseDouble(v);
                case "--pfade-max" -> pfadeMax = Integer.parseInt(v);
                case "--kimmung" -> kimmungArchiv = v;
                case "--sediment" -> sedimentArchiv = v;
                case "--sediment-zeit" -> sedimentZeit = Instant.parse(v);
                case "--sediment-schwebstoff" -> sedimentSchwebstoff = Double.parseDouble(v);
                case "--sediment-blick" -> {
                    String[] t = v.split(",");
                    sedimentBlick = new double[t.length];
                    for (int k = 0; k < t.length; k++) sedimentBlick[k] = Double.parseDouble(t[k].strip());
                }
                case "--kimmung-zeit" -> kimmungZeit = Instant.parse(v);
                case "--kimmung-blick" -> {
                    String[] t = v.split(",");
                    kimmungBlick = new double[t.length];
                    for (int k = 0; k < t.length; k++) kimmungBlick[k] = Double.parseDouble(t[k].strip());
                }
                default -> { }
            }
        }
        File gb = new File(gebiet), gg = gitter != null ? new File(gitter) : gb;
        File daten = gb.getAbsoluteFile().getParentFile();
        File ar = archiv != null ? new File(archiv) : new File(daten, "_p7" + File.separator + "archiv");
        File arR = archivRuhig != null ? new File(archivRuhig) : new File(ar.getAbsoluteFile().getParentFile(), "archiv_ruhig");
        System.out.println("ReferenzCheck – com.dan.render.reference " + Referenz.VERSION + " (Werkbuch HalligHooge Teil A, P7c)");
        A.info("Gebiet", gb.getPath() + (gg != gb ? "; Gitter aus " + gg.getPath() : "") + "; Archiv " + ar.getPath() + "; ruhiger Tag " + arR.getPath());
        A.info("Bilder", f("Referenz %d × %d, %d Pfade je Bildpunkt, %s, Dichte der Stöße %.1f, Keule %.2f; Echtzeit %d × %d, gemittelt je %d × %d; %d Kerne",
                breite, hoehe, pfade, spektral ? "spektral (Heldenwellenlänge, " + bahnen + " Bahnen)" : "drei Kanäle", dichte, keule, breite * faktor, hoehe * faktor, faktor, faktor,
                Runtime.getRuntime().availableProcessors()));
        try {
            Gebiet ge = Gebiet.lies(gg);
            try (Geraet g = new Geraet()) {
                A.info("Gerät", g.name + " (" + g.treiber + ")");
                Konto konto = g.konto("ReferenzCheck", 3L << 30);
                if (kimmungArchiv != null) kimmung(g, konto, gb, ge, new File(kimmungArchiv));
                else if (sedimentArchiv != null) sediment(g, konto, gb, ge, new File(sedimentArchiv));
                else lauf(g, konto, gb, ge, ar, arR);
                A.teil("Aufräumen");
                A.pruefe(konto.belegt() == 0, "Speicherkonto", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", name + ".md"), name.startsWith("P7") ? "P7c – ReferenzCheck" : name.replace('_', ' ') + " – ReferenzCheck (mit Seegang, Stufe 2)");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void lauf(Geraet g, Konto konto, File gb, Gebiet ge, File ar, File arR) throws Exception {
        int W = breite * faktor, H = hoehe * faktor;
        try (Archivquelle q = new Archivquelle(ar.toPath()); Archivquelle qR = new Archivquelle(arR.toPath());
             BildCheck.Szene sz = new BildCheck.Szene(g, konto, gb, ge, q, W, H); BildCheck.Szene szR = new BildCheck.Szene(g, konto, gb, ge, qR, W, H)) {
            A.teil("1 Szene");
            double[] zs = q.zeiten(), zr = qR.zeiten();
            double hw, lu, nw, ab;
            if (zs.length >= 3 && zr.length >= 3) {
                BildCheck.Zeiten s = BildCheck.zeiten(q, ge), r = BildCheck.zeiten(qR, ge);
                hw = s.z()[s.iHW()];
                lu = s.z()[s.iLU()];
                nw = r.z()[r.iNW()];
                ab = r.z()[r.iAb()];
            } else {
                hw = zs[0];
                lu = zs[zs.length - 1];
                nw = zr[0];
                ab = zr[zr.length - 1];
            }
            A.info("Zeiten", f("Sturm: Hochwasser %s, Landunter %s; ruhiger Tag: Niedrigwasser %s, Abend %s",
                    BildCheck.zeit(q.start, hw), BildCheck.zeit(q.start, lu), BildCheck.zeit(qR.start, nw), BildCheck.zeit(qR.start, ab)));
            Gelaende gel = sz.gelaende.gelaende;
            double westH = gel.hoehe(BildCheck.WEST_OST, BildCheck.WEST_NORD, 1) + 1.7, swH = gel.hoehe(BildCheck.SW_OST, BildCheck.SW_NORD, 1) + 1.7;
            double azAb = Gestirne.bei(qR.start.plusMillis(Math.round(ab * 1000))).sonne(BildCheck.LON, BildCheck.LAT).azimutGrad();
            List<Ansicht> av = new ArrayList<>();
            av.add(new Ansicht("Westspitze, Niedrigwasser (ruhiger Tag)", szR, qR.start, nw, new Blick(BildCheck.WEST_OST, BildCheck.WEST_NORD, westH, BildCheck.WEST_RICHTUNG, -2, 55)));
            av.add(new Ansicht("Westspitze, Hochwasser (Sturm)", sz, q.start, hw, new Blick(BildCheck.WEST_OST, BildCheck.WEST_NORD, westH, BildCheck.WEST_RICHTUNG, -2, 55)));
            av.add(new Ansicht("Vogelblick, Landunter (Sturm)", sz, q.start, lu, new Blick(470_400, 6_044_300, 300, 20, -30, 60)));
            av.add(new Ansicht("Abendsonne, Südwestdeich (ruhiger Tag)", szR, qR.start, ab, new Blick(BildCheck.SW_OST, BildCheck.SW_NORD, swH, azAb, -1, 55)));
            av.add(new Ansicht("Nah auf das Wasser, Hochwasser (Sturm)", sz, q.start, hw, new Blick(BildCheck.WEST_OST, BildCheck.WEST_NORD, westH, BildCheck.WEST_RICHTUNG, -12, 40)));
            // P8c (F77 A): vor dem Japsand im Sturm, wo die langen Wellen des Seegangs am größten sind (Ansicht „brandung“)
            av.add(new Ansicht("Brandung vor dem Japsand, Hochwasser (Sturm, Seegang)", sz, q.start, hw, new Blick(466_300, 6_047_300, 8, 265, -3, 55)));
            Schranke sch = new Schranke(gel, 470_000, 6_046_000);
            A.info("Schranke des Geländes", sch + f("; höchste %.2f m NHN", sch.hoechste()));
            List<BufferedImage[]> reihen = new ArrayList<>();
            for (int n = 0; n < av.size(); n++) {
                if (!nur.contains(String.valueOf(n + 1))) continue;
                reihen.add(ansicht(n + 1, av.get(n), sch));
            }
            statusBild(reihen);
        }
    }

    /** Seit P10b: die eine Ansicht der Kimmung aus einem Archiv mit atmos. */
    static void kimmung(Geraet g, Konto konto, File gb, Gebiet ge, File ar) throws Exception {
        int W = breite * faktor, H = hoehe * faktor;
        try (Archivquelle q = new Archivquelle(ar.toPath()); BildCheck.Szene sz = new BildCheck.Szene(g, konto, gb, ge, q, W, H)) {
            A.teil("1 Szene");
            if (q.luftGitter() == null) throw new IllegalStateException(ar + ": keine Luft im Archiv (luft_<Gitter>s; Nachlauf von atmos fehlt)");
            double t = java.time.Duration.between(q.start, kimmungZeit).toMillis() / 1000.0;
            double[] kb = kimmungBlick;
            A.info("Archiv", f("%s; Zeit %s; Blick O %.0f, N %.0f, %.1f m NHN, Richtung %.1f°, Neigung %.2f°, Bildwinkel %.1f°", ar, kimmungZeit, kb[0], kb[1], kb[2], kb[3], kb[4], kb[5]));
            Schranke sch = new Schranke(sz.gelaende.gelaende, 470_000, 6_046_000);
            A.info("Schranke des Geländes", sch + f("; höchste %.2f m NHN", sch.hoechste()));
            List<BufferedImage[]> reihen = new ArrayList<>();
            reihen.add(ansicht(7, new Ansicht("Fernglas, Kimmung", sz, q.start, t, new Blick(kb[0], kb[1], kb[2], kb[3], kb[4], kb[5])), sch));
            statusBild(reihen);
        }
    }

    /**
     * Seit P10d (S8, F102 A, Prüfstein 17): die Ansicht mit Sediment aus einem Archiv mit {@code sed.c}, zweimal – mit
     * Trübung am Ort in Echtzeit und Referenz, dann mit überall gleicher Trübung (Schwebstoff der Szene) und einem Viertel
     * der Pfade. Prüfstein: „Säule und Grund, Echtzeit durch Referenz“ unterscheidet sich zwischen beiden um höchstens 5 %.
     */
    static void sediment(Geraet g, Konto konto, File gb, Gebiet ge, File ar) throws Exception {
        int W = breite * faktor, H = hoehe * faktor;
        try (Archivquelle q = new Archivquelle(ar.toPath()); BildCheck.Szene sz = new BildCheck.Szene(g, konto, gb, ge, q, W, H)) {
            A.teil("1 Szene");
            double t = java.time.Duration.between(q.start, sedimentZeit).toMillis() / 1000.0;
            double[] kb = sedimentBlick;
            sz.wasser.optik.schwebstoff = sedimentSchwebstoff;
            A.info("Archiv", f("%s; Zeit %s; Blick O %.0f, N %.0f, %.1f m NHN, Richtung %.1f°, Neigung %.2f°, Bildwinkel %.1f°; Schwebstoff der Szene %.0f g/m³", ar, sedimentZeit,
                    kb[0], kb[1], kb[2], kb[3], kb[4], kb[5], sedimentSchwebstoff));
            Schranke sch = new Schranke(sz.gelaende.gelaende, 470_000, 6_046_000);
            Blick b = new Blick(kb[0], kb[1], kb[2], kb[3], kb[4], kb[5]);
            List<BufferedImage[]> reihen = new ArrayList<>();
            reihen.add(ansicht(8, new Ansicht("Prielmündung im Ebbstrom, Trübung am Ort", sz, q.start, t, b), sch));
            double mitOrt = letzteSaeule;
            int pf = pfade;
            pfade = Math.max(16, pfade / 4);
            sz.wasser.truebung = false;
            letzteSaeule = Double.NaN;
            reihen.add(ansicht(9, new Ansicht("Prielmündung im Ebbstrom, Trübung überall gleich", sz, q.start, t, b), sch));
            double gleich = letzteSaeule;
            sz.wasser.truebung = true;
            pfade = pf;
            A.teil("Prüfstein 17 (S8)");
            A.pruefe(Math.abs(mitOrt / gleich - 1) <= 0.05, "Prüfstein 17: Säule und Grund, Echtzeit durch Referenz, mit Trübung am Ort gegen überall gleich",
                    f("%.3f gegen %.3f (%+.1f %%)", mitOrt, gleich, 100 * (mitOrt / gleich - 1)), "Unterschied ≤ 5 % [G]");
            statusBild(reihen);
        }
    }

    /** Mittel über je bw Bildpunkte längs der Zeile, für die Zeilen y0 … y0 + zeilen − 1: [Kanal][Stück + (breite / bw) · Zeile]. */
    static double[][] zeilenBlock(double[][] l, int bw, int y0, int zeilen) {
        int nb = breite / bw;
        double[][] o = new double[3][nb * zeilen];
        for (int c = 0; c < 3; c++)
            for (int y = 0; y < zeilen; y++)
                for (int x = 0; x < nb * bw; x++) o[c][y * nb + x / bw] += l[c][(y0 + y) * breite + x] / bw;
        return o;
    }

    /** Zeile (ab 0) mit dem größten Sprung der Leuchtdichte zur nächsten im Stück k der Zeilenmittel aus {@link #zeilenBlock}. */
    static int kante(double[][] z, int nb, int zeilen, int k) {
        int beste = 0;
        double groesst = -1;
        for (int y = 0; y + 1 < zeilen; y++) {
            double a = lum(z, y * nb + k), b = lum(z, (y + 1) * nb + k), d = Math.abs(Math.log(Math.max(1e-6, a)) - Math.log(Math.max(1e-6, b)));
            if (d > groesst) {
                groesst = d;
                beste = y;
            }
        }
        return beste;
    }

    /** Eine Ansicht: Echtzeitbild, Referenz, Prüfsteine 6–9; gibt {Referenz, Echtzeit, Karte des ΔE₀₀} mit Beschriftung. */
    static BufferedImage[] ansicht(int nr, Ansicht an, Schranke sch) {
        A.teil(nr + " " + an.name() + " (" + BildCheck.zeit(an.t0(), an.t()) + ")");
        BildCheck.Szene sz = an.sz();
        int W = breite * faktor, H = hoehe * faktor;
        Blick b = an.blick().kopie().groesse(W, H);
        // Echtzeit: belichtet wie ein Standbild
        for (int k = 0; k < 12; k++) {
            sz.rahmen.belichtung.sofort();
            sz.zeichne(b, an.t0(), an.t(), null);
        }
        double vorb = sz.rahmen.ziel().vorbelichtung, F = sz.rahmen.belichtung.faktor();
        double[][] echt = mittle(sz.rahmen.ziel().hdr.lies(), W, vorb);
        Abbild abb = new Abbild(sz, F);
        A.info("Echtzeit", f("Belichtung EV %.2f (Faktor %.4g), Wind am Auge %.1f m/s", Math.log(F) / Math.log(2), F, Math.hypot(sz.bild.windU, sz.bild.windV)));
        // P7f (F60 A): das Wasser des Echtzeitbilds in seinen Teilen, jeder für sich gezeichnet (Wasserbild.teil)
        double[][][] teile = new double[5][][];
        for (int k = 1; k <= 5; k++) {
            sz.wasser.teil = k;
            sz.zeichne(b, an.t0(), an.t(), null);
            teile[k - 1] = mittle(sz.rahmen.ziel().hdr.lies(), W, sz.rahmen.ziel().vorbelichtung);
        }
        sz.wasser.teil = 0;
        // Referenz über denselben Ständen – seit P11b gebaut wie das Foto (Foto.referenz: Seegang, Sediment, Kimmung)
        Instant zeit = an.t0().plusMillis(Math.round(an.t() * 1000));
        Referenz ref = com.dan.lauf.Foto.referenz(sz.gelaende, sz.wasser, sz.himmel, sz.kimmung, sz.bild, b, zeit, sch);
        Gelaendeszene gs = (Gelaendeszene) ref.szene;
        if (gs.seegang != null) {
            double[] v = com.dan.core.szene.Seegangsoptik.werte(sz.wasser.seeNetze(), sz.wasser.zeit, b.ost, b.nord);
            A.info("Seegang (Stufe 2, auch in der Referenz)", v == null ? "kein Netz am Auge" : f("am Auge Hs %.2f m, Tp %.1f s, mss %.4f, diss %.1f W/m²; %d Netze", v[0], v[1], v[4], v[5], sz.wasser.seeNetze().size()));
        }
        com.dan.core.szene.Bodennetz bn = sz.bild.boden();
        if (bn != null)
            A.info("Sediment (S8, auch in der Referenz)", f("Trübung am Ort %s, Boden mit bed.dz %s; am Auge %.1f g/m³ (Szene %.0f g/m³), größte Hebung %.3f m",
                    sz.wasser.truebung && bn.truebung.gesetzt() ? "ja" : "nein", sz.gelaende.bodenHoehe && bn.boden.gesetzt() ? "ja" : "nein",
                    bn.truebungAm(b.ost, b.nord, sz.wasser.zeit, sz.wasser.optik.schwebstoff, com.dan.core.szene.Bodennetz.RAND_ZELLEN), sz.wasser.optik.schwebstoff, bn.dzMax()));
        ref.spektral = spektral;
        ref.wasserWeite = wasserWeite;
        ref.saat = nr;
        ref.bahnen = bahnen;
        ref.dichte = dichte;
        ref.keule = keule;
        // seit P10b: Zeichnet das Echtzeitbild mit Kimmung, laufen die Sehstrahlen der Referenz gekrümmt durch dasselbe Profil
        com.dan.render.air.Kimmung.Tafel tf = sz.kimmung.tafel();
        double[][] echtGerade = null;
        if (ref.luftstrahl != null) {
            A.info("Kimmung (P10b)", f("Fläche %+.2f m NHN, Auge %.2f m darüber, Sperrhöhe %.2f m, %d Säulen im Blickfeld; Tafel in %.3f s, größtes δ %.2f′; Wege der Referenz in %.1f s",
                    sz.bild.kimmFlaeche, tf.auge, tf.sperr, sz.bild.kimmSaeulen, sz.kimmung.tafelSekunden, tf.groesstes() * 60 * 180 / Math.PI, ref.luftstrahl.sekunden));
            // dasselbe Echtzeitbild ohne Kimmung, zur Auskunft
            sz.kimmung.an = false;
            sz.zeichne(b, an.t0(), an.t(), null);
            echtGerade = mittle(sz.rahmen.ziel().hdr.lies(), W, sz.rahmen.ziel().vorbelichtung);
            sz.kimmung.an = true;
            sz.zeichne(b, an.t0(), an.t(), null);
        }
        // Prüfstein 6 und die Arten der Bildpunkte (Strahl durch die Mitte)
        int[] art = pruefstein6(b.kopie().groesse(breite, hoehe), gs, sz);
        // in Runden zu --pfade, mit --bis-rauschen so lange, bis das Rauschen in Blöcken die Grenze hält (F56 A)
        double[][][] hal = null;
        double sek = 0;
        long pf = 0;
        int runden = 0;
        double[] rb;
        while (true) {
            ref.saat = nr * 1000L + runden;
            double[][][] h = ref.bild(b, breite, hoehe, pfade);
            sek += ref.sekunden;
            pf += ref.pfade.get();
            if (hal == null) hal = h;
            else
                for (int k = 0; k < 2; k++)
                    for (int c = 0; c < 3; c++)
                        for (int i = 0; i < h[k][c].length; i++) hal[k][c][i] = (hal[k][c][i] * runden + h[k][c][i]) / (runden + 1);
            runden++;
            rb = deBlock(abb, block(hal[0]), block(hal[1]));
            if (bisRauschen <= 0) break;
            System.out.println(f("         Runde %d: %d Pfade je Bildpunkt, Rauschen in Blöcken %.2f", runden, runden * pfade, rb[0]));
            if (rb[0] <= bisRauschen || (long) (runden + 1) * pfade > pfadeMax) break;
        }
        double[][] mit = Referenz.mittel(hal);
        A.info("Prüfstein 9: Zeit der Referenz", f("%.1f s für %d Pfade (%d je Bildpunkt in %d Runden), %.0f Pfade/s (%.0f je Kern); %.1f min für die Ansicht; Schnitte %d, Proben %d (%.1f je Schnitt)",
                sek, pf, runden * pfade, runden, pf / sek, pf / sek / Runtime.getRuntime().availableProcessors(), sek / 60,
                gs.schnitte.sum(), gs.proben.sum(), gs.proben.sum() / (double) Math.max(1, gs.schnitte.sum())));
        A.info("Schnitt, Auskunft", f("seitlich in den Wasserkörper %d, Start unter dem Gelände %d", gs.waende.sum(), gs.startUnter.sum()));
        // Bilder durch dieselbe Abbildung
        BufferedImage iE = abb.bild(echt), iR = abb.bild(mit), i0 = abb.bild(hal[0]), i1 = abb.bild(hal[1]);
        Bildvergleich.Ergebnis rausch = Bildvergleich.vergleiche(i0, i1, GRENZE), gegen = Bildvergleich.vergleiche(iE, iR, GRENZE);
        double[] gb = deBlock(abb, block(echt), block(mit));
        String bl = f("in Blöcken zu %d × %d Bildpunkten (%d × %d, F56 A)", BLOCK, BLOCK, breite / BLOCK, hoehe / BLOCK);
        A.pruefe(rb[0] <= RAUSCHEN, "Prüfstein 7: Rauschen, ΔE₀₀ zwischen den Hälften der Pfade " + bl, f("im Mittel %.2f, größte %.1f", rb[0], rb[1]), f("im Mittel ≤ %.0f [G]", RAUSCHEN));
        A.pruefe(gb[0] < GRENZE, "Prüfstein 8: ΔE₀₀ Echtzeit gegen Referenz " + bl, f("im Mittel %.2f, größte %.1f", gb[0], gb[1]), f("im Mittel < %.0f [G]", GRENZE));
        A.info("Je Bildpunkt (Auskunft)", f("Rauschen im Mittel %.2f, 95 %% ≤ %.2f; gegen das Echtzeitbild im Mittel %.2f, 95 %% ≤ %.2f, über %.0f: %.1f %%",
                rausch.mittel(), rausch.p95(), gegen.mittel(), gegen.p95(), GRENZE, 100 * gegen.anteilUeber()));
        if (ref.luftstrahl != null) {
            // Prüfstein 13 von P10b: im Streifen um den Horizont, in Stücken zu 16 × 1 Bildpunkten – die Kimm liegt waagerecht,
            // das Mittel längs der Zeile nimmt das Rauschen, ohne die Zeilen zu mischen
            int[] zeilen = new com.dan.render.air.Kimmung.Geometrie(b.kopie().groesse(breite, hoehe), breite, hoehe).streifen(tf.tanHalb);
            int y0 = zeilen[0], hs = zeilen[1] - zeilen[0] + 1, bw = 16, nb = breite / bw;
            double[][] zE = zeilenBlock(echt, bw, y0, hs), zR = zeilenBlock(mit, bw, y0, hs), zG = zeilenBlock(echtGerade, bw, y0, hs);
            double[] dE = deBlock(abb, zE, zR), dG = deBlock(abb, zG, zR), dN = deBlock(abb, zeilenBlock(hal[0], bw, y0, hs), zeilenBlock(hal[1], bw, y0, hs));
            A.pruefe(dE[0] < GRENZE, f("Prüfstein 13 (P10b): ΔE₀₀ Echtzeit mit Kimmung gegen die Referenz mit gekrümmten Strahlen im Streifen (Zeilen %d bis %d von %d), in Stücken zu %d × 1 Bildpunkten", y0,
                            zeilen[1], hoehe, bw),
                    f("im Mittel %.2f, größte %.1f; ohne Kimmung im Echtzeitbild %.2f; Rauschen der Referenz in denselben Stücken %.2f", dE[0], dE[1], dG[0], dN[0]), f("im Mittel < %.0f [G]", GRENZE));
            int[] unterschied = new int[nb], ohneK = new int[nb];
            for (int k = 0; k < nb; k++) {
                int kr = kante(zR, nb, hs, k);
                unterschied[k] = Math.abs(kante(zE, nb, hs, k) - kr);
                ohneK[k] = Math.abs(kante(zG, nb, hs, k) - kr);
            }
            java.util.Arrays.sort(unterschied);
            java.util.Arrays.sort(ohneK);
            int median = unterschied[nb / 2], p90 = unterschied[(int) (0.9 * (nb - 1))];
            A.pruefe(median <= 1, f("Prüfstein 13 (P10b): Zeile des stärksten Sprungs der Leuchtdichte im Streifen (die Kimm oder der Umriss davor), je Stück von %d Spalten, Echtzeit gegen Referenz", bw),
                    f("Median %d, 90 %% ≤ %d Bildpunkte der Referenz (ohne Kimmung im Echtzeitbild: Median %d, 90 %% ≤ %d); ein Bildpunkt sind %.2f′", median, p90, ohneK[nb / 2], ohneK[(int) (0.9 * (nb - 1))],
                            b.bildwinkel * 60 / hoehe), "Median ≤ 1 Bildpunkt [G]");
            A.info("Sehstrahlen der Referenz", f("%d im Streifen, %d mit Treffer, %d zum Himmel", ref.luftstrahl.strahlen.sum(), ref.luftstrahl.treffer.sum(), ref.luftstrahl.himmel.sum()));
        }
        double[] de = zerlegung(iE, iR, echt, mit, art, i0, i1);
        // die Zerlegungen der Säule und des Himmels rechnen die Referenz noch einmal; für die Ansicht der Kimmung (P10b) entfallen sie
        if (ref.luftstrahl == null) {
            if (wasserAnteil(art) >= 0.05) saeule(ref, b, echt, mit, art, teile);
            himmel(ref, b, sz, zeit, echt, mit, art);
        }
        BufferedImage karte = karte(de);
        String txt = nr + " " + an.name() + " · " + BildCheck.zeit(an.t0(), an.t());
        return new BufferedImage[] {beschrifte(iR, "Referenz " + txt), beschrifte(iE, "Echtzeit"), beschrifte(karte, f("ΔE₀₀ im Mittel %.2f, in Blöcken %.2f (0 schwarz, 10 weiß)", gegen.mittel(), gb[0]))};
    }

    /**
     * Prüfstein 6 an den Strahlen durch die Mitten der Bildpunkte: Höhe des Treffers gegen {@code Gelaende.hoehe} des
     * Echtzeitbilds (mit eigener Abfrage) und η des Treffers gegen die Regel des Echtzeitbilds
     * ({@code Wasserbild.etaBild}). Gibt die Art je Bildpunkt (0 Himmel, 1 Boden, 2 Wasser, 3 Meeresgrund außerhalb).
     */
    static int[] pruefstein6(Blick b, Gelaendeszene gs, BildCheck.Szene sz) {
        int n = breite * hoehe;
        int[] art = new int[n];
        double[] dh = new double[n], dw = new double[n];
        boolean[] bit = new boolean[n], wand = new boolean[n];
        Arrays.fill(bit, true);
        Gelaende echt = sz.gelaende.gelaende;
        IntStream.range(0, hoehe).parallel().forEach(y -> {
            Szene.Treffer tr = new Szene.Treffer();
            Gelaende.Abfrage abq = echt.abfrage();
            for (int x = 0; x < breite; x++) {
                int i = y * breite + x;
                double[] d = b.strahl(x + 0.5, y + 0.5);
                if (!gs.schneide(new double[3], d, Double.POSITIVE_INFINITY, tr)) continue;
                double px = tr.t * d[0], py = tr.t * d[1], pz = tr.t * d[2], hoch = py + gs.hAuge + gs.absenkung(px, pz);
                double o = gs.ost + px, nd = gs.nord - pz;
                // die Fläche am Treffer und 10⁻⁷ m davor (Ende des Halbierens): an einer Stufe der Fläche liegt der Treffer
                // auf ihrer Wand, also zwischen beiden Höhen
                double tv = tr.t - Gelaendeszene.GENAU, vx = tv * d[0], vz = tv * d[2];
                if (tr.art == Szene.WASSER) {
                    art[i] = 2;
                    float e = sz.wasser.etaBild((float) px, (float) pz, null), ev = sz.wasser.etaBild((float) vx, (float) vz, null);
                    double eg = gs.eta(px, pz);
                    bit[i] = Float.floatToIntBits(e) == Float.floatToIntBits((float) eg);
                    dw[i] = abstand(hoch, e, Float.isNaN(ev) ? e : ev);
                    if (Math.abs(e - ev) > 1e-4) wand[i] = true;
                } else {
                    float h = abq.hoehe(o, nd, 1), hv = abq.hoehe(gs.ost + vx, gs.nord - vz, 1);
                    double a = Float.isNaN(h) ? Schranke.KEIN_GRUND : h, av = Float.isNaN(hv) ? Schranke.KEIN_GRUND : hv;
                    art[i] = Float.isNaN(h) ? 3 : 1;
                    dh[i] = abstand(hoch, a, av);
                    if (Math.abs(a - av) > 1e-4) wand[i] = true;
                }
            }
        });
        long nb = 0, nw = 0, nk = 0, nh = 0, nbit = 0, nwand = 0;
        for (boolean x : wand) if (x) nwand++;
        double mh = 0, mw = 0;
        for (int i = 0; i < n; i++) {
            switch (art[i]) {
                case 1 -> { nb++; mh = Math.max(mh, dh[i]); }
                case 2 -> { nw++; mw = Math.max(mw, dw[i]); if (!bit[i]) nbit++; }
                case 3 -> { nk++; mh = Math.max(mh, dh[i]); }
                default -> nh++;
            }
        }
        A.pruefe(mh <= 1e-6, f("Prüfstein 6: Höhe der Treffer mit dem Gelände gegen Gelaende.hoehe am Treffer und 10⁻⁷ m davor (%d Strahlen, davon %d auf dem Meeresgrund außerhalb)", nb + nk, nk),
                f("größte Abweichung %.2e m", mh), "≤ 1 · 10⁻⁶ m");
        A.pruefe(nbit == 0 && mw <= 1e-6, f("Prüfstein 6: η der Treffer mit dem Wasser gegen die Regel des Echtzeitbilds (%d Strahlen)", nw),
                f("%d nicht bitgleich; Höhe des Treffers gegen η höchstens %.2e m", nbit, mw), "bitgleich; ≤ 1 · 10⁻⁶ m");
        A.info("Arten der Bildpunkte", f("Himmel %.1f %%, Gelände %.1f %%, Wasser %.1f %%, Meeresgrund außerhalb %.1f %%; Treffer auf einer Stufe der Fläche (Wand) %d",
                100.0 * nh / n, 100.0 * nb / n, 100.0 * nw / n, 100.0 * nk / n, nwand));
        return art;
    }

    /** Abstand der Höhe h vom Bereich zwischen a und b (0 darin). */
    static double abstand(double h, double a, double b) { return Math.max(0, Math.max(h - Math.max(a, b), Math.min(a, b) - h)); }

    /** Zerlegung je Art: Anteil, ΔE₀₀, Leuchtdichte und Kanäle Echtzeit / Referenz. Gibt ΔE₀₀ je Bildpunkt. */
    static double[] zerlegung(BufferedImage iE, BufferedImage iR, double[][] echt, double[][] ref, int[] art, BufferedImage h0, BufferedImage h1) {
        int n = art.length;
        double[] de = new double[n], la = new double[3], lb = new double[3];
        String[] namen = {"Himmel", "Gelände", "Wasser", "Meeresgrund außerhalb"};
        double[][] s = new double[4][9];
        for (int i = 0; i < n; i++) {
            int x = i % breite, y = i / breite;
            Bildvergleich.lab(iE.getRGB(x, y) & 0xFFFFFF, la);
            Bildvergleich.lab(iR.getRGB(x, y) & 0xFFFFFF, lb);
            de[i] = Bildvergleich.deltaE00(la[0], la[1], la[2], lb[0], lb[1], lb[2]);
            double[] q = s[art[i]];
            q[0]++;
            q[1] += de[i];
            Bildvergleich.lab(h0.getRGB(x, y) & 0xFFFFFF, la);
            Bildvergleich.lab(h1.getRGB(x, y) & 0xFFFFFF, lb);
            q[8] += Bildvergleich.deltaE00(la[0], la[1], la[2], lb[0], lb[1], lb[2]);
            for (int c = 0; c < 3; c++) {
                q[2 + c] += echt[c][i];
                q[5 + c] += ref[c][i];
            }
        }
        StringBuilder t = new StringBuilder();
        for (int a = 0; a < 4; a++) {
            double[] q = s[a];
            if (q[0] == 0) continue;
            double yE = 0.2126 * q[2] + 0.7152 * q[3] + 0.0722 * q[4], yR = 0.2126 * q[5] + 0.7152 * q[6] + 0.0722 * q[7];
            if (t.length() > 0) t.append("; ");
            t.append(f("%s %.1f %% (ΔE₀₀ %.2f, Rauschen %.2f, Leuchtdichte %.3g gegen %.3g cd/m², Verhältnis %.3f, R %.3f G %.3f B %.3f)", namen[a], 100 * q[0] / n, q[1] / q[0], q[8] / q[0],
                    yE / q[0], yR / q[0], yE / yR, q[2] / q[5], q[3] / q[6], q[4] / q[7]));
        }
        A.info("Zerlegung Echtzeit / Referenz", t.toString());
        return de;
    }

    /** Anteil der Bildpunkte mit Wasser. */
    static double wasserAnteil(int[] art) {
        long n = 0;
        for (int a : art) if (a == 2) n++;
        return n / (double) art.length;
    }

    /** Mittel über Blöcke von {@link #BLOCK} × {@link #BLOCK} Bildpunkten (lineares Licht). */
    static double[][] block(double[][] l) {
        int bw = breite / BLOCK, bh = hoehe / BLOCK;
        double[][] o = new double[3][bw * bh];
        for (int c = 0; c < 3; c++)
            for (int y = 0; y < bh * BLOCK; y++)
                for (int x = 0; x < bw * BLOCK; x++) o[c][(y / BLOCK) * bw + x / BLOCK] += l[c][y * breite + x] / (BLOCK * BLOCK);
        return o;
    }

    /** ΔE₀₀ im Mittel zwischen zwei Bildern gleicher Größe w × h in linearem Licht, durch dieselbe Abbildung. */
    static double[] deBlock(Abbild abb, double[][] a, double[][] b) {
        int n = a[0].length;
        double s = 0, mx = 0;
        double[] la = new double[3], lb = new double[3];
        for (int i = 0; i < n; i++) {
            Bildvergleich.lab(abb.rgb(a[0][i], a[1][i], a[2][i]), la);
            Bildvergleich.lab(abb.rgb(b[0][i], b[1][i], b[2][i]), lb);
            double d = Bildvergleich.deltaE00(la[0], la[1], la[2], lb[0], lb[1], lb[2]);
            s += d;
            mx = Math.max(mx, d);
        }
        return new double[] {s / n, mx};
    }

    /**
     * Zerlegung des Wassers für F55: dieselbe Ansicht mit einem Viertel der Pfade, seit P7h in einem Lauf nach dem Weg
     * der Pfade geteilt ({@code Pfadverfolger.teile}: nie im Wasser, Säule aus Sonne und Mond, Säule aus dem Himmel,
     * Grund), vorher als Unterschied zu einem Lauf ohne Wassersäule. F55 fragt, ob die Säule mehr als die Hälfte der
     * Abweichung des Echtzeitbilds trägt. Dazu die Teile des Echtzeitbilds (P7f) je gegen den Teil der Referenz (P7h).
     */
    static void saeule(Referenz ref, Blick b, double[][] echt, double[][] mit, int[] art, double[][][] teile) {
        // P7h: die Referenz in einem Lauf nach dem Weg der Pfade geteilt (Pfadverfolger.teile) statt der Differenz zu
        // einem Lauf ohne Säule: 0 nie im Wasser, 1 Säule aus Sonne und Mond, 2 Säule aus dem Himmel, 3 Grund
        ref.teile = true;
        ref.saat += 500;
        double[][] ganz = Referenz.mittel(ref.bild(b, breite, hoehe, Math.max(2, pfade / 4)));
        double[][][] tr = ref.teilBild;
        ref.teile = false;
        double yE = 0, yR = 0, yG = 0;
        double[] yQ = new double[4];
        long n = 0;
        for (int i = 0; i < art.length; i++) {
            if (art[i] != 2) continue;
            n++;
            yE += lum(echt, i);
            yR += lum(mit, i);
            yG += lum(ganz, i);
            for (int t = 0; t < 4; t++) yQ[t] += lum(tr[t], i);
        }
        yE /= n;
        yR /= n;
        yG /= n;
        for (int t = 0; t < 4; t++) yQ[t] /= n;
        double yO = yQ[0], saeule = yQ[1] + yQ[2] + yQ[3], abw = yE - yR;
        A.info("F55: Wasser, Leuchtdichte im Mittel der Bildpunkte mit Wasser", f("Echtzeit %.4g, Referenz %.4g cd/m², davon Fläche (nie im Wasser; Lauf in Teilen, %d Pfade, %.0f s) %.4g und Säule mit Grund %.4g (%.0f %%); Abweichung %.4g (%.0f %%); Säule %s der halben Abweichung",
                yE, yR, Math.max(2, pfade / 4), ref.sekunden, yO, saeule, 100 * saeule / yR, abw, 100 * abw / yR, saeule > 0.5 * Math.abs(abw) ? "über" : "unter"));
        // P7f: die Teile des Echtzeitbilds gegen Fläche und Säule der Referenz
        double[] yT = new double[5];
        for (int i = 0; i < art.length; i++)
            if (art[i] == 2) for (int k = 0; k < 5; k++) yT[k] += lum(teile[k], i) / n;
        // jedes Teilbild trägt das Streulicht der Luft vor dem Wasser (Luftperspektive), das ganze Bild nur einmal
        double luft = (yT[0] + yT[1] + yT[2] + yT[3] + yT[4] - yE) / 4;
        for (int k = 0; k < 5; k++) yT[k] -= luft;
        double flaeche = yT[0] + yT[1] + luft, koerper = yT[2] + yT[3] + yT[4];
        A.info("P7f: Wasser des Echtzeitbilds in Teilen (cd/m²)", f("Spiegelung %.4g, Glitzern %.4g, Streulicht der Luft davor %.4g, Säule aus Sonne und Mond %.4g, Säule aus dem Himmel %.4g, Grund %.4g; Fläche mit Luft %.4g gegen Referenz %.4g (Verhältnis %.2f); Säule und Grund %.4g gegen Referenz %.4g (Verhältnis %.2f)",
                yT[0], yT[1], luft, yT[2], yT[3], yT[4], flaeche, yO, flaeche / yO, koerper, saeule, koerper / saeule));
        A.pruefe(Math.abs(flaeche / yO - 1) <= 0.10, "P7g: Fläche des Wassers (Spiegelung, Glitzern, Luft davor), Echtzeit durch Referenz", f("%.2f", flaeche / yO), "1 ± 0,10 [G]");
        A.pruefe(Math.abs(koerper / saeule - 1) <= 0.10, "P7g: Säule und Grund, Echtzeit durch Referenz", f("%.2f", koerper / saeule), "1 ± 0,10 [G]");
        letzteSaeule = koerper / saeule;
        A.info("P7h: Wasserkörper in Teilen, Echtzeit gegen Referenz (cd/m²)", f("Säule aus Sonne und Mond %.4g gegen %.4g (Verhältnis %.2f), Säule aus dem Himmel %.4g gegen %.4g (%.2f), Grund %.4g gegen %.4g (%.2f); Anteile am Wasserkörper der Referenz %.0f / %.0f / %.0f %%; Lauf in Teilen im Ganzen %.4g gegen den Hauptlauf %.4g (%+.1f %%)",
                yT[2], yQ[1], yT[2] / yQ[1], yT[3], yQ[2], yT[3] / yQ[2], yT[4], yQ[3], yT[4] / yQ[3], 100 * yQ[1] / saeule, 100 * yQ[2] / saeule, 100 * yQ[3] / saeule, yG, yR, 100 * (yG / yR - 1)));
    }

    /**
     * P7h (F65 A): der Himmel der Szene in Stufen, an den Bildpunkten mit Himmel und je Band der Höhe über der
     * Waagerechten. Echtzeit (Himmelsansicht der Karte) gegen dasselbe Modell in Java ({@link Scattering#radianceSpektral}
     * wie in LuftCheck: nur die Sonne, darum nur bei Tag; an jedem neunten Bildpunkt), dieses gegen die Referenz über dem
     * Boden von LuftCheck (Kugel, Albedo der Tabellen, kein Wasser, Auge so hoch wie in der Szene), diese gegen die
     * Referenz der Szene. Ohne die Bildpunkte näher als 2° an der Sonne (die Scheibe).
     */
    static void himmel(Referenz ref, Blick blick, BildCheck.Szene sz, Instant zeit, double[][] echt, double[][] mit, int[] art) {
        long nh = Arrays.stream(art).filter(a -> a == 0).count();
        if (nh < 0.05 * art.length) return;
        Blick b = blick.kopie().groesse(breite, hoehe);
        Atmosphere luft = sz.himmel.luft();
        double h = ref.szene.augeHoehe();
        // seit F105 A: die Kugel mit der Albedo je Kanal, mit der der Himmel J gemischt hat
        double[] ak = sz.himmel.gemischtRgb();
        if (ak == null) ak = new double[] {luft.groundAlbedo, luft.groundAlbedo, luft.groundAlbedo};
        Ebenen eb = new Ebenen(-h, Double.NaN, ak, h);
        eb.kugel = true;
        Referenz rk = new Referenz(eb, luft, sz.wasser.optik, 0);
        rk.lichter.addAll(ref.lichter);
        rk.spektral = ref.spektral;
        rk.bahnen = ref.bahnen;
        rk.dichte = ref.dichte;
        rk.keule = ref.keule;
        rk.saat = ref.saat + 700;
        int np = Math.max(2, pfade / 4);
        double[][] kugel = Referenz.mittel(rk.bild(blick, breite, hoehe, np));
        Lichtquellen.Stand st = Lichtquellen.bei(zeit, blick.ost, blick.nord, 32);
        double[] l = st.sonneSicht();
        boolean tag = l[1] > 0;
        // seit F105 A gemischt wie die Karte: Java mit den Tabellen für Wasser und Land, je Kanal linear in der Albedo
        boolean je = sz.himmel.gemischtRgb() != null;
        Atmosphere lj = je ? Atmosphere.forVisibility(luft.visibilityKm, com.dan.render.air.Himmel.ALB_WASSER) : luft;
        if (tag && (himmelJava == null || himmelLuft.visibilityKm != lj.visibilityKm || himmelLuft.groundAlbedo != lj.groundAlbedo)) {
            himmelJava = new Scattering(lj);                                // die Streuordnungen rechnet es beim ersten Aufruf
            himmelLuft = lj;
        }
        if (tag && je && (himmelLand == null || himmelLand.atm.visibilityKm != luft.visibilityKm))
            himmelLand = new Scattering(Atmosphere.forVisibility(luft.visibilityKm, com.dan.render.air.Himmel.ALB_LAND));
        Scattering sc = tag ? himmelJava : null, sl = tag && je ? himmelLand : null;
        double[] fk = new double[3];
        for (int c = 0; c < 3; c++) fk[c] = (ak[c] - com.dan.render.air.Himmel.ALB_WASSER) / (com.dan.render.air.Himmel.ALB_LAND - com.dan.render.air.Himmel.ALB_WASSER);
        double fs = st.eSonne() / Lichtquellen.SONNE_LX, cs = Math.cos(Math.toRadians(2));
        String[] namen = {"Höhe unter 2°", "2° bis 6°", "über 6°", "alle"};
        double[][] s = new double[4][16];                                   // Zahl; Echtzeit, Java, Kugel, Szene, Echtzeit bei Java je RGB
        long t0 = System.nanoTime();
        for (int i = 0; i < art.length; i++) {
            if (art[i] != 0) continue;
            double[] v = b.strahl(i % breite + 0.5, i / breite + 0.5);
            double vl = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            v = new double[] {v[0] / vl, v[1] / vl, v[2] / vl};
            if (v[0] * l[0] + v[1] * l[1] + v[2] * l[2] > cs) continue;
            double hw = Math.toDegrees(Math.asin(v[1]));
            // Java an jedem dritten Bildpunkt in x und y (beide Seiten ohne Rauschen)
            double[] j = tag && i % breite % 3 == 1 && i / breite % 3 == 1 ? sc.radianceSpektral(h, v, l, fs, Scattering.VIEW_STEPS) : null;
            if (j != null && sl != null) {
                double[] jl = sl.radianceSpektral(h, v, l, fs, Scattering.VIEW_STEPS);
                for (int c = 0; c < 3; c++) j[c] += (jl[c] - j[c]) * fk[c];
            }
            for (double[] q : new double[][] {s[hw < 2 ? 0 : hw < 6 ? 1 : 2], s[3]}) {
                q[0]++;
                for (int c = 0; c < 3; c++) {
                    q[1 + c] += echt[c][i];
                    if (j != null) {
                        q[4 + c] += j[c];
                        q[13 + c] += echt[c][i];
                    }
                    q[7 + c] += kugel[c][i];
                    q[10 + c] += mit[c][i];
                }
            }
        }
        StringBuilder t = new StringBuilder();
        for (int k = 0; k < 4; k++) {
            double[] q = s[k];
            if (q[0] == 0) continue;
            if (t.length() > 0) t.append("; ");
            t.append(f("%s (%.1f %% des Bilds): Echtzeit %.4g, Referenz über der Kugel %.4g, Referenz der Szene %.4g cd/m²; ", namen[k], 100 * q[0] / art.length, y(q, 1) / q[0], y(q, 7) / q[0], y(q, 10) / q[0]));
            if (tag && q[4] > 0) t.append("Echtzeit / Java (jeder neunte Bildpunkt) ").append(verh(q, 13, 4)).append(f(", daraus Java / Kugel %.3f, ", y(q, 1) / y(q, 7) / (y(q, 13) / y(q, 4))));
            t.append("Echtzeit / Kugel ").append(verh(q, 1, 7)).append(", Kugel / Szene ").append(verh(q, 7, 10)).append(", Echtzeit / Szene ").append(verh(q, 1, 10));
        }
        A.info(f("P7h: Himmel in Stufen (Auge %.1f m, Sonne %.1f° hoch, Kugel mit Albedo R %.3f G %.3f B %.3f und %d Pfaden in %.0f s, Java in %.0f s%s)", h, Math.toDegrees(Math.asin(l[1])), ak[0], ak[1], ak[2], np, rk.sekunden,
                (System.nanoTime() - t0) / 1e9, tag ? "" : "; Nacht, ohne Java"), t.toString());
        // seit F105 A (Prüfstein 19): die Kugel mit der Albedo des Himmels gibt den Himmel der Szene
        double[] q = s[3];
        if (tag && q[0] > 0) {
            double ks = y(q, 7) / y(q, 10);
            A.pruefe(Math.abs(ks - 1) <= 0.03, "Prüfstein 19 (F105, F106 B): Himmel über der Kugel mit der Albedo des Himmels gegen den Himmel der Szene, alle Bildpunkte mit Himmel", verh(q, 7, 10), "1 ± 0,03 [G]");
            letzteKugel = ks;
        }
    }

    /** Kugel / Szene der letzten Ansicht (Prüfstein 19). */
    static double letzteKugel = Double.NaN;
    private static Scattering himmelLand;

    private static Scattering himmelJava;
    private static Atmosphere himmelLuft;

    private static double y(double[] q, int a) { return 0.2126 * q[a] + 0.7152 * q[a + 1] + 0.0722 * q[a + 2]; }

    private static String verh(double[] q, int a, int b) { return f("%.3f (R %.3f G %.3f B %.3f)", y(q, a) / y(q, b), q[a] / q[b], q[a + 1] / q[b + 1], q[a + 2] / q[b + 2]); }

    /** Das HDR-Bild (W breit, mit Vorbelichtung) gemittelt je faktor × faktor auf breite × hoehe, in cd/m². */
    static double[][] mittle(float[] hdr, int W, double vorb) {
        double[][] m = new double[3][breite * hoehe];
        for (int y = 0; y < hoehe; y++)
            for (int x = 0; x < breite; x++)
                for (int c = 0; c < 3; c++) {
                    double s = 0;
                    for (int v = 0; v < faktor; v++)
                        for (int u = 0; u < faktor; u++) s += hdr[4 * ((y * faktor + v) * W + x * faktor + u) + c];
                    m[c][y * breite + x] = s / (faktor * faktor) / vorb;
                }
        return m;
    }

    static double lum(double[][] l, int i) { return 0.2126 * l[0][i] + 0.7152 * l[1][i] + 0.0722 * l[2][i]; }

    /** Die Abbildung des Echtzeitbilds in double: Belichtung, Stäbchen, Filmkurve, sRGB. */
    static final class Abbild {
        final double F;
        final boolean nacht, kurve;
        final Filmkurve fk;

        Abbild(BildCheck.Szene sz, double F) {
            this.F = F;
            nacht = sz.rahmen.belichtung.nachtsehen;
            kurve = sz.rahmen.kurve;
            fk = new Filmkurve(sz.rahmen.film);
        }

        /** Lineares sRGB in cd/m² → 0xRRGGBB. */
        int rgb(double r, double g, double b) {
            double[] c = {r * F, g * F, b * F};
            if (nacht) {
                double y = Math.max(0, 0.2126 * r + 0.7152 * g + 0.0722 * b);
                c = Belichtung.staebchen(c[0], c[1], c[2], y);
            }
            double[] o = kurve ? fk.anwenden(c[0], c[1], c[2]) : c;
            int v = 0;
            for (int k = 0; k < 3; k++) {
                double l = Math.min(1, Math.max(0, o[k])), e = l <= 0.0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1 / 2.4) - 0.055;
                v = v << 8 | (int) Math.round(e * 255);
            }
            return v;
        }

        BufferedImage bild(double[][] l) {
            BufferedImage im = new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < breite * hoehe; i++) {
                double r = l[0][i], g = l[1][i], b = l[2][i];
                im.setRGB(i % breite, i / breite, Double.isFinite(r + g + b) ? rgb(r, g, b) : 0xFF00FF);
            }
            return im;
        }
    }

    static BufferedImage karte(double[] de) {
        BufferedImage im = new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < de.length; i++) {
            int v = (int) Math.round(255 * Math.min(1, de[i] / 10));
            im.setRGB(i % breite, i / breite, de[i] > GRENZE ? (v << 16 | (v / 2) << 8 | v / 4) : (v << 16 | v << 8 | v));
        }
        return im;
    }

    static BufferedImage beschrifte(BufferedImage im, String txt) {
        Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(10, hoehe / 24)));
        int x0 = 6, y0 = hoehe - 8;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(x0 - 3, y0 - g2.getFontMetrics().getAscent() - 2, g2.getFontMetrics().stringWidth(txt) + 6, g2.getFontMetrics().getHeight() + 2);
        g2.setColor(Color.WHITE);
        g2.drawString(txt, x0, y0);
        g2.dispose();
        return im;
    }

    /** Prüfstein 10: je Ansicht eine Reihe Referenz, Echtzeit, Karte des ΔE₀₀. */
    static void statusBild(List<BufferedImage[]> reihen) throws Exception {
        if (reihen.isEmpty()) return;
        BufferedImage alle = new BufferedImage(3 * breite, reihen.size() * hoehe, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = alle.createGraphics();
        for (int r = 0; r < reihen.size(); r++)
            for (int c = 0; c < 3; c++) g2.drawImage(reihen.get(r)[c], c * breite, r * hoehe, null);
        g2.dispose();
        File png = new File("docs" + File.separator + "status" + File.separator + name + ".png");
        png.getParentFile().mkdirs();
        ImageIO.write(alle, "png", png);
        A.info("Prüfstein 10: Status-Bild (Sichtprüfung)", png.getPath() + f(", %d × %d", alle.getWidth(), alle.getHeight()));
    }
}
