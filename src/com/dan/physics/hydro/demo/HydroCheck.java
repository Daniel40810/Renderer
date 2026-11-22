package com.dan.physics.hydro.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.pruef.Attest;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.hydro.Kette;
import com.dan.physics.hydro.Ringe;
import com.dan.physics.hydro.gpu.HydroKarte;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung A von hydro (Werkbuch HalligHooge Teil A, 7.1 und 12.5), ohne Fenster. Stand P4d: die Referenz in double mit
 * den Fällen 1–8 und 11–13 (Bauwerke und Schachtelung seit P4b); mit {@code --karte} dieselben Fälle auf der Karte
 * (float), dazu Fall 10 (Referenz gegen Karte nach einem Schritt) und die Zeit je Zellschritt; mit {@code --karte
 * --gebiet} dazu Fall 9, der Lauf über G2 → G3 → G4 mit der Vorgabe der Tide ({@link HydroLauf}), Fall 14 mit seinen
 * Zeiten und das Status-Bild {@code docs/status/P4_Hydro.png}. Die Grenzwerte standen fest, bevor gemessen wurde; [G]
 * heißt gesetzt.
 *
 * <pre>
 * java -cp dist\Renderer.jar com.dan.physics.hydro.demo.HydroCheck [--gebiet D:\Daten\HalligHooge\gebiet] [--karte] [--fall 13] [--ohne-attest] [--ohne-bild]
 * </pre>
 * {@code --fall n}: nur Fall n (zum Nachrechnen; dann ohne Attest).
 */
public final class HydroCheck {
    static final Attest A = new Attest("com.dan.physics.hydro", Hydro.VERSION);
    /** Reibung, die nichts bremst (Manning-n) [G]. */
    static final double OHNE_REIBUNG = 1e-6;

