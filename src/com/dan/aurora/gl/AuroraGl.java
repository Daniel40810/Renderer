package com.dan.aurora.gl;

import com.dan.aurora.Aurora;
import com.dan.aurora.Emission;
import com.dan.aurora.Niederschlag;
import com.dan.aurora.Spektrum;
import com.dan.aurora.Vorhang;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * Das Polarlicht auf der Grafikkarte (OpenGL 4.3), Phase 2: derselbe Strahlengang wie {@link Aurora#strahlung} in Java,
 * als Fragment-Shader ({@code aurora.frag}). Die Tabellen der Vorhänge (Mitte, Mitte des Rots, Strahlen, Helligkeit
 * über u) liegen als 1D-Textur-Array mit Mip-Stufen (die Stufen der Java-Fassung), die Höhenprofile der Linien als
 * 2D-Textur. {@link #zeichnen} rechnet in ein Zwischenbild halber Auflösung und legt es additiv über das gebundene Ziel
 * (das Polarlicht ist optisch dünn, Sterne und Himmel scheinen durch); {@link #panorama} legt es additiv in ein
 * Rundumbild (Azimut von Nord, Höhe), wie {@code com.dan.sky} es für Spiegelungen führt.
 * <p>
 * Ausgabe: lineares sRGB in cd/m², mal {@link View#preExposure} durch {@link View#einheit} (Einheit des Wirts in cd/m²).
 * Die Achsen Ost, Nord und oben des Wirts kommen als Vektoren mit; Vorgabe ist das Koordinatensystem von {@code sky}
 * (x Ost, y oben, z Süd). Die Daten ({@link #daten}) dürfen in einem anderen Faden gebaut werden; {@link #laden}
 * gehört in den GL-Faden.
 */
public final class AuroraGl {
    /** Höchstens so viele Vorhänge (Uniform-Felder im Shader). */
    public static final int HOECHSTENS = 8;
    /** Mip-Stufen der Tabellen: ⌊log₂ 16 001⌋ + 1 = 14 Stufen bis 1 (mehr verweigert glTexStorage). */
    static final int STUFEN = 32 - Integer.numberOfLeadingZeros(Vorhang.STUETZEN);

    /** Ein Blick: Matrizen und Ziel wie bei {@code sky.gl.SkyGl.View}. */
    public static final class View {
        /** Inverse der Projektion mal Ansicht, spaltenweise (16). */
        public float[] invViewProj;
        public boolean reversedZ;
        /** Größe des Ziels in Bildpunkten. */
        public int width = 1280, height = 720;
        /** Vorbelichtung des Wirts (alle Werte werden damit multipliziert) und seine Einheit der Strahldichte in cd/m² (1: cd/m²). */
        public double preExposure = 1, einheit = 1;
        /** Anteil der Zielgröße für das Zwischenbild (0,5: ein Viertel der Bildpunkte). */
        public double aufloesung = 0.5;
        /** Richtungen Ost, Nord und oben in den Koordinaten des Wirts (Vorgabe wie com.dan.sky: x Ost, y oben, z Süd). */
        public double[] ost = {1, 0, 0}, nord = {0, 0, -1}, oben = {0, 1, 0};
    }

    /** Alles, was die Grafikkarte von einem Polarlicht braucht, in einem Augenblick; gebaut mit {@link #daten}. */
    public static final class Daten {
        public final int n;
        /** Je Vorhang vier Tabellen über u ({@link Vorhang#STUETZEN}): Mitte, Mitte des Rots, Strahlfaktor, Helligkeit. */
        public final float[][] tabellen;
        /** Je Vorhang 9 Zeilen über die Höhe (glatt 4 Linien, Strahlen 4 Linien, Rot), zeilenweise. */
        public final float[] profile;
        public final float[] breite, sigmaRot, strahlen, hUnten, hOben, vMin, vMax;
        public final float eOst, eNord, pOst, pNord, cotI, hMin, hMax, hFein, dh;
        public final float[] tau = new float[Emission.LINIEN], farbe = new float[3 * Emission.LINIEN];

        Daten(Aurora a) {
            List<Vorhang> vs = a.vorhaenge;
            n = Math.min(HOECHSTENS, vs.size());
            int h = Niederschlag.HOEHEN.length;
            tabellen = new float[n * 4][];
            profile = new float[n * 9 * h];
            breite = new float[n]; sigmaRot = new float[n]; strahlen = new float[n]; hUnten = new float[n]; hOben = new float[n]; vMin = new float[n]; vMax = new float[n];
            for (int k = 0; k < n; k++) {
                Vorhang v = vs.get(k);
                for (int t = 0; t < 4; t++) {
                    double[] q = v.tabelle(t);
                    float[] f = new float[q.length];
                    for (int i = 0; i < q.length; i++) f[i] = (float) q[i];
                    tabellen[k * 4 + t] = f;
                }
                for (int l = 0; l < Emission.LINIEN; l++)
                    for (int i = 0; i < h; i++) {
                        profile[((k * 9) + l) * h + i] = (float) v.profilGlatt[l][i];
                        profile[((k * 9) + 4 + l) * h + i] = (float) v.profilStrahl[l][i];
                    }
                for (int i = 0; i < h; i++) profile[((k * 9) + 8) * h + i] = (float) v.profilRot[i];
                breite[k] = (float) v.breite;
                sigmaRot[k] = (float) v.sigmaRot();
                strahlen[k] = (float) v.strahlen;
                hUnten[k] = (float) v.hUnten;
                hOben[k] = (float) v.hOben;
                vMin[k] = (float) v.vMin();
                vMax[k] = (float) v.vMax();
            }
            double[] r = a.rahmen(), hh = a.hoehen();
            eOst = (float) r[0]; eNord = (float) r[1]; pOst = (float) r[2]; pNord = (float) r[3]; cotI = (float) r[4];
            hMin = (float) hh[0]; hMax = (float) hh[1]; hFein = (float) hh[2];
            dh = (float) a.schritt();
            for (int l = 0; l < Emission.LINIEN; l++) {
                tau[l] = (float) a.optischeDicke(l);
                double[] c = Spektrum.farbeJeKr(l);
                for (int i = 0; i < 3; i++) farbe[3 * l + i] = (float) c[i];
            }
        }
    }

    private int prog, progPano, progZusammen, vao, tab, prof, zwischenTex, zwischenFbo, zwW, zwH, tabSchichten, profZeilen;
    private Daten geladen;
    private boolean ok;
    private String status = "Polarlicht: noch nicht gestartet";

    public boolean bereit() { return ok && geladen != null; }
    public String status() { return status; }

    public void init(GL4 gl) {
        prog = Glsl.program(gl, "Polarlicht", new String[0], "fullscreen.vert", "aurora.frag");
        progPano = Glsl.program(gl, "Polarlicht rundum", new String[]{"PANORAMA"}, "fullscreen.vert", "aurora.frag");
        progZusammen = Glsl.program(gl, "Polarlicht zusammen", new String[0], "fullscreen.vert", "zusammen.frag");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        ok = true;
        status = "Polarlicht: Shader übersetzt";
    }

    /** Die Daten eines (vorbereiteten) Polarlichts für die Grafikkarte; darf in jedem Faden laufen. */
    public static Daten daten(Aurora a) { return new Daten(a); }

    /** Tabellen und Profile in die Texturen (GL-Faden). */
    public void laden(GL4 gl, Daten d) {
        if (!ok) return;
        int n = Vorhang.STUETZEN, h = Niederschlag.HOEHEN.length;
        if (d.n == 0) { geladen = d; return; }
        if (tab == 0 || tabSchichten != d.n * 4) {
            if (tab != 0) gl.glDeleteTextures(1, new int[]{tab}, 0);
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            tab = t[0];
            tabSchichten = d.n * 4;
            gl.glBindTexture(GL4.GL_TEXTURE_1D_ARRAY, tab);
            gl.glTexStorage2D(GL4.GL_TEXTURE_1D_ARRAY, STUFEN, GL.GL_R32F, n, tabSchichten);
            gl.glTexParameteri(GL4.GL_TEXTURE_1D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
            gl.glTexParameteri(GL4.GL_TEXTURE_1D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL4.GL_TEXTURE_1D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        } else gl.glBindTexture(GL4.GL_TEXTURE_1D_ARRAY, tab);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        for (int s = 0; s < tabSchichten; s++) {
            FloatBuffer b = Buffers.newDirectFloatBuffer(d.tabellen[s]);
            gl.glTexSubImage2D(GL4.GL_TEXTURE_1D_ARRAY, 0, 0, s, n, 1, GL4.GL_RED, GL.GL_FLOAT, b);
        }
        gl.glGenerateMipmap(GL4.GL_TEXTURE_1D_ARRAY);
        gl.glBindTexture(GL4.GL_TEXTURE_1D_ARRAY, 0);
        if (prof == 0 || profZeilen != d.n * 9) {
            if (prof != 0) gl.glDeleteTextures(1, new int[]{prof}, 0);
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            prof = t[0];
            profZeilen = d.n * 9;
            gl.glBindTexture(GL.GL_TEXTURE_2D, prof);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_R32F, h, profZeilen);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        } else gl.glBindTexture(GL.GL_TEXTURE_2D, prof);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, h, profZeilen, GL4.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(d.profile));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        geladen = d;
        int err = gl.glGetError();
        status = String.format(java.util.Locale.GERMAN, "Polarlicht: %d Vorhänge, Tabellen %d × %d in %d Stufen, Schritt %.2f km%s", d.n, n, tabSchichten, STUFEN, d.dh,
                err == GL.GL_NO_ERROR ? "" : " · GL-Fehler 0x" + Integer.toHexString(err));
    }

    /** Die Uniforms des Strahlengangs für ein gebundenes Programm. */
    private void uniforms(GL4 gl, int p, Daten d, double scale, double[] ost, double[] nord, double[] oben) {
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL4.GL_TEXTURE_1D_ARRAY, tab);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uTab"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, prof);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uProf"), 1);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uN"), d.n);
        gl.glUniform1fv(gl.glGetUniformLocation(p, "uBreite[0]"), d.n, d.breite, 0);
        gl.glUniform1fv(gl.glGetUniformLocation(p, "uSigmaRot[0]"), d.n, d.sigmaRot, 0);
        gl.glUniform1fv(gl.glGetUniformLocation(p, "uStrahlen[0]"), d.n, d.strahlen, 0);
        gl.glUniform1fv(gl.glGetUniformLocation(p, "uHUnten[0]"), d.n, d.hUnten, 0);
        gl.glUniform1fv(gl.glGetUniformLocation(p, "uHOben[0]"), d.n, d.hOben, 0);
        gl.glUniform1fv(gl.glGetUniformLocation(p, "uVMin[0]"), d.n, d.vMin, 0);
        gl.glUniform1fv(gl.glGetUniformLocation(p, "uVMax[0]"), d.n, d.vMax, 0);
        gl.glUniform2f(gl.glGetUniformLocation(p, "uE"), d.eOst, d.eNord);
        gl.glUniform2f(gl.glGetUniformLocation(p, "uP"), d.pOst, d.pNord);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uCotI"), d.cotI);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uHMin"), d.hMin);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uHMax"), d.hMax);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uHFein"), d.hFein);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uDh"), d.dh);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uU"), (float) Vorhang.U);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uSchritt"), (float) Vorhang.SCHRITT);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uZeilen"), profZeilen);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uHoehen"), Niederschlag.HOEHEN.length);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uMaxLod"), STUFEN - 1);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uTau"), d.tau[0], d.tau[1], d.tau[2], d.tau[3]);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uFarbe[0]"), Emission.LINIEN, d.farbe, 0);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uScale"), (float) scale);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uOst"), (float) ost[0], (float) ost[1], (float) ost[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uNord"), (float) nord[0], (float) nord[1], (float) nord[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uOben"), (float) oben[0], (float) oben[1], (float) oben[2]);
    }

    /**
     * Das Polarlicht additiv über das gebundene Ziel: erst in ein Zwischenbild ({@link View#aufloesung}), dann
     * aufgemischt (GL_ONE, GL_ONE). Tiefe wird nicht geschrieben; Mischung und Tiefentest stehen danach wie vorher.
     */
    public void zeichnen(GL4 gl, View v) {
        if (!bereit() || geladen.n == 0) return;
        int[] prev = new int[1], vp = new int[4];
        byte[] tiefe = new byte[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glGetBooleanv(GL.GL_DEPTH_WRITEMASK, tiefe, 0);
        boolean blend = gl.glIsEnabled(GL.GL_BLEND), depth = gl.glIsEnabled(GL.GL_DEPTH_TEST);
        int w = Math.max(8, (int) Math.round(v.width * v.aufloesung)), h = Math.max(8, (int) Math.round(v.height * v.aufloesung));
        zwischen(gl, w, h);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, zwischenFbo);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glBindVertexArray(vao);
        gl.glUseProgram(prog);
        uniforms(gl, prog, geladen, v.preExposure / v.einheit, v.ost, v.nord, v.oben);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uInvVP"), 1, false, v.invViewProj != null ? v.invViewProj : EINS, 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uReversed"), v.reversedZ ? 1 : 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // aufmischen
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glUseProgram(progZusammen);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, zwischenTex);
        gl.glUniform1i(gl.glGetUniformLocation(progZusammen, "uBild"), 0);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        if (!blend) gl.glDisable(GL.GL_BLEND);
        if (depth) gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(tiefe[0] != 0);
    }

    /** Das Polarlicht additiv in das gebundene Rundumbild (ganzer Viewport; Azimut von Nord über die Breite, Höhe über die Höhe). */
    public void panorama(GL4 gl, double preExposure, double einheit, double[] ost, double[] nord, double[] oben) {
        if (!bereit() || geladen.n == 0) return;
        boolean blend = gl.glIsEnabled(GL.GL_BLEND), depth = gl.glIsEnabled(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glBindVertexArray(vao);
        gl.glUseProgram(progPano);
        uniforms(gl, progPano, geladen, preExposure / einheit, ost, nord, oben);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        if (!blend) gl.glDisable(GL.GL_BLEND);
        if (depth) gl.glEnable(GL.GL_DEPTH_TEST);
    }

    /** Wie {@link #panorama} mit den Achsen von com.dan.sky. */
    public void panorama(GL4 gl, double preExposure, double einheit) {
        panorama(gl, preExposure, einheit, new double[]{1, 0, 0}, new double[]{0, 0, -1}, new double[]{0, 1, 0});
    }

    private static final float[] EINS = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    private void zwischen(GL4 gl, int w, int h) {
        if (zwischenFbo != 0 && zwW == w && zwH == h) return;
        if (zwischenFbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{zwischenFbo}, 0);
            gl.glDeleteTextures(1, new int[]{zwischenTex}, 0);
        }
        int[] t = new int[1], f = new int[1];
        gl.glGenTextures(1, t, 0);
        zwischenTex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, zwischenTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glGenFramebuffers(1, f, 0);
        zwischenFbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, zwischenFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, zwischenTex, 0);
        zwW = w;
        zwH = h;
    }

    public void dispose(GL4 gl) {
        if (!ok) return;
        for (int p : new int[]{prog, progPano, progZusammen}) if (p != 0) gl.glDeleteProgram(p);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (tab != 0) gl.glDeleteTextures(1, new int[]{tab}, 0);
        if (prof != 0) gl.glDeleteTextures(1, new int[]{prof}, 0);
        if (zwischenFbo != 0) gl.glDeleteFramebuffers(1, new int[]{zwischenFbo}, 0);
        if (zwischenTex != 0) gl.glDeleteTextures(1, new int[]{zwischenTex}, 0);
        prog = progPano = progZusammen = vao = tab = prof = zwischenFbo = zwischenTex = 0;
        ok = false;
    }
}
