package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.jawt.JAWT;
import org.lwjgl.system.jawt.JAWTDrawingSurface;
import org.lwjgl.system.jawt.JAWTDrawingSurfaceInfo;
import org.lwjgl.system.jawt.JAWTWin32DrawingSurfaceInfo;
import org.lwjgl.system.jawt.JAWTX11DrawingSurfaceInfo;
import org.lwjgl.system.windows.WinBase;
import org.lwjgl.vulkan.*;

import java.awt.Canvas;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.function.BiConsumer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.jawt.JAWTFunctions.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Eine Vulkan-Fläche in einem AWT-{@link Canvas} (seit P6a; der Weg aus P0.2, F8 A: das Fenster-Handle des Canvas
 * über JAWT, darauf eine Swapchain). Unter Windows eine Win32-Fläche, unter Linux eine Xlib-Fläche (für Prüfungen
 * unter Xvfb). Das {@link Geraet} braucht dafür {@code Optionen.flaeche}.
 * <p>
 * Je Bild: {@link #bild} nimmt das nächste Bild der Swapchain, lässt den Aufrufer hineinzeichnen (als {@link Bild},
 * mit einem Befehlspuffer der Grafik-Warteschlange), gibt es zum Zeigen frei und zeigt es. Ändert sich die Größe
 * oder meldet der Treiber, dass die Swapchain nicht mehr passt, baut die Fläche sie beim nächsten Bild neu. Höchstens
 * zwei Bilder sind zugleich unterwegs [G].
 */
public final class Flaeche implements AutoCloseable {
    private static final int UNTERWEGS = 2;

    private final Geraet g;
    private final Canvas canvas;
    private final long flaeche;
    private final boolean bildsynchron;
    private final Zeitleiste leiste;
    private final long[] bereit = new long[UNTERWEGS], wert = new long[UNTERWEGS];
    private long handle = VK_NULL_HANDLE;
    private int format = VK_FORMAT_UNDEFINED, raum, modus, breite, hoehe, neu;
    private Bild[] bilder = new Bild[0];
    private long[] fertig = new long[0];
    private boolean neuBauen = true;
    private long gezeigt;

    /** {@code bildsynchron}: FIFO (an die Bildwiederholung gebunden), sonst ohne Warten, wenn das Gerät es kann. */
    public Flaeche(Geraet g, Canvas c, boolean bildsynchron) {
        this.g = g;
        this.canvas = c;
        this.bildsynchron = bildsynchron;
        try (MemoryStack s = stackPush()) {
            LongBuffer lb = s.mallocLong(1);
            long[] h = handles(c);
            if (Platform.get() == Platform.WINDOWS) {
                VkWin32SurfaceCreateInfoKHR sci = VkWin32SurfaceCreateInfoKHR.calloc(s).sType$Default().hinstance(WinBase.nGetModuleHandle(NULL, NULL)).hwnd(h[0]);
                Geraet.ok(KHRWin32Surface.vkCreateWin32SurfaceKHR(g.instance, sci, null, lb), "vkCreateWin32SurfaceKHR");
            } else {
                VkXlibSurfaceCreateInfoKHR sci = VkXlibSurfaceCreateInfoKHR.calloc(s).sType$Default().dpy(h[1]).window(h[0]);
                Geraet.ok(KHRXlibSurface.vkCreateXlibSurfaceKHR(g.instance, sci, null, lb), "vkCreateXlibSurfaceKHR");
            }
            flaeche = lb.get(0);
            IntBuffer ib = s.mallocInt(1);
            vkGetPhysicalDeviceSurfaceSupportKHR(g.phys, g.grafik.familie, flaeche, ib);
            if (ib.get(0) != VK_TRUE) {
                vkDestroySurfaceKHR(g.instance, flaeche, null);
                throw new IllegalStateException(g.name + ": die Grafik-Warteschlange kann auf diese Fläche nicht zeigen");
            }
            for (int i = 0; i < UNTERWEGS; i++) {
                Geraet.ok(vkCreateSemaphore(g.device, VkSemaphoreCreateInfo.calloc(s).sType$Default(), null, lb), "vkCreateSemaphore");
                bereit[i] = lb.get(0);
            }
        }
        leiste = new Zeitleiste(g);
    }