    public static void main(String[] args) {
        List<String> a = List.of(args);
        int gi = a.indexOf("--gebiet");
        String gebiet = gi >= 0 && gi + 1 < a.size() ? a.get(gi + 1) : null;
        boolean karte = a.contains("--karte");
        int fi = a.indexOf("--fall");
        nurFall = fi >= 0 && fi + 1 < a.size() ? a.get(fi + 1) : null;
        KarteRechner kr = null;
        if (karte) R = kr = new KarteRechner();
        System.out.println("HydroCheck – com.dan.physics.hydro " + Hydro.VERSION + " (Werkbuch HalligHooge Teil A, P4)" + (karte ? ", auf der Karte: " + R.geraet() : ", Referenz"));
        if (karte) teil("10 Referenz gegen Karte", () -> referenzGegenKarte(gebiet));
        teil("1 Ruhender See", () -> ruhenderSee(gebiet));
        teil("2 Thacker-Schüssel", HydroCheck::thacker);
        teil("3 Dammbruch", HydroCheck::dammbruch);
        teil("4 Kelvin-Welle", HydroCheck::kelvin);
        teil("5 Gezeitenwelle im Kanal mit Reibung", HydroCheck::kanal);
        teil("6 Subgrid gegen durchgehend fein", HydroCheck::subgridGegenFein);
        teil("7 Wehr", HydroCheck::wehr);
        teil("8 Siel", HydroCheck::siel);
        teil("11 Windstau", HydroCheck::windstau);
        teil("12 Luftdruck", HydroCheck::luftdruck);
        teil("13 Schachtel", HydroCheck::schachtel);
        teil("15 Ruhender See auf der Kugel", HydroP5::fall15);
        teil("16 Gleichgewichtstide", HydroP5::fall16);
        teil("17 Einseitige Schachtel", HydroP5::fall17);
        teil("18 Speicher", HydroP5::fall18);
        if (karte && gebiet != null) teil("9 Lauf über die Gitterkette", () -> HydroLauf.fall9(gebiet));
        if (karte) teil("14 Zeit auf dem Gerät", () -> {
            zeit(gebiet);
            if (gebiet != null) HydroLauf.fall14(gebiet);
        });
        if (kr != null) kr.schliessen();
        if (HydroLauf.lauf != null && !a.contains("--ohne-bild")) {
            try {
                System.out.println("\nStatus-Bild: " + StatusBild.schreibe(Path.of("docs", "status", "P4_Hydro.png")));
            } catch (Exception e) {
                A.fehler(e);
            }
        }
        if (!a.contains("--ohne-attest") && nurFall == null) {
            Path p = A.schreibe(Path.of("docs", "status", karte ? "P4_HydroKarte.md" : "P4_Hydro.md"), karte ? "P4 – HydroCheck auf der Karte" : "P4 – HydroCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    interface Teil { void lauf() throws Exception; }

    private static String nurFall;

    static void teil(String name, Teil t) {
        if (nurFall != null && !name.startsWith(nurFall + " ")) return;
        A.teil(name);
        try {
            t.lauf();
        } catch (Throwable e) {
            A.fehler(e);
        } finally {
            R.ende();
        }
    }

    // ------------------------------------------------------------------ Rechner

    /** Wo gerechnet wird: Referenz in double auf dem Prozessor oder Betrieb in float auf der Karte ({@code --karte}). */
    interface Rechner {
        /** Übernimmt Einrichtung und Stand des Hydro (nach dem Aufbau und nach jedem Neusetzen des Stands). */
        void start(Hydro h);
        /** Ein Schritt; danach liegt der Stand im Hydro. */
        void schritt(Hydro h, double dt);
        double dtCfl(Hydro h);
        Schachtel kette(Hydro... h);
        boolean karte();
        /** Gibt die Karte nach einem Teil frei. */
        default void ende() { }
        default String geraet() { return "Prozessor"; }
    }

    /** Geschachtelte Gitter, auf dem Prozessor oder der Karte; nach jedem Schritt liegt der Stand in den Hydro. */
    interface Schachtel {
        void schritt(double dt);
        double dtCfl();
        double volumen();
        double abgeschnitten();
        long schritte(int ebene);
    }

    static final Rechner PROZESSOR = new Rechner() {
        public void start(Hydro h) { }
        public void schritt(Hydro h, double dt) { h.schritt(dt); }
        public double dtCfl(Hydro h) { return h.dtCfl(); }
        public Schachtel kette(Hydro... h) {
            Kette k = new Kette(h);
            return new Schachtel() {
                public void schritt(double dt) { k.schritt(dt); }
                public double dtCfl() { return h[0].dtCfl(); }
                public double volumen() { return k.volumen(); }
                public double abgeschnitten() {                       // in den Zellen außen und in jeder Ebene
                    double a = k.abgeschnitten;
                    for (Hydro e : h) a += e.abgeschnitten;
                    return a;
                }
                public long schritte(int e) { return k.schritte[e]; }
            };
        }
        public boolean karte() { return false; }
    };

    static Rechner R = PROZESSOR;

    /** Läuft bis {@code bis} mit Schritten nach CFL (höchstens dtMax) auf dem Rechner. */
    static int lauf(Hydro h, double bis, double dtMax) {
        int n = 0;
        while (h.zeit < bis - 1e-9) {
            R.schritt(h, Math.min(Math.min(dtMax, R.dtCfl(h)), bis - h.zeit));
            n++;
        }
        return n;
    }

    /** Volumen, wie es der Rechner hält: auf der Karte die Volumen der Zellen in float. */
    static double volumen(Hydro h) {
        if (!R.karte()) return h.volumen();
        double s = 0;
        for (double v : h.vol) s += (float) v;
        return s;
    }

    /** Grenze für |u| des ruhenden Sees: Referenz 1e-10, Karte 1e-6 m/s (7.1). */
    static double ruhGrenze() { return R.karte() ? 1e-6 : 1e-10; }

    /** Grenze für ein genaues Volumen: Referenz 1e-12, Karte 1e-5 relativ (wie Fall 9). */
    static double volGrenze() { return R.karte() ? 1e-5 : 1e-12; }

    // ------------------------------------------------------------------ Bau

    /** Ein Gitter mit Subgrid aus einer Höhenfunktion z(x, y) und einem festen Manning-n. */
    static Hydro bauen(Gitter g, int fein, DoubleBinaryOperator z, double n) {
        int w = g.nx * fein, h = g.ny * fein;
        double s = g.dx / fein, sy = g.dy / fein;
        double[] zf = new double[w * h], nf = new double[w * h];
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                zf[i + w * j] = z.applyAsDouble(g.x0 + (i + 0.5) * s, g.y0 + (j + 0.5) * sy);
                nf[i + w * j] = n;
            }
        Subgrid sg = Subgrid.bauen(g, fein, Subgrid.aus(zf, w, h), Subgrid.aus(nf, w, h), Subgrid.STUFEN);
        return new Hydro(g, sg.packen());
    }

    static double tiefe(Hydro h, int c) { return h.vol[c] / h.zellflaeche; }

    /** Kleinste Quadrate a0 + a cos ωt + b sin ωt; gibt {Amplitude, Phase φ} mit η ≈ a0 + Amplitude · sin(ωt + φ). */
    static double[] harmonisch(double[] t, double[] y, int n, double om) {
        double[][] m = new double[3][3];
        double[] r = new double[3];
        for (int k = 0; k < n; k++) {
            double[] b = {1, Math.cos(om * t[k]), Math.sin(om * t[k])};
            for (int i = 0; i < 3; i++) {
                r[i] += b[i] * y[k];
                for (int j = 0; j < 3; j++) m[i][j] += b[i] * b[j];
            }
        }
        double[] x = loese(m, r);
        return new double[] {Math.hypot(x[1], x[2]), Math.atan2(x[1], x[2])};
    }

    static double[] loese(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][];
        for (int i = 0; i < n; i++) m[i] = a[i].clone();
        double[] x = b.clone();
        for (int k = 0; k < n; k++) {
            int p = k;
            for (int i = k + 1; i < n; i++) if (Math.abs(m[i][k]) > Math.abs(m[p][k])) p = i;
            double[] tt = m[k]; m[k] = m[p]; m[p] = tt;
            double ss = x[k]; x[k] = x[p]; x[p] = ss;
            for (int i = k + 1; i < n; i++) {
                double fa = m[i][k] / m[k][k];
                for (int j = k; j < n; j++) m[i][j] -= fa * m[k][j];
                x[i] -= fa * x[k];
            }
        }
        for (int k = n - 1; k >= 0; k--) {
            double s = x[k];
            for (int j = k + 1; j < n; j++) s -= m[k][j] * x[j];
            x[k] = s / m[k][k];
        }
        return x;
    }

    // ------------------------------------------------------------------ 1 Ruhender See

    /** Das erzeugte Gelände von Fall 1: 40 × 40 Zellen zu 20 m, Hügel −3 … +4 m, ein Priel von 12 m. */
    static Hydro seeGelaende() {
        Gitter g = Gitter.utm("See", 32, 470000, 6046000, 20, 40, 40);
        DoubleBinaryOperator z = (x, y) -> {
            double a = (x - 470000) / 800, b = (y - 6046000) / 800;
            double v = 1.5 * Math.sin(5 * a) * Math.cos(4 * b) + 1.2 * Math.sin(17 * a + 3 * b) + 0.6 * Math.cos(31 * b - 7 * a);
            if (Math.abs(y - 6046400 - 50 * Math.sin(a * 9)) < 6) v -= 3;                  // Priel schmaler als eine Zelle
            return v + 0.8;
        };
        return bauen(g, 20, z, 0.025);
    }

    /** Ein Ausschnitt von G3 und seine Beschreibung. */
    record Fenster(Hydro h, String text) { }

    static void ruhenderSee(String gebiet) throws Exception {
        see(seeGelaende(), "erzeugtes Gelände (40 × 40 Zellen zu 20 m, Hügel −3 … +4 m, ein Priel von 12 m)");
        if (gebiet != null) {
            Fenster fe = g3UmHooge(gebiet, !R.karte());
            if (fe != null) see(fe.h(), fe.text());
        }
    }

    /**
     * G3 um den Ring Hooge (der Ring der Sonde mit 10 Zellen Rand) mit seinen Deich- und Sielkanten; mit {@code siele}
     * dazu der Prüfstein „binnen der Siele“ über ganz G3. null, wenn die Packung Version 1 ist (dann ein Verstoß).
     */
    static Fenster g3UmHooge(String gebiet, boolean siele) throws Exception {
        Gebiet ge = Gebiet.lies(new File(gebiet));
        Gitter g3 = ge.gitter("G3");
        Subgrid.Packung p = ge.packung("G3");
        if (p.kantenWerte() < 3) {
            A.pruefe(false, "Ruhender See über G3: die Packung ist Version 1 (ohne Querschnitt)", "neu backen: Aufbereitung --schritte backen", "Version 2");
            return null;
        }
        // Bauwerke und Ringe über ganz G3; das Fenster umfasst den Ring der Sonde Hooge mit 10 Zellen Rand
        Gebiet.Bauten bt = ge.bauwerke("G3");
        boolean[] bw = new boolean[(g3.nx + 1) * g3.ny + g3.nx * (g3.ny + 1)];
        for (Gebiet.Bauten.Kante k : bt.kanten()) bw[k.index()] = true;
        Ringe ri = new Ringe(g3.nx, g3.ny, bw);
        int hooge = ri.flaeche[g3.index(g3.i(HOOGE[0]), g3.j(HOOGE[1]))];
        int[] box = {g3.nx, g3.ny, -1, -1};
        for (int c = 0; c < g3.zellen(); c++)
            if (ri.flaeche[c] == hooge) {
                box[0] = Math.min(box[0], c % g3.nx); box[1] = Math.min(box[1], c / g3.nx);
                box[2] = Math.max(box[2], c % g3.nx); box[3] = Math.max(box[3], c / g3.nx);
            }
        if (siele) sieleHooge(g3, bt, ri, hooge);
        int i0 = Math.max(0, box[0] - 10), j0 = Math.max(0, box[1] - 10), ni = Math.min(g3.nx, box[2] + 11) - i0, nj = Math.min(g3.ny, box[3] + 11) - j0;
        Gitter w = Gitter.utm("G3-Fenster", 32, g3.x0 + i0 * g3.dx, g3.y0 + j0 * g3.dy, g3.dx, ni, nj);
        Hydro hw = new Hydro(w, fenster(p, g3, i0, j0, ni, nj));
        int[] n = bauwerkeImFenster(hw, bt, i0, j0);
        return new Fenster(hw, f("G3 um den Ring Hooge (%d × %d Zellen ab Zelle %d, %d; %d Deich- und %d Sielkanten)", ni, nj, i0, j0, n[0], n[1]));
    }

    /** Sonde im Ring Hooge (Rezept: ring.Hooge). */
    static final double[] HOOGE = {470779, 6047148};

    /**
     * Siele am Ring Hooge (eine Seite im Ring, die andere nicht): binnen muss der Ring sein. Ein Siel mit beiden Seiten in
     * derselben Fläche (Deichstück, das nichts umschließt) hat kein Binnen und bleibt zu; das Wasser fließt dort um ihn herum.
     */
    static void sieleHooge(Gitter g3, Gebiet.Bauten bt, Ringe ri, int hooge) {
        int am = 0, richtig = 0, imRing = 0;
        StringBuilder alle = new StringBuilder();
        for (Gebiet.Bauten.Siel s : bt.siele()) {
            int[] z = ri.zellen(s.kante());
            int b = ri.binnen(s.kante());
            int innen = b > 0 ? z[1] : b < 0 ? z[0] : -1;
            boolean amRing = ri.flaeche[z[0]] == hooge ^ ri.flaeche[z[1]] == hooge;
            if (ri.flaeche[z[0]] == hooge && ri.flaeche[z[1]] == hooge) imRing++;
            if (amRing) {
                am++;
                if (innen >= 0 && ri.flaeche[innen] == hooge) richtig++;
            }
            alle.append(f("%s%s: Tiefe %d/%d%s", alle.length() > 0 ? "; " : "", s.name(), ri.tiefe(z[0]), ri.tiefe(z[1]),
                    ri.flaeche[z[0]] == ri.flaeche[z[1]] ? " (beide Seiten in derselben Fläche: zu)" : innen < 0 ? " (unbestimmt)" : ri.flaeche[innen] == hooge ? " (binnen Hooge)" : ""));
        }
        A.pruefe(am > 0 && richtig == am, "Prüfung A (P4b): Siele am Ring Hooge haben binnen den Ring (Ringe über die Bauwerkskanten von G3)",
                f("%d von %d am Ring richtig; %d im Ring auf einem Deichstück, das nichts umschließt (zu); %d Flächen, Ring Hooge Tiefe %d", richtig, am, imRing, ri.flaechen, ri.tiefe[hooge]), "alle");
        A.info("Binnen aller Siele (Tiefe links/rechts bzw. unten/oben)", alle.toString());
    }

    /** Die Bauwerke von G3, die ganz im Fenster liegen, auf dessen Kanten; gibt {Deiche, Siele}. */
    static int[] bauwerkeImFenster(Hydro h, Gebiet.Bauten bt, int i0, int j0) { return h.bauwerke(bt, i0, j0); }

    static void see(Hydro h, String wo) {
        for (double eta : new double[] {1.0, 3.0}) {
            h.fuellen(eta);
            h.zeit = 0;
            R.start(h);
            double v0 = volumen(h);
            int nass = 0;
            for (int c = 0; c < h.zellen; c++) if (h.vol[c] > 0) nass++;
            double dt = R.dtCfl(h);
            for (int k = 0; k < 1000; k++) R.schritt(h, dt);
            double um = 0;
            for (double x : h.u) um = Math.max(um, Math.abs(x));
            double dv = Math.abs(volumen(h) - v0) / v0;
            A.pruefe(um < ruhGrenze() && dv < 1e-12, f("Prüfung A, Fall 1: ruhender See bei η = %+.0f m, 1000 Schritte zu %.2f s; %s; %d von %d Zellen nass", eta, dt, wo, nass, h.zellen),
                    f("|u| bis %.1e m/s, Volumen %.1e", um, dv), R.karte() ? "< 1e-6 m/s (Karte)" : "< 1e-10 m/s (Referenz)");
        }
    }

    /** Ein Fenster aus einer Packung (Zellen und ihre Kanten). */
    static Subgrid.Packung fenster(Subgrid.Packung p, Gitter g, int i0, int j0, int ni, int nj) { return p.fenster(g, i0, j0, ni, nj); }

    // ------------------------------------------------------------------ 10 Referenz gegen Karte, 14 Zeit

    /** Fall 10 (Grenze A): aus demselben Stand ein Schritt in der Referenz (double) und auf der Karte (float). */
    static void referenzGegenKarte(String gebiet) throws Exception {
        Hydro h = seeGelaende();
        h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
        h.randwert[Hydro.WEST] = (t, x, y) -> Math.sin(2 * Math.PI * t / 3600);
        h.fuellen(0);
        h.lauf(1800, 1e9);
        einSchritt(h, "erzeugtes Gelände aus Fall 1, Tide 1 m mit Periode 1 h am Westrand, nach 30 min");
        if (gebiet == null) return;
        Fenster fe = g3UmHooge(gebiet, false);
        if (fe == null) return;
        Hydro w = fe.h();
        for (int s = 0; s < 4; s++) {
            w.rand[s] = Hydro.Rand.WASSERSTAND;
            w.randwert[s] = (t, x, y) -> 2.8 * Math.sin(2 * Math.PI * t / 44714);
        }
        w.fuellen(0);
        w.lauf(9000, 1e9);
        int ueber = 0;
        for (int k = 0; k < w.bauwerke(); k++) if (w.fluss(w.bauKante(k)) != 0) ueber++;
        einSchritt(w, f("%s, Landunter (Tide 2,8 m, Periode 12,42 h, an allen Seiten) nach 2,5 h, %d Bauwerkskanten mit Fluss", fe.text(), ueber));
        HydroLauf.fall10Kette(gebiet);
        HydroP5Karte.fall10G0(gebiet);
    }

    static void einSchritt(Hydro h, String wo) {
        HydroKarte k = ((KarteRechner) R).karte(h);
        k.hoch();
        double[] tr = h.dtTeile(), tk = k.dtTeile();
        double dt = Math.min(tr[0], tr[1]);
        int nass = 0;
        for (int c = 0; c < h.zellen; c++) if (h.vol[c] > 0) nass++;
        h.schritt(dt);
        k.schritte(1, dt);
        float[] ek = k.etaKarte(), uk = k.uKarte();
        double de = 0, du = 0;
        for (int c = 0; c < h.zellen; c++) de = Math.max(de, Math.abs(ek[c] - h.eta[c]));
        for (int e = 0; e < h.kanten; e++) du = Math.max(du, Math.abs(uk[e] - h.u[e]));
        A.pruefe(de <= 1e-5 && du <= 1e-5, f("Prüfung A, Fall 10: Referenz gegen Karte nach einem Schritt zu %.3f s; %s; %d von %d Zellen nass", dt, wo, nass, h.zellen),
                f("|Δη| bis %.1e m, |Δu| bis %.1e m/s; Δt Welle/Speicher %.4f/%.4f s gegen %.4f/%.4f s", de, du, tr[0], tr[1], tk[0], tk[1]), "≤ 1e-5 (Grenze A)");
    }

    /** Fall 14 zur Auskunft: ns je Zellschritt auf dem Gerät, 50 Schritte in einem Befehlspuffer. */
    static void zeit(String gebiet) throws Exception {
        Fenster fe = gebiet != null ? g3UmHooge(gebiet, false) : null;
        Hydro h = fe != null ? fe.h() : seeGelaende();
        h.fuellen(1.0);
        HydroKarte k = ((KarteRechner) R).karte(h);
        double dt = k.dtCfl();
        k.schritte(5, dt);
        long t0 = System.nanoTime();
        k.schritte(50, dt);
        double ns = (System.nanoTime() - t0) / (50.0 * h.zellen);
        String g = R.geraet(), l = g.toLowerCase();
        boolean software = l.contains("llvmpipe") || l.contains("lavapipe") || l.contains("swiftshader");
        A.info(f("Fall 14: Zeit auf %s, %s, ruhender See bei +1 m, 50 Schritte in einem Befehlspuffer", g, fe != null ? fe.text() : "erzeugtes Gelände"),
                f("%.1f ns je Zellschritt (mit Abschicken und Warten)%s", ns, software ? " – Software-Gerät: ohne Aussage über die Karte" : ""));
    }

    // ------------------------------------------------------------------ 2 Thacker

    /**
     * Thacker in zwei Auflösungen: 200 m (zur Auskunft) und 100 m (Prüfstein), beide mit Pixeln zu 20 m. Das Verfahren ist
     * erster Ordnung; die Dämpfung am wandernden Ufer nimmt mit der Zellgröße ab (12.5, Stand P4a).
     */
    static void thacker() {
        for (int n : new int[] {120, 240}) {
            double[] r = thacker(n);
            String text = f("Prüfung A, Fall 2: Thacker-Schüssel (a = 10 km, h₀ = 10 m, η = 1 km, T = %.0f s), %d² Zellen zu %.0f m mit Subgrid 20 m, ohne Reibung: Neigung des Spiegels nach 3 Perioden (%.0f Schritte)",
                    r[3], n, 24000.0 / n, r[4]);
            String wert = f("Amplitude %.4f %% der Lösung (Anfang %.4f %%), Richtung %.1f°", 100 * r[0], 100 * r[1], r[2]);
            if (n == 120) A.info(text, wert + " – zur Auskunft (Reihe 200, 100 m)");
            else A.pruefe(Math.abs(r[0] - 1) <= 0.02, text, wert, "±2 % [G]");
        }
    }

    /** {Amplitude nach 3 Perioden / Lösung, am Anfang, Richtung (°), T, Schritte}. */
    static double[] thacker(int n) {
        double a = 10000, h0 = 10, e = 1000, g = 9.81, om = Math.sqrt(2 * g * h0) / a, T = 2 * Math.PI / om;
        double xm = 500000, ym = 6000000, d = 24000.0 / n;
        Gitter gi = Gitter.utm("Thacker", 32, xm - 12000, ym - 12000, d, n, n);
        Hydro h = bauen(gi, (int) Math.round(d / 20), (x, y) -> -h0 * (1 - (Math.pow(x - xm, 2) + Math.pow(y - ym, 2)) / (a * a)), OHNE_REIBUNG);
        Arrays.fill(h.f, 0);
        for (int c = 0; c < h.zellen; c++) {
            double x = gi.x(c % n) - xm;
            h.setze(c, e * h0 / (a * a) * (2 * x - e));
        }
        for (int j = 0; j <= n; j++)
            for (int i = 0; i < n; i++) {
                int k = h.kanteY(i, j);
                if (j > 0 && j < n && h.vol[i + n * (j - 1)] > 0 && h.vol[i + n * j] > 0) h.u[k] = e * om;
            }
        double soll = 2 * e * h0 / (a * a);
        double[] s0 = neigung(h, xm, ym, a);
        R.start(h);
        int schritte = lauf(h, 3 * T, 1e9);
        double[] s3 = neigung(h, xm, ym, a);
        if (n == 240) {                                                     // Schnitt durch die Mitte für das Status-Bild
            int j = n / 2;
            StatusBild.thackerX = new double[n];
            StatusBild.thackerEta = new double[n];
            StatusBild.thackerSoll = new double[n];
            StatusBild.thackerGrund = new double[n];
            for (int i = 0; i < n; i++) {
                double x = gi.x(i) - xm, yy = gi.y(j) - ym, z = -h0 * (1 - (x * x + yy * yy) / (a * a)), ls = e * h0 / (a * a) * (2 * x - e);
                int c = i + n * j;
                StatusBild.thackerX[i] = x / 1000;
                StatusBild.thackerGrund[i] = z;
                StatusBild.thackerSoll[i] = ls > z ? ls : Double.NaN;
                StatusBild.thackerEta[i] = h.vol[c] > 0 ? h.eta[c] : Double.NaN;
            }
        }
        return new double[] {Math.hypot(s3[0], s3[1]) / soll, Math.hypot(s0[0], s0[1]) / soll, Math.toDegrees(Math.atan2(s3[1], s3[0])), T, schritte};
    }

    /** Ebene durch η der ganz nassen Zellen innerhalb 0,8 a: {∂η/∂x, ∂η/∂y}. */
    static double[] neigung(Hydro h, double xm, double ym, double a) {
        double[][] m = new double[3][3];
        double[] r = new double[3];
        for (int c = 0; c < h.zellen; c++) {
            double x = h.gitter.x(c % h.nx) - xm, y = h.gitter.y(c / h.nx) - ym;
            if (Math.hypot(x, y) > 0.8 * a || h.benetzt(c, h.eta[c]) < h.zellflaeche) continue;
            double[] b = {1, x, y};
            for (int i = 0; i < 3; i++) {
                r[i] += b[i] * h.eta[c];
                for (int j = 0; j < 3; j++) m[i][j] += b[i] * b[j];
            }
        }
        double[] x = loese(m, r);
        return new double[] {x[1], x[2]};
    }

    // ------------------------------------------------------------------ 3 Dammbruch

    /**
     * Dammbruch in drei Auflösungen (2, 1 und 0,5 m; Prüfstein bei 0,5 m). Die Spitze über trockenem Grund ist in
     * einem Verfahren erster Ordnung um O(Δx) zu langsam (12.5, Stand P4a); die Reihe zeigt, dass der Fehler mit Δx fällt.
     */
    static void dammbruch() {
        double hl = 1, g = 9.81, c0 = Math.sqrt(g * hl), t = 40, x0 = 1000;
        for (double hr : new double[] {0, 0.1}) {
            double[] st = hr > 0 ? stoker(hl, hr, g) : null;
            double ziel = hr == 0 ? 0.01 * hl : 0.5 * (st[0] + hr), soll = hr == 0 ? 1.7 * c0 * t : st[1] * t;
            StringBuilder reihe = new StringBuilder();
            for (double dx : new double[] {2, 1, 0.5}) {
                int n = (int) Math.round(2 * x0 / dx);
                Gitter gi = Gitter.utm("Damm", 32, 0, 0, dx, n, 1);
                Hydro h = bauen(gi, 1, (x, y) -> 0, OHNE_REIBUNG);
                Arrays.fill(h.f, 0);
                for (int c = 0; c < h.zellen; c++) h.setze(c, gi.x(c) < x0 ? hl : hr);
                R.start(h);
                int schritte = lauf(h, t, 1e9);
                double xs = Double.NaN;
                for (int c = h.zellen - 1; c > 0; c--) {
                    double a = tiefe(h, c - 1), b = tiefe(h, c);
                    if (a >= ziel && b < ziel) { xs = gi.x(c - 1) + (a - ziel) / (a - b) * gi.dx; break; }
                }
                double fehler = (xs - x0) / soll - 1;
                if (dx == 0.5) {                                            // Profil für das Status-Bild, ab 300 m vor dem Damm
                    int q = hr == 0 ? 0 : 1, a0 = (int) Math.round((x0 - 300) / dx);
                    double[] px = new double[h.zellen - a0], ph = new double[px.length], ps = new double[px.length];
                    for (int c = a0; c < h.zellen; c++) {
                        double x = gi.x(c) - x0;
                        px[c - a0] = x;
                        ph[c - a0] = tiefe(h, c);
                        ps[c - a0] = hr == 0 ? ritter(x, t, hl, g) : stokerH(x, t, hl, hr, st, g);
                    }
                    StatusBild.dammX[q] = px;
                    StatusBild.dammH[q] = ph;
                    StatusBild.dammSoll[q] = ps;
                }
                if (dx > 0.5) { reihe.append(f("%s%.0f m: %+.2f %%", reihe.length() > 0 ? ", " : "", dx, 100 * fehler)); continue; }
                String text = hr == 0
                        ? f("Prüfung A, Fall 3: Dammbruch nach Ritter (1 m auf trocken), %d Zellen zu %.1f m, ohne Reibung, t = %.0f s (%d Schritte): Lage, wo die Tiefe auf 1 %% von h_L fällt", n, dx, t, schritte)
                        : f("Prüfung A, Fall 3: Dammbruch nach Stoker (1 m auf 0,1 m), %d Zellen zu %.1f m, t = %.0f s (%d Schritte): Lage des Sprungs (Mitte zwischen h_R und h_m = %.4f m)", n, dx, t, schritte, st[0]);
                A.pruefe(Math.abs(fehler) <= 0.02, text, f("%.1f m hinter dem Damm, Lösung %.1f m (%+.2f %%); gröber %s", xs - x0, soll, 100 * fehler, reihe), "±2 % [G]");
            }
        }
    }

    /** Tiefe nach Ritter im Abstand x hinter dem Damm zur Zeit t. */
    static double ritter(double x, double t, double hl, double g) {
        double c0 = Math.sqrt(g * hl);
        if (x <= -c0 * t) return hl;
        if (x >= 2 * c0 * t) return 0;
        double c = (2 * c0 - x / t) / 3;
        return c * c / g;
    }

    /** Tiefe nach Stoker (Verdünnung, h_m, Sprung) im Abstand x hinter dem Damm zur Zeit t; st = {h_m, Geschwindigkeit des Sprungs}. */
    static double stokerH(double x, double t, double hl, double hr, double[] st, double g) {
        double c0 = Math.sqrt(g * hl), cm = Math.sqrt(g * st[0]), um = 2 * (c0 - cm);
        if (x <= -c0 * t) return hl;
        if (x <= (um - cm) * t) {
            double c = (2 * c0 - x / t) / 3;
            return c * c / g;
        }
        return x <= st[1] * t ? st[0] : hr;
    }

    /** Stoker: {h_m, Geschwindigkeit des Sprungs}. */
    static double[] stoker(double hl, double hr, double g) {
        double lo = hr, hi = hl;
        for (int k = 0; k < 200; k++) {
            double hm = 0.5 * (lo + hi);
            double um1 = 2 * (Math.sqrt(g * hl) - Math.sqrt(g * hm)), um2 = (hm - hr) * Math.sqrt(g * (hm + hr) / (2 * hm * hr));
            if (um1 > um2) lo = hm;
            else hi = hm;
        }
        double hm = 0.5 * (lo + hi), um = 2 * (Math.sqrt(g * hl) - Math.sqrt(g * hm));
        return new double[] {hm, hm * um / (hm - hr)};
    }

    // ------------------------------------------------------------------ 4 Kelvin

    static void kelvin() {
        double H = 10, g = 9.81, f0 = 1e-4, c = Math.sqrt(g * H), rr = c / f0, T = 7200, om = 2 * Math.PI / T, amp = 0.1;
        int nx = 150, ny = 40;
        double d = 2000;
        Gitter gi = Gitter.utm("Kelvin", 32, 400000, 6000000, d, nx, ny);
        Hydro h = bauen(gi, 2, (x, y) -> -H, OHNE_REIBUNG);
        Arrays.fill(h.f, f0);
        h.fuellen(0);
        h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
        h.randwert[Hydro.WEST] = (t, x, y) -> amp * Math.exp(-(y - 6000000) / rr) * Math.sin(om * t) * Math.min(1, t / T);
        int spalte = 30, m = 0;
        double[] ts = new double[4000], ys = new double[4000], yn = new double[4000];
        double[][] quer = new double[ny][4000];
        R.start(h);
        double dt = R.dtCfl(h);
        while (h.zeit < 4 * T - 1e-9) {
            R.schritt(h, Math.min(dt, 4 * T - h.zeit));
            if (h.zeit >= 2 * T) {
                ts[m] = h.zeit;
                ys[m] = h.eta[spalte];
                yn[m] = h.eta[spalte + nx * (ny - 1)];
                for (int j = 0; j < ny; j++) quer[j][m] = h.eta[spalte + nx * j];
                m++;
            }
        }
        double as = harmonisch(ts, ys, m, om)[0], an = harmonisch(ts, yn, m, om)[0];
        StatusBild.kelvinY = new double[ny];
        StatusBild.kelvinA = new double[ny];
        StatusBild.kelvinSoll = new double[ny];
        for (int j = 0; j < ny; j++) {
            StatusBild.kelvinY[j] = j * d / 1000;
            StatusBild.kelvinA[j] = harmonisch(ts, quer[j], m, om)[0];
            StatusBild.kelvinSoll[j] = as * Math.exp(-j * d / rr);
        }
        double soll = Math.exp(-(ny - 1) * d / rr), ist = an / as;
        A.pruefe(Math.abs(ist / soll - 1) <= 0.03, f("Prüfung A, Fall 4: Kelvin-Welle im Kanal 300 × 80 km, h = 10 m, f = 1·10⁻⁴ 1/s, T = 2 h, ohne Reibung; Verhältnis der Amplituden an der Nord- und Südwand bei x = %.0f km (Stunde 4–8)", (spalte + 0.5) * d / 1000),
                f("%.4f, Lösung exp(−y/R) = %.4f (%+.2f %%); Süd %.4f m", ist, soll, 100 * (ist / soll - 1), as), "±3 % [G]");
    }

    // ------------------------------------------------------------------ 5 Kanal

    static void kanal() {
        double H = 10, n = 0.03, g = 9.81, L = 60000, d = 1000, amp = 0.2, T = 44712, om = 2 * Math.PI / T;
        int nx = (int) (L / d);
        Gitter gi = Gitter.utm("Kanal", 32, 400000, 6000000, d, nx, 1);
        Hydro h = bauen(gi, 4, (x, y) -> -H, n);
        Arrays.fill(h.f, 0);
        h.fuellen(0);
        h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
        h.randwert[Hydro.WEST] = (t, x, y) -> amp * Math.sin(om * t) * Math.min(1, t / T);
        int m = 0;
        double[] ts = new double[100000], ys = new double[100000];
        R.start(h);
        double dt = Math.min(R.dtCfl(h), 60);
        while (h.zeit < 6 * T - 1e-9) {
            R.schritt(h, Math.min(dt, 6 * T - h.zeit));
            if (h.zeit >= 4 * T) {
                ts[m] = h.zeit;
                ys[m] = h.eta[nx - 1];
                m++;
            }
        }
        double[] ist = harmonisch(ts, ys, m, om);
        // lineare Lösung mit Reibung nach Lorentz, Antrieb an der Geisterzelle (x = −Δx/2), Ende bei x = L
        double xe = L - d / 2, lp = L + d / 2, r = 1e-4;
        double[] loes = null;
        for (int it = 0; it < 100; it++) {
            double[] k = kanalK(om, g, H, r);
            double s2 = 0, s3 = 0;
            for (int q = 0; q <= 200; q++) {
                double x = -d / 2 + lp * q / 200;
                double[] st = sin(mul(k, new double[] {lp - (x + d / 2), 0}));        // sin(k (L' − x'))
                double[] deta = div(mul(new double[] {amp, 0}, mul(k, st)), cos(mul(k, new double[] {lp, 0})));
                double[] uu = div(mul(new double[] {g, 0}, deta), new double[] {r, om});
                double ua = Math.hypot(uu[0], uu[1]);
                s2 += ua * ua;
                s3 += ua * ua * ua;
            }
            double cf = g * n * n / Math.cbrt(H), neu = 8 / (3 * Math.PI) * cf * s3 / s2 / H;
            if (Math.abs(neu - r) < 1e-12) { r = neu; break; }
            r = neu;
        }
        double[] k = kanalK(om, g, H, r);
        double[] z = div(cos(mul(k, new double[] {L - xe, 0})), cos(mul(k, new double[] {lp, 0})));
        double sa = amp * Math.hypot(z[0], z[1]), sp = Math.atan2(z[1], z[0]);
        int sch = Math.max(1, m / 400);                                     // Zeitreihe für das Status-Bild
        StatusBild.kanalT = new double[m / sch];
        StatusBild.kanalEta = new double[m / sch];
        StatusBild.kanalSoll = new double[m / sch];
        for (int q = 0; q < m / sch; q++) {
            StatusBild.kanalT[q] = ts[q * sch] / 3600;
            StatusBild.kanalEta[q] = ys[q * sch];
            StatusBild.kanalSoll[q] = sa * Math.sin(om * ts[q * sch] + sp);
        }
        double dphi = Math.toDegrees(Math.IEEEremainder(ist[1] - sp, 2 * Math.PI));
        A.pruefe(Math.abs(ist[0] / sa - 1) <= 0.03 && Math.abs(dphi) <= 5, f("Prüfung A, Fall 5: Gezeitenwelle (M2, 0,2 m) im Kanal 60 km, h = 10 m, n = 0,03, am geschlossenen Ende gegen die lineare Lösung (Reibung nach Lorentz, r = %.2e 1/s); Perioden 5 und 6", r),
                f("Amplitude %.4f m gegen %.4f m (%+.2f %%), Phase %+.2f° daneben", ist[0], sa, 100 * (ist[0] / sa - 1), dphi), "±3 %, ±5° [G]");
    }

    /** k = (ω/c) √(1 − i r/ω) als {Re, Im}. */
    static double[] kanalK(double om, double g, double h, double r) {
        double[] w = csqrt(new double[] {1, -r / om});
        double s = om / Math.sqrt(g * h);
        return new double[] {s * w[0], s * w[1]};
    }

    static double[] mul(double[] a, double[] b) { return new double[] {a[0] * b[0] - a[1] * b[1], a[0] * b[1] + a[1] * b[0]}; }

    static double[] div(double[] a, double[] b) {
        double d = b[0] * b[0] + b[1] * b[1];
        return new double[] {(a[0] * b[0] + a[1] * b[1]) / d, (a[1] * b[0] - a[0] * b[1]) / d};
    }

    static double[] cos(double[] z) { return new double[] {Math.cos(z[0]) * Math.cosh(z[1]), -Math.sin(z[0]) * Math.sinh(z[1])}; }

    static double[] sin(double[] z) { return new double[] {Math.sin(z[0]) * Math.cosh(z[1]), Math.cos(z[0]) * Math.sinh(z[1])}; }

    static double[] csqrt(double[] z) {
        double r = Math.hypot(z[0], z[1]), a = Math.sqrt((r + z[0]) / 2), b = Math.copySign(Math.sqrt((r - z[0]) / 2), z[1]);
        return new double[] {a, b};
    }

    // ------------------------------------------------------------------ 6 Subgrid gegen fein

    static void subgridGegenFein() {
        double lx = 200, ly = 60;
        DoubleBinaryOperator z = (x, y) -> Math.abs(y - 30) < 3 ? -1.0 : 0.5;
        double[] q = new double[2], bil = new double[2];
        int[] zahl = new int[2];
        int k = 0;
        for (int fein : new int[] {1, 20}) {
            double d = fein == 1 ? 1 : 20;
            Gitter gi = Gitter.utm("Rinne", 32, 0, 0, d, (int) (lx / d), (int) (ly / d));
            Hydro h = bauen(gi, fein, (x, y) -> z.applyAsDouble(x, y), 0.025);
            Arrays.fill(h.f, 0);
            h.fuellen(0.2);
            h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
            h.rand[Hydro.OST] = Hydro.Rand.WASSERSTAND;
            h.randwert[Hydro.WEST] = (t, x, y) -> 0.3;
            h.randwert[Hydro.OST] = (t, x, y) -> 0.1;
            double v0 = h.volumen();
            R.start(h);
            lauf(h, 400, 1e9);
            int mitte = h.nx / 2, n = 0;
            double s = 0;
            while (h.zeit < 500 - 1e-9) {
                R.schritt(h, Math.min(R.dtCfl(h), 500 - h.zeit));
                double qq = 0;
                for (int j = 0; j < h.ny; j++) {
                    int e = h.kanteX(mitte, j), a = j * h.nx + mitte - 1;
                    double hh = h.u[e] > 0 ? h.eta[a] : h.eta[a + 1];
                    qq += h.u[e] * h.laenge(e) * h.hA(e, hh);
                }
                s += qq;
                n++;
            }
            q[k] = s / n;
            double rest = h.volumen() - v0 - (h.zufluss - h.abfluss + h.abgeschnitten);    // Abgeschnittenes ist entstandenes Wasser (Vorzeichen berichtigt in P4d)
            bil[k] = Math.abs(rest) / Math.max(h.volumen(), h.zufluss + h.abfluss);
            zahl[k] = h.zellen;
            k++;
        }
        A.pruefe(Math.abs(q[1] / q[0] - 1) <= 0.05, f("Prüfung A, Fall 6: Rinne 6 m breit (Sohle −1 m) zwischen Watt (+0,5 m), 200 × 60 m, Wasserstand 0,3 m West, 0,1 m Ost, n = 0,025: Durchfluss in der Mitte, Mittel über die Sekunden 400–500; Zellen 20 m mit Subgrid 1 m (%d Zellen) gegen durchgehend 1 m (%d Zellen)",
                zahl[1], zahl[0]), f("%.4f m³/s gegen %.4f m³/s (%+.2f %%)", q[1], q[0], 100 * (q[1] / q[0] - 1)), "±5 % [G]");
        A.pruefe(bil[0] <= volGrenze() && bil[1] <= volGrenze(), "Prüfung A, Fall 6: Volumen gegen Zu- und Abfluss über die Ränder (Bilanz)", f("Rest fein %.1e, Subgrid %.1e", bil[0], bil[1]),
                R.karte() ? "≤ 1e-5 relativ (Karte)" : "≤ 1e-12 relativ");
    }

    // ------------------------------------------------------------------ 11, 12 Windstau und Luftdruck

    static Hydro becken() {
        Gitter gi = Gitter.utm("Becken", 32, 0, 0, 500, 40, 4);
        Hydro h = bauen(gi, 5, (x, y) -> -5, 0.025);
        Arrays.fill(h.f, 0);
        h.fuellen(0);
        return h;
    }

    /** Neigung des Spiegels über die mittlere Hälfte (Zeilen gemittelt) und die mittlere Tiefe dort. */
    static double[] spiegel(Hydro h) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0, tief = 0;
        int n = 0;
        for (int i = h.nx / 4; i < 3 * h.nx / 4; i++) {
            double e = 0;
            for (int j = 0; j < h.ny; j++) e += h.eta[i + h.nx * j];
            e /= h.ny;
            double x = h.gitter.x(i);
            sx += x; sy += e; sxx += x * x; sxy += x * e; n++;
            tief += e + 5;
        }
        return new double[] {(n * sxy - sx * sy) / (n * sxx - sx * sx), tief / n};
    }

