package com.dan.aufbereitung;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Das Inventar eines Gebiets (Werkbuch HalligHooge Teil A, 10.4, Schritt 8; Prüfstein 1): jede Quelle mit Stand,
 * Lizenz und SHA-256, jede Ablage mit SHA-256. Zeilen in {@code gebiet.txt}, durch Tabulatoren getrennt:
 * {@code quelle <Gruppe> <Pfad> <Byte> <SHA-256> <Stand> <Lizenz>} und {@code datei <Pfad> <Byte> <SHA-256>}.
 */
public final class Inventar {
    /** Eine Quelle; der Pfad ist relativ zum Ordner der Daten (mit /). */
    public record Quelle(String gruppe, String pfad, long bytes, String sha, String stand, String lizenz) {
        public String zeile() { return String.join("\t", "quelle", gruppe, pfad, Long.toString(bytes), sha, stand, lizenz); }
    }

    /** Eine Ablage; der Pfad ist relativ zum Ordner des Gebiets. */
    public record Datei(String pfad, long bytes, String sha) {
        public String zeile() { return String.join("\t", "datei", pfad, Long.toString(bytes), sha); }
    }

    public final List<Quelle> quellen = new ArrayList<>();
    public final List<Datei> dateien = new ArrayList<>();

    /** SHA-256 einer Datei, hexadezimal. */
    public static String sha256(Path p) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = new byte[1 << 20];
            try (InputStream in = Files.newInputStream(p)) {
                int n;
                while ((n = in.read(b)) > 0) md.update(b, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** SHA-256 einer Reihung (für die Prüfung). */
    public static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Nimmt eine Quelle auf: Stand aus dem Rezept ({@code stand.<gruppe>}), sonst aus der Datei. */
    public void quelle(String gruppe, Path daten, Path p, Rezept r) throws IOException {
        String rel = daten.toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
        String stand = r.text("stand." + gruppe, "");
        if (stand.isEmpty()) stand = stand(p);
        String lizenz = r.text("lizenz." + gruppe, "?");
        quellen.add(new Quelle(gruppe, rel, Files.size(p), sha256(p), stand, lizenz));
    }

    /** Nimmt eine Ablage auf. */
    public void datei(Path gebiet, Path p) throws IOException {
        String rel = gebiet.toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
        dateien.add(new Datei(rel, Files.size(p), sha256(p)));
    }

    private static final Pattern OSM = Pattern.compile("\"timestamp_osm_base\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JAHR = Pattern.compile("_(19|20)(\\d\\d)\\.(xyz|tif)$");

    /** Stand einer Datei: OSM nach osm3s, DGM1 nach der Befliegung im Namen, Natural Earth nach VERSION.txt, sonst das Datum der Datei. */
    static String stand(Path p) throws IOException {
        String n = p.getFileName().toString();
        if (n.endsWith(".json")) {
            byte[] b = new byte[4096];
            int k;
            try (InputStream in = Files.newInputStream(p)) { k = in.readNBytes(b, 0, b.length); }
            Matcher m = OSM.matcher(new String(b, 0, k, StandardCharsets.UTF_8));
            if (m.find()) return "OSM " + m.group(1);
        }
        Matcher j = JAHR.matcher(n);
        if (j.find()) return "Befliegung " + j.group(1) + j.group(2);
        if (n.endsWith(".zip")) {
            try (ZipFile z = new ZipFile(p.toFile())) {
                for (var e = z.entries(); e.hasMoreElements(); ) {
                    ZipEntry x = e.nextElement();
                    if (x.getName().endsWith("VERSION.txt")) {
                        try (InputStream in = z.getInputStream(x)) { return "Version " + new String(in.readAllBytes(), StandardCharsets.UTF_8).strip(); }
                    }
                }
            }
        }
        return "Datei vom " + DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis()));
    }

    /** Liest die Zeilen {@code quelle} und {@code datei} aus {@code gebiet.txt}. */
    public static Inventar lies(Path gebietTxt) throws IOException {
        Inventar i = new Inventar();
        for (String z : Files.readAllLines(gebietTxt, StandardCharsets.UTF_8)) {
            String[] t = z.split("\t");
            if (t[0].equals("quelle") && t.length >= 7) i.quellen.add(new Quelle(t[1], t[2], Long.parseLong(t[3]), t[4], t[5], t[6]));
            else if (t[0].equals("datei") && t.length >= 4) i.dateien.add(new Datei(t[1], Long.parseLong(t[2]), t[3]));
        }
        return i;
    }
}
