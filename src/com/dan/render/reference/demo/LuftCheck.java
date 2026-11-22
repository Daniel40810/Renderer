package com.dan.render.reference.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Scattering;
import com.dan.core.szene.Wasseroptik;
import com.dan.core.szene.spektrum.Spektrum;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Pfadverfolger;
import com.dan.render.reference.Referenz;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static com.dan.core.pruef.Attest.f;

/**
 * Messung der Luft (P7e, Werkbuch HalligHooge Teil A, 12.8, F57 A): der Himmel der Referenz gegen den des Echtzeitbilds
 * ({@link Scattering}, Zeile für Zeile wie {@code luft.glsl}) an denselben Richtungen, zerlegt in
 * <ul>
 * <li><b>Einfachstreuung:</b> Referenz nur mit dem ersten Stoß gegen {@link Scattering#radianceEinfach} (dieselbe Luft,
 * dieselben Phasenfunktionen, drei Wellenlängen der Kanäle) – misst den Strahlengang;</li>
 * <li><b>Mehrfachstreuung:</b> Referenz ganz gegen Einfach + Ψ nach Hillaire;</li>
 * <li><b>Spektrum:</b> Referenz spektral (360–830 nm, sRGB) gegen dieselbe in den drei Kanälen.</li>
 * </ul>
 * Boden als Kugel mit Albedo 0,08 (wie die Tabellen des Echtzeitbilds), kein Wasser, Auge 2 m über dem Meer. Sicht 30 km mit
 * der Sonne 40°, 15°, 5°, 0° und −3°, Sicht 10 km mit 40°, 15° und 5° (F58 A).
 * Aufruf: {@code LuftCheck [--pfade n] [--nur 30|10] [--sonnen 40;15;5;0;-3] [--ohne-attest]}. Attest {@code docs/status/P7_Luft.md}.
 * <p>
 * Seit P10d (Messung d): {@code --nach-unten [--pfade n]} – die Luftperspektive nach unten, siehe {@link #nachUnten}.
 */
