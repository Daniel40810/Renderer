package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Wie ein Shader ein {@link Bild} als Textur liest (seit P6a): Filter und Rand. Eine Bindung {@link Kernel.Bindung#TEXTUR}
 * wird mit {@link #an} belegt.
 */
public final class Abtaster implements AutoCloseable {
    /** Ein Bild mit seinem Abtaster, als Belegung einer Bindung {@link Kernel.Bindung#TEXTUR}. */
    public record Textur(Bild bild, Abtaster abtaster) { }

    final Geraet g;
    public final long handle;
    final long nummer = Kernel.NUMMER.incrementAndGet();
    public final boolean linear, wiederholen;

    /** {@code linear}: bilinear, sonst der nächste Bildpunkt; {@code wiederholen}: Kachel, sonst am Rand festgehalten. */
    public Abtaster(Geraet g, boolean linear, boolean wiederholen) {
        this.g = g;
        this.linear = linear;
        this.wiederholen = wiederholen;
        int f = linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST, r = wiederholen ? VK_SAMPLER_ADDRESS_MODE_REPEAT : VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        try (MemoryStack s = stackPush()) {
            VkSamplerCreateInfo ci = VkSamplerCreateInfo.calloc(s).sType$Default().magFilter(f).minFilter(f).mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(r).addressModeV(r).addressModeW(r).maxLod(0).unnormalizedCoordinates(false);
            LongBuffer lb = s.mallocLong(1);
            Geraet.ok(vkCreateSampler(g.device, ci, null, lb), "vkCreateSampler");
            handle = lb.get(0);
        }
    }

    public Textur an(Bild b) { return new Textur(b, this); }

    @Override public void close() { vkDestroySampler(g.device, handle, null); }
}
