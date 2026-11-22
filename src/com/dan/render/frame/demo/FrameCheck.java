package com.dan.render.frame.demo;

import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Zeichner;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.render.frame.Belichtung;
import com.dan.render.frame.Filmkurve;
import com.dan.render.frame.Rahmen;
import com.dan.render.frame.film.Film;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung von render.frame ohne Fenster (P6a, Werkbuch HalligHooge Teil A, 12.7). Teile: 1 Blick (Lage relativ zum
 * Auge, Projektion), 2 Filmkurve (Tabelle gegen {@code Film.develop}), 3 Ausgabe auf der Karte (linear und sRGB),
 * 4 Histogramm und Belichtung, 5 Messen auf Gelände und Wasser (F49 A), 6 Sehen bei Nacht (F50 A). Endet mit „Alles in Ordnung.“ und schreibt das Attest nach
 * {@code docs/status/P6_Frame.md}. Die Grenzen stehen vor der Messung fest; [G] heißt gesetzt.
 */
public final class FrameCheck {
    private static final Attest A = new Attest("com.dan.render.frame", Rahmen.VERSION);

    /** Ein Bildteil, der ein hochgeladenes Bild in das HDR-Bild legt. */
    private static final class Testbild implements Bildteil {
        final Zeichner z, zt;
        final Abtaster at;
        Bild bild;
        /** Tiefe je Bildpunkt (R32F, 0 = Himmel) oder null: das Bildziel behält die gelöschte Tiefe 0. */
        Bild tiefe;

        Testbild(Geraet g) {
            Zeichner.Art art = new Zeichner.Art().ohneTiefe();
            art.farben = new int[] {Bild.RGBA16F};
            z = Zeichner.lade(g, "/com/dan/render/frame/voll.vert", "/com/dan/render/frame/demo/testbild.frag", 0, null, art, Kernel.Bindung.TEXTUR);
            Zeichner.Art mit = new Zeichner.Art();
            mit.farben = new int[] {Bild.RGBA16F};
            mit.vergleich = org.lwjgl.vulkan.VK10.VK_COMPARE_OP_ALWAYS;
            zt = Zeichner.lade(g, "/com/dan/render/frame/voll.vert", "/com/dan/render/frame/demo/testbild_tiefe.frag", 0, null, mit, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR);
            at = new Abtaster(g, false, false);
        }

        @Override public String name() { return "Testbild"; }

        @Override public void zeichnen(Ablauf a, Bildziel ziel) {
            if (tiefe == null) {
                long satz = z.satz(at.an(bild));
                a.zeichne("Testbild", new Bild[] {ziel.hdr}, null, null, null, Ablauf.textur(Ablauf.ZEICHNEN, bild), c -> z.zeichne(c, satz, null, 3, 1));
            } else {
                long satz = zt.satz(at.an(bild), at.an(tiefe));
                a.zeichne("Testbild mit Tiefe", new Bild[] {ziel.hdr}, null, ziel.tiefe, null, Ablauf.textur(Ablauf.ZEICHNEN, bild, tiefe), c -> zt.zeichne(c, satz, null, 3, 1));
            }
        }

        @Override public void close() {
            z.close();
            zt.close();
            at.close();
        }
    }

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        System.out.println("FrameCheck – com.dan.render.frame " + Rahmen.VERSION + " (Werkbuch HalligHooge Teil A, P6a)");
        blick();
        filmkurve();
        A.teil("3 Ausgabe auf der Karte");
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("FrameCheck", 256L << 20);
            karte(g, konto);
            boden(g, konto);
            nacht(g, konto);
            A.teil("7 Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                String erste = g.meldungen.isEmpty() ? "" : "; die erste Meldung: " + g.meldungen.get(0).replace('\n', ' ');
                A.pruefe(g.validierungsFehler == 0, "Validierungsschicht " + g.schicht + (g.synchronPruefung ? " mit Prüfung der Synchronisation" : " ohne Prüfung der Synchronisation")
                        + " über alle Teile", g.validierungsFehler + " Fehler" + erste, "0 Fehler");
            } else A.info("Validierung", "nicht eingeschaltet (-Dcore.gpu.validierung=true)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P6_Frame.md"), "P6a – FrameCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1 Blick

