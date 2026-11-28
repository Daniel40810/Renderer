package com.dan.lauf;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Brandung;
import com.dan.core.szene.Seegangsnetz;
import com.dan.core.szene.Seegangsoptik;
import com.dan.physics.swash.Boden;
import com.dan.physics.swash.Swash;
import com.dan.physics.swash.gpu.SwashKarte;
import com.dan.render.seegang.Randwelle;
import com.dan.render.seegang.SeegangKarte;
import com.dan.render.seegang.Seegangbild;

import java.util.List;
import java.util.Locale;

/**
 * Der Auflauf in der Anwendung (Seegang Werkbuch, S6b-2, Datei 67; SF52–SF55, SF61–SF64 A): ein Rechteck am Blick,
 * längs der Uferlinie gedreht, in dem {@code physics.swash} auf der Karte rechnet, angetrieben am seeseitigen Rand aus
 * der Synthese ({@code render.seegang.Randwelle}). Hier treffen Physik und Bild zusammen, die einander nicht kennen.
 * <ul>
 * <li><b>Lage</b> ({@link #setze}, auf dem Prozessor): Längs der Blickrichtung vom Auge die erste Wasserlinie zwischen
 * {@link #SUCHE_VON} und {@link #SUCHE_BIS} [G] (nass: Spiegel mindestens 1 cm über dem Gelände). Die Richtung zur See
 * folgt aus dem Gefälle des Geländes über ± 10 m [G], dann aus der Ausgleichsgeraden durch die Wasserlinie an
 * {@code 2 · LINIE_STELLEN + 1} Stellen über die Länge des Rechtecks (mindestens 5), die Neigung tan β im Mittel über ± 5 m an
 * diesen Stellen. y liegt längs des Ufers ({@link #NY} Zellen, seit 0.2 128 m statt 256 m, SF65 A), x quer dazu
 * ({@link #NX}, 64 m), von der See (x = 0, Seite W mit Antrieb) zum Land (Seite E). Seit 0.4 (Datei 73, F3) haben
 * alle vier Seiten einen Außenzustand je Randzelle: wo innen die See ist, an W die Synthese, an S und N die Zelle innen
 * (seit 0.6, F6: der Stau bleibt; in 0.5 ruhendes Wasser am Spiegel, F5, in 0.4 die Synthese), an E ruhendes Wasser am
 * Spiegel; wo innen Land ist, ein trockenes Außen, über das Wasser nur hinausfließt (Überlauf, SF70 A). In Ruhe ist nur
 * gefüllt, was mit dem seeseitigen Rand verbunden ist (F4). Liegt eine Krone innerhalb der Reichweite, reicht das Rechteck mindestens
 * {@link #KRONE_HINTER} m hinter sie. Landwärts so
 * weit wie der Auflauf (SF64 A): {@link #AUFLAUF_FAKTOR} · max(R2 % nach Stockdon u. a. 2006, Ru2 % nach EurOtop 2018) /
 * tan β [G], zwischen {@link #LAND_MIN} und {@link #LAND_MAX}, gemessen ab der landwärtigsten gefundenen Stelle der
 * Wasserlinie (SF67 A; ist der seeseitige Rand dann zu flach, ab P); seewärts der Rest, mindestens {@link #SEE_MIN}. Grund aus dem Gelände
 * (bilinear auf 0,25 m; ohne Wert: Fels), Ruhewasser und Grundwasser aus dem Spiegel des Wassers am seeseitigen Rand.
 * Boden bis 0.7 einheitlich Sand d50 0,2 mm [G], seit 0.8 aus den Klassen des Geländes (s. unten). Liegt der seeseitige Rand im Mittel flacher
 * als {@link #TIEFE_MIN} [G], bleibt der Löser aus (die gebundene lange Welle im Antrieb gilt dort nicht, A7); das prüft
 * das Setzen an 33 Stellen längs des Rands, bevor es das Gitter baut.</li>
 * <li><b>Neu gesetzt</b> ({@link #vorBild}): wenn sich das Auge mehr als {@link #WEG_M} bewegt oder der Blick mehr als
 * {@link #DREHUNG_GRAD} gedreht hat [G], nach einem Sprung der Zeit; nur bei Zeitraffer 1 (SF63 A), angehalten ruht er.</li>
 * <li><b>Je Bild</b> ({@link #vorbereiten}, als {@link Seegangbild.Anhang} nach der Clipmap im Ablauf des Bilds): der
 * Antrieb zur Zeit des Bilds in den einen Stand, der vorige bleibt im anderen; dann n Schritte von der vorigen Zeit bis
 * zu dieser, der Antrieb je Stufe linear dazwischen. Den Zeitschritt nimmt der Lauf aus {@code tempo} (zuletzt fertig,
 * × {@link #SICHERHEIT} [G], seit 0.2 1,2 statt 1,5; Courant der Referenz 0,4, SF65 A); mehr als
 * {@link #SCHRITTE_MAX} Schritte je Bild (seit 0.2 8 statt 4, SF66 A) macht er nicht (dann bleibt der Auflauf hinter der
 * Szene zurück, gezählt in {@link #verpasst}).</li>
 * </ul>
 * Seit 0.8 (S6c-3, Seegang Werkbuch 80, SF89 A) kommt der Boden aus den Klassen des Geländes ({@link #boden},
 * {@link Bodenkarte}: Deich und Warft Klei mit Grasnarbe, Deckwerk dicht, Watt und Sände aus dem Korn); ohne sie überall
 * Sand d50 0,2 mm (A8).
 * <p>
 * Seit 0.7 (S6c-1b, Seegang Werkbuch 78, SF85 A) erhält der Antrieb die Richtung jeder Welle im Löser
 * ({@code Randwelle.bezug}: Mitte der Punkte, Tiefe des Rands im Mittel); ohne das lief am Deich nur rund die Hälfte der
 * Energie hinein (Datei 77).
 * <p>
 * Das Bild zeigt den Löser erst mit S6c ({@code render.water}); bis dahin rechnet er nur. {@code -Dlauf.auflauf=aus}: kein
 * Auflauf.
 * <p>
 * Seit 0.11 (S6d-2, Seegang Werkbuch 84, SF58 A): <b>Flutkante</b>, die zweite Form des Rechtecks für das Watt. Ist der
 * seeseitige Rand der ersten Form zu flach ({@link #TIEFE_MIN}) oder das Ufer flacher als {@link #NEIGUNG_WATT}, setzt es dieselben {@link #NX} × {@link #NY} Zellen zu
 * {@link #DX_WATT} m (256 m quer, 512 m längs): seewärts so wenig wie möglich ab {@link #SEE_MIN_WATT} m in Schritten von
 * {@link #SEE_SCHRITT_WATT} m, bis der Rand im Mittel {@link #TIEFE_MIN_WATT} m tief ist, landwärts mindestens
 * {@link #LAND_MIN_WATT} m. Antrieb wie sonst: der Spiegel der Hydro am Rand ({@link #wasserstand}) mit der Synthese
 * darauf – steigt er, läuft die Flut über das Watt. Seiten: W überall die Welle (wo es trocken ist, gibt sie kein Wasser),
 * S und N die Zelle innen (die Front läuft längs des Rands weiter), E wie sonst. Neu gesetzt nicht nach dem Pegel
 * ({@link #NEU_PEGEL}), sondern wenn die Front das Rechteck verlässt: der Spiegel der Hydro schneidet den Grund im
 * Median der Zeilen weniger als {@link #FRONT_RAND} m vor der Landseite, oder der seeseitige Rand ist im Mittel flacher
 * als die Hälfte von {@link #TIEFE_MIN_WATT} ({@link #frontRaus}). Aus mit {@code -Dlauf.flutkante=aus}.
 */
