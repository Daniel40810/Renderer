package com.dan.render.frame.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.render.frame.Linse;
import java.nio.file.Path;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfstein 45, Teil Linse (Seegang Werkbuch 107, S9b-3): die Linse mit Kacheln (Höhenkarte je Kachel nur mit ihren Tropfen,
 * das Bild nur in den Kacheln mit Tropfen) gegen den Weg bis S9a (alle Tropfen je Zelle, das ganze Bild). Ein Testbild, darauf
 * Sätze von Tropfen: wenige, viele, sehr viele (die Listen laufen über: dann der alte Weg), am Rand, laufend gestreckt, mit
 * Wasserlinie, mit Haut. Soll: Farbe bitgleich (der Anteil a des HDR-Bilds liest danach niemand: ausgabe.frag nimmt rgb).
 * <pre>  LinseRechteckCheck</pre>
 */
public final class LinseRechteckCheck {
    private static final Attest A = new Attest("com.dan.render.frame", "S9b-3");
    static final int W = 1280, H = 720;

    public static void main(String[] args) {
        System.out.println("LinseRechteckCheck – com.dan.render.frame, S9b-3 (Seegang Werkbuch 107, Prüfstein 45)");
        try (Geraet g = new Geraet()) {
            pruefe(g);
        } catch (Throwable x) {
            A.fehler(x);
        }
        Path at = A.schreibe(Path.of("docs", "status", "S9b3_Linse.md"), "S9b-3 – die Linse nur in den Kacheln mit Tropfen");
        if (at != null) System.out.println("\nAttest: " + at);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** n Tropfen (je 8 float wie Wetglass.Snapshot.drops) mit Halbmesser von amin bis amax (m), Streckung bis st. */
    static float[] tropfen(Random r, int n, double amin, double amax, double st, double rand) {
        float[] t = new float[8 * n];
        for (int i = 0; i < n; i++) {
            double w = r.nextDouble() * 2 * Math.PI;
            t[8 * i] = (float) (-rand + r.nextDouble() * (0.064 + 2 * rand));
            t[8 * i + 1] = (float) (-rand + r.nextDouble() * (0.036 + 2 * rand));
            t[8 * i + 2] = (float) (amin + r.nextDouble() * (amax - amin));
            t[8 * i + 3] = (float) (r.nextDouble() * st);
            t[8 * i + 4] = (float) Math.cos(w);
            t[8 * i + 5] = (float) Math.sin(w);
        }
        return t;
    }

    static void box(int[] b, int x, int y) { b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.max(b[2], x); b[3] = Math.max(b[3], y); }

    static float[] lauf(Geraet g, Linse li, Bildziel z, Bild hdr, float[] test, float[] t, int n, double haut, boolean rechtecke) {
        hdr.schreibe(test);
        li.rechtecke = rechtecke;
        li.tropfen(t, n, haut);
        Ablauf a = new Ablauf(g, g.grafik);
        li.zeichnen(a, z);
        g.grafik.einmal(c -> a.aufnehmen(c, null));
        return hdr.lies();
    }

    static void pruefe(Geraet g) {
        Blick b = new Blick(470000, 6046000, 10, 30, -5, 50).groesse(W, H);
        try (Bild hdr = new Bild(g, null, "HDR", W, H, Bild.RGBA16F); Bild tf = new Bild(g, null, "Tiefe", W, H, Bild.D32F); Linse li = new Linse(g, null)) {
            Bildziel z = new Bildziel(hdr, tf);
            z.blick = b;
            float[] test = new float[4 * W * H];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = 4 * (x + W * y);
                    test[i] = (float) (0.5 + 0.5 * Math.sin(x * 0.031) * Math.cos(y * 0.017));
                    test[i + 1] = (float) ((x ^ y) % 64 / 32.0);
                    test[i + 2] = (float) (y / (double) H * 3);
                    test[i + 3] = 0.5f;
                }
            Random r = new Random(107);
            Object[][] faelle = {
                    {"12 Tropfen, 0,5–2 mm", tropfen(r, 12, 5e-4, 2e-3, 0, 0), 0.0, Double.NaN},
                    {"150 Tropfen, 0,2–1,5 mm, gestreckt", tropfen(r, 150, 2e-4, 1.5e-3, 1.5, 0), 0.0, Double.NaN},
                    {"600 Tropfen, 0,1–1 mm, auch über den Rand", tropfen(r, 600, 1e-4, 1e-3, 0.8, 3e-3), 0.0, Double.NaN},
                    {"1024 Tropfen, 0,5–3 mm (Listen voll: alter Weg)", tropfen(r, 1024, 5e-4, 3e-3, 0.5, 0), 0.0, Double.NaN},
                    {"200 Tropfen mit Wasserlinie", tropfen(r, 200, 2e-4, 1.5e-3, 0.5, 0), 0.0, 0.002},
                    {"80 Tropfen mit Haut 0,1 mm (ganzes Bild)", tropfen(r, 80, 3e-4, 1.5e-3, 0.5, 0), 1e-4, Double.NaN},
                    {"ein Tropfen von 4 mm in der Ecke", new float[] {0.001f, 0.035f, 0.004f, 0, 1, 0, 0, 0}, 0.0, Double.NaN}};
            for (Object[] fa : faelle) {
                String name = (String) fa[0];
                float[] t = (float[]) fa[1];
                int n = t.length / 8;
                double haut = (Double) fa[2], linie = (Double) fa[3];
                li.wasser(linie, 0.0, Double.isNaN(linie) ? 0 : 0.2);
                float[] alt = lauf(g, li, z, hdr, test, t, n, haut, false);
                int kAlt = li.kacheln;
                float[] neu = lauf(g, li, z, hdr, test, t, n, haut, true);
                int kNeu = li.kacheln;
                long anders = 0, gebrochen = 0;
                for (int i = 0; i < alt.length; i++) {
                    if ((i & 3) == 3) continue;
                    if (Float.floatToRawIntBits(alt[i]) != Float.floatToRawIntBits(neu[i])) anders++;
                    if (Float.floatToRawIntBits(alt[i]) != Float.floatToRawIntBits(test[i] == 0 ? 0f : Float.float16ToFloat(Float.floatToFloat16(test[i])))) gebrochen++;
                }
                if (System.getProperty("linse.debug") != null && anders > 0) {
                    int[] bxA = {W, H, -1, -1}, bxN = {W, H, -1, -1}, bxD = {W, H, -1, -1};
                    for (int y = 0; y < H; y++)
                        for (int x = 0; x < W; x++) {
                            int i = 4 * (x + W * y);
                            float tv = Float.float16ToFloat(Float.floatToFloat16(test[i]));
                            if (alt[i] != tv) box(bxA, x, y);
                            if (neu[i] != tv) box(bxN, x, y);
                            if (alt[i] != neu[i]) box(bxD, x, y);
                        }
                    System.out.printf("DEBUG %s: alt %s neu %s anders %s%n", name, java.util.Arrays.toString(bxA), java.util.Arrays.toString(bxN), java.util.Arrays.toString(bxD));
                    int x = (bxD[0] + bxD[2]) / 2, y = (bxD[1] + bxD[3]) / 2, i = 4 * (x + W * y);
                    System.out.printf("DEBUG Mitte %d %d: test %.4f alt %.4f neu %.4f%n", x, y, test[i], alt[i], neu[i]);
                }
                A.pruefe(anders == 0 && kAlt == -1, "Linse mit Kacheln gegen ohne: " + name,
                        f("%d Werte anders; %s, %d Werte gebrochen", anders, kNeu < 0 ? "ganzes Bild" : kNeu + " von " + Linse.KX * Linse.KY + " Kacheln", gebrochen), "0 (bitgleich)");
            }
        }
    }
}
