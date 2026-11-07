package com.dan.lens.demo;

import com.dan.lens.Aperture;
import com.dan.lens.Coating;
import com.dan.lens.FocusPuller;
import com.dan.lens.Glass;
import com.dan.lens.Lens;
import com.dan.lens.LensSystem;
import com.dan.lens.Spectrum;

import java.util.Locale;

/**
 * Selbsttest von com.dan.lens ohne Grafikkarte: Glas und Vergütung gegen die Formeln, der Doppel-Gauß gegen Smith, die
 * paraxiale Rechnung gegen echte Strahlen, das Katzenauge der Fassungen gegen den Durchgang echter Strahlen, die
 * Helligkeit im Unschärfekreis (sphärische Aberration) gegen ein Strahlenbündel, der Stern gegen die Fourier-Transformierte
 * der Blende (Fraunhofer), Schärfentiefe, Schärfezieher. Endet mit „Alles in Ordnung.“
 */
public final class LensCheck {
    private static int ok, bad;

    public static void main(String[] a) {
        Locale.setDefault(Locale.GERMAN);
        System.out.println("com.dan.lens – Prüfung");

        // ---- Glas
        Glass bk7 = Glass.BK7;
        check("Glas: n(587,56 nm) = n_d", Math.abs(bk7.n(Glass.LAMBDA_D) - bk7.nd()) < 1e-9, "%.5f", bk7.n(Glass.LAMBDA_D));
        double dn = bk7.n(Glass.LAMBDA_F) - bk7.n(Glass.LAMBDA_C);
        check("Glas: n_F − n_C = (n_d − 1)/V_d", Math.abs(dn - (bk7.nd() - 1) / bk7.vd()) < 1e-9, "%.5f (BK7: 0,00805)", dn);
        check("Glas: Blau bricht stärker als Rot", bk7.n(450) > bk7.n(650), "%.4f gegen %.4f", bk7.n(450), bk7.n(650));

        // ---- Vergütung
        Coating mg = Coating.mgf2(550);
        double r550 = mg.reflectance(1, 1.5168, 550), r0 = Coating.NONE.reflectance(1, 1.5168, 550);
        double rTheo = Math.pow((1.5168 - 1.38 * 1.38) / (1.5168 + 1.38 * 1.38), 2);
        check("Vergütung: λ/4-MgF₂ auf BK7 bei 550 nm wie ((n_g − n_f²)/(n_g + n_f²))²", Math.abs(r550 - rTheo) < 1e-6, "%.3f %% (Formel %.3f %%)", 100 * r550, 100 * rTheo);
        check("Vergütung: unvergütet Fresnel 4,2 %", Math.abs(r0 - 0.0422) < 0.001, "%.2f %%", 100 * r0);
        check("Vergütung: Rot und Blau stärker zurück als Grün (purpurne Reflexe)", mg.reflectance(1, 1.5168, 440) > r550 && mg.reflectance(1, 1.5168, 680) > r550,
                "440 nm %.2f %%, 680 nm %.2f %%", 100 * mg.reflectance(1, 1.5168, 440), 100 * mg.reflectance(1, 1.5168, 680));

        // ---- Doppel-Gauß
        LensSystem s = LensSystem.doubleGauss50(mg);
        double ld = Glass.LAMBDA_D, f = s.efl(ld);
        check("Doppel-Gauß: Brennweite 50 mm (Smith, auf 50 mm gebracht)", Math.abs(f - 50) < 1.0, "%.2f mm", f);
        check("Doppel-Gauß: volle Öffnung f/2", Math.abs(s.fNumberMin(ld) - 2) < 0.1, "f/%.2f", s.fNumberMin(ld));
        check("Doppel-Gauß: 45 Geisterbilder (10 Flächen ohne Blende)", s.ghosts(550, s.back() + s.bfl(550)).size() == 45, "%d", s.ghosts(550, s.back() + s.bfl(550)).size());
        double zInf = s.back() + s.bfl(550);
        double ta = s.transverse(0.05 * s.entrancePupilRadius(550), 550, zInf);
        check("Paraxial = echt: achsnaher Strahl trifft den paraxialen Brennpunkt", Math.abs(ta) < 2e-5, "%.2e mm", ta);
        double h1 = s.chiefHeight(Math.toRadians(1), 550, zInf), p1 = s.efl(550) * Math.tan(Math.toRadians(1));
        check("Paraxial = echt: Hauptstrahl bei 1°", Math.abs(h1 / p1 - 1) < 5e-4, "%.5f gegen %.5f mm", h1, p1);
        double dist20 = s.chiefHeight(Math.toRadians(20), 550, zInf) / (s.efl(550) * Math.tan(Math.toRadians(20))) - 1;
        check("Verzeichnung bei 20° klein (Doppel-Gauß fast symmetrisch)", Math.abs(dist20) < 0.03, "%.2f %%", 100 * dist20);
        double br = s.imageScale(s.imageZ(1000, 550), 550) / s.imageScale(zInf, 550);
        check("Fokusatmung: auf 1 m wird der Bildwinkel 3 bis 7 % enger", br > 1.03 && br < 1.07, "%.3f", br);
        double lca = s.bfl(Spectrum.RGB_NM[2]) - s.bfl(Spectrum.RGB_NM[0]);
        check("Längsfarbfehler klein (achromatisiert): |Blau − Rot| unter 0,3 mm", Math.abs(lca) < 0.3, "%.3f mm", lca);

        // ---- Katzenauge: Kreise der Fassungen (paraxial) gegen echte Strahlen
        double rStop = s.surfaces.get(s.stop).semiAperture();
        double[][] mc = s.mountCircles(rStop, 550);
        for (double deg : new double[]{10, 18}) {
            double th = Math.toRadians(deg);
            int in = 0, all = 0;
            for (int j = 0; j < 80; j++)
                for (int i = 0; i < 80; i++) {
                    double x = (i + 0.5) / 40 - 1, y = (j + 0.5) / 40 - 1;
                    if (x * x + y * y > 1) continue;
                    all++;
                    boolean pass = true;
                    for (double[] c : mc) if (c != null && Math.hypot(x - c[1] * th, y) > c[0]) pass = false;
                    if (pass) in++;
                }
            double model = in / (double) all, real = s.vignetting(th, rStop, 550, zInf, 60);
            check(String.format("Katzenauge bei %.0f°: Kreise der Fassungen wie echte Strahlen", deg), Math.abs(model - real) < 0.08, "Kreise %.3f, Strahlen %.3f", model, real);
        }

        // ---- sphärische Aberration im Unschärfekreis: Strahlenbündel gegen I(s) = 1 − 4ε3(s² − ½) − 6ε5(s⁴ − ⅓)
        Lens lens = new Lens();
        lens.focalMm = 50;
        lens.fNumber = 2;
        Lens.Optics o = lens.optics(5);
        double zs = s.imageZ(5000, 550);
        double rep = s.entrancePupilRadius(550);
        for (double objM : new double[]{2.5, 30}) {
            // Bündel aus dem Punkt auf der Achse durch die Pupille, Verteilung der Auftreffradien
            int n = 0, outer = 0;
            double[] rr = new double[40000];
            double rmax = 0;
            double zep = s.entrancePupilZ(550);
            for (int j = 0; j < 200; j++)
                for (int i = 0; i < 200; i++) {
                    double x = (i + 0.5) / 100 - 1, y = (j + 0.5) / 100 - 1;
                    if (x * x + y * y > 0.98) continue;
                    double[] org = {0, 0, -objM * 1000};
                    double[] tgt = {x * rep, y * rep, zep};
                    double[] d = {tgt[0] - org[0], tgt[1] - org[1], tgt[2] - org[2]};
                    double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                    d = new double[]{d[0] / l, d[1] / l, d[2] / l};
                    double[] hit = s.realTrace(org, d, 550, zs, true, rStop);
                    if (hit == null) continue;
                    double r = Math.hypot(hit[0], hit[1]);
                    rr[n++] = r;
                    rmax = Math.max(rmax, r);
                }
            for (int k = 0; k < n; k++) if (rr[k] > 0.8 * rmax) outer++;
            double fracReal = outer / (double) n;
            double c = o.cocMm(objM);
            // Vorhersage aus der Tabelle (wie im Shader): ∫_0,8^1 I(s) 2s ds
            double pred = 0;
            for (int k = 0; k < 2000; k++) {
                double sv = 0.8 + 0.2 * (k + 0.5) / 2000;
                pred += o.saWeight(sv, c) * 2 * sv * 0.2 / 2000;
            }
            boolean same = Math.abs(fracReal - pred) < 0.08;
            check(String.format("Bokeh %s dem Fokus (%.1f m): Helligkeit am Rand wie im Strahlenbündel", objM < 5 ? "vor" : "hinter", objM), same,
                    "äußere 20 %% des Radius: Strahlen %.3f, Formel %.3f (gleichmäßig 0,360)", fracReal, pred);
        }

        // ---- Blende und Stern
        Aperture hex = new Aperture(6, 0, 0);
        check("Blende: Sechseck mit geraden Kanten hat 3√3/(2π) der Kreisfläche", Math.abs(hex.areaFactor() - 3 * Math.sqrt(3) / (2 * Math.PI)) < 1e-3, "%.4f", hex.areaFactor());
        check("Blende: 6 Lamellen → 6 Strahlen, 7 → 14", hex.spikeAngles().length == 6 && new Aperture(7, 0, 0).spikeAngles().length == 14, "%d, %d", hex.spikeAngles().length, new Aperture(7, 0, 0).spikeAngles().length);
        check("Blende: rund ohne Strahlen", new Aperture(9, 1, 0).spikeAngles().length == 0 && new Aperture(0, 0, 0).spikeAngles().length == 0, "");
        // Fraunhofer: Energie längs eines Strahls zwischen den Frequenzen k1 und k2 gegen n_e·L/(4π²A)·N·(1/k1 − 1/k2)
        int N = 512, M = 128;
        final double BAND = 12.5;
        double[] re = new double[N * N], im = new double[N * N];
        float[] mask = hex.mask(M);
        for (int j = 0; j < M; j++) for (int i = 0; i < M; i++) re[(j + (N - M) / 2) * N + i + (N - M) / 2] = mask[j * M + i];
        fft2(re, im, N);
        double total = 0;
        for (int k = 0; k < N * N; k++) total += re[k] * re[k] + im[k] * im[k];
        double ang = hex.spikeAngles()[0], k1 = 24, k2 = 96, spike = 0;
        for (int v = 0; v < N; v++)
            for (int u = 0; u < N; u++) {
                int fu = u < N / 2 ? u : u - N, fv = v < N / 2 ? v : v - N;
                double along = fu * Math.cos(ang) + fv * Math.sin(ang), across = -fu * Math.sin(ang) + fv * Math.cos(ang);
                if (along >= k1 && along < k2 && Math.abs(across) <= BAND) spike += re[v * N + u] * re[v * N + u] + im[v * N + u] * im[v * N + u];
            }
        double frac = spike / total;
        double L = hex.straightEdge() * M / 2, A = Math.PI * Math.pow(M / 2.0, 2) * hex.areaFactor();
        // im Band ±BAND quer liegt der Anteil ∫ sinc²(x) dx über ±BAND·L/N der Hauptkeule und Nebenkeulen
        double inBand = 0, xw = BAND * L / N;
        for (int k = 0; k < 4000; k++) { double x = -xw + 2 * xw * (k + 0.5) / 4000, sx = Math.sin(Math.PI * x) / (Math.PI * x); inBand += sx * sx * 2 * xw / 4000; }
        double theo = hex.edgesPerSpike() * L / (4 * Math.PI * Math.PI * A) * N * (1 / k1 - 1 / k2) * inBand;
        check("Stern: Energie im Strahl wie Fraunhofer (FFT der Blende)", Math.abs(frac / theo - 1) < 0.25, "FFT %.2e, Formel %.2e (%.0f %%)", frac, theo, 100 * frac / theo);

        // ---- Schärfentiefe
        Lens.Optics q = lens.optics(10);
        double[] dof = q.dofM(0.03);
        check("Schärfentiefe: Kreis an den Grenzen = zulässiger Kreis", Math.abs(Math.abs(q.cocMm(dof[0])) - 0.03) < 1e-3 && (Double.isInfinite(dof[1]) || Math.abs(q.cocMm(dof[1]) - 0.03) < 1e-3),
                "nah %.2f m, fern %s", dof[0], Double.isInfinite(dof[1]) ? "∞" : String.format("%.1f m", dof[1]));
        check("Schärfentiefe: im Fokus scharf, dahinter positiv, davor negativ", Math.abs(q.cocMm(10)) < 1e-9 && q.cocMm(50) > 0 && q.cocMm(3) < 0, "%.3f / %.3f mm", q.cocMm(50), q.cocMm(3));

        // ---- Schärfezieher
        FocusPuller fp = new FocusPuller();
        fp.snap(20);
        double over = 0;
        double t = 0, at = Double.NaN;
        while (t < 3) {
            double m = fp.step(1 / 120.0, 2);
            t += 1 / 120.0;
            over = Math.max(over, 1 / m - 1 / 2.0);
            if (Double.isNaN(at) && Math.abs(1 / m - 0.5) < 0.02 * (0.5 - 0.05)) at = t;
        }
        check("Schärfezieher: von 20 m auf 2 m ohne Überschwingen, 98 % nach der eingestellten Zeit", over <= 1e-9 && at > 0.5 * fp.timeS && at < 1.6 * fp.timeS, "98 %% nach %.2f s (Zeit %.2f s)", at, fp.timeS);

        // ---- die beiden anderen Schnitte (W9a Runde 2, WF41 A)
        LensSystem wa = LensSystem.wideAngle(Coating.NONE), te = LensSystem.telephoto250(Coating.NONE);
        double fw = wa.efl(ld), zw = wa.back() + wa.bfl(550);
        double dw = wa.chiefHeight(Math.toRadians(38), 550, zw) / (wa.efl(550) * Math.tan(Math.toRadians(38))) - 1;
        check("Weitwinkel (Nakamura): f um 15,5 mm, f/1,9, bei 38° noch ein echter Hauptstrahl mit Tonne", Math.abs(fw - 15.5) < 0.3
                && Math.abs(wa.fNumberMin(ld) - 1.9) < 0.15 && dw < -0.01 && dw > -0.12, "f = %.2f mm, f/%.2f, Verzeichnung bei 38° %.1f %%", fw, wa.fNumberMin(ld), 100 * dw);
        double ft = te.efl(ld), zt = te.back() + te.bfl(550), lengthT = zt - te.z[0];
        check("Tele: f = 250 mm, Baulänge kürzer als die Brennweite (Televerhältnis unter 1)", Math.abs(ft - 250) < 2.5 && lengthT / ft < 1,
                "f = %.1f mm, f/%.2f, Televerhältnis %.2f", ft, te.fNumberMin(ld), lengthT / ft);
        Lens auto = new Lens();
        auto.background = false;
        auto.focalMm = 24;
        Lens.Design d24 = auto.designJetzt();
        Lens.Optics o24 = auto.optics(5);
        boolean fin = true;
        for (double v : o24.relIllum()) fin &= Double.isFinite(v) && v > 0;
        auto.focalMm = 200;
        Lens.Design d200 = auto.designJetzt();
        Lens.Optics o200 = auto.optics(50);
        auto.focalMm = 85;
        check("Schnitt nach der Brennweite: 24 mm Weitwinkel, 85 mm Doppel-Gauß, 200 mm Tele; Randabfall ohne Lücken", d24 == Lens.Design.WIDE
                && auto.designJetzt() == Lens.Design.DOUBLE_GAUSS && d200 == Lens.Design.TELE && fin && Math.abs(o200.efl() - 200) < 2,
                "%s, %s, %s; Ecke bei 24 mm %.2f", d24.label, auto.designJetzt().label, d200.label, o24.relIllum()[o24.relIllum().length - 1]);
        // ---- Geisterbilder: Energie
        Lens.Optics g = lens.optics(1e5);
        float[] gd = g.ghostData();
        double sum = 0;
        for (int k = 0; k < g.ghostCount(); k++) sum += gd[k * 12 + 9] * Math.PI * gd[k * 12 + 5] * gd[k * 12 + 5];
        double prim = 4 * g.fNumber() * g.fNumber() * Math.PI * Math.pow(g.pupilMm() / 2, 2) / Math.PI;   // Fluss der Sonne durch die Pupille, gleich gerechnet
        check("Geisterbilder: zusammen unter 1 % des Lichts der Sonne", sum / prim < 0.01, "%.3f %%", 100 * sum / prim);

        tropfen();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " von " + (ok + bad) + " Prüfungen fehlgeschlagen.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Seit W9c Runde 2: Tropfen auf der Frontlinse (FrontDrops). */
    static void tropfen() {
        com.dan.lens.FrontDrops d = new com.dan.lens.FrontDrops();
        double t = d.transmitted(), dm = Math.toDegrees(d.maxDeflection());
        check("Tropfen (60° auf Glas): über arcsin(1/n) = 48,6° ganz zurückgeworfen – es geht (0,75/0,866)² durch, abgelenkt bis 41,4°",
                Math.abs(t - Math.pow((1 / 1.333) / Math.sin(Math.toRadians(60)), 2)) < 1e-12 && Math.abs(dm - 41.4) < 0.1, "durch %.4f, größte Ablenkung %.2f°", t, dm);
        double[] ph = d.phase(32);
        double sum = 0, mean = 0, dmax = d.maxDeflection();
        for (int k = 0; k < 32; k++) {
            double d0 = dmax * k / 32, d1 = dmax * (k + 1) / 32, om = 2 * Math.PI * (Math.cos(d0) - Math.cos(d1));
            sum += ph[k] * om;
            mean += ph[k] * om * 0.5 * (d0 + d1);
        }
        check("Tropfen: die Verteilung der Ablenkung trägt genau das Durchgehende; im Mittel um 14°", Math.abs(sum - t) < 1e-9 && Math.toDegrees(mean / sum) > 12 && Math.toDegrees(mean / sum) < 25,
                "Σ p ΔΩ = %.6f (%.6f), mittlere Ablenkung %.1f°", sum, t, Math.toDegrees(mean / sum));
        d.set(new double[]{0}, new double[]{0}, new double[]{1.0}, 1);
        double c1 = d.coverAt(0, 0, 12), c2 = d.coverAt(12, 0, 12);
        check("Tropfen: einer von 1 mm in der Mitte deckt (1/12)² des Bündels einer Pupille von 12 mm, auf dem Rand des Bündels knapp halb so viel",
                Math.abs(c1 - 1 / 144.0) < 1e-12 && Math.abs(c2 - 0.5 / 144) < 0.05 / 144, "%.5f (%.5f), am Rand %.5f", c1, 1 / 144.0, c2);
        // gleiche Tropfen auf Tele (Pupille 23 mm hinter 15 mm, Feld ±4°) und Weitwinkel (Pupille 4,1 mm hinter 12 mm, Feld ±38°)
        java.util.Random r = new java.util.Random(7);
        int n = 25;
        double[] x = new double[n], y = new double[n], rr = new double[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * r.nextDouble(), q = 11 * Math.sqrt(r.nextDouble());
            x[i] = q * Math.cos(a); y[i] = q * Math.sin(a); rr[i] = 0.4 + 1.2 * r.nextDouble();
        }
        d.set(x, y, rr, n);
        float[] tele = d.coverage(48, 27, 24.9, 14.0, 250, 15.3, 23.0), weit = d.coverage(48, 27, 24.9, 14.0, 15.5, 12.1, 4.1);
        double[] st = minMaxMean(tele), sw = minMaxMean(weit);
        check("Tropfen: beim Tele ein gleichmäßiger Schleier, beim Weitwinkel einzelne Flecken (gleiche 25 Tropfen)",
                (st[1] - st[0]) / st[2] < 0.1 && (sw[1] - sw[0]) / sw[2] > 1.5, "Tele %.3f … %.3f, Weitwinkel %.3f … %.3f", st[0], st[1], sw[0], sw[1]);
    }

    static double[] minMaxMean(float[] g) {
        double lo = 1e9, hi = -1e9, s = 0;
        for (float v : g) { lo = Math.min(lo, v); hi = Math.max(hi, v); s += v; }
        return new double[]{lo, hi, s / g.length};
    }

    private static void check(String what, boolean pass, String fmt, Object... v) {
        if (pass) ok++;
        else bad++;
        System.out.println((pass ? "  ok    " : "  FEHLER ") + what + (fmt.isEmpty() ? "" : ": " + String.format(Locale.GERMAN, fmt, v)));
    }

    /** 2D-FFT (Zeilen, dann Spalten), N Zweierpotenz. */
    static void fft2(double[] re, double[] im, int n) {
        double[] r = new double[n], i = new double[n];
        for (int y = 0; y < n; y++) {
            System.arraycopy(re, y * n, r, 0, n);
            System.arraycopy(im, y * n, i, 0, n);
            fft(r, i);
            System.arraycopy(r, 0, re, y * n, n);
            System.arraycopy(i, 0, im, y * n, n);
        }
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) { r[y] = re[y * n + x]; i[y] = im[y * n + x]; }
            fft(r, i);
            for (int y = 0; y < n; y++) { re[y * n + x] = r[y]; im[y * n + x] = i[y]; }
        }
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double a = -2 * Math.PI / len, wr = Math.cos(a), wi = Math.sin(a);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int p = i + k, q = p + len / 2;
                    double tr = re[q] * cr - im[q] * ci, ti = re[q] * ci + im[q] * cr;
                    re[q] = re[p] - tr; im[q] = im[p] - ti;
                    re[p] += tr; im[p] += ti;
                    double nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
    }

    private LensCheck() { }
}
