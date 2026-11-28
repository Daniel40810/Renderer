package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flotte;
import com.dan.lauf.Szenen;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.dan.core.pruef.Attest.f;

/**
 * S9b-0 (Seegang Werkbuch 107): Warum misst die Abnahme in L7b 45–50 ms je Bild in Hoch, LeistungCheck in denselben
 * Ansichten laufend 34–38 ms? Je Ansicht der Abnahme nach dem Vorlauf wie LeistungCheck im Wechsel, {@link #RUNDEN}-mal
 * je {@link #BLOCK} Bilder:
 * <ul>
 * <li><b>A</b> wie die Abnahme und die Anwendung: Flotte und Bild nebeneinander;</li>
 * <li><b>B</b> wie LeistungCheck: nach jedem Bild auf den Faden der Flotte warten;</li>
 * <li><b>C</b> die Flotte ruht (kein Auftrag an ihren Faden);</li>
 * <li><b>S</b> das stehende Bild (Thermometer: dieselbe Arbeit, ändert es sich, liegt es an der Karte).</li>
 * </ul>
 * Je Art: Median der Karte (Summe der Durchgänge) und der Uhr, Netz, Bilder mit laufendem Bau der Fläche, stockende Bilder;
 * die Abschnitte mit dem größten Unterschied A gegen C. Dazu Prüfstein 45/46 mit fester Belichtung
 * ({@link Buehne#bildzeitenDt} = 0): das stehende Bild mit den neuen Wegen gegen die alten.
 * <pre>  BildzeitDiagnose --szenen ..\HalligHooge\src\szenen\Hooge.szenen</pre>
 */
public final class BildzeitDiagnose {
    static final Attest A = new Attest("Seegang: Bildzeit, Flotte und Bau der Fläche (S9b-0)", "S9b");
    static final int BLOCK = 30, RUNDEN = 3, STEHEND = 10;
    static final String NETZ = "Wasser, Netz";

    public static void main(String[] args) {
        File szenen = null;
        int breite = 1920, hoehe = 1080;
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                default -> { }
            }
        System.out.println("BildzeitDiagnose – S9b-0 (Seegang Werkbuch 107)");
        long t0 = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            alles(Szenen.lies(szenen), breite, hoehe);
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        Path pa = A.schreibe(Path.of("docs", "status", "S9b_Bildzeit.md"), "S9b-0 – Bildzeit: Flotte, Bau der Fläche, feste Belichtung");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Je Art gesammelt. */
    static final class Art {
        final String name;
        final List<Double> karte = new ArrayList<>(), uhr = new ArrayList<>(), netz = new ArrayList<>(), faden = new ArrayList<>();
        final Map<String, Double> summe = new TreeMap<>();
        int bilder, bau;

        Art(String name) { this.name = name; }

        void nimm(Buehne.Bildzeiten z, boolean baut, double fadenMs) {
            karte.add(z.karte()[0]);
            uhr.add(z.wand()[0]);
            netz.add(z.abschnitte().getOrDefault(NETZ, 0.0));
            if (!Double.isNaN(fadenMs)) faden.add(fadenMs);
            for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) summe.merge(e.getKey(), e.getValue(), Double::sum);
            bilder++;
            if (baut) bau++;
        }

        double mittel(String ab) { return summe.getOrDefault(ab, 0.0) / Math.max(1, bilder); }

        String text() {
            return f("%s: Karte %.2f ms, Uhr %.2f ms, Netz %.2f ms (Median); %d Bilder, Bau der Fläche in %d, über 2 × Median %d%s", name,
                    SchaumCheck.median(karte), SchaumCheck.median(uhr), SchaumCheck.median(netz), bilder, bau, stocken(),
                    faden.isEmpty() ? "" : f("; Faden je Bild %.2f ms (Median)", SchaumCheck.median(faden)));
        }

