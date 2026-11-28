package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Seegangsflaeche;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S3d (Seegang Werkbuch 27–31): die neue Fläche des Seegangs in der Anwendung, als gespielte Sitzung über den
 * {@link Flaechenwart} der Bühne, Bild für Bild wie die Bildschleife ({@code vorBild}, Bild, {@code nachBild}); die Uhr des
 * Warts läuft 1/30 s je Bild. Seit S3d-2 (Werkbuch 31, SF38 A, SF39 A) läuft die Modellzeit mit dem Zeitraffer des Szenarios
 * (300: 10 s je Bild); die Überblenden misst sie in der Pause mit fester Belichtung, weil die laufende Zeit das Bild von
 * selbst bewegt (Befund L2n: bis 16 % von Bild zu Bild, auch bei Nahgewicht 0).
 * <ol>
 * <li>Öffnen (Sturm, „Brandung vor dem Japsand“, 25.10.2026 13:30 MEZ), laufende Zeit, bis die Fläche mit Nahfeld steht;</li>
 * <li>laufende Zeit, bis drei weitere Flächen gewechselt sind; über die Zeit der letzten die Abweichung der gezeigten Fläche
 * von der Zeit des Bilds; Bildzeit während des Baus;</li>
 * <li>Pause: Anhalten bei laufender Zeit (seit S3d-3: der Bau, der beim Anhalten läuft, rechnet rund 2 h voraus und fällt
 * weg); bis das Nahfeld zur Zeit des Bilds passt; bis nichts mehr baut; Ruhe (Bildzeit ohne Bau); die ruhende Fläche gegen
 * dasselbe Standbild aus {@link Seegangsflaeche#baue} (bitgleich, feste Belichtung, derselbe Ort des feinen Felds);</li>
 * <li>Pause, Fahrt 1,2 km längs des Blicks, dann Ruhe: ein Bau, Überblenden gemessen;</li>
 * <li>Pause, Sprung der Zeit um −3 h: die Bilder danach (alle Stufen, B54), ein Bau, Überblenden gemessen;</li>
 * <li>Wechsel nach Xaver (dieselbe Ansicht, 5.12.2013 14:30 MEZ), laufende Zeit, bis die Fläche steht.</li>
 * </ol>
 * Prüfsteine: bereit nach Öffnen und Wechsel ≤ 25 s [G]; Abweichung der gezeigten Fläche von der Zeit des Bilds bei
 * laufender Zeit ≤ 1,5 h [G] (SF39 A); Bildzeit während des Baus gegen ohne (Median) ≤ +25 % [G]; größter Sprung der
 * mittleren Helligkeit von Bild zu Bild beim Überblenden ≤ 5 % [G]; nach dem Anhalten passt das Nahfeld zur Zeit des Bilds
 * nach ≤ 30 s Uhr [G] (ein Bau mit Fläche und Einblenden), und keine eingesetzte Fläche weicht um 30 min oder mehr ab
 * (seit S3d-3); in der Pause je Anlass ein Bau; bitgleich; das
 * Speicherkonto der Flächen nach dem Wechsel wie nach dem ersten Bau, nach dem Schließen leer.
 * <pre>  SeegangAppCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--breite 1920 --hoehe 1080]</pre>
 */
public final class SeegangAppCheck {
    static final double DT_UHR = 1.0 / 30;
    static final double BEREIT_S = 25, BAU_MEHR = 0.25, SPRUNG_HELL = 0.05, ABWEICHUNG_S = 5400;
    /** Seit S3d-3: nach dem Anhalten passt das Nahfeld zur Zeit des Bilds nach höchstens so viel Uhr (s) [G]. */
    static final double ANHALTEN_S = 30;

