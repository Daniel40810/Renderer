package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier2;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkViewport;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Ein Ablauf aus Durchgängen (Werkbuch HalligHooge Teil A, Abschnitt 6: „Graph aus Durchgängen und Ressourcen;
 * Barrieren und Übergänge rechnet der Graph selbst“). Jeder Durchgang sagt, welche {@link Mittel} er liest und
 * schreibt; der Ablauf setzt davor genau die Sperren, die nötig sind:
 * <ul>
 * <li>Lesen nach Lesen braucht keine Sperre.</li>
 * <li>Lesen nach Schreiben, Schreiben nach Lesen und Schreiben nach Schreiben brauchen eine – für dieses eine
 * Betriebsmittel, nicht für alle.</li>
 * <li>Ein {@link Bild} wechselt dabei die Anordnung (Layout), wenn der Durchgang eine andere verlangt.</li>
 * </ul>
 * Der Stand des letzten Zugriffs liegt am Betriebsmittel selbst, je Warteschlange für sich; ein Ablauf darf also über
 * mehrere Befehlspuffer und Aufrufe hinweg aufgenommen werden. Der Stand gilt in der Reihenfolge der Aufnahme: wer
 * ein Betriebsmittel in zwei Befehlspuffern aufnimmt, schickt sie in dieser Reihenfolge ab, und je Betriebsmittel
 * nimmt ein Faden zur Zeit auf. Arbeit auf einer anderen Warteschlange trennt eine {@link Zeitleiste}, keine Sperre;
 * der Ablauf nennt darum in einer Sperre nur Zugriffe seiner eigenen Warteschlange.
 * <p>
 * Die Durchgänge laufen in der Reihenfolge, in der sie gemeldet sind. Mit einer {@link Stoppuhr} bekommt jeder
 * Durchgang seinen Abschnitt.
 * <p>
 * Seit P6a zeichnet ein Ablauf auch ({@link #zeichne}: dynamisches Rendern in Farb- und Tiefenziele), liest Bilder als
 * Textur ({@link #textur}) und gibt ein Bild der Swapchain zum Zeigen frei ({@link #zeigen}).
 */
public final class Ablauf {
    /** Ein Zugriff eines Durchgangs: Betriebsmittel, Stufen, Zugriffe, bei Bildern die Anordnung. */
    public record Zugriff(Mittel mittel, long stufe, long zugriff, int anordnung) { }

    private record Durchgang(String name, Zugriff[] zugriffe, Consumer<VkCommandBuffer> aufnahme) { }

    private static final long SCHREIBEN = VK_ACCESS_2_SHADER_WRITE_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT | VK_ACCESS_2_TRANSFER_WRITE_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT
            | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT | VK_ACCESS_2_HOST_WRITE_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT;

    private final Schlange schlange;
    private final List<Durchgang> durchgaenge = new ArrayList<>();
    private int sperren, gesperrt;

    /** Ein Ablauf für die Rechen-Warteschlange. */
    public Ablauf(Geraet g) { this(g, g.rechnen); }

    /** Ein Ablauf für diese Warteschlange; er darf nur in ihre Befehlspuffer aufgenommen werden. */
    public Ablauf(Geraet g, Schlange schlange) { this.schlange = schlange; }

    /** Zugriffe eines Kernels, der diese Betriebsmittel liest. */
    public static Zugriff[] liest(Mittel... mittel) {
        Zugriff[] z = new Zugriff[mittel.length];
        for (int i = 0; i < z.length; i++) z[i] = new Zugriff(mittel[i], VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT, VK_ACCESS_2_SHADER_READ_BIT, VK_IMAGE_LAYOUT_GENERAL);
        return z;
    }

    /** Zugriffe eines Kernels, der diese Betriebsmittel schreibt (und dabei auch lesen darf). */
    public static Zugriff[] schreibt(Mittel... mittel) {
        Zugriff[] z = new Zugriff[mittel.length];
        for (int i = 0; i < z.length; i++)
            z[i] = new Zugriff(mittel[i], VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT, VK_ACCESS_2_SHADER_READ_BIT | VK_ACCESS_2_SHADER_WRITE_BIT, VK_IMAGE_LAYOUT_GENERAL);
        return z;
    }

    /** Stufen, in denen ein Zeichner liest: Scheitel- und Bildpunkt-Shader. */
    public static final long ZEICHNEN = VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT;
    /** Stufe, in der ein Kernel liest. */
    public static final long RECHNEN = VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT;

    /** Zugriffe, die diese Bilder in den Stufen {@code stufe} als Textur lesen ({@link Kernel.Bindung#TEXTUR}). */
    public static Zugriff[] textur(long stufe, Bild... bilder) {
        Zugriff[] z = new Zugriff[bilder.length];
        for (int i = 0; i < z.length; i++) z[i] = new Zugriff(bilder[i], stufe, VK_ACCESS_2_SHADER_SAMPLED_READ_BIT, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        return z;
    }

    /** Zugriffe, die diese Puffer oder Speicherbilder in den Stufen {@code stufe} lesen. */
    public static Zugriff[] liest(long stufe, Mittel... mittel) {
        Zugriff[] z = new Zugriff[mittel.length];
        for (int i = 0; i < z.length; i++) z[i] = new Zugriff(mittel[i], stufe, VK_ACCESS_2_SHADER_STORAGE_READ_BIT, VK_IMAGE_LAYOUT_GENERAL);
        return z;
    }

    /** Hängt Zugriffe aneinander. */
    public static Zugriff[] und(Zugriff[]... teile) {
        int n = 0;
        for (Zugriff[] t : teile) n += t.length;
        Zugriff[] z = new Zugriff[n];
        int k = 0;
        for (Zugriff[] t : teile) for (Zugriff x : t) z[k++] = x;
        return z;
    }

    /** Ein Durchgang mit beliebigen Befehlen. Nennt ein Durchgang ein Betriebsmittel zweimal, gelten beide Zugriffe zusammen. */
    public Ablauf durchgang(String name, Zugriff[] zugriffe, Consumer<VkCommandBuffer> aufnahme) {
        List<Zugriff> l = new ArrayList<>();
        for (Zugriff z : zugriffe) {
            int da = -1;
            for (int i = 0; i < l.size(); i++) if (l.get(i).mittel == z.mittel) da = i;
            if (da < 0) l.add(z);
            else l.set(da, new Zugriff(z.mittel, l.get(da).stufe | z.stufe, l.get(da).zugriff | z.zugriff, z.anordnung));
        }
        durchgaenge.add(new Durchgang(name, l.toArray(new Zugriff[0]), aufnahme));
        return this;
    }

    /**
     * Ein Durchgang, der einen Kernel ausführt. Die Push-Konstanten werden jetzt kopiert; der Puffer darf danach
     * vergehen (etwa mit dem Stapel).
     */
    public Ablauf rechne(String name, Kernel kernel, long satz, ByteBuffer push, int gx, int gy, int gz, Mittel[] liest, Mittel[] schreibt) {
        byte[] p = null;
        if (push != null) {
            p = new byte[Math.min(push.capacity(), kernel.pushGroesse)];
            push.duplicate().position(0).limit(p.length).get(p);
        }
        byte[] kopie = p;
        Zugriff[] a = liest(liest), b = schreibt(schreibt), z = new Zugriff[a.length + b.length];
        System.arraycopy(a, 0, z, 0, a.length);
        System.arraycopy(b, 0, z, a.length, b.length);
        return durchgang(name, z, c -> {
            try (MemoryStack s = stackPush()) {
                kernel.aufnehmen(c, satz, kopie == null ? null : s.bytes(kopie), gx, gy, gz);
            }
        });
    }

    /** Ein Durchgang, der einen Kernel mit diesen Zugriffen ausführt (etwa mit Texturen, {@link #textur}). */
    public Ablauf rechne(String name, Kernel kernel, long satz, ByteBuffer push, int gx, int gy, int gz, Zugriff[] zugriffe) {
        byte[] p = null;
        if (push != null) {
            p = new byte[Math.min(push.capacity(), kernel.pushGroesse)];
            push.duplicate().position(0).limit(p.length).get(p);
        }
        byte[] kopie = p;
        return durchgang(name, zugriffe, c -> {
            try (MemoryStack s = stackPush()) {
                kernel.aufnehmen(c, satz, kopie == null ? null : s.bytes(kopie), gx, gy, gz);
            }
        });
    }

    /**
     * Ein Durchgang, der zeichnet: dynamisches Rendern in die Farbziele {@code farben} und, wenn nicht null, die Tiefe.
     * {@code loeschen} ist null (Inhalt bleibt) oder je Farbziel RGBA; {@code tiefeLoeschen} ist null (bleibt) oder der
     * Wert (bei umgekehrter Tiefe 0). Bildfeld und Schere decken das ganze erste Ziel; das Bildfeld steht so, dass y im
     * Bild nach unten zeigt (wie in Vulkan). {@code liest} nennt, was die Zeichner darin lesen.
     */
    public Ablauf zeichne(String name, Bild[] farben, float[][] loeschen, Bild tiefe, Float tiefeLoeschen, Zugriff[] liest, Consumer<VkCommandBuffer> befehle) {
        int nz = farben.length + (tiefe != null ? 1 : 0);
        Zugriff[] ziele = new Zugriff[nz];
        for (int i = 0; i < farben.length; i++)
            ziele[i] = new Zugriff(farben[i], VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT | (loeschen == null ? VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT : 0), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        if (tiefe != null)
            ziele[nz - 1] = new Zugriff(tiefe, VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL);
        Bild erstes = farben.length > 0 ? farben[0] : tiefe;
        int w = erstes.breite, h = erstes.hoehe;
        return durchgang(name, und(ziele, liest == null ? new Zugriff[0] : liest), c -> {
            try (MemoryStack s = stackPush()) {
                VkRenderingAttachmentInfo.Buffer ca = VkRenderingAttachmentInfo.calloc(farben.length, s);
                for (int i = 0; i < farben.length; i++) {
                    ca.get(i).sType$Default().imageView(farben[i].ansicht).imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                            .loadOp(loeschen == null ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                    if (loeschen != null) ca.get(i).clearValue().color().float32(0, loeschen[i][0]).float32(1, loeschen[i][1]).float32(2, loeschen[i][2]).float32(3, loeschen[i][3]);
                }
                VkRenderingInfo ri = VkRenderingInfo.calloc(s).sType$Default().layerCount(1).pColorAttachments(ca);
                if (tiefe != null) {
                    VkRenderingAttachmentInfo da = VkRenderingAttachmentInfo.calloc(s).sType$Default().imageView(tiefe.ansicht).imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                            .loadOp(tiefeLoeschen == null ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                    if (tiefeLoeschen != null) da.clearValue().depthStencil().depth(tiefeLoeschen);
                    ri.pDepthAttachment(da);
                }
                ri.renderArea().extent().set(w, h);
                vkCmdBeginRendering(c, ri);
                vkCmdSetViewport(c, 0, VkViewport.calloc(1, s).x(0).y(0).width(w).height(h).minDepth(0).maxDepth(1));
                VkRect2D.Buffer sc = VkRect2D.calloc(1, s);
                sc.get(0).extent().set(w, h);
                vkCmdSetScissor(c, 0, sc);
                befehle.accept(c);
                vkCmdEndRendering(c);
            }
        });
    }

    /** Ein Durchgang ohne Befehle, der ein Bild der Swapchain zum Zeigen freigibt (Anordnung zum Zeigen). */
    public Ablauf zeigen(Bild b) {
        return durchgang("zeigen", new Zugriff[] {new Zugriff(b, VK_PIPELINE_STAGE_2_NONE, 0, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)}, c -> { });
    }

    /** Vergisst alle Durchgänge (der Stand der Betriebsmittel bleibt). */
    public Ablauf leere() {
        durchgaenge.clear();
        return this;
    }

    public int durchgaenge() { return durchgaenge.size(); }

    /** Nimmt alle Durchgänge mit ihren Sperren in den Befehlspuffer auf; {@code uhr} ist null oder misst je Durchgang. */
    public void aufnehmen(VkCommandBuffer c, Stoppuhr uhr) {
        sperren = gesperrt = 0;
        for (Durchgang d : durchgaenge) {
            sperre(c, d.zugriffe);
            if (uhr != null) uhr.start(c, d.name);
            d.aufnahme.accept(c);
            if (uhr != null) uhr.stopp(c, d.name);
        }
    }

    /** Zahl der Sperrbefehle der letzten Aufnahme. */
    public int sperren() { return sperren; }

    /** Zahl der Betriebsmittel, die in der letzten Aufnahme gesperrt wurden. */
    public int gesperrt() { return gesperrt; }

    private void sperre(VkCommandBuffer c, Zugriff[] zugriffe) {
        try (MemoryStack s = stackPush()) {
            int np = 0, nb = 0;
            boolean[] noetig = new boolean[zugriffe.length], nachziehen = new boolean[zugriffe.length];
            for (int i = 0; i < zugriffe.length; i++) {
                Zugriff z = zugriffe[i];
                Mittel.Stand.Seite st = z.mittel.stand().seiten[schlange.platz];
                boolean bild = z.mittel instanceof Bild, schreibt = (z.zugriff & SCHREIBEN) != 0, wechsel = bild && z.mittel.stand().anordnung != z.anordnung;
                noetig[i] = wechsel || st.benutzt && (st.schreibend || schreibt);
                // Lesen nach Lesen in einer Stufe, für die der letzte Schreibzugriff (oder Wechsel der Anordnung) nicht
                // sichtbar gemacht wurde: eine Sperre, die an die Zielstufen der letzten anschließt
                nachziehen[i] = !noetig[i] && st.benutzt && (z.stufe & ~st.gedeckt) != 0;
                if (!noetig[i] && !nachziehen[i]) {
                    if (st.benutzt) {                                   // Lesen nach Lesen: die Leser sammeln sich
                        st.stufen |= z.stufe;
                        st.zugriffe |= z.zugriff;
                    } else {                                            // erster Zugriff auf dieser Warteschlange
                        st.benutzt = true;
                        st.stufen = z.stufe;
                        st.zugriffe = z.zugriff;
                        st.schreibend = schreibt;
                        st.gedeckt = -1L;                               // nichts davor auf dieser Warteschlange
                    }
                } else if (bild) nb++; else np++;
            }
            if (np + nb == 0) return;
            VkBufferMemoryBarrier2.Buffer pb = np > 0 ? VkBufferMemoryBarrier2.calloc(np, s) : null;
            VkImageMemoryBarrier2.Buffer bb = nb > 0 ? VkImageMemoryBarrier2.calloc(nb, s) : null;
            int ip = 0, ib = 0;
            for (int i = 0; i < zugriffe.length; i++) {
                if (!noetig[i] && !nachziehen[i]) continue;
                Zugriff z = zugriffe[i];
                Mittel.Stand ganz = z.mittel.stand();
                Mittel.Stand.Seite st = ganz.seiten[schlange.platz];
                long vonStufe = st.benutzt ? st.stufen : VK_PIPELINE_STAGE_2_NONE, vonZugriff = st.benutzt ? st.zugriffe : 0;
                if (nachziehen[i]) {                                    // Kette: an die gedeckten Stufen, nichts zu schreiben
                    vonStufe = st.gedeckt;
                    vonZugriff = 0;
                }
                if (z.mittel instanceof Bild b) {
                    VkImageMemoryBarrier2 x = bb.get(ib++).sType$Default().srcStageMask(vonStufe).srcAccessMask(vonZugriff).dstStageMask(z.stufe).dstAccessMask(z.zugriff)
                            .oldLayout(ganz.anordnung).newLayout(z.anordnung).srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(b.handle);
                    x.subresourceRange().aspectMask(b.aspekt()).levelCount(VK_REMAINING_MIP_LEVELS).layerCount(VK_REMAINING_ARRAY_LAYERS);
                    ganz.anordnung = z.anordnung;
                } else {
                    pb.get(ip++).sType$Default().srcStageMask(vonStufe).srcAccessMask(vonZugriff).dstStageMask(z.stufe).dstAccessMask(z.zugriff)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).buffer(((Puffer) z.mittel).handle).offset(0).size(VK_WHOLE_SIZE);
                }
                if (nachziehen[i]) {
                    st.stufen |= z.stufe;
                    st.zugriffe |= z.zugriff;
                    st.gedeckt |= z.stufe;
                } else {
                    st.benutzt = true;
                    st.stufen = z.stufe;
                    st.zugriffe = z.zugriff;
                    st.schreibend = (z.zugriff & SCHREIBEN) != 0;
                    st.gedeckt = z.stufe;
                }
            }
            VkDependencyInfo di = VkDependencyInfo.calloc(s).sType$Default();
            if (pb != null) di.pBufferMemoryBarriers(pb);
            if (bb != null) di.pImageMemoryBarriers(bb);
            vkCmdPipelineBarrier2(c, di);
            sperren++;
            gesperrt += np + nb;
        }
    }
}
