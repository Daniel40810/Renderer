package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Ein Kernel (Werkbuch HalligHooge Teil A, Abschnitt 6): eine Rechen-Pipeline aus SPIR-V mit Push-Konstanten.
 * <ul>
 * <li><b>Satz 0</b> hat feste Bindungen in der Reihenfolge des Shaders: Speicherpuffer, Speicherbild, Textur oder
 * Beschleunigungsstruktur. {@link #satz} legt je Belegung einen Deskriptorsatz an und gibt ihn bei gleicher Belegung
 * wieder.</li>
 * <li><b>Satz 1</b> ist, wenn gewünscht, die {@link Feldtafel}: alle eingetragenen Puffer unter einer Nummer
 * (bindlos). Der Kernel wählt die Felder dann über Push-Konstanten.</li>
 * </ul>
 * Aufgenommen wird ein Kernel in einem {@link Ablauf}; der setzt die Sperren zwischen den Durchgängen.
 */
public final class Kernel implements AutoCloseable {
    /** Art einer Bindung in Satz 0. */
    public enum Bindung {
        PUFFER(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER), BILD(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE), STRAHLEN(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR),
        /** Bild mit {@link Abtaster} zum Lesen ({@code sampler2D}); belegt mit {@link Abtaster.Textur}, seit P6a. */
        TEXTUR(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        final int typ;
        Bindung(int typ) { this.typ = typ; }
    }

    /** Laufende Nummern für Puffer und Bilder (für die Sätze: Handles werden wiederverwendet, Nummern nicht). */
    static final java.util.concurrent.atomic.AtomicLong NUMMER = new java.util.concurrent.atomic.AtomicLong();

    final Geraet g;
    public final String name;
    final long modul, satzLayout, layout, pipeline;
    private final Bindung[] bindungen;
    private final Belegung belegung;
    public final int pushGroesse;
    private final Feldtafel tafel;

    /** Ein Kernel aus dem Klassenpfad (vorgebaut oder zur Laufzeit übersetzt). {@code tafel} ist null oder die Feldtafel für Satz 1. */
    public static Kernel lade(Geraet g, String pfad, int pushGroesse, Feldtafel tafel, Bindung... bindungen) {
        Shader.Spirv s = Shader.laden(pfad);
        try {
            return new Kernel(g, pfad.substring(pfad.lastIndexOf('/') + 1), s.code, pushGroesse, tafel, bindungen);
        } finally {
            s.frei();
        }
    }

    public Kernel(Geraet g, String name, ByteBuffer spirv, int pushGroesse, Feldtafel tafel, Bindung... bindungen) {
        this.g = g;
        this.name = name;
        this.bindungen = bindungen.clone();
        this.pushGroesse = pushGroesse;
        this.tafel = tafel;
        try (MemoryStack s = stackPush()) {
            LongBuffer lb = s.mallocLong(1);
            Geraet.ok(vkCreateShaderModule(g.device, VkShaderModuleCreateInfo.calloc(s).sType$Default().pCode(spirv), null, lb), "vkCreateShaderModule " + name);
            modul = lb.get(0);

            belegung = new Belegung(g, name, bindungen, VK_SHADER_STAGE_COMPUTE_BIT);
            satzLayout = belegung.layout;

            VkPipelineLayoutCreateInfo li = VkPipelineLayoutCreateInfo.calloc(s).sType$Default().pSetLayouts(tafel == null ? s.longs(satzLayout) : s.longs(satzLayout, tafel.layout));
            if (pushGroesse > 0) li.pPushConstantRanges(VkPushConstantRange.calloc(1, s).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(pushGroesse));
            Geraet.ok(vkCreatePipelineLayout(g.device, li, null, lb), "vkCreatePipelineLayout " + name);
            layout = lb.get(0);

            VkComputePipelineCreateInfo.Buffer ci = VkComputePipelineCreateInfo.calloc(1, s);
            ci.get(0).sType$Default().layout(layout)
                    .stage(VkPipelineShaderStageCreateInfo.calloc(s).sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(modul).pName(s.UTF8("main")));
            Geraet.ok(vkCreateComputePipelines(g.device, VK_NULL_HANDLE, ci, null, lb), "vkCreateComputePipelines " + name);
            pipeline = lb.get(0);
        }
    }

    /**
     * Der Deskriptorsatz für diese Belegung: je Bindung ein {@link Puffer}, ein {@link Bild} oder (für eine
     * Beschleunigungsstruktur) deren Handle als {@code Long}. Gleiche Belegung gibt denselben Satz.
     */
    public long satz(Object... belegung) { return this.belegung.satz(belegung); }

    /**
     * Nimmt den Kernel auf: Pipeline, Sätze, Push-Konstanten (oder null) und die Ausführung in gx · gy · gz Gruppen.
     * Die Sperren davor setzt der {@link Ablauf}.
     */
    public void aufnehmen(VkCommandBuffer c, long satz, ByteBuffer push, int gx, int gy, int gz) {
        try (MemoryStack s = stackPush()) {
            vkCmdBindPipeline(c, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            if (bindungen.length > 0) vkCmdBindDescriptorSets(c, VK_PIPELINE_BIND_POINT_COMPUTE, layout, 0, s.longs(satz), null);
            if (tafel != null) vkCmdBindDescriptorSets(c, VK_PIPELINE_BIND_POINT_COMPUTE, layout, 1, s.longs(tafel.satz), null);
            if (push != null && pushGroesse > 0) {
                ByteBuffer p = push.duplicate();
                p.limit(Math.min(p.capacity(), pushGroesse)).position(0);
                vkCmdPushConstants(c, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, p);
            }
            vkCmdDispatch(c, gx, gy, gz);
        }
    }

    /** Zahl der Gruppen für n Fäden bei einer Gruppengröße (aufgerundet). */
    public static int gruppen(int faeden, int gruppe) { return (faeden + gruppe - 1) / gruppe; }

    @Override public void close() {
        vkDestroyPipeline(g.device, pipeline, null);
        vkDestroyPipelineLayout(g.device, layout, null);
        belegung.close();
        vkDestroyShaderModule(g.device, modul, null);
    }
}
