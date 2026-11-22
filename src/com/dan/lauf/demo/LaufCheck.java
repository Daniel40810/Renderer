package com.dan.lauf.demo;

import static com.dan.core.pruef.Attest.f;

import com.dan.core.astro.Gestirne;
import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.bus.Verdrahtungsfehler;
import com.dan.core.feld.Eintrag;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Tidenfeld;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.pruef.Attest;
import com.dan.core.pruef.Pegel;
import com.dan.core.raum.Erde;
import com.dan.core.raum.Utm;
import com.dan.lauf.Lauf;
import com.dan.physics.antrieb.Antrieb;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.tide.Tide;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Prüfung von P5c (Werkbuch HalligHooge Teil A, 12.6, Plan P5c): {@code antrieb}, {@code tide} und {@code hydro} am
 * Bus, verdrahtet von {@link Lauf}. Ohne Fenster; die Grenzen standen vor der Messung fest, [G] heißt gesetzt.
 * <pre>
 * java -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.LaufCheck --gebiet D:\Daten\HalligHooge\gebiet [--karte] [--kette] [--stunden 6] [--start 2026-10-25T12:00:00Z]
 * </pre>
 * {@code --karte}: hydro auf der Karte (sonst die Referenz auf dem Prozessor), dazu die Referenz über eine Stunde zum
 * Vergleich. {@code --kette}: dazu ein Lauf mit G2 ↔ G3 ↔ G4 über eine Stunde.
 */
public final class LaufCheck {
    static final Attest A = new Attest("com.dan.lauf", Lauf.VERSION);

    // Grenzen [G], vor der Messung festgelegt (Werkbuch 12.6, Plan P5c)
    static final double GRENZE_BC = 1e-6, GRENZE_EQ = 1e-9, GRENZE_STAENDIG = 1e-3, GRENZE_P = 0.01, GRENZE_WIND = 1e-5, GRENZE_DREHUNG = 1e-9, GRENZE_BILANZ = 1e-5,
            GRENZE_Q = 1e-5;
    /** Umkreis um einen Pegel, in dem die tiefste nasse Zelle gilt (m) [G]. */
    static final double UMKREIS_PEGEL = 1000;
    static final String[] PEGEL = {"HELGOLAND_BINNENHAFEN", "BUESUM", "HUSUM", "PELLWORM_ANLEGER", "WITTDUEN", "HOERNUM", "DAGEBUELL", "LIST_AUF_SYLT"};

