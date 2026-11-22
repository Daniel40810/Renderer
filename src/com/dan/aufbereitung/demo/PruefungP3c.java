package com.dan.aufbereitung.demo;

import com.dan.aufbereitung.Bauwerke;
import com.dan.aufbereitung.Dgm1;
import com.dan.aufbereitung.Fuellwert;
import com.dan.aufbereitung.GeoTiff;
import com.dan.aufbereitung.Json;
import com.dan.aufbereitung.Osm;
import com.dan.aufbereitung.Rauheit;
import com.dan.aufbereitung.Rezept;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.raum.Utm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import static com.dan.aufbereitung.demo.AufbereitungCheck.A;
import static com.dan.core.pruef.Attest.f;

/**
 * Die Teile der Prüfung für P3c (Werkbuch HalligHooge Teil A, 12.4): ein Bit je Pixel und CCITT Gruppe 4, JSON und
 * OSM, Bauwerke und Rauheit an erzeugten Daten und, mit Rezept, am Gebiet (Prüfsteine 10–13 und 17).
 */
final class PruefungP3c {
    private PruefungP3c() { }

    /** Prüfstein 11: größter Abstand einer Deichlinie zur nächsten Deichkante (m) [G]; Prüfstein 12: Anteil [G]; 13: Rinne (m) und Abstand (m) [G]. */
    static final double ABSTAND = 20, SEITE = 0.03, RINNE = 0.5, SIEL = 20;

    // ------------------------------------------------------------------ ein Bit, Gruppe 4

    /** Mit PIL 12.3 (libtiff) geschrieben: 2601 × 24 Gruppe 4, 97 × 24 PackBits und ohne Kompression, MinIsBlack. */
    private static final String G4 = "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";
    private static final String PB = "SUkqACwBAAD+/wD++AAM4f//8B///wH///AfgAzh///wH///Af//8B+A//8K+A///4D///gP/4D//wr4D///gP//+A//gPX/AIAM//wH///Af//8B///gAz//Af//8B///wH//+ADP/8B///wH///Af//4AMHgP//+A///4D///gAAweA///4D///gP//+AA/v8A/vgADOH///Af//8B///wH4AM4f//8B///wH///AfgAzh///wH///Af//8B+A//8K+A///4D///gP/4D1/wCA//8K+A///4D///gP/4AM//wH///Af//8B///gAz//Af//8B///wH//+ADP/8B///wH///Af//4AMHgP//+A///4D///gAP7/AP74AAweA///4D///gP//+AACQAAAQMAAQAAAGEAAAABAQMAAQAAABgAAAACAQMAAQAAAAEAAAADAQMAAQAAAAWAAAAGAQMAAQAAAAEAAAARAQQAAQAAAAgAAAAWAQMAAQAAABgAAAAXAQQAAQAAACQBAAAcAQMAAQAAAAEAAAAAAAAA";
    private static final String ROH = "SUkqAAgAAAAIAAABBAABAAAAYQAAAAEBBAABAAAAGAAAAAMBAwABAAAAAQAAAAYBAwABAAAAAQAAABEBBAABAAAAbgAAABYBBAABAAAAGAAAABcBBAABAAAAOAEAABwBAwABAAAAAQAAAAAAAAD////+AAAAAAAAAAAA4f//8B///wH///AfgOH///Af//8B///wH4D///gP//+A///4D/+A///4D///gP//+A//gP///////////////4D//Af//8B///wH//+A//wH///Af//8B///gP/8B///wH///Af//4AeA///4D///gP//+AAHgP//+A///4D///gAP////4AAAAAAAAAAADh///wH///Af//8B+A4f//8B///wH///AfgOH///Af//8B///wH4D///gP//+A///4D/+A////////////////gP//+A///4D///gP/4D//Af//8B///wH//+A//wH///Af//8B///gP/8B///wH///Af//4AeA///4D///gP//+AA/////gAAAAAAAAAAAB4D///gP//+A///4AA=";

    static boolean schwarz(int x, int y) {
        if (y % 11 == 0) return 30 < x && x < 2500;                                        // Läufe über 1792: erweiterte Codes
        if (y % 11 == 5) return false;
        return ((x / 7) + (y / 3) + (x % 97 < 3 ? 1 : 0)) % 4 == 0;
    }

    static void einBit(Path tmp) throws IOException {
        String[][] f = {{"Gruppe 4 (CCITT T.6)", G4}, {"PackBits", PB}, {"ohne Kompression", ROH}};
        for (String[] t : f) {
            Path p = tmp.resolve("bit.tif");
            Files.write(p, Base64.getDecoder().decode(t[1]));
            try (GeoTiff g = GeoTiff.oeffne(p)) {
                float[] v = g.lies(0, 0, g.breite, g.hoehe);
                int falsch = 0;
                for (int y = 0; y < g.hoehe; y++)
                    for (int x = 0; x < g.breite; x++) {
                        boolean ink = g.photometrie == 0 ? v[y * g.breite + x] == 1 : v[y * g.breite + x] == 0;
                        if (ink != schwarz(x, y)) falsch++;
                    }
                float[] w = g.lies(g.breite - 20, 7, 30, 5);                                   // Fenster ragt hinaus
                boolean rand = Float.isNaN(w[25]) && w[0] == v[7 * g.breite + g.breite - 20];
                A.pruefe(falsch == 0 && rand, f("Ein Bit je Pixel, %s, %d × %d (libtiff), MinIsBlack, Läufe bis 2601", t[0], g.breite, g.hoehe),
                        f("%d Pixel falsch%s", falsch, rand ? "" : ", Fenster falsch"), "0");
            }
        }
    }

    // ------------------------------------------------------------------ JSON und OSM

