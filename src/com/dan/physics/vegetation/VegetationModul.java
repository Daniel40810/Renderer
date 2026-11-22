package com.dan.physics.vegetation;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.szene.Gestalt;
import com.dan.core.szene.Zonen;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Salzwiese am Bus (P13b, Werkbuch HalligHooge Teil A, 12.14; F153 A): je Zelle der Gitter aus den Zonen zu 1 m
 * ({@link Zonen}) und der Gestalt (beweidete Fenne) die Anteile der Zonen; daraus zu Beginn und bei jedem neuen Tag
 * <ul>
 * <li>{@code surf.z0.vegetation} (m): Mittel von z0 über den bewachsenen Teil, wo er wenigstens {@link #mindestAnteil}
 * der Zelle deckt, sonst 0 (dann gilt die Rauheit der Packung bzw. der andere Beitrag des Verbunds);</li>
 * <li>{@code veg.cover} (0–1): Bedeckung über die ganze Zelle (Anteil der Zone · Bedeckung der Pflanzen).</li>
 * </ul>
 * Die Zonen ändern sich im Lauf nicht (F146 A: kein Wachstum über Jahre); Höhe und Bedeckung folgen der Jahreszeit.
 */
public final class VegetationModul implements Modul {
    public static final String VERSION = "0.1";

    public final List<Gitter> gitter;
    public final Salzwiese salzwiese;
    public final double kopplung;
    public final Instant start;
    /** Anteil bewachsener Fläche, ab dem eine Zelle z0 bekommt [G]. */
    public double mindestAnteil = 0.5;
    /** Fester Tag des Jahres (0: aus der Zeit des Laufs). */
    public int tagFest;

    /** Je Gitter, je Zelle: Anteile {Pionier, unten, oben} unbeweidet, dann {Pionier, unten, oben} beweidet. */
    private final List<float[][]> anteile = new ArrayList<>();
    private int tagGelegt = -1;
    private final List<float[]> z0 = new ArrayList<>(), deckung = new ArrayList<>();

    public VegetationModul(List<Gitter> gitter, Zonen zonen, Gestalt gestalt, Salzwiese salzwiese, double kopplung, Instant start) {
        this.gitter = List.copyOf(gitter);
        this.salzwiese = salzwiese;
        this.kopplung = kopplung;
        this.start = start;
        for (Gitter g : gitter) anteile.add(anteile(g, zonen, gestalt));
    }

    /** Anteile der Zonen je Zelle aus den Bildpunkten der Zonen (1 m), getrennt nach beweidet (Fenne der Gestalt). */
    static float[][] anteile(Gitter g, Zonen zonen, Gestalt gestalt) {
        float[][] a = new float[6][g.zellen()];
        double s = zonen.kantenlaenge();
        int[] zahl = new int[g.zellen()];
        for (Zonen.Kachel k : zonen.kacheln) {
            Gestalt.Kachel gk = gestalt != null ? gestalt.kachel(k.ost() + 0.5 * s, k.nord() + 0.5 * s) : null;
            for (int j = 0; j < zonen.n; j++)
                for (int i = 0; i < zonen.n; i++) {
                    double ost = k.ost() + (i + 0.5) * zonen.zelle, nord = k.nord() + s - (j + 0.5) * zonen.zelle;
                    int gi = g.i(ost), gj = (int) Math.floor((nord - g.y0) / g.dy);
                    if (gi < 0 || gj < 0 || gi >= g.nx || gj >= g.ny) continue;
                    int z = k.daten()[2 * (j * zonen.n + i)] & 0xFF;
                    if (z < Zonen.PIONIER || z > Zonen.FENNE) continue;
                    boolean weide = z == Zonen.FENNE;   // F155 A: Fenne hinter dem Deich wie obere Salzwiese, beweidet
                    if (z == Zonen.FENNE) z = Zonen.OBEN;
                    if (gk != null) {
                        int pi = Math.min(gestalt.n - 1, (int) Math.floor((ost - gk.ost()) / gestalt.zelle)), pj = Math.min(gestalt.n - 1, (int) Math.floor((gk.nord() + gestalt.kantenlaenge() - nord) / gestalt.zelle));
                        weide |= (gk.daten()[2 * (pj * gestalt.n + pi)] & 0xFF) == Gestalt.FENNE;
                    }
                    a[(weide ? 3 : 0) + z - 1][gi + g.nx * gj] += 1;
                }
        }
        double proZelle = g.dx * g.dy / (zonen.zelle * zonen.zelle);
        for (float[] x : a) for (int c = 0; c < x.length; c++) x[c] /= proZelle;
        return a;
    }

    @Override public String name() { return "vegetation"; }

    @Override public String version() { return VERSION; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung);
        for (Gitter g : gitter) {
            a.schreibt("surf.z0.vegetation", "m", Ort.ZELLE, g, Feld.Zahl.FLOAT);
            a.schreibt("veg.cover", "–", Ort.ZELLE, g, Feld.Zahl.FLOAT);
        }
    }

    public int tag(double t) { return tagFest > 0 ? tagFest : start.plusMillis(Math.round(t * 1000)).atZone(ZoneOffset.UTC).getDayOfYear(); }

    /** Rechnet z0 und Bedeckung je Zelle für den Tag (nur bei neuem Tag). */
    public void rechne(int tag) {
        if (tag == tagGelegt) return;
        z0.clear();
        deckung.clear();
        for (float[][] a : anteile) {
            int nc = a[0].length;
            float[] z = new float[nc], d = new float[nc];
            for (int c = 0; c < nc; c++) {
                double flaeche = 0, lnz = 0, dk = 0;
                for (int q = 0; q < 6; q++) {
                    double f = a[q][c];
                    if (f <= 0) continue;
                    int zone = q % 3 + 1;
                    boolean weide = q >= 3;
                    flaeche += f;
                    lnz += f * Math.log(Math.max(1e-5, salzwiese.z0(zone, weide, tag)));
                    dk += f * salzwiese.deckung(zone, tag);
                }
                d[c] = (float) Math.min(1, dk);
                z[c] = flaeche >= mindestAnteil ? (float) Math.exp(lnz / flaeche) : 0f;
            }
            z0.add(z);
            deckung.add(d);
        }
        tagGelegt = tag;
    }

    /** z0 (m) und Bedeckung je Zelle des Gitters k nach {@link #rechne}. */
    public float[] z0(int k) { return z0.get(k); }

    public float[] deckung(int k) { return deckung.get(k); }

    /** Anteil bewachsener Fläche je Zelle des Gitters k. */
    public float[] bewachsen(int k) {
        float[][] a = anteile.get(k);
        float[] s = new float[a[0].length];
        for (float[] x : a) for (int c = 0; c < s.length; c++) s[c] += x[c];
        return s;
    }

    @Override public void beginn(double t0, Bus bus) { lege(bus, t0); }

    @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }

    private void lege(Bus bus, double t) {
        rechne(tag(t));
        for (int k = 0; k < gitter.size(); k++) {
            Gitter g = gitter.get(k);
            Feld fz = bus.schreibfeld(this, "surf.z0.vegetation", g), fd = bus.schreibfeld(this, "veg.cover", g);
            float[] z = z0.get(k), d = deckung.get(k);
            for (int c = 0; c < z.length; c++) { fz.setz(c, z[c]); fd.setz(c, d[c]); }
            bus.lege(this, "surf.z0.vegetation", g, t);
            bus.lege(this, "veg.cover", g, t);
        }
    }
}
