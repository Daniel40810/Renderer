package com.dan.netview.demo;

import com.dan.netview.Contour;
import com.dan.netview.NetLayer;
import com.dan.netview.Ramp;
import com.dan.netview.Simplify;
import com.dan.netview.gl.NetviewGl;

import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Netzblicks ohne Fenster und ohne Grafikkarte (seit LituyaBay1958 Phase 7, Werkbuch 5.12): Isolinien eines
 * bekannten Kreisfelds liegen auf dem Kreis und sind geschlossen, Douglas-Peucker hält Enden und Toleranz, die Stufen
 * wechseln bei den eingestellten Abständen, die Rampe trifft ihre Farben, und die Instanzen je Stück stimmen
 * (Strecken, Pfeilspitze, Punkte, Graph). Endet mit „Alles in Ordnung.“
 */
public final class NetviewCheck {
    private NetviewCheck() { }

    static int fails;

    static void ok(boolean c, String what, String detail) {
        System.out.printf(Locale.GERMAN, "  %-58s %s%s%n", what, c ? "OK" : "FEHLER", detail.isEmpty() ? "" : "  (" + detail + ")");
        if (!c) fails++;
    }

    public static void main(String[] a) {
        System.out.println("Netzblick (com.dan.netview) – Prüfung");
        circle();
        simplify();
        lod();
        ramp();
        instances();
        System.out.println(fails == 0 ? "Alles in Ordnung." : fails + " Fehler.");
        if (fails > 0) System.exit(1);
    }

