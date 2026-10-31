package com.dan.surf.demo;

import com.dan.surf.Bars;
import com.dan.surf.BreakerMesh;
import com.dan.surf.BreakerShape;
import com.dan.surf.FoamField;
import com.dan.surf.WaveTrain;
import com.dan.surf.Surf;
import com.dan.surf.Swash;
import com.dan.surf.WaveField;
import com.dan.surf.Surf.Breaker;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10): Dispersion, Grenzfälle tief und flach, Shoaling nach Green, Brechen
 * nach McCowan, Brechertypen nach Battjes, Presets, Ereignis (Hülle, Sylt Phase 1); seit Sylt Phase 5 das Wellenfeld:
 * Green und Snell im Feld, Sättigung in der Brandungszone (Battjes und Janssen), Set-down nach Longuet-Higgins und
 * Stewart, Set-up auf ebenem Hang nach Bowen, Längsstrom gegen Komar und Inman, Unterstrom, Rippströmung in einer Rinne,
 * Erhaltung der Masse, Riffe und Rinnen, Rechnen im eigenen Faden; seit Sylt Phase 6 die Brecher: Rayleigh und Gruppen,
 * Brechen bei γ·h, ein Typ je Becken, Lippe und Aufschlag, Walze, Schaum mit Nachleuchten, Schwebung, Netz; seit Sylt Phase 7 der Schwall: ruhender See, Dammbruch nach Ritter, Masse,
 * Auflauf gegen Stockdon, eine Spitze je Welle, Spülsaum, Trocknung nach dem Wetter, Stöße, Netz mit Schwall, Budget;
 * seit Lighthouse Phase 17 der Grund je Reihe (Bauwerk im Meer).
 * Endet mit „Alles in Ordnung.“
 */
public final class SurfCheck {
    private static int bad;

