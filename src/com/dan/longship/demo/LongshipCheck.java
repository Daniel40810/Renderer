package com.dan.longship.demo;

import com.dan.longship.Hull;
import com.dan.longship.Longship;
import com.dan.longship.Material;
import com.dan.longship.Mesh;
import com.dan.longship.ShipType;
import com.dan.longship.Water;

import java.util.Locale;

/** Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ */
public final class LongshipCheck {
    private static int bad;

    public static void main(String[] a) {
        for (ShipType t : ShipType.values()) hull(t);
        sail();
        sailing();
        upwind();
        mooring();
        passage();
        oars();
        seed();
        rig();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) {
        if (!ok) bad++;
        System.out.println((ok ? "  ok   " : "  FEHLER ") + what);
    }

    static void hull(ShipType t) {
        Hull h = new Hull(t);
        Mesh m = h.mesh;
        double[] b = m.bounds();
        double len = b[5] - b[2], beam = b[3] - b[0];
        check(len > t.length && len < t.length + 3.5 && beam > t.beam && beam < t.beam + 0.6 && b[1] < -t.draft + 0.05 && b[1] > -t.draft - 0.25,
                String.format(Locale.GERMAN, "%s: %.1f × %.1f m über alles, Kiel %.2f m unter der Wasserlinie (Soll %.1f × %.1f, %.2f)", t.label, len, beam, -b[1], t.length, t.beam, t.draft));
        // Außenhaut zeigt nach außen, Innenhaut nach innen
        float[] v = m.vertexData();
        int[] ix = m.indexData();
        double out = 0, outN = 0, in = 0, inN = 0;
        int wrongWind = 0;
        for (int i = 0; i < ix.length; i += 3) {
            int k = ix[i] * Mesh.STRIDE;
            int mat = (int) v[k + 8];
            double[] p0 = m.pos(ix[i]), p1 = m.pos(ix[i + 1]), p2 = m.pos(ix[i + 2]);
            double[] n = m.nrm(ix[i]);
            double[] geo = Mesh.normal(p0, p1, p2);
            if (Mesh.dot(geo, geo) > 0.5 && Mesh.dot(geo, n) < 0) wrongWind++;
            double cx = (p0[0] + p1[0] + p2[0]) / 3, cy = (p0[1] + p1[1] + p2[1]) / 3;
            double yc = t.freeboard * 0.2;
            double radial = n[0] * cx + n[1] * (cy - yc);
            double ar = Math.sqrt(Math.max(0, Mesh.dot(cross(p0, p1, p2), cross(p0, p1, p2)))) / 2;
            if (mat == Material.HULL.ordinal()) { outN += ar; if (radial > 0) out += ar; }
            if (mat == Material.INNER.ordinal()) { inN += ar; if (radial < 0) in += ar; }
        }
        check(out > outN * 0.85 && in > inN * 0.95 && wrongWind <= m.triangles() / 500,
                String.format(Locale.GERMAN, "%s: Außenhaut %.0f %% der Fläche nach außen (der Rest sind die Plankenkanten, nach unten), Innenhaut %.0f %% nach innen, Windung stimmt (%,d Dreiecke)", t.label, out * 100 / Math.max(1e-9, outN), in * 100 / Math.max(1e-9, inN), m.triangles()));
        double area = 0;
        for (double[] c : h.columns) area += c[2];
        double approx = t.length * t.beam * 0.62;
        check(h.columns.length >= 16 && area > approx * 0.6 && area < approx * 1.3,
                String.format(Locale.GERMAN, "%s: %d Säulen fürs Schwimmen, Wasserlinienfläche %.0f m²", t.label, h.columns.length, area));
        if (t.shields) check(h.shields.area(Material.SHIELD) > t.oarPairs * 2 * 0.4, String.format(Locale.GERMAN, "%s: Schilde am Bord", t.label));
    }

