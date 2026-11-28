package com.dan.render.terrain;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
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
import com.dan.render.terrain.clipmap.Clipmap;
import com.dan.render.terrain.clipmap.Source;
import org.lwjgl.vulkan.VkBufferCopy;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Das Gelände (P6b, Werkbuch HalligHooge Teil A, 12.7): Clipmap aus dem Bestand {@code clipmap} (Kern als Kopie in
 * {@code render.terrain.clipmap}) über dem Stapel {@code core.gitter.Gelaende}, den auch die Physik liest (Leitidee 2).
 * Neben der Höhe laden vier Clipmaps derselben Lage das Material ({@link Material}): Albedo R, G, B und der Anteil Watt.
 * Ein eigener Faden lädt alle fünf nacheinander; das Bild lädt nur hoch, was zwischen zwei Runden fertig ist.
 * <p>
 * Auf der Karte: ein Puffer mit fünf Ebenen je Stufe (Ring T × T, {@code gelaende.glsl}), die Sicht der Sonne je
 * Scheitel (Kernel {@code sicht.comp}, bis 6 Stufen [G], Strahl bis 3 km [G]), das Licht des Himmels auf eine waagerechte
 * Fläche ({@code licht.comp}) und je Stufe 8 × 8 Blöcke, von denen nur die im Sichtfeld und nicht von der feineren Stufe
 * bedeckten gezeichnet werden. Lage relativ zum Auge in float, die Lage des Scheitels (0, 0) einer Stufe in double
 * gerechnet. Erdkrümmung mit Refraktion wie {@code clipmap} (R/(1 − 0,13)).
 * <p>
 * Vor dem Himmel zeichnen: Der Himmel legt die Luftperspektive darüber und liest die Tiefe. Gelesen werden die geteilten
 * {@code Himmel/Durchlaessigkeit}, {@code Himmel/Ansicht} und {@code Himmel/Werte}; der Himmel muss vorbereitet sein,
 * bevor dieser Teil zeichnet (Rahmen: erst alle vorbereiten, dann alle zeichnen).
 */
public final class Gelaendebild implements Bildteil {
    public static final String VERSION = "0.3";
    public static final int EBENEN = 5, GITTER = 257, BLOECKE = 8;
    /** Kopf des Puffers der Stufen (Bytes) und Bytes je Stufe (gelaende.glsl). */
    static final int KOPF = 64 + 15 * 16, JE_STUFE = 48;

    public final Gelaende gelaende;
    public final Material material;
    /** Ursprung des Raums der Clipmap: x = Ost − e0, z = n0 − Nord. */
    public final double e0, n0;
    public final Clipmap hoehe;
    final Clipmap[] mat = new Clipmap[4];
    final Clipmap[] alle = new Clipmap[EBENEN];
    public final int stufen, T, M, B;

    /** Stufen mit Schatten [G] und Länge des Strahls zur Sonne (m) [G]. */
    public int schattenStufen = 6;
    public double schattenWeit = 3000;
    /** Nässe nach F41: Restnässe [G], Abdunklung (wie mudflat), Halbwertszeit (s) [G]. */
    public double rest = 0.3, dunkel = 0.45, halbwert = 7200;
    /**
     * Seit P10b: Was {@link #naesse} trägt. false: {@code water.wetAge} (s), die Nässe klingt mit der Halbwertszeit auf
     * die Restnässe ab; true: {@code surf.wet} aus physics.atmos, die Sättigung des obersten Zentimeters (0 … 1) selbst.
     */
    public boolean nassAlsSaettigung;