public final class LuftCheck {
    private static final Attest A = new Attest("com.dan.render.reference", Referenz.VERSION);

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        int pfade = 200_000;
        String nur = null;
        double[] s30 = {40, 15, 5, 0, -3};
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--pfade")) pfade = Integer.parseInt(args[i + 1]);
            if (args[i].equals("--nur")) nur = args[i + 1];
            if (args[i].equals("--sonnen")) s30 = java.util.Arrays.stream(args[i + 1].split(";")).mapToDouble(Double::parseDouble).toArray();
        }
        System.out.println("LuftCheck – Himmel der Referenz gegen die Streuordnungen (Werkbuch HalligHooge Teil A, P7e)");
        if (List.of(args).contains("--nach-unten")) {
            int pf = pfade == 200_000 ? 100_000 : pfade;
            try {
                nachUnten(pf);
            } catch (Throwable e) {
                A.fehler(e);
            }
            if (attest) {
                Path p = A.schreibe(Path.of("docs", "status", "P10_LuftNachUnten.md"), "P10d – LuftCheck --nach-unten (Messung d)");
                if (p != null) System.out.println("\nAttest: " + p);
            }
            System.out.println();
            System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
            System.exit(A.verstoesse() == 0 ? 0 : 1);
        }
        try {
            if (nur == null || nur.equals("30")) messe(30, s30, pfade);
            if (nur == null || nur.equals("10")) messe(10, new double[] {40, 15, 5}, pfade);
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P7_Luft.md"), "P7e – LuftCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Mittel und σ des Mittels je Kanal (sRGB linear, cd/m²) für n Pfade in Richtung v. */
    static double[] ref(Referenz ref, double[] v, int n, long saat) {
        int nt = 16;
        double[][] r = IntStream.range(0, nt).parallel().mapToObj(t -> {
            Pfadverfolger pv = ref.verfolger(saat * 101 + t);
            double[] acc = new double[6], s = new double[3];
            for (int i = 0; i < n / nt; i++) {
                pv.neueWellen();
                java.util.Arrays.fill(pv.wl.nacht, 0);                      // ohne Nachthimmel (Scattering rechnet ihn nicht)
                double[] L = pv.verfolge(new double[3], v);
                java.util.Arrays.fill(s, 0);
                pv.wl.sammle(L, s);
                double[] rgb = ref.spektral ? Spektrum.nachRgb(s) : s;
                for (int k = 0; k < 3; k++) {
                    acc[k] += rgb[k];
                    acc[3 + k] += rgb[k] * rgb[k];
                }
            }
            return acc;
        }).toArray(double[][]::new);
        int nn = (n / nt) * nt;
        double[] e = new double[6];
        for (int k = 0; k < 3; k++) {
            double a = 0, b = 0;
            for (double[] x : r) {
                a += x[k];
                b += x[3 + k];
            }
            e[k] = a / nn;
            e[3 + k] = Math.sqrt(Math.max(0, b / nn - e[k] * e[k]) / nn);
        }
        return e;
    }

    static Referenz referenz(Atmosphere luft, double[] l, boolean spektral, boolean einfach) {
        Ebenen sz = new Ebenen(-2, Double.NaN, new double[] {0.08, 0.08, 0.08}, 2);
        sz.kugel = true;                                                    // seit P7e: wie das Echtzeitbild (Dämmerung)
        Referenz ref = new Referenz(sz, luft, new Wasseroptik(), 0);
        ref.spektral = spektral;
        ref.bahnen = 16;
        ref.lichter.clear();
        ref.lichter.add(new Licht(l, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
        ref.einfach = einfach;
        return ref;
    }

    // ------------------------------------------------------------------ P10d, Messung d

    /**
     * Messung d (P10d, Prüfstein 5): Blick aus 300 m um 30° und 5° nach unten auf eine Kugel mit Lambert-Boden, Licht 20°
     * hoch („Tag“) und 35° hoch (wie der Mond in Ansicht 3; in drei Kanälen ist es dasselbe Licht, nur höher). Die Referenz
     * (drei Kanäle, Sicht 30 km) mit Bodenalbedo 0,23, 0,06 und 0 (nur die Luft) gegen das Echtzeitbild auf dem Prozessor:
     * Luftperspektive {@link Scattering#strecke} (8 Schritte wie das Bild, J einmal in der Mitte) plus Durchlässigkeit mal
     * Boden (Albedo / π mal direkte Sonne und Himmelslicht aus {@link Scattering#radiance} über die Halbkugel); je mit
     * den Tabellen bei Albedo 0,23 (wie das Bild) und bei der Albedo des Bodens, und mit 256 Schritten. Auskunft: Wie viel
     * der Abweichung tragen die Albedo der Tabellen und die Schritte?
     */
    static void nachUnten(int n) {
        A.teil("Messung d: Luftperspektive nach unten (P10d, Prüfstein 5)");
        double auge = 300, R = Atmosphere.BOTTOM, r = R + auge;
        double[] e = {Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX};
        java.util.Map<Double, Scattering> tab = new java.util.HashMap<>();
        for (double a : new double[] {0.23, 0.06, 0}) tab.put(a, new Scattering(Atmosphere.forVisibility(30, a)));
        A.info("Luft", Atmosphere.forVisibility(30, 0.23) + f(", Auge %.0f m, %d Pfade je Fall", auge, n));
        double hoechst = 0;
        for (double hl : new double[] {20, 35}) {
            double al = Math.toRadians(hl);
            double[] l = {0, Math.sin(al), -Math.cos(al)};
            for (double eb : new double[] {-30, -5}) {
                double er = Math.toRadians(eb), az = Math.toRadians(120);
                double[] v = {Math.cos(er) * Math.sin(az), Math.sin(er), -Math.cos(er) * Math.cos(az)};
                double mu = v[1], weg = -r * mu - Math.sqrt(Math.max(0, r * r * mu * mu - (r * r - R * R)));
                StringBuilder b = new StringBuilder();
                for (double a : new double[] {0.23, 0.06, 0}) {
                    Atmosphere luft = Atmosphere.forVisibility(30, a);
                    Ebenen sz = new Ebenen(-auge, Double.NaN, new double[] {a, a, a}, auge);
                    sz.kugel = true;
                    Referenz ref = new Referenz(sz, luft, new Wasseroptik(), 0);
                    ref.spektral = false;
                    ref.bahnen = 16;
                    ref.lichter.clear();
                    ref.lichter.add(new Licht(l, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
                    long t0 = System.nanoTime();
                    double[] q = ref(ref, v, n, (long) (hl * 1000 + eb * 10 + a * 100) + 77_000);
                    double sek = (System.nanoTime() - t0) / 1e9;
                    double[] rt8 = echt(tab.get(0.23), r, v, l, e, weg, 8, a), rta = echt(tab.get(a), r, v, l, e, weg, 8, a), rt256 = echt(tab.get(a), r, v, l, e, weg, 256, a);
                    double yq = y(q), y8 = y(rt8), ya = y(rta), y256 = y(rt256);
                    double alb = (y8 - ya) / (y8 - yq), sch = (ya - y256) / (y8 - yq);
                    hoechst = Math.max(hoechst, Math.abs(y8 / yq - 1));
                    b.append(f("Albedo %.2f: Referenz %.4g ± %.1f %% (%.0f s); Echtzeit (Tabellen 0,23, 8 Schritte) %.4g = %.2f der Referenz (R %.2f G %.2f B %.2f), davon Luft %.4g und Boden %.4g; "
                                    + "mit Tabellen der Albedo des Bodens %.2f, mit 256 Schritten %.2f; von der Abweichung trägt die Albedo der Tabellen %.0f %%, die Schritte %.0f %%; ",
                            a, yq, 100 * y(new double[] {q[3], q[4], q[5]}) / yq, sek, y8, y8 / yq, rt8[0] / q[0], rt8[1] / q[1], rt8[2] / q[2], y(new double[] {rt8[3], rt8[4], rt8[5]}),
                            y8 - y(new double[] {rt8[3], rt8[4], rt8[5]}), ya / yq, y256 / yq, Math.abs(y8 - yq) > 3 * y(new double[] {q[3], q[4], q[5]}) ? 100 * alb : Double.NaN,
                            Math.abs(y8 - yq) > 3 * y(new double[] {q[3], q[4], q[5]}) ? 100 * sch : Double.NaN));
                }
                A.info(f("Licht %.0f° hoch, Blick %.0f° nach unten (Weg %.0f m)", hl, -eb, weg), b.toString());
            }
        }
        A.info("Messung d im Ganzen", f("größte Abweichung des Echtzeitbilds (wie es ist) von der Referenz %.0f %% (Leuchtdichte); „trägt“ steht leer, wo die Abweichung unter 3 σ liegt", 100 * hoechst));
    }

    /**
     * Das Echtzeitbild auf dem Prozessor für den Blick v aus r bis zum Boden (weg): {L rgb, davon Luft rgb}. Boden nach
     * Lambert mit Albedo a: direkte Sonne (Durchlässigkeit aus der Tabelle in 0,5 m) und Himmelslicht (Scattering.radiance
     * in 0,5 m über 16 × 8 Richtungen, nach cos θ gewichtet), wie gelaende.frag ohne Schatten.
     */
    static double[] echt(Scattering sc, double r, double[] v, double[] l, double[] e, double weg, int schritte, double a) {
        double[][] st = sc.strecke(r, v, l, e, null, null, weg, schritte);
        double R = Atmosphere.BOTTOM;
        double[] t = sc.transmittance(R + 0.5, l[1]), es = new double[3];
        for (int i = 0; i < 16; i++)
            for (int j = 0; j < 8; j++) {
                double cz = Math.sqrt((j + 0.5) / 8), sz = Math.sqrt(1 - cz * cz), ph = 2 * Math.PI * (i + 0.5) / 16;
                double[] L = sc.radiance(0.5, new double[] {sz * Math.sin(ph), cz, -sz * Math.cos(ph)}, l, e, null, null);
                for (int c = 0; c < 3; c++) es[c] += L[c] * Math.PI / 128;           // ∫ L cos θ dω mit cos-gewichteten Richtungen
            }
        double[] o = new double[6];
        for (int c = 0; c < 3; c++) {
            double eg = e[c] * t[c] * Math.max(0, l[1]) + es[c];
            o[c] = st[0][c] + st[1][c] * a / Math.PI * eg;
            o[3 + c] = st[0][c];
        }
        return o;
    }

    static double y(double[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }

    static void messe(double sichtKm, double[] sonnen, int n) {
        Atmosphere luft = Atmosphere.forVisibility(sichtKm, 0.08);
        Scattering sc = new Scattering(luft);
        A.info(f("Luft, Sicht %.0f km", sichtKm), luft.toString());
        double[][] blicke = {{90, 0}, {30, 0}, {30, 90}, {30, 180}, {10, 0}, {10, 90}, {10, 180}, {3, 0}, {3, 90}, {3, 180}};
        double[] e = {Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX};
        for (double hs : sonnen) {
            A.teil(f("Sicht %.0f km, Sonne %.0f° hoch", sichtKm, hs));
            double a = Math.toRadians(hs);
            double[] l = {0, Math.sin(a), -Math.cos(a)};
            Referenz r3 = referenz(luft, l, false, false), r1 = referenz(luft, l, false, true), rs = referenz(luft, l, true, false);
            double[] sumE = new double[3], sumR = new double[3], sumS = new double[3], sumES = new double[3];
            double f1 = 0, f3 = 0, fs = 0;
            for (double[] b : blicke) {
                double eb = Math.toRadians(b[0]), az = Math.toRadians(b[1]);
                double[] v = {Math.cos(eb) * Math.sin(az), Math.sin(eb), -Math.cos(eb) * Math.cos(az)};
                long saat = (long) (sichtKm * 1e6 + hs * 1000 + b[0] * 10 + b[1]);
                double[] e1 = sc.radianceEinfach(2, v, l, e, 256), eg = sc.radiance(2, v, l, e, null, null, 256), egT = sc.radiance(2, v, l, e, null, null);
                double[] es = sc.radianceSpektral(2, v, l, 1, Scattering.VIEW_STEPS);
                double[] q1 = ref(r1, v, n, saat), q3 = ref(r3, v, n, saat + 1), qs = ref(rs, v, n, saat + 2);
                StringBuilder s = new StringBuilder();
                for (int c = 0; c < 3; c++) {
                    double d1 = e1[c] / q1[c] - 1, d3 = eg[c] / q3[c] - 1, ds = es[c] / qs[c] - 1;
                    // Einfachstreuung im Erdschatten (Sonne −3°, Blick von ihr weg): beide 0
                    if (!(q1[c] > 0)) {
                        d1 = e1[c] > 0 ? 1 : 0;
                        f1 = Math.max(f1, d1);
                    } else f1 = Math.max(f1, Math.abs(d1) - 3 * q1[3 + c] / q1[c]);
                    f3 = Math.max(f3, Math.abs(d3) - 3 * q3[3 + c] / q3[c]);
                    fs = Math.max(fs, Math.abs(ds) - 3 * qs[3 + c] / qs[c]);
                    s.append(f("%s: einfach %+.1f %%, ganz %+.1f %% (Mehrfach Referenz %.0f %%, Echtzeit %.0f %%), spektral %.4g gegen %.4g (%+.1f %%, Spektrum %+.1f %% zu drei Kanälen), σ %.1f %%; ",
                            "RGB".charAt(c), 100 * d1, 100 * d3, 100 * (1 - q1[c] / q3[c]), 100 * (1 - e1[c] / eg[c]), es[c], qs[c], 100 * ds, 100 * (qs[c] / q3[c] - 1),
                            100 * qs[3 + c] / qs[c]));
                    sumE[c] += egT[c];
                    sumR[c] += q3[c];
                    sumS[c] += qs[c];
                    sumES[c] += es[c];
                }
                A.info(f("Blick %.0f° hoch, %.0f° zur Sonne", b[0], b[1]), s.toString());
            }
            String sm = f("drei Kanäle (" + Scattering.VIEW_STEPS + " Schritte) R %.3f G %.3f B %.3f; spektral R %.3f G %.3f B %.3f",
                    sumE[0] / sumR[0], sumE[1] / sumR[1], sumE[2] / sumR[2], sumES[0] / sumS[0], sumES[1] / sumS[1], sumES[2] / sumS[2]);
            A.info("Im Mittel der Richtungen: Echtzeit durch Referenz", sm);
            double mS = 0;
            for (int c = 0; c < 3; c++) mS = Math.max(mS, Math.abs(sumES[c] / sumS[c] - 1));
            A.pruefe(f1 <= 0.02, f("Einfachstreuung, Sicht %.0f km, Sonne %.0f°: Echtzeit gegen Referenz je Richtung und Kanal (drei Kanäle)", sichtKm, hs), f("größte Abweichung über 3 σ %.1f %%", 100 * Math.max(0, f1)), "≤ 2 % [G]");
            A.pruefe(f3 <= 0.05, f("Ganz, drei Kanäle, Sicht %.0f km, Sonne %.0f°: Echtzeit (Streuordnungen) gegen Referenz je Richtung und Kanal", sichtKm, hs), f("größte Abweichung über 3 σ %.1f %%", 100 * Math.max(0, f3)), "≤ 5 % [G]");
            A.pruefe(fs <= 0.05 && mS <= 0.02, f("Spektral, Sicht %.0f km, Sonne %.0f°: Echtzeit (15 Wellenlängen) gegen Referenz (360–830 nm) je Richtung und Kanal, und im Mittel", sichtKm, hs),
                    f("größte Abweichung über 3 σ %.1f %%; im Mittel %s", 100 * Math.max(0, fs), sm), "≤ 5 % je Richtung, im Mittel ≤ 2 % [G]");
        }
    }
}
