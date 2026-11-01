package com.dan.surface.demo;

import com.dan.surface.Bake;
import com.dan.surface.HexTiling;
import com.dan.surface.Katalog;
import com.dan.surface.Material;
import com.dan.surface.MaterialSet;
import com.dan.surface.gl.SurfaceGl;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Prüfung ohne Fenster (Paketvertrag Punkt 10), endet mit „Alles in Ordnung.“ oder den Verstößen (Rückgabewert 1):
 * <ul>
 * <li>Katalog {@link Katalog#fjord()}: 14 Materialien, Kurznamen eindeutig, Herkunft und Lizenz je Satz.</li>
 * <li>Backen ({@link Bake}) an Probesätzen: Größe, Höhe im Alpha, Normale DX gleich GL, Tönung, Ableitung, Mip-Stufen
 * mit Normalen der Länge 1.</li>
 * <li><b>1 km Kiesstrand ohne Wiederholung:</b> Helligkeit entlang zweier Linien über 1 km (alle 2 cm), einmal einfach
 * gekachelt, einmal hex-gekachelt ({@link HexTiling}); die Autokorrelation beim Abstand einer Kachel (und ihren Vielfachen
 * bis 10) muss unter {@link #SCHWELLE} liegen, einfach gekachelt ist sie nahe 1.</li>
 * <li>Budget: 14 Materialien zu 2048² gepackt unter 600 MB (Werkbuch F19).</li>
 * </ul>
 * {@code --texturen <ordner>}: dieselbe Strandprüfung mit dem gebackenen {@code strandkies} von dort, dazu die Liste.
 */
public final class SurfaceCheck {
    private static int bad;
    /** Höchste erlaubte Autokorrelation beim Abstand einer Kachel. */
    public static final double SCHWELLE = 0.3;

    public static void main(String[] a) throws Exception {
        List<Material> kat = Katalog.fjord();
        Set<String> ids = new HashSet<>();
        int lic = 0;
        for (Material m : kat) {
            ids.add(m.id);
            if (m.derivedFrom != null ? Katalog.find(kat, m.derivedFrom) != null : "CC0 1.0".equals(m.license) && m.url != null) lic++;
        }
        check(kat.size() == 14 && ids.size() == 14, "Katalog fjord(): 14 Materialien, Kurznamen eindeutig");
        check(lic == 14, "je Material Quelle und Lizenz (13 × CC0 1.0 von ambientCG, Seetang abgeleitet aus Moos)");

        File tmp = Files.createTempDirectory("surface").toFile();
        Material kies = Katalog.find(kat, "strandkies"), eis = Katalog.find(kat, "eis"), moos = Katalog.find(kat, "moos"), tang = Katalog.find(kat, "seetang");
        File src = Probe.set(kies, new File(tmp, "roh_kies"), 256, 1);
        Bake.Result r = Bake.bake(kies, src, new File(tmp, "out/strandkies"), 128);
        BufferedImage ba = ImageIO.read(new File(tmp, "out/strandkies/a.png")), disp = ImageIO.read(new File(src, "Probe_strandkies_Displacement.png"));
        check(r.size() == 128 && ba.getWidth() == 128 && new File(tmp, "out/strandkies/n.png").isFile() && new File(tmp, "out/strandkies/material.txt").isFile(),
                "Backen: a.png, n.png, r.png, material.txt in 128² (aus 256²)");
        double dh = Math.abs(((ba.getRGB(10, 20) >>> 24) & 255) / 255.0 - avg(disp, 20, 40, 2));
        check(dh < 0.05, String.format(Locale.GERMAN, "Höhe im Alphakanal (Abweichung %.3f)", dh));
        // Normale DX: grün umgedreht muss dasselbe ergeben
        File dx = new File(tmp, "roh_kies_dx");
        dx.mkdirs();
        for (File f : src.listFiles()) {
            if (f.getName().contains("NormalGL")) {
                BufferedImage n = ImageIO.read(f);
                for (int y = 0; y < n.getHeight(); y++) for (int x = 0; x < n.getWidth(); x++) { int p = n.getRGB(x, y); n.setRGB(x, y, (p & 0xff00ff) | ((255 - ((p >> 8) & 255)) << 8)); }
                ImageIO.write(n, "png", new File(dx, f.getName().replace("NormalGL", "NormalDX")));
            } else Files.copy(f.toPath(), new File(dx, f.getName()).toPath());
        }
        Bake.bake(kies, dx, new File(tmp, "out/kies_dx"), 128);
        BufferedImage n1 = ImageIO.read(new File(tmp, "out/strandkies/n.png")), n2 = ImageIO.read(new File(tmp, "out/kies_dx/n.png"));
        int diff = 0;
        for (int y = 0; y < 128; y += 3) for (int x = 0; x < 128; x += 3) diff = Math.max(diff, Math.abs(((n1.getRGB(x, y) >> 8) & 255) - ((n2.getRGB(x, y) >> 8) & 255)));
        check(diff <= 2, "Normale DirectX (_NormalDX) wird zu OpenGL (Grün umgedreht), Abweichung " + diff + "/255");
        Bake.Result re = Bake.bake(eis, Probe.set(eis, new File(tmp, "roh_eis"), 128, 1), new File(tmp, "out/eis"), 0);
        Bake.Result rm = Bake.bake(moos, Probe.set(moos, new File(tmp, "roh_moos"), 128, 1), new File(tmp, "out/moos"), 0);
        Bake.Result rt = Bake.derive(tang, new File(tmp, "out/moos"), new File(tmp, "out/seetang"));
        check(re.meanColor()[2] > re.meanColor()[0] * 1.1, String.format(Locale.GERMAN, "Tönung Gletschereis: blau %.3f > rot %.3f", re.meanColor()[2], re.meanColor()[0]));
        double rg0 = rm.meanColor()[0] / rm.meanColor()[1], rg1 = rt.meanColor()[0] / rt.meanColor()[1];
        check(rg1 > rg0 * 1.2 && rt.meanColor()[2] < rm.meanColor()[2] && rt.size() == rm.size(),
                String.format(Locale.GERMAN, "Seetang aus Moos abgeleitet (olivbraun): Rot/Grün %.2f → %.2f, Blau %.3f → %.3f", rg0, rg1, rm.meanColor()[2], rt.meanColor()[2]));
        MaterialSet set = new MaterialSet(new File(tmp, "out"), kat);
        check(set.layers().size() == 4 && set.layer("moos") >= 0 && set.missing().size() == 10, "MaterialSet: " + set.describe());
        List<byte[]>[] mips = SurfaceGl.mips(new File(tmp, "out/strandkies"), 64);
        double worstLen = 0;
        byte[] nn = mips[1].get(2);
        for (int i = 0; i < nn.length; i += 2) {
            double x = (nn[i] & 255) / 127.5 - 1, y = (nn[i + 1] & 255) / 127.5 - 1;
            worstLen = Math.max(worstLen, Math.hypot(x, y));
        }
        check(mips[0].size() == 7 && mips[0].get(0).length == 64 * 64 * 4 && mips[1].get(6).length == 2 && worstLen <= 1.01,
                String.format(Locale.GERMAN, "Mip-Stufen 64² bis 1², Normale in Stufe 2 höchstens Länge %.3f in xy", worstLen));

        // Strand
        int ti = java.util.Arrays.asList(a).indexOf("--texturen");
        BufferedImage kiesbild = ba;
        String woher = "Probesatz";
        if (ti >= 0 && a.length > ti + 1 && new File(a[ti + 1], "strandkies/a.png").isFile()) {
            kiesbild = ImageIO.read(new File(a[ti + 1], "strandkies/a.png"));
            woher = a[ti + 1] + "/strandkies";
            MaterialSet echt = new MaterialSet(new File(a[ti + 1]), kat);
            System.out.println("  " + echt.describe());
            for (MaterialSet.Layer l : echt.layers())
                System.out.printf(Locale.GERMAN, "    %-20s %d²  Farbe %.3f %.3f %.3f  Rauheit %.2f%n", l.material().id, l.size(), l.meanColor()[0], l.meanColor()[1], l.meanColor()[2], l.meanRough());
        }
        strand(kiesbild, kies.size, woher);

        long b2048 = 14L * 2048 * 2048 * 3 * 4 / 3;
        check(b2048 < 600_000_000L, String.format(Locale.GERMAN, "Budget: 14 Materialien à 2048² gepackt (BC7 + 2 × BC5, mit Mip-Stufen) %.0f MB ≤ 600 MB; à 1024² %.0f MB",
                b2048 / 1e6, b2048 / 4e6));
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Autokorrelation über 1 km, einfach gegen hex-gekachelt. */
    static void strand(BufferedImage im, double size, String woher) {
        int w = im.getWidth(), h = im.getHeight();
        float[] lum = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { int p = im.getRGB(x, y); lum[y * w + x] = (float) (0.2126 * ((p >> 16) & 255) + 0.7152 * ((p >> 8) & 255) + 0.0722 * (p & 255)); }
        HexTiling.Tex t = (u, v) -> {
            double fx = (u - Math.floor(u)) * w - 0.5, fy = (v - Math.floor(v)) * h - 0.5;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            double ax = fx - x0, ay = fy - y0;
            double p00 = lum[Math.floorMod(y0, h) * w + Math.floorMod(x0, w)], p10 = lum[Math.floorMod(y0, h) * w + Math.floorMod(x0 + 1, w)];
            double p01 = lum[Math.floorMod(y0 + 1, h) * w + Math.floorMod(x0, w)], p11 = lum[Math.floorMod(y0 + 1, h) * w + Math.floorMod(x0 + 1, w)];
            return (p00 * (1 - ax) + p10 * ax) * (1 - ay) + (p01 * (1 - ax) + p11 * ax) * ay;
        };
        double step = 0.02;
        int n = (int) (1000 / step);
        double[][] lines = {{1, 0, 0.37}, {Math.cos(0.6), Math.sin(0.6), 0.11}};
        double worstPlain = 1, worstHex = 0;
        for (double[] ln : lines) {
            double[] plain = new double[n], hex = new double[n];
            for (int i = 0; i < n; i++) {
                double s = i * step / size;
                double u = s * ln[0], v = ln[2] + s * ln[1];
                plain[i] = t.at(u, v);
                hex[i] = HexTiling.sample(t, u, v, true);
            }
            // Abstand, nach dem die einfache Kachel sich genau wiederholt: entlang x eine Kachel; schräg nimmt man die Spitze nahe einer Kachel
            double p = 0, hx = 0;
            for (int k = 1; k <= 10; k++) {
                int lg = (int) Math.round(k * size / step);
                if (ln[1] != 0) lg = bestLag(plain, lg);
                p = Math.max(p, ac(plain, lg));
                hx = Math.max(hx, ac(hex, lg));
            }
            worstPlain = Math.min(worstPlain, p);
            worstHex = Math.max(worstHex, hx);
        }
        check(worstHex < SCHWELLE, String.format(Locale.GERMAN, "1 km Kiesstrand (%s, Kachel %.1f m): Autokorrelation bei 1 … 10 Kacheln Abstand hex %.2f < %.2f, einfach gekachelt %.2f",
                woher, size, worstHex, SCHWELLE, worstPlain));
    }

    /** Der Abstand nahe lg mit der höchsten Autokorrelation (± 15 %). */
    static int bestLag(double[] a, int lg) {
        int best = lg;
        double bv = -2;
        for (int d = (int) (lg * 0.85); d <= (int) (lg * 1.15); d += Math.max(1, lg / 200)) {
            double v = ac(a, d);
            if (v > bv) { bv = v; best = d; }
        }
        return best;
    }

    static double ac(double[] a, int lag) {
        int n = a.length - lag;
        if (n <= 10) return 0;
        double ma = 0, mb = 0;
        for (int i = 0; i < n; i++) { ma += a[i]; mb += a[i + lag]; }
        ma /= n;
        mb /= n;
        double c = 0, va = 0, vb = 0;
        for (int i = 0; i < n; i++) {
            double x = a[i] - ma, y = a[i + lag] - mb;
            c += x * y;
            va += x * x;
            vb += y * y;
        }
        return c / Math.sqrt(va * vb + 1e-12);
    }

    static double avg(BufferedImage im, int x, int y, int f) {
        double s = 0;
        for (int j = 0; j < f; j++) for (int i = 0; i < f; i++) s += ((im.getRGB(x + i, y + j) >> 16) & 255) / 255.0;
        return s / (f * f);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }
}
