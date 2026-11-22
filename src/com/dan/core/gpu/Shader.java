package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * GLSL nach SPIR-V (Werkbuch HalligHooge Teil A, Abschnitt 6 und F10): mit shaderc aus LWJGL, Ziel Vulkan 1.3 und
 * SPIR-V 1.6. Aus P0 übernommen.
 * <ul>
 * <li>{@link #laden}: zuerst das vom Ant-Ziel {@code spirv} gebaute {@code <datei>.spv} aus dem Klassenpfad, wenn es
 * noch zu den Quellen passt ({@link #aktuell}); sonst die Quelle zur Laufzeit übersetzen (NetBeans übersetzt beim
 * Speichern ohne Ant).</li>
 * <li>{@code #include "/com/dan/…"} wird aus dem Klassenpfad aufgelöst, relativ ohne führenden Schrägstrich.</li>
 * <li>{@link #ausGl}: ein Compute-Shader des Bestands (GLSL 430 mit losen Uniforms, wie {@code bay_*.comp}) wird zu
 * Vulkan-GLSL 450: Die Uniforms werden ein Push-Konstanten-Block in derselben Reihenfolge. So lassen sich die Shader
 * der Element-Pakete beim Umbau weiterverwenden (F6).</li>
 * </ul>
 * Ein {@link Spirv} gehört dem Aufrufer; er gibt es mit {@link Spirv#frei} zurück, sobald der {@link Kernel} gebaut ist.
 */
public final class Shader {
    private Shader() { }

    /** Ergebnis: SPIR-V (vom Aufrufer mit {@link #frei} freigeben), Herkunft, Zeit der Übersetzung in ms. */
    public static final class Spirv {
        public final ByteBuffer code;
        public final String woher;
        public final double ms;
        /** Kam der Code vorgebaut aus dem Klassenpfad? */
        public final boolean vorgebaut;

        Spirv(ByteBuffer code, String woher, double ms, boolean vorgebaut) { this.code = code; this.woher = woher; this.ms = ms; this.vorgebaut = vorgebaut; }

        public void frei() { memFree(code); }
    }

    private static long compiler;
    private static ShadercIncludeResolve aufloesen;
    private static ShadercIncludeResultRelease freigeben;

    // ------------------------------------------------------------------ Laden

    /** Lädt einen Shader; {@code pfad} absolut im Klassenpfad, etwa "/com/dan/physics/hydro/gpu/fluss.comp"; Art aus der Endung. */
    public static Spirv laden(String pfad) {
        byte[] vor = bytes(pfad + ".spv"), von = bytes(pfad + ".spv.von");
        boolean alt = vor != null && !aktuell(von, Shader::bytes);
        if (vor != null && !alt) {
            ByteBuffer b = memAlloc(vor.length);
            b.put(vor).flip();
            return new Spirv(b, "vorgebaut (Ant)", 0, true);
        }
        String q = text(pfad);
        if (q == null) throw new IllegalStateException("Shader fehlt im Klassenpfad: " + pfad);
        Spirv s = uebersetzen(q, pfad, art(pfad));
        return alt ? new Spirv(s.code, "zur Laufzeit (das vorgebaute SPIR-V ist älter als die Quelle)", s.ms, false) : s;
    }

    /**
     * Passt ein vorgebautes SPIR-V noch zu seinen Quellen? {@code von} ist der Inhalt von {@code <datei>.spv.von}, den
     * das Ant-Ziel schreibt: je Zeile die SHA-256 und der Pfad einer Quelle (der Shader und alles, was er einbindet).
     * Stimmt eine Quelle nicht mehr oder fehlt die Liste, gilt das SPIR-V als veraltet – so läuft nach dem Speichern
     * in NetBeans (ohne Ant) nie ein alter Kernel.
     */
    public static boolean aktuell(byte[] von, java.util.function.Function<String, byte[]> quelle) {
        if (von == null) return false;
        int n = 0;
        for (String zeile : new String(von, StandardCharsets.UTF_8).split("\\R")) {
            if (zeile.isBlank()) continue;
            int l = zeile.indexOf(' ');
            if (l < 0) return false;
            byte[] q = quelle.apply(zeile.substring(l + 1).trim());
            if (q == null || !sha256(q).equals(zeile.substring(0, l))) return false;
            n++;
        }
        return n > 0;
    }

    /** SHA-256 als 64 Hex-Zeichen. */
    public static String sha256(byte[] b) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Spirv frisch(String pfad) {
        String q = text(pfad);
        if (q == null) throw new IllegalStateException("Shader fehlt im Klassenpfad: " + pfad);
        return uebersetzen(q, pfad, art(pfad));
    }

    public static int art(String datei) {
        if (datei.endsWith(".comp")) return shaderc_compute_shader;
        if (datei.endsWith(".vert")) return shaderc_vertex_shader;
        if (datei.endsWith(".frag")) return shaderc_fragment_shader;
        throw new IllegalArgumentException("Unbekannte Shader-Art: " + datei);
    }

    public static int stufe(int art) {
        if (art == shaderc_vertex_shader) return VK_SHADER_STAGE_VERTEX_BIT;
        if (art == shaderc_fragment_shader) return VK_SHADER_STAGE_FRAGMENT_BIT;
        return VK_SHADER_STAGE_COMPUTE_BIT;
    }

    /** Übersetzt quelle (Name für Meldungen und relative Includes); wirft mit der Meldung von shaderc. */
    public static synchronized Spirv uebersetzen(String quelle, String name, int art) {
        long t0 = System.nanoTime();
        if (compiler == NULL) {
            compiler = shaderc_compiler_initialize();
            if (compiler == NULL) throw new IllegalStateException("shaderc lässt sich nicht starten");
            aufloesen = ShadercIncludeResolve.create((nutzer, gewuenscht, typ, anfordernd, tiefe) -> {
                String w = memUTF8(gewuenscht), von = memUTF8(anfordernd);
                String pfad = w.startsWith("/") ? w : von.substring(0, von.lastIndexOf('/') + 1) + w;
                String t = text(pfad);
                ShadercIncludeResult r = ShadercIncludeResult.calloc();
                if (t == null) r.source_name(memUTF8("", false)).content(memUTF8("Include nicht gefunden: " + pfad, false));
                else r.source_name(memUTF8(pfad, false)).content(memUTF8(t, false));
                return r.address();
            });
            freigeben = ShadercIncludeResultRelease.create((nutzer, adresse) -> {
                ShadercIncludeResult r = ShadercIncludeResult.create(adresse);
                memFree(r.source_name());
                memFree(r.content());
                r.free();
            });
        }
        long opt = shaderc_compile_options_initialize();
        try {
            shaderc_compile_options_set_target_env(opt, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_3);
            shaderc_compile_options_set_target_spirv(opt, shaderc_spirv_version_1_6);
            shaderc_compile_options_set_optimization_level(opt, shaderc_optimization_level_performance);
            shaderc_compile_options_set_include_callbacks(opt, aufloesen, freigeben, NULL);
            long r = shaderc_compile_into_spv(compiler, quelle, art, name, "main", opt);
            try {
                if (shaderc_result_get_compilation_status(r) != shaderc_compilation_status_success)
                    throw new IllegalStateException(name + " lässt sich nicht übersetzen:\n" + shaderc_result_get_error_message(r));
                ByteBuffer spv = shaderc_result_get_bytes(r);
                ByteBuffer kopie = memAlloc(spv.remaining());
                memCopy(memAddress(spv), memAddress(kopie), spv.remaining());
                return new Spirv(kopie, "zur Laufzeit übersetzt", (System.nanoTime() - t0) / 1e6, false);
            } finally {
                shaderc_result_release(r);
            }
        } finally {
            shaderc_compile_options_release(opt);
        }
    }

    // ------------------------------------------------------------------ Bestand: GLSL 430 → Vulkan

    /** Ein Uniform des Bestands als Push-Konstante. */
    public static final class Wert {
        public final String name, typ;
        public final int versatz;

        Wert(String name, String typ, int versatz) { this.name = name; this.typ = typ; this.versatz = versatz; }
    }

    /** Ein umgesetzter Shader des Bestands: Vulkan-Quelle und die Push-Konstanten in Reihenfolge. */
    public static final class Gl {
        public final String quelle;
        public final List<Wert> werte;
        public final int groesse;
        public final boolean namenlos;

        Gl(String quelle, List<Wert> werte, int groesse, boolean namenlos) {
            this.quelle = quelle; this.werte = werte; this.groesse = groesse; this.namenlos = namenlos;
        }

        /** Push-Konstanten aus Paaren Name, Wert (Integer → int/uint, Number → float); fehlende bleiben 0. */
        public ByteBuffer packen(MemoryStack s, Object... nameWert) {
            ByteBuffer b = s.calloc(Math.max(4, groesse));
            for (int i = 0; i < nameWert.length; i += 2) {
                String n = (String) nameWert[i];
                Wert w = null;
                for (Wert x : werte) if (x.name.equals(n)) { w = x; break; }
                if (w == null) continue;                       // wie glGetUniformLocation = −1: still übergehen
                Object v = nameWert[i + 1];
                if (w.typ.equals("float")) b.putFloat(w.versatz, ((Number) v).floatValue());
                else b.putInt(w.versatz, ((Number) v).intValue());
            }
            return b;
        }
    }

    private static final Pattern UNIFORM = Pattern.compile("(?m)^[ \\t]*uniform[ \\t]+(int|uint|float)[ \\t]+([^;]+);[^\\n]*$");

    /**
     * GLSL 430 des Bestands (ohne #version, lose Uniforms vom Typ int, uint, float) nach Vulkan-GLSL 450. namenlos: Block
     * ohne Namen (die Namen bleiben, wie sie sind); sonst Block {@code werte} und jeder Gebrauch eines Namens wird
     * {@code werte.name} (Ersatz, falls der Übersetzer den namenlosen Block nicht nimmt).
     */
    public static Gl ausGl(String gl, boolean namenlos) {
        List<Wert> werte = new ArrayList<>();
        Matcher m = UNIFORM.matcher(gl);
        StringBuilder out = new StringBuilder();
        int versatz = 0, ende = 0;
        boolean erster = true;
        while (m.find()) {
            out.append(gl, ende, m.start());
            for (String n : m.group(2).split(",")) {
                werte.add(new Wert(n.trim(), m.group(1), versatz));
                versatz += 4;
            }
            out.append(erster ? "@@BLOCK@@" : "");
            erster = false;
            ende = m.end();
        }
        out.append(gl.substring(ende));
        StringBuilder block = new StringBuilder("layout(push_constant) uniform Werte {");
        for (Wert w : werte) block.append(' ').append(w.typ).append(' ').append(w.name).append(';');
        block.append(namenlos ? " };" : " } werte;");
        String q = out.toString().replace("@@BLOCK@@", block.toString());
        if (!namenlos) {
            // Jeden Gebrauch eines Namens (nicht nach einem Punkt, nicht im Block selbst) auf werte.name lenken
            int b0 = q.indexOf("layout(push_constant)"), b1 = q.indexOf("} werte;", b0) + 8;
            String vor = q.substring(0, b0), blk = q.substring(b0, b1), nach = q.substring(b1);
            for (Wert w : werte) nach = nach.replaceAll("(?<![.\\w])" + Pattern.quote(w.name) + "(?!\\w)", "werte." + w.name);
            q = vor + blk + nach;
        }
        return new Gl("#version 450\n" + q, werte, versatz, namenlos);
    }

    /** Ein Shader des Bestands, nach Vulkan umgesetzt und übersetzt. */
    public record Bestand(Gl gl, Spirv spirv) { }

    /** Lädt einen Shader des Bestands und übersetzt ihn; zuerst mit namenlosem Block, sonst mit dem Block {@code werte}. */
    public static Bestand bestand(String pfad) {
        String gl = text(pfad);
        if (gl == null) throw new IllegalStateException("Shader des Bestands fehlt im Klassenpfad: " + pfad);
        Gl a = ausGl(gl, true);
        try {
            return new Bestand(a, uebersetzen(a.quelle, pfad, shaderc_compute_shader));
        } catch (IllegalStateException e) {
            Gl b = ausGl(gl, false);
            return new Bestand(b, uebersetzen(b.quelle, pfad, shaderc_compute_shader));
        }
    }

    // ------------------------------------------------------------------ Klassenpfad

    public static String text(String pfad) {
        byte[] b = bytes(pfad);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    static byte[] bytes(String pfad) {
        try (InputStream in = Shader.class.getResourceAsStream(pfad)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(pfad + ": " + e.getMessage(), e);
        }
    }

}