    /** Anfahren ohne Knick: ½ (1 − cos(π t / T)) bis T, dann 1 (regt die Eigenschwingung des Beckens kaum an). */
    static double rampe(double t, double T) { return t >= T ? 1 : 0.5 * (1 - Math.cos(Math.PI * t / T)); }

    /**
     * Windstau: Wind über 6 h ohne Knick angefahren, Gefälle gemittelt über die Stunden 18–24 (die Eigenschwingung des
     * Beckens, Periode 1,6 h, ist kaum gedämpft; gemessen wird der stationäre Stau, 12.5, Stand P4a).
     */
    static void windstau() {
        Hydro h = becken();
        double u10 = 15, anfahren = 6 * 3600, ende = 24 * 3600, von = 18 * 3600;
        h.windX = new double[h.zellen];
        h.windY = new double[h.zellen];
        double cd = (0.8 + 0.065 * u10) * 1e-3, tau = h.rhoL * cd * u10 * u10;
        R.start(h);
        double dt = R.dtCfl(h), summe = 0, tief = 0;
        int k = 0;
        while (h.zeit < ende - 1e-9) {
            Arrays.fill(h.windX, u10 * rampe(h.zeit, anfahren));
            R.schritt(h, Math.min(dt, ende - h.zeit));
            if (h.zeit > von) {
                double[] s = spiegel(h);
                summe += s[0];
                tief += s[1];
                k++;
            }
        }
        double gef = summe / k, soll = tau / (h.rhoW * h.g * tief / k);
        A.pruefe(Math.abs(gef / soll - 1) <= 0.02, f("Prüfung A, Fall 11: Windstau im geschlossenen Becken 20 × 2 km, 5 m tief, n = 0,025, Wind 15 m/s (über 6 h ohne Knick angefahren): Gefälle in der mittleren Hälfte, Mittel der Stunden 18–24, gegen τ / (ρ_W g h), C_d nach Wu = %.3e", cd),
                f("%.4e gegen %.4e (%+.2f %%)", gef, soll, 100 * (gef / soll - 1)), "±2 % [G]");
    }

