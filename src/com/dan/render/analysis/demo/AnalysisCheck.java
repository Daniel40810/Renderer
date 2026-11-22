package com.dan.render.analysis.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Geraet;
import com.dan.core.pruef.Attest;
import com.dan.render.analysis.Isolinien;
import com.dan.render.analysis.Messblick;
import com.dan.render.analysis.Querschnitt;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung von render.analysis ohne Gebiet (P11c2, Werkbuch HalligHooge Teil A, 12.12; Prüfsteine 17 und 18 aus Datei 55):
 * <ol>
 * <li>Isolinien (Java): Ebene gegen die Formel; Kegel gegen den linearen Schnitt auf der Kante (das verspricht Marching
 * Squares) und gegen den Kreis; geschlossene Linien; Lücken; Douglas und Peucker; Zeit auf einem Gitter wie G3.</li>
 * <li>Partikel auf der Karte ({@code partikel.comp}): gleichförmige Strömung und starrer Wirbel gegen die Formel, gegen
 * Runge-Kutta 2 in double; neu gesetzte Partikel nur auf Wasser.</li>
 * <li>Querschnitt (Java, seit P11c3): Durchfluss durch die Treppe gegen die Formel, geschlossener Weg, Profil, Bilanz.</li>
 * </ol>
 * Aufruf: {@code AnalysisCheck [--ohne-karte]}; Attest {@code docs/status/P11c2_Analysis.md}.
 */
public final class AnalysisCheck {
    private static final Attest A = new Attest("com.dan.render.analysis", Messblick.VERSION);