public final class Auflauf implements Seegangbild.Anhang, AutoCloseable {
    public static final String VERSION = "0.11";
    /** Gelände (m NHN; NaN: kein Wert) und Spiegel des Wassers (m NHN; NaN: keiner) am Ort (UTM). */
    @FunctionalInterface public interface Feld { double wert(double ost, double nord); }

    /**
     * Seit 0.8 (S6c-3, Seegang Werkbuch 80, SF89 A): der Boden am Ort (UTM) als {Manning n, Durchlässigkeit k_f (m/s),
     * Klasse}; {@link #name} nennt die Klasse für den Bericht. Die Anwendung gibt {@link Bodenkarte}.
     */
    public interface Bodenquelle {
        double[] werte(double ost, double nord);

        default String name(int klasse) { return "Klasse " + klasse; }
    }

    /** Zellen quer (x) und längs (y), Zellgröße (m) (SF53 A). */
    public static final int NX = 256, NY = 512;
    public static final double DX = 0.25;
    /** Suche der Wasserlinie längs des Blicks (m) [G]. */
    public static final double SUCHE_VON = 15, SUCHE_BIS = 300, SUCHE_SCHRITT = 1;
    /** Landwärts mindestens, höchstens (m), so viel mal der geschätzte Auflauf durch tan β (SF64 A) [G]. */
    public static final double LAND_MIN = 8, LAND_MAX = 48, AUFLAUF_FAKTOR = 2;
    /** Seewärts der Wasserlinie bei P mindestens (m) [G]. */
    public static final double SEE_MIN = 8;
    /** Seit 0.3 (SF70 A): so weit reicht das Rechteck mindestens hinter eine Krone (m); als Krone gilt der höchste Grund, hinter dem er um so viel fällt (m) [G]. */
    public static final double KRONE_HINTER = 5, KRONE_ABFALL = 0.2;
    /** Mittlere Tiefe am seeseitigen Rand mindestens (m) [G]. */
    public static final double TIEFE_MIN = 0.3;
    /**
     * Seit 0.11 (S6d-2, SF58 A): die Form für das Watt – Zelle (m), mittlere Tiefe am seeseitigen Rand mindestens (m),
     * seewärts mindestens und Schritt der Suche (m), landwärts mindestens (m), Abstand der Front zur Landseite, ab dem neu
     * gesetzt wird (m) [G].
     */
    public static final double DX_WATT = 1, TIEFE_MIN_WATT = 0.1, SEE_MIN_WATT = 16, SEE_SCHRITT_WATT = 8, LAND_MIN_WATT = 64, FRONT_RAND = 16;
    /**
     * Seit 0.11: flacher als das (tan β) gilt das Watt, auch wenn der Rand der ersten Form tief genug wäre (L4v: am Watt
     * 1:1054 lag ein Priel vor der Linie): dort liefe die Front bei 0,5 m/h Steigen mit 0,1–0,3 m/s in wenigen Minuten
     * über die 64 m der ersten Form [G].
     */
    public static final double NEIGUNG_WATT = 1.0 / 200;
    /** Seit 0.11: Flutkante (Form für das Watt) an; aus mit {@code -Dlauf.flutkante=aus}. */
    public volatile boolean flutkante = !"aus".equals(System.getProperty("lauf.flutkante"));
    /** Neu setzen ab so viel Weg des Auges (m) oder Drehung (Grad) [G]; Sprung der Zeit (s). */
    public static final double WEG_M = 40, DREHUNG_GRAD = 30, SPRUNG_S = 1;
    /** Ohne Rechteck alle so viel Sekunden Modellzeit ein neuer Versuch; neu setzen, wenn der Spiegel so weit vom Ruhewasser ist (m, Tide) [G]. */
    public static final double NEU_VERSUCH_S = 5, NEU_PEGEL = 0.25;
    /** Wasserlinie für Richtung und Neigung: Stellen je Seite (über die halbe Länge), Suche quer ± (m) [G]. */
    public static final int LINIE_STELLEN = 8;
    public static final double LINIE_SUCHE = 40;
    /** Seit S6c-2b: das Tempo je Zeile nur jedes so vielte Bild [G]. */
    public static final int TEMPO_JE = 2;
    /** Punkte des Antriebs längs des Rands alle (m) [G]. */
    public static final double PUNKT_ABSTAND = 2;
    /** Zeitschritt: Courant der Referenz, Sicherheit auf das zuletzt gemessene Tempo, höchstens Schritte je Bild [G]. */
    public static final double SICHERHEIT = 1.2;
    public static final int SCHRITTE_MAX = 8;
    /**
     * Seit 0.9 (S6c-2, Seegang Werkbuch 82, SF93 A): das Bild des Rechtecks für Gelände und Wasser im Bildziel – Puffer
     * {@link #BILD} ({@code SwashKarte.bild}) und Werte unter demselben Schlüssel: {Ecke der Zelle (0, 0) Ost, Nord (UTM),
     * Richtung x (quer, landwärts) Ost, Nord, Richtung y (längs) Ost, Nord, Zelle (m), nx, ny, Ruhewasser (m NHN), Zeit des
     * Lösers (s), Kontur des Spülsaums (m)}. Ohne Rechteck oder ohne Rechnen fehlen beide.
     */
    public static final String BILD = "Swash/Bild";
    /** Seit 0.9: Wasser über dem Grund, ab dem der höchste Stand einer Zelle zum Spülsaum zählt (m) [G]. */
    public static final double KONTUR_BILD = 0.01;
    /** Seit 0.9: Bild für Gelände und Wasser an (aus mit {@code -Dlauf.swashbild=aus}: wie bis 0.8). */
    public volatile boolean bildAn = !"aus".equals(System.getProperty("lauf.swashbild"));
    /** Seit 0.9: ist das Bild der Karte für das jetzige Rechteck gerechnet? */
    private boolean bildGilt;
    /**
     * Seit 0.9 (Seegang Werkbuch 82, SF94 A): ändert den Grund nach dem Setzen, vor dem Füllen (Prüfungen: der Maßstab rechnet
     * so EurOtops Seegang am Fuß ohne Bauwerk). null in der Anwendung.
     */
    public interface Grundaenderung { void aendere(Swash s, double ruhe); }

    public volatile Grundaenderung grundAendern;

