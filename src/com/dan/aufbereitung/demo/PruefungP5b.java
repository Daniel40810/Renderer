package com.dan.aufbereitung.demo;

import com.dan.aufbereitung.Aufbereitung;
import com.dan.aufbereitung.Eot20;
import com.dan.aufbereitung.Inventar;
import com.dan.aufbereitung.Json;
import com.dan.aufbereitung.Rezept;
import com.dan.core.astro.Partialtide;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Subgrid;
import com.dan.core.gitter.Tidenfeld;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.pruef.Harmonik;
import com.dan.core.pruef.Pegel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.dan.aufbereitung.demo.AufbereitungCheck.A;
import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der Daten aus P5b (Werkbuch HalligHooge Teil A, 12.6, Stand P5b) im aufbereiteten Gebiet: EOT20, Pegel,
 * ERA5 und das erweiterte G2 (F30 A), gegen die Messung im Befund (12.6, Befund 1–6). Teil 12 von {@link GebietCheck},
 * Prüfsteine 19–26.
 * <p>
 * Die Werte des Befunds sind unabhängig von den Lesern der Aufbereitung gewonnen: EOT20 mit numpy aus denselben Dateien
 * (Amplitude und Phase, bilinear über nasse Punkte), die Pegel mit UTide (14 Tiden, kleinste Quadrate).
 */
public final class PruefungP5b {
    /** EOT20 an den Pegeln nach dem Befund: Pegel, Tide, Amplitude (m), Phase (Grad). */
    static final String[][] EOT20 = {
            {"HELGOLAND_BINNENHAFEN", "M2", "1.12506", "310.626"}, {"BÜSUM", "M2", "1.45811", "335.265"}, {"HUSUM", "M2", "1.31931", "337.138"},
            {"PELLWORM_ANLEGER", "M2", "1.31539", "336.631"}, {"WITTDÜN", "M2", "1.12814", "337.034"}, {"HÖRNUM", "M2", "0.95387", "338.149"},
            {"DAGEBÜLL", "M2", "1.23233", "336.828"}, {"LIST_AUF_SYLT", "M2", "0.77526", "354.876"},
            {"HELGOLAND_BINNENHAFEN", "S2", "0.31187", "17.288"}, {"HELGOLAND_BINNENHAFEN", "N2", "0.18924", "281.336"},
            {"HELGOLAND_BINNENHAFEN", "K1", "0.05791", "13.251"}, {"HELGOLAND_BINNENHAFEN", "O1", "0.09403", "236.891"},
            {"WITTDÜN", "S2", "0.32537", "45.684"}, {"WITTDÜN", "N2", "0.18345", "307.559"}, {"WITTDÜN", "K1", "0.06063", "25.066"}, {"WITTDÜN", "O1", "0.09492", "246.324"}};
    /** Grenzen für EOT20 gegen den Befund [G]: Amplitude (m), Phase (Grad). */
    static final double EOT_A = 5e-4, EOT_G = 0.05;
    /** M2-Amphidromien der Nordsee nach dem Befund: Breite, Länge (Grad), Amplitude (m). */
    static final double[][] AMPHIDROMIEN = {{52.500, 2.875, 0.068}, {55.500, 5.375, 0.013}, {58.250, 6.000, 0.009}};
    /** M2 der Pegel nach dem Befund (UTide, 14 Tiden, ganze Reihe): Pegel, Amplitude (m), Phase (Grad). */
    static final String[][] PEGEL_M2 = {{"HELGOLAND_BINNENHAFEN", "1.062", "310.4"}, {"BÜSUM", "1.503", "331.6"}, {"HUSUM", "1.534", "356.3"},
            {"PELLWORM_ANLEGER", "1.397", "347.3"}, {"WITTDÜN", "1.196", "349.3"}, {"HÖRNUM", "0.937", "0.5"}, {"LIST_AUF_SYLT", "0.817", "22.1"}};
    /** Pegel mit langen Lücken: zur Auskunft (Befund 1,372 m, 10,7°). */
    static final String[] PEGEL_LUECKEN = {"DAGEBÜLL", "1.372", "10.7"};
    /** Grenzen der eigenen Analyse gegen UTide (nach der ersten Messung gesetzt, 12.6, Stand P5b): relativ, Grad. */
    static final double PEGEL_A = 0.01, PEGEL_G = 1.0;
    /** MThw und MTnw 2011–2020 nach dem Befund (m NHN). */
    static final String[][] KENNWERTE = {{"WITTDÜN", "1.24", "-1.42"}, {"HUSUM", "1.72", "-1.78"}, {"HELGOLAND_BINNENHAFEN", "1.17", "-1.20"}};

    private PruefungP5b() { }

