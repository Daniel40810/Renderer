package com.dan.aufbereitung.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Bauten;
import com.dan.core.szene.Gestalt;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Schritts gestalt (P12b, Werkbuch HalligHooge Teil A, 12.13, Datei 69; Prüfsteine 7, 11 und 14).
 * <ol>
 * <li><b>Leser und Schreiber</b> der Gestaltkarte an zwei erzeugten Kacheln: bitgleich zurück, {@code bei} an
 * bekannten Orten, Wasserstand als Byte (w / 50 − 1 m NHN).</li>
 * <li><b>Gestalt im Gebiet</b> ({@code --gebiet}): {@code gestalt/} und {@code bau/koerper.bin} aus dem Schritt, die
 * Werte aus {@code gestalt/pruefung.txt}. Prüfstein 7 (Mittellinie der Gräben im Wasser, im Umkreis 1 m), Prüfstein 11
 * (jeder Fething zu ≥ 90 % Wasser), Prüfstein 14 (Fuß der Pfähle ≤ 0,3 m unter dem Watt), dazu die Flächen je Art und
 * die Körper.</li>
 * </ol>
 * <pre>
 * java -cp dist\Renderer.jar com.dan.aufbereitung.demo.GestaltCheck [--gebiet D:\Daten\HalligHooge\gebiet]
 * </pre>
 */
public final class GestaltCheck {
    private static final Attest A = new Attest("com.dan.aufbereitung (gestalt)", "0.1");

