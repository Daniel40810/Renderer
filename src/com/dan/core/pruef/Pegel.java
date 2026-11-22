package com.dan.core.pruef;

import com.dan.core.astro.Partialtide;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ein Pegel als Maßstab (Werkbuch HalligHooge Teil A, 5.9, 7.2 und 12.6, P5b): Lage, Pegelnull, die Reihe des
 * Wasserstands in m NHN in festem Schritt (NaN: fehlt oder Ausreißer), die Kennwerte (MThw, MTnw, HThw …) in m NHN und
 * die harmonische Analyse der Reihe ({@link Harmonik}), aus der {@link #vorhersage} die astronomische Tide rechnet.
 * <p>
 * Abgelegt im Gebiet unter {@code pegel/}: {@code pegel.txt} (Text, UTF-8, Felder durch Tabulator getrennt; Zeilen mit
 * {@code #} sind Kommentar) und je Pegel die Reihe als float32 Little Endian. Zeilen, je Pegel in dieser Folge:
 * <pre>
 * pegel    Name  Nummer  Länge  Breite  Pegelnull(m NHN)  Gewässer
 * reihe    Name  Start(ISO 8601 UTC)  Schritt(s)  Anzahl  Datei  Ausreißer  Lücken
 * kennwert Name  Kürzel  Wert(m NHN)  von  bis  [Datum]
 * analyse  Name  Mittel(m NHN)  Rest-RMS(m)  Werte  Dauer(h)
 * tide     Name  Tide  Amplitude(m)  Phase(Grad, Greenwich)
 * </pre>
 */
public final class Pegel {
    public static final String ORDNER = "pegel", TEXT = "pegel.txt";

    /** Ein Kennwert aus den Stammdaten (m NHN), mit Zeitspanne und, bei HThw und NTnw, dem Tag. */
    public record Kennwert(String kuerzel, double wert, String von, String bis, String datum) { }

    public final String name, nummer, gewaesser;
    public final double laenge, breite, pegelnull;
    public final Instant start;
    public final int schritt;
    /** Wasserstand in m NHN je Schritt; NaN: fehlt oder als Ausreißer verworfen. */
    public final float[] werte;
    public final int ausreisser, luecken;
    public final Map<String, Kennwert> kennwerte;
    /** Harmonische Analyse der Reihe (null: keine). */
    public final Harmonik.Ergebnis analyse;

    public Pegel(String name, String nummer, String gewaesser, double laenge, double breite, double pegelnull, Instant start, int schritt, float[] werte, int ausreisser,
            int luecken, Map<String, Kennwert> kennwerte, Harmonik.Ergebnis analyse) {
        this.name = name;
        this.nummer = nummer;
        this.gewaesser = gewaesser;
        this.laenge = laenge;
        this.breite = breite;
        this.pegelnull = pegelnull;
        this.start = start;
        this.schritt = schritt;
        this.werte = werte;
        this.ausreisser = ausreisser;
        this.luecken = luecken;
        this.kennwerte = Collections.unmodifiableMap(new LinkedHashMap<>(kennwerte));
        this.analyse = analyse;
    }

    public Instant zeit(int k) { return start.plusSeconds((long) k * schritt); }

    public Instant ende() { return zeit(werte.length - 1); }

    /** Wasserstand (m NHN) zur Zeit t, linear zwischen zwei Werten; NaN, wo einer fehlt oder außerhalb der Reihe. */
    public double wert(Instant t) {
        double s = (t.getEpochSecond() - start.getEpochSecond() + t.getNano() * 1e-9) / schritt;
        if (s < -1e-9 || s > werte.length - 1 + 1e-9) return Double.NaN;
        int k = Math.min(werte.length - 2, Math.max(0, (int) Math.floor(s)));
        if (werte.length == 1) return werte[0];
        double w = Math.max(0, Math.min(1, s - k));
        return (1 - w) * werte[k] + w * werte[k + 1];
    }

    /** Astronomische Tide (m NHN) aus der Analyse: Mittel plus alle gefundenen Tiden. */
    public double vorhersage(Instant t) { return analyse.wert(t); }

    /** Ein Kennwert (m NHN) oder NaN. */
    public double kennwert(String kuerzel) {
        Kennwert k = kennwerte.get(kuerzel);
        return k == null ? Double.NaN : k.wert;
    }

    /** Datei der Reihe: der Name in ASCII. */
    public static String datei(String name) {
        String s = name.replace("Ä", "AE").replace("Ö", "OE").replace("Ü", "UE").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        return s.replaceAll("[^0-9A-Za-z_-]", "_") + ".f32";
    }

    // ------------------------------------------------------------------ Ablage

    /** Schreibt {@code pegel/pegel.txt} und die Reihen in den Ordner des Gebiets; {@code kopf}: weitere Zeilen. */
    public static void schreibe(File gebiet, List<Pegel> pegel, String kopf) throws IOException {
        File o = new File(gebiet, ORDNER);
        Files.createDirectories(o.toPath());
        StringBuilder s = new StringBuilder();
        s.append("# Pegel als Maßstab (Werkbuch HalligHooge Teil A, 12.6, P5b), Format in com.dan.core.pruef.Pegel\n");
        if (kopf != null && !kopf.isBlank()) s.append(kopf.strip()).append('\n');
        for (Pegel p : pegel) {
            String d = datei(p.name);
            ByteBuffer b = ByteBuffer.allocate(4 * p.werte.length).order(ByteOrder.LITTLE_ENDIAN);
            for (float x : p.werte) b.putFloat(x);
            File f = new File(o, d), ft = new File(o, d + ".neu");
            Files.write(ft.toPath(), b.array());
            Files.move(ft.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            s.append(zeile("pegel", p.name, p.nummer, z(p.laenge, 6), z(p.breite, 6), z(p.pegelnull, 3), p.gewaesser));
            s.append(zeile("reihe", p.name, p.start.toString(), Integer.toString(p.schritt), Integer.toString(p.werte.length), d, Integer.toString(p.ausreisser), Integer.toString(p.luecken)));
            for (Kennwert k : p.kennwerte.values())
                s.append(k.datum == null ? zeile("kennwert", p.name, k.kuerzel, z(k.wert, 3), k.von, k.bis) : zeile("kennwert", p.name, k.kuerzel, z(k.wert, 3), k.von, k.bis, k.datum));
            if (p.analyse != null) {
                s.append(zeile("analyse", p.name, z(p.analyse.mittel(), 4), z(p.analyse.restRms(), 4), Integer.toString(p.analyse.werte()), z(p.analyse.dauerStunden(), 3)));
                for (Harmonik.Tide t : p.analyse.tiden()) s.append(zeile("tide", p.name, t.tide().name, z(t.amplitude(), 5), z(t.phaseGrad(), 3)));
            }
        }
        File t = new File(o, TEXT), tt = new File(o, TEXT + ".neu");
        Files.writeString(tt.toPath(), s, StandardCharsets.UTF_8);
        Files.move(tt.toPath(), t.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String zeile(String... t) { return String.join("\t", t) + "\n"; }

    private static String z(double x, int stellen) { return String.format(Locale.ROOT, "%." + stellen + "f", x); }

    /** Liegen Pegel im Gebiet? */
    public static boolean da(File gebiet) { return new File(new File(gebiet, ORDNER), TEXT).isFile(); }

    /** Liest alle Pegel aus {@code pegel/} des Gebiets, in der Folge der Datei. */
    public static Map<String, Pegel> lies(File gebiet) throws IOException {
        File o = new File(gebiet, ORDNER);
        Map<String, String[]> kopf = new LinkedHashMap<>(), reihe = new LinkedHashMap<>(), analyse = new LinkedHashMap<>();
        Map<String, Map<String, Kennwert>> kenn = new LinkedHashMap<>();
        Map<String, List<Harmonik.Tide>> tiden = new LinkedHashMap<>();
        for (String z : Files.readAllLines(new File(o, TEXT).toPath(), StandardCharsets.UTF_8)) {
            if (z.isBlank() || z.startsWith("#")) continue;
            String[] t = z.split("\t");
            String n = t.length > 1 ? t[1] : "";
            switch (t[0]) {
                case "pegel" -> kopf.put(n, t);
                case "reihe" -> reihe.put(n, t);
                case "analyse" -> analyse.put(n, t);
                case "kennwert" -> kenn.computeIfAbsent(n, x -> new LinkedHashMap<>()).put(t[2],
                        new Kennwert(t[2], Double.parseDouble(t[3]), t[4], t[5], t.length > 6 ? t[6] : null));
                case "tide" -> {
                    Partialtide p = Partialtide.nach(t[2]);
                    if (p == null) throw new IOException(TEXT + ": unbekannte Tide " + t[2]);
                    tiden.computeIfAbsent(n, x -> new ArrayList<>()).add(new Harmonik.Tide(p, Double.parseDouble(t[3]), Double.parseDouble(t[4])));
                }
                default -> { }
            }
        }
        Map<String, Pegel> m = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : kopf.entrySet()) {
            String n = e.getKey();
            String[] k = e.getValue(), r = reihe.get(n), a = analyse.get(n);
            if (r == null) throw new IOException(TEXT + ": keine Reihe für " + n);
            int anzahl = Integer.parseInt(r[4]);
            byte[] roh = Files.readAllBytes(new File(o, r[5]).toPath());
            if (roh.length != 4 * anzahl) throw new IOException(r[5] + ": " + roh.length + " Byte statt " + 4 * anzahl);
            float[] w = new float[anzahl];
            ByteBuffer.wrap(roh).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(w);
            Harmonik.Ergebnis h = a == null ? null : new Harmonik.Ergebnis(Double.parseDouble(a[2]), tiden.getOrDefault(n, List.of()), List.of(),
                    Double.parseDouble(a[3]), Integer.parseInt(a[4]), Double.parseDouble(a[5]));
            m.put(n, new Pegel(n, k[2], k.length > 6 ? k[6] : "", Double.parseDouble(k[3]), Double.parseDouble(k[4]), Double.parseDouble(k[5]), Instant.parse(r[2]),
                    Integer.parseInt(r[3]), w, Integer.parseInt(r[6]), Integer.parseInt(r[7]), kenn.getOrDefault(n, Map.of()), h));
        }
        return m;
    }
}
