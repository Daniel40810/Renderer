package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Satz 0 eines {@link Kernel}s oder {@link Zeichner}s: die festen Bindungen in der Reihenfolge des Shaders, das
 * Layout und die Deskriptorsätze je Belegung (aus {@link Kernel} herausgezogen in P6a, damit beide dieselbe Regel
 * haben). Gleiche Belegung gibt denselben Satz.
 */
final class Belegung {
    private static final int SAETZE_JE_POOL = 64;

    private final Geraet g;
    private final String name;
    final Kernel.Bindung[] bindungen;
    final long layout;
    private final List<Long> pools = new ArrayList<>();
    private int imPool = SAETZE_JE_POOL;
    private final Map<String, Long> saetze = new HashMap<>();

    Belegung(Geraet g, String name, Kernel.Bindung[] bindungen, int stufen) {
        this.g = g;
        this.name = name;
        this.bindungen = bindungen.clone();
        try (MemoryStack s = stackPush()) {
            LongBuffer lb = s.mallocLong(1);
            VkDescriptorSetLayoutCreateInfo sl = VkDescriptorSetLayoutCreateInfo.calloc(s).sType$Default();
            if (bindungen.length > 0) {
                VkDescriptorSetLayoutBinding.Buffer b = VkDescriptorSetLayoutBinding.calloc(bindungen.length, s);
                for (int i = 0; i < bindungen.length; i++) b.get(i).binding(i).descriptorType(bindungen[i].typ).descriptorCount(1).stageFlags(stufen);
                sl.pBindings(b);
            }
            Geraet.ok(vkCreateDescriptorSetLayout(g.device, sl, null, lb), "vkCreateDescriptorSetLayout " + name);
            layout = lb.get(0);
        }
    }

    synchronized long satz(Object... belegung) {
        if (belegung.length != bindungen.length) throw new IllegalArgumentException(name + ": " + bindungen.length + " Bindungen, " + belegung.length + " belegt");
        if (bindungen.length == 0) return VK_NULL_HANDLE;
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < belegung.length; i++) {
            Object o = belegung[i];
            // nach der laufenden Nummer, nicht nach dem Handle: ein neuer Puffer kann das Handle eines geschlossenen bekommen
            if (o instanceof Puffer p) key.append('p').append(p.nummer);
            else if (o instanceof Bild b) key.append('b').append(b.nummer);
            else if (o instanceof Abtaster.Textur t) key.append('t').append(t.bild().nummer).append('/').append(t.abtaster().nummer);
            else if (o instanceof Long l) key.append('s').append(l.longValue());
            else throw new IllegalArgumentException(name + ": Bindung " + i + " mit " + o);
            key.append(',');
        }
        Long da = saetze.get(key.toString());
        if (da != null) return da;
        try (MemoryStack s = stackPush()) {
            LongBuffer lb = s.mallocLong(1);
            if (imPool == SAETZE_JE_POOL) {
                int[] zahl = new int[Kernel.Bindung.values().length];
                for (Kernel.Bindung b : bindungen) zahl[b.ordinal()]++;
                int arten = 0;
                for (int z : zahl) if (z > 0) arten++;
                VkDescriptorPoolSize.Buffer ps = VkDescriptorPoolSize.calloc(arten, s);
                int k = 0;
                for (Kernel.Bindung b : Kernel.Bindung.values()) if (zahl[b.ordinal()] > 0) ps.get(k++).type(b.typ).descriptorCount(zahl[b.ordinal()] * SAETZE_JE_POOL);
                Geraet.ok(vkCreateDescriptorPool(g.device, VkDescriptorPoolCreateInfo.calloc(s).sType$Default().maxSets(SAETZE_JE_POOL).pPoolSizes(ps), null, lb),
                        "vkCreateDescriptorPool " + name);
                pools.add(lb.get(0));
                imPool = 0;
            }
            Geraet.ok(vkAllocateDescriptorSets(g.device, VkDescriptorSetAllocateInfo.calloc(s).sType$Default().descriptorPool(pools.get(pools.size() - 1)).pSetLayouts(s.longs(layout)), lb),
                    "vkAllocateDescriptorSets " + name);
            imPool++;
            long set = lb.get(0);
            VkWriteDescriptorSet.Buffer w = VkWriteDescriptorSet.calloc(bindungen.length, s);
            for (int i = 0; i < bindungen.length; i++) {
                VkWriteDescriptorSet x = w.get(i).sType$Default().dstSet(set).dstBinding(i).descriptorCount(1).descriptorType(bindungen[i].typ);
                Object o = belegung[i];
                switch (bindungen[i]) {
                    case PUFFER -> x.pBufferInfo(VkDescriptorBufferInfo.calloc(1, s).buffer(((Puffer) o).handle).offset(0).range(VK_WHOLE_SIZE));
                    case BILD -> x.pImageInfo(VkDescriptorImageInfo.calloc(1, s).imageView(((Bild) o).ansicht).imageLayout(VK_IMAGE_LAYOUT_GENERAL));
                    case TEXTUR -> {
                        Abtaster.Textur t = (Abtaster.Textur) o;
                        x.pImageInfo(VkDescriptorImageInfo.calloc(1, s).sampler(t.abtaster().handle).imageView(t.bild().ansicht).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
                    }
                    case STRAHLEN -> x.pNext(VkWriteDescriptorSetAccelerationStructureKHR.calloc(s).sType$Default().pAccelerationStructures(s.longs((Long) o)).address());
                }
            }
            vkUpdateDescriptorSets(g.device, w, null);
            saetze.put(key.toString(), set);
            return set;
        }
    }

    void close() {
        for (long p : pools) vkDestroyDescriptorPool(g.device, p, null);
        vkDestroyDescriptorSetLayout(g.device, layout, null);
    }
}
