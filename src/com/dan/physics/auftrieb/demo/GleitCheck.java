package com.dan.physics.auftrieb.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.auftrieb.Form;
import com.dan.physics.auftrieb.Gleiten;
import com.dan.physics.auftrieb.Hydrostatik;
import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Vorgaben;
import com.dan.physics.auftrieb.Wasser;

import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Gleitens (Seegang Werkbuch, S7e2, Datei 101; SF150 A, Prüfstein 34). In glattem Wasser geschleppt: die Fahrt
 * längs wird je Schritt gesetzt und läuft von 0 an, Tauchen und Stampfen sind frei (wie ein Modell am Schleppwagen, Zug im
 * Schwerpunkt). Geprüft wird, dass Körper und {@link Gleiten} im Lauf der Zeit in Savitskys Gleichgewicht finden: Kräfte,
 * Druckpunkt und Übergang aus der Ruhe.
 * <ol>
 * <li><b>Gleitprisma</b> (Prüfstein 34): ein prismatischer Knickspant im Gültigkeitsbereich von Savitsky (15 × 3 m, β 12°,
 * 13 m/s, C_V 2,4): Trimm und Tiefe des Kiels am Spiegel im Gleichgewicht gegen Savitskys Gleichgewicht, ± 10 % [G].</li>
 * <li><b>Adler-Express</b> bei 24 kn (Auskunft): Trimm, Tiefe am Spiegel, Hub des Schwerpunkts, Widerstand und Leistung; dazu
 * Savitskys Gleichgewicht, das hier außerhalb seines Bereichs liegt (λ über 4, τ unter 2°).</li>
 * <li><b>Langsam wie bisher</b>: der Adler-Express bei 3 m/s (C_V 0,34, Anteil 0) mit und ohne Gleiten bitgleich.</li>
 * <li><b>Hart Ruder in voller Fahrt</b> (seit dem Befund im Container, Datei 101): der Adler-Express frei bei 24 kn, dann 70 s
 * Ruder 1: größte Krängung unter 20° [G], am Ende aufrecht; ohne Gleiten als Auskunft.</li>
 * </ol>
 */
public final class GleitCheck {
    static final Attest A = new Attest("Gleiten nach Savitsky (S7e2)", Gleiten.VERSION);

