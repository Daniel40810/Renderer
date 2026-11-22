import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

/**
 * GLSL → SPIR-V für den Bau (Werkbuch HalligHooge Teil A, Abschnitt 6 „Bau“; aus HalligHooge P0.5): übersetzt jede
 * Datei {@code *.comp}, {@code *.vert}, {@code *.frag} der Vulkan-Schichten mit shaderc (Vulkan 1.3, SPIR-V 1.6,
 * Optimierung auf Leistung) nach {@code <ziel>/<pfad>.spv}. {@code #include "/com/dan/…"} wird gegen die Quelle
 * aufgelöst, ohne führenden Schrägstrich relativ zur Datei. Bricht mit der Meldung von shaderc ab. Aufruf aus Ant
 * (Ziel {@code spirv}):
 * <pre>
 *   java -cp lib/lwjgl/lwjgl.jar;…shaderc… tools/SpirvBau.java src build/classes
 * </pre>
 * Vulkan-Schichten sind {@code com/dan/core/gpu}, {@code com/dan/physics/<m>/gpu} und {@code com/dan/render}; nur
 * dort wird übersetzt. Die Shader der Element-Packages (OpenGL, {@code gl/}) bleiben unberührt. Dieselben
 * Einstellungen hat {@code com.dan.core.gpu.Shader} zur Laufzeit; {@code GpuCheck} vergleicht beide Byte für Byte.
 * Neben jedes {@code .spv} kommt {@code .spv.von} mit der SHA-256 jeder Quelle, aus der es gebaut ist; ändert sich
 * eine, übersetzt {@code Shader.laden} wieder zur Laufzeit.
 */
public class SpirvBau {
    /** Liegt die Datei (Pfad unter der Quelle, mit /) in einer Vulkan-Schicht? */
    static boolean vulkan(String rel) {
        return rel.startsWith("com/dan/core/gpu/") || rel.startsWith("com/dan/render/") || rel.matches("com/dan/physics/[^/]+/gpu/.*");
    }

    /** „SHA-256 Pfad“ einer Quelle, der Pfad wie im Klassenpfad (/com/dan/…). */
    static String zeile(Path quelle, Path datei) throws IOException {
        try {
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(datei)));
            return hash + " /" + quelle.toAbsolutePath().normalize().relativize(datei.toAbsolutePath().normalize()).toString().replace('\\', '/') + "\n";
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] a) throws IOException {
        Path quelle = Path.of(a.length > 0 ? a[0] : "src"), ziel = Path.of(a.length > 1 ? a[1] : "build/classes");
        List<Path> dateien;
        try (Stream<Path> w = Files.walk(quelle)) {
            dateien = w.filter(p -> {
                String n = quelle.relativize(p).toString().replace('\\', '/');
                return (n.endsWith(".comp") || n.endsWith(".vert") || n.endsWith(".frag")) && vulkan(n);
            }).sorted().collect(Collectors.toList());
        }
        long compiler = shaderc_compiler_initialize();
        java.util.Set<Path> eingebunden = new java.util.LinkedHashSet<>();
        ShadercIncludeResolve aufloesen = ShadercIncludeResolve.create((nutzer, gewuenscht, typ, anfordernd, tiefe) -> {
            String w = memUTF8(gewuenscht), von = memUTF8(anfordernd);
            Path p = w.startsWith("/") ? quelle.resolve(w.substring(1)) : Path.of(von).resolveSibling(w);
            ShadercIncludeResult r = ShadercIncludeResult.calloc();
            try {
                r.source_name(memUTF8(p.toString(), false)).content(memUTF8(Files.readString(p, StandardCharsets.UTF_8), false));
                eingebunden.add(p.normalize());
            } catch (IOException e) {
                r.source_name(memUTF8("", false)).content(memUTF8("Include nicht gefunden: " + p, false));
            }
            return r.address();
        });
        ShadercIncludeResultRelease freigeben = ShadercIncludeResultRelease.create((nutzer, adresse) -> {
            ShadercIncludeResult r = ShadercIncludeResult.create(adresse);
            memFree(r.source_name());
            memFree(r.content());
            r.free();
        });
        int n = 0;
        long t0 = System.nanoTime();
        try {
            for (Path d : dateien) {
                String text = Files.readString(d, StandardCharsets.UTF_8), name = d.getFileName().toString();
                int art = name.endsWith(".comp") ? shaderc_compute_shader : name.endsWith(".vert") ? shaderc_vertex_shader : shaderc_fragment_shader;
                long opt = shaderc_compile_options_initialize();
                shaderc_compile_options_set_target_env(opt, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_3);
                shaderc_compile_options_set_target_spirv(opt, shaderc_spirv_version_1_6);
                shaderc_compile_options_set_optimization_level(opt, shaderc_optimization_level_performance);
                shaderc_compile_options_set_include_callbacks(opt, aufloesen, freigeben, NULL);
                eingebunden.clear();
                long r = shaderc_compile_into_spv(compiler, text, art, d.toString(), "main", opt);
                try {
                    if (shaderc_result_get_compilation_status(r) != shaderc_compilation_status_success) {
                        System.err.println(shaderc_result_get_error_message(r));
                        throw new IllegalStateException("SPIR-V: " + d + " lässt sich nicht übersetzen");
                    }
                    ByteBuffer spv = shaderc_result_get_bytes(r);
                    byte[] b = new byte[spv.remaining()];
                    spv.get(b);
                    Path aus = ziel.resolve(quelle.relativize(d).toString() + ".spv");
                    Files.createDirectories(aus.getParent());
                    Files.write(aus, b);
                    // woraus gebaut: der Shader und alles, was er einbindet – Shader.laden nimmt das .spv nur, solange das stimmt
                    StringBuilder von = new StringBuilder(zeile(quelle, d));
                    for (Path p : eingebunden) von.append(zeile(quelle, p));
                    Files.writeString(Path.of(aus + ".von"), von, StandardCharsets.UTF_8);
                    n++;
                } finally {
                    shaderc_result_release(r);
                    shaderc_compile_options_release(opt);
                }
            }
        } finally {
            shaderc_compiler_release(compiler);
            aufloesen.free();
            freigeben.free();
        }
        System.out.printf("SPIR-V: %d Shader der Vulkan-Schichten übersetzt, %.0f ms%n", n, (System.nanoTime() - t0) / 1e6);
    }
}
