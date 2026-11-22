package com.dan.render.overlay.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.render.overlay.Einblendung;
import com.dan.render.overlay.Kachel;
import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung von render.overlay ohne Gebiet (Werkbuch HalligHooge 113, W2): ein Ziel W × H mit einer Tiefe, die eine Wand
 * in fester Entfernung vorgibt; darüber Kacheln mit bekannter Farbe. Geprüft wird, was eine Einblendung zusichert:
 * <ol>
 * <li>Lage am Rand genau auf den Bildpunkt, Farbe ohne Verlust (linear vormultipliziert, ins sRGB-Ziel).</li>
 * <li>Lage in der Welt gegen {@code Blick.bildpunkt} (≤ 1 px); vor der Wand sichtbar, hinter ihr aus.</li>
 * <li>Deckung ½: Weiß über Schwarz gibt linear ½ (sRGB 188 ± 2).</li>
 * <li>Drehung um ¼: ein Balken 40 × 6 steht 6 × 40.</li>
 * <li>Hochladen nur bei Änderung.</li>
 * <li>Kosten mit 40 Kacheln bei 1920 × 1080 (Auskunft; die Grenze prüft {@code EinblendungCheck} im Steuerhaus).</li>
 * </ol>
 * Aufruf: {@code OverlayCheck}; Attest {@code docs/status/W2_Overlay.md}.
 */
public final class OverlayCheck {
    private static final Attest A = new Attest("com.dan.render.overlay", "0.1");
    static final int W = 640, H = 360;

