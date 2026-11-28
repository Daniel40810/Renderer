package com.dan.lauf;

import com.dan.core.pruef.Bildvergleich;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Bodennetz;
import com.dan.core.szene.Spiegelbild;
import com.dan.render.air.Himmel;
import com.dan.render.air.Kimmung;
import com.dan.render.frame.Belichtung;
import com.dan.render.frame.Filmkurve;
import com.dan.render.frame.Rahmen;
import com.dan.render.reference.Gelaendeszene;
import com.dan.render.reference.Luftstrahl;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.Schranke;
import com.dan.render.reference.Seegangsflaeche;
import com.dan.render.terrain.Gelaendebild;
import com.dan.render.water.Wasserbild;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Das Foto (P11b, Werkbuch HalligHooge Teil A, 12.12; F109 A, F112 B, F113 A): die Ansicht in Referenzqualität, gerechnet
 * mit der Referenz Stufe 1 ({@link Referenz} über einer {@link Gelaendeszene} derselben Stände wie das Echtzeitbild).
 * <ul>
 * <li><b>Fortschreitend:</b> in Runden zu einem Pfad je Bildpunkt; nach jeder Runde ein Zwischenbild ({@link #bild}).
 * Gerade Runden zählen zur ersten, ungerade zur zweiten Hälfte; aus beiden das Rauschen (ΔE₀₀ der Hälften).</li>
 * <li><b>Ende:</b> bei Rauschen ≤ {@link #RAUSCHEN} je Bildpunkt (im Mittel; in Blöcken zu {@link #BLOCK} × {@link #BLOCK}
 * Bildpunkten wird es nur angegeben – ein Foto soll in jedem Bildpunkt ruhig sein), nach der
 * Zeitgrenze ({@link #zeitgrenze}; eine Runde beginnt nur, wenn sie nach der Dauer der letzten noch hineinpasst; mindestens
 * zwei Runden), nach {@link #rundenMax} oder durch {@link #abbrechen}.</li>
 * <li><b>Fäden:</b> ein eigener Pool mit allen Kernen außer einem (der bleibt für das Bild und die Oberfläche).</li>
 * <li><b>Abbildung:</b> wie das Echtzeitbild – dessen Belichtung zur Zeit des Fotos, Stäbchen, Filmkurve, sRGB.</li>
 * <li><b>Ablage</b> ({@link #lege}): PNG (abgebildet), PFM (lineares sRGB in cd/m², float32) und eine Beschreibung.</li>
 * </ul>
 * Gleiche Saat, Größe und Rundenzahl geben dasselbe Bild, Bit für Bit (die Zeilen haben je eigenen Zufall).
 */
public final class Foto {
    public static final String VERSION = "0.1";
    /** Zeitgrenze (s) [G] (F109 A) und Grenze des Rauschens in Blöcken. */
    public static final double ZEITGRENZE = 180, RAUSCHEN = 1;
    public static final int BLOCK = 8;
    /** Wellenlängen je Pfad und Suchweite unter Wasser (m) wie {@code ReferenzCheck} [G]. */
    public static final int BAHNEN = 16;
    public static final double WASSER_WEITE = 5;

    /** Wie das Foto endete. */
    public enum Ende { LAEUFT, RAUSCHEN, ZEITGRENZE, RUNDEN, ABGEBROCHEN, FEHLER }

    public final Referenz ref;
    /** Der Blick in der Größe des Fotos. */
    public final Blick blick;
    public final int breite, hoehe;
    public final Instant zeit;
    public final String titel;
    /** Belichtung (Faktor auf cd/m²) und die übrige Abbildung des Echtzeitbilds zur Zeit des Fotos. */
    public final double faktor;
    private final boolean nacht, kurve;
    private final Filmkurve fk;

    public double zeitgrenze = ZEITGRENZE;
    public int rundenMax = Integer.MAX_VALUE;
    public int faeden = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    public long saat = 1;

    private final double[][][] summe;
    private final int[] zahl = new int[2];
    private volatile int runden;
    private volatile double sekunden, letzteRunde, rauschenBlock = Double.NaN, rauschenPunkt = Double.NaN;
    private volatile long pfade;
    private volatile Ende ende = Ende.LAEUFT;
    private volatile String fehler;
    private volatile BufferedImage zwischen;
    private volatile boolean abbrechen;
    private volatile ForkJoinPool pool;
    private volatile Thread faden;
    /** Nach {@link #lege}: die abgelegten Dateien. */
    public volatile List<Path> ablage = List.of();

    Foto(Referenz ref, Blick blick, int breite, int hoehe, Instant zeit, String titel, Rahmen rahmen) {
        this.ref = ref;
        this.breite = breite;
        this.hoehe = hoehe;
        this.blick = blick.kopie().groesse(breite, hoehe);
        this.zeit = zeit;
        this.titel = titel;
        faktor = rahmen.belichtung.faktor();
        nacht = rahmen.belichtung.nachtsehen;
        kurve = rahmen.kurve;
        fk = new Filmkurve(rahmen.film);
        summe = new double[2][3][breite * hoehe];
    }

    // ------------------------------------------------------------------ die Szene der Referenz

    /**
     * Die Referenz über denselben Ständen wie das zuletzt gezeichnete Echtzeitbild (P7c; seit P8c mit Seegang, seit P10b
     * mit gekrümmten Sehstrahlen, wenn das Bild die Kimmung zeigt, seit P10d mit Sediment). Nur rufen, solange sich die
     * Stände nicht ändern (im Faden des Bilds oder ohne Fenster).
     */
    public static Referenz referenz(Gelaendebild gel, Wasserbild wasser, Himmel himmel, Kimmung kimmung, Bild bild, Blick b, Instant zeit, Schranke sch) {
        return referenz(gel, wasser, himmel, kimmung, bild, b, zeit, sch, null, null);
    }

    /**
     * Seit P12c3 (F138 A, F141 A, F143 A): wie oben, dazu – wenn sie im Bild an sind – die Gestalt des Geländes (Wasser der
     * Gräben und Fethinge, Wege), die Häuser und Körper aus {@code hb} (dieselben Bereiche wie das Bild, Fensterlicht wie
     * zuletzt gezeichnet) und die Gehölze aus {@code bw} als Kronen (Laub des zuletzt gebauten Tags). Ohne alles ist die
     * Referenz dieselbe wie vorher.
     */
    public static Referenz referenz(Gelaendebild gel, Wasserbild wasser, Himmel himmel, Kimmung kimmung, Bild bild, Blick b, Instant zeit, Schranke sch,
                                    com.dan.render.bau.Hausbild hb, com.dan.render.bewuchs.Bewuchsbild bw) {
        Referenz ref = referenzGrund(gel, wasser, himmel, kimmung, bild, b, zeit, sch);
        if (hb == null && bw == null) return ref;
        Gelaendeszene gs = (Gelaendeszene) ref.szene;
        if (gel.gestaltAn && gel.gestalt() != null) gs.gestalt = new com.dan.render.reference.Gestaltfeld(gel.gestalt());
        com.dan.render.reference.Dreiecksbaum db = null;
        if (hb != null) {
            java.util.List<int[]> ber = hb.bereiche(b);
            if (!ber.isEmpty()) {
                db = new com.dan.render.reference.Dreiecksbaum(hb.bauten, ber, hb.zufallJeHaus(), b.ost, b.nord, b.hoehe, gs.k);
                db.fensterAnteil = hb.fensterAnteil;
                db.fensterL = hb.fensterL;
            }
        }
        com.dan.render.reference.Kronen kr = null;
        if (bw != null && bw.an) {
            if (zeit != null) bw.baue(bw.tagFuer(zeit));
            kr = new com.dan.render.reference.Kronen(bw.kronen(), bw.gehoelz.e0, bw.gehoelz.n0, b.ost, b.nord, b.hoehe, gs.k);
        }
        if (db == null && kr == null && gs.gestalt == null) return ref;
        Referenz neu = new Referenz(new com.dan.render.reference.Bauszene(gs, db, kr), himmel.luft(), wasser.optik, Math.hypot(bild.windU, bild.windV));
        neu.gestirne(zeit, b.ost, b.nord, 32);
        neu.spektral = ref.spektral;
        neu.wasserWeite = ref.wasserWeite;
        neu.bahnen = ref.bahnen;
        neu.luftstrahl = ref.luftstrahl;
        return neu;
    }

    private static Referenz referenzGrund(Gelaendebild gel, Wasserbild wasser, Himmel himmel, Kimmung kimmung, Bild bild, Blick b, Instant zeit, Schranke sch) {
        Spiegelbild sb = wasser.spiegelbild();
        // seit P10b: mit atmos in der Quelle die Nässe aus surf.wet (Halbwertszeit 0: die Sättigung selbst), sonst aus water.wetAge
        boolean atmos = bild.mitAtmos() && gel.nassAlsSaettigung;
        Gelaendeszene gs = new Gelaendeszene(sch, sb, gel.material.boden, atmos ? bild.atmosGitter() : bild.nassGitter(),
                (atmos ? bild.naesse() : bild.nassAlter()).clone(), gel.rest, gel.dunkel, atmos ? 0 : gel.halbwert, b.ost, b.nord, b.hoehe);
        if (wasser.seegang && !wasser.seeNetze().isEmpty()) gs.seegang = new Seegangsflaeche(wasser.seeNetze(), wasser.zeit);
        Bodennetz bn = bild.boden();
        if (bn != null) gs.sediment(bn, wasser.zeit, wasser.optik.schwebstoff, wasser.truebung, gel.bodenHoehe);
        Referenz ref = new Referenz(gs, himmel.luft(), wasser.optik, Math.hypot(bild.windU, bild.windV));
        ref.gestirne(zeit, b.ost, b.nord, 32);
        ref.spektral = true;
        ref.wasserWeite = WASSER_WEITE;
        ref.bahnen = BAHNEN;
        Kimmung.Tafel tf = kimmung.tafel();
        if (tf != null && bild.kimmHoehen != null)
            ref.luftstrahl = new Luftstrahl(gs, bild.kimmHoehen, bild.kimmBrechung, tf.auge, tf.sperr, kimmung.halb, 40_000, sch.hoechste() - bild.kimmFlaeche);
        return ref;
    }

    // ------------------------------------------------------------------ rechnen

    /** Rechnet in einem eigenen Faden („Foto“); gibt den Faden zurück. */
    public Thread starte() {
        Thread t = new Thread(this::rechne, "Foto");
        t.setDaemon(true);
        faden = t;
        t.start();
        return t;
    }

    /** Rechnet in diesem Faden bis zum Ende. */
    public void rechne() {
        long t0 = System.nanoTime();
        ForkJoinPool p = new ForkJoinPool(faeden);
        pool = p;
        try {
            int k = 0;
            while (true) {
                if (abbrechen) { ende = Ende.ABGEBROCHEN; break; }
                if (k >= rundenMax) { ende = Ende.RUNDEN; break; }
                if (k >= 2 && sekunden + letzteRunde > zeitgrenze) { ende = Ende.ZEITGRENZE; break; }
                ref.saat = saat * 1_000_000L + k;
                long r0 = System.nanoTime();
                double[][][] h = p.submit(() -> ref.bild(blick, breite, hoehe, 1)).get();
                if (abbrechen) { ende = Ende.ABGEBROCHEN; break; }
                int hf = k & 1;
                synchronized (summe) {
                    for (int c = 0; c < 3; c++) {
                        double[] s = summe[hf][c], v = h[0][c];
                        for (int i = 0; i < s.length; i++) s[i] += v[i];
                    }
                    zahl[hf]++;
                }
                k++;
                runden = k;
                pfade += ref.pfade.get();
                letzteRunde = (System.nanoTime() - r0) / 1e9;
                sekunden = (System.nanoTime() - t0) / 1e9;
                zwischen = bild();
                if (zahl[1] > 0) {
                    double[][][] hl = haelften();
                    rauschenBlock = deBlock(block(hl[0]), block(hl[1]))[0];
                    rauschenPunkt = Bildvergleich.vergleiche(abbild(hl[0]), abbild(hl[1]), 3).mittel();
                    if (rauschenPunkt <= RAUSCHEN) { ende = Ende.RAUSCHEN; break; }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ende = Ende.ABGEBROCHEN;
        } catch (ExecutionException | RuntimeException e) {
            fehler = String.valueOf(e.getCause() != null ? e.getCause() : e);
            ende = Ende.FEHLER;
        } finally {
            sekunden = (System.nanoTime() - t0) / 1e9;
            p.shutdownNow();
            try {
                p.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Bricht ab: die laufende Runde endet nach den Zeilen, die schon begonnen haben, und zählt nicht. */
    public void abbrechen() {
        abbrechen = true;
        ref.abbrechen = true;
    }

    /** Wartet auf das Ende des Fadens (höchstens ms); true, wenn er beendet ist und der Pool keine Fäden mehr hat. */
    public boolean warte(long ms) throws InterruptedException {
        Thread t = faden;
        if (t != null) t.join(ms);
        ForkJoinPool p = pool;
        return (t == null || !t.isAlive()) && (p == null || p.isTerminated());
    }

    // ------------------------------------------------------------------ Stand

    public int runden() { return runden; }

    public double sekunden() { return sekunden; }

    public long pfade() { return pfade; }

    /** Rauschen, ΔE₀₀ der Hälften im Mittel: in Blöcken und je Bildpunkt (NaN vor der zweiten Runde). */
    public double rauschenBlock() { return rauschenBlock; }

    public double rauschenPunkt() { return rauschenPunkt; }

    public Ende ende() { return ende; }

    public boolean fertig() { return ende != Ende.LAEUFT; }

    public String fehler() { return fehler; }

    /** Das letzte Zwischenbild (null vor der ersten Runde). */
    public BufferedImage zwischenbild() { return zwischen; }

    /** Eine Zeile zum Stand. */
    public String text() {
        String r = Double.isNaN(rauschenBlock) ? "Rauschen nach der zweiten Runde" : String.format(Locale.GERMAN, "Rauschen %.2f (Blöcke), %.1f (Bildpunkte)", rauschenBlock, rauschenPunkt);
        String e = switch (ende) {
            case LAEUFT -> "läuft";
            case RAUSCHEN -> "fertig (Rauschen je Bildpunkt ≤ 1)";
            case ZEITGRENZE -> "fertig (Zeitgrenze)";
            case RUNDEN -> "fertig";
            case ABGEBROCHEN -> "abgebrochen";
            case FEHLER -> "Fehler: " + fehler;
        };
        return String.format(Locale.GERMAN, "%d × %d, %d Pfade je Bildpunkt, %.0f s, %s – %s", breite, hoehe, runden, sekunden, r, e);
    }

    /** Mittel aller Runden je Kanal (lineares sRGB in cd/m²). */
    public double[][] mittel() {
        double[][] m = new double[3][breite * hoehe];
        synchronized (summe) {
            int n = zahl[0] + zahl[1];
            if (n == 0) return m;
            for (int c = 0; c < 3; c++)
                for (int i = 0; i < m[c].length; i++) m[c][i] = (summe[0][c][i] + summe[1][c][i]) / n;
        }
        return m;
    }

    /** Die beiden Hälften (gerade und ungerade Runden), je gemittelt. */
    public double[][][] haelften() {
        double[][][] h = new double[2][3][breite * hoehe];
        synchronized (summe) {
            for (int k = 0; k < 2; k++)
                for (int c = 0; c < 3; c++)
                    for (int i = 0; i < h[k][c].length; i++) h[k][c][i] = summe[k][c][i] / Math.max(1, zahl[k]);
        }
        return h;
    }

    // ------------------------------------------------------------------ Abbildung wie das Echtzeitbild

    /** Lineares sRGB in cd/m² → 0xRRGGBB: Belichtung, Stäbchen, Filmkurve, sRGB (wie {@code ReferenzCheck.Abbild}). */
    public int rgb(double r, double g, double b) {
        double[] c = {r * faktor, g * faktor, b * faktor};
        if (nacht) {
            double y = Math.max(0, 0.2126 * r + 0.7152 * g + 0.0722 * b);
            c = Belichtung.farbtreu(Belichtung.staebchen(c[0], c[1], c[2], y), faktor);       // seit L5e1
        }
        double[] o = kurve ? fk.anwenden(c[0], c[1], c[2]) : c;
        int v = 0;
        for (int k = 0; k < 3; k++) {
            double l = Math.min(1, Math.max(0, o[k])), e = l <= 0.0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1 / 2.4) - 0.055;
            v = v << 8 | (int) Math.round(e * 255);
        }
        return v;
    }

    BufferedImage abbild(double[][] l) {
        BufferedImage im = new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < breite * hoehe; i++) {
            double r = l[0][i], g = l[1][i], b = l[2][i];
            im.setRGB(i % breite, i / breite, Double.isFinite(r + g + b) ? rgb(r, g, b) : 0xFF00FF);
        }
        return im;
    }

    /** Das Foto, wie es jetzt steht, abgebildet. */
    public BufferedImage bild() { return abbild(mittel()); }

    double[][] block(double[][] l) {
        int bw = breite / BLOCK, bh = hoehe / BLOCK;
        double[][] o = new double[3][bw * bh];
        for (int c = 0; c < 3; c++)
            for (int y = 0; y < bh * BLOCK; y++)
                for (int x = 0; x < bw * BLOCK; x++) o[c][(y / BLOCK) * bw + x / BLOCK] += l[c][y * breite + x] / (BLOCK * BLOCK);
        return o;
    }

    double[] deBlock(double[][] a, double[][] b) {
        int n = a[0].length;
        double s = 0, mx = 0;
        double[] la = new double[3], lb = new double[3];
        for (int i = 0; i < n; i++) {
            Bildvergleich.lab(rgb(a[0][i], a[1][i], a[2][i]), la);
            Bildvergleich.lab(rgb(b[0][i], b[1][i], b[2][i]), lb);
            double d = Bildvergleich.deltaE00(la[0], la[1], la[2], lb[0], lb[1], lb[2]);
            s += d;
            mx = Math.max(mx, d);
        }
        return new double[] {s / Math.max(1, n), mx};
    }

    // ------------------------------------------------------------------ Ablage

    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter JETZT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(java.time.ZoneId.systemDefault());

    /**
     * Legt das Foto in {@code ordner} ab: {@code Foto_<Szenenzeit UTC>_<Titel>_<jetzt>} als .png, .pfm (float32, lineares
     * sRGB in cd/m², Zeilen von unten wie im Format) und .txt (Beschreibung). Gibt die Dateien zurück.
     */
    public List<Path> lege(File ordner) throws IOException {
        Files.createDirectories(ordner.toPath());
        String kurz = titel.replaceAll("[^A-Za-z0-9äöüÄÖÜß]+", "_").replaceAll("^_+|_+$", "");
        if (kurz.length() > 60) kurz = kurz.substring(0, 60);
        String stamm = "Foto_" + NAME.format(zeit) + "_" + kurz + "_" + JETZT.format(Instant.now());
        Path png = ordner.toPath().resolve(stamm + ".png"), pfm = ordner.toPath().resolve(stamm + ".pfm"), txt = ordner.toPath().resolve(stamm + ".txt");
        double[][] m = mittel();
        ImageIO.write(abbild(m), "png", png.toFile());
        byte[] kopf = ("PF\n" + breite + " " + hoehe + "\n-1.0\n").getBytes(StandardCharsets.US_ASCII);
        ByteBuffer bb = ByteBuffer.allocate(kopf.length + 12 * breite * hoehe).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(kopf);
        for (int y = hoehe - 1; y >= 0; y--)
            for (int x = 0; x < breite; x++)
                for (int c = 0; c < 3; c++) bb.putFloat((float) m[c][y * breite + x]);
        Files.write(pfm, bb.array());
        Files.writeString(txt, beschreibung(), StandardCharsets.UTF_8);
        ablage = List.of(png, pfm, txt);
        return ablage;
    }

    /** Die Beschreibung zur Ablage. */
    public String beschreibung() {
        List<String> z = new ArrayList<>();
        z.add("Foto – HalligHooge (Werkbuch HalligHooge Teil A, P11b; com.dan.lauf.Foto " + VERSION + ", com.dan.render.reference " + Referenz.VERSION + ")");
        z.add("Titel: " + titel);
        z.add("Szenenzeit: " + zeit + " (UTC)");
        z.add(String.format(Locale.ROOT, "Blick: Ost %.1f m, Nord %.1f m (UTM 32), Höhe %.2f m NHN, Richtung %.2f°, Neigung %.2f°, Bildwinkel %.2f° (senkrecht)",
                blick.ost, blick.nord, blick.hoehe, blick.richtung, blick.neigung, blick.bildwinkel));
        z.add("Größe: " + breite + " × " + hoehe + " Bildpunkte (halbes Fenster, F113 A)");
        z.add("Rechnung: Referenz Stufe 1 (Java, double), spektral mit " + ref.bahnen + " Wellenlängen je Pfad, " + (ref.luftstrahl != null ? "gekrümmte Sehstrahlen (Kimmung), " : "")
                + "Saat " + saat + ", " + faeden + " Fäden");
        z.add(String.format(Locale.ROOT, "Pfade: %d je Bildpunkt in %d Runden, zusammen %d; %.1f s, %.0f Pfade/s", runden, runden, pfade, sekunden, pfade / Math.max(1e-9, sekunden)));
        z.add(String.format(Locale.ROOT, "Rauschen (ΔE00 der Hälften, gerade und ungerade Runden): in Blöcken zu %d × %d im Mittel %.2f, je Bildpunkt im Mittel %.2f", BLOCK, BLOCK, rauschenBlock, rauschenPunkt));
        z.add(String.format(Locale.ROOT, "Abbildung: Belichtung des Echtzeitbilds EV %.2f (Faktor %.5g), Stäbchen %s, Filmkurve %s, sRGB", Math.log(faktor) / Math.log(2), faktor, nacht ? "ja" : "nein", kurve ? "ja" : "nein"));
        z.add("Ende: " + ende + (fehler != null ? " (" + fehler + ")" : "") + String.format(Locale.ROOT, "; Zeitgrenze %.0f s", zeitgrenze));
        z.add("Dateien: .png abgebildet wie das Echtzeitbild; .pfm lineares sRGB in cd/m² (float32, Portable Float Map, Zeilen von unten)");
        return String.join("\n", z) + "\n";
    }
}
