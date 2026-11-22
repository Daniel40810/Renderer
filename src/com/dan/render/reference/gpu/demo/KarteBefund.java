package com.dan.render.reference.gpu.demo;

import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Shader;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.gpu.ReferenzKarte;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Befund zur Geschwindigkeit der Referenz Stufe 2 auf der Karte (P11a, nach Lauf A2/A3: 477 Pfade/s, 256 Pfade in
 * 575–713 ms). Teil 1: was der Treiber aus den Kerneln macht – mit {@code VK_AMD_shader_info} Register, Scratch und
 * Maschinencode (in {@code docs/status/P11a_Befund_<kernel>.isa.txt}), sonst mit {@code VK_KHR_pipeline_executable_properties}.
 * Teil 2: einzelne Aufrufe von klein nach groß (Wasserflug, Himmel über der Kugel, Ofentest), jeder zweimal; über
 * 400 ms wird die Reihe abgebrochen (Windows bricht nach 2 s ab). Schreibt {@code docs/status/P11a_Befund.md}.
 */
public final class KarteBefund {
    static final StringBuilder B = new StringBuilder();

    static void z(String s) {
        System.out.println(s);
        System.out.flush();
        B.append(s).append('\n');
    }

    public static void main(String[] args) throws Exception {
        z("# P11a – Befund zur Geschwindigkeit der Referenz Stufe 2\n");
        try {
            shaderInfo(List.of("/com/dan/render/reference/gpu/pfad_ebenen.comp", "/com/dan/render/reference/gpu/pfad_ebenen.comp|32",
                    "/com/dan/render/reference/gpu/pfad_luft.comp", "/com/dan/render/reference/gpu/pfad_luft.comp|32", "/com/dan/render/reference/gpu/tafeln.comp"));
        } catch (Throwable e) {
            z("Teil 1 abgebrochen: " + e);
        }
        try {
            aufrufe();
        } catch (Throwable e) {
            z("Teil 2 abgebrochen: " + e);
        }
        try {
            stufe1();
        } catch (Throwable e) {
            z("Teil 3 abgebrochen: " + e);
        }
        Files.createDirectories(Path.of("docs", "status"));
        Files.writeString(Path.of("docs", "status", "P11a_Befund.md"), B.toString());
        System.out.println("\nBefund: docs/status/P11a_Befund.md");
    }

    // ------------------------------------------------------------------ Teil 1

