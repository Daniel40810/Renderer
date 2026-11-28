package com.dan.lauf;

import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Seegangsnetz;
import com.dan.physics.waves.gpu.NahfeldKarte;
import com.dan.render.seegang.Schaumfeld;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Die neue Fläche des Seegangs in der Anwendung (Seegang Werkbuch, S3d; SF26, SF35–SF37 A): hält die
 * {@link Seegangsflaeche} der Bühne, baut sie im Hintergrund neu und blendet über.
 * <ul>
 * <li><b>Bau:</b> ein eigener Faden baut Nahfeld und Phasenfelder ({@link Seegangsflaeche#baue}); den Anlauf auf der Karte
 * rechnet er in Stücken zwischen den Bildern, je Bild ein Stück zu {@link #RUNDEN_JE_BILD} Runde, solange noch keine
 * Fläche steht zu {@link #RUNDEN_OHNE_FLAECHE} [G] (seit S3d-2, SF38 A). Bis die erste Fläche steht, zeichnet der Weg ohne
 * Netz (dieselben langen Wellen wie das Fernfeld der Fläche).</li>
 * <li><b>Anlass (SF36 A):</b> beim Öffnen eines Szenarios und nach einem Sprung der Zeit sofort; wenn das Auge mehr als
 * {@link #WEG_M} [G] vom Bauort ist oder der Blick sich um mehr als {@link #DREHUNG_GRAD} [G] gedreht hat, nach
 * {@link #RUHE_S} [G] Ruhe; wenn die gezeigte Fläche zur Zeit, zu der ein neuer Bau fertig wäre, mehr als
 * {@link #ALTER_S} [G] von der Zeit des Bilds abweicht. Bei Zeitraffer 300 heißt das: laufend.</li>
 * <li><b>Zielzeit (seit S3d-2, SF39 A):</b> ein Bau rechnet für die Mitte der Zeit, in der seine Fläche gezeigt wird:
 * Beginn + (Dauer dieses Baus + Ausblenden + die Hälfte von Dauer und Einblenden des nächsten) × Zeitraffer. Den Zeitraffer
 * misst der Wart aus den Bildern (Modellzeit je Sekunde der Uhr), die Dauer der Baue auf derselben Uhr; in der Pause ist die
 * Zielzeit die Zeit des Bilds.</li>
 * <li><b>Wechsel (SF35 A):</b> das alte Nahfeld geht in {@link #BLENDE_S} [G] ins Fernfeld über, dann gilt die neue
 * Fläche und ihr Nahfeld kommt in {@link #BLENDE_S} heraus ({@code SeegangKarte.nahGewicht}). Das Fernfeld nimmt seine
 * Werte am Auge ({@code SeegangKarte.fernOst/fernNord}) und zur Zeit des Bilds, darum springt es beim Wechsel nicht.
 * Zwischen „fertig gebaut“ und dem Wechsel liegt seit L2l ein Bild ({@link #wechselt}), damit eine Prüfung das Bild davor
 * liest.</li>
 * <li><b>Wechsel des Zeitraffers (seit S3d-3):</b> die Bühne meldet je Bild den gewollten Zeitraffer ({@link #zeitraffer};
 * rückwärts negativ, angehalten 0). Ändert er sich, springt der gemessene auf ihn, statt über {@link #RAFFER_TAU_S} zu
 * gleiten, und was dann zur Mitte seiner Zeit im Bild um {@link #ALTER_S} oder mehr danebenläge, fällt weg: der laufende Bau
 * und eine fertige, noch nicht überblendete Fläche ({@link #verwerfen}). Die gezeigte Fläche bleibt, bis die neue steht.
 * Die Bildschleife wartet dabei nicht: der alte Faden läuft aus, danach startet der neue Bau. Wartet die Uhr auf Stände
 * (W5c), bleibt der gewollte Zeitraffer, darum bricht dort nichts ab. Anhalten bei Zeitraffer 300: das Nahfeld passt nach
 * einem Bau statt nach zweien, und keine Fläche rund 2 h aus der Zukunft kommt ins Bild.</li>
 * <li><b>Schaum (seit S4a, SF43 A):</b> Der Wart hält ein {@link Schaumfeld} über alle Flächen und hängt es an das Bild der
 * gezeigten Fläche ({@link #schaumfeld}); es rechnet mit deren Karte weiter, ein Wechsel der Fläche lässt den Schaum
 * stehen. Ein neues Szenario und „aus“ leeren es.</li>
 * <li><b>Körperfläche (seit S7d4, Seegang Werkbuch 92, SF121 A):</b> Hat die Bühne eine Mitte gesetzt ({@link #koerpermitte})
 * und gibt es Wasserpunkte, baut derselbe Faden nach der Fläche des Auges eine zweite, feste Fläche um diese Mitte
 * ({@link Seegangsflaeche#baueKoerper}): beim ersten Bau, wenn sie fehlt, und wenn ihre Zeit {@link #ALTER_S} von der
 * Zielzeit abweicht – nicht, wenn sich nur der Blick ändert. Die Körpersuche nimmt daraus die Energie unter den Schwimmern
 * außerhalb des feinen Gitters des Auges; die erste blendet in {@link #K_BLENDE_S} ein, spätere ersetzen die alte sofort
 * (dieselbe Mitte, rund dieselbe Energie). {@code -Dseegang.koerperflaeche=aus}: keine (wie bis S7d3).</li>
 * </ul>
 * Die Bühne ruft je Bild {@link #vorBild} und {@link #nachBild} im Faden der Bildschleife; Prüfungen dürfen das ebenso
 * (SeegangAppCheck). Nur für Szenarien aus einem Archiv (das Nahfeld liest es).
 */
public final class Flaechenwart implements AutoCloseable {
    public static final String VERSION = "0.6";
    /** Seit S7d4: Körperfläche an (Vorgabe) oder aus ({@code -Dseegang.koerperflaeche=aus}). */
    public static volatile boolean KOERPERFLAECHE = !"aus".equalsIgnoreCase(System.getProperty("seegang.koerperflaeche", "an"));
    /** Seit S7d4: Einblenden der ersten Körperfläche (s) [G]. */
    public static final double K_BLENDE_S = 3;
    /**
     * Runden des Anlaufs je Bild, solange eine Fläche steht, und solange noch keine steht (seit S3d-2, SF38 A) [G]. Eine
     * Runde kostet auf der 680M rund 18 ms der Karte (L2p, B59; L2m schätzte 5 ms), darum rechnet der Anlauf nur rund jedes
     * zweite Bild ein Stück; die Wandzeit eines Stücks taugt nicht als Maß, sie wartet hinter
     * dem Bild.
     */
    public static final int RUNDEN_JE_BILD = 1, RUNDEN_OHNE_FLAECHE = 3;
    /** Dauer eines Baus (s auf der Uhr der Bildschleife) ohne und mit Fläche, bis einer gemessen ist [S]. */
    public static final double DAUER_OHNE_S = 15, DAUER_MIT_S = 30;
    /** Zeitkonstante, über die der Wart den Zeitraffer aus den Bildern mittelt (s) [G]. */
    public static final double RAFFER_TAU_S = 1;
    /** Anlass: Weg des Auges vom Bauort (m), Drehung des Blicks (°), Ruhe davor (s), Abweichung der Zeit (s) [G]. */
    public static final double WEG_M = 1000, DREHUNG_GRAD = 60, RUHE_S = 2, ALTER_S = 1800;
    /** Ruhe heißt: in {@link #RUHE_S} nicht weiter als so weit (m) und nicht mehr als so viel gedreht (°) [G]. */
    public static final double RUHE_WEG_M = 5, RUHE_DREHUNG_GRAD = 5;
    /** Dauer je Hälfte des Überblendens (s) [G]. */
    public static final double BLENDE_S = 1.5;
    /** Nach so vielen Bildern ist eine abgelöste Fläche sicher frei (Bilder in Arbeit) [G]. */
    static final int SCHLIESSEN_NACH = 4;

    private final Geraet g;
    private final Konto konto;
    private final File gebiet;
    private final Consumer<Bildteil> teil;

    // Szenario
    private Path archiv;
    private List<Seegangsnetz> netze = List.of();
    private volatile boolean an = true;

    // Flächen
    private Seegangsflaeche aktiv;
    private double aktivOst, aktivNord, aktivRichtung, aktivZeit;      // aktivZeit: Zielzeit, für die gebaut wurde
    private volatile Seegangsflaeche neu;
    private volatile double neuOst, neuNord, neuRichtung, neuZeit;
    private int phase;                               // 0 ruhig, 3 Wechsel steht an, 1 das alte blendet aus, 2 das neue blendet ein
    private long phaseSeit;
    private final List<Object[]> zuSchliessen = new ArrayList<>();
    private long bildNr;

    // Bau
    private Thread bau;
    private volatile boolean abbrechen;
    private final Semaphore takt = new Semaphore(0);
    private volatile String fehler;
    private boolean sprung, gescheitert;
    private volatile double tJetzt;
    /** Seit S9f (SF197 A): Uhr (System.nanoTime) des letzten {@link #vorBild}; ob es eins gab. */
    private long gefuehrtNs;
    private boolean gefuehrt;
    private volatile int rundenJeStueck = RUNDEN_JE_BILD;

    // seit S3d-2 (SF39 A): Zeitraffer und Dauer der Baue auf der Uhr der Bildschleife
    private double tVor = Double.NaN, raffer;
    private long nsVor, bauStartUhr;
    private boolean bauOhneFlaeche;
    private double dauerOhne = DAUER_OHNE_S, dauerMit = DAUER_MIT_S;

    // seit S3d-3: der gewollte Zeitraffer der Bühne (NaN: keiner gemeldet), womit der laufende Bau rechnet (gewollter
    // Zeitraffer, erwartete Dauer) und womit die fertige, noch nicht gezeigte Fläche gerechnet hat
    private double gewollt = Double.NaN, gewolltBau = Double.NaN, dauerBau;
    private volatile double neuGewollt = Double.NaN;
    private volatile int verworfen;
    /** Seit S4a: das Schaumfeld über alle Flächen (erst mit der ersten Fläche angelegt). */
    private Schaumfeld schaum;
    /**
     * Seit S7b-1 (Seegang Werkbuch 88): das Wasser unter den Schwimmern – die Wasserpunkte der Flotte und die Suche auf der
     * Karte, über die Wechsel der Fläche hinweg (null: keine Flotte).
     */
    private volatile com.dan.core.szene.Wasserpunkte wasserpunkte;
    private com.dan.render.seegang.Koerpersuche koerpersuche;

    /** Seit S7b-1: die Wasserpunkte der Flotte (null: keine); die gezeigte Fläche rechnet sie ab dem nächsten Bild. */
    public synchronized void wasserpunkte(com.dan.core.szene.Wasserpunkte w) { wasserpunkte(w, null); }

    /**
     * Seit S7d (Seegang Werkbuch 89): dazu das Kielwasser der Flotte (null: keins); die Kacheln rechnet die gezeigte Fläche
     * ab dem nächsten Bild vor der Clipmap.
     */
    public synchronized void wasserpunkte(com.dan.core.szene.Wasserpunkte w, com.dan.core.szene.Kielwasser kw) {
        wasserpunkte = w;
        kielwasser = kw;
        if (w != null && koerpersuche == null) koerpersuche = new com.dan.render.seegang.Koerpersuche(g, konto);
        if (kw != null && kielkarte == null) kielkarte = new com.dan.render.seegang.Kielwasserkarte(g, konto);
        if (koerpersuche != null) koerpersuche.leeren();
        Seegangsflaeche a = aktiv;
        if (a != null) {
            a.bild.koerper = w != null ? koerpersuche : null;
            a.bild.wasserpunkte = w;
            a.bild.kielkarte = kw != null ? kielkarte : null;
            a.bild.kielwasser = kw;
        }
    }

    private volatile com.dan.core.szene.Kielwasser kielwasser;
    private com.dan.render.seegang.Kielwasserkarte kielkarte;

    // seit S7d4 (SF121 A): die Körperfläche – Mitte, gezeigte und fertig gebaute, Zeit, Einblenden
    private double[] koerperMitte;
    private Seegangsflaeche kAktiv;
    private volatile Seegangsflaeche kNeu;
    private volatile double[] kNeuMitte;
    private volatile double kNeuZeit;
    private double kZeit = Double.NaN;
    private long kSeit;
    private float kGewicht;
    private volatile String kBericht = "";
    private volatile int kBaue;
    private volatile String kFehler;
    private volatile boolean kGescheitert;

    /** Seit S7d4: der letzte Fehler eines Baus der Körperfläche oder null (danach baut sie nicht mehr, bis eine andere Mitte kommt). */
    public String koerperFehler() { return kFehler; }

    /**
     * Seit S7d4: die Mitte der Körperfläche (UTM; null: keine). Eine andere Mitte nimmt die gezeigte weg; die nächste Fläche
     * des Auges oder, wenn keine ansteht, ein eigener Bau bringt die neue.
     */
    public synchronized void koerpermitte(double[] m) {
        double[] mm = m == null || !KOERPERFLAECHE ? null : m.clone();
        if (java.util.Arrays.equals(mm, koerperMitte)) return;
        koerperMitte = mm;
        kGescheitert = false;
        kFehler = null;
        if (kAktiv != null) { spaeter(kAktiv); kAktiv = null; }
        kZeit = Double.NaN;
        kGewicht = 0f;
        if (koerpersuche != null) koerpersuche.koerperflaeche(null, 0);
    }

    /** Seit S7d4: die Mitte der Körperfläche oder null. */
    public synchronized double[] koerpermitte() { return koerperMitte == null ? null : koerperMitte.clone(); }

    /** Seit S7d4: steht die Körperfläche und ist sie ganz eingeblendet? */
    public synchronized boolean koerperBereit() { return kAktiv != null && kGewicht >= 1f; }

    /** Seit S7d4: die gezeigte Körperfläche oder null. */
    public synchronized Seegangsflaeche koerperflaeche() { return kAktiv; }

    /** Seit S7d4: Bericht des letzten Baus der Körperfläche und die Zahl der Baue. */
    public String koerperBericht() { return kBericht; }

    public int koerperBaue() { return kBaue; }

    /** Seit S7d4: braucht es zur Zielzeit t eine (neue) Körperfläche? */
    private boolean koerperAnlass(double t) {
        if (koerperMitte == null || wasserpunkte == null || kNeu != null || kGescheitert) return false;
        if (kAktiv == null) return true;
        return !(Math.abs(t - kZeit) < ALTER_S);
    }

    /** Seit S7d: die Kacheln des Kielwassers (null: noch keine Flotte mit Schiffen). */
    public synchronized com.dan.render.seegang.Kielwasserkarte kielkarte() { return kielkarte; }

    /** Seit S7b-1: die Suche der Wasserpunkte auf der Karte (null: noch keine Flotte). */
    public synchronized com.dan.render.seegang.Koerpersuche koerpersuche() { return koerpersuche; }

    /** Seit S6b-2 (Seegang Werkbuch 67): der Auflauf am Blick; hängt an der gezeigten Fläche (null: keiner). */
    public volatile Auflauf auflauf;
    /** Seit S5b-2: Suche und Gischt aus den Walzen, über die Wechsel der Fläche hinweg. */
    private com.dan.render.seegang.Gischtsuche gischtsuche;
    private final com.dan.core.szene.Kammgischt kammgischt = new com.dan.core.szene.Kammgischt();
    /** Seit S5b-4: Suche der Kämme am Fuß der Kanten und ihre Folge für die Brandung, über die Wechsel der Fläche hinweg. */
    private com.dan.render.seegang.Fusssuche fusssuche;
    private final com.dan.core.szene.Fusswellen fusswellen = new com.dan.core.szene.Fusswellen();

    // Ruhe
    private double ruheOst = Double.NaN, ruheNord, ruheRichtung;
    private long ruheSeit;

    // Zahlen für den Befund
    private volatile long bauStart, bauFertig;
    private volatile int baue, stuecke, runden;
    private volatile double stueckMsSumme, stueckMsMax;
    private volatile String bericht = "";

    /**
     * @param teil setzt den Bildteil der Clipmap in der Bühne ({@code Buehne.seegang}); null nimmt ihn weg
     */
    public Flaechenwart(Geraet g, Konto konto, File gebiet, Consumer<Bildteil> teil) {
        this.g = g;
        this.konto = konto;
        this.gebiet = gebiet;
        this.teil = teil;
    }

    // ------------------------------------------------------------------ von außen

    /** Ein neues Szenario: alles weg; mit Archiv und Netzen des Seegangs baut das nächste Bild neu (sonst ruht der Wart). */
    public synchronized void szenario(Path archiv, List<Seegangsnetz> netze) { szenario(archiv, netze, 0); }

    /**
     * Wie {@link #szenario(Path, List)}; seit S3d-2 mit dem Zeitraffer, mit dem das Szenario beginnt (Modellzeit je Sekunde),
     * damit schon der erste Bau eine Zielzeit hat. Danach misst der Wart ihn selbst.
     */
    public synchronized void szenario(Path archiv, List<Seegangsnetz> netze, double zeitraffer) {
        alles(true);
        if (auflauf != null) auflauf.vergiss();                           // seit S6b-2
        this.archiv = archiv;
        this.netze = netze == null ? List.of() : List.copyOf(netze);
        raffer = zeitraffer;
        tVor = Double.NaN;
        gewollt = Double.NaN;                                              // seit S3d-3: die Bühne meldet ihn wieder
    }

    /** Ein Sprung der Zeit: neu bauen (die alte Fläche bleibt, bis die neue steht). */
    public synchronized void zeitsprung() {
        stopBau(false);
        sprung = true;
        gescheitert = false;
        tVor = Double.NaN;                                                 // der Sprung zählt nicht zum Zeitraffer
    }

    /**
     * Seit S3d-3: der gewollte Zeitraffer der Bühne (Modellzeit je Sekunde; rückwärts negativ, angehalten 0), je Bild vor
     * {@link #vorBild}. Ändert er sich, springt der gemessene Zeitraffer auf ihn, statt über {@link #RAFFER_TAU_S} zu
     * gleiten; was dann zu weit danebenläge, verwirft {@link #vorBild}. NaN oder ohne Meldung (Prüfungen, die ihn nicht
     * führen) bleibt alles wie in 0.2.
     */
    public synchronized void zeitraffer(double r) {
        if (Double.isNaN(r)) return;
        if (Double.isNaN(gewollt) || Math.abs(r - gewollt) > 1e-9) raffer = r;
        gewollt = r;
    }

    /** An oder aus (SF37 A: Qualität und Schalter im Steuerhaus); aus nimmt alles weg. */
    public synchronized void an(boolean a) {
        if (an && !a) alles(true);
        an = a;
    }

    public boolean an() { return an; }

    /** Hat das Szenario Seegang aus einem Archiv (sonst ruht der Wart)? */
    public synchronized boolean moeglich() { return archiv != null && !netze.isEmpty(); }

    /** Steht eine Fläche (mit Nahfeld) im Bild? */
    public synchronized boolean bereit() { return aktiv != null; }

    /** Seit S9f (Seegang Werkbuch 115, SF197 A): so lange (s) nach dem letzten {@link #vorBild} gilt der Wart als geführt [G]. */
    public static final double GEFUEHRT_S = 2;

    /**
     * Seit S9f (Seegang Werkbuch 115, SF197 A): Kommt die erste Fläche erst noch? An, Seegang aus einem Archiv, noch keine
     * Fläche gezeigt, kein Fehler, und jemand führt den Wart (Bildschleife, Film oder Prüfung: {@link #vorBild} vor höchstens
     * {@link #GEFUEHRT_S}). Ein Standbild ohne Wart erwartet keine. Die Bühne gibt das an die Brandung
     * ({@code Brandungsbild.netzErwartet}).
     */
    public synchronized boolean ersteFlaecheKommt() {
        return an && archiv != null && !netze.isEmpty() && aktiv == null && !gescheitert && fehler == null
                && gefuehrt && System.nanoTime() - gefuehrtNs < GEFUEHRT_S * 1e9;
    }

    /**
     * Seit S8e2 (Video): Steht eine Fläche für diesen Blick und diese Zeit – kein Bau, kein Wechsel, kein Sprung offen, nicht
     * weiter als {@link #WEG_M} und {@link #DREHUNG_GRAD} vom Ort des Baus, nicht älter als {@link #ALTER_S}?
     */
    public synchronized boolean steht(Blick b, double ts) {
        boolean baut = bau != null && bau.isAlive();
        if (!baut && neu == null && (gescheitert || fehler != null)) return true;   // es kommt keine: nicht länger warten
        if (aktiv == null || phase != 0 || neu != null || sprung || baut) return false;
        return Math.hypot(b.ost - aktivOst, b.nord - aktivNord) <= WEG_M && Math.abs(winkel(b.richtung - aktivRichtung)) <= DREHUNG_GRAD
                && Math.abs(ts - aktivZeit) < ALTER_S;
    }

    /** Seit S8e2 (Video): wartet höchstens ms, bis der Bau das Stück dieses Bilds genommen hat oder nicht mehr läuft. */
    public void warteStueck(long ms) {
        long ende = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < ende) {
            Thread th = bau;
            if (th == null || !th.isAlive() || takt.availablePermits() == 0) return;
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Läuft ein Bau? */
    public synchronized boolean baut() { return bau != null && bau.isAlive(); }

    /** Die gezeigte Fläche oder null. */
    public synchronized Seegangsflaeche flaeche() { return aktiv; }

    /** Steht ein Wechsel an oder läuft er (fertige neue Fläche, Ausblenden, Einblenden)? */
    public synchronized boolean wechselt() { return phase != 0 || neu != null; }

    /** Seit L2m, für Prüfungen: die Phase (0 ruhig, 3 Wechsel steht an, 1 ausblenden, 2 einblenden). */
    public synchronized int phase() { return phase; }

    /** Gewicht des Nahfelds der gezeigten Fläche (0 … 1), NaN ohne. */
    public synchronized double nahGewicht() { return aktiv != null ? aktiv.karte.nahGewicht : Double.NaN; }

    /** Der letzte Fehler eines Baus oder null. */
    public String fehler() { return fehler; }

    /** Zahlen für den Befund: {Baue, Stücke, Runden, ms je Stück im Mittel, ms je Stück am meisten, s des letzten Baus}. */
    public double[] zahlen() {
        return new double[] {baue, stuecke, runden, stuecke > 0 ? stueckMsSumme / stuecke : 0, stueckMsMax, bauFertig > bauStart ? (bauFertig - bauStart) * 1e-9 : Double.NaN};
    }

    /** Das Speicherkonto der Flächen (Bühne: eigenes Konto „Seegangsfläche“). */
    public Konto konto() { return konto; }

    /** Ort, Richtung und Zeit, zu denen die gezeigte Fläche gebaut wurde: {Ost, Nord, Richtung, t}; null ohne. */
    public synchronized double[] bauort() { return aktiv == null ? null : new double[] {aktivOst, aktivNord, aktivRichtung, aktivZeit}; }

    /** Seit S3d-2: {gemessener Zeitraffer, Dauer eines Baus ohne Fläche, mit Fläche (s auf der Uhr der Bildschleife)}. */
    public synchronized double[] schaetzung() { return new double[] {raffer, dauerOhne, dauerMit}; }

    /** Seit S3d-3: laufende Baue und fertige, nie gezeigte Flächen, die ein Wechsel des Zeitraffers verworfen hat. */
    public int verworfen() { return verworfen; }

    /** Seit S4a: das Schaumfeld der Anwendung (angelegt, wenn nötig). */
    /** Seit S5b-2: die Suche der Orte für die Gischt aus den Walzen (angelegt, wenn nötig). */
    public synchronized com.dan.render.seegang.Gischtsuche gischtsuche() {
        if (gischtsuche == null) gischtsuche = new com.dan.render.seegang.Gischtsuche(g, konto);
        return gischtsuche;
    }

    /** Seit S5b-2: die Gischt aus den Walzen (für die Brandung). */
    public com.dan.core.szene.Kammgischt kammgischt() { return kammgischt; }

    /** Seit S5b-4: die Suche der Kämme am Fuß der Kanten (angelegt, wenn nötig). */
    public synchronized com.dan.render.seegang.Fusssuche fusssuche() {
        if (fusssuche == null) fusssuche = new com.dan.render.seegang.Fusssuche(g, konto);
        return fusssuche;
    }

    /** Seit S5b-4: die Kämme der Fläche am Fuß der Kanten (für die Brandung). */
    public com.dan.core.szene.Fusswellen fusswellen() { return fusswellen; }

    public synchronized Schaumfeld schaumfeld() {
        if (schaum == null) schaum = new Schaumfeld(g, konto);
        return schaum;
    }

    /** Bericht des letzten Baus (Seegangsflaeche.bericht). */
    public String bericht() { return bericht; }

    // ------------------------------------------------------------------ je Bild

    /**
     * Vor dem Bild: Anlass prüfen, Bau anstoßen, überblenden, Fernfeld und Zeit führen, alte Flächen schließen.
     *
     * @param jetztNs Uhr für Ruhe und Überblenden (ns); die Bühne gibt System.nanoTime
     */
    public synchronized void vorBild(Blick b, double ts, long jetztNs) {
        bildNr++;
        gefuehrtNs = System.nanoTime();                                     // seit S9f (SF197 A)
        gefuehrt = true;
        tJetzt = ts;
        // seit S3d-2 (SF39 A): der Zeitraffer aus den Bildern, gleitend über RAFFER_TAU_S
        if (!Double.isNaN(tVor) && jetztNs > nsVor) {
            double dtu = (jetztNs - nsVor) * 1e-9;
            raffer += (1 - Math.exp(-dtu / RAFFER_TAU_S)) * ((ts - tVor) / dtu - raffer);
        }
        if (Double.isNaN(tVor) || jetztNs > nsVor) { tVor = ts; nsVor = jetztNs; }
        schliessen(false);
        if (!an || !moeglich()) return;
        koerperBild(jetztNs);                                              // seit S7d4
        ruhe(b, jetztNs);
        // seit S3d-3: nach einem Wechsel des gewollten Zeitraffers (Anhalten, langsamer, rückwärts) fällt weg, was zu weit
        // danebenläge
        verwerfen(jetztNs);
        // fertig gebaut: überblenden
        // seit L2l: ein Bild zwischen „fertig“ und dem Wechsel (Phase 3), damit eine Prüfung das Bild davor sieht
        Seegangsflaeche n = neu;
        if (n != null && phase == 0) {
            phase = 3;
            double d = (jetztNs - bauStartUhr) * 1e-9;                      // seit S3d-2: Dauer auf der Uhr der Bildschleife
            if (bauOhneFlaeche) dauerOhne = d; else dauerMit = d;
        }
        else if (phase == 3) {
            if (n == null) phase = 0;
            else if (aktiv == null) einsetzen(n, jetztNs);
            else { phase = 1; phaseSeit = jetztNs; }
        }
        if (phase == 1) {
            double w = 1 - (jetztNs - phaseSeit) * 1e-9 / BLENDE_S;
            Seegangsflaeche nn = neu;
            if (nn == null) { aktiv.karte.nahGewicht = 1f; phase = 0; }
            else if (w <= 0) {
                spaeter(aktiv);
                einsetzen(nn, jetztNs);
            } else aktiv.karte.nahGewicht = (float) w;
        } else if (phase == 2) {
            double w = (jetztNs - phaseSeit) * 1e-9 / BLENDE_S;
            if (w >= 1) { aktiv.karte.nahGewicht = 1f; phase = 0; }
            else aktiv.karte.nahGewicht = (float) w;
        }
        if (aktiv != null) {
            aktiv.karte.fernOst = b.ost;
            aktiv.karte.fernNord = b.nord;
            Auflauf au = auflauf;                                          // seit S6b-2: Anlass zum Setzen, Zeitraffer
            if (au != null) au.vorBild(b, ts, gewollt, netze);
        }
        // Anlass für einen Bau (SF36 A)
        if (bau == null || !bau.isAlive()) {
            bau = null;
            if (fehler != null) gescheitert = true;                       // erst ein neuer Anlass von außen baut wieder
            if (neu == null && !gescheitert && anlass(b, ts, jetztNs)) starte(b, ts, jetztNs, true);
            else if (neu == null && !gescheitert && aktiv != null && koerperAnlass(ts + (dauerMit + BLENDE_S) * raffer)) starte(b, ts, jetztNs, false);   // seit S7d4
        }
    }

    /** Seit S7d4: eine fertige Körperfläche übernehmen, die erste einblenden, an die Körpersuche geben. */
    private void koerperBild(long jetztNs) {
        Seegangsflaeche kn = kNeu;
        if (kn != null) {
            kNeu = null;
            if (koerperMitte == null || !java.util.Arrays.equals(kNeuMitte, koerperMitte)) kn.close();   // nie gezeigt
            else {
                boolean erste = kAktiv == null;
                if (kAktiv != null) spaeter(kAktiv);
                kAktiv = kn;
                kZeit = kNeuZeit;
                if (erste) { kGewicht = 0f; kSeit = jetztNs; }
            }
        }
        if (kAktiv != null && kGewicht < 1f) kGewicht = (float) Math.min(1, Math.max(0, (jetztNs - kSeit) * 1e-9 / K_BLENDE_S));
        if (koerpersuche != null) koerpersuche.koerperflaeche(kAktiv != null ? kAktiv.karte : null, kAktiv != null ? kGewicht : 0);
    }

    /** Nach dem Bild: der Bau darf ein Stück des Anlaufs rechnen (höchstens eins je Bild). */
    public void nachBild() {
        if (takt.availablePermits() == 0) takt.release();
    }

    // ------------------------------------------------------------------ innen

    private void einsetzen(Seegangsflaeche n, long jetztNs) {
        aktiv = n;
        aktivOst = neuOst; aktivNord = neuNord; aktivRichtung = neuRichtung; aktivZeit = neuZeit;
        neu = null;
        n.karte.nahGewicht = 0f;
        n.bild.zeitQuelle = () -> tJetzt;
        n.bild.schaum = schaumfeld();                                     // seit S4a: der Schaum bleibt über den Wechsel
        n.bild.suche = gischtsuche();                                     // seit S5b-2: die Gischt aus den Walzen
        n.bild.gischt = kammgischt;
        n.bild.fuss = fusssuche();                                        // seit S5b-4: die Kämme am Fuß der Kanten
        n.bild.fuesse = fusswellen;
        n.bild.anhang = auflauf;                                          // seit S6b-2: der Auflauf rechnet mit dieser Karte
        com.dan.core.szene.Wasserpunkte wp = wasserpunkte;                  // seit S7b-1: das Wasser unter den Schwimmern
        n.bild.koerper = wp != null ? koerpersuche : null;
        n.bild.wasserpunkte = wp;
        com.dan.core.szene.Kielwasser kw = kielwasser;                    // seit S7d: das Kielwasser der Flotte
        n.bild.kielkarte = kw != null ? kielkarte : null;
        n.bild.kielwasser = kw;
        teil.accept(n.bild);
        phase = 2;
        phaseSeit = jetztNs;
    }

    private void ruhe(Blick b, long jetztNs) {
        if (Double.isNaN(ruheOst) || Math.hypot(b.ost - ruheOst, b.nord - ruheNord) > RUHE_WEG_M || Math.abs(winkel(b.richtung - ruheRichtung)) > RUHE_DREHUNG_GRAD) {
            ruheOst = b.ost; ruheNord = b.nord; ruheRichtung = b.richtung;
            ruheSeit = jetztNs;
        }
    }

    private boolean anlass(Blick b, double ts, long jetztNs) {
        if (aktiv == null || sprung) return true;                          // die erste Fläche, oder nach einem Sprung der Zeit
        // seit S3d-2 (SF39 A): weicht die gezeigte Fläche ab, wenn ein neuer Bau fertig wäre? Bei Zeitraffer 300: laufend
        if (Math.abs(ts + (dauerMit + BLENDE_S) * raffer - aktivZeit) >= ALTER_S) return true;
        boolean ruhig = (jetztNs - ruheSeit) * 1e-9 >= RUHE_S;
        boolean weit = Math.hypot(b.ost - aktivOst, b.nord - aktivNord) > WEG_M || Math.abs(winkel(b.richtung - aktivRichtung)) > DREHUNG_GRAD;
        return weit && ruhig;
    }

    static double winkel(double d) {
        d = ((d % 360) + 360) % 360;
        return d > 180 ? d - 360 : d;
    }

    /** auge: die Fläche des Auges bauen (sonst nur die Körperfläche, seit S7d4). */
    private void starte(Blick b, double ts, long jetztNs, boolean auge) {
        abbrechen = false;
        sprung = false;
        fehler = null;
        takt.drainPermits();
        final Blick bb = b.kopie();                                       // seit L2m: der Blick des Aufrufers darf sich ändern
        // seit S3d-2: Runden je Stück (SF38 A) und Zielzeit (SF39 A): die Mitte der Zeit, in der die Fläche gezeigt wird
        bauOhneFlaeche = aktiv == null;
        rundenJeStueck = bauOhneFlaeche ? RUNDEN_OHNE_FLAECHE : RUNDEN_JE_BILD;
        bauStartUhr = jetztNs;
        double dDieser = bauOhneFlaeche ? dauerOhne : dauerMit + BLENDE_S;
        final double t = ts + (dDieser + 0.5 * (dauerMit + BLENDE_S)) * raffer;
        gewolltBau = gewollt;                                             // seit S3d-3: für verwerfen
        dauerBau = dDieser;
        final double gw = gewollt;
        final Path a = archiv;
        final List<Seegangsnetz> nz = netze;
        final double[] km = koerperMitte != null && (!auge || koerperAnlass(t)) ? koerperMitte.clone() : null;   // seit S7d4: die Körperfläche im selben Faden
        if (auge) bauStart = System.nanoTime();
        Thread th = new Thread(() -> {
            try {
                if (auge) {
                    Seegangsflaeche f = Seegangsflaeche.baue(g, konto, gebiet, a, nz, bb, t, null, this::anlauf);
                    if (abbrechen) { f.close(); return; }                      // nie gezeigt
                    neuOst = bb.ost; neuNord = bb.nord; neuRichtung = bb.richtung; neuZeit = t;
                    neuGewollt = gw;
                    bericht = f.bericht;
                    baue++;
                    bauFertig = System.nanoTime();
                    neu = f;                                                   // volatile: danach sieht die Bildschleife alles
                }
                if (km != null) {
                    try {
                        Seegangsflaeche k = Seegangsflaeche.baueKoerper(g, konto, gebiet, a, nz, km, t, this::anlauf);
                        if (abbrechen) { k.close(); return; }
                        kNeuMitte = km;
                        kNeuZeit = t;
                        kBericht = k.bericht;
                        kBaue++;
                        kNeu = k;
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Throwable e) {
                        if (!abbrechen) { kFehler = "Körperfläche: " + e; kGescheitert = true; }   // die Fläche des Auges läuft weiter
                    }
                }
            } catch (InterruptedException e) {
                // abgebrochen
            } catch (Throwable e) {
                if (!abbrechen) fehler = "Seegangsfläche: " + e;              // seit S3d-3: ein verworfener Bau ist kein Fehler
            }
        }, "Seegangsfläche");
        th.setDaemon(true);
        bau = th;
        th.start();
    }

    /**
     * Seit S3d-3: was ein Wechsel des gewollten Zeitraffers zu weit danebenlegen würde, fällt weg. Maß ist die Mitte der Zeit,
     * in der die Fläche gezeigt würde: bis dorthin vergeht vom Bild an noch {@code rest} auf der Uhr; mit dem neuen statt dem
     * gewollten Zeitraffer, mit dem gebaut wird, läge sie um {@code rest} × Unterschied daneben. Ab {@link #ALTER_S} fallen
     * weg: die fertige, noch nicht überblendete Fläche ({@code rest} = halbe Dauer und Einblenden des nächsten Baus) und der
     * laufende Bau ({@code rest} = was von seiner erwarteten Dauer bleibt, dazu dasselbe). Den Bau stößt sie nur an
     * abzubrechen; die Bildschleife wartet nicht (die Phasenfelder am Ende eines Baus brechen nicht ab, rund 3 s), der
     * nächste Bau startet, wenn sein Faden ausgelaufen ist. Weil sie Zeitraffer vergleicht und nicht Zeiten, bricht weder ein
     * Bau ab, der länger dauert als erwartet, noch einer, während die Uhr auf Stände wartet.
     */
    private void verwerfen(long jetztNs) {
        if (Double.isNaN(gewollt)) return;
        double halb = 0.5 * (dauerMit + BLENDE_S);
        Seegangsflaeche n = neu;
        if (n != null && (phase == 0 || phase == 3) && !Double.isNaN(neuGewollt) && halb * Math.abs(gewollt - neuGewollt) >= ALTER_S) {
            neu = null;
            n.close();                                                     // nie gezeigt
            phase = 0;
            verworfen++;
        }
        Thread th = bau;
        if (th != null && th.isAlive() && !abbrechen && !Double.isNaN(gewolltBau)) {
            double rest = Math.max(0, dauerBau - (jetztNs - bauStartUhr) * 1e-9) + halb;
            if (rest * Math.abs(gewollt - gewolltBau) >= ALTER_S) {
                abbrechen = true;
                th.interrupt();
                takt.release();
                verworfen++;
            }
        }
    }

    /** Der Takt des Anlaufs im Faden des Baus: je Bild ein Stück zu {@link #rundenJeStueck} Runden (seit S3d-2 fest, SF38 A). */
    private void anlauf(NahfeldKarte.Anlauf an) throws InterruptedException {
        final int z = rundenJeStueck;
        while (true) {
            takt.acquire();
            if (abbrechen) throw new InterruptedException();
            long t0 = System.nanoTime();
            int r0 = an.runden();
            boolean fertig = an.weiter(z);
            double ms = (System.nanoTime() - t0) / 1e6;
            int r = an.runden() - r0;
            stuecke++;
            runden += r;
            stueckMsSumme += ms;
            stueckMsMax = Math.max(stueckMsMax, ms);
            if (fertig) return;
        }
    }

    /** Hält einen laufenden Bau an; mit {@code neuAuch} fällt auch eine fertige, noch nicht gezeigte Fläche weg. */
    private void stopBau(boolean neuAuch) {
        Thread th = bau;
        if (th != null && th.isAlive()) {
            abbrechen = true;
            th.interrupt();
            takt.release();
            try {
                th.join(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        bau = null;
        if (!neuAuch) return;
        Seegangsflaeche n = neu;
        neu = null;
        if (n != null) n.close();                                         // nie gezeigt
        Seegangsflaeche kn = kNeu;                                         // seit S7d4
        kNeu = null;
        if (kn != null) kn.close();
    }

    private void alles(boolean auchAktiv) {
        stopBau(true);
        sprung = false;
        gescheitert = false;
        fehler = null;
        if (auchAktiv && aktiv != null) {
            teil.accept(null);
            spaeter(aktiv);
            aktiv = null;
        }
        if (auchAktiv && kAktiv != null) {                                // seit S7d4: die Körperfläche gehört zum Szenario
            spaeter(kAktiv);
            kAktiv = null;
            kZeit = Double.NaN;
            kGewicht = 0f;
            if (koerpersuche != null) koerpersuche.koerperflaeche(null, 0);
        }
        if (auchAktiv && schaum != null) schaum.leeren();                // seit S4a
        if (auchAktiv) kammgischt.leeren();                               // seit S5b-2
        if (auchAktiv) { fusswellen.leeren(); if (fusssuche != null) fusssuche.leeren(); }   // seit S5b-4
        phase = 0;
        ruheOst = Double.NaN;
    }

    private void spaeter(Seegangsflaeche f) {
        if (f != null) zuSchliessen.add(new Object[] {f, bildNr});
    }

    private void schliessen(boolean alle) {
        if (zuSchliessen.isEmpty()) return;
        if (alle) g.ruhe();
        zuSchliessen.removeIf(o -> {
            if (alle || bildNr - (long) o[1] >= SCHLIESSEN_NACH) { ((Seegangsflaeche) o[0]).close(); return true; }
            return false;
        });
    }

    @Override public synchronized void close() {
        alles(true);
        schliessen(true);
        if (schaum != null) { g.ruhe(); schaum.close(); schaum = null; }  // seit S4a
        if (gischtsuche != null) { gischtsuche.close(); gischtsuche = null; }   // seit S5b-2
        if (fusssuche != null) { fusssuche.close(); fusssuche = null; }   // seit S5b-4
        if (auflauf != null) { auflauf.close(); auflauf = null; }         // seit S6b-2
        if (koerpersuche != null) { koerpersuche.close(); koerpersuche = null; }   // seit S7b-1
        if (kielkarte != null) { kielkarte.close(); kielkarte = null; }   // seit S7d
        archiv = null;
        netze = List.of();
    }
}
