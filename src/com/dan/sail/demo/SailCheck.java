package com.dan.sail.demo;

import com.dan.sail.Sail;
import com.dan.sail.Sail.Rig;

import java.util.Locale;

/**
 * Prüfung ohne Fenster (Paketvertrag Punkt 10) für {@code sail} (Physik seit Timmendorf Phase 7). Prüft den
 * scheinbaren Wind, die Beiwerte, die Polaren gegen Literaturwerte, Wendewinkel, Krängung gegen die Handrechnung, die
 * Bewegung über die Zeit (läuft auf die Polare zu, bleibt beschränkt) und die Ereignisse. Endet mit „Alles in Ordnung.“
 * oder mit den Verstößen (Rückgabewert 1).
 * <p>
 * Literatur: ORC One-Design-Zertifikat J/70 (data.orc.org, j70.od 2019): Kreuz-VMG, Fahrt bei 90° und Vorwind-VMG bei 8,
 * 12, 16 kn. RYA Portsmouth Numbers 2025: Optimist 1640, ILCA 7 1100, 420 1107 (Zeit auf der Bahn ∝ PN). Für Hobie 16 und
 * die Fahrtenyacht Bereiche [G, zu prüfen].
 * <p>
 * Seit Phase 8: Rahsegel, Gaffel und Kite (Bereiche [G]), die Polare ohne Stufen, Halse mit Baum, Kentern und
 * Aufrichten in der Bö, Patzer der Crew, und eine Regatta: ein Feld J/70 segelt zwei Runden.
 */
public final class SailCheck {
    private static int bad, good;
    static final double K = Sail.KN;

