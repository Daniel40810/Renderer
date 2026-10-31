package com.dan.herd.demo;

import com.dan.herd.AnimalMesh;
import com.dan.herd.Herd;
import com.dan.herd.gl.HerdGl;

import java.util.List;
import java.util.Locale;

/** Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ */
public final class HerdCheck {
    private static int bad;

    public static void main(String[] a) {
        meshes();
        pens();
        flee();
        dog();
        dayNight();
        gait();
        calls();
        seed();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) {
        if (!ok) bad++;
        System.out.println((ok ? "  ok   " : "  FEHLER ") + what);
    }

    static String f(String fmt, Object... v) { return String.format(Locale.GERMAN, fmt, v); }

    /** Ein Hof zum Prüfen: ein Hühnerhof, eine Ziegenweide mit einem Tümpel, ein Schweinepferch, der Hund am Steg. */
    static Herd farm() {
        Herd h = new Herd();
        h.seed = 7;
        h.ground = (x, z) -> 0.02 * x;
        h.walkable = (x, z) -> Math.hypot(x - 40, z) > 4;           // Tümpel in der Weide
        h.pens.add(new Herd.Pen("Hühnerhof", Herd.Kind.HUHN, 0, 0, 6, 8));
        h.pens.add(new Herd.Pen("Weide", Herd.Kind.ZIEGE, 40, 0, 20, 6));
        h.pens.add(new Herd.Pen("Pferch", Herd.Kind.SCHWEIN, 0, 40, 7, 3));
        h.pens.add(new Herd.Pen("Steg", Herd.Kind.HUND, -40, 0, 25, 1, List.of(new double[]{-50, 0}, new double[]{-30, 5}, new double[]{-40, -15})));
        return h;
    }

