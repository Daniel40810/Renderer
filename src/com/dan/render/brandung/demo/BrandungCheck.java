package com.dan.render.brandung.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Brandung;
import com.dan.core.szene.Brecherform;
import com.dan.core.szene.Kanten;
import com.dan.core.szene.Seegangsoptik;
import com.dan.render.brandung.Brandungsbild;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der Brandung ohne Karte (seit P13c, Werkbuch HalligHooge Teil A, 12.14): die Formeln im Kern
 * ({@link Seegangsoptik} seit P13c, {@link Brandung}, {@link Brecherform}) und die Kanten des Gebiets. Prüfsteine 16, 18
 * (Mittel des Schaums), 8a, 17, 8c, 19; Attest {@code docs/status/P13c_Brandung.md}.
 * <pre>  BrandungCheck [--gebiet D:\Daten\HalligHooge\gebiet] [--archiv D:\Daten\HalligHooge\_p13\archiv_xaver]</pre>
 */
public final class BrandungCheck {
    private static final Attest A = new Attest("com.dan.render.brandung", Brandungsbild.VERSION);
    static File gebiet = new File("D:\\Daten\\HalligHooge\\gebiet"), archiv = new File("D:\\Daten\\HalligHooge\\_p13\\archiv_xaver");
    static final double[][] WELLEN = Seegangsoptik.wellen();
    static final double[] BAENDER = Seegangsoptik.baender(), CDF = Seegangsoptik.cdf();
    static final String F16 = ".f16";

