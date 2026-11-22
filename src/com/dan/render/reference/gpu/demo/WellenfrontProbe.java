package com.dan.render.reference.gpu.demo;

import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.gpu.ReferenzKarte;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Probe zu F112 A (P11a, Werkbuch HalligHooge Datei 52): Stufe 2 als Wellenfront (wellenfront.glsl, Kernel
 * {@code wf_<variante>_<stufe>.comp}). Teil 1: Register und Scratch der zehn Kernel im Treiber. Teil 2: Wellenfront
 * gegen den großen Kernel mit denselben Zufallszahlen (gleiche Plätze, Mittel). Teil 3: Pfade/s auf der Karte gegen
 * Stufe 1 auf allen Kernen. Schreibt {@code docs/status/P11a_Probe.md}. Entscheidung (F112 A): Schafft die Wellenfront
 * ohne Wasser in den beiden Szenen der Luft mindestens das Zehnfache von Stufe 1, folgt der Umbau; sonst gilt B.
 */
public final class WellenfrontProbe {
    static final String[] STUFEN = {"neu", "schnitt", "flug", "auftrag", "ereignis"};

    record Szene(String name, Referenz ref, boolean ofen, double[] d, String variante, int pfadeTempo) { }

    public static void main(String[] args) throws Exception {
        KarteBefund.z("# P11a – Probe Wellenfront (F112 A), Lauf A7: mit Dauerrate\n");
        List<String> k = new ArrayList<>();
        for (String v : new String[] {"luft", "ebenen"})
            for (String st : STUFEN) k.add("/com/dan/render/reference/gpu/wf_" + v + "_" + st + ".comp");
        try {
            KarteBefund.shaderInfo(k);
        } catch (Throwable e) {
            KarteBefund.z("Teil 1 abgebrochen: " + e);
        }
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        Ebenen e3 = new Ebenen(-10, Double.NaN, new double[] {0.08, 0.08, 0.08}, 10);
        e3.kugel = true;
        Referenz r3 = new Referenz(e3, luft, new Wasseroptik(), 0);
        r3.lichter.add(ReferenceKarteCheck.sonne(40, 0));
        Referenz rb = new Referenz(new Ebenen(-12, Double.NaN, new double[] {1, 1, 1}, 0), luft, new Wasseroptik(), 0);
        Referenz ro = new Referenz(new Ebenen(-12, -10, new double[] {1, 1, 1}, 0), luft, new Wasseroptik(), 0);
        ro.sigma2 = 1e-6;
        Referenz rr = new Referenz(new Ebenen(-3, -1, new double[] {0.3, 0.25, 0.2}, 0.02), luft, new Wasseroptik(), 0);
        rr.lichter.add(ReferenceKarteCheck.sonne(40, 0));
        double[] unten = ReferenceKarteCheck.norm(new double[] {0, -0.5, -0.866});
        Szene[] sz = {
            new Szene("Himmel über der Kugel, Sonne 40°", r3, false, ReferenceKarteCheck.richtung(10, 90), "luft", 64),
            new Szene("Ofentest ohne Wasser (Luft und Boden)", rb, true, unten, "luft", 64),
            new Szene("Ofentest, glattes Wasser über Grund", ro, true, unten, "ebenen", 16),
            new Szene("Raues Wasser über Grund, Sonne 40°, Blick 30° hinab", rr, false, ReferenceKarteCheck.richtung(-30, 90), "ebenen", 32),
        };
        double[] karte = new double[sz.length], gesamt = new double[sz.length];   // Dauerbetrieb, im Ganzen
        try (Geraet g = new Geraet()) {
            Konto konto = g.konto("WellenfrontProbe", 1024L << 20);
            try (ReferenzKarte rk = new ReferenzKarte(g, konto)) {
                KarteBefund.z("\n## Teil 2: Wellenfront gegen den großen Kernel (dieselben Zufallszahlen, 2 048 Plätze)\n");
                for (Szene s : sz) {
                    stelle(rk, s);
                    int n = s.ofen && s.variante.equals("ebenen") ? 256 : 2048, pf = s.ofen && s.variante.equals("ebenen") ? 1 : 2;
                    try {
                        double[] a = rk.schaetze(ReferenzKarte.MODUS_RICHTUNG, n, pf, 7);
                        double[] b = rk.wfSchaetze(s.variante, ReferenzKarte.MODUS_RICHTUNG, n, pf, 7);
                        KarteBefund.z(String.format("- %s (%s): großer Kernel %.5f ± %.5f, Wellenfront %.5f ± %.5f; %d Runden; NaN %d, abgebrochen %d",
                                s.name, s.variante, a[6], a[7], b[6], b[7], rk.wfRunden, rk.nan, rk.abgebrochen));
                    } catch (Throwable e) {
                        KarteBefund.z("- " + s.name + ": abgebrochen: " + e);
                    }
                }
                KarteBefund.z("\n## Teil 3: Pfade/s auf der Karte (Wellenfront)\n");
                for (int i = 0; i < sz.length; i++) {
                    Szene s = sz[i];
                    stelle(rk, s);
                    for (int plaetze : new int[] {16384, 65536}) {
                        try {
                            rk.wellenfront(s.variante, ReferenzKarte.MODUS_RICHTUNG, plaetze, 1, 3);   // aufwärmen
                            rk.wellenfront(s.variante, ReferenzKarte.MODUS_RICHTUNG, plaetze, s.pfadeTempo, 11);
                            double ps = (double) plaetze * s.pfadeTempo / rk.wfSekunden;
                            gesamt[i] = Math.max(gesamt[i], ps);
                            if (!Double.isNaN(rk.wfDauerrate)) karte[i] = Math.max(karte[i], rk.wfDauerrate);
                            KarteBefund.z(String.format("- %s (%s), %d Plätze × %d Pfade: %.2f s auf der Karte, %d Runden in %d Abschickungen, längste %.0f ms: im Ganzen %.3g Pfade/s, im Dauerbetrieb %.3g Pfade/s; NaN %d, abgebrochen %d",
                                    s.name, s.variante, plaetze, s.pfadeTempo, rk.wfSekunden, rk.wfRunden, rk.wfAbschickungen, rk.wfGroessteMs, ps, rk.wfDauerrate, rk.nan, rk.abgebrochen));
                        } catch (Throwable e) {
                            KarteBefund.z("- " + s.name + ", " + plaetze + " Plätze: abgebrochen: " + e);
                            break;
                        }
                    }
                }
            }
        }
        int n = Runtime.getRuntime().availableProcessors();
        KarteBefund.z("\n## Teil 4: Stufe 1 auf dem Prozessor (" + n + " Fäden, je rund 4 s) und Verhältnis\n");
        for (int i = 0; i < sz.length; i++) {
            double s1 = KarteBefund.stufe1(n, sz[i].name, sz[i].ref, sz[i].ofen, sz[i].d);
            KarteBefund.z(String.format("  Karte / Stufe 1: im Dauerbetrieb %.2f, im Ganzen %.2f", karte[i] / s1, gesamt[i] / s1));
        }
        Files.createDirectories(Path.of("docs", "status"));
        Files.writeString(Path.of("docs", "status", "P11a_Probe.md"), KarteBefund.B.toString());
        System.out.println("\nProbe: docs/status/P11a_Probe.md");
    }

    static void stelle(ReferenzKarte rk, Szene s) {
        rk.schalter().uebernimm(s.ref);
        if (s.ofen) rk.ofen();
        rk.strahl(new double[3], s.d);
    }
}
