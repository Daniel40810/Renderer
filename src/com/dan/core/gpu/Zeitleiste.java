package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreTypeCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;

import java.nio.LongBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Eine Zeitleiste (Werkbuch HalligHooge Teil A, Abschnitt 6, „Takt“): ein Zeitleisten-Semaphor, dessen Wert mit jeder
 * abgeschickten Arbeit um eins steigt. Die Physik schickt auf der Rechen-Warteschlange ab und bekommt einen Wert;
 * das Bild wartet auf diesen Wert, bevor es die Felder liest – auf der Karte, ohne dass ein Faden steht
 * ({@link Schlange#schicke}). Der Prozessor kann den Stand abfragen oder warten ({@link #stand}, {@link #warte}).
 * <p>
 * Je Zeitleiste schickt ein Faden ab; die Werte steigen in der Reihenfolge des Abschickens. Eine Zeitleiste darf
 * vor der Warteschlange vergehen, sobald ihre Arbeit fertig ist.
 */
public final class Zeitleiste implements AutoCloseable {
    final Geraet g;
    final long handle;
    private final AtomicLong vergeben = new AtomicLong();
    private volatile boolean zu;

    public Zeitleiste(Geraet g) {
        this.g = g;
        try (MemoryStack s = stackPush()) {
            VkSemaphoreTypeCreateInfo ti = VkSemaphoreTypeCreateInfo.calloc(s).sType$Default().semaphoreType(VK_SEMAPHORE_TYPE_TIMELINE).initialValue(0);
            LongBuffer lp = s.mallocLong(1);
            Geraet.ok(vkCreateSemaphore(g.device, VkSemaphoreCreateInfo.calloc(s).sType$Default().pNext(ti.address()), null, lp), "vkCreateSemaphore");
            handle = lp.get(0);
        }
    }

    long naechster() { return vergeben.incrementAndGet(); }

    /** Wirft, wenn die Zeitleiste geschlossen ist – besser als ein Absturz im Treiber. */
    void offen() {
        if (zu) throw new IllegalStateException("Die Zeitleiste ist geschlossen");
    }

    /** Der zuletzt vergebene Wert: Ist er erreicht, ist alles fertig, was auf dieser Zeitleiste abgeschickt wurde. */
    public long vergeben() { return vergeben.get(); }

    /** Der Wert, den die Karte erreicht hat. */
    public long stand() {
        offen();
        try (MemoryStack s = stackPush()) {
            LongBuffer lp = s.mallocLong(1);
            Geraet.ok(vkGetSemaphoreCounterValue(g.device, handle, lp), "vkGetSemaphoreCounterValue");
            return lp.get(0);
        }
    }

    /** Wartet im Prozessor, bis der Wert erreicht ist, höchstens {@code nanos}. Gibt zurück, ob er erreicht ist. */
    public boolean warte(long wert, long nanos) {
        offen();
        try (MemoryStack s = stackPush()) {
            VkSemaphoreWaitInfo wi = VkSemaphoreWaitInfo.calloc(s).sType$Default().semaphoreCount(1).pSemaphores(s.longs(handle)).pValues(s.longs(wert));
            int r = vkWaitSemaphores(g.device, wi, nanos);
            if (r == VK_TIMEOUT) return false;
            Geraet.ok(r, "vkWaitSemaphores");
            return true;
        }
    }

    /** Schließt die Zeitleiste. Arbeit, die sie signalisiert oder auf sie wartet, muss fertig sein. */
    @Override public void close() {
        if (zu) return;
        zu = true;
        vkDestroySemaphore(g.device, handle, null);
    }
}