    /** Umgekehrtes Barometer: Druckgefälle über 6 h ohne Knick angefahren, Gefälle gemittelt über die Stunden 18–24. */
    static void luftdruck() {
        Hydro h = becken();
        double dp = 1000, anfahren = 6 * 3600, ende = 24 * 3600, von = 18 * 3600, lx = h.nx * h.dx;
        h.druck = new double[h.zellen];
        for (int c = 0; c < h.zellen; c++) h.druck[c] = 101325;
        R.start(h);
        double dt = R.dtCfl(h), summe = 0;
        int k = 0;
        while (h.zeit < ende - 1e-9) {
            double a = rampe(h.zeit, anfahren);
            for (int c = 0; c < h.zellen; c++) h.druck[c] = 101325 + a * dp * h.gitter.x(c % h.nx) / lx;
            R.schritt(h, Math.min(dt, ende - h.zeit));
            if (h.zeit > von) {
                summe += spiegel(h)[0];
                k++;
            }
        }
        double gef = summe / k, soll = -dp / (h.rhoW * h.g * lx);
        A.pruefe(Math.abs(gef / soll - 1) <= 0.01, "Prüfung A, Fall 12: umgekehrtes Barometer im Becken 20 × 2 km, Druckunterschied 1 000 Pa (über 6 h ohne Knick angefahren): Gefälle, Mittel der Stunden 18–24, gegen −Δp / (ρ_W g L)",
                f("%.4e gegen %.4e (%+.2f %%)", gef, soll, 100 * (gef / soll - 1)), "±1 % [G]");
    }

