package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.szene.Bauten;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schritt gebaeude (seit P12a, Werkbuch HalligHooge Teil A, 12.13; F125 A, F126 A): die Häuser eines Gebiets aus dem
 * 3D-Gebäudemodell LoD2 als Körper für das Bild ({@code core.szene.Bauten}).
 * <ol>
 * <li><b>Lesen:</b> CityGML ({@link CityGml}) aus {@code gebaeude.ordner}; Häuser, deren Grundriss in
 * {@code gebaeude.gebiet} liegt.</li>
 * <li><b>Höhen:</b> Boden aus den Grundflächen, Traufe (tiefster Punkt der Dächer), First (höchster Punkt). Prüfstein 2:
 * Boden gegen {@code Gelaende.hoehe} an den Ecken der Grundfläche. Damit kein Haus über einer Kante schwebt, reichen die
 * Wände bis {@link #sockel} unter das tiefste Gelände an diesen Ecken hinab [G].</li>
 * <li><b>Dach</b> (Daniel, 19.11.2026: „grundsätzlich rote Backsteinbauten mit Reetdach“): Reet, außer bei flachen
 * Dächern (Dachform 1000 oder alle Dachflächen flacher als {@link #flach}°). Die Farbe des Dachs ist gemessen: das
 * Mittel der DOP20-Pixel über den Dachflächen, {@link #rand} m vom Rand entfernt, mit der Eichung des Schritts
 * {@code luftbild} (ρ = g · d, {@code luftbild/albedo.txt}). Liegt sie außerhalb des Bereichs für Reet
 * ({@link #reetHell}), gilt die Vorgabe {@link #reet} [G]. Wie viele Dächer der Farbe nach Ziegel wären (Rot deutlich
 * über Grün und Blau), meldet der Bericht nur ({@code gebaeude.ausnahmen = farbe} würde sie als Ziegel führen).</li>
 * <li><b>Wände:</b> Backstein, {@link #wand} [G] mit ±{@link #streuung} je Haus.</li>
 * <li><b>Namen und Prüfstein 1:</b> Jedes Gebäude der OSM ({@code building=*}) im Gebiet bekommt das LoD2-Haus, dessen
 * Grundfläche seine Mitte enthält (sonst das nächste bis {@link #fang} m); sein Name (oder {@code amenity}) geht auf
 * das Haus.</li>
 * </ol>
 * Ablage {@code bau/gebaeude.bin} ({@link Bauten}) und {@code bau/gebaeude.txt} (Bericht, je Haus eine Zeile). Raum wie
 * die Clipmap des Geländes: Ursprung an der Nordwestecke der Stufe 1 m.
 */
public final class Gebaeude {
    /** Wände so tief unter das tiefste Gelände an den Ecken (m) [G]. */
    public double sockel = 0.15;
    /** Dachflächen flacher als dieser Winkel (Grad) gelten als flach [G]. */
    public double flach = 10;
    /** Abstand der gemessenen Pixel vom Rand der Dachfläche (m) [G]. */
    public double rand = 0.4;
    /** Albedo von Reet, wenn die Messung fehlt oder unplausibel ist [G: altes Reet, grau-braun]. */
    public double[] reet = {0.115, 0.100, 0.080};
    /** Bereich der Helligkeit Y für Reet aus der Messung [G]. */
    public double[] reetHell = {0.04, 0.22};
    /** Albedo der Dachpappe flacher Dächer [G]. */
    public double[] pappe = {0.06, 0.06, 0.065};
    /** Albedo des Backsteins, linear [G: dunkelroter Klinker der Halligen nach docs/img/Hooge1–3] und Streuung je Haus. */
    public double[] wand = {0.15, 0.045, 0.035};
    public double streuung = 0.08;
    /** Fang für OSM-Gebäude ohne Treffer (m) [G]. */
    public double fang = 5;
    /**
     * Zwerchgiebel (F129 A) [G]: je Reethaus einer über der Mitte der längsten Traufe (bevorzugt nach Süden oder Osten, wo
     * auch die Tür ist), wenn die Traufe mindestens {@link #zwerchTraufe} m lang ist, höchstens {@link #zwerchHoch} m über
     * dem Boden liegt und das Dach mindestens 30° steil und hoch genug ist. Breite, Höhe über der Traufe, Neigung.
     */
    public boolean zwerch = true;
    public double zwerchTraufe = 10, zwerchHoch = 4.5, zwerchBreite = 3.2, zwerchWand = 1.4, zwerchNeigung = 50;
    public int zwerchGebaut;
    /** Ausnahmen vom Reet: "keine" (Vorgabe) oder "farbe" (rote Dächer als Ziegel). */
    public String ausnahmen = "keine";

    // Ergebnis
    public Bauten bauten;
    public final StringBuilder bericht = new StringBuilder();
    public int haeuser, verworfen, osmGesamt, osmTreffer, rotNachFarbe, ohneMessung, flachDach;
    public double eckeMax, eckeMittel;
    public final List<String> zeilen = new ArrayList<>();

    static final Pattern GAIN = Pattern.compile("d (linear|sRGB): g = (\\d+[,.]\\d+) / (\\d+[,.]\\d+) / (\\d+[,.]\\d+)");

    /** Die Eichung des Luftbilds aus {@code albedo.txt}: {g_R, g_G, g_B, 1 = sRGB}; null ohne Datei. */
    public static double[] eichung(Path albedo) throws IOException {
        if (!Files.isRegularFile(albedo)) return null;
        String t = Files.readString(albedo, StandardCharsets.UTF_8);
        boolean srgb = t.contains("gilt sRGB");
        Matcher m = GAIN.matcher(t);
        while (m.find()) {
            if (m.group(1).equals(srgb ? "sRGB" : "linear"))
                return new double[] {zahl(m.group(2)), zahl(m.group(3)), zahl(m.group(4)), srgb ? 1 : 0};
        }
        return null;
    }

    static double zahl(String s) { return Double.parseDouble(s.replace(',', '.')); }

    /**
     * Baut die Häuser. {@code gebiet}: Ost0, Nord0, Ost1, Nord1; {@code gel}: Gelände (für Prüfstein 2 und den Sockel);
     * {@code dop}: Kacheln DOP20 (leer: ohne Messung); {@code gain}: Eichung ({@link #eichung}); {@code osm}: null oder
     * die OSM der Szene; (e0, n0): Ursprung des Raums.
     */
    public void bauen(CityGml gml, double[] gebiet, Gelaende gel, List<Path> dop, double[] gain, Osm osm, double e0, double n0) throws IOException {
        // ---- Häuser im Gebiet
        List<CityGml.Haus> l = new ArrayList<>();
        for (CityGml.Haus h : gml.haeuser) {
            double[] m = h.mitte();
            if (m[0] >= gebiet[0] && m[0] < gebiet[2] && m[1] >= gebiet[1] && m[1] < gebiet[3]) l.add(h);
        }
        // ---- OSM: Namen und Prüfstein 1
        Map<CityGml.Haus, String> namen = new HashMap<>();
        if (osm != null) {
            for (Osm.Flaeche f : osm.flaechen) {
                if (!f.tags().containsKey("building")) continue;
                double[][] r = f.ringe().get(0);
                double sx = 0, sy = 0;
                int n = r[0].length - 1;                                  // geschlossener Ring: der letzte Punkt ist der erste
                if (n < 3) continue;
                for (int i = 0; i < n; i++) { sx += r[0][i]; sy += r[1][i]; }
                double mx = sx / n, my = sy / n;
                if (!(mx >= gebiet[0] && mx < gebiet[2] && my >= gebiet[1] && my < gebiet[3])) continue;
                osmGesamt++;
                CityGml.Haus treffer = null;
                double best = fang;
                for (CityGml.Haus h : l) {
                    for (CityGml.Flaeche g : h.flaechen)
                        if (g.art() == CityGml.GRUND && innen(g.ring(), mx, my)) { treffer = h; best = 0; break; }
                    if (best == 0) break;
                    double[] hm = h.mitte();
                    double d = Math.hypot(hm[0] - mx, hm[1] - my);
                    if (d < best) { best = d; treffer = h; }
                }
                if (treffer != null) {
                    osmTreffer++;
                    String nm = f.tags().getOrDefault("name", f.tags().getOrDefault("amenity", ""));
                    if (!nm.isEmpty() && !namen.containsKey(treffer)) namen.put(treffer, nm);
                }
            }
        }
        // ---- DOP20
        List<Dop> kacheln = new ArrayList<>();
        for (Path p : dop) {
            Matcher m = Luftbild.NAME.matcher(p.getFileName().toString());
            if (m.matches()) kacheln.add(new Dop(p, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        }
        Bauten.Bauer bauer = new Bauten.Bauer();
        Gelaende.Abfrage ab = gel != null ? gel.abfrage() : null;
        double sumEcke = 0;
        int nEcke = 0;
        for (CityGml.Haus h : l) {
            double[] hz = h.hoehen();
            double boden = Double.MAX_VALUE, traufe = Double.MAX_VALUE, first = -Double.MAX_VALUE;
            boolean flachAlle = true;
            for (CityGml.Flaeche f : h.flaechen) {
                double[] r = f.ring();
                for (int i = 2; i < r.length; i += 3) {
                    if (f.art() == CityGml.GRUND) boden = Math.min(boden, r[i]);
                    if (f.art() == CityGml.DACH) { traufe = Math.min(traufe, r[i]); first = Math.max(first, r[i]); }
                }
                if (f.art() == CityGml.DACH) {
                    double[] nn = com.dan.core.szene.Zerlegung.normale(r);
                    if (nn != null && Math.toDegrees(Math.acos(Math.min(1, Math.abs(nn[2])))) >= flach) flachAlle = false;
                }
            }
            if (boden == Double.MAX_VALUE) boden = hz[0];
            if (first == -Double.MAX_VALUE) { traufe = hz[1]; first = hz[1]; }
            // Prüfstein 2 und Sockel: Gelände an den Ecken der Grundfläche. LoD2 legt den Boden auf den tiefsten Punkt des
            // Geländes am Haus (Befund P12a: Median 0,26 m unter dem DGM1); Fenster und Tür messen darum vom sichtbaren
            // Gelände (Median der Ecken, nicht unter dem Boden aus LoD2)
            double geMin = boden, eckeHaus = 0;
            List<Double> ecken = new ArrayList<>();
            if (ab != null)
                for (CityGml.Flaeche f : h.flaechen) {
                    if (f.art() != CityGml.GRUND) continue;
                    double[] r = f.ring();
                    for (int i = 0; i < r.length; i += 3) {
                        float g = ab.hoehe(r[i], r[i + 1], 1);
                        if (Float.isNaN(g)) continue;
                        geMin = Math.min(geMin, g);
                        ecken.add(r[i + 2] - (double) g);
                        double d = Math.abs(r[i + 2] - g);
                        eckeHaus = Math.max(eckeHaus, d);
                        sumEcke += d;
                        nEcke++;
                    }
                }
            eckeMax = Math.max(eckeMax, eckeHaus);
            double sockelH = ab != null ? Math.min(boden, geMin) - sockel : boden;     // ohne Gelände kein Sockel
            java.util.Collections.sort(ecken);
            double eckeMedian = ecken.isEmpty() ? 0 : ecken.get(ecken.size() / 2);   // Boden LoD2 − Gelände
            double sicht = boden - Math.min(0, eckeMedian);                           // sichtbares Gelände am Haus
            // Dach: Art und Farbe
            int art = "1000".equals(h.dachform) || flachAlle ? Bauten.FLACH : Bauten.REET;
            double[] gemessen = messen(h, kacheln, gain);
            boolean rot = gemessen != null && gemessen[0] > 1.35 * gemessen[1] && gemessen[0] > 1.35 * gemessen[2];
            if (art == Bauten.REET && rot) {
                rotNachFarbe++;
                if (ausnahmen.equals("farbe")) art = Bauten.ZIEGEL;
            }
            if (art == Bauten.FLACH) flachDach++;
            double[] dach;
            if (art == Bauten.FLACH) dach = pappe;
            else if (art == Bauten.ZIEGEL) dach = gemessen;
            else {
                double y = gemessen == null ? Double.NaN : Luftbild.Y[0] * gemessen[0] + Luftbild.Y[1] * gemessen[1] + Luftbild.Y[2] * gemessen[2];
                if (gemessen == null || rot || !(y >= reetHell[0] && y <= reetHell[1])) {
                    dach = reet;
                    ohneMessung++;
                } else dach = gemessen;
            }
            // Wand: Backstein mit Streuung je Haus (fest aus der Kennung)
            double s = 1 + streuung * (2 * zufall(h.id) - 1);
            Bauten.Gebaeude g = bauer.neu();
            g.boden = (float) sicht;
            g.traufe = (float) traufe;
            g.first = (float) first;
            g.dach = art;
            for (int c = 0; c < 3; c++) {
                g.dachAlbedo[c] = (float) dach[c];
                g.wandAlbedo[c] = (float) (wand[c] * s);
                g.dop[c] = gemessen == null ? Float.NaN : (float) gemessen[c];
            }
            g.kennung = h.id;
            g.name = namen.getOrDefault(h, "");
            double[] m = h.mitte();
            double mx = m[0] - e0, mz = n0 - m[1];
            double fl = 0;
            for (CityGml.Flaeche f : h.flaechen) {
                if (f.art() == CityGml.GRUND) fl += com.dan.core.szene.Zerlegung.flaeche(f.ring());
                if (f.art() != CityGml.WAND && f.art() != CityGml.DACH && f.art() != CityGml.SONST) continue;
                double[] r = f.ring(), q = new double[r.length];
                for (int i = 0; i < r.length; i += 3) {
                    q[i] = r[i] - e0;
                    q[i + 1] = r[i + 2];
                    q[i + 2] = n0 - r[i + 1];
                    if (f.art() == CityGml.WAND && r[i + 2] < boden + 0.01) q[i + 1] = sockelH;
                }
                bauer.flaeche(f.art() == CityGml.DACH ? Bauten.DACH : Bauten.WAND, q, mx, mz);
            }
            g.flaeche = (float) fl;
            if (zwerch && art == Bauten.REET && zwerch(bauer, h, sicht, traufe, first, e0, n0, mx, mz)) zwerchGebaut++;
            bauer.fertig();
            zeilen.add(String.format(Locale.ROOT, "%s\t%.1f\t%.2f\t%.2f\t%.2f\t%.2f\t%s\t%s\t%.3f %.3f %.3f\t%s\t%s\t%.2f", h.id, fl, boden, traufe, first, eckeHaus,
                    h.dachform.isEmpty() ? "-" : h.dachform, Bauten.DACHART[art], dach[0], dach[1], dach[2],
                    gemessen == null ? "-" : String.format(Locale.ROOT, "%.3f %.3f %.3f", gemessen[0], gemessen[1], gemessen[2]), g.name, eckeMedian));
        }
        verworfen = bauer.verworfen;
        bauten = bauer.bauten(e0, n0);
        haeuser = bauten.gebaeude.size();
        eckeMittel = nEcke > 0 ? sumEcke / nEcke : Double.NaN;
        bericht.append(String.format(Locale.ROOT, "%d Häuser aus %d Dateien (%d im Gebiet gelesen), %d Dreiecke, %d Flächen verworfen, %d innere Ringe weggelassen; "
                        + "OSM: %d von %d Gebäuden getroffen; Ecken gegen Gelände: Mittel %.2f m, größte %.2f m; Dächer: %d flach, %d Reet ohne Messung (Vorgabe), "
                        + "%d der Farbe nach Ziegel (%s); %d Zwerchgiebel", haeuser, gml.dateien, l.size(), bauten.dreiecke(), verworfen, gml.innereRinge, osmTreffer, osmGesamt,
                eckeMittel, eckeMax, flachDach, ohneMessung, rotNachFarbe, ausnahmen.equals("farbe") ? "als Ziegel geführt" : "als Reet geführt, Daniel 19.11.", zwerchGebaut));
    }

    /** Sucht die Traufe für den Zwerchgiebel und baut ihn (F129 A); false, wenn das Haus keine passende hat. */
    boolean zwerch(Bauten.Bauer bauer, CityGml.Haus h, double boden, double traufe, double first, double e0, double n0, double mx, double mz) {
        double[] best = null;
        double bestWert = -1;
        for (CityGml.Flaeche f : h.flaechen) {
            if (f.art() != CityGml.DACH) continue;
            double[] r = f.ring(), n = com.dan.core.szene.Zerlegung.normale(r);
            if (n == null) continue;
            if (n[2] < 0) n = new double[] {-n[0], -n[1], -n[2]};
            double neig = Math.toDegrees(Math.acos(Math.min(1, n[2])));
            if (neig < 30) continue;
            double zmin = Double.MAX_VALUE;
            for (int i = 2; i < r.length; i += 3) zmin = Math.min(zmin, r[i]);
            int k = r.length / 3;
            for (int i = 0; i < k; i++) {
                int j = (i + 1) % k;
                if (Math.abs(r[3 * i + 2] - zmin) > 0.15 || Math.abs(r[3 * j + 2] - zmin) > 0.15) continue;
                double dx = r[3 * j] - r[3 * i], dy = r[3 * j + 1] - r[3 * i + 1], len = Math.hypot(dx, dy);
                if (len < zwerchTraufe || zmin - boden > zwerchHoch) continue;
                // außen: waagerechter Teil der Normale (Ost, Nord); Süd oder Ost bevorzugt
                double hl = Math.hypot(n[0], n[1]), ox = n[0] / hl, oy = n[1] / hl;
                boolean suedOst = oy < -0.5 || ox > 0.5;
                double wert = len + (suedOst ? 1000 : 0);
                if (wert > bestWert) {
                    bestWert = wert;
                    best = new double[] {0.5 * (r[3 * i] + r[3 * j]), 0.5 * (r[3 * i + 1] + r[3 * j + 1]), dx / len, dy / len, ox, oy, zmin, neig};
                }
            }
        }
        if (best == null) return false;
        double hoehe = zwerchWand + 0.5 * zwerchBreite * Math.tan(Math.toRadians(zwerchNeigung));
        if (first - best[6] < hoehe + 0.5) return false;                 // das Hauptdach muss über dem Zwerchgiebel liegen
        // Raum: x = Ost − e0, z = n0 − Nord (Richtungen: z dreht das Vorzeichen von Nord)
        bauer.zwerchgiebel(best[0] - e0, n0 - best[1], best[2], -best[3], best[4], -best[5], best[6], zwerchBreite, zwerchWand, zwerchNeigung, best[7], mx, mz);
        return true;
    }

    /** Schreibt {@code ordner/gebaeude.bin} und {@code ordner/gebaeude.txt}. */
    public void schreibe(File ordner, String kopf) throws IOException {
        bauten.schreibe(ordner);
        StringBuilder b = new StringBuilder();
        b.append("# Häuser aus LoD2 (Werkbuch HalligHooge Teil A, 12.13, Schritt gebaeude; F125 A, F126 A)\n# ").append(kopf).append("\n# ").append(bericht).append('\n');
        b.append("# Kennung\tGrundfläche m²\tBoden\tTraufe\tFirst (m NHN)\tEcke gegen Gelände (m)\tDachform LoD2\tDach\tAlbedo Dach R G B\tDOP R G B (geeicht)\tName\tBoden LoD2 − Gelände, Median der Ecken (m)\n");
        for (String z : zeilen) b.append(z).append('\n');
        Files.writeString(new File(ordner, "gebaeude.txt").toPath(), b.toString(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ DOP20

    record Dop(Path pfad, int ostKm, int nordKm) { }

    /** Mittel der geeichten DOP-Farbe über den Dachflächen; null ohne Pixel. */
    double[] messen(CityGml.Haus h, List<Dop> kacheln, double[] gain) throws IOException {
        if (kacheln.isEmpty() || gain == null) return null;
        double[] sum = new double[3];
        long n = 0;
        for (CityGml.Flaeche f : h.flaechen) {
            if (f.art() != CityGml.DACH) continue;
            double[] r = f.ring();
            double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
            for (int i = 0; i < r.length; i += 3) {
                x0 = Math.min(x0, r[i]); x1 = Math.max(x1, r[i]);
                y0 = Math.min(y0, r[i + 1]); y1 = Math.max(y1, r[i + 1]);
            }
            for (Dop d : kacheln) {
                double ke = d.ostKm * 1000.0, kn = d.nordKm * 1000.0;
                if (x1 <= ke || x0 >= ke + 1000 || y1 <= kn || y0 >= kn + 1000) continue;
                int c0 = (int) Math.max(0, Math.floor((x0 - ke) / 0.2)), c1 = (int) Math.min(4999, Math.floor((x1 - ke) / 0.2));
                int z0 = (int) Math.max(0, Math.floor((kn + 1000 - y1) / 0.2)), z1 = (int) Math.min(4999, Math.floor((kn + 1000 - y0) / 0.2));
                if (c1 < c0 || z1 < z0) continue;
                int w = c1 - c0 + 1, hh = z1 - z0 + 1;
                float[][] b = new float[3][];
                try (GeoTiff g = GeoTiff.oeffne(d.pfad)) {
                    for (int c = 0; c < 3; c++) b[c] = g.lies(c0, z0, w, hh, c);
                }
                for (int zz = 0; zz < hh; zz++)
                    for (int cc = 0; cc < w; cc++) {
                        double px = ke + (c0 + cc + 0.5) * 0.2, py = kn + 1000 - (z0 + zz + 0.5) * 0.2;
                        if (!innen(r, px, py) || randAbstand(r, px, py) < rand) continue;
                        float v0 = b[0][zz * w + cc], v1 = b[1][zz * w + cc], v2 = b[2][zz * w + cc];
                        if (Float.isNaN(v0) || Float.isNaN(v1) || Float.isNaN(v2)) continue;
                        double[] v = {v0 / 255.0, v1 / 255.0, v2 / 255.0};
                        for (int c = 0; c < 3; c++) sum[c] += gain[c] * (gain[3] > 0.5 ? Luftbild.srgb(v[c]) : v[c]);
                        n++;
                    }
            }
        }
        if (n < 20) return null;
        return new double[] {sum[0] / n, sum[1] / n, sum[2] / n};
    }

    /** Liegt (x, y) im Ring (Ost, Nord, Höhe je Punkt), in der Ebene Ost–Nord? */
    static boolean innen(double[] r, double x, double y) {
        boolean in = false;
        int n = r.length / 3;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = r[3 * i], yi = r[3 * i + 1], xj = r[3 * j], yj = r[3 * j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    static double randAbstand(double[] r, double x, double y) {
        double d = Double.MAX_VALUE;
        int n = r.length / 3;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = r[3 * j], ay = r[3 * j + 1], bx = r[3 * i], by = r[3 * i + 1];
            double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
            double t = l2 > 0 ? Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / l2)) : 0;
            d = Math.min(d, Math.hypot(x - ax - t * dx, y - ay - t * dy));
        }
        return d;
    }

    /** Fester Zufall 0 … 1 aus einem Text. */
    static double zufall(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h >>> 11) / (double) (1L << 53);
    }
}