    static void osm() throws IOException {
        Object j = Json.text("{\"a\": [1, -2.5e2, true, false, null, \"x\\u00fc\\n\\\"\"], \"b\": {}, \"c\": 12345678901}");
        Map<String, Object> m = Json.objekt(j);
        List<Object> l = Json.feld(m.get("a"));
        boolean ok = l.size() == 6 && Long.valueOf(1).equals(l.get(0)) && Double.valueOf(-250).equals(l.get(1)) && Boolean.TRUE.equals(l.get(2)) && l.get(4) == null
                && "xü\n\"".equals(l.get(5)) && Json.objekt(m.get("b")).isEmpty() && Long.valueOf(12345678901L).equals(m.get("c"));
        boolean fehler = false;
        try {
            Json.text("{\"a\": [1, 2}");
        } catch (IOException e) {
            fehler = e.getMessage().contains("Zeile 1");
        }
        A.pruefe(ok && fehler, "JSON: Zahlen, Wahrheitswerte, null, Escapes, leere Objekte; Fehler mit Ort", ok && fehler ? "gleich" : "falsch", "gleich");
        // Overpass: Punkt, Weg, geschlossener Weg, Multipolygon aus drei Teilen mit einem Loch
        double x0 = 471000, y0 = 6046000;
        StringBuilder s = new StringBuilder("{\"version\": 0.6, \"osm3s\": {\"timestamp_osm_base\": \"2026-11-11T14:42:51Z\"}, \"elements\": [\n");
        s.append(knoten(1, x0, y0, "\"waterway\": \"lock_gate\"")).append(",\n");
        s.append(weg(2, new double[][] {{x0, y0}, {x0 + 100, y0}, {x0 + 100, y0 + 50}}, "\"man_made\": \"dyke\", \"name\": \"Süd\"")).append(",\n");
        s.append(weg(3, new double[][] {{x0, y0}, {x0 + 10, y0}, {x0 + 10, y0 + 10}, {x0, y0 + 10}, {x0, y0}}, "\"building\": \"yes\"")).append(",\n");
        double a = x0 + 500, b = y0;
        s.append("{\"type\": \"relation\", \"id\": 4, \"tags\": {\"type\": \"multipolygon\", \"natural\": \"sand\"}, \"members\": [")
                .append(glied(new double[][] {{a, b}, {a + 200, b}, {a + 200, b + 100}}, "outer")).append(", ")
                .append(glied(new double[][] {{a, b + 200}, {a + 200, b + 200}, {a + 200, b + 100}}, "outer")).append(", ")      // umgekehrt
                .append(glied(new double[][] {{a, b + 200}, {a, b}}, "outer")).append(", ")
                .append(glied(new double[][] {{a + 50, b + 50}, {a + 150, b + 50}, {a + 150, b + 150}, {a + 50, b + 150}, {a + 50, b + 50}}, "inner"))
                .append("]}\n]}");
        Osm o = Osm.aus(s.toString(), 32);
        Osm.Weg w = o.wege.get(0);
        double dl = Math.abs(w.laenge() - 150);
        Osm.Flaeche mp = null;
        for (Osm.Flaeche q : o.flaechen) if (q.relation()) mp = q;
        double fl = 0;
        if (mp != null) for (double[][] r : mp.ringe()) fl += Math.abs(flaeche(r));
        boolean ringe = mp != null && mp.ringe().size() == 2 && Math.abs(fl - (200 * 200 + 100 * 100)) < 1;
        A.pruefe(o.punkte.size() == 1 && o.wege.size() == 2 && o.flaechen.size() == 2 && dl < 1e-3 && ringe && "Süd".equals(w.tags().get("name")) && o.offeneRinge == 0,
                "Overpass (out body geom): Punkt, Weg, geschlossener Weg, Multipolygon aus drei Wegen (einer umgekehrt) und einem Loch; nach UTM und zurück",
                f("%d Punkte, %d Wege, %d Flächen, Länge %.4f m, Ringe %d mit %.1f m²", o.punkte.size(), o.wege.size(), o.flaechen.size(), w.laenge(),
                        mp == null ? 0 : mp.ringe().size(), fl), "1, 2, 2, 150 m, 2 Ringe 50 000 m²");
        Osm.Filter fi = Osm.Filter.lies("man_made=dyke|embankment; embankment=yes; waterway=* & name~siel");
        boolean f1 = fi.passt(Map.of("man_made", "dyke")) && fi.passt(Map.of("embankment", "yes")) && !fi.passt(Map.of("embankment", "no"))
                && fi.passt(Map.of("waterway", "ditch", "name", "Westersiel")) && !fi.passt(Map.of("name", "Westersiel")) && !Osm.Filter.lies("").passt(Map.of("a", "b"));
        A.pruefe(f1, "Auswahl nach Tags: Werte mit |, vorhanden (*), Muster (~, ohne Groß/klein), und (&), oder (;), leer", f1 ? "richtig" : "falsch", "richtig");
    }

    private static String ll(double x, double y) {
        double[] g = Utm.rueckwaerts(x, y, 32);
        return String.format(java.util.Locale.ROOT, "{\"lat\": %.9f, \"lon\": %.9f}", g[1], g[0]);
    }

    static String knoten(long id, double x, double y, String tags) {
        double[] g = Utm.rueckwaerts(x, y, 32);
        return String.format(java.util.Locale.ROOT, "{\"type\": \"node\", \"id\": %d, \"lat\": %.9f, \"lon\": %.9f, \"tags\": {%s}}", id, g[1], g[0], tags);
    }

    static String weg(long id, double[][] p, String tags) {
        StringBuilder s = new StringBuilder("{\"type\": \"way\", \"id\": " + id + ", \"geometry\": [");
        for (int i = 0; i < p.length; i++) s.append(i > 0 ? ", " : "").append(ll(p[i][0], p[i][1]));
        return s.append("], \"tags\": {").append(tags).append("}}").toString();
    }

    private static String glied(double[][] p, String rolle) {
        StringBuilder s = new StringBuilder("{\"type\": \"way\", \"ref\": 9, \"role\": \"" + rolle + "\", \"geometry\": [");
        for (int i = 0; i < p.length; i++) s.append(i > 0 ? ", " : "").append(ll(p[i][0], p[i][1]));
        return s.append("]}").toString();
    }