    /** Fenster-Handle des Canvas über JAWT: {HWND} unter Windows, {Drawable, Display} unter X11. */
    static long[] handles(Canvas c) {
        try (MemoryStack s = stackPush()) {
            JAWT awt = JAWT.calloc(s).version(JAWT_VERSION_1_4);
            if (!JAWT_GetAWT(awt)) throw new IllegalStateException("JAWT nicht verfügbar");
            JAWTDrawingSurface ds = JAWT_GetDrawingSurface(c, awt.GetDrawingSurface());
            if (ds == null) throw new IllegalStateException("JAWT: keine Zeichenfläche (ist der Canvas sichtbar?)");
            try {
                int lock = JAWT_DrawingSurface_Lock(ds, ds.Lock());
                if ((lock & JAWT_LOCK_ERROR) != 0) throw new IllegalStateException("JAWT: Zeichenfläche lässt sich nicht sperren");
                try {
                    JAWTDrawingSurfaceInfo dsi = JAWT_DrawingSurface_GetDrawingSurfaceInfo(ds, ds.GetDrawingSurfaceInfo());
                    if (dsi == null) throw new IllegalStateException("JAWT: keine Angaben zur Zeichenfläche");
                    try {
                        if (Platform.get() == Platform.WINDOWS) return new long[] {JAWTWin32DrawingSurfaceInfo.create(dsi.platformInfo()).hwnd()};
                        JAWTX11DrawingSurfaceInfo x = JAWTX11DrawingSurfaceInfo.create(dsi.platformInfo());
                        return new long[] {x.drawable(), x.display()};
                    } finally {
                        JAWT_DrawingSurface_FreeDrawingSurfaceInfo(dsi, ds.FreeDrawingSurfaceInfo());
                    }
                } finally {
                    JAWT_DrawingSurface_Unlock(ds, ds.Unlock());
                }
            } finally {
                JAWT_FreeDrawingSurface(ds, awt.FreeDrawingSurface());
            }
        }
    }

    /** Format der Bilder (BGRA8 sRGB, wenn die Fläche es kann); 0, solange noch nichts gebaut ist. */
    public int format() {
        if (format == VK_FORMAT_UNDEFINED) bauen();
        return format;
    }

    public int breite() { return breite; }
    public int hoehe() { return hoehe; }
    /** Wie oft die Swapchain neu gebaut wurde (nach dem ersten Mal). */
    public int neuGebaut() { return neu; }
    public long gezeigt() { return gezeigt; }
    /** Die Zeitleiste der gezeigten Bilder: ihr Wert steigt mit jedem Bild. */
    public Zeitleiste leiste() { return leiste; }

    /** Beim nächsten Bild neu bauen (etwa wenn der Canvas seine Größe ändert). */
    public void neuBauen() { neuBauen = true; }

