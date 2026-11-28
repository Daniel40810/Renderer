package com.dan.aufbereitung;

import com.dan.core.astro.Partialtide;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Kornfeld;
import com.dan.core.gitter.Tidenfeld;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.pruef.Harmonik;
import com.dan.core.pruef.Pegel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Die Aufbereitung eines Gebiets nach seinem Rezept (Werkbuch HalligHooge Teil A, 10.4). Schritte:
 * <ol>
 * <li>{@code gelaende} (P3b): der Kachelstapel des Feingeländes aus Grund (TrilaWatt, 10 m) und Land (DGM1, 1 m) mit
 * Füllwert, Saum, Hof und Mitteln;</li>
 * <li>{@code bauwerke} (P3c): Deiche und Siele als Kanten von G3, Mindesthöhen der geschnittenen Zellen ({@link Bauwerke});</li>
 * <li>{@code rauheit} (P3c): Rauheitsklassen 1 m und 10 m ({@link Rauheit});</li>
 * <li>{@code grob} (P3d): die Stufe 100 m über der Hülle von G1 mit dem Höhenbezug aus F22 ({@link Grob});</li>
 * <li>{@code schliessen} (P3d): die Maske für Land hinter Deichen ({@link Schliessen});</li>
 * <li>{@code backen} (P3d): G0–G3, G4 zur Probe, {@code gebiet.txt} mit dem Inventar ({@link Backen}, {@link Inventar}); seit P5 G0 mit Subgrid.</li>
 * <li>{@code vorgabe} (P4d): die Vorgabe der Tide am Rand von G2 und an den Pegeln aus EasyGSH ({@link Vorgabe}),
 * {@code vorgabe.txt}; braucht {@code gebiet.txt}.</li>
 * <li>{@code eot20} (P5b): EOT20 mit 17 Partialtiden auf G0 ausgeschnitten ({@link Eot20}, {@link Tidenfeld}),
 * {@code tide/}; meldet die Abdeckung des Rands von G0 und die Minima von M2 in der Nordsee;</li>
 * <li>{@code pegel} (P5b): PEGELONLINE in m NHN, Ausreißer verworfen, harmonische Analyse, Kennwerte
 * ({@link Pegelonline}, {@link Pegel}), {@code pegel/};</li>
 * <li>{@code antrieb} (P5b): ERA5 aus GRIB, stündlich {@code u10}, {@code v10}, {@code msl} ({@link Grib},
 * {@link Era5}, {@link Wetterfeld}), {@code antrieb/}; die Dateien lädt das Kartenwerkzeug aus dem Climate Data Store.
 * Seit P5e (F34) mit {@code antrieb.schub} im Rezept dazu {@code iews}, {@code inss} und {@code charnock}
 * (Quelle {@code era5_cds_schub}); {@code charnock} über Land aus den Nachbarn über See ({@link Era5#charnockFuellen}).</li>
 * </ol>
 *
 * <pre>
 * java com.dan.aufbereitung.Aufbereitung &lt;rezept&gt; [--schritte gelaende,bauwerke,rauheit,grob,schliessen,backen,vorgabe,eot20,pegel,antrieb] [--daten &lt;ordner&gt;] [--ziel &lt;ordner&gt;]
 * </pre>
 *
 * Ergebnis unter {@code ziel}: {@code gelaende/1m}, {@code 10m}, {@code 100m} (Format in {@link Gelaende}),
 * {@code geschlossen/} ({@code 10m}, {@code 100m}), {@code G0/} bis {@code G3/} ({@code ground.z.f32}, {@code subgrid.bin},
 * bei G0 {@code nass.f32}, bei G3 {@code bauwerke.txt} und {@code mindesthoehe/}), {@code rauheit/} ({@code 1m},
 * {@code 10m}, {@code klassen.txt}), {@code gebiet.txt}, seit P5b {@code tide/}, {@code pegel/}, {@code antrieb/},
 * Quellen aus ZIP-Dateien in {@code quellen/}, das Protokoll in {@code protokoll.txt}. Ohne {@code --schritte} laufen
 * alle; ein späterer Schritt liest, was ein früherer abgelegt hat.
 */
public final class Aufbereitung {
    public static final String VERSION = "0.6";
    /** Alle Schritte in ihrer Folge. */
    public static final List<String> SCHRITTE = List.of("gelaende", "bauwerke", "rauheit", "grob", "schliessen", "backen", "vorgabe", "eot20", "pegel", "antrieb", "wind");

    private final Rezept rezept;
    private final Path daten, ziel;
    private final StringBuilder protokoll = new StringBuilder();
    /** Gemessenes Land nach dem Füllwert, aus dem Schritt gelaende (für die Rauheit). */
    private Map<Long, float[]> land;
    private Bauwerke bauwerke;

    public Aufbereitung(Rezept rezept, Path daten, Path ziel) {
        this.rezept = rezept;
        this.daten = daten;
        this.ziel = ziel;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args[0].startsWith("--") || args.length % 2 == 0) {
            System.out.println(args.length < 1 || args[0].startsWith("--") ? "Aufbereitung: der Pfad zum Rezept fehlt, z. B. ..\\HalligHooge\\gebiet\\Hooge.rezept"
                    : "Aufbereitung: " + args[args.length - 1] + " ohne Wert");
            System.out.println("Aufbereitung <rezept> [--schritte " + String.join(",", SCHRITTE) + "] [--daten <ordner>] [--ziel <ordner>]");
            System.exit(2);
        }
        Rezept r = Rezept.lies(Path.of(args[0]));
        String schritte = String.join(",", SCHRITTE);
        for (int i = 1; i + 1 < args.length; i += 2) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("unbekannt: " + args[i]);
            if (args[i].equals("--schritte")) schritte = args[i + 1];
            else r.setze(args[i].substring(2), args[i + 1]);
        }
        Aufbereitung a = new Aufbereitung(r, Path.of(r.text("daten")), Path.of(r.text("ziel")));
        a.lauf(List.of(schritte.split(",")));
    }

    /** Führt die Schritte aus und schreibt das Protokoll (angehängt, wenn nicht alle laufen). */
    public void lauf(List<String> schritte) throws IOException {
        long t0 = System.nanoTime();
        Files.createDirectories(ziel);
        log("Aufbereitung " + VERSION + ", Rezept " + rezept.datei + ", Schritte " + String.join(", ", schritte) + ", "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        for (Map.Entry<String, String> e : rezept.alle().entrySet()) log("  " + e.getKey() + " = " + e.getValue());
        for (String s : schritte) {
            switch (s.strip()) {
                case "gelaende" -> gelaende();
                case "bauwerke" -> bauwerke();
                case "rauheit" -> rauheit();
                case "grob" -> grob();
                case "schliessen" -> schliessen();
                case "backen" -> backen();
                case "vorgabe" -> vorgabe();
                case "eot20" -> eot20();
                case "pegel" -> pegel();
                case "antrieb" -> antrieb();
                case "wind" -> wind();
                case "luftbild" -> luftbild();
                case "seegang" -> seegang();
                case "luft" -> luft();
                case "boee" -> boee();
                case "sediment" -> sediment();
                case "sentinel" -> sentinel();
                case "morpho" -> morpho();
                case "gebaeude" -> gebaeude();
                case "gestalt" -> gestalt();
                case "gehoelz" -> gehoelz();
                case "zonen" -> zonen();
                case "brandung" -> brandung();
                default -> throw new IllegalArgumentException("unbekannter Schritt: " + s);
            }
        }
        log(f("Fertig in %.1f s.", (System.nanoTime() - t0) / 1e9));
        boolean alle = schritte.size() == SCHRITTE.size();
        Files.writeString(ziel.resolve("protokoll.txt"), (alle ? "" : "\n") + protokoll, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                alle ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.APPEND);
    }

    /** Baut den Kachelstapel: Grund, Füllwert, Land mit Saum und Hof, Mittel. */
    public Gelaende gelaende() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt gelaende");
        double[] g2 = rezept.zahlen("grund.gebiet", 4), g3 = rezept.zahlen("land.gebiet", 4);
        File stapel = ziel.resolve("gelaende").toFile();
        // Grund
        Path qg = quelle(rezept.text("grund.datei"));
        Gelaende.Stufe grund;
        try (GeoTiff t = GeoTiff.oeffne(qg)) {
            log("Grund: " + t);
            if (t.epsg != 0 && t.epsg != 25832 && t.epsg != 32632) throw new IOException(qg + ": EPSG " + t.epsg + " statt 25832");
            grund = Stapel.grund(t, new File(stapel, rezept.text("grund.stufe", "10m")), g2[0], g2[1], g2[2], g2[3], 1000,
                    "Grund: " + qg.getFileName() + " (Werkbuch HalligHooge Teil A, 10.4)");
        }
        log(f("  %s, %.1f s", grund, (System.nanoTime() - t0) / 1e9));
        // Land
        long t1 = System.nanoTime();
        List<Dgm1.Kachel> kl = Dgm1.kacheln(pfad(rezept.text("land.ordner")), rezept.ganze("land.jahre"), g3[0], g3[1], g3[2], g3[3]);
        land = new HashMap<>();
        int[] b = new int[4];
        long unsauber = 0;
        for (Dgm1.Kachel k : kl) {
            float[] v = Dgm1.lies(k, b);
            if (b[1] != 0 || b[2] != 0) {
                unsauber++;
                log(f("  %s: %d Punkte außerhalb, %d doppelt", k.name(), b[1], b[2]));
            }
            land.put(Fuellwert.schluessel(k.ostKm(), k.nordKm()), v);
        }
        if (unsauber > 0) throw new IOException(unsauber + " Kacheln des Lands nicht sauber");
        log(f("Land: %d Kacheln (Jahre %s), gelesen in %.1f s", kl.size(), rezept.ganze("land.jahre"), (System.nanoTime() - t1) / 1e9));
        Fuellwert fw = new Fuellwert(rezept.zahl("land.fuellwert"), rezept.zahl("land.toleranz"), rezept.zahl("land.flaeche"));
        Fuellwert.Ergebnis fe = fw.entferne(land, Dgm1.N, Dgm1.ZELLE);
        log(f("  Füllwert %.2f m: %d Flächen ab %.0f m² mit %d Pixeln entfernt; %d kleinere Flächen mit %d Pixeln behalten", fw.wert, fe.flaechen(), fw.flaeche,
                fe.pixel(), fe.flaechenBehalten(), fe.kleinBehalten()));
        long t2 = System.nanoTime();
        int ko = (int) Math.round((g3[2] - g3[0]) / (Dgm1.N * Dgm1.ZELLE)), kn = (int) Math.round((g3[3] - g3[1]) / (Dgm1.N * Dgm1.ZELLE));
        Stapel.Ergebnis se = Stapel.land(land, Dgm1.N, Dgm1.ZELLE, grund, new File(stapel, rezept.text("land.stufe", "1m")), g3[0], g3[1], ko, kn,
                rezept.zahl("naht.saum"), rezept.zahl("naht.saum.max"), rezept.zahl("naht.steigung"),
                "Land: DGM1 " + rezept.ganze("land.jahre") + ", ohne Füllwert, mit Saum und Hof (Werkbuch HalligHooge Teil A, 10.4)");
        log(f("  Landstufe: %s; %.1f s", se, (System.nanoTime() - t2) / 1e9));
        log(f("  Schritt gelaende in %.1f s.", (System.nanoTime() - t0) / 1e9));
        return Gelaende.ordner(stapel);
    }

    /** Das Gitter G3 aus dem Rezept ({@code g3.gebiet}, {@code g3.zelle}). */
    Gitter g3() {
        double[] g = rezept.zahlen("g3.gebiet", 4);
        return Gitter.utmBereich("G3", 32, g[0], g[1], g[2], g[3], rezept.zahl("g3.zelle"));
    }

    /** Liest OSM aus einer oder mehreren Dateien (durch ; getrennt). */
    Osm osm(String schluessel, String vorgabe) throws IOException {
        List<Path> p = new ArrayList<>();
        for (String s : rezept.text(schluessel, vorgabe).split(";")) if (!s.isBlank()) p.add(pfad(s.strip()));
        Osm o = Osm.lies(32, p.toArray(new Path[0]));
        log(f("  OSM: %d Punkte, %d Wege, %d Flächen (%d Multipolygone mit offenen Ringen); Stand %s", o.punkte.size(), o.wege.size(), o.flaechen.size(),
                o.offeneRinge, o.stand));
        return o;
    }

    /** Schritt bauwerke: Deiche und Siele als Kanten von G3 (10.4, Schritt 5). */
    public Bauwerke bauwerke() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt bauwerke");
        Gelaende gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
        Gitter g = g3();
        Osm osm = osm("bauwerke.osm", null);
        Bauwerke b = new Bauwerke(g, gel);
        b.korridor = rezept.hat("bauwerke.korridor") ? rezept.zahl("bauwerke.korridor") : b.korridor;
        b.luecke = rezept.hat("bauwerke.luecke") ? rezept.zahl("bauwerke.luecke") : b.luecke;
        b.sielAbstand = rezept.hat("siel.abstand") ? rezept.zahl("siel.abstand") : b.sielAbstand;
        b.rinne = rezept.hat("siel.rinne") ? rezept.zahl("siel.rinne") : b.rinne;
        b.umgebung = rezept.hat("siel.umgebung") ? rezept.zahl("siel.umgebung") : b.umgebung;
        b.sielA = rezept.hat("siel.a") ? rezept.zahl("siel.a") : b.sielA;
        b.sielMu = rezept.hat("siel.mu") ? rezept.zahl("siel.mu") : b.sielMu;
        b.linien(osm, Osm.Filter.lies(rezept.text("bauwerke.deiche")), Osm.Filter.lies(rezept.text("bauwerke.ohne", "")));
        double l = 0;
        for (Bauwerke.Linie x : b.linien) l += x.laenge();
        log(f("  %s; Linien aus OSM: %d, %.1f km", g, b.linien.size(), l / 1000));
        Map<String, double[]> sonden = new LinkedHashMap<>();
        for (String k : rezept.alle().keySet()) if (k.startsWith("ring.")) sonden.put(k.substring(5), rezept.zahlen(k, 2));
        b.bauen(osm, Osm.Filter.lies(rezept.text("siel.punkte", "")), Osm.Filter.lies(rezept.text("siel.wege", "")), Osm.Filter.lies(rezept.text("siel.bruecke", "")), sonden);
        long dazu = b.luecken.stream().filter(t -> !t.startsWith("offen")).count();
        log(f("  Lücken zwischen Linien: %d geschlossen, %d offen", dazu, b.luecken.size() - dazu));
        for (String t : b.luecken) log("    " + t);
        log(f("  Kanten: %d (davon %d mit Siel); Siele: %d", b.kanten.size(), b.kanten.values().stream().filter(k -> k.art.equals("siel")).count(), b.siele.size()));
        for (Bauwerke.Siel s : b.siele)
            log(f("    %s: Kante %d bei O %.0f N %.0f, Sohle %.2f m, Krone geschlossen %.2f m; %s", s.name(), s.kante(), s.ost(), s.nord(), s.sohle(), s.krone(), s.quellen()));
        for (String t : b.niedrig) log("    niedrige Stelle ohne Siel: " + t);
        for (Bauwerke.Ring r : b.ringe)
            if (!r.namen().isEmpty() || r.flaeche() >= 1e5)
                log(f("  Ring %d %s: %.2f km², Krone am Rand %.2f–%.2f m (niedrigste an Kante %d), %d Randkanten; Seitenwechsel %.0f m² (%.2f %%), davon gehoben %.0f m²",
                        r.nummer(), r.namen(), r.flaeche() / 1e6, r.niedrigsteKrone(), r.hoechsteKrone(), r.niedrigsteKante(), r.randKanten(), r.seitenwechsel(),
                        100 * r.seitenwechsel() / r.flaeche(), r.gehoben()));
        if (!b.bericht().isEmpty()) log("  " + b.bericht().strip());
        b.schreibe(ziel.resolve(g.name).toFile(), "Aufbereitung " + VERSION + ", Rezept " + rezept.datei.getFileName() + ", "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\nOSM: " + osm.stand);
        log(f("  Schritt bauwerke in %.1f s.", (System.nanoTime() - t0) / 1e9));
        bauwerke = b;
        return b;
    }

    /** Schritt rauheit: Klassen 1 m und 10 m (10.4, Schritt 6). */
    public void rauheit() throws IOException {
        long t0 = System.nanoTime();
        if (bauwerke == null) bauwerke();
        log("Schritt rauheit");
        if (land == null) {
            double[] g3 = rezept.zahlen("land.gebiet", 4);
            land = new HashMap<>();
            for (Dgm1.Kachel k : Dgm1.kacheln(pfad(rezept.text("land.ordner")), rezept.ganze("land.jahre"), g3[0], g3[1], g3[2], g3[3]))
                land.put(Fuellwert.schluessel(k.ostKm(), k.nordKm()), Dgm1.lies(k, null));
            new Fuellwert(rezept.zahl("land.fuellwert"), rezept.zahl("land.toleranz"), rezept.zahl("land.flaeche")).entferne(land, Dgm1.N, Dgm1.ZELLE);
            log(f("  Land: %d Kacheln neu gelesen", land.size()));
        }
        Gelaende gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
        Osm osm = osm("rauheit.osm", rezept.text("bauwerke.osm"));
        Path dtk = rezept.hat("rauheit.dtk5") ? pfad(rezept.text("rauheit.dtk5")) : null;
        Rauheit r = new Rauheit(gel, rezept.text("grund.stufe", "10m"), land, bauwerke, osm, dtk);
        if (rezept.hat("rauheit.n")) {
            double[] n = rezept.zahlen("rauheit.n", 8);
            System.arraycopy(n, 0, r.manning, 1, 8);
        }
        r.wasserUnter = rezept.hat("rauheit.wasser.unter") ? rezept.zahl("rauheit.wasser.unter") : r.wasserUnter;
        r.sandUeber = rezept.hat("rauheit.sand.ueber") ? rezept.zahl("rauheit.sand.ueber") : r.sandUeber;
        r.deichBreite = rezept.hat("rauheit.deich.breite") ? rezept.zahl("rauheit.deich.breite") : r.deichBreite;
        r.bauen(ziel.resolve("rauheit").toFile());
        log(f("  %d Kacheln 1 m (davon %d mit DTK5), %d Kacheln 10 m (über der Stufe %s)", r.kacheln1, r.kachelnDtk5, r.kacheln10, rezept.text("grund.stufe", "10m")));
        log("  Fläche je Klasse (1 m / 10 m): " + r.bericht());
        log(f("  Schritt rauheit in %.1f s.", (System.nanoTime() - t0) / 1e9));
    }

    // ------------------------------------------------------------------ P3d: Gitterkette

    /** G0 aus dem Rezept: geographisch, {@code g0.gebiet} (West Süd Ost Nord in Grad), {@code g0.zelle} (Teiler je Grad, Länge und Breite). */
    Gitter g0() {
        double[] g = rezept.zahlen("g0.gebiet", 4), t = rezept.zahlen("g0.zelle", 2);
        return Gitter.geografisch("G0", g[0], g[1], 1 / t[0], 1 / t[1], (int) Math.round((g[2] - g[0]) * t[0]), (int) Math.round((g[3] - g[1]) * t[1]));
    }

    /** G1: UTM-Hülle von {@code g1.gebiet} (Grad) auf {@code g1.zelle} m. */
    Gitter g1() {
        double[] g = rezept.zahlen("g1.gebiet", 4);
        double z = rezept.zahl("g1.zelle");
        return Gitter.utmHuelle("G1", 32, g[0], g[1], g[2], g[3], z, z);
    }

    Gitter g2() {
        double[] g = rezept.zahlen("g2.gebiet", 4);
        return Gitter.utmBereich("G2", 32, g[0], g[1], g[2], g[3], rezept.zahl("g2.zelle"));
    }

    Gitter g4() {
        double[] s = rezept.zahlen("g4.suedwest", 2);
        int n = (int) rezept.zahl("g4.zellen");
        return Gitter.utm("G4", 32, s[0], s[1], rezept.zahl("g4.zelle"), n, n);
    }

    /** Rauheit von G0 und G1: mit {@code rauheit.grob.cd} n aus der Tiefe der Höhen {@code h}, sonst {@code fest}. */
    com.dan.core.gitter.Subgrid.Feingelaende rauheitGrob(com.dan.core.gitter.Subgrid.Feingelaende fest, com.dan.core.gitter.Subgrid.Feingelaende h) {
        return rezept.hat("rauheit.grob.cd") ? Backen.rauheitAusTiefe(h, rezept.zahl("rauheit.grob.cd"), tiefeGrob()) : fest;
    }

    /** Kleinste Tiefe für {@code rauheit.grob.cd} (m) [G]: Watt und Land wie 1 m Tiefe. */
    double tiefeGrob() { return rezept.hat("rauheit.grob.tiefe") ? rezept.zahl("rauheit.grob.tiefe") : 1.0; }

    int fein(String g) { return g.equals("g0") && !rezept.hat("g0.fein") ? 10 : (int) rezept.zahl(g + ".fein"); }

    /** Prüft die Schachtelung der Kette. */
    void schachtelung(Gitter g1, Gitter g2, Gitter g3, Gitter g4) {
        int a = g2.passtIn(g1), b = g3.passtIn(g2), c = g4.passtIn(g3);
        if (a == 0 || b == 0 || c == 0) throw new IllegalArgumentException(f("Gitterkette nicht geschachtelt: G2 in G1 %d, G3 in G2 %d, G4 in G3 %d", a, b, c));
        log(f("  Kette: %s · %s · %s · %s; Verhältnisse %d, %d, %d", g1, g2, g3, g4, a, b, c));
    }

    /** Land nach Natural Earth je 100-m-Pixel von G1 (Zeilen von Nord). */
    byte[] land100(NaturalEarth ne, Gitter g1, int fein) {
        return ne.maskeUtm(g1.zone, g1.x0, g1.y1(), g1.dx / fein, g1.nx * fein, g1.ny * fein);
    }

    List<Path> pfade(String schluessel) {
        List<Path> l = new ArrayList<>();
        for (String x : rezept.text(schluessel).split(";")) if (!x.isBlank()) l.add(pfad(x.strip()));
        return l;
    }

    /** Schritt grob: die Stufe 100 m über der Hülle von G1 (10.4, Schritte 4 und 7a). */
    public Grob grob() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt grob");
        Gitter g1 = g1();
        int f1 = fein("g1");
        log("  " + g1 + ", Feinpixel " + g1.dx / f1 + " m");
        File st = ziel.resolve("gelaende").toFile();
        File zehnOrdner = new File(st, rezept.text("grund.stufe", "10m"));
        Gelaende.Stufe zehn = new File(zehnOrdner, Gelaende.BESCHREIBUNG).isFile() ? Gelaende.Stufe.lies(zehnOrdner) : null;
        NaturalEarth ne = NaturalEarth.lies(pfad(rezept.text("natural_earth")));
        byte[] land = land100(ne, g1, f1);
        long nl = 0;
        for (byte b : land) nl += b;
        log(f("  Natural Earth: %d Länder; Land in der Hülle %.0f km²", ne.laender.size(), nl * Math.pow(g1.dx / f1, 2) / 1e6));
        Grob.Grad emod = Grob.Grad.lies(pfade("grob.emodnet")), geb = Grob.Grad.lies(List.of(pfad(rezept.text("grob.gebco"))));
        log(f("  EMODnet: %d Kacheln, %d × %d Pixel zu %.6f°, %.2f–%.2f° O, %.2f–%.2f° N; GEBCO: %d × %d Pixel zu %.6f°", emod.dateien.size(), emod.w, emod.h, emod.d,
                emod.west, emod.west + emod.w * emod.d, emod.nord - emod.h * emod.d, emod.nord, geb.w, geb.h, geb.d));
        Grob g = new Grob();
        if (rezept.hat("grob.unter")) g.unter = rezept.zahl("grob.unter");
        if (rezept.hat("grob.feld")) g.feld = rezept.zahl("grob.feld");
        if (rezept.hat("grob.punkte")) g.punkte = (int) rezept.zahl("grob.punkte");
        File aus = new File(st, rezept.text("grob.stufe", "100m"));
        try (GeoTiff tw = GeoTiff.oeffne(quelle(rezept.text("grund.datei")))) {
            g.bauen(g1, f1, zehn, tw, emod, geb, land, aus, "Grob 100 m über G1: TrilaWatt als Mittel, sonst EMODnet bzw. GEBCO mit Korrektur nach NHN (Werkbuch HalligHooge Teil A, 10.4, Schritte 4 und 7a)");
        }
        log("  " + g);
        log(f("  Korrektur %s; an den Feldern %.2f bis %.2f m", g.emodnet, g.spanneE[0], g.spanneE[1]));
        log(f("  Korrektur %s; an den Feldern %.2f bis %.2f m", g.gebco, g.spanneG[0], g.spanneG[1]));
        Files.writeString(new File(aus, "korrektur.txt").toPath(), "# Korrektur nach NHN (Werkbuch 10.4, Schritt 7a): Quelle, e0, n0, Grad, Felder, a0 … a5; k = a0 + a1·u + a2·v + a3·u² + a4·u·v + a5·v², u = (E − e0)/100 km, v = (N − n0)/100 km\n"
                + "# " + g.emodnet + "\n# " + g.gebco + "\n" + g.emodnet.zeile() + "\n" + g.gebco.zeile() + "\n", StandardCharsets.UTF_8);
        log(f("  Schritt grob in %.1f s.", (System.nanoTime() - t0) / 1e9));
        return g;
    }

    /** Schritt schliessen: die Maske für Land hinter Deichen (10.4, Schritt 7b). */
    public Schliessen schliessen() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt schliessen");
        Gitter g1 = g1(), g2 = g2();
        int f1 = fein("g1");
        File st = ziel.resolve("gelaende").toFile();
        File zehnOrdner = new File(st, rezept.text("grund.stufe", "10m"));
        Gelaende.Stufe zehn = new File(zehnOrdner, Gelaende.BESCHREIBUNG).isFile() ? Gelaende.Stufe.lies(zehnOrdner) : null;
        byte[] land = land100(NaturalEarth.lies(pfad(rezept.text("natural_earth"))), g1, f1);
        Schliessen s = new Schliessen();
        if (rezept.hat("schliessen.hoehe")) s.hoehe = rezept.zahl("schliessen.hoehe");
        if (rezept.hat("schliessen.luecke")) s.luecke = rezept.zahl("schliessen.luecke");
        File zo = ziel.resolve("geschlossen").toFile();
        try (GeoTiff tw = GeoTiff.oeffne(quelle(rezept.text("grund.datei")))) {
            s.bauen(g1, f1, g2, zehn, tw, land);
            log("  " + s);
            s.schreibe(new File(zo, "10m"));
            float[] a = s.anteil(g1, f1, land);
            int W = g1.nx * f1, H = g1.ny * f1, kz = 1000, ko = (W + kz - 1) / kz, kn = (H + kz - 1) / kz;
            double z = g1.dx / f1;
            File o100 = new File(zo, "100m");
            Stapel.leere(o100);
            Gelaende.Stufe sa = Gelaende.Stufe.anlegen(o100, z, kz, g1.x0, g1.y1() - kn * kz * z, ko, kn, "Anteil geschlossen je 100-m-Pixel von G1 (Werkbuch HalligHooge Teil A, 10.4, Schritt 7b)");
            double flaeche = 0;
            for (float x : a) if (x >= 0.5f) flaeche += z * z;
            for (int ty = 0; ty < kn; ty++)
                for (int tx = 0; tx < ko; tx++) {
                    float[] k = new float[kz * kz];
                    java.util.Arrays.fill(k, Float.NaN);
                    for (int j = 0; j < kz && ty * kz + j < H; j++)
                        for (int i = 0; i < kz && tx * kz + i < W; i++) {
                            float x = a[(ty * kz + j) * W + tx * kz + i];
                            if (x > 0) k[j * kz + i] = x;
                        }
                    sa.schreibe(tx, ty, k);
                }
            log(f("  G1: geschlossen (Anteil ab ½) %.0f km² von %.0f km²", flaeche / 1e6, g1.flaeche() / 1e6));
            for (String k : rezept.alle().keySet()) {
                if (!k.startsWith("zu.") && !k.startsWith("offen.")) continue;
                double[] p = rezept.zahlen(k, 2);
                boolean zu = s.zu((int) Math.floor(tw.spalte(p[0]) + 0.5), (int) Math.floor(tw.zeile(p[1]) + 0.5));
                log(f("  Sonde %s bei O %.0f N %.0f: %s", k, p[0], p[1], zu ? "geschlossen" : "offen"));
            }
            Files.writeString(new File(zo, "schliessen.txt").toPath(), "# Werkbuch 10.4, Schritt 7b\n" + s + "\n", StandardCharsets.UTF_8);
        }
        log(f("  Schritt schliessen in %.1f s.", (System.nanoTime() - t0) / 1e9));
        return s;
    }

    /** Schritt backen: G0–G3, G4 zur Probe, {@code gebiet.txt} (10.4, Schritte 7 und 8). */
    public void backen() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt backen");
        Gitter g0 = g0(), g1 = g1(), g2 = g2(), g3 = g3(), g4 = g4();
        schachtelung(g1, g2, g3, g4);
        Gelaende gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
        log("  Stapel: " + gel.beschreibe());
        double zuHoehe = rezept.hat("schliessen.auf") ? rezept.zahl("schliessen.auf") : 10.0;
        File zo = ziel.resolve("geschlossen").toFile();
        Gelaende.Stufe anteil = Gelaende.Stufe.lies(new File(zo, "100m")), maske = Gelaende.Stufe.lies(new File(zo, "10m"));
        Gelaende.Stufe mindest = Gelaende.Stufe.lies(ziel.resolve("G3").resolve("mindesthoehe").toFile());
        File ro = ziel.resolve("rauheit").toFile();
        Gelaende.Stufe r1 = Gelaende.Stufe.lies(new File(ro, "1m")), r10 = Gelaende.Stufe.lies(new File(ro, "10m"));
        double[] manning = manning(new File(ro, "klassen.txt"));
        // G0 und G1 haben keine Rauheitsklassen: ein Manning-n für alle Pixel (seit P5d aus dem Rezept, sonst Subgrid.MANNING),
        // seit P5e wahlweise n aus der Tiefe für einen festen Beiwert (rauheit.grob.cd)
        double nGrob = Backen.manningGrob(rezept);
        com.dan.core.gitter.Subgrid.Feingelaende rGrob = Backen.rauheit(nGrob);
        if (rezept.hat("rauheit.grob.cd"))
            log(f("  Rauheit G0 und G1: Manning-n aus der Tiefe für C_d = %.4f (rauheit.grob.cd), Tiefe ab %.1f m (rauheit.grob.tiefe)", rezept.zahl("rauheit.grob.cd"), tiefeGrob()));
        else log(f("  Rauheit G0 und G1: Manning-n %.3f für alle Pixel%s", nGrob, rezept.hat("rauheit.grob") ? " (rauheit.grob)" : " (ohne rauheit.grob im Rezept)"));
        StringBuilder pw = new StringBuilder();
        // G0
        NaturalEarth ne = NaturalEarth.lies(pfad(rezept.text("natural_earth")));
        Grob.Grad geb = Grob.Grad.lies(List.of(pfad(rezept.text("grob.gebco"))));
        int t = (int) (rezept.hat("g0.teilung") ? rezept.zahl("g0.teilung") : 3);
        double d = geb.d / t;
        byte[] landTeile = ne.maskeGrad(g0.x0, g0.y1(), d, (int) Math.rint(g0.nx * g0.dx / d), (int) Math.rint(g0.ny * g0.dy / d));
        Backen.G0 b0 = Backen.g0(g0, geb, t, landTeile, g1, Gelaende.Stufe.lies(ziel.resolve("gelaende").resolve(rezept.text("grob.stufe", "100m")).toFile()));
        File o0 = ziel.resolve("G0").toFile();
        Gebiet.schreibeF32(new File(o0, "ground.z.f32"), b0.groundZ());
        Gebiet.schreibeF32(new File(o0, "nass.f32"), b0.nass());
        long nass = 0, teil = 0;
        for (float x : b0.nass()) { if (x >= 1) nass++; else if (x > 0) teil++; }
        log(f("  %s: %d Zellen ganz nass, %d teilweise, %d an Land; %d Teile aus der Stufe 100 m; %.1f s", g0, nass, teil, g0.zellen() - nass - teil, b0.teileAusStapel(), b0.sekunden()));
        pw.append(String.format(Locale.ROOT, "zeit G0 %.1f s\n", b0.sekunden()));
        // G0 mit Subgrid (seit P5): ground.z und nass bleiben wie oben, die Packung kommt dazu
        Gelaende.Stufe hundert = Gelaende.Stufe.lies(ziel.resolve("gelaende").resolve(rezept.text("grob.stufe", "100m")).toFile());
        com.dan.core.gitter.Subgrid.Feingelaende h0 = Backen.g0Fein(g0, fein("g0"), geb, t, landTeile, g1, hundert, anteil, zuHoehe);
        Backen.Gebacken s0 = Backen.backe(g0, fein("g0"), h0, rauheitGrob(rGrob, Backen.g0Fein(g0, fein("g0"), geb, t, landTeile, g1, hundert, anteil, zuHoehe)), null, null);
        Gebiet.schreibePackung(new File(o0, "subgrid.bin"), s0.packung(), g0, fein("g0"));
        log(f("  %s, fein %d (Subgrid): %.1f s; Packung %.0f MB", g0, fein("g0"), s0.sekunden(), s0.packung().bytes() / 1e6));
        pw.append(String.format(Locale.ROOT, "zeit G0 Subgrid %.1f s\n", s0.sekunden()));
        // G1–G3
        Object[][] plan = {{g1, fein("g1"), new Backen.Zusatz(anteil, null, null, zuHoehe),
                rauheitGrob(rGrob, Backen.hoehen(gel, g1, fein("g1"), new Backen.Zusatz(anteil, null, null, zuHoehe), null, null))},
                {g2, fein("g2"), new Backen.Zusatz(null, maske, null, zuHoehe), Backen.rauheit(null, r10, manning, g2, fein("g2"))},
                {g3, fein("g3"), new Backen.Zusatz(null, null, mindest, zuHoehe), Backen.rauheit(r1, r10, manning, g3, fein("g3"))}};
        for (Object[] q : plan) {
            Gitter g = (Gitter) q[0];
            int fein = (Integer) q[1];
            java.util.concurrent.atomic.AtomicLong zu = new java.util.concurrent.atomic.AtomicLong(), hoch = new java.util.concurrent.atomic.AtomicLong();
            Backen.Gebacken b = Backen.backe(g, fein, Backen.hoehen(gel, g, fein, (Backen.Zusatz) q[2], zu, hoch), (com.dan.core.gitter.Subgrid.Feingelaende) q[3], zu, hoch);
            Backen.schreibe(b, ziel.resolve(g.name).toFile());
            double px = Math.pow(g.dx / fein, 2);
            log(f("  %s, fein %d: %.1f s; geschlossen %.1f km², gehoben %.3f km²; Packung %.0f MB", g, fein, b.sekunden(), b.geschlossen() * px / 1e6, b.gehoben() * px / 1e6, b.packung().bytes() / 1e6));
            pw.append(String.format(Locale.ROOT, "zeit %s %.1f s\ngeschlossen %s %.4f km2\ngehoben %s %.4f km2\n", g.name, b.sekunden(), g.name, b.geschlossen() * px / 1e6, g.name, b.gehoben() * px / 1e6));
        }
        // G4 zur Probe
        java.util.concurrent.atomic.AtomicLong h4 = new java.util.concurrent.atomic.AtomicLong();
        Backen.Gebacken b4 = Backen.backe(g4, fein("g4"), Backen.hoehen(gel, g4, fein("g4"), new Backen.Zusatz(null, null, mindest, zuHoehe), null, h4),
                Backen.rauheit(r1, r10, manning, g4, fein("g4")), null, h4);
        log(f("  %s, fein %d (zur Probe, nicht abgelegt): %.2f s", g4, fein("g4"), b4.sekunden()));
        pw.append(String.format(Locale.ROOT, "zeit G4 %.2f s\n", b4.sekunden()));
        // gebiet.txt
        Inventar inv = new Inventar();
        long ti = System.nanoTime();
        quellen(inv);
        for (String n : new String[] {"G0/ground.z.f32", "G0/nass.f32", "G0/subgrid.bin", "G1/ground.z.f32", "G1/subgrid.bin", "G2/ground.z.f32", "G2/subgrid.bin", "G3/ground.z.f32", "G3/subgrid.bin", "G3/bauwerke.txt"})
            inv.datei(ziel, ziel.resolve(n));
        log(f("  Inventar: %d Quellen, %d Ablagen, SHA-256 in %.1f s", inv.quellen.size(), inv.dateien.size(), (System.nanoTime() - ti) / 1e9));
        StringBuilder b = new StringBuilder();
        b.append("# Gebiet ").append(rezept.text("name", "?")).append(" (Werkbuch HalligHooge Teil A, 10.4, Schritt 8)\n");
        b.append("# Aufbereitung ").append(VERSION).append(", ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n#\n# Rezept ").append(rezept.datei.getFileName()).append('\n');
        for (Map.Entry<String, String> e : rezept.alle().entrySet()) b.append("rezept\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        b.append("#\n# Gitterkette: gitter Name Lage Zone x0 y0 dx dy nx ny fein (Südwestecke; G4 wird beim Setzen gebacken)\n");
        b.append(Gebiet.zeile(g0, fein("g0"))).append('\n').append(Gebiet.zeile(g1, fein("g1"))).append('\n').append(Gebiet.zeile(g2, fein("g2"))).append('\n').append(Gebiet.zeile(g3, fein("g3"))).append('\n');
        b.append("#\n# Quellen: Gruppe, Pfad (zu daten), Byte, SHA-256, Stand, Lizenz\n");
        for (Inventar.Quelle q : inv.quellen) b.append(q.zeile()).append('\n');
        b.append("#\n# Ablage: Pfad, Byte, SHA-256\n");
        for (Inventar.Datei q : inv.dateien) b.append(q.zeile()).append('\n');
        b.append("#\n# Prüfwerte\n");
        Path kor = ziel.resolve("gelaende").resolve(rezept.text("grob.stufe", "100m")).resolve("korrektur.txt");
        for (String z : Files.readAllLines(kor, StandardCharsets.UTF_8)) if (z.startsWith("korrektur ")) b.append(z).append('\n');
        for (String z : Files.readAllLines(zo.toPath().resolve("schliessen.txt"), StandardCharsets.UTF_8)) if (!z.startsWith("#")) b.append("schliessen\t").append(z).append('\n');
        b.append(pw);
        Files.writeString(ziel.resolve("gebiet.txt"), b.toString(), StandardCharsets.UTF_8);
        log(f("  gebiet.txt geschrieben; Schritt backen in %.1f s.", (System.nanoTime() - t0) / 1e9));
    }

    /** Manning-n je Klasse aus {@code klassen.txt} (Index = Klasse). */
    static double[] manning(File klassen) throws IOException { return Gelaende.manning(klassen); }

    /** Schritt vorgabe (P4d): die Vorgabe der Tide am Rand ({@code vorgabe.gitter}) und an den Pegeln, {@code vorgabe.txt}. */
    public void vorgabe() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt vorgabe");
        Gebiet ge = Gebiet.lies(ziel.toFile());
        Gitter g = ge.gitter(rezept.text("vorgabe.gitter", "G2"));
        String[] tide = rezept.text("vorgabe.tide", "M2 28.9841042").split("\\s+");
        Path pa = pfad(rezept.text("vorgabe.amplitude")), pp = pfad(rezept.text("vorgabe.phase")), pm = pfad(rezept.text("vorgabe.mittel"));
        Vorgabe v = new Vorgabe(pa, pp, pm, rezept.hat("vorgabe.umkreis") ? rezept.zahl("vorgabe.umkreis") : 2000, Gelaende.ordner(ziel.resolve("gelaende").toFile()));
        log("  Raster: " + v.a.t);
        Vorgabe.Ergebnis e = v.rand(g);
        String[] seite = {"West", "Ost", "Süd", "Nord"};
        double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
        for (Vorgabe.Wert w : e.rand()) {
            if (!w.da()) continue;
            double[] x = {w.mittel(), w.amplitude(), w.phase()};
            for (int q = 0; q < 3; q++) { lo[q] = Math.min(lo[q], x[q]); hi[q] = Math.max(hi[q], x[q]); }
        }
        for (int s = 0; s < 4; s++) log(f("  %s: %d Kanten mit Pixel im Umkreis, %d übernommen, %d ohne", seite[s], e.direkt()[s], e.uebernommen()[s], e.ohne()[s]));
        log(f("  %d Pixel mit Werten verworfen: Gelände über dem Niedrigwasser (Mittel − Amplitude)", v.trocken));
        log(f("  Rand von %s: Mittelwasser %.3f … %.3f m, Amplitude %.3f … %.3f m, Phase %.1f … %.1f°", g.name, lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]));
        Map<String, double[]> orte = Vorgabe.pegel(rezept);
        Map<String, Vorgabe.Wert> pw = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> p : orte.entrySet()) {
            Vorgabe.Wert w = v.am(p.getValue()[0], p.getValue()[1]);
            pw.put(p.getKey(), w);
            log(f("  Pegel %s (O %.0f, N %.0f): Mittelwasser %.3f m, Amplitude %.3f m, Phase %.1f°, Pixel %.0f m entfernt", p.getKey(), p.getValue()[0], p.getValue()[1],
                    w.mittel(), w.amplitude(), w.phase(), w.abstand()));
        }
        Inventar inv = new Inventar();
        inv.quelle("vorgabe", daten, pa, rezept);
        inv.quelle("vorgabe", daten, pp, rezept);
        inv.quelle("vorgabe", daten, pm, rezept);
        StringBuilder b = new StringBuilder();
        b.append("# Vorgabe der Tide am Rand (Werkbuch HalligHooge Teil A, 12.5, Plan P4d), bis P5 tide.bc liefert\n");
        b.append("# Aufbereitung ").append(VERSION).append(", ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                .append(", Rezept ").append(rezept.datei.getFileName()).append('\n');
        b.append(f("# eta = Mittelwasser + Amplitude cos(omega t - Phase); nächstes Pixel mit allen drei Werten und Gelände unter Mittel - Amplitude im Umkreis %.0f m, sonst die nächste Kante derselben Seite\n", v.umkreis));
        for (Inventar.Quelle q : inv.quellen) b.append("quelle ").append(String.join(" ", q.pfad(), Long.toString(q.bytes()), q.sha(), q.stand().replace(' ', '_'))).append('\n');
        b.append("gitter ").append(g.name).append('\n');
        b.append("tide ").append(tide[0]).append(' ').append(tide.length > 1 ? tide[1] : "28.9841042").append("   # Name, Winkelgeschwindigkeit (°/h)\n");
        for (String z : Vorgabe.zeilen(g, e, orte, pw)) b.append(z).append('\n');
        Path aus = ziel.resolve("vorgabe.txt"), tmp = ziel.resolve("vorgabe.txt.neu");
        Files.writeString(tmp, b, StandardCharsets.UTF_8);
        Files.move(tmp, aus, StandardCopyOption.REPLACE_EXISTING);
        log(f("  %s geschrieben; %.1f s", aus, (System.nanoTime() - t0) / 1e9));
    }

    // ------------------------------------------------------------------ P5b: Tide, Pegel, Antrieb

    /** Schritt eot20: EOT20 auf G0 ausgeschnitten, {@code tide/eot20.txt} und {@code .f32} (12.6, P5b). */
    public Tidenfeld eot20() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt eot20");
        double[] g = rezept.zahlen("g0.gebiet", 4);
        double rand = rezept.hat("eot20.rand") ? rezept.zahl("eot20.rand") : 0.5;
        int umkreis = rezept.hat("eot20.umkreis") ? (int) rezept.zahl("eot20.umkreis") : 2;
        Path q = pfad(rezept.text("eot20.datei"));
        Tidenfeld f = Eot20.lies(q, g[0] - rand, g[1] - rand, g[2] + rand, g[3] + rand, this::log);
        log("  Ausschnitt: " + f.raster + ", Tiden " + f.tiden);
        for (double[] m : Eot20.minima(f, Partialtide.M2, new double[] {-4, 50.5, 12, 62}, 0.15, 4))
            log(f("  M2-Minimum in der Nordsee (wie Befund 2): %.3f° N, %.3f° O, %.1f cm, Umlauf der Phase %+d", m[1], m[0], 100 * m[2], (int) m[3]));
        if (Files.isRegularFile(ziel.resolve("gebiet.txt"))) {
            Gebiet ge = Gebiet.lies(ziel.toFile());
            int[][] r = randG0(ge, f, umkreis);
            String[] seite = {"West", "Ost", "Süd", "Nord"};
            for (int s = 0; s < 4; s++)
                log(f("  Rand von G0, %s: %d Kanten an Zellen mit Wasser; EOT20 bilinear %d, nächster Punkt (≤ %d) %d, ohne %d", seite[s], r[s][0], r[s][1], umkreis, r[s][2], r[s][3]));
        }
        Inventar inv = new Inventar();
        inv.quelle("eot20", daten, q, rezept);
        StringBuilder k = new StringBuilder();
        k.append("# Aufbereitung ").append(VERSION).append(", ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(", Rezept ").append(rezept.datei.getFileName()).append('\n');
        k.append("# Quelle: Gruppe, Pfad (zu daten), Byte, SHA-256, Stand, Lizenz\n");
        for (Inventar.Quelle x : inv.quellen) k.append(x.zeile()).append('\n');
        k.append(f("# Rand %.2f° über G0 hinaus; am Rand von G0 bilinear über nasse Punkte, sonst der nächste nasse Punkt im Umkreis von %d Punkten\n", rand, umkreis));
        f.schreibe(ziel.toFile(), k.toString());
        Path o = ziel.resolve(Tidenfeld.ORDNER);
        ablagen(o.resolve(Tidenfeld.TEXT), o.resolve(Tidenfeld.DATEN));
        log(f("  %s geschrieben (%.0f MB); Schritt eot20 in %.1f s.", o.resolve(Tidenfeld.DATEN), Files.size(o.resolve(Tidenfeld.DATEN)) / 1e6, (System.nanoTime() - t0) / 1e9));
        return f;
    }

    /**
     * Abdeckung des Rands von G0 durch das Tidenfeld: je Seite (West, Ost, Süd, Nord) {Kanten an Zellen mit Wasser,
     * davon bilinear, nächster Punkt, ohne}; gefragt wird an der Mitte der Geisterzelle (eine halbe Zelle vor dem Rand).
     */
    static int[][] randG0(Gebiet ge, Tidenfeld f, int umkreis) throws IOException {
        Gitter g = ge.gitter("G0");
        float[] nass = ge.nass("G0");
        int[][] r = new int[4][4];
        for (int s = 0; s < 4; s++) {
            int n = s < 2 ? g.ny : g.nx;
            for (int k = 0; k < n; k++) {
                int i = s == 0 ? 0 : s == 1 ? g.nx - 1 : k, j = s < 2 ? k : (s == 2 ? 0 : g.ny - 1);
                if (!(nass[g.index(i, j)] > 0)) continue;
                double x = s == 0 ? g.x0 - 0.5 * g.dx : s == 1 ? g.x1() + 0.5 * g.dx : g.x(i), y = s < 2 ? g.y(j) : (s == 2 ? g.y0 - 0.5 * g.dy : g.y1() + 0.5 * g.dy);
                double[] ll = g.grad(x, y);
                r[s][0]++;
                Gradraster.Gewichte w = f.gewichte(ll[0], ll[1], umkreis);
                if (w == null) r[s][3]++;
                else if (w.naechster()) r[s][2]++;
                else r[s][1]++;
            }
        }
        return r;
    }

    /** Hängt die Ablagen mit Größe und SHA-256 an eine Beschreibung an (Zeilen {@code datei} wie in {@code gebiet.txt}). */
    void ablagen(Path text, Path... dateien) throws IOException {
        Inventar inv = new Inventar();
        for (Path p : dateien) inv.datei(ziel, p);
        StringBuilder b = new StringBuilder("# Ablage: Pfad, Byte, SHA-256\n");
        for (Inventar.Datei d : inv.dateien) b.append(d.zeile()).append('\n');
        Files.writeString(text, b, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    /** Schritt pegel: PEGELONLINE als Maßstab, {@code pegel/pegel.txt} und je Pegel die Reihe (12.6, P5b). */
    public List<Pegel> pegel() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt pegel");
        Path o = pfad(rezept.text("pegelonline.ordner", "gezeiten\\pegelonline"));
        Pegelonline po = new Pegelonline();
        if (rezept.hat("pegelonline.grenzen")) {
            double[] g = rezept.zahlen("pegelonline.grenzen", 2);
            po.unten = g[0];
            po.oben = g[1];
        }
        if (rezept.hat("pegelonline.spitze")) po.spitze = rezept.zahl("pegelonline.spitze");
        if (rezept.hat("pegelonline.fenster")) po.fenster = (int) rezept.zahl("pegelonline.fenster");
        List<String> auswahl = new ArrayList<>();
        if (rezept.hat("pegelonline.auswahl")) for (String x : rezept.text("pegelonline.auswahl").split(";")) if (!x.isBlank()) auswahl.add(x.strip());
        log(f("  %s; Grenzen %.1f … %.1f m NHN, Spitze %.2f m gegen den Median über ±%d min; Kandidaten der Analyse %s (Rayleigh 1)", o, po.unten, po.oben, po.spitze, po.fenster, po.tiden));
        Tidenfeld eot = Tidenfeld.da(ziel.toFile()) ? Tidenfeld.lies(ziel.toFile()) : null;
        Gebiet ge = Files.isRegularFile(ziel.resolve("gebiet.txt")) ? Gebiet.lies(ziel.toFile()) : null;
        Inventar inv = new Inventar();
        List<Pegel> alle = new ArrayList<>();
        for (Path p : Pegelonline.ordner(o)) {
            if (!auswahl.isEmpty() && !auswahl.contains(p.getFileName().toString())) continue;
            try (var w = Files.list(p)) {
                if (w.noneMatch(d -> d.getFileName().toString().matches("W_.*\\.json"))) {
                    log("  " + p.getFileName() + ": übergangen (keine Reihe W_*.json)");
                    continue;
                }
            }
            Pegelonline.Gelesen g = po.lies(p);
            Pegel x = g.pegel();
            alle.add(x);
            inv.quelle("pegelonline", daten, p.resolve("station.json"), rezept);
            try (var w = Files.list(p)) {
                for (Path d : w.filter(d -> d.getFileName().toString().matches("W_.*\\.json")).sorted().toList()) inv.quelle("pegelonline", daten, d, rezept);
            }
            Harmonik.Tide m2 = x.analyse.tide("M2"), s2 = x.analyse.tide("S2");
            log(f("  %s (%s, %.5f° O, %.5f° N, Pegelnull %.3f m NHN): %d Werte zu %d s, %s bis %s; %d Dateien, %d doppelt, %d Lücken; verworfen %d außerhalb, %d Spitzen; höchster %s",
                    x.name, x.nummer, x.laenge, x.breite, x.pegelnull, x.werte.length, x.schritt, x.start, x.ende(), g.dateien(), g.doppelt(), x.luecken, g.grenzen(), g.spitzen(), g.hoechster()));
            log(f("    Analyse: Mittel %.3f m NHN, M2 %.3f m %.1f°, S2 %.3f m %.1f°, Rest %.3f m RMS; %d Tiden, ausgelassen %s; MThw %.2f, MTnw %.2f m NHN (%s–%s)", x.analyse.mittel(),
                    m2.amplitude(), m2.phaseGrad(), s2 == null ? Double.NaN : s2.amplitude(), s2 == null ? Double.NaN : s2.phaseGrad(), x.analyse.restRms(), x.analyse.tiden().size(),
                    x.analyse.ausgelassen(), x.kennwert("MThw"), x.kennwert("MTnw"), x.kennwerte.containsKey("MThw") ? x.kennwerte.get("MThw").von() : "–",
                    x.kennwerte.containsKey("MThw") ? x.kennwerte.get("MThw").bis() : "–"));
            StringBuilder lage = new StringBuilder();
            if (eot != null) {
                double[][] k = eot.konstanten(x.laenge, x.breite, 2);
                if (k != null) {
                    double[] ap = Tidenfeld.amplitudePhase(k[eot.index(Partialtide.M2)]);
                    double dg = ap[1] - m2.phaseGrad();
                    dg -= 360 * Math.round(dg / 360);
                    lage.append(f("EOT20 M2 %.3f m %.1f° (%+.0f %%, %+.1f°)", ap[0], ap[1], 100 * (ap[0] / m2.amplitude() - 1), dg));
                }
            }
            if (ge != null) {
                List<Gebiet.Eintrag> kette = ge.kette();
                for (int q = kette.size() - 1; q >= 0; q--) {
                    Gitter gi = kette.get(q).gitter();
                    double[] pt = gi.punkt(x.laenge, x.breite);
                    if (!gi.enthaelt(pt[0], pt[1])) continue;
                    int c = gi.index(gi.i(pt[0]), gi.j(pt[1]));
                    lage.append(lage.length() > 0 ? "; " : "").append(f("in %s Zelle %d, %d (Grund %.2f m NHN)", gi.name, gi.i(pt[0]), gi.j(pt[1]), ge.groundZ(gi.name)[c]));
                    break;
                }
            }
            if (lage.length() > 0) log("    " + lage);
        }
        if (alle.isEmpty()) throw new IOException("pegel: keine Pegel in " + o);
        StringBuilder k = new StringBuilder();
        k.append("# Aufbereitung ").append(VERSION).append(", ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(", Rezept ").append(rezept.datei.getFileName()).append('\n');
        k.append(f("# Grenzen %.1f … %.1f m NHN, Spitze %.2f m gegen den Median über ±%d min; Analyse über die ganze Reihe, Rayleigh 1, Kandidaten %s\n", po.unten, po.oben, po.spitze, po.fenster, po.tiden));
        k.append("# Quelle: Gruppe, Pfad (zu daten), Byte, SHA-256, Stand, Lizenz\n");
        for (Inventar.Quelle x : inv.quellen) k.append(x.zeile()).append('\n');
        Pegel.schreibe(ziel.toFile(), alle, k.toString());
        Path po2 = ziel.resolve(Pegel.ORDNER);
        List<Path> ab = new ArrayList<>();
        for (Pegel x : alle) ab.add(po2.resolve(Pegel.datei(x.name)));
        ablagen(po2.resolve(Pegel.TEXT), ab.toArray(new Path[0]));
        log(f("  %d Pegel nach %s; Schritt pegel in %.1f s.", alle.size(), po2, (System.nanoTime() - t0) / 1e9));
        return alle;
    }

    /** Schritt antrieb: ERA5 aus GRIB, {@code antrieb/era5.txt} und {@code .f32} (12.6, P5b, F28). */
    /**
     * Wind an Stationen des DWD (P5e, F36 A): je {@code wind.station.<Name> = <Kennung>} im Rezept die ZIP mit der Kennung
     * aus {@code wind.ordner} ({@link Dwd}), abgelegt nach {@code wind/} ({@link com.dan.core.pruef.Windstation}). Die Höhe
     * des Gebers kommt aus den Metadaten des ZIP oder aus {@code wind.geber.<Name>}. Ohne {@code wind.ordner} im Rezept
     * gibt es nichts zu tun.
     */
    /**
     * Schritt luftbild (seit P6b, F41): Albedo je Rauheitsklasse und Struktur aus DOP20, geeicht gegen Sentinel-2
     * ({@link Luftbild}). Rezept: {@code luftbild.dop} (Ordner, Vorgabe {@code luftbild\dop20}), {@code luftbild.s2}
     * (Ordner mit {@code TCI.tif} und {@code SCL.tif}), {@code luftbild.ziel} (unter dem Ziel, Vorgabe {@code luftbild}).
     * Nicht in {@link #SCHRITTE}: er läuft nur, wenn er genannt ist.
     */
    public void luftbild() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt luftbild");
        Path dopOrdner = pfad(rezept.text("luftbild.dop", "luftbild\\dop20")), s2 = pfad(rezept.text("luftbild.s2", "luftbild\\sentinel2\\S2B_32UMF_20260501_0_L2A"));
        List<Path> dop = Luftbild.kacheln(dopOrdner);
        Gelaende.Stufe kl = Gelaende.Stufe.lies(ziel.resolve("rauheit").resolve("1m").toFile());
        Luftbild l = new Luftbild(dop, s2.resolve("TCI.tif"), s2.resolve("SCL.tif"), kl, Luftbild.namen(ziel.resolve("rauheit").resolve("klassen.txt")));
        if (rezept.hat("luftbild.saettigung")) l.saettigung = rezept.zahl("luftbild.saettigung");
        if (rezept.hat("luftbild.nass")) l.nassFlug = rezept.zahl("luftbild.nass");
        // F44 A: gegen die Bänder eichen, wenn sie da sind (Kartenwerkzeug: --quellen sentinel2 --baender B02,B03,B04)
        Path b4 = s2.resolve("B04.tif"), b3 = s2.resolve("B03.tif"), b2 = s2.resolve("B02.tif");
        if (Files.isRegularFile(b4) && Files.isRegularFile(b3) && Files.isRegularFile(b2)) {
            l.baender = new Path[] {b4, b3, b2};
            Path js = s2.resolve(s2.getFileName() + ".json");
            if (Files.isRegularFile(js)) {
                java.util.Map<String, Object> red = Json.objekt(Json.objekt(Json.objekt(Json.lies(js)).get("assets")).get("red"));
                java.util.Map<String, Object> rb = Json.objekt(Json.feld(red.get("raster:bands")).isEmpty() ? null : Json.feld(red.get("raster:bands")).get(0));
                if (!Double.isNaN(Json.zahl(rb.get("scale")))) l.massstab = Json.zahl(rb.get("scale"));
                if (!Double.isNaN(Json.zahl(rb.get("offset")))) l.versatz = Json.zahl(rb.get("offset"));
                // Element 84 legt die Werte ab Baseline 04.00 schon ohne den Versatz von 1 000 ab und sagt es hier; der Versatz
                // im JSON gilt dann nicht mehr (gemessen: Rot über Wasser und Watt 55–234, also 0,6–2,3 % Reflexion)
                Object ang = Json.objekt(Json.objekt(Json.lies(js)).get("properties")).get("earthsearch:boa_offset_applied");
                if (Boolean.TRUE.equals(ang)) l.versatz = 0;
            }
            log(f("  Eichung gegen die Bänder B04, B03, B02: Reflexion = Wert · %s + %s", l.massstab, l.versatz));
        } else log("  Bänder B02–B04 fehlen in " + s2 + ": Eichung gegen das TCI (Sättigung 25 % [S])");
        File aus = ziel.resolve(rezept.text("luftbild.ziel", "luftbild")).toFile();
        l.bauen(aus);
        log(f("  %d Kacheln DOP20 aus %s, Eichung gegen %s", l.kacheln, dopOrdner, s2.getFileName()));
        log("  " + l.bericht);
        for (int q = 1; q < l.albedo.length; q++)
            if (l.pixel[q] > 0) log(f("  Klasse %d %s: Albedo trocken %.3f / %.3f / %.3f, %d Pixel", q, l.namen[q], l.albedo[q][0], l.albedo[q][1], l.albedo[q][2], l.pixel[q]));
        log(f("  Schritt luftbild in %.1f s.", (System.nanoTime() - t0) / 1e9));
    }

    public void wind() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt wind");
        if (!rezept.hat("wind.ordner")) { log("  wind.ordner fehlt im Rezept, nichts zu tun"); return; }
        Path o = pfad(rezept.text("wind.ordner"));
        List<com.dan.core.pruef.Windstation> st = new ArrayList<>();
        for (Map.Entry<String, String> e : rezept.alle().entrySet()) {
            if (!e.getKey().startsWith("wind.station.")) continue;
            String name = e.getKey().substring("wind.station.".length()), k = e.getValue().strip();
            List<Path> zips = new ArrayList<>();
            if (Files.isDirectory(o)) try (var w = Files.list(o)) {
                zips.addAll(w.filter(p -> p.getFileName().toString().matches("(?i).*_" + k + "(_.*)?\\.zip$")).sorted().toList());
            }
            if (zips.isEmpty()) throw new IOException("wind: kein ZIP für " + name + " (" + k + ") in " + o
                    + " – zuerst laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen dwd_wind --ziel " + daten);
            if (zips.stream().noneMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("meta_daten")) && !rezept.hat("wind.geber." + name))
                throw new IOException("wind: Metadaten für " + name + " fehlen (Meta_Daten_zehn_min_ff_" + k + ".zip) – neu laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen dwd_wind --ziel " + daten);
            com.dan.core.pruef.Windstation w = Dwd.lies(name, k, zips, o.resolve("zehn_min_ff_Beschreibung_Stationen.txt"),
                    rezept.hat("wind.geber." + name) ? rezept.zahl("wind.geber." + name) : Double.NaN);
            int nan = 0;
            float max = 0;
            for (float v : w.ff) if (Float.isNaN(v)) nan++; else max = Math.max(max, v);
            log(f("  %s (%s): %.4f° N, %.4f° O, Station %.0f m, Geber %.1f m über Grund; %d Werte %s bis %s, %d fehlen, höchstens %.1f m/s",
                    name, k, w.breite, w.laenge, w.hoehe, w.geber, w.zeiten.length, java.time.Instant.ofEpochSecond(w.zeiten[0]),
                    java.time.Instant.ofEpochSecond(w.zeiten[w.zeiten.length - 1]), nan, max));
            st.add(w);
        }
        if (st.isEmpty()) throw new IOException("wind: keine Station im Rezept (wind.station.<Name> = <Kennung>)");
        com.dan.core.pruef.Windstation.schreibe(ziel.toFile(), st, "Quelle: DWD Climate Data Center, 10-Minuten-Werte Wind (recent und seit P13d historical), Lizenz CC BY 4.0\nRezept: " + rezept.datei);
        log(f("  wind/ geschrieben, %d Stationen; Schritt wind in %.1f s", st.size(), (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Zeitraum der Wetterdaten (P13a): {@code antrieb.zeitraum} (etwa {@code 201312} für Xaver, über die Befehlszeile
     * {@code --antrieb.zeitraum 201312}), sonst {@code antrieb.monat} aus dem Rezept, sonst keiner (alle Dateien). Die
     * Schritte antrieb, seegang und luft nehmen dann nur die GRIB-Dateien, deren Name mit dem Zeitraum beginnt.
     */
    String zeitraum() {
        if (rezept.hat("antrieb.zeitraum")) return rezept.text("antrieb.zeitraum").strip();
        return rezept.hat("antrieb.monat") ? rezept.text("antrieb.monat").strip() : null;
    }

    /** Ordner des Zeitraums unter {@code antrieb/}: null (also {@code antrieb/} selbst) für {@code antrieb.monat}, sonst der Zeitraum. */
    String zeitraumOrdner() {
        if (!rezept.hat("antrieb.zeitraum")) return null;
        String z = rezept.text("antrieb.zeitraum").strip();
        return rezept.hat("antrieb.monat") && rezept.text("antrieb.monat").strip().equals(z) ? null : z;
    }

    boolean imZeitraum(Path p) {
        String z = zeitraum();
        return z == null || p.getFileName().toString().startsWith(z);
    }

    public void antrieb() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt antrieb" + (zeitraum() != null ? ", Zeitraum " + zeitraum() + " nach " + Wetterfeld.ordner(zeitraumOrdner()) : ""));
        List<Path> dateien = new ArrayList<>();
        if (rezept.hat("antrieb.dateien")) dateien.addAll(pfade("antrieb.dateien"));
        else {
            Path o = pfad(rezept.text("antrieb.ordner", "wetter\\era5_cds"));
            if (Files.isDirectory(o)) try (var w = Files.list(o)) {
                dateien.addAll(w.filter(p -> p.getFileName().toString().matches("(?i).*\\.(grib|grb|grib1|grib2)$") && imZeitraum(p)).sorted().toList());
            }
            if (dateien.isEmpty()) throw new IOException("antrieb: keine GRIB-Dateien in " + o
                    + " – zuerst laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen era5_cds --monate 202610 --ziel " + daten);
            if (rezept.hat("antrieb.schub")) {
                Path os = pfad(rezept.text("antrieb.schub", "wetter\\era5_cds_schub"));
                List<Path> sd = new ArrayList<>();
                if (Files.isDirectory(os)) try (var w = Files.list(os)) {
                    sd.addAll(w.filter(p -> p.getFileName().toString().matches("(?i).*\\.(grib|grb|grib1|grib2)$") && imZeitraum(p)).sorted().toList());
                }
                if (sd.isEmpty()) throw new IOException("antrieb: keine GRIB-Dateien in " + os
                        + " – zuerst laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen era5_cds_schub --monate 202610 --ziel " + daten);
                dateien.addAll(sd);
            }
        }
        Era5.Daten d = Era5.lies(dateien);
        if (rezept.hat("antrieb.schub") && !d.schub) throw new IOException("antrieb: antrieb.schub steht im Rezept, aber keine Datei hat iews, inss, charnock");
        log(f("  %d Dateien, %d Nachrichten (Edition %s), %d anderer Größen übergangen, %d aus späteren Dateien ersetzt (davon %d mit anderen Werten); %s; %s bis %s, Schritt %d s, %d Zeiten",
                dateien.size(), d.nachrichten, d.editionen, d.andere, d.ersetzt, d.geaendert, d.raster, d.start(), d.ende(), d.schritt(), d.felder.size()));
        String l = Era5.luecken(d);
        if (!l.isEmpty()) throw new IOException("antrieb: " + l);
        if (Files.isRegularFile(ziel.resolve("gebiet.txt"))) {
            Gitter g = Gebiet.lies(ziel.toFile()).gitter("G0");
            double[] a = g.grad(g.x0 - 0.5 * g.dx, g.y0 - 0.5 * g.dy), b = g.grad(g.x1() + 0.5 * g.dx, g.y1() + 0.5 * g.dy);
            if (!d.raster.enthaelt(a[0], a[1]) || !d.raster.enthaelt(b[0], b[1]))
                throw new IOException(f("antrieb: ERA5 (%s) deckt die Geisterzellen von G0 (%.3f … %.3f° O, %.3f … %.3f° N) nicht", d.raster, a[0], b[0], a[1], b[1]));
            log(f("  deckt G0 mit den Geisterzellen (%.3f … %.3f° O, %.3f … %.3f° N)", a[0], b[0], a[1], b[1]));
        }
        // Kenngrößen: kleinster Druck, stärkster Wind, je mit Zeit und Ort; NaN
        Gradraster r = d.raster;
        double pmin = Double.POSITIVE_INFINITY, vmax = 0;
        long tp = 0, tv = 0;
        int kp = 0, kv = 0, nan = 0;
        for (var e : d.felder.entrySet()) {
            float[] u = e.getValue()[0], v = e.getValue()[1], p = e.getValue()[2];
            for (int k = 0; k < r.punkte(); k++) {
                if (Float.isNaN(u[k]) || Float.isNaN(v[k]) || Float.isNaN(p[k])) { nan++; continue; }
                if (p[k] < pmin) { pmin = p[k]; tp = e.getKey(); kp = k; }
                double w = Math.hypot(u[k], v[k]);
                if (w > vmax) { vmax = w; tv = e.getKey(); kv = k; }
            }
        }
        log(f("  kleinster Druck %.1f hPa am %s bei %.2f° N, %.2f° O; stärkster Wind %.1f m/s am %s bei %.2f° N, %.2f° O; %d Werte NaN", pmin / 100, Instant.ofEpochSecond(tp),
                r.breite(kp / r.nx()), r.laenge(kp % r.nx()), vmax, Instant.ofEpochSecond(tv), r.breite(kv / r.nx()), r.laenge(kv % r.nx()), nan));
        if (nan > 0) throw new IOException("antrieb: " + nan + " Werte NaN");
        if (d.schub) {
            // Windschub (P5e, F34): größte Schubspannung, Charnock über See, über Land gefüllt
            int ix = Era5.ALLE.indexOf("iews"), iy = Era5.ALLE.indexOf("inss"), ia = Era5.ALLE.indexOf("charnock");
            double tmax = 0, amin = Double.POSITIVE_INFINITY, amax = 0, asum = 0;
            long tt = 0, an = 0;
            int kt = 0, tnan = 0;
            for (var e : d.felder.entrySet()) {
                float[] x = e.getValue()[ix], y = e.getValue()[iy], al = e.getValue()[ia];
                for (int q = 0; q < r.punkte(); q++) {
                    if (Float.isNaN(x[q]) || Float.isNaN(y[q])) { tnan++; continue; }
                    double t = Math.hypot(x[q], y[q]);
                    if (t > tmax) { tmax = t; tt = e.getKey(); kt = q; }
                    if (!Float.isNaN(al[q])) { amin = Math.min(amin, al[q]); amax = Math.max(amax, al[q]); asum += al[q]; an++; }
                }
            }
            if (tnan > 0) throw new IOException("antrieb: " + tnan + " Werte der Schubspannung NaN");
            long gefuellt = Era5.charnockFuellen(d);
            log(f("  Windschub: größte Schubspannung %.2f N/m² am %s bei %.2f° N, %.2f° O; Charnock über See %.4f … %.4f (Mittel %.4f, %d Werte), über Land aus den Nachbarn: %d Werte",
                    tmax, Instant.ofEpochSecond(tt), r.breite(kt / r.nx()), r.laenge(kt % r.nx()), amin, amax, asum / Math.max(1, an), an, gefuellt));
        }
        Inventar inv = new Inventar();
        for (Path p : dateien) inv.quelle("antrieb", daten, p, rezept);
        StringBuilder k = new StringBuilder();
        k.append("# Aufbereitung ").append(VERSION).append(", ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(", Rezept ").append(rezept.datei.getFileName()).append('\n');
        k.append("# Quelle: Gruppe, Pfad (zu daten), Byte, SHA-256, Stand, Lizenz\n");
        for (Inventar.Quelle x : inv.quellen) k.append(x.zeile()).append('\n');
        List<float[][]> z = new ArrayList<>(d.felder.values());
        Wetterfeld.schreibeIn(ziel.toFile(), zeitraumOrdner(), "era5", r, d.start(), d.schritt(), z.size(), d.groessen(), (t, q) -> z.get(t)[q], k.toString());
        Path o = ziel.resolve(Wetterfeld.ordner(zeitraumOrdner()));
        ablagen(o.resolve(Wetterfeld.TEXT), o.resolve(Wetterfeld.DATEN));
        log(f("  %s geschrieben (%.0f MB); Schritt antrieb in %.1f s.", o.resolve(Wetterfeld.DATEN), Files.size(o.resolve(Wetterfeld.DATEN)) / 1e6, (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Schritt seegang (P8b, F74 A): Seegang aus ERA5 (GRIB der Quelle {@code era5_cds_seegang}) nach
     * {@code antrieb/seegang.txt} und {@code .f32} ({@link Seegang}, {@link Wetterfeld#SEEGANG}); über Land aus den
     * Nachbarn über See. Rezept: {@code seegang.ordner} (Vorgabe {@code wetter\era5_cds_seegang}). Nicht in
     * {@link #SCHRITTE}: er läuft nur, wenn er genannt ist.
     */
    /**
     * Schritt sediment (P9b, 12.10): der Boden für das Sediment aus der Sedimentologie von EasyGSH-DB über der Hülle von
     * G3 nach {@code sediment/} ({@link Kornfeld}). Läuft nur mit {@code --schritte sediment}.
     */
    public void sediment() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt sediment");
        Path zip = pfad(rezept.text("sediment.quelle", "sediment\\easygsh_sediment\\EasyGSH_DB_Sedimentologie_2016.zip"));
        if (!Files.isRegularFile(zip)) throw new IOException("sediment: " + zip
                + " fehlt – zuerst laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen easygsh_sediment --ziel " + daten);
        double[] g3 = rezept.zahlen("g3.gebiet", 4);
        double[] vor = rezept.hat("sediment.vorgabe") ? rezept.zahlen("sediment.vorgabe", 3) : new double[] {2e-4, 0.40, 0};
        int reich = rezept.hat("sediment.reichweite") ? (int) rezept.zahl("sediment.reichweite") : 10;
        Korn.Ergebnis e = Korn.backe(zip, ziel.resolve("quellen").resolve("sediment"), g3[0], g3[1], g3[2], g3[3], vor, reich);
        Kornfeld k = e.feld();
        List<String> z = new ArrayList<>();
        // Fester Boden aus den Rauheitsklassen (Schritt rauheit); ohne sie ist aller Boden lose
        File ro = ziel.resolve("rauheit").toFile();
        String festText;
        if (new File(ro, "10m").isDirectory()) {
            Gelaende.Stufe r1 = new File(ro, "1m").isDirectory() ? Gelaende.Stufe.lies(new File(ro, "1m")) : null, r10 = Gelaende.Stufe.lies(new File(ro, "10m"));
            long[] fz = Korn.fest(k, r1, r10);
            festText = f("fest: aus den Rauheitsklassen (Fenne, Salzwiese, Deich, Warft, Deckwerk); %d von %d Pixeln über die Hälfte fest, %d mit festem Anteil, %.2f %% der Punkte ohne Klasse",
                    fz[0], k.nx * k.ny, fz[1], 100.0 * fz[2] / (100.0 * k.nx * k.ny));
        } else festText = "fest: keine Rauheitsklassen unter " + ro + " (Schritt rauheit) – aller Boden gilt als lose";
        z.add("quelle " + zip.getFileName() + " (EasyGSH-DB, BAW)");
        for (String q : e.quellen()) z.add("# " + q);
        z.add(f("# CSV: %d Zeilen, %d im Raster; d50 der CSV gegen das GeoTIFF am selben Pixel: Median der Abweichung %.2f %% über %d Pixel", e.zeilen(), e.imRaster(),
                100 * e.d50Abweichung(), e.d50Punkte()));
        String[] einheit = {"mm", "", ""};
        for (int n = 0; n < 3; n++)
            z.add(f("# %s: %d von %d Pixeln aus der Quelle (Median %.4g %s), %d aus Nachbarn gefüllt, %d mit der Vorgabe %.4g", Kornfeld.FELDER[n], e.gemessen()[n], k.nx * k.ny,
                    e.median()[n] * (n == 0 ? 1e3 : 1), einheit[n], e.gefuellt()[n], e.vorgabe()[n], vor[n] * (n == 0 ? 1e3 : 1)));
        z.add("# " + festText);
        z.add(f("vorgabe %s %s %s", Double.toString(vor[0]), Double.toString(vor[1]), Double.toString(vor[2])));
        z.add("reichweite " + reich);
        k.schreibe(ziel.toFile(), z);
        log(f("  Raster %d × %d zu %.0f m ab O %.0f, N %.0f (Südwestecke)", k.nx, k.ny, k.zelle, k.x0, k.y0));
        for (String q : z) if (q.startsWith("#")) log("  " + q.substring(2));
        if (!(e.d50Abweichung() <= 0.05)) throw new IOException(f("sediment: d50 der CSV und des GeoTIFF weichen im Median um %.1f %% ab – stimmt die Lage des Rasters?", 100 * e.d50Abweichung()));
        log(f("  %s geschrieben; Schritt sediment in %.1f s.", ziel.resolve(Kornfeld.ORDNER), (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Schritt gestalt (P12b, F132–F134 A): die Gestalt des Geländes als Karte zu 0,5 m und die Körper im Watt und am
     * Anleger ({@link Gestaltkarte}); läuft nur, wenn er genannt ist ({@code --schritte gestalt}). Braucht
     * {@code gelaende/} und {@code rauheit/1m}. Ablage {@code gestalt/} und {@code bau/koerper.bin}.
     */
    public void gestalt() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt gestalt");
        double[] geb = rezept.hat("gestalt.gebiet") ? rezept.zahlen("gestalt.gebiet", 4)
                : rezept.hat("gebaeude.gebiet") ? rezept.zahlen("gebaeude.gebiet", 4) : rezept.zahlen("land.gebiet", 4);
        Gelaende gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
        Gelaende.Stufe fein = gel.stufen()[0];
        double e0 = fein.suedwestOst, n0 = fein.suedwestNord + fein.kachelnNord * fein.kachel();
        Gelaende.Stufe kl = Gelaende.Stufe.lies(ziel.resolve("rauheit").resolve("1m").toFile());
        Path osmPfad = pfad(rezept.text("gestalt.osm", rezept.text("bauwerke.osm", "vektor\\osm_szene\\osm_szene.json")));
        Osm osm = Osm.lies(32, osmPfad);
        Gestaltkarte g = new Gestaltkarte();
        if (rezept.hat("gestalt.unter_ufer")) g.unterUfer = rezept.zahl("gestalt.unter_ufer");
        if (rezept.hat("gestalt.anleger")) g.anlegerHoehe = rezept.zahl("gestalt.anleger");
        g.bauen(kl, gel, osm, geb, e0, n0);
        g.schreibe(ziel.resolve(rezept.text("gestalt.ziel", "gestalt")).toFile(), ziel.resolve(rezept.text("gebaeude.ziel", "bau")).toFile(),
                f("Gebiet %.0f %.0f %.0f %.0f; OSM %s", geb[0], geb[1], geb[2], geb[3], osmPfad.getFileName()));
        log("  " + g.bericht);
        log(f("  Schritt gestalt in %.1f s.", (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Schritt gehoelz (P12c, F136 A, F137 A): die Gehölze aus dem bDOM (20 cm) minus DGM1 und dem NDVI des DOP20
     * ({@link Gehoelzkarte}); läuft nur, wenn er genannt ist ({@code --schritte gehoelz}). Braucht {@code gelaende/}, die
     * Kacheln des bDOM ({@code gehoelz.bdom}, Vorgabe {@code hoehe\bdom}) und des DOP20 ({@code gehoelz.dop}, sonst
     * {@code luftbild.dop}); wenn da, {@code bau/gebaeude.bin} (Häuser ausnehmen) und {@code gestalt/} (nur über Land,
     * Nässe für die Art). Ablage {@code bau/gehoelz.bin}, {@code gehoelz.txt}, {@code gehoelz_pruefung.txt}.
     */
    /**
     * Schritt zonen (P13b, F151 A, F152 A): die Zonen der Salzwiese zu 1 m ({@link Zonenkarte}) aus der Statistik der
     * Überflutung eines Laufs ({@code zonen.ueberflutung}, Vorgabe {@code _p13/ueberflutung}; Lauf B1,
     * {@code lauf.demo.VegetationLaufCheck --teil statistik}), dem Gelände und der Gestalt. Legt die Statistik nach
     * {@code vegetation/} und die Zonen nach {@code vegetation/zonen/}; läuft nur, wenn er genannt ist.
     */
    public void zonen() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt zonen");
        Path uo = pfad(rezept.text("zonen.ueberflutung", "_p13\\ueberflutung"));
        com.dan.core.gitter.Ueberflutung u = com.dan.core.gitter.Ueberflutung.lies(uo.toFile());
        if (u == null) throw new IOException("Schritt zonen: Statistik der Überflutung fehlt in " + uo + " (Lauf B1: VegetationLaufCheck --teil statistik)");
        com.dan.core.szene.Gestalt gs = com.dan.core.szene.Gestalt.lies(ziel.resolve(rezept.text("gestalt.ziel", "gestalt")).toFile());
        if (gs == null) throw new IOException("Schritt zonen: Gestalt fehlt (Schritt gestalt)");
        Gelaende gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
        Zonenkarte z = new Zonenkarte();
        if (rezept.hat("zonen.pionier")) z.pionierUnten = rezept.zahl("zonen.pionier");
        if (rezept.hat("zonen.oben")) z.grenzeOben = rezept.zahl("zonen.oben");
        z.fenneOhneTide = !"tide".equals(rezept.text("zonen.fenne", "ohne_tide"));   // F155 A
        log(f("  Statistik %s: %s, %d Tiden; Gestalt %d Kacheln; Grenzen %.2f / %.2f m über MThw; Fenne %s", uo, u.gitter, u.tiden, gs.kacheln.size(), z.pionierUnten, z.grenzeOben,
                z.fenneOhneTide ? "ohne Tidezone (F155 A)" : "nach der Höhe"));
        z.bauen(u, gel, gs);
        u.schreibe(ziel.resolve("vegetation").toFile(), "Kopie aus " + uo);
        z.zonen.schreibe(ziel.toFile(), f("Statistik %s (%d Tiden); Grenzen %.2f / %.2f m über MThw; Fenne %s", uo, u.tiden, z.pionierUnten, z.grenzeOben, z.fenneOhneTide ? "ohne Tidezone (F155 A)" : "nach der Höhe"));
        log("  " + z.bericht);
        log(f("  Schritt zonen in %.1f s.", (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Schritt brandung (P13c, F160 A): die Kanten der Brandung ({@link Kantenkarte}) aus Gelände und Gestalt im Gebiet
     * {@code brandung.gebiet} (Vorgabe {@code gestalt.gebiet}); Ablage {@code brandung/kanten.txt}. Läuft nur, wenn er genannt ist.
     */
    public void brandung() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt brandung");
        double[] geb = rezept.hat("brandung.gebiet") ? rezept.zahlen("brandung.gebiet", 4) : rezept.zahlen("gestalt.gebiet", 4);
        Gelaende gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
        com.dan.core.szene.Gestalt gs = com.dan.core.szene.Gestalt.lies(ziel.resolve(rezept.text("gestalt.ziel", "gestalt")).toFile());
        Kantenkarte k = new Kantenkarte();
        if (rezept.hat("brandung.fuss_max")) k.fussMax = rezept.zahl("brandung.fuss_max");
        k.bauen(gel, gs, geb);
        k.kanten.schreibe(ziel.toFile(), f("Gebiet %.0f %.0f %.0f %.0f; %s", geb[0], geb[1], geb[2], geb[3], k.bericht));
        log("  " + k.bericht);
        log(f("  Schritt brandung in %.1f s.", (System.nanoTime() - t0) / 1e9));
    }

    public void gehoelz() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt gehoelz");
        double[] geb = rezept.hat("gehoelz.gebiet") ? rezept.zahlen("gehoelz.gebiet", 4)
                : rezept.hat("gestalt.gebiet") ? rezept.zahlen("gestalt.gebiet", 4)
                : rezept.hat("gebaeude.gebiet") ? rezept.zahlen("gebaeude.gebiet", 4) : rezept.zahlen("land.gebiet", 4);
        Gelaende gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
        Gelaende.Stufe fein = gel.stufen()[0];
        double e0 = fein.suedwestOst, n0 = fein.suedwestNord + fein.kachelnNord * fein.kachel();
        Path bdom = pfad(rezept.text("gehoelz.bdom", "hoehe\\bdom"));
        Path dop = pfad(rezept.text("gehoelz.dop", rezept.text("luftbild.dop", "luftbild\\dop20")));
        if (!Files.isDirectory(bdom)) throw new IOException("Schritt gehoelz: Ordner des bDOM fehlt: " + bdom
                + " (Kartenwerkzeug: laden --quellen bdom --ziel " + daten + ")");
        File bauOrdner = ziel.resolve(rezept.text("gebaeude.ziel", "bau")).toFile();
        com.dan.core.szene.Bauten haeuser = com.dan.core.szene.Bauten.lies(bauOrdner);
        com.dan.core.szene.Gestalt gs = com.dan.core.szene.Gestalt.lies(ziel.resolve(rezept.text("gestalt.ziel", "gestalt")).toFile());
        Gehoelzkarte g = new Gehoelzkarte();
        if (rezept.hat("gehoelz.ndvi")) g.minNdvi = rezept.zahl("gehoelz.ndvi");
        if (rezept.hat("gehoelz.hoehe")) g.minHoehe = rezept.zahl("gehoelz.hoehe");
        log(f("  bDOM %s, DOP20 %s; Häuser %s, Gestalt %s", bdom, dop, haeuser == null ? "fehlen" : haeuser.gebaeude.size(),
                gs == null ? "fehlt" : gs.kacheln.size() + " Kacheln"));
        g.bauen(bdom, dop, gel, haeuser, gs, geb, e0, n0);
        g.schreibe(bauOrdner, f("Gebiet %.0f %.0f %.0f %.0f", geb[0], geb[1], geb[2], geb[3]));
        log("  " + g.bericht);
        log(f("  Schritt gehoelz in %.1f s.", (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Schritt gebaeude (P12a, F125 A, F126 A): die Häuser aus dem 3D-Gebäudemodell LoD2 als Körper für das Bild
     * ({@link Gebaeude}); läuft nur, wenn er genannt ist ({@code --schritte gebaeude}). Braucht {@code gelaende/} und, für
     * die Farbe der Dächer, {@code luftbild/albedo.txt} (Schritt luftbild) und die DOP20. Ablage {@code bau/}.
     */
    public void gebaeude() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt gebaeude");
        Path ordner = pfad(rezept.text("gebaeude.ordner", "gebaeude\\lod2"));
        if (!Files.isDirectory(ordner)) throw new IOException("Schritt gebaeude: Ordner der LoD2-Daten fehlt: " + ordner
                + " (Kartenwerkzeug: laden --quellen lod2 --ziel " + daten + ")");
        CityGml gml = CityGml.lies(List.of(ordner));
        log(f("  CityGML: %d Dateien, %d Häuser, Bezug %s", gml.dateien, gml.haeuser.size(), gml.srs));
        double[] geb = rezept.hat("gebaeude.gebiet") ? rezept.zahlen("gebaeude.gebiet", 4) : rezept.zahlen("land.gebiet", 4);
        Gelaende gel = Gelaende.ordner(ziel.resolve("gelaende").toFile());
        Gelaende.Stufe fein = gel.stufen()[0];
        double e0 = fein.suedwestOst, n0 = fein.suedwestNord + fein.kachelnNord * fein.kachel();
        Path dopOrdner = pfad(rezept.text("gebaeude.dop", rezept.text("luftbild.dop", "luftbild\\dop20")));
        List<Path> dop = Files.isDirectory(dopOrdner) ? Luftbild.kacheln(dopOrdner) : List.of();
        double[] gain = Gebaeude.eichung(ziel.resolve(rezept.text("luftbild.ziel", "luftbild")).resolve("albedo.txt"));
        if (gain == null) log("  ohne Eichung des Luftbilds (Schritt luftbild fehlt): Dächer mit der Vorgabe");
        else log(f("  Eichung des Luftbilds: g = %.3f / %.3f / %.3f (%s)", gain[0], gain[1], gain[2], gain[3] > 0.5 ? "sRGB" : "linear"));
        Osm osm = null;
        Path osmPfad = pfad(rezept.text("gebaeude.osm", rezept.text("bauwerke.osm", "vektor\\osm_szene\\osm_szene.json")));
        if (Files.isRegularFile(osmPfad)) osm = Osm.lies(32, osmPfad);
        Gebaeude g = new Gebaeude();
        if (rezept.hat("gebaeude.ausnahmen")) g.ausnahmen = rezept.text("gebaeude.ausnahmen");
        if (rezept.hat("gebaeude.wand")) g.wand = rezept.zahlen("gebaeude.wand", 3);
        if (rezept.hat("gebaeude.reet")) g.reet = rezept.zahlen("gebaeude.reet", 3);
        if (rezept.hat("gebaeude.sockel")) g.sockel = rezept.zahl("gebaeude.sockel");
        g.bauen(gml, geb, gel, dop, gain, osm, e0, n0);
        File aus = ziel.resolve(rezept.text("gebaeude.ziel", "bau")).toFile();
        g.schreibe(aus, f("Quelle %s; Gebiet %.0f %.0f %.0f %.0f; Ursprung e0 %.1f, n0 %.1f; %d DOP-Kacheln", ordner, geb[0], geb[1], geb[2], geb[3], e0, n0, dop.size()));
        log("  " + g.bericht);
        log(f("  Schritt gebaeude in %.1f s.", (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Schritt sentinel (P9b, nur mit {@code --schritte sentinel}): Schwebstoff an der Oberfläche aus einer Szene von
     * Sentinel-2 L2A für die Prüfung des Sediments, über die Hülle von G3 auf 20 m (Mittel der vier Pixel zu 10 m), nur
     * wo die Szene Wasser sieht (SCL 6). Reflexion ρ_w aus B04 (665 nm) mit Maßstab und Versatz aus dem JSON der Szene –
     * steht dort {@code earthsearch:boa_offset_applied: true}, ist der Versatz in den Werten schon abgezogen; Schwebstoff
     * nach Nechad u. a. (2010): A ρ_w / (1 − ρ_w / C) mit A = 355,85 g/m³, C = 0,1728 (B = 0). Legt
     * {@code ziel/pruef/sentinel_spm.txt} und {@code .f32} ab ({@code core.gitter.Rasterfeld}).
     */
    public void sentinel() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt sentinel");
        Path s2 = pfad(rezept.text("sentinel.szene", "luftbild\\sentinel2\\S2B_32UMF_20260501_0_L2A"));
        Path b4 = s2.resolve("B04.tif"), sclP = s2.resolve("SCL.tif"), js = s2.resolve(s2.getFileName() + ".json");
        if (!Files.isRegularFile(b4) || !Files.isRegularFile(sclP) || !Files.isRegularFile(js))
            throw new IOException("sentinel: B04.tif, SCL.tif oder das JSON der Szene fehlen in " + s2 + " – laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen sentinel2 --baender B02,B03,B04 --ziel " + daten);
        String j = Files.readString(js, java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher mz = java.util.regex.Pattern.compile("\"datetime\":\\s*\"([^\"]+)\"").matcher(j);
        if (!mz.find()) throw new IOException(js + ": datetime fehlt");
        String zeit = mz.group(1);
        double massstab = 1e-4, versatz = 0;
        boolean abgezogen = java.util.regex.Pattern.compile("\"earthsearch:boa_offset_applied\":\\s*true").matcher(j).find();
        int o = j.indexOf("/B04.tif\"");
        if (o >= 0) {
            String t = j.substring(o, Math.min(j.length(), o + 900));
            java.util.regex.Matcher a = java.util.regex.Pattern.compile("\"scale\":\\s*([-0-9.eE]+)").matcher(t), b = java.util.regex.Pattern.compile("\"offset\":\\s*([-0-9.eE]+)").matcher(t);
            if (a.find()) massstab = Double.parseDouble(a.group(1));
            if (b.find() && !abgezogen) versatz = Double.parseDouble(b.group(1));
        }
        final double A = 355.85, C = 0.1728;
        double[] g3 = rezept.zahlen("g3.gebiet", 4);
        int nx = (int) Math.round((g3[2] - g3[0]) / 20), ny = (int) Math.round((g3[3] - g3[1]) / 20);
        float[] spm = new float[nx * ny];
        java.util.Arrays.fill(spm, Float.NaN);
        long wasser = 0, gueltig = 0, negativ = 0;
        List<Double> rhos = new ArrayList<>();
        try (GeoTiff t = GeoTiff.oeffne(b4); GeoTiff s = GeoTiff.oeffne(sclP)) {
            if (Math.abs(t.dx - 10) > 1e-6 || Math.abs(t.dy + 10) > 1e-6 || Math.abs(s.dx - 20) > 1e-6) throw new IOException("sentinel: B04 zu 10 m und SCL zu 20 m erwartet, nicht " + t.dx + " und " + s.dx);
            int c0 = (int) Math.round((g3[0] - t.x0) / t.dx), r0 = (int) Math.round((g3[3] - t.y0) / t.dy);
            int sc0 = (int) Math.round((g3[0] - s.x0) / s.dx), sr0 = (int) Math.round((g3[3] - s.y0) / s.dy);
            if (Math.abs(t.x0 + c0 * t.dx - g3[0]) > 0.01 || Math.abs(s.x0 + sc0 * s.dx - g3[0]) > 0.01) throw new IOException("sentinel: die Pixel der Szene liegen nicht auf dem Raster von G3");
            int streifen = 200;                                                 // Zeilen zu 20 m je Stück
            for (int z0 = 0; z0 < ny; z0 += streifen) {
                int h = Math.min(streifen, ny - z0);
                float[] dn = t.lies(c0, r0 + 2 * z0, 2 * nx, 2 * h), kl = s.lies(sc0, sr0 + z0, nx, h);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < nx; x++) {
                        if (kl[x + nx * y] != 6) continue;
                        wasser++;
                        double sum = 0;
                        int n = 0;
                        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++) {
                            float v = dn[2 * x + a + 2 * nx * (2 * y + b)];
                            if (v > 0) { sum += v * massstab + versatz; n++; }
                        }
                        if (n < 4) continue;
                        double rho = sum / n;
                        if (!(rho > 0)) { negativ++; continue; }
                        if (rho >= 0.9 * C) continue;
                        gueltig++;
                        if ((gueltig & 63) == 0) rhos.add(rho);
                        spm[x + nx * (ny - 1 - (z0 + y))] = (float) (A * rho / (1 - rho / C));
                    }
            }
        }
        rhos.sort(null);
        double med = rhos.isEmpty() ? Double.NaN : rhos.get(rhos.size() / 2);
        List<String> z = new ArrayList<>();
        z.add("# Schwebstoff an der Oberfläche (g/m³) aus Sentinel-2 L2A, Band B04 (665 nm), nach Nechad u. a. (2010); nur Wasser nach SCL (Klasse 6)");
        z.add("# Aufbereitung " + VERSION + ", Schritt sentinel (Werkbuch HalligHooge Teil A, 12.10, P9b)");
        z.add("szene " + s2.getFileName());
        z.add("zeit " + zeit);
        z.add("massstab " + massstab);
        z.add("versatz " + versatz + (abgezogen ? "   # earthsearch:boa_offset_applied: der Versatz ist in den Werten schon abgezogen" : ""));
        z.add("nechad " + A + " " + C);
        z.add(f("# %d Zellen Wasser, %d mit Wert (Median der Reflexion %.4f), %d mit Reflexion ≤ 0", wasser, gueltig, med, negativ));
        new com.dan.core.gitter.Rasterfeld(g3[0], g3[1], 20, nx, ny, spm).schreibe(ziel.resolve("pruef").resolve("sentinel_spm.txt").toFile(), "sentinel_spm.f32", z);
        log(f("  %s, Aufnahme %s; Reflexion = Wert · %s + %s%s", s2.getFileName(), zeit, massstab, versatz, abgezogen ? " (Versatz laut JSON schon abgezogen)" : ""));
        log(f("  Raster %d × %d zu 20 m: %d Zellen Wasser, %d mit Wert; Reflexion im Median %.4f, Schwebstoff dazu %.1f g/m³; %d mit Reflexion ≤ 0", nx, ny, wasser, gueltig, med,
                A * med / (1 - med / C), negativ));
        if (!(med > 0.002)) throw new IOException(f("sentinel: Reflexion über Wasser im Median %.4f – stimmen Maßstab und Versatz?", med));
        log(f("  %s geschrieben; Schritt sentinel in %.1f s.", ziel.resolve("pruef").resolve("sentinel_spm.txt"), (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Schritt morpho (P9c, nur mit {@code --schritte morpho}): die Bathymetrie der EasyGSH-DB (BAW) von 1996 und 2016 über
     * der Hülle von G3 auf 20 m (Mittel der vier Pixel zu 10 m) als {@code pruef/boden_1996.txt} und {@code boden_2016.txt}
     * ({@link com.dan.core.gitter.Rasterfeld}, m NHN) – der Maßstab für die Morphologie (Japsand, Auftrag und Abtrag).
     */
    public void morpho() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt morpho");
        Path o = pfad(rezept.text("morpho.ordner", "bathymetrie\\easygsh_bathymetrie"));
        double[] g3 = rezept.zahlen("g3.gebiet", 4);
        int nx = (int) Math.round((g3[2] - g3[0]) / 20), ny = (int) Math.round((g3[3] - g3[1]) / 20);
        for (String jahr : new String[] {"1996", "2016"}) {
            Path oj = o.resolve(jahr);
            List<Path> kacheln = new ArrayList<>();
            if (Files.isDirectory(oj)) try (var w = Files.list(oj)) {
                kacheln.addAll(w.filter(p -> p.getFileName().toString().matches("(?i).*\\.tif$")).sorted().toList());
            }
            if (kacheln.isEmpty()) throw new IOException("morpho: keine Kacheln in " + oj + " – laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen easygsh_bathymetrie --ziel " + daten);
            float[] z = new float[nx * ny];
            java.util.Arrays.fill(z, Float.NaN);
            long gesetzt = 0;
            for (Path k : kacheln)
                try (GeoTiff t = GeoTiff.oeffne(k)) {
                    if (Math.abs(t.dx - 10) > 1e-6 || Math.abs(t.dy + 10) > 1e-6) throw new IOException("morpho: Kachel zu 10 m erwartet, nicht " + t.dx + " in " + k);
                    float[] v = t.lies(0, 0, t.breite, t.hoehe);
                    for (int j = 0; j < ny; j++) {
                        int r = (int) Math.round((g3[1] + (j + 1) * 20 - t.y0) / t.dy);          // oberste Pixelzeile der Zelle (dy < 0)
                        if (r < 0 || r + 1 >= t.hoehe) continue;
                        for (int i = 0; i < nx; i++) {
                            int c = (int) Math.round((g3[0] + i * 20 - t.x0) / t.dx);
                            if (c < 0 || c + 1 >= t.breite) continue;
                            double sum = 0;
                            boolean ganz = true;
                            for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++) {
                                float w = v[c + a + t.breite * (r + b)];
                                if (Float.isNaN(w) || w == (float) t.leer || w < -1000 || w > 1000) ganz = false;
                                else sum += w;
                            }
                            if (!ganz) continue;
                            if (Float.isNaN(z[i + nx * j])) gesetzt++;
                            z[i + nx * j] = (float) (sum / 4);
                        }
                    }
                }
            List<String> kopf = new ArrayList<>();
            kopf.add("# Bathymetrie der EasyGSH-DB " + jahr + " (m NHN) über der Hülle von G3 auf 20 m, Mittel der vier Pixel zu 10 m; NaN: keine Kachel oder Lücke");
            kopf.add("# Aufbereitung " + VERSION + ", Schritt morpho (Werkbuch HalligHooge Teil A, 12.10, P9c)");
            kopf.add("quelle EasyGSH-DB (Bundesanstalt für Wasserbau), Bathymetrie " + jahr + ", " + kacheln.size() + " Kacheln aus " + oj);
            kopf.add("jahr " + jahr);
            new com.dan.core.gitter.Rasterfeld(g3[0], g3[1], 20, nx, ny, z).schreibe(ziel.resolve("pruef").resolve("boden_" + jahr + ".txt").toFile(), "boden_" + jahr + ".f32", kopf);
            log(f("  %s: %d Kacheln, %d von %d Zellen zu 20 m mit Wert; %s", jahr, kacheln.size(), gesetzt, (long) nx * ny, ziel.resolve("pruef").resolve("boden_" + jahr + ".txt")));
        }
        log(f("  Schritt morpho in %.1f s.", (System.nanoTime() - t0) / 1e9));
    }

    public void seegang() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt seegang" + (zeitraum() != null ? ", Zeitraum " + zeitraum() + " nach " + Wetterfeld.ordner(zeitraumOrdner()) : ""));
        Path o = pfad(rezept.text("seegang.ordner", "wetter\\era5_cds_seegang"));
        List<Path> dateien = new ArrayList<>();
        if (Files.isDirectory(o)) try (var w = Files.list(o)) {
            dateien.addAll(w.filter(p -> p.getFileName().toString().matches("(?i).*\\.(grib|grb|grib1|grib2)$") && imZeitraum(p)).sorted().toList());
        }
        if (dateien.isEmpty()) throw new IOException("seegang: keine GRIB-Dateien in " + o
                + " – zuerst laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen era5_cds_seegang --monate 202610 --ziel " + daten);
        Seegang.Daten d = Seegang.lies(dateien);
        log(f("  %d Dateien, %d Nachrichten, %d anderer Größen übergangen, %d ersetzt; %s; %s bis %s, Schritt %d s, %d Zeiten",
                dateien.size(), d.nachrichten, d.andere, d.ersetzt, d.raster, d.start(), d.ende(), d.schritt(), d.felder.size()));
        String l = Seegang.luecken(d);
        if (!l.isEmpty()) throw new IOException("seegang: " + l);
        Gradraster r = d.raster;
        int ih = Seegang.GROESSEN.indexOf("swh"), it = Seegang.GROESSEN.indexOf("pp1d");
        double hmax = 0;
        long th = 0;
        int kh = 0;
        for (var e : d.felder.entrySet()) {
            float[] h = e.getValue()[ih];
            for (int k = 0; k < r.punkte(); k++) if (!Float.isNaN(h[k]) && h[k] > hmax) { hmax = h[k]; th = e.getKey(); kh = k; }
        }
        double tpDort = d.felder.get(th)[it][kh];
        long gefuellt = Seegang.fuellen(d);
        log(f("  größte Wellenhöhe %.2f m (Tp %.1f s) am %s bei %.2f° N, %.2f° O; %d von %d Punkten über Land, %d Werte aus den Nachbarn gefüllt",
                hmax, tpDort, Instant.ofEpochSecond(th), r.breite(kh / r.nx()), r.laenge(kh % r.nx()), d.land, r.punkte(), gefuellt));
        Inventar inv = new Inventar();
        for (Path p : dateien) inv.quelle("seegang", daten, p, rezept);
        StringBuilder k = new StringBuilder();
        k.append("# Aufbereitung ").append(VERSION).append(", ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(", Rezept ").append(rezept.datei.getFileName()).append('\n');
        k.append("# Quelle: Gruppe, Pfad (zu daten), Byte, SHA-256, Stand, Lizenz\n");
        for (Inventar.Quelle x : inv.quellen) k.append(x.zeile()).append('\n');
        List<float[][]> z = new ArrayList<>(d.felder.values());
        Wetterfeld.schreibeIn(ziel.toFile(), zeitraumOrdner(), Wetterfeld.SEEGANG, r, d.start(), d.schritt(), z.size(), Seegang.GROESSEN, (t, q) -> z.get(t)[q], k.toString());
        Path oz = ziel.resolve(Wetterfeld.ordner(zeitraumOrdner()));
        ablagen(oz.resolve(Wetterfeld.SEEGANG + ".txt"), oz.resolve(Wetterfeld.SEEGANG + ".f32"));
        log(f("  %s geschrieben (%.1f MB); Schritt seegang in %.1f s.", oz.resolve(Wetterfeld.SEEGANG + ".f32"), Files.size(oz.resolve(Wetterfeld.SEEGANG + ".f32")) / 1e6, (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Schritt luft (P10b, F90 A): Temperatur, Taupunkt, Wassertemperatur, Strahlung, Wärmeflüsse, Bewölkung und
     * Niederschlag aus ERA5 (GRIB der Quelle {@code era5_cds_luft}) nach {@code antrieb/luft.txt} und {@code .f32}
     * ({@link Era5Luft}, {@link Wetterfeld#LUFT}); Summen der Stunde als Mittel in W/m², Wassertemperatur über Land aus
     * den Nachbarn über See. Rezept: {@code luft.ordner} (Vorgabe {@code wetter\era5_cds_luft}). Nicht in
     * {@link #SCHRITTE}: er läuft nur, wenn er genannt ist.
     */
    public void luft() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt luft" + (zeitraum() != null ? ", Zeitraum " + zeitraum() + " nach " + Wetterfeld.ordner(zeitraumOrdner()) : ""));
        Path o = pfad(rezept.text("luft.ordner", "wetter\\era5_cds_luft"));
        List<Path> dateien = new ArrayList<>();
        if (Files.isDirectory(o)) try (var w = Files.list(o)) {
            dateien.addAll(w.filter(p -> p.getFileName().toString().matches("(?i).*\\.(grib|grb|grib1|grib2)$") && imZeitraum(p)).sorted().toList());
        }
        if (dateien.isEmpty()) throw new IOException("luft: keine GRIB-Dateien in " + o
                + " – zuerst laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen era5_cds_luft --monate 202610 --ziel " + daten);
        Era5Luft.Daten d = Era5Luft.lies(dateien);
        log(f("  %d Dateien, %d Nachrichten, %d anderer Größen übergangen, %d ersetzt; %s; %s bis %s, Schritt %d s, %d Zeiten",
                dateien.size(), d.nachrichten, d.andere, d.ersetzt, d.raster, d.start(), d.ende(), d.schritt(), d.felder.size()));
        String l = Era5Luft.luecken(d);
        if (!l.isEmpty()) throw new IOException("luft: " + l);
        Era5Luft.mitteln(d);
        long gefuellt = Era5Luft.fuellen(d);
        Gradraster r = d.raster;
        double[] t2 = Era5Luft.spanne(d, "t2"), sst = Era5Luft.spanne(d, "sst"), sw = Era5Luft.spanne(d, "ssrd"), lw = Era5Luft.spanne(d, "strd");
        log(f("  Temperatur in 2 m %.1f bis %.1f °C, Wasser %.1f bis %.1f °C, Sonnenstrahlung bis %.0f W/m², Gegenstrahlung %.0f bis %.0f W/m²; "
                        + "%d von %d Punkten ohne Wassertemperatur (Land), %d Werte aus den Nachbarn gefüllt",
                t2[0] - 273.15, t2[1] - 273.15, sst[0] - 273.15, sst[1] - 273.15, sw[1], lw[0], lw[1], d.land, r.punkte(), gefuellt));
        Inventar inv = new Inventar();
        for (Path p : dateien) inv.quelle("antrieb", daten, p, rezept);
        StringBuilder k = new StringBuilder();
        k.append("# Aufbereitung ").append(VERSION).append(", ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(", Rezept ").append(rezept.datei.getFileName()).append('\n');
        k.append("# Quelle: Gruppe, Pfad (zu daten), Byte, SHA-256, Stand, Lizenz\n");
        for (Inventar.Quelle x : inv.quellen) k.append(x.zeile()).append('\n');
        List<float[][]> z = new ArrayList<>(d.felder.values());
        Wetterfeld.schreibeIn(ziel.toFile(), zeitraumOrdner(), Wetterfeld.LUFT, r, d.start(), d.schritt(), z.size(), Era5Luft.GROESSEN, (t, q) -> z.get(t)[q], k.toString());
        Path oz = ziel.resolve(Wetterfeld.ordner(zeitraumOrdner()));
        ablagen(oz.resolve(Wetterfeld.LUFT + ".txt"), oz.resolve(Wetterfeld.LUFT + ".f32"));
        log(f("  %s geschrieben (%.1f MB); Schritt luft in %.1f s.", oz.resolve(Wetterfeld.LUFT + ".f32"), Files.size(oz.resolve(Wetterfeld.LUFT + ".f32")) / 1e6, (System.nanoTime() - t0) / 1e9));
    }

    /**
     * Schritt boee (Seegang S8b, SF156 A): die Böe in 10 m ({@code fg10}, m/s) aus ERA5 (GRIB der Quelle
     * {@code era5_cds_boee}) nach {@code antrieb/boee.txt} und {@code .f32} ({@link Era5Boee}, {@link Wetterfeld#BOEE}),
     * Raster der Deutschen Bucht, stündlich. Sie gibt den Katzenpfoten im Seegang ihre Stärke. Rezept: {@code boee.ordner}
     * (Vorgabe {@code wetter\era5_cds_boee}). Mit Zeitraum ({@code --antrieb.zeitraum 201312}) in dessen Ordner. Nicht in
     * {@link #SCHRITTE}: er läuft nur, wenn er genannt ist.
     */
    public void boee() throws IOException {
        long t0 = System.nanoTime();
        log("Schritt boee" + (zeitraum() != null ? ", Zeitraum " + zeitraum() + " nach " + Wetterfeld.ordner(zeitraumOrdner()) : ""));
        Path o = pfad(rezept.text("boee.ordner", "wetter\\era5_cds_boee"));
        List<Path> dateien = new ArrayList<>();
        if (Files.isDirectory(o)) try (var w = Files.list(o)) {
            dateien.addAll(w.filter(p -> p.getFileName().toString().matches("(?i).*\\.(grib|grb|grib1|grib2)$") && imZeitraum(p)).sorted().toList());
        }
        if (dateien.isEmpty()) throw new IOException("boee: keine GRIB-Dateien in " + o
                + " – zuerst laden: python tools\\kartenmaterial\\kartenmaterial.py laden --quellen era5_cds_boee --monate 202610 --ziel " + daten);
        Era5Boee.Daten d = Era5Boee.lies(dateien);
        log(f("  %d Dateien, %d Nachrichten, %d anderer Größen übergangen, %d ersetzt; %s; %s bis %s, Schritt %d s, %d Zeiten",
                dateien.size(), d.nachrichten, d.andere, d.ersetzt, d.raster, d.start(), d.ende(), d.schritt(), d.felder.size()));
        String l = Era5Boee.luecken(d);
        if (!l.isEmpty()) throw new IOException("boee: " + l);
        double[] s = Era5Boee.spanne(d);
        log(f("  Böe %.1f bis %.1f m/s, im Mittel %.1f m/s", s[0], s[1], s[2]));
        Inventar inv = new Inventar();
        for (Path p : dateien) inv.quelle("antrieb", daten, p, rezept);
        StringBuilder k = new StringBuilder();
        k.append("# Aufbereitung ").append(VERSION).append(", ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(", Rezept ").append(rezept.datei.getFileName()).append('\n');
        k.append("# Quelle: Gruppe, Pfad (zu daten), Byte, SHA-256, Stand, Lizenz\n");
        for (Inventar.Quelle x : inv.quellen) k.append(x.zeile()).append('\n');
        List<float[]> z = new ArrayList<>(d.felder.values());
        Wetterfeld.schreibeIn(ziel.toFile(), zeitraumOrdner(), Wetterfeld.BOEE, d.raster, d.start(), d.schritt(), z.size(), Era5Boee.GROESSEN, (t, q) -> z.get(t), k.toString());
        Path oz = ziel.resolve(Wetterfeld.ordner(zeitraumOrdner()));
        ablagen(oz.resolve(Wetterfeld.BOEE + ".txt"), oz.resolve(Wetterfeld.BOEE + ".f32"));
        log(f("  %s geschrieben (%.2f MB); Schritt boee in %.1f s.", oz.resolve(Wetterfeld.BOEE + ".f32"), Files.size(oz.resolve(Wetterfeld.BOEE + ".f32")) / 1e6, (System.nanoTime() - t0) / 1e9));
    }

    /** Alle Quellen des Gebiets ins Inventar. */
    void quellen(Inventar inv) throws IOException {
        String gd = rezept.text("grund.datei");
        inv.quelle("grund", daten, pfad(gd.contains("!") ? gd.substring(0, gd.indexOf('!')) : gd), rezept);
        double[] g3 = rezept.zahlen("land.gebiet", 4);
        for (Dgm1.Kachel k : Dgm1.kacheln(pfad(rezept.text("land.ordner")), rezept.ganze("land.jahre"), g3[0], g3[1], g3[2], g3[3])) inv.quelle("land", daten, k.datei(), rezept);
        java.util.Set<Path> osm = new java.util.TreeSet<>();
        for (String k : new String[] {"bauwerke.osm", "rauheit.osm"})
            if (rezept.hat(k)) for (String x : rezept.text(k).split(";")) if (!x.isBlank()) osm.add(pfad(x.strip()));
        for (Path p : osm) inv.quelle("osm", daten, p, rezept);
        if (rezept.hat("rauheit.dtk5")) {
            try (var w = Files.list(pfad(rezept.text("rauheit.dtk5")))) {
                for (Path p : w.filter(x -> x.getFileName().toString().endsWith(".tif")).sorted().toList()) inv.quelle("dtk5", daten, p, rezept);
            }
        }
        for (Path p : pfade("grob.emodnet")) inv.quelle("emodnet", daten, p, rezept);
        inv.quelle("gebco", daten, pfad(rezept.text("grob.gebco")), rezept);
        inv.quelle("natural_earth", daten, pfad(rezept.text("natural_earth")), rezept);
    }

    /** Pfad aus dem Rezept: absolut oder relativ zu {@code daten}. */
    Path pfad(String p) {
        Path q = Path.of(p.replace('\\', File.separatorChar));
        return q.isAbsolute() ? q : daten.resolve(q);
    }

    /** Eine Quelle; {@code datei.zip!eintrag} wird einmal nach {@code ziel/quellen/} entpackt. */
    Path quelle(String spec) throws IOException {
        int k = spec.indexOf('!');
        if (k < 0) return pfad(spec);
        Path zip = pfad(spec.substring(0, k));
        String eintrag = spec.substring(k + 1);
        Path aus = ziel.resolve("quellen").resolve(Path.of(eintrag).getFileName().toString());
        try (ZipFile z = new ZipFile(zip.toFile())) {
            ZipEntry e = z.getEntry(eintrag);
            if (e == null) throw new IOException(zip + ": kein Eintrag " + eintrag);
            if (Files.isRegularFile(aus) && Files.size(aus) == e.getSize()) return aus;
            long t = System.nanoTime();
            Files.createDirectories(aus.getParent());
            Path tmp = aus.resolveSibling(aus.getFileName() + ".teil");
            try (InputStream in = z.getInputStream(e)) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, aus, StandardCopyOption.REPLACE_EXISTING);
            log(f("Entpackt: %s → %s (%.0f MB, %.1f s)", eintrag, aus, Files.size(aus) / 1e6, (System.nanoTime() - t) / 1e9));
        }
        return aus;
    }

    /** Ohne Ausgabe auf der Konsole (für die Prüfung, die Schritte noch einmal rechnet). */
    public boolean still;

    void log(String s) {
        if (!still) System.out.println(s);
        protokoll.append(s).append('\n');
    }

    static String f(String format, Object... w) { return String.format(Locale.GERMAN, format, w); }
}
