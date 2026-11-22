package com.dan.lauf.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.pruef.Attest;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Buehne;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der umgezogenen Daten (seit D1, Werkbuch HalligHooge 162) ohne Fenster und Grafikkarte: je Archiv der neue Ort
 * gegen den alten, so wie die App liest ({@link Archivquelle}).
 * <ol>
 * <li>Prüfstein D1: das Archiv öffnet am neuen Ort; an 12 Ständen ist jeder Wert aller Teile, die das Bild liest
 * (Wasser, Wind, Seegang, Sediment, Boden, Nässe, Luft, Flugsand, Strömung, Durchfluss), Bit für Bit wie am alten;</li>
 * <li>Prüfstein D2: eine Sonde über alle Stände (Zelle in der Mitte des feinsten Gitters) ≤ 3 s [G], gleich dem alten
 * Ort an jedem 40. Stand;</li>
 * <li>Prüfstein D3: Abspielen wie die Bildschleife mit Zeitraffer 1 800 für 12 s – die Uhr hält zusammen höchstens
 * 0,5 s [G] (der Vorleser kommt mit dem Entpacken nach);</li>
 * <li>Prüfstein D4: die Daten am neuen Ort zusammen ≤ 18 GB [G].</li>
 * </ol>
 * <pre>
 * java -cp Renderer.jar com.dan.lauf.demo.DatenCheck --neu daten --alt D:\Daten\HalligHooge [--nur _p6/archiv]
 * </pre>
 */
public final class DatenCheck {
    private static final Attest A = new Attest("Daten der App (D1)", "D1");
    static final double RAFFER = 1800, SPIELEN_S = 12, HALTEN_HOECHSTENS = 0.5, SONDE_S = 3, GB_HOECHSTENS = 18;