    public static void main(String[] args) {
        String gebiet = "D:\\Daten\\HalligHooge\\gebiet";
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--gebiet")) gebiet = args[i + 1];
        System.out.println("GestaltCheck – Schritt gestalt (Werkbuch HalligHooge Teil A, P12b)");
        try {
            leser();
            gebiet(new File(gebiet));
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P12b_Gestalt.md"), "P12b – GestaltCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1 Leser

    static void leser() throws Exception {
        A.teil("1 Leser und Schreiber");
        int n = 8;
        double zelle = 0.5;
        byte[] a = new byte[2 * n * n], b = new byte[2 * n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int o = 2 * (j * n + i);
                a[o] = (byte) (i < 4 ? Gestalt.FENNE : Gestalt.GRABEN);
                a[o + 1] = (byte) (i < 4 ? 0 : Gestalt.standByte(0.7));
                b[o] = (byte) (j == 0 ? Gestalt.KLINKER : Gestalt.WARFT);
            }
        Gestalt g = new Gestalt(zelle, n, List.of(new Gestalt.Kachel(1000, 2000, a), new Gestalt.Kachel(1004, 2000, b)));
        File d = Files.createTempDirectory("gestalt").toFile();
        g.schreibe(d, "Probe");
        Gestalt h = Gestalt.lies(d);
        boolean gleich = h != null && h.kacheln.size() == 2 && h.zelle == zelle && h.n == n;
        if (gleich) for (int k = 0; k < 2; k++) gleich &= Arrays.equals(g.kacheln.get(k).daten(), h.kacheln.get(k).daten());
        A.pruefe(gleich, "Gestaltkarte geschrieben und gelesen", gleich ? "2 Kacheln, bitgleich" : "anders", "bitgleich");
        // Zeile 0 ist die nördlichste: die Kachel reicht von 2000 bis 2004 nach Nord
        int[] w = h.bei(1003.2, 2003.1), x = h.bei(1001.0, 2001.0), y = h.bei(1005.0, 2003.9), z = h.bei(1005.0, 2000.2), aus = h.bei(990, 2000);
        boolean ok = w != null && w[0] == Gestalt.GRABEN && Math.abs(Gestalt.stand(w[1]) - 0.7) < 0.011 && x[0] == Gestalt.FENNE
                && y[0] == Gestalt.KLINKER && z[0] == Gestalt.WARFT && (aus == null || aus[0] == 0);
        A.pruefe(ok, "Art und Wasserstand an bekannten Orten (Zeile 0 im Norden)",
                f("Graben %s, Fenne %s, Klinker %s, Warft %s, außerhalb %s", Arrays.toString(w), Arrays.toString(x), Arrays.toString(y), Arrays.toString(z),
                        Arrays.toString(aus)), "wie gesetzt");
        double fehler = 0;
        for (double s = -0.98; s <= 4.0; s += 0.013) fehler = Math.max(fehler, Math.abs(Gestalt.stand(Gestalt.standByte(s)) - s));
        A.pruefe(fehler <= 0.01 + 1e-9, "Wasserstand als Byte (−0,98 … 4,0 m NHN)", f("größter Fehler %.4f m", fehler), "≤ 0,01 m");
        for (File f : d.listFiles()) Files.delete(f.toPath());
        Files.delete(d.toPath());
    }

    // ------------------------------------------------------------------ 2 Gebiet

    static void gebiet(File gb) throws Exception {
        A.teil("2 Gestalt im Gebiet");
        A.info("Gebiet", gb.getPath());
        Gestalt g = Gestalt.lies(new File(gb, "gestalt"));
        A.pruefe(g != null, "Gestaltkarte (gestalt/gestalt.txt und Kacheln)", g == null ? "fehlt" : f("%d Kacheln zu %.0f m, Zelle %.2f m", g.kacheln.size(), g.kantenlaenge(), g.zelle),
                "vorhanden");
        if (g == null) return;
        // Flächen je Art
        long[] zahl = new long[256];
        long stand = 0, standFehlt = 0;
        for (Gestalt.Kachel k : g.kacheln) {
            byte[] d = k.daten();
            for (int o = 0; o < d.length; o += 2) {
                int art = d[o] & 0xFF;
                zahl[art]++;
                if (Gestalt.wasser(art)) {
                    stand++;
                    if ((d[o + 1] & 0xFF) == 0) standFehlt++;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        boolean bekannt = true;
        double ha = g.zelle * g.zelle / 1e4;
        for (int a = 1; a < 256; a++)
            if (zahl[a] > 0) {
                String nm = Gestalt.name(a);
                if (nm.equals("keine")) bekannt = false;
                sb.append(sb.length() > 0 ? ", " : "").append(f("%s %.1f ha", nm, zahl[a] * ha));
            }
        A.info("Flächen je Art", sb.toString());
        A.pruefe(bekannt, "Nur bekannte Arten in der Karte", bekannt ? "ja" : "unbekannte Art", "ja");
        A.pruefe(standFehlt == 0, "Wasser hat einen Wasserstand", f("%d von %d Wasserpunkten ohne", standFehlt, stand), "0");
        // Werte des Schritts
        File pf = new File(new File(gb, "gestalt"), "pruefung.txt");
        A.pruefe(pf.isFile(), "Werte des Schritts (gestalt/pruefung.txt)", pf.isFile() ? "vorhanden" : "fehlt (Schritt gestalt neu laufen lassen)", "vorhanden");
        if (!pf.isFile()) return;
        Map<String, String> w = new HashMap<>();
        for (String z : Files.readAllLines(pf.toPath(), StandardCharsets.UTF_8)) {
            if (z.isBlank() || z.startsWith("#")) continue;
            int i = z.indexOf('=');
            if (i > 0) w.put(z.substring(0, i).strip(), z.substring(i + 1).strip());
        }
        A.info("Aus OSM", f("%s Fethinge, %s Gräben, %s Priele, %s Wege; %s Lahnungen (%s Pfähle), %s Steinbuhnen, %s Anleger und Stege",
                w.get("fethinge"), w.get("graeben"), w.get("priele"), w.get("wege"), w.get("lahnungen"), w.get("pfaehle"), w.get("buhnen"), w.get("anleger")));
        double lage = zahl(w, "lage_graben"), anteil = zahl(w, "anteil_fething"), fuss = zahl(w, "fuss_pfahl");
        int unter = (int) zahl(w, "fethinge_unter_90");
        A.pruefe(lage >= 0.95, "Prüfstein 7: Mittellinie der Gräben aus OSM (alle 1 m) mit Wasser im Umkreis von 1 m in der Karte",
                f("%.1f %% der Punkte", 100 * lage), "≥ 95 % [G]");
        A.pruefe(unter == 0, "Prüfstein 11: jeder Fething (Fläche aus OSM) zu ≥ 90 % Wasser in der Karte",
                f("im Mittel %.1f %%; unter 90 %%: %d", 100 * anteil, unter), "0 unter 90 %");
        A.pruefe(fuss >= 0 && fuss <= 0.3 + 1e-4, "Prüfstein 14: Fuß der Pfähle unter dem Watt (Mittel); Pfähle liegen auf den Linien der OSM",
                f("%.2f m", fuss), "≤ 0,3 m");
        // Körper
        Bauten k = Bauten.lies(new File(gb, "bau"), Bauten.KOERPER);
        A.pruefe(k != null, "Körper (bau/koerper.bin)", k == null ? "fehlt" : f("%d Körper, %d Dreiecke", k.gebaeude.size(), k.dreiecke()), "vorhanden");
        if (k == null) return;
        Map<String, Integer> arten = new TreeMap<>();
        for (Bauten.Gebaeude b : k.gebaeude) arten.merge(b.kennung.split(" ")[0], 1, Integer::sum);
        A.info("Körper je Art", arten.toString());
        long soll = (long) zahl(w, "dreiecke");
        A.pruefe(k.dreiecke() == soll, "Körper in der Datei wie im Schritt", f("%d Dreiecke, im Schritt %d", k.dreiecke(), soll), "gleich");
        List<String> tief = new ArrayList<>();
        for (Bauten.Gebaeude b : k.gebaeude) if (b.boden < -3 || b.first > 6) tief.add(b.kennung);
        A.pruefe(tief.isEmpty(), "Körper in plausibler Höhe (Boden ≥ −3 m, oben ≤ 6 m NHN)", tief.isEmpty() ? "alle" : tief.size() + " nicht: " + tief.get(0), "alle");
    }

    static double zahl(Map<String, String> w, String s) {
        String v = w.get(s);
        return v == null ? Double.NaN : Double.parseDouble(v);
    }
}
