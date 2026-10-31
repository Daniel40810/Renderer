package com.dan.cliff.demo;

import com.dan.cliff.CNoise;
import com.dan.cliff.Cliff;
import com.dan.cliff.CliffMap;
import com.dan.cliff.Blockfield;
import com.dan.cliff.Overhang;
import com.dan.cliff.Sod;
import com.dan.cliff.StrataMap;
import com.dan.cliff.Tor;

import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder
 * mit Rückgabewert 1. Prüft: Hash und Rauschen (Bereich, Stetigkeit, Seed), Material nach Neigung, keine Streifen an
 * senkrechten Stellen (die Farbe ändert sich entlang einer senkrechten Wand in beiden Richtungen ähnlich stark),
 * Feinform (Höhe, Ausblenden in der Ferne, Neigung der Normale), Wandkarte (Wand, Geröll unter der Wand, Absatz,
 * nasser Streifen), Überhang (ragt hinaus, oben weiter als unten), Stufen, Reproduzierbarkeit, Nässe und Schnee.
 * Seit Brocken Phase 8 außerdem Granit (keine Bänder, Wollsäcke: gerundete Blöcke zwischen Klüften, waagerechte
 * Bankung), Felsburg (Höhe, Lagen, Normalen nach außen, Superellipsoid mit e = 1 eine Kugel) und Blockmeer (gleiche
 * Blöcke bei gleichem Seed, Dichte, keine Blöcke ohne Dichte, in der Ferne nur große).
 * Seit Sylt Phase 4 Lockergestein ({@link Cliff#rotesKliff}): Gneis und Granit unverändert (Prüfsumme über 6000
 * Punkte), Schichten in der Reihenfolge, Lehm nass dunkler, Runsen, Sickerstreifen unter der Grenze, Hohlkehle,
 * Schichtkarte, Grasnarbe, Sturzkegel.
 */
public final class CliffCheck {
    private CliffCheck() { }
    private static int bad;

    public static void main(String[] args) {
        check(CNoise.hash(1, 2, 3) == CNoise.hash(1, 2, 3) && CNoise.hash(1, 2, 3) != CNoise.hash(1, 2, 4), "Hash: gleich bei gleichen Zahlen, anders bei anderen");
        double lo = 1, hi = 0, jump = 0;
        for (int i = 0; i < 20000; i++) {
            double x = i * 0.037, v = CNoise.value(x, x * 0.7, -x * 1.3, 5);
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
            if (i > 0) jump = Math.max(jump, Math.abs(v - CNoise.value(x - 0.037, (x - 0.037) * 0.7, -(x - 0.037) * 1.3, 5)));
        }
        check(lo >= 0 && hi < 1 && hi - lo > 0.8 && jump < 0.12, String.format(Locale.GERMAN, "Rauschen 0..1 (%.3f bis %.3f), stetig (größter Sprung %.3f bei 3,7 cm)", lo, hi, jump));
        check(CNoise.value(10.5, -3.2, 7.7, 1) != CNoise.value(10.5, -3.2, 7.7, 2), "Anderer Seed, anderes Rauschen");

        Cliff c = new Cliff();
        Cliff.Sample flat = c.material(100, 50, 100, 0, 1, 0, null, 0, 0, 0.05), wall = c.material(100, 50, 100, 0.99, 0.14, 0, null, 0, 0, 0.05);
        check(flat.rock < 0.05 && wall.rock > 0.95, String.format(Locale.GERMAN, "Fels nach Neigung: flach %.2f, Wand %.2f", flat.rock, wall.rock));
        check(c.isWall(70) && !c.isWall(30), "Wand ab " + Math.round(c.wallSlopeDeg) + "°");

        // keine Streifen: an einer senkrechten Wand (Normale +x) ändert sich die Farbe hoch und quer ähnlich stark
        double dy = 0, dz = 0;
        int n = 0;
        for (int i = 0; i < 400; i++) {
            double y = 20 + i * 0.73, z = -50 + (i * 37 % 100);
            Cliff.Sample a = c.material(0, y, z, 1, 0, 0, null, 0, 0, 0.05);
            Cliff.Sample b = c.material(0, y + 0.5, z, 1, 0, 0, null, 0, 0, 0.05);
            Cliff.Sample e = c.material(0, y, z + 0.5, 1, 0, 0, null, 0, 0, 0.05);
            dy += Math.abs(lum(a) - lum(b));
            dz += Math.abs(lum(a) - lum(e));
            n++;
        }
        double ratio = Math.max(dy, dz) / Math.max(1e-9, Math.min(dy, dz));
        check(ratio < 2.5, String.format(Locale.GERMAN, "Senkrechte Wand: Wechsel der Farbe hoch %.4f, quer %.4f je 0,5 m (Verhältnis %.2f, ohne Streifen unter 2,5)", dy / n, dz / n, ratio));

        double rmax = 0, rmin = 0, far = 0;
        for (int i = 0; i < 4000; i++) {
            double r = c.relief(i * 1.7, i * 0.9, -i * 1.3, 0.05);
            rmax = Math.max(rmax, r);
            rmin = Math.min(rmin, r);
            far = Math.max(far, Math.abs(c.relief(i * 1.7, i * 0.9, -i * 1.3, 100)));
        }
        check(rmax - rmin > c.reliefAmp * 0.8 && rmax - rmin < c.reliefAmp * 3 && far < 0.01,
                String.format(Locale.GERMAN, "Feinform: %.1f bis %.1f m nah, in der Ferne (1 Bildpunkt = 100 m) aus (%.3f m)", rmin, rmax, far));
        Cliff.Sample n1 = c.material(10, 80, 10, 0.94, 0.34, 0, null, 0, 0, 0.05);
        double tilt = Math.toDegrees(Math.acos(Math.min(1, n1.normal[0] * 0.94 + n1.normal[1] * 0.34)));
        check(tilt > 0.5 && tilt < 60, String.format(Locale.GERMAN, "Die Feinform neigt die Normale (%.1f°)", tilt));

        CliffMap m = CliffMap.build(c, DemoWall::height, -600, -600, 2.5, 480);
        m.addFall(0, 20, 290, 10, 1);
        double[] atWall = m.valuesAt(0, DemoWall.zAt(0, 150)), atFloor = m.valuesAt(200, 400), atFall = m.valuesAt(0, DemoWall.zAt(0, 100));
        check(atWall[0] > 0.8 && atFloor[0] < 0.1, String.format(Locale.GERMAN, "Wandkarte: Wand %.2f an der Wand, %.2f im Tal", atWall[0], atFloor[0]));
        check(m.talusShare > 0.005 && atFloor[1] < 0.05, String.format(Locale.GERMAN, "Geröll am Fuß (%.1f %% der Fläche), nicht mitten im Tal (%.2f)", m.talusShare * 100, atFloor[1]));
        check(atFall[2] > 0.5 && m.valuesAt(300, DemoWall.zAt(300, 100))[2] < 0.1, String.format(Locale.GERMAN, "Nasser Streifen unter dem Fall (%.2f), daneben trocken", atFall[2]));
        check(m.ledgeShare > 0.0005, String.format(Locale.GERMAN, "Absätze in der Wand: %.2f %% der Fläche", m.ledgeShare * 100));

        Overhang o = new Overhang(0, 260, -100, 180, 40, 24, 10, 7);
        Overhang.Mesh mesh = o.mesh(20);
        double top = o.protrusion(0, 0.8), bottom = o.protrusion(0, -0.8);
        check(o.maxReach() > 6 && top > bottom && mesh.vertexCount() == 400 && mesh.indices.length == 19 * 19 * 6,
                String.format(Locale.GERMAN, "Überhang ragt %.1f m hinaus, oben %.1f m, unten %.1f m (hohl), Netz %d Ecken", o.maxReach(), top, bottom, mesh.vertexCount()));
        int outward = 0;
        for (int i = 0; i < mesh.vertexCount(); i++) if (mesh.vertices[i * 6 + 5] > -0.2) outward++;   // Blick nach Süden (+z)
        check(outward > mesh.vertexCount() * 0.9, "Normalen des Überhangs zeigen aus der Wand (" + outward + " von " + mesh.vertexCount() + ")");

        check(Cliff.Quality.values().length == 4 && Cliff.Quality.LOW.octaves < Cliff.Quality.ULTRA.octaves && !Cliff.Quality.LOW.overhangs, "Vier Stufen: Oktaven steigen, Niedrig ohne Überhänge");
        Cliff c2 = new Cliff();
        check(lum(c2.material(5, 6, 7, 0, 0.3, 0.95, null, 0, 0, 0.05)) == lum(c.material(5, 6, 7, 0, 0.3, 0.95, null, 0, 0, 0.05)), "Gleicher Seed, gleiche Farbe");
        c2.seed = 2;
        check(lum(c2.material(5, 6, 7, 0, 0.3, 0.95, null, 0, 0, 0.05)) != lum(c.material(5, 6, 7, 0, 0.3, 0.95, null, 0, 0, 0.05)), "Anderer Seed, andere Farbe");
        Cliff.Sample dry = c.material(3, 40, 3, 1, 0, 0, null, 0, 0, 0.05), wet = c.material(3, 40, 3, 1, 0, 0, null, 1, 0, 0.05);
        check(lum(wet) < lum(dry) * 0.75, String.format(Locale.GERMAN, "Nach Regen dunkler (%.3f statt %.3f)", lum(wet), lum(dry)));
        Cliff.Sample sn = c.material(3, 40, 3, 0, 1, 0, null, 0, 1, 0.05);
        check(sn.snow > 0.3 && lum(sn) > lum(flat), String.format(Locale.GERMAN, "Schnee auf flachen Stellen (%.2f)", sn.snow));
        granit();
        locker();
        System.out.println(c.describe());
        System.out.println(m.describe());
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Fehler.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Seit Brocken Phase 8: Granit, Felsburg, Blockmeer. */
    static void granit() {
        Cliff g = Cliff.granit();
        // keine Bänder: senkrechte Wand, Farbe hoch und quer ähnlich
        double dy = 0, dz = 0;
        for (int i = 0; i < 400; i++) {
            double y = 20 + i * 0.73, z = -50 + (i * 37 % 100);
            Cliff.Sample a = g.material(0, y, z, 1, 0, 0, null, 0, 0, 0.05);
            dy += Math.abs(lum(a) - lum(g.material(0, y + 0.5, z, 1, 0, 0, null, 0, 0, 0.05)));
            dz += Math.abs(lum(a) - lum(g.material(0, y, z + 0.5, 1, 0, 0, null, 0, 0, 0.05)));
        }
        double ratio = Math.max(dy, dz) / Math.max(1e-9, Math.min(dy, dz));
        check(g.rock == Cliff.Rock.GRANIT && ratio < 2.5, String.format(Locale.GERMAN, "Granit ohne Bänder (Verhältnis hoch/quer %.2f)", ratio));
        // Wollsäcke: entlang einer Linie quer zu den Klüften wechseln volle Blöcke (≈1) und schmale Fugen (klein)
        int joint = 0, full = 0, n = 4000;
        for (int i = 0; i < n; i++) {
            double b = g.granitBlock(i * 0.02, 30.3 + i * 0.007, 7.1 + i * 0.013, 0.02);
            if (b < 0.25) joint++;
            if (b > 0.9) full++;
        }
        double js = joint / (double) n, fs = full / (double) n;
        check(js > 0.03 && js < 0.4 && fs > 0.3, String.format(Locale.GERMAN, "Wollsäcke: %.0f %% Fuge, %.0f %% voller Block", js * 100, fs * 100));
        // Bankung: senkrecht entlang einer Wand gibt es Fugen auch ohne senkrechte Kluft daneben
        int sheet = 0;
        for (int i = 0; i < 2000; i++) if (g.granitBlock(3.3, 10 + i * 0.01, 4.4, 0.02) < 0.25) sheet++;
        check(sheet > 20, String.format(Locale.GERMAN, "Waagerechte Bankung: %d von 2000 Punkten senkrecht in einer Fuge", sheet));
        check(Math.abs(g.granitBlock(1, 2, 3, 500) - 0.79) < 0.05, "Wollsäcke in der Ferne ausgeblendet");
        // Felsburg
        Tor t = new Tor(10, 100, -5, 6, 5, 30, 4);
        double top = t.top();
        Overhang.Mesh mesh = t.mesh(16);
        int out = 0, nv = mesh.vertexCount();
        java.util.List<Tor.Block> bs = t.blocks();
        int per = nv / bs.size();
        for (int k = 0; k < nv; k++) {
            Tor.Block b = bs.get(k / per);
            float[] v = mesh.vertices;
            double dx = v[k * 6] - b.cx(), dyy = v[k * 6 + 1] - b.cy(), dzz = v[k * 6 + 2] - b.cz();
            if (dx * v[k * 6 + 3] + dyy * v[k * 6 + 4] + dzz * v[k * 6 + 5] >= -1e-6) out++;
        }
        check(top > 100 + 6 * 0.8 && top < 100 + 6 * 1.2 && bs.size() >= t.layers, String.format(Locale.GERMAN, "Felsburg: %d Blöcke in %d Lagen, Oberkante %.1f m über dem Fuß (soll 6)", bs.size(), t.layers, top - 100));
        check(out > nv * 0.98, "Normalen der Felsburg zeigen nach außen (" + out + " von " + nv + ")");
        double[] sp = Tor.superellipsoid(0.4, 1.1, 2, 2, 2, 1);
        check(Math.abs(Math.sqrt(sp[0] * sp[0] + sp[1] * sp[1] + sp[2] * sp[2]) - 2) < 1e-9, "Superellipsoid mit e = 1 ist eine Kugel");
        check(new Tor(10, 100, -5, 6, 5, 30, 4).blocks().equals(bs), "Gleicher Seed, gleiche Felsburg");
        // Blockmeer
        Blockfield f = new Blockfield();
        f.density = (x, z) -> x > 0 ? 1 : 0;
        f.radius = 200;
        float[] a = f.instances(0, 0, 1e-6).clone();
        int na = f.count();
        Blockfield f2 = new Blockfield();
        f2.density = f.density;
        f2.radius = 200;
        float[] b = f2.instances(0, 0, 1e-6);
        boolean same = f2.count() == na && java.util.Arrays.equals(java.util.Arrays.copyOf(a, na * 8), java.util.Arrays.copyOf(b, na * 8));
        boolean east = true;
        for (int k = 0; k < na; k++) east &= a[k * 8] > 0;
        double expect = f.perM2 * Math.PI * 200 * 200 / 2;
        check(same && east, String.format(Locale.GERMAN, "Blockmeer: gleiche Blöcke bei gleichem Seed, nur wo Dichte (%d Blöcke)", na));
        check(Math.abs(na - expect) < expect * 0.1, String.format(Locale.GERMAN, "Blockmeer: %d Blöcke auf der halben Scheibe, erwartet %.0f (%.0f je ha)", na, expect, f.perHectare()));
        int v0 = f.version();
        f.instances(3, 2, 1e-6);
        boolean cached = f.version() == v0;
        f.instances(0, 0, 1e-3);
        int nFar = f.count();
        check(cached && nFar < na && nFar > na / 50, String.format(Locale.GERMAN, "Blockmeer: unter 8 m Weg kein neues Feld; bei 1 mrad je Bildpunkt %d statt %d Blöcke", nFar, na));
    }

    /** Prüfsumme über Material und Relief von Gneis und Granit an 6000 Punkten (Stand vor Sylt Phase 4). */
    static final long GOLDEN = 0x33bca6bbc98c973eL;

    static long golden() {
        Cliff[] cs = {new Cliff(), Cliff.granit()};
        java.util.Random r = new java.util.Random(7);
        double[] map = {0.8, 0.3, 0.4, 0.2};
        long h = 1125899906842597L;
        for (Cliff c : cs)
            for (int i = 0; i < 3000; i++) {
                double x = r.nextDouble() * 600 - 300, y = r.nextDouble() * 300, z = r.nextDouble() * 600 - 300;
                double sl = Math.toRadians(r.nextDouble() * 90), az = r.nextDouble() * 6.28;
                double nx = Math.sin(sl) * Math.sin(az), ny = Math.cos(sl), nz = -Math.sin(sl) * Math.cos(az);
                double fp = Math.pow(10, -2 + r.nextDouble() * 3);
                Cliff.Sample s = c.material(x, y, z, nx, ny, nz, (i % 2 == 0) ? map : null, r.nextDouble() * 0.5, (i % 3 == 0) ? 0.5 : 0, fp);
                double[] v = {s.albedo[0], s.albedo[1], s.albedo[2], s.normal[0], s.normal[1], s.normal[2], s.rock, s.wet, s.snow, c.relief(x, y, z, fp)};
                for (double d : v) h = 31 * h + Double.doubleToLongBits(Math.round(d * 1e9) / 1e9);
            }
        return h;
    }

    /** Seit Sylt Phase 4: Lockergestein (Rotes Kliff). */
    static void locker() {
        long g = golden();
        check(g == GOLDEN, String.format("Gneis und Granit unverändert (Prüfsumme %016x)", g));
        Cliff k = Cliff.rotesKliff();
        k.strataDefault = new double[]{28, 4, 1.5, 0};
        // senkrechte Wand nach Süden; Mittel über viele Punkte je Tiefe
        double[] lumAt = new double[6], red = new double[6];
        double[] ys = {28.05, 27.3, 26.05, 15, 6, 2};    // Narbe, Flugsand, Ortstein, Lehm, Lehm, Kaolinsand
        for (int t = 0; t < ys.length; t++) {
            for (int i = 0; i < 200; i++) {
                Cliff.Sample s = k.material(i * 0.83, ys[t] + (i % 7) * 0.003, 0, 0, 0.17, 0.985, null, 0, 0, 0.02);
                lumAt[t] += lum(s) / 200;
                red[t] += s.albedo[0] / Math.max(1e-4, s.albedo[2]) / 200;
            }
        }
        check(lumAt[0] < lumAt[1] * 0.5 && lumAt[1] > 0.3 && lumAt[2] < 0.2 && red[3] > 5 && red[4] > 5 && lumAt[5] > 0.4,
                String.format(Locale.GERMAN, "Schichten von oben: Narbe %.2f, Flugsand %.2f, Ortstein %.2f, Lehm %.2f (Rot/Blau %.0f), Kaolinsand %.2f", lumAt[0], lumAt[1], lumAt[2], lumAt[3], red[3], lumAt[5]));
        Cliff.Sample dry = k.material(3, 15, 0, 0, 0.17, 0.985, null, 0, 0, 0.05), wet = k.material(3, 15, 0, 0, 0.17, 0.985, null, 1, 0, 0.05);
        check(lum(wet) < lum(dry) * 0.65 && wet.albedo[2] / wet.albedo[0] < dry.albedo[2] / dry.albedo[0],
                String.format(Locale.GERMAN, "Nasser Lehm dunkler und satter (%.3f statt %.3f)", lum(wet), lum(dry)));
        // Runsen: eingetieft, ein Teil der Wand
        int in = 0;
        double rIn = 0, rOut = 0;
        int nOut = 0;
        for (int i = 0; i < 3000; i++) {
            double x = i * 0.37, y = 5 + (i % 40) * 0.5;
            double gv = k.gully(x, y, 0);
            double r = k.relief(x, y, 0, 0.01);
            if (gv > 0.5) { in++; rIn += r; } else if (gv < 0.05) { nOut++; rOut += r; }
        }
        check(in > 150 && in < 1500 && rIn / Math.max(1, in) < rOut / Math.max(1, nOut) - 0.5,
                String.format(Locale.GERMAN, "Runsen: %.0f %% der Wand, im Mittel %.2f m tiefer", in / 30.0, rOut / Math.max(1, nOut) - rIn / Math.max(1, in)));
        // Sickerstreifen: gleich unter der Grenze Sand/Lehm, nicht 15 m tiefer
        double topLehm = 28 - k.sodThick - 1.5 - k.bleachThick - k.podsolThick - k.stoneThick;
        double wHi = 0, wLo = 0;
        for (int i = 0; i < 2000; i++) {
            wHi = Math.max(wHi, k.material(i * 0.41, topLehm - 1.5, 0, 0, 0.17, 0.985, null, 0, 0, 0.05).wet);
            wLo = Math.max(wLo, k.material(i * 0.41, topLehm - 15, 0, 0, 0.17, 0.985, null, 0, 0, 0.05).wet);
        }
        check(wHi > 0.3 && wLo < 0.01, String.format(Locale.GERMAN, "Sickerstreifen unter der Grenze Sand/Lehm (bis %.2f), 15 m tiefer trocken (%.2f)", wHi, wLo));
        // seit Timmendorf Phase 9: Brodtener Ufer – Geschiebemergel ohne untere Schicht, braun-grau statt rostrot
        Cliff b = Cliff.brodtenerUfer();
        double bl = 0, br = 0, bt = 0;
        for (int i = 0; i < 200; i++) {
            Cliff.Sample s = b.material(i * 0.83, 2 + (i % 13) * 0.9, 0, 0, 0.17, 0.985, null, 0, 0, 0.02);
            bl += lum(s) / 200;
            br += s.albedo[0] / Math.max(1e-4, s.albedo[2]) / 200;
            bt += lum(b.material(i * 0.83, 17.98, 0, 0, 0.17, 0.985, null, 0, 0, 0.02)) / 200;
        }
        check(b.rock == Cliff.Rock.LOCKER && b.isWall(50) && !b.isWall(35) && bl > 0.18 && bl < 0.4 && br > 1.2 && br < 3 && bt < bl * 0.6,
                String.format(Locale.GERMAN, "Brodtener Ufer: Mergel bis zum Strand (Helligkeit %.2f, Rot/Blau %.1f statt rostrot), Narbe oben dunkel (%.2f), Wand ab %.0f°", bl, br, bt, b.wallSlopeDeg));
        // Schichtkarte und Hohlkehle
        Cliff.Heights h = (x, z) -> z < 0 ? 25 : z > 15 ? 1.5 : 25 - 23.5 * z / 15;
        StrataMap m = StrataMap.build(h, -50, -50, 1, 100, 120, 20, (x, z) -> 5, null, 1.5);
        double[] v = m.valuesAt(0, 10);
        int ver = m.version;
        m.paintNotch(0, 14, 4, 1.2);
        check(Math.abs(v[0] - 25) < 0.02 && Math.abs(v[1] - 5) < 0.01 && Math.abs(v[2] - 1.5) < 0.01 && m.version > ver,
                String.format(Locale.GERMAN, "Schichtkarte: Oberkante %.2f m (25), untere Grenze %.2f m, Decke %.2f m; %s", v[0], v[1], v[2], m.describe()));
        k.strata = m;
        double yMid = k.notchBase + k.notchHeight * Math.pow(0.5, 1 / 0.7);
        double dMid = k.shape(0, yMid, 14, 0.01) - k.relief(0, yMid, 14, 0.01), dTop = k.shape(0, k.notchBase + k.notchHeight + 0.5, 14, 0.01) - k.relief(0, k.notchBase + k.notchHeight + 0.5, 14, 0.01);
        check(dMid < -1.0 && Math.abs(dTop) < 1e-9, String.format(Locale.GERMAN, "Hohlkehle: %.2f m zurück in %.1f m, darüber 0", dMid, yMid));
        k.strata = null;
        // Grasnarbe
        double[] e = new double[200 * 5];
        for (int i = 0; i < 200; i++) { e[i * 5] = i * 0.5; e[i * 5 + 1] = 25; e[i * 5 + 2] = 0; e[i * 5 + 3] = 0; e[i * 5 + 4] = 1; }
        Sod sod = new Sod(e, 3);
        double[] st = sod.stats();
        Overhang.Mesh sm = sod.mesh();
        double up = 0;
        for (int i = 0; i < sm.vertexCount(); i++) if (i % 6 == 1) up += sm.vertices[i * 6 + 4];
        check(st[0] > 0.3 && st[0] < 1.6 && st[1] > 0.4 && st[1] < 0.95 && up / 200 > 0.7,
                String.format(Locale.GERMAN, "Grasnarbe: Ausladung bis %.2f m, %.0f %% der Kante mit Überhang, oben Normalen nach oben (%.2f)", st[0], st[1] * 100, up / 200));
        // Sturzkegel am Fuß des Kliffs der Vorführung
        CliffMap cm = CliffMap.build(k, DemoWall::kliff, -100, -100, 1, 200, 4, 18);
        double tal = 0;
        for (double x = -80; x <= 80; x += 2) tal = Math.max(tal, cm.valuesAt(x, DemoWall.kliffFoot(x) + 1)[1]);
        check(tal > 0.3, String.format(Locale.GERMAN, "Sturzkegel und Sandfuß am Fuß (Geröll bis %.2f)", tal));
    }

    static double lum(Cliff.Sample s) { return 0.2126 * s.albedo[0] + 0.7152 * s.albedo[1] + 0.0722 * s.albedo[2]; }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }
}