    private final Geraet g;
    private final Konto konto;
    private final Feld gelaende, wasser;
    public volatile boolean an = true;

    private SwashKarte karte, karteStrand, karteWatt;
    private Randwelle randwelle;
    /** Seit 0.11: Zelle der jetzigen Form (m); Form für das Watt? */
    private double dx = DX;
    private boolean watt;
    private Swash swash;
    /** Lage des Rechtecks: Wasserlinie (UTM), Richtung landwärts, Ruhewasser, Neigung, Auflauf (m), landwärts (m). */
    private double pOst = Double.NaN, pNord, xOst, xNord, bezug, neigung, rSchaetzung, land;
    private double setzOst = Double.NaN, setzNord, setzRichtung, setzZeit;
    private double tVor = Double.NaN, vSchaetzung, drehung, landAb;
    private boolean abP, kroneHinter;
    /** Seit 0.3: landwärts nach der Schätzung (2 R / tan β, 8–48 m); Krone (m quer ab P, m NHN; NaN: keine). */
    public volatile double landLinie, kroneX = Double.NaN, kroneZ = Double.NaN;
    /** Seegang aus den Netzen 30 m seewärts der Wasserlinie beim Setzen: Hs (m), Tp (s). */
    public volatile double hs, tp;
    private int linie;
    private int slot;
    private volatile double zeitraffer = Double.NaN;
    /** Zahl der Bilder, in denen der Löser rechnete; Schritte; Bilder mit zu wenig Schritten; Neu gesetzt. */
    public long bilder, schritte, verpasst, gesetzt;
    /** Was das letzte Setzen fand (Auskunft). */
    public volatile String bericht = "noch nicht gesetzt";
    /** Seit 0.8 (S6c-3): Boden je Ort; null: überall Sand d50 0,2 mm wie bis 0.7 (A8). Gilt ab dem nächsten Setzen. */
    public volatile Bodenquelle boden;
    /** Seit 0.8: der Boden des letzten Setzens (Anteile der Klassen ab SWL − 1 m, Auskunft). */
    public volatile String bodenBericht = "";

    public Auflauf(Geraet g, Konto konto, Feld gelaende, Feld wasser) {
        this.g = g;
        this.konto = konto;
        this.gelaende = gelaende;
        this.wasser = wasser;
    }

    /** Der Löser des Rechtecks (null: keiner gesetzt); die Karte. Für Prüfungen. */
    public Swash swash() { return swash; }

    public SwashKarte karte() { return karte; }

    public Randwelle randwelle() { return randwelle; }

    public boolean aktiv() { return swash != null && karte != null; }

    /** Seit 0.11: ist das Rechteck in der Form für das Watt gesetzt (Flutkante)? */
    public boolean watt() { return watt && aktiv(); }

    /** Seit 0.11: Zelle des jetzigen Rechtecks (m). */
    public double dx() { return dx; }

    /** Lage: {Wasserlinie Ost, Nord, Richtung landwärts Ost, Nord, Ruhewasser, tan β, Auflauf geschätzt, landwärts}. */
    public double[] lage() { return new double[] {pOst, pNord, xOst, xNord, bezug, neigung, rSchaetzung, land}; }

    /**
     * Vor dem Bild (Faden der Bildschleife): Zeitraffer (gewollt; NaN live), Anlass zum Setzen. Gibt true, wenn der Löser
     * in diesem Bild rechnen darf.
     */
    public synchronized boolean vorBild(Blick b, double t, double raffer, List<Seegangsnetz> netze) {
        zeitraffer = raffer;
        if (!an) return false;
        if (!(Math.abs(raffer - 1) < 1e-9)) { tVor = Double.NaN; return false; }   // SF63 A: nur bei Zeitraffer 1
        boolean neu = Double.isNaN(setzOst) || Math.hypot(b.ost - setzOst, b.nord - setzNord) > WEG_M
                || Math.abs(Math.IEEEremainder(b.richtung - setzRichtung, 360)) > DREHUNG_GRAD
                || swash == null && Math.abs(t - setzZeit) > NEU_VERSUCH_S
                || swash != null && !watt && Math.abs(wasserstand() - bezug) > NEU_PEGEL
                || swash != null && watt && frontRaus();                           // seit 0.11: Flutkante
        if (neu)
            try {
                setze(b, t, netze);
            } catch (RuntimeException e) {                                       // die Bildschleife läuft weiter, der Auflauf ruht
                aus("Fehler beim Setzen: " + e);
                System.err.println("Auflauf: " + bericht);
                e.printStackTrace();
            }
        return aktiv();
    }

    /** Stockdon u. a. (2006), R2 % aus H0, Tp, tan β (wie {@code surf.Swash.stockdon}; dissipativ unter ξ 0,3). */
    public static double stockdon(double h0, double tp, double beta) {
        if (!(h0 > 0) || !(tp > 0)) return 0;
        double l0 = 9.81 * tp * tp / (2 * Math.PI), hl = Math.sqrt(h0 * l0);
        if (beta / Math.sqrt(h0 / l0) < 0.3) return 0.043 * hl;
        double setup = 0.35 * beta * hl, s = Math.sqrt(h0 * l0 * (0.563 * beta * beta + 0.004));
        return 1.1 * (setup + s / 2);
    }

