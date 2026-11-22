package com.dan.render.air;

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
import com.dan.core.raum.Erde;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Die Kimmung (P10b, Werkbuch HalligHooge Teil A, 12.11; F93 A, F94 A, F95 A): Lichtstrahlen laufen durch die Luft
 * über dem Wasser nicht gerade, sondern in Bögen, deren Krümmung das Gefälle der Brechzahl gibt ({@code air.n} aus
 * physics.atmos). Über warmem Wasser biegen flache Strahlen nach oben: Die Kimm sinkt, und darüber steht das
 * Spiegelbild der Ferne (untere Spiegelung). Über kaltem Wasser biegen sie nach unten: Die Ferne hebt sich.
 * <p>
 * <b>Wie es ins Bild kommt (F94 A).</b> Gelände, Wasser und Himmel zeichnen das Bild wie immer, mit geraden Strahlen
 * und einer Erde, die um r² / 2 R′ gesenkt ist (R′ = R / (1 − 0,13): die Normalatmosphäre). Als letzter Teil läuft die
 * Kimmung über den Streifen um den Horizont (± {@link #halb}): Je Bildpunkt folgt sie dem gekrümmten Strahl und sucht,
 * wo er das trifft, was das gerade Bild in derselben Spalte zeigt; von dort nimmt sie die Farbe. Trifft er nichts,
 * zeigt er den Himmel in seiner Richtung am Ende. In der Normalatmosphäre sind die Strahlen im gesenkten Bild gerade,
 * und das Bild bleibt, wie es ist. Grenze: Zu sehen ist nur, was das gerade Bild zeigt – was erst die Krümmung über
 * die Kimm hebt, fehlt.
 * <p>
 * <b>Die Tafel</b> ({@link Tafel}). Die Luft gilt im Blickfeld als waagerecht geschichtet: ein Profil n − 1 über der
 * Höhe über der Fläche (dem Wasser). Für {@link #NB} Sehwinkel zwischen ± halb läuft je ein Strahl vom Auge los; in
 * jeder Schicht des Profils ist das Gefälle der Brechzahl fest und der Strahl eine Parabel, z″ = 1/R + dn/dz (für
 * flache Strahlen; geprüft gegen die Strahlgleichung). Die Tafel hält je Sehwinkel und Entfernung
 * ({@link #ND} Werte, geometrisch von {@link #dNah} bis {@link #dFern}), um wie viel der Strahl im gesenkten Bild
 * über der Geraden liegt, als Tangens: δ = y / d − tan β. Dazu je Sehwinkel: wo der Strahl die Fläche erreicht (die
 * Höhe, ab der sie Strahlen durchlässt, {@link #sperr}; F93 A), wohin er am Ende zeigt und zwischen welchen Werten
 * δ bleibt.
 * <p>
 * Die Tafel wird neu gerechnet, wenn sich das Profil, die Höhe des Auges über der Fläche oder die Sperrhöhe ändert
 * (Millisekunden, {@link #tafelSekunden}).
 */
public final class Kimmung implements Bildteil {
    public static final String VERSION = "0.1";
    /** Sehwinkel und Entfernungen der Tafel [G]. */
    public static final int NB = 1024, ND = 256;
    /** Größte Zahl der Zeilen, die ein Bildpunkt absucht [G]. */
    public static final int FENSTER = 160;
    private static final String PFAD = "/com/dan/render/air/";
    private static final int WERTE = 16, STUECK = 65536;

    /** An oder aus. */
    public boolean an = true;
    /** Halbe Höhe des Streifens um die Waagerechte (Grad) [G]. */
    public double halb = 0.5;
    /** Nächste und fernste Entfernung der Tafel (m) [G]. */
    public double dNah = 50, dFern = 100_000;
    /** Höhe über der Fläche, ab der sie Strahlen durchlässt (m; F93 A: Hs / 2, mindestens 2 cm). */
    public double sperr = 0.02;

    // das Profil: Höhen über der Fläche (m, steigend, die erste 0), n − 1, Höhe der Fläche (m NHN)
    private double[] hoehen, brechung;
    private double flaeche = Double.NaN;
    private boolean neu;
    private Tafel tafel;
    private double tafelAuge, tafelSperr, tafelHalb;
    /** Wie oft die Tafel gerechnet wurde und wie lange das letzte Mal (s). */
    public int tafeln;
    public double tafelSekunden;

    private final Geraet g;
    private final Konto konto;
    private final Kernel kKopie, kKimmung;
    private final Abtaster punkt;
    private final Puffer tafelPuffer, rand, werte;
    private final ByteBuffer tafelDaten, randDaten, werteDaten;
    private Bild kopie;

    public Kimmung(Geraet g, Konto konto) {
        this.g = g;
        this.konto = konto;
        kKopie = Kernel.lade(g, PFAD + "kimmungkopie.comp", 8, null, Kernel.Bindung.BILD, Kernel.Bindung.BILD);
        kKimmung = Kernel.lade(g, PFAD + "kimmung.comp", 8, null, Kernel.Bindung.BILD, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER,
                Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
        punkt = new Abtaster(g, false, false);
        tafelPuffer = Puffer.speicher(g, konto, "Kimmung/Tafel", 4L * NB * ND);
        rand = Puffer.speicher(g, konto, "Kimmung/Rand", 16L * NB);
        werte = Puffer.speicher(g, konto, "Kimmung/Werte", 4L * WERTE);
        tafelDaten = ByteBuffer.allocateDirect(4 * NB * ND).order(ByteOrder.nativeOrder());
        randDaten = ByteBuffer.allocateDirect(16 * NB).order(ByteOrder.nativeOrder());
        werteDaten = ByteBuffer.allocateDirect(4 * WERTE).order(ByteOrder.nativeOrder());
    }

    @Override public String name() { return "Kimmung"; }

    /**
     * Setzt die Luft: das Profil der Brechung über der Fläche und die Höhe der Fläche.
     *
     * @param hoehen   Höhen über der Fläche (m), steigend, die erste 0
     * @param nMinus1  Brechung n − 1 in diesen Höhen
     * @param flaeche  Höhe der Fläche (m NHN): der Wasserstand
     */
    public void luft(double[] hoehen, double[] nMinus1, double flaeche) {
        if (hoehen.length != nMinus1.length || hoehen.length < 2) throw new IllegalArgumentException("Kimmung: " + hoehen.length + " Höhen, " + nMinus1.length + " Werte");
        this.hoehen = hoehen.clone();
        this.brechung = nMinus1.clone();
        this.flaeche = flaeche;
        neu = true;
    }

    /** Ohne Luft: Die Kimmung lässt das Bild, wie es ist. */
    public void ohneLuft() {
        hoehen = brechung = null;
        tafel = null;
    }

    /** Die Tafel des letzten Bilds (null: keine). */
    public Tafel tafel() { return tafel; }

    // ------------------------------------------------------------------ Tafel

    /** Die Tafel der Strahlen für ein Auge; nur java.*, auch ohne Karte zu rechnen. */
    public static final class Tafel {
        public final int nb, nd;
        public final double tanHalb, dNah, dFern, auge, sperr;
        /** δ je Sehwinkel i und Entfernung j: {@code delta[i * nd + j]}. */
        public final float[] delta;
        /** Je Sehwinkel: Entfernung, in der der Strahl die Fläche erreicht (m; −1: nie), δ am Ende, kleinstes und größtes δ. */
        public final float[] rand;

        Tafel(int nb, int nd, double tanHalb, double dNah, double dFern, double auge, double sperr) {
            this.nb = nb;
            this.nd = nd;
            this.tanHalb = tanHalb;
            this.dNah = dNah;
            this.dFern = dFern;
            this.auge = auge;
            this.sperr = sperr;
            delta = new float[nb * nd];
            rand = new float[4 * nb];
        }

        /** Tangens des Sehwinkels der Zeile i. */
        public double tan(int i) { return -tanHalb + 2 * tanHalb * i / (nb - 1); }

        /** Entfernung der Spalte j (m). */
        public double entfernung(int j) { return dNah * Math.pow(dFern / dNah, j / (double) (nd - 1)); }

        /** Zeile zum Tangens eines Sehwinkels (gebrochen). */
        public double zeile(double tan) { return (tan + tanHalb) / (2 * tanHalb) * (nb - 1); }

        /** Spalte zu einer Entfernung (gebrochen, in den Grenzen der Tafel). */
        public double spalte(double d) { return Math.max(0, Math.min(nd - 1, Math.log(Math.max(d, dNah) / dNah) / Math.log(dFern / dNah) * (nd - 1))); }

        /** Entfernung, in der der Strahl der Zeile i die Fläche erreicht (m; −1: nie). */
        public double wasser(int i) { return rand[4 * i]; }

        /**
         * δ zum Tangens des Sehwinkels und zur Entfernung: zwischen den Entfernungen linear, zwischen zwei Zeilen linear,
         * wenn beide die Fläche erreichen oder beide nicht; sonst gilt die nähere Zeile (wie der Kernel).
         */
        public double delta(double tan, double d) {
            double v = Math.max(0, Math.min(nb - 1, zeile(tan)));
            int i0 = (int) Math.min(nb - 2, Math.floor(v));
            if (rand[4 * i0] >= 0 != rand[4 * (i0 + 1)] >= 0) v = Math.rint(v);
            i0 = (int) Math.min(nb - 2, Math.floor(v));
            double fv = v - i0, u = spalte(d);
            int j0 = (int) Math.min(nd - 2, Math.floor(u));
            double fu = u - j0;
            double a = delta[i0 * nd + j0] + (delta[i0 * nd + j0 + 1] - delta[i0 * nd + j0]) * fu;
            double b = delta[(i0 + 1) * nd + j0] + (delta[(i0 + 1) * nd + j0 + 1] - delta[(i0 + 1) * nd + j0]) * fu;
            return a + (b - a) * fv;
        }

        /** Größter Betrag von δ in der ganzen Tafel (0: die Strahlen sind im gesenkten Bild gerade). */
        public double groesstes() {
            double m = 0;
            for (int i = 0; i < nb; i++) m = Math.max(m, Math.max(Math.abs(rand[4 * i + 2]), Math.abs(rand[4 * i + 3])));
            return m;
        }
    }

    /** Ein Strahl im geschichteten Profil: Höhe über der Fläche z und Steigung s = dz/dd in der Entfernung d. */
    private static final class Strahl {
        final double[] z, g;       // Höhen der Stufen, Gefälle dn/dz je Schicht (1/m)
        final double oben;         // Gefälle über der obersten Stufe
        double d, hoehe, steigung;

        Strahl(double[] z, double[] r) {
            this.z = z;
            g = new double[z.length - 1];
            for (int k = 0; k < g.length; k++) g[k] = (r[k + 1] - r[k]) / (z[k + 1] - z[k]);
            oben = -Erde.REFRAKTION / Erde.R;
        }

        int schicht(double h, double s) {
            int n = z.length;
            if (h > z[n - 1] || h == z[n - 1] && s >= 0) return n - 1;           // über der Tabelle
            int k = 0;
            while (k < n - 2 && (h > z[k + 1] || h == z[k + 1] && s >= 0)) k++;
            return k;
        }

        /** Krümmung z″ in der Schicht k (k = Zahl der Schichten: über der Tabelle). */
        double kruemmung(int k) { return 1 / Erde.R + (k >= g.length ? oben : g[k]); }
    }

    /** Kleinste Lösung t > eps von h + s t + c t² / 2 = ziel; unendlich, wenn es keine gibt. */
    private static double bis(double h, double s, double c, double ziel, double eps) {
        double a = 0.5 * c, b = s, e = h - ziel, best = Double.POSITIVE_INFINITY;
        if (Math.abs(a) < 1e-300) {
            if (b != 0) {
                double t = -e / b;
                if (t > eps) best = t;
            }
            return best;
        }
        double D = b * b - 4 * a * e;
        if (D < 0) return best;
        double w = Math.sqrt(D), q = -0.5 * (b + (b >= 0 ? w : -w));
        double t1 = q / a, t2 = q != 0 ? e / q : Double.NaN;
        if (t1 > eps) best = t1;
        if (t2 > eps && t2 < best) best = t2;
        return best;
    }

    /**
     * Rechnet die Tafel.
     *
     * @param z     Höhen des Profils über der Fläche (m), steigend, die erste 0
     * @param r     n − 1 in diesen Höhen
     * @param auge  Höhe des Auges über der Fläche (m), größer als die Sperrhöhe
     * @param sperr Höhe über der Fläche, ab der sie Strahlen durchlässt (m)
     * @param halb  halbe Höhe des Streifens (Grad)
     */
    public static Tafel tafel(double[] z, double[] r, double auge, double sperr, double halb, double dNah, double dFern, int nb, int nd) {
        Tafel t = new Tafel(nb, nd, Math.tan(Math.toRadians(halb)), dNah, dFern, auge, sperr);
        double[] dj = new double[nd];
        for (int j = 0; j < nd; j++) dj[j] = t.entfernung(j);
        double rOpt = Erde.R_OPTISCH;
        int oberste = z.length - 1;
        for (int i = 0; i < nb; i++) {
            double tan = t.tan(i);
            Strahl st = new Strahl(z, r);
            st.hoehe = auge;
            st.steigung = tan;
            double wasser = -1, min = 0, max = 0, ende = 0;
            int j = 0;
            boolean unten = false;                       // unter der Fläche: gerade weiter
            for (int schritt = 0; schritt < 100_000 && j < nd; schritt++) {
                int k = unten ? -1 : st.schicht(st.hoehe, st.steigung);
                double c = unten ? 0 : st.kruemmung(k);
                if (!unten && k >= oberste && st.steigung >= 0) {
                    // über der Tabelle nach oben: bleibt dort; im gesenkten Bild gerade
                    double sFlach = st.steigung - st.d / rOpt, y0 = st.hoehe - auge - st.d * st.d / (2 * rOpt);
                    for (; j < nd; j++) {
                        double y = y0 + sFlach * (dj[j] - st.d), dl = y / dj[j] - tan;
                        t.delta[i * nd + j] = (float) dl;
                        min = Math.min(min, dl);
                        max = Math.max(max, dl);
                    }
                    ende = sFlach - tan;
                    break;
                }
                // bis zur nächsten Grenze der Schicht
                double weit = Double.POSITIVE_INFINITY, grenze = Double.NaN;
                if (!unten) {
                    double u = z[Math.min(k, oberste)], tu = bis(st.hoehe, st.steigung, c, u, 1e-9);
                    weit = tu;
                    grenze = u;
                    if (k < oberste) {
                        double to = bis(st.hoehe, st.steigung, c, z[k + 1], 1e-9);
                        if (to < weit) {
                            weit = to;
                            grenze = z[k + 1];
                        }
                    }
                    if (wasser < 0 && st.hoehe > sperr) {
                        double ws = bis(st.hoehe, st.steigung, c, sperr, 0);
                        if (ws <= weit) wasser = st.d + ws;
                    }
                }
                double bisD = st.d + weit;
                // Entfernungen der Tafel in diesem Stück
                while (j < nd && dj[j] <= bisD) {
                    double x = dj[j] - st.d, h = st.hoehe + st.steigung * x + 0.5 * c * x * x;
                    double y = h - auge - dj[j] * dj[j] / (2 * rOpt), dl = y / dj[j] - tan;
                    t.delta[i * nd + j] = (float) dl;
                    if (wasser < 0 || dj[j] <= wasser) {
                        min = Math.min(min, dl);
                        max = Math.max(max, dl);
                    }
                    j++;
                }
                if (j >= nd) {
                    // Richtung am Ende der Tafel: Steigung im gesenkten Bild
                    ende = st.steigung + c * (dFern - st.d) - dFern / rOpt - tan;
                    break;
                }
                st.steigung += c * weit;
                st.hoehe = grenze;                     // genau auf der Grenze, damit die Schicht eindeutig ist
                st.d = bisD;
                if (grenze == z[0] && st.steigung < 0) unten = true;
            }
            if (wasser < 0) {
                min = Math.min(min, ende);
                max = Math.max(max, ende);
            }
            t.rand[4 * i] = (float) wasser;
            t.rand[4 * i + 1] = (float) (wasser < 0 ? ende : 0);
            t.rand[4 * i + 2] = (float) min;
            t.rand[4 * i + 3] = (float) max;
        }
        return t;
    }

    // ------------------------------------------------------------------ der Durchgang auf dem Prozessor

    /**
     * Derselbe Durchgang wie {@code kimmung.comp}, auf dem Prozessor (zur Prüfung): aus dem geraden Bild (HDR, je
     * Bildpunkt vier Werte, und Tiefe = nah / Abstand längs der Blickachse, 0 = Himmel) das Bild mit Kimmung.
     * Zeilen außerhalb des Streifens bleiben.
     *
     * @return das neue HDR-Bild
     */
    public static float[] durchgang(Tafel t, Blick b, float[] hdr, float[] tiefe, int breite, int hoehe) {
        float[] aus = hdr.clone();
        Geometrie q = new Geometrie(b, breite, hoehe);
        int[] zeilen = q.streifen(t.tanHalb);
        for (int py = zeilen[0]; py <= zeilen[1]; py++)
            for (int px = 0; px < breite; px++) {
                double[] ziel = punkt(t, q, tiefe, px, py);
                if (ziel == null) continue;
                // Farbe aus der Zeile ziel[0] (gebrochen), linear zwischen zwei Zeilen
                double y = Math.max(0, Math.min(hoehe - 1, ziel[0]));
                int y0 = (int) Math.min(hoehe - 2, Math.floor(y));
                double f = y - y0;
                for (int c = 0; c < 4; c++)
                    aus[4 * (py * breite + px) + c] = (float) (hdr[4 * (y0 * breite + px) + c] * (1 - f) + hdr[4 * ((y0 + 1) * breite + px) + c] * f);
            }
        return aus;
    }

    /** Der Blick für den Durchgang: Richtung eines Bildpunkts, Zeile zu einem Tangens. */
    public static final class Geometrie {
        final double[] r, u, f;
        final double ax, ay, nah;
        final int breite, hoehe;

        public Geometrie(Blick b, int breite, int hoehe) {
            double[][] bas = b.basis();
            r = bas[0];
            u = bas[1];
            f = bas[2];
            double tt = 1 / Math.tan(Math.toRadians(b.bildwinkel) / 2);
            ax = (double) breite / hoehe / tt;
            ay = 1 / tt;
            nah = b.nah;
            this.breite = breite;
            this.hoehe = hoehe;
        }

        /** Richtung (nicht normiert) durch die Mitte des Bildpunkts (px, py); py darf gebrochen sein (Mitte bei ,5 − ,5). */
        double[] richtung(double px, double py) {
            double nx = (px + 0.5) / breite * 2 - 1, ny = (py + 0.5) / hoehe * 2 - 1;
            return new double[] {f[0] + r[0] * nx * ax - u[0] * ny * ay, f[1] + r[1] * nx * ax - u[1] * ny * ay, f[2] + r[2] * nx * ax - u[2] * ny * ay};
        }

        /** Zeile (gebrochen, Mitte eines Bildpunkts bei ganzen Zahlen), in der die Spalte px den Tangens tan der Höhe zeigt. */
        public double zeile(double px, double tan) {
            double nx = (px + 0.5) / breite * 2 - 1, X = nx * ax;
            // Richtung = f + r X − u Y; waagerecht |f_w + r X − u_w Y|, senkrecht f_y − u_y Y
            double Y = 0;
            for (int it = 0; it < 3; it++) {
                double wx = f[0] + r[0] * X - u[0] * Y, wz = f[2] + r[2] * X - u[2] * Y;
                Y = (f[1] - tan * Math.sqrt(wx * wx + wz * wz)) / u[1];
            }
            return (Y / ay + 1) / 2 * hoehe - 0.5;
        }

        /** Erste und letzte Zeile des Streifens ± tanHalb (in den Grenzen des Bilds; leer: erste > letzte). */
        public int[] streifen(double tanHalb) {
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (double px : new double[] {0, 0.5 * (breite - 1), breite - 1})
                for (double tan : new double[] {-tanHalb, tanHalb}) {
                    double y = zeile(px, tan);
                    lo = Math.min(lo, y);
                    hi = Math.max(hi, y);
                }
            return new int[] {(int) Math.max(0, Math.floor(lo) - 1), (int) Math.min(hoehe - 1, Math.ceil(hi) + 1)};
        }
    }

    /**
     * Wo der gekrümmte Strahl durch den Bildpunkt (px, py) das gerade Bild trifft: {Zeile (gebrochen), Art}; null: der
     * Bildpunkt bleibt. Art 1: Gelände oder Wasser getroffen, 2: die Fläche erreicht, 3: Himmel, 4: hinter der Kimm.
     */
    public static double[] punkt(Tafel t, Geometrie q, float[] tiefe, int px, int py) {
        double[] v = q.richtung(px, py);
        double tan = v[1] / Math.hypot(v[0], v[2]);
        if (Math.abs(tan) >= t.tanHalb) return null;
        int i = (int) Math.max(0, Math.min(t.nb - 1, Math.rint(t.zeile(tan))));
        double dW = t.rand[4 * i], ende = t.rand[4 * i + 1], min = t.rand[4 * i + 2], max = t.rand[4 * i + 3];
        // Zeilen je Einheit des Tangens: Der Bildpunkt bleibt, wenn der Strahl nirgends ein Viertel einer Zeile abweicht
        double jeTan = q.hoehe / (2 * q.ay);
        if (Math.max(Math.abs(min), Math.abs(max)) * jeTan < 0.25) return null;
        int unten = (int) Math.min(q.hoehe - 1, Math.ceil(q.zeile(px, tan + min)) + 1), oben = (int) Math.max(0, Math.floor(q.zeile(px, tan + max)) - 1);
        if (unten - oben > FENSTER) {
            // zu viele Zeilen: das Fenster um die eigene Zeile legen
            unten = Math.min(unten, py + FENSTER / 2);
            oben = Math.max(oben, unten - FENSTER);
        }
        double rOpt2 = 2 * Erde.R_OPTISCH;
        int vor = -1;
        double sVor = 0;
        boolean himmel = false;
        for (int rr = unten; rr >= oben; rr--) {
            float D = tiefe[rr * q.breite + px];
            if (D <= 0) {
                himmel = true;
                break;
            }
            double[] w = q.richtung(px, rr);
            double wag = Math.hypot(w[0], w[2]), d = q.nah / D / (w[0] * q.f[0] + w[1] * q.f[1] + w[2] * q.f[2]) * wag;
            if (dW >= 0 && d >= dW) return new double[] {grenzen(q.zeile(px, (t.sperr - t.auge) / dW - dW / rOpt2), py, q.hoehe), 2};
            // Höhe des Strahls über dem, was das gerade Bild in der Entfernung d zeigt
            double s = (tan - w[1] / wag + t.delta(tan, d)) * d - t.sperr;
            if (s <= 0) return new double[] {vor < 0 ? rr : vor + sVor / (sVor - s) * (rr - vor), 1};
            vor = rr;
            sVor = s;
        }
        if (dW >= 0) return vor < 0 ? null : new double[] {himmel ? vor : oben, 4};
        return new double[] {grenzen(q.zeile(px, tan + ende), py, q.hoehe), 3};
    }

    /** Die Zeile in den Grenzen, die die Kopie des Bilds deckt: um die eigene Zeile ± {@link #FENSTER}, im Bild. */
    private static double grenzen(double zeile, int py, int hoehe) { return Math.max(Math.max(0, py - FENSTER), Math.min(Math.min(hoehe - 1, py + FENSTER), zeile)); }

    // ------------------------------------------------------------------ je Bild

    /** Ob die Kimmung dieses Bild ändert: an, mit Luft, das Auge über der Sperrhöhe. */
    private boolean bereit(Bildziel z) {
        if (!an || hoehen == null || Double.isNaN(flaeche)) return false;
        double auge = z.blick.hoehe - flaeche;
        return auge > Math.max(0.2, 3 * sperr) && auge < 5000;
    }

    @Override public void vorbereiten(Ablauf a, Bildziel z) {
        if (!bereit(z)) {
            tafel = null;
            return;
        }
        double auge = z.blick.hoehe - flaeche;
        if (tafel == null || neu || Math.abs(auge - tafelAuge) > 0.005 || sperr != tafelSperr || halb != tafelHalb) {
            long n0 = System.nanoTime();
            tafel = tafel(hoehen, brechung, auge, sperr, halb, dNah, dFern, NB, ND);
            tafelSekunden = (System.nanoTime() - n0) * 1e-9;
            tafeln++;
            tafelAuge = auge;
            tafelSperr = sperr;
            tafelHalb = halb;
            neu = false;
            // hoch in Stücken zu 64 KB (mehr nimmt vkCmdUpdateBuffer nicht), im Ablauf dieses Bilds: Niemand wartet
            tafelDaten.clear();
            tafelDaten.asFloatBuffer().put(tafel.delta);
            randDaten.clear();
            randDaten.asFloatBuffer().put(tafel.rand);
            a.durchgang("Kimmung, Tafel", new Ablauf.Zugriff[] {new Ablauf.Zugriff(tafelPuffer, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                    new Ablauf.Zugriff(rand, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
                for (int ab = 0; ab < tafelDaten.capacity(); ab += STUECK) {
                    ByteBuffer teil = tafelDaten.duplicate().order(ByteOrder.nativeOrder());
                    teil.position(ab).limit(Math.min(tafelDaten.capacity(), ab + STUECK));
                    vkCmdUpdateBuffer(c, tafelPuffer.handle, ab, teil.slice());
                }
                ByteBuffer rd = randDaten.duplicate();
                rd.position(0).limit(randDaten.capacity());
                vkCmdUpdateBuffer(c, rand.handle, 0, rd);
            });
        }
        Blick b = z.blick;
        double tt = 1 / Math.tan(Math.toRadians(b.bildwinkel) / 2);
        werteDaten.clear();
        werteDaten.putFloat((float) tafel.tanHalb).putFloat((float) dNah).putFloat((float) Math.log(dFern / dNah)).putFloat((float) auge);
        werteDaten.putFloat((float) sperr).putFloat((float) (1 / (2 * Erde.R_OPTISCH))).putFloat((float) (z.hoehe * tt / 2)).putFloat(FENSTER);
        werteDaten.putFloat(NB).putFloat(ND);
        while (werteDaten.position() < werteDaten.capacity()) werteDaten.putFloat(0);
        werteDaten.flip();
        a.durchgang("Kimmung, Werte", new Ablauf.Zugriff[] {new Ablauf.Zugriff(werte, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, werte.handle, 0, werteDaten));
    }

    @Override public void zeichnen(Ablauf a, Bildziel z) {
        if (tafel == null) return;
        if (kopie == null || kopie.breite != z.breite || kopie.hoehe != z.hoehe) {
            if (kopie != null) {
                g.ruhe();
                kopie.close();
            }
            kopie = new Bild(g, konto, "Kimmung/Kopie", z.breite, z.hoehe, Bild.RGBA16F);
        }
        Geometrie q = new Geometrie(z.blick, z.breite, z.hoehe);
        int[] zeilen = q.streifen(tafel.tanHalb);
        if (zeilen[0] > zeilen[1]) return;
        // die Kopie reicht über den Streifen hinaus: Dort sucht der Durchgang
        int k0 = Math.max(0, zeilen[0] - FENSTER), k1 = Math.min(z.hoehe - 1, zeilen[1] + FENSTER);
        try (MemoryStack s = stackPush()) {
            ByteBuffer pk = s.malloc(8).putInt(0, k0).putInt(4, k1);
            a.rechne("Kimmung, Kopie", kKopie, kKopie.satz(z.hdr, kopie), pk, Kernel.gruppen(z.breite, 8), Kernel.gruppen(k1 - k0 + 1, 8), 1, Ablauf.schreibt(z.hdr, kopie));
            ByteBuffer pz = s.malloc(8).putInt(0, zeilen[0]).putInt(4, zeilen[1]);
            Puffer hw = z.puffer(Himmel.WERTE);
            a.rechne("Kimmung", kKimmung, kKimmung.satz(z.hdr, punkt.an(kopie), punkt.an(z.tiefe), tafelPuffer, rand, werte, hw), pz, Kernel.gruppen(z.breite, 8),
                    Kernel.gruppen(zeilen[1] - zeilen[0] + 1, 8), 1,
                    Ablauf.und(Ablauf.schreibt(z.hdr), Ablauf.textur(Ablauf.RECHNEN, kopie, z.tiefe), Ablauf.liest(Ablauf.RECHNEN, tafelPuffer, rand, werte, hw)));
        }
    }

    @Override public void close() {
        kKopie.close();
        kKimmung.close();
        punkt.close();
        tafelPuffer.close();
        rand.close();
        werte.close();
        if (kopie != null) kopie.close();
    }
}
