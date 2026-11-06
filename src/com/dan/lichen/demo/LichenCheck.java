package com.dan.lichen.demo;

import com.dan.lichen.BeardLichen;
import com.dan.lichen.LichenCard;
import com.dan.lichen.LichenLook;
import com.dan.lichen.gl.LichenGl;

import java.util.Locale;

/**
 * Prüfung von {@code com.dan.lichen} ohne Grafikkarte (seit LituyaBay1958 W6 Runde 7): Wuchs je Form (hängend oder
 * aufrecht, Fasern je Länge, Verzweigung), gleich bei gleichem Samen, Karte (Ansatz, Rand, Deckung), Farbart der Karte
 * wie das Foto der Form (gemessen mit {@link LichenVorlage}), Mip-Stufen erhalten die Deckung.
 */
public final class LichenCheck {
    static int all, bad;

    public static void main(String[] a) {
        // Farbart (R, G, B je Leuchtdichte) der Fotos, mittleres Drittel: Bartflechten2 (Usnea), 1 (Alectoria), 3 (Strauch)
        float[][] foto = {{1.040f, 1.047f, 0.420f}, {1.077f, 1.011f, 0.667f}, {0.874f, 1.050f, 0.873f}, {1.040f, 1.047f, 0.420f}};
        LichenLook[] looks = {LichenLook.usnea(), LichenLook.alectoria(), LichenLook.shrub(), LichenLook.longissima()};
        for (int f = 0; f < 4; f++) {
            LichenLook l = looks[f];
            BeardLichen b = BeardLichen.grow(l, 5), b2 = BeardLichen.grow(l, 5);
            boolean same = b.strands.size() == b2.strands.size();
            for (int i = 0; same && i < b.strands.size(); i++) same = java.util.Arrays.equals(b.strands.get(i).p, b2.strands.get(i).p);
            check(same, l.name + ": gleicher Samen, gleiches Büschel (" + b.strands.size() + " Fäden)");
            float[] L = b.lengths();
            boolean hang = -b.box[2] > 2 * Math.max(0, b.box[3]);
            boolean form = l.gravity > 0.5f ? hang : b.box[3] > -b.box[2];
            check(form, String.format(Locale.GERMAN, "%s: %s (y von %.0f bis %.0f cm)", l.name, l.gravity > 0.5f ? "hängt" : "steht auf", b.box[2] * 100, b.box[3] * 100));
            float perM = L[2] / Math.max(1e-6f, L[0] + L[1]);
            float soll = (l.fibrilMin + l.fibrilMax) / 2 / l.fibrilSpacing;
            check(perM > 0.5f * soll && perM < 1.6f * soll, String.format(Locale.GERMAN, "%s: Fasern %.2f m je m Achse (Abstand und Länge erwarten %.2f)", l.name, perM, soll));
            check(L[1] > 0.1f * L[0], String.format(Locale.GERMAN, "%s: verzweigt (Achsen %.2f m, Zweige %.2f m)", l.name, L[0], L[1]));
            LichenCard c = LichenCard.bake(b, 128, 256);
            float top = 0, rand = 0;
            for (int x = 0; x < c.w; x++) { top = Math.max(top, c.rgba[4 * x + 3]); rand = Math.max(rand, c.rgba[4 * ((c.h - 1) * c.w + x) + 3]); }
            boolean ansatz = c.attachV > 0 && c.attachV < (l.gravity > 0.5f ? 0.1f : 0.95f);
            check(ansatz && top == 0 && c.meanAlpha > 0.01f && c.meanAlpha < 0.4f,
                    String.format(Locale.GERMAN, "%s: Karte %.0f × %.0f cm, Ansatz bei v = %.2f, oberste Zeile leer, Deckung %.0f %%", l.name, c.widthM * 100, c.heightM * 100, c.attachV, c.meanAlpha * 100));
            float y = LichenLook.luma(c.meanColor);
            float[] ch = {c.meanColor[0] / y, c.meanColor[1] / y, c.meanColor[2] / y};
            boolean in = true;
            for (int k = 0; k < 3; k++) in &= Math.abs(ch[k] - foto[f][k]) < 0.12f;
            check(in, String.format(Locale.GERMAN, "%s: Farbart der Karte %.2f %.2f %.2f wie das Foto %.2f %.2f %.2f (±0,12)", l.name, ch[0], ch[1], ch[2], foto[f][0], foto[f][1], foto[f][2]));
            float[] m1 = LichenGl.down(c.rgba, c.w, c.h, c.w / 2, c.h / 2);
            double a0 = 0, a1 = 0;
            for (int i = 0; i < c.w * c.h; i++) a0 += c.rgba[4 * i + 3];
            for (int i = 0; i < c.w * c.h / 4; i++) a1 += m1[4 * i + 3];
            check(Math.abs(a0 / (c.w * c.h) - a1 / (c.w * c.h / 4)) < 1e-4, String.format(Locale.GERMAN, "%s: Mip-Stufe erhält die Deckung (%.4f / %.4f)", l.name, a0 / (c.w * c.h), a1 / (c.w * c.h / 4)));
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