    private static void blick() {
        A.teil("1 Blick");
        Random r = new Random(1);
        Blick b = new Blick(470_900.123, 6_046_615.456, 4.2, 255, -3, 50).groesse(1920, 1080);
        // Lage relativ zum Auge: in double gerechnet, als float auf der Karte
        double fehler = 0;
        for (int i = 0; i < 1000; i++) {
            double a = r.nextDouble() * 2 * Math.PI, d = 30_000 * r.nextDouble();
            double o = b.ost + d * Math.sin(a), n = b.nord + d * Math.cos(a), h = -5 + 10 * r.nextDouble();
            double[] rel = b.relativ(o, n, h);
            fehler = Math.max(fehler, Math.max(Math.abs((float) rel[0] - (o - b.ost)), Math.abs((float) rel[2] - (b.nord - n))));
        }
        A.pruefe(fehler <= 1e-3, "Lage relativ zum Auge in float, Punkte bis 30 km (Auge bei N 6 046 615 m)", f("größter Fehler %.2e m", fehler), "≤ 1 mm [G]");
        // Projektion der Karte (Matrix in float) gegen den Bildpunkt in double
        float[] m = b.ansichtProjektion();
        double dp = 0, dt = 0, dw = 0;
        int n = 0;
        for (int i = 0; i < 20000; i++) {
            double[] rel = {(r.nextDouble() - 0.5) * 4000, (r.nextDouble() - 0.5) * 200, (r.nextDouble() - 0.5) * 4000};
            double[] bp = b.bildpunkt(rel);
            if (bp == null || bp[0] < 0 || bp[0] > b.breite || bp[1] < 0 || bp[1] > b.hoehe_px) continue;          // nur was im Bild liegt
            double[] c = new double[4];
            for (int k = 0; k < 4; k++) c[k] = m[k] * rel[0] + m[4 + k] * rel[1] + m[8 + k] * rel[2] + m[12 + k];
            if (c[3] <= 0) continue;
            double x = (c[0] / c[3] + 1) / 2 * b.breite, y = (c[1] / c[3] + 1) / 2 * b.hoehe_px, z = c[2] / c[3];
            dp = Math.max(dp, Math.hypot(x - bp[0], y - bp[1]));
            dt = Math.max(dt, Math.abs(z - bp[2]) / bp[2]);
            double[] s = b.strahl(bp[0], bp[1]);
            double l = Math.sqrt(rel[0] * rel[0] + rel[1] * rel[1] + rel[2] * rel[2]);
            double kx = s[1] * rel[2] - s[2] * rel[1], ky = s[2] * rel[0] - s[0] * rel[2], kz = s[0] * rel[1] - s[1] * rel[0];
            dw = Math.max(dw, Math.atan2(Math.sqrt(kx * kx + ky * ky + kz * kz), s[0] * rel[0] + s[1] * rel[1] + s[2] * rel[2]));
            n++;
        }
        A.pruefe(dp <= 1e-2, "Projektion in float gegen Bildpunkt in double (" + n + " Punkte im Bild)", f("größter Abstand %.2e Bildpunkte", dp), "≤ 0,01 Bildpunkte [G]");
        A.pruefe(dt <= 1e-5, "Tiefe umgekehrt: nah / Abstand längs der Achse", f("größter relativer Fehler %.2e", dt), "≤ 1e-5 [G]");
        A.pruefe(dw <= 1e-9, "Strahl durch den Bildpunkt trifft den Punkt", f("größter Winkel %.2e rad", dw), "≤ 1e-9 [G]");
        double[] mitte = b.bildpunkt(b.achse());
        A.pruefe(Math.abs(mitte[0] - 960) < 1e-9 && Math.abs(mitte[1] - 540) < 1e-9, "Die Blickachse liegt in der Bildmitte", f("(%.6f, %.6f)", mitte[0], mitte[1]), "(960, 540)");
        double[] oben = b.bildpunkt(new double[] {b.achse()[0], b.achse()[1] + 0.1, b.achse()[2]});
        A.pruefe(oben[1] < 540, "Oben in der Szene ist oben im Bild (y nach unten)", f("y = %.1f", oben[1]), "< 540");
    }

