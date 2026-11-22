package com.dan.aufbereitung;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Leser für Gebäude in CityGML 1.0 und 2.0 (seit P12a, F125 A): das 3D-Gebäudemodell LoD2 der Vermessungsverwaltungen
 * (AdV-Profil; Schleswig-Holstein: EPSG:25832, Höhen NHN). Im Strom (StAX), auch in ZIP-Dateien, ohne Schema.
 * <ul>
 * <li>Je {@code bldg:Building} ein {@link Haus}; die Flächen seiner {@code bldg:BuildingPart} gehören dazu.</li>
 * <li>Flächen aus {@code bldg:boundedBy}: {@code WallSurface}, {@code RoofSurface}, {@code GroundSurface} (dazu
 * {@code ClosureSurface}, {@code OuterCeilingSurface}, {@code OuterFloorSurface} als {@link #SONST}). Polygone ohne
 * Thema (nur {@code lod2Solid} oder {@code lod2MultiSurface}) ordnet {@link #ordne} nach der Neigung ein.</li>
 * <li>Je Polygon der äußere Ring ({@code gml:exterior}) aus {@code gml:posList} oder {@code gml:pos}; innere Ringe
 * werden gezählt und weggelassen (im LoD2 selten).</li>
 * <li>Merkmale: {@code gml:id}, {@code gml:name}, {@code bldg:function}, {@code bldg:roofType},
 * {@code bldg:measuredHeight}.</li>
 * </ul>
 */
public final class CityGml {
    public static final int WAND = 0, DACH = 1, GRUND = 2, SONST = 3, OHNE = -1;

    /** Eine ebene Fläche: Art und Ring (Ost, Nord, Höhe je Punkt; der erste nicht wiederholt). */
    public record Flaeche(int art, double[] ring) { }

    public static final class Haus {
        public String id = "", name = "", funktion = "", dachform = "";
        public double hoehe = Double.NaN;
        public final List<Flaeche> flaechen = new ArrayList<>();

        /** Mitte des Grundrisses (Ost, Nord) aus den Flächen am Boden, sonst aus allen. */
        public double[] mitte() {
            double sx = 0, sy = 0;
            int n = 0;
            for (int pass = 0; pass < 2 && n == 0; pass++)
                for (Flaeche f : flaechen) {
                    if (pass == 0 && f.art != GRUND) continue;
                    for (int i = 0; i < f.ring.length; i += 3) { sx += f.ring[i]; sy += f.ring[i + 1]; n++; }
                }
            return n == 0 ? new double[] {Double.NaN, Double.NaN} : new double[] {sx / n, sy / n};
        }

        /** Tiefste und höchste Höhe aller Punkte. */
        public double[] hoehen() {
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (Flaeche f : flaechen)
                for (int i = 2; i < f.ring.length; i += 3) { lo = Math.min(lo, f.ring[i]); hi = Math.max(hi, f.ring[i]); }
            return new double[] {lo, hi};
        }
    }

    public final List<Haus> haeuser = new ArrayList<>();
    public int innereRinge, dateien;
    /** Bezugssystem aus srsName (z. B. „EPSG:25832“); das letzte gesehene. */
    public String srs = "";

    /** Liest alle .gml/.xml in einer Datei, einer ZIP-Datei oder einem Ordner (rekursiv). */
    public static CityGml lies(List<Path> quellen) throws IOException {
        CityGml c = new CityGml();
        for (Path p : quellen) c.lies(p);
        return c;
    }

    public void lies(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (var s = Files.walk(p)) {
                for (Path q : s.filter(Files::isRegularFile).sorted().toList()) {
                    String n = q.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".gml") || n.endsWith(".xml") || n.endsWith(".zip")) lies(q);
                }
            }
            return;
        }
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".zip")) {
            try (ZipFile z = new ZipFile(p.toFile())) {
                Enumeration<? extends ZipEntry> e = z.entries();
                while (e.hasMoreElements()) {
                    ZipEntry x = e.nextElement();
                    String m = x.getName().toLowerCase(Locale.ROOT);
                    if (x.isDirectory() || !(m.endsWith(".gml") || m.endsWith(".xml"))) continue;
                    try (InputStream in = z.getInputStream(x)) {
                        lies(in, p + "!" + x.getName());
                    }
                }
            }
        } else try (InputStream in = Files.newInputStream(p)) {
            lies(in, p.toString());
        }
    }

    public void lies(InputStream in, String name) throws IOException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        dateien++;
        try {
            XMLStreamReader r = f.createXMLStreamReader(in);
            try {
                lauf(r);
            } finally {
                r.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException("CityGML " + name + ": " + e.getMessage(), e);
        }
    }

    private void lauf(XMLStreamReader r) throws XMLStreamException {
        Haus haus = null;
        int tiefeHaus = -1, tiefe = 0, tiefeThema = -1, thema = OHNE, tiefeAussen = -1;
        boolean imAussen = false;
        StringBuilder text = new StringBuilder();
        List<Double> punkte = new ArrayList<>();
        int dim = 3;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                tiefe++;
                String ln = r.getLocalName();
                text.setLength(0);
                String srsName = r.getAttributeValue(null, "srsName");
                if (srsName != null) srs = srsName;
                switch (ln) {
                    case "Building" -> {
                        if (haus == null) {
                            haus = new Haus();
                            tiefeHaus = tiefe;
                            String id = attribut(r, "id");
                            if (id != null) haus.id = id;
                        }
                    }
                    case "WallSurface" -> { thema = WAND; tiefeThema = tiefe; }
                    case "RoofSurface" -> { thema = DACH; tiefeThema = tiefe; }
                    case "GroundSurface" -> { thema = GRUND; tiefeThema = tiefe; }
                    case "ClosureSurface", "OuterCeilingSurface", "OuterFloorSurface" -> { thema = SONST; tiefeThema = tiefe; }
                    case "exterior", "outerBoundaryIs" -> { imAussen = true; tiefeAussen = tiefe; punkte.clear(); }
                    case "interior", "innerBoundaryIs" -> innereRinge++;
                    case "posList" -> {
                        String d = r.getAttributeValue(null, "srsDimension");
                        dim = d != null ? Integer.parseInt(d.strip()) : 3;
                    }
                    default -> { }
                }
            } else if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                text.append(r.getText());
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                String ln = r.getLocalName();
                if (haus != null) {
                    switch (ln) {
                        case "posList" -> { if (imAussen) zahlen(text, punkte, dim); }
                        case "pos" -> { if (imAussen) zahlen(text, punkte, 3); }
                        case "name" -> { if (tiefe == tiefeHaus + 1 && haus.name.isEmpty()) haus.name = text.toString().strip(); }
                        case "function" -> { if (haus.funktion.isEmpty()) haus.funktion = text.toString().strip(); }
                        case "roofType" -> { if (haus.dachform.isEmpty()) haus.dachform = text.toString().strip(); }
                        case "measuredHeight" -> {
                            try {
                                double h = Double.parseDouble(text.toString().strip());
                                if (Double.isNaN(haus.hoehe) || h > haus.hoehe) haus.hoehe = h;
                            } catch (NumberFormatException ignored) { }
                        }
                        default -> { }
                    }
                    if (tiefe == tiefeAussen && imAussen) {
                        imAussen = false;
                        int n = punkte.size() / 3;
                        if (n >= 2 && punkte.get(0).equals(punkte.get(3 * (n - 1))) && punkte.get(1).equals(punkte.get(3 * (n - 1) + 1))
                                && punkte.get(2).equals(punkte.get(3 * (n - 1) + 2))) n--;
                        if (n >= 3) {
                            double[] ring = new double[3 * n];
                            for (int i = 0; i < 3 * n; i++) ring[i] = punkte.get(i);
                            haus.flaechen.add(new Flaeche(tiefeThema >= 0 ? thema : OHNE, ring));
                        }
                    }
                    if (tiefe == tiefeThema) { tiefeThema = -1; thema = OHNE; }
                    if (tiefe == tiefeHaus) {
                        ordne(haus);
                        haeuser.add(haus);
                        haus = null;
                        tiefeHaus = -1;
                    }
                }
                text.setLength(0);
                tiefe--;
            }
        }
    }

    private static String attribut(XMLStreamReader r, String lokal) {
        for (int i = 0; i < r.getAttributeCount(); i++) if (lokal.equals(r.getAttributeLocalName(i))) return r.getAttributeValue(i);
        return null;
    }

    private static void zahlen(CharSequence t, List<Double> aus, int dim) {
        String[] s = t.toString().strip().split("\\s+");
        if (s.length == 1 && s[0].isEmpty()) return;
        for (int i = 0; i + dim - 1 < s.length; i += dim) {
            aus.add(Double.parseDouble(s[i]));
            aus.add(Double.parseDouble(s[i + 1]));
            aus.add(dim >= 3 ? Double.parseDouble(s[i + 2]) : Double.NaN);
        }
    }

    /** Flächen ohne Thema nach der Neigung: fast senkrecht Wand, nach unten Grund, sonst Dach. */
    static void ordne(Haus h) {
        for (int i = 0; i < h.flaechen.size(); i++) {
            Flaeche f = h.flaechen.get(i);
            if (f.art != OHNE) continue;
            double[] n = com.dan.core.szene.Zerlegung.normale(f.ring);   // hier (Ost, Nord, Höhe): die Höhe ist die dritte
            int art = n == null ? SONST : Math.abs(n[2]) < 0.17 ? WAND : n[2] < -0.5 ? GRUND : DACH;
            h.flaechen.set(i, new Flaeche(art, f.ring));
        }
    }
}
