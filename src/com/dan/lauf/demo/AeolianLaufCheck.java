package com.dan.lauf.demo;

import com.dan.core.archiv.Archiv;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.gitter.Gitter;
import com.dan.core.pruef.Attest;
import com.dan.lauf.AtmosArchiv;
import com.dan.lauf.Nachlauf;
import com.dan.physics.aeolian.Aeolian;
import com.dan.physics.atmos.Atmos;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * aeolian im Nachlauf aus dem Archiv (P10c Teil 1, Werkbuch HalligHooge Teil A, 12.11, Datei 41): die Prüfsteine 11–15.
 * <ul>
 * <li><b>Lauf 1, ohne Regen</b>, abgelegt in einen Prüfordner: atmos muss Byte für Byte ergeben, was im Archiv liegt
 * (Prüfstein 11: {@code surf.wet}, {@code surf.T}, {@code air.n}). Seine Zahlen des Flugsands je Höhenklasse sind der
 * Vergleich mit der unabhängigen Rechnung des Befunds (Prüfstein 14; der Vergleich selbst steht außerhalb).</li>
 * <li><b>Lauf 2, mit Regen</b> (F97 A), abgelegt ins Archiv: atmos neu, dazu {@code aeolian_<Gitter>}.</li>
 * <li>In beiden: 12 Massenbilanz; 13 kein Abtrag auf Zellen, aus denen der Wind nie aufnehmen durfte, kein Fluss unter
 * der trockenen Schwelle; 15 Größe der Bodenänderung (Auskunft).</li>
 * </ul>
 * {@code --gebiet <ordner> --archiv <ordner> [--pruef <ordner>] [--vergleich <ordner>] [--name P10c_AeolianLauf]
 * [--ohne-ablage]}. Der Prüfordner (Vorgabe {@code <archiv>_ohneRegen}) bleibt liegen: Er hält atmos ohne Regen, wie es
 * vor P10c im Archiv lag. {@code --vergleich}: wogegen Prüfstein 11 misst (Vorgabe: das Archiv, solange es atmos ohne
 * Regen hält).
 */
