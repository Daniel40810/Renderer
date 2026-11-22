package com.dan.lauf.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auszug eines Gebiets für einen Lauf mit Bild in der Arbeitsumgebung (P6e, Werkbuch HalligHooge Teil A, 12.7): G0, G1
 * und G2 ganz, G3 nur als Fenster, dazu Tide, Antrieb und Vorgabe. Im Auszug <i>ist</i> G3 das Fenster: seine Zeile in
 * {@code gebiet.txt}, die Packung und die Bauwerke (Kanten und Siele, Zellen relativ zum Fenster; die Ringe fallen
 * weg, hydro rechnet die Binnenseiten selbst; die Höhe je Zelle braucht der Lauf nicht). So läuft {@code Lauf} im Auszug ohne {@code fenster3} genauso wie im
 * ganzen Gebiet mit.
 * <pre>
 * java -cp dist\Renderer.jar com.dan.lauf.demo.LaufAuszug D:\Daten\HalligHooge\gebiet D:\Daten\HalligHooge\_p6\lauf [i0 j0 ni nj]
 * </pre>
 * Vorgabe des Fensters: Zellen 600, 850, 500 × 400 von G3 (O 466–476 km, N 6 042–6 050 km, ganz Hooge mit Watt).
 */
public final class LaufAuszug {
    private LaufAuszug() { }

    public static void main(String[] a) throws Exception {
        File geb = new File(a[0]), ziel = new File(a[1]);
        int[] w = a.length > 5 ? new int[] {Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5])} : new int[] {600, 850, 500, 400};
        Gebiet ge = Gebiet.lies(geb);
        Gitter g3 = ge.gitter("G3"), g2 = ge.gitter("G2");
        double r = g2.dx / g3.dx;
        if (w[0] % r != 0 || w[1] % r != 0 || w[2] % r != 0 || w[3] % r != 0) throw new IllegalArgumentException("Fenster nicht auf Zellen von G2 (Vielfache von " + r + ")");
        Gitter gw = Gitter.utm("G3", g3.zone, g3.x0 + w[0] * g3.dx, g3.y0 + w[1] * g3.dy, g3.dx, w[2], w[3]);
        ziel.mkdirs();
        long bytes = 0;
        for (String n : new String[] {"G0", "G1", "G2"}) bytes += kopiere(ge.eintrag(n).ordner().toPath(), ziel.toPath().resolve(n));
        for (String n : new String[] {"tide", "antrieb", "wind"}) {
            Path q = geb.toPath().resolve(n);
            if (Files.isDirectory(q)) bytes += kopiere(q, ziel.toPath().resolve(n));
        }
        if (ge.hatVorgabe()) bytes += kopiere(geb.toPath().resolve("vorgabe.txt"), ziel.toPath().resolve("vorgabe.txt"));
        File o = new File(ziel, "G3");
        o.mkdirs();
        Gebiet.schreibePackung(new File(o, "subgrid.bin"), ge.packung("G3").fenster(g3, w[0], w[1], w[2], w[3]), gw, ge.eintrag("G3").fein());
        int[] nb = bauwerke(new File(ge.eintrag("G3").ordner(), "bauwerke.txt"), new File(o, "bauwerke.txt"), gw, w);
        // gebiet.txt: alles außer der Zeile von G3 und den Dateien von G3; G3 ist das Fenster
        List<String> aus = new ArrayList<>();
        aus.add(String.format(java.util.Locale.ROOT, "# Auszug für P6e (Werkbuch 12.7, LaufAuszug): G3 ist das Fenster ab Zelle %d, %d, %d × %d des ganzen G3 %s", w[0], w[1], w[2], w[3], g3));
        aus.add("auszug\tG3\t" + w[0] + " " + w[1] + " " + w[2] + " " + w[3]);
        for (String zl : Files.readAllLines(new File(geb, "gebiet.txt").toPath(), StandardCharsets.UTF_8)) {
            if (zl.startsWith("gitter G3 ")) zl = Gebiet.zeile(gw, ge.eintrag("G3").fein());
            else if (zl.startsWith("datei\tG3/") || zl.startsWith("quelle\t")) continue;
            aus.add(zl);
        }
        Files.write(new File(ziel, "gebiet.txt").toPath(), aus, StandardCharsets.UTF_8);
        System.out.printf(java.util.Locale.ROOT, "Auszug nach %s: G0–G2, Tide, Antrieb %.0f MB; G3 als Fenster %s mit %d Kanten und %d Sielen%n", ziel, bytes / 1e6, gw, nb[0], nb[1]);
    }

    /** Kanten und Siele im Fenster, Zellen und Kanten neu gezählt. */
    static int[] bauwerke(File von, File nach, Gitter gw, int[] w) throws Exception {
        int ni = w[2], nj = w[3], nk = 0, ns = 0;
        Map<Integer, Integer> neu = new HashMap<>();
        List<String> zeilen = Files.readAllLines(von.toPath(), StandardCharsets.UTF_8), aus = new ArrayList<>();
        aus.add("# Auszug für P6e aus G3/bauwerke.txt: Fenster ab Zelle " + w[0] + ", " + w[1] + ", " + ni + " × " + nj + " (ohne Ringe)");
        aus.add(String.format(java.util.Locale.ROOT, "gitter G3 %.3f %.3f %.3f %d %d", gw.x0, gw.y0, gw.dx, ni, nj));
        for (String zl : zeilen) {
            if (zl.startsWith("fein")) aus.add(zl);
            if (!zl.startsWith("kante")) continue;
            String[] t = zl.strip().split("\\s+");
            boolean qx = t[2].equals("x");
            int i = Integer.parseInt(t[3]) - w[0], j = Integer.parseInt(t[4]) - w[1];
            if (!(qx ? i > 0 && i < ni && j >= 0 && j < nj : i >= 0 && i < ni && j > 0 && j < nj)) continue;
            int idx = qx ? i + (ni + 1) * j : (ni + 1) * nj + i + ni * j;
            neu.put(Integer.parseInt(t[1]), idx);
            t[1] = Integer.toString(idx);
            t[3] = Integer.toString(i);
            t[4] = Integer.toString(j);
            aus.add(String.join(" ", t));
            nk++;
        }
        for (String zl : zeilen) {
            if (!zl.startsWith("siel")) continue;
            String[] t = zl.strip().split("\\s+");
            Integer idx = neu.get(Integer.parseInt(t[2]));
            if (idx == null) continue;
            t[2] = Integer.toString(idx);
            aus.add(String.join(" ", t));
            ns++;
        }
        Files.write(nach.toPath(), aus, StandardCharsets.UTF_8);
        return new int[] {nk, ns};
    }

    /** Kopiert eine Datei oder einen Ordner (eine Ebene tief genügt hier, rekursiv zur Sicherheit); gibt die Byte zurück. */
    static long kopiere(Path q, Path z) throws Exception {
        if (Files.isRegularFile(q)) {
            Files.createDirectories(z.getParent());
            Files.copy(q, z, StandardCopyOption.REPLACE_EXISTING);
            return Files.size(z);
        }
        long n = 0;
        try (var s = Files.walk(q)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path d = z.resolve(q.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(d);
                else {
                    Files.copy(p, d, StandardCopyOption.REPLACE_EXISTING);
                    n += Files.size(d);
                }
            }
        }
        return n;
    }
}
