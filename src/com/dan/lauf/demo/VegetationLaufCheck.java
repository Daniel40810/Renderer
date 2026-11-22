package com.dan.lauf.demo;

import com.dan.core.archiv.Archiv;
import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Ueberflutung;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Gestalt;
import com.dan.core.szene.Zonen;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Lauf;
import com.dan.lauf.Stroemung;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.vegetation.Salzwiese;
import com.dan.physics.vegetation.VegetationModul;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.dan.core.pruef.Attest.f;

/**
 * Salzwiese im Lauf (P13b, Werkbuch HalligHooge Teil A, 12.14, Datei 88; F151–F154 A).
 * <ul>
 * <li><b>Teil statistik</b> (Lauf B1, F151 A): Kette G0–G2 über eine Spring-Nipp-Folge (1 Tag Einschwingen, 29 Tiden,
 * Wind aus ERA5 Oktober 2026); ein Leser sammelt auf G2 die Scheitel jeder Tide ({@link Ueberflutung}) und legt die
 * Statistik ab ({@code --ziel}, Vorgabe {@code _p13/ueberflutung}); danach läuft die Aufbereitung, Schritt zonen.</li>
 * <li><b>Teil verdrahtung</b> (Datei 89): ein kleiner Bus mit hydro auf dem Prozessor, Rand, vegetation und dem
 * Verbund {@code surf.z0}, Boden von außen wie in {@link Stroemung}: hydro liest die Rauheit der Salzwiese, wenn
 * {@code rauheitGitter} vor dem Anmelden steht und die Salzwiese vor hydro angemeldet ist (Takt.beginn folgt der Anmeldung).</li>
 * <li><b>Teil bilanz</b> (Prüfstein 13, Datei 87): sediment mit Salzwiese in einem kleinen Becken mit Tide (hydro auf dem
 * Prozessor), ohne und mit Bedeckung; aeolian mit Salzwiese im Nachlauf zu {@code --archiv-flugsand} (P10c,
 * {@code archiv_m1b}). Massenbilanz ≤ 10⁻³ wie in P9b/P10c.</li>
 * <li><b>Teil nachlauf</b> (F154 A, Prüfstein 6): {@link Stroemung} aus dem Archiv von Xaver ohne und mit Salzwiese;
 * Strom über der bewachsenen Hallig, solange sie unter Wasser steht, und Scheitel am Pegel Hooge.</li>
 * <li><b>Teil bild</b> (Prüfsteine 7, 14, 10): Szenario ruhig mit Salzwiese an und aus (aus gegen die Gestalt ohne
 * Zonen: bitgleich), Kosten, Bilder Sommer und Winter {@code docs/status/P13b_Salzwiese.png}.</li>
 * </ul>
 * Attest {@code docs/status/P13b_Salzwiese.md}.
 */
public final class VegetationLaufCheck {
    private static final Attest A = new Attest("com.dan.lauf (Salzwiese)", Lauf.VERSION);
    static String gebiet = "D:\\Daten\\HalligHooge\\gebiet", ziel = "D:\\Daten\\HalligHooge\\_p13\\ueberflutung", teil = "statistik";
    static String archiv = "D:\\Daten\\HalligHooge\\_p13\\archiv_xaver", nachlauf = "D:\\Daten\\HalligHooge\\_p13\\nachlauf";
    static String szenen = "..\\HalligHooge\\src\\szenen\\Hooge.szenen", szenario = "ruhig", ansichten = "halme,lahnungen,warften";
    static Instant start = Instant.parse("2026-10-01T00:00:00Z");
    static double einschwingen = 86400;
    /** Zeit der Bilder (Teil bild): Mittag im Szenario ruhig. */
    static String zeit = "2026-10-30T11:00:00Z";
    /** Archiv für den Nachlauf von aeolian (Teil bilanz): das aus P10c. */
    static String flugsand = "D:\\Daten\\HalligHooge\\_p9c\\archiv_m1b";
    static int tiden = 29;
    static double hooge[] = {470410, 6048090};
    static final int BREITE = 960, HOEHE = 540;

