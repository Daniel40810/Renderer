package com.dan.beach.demo;

import com.dan.beach.Beach;

import java.util.Locale;

/**
 * Prüfklasse ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den
 * Verstößen (Rückgabewert 1).
 */
public final class BeachCheck {
    private static int bad;

    public static void main(String[] args) {
        Locale.setDefault(Locale.GERMAN);
        Beach.Maps m = Beach.maps();
        System.out.println(Beach.ostsee().describe());

        // Karten: gelesen, Abweichung 1, Mittel 0, kachelbar
        check(m.n >= 256 && m.relief.length == m.n * m.n && m.grain.length == m.n * m.n, f("Karten %d² aus %s, Quelle %.1f m", m.n, m.source, m.sourceTile));
        check(Math.abs(rms(m.relief) - 1) < 0.02 && Math.abs(mean(m.relief)) < 0.02, f("Relief: Abweichung %.3f, Mittel %.4f", rms(m.relief), mean(m.relief)));
        check(Math.abs(rms(m.grain) - 1) < 0.02 && Math.abs(mean(m.grain)) < 0.02, f("Korn: Abweichung %.3f, Mittel %.4f", rms(m.grain), mean(m.grain)));
        double rand = 0, innen = 0;
        int n = m.n;
        for (int j = 0; j < n; j++) {
            rand += Math.abs(m.relief[j * n] - m.relief[j * n + n - 1]) + Math.abs(m.relief[j] - m.relief[(n - 1) * n + j]);
            innen += Math.abs(m.relief[j * n + n / 3] - m.relief[j * n + n / 3 + 1]) + Math.abs(m.relief[(n / 3) * n + j] - m.relief[(n / 3 + 1) * n + j]);
        }
        check(rand < innen * 1.5, f("kachelbar: Sprung über den Rand %.4f, innen %.4f je Zelle", rand / (2 * n), innen / (2 * n)));
        check(m.sourceReliefRms > 0.01 && m.sourceReliefRms < 1 && m.slopeRms > 1, f("Quelle: Relief %.1f cm, Neigung je Kachel %.1f", m.sourceReliefRms * 100, m.slopeRms));

        // Ostsee: Abweichung nach Nässe und Tritten, Höhe passt dazu
        Beach b = Beach.ostsee();
        double s = 0, s2 = 0, sn = 0;
        int k = 0;
        for (int i = 0; i < 4000; i++) {
            double x = i * 0.731 % 40, z = i * 0.377 % 30;
            double h = b.heightAt(x, z, 1, 1), hn = b.heightAt(x, z, 0, 1);
            s += h; s2 += h * h; sn += hn * hn; k++;
        }
        double sd = Math.sqrt(s2 / k - (s / k) * (s / k)), sdn = Math.sqrt(sn / k);
        check(sd > b.relief * 0.8 && sd < b.relief * 1.6, f("trocken und zertreten: Abweichung %.1f cm (eingestellt %.1f cm, zwei Oktaven)", sd * 100, b.relief * 100));
        check(sdn < sd * 0.35, f("nass glatter: %.1f cm", sdn * 100));
        check(b.amplitude(1, 0) < b.amplitude(1, 1) && b.amplitude(0, 0) < b.amplitude(1, 0), "ruhig weniger als zertreten, nass am wenigsten");

        // Normale aus der Neigung passt zur Höhe (Differenzen)
        double maxErr = 0;
        for (int i = 0; i < 200; i++) {
            double x = 3.1 + i * 0.137, z = -2.7 + i * 0.091, e = 0.002;
            double gx = (b.heightAt(x + e, z, 1, 1) - b.heightAt(x - e, z, 1, 1)) / (2 * e), gz = (b.heightAt(x, z + e, 1, 1) - b.heightAt(x, z - e, 1, 1)) / (2 * e);
            double[] nn = b.normalAt(x, z, 1, 1);
            maxErr = Math.max(maxErr, Math.max(Math.abs(-gx - nn[0] / nn[1]), Math.abs(-gz - nn[2] / nn[1])));
        }
        check(maxErr < 0.05, f("Normale passt zur Höhe (größter Fehler der Neigung %.3f)", maxErr));

        // Helligkeit: um 1, Korn sichtbar, aber nicht wild
        double amin = 9, amax = 0, asum = 0;
        for (int i = 0; i < 4000; i++) {
            double a = b.albedoAt(i * 0.0137, i * 0.0291, 1, 1);
            amin = Math.min(amin, a); amax = Math.max(amax, a); asum += a;
        }
        check(Math.abs(asum / 4000 - 1) < 0.03 && amin > 0.6 && amax < 1.5, f("Helligkeit im Mittel %.3f, von %.2f bis %.2f", asum / 4000, amin, amax));

        // Sonne: hoch kein Ausgleich, tief weniger Licht im Relief, unter dem Horizont keins
        double sig = b.slopeRms(1, 1);
        double hoch = Beach.sunFactor(sig, Math.sin(Math.toRadians(60))), tief = Beach.sunFactor(sig, Math.sin(Math.toRadians(6)));
        check(hoch > 0.97 && tief < 0.9 && tief >= 0.2 && Beach.sunFactor(sig, -0.1) == 0, f("Ausgleich der Sonne bei σ %.2f: 60° %.2f, 6° %.2f", sig, hoch, tief));

        // seed, Presets, copy
        Beach c = Beach.ostsee();
        check(c.heightAt(1.234, 5.678, 1, 1) == b.heightAt(1.234, 5.678, 1, 1), "gleicher seed, gleicher Strand");
        c.seed = 99;
        check(c.heightAt(1.234, 5.678, 1, 1) != b.heightAt(1.234, 5.678, 1, 1), "anderer seed, anderer Strand");
        Beach d = Beach.duene();
        check(d.tile == m.sourceTile && d.relief == m.sourceReliefRms && Beach.nordsee().tile > b.tile, "Düne in der Größe des Assets, Nordsee gröber als Ostsee");
        Beach e = b.copy();
        e.relief = 0.5;
        check(b.relief == 0.022, "copy() ist unabhängig");
        check(Beach.Quality.LOW.range < Beach.Quality.ULTRA.range, "Reichweite steigt mit der Stufe");

        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static double mean(float[] a) { double s = 0; for (float v : a) s += v; return s / a.length; }

    static double rms(float[] a) {
        double m = mean(a), s = 0;
        for (float v : a) s += (v - m) * (v - m);
        return Math.sqrt(s / a.length);
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMAN, s, a); }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private BeachCheck() { }
}