    private final Geraet g;
    private final Konto konto;
    private final Zeichner zeichner;
    private final Kernel kSicht, kLicht;
    private final Puffer daten, sicht, stufenPuffer, licht;
    private final Puffer[] zwischen = new Puffer[3];
    private final Bild leer;
    /** Seit P12a: Werte „kein Schatten der Häuser“ ({@code core/gpu/bauschatten.glsl}). */
    private final Puffer ohneBau;
    // Seit P12b (F132 A): die Gestaltkarte als Atlas der Kacheln, ihre Tafel und der Schalter
    private Bild gestaltBild, gestaltLeer;
    private Puffer gestaltOhne;
    private Puffer gestaltPuffer;
    private ByteBuffer gestaltKopf;
    private double gestaltX0, gestaltY0;
    private int gestaltNx, gestaltNy;
    private com.dan.core.szene.Gestalt gestalt;
    /** Seit P12b: die Gestalt des Geländes zeigen (Wasser in Gräben und Fethingen, Wege, Deckwerk, Gras); aus: das Bild wie vor P12b. */
    public volatile boolean gestaltAn = !"aus".equals(System.getProperty("lauf.gestalt"));
    /**
     * Seit P13b (F152 A): Die Gestaltkarte trägt die Zonen der Salzwiese in der Art (6–9, {@code core.szene.Zonen});
     * aus: der Shader nimmt die Art ohne Zone, das Bild ist wie vor P13b.
     */
    public volatile boolean zonenAn = !"aus".equals(System.getProperty("lauf.vegetation"));
    /** Seit P13b: Das Gebiet hat Zonen der Salzwiese (sie stecken in {@link #gestalt()}). */
    public boolean hatZonen;
    /** Seit P12b [G]: Wasser der Gestalt bis zu diesem Fuß des Bildpunkts (m) voll, bis zum doppelten weich aus; Belag und Gras ebenso. */
    public volatile double gestaltWasserFuss = 4, gestaltBelagFuss = 1;
    /** Für die Prüfung (Prüfsteine 12, 13): Stärke der Kräuselung und der Muster, sonst 1. */
    public volatile double gestaltKraeusel = 1, gestaltMuster = 1;
    static final int KOPF_GESTALT = 64;
    /**
     * Seit S6c-2 (Seegang Werkbuch 82, SF93 A): Schlüssel des Bilds des Auflaufs im Bildziel (Puffer und Werte, legt
     * {@code lauf.Auflauf} ab; dieses Package kennt es nicht). Werte: Ecke Ost, Nord (UTM), Richtung x Ost, Nord, Richtung y
     * Ost, Nord, Zelle, nx, ny, Ruhewasser, Zeit, Kontur. Im Rechteck zeichnet das Gelände den Film, den nassen Sand und
     * den Spülsaum aus dem Löser ({@code gelaende.frag}); aus mit {@link #swashAn} = false.
     */
    public static final String SWASH_BILD = "Swash/Bild";
    /** Seit S6c-2: das Bild des Auflaufs im Gelände an (aus mit {@code -Dgelaende.swash=aus}). */
    public volatile boolean swashAn = !"aus".equals(System.getProperty("gelaende.swash"));
    /** Seit S6c-2: Puffer, wenn kein Bild des Auflaufs abgelegt ist (Bindung 15). */
    private Puffer swashLeer;
    private final Abtaster punkt;
    private Bild nassBild;
    private Gitter nassGitter;
    // Boden aus dem Sediment (P9b)
    private Bild bodBild, dzBild;
    /** Seit P9c: Das Gelände hebt und senkt sich mit {@code bed.dz} des Bodens aus dem Sediment (nur sinnvoll, wenn auch das Wasser mit diesem Boden gerechnet ist). */
    public boolean bodenHoehe = true;
    private Gitter bodGitter;
    /**
     * Seit S6d-1: das Bild des Bodens hat 2 · ny Zeilen – unten (0 … ny − 1) die Strömungsrippel und der Schlick aus
     * {@code bed.ripple}, oben (ny … 2 ny − 1) die Wellenrippel aus {@code lauf.Wellenrippelfeld} (Höhe, Länge, Richtung,
     * 0). Beide Hälften liegen hier, damit jede für sich neu geschrieben werden kann.
     */
    private float[] bodT;
    private boolean wellenGesetzt;
    /** Seit S6d-1: Wellenrippel zeichnen (false: nur die Strömungsrippel wie bis S6c). */
    public volatile boolean wellenrippel = true;
    /** Schlick (P9b) [G]: Abdunklung des Watts bei vollem Schlick, Schlickanteil für die volle Abdunklung; Stärke des Reliefs der Rippel (1: wie gerechnet, 0: aus). */
    public volatile double schlickDunkel = 0.3, schlickVoll = 0.3, relief = 1;
    private int bildNr;
    // Flugsand (P10c, F96 A)
    private Bild flugBild;
    private Gitter flugGitter;
    /**
     * Sandfegen (seit 0.2, P10c): Wo {@link #flugsand} einen Fluss q trägt, liegt ein Schleier aus fliegenden Körnern über
     * dem Boden. Seine senkrechte optische Dicke ist 3 Φ / (2 ρs d) mit der Masse in der Luft Φ = q / V: Korn d (m)
     * [G: Median des Japsands], Dichte des Korns (kg/m³), mittlere Geschwindigkeit der Körner V (m/s) [G: die Quellen
     * nennen sie unabhängig vom Wind, aber ohne Zahl; rund 1 m/s am Boden, mehr darüber].
     */
    public volatile double flugKorn = 0.17e-3, flugDichte = 2650, flugTempo = 2.0;
    /** Stärke der Streifen im Schleier (0: gleichmäßig … 1) und ihr Zug mit dem Wind (m/s) [G]. */
    public volatile double flugStreifen = 1, flugZug = 2.0;
    /** Zeit für den Zug der Streifen (s); NaN: die Uhr seit dem Bau dieses Teils (Laufzeit, nicht Szenenzeit: Im Zeitraffer rasten die Streifen sonst). */
    public volatile double flugZeit = Double.NaN;
    private final long flugStart = System.nanoTime();

    // Faden
    private final ReentrantLock runde = new ReentrantLock();
    private volatile double zielX = Double.NaN, zielZ = Double.NaN;
    private volatile long gewollt, geladen;
    private volatile boolean laeuft;
    private Thread lader;
    private Clipmap.Window[] gezeigt;
    private final ByteBuffer kopf;
    private ByteBuffer hoch;
    /** Zuletzt hochgeladene Floats und gezeichnete Blöcke (für Prüfungen und Zeiten). */
    public long hochgeladen, bloecke, bloeckeVerdeckt, bloeckeAusser;

    /**
     * Aus einem Gebiet ({@code gelaende/}, {@code rauheit/}, {@code luftbild/}); seit P6e mit der Mindesthöhe der
     * Bauwerke, wo das Gebiet eine hat (Deiche so hoch, wie hydro sie rechnet; {@link Gebiet#mindesthoehe}).
     */
    public static Gelaendebild aus(Geraet g, Konto konto, File gebiet) throws IOException {
        Gelaende gel = Gelaende.ordner(new File(gebiet, "gelaende"));
        Gelaende.Stufe m = Gebiet.mindesthoehe(gebiet);
        if (m != null) gel = gel.mitMindesthoehe(m);
        Gelaende.Stufe fein = gel.stufen()[0];
        double e0 = fein.suedwestOst, n0 = fein.suedwestNord + fein.kachelnNord * fein.kachel();
        Gelaendebild t = new Gelaendebild(g, konto, gel, Material.aus(gebiet, e0, n0), e0, n0, 100_000);
        com.dan.core.szene.Gestalt ge = com.dan.core.szene.Gestalt.lies(new File(gebiet, "gestalt"));
        if (ge != null) {
            com.dan.core.szene.Zonen zo = com.dan.core.szene.Zonen.lies(gebiet);                     // P13b: die Zonen der Salzwiese in der Art
            if (zo != null) {
                ge = zo.aufGestalt(ge);
                t.hatZonen = true;
            }
            t.gestalt(ge);
        }
        return t;
    }

