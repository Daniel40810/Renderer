package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.lauf.SeegangNachlauf;
import com.dan.physics.waves.Waves;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Messung SF14 (Seegang Werkbuch, Datei 4; SF22 A): Hängt der Seegang von {@code physics.waves} am Schritt der Quellen?
 * Derselbe Nachlauf des Seegangs (G2w und G3w, auf der Karte, ohne Ablage) über einen Ausschnitt eines Archivs, einmal mit
 * Schritten der Quellen zu 60 s (wie im Betrieb) und einmal zu 10 s. Verglichen wird Hs am Ende je Gitter über die Zellen
 * mit Hs über 0,1 m: Mittel und 95-%-Wert des relativen Unterschieds, größter Wert, dazu das Mittel von Hs.
 * <p>
 * SF14 A: erst bei mehr als 5 % (im Mittel auf G3w) kommen die Quellen je Teilschritt auch in {@code physics.waves}, und
 * nur zusammen mit einem neuen Lauf der Archive. Die Messung entscheidet nichts selbst; sie legt die Zahlen vor.
 * <pre>  QuellenMessung --gebiet D:\Daten\HalligHooge\gebiet --archiv D:\Daten\HalligHooge\_p8c\archiv [--ab 2026-10-25T09:00:00Z] [--dauer 2] [--vorlauf 1]</pre>
 * Attest {@code docs/status/S3c_Quellen.md}.
 */
public final class QuellenMessung {
    static final Attest A = new Attest("physics.waves – Schritt der Quellen (SF14)", "Seegang Werkbuch");
    /** Schritte der Quellen (s): wie im Betrieb und fein [G, SF14]. */
    static final double[] SCHRITTE = {60, 10};

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        File gebiet = null;
        Path archiv = null;
        Instant ab = Instant.parse("2026-10-25T09:00:00Z");
        double dauer = 2, vorlauf = 1;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--gebiet" -> gebiet = new File(args[i + 1]);
                case "--archiv" -> archiv = Path.of(args[i + 1]);
                case "--ab" -> ab = Instant.parse(args[i + 1]);
                case "--dauer" -> dauer = Double.parseDouble(args[i + 1]);
                case "--vorlauf" -> vorlauf = Double.parseDouble(args[i + 1]);
                default -> { }
            }
        }
        System.out.println("QuellenMessung – SF14 (Seegang Werkbuch)");
        try {
            if (gebiet == null || archiv == null) throw new IllegalArgumentException("--gebiet und --archiv fehlen");
            A.teil(f("Nachlauf des Seegangs ab %s, %.1f h Vorlauf, %.1f h verglichen; Quellen je %s s", ab, vorlauf, dauer, Arrays.toString(SCHRITTE)));
            List<double[][]> hs = new ArrayList<>();
            List<String> namen = new ArrayList<>();
            double[] sek = new double[SCHRITTE.length];
            for (int r = 0; r < SCHRITTE.length; r++) {
                SeegangNachlauf.Einstellung e = new SeegangNachlauf.Einstellung();
                e.gebiet = gebiet;
                e.archiv = archiv;
                e.ablegen = false;
                e.karte = true;
                e.vorlauf = vorlauf * 3600;
                e.hoechstens = dauer * 3600;
                e.ab = ab;
                long a = System.nanoTime();
                try (SeegangNachlauf s = SeegangNachlauf.bauen(e)) {
                    s.waves.dtQuellen = SCHRITTE[r];
                    s.rechne();
                    double[][] h = new double[s.waves.waves.size()][];
                    for (int k = 0; k < h.length; k++) {
                        Waves w = s.waves.waves.get(k);
                        s.waves.rechner.herunter(w);
                        h[k] = new double[w.nc];
                        for (int c = 0; c < w.nc; c++) h[k][c] = w.hs(c);
                        if (r == 0) namen.add(s.waves.gitter.get(k).name);
                    }
                    hs.add(h);
                }
                sek[r] = (System.nanoTime() - a) * 1e-9;
                A.info(f("Lauf mit Quellen je %.0f s", SCHRITTE[r]), f("%.0f s", sek[r]));
            }
            A.teil("Hs am Ende: 10 s gegen 60 s");
            for (int k = 0; k < namen.size(); k++) {
                double[] a = hs.get(0)[k], b = hs.get(1)[k];
                List<Double> rel = new ArrayList<>();
                double sa = 0, sb = 0, mx = 0;
                for (int c = 0; c < a.length; c++) {
                    if (!(a[c] > 0.1) || !Double.isFinite(b[c])) continue;
                    double d = (b[c] - a[c]) / a[c];
                    rel.add(Math.abs(d));
                    sa += a[c]; sb += b[c];
                    mx = Math.max(mx, Math.abs(d));
                }
                if (rel.isEmpty()) { A.info(namen.get(k), "keine Zelle mit Hs über 0,1 m"); continue; }
                double mittel = rel.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double[] so = rel.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                double q95 = so[(int) Math.min(so.length - 1, Math.floor(0.95 * so.length))];
                A.info(namen.get(k) + ": |Hs(10 s) − Hs(60 s)| / Hs(60 s)", f("im Mittel %.2f %%, 95 %% der Zellen unter %.2f %%, größter %.1f %% (%d Zellen); Hs im Mittel %.3f m gegen %.3f m (%+.2f %%)",
                        100 * mittel, 100 * q95, 100 * mx, so.length, sa / so.length, sb / so.length, 100 * (sb - sa) / sa));
                if (k == namen.size() - 1)
                    A.info("SF14: mehr als 5 % im Mittel auf dem feinsten Gitter?", mittel > 0.05 ? "ja – die Quellen je Teilschritt lohnen (mit einem neuen Lauf der Archive)" : "nein – physics.waves bleibt, wie es ist");
            }
            A.pruefe(hs.size() == SCHRITTE.length, "beide Läufe gerechnet", hs.size() + " von " + SCHRITTE.length, "alle");
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", "S3c_Quellen.md"), "S3c – Messung SF14: Schritt der Quellen in physics.waves");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }
}
