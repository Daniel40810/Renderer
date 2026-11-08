package com.dan.gertsch.gl;

import com.dan.gertsch.Lab;
import com.dan.gertsch.Punkte;
import com.dan.gertsch.Stich;
import com.dan.gertsch.Zufall;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Der Holzschnitt ({@link Stich}) auf der Grafikkarte (OpenGL 4.3), jedes Bild neu, aus einer beliebigen Textur: dem
 * Dickicht von {@link GertschGl} oder dem Bild einer anderen Szene. Dieselben Rechnungen wie auf dem Prozessor
 * ({@code stich.glsl}). Im Wind ziehen die Blätter unter den festen Punkten des Stocks durch; damit die Punkte nicht
 * flimmern, glättet eine <b>Trägheit</b> ({@link #traegheit}) die Helligkeit je Punkt über die Zeit, und eine
 * <b>Hysterese</b> lässt einen Punkt erst wechseln, wenn die Helligkeit um mehr als {@link #hysterese} Blenden über seine
 * Schwelle geht.
 * <p>
 * Je Bild ({@link #drucken}):
 * <ol>
 * <li>Helligkeit Y der Quelle ({@code stich_hell.frag}; eine Quelle mit Y, etwa {@link GertschGl#helligkeitTextur()},
 * geht direkt).</li>
 * <li>Die Tabelle des Tons ({@link Stich#tonKurve}) aus einem Histogramm von log2 Y: im Lauf aus dem Histogramm der
 * geglätteten Helligkeit, die die Punkte sahen, von vor zwei Bildern (Ring aus drei Puffern, ohne Warten); für einen
 * neuen Druck aus der Quelle ({@code histogramm.comp} von GertschGl).</li>
 * <li>Zustand je Zelle ({@code stich_punkte.comp}): gestochen oder nicht, mit Trägheit und Hysterese; Zähler.</li>
 * <li>Der Druck ({@code stich.frag}) in eine eigene Textur, so breit wie gewünscht (Vorgabe {@link com.dan.gertsch.Druck#BREITE}).</li>
 * </ol>
 * {@link #zeigen} bringt den Druck ins Ziel: ganz (verkleinert wie aus der Entfernung) oder mit der Lupe.
 */
public final class StichGl {
    /** Was die Quelle enthält: sRGB-Farbe (RGBA8) oder lineare Helligkeit (R32F). */
    public enum Eingang { FARBE, HELLIGKEIT }

    /** Die Einstellungen: Druck, Punkte, Wolken, Korn, Tonwerte, seed (wie auf dem Prozessor). */
    public final Stich stich = new Stich();
    /** Hysterese der Punkte in Blenden (0 = aus). */
    public double hysterese = 0.3;
    /**
     * Trägheit (s): Zeitkonstante, mit der die Helligkeit je Zelle geglättet wird (0 = aus). Sie dämpft das Flattern
     * der Blätter (2,5 bis 4 Hz) und lässt das Schwingen der Zweige (unter 1 Hz) fast durch.
     */
    public double traegheit = 0.15;

    private int pHell, pHisto, pPunkte, pDruck, pZeigen, vao;
    private final Map<Integer, Map<String, Integer>> orte = new HashMap<>();
    private int tBlau, tHell, fHell, tZustand, tGlatt, tDruck, fDruck, bZaehler;
    private double letzteZeit = Double.NaN;
    /** Histogramm der Quelle (für einen neuen Druck) und Ring aus drei Histogrammen der Zellen (geglättet). */
    private int bHisto;
    private final int[] bZellen = new int[3];
    private final boolean[] zellenGefuellt = new boolean[3];
    private int hw, hh, pw, ph, nx, ny, zw, zh, stufen;
    private float zelle;
    private String gestalt = "";
    private float[] tonKurve;
    private long bild;
    private boolean frisch = true;
    private final int[] zeitQ = new int[2];
    private double zeitMs;
    /** Nach {@link #statistik}: Zellen, gestochene Punkte, Punkte, die im letzten Bild wechselten. */
    public volatile int zellen, gestochen, gewechselt;

    public void init(GL4 gl) {
        String g = GertschGl.source("stich.glsl");
        pHell = link(gl, "", "stich.vert", "stich_hell.frag");
        pHisto = rechnen(gl, "", "histogramm.comp");
        pPunkte = rechnen(gl, g, "stich_punkte.comp");
        pDruck = link(gl, g, "stich.vert", "stich.frag");
        pZeigen = link(gl, "", "stich.vert", "stich_zeigen.frag");
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        int[] b = new int[5];
        gl.glGenBuffers(5, b, 0);
        bHisto = b[0]; bZellen[0] = b[1]; bZellen[1] = b[2]; bZellen[2] = b[3]; bZaehler = b[4];
        for (int hb : b) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, hb);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 257L * 4, null, GL4.GL_DYNAMIC_READ);
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bZaehler);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 8, null, GL4.GL_DYNAMIC_READ);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        // die Kachel der Blue-Noise-Ränge
        int[] tx = new int[1];
        gl.glGenTextures(1, tx, 0);
        tBlau = tx[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tBlau);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, Punkte.N, Punkte.N, 0, GL4.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(Punkte.blau()));
        nah(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Den nächsten Druck ohne Hysterese rechnen (neue Quelle). */
    public void neu() { frisch = true; }

    /**
     * Druckt die Quelle (Textur, Zeile 0 unten, qw × qh) {@code breite} Bildpunkte breit in die eigene Textur.
     * {@code art}: was die Quelle enthält. Ohne Zeit: ohne Trägheit.
     */
    public void drucken(GL4 gl, int quelle, int qw, int qh, Eingang art, int breite) { drucken(gl, quelle, qw, qh, art, breite, Double.NaN); }

    /** Wie oben, zur Zeit {@code zeit} (s): aus dem Abstand zum letzten Druck folgt, wie stark die Trägheit glättet. */
    public void drucken(GL4 gl, int quelle, int qw, int qh, Eingang art, int breite, double zeit) {
        double dt = Double.isNaN(zeit) || Double.isNaN(letzteZeit) ? Double.POSITIVE_INFINITY : Math.max(0, zeit - letzteZeit);
        letzteZeit = zeit;
        float alpha = traegheit <= 0 || Double.isInfinite(dt) ? 1 : (float) (1 - Math.exp(-dt / traegheit));
        if (zeitQ[0] == 0) gl.glGenQueries(2, zeitQ, 0);
        else {
            int[] fertig = new int[1];
            gl.glGetQueryObjectiv(zeitQ[1], GL4.GL_QUERY_RESULT_AVAILABLE, fertig, 0);
            if (fertig[0] != 0) {
                long[] a = new long[1], e = new long[1];
                gl.glGetQueryObjecti64v(zeitQ[0], GL4.GL_QUERY_RESULT, a, 0);
                gl.glGetQueryObjecti64v(zeitQ[1], GL4.GL_QUERY_RESULT, e, 0);
                zeitMs = (e[0] - a[0]) / 1e6;
            }
        }
        gl.glQueryCounter(zeitQ[0], GL4.GL_TIMESTAMP);
        int[] altRahmen = new int[1];
        gl.glGetIntegerv(GL4.GL_DRAW_FRAMEBUFFER_BINDING, altRahmen, 0);
        gl.glBindVertexArray(vao);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_CULL_FACE);

        // 1 Helligkeit
        int yTex = quelle;
        if (art == Eingang.FARBE) {
            hellZiel(gl, qw, qh);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fHell);
            gl.glViewport(0, 0, qw, qh);
            gl.glUseProgram(pHell);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, quelle);
            setI(gl, pHell, "uQuelle", 0);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            yTex = tHell;
        }

        // 2 Tabelle des Tons: aus dem Histogramm der geglätteten Helligkeit der Zellen von vor zwei Bildern (Ring aus
        //   drei, ohne auf die Grafikkarte zu warten); für einen neuen Druck aus dem Histogramm der Quelle, gewartet
        ziele(gl, breite, qw, qh);
        if (frisch) java.util.Arrays.fill(zellenGefuellt, false);
        int jetzt = (int) (bild % 3), alt = (int) ((bild + 1) % 3);
        if (zellenGefuellt[alt]) tonKurve = stich.tonKurve(histogramm(gl, bZellen[alt]));
        else {
            leeren(gl, bHisto);
            gl.glUseProgram(pHisto);
            gl.glActiveTexture(GL.GL_TEXTURE2);
            gl.glBindTexture(GL.GL_TEXTURE_2D, yTex);
            setI(gl, pHisto, "uY", 2);
            setF(gl, pHisto, "uLo", Stich.LO);
            setF(gl, pHisto, "uHi", Stich.HI);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 5, bHisto);
            gl.glDispatchCompute((qw + 15) / 16, (qh + 15) / 16, 1);
            gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
            tonKurve = stich.tonKurve(histogramm(gl, bHisto));
        }

        // 3 Zustand der Punkte, Histogramm der Zellen
        leeren(gl, bZellen[jetzt]);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 3, bZellen[jetzt]);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bZaehler);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 8, Buffers.newDirectIntBuffer(new int[]{0, 0}));
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        gl.glUseProgram(pPunkte);
        gemeinsam(gl, pPunkte);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, yTex);
        setI(gl, pPunkte, "uY", 2);
        gl.glBindImageTexture(0, tZustand, 0, false, 0, GL4.GL_READ_WRITE, GL4.GL_R8UI);
        gl.glBindImageTexture(2, tGlatt, 0, false, 0, GL4.GL_READ_WRITE, GL4.GL_R32F);
        setF(gl, pPunkte, "uAlpha", alpha);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, bZaehler);
        gl.glUniform2i(ort(gl, pPunkte, "uZellen"), zw, zh);
        gl.glUniform2i(ort(gl, pPunkte, "uInnen"), nx, ny);
        gl.glUniform2f(ort(gl, pPunkte, "uDruck"), pw, ph);
        setF(gl, pPunkte, "uHysterese", hysterese);
        setI(gl, pPunkte, "uFrisch", frisch ? 1 : 0);
        gl.glDispatchCompute((zw + 15) / 16, (zh + 15) / 16, 1);
        gl.glMemoryBarrier(GL4.GL_TEXTURE_FETCH_BARRIER_BIT | GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL4.GL_SHADER_STORAGE_BARRIER_BIT
                | GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        zellenGefuellt[jetzt] = true;

        // 4 Druck
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fDruck);
        gl.glViewport(0, 0, pw, ph);
        gl.glUseProgram(pDruck);
        gemeinsam(gl, pDruck);
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tZustand);
        setI(gl, pDruck, "uZustand", 3);
        gl.glUniform2f(ort(gl, pDruck, "uDruck"), pw, ph);
        double[] pap = new double[3], far = new double[3], pl = stich.papierLab(), fl = stich.farbeLab();
        Lab.zuLinear(pl[0], pl[1], pl[2], pap);
        Lab.zuLinear(fl[0], fl[1], fl[2], far);
        float[] aus = stich.ausgleich(ph / (double) pw);
        float[] p = new float[3], ln = new float[3];
        for (int c = 0; c < 3; c++) {
            p[c] = (float) Math.max(1e-6, pap[c]);
            ln[c] = (float) Math.log(Math.max(1e-6, far[c]) / p[c]);
        }
        gl.glUniform3fv(ort(gl, pDruck, "uPapier"), 1, p, 0);
        gl.glUniform3fv(ort(gl, pDruck, "uLnVerh"), 1, ln, 0);
        gl.glUniform3fv(ort(gl, pDruck, "uAusgleich"), 1, aus, 0);
        setF(gl, pDruck, "uWolken", stich.wolken);
        setF(gl, pDruck, "uKorn", stich.korn);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tDruck);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, altRahmen[0]);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glQueryCounter(zeitQ[1], GL4.GL_TIMESTAMP);
        frisch = false;
        bild++;
    }

    /**
     * Zeigt den letzten Druck im Rahmen {@code ziel} (0 = Fenster) im Rechteck x, y, Breite, Höhe. {@code lupe} ≤ 0:
     * ganz (Seitenverhältnis bleibt); sonst Bildpunkte des Drucks {@code lupe}-fach um die Mitte (mu, mv) (Anteile des
     * Drucks, v nach unten). Der Rand ist {@code grund} (0xRRGGBB).
     */
    public void zeigen(GL4 gl, int ziel, int rx, int ry, int rw, int rh, double lupe, double mu, double mv, int grund) {
        if (tDruck == 0) return;
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, ziel);
        if (ziel == 0) gl.glDrawBuffer(GL.GL_BACK);
        gl.glViewport(rx, ry, rw, rh);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vao);
        gl.glUseProgram(pZeigen);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tDruck);
        setI(gl, pZeigen, "uDruckTex", 0);
        gl.glUniform4f(ort(gl, pZeigen, "uRechteck"), rx, ry, rw, rh);
        float du, dv, u0, v0;
        if (lupe <= 0) {
            double s = Math.min(rw / (double) pw, rh / (double) ph);
            du = (float) (rw / (pw * s));
            dv = (float) (rh / (ph * s));
            u0 = 0.5f - du / 2;
            v0 = 0.5f - dv / 2;
        } else {
            du = (float) (rw / (pw * lupe));
            dv = (float) (rh / (ph * lupe));
            u0 = (float) Math.max(0, Math.min(1 - du, mu - du / 2));
            v0 = (float) Math.max(0, Math.min(1 - dv, (1 - mv) - dv / 2));
        }
        gl.glUniform4f(ort(gl, pZeigen, "uAusschnitt"), u0, v0, du, dv);
        setI(gl, pZeigen, "uLupe", lupe > 0 ? 1 : 0);
        gl.glUniform3f(ort(gl, pZeigen, "uGrund"), ((grund >> 16) & 255) / 255f, ((grund >> 8) & 255) / 255f, (grund & 255) / 255f);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** Zähler des letzten Drucks lesen (wartet auf die Grafikkarte; im selben {@code display()} aufrufen). */
    public void statistik(GL4 gl) {
        IntBuffer z = Buffers.newDirectIntBuffer(2);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, bZaehler);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 8, z);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        gestochen = z.get(0);
        gewechselt = z.get(1);
        zellen = nx * ny;
    }

    /** Anteil der gestochenen Punkte, die im letzten Bild wechselten. */
    public double flimmern() { return gestochen == 0 ? 0 : gewechselt / (double) gestochen; }

    /** Zeit der Grafikkarte für einen Druck (ms, ein Bild versetzt). */
    public double zeit() { return zeitMs; }

    public int druckBreite() { return pw; }
    public int druckHoehe() { return ph; }
    /** Die Tabelle des Tons, mit der zuletzt gedruckt wurde (Kopie). */
    public float[] tonKurve() { return tonKurve == null ? null : tonKurve.clone(); }

    /** Der letzte Druck (sRGB, oben zuerst). */
    public BufferedImage lesen(GL4 gl) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(pw * ph * 4);
        int[] alt = new int[1];
        gl.glGetIntegerv(GL4.GL_READ_FRAMEBUFFER_BINDING, alt, 0);
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, fDruck);
        gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, pw, ph, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, alt[0]);
        BufferedImage im = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        int[] px = new int[pw * ph];
        for (int y = 0; y < ph; y++)
            for (int x = 0; x < pw; x++) {
                int i = ((ph - 1 - y) * pw + x) * 4;
                px[y * pw + x] = ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255);
            }
        im.setRGB(0, 0, pw, ph, px, 0, pw);
        return im;
    }

    /** Lädt ein Bild als Textur (RGBA8, Zeile 0 unten, wie ein Bild der Grafikkarte); gibt die Textur zurück. */
    public static int textur(GL4 gl, BufferedImage bild) {
        int w = bild.getWidth(), h = bild.getHeight();
        int[] px = bild.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        for (int y = h - 1; y >= 0; y--)
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                bb.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) 255);
            }
        bb.flip();
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, w, h, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        nah(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    public void dispose(GL4 gl) {
        for (int p : new int[]{pHell, pHisto, pPunkte, pDruck, pZeigen}) if (p != 0) gl.glDeleteProgram(p);
        gl.glDeleteBuffers(5, new int[]{bHisto, bZellen[0], bZellen[1], bZellen[2], bZaehler}, 0);
        loeschen(gl);
        if (fHell != 0) { gl.glDeleteFramebuffers(1, new int[]{fHell}, 0); gl.glDeleteTextures(1, new int[]{tHell}, 0); }
        fHell = tHell = hw = hh = 0;
        if (tBlau != 0) gl.glDeleteTextures(1, new int[]{tBlau}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (zeitQ[0] != 0) gl.glDeleteQueries(2, zeitQ, 0);
        pHell = pHisto = pPunkte = pDruck = pZeigen = vao = tBlau = 0;
        zeitQ[0] = zeitQ[1] = 0;
        orte.clear();
    }

    // ------------------------------------------------------------------ Ziele

    private void hellZiel(GL4 gl, int qw, int qh) {
        if (qw == hw && qh == hh && fHell != 0) return;
        if (fHell != 0) { gl.glDeleteFramebuffers(1, new int[]{fHell}, 0); gl.glDeleteTextures(1, new int[]{tHell}, 0); }
        hw = qw;
        hh = qh;
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        tHell = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tHell);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, qw, qh, 0, GL4.GL_RED, GL.GL_FLOAT, null);
        nah(gl);
        gl.glGenFramebuffers(1, t, 0);
        fHell = t[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fHell);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tHell, 0);
        pruefen(gl, "Helligkeit");
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Druck und Zellen für Breite und Seitenverhältnis der Quelle; neu, wenn sich etwas an der Gestalt ändert. */
    private void ziele(GL4 gl, int breite, int qw, int qh) {
        int nw = breite, nh = (int) Math.round(breite * (double) qh / qw);
        Punkte pu = stich.punkte;
        float s = (float) (stich.punktPx(nw) / pu.groesse);
        int reich = pu.reichweite();
        String neu = nw + "/" + nh + "/" + s + "/" + reich;
        if (neu.equals(gestalt) && tDruck != 0) return;
        loeschen(gl);
        gestalt = neu;
        pw = nw;
        ph = nh;
        zelle = s;
        nx = (int) Math.ceil(pw / s);
        ny = (int) Math.ceil(ph / s);
        zw = nx + 2 * reich + 1;
        zh = ny + 2 * reich + 1;
        int[] t = new int[3];
        gl.glGenTextures(3, t, 0);
        tZustand = t[0];
        tDruck = t[1];
        tGlatt = t[2];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tZustand);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R8UI, zw, zh);
        nah(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tGlatt);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R32F, zw, zh);
        nah(gl);
        stufen = 1 + (int) Math.floor(Math.log(Math.max(pw, ph)) / Math.log(2));
        gl.glBindTexture(GL.GL_TEXTURE_2D, tDruck);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, stufen, GL.GL_RGBA8, pw, ph);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glGenFramebuffers(1, t, 0);
        fDruck = t[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fDruck);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tDruck, 0);
        pruefen(gl, "Druck");
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        frisch = true;
    }

    /** Druck und Zellen löschen (die Helligkeit der Quelle bleibt). */
    private void loeschen(GL4 gl) {
        if (fDruck != 0) gl.glDeleteFramebuffers(1, new int[]{fDruck}, 0);
        if (tDruck != 0) gl.glDeleteTextures(3, new int[]{tDruck, tZustand, tGlatt}, 0);
        fDruck = tDruck = tZustand = tGlatt = 0;
        gestalt = "";
    }

    // ------------------------------------------------------------------ Gemeinsame Werte

    private void gemeinsam(GL4 gl, int p) {
        Punkte pu = stich.punkte;
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tBlau);
        setI(gl, p, "uBlau", 1);
        gl.glUniform1ui(ort(gl, p, "uSeedP"), Zufall.pcg(pu.seed));
        setF(gl, p, "uOrdnung", pu.ordnung);
        setF(gl, p, "uZittern", pu.zittern);
        setF(gl, p, "uGroesse", pu.groesse);
        setF(gl, p, "uStreuung", pu.streuung);
        setF(gl, p, "uLaenge", pu.laenge);
        gl.glUniform1fv(ort(gl, p, "uKurve"), Punkte.KURVE + 1, pu.kurve(), 0);
        setI(gl, p, "uReich", pu.reichweite());
        setF(gl, p, "uZelle", zelle);
        gl.glUniform1fv(ort(gl, p, "uTon"), Stich.FAECHER + 2, tonKurve, 0);
        setF(gl, p, "uLo", Stich.LO);
        setF(gl, p, "uHi", Stich.HI);
        gl.glUniform1ui(ort(gl, p, "uSeedW"), Zufall.pcg(stich.seed * 0x2545F491 + 17));
        gl.glUniform1ui(ort(gl, p, "uSeedK"), Zufall.pcg(stich.seed + 0x3C6EF372));
        setF(gl, p, "uWBreite", stich.wolkenBreite);
        setF(gl, p, "uWLaenge", stich.wolkenLaenge);
        setF(gl, p, "uWBahnen", stich.wolkenBahnen);
        setF(gl, p, "uWFlecken", stich.wolkenFlecken);
        setF(gl, p, "uWFleck", stich.wolkenFleck);
    }

    private static void leeren(GL4 gl, int b) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 257L * 4, Buffers.newDirectIntBuffer(257), GL4.GL_DYNAMIC_READ);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private static long[] histogramm(GL4 gl, int b) {
        IntBuffer ib = Buffers.newDirectIntBuffer(257);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 257L * 4, ib);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        long[] f = new long[257];
        for (int i = 0; i < 257; i++) f[i] = ib.get(i) & 0xFFFFFFFFL;
        return f;
    }

    // ------------------------------------------------------------------ Hilfen

    private static void nah(GL4 gl) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
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

    private int link(GL4 gl, String gemeinsam, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, "", vert), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, gemeinsam, frag);
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
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + gemeinsam + "\n#line 1\n" + GertschGl.source(file)}, null, 0);
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
