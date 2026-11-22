package com.dan.lauf.demo;

import static com.dan.core.pruef.Attest.f;

import com.dan.core.astro.Partialtide;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Tidenfeld;
import com.dan.core.pruef.Attest;
import com.dan.core.pruef.Harmonik;
import com.dan.core.pruef.Pegel;
import com.dan.lauf.Lauf;
import com.dan.physics.antrieb.Antrieb;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.hydro.Ringe;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prüfung der Tide über die Kette G0 → G1 → G2 (Werkbuch HalligHooge Teil A, 12.6, P5d), ohne Fenster.
 *
 * <p><b>Teil 1, Tide allein:</b> ein Lauf ohne Wind über das Fenster der Pegel (12.10.–11.11.2026) nach einem Vorlauf;
 * harmonische Analyse je Zelle von G0 (M2-Amphidromien gegen EOT20, Prüfstein 1), an den Pegeln gegen die Analyse der
 * Beobachtung über dieselben Zeiten (M2, Prüfstein 2) und gegen deren astronomische Tide (RMSE, Prüfstein 3).
 * <b>Teil 2, Sturmflut:</b> ein Lauf mit Wind und Luftdruck aus ERA5 über den 25./26.10.2026, mit G3 als Fenster um
 * Hooge: Scheitel an vier Pegeln (Prüfstein 4) und Hooge zur Auskunft. In beiden Bilanz (5) und kein NaN (6).</p>
 *
 * <p>Aufruf: {@code TideCheck --gebiet <Ordner> [--karte] [--tage d] [--vorlauf h] [--sturm-start z] [--sturm-ende z]
 * [--ohne-hooge] [--nur tide|sturm] [--tide-start z] [--stufen "G0 | G1"] [--seegang] [--name P8c_Tide] [--ohne-attest]}; ohne {@code --tage} das ganze
 * Fenster der Pegel; {@code --tide-start} und {@code --stufen} nur zur Probe.</p>
 *
 * <p><b>Seegang (P8c):</b> Mit {@code --seegang} rechnet Teil 2 die Sturmflut zweimal, erst ohne Seegang (Vergleich, zur
 * Auskunft), dann mit Seegang auf der Karte (28 × 36, ab 24.10. 18 UTC) und seiner Rückwirkung auf hydro
 * ({@code waves.force}); Prüfstein 4 gilt für den Lauf mit Seegang. Dazu je Pegel und am Hafen von Hooge der Beitrag des
 * Seegangs zum Scheitel (mit minus ohne) und der Lauf mit Seegang, aber ohne Rückwirkung, entfällt (er gleicht dem ohne).</p>
 */
public final class TideCheck {
    static final Attest A = new Attest("com.dan.lauf", Lauf.VERSION);

    // ------------------------------------------------------------------ Grenzen [G], vor der Messung festgelegt (Werkbuch 12.6, Plan P5d)

    /** Prüfstein 1: Lage der M2-Amphidromien gegen EOT20 (km). */
    static final double GRENZE_AMPHIDROMIE = 50;
    /** Prüfstein 1: Suchkreis um die Lage aus dem Befund (km) und Tiefe der Zellen (m unter NHN). */
    static final double SUCHKREIS = 200, MINDESTTIEFE = 10;
    /** Prüfstein 2: M2 Amplitude (relativ) und Phase (Grad). */
    static final double GRENZE_AMPLITUDE = 0.10, GRENZE_PHASE = 10;
    /** Prüfstein 3: RMSE gegen die astronomische Tide (m). */
    static final double GRENZE_RMSE = 0.15;
    /** Prüfstein 4: Scheitel der Sturmflut (m). */
    static final double GRENZE_SCHEITEL = 0.30;
    /** Prüfstein 5: Volumen. */
    static final double GRENZE_BILANZ = 1e-5;
    /** Abtastung der Tide (s) [G]; Umkreis um einen Pegel, in dem die tiefste Zelle gilt (m) [G]. */
    static final double ABTASTUNG = 600, UMKREIS_PEGEL = 1000;
    /** Nass in der Auskunft über Hooge: mittlere Tiefe der Zelle (m) [G]. */
    static final double NASS_HOOGE = 0.01;

    /** Die drei M2-Amphidromien der Nordsee aus dem Befund (12.6, Punkt 2): Name, Breite, Länge. */
    static final Object[][] AMPHIDROMIEN = {{"Südliche Bucht", 52.50, 2.875}, {"mittlere Nordsee", 55.50, 5.375}, {"vor Norwegen", 58.25, 6.00}};
    /** Pegel mit Prüfstein 2 (und 3 an Husum, Wittdün; 4 an allen vier). */
    static final String[] PRUEF = {"HELGOLAND_BINNENHAFEN", "HUSUM", "WITTDUEN", "PELLWORM_ANLEGER"};
    static final String[] RMSE = {"HUSUM", "WITTDUEN"};
    /** Pegel zur Auskunft. */
    static final String[] AUSKUNFT = {"BUESUM", "DAGEBUELL", "HOERNUM", "LIST_AUF_SYLT", "EIDER-SPERRWERK_AP", "TOENNING"};
    /** Kandidaten der Analyse, wie die Aufbereitung (P5b, Schritt pegel). */
    static final List<Partialtide> KANDIDATEN = List.of(Partialtide.M2, Partialtide.S2, Partialtide.N2, Partialtide.K2, Partialtide.ZWEI_N2, Partialtide.MU2,
            Partialtide.NU2, Partialtide.K1, Partialtide.O1, Partialtide.P1, Partialtide.Q1, Partialtide.M4, Partialtide.MS4, Partialtide.MN4, Partialtide.M6);
    /** G3 als Fenster um Hooge: O 467,0–473,0 km, N 6 044,6–6 049,6 km (Zellen ab G3, auf Zellgrenzen von G2) [G]. */
    static final int[] FENSTER_HOOGE = {650, 980, 300, 250};