    // ------------------------------------------------------------------ 7 Wehr

    /** Poleni je Stelle mit Villemonte, unabhängig von {@code Hydro} gerechnet (m³/s). */
    static double poleni(float[] krone, double ds, double ho, double hu, double mu, double g) {
        double q = 0;
        for (float z : krone) {
            double h = ho - z;
            if (h <= 0) continue;
            double r = Math.max(0, hu - z) / h;
            q += 2.0 / 3 * mu * Math.sqrt(2 * g) * ds * Math.pow(h, 1.5) * Math.pow(1 - Math.pow(r, 1.5), 0.385);
        }
        return q;
    }

    /** Wehr in einer Rinne: frei und gestaut, Fluss gegen Poleni bzw. Villemonte mit den Wasserständen fünf Zellen ober- und unterhalb. */
    static void wehr() {
        float[] krone = new float[20];
        for (int s = 0; s < 20; s++) krone[s] = s < 10 ? 0.5f : 0.8f;
        for (double unten : new double[] {-1.0, 0.9}) {
            Gitter gi = Gitter.utm("Wehr", 32, 0, 0, 20, 30, 1);
            Hydro h = bauen(gi, 20, (x, y) -> -2, 0.025);
            Arrays.fill(h.f, 0);
            int e = h.kanteX(15, 0);
            int k = h.deich(e, krone);
            h.binnen();
            h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
            h.randwert[Hydro.WEST] = (t, x, y) -> 1.2;
            h.rand[Hydro.OST] = Hydro.Rand.WASSERSTAND;
            h.randwert[Hydro.OST] = (t, x, y) -> unten;
            for (int c = 0; c < h.zellen; c++) h.setze(c, c < 15 ? 1.2 : unten);
            double ende = 7200, von = ende - 600, q = 0, eo = 0, eu = 0, zeit = 0;
            R.start(h);
            while (h.zeit < ende - 1e-9) {
                double dt = Math.min(R.dtCfl(h), ende - h.zeit);
                R.schritt(h, dt);
                if (h.zeit > von) {
                    q += h.fluss(e) * dt;
                    eo += h.eta[10] * dt;
                    eu += h.eta[19] * dt;
                    zeit += dt;
                }
            }
            q /= zeit;
            eo /= zeit;
            eu /= zeit;
            double soll = poleni(krone, 1.0, eo, eu, h.muPoleni, h.g);
            boolean frei = eu < 0.5;
            A.pruefe(Math.abs(q / soll - 1) <= 0.02, f("Prüfung A, Fall 7: Wehr in einer Rinne aus 30 Zellen zu 20 m (Sohle −2 m, n = 0,025), Krone 0,5 m an 10 und 0,8 m an 10 Stellen, Oberwasser 1,2 m, Unterwasser %+.1f m (%s): Fluss über die Minuten 110–120 gegen %s mit η fünf Zellen ober- und unterhalb (%.4f und %.4f m)",
                    unten, frei ? "frei" : "gestaut", frei ? "Poleni" : "Poleni mit Villemonte", eo, eu), f("%.4f gegen %.4f m³/s (%+.2f %%)", q, soll, 100 * (q / soll - 1)), "±2 % [G]");
            if (k != 0) throw new IllegalStateException("Wehr: Bauwerk " + k);
        }
    }

