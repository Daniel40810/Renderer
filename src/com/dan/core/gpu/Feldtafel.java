package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Die Feldtafel (Werkbuch HalligHooge Teil A, Abschnitt 6: „bindlose Deskriptoren“): ein Deskriptorsatz mit
 * {@link #PLAETZE} Plätzen für Speicherpuffer. Ein Puffer wird einmal eingetragen und bekommt eine Nummer; ein Kernel
 * mit Feldtafel (Satz 1) wählt seine Felder über Push-Konstanten, ohne dass je Belegung ein Deskriptorsatz entsteht.
 * Das lohnt sich, wo derselbe Kernel über viele Gitter und Felder läuft (Schachteln).
 * <p>
 * Im Shader:
 * <pre>
 * #include "/com/dan/core/gpu/feldtafel.glsl"      // float feld[nummer].w[i]
 * </pre>
 * Plätze dürfen belegt und geräumt werden, während andere in Arbeit sind. Braucht die Fähigkeit
 * {@code deskriptorIndizes}.
 */
public final class Feldtafel implements AutoCloseable {
    /** Zahl der Plätze [G]. */
    public static final int PLAETZE = 1024;

    final Geraet g;
    final long layout, pool, satz;
    private final Puffer[] platz = new Puffer[PLAETZE];

    public Feldtafel(Geraet g) {
        if (!g.kann.deskriptorIndizes()) throw new IllegalStateException(g.name + " kann keine bindlosen Deskriptoren (Deskriptor-Indizes)");
        this.g = g;
        try (MemoryStack s = stackPush()) {
            LongBuffer lb = s.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer b = VkDescriptorSetLayoutBinding.calloc(1, s);
            b.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(PLAETZE).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT | VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutBindingFlagsCreateInfo fl = VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(s).sType$Default()
                    .pBindingFlags(s.ints(VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT | VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT));
            Geraet.ok(vkCreateDescriptorSetLayout(g.device, VkDescriptorSetLayoutCreateInfo.calloc(s).sType$Default().pNext(fl.address())
                    .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT).pBindings(b), null, lb), "vkCreateDescriptorSetLayout Feldtafel");
            layout = lb.get(0);
            VkDescriptorPoolSize.Buffer ps = VkDescriptorPoolSize.calloc(1, s).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(PLAETZE);
            Geraet.ok(vkCreateDescriptorPool(g.device, VkDescriptorPoolCreateInfo.calloc(s).sType$Default().flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT).maxSets(1).pPoolSizes(ps),
                    null, lb), "vkCreateDescriptorPool Feldtafel");
            pool = lb.get(0);
            Geraet.ok(vkAllocateDescriptorSets(g.device, VkDescriptorSetAllocateInfo.calloc(s).sType$Default().descriptorPool(pool).pSetLayouts(s.longs(layout)), lb),
                    "vkAllocateDescriptorSets Feldtafel");
            satz = lb.get(0);
        }
    }

    /** Trägt einen Puffer ein und gibt seine Nummer zurück; ein schon eingetragener behält seine Nummer. */
    public synchronized int trage(Puffer p) {
        int frei = -1;
        for (int i = 0; i < PLAETZE; i++) {
            if (platz[i] == p) return i;
            if (platz[i] == null && frei < 0) frei = i;
        }
        if (frei < 0) throw new IllegalStateException("Feldtafel voll (" + PLAETZE + " Plätze)");
        try (MemoryStack s = stackPush()) {
            VkWriteDescriptorSet.Buffer w = VkWriteDescriptorSet.calloc(1, s);
            w.get(0).sType$Default().dstSet(satz).dstBinding(0).dstArrayElement(frei).descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, s).buffer(p.handle).offset(0).range(VK_WHOLE_SIZE));
            vkUpdateDescriptorSets(g.device, w, null);
        }
        platz[frei] = p;
        return frei;
    }

    /** Räumt einen Platz. Arbeit, die ihn noch benutzt, muss fertig sein. */
    public synchronized void streiche(int nummer) { platz[nummer] = null; }

    /** Zahl der belegten Plätze. */
    public synchronized int belegt() {
        int n = 0;
        for (Puffer p : platz) if (p != null) n++;
        return n;
    }

    @Override public void close() {
        vkDestroyDescriptorPool(g.device, pool, null);
        vkDestroyDescriptorSetLayout(g.device, layout, null);
    }
}