    public static void main(String[] args) {
        List<String> a = List.of(args);
        String gebiet = wert(a, "--gebiet", null);
        boolean karte = a.contains("--karte");
        System.out.println("TideCheck – com.dan.lauf " + Lauf.VERSION + " (Werkbuch HalligHooge Teil A, P5d, P5e)" + (karte ? ", hydro auf der Karte" : ", hydro als Referenz"));
        if (gebiet == null) {
            A.pruefe(false, "Gebiet", "--gebiet fehlt", "Ordner des Gebiets");
        } else {
            try {
                TideCheck t = new TideCheck(new File(gebiet), karte);
                String tage = wert(a, "--tage", null), vor = wert(a, "--vorlauf", null), nur = wert(a, "--nur", "");
                if (tage != null) t.tage = Double.parseDouble(tage);
                if (vor != null) t.vorlauf = Double.parseDouble(vor) * 3600;
                t.sturmStart = Instant.parse(wert(a, "--sturm-start", t.sturmStart.toString()));
                t.sturmEnde = Instant.parse(wert(a, "--sturm-ende", t.sturmEnde.toString()));
                t.hooge = !a.contains("--ohne-hooge");
                t.tideStart = Instant.parse(wert(a, "--tide-start", t.tideStart.toString()));
                t.stufen = wert(a, "--stufen", null);
                t.reihenDatei = wert(a, "--reihen", null);
                t.schub = switch (wert(a, "--schub", "charnock")) {
                    case "era5" -> Antrieb.Schub.ERA5;
                    case "charnock" -> Antrieb.Schub.CHARNOCK;
                    case "wu" -> Antrieb.Schub.WU;
                    default -> throw new IllegalArgumentException("--schub wu|era5|charnock");
                };
                t.zerlegen = a.contains("--zerlegen");
                boolean see = a.contains("--seegang");
                if (!nur.equals("sturm")) t.tide();
                if (!nur.equals("tide")) {
                    if (see) {
                        t.sturm(false, true);
                        t.sturm(true, false);
                    } else t.sturm(false, false);
                }
                if (t.bild.tide != null || !t.bild.pegelSturm.isEmpty()) {
                    Path p = TideBild.schreibe(t.bild, Path.of("docs", "status", wert(a, "--name", "P5_Tide") + ".png"));
                    A.info("Status-Bild", p.toString());
                }
            } catch (Throwable e) {
                A.fehler(e);
            }
        }
        if (!a.contains("--ohne-attest")) {
            String nm = wert(a, "--name", karte ? "P5_Tide" : "P5_TideReferenz");
            Path p = A.schreibe(Path.of("docs", "status", nm + ".md"), nm.startsWith("P5") ? (karte ? "P5d – TideCheck auf der Karte" : "P5d – TideCheck (Referenz)") : nm.replace('_', ' ') + " – TideCheck" + (a.contains("--seegang") ? " mit Seegang" : ""));
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static String wert(List<String> a, String name, String vorgabe) {
        int i = a.indexOf(name);
        return i >= 0 && i + 1 < a.size() ? a.get(i + 1) : vorgabe;
    }

    // ------------------------------------------------------------------ Aufbau

    final File gebiet;
    final boolean karte;
    final Gebiet ge;
    final Map<String, Pegel> pegel = new LinkedHashMap<>();
    /** Tage des Fensters der Analyse (NaN: das ganze Fenster der Pegel); Vorlauf vor dem Fenster (s) [G]. */
    double tage = Double.NaN, vorlauf = 48 * 3600;
    Instant tideStart = Instant.parse("2026-10-10T12:00:00Z");
    Instant sturmStart = Instant.parse("2026-10-23T00:00:00Z"), sturmEnde = Instant.parse("2026-10-26T06:00:00Z");
    /** Fenster des Scheitels (UTC) [G]. */
    Instant scheitel0 = Instant.parse("2026-10-25T12:00:00Z"), scheitel1 = Instant.parse("2026-10-26T06:00:00Z");
    boolean hooge = true;
    /** Stufen des Tide-Laufs (nur zur Probe, sonst G0 | G1 | G2). */
    String stufen;
    /** Reihen der Pegel im Tide-Lauf als CSV (zur Auskunft); im Lauf der Sturmflut mit der Endung {@code .sturm.csv}. */
    String reihenDatei;
    /** Windschub im Lauf der Sturmflut (P5e, F34 B: Vorgabe Charnock mit α aus ERA5). */
    Antrieb.Schub schub = Antrieb.Schub.CHARNOCK;
    /** Sturmflut zerlegen: ein zweiter Lauf ohne Antrieb, Tide und Stau am beobachteten Scheitel (P5e, Auskunft). */
    boolean zerlegen;
    final TideBild.Daten bild = new TideBild.Daten();

    TideCheck(File gebiet, boolean karte) throws Exception {
        this.gebiet = gebiet;
        this.karte = karte;
        ge = Gebiet.lies(gebiet);
        if (Pegel.da(gebiet)) for (Pegel p : Pegel.lies(gebiet).values()) pegel.put(Pegel.datei(p.name).replace(".f32", ""), p);
    }

    /** Ein Pegel im Lauf: Gitter und Zelle (die tiefste im Umkreis, im feinsten Gitter, das ihn enthält). */
    record Ort(String name, Pegel pegel, int gitter, int zelle, double grund) { }

    Ort ort(Lauf l, String name) {
        Pegel p = pegel.get(name);
        if (p == null) return null;
        int[] z = zelle(l, p.laenge, p.breite);
        if (z == null || l.hydro.gitter.get(z[0]).grund(z[1]) > 2) return null;          // nur Land im Umkreis (geschlossen): ohne diesen Pegel
        return new Ort(name, p, z[0], z[1], l.hydro.gitter.get(z[0]).grund(z[1]));
    }

    /** {Gitter, Zelle}: die tiefste Zelle im Umkreis eines Orts im feinsten Gitter, das ihn enthält (wie LaufCheck). */
    static int[] zelle(Lauf l, double lon, double lat) {
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
                    if (h.grund(c) < tief) { tief = h.grund(c); best = c; }
                }
            if (best >= 0) return new int[] {k, best};
        }
        return null;
    }

    Lauf.Einstellung einstellung(Instant start, boolean antrieb) {
        Lauf.Einstellung e = new Lauf.Einstellung();
        e.gebiet = gebiet;
        e.start = start;
        e.karte = karte;
        e.antrieb = antrieb;
        return e;
    }

    // ------------------------------------------------------------------ Teil 1: Tide allein

