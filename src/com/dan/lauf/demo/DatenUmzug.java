package com.dan.lauf.demo;

import com.dan.core.archiv.Packer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32C;

/**
 * Zieht die Daten einer App um (seit D1, Werkbuch HalligHooge 162, F253–F255 A): alles, was die Szenendatei über
 * {@code ${daten}} liest – das Gebiet und die Archive der Szenarien –, vom alten Ort an einen neuen. Die Archive werden
 * verlustfrei gepackt ({@link Packer}, ohne die Spuren, die die App nicht liest), das Gebiet wird kopiert, ohne was
 * nur die Aufbereitung braucht ({@code --ohne}). Jede Datei und jeder Stand wird gegen das Original geprüft. Erst
 * wenn alles stimmt, stellt das Werkzeug {@code daten =} in den Szenendateien um ({@code --umstellen}, mit Sicherung)
 * und nimmt eine gesetzte Einstellung „Ort der Daten“ heraus ({@code --einstellungen}). Der alte Ort bleibt unverändert.
 * Ein abgebrochener Umzug fängt beim nächsten Aufruf dort an, wo er war.
 * <pre>
 * java -cp Renderer.jar com.dan.lauf.demo.DatenUmzug --szenen src\szenen\Hooge.szenen --nach daten
 *      --ohne "gebiet\quellen\topography_2021_ger.tif;gebiet\geschlossen"
 *      --umstellen "src\szenen\Hooge.szenen;build\classes\szenen\Hooge.szenen;dist\abbild\HalligHooge\app\szenen\Hooge.szenen"
 *      --einstellungen "%USERPROFILE%\.steuerhaus\HalligHooge\einstellungen.properties" --sicherung _to_delete\vorD1 [--reserve 5] [--faeden 8]
 * </pre>
 */
public final class DatenUmzug {
    /** Geschätzter Anteil gepackt an ungepackt für die Platzprüfung (gemessen 0,36; mit Spielraum). */
    static final double ANTEIL = 0.45;
    static final String FERTIG_KOPIE = "kopie.txt";
    private static final Pattern DATEN = Pattern.compile("(?m)^daten([ \\t]*)=[ \\t]*([^\\r\\n#]*?)[ \\t]*(#[^\\r\\n]*)?$");

    private DatenUmzug() { }