public final class AeolianLaufCheck {
    private static Attest A;
    static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneOffset.UTC);
    /** Endungen der Dateien im Archiv (float16, float32). */
    static final String HALB = ".f16", FLOAT = ".f32";
    static final double[] KLASSEN = {0.5, 1.0, 1.25, 1.5, 1.75, 2.0, 99};

    public static void main(String[] args) {
        List<String> a = List.of(args);
        File gebiet = new File(AtmosLaufCheck.arg(a, "--gebiet", "D:\\Daten\\HalligHooge\\gebiet"));
        Path archiv = Path.of(AtmosLaufCheck.arg(a, "--archiv", "D:\\Daten\\HalligHooge\\_p9c\\archiv_m1b"));
        Path pruef = Path.of(AtmosLaufCheck.arg(a, "--pruef", archiv.toString() + "_ohneRegen")), vergleich = Path.of(AtmosLaufCheck.arg(a, "--vergleich", archiv.toString()));
        String name = AtmosLaufCheck.arg(a, "--name", "P10c_AeolianLauf");
        boolean ablegen = !a.contains("--ohne-ablage");
        A = new Attest("com.dan.lauf (Nachlauf atmos und aeolian)", Nachlauf.VERSION + ", Atmos " + Atmos.VERSION + ", Aeolian " + Aeolian.VERSION);
        long n0 = System.nanoTime();
        try {
            A.pruefe(Katalog.grundstufe().namen().size() == 48, "Katalog", Katalog.grundstufe().namen().size() + " Felder", "48");
            // wogegen Prüfstein 11 misst, muss atmos ohne Regen sein
            String alt = kopfzeile(vergleich.resolve(AtmosArchiv.ATMOS + "G3").resolve("lauf.txt"), "antrieb.atmos");
            boolean altOhneRegen = alt != null && !alt.contains("mit Regen");
            Files.createDirectories(pruef);
            double[] z = hoehe(gebiet, archiv);
            Ergebnis ohne = lauf(gebiet, archiv, pruef, false, true, z, "Lauf 1 (ohne Regen, in den Prüfordner)");
            A.teil("11 atmos im neuen Nachlauf gegen das Archiv");
            if (altOhneRegen) {
                String[][] paare = {{AtmosArchiv.ATMOS + "G3", "surf.wet" + HALB}, {AtmosArchiv.ATMOS + "G3", "surf.T" + FLOAT}, {AtmosArchiv.ATMOS + "G3", "zeiten.f64"}, {AtmosArchiv.LUFT + "G3s", "air.n" + FLOAT}};
                StringBuilder b = new StringBuilder();
                boolean gleich = true;
                for (String[] p : paare) {
                    long d = unterschied(pruef.resolve(p[0]).resolve(p[1]), vergleich.resolve(p[0]).resolve(p[1]));
                    gleich &= d == -1;
                    b.append(p[1]).append(d == -1 ? " gleich (" + Files.size(pruef.resolve(p[0]).resolve(p[1])) / 1_000_000 + " MB); " : d == -2 ? " andere Länge; " : " ab Byte " + d + " verschieden; ");
                }
                A.pruefe(gleich, "Prüfstein 11: atmos ohne Regen gegen " + vergleich.getFileName() + " (" + alt + ")", b.toString(), "bitgleich");
            } else {
                A.info("Prüfstein 11", "nicht zu prüfen: " + vergleich + " hält " + (alt == null ? "kein atmos" : "schon atmos mit Regen (" + alt + ")") + "; mit --vergleich einen Ordner mit atmos ohne Regen nennen");
            }
            Ergebnis mit = lauf(gebiet, archiv, ablegen ? null : Files.createDirectories(pruef.resolveSibling(pruef.getFileName() + "_2")), true, ablegen, z, "Lauf 2 (mit Regen" + (ablegen ? ", ins Archiv)" : ", ohne Ablage)"));
            A.teil("Regen (F97 A): Lauf 2 gegen Lauf 1");
            Path neu = (ablegen ? archiv : pruef.resolveSibling(pruef.getFileName() + "_2")).resolve(AtmosArchiv.ATMOS + "G3").resolve("surf.wet" + HALB);
            long d = unterschied(pruef.resolve(AtmosArchiv.ATMOS + "G3").resolve("surf.wet" + HALB), neu);
            long jeStand = 2L * mit.zellen;
            String ab = d < 0 ? (d == -1 ? "nirgends" : "andere Länge") : TAG.format(mit.start.plusSeconds((long) (mit.erste + d / jeStand * mit.kopplung)));
            double tVerschieden = d < 0 ? Double.MAX_VALUE : mit.erste + d / jeStand * mit.kopplung;
            A.pruefe(mit.regen > 0 ? tVerschieden >= mit.ersterRegen : d == -1, "surf.wet mit Regen weicht erst von der Rechnung ohne Regen ab, wenn Regen gefallen ist",
                    f("verschieden ab %s; erster Regen %s (der Vorlauf beginnt %s); Regen im Mittel der Säulen %.2f mm im Vorlauf, %.2f mm danach", ab,
                            mit.regen > 0 ? TAG.format(mit.start.plusSeconds((long) mit.ersterRegen)) : "keiner", TAG.format(mit.start.plusSeconds((long) mit.beginn)), mit.regenVorlauf, mit.regen - mit.regenVorlauf),
                    "nicht vor dem ersten Regen");
            A.info("Flugsand mit Regen gegen ohne", f("abgetragen %.0f t gegen %.0f t (%+.1f %%); Zellen, aus denen der Wind aufnahm, %d gegen %d; größter Auftrag %.1f mm gegen %.1f mm", mit.abtrag / 1e3,
                    ohne.abtrag / 1e3, ohne.abtrag > 0 ? 100 * (mit.abtrag / ohne.abtrag - 1) : 0, mit.aufZellen, ohne.aufZellen, mit.groessterAuftrag * 1e3, ohne.groessterAuftrag * 1e3));
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.teil("Ablauf");
        A.info("Prüfstein 14", "der Vergleich mit der unabhängigen Rechnung (Python) steht außerhalb; dafür die Zeilen „Klasse“ von Lauf 1");
        A.info("Dauer", f("%.0f s", (System.nanoTime() - n0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", name + ".md"), "P10c – AeolianLaufCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    record Ergebnis(double abtrag, long aufZellen, double groessterAuftrag, double regen, double regenVorlauf, double ersterRegen, double erste, double beginn, double kopplung, int zellen,
                    java.time.Instant start) { }

    /** Der Wert zu einem Schlüssel in {@code lauf.txt}; null, wenn Datei oder Schlüssel fehlen. */
    static String kopfzeile(Path datei, String schluessel) throws IOException {
        if (!Files.isRegularFile(datei)) return null;
        for (String z : Files.readAllLines(datei)) if (z.startsWith(schluessel + " = ")) return z.substring(schluessel.length() + 3);
        return null;
    }

    /** Erste Stelle, an der sich zwei Dateien unterscheiden; −1: gleich; −2: verschieden lang. */
    static long unterschied(Path a, Path b) throws IOException {
        if (Files.size(a) != Files.size(b)) return -2;
        byte[] x = new byte[1 << 20], y = new byte[1 << 20];
        long ort = 0;
        try (InputStream p = new BufferedInputStream(Files.newInputStream(a), 1 << 20); InputStream q = new BufferedInputStream(Files.newInputStream(b), 1 << 20)) {
            while (true) {
                int n = p.readNBytes(x, 0, x.length), m = q.readNBytes(y, 0, y.length);
                if (n != m) return -2;
                if (n == 0) return -1;
                int k = Arrays.mismatch(x, 0, n, y, 0, n);
                if (k >= 0) return ort + k;
                ort += n;
            }
        }
    }

    /** Höhe der Zellen (m NHN): Gelände des Gebiets im Fenster des Archivs, dazu {@code bed.dz} aus dessen letztem Stand des Bodens, wenn es einen hält. */
    static double[] hoehe(File gebiet, Path archiv) throws IOException {
        Gitter g;
        try (Archiv w = Archiv.oeffnen(archiv.resolve("G3"))) {
            g = w.feld("water.h", Katalog.grundstufe()).gitter;
        }
        float[] grund = Nachlauf.grundDesGebiets(gebiet, g);
        double[] z = new double[grund.length];
        for (int c = 0; c < z.length; c++) z[c] = grund[c];
        Path boden = archiv.resolve("boden_G3");
        if (Files.isRegularFile(boden.resolve("lauf.txt")))
            try (Archiv b = Archiv.oeffnen(boden)) {
                Feld dz = b.feld("bed.dz", Katalog.grundstufe());
                b.lies("bed.dz", b.staende() - 1, dz);
                for (int c = 0; c < z.length; c++) z[c] += dz.hol(c);
            }
        return z;
    }

    static Ergebnis lauf(File gebiet, Path archiv, Path ziel, boolean regen, boolean ablegen, double[] z, String titel) throws IOException {
        A.teil(titel);
        Nachlauf.Einstellung e = new Nachlauf.Einstellung();
        e.gebiet = gebiet;
        e.archiv = archiv;
        e.ziel = ziel;
        e.ablegen = ablegen || ziel != null;
        e.regen = regen;
        e.aeolian = true;
        long n0 = System.nanoTime();
        try (Nachlauf n = Nachlauf.bauen(e)) {
            Aeolian ae = n.flugsand.aeolian;
            Atmos at = n.atmos.atmos;
            Gitter g = n.speiser.gitter;
            double dt = n.speiser.kopplung;
            A.info("Lauf", f("%s: %s bis %s, %d Stände alle %.0f s auf %s; Vorlauf ab %s; atmos %s; Hinweise der Verdrahtung: %s", archiv.getFileName(), n.antrieb.zeit(n.erste), n.antrieb.zeit(n.letzte),
                    n.speiser.archiv.staende(), dt, g, n.antrieb.zeit(n.beginn), n.atmos.mitRegen ? "mit Regen" : "ohne Regen", n.hinweise.isEmpty() ? "keine" : n.hinweise.toString()));
            // Vorlauf in Stunden, um den ersten Regen zu fassen
            double ersterRegen = Double.MAX_VALUE;
            for (double t = n.beginn + dt; t <= n.erste + 1e-6; t += dt) {
                n.bis(t);
                if (ersterRegen == Double.MAX_VALUE && max(at.regen) > 0) ersterRegen = t;
            }
            double regenVorlauf = n.atmos.regenSumme;
            boolean[] durfte = new boolean[ae.nc];
            long unterSchwelle = 0, schritte = 0, mitFluss = 0;
            double groessterFluss = 0, windMin = Double.MAX_VALUE, windMax = 0;
            for (double t = n.erste + dt; t <= n.letzte + 1e-6; t += dt) {
                n.bis(t);
                schritte++;
                if (ersterRegen == Double.MAX_VALUE && max(at.regen) > 0) ersterRegen = t;
                boolean fluss = false;
                for (int c = 0; c < ae.nc; c++) {
                    double wind = Math.hypot(ae.u[c], ae.v[c]), q = Math.hypot(ae.qx[c], ae.qy[c]);
                    if (ae.h[c] < ae.hNass && ae.fest[c] < ae.festAb && ae.schlick[c] <= ae.schlickMax && ae.prozent(ae.wet[c], ae.poros[c]) <= ae.wMax) durfte[c] = true;
                    if (q > 0) {
                        fluss = true;
                        if (!(ae.ustern(wind) > ae.schwelle(ae.d50[c]))) unterSchwelle++;
                        groessterFluss = Math.max(groessterFluss, q);
                    }
                }
                if (fluss) mitFluss++;
                double w = Math.hypot(ae.u[ae.nc / 2], ae.v[ae.nc / 2]);
                windMin = Math.min(windMin, w);
                windMax = Math.max(windMax, w);
            }
            double sekunden = (System.nanoTime() - n0) * 1e-9;
            // 12 Massenbilanz
            double masse = 0;
            for (int c = 0; c < ae.nc; c++) masse += ae.dz[c] * ae.rhoS * (1 - ae.poros[c]) * ae.dx * ae.dy;
            double bezug = Math.max(ae.abtrag, 1e-300);
            A.hoechstens("Prüfstein 12: Massenbilanz über " + f("%.0f h", schritte * dt / 3600) + " (Auftrag − Abtrag + Ausflug, bezogen auf den Abtrag)", Math.abs(ae.rest()) / bezug, 1e-9, "");
            A.hoechstens("Prüfstein 12: dasselbe aus der Höhe (Σ Δz ρs (1 − θ) A + Ausflug)", Math.abs(masse + ae.ausflug) / bezug, 1e-9, "");
            // 13
            long verboten = 0;
            double tiefster = 0;
            for (int c = 0; c < ae.nc; c++)
                if (!durfte[c] && ae.dz[c] < 0) {
                    verboten++;
                    tiefster = Math.min(tiefster, ae.dz[c]);
                }
            long nie = 0;
            for (boolean b : durfte) if (!b) nie++;
            A.pruefe(verboten == 0, "Prüfstein 13: Abtrag auf Zellen, aus denen der Wind nie aufnehmen durfte (Wasser, fest, Schlick, über " + f("%.0f", ae.wMax) + " % Wasser)",
                    f("%d von %d solchen Zellen, tiefste Änderung %.1e m", verboten, nie, tiefster), "keine");
            A.pruefe(unterSchwelle == 0, "Prüfstein 13: Fluss in Zellen, deren u* nicht über der trockenen Schwelle liegt", unterSchwelle + " Zellschritte", "keine");
            // 15 Größe
            double groesst = 0, kleinst = 0;
            int cg = 0, ck = 0;
            long mm = 0, cm = 0, auf = 0;
            for (int c = 0; c < ae.nc; c++) {
                if (ae.dz[c] > groesst) { groesst = ae.dz[c]; cg = c; }
                if (ae.dz[c] < kleinst) { kleinst = ae.dz[c]; ck = c; }
                if (Math.abs(ae.dz[c]) >= 1e-3) mm++;
                if (Math.abs(ae.dz[c]) >= 1e-2) cm++;
                if (ae.aufnahme[c] > 0) auf++;
            }
            A.info("Prüfstein 15: Größe der Bodenänderung", f("größter Auftrag %.1f mm bei O %.0f, N %.0f (%s, %.2f m NHN); größter Abtrag %.1f mm bei O %.0f, N %.0f (%.2f m NHN); "
                            + "Zellen mit |Δz| ≥ 1 mm: %d, ≥ 1 cm: %d", groesst * 1e3, g.x(cg % ae.nx), g.y(cg / ae.nx), ae.h[cg] >= ae.hNass ? "zuletzt Wasser" : ae.fest[cg] >= ae.festAb ? "fest" : "Sand", z[cg],
                    -kleinst * 1e3, g.x(ck % ae.nx), g.y(ck / ae.nx), z[ck], mm, cm));
            A.info("Massen", f("abgetragen %.0f t, aufgetragen %.0f t, davon ins Wasser %.0f t; über den Rand des Fensters %.0f t; größter Fluss %.1f g/(m s); Schritte mit Fluss %d von %d, mit gemittelter "
                    + "Richtung %d; Wind in der Mitte %.1f–%.1f m/s", ae.abtrag / 1e3, ae.auftrag / 1e3, ae.insWasser / 1e3, ae.ausflug / 1e3, groessterFluss * 1e3, mitFluss, schritte, ae.gemittelt, windMin, windMax));
            // Klassen nach der Höhe: was der Wind aus loser, nicht zu nasser Zelle aufnehmen konnte (für Prüfstein 14)
            long lose = 0;
            for (int k = 0; k + 1 < KLASSEN.length; k++) {
                long zellen = 0, mitAuf = 0;
                double stunden = 0, menge = 0, durch = 0;
                for (int c = 0; c < ae.nc; c++) {
                    if (ae.fest[c] >= ae.festAb || ae.schlick[c] > ae.schlickMax || !(z[c] >= KLASSEN[k] && z[c] < KLASSEN[k + 1])) continue;
                    zellen++;
                    if (ae.aufnahme[c] > 0) mitAuf++;
                    stunden += ae.dauerAuf[c] / 3600;
                    menge += ae.aufnahme[c];
                    durch += ae.menge[c];
                }
                lose += zellen;
                A.info(f("Klasse %.2f–%s m NHN", KLASSEN[k], KLASSEN[k + 1] > 90 ? "∞" : f("%.2f", KLASSEN[k + 1])), f("%d Zellen, aufnehmen konnte der Wind aus %d; Stunden im Mittel %.1f; Aufnahme im Mittel über alle "
                        + "%.1f kg/m; weitergegeben im Mittel %.1f kg/m", zellen, mitAuf, zellen > 0 ? stunden / zellen : 0, zellen > 0 ? menge / zellen : 0, zellen > 0 ? durch / zellen : 0));
            }
            A.info("Lose Zellen über +0,5 m NHN", lose + "");
            if (n.ablage != null)
                A.info("Ablage", f("%s: %d Stände atmos, %d Stände Flugsand, %.0f MB; water.h am Bus gegen das Archiv: größter Unterschied %.1e m", ziel != null ? ziel : archiv, n.ablage.staende, n.ablage.staendeAeolian,
                        n.ablage.bytes / 1e6, n.ablage.groessterUnterschied));
            A.info("Rechenzeit", f("%.0f s, davon atmos %.0f s, aeolian %.1f s (%.1f ms je Schritt)", sekunden, n.atmos.sekunden, n.flugsand.sekunden, 1e3 * n.flugsand.sekunden / Math.max(1, n.flugsand.schritte)));
            return new Ergebnis(ae.abtrag, auf, groesst, n.atmos.regenSumme, regenVorlauf, ersterRegen, n.erste, n.beginn, dt, ae.nc, n.speiser.start);
        }
    }

    static double max(double[] a) {
        double m = 0;
        for (double v : a) m = Math.max(m, v);
        return m;
    }
}