    void tide() throws Exception {
        A.teil("1 Tide allein: G0 | G1 | G2 über das Fenster der Pegel");
        Pegel bezug = pegel.get("HUSUM");
        if (bezug == null) throw new IllegalStateException("Pegel fehlen: Aufbereitung --schritte pegel");
        Instant w0 = spaeter(bezug.start, tideStart.plusSeconds((long) vorlauf)), w1 = bezug.ende();
        for (String n : PRUEF) if (pegel.containsKey(n)) w1 = frueher(w1, pegel.get(n).ende());
        if (!Double.isNaN(tage)) w1 = frueher(w1, w0.plusSeconds((long) (tage * 86400)));
        double t0 = Math.ceil(sekunden(tideStart, w0) / ABTASTUNG) * ABTASTUNG, t1 = Math.floor(sekunden(tideStart, w1) / ABTASTUNG) * ABTASTUNG;
        int n = (int) Math.round((t1 - t0) / ABTASTUNG) + 1;
        double dauer = (t1 - t0) / 3600;
        List<Partialtide> aus = new ArrayList<>(), tiden = Harmonik.trennbar(KANDIDATEN, dauer, 1, aus);
        Lauf.Einstellung e = einstellung(tideStart, false);
        if (stufen != null) {
            e.stufen(stufen);
            bild.titel = "P5d – Tide über " + stufen.replace(" | ", " → ") + " (TideCheck, Probe)";
        }
        long w = System.nanoTime();
        try (Lauf l = Lauf.bauen(e)) {
            A.info("Lauf", l + "; Aufbau " + f("%.1f s", l.aufbau));
            A.info("Fenster der Analyse", f("%s bis %s (%.1f Tage, %d Stände zu %.0f s; Vorlauf ab %s); Tiden %s, ausgelassen %s", zeit(tideStart, t0), zeit(tideStart, t1), dauer / 24, n,
                    ABTASTUNG, tideStart, namen(tiden), namen(aus)));
            List<Ort> orte = new ArrayList<>();
            for (String[] liste : new String[][] {PRUEF, AUSKUNFT})
                for (String name : liste) {
                    Ort o = ort(l, name);
                    if (o != null) orte.add(o);
                }
            StringBuilder ob = new StringBuilder();
            for (Ort o : orte) ob.append(f("%s%s in %s (Grund %.1f m NHN)", ob.length() > 0 ? "; " : "", o.name, l.hydro.gitter.get(o.gitter).gitter.name, o.grund));
            A.info("Pegel im Lauf", ob.toString());
            double[][] reihe = new double[orte.size()][n];
            double[] zeiten = new double[n];
            Hydro g0 = l.hydro.gitter.get(0);
            Harmonik.Sammler s = new Harmonik.Sammler(tiden, g0.zellen);
            int k = 0, tag = 0;
            for (double t = e.kopplung; t <= t1 + 1e-6; t += e.kopplung) {
                l.bis(t);
                if (t >= t0 - 1e-6 && Math.abs(Math.IEEEremainder(t - t0, ABTASTUNG)) < 1e-6 && k < n) {
                    Instant z = l.zeitpunkt(t);
                    zeiten[k] = z.getEpochSecond();
                    for (int q = 0; q < orte.size(); q++) reihe[q][k] = l.hydro.gitter.get(orte.get(q).gitter).eta[orte.get(q).zelle];
                    s.dazu(z, g0.eta);
                    k++;
                }
                if (t >= (tag + 1) * 86400.0 - 1e-6) {
                    tag++;
                    double r = (System.nanoTime() - w) / 1e9;
                    System.out.println(f("         t = %4.1f d: Zeitraffer %6.1f, Bilanz %s, noch rund %.0f min", t / 86400, t / r, bilanzen(l), r / t * (t1 - t) / 60));
                }
            }
            double r = (System.nanoTime() - w) / 1e9;
            A.info("Zeit", f("%.1f Tage in %.0f s, Zeitraffer %.0f, %.1f ns je Zellschritt (%.3e Zellschritte; mit tide und den Prüfungen; %s)", t1 / 86400, r, t1 / r,
                    r * 1e9 / l.rechnung.zellschritte(), (double) l.rechnung.zellschritte(), l.rechnung.geraet()));
            if (reihenDatei != null) {
                StringBuilder c = new StringBuilder("zeit");
                for (Ort o : orte) c.append(';').append(o.name);
                c.append('\n');
                for (int q = 0; q < n; q++) {
                    c.append(Instant.ofEpochSecond((long) zeiten[q]));
                    for (int o = 0; o < orte.size(); o++) c.append(';').append(String.format(java.util.Locale.ROOT, "%.4f", reihe[o][q]));
                    c.append('\n');
                }
                java.nio.file.Files.writeString(Path.of(reihenDatei), c);
            }
            amphidromien(l, s);
            pegelTide(orte, zeiten, reihe, tiden, l);
            bilanzUndNaN(l, "Tide allein");
        }
    }

    /** Prüfstein 1: M2-Amphidromien des Laufs gegen EOT20, jeweils als kleinste Amplitude im Suchkreis über tiefen Zellen. */
    void amphidromien(Lauf l, Harmonik.Sammler s) throws Exception {
        Hydro g0 = l.hydro.gitter.get(0);
        Gitter g = g0.gitter;
        int m2 = s.index("M2");
        Tidenfeld eot = Tidenfeld.lies(gebiet);
        int em = eot.index(Partialtide.M2);
        float[] z = ge.groundZ(g.name), nass = ge.nass(g.name);
        double[] modell = new double[g.zellen()], eotA = new double[g.zellen()], modellG = new double[g.zellen()], eotG = new double[g.zellen()];
        java.util.Arrays.fill(modell, Double.NaN);
        java.util.Arrays.fill(eotA, Double.NaN);
        java.util.stream.IntStream.range(0, g.zellen()).parallel().forEach(c -> {
            if (nass[c] < 0.5) return;
            double[] ap = s.tide(c, m2);
            modell[c] = ap[0];
            modellG[c] = ap[1];
            double[][] k = eot.konstanten(g.x(c % g.nx), g.y(c / g.nx), 2);
            if (k != null) {
                double[] e = Tidenfeld.amplitudePhase(k[em]);
                eotA[c] = e[0];
                eotG[c] = e[1];
            }
        });
        bild.tide = new TideBild.Tide(g, modell, modellG, eotA, eotG);
        int gut = 0;
        StringBuilder b = new StringBuilder();
        double schlimmst = 0;
        for (Object[] am : AMPHIDROMIEN) {
            int cm = minimum(g, modell, z, nass, (double) am[1], (double) am[2]), ce = minimum(g, eotA, z, nass, (double) am[1], (double) am[2]);
            if (cm < 0 || ce < 0) {
                b.append(f("%s%s: keine Zelle", b.length() > 0 ? "; " : "", am[0]));
                schlimmst = Double.POSITIVE_INFINITY;
                continue;
            }
            double d = abstand(g, cm, ce);
            schlimmst = Math.max(schlimmst, d);
            if (d <= GRENZE_AMPHIDROMIE) gut++;
            bild.tide.amphidromien().add(new double[] {cm, ce});
            b.append(f("%s%s: Lauf %.2f° N %.2f° O (%.1f cm), EOT20 %.2f° N %.2f° O (%.1f cm), %.0f km", b.length() > 0 ? "; " : "", am[0], g.y(cm / g.nx), g.x(cm % g.nx),
                    100 * modell[cm], g.y(ce / g.nx), g.x(ce % g.nx), 100 * eotA[ce], d));
        }
        A.pruefe(gut == AMPHIDROMIEN.length, f("Prüfstein 1: M2-Amphidromien der Nordsee, Lauf gegen EOT20 (kleinste Amplitude über Zellen tiefer als %.0f m im Kreis von %.0f km um die Lage aus dem Befund)",
                MINDESTTIEFE, SUCHKREIS), b + f(" – größter Abstand %.0f km", schlimmst), f("je ≤ %.0f km [G] (7.2)", GRENZE_AMPHIDROMIE));
        // M2 über der Nordsee zur Auskunft: Amplitude und Phase gegen EOT20 über tiefen Zellen der Nordsee
        double qa = 0, qg = 0;
        int nq = 0;
        for (int c = 0; c < g.zellen(); c++) {
            double lat = g.y(c / g.nx), lon = g.x(c % g.nx);
            if (lat < 51 || lat > 61 || lon < -4 || lon > 9 || Double.isNaN(modell[c]) || Double.isNaN(eotA[c]) || z[c] > -MINDESTTIEFE || eotA[c] < 0.2) continue;
            qa += sq(modell[c] / eotA[c] - 1);
            qg += sq(winkel(modellG[c] - eotG[c]));
            nq++;
        }
        // dazu je Teil der Nordsee das Mittel (Lauf zu hoch oder zu tief, zu spät oder zu früh)
        StringBuilder teile = new StringBuilder();
        for (Object[] r : TEILE_NORDSEE) {
            double sa = 0, sg = 0;
            int nr = 0;
            for (int c = 0; c < g.zellen(); c++) {
                double lat = g.y(c / g.nx), lon = g.x(c % g.nx);
                if (lat < (double) r[1] || lat > (double) r[2] || lon < (double) r[3] || lon > (double) r[4] || Double.isNaN(modell[c]) || Double.isNaN(eotA[c])
                        || z[c] > -MINDESTTIEFE || eotA[c] < 0.2) continue;
                sa += modell[c] / eotA[c] - 1;
                sg += winkel(modellG[c] - eotG[c]);
                nr++;
            }
            teile.append(f("; %s (%d Zellen) im Mittel %+.1f %%, %+.1f°", r[0], nr, 100 * sa / Math.max(1, nr), sg / Math.max(1, nr)));
        }
        A.info("M2 über der Nordsee (51–61° N, 4° W–9° O, tiefer als 10 m, EOT20 ab 20 cm)", f("Lauf gegen EOT20 über %d Zellen: Amplitude RMS %.1f %%, Phase RMS %.1f°", nq,
                100 * Math.sqrt(qa / Math.max(1, nq)), Math.sqrt(qg / Math.max(1, nq))) + teile);
    }