    public Gelaendebild(Geraet g, Konto konto, Gelaende gelaende, Material material, double e0, double n0, double reichweite) {
        this.g = g;
        this.konto = konto;
        this.gelaende = gelaende;
        this.material = material;
        this.e0 = e0;
        this.n0 = n0;
        double c0 = gelaende.stufen()[0].zelle;
        Source hq = new Source() {
            @Override public float height(double x, double z, double cell) { return gelaende.hoehe(e0 + x, n0 - z, cell); }
            @Override public void fill(double x0, double z0, double cell, int w, int h, float[] out) {
                float[] zeile = new float[w];
                for (int j = 0; j < h; j++) {
                    gelaende.fuelle(e0 + x0, n0 - (z0 + j * cell), cell, w, 1, zeile);
                    System.arraycopy(zeile, 0, out, j * w, w);
                }
            }
            @Override public double finestCell() { return c0; }
            @Override public String describe() { return "Gelände " + gelaende.beschreibe(); }
        };
        hoehe = new Clipmap(hq, GITTER, c0, 0.5 * c0, 0.5 * c0, reichweite);
        hoehe.noData = -30;          // außerhalb aller Stufen: Meeresgrund [G]
        alle[0] = hoehe;
        for (int k = 0; k < 4; k++) {
            mat[k] = new Clipmap(material.kanal(k), GITTER, c0, 0.5 * c0, 0.5 * c0, reichweite);
            alle[1 + k] = mat[k];
        }
        stufen = hoehe.levels();
        T = hoehe.texSize;
        M = hoehe.grid;
        B = (M - 1) / BLOECKE;
        long ebene = (long) T * T * 4;
        daten = Puffer.speicher(g, konto, "Gelände, Daten", ebene * stufen * EBENEN);
        sicht = Puffer.speicher(g, konto, "Gelände, Sicht der Sonne", ebene * stufen);
        stufenPuffer = Puffer.speicher(g, konto, "Gelände, Stufen", KOPF + (long) JE_STUFE * stufen);
        licht = Puffer.speicher(g, konto, "Gelände, Himmelslicht", 32);
        for (int i = 0; i < 3; i++) zwischen[i] = Puffer.geteilt(g, konto, "Gelände, Zwischen " + i, ebene * stufen * EBENEN);
        kopf = ByteBuffer.allocateDirect(KOPF + JE_STUFE * stufen).order(ByteOrder.nativeOrder());
        String p = "/com/dan/render/terrain/";
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {Bild.RGBA16F};
        zeichner = Zeichner.lade(g, p + "gelaende.vert", p + "gelaende.frag", 16, null, art, P, P, P, P, X, P, X, X, X, X, X, P, X, P, X, P);
        swashLeer = Puffer.speicher(g, konto, "Gelände, ohne Auflauf", 64);
        swashLeer.schreibe(new float[16]);
        kSicht = Kernel.lade(g, p + "sicht.comp", 0, null, P, P, P, P, X);
        kLicht = Kernel.lade(g, p + "licht.comp", 0, null, P, P, P, P, X, P, X, X);
        punkt = new Abtaster(g, false, false);
        leer = new Bild(g, konto, "Gelände, ohne Nässe", 1, 1, Bild.R32F);
        leer.schreibe(new float[] {Float.NaN});
        // seit P12a: ohne Häuser liest gelaende.frag diese Werte (Zahl der Karten 0: Faktor genau 1)
        ohneBau = Puffer.speicher(g, konto, "Gelände, ohne Schatten der Häuser", 256);
        ohneBau.schreibe(new float[64]);
        gestaltLeer = new Bild(g, konto, "Gelände, ohne Gestalt", 1, 1, Bild.RG8);
        gestaltLeer.schreibe(ByteBuffer.allocateDirect(2));
        gestaltOhne = Puffer.speicher(g, konto, "Gelände, ohne Gestalt (Kopf)", 64);
        gestaltOhne.schreibe(new float[16]);
        // Sicht vorbelegen: 1 (ohne Schatten), bis der Kernel rechnet
        ByteBuffer eins = ByteBuffer.allocateDirect((int) (ebene * stufen)).order(ByteOrder.nativeOrder());
        for (int i = 0; i < T * T * stufen; i++) eins.putFloat(1f);
        eins.flip();
        sicht.schreibe(eins, 0);
    }

    /**
     * Seit P12b (F132 A): die Gestaltkarte. Die Kacheln gehen in einen Atlas (8 Kacheln je Zeile, RG8: Art, Wasserstand),
     * ihre Lage im Gitter der Kacheln in eine Tafel; {@code gelaende.frag} liest beide (Bindungen 12 und 13).
     */
    public void gestalt(com.dan.core.szene.Gestalt ge) {
        double[] h = ge.huelle();
        double s = ge.kantenlaenge();
        int n = ge.n, spalten = Math.min(8, ge.kacheln.size()), zeilen = (ge.kacheln.size() + spalten - 1) / spalten;
        gestaltX0 = h[0];
        gestaltY0 = h[1];
        gestaltNx = (int) Math.round((h[2] - h[0]) / s);
        gestaltNy = (int) Math.round((h[3] - h[1]) / s);
        ByteBuffer atlas = ByteBuffer.allocateDirect(2 * spalten * n * zeilen * n);
        int[] slot = new int[gestaltNx * gestaltNy];
        java.util.Arrays.fill(slot, -1);
        for (int k = 0; k < ge.kacheln.size(); k++) {
            com.dan.core.szene.Gestalt.Kachel ka = ge.kacheln.get(k);
            int tx = (int) Math.round((ka.ost() - h[0]) / s), ty = (int) Math.round((ka.nord() - h[1]) / s);
            slot[ty * gestaltNx + tx] = k;
            int ax = (k % spalten) * n, ay = (k / spalten) * n;
            for (int j = 0; j < n; j++) atlas.put(2 * ((ay + j) * spalten * n + ax), ka.daten(), 2 * j * n, 2 * n);
        }
        if (gestaltBild != null) gestaltBild.close();
        if (gestaltPuffer != null) gestaltPuffer.close();
        gestaltBild = new Bild(g, konto, "Gelände, Gestalt", spalten * n, zeilen * n, Bild.RG8);
        gestaltBild.schreibe(atlas);
        gestaltKopf = ByteBuffer.allocateDirect(KOPF_GESTALT + 4 * slot.length).order(ByteOrder.nativeOrder());
        gestaltPuffer = Puffer.speicher(g, konto, "Gelände, Gestalt (Kopf und Tafel)", gestaltKopf.capacity());
        ByteBuffer t = ByteBuffer.allocateDirect(4 * slot.length + KOPF_GESTALT).order(ByteOrder.nativeOrder());
        t.position(KOPF_GESTALT);
        for (int v : slot) t.putInt(v);
        t.position(0);
        gestaltPuffer.schreibe(t, 0);
        gestaltSpalten = spalten;
        this.gestalt = ge;
    }

