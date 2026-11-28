package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Oberflaeche;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Seegangsflaeche;
import com.dan.lauf.Buehne;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.water.Wasserbild;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.dan.core.pruef.Attest.f;

/**
 * Feste Bilder des Seegangs zum Vergleich über die Phasen (Seegang Werkbuch, S0; SF13 A): je Eintrag Szenario, Ansicht
 * und Zeit ein Standbild in voller Größe und die Zeiten der Karte (Median aus {@code --zeiten} Bildern) mit allem, ohne
 * Brandung und ohne Seegang (Brandung und Seegang aus): der Anteil des Seegangs an einem Bild. Dieselbe Liste mit
 * {@code --kennung S3} … gibt die Bilder „nachher“ aus demselben Blick: seit S3c mit der neuen Fläche (Nahfeld, Synthese und
 * Netz je Bild, {@link Seegangsflaeche}; {@code --netz an|aus}, Vorgabe an außer für S0). Die Zeiten dann: alles mit Netz,
 * wie bisher ohne Netz, ohne Seegang.
 * <p>
 * Seit S3c-5 (Seegang Werkbuch 19/20): das Auge mindestens {@code ansicht.<name>.wasser} über dem Wasser (SF29 A, Prüfstein
 * „kein Auge unter dem Wasser“); N6 – Netz und Schattierung je Bild aus den Zeitstempeln der Durchgänge, mit und ohne
 * Abbruch über die Tiefe des Geländes (B48); N8 – Helligkeit beidseits der Nähte (Rand der Clipmap, Ränder des feinen und
 * des groben Nahfelds, B49); {@code --diagnose 5,…}: für diese Bilder eine Karte von Hs des Nahfelds und dasselbe Bild mit
 * doppeltem Anlauf.
 * <p>
 * Seit S3c-6 (Seegang Werkbuch 22): N6 dazu mit Vorlauf der Tiefe (statt ohne Abbruch, der nichts änderte), der alte Weg
 * ohne Netz als Vergleich, ein Bild mit Zählern je Bildpunkt (Bildpunkte des Netzes, Überzeichnung, Zeit je Bildpunkt);
 * steht ein Eintrag zweimal in der Liste (am Anfang und am Ende), vergleicht N6 die Zeiten kalt gegen warm.
 * <p>
 * Ablage {@code docs/status/seegang/<Kennung>_<nn>.png} (voll), {@code <Kennung>_Uebersicht.png} (Kontaktbogen) und das
 * Attest {@code docs/status/<Kennung>_Bilder.md}.
 * <pre>  SeegangBilder --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--kennung S0] [--bilder sz/ansicht/zeit,…] [--breite 1920 --hoehe 1080] [--zeiten 20]</pre>
 */
public final class SeegangBilder {
    static final DateTimeFormatter UHR = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm", Locale.GERMAN).withZone(ZoneId.of("Europe/Berlin"));
    /**
     * Die Bilder aus S0 (Werkbuch Seegang 2): Japsand, Steinkante West, Anleger, Hanswarft im Landunter und die
     * Prielmündung im Ebbstrom in der Sturmflut vom 25.10.2026, in Xaver und am ruhigen Tag (Zeiten UTC).
     */
    public static final String BILDER = String.join(",",
            "seegang/brandung/2026-10-25T12:30:00Z", "seegang/steinkante_west/2026-10-25T12:30:00Z", "seegang/hafen/2026-10-25T12:30:00Z",
            "seegang/landunter_hanswarft/2026-10-25T12:30:00Z", "seegang/prielmuendung/2026-10-25T14:30:00Z",
            "xaver/brandung/2013-12-05T13:30:00Z", "xaver/steinkante_west/2013-12-05T12:00:00Z", "xaver/hafen/2013-12-05T13:30:00Z",
            "xaver/landunter_hanswarft/2013-12-05T13:30:00Z", "xaver/prielmuendung/2013-12-06T08:30:00Z",
            "ruhigsee/brandung/2026-10-30T12:00:00Z", "ruhigsee/hafen/2026-10-30T12:00:00Z", "ruhigsee/prielmuendung/2026-10-30T08:00:00Z");

    record Bild(String text, BufferedImage bild) { }

