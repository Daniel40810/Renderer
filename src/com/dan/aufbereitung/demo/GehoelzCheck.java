package com.dan.aufbereitung.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Bauten;
import com.dan.core.szene.Gehoelz;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Schritts gehoelz (P12c, Werkbuch HalligHooge Teil A, 12.13, Dateien 75, 76; Prüfstein 17, Teil der Daten).
 * <ol>
 * <li><b>Leser und Schreiber</b> von {@code gehoelz.bin} an zwei erzeugten Gehölzen; Halbmesser der Krone zwischen den
 * acht Richtungen.</li>
 * <li><b>Gehölze im Gebiet</b> ({@code --gebiet}): Prüfstein 17 (Daten): Pflanzenfläche mit nDOM ≥ 2 m unter einer Krone
 * ≥ 85 % [G], Kronenfläche ohne Pflanze ≤ 30 % [G]; kein Gehölz mitten in einem Haus; Höhen und Kronen plausibel.</li>
 * </ol>
 * <pre>
 * java -cp dist\Renderer.jar com.dan.aufbereitung.demo.GehoelzCheck [--gebiet D:\Daten\HalligHooge\gebiet]
 * </pre>
 */
public final class GehoelzCheck {
    private static final Attest A = new Attest("com.dan.aufbereitung (gehoelz)", "0.1");