    public static void main(String[] args) throws Exception {
        boolean karte = true;
        for (String a : args) if (a.equals("--ohne-karte")) karte = false;
        System.out.println("AnalysisCheck – com.dan.render.analysis " + Messblick.VERSION + " (Werkbuch HalligHooge Teil A, P11c2)");
        try {
            teil1();
            teil3();
            if (karte) teil2();
            else A.info("Teil 2", "ohne Karte (--ohne-karte): entfällt");
        } catch (Throwable x) {
            A.fehler(x);
        }
        Path at = A.schreibe(Path.of("docs", "status", "P11c2_Analysis.md"), "P11c2 – render.analysis ohne Gebiet");
        if (at != null) System.out.println("\nAttest: " + at);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1 Isolinien

    static void teil1() {
        A.teil("1 Isolinien (Prüfstein 18)");
        int nx = 600, ny = 500;
        float[] f = new float[nx * ny];
        // Ebene
        double ax = 0.37, ay = -0.21, c0 = 3;
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) f[i + nx * j] = (float) (ax * (i + 0.5) + ay * (j + 0.5) + c0);
        double groesste = 0;
        int punkte = 0, linien = 0;
        for (double st = 10; st < 150; st += 13.7)
            for (Isolinien.Linie l : Isolinien.linien(f, null, nx, ny, st)) {
                linien++;
                for (int k = 0; k < l.punkte(); k++) {
                    groesste = Math.max(groesste, Math.abs(ax * l.x[k] + ay * l.y[k] + c0 - st) / Math.hypot(ax, ay));
                    punkte++;
                }
            }
        A.pruefe(groesste <= 1e-3 && linien == 11, "Ebene: Abstand der Punkte von der wahren Linie", f("größter %.2e Zellen; %d Linien, %d Punkte", groesste, linien, punkte), "≤ 1 · 10⁻³ Zelle [G]; 11 Linien");
        // Kegel
        double cx = 301.3, cy = 248.7;
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) f[i + nx * j] = (float) Math.hypot(i + 0.5 - cx, j + 0.5 - cy);
        double kante = 0;
        boolean alleZu = true;
        int zahl = 0;
        StringBuilder kreis = new StringBuilder();
        boolean kreisOk = true;
        for (double r : new double[] {20, 50, 100, 150, 200, 240}) {
            List<Isolinien.Linie> ls = Isolinien.linien(f, null, nx, ny, r);
            alleZu &= ls.size() == 1 && ls.get(0).geschlossen;
            double m = 0;
            for (Isolinien.Linie l : ls)
                for (int k = 0; k < l.punkte(); k++) {
                    m = Math.max(m, Math.abs(Math.hypot(l.x[k] - cx, l.y[k] - cy) - r));
                    kante = Math.max(kante, kantenFehler(f, nx, l.x[k], l.y[k], r));
                    zahl++;
                }
            kreis.append(f("%sr = %.0f: %.2e (h²/8r = %.2e)", kreis.length() == 0 ? "" : "; ", r, m, 1 / (8 * r)));
            if (r >= 150) kreisOk &= m <= 1e-3;
        }
        A.pruefe(kante <= 1e-3, "Kegel: Punkte gegen den linearen Schnitt auf ihrer Kante", f("größter %.2e Zellen, %d Punkte", kante, zahl), "≤ 1 · 10⁻³ Zelle [G]");
        A.pruefe(kreisOk, "Kegel: Punkte gegen den Kreis, Halbmesser ab 150 Zellen", kreis.toString(), "≤ 1 · 10⁻³ Zelle ab r = 150 [G]; darunter zur Auskunft");
        A.befund("Gegen den wahren Kreis kann ein linearer Schnitt zwischen den Mitten nicht genauer sein als die Krümmung erlaubt: rund h²/(8 r) mit h = 1 Zelle. Unter r = 125 Zellen liegt das über 10⁻³; die Linien treffen die Schranke, der Fehler ist der der Abtastung, nicht des Verfahrens.");
        A.pruefe(alleZu, "Kegel: je Stufe genau eine geschlossene Linie", alleZu ? "ja" : "nein", "ja");
        // Lücke: ein Loch aus NaN schneidet die Kreise auf
        float[] g = f.clone();
        for (int j = 230; j < 270; j++) for (int i = 420; i < 600; i++) g[i + nx * j] = Float.NaN;
        int offen = 0, zu = 0;
        for (double r : new double[] {150, 200, 240})
            for (Isolinien.Linie l : Isolinien.linien(g, null, nx, ny, r)) {
                if (l.geschlossen) zu++;
                else offen++;
            }
        A.pruefe(offen == 3 && zu == 0, "Lücke aus NaN quer durch drei Kreise", f("%d offene, %d geschlossene Linien", offen, zu), "3 offen, 0 geschlossen");
        // Douglas und Peucker
        double tol = 0.1, dpGroesste = 0;
        int vor = 0, nach = 0;
        for (double r : new double[] {20, 50, 100, 150, 200, 240})
            for (Isolinien.Linie l : Isolinien.linien(f, null, nx, ny, r)) {
                Isolinien.Linie v = Isolinien.vereinfache(l, tol);
                vor += l.punkte();
                nach += v.punkte();
                for (int k = 0; k < l.punkte(); k++) dpGroesste = Math.max(dpGroesste, abstand(v, l.x[k], l.y[k]));
            }
        A.pruefe(dpGroesste <= tol + 1e-6, "Douglas und Peucker: Abstand jedes Punkts zur vereinfachten Linie", f("größter %.3f Zellen; %d → %d Punkte", dpGroesste, vor, nach), f("≤ Toleranz %.1f Zellen", tol));
        // Zeit wie G3 (700 × 400) mit zehn Stufen
        int gx = 700, gy = 400;
        float[] h = new float[gx * gy];
        Random z = new Random(5);
        for (int j = 0; j < gy; j++)
            for (int i = 0; i < gx; i++) h[i + gx * j] = (float) (3 + 2.5 * Math.sin(i / 37.0) * Math.cos(j / 23.0) + 0.3 * z.nextGaussian());
        Gitter gi = Gitter.utm("G3", 32, 462000, 6042000, 20, gx, gy);
        float[] eta = new float[gx * gy];
        Messblick.linienRechnen(gi, new float[][] {null, null, eta, h}, null, Messblick.Linienart.TIEFE, null, tol, 0.05);
        long n0 = System.nanoTime();
        float[] v = Messblick.linienRechnen(gi, new float[][] {null, null, eta, h}, null, Messblick.Linienart.TIEFE, null, tol, 0.05);
        A.info("Zeit der Isolinien (Prozessor, ein Faden)", f("G3 700 × 400, %d Stufen der Tiefe, verrauschtes Feld: %.0f ms, %d Strecken", Messblick.STUFEN_TIEFE.length, (System.nanoTime() - n0) / 1e6, v.length / 8));
    }

    /** Abstand des Punkts (x, y) vom linearen Schnitt auf seiner Kante (der Punkt liegt auf einer Kante zwischen zwei Mitten). */
    static double kantenFehler(float[] f, int nx, double x, double y, double st) {
        double xi = x - 0.5, yj = y - 0.5;
        if (Math.abs(xi - Math.rint(xi)) < 1e-4) {                  // senkrechte Kante
            int i = (int) Math.rint(xi), j = (int) Math.floor(yj);
            double a = f[i + nx * j], b = f[i + nx * (j + 1)];
            return Math.abs(j + (st - a) / (b - a) - yj);
        }
        int i = (int) Math.floor(xi), j = (int) Math.rint(yj);
        double a = f[i + nx * j], b = f[i + 1 + nx * j];
        return Math.abs(i + (st - a) / (b - a) - xi);
    }

    static double abstand(Isolinien.Linie l, double x, double y) {
        double best = Double.POSITIVE_INFINITY;
        for (int k = 0; k + 1 < l.punkte(); k++) {
            double ax = l.x[k], ay = l.y[k], bx = l.x[k + 1], by = l.y[k + 1], dx = bx - ax, dy = by - ay, ll = dx * dx + dy * dy;
            double t = ll == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / ll));
            best = Math.min(best, Math.hypot(x - ax - t * dx, y - ay - t * dy));
        }
        return best;
    }

    // ------------------------------------------------------------------ 3 Querschnitt

    static void teil3() {
        A.teil("3 Querschnitt (P11c3)");
        int nx = 300, ny = 200;
        double dx = 20;
        Gitter g = Gitter.utm("T", 32, 460000, 6040000, dx, nx, ny);
        int kantenX = (nx + 1) * ny, kanten = kantenX + nx * (ny + 1);
        double qx = 1.3, qy = -0.7;                                         // m²/s nach Ost und Nord
        float[] q = new float[kanten];
        for (int e = 0; e < kanten; e++) q[e] = (float) (e < kantenX ? qx : qy);
        double gr = 0;
        Random z = new Random(31);
        int schritte = 0;
        for (int k = 0; k < 50; k++) {
            double ax = 460000 + z.nextInt(nx + 1) * dx, ay = 6040000 + z.nextInt(ny + 1) * dx, bx = 460000 + z.nextInt(nx + 1) * dx, by = 6040000 + z.nextInt(ny + 1) * dx;
            if (ax == bx && ay == by) continue;
            Querschnitt s = Querschnitt.von(g, ax, ay, bx, by);
            schritte += s.kante.length;
            double soll = qx * -(by - ay) + qy * (bx - ax);                 // q · n_links · L
            gr = Math.max(gr, Math.abs(s.durchfluss(q) - soll) / Math.max(1, Math.abs(soll)));
        }
        A.pruefe(gr <= 1e-5, "Durchfluss durch die Treppe bei gleichförmigem Fluss gegen q · n · L, 50 Strecken in alle Richtungen", f("größte relative Abweichung %.1e; %d Kanten", gr, schritte),
                "≤ 1 · 10⁻⁵ (q liegt wie im Archiv als float vor)");
        // geschlossener Weg um ein Rechteck bei einem Feld mit Quelle: der Fluss hinaus ist die Summe der Quelle
        float[] w = new float[kanten];
        for (int j = 0; j < ny; j++) for (int i = 0; i <= nx; i++) w[i + (nx + 1) * j] = (float) (0.01 * i);         // q_x = 0,01 i: div = 0,01 / dx je Zelle
        double raus = 0;
        double[][] ecken = {{460000 + 50 * dx, 6040000 + 40 * dx}, {460000 + 120 * dx, 6040000 + 40 * dx}, {460000 + 120 * dx, 6040000 + 90 * dx}, {460000 + 50 * dx, 6040000 + 90 * dx}};
        for (int k = 0; k < 4; k++) raus -= Querschnitt.von(g, ecken[k][0], ecken[k][1], ecken[(k + 1) % 4][0], ecken[(k + 1) % 4][1]).durchfluss(w);   // gegen den Uhrzeigersinn: links ist innen
        double soll = (120 - 50) * 0.01 * (90 - 40) * dx;                   // (q_x rechts − q_x links) · Höhe
        A.pruefe(Math.abs(raus - soll) <= 1e-6 * soll, "Geschlossener Weg um 70 × 50 Zellen, q nach Ost wächst je Spalte: Fluss hinaus", f("%.6f m³/s gegen %.6f", raus, soll), "≤ 1 · 10⁻⁶ relativ");
        // Profil: gleichförmige Strömung nach Ost, Schnitt nach Nord – quer (nach links = West) ist −u
        int n = nx * ny;
        float[] u = new float[n], v = new float[n], eta = new float[n], h = new float[n];
        java.util.Arrays.fill(u, 0.8f);
        java.util.Arrays.fill(eta, 1.2f);
        for (int c = 0; c < n; c++) h[c] = c % nx < 150 ? 3f : 0f;
        Querschnitt s = Querschnitt.von(g, 460000 + 100 * dx, 6040000 + 20 * dx, 460000 + 100 * dx, 6040000 + 180 * dx);
        Querschnitt.Profil p = s.profil(u, v, eta, h, null, 0.05, (o, no) -> -1.8);
        double qr = 0;
        for (double x : p.quer()) qr = Math.max(qr, Math.abs(x + 0.8));
        A.pruefe(qr <= 1e-6 && p.punkte() == 321, "Profil: Strömung 0,8 m/s nach Ost, Schnitt nach Nord, quer nach links", f("größte Abweichung von −0,8 m/s %.1e; %d Punkte", qr, p.punkte()), "≤ 1 · 10⁻⁶; 321 Punkte");
        Querschnitt t = Querschnitt.von(g, 460000 + 140 * dx, 6040000 + 100 * dx, 460000 + 160 * dx, 6040000 + 100 * dx);
        Querschnitt.Profil pt = t.profil(u, v, eta, h, null, 0.05, (o, no) -> -1.8);
        int trocken = 0;
        for (double e : pt.eta()) if (Double.isNaN(e)) trocken++;
        A.info("Profil über die Wasserkante (Spalte 150)", f("%d von %d Punkten trocken", trocken, pt.punkte()));
        // Bilanz: Q = Q0 sin(2π t / T) über zwei Perioden – links und rechts gleich, netto 0
        double T = 44_712, tau = 300;
        int m = (int) (2 * T / tau) + 1;
        double[] zt = new double[m], qq = new double[m];
        for (int k = 0; k < m; k++) {
            zt[k] = k * tau;
            qq[k] = k == 0 ? 0 : 1000 * (Math.cos(2 * Math.PI * (k - 1) * tau / T) - Math.cos(2 * Math.PI * k * tau / T)) * T / (2 * Math.PI * tau);   // Mittel über das Intervall
        }
        Querschnitt.Bilanz b = Querschnitt.bilanz(zt, qq, 0, zt[m - 1]);
        double flut = 2 * 1000 * T / Math.PI;                              // zwei Halbwellen nach links
        A.pruefe(Math.abs(b.links() - flut) <= 1e-3 * flut && Math.abs(b.netto()) <= 1e-2 * flut, "Bilanz einer Sinuswelle (1 000 m³/s, 12 h 25 min), zwei Perioden",
                f("links %.4g m³ (Formel %.4g), rechts %.4g m³, netto %.3g m³", b.links(), flut, b.rechts(), b.netto()), "links ±0,1 %; netto ≤ 1 % von links");
    }

    // ------------------------------------------------------------------ 2 Partikel auf der Karte

    static void teil2() {
        A.teil("2 Partikel auf der Karte (Prüfstein 17)");
        int n = 400, zahl = 2000;
        double dx = 20;
        Gitter gi = Gitter.utm("T", 32, 460000, 6040000, dx, n, n);
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            // gleichförmig
            try (Messblick m = new Messblick(g, zahl)) {
                double u = 0.8, v = -0.3, dt = 10;
                int schritte = 100;
                m.staende(gi, feld(n, (x, y) -> new double[] {u, v}, null), feld(n, (x, y) -> new double[] {u, v}, null), null, null, 0, 1);
                m.zeit(0);
                Random z = new Random(17);
                float[] p = new float[Messblick.WERTE * zahl];
                double[][] start = new double[zahl][];
                for (int i = 0; i < zahl; i++) {
                    double x = 100 + 100 * z.nextDouble(), y = 150 + 100 * z.nextDouble();
                    start[i] = new double[] {x, y};
                    setze(p, i, x, y);
                }
                m.partikelSetzen(p);
                m.partikelRechnen(dt, schritte, 2);
                float[] e = m.partikelLesen();
                double weg = Math.hypot(u, v) * dt * schritte / dx, gr = 0;
                for (int i = 0; i < zahl; i++) {
                    double ex = start[i][0] + u * dt * schritte / dx, ey = start[i][1] + v * dt * schritte / dx;
                    gr = Math.max(gr, Math.hypot(e[Messblick.WERTE * i] - ex, e[Messblick.WERTE * i + 1] - ey));
                }
                A.pruefe(gr / weg <= 1e-3, "Gleichförmige Strömung (0,8; −0,3 m/s), 1 000 s in 100 Schritten", f("größte Abweichung %.2e Zellen bei %.1f Zellen Weg: %.2e", gr, weg, gr / weg),
                        "≤ 0,1 % des Wegs [G]");
            }
            // starrer Wirbel: eine Stunde je Umlauf
            try (Messblick m = new Messblick(g, zahl)) {
                double om = 2 * Math.PI / 3600, xc = 200 * dx, yc = 200 * dx, dt = 10;
                int schritte = 360;
                Feldfunktion wirbel = (x, y) -> new double[] {-om * (y - yc), om * (x - xc)};
                m.staende(gi, feld(n, wirbel, null), feld(n, wirbel, null), null, null, 0, 1);
                m.zeit(0);
                Random z = new Random(23);
                float[] p = new float[Messblick.WERTE * zahl];
                double[][] start = new double[zahl][];
                for (int i = 0; i < zahl; i++) {
                    double r = 50 + 100 * z.nextDouble(), a = 2 * Math.PI * z.nextDouble();
                    double x = 200 + r * Math.cos(a), y = 200 + r * Math.sin(a);
                    start[i] = new double[] {x, y, r};
                    setze(p, i, x, y);
                }
                m.partikelSetzen(p);
                m.partikelRechnen(dt, schritte, 1);
                float[] e = m.partikelLesen();
                double gr = 0, java = 0, radial = 0;
                for (int i = 0; i < zahl; i++) {
                    double ex = e[Messblick.WERTE * i], ey = e[Messblick.WERTE * i + 1];
                    gr = Math.max(gr, Math.hypot(ex - start[i][0], ey - start[i][1]) / start[i][2]);
                    radial = Math.max(radial, Math.abs(Math.hypot(ex - 200, ey - 200) - start[i][2]) / start[i][2]);
                    double[] q = rk2(start[i][0], start[i][1], wirbel, dx, dt, schritte);
                    java = Math.max(java, Math.hypot(ex - q[0], ey - q[1]));
                }
                A.pruefe(gr <= 1e-3, "Starrer Wirbel, ein Umlauf (3 600 s, 360 Schritte, ω Δt = 0,0175)", f("größte Abweichung vom Ausgang %.2e des Halbmessers (radial %.2e)", gr, radial),
                        "≤ 0,1 % je Umlauf [G]");
                A.info("Karte gegen Runge-Kutta 2 in double", f("größte Abweichung %.2e Zellen", java));
            }
            // neu setzen nur auf Wasser
            try (Messblick m = new Messblick(g, zahl)) {
                Feldfunktion ruhe = (x, y) -> new double[] {0.1, 0};
                float[][] s = feld(n, ruhe, (x, y) -> x < 200 * dx ? 0 : 5);
                m.staende(gi, s, s, null, null, 0, 1);
                m.zeit(0);
                m.partikelSetzen(new float[Messblick.WERTE * zahl]);           // alle tot: Alter 0 ≥ Lebensdauer 0
                m.partikelRechnen(1, 3, 1);
                float[] e = m.partikelLesen();
                int lebend = 0, trocken = 0;
                for (int i = 0; i < zahl; i++) {
                    float x = e[Messblick.WERTE * i], leben = e[Messblick.WERTE * i + 3];
                    if (leben > 0) {
                        lebend++;
                        if (x < 199.5) trocken++;                      // ab der Mitte der letzten trockenen Zelle steigt h bilinear
                    }
                }
                A.pruefe(trocken == 0 && lebend > zahl / 2, "Neu gesetzte Partikel nur auf Wasser (Westhälfte trocken), drei Schritte", f("%d von %d leben, davon %d auf trockenen Zellen", lebend, zahl, trocken),
                        "0 auf trockenen; mehr als die Hälfte lebt");
            }
        }
    }

    interface Feldfunktion { double[] at(double x, double y); }

    interface Tiefe { double at(double x, double y); }

    /** u, v nach der Funktion (x, y in m vom Ursprung, Mitte der Zelle), η = 0, h = 10 m oder nach der Tiefe. */
    static float[][] feld(int n, Feldfunktion fu, Tiefe ti) {
        float[] u = new float[n * n], v = new float[n * n], eta = new float[n * n], h = new float[n * n];
        double dx = 20;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = (i + 0.5) * dx, y = (j + 0.5) * dx;
                double[] w = fu.at(x, y);
                u[i + n * j] = (float) w[0];
                v[i + n * j] = (float) w[1];
                h[i + n * j] = ti == null ? 10f : (float) ti.at(x, y);
            }
        return new float[][] {u, v, eta, h};
    }

    static void setze(float[] p, int i, double x, double y) {
        int o = Messblick.WERTE * i;
        p[o] = (float) x;
        p[o + 1] = (float) y;
        p[o + 2] = 0;
        p[o + 3] = 1e9f;
    }

    /** Runge-Kutta 2 (Mittelpunkt) in double, Lage in Zellen. */
    static double[] rk2(double x, double y, Feldfunktion fu, double dx, double dt, int n) {
        for (int k = 0; k < n; k++) {
            double[] v1 = fu.at(x * dx, y * dx);
            double mx = x + 0.5 * dt * v1[0] / dx, my = y + 0.5 * dt * v1[1] / dx;
            double[] v2 = fu.at(mx * dx, my * dx);
            x += dt * v2[0] / dx;
            y += dt * v2[1] / dx;
        }
        return new double[] {x, y};
    }
}