    private int gestaltSpalten;

    /** Seit P12b: die Gestaltkarte (null: keine). */
    public com.dan.core.szene.Gestalt gestalt() { return gestalt; }

    @Override public String name() { return "Gelände"; }

    /** Die Ebene der Daten (0 Höhe, 1–3 Albedo, 4 Watt) zum Prüfen; der Puffer gehört dem Bild. */
    public Puffer daten() { return daten; }

    public Puffer stufenPuffer() { return stufenPuffer; }

    public Puffer sicht() { return sicht; }

    public Puffer licht() { return licht; }

    public Clipmap[] clipmaps() { return alle.clone(); }

    /** Die Fenster, die zuletzt hochgeladen und gezeichnet wurden. */
    public Clipmap.Window[] gezeigt() { return gezeigt == null ? null : gezeigt.clone(); }

    // ------------------------------------------------------------------ Nässe

    /**
     * Setzt die Nässe: {@code water.wetAge} (s seit dem letzten Wasser) oder, mit {@link #nassAlsSaettigung},
     * {@code surf.wet} (Sättigung 0 … 1) eines Gitters in UTM, Zellen i + nx · j von Südwest. Null nimmt sie weg (dann
     * gilt alles als trocken).
     */
    public void naesse(Gitter gitter, float[] wetAge) {
        if (gitter == null) {
            nassGitter = null;
            return;
        }
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Nässe: nur Gitter in UTM, nicht " + gitter);
        if (nassBild == null || nassBild.breite != gitter.nx || nassBild.hoehe != gitter.ny) {
            if (nassBild != null) nassBild.close();
            nassBild = new Bild(g, konto, "Gelände, Nässe " + gitter.name, gitter.nx, gitter.ny, Bild.R32F);
        }
        nassBild.schreibe(wetAge);
        nassGitter = gitter;
    }

    /**
     * Setzt den Boden aus dem Sediment (P9b): die Werte eines Stands von {@code core.szene.Bodennetz.boden} (je Feld alle
     * Zellen: Rippelhöhe, Rippellänge, Richtung als cos und sin, Schlickanteil, Bodenänderung) auf einem Gitter in UTM.
     * Null nimmt ihn weg. Das Gelände zeigt den jüngsten Stand (der Boden ändert sich langsam).
     */
    public void boden(Gitter gitter, float[] werte) {
        if (gitter == null) {
            bodGitter = null;
            return;
        }
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Boden: nur Gitter in UTM, nicht " + gitter);
        int n = gitter.zellen();
        if (werte.length < 5 * n) throw new IllegalArgumentException("Boden: " + werte.length + " Werte statt mindestens " + 5 * n);
        bodAnlegen(gitter);
        float[] t = bodT;
        for (int c = 0; c < n; c++) {
            t[4 * c] = werte[c];
            t[4 * c + 1] = werte[n + c];
            t[4 * c + 2] = (float) Math.atan2(werte[3 * n + c], werte[2 * n + c]);
            t[4 * c + 3] = werte[4 * n + c];
        }
        bodBild.schreibe(t);
        float[] dz = new float[n];
        if (werte.length >= 6 * n) System.arraycopy(werte, 5 * n, dz, 0, n);
        dzBild.schreibe(dz);
        bodGitter = gitter;
    }

    /** Das Bild des Bodens (2 · ny Zeilen) und der Bodenänderung für dieses Gitter; neu nur, wenn die Größe wechselt. */
    private void bodAnlegen(Gitter gitter) {
        if (bodBild == null || bodBild.breite != gitter.nx || bodBild.hoehe != 2 * gitter.ny) {
            if (bodBild != null) bodBild.close();
            bodBild = new Bild(g, konto, "Gelände, Boden " + gitter.name, gitter.nx, 2 * gitter.ny, Bild.RGBA32F);
            if (dzBild != null) dzBild.close();
            dzBild = new Bild(g, konto, "Gelände, Bodenänderung " + gitter.name, gitter.nx, gitter.ny, Bild.R32F);
            bodT = new float[8 * gitter.zellen()];
            wellenGesetzt = false;
        }
    }

    /**
     * Seit S6d-1: setzt die Wellenrippel ({@code lauf.Wellenrippelfeld#textur}: je Zelle Höhe, Länge, Richtung in rad, 0)
     * auf dem Gitter des Bodens. Ohne Boden aus dem Archiv legt es ihn an (Strömungsrippel und Schlick 0). Null nimmt
     * sie weg.
     */
    public void wellenrippel(Gitter gitter, float[] werte) {
        if (gitter == null || werte == null) {
            wellenGesetzt = false;
            if (bodT != null && bodBild != null) {
                java.util.Arrays.fill(bodT, bodT.length / 2, bodT.length, 0f);
                bodBild.schreibe(bodT);
            }
            return;
        }
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Wellenrippel: nur Gitter in UTM, nicht " + gitter);
        int n = gitter.zellen();
        if (werte.length < 4 * n) throw new IllegalArgumentException("Wellenrippel: " + werte.length + " Werte statt " + 4 * n);
        if (bodGitter != null && (bodGitter.nx != gitter.nx || bodGitter.ny != gitter.ny || bodGitter.x0 != gitter.x0 || bodGitter.y0 != gitter.y0))
            throw new IllegalArgumentException("Wellenrippel: Gitter " + gitter + " ist nicht das des Bodens " + bodGitter);
        boolean neu = bodGitter == null;
        bodAnlegen(gitter);
        System.arraycopy(werte, 0, bodT, 4 * n, 4 * n);
        bodBild.schreibe(bodT);
        if (neu) {
            dzBild.schreibe(new float[n]);
            bodGitter = gitter;
        }
        wellenGesetzt = true;
    }

