package com.dan.vessel.gl;

import com.dan.vessel.Shipwright;
import com.dan.vessel.Troller;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Zeichnet die Boote des Bootsbauers ({@link Shipwright}, seit LituyaBay1958 W8a): Werkstoffe mit Plankennähten, Rost,
 * Bewuchs und Nässe, eigener Schatten aus einer Schattenkarte je Boot, Spiegelung des Rundumbilds, Glas mit dem
 * dunklen Inneren dahinter, Drähte und Leinen mit Mindestbreite und Deckung, Lichthöfe der Lampen. Das Netz wird im
 * Hintergrund gebaut ({@link #prepare}); bis es fertig ist, zeichnet {@link #draw} nichts und gibt false.
 * <p>
 * Zwei Durchgänge, weil Glas, Drähte und Lichthöfe durchscheinen: {@link #drawOpaque} mit den deckenden Teilen (Tiefe
 * wird geschrieben), {@link #drawTransparent} nach allem Deckenden der Szene. Für ein Spiegelbild schneidet
 * {@link View#clip} alles unter einer Ebene ab (Wasserspiegel). Licht linear, wie {@code com.dan.forest}.
 */
public final class CraftGl {
    /** Blick: Projektion · Blick (Spalten), Auge, umgekehrte Tiefe, tan des halben Blickfelds und Bildhöhe (für die Drähte). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ = true, tonemap;
        public double exposure = 1, tanHalfFovY = 0.4;
        public int viewportHeight = 1080;
        /** Schnittebene (Welt) a·x + b·y + c·z + d ≥ 0 bleibt; null = keine. */
        public double[] clip;
        /** Schattenkarte neu rechnen (aus für ein Spiegelbild im selben Bild). */
        public boolean updateShadow = true;
    }

    /** Licht: Sonne (Richtung zur Sonne, Farbe im Maßstab Albedo · E/π), Himmel und Boden der Halbkugel, Dunst, Rundumbild. */
    public static final class Light {
        public double[] sunDir = {0.4, 0.7, 0.3}, sunColor = {2.6, 2.5, 2.3}, skyColor = {0.3, 0.38, 0.5}, groundColor = {0.03, 0.035, 0.04}, fogColor = {0.6, 0.65, 0.7};
        public double fogDistance = 25_000;
        /** Rundumbild (Azimut von Nord, Höhe; wie com.dan.ocean), 0 = Himmelsfarbe. */
        public int panorama;
        public double panoramaGain = 1;
        /** Anteil der Sonne, der das Boot von außen erreicht (Schatten der Berge, Wolken). */
        public double sunVisibility = 1;
        /** Nässe 0 … 1 (Regen, Gischt). */
        public double wet;
        public boolean shadows = true;
        public double lampGlow = 1;
    }

    private static final class Boat {
        Troller plan;
        CompletableFuture<Shipwright.Model> future;
        Shipwright.Model m;
        int vaoO, vboO, nO, vaoG, vboG, nG, vaoW, vboW, nW, vaoL, vboL, nL, labels;
        int shadowTex, shadowFbo;
        float[] shadowVP;
        boolean up;
    }

    private int prog, shadowProg, wireProg, lampProg;
    private boolean init;
    private final Map<Object, Boat> boats = new HashMap<>();
    /** Kantenlänge der Schattenkarte je Boot. */
    public int shadowSize = 2048;

    public void init(GL4 gl) {
        if (init) return;
        prog = VesselGl.program(gl, VesselGl.read("craft.vert"), VesselGl.read("craft.frag"));
        shadowProg = VesselGl.program(gl, VesselGl.read("craft_shadow.vert"), VesselGl.read("craft_shadow.frag"));
        wireProg = VesselGl.program(gl, VesselGl.read("wire.vert"), VesselGl.read("wire.frag"));
        lampProg = VesselGl.program(gl, VesselGl.read("lamp.vert"), VesselGl.read("lamp.frag"));
        init = true;
    }

    /** Baut das Boot key nach dem Bauplan im Hintergrund (einmal; ein neuer Bauplan baut neu). */
    public synchronized void prepare(Object key, Troller t) {
        Boat b = boats.get(key);
        if (b != null && b.plan == t) return;
        Boat nb = new Boat();
        nb.plan = t;
        nb.future = CompletableFuture.supplyAsync(() -> Shipwright.build(t));
        if (b != null) nb.labels = -1;   // alte GL-Teile räumt dispose
        boats.put(key, nb);
    }

    /** Ob das Boot gebaut und hochgeladen ist (oder gerade fertig wurde). */
    public synchronized boolean ready(Object key) {
        Boat b = boats.get(key);
        return b != null && (b.up || b.future.isDone());
    }

    /** Das gebaute Modell (oder null, solange es entsteht). */
    public synchronized Shipwright.Model model(Object key) {
        Boat b = boats.get(key);
        return b == null || !b.future.isDone() ? null : b.future.join();
    }

    private boolean upload(GL4 gl, Boat b) {
        if (b.up) return true;
        if (!b.future.isDone()) return false;
        b.m = b.future.join();
        int[] v = vaoMesh(gl, b.m.opaque); b.vaoO = v[0]; b.vboO = v[1]; b.nO = v[2];
        v = vaoMesh(gl, b.m.glass); b.vaoG = v[0]; b.vboG = v[1]; b.nG = v[2];
        v = vaoInst(gl, b.m.wires, new int[]{3, 3, 2, 3}, Shipwright.WIRE); b.vaoW = v[0]; b.vboW = v[1]; b.nW = v[2];
        v = vaoInst(gl, b.m.lamps, new int[]{3, 3, 1}, 7); b.vaoL = v[0]; b.vboL = v[1]; b.nL = v[2];
        b.labels = labels(gl, b.m.labels);
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        b.shadowTex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, b.shadowTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, shadowSize, shadowSize);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_MODE, GL4.GL_COMPARE_REF_TO_TEXTURE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_FUNC, GL.GL_LEQUAL);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        b.shadowFbo = f[0];
        int[] prev = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, b.shadowFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, b.shadowTex, 0);
        gl.glDrawBuffer(GL.GL_NONE);
        gl.glReadBuffer(GL.GL_NONE);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        b.up = true;
        return true;
    }

    private static int[] vaoMesh(GL4 gl, float[] a) {
        int[] va = new int[1], vb = new int[1];
        gl.glGenVertexArrays(1, va, 0);
        gl.glGenBuffers(1, vb, 0);
        gl.glBindVertexArray(va[0]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vb[0]);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(a.length == 0 ? new float[Shipwright.STRIDE] : a);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, fb.capacity() * 4L, fb, GL.GL_STATIC_DRAW);
        int[] sz = {3, 3, 3, 2, 1, 1, 1};
        long off = 0;
        for (int i = 0; i < sz.length; i++) {
            gl.glEnableVertexAttribArray(i);
            gl.glVertexAttribPointer(i, sz[i], GL.GL_FLOAT, false, Shipwright.STRIDE * 4, off);
            off += sz[i] * 4L;
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        return new int[]{va[0], vb[0], a.length / Shipwright.STRIDE};
    }

    private static int[] vaoInst(GL4 gl, float[] a, int[] sz, int stride) {
        int[] va = new int[1], vb = new int[1];
        gl.glGenVertexArrays(1, va, 0);
        gl.glGenBuffers(1, vb, 0);
        gl.glBindVertexArray(va[0]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vb[0]);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(a.length == 0 ? new float[stride] : a);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, fb.capacity() * 4L, fb, GL.GL_STATIC_DRAW);
        long off = 0;
        for (int i = 0; i < sz.length; i++) {
            gl.glEnableVertexAttribArray(i);
            gl.glVertexAttribPointer(i, sz[i], GL.GL_FLOAT, false, stride * 4, off);
            gl.glVertexAttribDivisor(i, 1);
            off += sz[i] * 4L;
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        return new int[]{va[0], vb[0], a.length / stride};
    }

    /** Schrifttextur 1024 × 256 (R8, Mip-Stufen): oben der Name, unten der Heimathafen, in gemalten Großbuchstaben. */
    private static int labels(GL4 gl, String[] text) {
        int w = 1024, h = 256;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        for (int row = 0; row < 2; row++) {
            String s = row < text.length && text[row] != null ? text[row] : "";
            int size = row == 0 ? 112 : 64;
            Font f = new Font(Font.SERIF, Font.BOLD, size);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            while (fm.stringWidth(s) > w - 40 && size > 20) { size -= 4; f = new Font(Font.SERIF, Font.BOLD, size); g.setFont(f); fm = g.getFontMetrics(); }
            int x = (w - fm.stringWidth(s)) / 2, y = row * 128 + 64 + (fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(s, x, y);
        }
        g.dispose();
        byte[] px = ((java.awt.image.DataBufferByte) img.getRaster().getDataBuffer()).getData();
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1 + (int) (Math.log(w) / Math.log(2)), GL4.GL_R8, w, h);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, w, h, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, ByteBuffer.wrap(px));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    // ------------------------------------------------------------------ Matrizen (Spalten)

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        return r;
    }

    static float[] lookAt(double[] e, double[] c) {
        double[] f = {c[0] - e[0], c[1] - e[1], c[2] - e[2]};
        double l = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        for (int i = 0; i < 3; i++) f[i] /= l;
        double[] up = Math.abs(f[1]) > 0.98 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] s = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        l = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
        for (int i = 0; i < 3; i++) s[i] /= l;
        double[] u = {s[1] * f[2] - s[2] * f[1], s[2] * f[0] - s[0] * f[2], s[0] * f[1] - s[1] * f[0]};
        return new float[]{(float) s[0], (float) u[0], (float) -f[0], 0, (float) s[1], (float) u[1], (float) -f[1], 0, (float) s[2], (float) u[2], (float) -f[2], 0,
                (float) -(s[0] * e[0] + s[1] * e[1] + s[2] * e[2]), (float) -(u[0] * e[0] + u[1] * e[1] + u[2] * e[2]), (float) (f[0] * e[0] + f[1] * e[1] + f[2] * e[2]), 1};
    }

    static float[] ortho(double r, double n, double f) {
        return new float[]{(float) (1 / r), 0, 0, 0, 0, (float) (1 / r), 0, 0, 0, 0, (float) (-2 / (f - n)), 0, 0, 0, (float) (-(f + n) / (f - n)), 1};
    }

    static double[] apply(float[] m, double x, double y, double z) {
        return new double[]{m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]};
    }

    // ------------------------------------------------------------------ Zeichnen

    /** Deckende Teile des Boots key mit der Modellmatrix model (Rumpf → Welt); false, solange das Netz entsteht. */
    public synchronized boolean drawOpaque(GL4 gl, Object key, float[] model, View v, Light l) {
        Boat b = boats.get(key);
        if (b == null || !init || !upload(gl, b)) return false;
        double[] bd = b.m.bounds;
        if (l.shadows && (v.updateShadow || b.shadowVP == null)) shadow(gl, b, model, l);
        gl.glUseProgram(prog);
        common(gl, prog, b, model, v, l);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uGlass"), 0);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        clipOn(gl, v);
        gl.glBindVertexArray(b.vaoO);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, b.nO);
        gl.glBindVertexArray(0);
        clipOff(gl, v);
        gl.glUseProgram(0);
        return bd != null;
    }

    /** Glas, Drähte und Lichthöfe (nach allem Deckenden der Szene; Tiefe wird gelesen, nicht geschrieben). */
    public synchronized void drawTransparent(GL4 gl, Object key, float[] model, View v, Light l) {
        Boat b = boats.get(key);
        if (b == null || !b.up) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        clipOn(gl, v);
        if (b.nG > 0) {
            gl.glUseProgram(prog);
            common(gl, prog, b, model, v, l);
            gl.glUniform1i(gl.glGetUniformLocation(prog, "uGlass"), 1);
            gl.glBindVertexArray(b.vaoG);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, b.nG);
        }
        float pixel = (float) (2 * v.tanHalfFovY / Math.max(1, v.viewportHeight));
        if (b.nW > 0) {
            gl.glUseProgram(wireProg);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(wireProg, "uVP"), 1, false, v.viewProj, 0);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(wireProg, "uModel"), 1, false, model, 0);
            gl.glUniform3f(gl.glGetUniformLocation(wireProg, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
            gl.glUniform1f(gl.glGetUniformLocation(wireProg, "uPixel"), pixel);
            clipUniform(gl, wireProg, v);
            light(gl, wireProg, l);
            gl.glBindVertexArray(b.vaoW);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, b.nW);
        }
        if (b.nL > 0 && l.lampGlow > 0) {
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            gl.glUseProgram(lampProg);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(lampProg, "uVP"), 1, false, v.viewProj, 0);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(lampProg, "uModel"), 1, false, model, 0);
            gl.glUniform3f(gl.glGetUniformLocation(lampProg, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
            gl.glUniform1f(gl.glGetUniformLocation(lampProg, "uPixel"), pixel);
            gl.glUniform1f(gl.glGetUniformLocation(lampProg, "uGlow"), (float) l.lampGlow);
            clipUniform(gl, lampProg, v);
            gl.glBindVertexArray(b.vaoL);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, b.nL);
        }
        gl.glBindVertexArray(0);
        clipOff(gl, v);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glUseProgram(0);
    }

    /** Beides hintereinander (für Szenen ohne eigene Reihenfolge, Vorführungen). */
    public boolean draw(GL4 gl, Object key, float[] model, View v, Light l) {
        boolean ok = drawOpaque(gl, key, model, v, l);
        if (ok) drawTransparent(gl, key, model, v, l);
        return ok;
    }

    private void clipOn(GL4 gl, View v) { if (v.clip != null) gl.glEnable(GL4.GL_CLIP_DISTANCE0); }

    private void clipOff(GL4 gl, View v) { if (v.clip != null) gl.glDisable(GL4.GL_CLIP_DISTANCE0); }

    private static void clipUniform(GL4 gl, int p, View v) {
        double[] c = v.clip;
        if (c == null) gl.glUniform4f(gl.glGetUniformLocation(p, "uClip"), 0, 0, 0, 1);
        else gl.glUniform4f(gl.glGetUniformLocation(p, "uClip"), (float) c[0], (float) c[1], (float) c[2], (float) c[3]);
    }

    private static void light(GL4 gl, int p, Light l) {
        double sl = Math.sqrt(l.sunDir[0] * l.sunDir[0] + l.sunDir[1] * l.sunDir[1] + l.sunDir[2] * l.sunDir[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunDir"), (float) (l.sunDir[0] / sl), (float) (l.sunDir[1] / sl), (float) (l.sunDir[2] / sl));
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSkyColor"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uGroundColor"), (float) l.groundColor[0], (float) l.groundColor[1], (float) l.groundColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uSunVis"), (float) Math.max(0, Math.min(1, l.sunVisibility)));
        gl.glUniform1f(gl.glGetUniformLocation(p, "uWet"), (float) Math.max(0, Math.min(1, l.wet)));
    }

    private void common(GL4 gl, int p, Boat b, float[] model, View v, Light l) {
        Troller t = b.plan;
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uModel"), 1, false, model, 0);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        clipUniform(gl, p, v);
        light(gl, p, l);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uWaterline"), (float) t.waterline());
        gl.glUniform1f(gl.glGetUniformLocation(p, "uSeed"), (float) (t.seed % 97));
        gl.glUniform1f(gl.glGetUniformLocation(p, "uExposure"), (float) v.exposure);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uTonemap"), v.tonemap ? 1 : 0);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uZones"), (float) t.sheerStrakeHeight, (float) t.bootTopAbove, (float) t.bootTopBelow, (float) t.wear);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uAge"), (float) t.rust, (float) t.growth, 0.35f, 0);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uTop"), (float) t.topsides[0], (float) t.topsides[1], (float) t.topsides[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSheer"), (float) t.sheerStrake[0], (float) t.sheerStrake[1], (float) t.sheerStrake[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uBoot"), (float) t.bootTop[0], (float) t.bootTop[1], (float) t.bootTop[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uBottom"), (float) t.bottom[0], (float) t.bottom[1], (float) t.bottom[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uLamp"), t.anchorLight ? 1f : 0f, t.runningLights ? 1f : 0f, (float) t.lampRadiance);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uMastTop"), (float) (t.hull.deck(t.hull.mastS) + t.hull.mastHeight + 0.3));
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.panorama);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uPano"), 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uPanoOn"), l.panorama != 0 ? 1 : 0);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uPanoGain"), (float) l.panoramaGain);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, b.shadowTex);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uShadow"), 1);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uShadowOn"), l.shadows && b.shadowVP != null ? 1 : 0);
        if (b.shadowVP != null) gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uShadowVP"), 1, false, b.shadowVP, 0);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, b.labels);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uLabels"), 2);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    /** Schattenkarte des Boots aus Sicht der Sonne (klassische Tiefe, eigene Tiefenabbildung für die Dauer des Durchgangs). */
    private void shadow(GL4 gl, Boat b, float[] model, Light l) {
        if (l.sunDir[1] < -0.05) { b.shadowVP = null; return; }
        double[] bd = b.m.bounds;
        double[] c = apply(model, 0.5 * (bd[0] + bd[3]), 0.5 * (bd[1] + bd[4]), 0.5 * (bd[2] + bd[5]));
        double r = 0.5 * Math.sqrt(Math.pow(bd[3] - bd[0], 2) + Math.pow(bd[4] - bd[1], 2) + Math.pow(bd[5] - bd[2], 2)) + 0.5;
        double sl = Math.sqrt(l.sunDir[0] * l.sunDir[0] + l.sunDir[1] * l.sunDir[1] + l.sunDir[2] * l.sunDir[2]);
        double[] e = {c[0] + l.sunDir[0] / sl * 2 * r, c[1] + l.sunDir[1] / sl * 2 * r, c[2] + l.sunDir[2] / sl * 2 * r};
        b.shadowVP = mul(ortho(r, 0.1, 4 * r), lookAt(e, c));
        int[] fb = new int[1], vp = new int[4], df = new int[1], mode = new int[1];
        float[] cd = new float[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, fb, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glGetIntegerv(GL.GL_DEPTH_FUNC, df, 0);
        gl.glGetFloatv(GL.GL_DEPTH_CLEAR_VALUE, cd, 0);
        gl.glGetIntegerv(GL4.GL_CLIP_DEPTH_MODE, mode, 0);
        boolean zeroOne = mode[0] == GL4.GL_ZERO_TO_ONE;
        int[] origin = new int[1];
        gl.glGetIntegerv(GL4.GL_CLIP_ORIGIN, origin, 0);
        if (zeroOne) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_NEGATIVE_ONE_TO_ONE);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, b.shadowFbo);
        gl.glViewport(0, 0, shadowSize, shadowSize);
        gl.glClearDepth(1);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glUseProgram(shadowProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(shadowProg, "uVP"), 1, false, b.shadowVP, 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(shadowProg, "uModel"), 1, false, model, 0);
        gl.glBindVertexArray(b.vaoO);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, b.nO);
        gl.glBindVertexArray(0);
        if (zeroOne) gl.glClipControl(origin[0] == 0 ? GL4.GL_LOWER_LEFT : origin[0], GL4.GL_ZERO_TO_ONE);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fb[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glDepthFunc(df[0]);
        gl.glClearDepth(cd[0]);
        gl.glUseProgram(0);
    }

    public synchronized void dispose(GL4 gl) {
        for (Boat b : boats.values()) {
            if (!b.up) continue;
            gl.glDeleteVertexArrays(4, new int[]{b.vaoO, b.vaoG, b.vaoW, b.vaoL}, 0);
            gl.glDeleteBuffers(4, new int[]{b.vboO, b.vboG, b.vboW, b.vboL}, 0);
            gl.glDeleteTextures(2, new int[]{b.labels, b.shadowTex}, 0);
            gl.glDeleteFramebuffers(1, new int[]{b.shadowFbo}, 0);
            b.up = false;
        }
        boats.clear();
        if (init) {
            gl.glDeleteProgram(prog);
            gl.glDeleteProgram(shadowProg);
            gl.glDeleteProgram(wireProg);
            gl.glDeleteProgram(lampProg);
        }
        init = false;
    }
}