    private static double flaeche(double[][] r) {
        double s = 0;
        for (int i = 0, j = r[0].length - 1; i < r[0].length; j = i++) s += (r[0][j] + r[0][i]) * (r[1][i] - r[1][j]);
        return s / 2;
    }

    // ------------------------------------------------------------------ Bauwerke an erzeugten Daten

    /** Mittelpunkt der erzeugten Hallig. */
    static final double CX = 471500, CY = 6046500;

    /** Gelände der erzeugten Hallig: Ring­deich (Krone 3,0 m bei r = 520 m), Fenne 1,5 m, Warft, Siel mit Rinne im Norden. */
    static double hoehe(double x, double y) {
        double r = Math.hypot(x - CX, y - CY);
        double z = r < 1200 ? 0.5 : r < 1300 ? 0.5 - 3.5 * (r - 1200) / 100 : -3.0;
        if (r < 510) z = 1.5;
        if (r < 40) z = 4.0;
        z = Math.max(z, 3.0 - 0.25 * Math.abs(r - 520));
        if (Math.abs(x - CX) < 3 && y > CY + 380 && y < CY + 700) z = Math.min(z, 0.0);
        return Math.round(z * 100) / 100.0;
    }

    static void bauwerkeErzeugt(Path tmp) throws IOException {
        // Stapel: 1 m in 3 × 3 Kacheln, 10 m in einer Kachel
        File st = tmp.resolve("bw").toFile();
        Gelaende.Stufe l1 = Gelaende.Stufe.anlegen(new File(st, "1m"), 1, 1000, 470000, 6045000, 3, 3, "Probe"), l10 = Gelaende.Stufe.anlegen(new File(st, "10m"), 10, 500,
                469000, 6044000, 1, 1, "Probe");
        Map<Long, float[]> land = new HashMap<>();
        for (int ty = 0; ty < 3; ty++)
            for (int tx = 0; tx < 3; tx++) {
                float[] t = new float[1000 * 1000], m = new float[1000 * 1000];
                for (int r = 0; r < 1000; r++)
                    for (int c = 0; c < 1000; c++) {
                        double x = 470000 + tx * 1000 + c + 0.5, y = 6045000 + (3 - ty) * 1000 - r - 0.5;
                        t[r * 1000 + c] = (float) hoehe(x, y);
                        m[r * 1000 + c] = Math.hypot(x - CX, y - CY) < 530 ? t[r * 1000 + c] : Float.NaN;
                    }
                l1.schreibe(tx, ty, t);
                land.put(Fuellwert.schluessel(470 + tx, 6047 - ty), m);
            }
        float[] g = new float[500 * 500];
        for (int r = 0; r < 500; r++) for (int c = 0; c < 500; c++) g[r * 500 + c] = (float) hoehe(469000 + c * 10 + 5, 6049000 - r * 10 - 5);
        l10.schreibe(0, 0, g);
        // dazu eine gröbere Stufe wie die Stufe 100 m aus grob (seit P5b: die Rauheit darf sie nicht für 10 m nehmen)
        Gelaende.Stufe l100 = Gelaende.Stufe.anlegen(new File(st, "100m"), 100, 100, 465000, 6040000, 1, 1, "Probe");
        float[] h = new float[100 * 100];
        for (int r = 0; r < 100; r++) for (int c = 0; c < 100; c++) h[r * 100 + c] = (float) hoehe(465000 + c * 100 + 50, 6050000 - r * 100 - 50);
        l100.schreibe(0, 0, h);
        Gelaende gel = Gelaende.ordner(st);
        // OSM: zwei Bögen des Deichs mit einer Lücke im Gelände-Damm (Westen) und einer am Siel (Norden), ein Durchlass im Westen
        StringBuilder s = new StringBuilder("{\"version\": 0.6, \"elements\": [\n");
        s.append(weg(10, bogen(92, 300), "\"highway\": \"path\", \"embankment\": \"yes\"")).append(",\n");
        s.append(weg(11, bogen(303, 448), "\"man_made\": \"dyke\"")).append(",\n");
        s.append(knoten(12, CX, CY + 520, "\"waterway\": \"lock_gate\"")).append(",\n");
        double c2 = Math.cos(Math.toRadians(200)), s2 = Math.sin(Math.toRadians(200));
        s.append(weg(13, new double[][] {{CX + 480 * c2, CY + 480 * s2}, {CX + 560 * c2, CY + 560 * s2}}, "\"waterway\": \"ditch\", \"tunnel\": \"culvert\"")).append(",\n");
        s.append(weg(14, bogen(20, 60, 300), "\"embankment\": \"yes\", \"historic\": \"yes\"")).append("\n]}");
        Osm osm = Osm.aus(s.toString(), 32);
        Gitter t = Gitter.utmBereich("T", 32, 470000, 6045000, 473000, 6048000, 20);
        Bauwerke b = new Bauwerke(t, gel);
        b.linien(osm, Osm.Filter.lies("man_made=dyke|embankment; embankment=yes"), Osm.Filter.lies("historic=*"));
        Map<String, double[]> sonden = new LinkedHashMap<>();
        sonden.put("Mitte", new double[] {CX + 200, CY});
        long t0 = System.nanoTime();
        b.bauen(osm, Osm.Filter.lies("waterway=lock_gate|sluice_gate"), Osm.Filter.lies("waterway=* & tunnel=culvert"), Osm.Filter.lies("bridge=yes"), sonden);
        File ziel = tmp.resolve("bwT").toFile();
        b.schreibe(ziel, "Probe");
        A.info("Erzeugte Hallig", f("Ringdeich r = 520 m, Krone 3,0 m, zwei Linien mit einer Lücke im Damm und einer am Siel (Rinne 6 m, Sohle 0 m), Durchlass; "
                + "%d Linien, %d Kanten, %d Siele, %d Ringe, %.1f s; Lücken: %s", b.linien.size(), b.kanten.size(), b.siele.size(), b.ringe.size(), (System.nanoTime() - t0) / 1e9,
                String.join(" · ", b.luecken)));
        boolean zwei = b.siele.size() == 2 && b.luecken.stream().filter(x -> x.startsWith("geschlossen")).count() >= 1
                && b.luecken.stream().anyMatch(x -> x.startsWith("mit Siel"));
        A.pruefe(zwei && b.linien.stream().noneMatch(x -> x.quelle().equals("way/14")), "Lücke im Damm geschlossen, Lücke am Schleusentor als Siel, Durchlass als Siel, ausgeschlossene Linie fehlt",
                f("%d Siele: %s", b.siele.size(), b.siele.stream().map(x -> x.name() + " " + x.quellen()).toList()), "2 Siele");
        pruefeBauwerke(gel, Gelaende.Stufe.lies(new File(ziel, "mindesthoehe")), ziel.toPath().resolve("bauwerke.txt"), b.linien, sonden, 5, "erzeugt");
        // Rauheit
        Rauheit r = new Rauheit(gel, "10m", land, b, osm, null);
        File ro = tmp.resolve("rh").toFile();
        r.bauen(ro);
        Gelaende.Stufe k1 = Gelaende.Stufe.lies(new File(ro, "1m")), k10 = Gelaende.Stufe.lies(new File(ro, "10m"));
        A.pruefe(gel.stufen().length == 3 && k10.zelle == l10.zelle && k10.suedwestOst == l10.suedwestOst && k10.suedwestNord == l10.suedwestNord && k10.kachelZellen == l10.kachelZellen,
                "Rauheit 10 m über der Stufe des Grunds, auch wenn der Stapel eine gröbere Stufe hat (Stand P5b: bis dahin nahm sie die gröbste)",
                f("Stapel %d Stufen; Rauheit 10 m: Zelle %.0f m, Südwest O %.0f N %.0f, %d Zellen je Kachel", gel.stufen().length, k10.zelle, k10.suedwestOst, k10.suedwestNord, k10.kachelZellen),
                "wie die Stufe 10 m");
        double[][] orte = {{CX + 200, CY, Rauheit.FENNE}, {CX + 520, CY, Rauheit.DEICH}, {CX + 800, CY, Rauheit.WATT}, {CX, CY + 10, Rauheit.FENNE}, {CX + 1400, CY + 500, Rauheit.WASSER}};
        int richtig = 0;
        StringBuilder w = new StringBuilder();
        for (double[] o : orte) {
            float v = k1.wert((int) Math.round(k1.spalte(o[0])), (int) Math.round(k1.zeile(o[1])));
            if (Float.isNaN(v)) v = k10.wert((int) Math.round(k10.spalte(o[0])), (int) Math.round(k10.zeile(o[1])));
            if (v == o[2]) richtig++;
            w.append(Float.isNaN(v) ? "–" : Rauheit.NAMEN[(int) v]).append(o == orte[orte.length - 1] ? "" : ", ");
        }
        A.pruefe(richtig == orte.length, "Rauheit der erzeugten Hallig: Fenne im Ring, Deich auf der Linie, Watt davor, Wasser in der Tiefe (10 m)", w.toString(),
                "Fenne, Deich, Watt, Fenne, Wasser");
    }