    public static void main(String[] args) {
        Path neu = null, alt = null;
        String nur = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--neu" -> neu = Path.of(args[++i]);
                case "--alt" -> alt = Path.of(args[++i]);
                case "--nur" -> nur = args[++i].replace('\\', '/');
                default -> throw new IllegalArgumentException("unbekannt: " + args[i]);
            }
        }
        System.out.println("DatenCheck – die Daten der App am neuen Ort (Werkbuch HalligHooge 162, D1)");
        try {
            if (neu == null) throw new IllegalArgumentException("--neu fehlt");
            List<String> archive = archive(neu);
            if (nur != null) {
                String n = nur;
                archive.removeIf(a -> !a.equals(n));
            }
            A.info("Archive", archive.size() + " unter " + neu + (alt == null ? "" : ", verglichen mit " + alt));
            for (String a : archive) archiv(neu.resolve(a), alt == null ? null : alt.resolve(a), a);
            if (nur == null) {
                A.teil("Größe");
                long n = 0;
                try (Stream<Path> s = Files.walk(neu)) {
                    for (Path p : s.filter(Files::isRegularFile).toList()) n += Files.size(p);
                }
                A.pruefe(n / 1073741824.0 <= GB_HOECHSTENS, "Prüfstein D4: die Daten am neuen Ort zusammen", f("%.2f GB", n / 1073741824.0), f("≤ %.0f GB [G]", GB_HOECHSTENS));
            }
        } catch (Throwable x) {
            A.fehler(x);
        }
        Path at = A.schreibe(Path.of("docs", "status", "D1_Daten.md"), "D1 – Daten der App");
        if (at != null) System.out.println("\nAttest: " + at);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Die Archive unter dem neuen Ort: Ordner, deren Teilordner Läufe sind (relativ, mit /). */
    static List<String> archive(Path wurzel) throws java.io.IOException {
        List<String> l = new ArrayList<>();
        try (Stream<Path> s = Files.walk(wurzel, 2)) {
            for (Path p : s.filter(Files::isDirectory).sorted().toList()) {
                if (p.equals(wurzel) || p.getFileName().toString().equals("gebiet") || p.startsWith(wurzel.resolve("gebiet"))) continue;
                boolean lauf = false;
                try (Stream<Path> t = Files.list(p)) {
                    for (Path q : t.toList()) lauf |= Files.isRegularFile(q.resolve("lauf.txt")) && Files.isRegularFile(q.resolve("zeiten.f64"));
                }
                if (lauf) l.add(wurzel.relativize(p).toString().replace('\\', '/'));
            }
        }
        return l;
    }

    static void archiv(Path neu, Path alt, String name) throws Exception {
        A.teil(name);
        long o0 = System.nanoTime();
        try (Archivquelle n = new Archivquelle(neu); Archivquelle o = alt == null ? null : new Archivquelle(alt)) {
            double oeffnen = (System.nanoTime() - o0) / 1e9;
            double[] z = n.zeiten();
            List<Gitter> gs = n.gitter();
            // D1: zwölf Stände, alles, was das Bild liest
            long werte = 0, abw = 0;
            String erste = "";
            if (o != null) {
                SplittableRandom r = new SplittableRandom(162);
                int[] ks = new int[12];
                for (int i = 0; i < ks.length; i++) ks[i] = i == 0 ? 0 : i == 1 ? z.length - 1 : r.nextInt(z.length);
                for (int k : ks) {
                    double ts = z[k];
                    for (int g = 0; g < gs.size(); g++) {
                        int c = gs.get(g).zellen();
                        float[][] a = {new float[c], new float[c], new float[c]}, b = {new float[c], new float[c], new float[c]};
                        n.stand(ts, g, a[0], a[1], a[2]);
                        o.stand(ts, g, b[0], b[1], b[2]);
                        for (int q = 0; q < 3; q++) {
                            long d = vergleiche(a[q], b[q]);
                            if (d > 0 && erste.isEmpty()) erste = "Wasser " + gs.get(g).name + " Feld " + q + " Stand " + k;
                            abw += d;
                            werte += c;
                        }
                    }
                    if (n.windGitter() != null) {
                        int c = n.windGitter().zellen();
                        float[] u = new float[c], v = new float[c], u2 = new float[c], v2 = new float[c];
                        n.wind(ts, u, v);
                        o.wind(ts, u2, v2);
                        abw += vergleiche(u, u2) + vergleiche(v, v2);
                        werte += 2L * c;
                    }
                }
                long[] e = nebenfelder(n, o, r);
                werte += e[0];
                abw += e[1];
                if (e[1] > 0 && erste.isEmpty()) erste = "Nebenfelder";
                A.pruefe(abw == 0, "Prüfstein D1: neuer Ort = alter Ort, Bit für Bit (12 Stände, alle Teile des Bilds, Strömung, Durchfluss)",
                        f("%,d Werte verglichen, %d verschieden%s; geöffnet in %.2f s", werte, abw, erste.isEmpty() ? "" : " (zuerst " + erste + ")", oeffnen), "0 verschieden");
            } else A.info("Öffnen", f("%.2f s, %d Stände, %d Gitter", oeffnen, z.length, gs.size()));
            // D2: Sonde über alle Stände
            int g = gs.size() - 1;
            Gitter fein = gs.get(g);
            int zelle = (fein.ny / 2) * fein.nx + fein.nx / 2;
            float[] aus = new float[3], aus2 = new float[3];
            long s0 = System.nanoTime();
            for (double ts : z) n.standZelle(ts, g, zelle, aus);
            double sonde = (System.nanoTime() - s0) / 1e9;
            long sAbw = 0;
            if (o != null)
                for (int k = 0; k < z.length; k += 40) {
                    n.standZelle(z[k], g, zelle, aus);
                    o.standZelle(z[k], g, zelle, aus2);
                    for (int q = 0; q < 3; q++) if (Float.floatToRawIntBits(aus[q]) != Float.floatToRawIntBits(aus2[q])) sAbw++;
                }
            A.pruefe(sonde <= SONDE_S && sAbw == 0, "Prüfstein D2: Sonde über alle Stände (Mitte von " + fein.name + ")",
                    f("%d Stände in %.2f s; gleich dem alten Ort an jedem 40. Stand: %s", z.length, sonde, sAbw == 0 ? "ja" : sAbw + " verschieden"), f("≤ %.0f s [G]", SONDE_S));
            // D3: Abspielen mit Zeitraffer 1 800
            Spiel s = spiele(n, z[Math.min(z.length - 1, z.length / 4)], RAFFER, SPIELEN_S);
            A.pruefe(s.halten <= HALTEN_HOECHSTENS, "Prüfstein D3: Abspielen mit Zeitraffer 1 800 wie die Bildschleife",
                    f("%.0f s Szenenzeit in %.1f s, die Uhr hielt %.2f s (%d Mal), Lesen im Bild größte %.1f ms; Vorleser %d Stände, %.0f MB gelesen",
                            s.weit, s.sekunden, s.halten, s.halteZahl, s.lesenMax, n.vorStaende, n.vorBytes / 1048576.0),
                    f("Halten ≤ %.1f s [G]", HALTEN_HOECHSTENS));
        }
    }

    static long vergleiche(float[] a, float[] b) {
        long d = 0;
        for (int i = 0; i < a.length; i++) if (Float.floatToRawIntBits(a[i]) != Float.floatToRawIntBits(b[i])) d++;
        return d;
    }

    /** Seegang, Sediment, Boden, Nässe, Luft, Flugsand, Strömung und Durchfluss an je drei Ständen. {Werte, verschieden}. */
    static long[] nebenfelder(Archivquelle n, Archivquelle o, SplittableRandom r) {
        long w = 0, d = 0;
        List<Gitter> see = n.seegangGitter();
        double[] sz = n.seegangZeiten();
        for (int i = 0; i < 3 && sz.length > 0; i++) {
            double ts = sz[r.nextInt(sz.length)];
            for (int k = 0; k < see.size(); k++) {
                int c = see.get(k).zellen();
                float[][] a = new float[5][c], b = new float[5][c];
                if (!n.seegang(ts, k, a) || !o.seegang(ts, k, b)) continue;
                for (int q = 0; q < 5; q++) {
                    d += vergleiche(a[q], b[q]);
                    w += c;
                }
            }
        }
        if (n.sedimentGitter() != null) {
            double[] zs = n.sedimentZeiten();
            int c = n.sedimentGitter().zellen() * 2;
            for (int i = 0; i < 3; i++) {
                double ts = zs[r.nextInt(zs.length)];
                float[] a = new float[c], b = new float[c];
                if (n.sediment(ts, a) && o.sediment(ts, b)) {
                    d += vergleiche(a, b);
                    w += c;
                }
            }
            double[] zb = n.bodenZeiten();
            for (int i = 0; i < 3 && zb.length > 0; i++) {
                double ts = zb[r.nextInt(zb.length)];
                int cz = n.sedimentGitter().zellen();
                float[] ra = new float[3 * cz], rb = new float[3 * cz], sa = new float[cz], sb = new float[cz], da = new float[cz], db = new float[cz];
                if (n.boden(ts, ra, sa, da) && o.boden(ts, rb, sb, db)) {
                    d += vergleiche(ra, rb) + vergleiche(sa, sb) + vergleiche(da, db);
                    w += 5L * cz;
                }
            }
        }
        if (n.atmosGitter() != null) {
            double[] za = n.atmosZeiten();
            int c = n.atmosGitter().zellen();
            for (int i = 0; i < 3; i++) {
                double ts = za[r.nextInt(za.length)];
                float[] a = new float[c], b = new float[c];
                if (n.naesse(ts, a) && o.naesse(ts, b)) {
                    d += vergleiche(a, b);
                    w += c;
                }
            }
        }
        if (n.luftGitter() != null) {
            double[] zl = n.luftZeiten();
            int c = n.luftGitter().zellen() * n.luftStufen();
            for (int i = 0; i < 3; i++) {
                double ts = zl[r.nextInt(zl.length)];
                float[] a = new float[c], b = new float[c];
                if (n.luft(ts, a) && o.luft(ts, b)) {
                    d += vergleiche(a, b);
                    w += c;
                }
            }
        }
        if (n.flugGitter() != null) {
            double[] zf = n.flugZeiten();
            int c = n.flugGitter().zellen();
            for (int i = 0; i < 3; i++) {
                double ts = zf[r.nextInt(zf.length)];
                float[] a = new float[c], b = new float[c];
                if (n.flugsand(ts, a) && o.flugsand(ts, b)) {
                    d += vergleiche(a, b);
                    w += c;
                }
            }
        }
        if (n.stroemungGitter() != null) {
            Gitter g = n.stroemungGitter();
            double[] zs = n.stroemungZeiten();
            int c = g.zellen(), kanten = (g.nx + 1) * g.ny + g.nx * (g.ny + 1);
            for (int i = 0; i < 3; i++) {
                double ts = zs[r.nextInt(zs.length)];
                float[] u = new float[c], v = new float[c], u2 = new float[c], v2 = new float[c], q = new float[kanten], q2 = new float[kanten];
                if (n.stroemung(ts, u, v) && o.stroemung(ts, u2, v2)) {
                    d += vergleiche(u, u2) + vergleiche(v, v2);
                    w += 2L * c;
                }
                if (n.fluss(ts, q) && o.fluss(ts, q2)) {
                    d += vergleiche(q, q2);
                    w += kanten;
                }
            }
        }
        return new long[] {w, d};
    }

    record Spiel(double sekunden, double weit, double halten, int halteZahl, double lesenMax) { }

    /** Spielt wie die Bildschleife (60 Bilder/s): je Bild vorlesen und bereit fragen; bereit: die beiden Stände um t lesen, sonst hält die Uhr. */
    static Spiel spiele(Archivquelle q, double t, double raffer, double sekunden) throws Exception {
        double[] z = q.zeiten();
        double bild = 1 / 60.0, uhr = t, halten = 0, lesenMax = 0;
        int halteZahl = 0, paar = -1;
        boolean hielt = false;
        VorleseCheck.lies(q, t);
        long n0 = System.nanoTime();
        while ((System.nanoTime() - n0) / 1e9 < sekunden) {
            double gewollt = Math.min(z[z.length - 1], uhr + raffer * bild);
            q.vorlesen(gewollt, raffer, Buehne.VORLESEN_S);
            if (q.bereit(gewollt)) {
                uhr = gewollt;
                int k = VorleseCheck.index(z, uhr);
                if (k != paar) {
                    long l0 = System.nanoTime();
                    VorleseCheck.lies(q, z[k]);
                    if (k + 1 < z.length) VorleseCheck.lies(q, z[k + 1]);
                    lesenMax = Math.max(lesenMax, (System.nanoTime() - l0) / 1e6);
                    paar = k;
                }
                hielt = false;
            } else {
                halten += bild;
                if (!hielt) halteZahl++;
                hielt = true;
            }
            if (uhr >= z[z.length - 1]) break;
            Thread.sleep((long) (bild * 1000));
        }
        return new Spiel((System.nanoTime() - n0) / 1e9, uhr - t, halten, halteZahl, lesenMax);
    }

    static String text(Path p) throws java.io.IOException { return Files.readString(p, StandardCharsets.UTF_8); }
}
