package com.dan.render.reference.gpu;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Wasseroptik;
import com.dan.core.szene.spektrum.Spektrum;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Phasen;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.Wellen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Die Referenz Stufe 2 (P11a, Werkbuch HalligHooge Teil A, 8.5 und 12.12; F107–F111 A): derselbe Pfadverfolger wie
 * {@link Referenz} (Stufe 1) auf der Karte, in float, Lage relativ zum Auge. Die Shader folgen den Klassen von Stufe 1
 * Datei für Datei ({@code kopf.glsl}: Vek, Wellen, Licht; {@code pfad.glsl}: Phasen, Facette, Medien, Pfadverfolger;
 * {@code szene_ebenen.glsl}: Ebenen). Stufe 1 bleibt der Maßstab.
 * <ul>
 * <li><b>Einstellungen</b> kommen aus einer {@link Referenz} von Stufe 1 ({@link #uebernimm}), damit beide dieselbe Szene
 * rechnen; dazu die Schalter der Prüfungen ({@link #ofen}, {@link #ohneLuft}, {@link #nurRayleigh}).</li>
 * <li><b>Runden:</b> je Aufruf der Karte höchstens rund {@link #ZIEL_MS} ms (Windows bricht nach 2 s ab); jede Rechnung
 * beginnt mit einem Pfad je Faden, die Zahl verdoppelt sich, solange eine Runde unter der Hälfte des Ziels bleibt.</li>
 * <li><b>Ausgabe:</b> je Faden 24 Werte, über die Runden aufaddiert. Bild: Summe je Hälfte (gerade und ungerade Nummer
 * des Pfads) in XYZ bzw. RGB, mit {@link #teile} dazu die vier Teile des Wassers. Sonst Summe und Summe der Quadrate
 * je Kanal, des Mittels der Bahnen und die Zahl.</li>
 * </ul>
 */
public final class ReferenzKarte implements AutoCloseable {
    public static final String VERSION = "0.1";
    public static final int MODUS_BILD = 0, MODUS_RICHTUNG = 1, MODUS_WASSERFLUG = 2, MODUS_EINFACH = 3, MODUS_FF = 4, MODUS_TAFELN = 5;
    /** Werte je Faden in der Ausgabe. */
    public static final int STRIDE = 24;
    /** Ziel für eine Runde auf der Karte (ms) [G]. */
    public static final double ZIEL_MS = 60;

    // Indizes in W (wie kopf.glsl)
    public static final int W_HAUGE = 0, W_BODEN = 1, W_HAT_BODEN = 2, W_WASSER = 3, W_HAT_WASSER = 4, W_KUGEL = 5, W_SZ_SIGMA2 = 6, W_ALB = 7, W_ETA = 10,
            W_ALPHA2 = 11, W_SPEKTRAL = 12, W_NB = 13, W_MIE_EXT = 14, W_MIE_SCA = 15, W_BWASSER = 16, W_SCHWEB = 17, W_AS = 18, W_RAY = 21, W_OZ = 24,
            W_POPE = 27, W_SONNE_K = 30, W_MOND_K = 33, W_NACHT_K = 36, W_MONDG = 39, W_NACHTF = 42, W_KORR = 43, W_A443 = 52, W_SNAP = 53,
            W_BBSTERN = 54, W_BBZUB = 55, W_OFEN = 56, W_EINFACH = 57, W_MIT_R = 58, W_MIT_M = 59, W_MIT_O = 60, W_NACHT_AN = 61, W_OHNE_ABS = 62,
            W_JEDER = 63, W_KEULE = 64, W_DICHTE = 65, W_OHNE_SAEULE = 66, W_WWEITE = 67, W_MAXTIEFE = 68, W_NL = 70, W_L0 = 72, W_L1 = 80, W_CAM = 88,
            W_DIR = 97, W_P0 = 100, W_S = 103, W_MIEG = 105, W_RH = 106, W_MH = 107, W_OC = 108, W_OHW = 109, W_R = 110, W_RT = 111, W_TEILE = 112;
    static final int NW = 128;
    static final float UNENDLICH = 3.0e38f;

    final Geraet g;
    final Konto konto;
    final Kernel kernel, kTafeln;
    Kernel kTeile;
    final Puffer werte, spek, ff;
    Puffer aus;
    int ausFaeden;
    /** Werte der Szene und Schalter (Indizes wie kopf.glsl). */
    public final float[] w = new float[NW];
    /** Zerlegung des Wassers im Bild (wie {@link Referenz#teile}); nach {@link #bild} in {@link #teilBild}. */
    public boolean teile;
    public double[][][] teilBild;
    /** Pfade und Zeit (s) des letzten Aufrufs, Zeit nur auf der Karte, Runden. */
    public long pfade;
    public double sekunden, sekundenKarte;
    public int runden;

    public ReferenzKarte(Geraet g, Konto konto) {
        this(g, konto, "/com/dan/render/reference/gpu/pfad_ebenen.comp");
    }

    /** Mit einem anderen Kernel für die Pfade (Befund: {@code pfad_luft.comp}). */
    public ReferenzKarte(Geraet g, Konto konto, String kernelPfad) {
        this.g = g;
        this.konto = konto;
        Kernel.Bindung P = Kernel.Bindung.PUFFER;
        kernel = Kernel.lade(g, kernelPfad, 48, null, P, P, P, P);
        kTafeln = Kernel.lade(g, "/com/dan/render/reference/gpu/tafeln.comp", 48, null, P, P, P, P);
        werte = Puffer.speicher(g, konto, "Referenz Stufe 2, Werte", NW * 4L);
        float[] sp = spektrumTafel(), fft = ffTafel();
        spek = Puffer.speicher(g, konto, "Referenz Stufe 2, Spektrum", sp.length * 4L);
        spek.schreibe(sp);
        ff = Puffer.speicher(g, konto, "Referenz Stufe 2, Fournier-Forand", fft.length * 4L);
        ff.schreibe(fft);
        grundwerte();
    }

    // ------------------------------------------------------------------ Tafeln

    /** Spektrum je 1 nm (360–830): x̄, ȳ, z̄, Sonne, reines Wasser, Ozon (mal Teilchendichte), Basis R, G, B. */
    public static float[] spektrumTafel() {
        int n = Spektrum.N;
        float[] t = new float[9 * n];
        for (int i = 0; i < n; i++) {
            double l = Spektrum.lambda(i);
            t[i] = (float) Spektrum.xBar(l);
            t[n + i] = (float) Spektrum.yBar(l);
            t[2 * n + i] = (float) Spektrum.zBar(l);
            t[3 * n + i] = (float) Spektrum.sonne(l);
            t[4 * n + i] = (float) Spektrum.wasser(l);
            t[5 * n + i] = (float) (Spektrum.OZON_DICHTE * Spektrum.ozon(l));
            for (int k = 0; k < 3; k++) t[(6 + k) * n + i] = (float) Spektrum.basis(k, l);
        }
        return t;
    }

    /**
     * Fournier-Forand aus {@link Phasen#ffTafel}: 1 − cos θ_i = 2 sin²(θ_i / 2), F_i, 1 − F_i (in double gerechnet: float
     * trägt F nahe 1 nicht) und die Dichte je Stufe wie {@link Phasen#ffDichte}.
     */
    public static float[] ffTafel() {
        double[][] t = Phasen.ffTafel();
        double[] th = t[0], F = t[1];
        int n = th.length - 1;
        float[] r = new float[3 * (n + 1) + n];
        for (int i = 0; i <= n; i++) {
            double s = Math.sin(0.5 * th[i]);
            r[i] = (float) (2 * s * s);
            r[n + 1 + i] = (float) F[i];
            r[2 * (n + 1) + i] = (float) (1 - F[i]);
        }
        for (int i = 0; i < n; i++) {
            double dc = 2 * Math.sin(0.5 * (th[i] + th[i + 1])) * Math.sin(0.5 * (th[i + 1] - th[i]));
            r[3 * (n + 1) + i] = (float) ((F[i + 1] - F[i]) / (2 * Math.PI * dc));
        }
        return r;
    }

    // ------------------------------------------------------------------ Einstellungen

    /** Feste Werte: Konstanten der Luft, des Wassers, des Spektrums; Schalter wie Stufe 1 nach dem Bau. */
    void grundwerte() {
        Arrays.fill(w, 0);
        for (int c = 0; c < 3; c++) {
            w[W_RAY + c] = (float) Atmosphere.RAYLEIGH[c];
            w[W_OZ + c] = (float) Atmosphere.OZONE[c];
            w[W_POPE + c] = (float) Wasseroptik.POPE_FRY[c];
            w[W_SONNE_K + c] = (float) Lichtquellen.SONNE_LX;
            w[W_MOND_K + c] = (float) (Lichtquellen.SONNE_LX * Lichtquellen.MONDFARBE[c]);
            w[W_NACHT_K + c] = (float) Lichtquellen.NACHT;
        }
        double[] mg = Spektrum.gewichte(Lichtquellen.MONDFARBE);
        for (int c = 0; c < 3; c++) w[W_MONDG + c] = (float) mg[c];
        w[W_NACHTF] = (float) (Lichtquellen.NACHT / Spektrum.sonneLux());
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) w[W_KORR + 3 * i + j] = (float) Spektrum.KORREKTUR[i][j];
        w[W_A443] = (float) Wasseroptik.A_STERN_443;
        w[W_SNAP] = (float) Wasseroptik.S_NAP;
        w[W_BBSTERN] = (float) Wasseroptik.BB_STERN;
        w[W_BBZUB] = (float) Wellen.BB_ZU_B;
        w[W_MIEG] = (float) Atmosphere.MIE_G;
        w[W_RH] = (float) Atmosphere.RAYLEIGH_H;
        w[W_MH] = (float) Atmosphere.MIE_H;
        w[W_OC] = (float) Atmosphere.OZONE_CENTER;
        w[W_OHW] = (float) Atmosphere.OZONE_HALF_WIDTH;
        w[W_R] = (float) Atmosphere.BOTTOM;
        w[W_RT] = (float) Atmosphere.TOP;
        schalter();
    }

    /** Die Schalter eines neuen Pfadverfolgers von Stufe 1: Luft ganz, Nachthimmel, jeder Stoß, kein Ofen. */
    public ReferenzKarte schalter() {
        w[W_OFEN] = 0;
        w[W_OHNE_ABS] = 0;
        w[W_MIT_R] = w[W_MIT_M] = w[W_MIT_O] = 1;
        w[W_NACHT_AN] = 1;
        w[W_JEDER] = 1;
        w[W_MAXTIEFE] = 100_000;
        return this;
    }

    /** Ofentest (Pfadverfolger.ofen): ohne Lichtquellen, Umgebung 1, nichts absorbiert. */
    public ReferenzKarte ofen() { w[W_OFEN] = 1; w[W_OHNE_ABS] = 1; return this; }

    /** Ohne Luft und Nachthimmel (Pfadverfolger.ohneLuft). */
    public ReferenzKarte ohneLuft() { w[W_MIT_R] = w[W_MIT_M] = w[W_MIT_O] = 0; w[W_NACHT_AN] = 0; return this; }

    /** Nur Rayleigh, ohne Nachthimmel (Pfadverfolger.nurRayleigh). */
    public ReferenzKarte nurRayleigh() { w[W_MIT_M] = w[W_MIT_O] = 0; w[W_NACHT_AN] = 0; return this; }

    /**
     * Übernimmt Szene, Luft, Wasser, Lichter und Einstellungen einer Referenz von Stufe 1 (P11a: Szene {@link Ebenen}).
     * Die Schalter der Pfadverfolger bleiben, wie sie sind ({@link #schalter}).
     */
    public ReferenzKarte uebernimm(Referenz ref) {
        if (!(ref.szene instanceof Ebenen e)) throw new IllegalArgumentException("Stufe 2 kennt in P11a nur die Szene Ebenen");
        w[W_HAUGE] = (float) e.augeHoehe();
        w[W_HAT_BODEN] = Double.isNaN(e.boden) ? 0 : 1;
        w[W_BODEN] = Double.isNaN(e.boden) ? 0 : (float) e.boden;
        w[W_HAT_WASSER] = Double.isNaN(e.wasser) ? 0 : 1;
        w[W_WASSER] = Double.isNaN(e.wasser) ? 0 : (float) e.wasser;
        w[W_KUGEL] = e.kugel ? 1 : 0;
        if (e.neigung != null) throw new IllegalArgumentException("Stufe 2 kennt die Neigung der Ebenen nicht (P11a)");
        w[W_SZ_SIGMA2] = (float) e.sigma2;
        for (int c = 0; c < 3; c++) w[W_ALB + c] = (float) e.albedo[c];
        Atmosphere luft = ref.werte.luft;
        Wasseroptik wa = ref.werte.wasser;
        w[W_MIE_EXT] = (float) luft.mieExtinction;
        w[W_MIE_SCA] = (float) luft.mieScattering;
        w[W_ETA] = (float) ref.eta;
        w[W_ALPHA2] = (float) Math.max(1e-8, ref.sigma2);
        w[W_BWASSER] = (float) ref.werte.bWasser;
        w[W_SCHWEB] = (float) wa.schwebstoff;
        double[] as = wa.aSchweb();
        for (int c = 0; c < 3; c++) w[W_AS + c] = (float) as[c];
        w[W_SPEKTRAL] = ref.spektral ? 1 : 0;
        w[W_NB] = ref.spektral ? 4 : 3;
        w[W_KEULE] = (float) ref.keule;
        w[W_DICHTE] = (float) Math.max(1, ref.dichte);
        w[W_EINFACH] = ref.einfach ? 1 : 0;
        w[W_OHNE_SAEULE] = ref.ohneSaeule ? 1 : 0;
        w[W_WWEITE] = Double.isInfinite(ref.wasserWeite) ? UNENDLICH : (float) ref.wasserWeite;
        int n = Math.min(2, ref.lichter.size());
        w[W_NL] = n;
        for (int i = 0; i < n; i++) {
            Licht li = ref.lichter.get(i);
            int o = i == 0 ? W_L0 : W_L1;
            double[] r = li.richtung();
            for (int k = 0; k < 3; k++) w[o + k] = (float) r[k];
            w[o + 3] = (float) (1 - li.cosRadius());
            w[o + 4] = (float) li.randU();
            w[o + 5] = (float) li.faktor();
            w[o + 6] = li.sonne() ? 1 : 0;
        }
        return this;
    }

    /** Fester Strahl (Modus Richtung, Einfachstreuung): Ort relativ zum Auge und Richtung (Einheitsvektor). */
    public ReferenzKarte strahl(double[] p0, double[] d) {
        for (int k = 0; k < 3; k++) {
            w[W_P0 + k] = (float) p0[k];
            w[W_DIR + k] = (float) d[k];
        }
        return this;
    }

    /** Weg durch das Wasser im Modus Wasserflug (m). */
    public ReferenzKarte weg(double s) { w[W_S] = (float) s; return this; }

    // ------------------------------------------------------------------ Rechnen

    /**
     * Rechnet {@code n} Pfade je Faden in {@code faeden} Fäden im Modus {@code modus} und gibt die aufaddierten Werte
     * zurück (je Faden {@link #STRIDE}). Runden wie oben; {@code pfadBasis} zählt die Pfade je Faden fort (Hälften).
     */
    public double[] rechne(int modus, int faeden, int n, long saat, int breite, int hoehe) {
        w[W_TEILE] = teile ? 1 : 0;
        werte.schreibe(w);
        if (aus == null || ausFaeden < faeden) {
            if (aus != null) aus.close();
            aus = Puffer.geteilt(g, konto, "Referenz Stufe 2, Ausgabe", (long) faeden * STRIDE * 4);
            ausFaeden = faeden;
        }
        aus.nullen();
        long t0 = System.nanoTime();
        double karte = 0, groesste = 0;
        // Aufrufe in Stücken: ein Stück sind st Fäden mit k Pfaden je Faden. Beginn klein (256 Fäden, ein Pfad), dann
        // erst die Fäden, dann die Pfade verdoppeln, solange ein Aufruf unter der Hälfte des Ziels bleibt; über dem
        // Doppelten halbieren. Kein Aufruf soll in die Grenze von Windows (2 s) laufen.
        int st = Math.min(faeden, 256), k = 1, fertig = 0, runde = 0, aufrufe = 0;
        ByteBuffer push = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
        Kernel k0 = modus == MODUS_TAFELN || modus == MODUS_FF ? kTafeln : teile && modus == MODUS_BILD ? teileKernel() : kernel;
        long satz = k0.satz(werte, spek, ff, aus);
        StringBuilder pr = new StringBuilder();
        while (fertig < n) {
            int m = Math.min(k, n - fertig);
            double rundeMs = 0, rundeMax = 0;
            int stRunde = st;
            for (int v = 0; v < faeden; v += stRunde) {
                int anz = Math.min(stRunde, faeden - v);
                push.putInt(0, modus).putInt(4, m).putInt(8, runde).putInt(12, (int) saat).putInt(16, Math.max(1, breite)).putInt(20, Math.max(1, hoehe))
                        .putInt(24, anz).putInt(28, v).putInt(32, fertig).putInt(36, faeden);
                Ablauf a = new Ablauf(g, g.rechnen);
                a.rechne("Referenz Stufe 2", k0, satz, push, Kernel.gruppen(anz, 64), 1, 1, new Mittel[] {werte, spek, ff, aus}, new Mittel[] {aus});
                double ms = g.rechnen.einmal(c -> a.aufnehmen(c, null)) / 1e6;
                aufrufe++;
                rundeMs += ms;
                rundeMax = Math.max(rundeMax, ms);
                if (modus == MODUS_TAFELN) continue;
                if (ms > 2 * ZIEL_MS && anz == stRunde) st = Math.max(64, st / 2);       // ab dem nächsten Stück kleiner
            }
            karte += rundeMs / 1000;
            groesste = Math.max(groesste, rundeMax);
            if (runde < 3 || runde % 50 == 0) {
                String z = String.format(java.util.Locale.GERMAN, "R%d %d×%d %.0f ms; ", runde, Math.min(stRunde, faeden), m, rundeMax);
                pr.append(z);
                if (laut) {
                    System.out.println("           Karte, Modus " + modus + ": " + z);
                    System.out.flush();
                }
            }
            fertig += m;
            runde++;
            if (modus == MODUS_TAFELN) break;
            if (rundeMax < ZIEL_MS / 2 && st == stRunde) {
                if (st < faeden) st = Math.min(faeden, st * 2);
                else if (m == k) k = Math.min(1 << 16, k * 2);
            } else if (rundeMax > 2 * ZIEL_MS && k > 1) k = Math.max(1, k / 2);
        }
        runden = runde;
        this.aufrufe = aufrufe;
        groessterAufrufMs = groesste;
        sekundenKarte = karte;
        sekunden = (System.nanoTime() - t0) / 1e9;
        pfade = modus == MODUS_TAFELN ? faeden : (long) faeden * n;
        float[] f = aus.floats(faeden * STRIDE);
        double[] r = new double[f.length];
        abgebrochen = 0;
        nan = 0;
        for (int i = 0; i < f.length; i++) r[i] = f[i];
        if (modus != MODUS_TAFELN)
            for (int i = 0; i < faeden; i++) {
                nan += Math.round(r[i * STRIDE + 22]);
                abgebrochen += Math.round(r[i * STRIDE + 23]);
            }
        protokoll = pr.toString();
        return r;
    }

    /**
     * Ein einzelner Aufruf (Befund): {@code faeden} Fäden mit {@code pfade} Pfaden, gibt die Zeit auf der Karte in ms.
     * Die Ausgabe wird vorher genullt; danach stehen NaN und Abbrüche in {@link #nan}, {@link #abgebrochen}.
     */
    public double einAufruf(int modus, int faeden, int pfade, long saat) {
        w[W_TEILE] = 0;
        werte.schreibe(w);
        if (aus == null || ausFaeden < faeden) {
            if (aus != null) aus.close();
            aus = Puffer.geteilt(g, konto, "Referenz Stufe 2, Ausgabe", (long) faeden * STRIDE * 4);
            ausFaeden = faeden;
        }
        aus.nullen();
        Kernel k0 = modus == MODUS_TAFELN || modus == MODUS_FF ? kTafeln : kernel;
        long satz = k0.satz(werte, spek, ff, aus);
        ByteBuffer push = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
        push.putInt(0, modus).putInt(4, pfade).putInt(8, 0).putInt(12, (int) saat).putInt(16, 1).putInt(20, 1).putInt(24, faeden).putInt(28, 0).putInt(32, 0).putInt(36, faeden);
        Ablauf a = new Ablauf(g, g.rechnen);
        a.rechne("Referenz Stufe 2", k0, satz, push, Kernel.gruppen(faeden, 64), 1, 1, new Mittel[] {werte, spek, ff, aus}, new Mittel[] {aus});
        double ms = g.rechnen.einmal(c -> a.aufnehmen(c, null)) / 1e6;
        float[] f = aus.floats(faeden * STRIDE);
        nan = 0;
        abgebrochen = 0;
        for (int i = 0; i < faeden; i++) {
            nan += Math.round(f[i * STRIDE + 22]);
            abgebrochen += Math.round(f[i * STRIDE + 23]);
        }
        return ms;
    }

    /** Nach {@link #rechne}: Aufrufe der Karte, längster Aufruf (ms), Pfade mit NaN und von der Wache abgebrochene, Anfang der Runden. */
    public int aufrufe;
    public double groessterAufrufMs;
    public long nan, abgebrochen;
    public String protokoll = "";
    /** Die ersten Runden jeder Rechnung sofort ausgeben (Befund, falls die Karte ausfällt). */
    public boolean laut;

    /**
     * Ein Bild breite × hoehe mit {@code n} Pfaden je Bildpunkt wie {@link Referenz#bild}: [Hälfte][Kanal][Bildpunkt]
     * (lineares sRGB in cd/m²). Zeile 0 oben.
     */
    public double[][][] bild(Blick blick, int breite, int hoehe, int n, long saat) {
        Blick b = blick.kopie().groesse(breite, hoehe);
        double t = 1 / Math.tan(Math.toRadians(b.bildwinkel) / 2), a = (double) breite / hoehe;
        double[][] ba = b.basis();
        for (int i = 0; i < 3; i++) {
            w[W_CAM + i] = (float) (ba[0][i] * a / t);
            w[W_CAM + 3 + i] = (float) (-ba[1][i] / t);
            w[W_CAM + 6 + i] = (float) ba[2][i];
        }
        int px = breite * hoehe;
        double[] r = rechne(MODUS_BILD, px, n, saat, breite, hoehe);
        boolean sp = w[W_SPEKTRAL] > 0.5;
        double[][][] h = new double[2][3][px];
        int m0 = (n + 1) / 2, m1 = n / 2;
        for (int i = 0; i < px; i++)
            for (int hh = 0; hh < 2; hh++) {
                double[] v = new double[3];
                for (int c = 0; c < 3; c++) v[c] = r[i * STRIDE + 3 * hh + c] / Math.max(1, hh == 0 ? m0 : m1);
                double[] rgb = sp ? Spektrum.nachRgb(v) : v;
                for (int c = 0; c < 3; c++) h[hh][c][i] = rgb[c];
            }
        if (teile) {
            teilBild = new double[4][3][px];
            for (int i = 0; i < px; i++)
                for (int tt = 0; tt < 4; tt++) {
                    double[] v = new double[3];
                    for (int c = 0; c < 3; c++) v[c] = r[i * STRIDE + 6 + 3 * tt + c] / Math.max(1, n);
                    double[] rgb = sp ? Spektrum.nachRgb(v) : v;
                    for (int c = 0; c < 3; c++) teilBild[tt][c][i] = rgb[c];
                }
        } else teilBild = null;
        return h;
    }

    /**
     * Mittel und Streuung des Mittels je Kanal über {@code faeden} × {@code n} Pfade (Modus Richtung, Wasserflug,
     * Einfachstreuung, FF): {m0, m1, m2, σ0, σ1, σ2, Mittel der Bahnen, σ davon}.
     */
    public double[] schaetze(int modus, int faeden, int n, long saat) {
        double[] r = rechne(modus, faeden, n, saat, 1, 1);
        double[] s = new double[9];
        for (int i = 0; i < faeden; i++)
            for (int k = 0; k < 9; k++) s[k] += r[i * STRIDE + k];
        double N = s[8];
        double[] e = new double[8];
        for (int k = 0; k < 3; k++) {
            e[k] = s[k] / N;
            e[3 + k] = Math.sqrt(Math.max(0, s[3 + k] / N - e[k] * e[k]) / N);
        }
        e[6] = s[6] / N;
        e[7] = Math.sqrt(Math.max(0, s[7] / N - e[6] * e[6]) / N);
        return e;
    }

    // ------------------------------------------------------------------ Wellenfront (Probe P11a, F112 A)

    /** Kernel der Wellenfront in der Reihenfolge einer Runde (wellenfront.glsl); der Auftrag kommt zweimal. */
    static final String[] WF_KERNEL = {"neu", "schnitt", "flug", "auftrag", "ereignis"};
    static final int[] WF_RUNDE = {0, 1, 2, 3, 4, 3};
    static final int WF_Z = 72, WF_ZW = 28, WF_ZT = 9;
    Kernel[] wf;
    String wfVariante;
    Puffer wfZ, wfZW, wfZT, wfZaehler;
    int wfPlaetze;
    /** Nach {@link #wellenfront}: Runden, Abschickungen, Zeit auf der Karte (s), längste Abschickung (ms). */
    public int wfRunden, wfAbschickungen;
    public double wfSekunden, wfGroessteMs;
    /**
     * Pfade/s im Dauerbetrieb: zwischen der ersten Abschickung, nach der 10 %, und der letzten, nach der höchstens 90 %
     * der Pfade fertig sind (ohne Anlauf und ohne die letzten langen Pfade); NaN, wenn dazwischen keine Zeit liegt.
     */
    public double wfDauerrate;

    /** Die fünf Kernel einer Variante ({@code luft}: ohne Wasser, {@code ebenen}: mit), beim ersten Bedarf gebaut. */
    Kernel[] wfKernel(String variante) {
        if (wf != null && variante.equals(wfVariante)) return wf;
        if (wf != null) for (Kernel k : wf) k.close();
        Kernel.Bindung P = Kernel.Bindung.PUFFER;
        wf = new Kernel[WF_KERNEL.length];
        for (int i = 0; i < wf.length; i++)
            wf[i] = Kernel.lade(g, "/com/dan/render/reference/gpu/wf_" + variante + "_" + WF_KERNEL[i] + ".comp", 48, null, P, P, P, P, P, P, P, P);
        wfVariante = variante;
        return wf;
    }

    /**
     * Pfade als Wellenfront: {@code plaetze} Plätze mit je {@code pfade} Pfaden (Modus Bild oder Richtung). Die Runden
     * gehen in Abschickungen zu je r Runden; r wächst, solange eine Abschickung unter der Hälfte von {@link #ZIEL_MS}
     * bleibt. Gibt die Ausgabe je Platz zurück (wie {@link #rechne}).
     */
    public double[] wellenfront(String variante, int modus, int plaetze, int pfade, long saat) {
        Kernel[] k = wfKernel(variante);
        w[W_TEILE] = 0;
        werte.schreibe(w);
        if (wfPlaetze != plaetze) {
            if (wfZ != null) { wfZ.close(); wfZW.close(); wfZT.close(); }
            wfZ = Puffer.speicher(g, konto, "Referenz Stufe 2, Wellenfront Zustand", (long) WF_Z * plaetze * 4);
            wfZW = Puffer.speicher(g, konto, "Referenz Stufe 2, Wellenfront Wellen", (long) WF_ZW * plaetze * 4);
            wfZT = Puffer.speicher(g, konto, "Referenz Stufe 2, Wellenfront Treffer", (long) WF_ZT * plaetze * 4);
            wfPlaetze = plaetze;
        }
        if (wfZaehler == null) wfZaehler = Puffer.geteilt(g, konto, "Referenz Stufe 2, Wellenfront Zähler", 16);
        if (aus == null || ausFaeden < plaetze) {
            if (aus != null) aus.close();
            aus = Puffer.geteilt(g, konto, "Referenz Stufe 2, Ausgabe", (long) plaetze * STRIDE * 4);
            ausFaeden = plaetze;
        }
        aus.nullen();
        wfZ.nullen();
        wfZaehler.nullen();
        long[] satz = new long[k.length];
        for (int i = 0; i < k.length; i++) satz[i] = k[i].satz(werte, spek, ff, aus, wfZ, wfZaehler, wfZW, wfZT);
        ByteBuffer push = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
        push.putInt(0, modus).putInt(4, pfade).putInt(8, 0).putInt(12, (int) saat).putInt(16, 1).putInt(20, 1)
            .putInt(24, plaetze).putInt(28, 0).putInt(32, 0).putInt(36, plaetze);
        Mittel[] liest = {werte, spek, ff}, schreibt = {aus, wfZ, wfZaehler, wfZW, wfZT};
        int r = 1, gruppen = Kernel.gruppen(plaetze, 64);
        wfRunden = 0;
        wfAbschickungen = 0;
        wfSekunden = 0;
        wfGroessteMs = 0;
        nan = 0;
        abgebrochen = 0;
        long t0 = System.nanoTime();
        long gesamt = (long) plaetze * pfade;
        double tA = Double.NaN, tB = Double.NaN;
        long pA = 0, pB = 0;
        wfDauerrate = Double.NaN;
        while (true) {
            Ablauf a = new Ablauf(g, g.rechnen);
            for (int j = 0; j < r; j++)
                for (int i : WF_RUNDE) a.rechne("Wellenfront " + WF_KERNEL[i], k[i], satz[i], push, gruppen, 1, 1, liest, schreibt);
            double ms = g.rechnen.einmal(c -> a.aufnehmen(c, null)) / 1e6;
            wfSekunden += ms / 1000;
            wfGroessteMs = Math.max(wfGroessteMs, ms);
            wfRunden += r;
            wfAbschickungen++;
            int[] zz = wfZaehler.ints(2);
            int fertig = zz[0];
            long pf = zz[1] & 0xffffffffL;
            if (Double.isNaN(tA) && pf >= 0.1 * gesamt) { tA = wfSekunden; pA = pf; }
            if (pf <= 0.9 * gesamt) { tB = wfSekunden; pB = pf; }
            if (laut && wfAbschickungen <= 12) System.out.println("           Wellenfront: " + r + " Runden in " + String.format("%.1f", ms) + " ms, fertig " + fertig + " von " + plaetze);
            if (fertig >= plaetze) break;
            if ((System.nanoTime() - t0) / 1e9 > 600) throw new IllegalStateException("Wellenfront: nach 600 s nicht fertig (" + fertig + " von " + plaetze + ")");
            if (ms < ZIEL_MS / 2.0 && r < 4096) r *= 2;
            else if (ms > 2.0 * ZIEL_MS && r > 1) r /= 2;
        }
        if (!Double.isNaN(tA) && !Double.isNaN(tB) && tB > tA) wfDauerrate = (pB - pA) / (tB - tA);
        float[] f = aus.floats(plaetze * STRIDE);
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) d[i] = f[i];
        for (int i = 0; i < plaetze; i++) {
            nan += Math.round(f[i * STRIDE + 22]);
            abgebrochen += Math.round(f[i * STRIDE + 23]);
        }
        this.pfade = (long) plaetze * pfade;
        sekundenKarte = wfSekunden;
        return d;
    }

    /** Wie {@link #schaetze}, mit der Wellenfront. */
    public double[] wfSchaetze(String variante, int modus, int plaetze, int pfade, long saat) {
        double[] r = wellenfront(variante, modus, plaetze, pfade, saat);
        double[] s = new double[9];
        for (int i = 0; i < plaetze; i++)
            for (int k = 0; k < 9; k++) s[k] += r[i * STRIDE + k];
        double N = s[8];
        double[] e = new double[8];
        for (int k = 0; k < 3; k++) {
            e[k] = s[k] / N;
            e[3 + k] = Math.sqrt(Math.max(0, s[3 + k] / N - e[k] * e[k]) / N);
        }
        e[6] = s[6] / N;
        e[7] = Math.sqrt(Math.max(0, s[7] / N - e[6] * e[6]) / N);
        return e;
    }

    /** Der Kernel mit der Zerlegung des Wassers (pfad_ebenen_teile.comp), beim ersten Bedarf gebaut. */
    Kernel teileKernel() {
        if (kTeile == null) {
            Kernel.Bindung P = Kernel.Bindung.PUFFER;
            kTeile = Kernel.lade(g, "/com/dan/render/reference/gpu/pfad_ebenen_teile.comp", 48, null, P, P, P, P);
        }
        return kTeile;
    }

    @Override public void close() {
        if (wf != null) for (Kernel k : wf) k.close();
        if (wfZ != null) { wfZ.close(); wfZW.close(); wfZT.close(); }
        if (wfZaehler != null) wfZaehler.close();
        if (kTeile != null) kTeile.close();
        kTafeln.close();
        if (aus != null) aus.close();
        ff.close();
        spek.close();
        werte.close();
        kernel.close();
    }
}
