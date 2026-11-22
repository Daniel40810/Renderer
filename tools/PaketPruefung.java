import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Isolationsprüfung der Element-Packages (Paketvertrag Punkt 9, Ant-Target {@code check-packages}).
 * <p>
 * Jedes Package unter {@code src/com/dan/} des Projekts Renderer wird für sich allein übersetzt, ohne die übrigen
 * Quellen (aus Geiranger übernommen, seit dort fall und cliff hierher kamen):
 * <ol>
 * <li><b>Kern</b> (alles außer {@code gl/} und {@code demo/}) nur gegen das JDK,</li>
 * <li><b>Kern + gl/</b> zusätzlich gegen JOGL,</li>
 * <li><b>alles + demo/</b> zusätzlich gegen FStyle.</li>
 * </ol>
 * Dazu liest es die Importe und voll qualifizierten Namen und meldet alles außer {@code java.*}, {@code javax.*}, dem
 * eigenen Package (und JOGL bzw. FStyle, wo erlaubt), und es prüft, ob jede Shader-Datei, die {@code gl/} nennt, im
 * Package liegt. Rot heißt: nicht fertig. Aufruf ohne Ant: {@code java tools/PaketPruefung.java src lib}.
 * <p>
 * <b>Schichten</b> (Werkbuch HalligHooge Teil A, Abschnitt 4): Die Ordner {@code core}, {@code physics} und
 * {@code render} sind keine Element-Packages, sondern Schichten mit eigenem Vertrag. Ein Modul importiert nur Schichten
 * unter sich; Module derselben Schicht kennen einander nicht:
 * <pre>
 * com.dan.core           nur java.*                      (ohne gpu/ und demo/)
 * com.dan.core.gpu       core + LWJGL (lib/lwjgl/*.jar)
 * com.dan.physics.&lt;m&gt;    core; core.gpu und LWJGL nur in &lt;m&gt;/gpu/
 * com.dan.render.&lt;m&gt;     core + core.gpu + LWJGL
 * com.dan.aufbereitung   core (ohne gpu); Werkzeug, das Gebiete backt, läuft nicht im Lauf (seit P3)
 * com.dan.lauf           core + core.gpu + alle physics und render; verdrahtet die Module (seit P5c, F29 A)
 * demo/ jeweils          zusätzlich FStyle
 * </pre>
 * Jede Schicht wird gegen die übersetzten Klassen der Schichten darunter übersetzt, nie gegen deren Quellen. Dazu
 * kommen die <b>Feldnamen</b>: Jede Zeichenkette in {@code physics} und {@code render}, die wie ein Feld aussieht
 * ({@code "water.eta"}: eine Gruppe des Katalogs, ein Punkt, ein Name), muss im Feldkatalog
 * ({@code com.dan.core.feld.Katalog.grundstufe()}) stehen. Und die <b>Shader</b>: Jeder Pfad im Klassenpfad
 * ({@code "/com/dan/…"}), den eine Quelle oder ein Shader nennt – auch hinter {@code #include} –, muss in einem
 * Package liegen, das dieselbe Regel erlaubt; eine genannte Shader-Datei muss es geben.
 */
public final class PaketPruefung {
    static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE),
            FQN = Pattern.compile("\\bcom\\.dan\\.[\\w.]*\\w"),
            SHADER = Pattern.compile("\"([\\w./-]+\\.(?:vert|frag|comp|geom|glsl))\"");

    public static void main(String[] args) throws Exception {
        File src = new File(args.length > 0 ? args[0] : "src"), lib = new File(args.length > 1 ? args[1] : "lib");
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            System.err.println("check-packages: kein Java-Compiler gefunden (läuft Ant mit einem JRE statt einem JDK?)");
            System.exit(2);
        }
        String jogl = jars(lib, "jogl-all.jar", "gluegen-rt.jar"), fstyle = jars(lib, "FStyle.jar");
        // Phase 13: erlaubt sind nur Packages, die wirklich in FStyle.jar liegen (com.dan.fog und com.dan.flock beginnen
        // auch mit „com.dan.f“ und dürfen von anderen Demos nicht genommen werden)
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(fstyle)) {
            z.stream().map(java.util.zip.ZipEntry::getName).filter(n -> n.endsWith(".class") && n.lastIndexOf('/') > 0)
                    .forEach(n -> FSTYLE.add(n.substring(0, n.lastIndexOf('/')).replace('/', '.')));
        }
        File base = new File(src, "com/dan");
        File[] dirs = base.listFiles(f -> f.isDirectory() && !f.getName().equals("geiranger") && !SCHICHTEN.contains(f.getName()));
        if (dirs == null || dirs.length == 0) {
            System.out.println("check-packages: keine Element-Packages unter " + base);
            return;
        }
        java.util.Arrays.sort(dirs, Comparator.comparing(File::getName));
        System.out.println("check-packages: " + dirs.length + " Packages, jedes für sich übersetzt");
        System.out.printf("  %-14s %-6s %-6s %-6s %-8s %s%n", "Package", "Kern", "gl", "demo", "Importe", "Dateien");
        int failed = 0;
        List<String> reports = new ArrayList<>();
        for (File d : dirs) {
            String name = d.getName(), own = "com.dan." + name;
            List<File> core = new ArrayList<>(), gl = new ArrayList<>(), demo = new ArrayList<>();
            try (Stream<Path> s = Files.walk(d.toPath())) {
                s.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> {
                    String rel = d.toPath().relativize(p).toString().replace('\\', '/');
                    (rel.startsWith("gl/") ? gl : rel.startsWith("demo/") ? demo : core).add(p.toFile());
                });
            }
            String rCore = compile(javac, core, "", name + " Kern", reports);
            List<File> cg = new ArrayList<>(core);
            cg.addAll(gl);
            String rGl = gl.isEmpty() ? "–" : compile(javac, cg, jogl, name + " gl", reports);
            List<File> all = new ArrayList<>(cg);
            all.addAll(demo);
            String rDemo = demo.isEmpty() ? "–" : compile(javac, all, join(jogl, fstyle), name + " demo", reports);
            List<String> bad = new ArrayList<>();
            for (File f : core) bad.addAll(scan(f, own, false, false, d));
            for (File f : gl) bad.addAll(scan(f, own, true, false, d));
            for (File f : demo) bad.addAll(scan(f, own, true, true, d));
            for (File f : gl) bad.addAll(shaders(f));
            String rImp = bad.isEmpty() ? "ok" : "FEHLER";
            if (!bad.isEmpty()) reports.add(name + " Importe/Ressourcen:\n    " + String.join("\n    ", bad));
            boolean good = !rCore.equals("FEHLER") && !rGl.equals("FEHLER") && !rDemo.equals("FEHLER") && bad.isEmpty();
            if (!good) failed++;
            System.out.printf("  %-14s %-6s %-6s %-6s %-8s %d%n", name, rCore, rGl, rDemo, rImp, core.size() + gl.size() + demo.size());
        }
        int[] schichten = schichten(javac, base, lib, fstyle, reports);
        for (String r : reports) System.out.println("\n" + r);
        if (failed > 0 || schichten[1] > 0) {
            if (failed > 0) System.out.println("\ncheck-packages: " + failed + " von " + dirs.length + " Packages sind nicht unabhängig.");
            if (schichten[1] > 0) System.out.println("\ncheck-packages: " + schichten[1] + " von " + schichten[0] + " Modulen der Schichten verletzen den Schichtenvertrag.");
            System.exit(1);
        }
        System.out.println("check-packages: alle " + dirs.length + " Packages unabhängig" + (schichten[0] > 0 ? ", " + schichten[0] + " Module der Schichten halten den Schichtenvertrag." : "."));
    }

    // ------------------------------------------------------------------ Schichten (Werkbuch HalligHooge Teil A, 4)

    static final java.util.Set<String> SCHICHTEN = java.util.Set.of("core", "physics", "render", "aufbereitung", "lauf");
    static final Pattern FELD = Pattern.compile("\"([a-z]+)\\.([A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)*)\"");

    /** Ein Modul einer Schicht: sein Ordner, sein Package und was es außer java.* und sich selbst kennen darf. */
    record Schicht(String name, File dir, String own, boolean coreGpuImKern) { }

    /**
     * Prüft core, physics/&lt;m&gt; und render/&lt;m&gt;. Gibt {Zahl der Module, Zahl der Module mit Verstößen} zurück.
     */
    static int[] schichten(JavaCompiler javac, File base, File lib, String fstyle, List<String> reports) throws Exception {
        File core = new File(base, "core");
        List<Schicht> module = new ArrayList<>();
        for (String s : new String[] {"physics", "render"}) {
            File[] ms = new File(base, s).listFiles(File::isDirectory);
            if (ms == null) continue;
            java.util.Arrays.sort(ms, Comparator.comparing(File::getName));
            for (File m : ms) module.add(new Schicht(s + "." + m.getName(), m, "com.dan." + s + "." + m.getName(), s.equals("render")));
        }
        if (!core.isDirectory()) {
            if (!module.isEmpty()) {
                reports.add("Schichten: physics oder render ohne com.dan.core");
                return new int[] {module.size(), module.size()};
            }
            return new int[] {0, 0};
        }
        String lwjgl = "";
        File[] lj = new File(lib, "lwjgl").listFiles((d, n) -> n.endsWith(".jar") && !n.contains("natives"));
        if (lj != null) {
            java.util.Arrays.sort(lj);
            for (File f : lj) lwjgl = join(lwjgl, f.getAbsolutePath());
        }
        Path coreOut = Files.createTempDirectory("paketpruefung-core"), gpuOut = Files.createTempDirectory("paketpruefung-coregpu");
        List<Path> modulOut = new ArrayList<>();                                     // übersetzte Module für die Schicht lauf (seit P5c)
        int n = 0, failed = 0;
        try {
            System.out.println("check-packages: Schichten core, aufbereitung, physics, render, lauf");
            System.out.printf("  %-18s %-6s %-6s %-6s %-8s %-8s %s%n", "Modul", "Kern", "gpu", "demo", "Importe", "Felder", "Dateien");
            // ---- core: Kern nur gegen das JDK, gpu/ gegen Kern und LWJGL, demo/ zusätzlich gegen FStyle
            List<File> kern = new ArrayList<>(), gpu = new ArrayList<>(), demo = new ArrayList<>();
            teile(core, kern, gpu, demo);
            String rKern = compileTo(javac, kern, "", "core Kern", reports, coreOut);
            boolean hatGpu = !gpu.isEmpty();
            if (hatGpu && lwjgl.isEmpty()) reports.add("core gpu: lib/lwjgl mit den LWJGL-Bibliotheken fehlt");
            String rGpu = !hatGpu ? "–" : lwjgl.isEmpty() ? "FEHLER" : compileTo(javac, gpu, join(coreOut.toString(), lwjgl), "core gpu", reports, gpuOut);
            String unten = hatGpu ? join(join(coreOut.toString(), gpuOut.toString()), lwjgl) : coreOut.toString();
            String rDemo = demo.isEmpty() ? "–" : compile(javac, demo, join(unten, fstyle), "core demo", reports);
            List<String> bad = new ArrayList<>();
            java.util.function.Predicate<String> cKern = x -> x.startsWith("com.dan.core") && !x.startsWith("com.dan.core.gpu") && !x.contains(".demo");
            java.util.function.Predicate<String> cGpu = x -> x.startsWith("org.lwjgl.") || x.startsWith("com.dan.core") && !x.contains(".demo");
            java.util.function.Predicate<String> cDemo = x -> x.startsWith("org.lwjgl.") || x.startsWith("com.dan.core") || fstyle(x);
            for (File f : kern) bad.addAll(scanSchicht(f, core, cKern));
            for (File f : gpu) bad.addAll(scanSchicht(f, core, cGpu));
            for (File f : demo) bad.addAll(scanSchicht(f, core, cDemo));
            bad.addAll(pfade(core, base, rel -> rel.contains("/demo/") ? cDemo : rel.startsWith("/gpu/") ? cGpu : cKern));
            n++;
            if (zeile("core", rKern, rGpu, rDemo, bad, List.of(), kern.size() + gpu.size() + demo.size(), reports)) failed++;
            // ---- Feldkatalog aus dem übersetzten Kern
            java.util.Set<String> namen = new java.util.HashSet<>(), gruppen = new java.util.HashSet<>();
            if (rKern.equals("ok")) {
                try (java.net.URLClassLoader cl = new java.net.URLClassLoader(new java.net.URL[] {coreOut.toUri().toURL()})) {
                    Class<?> k = cl.loadClass("com.dan.core.feld.Katalog");
                    Object katalog = k.getMethod("grundstufe").invoke(null);
                    for (Object o : (java.util.Collection<?>) k.getMethod("namen").invoke(katalog)) namen.add((String) o);
                    for (Object o : (java.util.Collection<?>) k.getMethod("gruppen").invoke(katalog)) gruppen.add((String) o);
                } catch (ReflectiveOperationException e) {
                    reports.add("Schichten: Feldkatalog com.dan.core.feld.Katalog nicht lesbar (" + e + ")");
                    failed++;
                }
            }
            // ---- aufbereitung: Werkzeug über dem Kern (core ohne gpu), demo/ zusätzlich FStyle (Werkbuch 10.4, F21)
            File auf = new File(base, "aufbereitung");
            if (auf.isDirectory()) {
                kern.clear();
                gpu.clear();
                demo.clear();
                teile(auf, kern, gpu, demo);
                if (!gpu.isEmpty()) reports.add("aufbereitung: gpu/ ist in dieser Schicht nicht vorgesehen");
                Path out = Files.createTempDirectory("paketpruefung-aufbereitung");
                String rA, rD;
                try {
                    rA = compileTo(javac, kern, coreOut.toString(), "aufbereitung Kern", reports, out);
                    rD = demo.isEmpty() ? "–" : compile(javac, demo, join(join(out.toString(), coreOut.toString()), fstyle), "aufbereitung demo", reports);
                } finally {
                    loesche(out);
                }
                List<String> badA = new ArrayList<>();
                java.util.function.Predicate<String> aKern = x -> x.equals("com.dan.aufbereitung") || x.startsWith("com.dan.aufbereitung.")
                        || x.startsWith("com.dan.core") && !x.startsWith("com.dan.core.gpu") && !x.contains(".demo");
                java.util.function.Predicate<String> aDemo = aKern.or(PaketPruefung::fstyle);
                for (File f : kern) badA.addAll(scanSchicht(f, auf, x -> aKern.test(x) && !x.contains(".demo")));
                for (File f : demo) badA.addAll(scanSchicht(f, auf, aDemo));
                n++;
                if (zeile("aufbereitung", rA, gpu.isEmpty() ? "–" : "FEHLER", rD, badA, List.of(), kern.size() + gpu.size() + demo.size(), reports)) failed++;
            }
            // ---- physics/<m> und render/<m>
            for (Schicht m : module) {
                kern.clear();
                gpu.clear();
                demo.clear();
                teile(m.dir, kern, gpu, demo);
                String cpKern = m.coreGpuImKern ? unten : coreOut.toString(), mOut = "";
                Path out = Files.createTempDirectory("paketpruefung-modul");
                modulOut.add(out);
                try {
                    if (m.coreGpuImKern) {                                         // render: der ganze Kern darf core.gpu und LWJGL kennen
                        kern.addAll(gpu);
                        gpu.clear();
                    }
                    rKern = compileTo(javac, kern, cpKern, m.name + " Kern", reports, out);
                    mOut = out.toString();
                    if (!gpu.isEmpty() && !hatGpu) reports.add(m.name + " gpu: com.dan.core.gpu fehlt");
                    rGpu = gpu.isEmpty() ? "–" : !hatGpu ? "FEHLER" : compileTo(javac, gpu, join(mOut, unten), m.name + " gpu", reports, out);
                    rDemo = demo.isEmpty() ? "–" : compile(javac, demo, join(join(mOut, unten), fstyle), m.name + " demo", reports);
                } finally {
                    // bleibt bis zum Ende: die Schicht lauf wird gegen alle Module übersetzt
                }
                bad = new ArrayList<>();
                List<String> felder = new ArrayList<>();
                java.util.function.Predicate<String> eigen = x -> x.equals(m.own) || x.startsWith(m.own + ".");
                java.util.function.Predicate<String> coreOhneGpu = x -> x.startsWith("com.dan.core") && !x.startsWith("com.dan.core.gpu") && !x.contains(".demo");
                java.util.function.Predicate<String> coreMitGpu = x -> x.startsWith("org.lwjgl.") || x.startsWith("com.dan.core") && !x.contains(".demo");
                java.util.function.Predicate<String> mKern = eigen.or(m.coreGpuImKern ? coreMitGpu : coreOhneGpu), mGpu = eigen.or(coreMitGpu), mDemo = eigen.or(coreMitGpu).or(PaketPruefung::fstyle);
                for (File f : kern) bad.addAll(scanSchicht(f, m.dir, mKern));
                for (File f : gpu) bad.addAll(scanSchicht(f, m.dir, mGpu));
                for (File f : demo) bad.addAll(scanSchicht(f, m.dir, mDemo));
                bad.addAll(pfade(m.dir, base, rel -> rel.contains("/demo/") ? mDemo : rel.startsWith("/gpu/") || m.coreGpuImKern ? mGpu : mKern));
                for (List<File> l : List.of(kern, gpu, demo)) for (File f : l) felder.addAll(feldnamen(f, m.dir, namen, gruppen));
                n++;
                if (zeile(m.name, rKern, rGpu, rDemo, bad, felder, kern.size() + gpu.size() + demo.size(), reports)) failed++;
            }
            // ---- lauf: über allen Modulen (core, core.gpu, physics, render), demo/ zusätzlich FStyle (Werkbuch 4, F29 A, seit P5c)
            File lf = new File(base, "lauf");
            if (lf.isDirectory()) {
                kern.clear();
                gpu.clear();
                demo.clear();
                teile(lf, kern, gpu, demo);
                if (!gpu.isEmpty()) reports.add("lauf: gpu/ ist in dieser Schicht nicht vorgesehen");
                String alleModule = "";
                for (Path p : modulOut) alleModule = join(alleModule, p.toString());
                String cp = join(unten, alleModule), rL, rD;
                Path out = Files.createTempDirectory("paketpruefung-lauf");
                try {
                    rL = compileTo(javac, kern, cp, "lauf Kern", reports, out);
                    rD = demo.isEmpty() ? "–" : compile(javac, demo, join(join(out.toString(), cp), fstyle), "lauf demo", reports);
                } finally {
                    loesche(out);
                }
                List<String> badL = new ArrayList<>(), felderL = new ArrayList<>();
                java.util.function.Predicate<String> lKern = x -> !x.contains(".demo")
                        && (x.equals("com.dan.lauf") || x.startsWith("com.dan.lauf.") || x.startsWith("com.dan.core") || x.startsWith("com.dan.physics.") || x.startsWith("com.dan.render."));
                java.util.function.Predicate<String> lDemo = x -> x.startsWith("com.dan.lauf.demo") || lKern.test(x) || fstyle(x);
                for (File f : kern) badL.addAll(scanSchicht(f, lf, lKern));
                for (File f : demo) badL.addAll(scanSchicht(f, lf, lDemo));
                badL.addAll(pfade(lf, base, rel -> rel.contains("/demo/") ? lDemo : lKern));
                for (List<File> l : List.of(kern, demo)) for (File f : l) felderL.addAll(feldnamen(f, lf, namen, gruppen));
                n++;
                if (zeile("lauf", rL, gpu.isEmpty() ? "–" : "FEHLER", rD, badL, felderL, kern.size() + gpu.size() + demo.size(), reports)) failed++;
            }
        } finally {
            loesche(coreOut);
            loesche(gpuOut);
            for (Path p : modulOut) loesche(p);
        }
        return new int[] {n, failed};
    }

    /** Druckt die Zeile eines Moduls; true, wenn es Verstöße gibt. */
    static boolean zeile(String name, String rKern, String rGpu, String rDemo, List<String> bad, List<String> felder, int dateien, List<String> reports) {
        if (!bad.isEmpty()) reports.add(name + " Importe:\n    " + String.join("\n    ", bad));
        if (!felder.isEmpty()) reports.add(name + " Feldnamen, die nicht im Katalog stehen:\n    " + String.join("\n    ", felder));
        System.out.printf("  %-18s %-6s %-6s %-6s %-8s %-8s %d%n", name, rKern, rGpu, rDemo, bad.isEmpty() ? "ok" : "FEHLER", felder.isEmpty() ? "ok" : "FEHLER", dateien);
        return rKern.equals("FEHLER") || rGpu.equals("FEHLER") || rDemo.equals("FEHLER") || !bad.isEmpty() || !felder.isEmpty();
    }

    /** Verteilt die Quellen eines Moduls: demo/ (auch gpu/demo/), gpu/, sonst Kern. */
    static void teile(File dir, List<File> kern, List<File> gpu, List<File> demo) throws IOException {
        try (Stream<Path> s = Files.walk(dir.toPath())) {
            s.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> {
                String rel = "/" + dir.toPath().relativize(p).toString().replace('\\', '/');
                (rel.contains("/demo/") ? demo : rel.startsWith("/gpu/") ? gpu : kern).add(p.toFile());
            });
        }
    }

    /** Importe und voll qualifizierte Namen eines Moduls einer Schicht: erlaubt sind java.*, javax.* und was die Regel nennt. */
    static List<String> scanSchicht(File f, File dir, java.util.function.Predicate<String> erlaubt) throws IOException {
        List<String> bad = new ArrayList<>();
        String src = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        String rel = dir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
        Matcher m = IMPORT.matcher(src);
        while (m.find()) {
            String imp = m.group(2);
            if (imp.startsWith("java.") || imp.startsWith("javax.") || erlaubt.test(imp)) continue;
            bad.add(rel + ": import " + imp);
        }
        Matcher q = FQN.matcher(ohneKommentare(src).replaceAll("(?m)^\\s*(package|import)\\s.*$", ""));
        while (q.find()) if (!erlaubt.test(q.group())) bad.add(rel + ": " + q.group());
        return bad;
    }

    static final Pattern PFAD = Pattern.compile("\"(/com/dan/[\\w./-]*)\"");

    /**
     * Der Schichtenvertrag für Shader: jeder Pfad im Klassenpfad („/com/dan/…“), den eine Quelle oder ein Shader des
     * Moduls nennt (auch hinter #include), muss in einem erlaubten Package liegen; nennt er eine Shader-Datei, muss es
     * sie geben. {@code regel} gibt zur Lage der Datei im Modul („/gpu/Kernel.java“) die Regel für Package-Namen.
     */
    static List<String> pfade(File dir, File base, java.util.function.Function<String, java.util.function.Predicate<String>> regel) throws IOException {
        List<String> bad = new ArrayList<>();
        File src = base.getParentFile().getParentFile();
        List<Path> dateien;
        try (Stream<Path> s = Files.walk(dir.toPath())) {
            dateien = s.filter(p -> p.toString().matches(".*\\.(java|vert|frag|comp|geom|glsl)")).sorted().collect(java.util.stream.Collectors.toList());
        }
        for (Path p : dateien) {
            String rel = "/" + dir.toPath().relativize(p).toString().replace('\\', '/');
            Matcher m = PFAD.matcher(ohneKommentare(Files.readString(p, StandardCharsets.UTF_8)));
            while (m.find()) {
                String pfad = m.group(1), letzter = pfad.substring(pfad.lastIndexOf('/') + 1);
                boolean datei = letzter.contains(".");
                String ordner = datei ? pfad.substring(0, pfad.lastIndexOf('/')) : pfad.replaceAll("/$", "");
                String paket = ordner.substring(1).replace('/', '.');
                if (!regel.apply(rel).test(paket)) bad.add(rel.substring(1) + ": Pfad " + pfad + " liegt in einem Package, das hier nicht erlaubt ist");
                else if (datei && letzter.matches(".*\\.(vert|frag|comp|geom|glsl)") && !new File(src, pfad.substring(1)).isFile()) bad.add(rel.substring(1) + ": Shader " + pfad + " gibt es nicht");
            }
        }
        return bad;
    }

    /** Zeichenketten, die wie ein Feldname aussehen (Gruppe des Katalogs, Punkt, Name), aber nicht im Katalog stehen. */
    static List<String> feldnamen(File f, File dir, java.util.Set<String> namen, java.util.Set<String> gruppen) throws IOException {
        List<String> bad = new ArrayList<>();
        if (namen.isEmpty()) return bad;
        String rel = dir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
        Matcher m = FELD.matcher(ohneKommentare(Files.readString(f.toPath(), StandardCharsets.UTF_8)));
        while (m.find()) if (gruppen.contains(m.group(1)) && !namen.contains(m.group(1) + "." + m.group(2))) bad.add(rel + ": \"" + m.group(1) + "." + m.group(2) + "\"");
        return bad;
    }

    static String ohneKommentare(String src) { return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", ""); }

    static void loesche(Path out) throws IOException {
        try (Stream<Path> s = Files.walk(out)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    static final java.util.Set<String> FSTYLE = new java.util.HashSet<>();

    /** Gehört der Name (Import oder voll qualifiziert) zu einem Package aus FStyle.jar? */
    static boolean fstyle(String name) {
        for (String p = name; p.contains("."); p = p.substring(0, p.lastIndexOf('.'))) if (FSTYLE.contains(p)) return true;
        return false;
    }

    static String jars(File lib, String... names) {
        List<String> l = new ArrayList<>();
        for (String n : names) {
            File f = new File(lib, n);
            if (!f.isFile()) {
                System.err.println("check-packages: " + f + " fehlt");
                System.exit(2);
            }
            l.add(f.getAbsolutePath());
        }
        return String.join(File.pathSeparator, l);
    }

    static String join(String a, String b) { return a.isEmpty() ? b : b.isEmpty() ? a : a + File.pathSeparator + b; }

    /** Übersetzt die Dateien allein in einen leeren Ordner; „ok“ oder „FEHLER“ (Meldungen in reports). */
    static String compile(JavaCompiler javac, List<File> files, String cp, String what, List<String> reports) throws IOException {
        if (files.isEmpty()) return "–";
        Path out = Files.createTempDirectory("paketpruefung");
        try {
            return compileTo(javac, files, cp, what, reports, out);
        } finally {
            loesche(out);
        }
    }

    /** Übersetzt die Dateien nach {@code out} (der Ordner bleibt stehen, für die Schichten darüber). */
    static String compileTo(JavaCompiler javac, List<File> files, String cp, String what, List<String> reports, Path out) throws IOException {
        if (files.isEmpty()) return "–";
        {
            DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<>();
            try (StandardJavaFileManager fm = javac.getStandardFileManager(diag, Locale.GERMAN, StandardCharsets.UTF_8)) {
                List<String> opt = new ArrayList<>(List.of("--release", "21", "-encoding", "UTF-8", "-proc:none", "-nowarn", "-implicit:none",
                        "-d", out.toString(), "-sourcepath", ""));
                opt.add("-classpath");
                opt.add(cp);
                boolean ok = javac.getTask(null, fm, diag, opt, null, fm.getJavaFileObjectsFromFiles(files)).call();
                if (ok) return "ok";
                StringBuilder sb = new StringBuilder(what + ":");
                int n = 0;
                for (Diagnostic<? extends JavaFileObject> d : diag.getDiagnostics()) {
                    if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                    if (n++ == 6) { sb.append("\n    …"); break; }
                    String file = d.getSource() == null ? "" : new File(d.getSource().toUri()).getName() + ":" + d.getLineNumber() + " ";
                    sb.append("\n    ").append(file).append(d.getMessage(Locale.GERMAN).split("\n")[0]);
                }
                reports.add(sb.toString());
                return "FEHLER";
            }
        }
    }

    /** Importe und voll qualifizierte Namen außerhalb des Erlaubten (Kommentare zählen nicht). */
    static List<String> scan(File f, String own, boolean jogl, boolean fstyle, File pkgDir) throws IOException {
        List<String> bad = new ArrayList<>();
        String src = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        String rel = pkgDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
        Matcher m = IMPORT.matcher(src);
        while (m.find()) {
            String imp = m.group(2);
            if (imp.startsWith("java.") || imp.startsWith("javax.") || imp.equals(own) || imp.startsWith(own + ".")) continue;
            if (jogl && imp.startsWith("com.jogamp.")) continue;
            if (fstyle && fstyle(imp)) continue;
            bad.add(rel + ": import " + imp);
        }
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "").replaceAll("(?m)^\\s*(package|import)\\s.*$", "");
        Matcher q = FQN.matcher(code);
        while (q.find()) {
            String n = q.group();
            if (n.equals(own) || n.startsWith(own + ".") || (fstyle && fstyle(n))) continue;
            bad.add(rel + ": " + n);
        }
        return bad;
    }

    /**
     * Ein Pfad mit führendem / (Klassenpfad, etwa „/com/dan/mudflat/gl/mudflat.glsl“) zählt nur, wenn er im eigenen
     * Package liegt; sonst null.
     */
    static File absolute(File f, String n) {
        File d = f.getParentFile();
        while (d != null && !(d.getName().equals("dan") && d.getParentFile() != null && d.getParentFile().getName().equals("com"))) {
            if (d.getParentFile() != null && d.getParentFile().getName().equals("dan") && d.getParentFile().getParentFile() != null
                    && d.getParentFile().getParentFile().getName().equals("com")) {
                String own = "/com/dan/" + d.getName() + "/";
                File root = d.getParentFile().getParentFile().getParentFile();
                return n.startsWith(own) ? new File(root, n.substring(1)) : null;
            }
            d = d.getParentFile();
        }
        return null;
    }

    /** Jede Shader-Datei, die eine Klasse in gl/ beim Namen nennt, muss neben ihr liegen (oder im eigenen Package). */
    static List<String> shaders(File f) throws IOException {
        List<String> bad = new ArrayList<>();
        Matcher m = SHADER.matcher(Files.readString(f.toPath(), StandardCharsets.UTF_8));
        while (m.find()) {
            String n = m.group(1);
            File r = n.startsWith("/") ? absolute(f, n) : new File(f.getParentFile(), n);
            if (r == null || !r.isFile()) bad.add(f.getName() + ": Shader " + n + " liegt nicht im Package");
        }
        return bad;
    }
}
