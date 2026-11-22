package com.dan.render.air.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Scattering;
import com.dan.core.szene.spektrum.Spektrum;
import com.dan.render.air.Himmel;

import java.nio.file.Path;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * P10d, Prüfsteine 1 und 8 (Werkbuch HalligHooge Teil A, 12.11), ohne Karte und ohne Gebiet: die direkte Sonne auf
 * einer weißen, waagerechten Fläche am Boden durch die Luft, wie das Echtzeitbild sie rechnet (127 500 lx je Kanal mal
 * die Durchlässigkeit der drei Kanäle aus der Tabelle, wie {@code gelaende.frag}, mal {@link Lichtquellen#farbeNachLuft})
 * gegen die spektrale Rechnung (360–830 nm je nm, je Wellenlänge eigens über 4 000 Schritte bis zur Oberkante
 * integriert, nach linearem sRGB). Dazu zur Auskunft, was ohne die Farbe nach der Luft herauskam (bis P10c: {@link
 * Lichtquellen#SONNE_FARBE}), und was die gehobene statt der wahren Richtung bringt. Prüfstein 8: Zeit für die Farbe je
 * Bild (Sonne und Mond). Sicht 30 km, Albedo 0,23 wie {@link Himmel}. Attest {@code docs/status/P10_Sonnenfarbe.md}.
 */
public final class SonnenfarbeCheck {
    private static final Attest A = new Attest("com.dan.render.air", Himmel.VERSION);

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        System.out.println("SonnenfarbeCheck – direkte Sonne nach der Luft (Werkbuch HalligHooge Teil A, P10d)");
        try {
            pruefe();
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P10_Sonnenfarbe.md"), "P10d – SonnenfarbeCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Spektral, unabhängig von Lichtquellen: je nm die optische Tiefe über n gleichmäßig wachsende Schritte. */
    static double[] spektral(Atmosphere at, double h, double mu, int n) {
        double R = Atmosphere.BOTTOM, r = R + h, RT = Atmosphere.TOP;
        double d = -r * mu + Math.sqrt((RT - r) * (RT + r) + r * r * mu * mu);
        // Säulen einmal (die Dichten hängen nicht von der Wellenlänge ab), aber mit eigenem, feinerem Gitter (kubisch dicht am Anfang)
        double sR = 0, sM = 0, sO = 0;
        for (int k = 0; k < n; k++) {
            double a = (double) k / n, b = (k + 1.0) / n, t0 = d * a * a * a, t1 = d * b * b * b, t = 0.5 * (t0 + t1), dt = t1 - t0;
            double hh = Math.sqrt(r * r + t * t + 2 * r * mu * t) - R;
            sR += Atmosphere.rayleighDensity(hh) * dt;
            sM += Atmosphere.mieDensity(hh) * dt;
            sO += Atmosphere.ozoneDensity(hh) * dt;
        }
        double[] xyz = new double[3];
        for (int l = Spektrum.MIN; l <= Spektrum.MAX; l++) {
            double tau = Spektrum.rayleigh(l, Atmosphere.RAYLEIGH_H) * sR + at.mieExtinction * sM + Spektrum.OZON_DICHTE * Spektrum.ozon(l) * sO;
            double e = Spektrum.sonne(l) * Math.exp(-tau);
            double[] x = Spektrum.xyz(l);
            for (int c = 0; c < 3; c++) xyz[c] += x[c] * e;
        }
        double[] rgb = Spektrum.nachRgb(xyz);
        for (int c = 0; c < 3; c++) rgb[c] *= Spektrum.KM;
        return rgb;
    }

    static void pruefe() {
        Atmosphere at = Atmosphere.forVisibility(30, 0.23);
        Scattering sc = new Scattering(at);
        double h = 0.5;                                                     // wie gelaende.frag: h = max(0,5, …)
        A.teil("1 Direkte Sonne auf weißer Fläche (Prüfstein 1)");
        double fmax = 0;
        StringBuilder alt = new StringBuilder();
        for (double hs : new double[] {60, 21.6, 10, 5, 3.2, 2.4, 1}) {
            double mu = Math.sin(Math.toRadians(hs));
            double[] t = sc.transmittance(Atmosphere.BOTTOM + h, mu), fN = Lichtquellen.farbeNachLuft(at, mu), sp = spektral(at, h, mu, 4000);
            double[] echt = new double[3], vor = new double[3], v = new double[3], w = new double[3];
            for (int c = 0; c < 3; c++) {
                echt[c] = Lichtquellen.SONNE_LX * t[c] * fN[c];
                vor[c] = Lichtquellen.SONNE_LX * t[c] * Lichtquellen.SONNE_FARBE[c];
                v[c] = echt[c] / sp[c];
                w[c] = vor[c] / sp[c];
            }
            double ly = lum(echt) / lum(sp), lyV = lum(vor) / lum(sp);
            String z = f("Faktor %.3f / %.3f / %.3f; Echtzeit durch spektral R %.4f G %.4f B %.4f, Leuchtdichte %.4f (spektral %.0f / %.0f / %.0f lx); bis P10c R %.3f G %.3f B %.3f, Leuchtdichte %.3f",
                    fN[0], fN[1], fN[2], v[0], v[1], v[2], ly, sp[0], sp[1], sp[2], w[0], w[1], w[2], lyV);
            if (hs >= 3) {
                double m = Math.max(Math.abs(ly - 1), Math.max(Math.abs(v[0] - 1), Math.abs(v[1] - 1)));
                if (sp[2] > 1e-3 * sp[1]) m = Math.max(m, Math.abs(v[2] - 1));
                fmax = Math.max(fmax, m);
                A.info(f("Sonne %.1f°", hs), z);
            } else A.info(f("Sonne %.1f° (Auskunft)", hs), z);
        }
        A.pruefe(fmax <= 0.02, "Prüfstein 1: direkte Sonne durch die Luft, Rechnung des Echtzeitbilds (Tabelle der drei Kanäle mal Faktor nach der Luft) gegen spektral, Sonne 60°, 21,6°, 10°, 5°, 3,2°, je Kanal",
                f("größte Abweichung %.2f %%", 100 * fmax), "1 ± 0,02 [G]");
        // Mond: dieselbe Rechnung für seine Höhe; seine Farbe (MONDFARBE) liegt in beiden Fällen darüber
        double mm = 0;
        StringBuilder mb = new StringBuilder();
        for (double hm : new double[] {45, 20, 8, 3.2}) {
            double mu = Math.sin(Math.toRadians(hm));
            double[] t = sc.transmittance(Atmosphere.BOTTOM + h, mu), fN = Lichtquellen.farbeNachLuft(at, mu), sp = spektral(at, h, mu, 4000);
            mb.append(f("%.1f°: ", hm));
            for (int c = 0; c < 3; c++) {
                double e = Lichtquellen.MONDFARBE[c] * t[c] * fN[c] * Lichtquellen.SONNE_LX, s = Lichtquellen.MONDFARBE[c] * sp[c];
                mm = Math.max(mm, Math.abs(e / s - 1));
                mb.append(f("%s %.4f ", "RGB".charAt(c), e / s));
            }
            mb.append("; ");
        }
        A.pruefe(mm <= 0.02, "Prüfstein 1, Mond: dieselbe Rechnung für 45°, 20°, 8°, 3,2° (MONDFARBE auf beiden Seiten)", mb + f("größte Abweichung %.2f %%", 100 * mm), "1 ± 0,02 [G]");

        // F100 A: was die gehobene Richtung bringt (Auskunft; Prüfstein 4 zum Teil)
        StringBuilder g = new StringBuilder();
        double g15 = 0;
        for (double hs : new double[] {60, 21.6, 15, 10, 5.1, 3.2, 2.4, 1}) {
            double wahr = wahreHoehe(hs);
            double[] tg = sc.transmittance(Atmosphere.BOTTOM + h, Math.sin(Math.toRadians(hs))), tw = sc.transmittance(Atmosphere.BOTTOM + h, Math.sin(Math.toRadians(wahr)));
            double[] fg = Lichtquellen.farbeNachLuft(at, Math.sin(Math.toRadians(hs))), fw = Lichtquellen.farbeNachLuft(at, Math.sin(Math.toRadians(wahr)));
            double eg = Math.sin(Math.toRadians(hs)) * lum(new double[] {tg[0] * fg[0], tg[1] * fg[1], tg[2] * fg[2]});
            double ew = Math.max(0, Math.sin(Math.toRadians(wahr))) * lum(new double[] {tw[0] * fw[0], tw[1] * fw[1], tw[2] * fw[2]});
            g.append(f("%.1f° (wahr %.2f°): %.3f; ", hs, wahr, eg / ew));
            if (hs >= 15) g15 = Math.max(g15, Math.abs(eg / ew - 1));
        }
        A.info("F100 A: direkte Sonne auf waagerechter Fläche, gehoben durch wahr (Leuchtdichte)", g.toString());
        A.pruefe(g15 <= 0.005, "Prüfstein 4 (Teil): die gehobene Richtung ändert die direkte Sonne bei Sonne über 15° um höchstens 0,5 %", f("%.2f %%", 100 * g15), "≤ 0,5 % [G]");

        // ---- Prüfstein 8
        A.teil("2 Kosten (Prüfstein 8)");
        int n = 4000;
        double[] mus = new double[n];
        for (int i = 0; i < n; i++) mus[i] = Math.sin(Math.toRadians(1 + 60.0 * i / n));
        double s = 0;
        for (int i = 0; i < n; i++) s += Lichtquellen.farbeNachLuft(at, mus[i])[0];       // warm
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) s += Lichtquellen.farbeNachLuft(at, mus[i])[0];
        double ms = (System.nanoTime() - t0) / 1e6 / n;
        A.pruefe(2 * ms <= 0.1, "Prüfstein 8: Farbe nach der Luft je Bild auf dem Prozessor (Sonne und Mond, je " + Lichtquellen.FARBE_SCHRITTE + " Schritte und 471 nm)",
                f("%.4f ms je Bild (%.4f ms je Quelle; Prüfsumme %.1f)", 2 * ms, ms, s), "≤ 0,1 ms [G]");
    }

    /** Wahre Höhe zur scheinbaren hs (Grad), Umkehrung der Brechung (Sæmundsson) durch Halbierung. */
    static double wahreHoehe(double hs) {
        double a = hs - 2, b = hs;
        for (int i = 0; i < 60; i++) {
            double m = 0.5 * (a + b);
            if (m + Lichtquellen.brechung(m) > hs) b = m;
            else a = m;
        }
        return 0.5 * (a + b);
    }

    static double lum(double[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }
}
