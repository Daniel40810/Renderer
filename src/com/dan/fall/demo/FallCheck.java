package com.dan.fall.demo;

import com.dan.fall.Fall;
import com.dan.fall.gl.FallGl;

import java.util.Arrays;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10): Fallzeit und Bahn gegen eine Rechnung in kleinen
 * Schritten (Runge-Kutta), Abriss gegen die Grenztiefe, Breite des Schleiers gegen die Fallhöhe, Wind, Eis,
 * Ausgänge und „gleicher Seed, gleiches Bild“. Endet mit „Alles in Ordnung.“ oder mit Rückgabewert 1.
 */
public final class FallCheck {
    private FallCheck() { }

    static int bad;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok     " : "  FEHLER ") + what);
        if (!ok) bad++;
    }

    /** Senkrecht und waagerecht mit quadratischer Reibung in kleinen Schritten (RK4), getrennt wie die Formel. */
    static double[] rk4(double vt, double v0, double r0, double T, double g) {
        double y = 0, v = v0, x = 0, r = r0, dt = 1e-4;
        double k = g / (vt * vt);
        for (double t = 0; t < T - 1e-12; t += dt) {
            double h = Math.min(dt, T - t);
            double a1 = g - k * v * v, a2 = g - k * sq(v + 0.5 * h * a1), a3 = g - k * sq(v + 0.5 * h * a2), a4 = g - k * sq(v + h * a3);
            double b1 = -k * r * r, b2 = -k * sq(r + 0.5 * h * b1), b3 = -k * sq(r + 0.5 * h * b2), b4 = -k * sq(r + h * b3);
            y += h * (v + 2 * (v + 0.5 * h * a1) + 2 * (v + 0.5 * h * a2) + (v + h * a3)) / 6;
            x += h * (r + 2 * (r + 0.5 * h * b1) + 2 * (r + 0.5 * h * b2) + (r + h * b3)) / 6;
            v += h * (a1 + 2 * a2 + 2 * a3 + a4) / 6;
            r += h * (b1 + 2 * b2 + 2 * b3 + b4) / 6;
        }
        return new double[]{y, v, x, r};
    }

    static double sq(double x) { return x * x; }

    /** Seit Phase 7: der Lauf über Fels an senkrechter Wand, auf der Rampe und an der Stufenwand. */
    static void course(Fall free) {
        System.out.println("Lauf über Fels (Phase 7)");
        // senkrechte Wand: der Lauf über Fels fällt frei, so lange wie die geschlossene Form
        Fall c = DemoCliff.fall();
        c.overRock = true;
        c.update();
        double dt = 0;
        for (int i = 0; i < c.strands.size(); i++) dt = Math.max(dt, Math.abs(c.strands.get(i).tImpact - free.strands.get(i).tImpact) / free.strands.get(i).tImpact);
        Fall.Strand s0 = c.strands.get(0);
        check(dt < 0.03 && s0.landings == 0 && s0.onWater && s0.rockShare == 0,
                String.format(Locale.GERMAN, "Senkrechte Wand: frei, ohne Aufschlag, ins Wasser, Fallzeit wie die geschlossene Form (± %.1f %%)", dt * 100));
        // Rampe mit 60°: Gleiten mit slideTerminal · √(tan θ)
        Fall r = new Fall();
        r.lipA = new double[]{-10, 300, 0};
        r.lipB = new double[]{10, 300, 0};
        r.headingDeg = 180;
        r.waterLevel = 0;
        double tan = Math.tan(Math.toRadians(60));
        r.ground = (x, z) -> z <= 0 ? 300 : Math.max(-20, 300 - z * tan);
        r.overRock = true;
        r.update();
        Fall.Strand rs = r.strands.get(0);
        double vEq = r.slideTerminal * Math.sqrt(tan), vMid = Math.abs(rs.path[Fall.PATH / 2 * 4 + 3]);
        check(Math.abs(vMid - vEq) / vEq < 0.03 && rs.path[Fall.PATH / 2 * 4 + 3] < 0 && rs.onWater,
                String.format(Locale.GERMAN, "Rampe 60°: gleitet mit %.2f m/s (slideTerminal · √tan θ = %.2f m/s), auf dem Fels, ins Wasser", vMid, vEq));
        // nie im Fels: jede Probe mindestens standoff über dem Boden (bis auf Rundung)
        double worst = 1e9;
        for (Fall.Strand s : r.strands)   // ab der zweiten Probe: auf der Kante selbst liegt das Wasser auf dem Fels
            for (int k = 1; k < Fall.PATH; k++) worst = Math.min(worst, s.path[k * 4 + 1] - r.ground.height(s.path[k * 4], s.path[k * 4 + 2]));
        check(worst > r.standoff - 0.05, String.format(Locale.GERMAN, "Das Wasser steckt nie im Fels: mindestens %.2f m darüber (Abstand %.1f m)", worst, r.standoff));
        // Stufenwand: frei, Aufschlag, Gleiten, Absprung am Absatz, im See
        Fall st = DemoCliff.stufenFall();
        int land = 0;
        double free2 = 0, rock = 0;
        boolean water = true;
        for (Fall.Strand s : st.strands) { land += s.landings; free2 = Math.max(free2, s.longestFree); rock += s.rockShare / st.strands.size(); water &= s.onWater; }
        check(land >= st.strands.size() && free2 > DemoCliff.LIP - DemoCliff.ABSATZ - 10 && rock > 0.2 && rock < 0.95 && water,
                String.format(Locale.GERMAN, "Stufenwand: %s", st.courseLine()));
        // die Tabelle: in gleichen Zeitschritten, und course() trifft die Proben genau
        double dev = 0;
        for (int k = 0; k < Fall.PATH; k += 7) {
            double[] p = st.course(st.strands.get(0), st.strands.get(0).tImpact * k / (Fall.PATH - 1), 0, 0);
            dev = Math.max(dev, Math.abs(p[1] - st.strands.get(0).path[k * 4 + 1]));
        }
        check(dev < 1e-3, String.format(Locale.GERMAN, "Weg als Tabelle in gleichen Zeitschritten, die Bahn liest sie genau (± %.1f mm)", dev * 1000));
        Fall st2 = DemoCliff.stufenFall(), cp = st.copy();
        cp.update();
        check(Arrays.equals(st.strands.get(2).path, st2.strands.get(2).path) && Arrays.equals(FallGl.pathData(st), FallGl.pathData(cp)),
                "Gleicher Seed, gleicher Weg; copy() rechnet denselben Fall");
        Fall seven = DemoCliff.fall();
        seven.strandCount = 7;
        seven.quality = Fall.Quality.LOW;
        seven.update();
        check(seven.strands.size() == 7, "Feste Zahl der Strähnen gilt auf jeder Stufe (sieben Schwestern auch auf Niedrig)");
        Fall wet = DemoCliff.stufenFall();
        check(wet.wetLine(6).size() > 20, String.format(Locale.GERMAN, "Nasse Linie aus dem Weg: %d Punkte", wet.wetLine(6).size()));
        String sv = FallGl.source("strand.vert");
        check(sv.contains("vec4 fallCourse(") && sv.contains("uFallPath") && FallGl.source("veil.vert").contains("uSizeGain"),
                "Shader lesen den Weg aus der Tabelle (fallCourse), Schleier mit Stufen nach Abstand");
    }

    public static void main(String[] args) {
        Fall f = DemoCliff.fall();
        double g = f.gravity;
        System.out.println("Fall (com.dan.fall, Phase 6)");
        check(Fall.Quality.values().length == 4 && Fall.Quality.LOW.bundles < Fall.Quality.ULTRA.bundles, "Vier Stufen, Bündel steigen an");
        check(Math.abs(f.freeFallSeconds(250) - 7.139) < 0.01, "Freier Fall über 250 m ohne Luft: 7,14 s");

        // geschlossene Form gegen kleine Schritte
        double worst = 0;
        for (double[] c : new double[][]{{16, 0, 3}, {16, 30, 3}, {60, 5, 1}, {8, 12, 0.5}, {16, 15.9, 8}})
            for (double T : new double[]{0.5, 2, 6, 15}) {
                double[] a = Fall.drop(c[0], c[1], T, g), n = rk4(c[0], c[1], c[2], T, g);
                worst = Math.max(worst, Math.max(Math.abs(a[0] - n[0]), Math.abs(Fall.slide(c[2], c[0], T, g) - n[2])));
                worst = Math.max(worst, Math.abs(a[1] - n[1]) * 0.1);
            }
        check(worst < 0.01, String.format(Locale.GERMAN, "Bahn in geschlossener Form = Runge-Kutta (größte Abweichung %.1f mm, auch schneller als die Endgeschwindigkeit)", worst * 1000));
        double[] ohne = Fall.drop(1e5, 0, f.freeFallSeconds(250), g);
        check(Math.abs(ohne[0] - 250) < 0.5, String.format(Locale.GERMAN, "Ohne Luft (v_t sehr groß) fällt das Wasser in √(2h/g) genau 250 m (%.2f m)", ohne[0]));

        // Abriss
        double q = 0.1, hc = Math.cbrt(q * q / g);
        Fall one = DemoCliff.fall();
        one.lipA = new double[]{-1, 250, 1};
        one.lipB = new double[]{1, 250, 1};
        one.discharge = 0.2;
        one.update();
        Fall.Strand s1 = one.strands.get(0);
        double hc1 = Math.cbrt(s1.q * s1.q / g);
        check(Math.abs(hc - 0.1006) < 0.001 && Math.abs(s1.depth - 0.715 * hc1) < 1e-9 && Math.abs(s1.v0 * s1.depth - s1.q) < 1e-9,
                String.format(Locale.GERMAN, "Abriss: Grenztiefe ∛(q²/g) (q = 0,1 m²/s: %.3f m), Strahl 0,715 h_c dick, q = v · Dicke", hc));

        // der Fall der Vorführung
        System.out.println("  " + f.describe());
        Fall.Strand s = f.strands.get(0);
        check(Math.abs(f.height - 250) < 2 && f.fallTime > f.freeFallSeconds(250) * 1.1 && f.fallTime < f.freeFallSeconds(250) * 3,
                String.format(Locale.GERMAN, "250 m Fall: %.1f s mit Luft, länger als frei (%.1f s), kürzer als das Dreifache", f.fallTime, f.freeFallSeconds(250)));
        double[] atB = f.parcel(s, s.tBreak - 1e-6, s.vtDrop, 0, 0), afterB = f.parcel(s, s.tBreak + 1e-6, s.vtDrop, 0, 0);
        check(Math.abs(atB[1] - afterB[1]) < 1e-3 && Math.abs(atB[0] - afterB[0]) < 1e-3 && Math.abs(atB[3] - afterB[3]) < 1e-2,
                "Am Zerfall springt nichts: Ort und Geschwindigkeit stetig");
        check(Math.abs(s.y - f.parcel(s, s.tBreak, s.vtDrop, 0, 0)[1] - s.breakup) < 0.01,
                String.format(Locale.GERMAN, "Zerfall in der gerechneten Höhe (%.1f m unter der Kante)", s.breakup));
        double minB = 1e9;
        for (Fall.Strand st : f.strands) minB = Math.min(minB, st.breakup);
        check(minB > 10 && minB < 80, String.format(Locale.GERMAN, "Oben glatt: die Strähnen halten mindestens %.0f m, bevor sie zerfallen", minB));
        boolean water = true;
        double vmax = 0;
        for (Fall.Strand st : f.strands) { water &= st.onWater && Math.abs(st.iy) < 0.05; vmax = Math.max(vmax, st.impactSpeed); }
        check(water && vmax < f.dropTerminal * 1.6 && vmax > f.dropTerminal * 0.5,
                String.format(Locale.GERMAN, "Aufprall im See auf dem Spiegel, mit rund der Endgeschwindigkeit der Pakete (bis %.1f m/s)", vmax));

        // Schleier
        double w0 = f.veilWidth(1), w100 = f.veilWidth(100), w249 = f.veilWidth(249);
        check(w0 < w100 && w100 < w249 && w249 - w0 > 8 && w249 - w0 < 60,
                String.format(Locale.GERMAN, "Schleier wird mit der Fallhöhe breiter: %.0f m oben, %.0f m nach 100 m, %.0f m unten", w0, w100, w249));

        // Wind
        Fall wf = DemoCliff.fall();
        wf.windSpeed = 8;
        wf.windFromDeg = 270;
        wf.update();
        Fall.Strand ws = wf.strands.get(0), cs = f.strands.get(0);
        double topShift = wf.center(ws, wf.timeAtDrop(ws, 10))[0] - f.center(cs, f.timeAtDrop(cs, 10))[0];
        double botShift = ws.ix - cs.ix;
        check(botShift > 15 && topShift < 1, String.format(Locale.GERMAN, "Wind 8 m/s aus Westen biegt den Fall nach Osten: oben %.1f m, unten %.0f m", topShift, botShift));

        // Eis
        Fall ice = DemoCliff.fall();
        ice.temperature = -20;
        ice.update();
        check(ice.ice() == 1 && ice.strands.get(0).q < f.strands.get(0).q && ice.mist() < f.mist() && ice.describe().contains("Eis"),
                "Frost: der Fall steht, weniger Wasser, weniger Nebel");

        // Ausgänge
        check(f.ripples().size() == f.strands.size() && f.ripples().get(0)[2] > 0, String.format(Locale.GERMAN, "Ringwellen für das Wasser: %d Quellen, %.2f m hoch", f.ripples().size(), f.ripples().get(0)[2]));
        double l1 = f.soundLevel(100), l2 = f.soundLevel(200);
        check(Math.abs(l1 - l2 - 6.02) < 0.01 && l1 > 60 && l1 < 100, String.format(Locale.GERMAN, "Lautstärke %.0f dB in 100 m, je Verdopplung des Abstands 6 dB leiser", l1));
        Fall big = DemoCliff.fall();
        big.discharge = 20;
        big.update();
        check(big.mist() > f.mist() && big.ripples().get(0)[2] > f.ripples().get(0)[2], String.format(Locale.GERMAN, "Mehr Abfluss, mehr Nebel und höhere Ringwellen (Nebel %.2f statt %.2f)", big.mist(), f.mist()));
        check(!f.wetLine(10).isEmpty(), String.format(Locale.GERMAN, "Nasse Linie für die Wand: %d Punkte", f.wetLine(10).size()));

        // gleicher Seed, gleiches Bild
        Fall a = DemoCliff.fall(), b = DemoCliff.fall();
        check(Arrays.equals(FallGl.bundleData(a), FallGl.bundleData(b)) && Arrays.deepEquals(FallGl.strandUniforms(a), FallGl.strandUniforms(b))
                && Arrays.equals(FallGl.mistData(a), FallGl.mistData(b)), "Gleicher Seed, gleiches Bild (Strähnen, Bündel, Nebel)");
        b.seed = 2;
        b.update();
        check(!Arrays.equals(FallGl.bundleData(a), FallGl.bundleData(b)), "Anderer Seed, anderes Bild");

        course(f);

        // Stufen und Shader
        Fall lo = DemoCliff.fall();
        lo.quality = Fall.Quality.LOW;
        lo.update();
        check(lo.bundles.size() < f.bundles.size() && lo.strands.size() <= f.strands.size(), "Niedrig: weniger Bündel und Strähnen");
        String sv = FallGl.source("veil.vert"), ss = FallGl.source("strand.frag");
        check(sv.contains("vec4 fallParcel(") && ss.contains("fallFinish(") && !sv.contains("#include"), "Shader mit eingesetztem Baustein fall.glsl");
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Fehler.");
        System.exit(bad == 0 ? 0 : 1);
    }
}
