package com.dan.beacon.demo;

import com.dan.beacon.Beacon;
import com.dan.beacon.Characteristic;
import com.dan.beacon.Lens;
import com.dan.beacon.LightColour;
import com.dan.beacon.Range;
import com.dan.beacon.Scatter;
import com.dan.beacon.ScatterGrid;
import com.dan.beacon.SectorTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“.
 * Geprüft werden der Parser der Kennungen (gegen das Verzeichnis der Tegeler Plate), die Sektoren des Roten Sands, die
 * Tragweiten nach IALA, die Strahlverfolgung durch die Linse (Bündelung, Strahlbreite, Energie), die Blitze einer
 * Drehlinse aus Sicht eines Beobachters, das Nahfeld im Laternenraum, das Streugitter und der Strahl gegen eine feine
 * Rechnung mit 20 000 Schritten.
 */
public final class BeaconCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        for (String l : new BeaconCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // ------------------------------------------------ Kennungen
        Characteristic fl3 = Characteristic.tryParse("Fl(3) W 15s");
        check(fl3 != null && fl3.period == 15 && fl3.flashesPerPeriod() == 3 && Math.abs(fl3.on[2] - 1.5) < 1e-9 && Math.abs(fl3.on[4] - 3.0) < 1e-9,
                "Fl(3) W 15s: drei Blitze 0,5 s mit 1 s Pause, Wiederkehr 15 s" + (fl3 != null ? " (" + fl3.timesText() + ")" : ""));
        Characteristic tp = Characteristic.tryParse("Oc(3) WRG 12s");
        double[] want = {1, 3, 4, 6, 7, 12};
        boolean tpOk = tp != null && tp.on.length == 6 && tp.sectored;
        if (tpOk) for (int i = 0; i < 6; i++) tpOk &= Math.abs(tp.on[i] - want[i]) < 1e-9;
        check(tpOk, "Oc(3) WRG 12s ergibt den Takt der Tegeler Plate aus dem Verzeichnis: (1)+2+(1)+2+(1)+5");
        Characteristic card = Characteristic.tryParse("Q(6)+LFl W 15s");
        check(card != null && card.on.length == 14 && Math.abs(card.on[13] - card.on[12] - 2) < 1e-9, "Q(6)+LFl W 15s: sechs Funkel, dann ein langer Blitz von 2 s");
        Characteristic mo = Characteristic.tryParse("Mo(U) W 15s");
        check(mo != null && mo.on.length == 6 && Math.abs(mo.on[5] - mo.on[4] - 1.5) < 1e-9, "Mo(U): zwei Punkte und ein Strich (· · –)");
        Characteristic al = Characteristic.tryParse("Al.Fl WR 5s"), alf = Characteristic.tryParse("Al.WR");
        check(al != null && al.cycle == 10 && al.colourAt(0.2) == LightColour.W && al.colourAt(5.2) == LightColour.R && alf != null && alf.alternating,
                "Wechselfeuer: Al.Fl WR 5s wechselt die Farbe je Blitz (Wiederkehr der Farben 10 s), Al.WR ohne Art = Festfeuer");
        Characteristic blz = Characteristic.tryParse("Blz(2) W 9s");
        check(blz != null && blz.text.equals("Fl(2) W 9s"), "deutsche Kürzel: Blz(2) W 9s = " + (blz != null ? blz.text : "—"));
        String[][] badTexts = {{"Fl W", "Wiederkehr"}, {"Fl(3) X 15s", "Farbe"}, {"Fl(3 W 15s", ")"}, {"Xy 5s", "Art"}, {"Fl(6) W 3s", "passt nicht"}};
        boolean errs = true;
        StringBuilder eb = new StringBuilder();
        for (String[] b : badTexts) {
            try {
                Characteristic.parse(b[0]);
                errs = false;
            } catch (Characteristic.ParseException e) {
                if (!e.getMessage().contains(b[1])) errs = false;
                eb.append(eb.length() > 0 ? "; " : "").append(b[0]).append(" → ").append(e.getMessage());
            }
        }
        check(errs, "Fehler mit verständlicher Meldung: " + eb);
        double sum = 0, n = 15_000;
        for (int i = 0; i < n; i++) sum += fl3.level(i * 15 / n);
        check(Math.abs(sum / n - fl3.dutyCycle()) < 0.003, f("Lampenträgheit verschiebt nichts: Mittel über eine Wiederkehr %.4f, Lichtanteil %.4f", sum / n, fl3.dutyCycle()));
        // ------------------------------------------------ Sektoren
        Beacon rs = Beacon.roterSand1985();
        SectorTable st = rs.sectors();
        check(st.at(180).colour() == LightColour.W && st.at(200).colour() == LightColour.R && st.at(326).name().equals("Leitsektor")
                        && st.at(335).colour() == LightColour.G && st.at(10).name().equals("Schwachlicht"),
                "Roter Sand 1985: Peilung 180° weiß, 200° rot, 326° Leitsektor, 335° grün, 10° Schwachlicht");
        check(st.atAzimuth(20).colour() == LightColour.R && st.atAzimuth(0).colour() == LightColour.W,
                "Peilung von See: ein Schiff im Azimut 20° vom Turm peilt 200° und sieht Rot");
        double[] edge = st.rgbAt(185, Range::intensityForNominal), w180 = st.rgbAt(180, Range::intensityForNominal);
        check(edge[0] > edge[1] * 1.2 && LightColour.luminance(edge) < LightColour.luminance(w180), "an der Grenze 185° gehen Weiß und Rot über 0,5° ineinander über");
        // ------------------------------------------------ Tragweiten
        check(Math.abs(Range.intensityForNominal(9) - 835) < 5 && Math.abs(Range.nominalRange(835) - 9) < 0.01,
                f("Nenntragweite 9 sm ↔ %.0f cd (IALA E-200-2), zurück %.2f sm", Range.intensityForNominal(9), Range.nominalRange(835)));
        double g = Range.geographicRange(24, 5);
        check(Math.abs(g - 14.8) < 0.1, f("geografische Tragweite: Feuer 24 m, Auge 5 m → %.1f sm", g));
        double lr10 = Range.luminousRangeAtVisibility(835, 10), lr2 = Range.luminousRangeAtVisibility(835, 2);
        check(lr2 < lr10 && lr2 > 1 && lr2 < 4, f("Lichttragweite des weißen Sektors bei 10 sm Sicht %.1f sm, bei 2 sm Sicht %.1f sm", lr10, lr2));
        check(Math.abs(Range.effective(Range.peakForEffective(1000, 0.26), 0.26) - 1000) < 1e-6, "Blondel-Rey: Spitzenstärke und wirksame Stärke eines Blitzes passen zusammen");
        // ------------------------------------------------ Linse
        Lens lens = Lens.drum();
        Lens.Field p0 = lens.trace(0, 100_000, 1), p16 = lens.trace(0.016, 100_000, 1), p16b = lens.trace(0.016, 100_000, 1);
        check(p0.meanExitErrorDeg() < 0.3, f("Punktlampe: das Glas macht die Strahlen waagerecht, mittlere Abweichung %.2f° (gekrümmte Außenflächen)", p0.meanExitErrorDeg()));
        double expect = 2 * Math.toDegrees(Math.atan(0.016 / lens.focal));
        check(Math.abs(p16.fwhmDeg() - expect) / expect < 0.12, f("Strahlbreite aus der Lampengröße: %.2f° (2·atan(r/f) = %.2f°)", p16.fwhmDeg(), expect));
        check(p16.collimated() > 0.5 && p16.collimated() < 0.75 && p16.collimated() + p16.stray() <= 1.0001,
                f("Energie: %.0f %% gebündelt, %.0f %% Streulicht, %.0f %% an Messing, Sockel und als Reflexion verloren", p16.collimated() * 100, p16.stray() * 100, p16.absorbed() * 100));
        check(java.util.Arrays.equals(p16.far(), p16b.far()), "gleicher seed, gleiche Verfolgung");
        // ------------------------------------------------ Drehlinse aus Sicht eines Beobachters
        for (Beacon b : new Beacon[]{Beacon.stormNight(), Beacon.flash3()}) {
            b.snapOn(true);
            Beacon.Snapshot s = b.snapshot();
            double rotP = 360 / s.omega(), dt = 0.005, peak = 0;
            List<double[]> fl = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                boolean was = false;
                double start = 0;
                for (double t = 0; t < rotP; t += dt) {
                    b.setTime(t, s.omega() * t);
                    double v = LightColour.luminance(b.snapshot().intensityAzEl(0, 0));
                    if (k == 0) { peak = Math.max(peak, v); continue; }
                    boolean on = v > peak / 2;
                    if (on && !was) start = t;
                    if (!on && was) fl.add(new double[]{start, t});
                    was = on;
                }
            }
            Characteristic c = b.characteristic();
            boolean ok = fl.size() == c.flashesPerPeriod() * Math.round(rotP / c.period);
            double maxErr = 0, dur = 0;
            for (int i = 0; i < fl.size() && ok; i++) {
                int j = i % c.flashesPerPeriod();
                double mid = (c.on[2 * j] + c.on[2 * j + 1]) / 2 + (i / c.flashesPerPeriod()) * c.period;
                maxErr = Math.max(maxErr, Math.abs((fl.get(i)[0] + fl.get(i)[1]) / 2 - mid));
                dur += (fl.get(i)[1] - fl.get(i)[0]) / fl.size();
            }
            check(ok && maxErr < 0.02 && Math.abs(dur - s.flashSeconds()) / s.flashSeconds() < 0.1,
                    f("%s: Beobachter im Norden sieht %d Blitze je Umdrehung (%.0f s) zur Zeit der Kennung (± %.3f s), Dauer %.2f s (aus Strahlbreite %.2f s)",
                            c.text, fl.size(), rotP, maxErr, dur, s.flashSeconds()));
            double eff = Range.effective(peak, s.flashSeconds());
            check(Math.abs(Range.nominalRange(eff) - b.sectors().maxRangeNm()) < 0.3,
                    f("%s: wirksame Stärke %,.0f cd ergibt die Nenntragweite %.1f sm", c.text, eff, Range.nominalRange(eff)));
        }
        Beacon sn = Beacon.stormNight();
        sn.snapOn(true);
        Beacon.Snapshot s0 = sn.snapshot();
        sn.setTime(0.25 + 90 / s0.omega(), 90 + 0.25 * s0.omega());
        double v90 = LightColour.luminance(sn.snapshot().intensityAzEl(90, 0));
        sn.setTime(0.25, 0.25 * s0.omega());
        double v0 = LightColour.luminance(sn.snapshot().intensityAzEl(0, 0));
        check(Math.abs(v90 - v0) / v0 < 1e-6, "im Osten kommt derselbe Blitz eine Viertelumdrehung später (Drehung im Uhrzeigersinn)");
        // ------------------------------------------------ Nahfeld
        Beacon drum = Beacon.roterSand1985();
        drum.y = 0;
        drum.snapOn(true);
        Beacon.Snapshot ds = drum.snapshot();
        Lens.Field fd = ds.field();
        double flux = 0, dy = 0.005;
        for (double y = fd.y0(); y < fd.y1(); y += dy) flux += fd.near(1.6, y)[0] * dy;
        flux *= 2 * Math.PI;
        check(flux > 0.4 && flux < 1.0, f("Nahfeld: durch den Zylinder r 1,6 m geht %.0f %% des Lampenlichts (der Rest bleibt an Messing und Sockel)", flux * 100));
        double eGlass = LightColour.luminance(ds.nearIrradiance(0, 0, -1.84, 0, 0, 1)), eFloor = 0;
        double rMax = 0;
        for (double r = 0.5; r < 1.8; r += 0.01) {
            double e = LightColour.luminance(ds.nearIrradiance(r, -1.31, 0, 0, 1, 0));
            if (e > eFloor) { eFloor = e; rMax = r; }
        }
        check(eGlass > 20 && eFloor > 1 && rMax > 0.9 && rMax < 1.35,
                f("Laternenraum: Glas in Linsenhöhe %.0f lx, Lichtring auf den Dielen bei r %.2f m mit %.0f lx (Spalt zwischen Linse und Sockel)", eGlass, rMax, eFloor));
        // ------------------------------------------------ Streugitter und Strahl
        ScatterGrid grid = new ScatterGrid(32, 16, 24);
        grid.fill(Scatter.uniform(7e-4), 0, 24, 0);
        check(Math.abs(grid.sample(0.3, 0.1, -0.9, 120) - 7e-4) < 1e-9, "Streugitter: gleichmäßiger Regen bleibt überall gleich (trilinear)");
        Beacon fb = Beacon.flash3();
        fb.y = 24;
        fb.hazePerM = 1.5e-4;
        fb.snapOn(true);
        double[] o = {300, 10, 200}, d = norm(-340, 14, -160);
        // die Drehung so wählen, dass ein Strahl den Blick kreuzt
        double bestRot = 0, bestL = -1;
        for (int k = 0; k < 72; k++) {
            fb.setTime(0, k * 5);
            double l = fb.snapshot().beamRadiance(o, d, 3000, null, 48, 0.5)[1];
            if (l > bestL) { bestL = l; bestRot = k * 5; }
        }
        fb.setTime(0, bestRot);
        Beacon.Snapshot bs = fb.snapshot();
        double[] fast = bs.beamRadiance(o, d, 3000, grid, 48, 0.5), fine = brute(bs, o, d, 3000, grid, 20_000);
        double rel = Math.abs(fast[1] - fine[1]) / fine[1];
        check(rel < 0.03, f("Strahl mit 48 gleichwinkligen Schritten gegen 20 000 gleich lange: %.1f %% Abweichung", rel * 100));
        Beacon fx = Beacon.roterSand1985();
        fx.y = 24;
        fx.hazePerM = 1.5e-4;
        fx.snapOn(true);
        Beacon.Snapshot xs = fx.snapshot();
        double[] fa = xs.beamRadiance(o, d, 3000, grid, 48, 0.5), fi = brute(xs, o, d, 3000, grid, 20_000);
        double rx = Math.abs(fa[0] - fi[0]) / fi[0];
        check(rx < 0.03, f("Festfeuer (Trommel) ebenso: %.1f %% Abweichung", rx * 100));
        double[] clear = bs.beamRadiance(o, d, 3000, null, 48, 0.5);
        check(fast[1] > 3 * clear[1], f("Regen 5 mm/h macht den Strahl %.1f-mal heller als klare Luft (Sicht 20 km)", fast[1] / clear[1]));
        // ------------------------------------------------ Schalter, Voreinstellungen
        Beacon sw = Beacon.roterSand1985();
        sw.ambientLux = 10_000;
        sw.step(10);
        boolean offDay = !sw.isOn();
        sw.ambientLux = 200;
        sw.step(10);
        boolean stillOff = !sw.isOn();
        sw.ambientLux = 100;
        sw.step(10);
        check(offDay && stillOff && sw.isOn(), "Dämmerungsschalter: am Tag aus, bei 200 lx noch aus, unter 150 lx an (Hysterese)");
        boolean presets = true;
        for (int i = 0; i < Beacon.PRESETS.length; i++) presets &= Beacon.preset(i).snapshot().maxCd() > 0;
        check(presets, Beacon.PRESETS.length + " Voreinstellungen: " + String.join(", ", Beacon.PRESETS));
        Beacon oc = Beacon.tegelerPlate();
        oc.optic = Beacon.Optic.ROTATING;
        check(oc.effectiveOptic() == Beacon.Optic.DRUM, "eine unterbrochene Kennung (Oc) kann keine Drehlinse erzeugen, es bleibt die Trommel");
        // ------------------------------------------------ seit Sylt Phase 10: Kampen und Rotes Kliff
        Beacon ka = Beacon.kampen(), rk = Beacon.rotesKliff();
        Characteristic kc = Characteristic.tryParse("Iso WR 7s");
        check(kc != null && kc.period == 7 && Math.abs(kc.dutyCycle() - 0.5) < 1e-9 && !kc.flashing() && ka.sectors().at(112).colour() == LightColour.R
                        && ka.sectors().at(30).colour() == LightColour.W && ka.y == 62,
                f("Kampen: Iso WR 7s (3,5 s Licht, 3,5 s dunkel), Feuerhöhe 62 m, rot in Peilung 112° (%s)", ka.sectors().describe()));
        check(rk.sectors().at(90).colour() == LightColour.W && rk.sectors().at(60).colour() == LightColour.G && rk.sectors().at(120).colour() == LightColour.R
                        && Math.abs(Range.geographicRange(23, 2) - 2.075 * (Math.sqrt(23) + Math.sqrt(2))) < 1e-9,
                f("Rotes Kliff bis 1975: F WRG, Feuerhöhe 23 m, Leitsektor weiß um 90° (%s)", rk.sectors().describe()));
        check(Beacon.PRESETS.length == 11 && Beacon.preset(5).name.equals("Kampen") && Beacon.preset(6).name.startsWith("Quermarken"), "Voreinstellungen um Kampen und Rotes Kliff erweitert");
        // ------------------------------------------------ seit Timmendorf Phase 9: Travemünde, Nordermole, Niendorf, Alter Leuchtturm
        Beacon tm = Beacon.travemuende(), nm = Beacon.nordermole(), ni = Beacon.niendorf(), aDark = Beacon.alterLeuchtturm(false), aHist = Beacon.alterLeuchtturm(true);
        Characteristic tc = tm.characteristic();
        check(tc.flashing() && tc.period == 4 && tm.effectiveOptic() == Beacon.Optic.ROTATING && tm.y > 114 && tm.sectors().at(180).colour() == LightColour.R
                        && tm.sectors().at(300).colour() == LightColour.W && Range.geographicRange(tm.y, 2) > 25,
                f("Travemünde (Maritim): Fl WR 4s mit Drehlinse, Feuerhöhe 114,7 m, Kimm aus 2 m Augenhöhe %.1f sm (%s)", Range.geographicRange(tm.y, 2), tm.sectors().describe()));
        Characteristic oc4 = nm.characteristic();
        check(oc4.period == 4 && Math.abs(oc4.dutyCycle() - 0.75) < 1e-6 && nm.sectors().at(150).colour() == LightColour.R && nm.sectors().at(250).colour() == LightColour.G
                        && nm.sectors().at(30).colour() == LightColour.G && nm.effectiveOptic() == Beacon.Optic.DRUM,
                f("Nordermole: Oc RG 4s (3 s Licht, 1 s dunkel), rot 123–201°, grün 201–66° (%s)", nm.sectors().describe()));
        aDark.ambientLux = 0;
        aDark.step(5);
        aHist.ambientLux = 0;
        aHist.step(5);
        check(!aDark.isOn() && aHist.isOn() && ni.sectors().at(0).colour() == LightColour.R && Beacon.preset(10).name.startsWith("Alter"),
                "Alter Leuchtturm: heute dunkel auch bei Nacht, historisch F W; Hafenfeuer Niendorf F R");
        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Fehler.");
        return lines;
    }

    static double[] norm(double x, double y, double z) { double l = Math.sqrt(x * x + y * y + z * z); return new double[]{x / l, y / l, z / l}; }

    /** Der Strahl in gleich langen Schritten (Bezug für die gleichwinklige Abtastung). */
    static double[] brute(Beacon.Snapshot s, double[] o, double[] d, double tmax, ScatterGrid g, int n) {
        double[] L = new double[3];
        double dt = tmax / n;
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) * dt;
            double qx = o[0] + d[0] * t - s.x(), qy = o[1] + d[1] * t - s.y(), qz = o[2] + d[2] * t - s.z();
            double r = Math.sqrt(qx * qx + qy * qy + qz * qz);
            double wx = qx / r, wy = qy / r, wz = qz / r;
            double sig = s.hazePerM() + g.sample(wx, wy, wz, r);
            double[] I = s.intensity(wx, wy, wz);
            double k = sig * Beacon.phase(-(wx * d[0] + wy * d[1] + wz * d[2])) * Math.exp(-sig * (r + t)) / (r * r) * dt;
            for (int c = 0; c < 3; c++) L[c] += I[c] * k;
        }
        return L;
    }
}
