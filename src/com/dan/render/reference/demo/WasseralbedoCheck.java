package com.dan.render.reference.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Saeule;
import com.dan.core.szene.Wasseralbedo;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.Wasseralbedotabelle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * P10d, F105 A (Werkbuch HalligHooge Teil A, 12.11): die Albedo des Wassers für den Himmel ({@link Wasseralbedo}) gegen
 * die Referenz.
 * <ul>
 * <li>{@code --backen <datei>}: backt die Fläche ({@link Wasseralbedotabelle}) und legt sie ab (dann
 * {@code core/szene/wasseralbedo.bin}).</li>
 * <li>Sonst (die Prüfung): 1 zwei Stützstellen der abgelegten Fläche mit anderer Saat neu gemessen (Rauschen); 2 Wasser
 * mit Säule (Schwebstoff 5, 30, 60 g/m³) gegen Fläche plus Säule aus der Tafel; 3 Auskunft: die Kugel nach Lambert ist
 * fast linear in der Albedo (0,23 aus 0,06 und 0,40).</li>
 * </ul>
 * {@code --proben n} (Vorgabe 600 000 je Lauf). Attest {@code docs/status/P10_Wasseralbedo.md}.
 */
public final class WasseralbedoCheck {
    private static final Attest A = new Attest("com.dan.render.reference", Referenz.VERSION);

    public static void main(String[] args) {
        List<String> a = List.of(args);
        Wasseralbedotabelle t = new Wasseralbedotabelle();
        int ip = a.indexOf("--proben");
        if (ip >= 0) t.proben = Integer.parseInt(a.get(ip + 1));
        int ib = a.indexOf("--backen");
        if (ib >= 0) {
            System.out.println("WasseralbedoCheck --backen: Fläche der Albedo des Wassers, " + t.proben + " Richtungen je Lauf");
            try {
                float[] f = t.backe(System.out::println);
                File d = new File(a.get(ib + 1));
                Wasseralbedotabelle.schreibe(f, d);
                System.out.println("abgelegt: " + d.getAbsolutePath());
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        System.out.println("WasseralbedoCheck – Albedo des Wassers für den Himmel gegen die Referenz (Werkbuch HalligHooge Teil A, P10d, F105 A)");
        try {
            pruefe(t);
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (!a.contains("--ohne-attest")) {
            Path p = A.schreibe(Path.of("docs", "status", "P10_Wasseralbedo.md"), "P10d – WasseralbedoCheck (F105 A)");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static String rgb(double[] v) { return f("R %.3f G %.3f B %.3f", v[0], v[1], v[2]); }

    static void pruefe(Wasseralbedotabelle t) {
        Wasseralbedo wa = Wasseralbedo.lade();
        Saeule s = Saeule.lade();
        Wasseroptik w = new Wasseroptik();
        A.teil("1 Fläche, neu gemessen mit anderer Saat");
        A.info("Messung", f("Himmel als Mittel über Azimut und Höhe %.1f–%.0f° (ohne %.0f° um die Sonne), Auge %.1f m, Sicht %.0f km, Kugel nach Lambert %.2f / %.2f / %.2f; %d Richtungen je Lauf",
                Wasseralbedotabelle.H_MIN, Wasseralbedotabelle.H_MAX, Wasseralbedotabelle.UM_SONNE, Wasseralbedotabelle.AUGE, Wasseralbedotabelle.SICHT_KM,
                Wasseralbedotabelle.LAMBERT[0], Wasseralbedotabelle.LAMBERT[1], Wasseralbedotabelle.LAMBERT[2], t.proben));
        t.saat += 99;
        double fm = 0;
        double[][] punkte = {{4, 8}, {23, 13}};
        double[][][] lambert = new double[2][][];
        for (int p = 0; p < 2; p++) {
            double h = punkte[p][0], wind = punkte[p][1];
            lambert[p] = t.lambert(h, w);
            double[] neu = Wasseralbedotabelle.aequivalent(t.himmel(h, 0, wind, false, w), lambert[p]), alt = wa.flaeche(h, wind);
            for (int c = 0; c < 3; c++) fm = Math.max(fm, Math.abs(neu[c] - alt[c]));
            A.info(f("Sonne %.0f°, Wind %.0f m/s", h, wind), f("abgelegt %s; neu %s", rgb(alt), rgb(neu)));
        }
        A.pruefe(fm <= 0.02, "Fläche, neu gemessen gegen abgelegt, je Kanal", f("größte Abweichung %.3f", fm), "≤ 0,02 [G]");

        A.teil("2 Wasser mit Säule gegen Fläche plus Säule aus der Tafel");
        double sm = 0;
        for (double ssc : new double[] {5, 30, 60}) {
            Wasseroptik wq = new Wasseroptik();
            wq.schwebstoff = ssc;
            double[] ref = Wasseralbedotabelle.aequivalent(t.himmel(23, 0, 13, true, wq), lambert[1]), mod = wa.wasser(23, 13, s, wq), sae = Wasseralbedo.saeule(s, wq);
            for (int c = 0; c < 3; c++) sm = Math.max(sm, Math.abs(ref[c] - mod[c]));
            A.info(f("Schwebstoff %.0f g/m³, Sonne 23°, Wind 13 m/s", ssc), f("Referenz %s; Fläche plus Säule %s (Säule %s)", rgb(ref), rgb(mod), rgb(sae)));
        }
        A.pruefe(sm <= 0.03, "Albedo des Wassers mit Säule, Referenz gegen Fläche plus Säule aus der Tafel, je Kanal", f("größte Abweichung %.3f", sm), "≤ 0,03 [G]");

        A.teil("3 Auskunft: Kugel nach Lambert, linear in der Albedo?");
        double[][] ll = lambert[1];
        double[] lin = new double[3];
        for (int c = 0; c < 3; c++) {
            double g = (Wasseralbedotabelle.LAMBERT[1] - Wasseralbedotabelle.LAMBERT[0]) / (Wasseralbedotabelle.LAMBERT[2] - Wasseralbedotabelle.LAMBERT[0]);
            lin[c] = (ll[0][c] + g * (ll[2][c] - ll[0][c])) / ll[1][c];
        }
        A.info("Himmel bei 0,23, linear aus 0,06 und 0,40, durch gemessen (Sonne 23°)", f("R %.4f G %.4f B %.4f", lin[0], lin[1], lin[2]));
    }
}
