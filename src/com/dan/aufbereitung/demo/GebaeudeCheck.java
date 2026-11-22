package com.dan.aufbereitung.demo;

import com.dan.aufbereitung.CityGml;
import com.dan.aufbereitung.Gebaeude;
import com.dan.core.pruef.Attest;
import com.dan.core.szene.Bauten;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Schritts gebaeude (P12a, Werkbuch HalligHooge Teil A, 12.13; Prüfsteine 1–3).
 * <ol>
 * <li><b>Leser</b> an einem erzeugten CityGML 2.0 nach dem AdV-Profil (Haus mit Anbau als BuildingPart, Satteldach,
 * {@code posList} und {@code pos}, ein innerer Ring) und an einem ohne Themen (nur {@code lod2Solid}).</li>
 * <li><b>Häuser im Gebiet</b> ({@code --gebiet}): {@code bau/gebaeude.bin} und {@code bau/gebaeude.txt} aus dem Schritt;
 * Prüfstein 1 (OSM-Gebäude mit Gegenstück in LoD2 ≥ 95 %), Prüfstein 2 (Boden gegen das Gelände an den Ecken:
 * 95 % der Häuser ≤ 0,3 m), Prüfstein 3 (Albedo des Dachs gegen die geeichte Farbe des DOP20, wo gemessen:
 * ΔE₀₀ ≤ 5 im Mittel), dazu die Dacharten.</li>
 * </ol>
 * <pre>
 * java -cp dist\Renderer.jar com.dan.aufbereitung.demo.GebaeudeCheck [--gebiet D:\Daten\HalligHooge\gebiet]
 * </pre>
 */
public final class GebaeudeCheck {
    private static final Attest A = new Attest("com.dan.aufbereitung (gebaeude)", "0.1");