    // ------------------------------------------------------------------ 2 Filmkurve

    private static void filmkurve() {
        A.teil("2 Filmkurve");
        Film film = new Film();
        film.grain = film.halation = film.weave = film.flicker = false;
        Filmkurve k = new Filmkurve(film);
        Random r = new Random(2);
        double d = 0;
        for (int i = 0; i < 20000; i++) {
            double s = Math.pow(2, -12 + 20 * r.nextDouble()) * Film.GRAY;
            double[] c = {s * (0.2 + r.nextDouble()), s * (0.2 + r.nextDouble()), s * (0.2 + r.nextDouble())};
            double[] a = k.anwenden(c[0], c[1], c[2]), e = film.develop(c[0], c[1], c[2]);
            for (int j = 0; j < 3; j++) d = Math.max(d, Math.abs(a[j] - e[j]));
        }
        A.info("Kurve", f("%s auf %s, Tabelle %d Werte je Schicht über log10 H von %.0f bis %.0f", film.negative, film.print, Filmkurve.N, Filmkurve.X0, Filmkurve.X1));
        A.pruefe(d <= 1e-4, "Tabelle mit Matrix und Projektion gegen Film.develop (20 000 Farben, −12 bis +8 Blenden um die Graukarte)", f("größte Abweichung %.2e", d), "≤ 1e-4 [G]");
        double[] grau = film.develop(Film.GRAY, Film.GRAY, Film.GRAY);
        A.info("Graukarte", f("Szene 0,18 → Bildschirm linear %.4f, %.4f, %.4f", grau[0], grau[1], grau[2]));
    }

    // ------------------------------------------------------------------ 3, 4 Karte

    private static float halb(double v) { return Float.float16ToFloat(Float.floatToFloat16((float) v)); }

