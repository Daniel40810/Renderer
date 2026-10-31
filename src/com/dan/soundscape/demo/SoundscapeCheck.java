package com.dan.soundscape.demo;

import com.dan.soundscape.Aeolian;
import com.dan.soundscape.Blaze;
import com.dan.soundscape.Crowd;
import com.dan.soundscape.Workshop;
import com.dan.soundscape.Dsp;
import com.dan.soundscape.Edge;
import com.dan.soundscape.Flap;
import com.dan.soundscape.Rig;
import com.dan.soundscape.Steam;
import com.dan.soundscape.Hum;
import com.dan.soundscape.Room;
import com.dan.soundscape.Soundscape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Lautsprecher (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“. Gerechnet wird in
 * Zahlen, ausgewertet mit FFT: Rauschfarben, Filter, Wind nach Beaufort, Äolstöne nach Strouhal, Hörner nach KVR,
 * Laufzeit, Abstand, Richtung und Ohrenlaufzeit, Luftdämpfung, Wand und Raum, Regen auf Wasser und Glas, Donner,
 * Möwen, Maschine, Begrenzer, Anpassung des Ohrs, seed, Stufen und Rechenzeit. Seit Haithabu Phase 14 dazu die
 * Siedlung: Pegel und Gestalt der Rufe, Takt der Werkstätten, Ruderschlag, Feuer, Stimmengewirr, Schilf im Wind,
 * Glucksen an Pfählen, Langhaus, Dämpfung über Land und die Rechenzeit einer Siedlung. Seit Brocken Phase 15 das
 * Gebirge: Pfeife, Bergvögel, Eis, Dampflok (Auspuff, Rollen, Schienenstöße, Bremsen, Stand), Kanten im Sturm und die
 * Walpurgisnacht. Seit Timmendorf Phase 15 die Bucht: Pegel und Gestalt von Fall, Segel, Winsch, Signalhorn, Startschuss,
 * Abschuss, Zerlegung, Knistern und Fährhorn, Laufzeit des Feuerwerks, Flaggen, Fallen im Hafen und Schritte.
 */
public final class SoundscapeCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        for (String l : new SoundscapeCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    /** Eine stille Bühne: kein Wind, keine See, Pegel wie gerechnet. */
    static Soundscape quiet() {
        Soundscape s = Soundscape.beaufort(0);
        s.waveHeight = 0;
        s.windSpeed = 0;
        s.gustiness = 0;
        s.autoGain = false;
        s.earX = 0; s.earY = 2; s.earZ = 0; s.earYawDeg = 0;
        return s;
    }

    static double db(double rms) { return Dsp.db(rms); }

    public List<String> run() {
        lines.clear();
        bad = 0;
        int sr = Soundscape.Quality.HIGH.sampleRate;

        // ------------------------------------------------ Rauschen und Filter
        Dsp.Pink pk = new Dsp.Pink(3);
        Dsp.Brown br = new Dsp.Brown(3);
        double[] xp = new double[1 << 18], xb = new double[1 << 18];
        for (int i = 0; i < xp.length; i++) { xp[i] = pk.next(); xb[i] = br.next(); }
        double[] pp = Analysis.spectrum(xp, 8192), pb = Analysis.spectrum(xb, 8192);
        double slopeP = 10 * Math.log10(band(pp, sr, 8192, 3000, 3400) / band(pp, sr, 8192, 187, 212)) / 4;
        double slopeB = 10 * Math.log10(band(pb, sr, 8192, 3000, 3400) / band(pb, sr, 8192, 187, 212)) / 4;
        check(Math.abs(slopeP + 3) < 0.8 && Math.abs(slopeB + 6) < 1.2, f("Rauschen: rosa %.1f dB je Oktave (−3), braun %.1f (−6)", slopeP, slopeB));
        Dsp.Biquad bp = new Dsp.Biquad().bandpass(1000, 5, sr), lp = new Dsp.Biquad().lowpass(1000, Math.sqrt(0.5), sr);
        check(Math.abs(bp.response(1000, sr) - 1) < 0.01 && Math.abs(20 * Math.log10(lp.response(1000, sr)) + 3.01) < 0.05 && bp.response(2000, sr) < 0.25,
                f("Filter: Bandpass 0 dB in der Mitte, %.1f dB eine Oktave daneben; Tiefpass −3,0 dB an der Grenze", 20 * Math.log10(bp.response(2000, sr))));

        // ------------------------------------------------ Wind nach Beaufort
        double[] lw = new double[13];
        for (int b : new int[]{2, 6, 10}) {
            Soundscape s = Soundscape.beaufort(b);
            s.autoGain = false;
            s.waveHeight = 0;
            s.rainRate = 0;
            s.earY = 10;
            s.render(1);
            float[] o = s.render(4);
            lw[b] = db(Analysis.rms(o, sr, 0, 4));
        }
        check(lw[2] < 50 && lw[6] > lw[2] + 15 && lw[10] > lw[6] + 8 && lw[10] > 80 && lw[10] < 98,
                f("Wind am Ohr: Bft 2 %.0f dB, Bft 6 %.0f dB, Bft 10 %.0f dB", lw[2], lw[6], lw[10]));

        // ------------------------------------------------ Äolston
        Soundscape a = quiet();
        a.windSpeed = 15;
        a.earY = 10;
        a.bodies.add(new Aeolian("Stab", 1.5, 10, 0, 0.025, 20));
        a.render(2);
        float[] ao = a.render(4);
        double u = a.windAt(10), fWant = 0.2 * u / 0.025;
        double fGot = Analysis.peak(Analysis.spectrum(Analysis.mono(ao, sr, 0, 4), 16384), sr, 16384, fWant * 0.6, fWant * 1.6);
        a.windSpeed = 30;
        a.render(2);
        float[] ao2 = a.render(4);
        double fGot2 = Analysis.peak(Analysis.spectrum(Analysis.mono(ao2, sr, 0, 4), 16384), sr, 16384, fWant * 1.2, fWant * 3.2);
        check(Math.abs(fGot / fWant - 1) < 0.04 && Math.abs(fGot2 / fGot - 2) < 0.12,
                f("Äolston am Stab 25 mm: %.0f Hz bei %.1f m/s (Strouhal: %.0f Hz), doppelter Wind %.0f Hz", fGot, u, fWant, fGot2));

        // ------------------------------------------------ Hörner nach KVR
        Soundscape h = quiet();
        h.horn(Soundscape.Kind.NEBELHORN, 0, 2, 300, 1, 2.0);
        float[] ho = h.render(5);
        double t0 = Analysis.onset(ho, sr, 0, Dsp.amp(70)), t1 = offset(ho, sr, t0 + 0.2, Dsp.amp(70));
        double fh = Analysis.peak(Analysis.spectrum(Analysis.mono(ho, sr, t0 + 0.3, 1.2), 8192), sr, 8192, 80, 400);
        double fEnd = Analysis.peak(Analysis.spectrum(Analysis.mono(ho, sr, t0 + 1.9, 0.25), 8192), sr, 8192, 80, 400);
        check(Math.abs(fh - 175) < 4 && fEnd < fh * 0.95 && Math.abs((t1 - t0) - 2.2) < 0.3,
                f("Nebelhorn: Grundton %.0f Hz, am Ende %.0f Hz (Diaphon fällt ab), Ton mit Ausklingen %.2f s für 2 s", fh, fEnd, t1 - t0));
        Soundscape ty = quiet();
        ty.horn(Soundscape.Kind.TYFON, 0, 2, 300, 1, 2.0);
        float[] tyo = ty.render(4);
        double tst = 300 / ty.soundSpeed() + 0.4;
        double ft = Analysis.peak(Analysis.spectrum(Analysis.mono(tyo, sr, tst, 1.2), 8192), sr, 8192, 80, 500);
        double lt = db(Analysis.rms(tyo, sr, tst, 1.2)) + 20 * Math.log10(300);
        check(ft >= 130 && ft <= 350 && Math.abs(lt - 138) < 4, f("Typhon: Grundton %.0f Hz (KVR für 75 bis 200 m: 130 bis 350 Hz), %.0f dB in 1 m (138)", ft, lt));

        // ------------------------------------------------ Laufzeit, Abstand, Richtung
        Soundscape d = quiet();
        double c = d.soundSpeed();
        d.event(Soundscape.Kind.BRECHER, 0, 2, -686, 1);
        float[] dd = d.render(4);
        double on = Analysis.onset(dd, sr, 0, Dsp.amp(45));
        check(Math.abs(on - 686 / c) < 0.03, f("Laufzeit: Brecher in 686 m hört man nach %.3f s (686 m / %.1f m/s = %.3f s)", on, c, 686 / c));
        double[] lev = new double[2];
        double[] dist = {200, 2000};
        for (int k = 0; k < 2; k++) {
            Soundscape s = quiet();
            s.horn(Soundscape.Kind.TYFON, 0, 2, -dist[k], 1, 2.0);
            float[] o = s.render(dist[k] / c + 2.5);
            double st = dist[k] / c + 0.4;
            lev[k] = db(Analysis.rms(o, sr, st, 1.2));
        }
        check(Math.abs(lev[0] - lev[1] - 20) < 2.5, f("Abstand: Typhon in 200 m und 2 km, %.1f dB leiser (1/r: 20 dB, dazu die Luft)", lev[0] - lev[1]));
        Soundscape r = quiet();
        r.horn(Soundscape.Kind.TYFON, 300, 2, 0, 1, 1.5);
        float[] ro = r.render(3);
        double lL = Analysis.rms(ro, sr, 1.4, 1, 0), lR = Analysis.rms(ro, sr, 1.4, 1, 1);
        int lag = lag(ro, sr, 1.4, 0.5, 60);
        check(20 * Math.log10(lR / lL) > 8 && lag > 20 && lag < 36,
                f("Richtung: Quelle rechts, rechts %.0f dB lauter, linkes Ohr %.2f ms später (Kopf: 0,66 ms)", 20 * Math.log10(lR / lL), lag * 1000.0 / sr));

        // ------------------------------------------------ Luft
        double[] cen = new double[2];
        double[] far = {50, 2000};
        for (int k = 0; k < 2; k++) {
            Soundscape s = quiet();
            s.event(Soundscape.Kind.BRECHER, 0, 2, -far[k], 1);
            float[] o = s.render(far[k] / c + 1.5);
            cen[k] = Analysis.centroid(Analysis.spectrum(Analysis.mono(o, sr, far[k] / c, 1.2), 4096), sr, 4096);
        }
        check(cen[1] < cen[0] * 0.4, f("Luft nimmt die Höhen: Schwerpunkt eines Brechers in 50 m %.0f Hz, in 2 km %.0f Hz", cen[0], cen[1]));

        // ------------------------------------------------ Wand und Raum
        Soundscape wo = Soundscape.beaufort(8), wi = Soundscape.beaufort(8);
        for (Soundscape s : new Soundscape[]{wo, wi}) { s.autoGain = false; s.rainRate = 0; s.earY = 10; }
        wi.inside = 1;
        wi.room = Room.STONE_TOWER;
        wo.render(1);
        wi.render(1);
        float[] oo = wo.render(3), io = wi.render(3);
        double drop = db(Analysis.rms(oo, sr, 0, 3)) - db(Analysis.rms(io, sr, 0, 3));
        double ci = Analysis.centroid(Analysis.spectrum(Analysis.mono(io, sr, 0, 3), 4096), sr, 4096),
                co = Analysis.centroid(Analysis.spectrum(Analysis.mono(oo, sr, 0, 3), 4096), sr, 4096);
        check(drop > 18 && ci < 0.5 * co, f("Steinturm: Sturm draußen %.0f dB leiser und dumpf (Schwerpunkt %.0f statt %.0f Hz)", drop, ci, co));
        Dsp.Reverb rvb = new Dsp.Reverb(sr, 1.6);
        rvb.set(Room.STONE_TOWER.t60(), 0.35);
        float[] hl = new float[sr * 4], hr = new float[sr * 4];
        for (int i = 0; i < hl.length; i++) rvb.process(i == 0 ? 1f : 0f, hl, hr, i, 1f);
        double t60 = decay60(hl, sr);
        check(Math.abs(t60 / Room.STONE_TOWER.t60() - 1) < 0.25, f("Hall im Steinturm: Nachhallzeit %.2f s (eingestellt %.1f s, aus der Rückwärtsintegration nach Schroeder)", t60, Room.STONE_TOWER.t60()));

        // ------------------------------------------------ Regen
        Soundscape rn = quiet(), rn0 = quiet();
        rn.rainRate = 8;
        rn.render(1);
        float[] rno = rn.render(3), r0o = rn0.render(3);
        double[] rs = Analysis.spectrum(Analysis.mono(rno, sr, 0, 3), 4096);
        double hi = band(rs, sr, 4096, 1500, 6000), lo = band(rs, sr, 4096, 100, 400);
        check(db(Analysis.rms(rno, sr, 0, 3)) > db(Analysis.rms(r0o, sr, 0, 3)) + 20 && hi > lo * 2,
                f("Regen 8 mm/h auf dem Wasser: %.0f dB, Tropfen zwischen 1,5 und 6 kHz %.0f dB über dem Bass", db(Analysis.rms(rno, sr, 0, 3)), 10 * Math.log10(hi / lo)));
        Soundscape gl = quiet();
        gl.glassDrops = 40;
        float[] glo = gl.render(10);
        long ticks = gl.glassTicks();
        double tdb = db(Analysis.rms(glo, sr, 0, 10));
        check(Math.abs(ticks / 10.0 - 40) < 6 && tdb > 40, f("Regen auf Glas: %d Ticks in 10 s für 40 je s, %.0f dB", ticks, tdb));

        // ------------------------------------------------ Donner
        Soundscape th = quiet();
        double[] dist2 = new double[400], w = new double[400];
        for (int i = 0; i < 400; i++) { dist2[i] = 1000 + 3000.0 * i / 399; w[i] = 1; }
        th.thunder(0, 1000, -1000, 95, dist2, w, new double[]{0, 0.05, 0.12}, true);
        float[] tho = th.render(16);
        double tOn = Analysis.onset(tho, sr, 0, 1e-3), tOff = offset(tho, sr, tOn + 0.5, Dsp.amp(70));
        double expect = 3000 / c;
        double thc = Analysis.centroid(Analysis.spectrum(Analysis.mono(tho, sr, tOn, 1), 4096), sr, 4096);
        Soundscape tn = quiet();
        double[] near = new double[400];
        for (int i = 0; i < 400; i++) near[i] = 250 + 2000.0 * i / 399;
        tn.thunder(0, 300, -250, 115, near, w, null, true);
        float[] tno = tn.render(3);
        double tnc = Analysis.centroid(Analysis.spectrum(Analysis.mono(tno, sr, 0, 0.5), 4096), sr, 4096);
        check(Math.abs((tOff - tOn) / expect - 1) < 0.3 && tnc > 1.5 * thc,
                f("Donner: Kanal von 1 bis 4 km rollt %.1f s (3000 m / c = %.1f s); naher Knall Schwerpunkt %.0f Hz, ferner Donner %.0f Hz", tOff - tOn, expect, tnc, thc));

        // ------------------------------------------------ Möwe, Maschine
        Soundscape gu = quiet();
        double minF = 1e9, maxF = 0, len = 0;
        for (int k = 0; k < 12; k++) {
            Soundscape s = quiet();
            s.seed = 100 + k;
            s.event(Soundscape.Kind.MOEWE, 0, 2, -5, 1);
            float[] o = s.render(3.5);
            double on1 = Analysis.onset(o, sr, 0, Dsp.amp(55)), off1 = offset(o, sr, on1 + 0.05, Dsp.amp(55));
            double fq = Analysis.peak(Analysis.spectrum(Analysis.mono(o, sr, on1, Math.max(0.1, off1 - on1)), 2048), sr, 2048, 400, 1600);
            minF = Math.min(minF, fq);
            maxF = Math.max(maxF, fq);
            len = Math.max(len, off1 - on1);
        }
        check(minF > 550 && maxF < 1400 && len > 0.25 && len < 3.5, f("Möwe: Grundton %.0f bis %.0f Hz (Silbermöwe), längster Ruf %.1f s", minF, maxF, len));
        Soundscape hm = quiet();
        Hum diesel = new Hum("Kümo", 0, 0, 0);
        diesel.firingHz = Hum.firing(750, 6);
        diesel.aboard = true;
        hm.hums.add(diesel);
        float[] hmo = hm.render(3);
        double fd = Analysis.peak(Analysis.spectrum(Analysis.mono(hmo, sr, 0.5, 2.5), 32768), sr, 32768, 20, 80);
        check(Math.abs(fd - 37.5) < 1 && Math.abs(db(Analysis.rms(hmo, sr, 0.5, 2.5)) - diesel.aboardDb) < 4,
                f("Diesel an Bord: Zündfrequenz %.1f Hz (750 U/min, 6 Zylinder: 37,5 Hz), %.0f dB im Steuerhaus", fd, db(Analysis.rms(hmo, sr, 0.5, 2.5))));

        // ------------------------------------------------ seit Haithabu Phase 14: die Siedlung
        siedlung(sr);

        // ------------------------------------------------ Begrenzer, Anpassung des Ohrs
        Soundscape lim = quiet();
        lim.horn(Soundscape.Kind.TYFON, 0, 2, -1.5, 1, 1.5);
        float[] lo2 = lim.render(2);
        double peak = 0;
        for (float v : lo2) peak = Math.max(peak, Math.abs(v));
        check(peak <= 1.0, f("Begrenzer: Typhon in 1,5 m (über 130 dB) bleibt unter Vollaussteuerung (Spitze %.3f)", peak));
        Soundscape agcQ = Soundscape.beaufort(1), agcS = Soundscape.beaufort(11);
        agcQ.earY = agcS.earY = 24;
        agcQ.render(20);
        agcS.render(20);
        check(agcQ.autoGainDb() > 10 && agcS.autoGainDb() < -3,
                f("Ohr passt sich an: stille See %+.0f dB, Orkan %+.0f dB", agcQ.autoGainDb(), agcS.autoGainDb()));

        // ------------------------------------------------ seed, Stufen, Rechenzeit
        Soundscape s1 = Soundscape.beaufort(7), s2 = Soundscape.beaufort(7), s3 = Soundscape.beaufort(7);
        s3.seed = 2;
        float[] o1 = s1.render(1), o2 = s2.render(1), o3 = s3.render(1);
        check(java.util.Arrays.equals(o1, o2) && !java.util.Arrays.equals(o1, o3), "gleicher seed, gleicher Ton; anderer seed, anderer");
        Soundscape big = Soundscape.beaufort(10);
        big.bodies.add(new Aeolian("Handlauf", 3, 24, 0, 0.07, 27));
        big.bodies.add(new Aeolian("Stäbe", 3, 23.5, 0, 0.025, 60));
        big.bodies.add(new Aeolian("Seil", 0, 26, 0, 0.008, 15));
        big.surfRadius = 5;
        big.earX = 4;
        big.earY = 24;
        big.glassDrops = 60;
        big.roofDrops = 200;
        big.render(1);
        long tn0 = System.nanoTime();
        big.render(10);
        double ms = (System.nanoTime() - tn0) / 1e6 / 10;
        boolean q = Soundscape.Quality.LOW.sampleRate == 22_050 && Soundscape.Quality.ULTRA.sampleRate == 48_000 && !Soundscape.Quality.LOW.reverb;
        check(q && ms < 120, f("Stufen von 22 bis 48 kHz; Sturm mit drei Äolskörpern, Brandung, Regen auf Glas und Dach: %.0f ms Rechnen je s Ton", ms));

        glacier(sr);
        wadden(sr);
        brocken(sr);
        coast(sr);
        bucht(sr);
        // seit Brocken Phase 5: Wind von außen – am Ohr auf dem Gipfel laut, im Lee leise
        double[] lf = new double[2];
        for (int k = 0; k < 2; k++) {
            Soundscape s = Soundscape.beaufort(2);
            s.autoGain = false;
            s.waveHeight = 0;
            s.rainRate = 0;
            s.earX = k == 0 ? -50 : 50;
            s.earY = 10;
            s.wind = (x, y, z) -> new double[]{x < 0 ? 18 : 3, 0, 0};
            s.render(1);
            lf[k] = db(Analysis.rms(s.render(3), sr, 0, 3));
        }
        check(lf[0] > lf[1] + 15, f("Wind von außen: am Ohr im Sturm %.0f dB, im Lee %.0f dB (Bft 2 vorgegeben)", lf[0], lf[1]));
        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Prüfungen fehlgeschlagen.");
        return lines;
    }

    /** Lauteste 100 ms (dB). */
    static double max100(float[] o, int sr) {
        int w = sr / 10;
        double best = 0, acc = 0;
        for (int i = 0; i < o.length / 2; i++) {
            acc += 0.5 * (o[2 * i] * o[2 * i] + o[2 * i + 1] * o[2 * i + 1]);
            if (i >= w) { int j = i - w; acc -= 0.5 * (o[2 * j] * o[2 * j] + o[2 * j + 1] * o[2 * j + 1]); }
            best = Math.max(best, acc / w);
        }
        return db(Math.sqrt(best));
    }

    /** Einsätze (Zeiten, s), an denen der Pegel über 50 ms die Schwelle von unten kreuzt. */
    static List<Double> onsets(float[] o, int sr, double thrDb) {
        List<Double> l = new ArrayList<>();
        int w = (int) (0.01 * sr);
        boolean on = false;
        double thr = Dsp.amp(thrDb), off = Dsp.amp(thrDb - 6);
        for (int i = 0; 2 * (i + w) < o.length; i += w) {
            double e = 0;
            for (int k = 0; k < w; k++) { double v = 0.5 * (o[2 * (i + k)] + o[2 * (i + k) + 1]); e += v * v; }
            double r = Math.sqrt(e / w);
            if (!on && r > thr) { on = true; l.add(i / (double) sr); }
            else if (on && r < off) on = false;
        }
        return l;
    }

    /** Die Siedlung (seit Haithabu Phase 14): Rufe, Gewerke, Ruder, Feuer, Menge, Schilf, Pfähle, Langhaus, Dämpfung über Land. */
    private void siedlung(int sr) {
        // Pegel aller Rufe und Klänge in 10 m: lauteste 100 ms = Pegel in 1 m − 20 dB − 3 dB (ein Ohr hört die Hälfte)
        double worst = 0;
        String worstK = "";
        int kinds = 0;
        for (Soundscape.Kind k : Soundscape.Kind.values()) {
            if (!k.animal() && !k.craft()) continue;
            Soundscape s = quiet();
            s.render(0.05);
            s.event(k, 0, 2, -10, 1, k == Soundscape.Kind.RUDER ? 10 : 0.3);
            float[] o = s.render(5);
            double want = expected(k);
            double dev = max100(o, sr) - (want - 23);
            if (Math.abs(dev) > Math.abs(worst)) { worst = dev; worstK = k.name(); }
            kinds++;
        }
        check(kinds == 19 && Math.abs(worst) < 2.5, f("19 Rufe und Klänge der Siedlung in 10 m: lauteste 100 ms wie angegeben (größte Abweichung %+.1f dB, %s)", worst, worstK));
        // Ziege meckert (Zittern 7 bis 10 Hz), Hahn kräht gut 2 s, Hund bellt kurz, Schwalbe hell, Schwein tief
        Soundscape g = quiet();
        g.seed = 5;
        g.event(Soundscape.Kind.ZIEGE, 0, 2, -5, 1);
        float[] go = g.render(2);
        double gon = Analysis.onset(go, sr, 0, Dsp.amp(50));
        int en = 4096;
        double[] env = new double[en];
        int step = sr / 200;
        for (int i = 0; i < en && 2 * ((int) (gon * sr) + i * step + step) < go.length && i < 120; i++) {
            double e = 0;
            for (int k = 0; k < step; k++) { int j = (int) (gon * sr) + i * step + k; double v = go[2 * j]; e += v * v; }
            env[i] = Math.sqrt(e / step);
        }
        double mean = 0;
        for (int i = 0; i < 120; i++) mean += env[i] / 120;
        for (int i = 0; i < 120; i++) env[i] = i < 120 ? env[i] - mean : 0;
        for (int i = 120; i < en; i++) env[i] = 0;
        double trem = Analysis.peak(Analysis.spectrum(env, en), 200, en, 4, 16);
        Soundscape hh = quiet();
        hh.event(Soundscape.Kind.HAHN, 0, 2, -5, 1);
        float[] ho = hh.render(3.5);
        double hon = Analysis.onset(ho, sr, 0, Dsp.amp(50)), hoff = offset(ho, sr, 1.0, Dsp.amp(50));
        double[] cen = new double[3];
        Soundscape.Kind[] ck = {Soundscape.Kind.SCHWALBE, Soundscape.Kind.SCHWEIN, Soundscape.Kind.HUND};
        for (int i = 0; i < 3; i++) {
            Soundscape c = quiet();
            c.event(ck[i], 0, 2, -5, 1);
            float[] co = c.render(3);
            double on = Analysis.onset(co, sr, 0, Dsp.amp(45));
            cen[i] = Analysis.centroid(Analysis.spectrum(Analysis.mono(co, sr, on, 0.12), 4096), sr, 4096);
        }
        check(trem > 6 && trem < 11 && hoff - hon > 1.6 && hoff - hon < 2.8 && cen[0] > 3500 && cen[1] < 1200 && cen[2] > 600 && cen[2] < 2500,
                f("Ziege meckert mit %.1f Hz, Hahn kräht %.1f s; Schwerpunkt Schwalbe %.1f kHz, Schwein %.0f Hz, Hund %.1f kHz", trem, hoff - hon, cen[0] / 1000, cen[1], cen[2] / 1000));

        // Werkstätten: der Schmied schlägt in Reihen mit Pausen; aus = still
        Soundscape w = quiet();
        Workshop smith = new Workshop("Schmiede", Workshop.Craft.SCHMIED, 0, 1, -20);
        Workshop saw = new Workshop("Säger", Workshop.Craft.SAEGE, 30, 1, 0);
        w.workshops.add(smith);
        w.workshops.add(saw);
        float[] wo = w.render(60);
        long sm = smith.strikes(), sw = saw.strikes();
        smith.active = false;
        saw.active = false;
        w.render(20);
        long sm2 = smith.strikes() - sm;
        List<Double> on = onsets(wo, sr, 55);
        double minGap = 9, pause = 0;
        for (int i = 1; i < on.size(); i++) { double d = on.get(i) - on.get(i - 1); minGap = Math.min(minGap, d); pause = Math.max(pause, d); }
        check(sm > 20 && sm < 90 && sw > 30 && sw < 110 && sm2 == 0 && minGap > 0.15 && pause > 3,
                f("Schmied: %d Schläge in 60 s (Reihen, längste Pause %.0f s), Säger %d Striche; abgestellt still (%d)", sm, pause, sw, sm2));

        // Ruderschlag: zwölf Blätter gut 3 dB lauter als sechs
        double[] oar = new double[2];
        for (int i = 0; i < 2; i++) {
            double sum = 0;
            for (int k = 0; k < 6; k++) {
                Soundscape r = quiet();
                r.seed = 40 + k;
                r.event(Soundscape.Kind.RUDER, 0, 1, -10, 1, i == 0 ? 6 : 12);
                sum += max100(r.render(1.5), sr);
            }
            oar[i] = sum / 6;
        }
        check(Math.abs(oar[1] - oar[0] - 3.0) < 1.2, f("Ruderschlag: 12 Blätter %.1f dB, 6 Blätter %.1f dB in 10 m (+3 dB je Verdopplung)", oar[1], oar[0]));

        // Feuer: Herd, Esse, brennendes Haus; ein Feuer im Raum hört man draußen nicht
        double[] bl = new double[3];
        double[] sizes = {0.25, 0.4, 1};
        for (int i = 0; i < 3; i++) {
            Soundscape b = quiet();
            b.blazes.add(new Blaze("Feuer", 0, 2, -10, sizes[i], false));
            b.render(1);
            bl[i] = db(Analysis.rms(b.render(6), sr, 0, 6));
        }
        Soundscape bi = quiet();
        bi.blazes.add(new Blaze("Herd", 0, 1, -3, 0.25, true));
        bi.render(1);
        double outside = db(Analysis.rms(bi.render(3), sr, 0, 3));
        bi.inside = 1;
        bi.room = Room.LONGHOUSE;
        bi.render(1);
        double inside = db(Analysis.rms(bi.render(3), sr, 0, 3));
        check(bl[0] < bl[1] && bl[1] < bl[2] && bl[2] > 64 && bl[2] < 86 && outside < 25 && inside > 40,
                f("Feuer in 10 m: Herd %.0f dB, Esse %.0f dB, brennendes Haus %.0f dB; der Herd im Haus in 3 m: drinnen %.0f dB, draußen nichts", bl[0], bl[1], bl[2], inside));

        // Menge: zehnmal so viele Leute, 10 dB mehr
        double[] cr = new double[2];
        double[] ppl = {12, 120};
        for (int i = 0; i < 2; i++) {
            Soundscape m = quiet();
            m.crowds.add(new Crowd("Markt", 0, 2, -15, ppl[i]));
            m.render(2);
            cr[i] = db(Analysis.rms(m.render(10), sr, 0, 10));
        }
        double crWant = 60 + 10 * Math.log10(0.35 * 120) - 20 * Math.log10(15);
        check(Math.abs(cr[1] - cr[0] - 10) < 2 && Math.abs(cr[1] - crWant) < 2.5,
                f("Stimmengewirr in 15 m: 120 Leute %.1f dB (gerechnet %.1f), 12 Leute %.1f dB", cr[1], crWant, cr[0]));

        // Schilf im Wind: Rauschen im Band 2 bis 8 kHz, mehr mit dem Wind
        double[] rs = new double[3];
        double[][] cfg = {{6, 0}, {6, 1}, {11, 1}};
        for (int i = 0; i < 3; i++) {
            Soundscape r = quiet();
            r.windSpeed = cfg[i][0];
            r.gustiness = 0.2;
            r.reedNear = cfg[i][1];
            r.render(1);
            float[] ro = r.render(6);
            double[] p = Analysis.spectrum(Analysis.mono(ro, sr, 0, 6), 4096);
            rs[i] = 10 * Math.log10(band(p, sr, 4096, 2000, 8000));
        }
        check(rs[1] - rs[0] > 10 && rs[2] > rs[1] + 6, f("Schilf im Wind: Band 2 bis 8 kHz %+.0f dB gegen ohne Schilf (6 m/s), bei 11 m/s noch %+.0f dB", rs[1] - rs[0], rs[2] - rs[1]));

        // Pfähle: Glucksen nach Wellenhöhe; glattes Wasser still
        long[] cl = new long[2];
        double[] hs = {0.0, 0.3};
        for (int i = 0; i < 2; i++) {
            Soundscape pl = quiet();
            pl.waveHeight = hs[i];
            pl.wavePeriod = 2.5;
            for (int k = 0; k < 6; k++) pl.pilings.add(new double[]{-5 + 2 * k, -4});
            float[] po = pl.render(20);
            cl[i] = onsets(po, sr, 38).size();
        }
        check(cl[0] == 0 && cl[1] > 20, f("Pfähle: bei 30 cm Wellen %d Glucksen in 20 s, bei glattem Wasser %d", cl[1], cl[0]));

        // Langhaus und Dämpfung über Land
        Soundscape lh = quiet();
        lh.event(Soundscape.Kind.HUND, 0, 1, -20, 1);
        double lout = max100(lh.render(2), sr);
        Soundscape lh2 = quiet();
        lh2.inside = 1;
        lh2.room = Room.LONGHOUSE;
        lh2.event(Soundscape.Kind.HUND, 0, 1, -20, 1);
        double lin = max100(lh2.render(2), sr);
        Soundscape ob = quiet(), ob2 = quiet();
        ob2.obstruction = 4;
        ob.event(Soundscape.Kind.KRAEHE, 0, 2, -210, 1);
        ob2.event(Soundscape.Kind.KRAEHE, 0, 2, -210, 1);
        double o0 = max100(ob.render(3), sr), o4 = max100(ob2.render(3), sr);
        check(lout - lin > 9 && lout - lin < 20 && Math.abs(o0 - o4 - 8) < 1.5,
                f("Langhaus: ein Hund in 20 m draußen klingt drinnen %.0f dB leiser und dumpf; über Land 4 dB je 100 m: eine Krähe in 210 m %.1f dB leiser", lout - lin, o0 - o4));

        // Rechenzeit einer Siedlung: sechs Werkstätten, zwei Mengen, sechs Feuer, 60 Pfähle, Schilf und Reet
        Soundscape big = Soundscape.beaufort(4);
        big.quality = Soundscape.Quality.MEDIUM;
        big.obstruction = 4;
        big.reedNear = 0.5;
        big.thatchNear = 0.5;
        for (int k = 0; k < 6; k++) big.workshops.add(new Workshop("W" + k, Workshop.Craft.values()[k % 5], 30 * k - 80, 1, 40 - 15 * k));
        big.crowds.add(new Crowd("Markt", 80, 1.5, -40, 150));
        big.crowds.add(new Crowd("Hafen", -40, 1.5, 60, 40));
        for (int k = 0; k < 6; k++) big.blazes.add(new Blaze("F" + k, 5 * k, 0.5, -8, 0.3, k == 0));
        for (int k = 0; k < 60; k++) big.pilings.add(new double[]{-30 + k, 12});
        big.render(2);
        long t0 = System.nanoTime();
        big.render(10);
        double ms = (System.nanoTime() - t0) / 1e6 / 10;
        check(ms < 90, f("Siedlung mit 6 Werkstätten, 2 Mengen, 6 Feuern, 60 Pfählen, Schilf und Reet (32 kHz): %.0f ms Rechnen je s Ton", ms));
    }

    /** Pegel in 1 m je Art bei voller Stärke (wie in Calls und Crafts angegeben; Huhn und Ziege rufen dann warnend). */
    static double expected(Soundscape.Kind k) {
        return switch (k) {
            case KRAEHE -> 90; case ENTE -> 85; case ERPEL -> 72; case SCHWALBE -> 75; case KORMORAN -> 78; case SCHWAN -> 84;
            case HUHN -> 88; case HAHN -> 100; case ZIEGE -> 92; case SCHWEIN -> 78; case QUIEKEN -> 104; case HUND -> 100;
            case HAMMER -> 112; case KLOPFEN -> 92; case BEIL -> 100; case SAEGE -> 84; case FEINSAEGE -> 70;
            case RUDER -> 86; case EINSTURZ -> 116;
            default -> 0;
        };
    }

    public int failures() { return bad; }

    /** Anteil der Energie zwischen f0 und f1 an der Energie von 20 Hz bis top. */
    static double share(double[] p, int sr, int n, double f0, double f1, double top) {
        double a = 0, all = 0;
        for (int k = (int) (20.0 * n / sr); k <= (int) (top * n / sr) && k < p.length; k++) { all += p[k]; if (k >= (int) (f0 * n / sr) && k <= (int) (f1 * n / sr)) a += p[k]; }
        return a / Math.max(1e-30, all);
    }

    static double band(double[] p, int sr, int n, double f0, double f1) {
        double s = 0;
        int c = 0;
        for (int k = (int) (f0 * n / sr); k <= (int) (f1 * n / sr) && k < p.length; k++) { s += p[k]; c++; }
        return s / Math.max(1, c);
    }

    /** T60 aus der Energieabklingkurve (Schroeder): Steigung zwischen −5 und −25 dB, auf 60 dB hochgerechnet. */
    static double decay60(float[] h, int sr) {
        double[] e = new double[h.length];
        double acc = 0;
        for (int i = h.length - 1; i >= 0; i--) { acc += h[i] * h[i]; e[i] = acc; }
        double t5 = Double.NaN, t25 = Double.NaN;
        for (int i = 0; i < h.length; i++) {
            double d = 10 * Math.log10(e[i] / e[0]);
            if (Double.isNaN(t5) && d < -5) t5 = i / (double) sr;
            if (Double.isNaN(t25) && d < -25) { t25 = i / (double) sr; break; }
        }
        return (t25 - t5) * 3;
    }

    /** Erster Zeitpunkt ab t0, an dem der Pegel über 50 ms unter die Schwelle fällt. */
    static double offset(float[] st, int sr, double t0, double threshold) {
        int w = (int) (0.05 * sr);
        for (int i = (int) (t0 * sr); 2 * (i + w) < st.length; i += w / 5) {
            double s = 0;
            for (int k = 0; k < w; k++) { double v = 0.5 * (st[2 * (i + k)] + st[2 * (i + k) + 1]); s += v * v; }
            if (Math.sqrt(s / w) < threshold) return i / (double) sr;
        }
        return st.length / 2.0 / sr;
    }

    /** Verschiebung (Werte), um die der linke Kanal dem rechten nachläuft (Kreuzkorrelation). */
    static int lag(float[] st, int sr, double t0, double d, int max) {
        int a = (int) (t0 * sr), n = (int) (d * sr), best = 0;
        double bv = -1e30;
        for (int l = -max; l <= max; l++) {
            double s = 0;
            for (int i = 0; i < n; i++) {
                int j = a + i + l;
                if (j < 0 || 2 * j + 1 >= st.length) continue;
                s += st[2 * (a + i) + 1] * st[2 * j];
            }
            if (s > bv) { bv = s; best = l; }
        }
        return best;
    }

    /** Zählt kurze Ticks (Anstieg über das Fünffache des laufenden Mittels). */
    static int countClicks(float[] st, int sr) {
        int n = st.length / 2, w = (int) (0.0025 * sr), count = 0;
        double avg = 1e-9;
        int dead = 0;
        for (int i = 0; i + w < n; i += w) {
            double s = 0;
            for (int k = 0; k < w; k++) { double v = st[2 * (i + k)] + st[2 * (i + k) + 1]; s += v * v; }
            s = Math.sqrt(s / w);
            if (dead > 0) dead--;
            else if (s > 4 * avg && s > 1e-5) { count++; dead = 2; }
            avg += (s - avg) * 0.05;
        }
        return count;
    }

    /** Seit Glacier Phase 12: die Klänge des Eises und der Donner der Kalbung. */
    private void glacier(int sr) {
            Soundscape.Kind[] ks = {Soundscape.Kind.KNACKEN, Soundscape.Kind.AECHZEN, Soundscape.Kind.AUFSCHLAG, Soundscape.Kind.AUFLAUFEN, Soundscape.Kind.KONDOR};
            double[] tone = {0, 0, 20_000, 0, 0}, str = {1, 1, 1, 0.5, 1};
            double[] cen = new double[ks.length], lev = new double[ks.length], low = new double[ks.length];
            for (int k = 0; k < ks.length; k++) {
                Soundscape q = quiet();
                q.event(ks[k], 0, 2, -100, str[k], tone[k]);
                float[] o = q.render(4.3);
                double on = Analysis.onset(o, sr, 0, Dsp.amp(20));
                double[] sp = Analysis.spectrum(Analysis.mono(o, sr, Math.max(0, on), 1.5), 8192);
                cen[k] = Analysis.centroid(sp, sr, 8192);
                double lo = 0, all = 0;
                for (int b = 1; b < sp.length; b++) { all += sp[b]; if (b * (double) sr / 8192 < 100) lo += sp[b]; }
                low[k] = lo / Math.max(1e-30, all);
                double best = 0;
                for (double t = Math.max(0, on); t < 4.1; t += 0.02) best = Math.max(best, Analysis.rms(o, sr, t, 0.1));
                lev[k] = db(best) + 40;   // in 100 m gemessen (unter dem Begrenzer), auf 1 m gerechnet
            }
            check(cen[0] > 600 && lev[0] > 116 && lev[0] < 132,
                    f("Knacken: hell (Schwerpunkt %.0f Hz), stark %.0f dB in 1 m (wie ein Schuss)", cen[0], lev[0]));
            check(cen[1] < 350 && lev[1] > 98 && lev[1] < 114, f("Ächzen: tief (Schwerpunkt %.0f Hz), %.0f dB in 1 m", cen[1], lev[1]));
            check(low[2] > 0.25 && lev[2] > 120 && lev[2] < 138, f("Aufschlag von 20 000 m³: %.0f %% der Energie unter 100 Hz, %.0f dB in 1 m", 100 * low[2], lev[2]));
            check(cen[3] > 400 && cen[3] < 2500, f("Auflaufen der Welle: Rauschen mit Schwerpunkt %.0f Hz, %.0f dB in 1 m", cen[3], lev[3]));
            check(cen[4] > 600 && lev[4] < 76, f("Kondor: Luft in den Schwingen (Schwerpunkt %.0f Hz), leise (%.0f dB in 1 m)", cen[4], lev[4]));
            // der Donner einer Kalbung: aus dem Riss, nach der Laufzeit, rollt länger, je länger der Riss
            Soundscape q = quiet();
            double[][] path = new double[20][];
            for (int i = 0; i < 20; i++) path[i] = new double[]{0, 4 * i, -400 - 6 * i};
            q.calving(path, 30_000);
            float[] o = q.render(5);
            double cc = q.soundSpeed(), on = Analysis.onset(o, sr, 0, Dsp.amp(40));
            check(Math.abs(on - 400 / cc) < 0.08, f("Donner der Kalbung aus 400 m: kommt nach %.2f s (Laufzeit %.2f s), %.0f dB am Ohr", on, 400 / cc, db(Analysis.rms(o, sr, on, 0.5))));
            }

    /** Seit Rungholt Phase 14: das Watt (Vögel, Seehunde, Priel, Knistern), Vieh und Glocken, der Wind, der den Schall trägt. */
    private void wadden(int sr) {
        Soundscape.Kind[] ks = {Soundscape.Kind.AUSTERNFISCHER, Soundscape.Kind.ALPENSTRANDLAEUFER, Soundscape.Kind.RINGELGANS, Soundscape.Kind.SEEHUND,
                Soundscape.Kind.HEULER, Soundscape.Kind.RIND, Soundscape.Kind.SCHAF, Soundscape.Kind.GLOCKE, Soundscape.Kind.GLOCKE_WATT};
        double[] tone = {0.5, 6, 8, 0.5, 0.5, 0.5, 0.5, 0, 0};
        double[] cen = new double[ks.length], lev = new double[ks.length], tail = new double[ks.length];
        double[][] spec = new double[ks.length][];
        for (int k = 0; k < ks.length; k++) {
            Soundscape q = quiet();
            q.event(ks[k], 0, 2, -10, 1, tone[k]);
            float[] o = q.render(9);
            double on = Analysis.onset(o, sr, 0, Dsp.amp(10));
            if (Double.isNaN(on)) on = 0;
            spec[k] = Analysis.spectrum(Analysis.mono(o, sr, on, 1.5), 16384);
            cen[k] = Analysis.centroid(spec[k], sr, 16384);
            double best = 0;
            for (double t = on; t < 8.8; t += 0.02) best = Math.max(best, Analysis.rms(o, sr, t, 0.1));
            lev[k] = db(best) + 20;   // in 10 m gemessen, auf 1 m gerechnet
            tail[k] = db(Analysis.rms(o, sr, on + 3, 1)) - db(best);
        }
        check(cen[0] > 1800 && lev[0] > 90 && lev[0] < 106, f("Austernfischer: hell (Schwerpunkt %.0f Hz), laut %.0f dB in 1 m", cen[0], lev[0]));
        check(cen[1] > 2500 && lev[1] < lev[0], f("Alpenstrandläufer im Trupp: dünn und hoch (%.0f Hz), %.0f dB in 1 m", cen[1], lev[1]));
        check(cen[2] < cen[1] && lev[2] > 80, f("Ringelgänse murmeln tiefer (%.0f Hz), %.0f dB in 1 m", cen[2], lev[2]));
        check(cen[3] < 1500 && cen[4] > cen[3], f("Seehund tief (%.0f Hz), der Heuler höher (%.0f Hz)", cen[3], cen[4]));
        check(cen[5] < 800 && lev[5] > lev[6], f("Rind tief und laut (%.0f Hz, %.0f dB), Schaf leiser (%.0f dB)", cen[5], lev[5], lev[6]));
        double prime = Analysis.peak(spec[7], sr, 16384, 280, 345);
        check(Math.abs(prime - 311) < 6 && lev[7] > 110 && tail[7] > -40, f("Glocke: Prime %.0f Hz, %.0f dB in 1 m, nach 3 s noch %.0f dB unter der Spitze", prime, lev[7], -tail[7]));
        check(cen[8] < cen[7] * 0.85, f("Glocken aus dem Watt: dumpf wie unter Wasser (Schwerpunkt %.0f Hz gegen %.0f Hz)", cen[8], cen[7]));
        // der Priel: lauter und heller mit der Strömung
        double[] pl = new double[2], pc = new double[2];
        double[] v = {0.25, 1.0};
        for (int k = 0; k < 2; k++) {
            Soundscape q = quiet();
            com.dan.soundscape.Stream st = new com.dan.soundscape.Stream("Priel");
            st.x = 0; st.y = 0; st.z = -8; st.speed = v[k]; st.width = 30;
            q.streams.add(st);
            q.render(1);
            float[] o = q.render(4);
            pl[k] = db(Analysis.rms(o, sr, 0, 4));
            pc[k] = Analysis.centroid(Analysis.spectrum(Analysis.mono(o, sr, 0, 4), 8192), sr, 8192);
        }
        check(pl[1] - pl[0] > 10 && pc[1] > pc[0] && Math.abs(com.dan.soundscape.Stream.levelDb(1, 30) - 61) < 1,
                f("Priel: bei 1 m/s %.0f dB lauter als bei 0,25 m/s, heller (%.0f gegen %.0f Hz); 30 m breit bei 1 m/s %.0f dB in 1 m", pl[1] - pl[0], pc[1], pc[0], com.dan.soundscape.Stream.levelDb(1, 30)));
        // Knistern des trockenfallenden Watts: kurze helle Knackse
        Soundscape q0 = quiet(), q1 = quiet();
        q1.mudCrackle = 1;
        q0.render(1); q1.render(1);
        float[] o0 = q0.render(4), o1 = q1.render(4);
        int clicks = countClicks(o1, sr);
        double hi = band(Analysis.spectrum(Analysis.mono(o1, sr, 0, 4), 8192), sr, 8192, 2000, 8000);
        check(clicks > 25 && db(Analysis.rms(o1, sr, 0, 4)) > db(Analysis.rms(o0, sr, 0, 4)) + 10 && hi > 0,
                f("Knistern im Watt: %d Knackse in 4 s, %.0f dB am Ohr (ohne: %.0f dB)", clicks, db(Analysis.rms(o1, sr, 0, 4)), db(Analysis.rms(o0, sr, 0, 4))));
        // der Wind trägt den Schall: mit dem Wind lauter, gegen den Wind im Schallschatten
        Soundscape c = quiet();
        c.windCarry = true;
        c.windSpeed = 20;
        c.windFromDeg = 270;
        double with = c.carryDb(-2000, 0, 2000), against = c.carryDb(2000, 0, 2000), near = c.carryDb(-30, 0, 30);
        c.windCarry = false;
        double off = c.carryDb(-2000, 0, 2000);
        check(with > 2 && with <= 11 && against < -14 && near == 0 && off == 0,
                f("Wind aus West 20 m/s, Quelle 2 km entfernt: mit dem Wind %+.0f dB, gegen den Wind %+.0f dB, in 30 m und ohne Windtragen 0", with, against));
    }

    /** Seit Sylt Phase 14: die Sandküste (Brecher, Schwall, Sanderling, Abbruch), ferne Brandung, Flugsand, Strandhafer. */
    private void coast(int sr) {
        lines.add("Küste (seit Sylt Phase 14)");
        Soundscape.Kind[] ks = {Soundscape.Kind.SCHWALLBRECHER, Soundscape.Kind.STURZBRECHER, Soundscape.Kind.SCHWALL, Soundscape.Kind.SANDERLING,
                Soundscape.Kind.ABBRUCH, Soundscape.Kind.ERDBROCKEN, Soundscape.Kind.RIESELN};
        double[] tone = {1, 1, 0.4, 1, 100, 0.1, 3};
        double[] want = {102, 112, 78 + 14 * Math.log10(2), 82, 112, 92, 70};
        double worst = 0;
        String worstK = "";
        double[] cen = new double[ks.length], low = new double[ks.length], late = new double[ks.length];
        for (int k = 0; k < ks.length; k++) {
            Soundscape q = quiet();
            q.render(0.05);
            q.event(ks[k], 0, 2, -10, 1, tone[k]);
            float[] o = q.render(k == 4 ? 14 : 7);
            double dev = max100(o, sr) - (want[k] - 23);
            if (Math.abs(dev) > Math.abs(worst)) { worst = dev; worstK = ks[k].name(); }
            double on = Analysis.onset(o, sr, 0, Dsp.amp(10));
            if (Double.isNaN(on)) on = 0;
            double[] sp = Analysis.spectrum(Analysis.mono(o, sr, on, k == 3 ? 0.6 : 2), 16384);
            cen[k] = Analysis.centroid(sp, sr, 16384);
            low[k] = share(sp, sr, 16384, 20, 150, 12000);
            double[] lt = Analysis.spectrum(Analysis.mono(o, sr, on + (k == 2 ? 2.4 : 2.5), 1), 16384);
            late[k] = Analysis.centroid(lt, sr, 16384);
        }
        int co = 0;
        for (Soundscape.Kind k : Soundscape.Kind.values()) if (k.coast()) co++;
        check(co == 7 && Math.abs(worst) < 2.5, f("7 Klänge der Küste in 10 m: lauteste 100 ms wie angegeben (größte Abweichung %+.1f dB, %s)", worst, worstK));
        check(low[1] > low[0] * 1.5 && cen[1] < cen[0], f("Sturzbrecher dumpfer als Schwallbrecher: Anteil unter 150 Hz %.0f %% gegen %.0f %%, Schwerpunkt %.0f gegen %.0f Hz",
                low[1] * 100, low[0] * 100, cen[1], cen[0]));
        check(late[2] > 2500, f("Rücklauf über Sand zischt hell (Schwerpunkt nach 2,4 s %.0f Hz)", late[2]));
        check(cen[3] > 3000 && cen[3] < 6000, f("Sanderling: kurzes „plit“ um %.0f Hz", cen[3]));
        check(low[4] > 0.15 && low[5] > 0.1 && cen[6] > 2500, f("Abbruch grollt (%.0f %% unter 150 Hz), Erdbrocken dumpf (%.0f %%), Rieseln hell (%.0f Hz)", low[4] * 100, low[5] * 100, cen[6]));
        // ferne Brandung: eine gerade Brandungslinie 30 m vor dem Ohr (H 1 m: 4,5 kW je m vergehen) gibt P'/(2d)
        double pw = Soundscape.surfAcousticPower(4500);
        double[][] line = new double[201][];
        for (int i = 0; i < line.length; i++) line[i] = new double[]{-2500 + 25 * i, -30, pw, 0.3, 8, 25};
        Soundscape a = quiet();
        a.earY = 1.7;
        a.waterLevel = 0;
        a.surfLine = line;
        a.render(2);
        float[] ao = a.render(24);
        double la = db(Analysis.rms(ao, sr, 0, 24)), want30 = 10 * Math.log10(pw / (2 * 30) / 1e-12);
        double ca = Analysis.centroid(Analysis.spectrum(Analysis.mono(ao, sr, 0, 24), 8192), sr, 8192);
        double mn = 1e9, mx = -1e9;
        for (double t = 0; t < 23.5; t += 0.5) { double v = db(Analysis.rms(ao, sr, t, 0.5)); mn = Math.min(mn, v); mx = Math.max(mx, v); }
        check(Math.abs(la - want30) < 2.5 && Math.abs(a.surfLineDb() - want30) < 3.5, f("ferne Brandung: Linie mit H 1 m in 30 m %.1f dB am Ohr (Linienquelle P'/2d: %.1f dB, Anzeige %.1f dB)", la, want30, a.surfLineDb()));
        check(mx - mn > 5, f("nah atmet sie im Takt der Wellen: %.0f dB zwischen leisester und lautester halber Sekunde", mx - mn));
        // fern: leiser nach der Linie (−10 dB je Zehnfaches) und dumpfer
        Soundscape b = quiet();
        b.earY = 1.7;
        b.surfLine = line;
        b.earZ = 970;   // 1 km von der Linie
        b.render(2);
        float[] bo = b.render(12);
        double lb = db(Analysis.rms(bo, sr, 0, 12)), cb = Analysis.centroid(Analysis.spectrum(Analysis.mono(bo, sr, 0, 12), 8192), sr, 8192);
        check(la - lb > 11 && la - lb < 22 && cb < ca * 0.8, f("in 1 km %.0f dB leiser und dumpfer (Schwerpunkt %.0f gegen %.0f Hz): man hört das Rollen", la - lb, cb, ca));
        // Sturzbrecher rollen tiefer als Schwallbrecher
        double[][] pl = new double[line.length][];
        for (int i = 0; i < line.length; i++) { pl[i] = line[i].clone(); pl[i][3] = 1; }
        Soundscape c = quiet();
        c.surfLine = pl;
        c.render(1);
        float[] cpo = c.render(12);
        double[] cs = Analysis.spectrum(Analysis.mono(cpo, sr, 0, 12), 8192), as = Analysis.spectrum(Analysis.mono(ao, sr, 0, 12), 8192);
        double rp = share(cs, sr, 8192, 20, 160, 12000), rs = share(as, sr, 8192, 20, 160, 12000);
        check(rp > rs * 1.3, f("Sturzbrecher: Anteil unter 160 Hz %.0f %% (Schwallbrecher %.0f %%)", rp * 100, rs * 100));
        // Richtung: die Linie links (Westen, Blick nach Norden) – das linke Ohr hört mehr
        Soundscape d = quiet();
        double[][] wl = new double[81][];
        for (int i = 0; i < wl.length; i++) wl[i] = new double[]{-40, -1000 + 25 * i, pw, 0.3, 8, 25};
        d.surfLine = wl;
        d.render(1);
        float[] dO = d.render(6);
        double left = db(Analysis.rms(dO, sr, 0, 6, 0)), right = db(Analysis.rms(dO, sr, 0, 6, 1));
        check(left > right + 3, f("Brandung im Westen, Blick nach Norden: links %.0f dB, rechts %.0f dB", left, right));
        // Flugsand und Strandhafer
        Soundscape s0 = quiet(), s1 = quiet();
        s1.sandFlux = 0.05;
        s0.render(1); s1.render(1);
        float[] so0 = s0.render(4), so1 = s1.render(4);
        double ls1 = db(Analysis.rms(so1, sr, 0, 4)), cs1 = Analysis.centroid(Analysis.spectrum(Analysis.mono(so1, sr, 0, 4), 8192), sr, 8192);
        check(Math.abs(ls1 - 60) < 4 && cs1 > 3000 && db(Analysis.rms(so0, sr, 0, 4)) < 20, f("Flugsand 0,05 kg/(m·s) am Ohr: %.0f dB, hell (%.0f Hz); ohne Sand still", ls1, cs1));
        Soundscape g0 = quiet(), g1 = quiet();
        g0.windSpeed = g1.windSpeed = 12;
        g1.marramNear = 1;
        g0.render(1); g1.render(1);
        float[] go0 = g0.render(4), go1 = g1.render(4);
        double[] gs0 = Analysis.spectrum(Analysis.mono(go0, sr, 0, 4), 8192), gs1 = Analysis.spectrum(Analysis.mono(go1, sr, 0, 4), 8192);
        double hiG = 10 * Math.log10(band(gs1, sr, 8192, 3000, 8000) / band(gs0, sr, 8192, 3000, 8000));
        check(hiG > 10, f("Strandhafer im Wind (12 m/s): über 3 kHz %.0f dB mehr als ohne", hiG));
        // Rechenzeit: 200 Stücke, Sand und Halme (32 kHz)
        Soundscape r = Soundscape.beaufort(6);
        r.quality = Soundscape.Quality.MEDIUM;
        r.surfLine = line;
        r.sandFlux = 0.1;
        r.marramNear = 0.5;
        r.render(1);
        long t0 = System.nanoTime();
        r.render(5);
        double ms = (System.nanoTime() - t0) / 1e6 / 5;
        check(ms < 40, f("Küste mit 200 Stücken Brandung, Sand und Halmen (32 kHz, Bft 6): %.0f ms Rechnen je s Ton", ms));
    }

    /** Seit Brocken Phase 15: Pfeife, Bergvögel, Eis, Dampflok, Kanten im Sturm, Walpurgisnacht. */
    private void brocken(int sr) {
        // Pegel in 10 m wie angegeben (lauteste 100 ms = Pegel in 1 m − 23 dB)
        Soundscape.Kind[] ks = {Soundscape.Kind.PFEIFE, Soundscape.Kind.KOLKRABE, Soundscape.Kind.RINGDROSSEL, Soundscape.Kind.WIESENPIEPER,
                Soundscape.Kind.HAUSROTSCHWANZ, Soundscape.Kind.BUCHFINK, Soundscape.Kind.KREUZSCHNABEL, Soundscape.Kind.WINTERGOLDHAEHNCHEN,
                Soundscape.Kind.RAUFUSSKAUZ, Soundscape.Kind.EIS, Soundscape.Kind.LACHEN, Soundscape.Kind.KREISCHEN, Soundscape.Kind.TROMMEL};
        double[] want = {118, 95, 90, 84, 86, 90, 80 + 6 * Math.log10(3), 76, 88, 88, 92, 100, 118};
        double worst = 0;
        String worstK = "";
        double[] cen = new double[ks.length];
        double[][] spec = new double[ks.length][];
        for (int k = 0; k < ks.length; k++) {
            Soundscape q = quiet();
            q.render(0.05);
            q.event(ks[k], 0, 2, -10, 1);
            float[] o = q.render(6);
            double dev = max100(o, sr) - (want[k] - 23);
            if (Math.abs(dev) > Math.abs(worst)) { worst = dev; worstK = ks[k].name(); }
            double on = Analysis.onset(o, sr, 0, Dsp.amp(10));
            if (Double.isNaN(on)) on = 0;
            spec[k] = Analysis.spectrum(Analysis.mono(o, sr, on, 2), 32768);
            cen[k] = Analysis.centroid(spec[k], sr, 32768);
        }
        int hl = 0, birds = 0, wd = 0;
        for (Soundscape.Kind k : Soundscape.Kind.values()) { if (k.highland()) hl++; if (k.bird()) birds++; if (k.wadden()) wd++; }
        check(hl == 13 && birds == 8 && wd == 13 && Math.abs(worst) < 2.5,
                f("13 Klänge des Gebirges (8 Vögel) in 10 m: lauteste 100 ms wie angegeben (größte Abweichung %+.1f dB, %s); das Watt bleibt bei 13", worst, worstK));
        double fw = Analysis.peak(spec[0], sr, 32768, 450, 700);
        Soundscape pq = quiet();
        pq.event(Soundscape.Kind.PFEIFE, 0, 2, -10, 1, 2.5);
        float[] po = pq.render(5);
        List<Double> pon = onsets(po, sr, 60);
        double pOff = 0;
        for (double t = 0; t < 4.9; t += 0.01) if (Analysis.rms(po, sr, t, 0.02) > Dsp.amp(60)) pOff = t;
        double plen = pon.isEmpty() ? 0 : pOff - pon.get(0);
        check(Math.abs(fw - 560) < 25 && plen > 2.2 && plen < 3.0, f("Dampfpfeife: Grundton %.0f Hz (560), ein Ton von 2,5 s klingt %.1f s", fw, plen));
        double kauz = Analysis.peak(spec[8], sr, 32768, 500, 900);
        double low = band(spec[12], sr, 32768, 20, 250) * 230 / Math.max(1e-30, band(spec[12], sr, 32768, 20, 20000) * 19980);
        check(cen[1] < 1500 && cen[2] > 2400 && cen[2] < 3800 && cen[3] > 3500 && cen[7] > 5500 && kauz > 600 && kauz < 780 && low > 0.5,
                f("Rabe tief (%.0f Hz), Ringdrossel flötet um %.0f Hz, Wiesenpieper %.0f Hz, Goldhähnchen %.0f Hz, Raufußkauz %.0f Hz, Trommel %.0f %% unter 250 Hz",
                        cen[1], cen[2], cen[3], cen[7], kauz, 100 * low));
        // Eis: schwere Stücke lauter und dumpfer
        double[] il = new double[2], ic = new double[2];
        double[] kg = {0.1, 3};
        for (int k = 0; k < 2; k++) {
            Soundscape q = quiet();
            q.event(Soundscape.Kind.EIS, 0, 2, -10, 1, kg[k]);
            float[] o = q.render(3);
            il[k] = max100(o, sr) + 23;
            ic[k] = Analysis.centroid(Analysis.spectrum(Analysis.mono(o, sr, 0, 1.5), 16384), sr, 16384);
        }
        check(il[1] - il[0] > 10 && ic[1] < ic[0] && ic[0] > 900, f("Eis: 3 kg %.0f dB, 0,1 kg %.0f dB in 1 m; dumpfer (Schwerpunkt %.0f gegen %.0f Hz)", il[1], il[0], ic[1], ic[0]));

        // Dampflok: Pegel der Teile in 10 m
        double ch = steam(sr, 3, 1, 112, -50, false, 0, 0, null), dr = steam(sr, 8, 0, 112, -50, false, 0, 0, null), ro = steam(sr, 8.33, 0, -50, 83, false, 0, 0, null);
        check(Math.abs(ch - 109) < 2 && Math.abs(ch - dr - 16) < 2 && Math.abs(ro - 80) < 2,
                f("Dampflok: Auspuff unter Volllast %.0f dB in 1 m (112), ohne Dampf %.0f dB leiser (16); Rollen bei 30 km/h %.0f dB (83)", ch + 3, ch - dr, ro + 3));
        // Takt: 3 m/s auf Treibrädern von 1 m mit vier Schlägen je Umdrehung = 3,8 Schläge je s
        Soundscape tq = quiet();
        Steam ts = new Steam("Lok");
        ts.x = 0; ts.y = 2; ts.z = -10; ts.rx = 0; ts.ry = 2; ts.rz = -10; ts.nearest = 300; ts.rollDb = -50;
        ts.standing = false; ts.speed = 3; ts.beats = 3 / Math.PI * 4; ts.load = 1;
        tq.steams.add(ts);
        tq.render(1);
        float[] to = tq.render(8);
        double rate = Analysis.peak(envelopeSpectrum(to, sr), 200, 1024, 2, 8);
        check(Math.abs(rate - ts.beats) < 0.3, f("Auspuffschläge: %.2f je s gehört, %.2f je s gefahren (3 m/s, Treibrad 1 m, vier je Umdrehung)", rate, ts.beats));
        // Schienenstöße: nahe am Ohr ein Wagen, 8,33 m/s, Schienen 15 m
        long[] cl = new long[1];
        double rj = steam(sr, 8.33, 0, -50, 83, false, 0, 0, cl);
        long hits = cl[0] / 2;
        check(hits > 40 && hits < 120, f("Schienenstöße: %d Schläge der Räder nahe am Ohr in 7 s (Schienen 15 m, 30 km/h: gut 20 Achsen je knapp 4 Stöße)", hits));
        double sq = steam(sr, 2, 0, -50, -50, false, 1, 0, null), cu = steam(sr, 6, 0, -50, -50, false, 0, 1, null), stand = steam(sr, 0, 0, 112, 83, true, 0, 0, null);
        check(sq > 88 && cu > 90 && stand > 70 && stand < 100,
                f("Bremsen quietschen (%.0f dB in 1 m), Spurkränze kreischen in der Kurve (%.0f dB); die stehende Lok zischt und pumpt (%.0f dB)", sq + 3, cu + 3, stand + 3));

        // Kanten im Sturm: Spalt 1 cm bei 20 m/s heult um 400 Hz; unter 5 m/s still
        double[] el = new double[2];
        double ef = 0;
        for (int k = 0; k < 2; k++) {
            Soundscape q = quiet();
            double u = k == 0 ? 20 : 4;
            q.wind = (x, y, z) -> Math.hypot(x, z + 3) < 0.5 ? new double[]{u, 0, 0} : new double[]{0, 0, 0};   // Sturm nur an der Kante, still am Ohr
            q.edges.add(new Edge("Spalt", 0, 2, -3, 0.01, 1));
            q.render(1);
            float[] o = q.render(6);
            el[k] = q.levels()[1];
            if (k == 0) ef = Analysis.peak(Analysis.spectrum(Analysis.mono(o, sr, 0, 6), 32768), sr, 32768, 150, 1500);
        }
        double ew = new Edge("Spalt", 0, 0, 0, 0.01, 1).levelDb(20) - 20 * Math.log10(3);
        check(Math.abs(ef - 400) < 40 && Math.abs(el[0] - ew) < 3 && el[1] < 20,
                f("Kante im Sturm: Spalt von 1 cm bei 20 m/s heult um %.0f Hz (400), %.0f dB in 3 m (%.0f), bei 4 m/s still", ef, el[0], ew));

        // Walpurgisnacht: Wind ruft, Trommeln, Lachen, Kreischen; ohne sie still
        Soundscape w0 = quiet(), w1 = quiet();
        w1.walpurgis = 1;
        float[] wo = w1.render(60);
        w0.render(60);
        long[] wc = w1.walpurgisCounts(), wz = w0.walpurgisCounts();
        double wl = db(Analysis.rms(wo, sr, 0, 60));
        check(wc[0] >= 6 && wc[1] >= 10 && wc[2] >= 1 && wz[0] + wz[1] + wz[2] + wz[3] == 0 && wl > 40 && wl < 80,
                f("Walpurgisnacht in 60 s: %d Rufe des Winds, %d Takte der Trommeln, %d mal Gelächter, %d Schreie; %.0f dB am Ohr; ohne sie nichts", wc[0], wc[1], wc[2], wc[3], wl));

        // Rechenzeit: drei Züge, acht Kanten, Walpurgisnacht im Sturm
        Soundscape r = Soundscape.beaufort(9);
        r.waveHeight = 0;
        r.quality = Soundscape.Quality.MEDIUM;
        r.walpurgis = 1;
        r.earY = 1140;
        for (int k = 0; k < 3; k++) {
            Steam s = new Steam("Zug " + k);
            s.x = 100 * k; s.z = -200; s.rx = 100 * k; s.rz = -190; s.standing = false; s.speed = 6; s.beats = 7; s.load = 0.8; s.nearest = 20;
            r.steams.add(s);
        }
        for (int k = 0; k < 8; k++) r.edges.add(new Edge("Kante " + k, 10 * k, 1145, -20, 0.02 + 0.1 * k, 4));
        r.render(1);
        long t0 = System.nanoTime();
        r.render(5);
        double ms = (System.nanoTime() - t0) / 1e6 / 5;
        check(ms < 60, f("Gebirge mit drei Zügen, acht Kanten und der Walpurgisnacht im Sturm (32 kHz): %.0f ms Rechnen je s Ton", ms));
    }

    /** Pegel einer Dampflok in 10 m (lauteste 100 ms + 20 dB, beide Ohren); cl[0] = Schläge an Schienenstößen. */
    private static double steam(int sr, double v, double load, double chuff, double roll, boolean standing, double brake, double squeal, long[] cl) {
        Soundscape q = quiet();
        Steam st = new Steam("Lok");
        st.x = 0; st.y = 2; st.z = -10; st.rx = 0; st.ry = 2; st.rz = -10;
        st.nearest = cl != null ? 40 : 300;
        st.speed = v; st.beats = v / Math.PI * 4; st.load = load; st.standing = standing; st.chuffDb = chuff; st.rollDb = roll; st.braking = brake; st.squeal = squeal;
        q.steams.add(st);
        q.render(1);
        float[] o = q.render(6);
        if (cl != null) cl[0] = st.clacks();
        return max100(o, sr) + 20;
    }

    /** Spektrum der Hüllkurve (Abtastung 200 Hz, 1024 Punkte) für den Takt. */
    private static double[] envelopeSpectrum(float[] o, int sr) {
        int step = sr / 200, n = o.length / 2 / step;
        double[] env = new double[n];
        double mean = 0;
        for (int i = 0; i < n; i++) {
            double e = 0;
            for (int k = 0; k < step; k++) { double v = o[2 * (i * step + k)]; e += v * v; }
            env[i] = Math.sqrt(e / step);
            mean += env[i] / n;
        }
        for (int i = 0; i < n; i++) env[i] -= mean;
        return Analysis.spectrum(env, 1024);
    }
    /** Seit Timmendorf Phase 15: die Bucht (Boote, Hafen, Regatta, Feuerwerk, Fähre, Promenade). */
    private void bucht(int sr) {
        lines.add("Bucht (seit Timmendorf Phase 15)");
        Soundscape.Kind[] ks = {Soundscape.Kind.FALL, Soundscape.Kind.FALL, Soundscape.Kind.SEGEL, Soundscape.Kind.WINSCH, Soundscape.Kind.SIGNALHORN,
                Soundscape.Kind.STARTSCHUSS, Soundscape.Kind.ABSCHUSS, Soundscape.Kind.ZERLEGUNG, Soundscape.Kind.KNISTERN};
        double[] tone = {0, 1, 15, 12, 1, 0, 75, 75, 100};
        double worst = 0;
        String worstK = "";
        double[] cen = new double[ks.length], low = new double[ks.length];
        float[][] outs = new float[ks.length][];
        for (int k = 0; k < ks.length; k++) {
            Soundscape q = quiet();
            q.volume = 0.001;   // −60 dB: der Begrenzer soll Knall und Horn in 10 m nicht beschneiden
            q.render(0.05);
            q.event(ks[k], 0, 2, -10, 1, tone[k]);
            float[] o = q.render(4);
            outs[k] = o;
            double dev = max100(o, sr) + 60 - (Soundscape.bayLevel(ks[k], tone[k]) - 23);
            if (Math.abs(dev) > Math.abs(worst)) { worst = dev; worstK = ks[k].name() + (k == 1 ? " (Holz)" : ""); }
            double on = Analysis.onset(o, sr, 0, Dsp.amp(10));
            if (Double.isNaN(on)) on = 0;
            double[] sp = Analysis.spectrum(Analysis.mono(o, sr, on, 1.0), 16384);
            cen[k] = Analysis.centroid(sp, sr, 16384);
            low[k] = share(sp, sr, 16384, 20, 200, 12000);
        }
        int bays = 0;
        for (Soundscape.Kind k : Soundscape.Kind.values()) if (k.bay()) bays++;
        int coasts = 0;
        for (Soundscape.Kind k : Soundscape.Kind.values()) if (k.coast()) coasts++;
        check(bays == 9 && coasts == 7 && Math.abs(worst) < 2.5, f("9 Klänge der Bucht in 10 m: lauteste 100 ms wie angegeben (größte Abweichung %+.1f dB, %s); die Küste zählt weiter 7", worst, worstK));
        check(cen[0] > 1.6 * cen[1] && cen[0] > 1000, f("Fall am Aluminiummast hell (Schwerpunkt %.0f Hz), am Holzmast dumpf (%.0f Hz)", cen[0], cen[1]));
        // Segel: Schläge im Takt; ein großes Segel schlägt langsamer und tiefer
        Soundscape g1 = quiet();
        g1.render(0.05);
        g1.event(Soundscape.Kind.SEGEL, 0, 2, -5, 1, 7);
        float[] go1 = g1.render(4);
        Soundscape g2 = quiet();
        g2.render(0.05);
        g2.event(Soundscape.Kind.SEGEL, 0, 2, -5, 1, 300);
        float[] go2 = g2.render(5);
        int sl1 = onsets(go1, sr, max100(go1, sr) - 9).size(), sl2 = onsets(go2, sr, max100(go2, sr) - 9).size();
        double lo1 = share(Analysis.spectrum(Analysis.mono(go1, sr, 0, 3), 16384), sr, 16384, 20, 200, 12000),
                lo2 = share(Analysis.spectrum(Analysis.mono(go2, sr, 0, 3), 16384), sr, 16384, 20, 200, 12000);
        check(sl1 >= 3 && sl1 <= 20 && sl2 < sl1 && lo2 > lo1, f("Segel schlägt: Jolle (7 m²) %d Schläge, Großsegel (300 m²) %d, tiefer (unter 200 Hz %.0f %% gegen %.0f %%)", sl1, sl2, lo2 * 100, lo1 * 100));
        int wc = countClicks(outs[3], sr);
        check(wc >= 6 && cen[3] > 1500, f("Winsch rattert: %d Klicks gezählt (12), Schwerpunkt %.0f Hz", wc, cen[3]));
        double fs = Analysis.peak(Analysis.spectrum(Analysis.mono(outs[4], sr, 0.2, 0.6), 16384), sr, 16384, 200, 700);
        check(Math.abs(fs - 412) < 20, f("Signalhorn des Startschiffs: Grundton %.0f Hz (410)", fs));
        check(low[6] > 0.3 && cen[8] > 1500, f("Abschuss dumpf (%.0f %% unter 200 Hz), Knistern hell (Schwerpunkt %.0f Hz)", low[6] * 100, cen[8]));
        int kc = countClicks(outs[8], sr);
        check(kc > 40, f("Knistersterne: %d einzelne Knalle gezählt", kc));
        // Fährhorn nach KVR
        Soundscape fh = quiet();
        fh.horn(Soundscape.Kind.FAEHRHORN, 0, 2, 300, 1, 2.0);
        float[] fo = fh.render(4);
        double fst = 300 / fh.soundSpeed() + 0.4;
        double ff = Analysis.peak(Analysis.spectrum(Analysis.mono(fo, sr, fst, 1.2), 16384), sr, 16384, 50, 400);
        double lf = db(Analysis.rms(fo, sr, fst, 1.2)) + 20 * Math.log10(300);
        check(ff >= 70 && ff <= 200 && Math.abs(lf - 143) < 4, f("Fährhorn: Grundton %.0f Hz (KVR für 200 m und mehr: 70 bis 200 Hz), %.0f dB in 1 m (143)", ff, lf));
        // das Feuerwerk aus 700 m: erst das Bild, dann der Knall
        Soundscape fw = quiet();
        double c = fw.soundSpeed();
        fw.render(0.05);
        fw.event(Soundscape.Kind.ZERLEGUNG, 0, 90, -696, 1, 75);
        float[] fwo = fw.render(4);
        double on = Analysis.onset(fwo, sr, 0, Dsp.amp(50)), dist = Math.hypot(700 - 2 - 86, 0) * 0 + Math.sqrt(88 * 88 + 696 * 696);
        double atEar = max100(fwo, sr);
        check(Math.abs(on - dist / c - 0.05) < 0.06 && atEar > 76 && atEar < 92,
                f("Feuerwerk: Bombe von 75 mm in %.0f m zerlegt, der Knall kommt nach %.2f s (Laufzeit %.2f s), %.0f dB am Ohr", dist, on - 0.05, dist / c, atEar));
        Soundscape b2 = quiet();
        b2.volume = 0.001;
        b2.render(0.05);
        b2.event(Soundscape.Kind.ZERLEGUNG, 0, 2, -10, 1, 150);
        float[] bo2 = b2.render(4);
        double d150 = max100(bo2, sr) - max100(outs[7], sr);
        double o150 = Analysis.onset(bo2, sr, 0, Dsp.amp(40)), o75 = Analysis.onset(outs[7], sr, 0, Dsp.amp(40));
        double c150 = Analysis.centroid(Analysis.spectrum(Analysis.mono(bo2, sr, o150 - 0.01, 0.05), 2048), sr, 2048), c75 = Analysis.centroid(Analysis.spectrum(Analysis.mono(outs[7], sr, o75 - 0.01, 0.05), 2048), sr, 2048);
        check(Math.abs(d150 - 6) < 1.5 && c150 < c75, f("Bombe von 150 mm %.1f dB lauter als 75 mm (+6), dumpfer (N-Welle länger: %.0f gegen %.0f Hz)", d150, c150, c75));
        // Flaggen im Wind
        double[] flagDb = new double[2];
        long[] slaps = new long[2];
        double[] winds = {8, 1};
        for (int k = 0; k < 2; k++) {
            Soundscape a = quiet();
            a.windSpeed = winds[k];
            a.windGainDb = -80;
            a.flaps.add(new Flap("Strandflagge", 0, 6, -5, 1.5));
            a.render(1);
            long s0 = a.flagSlaps();
            float[] ao = a.render(10);
            slaps[k] = a.flagSlaps() - s0;
            flagDb[k] = db(Analysis.rms(ao, sr, 0, 10));
        }
        double fwant = Flap.frequency(com.dan.soundscape.Soundscape.beaufort(0).windAt(6) * 8 / Math.max(1e-9, com.dan.soundscape.Soundscape.beaufort(0).windSpeed), 1.5) * 10;
        check(slaps[0] > 15 && slaps[0] < 60 && slaps[1] == 0 && flagDb[0] > 40, f("Strandflagge (1,5 m²) bei 8 m/s: %d Schläge in 10 s (um %.0f), %.0f dB in 5 m; bei 1 m/s %d", slaps[0], fwant, flagDb[0], slaps[1]));
        // ein Hafen voller Masten
        long[] rs = new long[2];
        double[] rd = new double[2];
        double[] rw = {8, 2};
        for (int k = 0; k < 2; k++) {
            Soundscape a = quiet();
            a.windSpeed = rw[k];
            a.windGainDb = -80;
            a.rigs.add(new Rig("Hafen", 0, 1, -60, 40, 100));
            a.render(1);
            long s0 = a.rigSlaps();
            float[] ao = a.render(10);
            rs[k] = a.rigSlaps() - s0;
            rd[k] = db(Analysis.rms(ao, sr, 0, 10));
        }
        double rwant = 100 * 0.015 * (Soundscape.beaufort(0).windAt(9) / Math.max(1e-9, Soundscape.beaufort(0).windSpeed) * 8 - 2.5) * 10;
        check(Math.abs(rs[0] - rwant) < 0.35 * rwant && rs[1] == 0 && rd[0] > 35, f("Hafen mit 100 Masten bei 8 m/s: %d Schläge der Fallen in 10 s (um %.0f), %.0f dB in 60 m; bei 2 m/s %d", rs[0], rwant, rd[0], rs[1]));
        // Schritte
        double[] sc = new double[2], sdb = new double[2];
        long[] sn = new long[2];
        for (int k = 0; k < 2; k++) {
            Soundscape a = quiet();
            a.walkers = 10;
            a.footing = k == 0 ? 0 : 1;
            a.render(0.5);
            long s0 = a.footsteps();
            float[] ao = a.render(10);
            sn[k] = a.footsteps() - s0;
            sdb[k] = db(Analysis.rms(ao, sr, 0, 10));
            sc[k] = Analysis.centroid(Analysis.spectrum(Analysis.mono(ao, sr, 0, 10), 16384), sr, 16384);
        }
        check(Math.abs(sn[0] - 180) < 40 && sc[1] < sc[0] && sdb[1] > sdb[0], f("Schritte: 10 Leute gehen, %d Schritte in 10 s (180); Holzdeck tiefer (%.0f gegen %.0f Hz) und lauter (%.0f gegen %.0f dB)", sn[0], sc[1], sc[0], sdb[1], sdb[0]));
        // Rechenzeit einer Bucht
        Soundscape all = Soundscape.beaufort(4);
        all.quality = Soundscape.Quality.MEDIUM;
        all.autoGain = false;
        for (int i = 0; i < 12; i++) all.flaps.add(new Flap("Flagge " + i, 20 * i - 100, 6, -20, 1.5));
        all.rigs.add(new Rig("Hafen", 0, 1, -200, 80, 150));
        all.walkers = 20;
        all.render(0.5);
        long t0 = System.nanoTime();
        for (int i = 0; i < 6; i++) {
            all.event(Soundscape.Kind.SEGEL, 30, 2, -40, 1, 15);
            all.event(Soundscape.Kind.ZERLEGUNG, 0, 90, -500, 1, 100);
            all.event(Soundscape.Kind.KNISTERN, 0, 90, -500, 1, 200);
            all.render(1);
        }
        double ms = (System.nanoTime() - t0) / 1e6 / 6;
        check(ms < 60, f("Bucht mit 12 Flaggen, Hafen mit 150 Masten, 20 Gehenden, Segeln und Feuerwerk (32 kHz, Bft 4): %.0f ms Rechnen je s Ton", ms));
    }

}
