package com.dan.gertsch.gl;

import com.dan.gertsch.Dickicht;
import com.dan.gertsch.Kamera;
import com.dan.gertsch.Lab;
import com.dan.gertsch.Licht;
import com.dan.gertsch.Palette;
import com.dan.gertsch.Stufen;
import com.dan.gertsch.Vorlage;
import com.dan.gertsch.Vorlagen;
import com.dan.gertsch.Wind;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Die Malweise auf der Grafikkarte (OpenGL 4.3): dasselbe {@link Dickicht} wie {@link com.dan.gertsch.Malweise}, aber
 * jedes Bild neu und im {@link Wind}. Je Bild:
 * <ol>
 * <li><b>Schattenkarten</b> in zwei Stufen (im Wind jedes Bild, sonst wenn die Kamera sich bewegt): nah für die
 * Empfänger bis {@link #nahBis} vor der Kamera, fern für alle im Bild. Ein Werfer liegt entlang der Sonne über seinem
 * Empfänger; darum reicht das Rechteck der Empfänger im Bildkegel, und die Karte wird fein. {@code auswahl.comp} wählt
 * die Blätter entlang der Sonne (flächentreu zusammengefasst nach Texeln), {@code fleck.vert/.frag} zeichnet sie in
 * ihrer Blattform als Tiefe, dazu Stämme und Äste ({@code glied.*}).</li>
 * <li><b>Auswahl</b> für die Ansicht ({@code auswahl.comp}, je Zweig ein Faden): Größenregel wie in Malweise, Blatt aus
 * dem seed ({@link com.dan.gertsch.Zufall}, in GLSL gleich), Wind, Licht in der Mitte des Blatts (Schattenkarte,
 * Himmel aus der Blattfläche als 3D-Textur), Band der Tonwerte mit <b>Hysterese</b> (je Blatt ein Byte), Stufe, Akzent;
 * jeder Fleck an eine Liste für {@code glDrawArraysIndirect}.</li>
 * <li><b>Bild</b> mit Mehrfachabtastung: Boden, Stämme und Halme mit Licht je Bildpunkt (die Lichtflecken haben die Form
 * der Lücken im Laub), Flecken flach in ihrer Stufe, Kanten über die Abdeckung. Zweites Ziel: die Helligkeit Y.</li>
 * <li><b>Tonwerte nach der Vorlage:</b> {@code histogramm.comp} zählt Y; aus dem Histogramm des vorigen Bilds rechnet
 * {@link Stufen#grenzen} die Grenzen der Bänder, so dass jede Stufe ihren Anteil der Fläche bekommt.</li>
 * </ol>
 * Shader: {@code gertsch.glsl} (vor jedem), {@code auswahl.comp}, {@code fleck.vert}, {@code fleck.frag},
 * {@code glied.vert}, {@code glied.frag}, {@code halm.vert}, {@code halm.frag}, {@code boden.vert}, {@code boden.frag},
 * {@code histogramm.comp}; für GertschGlCheck {@code pruefen.comp}.
 */
public final class GertschGl {
    /** Qualität: Bildbreite, Mehrfachabtastung, Schattenkarte, Größe der Listen. */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    public Quality quality = Quality.HIGH;
    public Vorlage vorlage = Vorlagen.sommer();
    public final Wind wind = new Wind();
    /** Kleinste Fleckgröße (Anteil der Bildbreite) wie in Malweise; Hysterese (Blenden; 0 = aus): 0,3 drittelt bis viertelt das Flimmern im Wind. */
    public double fleck = 0.003, hysterese = 0.3;
    public double farbGewicht = 0.12, durchscheinen = 0.5, akzentBuntheit = 12, akzentWinkel = 40;
    public double seitenverhaeltnis = 2000 / 1328.0;
    /** Nah- und Fernebene (m). */
    public double nah = 0.3, fern = 250;
    /** Die nahe Schattenkarte gilt für Empfänger bis so weit vor der Kamera (m). */
    public double nahBis = 22;

    /** Bereich des Histogramms in log2 Y. */
    static final double LO = -24, HI = 4;
    /** Stufen der Schattenkarten: fern und nah. */
    static final int FERN = 0, NAH = 1;

    public int breite() { return switch (quality) { case LOW -> 640; case MEDIUM -> 1000; case HIGH -> 1600; case ULTRA -> 2400; }; }
    public int hoehe() { return (int) Math.round(breite() / seitenverhaeltnis); }
    int abtastungen() { return switch (quality) { case LOW -> 2; case MEDIUM, HIGH -> 4; case ULTRA -> 8; }; }
    int schattenKante(int s) {
        return s == NAH ? switch (quality) { case LOW -> 1024; case MEDIUM, HIGH -> 2048; case ULTRA -> 4096; }
                : switch (quality) { case LOW, MEDIUM -> 1024; case HIGH, ULTRA -> 2048; };   // fern reicht 2048: dort sind die Bildpunkte groß
    }
    /** Kleinste Größe eines Blatts in der Schattenkarte (Texel): kleinere werden flächentreu zusammengefasst. */
    double schattenZiel(int s) {
        // die Werfer kosten im Wind jedes Bild: flächentreu gröber zusammengefasst, die Lücken bleiben im Mittel gleich
        return s == NAH ? switch (quality) { case LOW -> 8; case MEDIUM -> 6; case HIGH -> 5; case ULTRA -> 3; }
                : switch (quality) { case LOW -> 10; case MEDIUM -> 8; case HIGH -> 6; case ULTRA -> 3; };
    }
    /** Die Zahl der Flecken hängt kaum von der Auflösung ab (die Größenregel wächst mit): etwa 1,6 Mio. im Sommer-Dickicht. */
    int kapAnsicht() { return switch (quality) { case LOW -> 2_500_000; case MEDIUM -> 3_000_000; case HIGH -> 4_000_000; case ULTRA -> 6_000_000; }; }
    int kapSonne(int s) {
        // nah braucht jeder Werfer über den Empfängern seine volle Größe (die Texel sind kleiner als ein Blatt): bis alle Blätter
        return s == NAH ? switch (quality) { case LOW -> 4_000_000; case MEDIUM -> 8_000_000; case HIGH -> 14_000_000; case ULTRA -> 18_000_000; }
                : switch (quality) { case LOW -> 2_000_000; case MEDIUM -> 4_000_000; case HIGH -> 7_000_000; case ULTRA -> 10_000_000; };
    }

    // Programme
    private int pAuswahl, pFleck, pGlied, pHalm, pBoden, pHisto, vao;
    private final Map<Integer, Map<String, Integer>> orte = new HashMap<>();
    // Puffer
    private int bZweige, bGlieder, bHalme, bListeA, bBefehlA, bHyst;
    private final int[] bListeS = new int[2], bBefehlS = new int[2], bHisto = new int[3];
    // Ziele
    private int tLai, fMsaa, rbFarbe, rbY, rbTiefe, fAufl, tFarbe, tY;
    private final int[] tSchatten = new int[2], fSchatten = new int[2], schattenN = new int[2], kapS = new int[2];
    private int w, h, abt, kapA;
    private Quality geladen;
    // Szene
    private Dickicht dickicht;
    private Licht licht;
    private int zweigZahl, gliedZahl, halmZahl;
    private final float[][] sonneM = new float[2][16];
    private final double[] texelProM = new double[2];
    private double schattenTiefe;
    /** Achsen entlang der Sonne (u, v quer, w zur Sonne) und Tiefe w der Szene. */
    private final double[] bu = new double[3], bv = new double[3];
    private double wMin, wMax;
    private float[] laiUrsprung = {0, 0, 0}, laiZahl = {1, 1, 1};
    private boolean schattenFertig;
    private float[] bodenRect = {-80, 0.3f, 80, 80};
    // Tonwerte
    private double[] grenzen;
    private double belichtung = 1;
    private long bild;
    // Kennzahlen (nach statistik())
    public volatile int instanzen, schattenInstanzen;
    public volatile long gewollt, schattenGewollt, wechsel;

    // ------------------------------------------------------------------ Aufbau

    public void init(GL4 gl) {
        String g = source("gertsch.glsl");
        pAuswahl = rechnen(gl, g, "auswahl.comp");
        pHisto = rechnen(gl, "", "histogramm.comp");
        pFleck = link(gl, g, "fleck.vert", "fleck.frag");
        pGlied = link(gl, g, "glied.vert", "glied.frag");
        pHalm = link(gl, g, "halm.vert", "halm.frag");
        pBoden = link(gl, g, "boden.vert", "boden.frag");
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        int[] b = new int[13];
        gl.glGenBuffers(13, b, 0);
        bZweige = b[0]; bGlieder = b[1]; bHalme = b[2]; bListeA = b[3]; bListeS[0] = b[4]; bListeS[1] = b[5]; bBefehlA = b[6];
        bBefehlS[0] = b[7]; bBefehlS[1] = b[8]; bHyst = b[9]; bHisto[0] = b[10]; bHisto[1] = b[11]; bHisto[2] = b[12];
        for (int hb : bHisto) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, hb);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 257L * 4, null, GL4.GL_DYNAMIC_READ);
        }
        for (int bb : new int[]{bBefehlA, bBefehlS[0], bBefehlS[1]}) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bb);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 32, null, GL4.GL_DYNAMIC_DRAW);
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Lädt das Dickicht (Zweige, Glieder, Halme, Plätze der Hysterese) und das Licht (Richtung, Farben, Blattfläche als
     * 3D-Textur, Rechteck der Schattenkarte entlang der Sonne). {@code licht.vorbereiten(d)} muss gelaufen sein.
     */
    public void laden(GL4 gl, Dickicht d, Licht l) {
        dickicht = d;
        licht = l;
        zweigZahl = d.zweige.size();
        ByteBuffer zb = ByteBuffer.allocateDirect(32 * zweigZahl).order(ByteOrder.nativeOrder());
        for (Dickicht.Zweig z : d.zweige) {
            if (z.blaetter > 255) throw new IllegalStateException("gertsch: Zweig mit " + z.blaetter + " Blättern (höchstens 255)");
            zb.putFloat(z.x).putFloat(z.y).putFloat(z.z).putFloat(z.radius);
            zb.putInt(z.blaetter | (z.boden ? 1 << 16 : 0)).putInt(z.seed).putInt(z.platz);
            zb.putInt((Float.floatToFloat16(z.laenge) & 0xFFFF) | (Float.floatToFloat16(z.schmal) << 16));
        }
        zb.flip();
        hochladen(gl, bZweige, zb, GL4.GL_STATIC_DRAW);
        gliedZahl = d.glieder.size();
        FloatBuffer gb = Buffers.newDirectFloatBuffer(12 * Math.max(1, gliedZahl));
        for (Dickicht.Glied g : d.glieder)
            gb.put(g.x0).put(g.y0).put(g.z0).put(g.r0).put(g.x1).put(g.y1).put(g.z1).put(g.r1).put(g.r).put(g.g).put(g.b).put(g.stamm ? 1 : 0);
        gb.flip();
        hochladen(gl, bGlieder, gb, GL4.GL_STATIC_DRAW);
        halmZahl = d.halmListe.size();
        FloatBuffer hb = Buffers.newDirectFloatBuffer(20 * Math.max(1, halmZahl));
        int hi = 0;
        for (Dickicht.Halm hm : d.halmListe) {
            float[] w4 = {hm.breite, hm.r, hm.g, hm.b, (Integer.remainderUnsigned(com.dan.gertsch.Zufall.pcg(hi++), 1000) / 1000f)};
            for (int k = 0; k < 5; k++) hb.put(hm.p[3 * k]).put(hm.p[3 * k + 1]).put(hm.p[3 * k + 2]).put(w4[k]);
        }
        hb.flip();
        hochladen(gl, bHalme, hb, GL4.GL_STATIC_DRAW);
        // Hysterese: ein Byte je Platz, 0xFF = noch kein Band
        IntBuffer hy = Buffers.newDirectIntBuffer(Math.max(1, d.plaetze / 4));
        while (hy.hasRemaining()) hy.put(-1);
        hy.flip();
        hochladen(gl, bHyst, hy, GL4.GL_DYNAMIC_COPY);
        // Blattfläche als 3D-Textur
        float[] lai = l.laiFeld();
        int[] n = l.laiZahl();
        double[] u0 = l.laiUrsprung();
        if (tLai == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            tLai = t[0];
        }
        gl.glBindTexture(GL4.GL_TEXTURE_3D, tLai);
        if (lai == null) { lai = new float[]{0}; n = new int[]{1, 1, 1}; }
        gl.glTexImage3D(GL4.GL_TEXTURE_3D, 0, GL4.GL_R32F, n[0], n[1], n[2], 0, GL4.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(lai));
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        for (int p : new int[]{GL.GL_TEXTURE_WRAP_S, GL.GL_TEXTURE_WRAP_T, GL4.GL_TEXTURE_WRAP_R})
            gl.glTexParameteri(GL4.GL_TEXTURE_3D, p, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, 0);
        laiUrsprung = new float[]{(float) u0[0], 0, (float) u0[2]};
        laiZahl = new float[]{n[0], n[1], n[2]};
        sonneAchsen();
        bodenRect = new float[]{(float) (-d.breite), 0.3f, (float) d.breite, (float) (d.fern * 1.3)};
        schattenFertig = false;
        kastenKamera = null;
        grenzen = null;
        bild = 0;
    }

    /** Achsen entlang der Sonne (wie Licht.schatten) und die Tiefe w der ganzen Szene (alle Werfer). */
    private void sonneAchsen() {
        double[] w0 = licht.zurSonne;
        double ux = w0[2], uz = -w0[0], l = Math.hypot(ux, uz);
        if (l < 1e-6) { ux = 1; uz = 0; l = 1; }
        bu[0] = ux / l; bu[1] = 0; bu[2] = uz / l;
        bv[0] = w0[1] * bu[2] - w0[2] * bu[1];
        bv[1] = w0[2] * bu[0] - w0[0] * bu[2];
        bv[2] = w0[0] * bu[1] - w0[1] * bu[0];
        double mw = 1e30, xw = -1e30;
        for (Dickicht.Zweig z : dickicht.zweige) {
            double ww = z.x * w0[0] + z.y * w0[1] + z.z * w0[2];
            mw = Math.min(mw, ww); xw = Math.max(xw, ww);
        }
        for (Dickicht.Glied g : dickicht.glieder) {
            mw = Math.min(mw, Math.min(g.x0 * w0[0] + g.y0 * w0[1] + g.z0 * w0[2], g.x1 * w0[0] + g.y1 * w0[1] + g.z1 * w0[2]));
            xw = Math.max(xw, Math.max(g.x0 * w0[0] + g.y0 * w0[1] + g.z0 * w0[2], g.x1 * w0[0] + g.y1 * w0[1] + g.z1 * w0[2]));
        }
        wMin = Math.min(mw, 0) - 2;   // der Boden (Empfänger) liegt auch darunter
        wMax = xw + 2;
        schattenTiefe = wMax - wMin;
    }

    private double[] kastenKamera;

    /**
     * Rechtecke der beiden Schattenkarten aus dem Bildkegel: die Empfänger zwischen {@link #nah} und {@link #nahBis}
     * (nah) bzw. bis zur Tiefe des Dickichts (fern), in den Achsen entlang der Sonne. Gibt true zurück, wenn sie sich
     * geändert haben (dann muss die Karte neu).
     */
    private boolean kaskaden(Kamera k) {
        double[] auge = k.auge, vorn = k.vorn();
        double[] schluessel = {auge[0], auge[1], auge[2], vorn[0], vorn[1], vorn[2], k.bildwinkel, w, h, quality.ordinal()};
        if (kastenKamera != null && java.util.Arrays.equals(schluessel, kastenKamera)) return false;
        kastenKamera = schluessel;
        double[] r = new double[3];
        for (int s = 0; s < 2; s++) {
            double t0 = nah, t1 = s == NAH ? nahBis : dickicht.fern * 1.3;
            double mu = 1e30, xu = -1e30, mv = 1e30, xv = -1e30;
            for (double t : new double[]{t0, t1})
                for (int c = 0; c < 4; c++) {
                    k.strahl((c & 1) == 0 ? 0 : w, (c & 2) == 0 ? 0 : h, r);
                    double d = t / Math.max(1e-6, r[0] * vorn[0] + r[1] * vorn[1] + r[2] * vorn[2]);
                    double x = auge[0] + r[0] * d, y = Math.max(0, auge[1] + r[1] * d), z = auge[2] + r[2] * d;
                    double u = x * bu[0] + y * bu[1] + z * bu[2], v = x * bv[0] + y * bv[1] + z * bv[2];
                    mu = Math.min(mu, u); xu = Math.max(xu, u); mv = Math.min(mv, v); xv = Math.max(xv, v);
                }
            mu -= 1; xu += 1; mv -= 1; xv += 1;
            double ext = Math.max(xu - mu, xv - mv), cu = (mu + xu) / 2, cv = (mv + xv) / 2;
            double[] w0 = licht.zurSonne;
            // Zeilen: x = 2(u − cu)/ext, y = 2(v − cv)/ext, z = −(2w − (wMax + wMin))/(wMax − wMin) (näher zur Sonne = kleiner)
            double[][] z = {
                    {2 * bu[0] / ext, 2 * bu[1] / ext, 2 * bu[2] / ext, -2 * cu / ext},
                    {2 * bv[0] / ext, 2 * bv[1] / ext, 2 * bv[2] / ext, -2 * cv / ext},
                    {-2 * w0[0] / schattenTiefe, -2 * w0[1] / schattenTiefe, -2 * w0[2] / schattenTiefe, (wMax + wMin) / schattenTiefe},
                    {0, 0, 0, 1}};
            for (int a = 0; a < 4; a++)
                for (int c = 0; c < 4; c++) sonneM[s][c * 4 + a] = (float) z[a][c];
            texelProM[s] = schattenKante(s) / ext;
        }
        return true;
    }

    private void ziele(GL4 gl) {
        int nw = breite(), nh = hoehe(), na = abtastungen();
        if (nw == w && nh == h && na == abt && fMsaa != 0 && geladen == quality) return;
        loeschenZiele(gl);
        w = nw; h = nh; abt = na; geladen = quality;
        schattenN[FERN] = schattenKante(FERN);
        schattenN[NAH] = schattenKante(NAH);
        int[] t = new int[3];
        gl.glGenRenderbuffers(3, t, 0);
        rbFarbe = t[0]; rbY = t[1]; rbTiefe = t[2];
        gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, rbFarbe);
        gl.glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, abt, GL.GL_RGBA8, w, h);
        gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, rbY);
        gl.glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, abt, GL4.GL_R32F, w, h);
        gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, rbTiefe);
        gl.glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, abt, GL.GL_DEPTH_COMPONENT24, w, h);
        int[] f = new int[4];
        gl.glGenFramebuffers(4, f, 0);
        fMsaa = f[0]; fAufl = f[1]; fSchatten[0] = f[2]; fSchatten[1] = f[3];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fMsaa);
        gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_RENDERBUFFER, rbFarbe);
        gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL4.GL_COLOR_ATTACHMENT1, GL.GL_RENDERBUFFER, rbY);
        gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_RENDERBUFFER, rbTiefe);
        pruefen(gl, "Bild");
        int[] tx = new int[4];
        gl.glGenTextures(4, tx, 0);
        tFarbe = tx[0]; tY = tx[1]; tSchatten[0] = tx[2]; tSchatten[1] = tx[3];
        textur(gl, tFarbe, GL.GL_RGBA8, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, w, h);
        textur(gl, tY, GL4.GL_R32F, GL4.GL_RED, GL.GL_FLOAT, w, h);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fAufl);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tFarbe, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL4.GL_COLOR_ATTACHMENT1, GL.GL_TEXTURE_2D, tY, 0);
        pruefen(gl, "Auflösung");
        for (int s = 0; s < 2; s++) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, tSchatten[s]);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT32F, schattenN[s], schattenN[s], 0, GL4.GL_DEPTH_COMPONENT, GL.GL_FLOAT, null);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_MODE, GL4.GL_COMPARE_REF_TO_TEXTURE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_FUNC, GL.GL_LEQUAL);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fSchatten[s]);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, tSchatten[s], 0);
            gl.glDrawBuffer(GL.GL_NONE);
            gl.glReadBuffer(GL.GL_NONE);
            pruefen(gl, "Schatten");
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        kapA = kapAnsicht();
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bListeA);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 16L * kapA, null, GL4.GL_DYNAMIC_COPY);
        for (int s = 0; s < 2; s++) {
            kapS[s] = kapSonne(s);
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bListeS[s]);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 8L * kapS[s], null, GL4.GL_DYNAMIC_COPY);
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        kastenKamera = null;
        schattenFertig = false;
    }

    // ------------------------------------------------------------------ Bild

    /**
     * Zeichnet ein Bild der Kamera zur Zeit {@code zeit} (s, für den Wind) und bringt es in den Rahmen
     * {@code ziel} (0 = Fenster) ins Rechteck x, y, Breite, Höhe (Seitenverhältnis bleibt, der Rand ist der dunkle Grund).
     */
    public void zeichnen(GL4 gl, Kamera k, double zeit, int ziel, int rx, int ry, int rw, int rh) {
        if (dickicht == null) return;
        ziele(gl);
        k.breite = w;
        k.hoehe = h;
        k.stellen();
        float[] vp = k.blickProjektion(nah, fern);
        double[] vorn = k.vorn();
        tonwerte(gl);
        zeitenLesen(gl);
        gl.glBindVertexArray(vao);
        marke(gl, 0);

        // 1 Schattenkarten (nah und fern)
        if (kaskaden(k) || !schattenFertig || wind.staerke > 0) {
            schatten(gl, zeit, k, FERN);
            schatten(gl, zeit, k, NAH);
            schattenFertig = true;
        }
        marke(gl, 1);

        // 2 Auswahl für die Ansicht
        befehlZuruecksetzen(gl, bBefehlA, kapA);
        gl.glUseProgram(pAuswahl);
        gemeinsam(gl, pAuswahl, zeit, k.auge, true);
        setI(gl, pAuswahl, "uModus", 0);
        gl.glUniform1ui(ort(gl, pAuswahl, "uZweigZahl"), zweigZahl);
        gl.glUniformMatrix4fv(ort(gl, pAuswahl, "uVP"), 1, false, vp, 0);
        set3(gl, pAuswahl, "uVorn", vorn);
        setF(gl, pAuswahl, "uFokus", k.brennweite());
        setF(gl, pAuswahl, "uZiel", fleck * w);
        setF(gl, pAuswahl, "uHysterese", hysterese);
        gl.glUniform2f(ort(gl, pAuswahl, "uBild"), w, h);
        speicher(gl, 0, bZweige);
        speicher(gl, 1, bListeA);
        speicher(gl, 2, bBefehlA);
        speicher(gl, 3, bHyst);
        gl.glDispatchCompute((zweigZahl + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_COMMAND_BARRIER_BIT | GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        marke(gl, 2);

        // 3 Bild mit Mehrfachabtastung: Farbe und Helligkeit
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fMsaa);
        gl.glDrawBuffers(2, new int[]{GL.GL_COLOR_ATTACHMENT0, GL4.GL_COLOR_ATTACHMENT1}, 0);
        gl.glViewport(0, 0, w, h);
        int grund = vorlage.palette.rgb(0);
        gl.glClearBufferfv(GL4.GL_COLOR, 0, new float[]{((grund >> 16) & 255) / 255f, ((grund >> 8) & 255) / 255f, (grund & 255) / 255f, 1}, 0);
        gl.glClearBufferfv(GL4.GL_COLOR, 1, new float[]{0, 0, 0, 0}, 0);
        gl.glClearBufferfv(GL4.GL_DEPTH, 0, new float[]{1}, 0);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        texturenBinden(gl);
        // Boden
        gl.glUseProgram(pBoden);
        gemeinsam(gl, pBoden, zeit, k.auge, false);
        gl.glUniformMatrix4fv(ort(gl, pBoden, "uVP"), 1, false, vp, 0);
        gl.glUniform4fv(ort(gl, pBoden, "uBodenRect"), 1, bodenRect, 0);
        gl.glUniform3f(ort(gl, pBoden, "uBoden"), dickicht.boden[0], dickicht.boden[1], dickicht.boden[2]);
        gl.glDrawArrays(GL.GL_TRIANGLE_STRIP, 0, 4);
        // Stämme und Äste
        gl.glUseProgram(pGlied);
        gemeinsam(gl, pGlied, zeit, k.auge, false);
        setI(gl, pGlied, "uModus", 0);
        gl.glUniformMatrix4fv(ort(gl, pGlied, "uVP"), 1, false, vp, 0);
        set3(gl, pGlied, "uVorn", vorn);
        setF(gl, pGlied, "uFokus", k.brennweite());
        speicher(gl, 4, bGlieder);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, gliedZahl);
        // Halme
        gl.glUseProgram(pHalm);
        gemeinsam(gl, pHalm, zeit, k.auge, false);
        gl.glUniformMatrix4fv(ort(gl, pHalm, "uVP"), 1, false, vp, 0);
        set3(gl, pHalm, "uVorn", vorn);
        setF(gl, pHalm, "uFokus", k.brennweite());
        speicher(gl, 6, bHalme);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 10, halmZahl);
        marke(gl, 3);
        // Flecken
        gl.glUseProgram(pFleck);
        gemeinsam(gl, pFleck, zeit, k.auge, false);
        setI(gl, pFleck, "uModus", 0);
        gl.glUniformMatrix4fv(ort(gl, pFleck, "uVP"), 1, false, vp, 0);
        set3(gl, pFleck, "uVorn", vorn);
        setF(gl, pFleck, "uFokus", k.brennweite());
        setF(gl, pFleck, "uZiel", fleck * w);
        speicher(gl, 0, bZweige);
        speicher(gl, 1, bListeA);
        gl.glEnable(GL.GL_SAMPLE_ALPHA_TO_COVERAGE);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, bBefehlA);
        gl.glDrawArraysIndirect(GL.GL_TRIANGLE_STRIP, 0);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, 0);
        gl.glDisable(GL.GL_SAMPLE_ALPHA_TO_COVERAGE);
        gl.glDisable(GL.GL_DEPTH_TEST);
        marke(gl, 4);

        // 4 Auflösen (beide Ziele)
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, fMsaa);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, fAufl);
        for (int a = 0; a < 2; a++) {
            gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0 + a);
            gl.glDrawBuffers(1, new int[]{GL.GL_COLOR_ATTACHMENT0 + a}, 0);
            gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST);
        }
        gl.glDrawBuffers(2, new int[]{GL.GL_COLOR_ATTACHMENT0, GL4.GL_COLOR_ATTACHMENT1}, 0);

        // 5 Histogramm der Helligkeit (gelesen im nächsten Bild)
        int hb = bHisto[(int) (bild % 3)];   // Ring aus drei: gelesen zwei Bilder später, ohne auf die Grafikkarte zu warten
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, hb);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 257L * 4, Buffers.newDirectIntBuffer(257), GL4.GL_DYNAMIC_READ);
        gl.glUseProgram(pHisto);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tY);
        setI(gl, pHisto, "uY", 2);
        setF(gl, pHisto, "uLo", LO);
        setF(gl, pHisto, "uHi", HI);
        speicher(gl, 5, hb);
        gl.glDispatchCompute((w + 15) / 16, (h + 15) / 16, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        gl.glActiveTexture(GL.GL_TEXTURE0);

        // 6 ins Ziel
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, fAufl);
        gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, ziel);
        if (ziel == 0) gl.glDrawBuffer(GL.GL_BACK);
        gl.glViewport(rx, ry, rw, rh);
        gl.glClearColor(((grund >> 16) & 255) / 255f, ((grund >> 8) & 255) / 255f, (grund & 255) / 255f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        double s = Math.min(rw / (double) w, rh / (double) h);
        int dw = (int) Math.round(w * s), dh = (int) Math.round(h * s), dx = rx + (rw - dw) / 2, dy = ry + (rh - dh) / 2;
        gl.glBlitFramebuffer(0, 0, w, h, dx, dy, dx + dw, dy + dh, GL.GL_COLOR_BUFFER_BIT, GL.GL_LINEAR);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, ziel);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        marke(gl, 5);
        bild++;
    }

    /** Namen der gemessenen Durchgänge (Zeit der Grafikkarte, ein Bild versetzt gelesen). */
    public static final String[] DURCHGAENGE = {"Schatten", "Auswahl", "Boden, Stämme, Halme", "Flecken", "Auflösen, Histogramm"};
    private final int[][] zeitQ = new int[2][6];
    private final double[] zeitenMs = new double[5];

    /** Zeit der Grafikkarte je Durchgang (ms) im vorletzten Bild, in der Reihenfolge von {@link #DURCHGAENGE}. */
    public double[] zeiten() { return zeitenMs.clone(); }

    private void marke(GL4 gl, int i) {
        if (zeitQ[0][0] == 0) {
            int[] q = new int[12];
            gl.glGenQueries(12, q, 0);
            for (int k = 0; k < 12; k++) zeitQ[k / 6][k % 6] = q[k];
        }
        gl.glQueryCounter(zeitQ[(int) (bild & 1)][i], GL4.GL_TIMESTAMP);
    }

    private void zeitenLesen(GL4 gl) {
        if (bild < 2 || zeitQ[0][0] == 0) return;
        int[] q = zeitQ[(int) ((bild - 1) & 1)], da = new int[1];
        gl.glGetQueryObjectiv(q[5], GL4.GL_QUERY_RESULT_AVAILABLE, da, 0);
        if (da[0] == 0) return;
        long[] t = new long[6];
        for (int i = 0; i < 6; i++) gl.glGetQueryObjecti64v(q[i], GL4.GL_QUERY_RESULT, t, i);
        for (int i = 0; i < 5; i++) zeitenMs[i] = (t[i + 1] - t[i]) / 1e6;
    }

    /** Eine Schattenkarte (s = FERN oder NAH): Auswahl entlang der Sonne, dann Blätter (in ihrer Form) und Glieder als Tiefe. */
    private void schatten(GL4 gl, double zeit, Kamera k, int s) {
        befehlZuruecksetzen(gl, bBefehlS[s], kapS[s]);
        gl.glUseProgram(pAuswahl);
        gemeinsam(gl, pAuswahl, zeit, k.auge, true);
        setI(gl, pAuswahl, "uModus", 1);
        gl.glUniform1ui(ort(gl, pAuswahl, "uZweigZahl"), zweigZahl);
        gl.glUniformMatrix4fv(ort(gl, pAuswahl, "uVP"), 1, false, sonneM[s], 0);
        setF(gl, pAuswahl, "uFokus", texelProM[s]);
        setF(gl, pAuswahl, "uZiel", schattenZiel(s));
        speicher(gl, 0, bZweige);
        speicher(gl, 7, bListeS[s]);
        speicher(gl, 2, bBefehlS[s]);
        speicher(gl, 3, bHyst);
        gl.glDispatchCompute((zweigZahl + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_COMMAND_BARRIER_BIT | GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fSchatten[s]);
        gl.glViewport(0, 0, schattenN[s], schattenN[s]);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        // nicht aus den Karten lesen, in die gezeichnet wird
        gl.glActiveTexture(GL4.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(pFleck);
        gemeinsam(gl, pFleck, zeit, k.auge, false);
        setI(gl, pFleck, "uModus", 1);
        gl.glUniformMatrix4fv(ort(gl, pFleck, "uVP"), 1, false, sonneM[s], 0);
        setF(gl, pFleck, "uFokus", texelProM[s]);
        setF(gl, pFleck, "uZiel", schattenZiel(s));
        speicher(gl, 0, bZweige);
        speicher(gl, 7, bListeS[s]);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, bBefehlS[s]);
        gl.glDrawArraysIndirect(GL.GL_TRIANGLE_STRIP, 0);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, 0);
        gl.glUseProgram(pGlied);
        gemeinsam(gl, pGlied, zeit, k.auge, false);
        setI(gl, pGlied, "uModus", 1);
        gl.glUniformMatrix4fv(ort(gl, pGlied, "uVP"), 1, false, sonneM[s], 0);
        setF(gl, pGlied, "uFokus", k.brennweite());
        speicher(gl, 4, bGlieder);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, gliedZahl);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Grenzen der Bänder und Belichtung aus dem Histogramm von vor zwei Bildern (Ring aus drei Puffern). */
    private void tonwerte(GL4 gl) {
        double[] an = Stufen.anteile(vorlage);
        if (bild < 2 || grenzen == null || grenzen.length != an.length - 1) {
            grenzen = new double[an.length - 1];
            for (int j = 0; j < grenzen.length; j++) grenzen[j] = -12 + 10.0 * (j + 1) / an.length;
            if (bild < 2) return;
        }
        int hb = bHisto[(int) ((bild - 2) % 3)];
        IntBuffer ib = Buffers.newDirectIntBuffer(257);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, hb);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 257L * 4, ib);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        long nullen = Integer.toUnsignedLong(ib.get(0));
        long[] fach = new long[256];
        long summe = nullen;
        for (int i = 0; i < 256; i++) { fach[i] = Integer.toUnsignedLong(ib.get(i + 1)); summe += fach[i]; }
        if (summe == 0) return;
        double[] alt = grenzen;
        grenzen = Stufen.grenzen(fach, nullen, LO, HI, an);
        // springen die Grenzen (erstes Histogramm, andere Vorlage, Sonne), fängt die Hysterese neu an: sonst behielten
        // Blätter an einer Grenze ein Band, das zu den alten Grenzen gehörte
        double sprung = 0;
        for (int j = 0; j < grenzen.length; j++) sprung = Math.max(sprung, Math.abs(grenzen[j] - alt[j]));
        if (sprung > 0.5) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bHyst);
            gl.glClearBufferData(GL4.GL_SHADER_STORAGE_BUFFER, GL4.GL_R32UI, GL4.GL_RED_INTEGER, GL.GL_UNSIGNED_INT, Buffers.newDirectIntBuffer(new int[]{-1}));
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        }
        // Belichtung: Median von Y auf das Y der Vorlage (für Buntheit und Farbton der Flecken, wie in Malweise)
        long halb = summe / 2, c = nullen;
        if (c < halb)
            for (int i = 0; i < 256; i++) {
                if (c + fach[i] >= halb) {
                    double t = fach[i] == 0 ? 0 : (halb - c) / (double) fach[i], x = LO + (HI - LO) * (i + t) / 256;
                    double l50 = vorlage.l50, yZiel = l50 > 8 ? Math.pow((l50 + 16) / 116, 3) : l50 / 903.3;
                    belichtung = yZiel / Math.pow(2, x);
                    break;
                }
                c += fach[i];
            }
    }

    /** Liest die Zähler der beiden Auswahlen (wartet auf die Grafikkarte: nur ab und zu rufen). */
    public void statistik(GL4 gl) {
        gl.glMemoryBarrier(GL4.GL_BUFFER_UPDATE_BARRIER_BIT);   // die Zähler schreiben Atomics im Compute-Shader
        IntBuffer ib = Buffers.newDirectIntBuffer(8);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bBefehlA);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 32, ib);
        instanzen = ib.get(1);
        wechsel = Integer.toUnsignedLong(ib.get(4));
        gewollt = Integer.toUnsignedLong(ib.get(6));
        int si = 0;
        long sg = 0;
        for (int s = 0; s < 2; s++) {
            ib.clear();
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bBefehlS[s]);
            gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 32, ib);
            si += ib.get(1);
            sg += Integer.toUnsignedLong(ib.get(6));
        }
        schattenInstanzen = si;
        schattenGewollt = sg;
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /** Anteil der Flecken, die im letzten Bild ihr Band gewechselt haben (nach {@link #statistik}). */
    public double flimmern() { return instanzen == 0 ? 0 : wechsel / (double) instanzen; }

    /**
     * Für Prüfungen: baut die Blätter (Zweig {@code zweige[k]}, Blatt {@code blaetter[k]}) auf der Grafikkarte
     * ({@code pruefen.comp}, dieselbe Funktion {@code blatt} wie alle Shader) und gibt je Blatt 12 Werte zurück: Mitte,
     * Normale, Längsachse, Länge, Breite, Zufall. Nach {@link #laden}.
     */
    public float[] blaetterPruefen(GL4 gl, int[] zweige, int[] blaetter) {
        if (pPruefen == 0) pPruefen = rechnen(gl, source("gertsch.glsl"), "pruefen.comp");
        int n = zweige.length;
        int[] b = new int[2];
        gl.glGenBuffers(2, b, 0);
        IntBuffer fragen = Buffers.newDirectIntBuffer(2 * n);
        for (int k = 0; k < n; k++) fragen.put(zweige[k]).put(blaetter[k]);
        fragen.flip();
        hochladen(gl, b[0], fragen, GL4.GL_STATIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b[1]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 48L * n, null, GL4.GL_DYNAMIC_READ);
        gl.glUseProgram(pPruefen);
        gemeinsam(gl, pPruefen, 0, new double[3], true);
        gl.glUniform1ui(ort(gl, pPruefen, "uZahl"), n);
        speicher(gl, 0, bZweige);
        speicher(gl, 8, b[0]);
        speicher(gl, 9, b[1]);
        gl.glDispatchCompute((n + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        FloatBuffer aus = Buffers.newDirectFloatBuffer(12 * n);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b[1]);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 48L * n, aus);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        gl.glDeleteBuffers(2, b, 0);
        gl.glUseProgram(0);
        float[] o = new float[12 * n];
        aus.get(o);
        return o;
    }

    private int pPruefen;

    /** Die gemerkten Bänder aller Blätter (ein Byte je Platz, 0xFF = keins); wartet auf die Grafikkarte, für Prüfungen. */
    public byte[] baender(GL4 gl) {
        gl.glMemoryBarrier(GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        int n = Math.max(1, dickicht.plaetze / 4) * 4;
        ByteBuffer bb = Buffers.newDirectByteBuffer(n);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bHyst);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, n, bb);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        byte[] o = new byte[n];
        bb.get(o);
        return o;
    }

    /** Grenzen der Bänder in log2 Y (Kopie). */
    public double[] grenzen() { return grenzen == null ? new double[0] : grenzen.clone(); }

    /** Das letzte Bild (sRGB, w × h). */
    public BufferedImage lesen(GL4 gl) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, fAufl);
        gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, 0);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                im.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return im;
    }

    public String info() {
        return String.format(Locale.GERMAN, "%,d Flecken%s, %,d im Schatten%s, %.1f %% wechseln je Bild das Band", instanzen,
                gewollt > instanzen ? " (Liste zu klein: " + gewollt + ")" : "", schattenInstanzen,
                schattenGewollt > schattenInstanzen ? " (zu klein)" : "", 100 * flimmern());
    }

    // ------------------------------------------------------------------ Gemeinsame Werte

    private void gemeinsam(GL4 gl, int p, double zeit, double[] auge, boolean rechnen) {
        Dickicht d = dickicht;
        Licht l = licht;
        setF(gl, p, "uNeigung", d.neigung);
        setF(gl, p, "uTrocken", d.trocken);
        set3f(gl, p, "uLaub", d.laub);
        set3f(gl, p, "uLaubTrocken", d.laubTrocken);
        set3f(gl, p, "uStreu", d.streuFarbe);
        gl.glUniform4f(ort(gl, p, "uWind"), (float) wind.staerke, (float) wind.richtung, (float) wind.boeen, (float) zeit);
        set3(gl, p, "uZurSonne", l.zurSonne);
        set3(gl, p, "uSonneFarbe", l.sonneFarbe);
        set3(gl, p, "uHimmelFarbe", l.himmelFarbe);
        setF(gl, p, "uDurch", durchscheinen);
        gl.glUniformMatrix4fv(ort(gl, p, "uSonneM"), 1, false, sonneM[FERN], 0);
        gl.glUniformMatrix4fv(ort(gl, p, "uSonneMNah"), 1, false, sonneM[NAH], 0);
        setI(gl, p, "uSchatten", 0);
        setI(gl, p, "uSchattenNah", 3);
        // Versatz gegen Selbstschatten: 6 cm und zwei Texel entlang der Sonne, als Anteil der Tiefe der Karte
        setF(gl, p, "uSchattenBias", (0.06 + 2.0 / texelProM[FERN]) / schattenTiefe);
        setF(gl, p, "uSchattenBiasNah", (0.04 + 2.0 / texelProM[NAH]) / schattenTiefe);
        setI(gl, p, "uLai", 1);
        gl.glUniform3fv(ort(gl, p, "uLaiUrsprung"), 1, laiUrsprung, 0);
        gl.glUniform3f(ort(gl, p, "uLaiZelle"), (float) Licht.ZELLE, (float) Licht.SCHICHT, (float) Licht.ZELLE);
        gl.glUniform3fv(ort(gl, p, "uLaiZahl"), 1, laiZahl, 0);
        setF(gl, p, "uAusloeschung", l.ausloeschung);
        set3(gl, p, "uAuge", auge);
        Palette pal = vorlage.palette;
        int k = pal.size();
        setI(gl, p, "uStufen", k);
        float[] g = new float[7], lab = new float[24], rgb = new float[24];
        for (int j = 0; grenzen != null && j < Math.min(7, grenzen.length); j++) g[j] = (float) grenzen[j];
        for (int i = 0; i < Math.min(8, k); i++) {
            int c = pal.rgb(i);
            for (int a = 0; a < 3; a++) lab[3 * i + a] = (float) pal.lab[i][a];
            rgb[3 * i] = ((c >> 16) & 255) / 255f;
            rgb[3 * i + 1] = ((c >> 8) & 255) / 255f;
            rgb[3 * i + 2] = (c & 255) / 255f;
        }
        gl.glUniform1fv(ort(gl, p, "uGrenze"), 7, g, 0);
        gl.glUniform3fv(ort(gl, p, "uStufeLab"), 8, lab, 0);
        gl.glUniform3fv(ort(gl, p, "uStufeRgb"), 8, rgb, 0);
        setF(gl, p, "uFarbGewicht", farbGewicht);
        setF(gl, p, "uBelichtung", belichtung);
        gl.glUniform2f(ort(gl, p, "uAkzentGrenze"), (float) akzentBuntheit, (float) akzentWinkel);
        setF(gl, p, "uFarbton", vorlage.farbton);
        double[] ak = akzent();
        set3(gl, p, "uAkzentLab", ak);
    }

    /**
     * Farbe der Akzente wie Malweise: Buntheit und Richtung der Akzente der Vorlage, mindestens 68° vom herrschenden
     * Farbton des Bilds entfernt; dieser hier aus den Anteilen der Stufen (die Tonwerte nach der Vorlage geben sie vor).
     */
    double[] akzent() {
        Palette pal = vorlage.palette;
        double[] an = Stufen.anteile(vorlage);
        double sa = 0, sb = 0;
        for (int i = 0; i < pal.size(); i++) { sa += an[i] * pal.lab[i][1]; sb += an[i] * pal.lab[i][2]; }
        double herrschend = sa == 0 && sb == 0 ? vorlage.farbton : Lab.farbton(sa, sb);
        double[] a = vorlage.akzentLab;
        double ha = Lab.farbton(a[1], a[2]), d = ((ha - herrschend + 540) % 360) - 180;
        double hh = Math.toRadians(herrschend + Math.signum(d == 0 ? -1 : d) * Math.max(Math.abs(d), 68));
        double cb = Math.max(20, Lab.buntheit(a[1], a[2]));
        return new double[]{a[0], cb * Math.cos(hh), cb * Math.sin(hh)};
    }

    private void texturenBinden(GL4 gl) {
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tSchatten[FERN]);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, tLai);
        gl.glActiveTexture(GL4.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tSchatten[NAH]);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    // ------------------------------------------------------------------ Hilfen

    private void befehlZuruecksetzen(GL4 gl, int b, int kap) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 32, Buffers.newDirectIntBuffer(new int[]{4, 0, 0, 0, 0, kap, 0, 0}));
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private static void speicher(GL4 gl, int bindung, int b) { gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, bindung, b); }

    private static void hochladen(GL4 gl, int b, java.nio.Buffer daten, int art) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b);
        long bytes = (long) daten.remaining() * (daten instanceof ByteBuffer ? 1 : 4);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, Math.max(16, bytes), bytes > 0 ? daten : null, art);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private static void textur(GL4 gl, int t, int intern, int format, int typ, int w, int h) {
        gl.glBindTexture(GL.GL_TEXTURE_2D, t);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, intern, w, h, 0, format, typ, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    private static void pruefen(GL4 gl, String was) {
        int s = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
        if (s != GL.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("gertsch: Rahmen " + was + " unvollständig (0x" + Integer.toHexString(s) + ")");
    }

    private int ort(GL4 gl, int p, String name) {
        return orte.computeIfAbsent(p, x -> new HashMap<>()).computeIfAbsent(name, n -> gl.glGetUniformLocation(p, n));
    }

    private void setF(GL4 gl, int p, String n, double v) { gl.glUniform1f(ort(gl, p, n), (float) v); }
    private void setI(GL4 gl, int p, String n, int v) { gl.glUniform1i(ort(gl, p, n), v); }
    private void set3(GL4 gl, int p, String n, double[] v) { gl.glUniform3f(ort(gl, p, n), (float) v[0], (float) v[1], (float) v[2]); }
    private void set3f(GL4 gl, int p, String n, float[] v) { gl.glUniform3f(ort(gl, p, n), v[0], v[1], v[2]); }

    private void loeschenZiele(GL4 gl) {
        if (fMsaa != 0) gl.glDeleteFramebuffers(4, new int[]{fMsaa, fAufl, fSchatten[0], fSchatten[1]}, 0);
        if (rbFarbe != 0) gl.glDeleteRenderbuffers(3, new int[]{rbFarbe, rbY, rbTiefe}, 0);
        if (tFarbe != 0) gl.glDeleteTextures(4, new int[]{tFarbe, tY, tSchatten[0], tSchatten[1]}, 0);
        fMsaa = fAufl = rbFarbe = rbY = rbTiefe = tFarbe = tY = 0;
        fSchatten[0] = fSchatten[1] = tSchatten[0] = tSchatten[1] = 0;
    }

    public void dispose(GL4 gl) {
        loeschenZiele(gl);
        for (int p : new int[]{pAuswahl, pFleck, pGlied, pHalm, pBoden, pHisto, pPruefen}) if (p != 0) gl.glDeleteProgram(p);
        gl.glDeleteBuffers(13, new int[]{bZweige, bGlieder, bHalme, bListeA, bListeS[0], bListeS[1], bBefehlA, bBefehlS[0], bBefehlS[1], bHyst,
                bHisto[0], bHisto[1], bHisto[2]}, 0);
        if (tLai != 0) gl.glDeleteTextures(1, new int[]{tLai}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        pAuswahl = pFleck = pGlied = pHalm = pBoden = pHisto = pPruefen = vao = tLai = 0;
        orte.clear();
    }

    // ------------------------------------------------------------------ Shader

    /** Liest eine Shader-Datei aus diesem Package. */
    public static String source(String file) {
        try (InputStream in = GertschGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("gertsch: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("gertsch: " + file + ": " + e.getMessage(), e);
        }
    }

    private int link(GL4 gl, String gemeinsam, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, gemeinsam, vert), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, gemeinsam, frag);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        geprueft(gl, p, vert + " + " + frag);
        return p;
    }

    private int rechnen(GL4 gl, String gemeinsam, String comp) {
        int p = gl.glCreateProgram();
        int cs = compile(gl, GL4.GL_COMPUTE_SHADER, gemeinsam, comp);
        gl.glAttachShader(p, cs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(cs);
        geprueft(gl, p, comp);
        return p;
    }

    private static void geprueft(GL4 gl, int p, String was) {
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] != 0) return;
        int[] len = new int[1];
        gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
        throw new IllegalStateException("gertsch: " + was + " lässt sich nicht binden: " + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
    }

    private static int compile(GL4 gl, int type, String gemeinsam, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + gemeinsam + "\n#line 1\n" + source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
            throw new IllegalStateException("gertsch: " + file + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return sh;
    }
}