    public static void main(String[] args) throws Exception {
        System.out.println("OverlayCheck – com.dan.render.overlay 0.1 (Werkbuch HalligHooge 113, W2)");
        try (Geraet g = new Geraet()) {
            pruefe(g);
            kosten(g);
        } catch (Throwable x) {
            A.fehler(x);
        }
        Path at = A.schreibe(Path.of("docs", "status", "W2_Overlay.md"), "W2 – render.overlay ohne Gebiet");
        if (at != null) System.out.println("\nAttest: " + at);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Ein Ziel mit Ausgabe und Tiefe; die Tiefe steht überall auf der Wand im Abstand {@code wand} (m). */
    record Ziel(Bild aus, Bild hdr, Bild tiefe, Bildziel z) implements AutoCloseable {
        @Override public void close() {
            aus.close();
            hdr.close();
            tiefe.close();
        }
    }

    static Ziel ziel(Geraet g, int w, int h, Blick b) {
        Bild aus = new Bild(g, null, "Ausgabe", w, h, Bild.RGBA8S), hdr = new Bild(g, null, "HDR", w, h, Bild.RGBA16F), t = new Bild(g, null, "Tiefe", w, h, Bild.D32F);
        Bildziel z = new Bildziel(hdr, t);
        z.blick = b;
        return new Ziel(aus, hdr, t, z);
    }

    /** Ein Bild: Ausgabe schwarz, Tiefe auf der Wand, dann die Einblendung; gibt RGBA (0 … 1) zurück. */
    static float[] bild(Geraet g, Ziel zi, Einblendung e, double wand, Stoppuhr su) {
        Ablauf a = new Ablauf(g, g.grafik);
        float tw = (float) (zi.z.blick.nah / wand);
        a.zeichne("Ausgabe leeren", new Bild[] {zi.aus}, new float[][] {{0, 0, 0, 1}}, null, null, null, c -> { });
        a.zeichne("Tiefe setzen", new Bild[0], null, zi.tiefe, tw, null, c -> { });
        e.vorbereiten(a, zi.z);
        e.auflegen(a, zi.z, zi.aus);
        g.grafik.einmal(c -> {
            if (su != null) su.runde(c);
            a.aufnehmen(c, su);
        });
        if (su != null) su.sammle(true);
        return zi.aus.lies();
    }

    static boolean ist(float[] p, int x, int y, double r, double gr, double bl, double tol) {
        int i = 4 * (x + W * y);
        return Math.abs(p[i] - r) <= tol && Math.abs(p[i + 1] - gr) <= tol && Math.abs(p[i + 2] - bl) <= tol;
    }

    static void pruefe(Geraet g) {
        Blick b = new Blick(470000, 6046000, 10, 30, -5, 50).groesse(W, H);
        try (Ziel zi = ziel(g, W, H, b); Einblendung e = new Einblendung(g, null)) {
            // 1 Rand
            A.teil("1 Lage am Rand");
            Kachel rot = e.neu("rot", 16, 8).imBild(Kachel.Ecke.OBEN_RECHTS, 10, 20);
            rot.male(gr -> {
                gr.setColor(Color.RED);
                gr.fillRect(0, 0, 16, 8);
            });
            float[] p = bild(g, zi, e, 500, null);
            int innen = 0, aussen = 0;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    boolean soll = x >= W - 26 && x < W - 10 && y >= 20 && y < 28;
                    boolean rotDa = ist(p, x, y, 1, 0, 0, 1.5 / 255);
                    if (soll && rotDa) innen++;
                    if (!soll && p[4 * (x + W * y)] > 0.01) aussen++;
                }
            A.pruefe(innen == 128 && aussen == 0, "Bildpunkte der Kachel oben rechts (16 × 8, Abstand 10, 20)", innen + " rot, " + aussen + " daneben", "128, 0");
            A.pruefe(e.zuletztHochgeladen > 0, "hochgeladen im ersten Bild", f("%d Byte", e.zuletztHochgeladen), "> 0");
            bild(g, zi, e, 500, null);
            A.pruefe(e.zuletztHochgeladen == 0, "hochgeladen im zweiten Bild ohne Änderung", f("%d Byte", e.zuletztHochgeladen), "0");
            e.weg(rot);

            // 2 Welt
            A.teil("2 Lage in der Welt und Verdecken");
            double[] vorn = punkt(b, 0.35, 0.55, 200), hinten = punkt(b, 0.70, 0.45, 800);
            Kachel kv = e.neu("vorn", 9, 9).inWelt(vorn[0], vorn[1], vorn[2], 0.5, 0.5, true);
            Kachel kh = e.neu("hinten", 9, 9).inWelt(hinten[0], hinten[1], hinten[2], 0.5, 0.5, true);
            for (Kachel k : List.of(kv, kh))
                k.male(gr -> {
                    gr.setColor(Color.MAGENTA);
                    gr.fillRect(0, 0, 9, 9);
                });
            p = bild(g, zi, e, 500, null);
            double[] sv = schwerpunkt(p, 0, W / 2), sh = schwerpunkt(p, W / 2, W);
            double[] bv = Einblendung.projiziere(b, vorn[0], vorn[1], vorn[2], 0), bh = Einblendung.projiziere(b, hinten[0], hinten[1], hinten[2], 0);
            double fehler = Math.hypot(sv[0] - bv[0], sv[1] - bv[1]);
            A.pruefe(sv[2] == 81 && fehler <= 1, "vor der Wand (200 m): Lage gegen Blick.bildpunkt", f("%.0f Bildpunkte, Abstand %.2f px", sv[2], fehler), "81, ≤ 1 px");
            A.pruefe(sh[2] == 0, "hinter der Wand (800 m, Wand 500 m): aus", f("%.0f Bildpunkte bei (%.0f, %.0f)", sh[2], bh[0], bh[1]), "0");
            p = bild(g, zi, e, 2000, null);
            sh = schwerpunkt(p, W / 2, W);
            A.pruefe(sh[2] == 81, "dieselbe hinter einer fernen Wand (2000 m): an", f("%.0f Bildpunkte", sh[2]), "81");
            e.weg(kv);
            e.weg(kh);

            // 3 Deckung
            A.teil("3 Deckung und Drehung");
            Kachel weiss = e.neu("weiss", 20, 20).imBild(Kachel.Ecke.OBEN_LINKS, 100, 100);
            weiss.deckung = 0.5;
            weiss.male(gr -> {
                gr.setColor(Color.WHITE);
                gr.fillRect(0, 0, 20, 20);
            });
            p = bild(g, zi, e, 500, null);
            double v = p[4 * (110 + W * 110)] * 255;
            A.pruefe(Math.abs(v - 188) <= 2, "Weiß mit Deckung ½ über Schwarz (sRGB)", f("%.1f", v), "188 ± 2");
            e.weg(weiss);

            // 4 Drehung
            Kachel balken = e.neu("balken", 40, 6).imBild(Kachel.Ecke.MITTE, 0, 0);
            balken.drehung = Math.PI / 2;
            balken.male(gr -> {
                gr.setColor(Color.GREEN);
                gr.fillRect(0, 0, 40, 6);
            });
            p = bild(g, zi, e, 500, null);
            int x0 = W, x1 = -1, y0 = H, y1 = -1;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    if (p[4 * (x + W * y) + 1] > 0.5) {
                        x0 = Math.min(x0, x);
                        x1 = Math.max(x1, x);
                        y0 = Math.min(y0, y);
                        y1 = Math.max(y1, y);
                    }
            int bw = x1 - x0 + 1, bh2 = y1 - y0 + 1;
            A.pruefe(Math.abs(bw - 6) <= 1 && Math.abs(bh2 - 40) <= 1, "Balken 40 × 6, gedreht um ¼", bw + " × " + bh2, "6 × 40 ± 1");
            e.weg(balken);
        }
    }

