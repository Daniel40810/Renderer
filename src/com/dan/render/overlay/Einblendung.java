package com.dan.render.overlay;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Auflage;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Zeichner;
import com.dan.core.raum.Blick;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkImageCopy;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Die Einblendungen über dem fertigen Bild (seit W2, Werkbuch HalligHooge 113, F186 A): {@link Kachel}n, in Java2D
 * gemalt, als Texturen hochgeladen, wenn sie sich ändern, und nach der Filmkurve vormultipliziert über das Bild gemischt.
 * Eine {@link Auflage}: render.frame ruft sie wie den Messblick; ohne Kacheln oder ausgeschaltet kostet sie
 * nichts, und das Bild bleibt bitgleich.
 * <ul>
 * <li><b>Lage am Rand:</b> eine Ecke oder Kantenmitte und ein Abstand in Bildpunkten.</li>
 * <li><b>Lage in der Welt:</b> ein Ort (UTM, m NHN) wird mit dem Blick des Bilds projiziert, die Krümmung der Erde wie
 * bei Häusern und Gelände (Radius {@link #kruemmung}). Mit {@code verdecken} liest der Scheitel-Shader die Tiefe der
 * Szene an fünf Stellen um den Ort; wo die Szene näher ist, blendet die Kachel anteilig aus.</li>
 * <li><b>Hochladen:</b> je Kachel ein Bild (RGBA16F, linear vormultipliziert) und drei Zwischenpuffer im Wechsel, damit
 * der Prozessor nie in einen Puffer schreibt, den die Karte noch liest (zwei Bilder sind zugleich unterwegs). Kopiert
 * wird im Ablauf des Bilds, nicht in einem eigenen Abschicken.</li>
 * <li><b>Stoppuhr:</b> Abschnitte „Einblendung, hochladen“ und „Einblendung“.</li>
 * </ul>
 * Die Kacheln dürfen aus jedem Faden kommen und gehen; die Mittel der Karte gehören dem Faden der Karte und werden erst
 * einige Bilder nach dem Entfernen einer Kachel freigegeben.
 */
public final class Einblendung implements Auflage {
    static final String PFAD = "/com/dan/render/overlay/";
    /** Zwischenpuffer je Kachel im Wechsel. */
    static final int RING = 3;
    /** So viele Bilder nach dem Entfernen bleiben die Mittel einer Kachel liegen. */
    static final int NACHLAUF = 4;
    /** Größe der Push-Konstanten: Rechteck, Ziel, Anker (je vec4). */
    static final int PUSH = 48;

    private final Geraet g;
    private final Konto konto;
    private final List<Kachel> kacheln = new CopyOnWriteArrayList<>();
    private final Abtaster punkt, linear;
    private final Map<Integer, Zeichner> zeichner = new HashMap<>();

    /** An oder aus; aus kostet nichts. */
    public volatile boolean an = true;
    /** Radius der Krümmung für Orte der Welt (m; 0: eben); die Bühne setzt ihn wie für Häuser und Bewuchs. */
    public volatile double kruemmung;

    /** Platz einer Kachel auf der Karte (nur im Faden der Karte). */
    private static final class Platz {
        Bild bild;
        final Puffer[] hin = new Puffer[RING];
        ByteBuffer roh;
        int ring;
        boolean geladen;
        long weg = -1;
    }

    private final Map<Kachel, Platz> mittel = new IdentityHashMap<>();
    private final List<Platz> abfall = new ArrayList<>();
    private long bildNr;
    private boolean zu;

    // ---- Blende (seit W3, F194 A): das Bild vor einem Schnitt merken und über das neue legen
    private volatile boolean merkWunsch;
    private volatile long gemerkt;
    private volatile boolean merkenMoeglich = true;
    /** Deckung des gemerkten Bilds über dem jetzigen (0: keine Überblendung); setzt die Anwendung je Bild. */
    public volatile double blende;
    private Bild merkBild;
    private final Map<Integer, Zeichner> blendeZeichner = new HashMap<>();

    /** Zuletzt gezeichnet: Zahl der Kacheln und hochgeladene Bytes (für Prüfungen und Anzeigen). */
    public volatile int zuletztGezeichnet;
    public volatile long zuletztHochgeladen;

    public Einblendung(Geraet g, Konto konto) {
        this.g = g;
        this.konto = konto;
        punkt = new Abtaster(g, false, false);
        linear = new Abtaster(g, true, false);
    }

    @Override public String name() { return "Einblendung"; }

    /** Eine neue Kachel der Größe {@code breite × hoehe} Bildpunkte; sie ist sichtbar, sobald sie gemalt ist. */
    public Kachel neu(String name, int breite, int hoehe) {
        Kachel k = new Kachel(name, breite, hoehe);
        kacheln.add(k);
        return k;
    }

    /** Nimmt eine Kachel heraus; ihre Mittel gibt der Faden der Karte einige Bilder später frei. */
    public void weg(Kachel k) { kacheln.remove(k); }

    /** Alle Kacheln, in der Reihenfolge des Zeichnens (spätere liegen oben). */
    public List<Kachel> kacheln() { return List.copyOf(kacheln); }

    /**
     * Seit W3: das nächste fertige Bild (vor den Kacheln) merken. {@link #gemerkt()} zählt hoch, sobald es aufgenommen
     * ist; kann die Ausgabe nicht Quelle einer Kopie sein, zählt es trotzdem hoch und {@link #merkenMoeglich()} wird
     * false (dann schneidet die Anwendung hart).
     */
    public void merken() { merkWunsch = true; }

    /** Wie viele Bilder bisher gemerkt sind. */
    public long gemerkt() { return gemerkt; }

    /** Ob die Ausgabe zuletzt kopiert werden konnte. */
    public boolean merkenMoeglich() { return merkenMoeglich; }

    /** Ist etwas zu zeichnen? */
    public boolean leer() {
        if (merkWunsch || blende > 0) return false;
        if (!an) return true;
        for (Kachel k : kacheln) if (k.sichtbar && k.gemalt()) return false;
        return true;
    }

    // ------------------------------------------------------------------ Auflage

    @Override public synchronized void vorbereiten(Ablauf a, Bildziel z) {
        if (zu) return;
        bildNr++;
        // entfernte Kacheln: Mittel in den Abfall, abgelaufener Abfall frei
        for (Iterator<Map.Entry<Kachel, Platz>> it = mittel.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Kachel, Platz> e = it.next();
            if (!kacheln.contains(e.getKey())) {
                e.getValue().weg = bildNr;
                abfall.add(e.getValue());
                it.remove();
            }
        }
        for (Iterator<Platz> it = abfall.iterator(); it.hasNext(); ) {
            Platz m = it.next();
            if (bildNr - m.weg > NACHLAUF) {
                frei(m);
                it.remove();
            }
        }
        for (Iterator<Object[]> it = abfallBild.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            if (bildNr - (Long) o[1] > NACHLAUF) {
                ((Bild) o[0]).close();
                it.remove();
            }
        }
        if (!an) return;
        List<Kachel> neu = new ArrayList<>();
        List<Platz> ziele = new ArrayList<>();
        long bytes = 0;
        for (Kachel k : kacheln) {
            if (!k.sichtbar) continue;
            short[] d = k.nimmNeu();
            if (d == null) continue;
            Platz m = mittel.computeIfAbsent(k, x -> anlegen(x));
            int n = d.length * 2;
            m.roh.clear();
            m.roh.asShortBuffer().put(d);
            m.roh.position(0).limit(n);
            m.ring = (m.ring + 1) % RING;
            m.hin[m.ring].schreibe(m.roh, 0);
            m.geladen = true;
            neu.add(k);
            ziele.add(m);
            bytes += n;
        }
        zuletztHochgeladen = bytes;
        if (neu.isEmpty()) return;
        Ablauf.Zugriff[] zg = new Ablauf.Zugriff[2 * neu.size()];
        for (int i = 0; i < neu.size(); i++) {
            Platz m = ziele.get(i);
            zg[2 * i] = new Ablauf.Zugriff(m.hin[m.ring], VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, VK_IMAGE_LAYOUT_GENERAL);
            zg[2 * i + 1] = new Ablauf.Zugriff(m.bild, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        }
        a.durchgang("Einblendung, hochladen", zg, c -> {
            try (MemoryStack s = MemoryStack.stackPush()) {
                for (int i = 0; i < neu.size(); i++) {
                    Kachel k = neu.get(i);
                    Platz m = ziele.get(i);
                    VkBufferImageCopy.Buffer kp = VkBufferImageCopy.calloc(1, s);
                    kp.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                    kp.get(0).imageExtent().set(k.breite, k.hoehe, 1);
                    vkCmdCopyBufferToImage(c, m.hin[m.ring].handle, m.bild.handle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, kp);
                }
            }
        });
    }

    private Platz anlegen(Kachel k) {
        Platz m = new Platz();
        long n = 8L * k.breite * k.hoehe;
        m.bild = new Bild(g, konto, "Einblendung " + k.name, k.breite, k.hoehe, Bild.RGBA16F);
        for (int i = 0; i < RING; i++) m.hin[i] = Puffer.neu(g, konto, "Einblendung " + k.name + " (hin " + i + ")", n, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, Puffer.Art.WIRT);
        m.roh = ByteBuffer.allocateDirect((int) n).order(ByteOrder.LITTLE_ENDIAN);
        return m;
    }

    private static void frei(Platz m) {
        if (m.bild != null) m.bild.close();
        for (Puffer p : m.hin) if (p != null) p.close();
    }

    /** Eine Kachel, wie sie in diesem Bild liegt: links oben, Anker (Bildpunkt, Abstand; −1 ohne Verdecken), Deckung. */
    record Lage(Kachel k, Platz m, double x, double y, double ax, double ay, double ad, double deckung, double dreh) { }

    @Override public synchronized void auflegen(Ablauf a, Bildziel z, Bild aus) {
        if (zu) return;
        blendeAuflegen(a, aus);
        if (!an || z.blick == null) {
            zuletztGezeichnet = 0;
            return;
        }
        List<Lage> lagen = lagen(z.blick, aus.breite, aus.hoehe);
        zuletztGezeichnet = lagen.size();
        if (lagen.isEmpty()) return;
        Zeichner zk = zeichner.computeIfAbsent(aus.format, fm -> {
            Zeichner.Art art = new Zeichner.Art().ohneTiefe();
            art.farben = new int[] {fm};
            art.mischen = true;
            return Zeichner.lade(g, PFAD + "kachel.vert", PFAD + "kachel.frag", PUSH, null, art, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR);
        });
        double nah = z.blick.nah;
        int n = lagen.size();
        long[] saetze = new long[n];
        byte[][] push = new byte[n][];
        Bild[] gelesen = new Bild[n + 1];
        gelesen[0] = z.tiefe;
        for (int i = 0; i < n; i++) {
            Lage l = lagen.get(i);
            saetze[i] = zk.satz(punkt.an(z.tiefe), linear.an(l.m.bild));
            ByteBuffer p = ByteBuffer.allocate(PUSH).order(ByteOrder.nativeOrder());
            p.putFloat((float) l.x).putFloat((float) l.y).putFloat(l.k.zb()).putFloat(l.k.zh());
            p.putFloat(aus.breite).putFloat(aus.hoehe).putFloat((float) l.dreh).putFloat((float) l.deckung);
            p.putFloat((float) l.ax).putFloat((float) l.ay).putFloat((float) l.ad).putFloat((float) nah);
            push[i] = p.array();
            gelesen[i + 1] = l.m.bild;
        }
        a.zeichne("Einblendung", new Bild[] {aus}, null, null, null, Ablauf.textur(Ablauf.ZEICHNEN, gelesen), c -> {
            try (MemoryStack s = MemoryStack.stackPush()) {
                for (int i = 0; i < n; i++) zk.zeichne(c, saetze[i], s.bytes(push[i]), 6, 1);
            }
        });
    }

    /** Merkt das Bild, wenn gewünscht, und legt das gemerkte mit {@link #blende} darüber. */
    private void blendeAuflegen(Ablauf a, Bild aus) {
        if (merkWunsch) {
            merkWunsch = false;
            if ((aus.nutzung & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0) {
                merkenMoeglich = false;
                gemerkt++;
                return;
            }
            merkenMoeglich = true;
            if (merkBild != null && (merkBild.breite != aus.breite || merkBild.hoehe != aus.hoehe || merkBild.format != aus.format)) {
                abfallBild.add(new Object[] {merkBild, bildNr});
                merkBild = null;
            }
            if (merkBild == null) merkBild = new Bild(g, konto, "Einblendung, gemerkt", aus.breite, aus.hoehe, aus.format);
            Bild ziel = merkBild;
            a.durchgang("Einblendung, merken", new Ablauf.Zugriff[] {
                    new Ablauf.Zugriff(aus, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL),
                    new Ablauf.Zugriff(ziel, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)}, c -> {
                try (MemoryStack s = MemoryStack.stackPush()) {
                    VkImageCopy.Buffer k = VkImageCopy.calloc(1, s);
                    k.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                    k.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                    k.get(0).extent().set(ziel.breite, ziel.hoehe, 1);
                    vkCmdCopyImage(c, aus.handle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, ziel.handle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, k);
                }
            });
            gemerkt++;
            return;                                                         // in diesem Bild noch keine Blende
        }
        double d = blende;
        Bild mb = merkBild;
        if (d <= 0 || mb == null || mb.breite != aus.breite || mb.hoehe != aus.hoehe) return;
        Zeichner zb = blendeZeichner.computeIfAbsent(aus.format, fm -> {
            Zeichner.Art art = new Zeichner.Art().ohneTiefe();
            art.farben = new int[] {fm};
            art.mischen = true;
            return Zeichner.lade(g, PFAD + "voll.vert", PFAD + "blende.frag", 16, null, art, Kernel.Bindung.TEXTUR);
        });
        long satz = zb.satz(punkt.an(mb));
        float df = (float) Math.min(1, d);
        a.zeichne("Einblendung, Blende", new Bild[] {aus}, null, null, null, Ablauf.textur(Ablauf.ZEICHNEN, mb), c -> {
            try (MemoryStack s = MemoryStack.stackPush()) {
                zb.zeichne(c, satz, s.malloc(16).putFloat(0, df).putFloat(4, 0).putFloat(8, 0).putFloat(12, 0), 3, 1);
            }
        });
    }

    /** Gemerkte Bilder alter Größe, mit der Nummer des Bilds, ab der sie frei werden dürfen. */
    private final List<Object[]> abfallBild = new ArrayList<>();

    /** Die Lagen aller sichtbaren, hochgeladenen Kacheln in einem Bild W × H mit dem Blick {@code blick}. */
    private List<Lage> lagen(Blick blick, int W, int H) {
        Blick b = blick.kopie().groesse(W, H);
        double R = kruemmung;
        List<Lage> rand = new ArrayList<>(), welt = new ArrayList<>();
        for (Kachel k : kacheln) {
            Platz m = mittel.get(k);
            if (!k.sichtbar || m == null || !m.geladen || k.deckung <= 0) {
                k.zuletztX = k.zuletztY = Double.NaN;
                continue;
            }
            if (!k.inWelt) {
                double[] p = k.amRand(W, H);
                double dr = k.drehung + (k.nachNorden ? -Math.toRadians(b.richtung) : 0);
                k.zuletztX = p[0];
                k.zuletztY = p[1];
                k.zuletztDrehung = dr;
                rand.add(new Lage(k, m, p[0], p[1], 0, 0, -1, k.deckung, dr));
                continue;
            }
            double[] p = projiziere(b, k.ost, k.nord, k.h, R);
            k.zuletztX = k.zuletztY = Double.NaN;
            if (p == null) continue;
            double d = p[3], f = 1;
            if (k.weite > 0) {
                if (d >= k.weite) continue;
                if (d > 0.7 * k.weite) f = (k.weite - d) / (0.3 * k.weite);
            }
            double x = Math.round(p[0] - k.bx * k.zb()), y = Math.round(p[1] - k.by * k.zh());
            if (x + k.zb() < 0 || y + k.zh() < 0 || x > W || y > H) continue;
            double dr = k.drehung + (k.nachNorden ? -Math.toRadians(b.richtung) : 0);
            welt.add(new Lage(k, m, x, y, p[0], p[1], k.verdecken ? p[2] : -1, k.deckung * f, dr));
        }
        // ausweichen: der Rang zuerst, dann die Reihenfolge der Liste; was ein schon gesetztes Rechteck schneidet, tritt zurück.
        // Die Kacheln am Rand stehen von Anfang an (seit Lauf W2: Ortsnamen schienen durch die Uhr).
        List<Lage> sortiert = new ArrayList<>(welt);
        sortiert.sort(Comparator.comparingInt(l -> l.k.rang));
        List<Lage> gesetzt = new ArrayList<>(rand);
        java.util.Set<Kachel> zurueck = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Lage l : sortiert) {
            if (!l.k.ausweichen) continue;
            boolean frei = true;
            for (Lage o : gesetzt)
                if (l.x < o.x + o.k.zb() && o.x < l.x + l.k.zb() && l.y < o.y + o.k.zh() && o.y < l.y + l.k.zh()) {
                    frei = false;
                    break;
                }
            if (frei) gesetzt.add(l);
            else zurueck.add(l.k);
        }
        List<Lage> alle = new ArrayList<>();
        for (Lage l : welt) {
            if (zurueck.contains(l.k)) continue;
            l.k.zuletztX = l.x;
            l.k.zuletztY = l.y;
            l.k.zuletztDrehung = l.dreh;
            alle.add(l);
        }
        alle.addAll(rand);                                                  // der Rand liegt über der Welt
        return alle;
    }

    /**
     * Bildpunkt eines Orts (UTM, m NHN) im Blick b mit der Krümmung R (m; 0: eben): {x, y, Abstand längs der Achse (m),
     * Abstand waagrecht (m)}; null hinter dem Auge.
     */
    public static double[] projiziere(Blick b, double ost, double nord, double h, double R) {
        double dh = Math.hypot(ost - b.ost, nord - b.nord);
        double senke = R > 0 ? dh * dh / (2 * R) : 0;
        double[] p = b.bildpunkt(b.relativ(ost, nord, h - senke));
        if (p == null) return null;
        return new double[] {p[0], p[1], b.nah / p[2], dh};
    }

    @Override public synchronized void close() {
        if (zu) return;
        zu = true;
        for (Platz m : mittel.values()) frei(m);
        for (Platz m : abfall) frei(m);
        mittel.clear();
        abfall.clear();
        for (Zeichner zk : zeichner.values()) zk.close();
        zeichner.clear();
        for (Zeichner zk : blendeZeichner.values()) zk.close();
        blendeZeichner.clear();
        if (merkBild != null) merkBild.close();
        for (Object[] o : abfallBild) ((Bild) o[0]).close();
        abfallBild.clear();
        punkt.close();
        linear.close();
    }
}
