package com.dan.render.air;

import com.dan.core.astro.Gestirne;
import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.raum.Blick;
import com.dan.core.raum.Utm;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Saeule;
import com.dan.core.szene.Scattering;
import com.dan.core.szene.Streuordnungen;
import com.dan.core.szene.Wasseralbedo;
import com.dan.core.szene.Wasseroptik;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Der Himmel (P6c, Werkbuch HalligHooge Teil A, 12.7; seit P7e, 12.8, F58 A): Strahlengang wie core.szene.Scattering
 * mit der ersten Streuung genau und ab der zweiten der Quelle J(h, μ, μ_s, φ) aus {@link Streuordnungen} (Rayleigh und
 * Mie getrennt, nach Bruneton in Streuordnungen gerechnet, statt Ψ nach Hillaire). Wenn sich die Luft ändert, rechnet
 * Java J (drei Kanäle und 15 Wellenlängen; auf der Platte unter {@code ~/.hallighooge/luft/} abgelegt) und lädt es hoch,
 * die Karte rechnet die Durchlässigkeit der drei Kanäle; je Bild rechnet sie die Himmelsansicht, die Sonne darin
 * spektral (fünf Gruppen zu drei Wellenlängen, nach linearem sRGB wie die Referenz), und legt alles mit den Werten im
 * Bildziel ab; beim Zeichnen
 * (als letzter Teil, nach Gelände und Wasser) füllt er, wo nichts gezeichnet ist, den Himmel mit Sonnen- und
 * Mondscheibe und legt über alles andere die Luftperspektive.
 * <p>
 * Sonne und Mond kommen aus {@code core.astro} (wahre Richtung, ohne Brechung, mit Parallaxe), in die Szene gedreht um
 * die Meridiankonvergenz von UTM; die Scheiben stehen mit der Strahlenbrechung der Normalatmosphäre (Sæmundsson) höher.
 * Seit P10d (F100 A) kommt auch das Licht aus der gehobenen Richtung – Streuung, Luftperspektive, Gelände, Wasser,
 * Schatten, Säule –, wie in der Referenz; Spiegelbild, Glitzern und Schatten gehören so zur sichtbaren Scheibe. Die
 * direkte Sonne und der Mond tragen je Bild ihre Farbe nach der Luft ({@link Lichtquellen#farbeNachLuft}, Werte 52 und 56).
 * Licht in lx und cd/m² ({@link Lichtquellen}, core.szene): Die Sonne bringt außerhalb der Luft 127 500 lx je Kanal in 1 AE. Sicht 30 km [G] bis atmos
 * sie rechnet, Bodenalbedo 0,23 (seit P7l: Wasser und Watt um Hooge, gemessen am Himmel der Referenz über der Szene; vorher
 * 0,08 wie in {@code sky}); ohne Wolken und ohne Kimmung (P10).
 * <p>
 * Abgelegt (Namen wie im Werkbuch): Bilder {@link #DURCH}, {@link #MEHR} (J der drei Kanäle, Aufbau wie
 * {@link Scattering#jBild}), {@link #ANSICHT}; Puffer {@link #WERTE}
 * (Aufbau {@code Himmelswerte} in {@code /com/dan/core/gpu/luft.glsl}); Zahlen {@link #SONNE}, {@link #MOND}.
 */
public final class Himmel implements Bildteil {
    public static final String VERSION = "0.2";
    public static final String DURCH = "Himmel/Durchlaessigkeit", MEHR = "Himmel/Mehrfachstreuung", ANSICHT = "Himmel/Ansicht", WERTE = "Himmel/Werte";
    /**
     * {Richtung x, y, z (Szene; seit P10d, F100 A, die gehobene, aus der das Licht kommt), Beleuchtungsstärke außerhalb der
     * Luft r, g, b (lx), scheinbare Höhe (Grad), Azimut (Grad), wahre Höhe (Grad)}; der Mond dazu den Phasenwinkel vor der
     * wahren Höhe.
     */
    public static final String SONNE = "Himmel/Sonne", MOND = "Himmel/Mond";
    public static final int TRANS_W = 256, TRANS_H = 64, ANSICHT_W = 256, ANSICHT_H = 128;
    /** Größe der Bilder von J (2 NPHI NMS × NMU NR) und Zahl der Gruppen der Himmelsansicht (je drei Wellenlängen). */
    public static final int J_W = 2 * Streuordnungen.NPHI * Streuordnungen.NMS, J_H = Streuordnungen.NMU * Streuordnungen.NR, GRUPPEN = Streuordnungen.N_SPEKTRAL / 3;
    /** Zahl der nm 360–830 in der Tafel nach sRGB ({@link Streuordnungen#spektralTafel}, wie SP_NM in ansicht.comp). */
    private static final int SP_NM = 471;
    /** Sonne, Mond und Nachthimmel: seit P7a (F52 A) in {@link Lichtquellen} (core.szene); hier gleichnamig für die Checks. */
    public static final double SONNE_LX = Lichtquellen.SONNE_LX, NACHT = Lichtquellen.NACHT;
    public static final double SONNE_MAG = Lichtquellen.SONNE_MAG, SONNE_RADIUS = Lichtquellen.SONNE_RADIUS, RAND_U = Lichtquellen.RAND_U;
    public static final double[] MONDFARBE = Lichtquellen.MONDFARBE;
    /** Seit P10d 60 (Farbe des Monds nach der Luft). */
    public static final int WERTE_FLOATS = 60;

    /** Sicht in km [G] und Bodenalbedo (seit P7l 0,23, siehe oben); Änderung rechnet die Tabellen neu. */
    public double sichtKm = 30, albedo = 0.23;
    /** Seit P10d (Messung d): Bodenalbedo über Wasser und über trockenem Land (Watt) [G]. */
    public static final double ALB_WASSER = 0.06, ALB_LAND = 0.23;
    /**
     * Seit P10d (Messung d): Anteil des Wassers am Boden der Szene (0 … 1), von der Anwendung je Bild gesetzt
     * ({@code lauf.Bild}). Damit gilt die Albedo ALB_WASSER + (ALB_LAND − ALB_WASSER) (1 − Anteil), und J wird auf der
     * Karte zwischen den Tabellen der beiden Albedos gemischt (neu, wenn sich die Albedo um mehr als {@link #MISCH_SCHRITT}
     * ändert), statt neu gerechnet. NaN (Vorgabe): {@link #albedo} gilt wie bisher.
     */
    public double wasserAnteil = Double.NaN;
    /**
     * Seit P10d (F105 A): das Wasser der Szene (null: über Wasser {@link #ALB_WASSER}) und der Wind in 10 m (m/s), von der
     * Anwendung je Bild gesetzt. Damit ist die Albedo über Wasser je Kanal die von {@link Wasseralbedo} (Fläche zur Höhe
     * der Sonne, bei Nacht des Monds, und zum Wind; Säule zum Schwebstoff), und J wird je Kanal (spektral je Wellenlänge)
     * gemischt, auch über die Tabelle des Lands hinaus (J ist linear in der Albedo).
     */
    public Wasseroptik wasserOptik;
    /**
     * Höchste Albedo des Wassers für J [G]: Bei tiefer Sonne und wenig Wind spiegelt das Wasser die Sonne so stark, dass
     * die gleichwertige Albedo bis 0,9 reicht; J ist aber nur zwischen 0,06 und 0,23 als linear gemessen, weit darüber wird
     * die Mischung ungenau (Bodenreflexe mehrfach).
     */
    public static final double ALB_MAX = 0.6;
    public double wind = 8;
    /** Änderung der Albedo (in einem Kanal), ab der J neu gemischt wird [G]. */
    public static final double MISCH_SCHRITT = 0.003;
    /** Schritte der Luftperspektive je Bildpunkt [G]. */
    public int schritte = 8;
    /** UTM-Zone des Blicks (Hooge: 32). */
    public int zone = 32;

    private final Geraet g;
    private final Kernel kDurch, kAnsicht, kHimmel, kMisch;
    /** Seit P10d: J für Wasser und Land, je drei Kanäle und fünf Gruppen (erst angelegt, wenn {@link #wasserAnteil} gesetzt ist). */
    private Bild[] jWasser, jLand;
    private final Konto konto;
    /** Albedo, mit der J zuletzt gemischt wurde (−1: noch nicht; seit F105 A das Mittel nach Y), je Kanal; Zahl der Mischungen (für Prüfungen). */
    private double gemischt = -1;
    private double[] gemischtRgb;
    /** Höhe von Sonne und Mond (Grad, scheinbar) im letzten Bild; Schwebstoff und Säule zuletzt. */
    private double hoeheSonne = 30, hoeheMond = -90, saeuleBei = Double.NaN;
    private double[] saeuleWert;
    public int mischungen;
    private boolean jNeu;
    private final Bild durch, mehr, ansicht;
    /** Spektral: Durchlässigkeit und J je Gruppe. */
    private final Bild[] spD = new Bild[GRUPPEN], spJ = new Bild[GRUPPEN];
    /** F62 A: je Gruppe ein Kernel und ihr Bild (drei Wellenlängen der Sonne je Einheit Bestrahlung). */
    private final Kernel[] kGruppe = new Kernel[GRUPPEN];
    private final Bild[] spL = new Bild[GRUPPEN];
    /**
     * F62 A: Die Himmelsansicht wird über fünf Bilder verteilt, je Bild eine Gruppe, und nach der fünften getauscht.
     * In einem Zug gerechnet wird beim ersten Mal, bei neuer Luft und bei einem Sprung (Richtung über 1e-2, rund 0,6°,
     * Stärke oder Höhe über 5 % [G]) – also bei jedem Standbild – oder immer, wenn der Schalter aus ist.
     */
    public boolean verteilt = true;
    private int gruppeDran;
    private final Puffer werte, spektrum;
    private Streuordnungen kanaele, spektral;
    private final Abtaster punkt;
    private final ByteBuffer daten = ByteBuffer.allocateDirect(WERTE_FLOATS * 4).order(ByteOrder.nativeOrder());
    private Atmosphere atm;
    private double gebautSicht = -1, gebautAlbedo = -1;
    private float[] letzte;
    /** Zahl der Bilder, in denen die Tabellen neu gerechnet wurden (für Prüfungen). */
    public int neuGerechnet;
    /** Zahl der Bilder, in denen die Himmelsansicht neu gerechnet wurde (für Prüfungen). */
    public int ansichtGerechnet;
    /** Werte, mit denen die Ansicht zuletzt gerechnet wurde. */
    private float[] ansichtWerte;

    public Himmel(Geraet g, Konto konto) {
        this.g = g;
        this.konto = konto;
        String p = "/com/dan/render/air/";
        kMisch = Kernel.lade(g, p + "jmisch.comp", 32, null, Kernel.Bindung.BILD, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR);
        kDurch = Kernel.lade(g, p + "durchlaessigkeit.comp", 0, null, Kernel.Bindung.BILD, Kernel.Bindung.PUFFER);
        Kernel.Bindung[] ba = new Kernel.Bindung[5 + GRUPPEN];
        ba[0] = Kernel.Bindung.BILD;
        ba[1] = ba[2] = Kernel.Bindung.TEXTUR;
        ba[3] = ba[4] = Kernel.Bindung.PUFFER;
        for (int i = 5; i < ba.length; i++) ba[i] = Kernel.Bindung.TEXTUR;
        kAnsicht = Kernel.lade(g, p + "ansicht.comp", 0, null, ba);
        kHimmel = Kernel.lade(g, p + "himmel.comp", 0, null, Kernel.Bindung.BILD, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR,
                Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER);
        durch = new Bild(g, konto, DURCH, TRANS_W, TRANS_H, Bild.RGBA32F);
        mehr = new Bild(g, konto, MEHR, J_W, J_H, Bild.RGBA16F);
        for (int i = 0; i < GRUPPEN; i++) {
            spD[i] = new Bild(g, konto, "Himmel/Durchlaessigkeit " + i, TRANS_W, TRANS_H, Bild.RGBA32F);
            spJ[i] = new Bild(g, konto, "Himmel/Mehrfachstreuung " + i, J_W, J_H, Bild.RGBA16F);
            spL[i] = new Bild(g, konto, "Himmel/Ansicht, Gruppe " + i, ANSICHT_W, ANSICHT_H, Bild.RGBA32F);
            kGruppe[i] = Kernel.lade(g, p + "gruppe" + i + ".comp", 0, null, Kernel.Bindung.BILD, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER,
                    Kernel.Bindung.PUFFER);
        }
        spektrum = Puffer.speicher(g, konto, "Himmel/Spektrum", 16L * (2 * GRUPPEN + SP_NM));
        ansicht = new Bild(g, konto, ANSICHT, ANSICHT_W, ANSICHT_H, Bild.RGBA32F);
        werte = Puffer.speicher(g, konto, WERTE, 4L * WERTE_FLOATS);
        punkt = new Abtaster(g, false, false);
    }

    @Override public String name() { return "Himmel"; }

    /** Gewichte nach Y (lineares sRGB) für eine Albedo aus dreien. */
    public static final double[] Y = {0.2126, 0.7152, 0.0722};

    /** Die Albedo, mit der der Himmel rechnet: {@link #albedo}, oder seit P10d aus {@link #wasserAnteil} (seit F105 A das Mittel der Kanäle nach Y). */
    public double albedoWirksam() {
        double[] a = albedoWirksamRgb();
        return Y[0] * a[0] + Y[1] * a[1] + Y[2] * a[2];
    }

    /** Seit F105 A: die Albedo je Kanal, Anteil des Wassers mal {@link #albedoWasser} plus Rest mal {@link #ALB_LAND}. */
    public double[] albedoWirksamRgb() {
        if (Double.isNaN(wasserAnteil)) return new double[] {albedo, albedo, albedo};
        double w = Math.max(0, Math.min(1, wasserAnteil));
        double[] aw = albedoWasser(), r = new double[3];
        for (int c = 0; c < 3; c++) r[c] = w * aw[c] + (1 - w) * ALB_LAND;
        return r;
    }

    /**
     * Seit F105 A: die Albedo des Wassers je Kanal ({@link Wasseralbedo}) zur Höhe der Sonne im letzten Bild (unter −6°
     * der Mond, wenn er höher steht) und zu {@link #wind}; ohne {@link #wasserOptik} {@link #ALB_WASSER}.
     */
    public double[] albedoWasser() {
        if (wasserOptik == null) return new double[] {ALB_WASSER, ALB_WASSER, ALB_WASSER};
        if (saeuleWert == null || saeuleBei != wasserOptik.schwebstoff) {
            saeuleWert = Wasseralbedo.saeule(Saeule.lade(), wasserOptik);
            saeuleBei = wasserOptik.schwebstoff;
        }
        double h = hoeheSonne > -6 || hoeheMond < hoeheSonne ? hoeheSonne : hoeheMond;
        double[] a = Wasseralbedo.lade().flaeche(h, Double.isNaN(wind) ? 8 : wind);
        for (int c = 0; c < 3; c++) a[c] = Math.min(ALB_MAX, a[c] + saeuleWert[c]);
        return a;
    }

    /** Seit F105 A: die Albedo je Kanal, mit der J zuletzt gemischt wurde (null: noch nicht oder ohne Anteil). */
    public double[] gemischtRgb() { return gemischtRgb == null ? null : gemischtRgb.clone(); }

    /**
     * Albedo bei der Wellenlänge λ (nm) aus der je Kanal: linear zwischen den Wellenlängen der Kanäle
     * ({@link Wasseroptik#LAMBDA}: 440, 550, 680 nm), außerhalb gehalten [G].
     */
    public static double albedoBei(double[] rgb, double lambda) {
        double[] l = Wasseroptik.LAMBDA;                                   // R, G, B: 680, 550, 440
        if (lambda <= l[2]) return rgb[2];
        if (lambda >= l[0]) return rgb[0];
        if (lambda <= l[1]) return rgb[2] + (lambda - l[2]) / (l[1] - l[2]) * (rgb[1] - rgb[2]);
        return rgb[1] + (lambda - l[1]) / (l[0] - l[1]) * (rgb[0] - rgb[1]);
    }

    /** Die Luft zu Sicht und Albedo (dieselbe, mit der die Tabellen gerechnet sind; mit Anteil die zuletzt gemischte). */
    public Atmosphere luft() {
        double a = Double.isNaN(wasserAnteil) ? albedo : gemischt >= 0 ? gemischt : albedoWirksam();
        if (atm == null || atm.visibilityKm != sichtKm || atm.groundAlbedo != a) atm = Atmosphere.forVisibility(sichtKm, a);
        return atm;
    }

    /** Die Werte des letzten Bilds (Aufbau wie {@code Himmelswerte}), für Prüfungen. */
    public float[] letzteWerte() { return letzte == null ? null : letzte.clone(); }

    public Bild durchlaessigkeit() { return durch; }

    /** J der drei Kanäle (seit P7e; bis P7d Ψ nach Hillaire). */
    public Bild mehrfachstreuung() { return mehr; }

    /** Die Streuordnungen der Luft (drei Kanäle und spektral), wie hochgeladen; null vor dem ersten Bild. */
    public Streuordnungen kanaele() { return kanaele; }

    public Streuordnungen spektral() { return spektral; }

    /**
     * Rechnet J (Java, beim ersten Mal je Luft auf der Platte abgelegt) und lädt es hoch: drei Kanäle nach {@link #MEHR},
     * spektral je Gruppe Durchlässigkeit und J, dazu Beiwerte und die Tafel nach sRGB je lx der Sonne. Wartet auf die Karte.
     */
    private void ladeJ() {
        Atmosphere at = luft();
        if (!Double.isNaN(wasserAnteil)) {
            // seit P10d: J für Wasser und Land als Quellen der Mischung (jmisch.comp); Durchlässigkeit hängt nicht an der Albedo
            Atmosphere aw = Atmosphere.forVisibility(sichtKm, ALB_WASSER), al = Atmosphere.forVisibility(sichtKm, ALB_LAND);
            Streuordnungen kw = Streuordnungen.kanaele(aw), sw = Streuordnungen.spektral(aw);
            kanaele = Streuordnungen.kanaele(al);
            spektral = Streuordnungen.spektral(al);
            if (jWasser == null) {
                jWasser = new Bild[1 + GRUPPEN];
                jLand = new Bild[1 + GRUPPEN];
                for (int i = 0; i <= GRUPPEN; i++) {
                    jWasser[i] = new Bild(g, konto, "Himmel/J Wasser " + i, J_W, J_H, Bild.RGBA16F);
                    jLand[i] = new Bild(g, konto, "Himmel/J Land " + i, J_W, J_H, Bild.RGBA16F);
                }
            }
            g.ruhe();
            jWasser[0].schreibe(Scattering.jBild(kw, 0));
            jLand[0].schreibe(Scattering.jBild(kanaele, 0));
            for (int i = 0; i < GRUPPEN; i++) {
                spD[i].schreibe(Scattering.durchBild(spektral, 3 * i));
                jWasser[1 + i].schreibe(Scattering.jBild(sw, 3 * i));
                jLand[1 + i].schreibe(Scattering.jBild(spektral, 3 * i));
            }
            gemischt = -1;
            gemischtRgb = null;
        } else {
            kanaele = Streuordnungen.kanaele(at);
            spektral = Streuordnungen.spektral(at);
            g.ruhe();
            mehr.schreibe(Scattering.jBild(kanaele, 0));
            for (int i = 0; i < GRUPPEN; i++) {
                spD[i].schreibe(Scattering.durchBild(spektral, 3 * i));
                spJ[i].schreibe(Scattering.jBild(spektral, 3 * i));
            }
        }
        float[] tafel = Streuordnungen.spektralTafel();
        if (tafel.length != 4 * SP_NM) throw new IllegalStateException("Himmel: Tafel nach sRGB hat " + tafel.length / 4 + " nm statt " + SP_NM);
        float[] f = new float[4 * (2 * GRUPPEN + SP_NM)];
        for (int i = 0; i < GRUPPEN; i++) for (int c = 0; c < 3; c++) {
            f[4 * i + c] = (float) spektral.rayleigh[3 * i + c];
            f[4 * (GRUPPEN + i) + c] = (float) spektral.ozon[3 * i + c];
        }
        for (int q = 0; q < SP_NM; q++) {
            f[4 * (2 * GRUPPEN + q)] = tafel[4 * q];
            for (int c = 1; c < 4; c++) f[4 * (2 * GRUPPEN + q) + c] = (float) (tafel[4 * q + c] / SONNE_LX);
        }
        spektrum.schreibe(f);
    }

    public Bild ansicht() { return ansicht; }

    // ------------------------------------------------------------------ Sonne und Mond

    /** Länge und Breite des Auges (Grad). */
    public double[] ort(Blick b) { return Utm.rueckwaerts(b.ost, b.nord, zone); }

    /** Richtung in der Szene aus Ost, Nord, oben am Ort ({@link Lichtquellen#inSzene}). */
    public double[] inSzene(double ost, double nord, double oben, double[] lonLat) { return Lichtquellen.inSzene(ost, nord, oben, lonLat, zone); }

    /** Strahlenbrechung (Grad), {@link Lichtquellen#brechung}. */
    public static double brechung(double hGrad) { return Lichtquellen.brechung(hGrad); }

    /** Phasenwinkel des Monds (Grad), {@link Lichtquellen#phasenwinkel}. */
    public static double phasenwinkel(Gestirne.Stand st) { return Lichtquellen.phasenwinkel(st); }

    /** Helligkeit des Monds (mag), {@link Lichtquellen#mondMag}. */
    public static double mondMag(double psi, double abstand) { return Lichtquellen.mondMag(psi, abstand); }

    // ------------------------------------------------------------------ je Bild

    /** Die Werte eines Bilds (Aufbau {@code Himmelswerte}); legt dabei Sonne und Mond als Zahlen ins Bildziel. */
    public float[] werte(Bildziel z) {
        Blick b = z.blick;
        Lichtquellen.Stand q = Lichtquellen.bei(z.zeit, b.ost, b.nord, zone);
        double[] s = q.sonne(), m = q.mond(), sS = q.sonneSicht(), mS = q.mondSicht();
        double eS = q.eSonne(), sinS = q.sinSonne(), sinM = q.sinMond();
        double[][] bas = b.basis();
        double t = 1 / Math.tan(Math.toRadians(b.bildwinkel) / 2), a = (double) z.breite / z.hoehe;
        double h = Math.max(1, b.hoehe);
        Atmosphere at = luft();
        float[] u = at.uniforms();
        float[] w = new float[WERTE_FLOATS];
        set(w, 0, u[0], u[1], u[2], u[3]);
        // seit P10d (F100 A): Sonne und Mond beleuchten aus der gehobenen Richtung (der sichtbaren Scheibe), wie die Referenz
        set(w, 4, sS[0], sS[1], sS[2], h);
        set(w, 8, eS, eS, eS, z.vorbelichtung);
        set(w, 12, mS[0], mS[1], mS[2], NACHT);
        double[] eM = q.eMond(), lM = q.lMond();
        set(w, 16, eM[0], eM[1], eM[2], 2 / t / z.hoehe);
        set(w, 20, bas[0][0], bas[0][1], bas[0][2], a / t);
        set(w, 24, bas[1][0], bas[1][1], bas[1][2], 1 / t);
        set(w, 28, bas[2][0], bas[2][1], bas[2][2], b.nah);
        set(w, 32, sS[0], sS[1], sS[2], Math.sqrt(1 - sinS * sinS));
        set(w, 36, q.lSonne(), RAND_U, sinS, 0);
        set(w, 40, mS[0], mS[1], mS[2], Math.sqrt(1 - sinM * sinM));
        set(w, 44, lM[0], lM[1], lM[2], sinM);
        set(w, 48, z.breite, z.hoehe, schritte, nachtStreu);                // w seit P13d (A36): Streulicht des Nachthimmels, 0 aus
        // seit P10d: Stärke und Farbe der direkten Sonne und des Monds nach der Luft, je für ihre Höhe (Lichtquellen.farbeNachLuft)
        long n0 = System.nanoTime();
        double[] fS = farbe(at, sS[1], 0), fM = farbe(at, mS[1], 1);
        farbeNs = System.nanoTime() - n0;
        set(w, 52, fS[0], fS[1], fS[2], 0);
        set(w, 56, fM[0], fM[1], fM[2], 0);
        double hsS = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sS[1])))), hmS = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, mS[1]))));
        hoeheSonne = hsS;
        hoeheMond = hmS;
        z.werte.put(SONNE, new double[] {sS[0], sS[1], sS[2], eS, eS, eS, hsS, q.sonneAzimut(), q.sonneHoehe()});
        z.werte.put(MOND, new double[] {mS[0], mS[1], mS[2], eM[0], eM[1], eM[2], hmS, q.mondAzimut(), q.phase(), q.mondHoehe()});
        return w;
    }

    /**
     * Seit P13d (A36, Datei 106): Anteil des Nachthimmels, den die Luft auf dem Blick schluckt und als gleichmäßige
     * Helligkeit zurückgibt [G]: 1, die Leuchtdichte des Zenits über dem Boden der Luft (Rayleigh streut, ohne zu schlucken;
     * das Mittel des Nachthimmels über die Halbkugel liegt beim 1- bis 1,5-Fachen des Zenits). Nicht angepasst.
     */
    public static final double NACHT_STREU = 1.0;
    /** Streulicht des Nachthimmels (0: aus, wie bis P13c; sonst {@link #NACHT_STREU}); seit der Abnahme von P13d (F171 A) an, aus mit {@code -Dlauf.nacht.streu=aus}. */
    public volatile double nachtStreu = "aus".equals(System.getProperty("lauf.nacht.streu")) ? 0 : NACHT_STREU;

    /** Zeit für die Farbe von Sonne und Mond im letzten Bild (ns), für Prüfstein 8. */
    public long farbeNs;
    /** Zuletzt gerechnete Farbe je Quelle (Sonne 0, Mond 1): {mu, Sicht, Albedo} und der Faktor. */
    private final double[][] farbeBei = new double[2][], farbeWert = new double[2][];

    /**
     * Faktor der direkten Quelle nach der Luft ({@link Lichtquellen#farbeNachLuft}); neu nur, wenn sich mu um mehr als
     * 1e-5 (rund 0,0006° an der Kimm) oder die Luft ändert [G].
     */
    private double[] farbe(Atmosphere at, double mu, int i) {
        double[] b = farbeBei[i];
        if (b == null || Math.abs(b[0] - mu) > 1e-5 || b[1] != at.visibilityKm || b[2] != at.groundAlbedo) {
            farbeWert[i] = Lichtquellen.farbeNachLuft(at, mu);
            farbeBei[i] = new double[] {mu, at.visibilityKm, at.groundAlbedo};
        }
        return farbeWert[i];
    }

    private static void set(float[] w, int i, double a, double b, double c, double d) {
        w[i] = (float) a;
        w[i + 1] = (float) b;
        w[i + 2] = (float) c;
        w[i + 3] = (float) d;
    }

    @Override public void vorbereiten(Ablauf a, Bildziel z) {
        float[] w = werte(z);
        letzte = w;
        daten.clear();
        for (float f : w) daten.putFloat(f);
        daten.flip();
        a.durchgang("Himmel, Werte", new Ablauf.Zugriff[] {new Ablauf.Zugriff(werte, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, werte.handle, 0, daten));
        boolean mischen = !Double.isNaN(wasserAnteil);
        if (gebautSicht != sichtKm || mischen != gebautMischen || (!mischen && gebautAlbedo != albedo)) {
            gebautMischen = mischen;
            ladeJ();
            a.rechne("Himmel, Durchlässigkeit", kDurch, kDurch.satz(durch, werte), null, Kernel.gruppen(TRANS_W, 8), Kernel.gruppen(TRANS_H, 8), 1,
                    Ablauf.und(Ablauf.schreibt(durch), Ablauf.liest(Ablauf.RECHNEN, werte)));
            gebautSicht = sichtKm;
            gebautAlbedo = albedo;
            neuGerechnet++;
        }
        // seit P10d: J für die Albedo der Szene mischen, wenn sie sich merklich ändert; die Ansicht folgt verteilt
        double[] ar = mischen ? albedoWirksamRgb() : null;
        boolean anders = false;
        if (mischen && gemischtRgb != null) for (int c = 0; c < 3; c++) anders |= Math.abs(ar[c] - gemischtRgb[c]) > MISCH_SCHRITT;
        if (mischen && (gemischt < 0 || anders)) {
            // seit F105 A je Kanal (die Tabelle der drei Kanäle) und je Wellenlänge (die Gruppen der Himmelsansicht)
            Bild[] ziel = new Bild[1 + GRUPPEN];
            ziel[0] = mehr;
            for (int i = 0; i < GRUPPEN; i++) ziel[1 + i] = spJ[i];
            for (int i = 0; i <= GRUPPEN; i++) {
                ByteBuffer pc = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
                for (int c = 0; c < 3; c++) {
                    double al = i == 0 ? ar[c] : albedoBei(ar, spektral.lambda[3 * (i - 1) + c]);
                    pc.putFloat(4 * c, (float) ((al - ALB_WASSER) / (ALB_LAND - ALB_WASSER)));
                }
                pc.putFloat(16, J_W).putFloat(20, J_H);
                a.rechne("Himmel, J mischen", kMisch, kMisch.satz(ziel[i], punkt.an(jWasser[i]), punkt.an(jLand[i])), pc, Kernel.gruppen(J_W, 8), Kernel.gruppen(J_H, 8), 1,
                        Ablauf.und(Ablauf.schreibt(ziel[i]), Ablauf.textur(Ablauf.RECHNEN, jWasser[i], jLand[i])));
            }
            jNeu = gemischt >= 0;
            if (gemischt < 0) neuGerechnet++;                   // erste Mischung: die Ansicht in einem Zug
            gemischtRgb = ar;
            gemischt = Y[0] * ar[0] + Y[1] * ar[1] + Y[2] * ar[2];
            mischungen++;
            float[] u = luft().uniforms();
            for (int i = 0; i < 4; i++) w[i] = u[i];
            letzte = w;
            daten.clear();
            for (float x : w) daten.putFloat(x);
            daten.flip();
            a.durchgang("Himmel, Werte nach der Mischung", new Ablauf.Zugriff[] {new Ablauf.Zugriff(werte, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                    c -> vkCmdUpdateBuffer(c, werte.handle, 0, daten));
        }
        // seit P7e: die Ansicht nur neu, wenn Sonne, Mond, Höhe oder Luft sich merklich ändern [G]: Richtung 2e-4 (0,01°,
        // die Sonne wandert sie in 2,4 s), Stärke und Höhe 1e-3 relativ; sonst gilt die vom Bild davor.
        // Seit F62 A über fünf Bilder verteilt: je Bild eine Gruppe, nach der fünften die Abbildung nach sRGB.
        boolean alles = ansichtWerte == null || neuGerechnet != gebautBei || abstand(w, 1e-2f, 5e-2f)
                || !verteilt && (gruppeDran > 0 || jNeu || abstand(w, 2e-4f, 1e-3f));
        if (alles || gruppeDran > 0 || jNeu || abstand(w, 2e-4f, 1e-3f)) {
            jNeu = false;
            gebautBei = neuGerechnet;
            boolean fertig;
            if (alles) {
                for (int i = 0; i < GRUPPEN; i++) gruppe(a, i);
                gruppeDran = 0;
                fertig = true;
            } else {
                gruppe(a, gruppeDran);
                gruppeDran = (gruppeDran + 1) % GRUPPEN;
                fertig = gruppeDran == 0;
            }
            if (fertig) {
                ansichtWerte = w.clone();
                ansichtGerechnet++;
                Object[] sa = new Object[5 + GRUPPEN];
                sa[0] = ansicht;
                sa[1] = punkt.an(durch);
                sa[2] = punkt.an(mehr);
                sa[3] = werte;
                sa[4] = spektrum;
                Bild[] tex = new Bild[2 + GRUPPEN];
                tex[0] = durch;
                tex[1] = mehr;
                for (int i = 0; i < GRUPPEN; i++) {
                    sa[5 + i] = punkt.an(spL[i]);
                    tex[2 + i] = spL[i];
                }
                a.rechne("Himmel, Ansicht", kAnsicht, kAnsicht.satz(sa), null, Kernel.gruppen(ANSICHT_W, 8), Kernel.gruppen(ANSICHT_H, 8), 1,
                        Ablauf.und(Ablauf.schreibt(ansicht), Ablauf.textur(Ablauf.RECHNEN, tex), Ablauf.liest(Ablauf.RECHNEN, werte, spektrum)));
            }
        }
        z.geteilt.put(DURCH, durch);
        z.geteilt.put(MEHR, mehr);
        z.geteilt.put(ANSICHT, ansicht);
        z.puffer.put(WERTE, werte);
    }

    /** Eine Gruppe der Ansicht: drei Wellenlängen der Sonne mit den Werten dieses Bilds. */
    private void gruppe(Ablauf a, int i) {
        a.rechne("Himmel, Ansicht, Gruppe", kGruppe[i], kGruppe[i].satz(spL[i], punkt.an(spD[i]), punkt.an(spJ[i]), werte, spektrum), null,
                Kernel.gruppen(ANSICHT_W, 8), Kernel.gruppen(ANSICHT_H, 8), 1,
                Ablauf.und(Ablauf.schreibt(spL[i]), Ablauf.textur(Ablauf.RECHNEN, spD[i], spJ[i]), Ablauf.liest(Ablauf.RECHNEN, werte, spektrum)));
    }

    /** Für Prüfungen: Die nächste Ansicht wird neu und in einem Zug gerechnet. */
    public void verwirfAnsicht() {
        ansichtWerte = null;
        gruppeDran = 0;
    }

    /** Ob die Werte von denen der letzten Ansicht um mehr als die Schwellen abweichen (Richtung absolut, Stärke und Höhe relativ). */
    private boolean abstand(float[] w, float richtung, float relativ) {
        float[] a = ansichtWerte;
        // die Albedo (Wert 2) zählt hier nicht: Ihre Änderung rechnet die Tabellen neu oder mischt J (seit P10d)
        for (int i = 0; i < 4; i++) if (i != 2 && a[i] != w[i]) return true;
        if (a[51] != w[51]) return true;                                    // seit P13d: Streulicht des Nachthimmels an oder aus
        for (int i : new int[] {4, 5, 6, 12, 13, 14}) if (Math.abs(a[i] - w[i]) > richtung) return true;
        for (int i : new int[] {7, 8, 9, 10, 15, 16, 17, 18})
            if (Math.abs(a[i] - w[i]) > relativ * Math.max(Math.abs(a[i]), Math.abs(w[i]))) return true;
        return false;
    }

    private int gebautBei = -1;
    private boolean gebautMischen;

    @Override public void zeichnen(Ablauf a, Bildziel z) {
        long satz = kHimmel.satz(z.hdr, punkt.an(z.tiefe), punkt.an(durch), punkt.an(mehr), punkt.an(ansicht), werte);
        a.rechne("Himmel", kHimmel, satz, null, Kernel.gruppen(z.breite, 8), Kernel.gruppen(z.hoehe, 8), 1,
                Ablauf.und(Ablauf.schreibt(z.hdr), Ablauf.textur(Ablauf.RECHNEN, z.tiefe, durch, mehr, ansicht), Ablauf.liest(Ablauf.RECHNEN, werte)));
    }

    /** Für Prüfungen: der Zeitpunkt als Stand der Gestirne. */
    public static Gestirne.Stand stand(Instant t) { return Gestirne.bei(t); }

    @Override public void close() {
        kDurch.close();
        kMisch.close();
        if (jWasser != null) for (int i = 0; i <= GRUPPEN; i++) {
            if (jWasser[i] != null) jWasser[i].close();
            if (jLand[i] != null) jLand[i].close();
        }
        kAnsicht.close();
        kHimmel.close();
        durch.close();
        mehr.close();
        for (int i = 0; i < GRUPPEN; i++) {
            spD[i].close();
            spJ[i].close();
            spL[i].close();
            kGruppe[i].close();
        }
        spektrum.close();
        ansicht.close();
        werte.close();
        punkt.close();
    }
}