    /** Teil 12 von {@link GebietCheck}. */
    static void daten(Rezept rz, Path daten, Path ziel, Path tmp) throws Exception {
        if (!Tidenfeld.da(ziel.toFile()) || !Pegel.da(ziel.toFile()) || !Wetterfeld.da(ziel.toFile())) {
            A.pruefe(false, "Prüfsteine 19–26: Daten aus P5b im Gebiet (tide/, pegel/, antrieb/)", "fehlen – Aufbereitung --schritte eot20,pegel,antrieb", "da");
            return;
        }
        Tidenfeld eot = Tidenfeld.lies(ziel.toFile());
        Map<String, Pegel> pegel = Pegel.lies(ziel.toFile());
        Gebiet ge = Gebiet.lies(ziel.toFile());
        eot20(eot, pegel);
        rand(ge, eot, rz);
        pegel(pegel, rz, daten);
        antrieb(ziel, ge);
        g2(ge, pegel);
        ablage(rz, daten, ziel, tmp);
    }

    // ------------------------------------------------------------------ 19–21 EOT20

    static void eot20(Tidenfeld eot, Map<String, Pegel> pegel) {
        double da = 0, dg = 0;
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String[] z : EOT20) {
            Pegel p = pegel.get(z[0]);
            int t = eot.index(Partialtide.nach(z[1]));
            if (p == null || t < 0) {
                b.append(z[0]).append(' ').append(z[1]).append(" fehlt; ");
                da = Double.POSITIVE_INFINITY;
                continue;
            }
            double[] ap = Tidenfeld.amplitudePhase(eot.konstanten(p.laenge, p.breite, 0)[t]);
            double a = Math.abs(ap[0] - Double.parseDouble(z[2])), g = Math.abs(winkel(ap[1] - Double.parseDouble(z[3])));
            da = Math.max(da, a);
            dg = Math.max(dg, g);
            n++;
            if (z[1].equals("M2")) b.append(f("%s %.4f m %.2f°; ", kurz(z[0]), ap[0], ap[1]));
        }
        A.pruefe(da <= EOT_A && dg <= EOT_G, f("Prüfstein 19: EOT20 (HDF5-Leser, Ausschnitt, bilinear über nasse Punkte) gegen den Befund (numpy aus denselben Dateien): M2 an 8 Pegeln, S2, N2, K1, O1 an Helgoland und Wittdün (%d Werte)", n),
                f("|ΔA| bis %.1e m, |ΔG| bis %.3f°; %s", da, dg, b.toString().replaceAll("; $", "")), f("≤ %.1e m, ≤ %.2f° [G]", EOT_A, EOT_G));
        List<double[]> m = Eot20.minima(eot, Partialtide.M2, new double[] {-4, 50.5, 12, 62}, 0.15, 4);
        int gefunden = 0;
        StringBuilder c = new StringBuilder();
        for (double[] a : AMPHIDROMIEN) {
            for (double[] x : m)
                if (Math.abs(x[1] - a[0]) < 1e-6 && Math.abs(x[0] - a[1]) < 1e-6 && Math.abs(x[2] - a[2]) <= 0.001) {
                    gefunden++;
                    c.append(f("%.3f° N %.3f° O %.1f cm (Umlauf %+d); ", x[1], x[0], 100 * x[2], (int) x[3]));
                }
        }
        A.pruefe(gefunden == AMPHIDROMIEN.length, "Prüfstein 20: M2-Amphidromien der Nordsee wie im Befund (örtliches Minimum der Amplitude über ±4 Punkte, unter 15 cm): 52,500° N 2,875° O; 55,500° N 5,375° O; 58,250° N 6,000° O",
                f("%d von 3 an denselben Punkten, Amplitude ±1 mm: %s(%d Minima im Kasten)", gefunden, c, m.size()), "alle drei");
    }

    static void rand(Gebiet ge, Tidenfeld eot, Rezept rz) throws IOException {
        int umkreis = rz.hat("eot20.umkreis") ? (int) rz.zahl("eot20.umkreis") : 2;
        Gitter g = ge.gitter("G0");
        float[] nass = ge.nass("G0");
        int kanten = 0, bil = 0, naechst = 0, ohne = 0;
        for (int s = 0; s < 4; s++) {
            int n = s < 2 ? g.ny : g.nx;
            for (int k = 0; k < n; k++) {
                int i = s == 0 ? 0 : s == 1 ? g.nx - 1 : k, j = s < 2 ? k : (s == 2 ? 0 : g.ny - 1);
                if (!(nass[g.index(i, j)] > 0)) continue;
                double x = s == 0 ? g.x0 - 0.5 * g.dx : s == 1 ? g.x1() + 0.5 * g.dx : g.x(i), y = s < 2 ? g.y(j) : (s == 2 ? g.y0 - 0.5 * g.dy : g.y1() + 0.5 * g.dy);
                double[] ll = g.grad(x, y);
                kanten++;
                Gradraster.Gewichte w = eot.gewichte(ll[0], ll[1], umkreis);
                if (w == null) ohne++;
                else if (w.naechster()) naechst++;
                else bil++;
            }
        }
        A.pruefe(ohne == 0 && kanten > 0, f("Prüfstein 21: Rand von G0 – jede Randkante an einer Zelle mit Wasser hat Werte aus EOT20 an der Mitte ihrer Geisterzelle (bilinear, sonst der nächste nasse Punkt im Umkreis von %d)", umkreis),
                f("%d Kanten: bilinear %d, nächster Punkt %d, ohne %d", kanten, bil, naechst, ohne), "keine ohne [G]");
    }

    // ------------------------------------------------------------------ 22–23 Pegel

    static void pegel(Map<String, Pegel> pegel, Rezept rz, Path daten) throws IOException {
        double ra = 0, rg = 0;
        StringBuilder b = new StringBuilder();
        boolean alle = true;
        for (String[] z : PEGEL_M2) {
            Pegel p = pegel.get(z[0]);
            Harmonik.Tide m2 = p == null ? null : p.analyse.tide("M2");
            if (m2 == null) {
                alle = false;
                b.append(z[0]).append(" fehlt; ");
                continue;
            }
            double a = m2.amplitude() / Double.parseDouble(z[1]) - 1, g = winkel(m2.phaseGrad() - Double.parseDouble(z[2]));
            ra = Math.max(ra, Math.abs(a));
            rg = Math.max(rg, Math.abs(g));
            b.append(f("%s %.3f m %.1f° (%+.1f %%, %+.1f°); ", kurz(z[0]), m2.amplitude(), m2.phaseGrad(), 100 * a, g));
        }
        A.pruefe(alle && ra <= PEGEL_A && rg <= PEGEL_G, "Prüfstein 22: M2 der Pegel aus der eigenen Analyse (Harmonik, Rayleigh 1) gegen den Befund (UTide, 14 Tiden), Pegel ohne lange Lücken",
                b.toString().replaceAll("; $", ""), f("±%.0f %%, ±%.0f° (nach der ersten Messung)", 100 * PEGEL_A, PEGEL_G));
        Pegel d = pegel.get(PEGEL_LUECKEN[0]);
        if (d != null) {
            Harmonik.Tide m2 = d.analyse.tide("M2");
            A.info("Prüfstein 22 daneben: Dagebüll (drei Lücken von 8–11 h)", f("M2 %.3f m %.1f° gegen %s m %s° im Befund; mit L2, MU2, NU2 wie UTide 1,369 m (die Lücken trennen sie von M2 nicht)",
                    m2.amplitude(), m2.phaseGrad(), PEGEL_LUECKEN[1], PEGEL_LUECKEN[2]));
        }
        // 23: Rohdaten, Ausreißer, Kennwerte, Scheitel
        Path wurzel = AufbereitungCheck.pfad(daten, rz.text("pegelonline.ordner", "gezeiten\\pegelonline"));
        long falsch = 0, verglichen = 0;
        for (Pegel p : pegel.values()) {
            Path o = wurzel.resolve(p.name);
            try (var w = Files.list(o)) {
                for (Path x : w.filter(q -> q.getFileName().toString().matches("W_.*\\.json")).toList())
                    for (Object e : Json.feld(Json.lies(x))) {
                        Map<String, Object> m = Json.objekt(e);
                        long t = OffsetDateTime.parse(String.valueOf(m.get("timestamp"))).toInstant().getEpochSecond();
                        int k = (int) ((t - p.start.getEpochSecond()) / p.schritt);
                        float v = (float) (Json.zahl(m.get("value")) / 100 + p.pegelnull);
                        if (Float.isNaN(p.werte[k])) continue;
                        verglichen++;
                        if (Float.compare(p.werte[k], v) != 0) falsch++;
                    }
            }
        }
        A.pruefe(falsch == 0 && verglichen > 0, "Prüfstein 23: jeder nicht verworfene Wert der Reihe gleich Rohwert / 100 + Pegelnull (float32), alle Pegel", f("%d von %d verschieden", falsch, verglichen), "0");
        Pegel dag = pegel.get("DAGEBÜLL");
        int aussen = 0;
        if (dag != null) {
            Path roh = wurzel.resolve(dag.name);
            try (var w = Files.list(roh)) {
                for (Path x : w.filter(q -> q.getFileName().toString().matches("W_.*\\.json")).toList())
                    for (Object e : Json.feld(Json.lies(x))) {
                        double v = Json.zahl(Json.objekt(e).get("value")) / 100 + dag.pegelnull;
                        if (v < -6 || v > 8) aussen++;
                    }
            }
        }
        A.pruefe(aussen == 6, "Prüfstein 23: Ausreißer in Dagebüll außerhalb der Grenzen (Befund 3: 6, bis 10,55 m NHN am 14.10.)", f("%d", aussen), "6");
        double dk = 0;
        StringBuilder k = new StringBuilder();
        for (String[] z : KENNWERTE) {
            Pegel p = pegel.get(z[0]);
            double hw = p == null ? Double.NaN : p.kennwert("MThw"), nw = p == null ? Double.NaN : p.kennwert("MTnw");
            dk = Math.max(dk, Math.max(Math.abs(hw - Double.parseDouble(z[1])), Math.abs(nw - Double.parseDouble(z[2]))));
            if (Double.isNaN(hw) || Double.isNaN(nw)) dk = Double.POSITIVE_INFINITY;
            k.append(f("%s %.3f / %.3f; ", kurz(z[0]), hw, nw));
        }
        A.pruefe(dk <= 0.0051, "Prüfstein 23: MThw und MTnw 2011–2020 in m NHN (Befund 3, auf cm gerundet: Wittdün 1,24 / −1,42, Husum 1,72 / −1,78, Helgoland 1,17 / −1,20)", k.toString().replaceAll("; $", ""), "±0,005 m (Rundung)");
        Pegel h = pegel.get("HUSUM");
        int km = -1;
        if (h != null) for (int q = 0; q < h.werte.length; q++) if (!Float.isNaN(h.werte[q]) && (km < 0 || h.werte[q] > h.werte[km])) km = q;
        Instant tm = km < 0 ? null : h.zeit(km);
        A.pruefe(km >= 0 && Math.abs(h.werte[km] - 3.856) < 0.0015 && tm.equals(Instant.parse("2026-10-25T23:45:00Z")),
                "Prüfstein 23: Scheitel der Sturmflut in Husum (Befund 4: 3,86 m über Pegelnull − 5,00 m am 26.10. 00:45 MEZ; mit Pegelnull −5,004 m: 3,856 m NHN)",
                km < 0 ? "keine Reihe" : f("%.3f m NHN am %s", h.werte[km], tm), "3,856 m NHN um 23:45 UTC");
    }

    // ------------------------------------------------------------------ 24 ERA5

    static void antrieb(Path ziel, Gebiet ge) throws IOException {
        Wetterfeld w = Wetterfeld.lies(ziel.toFile());
        Gitter g = ge.gitter("G0");
        double[] a = g.grad(g.x0 - 0.5 * g.dx, g.y0 - 0.5 * g.dy), b = g.grad(g.x1() + 0.5 * g.dx, g.y1() + 0.5 * g.dy);
        boolean deckt = w.raster.enthaelt(a[0], a[1]) && w.raster.enthaelt(b[0], b[1]);
        Instant s0 = Instant.parse("2026-10-24T00:00:00Z"), s1 = Instant.parse("2026-10-27T23:00:00Z");
        boolean zeit = !w.start.isAfter(s0) && !w.ende().isBefore(s1) && w.schritt == 3600;
        // seit P5e (F34) wahlweise mit iews, inss, charnock (antrieb.schub); dann auch diese plausibel [G]
        boolean schub = w.groessen.equals(com.dan.aufbereitung.Era5.ALLE);
        boolean groessen = w.groessen.equals(List.of("u10", "v10", "msl")) || schub;
        double tmax = 0, amin = Double.POSITIVE_INFINITY, amax = 0;
        long nanSchub = 0;
        if (schub) for (int z = 0; z < w.zeiten; z++) {
            float[] tx = w.feld(z, 3), ty = w.feld(z, 4), al = w.feld(z, 5);
            for (int k = 0; k < tx.length; k++) {
                if (Float.isNaN(tx[k]) || Float.isNaN(ty[k]) || Float.isNaN(al[k])) { nanSchub++; continue; }
                tmax = Math.max(tmax, Math.hypot(tx[k], ty[k]));
                amin = Math.min(amin, al[k]);
                amax = Math.max(amax, al[k]);
            }
        }
        double pmin = Double.POSITIVE_INFINITY, pmax = Double.NEGATIVE_INFINITY, vmax = 0, pNord = Double.POSITIVE_INFINITY;
        long nan = 0;
        Instant tNord = null;
        int[] ort = new int[2];
        for (int z = 0; z < w.zeiten; z++) {
            float[] u = w.feld(z, 0), v = w.feld(z, 1), p = w.feld(z, 2);
            Instant t = w.zeit(z);
            boolean sturm = !t.isBefore(s0) && !t.isAfter(s1);
            for (int k = 0; k < p.length; k++) {
                if (Float.isNaN(u[k]) || Float.isNaN(v[k]) || Float.isNaN(p[k])) { nan++; continue; }
                pmin = Math.min(pmin, p[k]);
                pmax = Math.max(pmax, p[k]);
                vmax = Math.max(vmax, Math.hypot(u[k], v[k]));
                double lo = w.raster.laenge(k % w.raster.nx()), la = w.raster.breite(k / w.raster.nx());
                if (sturm && lo >= -4 && lo <= 10 && la >= 51 && la <= 60 && p[k] < pNord) {
                    pNord = p[k];
                    tNord = t;
                    ort[0] = k % w.raster.nx();
                    ort[1] = k / w.raster.nx();
                }
            }
        }
        boolean plausibel = nan == 0 && pmin > 90000 && pmax < 108000 && vmax < 70 && (!schub || nanSchub == 0 && tmax < 30 && amin >= 0 && amax <= 0.3);
        A.pruefe(deckt && zeit && groessen && plausibel, "Prüfstein 24: ERA5 – stündlich ohne Lücke über 24.–27.10.2026, u10, v10, msl (seit P5e wahlweise dazu iews, inss, charnock), deckt die Geisterzellen von G0; Druck 900–1 080 hPa, Wind unter 70 m/s, Schubspannung unter 30 N/m², Charnock 0–0,3, kein NaN",
                f("%s bis %s zu %d s, %s; %s; Druck %.1f–%.1f hPa, Wind bis %.1f m/s, %d NaN", w.start, w.ende(), w.schritt, w.groessen, w.raster, pmin / 100, pmax / 100, vmax, nan)
                        + (schub ? f("; Schubspannung bis %.2f N/m², Charnock %.4f–%.4f, %d NaN", tmax, amin, amax, nanSchub) : ""), "[G]");
        if (tNord != null) A.info("Prüfstein 24 daneben: tiefster Druck über der Nordsee (51–60° N, 4° W–10° O) am 24.–27.10.2026",
                f("%.1f hPa am %s bei %.2f° N, %.2f° O", pNord / 100, tNord, w.raster.breite(ort[1]), w.raster.laenge(ort[0])));
    }

    // ------------------------------------------------------------------ 25 G2

    static void g2(Gebiet ge, Map<String, Pegel> pegel) throws IOException {
        Gitter g = ge.gitter("G2");
        boolean lage = g.nx == 370 && g.ny == 430 && Math.abs(g.x0 - 428000) < 1e-6 && Math.abs(g.y0 - 6011000) < 1e-6 && Math.abs(g.x1() - 502000) < 1e-6 && Math.abs(g.y1() - 6097000) < 1e-6;
        Subgrid.Packung p = ge.packung("G2");
        StringBuilder b = new StringBuilder();
        boolean nass = true;
        for (String n : new String[] {"HUSUM", "LIST_AUF_SYLT"}) {
            Pegel q = pegel.get(n);
            if (q == null) {
                nass = false;
                b.append(n).append(" fehlt; ");
                continue;
            }
            double[] pt = g.punkt(q.laenge, q.breite);
            if (!g.enthaelt(pt[0], pt[1])) {
                nass = false;
                b.append(f("%s außerhalb (O %.0f, N %.0f); ", kurz(n), pt[0], pt[1]));
                continue;
            }
            double mthw = q.kennwert("MThw"), best = 0;
            int bc = -1;
            int i0 = g.i(pt[0]), j0 = g.j(pt[1]);
            for (int j = Math.max(0, j0 - 1); j <= Math.min(g.ny - 1, j0 + 1); j++)
                for (int i = Math.max(0, i0 - 1); i <= Math.min(g.nx - 1, i0 + 1); i++) {
                    double v = p.volumen(g.index(i, j), mthw);
                    if (v > best) { best = v; bc = g.index(i, j); }
                }
            if (!(best > 0)) nass = false;
            b.append(f("%s in Zelle %d, %d; Wasser bei MThw %.2f m: %.0f m³ (Zelle %d, %d); ", kurz(n), i0, j0, mthw, best, bc % g.nx, bc / g.nx));
        }
        A.pruefe(lage && nass, "Prüfstein 25: G2 nach F30 A (O 428–502 km, N 6 011–6 097 km, 370 × 430 Zellen); Husum und List in G2, Wasser bei MThw in ihrer Zelle oder einer Nachbarin",
                f("%s; %s", g, b.toString().replaceAll("; $", "")), "370 × 430, beide nass");
    }

    // ------------------------------------------------------------------ 26 Ablage

    static void ablage(Rezept rz, Path daten, Path ziel, Path tmp) throws Exception {
        long falsch = 0, n = 0;
        StringBuilder b = new StringBuilder();
        for (String t : new String[] {"tide/eot20.txt", "pegel/pegel.txt", "antrieb/era5.txt"}) {
            Inventar inv = Inventar.lies(ziel.resolve(t));
            for (Inventar.Datei d : inv.dateien) {
                n++;
                Path p = ziel.resolve(d.pfad());
                if (!Files.isRegularFile(p) || Files.size(p) != d.bytes() || !Inventar.sha256(p).equals(d.sha())) { falsch++; b.append(d.pfad()).append(' '); }
            }
            for (Inventar.Quelle q : inv.quellen) {
                n++;
                Path p = daten.resolve(q.pfad());
                if (!Files.isRegularFile(p) || Files.size(p) != q.bytes() || !Inventar.sha256(p).equals(q.sha())) { falsch++; b.append(q.pfad()).append(' '); }
            }
        }
        A.pruefe(falsch == 0 && n > 0, "Prüfstein 26: Ablagen und Quellen in tide/eot20.txt, pegel/pegel.txt, antrieb/era5.txt mit Größe und SHA-256 (nachgerechnet)", f("%d von %d verschieden %s", falsch, n, b).strip(), "0");
        long t0 = System.nanoTime();
        Path neu = Files.createDirectories(tmp.resolve("p5b"));
        Aufbereitung au = new Aufbereitung(rz, daten, neu);
        au.still = true;
        au.eot20();
        au.pegel();
        au.antrieb();
        StringBuilder v = new StringBuilder();
        int gleich = 0, alle = 0;
        for (String d : List.of("tide/eot20.f32", "antrieb/era5.f32")) {
            alle++;
            if (Arrays.equals(Files.readAllBytes(ziel.resolve(d)), Files.readAllBytes(neu.resolve(d)))) gleich++;
            else v.append(d).append(' ');
        }
        for (Pegel p : Pegel.lies(ziel.toFile()).values()) {
            alle++;
            String d = "pegel/" + Pegel.datei(p.name);
            if (Arrays.equals(Files.readAllBytes(ziel.resolve(d)), Files.readAllBytes(neu.resolve(d)))) gleich++;
            else v.append(d).append(' ');
        }
        boolean analyse = Arrays.equals(zeilen(ziel.resolve("pegel/pegel.txt")), zeilen(neu.resolve("pegel/pegel.txt")));
        A.pruefe(gleich == alle && analyse, f("Prüfstein 26: eot20, pegel, antrieb in einem leeren Ordner neu (%.0f s) gegen die Ablage", (System.nanoTime() - t0) / 1e9),
                f("%d von %d Dateien bitgleich%s; Analysen und Kennwerte in pegel.txt %s", gleich, alle, v.length() > 0 ? " (verschieden: " + v.toString().strip() + ")" : "", analyse ? "gleich" : "verschieden"), "bitgleich");
    }

    /** Die Zeilen ohne Kommentar und ohne Inventar (Datum, SHA der neuen Ablage). */
    static String[] zeilen(Path p) throws IOException {
        return Files.readAllLines(p).stream().filter(z -> !z.startsWith("#") && !z.startsWith("quelle") && !z.startsWith("datei")).toArray(String[]::new);
    }

    static double winkel(double d) { return d - 360 * Math.round(d / 360); }

    static String kurz(String n) {
        String s = n.charAt(0) + n.substring(1).toLowerCase().replace("_binnenhafen", "").replace("_anleger", "").replace("_auf_sylt", "");
        return s;
    }

    // ------------------------------------------------------------------ Leser an erzeugten Proben (AufbereitungCheck, Teil 12)

    /** GRIB-Proben, mit ecCodes 2.50 erzeugt (gzip, Base64): msl (Edition 1, Nord → Süd), u10 (Edition 1, Süd → Nord, Bitmap), v10 (Edition 2, über 0°, Schritt 1 h, Bitmap). */
    static final String GRIB = "H4sIADeq9moC/5WPvUoDQRSFv93ZJEtMQJTFxICEYGG5imBhkSjoRmwWK7GSNGIhCD6Ag1iIVTpBm4AIPsZa21iI2AgWPoC1IOu9uzGS0jPM353D3O9EO1vrcO7Asu3Z1F7JabZcn0J2VcC4HMf1nDAMF7NbkxQ8DC+vNHYtzx/MbfIlI9e8b/2N2q2dhDpLbLPAGnucEHPIGTewIooyhstfhuQuZ5j+J4M0VwZByRk6udv34PsTWr6tPsQgLCH79KnR5X54Tv5I0hR32OhRmzv09H+n9FYpzEhBH7tGnyu6FNNxoWbIDHo30WoSHF28h6Z9PcA89UdT1FIrriG1mkvngVbKo7+CgiwT0BZCW83oJVKRU0lEozQQdsuxZIjpSJZmZtAoP9xNEpnZAQAA";
    /** HDF5-Proben, mit h5py 3 erzeugt (gzip, Base64): libver earliest (Superblock 0, Objektköpfe 1, Symboltabelle) und latest (Superblock 3, Objektköpfe 2). */
    static final String H5_ALT = "H4sIADeq9moC/+v0cHHj5ZLiYgABDg4GFgYBBmTwHwp4OFD5MPkEKM0IpTug9AommDgjWE4CKi4INR9dXUiQqytI9X80ALPHgAVCczCMgpEIPFwdA0B0BJSvAKVPMKGqK0pNzAHROYklYH52fm5BYnYJPH1akGgvIwMbRC8jjK8BoZkgAsxQddDkiYUvAdYqqGgPTbsODCbcDCb/oQpZgaIgeSYmiIEc0JzCzFjBiprDIEAIagpIsfmqb1kMDBl0jYdgP38XRrCvIOALG371sNLkBit+dbB8rcA8stK1/QMo/QFK/4DQ+y9A+TD6BZT+ApV/gN8cRmDKQk23kBhjZERNt8zo6VRBHpp2FRjEORjE6xkIpdMX0BTAg+azjkEW0lsOQWknWJ7mwBE2sCKFCR42AmD1AhwwMQHUMGGEhQkHVD0Lw8//OoyYeXXGoE6LANnH/+wMCAAA", H5_NEU = "H4sIADeq9moC/+v0cHHj5ZLiYubgYICB/1CQzgLhG0DFRfuP3PH3cAliYqhgEkJWCANcTAyMDAxs/AxAiqUoNTFnM1QnGx9IiDknsUSfCSoiBBJhz87PLUjMLnFnhiqUZcAPJp1PugJ2gqL5qm9ZyJiBkVGFgYGJiZERZhbU9QwYfBEGRkFFewaIhx0YTLgZTP4DFbECXc/MxQF0GDPjeUaI2gSYxQsYBj0Qc4z5DWPbP4DSH6D0Dwi9/wKUD6NfQOkvUPkHqGaimwMOe0ZgWIsAw5oREdbMyGGrIA8NawUGcQ4G8XoG1LC1hirmgVmygWHIAZ11loUMDFsOQXhbnLCnSRZ4OEFTPQOMZuZhYBTggPEFIOHDCcqDzMCw+/lfh5HhGMMwAbcbvJMB+/ylbmcEAAA=";

    static byte[] probe(String b64) throws IOException {
        try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(b64)))) {
            return in.readAllBytes();
        }
    }

    /** Teil 12 von {@link AufbereitungCheck}: die Leser für P5b an erzeugten Proben. */
    static void leser(Path tmp) throws Exception {
        // HDF5
        double da = 0;
        int namen = 0;
        for (String b : new String[] {H5_ALT, H5_NEU}) {
            com.dan.aufbereitung.Hdf5 h = com.dan.aufbereitung.Hdf5.lies(probe(b));
            if (h.namen().containsAll(List.of("real", "lat", "kompakt"))) namen++;
            double[] r = h.doubles("real"), t = h.doubles("real", 1, 3, 1, 3), l = h.doubles("lat"), k = h.doubles("kompakt");
            for (int j = 0; j < 3; j++) for (int i = 0; i < 4; i++) da = Math.max(da, Math.abs(r[j * 4 + i] - (0.5 * i - 0.25 * j)));
            for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) da = Math.max(da, Math.abs(t[j * 2 + i] - (0.5 * (i + 1) - 0.25 * (j + 1))));
            da = Math.max(da, Math.max(Math.abs(l[0] + 90) + Math.abs(l[2] - 90), Math.abs(k[0] + 7) + Math.abs(k[1] - 300)));
        }
        A.pruefe(namen == 2 && da == 0, "HDF5 (erzeugt): alte und neue Art (Superblock 0 und 3, Objektköpfe 1 und 2), float64 Big Endian zusammenhängend, float32, int16 kompakt, Ausschnitt",
                f("%d von 2 Dateien mit allen Namen, |Δ| %.1e", namen, da), "genau");
        // GRIB
        List<com.dan.aufbereitung.Grib.Feld> fe = new java.util.ArrayList<>();
        com.dan.aufbereitung.Grib.lies(new java.io.ByteArrayInputStream(probe(GRIB)), fe::add);
        double dg = 0;
        int nan = 0;
        boolean kopf = fe.size() == 3 && fe.get(0).name().equals("msl") && fe.get(1).name().equals("u10") && fe.get(2).name().equals("v10")
                && fe.get(0).edition() == 1 && fe.get(2).edition() == 2 && fe.get(0).zeit().equals(Instant.parse("2026-10-25T18:00:00Z"))
                && fe.get(1).zeit().equals(Instant.parse("2026-10-25T19:00:00Z")) && fe.get(2).zeit().equals(Instant.parse("2013-12-06T00:00:00Z"))
                && Math.abs(fe.get(0).raster().laenge0() - 7) < 1e-9 && Math.abs(fe.get(0).raster().breite0() - 54.5) < 1e-9 && Math.abs(fe.get(2).raster().laenge0() + 0.5) < 1e-9;
        if (fe.size() == 3)
            for (int j = 0; j < 3; j++)
                for (int i = 0; i < 4; i++) {
                    float[] w = {fe.get(0).werte()[j * 4 + i], fe.get(1).werte()[j * 4 + i], fe.get(2).werte()[j * 4 + i]};
                    double[] soll = {101000 + 25 * i - 40 * j, i == 2 && j == 1 ? Double.NaN : -5 + 1.5 * i + 0.75 * j, i == 0 && j == 2 ? Double.NaN : 3 - 0.5 * i + 2 * j};
                    for (int q = 0; q < 3; q++) {
                        if (Double.isNaN(soll[q])) { if (Float.isNaN(w[q])) nan++; else dg = Double.POSITIVE_INFINITY; }
                        else dg = Math.max(dg, Math.abs(w[q] - soll[q]));
                    }
                }
        A.pruefe(kopf && nan == 2 && dg <= 0.01, "GRIB (erzeugt): Edition 1 Nord → Süd und Süd → Nord mit Bitmap, Edition 2 über den Nullmeridian mit Vorhersageschritt und Bitmap; Größe, gültige Zeit, Raster, Werte",
                f("%d Felder, Kopf %s, %d fehlende als NaN, |Δ| bis %.4f", fe.size(), kopf ? "richtig" : "falsch", nan, dg), "≤ 0,01 (16 Bit)");
        // PEGELONLINE
        Path o = Files.createDirectories(tmp.resolve("pegelonline").resolve("PROBE"));
        Files.writeString(o.resolve("station.json"), "{\"number\":\"1\",\"shortname\":\"PROBE\",\"longitude\":8.5,\"latitude\":54.5,\"water\":{\"shortname\":\"NORDSEE\"},"
                + "\"timeseries\":[{\"shortname\":\"W\",\"unit\":\"cm\",\"gaugeZero\":{\"unit\":\"m. ü. NHN\",\"value\":-5.004},"
                + "\"characteristicValues\":[{\"shortname\":\"MThw\",\"unit\":\"cm\",\"value\":672.0,\"timespanStart\":\"2011-11-01\",\"timespanEnd\":\"2020-10-31\"},"
                + "{\"shortname\":\"HThw\",\"unit\":\"cm\",\"value\":986.0,\"occurrences\":[\"2013-12-06\"],\"timespanStart\":\"2011-11-01\",\"timespanEnd\":\"2020-10-31\"}]}]}");
        Instant p0 = Instant.parse("2026-10-12T00:00:00Z");
        int n = 15 * 1440;
        for (int datei = 0; datei < 2; datei++) {
            StringBuilder b = new StringBuilder("[");
            for (int k = datei == 0 ? 0 : n / 2; k < (datei == 0 ? n / 2 + 60 : n); k++) {
                if (k >= 5000 && k < 5100) continue;                                        // Lücke
                Instant t = p0.plusSeconds(60L * k);
                double v = 500.4 + 100 * (Partialtide.M2.wert(t, 1.2, 330) + Partialtide.S2.wert(t, 0.35, 20));
                if (k == 7000) v += 80;                                                        // Spitze
                if (k == 9000) v = 1555;                                                       // außerhalb
                b.append(b.length() > 1 ? "," : "").append(String.format(java.util.Locale.ROOT, "{\"timestamp\":\"%s\",\"value\":%.1f}", java.time.OffsetDateTime.ofInstant(t, java.time.ZoneOffset.ofHours(2)), v));
            }
            Files.writeString(o.resolve("W_bis_2026102" + datei + "_30_tage.json"), b.append("]"));
        }
        com.dan.aufbereitung.Pegelonline po = new com.dan.aufbereitung.Pegelonline();
        com.dan.aufbereitung.Pegelonline.Gelesen g = po.lies(o);
        Pegel p = g.pegel();
        Harmonik.Tide m2 = p.analyse.tide("M2");
        double v0 = p.werte[0], soll0 = (float) (Math.round(10 * (500.4 + 100 * (Partialtide.M2.wert(p0, 1.2, 330) + Partialtide.S2.wert(p0, 0.35, 20)))) / 1000.0 - 5.004);
        boolean ok = g.dateien() == 2 && g.doppelt() == 60 && p.luecken == 100 && g.spitzen() == 1 && g.grenzen() == 1 && Float.isNaN(p.werte[7000]) && Float.isNaN(p.werte[9000])
                && Math.abs(v0 - soll0) < 1e-6 && Math.abs(p.kennwert("MThw") - 1.716) < 1e-9 && "2013-12-06".equals(p.kennwerte.get("HThw").datum())
                && Math.abs(m2.amplitude() - 1.2) < 1e-3 && Math.abs(winkel(m2.phaseGrad() - 330)) < 0.05;
        A.pruefe(ok, "PEGELONLINE (erzeugt): zwei Dateien mit 60 Minuten Überlapp, 100 Minuten Lücke, eine Spitze (+0,8 m), ein Wert außerhalb; Pegelnull, Kennwerte, M2 der Analyse",
                f("%d Dateien, %d doppelt, %d Lücken, %d Spitzen, %d außerhalb; MThw %.3f m NHN; M2 %.4f m %.2f°", g.dateien(), g.doppelt(), p.luecken, g.spitzen(), g.grenzen(), p.kennwert("MThw"),
                        m2.amplitude(), m2.phaseGrad()), "1, 1; M2 1,2 m 330° (±1 mm, ±0,05°)");
        // ERA5: Raster verschieden, fehlende Größen und Stunden
        byte[] alle = probe(GRIB);
        int l1 = ((alle[4] & 0xff) << 16) | ((alle[5] & 0xff) << 8) | (alle[6] & 0xff), l2 = ((alle[l1 + 4] & 0xff) << 16) | ((alle[l1 + 5] & 0xff) << 8) | (alle[l1 + 6] & 0xff);
        Path gr = tmp.resolve("probe.grib"), zwei = tmp.resolve("probe_zwei.grib");
        Files.write(gr, alle);
        Files.write(zwei, Arrays.copyOf(alle, l1 + l2));
        String raster;
        try {
            com.dan.aufbereitung.Era5.lies(List.of(gr));
            raster = "nicht gemeldet";
        } catch (IOException e) {
            raster = e.getMessage().contains("statt") ? "gemeldet" : e.getMessage();
        }
        com.dan.aufbereitung.Era5.Daten d = com.dan.aufbereitung.Era5.lies(List.of(zwei));
        String l = com.dan.aufbereitung.Era5.luecken(d);
        A.pruefe(raster.equals("gemeldet") && d.felder.size() == 2 && l.contains("u10 fehlt") && l.contains("msl fehlt"), "ERA5 (erzeugt): verschiedene Raster, fehlende Größen werden gemeldet",
                "Raster " + raster + "; " + l, "gemeldet");
    }
}
