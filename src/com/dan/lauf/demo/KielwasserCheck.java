package com.dan.lauf.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Kielwasser;
import com.dan.core.szene.Profilpuffer;
import com.dan.core.szene.Wasserpunkte;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Flotte;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.seegang.Kielwasserkarte;
import com.dan.render.seegang.Koerpersuche;
import com.dan.render.seegang.SeegangKarte;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S7d (Seegang Werkbuch 89; seit S7d2, Datei 90: ohne das eigene Kielwasser, Teil 4 nach der Tide, Kentern): das Kielwasser der fahrenden Schiffe.
 * <ol>
 * <li><b>Tiefes Wasser</b> (Prüfstein 26, {@link Kielwasser} in double): ein Boot von 8 m mit 3 m/s über 1000 m Tiefe;
 * die stärkste Welle seitlich unter 19,47° ± 0,5° (Kelvin), die Querwellen auf der Achse 2π V²/g ± 3 %.</li>
 * <li><b>Flaches Wasser</b> (Prüfstein 27): Fr_h 1,2 – der halbe Winkel des Keils arcsin(1/Fr_h) = 56,4° ± 2°; dazu als
 * Auskunft der Keil bei Fr_h 0,5 bis 0,99 und 1,5, 2.</li>
 * <li><b>Karte gegen double</b> (Prüfstein 25): eine Fähre in Fahrt in 4 m Wasser und ein Boot in einer Kurve; die
 * Kacheln von {@code see_kiel.comp} gegen {@link Kielwasser#kachel} (Höhe ≤ 1 mm, Schaum ≤ 0,01), und die Höhe des
 * Kielwassers an 500 Punkten aus {@code see_koerper.comp} gegen double (≤ 1 mm).</li>
 * <li><b>In der Anwendung</b> (Prüfsteine 28, 29): ruhiger Tag, die Hilligenlei kommt nach Fahrplan (10:30 UTC) und legt an;
 * Kosten der Kacheln und ihres Einrechnens je Bild (≤ 0,2 ms [G]); der Kutter im Schwell der Fähre (Auskunft: Hub und
 * Rollen vor und nach dem Durchgang); Bilder für die Sichtprüfung. Seit S7d4 (Datei 92): Beginn 360 s vor der Ankunft;
 * die Reihe des Kutters nach dem Lauf ausgewertet (Befund L5d3 4), 240 s nach dem Durchgang, mit dem Kielwasser am Kutter in
 * double; das Bild aus der neuen Ansicht {@code kielwasser}, wenn die Fähre 40 m über ihren Punkt hinaus ist (SF124 A); die
 * Kosten einzeln (SF123 A); Verholen: 180 s nach dem Festmachen ≤ 1,5 m und ≤ 3° vom Platz [G] (SF125 A). Seit S7d5 (Datei 93):
 * 4a die Ankunft ab 520 s vorher, pünktlich ± 2 min [G] (SF130 A); 4b die Abfahrt aus der Ansicht {@code kutter}, dort
 * Prüfstein 29 (SF129 A). Teil 3: die weite Kachel nur Schaum (SF128 A). Seit S7d6 (Datei 94): Teil 3 das Kielwasser der
 * Körper direkt aus den Spuren gegen double am Ort, auch außerhalb der nahen Kachel (SF133 A); 4a ab 2,5 km nach dem Plan mit
 * dem Fahrprofil alle 10 s (SF136 A), die Ansicht {@code kielwasser} näher an der Route; 4b Bilder des Schwells nach 90, 150
 * und 210 s, danach ein Bild aus der Ansicht {@code kielwasser} (SF135 A). Seit S7d7 (Datei 95): Teil 3 mit dem Auge der Kacheln
 * fern (Befund L5d6 6); 4a endet nach der Zeit der Szene (SF139 A) und beginnt nach dem Plan des Lotsen mit {@code BREMS_AB}.</li>
 * <li>Seit S7e2 (Datei 101), nur mit {@code --teil 5}: der Adler-Express kommt an (ruhigsee, 12:20 UTC), pünktlich ± 2 min
 * [G], nicht gekentert, 4 Leinen; Attest dann {@code docs/status/S7e_Adler.md}.</li>
 * </ol>
 * <pre>  KielwasserCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--teil n]</pre>
 */
public final class KielwasserCheck {
    static final Attest A = new Attest("Kielwasser der Schiffe (S7d)", Kielwasser.VERSION);