    public static void main(String[] args) {
        System.out.println("GleitCheck – S7e2 (Seegang Werkbuch 101)");
        long t0 = System.nanoTime();
        try {
            prisma();
            adler();
            langsam();
            drehung();
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        Path pa = A.schreibe(Path.of("docs", "status", "S7e_Gleiten.md"), "S7e2 – Gleiten nach Savitsky (Gleiten " + Gleiten.VERSION + ", Koerper " + Koerper.VERSION + ")");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Das Gleitprisma: Knickspant ohne Verjüngung achtern, Bug ab der Mitte schmaler und aufgeholt. */
    static Koerper gleitprisma() {
        Form.Rumpf r = new Form.Rumpf("Gleitprisma", 15, 3, 1.4, 0.45);
        r.mitteVorn = 0.5; r.mitteAchtern = 1.0; r.spiegel = 1.0; r.bugVoll = 1.5; r.bugBreite = 0.2;
        r.aufkimmung = 12; r.kimmradius = 0.02; r.aufholVorn = 0.6; r.aufholVornAb = 0.5;
        Koerper k = new Koerper(r, 0.1, new Hydrostatik(r, 0.45), 0, 0.4, 0.38 * 3, 0.25 * 15, 0.26 * 15);
        k.zusatzAusStreifen(r);
        k.gleiten = Gleiten.fuer(r);
        return k;
    }

    /** Ergebnis einer Schleppfahrt (Mittel über die letzten 10 s). */
    record Schlepp(double tau, double tiefe, double lambda, double cv, double hub, double widerstand, double auftrieb, double schwankung) {}

    /** Schleppt den Körper nach Osten über dauer s, die Fahrt in anlauf s gleichmäßig von 0 auf u (m/s); längs je Schritt gesetzt. */
    static Schlepp schleppe(Koerper k, double u, double dauer, double anlauf) {
        Wasser wa = Wasser.ruhig(0);
        k.einschwimmen(wa, 0, 0, 90, 0, 0, 0);
        double z0 = k.lage()[2];
        double dt = 0.02, n10 = 10 / dt;
        double sTau = 0, sTief = 0, sLam = 0, sCv = 0, sHub = 0, sW = 0, sL = 0, tMin = 1e9, tMax = -1e9;
        int n = (int) Math.round(dauer / dt), m = 0;
        for (int i = 0; i < n; i++) {
            double ui = u * Math.min(1, (i + 1) * dt / anlauf);
            k.geschwindigkeit(ui, 0, k.geschwindigkeit()[2]);
            k.schritt(wa, dt);
            double[] v = k.geschwindigkeit();
            double w = k.masse * (ui - v[0]) / dt;                                 // was die Fahrt verlor: der Widerstand
            if (i >= n - n10) {
                Gleiten g = k.gleiten;
                double tau = Math.toDegrees(Math.asin(k.punktWelt(1, 0, 0)[2] - k.punktWelt(0, 0, 0)[2]));
                sTau += tau; sTief += g != null ? g.tiefe : 0; sLam += g != null ? g.lambda : 0; sCv += g != null ? g.cv : 0;
                sHub += k.lage()[2] - z0; sW += w; sL += g != null ? g.auftrieb : 0;
                tMin = Math.min(tMin, tau); tMax = Math.max(tMax, tau);
                m++;
            }
        }
        return new Schlepp(sTau / m, sTief / m, sLam / m, sCv / m, sHub / m, sW / m, sL / m, tMax - tMin);
    }

    static void prisma() {
        A.teil("1 Gleitprisma (Prüfstein 34)");
        Koerper k = gleitprisma();
        Form.Rumpf r = (Form.Rumpf) k.form;
        double u = 13, lcg = k.schwerpunkt[0] + r.laenge / 2;
        double[] e = Gleiten.gleichgewicht(k.masse, lcg, r.breite, r.aufkimmung, u, 1025);
        A.info("Körper", f("%s: %.1f × %.1f m, β %.0f°, %.0f kg, Schwerpunkt %.2f m vor dem Spiegel, %.2f m über dem Kiel; %d Proben", r.name(), r.laenge, r.breite,
                r.aufkimmung, k.masse, lcg, k.schwerpunkt[2], k.proben.n));
        A.info("Savitsky", f("%.1f m/s, C_V %.2f: τ %.2f°, λ %.2f, Tiefe am Spiegel %.3f m", u, e[3], e[0], e[1], e[2]));
        Schlepp s = schleppe(k, u, 60, 20);
        A.info("Körper im Gleichgewicht (Mittel der letzten 10 s)", f("τ %.2f° (Schwankung %.3f°), λ %.2f, Tiefe am Spiegel %.3f m, Auftrieb nach Savitsky %.1f kN (%.0f %% des Gewichts), Hub %.3f m, Widerstand %.2f kN",
                s.tau, s.schwankung, s.lambda, s.tiefe, s.auftrieb / 1e3, 100 * s.auftrieb / (k.masse * Koerper.G), s.hub, s.widerstand / 1e3));
        A.pruefe(Math.abs(s.tau / e[0] - 1) <= 0.10, "Trimm gegen Savitsky", f("%.2f° gegen %.2f° (%+.1f %%)", s.tau, e[0], 100 * (s.tau / e[0] - 1)), "± 10 % [G]");
        A.pruefe(Math.abs(s.tiefe / e[2] - 1) <= 0.10, "Tiefe des Kiels am Spiegel gegen Savitsky", f("%.3f m gegen %.3f m (%+.1f %%)", s.tiefe, e[2], 100 * (s.tiefe / e[2] - 1)), "± 10 % [G]");
        A.pruefe(s.schwankung < 0.1, "Ruhig im Gleichgewicht (kein Springen, R19)", f("Trimm schwankt um %.3f°", s.schwankung), "< 0,1° [G]");
    }

    static void adler() {
        A.teil("2 Adler-Express bei 24 kn (Auskunft)");
        Koerper k = Vorgaben.adlerExpress();
        Form.Rumpf r = (Form.Rumpf) k.form;
        double u = 24 * 1852 / 3600.0, lcg = k.schwerpunkt[0] + r.laenge / 2;
        double[] e = Gleiten.gleichgewicht(k.masse, lcg, r.breite, r.aufkimmung, u, 1025);
        Schlepp s = schleppe(k, u, 90, 40);
        A.info("Körper", f("%.0f t, Schwerpunkt %.2f m vor dem Spiegel, Tiefgang in Ruhe %.2f m; C_V %.2f, F_n %.2f, F_∇ %.2f", k.masse / 1e3, lcg, r.tiefgang, s.cv,
                u / Math.sqrt(Koerper.G * r.laenge), u / Math.sqrt(Koerper.G * Math.cbrt(k.masse / 1025))));
        A.info("Im Gleichgewicht", f("τ %.2f° (Schwankung %.3f°), λ %.2f, Tiefe am Spiegel %.2f m, Hub des Schwerpunkts %+.3f m, Auftrieb nach Savitsky %.0f kN (%.0f %% des Gewichts, Anteil w %.2f), Widerstand %.0f kN (%.3f des Gewichts), Leistung %.2f MW",
                s.tau, s.schwankung, s.lambda, s.tiefe, s.hub, s.auftrieb / 1e3, 100 * s.auftrieb / (k.masse * Koerper.G), Gleiten.anteil(s.cv), s.widerstand / 1e3, s.widerstand / (k.masse * Koerper.G), s.widerstand * u / 1e6));
        A.info("Savitsky (außerhalb seines Bereichs)", e == null ? "kein Gleichgewicht" : f("τ %.2f°, λ %.2f, Tiefe am Spiegel %.2f m", e[0], e[1], e[2]));
        A.pruefe(s.widerstand < k.schubMax, "Der Schub reicht für 24 kn", f("Widerstand %.0f kN, größter Schub %.0f kN", s.widerstand / 1e3, k.schubMax / 1e3), "Widerstand < Schub");
    }

    static void langsam() {
        A.teil("3 Langsam wie bisher");
        Koerper a = Vorgaben.adlerExpress(), b = Vorgaben.adlerExpress();
        b.gleiten = null;
        Schlepp sa = schleppe(a, 3, 30, 10), sb = schleppe(b, 3, 30, 10);
        boolean gleich = sa.tau() == sb.tau() && sa.hub() == sb.hub() && sa.widerstand() == sb.widerstand();
        A.pruefe(gleich, "Adler-Express bei 3 m/s (C_V 0,34): mit Gleiten wie ohne", f("Trimm %.5f° gegen %.5f°, Hub %.6f m gegen %.6f m, Widerstand %.3f kN gegen %.3f kN",
                sa.tau(), sb.tau(), sa.hub(), sb.hub(), sa.widerstand() / 1e3, sb.widerstand() / 1e3), "bitgleich");
    }

    static void drehung() {
        A.teil("4 Hart Ruder in voller Fahrt");
        double[] mit = drehe(true), ohne = drehe(false);
        A.info("Ohne Gleiten (Auskunft)", f("größte Krängung %.1f°, am Ende %.1f°, Fahrt am Ende %.1f m/s", ohne[0], ohne[1], ohne[2]));
        A.pruefe(mit[0] < 20 && Math.abs(mit[1]) < 5, "Adler-Express, 24 kn, 70 s Ruder 1: kentert nicht",
                f("größte Krängung %.1f°, am Ende %.1f°, Fahrt am Ende %.1f m/s, Drehung %.0f°", mit[0], mit[1], mit[2], mit[3]), "< 20°, am Ende < 5° [G]");
    }

    /** Größte Krängung, Krängung am Ende (°), Fahrt am Ende (m/s), Drehung (°) nach 20 s gerade und 70 s Ruder 1. */
    static double[] drehe(boolean gleiten) {
        Koerper k = Vorgaben.adlerExpress();
        if (!gleiten) k.gleiten = null;
        Wasser wa = Wasser.ruhig(0);
        k.einschwimmen(wa, 0, 0, 90, 0, 0, 0);
        k.geschwindigkeit(24 * 1852 / 3600.0, 0, 0);
        k.hebel = 0.55;
        double dt = 0.02, hMax = 0, h = 0, k0 = 0, dreh = 0;
        for (int i = 0; i < 4500; i++) {
            if (i == 1000) { k.ruder = 1; k0 = k.kurs(); }
            k.schritt(wa, dt);
            double[] q = k.punktWelt(0, 1, 0), c = k.punktWelt(0, 0, 0);
            h = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, q[2] - c[2]))));
            if (i >= 1000) { hMax = Math.max(hMax, Math.abs(h)); double kk = k.kurs(); dreh += ((kk - k0) % 360 + 540) % 360 - 180; k0 = kk; }
        }
        double[] v = k.geschwindigkeit();
        return new double[] {hMax, h, Math.hypot(v[0], v[1]), dreh};
    }
}