    /**
     * Setzt den Flugsand (P10c): {@code aeol.q} eines Gitters in UTM, je Zelle der Fluss nach Ost und nach Nord
     * (kg/(m s)) bei {@code 2 c} und {@code 2 c + 1}. Null nimmt ihn weg.
     */
    public void flugsand(Gitter gitter, float[] q) {
        if (gitter == null) {
            flugGitter = null;
            return;
        }
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Flugsand: nur Gitter in UTM, nicht " + gitter);
        int n = gitter.zellen();
        if (q.length < 2 * n) throw new IllegalArgumentException("Flugsand: " + q.length + " Werte statt " + 2 * n);
        if (flugBild == null || flugBild.breite != gitter.nx || flugBild.hoehe != gitter.ny) {
            if (flugBild != null) flugBild.close();
            flugBild = new Bild(g, konto, "Gelände, Flugsand " + gitter.name, gitter.nx, gitter.ny, Bild.RGBA32F);
        }
        float[] t = new float[4 * n];
        for (int c = 0; c < n; c++) {
            t[4 * c] = q[2 * c];
            t[4 * c + 1] = q[2 * c + 1];
        }
        flugBild.schreibe(t);
        flugGitter = gitter;
    }

    /** Senkrechte optische Dicke des Schleiers je Fluss (m s/kg): 3 / (2 ρs d V). */
    public double flugDicke() { return 3 / (2 * flugDichte * flugKorn * flugTempo); }

    // ------------------------------------------------------------------ Laden

    /** Startet den Ladefaden. */
    public synchronized void start() {
        if (laeuft) return;
        laeuft = true;
        lader = new Thread(this::schleife, "gelaende-lader");
        lader.setDaemon(true);
        lader.setPriority(Thread.NORM_PRIORITY - 1);
        lader.start();
    }

    public synchronized void stop() {
        laeuft = false;
        if (lader != null) lader.interrupt();
        lader = null;
    }

    /** Wohin das Auge schaut (Raum der Clipmap). */
    public void ziel(double x, double z) {
        if (x == zielX && z == zielZ) return;
        zielX = x;
        zielZ = z;
        gewollt++;
        Thread t = lader;
        if (t != null) LockSupport.unpark(t);
    }

    /** Lädt alle Stufen für das Ziel in diesem Faden (für Prüfungen und Standbilder). */
    public void ladeJetzt() {
        long w = gewollt;
        runde.lock();
        try {
            for (Clipmap c : alle) {
                c.update(zielX, zielZ);
                c.refreshNow();
            }
        } finally {
            runde.unlock();
        }
        geladen = w;
    }

    private void schleife() {
        while (laeuft) {
            if (Double.isNaN(zielX) || geladen >= gewollt) {
                LockSupport.parkNanos(20_000_000L);
                continue;
            }
            try {
                ladeJetzt();
            } catch (RuntimeException e) {
                System.err.println("Gelände: Laden fehlgeschlagen: " + e);
                LockSupport.parkNanos(200_000_000L);
            }
        }
    }

    /** Ort des Auges im Raum der Clipmap. */
    public double[] raum(Blick b) { return new double[] {b.ost - e0, n0 - b.nord}; }

    // ------------------------------------------------------------------ je Bild

    /**
     * Seit P10b (F95 A, Blick durch das Fernglas): Bei engem Bildwinkel rückt die Mitte der Clipmap vom Auge zum
     * Zielpunkt der Bildmitte, damit das Gelände dort fein ist, wo das Bild hinsieht (wie {@link Clipmap#focusCenter}):
     * ab {@link #fernglasWinkel} Grad Bildwinkel anteilig, 11° darunter ganz; höchstens {@link #fernglasWeit} m weit.
     * Der Zielpunkt liegt, wo die Blickachse die gesenkte Ebene in 0 m NHN trifft, sonst an der Kimm. Gibt den Versatz
     * im Raum der Clipmap (x Ost, z Süd).
     */
    public double[] fokus(Blick b) {
        double w = Math.max(0, Math.min(1, (fernglasWinkel - b.bildwinkel) / 11.0));
        if (w <= 0) return new double[] {0, 0};
        double h = Math.max(1, b.hoehe), t = Math.tan(Math.toRadians(b.neigung)), k = 1 / (2 * com.dan.core.raum.Erde.R_OPTISCH);
        double d = w * Math.min(fernglasWeit, (t + Math.sqrt(t * t + 4 * k * h)) / (2 * k)), az = Math.toRadians(b.richtung);
        return new double[] {Math.sin(az) * d, -Math.cos(az) * d};
    }

    /** Bildwinkel (Grad), unter dem die Mitte der Clipmap zum Zielpunkt rückt, und die größte Weite (m) [G]. */
    public double fernglasWinkel = 27, fernglasWeit = 8000;

    @Override public void vorbereiten(Ablauf a, Bildziel z) {
        double[] xz = raum(z.blick), fk = fokus(z.blick);
        ziel(xz[0] + fk[0], xz[1] + fk[1]);
        if (!laeuft) ladeJetzt();
        hochladen(a);
        if (gezeigt == null) return;
        schreibeStufen(a, z, xz);
    }