    /** Teile der Nordsee für die Auskunft zu M2: Name, Breite von–bis, Länge von–bis (Grad). */
    static final Object[][] TEILE_NORDSEE = {{"Deutsche Bucht", 53.3, 55.5, 6.5, 9.0}, {"Südliche Bucht", 51.0, 53.3, 1.5, 6.5},
            {"mittlere Nordsee", 53.3, 57.0, -1.0, 6.5}, {"nördliche Nordsee", 57.0, 61.0, -4.0, 9.0}};

    static int minimum(Gitter g, double[] a, float[] z, float[] nass, double lat, double lon) {
        int best = -1;
        double min = Double.POSITIVE_INFINITY;
        for (int c = 0; c < g.zellen(); c++) {
            if (Double.isNaN(a[c]) || z[c] > -MINDESTTIEFE || nass[c] < 0.99) continue;
            if (kilometer(lat, lon, g.y(c / g.nx), g.x(c % g.nx)) > SUCHKREIS) continue;
            if (a[c] < min) { min = a[c]; best = c; }
        }
        return best;
    }

    static double abstand(Gitter g, int a, int b) { return kilometer(g.y(a / g.nx), g.x(a % g.nx), g.y(b / g.nx), g.x(b % g.nx)); }

    static double kilometer(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2), dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double h = sq(Math.sin(dp / 2)) + Math.cos(p1) * Math.cos(p2) * sq(Math.sin(dl / 2));
        return 2 * 6371.0 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Prüfsteine 2 und 3: Analyse des Laufs und der Beobachtung über dieselben Zeiten, mit denselben Kandidaten. */
    void pegelTide(List<Ort> orte, double[] zeiten, double[][] reihe, List<Partialtide> tiden, Lauf l) {
        int gut2 = 0, n2 = 0, gut3 = 0, n3 = 0;
        StringBuilder b2 = new StringBuilder(), b3 = new StringBuilder(), bi = new StringBuilder(), bk = new StringBuilder();
        for (int q = 0; q < orte.size(); q++) {
            Ort o = orte.get(q);
            double[] beob = new double[zeiten.length];
            for (int k = 0; k < zeiten.length; k++) beob[k] = o.pegel.wert(Instant.ofEpochSecond((long) zeiten[k]));
            Harmonik.Ergebnis hm = Harmonik.analyse(zeiten, reihe[q], tiden, 1), hb = Harmonik.analyse(zeiten, beob, tiden, 1);
            Harmonik.Tide mm = hm.tide("M2"), mb = hb.tide("M2");
            double da = mm.amplitude() / mb.amplitude() - 1, dg = winkel(mm.phaseGrad() - mb.phaseGrad());
            String gname = l.hydro.gitter.get(o.gitter).gitter.name;
            String text = f("%s (%s) %.3f m, %.1f° gegen %.3f m, %.1f° (%+.1f %%, %+.1f°)", o.name, gname, mm.amplitude(), mm.phaseGrad(), mb.amplitude(), mb.phaseGrad(), 100 * da, dg);
            boolean pruef = java.util.Arrays.asList(PRUEF).contains(o.name);
            if (pruef) {
                n2++;
                if (Math.abs(da) <= GRENZE_AMPLITUDE && Math.abs(dg) <= GRENZE_PHASE) gut2++;
                b2.append(b2.length() > 0 ? "; " : "").append(text);
            } else bi.append(bi.length() > 0 ? "; " : "").append(text);
            // Prüfstein 3: gegen die astronomische Tide der Beobachtung, beide ohne Mittel
            double mmit = mittel(reihe[q]), sq = 0;
            int nn = 0;
            double[] astro = new double[zeiten.length];
            for (int k = 0; k < zeiten.length; k++) {
                astro[k] = hb.wert(Instant.ofEpochSecond((long) zeiten[k])) - hb.mittel();
                sq += sq((reihe[q][k] - mmit) - astro[k]);
                nn++;
            }
            double rmse = Math.sqrt(sq / nn);
            if (java.util.Arrays.asList(RMSE).contains(o.name)) {
                n3++;
                if (rmse < GRENZE_RMSE) gut3++;
                b3.append(f("%s%s %.3f m", b3.length() > 0 ? "; " : "", o.name, rmse));
                bild.pegelTide.put(o.name, new double[][] {zeiten, reihe[q], astro, beob});
            }
            // MThw und MTnw: Mittel der Hoch- und Niedrigwasser je halber Tide (12,42 h / 2) im Lauf und in der Beobachtung, Kennwert 2011–2020
            double[] hl = hochNiedrig(reihe[q]), hlb = hochNiedrig(beob);
            bk.append(f("%s%s: MThw %+.2f / %+.2f / %+.2f, MTnw %+.2f / %+.2f / %+.2f", bk.length() > 0 ? "; " : "", o.name, hl[0], hlb[0], o.pegel.kennwert("MThw"), hl[1], hlb[1],
                    o.pegel.kennwert("MTnw")));
        }
        A.pruefe(n2 == PRUEF.length && gut2 == n2, "Prüfstein 2: M2 an Helgoland, Husum, Wittdün, Pellworm, Lauf gegen Beobachtung (dieselben Zeiten und Tiden)", b2.toString(),
                f("Amplitude ±%.0f %%, Phase ±%.0f° [G] (7.2)", 100 * GRENZE_AMPLITUDE, GRENZE_PHASE));
        A.pruefe(n3 == RMSE.length && gut3 == n3, "Prüfstein 3: Tide allein gegen die astronomische Tide (Analyse der Beobachtung über dasselbe Fenster), beide ohne Mittel, RMSE", b3.toString(),
                f("< %.2f m [G] (7.2)", GRENZE_RMSE));
        if (bi.length() > 0) A.info("M2 an den übrigen Pegeln (Auskunft)", bi.toString());
        A.info("MThw und MTnw, m NHN: Lauf / Beobachtung im Fenster / Kennwert 2011–2020", bk.toString());
    }