    /** Setzt das Rechteck für den Blick b; false (und der Löser ruht), wenn keine Wasserlinie oder zu flach. */
    public synchronized boolean setze(Blick b, double t, List<Seegangsnetz> netze) {
        setzOst = b.ost; setzNord = b.nord; setzRichtung = b.richtung; setzZeit = t;
        tVor = Double.NaN;
        watt = false;
        dx = DX;
        double r = Math.toRadians(b.richtung), ex = Math.sin(r), ey = Math.cos(r);
        double sP = Double.NaN;
        Boolean vor = null;
        for (double s = SUCHE_VON; s <= SUCHE_BIS; s += SUCHE_SCHRITT) {
            double o = b.ost + s * ex, n = b.nord + s * ey;
            double h = gelaende.wert(o, n), w = wasser.wert(o, n);
            if (Double.isNaN(h)) continue;
            boolean nass = !Double.isNaN(w) && w - h > 0.01;
            if (vor != null && nass != vor) { sP = s - 0.5 * SUCHE_SCHRITT; break; }
            vor = nass;
        }
        if (Double.isNaN(sP)) { aus("keine Wasserlinie im Blick zwischen " + SUCHE_VON + " und " + SUCHE_BIS + " m"); return false; }
        pOst = b.ost + sP * ex;
        pNord = b.nord + sP * ey;
        // Richtung landwärts aus dem Gefälle über ± 10 m; ohne Gefälle die Richtung vom Nassen zum Trockenen längs des Blicks
        double gx = (gelaende.wert(pOst + 10, pNord) - gelaende.wert(pOst - 10, pNord)) / 20, gy = (gelaende.wert(pOst, pNord + 10) - gelaende.wert(pOst, pNord - 10)) / 20;
        double gl = Math.hypot(gx, gy);
        if (gl > 1e-4 && Double.isFinite(gl)) { xOst = gx / gl; xNord = gy / gl; neigung = gl; }
        else { xOst = Boolean.TRUE.equals(vor) ? ex : -ex; xNord = Boolean.TRUE.equals(vor) ? ey : -ey; neigung = 1e-3; }
        // Ruhewasser am Nassen neben der Linie
        double wSee = Double.NaN;
        for (double d = 2; d <= 20 && Double.isNaN(wSee); d += 2) wSee = wasser.wert(pOst - d * xOst, pNord - d * xNord);
        if (Double.isNaN(wSee)) { aus("kein Spiegel seewärts der Wasserlinie"); return false; }
        bezug = wSee;
        final double ruhe = wSee;
        // Richtung und Neigung aus der Wasserlinie über die Länge des Rechtecks: Ausgleichsgerade durch die Linie
        // Gelände = Ruhewasser an 17 Stellen über die Länge (seit 0.2 alle 8 m; je Stelle längs der vorläufigen Richtung ± 40 m); das
        // Gefälle am Ort allein dreht das Rechteck nach jeder Delle (Container: 8° schief, Wasserlinie ± 12 m quer)
        drehung = 0;
        linie = 0;
        landAb = 0;
        double[] ly = new double[2 * LINIE_STELLEN + 1], lx = new double[2 * LINIE_STELLEN + 1];
        {
            double yo = -xNord, yn = xOst, sy = 0, sx = 0, syy = 0, sxy = 0, sb = 0;
            int nb = 0;
            for (int q = -LINIE_STELLEN; q <= LINIE_STELLEN; q++) {
                double y = q * 0.5 * NY * DX / LINIE_STELLEN, so = pOst + y * yo, sn = pNord + y * yn;
                double hv = gelaende.wert(so - LINIE_SUCHE * xOst, sn - LINIE_SUCHE * xNord), xc = Double.NaN;
                for (double x = -LINIE_SUCHE + 0.5; x <= LINIE_SUCHE && Double.isNaN(xc); x += 0.5) {
                    double h = gelaende.wert(so + x * xOst, sn + x * xNord);
                    if (!Double.isNaN(hv) && !Double.isNaN(h) && hv < ruhe && h >= ruhe) xc = x - 0.5 * (h - ruhe) / (h - hv);
                    hv = h;
                }
                if (Double.isNaN(xc)) continue;
                ly[linie] = y;
                lx[linie] = xc;
                linie++;
                sy += y; sx += xc; syy += y * y; sxy += y * xc;
                double ha = gelaende.wert(so + (xc + 5) * xOst, sn + (xc + 5) * xNord), hb = gelaende.wert(so + (xc - 5) * xOst, sn + (xc - 5) * xNord);
                if (!Double.isNaN(ha) && !Double.isNaN(hb)) { sb += (ha - hb) / 10; nb++; }
            }
            double nen = linie * syy - sy * sy;
            if (linie >= 5 && nen > 0) {
                double bb = (linie * sxy - sy * sx) / nen, aa = (sx - bb * sy) / linie, l = Math.hypot(1, bb);
                pOst += aa * xOst;
                pNord += aa * xNord;
                double nxo = (xOst - bb * yo) / l, nxn = (xNord - bb * yn) / l;
                xOst = nxo;
                xNord = nxn;
                drehung = Math.toDegrees(Math.atan(bb));
                if (nb > 0 && sb / nb > 1e-3) neigung = sb / nb;
                // SF67 A: die landwärtigste gefundene Stelle der Linie, senkrecht zur Ausgleichsgeraden (m, landwärts +)
                for (int q = 0; q < linie; q++) landAb = Math.max(landAb, (lx[q] - aa - bb * ly[q]) / l);
            }
        }
        double[] sw = netze == null || netze.isEmpty() ? null : Seegangsoptik.werte(netze, t, pOst - 30 * xOst, pNord - 30 * xNord);
        double hs = sw != null && sw[0] > 0 ? sw[0] : 0.5, tp = sw != null && sw[1] > 0 ? sw[1] : 5;
        rSchaetzung = Math.max(stockdon(hs, tp, neigung), Brandung.ru2(hs, tp, neigung, 1));
        this.hs = hs;
        this.tp = tp;
        double landLinie = Math.max(LAND_MIN, Math.min(LAND_MAX, AUFLAUF_FAKTOR * rSchaetzung / Math.max(neigung, 0.01)));
        double yOst = -xNord, yNord = xOst, laengs = NY * DX, quer = NX * DX;
        // SF67 A: landwärts ab der landwärtigsten gefundenen Stelle der Linie; ist der seeseitige Rand dann zu flach, ab P
        land = Math.min(quer - SEE_MIN, landAb + landLinie);
        double tiefeVor = randTiefe(ruhe, land);
        abP = false;
        if (tiefeVor < TIEFE_MIN && landAb > 0) {
            double ab = randTiefe(ruhe, Math.min(quer - SEE_MIN, landLinie));
            if (ab >= TIEFE_MIN) { land = Math.min(quer - SEE_MIN, landLinie); tiefeVor = ab; abP = true; }
        }
        String wattGrund = "";
        boolean flach = flutkante && neigung < NEIGUNG_WATT;
        if (!(tiefeVor >= TIEFE_MIN) || flach) {
            String grund = !(tiefeVor >= TIEFE_MIN)
                    ? String.format(Locale.GERMAN, "seeseitiger Rand (%.0f m seewärts der Wasserlinie, tan β %.3f) im Mittel %.2f m tief (mindestens %.1f m)", quer - land, neigung, tiefeVor, TIEFE_MIN)
                    : String.format(Locale.GERMAN, "tan β %.4f flacher als 1:%.0f", neigung, 1 / NEIGUNG_WATT);
            // seit 0.11 (S6d-2): die Form für das Watt – 1 m Zellen, seewärts so wenig wie möglich, bis der Rand tief genug ist
            double querW = NX * DX_WATT, seeW = Double.NaN, tW = 0;
            // seit L4x: am flachen Watt ist die Wasserlinie über 512 m keine Gerade (L4x: P bei 16 m, im Median der Zeilen 215 m
            // landwärts). P rückt quer auf den Median der Wasserlinie über die ganze Länge (33 Zeilen, ± 256 m quer)
            double verschoben = 0;
            if (flutkante) {
                double yo = -xNord, yn = xOst;
                double[] xw = new double[33];
                int nw = 0;
                for (int q = 0; q <= 32; q++) {
                    double y = (q / 32.0 - 0.5) * NY * DX_WATT, so = pOst + y * yo, sn = pNord + y * yn;
                    double hv = gelaende.wert(so - 256 * xOst, sn - 256 * xNord);
                    for (double x = -255; x <= 256; x += 1) {
                        double h = gelaende.wert(so + x * xOst, sn + x * xNord);
                        if (!Double.isNaN(hv) && !Double.isNaN(h) && hv < ruhe && h >= ruhe) { xw[nw++] = x - 0.5; break; }
                        hv = h;
                    }
                }
                if (nw >= 5) {
                    double[] m = java.util.Arrays.copyOf(xw, nw);
                    java.util.Arrays.sort(m);
                    verschoben = m[nw / 2];
                    pOst += verschoben * xOst;
                    pNord += verschoben * xNord;
                }
            }
            if (flutkante)
                for (double sq = SEE_MIN_WATT; sq <= querW - LAND_MIN_WATT + 1e-9; sq += SEE_SCHRITT_WATT) {
                    tW = randTiefe(ruhe, querW - sq, DX_WATT);
                    if (tW >= TIEFE_MIN_WATT) { seeW = sq; break; }
                }
            if (Double.isNaN(seeW)) {
                aus(grund + (flutkante ? String.format(Locale.GERMAN, "; Watt (%.0f m Zellen): auch %.0f m seewärts im Mittel nur %.2f m tief (mindestens %.1f m)", DX_WATT, querW - LAND_MIN_WATT, tW, TIEFE_MIN_WATT) : ""));
                return false;
            }
            watt = true;
            dx = DX_WATT;
            laengs = NY * dx;
            quer = NX * dx;
            land = quer - seeW;
            tiefeVor = tW;
            abP = false;
            wattGrund = String.format(Locale.GERMAN, "Form für das Watt (Flutkante, %.0f m Zellen, %.0f × %.0f m), weil %s; P quer %+.0f m auf den Median der Wasserlinie über die Länge; ", dx, quer, laengs, grund, verschoben);
        }
        this.landLinie = landLinie;
        // SF70 A: liegt eine Krone (höchster Grund mit Abfall dahinter) innerhalb von landAb + LAND_MAX landwärts, reicht das
        // Rechteck mindestens KRONE_HINTER m hinter sie (die Landseite lässt das Wasser abfließen: Überlauf)
        kroneX = Double.NaN;
        kroneZ = Double.NaN;
        double bis = watt ? -1 : Math.min(quer - SEE_MIN - KRONE_HINTER, landAb + LAND_MAX);   // seit 0.11: am Watt keine Krone
        for (int q = -LINIE_STELLEN; q <= LINIE_STELLEN && !watt; q++) {
            double y = q * 0.5 * laengs / LINIE_STELLEN, so = pOst + y * yOst, sn = pNord + y * yNord;
            double zMax = Double.NEGATIVE_INFINITY, xMax = Double.NaN, zMin = Double.POSITIVE_INFINITY;
            for (double x = 0; x <= bis; x += 0.5) {
                double h = gelaende.wert(so + x * xOst, sn + x * xNord);
                if (Double.isNaN(h)) continue;
                if (h > zMax) { zMax = h; xMax = x; zMin = h; } else zMin = Math.min(zMin, h);
            }
            if (Double.isNaN(xMax) || !(zMax > ruhe) || zMax - zMin < KRONE_ABFALL) continue;   // keine Krone: steigt bis zum Ende
            if (Double.isNaN(kroneX) || xMax > kroneX) { kroneX = xMax; kroneZ = zMax; }
        }
        kroneHinter = false;
        if (!Double.isNaN(kroneX) && kroneX + KRONE_HINTER > land) {
            double neu = Math.min(quer - SEE_MIN, kroneX + KRONE_HINTER), t2 = randTiefe(ruhe, neu, dx);
            if (t2 >= TIEFE_MIN) { land = neu; tiefeVor = t2; kroneHinter = true; }
        }
        double o0 = pOst - (quer - land) * xOst - 0.5 * laengs * yOst, n0 = pNord - (quer - land) * xNord - 0.5 * laengs * yNord;
        Swash s = new Swash(NX, NY, dx, o0, n0, Math.atan2(xNord, xOst));
        final double dxF = dx;
        final Bodenquelle bq = boden;                                              // seit 0.8 (S6c-3): Boden aus den Klassen
        s.grund((x, y) -> gelaende.wert(s.weltX(x, y), s.weltY(x, y)))
                .rauheit((x, y) -> bq != null ? bq.werte(s.weltX(x, y), s.weltY(x, y))[0] : Boden.manningSand(0.2e-3))
                .durchlaessigkeit((x, y) -> bq != null ? bq.werte(s.weltX(x, y), s.weltY(x, y))[1] : Boden.durchlaessigkeit(0.2e-3))
                .grundwasser((x, y) -> ruhe);
        Grundaenderung ga = grundAendern;                                          // seit 0.9 (SF94 A): nur in Prüfungen
        if (ga != null) ga.aendere(s, ruhe);
        // seit 0.4 (Datei 73, F3): alle vier Seiten mit einem Außenzustand, den die Randwelle je Randzelle schreibt: wo innen
        // die See ist (mit dem seeseitigen Rand verbunden), an W die Synthese, an S und N die Zelle innen (F6), an E ruhendes
        // Wasser am Spiegel; wo
        // innen Land ist, ein trockenes Außen (Wasser fließt nur hinaus). Vorher ließen offene Seiten (Kopie der Zelle innen)
        // Wasser ohne Grenze hinein: in L4f/D flutete so über N das Land hinter dem Deich und lief über E ab (kein Überlauf).
        Swash.Antrieb platzhalter = (tt, x, y, aus) -> { aus[0] = ruhe; aus[1] = 0; aus[2] = 0; };   // nur für die Lage der Plätze
        for (int seite = 0; seite < 4; seite++) s.antrieb(seite, platzhalter);
        // F4: in Ruhe nur gefüllt, was mit der See verbunden ist; Senken hinter dem Deich bleiben trocken (vorher bis zum Spiegel gefüllt)
        boolean[] see = seeVerbunden(s, ruhe);
        s.fuellen(ruhe, new Swash.Anfang() {
            @Override public double stand(double x, double y) {
                int i = Math.max(0, Math.min(NX - 1, (int) Math.floor(x / dxF))), j = Math.max(0, Math.min(NY - 1, (int) Math.floor(y / dxF)));
                return see[j * NX + i] ? ruhe : ruhe - 1e3;
            }
        });
        seeZellen = see;
        double tiefe = 0;
        int nn = 0;
        for (int j = 0; j < NY; j++) { double h = s.b[s.id(0, j)]; if (!Double.isNaN(h)) { tiefe += Math.max(0, ruhe - h); nn++; } }
        tiefe = nn > 0 ? tiefe / nn : 0;
        double tMin = watt ? TIEFE_MIN_WATT : TIEFE_MIN;
        if (tiefe < tMin) { aus(String.format(Locale.GERMAN, "%sseeseitiger Rand im Mittel %.2f m tief (mindestens %.1f m)", wattGrund, tiefe, tMin)); return false; }
        // Wasserlinie in Ruhe je Zeile (erste trockene Zelle von der See), relativ zur Linie bei P (Auskunft)
        double wlMin = Double.POSITIVE_INFINITY, wlMax = Double.NEGATIVE_INFINITY;
        int nassRand = 0;
        for (int j = 0; j < NY; j++) {
            int i = 0;
            while (i < NX && !(s.b[s.id(i, j)] >= ruhe) && !Double.isNaN(s.b[s.id(i, j)])) i++;
            double d = s.x(i) - (quer - land);
            wlMin = Math.min(wlMin, d);
            wlMax = Math.max(wlMax, d);
            if (!(s.b[s.id(0, j)] >= ruhe) && !Double.isNaN(s.b[s.id(0, j)])) nassRand++;
        }
        wasserlinie = new double[] {wlMin, wlMax, nassRand / (double) NY};
        g.ruhe();                                                                 // die vorigen Bilder lesen die Puffer vielleicht noch
        // seit 0.11: je Form eine Karte (wechsle verlangt dieselbe Zelle)
        if (watt) {
            if (karteWatt == null) karteWatt = new SwashKarte(g, konto, s, 2, false);
            else karteWatt.wechsle(s);
            karte = karteWatt;
        } else {
            if (karteStrand == null) karteStrand = new SwashKarte(g, konto, s, 2, false);
            else karteStrand.wechsle(s);
            karte = karteStrand;
        }
        bildGilt = false;
        karte.hoch();
        swash = s;
        if (randwelle == null) randwelle = new Randwelle(g, konto, 1024, ZIELE);
        // Punkte der Synthese: W über die ganze Länge (0 … npW − 1, wie bisher), dann S und N über ihren Seeteil (seit 0.4)
        int npW = (int) Math.ceil(NY * dx / PUNKT_ABSTAND) + 1;
        int seeS = -1, seeN = -1;
        for (int i = 0; i < NX; i++) { if (see[i]) seeS = i; if (see[(NY - 1) * NX + i]) seeN = i; }
        // F5 (Datei 74): an S und N keine Punkte mehr; dort ruhendes Wasser am Spiegel. Die Synthese reichte an S und N bis an
        // die Uferlinie, wo ihre gebundene lange Welle nicht gilt (A7): in L4g/L4h Tempo bis 22–41 m/s in den Zeilen 0 und 511
        int npS = 0, npN = 0;
        double[] pw = new double[2 * (npW + npS + npN)];
        double xr = -0.5 * dx;                                                     // Mitte der ersten Randzelle
        for (int p = 0; p < npW; p++) { double y = p * PUNKT_ABSTAND; pw[2 * p] = s.weltX(xr, y); pw[2 * p + 1] = s.weltY(xr, y); }
        for (int p = 0; p < npS; p++) { double x = p * PUNKT_ABSTAND; pw[2 * (npW + p)] = s.weltX(x, xr); pw[2 * (npW + p) + 1] = s.weltY(x, xr); }
        for (int p = 0; p < npN; p++) { double x = p * PUNKT_ABSTAND, y = NY * dx - xr; pw[2 * (npW + npS + p)] = s.weltX(x, y); pw[2 * (npW + npS + p) + 1] = s.weltY(x, y); }
        randwelle.punkte(pw);
        // seit 0.7 (S6c-1b, Seegang Werkbuch 78, SF85 A): Richtung im Löser erhalten, Bezug in der Mitte der Linie der Punkte,
        // Tiefe des Rands im Mittel (Randwelle.bezug; -Dseegang.randrichtung=aus: wie bis 0.6)
        randwelle.bezug(s.weltX(xr, 0.5 * NY * dx), s.weltY(xr, 0.5 * NY * dx), tiefe);
        float[] ziele = new float[8 * ZIELE];
        int z = 0;
        int[] art = new int[4];                                                    // Ziele: Welle, trocken, Ruhe, Kopie
        for (int seite = 0; seite < 4; seite++) {
            boolean xs = seite == Swash.W || seite == Swash.E;
            int m = xs ? NY : NX;
            double nO, nN, tO, tN;                                                 // ins Gitter, längs (+y an W/E, +x an S/N)
            if (seite == Swash.W) { nO = xOst; nN = xNord; tO = yOst; tN = yNord; }
            else if (seite == Swash.E) { nO = -xOst; nN = -xNord; tO = yOst; tN = yNord; }
            else if (seite == Swash.S) { nO = yOst; nN = yNord; tO = xOst; tN = xNord; }
            else { nO = -yOst; nN = -yNord; tO = xOst; tN = xNord; }
            int p0 = seite == Swash.W ? 0 : seite == Swash.S ? npW : seite == Swash.N ? npW + npS : -1, pn = seite == Swash.W ? npW : seite == Swash.S ? npS : seite == Swash.N ? npN : 0;
            for (int k = -Swash.G; k < m + Swash.G; k++)
                for (int l = 1; l <= Swash.G; l++) {
                    int kc = Math.max(0, Math.min(m - 1, k));
                    int ai = seite == Swash.W ? 0 : seite == Swash.E ? NX - 1 : kc, aj = seite == Swash.S ? 0 : seite == Swash.N ? NY - 1 : kc;
                    boolean seeInnen = see[aj * NX + ai];
                    // Land: trocken; See: Welle, wo es Punkte gibt (W), an S und N die Zelle innen (seit 0.6, F6), an E Ruhe.
                    // F6 (Datei 74): Ruhe am Spiegel an S und N (F5) ist ein See daneben, in den der Stau abläuft (Container:
                    // viermal so viel Verlust wie mit der Zelle innen). Die Zelle innen gleicht einer langen Küste. Stau und
                    // Überlauf aus L4g/L4h kamen aber nicht wieder (L4j): Sie stammten aus der Synthese an S und N (A7).
                    int a = !seeInnen ? 1 : pn > 0 ? 0 : seite == Swash.S || seite == Swash.N ? 3 : 2;
                    // seit 0.11 (Flutkante): am Watt W überall die Welle (trocken gibt sie kein Wasser), S und N überall die Zelle innen
                    if (watt && seite != Swash.E) a = seite == Swash.W ? 0 : 3;
                    art[a]++;
                    double u = pn > 0 ? Math.max(0, Math.min(pn - 1, (xs ? s.y(k) : s.x(k)) / PUNKT_ABSTAND)) : 0;
                    ziele[8 * z] = karte.platz(seite, k, l);
                    ziele[8 * z + 1] = (float) (Math.max(0, p0) + u);
                    ziele[8 * z + 2] = (float) s.b[s.id(ai, aj)];
                    ziele[8 * z + 3] = a;
                    ziele[8 * z + 4] = (float) nO; ziele[8 * z + 5] = (float) nN;
                    ziele[8 * z + 6] = (float) tO; ziele[8 * z + 7] = (float) tN;
                    z++;
                }
        }
        randwelle.ziele(java.util.Arrays.copyOf(ziele, 8 * z));
        int verbunden = 0, abgetrennt = 0;
        for (int j = 0; j < NY; j++) for (int i = 0; i < NX; i++) { double bb = s.b[s.id(i, j)]; if (see[j * NX + i]) verbunden++; else if (bb < ruhe) abgetrennt++; }
        aussen = String.format(Locale.GERMAN, "Außen: %d Randzellen Welle (Punkte W %d, S %d, N %d), %d trocken, %d Ruhe, %d Zelle innen; in Ruhe mit der See verbunden %d Zellen, unter dem Spiegel ohne Verbindung (trocken gelassen) %d",
                art[0], npW, npS, npN, art[1], art[2], art[3], verbunden, abgetrennt);
        vSchaetzung = Math.sqrt(9.81 * Math.max(0.1, tiefe + hs)) + 2;
        bodenBericht = bodenBericht(s, bq, ruhe);
        gesetzt++;
        bericht = wattGrund + String.format(Locale.GERMAN, "Wasserlinie %.0f m vor dem Auge bei (%.0f, %.0f), landwärts %.0f°, tan β %.3f, Ruhewasser %.2f m NHN; Hs %.2f m, Tp %.1f s, Auflauf geschätzt %.2f m; landwärts %.0f m (%s), seewärts %.0f m, Rand im Mittel %.2f m tief; Wasserlinie längs des Rechtecks von %+.0f bis %+.0f m quer, seeseitiger Rand in %.0f %% der Zeilen nass; Richtung aus der Wasserlinie an %d von %d Stellen, %+.1f° gegen das Gefälle am Ort; %s",
                sP, pOst, pNord, (Math.toDegrees(Math.atan2(xOst, xNord)) + 360) % 360, neigung, bezug, hs, tp, rSchaetzung, land,
                abP ? String.format(Locale.GERMAN, "ab P; ab der landwärtigsten Stelle der Linie, %.0f m weiter, wäre der Rand zu flach", landAb)
                        : landAb > 0 ? String.format(Locale.GERMAN, "davon %.0f m bis zur landwärtigsten Stelle der Linie", landAb) : "ab P",
                quer - land, tiefe, wasserlinie[0], wasserlinie[1], 100 * wasserlinie[2], linie, 2 * LINIE_STELLEN + 1, drehung,
                Double.isNaN(kroneX) ? "keine Krone im Rechteck" : String.format(Locale.GERMAN, "Krone %.2f m NHN (%.2f m über dem Ruhewasser) %.0f m landwärts von P%s", kroneZ, kroneZ - bezug, kroneX,
                        kroneHinter ? ", Rechteck bis 5 m dahinter" : kroneX + KRONE_HINTER <= land ? "" : ", 5 m dahinter wäre der Rand zu flach"))
                + "; " + aussen + "; " + bodenBericht;
        return true;
    }

