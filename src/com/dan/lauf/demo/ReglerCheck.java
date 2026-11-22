package com.dan.lauf.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.pruef.Attest;
import com.dan.lauf.Lauf;
import com.dan.lauf.Pegelreihe;
import com.dan.physics.antrieb.Antrieb;
import com.dan.physics.hydro.HydroModul;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Regler im Live-Lauf (Steuerhaus Y2, Werkbuch HalligHooge 153): Faktor und Drehung des Winds im Antrieb, Aufschlag
 * am Rand der innersten Stufe in hydro. Drei Läufe wie live (G0 | G1 | G2 G3, Fenster von G3, ab 29.10.2026 03:00 UTC aus
 * der Ruhe, auf der Karte): Basis, Aufschlag +1,00 m ab 3 h, Wind × 2 und 30° gedreht ab 3 h; gemessen am Pegel Hooge
 * und am Wind dort bis 7 h. Grenzen [G] vor der Messung gesetzt.
 * <ul>
 * <li>R1 (ohne Karte): Drehung und Rampe gerechnet.</li>
 * <li>R2: bis zum Eingriff sind die drei Läufe gleich (Pegel bei 3 h, ≤ 1 mm).</li>
 * <li>R3: Aufschlag +1,00 m: Unterschied am Pegel Hooge nach 4 h (bei 7 h) 0,8 bis 1,1 m; wirksam am Ende 1,00 m.</li>
 * <li>R4: Wind am Ort Hooge (Bus) ab 4 h: Betrag mal 2,000 ± 0,001, Richtung +30° ± 0,1°; der Pegel antwortet (≥ 1 cm bei 7 h).</li>
 * <li>R5: Volumen je Stufe in allen Läufen (wie P5c, &lt; 1e-5), Pegel endlich.</li>
 * </ul>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.ReglerCheck --gebiet D:\Daten\HalligHooge\gebiet [--ohne-karte]
 * </pre>
 * Attest {@code docs/status/Y2_Regler.md}.
 */
public final class ReglerCheck {
    static final Attest A = new Attest("com.dan.lauf (Regler im Live-Lauf)", Lauf.VERSION);
    static final Instant START = Instant.parse("2026-10-29T03:00:00Z");
    static final double EINGRIFF = 3 * 3600, ENDE = 7 * 3600, PROBE = 900, ANTWORT = 0.01;
    static final double PEGEL_OST = 470410, PEGEL_NORD = 6048090, PEGEL_UMKREIS = 300;
    static final double AUFSCHLAG = 1.0, FAKTOR = 2, DREHUNG = 30;

    public static void main(String[] args) {
        List<String> a = List.of(args);
        int gi = a.indexOf("--gebiet");
        String gebiet = gi >= 0 && gi + 1 < a.size() ? a.get(gi + 1) : null;
        long n0 = System.nanoTime();
        System.out.println("ReglerCheck – Antrieb " + Antrieb.VERSION + ", hydro " + HydroModul.VERSION + " (Werkbuch HalligHooge 153)");
        try {
            r1();
            if (gebiet == null) A.info("Läufe", "ohne --gebiet nicht gerechnet");
            else if (!a.contains("--ohne-karte")) laeufe(new File(gebiet));
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.0f s", (System.nanoTime() - n0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", "Y2_Regler.md"), "Y2 – ReglerCheck (Regler im Live-Lauf)");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static String f(String m, Object... x) { return String.format(Locale.GERMAN, m, x); }

    /** R1: Drehung und Rampe gerechnet. */
    static void r1() {
        A.teil("R1 Rechnung");
        double[] nord = Antrieb.drehe(0, 1, 1, 90), b = Antrieb.drehe(3, 4, 2, 37), voll = Antrieb.drehe(3, 4, 1, 360), w = Antrieb.drehe(1, 0, 1, 90);
        boolean ok = Math.abs(nord[0] - 1) < 1e-12 && Math.abs(nord[1]) < 1e-12 && Math.abs(Math.hypot(b[0], b[1]) - 10) < 1e-12 && Math.abs(voll[0] - 3) < 1e-12
                && Math.abs(voll[1] - 4) < 1e-12 && Math.abs(w[0]) < 1e-12 && Math.abs(w[1] + 1) < 1e-12;
        double r1 = Antrieb.rampe(1, 2, 2.0 / 3600, 900), r2 = Antrieb.rampe(1, 2, 2.0 / 3600, 3600), r3 = HydroModul.rampe(0.8, -0.5, 1.0 / 3600, 1800), r4 = HydroModul.rampe(0, 0, 1, 10);
        ok &= Math.abs(r1 - 1.5) < 1e-12 && r2 == 2 && Math.abs(r3 - 0.3) < 1e-12 && r4 == 0;
        A.pruefe(ok, "Drehung im Uhrzeigersinn (Nord → Ost bei 90°, Ost → Süd), Betrag mal Faktor, 360° gleich; Rampe mit Grenze, beide Richtungen, am Ziel fest",
                f("Nord+90° = (%.3f, %.3f); |(3,4)·2| = %.3f; Rampe 1→2 in 15 min = %.3f, in 1 h = %.3f; 0,8→−0,5 in 30 min = %.3f", nord[0], nord[1], Math.hypot(b[0], b[1]), r1, r2, r3),
                "genau");
    }