    /** Mittel der Hoch- und der Niedrigwasser: größter und kleinster Wert je Abschnitt von 6,21 h [G] im Wechsel (über NaN hinweg). */
    static double[] hochNiedrig(double[] r) {
        int je = (int) Math.round(6.21 * 3600 / ABTASTUNG);
        List<Double> hoch = new ArrayList<>(), nied = new ArrayList<>();
        for (int a = 0; a + 2 * je <= r.length; a += 2 * je) {
            double hi = Double.NEGATIVE_INFINITY, lo = Double.POSITIVE_INFINITY;
            for (int k = a; k < a + 2 * je; k++) if (!Double.isNaN(r[k])) { hi = Math.max(hi, r[k]); lo = Math.min(lo, r[k]); }
            if (Double.isFinite(hi)) hoch.add(hi);
            if (Double.isFinite(lo)) nied.add(lo);
        }
        return new double[] {hoch.stream().mapToDouble(x -> x).average().orElse(Double.NaN), nied.stream().mapToDouble(x -> x).average().orElse(Double.NaN)};
    }

    // ------------------------------------------------------------------ Teil 2: Sturmflut

    /** Beginn des Seegangs in der Sturmflut (P8c), wie im Archivlauf: 12 h vor dem Fenster der Bilder [G]. */
    Instant seegangAb = Instant.parse("2026-10-24T18:00:00Z");
    /** Aus dem Lauf ohne Seegang (Vergleich): Scheitel je Pegel im Fenster und der höchste Stand am Hafen von Hooge. */
    final Map<String, Double> scheitelOhne = new LinkedHashMap<>();
    double hoogeOhne = Double.NaN;

