package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Auflauf;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.swash.Swash;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Seegang S6c-2 (Seegang Werkbuch 82, SF93 A): der Auflauf im Bild der Anwendung. Die Bühne öffnet den Sturm, blickt auf die
 * Kante mit dem höchsten Seegang am Fuß ({@link AuflaufCheck#kante}), wartet, bis die Fläche steht, und lässt dann die
 * Bildschleife wie die Anwendung laufen (Zeitraffer 1, {@link #BILDER_JE_S} Bilder je s Modellzeit: Wart vor dem Bild,
 * Bild, Wart danach). Der Auflauf setzt sein Rechteck und legt je Bild sein Bild ab; Gelände und Wasser zeigen es.
 * Prüfsteine: Rechteck gesetzt, Bild des Rechtecks gegen den Löser (bitgleich, wie {@link AuflaufCheck}), Spülsaum an der
 * höchsten Linie. Zur Sichtprüfung Standbilder aus zwei Blicken (Kante, nah am Rechteck) und je eins mit dem Auflauf aus
 * ({@code -Dgelaende.swash=aus}, {@code -Dwasser.swash=aus} wirken wie Schalter) in {@code docs/status/seegang/S6c2_*.png}.
 * Attest {@code docs/status/S6c2_Bild.md}.
 * <p>
 * Aufruf: {@code java … com.dan.lauf.demo.AuflaufBildCheck --szenen <daten>/szenen.txt [--dauer 60]}.
 */
public final class AuflaufBildCheck {
    static final int BILDER_JE_S = 60;
    static final double DAUER = 60, BEREIT_S = 120;
    /** Standbilder nach so vielen Sekunden Modellzeit seit dem Setzen [G]. */
    static final double[] STAND = {20, 40, 60};
    /** Blick nah: Auge so weit seewärts der Wasserlinie (m), so hoch über dem Ruhewasser (m), Neigung (Grad) [G]. */
    static final double NAH_ABSTAND = 30, NAH_HOEHE = 4, NAH_NEIGUNG = -12;
    /** Seit Datei 83: Blick schräg längs: Auge so weit seewärts und längs von P, so hoch über dem Ruhewasser, Ziel landwärts von P (m), Neigung [G]. */
    static final double SEITE_SEE = 6, SEITE_LAENGS = 25, SEITE_HOEHE = 2.5, SEITE_ZIEL = 4, SEITE_NEIGUNG = -8;

    static Buehne b;
    static Flaechenwart w;
    static double t;
    static long uhrNs;

    public static void main(String[] args) {
        File szenen = null;
        double dauer = DAUER;
        int breite = 1920, hoehe = 1080;
        for (int i = 0; i + 1 < args.length; i += 2)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--dauer" -> dauer = Double.parseDouble(args[i + 1]);
                default -> { }
            }
        Attest A = new Attest("Seegang, Auflauf im Bild der Anwendung (S6c-2)", "Seegang Werkbuch");
        System.out.println("AuflaufBildCheck – S6c-2 (Seegang Werkbuch 82)");
        long start = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            Ansicht an = sz.ansicht("brandung");
            b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            SchaumCheck.b = b;
            try {
                w = b.wart();
                Auflauf au = b.auflauf();
                A.pruefe(au != null, "Auflauf an der Bühne", au == null ? "keiner" : "ja", "vorhanden");
                if (au == null) return;
                Szenario sc = SchaumCheck.szenario(sz, "seegang");
                b.oeffne(sc);
                Blick bl = SchaumCheck.blick(an, "2026-10-25T12:30:00Z", breite, hoehe);
                t = SchaumCheck.t;
                b.wasserbild().seegang = true;
                b.brandung(true);
                b.standbild(bl, t);
                Blick bk = AuflaufCheck.kante(A, b, t, breite, hoehe, "Sturm");
                if (bk == null) return;
                w.zeitraffer(1);
                uhrNs = System.nanoTime();
                long n0 = System.nanoTime();
                int bilder = 0;
                while (!w.bereit() && (System.nanoTime() - n0) / 1e9 < BEREIT_S) { bild(bk, false); bilder++; }
                A.pruefe(w.bereit(), "Fläche mit Nahfeld bereit", w.bereit() ? f("nach %.0f s Uhr, %d Bildern", (System.nanoTime() - n0) / 1e9, bilder) : w.bericht(), f("≤ %.0f s", BEREIT_S));
                if (!w.bereit()) return;
                // bis das Rechteck steht
                long g0 = au.gesetzt;
                for (int k = 0; k < 10 * BILDER_JE_S && !au.aktiv(); k++) bild(bk, false);
                A.pruefe(au.aktiv(), "Rechteck gesetzt", au.aktiv() ? au.bericht : "nein: " + au.bericht, "ja");
                if (!au.aktiv()) return;
                A.info("Setzen", au.bericht);
                double[] lage = au.lage();
                double richtung = Math.toDegrees(Math.atan2(lage[2], lage[3]));
                Blick nah = new Blick(lage[0] - NAH_ABSTAND * lage[2], lage[1] - NAH_ABSTAND * lage[3], lage[4] + NAH_HOEHE, richtung, NAH_NEIGUNG, 60);
                nah.groesse(breite, hoehe);
                // seit S6c-2b (Seegang Werkbuch 83): dazu ein Blick schräg längs des Ufers, tief über dem Wasser, auf die Zunge
                double lx = -lage[3], ly = lage[2];                                 // längs (+y des Rechtecks)
                double eo = lage[0] - SEITE_SEE * lage[2] + SEITE_LAENGS * lx, en = lage[1] - SEITE_SEE * lage[3] + SEITE_LAENGS * ly;
                double zo = lage[0] + SEITE_ZIEL * lage[2], zn = lage[1] + SEITE_ZIEL * lage[3];
                Blick seite = new Blick(eo, en, lage[4] + SEITE_HOEHE, Math.toDegrees(Math.atan2(zo - eo, zn - en)), SEITE_NEIGUNG, 60);
                seite.groesse(breite, hoehe);
                A.info("Blicke", f("Kante: Auge (%.0f, %.0f) in %.1f m NHN, Richtung %.0f°; nah: Auge %.0f m seewärts der Wasserlinie bei P (%.0f, %.0f), %.1f m über dem Ruhewasser, Richtung %.0f°, Neigung %.0f°",
                        bk.ost, bk.nord, bk.hoehe, bk.richtung, NAH_ABSTAND, lage[0], lage[1], NAH_HOEHE, richtung, NAH_NEIGUNG));
                Path ordner = Path.of("docs", "status", "seegang");
                Files.createDirectories(ordner);
                int n = (int) Math.round(dauer * BILDER_JE_S), si = 0;
                double tSetz = t;
                for (int q = 1; q <= n + 1; q++) {                                  // seit Datei 83: n + 1, sonst fehlt das letzte (L4o: 60 s)
                    boolean stand = si < STAND.length && t - tSetz >= STAND[si] - 0.5 / BILDER_JE_S;
                    bild(bk, false);
                    if (stand) {
                        // dieselbe Zeit noch einmal: der Auflauf rechnet nicht weiter (Auflauf 0.9), Gelände und Wasser zeigen ihn
                        speichere(A, bk, ordner.resolve(f("S6c2_kante_%02.0f.png", STAND[si])), f("Kante nach %.0f s", STAND[si]));
                        speichere(A, nah, ordner.resolve(f("S6c2_nah_%02.0f.png", STAND[si])), f("nah nach %.0f s", STAND[si]));
                        speichere(A, seite, ordner.resolve(f("S6c2_seite_%02.0f.png", STAND[si])), f("schräg längs nach %.0f s", STAND[si]));
                        si++;
                    }
                }
                // Vergleich: derselbe Augenblick ohne das Bild des Auflaufs (wie bis S6b)
                b.gelaendebild().swashAn = false;
                b.wasserbild().swashAn = false;
                speichere(A, nah, ordner.resolve("S6c2_nah_ohne.png"), "nah, ohne das Bild des Auflaufs (wie bis S6b)");
                // seit Datei 83: je eine Seite allein (Diagnose: wer zeichnet was)
                b.gelaendebild().swashAn = true;
                speichere(A, nah, ordner.resolve("S6c2_nah_nurGelaende.png"), "nah, das Bild des Auflaufs nur im Gelände");
                b.gelaendebild().swashAn = false;
                b.wasserbild().swashAn = true;
                speichere(A, nah, ordner.resolve("S6c2_nah_nurWasser.png"), "nah, das Bild des Auflaufs nur im Wasser");
                speichere(A, seite, ordner.resolve("S6c2_seite_nurWasser.png"), "schräg längs, das Bild des Auflaufs nur im Wasser");
                b.gelaendebild().swashAn = true;
                b.wasserbild().swashAn = true;
                A.pruefe(au.an, "Auflauf nach den Bildern noch an (kein Fehler)", au.an ? "ja" : au.bericht, "ja");
                A.pruefe(au.gesetzt == g0 + 1 || au.gesetzt == g0, "kein neues Setzen während der Bilder", f("%d", au.gesetzt - g0), "≤ 1");
                b.geraet().ruhe();
                au.karte().runter();
                Swash s = au.swash();
                AuflaufCheck.bildPruefen(A, au, s, "Anwendung");
            } finally {
                b.close();
            }
            A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - start) / 1e9));
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "S6c2_Bild.md"), "S6c-2 – Auflauf im Bild");
        System.out.println();
        System.out.println("Attest: " + p.toAbsolutePath());
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Ein Bild der Schleife wie die Anwendung: Wart davor, Bild, Wart danach; Zeit weiter um 1/{@link #BILDER_JE_S} s. */
    static void bild(Blick bl, boolean stand) {
        w.zeitraffer(1);
        w.vorBild(bl, t, uhrNs);
        b.bildzeiten(bl, t, 1);
        w.nachBild();
        t += 1.0 / BILDER_JE_S;
        uhrNs += Math.round(1e9 / BILDER_JE_S);
    }

    /** Ein Standbild zur Zeit des letzten Bilds (der Auflauf rechnet dabei nicht weiter) als PNG. */
    static void speichere(Attest A, Blick bl, Path datei, String was) throws Exception {
        double tb = t - 1.0 / BILDER_JE_S;
        float[] px = b.standbild(bl, tb);
        ImageIO.write(SeegangBilder.bild(px, bl.breite, bl.hoehe_px), "png", datei.toFile());
        A.info("Standbild, " + was, datei.toString().replace('\\', '/'));
    }
}