    /**
     * Ein Bild: nimmt das nächste Bild der Swapchain, lässt {@code aufnahme} mit einem Befehlspuffer der
     * Grafik-Warteschlange hineinzeichnen, gibt es frei und zeigt es. Die Arbeit wartet auf der Karte auf
     * {@code warteAuf} (null: auf nichts). Gibt false zurück, wenn die Fläche gerade kein Bild hat (Größe 0).
     */
    public boolean bild(Zeitleiste warteAuf, long warteWert, BiConsumer<VkCommandBuffer, Bild> aufnahme) {
        if (neuBauen) bauen();
        if (breite == 0 || hoehe == 0) return false;
        int f = (int) (gezeigt % UNTERWEGS);
        if (wert[f] > 0) leiste.warte(wert[f], Long.MAX_VALUE);
        int i;
        try (MemoryStack s = stackPush()) {
            IntBuffer idx = s.mallocInt(1);
            int r = vkAcquireNextImageKHR(g.device, handle, Long.MAX_VALUE, bereit[f], VK_NULL_HANDLE, idx);
            if (r == VK_ERROR_OUT_OF_DATE_KHR) {
                neuBauen = true;
                return false;
            }
            if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) Geraet.ok(r, "vkAcquireNextImageKHR");
            if (r == VK_SUBOPTIMAL_KHR) neuBauen = true;
            i = idx.get(0);
        }
        Bild z = bilder[i];
        // Der alte Inhalt gilt nicht; der Semaphor „bereit“ wird in der Stufe der Farbziele abgewartet, die Sperre zum
        // Zeichnen hängt sich dort an.
        Mittel.Stand st = z.stand();
        st.anordnung = VK_IMAGE_LAYOUT_UNDEFINED;
        for (Mittel.Stand.Seite x : st.seiten) {
            x.benutzt = false;
            x.schreibend = false;
        }
        Mittel.Stand.Seite x = st.seiten[g.grafik.platz];
        x.benutzt = true;
        x.stufen = VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT;
        x.gedeckt = VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT;
        x.zugriffe = 0;
        VkCommandBuffer c = g.grafik.befehle();
        aufnahme.accept(c, z);
        new Ablauf(g, g.grafik).zeigen(z).aufnehmen(c, null);
        wert[f] = g.grafik.schicke(c, leiste, warteAuf, warteWert, bereit[f], VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, fertig[i]);
        int r = g.grafik.zeige(handle, i, fertig[i]);
        if (r == VK_ERROR_OUT_OF_DATE_KHR || r == VK_SUBOPTIMAL_KHR) neuBauen = true;
        else Geraet.ok(r, "vkQueuePresentKHR");
        gezeigt++;
        return true;
    }

    private void bauen() {
        for (long w : wert) if (w > 0) leiste.warte(w, Long.MAX_VALUE);
        vkDeviceWaitIdle(g.device);
        try (MemoryStack s = stackPush()) {
            VkSurfaceCapabilitiesKHR cap = VkSurfaceCapabilitiesKHR.malloc(s);
            Geraet.ok(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(g.phys, flaeche, cap), "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
            if (format == VK_FORMAT_UNDEFINED) {
                IntBuffer n = s.mallocInt(1);
                vkGetPhysicalDeviceSurfaceFormatsKHR(g.phys, flaeche, n, null);
                VkSurfaceFormatKHR.Buffer fm = VkSurfaceFormatKHR.malloc(n.get(0), s);
                vkGetPhysicalDeviceSurfaceFormatsKHR(g.phys, flaeche, n, fm);
                format = fm.get(0).format();
                raum = fm.get(0).colorSpace();
                for (int i = 0; i < fm.capacity(); i++)
                    if ((fm.get(i).format() == VK_FORMAT_B8G8R8A8_SRGB || fm.get(i).format() == VK_FORMAT_R8G8B8A8_SRGB) && fm.get(i).colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                        format = fm.get(i).format();
                        raum = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
                        break;
                    }
                vkGetPhysicalDeviceSurfacePresentModesKHR(g.phys, flaeche, n, null);
                IntBuffer m = s.mallocInt(n.get(0));
                vkGetPhysicalDeviceSurfacePresentModesKHR(g.phys, flaeche, n, m);
                modus = VK_PRESENT_MODE_FIFO_KHR;
                if (!bildsynchron) for (int i = 0; i < m.capacity(); i++) if (m.get(i) == VK_PRESENT_MODE_MAILBOX_KHR || m.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR && modus == VK_PRESENT_MODE_FIFO_KHR) modus = m.get(i);
            }
            int w = cap.currentExtent().width(), h = cap.currentExtent().height();
            if (w == 0xFFFFFFFF) {
                w = Math.max(1, canvas.getWidth());
                h = Math.max(1, canvas.getHeight());
            }
            long alt = handle;
            for (Bild b : bilder) b.close();
            for (long sf : fertig) vkDestroySemaphore(g.device, sf, null);
            bilder = new Bild[0];
            fertig = new long[0];
            breite = w;
            hoehe = h;
            if (w == 0 || h == 0) return;
            int anzahl = Math.max(cap.minImageCount() + 1, 3);
            if (cap.maxImageCount() > 0) anzahl = Math.min(anzahl, cap.maxImageCount());
            // seit W3 (Werkbuch HalligHooge 116, F194 A): auch Quelle einer Kopie, wenn die Fläche es kann (Überblendung)
            int nutzung = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT
                    | (cap.supportedUsageFlags() & VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
            VkSwapchainCreateInfoKHR ci = VkSwapchainCreateInfoKHR.calloc(s).sType$Default().surface(flaeche).minImageCount(anzahl).imageFormat(format).imageColorSpace(raum)
                    .imageArrayLayers(1).imageUsage(nutzung).imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(cap.currentTransform()).compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR).presentMode(modus).clipped(true).oldSwapchain(alt);
            ci.imageExtent().set(w, h);
            LongBuffer lb = s.mallocLong(1);
            Geraet.ok(vkCreateSwapchainKHR(g.device, ci, null, lb), "vkCreateSwapchainKHR");
            handle = lb.get(0);
            if (alt != VK_NULL_HANDLE) {
                vkDestroySwapchainKHR(g.device, alt, null);
                neu++;
            }
            IntBuffer n = s.mallocInt(1);
            vkGetSwapchainImagesKHR(g.device, handle, n, null);
            LongBuffer bl = s.mallocLong(n.get(0));
            vkGetSwapchainImagesKHR(g.device, handle, n, bl);
            bilder = new Bild[bl.capacity()];
            fertig = new long[bl.capacity()];
            for (int i = 0; i < bilder.length; i++) {
                bilder[i] = Bild.fremd(g, "Swapchain " + i, bl.get(i), w, h, format, nutzung);
                Geraet.ok(vkCreateSemaphore(g.device, VkSemaphoreCreateInfo.calloc(s).sType$Default(), null, lb), "vkCreateSemaphore");
                fertig[i] = lb.get(0);
            }
            neuBauen = false;
        }
    }

    /** Name der Darstellungsart für Meldungen. */
    public String modus() {
        return switch (modus) {
            case VK_PRESENT_MODE_FIFO_KHR -> "FIFO";
            case VK_PRESENT_MODE_MAILBOX_KHR -> "MAILBOX";
            case VK_PRESENT_MODE_IMMEDIATE_KHR -> "IMMEDIATE";
            default -> "Art " + modus;
        };
    }

    @Override public void close() {
        for (long w : wert) if (w > 0) leiste.warte(w, Long.MAX_VALUE);
        vkDeviceWaitIdle(g.device);
        for (Bild b : bilder) b.close();
        for (long sf : fertig) vkDestroySemaphore(g.device, sf, null);
        for (long b : bereit) vkDestroySemaphore(g.device, b, null);
        if (handle != VK_NULL_HANDLE) vkDestroySwapchainKHR(g.device, handle, null);
        vkDestroySurfaceKHR(g.instance, flaeche, null);
        leiste.close();
    }
}