    /**
     * {@code seegang}: mit Seegang und Rückwirkung (P8c); {@code vergleich}: nur zur Auskunft (Prüfstein 4 als Auskunft,
     * die Scheitel für den Beitrag des Seegangs merken, ohne Zerlegung).
     */
    void sturm(boolean seegang, boolean vergleich) throws Exception {
        A.teil("2 Sturmflut 25./26.10.2026 mit Wind und Luftdruck aus ERA5" + (hooge ? ", G3 um Hooge" : "") + (seegang ? ", mit Seegang und Rückwirkung (P8c)" : vergleich ? ", ohne Seegang (Vergleich)" : ""));
        if (!seegang) windVergleich();
        Lauf.Einstellung e = einstellung(sturmStart, true);
        e.schub = schub;
        if (seegang) {
            e.seegang = karte ? Lauf.Einstellung.Seegang.KARTE : Lauf.Einstellung.Seegang.PROZESSOR;
            e.seegangAb = Math.floor(sekunden(sturmStart, seegangAb) / e.seegangKopplung) * e.seegangKopplung;
            e.seegangRueckwirkung = true;
        }
        if (hooge) {
            e.stufen("G0 | G1 | G2 G3");
            e.fenster3 = FENSTER_HOOGE;
        } else if (stufen != null) e.stufen(stufen);
        double t1 = sekunden(sturmStart, sturmEnde);
        long w = System.nanoTime();
        try (Lauf l = Lauf.bauen(e)) {
            A.info("Lauf", l + (hooge ? f("; G3 als Fenster %d × %d Zellen ab Zelle %d, %d (O %.1f–%.1f km, N %.1f–%.1f km), %d Deich- und Sielkanten", FENSTER_HOOGE[2],
                    FENSTER_HOOGE[3], FENSTER_HOOGE[0], FENSTER_HOOGE[1], l.hydro.gitter.get(3).gitter.x0 / 1000, l.hydro.gitter.get(3).gitter.x1() / 1000,
                    l.hydro.gitter.get(3).gitter.y0 / 1000, l.hydro.gitter.get(3).gitter.y1() / 1000, l.hydro.gitter.get(3).bauwerke()) : "") + f("; Aufbau %.1f s", l.aufbau));
            List<Ort> orte = new ArrayList<>(), mehr = new ArrayList<>();
            for (String name : PRUEF) {
                Ort o = ort(l, name);
                if (o != null) orte.add(o);
            }
            for (String name : AUSKUNFT) {
                Ort o = ort(l, name);
                if (o != null) mehr.add(o);
            }
            // Hooge: Pegel am Hafen (Rezept) und der Ring
            Hydro h3 = hooge ? l.hydro.gitter.get(3) : null;
            int hafen = -1, ring = -1, ringZellen = 0;
            boolean[] imRing = null;
            if (h3 != null) {
                double[] p = zahlen(ge.rezept("pegel.Hooge")), rp = zahlen(ge.rezept("ring.Hooge"));
                Gitter g3 = h3.gitter;
                if (p != null && g3.enthaelt(p[0], p[1])) hafen = g3.index(g3.i(p[0]), g3.j(p[1]));
                Ringe rg = h3.ringe();
                if (rg != null && rp != null && g3.enthaelt(rp[0], rp[1])) {
                    ring = rg.flaeche[g3.index(g3.i(rp[0]), g3.j(rp[1]))];
                    imRing = new boolean[h3.zellen];
                    for (int c = 0; c < h3.zellen; c++) if (rg.flaeche[c] == ring && rg.tiefe(c) > 0) { imRing[c] = true; ringZellen++; }
                }
            }
            int n = (int) Math.round(t1 / e.kopplung);
            double[] zeiten = new double[n];
            double[][] reihe = new double[orte.size() + 1][n], reiheMehr = new double[mehr.size()][n];
            double hoogeMax = Double.NEGATIVE_INFINITY, ringMax = 0;
            Instant hoogeZeit = null, ringZeit = null;
            int tag = 0;
            for (int k = 0; k < n; k++) {
                double t = (k + 1) * e.kopplung;
                l.bis(t);
                Instant z = l.zeitpunkt(t);
                zeiten[k] = z.getEpochSecond();
                for (int q = 0; q < orte.size(); q++) reihe[q][k] = l.hydro.gitter.get(orte.get(q).gitter).eta[orte.get(q).zelle];
                for (int q = 0; q < mehr.size(); q++) reiheMehr[q][k] = l.hydro.gitter.get(mehr.get(q).gitter).eta[mehr.get(q).zelle];
                if (h3 != null) {
                    reihe[orte.size()][k] = hafen >= 0 ? h3.eta[hafen] : Double.NaN;
                    if (hafen >= 0 && h3.eta[hafen] > hoogeMax) {
                        hoogeMax = h3.eta[hafen];
                        hoogeZeit = z;
                        bild.hooge = new TideBild.Hooge(h3.gitter, tiefen(h3), h3.eta.clone(), z, imRing);
                    }
                    if (imRing != null) {
                        int nass = 0;
                        for (int c = 0; c < h3.zellen; c++) if (imRing[c] && h3.vol[c] > NASS_HOOGE * h3.flaeche(c)) nass++;
                        double anteil = (double) nass / ringZellen;
                        if (anteil > ringMax) { ringMax = anteil; ringZeit = z; }
                    }
                }
                if (t >= (tag + 1) * 6 * 3600.0 - 1e-6) {
                    tag++;
                    double r = (System.nanoTime() - w) / 1e9;
                    System.out.println(f("         %s: Zeitraffer %6.1f, Bilanz %s, noch rund %.0f min", z, t / r, bilanzen(l), r / t * (t1 - t) / 60));
                }
            }
            double r = (System.nanoTime() - w) / 1e9;
            A.info("Zeit", f("%.1f h in %.0f s, Zeitraffer %.0f, %.1f ns je Zellschritt (%.3e Zellschritte; mit antrieb, tide und den Prüfungen; %s)", t1 / 3600, r, t1 / r,
                    r * 1e9 / l.rechnung.zellschritte(), (double) l.rechnung.zellschritte(), l.rechnung.geraet()));
            // Prüfstein 4: Scheitel im Fenster
            int gut = 0;
            StringBuilder b = new StringBuilder();
            for (int q = 0; q < orte.size(); q++) {
                Ort o = orte.get(q);
                double mm = Double.NEGATIVE_INFINITY, mb = Double.NEGATIVE_INFINITY;
                Instant zm = null, zb = null;
                for (int k = 0; k < n; k++) {
                    Instant z = Instant.ofEpochSecond((long) zeiten[k]);
                    if (z.isBefore(scheitel0) || z.isAfter(scheitel1)) continue;
                    if (reihe[q][k] > mm) { mm = reihe[q][k]; zm = z; }
                }
                for (int k = 0; k < o.pegel.werte.length; k++) {
                    Instant z = o.pegel.zeit(k);
                    if (z.isBefore(scheitel0) || z.isAfter(scheitel1) || Float.isNaN(o.pegel.werte[k])) continue;
                    if (o.pegel.werte[k] > mb) { mb = o.pegel.werte[k]; zb = z; }
                }
                double d = mm - mb;
                if (Math.abs(d) <= GRENZE_SCHEITEL) gut++;
                b.append(f("%s%s (%s) %.2f m um %s gegen %.2f m um %s (%+.2f m)", b.length() > 0 ? "; " : "", o.name, l.hydro.gitter.get(o.gitter).gitter.name, mm, uhr(zm), mb, uhr(zb), d));
                double[] beob = new double[n];
                for (int k = 0; k < n; k++) beob[k] = o.pegel.wert(Instant.ofEpochSecond((long) zeiten[k]));
                bild.pegelSturm.put(o.name, new double[][] {zeiten, reihe[q], beob});
            }
            if (vergleich) A.info(f("Scheitel der Sturmflut %s bis %s ohne Seegang (Vergleich)", uhr(scheitel0), uhr(scheitel1)), b.toString());
            else A.pruefe(orte.size() == PRUEF.length && gut == orte.size(), f("Prüfstein 4: Scheitel der Sturmflut %s bis %s, Lauf gegen Beobachtung%s", uhr(scheitel0), uhr(scheitel1),
                    seegang ? " (mit Seegang)" : ""), b.toString(), f("±%.2f m [G] (wie Xaver, 7.2)", GRENZE_SCHEITEL));
            // Beitrag des Seegangs (P8c): Scheitel mit minus ohne, je Pegel und am Hafen von Hooge
            StringBuilder bs = new StringBuilder();
            for (int q = 0; q < orte.size(); q++) {
                double mm = Double.NEGATIVE_INFINITY;
                for (int k = 0; k < n; k++) {
                    Instant z = Instant.ofEpochSecond((long) zeiten[k]);
                    if (!z.isBefore(scheitel0) && !z.isAfter(scheitel1)) mm = Math.max(mm, reihe[q][k]);
                }
                String nm = orte.get(q).name;
                if (vergleich) scheitelOhne.put(nm, mm);
                else if (seegang && scheitelOhne.containsKey(nm))
                    bs.append(f("%s%s (%s) %+.3f m", bs.length() > 0 ? "; " : "", nm, l.hydro.gitter.get(orte.get(q).gitter).gitter.name, mm - scheitelOhne.get(nm)));
            }
            if (vergleich) hoogeOhne = hoogeMax;
            else if (seegang) {
                if (h3 != null && !Double.isNaN(hoogeOhne)) bs.append(f("; Hafen Hooge %+.3f m (%.2f gegen %.2f m NHN)", hoogeMax - hoogeOhne, hoogeMax, hoogeOhne));
                if (bs.length() > 0) A.info("Beitrag des Seegangs zum Scheitel (Lauf mit minus ohne)", bs.toString());
                if (l.waves != null) A.info("Seegang im Lauf", f("Rechenzeit Seegang %.0f s, hydro %.0f s; außerhalb der Stände gelesen %d", l.takt.rechenzeit("waves"), l.takt.rechenzeit("hydro"), l.bus.ausserhalb()));
                A.pruefe(l.bus.ausserhalb() == 0, "Sturmflut mit Seegang: außerhalb der Stände gelesen", String.valueOf(l.bus.ausserhalb()), "0");
                if (l.waves != null) A.pruefe(l.waves.unheil() == 0, "Sturmflut mit Seegang: Spektrum endlich und nicht negativ", String.valueOf(l.waves.unheil()), "0");
            }
            if (h3 != null) {
                bild.pegelSturm.put("HOOGE (Lauf)", new double[][] {zeiten, reihe[orte.size()], null});
                A.info("Hooge (Auskunft)", f("Hafen (Zelle mit Grund %.1f m NHN): höchster Stand %.2f m NHN um %s; Ring Hooge (%d Zellen, Krone ab 2,01 m): höchstens %.1f %% der Zellen nass (mittlere Tiefe ab %.0f cm)%s",
                        hafen >= 0 ? h3.grund(hafen) : Double.NaN, hoogeMax, uhr(hoogeZeit), ringZellen, 100 * ringMax, 100 * NASS_HOOGE, ringZeit == null ? "" : " um " + uhr(ringZeit)));
            }
            bilanzUndNaN(l, "Sturmflut");
            List<Ort> alle = new ArrayList<>(orte);
            alle.addAll(mehr);
            double[][] mit = new double[alle.size()][];
            for (int q = 0; q < alle.size(); q++) mit[q] = q < orte.size() ? reihe[q] : reiheMehr[q - orte.size()];
            if (reihenDatei != null) reihenSchreiben(Path.of(reihenDatei + ".sturm.csv"), zeiten, alle, mit);
            List<String> gn = new ArrayList<>();
            for (Hydro h : l.hydro.gitter) gn.add(h.gitter.name);
            if (zerlegen && !vergleich) zerlegen(e, zeiten, alle, mit, gn);
        }
    }

