package com.dan.aufbereitung.demo;

import com.dan.aufbereitung.Aufbereitung;
import com.dan.aufbereitung.Backen;
import com.dan.aufbereitung.GeoTiff;
import com.dan.aufbereitung.Grob;
import com.dan.aufbereitung.Inventar;
import com.dan.aufbereitung.NaturalEarth;
import com.dan.aufbereitung.Rezept;
import com.dan.aufbereitung.Schliessen;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.raum.Erde;
import com.dan.core.raum.Utm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static com.dan.aufbereitung.demo.AufbereitungCheck.A;
import static com.dan.core.pruef.Attest.f;

/**
 * Abnahme von P3 (Werkbuch HalligHooge Teil A, 12.4): das aufbereitete Gebiet gegen seine Quellen und gegen sich
 * selbst, ohne Fenster. Prüfsteine 1–18; die Grenzwerte standen fest, bevor gemessen wurde ([G] heißt gesetzt). Seit
 * P5b Teil 12 mit den Prüfsteinen 19–26: EOT20, Pegel, ERA5 und G2 nach F30 A ({@link PruefungP5b}). Attest
 * {@code docs/status/P3_Gebiet.md}, Status-Bild {@code docs/status/P3_Gebiet.png}.
 *
 * <pre>
 * java -Xmx8g -cp dist\Renderer.jar com.dan.aufbereitung.demo.GebietCheck --rezept ..\HalligHooge\gebiet\Hooge.rezept [--ohne-stapel] [--ohne-attest] [--nur 12]
 * </pre>
 * Mit {@code --nur} nur die genannten Teile (Nummern mit Komma), dann ohne Attest.
 */
public final class GebietCheck {
    /** Wasserstände für die Prüfsteine 8 und 9 (m NHN). */
    static final double[] ETA = {-2, -1, 0, 1, 2, 3};
    /**
     * Prüfstein 8: mittlere Tiefe [G]; Prüfstein 9: über den Überlapp und je Zelle (99 %) [G]. Je Zelle zählen nur
     * Zellen, deren feine Zellen im Mittel mindestens {@link #SCHWELLE} m tief sind (nach der Messung in der
     * Arbeitsumgebung von 1 cm auf 1 m gefasst: Fast trockene Zellen unterscheiden sich durch die Auflösung um bis zu
     * 100 %); die Fassung mit 1 cm und der Unterschied in mittlerer Tiefe stehen daneben.
     */
    static final double TIEFE = 1e-3, UEBERLAPP = 0.005, JE_ZELLE = 0.05, SCHWELLE = 1.0, SCHWELLE_VORHER = 0.01;
    /** Prüfstein 4: Stichproben [G] und Grenze [G]. */
    static final int PROBEN_GROB = 10000, PROBEN_G0 = 2000;
    static final double MM = 1e-3;

