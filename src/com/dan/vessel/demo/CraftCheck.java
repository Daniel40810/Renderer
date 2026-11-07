package com.dan.vessel.demo;

import com.dan.vessel.Hull;
import com.dan.vessel.Kutter;
import com.dan.vessel.Shipwright;
import com.dan.vessel.Troller;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Prüfung des Bootsbauers (seit LituyaBay1958 W8a): baut Edrie, Badger und Sunmore, zählt Dreiecke je Material, prüft,
 * dass der Rumpf genau auf den Spanten des Starrkörpers liegt, dass Verdeckung und Kanten im Bereich sind, dass Glas,
 * Schrift, Lampen und Drähte da sind, und dass zweimal derselbe Bauplan dasselbe Netz gibt.
 * {@code --export datei}: das Netz der Edrie als Binärdatei (für Vorschauen außerhalb von Java).
 */
public final class CraftCheck {
    static int fails, checks;

    static void check(String what, boolean ok, String detail) {
        checks++;
        if (!ok) fails++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what + (detail.isEmpty() ? "" : " – " + detail));
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.GERMANY);
        System.out.println("CraftCheck – com.dan.vessel.Shipwright (LituyaBay1958 W8a)");
        for (String n : new String[]{"Edrie", "Badger", "Sunmore"}) {
            Hull h = switch (n) { case "Edrie" -> Kutter.edrie().hull; case "Badger" -> Kutter.badger().hull; default -> Kutter.sunmore().hull; };
            long t0 = System.nanoTime();
            Troller t = Troller.fuer(h);
            Shipwright.Model m = Shipwright.build(t);
            double ms = (System.nanoTime() - t0) / 1e6;
            int[] per = new int[Shipwright.MATERIAL.length];
            double aoSum = 0, aoMin = 1, aoMax = 0, edgeMax = 0;
            int nv = 0;
            for (float[] a : new float[][]{m.opaque, m.glass})
                for (int i = 0; i < a.length; i += Shipwright.STRIDE) {
                    per[(int) a[i + 11]]++;
                    aoSum += a[i + 12]; aoMin = Math.min(aoMin, a[i + 12]); aoMax = Math.max(aoMax, a[i + 12]);
                    edgeMax = Math.max(edgeMax, a[i + 13]);
                    nv++;
                }
            System.out.printf("%s (%s): %d Dreiecke, %d Drähte, %d Lampen, gebaut in %.0f ms%n", n, t.name, m.triangles(), m.wires.length / Shipwright.WIRE, m.lamps.length / 7, ms);
            StringBuilder sb = new StringBuilder("  je Material:");
            for (int i = 0; i < per.length; i++) if (per[i] > 0) sb.append(' ').append(Shipwright.MATERIAL[i]).append(' ').append(per[i] / 3);
            System.out.println(sb);
            // Rumpf auf den Spanten: jede Ecke des Rumpfs (ohne Spiegel und Ruder) liegt auf halfBreadth
            double worst = 0;
            int n0 = 0;
            for (int i = 0; i < m.opaque.length; i += Shipwright.STRIDE) {
                if ((int) m.opaque[i + 11] != Shipwright.M_HULL) continue;
                double s = m.opaque[i], y = m.opaque[i + 1], q = Math.abs(m.opaque[i + 2]);
                if (Math.abs(m.opaque[i + 3]) > 0.9 || s < -0.5 * h.loa + 0.02 || s > 0.49 * h.loa) continue;   // Spiegel, Ruder, Steven
                if (y > h.deck(s) - 0.02 || y < h.keel(s) + 0.02) continue;
                double hb = h.halfBreadth(s, y);
                if (hb <= h.keelWidth / 2 + 1e-3) continue;
                worst = Math.max(worst, Math.abs(q - hb));
                n0++;
            }
            check("Rumpf auf den Spanten des Starrkörpers", worst < 0.04, String.format("größte Abweichung %.1f mm an %d Ecken", worst * 1000, n0));
            double[] b = m.bounds;
            check("Länge über alles mit Ruder", b[3] - b[0] > h.loa && b[3] - b[0] < h.loa + 1.2, String.format("%.2f m (Rumpf %.2f m)", b[3] - b[0], h.loa));
            check("Höhe bis zum Masttopp", b[4] > h.deck(h.mastS) + h.mastHeight, String.format("%.2f m über der Kielunterkante", b[4]));
            check("Spannweite der Trollstangen", b[5] - b[2] > 14, String.format("%.1f m", b[5] - b[2]));
            check("Verdeckung im Bereich", aoMin >= 0 && aoMax <= 1 && aoSum / nv > 0.35 && aoSum / nv < 0.95, String.format("Mittel %.2f, %.2f … %.2f", aoSum / nv, aoMin, aoMax));
            check("Kanten im Bereich", edgeMax > 0.5 && edgeMax <= 1, String.format("größte %.2f", edgeMax));
            check("Glas (Scheiben, Bullaugen)", per[Shipwright.M_GLASS] >= 3 * 40, (per[Shipwright.M_GLASS] / 3) + " Dreiecke");
            check("Schrift an Bug und Spiegel", per[Shipwright.M_DECAL] >= 3 * 40, (per[Shipwright.M_DECAL] / 3) + " Dreiecke");
            check("Drähte und Leinen", m.wires.length / Shipwright.WIRE >= 15, (m.wires.length / Shipwright.WIRE) + " Strecken");
            check("Ankerlicht vor Anker", m.lamps.length >= 7, (m.lamps.length / 7) + " Lampen");
            Shipwright.Model m2 = Shipwright.build(Troller.fuer(h));
            check("reproduzierbar", java.util.Arrays.equals(m.opaque, m2.opaque) && java.util.Arrays.equals(m.wires, m2.wires), "");
            check("Bauzeit", ms < 20000, String.format("%.0f ms", ms));
            if (n.equals("Edrie") && args.length >= 2 && args[0].equals("--export")) {
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])))) {
                    for (float[] a : new float[][]{m.opaque, m.glass, m.wires, m.lamps}) {
                        out.writeInt(Integer.reverseBytes(a.length));
                        for (float f : a) out.writeInt(Integer.reverseBytes(Float.floatToIntBits(f)));
                    }
                }
                System.out.println("  Netz geschrieben: " + args[1]);
            }
        }
        System.out.println(fails == 0 ? "Alles in Ordnung (" + checks + " Prüfungen)." : fails + " von " + checks + " Prüfungen FEHLER.");
        if (fails > 0) System.exit(1);
    }
}