    public static void main(String[] args) {
        String gebiet = "D:\\Daten\\HalligHooge\\gebiet";
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--gebiet")) gebiet = args[i + 1];
        System.out.println("GehoelzCheck – Schritt gehoelz (Werkbuch HalligHooge Teil A, P12c)");
        try {
            leser();
            gebiet(new File(gebiet));
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P12c_Gehoelz.md"), "P12c – GehoelzCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void leser() throws Exception {
        A.teil("1 Leser und Schreiber");
        List<Gehoelz.Pflanze> l = new ArrayList<>();
        for (int k = 0; k < 2; k++) {
            Gehoelz.Pflanze p = new Gehoelz.Pflanze();
            p.x = 10 + k; p.z = -20.5f; p.boden = 4.2f; p.hoehe = 7.5f + k; p.art = k == 0 ? Gehoelz.ESCHE : Gehoelz.HUNDSROSE; p.flaeche = 12;
            for (int r = 0; r < Gehoelz.RICHTUNGEN; r++) p.krone[r] = 1 + r;
            l.add(p);
        }
        Gehoelz g = new Gehoelz(470000, 6047000, l);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        g.schreibe(o);
        Gehoelz h = Gehoelz.lies(new ByteArrayInputStream(o.toByteArray()));
        boolean gleich = h.e0 == g.e0 && h.n0 == g.n0 && h.pflanzen.size() == 2;
        for (int k = 0; gleich && k < 2; k++) {
            Gehoelz.Pflanze a = g.pflanzen.get(k), b = h.pflanzen.get(k);
            gleich = a.x == b.x && a.z == b.z && a.boden == b.boden && a.hoehe == b.hoehe && a.art == b.art && a.flaeche == b.flaeche
                    && Arrays.equals(a.krone, b.krone);
        }
        A.pruefe(gleich, "gehoelz.bin geschrieben und gelesen", gleich ? "2 Gehölze, gleich" : "anders", "gleich");
        Gehoelz.Pflanze p = h.pflanzen.get(0);
        double ost = p.kroneIn(0), nordost = p.kroneIn(Math.PI / 4), zwischen = p.kroneIn(Math.PI / 8), west = p.kroneIn(Math.PI);
        boolean ok = Math.abs(ost - 1) < 1e-6 && Math.abs(nordost - 2) < 1e-6 && Math.abs(zwischen - 1.5) < 1e-6 && Math.abs(west - 5) < 1e-6;
        A.pruefe(ok, "Halbmesser der Krone nach Richtung (0 = Ost, gegen den Uhrzeigersinn)",
                f("Ost %.2f, Nordost %.2f, dazwischen %.2f, West %.2f", ost, nordost, zwischen, west), "1, 2, 1,5, 5");
    }

    static void gebiet(File gb) throws Exception {
        A.teil("2 Gehölze im Gebiet");
        A.info("Gebiet", gb.getPath());
        File bau = new File(gb, "bau");
        Gehoelz g = Gehoelz.lies(bau);
        A.pruefe(g != null, "Gehölze (bau/gehoelz.bin)", g == null ? "fehlt" : g.pflanzen.size() + " Gehölze", "vorhanden");
        if (g == null) return;
        int[] z = g.zahlJeArt();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Gehoelz.ARTEN; i++) s.append(i > 0 ? ", " : "").append(Gehoelz.name(i)).append(' ').append(z[i]);
        A.info("Arten (Regeln [G])", s.toString());
        float[] hs = new float[g.pflanzen.size()];
        int i = 0, falsch = 0;
        double kmax = 0;
        for (Gehoelz.Pflanze p : g.pflanzen) {
            hs[i++] = p.hoehe;
            kmax = Math.max(kmax, p.kroneMax());
            if (!(p.hoehe >= 1 && p.hoehe <= 30) || !(p.kroneMax() > 0 && p.kroneMax() <= 12) || p.art < 0 || p.art >= Gehoelz.ARTEN) falsch++;
        }
        Arrays.sort(hs);
        A.info("Höhe über dem Boden", hs.length == 0 ? "–" : f("Median %.1f m, 90 %% %.1f m, größte %.1f m; größter Halbmesser der Krone %.1f m",
                hs[hs.length / 2], hs[(int) (0.9 * (hs.length - 1))], hs[hs.length - 1], kmax));
        A.pruefe(falsch == 0, "Höhe 1 … 30 m, Halbmesser der Krone bis 12 m, Art bekannt", f("%d nicht", falsch), "0");
        // kein Gehölz mitten in einem Haus
        Bauten b = Bauten.lies(bau);
        if (b != null) {
            int drin = 0;
            for (Gehoelz.Pflanze p : g.pflanzen) {
                float[] sc = b.scheitel;
                for (int t = 0; t + 2 < sc.length / Bauten.FLOATS && drin >= 0; t += 3) {
                    double[] xs = new double[3], zs = new double[3];
                    for (int v = 0; v < 3; v++) { xs[v] = sc[(t + v) * Bauten.FLOATS]; zs[v] = sc[(t + v) * Bauten.FLOATS + 2]; }
                    if (innen(p.x, p.z, xs, zs)) { drin++; break; }
                }
            }
            A.pruefe(drin == 0, "Kein Gehölz mit dem Gipfel im Grundriss eines Hauses", f("%d von %d", drin, g.pflanzen.size()), "0");
        }
        // Prüfstein 17 (Daten)
        File pf = new File(bau, "gehoelz_pruefung.txt");
        A.pruefe(pf.isFile(), "Werte des Schritts (bau/gehoelz_pruefung.txt)", pf.isFile() ? "vorhanden" : "fehlt", "vorhanden");
        if (!pf.isFile()) return;
        Map<String, String> w = new HashMap<>();
        for (String zl : Files.readAllLines(pf.toPath(), StandardCharsets.UTF_8)) {
            if (zl.isBlank() || zl.startsWith("#")) continue;
            int k = zl.indexOf('=');
            if (k > 0) w.put(zl.substring(0, k).strip(), zl.substring(k + 1).strip());
        }
        double zelle = Double.parseDouble(w.get("zelle")), q = zelle * zelle;
        long hoch = Long.parseLong(w.get("hoch")), bed = Long.parseLong(w.get("hoch_bedeckt")), kr = Long.parseLong(w.get("krone")), leer = Long.parseLong(w.get("krone_leer"));
        A.info("Kacheln", w.get("kacheln"));
        A.pruefe(hoch > 0 && bed >= 0.85 * hoch, "Prüfstein 17 (Daten): Pflanzenfläche mit nDOM ≥ 2 m unter einer Krone",
                f("%.1f %% von %.0f m²", 100.0 * bed / Math.max(1, hoch), hoch * q), "≥ 85 % [G]");
        A.pruefe(kr > 0 && leer <= 0.30 * kr, "Prüfstein 17 (Daten): Kronenfläche ohne Pflanze (nDOM < 1 m oder NDVI < 0,15)",
                f("%.1f %% von %.0f m²", 100.0 * leer / Math.max(1, kr), kr * q), "≤ 30 % [G]");
    }

    static boolean innen(double x, double z, double[] xs, double[] zs) {
        double fl = (xs[1] - xs[0]) * (zs[2] - zs[0]) - (xs[2] - xs[0]) * (zs[1] - zs[0]);
        if (Math.abs(fl) < 1e-3) return false;                                     // Wand: im Grundriss eine Linie
        double d1 = (x - xs[1]) * (zs[0] - zs[1]) - (xs[0] - xs[1]) * (z - zs[1]);
        double d2 = (x - xs[2]) * (zs[1] - zs[2]) - (xs[1] - xs[2]) * (z - zs[2]);
        double d3 = (x - xs[0]) * (zs[2] - zs[0]) - (xs[2] - xs[0]) * (z - zs[0]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }
}