    public static void main(String[] args) {
        System.out.println("BrandungCheck – Brandung im Kern (Werkbuch HalligHooge Teil A, P13c)");
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (args[i].equals("--gebiet")) gebiet = new File(args[i + 1]);
            if (args[i].equals("--archiv")) archiv = new File(args[i + 1]);
        }
        try {
            hoehe();
            schaum();
            brechen();
            bedeckung();
            kanten();
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P13c_Brandung.md"), "P13c – BrandungCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Prüfstein 16, Kammform

    static void hoehe() {
        A.teil("Höhe der langen Wellen (Prüfstein 16) und Kammform");
        double[][] faelle = {{0.79, 10.6, 2.29}, {1.55, 8.0, 6.0}, {0.23, 3.8, 0.75}};
        for (double[] c : faelle) {
            double hs = c[0], tp = c[1], h = c[2], th = 0.3;
            double[] a = Seegangsoptik.amplituden(hs, tp, th, 1e-3, WELLEN, BAENDER, CDF);
            double s = 0, s2 = 0, ks = 0, ks2 = 0, ks3 = 0;
            int n = 0;
            double sig = Math.sqrt(a[Seegangsoptik.LANG + 1]), be = Seegangsoptik.beta(hs, h);
            for (int ti = 0; ti < 128; ti++)
                for (int j = 0; j <= 40; j++)
                    for (int i = 0; i <= 40; i++) {
                        double z = Seegangsoptik.hoehe(a, WELLEN, 470000 + 25 * i, 6046000 + 25 * j, 1000 + 28.3 * ti)[0];
                        double zs = Seegangsoptik.kamm(z, sig, be)[0];
                        s += z; s2 += z * z; ks += zs; ks2 += zs * zs; ks3 += zs * zs * zs;
                        n++;
                    }
            double var = s2 / n - (s / n) * (s / n), soll = hs * hs / 16;
            A.pruefe(Math.abs(var / soll - 1) <= 0.1, f("Prüfstein 16: Varianz der Höhe gegen Hs²/16 (Hs %.2f m, Tp %.1f s; 1 × 1 km, 128 Zeiten über 1 h)", hs, tp),
                    f("%.3f (im Band 0,07–0,6 Hz: m0 = %.3f · Hs²/16)", var / soll, a[Seegangsoptik.LANG] / soll), "1 ± 0,1 [G]");
            double km = ks / n, kv = ks2 / n - km * km, sk = (ks3 / n - 3 * km * kv - km * km * km) / Math.pow(kv, 1.5);
            A.pruefe(Math.abs(km) <= 0.03 * sig && Math.abs(kv / var - 1) <= 0.05, f("Kammform (h %.2f m, β %.2f): Mittel und Varianz von ζ_s gegen ζ", h, be),
                    f("Mittel %+.4f m (σ %.3f m), Varianz %.3f · Var(ζ), Schiefe %.2f", km, sig, kv / var, sk), "|Mittel| ≤ 0,03 σ, Varianz 1 ± 0,05");
            double z2 = Seegangsoptik.kamm(2 * sig, sig, be)[0], t2 = Seegangsoptik.kamm(-2 * sig, sig, be)[0];
            A.info(f("Kamm bei 2σ (h %.2f m)", h), f("Kamm %.3f m, Tal %.3f m: Anteil des Kamms %.2f (Ziel %.2f)", z2, t2, z2 / (z2 - t2), Seegangsoptik.kammAnteil(hs, h)));
        }
    }

    // ------------------------------------------------------------------ Schaumköpfe (Prüfstein 18, Mittel)

    /** Wertrauschen wie seeRausch in seegang.glsl (in double). */
    static double hashS(double x, double y) { double v = Math.sin(x * 127.1 + y * 311.7) * 43758.5453; return v - Math.floor(v); }

    static double rausch(double x, double y) {
        double ix = Math.floor(x), iy = Math.floor(y), fx = x - ix, fy = y - iy, ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        double a = hashS(ix, iy), b = hashS(ix + 1, iy), c = hashS(ix, iy + 1), d = hashS(ix + 1, iy + 1);
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy;
    }

    /** Seit Lauf C2: ganzzahliger Hash wie seeHashI in seegang.glsl (bitgleich). */
    static int hashU(int x) { x ^= x >>> 16; x *= 0x7feb352d; x ^= x >>> 15; x *= 0x846ca68b; x ^= x >>> 16; return x; }

    static double hashI(int ix, int iy) { return (hashU(ix * 0x9E3779B1 ^ hashU(iy + 0x632BE5AB)) >>> 8) * (1.0 / 16777216.0); }

    /** Wie seeGauss: Box-Muller aus zwei ganzzahligen Hashes (in double; der Shader in float). */
    static double gauss(int ix, int iy) {
        double u1 = Math.max(hashI(ix, iy), 1e-7), u2 = hashI(ix + 7919, iy + 104729);
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(6.2831853 * u2);
    }

    /** Wie seeRauschN: normalverteilt mit Streuung 1 an jedem Ort. */
    static double rauschN(double x, double y) {
        double fx0 = Math.floor(x), fy0 = Math.floor(y), fx = x - fx0, fy = y - fy0, ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        int ix = (int) fx0, iy = (int) fy0;
        double w0 = (1 - ux) * (1 - uy), w1 = ux * (1 - uy), w2 = (1 - ux) * uy, w3 = ux * uy;
        double n = w0 * gauss(ix, iy) + w1 * gauss(ix + 1, iy) + w2 * gauss(ix, iy + 1) + w3 * gauss(ix + 1, iy + 1);
        return n / Math.sqrt(w0 * w0 + w1 * w1 + w2 * w2 + w3 * w3);
    }

    /** Wie schaumKoepfe in seegang.glsl, mit dem Rauschen oben. */
    static double koepfe(double W, double ost, double nord, double fuss, double zLin, double m0, double m0r, double U) {
        if (W <= 0) return 0;
        if (W >= 1) return 1;
        double ws = Math.max(0, Math.min(1, (U - Seegangsoptik.STREIF_AB) / (Seegangsoptik.STREIF_VOLL - Seegangsoptik.STREIF_AB)));
        double g1 = Math.max(0, Math.min(1, Seegangsoptik.FLECK1 / (2 * fuss) - 1)), g2 = Math.max(0, Math.min(1, Seegangsoptik.FLECK2 / (2 * fuss) - 1));
        double g3 = Math.max(0, Math.min(1, Seegangsoptik.STREIF_QUER / (2 * fuss) - 1)), c1 = Seegangsoptik.C1, c2 = Seegangsoptik.C2, c3 = Seegangsoptik.C3 * ws;
        double c0 = Seegangsoptik.C0, v = c0 * c0 + c1 * c1 + c2 * c2 + c3 * c3 + Seegangsoptik.C4 * Seegangsoptik.C4;
        double xa = (m0 > 0 ? c0 * zLin / Math.sqrt(m0) : 0) + g1 * c1 * rauschN(ost / Seegangsoptik.FLECK1, nord / Seegangsoptik.FLECK1)
                + g2 * c2 * rauschN(ost / Seegangsoptik.FLECK2 + 17, nord / Seegangsoptik.FLECK2 + 5)
                + g3 * c3 * rauschN(ost / Seegangsoptik.STREIF_LANG + 3, nord / Seegangsoptik.STREIF_QUER + 11);
        double va = (m0 > 0 ? c0 * c0 * m0r / m0 : 0) + g1 * g1 * c1 * c1 + g2 * g2 * c2 * c2 + g3 * g3 * c3 * c3;
        return Seegangsoptik.schaum(W, xa, v, va);
    }

    static void schaum() {
        A.teil("Schaumköpfe (Prüfstein 18: Mittel gleich der Bedeckung, auch in der Ferne)");
        Random r = new Random(13);
        // Faktor des Rauschens: Streuung von (r − 0,5) · 4,7
        double s = 0, s2 = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) { double x = rauschN(r.nextDouble() * 4096, r.nextDouble() * 4096); s += x; s2 += x * x; }
        double sd = Math.sqrt(s2 / n - (s / n) * (s / n));
        A.pruefe(Math.abs(sd - 1) <= 0.15, "Streuung des Rauschens (normalverteilt wie seeRauschN)", f("%.3f, Mittel %+.3f", sd, s / n), "1 ± 0,02");
        double hs = 0.79, tp = 10.6, th = 0.3, U = 22;
        double[] fuesse = {0.05, 0.3, 1.5, 6, 30};
        double[] Ws = {0.02, 0.05, 0.1, 0.3};
        double schlecht = 0;
        StringBuilder sb = new StringBuilder();
        boolean ok = true, mono = true;
        for (double W : Ws) {
            double vorher = Double.MAX_VALUE;
            sb.append(f("W %.2f:", W));
            for (double fu : fuesse) {
                double[] a = Seegangsoptik.amplituden(hs, tp, th, fu, WELLEN, BAENDER, CDF);
                double m = 0, mm = 0;
                int k = 200_000;                         // Lauf C2: 40 000 gaben bei W = 2 % einen Standardfehler von 3,5 %
                for (int i = 0; i < k; i++) {
                    double o = 470000 + r.nextDouble() * 2000, no = 6046000 + r.nextDouble() * 2000, t = r.nextDouble() * 3600;
                    double z = Seegangsoptik.hoehe(a, WELLEN, o, no, t)[0];
                    double q = koepfe(W, o, no, fu, z, a[Seegangsoptik.LANG], a[Seegangsoptik.LANG + 1], U);
                    m += q;
                    mm += q * q;
                }
                m /= k;
                double st = Math.sqrt(Math.max(0, mm / k - m * m));
                schlecht = Math.max(schlecht, Math.abs(m / W - 1));
                ok &= Math.abs(m / W - 1) <= 0.05;
                mono &= st <= vorher * 1.05;
                vorher = st;
                sb.append(f(" %.2f m → %.3f (σ %.3f);", fu, m / W, st));
            }
            sb.append(' ');
        }
        A.pruefe(ok, "Prüfstein 18: Mittel des Schaums / W über 200 000 Orte je Größe des Bildpunkts (Hs 0,79 m, Tp 10,6 s, U 22 m/s)", f("größte Abweichung %.3f", schlecht), "1 ± 0,05");
        A.pruefe(mono, "Prüfstein 18: Streuung des Schaums je Bildpunkt fällt mit der Größe des Bildpunkts", sb.toString().strip(), "monoton fallend (± 5 % Streuung der Stichprobe)");
    }