    record Probe(double t, double pegel, double windU, double windV) { }

    record Ergebnis(List<Probe> proben, double bilanz, double aufschlag, double windFaktor, double windDrehung, double sekunden) {
        Probe bei(double t) {
            Probe b = null;
            for (Probe p : proben) if (b == null || Math.abs(p.t - t) < Math.abs(b.t - t)) b = p;
            return b;
        }
    }

    static void laeufe(File gebiet) throws Exception {
        A.teil("Läufe wie live (G0 | G1 | G2 G3, Fenster von G3, Karte), ab " + START + " aus der Ruhe, Eingriff nach 3 h");
        Ergebnis basis = lauf(gebiet, 0, 1, 0), auf = lauf(gebiet, AUFSCHLAG, 1, 0), wind = lauf(gebiet, 0, FAKTOR, DREHUNG);
        A.info("Rechenzeit der Läufe", f("Basis %.0f s, Aufschlag %.0f s, Wind %.0f s (je 7 h Modellzeit)", basis.sekunden, auf.sekunden, wind.sekunden));

        A.teil("R2 gleich bis zum Eingriff");
        double p0 = basis.bei(EINGRIFF).pegel, pa = auf.bei(EINGRIFF).pegel, pw = wind.bei(EINGRIFF).pegel;
        A.pruefe(Math.abs(pa - p0) <= 1e-3 && Math.abs(pw - p0) <= 1e-3, "Pegel Hooge bei 3 h in den drei Läufen", f("%+.4f / %+.4f / %+.4f m NHN", p0, pa, pw), "≤ 1 mm Unterschied [G]");

        A.teil("R3 Aufschlag +1,00 m am Rand von G2");
        StringBuilder zr = new StringBuilder();
        for (double t = EINGRIFF; t <= ENDE + 1; t += 3600) zr.append(f("%s%.0f h %+.2f", zr.length() > 0 ? ", " : "", t / 3600, auf.bei(t).pegel - basis.bei(t).pegel));
        double d7 = auf.bei(ENDE).pegel - basis.bei(ENDE).pegel;
        A.pruefe(d7 >= 0.8 && d7 <= 1.1 && Math.abs(auf.aufschlag - AUFSCHLAG) < 1e-9, "Unterschied am Pegel Hooge nach 4 h; wirksam am Ende",
                f("%+.3f m bei 7 h (Verlauf: %s m); wirksam %.3f m", d7, zr, auf.aufschlag), "0,8 bis 1,1 m [G]; 1,000 m");

        A.teil("R4 Wind × 2, um 30° gedreht");
        double qmin = Double.POSITIVE_INFINITY, qmax = 0, dmin = 999, dmax = -999;
        for (Probe p : wind.proben) {
            if (p.t < EINGRIFF + 3600 - 1) continue;
            Probe q = basis.bei(p.t);
            double sb = Math.hypot(q.windU, q.windV), sw = Math.hypot(p.windU, p.windV);
            if (!(sb > 0.5)) continue;                                      // Richtung nur bei Wind
            double r = sw / sb, d = Math.toDegrees(Math.atan2(q.windV, q.windU) - Math.atan2(p.windV, p.windU));   // im Uhrzeigersinn positiv
            d = ((d % 360) + 540) % 360 - 180;
            qmin = Math.min(qmin, r);
            qmax = Math.max(qmax, r);
            dmin = Math.min(dmin, d);
            dmax = Math.max(dmax, d);
        }
        Probe w5 = basis.bei(5 * 3600);
        A.info("Wind am Ort Hooge in der Basis bei 5 h", f("%.1f m/s nach %.0f° (Uhrzeigersinn von Nord, wohin)", Math.hypot(w5.windU, w5.windV),
                (Math.toDegrees(Math.atan2(w5.windU, w5.windV)) + 360) % 360));
        A.pruefe(qmin >= FAKTOR - 1e-3 && qmax <= FAKTOR + 1e-3 && dmin >= DREHUNG - 0.1 && dmax <= DREHUNG + 0.1, "Wind am Ort Hooge (Bus) ab 4 h gegen die Basis",
                f("Betrag × %.4f bis %.4f, gedreht %+.2f° bis %+.2f°; wirksam × %.2f, %+.0f°", qmin, qmax, dmin, dmax, wind.windFaktor, wind.windDrehung), "× 2,000 ± 0,001; +30° ± 0,1° [G]");
        double dw = wind.bei(ENDE).pegel - basis.bei(ENDE).pegel;
        StringBuilder zw = new StringBuilder();
        for (double t = EINGRIFF; t <= ENDE + 1; t += 3600) zw.append(f("%s%.0f h %+.2f", zw.length() > 0 ? ", " : "", t / 3600, wind.bei(t).pegel - basis.bei(t).pegel));
        A.pruefe(Math.abs(dw) >= ANTWORT, "Der Pegel Hooge antwortet auf den Wind", f("%+.3f m bei 7 h (Verlauf: %s m)", dw, zw), "≥ 1 cm [G]");

        A.teil("R5 Volumen und Werte");
        boolean endlich = true;
        for (Ergebnis e : List.of(basis, auf, wind)) for (Probe p : e.proben) endlich &= Double.isFinite(p.pegel);
        double bmax = Math.max(basis.bilanz, Math.max(auf.bilanz, wind.bilanz));
        A.pruefe(bmax < 1e-5 && endlich, "Volumen je Stufe (wie P5c) in den drei Läufen; Pegel endlich",
                f("größter Fehler %.1e (Basis %.1e, Aufschlag %.1e, Wind %.1e)", bmax, basis.bilanz, auf.bilanz, wind.bilanz), "< 1e-5");
    }