    static void shaderInfo(List<String> kernel) throws Exception {
        z("## Teil 1: Kernel im Treiber\n");
        try (MemoryStack s = stackPush()) {
            VkApplicationInfo ai = VkApplicationInfo.calloc(s).sType$Default().apiVersion(VK_API_VERSION_1_3);
            VkInstanceCreateInfo ici = VkInstanceCreateInfo.calloc(s).sType$Default().pApplicationInfo(ai);
            PointerBuffer pp = s.mallocPointer(1);
            ok(vkCreateInstance(ici, null, pp), "vkCreateInstance");
            VkInstance inst = new VkInstance(pp.get(0), ici);
            IntBuffer n = s.mallocInt(1);
            vkEnumeratePhysicalDevices(inst, n, null);
            PointerBuffer pd = s.mallocPointer(n.get(0));
            vkEnumeratePhysicalDevices(inst, n, pd);
            VkPhysicalDevice phys = new VkPhysicalDevice(pd.get(0), inst);
            VkPhysicalDeviceProperties pr = VkPhysicalDeviceProperties.calloc(s);
            vkGetPhysicalDeviceProperties(phys, pr);
            vkEnumerateDeviceExtensionProperties(phys, (ByteBuffer) null, n, null);
            VkExtensionProperties.Buffer ep = VkExtensionProperties.calloc(n.get(0), s);
            vkEnumerateDeviceExtensionProperties(phys, (ByteBuffer) null, n, ep);
            Set<String> ext = new HashSet<>();
            for (int i = 0; i < n.get(0); i++) ext.add(ep.get(i).extensionNameString());
            boolean amd = ext.contains("VK_AMD_shader_info"), khr = ext.contains("VK_KHR_pipeline_executable_properties");
            z("Gerät: " + pr.deviceNameString() + "; VK_AMD_shader_info " + (amd ? "ja" : "nein") + ", VK_KHR_pipeline_executable_properties " + (khr ? "ja" : "nein") + "\n");
            PointerBuffer en = s.mallocPointer(2);
            int ne = 0;
            if (amd) en.put(ne++, s.UTF8("VK_AMD_shader_info"));
            if (khr) en.put(ne++, s.UTF8("VK_KHR_pipeline_executable_properties"));
            en.limit(ne);
            VkDeviceQueueCreateInfo.Buffer q = VkDeviceQueueCreateInfo.calloc(1, s);
            q.get(0).sType$Default().queueFamilyIndex(0).pQueuePriorities(s.floats(1f));
            VkPhysicalDevicePipelineExecutablePropertiesFeaturesKHR pe = VkPhysicalDevicePipelineExecutablePropertiesFeaturesKHR.calloc(s).sType$Default().pipelineExecutableInfo(khr);
            // Größe der Wellen (32 oder 64 Fäden) vorgeben können: subgroupSizeControl aus Vulkan 1.3
            VkPhysicalDeviceVulkan13Features f13 = VkPhysicalDeviceVulkan13Features.calloc(s).sType$Default();
            VkPhysicalDeviceFeatures2 f2 = VkPhysicalDeviceFeatures2.calloc(s).sType$Default().pNext(f13);
            vkGetPhysicalDeviceFeatures2(phys, f2);
            boolean wahl = f13.subgroupSizeControl();
            VkPhysicalDeviceVulkan13Properties p13 = VkPhysicalDeviceVulkan13Properties.calloc(s).sType$Default();
            vkGetPhysicalDeviceProperties2(phys, VkPhysicalDeviceProperties2.calloc(s).sType$Default().pNext(p13));
            z("Wellen: " + p13.minSubgroupSize() + " bis " + p13.maxSubgroupSize() + " Fäden, vorgebbar " + (wahl && (p13.requiredSubgroupSizeStages() & VK_SHADER_STAGE_COMPUTE_BIT) != 0 ? "ja" : "nein") + "\n");
            VkPhysicalDeviceVulkan13Features an = VkPhysicalDeviceVulkan13Features.calloc(s).sType$Default().subgroupSizeControl(wahl);
            VkDeviceCreateInfo dci = VkDeviceCreateInfo.calloc(s).sType$Default().pQueueCreateInfos(q).ppEnabledExtensionNames(en).pNext(an);
            if (khr) an.pNext(pe.address());
            ok(vkCreateDevice(phys, dci, null, pp), "vkCreateDevice");
            VkDevice dev = new VkDevice(pp.get(0), phys, dci);
            for (String kw : kernel) {
                String k = kw.contains("|") ? kw.substring(0, kw.indexOf('|')) : kw;
                int welle = kw.contains("|") ? Integer.parseInt(kw.substring(kw.indexOf('|') + 1)) : 0;
                if (welle > 0 && !(wahl && welle >= p13.minSubgroupSize() && welle <= p13.maxSubgroupSize())) {
                    z("### " + k + ", Wellen zu " + welle + ": nicht vorgebbar\n");
                    continue;
                }
                Shader.Spirv sp = Shader.laden(k);
                LongBuffer lb = s.mallocLong(1);
                ok(vkCreateShaderModule(dev, VkShaderModuleCreateInfo.calloc(s).sType$Default().pCode(sp.code), null, lb), "vkCreateShaderModule");
                long mod = lb.get(0);
                VkDescriptorSetLayoutBinding.Buffer bi = VkDescriptorSetLayoutBinding.calloc(8, s);   // 8: auch die Wellenfront
                for (int i = 0; i < 8; i++) bi.get(i).binding(i).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
                ok(vkCreateDescriptorSetLayout(dev, VkDescriptorSetLayoutCreateInfo.calloc(s).sType$Default().pBindings(bi), null, lb), "dsl");
                long dsl = lb.get(0);
                VkPushConstantRange.Buffer pcr = VkPushConstantRange.calloc(1, s);
                pcr.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).size(128);
                ok(vkCreatePipelineLayout(dev, VkPipelineLayoutCreateInfo.calloc(s).sType$Default().pSetLayouts(s.longs(dsl)).pPushConstantRanges(pcr), null, lb), "pl");
                long pl = lb.get(0);
                VkComputePipelineCreateInfo.Buffer ci = VkComputePipelineCreateInfo.calloc(1, s);
                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(s).sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(mod).pName(s.UTF8("main"));
                if (welle > 0) stage.pNext(VkPipelineShaderStageRequiredSubgroupSizeCreateInfo.calloc(s).sType$Default().requiredSubgroupSize(welle));
                ci.get(0).sType$Default().flags(khr ? KHRPipelineExecutableProperties.VK_PIPELINE_CREATE_CAPTURE_STATISTICS_BIT_KHR : 0).layout(pl).stage(stage);
                long t0 = System.nanoTime();
                int r = vkCreateComputePipelines(dev, VK_NULL_HANDLE, ci, null, lb);
                long pipe = lb.get(0);
                z("### " + k + (welle > 0 ? ", Wellen zu " + welle : ", Wellen nach Treiber") + "\n\nSPIR-V " + sp.code.remaining() + " Byte; Pipeline " + r + " in " + String.format("%.1f", (System.nanoTime() - t0) / 1e9) + " s");
                if (amd) {
                    PointerBuffer gr = s.mallocPointer(1);
                    gr.put(0, VkShaderStatisticsInfoAMD.SIZEOF);
                    ByteBuffer sb0 = s.calloc(VkShaderStatisticsInfoAMD.ALIGNOF, VkShaderStatisticsInfoAMD.SIZEOF);
                    VkShaderStatisticsInfoAMD st = VkShaderStatisticsInfoAMD.create(MemoryUtil.memAddress(sb0));
                    int rr = AMDShaderInfo.vkGetShaderInfoAMD(dev, pipe, VK_SHADER_STAGE_COMPUTE_BIT, AMDShaderInfo.VK_SHADER_INFO_TYPE_STATISTICS_AMD, gr, sb0);
                    z(String.format("AMD: VGPR %d von %d (verfügbar %d), SGPR %d von %d, Scratch %d Byte je Faden, LDS %d Byte, Wellen je Recheneinheit %d (Ergebnis %d)",
                            st.resourceUsage().numUsedVgprs(), st.numPhysicalVgprs(), st.numAvailableVgprs(), st.resourceUsage().numUsedSgprs(), st.numPhysicalSgprs(),
                            st.resourceUsage().scratchMemUsageInBytes(), st.resourceUsage().ldsUsageSizeInBytes(), st.computeWorkGroupSize(0), rr));
                    gr.put(0, 0);
                    AMDShaderInfo.vkGetShaderInfoAMD(dev, pipe, VK_SHADER_STAGE_COMPUTE_BIT, AMDShaderInfo.VK_SHADER_INFO_TYPE_DISASSEMBLY_AMD, gr, null);
                    long gross = gr.get(0);
                    if (gross > 0) {
                        ByteBuffer d = MemoryUtil.memAlloc((int) gross);
                        AMDShaderInfo.vkGetShaderInfoAMD(dev, pipe, VK_SHADER_STAGE_COMPUTE_BIT, AMDShaderInfo.VK_SHADER_INFO_TYPE_DISASSEMBLY_AMD, gr, d);
                        byte[] b = new byte[(int) gr.get(0)];
                        d.get(b);
                        String name = k.substring(k.lastIndexOf('/') + 1).replace('.', '_') + (welle > 0 ? "_w" + welle : "");
                        Files.createDirectories(Path.of("docs", "status"));
                        Path f = Path.of("docs", "status", "P11a_Befund_" + name + ".isa.txt");
                        Files.write(f, b);
                        String isa = new String(b);
                        long zeilen = isa.lines().count(), scratch = isa.lines().filter(l -> l.contains("scratch_") || l.contains("buffer_store_dword") && l.contains("offen")).count();
                        z("Maschinencode " + b.length + " Byte, " + zeilen + " Zeilen, Zugriffe auf Scratch rund " + scratch + "; Datei " + f);
                        MemoryUtil.memFree(d);
                    }
                }
                if (khr) {
                    VkPipelineExecutableInfoKHR ei = VkPipelineExecutableInfoKHR.calloc(s).sType$Default().pipeline(pipe).executableIndex(0);
                    IntBuffer cnt = s.mallocInt(1);
                    KHRPipelineExecutableProperties.vkGetPipelineExecutableStatisticsKHR(dev, ei, cnt, null);
                    VkPipelineExecutableStatisticKHR.Buffer st = VkPipelineExecutableStatisticKHR.calloc(cnt.get(0), s);
                    for (int i = 0; i < cnt.get(0); i++) st.get(i).sType$Default();
                    KHRPipelineExecutableProperties.vkGetPipelineExecutableStatisticsKHR(dev, ei, cnt, st);
                    StringBuilder sb = new StringBuilder("KHR: ");
                    for (int i = 0; i < cnt.get(0); i++) {
                        VkPipelineExecutableStatisticKHR x = st.get(i);
                        String v = switch (x.format()) {
                            case KHRPipelineExecutableProperties.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_UINT64_KHR -> "" + x.value().u64();
                            case KHRPipelineExecutableProperties.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_FLOAT64_KHR -> "" + x.value().f64();
                            case KHRPipelineExecutableProperties.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_INT64_KHR -> "" + x.value().i64();
                            default -> "" + x.value().b32();
                        };
                        sb.append(x.nameString()).append(' ').append(v).append("; ");
                    }
                    z(sb.toString());
                }
                z("");
                vkDestroyPipeline(dev, pipe, null);
                vkDestroyPipelineLayout(dev, pl, null);
                vkDestroyDescriptorSetLayout(dev, dsl, null);
                vkDestroyShaderModule(dev, mod, null);
                sp.frei();
            }
            vkDestroyDevice(dev, null);
            vkDestroyInstance(inst, null);
        }
    }

    static void ok(int r, String was) { if (r != VK_SUCCESS) throw new IllegalStateException(was + ": " + r); }

    // ------------------------------------------------------------------ Teil 2

    static void aufrufe() {
        z("## Teil 2: einzelne Aufrufe (ms auf der Karte, je zweimal)\n");
        try (Geraet g = new Geraet()) {
            Konto konto = g.konto("KarteBefund", 256L << 20);
            try (ReferenzKarte rk = new ReferenzKarte(g, konto)) {
                Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
                // Wasserflug: kurze Schleife, misst die Karte ohne den Pfadverfolger
                Wasseroptik wa = new Wasseroptik();
                wa.schwebstoff = 0;
                Referenz rw = new Referenz(new Ebenen(-1000, Double.NaN, new double[] {0, 0, 0}, 0), luft, wa, 0);
                rw.spektral = false;
                rk.schalter().uebernimm(rw).weg(2.0);
                reihe(rk, "Wasserflug (2 m, ohne Pfadverfolger)", ReferenzKarte.MODUS_WASSERFLUG, new int[] {1024, 16384, 65536}, 16);
                // Himmel über der Kugel
                Ebenen e3 = new Ebenen(-10, Double.NaN, new double[] {0.08, 0.08, 0.08}, 10);
                e3.kugel = true;
                Referenz r3 = new Referenz(e3, luft, new Wasseroptik(), 0);
                r3.lichter.add(ReferenceKarteCheck.sonne(40, 0));
                rk.schalter().uebernimm(r3).strahl(new double[3], ReferenceKarteCheck.richtung(10, 90));
                reihe(rk, "Himmel über der Kugel, Sonne 40°", ReferenzKarte.MODUS_RICHTUNG, new int[] {64, 256, 1024, 4096, 16384, 65536}, 1);
                // Ofentest
                Referenz ro = new Referenz(new Ebenen(-12, -10, new double[] {1, 1, 1}, 0), luft, new Wasseroptik(), 0);
                ro.sigma2 = 1e-6;
                rk.schalter().uebernimm(ro).ofen().strahl(new double[3], ReferenceKarteCheck.norm(new double[] {0, -0.5, -0.866}));
                reihe(rk, "Ofentest, glattes Wasser über Grund", ReferenzKarte.MODUS_RICHTUNG, new int[] {64, 256, 1024, 4096}, 1);
                // Ofentest ohne Wasser: nur Luft und Boden
                Referenz rb = new Referenz(new Ebenen(-12, Double.NaN, new double[] {1, 1, 1}, 0), luft, new Wasseroptik(), 0);
                rk.schalter().uebernimm(rb).ofen().strahl(new double[3], ReferenceKarteCheck.norm(new double[] {0, -0.5, -0.866}));
                reihe(rk, "Ofentest ohne Wasser (Luft und Boden)", ReferenzKarte.MODUS_RICHTUNG, new int[] {64, 256, 1024, 4096}, 1);
            }
            // Derselbe Pfadverfolger ohne den Weg ins Wasser (pfad_luft.comp): wie viel kostet die Größe des Kernels?
            z("\nMit pfad_luft.comp (ohne Wasser):\n");
            try (ReferenzKarte rk = new ReferenzKarte(g, konto, "/com/dan/render/reference/gpu/pfad_luft.comp")) {
                Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
                Ebenen e3 = new Ebenen(-10, Double.NaN, new double[] {0.08, 0.08, 0.08}, 10);
                e3.kugel = true;
                Referenz r3 = new Referenz(e3, luft, new Wasseroptik(), 0);
                r3.lichter.add(ReferenceKarteCheck.sonne(40, 0));
                rk.schalter().uebernimm(r3).strahl(new double[3], ReferenceKarteCheck.richtung(10, 90));
                reihe(rk, "Himmel über der Kugel, Sonne 40° (pfad_luft)", ReferenzKarte.MODUS_RICHTUNG, new int[] {1024, 4096, 16384, 65536}, 1);
                Referenz rb = new Referenz(new Ebenen(-12, Double.NaN, new double[] {1, 1, 1}, 0), luft, new Wasseroptik(), 0);
                rk.schalter().uebernimm(rb).ofen().strahl(new double[3], ReferenceKarteCheck.norm(new double[] {0, -0.5, -0.866}));
                reihe(rk, "Ofentest ohne Wasser (pfad_luft)", ReferenzKarte.MODUS_RICHTUNG, new int[] {1024, 4096, 16384}, 1);
            }
        }
    }

    // ------------------------------------------------------------------ Teil 3

    /** Stufe 1 auf allen Kernen des Prozessors, dieselben Szenen wie Teil 2: Pfade/s, je rund 4 s. */
    static void stufe1() throws Exception {
        int n = Runtime.getRuntime().availableProcessors();
        z("\n## Teil 3: Stufe 1 auf dem Prozessor (" + n + " Fäden, je rund 4 s)\n");
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        Ebenen e3 = new Ebenen(-10, Double.NaN, new double[] {0.08, 0.08, 0.08}, 10);
        e3.kugel = true;
        Referenz r3 = new Referenz(e3, luft, new Wasseroptik(), 0);
        r3.lichter.add(ReferenceKarteCheck.sonne(40, 0));
        stufe1(n, "Himmel über der Kugel, Sonne 40°", r3, false, ReferenceKarteCheck.richtung(10, 90));
        Referenz ro = new Referenz(new Ebenen(-12, -10, new double[] {1, 1, 1}, 0), luft, new Wasseroptik(), 0);
        ro.sigma2 = 1e-6;
        stufe1(n, "Ofentest, glattes Wasser über Grund", ro, true, ReferenceKarteCheck.norm(new double[] {0, -0.5, -0.866}));
        Referenz rb = new Referenz(new Ebenen(-12, Double.NaN, new double[] {1, 1, 1}, 0), luft, new Wasseroptik(), 0);
        stufe1(n, "Ofentest ohne Wasser (Luft und Boden)", rb, true, ReferenceKarteCheck.norm(new double[] {0, -0.5, -0.866}));
    }

    static double stufe1(int n, String name, Referenz ref, boolean ofen, double[] d) throws Exception {
        java.util.concurrent.atomic.AtomicLong zahl = new java.util.concurrent.atomic.AtomicLong();
        long ende = System.nanoTime() + 4_000_000_000L, t0 = System.nanoTime();
        Thread[] th = new Thread[n];
        for (int i = 0; i < n; i++) {
            final long saat = 1000 + i;
            th[i] = new Thread(() -> {
                com.dan.render.reference.Pfadverfolger pv = ref.verfolger(saat);
                if (ofen) pv.ofen();
                long z = 0;
                while (System.nanoTime() < ende) {
                    for (int j = 0; j < 64; j++) {
                        pv.neueWellen();
                        pv.verfolge(new double[3], d.clone());
                    }
                    z += 64;
                }
                zahl.addAndGet(z);
            });
            th[i].start();
        }
        for (Thread t : th) t.join();
        double sek = (System.nanoTime() - t0) / 1e9;
        z(String.format("- %s: %d Pfade in %.1f s, %.3g Pfade/s", name, zahl.get(), sek, zahl.get() / sek));
        return zahl.get() / sek;
    }

    static void reihe(ReferenzKarte rk, String name, int modus, int[] faeden, int pfade) {
        StringBuilder sb = new StringBuilder("- " + name + ": ");
        rk.einAufruf(modus, 64, pfade, 0);                                 // Aufwärmen: der Treiber übersetzt beim ersten Aufruf
        for (int f : faeden) {
            double a = rk.einAufruf(modus, f, pfade, 1), b = rk.einAufruf(modus, f, pfade, 2);
            sb.append(String.format("%d × %d: %.1f / %.1f ms (%.3g Pfade/s)%s; ", f, pfade, a, b, f * pfade / (b / 1000), rk.abgebrochen + rk.nan > 0 ? ", " + rk.abgebrochen + " abgebrochen, " + rk.nan + " NaN" : ""));
            System.out.println("           " + name + ": " + f + " × " + pfade + ": " + String.format("%.1f / %.1f ms", a, b));
            System.out.flush();
            if (Math.max(a, b) > 400) {
                sb.append("abgebrochen (über 400 ms)");
                break;
            }
        }
        z(sb.toString());
    }
}