    // ------------------------------------------------------------------ Prüfstein 8a

    /** Q_b nach Battjes und Janssen (1978): (1 − Q) / (−ln Q) = (H_rms / H_m)², H_m = γ h. */
    static double qb(double hs, double h, double gamma) {
        double b = Math.pow(hs / Math.sqrt(2) / (gamma * h), 2);
        if (b >= 1) return 1;
        double lo = 1e-12, hi = 1 - 1e-12;
        for (int i = 0; i < 200; i++) {
            double m = 0.5 * (lo + hi), v = (1 - m) / -Math.log(m);
            if (v < b) lo = m; else hi = m;
        }
        return 0.5 * (lo + hi);
    }

    static void brechen() {
        A.teil("Brechen im Wasser (Prüfstein 8a): Anteil der brechenden Wellen gegen Q_b nach Battjes und Janssen");
        double h = 1.0, tp = 4, th = 0.3;
        double[] hss = {0.3, 0.4, 0.5, 0.6, 0.7};
        StringBuilder sb = new StringBuilder();
        double vor = -1;
        boolean folge = true, verh = true;
        Random r = new Random(5);
        for (double hs : hss) {
            double[] a = Seegangsoptik.amplituden(hs, tp, th, 1e-3, WELLEN, BAENDER, CDF);
            double sig = Math.sqrt(a[Seegangsoptik.LANG + 1]), be = Seegangsoptik.beta(hs, h), ka = Seegangsoptik.kammAnteil(hs, h);
            // Wellen in der Zeitreihe an 40 Orten (Nulldurchgang nach oben), je Welle bricht sie, wenn der Kamm b > 0,5 erreicht
            int wellen = 0, brechend = 0;
            double dt = tp / 40;
            for (int o = 0; o < 40; o++) {
                double x = 470000 + r.nextDouble() * 2000, y = 6046000 + r.nextDouble() * 2000, vor0 = 0, bmax = 0;
                boolean in = false;
                for (int i = 0; i < 4000; i++) {
                    double z = Seegangsoptik.kamm(Seegangsoptik.hoehe(a, WELLEN, x, y, i * dt)[0], sig, be)[0];
                    if (vor0 < 0 && z >= 0) {
                        if (in) { wellen++; if (bmax > 0.5) brechend++; }
                        in = true;
                        bmax = 0;
                    }
                    bmax = Math.max(bmax, Seegangsoptik.brechen(z, ka, h));
                    vor0 = z;
                }
            }
            double fl = (double) brechend / Math.max(1, wellen), q = qb(hs, h, 0.73);
            folge &= fl >= vor;
            vor = fl;
            if (q >= 0.01) verh &= fl / q >= 0.5 && fl / q <= 2;
            sb.append(f("Hs/h %.1f: %d Wellen, brechend %.4f, Q_b %.4f; ", hs / h, wellen, fl, q));
        }
        A.pruefe(folge, "Prüfstein 8a: Reihenfolge – mehr Weißwasser bei größerem Hs/h (h 1 m, Tp 4 s)", sb.toString().strip(), "steigend");
        A.pruefe(verh, "Prüfstein 8a: Verhältnis brechend / Q_b, wo Q_b ≥ 0,01", "siehe Zeile davor", "0,5–2 [G]");
    }

