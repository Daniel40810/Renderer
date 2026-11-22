package com.dan.lauf.demo;

import com.dan.core.archiv.Archiv;
import com.dan.core.astro.Gestirne;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.pruef.Attest;
import com.dan.core.pruef.Statistik;
import com.dan.lauf.Nachlauf;
import com.dan.physics.antrieb.Antrieb;
import com.dan.physics.atmos.Atmos;
import com.dan.physics.atmos.Luft;
import com.dan.physics.atmos.Profil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * atmos im Nachlauf aus dem Archiv (P10b Teil 1, Werkbuch HalligHooge Teil A, 12.11): die Prüfsteine 1–8 und, je Archiv
 * und Stunde, die Tabelle für die Wahl des Tags der Kimmung.
 * <ul>
 * <li><b>1 Wetter:</b> {@code antrieb/luft} lückenlos und endlich; die Sonnenstrahlung nachts 0 und nie über der eines
 * wolkenlosen Himmels (Haurwitz 1945: 1098 cos z · exp(−0,057 / cos z), aus dem Gedächtnis; Sonnenstand aus
 * {@code core.astro}) mal 1,05 [G].</li>
 * <li><b>2 Antrieb:</b> Was atmos vom Bus liest, gegen die Datei, hier noch einmal gerechnet (bilinear, zur Zeit; die
 * Strahlung zur Mitte der Stunde).</li>
 * <li><b>3 Speisen:</b> {@code water.h} am Bus gleich dem Stand des Archivs; Verdrahtung ohne Hinweis; Katalog.</li>
 * <li><b>4 Wiederholbar:</b> derselbe Nachlauf über {@link #KURZ} Schritte auf allen Fäden und auf einem.</li>
 * <li><b>5 Energiebilanz</b> über die gezählte Zeit; dazu die Temperatur der Oberfläche am ersten Mittag mit einem und
 * mit zwei Tagen Vorlauf.</li>
 * <li><b>6 Flüsse</b> über tiefem Wasser gegen ERA5 ({@code sshf}, {@code slhf} am Punkt {@link #SEE}).</li>
 * <li><b>7 Rechenzeit</b> aller Nachläufe.</li>
 * <li><b>8 Nässe</b> beim niedrigsten Wasserstand: Rang zwischen Nässe und Höhe der trockenen Zellen.</li>
 * </ul>
 * {@code --gebiet <ordner> --archiv <ordner>[,<ordner>…] [--name P10b_Atmos] [--ohne-ablage] [--auge 4]}. Die Prüfsteine
 * 4 und der Vorlauf in 5 laufen nur am ersten Archiv. Attest {@code docs/status/<name>.md}.
 */
public final class AtmosLaufCheck {
    private static Attest A;
    /** Schritte des kurzen Laufs für Prüfstein 4. */
    static final int KURZ = 48;
    /** Punkt von ERA5 über See westlich von Hooge (° O, ° N) für Prüfstein 6 [G]. */
    static final double[] SEE = {8.25, 54.5};
    static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneOffset.UTC);

    public static void main(String[] args) {
        List<String> a = List.of(args);
        File gebiet = new File(arg(a, "--gebiet", "D:\\Daten\\HalligHooge\\gebiet"));
        String archive = arg(a, "--archiv", "D:\\Daten\\HalligHooge\\_p9b\\archiv_ruhig"), name = arg(a, "--name", "P10b_Atmos");
        boolean ablegen = !a.contains("--ohne-ablage");
        double auge = Double.parseDouble(arg(a, "--auge", "4"));
        A = new Attest("com.dan.lauf (Nachlauf atmos)", Nachlauf.VERSION + ", Atmos " + Atmos.VERSION);
        long n0 = System.nanoTime();
        double rechen = 0;
        try {
            wetter(gebiet);
            boolean erstes = true;
            for (String ar : archive.split(",")) {
                rechen += nachlauf(gebiet, Path.of(ar.strip()), ablegen, erstes, auge);
                erstes = false;
            }
            A.teil("7 Rechenzeit");
            A.hoechstens("Prüfstein 7: alle Nachläufe mit Ablage (ohne die Läufe der Prüfsteine 4 und 5)", rechen / 60, 5.0, "min");
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.teil("Ablauf");
        A.info("Dauer", f("%.0f s", (System.nanoTime() - n0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", name + ".md"), "P10b – AtmosLaufCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static String arg(List<String> a, String name, String vorgabe) {
        int i = a.indexOf(name);
        return i >= 0 && i + 1 < a.size() ? a.get(i + 1) : vorgabe;
    }

    // ------------------------------------------------------------------ 1 Wetter

    static double haurwitz(double sinH) { return sinH <= 0 ? 0 : 1098 * sinH * Math.exp(-0.057 / sinH); }

    static void wetter(File gebiet) throws IOException {
        A.teil("1 Wetter (antrieb/luft)");
        Wetterfeld l = Wetterfeld.lies(gebiet, Wetterfeld.LUFT);
        Gradraster r = l.raster;
        int isw = l.groesse("ssrd"), n = r.punkte();
        long unendlich = 0, nachtFehler = 0, ueber = 0, tagWerte = 0;
        double groesstNacht = 0, groesstVerhaeltnis = 0, groesstUeber = 0;
        for (int q = 0; q < l.groessen.size(); q++)
            for (int k = 0; k < l.zeiten; k++)
                for (float v : l.feld(k, q)) if (!Float.isFinite(v)) unendlich++;
        int proben = 12;
        double[] klar = new double[n];
        boolean[] nacht = new boolean[n];
        for (int k = 0; k < l.zeiten; k++) {
            Arrays.fill(klar, 0);
            Arrays.fill(nacht, true);
            for (int s = 0; s < proben; s++) {
                Gestirne.Stand st = Gestirne.bei(l.zeit(k).minusSeconds(Math.round(l.schritt * (s + 0.5) / proben)));
                for (int j = 0; j < r.ny(); j++)
                    for (int i = 0; i < r.nx(); i++) {
                        double h = st.sonne(r.laenge(i), r.breite(j)).hoeheGrad();
                        klar[j * r.nx() + i] += haurwitz(Math.sin(Math.toRadians(h))) / proben;
                        if (h > -2) nacht[j * r.nx() + i] = false;
                    }
            }
            float[] sw = l.feld(k, isw);
            for (int p = 0; p < n; p++) {
                if (nacht[p]) {
                    groesstNacht = Math.max(groesstNacht, Math.abs(sw[p]));
                    if (Math.abs(sw[p]) > 0.5) nachtFehler++;
                } else {
                    tagWerte++;
                    double zuviel = sw[p] - 1.05 * klar[p];
                    if (zuviel > 5) {                       // 5 W/m² Spiel bei tiefer Sonne [G]
                        ueber++;
                        groesstUeber = Math.max(groesstUeber, zuviel);
                    }
                    if (klar[p] > 50) groesstVerhaeltnis = Math.max(groesstVerhaeltnis, sw[p] / klar[p]);
                }
            }
        }
        long soll = (l.ende().getEpochSecond() - l.start.getEpochSecond()) / l.schritt + 1;
        A.pruefe(l.zeiten == soll && unendlich == 0 && l.groessen.containsAll(List.of("t2", "d2", "sst", "ssrd", "strd", "sshf", "slhf", "tcc", "tp")),
                "Prüfstein 1: jede Stunde mit allen neun Größen, an jedem Punkt ein Wert", f("%d Zeiten von %s bis %s, %d Größen, %d Werte ohne Zahl", l.zeiten, l.start, l.ende(), l.groessen.size(), unendlich),
                "lückenlos");
        A.pruefe(nachtFehler == 0, "Prüfstein 1: Sonnenstrahlung nachts (Sonne die ganze Stunde tiefer als −2°)", f("größter Wert %.3f W/m², %d über 0,5 W/m²", groesstNacht, nachtFehler), "0");
        // Feuchte aus dem Taupunkt: der Antrieb gegen physics.atmos (Prüfstein 2)
        double q = 0;
        for (double td = 263.15; td <= 298.15; td += 0.5)
            for (double p = 96000; p <= 105000; p += 1500) q = Math.max(q, Math.abs(Antrieb.feuchte(td, p) / Luft.feuchte(Luft.saettigung(td), p) - 1));
        A.hoechstens("Prüfstein 2: Feuchte aus dem Taupunkt im Antrieb gegen Luft.feuchte (−10 bis 25 °C, 960 bis 1050 hPa), relativ", q, 1e-6, "");
        A.pruefe(ueber == 0, "Prüfstein 1: Sonnenstrahlung nie über der eines wolkenlosen Himmels mal 1,05 (5 W/m² Spiel)",
                f("%d von %d Werten darüber, größter Überschuss %.1f W/m²; größtes Verhältnis zu wolkenlos %.2f (über 50 W/m²)", ueber, tagWerte, groesstUeber, groesstVerhaeltnis), "keiner [G]");
    }

    // ------------------------------------------------------------------ Nachlauf

    /** Was atmos in der Säule s vom Bus hat, gegen die Dateien: größte relative Abweichung über t2, q2, sw, lw, sst, Wind, Druck. */
    static double antriebGegenDatei(Nachlauf n, Wetterfeld wind, Wetterfeld luft, double t, int s) {
        Atmos at = n.atmos.atmos;
        Instant z = n.antrieb.zeit(t);
        int i = s % at.sx, j = s / at.sx;
        double[] ll = n.atmos.saeulen.grad(n.atmos.saeulen.x(i), n.atmos.saeulen.y(j));
        Gradraster.Gewichte gl = luft.raster.gewichte(ll[0], ll[1], null, 0, 1, 0), gw = wind.raster.gewichte(ll[0], ll[1], null, 0, 1, 0);
        Instant mitte = z.plusSeconds(luft.schritt / 2);
        double sw = Math.max(0, luft.wert(luft.groesse("ssrd"), mitte, gl)), lw = luft.wert(luft.groesse("strd"), mitte, gl);
        double u = Math.hypot(wind.wert(wind.groesse("u10"), z, gw), wind.wert(wind.groesse("v10"), z, gw));
        // die Feuchte rechnet der Antrieb an den Punkten der Luft und der Bus bilinear dazwischen
        double q = 0;
        for (int k = 0; k < gl.index().length; k++) {
            int p = gl.index()[k];
            Gradraster.Gewichte punkt = new Gradraster.Gewichte(new int[] {p}, new double[] {1}, true);
            Gradraster.Gewichte druck = wind.raster.gewichte(luft.raster.laenge(p % luft.raster.nx()), luft.raster.breite(p / luft.raster.nx()), null, 0, 1, 0);
            q += gl.gewicht()[k] * Antrieb.feuchte(luft.wert(luft.groesse("d2"), z, punkt), wind.wert(wind.groesse("msl"), z, druck));
        }
        double[][] paare = {{at.t2[s], luft.wert(luft.groesse("t2"), z, gl)}, {at.q2[s], q}, {at.sonne[s], sw}, {at.gegen[s], lw}, {at.tMeer[s], luft.wert(luft.groesse("sst"), z, gl)},
                {at.wind[s], u}, {at.druck[s], wind.wert(wind.groesse("msl"), z, gw)}};
        double g = 0;
        for (double[] p : paare) g = Math.max(g, Math.abs(p[0] - p[1]) / Math.max(1e-9, Math.max(Math.abs(p[1]), p == paare[2] ? 1 : 0)));
        return g;
    }

    /** Rangkorrelation nach Spearman (gleiche Werte mit dem mittleren Rang). */
    static double rang(double[] a, double[] b) { return Statistik.korrelation(raenge(a), raenge(b)); }

    static double[] raenge(double[] a) {
        Integer[] o = new Integer[a.length];
        for (int i = 0; i < o.length; i++) o[i] = i;
        Arrays.sort(o, (x, y) -> Double.compare(a[x], a[y]));
        double[] r = new double[a.length];
        for (int i = 0; i < o.length; ) {
            int e = i;
            while (e + 1 < o.length && a[o[e + 1]] == a[o[i]]) e++;
            for (int k = i; k <= e; k++) r[o[k]] = 0.5 * (i + e);
            i = e + 1;
        }
        return r;
    }

    /** Rechnet den Nachlauf zu einem Archiv mit allen Prüfungen; gibt die Rechenzeit des Laufs (s) zurück. */
    static double nachlauf(File gebiet, Path archiv, boolean ablegen, boolean erstes, double auge) throws IOException {
        String kurz = archiv.getFileName().toString();
        A.teil("Nachlauf " + kurz);
        Wetterfeld wind = Wetterfeld.lies(gebiet), luft = Wetterfeld.lies(gebiet, Wetterfeld.LUFT);
        Nachlauf.Einstellung e = new Nachlauf.Einstellung();
        e.gebiet = gebiet;
        e.archiv = archiv;
        e.ablegen = ablegen;
        e.aeolian = false;                              // seit P10c: der Flugsand hat seinen eigenen Check (AeolianLaufCheck); atmos rechnet hier mit Regen (F97 A)
        long n0 = System.nanoTime();
        double sekunden;
        try (Nachlauf n = Nachlauf.bauen(e)) {
            Atmos at = n.atmos.atmos;
            double dt = n.speiser.kopplung;
            int mitte = at.saeule(at.nx / 2, at.ny / 2);
            A.info("Lauf", f("%s: %s bis %s, %d Stände alle %.0f s auf %s; Vorlauf %.0f h ab %s; Säulen %d × %d zu %.0f m; Gelände im Fenster %.2f bis %.2f m NHN", kurz,
                    n.antrieb.zeit(n.erste), n.antrieb.zeit(n.letzte), n.speiser.archiv.staende(), dt, n.speiser.gitter, e.vorlauf / 3600, n.antrieb.zeit(n.beginn), at.sx, at.sy,
                    n.atmos.saeulen.dx, min(n.atmos.grund), max(n.atmos.grund)));
            A.pruefe(n.hinweise.isEmpty() && Katalog.grundstufe().namen().size() == 48, "Prüfstein 3: Verdrahtung ohne Hinweis; Katalog", n.hinweise + ", " + Katalog.grundstufe().namen().size() + " Felder",
                    "kein Hinweis; 48");
            double antrieb = antriebGegenDatei(n, wind, luft, n.beginn, mitte);
            n.bis(n.erste);
            antrieb = Math.max(antrieb, antriebGegenDatei(n, wind, luft, n.erste, mitte));
            // gezählte Zeit
            at.eStrahlung = at.eFuehlbar = at.eLatent = at.eZufluss = at.eMeer = 0;
            at.begrenzt = 0;
            double e0 = at.waerme();
            Archiv see = Files.isRegularFile(archiv.resolve("waves_" + n.speiser.gitter.name + "w").resolve("lauf.txt")) ? Archiv.oeffnen(archiv.resolve("waves_" + n.speiser.gitter.name + "w")) : null;
            Feld hs = see == null ? null : see.feld("waves.hs", Katalog.grundstufe());
            double hW = 0, leW = 0, pegelTief = Double.MAX_VALUE, tTief = 0;
            long nW = 0, schritte = 0;
            double[] wetTief = null;
            boolean[] trockenTief = null;
            List<String> zeilen = new ArrayList<>();
            double groesstWinkel = 0, kleinstWinkel = Double.MAX_VALUE, tGroesst = 0, tKleinst = 0;
            for (double t = n.erste + dt; t <= n.letzte + 1e-6; t += dt) {
                n.bis(t);
                schritte++;
                double sh = 0, sl = 0;
                long nass = 0, tief = 0, land = 0;
                double wetLand = 0;
                for (int c = 0; c < at.nc; c++) {
                    if (at.h[c] >= at.hTief) { sh += at.hf[c]; sl += at.le[c]; tief++; }
                    if (at.h[c] > 0) nass++;
                    else { land++; wetLand += at.wet[c]; }
                }
                if (tief > 0) { hW += sh / tief; leW += sl / tief; nW++; }
                if (!Double.isNaN(n.atmos.pegel) && n.atmos.pegel < pegelTief) {
                    pegelTief = n.atmos.pegel;
                    tTief = t;
                    wetTief = at.wet.clone();
                    trockenTief = new boolean[at.nc];
                    for (int c = 0; c < at.nc; c++) trockenTief[c] = at.h[c] == 0;
                }
                if (Math.abs(t / 1800 - Math.rint(t / 1800)) < 1e-9) antrieb = Math.max(antrieb, antriebGegenDatei(n, wind, luft, t, mitte));
                if (Math.abs(t / 3600 - Math.rint(t / 3600)) < 1e-9) {
                    // Säule mit dem meisten Wasser, der Mitte am nächsten
                    int beste = mitte;
                    double besteGuete = -1;
                    for (int s = 0; s < at.ns; s++) {
                        int i0 = s % at.sx * at.teiler, j0 = s / at.sx * at.teiler, w = 0;
                        for (int j = j0; j < j0 + at.teiler; j++) for (int i = i0; i < i0 + at.teiler; i++) if (at.h[i + at.nx * j] >= at.hTief) w++;
                        double ab = Math.hypot(s % at.sx - at.sx / 2.0, s / at.sx - at.sy / 2.0), guete = w - 0.01 * ab;
                        if (guete > besteGuete) { besteGuete = guete; beste = s; }
                    }
                    Profil pr = at.profile[beste];
                    double welle = 0;
                    if (see != null) {
                        see.lies("waves.hs", t, hs);
                        double sum = 0;
                        int m = 0;
                        for (int k = 0; k < hs.laenge; k++) if (hs.hol(k) > 0) { sum += hs.hol(k); m++; }
                        welle = m > 0 ? sum / m : 0;
                    }
                    double sperr = Math.max(0.02, welle / 2), min = 60 * 180 / Math.PI;
                    double glatt = KimmungCheck.grenzwinkelFrei(pr, auge, 0) * min, mitWelle = KimmungCheck.grenzwinkelFrei(pr, auge, sperr) * min;
                    double normal = KimmungCheck.grenzwinkelFrei(Profil.ausGefaelle(at.t2[beste], -0.0065, at.druck[beste], at.lambda, at.co2), auge, sperr) * min;
                    if (mitWelle - normal > groesstWinkel) { groesstWinkel = mitWelle - normal; tGroesst = t; }
                    if (mitWelle - normal < kleinstWinkel) { kleinstWinkel = mitWelle - normal; tKleinst = t; }
                    if (Math.abs(t / 7200 - Math.rint(t / 7200)) < 1e-9)
                        zeilen.add(f("%s: Luft %.1f °C, Wasser %.1f °C (%+.1f K), Wind %.1f m/s, Sonne %.0f W/m², Pegel %+.2f m, %d %% nass, Nässe an Land %.2f; H %+.0f, LE %+.0f W/m² über Wasser; "
                                        + "Hs %.2f m; Kimm %.2f′ (glatt %.2f′, Normalatmosphäre %.2f′)", TAG.format(n.antrieb.zeit(t)), at.t2[beste] - 273.15, at.tMeer[beste] - 273.15,
                                at.tMeer[beste] - at.t2[beste], at.wind[beste], at.sonne[beste], n.atmos.pegel, 100 * nass / at.nc, land > 0 ? wetLand / land : Double.NaN,
                                tief > 0 ? sh / tief : Double.NaN, tief > 0 ? sl / tief : Double.NaN, welle, mitWelle, glatt, normal));
                }
            }
            sekunden = (System.nanoTime() - n0) * 1e-9;
            if (see != null) see.close();
            double rest = at.waerme() - e0 - (at.eStrahlung - at.eFuehlbar - at.eLatent + at.eZufluss + at.eMeer);
            double bezug = Math.abs(at.eStrahlung) + Math.abs(at.eFuehlbar) + Math.abs(at.eLatent);
            A.hoechstens("Prüfstein 2: Antrieb in der mittleren Säule gegen die Dateien (t2, q2, sw, lw, sst, Wind, Druck), jede halbe Stunde, relativ", antrieb, 1e-6, "");
            if (n.ablage != null && n.ablage.pruefe != null)
                A.pruefe(n.ablage.groessterUnterschied == 0 && n.ablage.verglichen == schritte + 1, "Prüfstein 3: water.h am Bus gegen den Stand des Archivs",
                        f("größter Unterschied %.1e m über %d Stände", n.ablage.groessterUnterschied, n.ablage.verglichen), "bitgleich");
            A.hoechstens("Prüfstein 5: Energiebilanz über die gezählte Zeit, relativ zu Strahlung + fühlbar + latent", rest / bezug, 1e-6, "");
            A.info("Posten (Mittel über die Zellen und die Zeit, W/m²)", f("Strahlung %+.1f, fühlbar %+.1f, latent %+.1f; Verdunstung am Vorrat begrenzt in %d Zellschritten "
                            + "(Zufluss und Austausch mit dem Meer zählen gegen 0 K und stehen deshalb nicht hier)",
                    at.eStrahlung / at.nc / (schritte * dt), at.eFuehlbar / at.nc / (schritte * dt), at.eLatent / at.nc / (schritte * dt), at.begrenzt));
            // 6 Flüsse gegen ERA5
            Gradraster.Gewichte gs = luft.raster.gewichte(SEE[0], SEE[1], null, 0, 1, 0);
            double hE = 0, lE = 0;
            int nE = 0;
            for (double t = n.erste + 3600; t <= n.letzte + 1e-6; t += 3600) {
                hE -= luft.wert(luft.groesse("sshf"), n.antrieb.zeit(t), gs);
                lE -= luft.wert(luft.groesse("slhf"), n.antrieb.zeit(t), gs);
                nE++;
            }
            hE /= nE; lE /= nE; hW /= Math.max(1, nW); leW /= Math.max(1, nW);
            A.pruefe(Math.abs(hW - hE) <= Math.max(15, 0.3 * Math.abs(hE)) && Math.abs(leW - lE) <= Math.max(15, 0.3 * Math.abs(lE)),
                    f("Prüfstein 6: fühlbare und latente Wärme über tiefem Wasser gegen ERA5 bei %.2f° O, %.2f° N, Mittel über die Zeit", SEE[0], SEE[1]),
                    f("H %+.1f gegen %+.1f W/m², LE %+.1f gegen %+.1f W/m²", hW, hE, leW, lE), "±30 % oder ±15 W/m² [G]");
            // 8 Nässe beim niedrigsten Wasserstand
            if (wetTief != null && n.atmos.grund != null) {
                int m = 0;
                for (boolean b : trockenTief) if (b) m++;
                double[] w = new double[m], z = new double[m];
                double[] klassen = {-9, -0.5, 0.5, 1.5, 9};
                double[] sum = new double[4];
                int[] zahl = new int[4];
                for (int c = 0, k = 0; c < at.nc; c++)
                    if (trockenTief[c]) {
                        w[k] = wetTief[c];
                        z[k++] = n.atmos.grund[c];
                        for (int q = 0; q < 4; q++) if (n.atmos.grund[c] >= klassen[q] && n.atmos.grund[c] < klassen[q + 1]) { sum[q] += wetTief[c]; zahl[q]++; }
                    }
                double rg = m > 2 ? rang(w, z) : Double.NaN;
                A.pruefe(rg <= -0.5, f("Prüfstein 8: Rang zwischen Nässe und Höhe der %d trockenen Zellen beim niedrigsten Wasserstand (%s, %+.2f m NHN)", m, TAG.format(n.antrieb.zeit(tTief)), pegelTief),
                        f("%.2f", rg), "≤ −0,5 [G]");
                A.info("Nässe nach der Höhe", f("unter −0,5 m NHN: %.2f (%d Zellen); −0,5 bis +0,5 m: %.2f (%d); +0,5 bis +1,5 m: %.2f (%d); über +1,5 m: %.2f (%d)", zahl[0] > 0 ? sum[0] / zahl[0] : Double.NaN,
                        zahl[0], zahl[1] > 0 ? sum[1] / zahl[1] : Double.NaN, zahl[1], zahl[2] > 0 ? sum[2] / zahl[2] : Double.NaN, zahl[2], zahl[3] > 0 ? sum[3] / zahl[3] : Double.NaN, zahl[3]));
            }
            A.info("Kimm gegen die Normalatmosphäre (Auge " + f("%.0f", auge) + " m, Fläche frei ab Hs/2, mindestens 2 cm)",
                    f("am tiefsten %+.2f′ (%s), am höchsten %+.2f′ (%s); positiv: untere Spiegelung, negativ: Hebung", groesstWinkel, TAG.format(n.antrieb.zeit(tGroesst)), kleinstWinkel,
                            TAG.format(n.antrieb.zeit(tKleinst))));
            for (String z : zeilen) A.info("Stunde", z);
            A.info("Zeiten", f("Nachlauf %.1f s, davon atmos %.1f s (%.3f s je Schritt), Ablage %.1f s (%d Stände, %.0f MB)", sekunden, n.atmos.sekunden, n.atmos.sekunden / Math.max(1, n.atmos.schritte),
                    n.ablage == null ? 0 : n.ablage.sekunden, n.ablage == null ? 0 : n.ablage.staende, n.ablage == null ? 0 : n.ablage.bytes / 1e6));
        }
        if (erstes) {
            wiederholbar(gebiet, archiv);
            vorlauf(gebiet, archiv);
        }
        return sekunden;
    }

    static double min(float[] a) { double m = Double.MAX_VALUE; for (float v : a) m = Math.min(m, v); return m; }

    static double max(float[] a) { double m = -Double.MAX_VALUE; for (float v : a) m = Math.max(m, v); return m; }

    // ------------------------------------------------------------------ 4 wiederholbar

    /** Ein Nachlauf ohne Ablage und ohne Prüfung des Speisens, am Beginn. */
    static Nachlauf kurz(File gebiet, Path archiv, boolean parallel, double vorlauf) throws IOException {
        Nachlauf.Einstellung e = new Nachlauf.Einstellung();
        e.gebiet = gebiet;
        e.archiv = archiv;
        e.ablegen = false;
        e.pruefen = false;
        e.aeolian = false;
        e.parallel = parallel;
        e.vorlauf = vorlauf;
        return Nachlauf.bauen(e);
    }

    static void wiederholbar(File gebiet, Path archiv) throws IOException {
        A.teil("4 Wiederholbar");
        try (Nachlauf a = kurz(gebiet, archiv, true, 86400); Nachlauf b = kurz(gebiet, archiv, false, 86400)) {
            double dt = a.speiser.kopplung;
            a.bis(a.beginn + KURZ * dt);
            b.bis(b.beginn + KURZ * dt);
            Atmos x = a.atmos.atmos, y = b.atmos.atmos;
            boolean gleich = Arrays.equals(x.ts, y.ts) && Arrays.equals(x.wet, y.wet) && Arrays.equals(x.tb, y.tb) && Arrays.equals(x.tw, y.tw) && x.eStrahlung == y.eStrahlung && x.eLatent == y.eLatent;
            for (int s = 0; s < x.ns && gleich; s++) gleich = Arrays.equals(x.profile[s].r, y.profile[s].r);
            A.pruefe(gleich, f("Prüfstein 4: %d Schritte auf allen Fäden und auf einem: Oberfläche, Boden, Wasser, Nässe, Profile, Bilanz", KURZ), gleich ? "bitgleich" : "VERSCHIEDEN", "bitgleich");
            A.info("Zeiten", f("auf allen Fäden %.3f s je Schritt, auf einem %.3f s", a.atmos.sekunden / a.atmos.schritte, b.atmos.sekunden / b.atmos.schritte));
        }
    }

    // ------------------------------------------------------------------ 5 Vorlauf

    static void vorlauf(File gebiet, Path archiv) throws IOException {
        A.teil("5 Empfindlichkeit auf den Vorlauf");
        double[] groesst = new double[2], mittel = new double[2];
        try (Nachlauf zwei = kurz(gebiet, archiv, true, 2 * 86400)) {
            double mittag = zwei.erste + 12 * 3600 - (zwei.antrieb.zeit(zwei.erste).getEpochSecond() % 86400);
            if (mittag <= zwei.erste) mittag += 86400;
            if (mittag > zwei.letzte) mittag = zwei.letzte;
            zwei.bis(mittag);
            double[] vorlaeufe = {86400, 0};
            for (int q = 0; q < 2; q++)
                try (Nachlauf n = kurz(gebiet, archiv, true, vorlaeufe[q])) {
                    n.bis(mittag);
                    Atmos x = n.atmos.atmos, y = zwei.atmos.atmos;
                    int m = 0;
                    for (int c = 0; c < x.nc; c++) {
                        if (x.h[c] >= x.hTief) continue;                     // tiefes Wasser hat die Temperatur des Meers
                        double d = Math.abs(x.ts[c] - y.ts[c]);
                        groesst[q] = Math.max(groesst[q], d);
                        mittel[q] += d;
                        m++;
                    }
                    mittel[q] /= Math.max(1, m);
                }
            A.hoechstens("Prüfstein 5: Temperatur der Oberfläche am ersten Mittag (" + TAG.format(zwei.antrieb.zeit(mittag)) + ") mit einem Tag Vorlauf gegen zwei, größter Unterschied außerhalb des tiefen Wassers",
                    groesst[0], 0.5, "K");
            A.info("Unterschiede gegen zwei Tage Vorlauf", f("ein Tag: größter %.2f K, im Mittel %.3f K; ohne Vorlauf: größter %.2f K, im Mittel %.3f K", groesst[0], mittel[0], groesst[1], mittel[1]));
        }
    }
}