    public static void main(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            String v = args[i + 1];
            switch (args[i]) {
                case "--gebiet" -> gebiet = v;
                case "--ziel" -> ziel = v;
                case "--teil" -> teil = v;
                case "--archiv" -> archiv = v;
                case "--nachlauf" -> nachlauf = v;
                case "--szenen" -> szenen = v;
                case "--szenario" -> szenario = v;
                case "--ansichten" -> ansichten = v;
                case "--start" -> start = Instant.parse(v);
                case "--tiden" -> tiden = Integer.parseInt(v);
                case "--einschwingen" -> einschwingen = Double.parseDouble(v) * 3600;
                case "--zeit" -> zeit = v;
                case "--archiv-flugsand" -> flugsand = v;
                default -> { }
            }
        }
        System.out.println("VegetationLaufCheck – Salzwiese (Werkbuch HalligHooge Teil A, P13b), Teile " + teil);
        long n0 = System.nanoTime();
        try {
            if (teil.contains("statistik")) statistik(new File(gebiet), new File(ziel));
            if (teil.contains("verdrahtung")) verdrahtung();
            if (teil.contains("bilanz")) { bilanzSediment(); bilanzFlugsand(new File(gebiet), Path.of(flugsand)); }
            if (teil.contains("nachlauf")) nachlauf(new File(gebiet), Path.of(archiv), Path.of(nachlauf));
            if (teil.contains("bild")) bild(new File(szenen));
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.1f min", (System.nanoTime() - n0) / 6e10));
        String name = teil.equals("statistik") ? "P13b_Ueberflutung.md" : "P13b_Salzwiese.md";
        Path p = A.schreibe(Path.of("docs", "status", name), "P13b – VegetationLaufCheck (" + teil + ")");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Verdrahtung (Datei 89)

    /** Schreibt den Rand {@code tide.bc} (Wasserstand 0, Fluss 0) für den kleinen Bus. */
    static final class Rand implements Modul {
        final Gitter g;
        Rand(Gitter g) { this.g = g; }
        @Override public String name() { return "tide"; }
        @Override public void anmelden(Anmeldung a) { a.kopplung(300); a.schreibt("tide.bc", "m, m²/s", Ort.RAND, g, Feld.Zahl.FLOAT); }
        @Override public void beginn(double t0, Bus b) { b.lege(this, "tide.bc", g, t0); }
        @Override public void schritt(double t0, double t1, Bus b) { b.lege(this, "tide.bc", g, t1); }
    }

    static void verdrahtung() throws Exception {
        A.teil("Verdrahtung am Bus mit Salzwiese (Datei 89, Fehler aus Lauf B)");
        Gitter g = Gitter.utm("G3", 32, 1000, 2000, 20, 25, 25);
        double[] zf = new double[100 * 100], nf = new double[100 * 100];
        java.util.Arrays.fill(zf, -1.0);
        java.util.Arrays.fill(nf, 0.03);
        com.dan.core.gitter.Subgrid sg = com.dan.core.gitter.Subgrid.bauen(g, 4, com.dan.core.gitter.Subgrid.aus(zf, 100, 100), com.dan.core.gitter.Subgrid.aus(nf, 100, 100), com.dan.core.gitter.Subgrid.STUFEN);
        com.dan.physics.hydro.Hydro h = new com.dan.physics.hydro.Hydro(g, sg.packen());
        com.dan.physics.hydro.HydroModul hy = new com.dan.physics.hydro.HydroModul(List.<com.dan.physics.hydro.Hydro[]>of(new com.dan.physics.hydro.Hydro[] {h}),
                new com.dan.physics.hydro.Rechnung.Prozessor(), 300, false, java.util.Set.of());
        hy.rauheitGitter = java.util.Set.of(g.name);
        hy.boden = java.util.Set.of(g.name);
        hy.bodenquelle = (t, ziel) -> false;   // wie Stroemung: Boden von außen, hydro liest surf.z0 schon in beginn (Fehler aus Lauf B2)
        int n = 500;
        byte[] d = new byte[2 * n * n];
        for (int k = 0; k < n * n; k++) { d[2 * k] = Zonen.OBEN; d[2 * k + 1] = (byte) Zonen.hoeheByte(0.5); }
        Zonen zo = new Zonen(1, n, List.of(new Zonen.Kachel(1000, 2000, d)));
        Bus bus = new Bus(Katalog.grundstufe());
        bus.anmelden(new Rand(g));
        bus.anmelden(new VegetationModul(List.of(g), zo, null, new Salzwiese(), 300, Instant.parse("2026-07-22T00:00:00Z")))
                .anmelden(new com.dan.core.bus.Verbund(bus.katalog, "surf.z0", g, Feld.Zahl.FLOAT, 300, "vegetation"));
        bus.anmelden(hy);   // nach der Salzwiese, wie in Stroemung, Lauf und Nachlauf
        String fehler = "";
        try {
            bus.verdrahten();
            com.dan.core.takt.Takt t = new com.dan.core.takt.Takt(bus);
            t.beginn(0);
            t.bis(600);
        } catch (RuntimeException x) {
            fehler = x.getMessage();
        }
        A.pruefe(fehler.isEmpty(), "Verdrahtung: hydro liest surf.z0 aus dem Verbund der Salzwiese, 600 s gerechnet", fehler.isEmpty() ? "ok" : fehler, "ohne Fehler");
    }

    // ------------------------------------------------------------------ Bilanz (Prüfstein 13)

    /** Rand mit Tide im Westen: Wasserstand a · sin(2π t / T), sonst geschlossen. */
    static final class Westrand implements Modul {
        final Gitter g;
        final double a, periode;
        Westrand(Gitter g, double a, double periode) { this.g = g; this.a = a; this.periode = periode; }
        @Override public String name() { return "tide"; }
        @Override public void anmelden(Anmeldung an) { an.kopplung(60); an.schreibt("tide.bc", "m, m²/s", Ort.RAND, g, Feld.Zahl.DOUBLE); }
        @Override public void beginn(double t0, Bus b) { lege(b, t0); }
        @Override public void schritt(double t0, double t1, Bus b) { lege(b, t1); }
        private void lege(Bus b, double t) {
            Feld bc = b.schreibfeld(this, "tide.bc");
            int n = 2 * (g.nx + g.ny);
            for (int q = 0; q < n; q++) {
                int[] c = g.randZelle(q);
                bc.d[2 * q] = c[0] == 0 ? a * Math.sin(2 * Math.PI * t / periode) : Double.NaN;
                bc.d[2 * q + 1] = 0;
            }
            b.lege(this, "tide.bc", t);
        }
    }

    /** Becken 1000 × 200 m, Boden von −2 m im Westen auf +0,6 m im Osten; östliche Hälfte untere Salzwiese. */
    static double[] sedimentLauf(boolean mitSalzwiese) {
        Gitter g = Gitter.utm("G3", 32, 1000, 2000, 20, 50, 10);
        int fein = 4, w = g.nx * fein, hh = g.ny * fein;
        double[] zf = new double[w * hh], nf = new double[w * hh];
        for (int j = 0; j < hh; j++)
            for (int i = 0; i < w; i++) { zf[i + w * j] = -2.0 + 2.6 * (i + 0.5) / w; nf[i + w * j] = 0.025; }
        com.dan.core.gitter.Subgrid sg = com.dan.core.gitter.Subgrid.bauen(g, fein, com.dan.core.gitter.Subgrid.aus(zf, w, hh), com.dan.core.gitter.Subgrid.aus(nf, w, hh), com.dan.core.gitter.Subgrid.STUFEN);
        com.dan.physics.hydro.Hydro h = new com.dan.physics.hydro.Hydro(g, sg.packen());
        com.dan.physics.hydro.HydroModul hy = new com.dan.physics.hydro.HydroModul(List.<com.dan.physics.hydro.Hydro[]>of(new com.dan.physics.hydro.Hydro[] {h}),
                new com.dan.physics.hydro.Rechnung.Prozessor(), 60, false, java.util.Set.of());
        if (mitSalzwiese) hy.rauheitGitter = java.util.Set.of(g.name);
        int n = 500;
        byte[] d = new byte[2 * n * n];
        // Zonen-Kachel 500 × 500 m ab Ost 1500 (östliche Hälfte des Beckens)
        for (int k = 0; k < n * n; k++) { d[2 * k] = Zonen.UNTEN; d[2 * k + 1] = (byte) Zonen.hoeheByte(0.1); }
        Zonen zo = new Zonen(1, n, List.of(new Zonen.Kachel(1500, 2000, d)));
        com.dan.physics.sediment.SedimentModul sm = new com.dan.physics.sediment.SedimentModul(g, h.packung, null, new com.dan.physics.sediment.SedimentModul.Prozessor(), 60, false, 0.02);
        sm.vegetation = mitSalzwiese;
        Bus bus = new Bus(Katalog.grundstufe());
        bus.anmelden(new Westrand(g, 1.2, 3 * 3600));
        if (mitSalzwiese) bus.anmelden(new VegetationModul(List.of(g), zo, null, new Salzwiese(), 60, Instant.parse("2026-07-22T00:00:00Z")))
                .anmelden(new com.dan.core.bus.Verbund(bus.katalog, "surf.z0", g, Feld.Zahl.FLOAT, 60, "vegetation"));
        bus.anmelden(hy).anmelden(sm);
        bus.verdrahten();
        com.dan.core.takt.Takt t = new com.dan.core.takt.Takt(bus);
        t.beginn(0);
        t.bis(2 * 3 * 3600);
        double[] b = sm.bilanz();
        return new double[] {b[0], b[1], sm.umsatz[0], sm.umsatz[1]};
    }

    static void bilanzSediment() {
        A.teil("Prüfstein 13: sediment mit Salzwiese, kleines Becken mit Tide (synthetisch, hydro auf dem Prozessor)");
        double[] ohne = sedimentLauf(false), mit = sedimentLauf(true);
        A.info("Umsatz Sand / Schlick über zwei Tiden (kg)", f("ohne Salzwiese %.3g / %.3g, mit %.3g / %.3g", ohne[2], ohne[3], mit[2], mit[3]));
        A.pruefe(mit[0] <= 1e-3 && mit[1] <= 1e-3 && ohne[0] <= 1e-3 && ohne[1] <= 1e-3, "Prüfstein 13: Massenbilanz sediment, ohne und mit Salzwiese (relativ zum Umsatz; Sand, Schlick)",
                f("ohne %.1e / %.1e, mit %.1e / %.1e", ohne[0], ohne[1], mit[0], mit[1]), "≤ 10⁻³");
        A.pruefe(mit[2] + mit[3] > 0 && mit[2] + mit[3] < ohne[2] + ohne[3], "Prüfstein 13: Umsatz mit Salzwiese kleiner (Schub am Korn geteilt)",
                f("%.3g gegen %.3g kg", mit[2] + mit[3], ohne[2] + ohne[3]), "kleiner, > 0");
    }

    static void bilanzFlugsand(File gb, Path archiv) throws Exception {
        A.teil("Prüfstein 13: aeolian mit Salzwiese im Nachlauf zu " + archiv);
        if (!Files.isDirectory(archiv.resolve("G3"))) { A.pruefe(false, "Archiv für den Flugsand", "fehlt: " + archiv, "vorhanden"); return; }
        com.dan.lauf.Nachlauf.Einstellung e = new com.dan.lauf.Nachlauf.Einstellung();
        e.gebiet = gb;
        e.archiv = archiv;
        e.aeolian = true;
        e.vegetation = true;
        e.ablegen = false;   // das Archiv aus P10c bleibt unberührt
        e.pruefen = false;
        long n0 = System.nanoTime();
        try (com.dan.lauf.Nachlauf n = com.dan.lauf.Nachlauf.bauen(e)) {
            com.dan.physics.aeolian.Aeolian ae = n.flugsand.aeolian;
            double dt = n.speiser.kopplung;
            for (double t = n.beginn + dt; t <= n.letzte + 1e-6; t += dt) n.bis(t);
            long bedeckt = 0;
            if (ae.deckung != null) for (double x : ae.deckung) if (x > 0) bedeckt++;
            double bezug = Math.max(ae.abtrag, 1e-300);
            A.info("Nachlauf", f("%.1f min; Abtrag %.4g kg, Auftrag %.4g kg, Ausflug %.4g kg; %d Zellen mit Bedeckung", (System.nanoTime() - n0) / 6e10, ae.abtrag, ae.auftrag, ae.ausflug, bedeckt));
            A.pruefe(bedeckt > 0 && Math.abs(ae.rest()) / bezug <= 1e-3, "Prüfstein 13: Massenbilanz aeolian mit Salzwiese (Auftrag − Abtrag + Ausflug, bezogen auf den Abtrag)",
                    f("%.1e; %d Zellen mit Bedeckung", Math.abs(ae.rest()) / bezug, bedeckt), "≤ 10⁻³");
        }
    }

    // ------------------------------------------------------------------ Statistik (Lauf B1)

    /** Der Leser der Statistik am Bus: sammelt Wasserstand und Tiefe auf einem Gitter. */
    static final class Sammler implements Modul {
        final Gitter g;
        final double kopplung;
        final Ueberflutung u;
        private Feld eta, h;
        private float[] fe, fh;

        Sammler(Gitter g, double kopplung, double ab) {
            this.g = g;
            this.kopplung = kopplung;
            u = new Ueberflutung(g, ab);
        }

        @Override public String name() { return "vegetation.ueberflutung"; }

        @Override public void anmelden(Anmeldung a) {
            a.kopplung(kopplung);
            a.liestImIntervall("water.eta", "m NHN", Ort.ZELLE, g).liestImIntervall("water.h", "m", Ort.ZELLE, g);
        }

        @Override public void beginn(double t0, Bus bus) {
            eta = bus.lesefeld(this, "water.eta", g);
            h = bus.lesefeld(this, "water.h", g);
            fe = new float[g.zellen()];
            fh = new float[g.zellen()];
        }

        @Override public void schritt(double t0, double t1, Bus bus) {
            if (t1 < u.ab) return;
            bus.lies(this, "water.eta", t1, eta);
            bus.lies(this, "water.h", t1, h);
            for (int c = 0; c < fe.length; c++) { fe[c] = (float) eta.hol(c); fh[c] = (float) h.hol(c); }
            u.stand(t1, t1 - t0, fe, fh);
        }
    }

    static void statistik(File gb, File ziel) throws Exception {
        A.teil("Lauf B1: Statistik der Überflutung (G0–G2, Spring-Nipp-Folge)");
        Lauf.Einstellung e = new Lauf.Einstellung();
        e.gebiet = gb;
        e.start = start;
        e.karte = true;
        e.name = "P13b Statistik der Überflutung " + start;
        Gitter g2 = Gebiet.lies(gb).gitter("G2");
        double ende = einschwingen + tiden * Ueberflutung.TIDE + 300;
        Sammler s = new Sammler(g2, e.kopplung, einschwingen);
        e.weitere = new ArrayList<>(List.of(s));
        long n0 = System.nanoTime();
        try (Lauf l = Lauf.bauen(e)) {
            A.info("Lauf", l + f("; bis %.1f Tage (Einschwingen %.0f h, %d Tiden)", ende / 86400, einschwingen / 3600, tiden));
            for (double t = 0; t < ende; ) {
                t = Math.min(ende, t + 86400);
                l.bis(t);
                System.out.printf(Locale.ROOT, "  %s gerechnet (%.1f min)%n", l.zeitpunkt(t), (System.nanoTime() - n0) / 6e10);
            }
        }
        Ueberflutung u = s.u;
        u.abschliessen(ende);
        u.schreibe(ziel, "Lauf B1 (P13b): " + e.stufenText() + ", Start " + start + ", Einschwingen " + einschwingen / 3600 + " h, Wind ERA5");
        A.info("Zeit", f("%.1f Tage Modellzeit in %.1f min; abgelegt nach %s", ende / 86400, (System.nanoTime() - n0) / 6e10, ziel));
        A.pruefe(u.tiden >= tiden - 1, "Lauf B1: volle Tiden in der Statistik", u.tiden + " von " + tiden, "≥ " + (tiden - 1));
        int nass = 0, gef = 0;
        for (int c = 0; c < g2.zellen(); c++) { if (u.gefuellt[c] == 0) nass++; else gef++; }
        double mh = u.wert(u.mthw, hooge[0], hooge[1]), sp = u.wert(u.mspthw, hooge[0], hooge[1]), np = u.wert(u.mnpthw, hooge[0], hooge[1]);
        A.pruefe(mh > 0.8 && mh < 2.2 && sp > np, "Lauf B1: MThw am Pegel Hooge (Plausibilität)", f("MThw %.2f, MSpThw %.2f, MNpThw %.2f m NHN", mh, sp, np), "0,8–2,2 m NHN [G]; Spring über Nipp");
        A.info("Zellen von G2", f("%d mit eigenem MThw (in ≥ %.0f %% der Tiden nass), %d aus den Nachbarn gefüllt", nass, 100 * u.mindestAnteil, gef));
    }

    // ------------------------------------------------------------------ Nachlauf (Prüfstein 6)

    /** Reicht der Nachlauf in {@code nl} bis zum letzten Stand des Archivs {@code ar}? */
    static boolean vollstaendig(Path nl, Path ar) {
        if (!Files.isDirectory(nl)) return false;
        try (Archiv a = Archiv.oeffnen(nl); Archiv h = Archiv.oeffnen(ar)) {
            return a.staende() > 0 && a.zeit(a.staende() - 1) >= h.zeit(h.staende() - 1) - 1;
        } catch (Exception x) {
            return false;
        }
    }

    static void nachlauf(File gb, Path ar, Path ziel) throws Exception {
        A.teil("Nachlauf hydro auf G3 aus dem Archiv " + ar + ", ohne und mit Salzwiese (Prüfstein 6)");
        Zonen zo = Zonen.lies(gb);
        A.pruefe(zo != null, "Zonen im Gebiet", zo == null ? "fehlen" : zo.kacheln.size() + " Kacheln", "vorhanden");
        if (zo == null) return;
        Path[] z = {ziel.resolve("ohne"), ziel.resolve("mit")};
        for (int k = 0; k < 2; k++) {
            if (vollstaendig(z[k].resolve("hydro_G3"), ar.resolve("G3"))) {   // liegt aus einem früheren Lauf vor
                A.info(k == 0 ? "ohne Salzwiese" : "mit Salzwiese", "liegt vollständig in " + z[k] + ", nicht neu gerechnet (zum Neurechnen den Ordner wegräumen)");
                continue;
            }
            Stroemung.Einstellung e = new Stroemung.Einstellung();
            e.gebiet = gb;
            e.archiv = ar;
            e.ziel = z[k];
            e.vegetation = k == 1;
            Files.createDirectories(z[k]);
            long n0 = System.nanoTime();
            try (Stroemung s = Stroemung.bauen(e)) {
                s.rechne();
                A.info(k == 0 ? "ohne Salzwiese" : "mit Salzwiese", f("%d Stände in %.1f min; Wasserstand gegen das Archiv: RMS %.3f m, größte %.3f m", s.staende,
                        (System.nanoTime() - n0) / 6e10, s.rms(), s.groesste));
            }
        }
        // Vergleich: Strom über der bewachsenen Hallig, solange sie unter Wasser steht
        Katalog kat = Katalog.grundstufe();
        try (Archiv a0 = Archiv.oeffnen(z[0].resolve("hydro_G3")); Archiv a1 = Archiv.oeffnen(z[1].resolve("hydro_G3"));
             Archiv ah = Archiv.oeffnen(ar.resolve("G3"))) {
            Gitter g = a0.lauf.gitter.get("G3");
            VegetationModul vm = new VegetationModul(List.of(g), zo, Gestalt.lies(new File(gb, "gestalt")), new Salzwiese(), 300, a0.lauf.start);
            float[] bew = vm.bewachsen(0);
            vm.rechne(vm.tag(a0.zeit(0)));
            float[] z0 = vm.z0(0);
            int veg = 0;
            double nS = 0;
            for (int c = 0; c < bew.length; c++) if (z0[c] > 0) { veg++; nS += Salzwiese.manning(z0[c]); }
            A.info("Salzwiese auf G3", f("%d Zellen mit Rauheit der Pflanzen (≥ %.0f %% bewachsen); Manning-n im Mittel %.4f (Tag %d)", veg, 100 * vm.mindestAnteil, nS / Math.max(1, veg), vm.tag(a0.zeit(0))));
            Feld u0 = a0.feld("water.u", kat), v0 = a0.feld("water.v", kat), u1 = a1.feld("water.u", kat), v1 = a1.feld("water.v", kat);
            Feld e0 = a0.feld("water.eta", kat), e1 = a1.feld("water.eta", kat), h = ah.feld("water.h", kat);
            int hc = g.index(Math.max(0, Math.min(g.nx - 1, g.i(hooge[0]))), Math.max(0, Math.min(g.ny - 1, g.j(hooge[1]))));
            double s0 = 0, s1 = 0, max0 = Double.NEGATIVE_INFINITY, max1 = Double.NEGATIVE_INFINITY;
            long n = 0;
            int staende = 0;
            for (int k = 0; k < a0.staende(); k++) {
                double t = a0.zeit(k);
                a0.lies("water.u", k, u0); a0.lies("water.v", k, v0); a1.lies("water.u", k, u1); a1.lies("water.v", k, v1);
                a0.lies("water.eta", k, e0); a1.lies("water.eta", k, e1);
                ah.lies("water.h", t, h);
                max0 = Math.max(max0, e0.hol(hc));
                max1 = Math.max(max1, e1.hol(hc));
                if (e0.hol(hc) < 2.5) continue;                                    // die Hallig steht unter Wasser (Pegel Hooge über 2,5 m NHN [G])
                staende++;
                for (int c = 0; c < bew.length; c++) {
                    if (z0[c] <= 0 || h.hol(c) < 0.2) continue;
                    s0 += Math.hypot(u0.hol(c), v0.hol(c));
                    s1 += Math.hypot(u1.hol(c), v1.hol(c));
                    n++;
                }
            }
            double r = s1 / s0;
            A.pruefe(n > 0 && r >= 0.7 && r <= 1.0, "Prüfstein 6: Strom über der bewachsenen Hallig mit gegen ohne Salzwiese",
                    f("%.3f (Mittel %.3f gegen %.3f m/s über %d Zellstände in %d Ständen mit Pegel Hooge über 2,5 m)", r, s1 / Math.max(1, n), s0 / Math.max(1, n), n, staende), "0,7–1,0 [G]");
            A.pruefe(Math.abs(max1 - max0) <= 0.02, "Prüfstein 6: Scheitel am Pegel Hooge mit gegen ohne",
                    f("%.3f gegen %.3f m NHN (%+.3f m)", max1, max0, max1 - max0), "± 0,02 m");
        }
    }

    // ------------------------------------------------------------------ Bild (Prüfsteine 7, 14, 10)

    record Probe(String text, float[] bild) { }

    static void bild(File datei) throws Exception {
        A.teil("Bild der Salzwiese");
        Szenen sz = Szenen.lies(datei);
        Szenario s = null;
        for (Szenario x : sz.szenarien()) if (x.schluessel.equals(szenario)) s = x;
        if (s == null) { A.pruefe(false, "Szenario " + szenario, "fehlt", "vorhanden"); return; }
        File gb = sz.gebiet();
        Gestalt ohne = Gestalt.lies(new File(gb, "gestalt"));
        List<Probe> proben = new ArrayList<>();
        try (Buehne b = Buehne.ohneFenster(gb, BREITE, HOEHE)) {
            A.pruefe(b.hatSalzwiese(), "Die Bühne hat Zonen der Salzwiese", b.hatSalzwiese() ? "ja" : "nein", "ja");
            if (!b.hatSalzwiese()) return;
            b.oeffne(s);
            double t = b.uhr().t(Instant.parse(zeit));   // Mittag des Szenarios (Lauf B3: 4 h nach dem Start des Archivs war Nacht)
            String[] an = ansichten.split(",");
            com.dan.render.bewuchs.Bewuchsbild bw = b.bewuchsbild();
            long gleich = 0, alle = 0;
            double msAn = 0, msAus = 0;
            for (String name : an) {
                Ansicht a = sz.ansicht(name.strip());
                if (a == null) { A.info("Ansicht " + name, "fehlt"); continue; }
                Blick bl = new Blick();
                a.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                bl.groesse(BREITE, HOEHE);
                // Prüfstein 7: aus gegen die Gestalt ohne Zonen, bei fester Belichtung (wie HausCheck)
                var bel = b.belichtung();
                double evMin = bel.evMin, evMax = bel.evMax;
                b.salzwiese(false);
                bel.sofort();                                                         // je Ansicht neu messen
                b.standbild(bl, t);
                double ev = bel.ev();
                bel.evMin = bel.evMax = ev;
                bel.setze(ev);
                float[] aus = b.standbild(bl, t).clone();
                com.dan.core.szene.Gestalt mitZonen = b.gelaendebild().gestalt();
                b.gelaendebild().gestalt(ohne);
                b.gelaendebild().hatZonen = false;
                if (bw != null) bw.halme(b.gelaendebild().gelaende, ohne, b.gelaendebild().material.boden);
                bel.setze(ev);
                b.standbild(bl, t);
                bel.setze(ev);
                float[] alt = b.standbild(bl, t);
                bel.evMin = evMin;
                bel.evMax = evMax;
                for (int q = 0; q < aus.length; q++) { alle++; if (Float.floatToIntBits(aus[q]) == Float.floatToIntBits(alt[q])) gleich++; }
                msAus = Math.max(msAus, b.bildzeiten(bl, t, 20).karte()[10]);
                b.gelaendebild().gestalt(mitZonen);
                b.gelaendebild().hatZonen = true;
                if (bw != null) bw.halme(b.gelaendebild().gelaende, mitZonen, b.gelaendebild().material.boden);
                b.salzwiese(true);
                msAn = Math.max(msAn, b.bildzeiten(bl, t, 20).karte()[10]);
                // Sommer, Herbst (Queller rot) und Winter (Tag fest); Belichtung je Bild gemessen
                int[] tage = {200, 270, 20};
                String[] namen = {"Juli", "September", "Januar"};
                for (int q = 0; q < tage.length; q++) {
                    if (bw != null) bw.tagFest = tage[q];
                    bel.sofort();
                    b.standbild(bl, t);
                    b.standbild(bl, t);
                    proben.add(new Probe(a.name + ", " + namen[q], b.standbild(bl, t).clone()));
                }
                if (bw != null) {
                    StringBuilder je = new StringBuilder();
                    for (int k = 0; k < bw.bueschelJeArt.length; k++) if (bw.bueschelJeArt[k] > 0) je.append(je.length() > 0 ? ", " : "").append(k).append(": ").append(bw.bueschelJeArt[k]);
                    A.info(a.name + ": Büschel je Art (mit Salzwiese, Januar)", je.length() == 0 ? "keine" : je.toString());
                    bw.tagFest = 0;
                }
            }
            A.pruefe(alle > 0 && gleich == alle, "Prüfstein 7: Bild mit Salzwiese aus gegen die Gestalt ohne Zonen", f("%d von %d Werten gleich", gleich, alle), "bitgleich");
            A.pruefe(msAn - msAus <= 0.5, "Prüfstein 14: Kosten der Salzwiese im Bild (langsamste Ansicht, Median aus 20)", f("%.2f gegen %.2f ms (%+.2f ms)", msAn, msAus, msAn - msAus), "≤ +0,5 ms [G]");
        }
        if (!proben.isEmpty()) statusBild(proben, ansichten.split(",").length);
        try {
            karte(gb, sz);
        } catch (Exception x) {
            A.info("Karte der Zonen", "nicht gezeichnet: " + x);
        }
    }

    /**
     * Karte der Arten mit Zonen (1 km um das Auge der Ansicht „vorland“, 1 Bildpunkt je Meter) für die Sichtprüfung: Pionier rot,
     * untere Salzwiese graugrün, obere grün, Salzwiese ohne Zone türkis, Schlick unter der Pionierzone braun, Fenne oliv, Fenne unten gelbgrün, Wasser blau,
     * Deich und Warft grau; das Auge der Ansicht als weißes Kreuz, der Ring der Halme (30 m) als Kreis.
     */
    static void karte(File gb, Szenen sz) throws Exception {
        Ansicht a = sz.ansicht("vorland");
        Gestalt g = Gestalt.lies(new File(gb, "gestalt"));
        Zonen z = Zonen.lies(gb);
        if (a == null || g == null || z == null) { A.info("Karte der Zonen", "Ansicht vorland, Gestalt oder Zonen fehlen"); return; }
        Gestalt mz = z.aufGestalt(g);
        double[] ort = {a.ost, a.nord};
        int n = 1000;
        BufferedImage bi = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        int[] zahl = new int[32];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double ost = ort[0] - n / 2.0 + i + 0.5, nord = ort[1] + n / 2.0 - j - 0.5;
                int[] w = mz.bei(ost, nord);
                int art = w == null ? 0 : w[0];
                if (art >= 0 && art < 32) zahl[art]++;
                int c = switch (art) {
                    case Zonen.ART_PIONIER -> 0xC0302A; case Zonen.ART_SALZ_UNTEN -> 0x8FA07A; case Zonen.ART_SALZ_OBEN -> 0x4E8A3A;
                    case Gestalt.SALZWIESE -> 0x3AA0A0; case Zonen.ART_SCHLICK -> 0x5A4A38; case Gestalt.FENNE -> 0x8C8A40; case Zonen.ART_FENNE_UNTEN -> 0xB8C850;
                    case Gestalt.GRABEN, Gestalt.PRIEL, Gestalt.FETHING -> 0x3060C0; case Gestalt.DEICH, Gestalt.WARFT -> 0x909090; default -> 0x202830;
                };
                bi.setRGB(i, j, c);
            }
        Graphics2D gr = bi.createGraphics();
        gr.setColor(Color.WHITE);
        gr.drawLine(n / 2 - 8, n / 2, n / 2 + 8, n / 2);
        gr.drawLine(n / 2, n / 2 - 8, n / 2, n / 2 + 8);
        gr.drawOval(n / 2 - 30, n / 2 - 30, 60, 60);
        double ri = Math.toRadians(a.richtung);
        gr.drawLine(n / 2, n / 2, n / 2 + (int) Math.round(60 * Math.sin(ri)), n / 2 - (int) Math.round(60 * Math.cos(ri)));   // Blickrichtung
        gr.dispose();
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        ImageIO.write(bi, "png", d.resolve("P13b_Zonen_Vorland.png").toFile());
        StringBuilder je = new StringBuilder();
        for (int k = 0; k < 32; k++) if (zahl[k] > 0) je.append(je.length() > 0 ? ", " : "").append(k).append(": ").append(f("%.2f ha", zahl[k] / 1e4));
        A.info(f("Karte der Zonen (1 km um O %.0f, N %.0f)", a.ost, a.nord), "docs/status/P13b_Zonen_Vorland.png; Arten: " + je);
    }

    static void statusBild(List<Probe> proben, int spalten) throws Exception {
        int w = BREITE / 2, h = HOEHE / 2, n = 3, zeilen = (proben.size() + n - 1) / n;
        BufferedImage alle = new BufferedImage(n * w, zeilen * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = alle.createGraphics();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int i = 0; i < proben.size(); i++) {
            int x = (i % n) * w, y = (i / n) * h;
            g.drawImage(HausCheck.bild(proben.get(i).bild), x, y, w, h, null);
            String t = proben.get(i).text;
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(x + 4, y + 4, g.getFontMetrics().stringWidth(t) + 8, 18);
            g.setColor(Color.WHITE);
            g.drawString(t, x + 8, y + 17);
        }
        g.dispose();
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        ImageIO.write(alle, "png", d.resolve("P13b_Salzwiese.png").toFile());
        for (int i = 0; i < proben.size(); i++) ImageIO.write(HausCheck.bild(proben.get(i).bild), "png", d.resolve(f("P13b_Salzwiese_%d.png", i + 1)).toFile());
        A.info("Bilder zur Sichtprüfung (Prüfstein 10)", "docs/status/P13b_Salzwiese.png, je Ansicht Juli, September und Januar, " + zeit);
    }
}