    /** Feld f = Abstand zur Mitte (300, −200), 10 m Raster: die Isolinie zu R liegt auf dem Kreis mit Radius R. */
    static void circle() {
        int w = 161, h = 141;
        double ox = -500, oz = -900, step = 10, mx = 300, mz = -200;
        float[] f = new float[w * h];
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) f[j * w + i] = (float) Math.hypot(ox + i * step - mx, oz + j * step - mz);
        for (double r : new double[]{55, 250, 480}) {
            List<float[]> ls = Contour.lines(f, w, h, ox, oz, step, r);
            double max = 0;
            boolean closed = ls.size() == 1;
            for (float[] l : ls) {
                for (int k = 0; k < l.length; k += 2) max = Math.max(max, Math.abs(Math.hypot(l[k] - mx, l[k + 1] - mz) - r));
                closed &= l.length >= 8 && l[0] == l[l.length - 2] && l[1] == l[l.length - 1];
            }
            // lineare Deutung zwischen zwei Rasterpunkten: Fehler ≤ step²/(8 r) für den Abstand zur Mitte
            double lim = step * step / (8 * r) + 1e-3;
            ok(max <= lim && closed, String.format(Locale.GERMAN, "Kreis r = %.0f m: Isolinie auf dem Kreis, geschlossen", r),
                    String.format(Locale.GERMAN, "%d Zug, größter Abstand %.3f m ≤ %.3f m", ls.size(), max, lim));
        }
        // Rand: Kreis über den Rand hinaus gibt offene Züge, Ränder des Felds mit NaN werden ausgelassen
        float[] g = f.clone();
        for (int j = 0; j < h; j++) for (int i = 0; i < 60; i++) g[j * w + i] = Float.NaN;   // x < 100 m schneidet den Kreis
        List<float[]> cut = Contour.lines(g, w, h, ox, oz, step, 480);
        boolean open = !cut.isEmpty();
        for (float[] l : cut) open &= !(l[0] == l[l.length - 2] && l[1] == l[l.length - 1]);
        ok(open, "Feld mit NaN-Rand: offene Züge, keine Linie ins Leere", cut.size() + " Züge");
        NetLayer layer = new NetLayer("Kreise");
        int n = layer.isolines(f, w, h, ox, oz, step, 5, new double[]{100, 200, 300}, 0xFFFFFF, 2);
        ok(n == 3 && layer.items.get(1).values[0] == 200f && layer.items.get(1).xyz[1] == 5f, "NetLayer.isolines: drei Isolinien mit Wert und Höhe", n + " Stücke");
    }

    /** Douglas-Peucker: Enden bleiben, Abweichung ≤ Toleranz, deutlich weniger Punkte; geschlossener Zug bleibt geschlossen. */
    static void simplify() {
        int n = 2001;
        float[] p = new float[3 * n];
        for (int i = 0; i < n; i++) { double x = i * 2.0; p[3 * i] = (float) x; p[3 * i + 1] = 0; p[3 * i + 2] = (float) (40 * Math.sin(x / 300) + 3 * Math.sin(x / 7)); }
        for (double tol : new double[]{1, 4, 25}) {
            int[] k = Simplify.keep(p, tol);
            double dev = Simplify.maxDeviation(p, k);
            ok(k[0] == 0 && k[k.length - 1] == n - 1 && dev <= tol + 1e-6 && k.length < n,
                    String.format(Locale.GERMAN, "Douglas-Peucker %.0f m: Enden, Abweichung, weniger Punkte", tol),
                    String.format(Locale.GERMAN, "%d von %d Punkten, größte Abweichung %.2f m", k.length, n, dev));
        }
        int m = 400;
        float[] c = new float[3 * (m + 1)];
        for (int i = 0; i <= m; i++) { double t = 2 * Math.PI * i / m; c[3 * i] = (float) (500 * Math.cos(t)); c[3 * i + 2] = (float) (500 * Math.sin(t)); }
        c[3 * m] = c[0]; c[3 * m + 2] = c[2];
        int[] k = Simplify.keep(c, 4);
        double dev = Simplify.maxDeviation(c, k);
        ok(k.length >= 4 && k[0] == 0 && k[k.length - 1] == m && dev <= 4 + 1e-6, "Kreis 500 m geschlossen, 4 m: bleibt ein Ring",
                String.format(Locale.GERMAN, "%d von %d Punkten, Abweichung %.2f m (Sehne: %.0f Punkte erwartet)", k.length, m + 1, dev, Math.PI / Math.acos(1 - 4 / 500.0)));
    }

    /** Stufen nach Abstand (Rand der Hülle zum Auge). */
    static void lod() {
        NetLayer l = new NetLayer("Stufen");
        NetLayer.Item it = l.line(new float[]{-100, 0, 0, 100, 0, 0}, 0xFFFFFF, 2);
        int a = l.lodFor(it, 100), b = l.lodFor(it, 100 + 799), c = l.lodFor(it, 100 + 801), d = l.lodFor(it, 100 + 3999), e = l.lodFor(it, 100 + 4001);
        ok(a == 0 && b == 0 && c == 1 && d == 1 && e == 2, "Stufen bei 800 m und 4 km (ab dem Rand der Hülle)", a + " " + b + " " + c + " " + d + " " + e);
        boolean thrown = false;
        try { l.graph(new float[]{0, 0, 0, 1, 0, 0}, new int[]{0, 2}, 0, 1); } catch (IllegalArgumentException x) { thrown = true; }
        boolean thrown2 = false;
        try { l.band(new float[]{0, 0, 0, 1, 0, 0}, new float[]{1}, 4); } catch (IllegalArgumentException x) { thrown2 = true; }
        ok(thrown && thrown2, "Falsche Kante und falsche Zahl der Werte werden abgewiesen", "");
    }

    static void ramp() {
        Ramp r = Ramp.TIME.copy(0, 400);
        int s = r.stops.length;
        boolean ends = r.color(-50) == r.stops[0] && r.color(0) == r.stops[0] && r.color(400) == r.stops[s - 1] && r.color(900) == r.stops[s - 1];
        double mid = 400.0 / (s - 1);
        boolean stops = r.color(mid) == r.stops[1] && r.color(2 * mid) == r.stops[2];
        int half = new Ramp(0, 1, 0x000000, 0xFFFFFF).color(0.5);
        ok(ends && stops && (half == 0x808080 || half == 0x7F7F7F), "Rampe: Enden geklemmt, Stützfarben getroffen, Mitte grau", String.format("0x%06X", half));
    }

    static void instances() {
        NetLayer l = new NetLayer("Instanzen");
        NetLayer.Item line = l.line(new float[]{0, 0, 0, 10, 0, 0, 20, 0, 5, 30, 0, 0}, 0xFF0000, 2);
        NetLayer.Item arrow = l.arrow(new float[]{0, 0, 0, 10, 0, 0, 20, 0, 0}, 0x00FF00, 2);
        NetLayer.Item pts = l.points(new float[]{0, 0, 0, 1, 0, 0, 2, 0, 0}, new float[]{10, 20, 30}, -1, 8);
        NetLayer.Item graph = l.graph(new float[]{0, 0, 0, 10, 0, 0, 10, 0, 10, 0, 0, 10}, new int[]{0, 1, 1, 2, 2, 3, 3, 0, 0, 2}, 0x8080FF, 1.5f);
        NetLayer.Item band = l.band(new float[]{0, 0, 0, 10, 0, 0, 20, 0, 0}, new float[]{5, 50, 100}, 6);
        int nl = NetviewGl.instances(line, 0).size(), na = NetviewGl.instances(arrow, 0).size(), np = NetviewGl.instances(pts, 0).size(),
                ng = NetviewGl.instances(graph, 0).size(), nb = NetviewGl.instances(band, 0).size();
        float[] head = NetviewGl.instances(arrow, 0).get(na - 1);
        float[] b0 = NetviewGl.instances(band, 0).get(0);
        ok(nl == 3 && na == 3 && head[3] == 1 && np == 3 && ng == 5 + 4 && nb == 2 && b0[3] == 5 && b0[14] == 1 && b0[12] == 5 && b0[13] == 50,
                "Instanzen: 3 Strecken, Pfeil 2 + Spitze, 3 Punkte, Graph 5 Kanten + 4 Knoten, Band mit Werten",
                nl + " / " + na + " / " + np + " / " + ng + " / " + nb);
        // Stufe 2 an einer fast geraden Linie: nur noch eine Strecke
        float[] wiggle = new float[3 * 200];
        for (int i = 0; i < 200; i++) { wiggle[3 * i] = i * 10; wiggle[3 * i + 2] = (float) Math.sin(i) * 2; }
        NetLayer.Item w = l.line(wiggle, 0xFFFFFF, 2);
        int s0 = NetviewGl.instances(w, 0).size(), s1 = NetviewGl.instances(w, 1).size(), s2 = NetviewGl.instances(w, 2).size();
        ok(s0 == 199 && s1 == 1 && s2 == 1, "Stufen dünnen aus: 199 Strecken nah, 1 fern (Zickzack 2 m < 4 m)", s0 + " / " + s1 + " / " + s2);
        ok(l.segments() == 3 + 2 + 0 + 5 + 2 + 199 && l.version() == 6, "Strecken der Ebene und Version", l.segments() + ", Version " + l.version());
    }
}
