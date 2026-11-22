package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Ein Zeichner (seit P6a, Werkbuch HalligHooge Teil A, 12.7): eine Raster-Pipeline aus einem Scheitel- und einem
 * Bildpunkt-Shader (SPIR-V), mit dynamischem Rendern, also ohne Render-Pass-Objekte.
 * <ul>
 * <li><b>Keine Scheitel-Eingänge.</b> Der Scheitel-Shader holt seine Daten über {@code gl_VertexIndex} und
 * {@code gl_InstanceIndex} aus Speicherpuffern oder Texturen, wie ein Kernel. So gilt für Zeichner und Kernel
 * dieselbe Belegung (Satz 0, {@link Kernel.Bindung}) und dieselbe {@link Feldtafel} (Satz 1).</li>
 * <li><b>Push-Konstanten</b> gelten für beide Shader.</li>
 * <li><b>Ziel:</b> die Formate der Farbziele und, wenn gesetzt, das der Tiefe; ein Zeichner zeichnet nur in Ziele
 * dieser Formate. Aufgenommen wird er in {@link Ablauf#zeichne}.</li>
 * <li><b>Tiefe umgekehrt</b> (Werkbuch 8.1): Vorgabe ist der Vergleich „größer oder gleich“; die Hinterebene ist 0,
 * die vordere 1.</li>
 * </ul>
 */
public final class Zeichner implements AutoCloseable {
    /** Wie gezeichnet wird. Die Vorgaben passen zu einem Gelände: Dreiecke, beide Seiten, Tiefe umgekehrt mit Schreiben. */
    public static final class Art {
        /** Formate der Farbziele, der Reihe nach. */
        public int[] farben = {Bild.RGBA16F};
        /** Format der Tiefe oder {@code VK_FORMAT_UNDEFINED} (0): ohne Tiefe. */
        public int tiefe = Bild.D32F;
        public boolean tiefeTesten = true, tiefeSchreiben = true;
        public int vergleich = VK_COMPARE_OP_GREATER_OR_EQUAL;
        public int topologie = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        public int weglassen = VK_CULL_MODE_NONE;
        /** Gegen den Uhrzeigersinn ist vorn (im Bild, y nach unten). */
        public boolean vornGegen = true;
        /** Mischen mit vormultipliziertem Alpha: Ziel = Quelle + (1 − α) · Ziel. */
        public boolean mischen = false;
        public boolean linien = false;

        public Art ohneTiefe() {
            tiefe = VK_FORMAT_UNDEFINED;
            tiefeTesten = tiefeSchreiben = false;
            return this;
        }
    }

    final Geraet g;
    public final String name;
    final long layout, pipeline;
    public final int pushGroesse;
    public final Art art;
    private final Belegung belegung;
    private final Feldtafel tafel;

    /** Ein Zeichner aus dem Klassenpfad (vorgebaut oder zur Laufzeit übersetzt). */
    public static Zeichner lade(Geraet g, String scheitel, String punkt, int pushGroesse, Feldtafel tafel, Art art, Kernel.Bindung... bindungen) {
        Shader.Spirv v = Shader.laden(scheitel), f = Shader.laden(punkt);
        try {
            return new Zeichner(g, scheitel.substring(scheitel.lastIndexOf('/') + 1), v.code, f.code, pushGroesse, tafel, art, bindungen);
        } finally {
            v.frei();
            f.frei();
        }
    }

    public Zeichner(Geraet g, String name, ByteBuffer scheitel, ByteBuffer punkt, int pushGroesse, Feldtafel tafel, Art art, Kernel.Bindung... bindungen) {
        this.g = g;
        this.name = name;
        this.pushGroesse = pushGroesse;
        this.art = art;
        this.tafel = tafel;
        int stufen = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
        belegung = new Belegung(g, name, bindungen, stufen);
        try (MemoryStack s = stackPush()) {
            LongBuffer lb = s.mallocLong(1);
            Geraet.ok(vkCreateShaderModule(g.device, VkShaderModuleCreateInfo.calloc(s).sType$Default().pCode(scheitel), null, lb), "vkCreateShaderModule " + name);
            long mv = lb.get(0);
            Geraet.ok(vkCreateShaderModule(g.device, VkShaderModuleCreateInfo.calloc(s).sType$Default().pCode(punkt), null, lb), "vkCreateShaderModule " + name);
            long mf = lb.get(0);
            try {
                VkPipelineLayoutCreateInfo li = VkPipelineLayoutCreateInfo.calloc(s).sType$Default()
                        .pSetLayouts(tafel == null ? s.longs(belegung.layout) : s.longs(belegung.layout, tafel.layout));
                if (pushGroesse > 0) li.pPushConstantRanges(VkPushConstantRange.calloc(1, s).stageFlags(stufen).offset(0).size(pushGroesse));
                Geraet.ok(vkCreatePipelineLayout(g.device, li, null, lb), "vkCreatePipelineLayout " + name);
                layout = lb.get(0);

                VkPipelineShaderStageCreateInfo.Buffer st = VkPipelineShaderStageCreateInfo.calloc(2, s);
                st.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(mv).pName(s.UTF8("main"));
                st.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(mf).pName(s.UTF8("main"));
                VkPipelineColorBlendAttachmentState.Buffer mb = VkPipelineColorBlendAttachmentState.calloc(art.farben.length, s);
                for (int i = 0; i < art.farben.length; i++) {
                    mb.get(i).colorWriteMask(0xF).blendEnable(art.mischen);
                    if (art.mischen) mb.get(i).srcColorBlendFactor(VK_BLEND_FACTOR_ONE).dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).colorBlendOp(VK_BLEND_OP_ADD)
                            .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE).dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).alphaBlendOp(VK_BLEND_OP_ADD);
                }
                VkPipelineRenderingCreateInfo ri = VkPipelineRenderingCreateInfo.calloc(s).sType$Default().colorAttachmentCount(art.farben.length)
                        .pColorAttachmentFormats(s.ints(art.farben)).depthAttachmentFormat(art.tiefe);
                VkGraphicsPipelineCreateInfo.Buffer ci = VkGraphicsPipelineCreateInfo.calloc(1, s);
                ci.get(0).sType$Default().pNext(ri.address()).pStages(st)
                        .pVertexInputState(VkPipelineVertexInputStateCreateInfo.calloc(s).sType$Default())
                        .pInputAssemblyState(VkPipelineInputAssemblyStateCreateInfo.calloc(s).sType$Default().topology(art.topologie))
                        .pViewportState(VkPipelineViewportStateCreateInfo.calloc(s).sType$Default().viewportCount(1).scissorCount(1))
                        .pRasterizationState(VkPipelineRasterizationStateCreateInfo.calloc(s).sType$Default().polygonMode(art.linien ? VK_POLYGON_MODE_LINE : VK_POLYGON_MODE_FILL)
                                .cullMode(art.weglassen).frontFace(art.vornGegen ? VK_FRONT_FACE_COUNTER_CLOCKWISE : VK_FRONT_FACE_CLOCKWISE).lineWidth(1f))
                        .pMultisampleState(VkPipelineMultisampleStateCreateInfo.calloc(s).sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT))
                        .pDepthStencilState(VkPipelineDepthStencilStateCreateInfo.calloc(s).sType$Default().depthTestEnable(art.tiefeTesten).depthWriteEnable(art.tiefeSchreiben)
                                .depthCompareOp(art.vergleich))
                        .pColorBlendState(VkPipelineColorBlendStateCreateInfo.calloc(s).sType$Default().pAttachments(mb))
                        .pDynamicState(VkPipelineDynamicStateCreateInfo.calloc(s).sType$Default().pDynamicStates(s.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR)))
                        .layout(layout);
                Geraet.ok(vkCreateGraphicsPipelines(g.device, VK_NULL_HANDLE, ci, null, lb), "vkCreateGraphicsPipelines " + name);
                pipeline = lb.get(0);
            } finally {
                vkDestroyShaderModule(g.device, mv, null);
                vkDestroyShaderModule(g.device, mf, null);
            }
        }
    }

    /** Der Deskriptorsatz für diese Belegung (wie {@link Kernel#satz}). */
    public long satz(Object... belegung) { return this.belegung.satz(belegung); }

    /**
     * Zeichnet {@code scheitel} Scheitel in {@code instanzen} Instanzen. Muss innerhalb von {@link Ablauf#zeichne}
     * aufgenommen werden (dort sind Ziele, Bildfeld und Schere gesetzt).
     */
    public void zeichne(VkCommandBuffer c, long satz, ByteBuffer push, int scheitel, int instanzen) { zeichne(c, satz, push, scheitel, instanzen, 0); }

    /** Wie oben, ab dem Scheitel {@code erster} (seit P12b: Häuser und Körper getrennt schaltbar). */
    public void zeichne(VkCommandBuffer c, long satz, ByteBuffer push, int scheitel, int instanzen, int erster) {
        zeichne(c, satz, push, scheitel, instanzen, erster, 0);
    }

    /** Wie oben, dazu die erste Instanz (seit P12c: gl_InstanceIndex beginnt dort; Listen von Instanzen in einem Puffer). */
    public void zeichne(VkCommandBuffer c, long satz, ByteBuffer push, int scheitel, int instanzen, int erster, int ersteInstanz) {
        try (MemoryStack s = stackPush()) {
            vkCmdBindPipeline(c, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            if (belegung.bindungen.length > 0) vkCmdBindDescriptorSets(c, VK_PIPELINE_BIND_POINT_GRAPHICS, layout, 0, s.longs(satz), null);
            if (tafel != null) vkCmdBindDescriptorSets(c, VK_PIPELINE_BIND_POINT_GRAPHICS, layout, 1, s.longs(tafel.satz), null);
            if (push != null && pushGroesse > 0) {
                ByteBuffer p = push.duplicate();
                p.limit(Math.min(p.capacity(), pushGroesse)).position(0);
                vkCmdPushConstants(c, layout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, p);
            }
            vkCmdDraw(c, scheitel, instanzen, erster, ersteInstanz);
        }
    }

    @Override public void close() {
        vkDestroyPipeline(g.device, pipeline, null);
        vkDestroyPipelineLayout(g.device, layout, null);
        belegung.close();
    }
}
