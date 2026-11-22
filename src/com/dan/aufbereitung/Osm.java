package com.dan.aufbereitung;

import com.dan.core.raum.Utm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * OpenStreetMap aus einer Antwort von Overpass mit {@code out body geom} (Werkbuch HalligHooge Teil A, 10.4, Schritte
 * 5 und 6), in UTM umgerechnet ({@link Utm}): Punkte, Wege mit ihrer Geometrie und Flächen. Flächen sind geschlossene
 * Wege und Multipolygone; die Ringe eines Multipolygons werden aus den Wegen seiner Mitglieder zusammengesetzt und
 * gerade-ungerade gefüllt (innere Ringe ohne Rolle sind damit Löcher).
 * <p>
 * Mehrere Dateien werden zusammengelegt; ein Objekt, das in mehreren steht, zählt einmal.
 */
public final class Osm {
    /** Ein Punkt in UTM. */
    public record Punkt(long id, Map<String, String> tags, double ost, double nord) { }

    /** Ein Weg in UTM; geschlossen, wenn erster und letzter Punkt gleich sind. */
    public record Weg(long id, Map<String, String> tags, double[] ost, double[] nord) {
        public boolean geschlossen() { return ost.length > 3 && ost[0] == ost[ost.length - 1] && nord[0] == nord[nord.length - 1]; }

        public double laenge() {
            double l = 0;
            for (int i = 1; i < ost.length; i++) l += Math.hypot(ost[i] - ost[i - 1], nord[i] - nord[i - 1]);
            return l;
        }

        public String name() { return "way/" + id; }
    }

    /** Eine Fläche: Ringe {Ost[], Nord[]}, gerade-ungerade gefüllt. {@code relation}: aus einem Multipolygon. */
    public record Flaeche(long id, boolean relation, Map<String, String> tags, List<double[][]> ringe) {
        public double[] grenzen() {
            double[] g = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (double[][] r : ringe)
                for (int i = 0; i < r[0].length; i++) {
                    g[0] = Math.min(g[0], r[0][i]);
                    g[1] = Math.min(g[1], r[1][i]);
                    g[2] = Math.max(g[2], r[0][i]);
                    g[3] = Math.max(g[3], r[1][i]);
                }
            return g;
        }

        public String name() { return (relation ? "relation/" : "way/") + id; }
    }

    public final List<Punkt> punkte = new ArrayList<>();
    public final List<Weg> wege = new ArrayList<>();
    public final List<Flaeche> flaechen = new ArrayList<>();
    /** Multipolygone, deren Ringe sich nicht schließen ließen (ihre offenen Teile fehlen). */
    public int offeneRinge;
    /** Stand der Daten (osm3s.timestamp_osm_base) je Datei. */
    public final List<String> stand = new ArrayList<>();

    private final Set<String> gesehen = new HashSet<>();

    /** Liest eine oder mehrere Dateien; Zone von UTM (32 für EPSG:25832). */
    public static Osm lies(int zone, Path... dateien) throws IOException {
        Osm o = new Osm();
        for (Path p : dateien) o.dazu(Json.lies(p), zone, p.getFileName().toString());
        return o;
    }

    /** Aus einem schon gelesenen JSON-Text (für die Prüfung). */
    public static Osm aus(String json, int zone) throws IOException {
        Osm o = new Osm();
        o.dazu(Json.text(json), zone, "Text");
        return o;
    }

    private void dazu(Object wurzel, int zone, String quelle) throws IOException {
        Map<String, Object> w = Json.objekt(wurzel);
        Object st = Json.objekt(w.get("osm3s")).get("timestamp_osm_base");
        stand.add(quelle + ": " + (st == null ? "?" : st));
        for (Object e : Json.feld(w.get("elements"))) {
            Map<String, Object> m = Json.objekt(e);
            String typ = String.valueOf(m.get("type"));
            long id = ((Number) m.get("id")).longValue();
            if (!gesehen.add(typ + id)) continue;
            Map<String, String> tags = tags(m.get("tags"));
            switch (typ) {
                case "node" -> {
                    double[] u = Utm.vorwaerts(Json.zahl(m.get("lon")), Json.zahl(m.get("lat")), zone);
                    if (!tags.isEmpty()) punkte.add(new Punkt(id, tags, u[0], u[1]));
                }
                case "way" -> {
                    for (double[][] t : geometrie(m.get("geometry"), zone)) {
                        Weg weg = new Weg(id, tags, t[0], t[1]);
                        wege.add(weg);
                        if (weg.geschlossen()) flaechen.add(new Flaeche(id, false, tags, List.<double[][]>of(t)));
                    }
                }
                case "relation" -> {
                    if (!"multipolygon".equals(tags.get("type")) && !"boundary".equals(tags.get("type"))) continue;
                    List<double[][]> teile = new ArrayList<>();
                    for (Object mo : Json.feld(m.get("members"))) {
                        Map<String, Object> mm = Json.objekt(mo);
                        if ("way".equals(mm.get("type"))) teile.addAll(geometrie(mm.get("geometry"), zone));
                    }
                    List<double[][]> ringe = ringe(teile);
                    if (!ringe.isEmpty()) flaechen.add(new Flaeche(id, true, tags, ringe));
                }
                default -> { }
            }
        }
    }

