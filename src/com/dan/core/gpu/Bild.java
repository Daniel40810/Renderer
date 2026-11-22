package com.dan.core.gpu;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Ein zweidimensionales Bild auf der Karte (Werkbuch HalligHooge Teil A, Abschnitt 6: „Ein Feld auf der Karte ist ein
 * Puffer oder Bild“): Kernels schreiben hinein ({@code imageStore}), Zeichner zeichnen hinein (Farbe oder Tiefe),
 * Kernels und Zeichner lesen es als Textur ({@link Abtaster}). Die Anordnung (Layout) führt der {@link Ablauf}; wer
 * ein Bild außerhalb eines Ablaufs benutzt, nimmt {@link #lies}, {@link #roh} oder {@link #schreibe}.
 * <p>
 * Formate: {@link #R32F} (ein float je Bildpunkt, etwa ein Wasserstand), {@link #R16F}, {@link #RG16F},
 * {@link #RGBA16F} (HDR), {@link #RGBA32F}, {@link #RGBA8} (linear), {@link #RGBA8S} (sRGB, Ausgabe),
 * {@link #D32F} (Tiefe). Die Nutzung folgt aus dem, was das Gerät für das Format kann: Speicherbild nur, wenn es
 * geht (sRGB und Tiefe meist nicht), Farb- bzw. Tiefenziel, Textur und Kopie immer.
 * <p>
 * Seit P6a (Werkbuch 12.7): Tiefe, sRGB, Ziel beim Zeichnen, Hochladen, Lesen in jedem Format, Bilder der Swapchain
 * ({@link #fremd}).
 */
public final class Bild implements Mittel, AutoCloseable {
    public static final int R32F = VK_FORMAT_R32_SFLOAT, R16F = VK_FORMAT_R16_SFLOAT, RG16F = VK_FORMAT_R16G16_SFLOAT, RGBA16F = VK_FORMAT_R16G16B16A16_SFLOAT,
            RGBA32F = VK_FORMAT_R32G32B32A32_SFLOAT, RGBA8 = VK_FORMAT_R8G8B8A8_UNORM, RGBA8S = VK_FORMAT_R8G8B8A8_SRGB, D32F = VK_FORMAT_D32_SFLOAT,
            /** Seit P12b: zwei Byte je Bildpunkt (die Gestaltkarte). */
            RG8 = VK_FORMAT_R8G8_UNORM;

    final Geraet g;
    public final long handle, ansicht;
    final long nummer = Kernel.NUMMER.incrementAndGet();
    public final int breite, hoehe, format, nutzung;
    final long allocation;
    private final Konto konto;
    private final String name;
    private final long bytes;
    private final Stand stand = new Stand();
    private boolean zu;

    /** Ein Bild als Ziel von Kernels oder Zeichnern und Quelle für Texturen und Kopien. */
    public Bild(Geraet g, Konto konto, String name, int breite, int hoehe, int format) {
        this.g = g;
        this.konto = konto;
        this.name = name;
        this.breite = breite;
        this.hoehe = hoehe;
        this.format = format;
        bytes = (long) breite * hoehe * bytesJePunkt(format);
        nutzung = nutzung(g, format);
        if (konto != null) konto.buche(bytes, name);
        try (MemoryStack s = stackPush()) {
            VkImageCreateInfo ci = VkImageCreateInfo.calloc(s).sType$Default().imageType(VK_IMAGE_TYPE_2D).format(format).mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).usage(nutzung);
            ci.extent().set(breite, hoehe, 1);
            if (g.familien.length > 1) ci.sharingMode(VK_SHARING_MODE_CONCURRENT).pQueueFamilyIndices(s.ints(g.familien));
            else ci.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            VmaAllocationCreateInfo ai = VmaAllocationCreateInfo.calloc(s).usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            LongBuffer lb = s.mallocLong(1);
            PointerBuffer pa = s.mallocPointer(1);
            int r = vmaCreateImage(g.vma, ci, ai, lb, pa, null);
            if (r != VK_SUCCESS) {
                if (konto != null) konto.gib(bytes);
                Geraet.ok(r, "vmaCreateImage " + name);
            }
            handle = lb.get(0);
            allocation = pa.get(0);
            ansicht = ansicht(g, handle, format, name);
        }
    }

    /** Ein Bild, das einem anderen gehört (der Swapchain): kein Speicher, nur Ansicht und Stand. */
    private Bild(Geraet g, String name, long handle, int breite, int hoehe, int format, int nutzung) {
        this.g = g;
        this.konto = null;
        this.name = name;
        this.breite = breite;
        this.hoehe = hoehe;
        this.format = format;
        this.handle = handle;
        this.allocation = 0;
        this.bytes = 0;
        this.nutzung = nutzung;
        this.ansicht = ansicht(g, handle, format, name);
    }

    /** Ein Bild der Swapchain als {@link Mittel}; {@link #close} gibt nur die Ansicht frei. */
    public static Bild fremd(Geraet g, String name, long handle, int breite, int hoehe, int format) {
        return new Bild(g, name, handle, breite, hoehe, format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
    }

    /** Wie oben, mit der Nutzung, mit der das Bild angelegt ist (seit W3: die Swapchain auch als Quelle einer Kopie). */
    public static Bild fremd(Geraet g, String name, long handle, int breite, int hoehe, int format, int nutzung) {
        return new Bild(g, name, handle, breite, hoehe, format, nutzung);
    }

    private static long ansicht(Geraet g, long handle, int format, String name) {
        try (MemoryStack s = stackPush()) {
            VkImageViewCreateInfo vi = VkImageViewCreateInfo.calloc(s).sType$Default().image(handle).viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
            vi.subresourceRange().aspectMask(aspekt(format)).levelCount(1).layerCount(1);
            LongBuffer lb = s.mallocLong(1);
            Geraet.ok(vkCreateImageView(g.device, vi, null, lb), "vkCreateImageView " + name);
            return lb.get(0);
        }
    }

    /** Tiefe oder Farbe. */
    public static boolean tiefe(int format) { return format == D32F || format == VK_FORMAT_D16_UNORM || format == VK_FORMAT_X8_D24_UNORM_PACK32; }

    static int aspekt(int format) { return tiefe(format) ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT; }

    public int aspekt() { return aspekt(format); }

    public static int bytesJePunkt(int format) {
        return switch (format) {
            case R16F, RG8 -> 2;
            case R32F, RG16F, RGBA8, RGBA8S, D32F, VK_FORMAT_B8G8R8A8_SRGB, VK_FORMAT_B8G8R8A8_UNORM -> 4;
            case RGBA16F -> 8;
            case RGBA32F -> 16;
            default -> throw new IllegalArgumentException("Bild: Format " + format + " unbekannt");
        };
    }

    /** Was ein Bild dieses Formats auf diesem Gerät kann. */
    static int nutzung(Geraet g, int format) {
        try (MemoryStack s = stackPush()) {
            VkFormatProperties fp = VkFormatProperties.malloc(s);
            vkGetPhysicalDeviceFormatProperties(g.phys, format, fp);
            int f = fp.optimalTilingFeatures(), n = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            if ((f & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) != 0) n |= VK_IMAGE_USAGE_SAMPLED_BIT;
            if ((f & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) != 0) n |= VK_IMAGE_USAGE_STORAGE_BIT;
            if ((f & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT) != 0) n |= VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
            if ((f & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) n |= VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
            if ((n & (VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)) == 0)
                throw new IllegalStateException(g.name + ": Format " + format + " ist weder Textur noch Ziel");
            return n;
        }
    }

    /** Kann das Gerät dieses Format als Speicherbild (für {@code imageStore})? */
    public boolean speicherbild() { return (nutzung & VK_IMAGE_USAGE_STORAGE_BIT) != 0; }

    @Override public Stand stand() { return stand; }
    @Override public String name() { return name; }

    /**
     * Liest das Bild in Rohform (Bildpunkte zeilenweise von oben, Little Endian; wartet). Arbeit, die das Bild schreibt,
     * muss vorher fertig sein. Die Sperren rechnet ein eigener kleiner {@link Ablauf}.
     */
    public ByteBuffer roh() {
        long n = (long) breite * hoehe * bytesJePunkt(format);
        try (Puffer z = Puffer.neu(g, null, name + " (zurück)", n, 0, Puffer.Art.WIRT)) {
            Ablauf a = new Ablauf(g, g.grafik).durchgang("lesen", new Ablauf.Zugriff[] {new Ablauf.Zugriff(this, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)}, c -> {
                try (MemoryStack s = stackPush()) {
                    VkBufferImageCopy.Buffer k = VkBufferImageCopy.calloc(1, s);
                    k.get(0).imageSubresource().aspectMask(aspekt()).layerCount(1);
                    k.get(0).imageExtent().set(breite, hoehe, 1);
                    vkCmdCopyImageToBuffer(c, handle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, z.handle, k);
                }
            });
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            ByteBuffer b = ByteBuffer.allocateDirect((int) n).order(ByteOrder.LITTLE_ENDIAN);
            z.lies(b, 0);
            b.rewind();
            return b;
        }
    }

    /**
     * Liest ein Bild in floats, je Bildpunkt so viele Werte, wie das Format Kanäle hat (R32F, D32F: 1; RG16F: 2;
     * RGBA16F, RGBA32F: 4; RGBA8, RGBA8S: 4, als Wert / 255, sRGB nicht entschlüsselt).
     */
    public float[] lies() {
        ByteBuffer b = roh();
        return switch (format) {
            case R32F, D32F, RGBA32F -> {
                float[] f = new float[b.remaining() / 4];
                b.asFloatBuffer().get(f);
                yield f;
            }
            case R16F, RG16F, RGBA16F -> {
                float[] f = new float[b.remaining() / 2];
                for (int i = 0; i < f.length; i++) f[i] = Float.float16ToFloat(b.getShort(2 * i));
                yield f;
            }
            case RGBA8, RGBA8S -> {
                float[] f = new float[b.remaining()];
                for (int i = 0; i < f.length; i++) f[i] = (b.get(i) & 0xFF) / 255f;
                yield f;
            }
            default -> throw new IllegalStateException(name + ": lies kennt das Format " + format + " nicht");
        };
    }

    /**
     * Lädt Bildpunkte hoch (Rohform wie {@link #roh}; wartet). Danach liegt das Bild zum Lesen als Textur bereit. Arbeit,
     * die das Bild noch liest, muss vorher fertig sein.
     */
    public void schreibe(ByteBuffer daten) {
        long n = (long) breite * hoehe * bytesJePunkt(format);
        if (daten.remaining() != n) throw new IllegalArgumentException(name + ": " + daten.remaining() + " Byte statt " + n);
        try (Puffer z = Puffer.neu(g, null, name + " (hin)", n, 0, Puffer.Art.WIRT)) {
            ByteBuffer d = daten;
            if (!d.isDirect()) d = ByteBuffer.allocateDirect((int) n).put(daten.duplicate()).flip();
            z.schreibe(d, 0);
            Ablauf a = new Ablauf(g, g.grafik).durchgang("hochladen", new Ablauf.Zugriff[] {new Ablauf.Zugriff(this, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)}, c -> {
                try (MemoryStack s = stackPush()) {
                    VkBufferImageCopy.Buffer k = VkBufferImageCopy.calloc(1, s);
                    k.get(0).imageSubresource().aspectMask(aspekt()).layerCount(1);
                    k.get(0).imageExtent().set(breite, hoehe, 1);
                    vkCmdCopyBufferToImage(c, z.handle, handle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, k);
                }
            });
            g.grafik.einmal(c -> a.aufnehmen(c, null));
        }
    }

    /** Lädt floats hoch: R32F, RGBA32F, D32F direkt; R16F, RG16F, RGBA16F als float16 gepackt. */
    public void schreibe(float[] werte) {
        int k = bytesJePunkt(format);
        boolean halb = format == R16F || format == RG16F || format == RGBA16F;
        if (format != R32F && format != RGBA32F && format != D32F && !halb) throw new IllegalStateException(name + ": float hochladen geht für Format " + format + " nicht");
        ByteBuffer b = ByteBuffer.allocateDirect(breite * hoehe * k).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : werte) if (halb) b.putShort(Float.floatToFloat16(v)); else b.putFloat(v);
        b.flip();
        schreibe(b);
    }

    @Override public void close() {
        if (zu) return;
        zu = true;
        vkDestroyImageView(g.device, ansicht, null);
        if (allocation != 0) {
            vmaDestroyImage(g.vma, handle, allocation);
            if (konto != null) konto.gib(bytes);
        }
    }

    @Override public String toString() { return name + " (" + breite + " × " + hoehe + ")"; }
}