    /** Vergisst Rechteck und Lage (neues Szenario); das nächste {@link #vorBild} setzt neu. */
    public synchronized void vergiss() {
        setzOst = Double.NaN;
        tVor = Double.NaN;
        aus("neues Szenario");
    }

    /** Mittlere Tiefe (m) am seeseitigen Rand an 33 Stellen für so viel landwärts (m ab P); billig, vor dem Gitter. */
    /** Seit 0.8: Anteile der Klassen des Bodens in den Zellen ab SWL − 1 m (Auflauf und Land), absteigend. */
    private static String bodenBericht(Swash s, Bodenquelle bq, double ruhe) {
        if (bq == null) return "Boden überall Sand d50 0,2 mm (ohne Bodenkarte)";
        int[] z = new int[32];
        int alle = 0;
        for (int j = 0; j < NY; j++)
            for (int i = 0; i < NX; i++) {
                double bb = s.b[s.id(i, j)];
                if (Double.isNaN(bb) || bb < ruhe - 1) continue;
                double[] w = bq.werte(s.weltX(s.x(i), s.y(j)), s.weltY(s.x(i), s.y(j)));
                z[Math.max(0, Math.min(31, (int) w[2]))]++;
                alle++;
            }
        if (alle == 0) return "Boden: keine Zelle ab SWL − 1 m";
        StringBuilder b = new StringBuilder("Boden ab SWL − 1 m:");
        boolean[] fertig = new boolean[32];
        for (int r = 0; r < 32; r++) {
            int best = -1;
            for (int k = 0; k < 32; k++) if (!fertig[k] && z[k] > 0 && (best < 0 || z[k] > z[best])) best = k;
            if (best < 0) break;
            fertig[best] = true;
            b.append(r > 0 ? "," : "").append(String.format(Locale.GERMAN, " %s %.0f %%", bq.name(best), 100.0 * z[best] / alle));
        }
        return b.toString();
    }