    private static Map<String, String> tags(Object o) {
        Map<String, Object> t = Json.objekt(o);
        if (t.isEmpty()) return Map.of();
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : t.entrySet()) m.put(e.getKey(), String.valueOf(e.getValue()));
        return Collections.unmodifiableMap(m);
    }

    /** Geometrie eines Wegs; Lücken (null in Overpass) teilen ihn in Stücke. */
    private static List<double[][]> geometrie(Object o, int zone) {
        List<double[][]> stuecke = new ArrayList<>();
        List<double[]> s = new ArrayList<>();
        for (Object q : Json.feld(o)) {
            if (q == null) {
                if (s.size() >= 2) stuecke.add(feld(s));
                s.clear();
                continue;
            }
            Map<String, Object> p = Json.objekt(q);
            s.add(Utm.vorwaerts(Json.zahl(p.get("lon")), Json.zahl(p.get("lat")), zone));
        }
        if (s.size() >= 2) stuecke.add(feld(s));
        return stuecke;
    }

    private static double[][] feld(List<double[]> s) {
        double[][] t = new double[2][s.size()];
        for (int i = 0; i < s.size(); i++) {
            t[0][i] = s.get(i)[0];
            t[1][i] = s.get(i)[1];
        }
        return t;
    }

    /** Setzt Wegstücke an gleichen Endpunkten zu geschlossenen Ringen zusammen. */
    private List<double[][]> ringe(List<double[][]> teile) {
        List<double[][]> ringe = new ArrayList<>(), offen = new ArrayList<>();
        for (double[][] t : teile) {
            int n = t[0].length;
            if (n > 3 && t[0][0] == t[0][n - 1] && t[1][0] == t[1][n - 1]) ringe.add(t);
            else offen.add(t);
        }
        boolean fehlt = false;
        while (!offen.isEmpty()) {
            List<double[]> r = new ArrayList<>();
            double[][] a = offen.remove(offen.size() - 1);
            for (int i = 0; i < a[0].length; i++) r.add(new double[] {a[0][i], a[1][i]});
            boolean zu = false;
            while (!zu) {
                double[] ende = r.get(r.size() - 1), anfang = r.get(0);
                if (r.size() > 3 && ende[0] == anfang[0] && ende[1] == anfang[1]) { zu = true; break; }
                int k = -1;
                boolean umgekehrt = false;
                for (int i = 0; i < offen.size() && k < 0; i++) {
                    double[][] t = offen.get(i);
                    int n = t[0].length;
                    if (t[0][0] == ende[0] && t[1][0] == ende[1]) k = i;
                    else if (t[0][n - 1] == ende[0] && t[1][n - 1] == ende[1]) { k = i; umgekehrt = true; }
                }
                if (k < 0) break;
                double[][] t = offen.remove(k);
                int n = t[0].length;
                for (int i = 1; i < n; i++) {
                    int j = umgekehrt ? n - 1 - i : i;
                    r.add(new double[] {t[0][j], t[1][j]});
                }
            }
            if (zu) ringe.add(feld(r));
            else fehlt = true;
        }
        if (fehlt) offeneRinge++;
        return ringe;
    }

    // ------------------------------------------------------------------ Auswahl

    /**
     * Auswahl nach Tags: Ausdrücke durch {@code ;} getrennt, von denen einer passen muss. Ein Ausdruck ist
     * {@code schlüssel=w1|w2}, {@code schlüssel=*} (vorhanden) oder {@code schlüssel~regex} (ohne Groß- und
     * Kleinschreibung); mehrere mit {@code &} müssen alle passen. Leerer Text: nichts passt.
     */
    public static final class Filter {
        private record Glied(String schluessel, Pattern muster, Set<String> werte) {
            boolean passt(Map<String, String> tags) {
                String v = tags.get(schluessel);
                if (v == null) return false;
                if (muster != null) return muster.matcher(v).find();
                return werte == null || werte.contains(v);
            }
        }

        private final List<List<Glied>> oder = new ArrayList<>();
        private final String text;

        private Filter(String text) { this.text = text; }

        public static Filter lies(String text) {
            Filter f = new Filter(text == null ? "" : text.strip());
            if (f.text.isEmpty()) return f;
            for (String a : f.text.split(";")) {
                if (a.isBlank()) continue;
                List<Glied> und = new ArrayList<>();
                for (String b : a.split("&")) {
                    b = b.strip();
                    int k = b.indexOf('='), r = b.indexOf('~');
                    if (r > 0 && (k < 0 || r < k)) und.add(new Glied(b.substring(0, r).strip(), Pattern.compile(b.substring(r + 1).strip(), Pattern.CASE_INSENSITIVE), null));
                    else if (k > 0) {
                        String w = b.substring(k + 1).strip();
                        Set<String> werte = null;
                        if (!w.equals("*")) {
                            werte = new HashSet<>();
                            for (String x : w.split("\\|")) werte.add(x.strip());
                        }
                        und.add(new Glied(b.substring(0, k).strip(), null, werte));
                    } else throw new IllegalArgumentException("Filter: '" + b + "' ohne = oder ~");
                }
                f.oder.add(und);
            }
            return f;
        }

        public boolean leer() { return oder.isEmpty(); }

        public boolean passt(Map<String, String> tags) {
            for (List<Glied> und : oder) {
                boolean alle = true;
                for (Glied g : und) if (!g.passt(tags)) { alle = false; break; }
                if (alle) return true;
            }
            return false;
        }

        @Override public String toString() { return text; }
    }
}
