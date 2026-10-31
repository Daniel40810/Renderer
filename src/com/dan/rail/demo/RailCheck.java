package com.dan.rail.demo;

import com.dan.rail.Track;
import com.dan.rail.Train;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Achse, Lage, Steigung, Windungen, Schwellen, Zug; seit Brocken Phase 12 Achse aus Flächen,
 * Gradiente mit Grenze, Überhöhung, Umläufe, Bahnübergänge, Bahnkörper; seit Phase 13 Fahrdynamik, Bogengrenze, Halte,
 * Pfiffe und Auspuffschläge.
 */
public final class RailCheck {
    private static int bad;

    public static void main(String[] args) {
        Track g = new Track("Gerade", new double[][]{{0, 0, 0}, {0, 10, -1000}}, Track.METERSPUR, Track.Quality.MEDIUM);
        check(Math.abs(g.length() - 1000) < 1e-9, "Länge in der Ebene");
        Track.Pose p = g.at(500);
        check(Math.abs(p.z() + 500) < 1e-9 && Math.abs(p.y() - 5) < 1e-9, "Lage in der Mitte");
        check(Math.abs(p.heading()) < 1e-9 || Math.abs(p.heading() - 360) < 1e-9, "nach Norden heißt 0°");
        check(Math.abs(p.gradient() - 0.01) < 1e-9 && Double.isInfinite(p.radius()), "1 % Steigung, keine Krümmung");
        check(g.at(-50).s() == 0 && g.at(5000).s() == g.length(), "at() bleibt auf dem Gleis");
        // Spirale wie am Brocken: 1,5 Windungen, Halbmesser 900 → 600 m, von 1 000 auf 1 125 m [G]
        double[][] ax = Track.spirale(0, 0, 900, 600, 1.5, 1000, 1125, 400);
        Track sp = new Track("Spirale", ax, Track.METERSPUR, Track.Quality.HIGH);
        check(Math.abs(Math.abs(sp.windungen()) - 1.5) < 0.02, String.format(java.util.Locale.GERMAN, "Spirale mit 1,5 Windungen (%.3f)", sp.windungen()));
        check(sp.windungen() > 0, "rechts herum heißt positiv");
        check(sp.maxGradient(100) < 0.033, String.format(java.util.Locale.GERMAN, "Steigung unter 3,3 %% (%.2f %%)", sp.maxGradient(100) * 100));
        Track.Pose m = sp.at(sp.length() / 2);
        check(m.radius() > 600 && m.radius() < 900, String.format(java.util.Locale.GERMAN, "Halbmesser in der Mitte zwischen 600 und 900 m (%.0f m)", m.radius()));
        check(sp.schwellen() > sp.length() / 0.7 && sp.schwelle(10) != sp.schwelle(11), "Schwellen alle 0,65 m");
        Track sp2 = new Track("Spirale", ax, Track.METERSPUR, Track.Quality.HIGH);
        sp2.seed = sp.seed;
        check(sp.schwelle(123) == sp2.schwelle(123), "gleicher seed, gleiche Schwellen");
        sp2.seed = sp.seed + 1;
        check(sp.schwelle(123) != sp2.schwelle(123), "anderer seed, andere Schwellen");
        Train t = Train.brockenzug("Probe");
        double[] an = {-1};
        t.onAnkunft = (z, sek) -> an[0] = sek;
        for (int i = 0; i < 20 * 60 * 10 && t.faehrt; i++) t.step(sp, 0.1);
        check(!t.faehrt && Math.abs(t.s - sp.length()) < 0.6 && an[0] > 0, "der Zug kommt am Ende an und meldet es");
        check(an[0] > sp.length() / t.vMax, "nicht schneller als die Höchstgeschwindigkeit erlaubt");
        // seit Brocken Phase 12: Achse aus Flächen, Gradiente, Überhöhung, Umläufe, Übergänge, Bahnkörper
        java.util.List<double[][]> fl = new java.util.ArrayList<>();
        // ein Band von 12 m Breite im Bogen (Viertelkreis, Halbmesser 300 m), in zwei Stücke mit 20 m Lücke geteilt
        for (int part = 0; part < 2; part++) {
            int n = 40;
            double[] ring = new double[(n + 1) * 4];
            double a0 = part == 0 ? 0 : Math.PI / 4 + 20 / 300.0, a1 = part == 0 ? Math.PI / 4 : Math.PI / 2;
            for (int k = 0; k <= n; k++) {
                double wk = a0 + (a1 - a0) * k / n;
                ring[k * 2] = 306 * Math.cos(wk); ring[k * 2 + 1] = 306 * Math.sin(wk);
                ring[(2 * n + 1 - k) * 2] = 294 * Math.cos(wk); ring[(2 * n + 1 - k) * 2 + 1] = 294 * Math.sin(wk);
            }
            fl.add(new double[][]{ring});
        }
        com.dan.rail.Corridor co = com.dan.rail.Corridor.of(fl, 1);
        double[][] mid = co.path(300, 0, 0, 300, 10, 2);
        double maxOff = 0;
        for (double[] q : mid) maxOff = Math.max(maxOff, Math.abs(Math.hypot(q[0], q[1]) - 300));
        double len = 0;
        for (int i = 1; i < mid.length; i++) len += Math.hypot(mid[i][0] - mid[i - 1][0], mid[i][1] - mid[i - 1][1]);
        check(mid.length > 100 && maxOff < 1.5 && Math.abs(len - Math.PI / 2 * 300) < 15, String.format(java.util.Locale.GERMAN,
                "Achse aus Flächen: Mittellinie auf %.1f m genau, %.0f m lang (Viertelkreis 471 m), über eine Lücke von 20 m · %s", maxOff, len, co.describe()));
        double[] ground = new double[1001];
        for (int i = 0; i <= 1000; i++) ground[i] = i < 300 ? i * 2 * 0.06 : i < 600 ? 36 + (i - 300) * 2 * 0.01 : 42 + (i - 600) * 2 * 0.05;
        double[] pr = Track.profile(ground, 2, 0.033, 60, new double[][]{{900, 1100}}, 0.004, 0.3);
        double gmax = 0, gst = 0;
        for (int i = 1; i <= 1000; i++) {
            double gr = Math.abs(pr[i] - pr[i - 1]) / 2;
            gmax = Math.max(gmax, gr);
            if (i * 2 >= 900 && i * 2 <= 1100) gst = Math.max(gst, gr);
        }
        check(gmax <= 0.0330001 && gst <= 0.0040001, String.format(java.util.Locale.GERMAN, "Gradiente: Gelände bis 6 %%, Gleis höchstens %.2f %%, im Bahnhof %.2f %%", gmax * 100, gst * 100));
        double[][] axr = new double[201][];
        for (int i = 0; i <= 200; i++) { double wb = i / 200.0 * Math.PI; axr[i] = new double[]{100 * Math.sin(wb), 0, -100 * Math.cos(wb) + 100}; }
        Track bogen = new Track("Bogen", axr, Track.METERSPUR, Track.Quality.HIGH);
        double c1 = bogen.cant(bogen.length() / 2);
        check(Math.abs(c1) > 0.01 && Math.abs(c1) <= bogen.maxCant && bogen.cant(0.5) * c1 >= 0, String.format(java.util.Locale.GERMAN, "Überhöhung im Bogen von 100 m: %.0f mm", Math.abs(c1) * 1000));
        check(Math.abs(Math.abs(sp.turnsAround(0, 0, 0, sp.length())) - 1.5) < 0.02, String.format(java.util.Locale.GERMAN, "Umläufe um die Mitte der Spirale: %.2f", sp.turnsAround(0, 0, 0, sp.length())));
        java.util.List<double[][]> strasse = new java.util.ArrayList<>(); strasse.add(new double[][]{{-5, -600, 5, -600, 5, -400, -5, -400}});
        java.util.List<double[]> ue = g.crossings(strasse);
        check(ue.size() == 1 && Math.abs(ue.get(0)[0] - 400) < 1 && Math.abs(ue.get(0)[1] - 600) < 1, "Bahnübergang: wo die Achse die Fläche quert (400 bis 600 m)");
        com.dan.rail.Bed bed = new com.dan.rail.Bed();
        Track flach = new Track("Damm", new double[][]{{0, 5, 0}, {0, 5, -400}}, Track.METERSPUR, Track.Quality.HIGH);
        bed.build(flach, (x, z) -> 0, -50, -420, 1, 100, 440);
        double mitte = bed.deltaAt(0, -200), rand = bed.deltaAt(18, -200);
        check(Math.abs(mitte - 4.45) < 0.05 && rand < 0.3 && bed.deltaAt(40, -200) == 0, String.format(java.util.Locale.GERMAN,
                "Bahnkörper: Damm 4,45 m (3 m gebaut, 1 : 1,5; der Rest flach angeglichen), 18 m neben der Achse %.2f m · %s", rand, bed.describe()));
        // seit Brocken Phase 13: Fahrdynamik, Bogengrenze, Halte, Pfiffe, Auspuffschläge
        Track steil = new Track("Steil", new double[][]{{0, 0, 0}, {0, 165, -5000}}, Track.METERSPUR, Track.Quality.MEDIUM);
        Train z = Train.brockenzug("Berg", 5);
        double vSteil = 0, lastSteil = 0;
        for (int i = 0; i < 6000 && z.faehrt; i++) { z.step(steil, 0.5); if (z.s > 3000 && vSteil == 0) { vSteil = z.v; lastSteil = z.last(); } }
        // im Beharrungszustand: Leistung = Gewicht · (Steigung + Grundwiderstand) · v
        double vErwartet = z.leistung / (z.masse * 1000 * 9.81 * (0.033 + 0.0027));
        check(Math.abs(vSteil - vErwartet) < 0.4 && lastSteil > 0.95, String.format(java.util.Locale.GERMAN,
                "33 ‰ mit fünf Wagen (%.0f t): Beharrung %.1f km/h (aus der Leistung %.1f km/h), volle Kraft", z.masse, vSteil * 3.6, vErwartet * 3.6));
        Train fl2 = Train.brockenzug("Eben", 3);
        double vEben = 0;
        Track eben = new Track("Eben", new double[][]{{0, 0, 0}, {0, 0, -1500}}, Track.METERSPUR, Track.Quality.MEDIUM);
        for (int i = 0; i < 8000 && fl2.faehrt; i++) { fl2.step(eben, 0.25); vEben = Math.max(vEben, fl2.v); }
        check(Math.abs(vEben - fl2.vMax) < 0.1 && !fl2.faehrt, String.format(java.util.Locale.GERMAN, "in der Ebene auf 1 500 m bis zur Höchstgeschwindigkeit (%.1f km/h) und Halt am Ende", vEben * 3.6));
        // Bogen mit 60 m Halbmesser zwischen zwei Geraden
        java.util.List<double[]> bx = new java.util.ArrayList<>();
        for (int i = 0; i <= 100; i++) bx.add(new double[]{0, 0, -i * 4.0});
        for (int i = 1; i <= 60; i++) { double wb = i / 60.0 * Math.PI; bx.add(new double[]{60 - 60 * Math.cos(wb), 0, -400 - 60 * Math.sin(wb)}); }
        for (int i = 1; i <= 100; i++) bx.add(new double[]{120, 0, -400 + i * 4.0});
        Track bg = new Track("Bogen 60", bx.toArray(new double[0][]), Track.METERSPUR, Track.Quality.HIGH);
        Train zb = Train.brockenzug("Bogen", 3);
        double vBogen = 0, vVor = 0;
        for (int i = 0; i < 8000 && zb.faehrt; i++) {
            zb.step(bg, 0.25);
            if (zb.s - zb.laenge > 420 && zb.s < 400 + Math.PI * 60 - 20) vBogen = Math.max(vBogen, zb.v);
            if (zb.s > 250 && zb.s < 300) vVor = Math.max(vVor, zb.v);
        }
        double vGrenze = Math.sqrt(zb.querbeschleunigung * 60);
        check(vBogen > 0 && vBogen < vGrenze * 1.08 && vVor > vGrenze * 1.3, String.format(java.util.Locale.GERMAN,
                "Bogen 60 m: %.1f km/h im Bogen (Grenze %.1f km/h), davor %.1f km/h", vBogen * 3.6, vGrenze * 3.6, vVor * 3.6));
        Train zh = Train.brockenzug("Halt", 4);
        java.util.List<String> pf = new java.util.ArrayList<>();
        zh.onPfiff = (zz, sp0, grund) -> pf.add(grund);
        zh.halte.add(new Train.Halt(500, 60, "Mitte"));
        zh.pfiffe.add(new double[]{750});
        double haltAn = -1, haltAb = -1, haltS = 0;
        for (int i = 0; i < 20000 && zh.faehrt; i++) {
            zh.step(steil, 0.1);
            if (zh.haelt() && haltAn < 0) { haltAn = zh.zeit(); haltS = zh.s; }
            if (haltAn > 0 && haltAb < 0 && !zh.haelt()) haltAb = zh.zeit();
        }
        check(Math.abs(haltS - 500) < 0.6 && haltAb - haltAn >= 59.9 && pf.equals(java.util.List.of("Abfahrt", "Bahnübergang")) && !zh.faehrt,
                String.format(java.util.Locale.GERMAN, "Halt bei %.1f m für %.0f s, Pfiffe %s, dann bis zum Ende", haltS, haltAb - haltAn, pf));
        Train zab = Train.brockenzug("Tal", 5);
        zab.richtung = -1;
        zab.s = steil.length();
        double vAb = 0;
        boolean bremst = false;
        for (int i = 0; i < 20000 && zab.faehrt; i++) { zab.step(steil, 0.25); vAb = Math.max(vAb, zab.v); bremst |= zab.zustand().equals("bremst"); }
        check(!zab.faehrt && zab.s < 0.6 && vAb <= zab.vMax + 0.05 && bremst, String.format(java.util.Locale.GERMAN, "bergab: höchstens %.1f km/h, bremst, kommt unten an", vAb * 3.6));
        Train zs = Train.brockenzug("Takt", 4);
        zs.v = 20 / 3.6;
        check(Math.abs(zs.schlaege() - 20 / 3.6 / Math.PI * 4) < 1e-9 && zs.fahrzeuge().length == 5 && zs.wagen() == 4, String.format(java.util.Locale.GERMAN,
                "bei 20 km/h %.1f Auspuffschläge je Sekunde (Treibrad 1 m, 4 je Umdrehung); Lok und 4 Wagen", zs.schlaege()));
        // Signal: der Zug wartet am Halt, bis die App den Abschnitt freigibt; Ausweichgleis daneben
        Train zw = Train.brockenzug("Kreuzung", 3);
        boolean[] frei = {false};
        double[] amHalt = {-1};
        zw.halte.add(new Train.Halt(300, 0, "Ausweiche"));
        zw.signal = (zz, h) -> frei[0];
        zw.onHalt = (zz, h, sek) -> amHalt[0] = sek;
        for (int i = 0; i < 4000; i++) { zw.step(steil, 0.25); if (zw.zeit() > 600) frei[0] = true; }
        check(amHalt[0] > 0 && amHalt[0] < 300 && zw.s > 1000, String.format(java.util.Locale.GERMAN,
                "Signal: am Halt nach %.0f s, gewartet bis zur Freigabe nach 600 s, dann weiter (%.0f m)", amHalt[0], zw.s));
        Track aus = steil.parallel("Ausweiche", 1000, 1300, -4.2, 40);
        Track.Pose am = aus.at(150), hm = steil.at(1150);
        double ab = Math.hypot(am.x() - hm.x(), am.z() - hm.z());
        com.dan.rail.Bed bed2 = new com.dan.rail.Bed().build(java.util.List.of(steil, aus), (gx, gz) -> -gz * 0.033, -40, -1400, 1, 80, 500);
        check(Math.abs(ab - 4.2) < 0.05 && Math.abs(aus.length() - 300) < 1 && bed2.distAt(-4.2, -1150) < 0.8 && bed2.distAt(-2.1, -1150) < 2.3,
                String.format(java.util.Locale.GERMAN, "Ausweichgleis 4,2 m links (%.2f m), %.0f m lang, im Bahnkörper beider Gleise", ab, aus.length()));
        // seit Brocken Phase 14: Seite des Bahnsteigs
        Track.Station rechts = new Track.Station("A", 100, 80), links = new Track.Station("B", 300, 80, -1);
        check(rechts.side() == 1 && links.side() == -1, "Bahnhöfe mit der Seite des Bahnsteigs (rechts als Vorgabe, links, ohne)");
        System.out.println(sp.describe());
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private RailCheck() { }
}