    // ------------------------------------------------------------------ 8 Siel

    /** Siel in einem Koog: binnen, nie Zustrom, Abfluss gegen μ A √(2 g Δh). */
    static void siel() {
        int nx = 12, ny = 7;
        double sohle = -0.3, aS = 1, mu = 0.6, T = 7200;
        Gitter gi = Gitter.utm("Koog", 32, 0, 0, 20, nx, ny);
        java.util.function.IntPredicate koog = c -> c % nx >= 7 && c % nx <= 10 && c / nx >= 2 && c / nx <= 4;
        Hydro h = bauen(gi, 20, (x, y) -> x >= 140 && x < 220 && y >= 40 && y < 100 ? -0.5 : -2, 0.025);
        Arrays.fill(h.f, 0);
        float[] krone = new float[20];
        Arrays.fill(krone, 3.0f);
        int siel = -1;
        for (int j = 2; j <= 4; j++) {
            if (j == 3) siel = h.siel(h.kanteX(7, j), krone, sohle, aS, mu, "Siel");
            else h.deich(h.kanteX(7, j), krone);
            h.deich(h.kanteX(11, j), krone);
        }
        for (int i = 7; i <= 10; i++) {
            h.deich(h.kanteY(i, 2), krone);
            h.deich(h.kanteY(i, 5), krone);
        }
        int offen = h.binnen(), innen = h.sielBinnenZelle(siel), aussen = 6 + nx * 3;
        A.pruefe(offen == 0 && innen == 7 + nx * 3, "Prüfung A, Fall 8: Binnen des Siels aus den Ringen (Koog aus 4 × 3 Zellen, ringsum Deichkanten)",
                f("binnen Zelle %d, %d (Tiefe %d), außen Tiefe %d", innen % nx, innen / nx, h.ringe().tiefe(innen), h.ringe().tiefe(aussen)), "die Zelle im Koog");
        h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
        h.randwert[Hydro.WEST] = (t, x, y) -> 0.8 * Math.sin(2 * Math.PI * t / T);
        for (int c = 0; c < h.zellen; c++) h.setze(c, koog.test(c) ? 0.5 : 0);
        double zu = 0, rund = 0, abw = 0;
        int geprueft = 0, offenS = 0, es = h.bauKante(siel);
        double hinein = innen == h.rechts(es) ? 1 : -1;                   // Vorzeichen: Fluss in den Koog
        R.start(h);
        while (h.zeit < 2 * T - 1e-9) {
            double dt = Math.min(R.dtCfl(h), 2 * T - h.zeit);
            double ein = h.eta[innen], aus = h.eta[aussen], v0 = 0, v1 = 0;
            for (int c = 0; c < h.zellen; c++) if (koog.test(c)) v0 += h.vol[c];
            R.schritt(h, dt);
            for (int c = 0; c < h.zellen; c++) if (koog.test(c)) v1 += h.vol[c];
            zu = Math.max(zu, hinein * h.fluss(es));
            if (v1 > v0) rund = Math.max(rund, v1 - v0);
            double dh = ein - Math.max(aus, sohle);
            if (ein > aus && dh >= 0.05) {
                double soll = mu * aS * Math.sqrt(2 * h.g * dh), ist = (v0 - v1) / dt;
                abw = Math.max(abw, Math.abs(ist / soll - 1));
                geprueft++;
            }
            if (v1 < v0) offenS++;
        }
        A.pruefe(zu == 0, "Prüfung A, Fall 8: Siel (Sohle −0,3 m, A = 1 m², μ = 0,6) im Koog, Tide 0,8 m mit Periode 2 h am Westrand, Koog anfangs 0,5 m, 4 h: größter Fluss über die Sielkante in den Koog",
                f("%.1e m³/s (Siel offen in %d Schritten; Summe des Koogvolumens nimmt höchstens um %.1e m³ zu, Rundung über 12 Zellen)", zu, offenS, rund), "0 (genau)");
        A.pruefe(geprueft > 0 && abw <= 0.01, "Prüfung A, Fall 8: Abfluss aus der Änderung des Koogvolumens je Schritt gegen μ A √(2 g Δh) aus dem Stand am Anfang, wo Δh ≥ 5 cm",
                f("größte Abweichung %.2e über %d Schritte", abw, geprueft), "±1 % [G]");
    }