    private double randTiefe(double ruhe, double landM) { return randTiefe(ruhe, landM, DX); }

    /** Seit 0.11: dasselbe für ein Rechteck mit der Zelle d (m). */
    private double randTiefe(double ruhe, double landM, double d) {
        double quer = NX * d, laengs = NY * d, yOst = -xNord, yNord = xOst;
        double o0 = pOst - (quer - landM) * xOst - 0.5 * laengs * yOst, n0 = pNord - (quer - landM) * xNord - 0.5 * laengs * yNord;
        double t = 0;
        int n = 0;
        for (int q = 0; q <= 32; q++) {
            double h = gelaende.wert(o0 + q / 32.0 * laengs * yOst, n0 + q / 32.0 * laengs * yNord);
            if (!Double.isNaN(h)) { t += Math.max(0, ruhe - h); n++; }
        }
        return n > 0 ? t / n : 0;
    }

    /** Wasserlinie in Ruhe längs des Rechtecks: am weitesten seewärts, landwärts (m quer, relativ zur Linie bei P); Anteil der Zeilen mit nassem seeseitigem Rand. */
    public volatile double[] wasserlinie;

    /** Seit 0.4 (F3): Zahl der Ziele der Randwelle, alle vier Seiten je zwei Lagen. */
    static final int ZIELE = 2 * (NY + 2 * Swash.G) * Swash.G + 2 * (NX + 2 * Swash.G) * Swash.G;
    /** Seit 0.4 (F4): je Zelle (j · NX + i), ob sie in Ruhe mit dem seeseitigen Rand verbunden unter dem Spiegel liegt; Außenzustand (Auskunft). */
    public volatile boolean[] seeZellen;
    public volatile String aussen = "";