    public static void main(String[] args) {
        Path szenen = null, nach = null, sicherung = null, einstellungen = null;
        List<String> ohne = new ArrayList<>(), umstellen = new ArrayList<>();
        double reserve = 5;
        int faeden = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> szenen = Path.of(args[++i]);
                case "--nach" -> nach = Path.of(args[++i]);
                case "--ohne" -> ohne.addAll(liste(args[++i]));
                case "--umstellen" -> umstellen.addAll(liste(args[++i]));
                case "--einstellungen" -> einstellungen = Path.of(args[++i]);
                case "--sicherung" -> sicherung = Path.of(args[++i]);
                case "--reserve" -> reserve = Double.parseDouble(args[++i]);
                case "--faeden" -> faeden = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("unbekannt: " + args[i]);
            }
        }
        if (szenen == null || nach == null) {
            System.out.println("Aufruf: DatenUmzug --szenen <Szenendatei> --nach <Ordner> [--ohne a;b] [--umstellen a;b;c] [--einstellungen <Datei>] [--sicherung <Ordner>] [--reserve GB] [--faeden n]");
            System.exit(2);
        }
        long t0 = System.nanoTime();
        try {
            int e = umziehen(szenen, nach.toAbsolutePath().normalize(), ohne, umstellen, einstellungen, sicherung, reserve, faeden);
            System.out.printf(Locale.GERMAN, "%nUmzug %s nach %.1f min.%n", e == 0 ? "fertig" : "abgebrochen", (System.nanoTime() - t0) / 6e10);
            System.exit(e);
        } catch (Throwable x) {
            System.out.println("FEHLER: " + x);
            x.printStackTrace(System.out);
            System.out.println("Abbruch – die Szenendateien sind unverändert, der alte Ort auch. Ein neuer Aufruf macht dort weiter.");
            System.exit(1);
        }
    }

    static List<String> liste(String s) {
        List<String> l = new ArrayList<>();
        for (String x : s.split(";")) if (!x.isBlank()) l.add(x.strip());
        return l;
    }

    /** Der Wert von {@code daten =} in einer Szenendatei (ohne Kommentar) oder null. */
    static String daten(String text) {
        Matcher m = DATEN.matcher(text);
        return m.find() ? m.group(2).strip() : null;
    }

    /** Die Pfade unter {@code ${daten}}, die die Szenendatei nennt (Gebiet, Archive), in ihrer Folge, ohne doppelte. */
    static List<String> teile(String text) {
        Set<String> s = new LinkedHashSet<>();
        for (String z : text.split("\\R")) {
            String t = z.strip();
            if (t.startsWith("#")) continue;
            int p = t.indexOf('=');
            if (p < 0) continue;
            String v = t.substring(p + 1).strip();
            int h = v.indexOf(" #");
            if (h >= 0) v = v.substring(0, h).strip();
            int d = v.indexOf("${daten}");
            if (d != 0) continue;
            String rel = v.substring("${daten}".length()).replaceAll("^[\\\\/]+", "");
            if (!rel.isEmpty()) s.add(rel.replace('\\', '/'));
        }
        return new ArrayList<>(s);
    }

    static int umziehen(Path szenen, Path nach, List<String> ohne, List<String> umstellen, Path einstellungen, Path sicherung, double reserveGb, int faeden) throws IOException {
        String text = Files.readString(szenen, StandardCharsets.UTF_8);
        String alt = daten(text);
        if (alt == null) throw new IOException(szenen + ": keine Zeile „daten = …“");
        Path von = Path.of(alt);
        List<String> teile = teile(text);
        System.out.println("DatenUmzug – Daten der App umziehen (Werkbuch HalligHooge 162, D1)");
        System.out.println("  von  " + von);
        System.out.println("  nach " + nach);
        System.out.println("  " + teile.size() + " Teile: " + String.join(", ", teile));
        if (von.toAbsolutePath().normalize().equals(nach)) {
            System.out.println("  Die Szenendatei zeigt schon auf den neuen Ort – nichts zu tun.");
            return 0;
        }
        List<String> ohneN = new ArrayList<>();
        for (String o : ohne) ohneN.add(o.replace('\\', '/').replaceAll("/+$", ""));

        // 1 Platz
        System.out.println("\n[1/5] Platz prüfen");
        long brauchtRoh = 0, braucht = 0;
        for (String t : teile) {
            Path q = von.resolve(t);
            if (!Files.isDirectory(q)) throw new IOException("fehlt: " + q + " (ist die Platte angeschlossen?)");
            long g = groesse(q, von, ohneN, t.startsWith("gebiet") ? Set.of() : Set.of("pruef_"));
            brauchtRoh += g;
            boolean archiv = !Files.isRegularFile(q.resolve("gebiet.txt"));
            long b = archiv ? (long) (g * ANTEIL) : g;
            if (Files.isDirectory(nach.resolve(t))) b = 0;          // schon da (vorheriger Durchgang)
            braucht += b;
            System.out.printf(Locale.GERMAN, "  %-22s %6.1f GB%s%n", t, g / 1e9 / 1.073741824, archiv ? " (gepackt rund " + String.format(Locale.GERMAN, "%.1f", g * ANTEIL / 1e9 / 1.073741824) + ")" : "");
        }
        Files.createDirectories(nach);
        FileStore fs = Files.getFileStore(nach);
        double frei = fs.getUsableSpace() / 1073741824.0, brauchtG = braucht / 1073741824.0;
        System.out.printf(Locale.GERMAN, "  zusammen %.1f GB, am neuen Ort rund %.1f GB; frei %.1f GB, Reserve %.0f GB%n", brauchtRoh / 1073741824.0, brauchtG, frei, reserveGb);
        if (brauchtG + reserveGb > frei) {
            System.out.println("  zu wenig Platz – Abbruch, nichts verändert.");
            return 3;
        }

        // 2–3 Gebiet kopieren, Archive packen (jeweils mit Prüfung)
        long t1 = System.nanoTime();
        System.out.println("\n[2/5] Gebiet kopieren und Archive packen, jede Datei und jeden Stand prüfen");
        long gepacktSumme = 0, rohSumme = 0;
        for (String t : teile) {
            Path q = von.resolve(t), z = nach.resolve(t);
            if (Files.isRegularFile(q.resolve("gebiet.txt"))) {
                long[] k = kopiere(q, z, von, ohneN);
                System.out.printf(Locale.GERMAN, "  %-22s %d Dateien, %,.0f MB kopiert und geprüft%s%n", t, k[0], k[1] / 1048576.0, k[2] == 1 ? " (schon da)" : "");
            } else {
                System.out.println("  " + t);
                List<Packer.Ergebnis> e = Packer.packeArchiv(q, z, Packer.WEGLASSEN, faeden, s -> System.out.println("  " + s));
                for (Packer.Ergebnis x : e) {
                    rohSumme += x.roh();
                    gepacktSumme += x.gepackt();
                }
            }
        }
        System.out.printf(Locale.GERMAN, "  gepackt in diesem Durchgang: %,.0f MB → %,.0f MB, %.1f min%n", rohSumme / 1048576.0, gepacktSumme / 1048576.0, (System.nanoTime() - t1) / 6e10);

        // 4 Größe am neuen Ort
        System.out.println("\n[3/5] Größe am neuen Ort");
        long summe = 0;
        for (String t : teile) {
            long g = groesse(nach.resolve(t), nach, List.of(), Set.of());
            summe += g;
            System.out.printf(Locale.GERMAN, "  %-22s %6.2f GB%n", t, g / 1073741824.0);
        }
        System.out.printf(Locale.GERMAN, "  zusammen %.2f GB (vorher %.2f GB)%n", summe / 1073741824.0, brauchtRoh / 1073741824.0);
        StringBuilder bericht = new StringBuilder();
        bericht.append("# Umzug der Daten (com.dan.lauf.demo.DatenUmzug, D1, Werkbuch HalligHooge 162)\n");
        bericht.append("von = ").append(von).append('\n').append("teile = ").append(String.join(", ", teile)).append('\n');
        bericht.append(String.format(Locale.ROOT, "vorher = %d%nnachher = %d%n", brauchtRoh, summe));
        Files.writeString(nach.resolve("umzug.txt"), bericht, StandardCharsets.UTF_8);

        // 5 Umstellen
        System.out.println("\n[4/5] Szenendateien auf den neuen Ort umstellen" + (sicherung == null ? "" : " (Sicherung " + sicherung + ")"));
        for (int n = 1; n <= umstellen.size(); n++) {
            // die Sicherung heißt nach der Stelle in der Liste (1_, 2_, 3_), auch wenn eine Datei fehlt – daten_zurueck.cmd verlässt sich darauf
            Path f = Path.of(umstellen.get(n - 1));
            if (!Files.isRegularFile(f)) {
                System.out.println("  fehlt (übersprungen): " + f);
                continue;
            }
            String s = Files.readString(f, StandardCharsets.UTF_8);
            Matcher m = DATEN.matcher(s);
            if (!m.find()) {
                System.out.println("  keine Zeile „daten =“ (übersprungen): " + f);
                continue;
            }
            if (sicherung != null) {
                Files.createDirectories(sicherung);
                Files.copy(f, sicherung.resolve(n + "_" + f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            String neu = s.substring(0, m.start()) + "daten" + m.group(1) + "= " + nach + (m.group(3) == null ? "" : "   " + m.group(3)) + s.substring(m.end());
            Files.writeString(f, neu, StandardCharsets.UTF_8);
            System.out.println("  " + f + ": daten = " + nach);
        }

        System.out.println("\n[5/5] Einstellung „Ort der Daten“ der App");
        if (einstellungen != null && Files.isRegularFile(einstellungen)) {
            List<String> l = Files.readAllLines(einstellungen, StandardCharsets.ISO_8859_1), rest = new ArrayList<>();
            for (String z : l) if (!z.matches("^\\s*daten\\s*[=:].*")) rest.add(z);
            if (rest.size() != l.size()) {
                if (sicherung != null) Files.copy(einstellungen, sicherung.resolve("einstellungen.properties"), StandardCopyOption.REPLACE_EXISTING);
                Files.write(einstellungen, rest, StandardCharsets.ISO_8859_1);
                System.out.println("  zurückgesetzt (war gesetzt): " + einstellungen);
            } else System.out.println("  war nicht gesetzt");
        } else System.out.println("  keine Einstellungsdatei");
        System.out.println("\nDie App liest jetzt aus " + nach + ". Der alte Ort " + von + " ist unverändert.");
        return 0;
    }

    /** Größe eines Ordners ohne die ausgenommenen Pfade (relativ zu {@code wurzel}) und ohne Teilordner mit diesen Vorsätzen. */
    static long groesse(Path ordner, Path wurzel, List<String> ohne, Set<String> ohneVorsatz) throws IOException {
        long[] n = new long[1];
        try (Stream<Path> s = Files.walk(ordner)) {
            s.filter(Files::isRegularFile).forEach(p -> {
                if (ausgenommen(wurzel.relativize(p), ohne)) return;
                for (Path x : ordner.relativize(p)) for (String v : ohneVorsatz) if (x.toString().startsWith(v)) return;
                try {
                    n[0] += Files.size(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
        return n[0];
    }

    static boolean ausgenommen(Path rel, List<String> ohne) {
        String r = rel.toString().replace('\\', '/');
        for (String o : ohne) if (r.equals(o) || r.startsWith(o + "/")) return true;
        return false;
    }

    /**
     * Kopiert einen Ordner ohne die ausgenommenen Pfade nach {@code <ziel>.teil}, prüft jede Datei (CRC32C beim Lesen
     * des Originals gegen CRC32C der Kopie) und benennt dann um. {Dateien, Bytes, schon da 0|1}.
     */
    static long[] kopiere(Path von, Path ziel, Path wurzel, List<String> ohne) throws IOException {
        if (Files.isRegularFile(ziel.resolve(FERTIG_KOPIE))) return new long[] {0, 0, 1};
        if (Files.exists(ziel)) throw new IOException(ziel + " besteht schon, aber ohne " + FERTIG_KOPIE + " – bitte von Hand ansehen");
        Path teil = ziel.resolveSibling(ziel.getFileName() + ".teil");
        if (Files.exists(teil)) try (Stream<Path> s = Files.walk(teil)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
        long dateien = 0, bytes = 0;
        List<Path> alle;
        try (Stream<Path> s = Files.walk(von)) {
            alle = s.sorted().toList();
        }
        byte[] puffer = new byte[1 << 20];
        for (Path p : alle) {
            Path rel = von.relativize(p);
            if (ausgenommen(wurzel.relativize(p), ohne)) continue;
            Path z = teil.resolve(rel.toString());
            if (Files.isDirectory(p)) {
                Files.createDirectories(z);
                continue;
            }
            Files.createDirectories(z.getParent());
            CRC32C a = new CRC32C(), b = new CRC32C();
            try (InputStream in = Files.newInputStream(p); OutputStream out = Files.newOutputStream(z, StandardOpenOption.CREATE_NEW)) {
                int r;
                while ((r = in.read(puffer)) > 0) {
                    a.update(puffer, 0, r);
                    out.write(puffer, 0, r);
                }
            }
            try (InputStream in = Files.newInputStream(z)) {
                int r;
                while ((r = in.read(puffer)) > 0) b.update(puffer, 0, r);
            }
            if (a.getValue() != b.getValue() || Files.size(p) != Files.size(z)) throw new IOException("Kopie weicht ab: " + z);
            Files.setLastModifiedTime(z, FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis()));
            dateien++;
            bytes += Files.size(z);
        }
        Files.writeString(teil.resolve(FERTIG_KOPIE), "# Kopiert und geprüft (DatenUmzug, D1)\nvon = " + von.toAbsolutePath() + "\ndateien = " + dateien + "\nbytes = " + bytes
                + "\nohne = " + String.join(", ", ohne) + "\n", StandardCharsets.UTF_8);
        Packer.verschieben(teil, ziel);
        return new long[] {dateien, bytes, 0};
    }
}
