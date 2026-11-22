package com.dan.core.demo;

import com.dan.core.Core;
import com.dan.core.archiv.Archiv;
import com.dan.core.archiv.Packer;
import com.dan.core.astro.FMoonPosition;
import com.dan.core.astro.FSun;
import com.dan.core.astro.Gestirne;
import com.dan.core.astro.Partialtide;
import com.dan.core.bilanz.Bilanz;
import com.dan.core.bilanz.Bilanzfehler;
import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.bus.Verdrahtungsfehler;
import com.dan.core.feld.Eintrag;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Subgrid;
import com.dan.core.gitter.Tidenfeld;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.gitter.Umrechnung;
import com.dan.core.pruef.Attest;
import com.dan.core.pruef.Bildvergleich;
import com.dan.core.pruef.Harmonik;
import com.dan.core.pruef.Pegel;
import com.dan.core.pruef.Statistik;
import com.dan.core.raum.Ebene;
import com.dan.core.raum.Erde;
import com.dan.core.raum.Utm;
import com.dan.core.takt.Takt;
import com.dan.core.zeit.Uhr;
import com.dan.core.zeit.Zeit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Kerns ohne Fenster und ohne Grafikkarte (Werkbuch HalligHooge Teil A, Phase P1). Endet mit „Alles in
 * Ordnung.“ oder mit der Zahl der Verstöße (Rückgabewert 1) und schreibt das Attest nach
 * {@code docs/status/P1_CoreCheck.md} (mit {@code --ohne-attest} nur auf die Konsole).
 * <p>
 * Die Grenzwerte standen fest, bevor gemessen wurde; [G] heißt gesetzt. Vergleichswerte von außen: UTM aus PROJ 9
 * (pyproj, EPSG:25832), Sonnen- und Mondstand aus astropy 8.0 (ohne Refraktion), Sonne und Mond nach den Beispielen
 * 25.b und 47.a bei Meeus, ΔE₀₀ nach den Prüfpaaren von Sharma u. a. 2005.
 */
public final class CoreCheck {
    private static final Attest A = new Attest("com.dan.core", Core.VERSION);

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        System.out.println("CoreCheck – com.dan.core " + Core.VERSION + " (Werkbuch HalligHooge Teil A, P1)");
        teil("1 Raum", CoreCheck::raum);
        teil("2 Zeit und Himmelsmechanik", CoreCheck::astro);
        teil("3 Gezeitenpotential und Partialtiden", CoreCheck::potential);
        teil("4 Felder und Katalog", CoreCheck::felder);
        teil("5 Gitter", CoreCheck::gitter);
        teil("6 Subgrid", CoreCheck::subgrid);
        teil("7 Umrechnung zwischen Gittern", CoreCheck::umrechnung);
        teil("8 Bus", CoreCheck::bus);
        teil("9 Takt", CoreCheck::takt);
        teil("10 Archiv", CoreCheck::archiv);
        teil("10b Archiv gepackt (D1)", CoreCheck::archivGepackt);
        teil("11 Bilanzen", CoreCheck::bilanzen);
        teil("12 Prüfrahmen", CoreCheck::pruefrahmen);
        teil("13 Feingelände (Kachelstapel, P3)", CoreCheck::gelaende);
        teil("14 Gebiet (Gitterkette, Packung, Bauwerke, P3d)", CoreCheck::gebiet);
        teil("15 Felder auf Gradrastern und Pegel (P5b)", CoreCheck::gradraster);
        teil("16 Bus: ein Feld auf mehreren Gittern, Antrieb im Intervall (P5c)", CoreCheck::busP5c);
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P1_CoreCheck.md"), "P1 – CoreCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    private interface Teil { void pruefe() throws Exception; }

    private static void teil(String name, Teil t) {
        A.teil(name);
        try {
            t.pruefe();
        } catch (Throwable e) {
            A.fehler(e);
        }
    }

    // ------------------------------------------------------------------ 1 Raum

    /** Länge, Breite → Ost, Nord, Konvergenz (Grad), Maßstab aus PROJ 9 (EPSG:4258 → EPSG:25832). */
    private static final double[][] PROJ = {
            {8.55, 54.57, 470907.2335, 6047036.2475, -0.366673519, 0.999610384224},
            {6.0, 53.2, 299626.2828, 5898721.3634, -2.402987586, 1.000092777399},
            {11.9, 55.8, 681771.0300, 6187626.4998, 2.399184892, 1.000005286474},
            {9.0, 0.0, 500000.0000, 0.0000, 0, 0.9996},
            {7.87, 54.24, 426357.2363, 6010814.0360, -0.917004242, 0.999666542672}};

    private static void raum() {
        double lage = 0, runde = 0, konv = 0, mass = 0;
        for (double[] p : PROJ) {
            double[] u = Utm.vorwaerts(p[0], p[1], 32), g = Utm.rueckwaerts(u[0], u[1], 32), fa = Utm.faktoren(p[0], p[1], 32);
            lage = Math.max(lage, Math.hypot(u[0] - p[2], u[1] - p[3]));
            runde = Math.max(runde, Math.max(Math.abs(g[0] - p[0]), Math.abs(g[1] - p[1])));
            konv = Math.max(konv, Math.abs(fa[0] - p[4]));
            mass = Math.max(mass, Math.abs(fa[1] - p[5]));
        }
        A.hoechstens("UTM 32 gegen PROJ, 5 Orte", lage, 1e-3, "m");
        A.hoechstens("UTM hin und zurück", runde, 1e-9, "°");
        A.hoechstens("Meridiankonvergenz gegen PROJ", konv, 1e-6, "°");
        A.hoechstens("Maßstab gegen PROJ", mass, 1e-8, "");
        double[] hans = Utm.rueckwaerts(470_779, 6_047_148, 32);
        A.hoechstens("Hanswarft O 470 779, N 6 047 148 → 8,548005° O, 54,570997° N", Math.max(Math.abs(hans[0] - 8.548005472), Math.abs(hans[1] - 54.570996904)), 1e-7, "°");
        Ebene e = new Ebene(32, 470_779, 6_047_148);
        A.pruefe(Math.abs(e.coriolis - 1.19e-4) <= 0.005e-4, "Coriolis-Parameter bei Hooge", f("%.4f·10⁻⁴ 1/s", e.coriolis * 1e4), "1,19·10⁻⁴ ± 0,005·10⁻⁴");
        A.hoechstens("Normalschwere bei 45° gegen GRS80 (9,806199 m/s²)", Erde.schwere(45) - 9.806199203, 1e-6, "m/s²");
        A.hoechstens("Oberfläche des Ellipsoids gegen GRS80 (510 065 621,7 km²), relativ", Erde.zonenflaeche(-90, 90, 360) / 510_065_621.724e6 - 1, 1e-9, "");
        double[] x = Erde.ecef(8.55, 54.57, 12.5), g = Erde.geodaetisch(x[0], x[1], x[2]);
        A.hoechstens("Erdfest hin und zurück", Math.max(Math.max(Math.abs(g[0] - 8.55), Math.abs(g[1] - 54.57)) * 111_000, Math.abs(g[2] - 12.5)), 1e-6, "m");
        A.hoechstens("Absenkung in 10 km gegen clipmap (r²/2R′ = 6,8278 m)", Erde.absenkung(10_000) - 1e8 / (2 * 6_371_000 / 0.87), 1e-9, "m");
        A.info("Kimm für ein Auge 2 m über dem Wasser", f("%.0f m", Erde.kimmweite(2)));
        A.info("Maßstab und Konvergenz an der Hanswarft", f("%.6f, %.4f° (24 km im Gitter sind %.1f m kürzer als in der Natur)", e.massstab, e.konvergenzGrad, e.wahr(24_000) - 24_000));
        float[] rel = new float[3];
        double px = e.x(470_907.2335), pz = e.z(6_047_036.2475);
        Ebene.relativ(px, 1.25, pz, px - 1000.125, 2.0, pz + 500.0625, rel, 0);
        double roh = Math.abs((double) (float) 6_047_036.2475 - 6_047_036.2475);
        A.pruefe(Math.abs(rel[0] - 1000.125) < 1e-4 && Math.abs(rel[2] + 500.0625) < 1e-4 && roh > 0.1,
                "Lage relativ zum Auge als float", f("Fehler %.1e m (Nord als float allein: %.2f m)", Math.abs(rel[0] - 1000.125), roh), "< 1·10⁻⁴ m");
    }

    // ------------------------------------------------------------------ 2 Zeit und Himmelsmechanik

    /** Zeit (UTC), dann Sonne Höhe, Azimut, Abstand vom Ort (AE), Mond Höhe, Azimut, Abstand vom Ort (km): astropy 8.0, Hooge. */
    private static final Object[][] ASTROPY = {
            {"2026-06-21T10:00:00Z", 54.8435, 143.5353, 1.016163, -9.4881, 76.7907, 386616.0},
            {"2026-12-05T13:30:00Z", 7.8615, 210.9096, 0.985507, -6.6546, 247.5928, 397039.3},
            {"2013-12-06T02:00:00Z", -45.8114, 60.0480, 0.985396, -51.6554, 353.2230, 367307.3}};

    private static void astro() {
        A.pruefe(Zeit.ttMinusUtc(Instant.parse("2026-11-11T00:00:00Z")) == 69.184 && Zeit.ttMinusUtc(Instant.parse("1999-06-01T00:00:00Z")) == 64.184,
                "TT − UTC", f("2026: %.3f s, 1999: %.3f s", Zeit.ttMinusUtc(Instant.parse("2026-11-11T00:00:00Z")), Zeit.ttMinusUtc(Instant.parse("1999-06-01T00:00:00Z"))),
                "69,184 s und 64,184 s");
        A.hoechstens("Julianisches Datum 2000-01-01 12:00 UTC = 2 451 545,0", Zeit.jd(Instant.parse("2000-01-01T12:00:00Z")) - 2_451_545.0, 1e-9, "d");
        FSun s = FSun.at(2_448_908.5);
        A.hoechstens("Sonne, Meeus 25.b (α 198,378121°, δ −7,783817°)", Math.max(Math.abs(s.rightAscension - 198.378121), Math.abs(s.declination + 7.783817)), 1e-3, "°");
        FMoonPosition m = FMoonPosition.at(2_448_724.5);
        A.hoechstens("Mond, Meeus 47.a (α 134,688470°, δ 13,768368°)", Math.max(Math.abs(m.rightAscension - 134.688470), Math.abs(m.declination - 13.768368)), 5e-4, "°");
        A.hoechstens("Mond, Meeus 47.a, Abstand 368 409,7 km", m.distanceKm - 368_409.7, 1, "km");
        double so = 0, mo = 0, ms = 0, sd = 0;
        for (Object[] z : ASTROPY) {
            Gestirne.Stand st = Gestirne.bei(Instant.parse((String) z[0]));
            Gestirne.Richtung rs = st.sonne(8.55, 54.57), rm = st.mond(8.55, 54.57);
            so = Math.max(so, Math.max(Math.abs(rs.hoeheGrad() - (double) z[1]), Math.abs(rs.azimutGrad() - (double) z[2]) * Math.cos(Math.toRadians(rs.hoeheGrad()))));
            sd = Math.max(sd, Math.abs(rs.abstand() / Gestirne.AE - (double) z[3]));
            mo = Math.max(mo, Math.max(Math.abs(rm.hoeheGrad() - (double) z[4]), Math.abs(rm.azimutGrad() - (double) z[5]) * Math.cos(Math.toRadians(rm.hoeheGrad()))));
            ms = Math.max(ms, Math.abs(rm.abstand() / 1000 - (double) z[6]));
        }
        A.hoechstens("Sonnenstand über Hooge gegen astropy, 3 Zeiten", so, 0.02, "°");
        A.hoechstens("Abstand der Sonne vom Ort gegen astropy", sd, 1e-5, "AE");
        A.hoechstens("Mondstand über Hooge gegen astropy, mit Parallaxe", mo, 0.05, "°");
        A.hoechstens("Abstand des Monds vom Ort gegen astropy", ms, 100, "km");
        double nah = Gestirne.bei(Instant.parse("2026-01-03T12:00:00Z")).sonneE(), fern = Gestirne.bei(Instant.parse("2026-07-05T12:00:00Z")).sonneE();
        A.pruefe(Math.abs(nah / fern - 1.069) < 0.002, "sun.E im Perihel gegen Aphel", f("%.1f und %.1f W/m², Verhältnis %.4f", nah, fern, nah / fern), "1,069 ± 0,002");
        Uhr uhr = new Uhr(Instant.parse("2026-03-20T06:00:00Z"));
        uhr.zeitraffer = 48;
        uhr.vor(60);
        uhr.zeitraffer = -12;
        uhr.vor(30);
        A.pruefe(uhr.sekunden() == 48 * 60 - 12 * 30 && uhr.jetzt().equals(Instant.parse("2026-03-20T06:42:00Z")) && uhr.t(uhr.jetzt()) == uhr.sekunden(),
                "Szenenzeit = Start + Zeitraffer · Laufzeit, auch rückwärts", uhr.jetzt().toString(), "2026-03-20T06:42:00Z");
    }

    // ------------------------------------------------------------------ 3 Gezeitenpotential und Partialtiden

    private static void potential() {
        // Winkelgeschwindigkeiten gegen die Konstanten des Packages tide (Grad je Stunde, nach Doodson)
        Object[][] soll = {{"M2", 28.9841042}, {"S2", 30.0}, {"N2", 28.4397295}, {"K2", 30.0821373}, {"K1", 15.0410686}, {"O1", 13.9430356}, {"M4", 57.9682084},
                {"MS4", 58.9841042}, {"MN4", 57.4238337}, {"M6", 86.9523127}};
        double dw = 0;
        for (Object[] z : soll) dw = Math.max(dw, Math.abs(Partialtide.nach((String) z[0]).gradJeStunde() - (double) z[1]));
        A.hoechstens("Winkelgeschwindigkeit von 10 Partialtiden gegen das Package tide", dw, 2e-7, "°/h");
        A.pruefe(Partialtide.EOT20.size() == 17, "Partialtiden von EOT20", Partialtide.EOT20.toString(), "17");
        // Knotenfaktoren gegen die geschlossenen Formeln von Schureman bei N = 0° und 180° (Neigung I = 23,452° ± 5,145°)
        double df = 0;
        for (double n : new double[] {0, 180}) {
            double i = Math.toRadians(23.452 + (n == 0 ? 5.145 : -5.145));
            double fm2 = Math.pow(Math.cos(i / 2), 4) / 0.9154, fo1 = Math.sin(i) * Math.pow(Math.cos(i / 2), 2) / 0.3800;
            df = Math.max(df, Math.max(Math.abs(Partialtide.M2.fu(n)[0] / fm2 - 1), Math.abs(Partialtide.O1.fu(n)[0] / fo1 - 1)));
        }
        A.hoechstens("Knotenfaktor f von M2 und O1 gegen Schureman, relativ", df, 5e-3, "");
        double n26 = Partialtide.knotenlaenge(Instant.parse("2026-07-01T00:00:00Z"));
        A.info("Mondknoten Mitte 2026", f("N = %.1f°, f(M2) = %.3f, u(M2) = %.2f°, f(K1) = %.3f, f(O1) = %.3f", n26, Partialtide.M2.fu(n26)[0], Partialtide.M2.fu(n26)[1],
                Partialtide.K1.fu(n26)[0], Partialtide.O1.fu(n26)[0]));

        // Gleichgewichtstide aus den Ephemeriden über 369 Tage, stündlich: am Äquator (halbtägig) und auf 45° N (eintägig)
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        int n = 369 * 24;
        double[] aeq = new double[n], mitte = new double[n];
        for (int k = 0; k < n; k++) {
            Gestirne.Stand st = Gestirne.bei(start.plusSeconds(3600L * k));
            aeq[k] = st.gleichgewicht(0, 0);
            mitte[k] = st.gleichgewicht(0, 45.1924);                     // geozentrisch 45°: sin 2φ = 1
        }
        List<Partialtide> kandidaten = List.of(Partialtide.M2, Partialtide.S2, Partialtide.N2, Partialtide.K2, Partialtide.K1, Partialtide.O1, Partialtide.P1,
                Partialtide.Q1, Partialtide.MF, Partialtide.MM, Partialtide.SSA, Partialtide.ZWEI_N2, Partialtide.J1, Partialtide.NU2, Partialtide.MU2);
        Harmonik.Ergebnis ea = Harmonik.analyse(start, 3600, aeq, kandidaten, 1), em = Harmonik.analyse(start, 3600, mitte, kandidaten, 1);
        A.pruefe(ea.ausgelassen().isEmpty(), "Rayleigh über 369 Tage", ea.tiden().size() + " Tiden getrennt, ausgelassen " + ea.ausgelassen(), "alle 15 trennbar");
        double amp = 0, pha = 0;
        StringBuilder txt = new StringBuilder();
        for (String name : new String[] {"M2", "S2", "N2", "K1", "O1"}) {
            Harmonik.Tide t = (name.endsWith("1") ? em : ea).tide(name);
            double soll2 = t.tide().gleichgewicht, g = t.phaseGrad() > 180 ? t.phaseGrad() - 360 : t.phaseGrad();
            amp = Math.max(amp, Math.abs(t.amplitude() / soll2 - 1));
            pha = Math.max(pha, Math.abs(g));
            txt.append(f("%s %.4f m (%+.1f %%), %+.2f°; ", name, t.amplitude(), (t.amplitude() / soll2 - 1) * 100, g));
        }
        A.info("Gleichgewichtstide aus Sonne und Mond", txt.toString().trim());
        A.hoechstens("Amplitude von M2, S2, N2, K1, O1 gegen die Gleichgewichtstide (Cartwright), relativ [G]", amp, 0.02, "");
        A.hoechstens("Phase von M2, S2, N2, K1, O1 gegen das astronomische Argument [G]", pha, 1, "°");
        A.info("Rest der Analyse (Äquator, 45° N)", f("%.1f mm, %.1f mm bei Hub %.2f m", ea.restRms() * 1000, em.restRms() * 1000, max(aeq) - min(aeq)));
        A.pruefe(Math.abs(Gestirne.LOVE - 0.69) < 0.005, "Faktor der festen Erde 1 + k₂ − h₂", f("%.3f", Gestirne.LOVE), "0,69 ± 0,005");
    }

