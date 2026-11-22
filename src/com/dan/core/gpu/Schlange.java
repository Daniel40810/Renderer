package com.dan.core.gpu;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Eine Warteschlange des Geräts mit ihren Befehlspuffern (Werkbuch HalligHooge Teil A, Abschnitt 6).
 * <p>
 * <b>Fäden:</b> Jeder Faden hat seinen eigenen Befehlspool; aufnehmen dürfen also mehrere Fäden zugleich (die Physik
 * und das Bild). Das Abschicken ist je Warteschlange gesperrt – auch dann richtig, wenn {@code rechnen} und
 * {@code grafik} dieselbe Warteschlange sind.
 * <p>
 * <b>Abschicken:</b> {@link #schicke} schickt ab, ohne zu warten; die Arbeit signalisiert am Ende einen Wert auf einer
 * {@link Zeitleiste} und kann vorher auf Werte anderer Zeitleisten warten (auf der Karte, nicht im Prozessor).
 * {@link #einmal} nimmt auf, schickt ab und wartet.
 * <p>
 * <b>Freigabe:</b> Jede Arbeit signalisiert außerdem die eigene Zeitleiste der Warteschlange. An ihr – nicht an der
 * Zeitleiste des Aufrufers, die vor der Warteschlange vergehen darf – sieht die Warteschlange, welche Befehlspuffer
 * fertig sind, und gibt sie beim nächsten {@link #befehle} frei. Pools beendeter Fäden werden abgeräumt.
 */
public final class Schlange {
    /** Ein abgeschickter Befehlspuffer, der frei wird, wenn die eigene Zeitleiste der Warteschlange den Wert erreicht hat. */
    private record Offen(VkCommandBuffer befehle, long wert) { }

    private static final class Faden {
        long pool;
        final List<Offen> offen = new ArrayList<>();
    }

    public final String name;
    public final int familie;
    /** Platz im Stand der Betriebsmittel: 0 Grafik, 1 Rechnen, 2 Übertragung. */
    final int platz;
    final Geraet g;
    final VkQueue queue;
    /** Gültige Bits der Zeitstempel dieser Familie; 0: keine Zeitstempel. */
    final int zeitstempelBits;
    private final Map<Thread, Faden> faeden = new ConcurrentHashMap<>();
    private final Zeitleiste eigene;

    Schlange(Geraet g, String name, int familie, int platz, int zeitstempelBits) { this(g, name, familie, platz, zeitstempelBits, null); }

    /**
     * {@code geteilt} ist null oder eine Schlange, deren Warteschlange diese mitbenutzt (Prüfhilfe
     * {@code core.gpu.getrennt}): eigene Pools, eigene Zeitleiste, eigener Stand – aber abgeschickt wird auf derselben
     * Warteschlange und unter derselben Sperre.
     */
    Schlange(Geraet g, String name, int familie, int platz, int zeitstempelBits, Schlange geteilt) {
        this.g = g;
        this.platz = platz;
        this.name = name;
        this.familie = familie;
        this.zeitstempelBits = zeitstempelBits;
        if (geteilt != null) queue = geteilt.queue;
        else try (MemoryStack s = stackPush()) {
            PointerBuffer pp = s.mallocPointer(1);
            vkGetDeviceQueue(g.device, familie, 0, pp);
            queue = new VkQueue(pp.get(0), g.device);
        }
        eigene = new Zeitleiste(g);
    }

    private Faden faden() {
        Faden da = faeden.get(Thread.currentThread());
        if (da != null) return da;
        raeume();
        return faeden.computeIfAbsent(Thread.currentThread(), t -> {
            Faden f = new Faden();
            try (MemoryStack s = stackPush()) {
                VkCommandPoolCreateInfo ci = VkCommandPoolCreateInfo.calloc(s).sType$Default().flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT).queueFamilyIndex(familie);
                LongBuffer lp = s.mallocLong(1);
                Geraet.ok(vkCreateCommandPool(g.device, ci, null, lp), "vkCreateCommandPool");
                f.pool = lp.get(0);
            }
            return f;
        });
    }

    /** Gibt die Pools beendeter Fäden frei, sobald ihre Arbeit fertig ist. */
    private void raeume() {
        long stand = -1;
        for (Iterator<Map.Entry<Thread, Faden>> it = faeden.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Thread, Faden> e = it.next();
            if (e.getKey().isAlive()) continue;
            if (stand < 0) stand = eigene.stand();
            boolean fertig = true;
            for (Offen o : e.getValue().offen) fertig &= o.wert <= stand;
            if (!fertig) continue;
            if (faeden.remove(e.getKey(), e.getValue())) vkDestroyCommandPool(g.device, e.getValue().pool, null);
        }
    }

    /** Ein Befehlspuffer aus dem Pool dieses Fadens, zum Aufnehmen geöffnet. Fertige Puffer dieses Fadens werden dabei frei. */
    public VkCommandBuffer befehle() {
        Faden f = faden();
        if (!f.offen.isEmpty()) {
            long stand = eigene.stand();
            for (Iterator<Offen> it = f.offen.iterator(); it.hasNext(); ) {
                Offen o = it.next();
                if (o.wert > stand) continue;
                vkFreeCommandBuffers(g.device, f.pool, o.befehle);
                it.remove();
            }
        }
        try (MemoryStack s = stackPush()) {
            VkCommandBufferAllocateInfo ai = VkCommandBufferAllocateInfo.calloc(s).sType$Default().commandPool(f.pool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
            PointerBuffer pp = s.mallocPointer(1);
            Geraet.ok(vkAllocateCommandBuffers(g.device, ai, pp), "vkAllocateCommandBuffers");
            VkCommandBuffer c = new VkCommandBuffer(pp.get(0), g.device);
            Geraet.ok(vkBeginCommandBuffer(c, VkCommandBufferBeginInfo.calloc(s).sType$Default().flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)), "vkBeginCommandBuffer");
            return c;
        }
    }

    /**
     * Schließt die Aufnahme und schickt ab, ohne zu warten. Die Arbeit beginnt erst, wenn {@code warteAuf} den Wert
     * {@code warteWert} erreicht hat (null: sofort), und signalisiert am Ende auf {@code signal} den nächsten Wert, den
     * die Methode zurückgibt. Muss im Faden laufen, der {@link #befehle} gerufen hat. Die Zeitleisten dürfen erst
     * geschlossen werden, wenn die Arbeit fertig ist.
     */
    public long schicke(VkCommandBuffer c, Zeitleiste signal, Zeitleiste warteAuf, long warteWert) { return schicke(c, signal, warteAuf, warteWert, VK_NULL_HANDLE, 0, VK_NULL_HANDLE); }

    /**
     * Wie {@link #schicke(VkCommandBuffer, Zeitleiste, Zeitleiste, long)}, dazu (seit P6a, für die Swapchain) ein
     * binärer Semaphor, auf den in den Stufen {@code warteStufe} gewartet wird, und einer, der am Ende signalisiert wird
     * (je {@code VK_NULL_HANDLE}: keiner).
     */
    public long schicke(VkCommandBuffer c, Zeitleiste signal, Zeitleiste warteAuf, long warteWert, long warteBinaer, long warteStufe, long signalBinaer) {
        signal.offen();
        if (warteAuf != null) warteAuf.offen();
        Geraet.ok(vkEndCommandBuffer(c), "vkEndCommandBuffer");
        try (MemoryStack s = stackPush()) {
            VkSubmitInfo2.Buffer si = VkSubmitInfo2.calloc(1, s);
            si.get(0).sType$Default().pCommandBufferInfos(VkCommandBufferSubmitInfo.calloc(1, s).sType$Default().commandBuffer(c));
            int nw = (warteAuf != null ? 1 : 0) + (warteBinaer != VK_NULL_HANDLE ? 1 : 0);
            if (nw > 0) {
                VkSemaphoreSubmitInfo.Buffer wi = VkSemaphoreSubmitInfo.calloc(nw, s);
                int k = 0;
                if (warteAuf != null) wi.get(k++).sType$Default().semaphore(warteAuf.handle).value(warteWert).stageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT);
                if (warteBinaer != VK_NULL_HANDLE) wi.get(k).sType$Default().semaphore(warteBinaer).stageMask(warteStufe);
                si.get(0).pWaitSemaphoreInfos(wi);
            }
            boolean beide = signal != eigene;
            int ns = (beide ? 2 : 1) + (signalBinaer != VK_NULL_HANDLE ? 1 : 0);
            VkSemaphoreSubmitInfo.Buffer sig = VkSemaphoreSubmitInfo.calloc(ns, s);
            if (signalBinaer != VK_NULL_HANDLE) sig.get(ns - 1).sType$Default().semaphore(signalBinaer).stageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT);
            long wert, eigen;
            synchronized (queue) {
                // die Werte werden unter der Sperre vergeben: Werte einer Zeitleiste müssen in der Reihenfolge des Abschickens steigen
                eigen = eigene.naechster();
                sig.get(0).sType$Default().semaphore(eigene.handle).value(eigen).stageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT);
                wert = eigen;
                if (beide) {
                    wert = signal.naechster();
                    sig.get(1).sType$Default().semaphore(signal.handle).value(wert).stageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT);
                }
                si.get(0).pSignalSemaphoreInfos(sig);
                Geraet.ok(vkQueueSubmit2(queue, si, VK_NULL_HANDLE), "vkQueueSubmit2");
            }
            faden().offen.add(new Offen(c, eigen));
            return wert;
        }
    }

    /**
     * Zeigt ein Bild der Swapchain, sobald {@code warte} signalisiert ist (seit P6a, für {@link Flaeche}). Gibt den
     * Rückgabewert von {@code vkQueuePresentKHR} zurück (auch {@code VK_SUBOPTIMAL_KHR}, {@code VK_ERROR_OUT_OF_DATE_KHR}).
     */
    public int zeige(long swapchain, int index, long warte) {
        try (MemoryStack s = stackPush()) {
            org.lwjgl.vulkan.VkPresentInfoKHR pi = org.lwjgl.vulkan.VkPresentInfoKHR.calloc(s).sType$Default()
                    .pWaitSemaphores(s.longs(warte)).swapchainCount(1).pSwapchains(s.longs(swapchain)).pImageIndices(s.ints(index));
            synchronized (queue) {
                return org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR(queue, pi);
            }
        }
    }

    /** Nimmt auf, schickt ab und wartet, bis die Karte fertig ist. Gibt die Wartezeit in ns zurück. */
    public long einmal(Consumer<VkCommandBuffer> aufnahme) {
        VkCommandBuffer c = befehle();
        aufnahme.accept(c);
        long t0 = System.nanoTime(), wert = schicke(c, eigene, null, 0);
        eigene.warte(wert, Long.MAX_VALUE);
        return System.nanoTime() - t0;
    }

    void schliessen() {
        for (Faden f : faeden.values()) vkDestroyCommandPool(g.device, f.pool, null);
        faeden.clear();
        eigene.close();
    }

    @Override public String toString() { return name + " (Familie " + familie + ")"; }
}
