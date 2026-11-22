package com.dan.render.bau;

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
import com.dan.core.szene.Bauten;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Die Häuser (seit P12a, Werkbuch HalligHooge Teil A, 12.13; F126 A): Körper aus {@code core.szene.Bauten} im selben
 * Licht wie Gelände und Wasser, dazu der Schatten der Häuser für alle, die ihn empfangen.
 * <p>
 * Je Bild, alles in {@link #zeichnen} (die Richtung der Sonne legt {@code render.air} erst beim Vorbereiten ab):
 * <ol>
 * <li><b>Licht des Himmels</b> ({@code licht.comp}): Himmel und Boden aus {@code Himmel/Ansicht} als 9 Glieder nach
 * Kugelflächenfunktionen – eine Wand zur Sonne bekommt mehr Himmelslicht als eine im Norden.</li>
 * <li><b>Schatten:</b> {@link #KASKADEN} Karten der Tiefe nebeneinander in einem Bild ({@code Bau/Schatten}), je eine
 * Parallelprojektion entlang der Richtung zur Sonne (nachts zum Mond, wenn er über der Kimm steht), Seiten
 * {@link #kaskaden} m [G], am Raster der Texel festgehalten (kein Flimmern beim Gehen). Die Abbildungen stehen in
 * {@code Bau/Schattenwerte} (Aufbau in {@code core/gpu/bauschatten.glsl}); Gelände, Wasser und Häuser lesen beide.</li>
 * <li><b>Häuser</b> ({@code haus.vert}, {@code haus.frag}) in das HDR-Bild und die Tiefe.</li>
 * </ol>
 * Darum steht dieser Teil im Rahmen <b>vor</b> dem Gelände: {@code List.of(haeuser, gelaende, wasser, himmel, …)}. Ohne
 * Häuser (oder mit {@link #an} = false) legt er nichts ab; Gelände und Wasser sind dann bitgleich wie vorher.
 */
public final class Hausbild implements Bildteil {
    public static final String VERSION = "0.2";
    public static final String SCHATTEN = "Bau/Schatten", SCHATTENWERTE = "Bau/Schattenwerte";
    public static final int KASKADEN = 3;
    /** Kopf (Byte) und Schattenwerte (Byte), wie bau.glsl und bauschatten.glsl. */
    static final int KOPF = 64 + 3 * 16, WERTE = KASKADEN * 64 + 4 * 16;

    public final Bauten bauten;
    /** Häuser zeichnen (Schalter der Anwendung); aus: nichts im Bild, kein Schatten. */
    public volatile boolean an = true;
    /** Halbe Seite der Karten des Schattens (m) [G] und Texel je Karte. */
    public final double[] kaskaden = {120, 500, 2500};
    public final int n;
    /** Erdkrümmung mit Refraktion wie das Gelände ({@code Clipmap.curvatureRadius}); 0: ohne. */
    public volatile double kruemmung = 6_371_000 / (1 - 0.13);
    /** Fensterlicht [G]: Leuchtdichte eines hellen Fensters (cd/m²), Zeitzone der Uhr in den Häusern. */
    public volatile double fensterL = 40;
    public ZoneId zone = ZoneId.of("Europe/Berlin");
    /** Versatz längs der Normale beim Lesen des Schattens und Grundabstand der Tiefe (Texel) [G]. */
    public volatile double versatz = 0.25, abstand = 1.0;
    /** Zuletzt: Quelle des Schattens (0 Sonne, 1 Mond, −1 keine), Anteil der Fenster mit Licht. */
    public volatile int quelle = -1;
    public volatile double fensterAnteil;

    private final Geraet g;
    private final Zeichner zHaus, zSchatten;
    private final Kernel kLicht;
    private final Puffer scheitel, haeuser, kopf, licht, werte;
    private final Bild schatten;
    private final Abtaster punkt;
    private final ByteBuffer kopfB = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder()), werteB = ByteBuffer.allocateDirect(WERTE).order(ByteOrder.nativeOrder());
    private final float[][] matrix = new float[KASKADEN][];
    private final int zahl, zahlHaeuser;
    /** Seit P12b: erster Scheitel der Pfähle (alles danach sind Pfähle) und die Gruppen {erster, Zahl, x0, z0, x1, z1}. */
    private final int pfahlErster;
    private final float[][] pfahlGruppen;
    /** Seit P12b [G]: Pfähle bis zu dieser Entfernung (m) zeichnen; weiter sind 12 cm unter einem Bildpunkt, das Buschwerk bleibt. */
    public volatile double pfahlWeite = 400;
    /** Gruppen der Pfähle im letzten Bild (für Prüfungen). */
    public volatile int pfahlGezeichnet;
    /** Seit P12b: die Körper der Gestalt (Lahnungen, Buhnen, Anleger) zeigen. */
    /** Seit P12b: in so vielen Kaskaden des Schattens (von der nächsten an) werfen die Körper Schatten. */
    public static final int KOERPER_KASKADEN = 2;
    public volatile boolean koerperAn = !"aus".equals(System.getProperty("lauf.gestalt"));
    private volatile double[] letzte;

    /**
     * Aus einem Gebiet ({@code bau/gebaeude.bin}, seit P12b dazu {@code bau/koerper.bin} mit den Körpern der Gestalt);
     * null, wenn das Gebiet beides nicht hat.
     */
    public static Hausbild aus(Geraet g, Konto konto, File gebiet) throws IOException {
        Bauten b = Bauten.lies(new File(gebiet, "bau")), k = Bauten.lies(new File(gebiet, "bau"), Bauten.KOERPER);
        Bauten alle = Bauten.zusammen(b, k);
        if (alle == null || alle.scheitelZahl() == 0) return null;
        return new Hausbild(g, konto, alle, 2048, b == null ? 0 : b.scheitelZahl());
    }

    public Hausbild(Geraet g, Konto konto, Bauten bauten, int n) { this(g, konto, bauten, n, bauten.scheitelZahl()); }

    /** {@code nHaeuser}: Scheitel der Häuser vorn; der Rest sind Körper der Gestalt (seit P12b, eigener Schalter). */
    public Hausbild(Geraet g, Konto konto, Bauten bauten, int n, int nHaeuser) {
        this.g = g;
        this.bauten = bauten;
        this.n = n;
        this.zahl = bauten.scheitelZahl();
        this.zahlHaeuser = nHaeuser;
        // seit P12b: die Pfähle der Lahnungen (Gruppen „Pfähle …“ am Ende) – gezeichnet nur in der Nähe
        int pe = zahl;
        List<float[]> pg = new ArrayList<>();
        for (Bauten.Gebaeude b : bauten.gebaeude)
            if (b.kennung != null && b.kennung.startsWith("Pfähle ") && b.erster >= nHaeuser) {
                pe = Math.min(pe, b.erster);
                pg.add(new float[] {b.erster, b.scheitel, b.huelle[0], b.huelle[1], b.huelle[2], b.huelle[3]});
            }
        this.pfahlErster = pe;
        this.pfahlGruppen = pg.toArray(new float[0][]);
        scheitel = Puffer.speicher(g, konto, "Häuser, Scheitel", 4L * bauten.scheitel.length);
        scheitel.schreibe(bauten.scheitel);
        float[] h = new float[12 * bauten.gebaeude.size()];
        for (int i = 0; i < bauten.gebaeude.size(); i++) {
            Bauten.Gebaeude b = bauten.gebaeude.get(i);
            int o = 12 * i;
            for (int c = 0; c < 3; c++) {
                h[o + c] = b.dachAlbedo[c];
                h[o + 4 + c] = b.wandAlbedo[c];
            }
            h[o + 3] = b.dach;
            h[o + 7] = b.boden;
            h[o + 8] = b.traufe;
            h[o + 9] = b.first;
            h[o + 10] = (float) zufall(b.kennung + "#" + i);
        }
        haeuser = Puffer.speicher(g, konto, "Häuser, Material", 4L * Math.max(12, h.length));
        haeuser.schreibe(h);
        kopf = Puffer.speicher(g, konto, "Häuser, Kopf", KOPF);
        licht = Puffer.speicher(g, konto, "Häuser, Himmelslicht", 9 * 16);
        werte = Puffer.speicher(g, konto, "Häuser, Schattenwerte", WERTE);
        schatten = new Bild(g, konto, "Häuser, Schatten", KASKADEN * n, n, Bild.D32F);
        String p = "/com/dan/render/bau/";
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {Bild.RGBA16F};
        zHaus = Zeichner.lade(g, p + "haus.vert", p + "haus.frag", 0, null, art, P, P, P, P, X, P, X, X, P);
        Zeichner.Art st = new Zeichner.Art();
        st.farben = new int[0];
        st.vergleich = VK_COMPARE_OP_LESS_OR_EQUAL;
        zSchatten = Zeichner.lade(g, p + "schatten.vert", p + "schatten.frag", 64, null, st, P, P, P);
        kLicht = Kernel.lade(g, p + "licht.comp", 0, null, P, P, P, P, X, P, X);
        punkt = new Abtaster(g, false, false);
    }

    @Override public String name() { return "Häuser"; }

    public Bild schatten() { return schatten; }

    public Puffer schattenwerte() { return werte; }

    public Puffer licht() { return licht; }

    /** Seit P12b: Gibt es Körper der Gestalt? */
    public boolean hatKoerper() { return zahl > zahlHaeuser; }

    public float[] matrix(int k) { return matrix[k] == null ? null : matrix[k].clone(); }

    /** Richtung zur Quelle des Schattens im letzten Bild (null: keine). */
    public double[] letzteQuelle() { return letzte == null ? null : letzte.clone(); }

    /**
     * Seit P12c3: die Bereiche der Scheitel {erster, Zahl}, die das Bild für dieses Auge zeichnet (Häuser, Körper ohne
     * Pfähle, die Gruppen der Pfähle bis {@link #pfahlWeite}) – für die Referenz.
     */
    public List<int[]> bereiche(Blick b) {
        List<int[]> l = new ArrayList<>();
        int von = an ? 0 : zahlHaeuser, bis = koerperAn ? zahl : zahlHaeuser;
        if (bis <= von) return l;
        int ende = Math.min(bis, pfahlErster);
        if (ende > von) l.add(new int[] {von, ende - von});
        if (koerperAn) {
            double[] auge = auge(b);
            for (float[] q : pfahlGruppen) {
                double dx = Math.max(0, Math.max(q[2] - auge[0], auge[0] - q[4])), dz = Math.max(0, Math.max(q[3] - auge[2], auge[2] - q[5]));
                if (Math.hypot(dx, dz) <= pfahlWeite) l.add(new int[] {(int) q[0], (int) q[1]});
            }
        }
        return l;
    }

    /** Seit P12c3: je Gebäude die Zahl 0 … 1 des Bilds (Fenster mit Licht, Steine). */
    public float[] zufallJeHaus() {
        float[] z = new float[bauten.gebaeude.size()];
        for (int i = 0; i < z.length; i++) z[i] = (float) zufall(bauten.gebaeude.get(i).kennung + "#" + i);
        return z;
    }

    /** Lage des Auges im Raum der Bauten (x = Ost − e0, y = Höhe, z = n0 − Nord), in double. */
    public double[] auge(Blick b) { return new double[] {b.ost - bauten.e0, b.hoehe, bauten.n0 - b.nord}; }

    @Override public void zeichnen(Ablauf a, Bildziel z) {
        z.geteilt.remove(SCHATTEN);
        z.puffer.remove(SCHATTENWERTE);
        // Bereiche: Häuser [0, zahlHaeuser), Körper [zahlHaeuser, zahl) – je nach Schalter
        int von = an ? 0 : zahlHaeuser, bis = koerperAn ? zahl : zahlHaeuser;
        if (bis <= von) return;
        Blick b = z.blick;
        double[] auge = auge(b);
        // seit P12b: die Gruppen der Pfähle in der Nähe des Auges (Abstand zur Hülle)
        List<int[]> nah = new ArrayList<>();
        if (koerperAn)
            for (float[] q : pfahlGruppen) {
                double dx = Math.max(0, Math.max(q[2] - auge[0], auge[0] - q[4])), dz = Math.max(0, Math.max(q[3] - auge[2], auge[2] - q[5]));
                if (Math.hypot(dx, dz) <= pfahlWeite) nah.add(new int[] {(int) q[0], (int) q[1]});
            }
        pfahlGezeichnet = nah.size();
        // ---- Quelle des Schattens
        double[] so = z.werte.get("Himmel/Sonne"), mo = z.werte.get("Himmel/Mond");
        double[] l = null;
        quelle = -1;
        if (so != null && so[1] > Math.sin(Math.toRadians(-0.5))) { l = new double[] {so[0], so[1], so[2]}; quelle = 0; }
        else if (mo != null && mo[1] > Math.sin(Math.toRadians(2))) { l = new double[] {mo[0], mo[1], mo[2]}; quelle = 1; }
        letzte = l;
        // ---- Fensterlicht nach Uhrzeit und Dämmerung [G]
        double hs = so != null ? so[6] : 90;
        fensterAnteil = fenster(z.zeit, hs);
        // ---- Kopf
        kopfB.clear();
        for (float f : b.ansichtProjektion()) kopfB.putFloat(f);
        float hx = (float) auge[0], hy = (float) auge[1], hz = (float) auge[2];
        double R = kruemmung;
        kopfB.putFloat(hx).putFloat(hy).putFloat(hz).putFloat(R > 0 ? (float) (1 / (2 * R)) : 0);
        kopfB.putFloat((float) (auge[0] - hx)).putFloat((float) (auge[1] - hy)).putFloat((float) (auge[2] - hz)).putFloat((float) b.hoehe);
        kopfB.putFloat((float) fensterAnteil).putFloat((float) fensterL).putFloat(so != null ? (float) so[1] : 1).putFloat(fensterAnteil > 0 ? 1 : 0);
        kopfB.flip();
        // ---- Karten des Schattens
        werteB.clear();
        double[] f = {Math.sin(Math.toRadians(b.richtung)), 0, -Math.cos(Math.toRadians(b.richtung))};
        for (int k = 0; k < KASKADEN; k++) {
            if (l != null) {
                double s = kaskaden[k], D = s + 300;
                double[] c = {auge[0] + f[0] * 0.5 * s, Math.min(auge[1], 3.0), auge[2] + f[2] * 0.5 * s};
                matrix[k] = karte(l, c, auge, s, D, n);
            } else matrix[k] = new float[16];
            for (float v : matrix[k]) werteB.putFloat(v);
        }
        werteB.putFloat(l != null ? KASKADEN : 0).putFloat(Math.max(quelle, 0)).putFloat((float) versatz).putFloat((float) abstand);
        for (int k = 0; k < 3; k++) werteB.putFloat((float) (2 * kaskaden[k] / n));
        werteB.putFloat(n);
        for (int k = 0; k < 3; k++) werteB.putFloat((float) (1 / (2 * (kaskaden[k] + 300))));
        werteB.putFloat(0);
        if (l != null) werteB.putFloat((float) l[0]).putFloat((float) l[1]).putFloat((float) l[2]).putFloat(0);
        else werteB.putFloat(0).putFloat(1).putFloat(0).putFloat(0);
        werteB.flip();
        a.durchgang("Häuser, Kopf", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(werte, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB);
            vkCmdUpdateBuffer(c, werte.handle, 0, werteB);
        });
        Bild ansicht = z.geteilt("Himmel/Ansicht"), durch = z.geteilt("Himmel/Durchlaessigkeit");
        Puffer hw = z.puffer("Himmel/Werte");
        // ---- Licht des Himmels
        a.rechne("Häuser, Himmelslicht", kLicht, kLicht.satz(scheitel, haeuser, kopf, hw, punkt.an(durch), licht, punkt.an(ansicht)), null, 1, 1, 1,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, hw), Ablauf.textur(Ablauf.RECHNEN, ansicht, durch), Ablauf.schreibt(licht)));
        // ---- Schatten
        if (l != null) {
            long satzS = zSchatten.satz(scheitel, haeuser, kopf);
            ByteBuffer push = ByteBuffer.allocateDirect(64 * KASKADEN).order(ByteOrder.nativeOrder());
            for (int k = 0; k < KASKADEN; k++) for (float v : matrix[k]) push.putFloat(v);
            a.zeichne("Häuser, Schatten", new Bild[0], null, schatten, 1f, Ablauf.liest(Ablauf.ZEICHNEN, scheitel, kopf), c -> {
                try (MemoryStack s = stackPush()) {
                    for (int k = 0; k < KASKADEN; k++) {
                        vkCmdSetViewport(c, 0, VkViewport.calloc(1, s).x(k * n).y(0).width(n).height(n).minDepth(0).maxDepth(1));
                        VkRect2D.Buffer sc = VkRect2D.calloc(1, s);
                        sc.get(0).offset().set(k * n, 0);
                        sc.get(0).extent().set(n, n);
                        vkCmdSetScissor(c, 0, sc);
                        // seit P12b: die Körper der Gestalt nur in den Kaskaden 0 und 1 – in der weiten (Texel rund 2,4 m) sind
                        // Pfähle von 12 cm und ihre Schatten nicht aufzulösen
                        ByteBuffer pk = push.slice(64 * k, 64).order(ByteOrder.nativeOrder());
                        int ende = k < KOERPER_KASKADEN ? Math.min(bis, pfahlErster) : Math.min(bis, zahlHaeuser);
                        if (ende > von) zSchatten.zeichne(c, satzS, pk, ende - von, 1, von);
                        if (k == 0) for (int[] r : nah) zSchatten.zeichne(c, satzS, pk, r[1], 1, r[0]);   // Pfähle nur in der nächsten Kaskade
                    }
                }
            });
        }
        // ---- Häuser
        long satz = zHaus.satz(scheitel, haeuser, kopf, hw, punkt.an(durch), licht, punkt.an(ansicht), punkt.an(schatten), werte);
        a.zeichne("Häuser", new Bild[] {z.hdr}, null, z.tiefe, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, scheitel, haeuser, kopf, hw, licht, werte), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, schatten)),
                c -> {
                    int ende = Math.min(bis, pfahlErster);
                    if (ende > von) zHaus.zeichne(c, satz, null, ende - von, 1, von);
                    for (int[] r : nah) zHaus.zeichne(c, satz, null, r[1], 1, r[0]);
                });
        if (l != null) {
            z.geteilt.put(SCHATTEN, schatten);
            z.puffer.put(SCHATTENWERTE, werte);
        }
    }

    /**
     * Abbildung einer Karte des Schattens (spaltenweise): Lage relativ zum Auge → Bild der Karte (x, y in −1 … 1, Tiefe
     * 0 … 1 von der Quelle weg). l: Richtung zur Quelle; c: Mitte im Raum der Bauten; s: halbe Seite; D: halbe Tiefe (m).
     * Die Mitte wird auf das Raster der Texel gelegt (in den Achsen der Karte, im festen Raum), damit Kanten beim
     * Gehen nicht flimmern.
     */
    static float[] karte(double[] l, double[] c, double[] auge, double s, double D, int n) {
        double[] r = norm(kreuz(l, Math.abs(l[1]) > 0.999 ? new double[] {0, 0, -1} : new double[] {0, 1, 0}));
        double[] u = kreuz(r, l);
        double t = 2 * s / n;
        double cr = dot(r, c), cu = dot(u, c), cl = dot(l, c);
        double sr = Math.floor(cr / t) * t, su = Math.floor(cu / t) * t;
        double[] cm = new double[3];
        for (int i = 0; i < 3; i++) cm[i] = c[i] + r[i] * (sr - cr) + u[i] * (su - cu) - auge[i];   // relativ zum Auge
        // Zeilen: x = r·(p − cm)/s, y = −u·(p − cm)/s, z = 0,5 − l·(p − cm)/(2D)
        double[][] z = {
                {r[0] / s, r[1] / s, r[2] / s, -dot(r, cm) / s},
                {-u[0] / s, -u[1] / s, -u[2] / s, dot(u, cm) / s},
                {-l[0] / (2 * D), -l[1] / (2 * D), -l[2] / (2 * D), 0.5 + dot(l, cm) / (2 * D)},
                {0, 0, 0, 1}};
        float[] m = new float[16];
        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) m[j * 4 + i] = (float) z[i][j];
        return m;
    }

    /**
     * Anteil der Fenster mit Licht [G]: nur in der Dämmerung und nachts (Sonne unter −1°, voll ab −6°), abends viel,
     * nach 22 Uhr weniger, nachts wenige, morgens einige (Ortszeit).
     */
    public double fenster(Instant t, double hoeheSonne) {
        if (t == null) return 0;
        double dunkel = Math.max(0, Math.min(1, (-hoeheSonne - 1) / 5));
        if (dunkel <= 0) return 0;
        ZonedDateTime o = t.atZone(zone);
        double h = o.getHour() + o.getMinute() / 60.0;
        double p;
        if (h >= 15 && h < 22) p = 0.65;
        else if (h >= 22 && h < 23.5) p = 0.65 - (h - 22) / 1.5 * 0.53;
        else if (h >= 23.5 || h < 5.5) p = 0.06;
        else if (h < 8) p = 0.35;
        else p = 0.5;
        return p * dunkel;
    }

    /** Wie kugel.glsl: reelle Kugelflächenfunktionen bis Grad 2 (für die Prüfung). */
    public static double[] kugelY(double x, double y, double z) {
        return new double[] {0.282095, 0.488603 * y, 0.488603 * z, 0.488603 * x, 1.092548 * x * y, 1.092548 * y * z, 0.315392 * (3 * z * z - 1), 1.092548 * x * z,
                0.546274 * (x * x - y * y)};
    }

    static double zufall(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h >>> 11) / (double) (1L << 53);
    }

    static double[] kreuz(double[] a, double[] b) { return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return new double[] {a[0] / l, a[1] / l, a[2] / l};
    }

    @Override public void close() {
        zHaus.close();
        zSchatten.close();
        kLicht.close();
        punkt.close();
        scheitel.close();
        haeuser.close();
        kopf.close();
        licht.close();
        werte.close();
        schatten.close();
    }
}
