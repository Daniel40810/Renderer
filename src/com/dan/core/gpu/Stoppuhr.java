package com.dan.core.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocLong;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Zeiten auf der Karte je Abschnitt (Werkbuch HalligHooge Teil A, Abschnitt 6, „Zeiten“; aus {@code gputime}): benannte
 * Abschnitte mit Zeitstempeln im Befehlsstrom, ohne im Lauf zu warten.
 * <p>
 * Gebrauch je Befehlspuffer: {@link #runde} beginnt eine Runde, {@link #start} und {@link #stopp} klammern einen
 * Abschnitt (derselbe Name mehrfach: die Summe zählt), {@link #sammle} liest die Runden, deren Zeiten die Karte schon
 * gemeldet hat. {@link #ms} gibt das gleitende Mittel, {@link #letzte} den letzten Wert. Im Ring liegen
 * {@link #RUNDEN} Runden; wird eine Runde wiederverwendet, bevor sie gelesen ist, geht sie verloren ({@link #verloren}).
 * <p>
 * Hat die Warteschlange keine Zeitstempel ({@link #verfuegbar} ist false), tun alle Methoden nichts.
 */
public final class Stoppuhr implements AutoCloseable {
    public static final int RUNDEN = 4;

    private record Abschnitt(String name, int von, int bis) { }

    private final Geraet g;
    private final Schlange schlange;
    private final long pool;
    private final int stempel;
    public final boolean verfuegbar;
    /** Gewicht des neuen Werts im Mittel (0,1: rund zehn Runden). */
    public double gewicht = 0.1;
    private final List<List<Abschnitt>> runden = new ArrayList<>();
    private final int[] benutzt = new int[RUNDEN];
    private final Map<String, Integer> offen = new LinkedHashMap<>();
    private final Map<String, Double> mittel = new LinkedHashMap<>(), zuletzt = new LinkedHashMap<>();
    private int runde = -1;
    private long verloren, gelesen;

    /**
     * @param schlange   die Warteschlange, auf der gemessen wird
     * @param abschnitte größte Zahl von Abschnitten je Runde
     */
    public Stoppuhr(Geraet g, Schlange schlange, int abschnitte) {
        this.g = g;
        this.schlange = schlange;
        stempel = 2 * abschnitte;
        verfuegbar = schlange.zeitstempelBits > 0;
        for (int r = 0; r < RUNDEN; r++) runden.add(new ArrayList<>());
        if (!verfuegbar) {
            pool = VK_NULL_HANDLE;
            return;
        }
        try (MemoryStack s = stackPush()) {
            LongBuffer lp = s.mallocLong(1);
            Geraet.ok(vkCreateQueryPool(g.device, VkQueryPoolCreateInfo.calloc(s).sType$Default().queryType(VK_QUERY_TYPE_TIMESTAMP).queryCount(stempel * RUNDEN), null, lp),
                    "vkCreateQueryPool");
            pool = lp.get(0);
        }
    }

    /** Beginnt eine Runde in diesem Befehlspuffer. */
    public void runde(VkCommandBuffer c) {
        if (!verfuegbar) return;
        runde = (runde + 1) % RUNDEN;
        if (!runden.get(runde).isEmpty()) verloren++;
        runden.get(runde).clear();
        benutzt[runde] = 0;
        offen.clear();
        vkCmdResetQueryPool(c, pool, runde * stempel, stempel);
    }

    /** Beginnt den Abschnitt mit diesem Namen. */
    public void start(VkCommandBuffer c, String name) {
        if (!verfuegbar || runde < 0 || benutzt[runde] + 2 > stempel) return;
        int q = runde * stempel + benutzt[runde];
        benutzt[runde] += 2;
        offen.put(name, q);
        vkCmdWriteTimestamp2(c, VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT, pool, q);
    }

    /** Beendet den Abschnitt mit diesem Namen. */
    public void stopp(VkCommandBuffer c, String name) {
        if (!verfuegbar || runde < 0) return;
        Integer q = offen.remove(name);
        if (q == null) return;
        vkCmdWriteTimestamp2(c, VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT, pool, q + 1);
        runden.get(runde).add(new Abschnitt(name, q, q + 1));
    }

    /**
     * Liest alle Runden, deren Zeiten vorliegen; mit {@code warten} wartet es auf die Karte (die Arbeit muss dann
     * abgeschickt sein). Gibt die Zahl der gelesenen Runden zurück.
     */
    public int sammle(boolean warten) {
        if (!verfuegbar) return 0;
        int n = 0;
        LongBuffer lb = memAllocLong(2 * stempel);
        try {
            for (int r = 0; r < RUNDEN; r++) {
                if (runden.get(r).isEmpty()) continue;
                lb.clear();
                int rc = vkGetQueryPoolResults(g.device, pool, r * stempel, benutzt[r], lb, 16, VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT | (warten ? VK_QUERY_RESULT_WAIT_BIT : 0));
                if (rc != VK_SUCCESS && rc != VK_NOT_READY) Geraet.ok(rc, "vkGetQueryPoolResults");
                boolean da = true;
                for (Abschnitt a : runden.get(r)) da &= lb.get(2 * (a.von - r * stempel) + 1) != 0 && lb.get(2 * (a.bis - r * stempel) + 1) != 0;
                if (!da) continue;
                long maske = schlange.zeitstempelBits >= 64 ? -1L : (1L << schlange.zeitstempelBits) - 1;
                Map<String, Double> summe = new LinkedHashMap<>();
                for (Abschnitt a : runden.get(r)) {
                    long t0 = lb.get(2 * (a.von - r * stempel)) & maske, t1 = lb.get(2 * (a.bis - r * stempel)) & maske;
                    summe.merge(a.name, ((t1 - t0) & maske) * (double) g.zeitstempelTakt * 1e-6, Double::sum);
                }
                for (Map.Entry<String, Double> e : summe.entrySet()) {
                    zuletzt.put(e.getKey(), e.getValue());
                    mittel.merge(e.getKey(), e.getValue(), (alt, neu) -> alt + gewicht * (neu - alt));
                }
                runden.get(r).clear();
                gelesen++;
                n++;
            }
        } finally {
            memFree(lb);
        }
        return n;
    }

    /** Gleitendes Mittel eines Abschnitts in ms (0, solange nichts gelesen ist). */
    public double ms(String name) { return mittel.getOrDefault(name, 0.0); }

    /** Letzter gelesener Wert eines Abschnitts in ms. */
    public double letzte(String name) { return zuletzt.getOrDefault(name, 0.0); }

    /** Namen aller gelesenen Abschnitte. */
    public List<String> abschnitte() { return new ArrayList<>(mittel.keySet()); }

    public long verloren() { return verloren; }
    public long gelesen() { return gelesen; }

    /** „Name 1,23 · Name 0,45“ in ms (gleitendes Mittel). */
    @Override public String toString() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Double> e : mittel.entrySet()) b.append(b.length() == 0 ? "" : " · ").append(e.getKey()).append(String.format(Locale.GERMAN, " %.2f", e.getValue()));
        return b.toString();
    }

    @Override public void close() {
        if (pool != VK_NULL_HANDLE) vkDestroyQueryPool(g.device, pool, null);
    }
}
