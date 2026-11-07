package com.dan.shutter.demo;

import com.dan.shutter.MotionBlur;
import com.dan.shutter.Pupil;
import com.dan.shutter.Shutter;
import com.dan.shutter.SubFrames;

import java.util.Locale;
import java.util.Random;

/**
 * Prüfung von com.dan.shutter ohne Grafikkarte: Belichtungszeit der Umlaufblende, Öffnungsgang und Flanke, Wagenrad, die
 * Pupille (Fläche, Gleichmaß, Lamellen, Stauchung), die Teilbilder (Schichtung in der Zeit, Fenster), die Scherung (die
 * Schärfenebene steht, der Kreis davor und dahinter ist der der dünnen Linse), die Länge der Bewegungsunschärfe. Endet mit
 * „Alles in Ordnung.“
 */
public final class ShutterCheck {
    static int ok, bad;

    public static void main(String[] a) {
        Locale.setDefault(Locale.GERMAN);
        System.out.println("com.dan.shutter – Prüfung");
        Shutter sh = new Shutter();
        check("Umlaufblende 180° bei 24 B/s belichtet 1/48 s", Math.abs(sh.openS() - 1 / 48.0) < 1e-15 && Math.abs(Shutter.angleFor(1 / 48.0, 24) - 180) < 1e-9,
                "%.6f s", sh.openS());
        check("172,8° bei 25 B/s (Netzfrequenz 50 Hz) belichtet 1/52,08 s", Math.abs(new Shutter(25, 172.8).openS() - 172.8 / 360 / 25) < 1e-15, "%.6f s", new Shutter(25, 172.8).openS());
        double[] r = sh.openRange();
        double span = r[1] - r[0];
        double expect = (Math.atan2(sh.gateWidthMm / 2, sh.gateRadiusMm - sh.gateHeightMm / 2) * 2) / (2 * Math.PI * 24);
        check("Öffnungsgang: die Kante braucht über das Fenster den Winkel, unter dem es von der Achse erscheint", Math.abs(span - expect) / expect < 0.02 && span > 0.002 && span < 0.008,
                "%.2f ms (erwartet %.2f ms, Fenster %.1f × %.1f mm, R = %.0f mm)", 1000 * span, 1000 * expect, sh.gateWidthMm, sh.gateHeightMm, sh.gateRadiusMm);
        check("Öffnungsgang: Bildmitte öffnet bei 0, links und rechts auf entgegengesetzten Seiten", Math.abs(sh.openAt(0, 0)) < 1e-15 && sh.openAt(-0.5, 0) * sh.openAt(0.5, 0) < 0,
                "links %.2f ms, rechts %.2f ms", 1000 * sh.openAt(-0.5, 0), 1000 * sh.openAt(0.5, 0));
        double ramp = sh.rampS(2);
        check("Flanke bei f/2 rund 1 % der Belichtung (die Kante schneidet den Lichtkegel fast im Augenblick)", ramp / sh.openS() < 0.02 && ramp / sh.openS() > 0.005, "%.3f ms = %.2f %%", 1000 * ramp, 100 * ramp / sh.openS());
        // Wagenrad
        double still = Shutter.apparentRevPerS(3, 8, 24), back = Shutter.apparentRevPerS(2.9, 8, 24), fwd = Shutter.apparentRevPerS(0.5, 8, 24);
        check("Wagenrad: 8 Speichen bei 3 U/s und 24 B/s stehen, bei 2,9 U/s laufen sie rückwärts, bei 0,5 U/s wie sie sind",
                Math.abs(still) < 1e-12 && Math.abs(back + 0.1) < 1e-9 && Math.abs(fwd - 0.5) < 1e-12, "%.3f / %.3f / %.3f U/s", still, back, fwd);
        // Pupille
        Random rnd = new Random(7);
        for (Pupil p : new Pupil[]{Pupil.round(), new Pupil(6, 0, 0, 1), new Pupil(7, 0.3, 10, 1), new Pupil(9, 0.6, 0, 2)}) {
            int in = 0, m = 400000;
            double box = 2.2 * p.squeeze;
            for (int i = 0; i < m; i++) if (p.inside((rnd.nextDouble() * 2 - 1) * 2.2, (rnd.nextDouble() * 2 - 1) * box)) in++;
            double area = in / (double) m * 4 * 2.2 * box;
            boolean allIn = true;
            double sx = 0, sy = 0, sr = 0;
            int ns = 20000;
            for (int i = 0; i < ns; i++) {
                double[] q = p.sample(rnd.nextDouble(), rnd.nextDouble());
                allIn &= p.inside(q[0], q[1]);
                sx += q[0]; sy += q[1]; sr += q[0] * q[0] + q[1] * q[1] / (p.squeeze * p.squeeze);
            }
            String nm = p.blades < 3 ? "Kreis" : p.blades + " Lamellen, Rundung " + p.roundness + (p.squeeze != 1 ? ", Stauchung " + p.squeeze : "");
            boolean rr = p.blades >= 3 || Math.abs(sr / ns - 0.5) < 0.01;
            check("Pupille (" + nm + "): Fläche π · Stauchung, alle Punkte innen, Schwerpunkt in der Mitte" + (p.blades < 3 ? ", E[r²] = 1/2" : ""),
                    Math.abs(area / (Math.PI * p.squeeze) - 1) < 0.01 && allIn && Math.hypot(sx / ns, sy / ns) < 0.02 && rr,
                    "Fläche %.4f · π, Schwerpunkt (%.3f, %.3f), E[r²] %.3f", area / Math.PI, sx / ns, sy / ns, sr / ns);
        }
        Pupil hex = new Pupil(6, 0, 0, 1);
        check("Pupille: gerades Sechseck hat 3√3/(2π) der Fläche seines Umkreises", Math.abs(hex.areaFactor() - 3 * Math.sqrt(3) / (2 * Math.PI)) < 1e-4, "%.4f", hex.areaFactor());
        // Gleichmaß der Pupille aus der Folge: in 8 × 8 Feldern des Quadrats je u1, u2 (vor der Abbildung) gleich viele
        SubFrames.Sample[] ss = SubFrames.plan(sh, Pupil.round(), 256, 0, true);
        double[] hist = new double[16];
        for (SubFrames.Sample s : ss) {
            double ang = Math.atan2(s.v(), s.u()), rad = Math.hypot(s.u(), s.v());
            int k = (int) ((ang + Math.PI) / (2 * Math.PI) * 4) % 4, l = Math.min(3, (int) (rad * rad * 4));
            hist[k * 4 + l]++;
        }
        double chi = 0;
        for (double h : hist) chi += (h - 16) * (h - 16) / 16;
        check("Teilbilder: 256 Punkte der Pupille gleichmäßig auf 16 Felder gleicher Fläche (χ² weit unter Zufall ≈ 15)", chi < 6, "χ² = %.2f", chi);
        // Schichtung der Zeit, Fenster
        int n = 64;
        SubFrames.Sample[] s64 = SubFrames.plan(sh, Pupil.round(), n, 17, true);
        double w = r[1] - r[0] + sh.openS();
        boolean sorted = true, strata = true, inWin = true;
        boolean[] seen = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (i > 0) sorted &= s64[i].tauS() >= s64[i - 1].tauS();
            int k = (int) Math.floor((s64[i].tauS() - r[0]) / w * n);
            inWin &= k >= 0 && k < n;
            if (k >= 0 && k < n) { strata &= !seen[k]; seen[k] = true; }
            strata &= Math.abs(s64[i].jx()) < 1 && Math.abs(s64[i].jy()) < 1;
        }
        int inCenter = 0, inEdge = 0;
        double o0 = sh.openAt(0, 0), oe = sh.openAt(0.5, 0);
        for (SubFrames.Sample s : s64) {
            if (s.tauS() >= o0 && s.tauS() < o0 + sh.openS()) inCenter++;
            if (s.tauS() >= oe && s.tauS() < oe + sh.openS()) inEdge++;
        }
        check("Teilbilder: nach der Zeit geordnet, je Streifen der Zeit eines, alle im Fenster; jeder Bildpunkt sieht T/W davon",
                sorted && strata && inWin && Math.abs(inCenter - n * sh.openS() / w) <= 1.5 && Math.abs(inEdge - n * sh.openS() / w) <= 1.5,
                "Mitte %d, Rand %d von %d (erwartet %.1f)", inCenter, inEdge, n, n * sh.openS() / w);
        // Scherung: die Schärfenebene steht, der Kreis davor und dahinter ist der der dünnen Linse
        double f = 50, nN = 2.8, sM = 10, dM = 40, fov = 2 * Math.atan(12 / f);
        float[] proj = persp(fov, 16 / 9.0), view = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        double dMm = f / nN, maxDev = 0, maxShift = 0;
        double[] pFocus = {0.7, -0.4, -sM}, pFar = {0.7, -0.4 * dM / sM, -dM};
        double[] c0 = ndc(proj, view, pFocus);
        for (int i = 0; i < 32; i++) {
            double ang = 2 * Math.PI * i / 32, ex = Math.cos(ang) * dMm / 2000, ey = Math.sin(ang) * dMm / 2000;
            float[] v2 = SubFrames.offsetView(view, ex, ey), p2 = SubFrames.shearProjection(proj, ex, ey, sM, 0, 0, 1920, 1080);
            double[] c = ndc(p2, v2, pFocus);
            maxDev = Math.max(maxDev, Math.hypot(c[0] - c0[0], c[1] - c0[1]));
            double[] cf = ndc(p2, v2, pFar), cf0 = ndc(proj, view, pFar);
            // NDC: Höhe 2 = 24 mm, Breite 2 = 24 · 16/9 mm auf dem Sensor
            maxShift = Math.max(maxShift, Math.hypot((cf[0] - cf0[0]) * 12 * 16 / 9.0, (cf[1] - cf0[1]) * 12));
        }
        double diaMm = 2 * maxShift;
        double thin = dMm * f * (dM * 1000 - sM * 1000) / (dM * 1000 * (sM * 1000 - f));
        check("Scherung: die Schärfenebene steht in allen Teilbildern, der Kreis in 40 m ist der der dünnen Linse (50 mm f/2,8 auf 10 m)",
                maxDev < 1e-6 && Math.abs(diaMm / thin - 1) < 0.01, "Abweichung %.1e, Kreis %.4f mm gegen %.4f mm", maxDev, diaMm, thin);
        // Bewegungsunschärfe
        MotionBlur mb = new MotionBlur();
        check("Bewegungsunschärfe: bei 60 Bildern/s am Bildschirm und 1/48 s ist sie 1,25 Wege zwischen zwei Bildern; ohne Szenenzeit keine",
                Math.abs(mb.scale(sh.openS(), 1 / 60.0) - 1.25) < 1e-12 && mb.scale(sh.openS(), 0) == 0 && mb.tileFor(2160) == 40,
                "%.3f, Kachel bei 2160 Zeilen %d", mb.scale(sh.openS(), 1 / 60.0), mb.tileFor(2160));
        check("Bewegungsunschärfe: 1 000 Bildpunkte je Sekunde bei 1/48 s sind 20,8 Bildpunkte", Math.abs(sh.blurPx(1000) - 1000 / 48.0) < 1e-9, "%.2f", sh.blurPx(1000));
        aberration();
        System.out.println(sh.describe());
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " von " + (ok + bad) + " Prüfungen fehlgeschlagen.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Seit W9c Runde 2: sphärische Aberration und Längsfarbfehler je Teilbild (com.dan.shutter.Aberration). */
    static void aberration() {
        // 1 reine Unschärfe als „Aberration“ (TA = β ρ): scharfgestellt auf den kleinsten Fleck bleibt nichts, paraxial ist es
        //   genau eine andere Schärfe: der Versatz −β (u, v) wie die Scherung für s', f · e · (1/s' − 1/s) = −β ρ̂
        double beta = 0.05;
        double[] ta = new double[33];
        for (int i = 0; i < 33; i++) ta[i] = beta * i / 32.0;
        com.dan.shutter.Aberration a = new com.dan.shutter.Aberration(ta, 0, 0, 25, 50);
        double[] o = new double[2];
        a.shiftMm(0.6, -0.3, 1, 1, o);
        double best = Math.hypot(o[0], o[1]);
        a.bestFocus = false;
        a.shiftMm(0.6, -0.3, 1, 1, o);
        double[] l1 = a.landingMm(40, 10, 50, 0.6, -0.3, 1, 1);
        // dieselbe Landung ohne Aberration bei einer Schärfe s' mit f · (D/2) · (1/s' − 1/s) = −β (mm)
        double sp = 1 / (1 / 10000.0 - beta / (50 * 12.5)) / 1000;
        com.dan.shutter.Aberration n0 = new com.dan.shutter.Aberration(new double[33], 0, 0, 25, 50);
        double[] l2 = n0.landingMm(40, sp, 50, 0.6, -0.3, 1, 1);
        check("Aberration: reine Unschärfe β ρ ist scharfgestellt nichts, paraxial genau eine andere Schärfe (dünne Linse)",
                best < 1e-6 && Math.abs(o[0] + beta * 0.6) < 1e-12 && Math.hypot(l1[0] - l2[0], l1[1] - l2[1]) < 1e-9,
                "beste %.1e mm, Landung %.6f/%.6f gegen %.6f/%.6f mm (s' = %.3f m)", best, l1[0], l1[1], l2[0], l2[1], sp);
        // 2 sphärische Aberration a ρ³: bester Fleck α = 2a/3, RMS a/√... kleiner als paraxial
        double am = 0.03;
        for (int i = 0; i < 33; i++) ta[i] = am * Math.pow(i / 32.0, 3);
        com.dan.shutter.Aberration s3 = new com.dan.shutter.Aberration(ta, 0, 0, 25, 50);
        double al = s3.bestAlpha(), rp = s3.rmsSpotMm(false), rb = s3.rmsSpotMm(true);
        // geschlossen: ∫ (aρ³ − αρ)² 2ρ dρ mit α = 2a/3: a² (1/4 − 2·(2/3)/3 + (4/9)/2) = a² / 36 … RMS a/6; paraxial a/2
        check("Aberration: TA = a ρ³ – kleinster Fleck bei α = 2a/3, RMS a/6 statt a/2 (paraxial)", Math.abs(al - 2 * am / 3) < 2e-3 * am
                && Math.abs(rb - am / 6) < 2e-4 && Math.abs(rp - am / 2) < 2e-4, "α %.5f (%.5f), RMS %.5f (%.5f) und %.5f (%.5f) mm", al, 2 * am / 3, rb, am / 6, rp, am / 2);
        // 3 Helligkeit im Kreis aus den Orten der Pupille gegen das Histogramm der Landung (wie com.dan.lens, saProfile)
        double S = -1.0, bins = 20;   // mm je Einheit der Pupille (vor der Schärfe)
        double[] h1 = new double[(int) bins + 6], h2 = new double[(int) bins + 6];
        SubFrames.Sample[] pl = SubFrames.plan(new Shutter(24, 180), Pupil.round(), 20000, 7, false);
        double rmax = 0;
        for (int k = 0; k <= 400; k++) { double r = k / 400.0; rmax = Math.max(rmax, Math.abs(S * r - (s3.ta(r) - al * r))); }
        for (SubFrames.Sample q : pl) {
            s3.shiftMm(q.u(), q.v(), 1, 1, o);
            double x = S * q.u() + o[0], y = S * q.v() + o[1], r = Math.hypot(x, y) / rmax;
            int b = (int) Math.min(h1.length - 1, Math.floor(r * bins));
            h1[b] += 1.0 / pl.length;
        }
        int nr = 200000;
        for (int k = 0; k < nr; k++) {
            double rho = (k + 0.5) / nr, r = Math.abs(S * rho - (s3.ta(rho) - al * rho)) / rmax;
            int b = (int) Math.min(h2.length - 1, Math.floor(r * bins));
            h2[b] += 2 * rho / nr;
        }
        double dev = 0;
        for (int b = 0; b < bins; b++) dev = Math.max(dev, Math.abs(h1[b] - h2[b]));
        check("Aberration: die Orte der Pupille, je Teilbild verschoben, geben die Helligkeit im Kreis wie die Landung der Strahlen", dev < 0.01,
                "größte Abweichung je Ring %.4f (Ringe zu 1/%d des Halbmessers)", dev, (int) bins);
        // 4 Längsfarbfehler: Blau 0,14 mm vor Grün, 50 mm, Pupille 25 mm: −dz (D/2)/v je Einheit der Pupille
        com.dan.shutter.Aberration c = new com.dan.shutter.Aberration(new double[33], 0.063, -0.14, 25, 50);
        double[] sb = new double[2], sr = new double[2];
        c.shiftMm(1, 0, 1, 2, sb);
        c.shiftMm(1, 0, 1, 0, sr);
        check("Aberration: Längsfarbfehler am Rand der Pupille: Blau +dz·(D/2)/v nach außen, Rot nach innen (wie ein näherer und ein fernerer Fokus)",
                Math.abs(sb[0] - 0.14 * 12.5 / 50) < 1e-12 && Math.abs(sr[0] + 0.063 * 12.5 / 50) < 1e-12 && Math.abs(c.chromaticPx(2, 0.01) + 3.5) < 1e-9,
                "Blau %+.4f, Rot %+.4f mm; Blau %.1f Bildpunkte je Einheit bei 10 µm", sb[0], sr[0], c.chromaticPx(2, 0.01));
    }