    public static void main(String[] args) {
        double g = Surf.G;
        // Dispersion: Rest der Gleichung, Grenzfälle
        boolean res = true;
        for (double T : new double[]{3, 6, 8, 12, 16})
            for (double h : new double[]{0.05, 0.5, 2, 5, 13, 40, 400}) {
                double k = Surf.wavenumber(T, h), w = 2 * Math.PI / T;
                if (Math.abs(g * k * Math.tanh(k * h) - w * w) > 1e-10 * w * w) res = false;
            }
        check(res, "Dispersion ω² = g·k·tanh(kh) auf 1e-10 erfüllt (35 Fälle)");
        double T = 8, w = 2 * Math.PI / T;
        check(Math.abs(Surf.wavenumber(T, 2000) - w * w / g) < 1e-9, "tiefes Wasser: k = ω²/g");
        check(Math.abs(Surf.wavenumber(T, 0.01) * Math.sqrt(g * 0.01) / w - 1) < 1e-3, "flaches Wasser: c = √(gh)");
        double L5 = 2 * Math.PI / Surf.wavenumber(8, 5);
        check(Math.abs(L5 - 53.1) < 0.1, String.format("T = 8 s, h = 5 m: L = %.1f m (Tafel: h/L₀ = 0,05 ⇒ h/L = 0,0942, L = 53,1 m)", L5));
        check(Math.abs(Surf.deepLength(8) - 99.9) < 0.1, "L₀ = gT²/2π = 99,9 m bei 8 s");
        // Shoaling
        check(Math.abs(Surf.shoaling(8, 3000) - 1) < 1e-6, "Shoaling tief: K_s = 1");
        double min = 9;
        for (double h = 1; h < 60; h += 0.05) min = Math.min(min, Surf.shoaling(8, h));
        check(Math.abs(min - 0.913) < 0.002, String.format("Shoaling: Minimum K_s = %.3f (Lehrbuch 0,913)", min));
        double r = Surf.shoaling(20, 0.02) / Surf.shoaling(20, 0.32);
        check(Math.abs(r - 2) < 0.01, String.format("Green im Flachen: 16-fach flacher ⇒ %.3f-fach höher (2)", r));
        // McCowan auf ebenem Hang 1:50
        Surf s = Surf.sylt();
        s.bed = (x, z) -> -10 + x / 50.0;       // Hang steigt nach Osten (+x)
        s.seaFromDeg = 270;
        boolean cap = true;
        for (double x = 0; x < 500; x += 1) if (s.heightAt(x, 0, 0) > s.gamma * s.depthAt(x, 0, 0) + 1e-12) cap = false;
        check(cap, "nirgends höher als γ·h (McCowan)");
        double xb = s.breakPointAlong(0, 0, 500, 0, 0), hb = s.depthAt(xb, 0, 0);
        check(xb > 300 && xb < 500 && Math.abs(s.hs * Surf.shoaling(s.tp, hb) - s.gamma * hb) < 0.03,
                String.format("Brechpunkt bei h = %.2f m: H = γ·h", hb));
        check(Math.abs(s.slopeAt(250, 0) - 0.02) < 1e-9, "Hang 1:50 in Laufrichtung gemessen");
        check(s.depthAt(520, 0, 0) < 0 && s.heightAt(520, 0, 0) == 0 && !s.breakingAt(520, 0, 0), "auf dem Trockenen keine Welle");
        // Brechertypen nach Battjes (1974)
        check(Surf.typeOffshore(0.3) == Breaker.SPILLING && Surf.typeOffshore(1.0) == Breaker.PLUNGING
                && Surf.typeOffshore(4.0) == Breaker.COLLAPSING, "Typen aus ξ₀: 0,5 / 3,3");
        check(Surf.typeAtBreaking(0.3) == Breaker.SPILLING && Surf.typeAtBreaking(1.0) == Breaker.PLUNGING
                && Surf.typeAtBreaking(2.5) == Breaker.COLLAPSING && Surf.typeAtBreaking(4) == Breaker.SURGING, "Typen aus ξ_b: 0,4 / 2,0");
        Surf riff = Surf.sylt();
        riff.seaFromDeg = 270;
        riff.bed = (x, z) -> -3 + x / 100.0;                     // flaches Riff 1:100
        Surf steil = Surf.sylt();
        steil.seaFromDeg = 270;
        steil.bed = (x, z) -> -3 + x / 8.0;                      // steiler Strandhang 1:8
        double xr = riff.breakPointAlong(0, 0, 300, 0, 0), xs = steil.breakPointAlong(0, 0, 24, 0, 0);
        check(riff.typeAt(xr, 0, 0) == Breaker.SPILLING, "flaches Riff: Schwallbrecher");
        check(steil.typeAt(xs, 0, 0) == Breaker.PLUNGING, "steiler Strandhang: Sturzbrecher");
        // Presets
        check(Surf.calm().hs < Surf.breeze().hs && Surf.breeze().hs < Surf.gale().hs && Surf.gale().hs < Surf.storm().hs,
                "Presets steigen von calm bis storm");
        check(Surf.beaufort(-3).hs == Surf.beaufort(0).hs && Surf.beaufort(99).hs == Surf.beaufort(12).hs, "beaufort(n) begrenzt auf 0..12");
        check(Surf.ostsee().tp < Surf.sylt().tp && Surf.sockel().hs > Surf.sylt().hs, "Orte: ostsee kurz, sockel hoch");
        // Ereignis über eigene Schnittstelle
        int[] calls = new int[1];
        s.onBreaking = (x, z, h, type) -> calls[0]++;
        s.breakPointAlong(0, 0, 500, 0, 0);
        check(calls[0] == 1, "Brechen wird über die eigene Schnittstelle gemeldet");
        Surf o = new Surf();
        o.offshore = new Surf.Offshore() {
            public double hs() { return 2.5; }
            public double tp() { return 11; }
            public double fromDeg() { return 300; }
        };
        check(o.hs() == 2.5 && o.tp() == 11, "See von außen über Offshore");
        feld();
        brecher();
        schwall();
        meeresleuchten();
        bauwerk();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Seit Sylt Phase 5: das Wellenfeld. */
    static void feld() {
        // Green: kleine Wellen, senkrecht auf einen Hang 1:100, ohne Brechen
        Surf g = Surf.sylt();
        g.hs = 0.2; g.tp = 10; g.seaFromDeg = 270;
        g.bed = (x, z) -> -20 + x / 100.0;
        WaveField fg = new WaveField(g, 0, -100, 90, 1900, 200, 5);
        fg.compute(0);
        double h8 = 20 - 1200 / 100.0, h2 = 20 - 1800 / 100.0;
        double rg = fg.hrmsAt(1800, 0) / fg.hrmsAt(1200, 0), rk = Surf.shoaling(10, h2) / Surf.shoaling(10, h8);
        check(Math.abs(rg / rk - 1) < 0.01 && fg.breakingAt(1800, 0) < 0.01,
                String.format("Feld, Green: H(2 m)/H(8 m) = %.4f, Shoaling %.4f (ohne Brechen, Q_b %.4f)", rg, rk, fg.breakingAt(1800, 0)));
        // Snell: schräg (30° draußen) auf gerade Tiefenlinien, sin θ / c bleibt
        Surf sn = Surf.sylt();
        sn.hs = 0.2; sn.tp = 8; sn.seaFromDeg = 240;
        sn.bed = (x, z) -> -20 + x / 100.0;
        WaveField fs = new WaveField(sn, 0, -300, 90, 1900, 600, 5);
        fs.compute(0);
        double c0 = Surf.G * 8 / (2 * Math.PI), ref = Math.sin(Math.toRadians(30)) / c0, worst = 0;
        for (double x : new double[]{400, 1000, 1500, 1800}) {
            double h = 20 - x / 100, c = 2 * Math.PI / 8 / Surf.wavenumber(8, h);
            double[] d = fs.directionAt(x, 0);
            double sinT = Math.abs(d[1]);
            worst = Math.max(worst, Math.abs(sinT / c / ref - 1));
        }
        check(worst < 0.005, String.format("Feld, Snell: sin θ / c auf geraden Tiefenlinien bis auf %.3f %% gleich", worst * 100));
        // Ebener Hang 1:50 für Brandung, Set-up, Längsstrom
        Surf p = Surf.sylt();
        p.hs = 1.0; p.tp = 8; p.seaFromDeg = 270;
        p.bed = (x, z) -> -10 + x / 50.0;
        WaveField fp = new WaveField(p, 0, -100, 90, 560, 200, 2);
        WaveField.Result rp = fp.compute(0);
        boolean cap = true;
        for (int q = 0; q < rp.hrms.length; q++) if (rp.hrms[q] > rp.gamma * rp.depth[q] + 1e-5) cap = false;
        double sat = 0;
        int ns = 0;
        for (double x = 440; x <= 480; x += 4) { sat += fp.hrmsAt(x, 0) / (10 - x / 50 + fp.setupAt(x, 0)); ns++; }
        sat /= ns;
        check(cap && sat > 0.35 && sat < 0.6, String.format("Feld, Brechen: nirgends H_rms > γ·h (γ = %.2f); im Inneren der Brandung H_rms/h = %.2f (Thornton und Guza 0,42)", rp.gamma, sat));
        // Set-down vor dem Brechen nach Longuet-Higgins und Stewart: η = −H_rms²·k / (8·sinh 2kh)
        double xd = 260, hd = 10 - xd / 50, kd = Surf.wavenumber(8, hd), hr = fp.hrmsAt(xd, 0);
        double sdT = -hr * hr * kd / (8 * Math.sinh(2 * kd * hd)), sd = fp.setupAt(xd, 0);
        check(fp.breakingAt(xd, 0) < 0.002 && Math.abs(sd / sdT - 1) < 0.08, String.format("Feld, Set-down vor dem Brechen: %.4f m, Longuet-Higgins und Stewart %.4f m", sd, sdT));
        // Set-up im Inneren nach Bowen: dη/dx = −K·dh/dx, K = (3γ²/8)/(1 + 3γ²/8) mit dem örtlichen γ = H_rms/(h + η)
        double xa = 450, xb = 476;
        double slopeEta = (fp.setupAt(xb, 0) - fp.setupAt(xa, 0)) / (xb - xa), gl = 0;
        for (double x = xa; x <= xb; x += 2) gl += fp.hrmsAt(x, 0) / (10 - x / 50 + fp.setupAt(x, 0));
        gl /= ((xb - xa) / 2 + 1);
        double K = 3 * gl * gl / 8 / (1 + 3 * gl * gl / 8), slopeT = K / 50;
        double eShore = 0;
        for (double x = 400; x <= 520; x += 1) if (10 - x / 50 + fp.setupAt(x, 0) > 0.05) eShore = Math.max(eShore, fp.setupAt(x, 0));
        check(Math.abs(slopeEta / slopeT - 1) < 0.25 && eShore / p.hs > 0.08 && eShore / p.hs < 0.3,
                String.format("Feld, Set-up auf ebenem Hang: Gefälle %.5f, Bowen %.5f (γ örtlich %.2f); an der Wasserlinie %.2f m = %.2f·H_s", slopeEta, slopeT, gl, eShore, eShore / p.hs));
        // Unterstrom unter den Brechern
        double uu = fp.currentAt(460, 0)[0];
        check(uu < -0.03 && Math.abs(fp.currentAt(460, 0)[1]) < 0.01, String.format("Feld, Unterstrom: %.2f m/s seewärts in der Brandung, kein Längsstrom bei senkrechter See", uu));
        // Längsstrom: schräg, gegen Komar und Inman (1970) V = 1,17·√(g·H_b)·sin α_b·cos α_b
        Surf o = Surf.sylt();
        o.hs = 1.0; o.tp = 8; o.seaFromDeg = 250;
        o.bed = p.bed;
        WaveField fo = new WaveField(o, 0, -150, 90, 560, 300, 2);
        fo.compute(0);
        double xBr = 0;
        for (double x = 200; x < 500; x += 2) if (fo.breakingAt(x, 0) > 0.1) { xBr = x; break; }
        double hb = fo.hrmsAt(xBr, 0), ab = Math.abs(Math.asin(fo.directionAt(xBr, 0)[1]));
        double vK = 1.17 * Math.sqrt(Surf.G * hb) * Math.sin(ab) * Math.cos(ab), vMid = Math.abs(fo.currentAt((xBr + 500) / 2, 0)[1]), vOff = Math.abs(fo.currentAt(150, 0)[1]);
        check(Math.abs(vMid / vK - 1) < 0.4 && vOff < 0.02,
                String.format("Feld, Längsstrom mitten in der Brandung %.2f m/s, Komar und Inman %.2f m/s (H_b %.2f m, α_b %.1f°); draußen %.3f m/s", vMid, vK, hb, Math.toDegrees(ab), vOff));
        // Riffe und Rinnen
        Bars b = new Bars();
        b.crest = new double[]{120}; b.height = new double[]{1.2}; b.width = new double[]{30}; b.ripSpacing = 300; b.ripJitter = 0;
        double[] rips = b.ripsBetween(-400, 400);
        double vRip = rips[0], vBar = vRip + 150;
        check(Math.abs(b.offset(120 - b.crescent, vBar) - 1.2 * (1 - b.ripAt(vBar))) < 0.25 && b.offset(120, vRip) < -0.3,
                String.format("Riffe und Rinnen: Kamm %.2f m hoch zwischen den Rinnen, in der Rinne %.2f m; %s", b.offset(120, vBar), b.offset(120, vRip), b.describe()));
        Bars b2 = new Bars();
        b2.crest = b.crest; b2.height = b.height; b2.width = b.width; b2.ripSpacing = 300; b2.ripJitter = 0;
        double[] before = b2.ripsBetween(-400, 400);
        b2.storm();
        double[] after = b2.ripsBetween(-400, 400);
        Bars b3 = new Bars();
        b3.ripSpacing = 300; b3.ripJitter = 0; b3.storm();
        check(java.util.Arrays.equals(rips, before) && !java.util.Arrays.equals(before, after) && java.util.Arrays.equals(after, b3.ripsBetween(-400, 400)),
                "Riffe und Rinnen: gleicher Startwert, gleiche Lage; nach einem Sturm verlagert, wieder reproduzierbar");
        // Rippströmung in der Rinne
        Surf rs = Surf.sylt();
        rs.hs = 1.2; rs.tp = 8; rs.seaFromDeg = 270;
        Bars.Coast coast = Bars.Coast.straight(600, 0, 270);
        rs.bed = b.apply((x, z) -> -8 + x / 75.0, coast);
        WaveField fr = new WaveField(rs, 0, -450, 90, 700, 900, 4);
        WaveField.Result rr = fr.compute(0);
        double zRip = -vRip, zBar = -vBar;
        double uRip = 0, uBar = 0;
        for (double x = 490; x <= 530; x += 4) { uRip += fr.currentAt(x, zRip)[0] / 11; uBar += fr.currentAt(x, zBar)[0] / 11; }
        check(uRip < -0.2 && uBar > uRip + 0.2, String.format("Rippströmung: in der Rinne %.2f m/s seewärts, über dem Riff %.2f m/s", uRip, uBar));
        // Masse: was die Wellen zur Küste tragen, fließt in jeder Zeile zurück (gerader Strand, schräge See)
        WaveField.Result ro = fo.result();
        double worstM = 0, muM = 0;
        for (int i = 0; i < fo.nu; i += 10) {
            double net = 0, mu = 0;
            for (int j = 0; j < fo.nv; j++) {
                int q = i * fo.nv + j;
                double h = ro.depth[q];
                if (h < 0.3) continue;   // flacher rechnet das Feld die Strömung mit 0,3 m (Unterstrom begrenzt)
                double c = 2 * Math.PI / 8 / Surf.wavenumber(8, h), m = Surf.G * ro.hrms[q] * ro.hrms[q] / 8 / c * Math.cos(ro.theta[q]);
                net += h * ro.cu[q] + m;
                mu += m;
            }
            worstM = Math.max(worstM, Math.abs(net) / Math.max(1e-9, mu));
            muM = Math.max(muM, mu);
        }
        check(worstM < 0.01, String.format("Masse erhalten: in jeder Zeile fließt zurück, was die Wellen bringen (Rest %.2f %%)", worstM * 100));
        // im eigenen Faden
        Surf a = Surf.sylt();
        a.bed = p.bed;
        double[] lvl = {0};
        a.level = t -> lvl[0];
        WaveField fa = new WaveField(a, 0, -50, 90, 560, 100, 4);
        boolean s1 = fa.update(0);
        long t0 = System.currentTimeMillis();
        while (fa.busy() && System.currentTimeMillis() - t0 < 10000) Thread.onSpinWait();
        boolean s2 = fa.update(0);
        lvl[0] = 0.5;
        boolean s3 = fa.update(0);
        while (fa.busy() && System.currentTimeMillis() - t0 < 20000) Thread.onSpinWait();
        check(s1 && !s2 && s3 && fa.result() != null && Math.abs(fa.result().level - 0.5) < 1e-9,
                "Feld im eigenen Faden: neu nur, wenn sich See, Pegel oder Grund ändern; danach getauscht");
        // Surf fragt das Feld
        p.field = fp;
        check(Math.abs(p.heightAt(450, 0, 0) - fp.hsAt(450, 0)) < 1e-12 && p.heightAt(450, 0, 0) < Surf.sylt().hs * Surf.shoaling(8, 1),
                String.format("Surf.heightAt aus dem Feld (%.2f m statt der Obergrenze der Hülle)", p.heightAt(450, 0, 0)));
        System.out.println(fp.describe());
        System.out.println(fr.describe());
    }

    /** Seit Sylt Phase 6: die Brecher (Wellenzug, Formen, Schaum, Netz). */
    static void brecher() {
        // Rayleigh: Höhen der hereinkommenden Wellen, mit und ohne Gruppen
        double[] stat0 = rayleigh(0), stat1 = rayleigh(0.45);
        check(Math.abs(stat0[0] - 1) < 0.05 && Math.abs(stat1[0] - 1) < 0.06,
                String.format(java.util.Locale.GERMAN, "Rayleigh: H_rms/(H_s/√2) = %.3f ohne, %.3f mit Gruppen", stat0[0], stat1[0]));
        check(Math.abs(stat0[1] - 1.416) < 0.08, String.format(java.util.Locale.GERMAN, "H_1/3 = %.3f·H_rms (Rayleigh 1,416)", stat0[1]));
        check(Math.abs(stat0[2]) < 0.1 && stat1[2] > 0.1,
                String.format(java.util.Locale.GERMAN, "Gruppen: Folgekorrelation der Höhen %.2f ohne, %.2f mit", stat0[2], stat1[2]));
        // die drei Becken je 150 s
        SurfBreakers sb = new SurfBreakers();
        Surf.Breaker[] want = {Surf.Breaker.SPILLING, Surf.Breaker.PLUNGING, Surf.Breaker.COLLAPSING};
        String[] names = {"Schwall", "Sturz", "Reflexion"};
        boolean nanFree = true, idxOk = true;
        for (int i = 0; i < 3; i++) {
            SurfBreakers.Tank t = sb.tanks[i];
            double[] ratio = {0, 0};
            int[] imp = {0};
            WaveTrain tr = t.train;
            tr.onBreaking = (x, y, h, type) -> { ratio[0] += h / Math.max(1e-6, tr.depth(x)); ratio[1]++; };
            WaveTrain.Impact old = tr.onImpact;
            tr.onImpact = (x, y, z, v, sp) -> { imp[0]++; old.impact(x, y, z, v, sp); };
            double boreErr = 0;
            int boreN = 0;
            for (int f = 0; f < 150 * 60; f++) {
                t.step(1 / 60.0);
                if (f % 30 == 0) {
                    t.mesh.build(tr, t.xEnd);
                    for (int q = 0; q < t.mesh.vertexCount * BreakerMesh.STRIDE; q++) if (!Float.isFinite(t.mesh.vertices[q])) { nanFree = false; break; }
                    for (int q = 0; q < t.mesh.indexCount; q++) if (t.mesh.indices[q] < 0 || t.mesh.indices[q] >= t.mesh.vertexCount) { idxOk = false; break; }
                    // Walze: Geschwindigkeit nach der Sprungformel
                    for (WaveTrain.Wave w : tr.waves) {
                        int r = tr.rows / 2;
                        double p = tr.progress(w, r), h = tr.depth(w.x);
                        boolean all = true;
                        for (int k = 0; k < tr.rows; k++) if (!w.broken(k) || tr.progress(w, k) < 2) all = false;
                        if (!all || Double.isNaN(p) || h < 0.3) continue;
                        double H = 0;
                        for (int k = 0; k < tr.rows; k++) H += tr.heightRow(w, k) / tr.rows;
                        double h2 = h + H, cb = Math.sqrt(Surf.G * h2 * (h + h2) / (2 * h));
                        boreErr = Math.max(boreErr, Math.abs(w.c / cb - 1));
                        boreN++;
                    }
                }
            }
            int[] by = tr.brokenByType;
            int total = 0;
            for (int n : by) total += n;
            int mine = by[want[i].ordinal()] + (i == 2 ? by[Surf.Breaker.SURGING.ordinal()] : 0);
            check(total >= 8 && mine >= 0.75 * total, String.format(java.util.Locale.GERMAN, "Becken %s: %d von %d Wellen brechen als %s", names[i], mine, total, want[i]));
            double mr = ratio[0] / Math.max(1, ratio[1]);
            check(Math.abs(mr - tr.gamma) < 0.04, String.format(java.util.Locale.GERMAN, "Becken %s: Brechen bei H/h = %.3f (γ = %.2f)", names[i], mr, tr.gamma));
            if (i == 1) check(imp[0] > 0 && imp[0] == tr.impacts, String.format("Sturz: %d Aufschläge der Lippe, jeder gemeldet", imp[0]));
            else check(tr.impacts == 0, "Becken " + names[i] + ": keine Aufschläge");
            if (i == 0) check(boreN > 0 && boreErr < 0.03, String.format(java.util.Locale.GERMAN, "Walze: c = √(g·h₂·(h₁+h₂)/2h₁) (%d Proben)", boreN));
        }
        check(nanFree && idxOk, "Netz: alle Zahlen endlich, alle Indizes gültig");
        // Formen
        double H = 2, h = 2.6;
        double plMin = 9, coMax = 0, spMax = 0;
        for (double p = 0.5; p <= 1.0001; p += 0.1) {
            plMin = Math.min(plMin, overhang(BreakerShape.plunging(H, h, 12, 12, p)));
            coMax = Math.max(coMax, overhang(BreakerShape.collapsing(H, h, 12, 12, p)));
            spMax = Math.max(spMax, overhang(BreakerShape.spilling(H, h, 12, 12, p)));
        }
        double rest = Math.max(overhang(BreakerShape.unbroken(H, h, 12, 12, 1)), overhang(BreakerShape.bore(H, h, 12, 12)));
        check(plMin > 0.3 * H && coMax < 0.2 * H && spMax == 0 && rest == 0,
                String.format(java.util.Locale.GERMAN, "Überhang: Sturz %.2f·H, Reflexion höchstens %.2f·H (der Fuß kippt), Schwall, Walze und ungebrochen keiner", plMin / H, coMax / H));
        double[] tip = BreakerShape.lipTip(H, h, 1);
        double trough = (BreakerShape.crestShare(H, h) - 1) * H;
        check(Math.abs(tip[1] - trough) < 1e-9 && tip[0] > H, String.format(java.util.Locale.GERMAN, "Lippe trifft bei p = 1 den Trog, %.2f m vor dem Kamm", tip[0]));
        BreakerShape.Curve c = BreakerShape.plunging(H, h, 12, 12, 0.7);
        double maxThin = 0;
        for (int k = 0; k < BreakerShape.K; k++) { maxThin = Math.max(maxThin, c.thin[k]); }
        check(c.x.length == BreakerShape.K && maxThin > 0.9, "Form: K Punkte, die Lippe als dünn markiert");
        // Schaum: dicht zerfällt, Schlieren leuchten nach
        FoamField ff = new FoamField(0, 50, 0.5, 1);
        ff.drift = 0;
        ff.deposit(0, 20, 22, 1);
        for (int k = 0; k < 6 * 60; k++) ff.step(1 / 60.0);
        double d6 = ff.denseAt(21, 0), r6 = ff.residualAt(21, 0);
        for (int k = 0; k < 24 * 60; k++) ff.step(1 / 60.0);
        double d30 = ff.denseAt(21, 0), r30 = ff.residualAt(21, 0);
        for (int k = 0; k < 270 * 60; k++) ff.step(1 / 60.0);
        double all300 = ff.at(21, 0);
        check(Math.abs(d6 - Math.exp(-1)) < 0.01 && d30 < 0.01 && r30 > 0.2 && r30 > d30 && all300 < 0.01,
                String.format(java.util.Locale.GERMAN, "Schaum: dicht %.2f nach 6 s, Nachleuchten %.2f nach 30 s (dicht %.3f), vergangen nach 5 min", d6, r30, d30));
        // Schwebung: am Ufer, nicht draußen
        WaveTrain bt = new WaveTrain(4, 0, 200, 20);
        bt.profile = x -> -4 + x / 40.0;
        bt.hs = 1;
        double lo = 9, hi = -9, off = 0;
        for (int k = 0; k < 400; k++) {
            bt.step(0.5);
            double v = bt.surfBeat(155);
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
            off = Math.max(off, Math.abs(bt.surfBeat(0)));
        }
        check(hi - lo > 0.1 * bt.hs && hi - lo < 0.4 * bt.hs && off < 1e-9,
                String.format(java.util.Locale.GERMAN, "Brandungsschwebung am Ufer %.2f m Hub, draußen 0", hi - lo));
        // Bestimmtheit (auch das parallel gebaute Netz)
        SurfBreakers a = new SurfBreakers(), b = new SurfBreakers();
        for (int k = 0; k < 40 * 60; k++) { a.tanks[1].step(1 / 60.0); b.tanks[1].step(1 / 60.0); }
        a.tanks[1].mesh.build(a.tanks[1].train, a.tanks[1].xEnd);
        b.tanks[1].mesh.build(b.tanks[1].train, b.tanks[1].xEnd);
        int n = a.tanks[1].mesh.vertexCount * BreakerMesh.STRIDE;
        check(n == b.tanks[1].mesh.vertexCount * BreakerMesh.STRIDE
                && java.util.Arrays.equals(a.tanks[1].mesh.vertices, 0, n, b.tanks[1].mesh.vertices, 0, n),
                "gleicher Samen, gleiche Brandung (Netz Zahl für Zahl)");
    }

    /** Seit Sylt Phase 7: Auflauf und Rücklauf (Swash). */
    static void schwall() {
        java.util.Locale de = java.util.Locale.GERMAN;
        // ruhender See auf welligem Grund mit trockenem Teil: nichts bewegt sich
        Swash lake = new Swash(3, 0, 40, 0.25, 3, x -> -1 + x / 20.0 + 0.15 * Math.sin(x * 1.3), 0.3);
        lake.infiltration = 0;
        for (int i = 0; i < 600; i++) lake.step(0.1, null);
        double um = 0;
        for (int k = 0; k < lake.d.length; k++) if (lake.d[k] > 1e-3) um = Math.max(um, Math.abs(lake.q[k] / lake.d[k]));
        check(um < 1e-10, String.format(de, "Schwall: ruhender See auf welligem Grund mit trockenem Ufer bleibt in Ruhe (|u| ≤ %.0e m/s nach 60 s)", um));
        // Dammbruch nach Ritter: 1 m gegen trocken
        Swash r = new Swash(1, 0, 200, 0.25, 1, x -> 0, 0);
        r.infiltration = 0;
        r.cf = 0;
        for (int i = 0; i < r.nx; i++) { r.d[i] = r.x(i) < 100 ? 1 : 0; r.q[i] = 0; }
        double v0 = r.volume(0), T = 6, c0 = Math.sqrt(Surf.G);
        for (int i = 0; i < 600; i++) r.step(T / 600, null);
        double worst = 0;
        for (double h : new double[]{0.7, 0.5, 0.3, 0.2}) {
            int tip = 0;
            for (int i = 0; i < r.nx; i++) if (r.d[i] > h) tip = i;
            double ex = 100 + (2 * c0 - 3 * Math.sqrt(Surf.G * h)) * T;
            worst = Math.max(worst, Math.abs(r.x(tip) - ex));
        }
        int tip = 0;
        for (int i = 0; i < r.nx; i++) if (r.d[i] > 0.01) tip = i;
        double front = (r.x(tip) - 100) / ((2 * c0 - 3 * Math.sqrt(Surf.G * 0.01)) * T);
        check(Math.abs(r.depth(0, 100) - 4 / 9.0) < 0.005 && worst < 0.3 && front > 0.85 && front < 1.02 && Math.abs(r.volume(0) / v0 - 1) < 1e-12,
                String.format(de, "Schwall: Dammbruch nach Ritter – am Damm %.4f m (4/9), Profil auf %.2f m, Front (1 cm) bei %.0f %%, Masse erhalten", r.depth(0, 100), worst, front * 100));
        // Strände: 1:20 mit H_s 1 und 0,5, 1:30, flach 1:100
        double[][] cfg = {{1 / 20.0, 3, 8, 1}, {1 / 20.0, 3, 8, 0.5}, {1 / 30.0, 3, 8, 1}, {1 / 100.0, 3, 8, 1}};
        String[] names = {"1:20, H_s 1 m", "1:20, H_s 0,5 m", "1:30, H_s 1 m", "1:100, H_s 1 m"};
        double[] r2s = new double[cfg.length];
        Swash s20 = null;
        WaveTrain t20 = null;
        int[] ev = new int[3];
        for (int c = 0; c < cfg.length; c++) {
            double slope = cfg[c][0], depth0 = cfg[c][1], tp = cfg[c][2], hs = cfg[c][3], flat = 20;
            double xEnd = flat + (depth0 + 2.5 * hs + 0.5) / slope;
            WaveTrain t = new WaveTrain(8, 0, xEnd, 40);
            t.profile = x -> x < flat ? -depth0 : -depth0 + (x - flat) * slope;
            t.hs = hs;
            t.tp = tp;
            t.seed = 11;
            Swash s = new Swash(t, flat + Math.max(0, depth0 - 1.5 * hs - 0.3) / slope, xEnd, 0.5);
            if (c == 0) {
                s.onSwash = (x, y, z, kind, st) -> ev[kind.ordinal()]++;
                s20 = s;
                t20 = t;
            }
            for (int i = 0; i < 60 * 60; i++) { t.step(1 / 60.0); s.step(1 / 60.0, t); }
            s.resetStatistics();
            if (c == 0) java.util.Arrays.fill(ev, 0);
            int spawned = t.spawned;
            double[] vol0 = new double[s.rows], in0 = new double[s.rows], inf0 = new double[s.rows];
            for (int k = 0; k < s.rows; k++) { vol0[k] = s.volume(k); in0[k] = s.inflow(k); inf0[k] = s.infiltrated(k); }
            for (int i = 0; i < 400 * 60; i++) { t.step(1 / 60.0); s.step(1 / 60.0, t); }
            double st = Swash.stockdon(hs / Surf.shoaling(tp, depth0), tp, slope), ratio = s.r2() / st;
            r2s[c] = s.r2();
            check(ratio > 0.7 && ratio < 1.35, String.format(de, "Auflauf %s: R_2%% = %.2f m, Stockdon (2006) %.2f m (%.0f %%)", names[c], s.r2(), st, ratio * 100));
            if (c == 0) {
                int waves = t.spawned - spawned;
                double per = (double) s.runups / s.rows / waves;
                check(per > 0.5 && per <= 1.05 && ev[Swash.Kind.RUNUP.ordinal()] == s.runups,
                        String.format(de, "Auflauf 1:20: %d Spitzen in %d Reihen bei %d Wellen (%.0f %% der Wellen), jede gemeldet", s.runups, s.rows, waves, per * 100));
                check(s.collisions > 0 && ev[Swash.Kind.COLLISION.ordinal()] == s.collisions && ev[Swash.Kind.BORE.ordinal()] == s.bores && s.bores > 0,
                        String.format(de, "Ereignisse: %d Stöße (Rücklauf trifft Walze), %d Walzen am Rand, alle gemeldet", s.collisions, s.bores));
                double worstM = 0;
                for (int k = 0; k < s.rows; k++) {
                    double dv = s.volume(k) - vol0[k], net = (s.inflow(k) - in0[k]) - (s.infiltrated(k) - inf0[k]);
                    worstM = Math.max(worstM, Math.abs(dv - net) / Math.max(1e-9, s.inflow(k) - in0[k] > 0 ? Math.abs(s.inflow(k) - in0[k]) + Math.abs(s.infiltrated(k) - inf0[k]) : 1));
                }
                check(worstM < 1e-6, String.format(de, "Masse im Schwall: Änderung = Zufluss über den Rand − Versickern (Rest %.1e)", worstM));
            }
        }
        check(r2s[1] < r2s[0], String.format(de, "Auflauf wächst mit der Welle: H_s 0,5 m %.2f m < H_s 1 m %.2f m", r2s[1], r2s[0]));
        // Spülsaum: Tang und Schill liegen oben im Bereich der Spitzen
        double wsum = 0, wz = 0;
        for (int k = 0; k < s20.rows; k++)
            for (int i = 0; i < s20.nx; i++) { double w = s20.wrack[k * s20.nx + i]; wsum += w; wz += w * (s20.bed[i] - t20.level); }
        double wMean = wz / Math.max(1e-9, wsum), med = s20.exceed(0.5), hi = s20.exceed(0.01);
        check(wsum > 0 && wMean > med && wMean < hi + 0.05,
                String.format(de, "Spülsaum: Tang und Schill im Mittel %.2f m über Ruhewasser, zwischen dem Median der Spitzen (%.2f m) und R_1%% (%.2f m)", wMean, med, hi));
        // Trocknung: Wellen aus, dann Sonne und Wind
        s20.weather = Swash.Weather.SUNNY_WINDY;
        t20.hs = 1e-4;
        for (int i = 0; i < 90 * 60; i++) { t20.step(1 / 60.0); s20.step(1 / 60.0, t20); }
        double filmUp = 0, low = 1;
        int up = 0;
        double[] m0 = new double[s20.nx];
        int rr = 3;
        for (int i = 0; i < s20.nx; i++) {
            int k = rr * s20.nx + i;
            m0[i] = s20.moist[k];
            double above = s20.bed[i] - s20.groundwater();
            if (above > 0.15 && s20.d[k] <= 0.002) { filmUp = Math.max(filmUp, s20.film[k]); up++; }
            if (above < -0.02 && s20.bed[i] > t20.level - 0.3 && s20.d[k] <= 0.002) low = Math.min(low, s20.moist[k]);
        }
        check(up > 0 && filmUp < 0.15, String.format(de, "Wasserfilm über dem Grundwasser nach 90 s versickert (höchstens %.2f)", filmUp));
        double half = s20.halfLife(s20.weather);
        for (int i = 0; i < (int) Math.round(half * 10); i++) { t20.step(0.1); s20.step(0.1, t20); }
        double worstH = 0;
        int nH = 0;
        for (int i = 0; i < s20.nx; i++) {
            int k = rr * s20.nx + i;
            double eq = s20.equilibrium(s20.bed[i] - s20.groundwater());
            if (m0[i] - eq > 0.2 && s20.d[k] <= 0.002) { worstH = Math.max(worstH, Math.abs((s20.moist[k] - eq) / (m0[i] - eq) - 0.5)); nH++; }
        }
        check(nH > 0 && worstH < 0.03, String.format(de, "nasser Sand trocknet bei Sonne und Wind mit der Halbwertszeit %.0f min (Abweichung %.3f, %d Zellen)", half / 60, worstH, nH));
        check(s20.halfLife(Swash.Weather.CALM_SHADE) > 10 * half && s20.halfLife(new Swash.Weather(1, 10, 20, 3)) > 10 * half,
                String.format(de, "im Schatten bei Windstille %.1f h, bei Regen trocknet nichts", s20.halfLife(Swash.Weather.CALM_SHADE) / 3600));
        double lowAfter = 1;
        for (int i = 0; i < s20.nx; i++) {
            int k = rr * s20.nx + i;
            if (s20.bed[i] - s20.groundwater() < -0.02 && s20.bed[i] > t20.level - 0.3 && s20.d[k] <= 0.002) lowAfter = Math.min(lowAfter, s20.moist[k]);
        }
        check(lowAfter > 0.9, String.format(de, "unter dem Grundwasser bleibt der Sand nass (Nässe ≥ %.2f)", lowAfter));
        // Bestimmtheit und Netz mit Schwall
        SurfBreakers a = new SurfBreakers(), b = new SurfBreakers();
        for (int k = 0; k < 30 * 60; k++) { a.tanks[1].step(1 / 60.0); b.tanks[1].step(1 / 60.0); }
        check(java.util.Arrays.equals(a.tanks[1].swash.d, b.tanks[1].swash.d) && java.util.Arrays.equals(a.tanks[1].swash.moist, b.tanks[1].swash.moist),
                "gleicher Samen, gleicher Schwall (Tiefe und Nässe Zahl für Zahl)");
        SurfBreakers.Tank tk = a.tanks[1];
        tk.mesh.build(tk.train, tk.xEnd, tk.swash);
        tk.bed.updateBed(tk.swash);
        boolean fin = true, idx = true, behind = true;
        double xb = tk.swash.boundaryX();
        int last = (tk.mesh.ns - 1);
        for (int i = 0; i < tk.mesh.vertexCount * BreakerMesh.STRIDE; i++) if (!Float.isFinite(tk.mesh.vertices[i])) fin = false;
        for (int i = 0; i < tk.mesh.indexCount; i++) if (tk.mesh.indices[i] < 0 || tk.mesh.indices[i] >= tk.mesh.vertexCount) idx = false;
        for (int rr2 = 0; rr2 < tk.mesh.ny; rr2++) if (tk.mesh.vertices[(rr2 * tk.mesh.ns + last) * BreakerMesh.STRIDE] < xb) behind = false;
        boolean bedOk = true;
        for (int i = 0; i < tk.bed.vertexCount * BreakerMesh.STRIDE; i++) if (!Float.isFinite(tk.bed.vertices[i])) bedOk = false;
        check(fin && idx && behind && bedOk && tk.bed.ny == tk.train.rows,
                String.format(de, "Netz mit Schwall: %d Ecken je Reihe (%d aus dem Schwall), alle endlich; Sand mit Nässe je Ecke", tk.mesh.ns, tk.swash.nx - tk.swash.cellAt(xb + 1e-6)));
        // Budget: drei Becken der Vorführung mit je 48 Reihen
        double ms = 0;
        for (int k = 0; k < 600; k++) for (SurfBreakers.Tank t : a.tanks) { t.step(1 / 60.0); if (k >= 300) ms += t.swash.ms; }
        ms /= 300;
        check(ms < 6, String.format(de, "Budget: Schwall der drei Becken (je 48 Reihen, Zellen 0,5 m) %.2f ms je Bild auf %d Kernen", ms, Runtime.getRuntime().availableProcessors()));
    }

    /** Wie weit die Linie nach vorn überhängt (m): größter Rücklauf in x. */
    static double overhang(BreakerShape.Curve c) {
        double max = c.x[0], o = 0;
        for (int i = 1; i < c.x.length; i++) { max = Math.max(max, c.x[i]); o = Math.max(o, max - c.x[i]); }
        return o;
    }

    /** H_rms/(H_s/√2), H_1/3/H_rms und Folgekorrelation der Höhen von 4000 hereinkommenden Wellen. */
    static double[] rayleigh(double groups) {
        WaveTrain t = new WaveTrain(2, 0, 400, 10);
        t.profile = x -> -30;
        t.hs = 2;
        t.tp = 8;
        t.groupDepth = groups;
        t.beat = 0;
        java.util.List<Double> hs = new java.util.ArrayList<>();
        int seen = 0;
        while (hs.size() < 4000) {
            t.step(1);
            if (t.spawned > seen) { seen = t.spawned; hs.add(t.waves.get(0).hu); }
        }
        double s2 = 0, m = 0;
        for (double v : hs) { s2 += v * v; m += v; }
        double hrms = Math.sqrt(s2 / hs.size());
        m /= hs.size();
        double[] sorted = hs.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double top = 0;
        int n3 = sorted.length / 3;
        for (int i = sorted.length - n3; i < sorted.length; i++) top += sorted[i];
        double cov = 0, var = 0;
        for (int i = 0; i < hs.size(); i++) { double d = hs.get(i) - m; var += d * d; if (i > 0) cov += d * (hs.get(i - 1) - m); }
        return new double[]{hrms / (t.hs / Math.sqrt(2)), top / n3 / hrms, cov / var};
    }

    /** Phase der Riffbögen über die Rinnen (die erste Rinne bei −20 km + Phase). */
    static double ripPhase(Bars b) { double[] r = b.ripsBetween(-20_001, -19_000); return r.length > 0 ? r[0] + 20_000 : 0; }

    /** Seit Sylt Phase 13: Meeresleuchten nur in warmen Sommernächten, blaugrün, dunkeladaptiert sichtbar. */
    static void meeresleuchten() {
        double aug = com.dan.surf.Bioluminescence.bloom(8, com.dan.surf.Bioluminescence.northSeaTemperature(8));
        double jan = com.dan.surf.Bioluminescence.bloom(1, com.dan.surf.Bioluminescence.northSeaTemperature(1));
        double kalt = com.dan.surf.Bioluminescence.bloom(8, 11);
        check(aug > 0.8 && jan == 0 && kalt == 0, String.format(java.util.Locale.GERMAN, "Meeresleuchten: Blüte im August %.2f, im Januar %.2f, bei 11 °C Wasser %.2f", aug, jan, kalt));
        double[] c = com.dan.surf.Bioluminescence.COLOUR, l = com.dan.surf.Bioluminescence.luminance(1, 1, 1);
        double lum = 0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2];
        check(c[2] > c[1] && c[1] > c[0] && Math.abs(lum - com.dan.surf.Bioluminescence.PEAK) < 1e-12 && lum > 1e-3 && lum < 3e-2,
                String.format(java.util.Locale.GERMAN, "blaugrün (%.2f, %.2f, %.2f), stürzender Kamm %.1e cd/m² (dunkeladaptiert gut sichtbar)", c[0], c[1], c[2], lum));
    }