    // ------------------------------------------------------------------ 13 Schachtel

    /** Ebene Welle durch die Grenze 200 m / 20 m: Volumen über beide Gitter, Rückwurf gegen den Lauf ohne feines Gitter. */
    static void schachtel() {
        double tief = 10, a0 = 0.1, x0 = 8000, sigma = 1500, ende = 3200;
        DoubleBinaryOperator buckel = (x, y) -> a0 * Math.exp(-(x - x0) * (x - x0) / (2 * sigma * sigma));
        Gitter gg = Gitter.utm("Grob", 32, 0, 0, 200, 200, 12), gf = Gitter.utm("Fein", 32, 80 * 200, 3 * 200, 20, 400, 60);
        Hydro grob = bauen(gg, 1, (x, y) -> -tief, OHNE_REIBUNG), fein = bauen(gf, 1, (x, y) -> -tief, OHNE_REIBUNG), ref = bauen(gg, 1, (x, y) -> -tief, OHNE_REIBUNG);
        for (Hydro h : new Hydro[] {grob, fein, ref}) {
            Arrays.fill(h.f, 0);
            for (int c = 0; c < h.zellen; c++) h.setze(c, buckel.applyAsDouble(h.gitter.x(c % h.nx), h.gitter.y(c / h.nx)));
        }
        Schachtel k = R.kette(grob, fein);
        R.start(ref);
        double v0 = k.volumen(), rueck = 0, durch = 0, spitzeN = 0, spitzeR = 0;
        int vor = 60, hinter = 140;
        while (grob.zeit < ende - 1e-9) {
            double dt = Math.min(Math.min(k.dtCfl(), R.dtCfl(ref)), ende - grob.zeit);
            k.schritt(dt);
            R.schritt(ref, dt);
            double an = 0, ar = 0, bn = 0, br = 0;
            for (int j = 0; j < gg.ny; j++) {
                an += grob.eta[vor + gg.nx * j] / gg.ny;
                ar += ref.eta[vor + gg.nx * j] / gg.ny;
                bn += grob.eta[hinter + gg.nx * j] / gg.ny;
                br += ref.eta[hinter + gg.nx * j] / gg.ny;
            }
            rueck = Math.max(rueck, Math.abs(an - ar));
            durch = Math.max(durch, Math.abs(bn - br));
            spitzeN = Math.max(spitzeN, bn);
            spitzeR = Math.max(spitzeR, br);
        }
        double dv = Math.abs(k.volumen() - v0) / v0, amp = a0 / 2;
        A.pruefe(dv <= volGrenze(), f("Prüfung A, Fall 13: Schachtel 200 m / 20 m (Becken 40 × 2,4 km, 10 m tief, fein 8 × 1,2 km in der Mitte), Buckel 0,1 m, %.0f s (%d grobe und %d feine Schritte): Volumen über beide Gitter, relativ", ende, k.schritte(0), k.schritte(1)),
                f("%.1e (abgeschnitten %.1e m³)", dv, k.abgeschnitten()), R.karte() ? "≤ 1e-5 (Karte)" : "≤ 1e-12");
        A.pruefe(rueck / amp <= 0.05, "Prüfung A, Fall 13: Rückwurf an der Sonde 4 km vor der Grenze (Zeilenmittel): größter Unterschied zum Lauf ohne feines Gitter, relativ zur einlaufenden Amplitude 0,05 m",
                f("%.2f %% (%.2e m)", 100 * rueck / amp, rueck), "≤ 5 % [G]");
        A.info("Fall 13: durchgelaufener Puls 4 km hinter dem feinen Gitter", f("größter Unterschied %.2f %% der Amplitude; Spitze %.5f m gegen %.5f m ohne feines Gitter", 100 * durch / amp, spitzeN, spitzeR));
        ruhendeSchachtel();
        rauschendeSchachtel();
    }