    /** Ein Ort der Welt, der im Bild bei (fx, fy) liegt, im Abstand d längs des Strahls: {Ost, Nord, Höhe}. */
    static double[] punkt(Blick b, double fx, double fy, double d) {
        double[] s = b.strahl(fx * b.breite, fy * b.hoehe_px);
        return new double[] {b.ost + s[0] * d, b.nord - s[2] * d, b.hoehe + s[1] * d};
    }

    /** Schwerpunkt der magentafarbenen Bildpunkte in den Spalten x0 … x1: {x, y, Zahl} (Mitten bei ,5). */
    static double[] schwerpunkt(float[] p, int x0, int x1) {
        double sx = 0, sy = 0;
        int n = 0;
        for (int y = 0; y < H; y++)
            for (int x = x0; x < x1; x++)
                if (ist(p, x, y, 1, 0, 1, 1.5 / 255)) {
                    sx += x + 0.5;
                    sy += y + 0.5;
                    n++;
                }
        return n == 0 ? new double[] {Double.NaN, Double.NaN, 0} : new double[] {sx / n, sy / n, n};
    }

    /** Kosten: 40 Kacheln (36 Namen mit Verdecken, 4 am Rand) bei 1920 × 1080, Mittel über 20 Bilder. */
    static void kosten(Geraet g) {
        A.teil("5 Kosten (Auskunft)");
        Blick b = new Blick(470000, 6046000, 10, 30, -5, 50).groesse(1920, 1080);
        try (Ziel zi = ziel(g, 1920, 1080, b); Einblendung e = new Einblendung(g, null); Stoppuhr su = new Stoppuhr(g, g.grafik, 16)) {
            if (!su.verfuegbar) {
                A.info("Kosten", "die Warteschlange hat keine Zeitstempel");
                return;
            }
            List<Kachel> alle = new ArrayList<>();
            for (int i = 0; i < 36; i++) {
                double[] o = punkt(b, 0.1 + 0.8 * (i % 6) / 5, 0.4 + 0.3 * (i / 6) / 5, 150 + 20 * i);
                Kachel k = e.neu("Ort " + i, 140, 28).inWelt(o[0], o[1], o[2], 0.5, 1, true);
                alle.add(k);
            }
            alle.add(e.neu("Uhr", 300, 120).imBild(Kachel.Ecke.OBEN_LINKS, 16, 16));
            alle.add(e.neu("Kompass", 128, 128).imBild(Kachel.Ecke.OBEN_RECHTS, 16, 16));
            alle.add(e.neu("Maßstab", 220, 40).imBild(Kachel.Ecke.UNTEN_LINKS, 16, 16));
            alle.add(e.neu("Minikarte", 256, 160).imBild(Kachel.Ecke.UNTEN_RECHTS, 16, 16));
            for (Kachel k : alle)
                k.male(gr -> {
                    gr.setColor(new Color(20, 30, 40, 160));
                    gr.fillRoundRect(0, 0, k.breite, k.hoehe, 8, 8);
                    gr.setColor(Color.WHITE);
                    gr.drawString(k.name, 6, 18);
                });
            double summe = 0, hoch = 0;
            int n = 20;
            for (int i = 0; i < n; i++) {
                if (i % 4 == 0) alle.get(36).male(gr -> gr.fillRect(0, 0, 50, 20));          // die Uhr ändert sich ab und zu
                bild(g, zi, e, 3000, su);
                summe += su.letzte("Einblendung");
                hoch += su.letzte("Einblendung, hochladen");
            }
            A.info("Einblendung je Bild (40 Kacheln, 1920 × 1080)", f("%.3f ms", summe / n));
            A.info("Einblendung, hochladen je Bild (Mittel, Uhr jedes vierte Bild)", f("%.3f ms", hoch / n));
            A.info("gezeichnet zuletzt", e.zuletztGezeichnet + " Kacheln");
        }
    }
}