    // ------------------------------------------------------------------ 4 Felder und Katalog

    private static void felder() {
        Katalog k = Katalog.grundstufe();
        A.pruefe(k.namen().size() == 48 && k.gruppen().size() == 11 && k.kennt("air.rain") && k.kennt("aeol.q") && k.kennt("tide.eq") && k.kennt("air.taux") && k.kennt("air.tauy") && k.kennt("air.sw") && k.kennt("water.sst"),
                "Katalog der Grundstufe (Werkbuch 5.3, mit den vier Beiträgen aus F18, tide.eq aus P5c, air.taux, air.tauy aus P5e, bed.fluff aus P9c, air.sw, air.lw, water.sst aus P10b)",
                k.namen().size() + " Felder in " + k.gruppen().size() + " Gruppen " + k.gruppen(), "48 Felder, 11 Gruppen");
        Gitter g = Gitter.utm("T", 32, 0, 0, 20, 7, 5);
        Feld eta = new Feld(k.verlange("water.eta"), g, Feld.Zahl.DOUBLE), q = new Feld(k.verlange("water.q"), g, Feld.Zahl.FLOAT),
                bc = new Feld(k.verlange("tide.bc"), g, Feld.Zahl.DOUBLE), n = new Feld(k.verlange("air.n"), g, Feld.Zahl.FLOAT),
                rip = new Feld(k.verlange("bed.ripple"), g, Feld.Zahl.FLOAT), sonne = new Feld(k.verlange("sun.dir"), null, Feld.Zahl.DOUBLE),
                spek = new Feld(k.verlange("waves.spec"), g, Feld.Zahl.FLOAT, 28 * 36);
        A.pruefe(eta.laenge == 35 && q.laenge == 8 * 5 + 7 * 6 && bc.laenge == 2 * 24 && n.laenge == 35 * 64 && rip.laenge == 105 && sonne.laenge == 3 && spek.laenge == 35 * 1008,
                "Länge nach Ort, Schichten und Komponenten", f("Zelle %d, Kante %d, Rand %d, Säule %d, 3 Komponenten %d, global %d, Spektrum %d", eta.laenge, q.laenge, bc.laenge,
                        n.laenge, rip.laenge, sonne.laenge, spek.laenge), "35, 82, 48, 2240, 105, 3, 35 280");
        A.pruefe(eta.d != null && eta.f == null && q.f != null && q.d == null && q.kanteY(6, 5) == q.laenge - 1 && q.kanteX(7, 4) == 8 * 5 - 1,
                "Referenz in double, Betrieb in float; Kanten erst quer zu x, dann quer zu y", "ja", "");
        boolean tabelle = false, falsch = false;
        try { new Feld(k.verlange("ground.hyps"), g, Feld.Zahl.FLOAT); } catch (IllegalArgumentException e) { tabelle = true; }
        try { new Feld(k.verlange("air.n"), g, Feld.Zahl.FLOAT, 32); } catch (IllegalArgumentException e) { falsch = true; }
        A.pruefe(tabelle && falsch, "ground.hyps ist kein Feld; air.n hat 64 Schichten und keine andere Zahl", "beides abgelehnt", "");
        int[] rufe = new int[2];
        eta.karte = new Feld.Kartenseite() {
            @Override public void hoch(Feld x) { rufe[0]++; }
            @Override public void herunter(Feld x) { rufe[1]++; }
        };
        eta.hoch();
        eta.herunter();
        q.hoch();
        A.pruefe(rufe[0] == 1 && rufe[1] == 1, "Abgleich mit der Karte ist ausdrücklich (hoch, herunter); ohne Karte geschieht nichts", "je ein Ruf", "");
        List<String> teile = new ArrayList<>();
        for (Eintrag e : k.beitraege("bed.dz")) teile.add(e.name());
        for (Eintrag e : k.beitraege("surf.z0")) teile.add(e.name());
        A.pruefe(k.mehrfachGeschrieben().isEmpty() && teile.equals(List.of("bed.dz.sediment", "bed.dz.aeolian", "surf.z0.sediment", "surf.z0.vegetation"))
                        && k.verlange("bed.dz").verbund() == Eintrag.Verbund.SUMME && k.verlange("surf.z0").verbund() == Eintrag.Verbund.MAXIMUM && k.verlange("bed.dz").darfSchreiben("core.verbund.bed.dz"),
                "Ein Schreiber je Feld (F18 A): bed.dz und surf.z0 bildet der Kern aus Beiträgen", String.join(", ", teile), "kein Feld mit zwei Schreibern");
    }

    // ------------------------------------------------------------------ 5 Gitter

    static final Gitter G0 = Gitter.geografisch("G0", -15, 43, 1.0 / 12, 1.0 / 18, 336, 378), G1 = Gitter.utmHuelle("G1", 32, 6.0, 53.2, 9.1, 55.8, 1000, 1000),
            G2 = Gitter.utmBereich("G2", 32, 428_000, 6_011_000, 502_000, 6_097_000, 200), G3 = Gitter.utmBereich("G3", 32, 454_000, 6_025_000, 478_000, 6_057_000, 20),
            G4 = Gitter.utm("G4", 32, 469_760, 6_046_120, 2, 1000, 1000);

    private static void gitter() {
        A.pruefe(G0.nx == 336 && G0.ny == 378 && Math.abs(G0.x1() - 13) < 1e-9 && Math.abs(G0.y1() - 64) < 1e-9, "G0 Schelf, 15° W–13° O, 43–64° N", G0.nx + " × " + G0.ny, "336 × 378");
        A.pruefe(G1.nx >= 190 && G1.nx <= 215 && G1.ny >= 280 && G1.ny <= 295, "G1 Deutsche Bucht, Hülle in UTM 32 auf volle km", G1.toString(), "rund 200 × 290");
        A.pruefe(G2.nx == 370 && G2.ny == 430, "G2 Nordfriesland, 200 m (seit P5b nach F30 A bis O 502 km, N 6 097 km)", G2.nx + " × " + G2.ny, "370 × 430");
        A.pruefe(G3.nx == 1200 && G3.ny == 1600, "G3 Szene Hooge, 20 m", G3.nx + " × " + G3.ny, "1200 × 1600");
        A.pruefe(G3.passtIn(G2) == 10 && G4.passtIn(G3) == 10 && G2.passtIn(G1) == 5 && G3.passtIn(G0) == 0,
                "Schachteln: G3 in G2 im Verhältnis 10, G2 in G1 im Verhältnis 5, G4 (1000² Zellen zu 2 m, F19 A) in G3 im Verhältnis 10",
                "G3/G2 " + G3.passtIn(G2) + ", G4/G3 " + G4.passtIn(G3) + ", G2/G1 " + G2.passtIn(G1), "10, 10, 5");
        A.hoechstens("Fläche von G0 gegen den Streifen des Ellipsoids, relativ", G0.flaeche() / Erde.zonenflaeche(43, 64, 28) - 1, 1e-12, "");
        A.info("Zellen", f("G0 %,d · G1 %,d · G2 %,d · G3 %,d · G4 %,d", G0.zellen(), G1.zellen(), G2.zellen(), G3.zellen(), G4.zellen()));
        A.info("Zelle von G0 im Süden und im Norden", f("%.1f × %.1f km und %.1f × %.1f km", G0.breite(0) / 1000, G0.hoehe(0) / 1000, G0.breite(G0.ny - 1) / 1000, G0.hoehe(G0.ny - 1) / 1000));
        double[] p = G2.von(G0, 8.55, 54.57);
        A.hoechstens("Punkt von G0 nach G2 (über Länge und Breite)", Math.hypot(p[0] - 470_907.2335, p[1] - 6_047_036.2475), 1e-3, "m");
    }

    // ------------------------------------------------------------------ 6 Subgrid

