package com.dan.render.water.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Auszug für WaterCheck (P6d): aus einem Gebiet die tiefste Pixelhöhe je Zelle von G1, G2 und G3
 * ({@code <G>.zmin.f32}), die Bauwerke von G3 und ein Fenster von G3 als eigenes Gebiet ({@code fenster/}: Packung und
 * Bauwerke mit Zellen relativ zum Fenster). Klein genug, um es in eine Arbeitsumgebung zu tragen.
 * <pre>
 * java -cp dist\Renderer.jar com.dan.render.water.demo.WasserAuszug D:\Daten\HalligHooge\gebiet D:\Daten\HalligHooge\_p6\wasser [i0 j0 ni nj]
 * </pre>
 * Vorgabe des Fensters: Zellen 800, 1000, 100 × 100 von G3 (O 470–472 km, N 6 045–6 047 km).
 */
public final class WasserAuszug {
    public static void main(String[] a) throws Exception {
        File geb = new File(a[0]), ziel = new File(a[1]);
        int i0 = a.length > 5 ? Integer.parseInt(a[2]) : 800, j0 = a.length > 5 ? Integer.parseInt(a[3]) : 1000;
        int ni = a.length > 5 ? Integer.parseInt(a[4]) : 100, nj = a.length > 5 ? Integer.parseInt(a[5]) : 100;
        Gebiet ge = Gebiet.lies(geb);
        ziel.mkdirs();
        for (String n : new String[] {"G1", "G2", "G3"}) {
            Gitter g = ge.gitter(n);
            Subgrid.Packung p = ge.packung(n);
            float[] zmin = new float[g.zellen()];
            int S = p.stufen();
            for (int c = 0; c < zmin.length; c++) zmin[c] = (float) ((double) p.basis()[c] + Float.float16ToFloat(p.tabelle()[c * 3 * S]));
            Gebiet.schreibeF32(new File(ziel, n + ".zmin.f32"), zmin);
            System.out.println(n + " " + g + ": tiefste Pixelhöhe geschrieben");
            if (!n.equals("G3")) continue;
            Gitter gw = Gitter.utm("G3", g.zone, g.x0 + i0 * g.dx, g.y0 + j0 * g.dy, g.dx, ni, nj);
            File o = new File(ziel, "fenster/G3");
            o.mkdirs();
            Gebiet.schreibePackung(new File(o, "subgrid.bin"), p.fenster(g, i0, j0, ni, nj), gw, ge.eintrag("G3").fein());
            List<String> aus = new ArrayList<>();
            aus.add("# Auszug für P6d aus G3/bauwerke.txt: Fenster ab Zelle " + i0 + ", " + j0 + ", " + ni + " × " + nj);
            aus.add(String.format(Locale.ROOT, "gitter G3 %.3f %.3f %.3f %d %d", gw.x0, gw.y0, gw.dx, ni, nj));
            int nk = 0;
            File bw = new File(ge.eintrag("G3").ordner(), "bauwerke.txt");
            for (String z : Files.readAllLines(bw.toPath(), StandardCharsets.UTF_8)) {
                if (z.startsWith("fein")) aus.add(z);
                if (!z.startsWith("kante")) continue;
                String[] t = z.strip().split("\\s+");
                boolean qx = t[2].equals("x");
                int i = Integer.parseInt(t[3]) - i0, j = Integer.parseInt(t[4]) - j0;
                if (!(qx ? i > 0 && i < ni && j >= 0 && j < nj : i >= 0 && i < ni && j > 0 && j < nj)) continue;
                t[1] = Integer.toString(qx ? i + (ni + 1) * j : (ni + 1) * nj + i + ni * j);
                t[3] = Integer.toString(i);
                t[4] = Integer.toString(j);
                aus.add(String.join(" ", t));
                nk++;
            }
            Files.write(new File(o, "bauwerke.txt").toPath(), aus, StandardCharsets.UTF_8);
            Files.writeString(new File(ziel, "fenster/gebiet.txt").toPath(), "# Auszug für P6d (Werkbuch 12.7): Fenster von G3\n" + Gebiet.zeile(gw, ge.eintrag("G3").fein()) + "\n",
                    StandardCharsets.UTF_8);
            Files.copy(bw.toPath(), new File(ziel, "G3.bauwerke.txt").toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Fenster " + gw + ": " + nk + " Bauwerkskanten");
        }
    }
}