    static final String GML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <core:CityModel xmlns:core="http://www.opengis.net/citygml/2.0" xmlns:bldg="http://www.opengis.net/citygml/building/2.0" xmlns:gml="http://www.opengis.net/gml">
             <core:cityObjectMember>
              <bldg:Building gml:id="DESH_TEST_1">
               <gml:name>Friesenhaus</gml:name>
               <bldg:function>31001_1000</bldg:function>
               <bldg:roofType>3100</bldg:roofType>
               <bldg:measuredHeight uom="urn:adv:uom:m">8.0</bldg:measuredHeight>
               <bldg:boundedBy><bldg:GroundSurface><bldg:lod2MultiSurface><gml:MultiSurface srsName="EPSG:25832"><gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing>
                 <gml:posList srsDimension="3">0 0 4 0 9 4 16 9 4 16 0 4 0 0 4</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember></gml:MultiSurface></bldg:lod2MultiSurface></bldg:GroundSurface></bldg:boundedBy>
               <bldg:boundedBy><bldg:WallSurface><bldg:lod2MultiSurface><gml:MultiSurface><gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing>
                 <gml:posList>0 0 4 16 0 4 16 0 6.6 0 0 6.6 0 0 4</gml:posList></gml:LinearRing></gml:exterior>
                 <gml:interior><gml:LinearRing><gml:posList>2 0 5 3 0 5 3 0 6 2 0 6 2 0 5</gml:posList></gml:LinearRing></gml:interior></gml:Polygon></gml:surfaceMember></gml:MultiSurface></bldg:lod2MultiSurface></bldg:WallSurface></bldg:boundedBy>
               <bldg:boundedBy><bldg:WallSurface><bldg:lod2MultiSurface><gml:MultiSurface><gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing>
                 <gml:posList>16 9 4 0 9 4 0 9 6.6 16 9 6.6 16 9 4</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember></gml:MultiSurface></bldg:lod2MultiSurface></bldg:WallSurface></bldg:boundedBy>
               <bldg:boundedBy><bldg:WallSurface><bldg:lod2MultiSurface><gml:MultiSurface><gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing>
                 <gml:pos>0 9 4</gml:pos><gml:pos>0 0 4</gml:pos><gml:pos>0 0 6.6</gml:pos><gml:pos>0 4.5 12</gml:pos><gml:pos>0 9 6.6</gml:pos><gml:pos>0 9 4</gml:pos></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember></gml:MultiSurface></bldg:lod2MultiSurface></bldg:WallSurface></bldg:boundedBy>
               <bldg:boundedBy><bldg:WallSurface><bldg:lod2MultiSurface><gml:MultiSurface><gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing>
                 <gml:posList>16 0 4 16 9 4 16 9 6.6 16 4.5 12 16 0 6.6 16 0 4</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember></gml:MultiSurface></bldg:lod2MultiSurface></bldg:WallSurface></bldg:boundedBy>
               <bldg:boundedBy><bldg:RoofSurface><bldg:lod2MultiSurface><gml:MultiSurface><gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing>
                 <gml:posList>0 0 6.6 16 0 6.6 16 4.5 12 0 4.5 12 0 0 6.6</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember></gml:MultiSurface></bldg:lod2MultiSurface></bldg:RoofSurface></bldg:boundedBy>
               <bldg:boundedBy><bldg:RoofSurface><bldg:lod2MultiSurface><gml:MultiSurface><gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing>
                 <gml:posList>16 9 6.6 0 9 6.6 0 4.5 12 16 4.5 12 16 9 6.6</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember></gml:MultiSurface></bldg:lod2MultiSurface></bldg:RoofSurface></bldg:boundedBy>
               <bldg:consistsOfBuildingPart><bldg:BuildingPart gml:id="DESH_TEST_1_A">
                <bldg:roofType>1000</bldg:roofType>
                <bldg:boundedBy><bldg:RoofSurface><bldg:lod2MultiSurface><gml:MultiSurface><gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing>
                  <gml:posList>16 2 7 20 2 7 20 7 7 16 7 7 16 2 7</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember></gml:MultiSurface></bldg:lod2MultiSurface></bldg:RoofSurface></bldg:boundedBy>
               </bldg:BuildingPart></bldg:consistsOfBuildingPart>
              </bldg:Building>
             </core:cityObjectMember>
             <core:cityObjectMember>
              <bldg:Building gml:id="DESH_TEST_2">
               <bldg:lod2Solid><gml:Solid><gml:exterior><gml:CompositeSurface>
                <gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing><gml:posList>40 0 4 40 5 4 45 5 4 45 0 4 40 0 4</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember>
                <gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing><gml:posList>40 0 4 45 0 4 45 0 7 40 0 7 40 0 4</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember>
                <gml:surfaceMember><gml:Polygon><gml:exterior><gml:LinearRing><gml:posList>40 0 7 45 0 7 45 5 7 40 5 7 40 0 7</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon></gml:surfaceMember>
               </gml:CompositeSurface></gml:exterior></gml:Solid></bldg:lod2Solid>
              </bldg:Building>
             </core:cityObjectMember>
            </core:CityModel>
            """;

    public static void main(String[] args) {
        String gebiet = null;
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--gebiet")) gebiet = args[i + 1];
        System.out.println("GebaeudeCheck – Schritt gebaeude (Werkbuch HalligHooge Teil A, P12a)");
        try {
            leser();
            if (gebiet != null) gebiet(new File(gebiet));
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P12a_Gebaeude.md"), "P12a – GebaeudeCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void leser() throws Exception {
        A.teil("1 Leser");
        CityGml c = new CityGml();
        c.lies(new ByteArrayInputStream(GML.getBytes(StandardCharsets.UTF_8)), "Probe");
        A.pruefe(c.haeuser.size() == 2, "Häuser (Building; BuildingPart gehört zum Haus)", String.valueOf(c.haeuser.size()), "2");
        CityGml.Haus h = c.haeuser.get(0);
        int[] art = new int[4];
        for (CityGml.Flaeche fl : h.flaechen) art[fl.art()]++;
        A.pruefe(h.id.equals("DESH_TEST_1") && h.name.equals("Friesenhaus") && h.dachform.equals("3100") && h.hoehe == 8.0 && art[CityGml.WAND] == 4 && art[CityGml.DACH] == 3
                        && art[CityGml.GRUND] == 1 && c.innereRinge == 1 && c.srs.equals("EPSG:25832"),
                "Merkmale und Flächen des ersten Hauses", f("%s, %s, Dach %s, %.1f m; Wand %d, Dach %d, Grund %d; innere Ringe %d; %s", h.id, h.name, h.dachform, h.hoehe,
                        art[0], art[1], art[2], c.innereRinge, c.srs), "4 Wände, 3 Dächer, 1 Grund, 1 innerer Ring");
        CityGml.Haus h2 = c.haeuser.get(1);
        int[] a2 = new int[4];
        for (CityGml.Flaeche fl : h2.flaechen) a2[fl.art()]++;
        A.pruefe(a2[CityGml.GRUND] == 1 && a2[CityGml.WAND] == 1 && a2[CityGml.DACH] == 1, "Flächen ohne Thema nach der Neigung", f("Grund %d, Wand %d, Dach %d", a2[2], a2[0], a2[1]),
                "je 1");
        // Bauen ohne Gelände und Luftbild: Dach Reet (Vorgabe), flaches Haus
        Gebaeude g = new Gebaeude();
        g.zwerch = false;
        g.bauen(c, new double[] {-100, -100, 100, 100}, null, List.of(), null, null, 0, 20);
        Bauten b = g.bauten;
        Bauten.Gebaeude b1 = b.gebaeude.get(0), b2 = b.gebaeude.get(1);
        A.pruefe(b.gebaeude.size() == 2 && b1.dach == Bauten.REET && b2.dach == Bauten.FLACH && Math.abs(b1.first - 12) < 1e-6 && Math.abs(b1.traufe - 6.6) < 1e-6
                        && Math.abs(b1.boden - 4) < 1e-6, "Häuser gebaut: Reet als Regel, flaches Dach erkannt; Boden, Traufe, First",
                f("%s und %s; Boden %.2f, Traufe %.2f, First %.2f m", Bauten.DACHART[b1.dach], Bauten.DACHART[b2.dach], b1.boden, b1.traufe, b1.first), "Reet, flach; 4 / 6,6 / 12 m");
        // Flächen: Summe der Dreiecke gleich den Flächen der Ringe (ohne den inneren Ring)
        double soll = 2 * 16 * 2.6 + 2 * (9 * 2.6 + 0.5 * 9 * 5.4) + 2 * 16 * Math.hypot(4.5, 5.4) + 4 * 5;
        double ist = 0;
        float[] s = b.scheitel;
        for (int i = 0; i < b1.erster + b1.scheitel; i += 3) {
            double[] p0 = {s[i * 12], s[i * 12 + 1], s[i * 12 + 2]}, p1 = {s[(i + 1) * 12], s[(i + 1) * 12 + 1], s[(i + 1) * 12 + 2]}, p2 = {s[(i + 2) * 12], s[(i + 2) * 12 + 1], s[(i + 2) * 12 + 2]};
            double[] u = {p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]}, v = {p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]};
            ist += 0.5 * Math.sqrt(Math.pow(u[1] * v[2] - u[2] * v[1], 2) + Math.pow(u[2] * v[0] - u[0] * v[2], 2) + Math.pow(u[0] * v[1] - u[1] * v[0], 2));
        }
        A.pruefe(Math.abs(ist / soll - 1) < 1e-4, "Dreiecke des ersten Hauses: Fläche gleich der Fläche der Ringe",
                f("%.3f m² gegen %.3f m² (Sockel nicht mitgerechnet: ohne Gelände kein Sockel)", ist, soll), "relativ < 1e-4");
        // Normalen der Wände nach außen, der Dächer nach oben
        boolean aussen = true;
        double mx = 8, mz = 20 - 4.5;
        for (int i = b1.erster; i < b1.erster + b1.scheitel; i++) {
            int art0 = (int) s[i * 12 + 3];
            double nx = s[i * 12 + 4], ny = s[i * 12 + 5], nz = s[i * 12 + 6];
            if (art0 == Bauten.DACH && ny <= 0) aussen = false;
            if (art0 == Bauten.WAND && nx * (s[i * 12] - mx) + nz * (s[i * 12 + 2] - mz) < -1e-6) aussen = false;
        }
        A.pruefe(aussen, "Normalen: Wände nach außen, Dächer nach oben", aussen ? "ja" : "nein", "ja");
        // Zwerchgiebel (F129 A): über der Südtraufe (N = 0), Giebelwand über der Traufe in der Ebene der Wand
        Gebaeude gz = new Gebaeude();
        gz.bauen(c, new double[] {-100, -100, 100, 100}, null, List.of(), null, null, 0, 20);
        Bauten bz = gz.bauten;
        Bauten.Gebaeude z1 = bz.gebaeude.get(0);
        float[] sz = bz.scheitel;
        double wandOben = -1, giebelFirst = -1;
        for (int i = z1.erster; i < z1.erster + z1.scheitel; i++) {
            if ((int) sz[i * 12 + 3] != Bauten.WAND || Math.abs(sz[i * 12 + 2] - 20) > 1e-4) continue;    // Wand in der Ebene N = 0 (z = n0)
            wandOben = Math.max(wandOben, sz[i * 12 + 1]);
            if (Math.abs(sz[i * 12] - 8) < 1e-4) giebelFirst = sz[i * 12 + 1];
        }
        double sollZ = 6.6 + gz.zwerchWand + 0.5 * gz.zwerchBreite * Math.tan(Math.toRadians(gz.zwerchNeigung));
        A.pruefe(gz.zwerchGebaut == 1 && z1.scheitel > b1.scheitel && Math.abs(giebelFirst - sollZ) < 1e-3 && Math.abs(wandOben - sollZ) < 1e-3,
                "Zwerchgiebel über der Mitte der Südtraufe (F129 A)", f("%d gebaut; Giebelwand bis %.2f m in der Mitte (E 8), Soll %.2f m; %d statt %d Scheitel",
                        gz.zwerchGebaut, giebelFirst, sollZ, z1.scheitel, b1.scheitel), "1; First des Giebels ± 1 mm");
    }

    static void gebiet(File gebiet) throws Exception {
        A.teil("2 Häuser im Gebiet");
        File bau = new File(gebiet, "bau");
        Bauten b = Bauten.lies(bau);
        A.pruefe(b != null, "bau/gebaeude.bin", b == null ? "fehlt" : f("%d Häuser, %d Dreiecke, Ursprung %.1f / %.1f", b.gebaeude.size(), b.dreiecke(), b.e0, b.n0), "vorhanden");
        if (b == null) return;
        List<String> z = Files.readAllLines(new File(bau, "gebaeude.txt").toPath(), StandardCharsets.UTF_8);
        String bericht = z.size() > 2 ? z.get(2).substring(2) : "";
        A.info("Bericht des Schritts", bericht);
        // Prüfstein 1
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("OSM: (\\d+) von (\\d+) Gebäuden").matcher(bericht);
        if (m.find()) {
            int t = Integer.parseInt(m.group(1)), n = Integer.parseInt(m.group(2));
            A.pruefe(n > 0 && t >= 0.95 * n, "Prüfstein 1: Gebäude der OSM mit Gegenstück in LoD2", f("%d von %d (%.1f %%)", t, n, 100.0 * t / Math.max(1, n)), "≥ 95 % [G]");
        } else A.pruefe(false, "Prüfstein 1: Gebäude der OSM mit Gegenstück in LoD2", "kein Wert im Bericht", "≥ 95 % [G]");
        // Prüfstein 2 und 3 aus den Zeilen
        List<Double> ecke = new ArrayList<>(), vorz = new ArrayList<>();
        double de = 0;
        int gemessen = 0, reet = 0, flach = 0, ziegel = 0;
        for (String s : z) {
            if (s.startsWith("#") || s.isBlank()) continue;
            String[] t = s.split("\t");
            ecke.add(Double.parseDouble(t[5]));
            if (t.length > 11) vorz.add(Double.parseDouble(t[11]));
            switch (t[7]) {
                case "Reet" -> reet++;
                case "flach" -> flach++;
                default -> ziegel++;
            }
            if (!t[9].equals("-")) {
                double[] alb = zahlen(t[8]), dop = zahlen(t[9]);
                de += deltaE00(alb, dop);
                gemessen++;
            }
        }
        double[] e = ecke.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double q95 = e.length > 0 ? e[(int) Math.min(e.length - 1, Math.floor(0.95 * e.length))] : Double.NaN;
        // Prüfstein 2 seit F131 A (Datei 66): kein Haus schwebt – LoD2 legt den Boden auf den tiefsten Punkt des Geländes am
        // Haus; gemessen wird der Boden aus LoD2 über dem Gelände (Median der Ecken je Haus, mit Vorzeichen)
        A.info("Boden gegen das Gelände an den Ecken, Betrag (größte Abweichung je Haus; bis F131 Prüfstein 2)",
                f("95 %% der Häuser ≤ %.2f m; Median %.2f m, größte %.2f m", q95, e.length > 0 ? e[e.length / 2] : Double.NaN, e.length > 0 ? e[e.length - 1] : Double.NaN));
        if (!vorz.isEmpty()) {
            double[] v = vorz.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double v95 = v[Math.min(v.length - 1, (int) (0.95 * v.length))];
            A.pruefe(v95 <= 0.1, "Prüfstein 2 (F131 A): kein Haus schwebt – Boden aus LoD2 über dem Gelände (Median der Ecken je Haus)",
                    f("95 %%: %+.2f m; 5 %%: %+.2f m, Median %+.2f m; über 0,1 m: %d von %d Häusern", v95, v[(int) (0.05 * v.length)], v[v.length / 2],
                            java.util.Arrays.stream(v).filter(x -> x > 0.1).count(), v.length), "95 % der Häuser ≤ +0,1 m [G]");
        } else A.pruefe(false, "Prüfstein 2 (F131 A): kein Haus schwebt", "Spalte fehlt in gebaeude.txt", "95 % der Häuser ≤ +0,1 m [G]");
        double mittel = gemessen > 0 ? de / gemessen : Double.NaN;
        A.pruefe(gemessen > 0 && mittel <= 5, "Prüfstein 3: Albedo des Dachs gegen die Farbe im DOP20 (geeicht), wo gemessen",
                f("ΔE₀₀ im Mittel %.2f über %d Dächer", mittel, gemessen), "≤ 5 [G]");
        A.info("Dächer", f("%d Reet, %d flach, %d Ziegel", reet, flach, ziegel));
    }

    static double[] zahlen(String s) {
        String[] t = s.strip().split("\\s+");
        return new double[] {Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2])};
    }

    /** ΔE₀₀ (CIEDE2000) zwischen zwei Albedos (linear sRGB, D65), über XYZ und Lab. */
    static double deltaE00(double[] a, double[] b) {
        double[] l1 = lab(a), l2 = lab(b);
        double C1 = Math.hypot(l1[1], l1[2]), C2 = Math.hypot(l2[1], l2[2]), Cm = 0.5 * (C1 + C2);
        double G = 0.5 * (1 - Math.sqrt(Math.pow(Cm, 7) / (Math.pow(Cm, 7) + Math.pow(25, 7))));
        double a1 = (1 + G) * l1[1], a2 = (1 + G) * l2[1];
        double c1 = Math.hypot(a1, l1[2]), c2 = Math.hypot(a2, l2[2]);
        double h1 = Math.toDegrees(Math.atan2(l1[2], a1)), h2 = Math.toDegrees(Math.atan2(l2[2], a2));
        if (h1 < 0) h1 += 360;
        if (h2 < 0) h2 += 360;
        double dL = l2[0] - l1[0], dC = c2 - c1, dh = h2 - h1;
        if (c1 * c2 == 0) dh = 0;
        else if (dh > 180) dh -= 360;
        else if (dh < -180) dh += 360;
        double dH = 2 * Math.sqrt(c1 * c2) * Math.sin(Math.toRadians(dh / 2));
        double Lm = 0.5 * (l1[0] + l2[0]), cm = 0.5 * (c1 + c2), hm = h1 + h2;
        if (c1 * c2 != 0) {
            if (Math.abs(h1 - h2) > 180) hm = hm < 360 ? hm + 360 : hm - 360;
            hm /= 2;
        }
        double T = 1 - 0.17 * Math.cos(Math.toRadians(hm - 30)) + 0.24 * Math.cos(Math.toRadians(2 * hm)) + 0.32 * Math.cos(Math.toRadians(3 * hm + 6))
                - 0.20 * Math.cos(Math.toRadians(4 * hm - 63));
        double dT = 30 * Math.exp(-Math.pow((hm - 275) / 25, 2));
        double Rc = 2 * Math.sqrt(Math.pow(cm, 7) / (Math.pow(cm, 7) + Math.pow(25, 7)));
        double Sl = 1 + 0.015 * Math.pow(Lm - 50, 2) / Math.sqrt(20 + Math.pow(Lm - 50, 2)), Sc = 1 + 0.045 * cm, Sh = 1 + 0.015 * cm * T;
        double Rt = -Math.sin(Math.toRadians(2 * dT)) * Rc;
        return Math.sqrt(Math.pow(dL / Sl, 2) + Math.pow(dC / Sc, 2) + Math.pow(dH / Sh, 2) + Rt * (dC / Sc) * (dH / Sh));
    }

    static double[] lab(double[] c) {
        double X = 0.4124 * c[0] + 0.3576 * c[1] + 0.1805 * c[2], Y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2], Z = 0.0193 * c[0] + 0.1192 * c[1] + 0.9505 * c[2];
        double fx = fl(X / 0.95047), fy = fl(Y), fz = fl(Z / 1.08883);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    static double fl(double t) { return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116; }

    static { Locale.setDefault(Locale.GERMANY); }
}