    /**
     * Rauschen in der Schachtel (Befund P4c): grob 20 × 10 Zellen zu 200 m, fein 100 × 40 zu 20 m, 10 m tief, ohne
     * Reibung; im feinen Gitter ein Rauschen von ±0,5 mm, 4 h. Gemessen wird das Flächenmittel von η² über beide
     * Gitter (ohne grob unter fein), als Wurzel; es darf nicht wachsen.
     */
    static void rauschendeSchachtel() {
        double tief = 10, ende = 4 * 3600;
        Hydro grob = bauen(Gitter.utm("Grob", 32, 0, 0, 200, 20, 10), 1, (x, y) -> -tief, OHNE_REIBUNG);
        Hydro fein = bauen(Gitter.utm("Fein", 32, 5 * 200, 3 * 200, 20, 100, 40), 1, (x, y) -> -tief, OHNE_REIBUNG);
        for (Hydro h : new Hydro[] {grob, fein}) {
            Arrays.fill(h.f, 0);
            h.fuellen(0);
        }
        java.util.Random zufall = new java.util.Random(1);
        for (int c = 0; c < fein.zellen; c++) fein.setze(c, 1e-3 * (zufall.nextDouble() - 0.5));
        Kette lage = new Kette(grob, fein);                            // nur für die Lage (grob unter fein)
        Schachtel k = R.kette(grob, fein);
        double r0 = rauschen(lage, grob, fein), rmax = r0, dv0 = k.volumen();
        while (grob.zeit < ende - 1e-9) {
            k.schritt(Math.min(k.dtCfl(), ende - grob.zeit));
            rmax = Math.max(rmax, rauschen(lage, grob, fein));
        }
        double r1 = rauschen(lage, grob, fein);
        A.pruefe(rmax <= 2 * r0, f("Prüfung A, Fall 13: Rauschen ±0,5 mm im feinen Gitter der Schachtel (grob 20 × 10 zu 200 m, fein 100 × 40 zu 20 m, 10 m tief, ohne Reibung), 4 h (%d grobe und %d feine Schritte): größtes Flächenmittel √(η²) über die Zeit, relativ zum Anfang", k.schritte(0), k.schritte(1)),
                f("%.2f (am Ende %.2f; Volumen %.1e)", rmax / r0, r1 / r0, Math.abs(k.volumen() - dv0) / dv0), "≤ 2 [G]");
    }

    /** Wurzel aus dem Flächenmittel von η² über beide Gitter, ohne die groben Zellen unter dem feinen. */
    private static double rauschen(Kette lage, Hydro grob, Hydro fein) {
        double s = 0, a = 0;
        for (int c = 0; c < grob.zellen; c++) if (!lage.unter(0, c)) { s += grob.eta[c] * grob.eta[c] * grob.zellflaeche; a += grob.zellflaeche; }
        for (int c = 0; c < fein.zellen; c++) { s += fein.eta[c] * fein.eta[c] * fein.zellflaeche; a += fein.zellflaeche; }
        return Math.sqrt(s / a);
    }

    /** Ruhender See durch die Schachtel: Gelände mit Hügeln und Priel, grob 200 m (Subgrid 10 m), fein 20 m (Subgrid 1 m). */
    static void ruhendeSchachtel() {
        DoubleBinaryOperator z = (x, y) -> {
            double a = x / 800, b = y / 800;
            double v = 1.5 * Math.sin(5 * a) * Math.cos(4 * b) + 1.2 * Math.sin(17 * a + 3 * b) + 0.6 * Math.cos(31 * b - 7 * a);
            if (Math.abs(y - 1200 - 50 * Math.sin(a * 9)) < 6) v -= 3;
            return v + 0.8;
        };
        for (double eta : new double[] {1.0, 3.0}) {
            Hydro grob = bauen(Gitter.utm("Grob", 32, 0, 0, 200, 12, 12), 20, z, 0.025);
            Hydro fein = bauen(Gitter.utm("Fein", 32, 600, 600, 20, 60, 60), 20, z, 0.025);
            grob.fuellen(eta);
            fein.fuellen(eta);
            Schachtel k = R.kette(grob, fein);
            double v0 = k.volumen(), dt = k.dtCfl();
            for (int n = 0; n < 100; n++) k.schritt(dt);
            double um = 0;
            for (Hydro h : new Hydro[] {grob, fein}) for (double x : h.u) um = Math.max(um, Math.abs(x));
            double dv = Math.abs(k.volumen() - v0) / v0;
            A.pruefe(um < ruhGrenze() && dv < volGrenze(), f("Prüfung A, Fall 13: ruhender See bei η = %+.0f m durch die Schachtel (grob 12² Zellen zu 200 m, fein 60² zu 20 m, Hügel und Priel), 100 grobe Schritte zu %.1f s (%d feine)", eta, dt, k.schritte(1)),
                    f("|u| bis %.1e m/s, Volumen %.1e", um, dv), R.karte() ? "< 1e-6 m/s (Karte)" : "< 1e-10 m/s (wie Fall 1)");
        }
    }
}
