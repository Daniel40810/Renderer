package com.dan.render.bewuchs;

import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Zeichner;
import com.dan.core.raum.Blick;
import com.dan.core.gitter.Gelaende;
import com.dan.core.szene.Boden;
import com.dan.core.szene.Gehoelz;
import com.dan.core.szene.Gestalt;
import com.dan.render.bewuchs.baum.Season;
import com.dan.render.bewuchs.baum.Species;
import com.dan.render.bewuchs.baum.TreeGenerator;
import com.dan.render.bewuchs.baum.TreeMesh;
import com.dan.render.bewuchs.baum.TreeModel;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Der Bewuchs (seit P12c, Werkbuch HalligHooge Teil A, 12.13, F135–F137 A): die Gehölze der Hallig aus
 * {@code bau/gehoelz.bin} (Schritt gehoelz) als Netze aus dem Baumgenerator ({@code baum}, übernommen aus dem Bestand
 * {@code forest}, dazu Esche, Salweide, Holunder, Hundsrose).
 * <ul>
 * <li><b>Netze:</b> je Art zwei Varianten in drei Stufen: nah (Blätter als Büschel), mittel (Kronen als Ballen),
 * Silhouette. Laubfarbe und Laubfall nach dem Tag im Jahr (UTC) aus {@code Season}; neu gebaut, wenn der Tag wechselt.</li>
 * <li><b>Pflanzen:</b> das Netz gestreckt auf die gemessene Höhe und die mittleren Halbmesser der Krone (Ost–West,
 * Nord–Süd), die Krone um den Unterschied versetzt (oben voll); Windflüchter aus der Art (Wind aus 250°).</li>
 * <li><b>Stufe nach dem Abstand:</b> bis {@link #weitNah} nah, bis {@link #weitMittel} mittel, darüber Silhouette.</li>
 * <li><b>Wind:</b> mittlere Biegung mit U², Schwanken und Böen, Flattern des Laubs ({@code bewuchs.glsl}).</li>
 * <li><b>Licht</b> wie render.bau (Sonne, Mond, Himmel und Boden), Laub zweiseitig und durchscheinend [G].</li>
 * <li><b>Schatten:</b> die Gehölze zeichnen sich in die Karte, die render.bau unter „Bau/Schatten“ ablegt (in der Folge
 * des Rahmens nach den Häusern, vor dem Gelände): nahe Gehölze in Karte 0 und 1 mit dem mittleren Netz, ferne mit der
 * Silhouette, in Karte 2 alle als Silhouette. Ohne Häuser kein Schatten.</li>
 * </ul>
 * Aus ({@link #an} = false oder {@code -Dlauf.bewuchs=aus}) zeichnet der Teil nichts: das Bild ist wie ohne ihn.
 */
public final class Bewuchsbild implements Bildteil {
    public static final String VERSION = "0.1";
    static final int VARIANTEN = 2, STUFEN = 3, FLOATS = 12, KOPF = 64 + 3 * 16;

    /** An oder aus (Vorgabe an; {@code -Dlauf.bewuchs=aus}). */
    public volatile boolean an = !"aus".equals(System.getProperty("lauf.bewuchs"));
    /** Wind in 10 m (m/s) nach Ost und Nord; Zeit des Winds (s), NaN: die Uhr. */
    public volatile double windU, windV, windZeit = Double.NaN;
    /** Böigkeit (0 … 1) [G]. */
    public volatile double boeig = 0.3;
    /** Grenzen der Stufen (m) [G]; Schatten mit dem mittleren Netz bis {@link #schattenNah}. */
    public volatile double weitNah = 120, weitMittel = 450, schattenNah = 250;
    /** Krümmung der Erde (R', m). */
    public double kruemmung;
    /** Tag im Jahr, für den das Laub zuletzt gebaut wurde; Zahl der Gehölze je Stufe im letzten Bild. */
    public volatile int tag = -1;
    public final int[] jeStufe = new int[STUFEN];

    private final Geraet g;
    private final Konto konto;
    public final Gehoelz gehoelz;
    private final Species[] arten = new Species[Gehoelz.ARTEN];
    private final TreeMesh[] netze = new TreeMesh[Gehoelz.ARTEN * VARIANTEN * STUFEN];
    private final float[] hoehe0 = new float[Gehoelz.ARTEN * VARIANTEN], radius0 = new float[Gehoelz.ARTEN * VARIANTEN];
    private final int[] erster = new int[netze.length], zahl = new int[netze.length];
    private final int[] netzVon;        // je Pflanze: Art · VARIANTEN + Variante
    /**
     * Seit P12c3: je Netz (Art · VARIANTEN + Variante) das Laub des Tags am nahen Netz: Fläche (m²), Farbe (RGB, nach
     * Fläche gemittelt), Höhe unten und oben (5 % und 99 % der Laubpunkte, m), Farbe der Rinde, Halbmesser des Stamms (m).
     */
    private final double[][] laubDaten = new double[Gehoelz.ARTEN * VARIANTEN][];
    private final Puffer scheitel, pflanzen, kopf, liste, licht, ohneBau;
    private final Bild leer;
    private final Zeichner zBaum, zSchatten;
    private final Kernel kLicht;
    private final Abtaster punkt;
    private final ByteBuffer kopfB, listeB;
    private final long start = System.nanoTime();

    // ---- Halme (F135 A)
    /** Halme an oder aus (mit {@link #an}); Übergang in das Material zwischen {@link #halmVon} und {@link #halmBis} (m) [G]. */
    public volatile boolean halmeAn = true;
    public volatile double halmVon = 22, halmBis = 30, halmHoehe = 1;
    /** Ringe um das Auge: äußerer Radius (m), Kante der Zelle (m), Halme je Büschel [G]. */
    static final double[][] RINGE = {{6, 0.25, 8}, {14, 0.45, 7}, {30, 0.9, 6}};
    static final int HALME = 8, BUESCHEL_MAX = 16000;
    private Gelaende halmGelaende;
    private Gestalt halmGestalt;
    private Boden halmBoden;
    private double[] halmAuge;
    private int bueschelZahl;
    private ByteBuffer bueschelB;
    private final Puffer bueschel, halmKopf;
    private final Zeichner zHalm;

    /** Aus einem Gebiet ({@code bau/gehoelz.bin}); null, wenn es keine Gehölze hat. */
    public static Bewuchsbild aus(Geraet g, Konto konto, File gebiet) throws IOException {
        Gehoelz h = Gehoelz.lies(new File(gebiet, "bau"));
        return h == null || h.pflanzen.isEmpty() ? null : new Bewuchsbild(g, konto, h);
    }

    public Bewuchsbild(Geraet g, Konto konto, Gehoelz gehoelz) {
        this.g = g;
        this.konto = konto;
        this.gehoelz = gehoelz;
        arten[Gehoelz.ESCHE] = Species.ash();
        arten[Gehoelz.SCHWARZERLE] = Species.alder();
        arten[Gehoelz.EBERESCHE] = Species.rowan();
        arten[Gehoelz.SALWEIDE] = Species.goatWillow();
        arten[Gehoelz.HOLUNDER] = Species.elder();
        arten[Gehoelz.HUNDSROSE] = Species.dogRose();
        long platz = 0;
        for (int a = 0; a < Gehoelz.ARTEN; a++)
            for (int v = 0; v < VARIANTEN; v++) {
                int av = a * VARIANTEN + v;
                TreeModel m = TreeGenerator.grow(arten[a], 1000L * a + 17L * v + 3, 1f);
                hoehe0[av] = m.height;
                netze[av * STUFEN] = TreeMesh.build(m, 1);
                // Halbmesser der Krone, wie das Laub ihn zeigt (90 % der Laubpunkte um den Stamm), nicht die Reichweite der Äste
                radius0[av] = Math.max(0.3f, kronenRadius(netze[av * STUFEN], m.crownRadius));
                netze[av * STUFEN + 1] = TreeMesh.build(m, 2);
                netze[av * STUFEN + 2] = TreeMesh.silhouette(m, 8, 5);
                for (int s = 0; s < STUFEN; s++) platz += 3L * netze[av * STUFEN + s].nt;
            }
        scheitel = Puffer.speicher(g, konto, "Bewuchs, Scheitel", 4L * FLOATS * platz);
        // Pflanzen
        int n = gehoelz.pflanzen.size();
        netzVon = new int[n];
        float[] p = new float[12 * n];
        for (int i = 0; i < n; i++) {
            Gehoelz.Pflanze q = gehoelz.pflanzen.get(i);
            int v = (int) (zufall(i, 1) * VARIANTEN) % VARIANTEN, av = q.art * VARIANTEN + v;
            netzVon[i] = av;
            float rO = q.krone[0], rN = q.krone[2], rW = q.krone[4], rS = q.krone[6];
            float sx = (rO + rW) / (2 * radius0[av]), sz = (rN + rS) / (2 * radius0[av]), sy = q.hoehe / hoehe0[av];
            int o = 12 * i;
            p[o] = q.x; p[o + 1] = q.boden - 0.1f; p[o + 2] = q.z; p[o + 3] = (float) ((zufall(i, 2) - 0.5) * 0.6);   // Drehung ±0,3 rad [G]
            p[o + 4] = sx; p[o + 5] = sy; p[o + 6] = sz; p[o + 7] = (float) zufall(i, 3);
            p[o + 8] = (rO - rW) / 2; p[o + 9] = (rS - rN) / 2; p[o + 10] = hoehe0[av]; p[o + 11] = 0;
        }
        pflanzen = Puffer.speicher(g, konto, "Bewuchs, Pflanzen", 4L * Math.max(12, p.length));
        pflanzen.schreibe(p);
        kopf = Puffer.speicher(g, konto, "Bewuchs, Kopf", KOPF);
        liste = Puffer.speicher(g, konto, "Bewuchs, Liste", 4L * Math.max(4, 4 * n));
        licht = Puffer.speicher(g, konto, "Bewuchs, Himmelslicht", 9 * 16);
        ohneBau = Puffer.speicher(g, konto, "Bewuchs, ohne Schatten", 256);
        ohneBau.schreibe(new float[64]);
        leer = new Bild(g, konto, "Bewuchs, ohne Schatten", 1, 1, Bild.R32F);
        leer.schreibe(new float[] {1});
        kopfB = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
        listeB = ByteBuffer.allocateDirect(4 * 4 * Math.max(1, n)).order(ByteOrder.nativeOrder());
        String pf = "/com/dan/render/bewuchs/";
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {Bild.RGBA16F};
        zBaum = Zeichner.lade(g, pf + "baum.vert", pf + "baum.frag", 0, null, art, P, P, P, P, P, P, X, P, X, X);
        Zeichner.Art st = new Zeichner.Art();
        st.farben = new int[0];
        st.vergleich = VK_COMPARE_OP_LESS_OR_EQUAL;
        zSchatten = Zeichner.lade(g, pf + "schatten.vert", pf + "schatten.frag", 4, null, st, P, P, P, P, P);
        kLicht = Kernel.lade(g, pf + "licht.comp", 0, null, P, X, P);
        punkt = new Abtaster(g, false, false);
        bueschel = Puffer.speicher(g, konto, "Bewuchs, Büschel", 48L * BUESCHEL_MAX);
        halmKopf = Puffer.speicher(g, konto, "Bewuchs, Halme", 16);
        bueschelB = ByteBuffer.allocateDirect(48 * BUESCHEL_MAX).order(ByteOrder.nativeOrder());
        zHalm = Zeichner.lade(g, pf + "halm.vert", pf + "baum.frag", 0, null, art, P, P, P, P, P, P, X, P, X, X, P, P);
        baue(300);
    }

    @Override public String name() { return "Bewuchs"; }

    /** Laub und Rinde eines gebauten Netzes (Scheitel zu FLOATS wie im Puffer). */
    static double[] laubDaten(float[] d, int w, float h0) {
        double la = 0, lr = 0, lg = 0, lb = 0, ba = 0, br = 0, bg = 0, bb = 0;
        List<Float> ys = new ArrayList<>(), stamm = new ArrayList<>();
        for (int o = 0; o + 3 * FLOATS <= w; o += 3 * FLOATS) {
            int a = o, b = o + FLOATS, c = o + 2 * FLOATS;
            double ux = d[b] - d[a], uy = d[b + 1] - d[a + 1], uz = d[b + 2] - d[a + 2], vx = d[c] - d[a], vy = d[c + 1] - d[a + 1], vz = d[c + 2] - d[a + 2];
            double f = 0.5 * Math.sqrt(Math.pow(uy * vz - uz * vy, 2) + Math.pow(uz * vx - ux * vz, 2) + Math.pow(ux * vy - uy * vx, 2));
            boolean laub = d[a + 7] > 0.5f;
            double r = (d[a + 8] + d[b + 8] + d[c + 8]) / 3, g = (d[a + 9] + d[b + 9] + d[c + 9]) / 3, bl = (d[a + 10] + d[b + 10] + d[c + 10]) / 3;
            if (laub) {
                la += f;
                lr += f * r;
                lg += f * g;
                lb += f * bl;
                for (int e = 0; e < 3; e++) ys.add(d[o + e * FLOATS + 1]);
            } else {
                ba += f;
                br += f * r;
                bg += f * g;
                bb += f * bl;
                for (int e = 0; e < 3; e++)
                    if (d[o + e * FLOATS + 1] < 0.15f * h0) stamm.add((float) Math.hypot(d[o + e * FLOATS], d[o + e * FLOATS + 2]));
            }
        }
        ys.sort(null);
        stamm.sort(null);
        double y0 = ys.isEmpty() ? 0.4 * h0 : ys.get((int) (0.05 * (ys.size() - 1))), y1 = ys.isEmpty() ? h0 : ys.get((int) (0.99 * (ys.size() - 1)));
        double sr = stamm.isEmpty() ? 0.02 * h0 : stamm.get((int) (0.9 * (stamm.size() - 1)));
        return new double[] {la, la > 0 ? lr / la : 0, la > 0 ? lg / la : 0, la > 0 ? lb / la : 0, y0, y1,
                ba > 0 ? br / ba : 0.1, ba > 0 ? bg / ba : 0.08, ba > 0 ? bb / ba : 0.06, sr};
    }

    /**
     * Seit P12c3: die Kronen für die Referenz ({@code render.reference.Kronen}, F138 A), mit dem Laub des zuletzt gebauten
     * Tags. Je Pflanze 15 Werte im Raum der Bauten (x = Ost − e0, z = n0 − Nord): Mitte der Krone x, z; Boden (m NHN);
     * Halbachsen O–W und N–S (m); Höhe der Krone unten und oben über dem Boden (m); Blattfläche je Volumen (m²/m³);
     * Halbmesser des Stamms (m); Laub RGB; Rinde RGB.
     */
    public synchronized float[][] kronen() {
        int n = gehoelz.pflanzen.size();
        float[][] k = new float[n][];
        for (int i = 0; i < n; i++) {
            Gehoelz.Pflanze q = gehoelz.pflanzen.get(i);
            int av = netzVon[i];
            double[] L = laubDaten[av];
            if (L == null) continue;
            float rO = q.krone[0], rN = q.krone[2], rW = q.krone[4], rS = q.krone[6];
            double sx = (rO + rW) / (2 * radius0[av]), sz = (rN + rS) / (2 * radius0[av]), sy = q.hoehe / hoehe0[av];
            double r0 = radius0[av], v0 = 4.0 / 3 * Math.PI * r0 * r0 * Math.max(0.1, (L[5] - L[4]) / 2);
            double lad = L[0] / v0 / Math.cbrt(sx * sy * sz);
            k[i] = new float[] {q.x + (rO - rW) / 2, q.z + (rS - rN) / 2, q.boden - 0.1f, (rO + rW) / 2, (rN + rS) / 2, (float) (L[4] * sy), (float) (L[5] * sy),
                    (float) lad, (float) (L[9] * 0.5 * (sx + sz)), (float) L[1], (float) L[2], (float) L[3], (float) L[6], (float) L[7], (float) L[8]};
        }
        return k;
    }

    static float kronenRadius(TreeMesh m, float vorgabe) {
        float[] r = new float[m.nv];
        int n = 0;
        for (int i = 0; i < m.nv; i++)
            if (m.part[i] != TreeMesh.BARK && m.part[i] != TreeMesh.CONE) r[n++] = (float) Math.hypot(m.pos[3 * i], m.pos[3 * i + 2]);
        if (n < 10) return vorgabe;
        float[] a = java.util.Arrays.copyOf(r, n);
        java.util.Arrays.sort(a);
        return a[(int) (0.90 * (n - 1))];
    }

    /** Seit P12c: die Halme brauchen das Gelände (Höhe), die Gestaltkarte (Art) und den Boden (Albedo wie die Clipmap). */
    public void halme(Gelaende gelaende, Gestalt gestalt, Boden boden) {
        halmGelaende = gelaende;
        halmGestalt = gestalt;
        halmBoden = boden;
        halmAuge = null;
    }

    /** Zahl der Büschel im letzten Bild. */
    public int bueschel() { return bueschelZahl; }
    /** Seit P13b: Büschel je Art der letzten Auswahl (Index = Art, wie sie an die Karte geht; für Prüfungen). */
    public final int[] bueschelJeArt = new int[32];

    static boolean grasArt(int art) {
        return art == Gestalt.FENNE || art == Gestalt.SALZWIESE || art == Gestalt.DEICH || art == Gestalt.WARFT || art == Gestalt.GRASWEG
                || art >= com.dan.core.szene.Zonen.ART_PIONIER && art <= com.dan.core.szene.Zonen.ART_FENNE_UNTEN;
    }

    /**
     * Seit P13b (F152 A): die Zonen der Salzwiese (Arten 6–9 der Gestalt, {@code core.szene.Zonen}) für die Halme; aus:
     * die Art ohne Zone, die Halme wie vor P13b.
     */
    public volatile boolean zonenAn = !"aus".equals(System.getProperty("lauf.vegetation"));
    private boolean zonenGelegt;
    private int tagGelegt = -2;

    /** Die Büschel um das Auge (Raum der Bauten), auf einem festen Raster der Welt je Ring. */
    private void bueschelBauen(double[] auge) {
        bueschelB.clear();
        java.util.Arrays.fill(bueschelJeArt, 0);
        int z = 0;
        double r0 = 0;
        for (int ring = 0; ring < RINGE.length; ring++) {
            double r1 = RINGE[ring][0], c = RINGE[ring][1];
            int halme = (int) RINGE[ring][2];
            int i0 = (int) Math.floor((auge[0] - r1) / c), i1 = (int) Math.floor((auge[0] + r1) / c);
            int j0 = (int) Math.floor((auge[2] - r1) / c), j1 = (int) Math.floor((auge[2] + r1) / c);
            for (int j = j0; j <= j1 && z < BUESCHEL_MAX; j++)
                for (int i = i0; i <= i1 && z < BUESCHEL_MAX; i++) {
                    long h = ((long) i * 73856093L) ^ ((long) j * 19349663L) ^ (ring * 83492791L);
                    double x = (i + zufall((int) h, 11)) * c, zz = (j + zufall((int) h, 12)) * c;
                    double d = Math.hypot(x - auge[0], zz - auge[2]);
                    if (d < r0 || d >= r1) continue;
                    double ost = gehoelz.e0 + x, nord = gehoelz.n0 - zz;
                    int[] w = halmGestalt.bei(ost, nord);
                    if (w == null) continue;
                    int art = zonenAn ? w[0] : com.dan.core.szene.Zonen.ohneZone(w[0]);
                    if (!grasArt(art)) continue;
                    float y = halmGelaende.hoehe(ost, nord, 1);
                    if (Float.isNaN(y)) continue;
                    double[] alb = halmBoden.pixel(ost, nord);
                    int n = w[0] == Gestalt.GRASWEG ? Math.max(2, halme / 3) : halme;
                    if (art >= com.dan.core.szene.Zonen.ART_PIONIER && art <= com.dan.core.szene.Zonen.ART_FENNE_UNTEN)
                        n = Math.max(1, (int) Math.round(n * flaechenFaktor(art, tag > 0 ? tag : 300)));   // F157 A
                    if (art >= 0 && art < bueschelJeArt.length) bueschelJeArt[art]++;
                    bueschelB.putFloat((float) x).putFloat(y).putFloat((float) zz).putFloat(art);
                    bueschelB.putFloat((float) alb[0]).putFloat((float) alb[1]).putFloat((float) alb[2]).putFloat((float) zufall((int) h, 13));
                    bueschelB.putFloat(n).putFloat((float) c).putFloat(0).putFloat(0);
                    z++;
                }
            r0 = r1;
        }
        bueschelB.flip();
        bueschelZahl = z;
        halmAuge = auge.clone();
    }

    /**
     * F157 A: Anteil der Halme je Büschel einer Zone, damit Breite × Höhe der Halme im Mittel nicht über der Klasse
     * Salzwiese (0,006 × 0,12 m) liegt; Höhen und Breiten wie in {@code halm.vert} für den Tag im Jahr. Höchstens 1.
     */
    static double flaechenFaktor(int art, double tag) {
        double s = 0.5 - 0.5 * Math.cos(2 * Math.PI * (tag - 20.0) / 365.25), f;
        switch (art) {
            case com.dan.core.szene.Zonen.ART_PIONIER -> f = 0.010 * (0.04 + s * 0.16);
            case com.dan.core.szene.Zonen.ART_SALZ_UNTEN -> f = 0.005 * (0.08 + s * 0.07);
            case com.dan.core.szene.Zonen.ART_SALZ_OBEN -> f = 0.006 * (0.15 + s * 0.15);
            case com.dan.core.szene.Zonen.ART_FENNE_UNTEN -> f = 0.005 * 1.5 * (0.04 + s * 0.04);
            default -> { return 1; }
        }
        return Math.min(1, 0.006 * 0.12 / f);
    }

    static double zufall(int i, int k) {
        long h = (i * 0x9E3779B97F4A7C15L) ^ (k * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Seit P12c3: fester Tag im Jahr für das Laub (für Prüfungen); 0 oder weniger: der Tag der Zeit. */
    public volatile int tagFest = 0;

    /** Seit P12c3: der Tag des Laubs zur Zeit t (UTC, oder {@link #tagFest}). */
    public int tagFuer(java.time.Instant t) { return tagFest > 0 ? tagFest : t.atZone(ZoneOffset.UTC).getDayOfYear(); }

    /** Netze und Laub für den Tag im Jahr (1 … 365) bauen und hochladen. */
    public synchronized void baue(int t) {
        if (t == tag) return;
        List<float[]> teile = new ArrayList<>();
        int z = 0;
        float[] col = new float[3];
        for (int k = 0; k < netze.length; k++) {
            TreeMesh m = netze[k];
            int av = k / STUFEN;
            Species sp = arten[av / VARIANTEN];
            Season s = Season.of(sp, t, 0);
            float[] farbe = new float[3 * m.nv];
            s.colorize(m, farbe);
            float h0 = hoehe0[av];
            // Laub, das noch hängt: Blätter mit eigener Schwelle; Ballen (ohne Blattnummer) nach dem Anteil
            float bleibt = s.evergreen ? 1 : s.foliage * (1 - s.drop);
            float[] d = new float[3 * m.nt * FLOATS];
            int w = 0;
            for (int tr = 0; tr < m.nt; tr++) {
                int i0 = m.tri[3 * tr];
                byte teil = m.part[i0];
                boolean laub = teil != TreeMesh.BARK && teil != TreeMesh.CONE;
                if (laub) {
                    int li = m.leaf[i0];
                    if (li >= 0 && li < m.leafIds.length) {
                        if (s.fallen(m, li, 0) || s.foliage <= 0.02f) continue;
                    } else if (zufall(tr + 7919 * k, 9) >= bleibt) continue;
                }
                for (int e = 0; e < 3; e++) {
                    int i = m.tri[3 * tr + e];
                    float y = m.pos[3 * i + 1];
                    d[w++] = m.pos[3 * i]; d[w++] = y; d[w++] = m.pos[3 * i + 2];
                    d[w++] = (float) Math.pow(Math.max(0, Math.min(1, y / h0)), 1.5);
                    d[w++] = m.nrm[3 * i]; d[w++] = m.nrm[3 * i + 1]; d[w++] = m.nrm[3 * i + 2]; d[w++] = laub ? 1 : 0;
                    d[w++] = farbe[3 * i]; d[w++] = farbe[3 * i + 1]; d[w++] = farbe[3 * i + 2];
                    d[w++] = (float) zufall(m.leaf[i] + 31 * k, 5);
                }
            }
            if (k % STUFEN == 0) laubDaten[av] = laubDaten(d, w, h0);
            erster[k] = z;
            zahl[k] = w / FLOATS;
            z += zahl[k];
            teile.add(java.util.Arrays.copyOf(d, w));
        }
        ByteBuffer b = ByteBuffer.allocateDirect(4 * FLOATS * Math.max(1, z)).order(ByteOrder.nativeOrder());
        for (float[] f : teile) for (float v : f) b.putFloat(v);
        b.flip();
        if (z > 0) scheitel.schreibe(b, 0);
        tag = t;
    }

    /** Zahl der Dreiecke der Stufe s (über alle Arten und Varianten, mit dem Laub des Tags). */
    public int dreiecke(int s) {
        int d = 0;
        for (int k = s; k < netze.length; k += STUFEN) d += zahl[k] / 3;
        return d;
    }

    /** Lage des Auges im Raum der Bauten. */
    public double[] auge(Blick b) { return new double[] {b.ost - gehoelz.e0, b.hoehe, gehoelz.n0 - b.nord}; }

    @Override public void zeichnen(Ablauf a, Bildziel z) {
        if (!an) return;
        Blick b = z.blick;
        if (z.zeit != null) baue(tagFuer(z.zeit));
        double[] auge = auge(b);
        // ---- Listen: Bild nach Stufe, Schatten nah (mittleres Netz), fern und alle (Silhouette)
        int n = gehoelz.pflanzen.size(), G = netze.length;
        int[][] zaehl = new int[4][G];
        int[] stufe = new int[n];
        boolean[] nahS = new boolean[n];
        java.util.Arrays.fill(jeStufe, 0);
        for (int i = 0; i < n; i++) {
            Gehoelz.Pflanze q = gehoelz.pflanzen.get(i);
            double d = Math.hypot(q.x - auge[0], q.z - auge[2]);
            stufe[i] = d < weitNah ? 0 : d < weitMittel ? 1 : 2;
            nahS[i] = d < schattenNah;
            jeStufe[stufe[i]]++;
            int av = netzVon[i];
            zaehl[0][av * STUFEN + stufe[i]]++;
            if (nahS[i]) zaehl[1][av * STUFEN + 1]++; else zaehl[2][av * STUFEN + 2]++;
            zaehl[3][av * STUFEN + 2]++;
        }
        int[][] anfang = new int[4][G];
        int o = 0;
        for (int l = 0; l < 4; l++) for (int k = 0; k < G; k++) { anfang[l][k] = o; o += zaehl[l][k]; }
        int[][] pos = new int[4][];
        for (int l = 0; l < 4; l++) pos[l] = anfang[l].clone();
        int[] li = new int[o];
        for (int i = 0; i < n; i++) {
            int av = netzVon[i];
            li[pos[0][av * STUFEN + stufe[i]]++] = i;
            if (nahS[i]) li[pos[1][av * STUFEN + 1]++] = i; else li[pos[2][av * STUFEN + 2]++] = i;
            li[pos[3][av * STUFEN + 2]++] = i;
        }
        listeB.clear();
        for (int v : li) listeB.putInt(v);
        listeB.flip();
        // ---- Kopf
        kopfB.clear();
        for (float f : b.ansichtProjektion()) kopfB.putFloat(f);
        float hx = (float) auge[0], hy = (float) auge[1], hz = (float) auge[2];
        double R = kruemmung;
        kopfB.putFloat(hx).putFloat(hy).putFloat(hz).putFloat(R > 0 ? (float) (1 / (2 * R)) : 0);
        kopfB.putFloat((float) (auge[0] - hx)).putFloat((float) (auge[1] - hy)).putFloat((float) (auge[2] - hz)).putFloat((float) b.hoehe);
        double t = Double.isNaN(windZeit) ? (System.nanoTime() - start) * 1e-9 : windZeit;
        kopfB.putFloat((float) windU).putFloat((float) -windV).putFloat((float) (t % 3600)).putFloat((float) boeig);
        kopfB.flip();
        a.durchgang("Bewuchs, Kopf", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(liste, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB);
            if (listeB.remaining() > 0) vkCmdUpdateBuffer(c, liste.handle, 0, listeB);
        });
        Bild ansicht = z.geteilt("Himmel/Ansicht"), durch = z.geteilt("Himmel/Durchlaessigkeit");
        Puffer hw = z.puffer("Himmel/Werte");
        a.rechne("Bewuchs, Himmelslicht", kLicht, kLicht.satz(hw, punkt.an(ansicht), licht), null, 1, 1, 1,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, hw), Ablauf.textur(Ablauf.RECHNEN, ansicht), Ablauf.schreibt(licht)));
        // ---- Schatten in die Karte von render.bau
        Bild atlas = z.geteilt.get("Bau/Schatten");
        Puffer bauW = z.puffer.get("Bau/Schattenwerte");
        if (atlas != null && bauW != null) {
            int N = atlas.hoehe, karten = Math.min(3, atlas.breite / Math.max(1, N));
            long satzS = zSchatten.satz(scheitel, pflanzen, kopf, liste, bauW);
            ByteBuffer push = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            a.zeichne("Bewuchs, Schatten", new Bild[0], null, atlas, null, Ablauf.liest(Ablauf.ZEICHNEN, scheitel, pflanzen, kopf, liste, bauW), c -> {
                try (MemoryStack s = stackPush()) {
                    for (int k = 0; k < karten; k++) {
                        vkCmdSetViewport(c, 0, VkViewport.calloc(1, s).x(k * N).y(0).width(N).height(N).minDepth(0).maxDepth(1));
                        VkRect2D.Buffer sc = VkRect2D.calloc(1, s);
                        sc.get(0).offset().set(k * N, 0);
                        sc.get(0).extent().set(N, N);
                        vkCmdSetScissor(c, 0, sc);
                        push.putInt(0, k);
                        int[] listen = k == 0 ? new int[] {1} : k == 1 ? new int[] {1, 2} : new int[] {3};
                        for (int l : listen)
                            for (int g2 = 0; g2 < G; g2++)
                                if (zaehl[l][g2] > 0 && zahl[g2] > 0) zSchatten.zeichne(c, satzS, push, zahl[g2], zaehl[l][g2], erster[g2], anfang[l][g2]);
                    }
                }
            });
        }
        // ---- Halme: Büschel neu, wenn das Auge 0,5 m gegangen ist
        boolean halme = halmeAn && halmGelaende != null && halmGestalt != null && halmBoden != null;
        if (halme && (halmAuge == null || Math.hypot(auge[0] - halmAuge[0], auge[2] - halmAuge[2]) > 0.5 || Math.abs(auge[1] - halmAuge[1]) > 2
                || zonenGelegt != zonenAn || tagGelegt != tag)) {
            zonenGelegt = zonenAn;
            tagGelegt = tag;
            bueschelBauen(auge);
            ByteBuffer bb = bueschelB.duplicate();
            ByteBuffer hk = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            // P13b: der Tag im Jahr für die Halme der Salzwiese (Arten 6–9); ohne Tag Ende Oktober
            hk.putFloat((float) halmVon).putFloat((float) halmBis).putFloat((float) halmHoehe).putFloat(tag > 0 ? tag : 300).flip();
            a.durchgang("Bewuchs, Büschel", new Ablauf.Zugriff[] {
                    new Ablauf.Zugriff(bueschel, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                    new Ablauf.Zugriff(halmKopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
                for (int off = 0; off < bb.limit(); off += 65536) {
                    ByteBuffer teil = bb.duplicate();
                    teil.position(off).limit(Math.min(bb.limit(), off + 65536));
                    vkCmdUpdateBuffer(c, bueschel.handle, off, teil.slice().order(ByteOrder.nativeOrder()));
                }
                vkCmdUpdateBuffer(c, halmKopf.handle, 0, hk);
            });
        }
        // ---- Gehölze
        Bild bauB = atlas != null && bauW != null ? atlas : leer;
        Puffer bw = atlas != null && bauW != null ? bauW : ohneBau;
        long satz = zBaum.satz(scheitel, pflanzen, kopf, liste, bw, hw, punkt.an(durch), licht, punkt.an(ansicht), punkt.an(bauB));
        a.zeichne("Bewuchs", new Bild[] {z.hdr}, null, z.tiefe, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, scheitel, pflanzen, kopf, liste, bw, hw, licht), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, bauB)),
                c -> {
                    for (int g2 = 0; g2 < G; g2++)
                        if (zaehl[0][g2] > 0 && zahl[g2] > 0) zBaum.zeichne(c, satz, null, zahl[g2], zaehl[0][g2], erster[g2], anfang[0][g2]);
                });
        if (halme && bueschelZahl > 0) {
            long satzH = zHalm.satz(scheitel, pflanzen, kopf, liste, bw, hw, punkt.an(durch), licht, punkt.an(ansicht), punkt.an(bauB), bueschel, halmKopf);
            int bz = bueschelZahl;
            a.zeichne("Bewuchs, Halme", new Bild[] {z.hdr}, null, z.tiefe, null,
                    Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, scheitel, pflanzen, kopf, liste, bw, hw, licht, bueschel, halmKopf),
                            Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, bauB)),
                    c -> zHalm.zeichne(c, satzH, null, 15 * HALME, bz, 0, 0));
        }
    }

    @Override public void close() {
        zBaum.close();
        zHalm.close();
        bueschel.close();
        halmKopf.close();
        zSchatten.close();
        kLicht.close();
        punkt.close();
        scheitel.close();
        pflanzen.close();
        kopf.close();
        liste.close();
        licht.close();
        ohneBau.close();
        leer.close();
    }
}