    private static void subgrid() {
        Gitter g = Probe.G;
        int fein = Probe.FEIN, nxf = g.nx * fein, nyf = g.ny * fein;
        double[] z = Probe.watt(2026);
        long t0 = System.nanoTime();
        Subgrid s = Subgrid.bauen(g, fein, Subgrid.aus(z, nxf, nyf));
        double dauer = (System.nanoTime() - t0) * 1e-9;
        int stufen = s.stufen;
        // (a) an den Stufen, (b) über der höchsten und unter der tiefsten
        double anStufe = 0, aussen = 0, zwischen = 0, umkehr = 0;
        double groessteSpanne = 0;
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++) {
                int c = g.index(i, j);
                double spanne = s.hoechste(c) - s.grund(c), schranke = s.zellflaeche * spanne / (4.0 * (stufen - 1) * (stufen - 1));
                groessteSpanne = Math.max(groessteSpanne, spanne);
                for (int l = 0; l < stufen; l++) {
                    double eta = s.stufe[c * stufen + l], fe = Probe.volumenFein(z, i, j, eta);
                    anStufe = Math.max(anStufe, Math.abs(s.volumen(c, eta) - fe) / Math.max(fe, s.zellflaeche));
                    anStufe = Math.max(anStufe, Math.abs(s.volumen[c * stufen + l] - fe) / Math.max(fe, s.zellflaeche));
                }
                for (double eta : new double[] {s.grund(c) - 0.1, s.hoechste(c) + 0.5, s.hoechste(c) + 3}) {
                    double fe = Probe.volumenFein(z, i, j, eta);
                    aussen = Math.max(aussen, Math.abs(s.volumen(c, eta) - fe) / Math.max(fe, s.zellflaeche));
                }
                for (int q = 0; q < fein * fein; q++) {                                          // an jedem Knick der Kurve: jede Pixelhöhe der Zelle
                    double eta = z[i * fein + q % fein + nxf * (j * fein + q / fein)], v = s.volumen(c, eta);
                    if (schranke > 0) zwischen = Math.max(zwischen, Math.abs(v - Probe.volumenFein(z, i, j, eta)) / schranke);
                    if (v > 0) umkehr = Math.max(umkehr, Math.abs(s.eta(c, v) - eta));
                }
                for (int q = 1; q < 50; q++) {
                    double eta = s.grund(c) + spanne * q / 50, v = s.volumen(c, eta);
                    if (schranke > 0) zwischen = Math.max(zwischen, Math.abs(v - Probe.volumenFein(z, i, j, eta)) / schranke);
                    umkehr = Math.max(umkehr, Math.abs(s.eta(c, v) - eta));
                }
                umkehr = Math.max(umkehr, Math.abs(s.eta(c, s.volumen(c, s.hoechste(c) + 2)) - (s.hoechste(c) + 2)));
            }
        A.hoechstens("Volumen an den Stufen gegen die Summe über die Pixel, relativ", anStufe, 1e-12, "");
        A.hoechstens("Volumen unter der tiefsten und über der höchsten Stufe, relativ", aussen, 1e-12, "");
        A.hoechstens("Volumen zwischen den Stufen an jeder Pixelhöhe, Anteil der Schranke A·Spanne/(4·11²)", zwischen, 1, "");
        A.hoechstens("Umkehrung η(V(η))", umkehr, 1e-9, "m");
        // (e) ganzes Gebiet bei ebenem Wasserspiegel
        double gebiet = 0, flaeche = g.zellen() * s.zellflaeche;
        StringBuilder txt = new StringBuilder();
        for (int q = 0; q <= 40; q++) {
            double eta = -2.5 + 0.125 * q, tab = 0, fe = 0;
            for (int j = 0; j < g.ny; j++)
                for (int i = 0; i < g.nx; i++) {
                    tab += s.volumen(g.index(i, j), eta);
                    fe += Probe.volumenFein(z, i, j, eta);
                }
            gebiet = Math.max(gebiet, Math.abs(tab - fe) / flaeche);
            if (q % 10 == 0 || q == 22) txt.append(f("η %+.2f m: %,.0f m³ (%+.3f %%); ", eta, fe, fe > 0 ? (tab / fe - 1) * 100 : 0));
        }
        A.hoechstens("Volumen des Gebiets bei ebenem Spiegel, 41 Stände: Fehler als mittlere Tiefe [G]", gebiet, 1e-3, "m");
        A.info("Volumen des Gebiets (Abweichung der Tabelle)", txt.toString().trim());
        // (f) der Priel ist schmaler als eine Zelle und führt Wasser
        double eta = -1.0;
        int nassGrob = 0;
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++) if (Probe.volumenFein(z, i, j, 50) / s.zellflaeche > 50 - eta) nassGrob++;            // mittlere Höhe der Zelle unter η
        boolean[] erreicht = new boolean[g.zellen()];
        List<Integer> offen = new ArrayList<>();
        for (int j = 0; j < g.ny; j++) if (s.leitwert(s.kanteX(0, j), eta) > 0) { erreicht[g.index(0, j)] = true; offen.add(g.index(0, j)); }
        boolean durch = false;
        int nasseKanten = 0;
        while (!offen.isEmpty()) {
            int c = offen.remove(offen.size() - 1), i = c % g.nx, j = c / g.nx;
            if (i == g.nx - 1 && s.leitwert(s.kanteX(g.nx, j), eta) > 0) durch = true;
            int[][] nachbar = {{i + 1, j, s.kanteX(i + 1, j)}, {i - 1, j, s.kanteX(i, j)}, {i, j + 1, s.kanteY(i, j + 1)}, {i, j - 1, s.kanteY(i, j)}};
            for (int[] nb : nachbar) {
                if (nb[0] < 0 || nb[1] < 0 || nb[0] >= g.nx || nb[1] >= g.ny || s.leitwert(nb[2], eta) <= 0) continue;
                int d = g.index(nb[0], nb[1]);
                if (!erreicht[d]) { erreicht[d] = true; offen.add(d); }
            }
        }
        for (int e = 0; e < s.kanten; e++) if (s.leitwert(e, eta) > 0) nasseKanten++;
        A.pruefe(nassGrob == 0, "Prüfstück: der Priel (7 m breit, Sohle −2,0 m) ist schmaler als eine Zelle – bei η = −1,0 m liegt keine Zelle im Mittel unter Wasser",
                nassGrob + " Zellen mit mittlerer Höhe unter η", "0");
        A.pruefe(durch, "Der Priel führt bei η = −1,0 m Wasser von West nach Ost", (durch ? "durchgehend" : "unterbrochen") + ", " + nasseKanten + " Kanten mit Leitwert", "durchgehend");
        // (g) Leitwert gegen die Summe über die Pixel, dicht abgetastet: 400 Wasserstände je Kante bis 1,5 m über die höchste Stufe
        double kMax = 0, kSumme = 0, filmMax = 0, hFilm = 0;
        int kZahl = 0, filmZahl = 0, filmUeber = 0;
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i <= g.nx; i++) {
                int e = s.kanteX(i, j);
                double unten = s.kStufe[e * stufen], oben = s.kStufe[e * stufen + stufen - 1];
                for (int q = 1; q <= 400; q++) {
                    double h = unten + (oben - unten + 1.5) * q / 400, fe = Probe.leitwertFeinX(z, i, j, h, Subgrid.MANNING);
                    if (fe <= 0) continue;
                    double tief = Math.pow(fe / s.kWeite[e], 0.6), rel = Math.abs(s.leitwert(e, h) / fe - 1);
                    if (tief < Subgrid.FILM) {
                        hFilm = Math.max(hFilm, Math.abs(s.wirksameTiefe(e, h) - tief));
                        filmMax = Math.max(filmMax, rel);
                        filmZahl++;
                        if (rel > 0.05) filmUeber++;
                        continue;
                    }
                    kMax = Math.max(kMax, rel);
                    kSumme += rel;
                    kZahl++;
                }
            }
        A.hoechstens("Leitwert der Kanten gegen die Summe über die Pixel ab 1 cm wirksamer Tiefe, relativ [G]", kMax, 0.05, "");
        A.hoechstens("Wirksame Tiefe der Kanten im Film unter 1 cm [G]", hFilm, 5e-4, "m");
        A.info("Leitwert der Kanten", f("im Mittel %.2f %% daneben über %,d Proben; im Film unter 1 cm bis %.0f %% (%d von %,d Proben über 5 %%)", 100 * kSumme / Math.max(1, kZahl), kZahl,
                100 * filmMax, filmUeber, filmZahl));
        // (g2) Querschnitt der Kanten (P4, F25): an den Stufen genau, dazwischen gegen die Summe über die Pixel
        double qStufe = 0, qMax = 0, qFilm = 0;
        int qZahl = 0;
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i <= g.nx; i++) {
                int e = s.kanteX(i, j);
                for (int l = 0; l < stufen; l++) qStufe = Math.max(qStufe, Math.abs(s.kQuer[e * stufen + l] - Probe.querschnittFeinX(z, i, j, s.kStufe[e * stufen + l])));
                double unten = s.kStufe[e * stufen], oben = s.kStufe[e * stufen + stufen - 1];
                for (int q = 1; q <= 400; q++) {
                    double h = unten + (oben - unten + 1.5) * q / 400, fe = Probe.querschnittFeinX(z, i, j, h), d = Math.abs(s.querschnitt(e, h) - fe);
                    if (fe < 0.01) { qFilm = Math.max(qFilm, d); continue; }
                    qMax = Math.max(qMax, d / fe);
                    qZahl++;
                }
            }
        A.hoechstens("Querschnitt der Kanten (mittlere benetzte Tiefe h_A) an den Stufen gegen die Summe über die Pixel (P4, F25)", qStufe, 1e-12, "m");
        A.hoechstens(f("Querschnitt der Kanten zwischen den Stufen ab 1 cm mittlerer Tiefe, relativ [G] (%,d Proben)", qZahl), qMax, 0.05, "");
        A.hoechstens("Querschnitt der Kanten im Film unter 1 cm [G]", qFilm, 5e-3, "m");
        // (h) neu backen, wo sich der Boden ändert
        double[] z2 = z.clone();
        for (int j = 5 * fein + 3; j < 9 * fein - 2; j++) for (int i = 7 * fein + 1; i < 12 * fein + 4; i++) z2[i + nxf * j] -= 0.37;
        Subgrid frisch = Subgrid.bauen(g, fein, Subgrid.aus(z2, nxf, nyf));
        System.arraycopy(z2, 0, z, 0, z.length);                                                       // s liest weiter aus z
        s.neu(7, 5, 13, 9);
        boolean gleich = java.util.Arrays.equals(s.stufe, frisch.stufe) && java.util.Arrays.equals(s.volumen, frisch.volumen) && java.util.Arrays.equals(s.flaeche, frisch.flaeche)
                && java.util.Arrays.equals(s.kStufe, frisch.kStufe) && java.util.Arrays.equals(s.kTiefe, frisch.kTiefe) && java.util.Arrays.equals(s.kQuer, frisch.kQuer)
                && java.util.Arrays.equals(s.kWeite, frisch.kWeite);
        A.pruefe(gleich, "Neu gebacken, wo sich der Boden geändert hat (6 × 4 Zellen): gleich dem Neubau", gleich ? "bitgleich" : "verschieden", "bitgleich");
        // (i) Packung für die Karte
        Subgrid.Packung p = s.packen();
        double pack = 0;
        for (int c = 0; c < g.zellen(); c++) {
            double spanne = s.hoechste(c) - s.grund(c);
            for (int q = 0; q <= 50; q++) {
                double h = s.grund(c) + (spanne + 1) * q / 50;
                pack = Math.max(pack, Math.abs(p.volumen(c, h) - s.volumen(c, h)) / (s.zellflaeche * Math.max(1, spanne)));
            }
        }
        A.hoechstens("Volumen aus der Packung (float16) gegen die Tabelle in double, relativ zu Zellfläche · Spanne [G]", pack, 2e-3, "");
        double packK = 0;
        for (int e = 0; e < s.kanten; e++) {
            double unten = s.kStufe[e * stufen], spanne = s.kStufe[e * stufen + stufen - 1] - unten;
            for (int q = 0; q <= 50; q++) {
                double h = unten + (spanne + 1) * q / 50;
                packK = Math.max(packK, Math.max(Math.abs(p.querschnitt(e, h) - s.querschnitt(e, h)), Math.abs(p.wirksameTiefe(e, h) - s.wirksameTiefe(e, h))) / Math.max(1, spanne + 1));
            }
        }
        A.hoechstens("Querschnitt und wirksame Tiefe der Kanten aus der Packung (float16) gegen double, relativ zur Spanne [G]", packK, 2e-3, "");
        double jeZelle = p.bytes() / (double) g.zellen(), g3 = G3.zellen() * (4.0 * 5 + 2.0 * 108) / (1 << 20);
        A.info("Größe der Packung", f("%.0f Byte je Zelle (mit Randkanten); G3 mit 1,92 Mio. Zellen: %.0f MB", jeZelle, g3));
        A.info("Backen", f("%d Zellen mit %d Pixeln und %d Kanten in %.2f s; größte Spanne in einer Zelle %.2f m", g.zellen(), fein * fein, s.kanten, dauer, groessteSpanne));
        A.befund(f("Die Subgrid-Tabellen von G3 brauchen auf der Karte %.0f MB (Zelle: Stufen, V, A; zwei Kanten: Stufen, wirksame Tiefe, Querschnitt – Version 2 seit P4), "
                + "nicht 160 MB wie in Abschnitt 11 geschätzt. Das Budget von 1,5 GB hält.", g3));
    }

    // ------------------------------------------------------------------ 7 Umrechnung

    private static void umrechnung() {
        Katalog k = Katalog.grundstufe();
        Eintrag h = k.verlange("water.h");
        Gitter fein = Gitter.utm("fein", 32, 468_000, 6_046_000, 20, 60, 40), grob = Gitter.utm("grob", 32, 466_000, 6_044_000, 200, 30, 25);
        SplittableRandom r = new SplittableRandom(7);
        Feld a = new Feld(h, fein, Feld.Zahl.DOUBLE), b = new Feld(h, grob, Feld.Zahl.DOUBLE), c = new Feld(h, fein, Feld.Zahl.DOUBLE);
        for (int i = 0; i < a.laenge; i++) a.d[i] = r.nextDouble() * 3;
        Umrechnung auf = Umrechnung.bauen(fein, grob), ab = Umrechnung.bauen(grob, fein);
        auf.menge(a, b);
        double erhalt = Math.abs(b.summe() / a.summe() - 1);
        for (int i = 0; i < b.laenge; i++) b.d[i] = r.nextDouble() * 500;
        ab.menge(b, c);
        double innen = 0;                                                                              // die Menge der groben Zellen, die das feine Gitter überdeckt
        for (int j = 10; j < 14; j++) for (int i = 10; i < 16; i++) innen += b.d[grob.index(i, j)];
        erhalt = Math.max(erhalt, Math.abs(c.summe() / innen - 1));
        A.hoechstens("Menge bleibt erhalten: Schachtel fein → grob und grob → fein, relativ", erhalt, 1e-12, "");
        A.pruefe(auf.verlust == 0 && Math.abs(ab.verlust - (1 - 24.0 / 750)) < 1e-12, "Anteil außerhalb des Ziels", f("fein → grob %.3f, grob → fein %.3f", auf.verlust, ab.verlust), "0 und 0,968");
        // geographisch → UTM: ein Stück von G0 in ein UTM-Gitter, das es ganz enthält
        Gitter geo = Gitter.geografisch("geo", 8.0, 54.2, 1.0 / 12, 1.0 / 18, 9, 10), utm = Gitter.utm("utm", 32, 380_000, 5_980_000, 1000, 160, 130);
        Feld ga = new Feld(h, geo, Feld.Zahl.DOUBLE), ub = new Feld(h, utm, Feld.Zahl.DOUBLE);
        for (int i = 0; i < ga.laenge; i++) ga.d[i] = 5 + r.nextDouble() * 20;
        Umrechnung gu = Umrechnung.bauen(geo, utm, 8);
        gu.menge(ga, ub);
        double m1 = Math.abs(ub.summe() / ga.summe() - 1);
        gu.dichte(ga, ub);
        double ig = 0, iu = 0;
        for (int j = 0; j < geo.ny; j++) for (int i = 0; i < geo.nx; i++) ig += ga.d[geo.index(i, j)] * geo.flaeche(j);
        for (int j = 0; j < utm.ny; j++) for (int i = 0; i < utm.nx; i++) iu += ub.d[utm.index(i, j)] * utm.flaeche(j);
        A.hoechstens("Menge und Flächenintegral bleiben erhalten: geographisch → UTM, relativ", Math.max(m1, Math.abs(iu / ig - 1)), 1e-12, "");
        // Zustand bilinear: eine Ebene bleibt eine Ebene
        for (int j = 0; j < grob.ny; j++) for (int i = 0; i < grob.nx; i++) b.d[grob.index(i, j)] = 1.5 + 2e-4 * grob.x(i) - 3e-4 * grob.y(j);
        ab.zustand(b, c);
        double eb = 0;
        for (int j = 0; j < fein.ny; j++) for (int i = 0; i < fein.nx; i++) eb = Math.max(eb, Math.abs(c.d[fein.index(i, j)] - (1.5 + 2e-4 * fein.x(i) - 3e-4 * fein.y(j))));
        A.hoechstens("Zustand bilinear: eine Ebene im groben Gitter bleibt im feinen dieselbe", eb, 1e-9, "m");
        Feld gz = new Feld(h, geo, Feld.Zahl.DOUBLE), uz = new Feld(h, utm, Feld.Zahl.DOUBLE);
        for (int j = 0; j < geo.ny; j++) for (int i = 0; i < geo.nx; i++) gz.d[geo.index(i, j)] = 0.3 * geo.x(i) - 0.2 * geo.y(j);
        uz.fuellen(Double.NaN);
        gu.zustand(gz, uz);
        double eg = 0;
        int drin = 0;
        for (int j = 0; j < utm.ny; j++)
            for (int i = 0; i < utm.nx; i++) {
                int q = utm.index(i, j);
                double[] ll = utm.grad(utm.x(i), utm.y(j));
                boolean kern = ll[0] > geo.x(0) && ll[0] < geo.x(geo.nx - 1) && ll[1] > geo.y(0) && ll[1] < geo.y(geo.ny - 1);
                if (gu.innen[q]) drin++; else if (!Double.isNaN(uz.d[q])) eg = 1;
                if (kern) eg = Math.max(eg, Math.abs(uz.d[q] - (0.3 * ll[0] - 0.2 * ll[1])));
            }
        A.hoechstens("Zustand geographisch → UTM: linear in Länge und Breite; außerhalb bleibt das Ziel unberührt", eg, 1e-9, "");
        A.info("Zellen des UTM-Gitters in der geographischen Quelle", drin + " von " + utm.zellen());
    }

    // ------------------------------------------------------------------ 8 Bus

    /** Ein Modul mit beliebigem Namen und Anschlüssen, das nichts rechnet – für die Verdrahtung. */
    private static Modul stumm(String name, java.util.function.Consumer<Anmeldung> anschluesse) {
        return new Modul() {
            @Override public String name() { return name; }
            @Override public void anmelden(Anmeldung a) { anschluesse.accept(a); }
            @Override public void beginn(double t0, Bus bus) { }
            @Override public void schritt(double t0, double t1, Bus bus) { }
        };
    }

    /** Schreibt {@code waves.hs} = 3 + 0,25·t in jeder Zelle: linear in der Zeit. */
    private static final class Linear implements Modul {
        final Gitter g;
        Linear(Gitter g) { this.g = g; }
        @Override public String name() { return "waves"; }
        @Override public void anmelden(Anmeldung a) { a.kopplung(1200).schreibt("waves.hs", "m", Ort.ZELLE, g, Feld.Zahl.DOUBLE); }
        @Override public void beginn(double t0, Bus bus) { lege(bus, t0); }
        @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }
        private void lege(Bus bus, double t) {
            Feld f = bus.schreibfeld(this, "waves.hs");
            for (int j = 0; j < g.ny; j++) for (int i = 0; i < g.nx; i++) f.d[g.index(i, j)] = 3 + 0.25 * t + 1e-3 * g.x(i) - 2e-3 * g.y(j);
            bus.lege(this, "waves.hs", t);
        }
    }

    private static void bus() {
        Katalog k = Katalog.grundstufe();
        Gitter g = Probe.G, grob = Gitter.utm("grob", 32, 468_000, 6_046_000, 80, 6, 4);
        // Mängel: jeder für sich, dann alle auf einmal
        String[][] faelle = {
                {"kein Schreiber", "liest water.eta: kein Schreiber angemeldet"},
                {"zwei Schreiber", "water.h: zwei Schreiber (hydro, hydro.neben)"},
                {"falsche Einheit", "liest water.h in [cm], der Katalog sagt [m]"},
                {"falscher Ort", "liest water.q am Ort ZELLE, der Katalog sagt KANTE"},
                {"nicht im Katalog", "water.temp: steht nicht im Katalog"},
                {"fremder Schreiber", "atmos schreibt water.eta: Schreiber laut Katalog ist hydro"},
                {"anderes Gitter am Rand", "liest tide.bc auf grob"}};
        int erkannt = 0;
        List<String> alle = new ArrayList<>();
        for (int fall = 0; fall <= faelle.length; fall++) {
            Bus b = new Bus(k);
            int w = fall;
            boolean jeder = fall == faelle.length;
            b.anmelden(stumm("tide", a -> a.kopplung(60).schreibt("tide.bc", "m, m²/s", Ort.RAND, g, Feld.Zahl.DOUBLE)));
            if (w != 0) b.anmelden(stumm("hydro", a -> {
                a.kopplung(20).schreibt("water.h", "m", Ort.ZELLE, g, Feld.Zahl.FLOAT).schreibt("water.q", "m²/s", Ort.KANTE, g, Feld.Zahl.DOUBLE);
                a.schreibt("water.eta", "m NHN", Ort.ZELLE, g, Feld.Zahl.DOUBLE);
                if (w == 6 || jeder) a.liest("tide.bc", "m, m²/s", Ort.RAND, grob);
            }));
            b.anmelden(stumm("sediment", a -> {
                a.kopplung(20);
                if (w == 0) a.liest("water.eta", "m NHN", Ort.ZELLE, g);
                if (w == 2 || jeder) a.liest("water.h", "cm", Ort.ZELLE, g);
                if (w == 3 || jeder) a.liest("water.q", "m²/s", Ort.ZELLE, g);
                if (w == 4 || jeder) a.liest("water.temp", "K", Ort.ZELLE, g);
            }));
            if (w == 1 || jeder) b.anmelden(stumm("hydro.neben", a -> a.kopplung(60).schreibt("water.h", "m", Ort.ZELLE, g, Feld.Zahl.FLOAT)));
            if (w == 5 || jeder) b.anmelden(stumm("atmos", a -> a.kopplung(300).schreibt("water.eta", "m NHN", Ort.ZELLE, g, Feld.Zahl.DOUBLE)));
            try {
                b.verdrahten();
            } catch (Verdrahtungsfehler e) {
                if (jeder) alle.addAll(e.maengel);
                else if (e.maengel.stream().anyMatch(m -> m.contains(faelle[w][1]))) erkannt++;
                else A.befund("Fall „" + faelle[w][0] + "“ nicht erkannt: " + e.maengel);
                boolean tot = false;
                try { b.felder(); b.schreibfeld(b.module().get(0), "tide.bc"); } catch (IllegalStateException x) { tot = true; }
                if (!tot) A.befund("Bus arbeitet trotz Mängeln");
            }
        }
        A.pruefe(erkannt == faelle.length, "Mängel der Verdrahtung, jeder für sich: es startet nichts", erkannt + " von " + faelle.length + " erkannt", "alle");
        A.pruefe(alle.size() >= 6, "Alle Mängel in einer Meldung", alle.size() + " Mängel", "≥ 6");

        // Lesen zur Zeit t: Zustand linear, auf einem anderen Gitter bilinear
        Bus b = new Bus(k);
        Linear lin = new Linear(g);
        Modul leser = stumm("render", a -> a.ohneTakt().liest("waves.hs", "m", Ort.ZELLE, g)), fern = stumm("sediment", a -> a.kopplung(600).liest("waves.hs", "m", Ort.ZELLE, grob));
        List<String> hinweise = b.anmelden(lin).anmelden(leser).anmelden(fern).verdrahten();
        Takt takt = new Takt(b);
        takt.beginn(0);
        takt.bis(3600);
        Feld ziel = b.lesefeld(leser, "waves.hs"), zielGrob = b.lesefeld(fern, "waves.hs");
        double ez = 0, eg = 0;
        double[] da = b.staende("waves.hs");
        for (int q = 0; q <= 36; q++) {
            double t = da[0] + (da[da.length - 1] - da[0]) * q / 36;
            b.lies(leser, "waves.hs", t, ziel);
            b.lies(fern, "waves.hs", t, zielGrob);
            for (int j = 0; j < g.ny; j++) for (int i = 0; i < g.nx; i++) ez = Math.max(ez, Math.abs(ziel.d[g.index(i, j)] - (3 + 0.25 * t + 1e-3 * g.x(i) - 2e-3 * g.y(j))));
            for (int j = 0; j < grob.ny; j++) for (int i = 0; i < grob.nx; i++) eg = Math.max(eg, Math.abs(zielGrob.d[grob.index(i, j)] - (3 + 0.25 * t + 1e-3 * grob.x(i) - 2e-3 * grob.y(j))));
        }
        A.pruefe(da.length == Bus.STAENDE && b.ausserhalb() == 0, "Der Bus hält drei Stände; gelesen wird dazwischen", f("Stände bei %.0f, %.0f, %.0f s", da[0], da[1], da[2]), "3 Stände, kein Lesen außerhalb");
        A.hoechstens("Zustand zur Zeit t zwischen zwei Ständen (linear in der Zeit)", ez, 1e-9, "m");
        A.hoechstens("Derselbe Zustand auf einem gröberen Gitter (bilinear)", eg, 1e-9, "m");
        b.lies(leser, "waves.hs", da[0] - 100, ziel);
        A.pruefe(b.ausserhalb() == 1 && ziel.zeit == da[0], "Lesen vor dem ältesten Stand: gilt der älteste, und es wird gezählt", "Zeit " + ziel.zeit + " s, gezählt " + b.ausserhalb(), "");
        A.pruefe(hinweise.isEmpty(), "Leser, die das Werkbuch nennt, geben keinen Hinweis",
                hinweise.toString(), "kein Hinweis");
        boolean fremd = false;
        try { b.lies(lin, "waves.hs", 0, ziel); } catch (IllegalStateException e) { fremd = true; }
        A.pruefe(fremd, "Ein Modul liest nur, was es angemeldet hat", fremd ? "abgelehnt" : "erlaubt", "abgelehnt");

        // Verbund (F18 A): sediment und aeolian legen ihre Teile ab, der Kern schreibt bed.dz als Summe und surf.z0 als Maximum
        Bus vb = new Bus(k);
        vb.anmelden(teil("sediment", g, 20, "bed.dz.sediment", t -> 0.001 * t, "surf.z0.sediment", t -> 0.004)).anmelden(teil("aeolian", g, 60, "bed.dz.aeolian", t -> -0.0002 * t, null, null))
                .anmelden(teil("vegetation", g, 300, "surf.z0.vegetation", t -> 0.03, null, null))
                .anmelden(new com.dan.core.bus.Verbund(k, "bed.dz", g, Feld.Zahl.DOUBLE, 60, "sediment", "aeolian"))
                .anmelden(new com.dan.core.bus.Verbund(k, "surf.z0", g, Feld.Zahl.DOUBLE, 300, "sediment", "vegetation"));
        Modul hy = stumm("hydro", a -> a.ohneTakt().liest("surf.z0", "m", Ort.ZELLE, g)), re = stumm("render", a -> a.ohneTakt().liest("bed.dz", "m", Ort.ZELLE, g));
        List<String> vh = vb.anmelden(hy).anmelden(re).verdrahten();
        Takt vt = new Takt(vb);
        vt.beginn(0);
        vt.bis(600);
        Feld dz = vb.lesefeld(re, "bed.dz"), z0 = vb.lesefeld(hy, "surf.z0");
        vb.lies(re, "bed.dz", 600, dz);
        vb.lies(hy, "surf.z0", 600, z0);
        double sollDz = 0.001 * 540 - 0.0002 * 540;                                                   // die Beiträge zur Startzeit des letzten Intervalls
        A.pruefe(Math.abs(dz.d[7] - sollDz) < 1e-12 && z0.d[7] == 0.03 && vh.isEmpty() && vb.ausserhalb() == 0, "Verbund am Bus: bed.dz als Summe, surf.z0 als Maximum der Beiträge",
                f("bed.dz %.4f m (Beiträge zur Zeit 540 s), surf.z0 %.3f m", dz.d[7], z0.d[7]), "0,4320 m und 0,030 m");
        boolean ohne = false;
        try { new com.dan.core.bus.Verbund(k, "water.h", g, Feld.Zahl.DOUBLE, 60, "hydro"); } catch (IllegalArgumentException e) { ohne = true; }
        Bus zwei = new Bus(k);
        zwei.anmelden(stumm("sediment", a -> a.kopplung(20).schreibt("bed.dz", "m", Ort.ZELLE, g, Feld.Zahl.FLOAT)));
        try { zwei.verdrahten(); ohne = false; } catch (Verdrahtungsfehler e) { ohne &= e.maengel.get(0).contains("Schreiber laut Katalog ist core"); }
        A.pruefe(ohne, "bed.dz schreibt nur der Kern; ein Feld ohne Verbund lässt sich nicht zusammensetzen", ohne ? "beides abgelehnt" : "erlaubt", "abgelehnt");
    }

    /** Ein Modul, das ein oder zwei Beiträge schreibt: in jeder Zelle der Wert der Funktion zur Zeit t. */
    private static Modul teil(String name, Gitter g, double kopplung, String feld1, java.util.function.DoubleUnaryOperator w1, String feld2, java.util.function.DoubleUnaryOperator w2) {
        return new Modul() {
            @Override public String name() { return name; }
            @Override public void anmelden(Anmeldung a) {
                a.kopplung(kopplung).schreibt(feld1, "m", Ort.ZELLE, g, Feld.Zahl.DOUBLE);
                if (feld2 != null) a.schreibt(feld2, "m", Ort.ZELLE, g, Feld.Zahl.DOUBLE);
            }
            @Override public void beginn(double t0, Bus bus) { lege(bus, t0); }
            @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }
            private void lege(Bus bus, double t) {
                bus.schreibfeld(this, feld1).fuellen(w1.applyAsDouble(t));
                bus.lege(this, feld1, t);
                if (feld2 == null) return;
                bus.schreibfeld(this, feld2).fuellen(w2.applyAsDouble(t));
                bus.lege(this, feld2, t);
            }
        };
    }

    // ------------------------------------------------------------------ 16 Bus (P5c)

    /** Ein Modul, das water.eta auf mehreren Gittern ablegt: in jeder Zelle 1 + 0,01 t + 10⁻³ x − 2·10⁻³ y. */
    private static final class Mehrfach implements Modul {
        final Gitter[] gitter;
        Mehrfach(Gitter... gitter) { this.gitter = gitter; }
        @Override public String name() { return "hydro"; }
        @Override public void anmelden(Anmeldung a) {
            a.kopplung(60);
            for (Gitter g : gitter) a.schreibt("water.eta", "m NHN", Ort.ZELLE, g, Feld.Zahl.DOUBLE);
        }
        @Override public void beginn(double t0, Bus bus) { lege(bus, t0); }
        @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }
        static double wert(double t, double x, double y) { return 1 + 0.01 * t + 1e-3 * (x - 468_000) - 2e-3 * (y - 6_046_000); }
        private void lege(Bus bus, double t) {
            for (Gitter g : gitter) {
                Feld f = bus.schreibfeld(this, "water.eta", g);
                for (int j = 0; j < g.ny; j++) for (int i = 0; i < g.nx; i++) f.d[g.index(i, j)] = wert(t, g.x(i), g.y(j));
                bus.lege(this, "water.eta", g, t);
            }
        }
    }

    private static void busP5c() {
        // Folge des Rands (Ort.RAND): im Süden beginnend gegen den Uhrzeigersinn; Geisterzelle eine halbe Zelle außerhalb
        {
            Gitter r = Gitter.utm("R", 32, 400_000, 6_000_000, 100, 5, 3);
            int falsch = 0;
            String folge = "";
            for (int q = 0; q < r.randZellen(); q++) {
                int[] z = r.randZelle(q);
                double[] p = r.geist(q);
                int k2 = z[2] == Gitter.RAND_SUED || z[2] == Gitter.RAND_NORD ? z[0] : z[1];
                double di = (p[0] - r.x(z[0])) / r.dx, dj = (p[1] - r.y(z[1])) / r.dy;
                boolean seite = switch (z[2]) {
                    case Gitter.RAND_SUED -> z[1] == 0 && di == 0 && dj == -1;
                    case Gitter.RAND_OST -> z[0] == r.nx - 1 && di == 1 && dj == 0;
                    case Gitter.RAND_NORD -> z[1] == r.ny - 1 && di == 0 && dj == 1;
                    default -> z[0] == 0 && di == -1 && dj == 0;
                };
                if (!seite || r.rand(z[2], k2) != q || r.enthaelt(p[0], p[1])) falsch++;
                if (q < 9) folge += (q > 0 ? " " : "") + z[0] + "," + z[1];
            }
            A.pruefe(falsch == 0 && folge.equals("0,0 1,0 2,0 3,0 4,0 4,0 4,1 4,2 4,2"), "Rand nach Ort.RAND (seit P5c in Gitter): Süd, Ost, Nord, West gegen den Uhrzeigersinn, je Randkante eine Zelle, Geisterzelle eine Zelle weiter außen",
                    f("%d von %d falsch; Folge %s …", falsch, r.randZellen(), folge), "0");
        }
        Katalog k = Katalog.grundstufe();
        Gitter g = Probe.G, grob = Gitter.utm("grob", 32, 468_000, 6_046_000, 80, 6, 4), innen = Gitter.utm("innen", 32, 468_100, 6_046_060, 10, 12, 10);
        // ein Schreiber, zwei Gitter; Leser auf jedem und auf einem dritten (umgerechnet aus dem feinsten, das es deckt)
        Bus b = new Bus(k);
        Mehrfach hy = new Mehrfach(grob, g);
        Modul lg = stumm("render", a -> a.ohneTakt().liest("water.eta", "m NHN", Ort.ZELLE, g)), lgr = stumm("analysis", a -> a.ohneTakt().liest("water.eta", "m NHN", Ort.ZELLE, grob)),
                li = stumm("render.water", a -> a.ohneTakt().liest("water.eta", "m NHN", Ort.ZELLE, innen));
        List<String> h = b.anmelden(hy).anmelden(lg).anmelden(lgr).anmelden(li).verdrahten();
        Takt t = new Takt(b);
        t.beginn(0);
        t.bis(600);
        double fehler = 0;
        for (Modul m : new Modul[] {lg, lgr, li}) {
            Feld f = b.lesefeld(m, "water.eta");
            b.lies(m, "water.eta", 570, f);
            for (int j = 0; j < f.gitter.ny; j++) for (int i = 0; i < f.gitter.nx; i++) fehler = Math.max(fehler, Math.abs(f.d[f.gitter.index(i, j)] - Mehrfach.wert(570, f.gitter.x(i), f.gitter.y(j))));
        }
        boolean gitterNennen = false;
        try { b.schreibfeld(hy, "water.eta"); } catch (IllegalStateException e) { gitterNennen = e.getMessage().contains("Gitter angeben"); }
        A.pruefe(fehler < 1e-9 && h.isEmpty() && b.gitter("water.eta").size() == 2 && b.ausserhalb() == 0 && gitterNennen,
                "Ein Feld auf zwei Gittern eines Schreibers (seit P5c): Leser auf jedem und auf einem dritten (bilinear aus dem feinsten, das es deckt); ohne Gitter kein Schreibfeld",
                f("|Δ| bis %.1e m, %d Leitungen, Hinweise %s, Schreibfeld ohne Gitter %s", fehler, b.gitter("water.eta").size(), h, gitterNennen ? "abgelehnt" : "erlaubt"), "< 1e-9 m, 2, keine, abgelehnt");
        // Mängel: zweimal auf demselben Gitter, ein Leser außerhalb aller Gitter, Antrieb im Kreis
        Gitter fern = Gitter.utm("fern", 32, 470_000, 6_050_000, 20, 4, 4);
        String[][] faelle = {{"zweimal", "schreibt water.eta zweimal auf P"}, {"außerhalb", "keines der Gitter des Schreibers (grob, P) deckt es"}, {"Kreis", "Antrieb im Kreis: "}};
        int erkannt = 0;
        for (int w = 0; w < faelle.length; w++) {
            Bus x = new Bus(k);
            int ww = w;
            x.anmelden(ww == 0 ? new Mehrfach(grob, g, g) : new Mehrfach(grob, g));
            if (ww == 1) x.anmelden(stumm("render", a -> a.ohneTakt().liest("water.eta", "m NHN", Ort.ZELLE, fern)));
            if (ww == 2) {
                x.anmelden(stumm("tide", a -> a.kopplung(300).schreibt("tide.bc", "m, m²/s", Ort.RAND, g, Feld.Zahl.DOUBLE).liestImIntervall("air.p", "Pa", Ort.ZELLE, grob)));
                x.anmelden(stumm("antrieb", a -> a.kopplung(300).schreibt("air.p", "Pa", Ort.ZELLE, grob, Feld.Zahl.FLOAT).liestImIntervall("tide.bc", "m, m²/s", Ort.RAND, g)));
            }
            try {
                x.verdrahten();
                A.befund("Fall „" + faelle[w][0] + "“ verdrahtet ohne Mangel");
            } catch (Verdrahtungsfehler e) {
                if (e.maengel.stream().anyMatch(m -> m.contains(faelle[ww][1]))) erkannt++;
                else A.befund("Fall „" + faelle[w][0] + "“ nicht erkannt: " + e.maengel);
            }
        }
        A.pruefe(erkannt == faelle.length, "Mängel mehrerer Gitter und der Antriebe: zweimal auf demselben Gitter, ein Leser außerhalb aller Gitter des Schreibers, Antrieb im Kreis",
                erkannt + " von " + faelle.length + " erkannt", "alle");
        // Antrieb im Intervall: hydro (300 s) liest tide.bc (60 s) über sein ganzes Intervall; tide ist nach hydro angemeldet
        Bus ab = new Bus(k);
        double[] gelesen = {0, 0};
        Modul ti = new Modul() {
            @Override public String name() { return "tide"; }
            @Override public void anmelden(Anmeldung a) { a.kopplung(60).schreibt("tide.bc", "m, m²/s", Ort.RAND, g, Feld.Zahl.DOUBLE); }
            @Override public void beginn(double t0, Bus bus) { lege(bus, t0); }
            @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }
            private void lege(Bus bus, double t) {
                bus.schreibfeld(this, "tide.bc").fuellen(0.5 + 0.001 * t);
                bus.lege(this, "tide.bc", t);
            }
        };
        Modul hz = new Modul() {
            Feld rand;
            @Override public String name() { return "hydro"; }
            @Override public void anmelden(Anmeldung a) { a.kopplung(300).liestImIntervall("tide.bc", "m, m²/s", Ort.RAND, g); }
            @Override public void beginn(double t0, Bus bus) { rand = bus.lesefeld(this, "tide.bc"); }
            @Override public void schritt(double t0, double t1, Bus bus) {
                for (int q = 0; q <= 8; q++) {
                    double tt = t0 + (t1 - t0) * q / 8;
                    bus.lies(this, "tide.bc", tt, rand);
                    gelesen[0] = Math.max(gelesen[0], Math.abs(rand.d[0] - (0.5 + 0.001 * tt)));
                    gelesen[1]++;
                }
            }
        };
        List<String> ha = ab.anmelden(hz).anmelden(ti).verdrahten();
        Takt ta = new Takt(ab);
        ta.mitschreiben = true;
        ta.beginn(0);
        ta.bis(1800);
        boolean ordnung = ta.protokoll.size() >= 6 && ta.protokoll.get(4).equals("tide 240.0 300.0") && ta.protokoll.get(5).equals("hydro 0.0 300.0");
        A.pruefe(ordnung && ab.ausserhalb() == 0 && gelesen[0] < 1e-12 && ab.tiefe("tide.bc") == 7 && ha.isEmpty(),
                "Antrieb im Intervall (seit P5c): hydro (300 s) liest tide.bc (60 s) zu neun Zeiten in jedem Intervall; tide, später angemeldet, rechnet vorher bis zum Ende des Intervalls",
                f("Beginn %s; %.0f Lesungen, |Δ| bis %.1e, außerhalb %d, Leitung mit %d Ständen", ta.protokoll.subList(0, Math.min(6, ta.protokoll.size())), gelesen[1], gelesen[0], ab.ausserhalb(), ab.tiefe("tide.bc")),
                "fünfmal tide, dann hydro; genau; 0; 7 Stände");
    }

    // ------------------------------------------------------------------ 9 Takt

    /** Ein Aufbau aus Tide (60 s), Hydro (20 s), Atmos (300 s, gröberes Gitter) und dem Bild. */
    private static final class Aufbau {
        final Gitter g = Probe.G, grob = Gitter.utm("grob", 32, 468_000, 6_046_000, 80, 6, 4);
        final Bus bus = new Bus(Katalog.grundstufe());
        final Probe.Tide tide = new Probe.Tide(g, 60);
        final Probe.Hydro hydro = new Probe.Hydro(g, 20);
        final Probe.Atmos atmos;
        final Probe.Bild bild = new Probe.Bild(g, grob);
        final List<String> hinweise;
        final Takt takt;

        Aufbau(long seed) {
            atmos = new Probe.Atmos(grob, 300, seed);
            hinweise = bus.anmelden(tide).anmelden(hydro).anmelden(atmos).anmelden(bild).verdrahten();
            takt = new Takt(bus);
            takt.mitschreiben = true;
            takt.beginn(0);
        }

        double pruefsumme() {
            Feld e = bus.lesefeld(bild, "water.eta");
            bus.lies(bild, "water.eta", takt.zeit(), e);
            return atmos.pruefsumme + e.summe();
        }
    }

    private static void takt() throws Exception {
        Aufbau a = new Aufbau(42);
        a.takt.bis(1800);
        // Reihenfolge: wer zurückliegt, rechnet; bei Gleichstand der früher Angemeldete
        boolean ordnung = a.takt.protokoll.size() >= 4 && a.takt.protokoll.get(0).equals("tide 0.0 60.0") && a.takt.protokoll.get(1).equals("hydro 0.0 20.0")
                && a.takt.protokoll.get(2).equals("atmos 0.0 300.0") && a.takt.protokoll.get(3).equals("hydro 20.0 40.0");
        A.pruefe(ordnung && a.takt.schritte() == 30 + 90 + 6, "Reihenfolge und Zahl der Schritte in 30 min (Tide 60 s, Hydro 20 s, Atmos 300 s)",
                a.takt.schritte() + " Schritte, Beginn: " + a.takt.protokoll.subList(0, 4), "126 Schritte; tide, hydro, atmos, hydro");
        A.pruefe(a.bus.ausserhalb() == 0, "Jedes Modul findet die Felder der anderen zu seiner Startzeit zwischen zwei Ständen", a.bus.ausserhalb() + " Lesungen außerhalb", "0");
        A.pruefe(a.hinweise.size() == 1 && a.hinweise.get(0).startsWith("render.water liest surf.wet"), "Hinweis auf einen Leser, den das Werkbuch nicht nennt", a.hinweise.toString(), "1 Hinweis");
        // Fluss als Zeitmittel: Σ Mittel · Intervall = ∫ (2 + 0,01 t) dt
        Bus fb = new Bus(Katalog.grundstufe());
        Probe.Tide ft = new Probe.Tide(Probe.G, 60);
        Probe.Hydro fh = new Probe.Hydro(Probe.G, 20);
        Modul fs = stumm("sediment", x -> x.kopplung(20).liest("water.q", "m²/s", Ort.KANTE, Probe.G));
        fb.anmelden(ft).anmelden(fh).anmelden(fs).verdrahten();
        Takt ftakt = new Takt(fb);
        ftakt.beginn(0);
        Feld q = fb.lesefeld(fs, "water.q");
        double integral = 0;
        for (int n = 1; n <= 30; n++) {
            ftakt.bis(20.0 * n);
            fb.lies(fs, "water.q", 20.0 * n - 7, q);                                                  // irgendwo im Intervall: das Mittel des Intervalls
            integral += q.d[q.kanteY(3, 2)] * 20;
        }
        A.hoechstens("Fluss als Zeitmittel über das Kopplungsintervall: Σ q̄·Δt gegen ∫ q dt über 10 min, relativ", integral / (2 * 600 + 0.005 * 600 * 600) - 1, 1e-12, "");
        // reproduzierbar: zweimal nacheinander und einmal im eigenen Faden
        Aufbau b = new Aufbau(42), anders = new Aufbau(43);
        b.takt.bis(1800);
        anders.takt.bis(1800);
        Aufbau d = new Aufbau(42);
        Uhr uhr = new Uhr(Instant.parse("2026-03-20T00:00:00Z"));
        d.takt.ziel = 20_000;
        d.takt.laufen(uhr);
        AtomicLong zerrissen = new AtomicLong(), voraus = new AtomicLong(), bilder = new AtomicLong();
        Feld e = d.bus.lesefeld(d.bild, "water.eta"), wet = d.bus.lesefeld(d.bild, "surf.wet");
        long ende = System.nanoTime() + 3_000_000_000L, zuletzt = System.nanoTime();
        while (uhr.sekunden() < 1500 && System.nanoTime() < ende && d.takt.laeuft()) {
            long jetzt = System.nanoTime();
            double t = d.takt.fuehre(uhr, (jetzt - zuletzt) * 1e-9);
            zuletzt = jetzt;
            if (t > d.takt.zeit() + 1e-9) voraus.incrementAndGet();
            d.bus.lies(d.bild, "water.eta", t, e);
            d.bus.lies(d.bild, "surf.wet", t, wet);
            for (int i = 1; i < e.laenge; i++) if (e.d[i] != e.d[0]) { zerrissen.incrementAndGet(); break; }
            bilder.incrementAndGet();
            Thread.sleep(1);
        }
        d.takt.anhalten();
        d.takt.bis(1800);
        boolean gleich = a.takt.protokoll.equals(b.takt.protokoll) && a.pruefsumme() == b.pruefsumme() && a.takt.protokoll.equals(d.takt.protokoll) && a.pruefsumme() == d.pruefsumme();
        A.pruefe(gleich && a.pruefsumme() != anders.pruefsumme(), "Reproduzierbar: gleicher seed, gleiche Reihenfolge, gleiches Ergebnis – nacheinander und im eigenen Faden",
                gleich ? "Protokoll und Prüfsumme gleich; anderer seed, andere Prüfsumme" : "verschieden", "bitgleich");
        A.pruefe(zerrissen.get() == 0 && voraus.get() == 0 && d.bus.ausserhalb() == 0 && bilder.get() > 20, "Bild liest im eigenen Faden, während die Physik rechnet",
                bilder.get() + " Bilder bis " + f("%.0f s", uhr.sekunden()) + ": zerrissen " + zerrissen.get() + ", Uhr vor der Physik " + voraus.get() + ", außerhalb gelesen " + d.bus.ausserhalb(),
                "nichts davon");
        A.info("Zeitraffer im Faden", f("Ziel 20 000, gehalten %.0f; die Physik könnte %.2g", uhr.zeitraffer, d.takt.erreichbar()));
        // Physik zu langsam: der Zeitraffer sinkt, die Uhr läuft der Physik nicht davon; holt die Physik auf, steigt er wieder
        Aufbau l = new Aufbau(42);
        l.hydro.bremse = 2;                                                                            // 2 ms je 20 s: höchstens 10 000-fach
        Uhr u2 = new Uhr(Instant.parse("2026-03-20T00:00:00Z"));
        l.takt.ziel = 50_000;
        l.takt.laufen(u2);
        long v2 = 0;
        double langsam = Double.NaN, erreichbar = Double.NaN;
        long beginn = System.nanoTime();
        zuletzt = beginn;
        Feld e2 = l.bus.lesefeld(l.bild, "water.eta");
        while (l.takt.laeuft()) {
            long jetzt = System.nanoTime();
            double t = l.takt.fuehre(u2, (jetzt - zuletzt) * 1e-9);
            zuletzt = jetzt;
            if (t > l.takt.zeit() + 1e-9) v2++;
            l.bus.lies(l.bild, "water.eta", t, e2);
            if (Double.isNaN(langsam) && jetzt - beginn > 1_200_000_000L) {                           // nach 1,2 s: Stand festhalten, Bremse lösen
                langsam = u2.zeitraffer;
                erreichbar = l.takt.erreichbar();
                l.hydro.bremse = 0;
            }
            if (!Double.isNaN(langsam) && (u2.zeitraffer == 50_000 || jetzt - beginn > 6_000_000_000L)) break;
            Thread.sleep(2);
        }
        l.takt.anhalten();
        A.pruefe(l.takt.angehalten() > 0 && langsam < 50_000 && langsam <= erreichbar && v2 == 0 && l.bus.ausserhalb() == 0,
                "Physik zu langsam (2 ms je 20 s): der Zeitraffer sinkt auf das Erreichbare, die Uhr bleibt hinter der Physik",
                f("Ziel 50 000, gehalten %.0f (erreichbar %.0f), %d-mal angehalten, außerhalb gelesen %d", langsam, erreichbar, l.takt.angehalten(), l.bus.ausserhalb()),
                "gesenkt, ≤ erreichbar, nie voraus");
        A.pruefe(u2.zeitraffer == 50_000, "Die Physik holt auf: der Zeitraffer steigt wieder auf das Ziel", f("%.0f nach %.1f s", u2.zeitraffer, (System.nanoTime() - beginn) * 1e-9 - 1.2),
                "50 000 innerhalb von 4,8 s");
    }

    // ------------------------------------------------------------------ 10 Archiv

    private static void archiv() throws IOException {
        Path ordner = Files.createTempDirectory("corecheck-lauf");
        try {
            Katalog k = Katalog.grundstufe();
            Gitter g = Probe.G;
            Feld eta = new Feld(k.verlange("water.eta"), g, Feld.Zahl.DOUBLE), h = new Feld(k.verlange("water.h"), g, Feld.Zahl.FLOAT);
            Archiv.Lauf lauf = new Archiv.Lauf();
            lauf.name = "Springtide, Prüflauf";
            lauf.start = Instant.parse("2026-03-20T00:00:00Z");
            lauf.seed = 2026;
            lauf.antrieb.put("tide", "EOT20, 17 Partialtiden");
            lauf.module.put("hydro", "0.1");
            int n = 13;
            try (Archiv a = Archiv.anlegen(ordner, lauf)) {
                a.spur(eta, Archiv.Ablage.HALB).spur(h, Archiv.Ablage.FLOAT);
                for (int s = 0; s < n; s++) {
                    fuelle(eta, h, 300.0 * s);
                    a.stand(300.0 * s, eta, h);
                    a.zeile("bilanzen.csv", "Volumen P;" + 300.0 * s + ";0");
                }
            }
            try (Archiv a = Archiv.oeffnen(ordner)) {
                boolean kopf = a.lauf.name.equals(lauf.name) && a.lauf.start.equals(lauf.start) && a.lauf.seed == 2026 && a.lauf.antrieb.equals(lauf.antrieb)
                        && a.lauf.module.equals(lauf.module) && a.lauf.gitter.get("P").gleich(g) && a.staende() == n && a.zeit(n - 1) == 3600;
                long groesse = Files.size(ordner.resolve("water.eta.f16")) + Files.size(ordner.resolve("water.h.f32"));
                A.pruefe(kopf && groesse == n * a.bytesJeStand() && a.bytesJeStand() == g.zellen() * 6L && Files.readAllLines(ordner.resolve("bilanzen.csv")).size() == n,
                        "Kopf, Stände und Größe eines Laufs nach dem Wiederöffnen", a.staende() + " Stände zu " + a.bytesJeStand() + " Byte, Kopf " + (kopf ? "gleich" : "verschieden"),
                        "13 Stände zu 2304 Byte");
                Feld le = a.feld("water.eta", k), lh = a.feld("water.h", k);
                double stand = 0, zwischen = 0;
                for (int s = 0; s < n; s++) {
                    a.lies("water.eta", s, le);
                    a.lies("water.h", s, lh);
                    fuelle(eta, h, 300.0 * s);
                    for (int i = 0; i < le.laenge; i++) {
                        stand = Math.max(stand, Math.abs(le.f[i] - Float.float16ToFloat(Float.floatToFloat16((float) eta.d[i]))));
                        stand = Math.max(stand, Math.abs(lh.f[i] - h.f[i]));
                    }
                }
                A.hoechstens("Stände gelesen wie geschrieben (float32 genau, float16 auf den nächsten Wert)", stand, 0, "");
                // abspielen: vorwärts, rückwärts und gesprungen
                double[] zeiten = new double[80];
                for (int q = 0; q < 30; q++) zeiten[q] = 3600.0 * q / 29;
                for (int q = 0; q < 30; q++) zeiten[30 + q] = 3600 - 3600.0 * q / 29;
                SplittableRandom r = new SplittableRandom(5);
                for (int q = 60; q < 80; q++) zeiten[q] = r.nextDouble() * 3600;
                Feld e0 = eta.gleiches(), e1 = eta.gleiches();
                for (double t : zeiten) {
                    a.lies("water.eta", t, le);
                    int s = (int) Math.min(n - 2, Math.floor(t / 300));
                    fuelle(e0, h, 300.0 * s);
                    fuelle(e1, h, 300.0 * (s + 1));
                    double w = (t - 300.0 * s) / 300;
                    for (int i = 0; i < le.laenge; i++) {
                        float x = Float.float16ToFloat(Float.floatToFloat16((float) e0.d[i])), y = Float.float16ToFloat(Float.floatToFloat16((float) e1.d[i]));
                        zwischen = Math.max(zwischen, Math.abs(le.f[i] - (float) (x + w * (y - x))));
                    }
                }
                A.hoechstens("Abspielen vorwärts, rückwärts und gesprungen: linear zwischen zwei Ständen", zwischen, 0, "");
                A.pruefe(a.lies("water.eta", -50.0, le) == 0 && a.lies("water.eta", 9999.0, le) == 3600, "Vor dem ersten und nach dem letzten Stand gilt dieser", "0 s und 3600 s", "");
                // seit W5b (F216 A): einzelne Werte ohne den Satz
                double wert = 0;
                for (int s = 0; s < n; s++) {
                    a.lies("water.eta", s, le);
                    a.lies("water.h", s, lh);
                    for (int i = 0; i < le.laenge; i += 7) {
                        wert = Math.max(wert, Math.abs(a.wert("water.eta", n - 1 - s, i) - a.wert("water.eta", n - 1 - s, i)));
                        wert = Math.max(wert, Math.abs(a.wert("water.eta", s, i) - le.f[i]));
                        wert = Math.max(wert, Math.abs(a.wert("water.h", s, i) - lh.f[i]));
                    }
                }
                a.lies("water.eta", 3, le);
                float vorher = le.f[5];
                a.wert("water.eta", 9, 5);
                a.lies("water.eta", 3, le);
                A.hoechstens("Einzelne Werte (Archiv.wert) gleich den Sätzen; die gehaltenen Sätze bleiben", wert + Math.abs(le.f[5] - vorher), 0, "");
                // seit W5c: Vorlesen liest alle Spuren eines Stands, lässt die gehaltenen Sätze und meldet ihn
                a.lies("water.eta", 4, le);
                float v4 = le.f[3];
                long vb = a.vorlesen(7);
                a.lies("water.eta", 4, le);
                A.pruefe(vb == a.bytesJeStand() && a.vorgelesen(7) && !a.vorgelesen(6) && le.f[3] == v4 && a.vorlesen(7) == 0 && a.standBis(301) == 1,
                        "Vorlesen eines Stands (W5c): alle Spuren, nur einmal, die gehaltenen Sätze bleiben", vb + " Byte für Stand 7", a.bytesJeStand() + " Byte");
                Archiv.Messung me = Archiv.messung();
                A.info("Messung des Lesens (dieser Faden)", me.toString());
            }
            double g3 = G3.zellen() * 4 * 2 / 1e6;
            A.info("Größe eines Stands von G3 (h, u, v, c als float16)", f("%.1f MB; eine Tide in 5-min-Schritten (149 Stände): %.2f GB", g3, g3 * 149 / 1000));
            A.info("Auflösung von float16", f("bei 1 m %.2f mm, bei 3 m %.1f mm, bei 30 m %.0f mm", halb(1), halb(3), halb(30)));
        } finally {
            try (Stream<Path> s = Files.walk(ordner)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    // ------------------------------------------------------------------ 10b Archiv gepackt (seit D1, Werkbuch HalligHooge 162)

    private static void archivGepackt() throws IOException {
        Path wurzel = Files.createTempDirectory("corecheck-packung");
        try {
            Katalog k = Katalog.grundstufe();
            // 300 × 200 Zellen: float16 zwei Blöcke je Stand, float32 vier – so prüft die Sonde auch Blöcke mitten im Stand
            Gitter g = Gitter.utm("T", 32, 468_000, 6_046_000, 20, 300, 200);
            Feld eta = new Feld(k.verlange("water.eta"), g, Feld.Zahl.DOUBLE), h = new Feld(k.verlange("water.h"), g, Feld.Zahl.FLOAT),
                    alter = new Feld(k.verlange("water.wetAge"), g, Feld.Zahl.FLOAT);
            Archiv.Lauf lauf = new Archiv.Lauf();
            lauf.name = "Prüflauf gepackt";
            lauf.start = Instant.parse("2026-10-30T00:00:00Z");
            int n = 9;
            Path roh = wurzel.resolve("roh"), gep = wurzel.resolve("gepackt"), ohne = wurzel.resolve("ohne_h");
            try (Archiv a = Archiv.anlegen(roh, lauf)) {
                a.spur(eta, Archiv.Ablage.HALB).spur(h, Archiv.Ablage.FLOAT).spur(alter, Archiv.Ablage.FLOAT);
                for (int s = 0; s < n; s++) {
                    fuelle(eta, h, 300.0 * s);
                    for (int i = 0; i < alter.laenge; i++) alter.f[i] = (i % 300) < 120 ? 300f * s + 7 : -345_600f;   // wie wetAge: große Flächen gleich
                    a.stand(300.0 * s, eta, h, alter);
                }
            }
            List<String> meldungen = new ArrayList<>();
            long t0 = System.nanoTime();
            Packer.Ergebnis e = Packer.packeLauf(roh, gep, java.util.Set.of(), 2, meldungen::add);
            double packS = (System.nanoTime() - t0) / 1e9;
            Packer.Ergebnis e2 = Packer.packeLauf(roh, ohne, java.util.Set.of("water.h"), 2, meldungen::add);
            boolean dateien = Files.isRegularFile(gep.resolve("water.eta.f16z")) && Files.isRegularFile(gep.resolve("water.h.f32z")) && !Files.exists(gep.resolve("water.eta.f16"))
                    && Files.isRegularFile(gep.resolve(Packer.FERTIG)) && !Files.exists(wurzel.resolve("gepackt.teil"));
            try (Archiv r = Archiv.oeffnen(roh); Archiv p = Archiv.oeffnen(gep); Archiv o = Archiv.oeffnen(ohne)) {
                A.pruefe(dateien && p.gepackt() && !r.gepackt() && p.staende() == n && p.lauf.spuren.get("water.h").gepackt() && o.lauf.spuren.size() == 2 && !o.lauf.spuren.containsKey("water.h")
                                && e2.weg().equals(List.of("water.h")),
                        "Packer: Dateien .f16z/.f32z, Kopf mit ;Z, Lauf erst nach der Prüfung fertig; weggelassene Spur fehlt im Kopf",
                        f("%s, %d Stände, %.2f× in %.2f s; ohne water.h: %d Spuren", dateien ? "Dateien wie erwartet" : "Dateien FALSCH", p.staende(), e.faktor(), packS, o.lauf.spuren.size()), "");
                // jeder Stand Bit für Bit gleich, auch zwischen den Ständen und für einzelne Zellen
                long abw = 0, werte = 0;
                for (String sp : new String[] {"water.eta", "water.h", "water.wetAge"}) {
                    Feld fr = r.feld(sp, k), fp = p.feld(sp, k);
                    for (int s = n - 1; s >= 0; s--) {
                        r.lies(sp, s, fr);
                        p.lies(sp, s, fp);
                        for (int i = 0; i < fr.laenge; i++, werte++) if (Float.floatToRawIntBits(fr.f[i]) != Float.floatToRawIntBits(fp.f[i])) abw++;
                    }
                    SplittableRandom zr = new SplittableRandom(162);
                    for (int q = 0; q < 12; q++) {
                        double t = zr.nextDouble() * 300 * (n - 1);
                        r.lies(sp, t, fr);
                        p.lies(sp, t, fp);
                        for (int i = 0; i < fr.laenge; i++, werte++) if (Float.floatToRawIntBits(fr.f[i]) != Float.floatToRawIntBits(fp.f[i])) abw++;
                    }
                    for (int s = 0; s < n; s++)
                        for (int i = 0; i < fr.laenge; i += 997, werte++) if (Double.doubleToRawLongBits(r.wert(sp, s, i)) != Double.doubleToRawLongBits(p.wert(sp, s, i))) abw++;
                    // die letzte Zelle (Rest eines Blocks)
                    if (Double.doubleToRawLongBits(r.wert(sp, 3, fr.laenge - 1)) != Double.doubleToRawLongBits(p.wert(sp, 3, fr.laenge - 1))) abw++;
                }
                A.hoechstens("Gepackt gelesen = ungepackt gelesen, Bit für Bit (Stände, Zwischenzeiten, einzelne Zellen)", abw, 0, f("%,d Werte verglichen", werte));
                // Vorrat: vorgelesen, das Bild nimmt den Stand von dort; verdrängt wird, was am weitesten weg liegt
                Feld fp = p.feld("water.eta", k), fr = r.feld("water.eta", k);
                long vb = p.vorlesen(5);
                p.lies("water.eta", 5, fp);
                r.lies("water.eta", 5, fr);
                boolean gleich = true;
                for (int i = 0; i < fr.laenge; i++) gleich &= Float.floatToRawIntBits(fr.f[i]) == Float.floatToRawIntBits(fp.f[i]);
                A.pruefe(vb > 0 && vb < p.bytesJeStand() && p.vorgelesen(5) && !p.vorgelesen(4) && p.vorlesen(5) == 0 && gleich && p.vorausPlatz() >= 8 && r.vorausPlatz() == Integer.MAX_VALUE
                                && p.wert("water.eta", 5, 77) == r.wert("water.eta", 5, 77),
                        "Vorlesen gepackt: entpackt in den Vorrat, gleich dem Original; Platz voraus nach Größe", f("Stand 5: %,d Byte gelesen (ungepackt %,d), Platz %d Stände", vb, p.bytesJeStand(), p.vorausPlatz()),
                        "weniger als ungepackt");
                // Speicher klein: der Vorrat hält höchstens seinen Platz und verdrängt, was am weitesten vom Bild weg liegt
                int platz = p.vorausPlatz();
                p.vorratPlatz(4);
                p.bezug(0);
                for (int s = 0; s < n; s++) p.vorlesen(s);
                int gehalten = 0;
                for (int s = 0; s < n; s++) if (p.vorgelesen(s)) gehalten++;
                boolean nah = p.vorgelesen(0) && p.vorgelesen(1) && p.vorgelesen(2) && p.vorgelesen(3);
                p.lies("water.eta", 8, fp);
                r.lies("water.eta", 8, fr);
                for (int i = 0; i < fr.laenge; i++) nah &= Float.floatToRawIntBits(fr.f[i]) == Float.floatToRawIntBits(fp.f[i]);
                p.vorratPlatz(0);
                A.pruefe(gehalten == 4 && nah && platz == (int) Math.min(400, Archiv.VORRAT_BYTES / p.rohJeStand()),
                        "Vorrat hält höchstens seinen Platz, das Fernste geht; was fehlt, liest das Bild selbst",
                        gehalten + " von " + n + " Ständen bei Platz 4 (die nächsten 0–3: " + (nah ? "ja" : "NEIN") + "); Platz nach Größe " + platz, "");
            }
            // die Prüfsumme findet einen beschädigten Block
            Path kaputt = wurzel.resolve("kaputt");
            Files.createDirectories(kaputt);
            try (Stream<Path> s = Files.list(gep)) {
                for (Path q : s.toList()) Files.copy(q, kaputt.resolve(q.getFileName()));
            }
            Path ziel = kaputt.resolve("water.h.f32z");
            byte[] b = Files.readAllBytes(ziel);
            b[b.length / 2] ^= 0x10;                 // mitten in den gepackten Daten (seit Lauf D1; am Ende traf es unter Windows den Schluss eines Blocks)
            Files.write(ziel, b);
            String gefunden;
            try {
                Packer.pruefeLauf(kaputt);
                gefunden = "nicht gefunden";
            } catch (IOException x) {
                gefunden = x.getMessage();
            }
            int geprueft = Packer.pruefeLauf(gep);
            A.pruefe(!gefunden.equals("nicht gefunden") && geprueft == n, "Prüfung: jeder Stand gegen die Prüfsumme des Originals; ein gekipptes Bit fällt auf",
                    geprueft + " Stände in Ordnung; beschädigt: " + gefunden, "");
            Packer.Ergebnis e3 = Packer.packeLauf(roh, gep, java.util.Set.of(), 2, meldungen::add);
            A.pruefe(e3.schon(), "Ein fertiger Lauf wird beim zweiten Durchgang übersprungen", meldungen.get(meldungen.size() - 1).strip(), "");
            A.info("Packen im Prüflauf", f("%,d → %,d Byte (%.2f×), %.2f s", e.roh(), e.gepackt(), e.faktor(), packS));
        } finally {
            try (Stream<Path> s = Files.walk(wurzel)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static double halb(double wert) {
        float a = Float.float16ToFloat(Float.floatToFloat16((float) wert)), b = Float.float16ToFloat((short) (Float.floatToFloat16((float) wert) + 1));
        return (b - a) * 1000.0;
    }

    private static void fuelle(Feld eta, Feld h, double t) {
        for (int i = 0; i < eta.laenge; i++) {
            eta.d[i] = 1.6 * Math.sin(t / 7000 + i * 0.01) + 0.002 * (i % 17);
            h.f[i] = (float) (eta.d[i] + 2.5 + 0.1 * Math.cos(i * 0.3));
        }
    }

    // ------------------------------------------------------------------ 11 Bilanzen

    private static void bilanzen() {
        Bilanz b = new Bilanz("Volumen G3", "m³", 1e-5, "Zufluss", "Abfluss", "Regen", "Siel");
        SplittableRandom r = new SplittableRandom(11);
        double bestand = 4.0e8;
        b.beginn(0, bestand);
        for (int s = 1; s <= 2000; s++) {
            double zu = 2e5 * (1 + Math.sin(s * 0.01)), ab = -2e5 * (1 - Math.sin(s * 0.01)), regen = r.nextDouble() * 50, siel = s % 40 < 10 ? 300 : 0;
            bestand += zu + ab + regen + siel;
            b.buche(20.0 * s, bestand, zu, ab, regen, siel);
        }
        A.hoechstens("Geschlossene Bilanz über 2000 Schritte: Rest relativ zum Bezug", b.relativ(), 1e-12, "");
        A.pruefe(b.kopf().equals("bilanz;zeit_s;bestand_m³;Zufluss;Abfluss;Regen;Siel;rest;rest_relativ") && b.zeile().split(";").length == 9, "Zeile für bilanzen.csv", b.zeile(), "9 Spalten");
        Bilanz leck = new Bilanz("Volumen G3", "m³", 1e-5, "Zufluss", "Abfluss");
        leck.beginn(0, 4.0e8);
        int bei = -1;
        String meldung = "";
        double v = 4.0e8;
        try {
            for (int s = 1; s <= 2000; s++) {
                v += 1000 - 800 - 30;                                                                 // 30 m³ je Schritt verschwinden
                leck.buche(20.0 * s, v, 1000, -800);
                bei = s;
            }
        } catch (Bilanzfehler e) {
            meldung = e.getMessage();
        }
        A.pruefe(bei == 133 && meldung.contains("Rest"), "Ein Leck von 30 m³ je Schritt bei 4·10⁸ m³ und Grenze 1·10⁻⁵: Fehler, keine Warnung",
                "Fehler im Schritt " + (bei + 1) + ": " + meldung, "Fehler im Schritt 134 (Rest über 4000 m³)");
    }

    // ------------------------------------------------------------------ 12 Prüfrahmen

    private static void pruefrahmen() {
        // Pegel aus zehn Partialtiden (Größen wie Husum), 369 Tage stündlich
        Object[][] soll = {{"M2", 1.7816, 195.03}, {"S2", 0.3769, 91.29}, {"N2", 0.2626, 70.85}, {"K2", 0.1643, 232.23}, {"K1", 0.0531, 39.58}, {"O1", 0.0602, 115.05},
                {"M4", 0.0403, 25.71}, {"MS4", 0.0221, 218.30}, {"MN4", 0.0090, 263.23}, {"M6", 0.1080, 27.23}};
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        int n = 369 * 24;
        double[] y = new double[n];
        List<Partialtide> tiden = new ArrayList<>();
        for (Object[] z : soll) tiden.add(Partialtide.nach((String) z[0]));
        for (int k = 0; k < n; k++) {
            Instant t = start.plusSeconds(3600L * k);
            y[k] = -0.1861;
            for (Object[] z : soll) y[k] += Partialtide.nach((String) z[0]).wert(t, (double) z[1], (double) z[2]);
        }
        Harmonik.Ergebnis e = Harmonik.analyse(start, 3600, y, tiden, 1);
        double da = 0, dp = 0;
        for (Object[] z : soll) {
            Harmonik.Tide t = e.tide((String) z[0]);
            da = Math.max(da, Math.abs(t.amplitude() - (double) z[1]));
            dp = Math.max(dp, Math.abs(((t.phaseGrad() - (double) z[2] + 540) % 360) - 180));
        }
        A.hoechstens("Harmonische Analyse eines Pegels aus 10 Partialtiden, 369 Tage: Amplitude", da, 1e-9, "m");
        A.hoechstens("Harmonische Analyse: Phase", dp, 1e-6, "°");
        A.hoechstens("Harmonische Analyse: Mittel und Rest", Math.max(Math.abs(e.mittel() + 0.1861), e.restRms()), 1e-9, "m");
        double vor = 0;
        for (int k = 0; k < 200; k++) {
            Instant t = Instant.parse("2031-05-01T00:00:00Z").plusSeconds(1800L * k);
            double w = -0.1861;
            for (Object[] z : soll) w += Partialtide.nach((String) z[0]).wert(t, (double) z[1], (double) z[2]);
            vor = Math.max(vor, Math.abs(e.wert(t) - w));
        }
        A.hoechstens("Vorhersage aus dem Ergebnis für 2031", vor, 1e-8, "m");
        // Rayleigh: 15 Tage trennen M2 und S2, aber nicht N2, K2, P1
        List<Partialtide> aus = new ArrayList<>(), gut = Harmonik.trennbar(List.of(Partialtide.M2, Partialtide.S2, Partialtide.N2, Partialtide.K2, Partialtide.K1, Partialtide.O1,
                Partialtide.P1, Partialtide.M4, Partialtide.MS4), 360, 1, aus);
        A.pruefe(gut.toString().equals("[M2, K1, S2, O1, M4, MS4]") && aus.toString().equals("[P1, N2, K2]"), "Rayleigh-Kriterium bei 15 Tagen", "getrennt " + gut + ", ausgelassen " + aus,
                "M2, S2, K1, O1, M4, MS4; ohne N2, K2, P1");
        // 30 Tage mit Rauschen von 5 cm
        SplittableRandom r = new SplittableRandom(3);
        double[] yr = new double[720];
        for (int k = 0; k < yr.length; k++) {
            Instant t = start.plusSeconds(3600L * k);
            yr[k] = Partialtide.M2.wert(t, 1.5, 200) + Partialtide.S2.wert(t, 0.4, 250) + Partialtide.K1.wert(t, 0.08, 30) + Partialtide.O1.wert(t, 0.07, 300) + 0.05 * r.nextGaussian();
        }
        Harmonik.Ergebnis er = Harmonik.analyse(start, 3600, yr, List.of(Partialtide.M2, Partialtide.S2, Partialtide.K1, Partialtide.O1), 1);
        A.hoechstens("30 Tage mit 5 cm Rauschen: Amplitude von M2 [G]", er.tide("M2").amplitude() - 1.5, 0.01, "m");
        A.info("30 Tage mit Rauschen", f("M2 %.4f m, %.2f°; Rest %.1f cm", er.tide("M2").amplitude(), er.tide("M2").phaseGrad(), er.restRms() * 100));
        // Sammler (P5d): viele Orte mit denselben Zeiten wie je eine Analyse
        List<Partialtide> st = List.of(Partialtide.M2, Partialtide.S2, Partialtide.K1, Partialtide.O1);
        Harmonik.Sammler sa = new Harmonik.Sammler(st, 3);
        double[][] ys = new double[3][720];
        for (int k = 0; k < 720; k++) {
            Instant t = start.plusSeconds(3600L * k);
            double[] w = new double[3];
            for (int q = 0; q < 3; q++) w[q] = ys[q][k] = (q == 0 ? yr[k] : Partialtide.M2.wert(t, 0.3 * q, 40 * q) + Partialtide.K1.wert(t, 0.05, 90) + 0.1 * q);
            sa.dazu(t, w);
        }
        double ds = 0;
        for (int q = 0; q < 3; q++) {
            Harmonik.Ergebnis eq = Harmonik.analyse(start, 3600, ys[q], st, 1);
            for (Partialtide p : st) {
                double[] ap = sa.tide(q, sa.index(p.name));
                Harmonik.Tide t = eq.tide(p.name);
                ds = Math.max(ds, Math.max(Math.abs(ap[0] - t.amplitude()), t.amplitude() < 0.01 ? 0 : Math.abs(((ap[1] - t.phaseGrad() + 540) % 360) - 180) * 1e-3));
            }
            ds = Math.max(ds, Math.abs(sa.loesung(q)[0] - eq.mittel()));
        }
        A.hoechstens("Sammler (P5d): drei Orte, 30 Tage, gegen je eine Analyse: Mittel und Amplitude (m), Phase (° · 10⁻³)", ds, 1e-10, "");
        // aus parallelen Aufrufen wie in TideCheck über alle Zellen von G0 (P5d, nach einem Fehler im 30-Tage-Lauf: die Umkehrung
        // wurde von einem Faden gebaut, während ein anderer sie schon las)
        int viele = 4000;
        Harmonik.Sammler sp = new Harmonik.Sammler(st, viele);
        for (int k = 0; k < 720; k++) {
            double[] w = new double[viele];
            for (int c = 0; c < viele; c++) w[c] = ys[c % 3][k];
            sp.dazu(start.plusSeconds(3600L * k), w);
        }
        // acht Runden: jede neue Summe verwirft die Umkehrung, dann fragen alle Fäden zugleich
        java.util.concurrent.atomic.AtomicInteger ausnahmen = new java.util.concurrent.atomic.AtomicInteger();
        double dpm = 0;
        for (int runde = 0; runde < 8; runde++) {
            if (runde > 0) {
                double[] w = new double[viele];
                for (int c = 0; c < viele; c++) w[c] = 0.01 * runde * (c % 3);
                sp.dazu(start.plusSeconds(3600L * (720 + runde)), w);
            }
            double[][] par = new double[viele][];
            java.util.stream.IntStream.range(0, viele).parallel().forEach(c -> {
                try {
                    double[] rp = new double[2 * st.size()];
                    for (int k = 0; k < st.size(); k++) System.arraycopy(sp.tide(c, k), 0, rp, 2 * k, 2);
                    par[c] = rp;
                } catch (RuntimeException x) {
                    ausnahmen.incrementAndGet();
                }
            });
            for (int c = 0; c < viele; c++)
                for (int k = 0; k < st.size(); k++) {
                    double[] ap = sp.tide(c, k), ref = runde == 0 ? sa.tide(c % 3, k) : ap;
                    if (par[c] != null) dpm = Math.max(dpm, Math.max(Math.abs(par[c][2 * k] - ap[0]), Math.abs(par[c][2 * k + 1] - ap[1])));
                    dpm = Math.max(dpm, Math.max(Math.abs(ap[0] - ref[0]), ref[0] < 0.01 ? 0 : Math.abs(((ap[1] - ref[1] + 540) % 360) - 180) * 1e-3));
                }
        }
        A.pruefe(ausnahmen.get() == 0 && dpm <= 1e-12, f("Sammler aus parallelen Aufrufen: %d Orte, acht Runden, jede Tide", viele), f("%d Ausnahmen, Abweichung bis %.1e", ausnahmen.get(), dpm),
                "keine Ausnahme, ≤ 1e-12");
        // Statistik
        double[] o = {1, 2, 3, 4}, m = {1.5, 2.5, 3.5, 4.5};
        A.pruefe(Statistik.rmse(m, o) == 0.5 && Statistik.bias(m, o) == 0.5 && Math.abs(Statistik.willmott(m, o) - (1 - 1 / 21.0)) < 1e-15 && Statistik.willmott(o, o) == 1
                && Math.abs(Statistik.korrelation(m, o) - 1) < 1e-15, "RMSE, Bias, Skill nach Willmott, Korrelation an einem Handbeispiel", f("RMSE %.1f, Bias %.1f, Skill %.6f", Statistik.rmse(m, o),
                Statistik.bias(m, o), Statistik.willmott(m, o)), "0,5; 0,5; 0,952381");
        // Vergleich zweier Felder wie in P0
        Katalog k = Katalog.grundstufe();
        Feld fa = new Feld(k.verlange("water.h"), Probe.G, Feld.Zahl.FLOAT), fb = fa.gleiches();
        for (int i = 0; i < fa.laenge; i++) fa.f[i] = fb.f[i] = 8 + i * 0.01f;
        fb.f[100] = Math.nextUp(Math.nextUp(fb.f[100]));
        fb.f[200] = Float.NaN;
        Statistik.Vergleich v = Statistik.vergleiche(fa, fb, 1e-5);
        A.pruefe(v.schritte() == 2 && v.wo() == 100 && v.nan() == 1 && v.median() == 0 && v.gleich(1e-5) == false && Math.abs(v.groesste() - 2 * Math.ulp(9f)) < 1e-12,
                "Vergleich zweier Felder: größte Abweichung in float-Schritten, NaN zählt", f("%.1e m = %.0f Schritte in Zelle %d, %d NaN", v.groesste(), v.schritte(), v.wo(), v.nan()), "2 Schritte, 1 NaN");
        // Bild
        double de = Math.max(Math.abs(Bildvergleich.deltaE00(50, 2.6772, -79.7751, 50, 0, -82.7485) - 2.0425), Math.max(
                Math.abs(Bildvergleich.deltaE00(50, 3.1571, -77.2803, 50, 0, -82.7485) - 2.8615), Math.abs(Bildvergleich.deltaE00(50, 2.8361, -74.0200, 50, 0, -82.7485) - 3.4412)));
        A.hoechstens("ΔE₀₀ gegen drei Prüfpaare von Sharma u. a.", de, 1e-4, "");
        java.awt.image.BufferedImage b1 = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), b2 = new java.awt.image.BufferedImage(8, 8,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 64; i++) {
            b1.setRGB(i % 8, i / 8, 0x4080C0);
            b2.setRGB(i % 8, i / 8, i < 16 ? 0x4C8CC0 : 0x4080C0);
        }
        Bildvergleich.Ergebnis bv = Bildvergleich.vergleiche(b1, b2, 1);
        A.pruefe(bv.anteilUeber() == 0.25 && bv.groesste() > 2 && bv.groesste() < 8 && Math.abs(bv.mittel() - bv.groesste() / 4) < 1e-12, "Bildvergleich: ein Viertel der Bildpunkte verfärbt (Rot und Grün +12 von 255)",
                f("ΔE₀₀ im Mittel %.2f, größter %.2f, %.0f %% über 1", bv.mittel(), bv.groesste(), bv.anteilUeber() * 100), "25 % über 1");
    }

    private static double max(double[] a) { double m = -Double.MAX_VALUE; for (double v : a) m = Math.max(m, v); return m; }
    private static double min(double[] a) { double m = Double.MAX_VALUE; for (double v : a) m = Math.min(m, v); return m; }

    private CoreCheck() { }

    // ------------------------------------------------------------------ 13 Feingelände

    /**
     * Ein Stapel aus zwei Stufen: 10 m mit einer Ebene, 1 m nur in der mittleren Kachel, mit einem leeren Pixel. Geprüft
     * werden Ablage, die Regel „fein vor grob, Pixel für Pixel“ und das Feingelände unter einem Gitter mit Subgrid.
     */
    private static void gelaende() throws IOException {
        Path d = Files.createTempDirectory("corecheck-gelaende");
        try {
            Gelaende.Stufe g = Gelaende.Stufe.anlegen(d.resolve("10m").toFile(), 10, 50, 995, 1995, 1, 1, "Ebene");
            float[] e = new float[50 * 50];
            for (int r = 0; r < 50; r++) for (int c = 0; c < 50; c++) e[r * 50 + c] = (float) (0.01 * g.ost(c) + 0.02 * g.nord(r));
            g.schreibe(0, 0, e);
            Gelaende.Stufe f = Gelaende.Stufe.anlegen(d.resolve("1m").toFile(), 1, 100, 1000, 2000, 3, 3, "Mitte");
            float[] m = new float[100 * 100];
            for (int r = 0; r < 100; r++) for (int c = 0; c < 100; c++) m[r * 100 + c] = (float) (5 + 0.001 * ((c * 7 + r * 13) % 97));
            m[40 * 100 + 30] = Float.NaN;
            f.schreibe(1, 1, m);
            Gelaende st = Gelaende.ordner(d.toFile());
            Gelaende.Stufe f2 = st.stufen()[0];
            boolean ablage = f2.zelle == 1 && f2.kachelZellen == 100 && f2.kachelnOst == 3 && f2.suedwestNord == 2000 && Arrays.equals(f2.kachel(1, 1), m)
                    && f2.kachel(0, 0).length == 0 && st.stufen()[1].zelle == 10;
            A.pruefe(ablage, "Ablage: STUFE.txt und Kacheln float32 Little Endian, Zeilen von Nord; fehlende Kachel ohne Werte", ablage ? "gleich" : "falsch", "gleich");
            // Regel
            double xm = 1100 + 17.5, ym = 2200 - 23.5;                                             // Mitte eines Pixels der mittleren Kachel
            double xl = 1100 + 30.5, yl = 2200 - 40.5;                                              // das leere Pixel
            double xa = 1050.5, ya = 2250.5;                                                        // Kachel ohne Datei
            float vm = st.hoehe(xm, ym, 1), vl = st.hoehe(xl, yl, 1), va = st.hoehe(xa, ya, 1), v10 = st.hoehe(xm, ym, 10);
            float sm = m[23 * 100 + 17], eb = g.bilinear(xl, yl), ea = g.bilinear(xa, ya), e10 = g.bilinear(xm, ym);
            boolean regel = vm == sm && vl == eb && va == ea && v10 == e10 && Math.abs(eb - (0.01 * xl + 0.02 * yl)) < 1e-4;
            A.pruefe(regel, "Regel: Abtastung 1 m an einer Pixelmitte genau die Kachel; leeres Pixel und Kachel ohne Datei aus der 10-m-Stufe (bilinear, Ebene); Abtastung 10 m aus der 10-m-Stufe",
                    f("%.4f, %.4f, %.4f, %.4f", vm, vl, va, v10), f("%.4f, %.4f, %.4f, %.4f", sm, eb, ea, e10));
            // Feingelände unter einem Gitter: Pixel (i, j) von Südwest, Mitte bei x0 + (i + ½)·dx/fein
            Gitter gi = Gitter.utmBereich("F", 32, 1100, 2100, 1200, 2200, 20);
            Subgrid.Feingelaende fg = st.feingelaende(gi, 20);
            double[] z = new double[102 * 102];
            fg.lies(-1, -1, 102, 102, z);
            int gleich = 0, n = 0;
            for (int j = 0; j < 100; j++)
                for (int i = 0; i < 100; i++) {
                    if (i == 30 && j == 100 - 1 - 40) continue;
                    n++;
                    if (z[(j + 1) * 102 + i + 1] == m[(99 - j) * 100 + i]) gleich++;
                }
            Subgrid sg = Subgrid.bauen(gi, 20, fg);
            double v = 0;
            for (int c = 0; c < gi.zellen(); c++) v += sg.volumen(c, 6);
            double soll = 0;
            for (int j = 0; j < 100; j++) for (int i = 0; i < 100; i++) soll += Math.max(0, 6 - z[(j + 1) * 102 + i + 1]);
            A.pruefe(gleich == n && Math.abs(v - soll) < 1e-6 * soll && !Double.isNaN(z[0]),
                    "Feingelände unter einem Gitter 20 m mit 20 Pixeln je Zelle: Pixel gleich der Kachel (Süd nach Nord), Rand aus der 10-m-Stufe, Subgrid ohne Lücke",
                    f("%d von %d Pixeln gleich, Volumen bei η = 6 m %.6g von %.6g m³", gleich, n, v, soll), "gleich");
            // Mindesthöhe im Stapel (seit P6e): Abtastung 1 m wie Gelaende.mindestens beim Backen, gröber unverändert
            Gelaende.Stufe mh = Gelaende.Stufe.anlegen(d.resolve("mh").toFile(), 1, 100, 1000, 2000, 3, 3, "Mindesthöhe");
            float[] k = new float[100 * 100];
            Arrays.fill(k, Float.NaN);
            for (int r = 0; r < 100; r++) k[r * 100 + 50] = 5.05f;                                      // ein Deich in Spalte 50
            mh.schreibe(1, 1, k);
            Gelaende smh = st.mitMindesthoehe(mh);
            Subgrid.Feingelaende fb = Gelaende.mindestens(st.feingelaende(gi, 20), mh, gi, 20, null);
            double[] zb = new double[100 * 100];
            fb.lies(0, 0, 100, 100, zb);
            int gl = 0, hoch = 0;
            for (int j = 0; j < 100; j++)
                for (int i = 0; i < 100; i++) {
                    float hm = smh.hoehe(1100 + i + 0.5, 2100 + j + 0.5, 1);
                    if (hm == (float) zb[j * 100 + i]) gl++;
                    if (hm > st.hoehe(1100 + i + 0.5, 2100 + j + 0.5, 1)) hoch++;
                }
            boolean grob = smh.hoehe(1150.5, 2150.5, 10) == st.hoehe(1150.5, 2150.5, 10);
            A.pruefe(gl == 10000 && hoch > 0 && grob, "Mindesthöhe im Stapel (P6e): Abtastung 1 m gegen Gelaende.mindestens beim Backen; Abtastung 10 m unverändert",
                    f("%d von 10000 Pixeln gleich, %d gehoben; 10 m %s", gl, hoch, grob ? "gleich" : "VERSCHIEDEN"), "alle gleich, gehoben > 0");
        } finally {
            try (Stream<Path> w = Files.walk(d)) {
                w.sorted(Comparator.reverseOrder()).forEach(q -> q.toFile().delete());
            }
        }
    }

    // ------------------------------------------------------------------ 14 Gebiet

    /** Gitterkette aus gebiet.txt, Packung, ground.z und Bauwerke hin und zurück; Leser einer Stufe. */
    private static void gebiet() throws IOException {
        Path d = Files.createTempDirectory("corecheck-gebiet");
        try {
            Gitter g0 = Gitter.geografisch("G0", -15, 43, 1 / 12.0, 1 / 18.0, 336, 378), g1 = Gitter.utm("G1", 32, 434000, 6011000, 1000, 30, 20);
            int fein = 8;
            double[] z = new double[g1.nx * fein * g1.ny * fein];
            SplittableRandom r = new SplittableRandom(14);
            for (int j = 0; j < g1.ny * fein; j++)
                for (int i = 0; i < g1.nx * fein; i++) z[j * g1.nx * fein + i] = -3 + 0.02 * i + 0.01 * j + 0.3 * r.nextDouble();
            Subgrid sg = Subgrid.bauen(g1, fein, Subgrid.aus(z, g1.nx * fein, g1.ny * fein));
            Subgrid.Packung p = sg.packen();
            float[] gz = new float[g1.zellen()], nass = new float[g0.zellen()];
            for (int c = 0; c < gz.length; c++) gz[c] = (float) (sg.hoechste(c) - sg.volumen(c, sg.hoechste(c)) / sg.zellflaeche);
            for (int c = 0; c < nass.length; c++) nass[c] = (c % 7) / 6f;
            Files.writeString(d.resolve("gebiet.txt"), "# Probe\nrezept\tname\tProbe\n" + Gebiet.zeile(g0, 0) + "\n" + Gebiet.zeile(g1, fein) + "\nquelle\tx\ty\t1\t00\tStand\tLizenz\n");
            Gebiet.schreibePackung(d.resolve("G1").resolve("subgrid.bin").toFile(), p, g1, fein);
            Gebiet.schreibeF32(d.resolve("G1").resolve("ground.z.f32").toFile(), gz);
            Gebiet.schreibeF32(d.resolve("G0").resolve("nass.f32").toFile(), nass);
            Files.writeString(d.resolve("G1").resolve("bauwerke.txt"), """
                    # Bauwerke
                    gitter G1 434000.000 6011000.000 1000.000 30 20
                    fein 3
                    ring 1 12 12000000 2.010 3.430 107 22 5000 4000 0 Hooge
                    ring 2 3 3000000 nan nan -1 0 0 0 1 -
                    siel Siel1 40 434500.0 6011700.0 0.070 3.180 1.00 0.60 node/1
                    kante 40 x 9 1 siel 0.070 2.500 1 node/1 0.070 2.900 nan
                    kante 700 y 9 2 deich 2.810 2.950 0 way/2,way/3 2.810 2.950 3.100
                    """);
            Gebiet ge = Gebiet.lies(d.toFile());
            List<Gebiet.Eintrag> k = ge.kette();
            A.pruefe(k.size() == 2 && k.get(0).gitter().gleich(g0) && k.get(1).gitter().gleich(g1) && k.get(1).fein() == fein && k.get(0).fein() == 0,
                    "gebiet.txt: Gitterkette (geographisch und UTM) hin und zurück", k.size() + " Gitter, " + (k.size() == 2 && k.get(0).gitter().gleich(g0) && k.get(1).gitter().gleich(g1) ? "gleich" : "VERSCHIEDEN"), "gleich");
            Subgrid.Packung q = ge.packung("G1");
            boolean gleich = Arrays.equals(q.basis(), p.basis()) && Arrays.equals(q.tabelle(), p.tabelle()) && q.stufen() == p.stufen() && q.zellflaeche() == p.zellflaeche();
            double dv = 0;
            for (int c = 0; c < g1.zellen(); c++) for (double eta = -3; eta <= 2; eta += 0.25) dv = Math.max(dv, Math.abs(q.volumen(c, eta) - p.volumen(c, eta)));
            A.pruefe(gleich && dv == 0, f("subgrid.bin: Packung von %d Zellen und %d Kanten (%d Byte) hin und zurück, Volumen an 21 Wasserständen", p.zellen(), p.kanten(), p.bytes()),
                    gleich ? f("bitgleich, Volumen gleich (%.1e)", dv) : "VERSCHIEDEN", "bitgleich");
            A.pruefe(Arrays.equals(ge.groundZ("G1"), gz) && Arrays.equals(ge.nass("G0"), nass), "ground.z.f32 und nass.f32 hin und zurück", "gleich", "bitgleich");
            Gebiet.Bauten b = ge.bauwerke("G1");
            boolean bw = b.fein() == 3 && b.ringe().size() == 2 && b.ringe().get(0).namen().equals(List.of("Hooge")) && Double.isNaN(b.ringe().get(1).niedrigste())
                    && b.siele().size() == 1 && b.siele().get(0).kante() == 40 && b.siele().get(0).sohle() == 0.07 && b.kanten().size() == 2 && b.kanten().get(0).angehoben()
                    && Float.isNaN(b.kanten().get(0).profil()[2]) && b.kanten().get(1).querX() == false && b.kanten().get(1).krone() == 2.81 && b.kanten().get(1).profil().length == 3;
            A.pruefe(bw, "bauwerke.txt: Ringe (mit Namen, ohne Krone), Siel, Kanten mit Profil und nan", f("%d Ringe, %d Siele, %d Kanten", b.ringe().size(), b.siele().size(), b.kanten().size()), "gelesen wie geschrieben");
            // Leser einer Stufe
            Gelaende.Stufe st = Gelaende.Stufe.anlegen(d.resolve("st").toFile(), 10, 40, 995, 1995, 2, 2, "Leser");
            for (int ty = 0; ty < 2; ty++)
                for (int tx = 0; tx < 2; tx++) {
                    float[] v = new float[1600];
                    for (int c = 0; c < 1600; c++) v[c] = tx * 10000 + ty * 1000 + c;
                    if (tx + ty < 2) st.schreibe(tx, ty, v);
                }
            Gelaende.Stufe.Leser l = st.leser();
            int falsch = 0;
            for (int n = 0; n < 2000; n++) {
                int sp = r.nextInt(-3, 83), ze = r.nextInt(-3, 83);
                float a = st.wert(sp, ze), c = l.wert(sp, ze);
                double ost = st.ost(Math.max(0, Math.min(79, sp))) + r.nextDouble(-5, 5), nord = st.nord(Math.max(0, Math.min(79, ze))) + r.nextDouble(-5, 5);
                float e = l.imPixel(ost, nord), soll = st.wert((int) Math.floor((ost - 995) / 10), (int) Math.floor((1995 + 800 - nord) / 10));
                if (Float.floatToIntBits(a) != Float.floatToIntBits(c) || Float.floatToIntBits(e) != Float.floatToIntBits(soll)) falsch++;
            }
            A.pruefe(falsch == 0, "Stufe.Leser: Pixel und Pixel am Ort gegen Stufe.wert, 2000 Stichproben über vier Kacheln (eine fehlt)", falsch + " falsch", "0");
        } finally {
            try (Stream<Path> w = Files.walk(d)) {
                w.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
            }
        }
    }

    // ------------------------------------------------------------------ 15 Gradraster (P5b)

    private static void gradraster() throws IOException {
        Path d = Files.createTempDirectory("corecheck-p5b");
        try {
            // Gradraster: bilinear über gültige Punkte, nächster Punkt, außerhalb
            Gradraster r = new Gradraster(-2, 50, 0.25, 0.125, 9, 13);
            float[] f = new float[r.punkte()];
            for (int k = 0; k < f.length; k++) f[k] = (float) (3 + 0.5 * r.laenge(k % r.nx()) - 0.25 * r.breite(k / r.nx()));
            SplittableRandom q = new SplittableRandom(15);
            double fehler = 0;
            for (int n = 0; n < 1000; n++) {
                double lo = q.nextDouble(-2, 0), la = q.nextDouble(50, 51.5);
                fehler = Math.max(fehler, Math.abs(r.gewichte(lo, la, null, 0, 1, 0).wert(f, 0, 1) - (3 + 0.5 * lo - 0.25 * la)));
            }
            A.hoechstens("Gradraster: bilinear an 1 000 Orten gegen eine lineare Funktion", fehler, 1e-5, "");
            float[] g = f.clone();
            int a = r.nx() * 4 + 3;
            g[a] = Float.NaN;
            Gradraster.Gewichte w = r.gewichte(r.laenge(3) + 0.1, r.breite(4) + 0.05, g, 0, 1, 0);
            double sw = 0;
            boolean ohneNaN = true;
            for (int k = 0; k < w.index().length; k++) {
                sw += w.gewicht()[k];
                if (w.index()[k] == a) ohneNaN = false;
            }
            Arrays.fill(g, Float.NaN);
            g[r.nx() * 7 + 6] = 1;
            Gradraster.Gewichte nah = r.gewichte(r.laenge(4) + 0.1, r.breite(6) + 0.05, g, 0, 1, 2), weit = r.gewichte(r.laenge(1), r.breite(1), g, 0, 1, 2);
            A.pruefe(w.index().length == 3 && Math.abs(sw - 1) < 1e-12 && ohneNaN && nah != null && nah.naechster() && nah.index()[0] == r.nx() * 7 + 6 && weit == null
                            && r.gewichte(-2.5, 50.5, null, 0, 1, 0) == null,
                    "Gradraster: ein Punkt NaN – drei Gewichte mit Summe 1; alle vier NaN – der nächste gültige im Umkreis von 2 Punkten, weiter weg keiner; außerhalb keiner",
                    f("%d Gewichte (Summe %.12f), nächster %s, weit %s", w.index().length, sw, nah == null ? "–" : nah.index()[0], weit), "");
            // Tidenfeld: Ablage und Wasserstand gegen Partialtide.wert
            List<Partialtide> tiden = List.of(Partialtide.M2, Partialtide.S2, Partialtide.K1, Partialtide.O1);
            Gradraster rt = new Gradraster(6, 53, 0.125, 0.125, 17, 9);
            float[][] wt = new float[tiden.size()][2 * rt.punkte()];
            for (int t = 0; t < tiden.size(); t++)
                for (int k = 0; k < rt.punkte(); k++) {
                    double amp = (t == 0 ? 1.2 : 0.3 / (t + 1)) * (1 + 0.01 * (k % rt.nx())), ph = Math.toRadians(300 + 7 * t + 2 * (k / rt.nx()));
                    wt[t][2 * k] = (float) (amp * Math.cos(ph));
                    wt[t][2 * k + 1] = (float) (amp * Math.sin(ph));
                }
            wt[0][2 * 5] = wt[0][2 * 5 + 1] = Float.NaN;
            Tidenfeld tf = new Tidenfeld(rt, tiden, wt);
            tf.schreibe(d.toFile(), "# CoreCheck");
            Tidenfeld tl = Tidenfeld.lies(d.toFile());
            boolean gleich = tl.tiden.equals(tiden) && tl.raster.equals(rt);
            for (int t = 0; t < tiden.size(); t++) gleich &= Arrays.equals(tl.werte(t), wt[t]);
            double[][] kon = tl.konstanten(7.03, 53.71, 0);
            double de = 0;
            for (int n = 0; n < 200; n++) {
                Instant t = Instant.parse("2026-10-12T00:00:00Z").plusSeconds(n * 3637L);
                double soll = 0;
                for (int k = 0; k < tiden.size(); k++) {
                    double[] ap = Tidenfeld.amplitudePhase(kon[k]);
                    soll += tiden.get(k).wert(t, ap[0], ap[1]);
                }
                de = Math.max(de, Math.abs(Tidenfeld.eta(kon, tl.faktoren(t)) - soll));
            }
            A.pruefe(gleich && de < 1e-9, "Tidenfeld: geschrieben und gelesen bitgleich (NaN an Land eingeschlossen); Wasserstand aus den Faktoren gegen Σ Partialtide.wert, 200 Zeiten",
                    f("%s, |Δη| bis %.1e m", gleich ? "bitgleich" : "verschieden", de), "bitgleich, < 1e-9 m");
            // Wetterfeld: Ablage, linear in der Zeit, bilinear im Ort
            Gradraster rw = new Gradraster(-1, 54, 0.25, 0.25, 5, 4);
            Instant s0 = Instant.parse("2026-10-25T00:00:00Z");
            Path dw = Files.createDirectories(d.resolve("w"));
            Wetterfeld.schreibe(dw.toFile(), rw, s0, 3600, 3, List.of("u10", "v10", "msl"), (t, k) -> {
                float[] x = new float[rw.punkte()];
                for (int p = 0; p < x.length; p++) x[p] = (float) ((k == 2 ? 101000 : 5) + (k + 1) * (t * 10 + rw.laenge(p % rw.nx()) * 2 + rw.breite(p / rw.nx())));
                return x;
            }, "# CoreCheck");
            Wetterfeld wf = Wetterfeld.lies(dw.toFile());
            Gradraster.Gewichte gw = rw.gewichte(-0.6, 54.3, null, 0, 1, 0);
            double dw0 = 0;
            for (int k = 0; k < 3; k++) {
                double soll = (k == 2 ? 101000 : 5) + (k + 1) * (15 + -0.6 * 2 + 54.3);
                dw0 = Math.max(dw0, Math.abs(wf.wert(k, s0.plusSeconds(5400), gw) - soll) / (k + 1));
            }
            A.pruefe(dw0 < 2e-3 && wf.zeiten == 3 && wf.groessen.equals(List.of("u10", "v10", "msl")) && Double.isNaN(wf.wert(0, s0.plusSeconds(3 * 3600), gw)),
                    "Wetterfeld: geschrieben, abgebildet gelesen; nach 1,5 h zwischen den Punkten gegen die lineare Funktion; nach dem Ende NaN", f("|Δ| bis %.1e", dw0), "< 2e-3 (float)");
            // Pegel: Ablage mit Analyse, Kennwerten, Vorhersage
            Instant p0 = Instant.parse("2026-10-12T00:00:00Z");
            float[] y = new float[30 * 24 * 6];
            for (int k = 0; k < y.length; k++) {
                Instant t = p0.plusSeconds(600L * k);
                y[k] = (float) (0.1 + Partialtide.M2.wert(t, 1.3, 350) + Partialtide.S2.wert(t, 0.4, 40) + Partialtide.K1.wert(t, 0.08, 20));
            }
            y[100] = Float.NaN;
            Harmonik.Ergebnis h = Harmonik.analyse(p0, 600, toDouble(y), List.of(Partialtide.M2, Partialtide.S2, Partialtide.K1), 1);
            Pegel pg = new Pegel("PROBE_Ö", "123", "NORDSEE", 8.5, 54.5, -5.01, p0, 600, y, 1, 0,
                    java.util.Map.of("MThw", new Pegel.Kennwert("MThw", 1.5, "2011-11-01", "2020-10-31", null)), h);
            Pegel.schreibe(d.toFile(), List.of(pg), "# CoreCheck");
            Pegel pl = Pegel.lies(d.toFile()).get("PROBE_Ö");
            Instant tm = p0.plusSeconds(600L * 50 + 300);
            double dv = Math.abs(pl.vorhersage(tm) - h.wert(tm)), dw1 = Math.abs(pl.wert(tm) - 0.5 * (y[50] + y[51]));
            boolean ok = pl != null && Arrays.equals(pl.werte, y) && pl.kennwert("MThw") == 1.5 && pl.analyse.tide("M2") != null
                    && Math.abs(pl.analyse.tide("M2").amplitude() - 1.3) < 1e-4 && Double.isNaN(pl.wert(p0.plusSeconds(600L * 100))) && Pegel.datei("PROBE_Ö").equals("PROBE_OE.f32");
            A.pruefe(ok && dv < 1e-4 && dw1 < 1e-6, "Pegel: Reihe, Kennwerte und Analyse geschrieben und gelesen; Vorhersage aus den abgelegten Tiden (auf 0,1 mm abgelegt), Wert zwischen zwei Schritten, NaN bleibt NaN",
                    f("M2 %.4f m %.2f°, |Δ Vorhersage| %.1e m, |Δ Wert| %.1e m", pl.analyse.tide("M2").amplitude(), pl.analyse.tide("M2").phaseGrad(), dv, dw1), "< 1e-4 m");
        } finally {
            try (Stream<Path> w = Files.walk(d)) {
                w.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
            }
        }
    }

    static double[] toDouble(float[] f) {
        double[] r = new double[f.length];
        for (int k = 0; k < f.length; k++) r[k] = f[k];
        return r;
    }
}