    public static void main(String[] args) {
        File szenen = null;
        String kennung = "S0", bilder = BILDER, netzWahl = null, diagnose = "";
        int breite = 1920, hoehe = 1080, zeiten = 20;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--kennung" -> kennung = args[i + 1];
                case "--bilder" -> bilder = args[i + 1];
                case "--breite" -> breite = Integer.parseInt(args[i + 1]);
                case "--hoehe" -> hoehe = Integer.parseInt(args[i + 1]);
                case "--zeiten" -> zeiten = Integer.parseInt(args[i + 1]);
                case "--netz" -> netzWahl = args[i + 1];
                case "--diagnose" -> diagnose = args[i + 1];
                default -> { }
            }
        }
        // seit S3c: mit der neuen Fläche (Nahfeld, Synthese, Netz), Vorgabe ab der Kennung S3; --netz an|aus
        boolean netz = netzWahl != null ? netzWahl.equals("an") : !kennung.startsWith("S0");
        Attest A = new Attest("Seegang, Bilder " + kennung, "Seegang Werkbuch");
        System.out.println("SeegangBilder – " + kennung + " (Seegang Werkbuch)");
        List<Bild> liste = new ArrayList<>();
        Path ordner = Path.of("docs", "status", "seegang");
        int leer = 0, gerechnet = 0;
        double summeAlles = 0, summeSee = 0, groesste = 0;
        String groessteName = "";
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Files.createDirectories(ordner);
            Szenen sz = Szenen.lies(szenen);
            A.teil("Bilder (" + breite + " × " + hoehe + "), Zeiten der Karte als Median aus " + zeiten + " Bildern");
            String offen = null;
            Szenario offenS = null;
            Buehne b = null;
            double summeNetz = 0, summeAlt = 0, summeN6 = 0, groessteN6 = 0, summeN6ohne = 0;
            int mitNetz = 0, gehoben = 0, unter = 0, n6zahl = 0, mitWasser = 0;
            String groessteN6Name = "";
            double groessteUeber = 0, summeMehr = 0, groessteMehr = -1e9;
            String groessteMehrName = "";
            java.util.Map<String, Double> n6Je = new java.util.HashMap<>();
            java.util.Set<Integer> diag = new java.util.HashSet<>();
            for (String d : diagnose.split(",")) if (!d.isBlank()) diag.add(Integer.parseInt(d.strip()));
            int nr = 0;
            try {
                for (String eintrag : bilder.split(",")) {
                    nr++;
                    String[] q = eintrag.strip().split("/");
                    if (q.length != 3) { A.info("Eintrag " + nr, "nicht Szenario/Ansicht/Zeit: " + eintrag); continue; }
                    if (!q[0].equals(offen)) {
                        if (b != null) b.close();
                        b = null;
                        offen = null;
                        Szenario s = null;
                        for (Szenario x : sz.szenarien()) if (x.schluessel.equals(q[0])) s = x;
                        if (s == null) { A.info("Szenario " + q[0], "nicht in der Szenendatei"); continue; }
                        long n0 = System.nanoTime();
                        b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
                        b.oeffne(s);
                        offen = q[0];
                        offenS = s;
                        A.info("Szenario " + q[0], f("%s, geöffnet in %.1f s", s.name, (System.nanoTime() - n0) / 1e9));
                    }
                    Ansicht an = sz.ansicht(q[1]);
                    if (an == null) { A.info("Ansicht " + q[1], "nicht in der Szenendatei"); continue; }
                    double t = (Instant.parse(q[2]).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
                    Blick bl = new Blick();
                    an.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                    bl.groesse(breite, hoehe);
                    Wasserbild w = b.wasserbild();
                    w.seegang = true;
                    b.brandung(true);
                    b.standbild(bl, t);                                        // die Belichtung stellt sich ein, die Stände sind geladen
                    // seit S3c-5 (SF29 A): das Auge mindestens ansicht.<name>.wasser über Spiegel plus Hs/2
                    double[] wa = b.wasserUndSeegang(bl.ost, bl.nord);
                    if (!Double.isNaN(wa[0])) mitWasser++;
                    double geh = Ansicht.hebe(bl, wa[0], wa[1], an.ueberWasser);
                    if (geh > 0) {
                        gehoben++;
                        A.info("Auge " + nr, f("um %.2f m gehoben auf %.2f m NHN (Spiegel %.2f m NHN, Hs %.2f m, mindestens %.1f m darüber)", geh, bl.hoehe, wa[0], wa[1], an.ueberWasser));
                        b.standbild(bl, t);
                    }
                    Seegangsflaeche sf = null;
                    if (netz && offenS != null && offenS.archiv != null) {
                        sf = Seegangsflaeche.baue(b.geraet(), b.konto(), sz.gebiet(), offenS.archiv.toPath(), b.seegangsnetze(), bl, t, an.fein);
                        sf.bild.zeitQuelle = () -> w.zeit;               // dieselbe Zeit wie das Wasser (Fernfeld jenseits der Clipmap)
                        b.seegang(sf.bild);
                        A.info("Fläche " + nr, sf.bericht);
                    }
                    b.standbild(bl, t);
                    float[] p = b.standbild(bl, t).clone();
                    double[] wn = b.wasserUndSeegang(bl.ost, bl.nord);
                    if (!Double.isNaN(wn[0]) && bl.hoehe < wn[0] + 0.5 * wn[1]) {
                        unter++;
                        A.info("Auge " + nr, f("unter dem Wasser: %.2f m NHN, Spiegel %.2f m NHN, Hs %.2f m", bl.hoehe, wn[0], wn[1]));
                    }
                    BufferedImage bi = bild(p, breite, hoehe);
                    String datei = f("%s_%02d.png", kennung, nr);
                    ImageIO.write(bi, "png", ordner.resolve(datei).toFile());
                    double hell = helligkeit(p);
                    if (hell < 0.01) leer++;
                    double[] ms = new double[4];
                    double[] n6 = new double[2];
                    String abschnitte = "";
                    StringBuilder abV = new StringBuilder();
                    double wasserAlt = 0, netzAllein = 0, vorlaufAllein = 0, wasserNeben = 0;
                    int runden = sf != null ? 4 : 3;
                    for (int k = 0; k < runden; k++) {
                        // ohne Netz (S0): alles, ohne Brandung, ohne Seegang; mit Netz (S3): alles, wie bisher ohne Netz, ohne
                        // Seegang, seit S3c-5 dazu alles mit Netz ohne Abbruch über die Tiefe (B48), seit S3c-6 statt dessen mit Vorlauf der Tiefe
                        w.seegang = k < 2 || k == 3;
                        b.brandung(sf != null ? k < 2 || k == 3 : k == 0);
                        if (sf != null) { sf.bild.an = k == 0 || k == 3; w.netz = k == 0 || k == 3; }
                        w.vorlauf = k == 3;                                   // seit S3c-6 (N6): Runde 3 mit Vorlauf der Tiefe
                        b.standbild(bl, t);
                        Buehne.Bildzeiten z = b.bildzeiten(bl, t, zeiten, sf != null && k != 2);
                        ms[k] = z.karte()[z.karte().length / 2];
                        if (sf != null && k != 2) {
                            double nz = 0;
                            StringBuilder ab = new StringBuilder();
                            for (java.util.Map.Entry<String, Double> e : z.abschnitte().entrySet()) {
                                if (e.getKey().startsWith("Wasser, Scheitel des Netzes") || e.getKey().startsWith("Wasser, Netz")) nz += e.getValue();
                                if (k == 1 && e.getKey().equals("Wasser")) wasserAlt = e.getValue();
                                if (k == 0 && e.getKey().equals("Wasser, Netz")) netzAllein = e.getValue();
                                if (k == 0 && e.getKey().equals("Wasser")) wasserNeben = e.getValue();
                                if (k == 3 && e.getKey().equals("Wasser, Netz, Vorlauf")) vorlaufAllein = e.getValue();
                                if (k != 1 && (e.getKey().startsWith("Wasser") || e.getKey().startsWith("Seegang") || e.getKey().startsWith("Brandung")))
                                    (k == 0 ? ab : abV).append(f("%s %.2f; ", e.getKey(), e.getValue()));
                            }
                            if (k != 1) n6[k == 0 ? 0 : 1] = nz;
                            if (k == 0) abschnitte = ab.toString();
                        }
                    }
                    w.seegang = true;
                    w.netz = true;
                    w.vorlauf = false;
                    b.brandung(true);
                    if (sf != null) {
                        sf.bild.an = true;
                        // seit S3c-6 (N6): ein Bild mit Zählern je Bildpunkt (ohne Vorlauf): Bildpunkte des Netzes, Überzeichnung
                        w.zaehlen = true;
                        b.standbild(bl, t);
                        long[] zl = w.zaehlung();
                        w.zaehlen = false;
                        b.standbild(bl, t);
                        double pxN = zl == null ? 0 : zl[0], ueber = zl == null || zl[0] == 0 ? 0 : (double) zl[1] / zl[0];
                        double mehr1 = n6[0] + wasserNeben - wasserAlt;             // seit S3c-7 (SF32 A): N6 neu
                        // seit S3d (Werkbuch 27): N6 als Median aus drei Durchgängen (das Rauschen zwischen Läufen ist 1–2 ms)
                        double mehr2 = mehrMessen(b, w, sf, bl, t, zeiten), mehr3 = mehrMessen(b, w, sf, bl, t, zeiten);
                        double[] mm = {mehr1, mehr2, mehr3};
                        java.util.Arrays.sort(mm);
                        double mehr = mm[1];
                        A.info("N6 " + nr, f("Mehrkosten des Wassers mit Netz %+.2f ms (Median aus %+.2f, %+.2f, %+.2f; im ersten: Scheitel und Netz %.2f ms, alter Weg daneben %.2f ms, alter Weg allein %.2f ms); "
                                + "mit Vorlauf der Tiefe %.2f ms (Vorlauf %.2f ms); Netz an %.0f Bildpunkten (%.1f %%), Aufrufe bis zur Optik je Bildpunkt %.3f (am meisten %d, mehr als einer an %d); "
                                + "Schattierung des Netzes %.2f ns je Bildpunkt; Durchgänge (ms): %s; mit Vorlauf: %s",
                                mehr, mehr1, mehr2, mehr3, n6[0], wasserNeben, wasserAlt, n6[1], vorlaufAllein, pxN, 100 * pxN / ((double) breite * hoehe), ueber, zl == null ? 0 : zl[2], zl == null ? 0 : zl[3],
                                pxN > 0 ? 1e6 * netzAllein / pxN : 0, abschnitte, abV));
                        if (zl != null)
                            A.info("N6 Zähler " + nr, f("B47 an %d Bildpunkten (%.1f %% des Netzes); alter Weg: %d Aufrufe, im Netz nach der Suche verworfen %d Bildpunkte (%.1f %% des Netzes), bis zur Optik %d Bildpunkte",
                                    zl[8], pxN > 0 ? 100.0 * zl[8] / pxN : 0, zl[10], zl[9], pxN > 0 ? 100.0 * zl[9] / pxN : 0, zl[11]));
                        summeN6 += n6[0];
                        summeN6ohne += n6[1];
                        summeMehr += mehr;
                        n6zahl++;
                        if (n6[0] > groessteN6) { groessteN6 = n6[0]; groessteN6Name = f("%02d", nr); }
                        if (mehr > groessteMehr) { groessteMehr = mehr; groessteMehrName = f("%02d", nr); }
                        groessteUeber = Math.max(groessteUeber, ueber);
                        Double frueher = n6Je.putIfAbsent(eintrag.strip(), n6[0]);
                        if (frueher != null) A.info("N6 kalt gegen warm", f("Bild %02d ist %s noch einmal: Netz und Schattierung %.2f ms, am Anfang %.2f ms (%.2f-fach)",
                                nr, eintrag.strip(), n6[0], frueher, n6[0] / frueher));
                        naehte(A, b, bl, sf, p, breite, hoehe, nr);
                        if (diag.contains(nr)) diagnose(A, b, sz, offenS, bl, t, an, sf, kennung, nr, ordner, breite, hoehe);
                    }
                    String name = f("%02d %s – %s, %s MEZ", nr, sz.ansicht(q[1]).name, q[0], UHR.format(Instant.parse(q[2])));
                    if (sf != null) {
                        A.info("Bild " + name, f("%s; mittlere Helligkeit %.3f; Karte: alles mit Netz %.2f ms, wie bisher ohne Netz %.2f ms, ohne Seegang %.2f ms – neue Fläche %+.2f ms, bisher %+.2f ms",
                                datei, hell, ms[0], ms[1], ms[2], ms[0] - ms[2], ms[1] - ms[2]));
                        summeNetz += ms[0] - ms[2];
                        summeAlt += ms[1] - ms[2];
                        mitNetz++;
                        b.seegang(null);
                        sf.close();
                    } else
                        A.info("Bild " + name, f("%s; mittlere Helligkeit %.3f; Karte: alles %.2f ms, ohne Brandung %.2f ms, ohne Seegang %.2f ms – Seegang und Brandung %+.2f ms",
                                datei, hell, ms[0], ms[1], ms[2], ms[0] - ms[2]));
                    liste.add(new Bild(name, bi));
                    gerechnet++;
                    summeAlles += ms[0];
                    summeSee += ms[0] - ms[2];
                    if (ms[0] - ms[2] > groesste) { groesste = ms[0] - ms[2]; groessteName = name; }
                }
            } finally {
                if (b != null) b.close();
            }
            A.pruefe(gerechnet > 0 && leer == 0, "Prüfstein S0.1: jedes Bild gerechnet und nicht schwarz", f("%d Bilder, %d schwarz", gerechnet, leer), "alle, keins schwarz");
            A.pruefe(unter == 0, f("SF29: kein Auge unter dem Wasser (Spiegel plus Hs/2; Wasser am Ort des Auges bekannt bei %d von %d)", mitWasser, gerechnet),
                    f("%d von %d (gehoben %d)", unter, gerechnet, gehoben), "0");
            if (n6zahl > 0) {
                A.hoechstens(f("N6 (seit S3c-7, SF32 A): Mehrkosten des Wassers mit Netz gegen den Weg ohne Netz je Bild, am meisten (Bild %s; im Mittel %+.2f ms)", groessteMehrName, summeMehr / n6zahl),
                        groessteMehr, 2.5, "ms");
                A.info("N6 bis S3c-6: Netz und Schattierung je Bild, am meisten", f("%.2f ms (Bild %s; im Mittel %.2f ms, mit Vorlauf der Tiefe %.2f ms)", groessteN6, groessteN6Name, summeN6 / n6zahl, summeN6ohne / n6zahl));
                A.info("N6: Überzeichnung des Netzes, am meisten", f("%.3f Aufrufe bis zur Optik je Bildpunkt des Netzes", groessteUeber));
            }
            if (mitNetz > 0)
                A.info("Kosten des Seegangs mit Netz und wie bisher (im Mittel je Bild)", f("%.2f ms gegen %.2f ms (%d Bilder)", summeNetz / mitNetz, summeAlt / mitNetz, mitNetz));
            if (gerechnet > 0)
                A.info("Kosten des Seegangs heute", f("im Mittel %.2f ms je Bild bei %.2f ms für alles; am meisten %.2f ms (%s)", summeSee / gerechnet, summeAlles / gerechnet, groesste, groessteName));
            if (!liste.isEmpty()) {
                Path ue = ordner.resolve(kennung + "_Uebersicht.png");
                ImageIO.write(bogen(liste), "png", ue.toFile());
                A.info("Kontaktbogen", ue.toString());
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", kennung + "_Bilder.md"), kennung + " – Bilder des Seegangs");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /**
     * N8 (seit S3c-5, B49): Helligkeit beidseits jeder Naht der Fläche – Rand der Clipmap (innerer Bereich des Netzes), Ränder
     * des feinen und des groben Nahfelds. Längs jeder Kante alle 10 m zwei Orte quer dazu im Abstand von je 3 Bildpunkten
     * (mindestens 3 m), beide nass und im Bild; verglichen wird das Mittel der Helligkeit innen gegen außen. Zählt nur mit
     * mindestens 50 Paaren.
     */
    static void naehte(Attest A, Buehne b, Blick bl, Seegangsflaeche sf, float[] p, int breite, int hoehe, int nr) {
        double[] nw = sf.bild.netzWerte(bl.ost, bl.nord);
        Oberflaeche.Stufe fe = sf.oberflaeche.fein, gr = sf.oberflaeche.grob;
        double[][] kanten = {
                {nw[2], nw[3], nw[4], nw[5]},
                {fe.x0, fe.x0 + fe.nx * fe.dx, fe.y0, fe.y0 + fe.ny * fe.dx},
                {gr.x0, gr.x0 + gr.nx * gr.dx, gr.y0, gr.y0 + gr.ny * gr.dx}};
        String[] namen = {"Rand der Clipmap", "Rand des feinen Nahfelds", "Rand des groben Nahfelds"};
        double pw = Math.toRadians(bl.bildwinkel) / hoehe;
        for (int q = 0; q < kanten.length; q++) {
            double[] r = kanten[q];
            double si = 0, sa = 0;
            int paare = 0;
            double[][] ecken = {{r[0], r[2]}, {r[1], r[2]}, {r[1], r[3]}, {r[0], r[3]}};
            for (int e = 0; e < 4; e++) {
                double[] a = ecken[e], c = ecken[(e + 1) % 4];
                double len = Math.hypot(c[0] - a[0], c[1] - a[1]);
                double tx = (c[0] - a[0]) / len, ty = (c[1] - a[1]) / len, nx = ty, ny = -tx;   // nx, ny: nach außen (Umlauf gegen den Uhrzeiger)
                for (double l = 5; l < len; l += 10) {
                    double ox = a[0] + tx * l, oy = a[1] + ty * l;
                    double dist = Math.hypot(ox - bl.ost, oy - bl.nord);
                    double d = Math.max(3, 3 * pw * Math.hypot(dist, bl.hoehe));
                    double[] in = {ox - nx * d, oy - ny * d}, aus = {ox + nx * d, oy + ny * d};
                    double[] wi = b.wasserUndSeegang(in[0], in[1]), wo = b.wasserUndSeegang(aus[0], aus[1]);
                    if (Double.isNaN(wi[0]) || Double.isNaN(wo[0])) continue;
                    double[] pi = bl.bildpunkt(bl.relativ(in[0], in[1], wi[0])), po = bl.bildpunkt(bl.relativ(aus[0], aus[1], wo[0]));
                    if (pi == null || po == null) continue;
                    int xi = (int) pi[0], yi = (int) pi[1], xo = (int) po[0], yo = (int) po[1];
                    if (xi < 0 || yi < 0 || xi >= breite || yi >= hoehe || xo < 0 || yo < 0 || xo >= breite || yo >= hoehe) continue;
                    si += hell(p, yi * breite + xi);
                    sa += hell(p, yo * breite + xo);
                    paare++;
                }
            }
            String text = f("N8: Bild %02d – %s, Helligkeit innen gegen außen − 1", nr, namen[q]);
            if (paare >= 50) A.hoechstens(text + f(" (%d Paare, innen %.3f, außen %.3f)", paare, si / paare, sa / paare), Math.abs(si / Math.max(1e-9, sa) - 1), 0.05, "");
            else A.info(text, f("%d Paare im Bild, zu wenige", paare));
        }
    }

    /**
     * Seit S3d: ein weiterer Durchgang für N6 – mit Netz (Scheitel, Netz und alter Weg daneben) und ohne Netz (alter Weg
     * allein), je der Median aus {@code zeiten} Bildern; gibt die Mehrkosten (ms) zurück. Danach wie vorher: alles an.
     */
    static double mehrMessen(Buehne b, Wasserbild w, com.dan.lauf.Seegangsflaeche sf, Blick bl, double t, int zeiten) {
        double[] r = new double[2];
        for (int k = 0; k < 2; k++) {
            w.seegang = true;
            b.brandung(true);
            sf.bild.an = k == 0;
            w.netz = k == 0;
            w.vorlauf = false;
            b.standbild(bl, t);
            Buehne.Bildzeiten z = b.bildzeiten(bl, t, zeiten, true);
            double s = 0;
            for (java.util.Map.Entry<String, Double> e : z.abschnitte().entrySet())
                if (k == 0 ? e.getKey().startsWith("Wasser, Scheitel des Netzes") || e.getKey().equals("Wasser, Netz") || e.getKey().equals("Wasser")
                        : e.getKey().equals("Wasser")) s += e.getValue();
            r[k] = s;
        }
        sf.bild.an = true;
        w.netz = true;
        return r[0] - r[1];
    }

    static double hell(float[] p, int k) { return 0.2126 * p[4 * k] + 0.7152 * p[4 * k + 1] + 0.0722 * p[4 * k + 2]; }

    /**
     * Diagnose (seit S3c-5, B49b): eine Karte von Hs des Nahfelds (grob und fein, Auge und Blick eingetragen) und dasselbe
     * Bild noch einmal mit doppeltem Anlauf; dazu die Änderung von Hs durch den zweiten Anlauf.
     */
    static void diagnose(Attest A, Buehne b, Szenen sz, Szenario s, Blick bl, double t, Ansicht an, Seegangsflaeche sf, String kennung, int nr, Path ordner,
                         int breite, int hoehe) throws Exception {
        Oberflaeche.Stufe gr = sf.oberflaeche.grob, fe = sf.oberflaeche.fein;
        double[] hsG = hs(gr), hsF = hs(fe);
        ImageIO.write(hsKarte(gr, hsG, fe, hsF, bl), "png", ordner.resolve(f("%s_%02d_Hs.png", kennung, nr)).toFile());
        Seegangsflaeche.ANLAUF_DOPPELT = true;
        Seegangsflaeche sf2;
        try {
            sf2 = Seegangsflaeche.baue(b.geraet(), b.konto(), sz.gebiet(), s.archiv.toPath(), b.seegangsnetze(), bl, t, an.fein);
        } finally {
            Seegangsflaeche.ANLAUF_DOPPELT = false;
        }
        try {
            A.info("Diagnose " + nr, "doppelter Anlauf: " + sf2.bericht);
            double[] hsG2 = hs(sf2.oberflaeche.grob), hsF2 = hs(sf2.oberflaeche.fein);
            A.info("Diagnose " + nr, f("Hs grob: Änderung durch den zweiten Anlauf %s; fein: %s", aenderung(hsG, hsG2), aenderung(hsF, hsF2)));
            ImageIO.write(hsKarte(sf2.oberflaeche.grob, hsG2, sf2.oberflaeche.fein, hsF2, bl), "png", ordner.resolve(f("%s_%02d_Hs_Anlauf2.png", kennung, nr)).toFile());
            Wasserbild w = b.wasserbild();
            sf2.bild.zeitQuelle = () -> w.zeit;
            b.seegang(sf2.bild);
            b.standbild(bl, t);
            float[] p2 = b.standbild(bl, t).clone();
            ImageIO.write(bild(p2, breite, hoehe), "png", ordner.resolve(f("%s_%02d_Anlauf2.png", kennung, nr)).toFile());
            naehte(A, b, bl, sf2, p2, breite, hoehe, 100 + nr);
        } finally {
            b.seegang(sf.bild);
            sf2.close();
        }
    }

    static double[] hs(Oberflaeche.Stufe s) {
        double[] h = new double[s.nc];
        int m = s.nb * s.nd;
        for (int c = 0; c < s.nc; c++) {
            double m0 = 0;
            for (int k = 0; k < m; k++) m0 += s.e[c * m + k];
            h[c] = 4 * Math.sqrt(Math.max(0, m0));
        }
        return h;
    }

    static String aenderung(double[] a, double[] b) {
        double gr = 0, su = 0, hm = 0;
        int n = 0;
        for (double v : a) hm = Math.max(hm, v);
        for (int c = 0; c < a.length; c++) {
            if (a[c] < 0.05 * hm && b[c] < 0.05 * hm) continue;
            double d = Math.abs(b[c] - a[c]) / Math.max(1e-6, Math.max(a[c], b[c]));
            gr = Math.max(gr, d);
            su += d;
            n++;
        }
        return f("im Mittel %.1f %%, größte %.1f %% (%d Zellen mit Hs über 5 %% des größten)", n > 0 ? 100 * su / n : 0, 100 * gr, n);
    }

    /** Karte von Hs: das grobe Gitter, darüber das feine; Farbe von dunkel (0) bis hell (größtes Hs); Auge rot, Blick als Linie. */
    static BufferedImage hsKarte(Oberflaeche.Stufe gr, double[] hg, Oberflaeche.Stufe fe, double[] hf, Blick bl) {
        int px = 1000;
        double w = gr.nx * gr.dx, m = 0;
        for (double v : hg) m = Math.max(m, v);
        for (double v : hf) m = Math.max(m, v);
        BufferedImage im = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < px; y++)
            for (int x = 0; x < px; x++) {
                double ost = gr.x0 + (x + 0.5) / px * w, nord = gr.y0 + w - (y + 0.5) / px * w;
                double v;
                int fi = (int) Math.floor((ost - fe.x0) / fe.dx), fj = (int) Math.floor((nord - fe.y0) / fe.dx);
                boolean imFein = fi >= 0 && fj >= 0 && fi < fe.nx && fj < fe.ny;
                if (imFein) v = hf[fj * fe.nx + fi];
                else {
                    int gi = Math.min(gr.nx - 1, (int) ((ost - gr.x0) / gr.dx)), gj = Math.min(gr.ny - 1, (int) ((nord - gr.y0) / gr.dx));
                    v = hg[gj * gr.nx + gi];
                }
                double q = m > 0 ? Math.min(1, v / m) : 0;
                int r = (int) (255 * Math.min(1, 1.6 * q * q)), g = (int) (255 * q), bb = (int) (255 * Math.min(1, 0.3 + 0.9 * q));
                if (v <= 0) r = g = bb = 30;
                if (imFein && (fi == 0 || fj == 0 || fi == fe.nx - 1 || fj == fe.ny - 1)) { r = 255; g = 255; bb = 255; }
                im.setRGB(x, y, r << 16 | g << 8 | bb);
            }
        java.awt.Graphics2D g2 = im.createGraphics();
        int ex = (int) ((bl.ost - gr.x0) / w * px), ey = (int) ((gr.y0 + w - bl.nord) / w * px);
        double a = Math.toRadians(bl.richtung);
        g2.setColor(java.awt.Color.RED);
        g2.fillOval(ex - 5, ey - 5, 10, 10);
        g2.drawLine(ex, ey, (int) (ex + 300 * Math.sin(a)), (int) (ey - 300 * Math.cos(a)));
        g2.setColor(java.awt.Color.WHITE);
        g2.drawString(f("Hs grob und fein, größtes %.2f m; Gitter %.0f m ab (%.0f, %.0f)", m, w, gr.x0, gr.y0), 8, 16);
        g2.dispose();
        return im;
    }

    static BufferedImage bild(float[] p, int breite, int hoehe) {
        BufferedImage im = new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < breite * hoehe; k++) {
            int r = Math.min(255, Math.max(0, Math.round(255 * p[4 * k]))), g = Math.min(255, Math.max(0, Math.round(255 * p[4 * k + 1]))),
                    bl = Math.min(255, Math.max(0, Math.round(255 * p[4 * k + 2])));
            im.setRGB(k % breite, k / breite, r << 16 | g << 8 | bl);
        }
        return im;
    }

    static double helligkeit(float[] p) {
        double s = 0;
        for (int k = 0; k < p.length; k += 4) s += 0.2126 * p[k] + 0.7152 * p[k + 1] + 0.0722 * p[k + 2];
        return s / (p.length / 4.0);
    }

    /** Kontaktbogen: drei Spalten zu 640 × 360 mit Beschriftung. */
    static BufferedImage bogen(List<Bild> liste) {
        int w = 640, h = 360, sp = 3, ze = (liste.size() + sp - 1) / sp;
        BufferedImage alle = new BufferedImage(sp * w, ze * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = alle.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int i = 0; i < liste.size(); i++) {
            int x = (i % sp) * w, y = (i / sp) * h;
            g.drawImage(liste.get(i).bild, x, y, w, h, null);
            String text = liste.get(i).text;
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(x + 4, y + 4, g.getFontMetrics().stringWidth(text) + 8, 18);
            g.setColor(Color.WHITE);
            g.drawString(text, x + 8, y + 17);
        }
        g.dispose();
        return alle;
    }
}