    /** Rauheitslänge über See für die Umrechnung des Stationswinds auf 10 m (m) [G]. */
    static final double Z0_SEE = 2e-4;

    /**
     * Wind von ERA5 gegen den Wind an Stationen des DWD (P5e, F36 A, Auskunft): je Station (Aufbereitung, Schritt
     * {@code wind}) ERA5 {@code u10}, {@code v10} bilinear am Ort und linear in der Zeit gegen das Stundenmittel der
     * Station (10-Minuten-Werte mit Ende bis 30 min vor und nach der vollen Stunde), auf 10 m umgerechnet mit dem
     * logarithmischen Profil über See (z0 = 0,2 mm [G]). Über den Sturm ({@link #scheitel0} − 1 Tag bis {@link #scheitel1})
     * und den Anstieg des Hauptstaus (25.10. 15–23 UTC): Σ U_ERA5 / Σ U_Station, Σ U_ERA5² / Σ U_Station² (so viel Schub
     * fehlt oder ist zu viel), die höchsten Stundenmittel und der Unterschied der Richtung.
     */
    void windVergleich() throws Exception {
        Map<String, com.dan.core.pruef.Windstation> st = com.dan.core.pruef.Windstation.lies(gebiet);
        if (st.isEmpty()) {
            A.info("Wind an Stationen (Auskunft, F36)", "keine Stationen im Gebiet (Aufbereitung, Schritt wind, mit wind.ordner im Rezept)");
            return;
        }
        com.dan.core.gitter.Wetterfeld w = com.dan.core.gitter.Wetterfeld.lies(gebiet);
        int iu = w.groesse("u10"), iv = w.groesse("v10");
        Instant[][] fenster = {{scheitel0.minusSeconds(86400), scheitel1}, {Instant.parse("2026-10-25T15:00:00Z"), Instant.parse("2026-10-25T23:00:00Z")}};
        StringBuilder b = new StringBuilder();
        for (com.dan.core.pruef.Windstation s : st.values()) {
            var gw = w.raster.gewichte(s.laenge, s.breite, null, 0, 1, 0);
            b.append(b.length() > 0 ? "; " : "").append(f("%s (%s, Geber %.0f m)", s.name, s.kennung, s.geber));
            String[] namen = {"Sturm", "Anstieg 25.10. 15–23 UTC"};
            for (int q = 0; q < 2; q++) {
                double se = 0, ss = 0, se2 = 0, ss2 = 0, me = 0, ms = 0, dr = 0, gwr = 0;
                int n = 0;
                for (Instant t = fenster[q][0]; !t.isAfter(fenster[q][1]); t = t.plusSeconds(3600)) {
                    double us = s.mittel(t, 1800);
                    if (Double.isNaN(us)) continue;
                    us = s.auf10m(us, Z0_SEE);
                    double k = (t.getEpochSecond() - w.start.getEpochSecond()) / (double) w.schritt;
                    int k0 = (int) Math.floor(k);
                    double a = k - k0, ux = 0, uy = 0;
                    for (int m = 0; m < gw.index().length; m++) {
                        int pnt = gw.index()[m];
                        ux += gw.gewicht()[m] * ((1 - a) * w.feld(k0, iu)[pnt] + a * w.feld(Math.min(w.zeiten - 1, k0 + 1), iu)[pnt]);
                        uy += gw.gewicht()[m] * ((1 - a) * w.feld(k0, iv)[pnt] + a * w.feld(Math.min(w.zeiten - 1, k0 + 1), iv)[pnt]);
                    }
                    double ue = Math.hypot(ux, uy);
                    se += ue; ss += us; se2 += ue * ue; ss2 += us * us; me = Math.max(me, ue); ms = Math.max(ms, us);
                    double dd = stationRichtung(s, t);
                    if (!Double.isNaN(dd) && us > 5) {
                        double de = (Math.toDegrees(Math.atan2(-ux, -uy)) + 360) % 360, d = ((de - dd + 540) % 360) - 180;
                        dr += d * us; gwr += us;
                    }
                    n++;
                }
                b.append(f(" – %s (%d h): U_ERA5 / U_Station %.2f, U² %.2f, höchstens %.1f gegen %.1f m/s, Richtung ERA5 %+.0f°",
                        namen[q], n, se / ss, se2 / ss2, me, ms, gwr > 0 ? dr / gwr : Double.NaN));
            }
        }
        A.info("Wind an Stationen des DWD gegen ERA5 (Auskunft, F36; Station auf 10 m mit z0 = 0,2 mm)", b.toString());
    }

    /** Richtung (°, woher) der Station: der Wert mit Ende am nächsten an t. */
    static double stationRichtung(com.dan.core.pruef.Windstation s, Instant t) {
        int i = java.util.Arrays.binarySearch(s.zeiten, t.getEpochSecond());
        if (i < 0) i = Math.min(s.zeiten.length - 1, -i - 1);
        return s.dd[i];
    }