    static Buehne b;
    static Flaechenwart w;
    static int breiteB, hoeheB;
    static double t, dtModell;
    static long uhrNs;
    static final List<Double> ruhe = new ArrayList<>(), imBau = new ArrayList<>();
    // seit L2p (Befund L2o, nur Auskunft): Bildzeit im Bau getrennt nach laufender Zeit und Pause; Bildzeit beim Öffnen
    static final List<Double> imBauLauf = new ArrayList<>(), imBauPause = new ArrayList<>(), oeffnen = new ArrayList<>();
    static int ersteStueckBild = -1, letzteStueckBild = -1;
    static double stueckeBereit = 0;
    static double hellSprung = 0, hellSprungTeil = 0;
    static int hellBilder = 0;
    static boolean hellMessen = false;
    static double vorAnlaufS = Double.NaN;
    static int bilderBereit = 0;
    // Protokoll der gelesenen Bilder, die zwei Bilder am größten Sprung, die Bilder nach dem Sprung der Zeit (seit L2n)
    static final StringBuilder hellLog = new StringBuilder("Teil;Bild;Uhr s;Modellzeit s;Phase;Nahgewicht;Helligkeit;Sprung\n");
    static String teilJetzt = "";
    static int bildNr = 0;
    static float[] vorherBild, sprungVor, sprungNach;
    static String sprungWo = "";
    static boolean zeitSprungMessen = false;
    static double hellZeitSprung = 0;
    // Abweichung der gezeigten Fläche (seit S3d-2)
    static boolean abweichungMessen = false;
    static double abweichungMax = 0, abweichungSumme = 0;
    static int abweichungBilder = 0, ohneBauBilder = 0;