    static float[] persp(double fovy, double aspect) {
        double t = 1 / Math.tan(fovy / 2), n = 0.3, fa = 1e5;
        float[] p = new float[16];
        p[0] = (float) (t / aspect);
        p[5] = (float) t;
        p[10] = (float) (-(fa + n) / (fa - n));
        p[11] = -1;
        p[14] = (float) (-2 * fa * n / (fa - n));
        return p;
    }

    static double[] ndc(float[] proj, float[] view, double[] x) {
        double[] v = new double[4], c = new double[4];
        for (int i = 0; i < 4; i++) v[i] = view[i] * x[0] + view[4 + i] * x[1] + view[8 + i] * x[2] + view[12 + i];
        for (int i = 0; i < 4; i++) c[i] = proj[i] * v[0] + proj[4 + i] * v[1] + proj[8 + i] * v[2] + proj[12 + i] * v[3];
        return new double[]{c[0] / c[3], c[1] / c[3]};
    }

    static void check(String what, boolean pass, String fmt, Object... v) {
        if (pass) ok++;
        else bad++;
        System.out.println((pass ? "  ok    " : "  FEHLER ") + what + (fmt.isEmpty() ? "" : ": " + String.format(Locale.GERMAN, fmt, v)));
    }

    private ShutterCheck() { }
}