        int stocken() {
            double m = SchaumCheck.median(karte);
            int n = 0;
            for (double x : karte) if (x > 2 * m) n++;
            return n;
        }
    }

    static void alles(Szenen sz, int breite, int hoehe) throws Exception {
        Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
        A.info("Gerät", b.geraet().name + f(", %d × %d", breite, hoehe));
        List<double[]> ab = new ArrayList<>();
        List<String> abWo = new ArrayList<>();
        List<double[]> pr = new ArrayList<>();
        try {
            String offen = null;
            for (String[] fa : SeegangAbnahme.FAELLE) {
                A.teil(fa[0] + ", " + fa[1]);
                if (!fa[0].equals(offen)) {
                    SchwimmerCheck.pegel(b, sz);
                    b.oeffne(SchaumCheck.szenario(sz, fa[0]));
                    b.wasserbild().seegang = true;
                    offen = fa[0];
                }
                LeistungCheck.Fall c = LeistungCheck.einrichten(b, sz, fa, breite, hoehe);
                LeistungCheck.festhalten(c);
                // Prüfstein 45/46 mit fester Belichtung
                double[] v = vergleichFest(b, c);
                pr.add(v);
                A.info("Prüfstein 45/46, feste Belichtung: neue Wege gegen alte", f("ΔE₀₀ Mittel %.4f, 99,9 %% %.3f, größter %.3f; Bildpunkte anders %d (%.3f %%); zweimal neu: %d anders",
                        v[0], v[1], v[2], (long) v[3], 100 * v[4], (long) v[5]));
                Art a = new Art("A Flotte nebenher (Anwendung, Abnahme)"), bb = new Art("B nach jedem Bild auf die Flotte warten (LeistungCheck)"),
                        cc = new Art("C Flotte ruht"), s = new Art("S stehendes Bild");
                Flotte fl = b.flotte();
                for (int r = 0; r < RUNDEN; r++) {
                    for (int i = 0; i < BLOCK; i++) {
                        Buehne.Bildzeiten z = SeegangAbnahme.bild(b, c.w, c.k, c.bl0, c.zt, c.dt, c.uhrNs);
                        c.uhrNs += Math.round(c.dt * 1e9);
                        a.nimm(z, c.w.baut(), fl != null ? fl.fadenMs : Double.NaN);
                    }
                    for (int i = 0; i < BLOCK; i++) {
                        Buehne.Bildzeiten z = SeegangAbnahme.bild(b, c.w, c.k, c.bl0, c.zt, c.dt, c.uhrNs);
                        c.uhrNs += Math.round(c.dt * 1e9);
                        if (fl != null) fl.warte();
                        bb.nimm(z, c.w.baut(), fl != null ? fl.fadenMs : Double.NaN);
                    }
                    if (fl != null) fl.warte();
                    for (int i = 0; i < BLOCK; i++) {
                        Buehne.Bildzeiten z = ohneFlotte(b, c);
                        cc.nimm(z, c.w.baut(), Double.NaN);
                    }
                    Buehne.Bildzeiten z = b.bildzeiten(c.fest, c.tFest, STEHEND, true);
                    for (int i = 0; i < STEHEND; i++) s.karte.add(z.karte()[i]);
                    s.bilder += STEHEND;
                }
                for (Art x : new Art[] {a, bb, cc}) A.info("Art " + x.name.charAt(0), x.text());
                A.info("Art S", f("stehendes Bild: Karte %.2f ms (Median über %d Bilder in %d Runden)", SchaumCheck.median(s.karte), s.bilder, RUNDEN));
                A.info("A gegen C je Abschnitt (ms, größte Unterschiede)", unterschied(a, cc, 8));
                A.info("A gegen B je Abschnitt (ms, größte Unterschiede)", unterschied(a, bb, 6));
                ab.add(new double[] {SchaumCheck.median(a.karte), SchaumCheck.median(bb.karte), SchaumCheck.median(cc.karte), SchaumCheck.median(s.karte)});
                abWo.add(fa[0] + ", " + fa[1]);
                if (c.k != null) b.bordKamera(null);
            }
        } finally {
            b.close();
        }
        A.teil("Urteil");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ab.size(); i++) {
            double[] v = ab.get(i);
            sb.append(f("%s%s: A %.1f, B %.1f, C %.1f, S %.1f", sb.length() > 0 ? "; " : "", abWo.get(i), v[0], v[1], v[2], v[3]));
        }
        A.info("Karte je Bild (ms, Median) je Ansicht und Art", sb.toString());
        double dMax = 0, aMax = 0;
        int ueber = 0;
        for (double[] v : pr) { dMax = Math.max(dMax, v[0]); aMax = Math.max(aMax, v[4]); if (v[3] > v[5]) ueber++; }
        A.pruefe(dMax < 1, "Prüfstein 45: neue Wege gegen alte, ΔE₀₀ im Mittel (feste Belichtung; größtes Mittel über die Ansichten)", f("%.4f", dMax), "< 1");
        // seit S9c (SF179 A): neu gegen alt nicht mehr Bildpunkte anders als zweimal neu (das Rauschen der Szene)
        A.pruefe(ueber == 0, "Prüfstein 46: neue Wege gegen alte, Bildpunkte anders, höchstens so viele wie zweimal neu",
                f("%d von %d Ansichten darüber; höchster Anteil neu gegen alt %.3f %%", ueber, pr.size(), 100 * aMax), "0");
    }

    /** Ein Bild ohne Auftrag an die Flotte (sonst wie SeegangAbnahme.bild). */
    static Buehne.Bildzeiten ohneFlotte(Buehne b, LeistungCheck.Fall c) {
        c.w.zeitraffer(1);
        com.dan.core.raum.Blick bl = c.k != null ? c.k.weiter(c.dt, c.zt[0], c.bl0) : null;
        if (bl == null) bl = c.bl0.kopie();
        bl.groesse(c.bl0.breite, c.bl0.hoehe_px);
        c.w.vorBild(bl, c.zt[0], c.uhrNs);
        Buehne.Bildzeiten z = b.bildzeiten(bl, c.zt[0], 1, true);
        c.w.nachBild();
        c.zt[0] += c.dt;
        c.uhrNs += Math.round(c.dt * 1e9);
        return z;
    }

    /** Seit L7g: Unterschied zweier Mittel je Abschnitt, die n größten. */
    static String unterschiedKarten(Map<String, Double> x, Map<String, Double> y, int n) {
        Map<String, Double> d = new TreeMap<>();
        for (Map.Entry<String, Double> e : x.entrySet()) d.put(e.getKey(), e.getValue() - y.getOrDefault(e.getKey(), 0.0));
        for (Map.Entry<String, Double> e : y.entrySet()) d.putIfAbsent(e.getKey(), -e.getValue());
        List<Map.Entry<String, Double>> l = new ArrayList<>(d.entrySet());
        l.sort((p, q) -> Double.compare(Math.abs(q.getValue()), Math.abs(p.getValue())));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, l.size()); i++) sb.append(f("%s%s %+.2f%s", i > 0 ? "; " : "", l.get(i).getKey(), l.get(i).getValue(), y.containsKey(l.get(i).getKey()) ? "" : " (neu)"));
        return sb.toString();
    }

    static String unterschied(Art x, Art y, int n) {
        Map<String, Double> d = new TreeMap<>();
        for (String k : x.summe.keySet()) d.put(k, x.mittel(k) - y.mittel(k));
        for (String k : y.summe.keySet()) d.putIfAbsent(k, -y.mittel(k));
        List<Map.Entry<String, Double>> l = new ArrayList<>(d.entrySet());
        l.sort((p, q) -> Double.compare(Math.abs(q.getValue()), Math.abs(p.getValue())));
        StringBuilder sb = new StringBuilder();
        double summe = 0;
        for (Map.Entry<String, Double> e : l) summe += e.getValue();
        for (int i = 0; i < Math.min(n, l.size()); i++) sb.append(f("%s%s %+.2f", i > 0 ? "; " : "", l.get(i).getKey(), l.get(i).getValue()));
        return sb + f("; Summe %+.2f", summe);
    }

    /**
     * Prüfstein 45/46 mit fester Belichtung: das stehende Bild neu, alt, noch einmal neu (je drei Bilder, das letzte zählt).
     * {ΔE₀₀ Mittel, 99,9 %, größter (neu/alt), Bildpunkte anders, ihr Anteil, Bildpunkte anders neu/neu}.
     */
    static double[] vergleichFest(Buehne b, LeistungCheck.Fall c) {
        int w = c.bl0.breite, h = c.bl0.hoehe_px;
        b.bildzeiten(c.fest, c.tFest, 30, false);                               // Belichtung einschwingen
        double dt0 = b.bildzeitenDt;
        b.bildzeitenDt = 0;
        try {
            b.bildzeiten(c.fest, c.tFest, 3, false);
            float[] neu = b.letztesBild();
            LeistungCheck.alteWege(b, true);
            float[] alt;
            try {
                b.bildzeiten(c.fest, c.tFest, 3, false);
                alt = b.letztesBild();
            } finally {
                LeistungCheck.alteWege(b, false);
            }
            b.bildzeiten(c.fest, c.tFest, 3, false);
            float[] neu2 = b.letztesBild();
            long anders = 0, rauschen = 0, n = (long) w * h;
            for (int i = 0; i < n; i++) {
                boolean g = true, g2 = true;
                for (int k = 0; k < 3; k++) {
                    if (Float.floatToRawIntBits(neu[4 * i + k]) != Float.floatToRawIntBits(alt[4 * i + k])) g = false;
                    if (Float.floatToRawIntBits(neu[4 * i + k]) != Float.floatToRawIntBits(neu2[4 * i + k])) g2 = false;
                }
                if (!g) anders++;
                if (!g2) rauschen++;
            }
            com.dan.core.pruef.Bildvergleich.Ergebnis e = com.dan.core.pruef.Bildvergleich.vergleiche(SeegangBilder.bild(neu, w, h), SeegangBilder.bild(alt, w, h), 1.0);
            double[] de = deltaE(neu, alt, w, h);
            return new double[] {e.mittel(), de[0], e.groesste(), anders, anders / (double) n, rauschen};
        } finally {
            b.bildzeitenDt = dt0;
        }
    }

    /** 99,9 % des ΔE₀₀ über die Bildpunkte, die sich unterscheiden (0, wenn keiner). */
    static double[] deltaE(float[] a, float[] b, int w, int h) {
        List<Double> l = new ArrayList<>();
        double[] la = new double[3], lb = new double[3];
        for (int i = 0; i < w * h; i++) {
            int ra = rgb(a, i), rb = rgb(b, i);
            if (ra == rb) { l.add(0.0); continue; }
            com.dan.core.pruef.Bildvergleich.lab(ra, la);
            com.dan.core.pruef.Bildvergleich.lab(rb, lb);
            l.add(com.dan.core.pruef.Bildvergleich.deltaE00(la[0], la[1], la[2], lb[0], lb[1], lb[2]));
        }
        return new double[] {LeistungCheck.quantil(l, 0.999)};
    }

    static int rgb(float[] p, int i) {
        int r = Math.round(Math.max(0, Math.min(1, p[4 * i])) * 255), g = Math.round(Math.max(0, Math.min(1, p[4 * i + 1])) * 255), b = Math.round(Math.max(0, Math.min(1, p[4 * i + 2])) * 255);
        return (r << 16) | (g << 8) | b;
    }
}