    public static void main(String[] args) {
        File szenen = null;
        int breite = 1920, hoehe = 1080;
        for (int i = 0; i + 1 < args.length; i += 2)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--breite" -> breite = Integer.parseInt(args[i + 1]);
                case "--hoehe" -> hoehe = Integer.parseInt(args[i + 1]);
                default -> { }
            }
        Attest A = new Attest("Seegang, Fläche in der Anwendung (S3d)", "Seegang Werkbuch");
        System.out.println("SeegangAppCheck – S3d (Seegang Werkbuch)");
        long start = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            Szenario sturm = szenario(sz, "seegang"), xaver = szenario(sz, "xaver");
            Ansicht an = sz.ansicht("brandung");
            b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            breiteB = breite;
            hoeheB = hoehe;
            try {
                w = b.wart();
                teil(A, "1 Öffnen (Sturm), laufende Zeit");
                b.oeffne(sturm);
                Blick bl = blick(an, "2026-10-25T12:30:00Z", breite, hoehe);
                b.wasserbild().seegang = true;
                b.brandung(true);
                b.standbild(bl, t);                                          // Stände geladen, Belichtung steht
                long konto0 = w.konto().belegt();
                dtModell = Math.abs(sturm.zeitraffer) * DT_UHR;
                A.info("Zeitraffer des Szenarios", f("%.0f, also %.0f s Modellzeit je Bild", Math.abs(sturm.zeitraffer), dtModell));
                double bereit = bisBereit(bl, 90);
                A.hoechstens(f("Fläche mit Nahfeld bereit nach dem Öffnen (%s)", w.fehler() != null ? w.fehler() : w.bericht()), bereit, BEREIT_S, "s");
                zahlenZeigen(A);

                teil(A, "2 Laufende Zeit");
                int baue0 = (int) w.zahlen()[0];
                double s2 = bisGewechselt(bl, baue0 + 2, 240, false);
                A.info("zwei Flächen gewechselt", f("nach %.1f s Uhr; Schätzung des Warts: %s", s2, schaetzung()));
                abweichungMessen = true;
                double s3 = bisGewechselt(bl, baue0 + 3, 120, false);
                abweichungMessen = false;
                A.info("die dritte", f("nach weiteren %.1f s Uhr; %s", s3, w.bericht()));
                A.info("Bilder ohne Bau bei laufender Zeit (über die Zeit der dritten Fläche)", f("%d von %d", ohneBauBilder, abweichungBilder));
                A.hoechstens(f("Abweichung der gezeigten Fläche von der Zeit des Bilds, am meisten (im Mittel %.2f h, %d Bilder)", abweichungSumme / Math.max(1, abweichungBilder) / 3600, abweichungBilder),
                        abweichungBilder > 0 ? abweichungMax / 3600 : Double.NaN, ABWEICHUNG_S / 3600, "h");

                teil(A, "3 Pause: Anhalten, Ruhe, bitgleich");
                anhalten(A, bl);                                             // seit S3d-3; setzt dtModell = 0
                double s4 = bisRuhig(bl, 150);
                A.info("nichts mehr gebaut", f("nach %.1f s Uhr; %s", s4, schaetzung()));
                for (int k = 0; k < 40; k++) bild(bl, ruhe);
                double mRuhe = median(ruhe);
                A.info("Bildzeit der Karte ohne Bau (Median aus 40 Bildern)", f("%.2f ms", mRuhe));
                long konto1 = w.konto().belegt();
                bitgleich(A, bl, sz, sturm.archiv.toPath());

                // die Überblenden in der Pause mit fester Belichtung (wie BrandungBildCheck)
                var bel = b.belichtung();
                double evMin0 = bel.evMin, evMax0 = bel.evMax;
                bel.sofort();
                b.standbild(bl, t);
                double ev = bel.ev();
                bel.evMin = bel.evMax = ev;
                bel.setze(ev);
                A.info("feste Belichtung für die Überblenden", f("%+.2f Blenden", ev));
                hellMessen = true;

                teil(A, "4 Pause: Fahrt 1,2 km, dann Ruhe");
                double r = Math.toRadians(bl.richtung);
                for (int k = 0; k < 60; k++) {
                    bl.ost += 20 * Math.sin(r);
                    bl.nord += 20 * Math.cos(r);
                    bild(bl, null);
                }
                baue0 = (int) w.zahlen()[0];
                double neu = bisGewechselt(bl, baue0 + 1, 150, true);
                int baueFahrt = (int) w.zahlen()[0] - baue0;
                A.info("Neubau nach der Fahrt", f("fertig und übergeblendet nach %.1f s Uhr; %s", neu, w.bericht()));
                sprungZeigen(A);

                teil(A, "5 Pause: Sprung der Zeit um −3 h");
                w.zeitsprung();
                t -= 3 * 3600;
                {
                    float[] p0 = b.standbild(bl, t).clone();
                    double s0 = mittel(p0);
                    teilJetzt = "5 nach dem Sprung";
                    hellLog.append(f("%s;%d;%.3f;%.0f;%d;%.4f;%.6f;%s%n", teilJetzt, bildNr, uhrNs / 1e9, t, w.phase(), w.nahGewicht(), s0, "Standbild"));
                    hellVorher = s0;
                    vorherBild = null;
                    zeitSprungMessen = true;
                    bild(bl, null);
                    double ersterSprung = hellZeitSprung;
                    hellZeitSprung = 0;
                    for (int k = 0; k < 3; k++) bild(bl, null);
                    zeitSprungMessen = false;
                    teilJetzt = "5 Pause: Sprung der Zeit um −3 h";
                    A.info("nach dem Sprung (nicht im Prüfstein)", f("Standbild vor dem ersten Bild gegen das erste Bild %.2f %% (das Standbild fragt den Wart nicht); Bild 1 bis 4 am meisten %.3f %%",
                            100 * ersterSprung, 100 * hellZeitSprung));
                }
                baue0 = (int) w.zahlen()[0];
                neu = bisGewechselt(bl, baue0 + 1, 150, true);
                int baueSprung = (int) w.zahlen()[0] - baue0;
                A.info("Neubau nach dem Sprung", f("nach %.1f s Uhr; %s", neu, w.bericht()));
                sprungZeigen(A);
                hellMessen = false;
                bel.evMin = evMin0;
                bel.evMax = evMax0;
                bel.sofort();

                teil(A, "6 Wechsel nach Xaver, laufende Zeit");
                b.oeffne(xaver);
                bl = blick(an, "2013-12-05T13:30:00Z", breite, hoehe);
                b.standbild(bl, t);
                dtModell = Math.abs(xaver.zeitraffer) * DT_UHR;
                double bereitX = bisBereit(bl, 90);
                A.hoechstens(f("Fläche mit Nahfeld bereit nach dem Wechsel (%s)", w.fehler() != null ? w.fehler() : w.bericht()), bereitX, BEREIT_S, "s");
                zahlenZeigen(A);
                for (int k = 0; k < 10; k++) bild(bl, null);
                long konto2 = w.konto().belegt();

                teil(A, "Prüfsteine");
                double mBau = median(imBau);
                A.hoechstens(f("Bildzeit der Karte während des Baus gegen ohne Bau − 1 (Median aus %d gegen %d Bildern: %.2f gegen %.2f ms)", imBau.size(), ruhe.size(), mBau, mRuhe),
                        imBau.isEmpty() ? Double.NaN : mBau / mRuhe - 1, BAU_MEHR, "");
                A.hoechstens(f("größter Sprung der mittleren Helligkeit von Bild zu Bild beim Überblenden in der Pause, feste Belichtung (%d Bilder; am größten %s)", hellBilder, sprungWo),
                        hellBilder > 0 ? hellSprung : Double.NaN, SPRUNG_HELL, "");
                // seit L2p (Befund L2o, nur Auskunft): der Prüfstein darüber mischt Bilder bei laufender Zeit mit Ruhe in der Pause
                A.info("Bildzeit der Karte während des Baus, getrennt (Auskunft, seit L2p)", f("bei laufender Zeit %.2f ms aus %d Bildern, in der Pause %.2f ms aus %d Bildern (%+.0f %% gegen Ruhe in der Pause)",
                        median(imBauLauf), imBauLauf.size(), median(imBauPause), imBauPause.size(), 100 * (median(imBauPause) / mRuhe - 1)));
                A.pruefe(baueFahrt == 1 && baueSprung == 1, "in der Pause je Anlass ein Bau: Fahrt, Sprung", f("%d und %d", baueFahrt, baueSprung), "1 und 1");
                double[] z = w.zahlen();
                A.info("Anlauf in Stücken, alle Baue", f("%.0f Baue, %.0f Stücke, %.0f Runden, Wandzeit je Stück im Mittel %.2f ms, am meisten %.2f ms (Runden je Stück %d, ohne Fläche %d)",
                        z[0], z[1], z[2], z[3], z[4], Flaechenwart.RUNDEN_JE_BILD, Flaechenwart.RUNDEN_OHNE_FLAECHE));
                A.pruefe(konto2 == konto1, "Speicherkonto der Flächen nach dem Wechsel nach Xaver wie nach dem Bau in der Pause",
                        f("%.1f MB gegen %.1f MB (vor dem ersten Bau %.1f MB)", konto2 / 1048576.0, konto1 / 1048576.0, konto0 / 1048576.0), "gleich");
                w.close();
                A.pruefe(w.konto().belegt() == 0, "Speicherkonto der Flächen nach dem Schließen", f("%.1f MB", w.konto().belegt() / 1048576.0), "0");
                if (w.fehler() != null) A.pruefe(false, "Fehler eines Baus", w.fehler(), "keiner");
            } finally {
                b.close();
            }
            A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - start) / 1e9));
        } catch (Throwable e) {
            A.fehler(e);
        }
        try {
            java.nio.file.Files.writeString(Path.of("docs", "status", "S3d_App_Hell.csv"), hellLog.toString());
            if (sprungVor != null) {
                ImageIO.write(SeegangBilder.bild(sprungVor, breiteB, hoeheB), "png", Path.of("docs", "status", "S3d_App_Sprung_vor.png").toFile());
                ImageIO.write(SeegangBilder.bild(sprungNach, breiteB, hoeheB), "png", Path.of("docs", "status", "S3d_App_Sprung_nach.png").toFile());
                System.out.println("größter Sprung beim Überblenden: " + sprungWo);
            }
        } catch (Exception e) {
            System.out.println("Protokoll nicht geschrieben: " + e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "S3d_App.md"), "S3d – die neue Fläche des Seegangs in der Anwendung");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static Szenario szenario(Szenen sz, String schluessel) {
        for (Szenario x : sz.szenarien()) if (x.schluessel.equals(schluessel)) return x;
        throw new IllegalArgumentException("Szenario " + schluessel + " fehlt in der Szenendatei");
    }

    static Blick blick(Ansicht an, String zeit, int breite, int hoehe) {
        t = (Instant.parse(zeit).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        Blick bl = new Blick();
        an.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
        bl.groesse(breite, hoehe);
        double[] wa = b.wasserUndSeegang(bl.ost, bl.nord);
        Ansicht.hebe(bl, wa[0], wa[1], an.ueberWasser);
        return bl;
    }

    static double hellVorher = Double.NaN;

    /**
     * Ein Bild wie in der Bildschleife. In {@code zeiten} die Zeit der Karte (ms). Gelesen wird das Bild nur, wenn die
     * Helligkeit gemessen wird und ein Wechsel ansteht oder läuft, oder nach einem Sprung der Zeit (sonst wäre die Prüfung
     * langsamer als die App, L2l).
     */
    static void bild(Blick bl, List<Double> zeiten) {
        w.zeitraffer(dtModell / DT_UHR);                                  // seit S3d-3 wie die Bühne: der gewollte Zeitraffer
        w.vorBild(bl, t, uhrNs);
        bildNr++;
        boolean hell = zeitSprungMessen || hellMessen && w.wechselt();
        if (abweichungMessen) {
            double[] ort = w.bauort();
            if (ort != null) {
                double d = Math.abs(t - ort[3]);
                abweichungMax = Math.max(abweichungMax, d);
                abweichungSumme += d;
                abweichungBilder++;
                if (!w.baut() && !w.wechselt()) ohneBauBilder++;
            }
        }
        if (hell) {
            float[] p = b.standbild(bl, t).clone();
            double s = mittel(p);
            double sprung = Double.NaN;
            if (!Double.isNaN(hellVorher)) {
                sprung = Math.abs(s / hellVorher - 1);
                if (zeitSprungMessen) hellZeitSprung = Math.max(hellZeitSprung, sprung);
                else {
                    if (sprung > hellSprung && vorherBild != null) {
                        sprungVor = vorherBild;
                        sprungNach = p;
                        sprungWo = f("%s, Bild %d (Phase %d, Nahgewicht %.3f)", teilJetzt, bildNr, w.phase(), w.nahGewicht());
                    }
                    hellSprung = Math.max(hellSprung, sprung);
                    hellSprungTeil = Math.max(hellSprungTeil, sprung);
                    hellBilder++;
                }
            }
            hellLog.append(f("%s;%d;%.3f;%.0f;%d;%.4f;%.6f;%.5f%n", teilJetzt, bildNr, uhrNs / 1e9, t, w.phase(), w.nahGewicht(), s, sprung));
            hellVorher = s;
            vorherBild = p;
        } else {
            hellVorher = Double.NaN;
            vorherBild = null;
            Buehne.Bildzeiten z = b.bildzeiten(bl, t, 1);
            if (zeiten != null) zeiten.add(z.karte()[0]);
        }
        w.nachBild();
        t += dtModell;
        uhrNs += Math.round(DT_UHR * 1e9);
    }

    static double mittel(float[] p) {
        double s = 0;
        for (int k = 0; k < p.length / 4; k++) s += SeegangBilder.hell(p, k);
        return s / (p.length / 4);
    }

    /** Bilder, bis die Fläche mit vollem Nahfeld steht; gibt die Wandzeit (s) bis „bereit“ zurück. */
    static double bisBereit(Blick bl, double hoechstensS) {
        long n0 = System.nanoTime();
        double bereit = Double.NaN;
        double stuecke0 = w.zahlen()[1];
        vorAnlaufS = Double.NaN;
        bilderBereit = 0;
        ersteStueckBild = letzteStueckBild = -1;
        stueckeBereit = 0;
        oeffnen.clear();
        hellVorher = Double.NaN;
        while ((System.nanoTime() - n0) / 1e9 < hoechstensS) {
            bild(bl, Double.isNaN(bereit) ? oeffnen : null);
            if (Double.isNaN(bereit)) bilderBereit++;
            if (Double.isNaN(vorAnlaufS) && w.zahlen()[1] > stuecke0) vorAnlaufS = (System.nanoTime() - n0) / 1e9;
            double st = w.zahlen()[1] - stuecke0;                         // seit L2p: Bilder vom ersten bis zum letzten Stück
            if (Double.isNaN(bereit) && st > stueckeBereit) {
                if (ersteStueckBild < 0) ersteStueckBild = bilderBereit;
                letzteStueckBild = bilderBereit;
                stueckeBereit = st;
            }
            if (w.bereit() && Double.isNaN(bereit)) bereit = (System.nanoTime() - n0) / 1e9;
            if (w.bereit() && !w.wechselt()) break;
            if (w.fehler() != null) break;
        }
        return bereit;
    }

    /**
     * Bilder, bis {@code baue} Flächen gebaut und übergeblendet sind (mit {@code ohneBau}: und kein Bau mehr läuft);
     * Bildzeiten während eines Baus (Netz im Bild, kein Wechsel) gesammelt. Gibt die Zeit der Uhr (s) zurück.
     */
    static double bisGewechselt(Blick bl, int baue, double hoechstensS, boolean ohneBau) {
        long u0 = uhrNs, n0 = System.nanoTime();
        hellVorher = Double.NaN;
        while ((System.nanoTime() - n0) / 1e9 < hoechstensS) {
            boolean bau = w.baut() && !w.wechselt() && w.bereit();
            int vor = imBau.size();
            bild(bl, bau ? imBau : null);
            if (imBau.size() > vor) (dtModell > 0 ? imBauLauf : imBauPause).add(imBau.get(imBau.size() - 1));   // seit L2p
            if (w.zahlen()[0] >= baue && !w.wechselt() && (!ohneBau || !w.baut())) break;
            if (w.fehler() != null) break;
        }
        return (uhrNs - u0) / 1e9;
    }

    /** Bilder, bis zehn Bilder hintereinander nichts baut und nichts wechselt; gibt die Zeit der Uhr (s) zurück. */
    static double bisRuhig(Blick bl, double hoechstensS) {
        long u0 = uhrNs, n0 = System.nanoTime();
        int ruhig = 0;
        while ((System.nanoTime() - n0) / 1e9 < hoechstensS && ruhig < 10) {
            bild(bl, null);
            ruhig = !w.baut() && !w.wechselt() ? ruhig + 1 : 0;
            if (w.fehler() != null) break;
        }
        return (uhrNs - u0) / 1e9;
    }

    /**
     * Seit S3d-3: Anhalten bei laufender Zeit. Bilder, bis das Nahfeld zur Zeit des Bilds passt (Fläche bereit, kein Wechsel,
     * Abweichung unter {@link Flaechenwart#ALTER_S}); jede Fläche, die dabei eingesetzt wird, gegen die Zeit des Bilds.
     */
    static void anhalten(Attest A, Blick bl) {
        double[] ort = w.bauort();
        boolean lief = w.baut();
        A.info("beim Anhalten", f("Zeitraffer %.0f; die gezeigte Fläche weicht %+.2f h von der Zeit des Bilds ab; %s", dtModell / DT_UHR,
                ort == null ? Double.NaN : (ort[3] - t) / 3600, lief ? "ein Bau läuft" : "kein Bau läuft"));
        dtModell = 0;
        int verworfen0 = w.verworfen(), baue0 = (int) w.zahlen()[0], eingesetzt = 0, daneben = 0;
        double danebenMax = 0;
        Seegangsflaeche vorher = w.flaeche();
        long u0 = uhrNs, n0 = System.nanoTime();
        double passt = Double.NaN;
        while ((System.nanoTime() - n0) / 1e9 < 150) {
            bild(bl, null);
            Seegangsflaeche fl = w.flaeche();
            double[] o = w.bauort();
            if (fl != null && fl != vorher && o != null) {                 // eine Fläche eingesetzt
                vorher = fl;
                eingesetzt++;
                double d = Math.abs(o[3] - t);
                danebenMax = Math.max(danebenMax, d);
                if (d >= Flaechenwart.ALTER_S) daneben++;
            }
            if (o != null && w.bereit() && !w.wechselt() && Math.abs(o[3] - t) < Flaechenwart.ALTER_S) {
                passt = (uhrNs - u0) / 1e9;
                break;
            }
            if (w.fehler() != null) break;
        }
        A.info("nach dem Anhalten", f("%d verworfen (Bau oder fertige Fläche), %.0f gebaut, %d eingesetzt; %s",
                w.verworfen() - verworfen0, w.zahlen()[0] - baue0, eingesetzt, w.bericht()));
        A.hoechstens(f("nach dem Anhalten passt das Nahfeld zur Zeit des Bilds (Abweichung unter %.0f min, Fläche ganz eingeblendet)", Flaechenwart.ALTER_S / 60),
                passt, ANHALTEN_S, "s");
        A.pruefe(daneben == 0, "nach dem Anhalten keine Fläche eingesetzt, die 30 min oder mehr von der Zeit des Bilds abweicht",
                f("%d von %d (am meisten %.2f h)", daneben, eingesetzt, danebenMax / 3600), "0");
    }

    static String schaetzung() {
        double[] s = w.schaetzung();
        return f("Zeitraffer %.0f, Dauer eines Baus ohne Fläche %.1f s, mit Fläche %.1f s (Uhr)", s[0], s[1], s[2]);
    }

    /** Die ruhende Fläche des Warts gegen dasselbe Standbild aus Seegangsflaeche.baue (gleicher Bauort, Zielzeit, feines Feld). */
    static void bitgleich(Attest A, Blick bl, Szenen sz, Path archiv) throws Exception {
        double[] ort = w.bauort();
        Seegangsflaeche aktiv = w.flaeche();
        if (ort == null || aktiv == null) {
            A.pruefe(false, "ruhende Fläche des Warts gegen dasselbe Standbild aus Seegangsflaeche.baue", "keine Fläche", "bitgleich");
            return;
        }
        // seit S3d-2: feste Belichtung (Befund L2n: jedes Standbild misst neu, die Werte wichen um 1/255 ab, auch im Himmel)
        var bel = b.belichtung();
        double evMin0 = bel.evMin, evMax0 = bel.evMax;
        bel.sofort();
        w.vorBild(bl, t, uhrNs);
        b.standbild(bl, t);
        w.nachBild();
        double ev = bel.ev();
        bel.evMin = bel.evMax = ev;
        bel.setze(ev);
        // zwei Bilder zur selben Zeit: die fernen Stufen der Clipmap rechnet das Bild jedes zweite Mal (B54)
        w.vorBild(bl, t, uhrNs);
        b.standbild(bl, t);
        w.nachBild();
        w.vorBild(bl, t, uhrNs);
        float[] mitWart = b.standbild(bl, t).clone();
        w.nachBild();
        Blick bb = bl.kopie();
        bb.ost = ort[0];
        bb.nord = ort[1];
        bb.richtung = ort[2];
        // seit S3d-2: derselbe Ort des feinen Felds (die Suche nach der Brandungslinie liest die Netze des Bilds, die nur
        // zwei Stände um die Zeit des Bilds halten, L2n)
        Seegangsflaeche sf = Seegangsflaeche.baue(b.geraet(), b.konto(), sz.gebiet(), archiv, b.seegangsnetze(), bb, ort[3], new double[] {aktiv.feinOst, aktiv.feinNord});
        sf.bild.schaum = w.schaumfeld();   // seit S4a: dasselbe Schaumfeld (die Zeit steht: der Schritt ändert es nicht)
        try {
            sf.bild.zeit = t;
            sf.karte.fernOst = bl.ost;
            sf.karte.fernNord = bl.nord;
            b.seegang(sf.bild);
            b.standbild(bl, t);
            float[] standbild = b.standbild(bl, t).clone();
            long anders = 0;
            for (int i = 0; i < mitWart.length; i++) if (Float.floatToRawIntBits(mitWart[i]) != Float.floatToRawIntBits(standbild[i])) anders++;
            A.pruefe(anders == 0, "ruhende Fläche des Warts gegen dasselbe Standbild aus Seegangsflaeche.baue (feste Belichtung)", f("%d Werte anders", anders), "0 (bitgleich)");
            A.info("Bau des Warts", w.bericht());
            A.info("Bau zum Vergleich", sf.bericht);
            if (anders > 0) {
                int pk = 0, x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
                double gr = 0, grRel = 0;
                BufferedImage im = SeegangBilder.bild(mitWart, breiteB, hoeheB);
                for (int k = 0; k < mitWart.length / 4; k++) {
                    boolean a = false;
                    for (int c = 0; c < 4; c++) {
                        float u = mitWart[4 * k + c], v = standbild[4 * k + c];
                        if (Float.floatToRawIntBits(u) != Float.floatToRawIntBits(v)) {
                            a = true;
                            gr = Math.max(gr, Math.abs(u - v));
                            grRel = Math.max(grRel, Math.abs(u - v) / Math.max(1e-6, Math.max(Math.abs(u), Math.abs(v))));
                        }
                    }
                    if (a) {
                        pk++;
                        int x = k % breiteB, y = k / breiteB;
                        x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
                        for (int dy = -2; dy <= 2; dy++)
                            for (int dx = -2; dx <= 2; dx++) {
                                int xx = x + dx, yy = y + dy;
                                if (xx >= 0 && yy >= 0 && xx < breiteB && yy < hoeheB && (Math.abs(dx) == 2 || Math.abs(dy) == 2)) im.setRGB(xx, yy, 0xFF0000);
                            }
                    }
                }
                A.info("anders: wo und wie viel", f("%d Bildpunkte, im Rechteck x %d–%d, y %d–%d; größte Abweichung %.3g (relativ %.3g)", pk, x0, x1, y0, y1, gr, grRel));
                try {
                    ImageIO.write(im, "png", Path.of("docs", "status", "S3d_App_bitgleich.png").toFile());
                } catch (Exception e) {
                    A.info("Bild der Abweichung", "nicht geschrieben: " + e);
                }
            }
            b.seegang(aktiv.bild);
        } finally {
            bel.evMin = evMin0;
            bel.evMax = evMax0;
            bel.sofort();
            b.geraet().ruhe();
            sf.close();
        }
    }

    static void teil(Attest A, String name) {
        A.teil(name);
        teilJetzt = name;
    }

    static void zahlenZeigen(Attest A) {
        double[] z = w.zahlen();
        A.info("Anlauf bis hier", f("%.0f Baue, %.0f Stücke, %.0f Runden; Wandzeit je Stück (mit dem Warten hinter dem Bild) im Mittel %.2f ms, am meisten %.2f ms; Bau %.1f s", z[0], z[1], z[2], z[3], z[4], z[5]));
        A.info("bis bereit", f("%d Bilder; das erste Stück des Anlaufs nach %.1f s (davor baut die Quelle das Nahfeld)", bilderBereit, vorAnlaufS));
        // seit L2p (Befund L2o, nur Auskunft): rechnet der Anlauf je Bild ein Stück, und was kostet ein Bild dabei?
        A.info("Anlauf je Bild (Auskunft, seit L2p)", f("%.0f Stücke in %d Bildern vom ersten bis zum letzten Stück (%.2f je Bild, gedacht ist 1); Bildzeit der Karte bis bereit im Median %.2f ms aus %d Bildern",
                stueckeBereit, letzteStueckBild - ersteStueckBild + 1, stueckeBereit / Math.max(1, letzteStueckBild - ersteStueckBild + 1), median(oeffnen), oeffnen.size()));
    }

    /** Der größte Sprung der Helligkeit in diesem Teil (der Prüfstein nimmt den größten über alle Teile). */
    static void sprungZeigen(Attest A) {
        A.info("größter Sprung der Helligkeit in diesem Teil", f("%.2f %%", 100 * hellSprungTeil));
        hellSprungTeil = 0;
    }

    static double median(List<Double> l) {
        if (l.isEmpty()) return Double.NaN;
        double[] a = l.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(a);
        return a[a.length / 2];
    }
}