    /**
     * Seit 0.4 (F4): die Zellen unter dem Ruhewasser, die über Nachbarn (4er) mit einer solchen Zelle am seeseitigen Rand
     * (i = 0) verbunden sind. Senken hinter einer Krone gehören nicht dazu.
     */
    static boolean[] seeVerbunden(Swash s, double ruhe) {
        boolean[] see = new boolean[NX * NY];
        int[] stapel = new int[NX * NY];
        int n = 0;
        for (int j = 0; j < NY; j++) { double bb = s.b[s.id(0, j)]; if (bb < ruhe) { see[j * NX] = true; stapel[n++] = j * NX; } }
        while (n > 0) {
            int c = stapel[--n], i = c % NX, j = c / NX;
            for (int d = 0; d < 4; d++) {
                int a = i + (d == 0 ? 1 : d == 1 ? -1 : 0), b = j + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (a < 0 || a >= NX || b < 0 || b >= NY || see[b * NX + a]) continue;
                double bb = s.b[s.id(a, b)];
                if (!(bb < ruhe)) continue;                                        // trocken oder Fels (NaN)
                see[b * NX + a] = true;
                stapel[n++] = b * NX + a;
            }
        }
        return see;
    }

    private void aus(String warum) {
        swash = null;
        bericht = "aus: " + warum;
    }