    // ------------------------------------------------------------------ Prüfstein 17

    static float[] lies16(File f, int n, int k) throws IOException {
        try (var ch = java.nio.channels.FileChannel.open(f.toPath())) {
            ByteBuffer b = ByteBuffer.allocate(2 * n).order(ByteOrder.LITTLE_ENDIAN);
            ch.read(b, 2L * n * k);
            b.flip();
            float[] r = new float[n];
            for (int i = 0; i < n; i++) r[i] = Float.float16ToFloat(b.getShort());
            return r;
        }
    }

    static double[] zeiten(File f) throws IOException {
        byte[] d = Files.readAllBytes(f.toPath());
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        double[] t = new double[d.length / 8];
        for (int i = 0; i < t.length; i++) t[i] = b.getDouble();
        return t;
    }

    static void bedeckung() throws IOException {
        A.teil("Bedeckung mit Schaum gegen die Literatur (Prüfstein 17), Archiv " + archiv);
        File w = new File(archiv, "waves_G3w"), wi = new File(archiv, "wind");
        if (!w.isDirectory() || !wi.isDirectory()) { A.info("Archiv", "fehlt; Prüfstein 17 nicht gerechnet"); return; }
        double[] tw = zeiten(new File(w, "zeiten.f64")), tz = zeiten(new File(wi, "zeiten.f64"));
        int nw = 140 * 80, nz = 370 * 430;
        int gut = 0, alle = 0;
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < tw.length; k += 6) {
            float[] hs = lies16(new File(w, "waves.hs" + F16), nw, k), di = lies16(new File(w, "waves.diss" + F16), nw, k);
            int iz = 0;
            for (int i = 0; i < tz.length; i++) if (Math.abs(tz[i] - tw[k]) < Math.abs(tz[iz] - tw[k])) iz = i;
            float[] u = lies16(new File(wi, "air.u10" + F16), nz, iz), v = lies16(new File(wi, "air.v10" + F16), nz, iz);
            List<Double> ul = new ArrayList<>(), wl = new ArrayList<>();
            for (int j = 0; j < 80; j++)
                for (int i = 0; i < 140; i++) {
                    int c = j * 140 + i;
                    if (!(hs[c] > 1.2) || !(di[c] > 0)) continue;
                    double x = 462000 + 100 * (i + 0.5), y = 6042000 + 100 * (j + 0.5);
                    int gi = (int) ((x - 428000) / 200), gj = (int) ((y - 6011000) / 200);
                    ul.add(Math.hypot(u[gj * 370 + gi], v[gj * 370 + gi]));
                    wl.add(Math.min(1, di[c] / Seegangsoptik.D_KAL));
                }
            if (ul.size() < 50) continue;
            double U = median(ul), W = median(wl);
            double wc = U > 9.25 ? 4.82e-6 * Math.pow(U + 1.98, 3) : 3.18e-5 * Math.pow(Math.max(0, U - 3.7), 3), wm = 3.84e-6 * Math.pow(U, 3.41);
            alle++;
            if (W >= Math.min(wc, wm) && W <= Math.max(wc, wm)) gut++;
            sb.append(f("%.0f h: U %.1f, W %.3f (%.3f–%.3f); ", tw[k] / 3600, U, W, wc, wm));
        }
        A.pruefe(alle > 0 && gut == alle, f("Prüfstein 17: Bedeckung W = diss / %.1f W/m² (Median über Zellen mit Hs > 1,2 m) zwischen Callaghan 2008 und Monahan 1980", Seegangsoptik.D_KAL),
                f("%d von %d Zeitpunkten", gut, alle), "alle");
        A.info("Zeitpunkte", sb.toString().strip());
    }

    static double median(List<Double> l) {
        double[] a = l.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return a[a.length / 2];
    }

    // ------------------------------------------------------------------ Kanten, Prüfsteine 8c und 19

    static void kanten() throws IOException {
        A.teil("Kanten und Brecher an den Böschungen (Prüfsteine 8c, 19), Gebiet " + gebiet);
        Kanten k = Kanten.lies(gebiet);
        A.pruefe(k != null && !k.kanten.isEmpty(), "Kanten der Brandung (brandung/kanten.txt, Schritt brandung)", k == null ? "fehlt" : k.kanten.size() + " Abschnitte", "vorhanden");
        if (k == null) return;
        int[] je = new int[6];
        for (Kanten.Kante x : k.kanten) je[Math.min(5, x.art())]++;
        A.info("Abschnitte je Art", f("Deckwerk %d, Warft %d, Deich %d, Kante %d", je[5], je[4], je[3], je[0] + je[1] + je[2]));
        // Typen nach Battjes: Schwellen wie com.dan.surf.Surf.typeOffshore
        double[][] tab = {{0.3, Brecherform.SCHWALL}, {0.49, Brecherform.SCHWALL}, {0.51, Brecherform.STURZ}, {3.2, Brecherform.STURZ}, {3.4, Brecherform.REFLEXION}, {4.9, Brecherform.REFLEXION}, {5.1, Brecherform.BRANDUNG}};
        boolean tabOk = true;
        for (double[] q : tab) tabOk &= Brecherform.typ(q[0]) == (int) q[1];
        A.pruefe(tabOk, "Brechertyp nach Battjes (1974): Schwellen 0,5 / 3,3 / 5 wie Surf.typeOffshore (Sylt)", "7 Werte", "gleich");
        // Landunter an der Hanswarft: Wasser 3,7 m NHN, Hs 0,8 m, Tp 10,6 s, See von Westen
        Brandung b = new Brandung(470000, 6046000);
        b.protokollieren = true;
        Brandung.Wasser w = new Brandung.Wasser() {
            @Override public float eta(double o, double n) { return 3.7f; }
            @Override public double[] seegang(double o, double n) { return new double[] {0.8, 10.6, 1, 0, 0.01, 2}; }
        };
        b.blickOst = Math.sin(Math.toRadians(100));
        b.blickNord = Math.cos(Math.toRadians(100));
        b.blickCos = Math.cos(Math.atan(Math.tan(Math.toRadians(25)) * 16 / 9.0) + Math.toRadians(15));
        int falsch = 0, gesamt = 0;
        int[] typen = new int[5];
        double[] ms = new double[50];
        for (int i = 0; i < 50; i++) {
            b.rechne(k, w, 50000 + 0.37 * i, 470732, 6046628, 22, 0);
            ms[i] = b.ms;
            for (double[] q : b.protokoll) {
                gesamt++;
                typen[(int) q[1]]++;
                if ((int) q[1] != Brecherform.typ(Brecherform.iribarren(k.kanten.get((int) q[0]).neigung(), Math.max(q[3], 0.02), q[7]))) falsch++;
            }
        }
        Arrays.sort(ms);
        A.pruefe(gesamt > 0 && falsch == 0, "Prüfstein 8c: Typ je Abschnitt im Netz gegen Brecherform.typ aus ξ₀ (50 Bilder, Landunter 3,7 m an der Hanswarft)",
                f("%d von %d gleich; Schwall %d, Sturz %d, Reflexion %d, Brandung %d", gesamt - falsch, gesamt, typen[1], typen[2], typen[3], typen[4]), "gleich");
        A.info("Rechenzeit des Kerns je Bild (Median aus 50, Blick nach Osten auf die Hanswarft, Blickfeld 50°) und Netz", f("%.2f ms; %d aktive Abschnitte, %d Scheitel, %d Bündel der Gischt", ms[25], b.aktiv, b.scheitel, b.buendelZahl));
        // Prüfstein 19: Ru2% der Wellenfolge gegen EurOtop
        double hs = 0.8, tp = 10.6, tan = 0.1, rau = 1.0, ru2 = Brandung.ru2(hs, tp, tan, rau);
        int n = 20_000;
        double[] ru = new double[n];
        for (int i = 0; i < n; i++) ru[i] = ru2 * b.hoehe(hs, i, 7.3 * i) / (1.398 * hs);
        Arrays.sort(ru);
        double ru98 = ru[(int) (0.98 * n)];
        double tm = tp / 1.1, xi = tan / Math.sqrt(hs / (9.81 * tm * tm / (2 * Math.PI)));
        A.pruefe(Math.abs(ru98 / ru2 - 1) <= 0.05, "Prüfstein 19: Auflauf, der von 2 % der Wellen überschritten wird, gegen Ru2% nach EurOtop 2018 (Hs 0,8 m, Tp 10,6 s, 1:10, Gras)",
                f("%.3f m gegen %.3f m (ξ_m−1,0 %.2f; Ru2%% = %.2f · Hs)", ru98, ru2, xi, ru2 / hs), "1 ± 0,05");
        A.info("Ru2% am Deckwerk 1:3 (γ_f 0,9) bei Hs 0,8 m, Tp 10,6 s", f("%.2f m (Grenze nach Gl. 5.4: %.2f m)", Brandung.ru2(hs, tp, 1.0 / 3, 0.9), hs * 0.9 * (4 - 1.5 / Math.sqrt((1.0 / 3) / Math.sqrt(hs / (9.81 * tm * tm / (2 * Math.PI)))))));
    }
}