    public static void main(String[] args) {
        List<String> a = List.of(args);
        int gi = a.indexOf("--gebiet");
        String gebiet = gi >= 0 && gi + 1 < a.size() ? a.get(gi + 1) : null;
        boolean karte = a.contains("--karte"), kette = a.contains("--kette");
        int si = a.indexOf("--stunden"), ti = a.indexOf("--start");
        double stunden = si >= 0 ? Double.parseDouble(a.get(si + 1)) : 6;
        Instant start = Instant.parse(ti >= 0 ? a.get(ti + 1) : "2026-10-25T12:00:00Z");
        System.out.println("LaufCheck – com.dan.lauf " + Lauf.VERSION + " (Werkbuch HalligHooge Teil A, P5c)" + (karte ? ", hydro auf der Karte" : ", hydro als Referenz"));
        if (gebiet == null) {
            A.pruefe(false, "Gebiet", "--gebiet fehlt", "Ordner des Gebiets");
        } else {
            try {
                new LaufCheck(new File(gebiet), karte, kette, stunden, start).alles();
            } catch (Throwable e) {
                A.fehler(e);
            }
        }
        if (!a.contains("--ohne-attest")) {
            Path p = A.schreibe(Path.of("docs", "status", karte ? "P5_LaufKarte.md" : "P5_Lauf.md"), karte ? "P5c – LaufCheck auf der Karte" : "P5c – LaufCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Aufbau

    final File gebiet;
    final boolean karte, kette;
    final double stunden;
    final Instant start;
    /** Leser der Prüfung am Bus: tide.* und water.* (nur sie geben Hinweise; das Werkbuch nennt sie nicht). */
    final Leser hy = new Leser("hydro.pruefung"), co = new Leser("core.pruefung");

    LaufCheck(File gebiet, boolean karte, boolean kette, double stunden, Instant start) {
        this.gebiet = gebiet;
        this.karte = karte;
        this.kette = kette;
        this.stunden = stunden;
        this.start = start;
    }

    Lauf.Einstellung einstellung() {
        Lauf.Einstellung e = new Lauf.Einstellung();
        e.gebiet = gebiet;
        e.start = start;
        e.karte = karte;
        e.schub = Antrieb.Schub.WU;          // Teile 1–8 prüfen den Weg mit dem Wind in hydro (Prüfstein 6); die Wege mit Schub prüft Teil 9
        return e;
    }

    /** Ein Modul, das nur liest (ohne Takt). */
    static final class Leser implements Modul {
        final String name;
        final List<Object[]> lesen = new ArrayList<>();
        Leser(String name) { this.name = name; }
        Leser liest(String feld, String einheit, Ort ort, Gitter g) {
            lesen.add(new Object[] {feld, einheit, ort, g});
            return this;
        }
        @Override public String name() { return name; }
        @Override public void anmelden(Anmeldung a) {
            a.ohneTakt();
            for (Object[] l : lesen) a.liest((String) l[0], (String) l[1], (Ort) l[2], (Gitter) l[3]);
        }
        @Override public void beginn(double t0, Bus bus) { }
        @Override public void schritt(double t0, double t1, Bus bus) { }
    }

    void alles() throws Exception {
        Lauf.Einstellung e = einstellung();
        // Gitter für die Leser der Prüfung aus dem Gebiet
        com.dan.core.gitter.Gebiet ge = com.dan.core.gitter.Gebiet.lies(gebiet);
        Gitter g0 = ge.gitter("G0"), g1 = ge.gitter("G1"), g2 = ge.gitter("G2");
        hy.liest("tide.bc", "m, m²/s", Ort.RAND, g0).liest("tide.eq", "m", Ort.ZELLE, g0).liest("tide.eq", "m", Ort.ZELLE, g1);
        co.liest("water.q", "m²/s", Ort.KANTE, g0).liest("water.q", "m²/s", Ort.KANTE, g1).liest("water.eta", "m NHN", Ort.ZELLE, g2);
        e.weitere = List.of(hy, co);
        A.teil("1 Katalog und Verdrahtung");
        Katalog k = Katalog.grundstufe();
        Eintrag eq = k.eintrag("tide.eq"), p = k.eintrag("air.p");
        A.pruefe(k.namen().size() == 42 && eq != null && eq.einheit().equals("m") && eq.ort() == Ort.ZELLE && eq.darfSchreiben("tide") && eq.alsLeserGenannt("hydro")
                        && p.alsLeserGenannt("tide") && k.mehrfachGeschrieben().isEmpty(),
                "Katalog: tide.eq (m, Zelle, schreibt tide, liest hydro), air.p auch von tide gelesen, je Feld ein Schreiber (seit P5e mit air.taux, air.tauy)", f("%d Felder; tide.eq %s", k.namen().size(), eq),
                "42 Felder");
        try (Lauf l = Lauf.bauen(e)) {
            List<String> eigen = new ArrayList<>();
            for (String h : l.hinweise) if (!h.startsWith(hy.name + " ") && !h.startsWith(co.name + " ")) eigen.add(h);
            StringBuilder fg = new StringBuilder();
            for (String n : l.bus.felder()) {
                List<String> gs = new ArrayList<>();
                for (Gitter g : l.bus.gitter(n)) gs.add(g == null ? "–" : g.name);
                fg.append(fg.length() > 0 ? "; " : "").append(n).append(" auf ").append(String.join(", ", gs));
            }
            A.pruefe(eigen.isEmpty() && l.bus.felder().size() == 11, f("Lauf %s mit antrieb, tide, hydro verdrahtet (%s)", l.e.stufenText(), l.rechnung.geraet()),
                    f("keine Mängel, %d Hinweise; %d Felder: %s", eigen.size(), l.bus.felder().size(), fg), "keine Mängel, keine Hinweise");
            A.info("Aufbau", f("%.1f s (Gebiet lesen, Gitter, Einrichten, Verdrahten, Anfangsstand; der ständige Anteil der Gleichgewichtstide aus 8 760 Stunden)", l.aufbau));
            A.info("tide", l.tide.toString());
            gegenproben(l);
            new Hauptlauf(l).rechnen();
        }
        tideAllein();
        if (kette) kette();
        schub();
    }

    /** Grenze für den Windschub in hydro gegen die eigene Rechnung (N/m²) [G]. */
    static final double GRENZE_SCHUB = 1e-8;

    /**
     * Teil 9 (P5e, F34): Windschub vom Bus. Je Art (ERA5, Charnock) G0 | G1 | G2 über zwei Intervalle nach dem Anlauf:
     * verdrahtet, hydro liest air.taux und air.tauy statt des Windes; die Kraft an der Oberfläche in hydro auf G0 und G2
     * gegen eine eigene Rechnung an den Punkten von ERA5 (Charnock mit Bisektion statt Einsetzen), bilinear an den
     * Zellmitten, in UTM gedreht. Dazu die Gegenprobe: ein Wetterfeld ohne die Größen wird abgelehnt.
     */
    void schub() throws Exception {
        A.teil("9 Windschub vom Bus (P5e)");
        Wetterfeld wetter = Wetterfeld.lies(gebiet);
        if (wetter.groesse("iews") < 0 || wetter.groesse("charnock") < 0) {
            A.pruefe(false, "Wetterfeld mit iews, inss, charnock (Aufbereitung mit antrieb.schub)", "Größen " + wetter.groessen, "vorhanden");
            return;
        }
        for (Antrieb.Schub art : new Antrieb.Schub[] {Antrieb.Schub.ERA5, Antrieb.Schub.CHARNOCK}) {
            Lauf.Einstellung e = einstellung();
            e.schub = art;
            e.anlauf = 300;                                    // zwei Intervalle nach dem Anlauf, r = 1
            try (Lauf l = Lauf.bauen(e)) {
                boolean liest = l.bus.felder().contains("air.taux") && l.bus.felder().contains("air.tauy");
                l.bis(3 * e.kopplung);
                double tm = l.zeit() - 0.5 * e.kopplung;
                double[] m0 = schubFehler(l, wetter, l.hydro.gitter.get(0), tm, art), m2 = schubFehler(l, wetter, l.hydro.gitter.get(2), tm, art);
                double b = 0;
                for (int s = 0; s < l.hydro.stufen.size(); s++) b = Math.max(b, l.bilanz(s));
                A.pruefe(liest && l.hinweise.isEmpty() && m0[0] <= GRENZE_SCHUB && m2[0] <= GRENZE_SCHUB && b < GRENZE_BILANZ,
                        f("Windschub %s: verdrahtet (hydro liest air.taux, air.tauy), Kraft an der Oberfläche in hydro auf G0 (geographisch) und G2 (UTM, gedreht) gegen die eigene Rechnung, Bilanz über 15 min", Lauf.schubText(art)),
                        f("Hinweise %s; |Δτ| G0 bis %.1e, G2 bis %.1e N/m² (τ bis %.2f bzw. %.2f N/m²); Bilanz %.1e", l.hinweise, m0[0], m2[0], m0[1], m2[1], b),
                        f("keine Hinweise; ≤ %.0e N/m² [G]; < 1e-5", GRENZE_SCHUB));
            }
        }
        // Gegenprobe: Wetterfeld ohne Schub
        File t = java.nio.file.Files.createTempDirectory("schub").toFile();
        Gradraster r = new Gradraster(5, 53, 0.25, 0.25, 4, 4);
        Wetterfeld.schreibe(t, r, start, 3600, 2, List.of("u10", "v10", "msl"), (z, q) -> new float[r.punkte()], null);
        int abgelehnt = 0;
        for (Antrieb.Schub art : new Antrieb.Schub[] {Antrieb.Schub.ERA5, Antrieb.Schub.CHARNOCK}) {
            Antrieb an = new Antrieb(Wetterfeld.lies(t), start);
            an.schub = art;
            try {
                new Bus(Katalog.grundstufe()).anmelden(an);
            } catch (IllegalArgumentException x) {
                abgelehnt++;
            }
        }
        A.pruefe(abgelehnt == 2, "Gegenprobe: Windschub aus ERA5 oder nach Charnock mit einem Wetterfeld ohne iews, inss, charnock", abgelehnt + " von 2 abgelehnt", "beide");
    }

    /** C_d nach Charnock mit zähem Anteil, unabhängig gelöst: Bisektion über u* (zur Prüfung von {@link Antrieb#cdCharnock}). */
    static double cdBisektion(double u, double alpha) {
        if (!(u > 0)) return 0;
        double a = 1e-6, b = u;                               // f(u*) = u* ln(10/z0(u*)) − κ U, steigt in u*
        for (int k = 0; k < 200; k++) {
            double m = 0.5 * (a + b), z0 = alpha * m * m / Antrieb.G + 0.11 * Antrieb.NU / m;
            if (m * Math.log(10 / z0) - Antrieb.KARMAN * u > 0) b = m; else a = m;
        }
        double us = 0.5 * (a + b);
        return us * us / (u * u);
    }

    /** {größter Fehler, größtes τ} der Kraft an der Oberfläche in hydro zur Mitte des letzten Intervalls. */
    double[] schubFehler(Lauf l, Wetterfeld w, Hydro h, double tm, Antrieb.Schub art) {
        double tau = l.e.kopplung;
        Instant za = l.zeitpunkt(tm - 0.5 * tau), zb = l.zeitpunkt(tm + 0.5 * tau);
        Gradraster r = w.raster;
        double[][] punkte = new double[2][];
        for (int s = 0; s < 2; s++) {
            Instant z = s == 0 ? za : zb;
            double k = (z.getEpochSecond() - w.start.getEpochSecond()) / (double) w.schritt;
            int k0 = (int) Math.floor(k);
            double q = k - k0;
            double[] t = new double[2 * r.punkte()];
            float[][] f0 = new float[w.groessen.size()][], f1 = new float[w.groessen.size()][];
            for (int g = 0; g < w.groessen.size(); g++) { f0[g] = w.feld(k0, g); f1[g] = w.feld(Math.min(w.zeiten - 1, k0 + 1), g); }
            for (int pp = 0; pp < r.punkte(); pp++) {
                final int p = pp;
                java.util.function.IntToDoubleFunction v = g -> (1 - q) * f0[g][p] + q * f1[g][p];
                if (art == Antrieb.Schub.ERA5) {
                    t[2 * p] = v.applyAsDouble(w.groesse("iews"));
                    t[2 * p + 1] = v.applyAsDouble(w.groesse("inss"));
                } else {
                    double ux = v.applyAsDouble(w.groesse("u10")), uy = v.applyAsDouble(w.groesse("v10")), u = Math.hypot(ux, uy);
                    double c = 1.25 * cdBisektion(u, v.applyAsDouble(w.groesse("charnock"))) * u;
                    t[2 * p] = c * ux;
                    t[2 * p + 1] = c * uy;
                }
            }
            punkte[s] = t;
        }
        Gitter g = h.gitter;
        return java.util.stream.IntStream.range(0, g.ny).parallel().mapToObj(j -> {
            double[] x = new double[2];
            for (int i = 0; i < g.nx; i++) {
                int c = g.index(i, j);
                double[] ll = g.grad(g.x(i), g.y(j));
                Gradraster.Gewichte gw = r.gewichte(ll[0], ll[1], null, 0, 1, 0);
                double tx = 0, ty = 0;
                for (int n = 0; n < gw.index().length; n++)
                    for (int s = 0; s < 2; s++) {
                        tx += 0.5 * gw.gewicht()[n] * punkte[s][2 * gw.index()[n]];
                        ty += 0.5 * gw.gewicht()[n] * punkte[s][2 * gw.index()[n] + 1];
                    }
                double sx = tx, sy = ty;
                if (g.lage == Gitter.Lage.UTM) {
                    double gam = Math.toRadians(Utm.faktoren(ll[0], ll[1], g.zone)[0]);
                    sx = tx * Math.cos(gam) - ty * Math.sin(gam);
                    sy = ty * Math.cos(gam) + tx * Math.sin(gam);
                }
                x[0] = Math.max(x[0], Math.max(Math.abs(h.kraftX[c] - sx), Math.abs(h.kraftY[c] - sy)));
                x[1] = Math.max(x[1], Math.hypot(tx, ty));
            }
            return x;
        }).reduce(new double[2], (x, y) -> new double[] {Math.max(x[0], y[0]), Math.max(x[1], y[1])});
    }

    /** Hydro ohne tide, hydro mit Antrieb ohne antrieb: es startet nichts. */
    void gegenproben(Lauf l) {
        Katalog k = Katalog.grundstufe();
        String[][] faelle = {{"hydro ohne tide", "hydro liest tide.bc: kein Schreiber angemeldet"}, {"hydro mit Antrieb ohne antrieb", "hydro liest air.u10: kein Schreiber angemeldet"},
                {"tide mit Barometer ohne antrieb", "tide liest air.p: kein Schreiber angemeldet"}};
        Modul[][] module = {{l.antrieb, l.hydro}, {l.tide, l.hydro}, {l.tide, l.hydro}};
        int erkannt = 0;
        for (int w = 0; w < faelle.length; w++) {
            Bus b = new Bus(k);
            for (Modul m : module[w]) b.anmelden(m);
            try {
                b.verdrahten();
                A.befund("Gegenprobe „" + faelle[w][0] + "“ verdrahtet ohne Mangel");
            } catch (Verdrahtungsfehler x) {
                int ww = w;
                if (x.maengel.stream().anyMatch(m -> m.equals(faelle[ww][1]))) erkannt++;
                else A.befund("Gegenprobe „" + faelle[w][0] + "“: " + x.maengel);
            }
        }
        A.pruefe(erkannt == faelle.length, "Gegenproben: hydro ohne tide; hydro mit Antrieb ohne antrieb; tide mit Barometer ohne antrieb – es startet nichts", erkannt + " von " + faelle.length + " erkannt", "alle");
    }

    /** Die Tide allein (für Prüfstein 3 in P5d): ohne antrieb und ohne Barometer. */
    void tideAllein() throws Exception {
        A.teil("7 Tide allein");
        Lauf.Einstellung e = einstellung();
        e.antrieb = false;
        e.karte = false;
        try (Lauf l = Lauf.bauen(e)) {
            boolean ohneLuft = l.bus.felder().stream().noneMatch(n -> n.startsWith("air.")) && l.antrieb == null && !l.tide.druck;
            l.bis(l.e.kopplung);
            A.pruefe(l.hinweise.isEmpty() && ohneLuft && l.bilanz(0) < GRENZE_BILANZ, "Lauf ohne Antrieb (Tide allein) verdrahtet und rechnet ein Intervall",
                    f("Hinweise %s; Felder %s; Bilanz G0 %.1e", l.hinweise, l.bus.felder(), l.bilanz(0)), "keine Hinweise, ohne air.*");
        }
    }

    /** G2 ↔ G3 ↔ G4 in der Kette, eine Stunde. */
    void kette() throws Exception {
        A.teil("8 Lauf mit G2 ↔ G3 ↔ G4");
        Lauf.Einstellung e = einstellung();
        e.stufen("G0 | G1 | G2 G3 G4");
        try (Lauf l = Lauf.bauen(e)) {
            A.pruefe(l.hinweise.isEmpty(), f("Lauf %s verdrahtet (%s)", e.stufenText(), l.rechnung.geraet()), f("Hinweise %s; Aufbau %.1f s", l.hinweise, l.aufbau), "keine Mängel, keine Hinweise");
            long w0 = System.nanoTime();
            l.bis(3600);
            double wand = (System.nanoTime() - w0) / 1e9;
            double b = 0;
            StringBuilder t = new StringBuilder();
            for (int s = 0; s < l.hydro.stufen.size(); s++) {
                b = Math.max(b, l.bilanz(s));
                t.append(f("%sStufe %d %.1e", s > 0 ? ", " : "", s, l.bilanz(s)));
            }
            A.pruefe(b < GRENZE_BILANZ && nan(l) == 0, "Lauf mit G3 und G4 über 1 h: Bilanz je Stufe (Abgeschnittenes zählt als Fehler), kein NaN", t + f("; %d nicht endlich", nan(l)), "< 1e-5; keins");
            A.info("Zeit mit G3 und G4", f("%.0f s für 1 h, Zeitraffer %.1f, %.1f ns je Zellschritt (%.3e Zellschritte)", wand, 3600 / wand, 1e9 * wand / l.rechnung.zellschritte(), (double) l.rechnung.zellschritte()));
        }
    }

    static long nan(Lauf l) {
        long n = 0;
        for (Hydro h : l.hydro.gitter) {
            for (double x : h.eta) if (!Double.isFinite(x)) n++;
            for (double x : h.vol) if (!Double.isFinite(x)) n++;
            for (double x : h.u) if (!Double.isFinite(x)) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ der Hauptlauf

    final class Hauptlauf {
        final Lauf l;
        final Tidenfeld eot;
        final Wetterfeld wetter;
        final Gitter g0;
        final float[] nass0;
        /** Je Randzelle: Konstanten (null an Land), Gewichte des Luftdrucks in der Randzelle. */
        final double[][][] konst;
        final Gradraster.Gewichte[] druck;
        double bcFehler, bcNaN;
        int bcZeiten, bcOffen;
        double eqFehler, eqG2;
        int eqZeiten;
        double[][] eta1h;

        Hauptlauf(Lauf l) throws Exception {
            this.l = l;
            eot = Tidenfeld.lies(gebiet);
            wetter = Wetterfeld.lies(gebiet);
            g0 = l.hydro.gitter.get(0).gitter;
            nass0 = l.gebiet.nass(g0.name);
            int n = g0.randZellen();
            konst = new double[n][][];
            druck = new Gradraster.Gewichte[n];
            for (int r = 0; r < n; r++) {
                // eigene Rechnung des Orts: Süd (i von West), Ost (j von Süd), Nord (i von Ost), West (j von Nord)
                int i, j;
                double lon, lat;
                if (r < g0.nx) { i = r; j = 0; lon = g0.x0 + (i + 0.5) * g0.dx; lat = g0.y0 - 0.5 * g0.dy; }
                else if (r < g0.nx + g0.ny) { i = g0.nx - 1; j = r - g0.nx; lon = g0.x0 + (g0.nx + 0.5) * g0.dx; lat = g0.y0 + (j + 0.5) * g0.dy; }
                else if (r < 2 * g0.nx + g0.ny) { i = g0.nx - 1 - (r - g0.nx - g0.ny); j = g0.ny - 1; lon = g0.x0 + (i + 0.5) * g0.dx; lat = g0.y0 + (g0.ny + 0.5) * g0.dy; }
                else { i = 0; j = g0.ny - 1 - (r - 2 * g0.nx - g0.ny); lon = g0.x0 - 0.5 * g0.dx; lat = g0.y0 + (j + 0.5) * g0.dy; }
                if (!(nass0[i + g0.nx * j] > 0)) continue;
                konst[r] = eot.konstanten(lon, lat, l.tide.umkreis);
                druck[r] = wetter.raster.gewichte(g0.x0 + (i + 0.5) * g0.dx, g0.y0 + (j + 0.5) * g0.dy, null, 0, 1, 0);
                bcOffen++;
            }
        }

        void rechnen() throws Exception {
            Lauf.Einstellung e = l.e;
            double tau = e.kopplung, ende = 3600 * stunden;
            l.takt.mitschreiben = true;
            Hydro h0 = l.hydro.gitter.get(0), h1 = l.hydro.gitter.get(1);
            double[] v0 = null, v1 = null;
            long w0 = System.nanoTime();
            int schritt = 0;
            double stunde = 3600;
            System.out.println(f("         Lauf über %.1f h ab %s; jede Stunde eine Zeile.", stunden, start));
            while (l.zeit() < ende - 1e-6) {
                double t1 = l.zeit() + tau;
                if (t1 >= ende - 1e-6) {
                    v0 = h0.vol.clone();
                    v1 = h1.vol.clone();
                }
                l.bis(t1);
                schritt++;
                tideBc(t1);
                if (schritt % 6 == 1 || t1 >= ende - 1e-6) tideEq(t1);
                if (Math.abs(t1 - 3600) < 1e-6) {
                    eta1h = new double[l.hydro.gitter.size()][];
                    for (int k = 0; k < eta1h.length; k++) eta1h[k] = l.hydro.gitter.get(k).eta.clone();
                }
                if (t1 >= stunde - 1e-6) {
                    stunde += 3600;
                    double wand = (System.nanoTime() - w0) / 1e9;
                    System.out.println(f("         t = %4.1f h: Zeitraffer %6.1f, Bilanz %.1e / %.1e / %.1e, noch rund %.0f s", t1 / 3600, t1 / wand, l.bilanz(0), l.bilanz(1),
                            l.hydro.stufen.size() > 2 ? l.bilanz(2) : 0.0, (ende - t1) * wand / t1));
                }
            }
            double wand = (System.nanoTime() - w0) / 1e9;
            A.teil("2 tide");
            A.pruefe(bcFehler <= GRENZE_BC && bcNaN == 0, f("tide.bc am Bus gegen eine eigene Rechnung (Ort der Geisterzelle, EOT20 mit Knotenfaktoren, Anlauf, umgekehrtes Barometer aus ERA5 in der Randzelle), %d offene Randzellen zu %d Zeiten",
                    bcOffen, bcZeiten), f("|Δη| bis %.1e m; NaN an offenen Randzellen %.0f", bcFehler, bcNaN), "≤ 1e-6 m [G]");
            A.pruefe(eqFehler <= GRENZE_EQ, f("tide.eq am Bus auf G0 und G1 gegen 0,69 · r(t) · (η_eq − η̄) aus den Ephemeriden, alle Zellen zu %d Zeiten", eqZeiten), f("|Δ| bis %.1e m", eqFehler),
                    "≤ 1e-9 m [G]");
            staendig();
            A.info("Gleichgewichtstide über G2 (nicht angewandt, F27 A: nur gemessen)", f("größte Spanne zwischen den Zellen von G2 über den Lauf %.2f mm (Schätzung im Plan: unter 1 mm [S])", 1000 * eqG2));
            A.teil("3 antrieb");
            antrieb(l.hydro.gitter.get(0), l.zeit() - 0.5 * tau);
            antrieb(l.hydro.gitter.get(2), l.zeit() - 0.5 * tau);
            A.teil("4 Takt");
            List<String> pr = l.takt.protokoll;
            boolean ordnung = pr.size() >= 3 && pr.get(0).equals("antrieb 0.0 300.0") && pr.get(1).equals("tide 0.0 300.0") && pr.get(2).equals("hydro 0.0 300.0");
            A.pruefe(ordnung && l.bus.ausserhalb() == 0, "Reihenfolge je Intervall: antrieb, tide, dann hydro, das beide im Intervall liest; keine Lesung außerhalb der gehaltenen Stände",
                    f("Beginn %s; %d Schritte, %d außerhalb", pr.subList(0, Math.min(3, pr.size())), l.takt.schritte(), l.bus.ausserhalb()), "antrieb, tide, hydro; 0");
            A.teil("5 Lauf " + l.e.stufenText());
            double b = 0;
            StringBuilder t = new StringBuilder();
            for (int s = 0; s < l.hydro.stufen.size(); s++) {
                b = Math.max(b, l.bilanz(s));
                double[] bi = l.hydro.bilanz(s);
                t.append(f("%sStufe %d %.1e (abgeschnitten %.0f m³)", s > 0 ? ", " : "", s, l.bilanz(s), bi[4]));
            }
            A.pruefe(b < GRENZE_BILANZ, f("Volumen je Stufe über %.1f h: |V − V₀ − (Zufluss − Abfluss)| / Bezug, Abgeschnittenes zählt als Fehler", stunden), t.toString(), "< 1e-5 (wie Fall 9)");
            long nn = nan(l), nw = 0;
            Feld we = l.bus.lesefeld(co, "water.eta");
            l.bus.lies(co, "water.eta", l.zeit(), we);
            for (float x : we.f) if (!Float.isFinite(x)) nw++;
            A.pruefe(nn == 0 && nw == 0, "kein NaN in η, V, u aller Gitter und in water.eta am Bus (G2)", f("%d und %d nicht endlich", nn, nw), "keins");
            q(h0, v0, tau);
            q(h1, v1, tau);
            pegel();
            A.info("Zeit", f("%.1f h in %.0f s, Zeitraffer %.1f, %.1f ns je Zellschritt (%.3e Zellschritte; mit antrieb, tide und den Prüfungen)", stunden, wand, 3600 * stunden / wand,
                    1e9 * wand / l.rechnung.zellschritte(), (double) l.rechnung.zellschritte()));
            wiederholt();
        }

        /** tide.bc zur Zeit t gegen die eigene Rechnung. */
        void tideBc(double t) {
            Feld bc = l.bus.lesefeld(hy, "tide.bc");
            l.bus.lies(hy, "tide.bc", t, bc);
            Instant z = l.zeitpunkt(t);
            double[][] fk = eot.faktoren(z);
            double r = Tide.anteil(t, l.e.anlauf), rp = Antrieb.anteil(t, l.e.anlauf);
            int ip = wetter.groesse("msl");
            for (int q = 0; q < konst.length; q++) {
                double ist = bc.d[2 * q];
                if (konst[q] == null) {
                    if (!Double.isNaN(ist)) bcFehler = Math.max(bcFehler, Math.abs(ist));
                    continue;
                }
                if (Double.isNaN(ist)) { bcNaN++; continue; }
                double p = wetter.wert(ip, z, druck[q]);
                p = 101_325 + rp * (p - 101_325);
                double soll = l.e.mittel + r * Tidenfeld.eta(konst[q], fk) - (p - 101_325) / (1025 * 9.81);
                bcFehler = Math.max(bcFehler, Math.abs(ist - soll));
            }
            bcZeiten++;
        }

        /** tide.eq zur Zeit t auf G0 und G1 gegen die Ephemeriden; dazu die Spanne über G2. */
        void tideEq(double t) {
            Gestirne.Stand st = Gestirne.bei(l.zeitpunkt(t));
            double fr = l.tide.faktor * Tide.anteil(t, l.e.anlauf);
            for (int k = 0; k < 2; k++) {
                Hydro h = l.hydro.gitter.get(k);
                Feld f = l.bus.lesefeld(hy, "tide.eq", h.gitter);
                l.bus.lies(hy, "tide.eq", t, f);
                Gitter g = h.gitter;
                double m = java.util.stream.IntStream.range(0, g.ny).parallel().mapToDouble(j -> {
                    double x = 0;
                    for (int i = 0; i < g.nx; i++) {
                        double[] ll = g.grad(g.x(i), g.y(j));
                        x = Math.max(x, Math.abs(f.d[g.index(i, j)] - fr * (st.gleichgewicht(ll[0], ll[1]) - l.tide.staendig(ll[1]))));
                    }
                    return x;
                }).max().orElse(0);
                eqFehler = Math.max(eqFehler, m);
            }
            Gitter g2 = l.hydro.gitter.get(2).gitter;
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < g2.ny; j += 5)
                for (int i = 0; i < g2.nx; i += 5) {
                    double[] ll = g2.grad(g2.x(i), g2.y(j));
                    double v = l.tide.faktor * (st.gleichgewicht(ll[0], ll[1]) - l.tide.staendig(ll[1]));
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
            eqG2 = Math.max(eqG2, hi - lo);
            eqZeiten++;
        }

        /**
         * Der ständige Anteil η̄(φ) gegen die geschlossene Form C · P₂(sin φ′), φ′ geozentrisch, mit C aus dem Mittel von
         * Σ GM R² / d³ · P₂(sin δ) über Sonne und Mond (dasselbe Jahr, stündlich), durch die Normschwere.
         */
        void staendig() {
            int n = 365 * 24;
            double c = java.util.stream.IntStream.range(0, n).parallel().mapToDouble(h -> {
                Gestirne.Stand st = Gestirne.bei(start.plusSeconds(3600L * h));
                double sm = Math.sin(st.mondDek), ss = Math.sin(st.sonneDek);
                return Gestirne.GM_MOND * Erde.R * Erde.R / Math.pow(st.mondAbstand, 3) * (1.5 * sm * sm - 0.5)
                        + Gestirne.GM_SONNE * Erde.R * Erde.R / Math.pow(st.sonneAbstand, 3) * (1.5 * ss * ss - 0.5);
            }).sum() / n / Erde.G0;
            double fehler = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (double b = 43.05; b <= 63.95; b += 0.1) {
                double s = Math.sin(Math.toRadians(Erde.geozentrisch(b))), soll = c * (1.5 * s * s - 0.5), ist = l.tide.staendig(b);
                fehler = Math.max(fehler, Math.abs(ist - soll));
                lo = Math.min(lo, ist);
                hi = Math.max(hi, ist);
            }
            A.pruefe(fehler <= GRENZE_STAENDIG, "Ständiger Anteil der Gleichgewichtstide η̄(φ) über 43–64° N gegen C · P₂(sin φ′) aus den Deklinationen desselben Jahres",
                    f("|Δ| bis %.2e m; C = %.4f m, η̄ von %.4f bis %.4f m (mal 0,69: Neigung %.1f cm über G0)", fehler, c, lo, hi, 100 * l.tide.faktor * (hi - lo)), "≤ 1 mm [G]");
        }

        /**
         * Wind und Luftdruck in hydro (zur Mitte des letzten Intervalls) gegen ERA5 an den Zellmitten; in UTM gedreht. Der Bus
         * legt zwischen den Ständen am Anfang und Ende des Intervalls linear: zur Mitte also deren Mittel, auch im Anlauf.
         */
        void antrieb(Hydro h, double tm) {
            Gitter g = h.gitter;
            double tau = l.e.kopplung;
            Instant za = l.zeitpunkt(tm - 0.5 * tau), zb = l.zeitpunkt(tm + 0.5 * tau);
            int iu = wetter.groesse("u10"), iv = wetter.groesse("v10"), ip = wetter.groesse("msl");
            double ra = Antrieb.anteil(tm - 0.5 * tau, l.e.anlauf), rb = Antrieb.anteil(tm + 0.5 * tau, l.e.anlauf);
            double[] m = java.util.stream.IntStream.range(0, g.ny).parallel().mapToObj(j -> {
                double[] x = new double[4];
                for (int i = 0; i < g.nx; i++) {
                    int c = g.index(i, j);
                    double[] ll = g.grad(g.x(i), g.y(j));
                    Gradraster.Gewichte w = wetter.raster.gewichte(ll[0], ll[1], null, 0, 1, 0);
                    double u = 0.5 * (ra * wetter.wert(iu, za, w) + rb * wetter.wert(iu, zb, w)), v = 0.5 * (ra * wetter.wert(iv, za, w) + rb * wetter.wert(iv, zb, w)),
                            p = 101_325 + 0.5 * (ra * (wetter.wert(ip, za, w) - 101_325) + rb * (wetter.wert(ip, zb, w) - 101_325));
                    double ux = u, uy = v;
                    if (g.lage == Gitter.Lage.UTM) {
                        double gam = Math.toRadians(Utm.faktoren(ll[0], ll[1], g.zone)[0]);
                        ux = u * Math.cos(gam) - v * Math.sin(gam);
                        uy = v * Math.cos(gam) + u * Math.sin(gam);
                        x[3] = Math.max(x[3], Math.abs(Math.hypot(h.windX[c], h.windY[c]) - Math.hypot(u, v)));
                    }
                    x[0] = Math.max(x[0], Math.abs(h.druck[c] - p));
                    x[1] = Math.max(x[1], Math.max(Math.abs(h.windX[c] - ux), Math.abs(h.windY[c] - uy)));
                    x[2] = Math.max(x[2], Math.hypot(u, v));
                }
                return x;
            }).reduce(new double[4], (x, y) -> new double[] {Math.max(x[0], y[0]), Math.max(x[1], y[1]), Math.max(x[2], y[2]), Math.max(x[3], y[3])});
            A.pruefe(m[0] <= GRENZE_P && m[1] <= GRENZE_WIND && m[3] <= GRENZE_DREHUNG * Math.max(1, m[2]),
                    f("Luftdruck und Wind in hydro auf %s (%s, vom Bus umgerechnet) zur Mitte des letzten Intervalls gegen ERA5 bilinear an den Zellmitten (Mittel der Stände am Anfang und Ende des Intervalls)%s", g.name, g.lage == Gitter.Lage.UTM ? "UTM" : "geographisch",
                            g.lage == Gitter.Lage.UTM ? ", Wind um die Meridiankonvergenz gedreht" : ""),
                    f("|Δp| bis %.1e Pa, |Δu| bis %.1e m/s (Wind bis %.1f m/s)%s", m[0], m[1], m[2], g.lage == Gitter.Lage.UTM ? f(", Betrag nach dem Drehen bis %.1e m/s", m[3]) : ""),
                    "≤ 0,01 Pa, ≤ 1e-5 m/s [G]");
        }

        /**
         * water.q gegen die Masse: je Zelle Σ q · Länge · τ über das letzte Intervall gegen die Änderung des Volumens,
         * bezogen auf den Umsatz der Zelle (Σ über ihre Kanten und alle Massen |Fluss| · Δt, dazu |ΔV|): Pendelt der Fluss
         * in einer fast trockenen Zelle, ist der Umsatz viel größer als der Saldo, und float rundet relativ zum Umsatz
         * (gefasst nach der ersten Messung auf der Karte; die Grenze blieb).
         */
        void q(Hydro h, double[] vorher, double tau) {
            Feld q = l.bus.lesefeld(co, "water.q", h.gitter);
            l.bus.lies(co, "water.q", l.zeit(), q);
            double schlimmst = 0, umsatz = 0;
            int wo = -1;
            for (int c = 0; c < h.zellen; c++) {
                int i = c % h.nx, j = c / h.nx;
                int[] k = {h.kanteX(i, j), h.kanteX(i + 1, j), h.kanteY(i, j), h.kanteY(i, j + 1)};
                double rein = 0, betrag = 0;
                for (int n = 0; n < 4; n++) {
                    double m = q.f[k[n]] * h.laenge(k[n]) * tau;
                    rein += n % 2 == 0 ? m : -m;
                    betrag += h.qBrutto[k[n]];
                }
                double dv = h.vol[c] - vorher[c], rest = Math.abs(dv - rein), bez = betrag + Math.abs(dv);
                umsatz = Math.max(umsatz, bez);
                if (bez > 0 && rest / bez > schlimmst) { schlimmst = rest / bez; wo = c; }
            }
            A.pruefe(schlimmst <= GRENZE_Q, f("water.q (Zeitmittel, am Bus in float) auf %s gegen die Masse: je Zelle |ΔV − Σ q · Länge · τ| über das letzte Intervall, bezogen auf den Umsatz Σ |Fluss| · Δt ihrer Kanten + |ΔV|", h.gitter.name),
                    f("höchstens %.1e (Zelle %d, %d)", schlimmst, wo < 0 ? -1 : wo % h.nx, wo < 0 ? -1 : wo / h.nx), "≤ 1e-5 [G]");
        }

        /** Pegel zur Auskunft: der Lauf ist noch nicht eingeschwungen; die Prüfung der Tide ist P5d. */
        void pegel() throws Exception {
            if (!Pegel.da(gebiet)) return;
            Map<String, Pegel> alle = Pegel.lies(gebiet);
            Instant z = l.zeitpunkt(l.zeit());
            StringBuilder b = new StringBuilder();
            for (String n : PEGEL) {
                Pegel p = null;
                for (Pegel x : alle.values()) if (Pegel.datei(x.name).equals(n + ".f32")) p = x;   // Namen mit Umlaut, Datei in ASCII
                if (p == null) continue;
                double[] m = modell(p.laenge, p.breite);
                if (m == null) continue;
                b.append(f("%s%s (%s) %+.2f gegen %+.2f m", b.length() > 0 ? "; " : "", p.name, l.hydro.gitter.get((int) m[1]).gitter.name, m[0], p.wert(z)));
            }
            A.info(f("Pegel am Ende (%s), Modell gegen Beobachtung, m NHN", z), b + " – nach " + f("%.1f", stunden) + " h ab Ruhe noch nicht eingeschwungen; die Prüfung der Tide ist P5d");
        }

        /** η der tiefsten nassen Zelle im Umkreis eines Orts im feinsten Gitter, das ihn enthält: {η, Gitter}. */
        double[] modell(double lon, double lat) {
            for (int k = l.hydro.gitter.size() - 1; k >= 0; k--) {
                Hydro h = l.hydro.gitter.get(k);
                Gitter g = h.gitter;
                double[] p = g.punkt(lon, lat);
                if (!g.enthaelt(p[0], p[1])) continue;
                int i0 = g.i(p[0]), j0 = g.j(p[1]), r = g.lage == Gitter.Lage.UTM ? (int) Math.ceil(UMKREIS_PEGEL / g.dx) : 0;
                int best = -1;
                double tief = Double.POSITIVE_INFINITY;
                for (int j = Math.max(0, j0 - r); j <= Math.min(g.ny - 1, j0 + r); j++)
                    for (int i = Math.max(0, i0 - r); i <= Math.min(g.nx - 1, i0 + r); i++) {
                        int c = g.index(i, j);
                        if (h.vol[c] > 0 && h.grund(c) < tief) { tief = h.grund(c); best = c; }
                    }
                if (best >= 0) return new double[] {h.eta[best], k};
            }
            return null;
        }

        /** Derselbe Lauf noch einmal über 1 h: bitgleich; mit --karte dazu die Referenz zur Auskunft. */
        void wiederholt() throws Exception {
            if (eta1h == null) return;
            A.teil("6 Wiederholbar");
            Lauf.Einstellung e = einstellung();
            try (Lauf w = Lauf.bauen(e)) {
                w.bis(3600);
                int ungleich = 0;
                for (int k = 0; k < eta1h.length; k++) if (!Arrays.equals(eta1h[k], w.hydro.gitter.get(k).eta)) ungleich++;
                A.pruefe(ungleich == 0, f("Derselbe Lauf noch einmal über 1 h (%s): η aller Gitter", w.rechnung.geraet()), f("%d von %d Gittern verschieden", ungleich, eta1h.length), "bitgleich");
            }
            if (!karte) return;
            e = einstellung();
            e.karte = false;
            try (Lauf w = Lauf.bauen(e)) {
                long w0 = System.nanoTime();
                w.bis(3600);
                StringBuilder b = new StringBuilder();
                for (int k = 0; k < eta1h.length; k++) {
                    Hydro h = w.hydro.gitter.get(k);
                    double m = 0;
                    for (int c = 0; c < h.zellen; c++) if (h.vol[c] > 0) m = Math.max(m, Math.abs(h.eta[c] - eta1h[k][c]));
                    b.append(f("%s%s %.1e m", k > 0 ? ", " : "", h.gitter.name, m));
                }
                A.info("Referenz (Prozessor) gegen Karte nach 1 h, η in nassen Zellen", b + f(" (Drift in float, ohne Grenze wie Fall 9; Referenz in %.0f s)", (System.nanoTime() - w0) / 1e9));
            }
        }
    }
}