    public static void main(String[] args) throws Exception {
        List<String> a = List.of(args);
        int ri = a.indexOf("--rezept");
        if (ri < 0 || ri + 1 >= a.size() || a.get(ri + 1).startsWith("--")) {
            System.out.println("GebietCheck: --rezept braucht den Pfad zum Rezept, z. B. --rezept ..\\HalligHooge\\gebiet\\Hooge.rezept");
            System.out.println("GebietCheck --rezept <datei> [--daten <ordner>] [--ziel <ordner>] [--ohne-stapel] [--ohne-attest] [--nur 12]");
            System.exit(2);
        }
        System.out.println("GebietCheck – com.dan.aufbereitung " + Aufbereitung.VERSION + " (Werkbuch HalligHooge Teil A, P3 und P5b)");
        Rezept rz = Rezept.lies(Path.of(a.get(ri + 1)));
        for (int i = 0; i + 1 < a.size(); i++)
            if (a.get(i).startsWith("--") && !List.of("--rezept", "--ohne-attest", "--ohne-stapel", "--nur").contains(a.get(i))) rz.setze(a.get(i).substring(2), a.get(i + 1));
        int ni = a.indexOf("--nur");
        if (ni >= 0 && ni + 1 < a.size()) nur = List.of(a.get(ni + 1).split(","));
        Path tmp = Files.createTempDirectory("gebiet-check");
        Lage c = new Lage(rz);
        try {
            teil("1 Inventar, zweimal gebacken", () -> inventar(c, tmp));
            teil("2 Quellen auf dem eigenen Rechner", () -> AufbereitungCheck.quellen(c.daten, c.ziel));
            if (!a.contains("--ohne-stapel")) teil("3 Stapel des Gebiets gegen seine Quellen", () -> AufbereitungCheck.gebiet(rz, c.daten, c.ziel, tmp));
            teil("4 Höhenbezug: Stufe 100 m und G0", () -> hoehenbezug(c));
            teil("5 Subgrid gegen das Feingelände", () -> subgrid(c));
            teil("6 Schachteln", () -> schachteln(c));
            teil("7 Bauwerke von G3", () -> PruefungP3c.bauwerkeGebiet(rz, c.daten, c.ziel));
            teil("8 Rauheit", () -> PruefungP3c.rauheitGebiet(rz, c.daten, c.ziel));
            teil("9 G0 gegen Natural Earth", () -> g0NaturalEarth(c));
            teil("10 Schließen", () -> schliessen(c));
            teil("11 Zeiten und Status-Bild", () -> zeitenUndBild(c));
            teil("12 Tide, Pegel, Antrieb und G2 (P5b)", () -> PruefungP5b.daten(rz, c.daten, c.ziel, tmp));
        } finally {
            AufbereitungCheck.loesche(tmp.toFile());
        }
        if (!a.contains("--ohne-attest") && nur == null) {
            Path p = A.schreibe(Path.of("docs", "status", "P3_Gebiet.md"), "P3 – GebietCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Nur diese Teile (Nummern), null: alle. */
    static List<String> nur;

    static void teil(String name, AufbereitungCheck.Teil t) {
        if (nur != null && !nur.contains(name.substring(0, name.indexOf(' ')))) return;
        AufbereitungCheck.teil(name, t);
    }

    /** Was alle Teile brauchen: Rezept, Ordner, Gitterkette aus gebiet.txt, Stufen. */
    static final class Lage {
        final Rezept rz;
        final Path daten, ziel;
        Gebiet gebiet;
        Gelaende gel;
        Gelaende.Stufe anteil, maske, mindest, r1, r10, hundert, zehn;
        double[] manning;
        double auf;
        Gitter g0, g1, g2, g3, g4;
        int f1, f2, f3, f4;
        final Map<String, Subgrid.Packung> packungen = new HashMap<>();
        final List<String> gebietTxt = new ArrayList<>();

        Lage(Rezept rz) {
            this.rz = rz;
            daten = Path.of(rz.text("daten"));
            ziel = Path.of(rz.text("ziel"));
        }

        void laden() throws IOException {
            if (gebiet != null) return;
            gebiet = Gebiet.lies(ziel.toFile());
            gebietTxt.addAll(Files.readAllLines(ziel.resolve("gebiet.txt"), StandardCharsets.UTF_8));
            g0 = gebiet.gitter("G0");
            g1 = gebiet.gitter("G1");
            g2 = gebiet.gitter("G2");
            g3 = gebiet.gitter("G3");
            f1 = gebiet.eintrag("G1").fein();
            f2 = gebiet.eintrag("G2").fein();
            f3 = gebiet.eintrag("G3").fein();
            double[] s = rz.zahlen("g4.suedwest", 2);
            int n = (int) rz.zahl("g4.zellen");
            g4 = Gitter.utm("G4", 32, s[0], s[1], rz.zahl("g4.zelle"), n, n);
            f4 = (int) rz.zahl("g4.fein");
            gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
            for (Gelaende.Stufe st : gel.stufen()) {
                if (st.name.equals(rz.text("grob.stufe", "100m"))) hundert = st;
                if (st.name.equals(rz.text("grund.stufe", "10m"))) zehn = st;
            }
            File zo = ziel.resolve("geschlossen").toFile();
            anteil = Gelaende.Stufe.lies(new File(zo, "100m"));
            maske = Gelaende.Stufe.lies(new File(zo, "10m"));
            mindest = Gelaende.Stufe.lies(ziel.resolve("G3").resolve("mindesthoehe").toFile());
            r1 = Gelaende.Stufe.lies(ziel.resolve("rauheit").resolve("1m").toFile());
            r10 = Gelaende.Stufe.lies(ziel.resolve("rauheit").resolve("10m").toFile());
            manning = new double[16];
            for (String z : Files.readAllLines(ziel.resolve("rauheit").resolve("klassen.txt"), StandardCharsets.UTF_8)) {
                if (z.startsWith("#") || z.isBlank()) continue;
                String[] t = z.strip().split("\\s+");
                manning[Integer.parseInt(t[0])] = Double.parseDouble(t[1]);
            }
            auf = rz.hat("schliessen.auf") ? rz.zahl("schliessen.auf") : 10.0;
        }

        Gitter gitter(String n) { return switch (n) { case "G1" -> g1; case "G2" -> g2; case "G3" -> g3; case "G4" -> g4; default -> g0; }; }

        int fein(String n) { return switch (n) { case "G0" -> rz.hat("g0.fein") ? (int) rz.zahl("g0.fein") : 10; case "G1" -> f1; case "G2" -> f2; case "G3" -> f3; case "G4" -> f4; default -> 0; }; }

        Backen.Zusatz zusatz(String n) {
            return switch (n) {
                case "G1" -> new Backen.Zusatz(anteil, null, null, auf);
                case "G2" -> new Backen.Zusatz(null, maske, null, auf);
                default -> new Backen.Zusatz(null, null, mindest, auf);
            };
        }

        Subgrid.Feingelaende hoehen(Gitter g, String art, int fein) { return Backen.hoehen(gel, g, fein, zusatz(art), null, null); }

        /** Rauheit wie beim Backen; G0 und G1 seit P5e mit {@code rauheit.grob.cd} aus der Tiefe der Höhen {@code h0} (G0) bzw. des Stapels (G1). */
        Subgrid.Feingelaende rauheit(Gitter g, String art, int fein, Subgrid.Feingelaende h0) {
            if (rz.hat("rauheit.grob.cd") && (art.equals("G0") || art.equals("G1")))
                return Backen.rauheitAusTiefe(art.equals("G0") ? h0 : hoehen(g, art, fein), rz.zahl("rauheit.grob.cd"), rz.hat("rauheit.grob.tiefe") ? rz.zahl("rauheit.grob.tiefe") : 1.0);
            return rauheit(g, art, fein);
        }

        Subgrid.Feingelaende rauheit(Gitter g, String art, int fein) {
            return switch (art) {
                case "G0", "G1" -> Backen.rauheit(Backen.manningGrob(rz));
                case "G2" -> Backen.rauheit(null, r10, manning, g, fein);
                default -> Backen.rauheit(r1, r10, manning, g, fein);
            };
        }

        Subgrid.Packung packung(String n) throws IOException {
            Subgrid.Packung p = packungen.get(n);
            if (p == null) packungen.put(n, p = gebiet.packung(n));
            return p;
        }

        List<Grob.Korrektur> korrekturen() {
            List<Grob.Korrektur> l = new ArrayList<>();
            for (String z : gebietTxt) if (z.startsWith("korrektur ")) l.add(Grob.Korrektur.aus(z));
            return l;
        }

        Path pfad(String p) { return AufbereitungCheck.pfad(daten, p); }

        Path tw() {
            String gd = rz.text("grund.datei");
            return gd.contains("!") ? ziel.resolve("quellen").resolve(Path.of(gd.substring(gd.indexOf('!') + 1)).getFileName().toString()) : pfad(gd);
        }
    }

    // ------------------------------------------------------------------ 1 Inventar

    static void inventar(Lage c, Path tmp) throws Exception {
        c.laden();
        Inventar inv = Inventar.lies(c.ziel.resolve("gebiet.txt"));
        int falsch = 0, ohne = 0;
        java.util.Set<String> gruppen = new java.util.TreeSet<>();
        List<String> fehl = new ArrayList<>();
        for (Inventar.Quelle q : inv.quellen) {
            gruppen.add(q.gruppe());
            Path p = c.daten.resolve(q.pfad());
            if (!Files.isRegularFile(p) || !Inventar.sha256(p).equals(q.sha())) { falsch++; fehl.add(q.pfad()); }
            if (q.stand().isBlank() || q.lizenz().isBlank() || q.lizenz().equals("?")) ohne++;
        }
        A.pruefe(falsch == 0 && ohne == 0 && !inv.quellen.isEmpty(), "Prüfstein 1: jede Quelle in gebiet.txt mit Stand, Lizenz und SHA-256 (nachgerechnet)",
                f("%d Quellen in %d Gruppen %s; %d mit anderer Prüfsumme%s, %d ohne Stand oder Lizenz", inv.quellen.size(), gruppen.size(), gruppen, falsch,
                        fehl.isEmpty() ? "" : " (" + String.join(", ", fehl.subList(0, Math.min(3, fehl.size()))) + ")", ohne), "alle");
        Map<String, String> soll = new LinkedHashMap<>();
        for (Inventar.Datei d : inv.dateien) soll.put(d.pfad(), d.sha());
        int abl = 0;
        for (Map.Entry<String, String> e : soll.entrySet()) if (!Inventar.sha256(c.ziel.resolve(e.getKey())).equals(e.getValue())) abl++;
        // G0–G2 neu
        long t0 = System.nanoTime();
        Map<String, String> ist = new LinkedHashMap<>();
        Path n = tmp.resolve("neu");
        NaturalEarth ne = NaturalEarth.lies(c.pfad(c.rz.text("natural_earth")));
        Grob.Grad geb = Grob.Grad.lies(List.of(c.pfad(c.rz.text("grob.gebco"))));
        int t = (int) (c.rz.hat("g0.teilung") ? c.rz.zahl("g0.teilung") : 3);
        double d = geb.d / t;
        byte[] lt = ne.maskeGrad(c.g0.x0, c.g0.y1(), d, (int) Math.rint(c.g0.nx * c.g0.dx / d), (int) Math.rint(c.g0.ny * c.g0.dy / d));
        Backen.G0 b0 = Backen.g0(c.g0, geb, t, lt, c.g1, c.hundert);
        Gebiet.schreibeF32(n.resolve("G0").resolve("ground.z.f32").toFile(), b0.groundZ());
        Gebiet.schreibeF32(n.resolve("G0").resolve("nass.f32").toFile(), b0.nass());
        boolean g0sub = soll.containsKey("G0/subgrid.bin");                 // seit P5: G0 mit Subgrid
        if (g0sub) {
            int f0 = c.fein("G0");
            Backen.Gebacken s0 = Backen.backe(c.g0, f0, Backen.g0Fein(c.g0, f0, geb, t, lt, c.g1, c.hundert, c.anteil, c.auf), c.rauheit(c.g0, "G0", f0, Backen.g0Fein(c.g0, f0, geb, t, lt, c.g1, c.hundert, c.anteil, c.auf)), null, null);
            Gebiet.schreibePackung(n.resolve("G0").resolve("subgrid.bin").toFile(), s0.packung(), c.g0, f0);
        }
        for (String g : new String[] {"G1", "G2"}) {
            Gitter gi = c.gitter(g);
            Backen.Gebacken b = Backen.backe(gi, c.fein(g), c.hoehen(gi, g, c.fein(g)), c.rauheit(gi, g, c.fein(g), null), null, null);
            Backen.schreibe(b, n.resolve(g).toFile());
        }
        int gleich = 0, verschieden = 0;
        List<String> anders = new ArrayList<>();
        for (String p : new String[] {"G0/ground.z.f32", "G0/nass.f32", "G0/subgrid.bin", "G1/ground.z.f32", "G1/subgrid.bin", "G2/ground.z.f32", "G2/subgrid.bin"}) {
            if (p.equals("G0/subgrid.bin") && !g0sub) continue;
            String s = Inventar.sha256(n.resolve(p));
            if (s.equals(soll.get(p))) gleich++;
            else { verschieden++; anders.add(p); }
        }
        A.pruefe(verschieden == 0 && abl == 0, f("Prüfstein 1: G0–G2 neu gebacken (%.0f s) gegen die SHA-256 der Ablage; die Ablage unverändert", (System.nanoTime() - t0) / 1e9),
                f("%d von %d Dateien gleich%s; Ablage: %d von %d unverändert", gleich, gleich + verschieden, anders.isEmpty() ? "" : " (anders: " + anders + ")", soll.size() - abl, soll.size()), "gleich");
        // G3 an einem Fenster
        double[] s = c.rz.hat("ring.Hooge") ? c.rz.zahlen("ring.Hooge", 2) : new double[] {0.5 * (c.g3.x0 + c.g3.x1()), 0.5 * (c.g3.y0 + c.g3.y1())};
        int nf = 100, i0 = Math.max(0, Math.min(c.g3.nx - nf, c.g3.i(s[0]) - nf / 2)), j0 = Math.max(0, Math.min(c.g3.ny - nf, c.g3.j(s[1]) - nf / 2));
        Gitter sub = Gitter.utm("G3", 32, c.g3.x0 + i0 * c.g3.dx, c.g3.y0 + j0 * c.g3.dy, c.g3.dx, nf, nf);
        Subgrid.Packung ps = Backen.backe(sub, c.f3, c.hoehen(sub, "G3", c.f3), c.rauheit(sub, "G3", c.f3), null, null).packung(), pv = c.packung("G3");
        int st = ps.stufen(), ungleich = 0, nzs = sub.zellen(), nzv = c.g3.zellen();
        for (int j = 0; j < nf; j++)
            for (int i = 0; i < nf; i++) {
                int a = i + nf * j, b = (i0 + i) + c.g3.nx * (j0 + j);
                if (Float.floatToIntBits(ps.basis()[a]) != Float.floatToIntBits(pv.basis()[b])) ungleich++;
                for (int q = 0; q < 3 * st; q++) if (ps.tabelle()[a * 3 * st + q] != pv.tabelle()[b * 3 * st + q]) ungleich++;
            }
        int kanten = 0;
        for (int j = 0; j <= nf; j++)
            for (int i = 0; i <= nf; i++)
                for (int r = 0; r < 2; r++) {
                    if (r == 0 && j == nf || r == 1 && i == nf) continue;
                    int es = r == 0 ? i + (nf + 1) * j : (nf + 1) * nf + i + nf * j;
                    int ev = r == 0 ? (i0 + i) + (c.g3.nx + 1) * (j0 + j) : (c.g3.nx + 1) * c.g3.ny + (i0 + i) + c.g3.nx * (j0 + j);
                    kanten++;
                    for (int q = 0; q < 2; q++) if (Float.floatToIntBits(ps.basis()[nzs + 2 * es + q]) != Float.floatToIntBits(pv.basis()[nzv + 2 * ev + q])) ungleich++;
                    if (ps.kantenWerte() != pv.kantenWerte()) ungleich++;
                    for (int q = 0; q < ps.kantenWerte() * st; q++) if (ps.tabelle()[ps.kantenVersatz(es) + q] != pv.tabelle()[pv.kantenVersatz(ev) + q]) ungleich++;
                }
        A.pruefe(ungleich == 0, f("Prüfstein 1: G3 an einem Fenster von %d × %d Zellen (ab Zelle %d, %d) neu gebacken gegen die abgelegte Packung", nf, nf, i0, j0),
                f("%d Zellen, %d Kanten: %d Werte verschieden", nzs, kanten, ungleich), "bitgleich");
    }

    // ------------------------------------------------------------------ 4 Höhenbezug

    /** Ein GeoTIFF in Grad ganz im Speicher, eigene bilineare Abtastung. */
    record Feld(double west, double nord, double d, int w, int h, float[] v) {
        static Feld lies(Path p) throws IOException { return mosaik(List.of(p)); }

        /** Kacheln gleicher Pixelgröße zu einem Feld (eigene Rechnung, nicht über Grob). */
        static Feld mosaik(List<Path> ps) throws IOException {
            List<GeoTiff> t = new ArrayList<>();
            try {
                for (Path p : ps) t.add(GeoTiff.oeffne(p));
                double d = t.get(0).dx, w0 = 1e9, n0 = -1e9, o0 = -1e9, s0 = 1e9;
                for (GeoTiff g : t) {
                    w0 = Math.min(w0, g.x0);
                    n0 = Math.max(n0, g.y0);
                    o0 = Math.max(o0, g.x0 + g.breite * d);
                    s0 = Math.min(s0, g.y0 - g.hoehe * d);
                }
                int w = (int) Math.round((o0 - w0) / d), h = (int) Math.round((n0 - s0) / d);
                float[] v = new float[w * h];
                Arrays.fill(v, Float.NaN);
                for (GeoTiff g : t) {
                    float[] a = g.lies(0, 0, g.breite, g.hoehe);
                    int c0 = (int) Math.round((g.x0 - w0) / d), r0 = (int) Math.round((n0 - g.y0) / d);
                    for (int r = 0; r < g.hoehe; r++) System.arraycopy(a, r * g.breite, v, (r0 + r) * w + c0, g.breite);
                }
                return new Feld(w0, n0, d, w, h, v);
            } finally {
                for (GeoTiff g : t) g.close();
            }
        }

        double wert(double lon, double lat) {
            double c = (lon - west) / d - 0.5, r = (nord - lat) / d - 0.5;
            int i = (int) Math.floor(c), j = (int) Math.floor(r);
            double fx = c - i, fy = r - j, s = 0, g = 0;
            double[] w = {(1 - fx) * (1 - fy), fx * (1 - fy), (1 - fx) * fy, fx * fy};
            int[][] p = {{i, j}, {i + 1, j}, {i, j + 1}, {i + 1, j + 1}};
            for (int k = 0; k < 4; k++) {
                if (p[k][0] < 0 || p[k][1] < 0 || p[k][0] >= this.w || p[k][1] >= h) continue;
                float x = v[p[k][1] * this.w + p[k][0]];
                if (Float.isNaN(x)) continue;
                s += w[k] * x;
                g += w[k];
            }
            return g >= 0.25 ? s / g : Double.NaN;
        }
    }

    static void hoehenbezug(Lage c) throws Exception {
        c.laden();
        List<Grob.Korrektur> k = c.korrekturen();
        Grob.Korrektur ke = k.stream().filter(x -> x.quelle().equals("EMODnet")).findFirst().orElseThrow(), kg = k.stream().filter(x -> x.quelle().equals("GEBCO")).findFirst().orElseThrow();
        List<Path> ep = new ArrayList<>();
        for (String s : c.rz.text("grob.emodnet").split(";")) if (!s.isBlank()) ep.add(c.pfad(s.strip()));
        List<Feld> emod = List.of(Feld.mosaik(ep));
        Feld geb = Feld.lies(c.pfad(c.rz.text("grob.gebco")));
        NaturalEarth ne = NaturalEarth.lies(c.pfad(c.rz.text("natural_earth")));
        int W = c.g1.nx * c.f1, H = c.g1.ny * c.f1;
        double z = c.g1.dx / c.f1;
        byte[] land = ne.maskeUtm(32, c.g1.x0, c.g1.y1(), z, W, H);
        SplittableRandom r = new SplittableRandom(4);
        int ganz = 0, ohneTw = 0, teil = 0, falsch = 0;
        double schlimmst = 0;
        List<Double> rest = new ArrayList<>();
        Gelaende.Stufe.Leser lz = c.zehn == null ? null : c.zehn.leser(), lh = c.hundert.leser();
        try (GeoTiff tw = GeoTiff.oeffne(c.tw())) {
            for (int n = 0; n < PROBEN_GROB; n++) {
                int pc = r.nextInt(W), pr = r.nextInt(H);
                double x0 = c.g1.x0 + pc * z, yN = c.g1.y1() - pr * z;
                int tc = (int) Math.rint((x0 - tw.x0) / tw.dx - 0.5), tr = (int) Math.rint((yN - tw.y0) / tw.dy - 0.5);
                float[] t = tw.lies(tc, tr, 11, 11);
                for (int l = 0; l < 11; l++)
                    for (int m = 0; m < 11; m++) {
                        if (lz == null) continue;
                        float v = lz.wert((int) Math.rint(c.zehn.spalte(tw.x(tc + m))), (int) Math.rint(c.zehn.zeile(tw.y(tr + l))));
                        if (!Float.isNaN(v)) t[l * 11 + m] = v;
                    }
                double s = 0;
                int g = 0, voll = 0;
                for (float v : t) if (!Float.isNaN(v)) voll++;
                for (int l = 0; l < 10; l++)
                    for (int m = 0; m < 10; m++) {
                        double a = 0;
                        int q = 0;
                        for (int o : new int[] {l * 11 + m, l * 11 + m + 1, (l + 1) * 11 + m, (l + 1) * 11 + m + 1})
                            if (!Float.isNaN(t[o])) { a += t[o]; q++; }
                        if (q > 0) { s += a / q; g++; }
                    }
                double e = x0 + z / 2, nn = yN - z / 2;
                double[] gr = Utm.rueckwaerts(e, nn, 32);
                double eigen;
                boolean lnd = land[pr * W + pc] != 0;
                double ve = Double.NaN;
                for (Feld fe : emod) {
                    double v = fe.wert(gr[0], gr[1]);
                    if (!Double.isNaN(v)) { ve = v; break; }
                }
                if (g == 100) { eigen = s / 100; ganz++; }
                else {
                    double ersatz = lnd ? geb.wert(gr[0], gr[1]) : !Double.isNaN(ve) ? ve - ke.wert(e, nn) : geb.wert(gr[0], gr[1]) - kg.wert(e, nn);
                    eigen = (s + (100 - g) * ersatz) / 100;
                    if (g == 0) ohneTw++;
                    else teil++;
                }
                float ist = lh.wert((int) Math.floor((x0 + z / 2 - c.hundert.suedwestOst) / c.hundert.zelle), (int) Math.floor(c.hundert.zeile(nn) + 0.5));
                double dd = Math.abs(ist - eigen);
                if (!(dd <= MM)) falsch++;
                if (!Double.isNaN(dd)) schlimmst = Math.max(schlimmst, dd);
                if (voll == 121 && !lnd && s / 100 < c.rz.zahl("grob.unter") && !Double.isNaN(ve)) rest.add(Math.abs(ve - ke.wert(e, nn) - s / 100));
            }
        }
        A.pruefe(falsch == 0, f("Prüfstein 4: %d Pixel der Stufe 100 m gegen eine eigene Rechnung (ganz aus TrilaWatt: Mittel an 10 × 10 Punkten; sonst mit EMODnet bzw. GEBCO und der Korrektur aus gebiet.txt)", PROBEN_GROB),
                f("%d ganz aus TrilaWatt, %d teilweise, %d ohne; %d über 1 mm, größte Abweichung %.1e m", ganz, teil, ohneTw, falsch, schlimmst), "≤ 1 mm [G]");
        double[] rs = rest.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        A.info("Rest der Korrektur von EMODnet gegen TrilaWatt (Meer, TrilaWatt unter " + c.rz.text("grob.unter") + " m, an den Stichproben)", rs.length == 0 ? "keine Punkte"
                : f("%d Punkte: Median %.3f m, 95 %% %.3f m; Korrektur %s", rs.length, rs[rs.length / 2], rs[(int) (0.95 * (rs.length - 1))], ke.zeile().substring(10)));
        // G0
        float[] gz = c.gebiet.groundZ("G0"), nass = c.gebiet.nass("G0");
        int t = (int) (c.rz.hat("g0.teilung") ? c.rz.zahl("g0.teilung") : 3);
        double d = geb.d() / t;
        int tu = (int) Math.rint(c.g0.dx / d), tv = (int) Math.rint(c.g0.dy / d);
        int u0 = (int) Math.rint((c.g0.x0 - geb.west()) / d), v0 = (int) Math.rint((geb.nord() - c.g0.y1()) / d);
        int f0 = 0, fn = 0;
        double sz = 0, sn = 0;
        for (int n = 0; n < PROBEN_G0; n++) {
            int i = r.nextInt(c.g0.nx), j = r.nextInt(c.g0.ny), jn = c.g0.ny - 1 - j;
            byte[] lm = ne.maskeGrad(c.g0.x0 + i * c.g0.dx, c.g0.y1() - jn * c.g0.dy, d, tu, tv);
            double am = 0, sm = 0, aa = 0, sa = 0;
            for (int pv = 0; pv < tv; pv++) {
                double lat = geb.nord() - (v0 + jn * tv + pv + 0.5) * d;
                double ar = Erde.zonenflaeche(lat - d / 2, lat + d / 2, d);
                for (int pu = 0; pu < tu; pu++) {
                    double lon = geb.west() + (u0 + i * tu + pu + 0.5) * d;
                    double[] e = Utm.vorwaerts(lon, lat, 32);
                    double v = Double.NaN;
                    if (c.g1.enthaelt(e[0], e[1])) v = c.hundert.bilinear(e[0], e[1]);
                    if (Double.isNaN(v)) v = geb.v()[((v0 + jn * tv + pv) / t) * geb.w() + (u0 + i * tu + pu) / t];
                    sa += ar * v;
                    aa += ar;
                    if (lm[pv * tu + pu] == 0) { sm += ar * v; am += ar; }
                }
            }
            double ez = am > 0 ? sm / am : sa / aa, en = am / aa;
            int cc = c.g0.index(i, j);
            double dz = Math.abs(gz[cc] - ez), dn = Math.abs(nass[cc] - en);
            sz = Math.max(sz, dz);
            sn = Math.max(sn, dn);
            if (!(dz <= MM)) f0++;
            if (!(dn <= 1e-6)) fn++;
        }
        A.pruefe(f0 == 0 && fn == 0, f("Prüfstein 4: %d Zellen von G0 gegen eine eigene Rechnung aus GEBCO (%d × %d Teile je Pixel), der Stufe 100 m in G1 und Natural Earth", PROBEN_G0, t, t),
                f("ground.z: %d über 1 mm (größte %.1e m); nasser Anteil: %d über 1e-6 (größte %.1e)", f0, sz, fn, sn), "≤ 1 mm [G]");
    }

    // ------------------------------------------------------------------ 5 Subgrid

    /** Volumen je Zelle aus dem Feingelände (Summe über die Pixel) für alle ETA; Zeilen von Zellen in Bändern. */
    static double[][] pixelVolumen(Gitter g, int fein, Subgrid.Feingelaende h) {
        double px = g.dx * g.dy / ((double) fein * fein);
        double[][] v = new double[ETA.length][g.zellen()];
        int w = g.nx * fein, band = (int) Math.max(1, Math.min(g.ny, (16L << 20) / ((long) w * fein)));
        double[] z = new double[w * band * fein];
        for (int j0 = 0; j0 < g.ny; j0 += band) {
            int nb = Math.min(band, g.ny - j0);
            h.lies(0, j0 * fein, w, nb * fein, z);
            final int jj = j0;
            IntStream.range(0, nb).parallel().forEach(jb -> {
                for (int i = 0; i < g.nx; i++) {
                    double[] s = new double[ETA.length];
                    for (int b = 0; b < fein; b++)
                        for (int a = 0; a < fein; a++) {
                            double x = z[(jb * fein + b) * w + i * fein + a];
                            for (int e = 0; e < ETA.length; e++) if (ETA[e] > x) s[e] += ETA[e] - x;
                        }
                    for (int e = 0; e < ETA.length; e++) v[e][g.index(i, jj + jb)] = s[e] * px;
                }
            });
        }
        return v;
    }

    static void subgrid(Lage c) throws Exception {
        c.laden();
        for (String n : new String[] {"G1", "G2", "G3"}) {
            long t0 = System.nanoTime();
            Gitter g = c.gitter(n);
            Subgrid.Packung p = c.packung(n);
            double[][] vx = pixelVolumen(g, c.fein(n), c.hoehen(g, n, c.fein(n)));
            double schlimmst = 0;
            String wo = "";
            for (int e = 0; e < ETA.length; e++) {
                double sp = 0, sx = 0;
                for (int k = 0; k < g.zellen(); k++) {
                    sp += p.volumen(k, ETA[e]);
                    sx += vx[e][k];
                }
                double d = Math.abs(sp - sx) / (g.zellen() * p.zellflaeche());
                if (d > schlimmst) { schlimmst = d; wo = f("η = %+.0f m", ETA[e]); }
            }
            A.hoechstens(f("Prüfstein 8 (%s, %d × %d Zellen, fein %d; %.0f s): Volumen aller Zellen aus der Packung bei η = −2 … +3 m gegen die Summe über die Pixel des Feingeländes, als mittlere Tiefe (größte bei %s)",
                    n, g.nx, g.ny, c.fein(n), (System.nanoTime() - t0) / 1e9, wo), schlimmst, TIEFE, "m");
        }
    }

    // ------------------------------------------------------------------ 6 Schachteln

    /** Je Zelle: hat das gebackene Feingelände ein geschlossenes Pixel? */
    static boolean[] geschlossen(Lage c, String n) {
        Gitter g = c.gitter(n);
        int fein = c.fein(n);
        double s = g.dx / fein;
        boolean[] zu = new boolean[g.zellen()];
        if (n.equals("G3")) return zu;
        IntStream.range(0, g.ny).parallel().forEach(j -> {
            Gelaende.Stufe.Leser la = c.anteil.leser(), lm = c.maske.leser();
            for (int i = 0; i < g.nx; i++) {
                boolean z = false;
                for (int b = 0; b < fein && !z; b++)
                    for (int a = 0; a < fein && !z; a++) {
                        double x = g.x0 + (i * fein + a + 0.5) * s, y = g.y0 + (j * fein + b + 0.5) * s;
                        z = n.equals("G1") ? la.imPixel(x, y) >= 0.5f : Schliessen.zu(lm, c.maske, x, y);
                    }
                zu[g.index(i, j)] = z;
            }
        });
        return zu;
    }

    static void schachteln(Lage c) throws Exception {
        c.laden();
        Map<String, boolean[]> zu = new HashMap<>();
        for (String n : new String[] {"G1", "G2"}) zu.put(n, geschlossen(c, n));
        for (String[] paar : new String[][] {{"G1", "G2"}, {"G2", "G3"}}) {
            Gitter gg = c.gitter(paar[0]), gf = c.gitter(paar[1]);
            int k = gf.passtIn(gg);
            Subgrid.Packung pg = c.packung(paar[0]), pf = c.packung(paar[1]);
            int i0 = (int) Math.rint((gf.x0 - gg.x0) / gg.dx), j0 = (int) Math.rint((gf.y0 - gg.y0) / gg.dy), ni = gf.nx / k, nj = gf.ny / k;
            boolean[] zg = zu.get(paar[0]), zf = zu.getOrDefault(paar[1], new boolean[gf.zellen()]);
            double[] sg = new double[ETA.length], sf = new double[ETA.length];
            List<Double> rel = new ArrayList<>(), relVorher = new ArrayList<>(), tiefe = new ArrayList<>();
            int zellen = 0, aus = 0;
            for (int j = 0; j < nj; j++)
                for (int i = 0; i < ni; i++) {
                    int cg = gg.index(i0 + i, j0 + j);
                    boolean x = zg[cg];
                    for (int b = 0; b < k && !x; b++) for (int a = 0; a < k && !x; a++) x = zf[gf.index(i * k + a, j * k + b)];
                    if (x) { aus++; continue; }
                    zellen++;
                    for (int e = 0; e < ETA.length; e++) {
                        double vg = pg.volumen(cg, ETA[e]), vf = 0;
                        for (int b = 0; b < k; b++) for (int a = 0; a < k; a++) vf += pf.volumen(gf.index(i * k + a, j * k + b), ETA[e]);
                        sg[e] += vg;
                        sf[e] += vf;
                        if (vf >= SCHWELLE * pg.zellflaeche()) rel.add(Math.abs(vg - vf) / vf);
                        if (vf >= SCHWELLE_VORHER * pg.zellflaeche()) relVorher.add(Math.abs(vg - vf) / vf);
                        tiefe.add(Math.abs(vg - vf) / pg.zellflaeche());
                    }
                }
            double ue = 0;
            String wo = "";
            for (int e = 0; e < ETA.length; e++) {
                double d = sf[e] > 0 ? Math.abs(sg[e] - sf[e]) / sf[e] : 0;
                if (d > ue) { ue = d; wo = f("η = %+.0f m", ETA[e]); }
            }
            double[] r = rel.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double q99 = q99(rel), q99v = q99(relVorher), q99t = q99(tiefe);
            A.pruefe(ue <= UEBERLAPP && q99 <= JE_ZELLE && zellen > 0, f("Prüfstein 9 (%s/%s): Volumen jeder groben Zelle gegen die Summe ihrer %d feinen bei η = −2 … +3 m, über %d grobe Zellen ohne geschlossenes Pixel (%d mit)",
                            paar[0], paar[1], k * k, zellen, aus),
                    f("über den Überlapp %.3f %% (größte bei %s); je Zelle 99 %% %.2f %% (%d Werte, feine Zellen im Mittel ab %.0f m tief)", 100 * ue, wo, 100 * q99, r.length, SCHWELLE),
                    "≤ 0,5 % [G]; ≤ 5 % [G]");
            A.info(f("Prüfstein 9 (%s/%s) daneben", paar[0], paar[1]), f("je Zelle ab 1 cm mittlerer Tiefe (Fassung vor der Messung) 99 %% %.1f %% (%d Werte); Unterschied in mittlerer Tiefe der groben Zelle 99 %% %.3f m",
                    100 * q99v, relVorher.size(), q99t));
        }
    }

    static double q99(List<Double> l) {
        double[] r = l.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return r.length == 0 ? 0 : r[(int) Math.floor(0.99 * (r.length - 1))];
    }

    // ------------------------------------------------------------------ 9 G0

    static void g0NaturalEarth(Lage c) throws Exception {
        c.laden();
        Feld geb = Feld.lies(c.pfad(c.rz.text("grob.gebco")));
        float[] nass = c.gebiet.nass("G0");
        int t = (int) (c.rz.hat("g0.teilung") ? c.rz.zahl("g0.teilung") : 3);
        double d = geb.d() / t;
        int tu = (int) Math.rint(c.g0.dx / d), tv = (int) Math.rint(c.g0.dy / d);
        int u0 = (int) Math.rint((c.g0.x0 - geb.west()) / d), v0 = (int) Math.rint((geb.nord() - c.g0.y1()) / d);
        int[] ab = new int[1], kueste = new int[1], abK = new int[1];
        double[] unter = new double[c.g0.zellen()];
        IntStream.range(0, c.g0.ny).parallel().forEach(jn -> {
            int j = c.g0.ny - 1 - jn;
            for (int i = 0; i < c.g0.nx; i++) {
                double a = 0, u = 0;
                for (int pv = 0; pv < tv; pv++) {
                    double lat = geb.nord() - (v0 + jn * tv + pv + 0.5) * d, ar = Erde.zonenflaeche(lat - d / 2, lat + d / 2, d);
                    for (int pu = 0; pu < tu; pu++) {
                        a += ar;
                        if (geb.v()[((v0 + jn * tv + pv) / t) * geb.w() + (u0 + i * tu + pu) / t] < 0) u += ar;
                    }
                }
                unter[c.g0.index(i, j)] = u / a;
            }
        });
        for (int k = 0; k < unter.length; k++) {
            boolean an = Math.min(unter[k], nass[k]) < 1 && Math.max(unter[k], nass[k]) > 0;
            boolean x = Math.abs(unter[k] - nass[k]) > 0.5;
            if (x) ab[0]++;
            if (an) { kueste[0]++; if (x) abK[0]++; }
        }
        double anteil = ab[0] / (double) unter.length;
        A.pruefe(anteil <= 0.03, "Prüfstein 14: G0 – Anteil unter 0 m nach GEBCO gegen den nassen Anteil (Meer nach Natural Earth); abweichend, wo beide um mehr als ½ auseinanderliegen, über alle Zellen",
                f("%d von %d Zellen (%.2f %%); an der Küste %d von %d (%.1f %%)", ab[0], unter.length, 100 * anteil, abK[0], kueste[0], 100.0 * abK[0] / Math.max(1, kueste[0])), "≤ 3 % [G]");
    }

    // ------------------------------------------------------------------ 10 Schließen

    static void schliessen(Lage c) throws Exception {
        c.laden();
        int richtig = 0, n = 0;
        StringBuilder b = new StringBuilder();
        Gelaende.Stufe.Leser lm = c.maske.leser();
        for (String k : c.rz.alle().keySet()) {
            boolean soll;
            if (k.startsWith("zu.")) soll = true;
            else if (k.startsWith("offen.")) soll = false;
            else continue;
            n++;
            double[] p = c.rz.zahlen(k, 2);
            boolean px = lm.imPixel(p[0], p[1]) == 1f;
            int i = c.g2.i(p[0]), j = c.g2.j(p[1]), zu = 0;
            double s = c.g2.dx / c.f2;
            for (int bb = 0; bb < c.f2; bb++)
                for (int a = 0; a < c.f2; a++)
                    if (Schliessen.zu(lm, c.maske, c.g2.x0 + (i * c.f2 + a + 0.5) * s, c.g2.y0 + (j * c.f2 + bb + 0.5) * s)) zu++;
            boolean ok = soll ? px && zu == c.f2 * c.f2 : !px && zu == 0;
            if (ok) richtig++;
            b.append(f("%s%s: Pixel %s, Zelle von G2 %d/%d geschlossen", b.length() > 0 ? "; " : "", k, px ? "zu" : "offen", zu, c.f2 * c.f2));
        }
        A.pruefe(n > 0 && richtig == n, "Prüfstein 18: Sonden im Rezept – geschlossen bzw. offen in der Maske und in ihrer Zelle von G2", n == 0 ? "keine Sonden" : b.toString(), "alle");
        for (String z : c.gebietTxt) if (z.startsWith("schliessen\t")) A.info("Maske", z.substring(11));
        for (String z : c.gebietTxt) if (z.startsWith("geschlossen ")) A.info("Geschlossen beim Backen", z.substring(12));
    }

    // ------------------------------------------------------------------ 11 Zeiten und Bild

    static void zeitenUndBild(Lage c) throws Exception {
        c.laden();
        StringBuilder b = new StringBuilder();
        for (String z : c.gebietTxt) if (z.startsWith("zeit ")) b.append(b.length() > 0 ? ", " : "").append(z.substring(5));
        long t0 = System.nanoTime();
        Backen.backe(c.g4, c.f4, c.hoehen(c.g4, "G4", c.f4), c.rauheit(c.g4, "G4", c.f4), null, null);
        double t4 = (System.nanoTime() - t0) / 1e9;
        A.pruefe(b.length() > 0, f("Prüfstein 15: Zeiten beim Backen (gebiet.txt) und G4 an der Hanswarft (%s) jetzt", c.g4), f("%s; G4 jetzt %.2f s", b, t4), "gemessen");
        Path p = Path.of("docs", "status", "P3_Gebiet.png");
        StatusBild.zeichne(c, p);
        A.pruefe(Files.isRegularFile(p), "Prüfstein 16: Status-Bild mit Gitterkette, G1 und G2 mit geschlossenen Flächen, G3 mit Japsand, Hooge in 1 m mit Deichkanten und Sielen, Naht",
                p.toAbsolutePath().toString(), "Sichtprüfung (Nutzer)");
    }
}