    /** CSV: Zeit, je Ort Lauf und Beobachtung. */
    static void reihenSchreiben(Path p, double[] zeiten, List<Ort> orte, double[][] lauf) throws java.io.IOException {
        StringBuilder c = new StringBuilder("zeit");
        for (Ort o : orte) c.append(',').append(o.name).append("_lauf,").append(o.name).append("_beob");
        c.append('\n');
        for (int k = 0; k < zeiten.length; k++) {
            Instant z = Instant.ofEpochSecond((long) zeiten[k]);
            c.append(z);
            for (int q = 0; q < orte.size(); q++) c.append(String.format(java.util.Locale.ROOT, ",%.4f,%.4f", lauf[q][k], orte.get(q).pegel.wert(z)));
            c.append('\n');
        }
        java.nio.file.Files.writeString(p, c);
        A.info("Reihen", p.toString());
    }

    /**
     * Zerlegung der Sturmflut (P5e, Auskunft): derselbe Lauf ohne Antrieb gibt die Tide, Lauf mit Antrieb minus Lauf ohne
     * den Stau. Am beobachteten Scheitel im Fenster: Tide im Lauf gegen die Vorhersage aus der Analyse des Pegels, Stau im
     * Lauf gegen Beobachtung minus Vorhersage.
     */
    void zerlegen(Lauf.Einstellung mitAntrieb, double[] zeiten, List<Ort> orte, double[][] mit, List<String> gitterNamen) throws Exception {
        Lauf.Einstellung e = einstellung(sturmStart, false);
        e.stufen = mitAntrieb.stufen;
        e.fenster3 = mitAntrieb.fenster3;
        int n = zeiten.length;
        double[][] ohne = new double[orte.size()][n];
        long w = System.nanoTime();
        try (Lauf l = Lauf.bauen(e)) {
            for (int k = 0; k < n; k++) {
                l.bis((k + 1) * e.kopplung);
                for (int q = 0; q < orte.size(); q++) ohne[q][k] = l.hydro.gitter.get(orte.get(q).gitter).eta[orte.get(q).zelle];
            }
        }
        if (reihenDatei != null) reihenSchreiben(Path.of(reihenDatei + ".ohne.csv"), zeiten, orte, ohne);
        StringBuilder b = new StringBuilder();
        double sl = 0, sb = 0;
        for (int q = 0; q < orte.size(); q++) {
            Ort o = orte.get(q);
            int kb = -1;
            double mb = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < n; k++) {
                Instant z = Instant.ofEpochSecond((long) zeiten[k]);
                if (z.isBefore(scheitel0) || z.isAfter(scheitel1)) continue;
                double v = o.pegel.wert(z);
                if (v > mb) { mb = v; kb = k; }
            }
            if (kb < 0) continue;
            Instant z = Instant.ofEpochSecond((long) zeiten[kb]);
            double tideB = o.pegel.vorhersage(z), stauB = mb - tideB, tideL = ohne[q][kb], stauL = mit[q][kb] - ohne[q][kb];
            sl += stauL;
            sb += stauB;
            b.append(f("%s%s (%s) um %s: Tide %.2f gegen %.2f m, Stau %.2f gegen %.2f m (%+.0f %%)", b.length() > 0 ? "; " : "", o.name,
                    gitterNamen.get(o.gitter), uhr(z), tideL, tideB, stauL, stauB, 100 * (stauL / stauB - 1)));
        }
        A.info("Zerlegung am beobachteten Scheitel (Auskunft, Windschub " + Lauf.schubText(mitAntrieb.schub) + ")",
                b + f("; Stau im Mittel %+.0f %%; Lauf ohne Antrieb in %.0f s", 100 * (sl / sb - 1), (System.nanoTime() - w) / 1e9));
    }

    static float[] tiefen(Hydro h) {
        float[] t = new float[h.zellen];
        for (int c = 0; c < h.zellen; c++) t[c] = (float) (h.vol[c] / h.flaeche(c));
        return t;
    }

    // ------------------------------------------------------------------ Prüfsteine 5 und 6, Hilfen

    void bilanzUndNaN(Lauf l, String lauf) {
        StringBuilder b = new StringBuilder();
        double schlimmst = 0;
        for (int s = 0; s < l.hydro.stufen.size(); s++) {
            double x = l.bilanz(s);
            schlimmst = Math.max(schlimmst, Double.isNaN(x) ? Double.POSITIVE_INFINITY : x);
            b.append(f("%sStufe %d %.1e", b.length() > 0 ? ", " : "", s, x));
        }
        A.pruefe(schlimmst < GRENZE_BILANZ, "Prüfstein 5: Volumen je Stufe über den Lauf (" + lauf + "), Abgeschnittenes zählt als Fehler", b.toString(), "< 1e-5 (wie Fall 9)");
        long nan = 0;
        for (Hydro h : l.hydro.gitter) {
            for (double x : h.eta) if (!Double.isFinite(x)) nan++;
            for (double x : h.u) if (!Double.isFinite(x)) nan++;
        }
        A.pruefe(nan == 0, "Prüfstein 6: kein NaN in η und u am Ende (" + lauf + ")", nan + " nicht endlich", "keins");
    }

    static String bilanzen(Lauf l) {
        StringBuilder b = new StringBuilder();
        for (int s = 0; s < l.hydro.stufen.size(); s++) b.append(s > 0 ? " / " : "").append(f("%.1e", l.bilanz(s)));
        return b.toString();
    }

    static double[] zahlen(String v) {
        if (v == null) return null;
        String[] t = v.trim().split("\\s+");
        return new double[] {Double.parseDouble(t[0]), Double.parseDouble(t[1])};
    }

    static double mittel(double[] r) {
        double s = 0;
        int n = 0;
        for (double x : r) if (!Double.isNaN(x)) { s += x; n++; }
        return s / n;
    }

    static double winkel(double g) {
        double x = ((g + 180) % 360 + 360) % 360 - 180;
        return x;
    }

    static double sq(double x) { return x * x; }

    static double sekunden(Instant a, Instant b) { return (b.toEpochMilli() - a.toEpochMilli()) / 1000.0; }

    static Instant spaeter(Instant a, Instant b) { return a.isAfter(b) ? a : b; }

    static Instant frueher(Instant a, Instant b) { return a.isBefore(b) ? a : b; }

    static String zeit(Instant start, double t) { return start.plusMillis(Math.round(t * 1000)).toString(); }

    static String uhr(Instant z) { return z == null ? "–" : z.toString().replace("2026-", "").replace(":00Z", "Z"); }

    static String namen(List<Partialtide> l) {
        List<String> n = new ArrayList<>();
        for (Partialtide p : l) n.add(p.name);
        return String.join(", ", n);
    }
}