    /** Spiegel des Wassers am seeseitigen Rand (Mitte), m NHN; ohne Wert das Ruhewasser (seit 0.3 öffentlich, für Prüfungen). */
    public double wasserstand() {
        Swash s = swash;
        double w = wasser.wert(s.weltX(0, 0.5 * NY * s.dx), s.weltY(0, 0.5 * NY * s.dx));
        return Double.isNaN(w) ? bezug : w;
    }

    /**
     * Seit 0.11 (Flutkante): wo der Spiegel der Hydro den Grund des Rechtecks schneidet – je Zeile die erste Zelle von der
     * See her, deren Grund mindestens am Spiegel liegt (m quer von der seeseitigen Kante; nx · dx, wenn keine), Median über
     * {@code zeilen} gleich verteilte Zeilen. NaN ohne Rechteck.
     */
    public double frontHydro(int zeilen) {
        Swash s = swash;
        if (s == null) return Double.NaN;
        double ws = wasserstand();
        double[] x = new double[zeilen];
        for (int q = 0; q < zeilen; q++) {
            int j = (int) ((q + 0.5) * s.ny / zeilen), i = 0;
            while (i < s.nx && !(s.b[s.id(i, j)] >= ws) && !Double.isNaN(s.b[s.id(i, j)])) i++;
            x[q] = i * s.dx;
        }
        java.util.Arrays.sort(x);
        return x[zeilen / 2];
    }

    /** Seit 0.11 (Flutkante): hat die Front das Rechteck verlassen (s. Klasse)? */
    public boolean frontRaus() {
        Swash s = swash;
        if (s == null) return false;
        if (frontHydro(17) > s.nx * s.dx - FRONT_RAND) return true;
        double ws = wasserstand(), t = 0;
        int n = 0;
        for (int j = 0; j < s.ny; j += 8) { double bb = s.b[s.id(0, j)]; if (!Double.isNaN(bb)) { t += Math.max(0, ws - bb); n++; } }
        return n > 0 && t / n < 0.5 * TIEFE_MIN_WATT;
    }

    /** Je Bild nach der Clipmap (Seegangbild.Anhang): Antrieb und Schritte von der vorigen Zeit bis zu dieser. */
    @Override public synchronized void vorbereiten(Ablauf a, Bildziel z, SeegangKarte k, double zeit) {
        if (!an || !aktiv() || !(Math.abs(zeitraffer - 1) < 1e-9)) { ablageWeg(z); return; }
        try {
            bild(a, k, zeit);
            ablegen(a, z);
        } catch (RuntimeException e) {                                           // die Bildschleife läuft weiter, der Auflauf ist aus
            an = false;
            bericht = "aus nach Fehler im Bild: " + e;
            System.err.println("Auflauf: " + bericht);
            e.printStackTrace();
        }
    }

    private void bild(Ablauf a, SeegangKarte k, double zeit) {
        double ws = wasserstand();
        if (zeit == tVor) return;                                                   // seit 0.9: dasselbe Bild noch einmal (Standbild): kein Schritt
        if (Double.isNaN(tVor) || zeit <= tVor || zeit - tVor > SPRUNG_S) {          // Anfang: Stand der Ruhe, Antrieb in den Stand
            g.ruhe();
            karte.hoch();
            slot = 0;
            randwelle.rechne(a, k, 0, 0, 1);
            randwelle.verteilen(a, karte.antriebPuffer(), slot * karte.platzJeSlot, 0, ws, bezug);
            tVor = zeit;
            bildGilt = false;
            return;
        }
        int s0 = slot, s1 = 1 - slot;
        randwelle.rechne(a, k, 0, 0, 1);
        randwelle.verteilen(a, karte.antriebPuffer(), s1 * karte.platzJeSlot, 0, ws, bezug);
        double dtB = zeit - tVor, v = Math.max(karte.tempoLesen(), 0.5 * vSchaetzung);
        double dtMax = swash.cfl * swash.dx / Math.max(swash.vMin, SICHERHEIT * v);
        int n = (int) Math.ceil(dtB / dtMax);
        double h = dtB / n;
        if (n > SCHRITTE_MAX) { n = SCHRITTE_MAX; h = dtMax; verpasst++; }
        for (int q = 0; q < n; q++) {
            double ta = q * h;
            karte.bildImSchritt = bildAn && q == n - 1;                             // seit S6c-2b: das Bild nur im letzten Schritt
            karte.schritt(a, h, s0, s1, ta / dtB, s0, s1, (ta + h) / dtB);
        }
        karte.bildImSchritt = true;
        // seit S6c-2b (Seegang Werkbuch 83): das Tempo nur jedes TEMPO_JE-te Bild (L4p 0,057 ms je Bild); der Lauf liest es
        // ohnehin einige Bilder alt und nimmt es mit SICHERHEIT
        if (bilder % TEMPO_JE == 0) karte.tempo(a);
        schritte += n;
        bilder++;
        tVor = zeit;
        slot = s1;
    }

    /** Seit 0.9: das Bild des Rechtecks rechnen und im Bildziel ablegen (Gelände und Wasser lesen es); ohne Ziel nur rechnen. */
    private void ablegen(Ablauf a, Bildziel z) {
        if (!bildAn) { karte.bildNeu(); ablageWeg(z); return; }              // seit 0.10: beim Wiedereinschalten ganz neu füllen
        if (Double.isNaN(tVor)) return;
        karte.bild(a, KONTUR_BILD);
        bildGilt = true;
        if (z == null) return;
        Swash s = swash;
        double o0 = s.weltX(0, 0), n0 = s.weltY(0, 0);
        z.puffer.put(BILD, karte.bildPuffer());
        z.werte.put(BILD, new double[] {o0, n0, s.weltX(1, 0) - o0, s.weltY(1, 0) - n0, s.weltX(0, 1) - o0, s.weltY(0, 1) - n0, s.dx, s.nx, s.ny, s.bezug, karte.zeit(), KONTUR_BILD});
    }

    /** Seit 0.9: nimmt das Bild aus dem Bildziel (kein Rechteck oder kein Rechnen). */
    private void ablageWeg(Bildziel z) {
        if (z == null) return;
        z.puffer.remove(BILD);
        z.werte.remove(BILD);
    }

    /** Seit 0.9: ist das Bild der Karte für das jetzige Rechteck gerechnet (Prüfungen)? */
    public synchronized boolean bildGilt() { return bildGilt && aktiv(); }

    @Override public synchronized void close() {
        if (karteStrand != null) { karteStrand.close(); karteStrand = null; }
        if (karteWatt != null) { karteWatt.close(); karteWatt = null; }
        karte = null;
        if (randwelle != null) { randwelle.close(); randwelle = null; }
        swash = null;
    }
}
