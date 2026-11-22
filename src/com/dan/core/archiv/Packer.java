package com.dan.core.archiv;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import java.util.zip.CRC32C;

/**
 * Packt abgelegte Läufe verlustfrei (seit D1, Werkbuch HalligHooge 162, F253 A): jede Spur Stand für Stand mit Deflate
 * in Blöcken zu 64 KB ({@link Packung}), jeder Stand gleich nach dem Packen entpackt und Byte für Byte verglichen, die
 * fertige Datei noch einmal ganz gelesen und je Stand gegen die Prüfsumme des Originals (CRC32C) gehalten. Spuren, die
 * keiner liest, können wegbleiben ({@link #WEGLASSEN}). Das Original bleibt unberührt.
 * <p>
 * Gelesen wird je Spur der Reihe nach in großen Stücken (eine Platte mag keine vielen Ströme zugleich), gepackt in
 * mehreren Fäden. Ein Lauf entsteht in {@code <ziel>.teil} und heißt erst nach der Prüfung {@code <ziel>}; ein
 * abgebrochener Durchgang fängt dort neu an, fertige Läufe ({@code packung.txt}) werden übersprungen.
 * <pre>
 *   java -cp Renderer.jar com.dan.core.archiv.Packer --von D:\Daten\HalligHooge\_p6\archiv --nach daten\_p6\archiv [--faeden 8] [--alles]
 *   java -cp Renderer.jar com.dan.core.archiv.Packer --pruefen daten\_p6\archiv
 * </pre>
 */
public final class Packer {
    /** Je Vorsatz des Teilordners die Spuren, die die App nicht liest (Befund D1, Werkbuch HalligHooge 161). */
    public static final Map<String, Set<String>> WEGLASSEN = Map.of("hydro_", Set.of("water.eta"), "atmos_", Set.of("surf.T"));
    /** Name der Datei, die einen fertig gepackten und geprüften Lauf kennzeichnet. */
    public static final String FERTIG = "packung.txt";
    /** Größe der Stücke beim Lesen. */
    static final long STUECK = 64L << 20;

    /** Was ein Lauf ergab. */
    public record Ergebnis(String lauf, long roh, long gepackt, int spuren, List<String> weg, int staende, double sekunden, boolean schon) {
        public double faktor() { return gepackt == 0 ? 0 : (double) roh / gepackt; }
    }

    /** Wohin Meldungen gehen. */
    public interface Meldung { void zeile(String s); }

    private Packer() { }

    // ------------------------------------------------------------------ Archiv (Ordner mit Teilläufen)

