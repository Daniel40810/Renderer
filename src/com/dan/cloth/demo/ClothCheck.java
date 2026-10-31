package com.dan.cloth.demo;

import com.dan.cloth.Cloth;

import java.util.Locale;

/**
 * Prüfung ohne Fenster (Paketvertrag Punkt 10) für {@code cloth} (seit Timmendorf Phase 6 mit XPBD): Wiederholbarkeit,
 * Ränder, Durchhang gegen die Kettenlinie, Pendel, Frequenz einer Flagge gegen die Strouhal-Zahl, Energie bleibt
 * beschränkt, große Schritte ohne Explosion, Kraft an der Stange gleich dem Gewicht, Leinen, Reffen, alle Presets.
 * Endet mit „Alles in Ordnung.“ oder mit den Verstößen (Rückgabewert 1).
 */
public final class ClothCheck {
    private static int bad, good;

    public static void main(String[] args) {
        System.out.println("com.dan.cloth – Prüfung");
        grundlagen();
        kettenlinie();
        pendel();
        flattern();
        robust();
        raender();
        System.out.println();
        System.out.println(good + " Prüfungen bestanden, " + bad + " fehlgeschlagen.");
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static Cloth.Wind still() { return (x, y, z, t, o) -> { o[0] = 0; o[1] = 0; o[2] = 0; }; }

    static Cloth.Wind west(double u) { return (x, y, z, t, o) -> { o[0] = u; o[1] = 0; o[2] = 0; }; }

    static void grundlagen() {
        System.out.println("Grundlagen");
        Cloth a = Cloth.beachFlag(0, 0, 0), b = Cloth.beachFlag(0, 0, 0);
        a.wind = b.wind = west(7);
        double[] p0 = a.point(0, 0);
        for (int i = 0; i < 300; i++) { a.step(1 / 60.0); b.step(1 / 60.0); }
        check(java.util.Arrays.equals(a.pos, b.pos), "gleiche Eingänge, gleiches Tuch");
        double[] p1 = a.point(0, 0);
        check(p0[0] == p1[0] && p0[1] == p1[1] && p0[2] == p1[2], "Punkte an der Stange bleiben fest");
        check(a.maxStretch() < 0.03, f("Fahnentuch dehnt sich kaum (größte Dehnung %.2f %%)", a.maxStretch() * 100));
        check(finite(a), "keine NaN oder Unendlichkeiten");
        double tip = 0;
        for (int i = 0; i < a.nu; i++) tip += a.point(i, a.nv - 1)[0] / a.nu;
        check(tip > 1.0, f("die Flagge weht im Westwind nach Osten aus (freie Kante bei x = %.2f m von 1,5 m)", tip));
        Cloth s = Cloth.beachFlag(0, 0, 0);
        s.wind = still();
        s.damping = 1.5;
        for (int i = 0; i < 900; i++) s.step(1 / 60.0);
        double[] hang = s.point(s.nu / 2, s.nv - 1);
        check(Math.abs(hang[0]) < 0.35 && hang[1] < 6 - 0.9, f("ohne Wind hängt sie an der Stange (freie Ecke x = %.2f m, y = %.2f m)", hang[0], hang[1]));
        check(s.meanSpeed(1 / 60.0) < 0.05, f("ohne Wind kommt sie zur Ruhe (%.3f m/s)", s.meanSpeed(1 / 60.0)));
        double[] fp = s.pinForce();
        double wgt = s.freeWeight();
        check(Math.abs(-fp[1] - wgt) < 0.05 * wgt, f("die Stange trägt das Gewicht des Tuchs (%.3f N, Gewicht %.3f N)", -fp[1], wgt));
        Cloth q = Cloth.beachFlag(0, 0, 0);
        q.quality = Cloth.Quality.LOW;
        int lo = q.substeps();
        q.quality = Cloth.Quality.ULTRA;
        check(lo < q.substeps(), f("Stufen ändern die Zahl der Unterschritte (%d bis %d je 1/60 s)", lo, q.substeps()));
        Cloth n = Cloth.beachFlag(0, 0, 0), d = Cloth.beachFlag(0, 0, 0);
        n.wind = d.wind = still();
        d.wetness = 1;
        check(Math.abs(d.freeWeight() / n.freeWeight() - 2.5) < 1e-9, "nass ist das Tuch zweieinhalbmal so schwer");
    }

    /**
     * Ein Band hängt zwischen zwei Punkten: Durchhang gegen die Kettenlinie y = a·(cosh(x/a) − 1) mit der Länge
     * S = 2·a·sinh(L / 2a).
     */
    static void kettenlinie() {
        System.out.println("Kettenlinie");
        double span = 4, len = 4.4;
        Cloth c = Cloth.strip(span, len, 0.05, 41);
        c.wind = still();
        c.flutter = 0;
        c.damping = 3;
        c.quality = Cloth.Quality.ULTRA;
        c.iterations = 6;
        for (int i = 0; i < 1500; i++) c.step(1 / 60.0);
        double low = 0;
        for (int i = 0; i < c.nu; i++) low = Math.min(low, c.point(i, 0)[1]);
        double a = 1;
        for (int it = 0; it < 200; it++) {   // Newton für S = 2a·sinh(L/2a)
            double g = 2 * a * Math.sinh(span / (2 * a)) - len, dg = 2 * Math.sinh(span / (2 * a)) - span / a * Math.cosh(span / (2 * a));
            a -= g / dg;
        }
        double sag = a * (Math.cosh(span / (2 * a)) - 1);
        check(Math.abs(-low - sag) < 0.04 * sag, f("Durchhang %.3f m, Kettenlinie %.3f m (a = %.3f m)", -low, sag, a));
        check(c.maxStretch() < 0.01, f("das Band bleibt fast undehnbar (%.2f %%)", c.maxStretch() * 100));
    }

    /** Ein Pendel aus zwei Punkten an einem Faden der Länge L: Schwingungsdauer 2π·√(L/g) bei kleinem Ausschlag. */
    static Cloth pendelTuch(double l, double deg) {
        Cloth c = new Cloth(2, 2, l, 0.01);
        double s = Math.sin(Math.toRadians(deg)), co = Math.cos(Math.toRadians(deg));
        for (int j = 0; j < 2; j++) {
            c.moveTo(j * 2, 0, 0, j * 0.01);
            c.moveTo(j * 2 + 1, l * s, -l * co, j * 0.01);
            c.pin(j * 2, true);
        }
        c.setRest().finish();
        System.arraycopy(c.pos, 0, c.prev, 0, c.pos.length);
        c.wind = still();
        c.airDensity = 0;
        c.flutter = 0;
        c.damping = 0;
        c.stretchCompliance = 0;
        c.quality = Cloth.Quality.ULTRA;
        return c;
    }

    static void pendel() {
        System.out.println("Pendel");
        double l = 1.0;
        Cloth c = pendelTuch(l, 5);
        double dt = 1 / 600.0, t = 0, lastX = c.pos[3], first = -1, lastCross = -1;
        int crossings = 0;
        for (int i = 0; i < 6000; i++) {
            c.step(dt);
            t += dt;
            double x = c.pos[3];
            if (lastX > 0 && x <= 0) {
                double tc = t - dt * x / (x - lastX);
                if (first < 0) first = tc; else { crossings++; lastCross = tc; }
            }
            lastX = x;
        }
        double period = crossings > 0 ? (lastCross - first) / crossings : 0, soll = 2 * Math.PI * Math.sqrt(l / Cloth.G);
        check(Math.abs(period / soll - 1) < 0.02, f("Schwingungsdauer %.4f s, 2π·√(L/g) = %.4f s (%d Schwingungen)", period, soll, crossings));
        Cloth e = pendelTuch(l, 60);
        double e0 = e.energy(), emax = e0, emin = e0;
        for (int i = 0; i < 6000; i++) {
            e.step(1 / 600.0);
            emax = Math.max(emax, e.energy());
            emin = Math.min(emin, e.energy());
        }
        check(emax <= e0 + 0.01 * Math.abs(e.freeWeight() * l), f("Energie bleibt beschränkt (größte %.4f J, Anfang %.4f J)", emax, e0));
        check(emin > e0 - 0.25 * Math.abs(e.freeWeight() * l), f("und versickert nicht sofort (kleinste %.4f J nach 10 s)", emin));
    }

    /** Flagge bei 8 m/s: Frequenz der Querbewegung an der freien Kante gegen St·U / A. */
    static void flattern() {
        System.out.println("Flattern");
        double u = 8;
        Cloth c = Cloth.beachFlag(0, 0, 0);
        c.wind = west(u);
        for (int i = 0; i < 180; i++) c.step(1 / 60.0);
        int n = 600;
        double[] z = new double[n];
        double mean = 0;
        int k = (c.nv - 1) * c.nu + c.nu / 2;
        for (int i = 0; i < n; i++) {
            c.step(1 / 120.0);
            z[i] = c.pos[k * 3 + 2];
            mean += z[i] / n;
        }
        int cross = 0;
        for (int i = 1; i < n; i++) if ((z[i - 1] - mean) < 0 && (z[i] - mean) >= 0) cross++;
        double freq = cross / (n / 120.0), soll = c.flutterFrequency(u);
        check(Math.abs(freq / soll - 1) < 0.25, f("Flattern %.2f Hz, Strouhal St·U/A = %.2f Hz (St %.2f, U %.0f m/s)", freq, soll, Cloth.STROUHAL, u));
        double amp = 0;
        for (double v : z) amp = Math.max(amp, Math.abs(v - mean));
        check(amp > 0.03 && amp < 0.6, f("Ausschlag an der freien Kante %.2f m", amp));
        int flaps[] = {0};
        Cloth g = Cloth.beachFlag(0, 0, 0);
        g.wind = west(14);
        g.onFlap = (x, y, zz, s) -> flaps[0]++;
        for (int i = 0; i < 240; i++) g.step(1 / 60.0);
        check(flaps[0] > 0, f("bei 14 m/s schlägt das Tuch hörbar (%d Ereignisse in 4 s)", flaps[0]));
        Cloth h = Cloth.beachFlag(0, 0, 0);
        h.wind = west(14);
        for (int i = 0; i < 240; i++) h.step(1 / 60.0);
        double[] fp = h.pinForce();
        // Widerstand einer flatternden Flagge: c_D um 0,1 bis 0,4 auf die Fläche [G, zu prüfen]
        double q = 0.5 * 1.225 * 14 * 14 * 1.5, cd = fp[0] / q;
        check(cd > 0.02 && cd < 0.8, f("Zug an der Stange in Windrichtung %.1f N (c_D %.2f)", fp[0], cd));
    }

    static void robust() {
        System.out.println("Große Schritte, Sturm");
        Cloth c = Cloth.beachFlag(0, 0, 0);
        c.wind = (x, y, z, t, o) -> { o[0] = 25 + 8 * Math.sin(t * 3); o[1] = 2 * Math.sin(t * 5); o[2] = 6 * Math.sin(t * 1.3); };
        for (int i = 0; i < 300; i++) c.step(1 / 20.0);
        check(finite(c) && c.maxStretch() < 0.1, f("Bft 10 mit Böen in Schritten zu 1/20 s: endlich, Dehnung %.1f %%", c.maxStretch() * 100));
        Cloth l = Cloth.beachFlag(0, 0, 0);
        l.quality = Cloth.Quality.LOW;
        l.wind = west(20);
        for (int i = 0; i < 300; i++) l.step(1 / 30.0);
        check(finite(l) && l.maxStretch() < 0.1, f("Stufe niedrig bei 20 m/s und 30 Bildern/s: Dehnung %.1f %%", l.maxStretch() * 100));
        for (Object[] p : new Object[][]{{"Wimpel", Cloth.pennant(0, 4, 0, 1.2, 0.3)}, {"Markise", Cloth.awning(0, 1.4, 0, 1.2, 0.6)},
                {"Sonnenschirm", Cloth.parasol(0, 2.2, 0, 1.0)}, {"Handtuch", Cloth.towel(0, 0, 0)}}) {
            Cloth m = (Cloth) p[1];
            m.wind = west(9);
            m.ground = (x, z) -> 0;
            for (int i = 0; i < 300; i++) m.step(1 / 60.0);
            double low = Double.MAX_VALUE;
            for (int k = 1; k < m.pos.length; k += 3) low = Math.min(low, m.pos[k]);
            check(finite(m) && m.maxStretch() < 0.15 && low >= 0, f("%s bei 9 m/s: endlich, Dehnung %.1f %%, tiefster Punkt %.2f m", p[0], m.maxStretch() * 100, low));
        }
    }

    static void raender() {
        System.out.println("Ränder: Leinen, Reffen, Mitnehmen");
        Cloth a = Cloth.awning(0, 1.4, 0, 1.2, 0.6);
        a.wind = (x, y, z, t, o) -> { o[0] = 0; o[1] = 12; o[2] = 0; };   // Fallböe von unten
        for (int i = 0; i < 300; i++) a.step(1 / 60.0);
        int c0 = (a.nv - 1) * a.nu;
        double[] an = {a.tether[c0 * 4], a.tether[c0 * 4 + 1], a.tether[c0 * 4 + 2]};
        double dd = Math.sqrt(Math.pow(a.pos[c0 * 3] - an[0], 2) + Math.pow(a.pos[c0 * 3 + 1] - an[1], 2) + Math.pow(a.pos[c0 * 3 + 2] - an[2], 2));
        check(dd < a.tether[c0 * 4 + 3] * 1.05, f("die Leine hält die Markise im Aufwind (%.2f m, Leine %.2f m)", dd, a.tether[c0 * 4 + 3]));
        Cloth r = Cloth.beachFlag(0, 0, 0);
        r.wind = west(8);
        for (int i = 0; i < 120; i++) r.step(1 / 60.0);
        r.reef(1);
        for (int i = 0; i < 60; i++) r.step(1 / 60.0);
        double far = 0;
        for (int k = 0; k < r.pinned.length; k++) far = Math.max(far, Math.abs(r.pos[k * 3]));
        check(far < 0.4, f("geborgen liegt das Tuch an der Stange (weitester Punkt %.2f m)", far));
        r.reef(0);
        for (int i = 0; i < 240; i++) r.step(1 / 60.0);
        double tip = r.point(r.nu / 2, r.nv - 1)[0];
        check(tip > 0.9, f("wieder gesetzt weht es aus (%.2f m)", tip));
        Cloth m = Cloth.beachFlag(0, 0, 0);
        m.wind = west(6);
        for (int i = 0; i < 120; i++) { m.translatePinned(0.05, 0, 0); m.step(1 / 60.0); }
        check(finite(m) && m.point(0, 0)[0] > 5.9, f("die Stange fährt 6 m mit, das Tuch folgt (Dehnung %.1f %%)", m.maxStretch() * 100));
    }

    static boolean finite(Cloth c) {
        for (double v : c.pos) if (!Double.isFinite(v)) return false;
        return true;
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMAN, s, a); }

    static void check(boolean c, String what) {
        if (c) good++; else bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private ClothCheck() { }
}