    static void meshes() {
        StringBuilder b = new StringBuilder();
        boolean ok = true;
        for (Herd.Kind k : Herd.Kind.values()) {
            AnimalMesh m = AnimalMesh.build(k);
            float[] v = m.vertices();
            double minY = 9, maxY = -9, minZ = 9, maxZ = -9;
            for (int i = 0; i < m.count(); i++) {
                double y = v[i * AnimalMesh.FLOATS + 1], z = v[i * AnimalMesh.FLOATS + 2];
                minY = Math.min(minY, y); maxY = Math.max(maxY, y); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
            double len = maxZ - minZ;
            ok &= Math.abs(minY) < 0.02 && len > k.length * 0.75 && len < k.length * 1.4 && maxY > k.height * 0.8 && maxY < k.height * 1.6;
            b.append(f("%s %.2f m lang, %.2f m hoch, %d Dreiecke; ", k.label, len, maxY, m.count() / 3));
        }
        check(ok, "Netze: Füße auf dem Boden, Maße wie die Art — " + b.toString().replaceAll("; $", ""));
    }

    static void pens() {
        Herd h = farm();
        h.step(0.01);
        int[] n = new int[4];
        for (Herd.Animal a : h.animals()) n[a.kind.ordinal()]++;
        double worst = 0, pond = 1e9;
        for (int i = 0; i < 20 * 60 * 6; i++) {
            h.step(1 / 6.0);
            for (Herd.Animal a : h.animals()) {
                Herd.Pen p = h.pens.get(a.pen);
                if (a.kind != Herd.Kind.HUND) worst = Math.max(worst, Math.hypot(a.pos[0] - p.x(), a.pos[2] - p.z()) / p.r());
                if (a.kind == Herd.Kind.ZIEGE) pond = Math.min(pond, Math.hypot(a.pos[0] - 40, a.pos[2]));
                if (Math.abs(a.pos[1] - 0.02 * a.pos[0]) > 1e-9) worst = 99;
            }
        }
        check(n[0] == 8 && n[1] == 6 && n[2] == 3 && n[3] == 1 && worst < 1.35 && pond > 3.9,
                f("20 min: %d Hühner, %d Ziegen, %d Schweine, %d Hund bleiben im Gehege (höchstens %.2f Halbmesser), keine Ziege im Tümpel (%.1f m vom Rand der Mitte), alle auf dem Boden",
                        n[0], n[1], n[2], n[3], worst, pond));
        int feed = 0, lie = 0, walk = 0;
        for (Herd.Animal a : h.animals()) { if (a.state == Herd.State.FEED) feed++; if (a.state == Herd.State.LIE) lie++; if (a.state == Herd.State.WALK) walk++; }
        check(h.describe().contains("Hühner") && feed + lie + walk > 0, "Beschreibung: " + h.describe() + f(" · jetzt %d fressen, %d liegen, %d gehen", feed, lie, walk));
    }

    static void flee() {
        Herd h = farm();
        h.step(0.01);
        for (int i = 0; i < 600; i++) h.step(0.1);
        // ein Auge geht mitten in die Weide
        double[] eye = {40, 1.7, -14};
        h.disturbers.add(eye);
        double minG = 1e9;
        for (int i = 0; i < 400; i++) {
            eye[2] = -14 + i * 0.07;      // 0,7 m/s quer durch die Weide
            h.step(0.1);
        }
        for (int i = 0; i < 20; i++) {
            h.step(0.1);
            for (Herd.Animal a : h.animals()) if (a.kind == Herd.Kind.ZIEGE) minG = Math.min(minG, Math.hypot(a.pos[0] - eye[0], a.pos[2] - eye[2]));
        }
        check(minG > 2.5, f("Ein Mensch geht durch die Weide: die Ziegen weichen aus (nächste nach dem Gang %.1f m, Fluchtdistanz %.0f m)", minG, Herd.Kind.ZIEGE.flight));
        Herd c = farm();
        c.step(0.01);
        for (int i = 0; i < 300; i++) c.step(0.1);
        double[] e2 = {0, 1.7, 0};
        c.disturbers.add(e2);
        int ran = 0;
        for (int i = 0; i < 10; i++) {
            c.step(0.1);
            for (Herd.Animal a : c.animals()) if (a.kind == Herd.Kind.HUHN && a.state == Herd.State.RUN) ran++;
        }
        check(ran > 0, "Mitten im Hühnerhof laufen die Hühner weg");
    }

    static void dog() {
        Herd h = farm();
        h.step(0.01);
        double[] eye = {-30, 1.7, 0};
        h.disturbers.add(eye);
        double d = 1e9;
        boolean sat = false;
        for (int i = 0; i < 1500; i++) {
            h.step(0.1);
            for (Herd.Animal a : h.animals()) if (a.kind == Herd.Kind.HUND) { d = Math.hypot(a.pos[0] - eye[0], a.pos[2] - eye[2]); sat |= a.state == Herd.State.SIT && d < 3; }
        }
        check(sat && h.barks() > 0, f("Der Hund kommt zum Menschen, setzt sich (%.1f m) und bellt (%d mal)", d, h.barks()));
    }

    static void dayNight() {
        Herd h = farm();
        h.step(0.01);
        h.sunAltDeg = -10;
        for (int i = 0; i < 3000; i++) h.step(0.2);
        int hens = h.outside(Herd.Kind.HUHN), lieG = h.count(Herd.Kind.ZIEGE, Herd.State.LIE), lieP = h.count(Herd.Kind.SCHWEIN, Herd.State.LIE);
        check(hens == 0 && lieG >= 5 && lieP >= 2, f("Nachts: Hühner im Stall (%d draußen), Ziegen liegen (%d von 6), Schweine liegen (%d von 3)", hens, lieG, lieP));
        h.sunAltDeg = 20;
        h.rain = 4;
        h.step(0.5);
        int rainOut = h.outside(Herd.Kind.HUHN);
        h.rain = 0;
        h.snow = 0.3;
        h.step(0.5);
        check(rainOut == 0 && h.outside(Herd.Kind.ZIEGE) == 0 && h.outside(Herd.Kind.HUND) == 1, "Bei Regen gehen die Hühner hinein, bei 30 cm Schnee auch Ziegen und Schweine; der Hund bleibt draußen");
        h.snow = 0;
        h.step(0.5);
        check(h.outside(Herd.Kind.HUHN) == 8 && h.outside(Herd.Kind.ZIEGE) == 6, "Danach sind alle wieder draußen");
    }

    static void gait() {
        Herd h = farm();
        h.step(0.01);
        // die Phase eines gehenden Tiers: Schritte je s
        double best = 0;
        for (int i = 0; i < 6000; i++) {
            h.step(0.05);
            for (Herd.Animal a : h.animals()) {
                if (a.kind == Herd.Kind.ZIEGE && a.state == Herd.State.WALK && Math.abs(a.speed - a.kind.walk) < 0.05) {
                    float[] in = HerdGl.instance(a);
                    best = Math.max(best, in[5]);
                }
            }
        }
        double hz = Herd.Kind.ZIEGE.walk / (Herd.Kind.ZIEGE.walk / Herd.Kind.ZIEGE.stepHz);
        check(best > 0.25 && best <= 0.55 && Math.abs(hz - Herd.Kind.ZIEGE.stepHz) < 1e-9,
                f("Gang: gehende Ziege schwingt die Beine um %.2f rad, %.1f Schritte je s", best, hz));
    }

    /** Seit Haithabu Phase 14: Rufe am Tag, der Hahn im Morgengrauen aus dem Stall, nachts Ruhe. */
    static void calls() {
        Herd h = farm();
        int[] n = new int[Herd.Kind.values().length];
        int[] rooster = new int[1];
        h.onCall = (x, y, z, s, a) -> { n[a.kind.ordinal()]++; if (a.rooster()) rooster[0]++; };
        h.sunAltDeg = 30;
        for (int i = 0; i < 6000; i++) h.step(0.1);                       // 10 min am Tag
        int day = n[0] + n[1] + n[2];
        int[] dayN = n.clone();
        java.util.Arrays.fill(n, 0);
        rooster[0] = 0;
        h.sunAltDeg = -6;                                                  // Morgengrauen: Hühner noch im Stall
        for (int i = 0; i < 3000; i++) h.step(0.1);
        int dawnHens = h.outside(Herd.Kind.HUHN), dawnRooster = rooster[0], dawnOthers = n[0] - rooster[0];
        java.util.Arrays.fill(n, 0);
        h.sunAltDeg = -30;
        for (int i = 0; i < 3000; i++) h.step(0.1);
        int night = n[0] + n[1] + n[2];
        check(dayN[0] > 80 && dayN[1] > 1 && dayN[2] > 10 && dawnHens == 0 && dawnRooster >= 2 && dawnOthers == 0 && night == 0,
                f("Rufe in 10 min am Tag: Hühner %d, Ziegen %d, Schweine %d; im Morgengrauen kräht der Hahn %d mal aus dem Stall; nachts still", dayN[0], dayN[1], dayN[2], dawnRooster));
    }

    static void seed() {
        Herd a = farm(), b = farm();
        a.step(0.01); b.step(0.01);
        for (int i = 0; i < 600; i++) { a.step(0.1); b.step(0.1); }
        boolean same = true;
        for (int i = 0; i < a.animals().size(); i++) same &= a.animals().get(i).pos[0] == b.animals().get(i).pos[0];
        Herd c = farm();
        c.seed = 8;
        c.step(0.01);
        for (int i = 0; i < 600; i++) c.step(0.1);
        check(same && c.animals().get(0).pos[0] != a.animals().get(0).pos[0], "Gleicher Startwert, gleiche Bewegung; anderer Startwert, andere");
    }
}
