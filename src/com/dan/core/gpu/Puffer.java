package com.dan.core.gpu;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Ein Puffer auf der Karte über VMA (Werkbuch HalligHooge Teil A, Abschnitt 6, „Speicher“). Drei Arten:
 * <ul>
 * <li>{@link Art#KARTE}: im Speicher der Karte, nicht abgebildet. Daten gehen über einen Zwischenpuffer hin und zurück.
 * Die Vorgabe für Felder.</li>
 * <li>{@link Art#GETEILT}: vom Prozessor sichtbar und abgebildet; Kernels rechnen darauf, und der Prozessor liest und
 * schreibt ohne Kopie. Auf der 680M ist das derselbe Hauptspeicher; ob Kernels darauf gleich schnell sind, misst
 * {@code GpuCheck}.</li>
 * <li>{@link Art#WIRT}: beim Prozessor, abgebildet, nur für die Übertragung (Zwischenpuffer).</li>
 * </ul>
 * Jeder Puffer bucht auf ein {@link Konto}. Hat das Gerät eigene Warteschlangen für Rechnen oder Übertragung, gehört
 * der Puffer allen gemeinsam; Übergaben zwischen den Familien entfallen.
 */
public final class Puffer implements Mittel, AutoCloseable {
    public enum Art { KARTE, GETEILT, WIRT }

    final Geraet g;
    public final long handle, groesse;
    final long nummer = Kernel.NUMMER.incrementAndGet();
    public final Art art;
    final long allocation;
    /** Abgebildeter Zeiger (nicht bei {@link Art#KARTE}), sonst 0. */
    final long abbild;
    private final Konto konto;
    private final String name;
    private final Stand stand = new Stand();
    private boolean zu;

    private Puffer(Geraet g, Konto konto, String name, long groesse, int nutzung, Art art) {
        long grenze = (nutzung & VK_BUFFER_USAGE_STORAGE_BUFFER_BIT) != 0 ? Math.min(g.groessteZuteilung, g.groessterPuffer) : g.groessteZuteilung;
        if (groesse > grenze)
            throw new IllegalArgumentException("Puffer " + name + " mit " + Geraet.mb(groesse) + " ist größer als eine Zuteilung dieses Geräts (" + Geraet.mb(grenze)
                    + "); in Stücken führen");
        this.g = g;
        this.konto = konto;
        this.name = name;
        this.groesse = groesse;
        this.art = art;
        if (konto != null) konto.buche(groesse, name);
        try (MemoryStack s = stackPush()) {
            int u = nutzung | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            if (!g.kann.adressen()) u &= ~VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
            VkBufferCreateInfo bi = VkBufferCreateInfo.calloc(s).sType$Default().size(groesse).usage(u);
            if (g.familien.length > 1) bi.sharingMode(VK_SHARING_MODE_CONCURRENT).pQueueFamilyIndices(s.ints(g.familien));
            else bi.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            VmaAllocationCreateInfo ai = VmaAllocationCreateInfo.calloc(s);
            if (art == Art.WIRT) ai.usage(VMA_MEMORY_USAGE_AUTO_PREFER_HOST).flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT | VMA_ALLOCATION_CREATE_MAPPED_BIT);
            else if (art == Art.GETEILT) ai.usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE).flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT | VMA_ALLOCATION_CREATE_MAPPED_BIT);
            else ai.usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            LongBuffer lb = s.mallocLong(1);
            PointerBuffer pa = s.mallocPointer(1);
            VmaAllocationInfo info = VmaAllocationInfo.calloc(s);
            int r = vmaCreateBuffer(g.vma, bi, ai, lb, pa, info);
            if (r != VK_SUCCESS) {
                if (konto != null) konto.gib(groesse);
                Geraet.ok(r, "vmaCreateBuffer " + name + " (" + Geraet.mb(groesse) + ")");
            }
            handle = lb.get(0);
            allocation = pa.get(0);
            abbild = art == Art.KARTE ? NULL : info.pMappedData();
        }
    }

    /** Ein Puffer mit eigener Nutzung (VK_BUFFER_USAGE_…), zusätzlich zur Übertragung hin und zurück. */
    public static Puffer neu(Geraet g, Konto konto, String name, long bytes, int nutzung, Art art) { return new Puffer(g, konto, name, Math.max(16, bytes), nutzung, art); }

    /** Ein Speicherpuffer für Kernels im Speicher der Karte. */
    public static Puffer speicher(Geraet g, Konto konto, String name, long bytes) { return neu(g, konto, name, bytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, Art.KARTE); }

    /** Ein Speicherpuffer für Kernels, den auch der Prozessor ohne Kopie liest und schreibt. */
    public static Puffer geteilt(Geraet g, Konto konto, String name, long bytes) { return neu(g, konto, name, bytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, Art.GETEILT); }

    @Override public Stand stand() { return stand; }
    @Override public String name() { return name; }

    /** Adresse auf der Karte (für Beschleunigungsstrukturen und Zeiger in Shadern). */
    public long adresse() {
        try (MemoryStack s = stackPush()) {
            return vkGetBufferDeviceAddress(g.device, VkBufferDeviceAddressInfo.calloc(s).sType$Default().buffer(handle));
        }
    }

    // ------------------------------------------------------------------ Daten

    /**
     * Schreibt die Bytes ab {@code versatz}. Wartet, bis sie auf der Karte sind. Arbeit, die den Puffer noch benutzt,
     * muss vorher fertig sein ({@link Zeitleiste#warte}).
     */
    public void schreibe(ByteBuffer daten, long versatz) {
        long n = daten.remaining();
        if (versatz < 0 || versatz + n > groesse) throw new IndexOutOfBoundsException(name + ": " + n + " Byte ab " + versatz + " in " + groesse);
        if (abbild != NULL) {
            memCopy(memAddress(daten), abbild + versatz, n);
            vmaFlushAllocation(g.vma, allocation, versatz, n);
            return;
        }
        try (Puffer z = neu(g, null, name + " (hin)", n, 0, Art.WIRT)) {
            memCopy(memAddress(daten), z.abbild, n);
            vmaFlushAllocation(g.vma, z.allocation, 0, n);
            g.uebertragung.einmal(c -> {
                try (MemoryStack s = stackPush()) {
                    vkCmdCopyBuffer(c, z.handle, handle, VkBufferCopy.calloc(1, s).srcOffset(0).dstOffset(versatz).size(n));
                }
            });
        }
    }

    /** Liest Bytes ab {@code versatz}; Arbeit, die den Puffer schreibt, muss vorher fertig sein. */
    public void lies(ByteBuffer ziel, long versatz) {
        long n = ziel.remaining();
        if (versatz < 0 || versatz + n > groesse) throw new IndexOutOfBoundsException(name + ": " + n + " Byte ab " + versatz + " in " + groesse);
        if (abbild != NULL) {
            vmaInvalidateAllocation(g.vma, allocation, versatz, n);
            memCopy(abbild + versatz, memAddress(ziel), n);
            return;
        }
        try (Puffer z = neu(g, null, name + " (zurück)", n, 0, Art.WIRT)) {
            g.uebertragung.einmal(c -> {
                try (MemoryStack s = stackPush()) {
                    vkCmdCopyBuffer(c, handle, z.handle, VkBufferCopy.calloc(1, s).srcOffset(versatz).dstOffset(0).size(n));
                }
            });
            vmaInvalidateAllocation(g.vma, z.allocation, 0, n);
            memCopy(z.abbild, memAddress(ziel), n);
        }
    }

    public void schreibe(float[] werte) {
        ByteBuffer b = memAlloc(werte.length * 4);
        try {
            b.asFloatBuffer().put(werte);
            schreibe(b, 0);
        } finally {
            memFree(b);
        }
    }

    public void schreibe(int[] werte) {
        ByteBuffer b = memAlloc(werte.length * 4);
        try {
            b.asIntBuffer().put(werte);
            schreibe(b, 0);
        } finally {
            memFree(b);
        }
    }

    public void schreibe(short[] werte) {
        ByteBuffer b = memAlloc(werte.length * 2);
        try {
            b.asShortBuffer().put(werte);
            schreibe(b, 0);
        } finally {
            memFree(b);
        }
    }

    /** Die ersten n float-Werte. */
    public float[] floats(int n) {
        ByteBuffer b = memAlloc(n * 4);
        try {
            lies(b, 0);
            float[] f = new float[n];
            b.asFloatBuffer().get(f);
            return f;
        } finally {
            memFree(b);
        }
    }

    /** Die ersten n int-Werte. */
    public int[] ints(int n) {
        ByteBuffer b = memAlloc(n * 4);
        try {
            lies(b, 0);
            int[] f = new int[n];
            b.asIntBuffer().get(f);
            return f;
        } finally {
            memFree(b);
        }
    }

    /** Die ersten n short-Werte (float16 oder ganze Zahlen mit 16 Bit). */
    public short[] shorts(int n) {
        ByteBuffer b = memAlloc(n * 2);
        try {
            lies(b, 0);
            short[] f = new short[n];
            b.asShortBuffer().get(f);
            return f;
        } finally {
            memFree(b);
        }
    }

    /** Füllt den Puffer auf der Karte mit Nullen. */
    public void nullen() { g.uebertragung.einmal(c -> vkCmdFillBuffer(c, handle, 0, VK_WHOLE_SIZE, 0)); }

    @Override public void close() {
        if (zu) return;
        zu = true;
        vmaDestroyBuffer(g.vma, handle, allocation);
        if (konto != null) konto.gib(groesse);
    }

    @Override public String toString() { return name + " (" + Geraet.mb(groesse) + ", " + art + ")"; }
}
