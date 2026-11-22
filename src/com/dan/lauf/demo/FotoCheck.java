package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Foto;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Fotos (P11b, Werkbuch HalligHooge Teil A, 12.12; Datei 52, Prüfsteine 9–12) auf einer Bühne ohne Fenster.
 * <ul>
 * <li>Prüfstein 9: Das Foto rechnet dasselbe wie {@link com.dan.render.reference.Referenz} über denselben Ständen – zwei
 * Runden des Fotos gegen zwei Bilder der Referenz mit derselben Saat, bitgleich.</li>
 * <li>Prüfstein 10: zwei Fotos mit derselben Saat, bitgleich.</li>
 * <li>Prüfstein 11 (Auskunft): je Szenario aus dem Archiv das Foto seiner Ansicht zur Startzeit, in der Größe eines
 * halben Fensters ({@code --breite}, {@code --hoehe}, Vorgabe 800 × 410) mit der Zeitgrenze ({@code --sekunden},
 * Vorgabe 180); Rauschen, Pfade/s. Die Fotos liegen in {@code --ablage} (Vorgabe {@code docs/fotos/FotoCheck}).</li>
 * <li>Prüfstein 12: Abbrechen nach 5 s – Faden und Pool sind nach höchstens 15 s [G] beendet; die Ablage ist
 * vollständig (PNG in der Größe, PFM mit 12 Byte je Bildpunkt, Beschreibung).</li>
 * </ul>
 * Optionen: {@code --szenen} (Pflicht), {@code --nur} (Schlüssel der Szenarien, mit Komma), {@code --ohne-attest}.
 * Attest {@code docs/status/P11b_Foto.md}.
 */
public final class FotoCheck {
    private static final Attest A = new Attest("com.dan.lauf.Foto", Foto.VERSION);
    static final int BW = 1600, BH = 820;           // das Echtzeitbild wie die Leinwand der Anwendung (vor dem Maximieren)

