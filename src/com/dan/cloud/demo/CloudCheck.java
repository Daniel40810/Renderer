package com.dan.cloud.demo;

import com.dan.cloud.CloudType;
import com.dan.cloud.Clouds;
import com.dan.cloud.Noise;
import com.dan.cloud.gl.CloudGl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10): Rauschen (reproduzierbar,
 * kachelbar), Bedeckung von unten gegen die eingestellten Achtel für alle Arten an anderen Orten als bei der Eichung,
 * Höhen und Amboss, Zug mit dem Wind, Schatten, Regen, Helligkeit und die Vollständigkeit der Uniforms. Endet mit
 * „Alles in Ordnung.“ oder mit den Verstößen (Rückgabewert 1).
 */
public final class CloudCheck {
    /** Prüfsummen der Rauschtexturen (CRC32 der Bytes); ändern sich nur, wenn {@link Noise} geändert wird. */
    static final long CRC_BASE = 0xE11F578FL, CRC_DETAIL = 0x0E705552L, CRC_WEATHER = 0xE05D4934L;
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        CloudCheck c = new CloudCheck();
        long t0 = System.nanoTime();
        c.run();
        for (String l : c.lines) System.out.println(l);
        System.out.printf(Locale.GERMAN, "(%.1f s)%n", (System.nanoTime() - t0) / 1e9);
        System.out.println(c.bad == 0 ? "Alles in Ordnung." : c.bad + " Verstöße.");
        System.exit(c.bad == 0 ? 0 : 1);
    }

    public List<String> run() {
        noise();
        coverage();
        shape();
        drift();
        shadowAndRain();
        lens();
        mountains();
        gelaende();
        smoothNoise();
        gpuContract();
        return lines;
    }

    public int failures() { return bad; }

    void check(boolean c, String what) {
        if (!c) bad++;
        lines.add((c ? "  ok    " : "  FEHLER ") + what);
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    static long crc(byte[] b) {
        CRC32 c = new CRC32();
        c.update(b);
        return c.getValue();
    }

    // ------------------------------------------------------------------ Rauschen

    void noise() {
        lines.add("Rauschen");
        long t0 = System.nanoTime();
        byte[] b = Noise.base(), d = Noise.detail(), w = Noise.weather();
        double ms = (System.nanoTime() - t0) / 1e6;
        check(b.length == 128 * 128 * 128 * 4 && d.length == 32 * 32 * 32 * 4 && w.length == 512 * 512 * 4,
                f("Größen 128³, 32³, 512² RGBA (gebaut in %.0f ms)", ms));
        long cb = crc(b), cd = crc(d), cw = crc(w);
        boolean known = CRC_BASE != 0;
        check(!known || (cb == CRC_BASE && cd == CRC_DETAIL && cw == CRC_WEATHER),
                f("reproduzierbar: Prüfsummen %08X %08X %08X%s", cb, cd, cw, known ? "" : " (noch nicht hinterlegt)"));
        // kachelbar: Abtasten bei u und u + 1 gibt dasselbe; die Ränder gehen stetig ineinander über
        double worst = 0, jump = 0;
        double[] p = new double[4], q = new double[4];
        for (int i = 0; i < 200; i++) {
            double u = i * 0.0137, v = i * 0.0291, s = i * 0.0073;
            sample3(b, 128, u, v, s, p);
            sample3(b, 128, u + 1, v - 2, s + 3, q);
            for (int k = 0; k < 4; k++) worst = Math.max(worst, Math.abs(p[k] - q[k]));
            int x = i % 128, y = (i * 7) % 128;
            int o1 = ((y * 128 + x) * 128 + 127) * 4, o2 = ((y * 128 + x) * 128) * 4;
            jump = Math.max(jump, Math.abs((b[o1] & 255) - (b[o2] & 255)) / 255.0);
        }
        check(worst < 1e-9 && jump < 0.35, f("kachelbar: Abweichung bei u+1 %.1e, Sprung über den Rand höchstens %.2f", worst, jump));
        double[] hist = new double[4];
        for (int i = 0; i < w.length; i += 4) for (int k = 0; k < 4; k++) hist[k] += (w[i + k] & 255) / 255.0;
        for (int k = 0; k < 4; k++) hist[k] /= w.length / 4.0;
        check(hist[0] > 0.3 && hist[0] < 0.7 && hist[1] > 0.3 && hist[1] < 0.7, f("Wetterkarte im Mittel R %.2f, G %.2f, B %.2f, A %.2f", hist[0], hist[1], hist[2], hist[3]));
    }

    /** Trilinear wie die Grafikkarte (der Kern hält das package-intern; hier nachgebaut). */
    static void sample3(byte[] t, int n, double u, double v, double w, double[] out) {
        double x = u * n - 0.5, y = v * n - 0.5, z = w * n - 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = x - x0, fy = y - y0, fz = z - z0;
        out[0] = out[1] = out[2] = out[3] = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
            double g = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            int o = ((Math.floorMod(z0 + dz, n) * n + Math.floorMod(y0 + dy, n)) * n + Math.floorMod(x0 + dx, n)) * 4;
            for (int k = 0; k < 4; k++) out[k] += g * (t[o + k] & 255) / 255.0;
        }
    }

    // ------------------------------------------------------------------ Bedeckung

    /** Mittel über 256 Orte gleichmäßig über die Kachel der Wetterkarte, versetzt gegen die Orte der Eichung. */
    static double observed(Clouds c) {
        Clouds.Snapshot s = c.snapshot();
        double sum = 0, tile = s.tile();
        for (int i = 0; i < 16; i++)
            for (int j = 0; j < 16; j++) sum += Clouds.observedCoverage(s, (i + 0.75) * tile / 16, (j + 0.75) * tile / 16, 10);
        return sum / 256;
    }

    /** Szenen-y, das an (x, z) über der gekrümmten Erde die Höhe h ergibt. */
    static double yAt(double x, double z, double h) {
        double r = Clouds.EARTH_R + h;
        return Math.sqrt(r * r - x * x - z * z) - Clouds.EARTH_R;
    }

    void coverage() {
        lines.add("Bedeckung von unten (256 Orte, Richtungen bis 70° vom Zenit, optische Dicke ab 1)");
        for (CloudType t : CloudType.values()) {
            StringBuilder sb = new StringBuilder();
            double worst = 0, prev = -1;
            boolean mono = true;
            for (int o = 0; o <= 8; o += 2) {
                Clouds c = Clouds.of(t, o);
                double v = observed(c);
                worst = Math.max(worst, Math.abs(v - o / 8.0));
                if (v < prev - 0.02) mono = false;
                prev = v;
                sb.append(f(" %d/8→%.2f", o, v * 8));
            }
            check(worst < 0.08 && mono, f("%-14s%s (größte Abweichung %.2f Achtel)", t.label, sb, worst * 8));
        }
    }

    // ------------------------------------------------------------------ Form

    /** Seit Glacier Phase 3: Lentikularis als ortsfeste Linsen einer Leewelle. */
    void lens() {
        lines.add("Lentikularis");
        Clouds c = Clouds.fair();
        c.lens = 0.9;
        c.lensFromDeg = 270;
        Clouds.Snapshot s0 = c.snapshot();
        c.step(600);
        Clouds.Snapshot s1 = c.snapshot();
        int hit = 0, hitTop = 0, n = 0;
        boolean fixed = true;
        double maxTau = 0;
        for (int i = 0; i < 4000; i++) {
            double x = (i * 7919) % 40_000 - 20_000, z = (i * 104_729) % 40_000 - 20_000;
            double t0 = Clouds.lensTau(s0, x, z, 0), t2 = Clouds.lensTau(s0, x, z, 2);
            fixed &= Math.abs(t0 - Clouds.lensTau(s1, x, z, 0)) < 1e-12;
            if (t0 > 0.5) hit++;
            if (t2 > 0.5) hitTop++;
            maxTau = Math.max(maxTau, t0);
            n++;
        }
        // Wind aus West: die Kämme liegen quer zum Wind, also Nord-Süd; eine Linse reicht quer weiter als längs
        int base = 0, keepAlong = 0, keepAcross = 0;
        for (int i = 0; i < 6000; i++) {
            double x = (i * 7919) % 60_000 - 30_000, z = (i * 104_729) % 60_000 - 30_000;
            if (Clouds.lensTau(s0, x, z, 0) <= 0.5) continue;
            base++;
            if (Clouds.lensTau(s0, x + 1500, z, 0) > 0.5) keepAlong++;
            if (Clouds.lensTau(s0, x, z + 1500, 0) > 0.5) keepAcross++;
        }
        double along = keepAlong / (double) Math.max(1, base), across = keepAcross / (double) Math.max(1, base);
        c.lens = 0;
        boolean none = Clouds.lensTau(c.snapshot(), 0, 0, 0) == 0;
        check(hit > n * 0.03 && hit < n * 0.4 && hitTop < hit && fixed && none && maxTau > 1,
                f("Linsen auf %.0f %% der Fläche (obere Lage %.0f %%, Stapel), τ bis %.1f, ortsfest beim Ziehen der Wolken, aus bei 0", hit * 100.0 / n, hitTop * 100.0 / n, maxTau));
        check(across > 2 * along, f("Linsen quer zum Wind gestreckt: 1,5 km weiter noch Linse quer %.0f %%, längs %.0f %%", across * 100, along * 100));
    }

    void shape() {
        lines.add("Form");
        Clouds cu = Clouds.of(CloudType.CUMULUS, 3);
        Clouds.Snapshot s = cu.snapshot();
        double below = 0, inside = 0, above = 0;
        for (int i = 0; i < 4000; i++) {
            double x = (i * 7919) % 30_000, z = (i * 104_729) % 30_000;
            below = Math.max(below, Clouds.density(s, x, yAt(x, z, 850), z, true));
            inside = Math.max(inside, Clouds.density(s, x, yAt(x, z, 1300), z, true));
            above = Math.max(above, Clouds.density(s, x, yAt(x, z, 2600), z, true));
        }
        check(below == 0 && inside > 0 && above == 0, f("Cumulus: nichts unter 900 m und über 2 500 m, dicht dazwischen (größte Auslöschung %.3f/m)", inside));
        Clouds cb = Clouds.of(CloudType.CUMULONIMBUS, 3);
        Clouds.Snapshot sb = cb.snapshot();
        int high = 0, mid = 0, n = 6000;
        for (int i = 0; i < n; i++) {
            double x = (i * 7919) % 120_000, z = (i * 104_729) % 120_000;
            if (Clouds.density(sb, x, yAt(x, z, 10_300), z, false) > 0) high++;
            if (Clouds.density(sb, x, yAt(x, z, 5_000), z, false) > 0) mid++;
        }
        check(high > mid && mid > 0, f("Cumulonimbus: Amboss in 10,3 km breiter als die Türme in 5 km (%.1f %% gegen %.1f %% der Fläche)", 100.0 * high / n, 100.0 * mid / n));
        boolean order = CloudType.CUMULONIMBUS.top() > CloudType.NIMBOSTRATUS.top() && CloudType.STRATUS.base < CloudType.CUMULUS.base;
        check(order, f("Höhen: Stratus ab %.0f m, Cumulus ab %.0f m, Cumulonimbus bis %.0f m", CloudType.STRATUS.base, CloudType.CUMULUS.base, CloudType.CUMULONIMBUS.top()));
        Clouds e = Clouds.of(CloudType.STRATOCUMULUS, 0);
        Clouds.Snapshot se = e.snapshot();
        double any = 0;
        for (int i = 0; i < 2000; i++) any = Math.max(any, Clouds.density(se, i * 137.0, yAt(i * 137.0, i * 71.0, 1000), i * 71.0, true));
        check(any == 0, "0/8: keine Wolke");
    }

    // ------------------------------------------------------------------ Zug

    void drift() {
        lines.add("Zug mit dem Wind");
        Clouds c = Clouds.of(CloudType.CUMULUS, 4);
        c.windSpeed = 12;
        c.windFromDeg = 270;   // aus West: die Wolken ziehen nach Osten (+x)
        c.evolve = 0;
        c.setTime(0);
        Clouds.Snapshot a = c.snapshot();
        c.setTime(100);
        Clouds.Snapshot b = c.snapshot();
        double worst = 0;
        for (int i = 0; i < 400; i++) {
            double x = i * 53.0, z = i * 31.0;
            worst = Math.max(worst, Math.abs(Clouds.coverage(a, x, z) - Clouds.coverage(b, x + 1200, z)));
        }
        check(worst < 1e-12, f("Wind 12 m/s aus West: nach 100 s steht dieselbe Wolke 1 200 m weiter östlich (± %.1e)", worst));
        Clouds d = Clouds.of(CloudType.CUMULUS, 4);
        d.windSpeed = 10;
        d.windFromDeg = 0;
        d.step(10);
        Clouds.Snapshot sd = d.snapshot();
        Clouds e = Clouds.of(CloudType.CUMULUS, 4);
        e.windSpeed = 10;
        e.windFromDeg = 0;
        e.setTime(10);
        check(Math.abs(sd.offX() - e.snapshot().offX()) < 1e-9 && Math.abs(sd.offZ() - e.snapshot().offZ()) < 1e-9,
                f("step und setTime stimmen überein (Versatz %.1f / %.1f m)", sd.offX(), sd.offZ()));
    }

    // ------------------------------------------------------------------ Schatten, Regen, Helligkeit

    void shadowAndRain() {
        lines.add("Schatten, Regen, Helligkeit");
        double[] sun = {0, 0.6, -0.8};
        double ns = Clouds.shadow(Clouds.of(CloudType.NIMBOSTRATUS, 8).snapshot(), 0, 0, sun);
        double clear = Clouds.shadow(Clouds.of(CloudType.CUMULUS, 0).snapshot(), 0, 0, sun);
        Clouds cu = Clouds.of(CloudType.CUMULUS, 4);
        Clouds.Snapshot s = cu.snapshot();
        int lit = 0, n = 400;
        for (int i = 0; i < n; i++) if (Clouds.shadow(s, i * 211.0, i * 97.0, sun) > 0.5) lit++;
        check(ns < 0.01 && clear == 1, f("Schatten: 8/8 Nimbostratus %.3f, wolkenlos %.2f", ns, clear));
        check(lit > n * 0.2 && lit < n * 0.8, f("Cumulus 4/8: %.0f %% des Meeres in der Sonne", 100.0 * lit / n));
        Clouds hi = Clouds.of(CloudType.CUMULUS, 0);
        hi.highCover = 1;
        double h = Clouds.shadow(hi.snapshot(), 0, 0, sun);
        check(h > 0.3 && h < 0.95, f("Hohe Schicht ganz: %.2f des Sonnenlichts kommt durch", h));
        Clouds dry = Clouds.of(CloudType.CUMULONIMBUS, 6);
        Clouds wet = Clouds.storm();
        double maxDry = 0, maxWet = 0;
        for (int i = 0; i < 2000; i++) {
            maxDry = Math.max(maxDry, Clouds.rain(dry.snapshot(), i * 397.0, i * 211.0));
            maxWet = Math.max(maxWet, Clouds.rain(wet.snapshot(), i * 397.0, i * 211.0));
        }
        check(maxDry == 0 && maxWet > 0.3, f("Regen: ohne Niederschlag 0, Gewitter bis %.2f", maxWet));
        double prev = 2;
        boolean mono = true;
        for (int o = 0; o <= 8; o++) {
            double b = Clouds.of(CloudType.NIMBOSTRATUS, o).brightnessFactor();
            if (b > prev + 1e-12) mono = false;
            prev = b;
        }
        check(mono && prev < 0.3, f("Helligkeit fürs Auge fällt mit der Bedeckung (8/8 Nimbostratus %.2f)", prev));
    }

    // ------------------------------------------------------------------ Gebirge (seit Brocken Phase 3)

    /** Wolkenbasis von außen, Schatten in der Höhe eines Punkts, Fläche der Schattenkarte. */
    void mountains() {
        lines.add("Gebirge (seit Brocken Phase 3)");
        Clouds d = Clouds.of(CloudType.CUMULUS, 4);
        check(d.snapshot().base() == CloudType.CUMULUS.base && d.snapshot().shadowHeight() == 0, f("ohne Vorgabe: Basis der Art (%.0f m), Schattenfläche auf Meereshöhe", d.snapshot().base()));
        Clouds st = Clouds.of(CloudType.STRATUS, 8);
        st.baseHeight = 500;
        Clouds.Snapshot s = st.snapshot();
        double below = 0, inside = 0, above = 0;
        for (int i = 0; i < 400; i++) {
            double x = (i % 20) * 150.0, z = (i / 20) * 150.0;   // nah am Ursprung: weiter draußen sinkt die Erde unter die Höhe
            below = Math.max(below, Clouds.density(s, x, 480, z, true));
            inside = Math.max(inside, Clouds.density(s, x, 500 + CloudType.STRATUS.thickness * 0.5, z, true));
            above = Math.max(above, Clouds.density(s, x, s.top() + 20, z, true));
        }
        check(below == 0 && inside > 0 && above == 0, f("Stratus 8/8 auf 500 m gehoben: nichts darunter und über %.0f m, dicht dazwischen (%.3f/m)", s.top(), inside));
        double[] sun = {0.3, 0.5, -0.8};
        double low = Clouds.shadow(s, 100, 300, 100, sun), high = Clouds.shadow(s, 100, s.top() + 50, 100, sun);
        double up = Clouds.upperShadow(s, 100, s.top() + 50, 100, sun);
        check(low < 0.05 && high > 0.99 && Math.abs(up - high) < 1e-12, f("Schatten nach der Höhe: unter der Decke %.3f, über ihr %.3f (Nebelmeer: der Gipfel in der Sonne)", low, high));
        Clouds hc = Clouds.of(CloudType.STRATUS, 8);
        hc.baseHeight = 500;
        hc.highCover = 1;
        Clouds.Snapshot hs = hc.snapshot();
        double hTop = Clouds.shadow(hs, 0, hs.top() + 50, 0, sun), hRef = Clouds.upperShadow(hs, 0, hs.top() + 50, 0, sun);
        check(hTop < 0.99 && Math.abs(hTop - hRef) < 1e-12, f("über der Decke wirft die hohe Schicht noch Schatten (%.2f)", hTop));
        st.shadowHeight = 700;
        Clouds.Snapshot s7 = st.snapshot();
        double a = Clouds.shadow(s7, 1234, 4321, sun), b = Clouds.shadow(s7, 1234, 700, 4321, sun);
        check(Math.abs(a - b) < 1e-12, "Schattenkarte auf der Fläche shadowHeight (700 m) = Schatten am Punkt in 700 m");
    }

    // ------------------------------------------------------------------ Vertrag mit der Grafikkarte

    /** Jedes Uniform, das ein Shader deklariert, kommt in der Liste von CloudGl.uniforms vor (Sampler und uTarget ausgenommen). */
    void gpuContract() {
        lines.add("Grafikkarte");
        Clouds c = Clouds.storm();
        CloudGl.View v = new CloudGl.View();
        v.viewProj = new float[16];
        v.invViewProj = new float[16];
        Map<String, Map<String, CloudGl.U>> u = new CloudGl().uniforms(c.snapshot(), new CloudGl.Light(), v);
        String[][] progs = {{"march", "clouds.frag", "cloud_common.glsl"}, {"pano", "clouds.frag", "cloud_common.glsl"}, {"shadow", "shadow.frag", "cloud_common.glsl"}};
        Pattern decl = Pattern.compile("uniform\\s+(\\w+)\\s+(\\w+)");
        List<String> missing = new ArrayList<>();
        for (String[] p : progs) {
            Set<String> need = new HashSet<>();
            for (int i = 1; i < p.length; i++) {
                Matcher m = decl.matcher(read(p[i]));
                while (m.find()) if (!m.group(1).startsWith("sampler") && !m.group(2).equals("uTarget")) need.add(m.group(2));
            }
            for (String n : need) if (!u.get(p[0]).containsKey(n)) missing.add(p[0] + "." + n);
        }
        check(missing.isEmpty(), "Alle Uniforms der Shader werden gesetzt" + (missing.isEmpty() ? "" : ": fehlen " + missing));
        boolean q = true;
        for (Clouds.Quality k : Clouds.Quality.values()) if (k.divisor < 1) q = false;
        check(q && Clouds.Quality.MEDIUM.divisor == 4 && Clouds.Quality.HIGH.divisor == 2,
                f("Stufen: Mittel 1/%d bis %d Schritte, Hoch 1/%d bis %d Schritte", Clouds.Quality.MEDIUM.divisor, Clouds.Quality.MEDIUM.maxSteps,
                        Clouds.Quality.HIGH.divisor, Clouds.Quality.HIGH.maxSteps));
        // seit LituyaBay1958 W7: lange Brennweite – kleinere Bildpunkte, langsamer wachsende Schritte (höchstens 4×); 60° wie bisher
        CloudGl g = new CloudGl();
        double aus = g.teleFactor(proj(Math.toDegrees(2 * Math.atan(12 / 200.0)), 1080), Clouds.Quality.HIGH);
        g.teleMax = 4;
        double t60 = g.teleFactor(proj(60, 1080), Clouds.Quality.HIGH), t200 = g.teleFactor(proj(Math.toDegrees(2 * Math.atan(12 / 200.0)), 1080), Clouds.Quality.HIGH);
        Map<String, CloudGl.U> m60 = g.uniforms(c.snapshot(), new CloudGl.Light(), proj(60, 1080)).get("march");
        check(aus == 1 && Math.abs(t60 - 1) < 1e-6 && Math.abs(t200 - 4) < 1e-6 && Math.abs(m60.get("uStepGrow").v()[0] - 5000) < 1e-3,
                f("Lange Brennweite (teleMax 4, Vorgabe 1 = aus): 60° ×%.2f, 200 mm ×%.2f (Schritte wachsen ab %.0f m statt 5 000 m)", t60, t200, 5000 * t200));
    }

    /**
     * Seit LituyaBay1958 W7: Mischen mit stetiger Ableitung (smoothNoise). Aus: genau wie bisher. An: auf einer Linie durch
     * eine Wolke in 1 m Schritten (ein Texel der Feinheit sind dort rund 14 m) springt die Steigung der Dichte an den
     * Texelgrenzen nicht mehr (größte zweite Differenz klein gegen ohne), die Dichte bleibt im Mittel dieselbe.
     */
    void smoothNoise() {
        Clouds a = Clouds.of(CloudType.CUMULUS, 6);
        Clouds b = Clouds.of(CloudType.CUMULUS, 6);
        b.smoothNoise = true;
        Clouds.Snapshot sa0 = a.snapshot(), sb0 = b.snapshot();
        // Knicke an den Grenzen der Texel der Feinheit (x-Richtung): mittlere |zweite Differenz| dort gegen den Rest
        double ds = sa0.detailTile(), cell = ds / Noise.DETAIL;
        double[] onA = new double[2], offA = new double[2], onB = new double[2], offB = new double[2];
        double sa = 0, sb = 0;
        int n = 0;
        for (int line = 0; line < 12; line++) {
            double y = 1500 + line * 60, z = line * 311.0;
            double pa0 = Double.NaN, pa1 = Double.NaN, pb0 = Double.NaN, pb1 = Double.NaN;
            for (int i = 0; i < 4000; i++) {
                double x = -2000 + i * 1.0;
                double da = Clouds.density(sa0, x, y, z, true), db = Clouds.density(sb0, x, y, z, true);
                if (!Double.isNaN(pa0) && da > 0 && pa1 > 0 && pa0 > 0 && db > 0 && pb1 > 0 && pb0 > 0) {
                    double xm = x - 1, g = (xm + sa0.offX()) / cell - 0.5, fr = g - Math.floor(g);
                    boolean grenze = fr < 0.5 / cell || fr > 1 - 0.5 / cell;
                    double ka = Math.abs(da - 2 * pa1 + pa0), kb = Math.abs(db - 2 * pb1 + pb0);
                    if (grenze) { onA[0] += ka; onA[1]++; onB[0] += kb; onB[1]++; } else { offA[0] += ka; offA[1]++; offB[0] += kb; offB[1]++; }
                }
                pa0 = pa1; pa1 = da; pb0 = pb1; pb1 = db;
                sa += da; sb += db; n++;
            }
        }
        double ra = (onA[0] / Math.max(1, onA[1])) / Math.max(1e-30, offA[0] / Math.max(1, offA[1]));
        double rb = (onB[0] / Math.max(1, onB[1])) / Math.max(1e-30, offB[0] / Math.max(1, offB[1]));
        Clouds c = Clouds.of(CloudType.CUMULUS, 6);
        double same = 0;
        for (int i = 0; i < 200; i++) same = Math.max(same, Math.abs(Clouds.density(c.snapshot(), i * 37.0, 1600, i * 53.0, true) - Clouds.density(sa0, i * 37.0, 1600, i * 53.0, true)));
        check(same == 0 && ra > 2 && rb < 0.5 * ra && Math.abs(sb - sa) < 0.15 * sa,
                f("Mischen mit stetiger Ableitung: Knicke an den Texelgrenzen der Feinheit %.1f× so stark wie dazwischen, vorher %.1f×; mittlere Dichte %.4f statt %.4f/m (aus: wie bisher)", rb, ra, sb / n, sa / n));
    }

    /** Eine Ansicht mit senkrechtem Blickfeld fovy (Grad) und h Zeilen, Blick nach Norden. */
    static CloudGl.View proj(double fovy, int h) {
        CloudGl.View v = new CloudGl.View();
        double f = 1 / Math.tan(Math.toRadians(fovy) / 2);
        // Spaltenweise: Zeile 1 der Projektion · Blick (Einheitsvektor oben = y) – nur die Länge der zweiten Zeile zählt
        v.viewProj = new float[16];
        v.viewProj[0] = (float) (f * 1080 / 1920.0);
        v.viewProj[5] = (float) f;
        v.viewProj[10] = -1;
        v.viewProj[11] = -1;
        v.invViewProj = new float[16];
        v.width = h * 16 / 9;
        v.height = h;
        return v;
    }

    /**
     * Seit LituyaBay1958 W7: Wolken am Gelände. Ein Testberg (Gauß, 4 600 m, Halbmesser 5 km) über einem Meer von 60 km: die Bank hängt am Hang und nicht über dem Meer, am Luvhang dichter; die Kappe sitzt über
     * dem Gipfel ab dem Kondensationsniveau; Quellwolken über Land, Lücken im Lee; ohne Stärken bleibt alles wie bisher.
     */
    void gelaende() {
        int n = 512;
        double size = 60_000, x0 = -30_000, z0 = -30_000;
        float[] hgt = new float[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = x0 + (i + 0.5) * size / n, z = z0 + (j + 0.5) * size / n;
                double berg = 4600 * Math.exp(-(x * x + z * z) / (2 * 5000.0 * 5000));
                hgt[j * n + i] = (float) berg;
            }
        Clouds.Terrain t = new Clouds.Terrain(x0, z0, size, n, hgt);
        Clouds c = Clouds.of(CloudType.STRATOCUMULUS, 0.0);
        c.windFromDeg = 270;   // Westwind: Luv im Westen
        c.terrain = t;
        Clouds.Snapshot s0 = c.snapshot();
        check(s0.oro() == null && s0.shellLow() == s0.base() && s0.shellHigh() == s0.top(), "Gelände ohne Stärken: keine Strömungskarte, Schicht wie bisher");
        c.bank = 1;
        c.bankBase = 1200;
        c.bankTop = 2600;
        c.cap = 1;
        c.capBase = 3900;
        Clouds.Snapshot s = c.snapshot();
        Clouds.Oro o = s.oro();
        check(o != null && Math.abs(o.peak() - 4600) < 60 && s.shellLow() < 1200 && s.shellHigh() > 5000,
                f("Strömungskarte %d², höchster Gipfel %.0f m, Wolken von %.0f bis %.0f m", o == null ? 0 : o.n(), o == null ? 0 : o.peak(), s.shellLow(), s.shellHigh()));
        // Bank: am Westhang (Luv) in 1 800 m, am Osthang (Lee) in derselben Höhe, über dem Meer
        double luv = 0, lee = 0, meer = 0;
        int k = 0;
        double[] g = new double[4];
        for (int a = 0; a < 40; a++)
            for (int b = 0; b < 20; b++) {
                double z = -3000 + a * 150, y = 1600 + b * 20;
                // Abstand vom Gipfel, in dem das geglättete Gelände gerade 1 700 m hoch ist (Westen und Osten)
                double xr = 5000 * Math.sqrt(2 * Math.log(4600 / 1700.0));
                luv += Clouds.density(s, -xr, y, z, false);
                lee += Clouds.density(s, xr, y, z, false);
                meer += Clouds.density(s, -22_000, y, z, false);
                k++;
            }
        Clouds.oroAt(o, -5000 * Math.sqrt(2 * Math.log(4600 / 1700.0)), 0, g);
        check(luv / k > 2e-3 && meer == 0 && luv > lee, f("Bank am Hang: Luv %.4f/m, Lee %.4f/m, über dem Meer %.4f/m (Hang dort %.0f m, Steigung %.2f gegen den Wind)", luv / k, lee / k, meer / k, g[0], g[1]));
        // Kappe: über dem Gipfel in 4 300 m dicht, in 3 700 m (unter dem Kondensationsniveau) und 8 km daneben in 4 300 m leer
        double kap = 0, unten = 0, neben = 0;
        for (int a = 0; a < 25; a++) {
            double x = -600 + (a % 5) * 300, z = -600 + (a / 5) * 300;
            kap += Clouds.density(s, x, 4400, z, false);
            unten += Clouds.density(s, x, 3700, z, false);
            neben += Clouds.density(s, x + 8000, 4400, z, false);
        }
        check(kap / 25 > 5e-3 && unten == 0 && neben == 0, f("Kappe über dem Gipfel %.4f/m, unter dem Kondensationsniveau %.4f/m, 8 km daneben %.4f/m", kap / 25, unten / 25, neben / 25));
        // seit Runde 1b: die Kappe ohne Wände – auf einer Linie quer durch ihren Rand in 4 200 m (ohne Feinheit, 10 m Schritt)
        // ändert sich die Bedeckung stetig; der Rand der Hülle liegt dort, wo das Gelände nahe 3 900 m erreicht
        double sprung = 0, groesste = 0, vor = Double.NaN;
        for (int i = 0; i <= 600; i++) {
            double x = i * 10.0;
            double[] gg = new double[4];
            Clouds.oroAt(o, x - o.windToX() * 400, 0, gg);
            double[] cs = Clouds.oroCover(s, o, gg, x, 0, 4200);
            double cv = cs == null ? 0 : cs[0];
            if (!Double.isNaN(vor)) sprung = Math.max(sprung, Math.abs(cv - vor));
            groesste = Math.max(groesste, cv);
            vor = cv;
        }
        check(groesste > 0.5 && sprung < 0.05 * groesste, f("Kappe ohne Wände: Bedeckung bis %.2f, größter Sprung je 10 m %.3f", groesste, sprung));
        double[] sun = {0.2, 0.9, 0.1};
        double up = Clouds.upperShadow(s, 0, 4500, 0, sun), frei = Clouds.upperShadow(s, 15_000, 4500, 15_000, sun);
        check(up < 0.6 && frei > 0.99, f("Schatten der Kappe in Kanal G: unter ihr %.2f, frei %.2f", up, frei));
        // Quellwolken über Land und Lücken im Lee: Bedeckung der Wetterkarte über dem Berg, über dem Meer, im Lee
        Clouds q = Clouds.of(CloudType.CUMULUS, 3);
        q.windFromDeg = 270;
        q.terrain = t;
        double meerOhne = cover(q.snapshot(), -22_000, 0), bergOhne = cover(q.snapshot(), 0, 0);
        q.convection = 1;
        double meerMit = cover(q.snapshot(), -22_000, 0), bergMit = cover(q.snapshot(), 0, 0);
        q.convection = 0;
        q.lee = 1;
        double leeMit = cover(q.snapshot(), 5500, 0), leeOhne = 0;
        q.lee = 0;
        q.bank = 0.0001;   // Karte an, Stärken fast aus
        leeOhne = cover(q.snapshot(), 5500, 0);
        check(bergMit > bergOhne + 0.1 && meerMit < meerOhne - 0.05 && leeMit < leeOhne - 0.05,
                f("Quellwolken über Land: Berg %.2f → %.2f, Meer %.2f → %.2f; Lee %.2f → %.2f", bergOhne, bergMit, meerOhne, meerMit, leeOhne, leeMit));
        // Gleichheit: Bank so schwach, dass sie nirgends dichter als die Schicht ist → wie ohne Gelände
        Clouds a = Clouds.of(CloudType.CUMULUS, 4), b = Clouds.of(CloudType.CUMULUS, 4);
        b.terrain = t;
        b.lee = 1e-9;
        double dmax = 0;
        for (int i = 0; i < 400; i++) {
            double x = -20_000 + (i * 977) % 40_000, z = -20_000 + (i * 613) % 40_000, y = 900 + (i * 37) % 1500;
            dmax = Math.max(dmax, Math.abs(Clouds.density(a.snapshot(), x, y, z, true) - Clouds.density(b.snapshot(), x, y, z, true)));
        }
        check(dmax < 1e-4, f("ohne Stärken gleich der Schicht: größte Abweichung %.1e/m an 400 Orten", dmax));
    }

    /** Mittlere Bedeckung der Wetterkarte in einem Kreis von 3 km. */
    static double cover(Clouds.Snapshot s, double x, double z) {
        double sum = 0;
        int k = 0;
        for (int a = 0; a < 15; a++)
            for (int b = 0; b < 15; b++) { sum += Clouds.coverage(s, x - 3000 + a * 430, z - 3000 + b * 430); k++; }
        return sum / k;
    }

    static String read(String file) {
        try (InputStream in = CloudGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IOException("fehlt");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