    /** Punkte eines Kreisbogens um die Mitte, r = 520 m, alle 2°. */
    private static double[][] bogen(double von, double bis) { return bogen(von, bis, 520); }

    private static double[][] bogen(double von, double bis, double r) {
        List<double[]> p = new ArrayList<>();
        for (double a = von; a <= bis + 1e-9; a += 2) p.add(new double[] {CX + r * Math.cos(Math.toRadians(a)), CY + r * Math.sin(Math.toRadians(a))});
        return p.toArray(new double[0][]);
    }

    // ------------------------------------------------------------------ Prüfsteine 10–13

    /** Eine gelesene Kante aus bauwerke.txt. */
    record K(int index, boolean querX, int i, int j, String art, double krone, boolean angehoben, String[] quellen, float[] profil) { }

    /** Prüfsteine 10–13 für die Bauwerke eines Gitters, aus den abgelegten Dateien. */
    static void pruefeBauwerke(Gelaende gel, Gelaende.Stufe mindest, Path datei, List<Bauwerke.Linie> linien, Map<String, double[]> sonden, double korridor, String wo)
            throws IOException {
        Gitter g = null;
        int fein = 0;
        Map<Integer, K> kanten = new HashMap<>();
        List<String[]> siele = new ArrayList<>(), ringe = new ArrayList<>();
        for (String z : Files.readAllLines(datei, StandardCharsets.UTF_8)) {
            String[] t = z.split(" ");
            switch (t[0]) {
                case "gitter" -> g = Gitter.utm(t[1], 32, Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[4]), Integer.parseInt(t[5]),
                        Integer.parseInt(t[6]));
                case "fein" -> fein = Integer.parseInt(t[1]);
                case "siel" -> siele.add(t);
                case "ring" -> ringe.add(t);
                case "kante" -> {
                    float[] p = new float[t.length - 10];
                    for (int s = 0; s < p.length; s++) p[s] = t[10 + s].equals("nan") ? Float.NaN : Float.parseFloat(t[10 + s]);
                    kanten.put(Integer.parseInt(t[1]), new K(Integer.parseInt(t[1]), t[2].equals("x"), Integer.parseInt(t[3]), Integer.parseInt(t[4]), t[5],
                            Double.parseDouble(t[6]), t[8].equals("1"), t[9].split(","), p));
                }
                default -> { }
            }
        }
        if (g == null) throw new IOException(datei + ": ohne Gitter");
        final Gitter gt = g;
        int nx = g.nx, ny = g.ny;
        // ---- Prüfstein 11a: jeder Punkt einer Deichlinie nah an einer Deichkante
        Map<Integer, List<K>> nahe = new HashMap<>();
        for (K k : kanten.values()) {
            for (int dj = -2; dj <= 2; dj++) for (int di = -2; di <= 2; di++) {
                int i = k.i + di, j = k.j + dj;
                if (i >= 0 && j >= 0 && i < nx && j < ny) nahe.computeIfAbsent(i + nx * j, q -> new ArrayList<>()).add(k);
            }
        }
        double weitester = 0;
        long punkte = 0, weit = 0;
        String wo11 = "";
        for (Bauwerke.Linie L : linien) {
            if (L.quelle().startsWith("Lücke")) continue;
            for (int s = 0; s + 1 < L.x().length; s++) {
                double l = Math.hypot(L.x()[s + 1] - L.x()[s], L.y()[s + 1] - L.y()[s]);
                for (double d = 0; d <= l; d += 1) {
                    double x = L.x()[s] + (L.x()[s + 1] - L.x()[s]) * d / Math.max(l, 1e-9), y = L.y()[s] + (L.y()[s + 1] - L.y()[s]) * d / Math.max(l, 1e-9);
                    if (!g.enthaelt(x, y)) continue;
                    double best = Double.POSITIVE_INFINITY;
                    for (K k : nahe.getOrDefault(g.index(g.i(x), g.j(y)), List.of())) {
                        double ax = g.x0 + k.i * g.dx, ay = g.y0 + k.j * g.dy, bx = k.querX ? ax : ax + g.dx, by = k.querX ? ay + g.dy : ay;
                        best = Math.min(best, strecke(x, y, ax, ay, bx, by));
                    }
                    punkte++;
                    if (best > ABSTAND) weit++;
                    if (best > weitester) { weitester = best; wo11 = f("O %.0f N %.0f (%s)", x, y, L.quelle()); }
                }
            }
        }
        A.pruefe(weit == 0 && punkte > 0, f("Prüfstein 11 (%s): jeder Punkt einer Deichlinie (je 1 m) liegt höchstens %.0f m [G] von einer Deichkante", wo, ABSTAND),
                f("%d Punkte, größter Abstand %.1f m bei %s", punkte, weitester, wo11), f("≤ %.0f m", ABSTAND));
        // ---- Prüfstein 11b: Krone je Stelle gleich dem höchsten Pixel des Querschnitts (eigene Rechnung über die ganze Linie)
        Map<String, List<Bauwerke.Linie>> nachQuelle = new HashMap<>();
        for (Bauwerke.Linie L : linien) nachQuelle.computeIfAbsent(L.quelle(), q -> new ArrayList<>()).add(L);
        Gelaende.Stufe px = gel.stufen()[0];
        long stellen = 0, ungleich = 0, ohne = 0;
        double groesste = 0;
        for (K k : kanten.values()) {
            if (k.angehoben) continue;
            List<Bauwerke.Linie> ls = new ArrayList<>();
            boolean nurOsm = true;
            for (String q : k.quellen) {
                if (!nachQuelle.containsKey(q) || q.startsWith("Lücke")) nurOsm = false;
                else ls.addAll(nachQuelle.get(q));
            }
            if (!nurOsm) { ohne++; continue; }
            double h = g.dx / fein, xa = g.x0 + k.i * g.dx, ya = g.y0 + k.j * g.dy;
            for (int s = 0; s < fein; s++) {
                double x = k.querX ? xa : xa + (s + 0.5) * h, y = k.querX ? ya + (s + 0.5) * h : ya;
                double soll = Double.NaN;
                for (Bauwerke.Linie L : ls) {
                    double[] best = null;
                    int bs = 0;
                    for (int q = 0; q + 1 < L.x().length; q++) {
                        double[] p = punktAufStrecke(x, y, L.x()[q], L.y()[q], L.x()[q + 1], L.y()[q + 1]);
                        if (best == null || p[2] < best[2]) { best = p; bs = q; }
                    }
                    double dx = L.x()[bs + 1] - L.x()[bs], dy = L.y()[bs + 1] - L.y()[bs], dl = Math.max(1e-12, Math.hypot(dx, dy));
                    double m = Double.NEGATIVE_INFINITY;
                    for (double tt = -korridor; tt <= korridor + 1e-9; tt += px.zelle / 2) {
                        double v = pixelwert(gel, px, best[0] - tt * dy / dl, best[1] + tt * dx / dl);
                        if (v > m) m = v;
                    }
                    if (m == Double.NEGATIVE_INFINITY) continue;
                    if (!(soll >= m)) soll = m;
                }
                stellen++;
                double d = Math.abs(soll - k.profil[s]);
                if (Double.isNaN(soll) && Float.isNaN(k.profil[s])) d = 0;
                if (!(d <= 6e-4)) {
                    ungleich++;
                    groesste = Math.max(groesste, Double.isNaN(d) ? 99 : d);
                    if (Boolean.getBoolean("p3c.zeige") && ungleich < 30) System.out.printf("  11b: Kante %d Stelle %d bei O %.1f N %.1f: Datei %.3f, eigene %.3f, %s%n", k.index, s, x, y, k.profil[s], soll, Arrays.toString(k.quellen));
                }
            }
        }
        A.pruefe(ungleich == 0 && stellen > 0, f("Prüfstein 11 (%s): Krone je Stelle gleich dem höchsten Pixel im Querschnitt ±%.0f m quer zur Linie (eigene Rechnung)", wo, korridor),
                f("%d Stellen an Kanten aus OSM gleich, %d ungleich%s; %d Kanten mit Lücke oder Siel nicht nachgerechnet", stellen - ungleich, ungleich,
                        ungleich > 0 ? f(" (bis %.3f m)", groesste) : "", ohne), "gleich (auf 1 mm)");
        // ---- Prüfstein 13: Siele
        int sieleOk = 0;
        StringBuilder st = new StringBuilder();
        for (String[] s : siele) {
            K k = kanten.get(Integer.parseInt(s[2]));
            double x = Double.parseDouble(s[3]), y = Double.parseDouble(s[4]), krone = Double.parseDouble(s[6]);
            double tief = Double.POSITIVE_INFINITY;
            for (double dy = -SIEL; dy <= SIEL; dy += px.zelle)
                for (double dx = -SIEL; dx <= SIEL; dx += px.zelle)
                    if (dx * dx + dy * dy <= SIEL * SIEL) tief = Math.min(tief, pixelwert(gel, px, x + dx, y + dy));
            boolean rinne = tief <= krone - RINNE, osm = s[9].contains("node/") || s[9].contains("way/");
            boolean auf = k != null && k.art.equals("siel");
            if (auf && (rinne || osm)) sieleOk++;
            st.append(f("%s: %s%s%s; ", s[1], auf ? "auf Sielkante" : "NICHT auf Sielkante", rinne ? f(", Rinne %.2f m unter der Krone", krone - tief) : "",
                    osm ? ", OSM" : ""));
        }
        A.pruefe(sieleOk == siele.size(), f("Prüfstein 13 (%s): jedes Siel auf einer Deichkante; das Gelände zeigt eine Rinne (≥ %.1f m [G] unter der Krone, ≤ %.0f m entfernt) oder OSM nennt einen Durchlass",
                        wo, RINNE, SIEL), f("%d von %d: %s", sieleOk, siele.size(), st.toString().strip()), "alle");
        // ---- Prüfsteine 10 und 12 je Sonde
        for (Map.Entry<String, double[]> e : sonden.entrySet()) ring(gel, mindest, g, fein, kanten, e.getKey(), e.getValue(), ringe, wo);
    }

    /** Prüfstein 10 und 12 für den Ring um eine Sonde. */
    static void ring(Gelaende gel, Gelaende.Stufe mindest, Gitter g, int fein, Map<Integer, K> kanten, String name, double[] sonde, List<String[]> ringe, String wo) {
        int nx = g.nx, ny = g.ny;
        // Ring: von der Sonde über alle Kanten ohne Bauwerk
        int start = g.index(g.i(sonde[0]), g.j(sonde[1]));
        Set<Integer> ring = new HashSet<>();
        ArrayDeque<Integer> q = new ArrayDeque<>(List.of(start));
        ring.add(start);
        boolean offen = false;
        while (!q.isEmpty() && !offen) {
            int c = q.poll(), i = c % nx, j = c / nx;
            if (i == 0 || j == 0 || i == nx - 1 || j == ny - 1) { offen = true; break; }
            int[][] nb = {{c - 1, i + (nx + 1) * j}, {c + 1, i + 1 + (nx + 1) * j}, {c - nx, (nx + 1) * ny + i + nx * j}, {c + nx, (nx + 1) * ny + i + nx * (j + 1)}};
            for (int[] v : nb) if (!kanten.containsKey(v[1]) && ring.add(v[0])) q.add(v[0]);
        }
        if (offen) {
            A.pruefe(false, f("Prüfstein 10 (%s): Ring um %s geschlossen", wo, name), f("offen: von O %.0f N %.0f aus ist der Rand des Gitters ohne Deichkante zu erreichen", sonde[0], sonde[1]),
                    "geschlossen");
            return;
        }
        // Fenster: Ring mit Rand
        int i0 = nx, j0 = ny, i1 = 0, j1 = 0;
        for (int c : ring) { i0 = Math.min(i0, c % nx); i1 = Math.max(i1, c % nx); j0 = Math.min(j0, c / nx); j1 = Math.max(j1, c / nx); }
        int rand = 10;
        i0 = Math.max(0, i0 - rand); j0 = Math.max(0, j0 - rand); i1 = Math.min(nx - 1, i1 + rand); j1 = Math.min(ny - 1, j1 + rand);
        int wn = i1 - i0 + 1, wm = j1 - j0 + 1, pw = wn * fein, ph = wm * fein;
        Gitter w = Gitter.utm("Fenster", 32, g.x0 + i0 * g.dx, g.y0 + j0 * g.dy, g.dx, wn, wm);
        double[] z = new double[pw * ph], m = new double[pw * ph];
        gel.feingelaende(w, fein).lies(0, 0, pw, ph, z);
        new Gelaende(mindest).feingelaende(w, fein).lies(0, 0, pw, ph, m);
        long mindestPixel = 0;
        for (int p = 0; p < z.length; p++) if (!Double.isNaN(m[p])) { if (m[p] > z[p]) z[p] = m[p]; }
        // Schwellen und tiefstes Pixel je Zelle
        double[] tief = new double[wn * wm];
        Arrays.fill(tief, Double.POSITIVE_INFINITY);
        for (int v = 0; v < ph; v++) for (int u = 0; u < pw; u++) { int c = (u / fein) + wn * (v / fein); tief[c] = Math.min(tief[c], z[v * pw + u]); }
        // Überlaufhöhe vom Rand des Fensters (Minimax)
        double[] ueber = new double[wn * wm];
        Arrays.fill(ueber, Double.POSITIVE_INFINITY);
        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        for (int j = 0; j < wm; j++) for (int i = 0; i < wn; i++) if (i == 0 || j == 0 || i == wn - 1 || j == wm - 1) { ueber[i + wn * j] = tief[i + wn * j]; pq.add(new double[] {tief[i + wn * j], i + wn * j}); }
        while (!pq.isEmpty()) {
            double[] a = pq.poll();
            int c = (int) a[1], i = c % wn, j = c / wn;
            if (a[0] > ueber[c]) continue;
            for (int r = 0; r < 4; r++) {
                int ni = i + (r == 0 ? -1 : r == 1 ? 1 : 0), nj = j + (r == 2 ? -1 : r == 3 ? 1 : 0);
                if (ni < 0 || nj < 0 || ni >= wn || nj >= wm) continue;
                int gi = i0 + Math.max(i, ni), gj = j0 + Math.max(j, nj);
                int idx = r < 2 ? gi + (nx + 1) * (j0 + j) : (nx + 1) * ny + (i0 + i) + nx * gj;
                K k = kanten.get(idx);
                double s;
                if (k != null) s = k.krone;
                else s = schwelle(z, pw, fein, r < 2, r < 2 ? Math.max(i, ni) : i, r < 2 ? j : Math.max(j, nj));
                double nv = Math.max(a[0], Math.max(s, tief[ni + wn * nj]));
                if (nv < ueber[ni + wn * nj]) { ueber[ni + wn * nj] = nv; pq.add(new double[] {nv, ni + wn * nj}); }
            }
        }
        // Kronen am Ring
        double kMin = Double.POSITIVE_INFINITY, kMax = Double.NEGATIVE_INFINITY;
        int kMinIdx = -1;
        for (K k : kanten.values()) {
            int[] ab = zellen(k, nx, ny);
            boolean ia = ring.contains(ab[0]), ib = ring.contains(ab[1]);
            if (ia == ib) continue;
            if (k.krone < kMin) { kMin = k.krone; kMinIdx = k.index; }
        }
        for (K k : kanten.values()) {
            int[] ab = zellen(k, nx, ny);
            boolean ia = ring.contains(ab[0]), ib = ring.contains(ab[1]);
            if (ia == ib) continue;
            int aussen = ia ? ab[1] : ab[0];
            int ai = aussen % nx - i0, aj = aussen / nx - j0;
            if (aussen < 0 || ai < 0 || aj < 0 || ai >= wn || aj >= wm) continue;
            if (ueber[ai + wn * aj] < kMin && k.krone > kMax) kMax = k.krone;                       // äußerer Rand: davor ist es früher nass
        }
        double zuerst = Double.POSITIVE_INFINITY;
        int nass = 0, zuNass = 0, trocken = 0;
        double hoehe = kMax + 0.05;
        for (int c : ring) {
            int ci = c % nx - i0, cj = c / nx - j0, wc = ci + wn * cj;
            zuerst = Math.min(zuerst, ueber[wc]);
            if (tief[wc] < hoehe) {
                zuNass++;
                if (ueber[wc] <= hoehe) nass++;
                else trocken++;
            }
        }
        double[] ki = mitte(g, kanten.get(kMinIdx));
        A.pruefe(zuerst >= kMin - 0.05, f("Prüfstein 10 (%s): Ring um %s geschlossen; bei der niedrigsten Krone − 5 cm keine Zelle im Ring nass (Flutfüllung über die Kanten, Siele zu)", wo, name),
                f("%d Zellen (%.2f km²); erste Zelle nass bei %.2f m, niedrigste Krone %.2f m bei O %.0f N %.0f", ring.size(), ring.size() * g.dx * g.dy / 1e6, zuerst, kMin, ki[0], ki[1]),
                "≥ niedrigste Krone − 0,05 m");
        A.pruefe(trocken == 0 && zuNass > 0, f("Prüfstein 10 (%s): bei der höchsten Krone des Rings + 5 cm (%.2f m) sind alle Zellen nass, die tiefer liegen", wo, hoehe),
                f("%d von %d Zellen nass", nass, zuNass), "alle");
        // Prüfstein 12: Pixel mit Seitenwechsel im Ring und an seinem Rand
        Set<Integer> nah = new HashSet<>(ring);
        for (int c : ring) for (int d : new int[] {c - 1, c + 1, c - nx, c + nx}) nah.add(d);
        for (int v = 0; v < ph; v++)
            for (int u = 0; u < pw; u++) {
                if (Double.isNaN(m[v * pw + u])) continue;
                int c = (i0 + u / fein) + nx * (j0 + v / fein);
                if (nah.contains(c)) mindestPixel++;
            }
        double fl = ring.size() * g.dx * g.dy, wechsel = mindestPixel * (g.dx / fein) * (g.dx / fein);
        String laut = "";
        for (String[] r : ringe) if (r.length > 11 && Arrays.asList(r[11].split(",")).contains(name)) laut = f("; laut bauwerke.txt %s m²", r[8]);
        A.pruefe(wechsel <= SEITE * fl, f("Prüfstein 12 (%s): Fläche, die durch das Einrasten die Seite wechselt, im Ring %s und an seinem Rand", wo, name),
                f("%.0f m² = %.2f %% von %.2f km²%s", wechsel, 100 * wechsel / fl, fl / 1e6, laut), f("≤ %.0f %% [G]", 100 * SEITE));
    }

    /** Zellen beiderseits einer Kante, −1 außerhalb. */
    private static int[] zellen(K k, int nx, int ny) {
        if (k.querX) return new int[] {k.i > 0 && k.i <= nx ? k.i - 1 + nx * k.j : -1, k.i < nx ? k.i + nx * k.j : -1};
        return new int[] {k.j > 0 && k.j <= ny ? k.i + nx * (k.j - 1) : -1, k.j < ny ? k.i + nx * k.j : -1};
    }

    /** Schwelle einer Kante im Fenster: kleinste über die Pixelpaare der höheren Höhe. */
    private static double schwelle(double[] z, int pw, int fein, boolean querX, int i, int j) {
        double s = Double.POSITIVE_INFINITY;
        for (int k = 0; k < fein; k++) {
            double a, b;
            if (querX) { int v = j * fein + k; a = z[v * pw + i * fein - 1]; b = z[v * pw + i * fein]; }
            else { int u = i * fein + k; a = z[(j * fein - 1) * pw + u]; b = z[j * fein * pw + u]; }
            s = Math.min(s, Math.max(a, b));
        }
        return s;
    }

    private static double[] mitte(Gitter g, K k) {
        if (k == null) return new double[] {Double.NaN, Double.NaN};
        double x = g.x0 + k.i * g.dx, y = g.y0 + k.j * g.dy;
        return k.querX ? new double[] {x, y + g.dy / 2} : new double[] {x + g.dx / 2, y};
    }

    /** Wert des Pixels unter einem Punkt: feinste Stufe, sonst das Gelände (wie in den Bauwerken). */
    static double pixelwert(Gelaende gel, Gelaende.Stufe px, double x, double y) {
        int c = (int) Math.floor((x - px.suedwestOst) / px.zelle), r = (int) Math.floor((px.suedwestNord + px.kachelnNord * px.kachel() - y) / px.zelle);
        float v = px.wert(c, r);
        return Float.isNaN(v) ? gel.hoehe(x, y, px.zelle) : v;
    }

    private static double strecke(double x, double y, double ax, double ay, double bx, double by) { return punktAufStrecke(x, y, ax, ay, bx, by)[2]; }

    private static double[] punktAufStrecke(double x, double y, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, ll = dx * dx + dy * dy;
        double t = ll == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / ll));
        double px = ax + t * dx, py = ay + t * dy;
        return new double[] {px, py, Math.hypot(x - px, y - py)};
    }

    // ------------------------------------------------------------------ mit Rezept: Gebiet

    static void bauwerkeGebiet(Rezept rz, Path daten, Path ziel) throws IOException {
        Gelaende gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
        double[] g = rz.zahlen("g3.gebiet", 4);
        Gitter g3 = Gitter.utmBereich("G3", 32, g[0], g[1], g[2], g[3], rz.zahl("g3.zelle"));
        List<Path> ps = new ArrayList<>();
        for (String s : rz.text("bauwerke.osm").split(";")) ps.add(AufbereitungCheck.pfad(daten, s.strip()));
        Osm osm = Osm.lies(32, ps.toArray(new Path[0]));
        Bauwerke b = new Bauwerke(g3, gel);
        b.linien(osm, Osm.Filter.lies(rz.text("bauwerke.deiche")), Osm.Filter.lies(rz.text("bauwerke.ohne", "")));
        Map<String, double[]> sonden = new LinkedHashMap<>();
        for (String k : rz.alle().keySet()) if (k.startsWith("ring.")) sonden.put(k.substring(5), rz.zahlen(k, 2));
        Path d = ziel.resolve(g3.name).resolve("bauwerke.txt");
        A.info("Bauwerke des Gebiets", f("%s; %d Linien aus OSM (%s)", d, b.linien.size(), osm.stand));
        pruefeBauwerke(gel, Gelaende.Stufe.lies(ziel.resolve(g3.name).resolve("mindesthoehe").toFile()), d, b.linien, sonden,
                rz.hat("bauwerke.korridor") ? rz.zahl("bauwerke.korridor") : 5, "Gebiet");
    }

    /** Vergleichswerte aus PIL 12.3 (libtiff): schwarze Bildpunkte je Folie der DTK5 (12.11.2026). */
    static final Object[][] FOLIEN = {
            {"dtk5_32470_6046_2_sh_watt.tif", 9835347L}, {"dtk5_32470_6046_2_sh_wies.tif", 47813322L}, {"dtk5_32468_6046_2_sh_babl.tif", 642566L},
            {"dtk5_32472_6044_2_sh_sebl.tif", 62388993L}};

    static void rauheitGebiet(Rezept rz, Path daten, Path ziel) throws IOException {
        if (rz.hat("rauheit.dtk5")) {
            Path dtk = AufbereitungCheck.pfad(daten, rz.text("rauheit.dtk5"));
            for (Object[] fo : FOLIEN) {
                Path p = dtk.resolve((String) fo[0]);
                if (!Files.isRegularFile(p)) { A.pruefe(false, fo[0] + ": Folie", "fehlt", "vorhanden"); continue; }
                long schwarz = 0;
                try (GeoTiff t = GeoTiff.oeffne(p)) {
                    for (int y = 0; y < t.hoehe; y += 500) {
                        float[] v = t.lies(0, y, t.breite, 500);
                        for (float a : v) if ((a == 1) == (t.photometrie == 0) && !Float.isNaN(a)) schwarz++;
                    }
                }
                A.pruefe(schwarz == (long) fo[1], f("%s (CCITT Gruppe 4): schwarze Bildpunkte", fo[0]), f("%d", schwarz), "gleich libtiff");
            }
        }
        // Prüfstein 17: jedes Pixel des Stapels hat eine Klasse
        File st = ziel.resolve("gelaende").toFile(), ra = ziel.resolve("rauheit").toFile();
        String[][] paare = {{rz.text("land.stufe", "1m"), "1m"}, {rz.text("grund.stufe", "10m"), "10m"}};
        for (String[] p : paare) {
            Gelaende.Stufe h = Gelaende.Stufe.lies(new File(st, p[0])), k = Gelaende.Stufe.lies(new File(ra, p[1]));
            long mitWert = 0, ohneKlasse = 0, falsch = 0;
            long[] zahl = new long[9];
            for (int ty = 0; ty < h.kachelnNord; ty++)
                for (int tx = 0; tx < h.kachelnOst; tx++) {
                    float[] a = h.kachel(tx, ty);
                    if (a.length == 0) continue;
                    float[] b = k.kachel(tx, ty);
                    for (int i = 0; i < a.length; i++) {
                        if (Float.isNaN(a[i])) continue;
                        mitWert++;
                        float c = b.length == 0 ? Float.NaN : b[i];
                        if (Float.isNaN(c)) ohneKlasse++;
                        else if (c < 1 || c > 8 || c != Math.rint(c)) falsch++;
                        else zahl[(int) c]++;
                    }
                    h.vergiss();
                    k.vergiss();
                }
            StringBuilder s = new StringBuilder();
            for (int c = 1; c <= 8; c++) s.append(f("%s %.2f km²%s", Rauheit.NAMEN[c], zahl[c] * h.zelle * h.zelle / 1e6, c < 8 ? ", " : ""));
            A.pruefe(ohneKlasse == 0 && falsch == 0 && mitWert > 0, f("Prüfstein 17 (Gebiet): jedes Pixel der Stufe %s mit Höhe hat eine Rauheitsklasse 1–8", p[0]),
                    f("%d Pixel, %d ohne Klasse, %d falsch; %s", mitWert, ohneKlasse, falsch, s), "alle");
        }
        Path kl = ra.toPath().resolve("klassen.txt");
        A.info("Rauheit: klassen.txt", Files.isRegularFile(kl) ? String.join(" · ", Files.readAllLines(kl, StandardCharsets.UTF_8).stream().filter(x -> !x.startsWith("#")).toList()) : "fehlt");
    }
}
