package com.dan.beacon.gl;

import com.dan.beacon.Beacon;
import com.dan.beacon.ScatterGrid;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Das Leuchtfeuer auf der Grafikkarte. Je Bild:
 * <ol>
 * <li>{@link #update}: Sektortabelle und Höhenprofil hochladen (nur bei Änderung), das Streugitter (bei neuer Version)
 * und daraus die Lichtverteilung als Rundumbild rechnen ({@link #patternTexture()}, RGBA32F, RGB in cd). Dieses Bild
 * lesen der Strahl, der Lichtpunkt, fremde Shader über {@code beacon.glsl} und etwa ein Meer als Punktlicht.</li>
 * <li>{@link #drawBeam}: der sichtbare Strahl in verkleinerter Auflösung, dann additiv aufs Bild (nach der Szene, mit
 * einer Kopie ihrer Tiefe).</li>
 * <li>{@link #drawGlare}: Lichtpunkte für dieses und weitere Feuer (Nachbartürme), mit Kern und Hof.</li>
 * </ol>
 * Alles, was ins Bild geht, ist linear und vorbelichtet (View.preExposure) und wird mit ONE, ONE addiert.
 */
public final class BeaconGl {

    /** Blick und Ziel. */
    public static final class View {
        public float[] viewProj, invViewProj;
        public double[] eye = {0, 2, 0};
        public boolean reversedZ = true;
        public int width = 1, height = 1;
        public double fovyDeg = 50;
        /** Tiefe der Szene in voller Auflösung (Kopie, darf nicht das Ziel sein). */
        public int depthTexture;
        /** Rahmenpuffer und Ansicht des Ziels (HDR). */
        public int targetFbo;
        public double preExposure = 1;
        public double maxDistance = 20_000;
        /** Wolkenuntergrenze in m (≤ 0: keine), ihre Albedo und optional eine Karte des durchgelassenen Lichts von oben. */
        public double ceilingHeight, ceilingAlbedo = 0.7;
        public int ceilingTexture;
        public double[] ceilingRect = {0, 0, 1};
        /** Deckkraft des Strahls (Bedienfeld), 1 = physikalisch. */
        public double strength = 1;
        /** Rauschen je Bildpunkt (true) oder feste Mitte der Schritte (Standbild ohne Rauschen). */
        public boolean jitter = true;
    }

    /** Ein Lichtpunkt: Ort und Beleuchtungsstärke am Auge (lx, RGB, ohne Vorbelichtung). */
    public record Glare(double x, double y, double z, double[] lux, double core, double testOffset) { }

    private int patternProg, beamProg, compositeProg, glareProg, checkProg, vao;
    private int lutTex, profileTex, patternTex, patternFbo, gridTex, nearTex, beamTex, beamFbo, ceilDummy;
    private int pw, ph, gAz, gEl, gR, beamW, beamH, gridVersion = -1;
    private float[] lastLut, lastProfile;
    private Object lastField;
    private Beacon.Snapshot snap;
    private ScatterGrid grid;
    private int frame;
    private boolean ready;
    private String info = "";
    private double nearR0, nearR1, nearY0, nearY1;

    public boolean ready() { return ready; }
    public String info() { return info; }
    public int patternTexture() { return patternTex; }
    public int patternWidth() { return pw; }
    public int patternHeight() { return ph; }

    public void init(GL4 gl) {
        String fs = read("fullscreen.vert");
        patternProg = program(gl, fs, read("pattern.frag"));
        beamProg = program(gl, fs, read("beam.frag"));
        compositeProg = program(gl, fs, read("composite.frag"));
        checkProg = program(gl, fs, read("check.frag"));
        glareProg = program(gl, read("glare.vert"), read("glare.frag"));
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        int[] t = new int[5];
        gl.glGenTextures(5, t, 0);
        lutTex = t[0];
        profileTex = t[1];
        gridTex = t[2];
        nearTex = t[3];
        ceilDummy = t[4];
        gl.glBindTexture(GL.GL_TEXTURE_2D, ceilDummy);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_R32F, 1, 1, 0, GL2ES2.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(new float[]{1}));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        ready = true;
    }

    /** Der Baustein für fremde Shader (nach #version einsetzen). */
    public static String includeSource() { return read("beacon.glsl"); }

    // ------------------------------------------------------------------ Hochladen

    /** Zustand und Streugitter (darf null sein) übernehmen und das Rundumbild rechnen. */
    public void update(GL4 gl, Beacon.Snapshot s, ScatterGrid g) {
        snap = s;
        Beacon.Quality q = s.quality();
        int[] prev = new int[1], vp = new int[4], va = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glGetIntegerv(GL2ES3.GL_VERTEX_ARRAY_BINDING, va, 0);
        boolean blend = gl.glIsEnabled(GL.GL_BLEND), depth = gl.glIsEnabled(GL.GL_DEPTH_TEST);
        if (s.lut() != lastLut) {
            gl.glBindTexture(GL4.GL_TEXTURE_1D, lutTex);
            gl.glTexImage1D(GL4.GL_TEXTURE_1D, 0, GL.GL_RGB32F, Beacon.LUT_N, 0, GL.GL_RGB, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(s.lut()));
            param1(gl, GL.GL_REPEAT);
            lastLut = s.lut();
        }
        if (s.profile() != lastProfile) {
            gl.glBindTexture(GL4.GL_TEXTURE_1D, profileTex);
            gl.glTexImage1D(GL4.GL_TEXTURE_1D, 0, GL.GL_R32F, Beacon.PROFILE_N, 0, GL2ES2.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(s.profile()));
            param1(gl, GL.GL_CLAMP_TO_EDGE);
            lastProfile = s.profile();
        }
        if (s.field() != lastField) {
            var f = s.field();
            float[] rg = new float[f.nr() * f.ny() * 2];
            for (int i = 0; i < f.nr() * f.ny(); i++) { rg[2 * i] = f.nearF()[i]; rg[2 * i + 1] = f.nearS()[i]; }
            gl.glBindTexture(GL.GL_TEXTURE_2D, nearTex);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RG32F, f.nr(), f.ny(), 0, GL2ES2.GL_RG, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(rg));
            param2(gl, GL.GL_CLAMP_TO_EDGE, GL.GL_CLAMP_TO_EDGE, GL.GL_LINEAR);
            nearR0 = f.r0();
            nearR1 = f.r1();
            nearY0 = f.y0();
            nearY1 = f.y1();
            lastField = f;
        }
        grid = g;
        if (g != null && g.version() != gridVersion) {
            gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, gridTex);
            gl.glTexImage3D(GL2ES2.GL_TEXTURE_3D, 0, GL.GL_R32F, g.nAz, g.nEl, g.nR, 0, GL2ES2.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(g.data()));
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL.GL_CLAMP_TO_EDGE);
            gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
            gAz = g.nAz;
            gEl = g.nEl;
            gR = g.nR;
            gridVersion = g.version();
        } else if (g == null && gAz == 0) {
            gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, gridTex);
            gl.glTexImage3D(GL2ES2.GL_TEXTURE_3D, 0, GL.GL_R32F, 1, 1, 1, 0, GL2ES2.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(new float[]{0}));
            gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
            gAz = 1;
        }
        // Rundumbild
        if (pw != q.patternW || ph != q.patternH) {
            if (patternTex != 0) {
                gl.glDeleteFramebuffers(1, new int[]{patternFbo}, 0);
                gl.glDeleteTextures(1, new int[]{patternTex}, 0);
            }
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            patternTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, patternTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, q.patternW, q.patternH);
            param2(gl, GL.GL_REPEAT, GL.GL_CLAMP_TO_EDGE, GL.GL_LINEAR);
            gl.glGenFramebuffers(1, t, 0);
            patternFbo = t[0];
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, patternFbo);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, patternTex, 0);
            pw = q.patternW;
            ph = q.patternH;
            info = String.format(Locale.GERMAN, "Rundumbild %d × %d, Gitter %d × %d × %d, %d Schritte", pw, ph, q.gridAz, q.gridEl, q.gridR, q.beamSteps);
        }
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, patternFbo);
        gl.glViewport(0, 0, pw, ph);
        gl.glUseProgram(patternProg);
        tex(gl, patternProg, "uLut", 0, GL4.GL_TEXTURE_1D, lutTex);
        tex(gl, patternProg, "uProfile", 1, GL4.GL_TEXTURE_1D, profileTex);
        panels(gl, patternProg, "uPanels", "uPanelCount", s);
        gl.glUniform4f(loc(gl, patternProg, "uState"), s.optic() == Beacon.Optic.ROTATING ? 1f : 0f, (float) s.rotDeg(), (float) s.gain(), (float) s.level());
        gl.glUniform3f(loc(gl, patternProg, "uTint"), (float) s.tint()[0], (float) s.tint()[1], (float) s.tint()[2]);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glBindVertexArray(va[0]);
        if (blend) gl.glEnable(GL.GL_BLEND);
        if (depth) gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(0);
        frame++;
    }

    /**
     * Setzt die bcn*-Größen von {@code beacon.glsl} im (schon benutzten) Programm prog; belegt die Textureinheiten
     * unit, unit+1, unit+2. pre = Vorbelichtung des Programms.
     */
    public void bind(GL4 gl, int prog, int unit, double pre) {
        Beacon.Snapshot s = snap;
        if (s == null) return;
        tex(gl, prog, "bcnPattern", unit, GL.GL_TEXTURE_2D, patternTex);
        tex(gl, prog, "bcnNear", unit + 1, GL.GL_TEXTURE_2D, nearTex);
        tex(gl, prog, "bcnLut", unit + 2, GL4.GL_TEXTURE_1D, lutTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform4f(loc(gl, prog, "bcnPos"), (float) s.x(), (float) s.y(), (float) s.z(), (float) s.level());
        gl.glUniform4f(loc(gl, prog, "bcnNearP"), (float) nearR0, (float) nearR1, (float) nearY0, (float) nearY1);
        gl.glUniform4f(loc(gl, prog, "bcnLens"), (float) s.lensOuter(), (float) s.lensHalfHeight(), (float) s.screenRadius(), (float) s.lumen());
        gl.glUniform4f(loc(gl, prog, "bcnRot"), s.optic() == Beacon.Optic.ROTATING ? 1f : 0f, (float) s.rotDeg(), (float) s.panelWidth(),
                (float) (2 * s.field().lampRadius() / 0.39));
        gl.glUniform4f(loc(gl, prog, "bcnTint"), (float) s.tint()[0], (float) s.tint()[1], (float) s.tint()[2], (float) s.maxCd());
        gl.glUniform1f(loc(gl, prog, "bcnPre"), (float) pre);
        panels(gl, prog, "bcnPanels", "bcnPanelCount", s);
    }

    // ------------------------------------------------------------------ Strahl

    /** Der sichtbare Strahl über das Bild im Ziel von View (additiv). */
    public void drawBeam(GL4 gl, View v) {
        Beacon.Snapshot s = snap;
        if (s == null || !s.lit() || v.strength <= 0) { beamMean = 0; return; }
        Beacon.Quality q = s.quality();
        int w = Math.max(16, v.width / q.beamDownscale), h = Math.max(16, v.height / q.beamDownscale);
        if (w != beamW || h != beamH) {
            if (beamTex != 0) {
                gl.glDeleteFramebuffers(1, new int[]{beamFbo}, 0);
                gl.glDeleteTextures(1, new int[]{beamTex}, 0);
            }
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            beamTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, beamTex);
            int levels = 1 + (int) Math.floor(Math.log(Math.max(w, h)) / Math.log(2));
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, levels, GL.GL_RGBA32F, w, h);
            param2(gl, GL.GL_CLAMP_TO_EDGE, GL.GL_CLAMP_TO_EDGE, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL2ES3.GL_TEXTURE_MAX_LEVEL, levels - 1);
            meanLevel = 0;
            while ((w >> meanLevel) > 16 || (h >> meanLevel) > 16) meanLevel++;
            gl.glGenFramebuffers(1, t, 0);
            beamFbo = t[0];
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, beamFbo);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, beamTex, 0);
            beamW = w;
            beamH = h;
        }
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(false);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, beamFbo);
        gl.glViewport(0, 0, w, h);
        gl.glUseProgram(beamProg);
        setBeam(gl, s, v, q.beamSteps);
        gl.glUniform1f(loc(gl, beamProg, "uJitter"), v.jitter ? -1f : 0.5f);
        gl.glUniform2f(loc(gl, beamProg, "uCheckRays"), 0f, 0f);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        requestMean(gl, v.preExposure * v.strength);
        // auf das volle Bild
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, v.targetFbo);
        gl.glViewport(0, 0, v.width, v.height);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glUseProgram(compositeProg);
        tex(gl, compositeProg, "uBeam", 0, GL.GL_TEXTURE_2D, beamTex);
        tex(gl, compositeProg, "uDepth", 1, GL.GL_TEXTURE_2D, v.depthTexture);
        gl.glUniformMatrix4fv(loc(gl, compositeProg, "uInvVP"), 1, false, v.invViewProj, 0);
        gl.glUniform3f(loc(gl, compositeProg, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1i(loc(gl, compositeProg, "uReversed"), v.reversedZ ? 1 : 0);
        gl.glUniform1f(loc(gl, compositeProg, "uMaxDist"), (float) v.maxDistance);
        gl.glUniform1f(loc(gl, compositeProg, "uStrength"), (float) v.strength);
        gl.glUniform4f(loc(gl, compositeProg, "uCeil"), (float) v.ceilingHeight, 0f, v.ceilingHeight > 0 ? 1f : 0f, 0f);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDisable(GL.GL_BLEND);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUseProgram(0);
        gl.glDepthMask(true);
    }

    // ------------------------------------------------------------------ mittlere Leuchtdichte des Strahls

    private int meanLevel, meanPbo, meanW, meanH;
    private long meanFence;
    private double meanPre = 1, beamMean = Double.NaN;

    /**
     * Mittlere Leuchtdichte des Strahls über das ganze Bild in cd/m² (ohne Vorbelichtung), für die Augenanpassung.
     * Kommt aus der Mipmap des Strahlbilds und wird ohne Warten ein Bild später zurückgelesen; NaN, solange noch
     * nichts da ist. Schmale Strahlen einer Drehlinse fallen so nicht zwischen ein paar Probeblicke.
     */
    public double beamMeanLuminance() { return beamMean; }

    private void requestMean(GL4 gl, double pre) {
        // die vorige Abfrage abholen, wenn sie fertig ist
        if (meanFence != 0) {
            int st = gl.glClientWaitSync(meanFence, 0, 0);
            if (st == GL4.GL_ALREADY_SIGNALED || st == GL4.GL_CONDITION_SATISFIED) {
                gl.glDeleteSync(meanFence);
                meanFence = 0;
                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, meanPbo);
                java.nio.ByteBuffer bb = gl.glMapBufferRange(GL4.GL_PIXEL_PACK_BUFFER, 0, (long) meanW * meanH * 16, GL4.GL_MAP_READ_BIT);
                if (bb != null) {
                    java.nio.FloatBuffer fb = bb.order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
                    double sum = 0;
                    for (int i = 0; i < meanW * meanH; i++)
                        sum += 0.2126 * fb.get(i * 4) + 0.7152 * fb.get(i * 4 + 1) + 0.0722 * fb.get(i * 4 + 2);
                    gl.glUnmapBuffer(GL4.GL_PIXEL_PACK_BUFFER);
                    beamMean = sum / (meanW * meanH) / Math.max(1e-30, meanPre);
                }
                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, 0);
            } else return;      // noch nicht fertig: keine neue Abfrage
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, beamTex);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        int w = Math.max(1, beamW >> meanLevel), h = Math.max(1, beamH >> meanLevel);
        if (meanPbo == 0) {
            int[] b = new int[1];
            gl.glGenBuffers(1, b, 0);
            meanPbo = b[0];
        }
        gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, meanPbo);
        if (w != meanW || h != meanH) {
            gl.glBufferData(GL4.GL_PIXEL_PACK_BUFFER, (long) w * h * 16, null, GL4.GL_STREAM_READ);
            meanW = w;
            meanH = h;
        }
        gl.glGetTexImage(GL.GL_TEXTURE_2D, meanLevel, GL.GL_RGBA, GL.GL_FLOAT, 0);
        gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        meanFence = gl.glFenceSync(GL4.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        meanPre = pre;
    }

    /** Der Strahl ist aus (Schalter, Feuer dunkel): die Messung zählt 0. */
    public void beamOff() { beamMean = 0; }

    private void setBeam(GL4 gl, Beacon.Snapshot s, View v, int steps) {
        int p = beamProg;
        bind(gl, p, 4, v.preExposure);
        tex(gl, p, "uDepth", 0, GL.GL_TEXTURE_2D, v.depthTexture);
        tex(gl, p, "uGrid", 1, GL2ES2.GL_TEXTURE_3D, gridTex);
        tex(gl, p, "uCeilMap", 2, GL.GL_TEXTURE_2D, v.ceilingTexture != 0 ? v.ceilingTexture : ceilDummy);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        if (v.invViewProj != null) gl.glUniformMatrix4fv(loc(gl, p, "uInvVP"), 1, false, v.invViewProj, 0);
        gl.glUniform3f(loc(gl, p, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1i(loc(gl, p, "uReversed"), v.reversedZ ? 1 : 0);
        ScatterGrid g = grid;
        gl.glUniform3f(loc(gl, p, "uGridP"), g != null ? (float) g.maxElDeg : 40f, g != null ? (float) g.r0 : 0.5f, g != null ? (float) g.r1 : 12_000f);
        gl.glUniform1f(loc(gl, p, "uHaze"), (float) s.hazePerM());
        gl.glUniform1i(loc(gl, p, "uSteps"), steps);
        gl.glUniform1i(loc(gl, p, "uPanelSteps"), Beacon.Snapshot.panelSteps(steps));
        gl.glUniform1f(loc(gl, p, "uWedge"), (float) s.wedgeDeg());
        gl.glUniform1f(loc(gl, p, "uMaxDist"), (float) v.maxDistance);
        gl.glUniform1i(loc(gl, p, "uFrame"), frame);
        gl.glUniform4f(loc(gl, p, "uCeil"), (float) v.ceilingHeight, (float) v.ceilingAlbedo, v.ceilingHeight > 0 ? 1f : 0f, 0f);
        gl.glUniform4f(loc(gl, p, "uCeilRect"), (float) v.ceilingRect[0], (float) v.ceilingRect[1], (float) v.ceilingRect[2], v.ceilingTexture != 0 ? 1f : 0f);
    }

    // ------------------------------------------------------------------ Lichtpunkte

    /** Lichtpunkte über das Bild (additiv); höchstens acht. */
    public void drawGlare(GL4 gl, View v, List<Glare> lights) {
        if (lights.isEmpty()) return;
        int n = Math.min(8, lights.size());
        float[] pos = new float[32], e = new float[32];
        for (int i = 0; i < n; i++) {
            Glare g = lights.get(i);
            pos[i * 4] = (float) g.x();
            pos[i * 4 + 1] = (float) g.y();
            pos[i * 4 + 2] = (float) g.z();
            pos[i * 4 + 3] = (float) g.core();
            for (int k = 0; k < 3; k++) e[i * 4 + k] = (float) (g.lux()[k] * v.preExposure);
            e[i * 4 + 3] = (float) g.testOffset();
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, v.targetFbo);
        gl.glViewport(0, 0, v.width, v.height);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glUseProgram(glareProg);
        gl.glUniformMatrix4fv(loc(gl, glareProg, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform4fv(loc(gl, glareProg, "uGlarePos"), 8, pos, 0);
        gl.glUniform4fv(loc(gl, glareProg, "uGlareE"), 8, e, 0);
        gl.glUniform3f(loc(gl, glareProg, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        tex(gl, glareProg, "uDepth", 0, GL.GL_TEXTURE_2D, v.depthTexture);
        gl.glUniform1i(loc(gl, glareProg, "uReversed"), v.reversedZ ? 1 : 0);
        gl.glUniform2f(loc(gl, glareProg, "uViewport"), v.width, v.height);
        gl.glUniform1f(loc(gl, glareProg, "uSize"), 120f);
        double px = 2 * Math.tan(Math.toRadians(v.fovyDeg) / 2) / v.height;
        gl.glUniform1f(loc(gl, glareProg, "uPixelSr"), (float) (px * px));
        gl.glUniform1f(loc(gl, glareProg, "uDegPerPx"), (float) Math.toDegrees(px));
        gl.glBindVertexArray(vao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, n);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(0);
    }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Vergleicht die Grafikkarte mit dem Prozessor: das Rundumbild an 2 048 Texelmitten gegen
     * {@link Beacon.Snapshot#intensityAzEl}, dann 64 Blicke durch den Strahl gegen {@link Beacon.Snapshot#beamRadiance}
     * (beide mit dem Rundumbild als Lichtquelle, wie im Shader). „Grafikkarte = Prozessor: Lichtstärke ± 0,01 %,
     * Strahl ± 0,2 % (Median), …“
     */
    public String selfCheck(GL4 gl, Beacon.Snapshot s, ScatterGrid g) {
        update(gl, s, g);
        int[] prev = new int[1], vp = new int[4], va = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glGetIntegerv(GL2ES3.GL_VERTEX_ARRAY_BINDING, va, 0);
        boolean blend = gl.glIsEnabled(GL.GL_BLEND), depth = gl.glIsEnabled(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        int[] f = new int[2];
        gl.glGenFramebuffers(2, f, 0);
        // 1 Rundumbild
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, 64, 32);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, t[0], 0);
        gl.glViewport(0, 0, 64, 32);
        gl.glUseProgram(checkProg);
        tex(gl, checkProg, "uPattern", 0, GL.GL_TEXTURE_2D, patternTex);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(64 * 32 * 4);
        gl.glReadPixels(0, 0, 64, 32, GL.GL_RGBA, GL.GL_FLOAT, fb);
        List<Double> errP = new ArrayList<>();
        double scale = 0;
        float[] pat = cpuPattern(s, pw, ph);
        for (int i = 0; i < 64 * 32; i++) scale = Math.max(scale, fb.get(i * 4 + 1));
        for (int i = 0; i < 64 * 32; i++) {
            int code = (int) fb.get(i * 4 + 3), px = code / 1024, py = code % 1024;
            double cpu = pat[(py * pw + px) * 4 + 1], gpu = fb.get(i * 4 + 1);
            if (cpu > scale * 1e-3 || gpu > scale * 1e-3) errP.add(Math.abs(gpu - cpu) / Math.max(cpu, scale * 1e-3));
        }
        // 2 Strahl: 64 Blicke um das Feuer
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[1]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, 8, 8);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[1]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, t[1], 0);
        gl.glViewport(0, 0, 8, 8);
        gl.glUseProgram(beamProg);
        View v = new View();
        v.invViewProj = new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        v.depthTexture = ceilDummy;
        setBeam(gl, s, v, s.quality().beamSteps);
        float[] ro = new float[256], rd = new float[256];
        java.util.Random rnd = new java.util.Random(s.quality().ordinal() * 31 + 7);
        double[][] O = new double[64][], Dd = new double[64][];
        double[] tm = new double[64];
        for (int i = 0; i < 64; i++) {
            double ang = rnd.nextDouble() * 2 * Math.PI, dist = 20 + rnd.nextDouble() * 900;
            double[] o = {s.x() + Math.sin(ang) * dist, 2 + rnd.nextDouble() * 40, s.z() - Math.cos(ang) * dist};
            // Blick ungefähr am Feuer vorbei
            double tx = s.x() + (rnd.nextDouble() - 0.5) * 200, ty = s.y() + (rnd.nextDouble() - 0.5) * 20, tz = s.z() + (rnd.nextDouble() - 0.5) * 200;
            double dx = tx - o[0], dy = ty - o[1], dz = tz - o[2], dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double[] d = {dx / dl, dy / dl, dz / dl};
            O[i] = o;
            Dd[i] = d;
            tm[i] = 200 + rnd.nextDouble() * 4000;
            ro[i * 4] = (float) o[0]; ro[i * 4 + 1] = (float) o[1]; ro[i * 4 + 2] = (float) o[2]; ro[i * 4 + 3] = (float) tm[i];
            rd[i * 4] = (float) d[0]; rd[i * 4 + 1] = (float) d[1]; rd[i * 4 + 2] = (float) d[2];
        }
        gl.glUniform4fv(loc(gl, beamProg, "uRayO"), 64, ro, 0);
        gl.glUniform4fv(loc(gl, beamProg, "uRayD"), 64, rd, 0);
        gl.glUniform2f(loc(gl, beamProg, "uCheckRays"), 1f, 0f);
        gl.glUniform1f(loc(gl, beamProg, "uJitter"), 0.5f);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer bb = Buffers.newDirectFloatBuffer(64 * 4);
        gl.glReadPixels(0, 0, 8, 8, GL.GL_RGBA, GL.GL_FLOAT, bb);
        List<Double> errB = new ArrayList<>();
        double bmax = 0;
        double[] cpuB = new double[64];
        for (int i = 0; i < 64; i++) {
            double[] L = s.beamRadiance(O[i], Dd[i], tm[i], g, s.quality().beamSteps, 0.5, w -> samplePattern(pat, pw, ph, w));
            cpuB[i] = L[1];
            bmax = Math.max(bmax, L[1]);
        }
        for (int i = 0; i < 64; i++) {
            double gpu = bb.get(i * 4 + 1), cpu = cpuB[i];
            if (cpu > bmax * 1e-3) errB.add(Math.abs(gpu - cpu) / cpu);
        }
        gl.glUniform2f(loc(gl, beamProg, "uCheckRays"), 0f, 0f);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glBindVertexArray(va[0]);
        if (blend) gl.glEnable(GL.GL_BLEND);
        if (depth) gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(0);
        gl.glDeleteFramebuffers(2, f, 0);
        gl.glDeleteTextures(2, t, 0);
        if (errP.isEmpty() || errB.isEmpty()) return "";
        double[] ep = errP.stream().mapToDouble(Double::doubleValue).sorted().toArray(), eb = errB.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        lastCheck = new double[]{ep[ep.length / 2], ep[ep.length - 1], eb[eb.length / 2], eb[eb.length - 1]};
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Lichtstärke ± %.3f %% (Median), größte %.2f %%, %d Proben; Strahl ± %.2f %% (Median), größte %.1f %%, %d Blicke",
                ep[ep.length / 2] * 100, ep[ep.length - 1] * 100, ep.length, eb[eb.length / 2] * 100, eb[eb.length - 1] * 100, eb.length);
    }

    private double[] lastCheck;

    /** Die Zahlen des letzten Selbsttests: Median und größte relative Abweichung der Lichtstärke, dann des Strahls. */
    public double[] lastCheck() { return lastCheck == null ? null : lastCheck.clone(); }

    /** Das Rundumbild auf dem Prozessor (RGBA je Texelmitte, wie pattern.frag). */
    public static float[] cpuPattern(Beacon.Snapshot s, int w, int h) {
        float[] p = new float[w * h * 4];
        for (int j = 0; j < h; j++) {
            double sv = (j + 0.5) / h * 2 - 1, el = Math.signum(sv) * sv * sv * 90;
            for (int i = 0; i < w; i++) {
                double az = (i + 0.5) / w * 360;
                double[] c = s.intensityAzEl(az, el);
                int o = (j * w + i) * 4;
                p[o] = (float) c[0];
                p[o + 1] = (float) c[1];
                p[o + 2] = (float) c[2];
                p[o + 3] = 1;
            }
        }
        return p;
    }

    /** Das Rundumbild bilinear lesen wie {@code texture()} (Azimut wiederholt, Höhe geklemmt). */
    public static double[] samplePattern(float[] p, int w, int h, double[] dir) {
        double az = Math.atan2(dir[0], -dir[2]) / (2 * Math.PI);
        az -= Math.floor(az);
        double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dir[1]))));
        double v = 0.5 + 0.5 * Math.signum(el) * Math.sqrt(Math.abs(el) / 90);
        double u = az * w - 0.5, vv = v * h - 0.5;
        int i0 = (int) Math.floor(u), j0 = (int) Math.floor(vv);
        double fu = u - i0, fv = vv - j0;
        double[] out = new double[3];
        for (int k = 0; k < 4; k++) {
            int i = Math.floorMod(i0 + (k & 1), w), j = Math.max(0, Math.min(h - 1, j0 + (k >> 1)));
            double wt = ((k & 1) != 0 ? fu : 1 - fu) * ((k >> 1) != 0 ? fv : 1 - fv);
            for (int c = 0; c < 3; c++) out[c] += wt * p[(j * w + i) * 4 + c];
        }
        return out;
    }

    public void dispose(GL4 gl) {
        for (int p : new int[]{patternProg, beamProg, compositeProg, glareProg, checkProg}) if (p != 0) gl.glDeleteProgram(p);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        int[] tx = {lutTex, profileTex, gridTex, nearTex, ceilDummy, patternTex, beamTex};
        gl.glDeleteTextures(tx.length, tx, 0);
        int[] fb = {patternFbo, beamFbo};
        gl.glDeleteFramebuffers(2, fb, 0);
        patternProg = beamProg = compositeProg = glareProg = checkProg = vao = 0;
        lutTex = profileTex = gridTex = nearTex = ceilDummy = patternTex = beamTex = patternFbo = beamFbo = 0;
        pw = ph = beamW = beamH = gAz = 0;
        gridVersion = -1;
        lastLut = lastProfile = null;
        lastField = null;
        ready = false;
    }

    // ------------------------------------------------------------------ Kleinkram

    private void panels(GL4 gl, int prog, String arr, String count, Beacon.Snapshot s) {
        float[] p = Arrays.copyOf(s.panels(), 64);
        gl.glUniform1fv(loc(gl, prog, arr), 64, p, 0);
        gl.glUniform1i(loc(gl, prog, count), Math.min(64, s.panels().length));
    }

    private static void tex(GL4 gl, int prog, String name, int unit, int target, int tex) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(target, tex);
        gl.glUniform1i(gl.glGetUniformLocation(prog, name), unit);
    }

    private static int loc(GL4 gl, int prog, String name) { return gl.glGetUniformLocation(prog, name); }

    private static void param1(GL4 gl, int wrap) {
        gl.glTexParameteri(GL4.GL_TEXTURE_1D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_1D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_1D, GL.GL_TEXTURE_WRAP_S, wrap);
        gl.glBindTexture(GL4.GL_TEXTURE_1D, 0);
    }

    private static void param2(GL4 gl, int ws, int wt, int filter) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, filter);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, filter);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, ws);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, wt);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    // ------------------------------------------------------------------ Shader

    /** Liest eine Shader-Datei des Pakets und setzt {@code #include "datei"} aus demselben Ordner ein. */
    static String read(String file) {
        String text;
        try (InputStream in = BeaconGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("beacon: Shader-Datei fehlt: " + file);
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("beacon: " + file + ": " + e.getMessage(), e);
        }
        StringBuilder b = new StringBuilder();
        for (String l : text.split("\r?\n", -1)) {
            String t = l.trim();
            if (t.startsWith("#include")) b.append(read(t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')))).append('\n');
            else b.append(l).append('\n');
        }
        return b.toString();
    }

    /** Übersetzt und bindet zwei Stufen (ohne #version; wird vorangestellt); auch für Demos. */
    public static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL2ES2.GL_VERTEX_SHADER, "#version 430 core\n" + vert, "vert"), fs = stage(gl, GL2ES2.GL_FRAGMENT_SHADER, "#version 430 core\n" + frag, "frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL2ES2.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL2ES2.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("beacon: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return p;
    }

    static int stage(GL4 gl, int type, String src, String name) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL2ES2.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL2ES2.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            throw new IllegalStateException("beacon: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