    public static void main(String[] args) {
        System.out.println("com.dan.sail – Prüfung");
        wind();
        Sail s = new Sail();
        s.quality = Sail.Quality.MEDIUM;
        polaren(s);
        gleichgewicht(s);
        bewegung(s);
        takelagen(s);
        manoever();
        regatta();
        System.out.println();
        System.out.println(good + " Prüfungen bestanden, " + bad + " fehlgeschlagen.");
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void wind() {
        System.out.println("Scheinbarer Wind und Beiwerte");
        double[] aw = Sail.apparentWind(6, 90, 3, 0);
        check(Math.abs(aw[0] - Math.hypot(6, 3)) < 1e-9, f("halber Wind: V_A = √(6² + 3²) = %.3f m/s", aw[0]));
        check(Math.abs(aw[1] - Math.toDegrees(Math.atan2(6, 3))) < 1e-9 && aw[2] > 0, f("… von Steuerbord vorn, β_A = %.1f°", aw[1]));
        double[] gegen = Sail.apparentWind(6, 0, 3, 0), vor = Sail.apparentWind(6, 180, 3, 0);
        check(Math.abs(gegen[0] - 9) < 1e-9 && gegen[1] < 1e-9, "Wind von vorn: Fahrt kommt dazu (9 m/s)");
        check(Math.abs(vor[0] - 3) < 1e-9 && Math.abs(vor[1] - 180) < 1e-9, "Wind von achtern: Fahrt geht ab (3 m/s)");
        double[] c15 = Sail.coefficients(15), c40 = Sail.coefficients(40), c150 = Sail.coefficients(150);
        check(c15[0] < c40[0] && c150[0] < c40[0], "Auftrieb des Riggs am größten zwischen 30° und 60°");
        check(c15[1] < c40[1] && c40[1] < c150[1], "Widerstand wächst zum Vorwind hin");
        check(Sail.RIGS.size() == 9, "neun Takelagen nach F11 A");
        check(Math.abs(Sail.hullSpeed(Sail.J70) - 0.4 * Math.sqrt(9.81 * 6.2)) < 1e-12, f("Rumpfgeschwindigkeit J/70 %.2f m/s", Sail.hullSpeed(Sail.J70)));
    }

    static void polaren(Sail s) {
        System.out.println("Polaren gegen Literatur");
        double[][] orc = {{8, 3.70, 6.22, 4.46}, {12, 4.53, 7.06, 5.52}, {16, 4.66, 8.01, 6.67}};
        for (double[] o : orc) {
            double[] v = s.vmg(Sail.J70, o[0] * K);
            double s90 = s.speed(Sail.J70, o[0] * K, 90)[0] / K;
            check(near(v[1] / K, o[1], 0.12) && near(s90, o[2], 0.12) && near(v[4] / K, o[3], 0.12),
                    f("J/70 bei %.0f kn: Kreuz-VMG %.2f kn (ORC %.2f), 90° %.2f kn (%.2f), Vorwind-VMG %.2f kn (%.2f)", o[0], v[1] / K, o[1], s90, o[2], v[4] / K, o[3]));
        }
        double tw = s.tackAngle(Sail.J70, 12 * K);
        check(near(tw, 75, 0.1), f("Wendewinkel J/70 bei 12 kn %.0f° (ORC 2 × 37,6° = 75°)", tw));
        double ti = bahn(s, Sail.ILCA), to = bahn(s, Sail.OPTIMIST), t4 = bahn(s, Sail.DINGHY_420);
        check(near(to / ti, 1640.0 / 1100, 0.12), f("Optimist gegen ILCA 7 auf der Bahn bei 10 kn: %.3f (Portsmouth 1640/1100 = %.3f)", to / ti, 1640.0 / 1100));
        check(near(t4 / ti, 1107.0 / 1100, 0.12), f("420er gegen ILCA 7: %.3f (Portsmouth 1107/1100 = %.3f)", t4 / ti, 1107.0 / 1100));
        double[] il = s.vmg(Sail.ILCA, 10 * K);
        check(il[1] / K > 2.8 && il[1] / K < 3.8, f("ILCA 7 bei 10 kn: Kreuz-VMG %.2f kn unter %.0f° [G 2,8 bis 3,8]", il[1] / K, il[0]));
        double[] ho = s.vmg(Sail.HOBIE, 10 * K);
        double hmax = max(s, Sail.HOBIE, 15 * K);
        check(ho[1] / K > 4.3 && ho[1] / K < 6.5 && hmax > 14 && hmax < 21,
                f("Hobie 16 bei 10 kn: Kreuz-VMG %.2f kn; bei 15 kn bis %.1f kn [G 4,3 bis 6,5 und 14 bis 21]", ho[1] / K, hmax));
        double[] cr = s.vmg(Sail.CRUISER, 10 * K);
        double cmax = max(s, Sail.CRUISER, 15 * K), hs = Sail.hullSpeed(Sail.CRUISER) / K;
        check(cr[1] / K > 3.8 && cr[1] / K < 5.0 && cmax < hs * 1.12,
                f("Fahrtenyacht bei 10 kn: Kreuz-VMG %.2f kn; bei 15 kn höchstens %.1f kn (Rumpfgeschwindigkeit %.1f kn)", cr[1] / K, cmax, hs));
        double[] op = s.vmg(Sail.OPTIMIST, 10 * K);
        check(op[1] > 0 && op[4] > op[1], f("Optimist: Kreuz-VMG %.2f kn, Vorwind-VMG %.2f kn", op[1] / K, op[4] / K));
        double[][] p = s.polar(Sail.J70, 12 * K);
        boolean dn = false;
        for (double[] r : p) if (r[0] >= 120 && r[5] > 0.5) dn = true;
        check(dn, "J/70 setzt raumschots den Gennaker");
        double u6 = s.vmg(Sail.ILCA, 6 * K)[1], u14 = s.vmg(Sail.ILCA, 14 * K)[1];
        check(u14 > u6, f("mehr Wind, mehr Fahrt am Wind (ILCA %.2f kn bei 6, %.2f kn bei 14 kn)", u6 / K, u14 / K));
        double[] hi = s.speed(Sail.ILCA, 20 * K, 45);
        check(hi[5] < 0.95 && hi[1] <= Sail.ILCA.maxHeelDeg() + 1e-9, f("ILCA bei 20 kn am Wind: entlastet (Flachheit %.0f %%), Krängung %.0f°", hi[5] * 100, hi[1]));
    }

    static void takelagen(Sail s) {
        System.out.println("Takelagen seit Phase 8");
        check(Sail.RIGS.stream().filter(r -> r.plan() != Sail.Plan.BERMUDA).count() == 3, "Segelpläne: Gaffel (Schoner), Rahsegel (Passat), Kite");
        double[] p16 = s.vmg(Sail.PASSAT, 16 * K);
        double pmax = max(s, Sail.PASSAT, 16 * K), p45 = s.speed(Sail.PASSAT, 16 * K, 45)[0] / K;
        check(p16[0] >= 60 && pmax > 6 && pmax < 11 && p45 < 1.5,
                f("Passat bei 16 kn: höchstens %.1f kn [G 6 bis 11], am Wind nicht höher als %.0f° (bei 45° %.1f kn) – Rahsegler kreuzen schlecht", pmax, p16[0], p45));
        double ts = s.tackAngle(Sail.SCHOONER, 12 * K), tj = s.tackAngle(Sail.J70, 12 * K);
        check(ts > tj && ts < 120, f("Gaffelschoner wendet über %.0f°, die J/70 über %.0f°", ts, tj));
        double[] kv = s.vmg(Sail.KITE, 16 * K);
        double kmax = max(s, Sail.KITE, 16 * K);
        check(kv[0] >= 45 && kv[0] <= 66 && kmax > 18 && kmax < 30,
                f("Kite 12 m² bei 16 kn: Kreuzwinkel %.0f° [G 45 bis 66], bis %.1f kn [G 18 bis 30]", kv[0], kmax));
        // glatt: die Polare des Hobie bei 16 kn hat zwischen 40° und 115° keine Zacken (Befund aus Phase 7: Flachheit in Stufen)
        double[][] hp = s.polar(Sail.HOBIE, 16 * K);
        int peak = 0, dips = 0;
        for (int i = 0; i < hp.length; i++) if (hp[i][1] > hp[peak][1]) peak = i;
        for (int i = 1; i <= peak; i++) if (hp[i][0] > 40 && hp[i][1] < hp[i - 1][1]) dips++;
        check(dips == 0 && hp[peak][0] >= 90, f("Polare Hobie 16 bei 16 kn ohne Zacken: steigt von 40° bis zur Spitze bei %.0f° (%.1f kn) ohne Einbruch (Phase 7: Zacke bei 95°)", hp[peak][0], hp[peak][1] / K));
        Sail.Rig r = Sail.J70;
        check(Sail.windage(r) < 1.0 && Sail.windage(Sail.HOBIE) > Sail.windage(r), f("Luftwiderstand von Crew und Rigg: J/70 %.2f m², Hobie %.2f m²", Sail.windage(r), Sail.windage(Sail.HOBIE)));
    }

    static void manoever() {
        System.out.println("Manöver seit Phase 8");
        double[] w = {10 * K};
        Sail s = windSail(w);
        int[] cap = {0}, right = {0}, gy = {0};
        double[] vio = {0};
        s.events = new Sail.Events() {
            @Override public void onCapsize(Sail.Boat b) { cap[0]++; }
            @Override public void onRight(Sail.Boat b) { right[0]++; }
            @Override public void onGybe(Sail.Boat b, double v) { gy[0]++; vio[0] = Math.max(vio[0], v); }
        };
        // Halse: der Baum geht über, einmal
        Sail.Boat c = new Sail.Boat(Sail.ILCA);
        c.headingDeg = 155;
        w[0] = 16 * K;
        for (int i = 0; i < 400; i++) s.step(c, 0.1);
        double before = c.boomDeg;
        c.targetHeadingDeg = 205;
        double mr = 0;
        for (int i = 0; i < 200; i++) { s.step(c, 0.1); mr = Math.max(mr, Math.abs(c.rollDeg)); }
        check(gy[0] == 1 && Math.signum(before) != Math.signum(c.boomDeg) && vio[0] > 0.2,
                f("Halse ILCA bei 16 kn: Baum von %.0f° nach %.0f°, ein onGybe, Heftigkeit %.2f, Krängung bis %.0f°", before, c.boomDeg, vio[0], mr));
        // Bö: der geübte Kat fiert und bleibt aufrecht, die ungeübte Crew kentert, richtet auf und segelt weiter
        boolean[] keep = new boolean[2];
        double[] rec = new double[2];
        for (int k = 0; k < 2; k++) {
            w[0] = 10 * K;
            cap[0] = right[0] = 0;
            Sail.Boat b = new Sail.Boat(Sail.HOBIE);
            b.headingDeg = 90;
            b.skill = k == 0 ? 1 : 0.5;
            for (int i = 0; i < 600; i++) s.step(b, 0.1);
            for (int i = 0; i < 100; i++) { w[0] = Math.min(22, 10 + 12 * i / 30.0) * K; s.step(b, 0.1); }
            keep[k] = cap[0] == 0;
            w[0] = 10 * K;
            b.targetHeadingDeg = 90;
            for (int i = 0; i < 2600; i++) s.step(b, 0.1);
            rec[k] = b.speed / Math.max(1e-6, s.speed(Sail.HOBIE, 10 * K, 90)[0]);
            if (k == 1) check(cap[0] == 1 && right[0] == 1 && !b.capsized && rec[1] > 0.6,
                    f("Hobie 16, Bö 10 → 22 kn, Crew mit Können 0,5: kentert, wird nach %.0f s aufgerichtet und läuft wieder (%.0f %% der Polare)", Sail.HOBIE.rightingTime() / 0.5, rec[1] * 100));
        }
        check(keep[0] && !keep[1], "dieselbe Bö: die geübte Crew fiert die Schot und bleibt aufrecht");
        // Patzer: capsize() von außen
        cap[0] = right[0] = 0;
        w[0] = 8 * K;
        Sail.Boat o = new Sail.Boat(Sail.OPTIMIST);
        o.headingDeg = 60;
        for (int i = 0; i < 300; i++) s.step(o, 0.1);
        s.capsize(o);
        for (int i = 0; i < 40; i++) s.step(o, 0.1);
        double lying = Math.abs(o.rollDeg), v = o.speed;
        check(cap[0] == 1 && lying > 80 && v < 0.3, f("Optimist kentert (Regie): liegt nach 4 s auf %.0f°, %.2f m/s", lying, v));
        double t = 0;
        while (o.capsized && t < 300) { s.step(o, 0.1); t += 0.1; }
        check(right[0] == 1 && t < Sail.OPTIMIST.rightingTime() + 10, f("… nach %.0f s wieder aufgerichtet (onRight)", t + 4));
        // Kielboot kentert nicht
        w[0] = 30 * K;
        Sail.Boat j = new Sail.Boat(Sail.J70);
        j.headingDeg = 60;
        cap[0] = 0;
        double jr = 0;
        for (int i = 0; i < 300; i++) { s.step(j, 0.1); jr = Math.max(jr, Math.abs(j.rollDeg)); }
        check(!j.capsized && jr < 75, f("J/70 in 30 kn am Wind: kentert nicht (Krängung bis %.0f°)", jr));
        // die Wende läuft durch null: die Krängung wechselt die Seite
        w[0] = 12 * K;
        Sail.Boat d = new Sail.Boat(Sail.ILCA);
        d.headingDeg = 45;
        for (int i = 0; i < 300; i++) s.step(d, 0.1);
        double r0 = d.rollDeg;
        d.targetHeadingDeg = 315;
        for (int i = 0; i < 300; i++) s.step(d, 0.1);
        check(Math.signum(r0) != Math.signum(d.rollDeg) && Math.abs(d.rollDeg) > 1, f("Wende ILCA: Krängung %.0f° → %.0f° (Vorzeichen wechselt)", r0, d.rollDeg));
    }

    static Sail windSail(double[] w) {
        Sail s = new Sail();
        s.quality = Sail.Quality.MEDIUM;
        s.wind = new Sail.Wind() {
            public double speed(double x, double z, double t) { return w[0]; }
            public double fromDeg(double x, double z, double t) { return 0; }
        };
        return s;
    }

    static void regatta() {
        System.out.println("Regatta seit Phase 8");
        String[] order = new String[2];
        for (int run = 0; run < 2; run++) {
            Sail s = new Sail();
            s.quality = Sail.Quality.MEDIUM;
            s.windSpeed = 12 * K;
            s.windFromDeg = 0;
            com.dan.sail.Regatta r = new com.dan.sail.Regatta(s, new com.dan.sail.Regatta.Course(0, 0, 0, 600, 100, 2), 11);
            r.add(Sail.J70, 10);
            java.util.List<String> sig = new java.util.ArrayList<>();
            int[] rounds = {0};
            r.listener = new com.dan.sail.Regatta.Listener() {
                @Override public void onSignal(String what, double t) { sig.add(what); }
                @Override public void onRound(com.dan.sail.Regatta.Entry e, String m, double t) { rounds[0]++; }
            };
            r.start(300);
            int vorStart = 0, danach = 0;
            for (int i = 0; i < 3600 / 0.05 && !r.over(); i++) {
                r.step(0.05);
                for (com.dan.sail.Regatta.Entry e : r.entries) {
                    if (e.leg == com.dan.sail.Regatta.Leg.PRESTART && e.boat.downwind) vorStart++;
                    if (e.leg == com.dan.sail.Regatta.Leg.RUN && e.boat.downwind) danach++;
                }
            }
            StringBuilder o = new StringBuilder();
            double first = Double.MAX_VALUE;
            int tacked = 0;
            for (com.dan.sail.Regatta.Entry e : r.entries) {
                first = Math.min(first, e.finishTime);
                if (e.tacks >= 2) tacked++;
            }
            for (com.dan.sail.Regatta.Entry e : r.ranking()) o.append(e.number).append(' ');
            order[run] = o.toString();
            if (run == 1) break;
            double[] v = s.vmgCached(Sail.J70, 12 * K);
            double est = 2 * (600 / v[1] + 520 / v[4]);
            if (run == 0) check(vorStart == 0 && danach > 0, f("seit Timmendorf Phase 9: vor dem Start kein Gennaker (%d Schritte mit), raumschots gesetzt (%d Schritte)", vorStart, danach));
            check(r.over() && r.finished() == 10, f("10 J/70 segeln zwei Runden auf einer Bahn von 600 m: alle im Ziel (%d Tonnen gerundet)", rounds[0]));
            check(sig.equals(java.util.List.of("Ankündigung", "Vorbereitung", "Eine Minute", "Start", "Ziel")) || sig.containsAll(java.util.List.of("Ankündigung", "Vorbereitung", "Eine Minute", "Start", "Ziel")),
                    "Startverfahren 5–4–1–0 und Ziel: " + String.join(", ", sig));
            check(first > 0.9 * est && first < 1.45 * est, f("der Erste braucht %d:%02d, die Polare verspricht %d:%02d (ohne Manöver, Abdeckung und Ausweichen)",
                    (int) first / 60, (int) first % 60, (int) est / 60, (int) est % 60));
            check(tacked >= 8 && r.giveWays() > 0 && r.contacts() <= 3 * r.entries.size(),
                    f("%d von 10 wenden mindestens zweimal; %d Ausweichmanöver nach den Regeln 10–12, %d Berührungen", tacked, r.giveWays(), r.contacts()));
            Sail s1 = new Sail();
            s1.windSpeed = 12 * K;
            s1.windFromDeg = 0;
            com.dan.sail.Regatta one = new com.dan.sail.Regatta(s1, new com.dan.sail.Regatta.Course(0, 0, 0, 600, 100, 1), 1);
            Sail.Boat e0 = one.add(Sail.J70, 1).get(0).boat;
            e0.x = 0;
            e0.z = 0;
            double behind = one.shade(0, 3 * 9.5, 0), ahead = one.shade(0, -3 * 9.5, 0), aside = one.shade(40, 3 * 9.5, 0);
            check(behind < 0.85 && ahead == 1 && aside > 0.97, f("Abdeckung: 3 Masthöhen in Lee eines Boots %.0f %% Wind, in Luv %.0f %%, 40 m seitlich %.0f %%", behind * 100, ahead * 100, aside * 100));
        }
        check(order[0].equals(order[1]), "gleicher Startwert, gleiche Reihenfolge: " + order[0].trim());
    }

    /** Zeit für eine Seemeile auf und eine ab bei 10 kn (h). */
    static double bahn(Sail s, Rig r) {
        double[] v = s.vmg(r, 10 * K);
        return (1 / (v[1] / K) + 1 / (v[4] / K));
    }

    static double max(Sail s, Rig r, double tws) {
        double m = 0;
        for (double a = 50; a <= 170; a += 10) m = Math.max(m, s.speed(r, tws, a)[0]);
        return m / K;
    }

    static void gleichgewicht(Sail s) {
        System.out.println("Gleichgewicht");
        // Fahrtenyacht ohne Crew auf der Kante: sin φ = M / (W · g · GM) (Handrechnung mit dem Moment bei aufrechtem Boot)
        Rig r = Sail.CRUISER;
        Rig bare = new Rig(r.name(), r.kind(), r.loa(), r.lwl(), r.beam(), r.mass(), 0, 0, r.mainArea(), r.jibArea(), 0, r.ceHeight(), r.mastHeight(),
                r.finArea(), r.finDepth(), r.wetted(), r.gm(), 0, 60, r.wave(), r.planing(), r.formFactor(), r.turnRate());
        double v = 5 * K, tws = 12 * K;
        double[] b = s.balance(bare, v, tws, 90, 1, false);
        double[] aw = Sail.apparentWind(tws, 0, v, 90);
        double m = s.aero(bare, aw[0], aw[1], 0, 1, false)[2];
        double hand = Math.toDegrees(Math.asin(Math.min(1, m / (bare.total() * Sail.G * bare.gm()))));
        check(near(b[1], hand, 0.12), f("Krängung der Fahrtenyacht bei 12 kn halbem Wind: %.1f°, Handrechnung %.1f°", b[1], hand));
        double rm = Sail.rightingMoment(Sail.HOBIE, 20), wb = Sail.HOBIE.total() * Sail.G * Sail.HOBIE.beam() / 2;
        check(rm < wb + Sail.HOBIE.crew() * Sail.HOBIE.crewMass() * Sail.G * Sail.HOBIE.hikeArm() + 1, f("Kat: aufrichtendes Moment höchstens W · B/2 plus Trapez (%.0f Nm)", rm));
        double[] ap = s.appendage(Sail.J70, 3, 600);
        check(ap[0] > 1 && ap[0] < 10, f("J/70: 600 N Querkraft bei 3 m/s verlangen %.1f° Abdrift", ap[0]));
    }

    static void bewegung(Sail s) {
        System.out.println("Bewegung über die Zeit");
        double tws = 12 * K;
        Sail w = new Sail();
        w.quality = Sail.Quality.MEDIUM;
        w.windSpeed = tws;
        w.windFromDeg = 0;
        Sail.Boat b = new Sail.Boat(Sail.J70);
        b.headingDeg = 90;
        for (int i = 0; i < 120 * 10; i++) w.step(b, 0.1);
        double pol = s.speed(Sail.J70, tws, 90)[0];
        check(near(b.speed, pol, 0.08), f("J/70 beschleunigt bei halbem Wind auf %.2f kn (Polare %.2f kn)", b.speed / K, pol / K));
        check(b.heelDeg > 0 && b.heelDeg < 30 && Double.isFinite(b.x), f("Krängung %.1f°, Abdrift %.1f°", b.heelDeg, b.leewayDeg));
        int[] tacks = {0}, luffs = {0};
        w.events = new Sail.Events() {
            @Override public void onTack(Sail.Boat x) { tacks[0]++; }
            @Override public void onLuff(Sail.Boat x, double st) { luffs[0]++; }
        };
        b.headingDeg = 42;
        for (int i = 0; i < 600; i++) w.step(b, 0.1);
        double v0 = b.speed;
        b.targetHeadingDeg = 318;
        double low = v0;
        for (int i = 0; i < 300; i++) { w.step(b, 0.1); low = Math.min(low, b.speed); }
        check(tacks[0] == 1 && luffs[0] > 0, f("Wende: ein onTack, das Segel killt im Wind (%d Ereignisse)", luffs[0]));
        check(low < v0 * 0.8 && b.speed > v0 * 0.85, f("die Wende kostet Fahrt (%.2f → %.2f kn) und das Boot läuft wieder an (%.2f kn)", v0 / K, low / K, b.speed / K));
        Sail.Boat a = new Sail.Boat(Sail.ILCA), c = new Sail.Boat(Sail.ILCA);
        a.headingDeg = c.headingDeg = 120;
        double maxV = 0;
        for (int i = 0; i < 600; i++) { w.step(a, 1 / 30.0); maxV = Math.max(maxV, a.speed); }
        for (int i = 0; i < 600; i++) w.step(c, 1 / 30.0);
        check(Double.isFinite(maxV) && maxV < 3 * tws, f("ILCA raumschots: beschränkt (höchstens %.1f kn)", maxV / K));
        Sail w2 = new Sail();
        w2.quality = Sail.Quality.MEDIUM;
        w2.windSpeed = tws;
        w2.windFromDeg = 0;
        Sail.Boat d = new Sail.Boat(Sail.ILCA);
        d.headingDeg = 120;
        for (int i = 0; i < 600; i++) w2.step(d, 1 / 30.0);
        check(d.x == c.x && d.z == c.z, "gleiche Eingänge, gleiche Fahrt");
        Sail.Boat o = new Sail.Boat(Sail.OPTIMIST);
        o.headingDeg = 0;
        o.speed = 1;
        for (int i = 0; i < 300; i++) w.step(o, 0.1);
        check(o.speed < 0.6 * K, f("im Wind stehend kommt der Optimist zum Stehen (%.2f kn)", o.speed / K));
    }

    static boolean near(double a, double b, double rel) { return Math.abs(a - b) <= rel * Math.abs(b); }

    static String f(String s, Object... a) { return String.format(Locale.GERMAN, s, a); }

    static void check(boolean c, String what) {
        if (c) good++; else bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private SailCheck() { }
}
