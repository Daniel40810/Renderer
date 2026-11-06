package com.dan.moss.demo;

import com.dan.moss.MossLook;
import com.dan.moss.MossTexture;

import java.util.Arrays;
import java.util.Locale;

/**
 * Prüfung von {@code com.dan.moss} ohne Grafikkarte (seit LituyaBay1958 W6 Runde 7): nahtlos, gleich bei gleichem
 * Samen, Deckung, Farbart je Drittel innerhalb der Spanne der Fotos (gemessen mit {@link MossVorlage} an Moos1–Moos4),
 * Kontrast, Albedo, Höhe, Normalen.
 */
public final class MossCheck {
    static int all, bad;

    /** Farbart R, G, B je Leuchtdichte über alle Drittel der vier Fotos: kleinster und größter Wert (MossVorlage, Runde 7). */
    static final float[] FOTO_MIN = {0.375f, 1.094f, 0.021f}, FOTO_MAX = {0.969f, 1.283f, 0.402f};

    public static void main(String[] a) {
        for (MossLook look : new MossLook[]{MossLook.feather(), MossLook.cushion()}) {
            long t0 = System.nanoTime();
            MossTexture t = MossTexture.bake(look, 256, 42);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            MossTexture t2 = MossTexture.bake(look, 256, 42);
            int n = t.n;
            check(Arrays.equals(t.albedo, t2.albedo) && Arrays.equals(t.height, t2.height), look.name + ": gleicher Samen, gleiche Textur (" + ms + " ms)");
            // nahtlos: Sprung über die Kante wie im Inneren
            double edge = 0, inner = 0;
            for (int y = 0; y < n; y++) {
                edge += Math.abs(t.height[y * n] - t.height[y * n + n - 1]) + Math.abs(t.height[y] - t.height[(n - 1) * n + y]);
                inner += Math.abs(t.height[y * n + n / 2] - t.height[y * n + n / 2 - 1]) + Math.abs(t.height[(n / 2) * n + y] - t.height[(n / 2 - 1) * n + y]);
            }
            check(edge < inner * 1.4, String.format(Locale.GERMAN, "%s: nahtlos (Sprung an der Kante %.3f, im Inneren %.3f)", look.name, edge / (2 * n), inner / (2 * n)));
            check(t.coverage > 0.95f, String.format(Locale.GERMAN, "%s: Triebe decken %.0f %% (Unterschicht, keine kahlen Lücken)", look.name, t.coverage * 100));
            // Farbart je Drittel der gesehenen Leuchtdichte
            float[] y = t.seenLuma(), s = y.clone();
            Arrays.sort(s);
            float q1 = s[n * n / 3], q2 = s[2 * n * n / 3];
            double[][] acc = new double[3][4];
            for (int i = 0; i < n * n; i++) {
                int k = y[i] < q1 ? 0 : y[i] < q2 ? 1 : 2;
                for (int c = 0; c < 3; c++) acc[k][c] += t.albedo[3 * i + c] * t.ao[i];
                acc[k][3] += y[i];
            }
            boolean in = true;
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 3; k++) {
                sb.append(k == 0 ? "" : " / ");
                for (int c = 0; c < 3; c++) {
                    float v = (float) (acc[k][c] / acc[k][3]);
                    sb.append(c == 0 ? "" : " ").append(String.format(Locale.GERMAN, "%.2f", v));
                    in &= v >= FOTO_MIN[c] * 0.9f && v <= FOTO_MAX[c] * 1.1f;
                }
            }
            check(in, look.name + ": Farbart dunkel / mittel / hell (" + sb + ") in der Spanne der Fotos ±10 %");
            float p25 = s[n * n / 4], p75 = s[3 * n * n / 4];
            check(p75 / p25 > 1.6f && p75 / p25 < 8, String.format(Locale.GERMAN, "%s: Kontrast ohne Licht P75/P25 = %.1f (Fotos mit Licht 4,4–7,7)", look.name, p75 / p25));
            float ym = MossLook.luma(t.meanSeen);
            check(ym > 0.035f && ym < 0.15f, String.format(Locale.GERMAN, "%s: gesehene Albedo im Mittel %.3f (Moos 0,035–0,15 [G])", look.name, ym));
            check(t.heightRange > look.shootMax && t.heightRange < 6 * look.shootMax,
                    String.format(Locale.GERMAN, "%s: Höhe der Polster %.1f mm (Triebe bis %.0f mm)", look.name, t.heightRange * 1000, look.shootMax * 1000));
            boolean nok = true;
            for (int i = 0; i < n * n && nok; i++) {
                float nx = t.normal[2 * i], ny = t.normal[2 * i + 1];
                nok = Float.isFinite(nx) && Float.isFinite(ny) && nx * nx + ny * ny <= 1.0001f;
            }
            check(nok, look.name + ": Normalen gültig");
        }
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        if (bad > 0) System.exit(1);
    }

    static void check(boolean ok, String what) {
        all++;
        if (!ok) bad++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
    }
}