    private static double srgb(double l) { return l <= 0.0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1 / 2.4) - 0.055; }

    private static void karte(Geraet g, Konto konto) {
        int W = 160, H = 96;
        Random r = new Random(3);
        float[] px = new float[W * H * 4];
        for (int i = 0; i < W * H; i++) {
            double s = Math.pow(2, -10 + 16 * r.nextDouble()) * Film.GRAY;
            for (int c = 0; c < 3; c++) px[4 * i + c] = halb(s * (0.2 + r.nextDouble()));
            px[4 * i + 3] = 1;
        }
        Blick b = new Blick(470_900, 6_046_615, 4, 270, 0, 50);
        try (Rahmen rahmen = new Rahmen(g, konto, W, H); Testbild t = new Testbild(g);
             Bild hdrBild = new Bild(g, konto, "Testbild", W, H, Bild.RGBA16F); Bild linear = new Bild(g, konto, "Ausgabe linear", W, H, Bild.RGBA32F);
             Bild srgbBild = new Bild(g, konto, "Ausgabe sRGB", W, H, Bild.RGBA8S)) {
            hdrBild.schreibe(px);
            t.bild = hdrBild;
            rahmen.belichtung.setze(0);
            rahmen.belichtung.nachtsehen = false;                      // Teile 3 und 4: die Kurve allein; Nacht prüft Teil 6
            Filmkurve k = new Filmkurve(rahmen.film);
            // ein Bild, linear: Faktor 1 (Belichtung 0, Vorbelichtung 1)
            Ablauf a = new Ablauf(g, g.grafik);
            rahmen.bild(a, List.of(t), b, Instant.parse("2026-10-25T12:00:00Z"), linear, 0);
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            A.info("Ablauf", a.durchgaenge() + " Durchgänge, " + a.sperren() + " Sperren für " + a.gesperrt() + " Betriebsmittel");
            float[] o = linear.lies();
            double dk = 0, df = 0;
            int nan = 0;
            for (int i = 0; i < W * H; i++) {
                double[] e = k.anwenden(px[4 * i], px[4 * i + 1], px[4 * i + 2]), d = rahmen.film.develop(px[4 * i], px[4 * i + 1], px[4 * i + 2]);
                for (int c = 0; c < 3; c++) {
                    if (!Float.isFinite(o[4 * i + c])) nan++;
                    dk = Math.max(dk, Math.abs(o[4 * i + c] - e[c]));
                    df = Math.max(df, Math.abs(o[4 * i + c] - d[c]));
                }
            }
            A.pruefe(dk <= 2e-4, "Ausgabe auf der Karte (RGBA32F) gegen dieselbe Rechnung in double (" + W * H + " Bildpunkte, −10 bis +6 Blenden)", f("größte Abweichung %.2e", dk), "≤ 2e-4 [G]");
            A.pruefe(df <= 5e-4, "Ausgabe auf der Karte gegen Film.develop", f("größte Abweichung %.2e", df), "≤ 5e-4 [G]");
            A.pruefe(nan == 0, "Kein NaN in der Ausgabe", nan + " Werte", "0");
            // ein Bild in sRGB (8 bit): die Karte verschlüsselt selbst
            Ablauf a2 = new Ablauf(g, g.grafik);
            rahmen.bild(a2, List.of(t), b, Instant.parse("2026-10-25T12:00:00Z"), srgbBild, 0);
            g.grafik.einmal(c -> a2.aufnehmen(c, null));
            float[] s = srgbBild.lies();
            int schritte = 0;
            for (int i = 0; i < W * H; i++)
                for (int c = 0; c < 3; c++) {
                    double soll = srgb(Math.min(1, Math.max(0, o[4 * i + c]))) * 255;
                    schritte = (int) Math.max(schritte, Math.abs(Math.round(s[4 * i + c] * 255) - soll));
                }
            A.pruefe(schritte <= 1, "Ausgabe in sRGB (RGBA8, von der Karte verschlüsselt) gegen die lineare Ausgabe", schritte + " Stufen", "≤ 1 Stufe");

            A.teil("4 Histogramm und Belichtung");
            // drittes Bild: es liest das Histogramm des ersten
            Ablauf a3 = new Ablauf(g, g.grafik);
            rahmen.belichtung.sofort();
            rahmen.bild(a3, List.of(t), b, Instant.parse("2026-10-25T12:00:00Z"), linear, 0);
            g.grafik.einmal(c -> a3.aufnehmen(c, null));
            long[] karte = rahmen.letztesHistogramm();
            long[] java = new long[Belichtung.FAECHER];
            float[] hdr = rahmen.ziel().hdr.lies();                       // drittes Bild: dasselbe Testbild, Vorbelichtung jetzt anders
            int st = Rahmen.SCHRITT;
            for (int y = st / 2; y < H; y += st)
                for (int x = st / 2; x < W; x += st) {
                    int i = y * W + x;
                    float L = 0.2126f * px[4 * i] + 0.7152f * px[4 * i + 1] + 0.0722f * px[4 * i + 2];
                    float tt = (float) Math.min(0.99999, Math.max(0, (Math.log(L) / Math.log(2) - Belichtung.LOG_MIN) / (Belichtung.LOG_MAX - Belichtung.LOG_MIN)));
                    float mx = (x + 0.5f) / W * 2 - 1, my = (y + 0.5f) / H * 2 - 1;
                    int w = 1 + (int) (3 * (1 - Math.min(1, Math.max(0, mx * mx + my * my))));
                    java[(int) (tt * 64)] += w;
                }
            long summeK = 0, summeJ = 0, verschoben = 0;
            for (int i = 0; i < java.length; i++) {
                summeK += karte[i];
                summeJ += java[i];
                verschoben += Math.abs(karte[i] - java[i]);
            }
            A.pruefe(summeK == summeJ, "Histogramm auf der Karte: Summe der Gewichte wie in Java (Schritt 4, zur Mitte schwerer)", summeK + " gegen " + summeJ, "gleich");
            A.pruefe(verschoben <= summeJ / 1000, "Histogramm auf der Karte: Fächer wie in Java (Rundung von log2 an den Grenzen der Fächer)", f("%d von %d Gewichten in einem anderen Fach", verschoben / 2, summeJ),
                    "≤ 0,1 % [G]");
            double mJ = new Belichtung().mittelAus(java), ev = rahmen.belichtung.ev();
            long[] pu = rahmen.letzterPuffer();
            A.pruefe(pu[2 * Belichtung.FAECHER] == 0 && !rahmen.belichtung.amBoden(), "Ohne Tiefe im Bild (Testbild ohne Gelände): kein Bildpunkt mit Tiefe, es gilt das ganze Bild (F49 A)",
                    f("%d mit Tiefe von %d gezählten", pu[2 * Belichtung.FAECHER], pu[2 * Belichtung.FAECHER + 1]), "0; ganzes Bild");
            A.pruefe(Math.abs(ev - Math.log(rahmen.belichtung.bezug / mJ) / Math.log(2)) < 1e-9, "Belichtung nach der ersten Messung (sofort): log2(Bezug / Mittel der Szene)",
                    f("%+.3f Blenden, Mittel %.4g", ev, rahmen.belichtung.mittel()), "genau");
            double vorb = rahmen.ziel().vorbelichtung;
            double maxH = 0;
            for (int i = 0; i < W * H; i++) maxH = Math.max(maxH, Math.abs(hdr[4 * i] - px[4 * i]));
            A.info("Vorbelichtung im dritten Bild", f("%.4g (das HDR-Bild trägt L · Vorbelichtung; hier legt das Testbild L ohne sie hinein)", vorb));
        }
        // Zeit der Augenanpassung (nur Java)
        Belichtung bl = new Belichtung();
        bl.setze(0);
        long[] f = new long[Belichtung.FAECHER];
        f[20] = 1000;                                                    // ein Fach, dunkel
        bl.messung(f, 1, 0);
        double ziel = bl.ziel(), nach = 0;
        for (int i = 0; i < 30; i++) bl.messung(null, 1, 0.1);
        nach = bl.ev();
        double soll = ziel * (1 - Math.exp(-3.0 / bl.tauDunkel));
        A.pruefe(Math.abs(nach - soll) < 1e-9, "Augenanpassung ins Dunkle: nach 3 s um 1 − e^(−3/τ) des Wegs (τ = 3 s)", f("%+.6f gegen %+.6f Blenden", nach, soll), "genau");
    }

    // ------------------------------------------------------------------ 5 Messen auf Gelände und Wasser (F49 A)

    /** Histogramm in Java wie histogramm.comp: Fächer über die Bildpunkte mit Tiefe, über alle, Zahl mit Tiefe, Zahl. */
    static long[] histogrammJava(float[] px, float[] tf, int W, int H, double vorb) {
        long[] h = new long[Belichtung.PUFFER];
        int st = Rahmen.SCHRITT;
        for (int y = st / 2; y < H; y += st)
            for (int x = st / 2; x < W; x += st) {
                int i = y * W + x;
                float r = halb(px[4 * i] * vorb), gg = halb(px[4 * i + 1] * vorb), b = halb(px[4 * i + 2] * vorb);
                float L = 0.2126f * r + 0.7152f * gg + 0.0722f * b;
                if (!(L > 0)) continue;
                float tt = (float) Math.min(0.99999, Math.max(0, (Math.log(L) / Math.log(2) - Belichtung.LOG_MIN) / (Belichtung.LOG_MAX - Belichtung.LOG_MIN)));
                float mx = (x + 0.5f) / W * 2 - 1, my = (y + 0.5f) / H * 2 - 1;
                int w = 1 + (int) (3 * (1 - Math.min(1, Math.max(0, mx * mx + my * my))));
                int fach = (int) (tt * 64);
                h[Belichtung.FAECHER + fach] += w;
                h[2 * Belichtung.FAECHER + 1]++;
                if (tf[i] > 0) {
                    h[fach] += w;
                    h[2 * Belichtung.FAECHER]++;
                }
            }
        return h;
    }

    /** Ein Bild ins Bildziel, ohne Ausgabe zu prüfen; das dritte liest das Histogramm des ersten. */
    static void dreiBilder(Geraet g, Rahmen rahmen, Testbild t, Bild aus) {
        Blick b = new Blick(470_900, 6_046_615, 4, 270, 0, 50);
        rahmen.belichtung.setze(0);
        for (int k = 0; k < 3; k++) {
            if (k == 2) rahmen.belichtung.sofort();
            Ablauf a = new Ablauf(g, g.grafik);
            rahmen.bild(a, List.of(t), b, Instant.parse("2026-10-25T12:00:00Z"), aus, 0);
            g.grafik.einmal(c -> a.aufnehmen(c, null));
        }
    }

    private static void boden(Geraet g, Konto konto) {
        A.teil("5 Messen auf Gelände und Wasser (F49 A)");
        int W = 160, H = 96;
        Random r = new Random(5);
        float[] px = new float[W * H * 4];
        try (Rahmen rahmen = new Rahmen(g, konto, W, H); Testbild t = new Testbild(g);
             Bild hdrBild = new Bild(g, konto, "Testbild", W, H, Bild.RGBA16F); Bild tiefe = new Bild(g, konto, "Testbild, Tiefe", W, H, Bild.R32F);
             Bild aus = new Bild(g, konto, "Ausgabe", W, H, Bild.RGBA32F)) {
            t.bild = hdrBild;
            t.tiefe = tiefe;
            rahmen.belichtung.nachtsehen = false;
            // Gegenlicht nachgestellt: oben Himmel (Tiefe 0) 4–6 Blenden über der Graukarte, unten Gelände (Tiefe > 0)
            // 6–8 Blenden darunter. Fall 1: Gelände ab Zeile 48 (die Hälfte); Fall 2: nur die untersten 6 Zeilen (im Raster 8 %).
            for (int fall = 1; fall <= 2; fall++) {
                int grenze = fall == 1 ? H / 2 : H - 6;
                float[] tf = new float[W * H];
                for (int y = 0; y < H; y++)
                    for (int x = 0; x < W; x++) {
                        int i = y * W + x;
                        boolean gel = y >= grenze;
                        double s = Math.pow(2, gel ? -8 + 2 * r.nextDouble() : 4 + 2 * r.nextDouble()) * Film.GRAY;
                        for (int c = 0; c < 3; c++) px[4 * i + c] = halb(s * (0.6 + 0.4 * r.nextDouble()));
                        px[4 * i + 3] = 1;
                        tf[i] = gel ? 0.25f + 0.5f * r.nextFloat() : 0f;
                    }
                hdrBild.schreibe(px);
                tiefe.schreibe(tf);
                dreiBilder(g, rahmen, t, aus);
                long[] karte = rahmen.letzterPuffer(), java = histogrammJava(px, tf, W, H, 1);
                long dBoden = 0, dAlle = 0, sB = 0, sA = 0;
                for (int i = 0; i < Belichtung.FAECHER; i++) {
                    dBoden += Math.abs(karte[i] - java[i]);
                    dAlle += Math.abs(karte[Belichtung.FAECHER + i] - java[Belichtung.FAECHER + i]);
                    sB += java[i];
                    sA += java[Belichtung.FAECHER + i];
                }
                int nb = 2 * Belichtung.FAECHER, na = nb + 1;
                String name = fall == 1 ? "Fall 1, Gelände auf der unteren Hälfte" : "Fall 2, Gelände auf den untersten 6 Zeilen";
                A.pruefe(karte[nb] == java[nb] && karte[na] == java[na], "Histogramm auf der Karte, " + name + ": gezählte Bildpunkte mit Tiefe und alle wie in Java",
                        f("%d und %d gegen %d und %d", karte[nb], karte[na], java[nb], java[na]), "gleich");
                A.pruefe(dBoden <= sB / 1000 && dAlle <= sA / 1000, "Histogramm auf der Karte, " + name + ": Fächer mit Tiefe und über alle wie in Java",
                        f("%d von %d und %d von %d Gewichten in einem anderen Fach", dBoden / 2, sB, dAlle / 2, sA), "≤ 0,1 % [G]");
                Belichtung j = new Belichtung();
                j.nachtsehen = false;
                long[] gilt = j.waehle(java);
                double m = j.mittelAus(gilt), soll = Math.log(j.bezug / m) / Math.log(2);
                boolean amBoden = fall == 1;
                A.pruefe(rahmen.belichtung.amBoden() == amBoden && Math.abs(rahmen.belichtung.ev() - soll) < 1e-9,
                        "Belichtung, " + name + ": " + (amBoden ? "gemessen auf dem Gelände" : "unter 10 % [G] über das ganze Bild"),
                        f("%s, Anteil mit Tiefe %.1f %%, %+.3f Blenden gegen %+.3f", rahmen.belichtung.amBoden() ? "Gelände" : "ganzes Bild", 100 * rahmen.belichtung.anteil(),
                                rahmen.belichtung.ev(), soll), amBoden ? "Gelände; genau" : "ganzes Bild; genau");
                if (fall == 1) {
                    Belichtung alle = new Belichtung();
                    alle.nurBoden = false;
                    double mAlle = alle.mittelAus(alle.waehle(java));
                    A.info("Fall 1: Belichtung über das ganze Bild zum Vergleich", f("%+.2f Blenden; auf dem Gelände %+.2f, also %.1f Blenden heller",
                            Math.log(alle.bezug / mAlle) / Math.log(2), soll, soll - Math.log(alle.bezug / mAlle) / Math.log(2)));
                }
            }
        }
    }

    // ------------------------------------------------------------------ 6 Sehen bei Nacht (F50 A)

    static Film film() {
        Film f = new Film();
        f.grain = f.halation = f.weave = f.flicker = false;
        return f;
    }

    private static void nacht(Geraet g, Konto konto) {
        A.teil("6 Sehen bei Nacht (F50 A)");
        Belichtung bl = new Belichtung();
        Filmkurve fk = new Filmkurve(film());
        bl.bild = x -> fk.anwenden(x, x, x)[1];
        A.pruefe(bl.bezug(bl.bezugY) == bl.bezug, "Bezug nach Krawczyk bei der Leuchtdichte des Bezugs (1 000 cd/m²) gleich 0,12", f("%.6f", bl.bezug(bl.bezugY)), "0,12 genau [G]");
        double nacht = Math.log(bl.bezug(0.0053) / bl.bezug) / Math.log(2), tag = Math.log(bl.bezug(2780) / bl.bezug) / Math.log(2);
        A.info("Bezug nach Krawczyk (Gl. 11)", f("Mondnacht 0,0053 cd/m² (Landunter 23:45): %+.2f Blenden; Tag 2 780 cd/m² (Hochwasser 12:20): %+.2f Blenden; σ bei 0,005 cd/m²: %.3f",
                nacht, tag, Belichtung.sigma(0.005)));
        // Krawczyk bestimmt die Helligkeit im Bild: das Mittel der Mondnacht nach der Kurve gegen das des Tags
        double bn = bl.bild.applyAsDouble(bl.bezug(0.00528)), bt = bl.bild.applyAsDouble(bl.bezug), soll = Belichtung.imBild(0.00528) / Belichtung.imBild(bl.bezugY);
        A.pruefe(Math.abs(bn / bt / soll - 1) < 1e-6, "Mondnacht im Bild (nach der Filmkurve) zum Tag wie bei Krawczyk: α/(1+α) bei 0,0053 gegen 1 000 cd/m²",
                f("%.5f gegen %.5f; Mittel im Bild %.4f (sRGB %.0f von 255), Tag %.4f (sRGB %.0f)", bn / bt, soll, bn, 255 * srgb(bn), bt, 255 * srgb(bt)), "relativ ≤ 1e-6");
        boolean mono = true;
        for (double y = 1e-5; y < 1e5; y *= 1.5) mono &= bl.bezug(y * 1.5) > bl.bezug(y);
        A.pruefe(mono && Math.abs(Belichtung.sigma(Belichtung.STAEBCHEN_K) - 0.5) < 1e-15, "Bezug steigt mit der Leuchtdichte; σ(K) = 1/2", mono ? "ja; 0,5" : "nein", "ja; 0,5");
        int W = 160, H = 96;
        Random r = new Random(7);
        float[] px = new float[W * H * 4];
        for (int i = 0; i < W * H; i++) {
            double y = Math.pow(10, -4 + 6 * r.nextDouble());                 // 10⁻⁴ … 10² cd/m²: Stäbchen ganz bis Zapfen
            for (int c = 0; c < 3; c++) px[4 * i + c] = halb(y * (0.2 + 1.6 * r.nextDouble()));
            px[4 * i + 3] = 1;
        }
        Blick b = new Blick(470_900, 6_046_615, 4, 270, 0, 50);
        try (Rahmen rahmen = new Rahmen(g, konto, W, H); Testbild t = new Testbild(g);
             Bild hdrBild = new Bild(g, konto, "Testbild", W, H, Bild.RGBA16F); Bild linear = new Bild(g, konto, "Ausgabe linear", W, H, Bild.RGBA32F)) {
            hdrBild.schreibe(px);
            t.bild = hdrBild;
            rahmen.belichtung.setze(4);                                         // Faktor 16 auf die Ausgabe, Vorbelichtung 16
            Filmkurve k = new Filmkurve(rahmen.film);
            Ablauf a = new Ablauf(g, g.grafik);
            rahmen.bild(a, List.of(t), b, Instant.parse("2026-10-25T23:45:00Z"), linear, 0);
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            double vorb = rahmen.ziel().vorbelichtung, fak = rahmen.belichtung.faktor() / vorb;
            float[] o = linear.lies();
            double dk = 0;
            for (int i = 0; i < W * H; i++) {
                double y = (0.2126 * px[4 * i] + 0.7152 * px[4 * i + 1] + 0.0722 * px[4 * i + 2]) / vorb;
                double[] s = Belichtung.staebchen(px[4 * i] * fak, px[4 * i + 1] * fak, px[4 * i + 2] * fak, y), e = k.anwenden(s[0], s[1], s[2]);
                for (int c = 0; c < 3; c++) dk = Math.max(dk, Math.abs(o[4 * i + c] - e[c]));
            }
            A.pruefe(dk <= 2e-4, "Ausgabe mit Stäbchen auf der Karte gegen Belichtung.staebchen und die Kurve in double (" + W * H + " Bildpunkte, 10⁻⁴ bis 10² cd/m²)",
                    f("größte Abweichung %.2e", dk), "≤ 2e-4 [G]");
            A.info("Anteil der Stäbchen σ(Y)", f("10⁻⁴ cd/m²: %.3f; 10⁻² : %.3f; 1: %.3f; 100: %.5f", Belichtung.sigma(1e-4), Belichtung.sigma(1e-2), Belichtung.sigma(1), Belichtung.sigma(100)));
        }
    }
}