    /**
     * Packt ein Archiv: jeden Teilordner mit {@code lauf.txt}; Teilordner ohne Lauf (etwa {@code pruef_sediment}) bleiben
     * weg, Dateien neben den Teilordnern werden kopiert. {@code weglassen} null: {@link #WEGLASSEN}.
     */
    public static List<Ergebnis> packeArchiv(Path von, Path nach, Map<String, Set<String>> weglassen, int faeden, Meldung m) throws IOException {
        if (weglassen == null) weglassen = WEGLASSEN;
        List<Ergebnis> l = new ArrayList<>();
        Files.createDirectories(nach);
        List<Path> teile;
        try (Stream<Path> s = Files.list(von)) {
            teile = s.sorted().toList();
        }
        for (Path p : teile) {
            String n = p.getFileName().toString();
            if (Files.isDirectory(p)) {
                if (!Files.isRegularFile(p.resolve("lauf.txt"))) {
                    m.zeile("  " + n + ": kein Lauf (keine lauf.txt), bleibt weg");
                    continue;
                }
                Set<String> weg = Set.of();
                for (Map.Entry<String, Set<String>> e : weglassen.entrySet()) if (n.startsWith(e.getKey())) weg = e.getValue();
                l.add(packeLauf(p, nach.resolve(n), weg, faeden, m));
            } else if (Files.isRegularFile(p)) {
                Files.copy(p, nach.resolve(n), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ ein Lauf

    /** Packt einen Lauf ({@code von} mit {@code lauf.txt}) nach {@code nach}; Spuren in {@code weg} bleiben weg. */
    public static Ergebnis packeLauf(Path von, Path nach, Set<String> weg, int faeden, Meldung m) throws IOException {
        String name = von.getParent() == null ? von.getFileName().toString() : von.getParent().getFileName() + "/" + von.getFileName();
        if (Files.isRegularFile(nach.resolve(FERTIG))) {
            m.zeile(String.format(Locale.GERMAN, "  %-28s schon gepackt und geprüft (%s), übersprungen", name, FERTIG));
            return new Ergebnis(name, 0, 0, 0, List.of(), 0, 0, true);
        }
        if (Files.exists(nach)) throw new IOException(nach + " besteht schon, aber ohne " + FERTIG + " – bitte von Hand ansehen");
        Path teil = nach.resolveSibling(nach.getFileName() + ".teil");
        leeren(teil);
        Files.createDirectories(teil);
        long t0 = System.nanoTime();
        long roh = 0, gepackt = 0;
        List<String> weggelassen = new ArrayList<>();
        Map<String, String> zeilen = new LinkedHashMap<>();
        int staende;
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, faeden));
        try (Archiv a = Archiv.oeffnen(von)) {
            staende = a.staende();
            for (Archiv.Spur s : a.lauf.spuren.values()) {
                if (weg.contains(s.feld())) {
                    weggelassen.add(s.feld());
                    m.zeile(String.format(Locale.GERMAN, "  %-28s %-16s bleibt weg (liest die App nicht), %,.0f MB", name, s.feld(), s.bytesJeStand() * (double) staende / 1048576));
                    continue;
                }
                long s0 = System.nanoTime();
                Path quelle = von.resolve(s.datei()), ziel = teil.resolve(s.alsGepackt().datei());
                if (s.gepackt()) {
                    Files.copy(quelle, ziel, StandardCopyOption.COPY_ATTRIBUTES);
                    zeilen.put(s.feld(), "schon gepackt, kopiert");
                    continue;
                }
                long[] g = packeSpur(quelle, ziel, s, staende, pool);
                roh += s.bytesJeStand() * staende;
                gepackt += g[0];
                String z = String.format(Locale.GERMAN, "%s, %d Stände: %,.0f MB → %,.0f MB (%.2f×%s) in %.1f s", s.ablage(), staende, s.bytesJeStand() * (double) staende / 1048576,
                        g[0] / 1048576.0, s.bytesJeStand() * (double) staende / Math.max(1, g[0]), g[1] == 1 ? ", umgeordnet" : "", (System.nanoTime() - s0) / 1e9);
                zeilen.put(s.feld(), z);
                m.zeile(String.format(Locale.GERMAN, "  %-28s %-16s %s", name, s.feld(), z));
            }
            // Kopf: Spuren gepackt, weggelassene ohne Zeile; dazu, wie gepackt wurde
            StringBuilder kopf = new StringBuilder();
            for (String z : Files.readAllLines(von.resolve("lauf.txt"), StandardCharsets.UTF_8)) {
                if (z.startsWith("spur.")) {
                    int p = z.indexOf(" = ");
                    String feld = p < 0 ? "" : z.substring(5, p);
                    if (weg.contains(feld)) continue;
                    Archiv.Spur s = a.lauf.spuren.get(feld);
                    if (s != null) {
                        kopf.append(Archiv.spurZeile(s.alsGepackt())).append('\n');
                        continue;
                    }
                }
                kopf.append(z).append('\n');
            }
            kopf.append("packung = Deflate in Blöcken zu 64 KB, verlustfrei (D1, Werkbuch HalligHooge 162)").append(weggelassen.isEmpty() ? "" : "; ohne " + String.join(", ", weggelassen)).append('\n');
            Files.writeString(teil.resolve("lauf.txt"), kopf, StandardCharsets.UTF_8);
        } finally {
            pool.shutdownNow();
        }
        // die übrigen Dateien des Laufs (zeiten.f64, hinweis.txt, Bilanzen …); keine Spurdateien, keine Ordner
        try (Stream<Path> s = Files.list(von)) {
            for (Path p : s.sorted().toList()) {
                String n = p.getFileName().toString();
                if (!Files.isRegularFile(p) || n.equals("lauf.txt") || n.endsWith(".f16") || n.endsWith(".f32") || n.endsWith(".f16z") || n.endsWith(".f32z")) continue;
                Files.copy(p, teil.resolve(n), StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        // Prüfung der fertigen Dateien: jeder Stand jeder Spur gegen die Prüfsumme des Originals
        long p0 = System.nanoTime();
        int geprueft = pruefeLauf(teil);
        double pruefS = (System.nanoTime() - p0) / 1e9;
        double sek = (System.nanoTime() - t0) / 1e9;
        StringBuilder f = new StringBuilder();
        f.append("# Gepackt und geprüft (com.dan.core.archiv.Packer, D1, Werkbuch HalligHooge 162)\n");
        f.append("von = ").append(von.toAbsolutePath()).append('\n');
        f.append("staende = ").append(staende).append('\n');
        f.append(String.format(Locale.ROOT, "roh = %d%ngepackt = %d%nfaktor = %.3f%n", roh, gepackt, gepackt == 0 ? 0.0 : (double) roh / gepackt));
        f.append("weggelassen = ").append(String.join(", ", weggelassen)).append('\n');
        f.append("geprueft = ").append(geprueft).append(" Stände, jeder gleich der Prüfsumme des Originals (CRC32C)\n");
        f.append(String.format(Locale.ROOT, "sekunden = %.1f (davon Prüfung %.1f)%n", sek, pruefS));
        zeilen.forEach((k, v) -> f.append("spur.").append(k).append(" = ").append(v).append('\n'));
        Files.writeString(teil.resolve(FERTIG), f, StandardCharsets.UTF_8);
        verschieben(teil, nach);
        m.zeile(String.format(Locale.GERMAN, "  %-28s fertig: %,.0f MB → %,.0f MB (%.2f×), %d Stände geprüft, %.0f s", name, roh / 1048576.0, gepackt / 1048576.0,
                gepackt == 0 ? 0.0 : (double) roh / gepackt, geprueft, sek));
        return new Ergebnis(name, roh, gepackt, zeilen.size(), weggelassen, staende, sek, false);
    }

    /**
     * Packt eine Spur. Liest der Reihe nach in Stücken, packt jeden Stand in einem Faden des Pools (mit Probe) und
     * schreibt in der Folge der Stände. Ob umgeordnet wird, entscheiden der erste und der mittlere Stand. Gibt {Bytes der Datei, umgeordnet 0|1}.
     */
    static long[] packeSpur(Path quelle, Path ziel, Archiv.Spur s, int staende, ExecutorService pool) throws IOException {
        int w = s.ablage().bytes, laenge = s.laenge();
        int jeStand = (int) s.bytesJeStand();
        int stueck = (int) Math.max(1, Math.min(staende, STUECK / Math.max(1, jeStand)));
        try (FileChannel k = FileChannel.open(quelle, StandardOpenOption.READ)) {
            if (k.size() != (long) jeStand * staende) throw new IOException(quelle + ": " + k.size() + " Byte statt " + (long) jeStand * staende);
            // umordnen oder nicht: am ersten und am mittleren Stand (der erste eines Nachlaufs ist oft Ruhe und sagt wenig)
            long mit = 0, ohne = 0;
            for (int p : new int[] {0, staende / 2}) {
                byte[] probe = new byte[jeStand];
                liesGanz(k, probe, (long) p * jeStand);
                mit += Packung.packe(probe, laenge, w, true).bytes();
                ohne += Packung.packe(probe, laenge, w, false).bytes();
            }
            boolean um = mit < ohne;
            try (Packung.Schreiber sch = new Packung.Schreiber(ziel, w, laenge, staende, um)) {
                List<Future<Packung.Stand>> laufend = new ArrayList<>();
                for (int k0 = 0; k0 < staende; k0 += stueck) {
                    int n = Math.min(stueck, staende - k0);
                    List<Future<Packung.Stand>> neu = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        byte[] roh = new byte[jeStand];
                        liesGanz(k, roh, (long) (k0 + i) * jeStand);
                        final boolean u = um;
                        neu.add(pool.submit(() -> Packung.packe(roh, laenge, w, u)));
                    }
                    // das vorige Stück schreiben, während dieses gepackt wird
                    for (Future<Packung.Stand> f : laufend) sch.schreibe(holen(f));
                    laufend = neu;
                }
                for (Future<Packung.Stand> f : laufend) sch.schreibe(holen(f));
            }
            return new long[] {Files.size(ziel), um ? 1 : 0};
        }
    }

    private static Packung.Stand holen(Future<Packung.Stand> f) throws IOException {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("unterbrochen", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof IOException io) throw io;
            throw new IOException(c);
        }
    }

    private static void liesGanz(FileChannel k, byte[] b, long ab) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b);
        while (bb.hasRemaining()) if (k.read(bb, ab + bb.position()) < 0) throw new IOException("Unerwartetes Ende");
    }

    // ------------------------------------------------------------------ Prüfen

    /**
     * Liest jeden Stand jeder gepackten Spur eines Laufs ganz und vergleicht seine Prüfsumme mit der des Originals; gibt
     * die Zahl der Stände (je Spur gezählt einmal: die des Laufs). Wirft bei der ersten Abweichung.
     */
    public static int pruefeLauf(Path lauf) throws IOException {
        try (Archiv a = Archiv.oeffnen(lauf)) {
            for (Archiv.Spur s : a.lauf.spuren.values()) {
                Packung p = a.packung(s.feld());
                if (p == null) continue;
                byte[] roh = new byte[(int) p.bytesJeStand()];
                CRC32C c = new CRC32C();
                for (int k = 0; k < p.staende; k++) {
                    p.lies(k, roh);
                    c.reset();
                    c.update(roh, 0, roh.length);
                    if ((int) c.getValue() != p.pruefsumme(k)) throw new IOException(lauf + ": " + s.feld() + " Stand " + k + " weicht vom Original ab (Prüfsumme)");
                }
            }
            return a.staende();
        }
    }

    /** Prüft alle gepackten Läufe unter einem Ordner (Archiv oder Teil); gibt die Zahl der Läufe. */
    public static int pruefe(Path ordner, Meldung m) throws IOException {
        List<Path> laeufe;
        try (Stream<Path> s = Files.walk(ordner, 3)) {
            laeufe = s.filter(p -> Files.isRegularFile(p.resolve("lauf.txt"))).sorted().toList();
        }
        for (Path p : laeufe) {
            long t0 = System.nanoTime();
            int n = pruefeLauf(p);
            m.zeile(String.format(Locale.GERMAN, "  %s: %d Stände gleich dem Original (%.1f s)", ordner.relativize(p), n, (System.nanoTime() - t0) / 1e9));
        }
        return laeufe.size();
    }

    /**
     * Benennt einen fertigen Ordner um (gleiche Platte). Unter Windows hält ein Virenscanner oder der Suchindex eine eben
     * geschriebene Datei manchmal noch einen Augenblick: dann bis zu zehnmal im Abstand von 1 s.
     */
    public static void verschieben(Path von, Path nach) throws IOException {
        for (int i = 0; ; i++) {
            try {
                Files.move(von, nach, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (java.nio.file.FileSystemException e) {
                if (i >= 9 || e instanceof java.nio.file.FileAlreadyExistsException) throw e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /** Leert einen Ordner, den der Packer selbst angelegt hat ({@code *.teil}), und entfernt ihn. */
    static void leeren(Path teil) throws IOException {
        if (!Files.exists(teil)) return;
        if (!teil.getFileName().toString().endsWith(".teil")) throw new IOException("nur *.teil wird geleert: " + teil);
        try (Stream<Path> s = Files.walk(teil)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    // ------------------------------------------------------------------ Aufruf

    public static void main(String[] args) throws Exception {
        Path von = null, nach = null, pruefen = null;
        int faeden = Runtime.getRuntime().availableProcessors();
        boolean alles = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--von" -> von = Path.of(args[++i]);
                case "--nach" -> nach = Path.of(args[++i]);
                case "--faeden" -> faeden = Integer.parseInt(args[++i]);
                case "--alles" -> alles = true;
                case "--pruefen" -> pruefen = Path.of(args[++i]);
                default -> { }
            }
        }
        Meldung m = System.out::println;
        if (pruefen != null) {
            int n = pruefe(pruefen, m);
            System.out.println(n + " Läufe geprüft, alle gleich dem Original.");
            return;
        }
        if (von == null || nach == null) {
            System.out.println("Aufruf: Packer --von <Archiv> --nach <Ziel> [--faeden n] [--alles]  |  Packer --pruefen <Ordner>");
            System.exit(2);
        }
        long t0 = System.nanoTime();
        List<Ergebnis> e = Files.isRegularFile(von.resolve("lauf.txt"))
                ? List.of(packeLauf(von, nach, alles ? Set.of() : weg(von.getFileName().toString()), faeden, m))
                : packeArchiv(von, nach, alles ? Map.of() : WEGLASSEN, faeden, m);
        long roh = 0, gp = 0;
        for (Ergebnis x : e) {
            roh += x.roh();
            gp += x.gepackt();
        }
        System.out.printf(Locale.GERMAN, "%d Läufe, %,.0f MB → %,.0f MB (%.2f×), %.0f s%n", e.size(), roh / 1048576.0, gp / 1048576.0, gp == 0 ? 0.0 : (double) roh / gp, (System.nanoTime() - t0) / 1e9);
    }

    private static Set<String> weg(String teil) {
        for (Map.Entry<String, Set<String>> e : WEGLASSEN.entrySet()) if (teil.startsWith(e.getKey())) return e.getValue();
        return Set.of();
    }
}