    static double[] cross(double[] a, double[] b, double[] c) {
        double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, v = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        return new double[]{u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
    }

    static void sail() {
        Longship k = new Longship(ShipType.KNORR, "Knorr");
        k.sailSet = 1;
        Mesh m = k.dynamicMesh(new Mesh());
        double full = m.area(Material.SAIL);
        k.sailSet = 0;
        double furled = k.dynamicMesh(new Mesh()).area(Material.SAIL);
        double want = ShipType.KNORR.sailArea();
        check(full > want * 0.9 && full < want * 1.25 && furled < full * 0.2,
                String.format(Locale.GERMAN, "Segel gesetzt %.0f m² (Rah × Höhe %.0f m²), aufgetucht %.0f m²", full, want, furled));
        check(Longship.sailEfficiency(150) == 0 && Longship.sailEfficiency(45) > Longship.sailEfficiency(0) && Longship.sailEfficiency(100) > 0,
                "Rahsegel: raumschots am besten, hart am Wind nichts");
    }

    static void sailing() {
        Longship k = new Longship(ShipType.KNORR, "Knorr");
        k.windFromDeg = 270; k.windSpeed = 6;
        k.place(0, 0, 90);
        k.commands.add(Longship.Command.sail(1));
        k.commands.add(Longship.Command.sailTo(800, 100, 10));
        double t = 0;
        while (!k.commands.isEmpty() && t < 900) { k.step(0.1); t += 0.1; }
        check(k.commands.isEmpty() && Math.hypot(k.x - 800, k.z - 100) < 12 && t > 150 && t < 600,
                String.format(Locale.GERMAN, "Knorr segelt bei 6 m/s Wind 800 m raumschots in %.0f s (%.1f m/s im Mittel)", t, Math.hypot(800, 100) / t));
    }

    static void upwind() {
        Longship k = new Longship(ShipType.KNORR, "Knorr");
        k.windFromDeg = 270; k.windSpeed = 6;
        k.place(0, 0, 270);
        k.sailSet = k.sailTarget = 1;
        k.commands.add(Longship.Command.sailTo(-200, 0, 8));
        for (int i = 0; i < 50; i++) k.step(0.1);
        check(k.sailStalled && k.mode == Longship.Mode.RUDERN, "Gegen den Wind zieht das Segel nicht: die Knorr rudert (" + k.describe() + ")");
    }

    static void mooring() {
        Longship k = new Longship(ShipType.KNORR, "Knorr");
        k.place(0, 0, 0);
        k.commands.add(Longship.Command.rowTo(0, -60, 6));
        k.commands.add(Longship.Command.moor(30, -120, 80));
        double t = 0;
        while ((!k.commands.isEmpty() || k.mode != Longship.Mode.FEST) && t < 600) { k.step(0.1); t += 0.1; }
        for (int i = 0; i < 100; i++) k.step(0.1);
        double err = Math.abs(((k.heading - 80) % 360 + 540) % 360 - 180);
        check(k.mode == Longship.Mode.FEST && Math.hypot(k.x - 30, k.z + 120) < 1.0 && err < 6,
                String.format(Locale.GERMAN, "Anlegen unter Riemen in %.0f s: %.2f m vom Liegeplatz, Kurs %.1f° daneben", t, Math.hypot(k.x - 30, k.z + 120), err));
    }

    /** Durch eine Lücke von 14 m in einer Pfahlreihe: aus einem Winkel von 100° heran, dann auf der Linie hindurch. */
    static void passage() {
        Longship k = new Longship(ShipType.KNORR, "Knorr");
        k.place(60, 40, 0);
        k.commands.add(Longship.Command.passThrough(0, 0, 270));
        double worst = 0, t = 0;
        boolean crossed = false;
        while (!k.commands.isEmpty() && t < 400) {
            double px = k.x;
            k.step(0.1);
            t += 0.1;
            if (px > 0 && k.x <= 0) { crossed = true; worst = Math.abs(k.z); }
        }
        check(crossed && worst + k.type.beam / 2 < 7 && k.commands.isEmpty(),
                String.format(Locale.GERMAN, "Durch eine Lücke von 14 m: %.1f m neben der Mitte, Bord bleibt %.1f m vom Pfahl, in %.0f s", worst, 7 - worst - k.type.beam / 2, t));
    }

    static void oars() {
        Longship l = new Longship(ShipType.LANGSCHIFF, "Langschiff");
        int[] strokes = {0};
        l.onStroke = (x, y, z, v) -> strokes[0]++;
        l.place(0, 0, 0);
        l.commands.add(Longship.Command.rowTo(0, -2000, 5));
        for (int i = 0; i < 600; i++) l.step(0.1);
        check(Math.abs(strokes[0] - 60 * l.strokeRate) <= 2 && l.speed > ShipType.LANGSCHIFF.rowSpeed * 0.9,
                String.format(Locale.GERMAN, "Langschiff rudert: %d Schläge in 60 s, %.1f m/s", strokes[0], l.speed));
        Mesh m = l.dynamicMesh(new Mesh());
        double[] b = m.bounds();
        check(b[3] - b[0] > ShipType.LANGSCHIFF.beam + 2 * ShipType.LANGSCHIFF.oarLength * 0.6 && b[1] < 0.1,
                String.format(Locale.GERMAN, "Riemen ausgelegt %.1f m von Blatt zu Blatt, Blätter bis %.2f m", b[3] - b[0], b[1]));
        double[][] blades = l.oarBlades();
        double far = 1e9;
        for (double[] bl : blades) far = Math.min(far, Math.hypot(bl[0] - l.x, bl[2] - l.z));
        check(blades.length == 52 && far > ShipType.LANGSCHIFF.beam / 2 + 1, String.format(Locale.GERMAN, "%d Blätter für Spritzer, alle außenbords (mindestens %.1f m von der Mitte)", blades.length, far));
    }

    static void seed() {
        Longship a = new Longship(ShipType.LANGSCHIFF, "a"), b = new Longship(ShipType.LANGSCHIFF, "b");
        for (Longship s : new Longship[]{a, b}) { s.water = (x, z) -> 0.1 * Math.sin(x * 0.3 + z * 0.2); s.place(0, 0, 45); s.commands.add(Longship.Command.rowTo(100, -100, 5)); }
        for (int i = 0; i < 300; i++) { a.step(0.05); b.step(0.05); }
        check(a.x == b.x && a.z == b.z && a.pitch == b.pitch && java.util.Arrays.equals(a.hull.mesh.vertexData(), b.hull.mesh.vertexData()),
                "Gleiche Befehle, gleiche Fahrt; gleicher Typ, gleiches Netz");
    }

    /** Seit Timmendorf Phase 18: ein Segelmodell von außen gibt Fahrt, Krängung nach Lee, Abdrift und die Höhe am Wind. */
    static void rig() {
        Longship l = new Longship(ShipType.LANGSCHIFF, "Langschiff");
        l.rig = new Longship.Rig() {
            public double[] sail(Longship s, double course) { return s.windAngle(course) > 120 ? new double[]{0, 0, 0} : new double[]{4.0, 8, 5}; }
            public double limit(Longship s) { return 120; }
        };
        l.windFromDeg = 270;                  // Wind aus West: auf Nordkurs von Backbord, Lee ist Steuerbord
        l.windSpeed = 7;
        l.place(0, 0, 0);
        l.sailSet = l.sailTarget = 1;
        l.commands.add(Longship.Command.sailTo(0, -3000, 10));
        for (int i = 0; i < 1200; i++) l.step(0.1);
        check(l.mode == Longship.Mode.SEGELN && Math.abs(l.speed - 4.0) < 0.2, String.format(Locale.GERMAN, "Segelmodell von außen: %.1f m/s unter Segel (Modell 4,0)", l.speed));
        check(l.heelDeg > 7 && l.leewayDeg > 4 && l.roll > 0.1 && l.x > 20,
                String.format(Locale.GERMAN, "Krängung %.1f° und Abdrift %.1f° nach Lee (Steuerbord), %.0f m versetzt", l.heelDeg, l.leewayDeg, l.x));
        check(l.canSail(330) && !l.canSail(300) && l.windAngle(330) == 120, "Höhe am Wind aus dem Modell: 120° zum Wind trägt noch, 150° nicht");
    }
}