    /** Ein Lauf wie live; nach 3 h die Regler, Proben alle 15 min ab 3 h bis 7 h. */
    static Ergebnis lauf(File gebiet, double aufschlag, double faktor, double drehung) throws Exception {
        Lauf.Einstellung e = new Lauf.Einstellung();
        e.gebiet = gebiet;
        e.start = START;
        e.stufen("G0 | G1 | G2 G3");
        e.karte = true;
        e.fenster3 = new int[] {600, 850, 500, 400};
        e.bild = true;
        e.name = "ReglerCheck";
        long n0 = System.nanoTime();
        List<Probe> pr = new ArrayList<>();
        double bilanz = 0;
        try (Lauf l = Lauf.bauen(e)) {
            l.bis(EINGRIFF);
            pr.add(probe(l, EINGRIFF));
            l.hydro.aufschlagZiel = aufschlag;
            l.antrieb.windFaktorZiel = faktor;
            l.antrieb.windDrehungZiel = drehung;
            for (double t = EINGRIFF + PROBE; t <= ENDE + 1; t += PROBE) {
                l.bis(t);
                pr.add(probe(l, t));
            }
            for (int s = 0; s < l.hydro.stufen.size(); s++) bilanz = Math.max(bilanz, l.bilanz(s));
            System.out.printf(Locale.GERMAN, "ReglerCheck: Lauf Aufschlag %.2f m, Wind × %.1f %+.0f° – Pegel bei 7 h %+.3f m NHN, %.0f s%n", aufschlag, faktor, drehung,
                    pr.get(pr.size() - 1).pegel, (System.nanoTime() - n0) * 1e-9);
            return new Ergebnis(pr, bilanz, l.hydro.aufschlag(), l.antrieb.windFaktor(), l.antrieb.windDrehung(), (System.nanoTime() - n0) * 1e-9);
        }
    }

    /** Pegel Hooge (wie die Bühne: tiefste nasse Zelle im Umkreis) und der Wind am Ort, am jüngsten Stand bis t. */
    static Probe probe(Lauf l, double t) {
        Pegelreihe p = Pegelreihe.aus(l.bild, l.e.start, l.gebiet, "Hooge", PEGEL_OST, PEGEL_NORD, PEGEL_UMKREIS);
        double[] z = p.zeiten(), w = p.pegel();
        int k = -1;
        for (int i = 0; i < z.length; i++) if (z[i] <= t + 1e-6) k = i;
        double ts = k >= 0 ? z[k] : Double.NaN, pegel = k >= 0 ? w[k] : Double.NaN;
        double u = Double.NaN, v = Double.NaN;
        Gitter g = l.bild.windGitter();
        if (g != null && k >= 0 && g.lage == Gitter.Lage.UTM) {
            int c = g.index(Math.max(0, Math.min(g.nx - 1, g.i(PEGEL_OST))), Math.max(0, Math.min(g.ny - 1, g.j(PEGEL_NORD))));
            float[] uv = new float[2];
            if (l.bild.windZelle(ts, c, uv)) {
                u = uv[0];
                v = uv[1];
            }
        }
        return new Probe(t, pegel, u, v);
    }
}