    /**
     * Seit Lighthouse Phase 17: Grund je Reihe ({@link WaveTrain#rowProfile}) für ein Bauwerk im Meer. Gleicher Grund in
     * allen Reihen gibt dieselben Wellen wie der gemeinsame; eine Reihe über einer Schüttung bricht dort und bleibt
     * dahinter niedrig, eine freie Reihe daneben läuft weiter; an einer Kante bricht keine Welle höher als 1,25·γ·h.
     */
    static void bauwerk() {
        System.out.println("Bauwerk im Meer: Grund je Reihe (seit Lighthouse Phase 17)");
        WaveTrain a = new WaveTrain(8, 0, 80, 20), b = new WaveTrain(8, 0, 80, 20);
        a.profile = x -> -5 + x / 16;
        b.profile = a.profile;
        b.rowProfile = (x, r) -> -5 + x / 16;
        for (WaveTrain t : new WaveTrain[]{a, b}) { t.hs = 1.2; t.tp = 8; }
        double d = 0;
        for (int i = 0; i < 30 * 40; i++) {
            a.step(1 / 30.0);
            b.step(1 / 30.0);
            for (int k = 0; k < Math.min(a.waves.size(), b.waves.size()); k++)
                for (int r = 0; r < 8; r++) d = Math.max(d, Math.abs(a.heightRow(a.waves.get(k), r) - b.heightRow(b.waves.get(k), r)));
        }
        check(d < 1e-3 && a.spawned == b.spawned && java.util.Arrays.equals(a.brokenByType, b.brokenByType),
                String.format(java.util.Locale.GERMAN, "gleicher Grund je Reihe wie der gemeinsame: dieselben Wellen (Abweichung %.1e m)", d));
        WaveTrain c = new WaveTrain(2, 0, 60, 10);
        c.profile = x -> -7;
        c.rowProfile = (x, r) -> r == 0 ? -7 : (x > 25 && x < 30 ? 2 : x > 18 && x < 34 ? -1.0 : -7);
        c.hs = 2.5;
        c.tp = 8;
        c.slopeBase = 3;
        double lee0 = 0, lee1 = 0, hbMax = 0;
        for (int i = 0; i < 30 * 90; i++) {
            c.step(1 / 30.0);
            for (WaveTrain.Wave w : c.waves) {
                if (w.x > 40 && w.x < 50) { lee0 = Math.max(lee0, c.heightRow(w, 0)); lee1 = Math.max(lee1, c.heightRow(w, 1)); }
                if (w.broken(1)) hbMax = Math.max(hbMax, w.hb[1]);
            }
        }
        check(c.brokenByType[Breaker.NONE.ordinal()] == 0 && hbMax > 0.5 && hbMax <= 1.25 * c.gamma * 1.0 + 0.3 && lee1 < 0.6 && lee0 > 1.0,
                String.format(java.util.Locale.GERMAN, "Schüttung in einer Reihe: Brecher bis %.2f m (Kante 1 m tief), dahinter %.2f m gegen %.2f m in der freien Reihe", hbMax, lee1, lee0));
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private SurfCheck() { }
}
