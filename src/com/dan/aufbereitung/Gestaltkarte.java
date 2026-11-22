package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.szene.Bauten;
import com.dan.core.szene.Gestalt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schritt gestalt (seit P12b, Werkbuch HalligHooge Teil A, 12.13; F132–F134 A): die Gestalt des Geländes als Karte zu
 * 0,5 m ({@code core.szene.Gestalt}) und die Körper im Watt und am Anleger ({@code bau/koerper.bin}).
 * <ol>
 * <li><b>Grund:</b> je Bildpunkt die Rauheitsklasse aus {@code rauheit/1m}: Fenne, Salzwiese, Deich, Warft, Deckwerk.
 * Kacheln zu 500 m nur dort, wo eine dieser Klassen vorkommt.</li>
 * <li><b>Wasser</b> (F134 A): Fethinge ({@code natural=water} ohne Art, Teich, Becken) ganz; Gräben und kleine Priele
 * ({@code waterway=ditch}, {@code stream}) in ihrer Breite (OSM {@code width}, sonst {@link #grabenBreite},
 * {@link #prielBreite} [G]). Wasserstand fest {@link #unterUfer} m unter dem Ufer (Median des Geländes am Ring bzw.
 * je Abschnitt beiderseits außerhalb der Breite); das Bild zeigt im Graben Wasser, wo das Gelände darunter liegt, im
 * Fething überall. Steht hydro höher, deckt dessen Wasser das Bild zu.</li>
 * <li><b>Wege</b> darüber, Belag aus {@code surface}, sonst nach der Art des Wegs [G]; Breite aus {@code width}, sonst
 * nach der Art [G].</li>
 * <li><b>Körper</b> (F133 A): {@code groyne} ohne Material oder aus Holz = Lahnung (zwei Pfahlreihen ±0,25 m, Pfahl
 * 0,12 m alle 0,8 m, 0,8 m über dem Watt, 0,3 m darin; Buschwerk dazwischen 0,55 m hoch) [G]; {@code breakwater} und
 * {@code material=boulder} = Steinbuhne (Schüttung 1,5 m breit, oben 0,6 m, 0,6 m hoch) [G]; {@code pier} als Fläche =
 * Anleger aus Beton (Deck auf {@link #anlegerHoehe} m NHN oder über dem Gelände, 1 m dick), als Linie = Steg aus Holz
 * (1,5 m breit, Deck {@link #stegHoehe} m NHN, Pfähle alle 3 m) [G].</li>
 * </ol>
 */
public final class Gestaltkarte {
    public double zelle = 0.5;
    public int n = 1000;
    public double grabenBreite = 2.0, prielBreite = 3.0, unterUfer = 0.3;
    public double anlegerHoehe = 2.6, stegHoehe = 2.2;
    public double pfahlAbstand = 0.8, pfahlUeber = 0.8, pfahlIn = 0.3, pfahl = 0.12, reihe = 0.25;

    // Ergebnis
    public Gestalt gestalt;
    public Bauten koerper;
    public final StringBuilder bericht = new StringBuilder();
    public int fethinge, graeben, priele, wege, lahnungen, buhnen, anleger, pfaehle;
    /** Prüfsteine 7, 11, 14 (Werkbuch Datei 69). */
    public double lageGraben = Double.NaN, anteilFething = Double.NaN, fussPfahl = Double.NaN;
    public int fethingeUnter90;

    record Kach(double ost, double nord, byte[] d) { }

    /** Vorläufige Art für Klasse 7 der Rauheit (nur während des Baus). */
    static final int WASSER_KLASSE = 9;

    private final List<Kach> kacheln = new ArrayList<>();
    private double gx0, gy0;
    private int nx, ny;
    private Kach[] raster;

    /** Baut Karte und Körper. gebiet: Ost0, Nord0, Ost1, Nord1; (e0, n0): Ursprung des Raums der Körper. */
    public void bauen(Gelaende.Stufe klassen, Gelaende gel, Osm osm, double[] gebiet, double e0, double n0) throws IOException {
        double s = zelle * n;
        gx0 = Math.floor(gebiet[0] / s) * s;
        gy0 = Math.floor(gebiet[1] / s) * s;
        nx = (int) Math.ceil((gebiet[2] - gx0) / s);
        ny = (int) Math.ceil((gebiet[3] - gy0) / s);
        raster = new Kach[nx * ny];
        Gelaende.Stufe.Leser kl = klassen.leser();
        Gelaende.Abfrage ab = gel.abfrage();
        // ---- 1 Grund aus den Klassen
        // Klasse 7 (Wasser) trägt in der Rauheit auch die Gräben und kleinen Priele: vorläufig als WASSER_KLASSE, damit das
        // Wasser der OSM dort liegen darf; was davon übrig bleibt, wird am Ende 0 (das große Wasser ist hydro)
        int[] code = {0, 0, 0, Gestalt.FENNE, Gestalt.SALZWIESE, Gestalt.DEICH, Gestalt.WARFT, WASSER_KLASSE, Gestalt.DECKWERK};
        for (int ty = 0; ty < ny; ty++)
            for (int tx = 0; tx < nx; tx++) {
                double ko = gx0 + tx * s, kn = gy0 + ty * s;
                byte[] d = new byte[2 * n * n];
                boolean land = false;
                for (int j = 0; j < n; j++)
                    for (int i = 0; i < n; i++) {
                        float c = kl.imPixel(ko + (i + 0.5) * zelle, kn + s - (j + 0.5) * zelle);
                        int q = Float.isNaN(c) ? 0 : (int) c;
                        int a = q >= 0 && q < code.length ? code[q] : 0;
                        if (a != 0) {
                            if (a != WASSER_KLASSE) land = true;
                            d[2 * (j * n + i)] = (byte) a;
                        }
                    }
                if (land) {
                    Kach k = new Kach(ko, kn, d);
                    kacheln.add(k);
                    raster[ty * nx + tx] = k;
                }
            }
        // ---- 2 Wasser
        List<double[][]> teiche = new ArrayList<>();
        for (Osm.Flaeche f : osm.flaechen) {
            Map<String, String> t = f.tags();
            String w = t.getOrDefault("water", "");
            if (!"water".equals(t.get("natural")) || !(w.isEmpty() || w.equals("pond") || w.equals("reservoir") || w.equals("basin"))) continue;
            double[][] r = f.ringe().get(0);
            if (!imGebiet(r, gebiet)) continue;
            double ufer = median(uferHoehen(r, ab));
            int st = Gestalt.standByte(ufer - unterUfer);
            if (fuelle(r, Gestalt.FETHING, st) > 0) {
                fethinge++;
                teiche.add(r);
            }
        }
        List<Osm.Weg> grabenLinien = new ArrayList<>();
        for (Osm.Weg w : osm.wege) {
            String ww = w.tags().get("waterway");
            if (!"ditch".equals(ww) && !"stream".equals(ww)) continue;
            if (!imGebiet(new double[][] {w.ost(), w.nord()}, gebiet)) continue;
            boolean graben = "ditch".equals(ww);
            double b = breite(w.tags(), graben ? grabenBreite : prielBreite);
            int art = graben ? Gestalt.GRABEN : Gestalt.PRIEL;
            for (int i = 0; i + 1 < w.ost().length; i++) {
                double ax = w.ost()[i], ay = w.nord()[i], bx = w.ost()[i + 1], by = w.nord()[i + 1];
                double ufer = uferAbschnitt(ax, ay, bx, by, b, ab);
                strich(ax, ay, bx, by, b, art, Gestalt.standByte(ufer - unterUfer), false);
            }
            if (graben) { graeben++; grabenLinien.add(w); } else priele++;
        }
        // ---- 3 Wege
        for (Osm.Weg w : osm.wege) {
            String h = w.tags().get("highway");
            if (h == null || "proposed".equals(h) || "construction".equals(h)) continue;
            if (!imGebiet(new double[][] {w.ost(), w.nord()}, gebiet)) continue;
            int belag = belag(h, w.tags().get("surface"));
            double b = breite(w.tags(), wegBreite(h));
            if (b <= 0) continue;
            for (int i = 0; i + 1 < w.ost().length; i++) strich(w.ost()[i], w.nord()[i], w.ost()[i + 1], w.nord()[i + 1], b, belag, 0, true);
            wege++;
        }
        for (Kach k : kacheln)
            for (int o = 0; o < k.d.length; o += 2) if ((k.d[o] & 0xFF) == WASSER_KLASSE) k.d[o] = 0;
        gestalt = new Gestalt(zelle, n, kacheln.stream().map(k -> new Gestalt.Kachel(k.ost, k.nord, k.d)).toList());
        // ---- Prüfsteine 7 und 11
        pruefen(grabenLinien, teiche);
        // ---- 4 Körper
        koerper(osm, ab, gebiet, e0, n0);
        bericht.append(String.format(Locale.ROOT, "%d Kacheln zu %.0f m (%.1f MB), %d Fethinge, %d Gräben, %d Priele, %d Wege; Körper: %d Lahnungen (%d Pfähle), %d Steinbuhnen, "
                        + "%d Anleger und Stege, %d Dreiecke; Prüfstein 7: Mittellinie der Gräben im Wasser %.1f %%; Prüfstein 11: Fethinge zu %.1f %% Wasser (unter 90 %%: %d); "
                        + "Prüfstein 14: Fuß der Pfähle %.2f m unter dem Gelände", kacheln.size(), s, kacheln.size() * 2.0 * n * n / 1e6, fethinge, graeben, priele, wege, lahnungen, pfaehle,
                buhnen, anleger, koerper.dreiecke(), 100 * lageGraben, 100 * anteilFething, fethingeUnter90, fussPfahl));
    }

    // ------------------------------------------------------------------ Raster

    private Kach kachel(double x, double y) {
        int tx = (int) Math.floor((x - gx0) / (zelle * n)), ty = (int) Math.floor((y - gy0) / (zelle * n));
        if (tx < 0 || ty < 0 || tx >= nx || ty >= ny) return null;
        return raster[ty * nx + tx];
    }

    /** Index in den Daten der Kachel für den Bildpunkt mit der Mitte nahe (x, y); −1 außerhalb. */
    private int index(Kach k, double x, double y) {
        int i = (int) Math.floor((x - k.ost) / zelle), j = (int) Math.floor((k.nord + zelle * n - y) / zelle);
        if (i < 0 || j < 0 || i >= n || j >= n) return -1;
        return 2 * (j * n + i);
    }

    int art(double x, double y) {
        Kach k = kachel(x, y);
        if (k == null) return 0;
        int o = index(k, x, y);
        return o < 0 ? 0 : k.d[o] & 0xFF;
    }

    private void setze(double x, double y, int art, int stand, boolean auchOhneLand) {
        Kach k = kachel(x, y);
        if (k == null) return;
        int o = index(k, x, y);
        if (o < 0) return;
        if (!auchOhneLand && k.d[o] == 0) return;            // Wasser nur auf Land (im Watt ist es hydro)
        k.d[o] = (byte) art;
        k.d[o + 1] = (byte) stand;
    }

    /** Füllt einen Ring (Ost[], Nord[], geschlossen); Zahl der gesetzten Bildpunkte. */
    private int fuelle(double[][] r, int art, int stand) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (int i = 0; i < r[0].length; i++) {
            x0 = Math.min(x0, r[0][i]); x1 = Math.max(x1, r[0][i]);
            y0 = Math.min(y0, r[1][i]); y1 = Math.max(y1, r[1][i]);
        }
        int z = 0;
        for (double y = Math.floor(y0 / zelle) * zelle + zelle / 2; y < y1; y += zelle)
            for (double x = Math.floor(x0 / zelle) * zelle + zelle / 2; x < x1; x += zelle)
                if (innen(r, x, y)) {
                    Kach k = kachel(x, y);
                    if (k == null) continue;
                    int o = index(k, x, y);
                    if (o < 0) continue;
                    k.d[o] = (byte) art;
                    k.d[o + 1] = (byte) stand;
                    z++;
                }
        return z;
    }

    /** Ein Strich der Breite b von a nach c. */
    private void strich(double ax, double ay, double cx, double cy, double b, int art, int stand, boolean auchOhneLand) {
        double h = b / 2;
        double x0 = Math.min(ax, cx) - h, x1 = Math.max(ax, cx) + h, y0 = Math.min(ay, cy) - h, y1 = Math.max(ay, cy) + h;
        double dx = cx - ax, dy = cy - ay, l2 = dx * dx + dy * dy;
        for (double y = Math.floor(y0 / zelle) * zelle + zelle / 2; y < y1; y += zelle)
            for (double x = Math.floor(x0 / zelle) * zelle + zelle / 2; x < x1; x += zelle) {
                double t = l2 > 0 ? Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / l2)) : 0;
                if (Math.hypot(x - ax - t * dx, y - ay - t * dy) <= h) setze(x, y, art, stand, auchOhneLand);
            }
    }

    /** Setzt im Strich der Breite b von a nach c die Art {@code alt} auf keine (0). */
    private void loesche(double ax, double ay, double cx, double cy, double b, int alt) {
        double h = b / 2;
        double x0 = Math.min(ax, cx) - h, x1 = Math.max(ax, cx) + h, y0 = Math.min(ay, cy) - h, y1 = Math.max(ay, cy) + h;
        double dx = cx - ax, dy = cy - ay, l2 = dx * dx + dy * dy;
        for (double y = Math.floor(y0 / zelle) * zelle + zelle / 2; y < y1; y += zelle)
            for (double x = Math.floor(x0 / zelle) * zelle + zelle / 2; x < x1; x += zelle) {
                double t = l2 > 0 ? Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / l2)) : 0;
                if (Math.hypot(x - ax - t * dx, y - ay - t * dy) > h) continue;
                Kach k = kachel(x, y);
                if (k == null) continue;
                int o = index(k, x, y);
                if (o >= 0 && (k.d[o] & 0xFF) == alt) {
                    k.d[o] = 0;
                    k.d[o + 1] = 0;
                }
            }
    }

    static boolean innen(double[][] r, double x, double y) {
        boolean in = false;
        int n = r[0].length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = r[0][i], yi = r[1][i], xj = r[0][j], yj = r[1][j];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    static boolean imGebiet(double[][] r, double[] g) {
        for (int i = 0; i < r[0].length; i++) if (r[0][i] >= g[0] && r[0][i] < g[2] && r[1][i] >= g[1] && r[1][i] < g[3]) return true;
        return false;
    }

    static List<Double> uferHoehen(double[][] r, Gelaende.Abfrage ab) {
        List<Double> l = new ArrayList<>();
        for (int i = 0; i < r[0].length; i++) {
            float h = ab.hoehe(r[0][i], r[1][i], 1);
            if (!Float.isNaN(h)) l.add((double) h);
        }
        return l;
    }

    /** Ufer eines Abschnitts: Median des Geländes beiderseits, 1 m außerhalb der halben Breite, alle 2 m. */
    static double uferAbschnitt(double ax, double ay, double bx, double by, double b, Gelaende.Abfrage ab) {
        double dx = bx - ax, dy = by - ay, l = Math.hypot(dx, dy);
        List<Double> h = new ArrayList<>();
        if (l < 1e-6) return Double.NaN;
        double qx = -dy / l, qy = dx / l, a = b / 2 + 1;
        for (double t = 0; t <= l; t += 2)
            for (int s = -1; s <= 1; s += 2) {
                float v = ab.hoehe(ax + dx * t / l + s * qx * a, ay + dy * t / l + s * qy * a, 1);
                if (!Float.isNaN(v)) h.add((double) v);
            }
        return median(h);
    }

    static double median(List<Double> l) {
        if (l.isEmpty()) return 1.5;                              // Fenne von Hooge [G], wenn das Gelände fehlt
        List<Double> s = new ArrayList<>(l);
        java.util.Collections.sort(s);
        return s.get(s.size() / 2);
    }

    static double breite(Map<String, String> t, double vorgabe) {
        String w = t.get("width");
        if (w != null) try {
            return Double.parseDouble(w.replace(',', '.').replace("m", "").strip());
        } catch (NumberFormatException ignored) { }
        return vorgabe;
    }

    /** Breite eines Wegs nach seiner Art (m) [G]. */
    static double wegBreite(String h) {
        return switch (h) {
            case "primary", "secondary", "tertiary" -> 3.5;
            case "unclassified", "residential", "service" -> 3.0;
            case "track" -> 2.5;
            case "cycleway" -> 2.0;
            case "footway", "steps", "pedestrian" -> 1.5;
            case "path", "bridleway" -> 1.0;
            default -> 0;
        };
    }

    /** Belag aus {@code surface}, sonst nach der Art [G]. */
    static int belag(String h, String surface) {
        if (surface != null) switch (surface) {
            case "asphalt" -> { return Gestalt.ASPHALT; }
            case "paved", "paving_stones", "sett", "bricks", "cobblestone", "unhewn_cobblestone" -> { return Gestalt.KLINKER; }
            case "gravel", "fine_gravel", "compacted", "unpaved", "pebblestone", "shells", "sand" -> { return Gestalt.SCHOTTER; }
            case "concrete", "concrete:plates", "concrete:lanes" -> { return Gestalt.BETON; }
            case "grass", "grass_paver", "ground", "dirt", "earth", "mud" -> { return Gestalt.GRASWEG; }
            case "wood", "metal" -> { return Gestalt.HOLZ; }
            default -> { }
        }
        return switch (h) {
            case "primary", "secondary", "tertiary", "cycleway" -> Gestalt.ASPHALT;
            case "unclassified", "residential", "service", "footway", "pedestrian" -> Gestalt.KLINKER;
            case "track", "steps" -> Gestalt.BETON;
            default -> Gestalt.GRASWEG;
        };
    }

    // ------------------------------------------------------------------ Prüfsteine 7, 11

    private void pruefen(List<Osm.Weg> graeben, List<double[][]> teiche) {
        long im = 0, alle = 0;
        for (Osm.Weg w : graeben)
            for (int i = 0; i + 1 < w.ost().length; i++) {
                double dx = w.ost()[i + 1] - w.ost()[i], dy = w.nord()[i + 1] - w.nord()[i], l = Math.hypot(dx, dy);
                for (double t = 0.5; t < l; t += 1) {
                    double x = w.ost()[i] + dx * t / l, y = w.nord()[i] + dy * t / l;
                    if (art(x, y) == 0) continue;                 // außerhalb der Karte (im Watt)
                    alle++;
                    boolean ok = false;
                    for (double oy = -1; oy <= 1 && !ok; oy += 0.5)
                        for (double ox = -1; ox <= 1 && !ok; ox += 0.5) if (Math.hypot(ox, oy) <= 1 && Gestalt.wasser(art(x + ox, y + oy))) ok = true;
                    if (ok) im++;
                }
            }
        lageGraben = alle > 0 ? (double) im / alle : Double.NaN;
        double summe = 0;
        int z = 0;
        for (double[][] r : teiche) {
            long innen = 0, wasser = 0;
            double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
            for (int i = 0; i < r[0].length; i++) {
                x0 = Math.min(x0, r[0][i]); x1 = Math.max(x1, r[0][i]);
                y0 = Math.min(y0, r[1][i]); y1 = Math.max(y1, r[1][i]);
            }
            for (double y = Math.floor(y0 / zelle) * zelle + zelle / 2; y < y1; y += zelle)
                for (double x = Math.floor(x0 / zelle) * zelle + zelle / 2; x < x1; x += zelle)
                    if (innen(r, x, y)) {
                        innen++;
                        if (Gestalt.wasser(art(x, y))) wasser++;
                    }
            if (innen == 0) continue;
            double a = (double) wasser / innen;
            summe += a;
            z++;
            if (a < 0.9) fethingeUnter90++;
        }
        anteilFething = z > 0 ? summe / z : Double.NaN;
    }

    // ------------------------------------------------------------------ Körper

    private final List<Object[]> pfahlGruppen = new ArrayList<>();

    private void koerper(Osm osm, Gelaende.Abfrage ab, double[] gebiet, double e0, double n0) {
        Bauten.Bauer b = new Bauten.Bauer();
        double fussSumme = 0;
        int fussZahl = 0;
        for (Osm.Weg w : osm.wege) {
            String mm = w.tags().get("man_made");
            if (mm == null || !(mm.equals("groyne") || mm.equals("breakwater") || mm.equals("pier"))) continue;
            if (w.geschlossen() && mm.equals("pier")) continue;              // Fläche: unten
            if (!imGebiet(new double[][] {w.ost(), w.nord()}, gebiet)) continue;
            String mat = w.tags().getOrDefault("material", "");
            double[] x = w.ost(), y = w.nord();
            if (mm.equals("pier")) {
                // Steg aus Holz: Deck als flacher Strang, Pfähle alle 3 m
                Bauten.Gebaeude g = neu(b, "Steg " + w.name(), Bauten.HOLZ);
                double bb = breite(w.tags(), 1.5);
                double[] xs = new double[x.length], zs = new double[x.length], ys = new double[x.length];
                double tief = Double.MAX_VALUE;
                for (int i = 0; i < x.length; i++) {
                    xs[i] = x[i] - e0; zs[i] = n0 - y[i]; ys[i] = stegHoehe - 0.15;
                    tief = Math.min(tief, gel(ab, x[i], y[i]));
                }
                g.boden = (float) tief; g.traufe = g.first = (float) stegHoehe;
                b.strang(Bauten.HOLZ, xs, zs, ys, bb, bb, 0.15);
                laengs(x, y, 3.0, (px, py, dx, dy) -> {
                    double gg = gel(ab, px, py);
                    b.quader(Bauten.HOLZ, px - e0, n0 - py, dx, -dy, 0.1, 0.1, gg - 0.5, stegHoehe - 0.15);
                });
                b.fertig();
                anleger++;
                continue;
            }
            boolean stein = mat.equals("boulder") || mat.equals("stone") || (mm.equals("breakwater") && !mat.equals("wood"));
            if (stein) {
                Bauten.Gebaeude g = neu(b, "Steinbuhne " + w.name(), Bauten.STEIN);
                List<double[]> p = punkte(x, y, 1.0);
                double[] xs = new double[p.size()], zs = new double[p.size()], ys = new double[p.size()];
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (int i = 0; i < p.size(); i++) {
                    xs[i] = p.get(i)[0] - e0; zs[i] = n0 - p.get(i)[1]; ys[i] = gel(ab, p.get(i)[0], p.get(i)[1]) - 0.1;
                    lo = Math.min(lo, ys[i]); hi = Math.max(hi, ys[i] + 0.7);
                }
                g.boden = (float) lo; g.traufe = g.first = (float) hi;
                b.strang(Bauten.STEIN, xs, zs, ys, 1.5, 0.6, 0.7);
                b.fertig();
                buhnen++;
            } else {
                // Lahnung: zwei Pfahlreihen, Buschwerk dazwischen. Die Rauheit hat Lahnungen in Klasse 8 („Deckwerk und
                // Lahnung“): unter der Lahnung liegt aber Schlick, keine Steine – dort kein Deckwerk in der Karte
                for (int i = 0; i + 1 < x.length; i++) loesche(x[i], y[i], x[i + 1], y[i + 1], 4.0, Gestalt.DECKWERK);
                // Die Pfähle kommen als eigene Gruppe ans Ende der Körper (render.bau zeichnet sie nur in der Nähe)
                Bauten.Gebaeude g = neu(b, "Lahnung " + w.name(), Bauten.BUSCH);
                double[] lohi = {Double.MAX_VALUE, -Double.MAX_VALUE};
                List<double[]> pf = new ArrayList<>();
                laengs(x, y, pfahlAbstand, (px, py, dx, dy) -> {
                    double l = Math.hypot(dx, dy), qx = -dy / l, qy = dx / l;
                    for (int s = -1; s <= 1; s += 2) {
                        double ox = px + s * qx * reihe, oy = py + s * qy * reihe, gg = gel(ab, ox, oy);
                        pf.add(new double[] {ox, oy, dx, dy, gg});
                        lohi[0] = Math.min(lohi[0], gg - pfahlIn);
                        lohi[1] = Math.max(lohi[1], gg + pfahlUeber);
                    }
                });
                pfahlGruppen.add(new Object[] {w.name(), pf});
                List<double[]> p = punkte(x, y, 2.0);
                double[] xs = new double[p.size()], zs = new double[p.size()], ys = new double[p.size()];
                for (int i = 0; i < p.size(); i++) { xs[i] = p.get(i)[0] - e0; zs[i] = n0 - p.get(i)[1]; ys[i] = gel(ab, p.get(i)[0], p.get(i)[1]) - 0.05; }
                b.strang(Bauten.BUSCH, xs, zs, ys, 2 * reihe - 0.1, 2 * reihe - 0.2, 0.55);
                g.boden = (float) lohi[0]; g.traufe = g.first = (float) lohi[1];
                b.fertig();
                lahnungen++;
            }
        }
        // Anleger als Fläche (Beton)
        for (Osm.Flaeche f : osm.flaechen) {
            if (!"pier".equals(f.tags().get("man_made"))) continue;
            double[][] r = f.ringe().get(0);
            if (!imGebiet(r, gebiet)) continue;
            int k = r[0].length - 1;
            if (k < 3) continue;
            double hoch = anlegerHoehe, mx = 0, mz = 0;
            for (int i = 0; i < k; i++) { hoch = Math.max(hoch, gel(ab, r[0][i], r[1][i]) + 0.3); mx += r[0][i] - e0; mz += n0 - r[1][i]; }
            mx /= k; mz /= k;
            Bauten.Gebaeude g = neu(b, "Anleger " + f.name(), Bauten.BETON);
            g.boden = (float) (hoch - 1); g.traufe = g.first = (float) hoch;
            double[] deck = new double[3 * k];
            for (int i = 0; i < k; i++) {
                int j = (i + 1) % k;
                double ax = r[0][i] - e0, az = n0 - r[1][i], cx = r[0][j] - e0, cz = n0 - r[1][j];
                b.flaeche(Bauten.BETON, new double[] {ax, hoch - 1, az, cx, hoch - 1, cz, cx, hoch, cz, ax, hoch, az}, mx, mz);
                deck[3 * i] = ax; deck[3 * i + 1] = hoch; deck[3 * i + 2] = az;
            }
            b.flaeche(Bauten.BETON, deck, mx, mz);
            b.fertig();
            anleger++;
        }
        // Pfähle der Lahnungen zuletzt, je Lahnung eine Gruppe „Pfähle …“
        for (Object[] o : pfahlGruppen) {
            @SuppressWarnings("unchecked") List<double[]> pf = (List<double[]>) o[1];
            Bauten.Gebaeude g = neu(b, "Pfähle " + o[0], Bauten.HOLZ);
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (double[] q : pf) {
                b.quader(Bauten.HOLZ, q[0] - e0, n0 - q[1], q[2], -q[3], pfahl / 2, pfahl / 2, q[4] - pfahlIn, q[4] + pfahlUeber);
                lo = Math.min(lo, q[4] - pfahlIn);
                hi = Math.max(hi, q[4] + pfahlUeber);
                fussSumme += pfahlIn;
                fussZahl++;
                pfaehle++;
            }
            g.boden = (float) lo; g.traufe = g.first = (float) hi;
            b.fertig();
        }
        pfahlGruppen.clear();
        koerper = b.bauten(e0, n0);
        fussPfahl = fussZahl > 0 ? fussSumme / fussZahl : Double.NaN;
    }

    private static Bauten.Gebaeude neu(Bauten.Bauer b, String name, int art) {
        Bauten.Gebaeude g = b.neu();
        g.dach = art;
        g.kennung = name;
        g.name = name;
        return g;
    }

    private static double gel(Gelaende.Abfrage ab, double x, double y) {
        float h = ab.hoehe(x, y, 1);
        return Float.isNaN(h) ? 0 : h;
    }

    interface Stelle { void bei(double x, double y, double dx, double dy); }

    /** Ruft {@code s} alle {@code abstand} m längs der Linie auf, mit der Richtung des Abschnitts. */
    static void laengs(double[] x, double[] y, double abstand, Stelle s) {
        double rest = abstand / 2;
        for (int i = 0; i + 1 < x.length; i++) {
            double dx = x[i + 1] - x[i], dy = y[i + 1] - y[i], l = Math.hypot(dx, dy);
            if (l < 1e-6) continue;
            double t = rest;
            while (t <= l) {
                s.bei(x[i] + dx * t / l, y[i] + dy * t / l, dx, dy);
                t += abstand;
            }
            rest = t - l;
        }
    }

    /** Punkte längs der Linie, höchstens {@code abstand} m auseinander, mit den Ecken. */
    static List<double[]> punkte(double[] x, double[] y, double abstand) {
        List<double[]> p = new ArrayList<>();
        for (int i = 0; i + 1 < x.length; i++) {
            double dx = x[i + 1] - x[i], dy = y[i + 1] - y[i], l = Math.hypot(dx, dy);
            int k = Math.max(1, (int) Math.ceil(l / abstand));
            for (int j = 0; j < k; j++) p.add(new double[] {x[i] + dx * j / k, y[i] + dy * j / k});
        }
        p.add(new double[] {x[x.length - 1], y[y.length - 1]});
        return p;
    }

    /** Schreibt {@code gestalt/} und {@code bau/koerper.bin}. */
    public void schreibe(File gestaltOrdner, File bauOrdner, String kopf) throws IOException {
        gestalt.schreibe(gestaltOrdner, kopf + "; " + bericht);
        java.nio.file.Files.createDirectories(bauOrdner.toPath());
        try (var out = new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(new File(bauOrdner, "koerper.bin").toPath()))) {
            koerper.schreibe(out);
        }
        // die Werte der Prüfsteine 7, 11 und 14 für GestaltCheck (Schlüssel = Wert)
        java.nio.file.Files.writeString(new File(gestaltOrdner, "pruefung.txt").toPath(), String.format(Locale.ROOT,
                "# Gestaltkarte, Prüfsteine (Werkbuch HalligHooge Datei 69)%nkacheln = %d%nfethinge = %d%ngraeben = %d%npriele = %d%nwege = %d%n"
                        + "lahnungen = %d%npfaehle = %d%nbuhnen = %d%nanleger = %d%ndreiecke = %d%nlage_graben = %.5f%nanteil_fething = %.5f%n"
                        + "fethinge_unter_90 = %d%nfuss_pfahl = %.4f%n",
                gestalt.kacheln.size(), fethinge, graeben, priele, wege, lahnungen, pfaehle, buhnen, anleger, koerper.dreiecke(), lageGraben, anteilFething,
                fethingeUnter90, fussPfahl), java.nio.charset.StandardCharsets.UTF_8);
    }
}