    public static void main(String[] args) throws Exception {
        String szenen = null, nur = null, ablage = "docs" + File.separator + "fotos" + File.separator + "FotoCheck";
        int breite = 800, hoehe = 410;
        double sekunden = Foto.ZEITGRENZE;
        boolean attest = true;
        for (int i = 0; i < args.length; i++) {
            String v = i + 1 < args.length ? args[i + 1] : null;
            switch (args[i]) {
                case "--szenen" -> szenen = v;
                case "--nur" -> nur = v;
                case "--ablage" -> ablage = v;
                case "--breite" -> breite = Integer.parseInt(v);
                case "--hoehe" -> hoehe = Integer.parseInt(v);
                case "--sekunden" -> sekunden = Double.parseDouble(v);
                case "--ohne-attest" -> attest = false;
                default -> { }
            }
        }
        System.out.println("FotoCheck – com.dan.lauf.Foto " + Foto.VERSION + " (Werkbuch HalligHooge Teil A, P11b)");
        try {
            Szenen sz = Szenen.lies(new File(szenen));
            File gb = sz.gebiet();
            List<Szenario> liste = new ArrayList<>();
            for (Szenario s : sz.szenarien())
                if (!s.live() && (nur == null || List.of(nur.split(",")).contains(s.schluessel))) liste.add(s);
            A.info("Rahmen", f("%d Szenarien aus dem Archiv; Foto %d × %d, Zeitgrenze %.0f s, %d Fäden; Echtzeitbild %d × %d; Ablage %s", liste.size(), breite, hoehe, sekunden,
                    Math.max(1, Runtime.getRuntime().availableProcessors() - 1), BW, BH, ablage));
            pruefsteine9bis12(sz, gb, liste.get(0), new File(ablage));
            A.teil("Prüfstein 11: Fotos je Szenario (Auskunft)");
            for (Szenario s : liste) foto(sz, gb, s, breite, hoehe, sekunden, new File(ablage));
            int faeden = 0;
            for (Thread t : Thread.getAllStackTraces().keySet()) if (t.getName().equals("Foto") && t.isAlive()) faeden++;
            A.pruefe(faeden == 0, "Prüfstein 12: Fäden „Foto“ am Ende", String.valueOf(faeden), "0");
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P11b_Foto.md"), "P11b – FotoCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static Blick blick(Szenen sz, Szenario s, Buehne b, double t) {
        Ansicht a = s.ansicht != null ? sz.ansicht(s.ansicht) : sz.ansichten().get(0);
        Blick bl = new Blick();
        a.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
        return bl.groesse(BW, BH);
    }

    static void pruefsteine9bis12(Szenen sz, File gb, Szenario s, File ablage) throws Exception {
        A.teil("Prüfsteine 9, 10, 12 (" + s.name + ", Foto 160 × 82)");
        int w = 160, h = 82;
        try (Buehne b = Buehne.ohneFenster(gb, BW, BH)) {
            b.oeffne(s);
            double t = b.uhr().sekunden();
            Blick bl = blick(sz, s, b, t);
            // 9: zwei Runden des Fotos gegen die Referenz, dieselbe Saat
            Foto f1 = b.foto(bl, t, "Prüfung", w, h);
            f1.rundenMax = 2;
            f1.saat = 7;
            f1.rechne();
            Foto f0 = b.foto(bl, t, "Prüfung", w, h);
            double[][][] hf = f1.haelften();
            long ungleich = 0;
            for (int k = 0; k < 2; k++) {
                f0.ref.saat = 7 * 1_000_000L + k;
                double[][][] r = f0.ref.bild(bl, w, h, 1);
                for (int c = 0; c < 3; c++)
                    for (int i = 0; i < w * h; i++) if (Double.doubleToLongBits(r[0][c][i]) != Double.doubleToLongBits(hf[k][c][i])) ungleich++;
            }
            A.pruefe(ungleich == 0, "Prüfstein 9: zwei Runden des Fotos gegen zwei Bilder der Referenz (Saat 7, 7 000 001)", f("%d von %d Werten ungleich", ungleich, 2 * 3 * w * h), "bitgleich");
            // 10: zweimal dieselbe Saat
            Foto f2 = b.foto(bl, t, "Prüfung", w, h);
            f2.rundenMax = 3;
            f2.saat = 11;
            f2.rechne();
            Foto f3 = b.foto(bl, t, "Prüfung", w, h);
            f3.rundenMax = 3;
            f3.saat = 11;
            f3.rechne();
            double[][] m2 = f2.mittel(), m3 = f3.mittel();
            long u2 = 0;
            for (int c = 0; c < 3; c++) for (int i = 0; i < w * h; i++) if (Double.doubleToLongBits(m2[c][i]) != Double.doubleToLongBits(m3[c][i])) u2++;
            A.pruefe(u2 == 0, "Prüfstein 10: zwei Fotos mit derselben Saat (3 Runden)", f("%d von %d Werten ungleich", u2, 3 * w * h), "bitgleich");
            // 12: abbrechen nach 5 s
            Foto f4 = b.foto(bl, t, "Prüfung", 800, 410);
            f4.starte();
            Thread.sleep(5000);
            long a0 = System.nanoTime();
            f4.abbrechen();
            boolean zu = f4.warte(15_000);
            double s0 = (System.nanoTime() - a0) / 1e9;
            A.pruefe(zu && f4.ende() == Foto.Ende.ABGEBROCHEN, "Prüfstein 12: Abbrechen nach 5 s (800 × 410)", f("beendet %s nach %.1f s, Ende %s, %d Runden", zu ? "ja" : "nein", s0, f4.ende(), f4.runden()),
                    "Faden und Pool beendet ≤ 15 s [G]");
            // 12: Ablage
            List<Path> p = f2.lege(ablage);
            BufferedImage im = ImageIO.read(p.get(0).toFile());
            long pfm = Files.size(p.get(1));
            String txt = Files.readString(p.get(2));
            long kopf = ("PF\n" + w + " " + h + "\n-1.0\n").length();
            boolean voll = im != null && im.getWidth() == w && im.getHeight() == h && pfm == kopf + 12L * w * h && txt.contains("Szenenzeit") && txt.contains("Rauschen") && txt.contains("Blick");
            A.pruefe(voll, "Prüfstein 12: Ablage (PNG, PFM, Beschreibung)", f("%s; PNG %s, PFM %d Byte (erwartet %d), Beschreibung %d Zeichen", p.get(0).getFileName(),
                    im == null ? "unlesbar" : im.getWidth() + " × " + im.getHeight(), pfm, kopf + 12L * w * h, txt.length()), "vollständig");
        }
    }

    static void foto(Szenen sz, File gb, Szenario s, int breite, int hoehe, double sekunden, File ablage) {
        long n0 = System.nanoTime();
        try (Buehne b = Buehne.ohneFenster(gb, BW, BH)) {
            b.oeffne(s);
            double t = b.uhr().sekunden();
            Blick bl = blick(sz, s, b, t);
            Ansicht a = s.ansicht != null ? sz.ansicht(s.ansicht) : sz.ansichten().get(0);
            long n1 = System.nanoTime();
            Foto fo = b.foto(bl, t, s.name + " · " + a.name, breite, hoehe);
            double bau = (System.nanoTime() - n1) / 1e9;
            fo.zeitgrenze = sekunden;
            fo.rechne();
            List<Path> p = fo.lege(ablage);
            A.info(s.name + " · " + a.name, f("%s; %d Pfade je Bildpunkt, %.0f Pfade/s, Rauschen in Blöcken %.2f, je Bildpunkt %.2f; Ende %s; Bau der Referenz %.1f s, alles %.0f s; %s",
                    BildCheck.zeit(b.uhr().start, t), fo.runden(), fo.pfade() / Math.max(1e-9, fo.sekunden()), fo.rauschenBlock(), fo.rauschenPunkt(), fo.ende(), bau,
                    (System.nanoTime() - n0) / 1e9, p.get(0).getFileName()));
        } catch (Throwable e) {
            A.pruefe(false, "Prüfstein 11: " + s.name, String.valueOf(e), "ohne Fehler");
        }
    }
}