    /** Was seit dem letzten Bild geladen ist, in den Zwischenpuffer und von dort auf die Karte. */
    private void hochladen(Ablauf a) {
        if (!runde.tryLock()) return;
        try {
            Clipmap.Window[] w = hoehe.windows();
            for (Clipmap.Window x : w) if (!x.loaded()) return;
            long ebene = (long) T * T;
            List<long[]> bereiche = new ArrayList<>();          // Quelle (Float), Ziel (Float), Anzahl
            List<float[]> quellen = new ArrayList<>();
            List<Integer> von = new ArrayList<>();
            for (int e = 0; e < EBENEN; e++) {
                final int ee = e;
                List<int[]> rechtecke = new ArrayList<>();
                float[][] speicher = new float[stufen][];
                boolean[] ganz = new boolean[stufen];
                alle[e].drain((level, i0, j0, bw, bh, store, t) -> {
                    speicher[level] = store;
                    if (bw >= M && bh >= M) ganz[level] = true;
                    else rechtecke.add(new int[] {level, i0, j0, bw, bh});
                });
                for (int L = 0; L < stufen; L++)
                    if (ganz[L]) {
                        quellen.add(speicher[L].clone());
                        bereiche.add(new long[] {0, (ee * (long) stufen + L) * ebene, ebene});
                        von.add(quellen.size() - 1);
                    }
                // Teilrechtecke: je Zeile des Rings bis zu zwei zusammenhängende Stücke
                for (int[] r : rechtecke) {
                    int L = r[0];
                    if (ganz[L]) continue;
                    long basis = (ee * (long) stufen + L) * ebene;
                    for (int j = r[2]; j < r[2] + r[4]; j++) {
                        int zr = Math.floorMod(j, T), c0 = Math.floorMod(r[1], T), n = r[3];
                        while (n > 0) {
                            int len = Math.min(n, T - c0);
                            float[] q = new float[len];
                            System.arraycopy(speicher[L], zr * T + c0, q, 0, len);
                            quellen.add(q);
                            bereiche.add(new long[] {0, basis + (long) zr * T + c0, len});
                            von.add(quellen.size() - 1);
                            n -= len;
                            c0 = 0;
                        }
                    }
                }
            }
            gezeigt = w;
            if (bereiche.isEmpty()) {
                hochgeladen = 0;
                return;
            }
            long n = 0;
            for (long[] b : bereiche) n += b[2];
            if (hoch == null || hoch.capacity() < n * 4) hoch = ByteBuffer.allocateDirect((int) (n * 4)).order(ByteOrder.nativeOrder());
            hoch.clear();
            FloatBuffer fb = hoch.asFloatBuffer();
            long o = 0;
            for (int i = 0; i < bereiche.size(); i++) {
                long[] b = bereiche.get(i);
                b[0] = o;
                fb.put(quellen.get(von.get(i)));
                o += b[2];
            }
            hoch.limit((int) (n * 4));
            Puffer zw = zwischen[bildNr++ % zwischen.length];
            zw.schreibe(hoch, 0);
            hochgeladen = n;
            a.durchgang("Gelände hochladen", new Ablauf.Zugriff[] {
                    new Ablauf.Zugriff(zw, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, 0),
                    new Ablauf.Zugriff(daten, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
                VkBufferCopy.Buffer k = VkBufferCopy.calloc(bereiche.size());       // viele Stücke: nicht auf dem Stapel
                try {
                    for (int i = 0; i < bereiche.size(); i++) {
                        long[] b = bereiche.get(i);
                        k.get(i).srcOffset(b[0] * 4).dstOffset(b[1] * 4).size(b[2] * 4);
                    }
                    vkCmdCopyBuffer(c, zw.handle, daten.handle, k);
                } finally {
                    k.free();
                }
            });
        } finally {
            runde.unlock();
        }
    }

    /** Lage des Scheitels (0, 0) der Stufe relativ zum Auge: {x, z, i0, j0}. */
    double[] lage(Clipmap.Window w, double[] auge) {
        int h = hoehe.half(), i0 = w.ci() - h, j0 = w.cj() - h;
        return new double[] {hoehe.originX + i0 * w.cell() - auge[0], hoehe.originZ + j0 * w.cell() - auge[1], i0, j0};
    }

    private void schreibeStufen(Ablauf a, Bildziel z, double[] auge) {
        Blick b = z.blick;
        kopf.clear();
        for (float f : b.ansichtProjektion()) kopf.putFloat(f);
        double R = hoehe.curvatureRadius;
        kopf.putFloat((float) b.hoehe).putFloat(R > 0 ? (float) (1 / (2 * R)) : 0).putFloat(T).putFloat(M);
        kopf.putFloat(stufen).putFloat(B).putFloat(Math.min(schattenStufen, stufen)).putFloat((float) schattenWeit);
        Gitter ng = nassGitter;
        if (ng != null) {
            kopf.putFloat((float) (ng.x0 - b.ost)).putFloat((float) (ng.y0 - b.nord)).putFloat((float) ng.dx).putFloat((float) ng.dy);
            kopf.putInt(ng.nx).putInt(ng.ny).putInt(1).putInt(0);
        } else {
            kopf.putFloat(0).putFloat(0).putFloat(1).putFloat(1);
            kopf.putInt(0).putInt(0).putInt(0).putInt(0);
        }
        kopf.putFloat((float) rest).putFloat((float) dunkel).putFloat((float) halbwert).putFloat(nassAlsSaettigung ? 1 : 0);
        Gitter bg = bodGitter;
        if (bg != null) kopf.putFloat((float) (bg.x0 - b.ost)).putFloat((float) (bg.y0 - b.nord)).putFloat((float) bg.dx).putFloat(1).putInt(bg.nx).putInt(bg.ny).putInt(0).putInt(0);
        else kopf.putFloat(0).putFloat(0).putFloat(1).putFloat(0).putInt(0).putInt(0).putInt(0).putInt(0);
        kopf.putFloat((float) schlickDunkel).putFloat((float) schlickVoll).putFloat((float) relief).putFloat(bg != null && wellenGesetzt && wellenrippel ? 1 : 0);
        Gitter fg = flugGitter;
        if (fg != null) kopf.putFloat((float) (fg.x0 - b.ost)).putFloat((float) (fg.y0 - b.nord)).putFloat((float) fg.dx).putFloat(1).putInt(fg.nx).putInt(fg.ny).putInt(0).putInt(0);
        else kopf.putFloat(0).putFloat(0).putFloat(1).putFloat(0).putInt(0).putInt(0).putInt(0).putInt(0);
        double fz = flugZeit;
        if (Double.isNaN(fz)) fz = (System.nanoTime() - flugStart) * 1e-9;
        kopf.putFloat((float) flugDicke()).putFloat((float) (fz % 3600)).putFloat((float) flugStreifen).putFloat((float) flugZug);
        double[] sand = material.albedo != null && material.albedo.length > 2 && material.albedo[2] != null && !Double.isNaN(material.albedo[2][0]) ? material.albedo[2] : new double[] {0.45, 0.40, 0.32};
        kopf.putFloat((float) sand[0]).putFloat((float) sand[1]).putFloat((float) sand[2]).putFloat(0);
        // seit S6c-2: das Rechteck des Auflaufs (relativ zum Auge dieses Bilds)
        double[] sw = swashAn && z.puffer.containsKey(SWASH_BILD) ? z.werte.get(SWASH_BILD) : null;
        if (sw != null && sw.length >= 12) {
            kopf.putFloat((float) (sw[0] - b.ost)).putFloat((float) (sw[1] - b.nord)).putFloat((float) sw[2]).putFloat((float) sw[3]);
            kopf.putFloat((float) sw[4]).putFloat((float) sw[5]).putFloat((float) sw[6]).putFloat((float) sw[11]);
            kopf.putInt((int) sw[7]).putInt((int) sw[8]).putInt(1).putInt(Float.floatToRawIntBits((float) sw[10]));   // w seit S6c-2b: Zeit der Karte (Bits)
        } else {
            kopf.putFloat(0).putFloat(0).putFloat(1).putFloat(0).putFloat(0).putFloat(1).putFloat(1).putFloat(0);
            kopf.putInt(0).putInt(0).putInt(0).putInt(0);
        }
        for (int L = 0; L < stufen; L++) {
            Clipmap.Window w = gezeigt[L];
            double[] l = lage(w, auge);
            kopf.putInt((int) l[2]).putInt((int) l[3]).putInt(L + 1 < stufen ? 1 : 0).putInt(0);
            kopf.putFloat((float) l[0]).putFloat((float) l[1]).putFloat((float) w.cell()).putFloat(0);
            if (L > 0) {
                double[] f = innen(L, auge);
                kopf.putFloat((float) f[0]).putFloat((float) f[1]).putFloat((float) f[2]).putFloat((float) f[3]);
            } else kopf.putFloat(1).putFloat(1).putFloat(-1).putFloat(-1);
        }
        kopf.flip();
        a.durchgang("Gelände, Stufen", new Ablauf.Zugriff[] {new Ablauf.Zugriff(stufenPuffer, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, stufenPuffer.handle, 0, kopf));
    }

    /**
     * Rechteck der Stufe L − 1 relativ zum Auge (xmin, zmin, xmax, zmax): dort zeichnet die feinere Stufe. Um eine halbe
     * Zelle der feineren Stufe kleiner [G]: Die gröbere Stufe läuft unter dem Rand der feineren durch, damit an der Naht
     * keine Bildpunkte durch Rundung der Lage in float fallen (Befund P6b: ein Loch auf 130 000 Bildpunkte).
     */
    double[] innen(int L, double[] auge) {
        double[] r = gezeigt[L - 1].rect(hoehe);
        double u = 0.5 * gezeigt[L - 1].cell();
        return new double[] {r[0] - auge[0] + u, r[1] - auge[1] + u, r[2] - auge[0] - u, r[3] - auge[1] - u};
    }

    @Override public void zeichnen(Ablauf a, Bildziel z) {
        if (gezeigt == null) return;
        Bild ansicht = z.geteilt("Himmel/Ansicht"), durch = z.geteilt("Himmel/Durchlaessigkeit");
        Puffer werte = z.puffer("Himmel/Werte");
        Bild nb = nassGitter != null ? nassBild : leer;
        Ablauf.Zugriff[] lies = Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, daten, stufenPuffer, werte), Ablauf.textur(Ablauf.RECHNEN, durch, ansicht));
        a.rechne("Gelände, Himmelslicht", kLicht, kLicht.satz(daten, sicht, stufenPuffer, werte, punkt.an(durch), licht, punkt.an(nb), punkt.an(ansicht)), null, 1, 1, 1,
                Ablauf.und(lies, Ablauf.schreibt(licht)));
        int ns = Math.min(schattenStufen, stufen);
        if (ns > 0)
            a.rechne("Gelände, Sicht der Sonne", kSicht, kSicht.satz(daten, sicht, stufenPuffer, werte, punkt.an(durch)), null, Kernel.gruppen(M, 8), Kernel.gruppen(M, 8), ns,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, daten, stufenPuffer, werte), Ablauf.textur(Ablauf.RECHNEN, durch), Ablauf.schreibt(sicht)));
        Bild bb = bodGitter != null ? bodBild : leer;
        Bild db = bodGitter != null && bodenHoehe ? dzBild : leer;
        Bild fb = flugGitter != null ? flugBild : leer;
        // seit P12a: der Schatten der Häuser, wenn render.bau ihn abgelegt hat
        Bild bauB = z.geteilt.getOrDefault("Bau/Schatten", leer);
        Puffer bauW = z.puffer.getOrDefault("Bau/Schattenwerte", ohneBau);
        // seit P12b: die Gestaltkarte (Kopf je Bild: Lage relativ zum Auge, Zeit für die Kräuselung)
        boolean ga = gestaltBild != null && gestaltAn;
        Bild gb = ga ? gestaltBild : gestaltLeer;
        Puffer gp = ga ? gestaltPuffer : gestaltOhne;
        if (ga) {
            Blick bl = z.blick;
            double fz = flugZeit;
            if (Double.isNaN(fz)) fz = (System.nanoTime() - flugStart) * 1e-9;
            gestaltKopf.clear();
            gestaltKopf.putFloat((float) (gestaltX0 - bl.ost)).putFloat((float) (gestaltY0 - bl.nord)).putFloat((float) gestalt.zelle).putFloat(1);
            gestaltKopf.putInt(gestaltNx).putInt(gestaltNy).putInt(gestaltSpalten).putInt(gestalt.n);
            gestaltKopf.putFloat((float) (fz % 3600)).putFloat((float) gestaltWasserFuss).putFloat((float) gestaltBelagFuss).putFloat(hatZonen && zonenAn ? 1 : 0);
            gestaltKopf.putFloat((float) gestaltKraeusel).putFloat((float) gestaltMuster).putFloat(0).putFloat(0);
            gestaltKopf.flip();
            a.durchgang("Gelände, Gestalt", new Ablauf.Zugriff[] {new Ablauf.Zugriff(gestaltPuffer, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                    c -> vkCmdUpdateBuffer(c, gestaltPuffer.handle, 0, gestaltKopf));
        }
        Puffer swP = swashAn && z.werte.containsKey(SWASH_BILD) ? z.puffer.getOrDefault(SWASH_BILD, swashLeer) : swashLeer;   // seit S6c-2
        long satz = zeichner.satz(daten, sicht, stufenPuffer, werte, punkt.an(durch), licht, punkt.an(nb), punkt.an(bb), punkt.an(db), punkt.an(fb), punkt.an(bauB), bauW,
                punkt.an(gb), gp, punkt.an(ansicht), swP);
        List<int[]> wahl = waehle(z.blick);
        ByteBuffer push = ByteBuffer.allocateDirect(16 * Math.max(1, wahl.size())).order(ByteOrder.nativeOrder());
        a.zeichne("Gelände", new Bild[] {z.hdr}, null, z.tiefe, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, daten, sicht, stufenPuffer, werte, licht, bauW, gp, swP), Ablauf.textur(Ablauf.ZEICHNEN, durch, nb, bb, db, fb, bauB, gb, ansicht)), c -> {
                    int i = 0;
                    for (int[] w : wahl) {
                        ByteBuffer p = push.slice(16 * i++, 16).order(ByteOrder.nativeOrder());
                        p.putInt(0, w[0]).putInt(4, w[1]).putInt(8, w[2]).putInt(12, 0);
                        zeichner.zeichne(c, satz, p, 6 * B * B, 1);
                    }
                });
    }

    /** Die Blöcke, die gezeichnet werden: im Sichtfeld und nicht ganz von der feineren Stufe bedeckt. {Stufe, bx, by}. */
    List<int[]> waehle(Blick b) {
        double[] auge = raum(b);
        float[] vp = b.ansichtProjektion();
        List<int[]> l = new ArrayList<>();
        bloecke = bloeckeVerdeckt = bloeckeAusser = 0;
        for (int L = 0; L < stufen; L++) {
            Clipmap.Window w = gezeigt[L];
            double[] lg = lage(w, auge);
            double c = w.cell();
            double[] in = L > 0 ? innen(L, auge) : null;
            double y0 = w.min() - b.hoehe, y1 = w.max() - b.hoehe;
            for (int by = 0; by < BLOECKE; by++)
                for (int bx = 0; bx < BLOECKE; bx++) {
                    double x0 = lg[0] + bx * B * c, x1 = x0 + B * c, z0 = lg[1] + by * B * c, z1 = z0 + B * c;
                    if (in != null && x0 >= in[0] && x1 <= in[2] && z0 >= in[1] && z1 <= in[3]) {
                        bloeckeVerdeckt++;
                        continue;
                    }
                    double r = Math.max(Math.max(Math.abs(x0), Math.abs(x1)), Math.max(Math.abs(z0), Math.abs(z1)));
                    double drop = hoehe.drop(r * Math.sqrt(2));
                    if (!sichtbar(vp, x0, x1, y0 - drop, y1, z0, z1)) {
                        bloeckeAusser++;
                        continue;
                    }
                    l.add(new int[] {L, bx, by});
                    bloecke++;
                }
        }
        return l;
    }

    /** Liegt die Schachtel (relativ zum Auge) wenigstens teilweise im Sichtfeld? Wahr im Zweifel. */
    static boolean sichtbar(float[] m, double x0, double x1, double y0, double y1, double z0, double z1) {
        int[] aus = new int[5];
        for (int k = 0; k < 8; k++) {
            double x = (k & 1) == 0 ? x0 : x1, y = (k & 2) == 0 ? y0 : y1, z = (k & 4) == 0 ? z0 : z1;
            double cx = m[0] * x + m[4] * y + m[8] * z + m[12], cy = m[1] * x + m[5] * y + m[9] * z + m[13], cw = m[3] * x + m[7] * y + m[11] * z + m[15];
            if (cw <= 0) aus[4]++;
            if (cx < -cw) aus[0]++;
            if (cx > cw) aus[1]++;
            if (cy < -cw) aus[2]++;
            if (cy > cw) aus[3]++;
        }
        for (int v : aus) if (v == 8) return false;
        return true;
    }

    @Override public void close() {
        stop();
        zeichner.close();
        kSicht.close();
        kLicht.close();
        daten.close();
        sicht.close();
        stufenPuffer.close();
        licht.close();
        for (Puffer p : zwischen) p.close();
        leer.close();
        ohneBau.close();
        gestaltLeer.close();
        gestaltOhne.close();
        swashLeer.close();
        if (gestaltBild != null) gestaltBild.close();
        if (gestaltPuffer != null) gestaltPuffer.close();
        if (bodBild != null) bodBild.close();
        if (dzBild != null) dzBild.close();
        if (nassBild != null) nassBild.close();
        if (flugBild != null) flugBild.close();
        punkt.close();
    }
}