    public static void main(String[] args) {
        File szenen = null;
        int breite = 1280, hoehe = 720;
        java.util.Set<String> nur = new java.util.HashSet<>();
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                case "--teil" -> nur.add(args[++i]);
                default -> { }
            }
        System.out.println("KielwasserCheck – S7d, S7d2, S7d4, S7d5, S7d6, S7d7 (Seegang Werkbuch 89, 90, 92, 93, 94, 95)");
        long t0 = System.nanoTime();
        try {
            if (nur.isEmpty() || nur.contains("1")) SchwimmerCheck.zeit("1", KielwasserCheck::tief);
            if (nur.isEmpty() || nur.contains("2")) SchwimmerCheck.zeit("2", KielwasserCheck::flach);
            if (nur.isEmpty() || nur.contains("3")) SchwimmerCheck.zeit("3", KielwasserCheck::karte);
            if (nur.isEmpty() || nur.contains("4")) {
                if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
                File sz = szenen;
                SchwimmerCheck.zeit("4", () -> anwendung(sz, breite, hoehe));
            }
            if (nur.contains("5")) {                                             // seit S7e2 (Datei 101): nur auf Wunsch
                if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
                File sz = szenen;
                SchwimmerCheck.zeit("5", () -> adler(sz, breite, hoehe));
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        // seit S7e2: Teil 5 allein schreibt sein eigenes Attest
        Path pa = A.schreibe(Path.of("docs", "status", nur.equals(java.util.Set.of("5")) ? "S7e_Adler.md" : "S7d_Kielwasser.md"),
                "S7d – Kielwasser (Kielwasser " + Kielwasser.VERSION + ", Karte " + Kielwasserkarte.VERSION + ")");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Ein Kielwasser mit einer Quelle, die t Sekunden geradeaus nach Nord gefahren ist (Bug zuletzt bei y = V t). */
    static Kielwasser gerade(double l, double b, double tg, double v, double h, double dauer) {
        Kielwasser k = new Kielwasser();
        Kielwasser.Quelle q = k.neu("Probe", l, b, tg, false);
        for (int i = 0; i <= (int) (dauer / 0.1); i++) k.setze(q, 0, i * v * 0.1, 0, v, h, i * 0.1);
        return k;
    }

    // ------------------------------------------------------------------ 1

    static void tief() {
        A.teil("1 Tiefes Wasser (Prüfstein 26)");
        double v = 3, L = 8;
        Kielwasser k = gerade(L, 2.5, 0.5, v, 1000, 600);
        Kielwasser.Quelle q = k.quellen().get(0);
        double yb = q.bug()[1];
        // stärkste Welle seitlich in r = 24 L (in der Reichweite des Musters; Hülle über eine Wellenlänge)
        double r = 24 * L, best = 0, bestA = 0, lam = 2 * Math.PI * v * v / 9.81;
        for (double a = 5; a <= 30; a += 0.05) {
            double sa = Math.sin(Math.toRadians(a)), ca = Math.cos(Math.toRadians(a)), e = 0;
            for (int s = 0; s < 40; s++) {
                double rr = r + s * lam / 40;
                e = Math.max(e, Math.abs(k.hoehe(rr * sa, yb - rr * ca)));
            }
            if (e > best) { best = e; bestA = a; }
        }
        A.pruefe(Math.abs(bestA - Kielwasser.keilwinkel(v, 1000)) <= 0.5 && Math.abs(bestA - 19.47) <= 0.5, "stärkste Welle seitlich (r = 24 L)",
                f("%.2f° (Kelvin 19,47°; Tafel %.2f°)", bestA, Kielwasser.keilwinkel(v, 1000)), "19,47° ± 0,5°");
        // Querwellen auf der Achse: Abstand der fallenden Nulldurchgänge zwischen 5 und 25 L hinter dem Bug
        double erst = Double.NaN, letzt = Double.NaN, vor = Double.NaN;
        int z = 0;
        for (int i = 0; i < 20000; i++) {
            double y = yb - 5 * L - i * 0.01;
            if (yb - y > 25 * L) break;
            double h = k.hoehe(0.001, y);
            if (!Double.isNaN(vor) && vor > 0 && h <= 0) { if (z == 0) erst = y; letzt = y; z++; }
            vor = h;
        }
        double lm = (erst - letzt) / (z - 1);
        A.pruefe(Math.abs(lm / lam - 1) <= 0.03, "Querwellen auf der Achse: Wellenlänge", f("%.3f m aus %d Durchgängen (2π V²/g = %.3f m, %+.2f %%)", lm, z, lam, 100 * (lm / lam - 1)), "± 3 %");
        A.info("Höhe in einer Länge Abstand quer (Vorgabe [G])", f("%.3f m bei Fr %.2f", 0.12 * 0.5 * Math.pow(v / Math.sqrt(9.81 * L), 2) / (0.08 + Math.pow(v / Math.sqrt(9.81 * L), 2)), v / Math.sqrt(9.81 * L)));
    }

    // ------------------------------------------------------------------ 2

    static void flach() {
        A.teil("2 Flaches Wasser (Prüfstein 27)");
        double h = 4, v = 1.2 * Math.sqrt(9.81 * h);
        double kw = Kielwasser.keilwinkel(v, h), soll = Math.toDegrees(Math.asin(1 / 1.2));
        A.pruefe(Math.abs(kw - soll) <= 2, "Fr_h 1,2: halber Winkel des Keils", f("%.2f° (arcsin(1/Fr_h) = %.2f°)", kw, soll), "± 2°");
        StringBuilder sb = new StringBuilder();
        for (double fr : new double[] {0.5, 0.7, 0.9, 0.95, 0.99, 1.5, 2.0}) sb.append(f("Fr_h %.2f: %.1f°; ", fr, Kielwasser.keilwinkel(fr * Math.sqrt(9.81 * h), h)));
        A.info("Keil nach Fr_h (Tafel)", sb.toString());
        // Muster bei Fr_h 1,2: keine Querwellen auf der Achse
        Kielwasser k = gerade(38, 10, 1.6, v, h, 300);
        Kielwasser.Quelle q = k.quellen().get(0);
        double yb = q.bug()[1], mx = 0, seit = 0;
        for (int i = 0; i < 400; i++) mx = Math.max(mx, Math.abs(k.hoehe(0.001, yb - 100 - i * 0.5)));
        for (int i = 0; i < 400; i++) { double rr = 300, a = Math.toRadians(50 + i * 0.02); seit = Math.max(seit, Math.abs(k.hoehe(rr * Math.sin(a), yb - rr * Math.cos(a)))); }
        A.info("Fr_h 1,2: Höhe auf der Achse (100–300 m hinter dem Bug) gegen die Höhe am Rand des Keils (r = 300 m)", f("%.4f m gegen %.4f m", mx, seit));
    }

    // ------------------------------------------------------------------ 3

    static SeegangKarte karte;

    static void karte() {
        A.teil("3 Karte gegen double (Prüfstein 25)");
        Profilpuffer p = new Profilpuffer(SchwimmerCheck.OM, SchwimmerCheck.UNTEN, SchwimmerCheck.OBEN, SchwimmerCheck.ND, 0x5EE6A3L, false);
        SchwimmerCheck.ob = SchwimmerCheck.szene(p);
        karte = SeegangKarte.mitGeraet(SchwimmerCheck.ob, SchwimmerCheck.STUFEN, SchwimmerCheck.N, SchwimmerCheck.D0);
        Geraet g = karte.geraet();
        A.info("Gerät", g.name + " (" + g.treiber + ")");
        karte.eingaben();
        double bx = SchwimmerCheck.BX, by = SchwimmerCheck.BY;
        karte.bezug(bx, by);
        Kielwasserkarte kk = new Kielwasserkarte(g, g.konto("Kielwasser", 64L << 20));
        Koerpersuche ks = new Koerpersuche(g, g.konto("Körpersuche", 256L << 20), SchwimmerCheck.NB);
        try {
            // eine Fähre geradeaus nach Nordost, ein Boot in einer Kurve; Auge zwischen beiden
            Kielwasser k = new Kielwasser();
            Kielwasser.Quelle fae = k.neu("Fähre", 38.3, 10, 1.6, true), boot = k.neu("Boot", 16, 4.8, 1.6, true);
            double ex = 466000, ey = 6046000;
            for (int i = 0; i <= 1200; i++) {
                double t = i * 0.1;
                double kf = 40, vf = 5.1;
                k.setze(fae, ex - 300 + Math.sin(Math.toRadians(kf)) * vf * t, ey - 400 + Math.cos(Math.toRadians(kf)) * vf * t, kf, vf, 4, t);
                double om = 0.01, kb = Math.toDegrees(om * t), rb = 3.5 / om;              // Kreis, Halbmesser 350 m
                k.setze(boot, ex + 100 + rb * (Math.cos(om * t) - 1), ey - 200 + rb * Math.sin(om * t), (kb + 360) % 360, 3.5, 6, t);
            }
            long n0 = System.nanoTime();
            float[] gpu = kk.sofort(k, bx, by, ex, ey);
            double ms = (System.nanoTime() - n0) / 1e6;
            if (gpu == null) throw new IllegalStateException("keine Kacheln");
            int n = Kielwasserkarte.N;
            double dh = 0, df = 0, hmax = 0;
            double[][] cpu = new double[2][4 * n * n];
            for (int t = 0; t < 2; t++) {
                k.kachel(kk.kachel[3 * t] + bx, kk.kachel[3 * t + 1] + by, kk.kachel[3 * t + 2], n, cpu[t]);
                if (t == 1) nurSchaum(cpu[t]);                                 // seit S7d5 (SF128 A): die weite Kachel nur Schaum
                for (int c = 0; c < n * n; c++) {
                    dh = Math.max(dh, Math.abs(gpu[4 * (t * n * n + c)] - cpu[t][4 * c]));
                    df = Math.max(df, Math.abs(gpu[4 * (t * n * n + c) + 1] - cpu[t][4 * c + 1]));
                    hmax = Math.max(hmax, Math.abs(cpu[t][4 * c]));
                }
            }
            A.info("Kacheln", f("%d × %d Zellen, nah %.0f m, weit %.0f m; größte Höhe %.3f m; sofort mit Senden und Warten %.1f ms", n, n, Kielwasserkarte.NAH, Kielwasserkarte.WEIT, hmax, ms));
            A.pruefe(dh <= 0.001 && df <= 0.01, "Kacheln der Karte gegen double", f("Höhe größte Abweichung %.3f mm, Schaum %.4f", 1000 * dh, df), "≤ 1 mm; ≤ 0,01");
            // seit S7d6 (SF133 A): die Körper rechnen das Kielwasser direkt aus den Spuren. Höhe an 500 Punkten aus see_koerper
            // gegen Kielwasser.am in double am Ort selbst (größte Wellenzahl Koerpersuche.KIEL_KMAX): die Hälfte um das Auge
            // (±120 m), die Hälfte längs des Anfangs der Spur der Fähre, 350–650 m vom Auge, außerhalb der nahen Kachel
            java.util.Random r = new java.util.Random(3);
            int m = 500, h2 = m / 2;
            double[] x = new double[m], y = new double[m];
            double kfR = Math.toRadians(40);
            for (int i = 0; i < m; i++) {
                if (i < h2) { x[i] = ex - 120 + 240 * r.nextDouble(); y[i] = ey - 120 + 240 * r.nextDouble(); continue; }
                double s0 = 20 + 260 * r.nextDouble(), quer = -60 + 120 * r.nextDouble();            // längs ab dem Start, quer
                x[i] = ex - 300 + Math.sin(kfR) * s0 + Math.cos(kfR) * quer;
                y[i] = ey - 400 + Math.cos(kfR) * s0 - Math.sin(kfR) * quer;
            }
            Wasserpunkte.Anfrage q = new Wasserpunkte.Anfrage(1, x, y, m, new int[] {0}, new int[] {m});
            Wasserpunkte wp = new Wasserpunkte();
            karte.zeit(SchwimmerCheck.T0);
            // seit S7d7 (Befund L5d6 6): das Auge der Kacheln 2,1 km weg, so liegen alle Punkte außerhalb der nahen Kachel, auch
            // dort, wo das Kielwasser hoch ist; see_koerper rechnet ohne Kacheln
            kk.sofort(k, bx, by, ex + 1500, ey + 1500);
            ks.sofort(karte, SchwimmerCheck.T0, q, wp, kk, false);
            Wasserpunkte.Stand st = wp.stand();
            double dk = 0, hFern = 0;
            int nFern = 0;
            double[] o = new double[4];
            for (int i = 0; i < m; i++) {
                k.am(x[i], y[i], Koerpersuche.KIEL_KMAX, o, null);
                dk = Math.max(dk, Math.abs(st.werte[st.je * i + 8] - o[0]));
                if (!inKachel(kk, 0, x[i] - bx, y[i] - by)) { nFern++; hFern = Math.max(hFern, Math.abs(o[0])); }
            }
            A.pruefe(dk <= 0.001, "Kielwasser an den Punkten der Körper (see_koerper, seit S7d6 direkt aus den Spuren) gegen double am Ort selbst",
                    f("größte Abweichung %.3f mm; seit S7d7 mit dem Auge der Kacheln 2,1 km entfernt: %d von %d Punkten außerhalb der nahen Kachel, Kielwasser dort bis %.3f m",
                            1000 * dk, nFern, m, hFern), "≤ 1 mm");
            // seit S7d2: Punkte als Körper der Fähre (die erste Hälfte, um ihre Mitte bis 0,6 L) und ohne Quelle (die zweite); an
            // der Fähre fehlt ihr eigenes Kielwasser. Seit S7d6 gegen Kielwasser.am ohne die Fähre in double.
            for (int i = 0; i < h2; i++) {
                double rr = 0.6 * 38.3 * Math.sqrt(r.nextDouble()), ww = 2 * Math.PI * r.nextDouble();
                x[i] = fae.x + rr * Math.cos(ww);
                y[i] = fae.y + rr * Math.sin(ww);
            }
            Wasserpunkte.Anfrage q2 = new Wasserpunkte.Anfrage(2, x, y, m, new int[] {0, h2}, new int[] {h2, m - h2}, new Kielwasser.Quelle[] {fae, null});
            Wasserpunkte wp2 = new Wasserpunkte();
            ks.sofort(karte, SchwimmerCheck.T0, q2, wp2, kk, false);
            Wasserpunkte.Stand st2 = wp2.stand();
            double de = 0, deAnders = 0, eigenMax = 0;
            double[] oa = new double[4];
            for (int i = 0; i < m; i++) {
                k.am(x[i], y[i], Koerpersuche.KIEL_KMAX, oa, null);
                k.am(x[i], y[i], Koerpersuche.KIEL_KMAX, o, null, i < h2 ? fae : null);
                de = Math.max(de, Math.abs(st2.werte[st2.je * i + 8] - o[0]));
                if (i < h2) { eigenMax = Math.max(eigenMax, Math.abs(oa[0] - o[0])); deAnders = Math.max(deAnders, Math.abs(st2.werte[st2.je * i + 8] - oa[0])); }
            }
            A.pruefe(de <= 0.001, "Ohne das eigene Kielwasser (seit S7d2): see_koerper an den Punkten der Fähre gegen double ohne die Fähre, die übrigen mit allen",
                    f("größte Abweichung %.3f mm; das eigene war dort bis %.3f m hoch (Abstand zu „mit allen“ bis %.3f m)", 1000 * de, eigenMax, deAnders), "≤ 1 mm");
        } finally {
            ks.close();
            kk.close();
            karte.close();
        }
    }

    /** Seit S7d6 (SF136 A): ab diesem Abstand vom Anleger (m, nach dem Plan) beginnt Teil 4a und schreibt das Fahrprofil. */
    static final double PROFIL_AB = 2500;

    /** Der Punkt, auf den die Ansicht schaut (Mitte des Bilds auf dem Wasser): Ost, Nord. */
    static double[] punktDerAnsicht(Buehne b, Blick bl) {
        double sp0 = b.spiegel(bl.ost, bl.nord);
        double hAuge = bl.hoehe - (Double.isNaN(sp0) ? 0 : sp0), weitZ = hAuge / Math.tan(Math.toRadians(Math.max(1, -bl.neigung)));
        return new double[] {bl.ost + weitZ * Math.sin(Math.toRadians(bl.richtung)), bl.nord + weitZ * Math.cos(Math.toRadians(bl.richtung))};
    }

    /**
     * Seit S7d6 (SF136 A, Befund L5d5 8): das Fahrprofil der Ankunft alle 10 s in docs/status/S7d_fahrprofil.csv, im Attest je
     * 250 m Weg eine Zeile und die Zeit je Abschnitt gegen den Plan (FAHRT_PLAN). Tiefe: Spiegel minus Gelände am Ort der Fähre;
     * Fr_h mit der Fahrt durchs Wasser.
     */
    static void fahrprofil(Buehne b, List<double[]> profil, List<String> phase, Flotte.Schwimmer fae, double tAnkunft) throws java.io.IOException {
        StringBuilder csv = new StringBuilder("utc;phase;weg_m;gerade_m;fahrt_grund_ms;fahrt_wasser_ms;tiefe_m;fr_h;hebel;ruder\n");
        java.util.Locale de = java.util.Locale.GERMANY;
        for (int i = 0; i < profil.size(); i++) {
            double[] p = profil.get(i);
            double frh = p[5] > 0.05 ? p[4] / Math.sqrt(9.81 * p[5]) : Double.NaN;
            csv.append(b.uhr().bei(p[0]).toString(), 11, 19).append(';').append(phase.get(i)).append(';')
                    .append(String.format(de, "%.0f;%.0f;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f%n", p[1], p[2], p[3], p[4], p[5], frh, p[6], p[7]));
        }
        java.nio.file.Files.writeString(Path.of("docs", "status", "S7d_fahrprofil.csv"), csv.toString());
        StringBuilder z = new StringBuilder();
        double naechste = PROFIL_AB, tVor = Double.NaN, sVor = Double.NaN;
        for (double[] p : profil) {
            if (Double.isNaN(p[1]) || p[1] > naechste + 1e-6) continue;
            double frh = p[5] > 0.05 ? p[4] / Math.sqrt(9.81 * p[5]) : Double.NaN;
            String abschnitt = Double.isNaN(tVor) ? "" : f(", Abschnitt %.0f s (Plan %.0f s)", p[0] - tVor, (sVor - p[1]) / Flotte.FAHRT_PLAN);
            z.append(f("%s%.0f m um %s: Grund %.2f, Wasser %.2f m/s, Tiefe %.2f m, Fr_h %.2f, Hebel %.2f, Ruder %.2f%s",
                    z.length() > 0 ? " / " : "", p[1], b.uhr().bei(p[0]).toString().substring(11, 19), p[3], p[4], p[5], frh, p[6], p[7], abschnitt));
            tVor = p[0];
            sVor = p[1];
            while (naechste >= p[1] - 1e-6) naechste -= 250;
        }
        A.info("Fahrprofil der Ankunft (seit S7d6, SF136 A; alle 10 s in docs/status/S7d_fahrprofil.csv), je 250 m Weg", z.length() > 0 ? z.toString() : "leer");
        if (!Double.isNaN(fae.festSeit) && !profil.isEmpty()) {
            double[] p0 = profil.get(0);
            A.info("  Fahrzeit", f("von %.0f m Weg bis fest %.0f s; der Plan rechnet %.0f s (bis %.0f m vor dem Anleger mit %.1f m/s, dann %.0f s fürs Anlegen); fest %+.0f s gegen den Plan",
                    p0[1], fae.festSeit - p0[0], Flotte.planZeit(p0[1]), Flotte.BREMS_AB, Flotte.FAHRT_PLAN, Flotte.ANLEGEN_S, fae.festSeit - tAnkunft));
        }
    }

    /** Seit S7d6: das Kielwasser am Ort des Kutters in double (größte Wellenzahl wie see_koerper). */
    static double kielAmKutter(Flotte fl, double[] ok, double[] out) {
        fl.kielwasser.am(ok[0], ok[1], Koerpersuche.KIEL_KMAX, out, fl.kielwasser.spuren());
        return out[0];
    }

    /** Seit S7d6: ob der Ort (relativ zu B) in der Kachel t der letzten Rechnung liegt. */
    static boolean inKachel(Kielwasserkarte kk, int t, double px, double py) {
        double x0 = kk.kachel[3 * t], y0 = kk.kachel[3 * t + 1], z = kk.kachel[3 * t + 2], kante = z * Kielwasserkarte.N;
        return px >= x0 && py >= y0 && px <= x0 + kante && py <= y0 + kante;
    }

    /**
     * Seit S7d3: die Ankunft der Fähre am 30.10. (ms, UTC) nach der Pegelreihe der Bühne: das Hochwasser zwischen 12 und 17 UTC
     * plus dem Versatz der Ankunft im Fahrplan (nach der Tide); NaN ohne.
     */
    static double ankunft(Buehne b, Flotte.Schwimmer fae) {
        com.dan.lauf.Pegelreihe pr = b.pegelreihe();
        if (pr == null || fae.angabe.fahrplan == null || !fae.angabe.nachTide) return Double.NaN;
        double von = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli(), bis = Instant.parse("2026-10-30T17:00:00Z").toEpochMilli();
        for (com.dan.lauf.Pegelreihe.Marke mk : pr.marken()) {
            if (mk.art() != com.dan.lauf.Pegelreihe.Art.HOCHWASSER) continue;
            double ms = pr.start.toEpochMilli() + 1000 * mk.t();
            if (ms >= von && ms <= bis) return ms + 1000 * fae.angabe.fahrplan[0];
        }
        return Double.NaN;
    }

    /** Seit S7d5: in einer Kachel nur den Schaum lassen (Höhe, K und Σ g null), wie die weite in see_kiel.comp. */
    static void nurSchaum(double[] k) {
        for (int c = 0; c + 3 < k.length; c += 4) { k[c] = 0; k[c + 2] = 0; k[c + 3] = 0; }
    }

    /** Wie kielAm in see_kiel_am.glsl (d = 1 m), aus den Kacheln in double (Orte relativ zu B). */
    static double amKachel(double[][] cpu, double[] kd, int n, double px, double py) {
        double[] r = new double[2], v = new double[2];
        for (int t = 0; t < 2; t++) {
            double qx = (px - kd[3 * t]) / kd[3 * t + 2] - 0.5, qy = (py - kd[3 * t + 1]) / kd[3 * t + 2] - 0.5;
            r[t] = -1;
            if (qx < 0 || qy < 0 || qx > n - 1 || qy > n - 1) continue;
            int ix = Math.min((int) Math.floor(qx), n - 2), iy = Math.min((int) Math.floor(qy), n - 2);
            double ax = qx - ix, ay = qy - iy;
            int b = iy * n + ix;
            double[] c = cpu[t];
            double v0 = c[4 * b] + ax * (c[4 * (b + 1)] - c[4 * b]), v1 = c[4 * (b + n)] + ax * (c[4 * (b + n + 1)] - c[4 * (b + n)]);
            v[t] = v0 + ay * (v1 - v0);
            r[t] = Math.min(Math.min(qx, qy), Math.min(n - 1 - qx, n - 1 - qy));
        }
        if (r[0] >= 8) return v[0];
        if (r[0] < 0) return r[1] < 0 ? 0 : v[1];
        double w = Math.max(0, Math.min(1, r[0] / 8));
        return v[1] + w * (v[0] - v[1]);
    }

    // ------------------------------------------------------------------ 4

    static void anwendung(File szenen, int breite, int hoehe) {
        A.teil("4 In der Anwendung (Prüfsteine 28, 29)");
        try {
            Szenen sz = Szenen.lies(szenen);
            Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            try {
                ankunft(b, sz, breite, hoehe);
                abfahrt(b, sz, breite, hoehe);                                  // seit S7d5 (SF129 A)
            } finally {
                b.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Seit S7e2 (Seegang Werkbuch 101, SF150 A): Teil 5, der Adler-Express in der Anwendung (ruhigsee, 30.10.2026): von 9 min
     * vor der Ankunft (13:20 Ortszeit, 12:20 UTC) bis 60 s nach dem Festmachen am Pierkopf, höchstens 15 min Szene. Pünktlich
     * (± 2 min [G]), nicht gekentert, 4 Leinen; dazu das Fahrprofil (Auskunft) und ein Bild am Anleger.
     */
    static void adler(File szenen, int breite, int hoehe) {
        A.teil("5 Adler-Express in der Anwendung (S7e2)");
        try {
            Szenen sz = Szenen.lies(szenen);
            Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            try {
                Flaechenwart w = b.wart();
                Flotte fl = oeffne(b, sz);
                Flotte.Schwimmer ad = null;
                for (Flotte.Schwimmer x : fl.schwimmer) if (x.angabe.name.equals("adler")) ad = x;
                if (ad == null) throw new IllegalStateException("kein Adler-Express im Szenario ruhigsee");
                double anMs = Instant.parse("2026-10-30T12:20:00Z").toEpochMilli(), tAn = (anMs - b.uhr().start.toEpochMilli()) / 1000.0;
                double t = tAn - 540, tBeginn = t, dt = 1.0 / 30;
                Ansicht ans = sz.ansicht("faehre");
                Blick bl = new Blick();
                ans.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                bl.groesse(breite, hoehe);
                b.standbild(bl, t);
                fl.neuSetzen();
                long uhrNs = 0, w0 = System.nanoTime();
                double vMax = 0, tauMax = 0, wMax = 0, tSicht = Double.NaN, tProfil = Double.NEGATIVE_INFINITY;
                String phase0 = null;
                StringBuilder profil = new StringBuilder();
                for (int i = 0; i < 30 * 900; i++) {
                    w.zeitraffer(1);
                    w.vorBild(bl, t, uhrNs);
                    b.flotteBild(t, 1, dt);
                    b.bildzeiten(bl, t, 1, true);
                    w.nachBild();
                    fl.warte();
                    String ph = ad.phase();
                    if (phase0 == null) phase0 = ph;
                    if (Double.isNaN(tSicht) && ph != null && !ph.startsWith("WEG")) tSicht = t;
                    double[] o = ad.koerper.lage(), v = ad.koerper.geschwindigkeit();
                    if (!ad.weg) {
                        vMax = Math.max(vMax, Math.hypot(v[0], v[1]));
                        if (ad.koerper.gleiten != null) { tauMax = Math.max(tauMax, ad.koerper.gleiten.tau); wMax = Math.max(wMax, ad.koerper.gleiten.anteil); }
                    }
                    if (t >= tProfil + 60 && !ad.weg) {
                        tProfil = t;
                        profil.append(f("%s%s %s, %.0f m vom Platz, %.1f m/s, Hebel %.2f, Trimm %.2f°", profil.length() > 0 ? "; " : "", b.uhr().bei(t).toString().substring(11, 19), ph,
                                Math.hypot(o[0] - ad.angabe.ost, o[1] - ad.angabe.nord), Math.hypot(v[0], v[1]), ad.koerper.hebel, ad.koerper.trimm()));
                    }
                    t += dt;
                    uhrNs += Math.round(dt * 1e9);
                    if (!Double.isNaN(ad.festSeit) && t > ad.festSeit + 60) break;
                    if (t - tBeginn > 900 || (System.nanoTime() - w0) / 1e9 > 1800) break;
                }
                A.info("Dauer", f("%.0f s Szene in %.0f s Wandzeit; zu Beginn „%s“, zu sehen ab %s UTC", t - tBeginn, (System.nanoTime() - w0) / 1e9, phase0,
                        Double.isNaN(tSicht) ? "–" : b.uhr().bei(tSicht).toString().substring(11, 19)));
                A.info("Fahrprofil (je Minute)", profil.toString());
                A.info("Fahrt und Gleiten", f("bis %.1f m/s (Plan 11 m/s, voll 12,3 m/s), Trimm bis %.2f°, Anteil des Gleitens bis %.2f", vMax, tauMax, wMax));
                double spaet = ad.festSeit - tAn;
                A.pruefe(!Double.isNaN(ad.festSeit) && Math.abs(spaet) <= 120, "Adler-Express pünktlich: fest gegen 13:20 Ortszeit (12:20 UTC)",
                        Double.isNaN(ad.festSeit) ? "nicht fest" : f("fest um %s UTC, %+.0f s gegen den Plan", b.uhr().bei(ad.festSeit).toString().substring(11, 19), spaet), "± 2 min [G]");
                A.pruefe(ad.kentern == null, "Adler-Express nicht gekentert", f("größte Krängung %.1f°%s", ad.kraengungMax, ad.kentern != null ? "; GEKENTERT: " + ad.kentern : ""), "≤ " + (int) Flotte.KENTERN + "° [G]");
                A.pruefe(ad.leinen() == 4, "Leinen des Adler-Express am Pierkopf", f("%d Leinen", ad.leinen()), "genau 4");
                javax.imageio.ImageIO.write(SeegangBilder.bild(b.standbild(bl, t), breite, hoehe), "png", Path.of("docs", "status", "S7e_adler_angelegt.png").toFile());
                A.info("Angelegt", f("Bild docs/status/S7e_adler_angelegt.png um %s UTC (Ansicht faehre)", b.uhr().bei(t).toString().substring(11, 19)));
            } finally {
                b.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Hilligenlei und Kutter der Flotte der Bühne (oder Fehler). */
    static Flotte.Schwimmer[] faehreUndKutter(Flotte fl) {
        Flotte.Schwimmer fae = null, ku = null;
        for (Flotte.Schwimmer x : fl.schwimmer) {
            if (x.angabe.name.equals("hilligenlei")) fae = x;
            if (x.angabe.name.equals("kutter")) ku = x;
        }
        if (fae == null || ku == null) throw new IllegalStateException("Hilligenlei oder Kutter fehlt");
        return new Flotte.Schwimmer[] {fae, ku};
    }

    /** Öffnet ruhigsee neu (Fläche, Körperfläche und Flotte neu) und gibt die Flotte. */
    static Flotte oeffne(Buehne b, Szenen sz) throws Exception {
        Szenario s = sz.szenario("ruhigsee");
        SchwimmerCheck.pegel(b, sz);                                            // seit S7d2: Hochwasser für den Fahrplan nach der Tide
        b.oeffne(s);
        b.wasserbild().seegang = true;
        Flotte fl = b.flotte();
        if (fl == null) throw new IllegalStateException("keine Flotte");
        return fl;
    }

    /**
     * 4a: die Ankunft. Seit S7d5 (SF130 A) 520 s vor der Ankunft nach der Pegelreihe (die Fähre plant mit 3,6 m/s und 100 s
     * fürs Anlegen, sie ist dann rund 1,5 km draußen): Bild aus der Ansicht {@code kielwasser}, wenn sie 40 m über deren Punkt
     * hinaus ist; Kosten des Kielwassers je Bild (≤ 0,2 ms [G]); pünktlich (fest höchstens 2 min nach dem Plan [G]); Verholen
     * (180 s nach dem Festmachen ≤ 1,5 m, ≤ 3° [G]).
     */
    static void ankunft(Buehne b, Szenen sz, int breite, int hoehe) throws Exception {
        Flaechenwart w = b.wart();
        Flotte fl = oeffne(b, sz);
        Flotte.Schwimmer[] fk = faehreUndKutter(fl);
        Flotte.Schwimmer fae = fk[0];
        Ansicht ans = sz.ansicht("kielwasser");
        double ankunftMs = ankunft(b, fae);
        // seit S7d6 (SF136 A): Beginn, wenn die Fähre nach dem Plan 2,5 km vor dem Anleger ist
        double vorlauf = Flotte.planZeit(PROFIL_AB);                          // seit S7d7 mit BREMS_AB
        A.info("Ankunft nach der Pegelreihe", Double.isNaN(ankunftMs) ? "kein Hochwasser gefunden" : Instant.ofEpochMilli((long) ankunftMs).toString().substring(0, 19).replace('T', ' ')
                + f(" UTC; Beginn %.0f s vorher (nach dem Plan %.1f km vor dem Anleger)", vorlauf, PROFIL_AB / 1000));
        if (Double.isNaN(ankunftMs)) throw new IllegalStateException("keine Ankunft der Fähre");
        double t = (ankunftMs - 1000 * vorlauf - b.uhr().start.toEpochMilli()) / 1000.0, tAnkunft = (ankunftMs - b.uhr().start.toEpochMilli()) / 1000.0;
        Blick bl = new Blick();
        ans.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
        bl.groesse(breite, hoehe);
        b.standbild(bl, t);
        fl.neuSetzen();
        // der Punkt, auf den die Ansicht schaut (Mitte des Bilds auf dem Wasser)
        double[] zp = punktDerAnsicht(b, bl);
        double zx = zp[0], zy = zp[1];
        double dt = 1.0 / 30;
        List<double[]> profil = new ArrayList<>();                            // seit S7d6: t, Weg, gerade, v Grund, v Wasser, Tiefe, Hebel, Ruder
        List<String> profilPhase = new ArrayList<>();
        double tProfil = Double.NEGATIVE_INFINITY;
        long uhrNs = 0;
        int bereit = -1;
        Map<String, Double> summe = new java.util.TreeMap<>();
        int nSumme = 0;
        double dMinZ = Double.POSITIVE_INFINITY;
        String bildA = null, bildPhase = null, bildZeit = null, phase0 = null;
        long w0 = System.nanoTime();
        double tBeginn = t;
        for (int i = 0; i < 30 * 1800; i++) {                                 // bis 180 s nach dem Festmachen; seit S7d7 (SF139 A) höchstens 30 min Szene
            w.zeitraffer(1);
            w.vorBild(bl, t, uhrNs);
            b.flotteBild(t, 1, dt);
            Buehne.Bildzeiten z = b.bildzeiten(bl, t, 1, true);
            w.nachBild();
            if (bereit < 0 && w.bereit() && !w.wechselt() && (w.koerpermitte() == null || w.koerperBereit())) bereit = i;
            fl.warte();
            if (bereit >= 0 && fl.kielwasser.inFahrt().size() > 0) {
                for (Map.Entry<String, Double> e2 : z.abschnitte().entrySet()) summe.merge(e2.getKey(), e2.getValue(), Double::sum);
                nSumme++;
            }
            double[] of = fae.koerper.lage();
            // seit S7d4 (SF124 A): das Bild, wenn die Fähre in Fahrt 40 m über den Punkt der Ansicht hinaus ist
            double dz = Math.hypot(of[0] - zx, of[1] - zy);
            if (bereit >= 0) dMinZ = Math.min(dMinZ, dz);
            if (bildA == null && bereit >= 0 && dMinZ < 120 && dz >= dMinZ + 40 && fl.kielwasser.inFahrt().size() > 0) {
                bildA = "S7d_ruhigsee_kielwasser.png";
                javax.imageio.ImageIO.write(SeegangBilder.bild(b.standbild(bl, t), breite, hoehe), "png", Path.of("docs", "status", bildA).toFile());
                double vorAnl = Math.hypot(of[0] - fae.angabe.festZahlen[0], of[1] - fae.angabe.festZahlen[1]);
                bildPhase = fae.phase() + f(", %.0f m vor dem Anleger, %.0f m hinter dem Punkt der Ansicht, Fahrt fürs Kielwasser %.1f m/s", vorAnl, dz, fae.kielFahrt);
                bildZeit = b.uhr().bei(t).toString().substring(11, 19);
            }
            if (phase0 == null && bereit >= 0) phase0 = fae.phase() + f(", %.0f m vor dem Anleger", Math.hypot(of[0] - fae.angabe.festZahlen[0], of[1] - fae.angabe.festZahlen[1]));
            // seit S7d6 (SF136 A): das Fahrprofil alle 10 s, bis sie fest ist
            if (t >= tProfil + 10 && Double.isNaN(fae.festSeit)) {
                tProfil = t;
                double[] vg = fae.koerper.geschwindigkeit();
                double sp = b.spiegel(of[0], of[1]), gr = b.gelaendeHoehe(of[0], of[1]);
                profil.add(new double[] {t, fae.weg(of[0], of[1]), Math.hypot(of[0] - fae.angabe.festZahlen[0], of[1] - fae.angabe.festZahlen[1]),
                        Math.hypot(vg[0], vg[1]), fae.kielFahrt, sp - gr, fae.koerper.hebel, fae.koerper.ruder});
                profilPhase.add(fae.phase());
            }
            t += dt;
            uhrNs += Math.round(dt * 1e9);
            boolean fest = fae.phase() != null && fae.phase().contains("fest") && !Double.isNaN(fae.festSeit);
            if (fest && t > fae.festSeit + 180 && bildA != null) break;
            if (t - tBeginn > 1800 || (System.nanoTime() - w0) / 1e9 > 3000) break;   // seit S7d7: 30 min Szene, 50 min Wandzeit
        }
        A.info("Dauer der Ankunft (seit S7d7)", f("%.0f s Szene in %.0f s Wandzeit", t - tBeginn, (System.nanoTime() - w0) / 1e9));
        fahrprofil(b, profil, profilPhase, fae, tAnkunft);
        // seit S7d4 (SF123 A): die Kosten einzeln
        double kosten = 0;
        StringBuilder einzeln = new StringBuilder();
        for (Map.Entry<String, Double> e2 : summe.entrySet())
            if (e2.getKey().startsWith("Kielwasser") || e2.getKey().startsWith("see_kiel")) {
                double v = e2.getValue() / Math.max(1, nSumme);
                kosten += v;
                einzeln.append(f("%s%s %.3f ms", einzeln.length() > 0 ? ", " : "", e2.getKey(), v));
            }
        A.pruefe(nSumme > 0 && kosten <= 0.2, "Kosten der Kacheln und ihres Einrechnens je Bild (see_kiel, see_kiel_dazu)", f("%.3f ms im Mittel über %d Bilder mit Fähre in Fahrt", kosten, nSumme), "≤ 0,2 ms [G]");
        A.info("  einzeln (seit S7d4)", einzeln.toString());
        // seit S7d5 (SF130 A): pünktlich
        double spaet = fae.festSeit - tAnkunft;
        A.pruefe(!Double.isNaN(fae.festSeit) && Math.abs(spaet) <= 120, "Fähre pünktlich (seit S7d5): fest gegen die Ankunft des Fahrplans",
                Double.isNaN(fae.festSeit) ? "nicht fest" : f("fest um %s UTC, %+.0f s gegen den Plan; zu Beginn „%s“", b.uhr().bei(fae.festSeit).toString().substring(11, 19), spaet, phase0), "± 2 min [G]");
        for (Flotte.Schwimmer x : fl.schwimmer)
            if (x.kiel != null) A.pruefe(x.kentern == null, "Kein Schiff gekentert (seit S7d2), Ankunft: " + x.angabe.name,
                    f("größte Krängung %.1f°%s", x.kraengungMax, x.kentern != null ? "; GEKENTERT: " + x.kentern : ""), "≤ " + (int) Flotte.KENTERN + "° [G]");
        A.info("Sichtprüfung", bildA != null ? "Bild docs/status/" + bildA + " um " + bildZeit + " UTC (" + bildPhase + ")" : f("kein Bild: die Fähre kam dem Punkt der Ansicht nur %.0f m nahe", dMinZ));
        // seit S7d4 (SF125 A): 180 s nach dem Festmachen am Platz
        double[] of = fae.koerper.lage();
        double ab = Math.hypot(of[0] - fae.angabe.ost, of[1] - fae.angabe.nord), dk = Math.abs(((fae.koerper.kurs() - fae.angabe.kurs) % 360 + 540) % 360 - 180);
        boolean verholt = !Double.isNaN(fae.festSeit) && t >= fae.festSeit + 180;
        A.pruefe(verholt && ab <= 1.5 && dk <= 3, "Verholen (seit S7d4): die Fähre 180 s nach dem Festmachen an ihrem Platz",
                verholt ? f("%.2f m vom Platz der Szene, Kurs %.1f° (Szene %.0f°, %.1f° daneben); %d Leinen fest; fest %+.0f s gegen den Plan", ab, fae.koerper.kurs(), fae.angabe.kurs, dk,
                        fae.leinen(), fae.festSeit - tAnkunft) : "nicht 180 s fest (Lauf zu kurz)", "≤ 1,5 m, ≤ 3° [G]");
        // seit S7d8 (SF142 A, Befund L5d7 2): fest vor der Zeit des Plans legte die Flotte bis S7d7 in jedem Schritt neue Leinen an
        A.pruefe(fae.leinen() == 4, "Leinen der Fähre am Anleger (seit S7d8)", f("%d Leinen", fae.leinen()), "genau 4");
        Ansicht af = sz.ansicht("faehre");
        Blick bf = new Blick();
        af.setze(bf, b.gelaende, b.utm(), b.uhr().bei(t));
        bf.groesse(breite, hoehe);
        javax.imageio.ImageIO.write(SeegangBilder.bild(b.standbild(bf, t), breite, hoehe), "png", Path.of("docs", "status", "S7d_ruhigsee_angelegt.png").toFile());
        A.info("Angelegt", f("Bild docs/status/S7d_ruhigsee_angelegt.png um %s UTC", b.uhr().bei(t).toString().substring(11, 19)));
        A.info("Vorschau", bildZeit != null ? f("kielwasser: Bild um %s UTC; Anfang der Vorschau 10 s vorher", bildZeit) : "–");
    }

    /**
     * Seit S7d8 (Seegang Werkbuch 97, SF142 A, SF144 A): 4a prüft, dass genau 4 Leinen fest sind; 4b den Abstand der Fähre zum
     * Kutter bei der Abfahrt (≥ 50 m [G]) und schreibt die Stufen der Abfahrt auf (rückwärts, dreht, in Fahrt).
     * <p>
     * 4b, seit S7d5 (SF129 A, Befund L5d4 5): die Abfahrt (Hochwasser + 1:30), Prüfstein 29 aus der Ansicht {@code kutter}
     * (die nahe Kachel des Kielwassers liegt dann um den Kutter): 120 s vorher beginnen, die Reihe des Kutters aufzeichnen und
     * nach dem Lauf auswerten – vorher, solange die Fähre liegt; danach 240 s ab dem Durchgang, mit dem Kielwasser am Kutter in
     * double. Auskunft, kein Prüfstein mit Grenze.
     */
    static void abfahrt(Buehne b, Szenen sz, int breite, int hoehe) throws Exception {
        Flaechenwart w = b.wart();
        Flotte fl = oeffne(b, sz);
        Flotte.Schwimmer[] fk = faehreUndKutter(fl);
        Flotte.Schwimmer fae = fk[0], ku = fk[1];
        double ankunftMs = ankunft(b, fae);
        double abfahrtMs = ankunftMs + 1000 * (fae.angabe.fahrplan[1] - fae.angabe.fahrplan[0]);
        A.info("Abfahrt nach der Pegelreihe", Instant.ofEpochMilli((long) abfahrtMs).toString().substring(0, 19).replace('T', ' ') + " UTC; Beginn 120 s vorher; Ansicht kutter");
        double t = (abfahrtMs - 120e3 - b.uhr().start.toEpochMilli()) / 1000.0, tAb = (abfahrtMs - b.uhr().start.toEpochMilli()) / 1000.0;
        Ansicht ans = sz.ansicht("kutter");
        Blick bl = new Blick();
        ans.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
        bl.groesse(breite, hoehe);
        double[] wa = b.wasserUndSeegang(bl.ost, bl.nord);
        Ansicht.hebe(bl, wa[0], wa[1], ans.ueberWasser);
        b.standbild(bl, t);
        fl.neuSetzen();
        double dt = 1.0 / 30;
        long uhrNs = 0;
        int bereit = -1;
        List<double[]> reihe = new ArrayList<>();                              // t, Abstand Fähre–Kutter, Hub, Krängung, Kielwasser am Kutter (double)
        double naechst = Double.POSITIVE_INFINITY, tNaechst = Double.NaN, vMax = 0;
        double[] kwOut = new double[3];
        // seit S7d6 (SF135 A): Bilder des Schwells 90, 150 und 210 s nach dem Durchgang (Ansicht kutter); danach die Ansicht
        // kielwasser und dort ein Bild, wenn die Fähre 40 m über ihren Punkt hinaus ist. Die Reihe des Kutters läuft weiter:
        // Seegang (Körperfläche) und Kielwasser (direkt) an ihm hängen nicht mehr am Auge.
        double[] schwellS = {90, 150, 210};
        List<String> bilderB = new ArrayList<>();
        int naechstesBild = 0;
        boolean zweite = false;
        int bereit2 = -1;
        double zx = Double.NaN, zy = Double.NaN, dMinZ = Double.POSITIVE_INFINITY;
        String bildAb = null, bildAbText = null;
        // seit S7d8 (SF144 A): wann die Stufen der Abfahrt beginnen (rückwärts, dreht, in Fahrt), s nach der Abfahrt
        Map<String, Double> stufen = new java.util.LinkedHashMap<>();
        double[] drehOrt = null;
        long w0 = System.nanoTime();
        for (int i = 0; i < 30 * 1500; i++) {                                 // bis 240 s nach dem Durchgang und dem Bild, höchstens 25 min (seit S7d8)
            w.zeitraffer(1);
            w.vorBild(bl, t, uhrNs);
            b.flotteBild(t, 1, dt);
            b.bildzeiten(bl, t, 1, true);
            w.nachBild();
            if (bereit < 0 && w.bereit() && !w.wechselt() && (w.koerpermitte() == null || w.koerperBereit())) bereit = i;
            if (zweite && bereit2 < 0 && w.bereit() && !w.wechselt()) bereit2 = i;
            fl.warte();
            double[] of = fae.koerper.lage(), ok = ku.koerper.lage();
            double d = Math.hypot(of[0] - ok[0], of[1] - ok[1]);
            if (bereit >= 0 && t > tAb && d < naechst) { naechst = d; tNaechst = t; }
            String phs = fae.phase();
            if (phs != null && phs.startsWith("FAEHRT_AB")) {
                String st = phs.contains("rückwärts") ? "rückwärts" : phs.contains("dreht") ? "dreht" : "in Fahrt";
                if (!stufen.containsKey(st)) {
                    stufen.put(st, t - tAb);
                    if (st.equals("dreht")) drehOrt = new double[] {Math.hypot(of[0] - fae.angabe.festZahlen[0], of[1] - fae.angabe.festZahlen[1]), fae.koerper.kurs()};
                    if (st.equals("in Fahrt") && drehOrt != null) drehOrt = new double[] {drehOrt[0], drehOrt[1], fae.koerper.kurs()};
                }
            }
            if (t > tAb) vMax = Math.max(vMax, fae.kielFahrt);
            double kw = Double.NaN;
            if (bereit >= 0 && i % 10 == 0) {
                fl.kielwasser.am(ok[0], ok[1], Koerpersuche.KIEL_KMAX, kwOut, fl.kielwasser.spuren());
                kw = kwOut[0];
            }
            if (bereit >= 0) reihe.add(new double[] {t, d, ok[2], ku.koerper.kraengung(), kw});
            if (!zweite && naechstesBild < schwellS.length && !Double.isNaN(tNaechst) && t > tNaechst + schwellS[naechstesBild]) {
                String nb = f("S7d_ruhigsee_schwell_%.0fs.png", schwellS[naechstesBild]);
                javax.imageio.ImageIO.write(SeegangBilder.bild(b.standbild(bl, t), breite, hoehe), "png", Path.of("docs", "status", nb).toFile());
                bilderB.add(nb + f(" (+%.0f s, Kielwasser am Kutter %.3f m)", schwellS[naechstesBild], kielAmKutter(fl, ok, kwOut)));
                naechstesBild++;
                if (naechstesBild == schwellS.length) {                         // die Ansicht kielwasser
                    zweite = true;
                    Ansicht ak = sz.ansicht("kielwasser");
                    ak.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                    bl.groesse(breite, hoehe);
                    double[] zp = punktDerAnsicht(b, bl);
                    zx = zp[0];
                    zy = zp[1];
                }
            }
            if (zweite && bildAb == null) {
                double dz = Math.hypot(of[0] - zx, of[1] - zy);
                dMinZ = Math.min(dMinZ, dz);                                    // auch solange die Fläche noch entsteht
                if (bereit2 >= 0 && dMinZ < 120 && dz >= dMinZ + 40 && dz <= dMinZ + 160 && fae.kielFahrt > 0) {
                    bildAb = "S7d_ruhigsee_kielwasser_ab.png";
                    javax.imageio.ImageIO.write(SeegangBilder.bild(b.standbild(bl, t), breite, hoehe), "png", Path.of("docs", "status", bildAb).toFile());
                    double[] oa = {bl.ost, bl.nord};
                    bildAbText = f("um %s UTC, %.0f m hinter dem Punkt der Ansicht, %.0f m vom Auge, Fahrt fürs Kielwasser %.1f m/s",
                            b.uhr().bei(t).toString().substring(11, 19), dz, Math.hypot(of[0] - oa[0], of[1] - oa[1]), fae.kielFahrt);
                }
            }
            t += dt;
            uhrNs += Math.round(dt * 1e9);
            boolean schwellFertig = !Double.isNaN(tNaechst) && d > naechst + 100 && t > tNaechst + 240;
            boolean abFertig = bildAb != null || zweite && dMinZ < 1e9 && Math.hypot(of[0] - zx, of[1] - zy) > dMinZ + 400;
            if (schwellFertig && abFertig) break;
            if ((System.nanoTime() - w0) / 1e9 > 1500) break;                  // seit S7d8 1500 s (vorher 1200): das Drehen dauert
        }
        double zm = 0, rm = 0, zq = 0, rq = 0, z1 = 0, r1 = 0, kwMax = 0, tKw = Double.NaN;
        int n0 = 0, n1 = 0;
        for (double[] r : reihe) if (r[0] < tAb) { zm += r[2]; rm += r[3]; zq += r[2] * r[2]; rq += r[3] * r[3]; n0++; }
        if (n0 > 0) { zm /= n0; rm /= n0; }
        double zs = n0 > 0 ? Math.sqrt(Math.max(0, zq / n0 - zm * zm)) : Double.NaN, rs = n0 > 0 ? Math.sqrt(Math.max(0, rq / n0 - rm * rm)) : Double.NaN;
        for (double[] r : reihe)
            if (!Double.isNaN(tNaechst) && r[0] >= tNaechst && r[0] <= tNaechst + 240) {
                n1++;
                if (n0 > 0) { z1 = Math.max(z1, Math.abs(r[2] - zm)); r1 = Math.max(r1, Math.abs(r[3] - rm)); }
                if (!Double.isNaN(r[4]) && Math.abs(r[4]) > kwMax) { kwMax = Math.abs(r[4]); tKw = r[0]; }
            }
        A.info("Fähre bei der Abfahrt", f("dem Kutter am nächsten %.0f m um %s UTC (%.0f s nach der Abfahrt); Fahrt fürs Kielwasser bis %.1f m/s; zuletzt „%s“",
                naechst, Double.isNaN(tNaechst) ? "–" : b.uhr().bei(tNaechst).toString().substring(11, 19), tNaechst - tAb, vMax, fae.phase()));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e2 : stufen.entrySet()) sb.append(sb.length() > 0 ? ", " : "").append(e2.getKey()).append(f(" ab %+.0f s", e2.getValue()));
        if (drehOrt != null) sb.append(f("; gedreht %.0f m vor dem Anleger von %.0f°", drehOrt[0], drehOrt[1])).append(drehOrt.length > 2 ? f(" auf %.0f°", drehOrt[2]) : " (nicht fertig)");
        A.info("Stufen der Abfahrt (seit S7d8, SF144 A)", sb.length() > 0 ? sb.toString() : "keine");
        // seit S7d8 (SF144 A, Befund L5d7 4: im Bogen 22 m am Kutter vorbei)
        A.pruefe(!Double.isNaN(tNaechst) && naechst >= 50, "Abfahrt (seit S7d8): Abstand der Fähre zum Kutter, Mitte zu Mitte",
                Double.isNaN(tNaechst) ? "nicht gemessen" : f("%.0f m, %.0f s nach der Abfahrt", naechst, tNaechst - tAb), "≥ 50 m [G]");
        A.info("Kutter im Schwell der Fähre (Prüfstein 29, seit S7d5 bei der Abfahrt)", n0 > 0 && n1 > 0
                ? f("vorher, solange sie liegt (%d Bilder): Hub RMS %.3f m, Rollen RMS %.2f°; in 240 s ab dem Durchgang (%d Bilder): Hub bis %.3f m, Rollen bis %.2f° vom Mittel; Kielwasser am Kutter (double) bis %.3f m, %.0f s nach dem Durchgang",
                n0, zs, rs, n1, z1, r1, kwMax, tKw - tNaechst)
                : f("nicht messbar: %d Bilder vorher, %d nachher", n0, n1));
        for (Flotte.Schwimmer x : fl.schwimmer)
            if (x.kiel != null) A.pruefe(x.kentern == null, "Kein Schiff gekentert (seit S7d2), Abfahrt: " + x.angabe.name,
                    f("größte Krängung %.1f°%s", x.kraengungMax, x.kentern != null ? "; GEKENTERT: " + x.kentern : ""), "≤ " + (int) Flotte.KENTERN + "° [G]");
        A.info("Sichtprüfung, Abfahrt (seit S7d6, SF135 A)", bilderB.isEmpty() ? "kein Bild des Schwells" : "Ansicht kutter: docs/status/" + String.join(", ", bilderB));
        A.info("  Kielwasser bei der Abfahrt", bildAb != null ? "Bild docs/status/" + bildAb + ", Ansicht kielwasser, " + bildAbText
                : f("kein Bild: die Fähre kam dem Punkt der Ansicht kielwasser nur %.0f m nahe%s", dMinZ, bereit2 < 0 ? " (Fläche nicht bereit)" : ""));
    }
}
