package com.dan.vessel.demo;

import com.dan.vessel.Boat;
import com.dan.vessel.Chain;
import com.dan.vessel.Hull;
import com.dan.vessel.Kutter;
import com.dan.vessel.Mesh;
import com.dan.vessel.Water;

import java.util.Locale;

/**
 * Prüfung von {@code com.dan.vessel} ohne Fenster (LituyaBay1958 Phase 15, Werkbuch 5.9): Form und Hydrostatik der drei
 * Kutter, Schwimmlage, aufrichtender Hebel gegen GM, Roll- und Tauchperiode gegen die Handrechnung, Kette in Ruhe gegen die
 * Kettenlinie, Anker slippt, Kette reißt, Kentern, Sinken, trocken in Ruhe, Fahrt mit Maschine und Ruder, und die Edrie
 * in der Einzelwelle der {@link Bucht}: Kette reißt, wenn die Front kommt, das Boot reitet die Welle und kommt aufrecht
 * zurück. Ausgabe je Zeile „ok“ oder „FEHLER“, am Ende „Alles in Ordnung.“
 */
public final class VesselCheck {
    private VesselCheck() { }

    static int n, bad;

    static void check(boolean c, String fmt, Object... a) {
        n++;
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + String.format(Locale.GERMAN, fmt, a));
    }

    static void teil(String t) { System.out.println("\n" + t); }

    static Water ruhig(double tiefe) { return Water.still(0, tiefe); }

    /** Nulldurchgänge von unten nach oben einer Größe über die Zeit: mittlere Periode über drei Schwingungen. */
    interface Groesse { double von(Boat b); }

    static double periode(Boat b, Water w, Groesse g, double dauer) {
        double last = g.von(b), t0 = 0, t1 = 0;
        int k = 0;
        for (int i = 0; i < dauer / 0.01; i++) {
            b.step(w, 0.01);
            double v = g.von(b);
            if (last < 0 && v >= 0) { k++; if (k == 1) t0 = b.time(); if (k == 4) { t1 = b.time(); break; } }
            last = v;
        }
        return k >= 4 ? (t1 - t0) / 3 : Double.NaN;
    }

    public static void main(String[] args) {
        long tStart = System.nanoTime();
        System.out.println("VesselCheck – com.dan.vessel (LituyaBay1958 Phase 15)");

        teil("Form und Hydrostatik der drei Troller (MILLER60: „about 40 feet long“)");
        for (Boat b : new Boat[]{Kutter.edrie(), Kutter.badger(), Kutter.sunmore()}) {
            Hull h = b.hull;
            check(Math.abs(h.loa / 0.3048 - 40) < 2 && b.mass > 15e3 && b.mass < 30e3 && h.block() > 0.22 && h.block() < 0.4,
                    "%s: %.1f m (%.0f Fuß), %.1f t, Völligkeit %.2f, %d Proben zu %.2f m, GM %.2f m, KG %.2f m (%.2f der Seitenhöhe)",
                    h.name, h.loa, h.loa / 0.3048, b.mass / 1000, h.block(), h.probes(), h.cell(), b.gmDesign(), b.cg[1], b.cg[1] / h.depth);
        }

        Boat e = Kutter.edrie();
        Water tief = ruhig(30);
        teil("Schwimmlage, Hebel, Eigenperioden (Edrie)");
        e.settle(tief, 0, 0, 0);
        for (int i = 0; i < 2000; i++) e.step(tief, 0.01);
        check(Math.abs(e.draftNow(tief) - e.hull.draft) < 0.05 && Math.abs(e.pitch()) < 0.5 && Math.abs(e.roll()) < 0.2,
                "nach 20 s in Ruhe: Tiefgang %.3f m (Konstruktion %.2f), Trimm %.2f°, Krängung %.2f°", e.draftNow(tief), e.hull.draft, e.pitch(), e.roll());
        double gz2 = e.gz(tief, 0, 0, 0, 2), gm = e.gmDesign();
        check(Math.abs(gz2 / (gm * Math.sin(Math.toRadians(2))) - 1) < 0.12, "aufrichtender Hebel bei 2°: GZ %.4f m gegen GM·sin φ %.4f m (GM %.2f m aus KB + BM − KG)", gz2, gm * Math.sin(Math.toRadians(2)), gm);
        double gz30 = e.gz(tief, 0, 0, 0, 30), gz60 = e.gz(tief, 0, 0, 0, 60);
        check(gz30 > 0.1 && gz60 > 0, "Hebelkurve: GZ(30°) %.3f m, GZ(60°) %.3f m – richtet sich auf", gz30, gz60);
        e.settle(tief, 0, 0, 0);
        double[] p = e.position();
        e.place(p[0], p[1], p[2], 0, 8);
        double tr = periode(e, tief, Boat::roll, 40);
        check(Math.abs(tr / e.rollPeriodHand() - 1) < 0.1, "Rollen aus 8°: Periode %.2f s, Handrechnung 2π k √(1 + a) / √(g GM) %.2f s", tr, e.rollPeriodHand());
        e.settle(tief, 0, 0, 0);
        p = e.position();
        double y0 = p[1];
        e.place(p[0], p[1] + 0.3, p[2], 0);
        double th = periode(e, tief, b -> b.position()[1] - y0, 40);
        check(Math.abs(th / e.heavePeriodHand() - 1) < 0.15, "Tauchen aus 0,3 m: Periode %.2f s, Handrechnung 2π √(m (1 + a) / (ρ g A)) %.2f s", th, e.heavePeriodHand());
        e.settle(tief, 0, 0, 0);
        for (int i = 0; i < 3000; i++) e.step(tief, 0.01);
        check(e.flood() < 1, "30 s in Ruhe: trocken (%.1f kg Wasser im Rumpf)", e.flood());

        teil("Ankerkette aus Massenpunkten");
        Water anker = ruhig(Kutter.EDRIE_TIEFE);
        for (double X : new double[]{66, 69}) {
            Chain c = Kutter.edrieKette();
            c.friction = 0;
            double[] f = {X, 1.2, 0}, fv = {0, 0, 0}, F = null;
            c.lay(anker, 0, 0, f);
            for (int i = 0; i < 25_000; i++) F = c.step(anker, 0.004, f, fv);
            double T = Math.sqrt(F[0] * F[0] + F[1] * F[1] + F[2] * F[2]);
            double[] cat = Chain.catenaryTension(f[1] - (anker.bed(0, 0) + c.diameter / 2), X, c.length, c.wetWeight());
            check(cat != null && Math.abs(T / cat[2] - 1) < 0.05 && Math.abs(Math.hypot(F[0], F[2]) / cat[0] - 1) < 0.1,
                    "Kettenlinie, Anker %.0f m entfernt (5 Faden, 40 Faden Kette): Zug an der Klüse %.0f N gegen %.0f N, waagerecht %.0f gegen %.0f N, auf dem Grund %.1f gegen %.1f m",
                    X, T, cat == null ? 0 : cat[2], Math.hypot(F[0], F[2]), cat == null ? 0 : cat[0], c.onBed(anker), cat == null ? 0 : c.length - cat[1]);
        }
        {
            Chain c = Chain.proofCoil(Kutter.EDRIE_KETTE, 0.0095);
            c.holding = 3000;
            c.armAfter = 0;
            double[] f = {60, 1.2, 0}, fv = {0.5, 0, 0};
            c.lay(anker, 0, 0, f);
            double xa0 = c.node(c.nodes() - 1)[0];
            for (int i = 0; i < 20_000; i++) { f[0] += 0.5 * 0.004; c.step(anker, 0.004, f, fv); }
            double xa1 = c.node(c.nodes() - 1)[0];
            check(c.dragging() && xa1 - xa0 > 5 && !c.broken(), "Anker hält 3 kN: gezogen mit 0,5 m/s slippt er (%.1f m über den Grund), Zug an der Klüse %.1f kN", xa1 - xa0, c.fairleadTension() / 1000);
        }
        {
            Chain c = Kutter.edrieKette();
            c.armAfter = 1;
            double[] f = {66, 1.2, 0}, fv = {0, 0, 0};
            c.lay(anker, 0, 0, f);
            for (int i = 0; i < 500; i++) c.step(anker, 0.004, f, fv);
            for (int i = 0; i < 5000 && !c.broken(); i++) { f[1] += 3 * 0.004; f[0] += 3 * 0.004; fv[0] = fv[1] = 3; c.step(anker, 0.004, f, fv); }
            check(c.broken() && c.peak() >= c.breakingLoad, "Anker festgehakt, Klüse steigt mit 3 m/s: die Kette reißt bei %.1f kN (Bruchlast %.0f kN, 3/8″ Güte 30), am Glied %d von %d",
                    c.peak() / 1000, c.breakingLoad / 1000, c.brokenAt(), c.nodes() - 1);
        }

        teil("Kentern, Volllaufen, Sinken");
        {
            Hull h = Kutter.troller("kopflastig", 12.2, 3.7, 2.45, 1.6);
            h.houseBuoyancy = 0;
            Boat k = new Boat(h, -1, 4.5);
            k.settle(tief, 0, 0, 0);
            p = k.position();
            k.place(p[0], p[1], p[2], 0, 3);
            double tk = Double.NaN;
            for (int i = 0; i < 6000; i++) { k.step(tief, 0.01); if (k.capsized() && Double.isNaN(tk)) tk = k.time(); }
            check(k.capsized() && k.upright() < 0, "Schwerpunkt 4,5 m über dem Kiel (GM %.2f m): aus 3° gekentert nach %.1f s, Hochachse zeigt %.2f", k.gmDesign(), tk, k.upright());
        }
        {
            Boat s = Kutter.edrie();
            Water w15 = ruhig(15);
            s.settle(w15, 0, 0, 0);
            s.fill(0.95 * s.floodCapacity());
            for (int i = 0; i < 12_000 && !s.grounded(); i++) s.step(w15, 0.01);
            check(s.sunk() && s.grounded(), "voll Wasser (%.1f t): gesunken, liegt nach %.0f s auf dem Grund in 15 m", s.flood() / 1000, s.time());
        }

        teil("Maschine und Ruder");
        {
            Boat m = Kutter.sunmore();
            m.settle(tief, 0, 0, 90);
            m.throttle = 1;
            for (int i = 0; i < 6000; i++) m.step(tief, 0.01);
            double[] v = m.velocity();
            double kn = Math.hypot(v[0], v[2]) / 0.5144;
            double h0 = m.heading();
            m.rudder = 0.6;
            for (int i = 0; i < 3000; i++) m.step(tief, 0.01);
            double dh = ((m.heading() - h0) + 540) % 360 - 180;
            check(kn > 6 && kn < 10 && dh > 30, "voraus %.0f kN: %.1f kn nach 60 s (Rumpfgeschwindigkeit %.1f kn); Ruder 0,6 nach Steuerbord: %+.0f° in 30 s gedreht", m.maxThrust / 1000, kn, 1.34 * Math.sqrt(m.hull.loa * 0.92 / 0.3048), dh);
        }

        teil("Die Edrie in der Einzelwelle (Bucht, Front 15 m, 5 Faden, 40 Faden Kette, Anker fest)");
        {
            Bucht w = new Bucht();
            Boat b = Kutter.edrie();
            b.settle(w, 0, 0, 270);
            Chain c = Kutter.edrieKette();
            double[] fl = b.hull.fairlead();
            c.lay(w, -66, 0, b.toWorld(fl[0], fl[1], fl[2]));
            b.attach(c);
            double yRest = b.position()[1], yMax = -1e9, xMax = -1e9, rollMax = 0, pitchMax = 0, tFront = w.ankunft(0);
            long t0 = System.nanoTime();
            double dt = 1 / 30.0;
            for (int i = 0; i < 30 * 120; i++) {
                w.t += dt;
                b.step(w, dt);
                double[] q = b.position();
                yMax = Math.max(yMax, q[1]);
                xMax = Math.max(xMax, q[0]);
                rollMax = Math.max(rollMax, Math.abs(b.roll()));
                pitchMax = Math.max(pitchMax, Math.abs(b.pitch()));
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            double[] q = b.position();
            check(c.broken() && c.brokenTime() > tFront - 10 && c.brokenTime() < tFront + 8,
                    "Kette reißt nach %.1f s (Front am Ankerplatz nach %.1f s) bei %.1f kN – „The anchor chain snapped as the boat rose with the wave“", c.brokenTime(), tFront, c.peak() / 1000);
            check(yMax - yRest > 12, "das Boot steigt mit der Welle %.1f m und wird %.0f m zum Ufer getragen (Uferlinie bei %.0f m)", yMax - yRest, xMax, w.ufer());
            check(!b.capsized() && !b.sunk() && !b.grounded() && q[0] < xMax - 20, "der Rückstrom trägt es zurück (x %.0f m): aufrecht, schwimmt, %.1f t Wasser im Rumpf; größte Krängung %.0f°, Trimm %.0f°",
                    q[0], b.flood() / 1000, rollMax, pitchMax);
            check(ms / 120 < 120, "Rechenzeit %.0f ms je Sekunde Welle (Boot %d Proben, Kette %d Glieder, Teilschritt %.0f ms)", ms / 120, b.hull.probes(), c.nodes() - 1, b.maxStep * 1000);
        }

        teil("Netz");
        float[] m = Mesh.kutter(Kutter.edrie().hull);
        check(Mesh.vertices(m) > 3000, "Netz der Edrie: %,d Dreiecke (Rumpf, Deck, Spiegel, Schanzkleid, Steuerhaus, Mast, Trollstangen, Winde)", Mesh.vertices(m) / 3);

        System.out.printf(Locale.GERMAN, "%n%d Prüfungen in %.1f s%n", n, (System.nanoTime() - tStart) / 1e9);
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " FEHLER");
        if (bad > 0) System.exit(1);
    }
}
