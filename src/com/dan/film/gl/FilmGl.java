package com.dan.film.gl;

import com.dan.film.Film;
import com.dan.film.Grade;
import com.dan.film.Stock;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Der Film auf der Grafikkarte (GL 4.3): {@code halation_down.frag} und {@code halation.frag} (das Licht aus dem Träger in
 * die rote Schicht, auf einem kleinen Puffer, in dem der Ring {@link Film#HALATION_TEXELS} Bildpunkte misst; so kostet
 * die Halation bei jeder Auflösung gleich wenig) und {@code film.frag} (Negativ, Korn, Kopie mit Kopierlicht, Leinwand,
 * sRGB) ins Ziel. Ersetzt die Filmkurve der App (Belichtung, Kurve, Gamma). Je Filmbild ({@link Film#fps}) neues Korn,
 * neuer Bildstand, neues Flackern; dazwischen bleibt es stehen wie im Projektor. Danach: Mischen aus, Framebuffer und
 * Viewport verändert.
 */
public final class FilmGl {

    /** Was die App je Bild mitgibt. */
    public static final class View {
        /** HDR-Bild (Einheiten der Szene) und Ziel (Framebuffer, Größe). */
        public int hdrTexture, targetFbo, outWidth = 1, outHeight = 1;
        /** Belichtung der App (Szene → Bild, wie vor der Filmkurve). */
        public double exposure = 1;
        /** Zeit (s) für Filmbild, Korn und Bildstand. */
        public double timeS;
    }

    private final Film film;
    private boolean ready;
    private int progDown, progHal, progFilm, vao, fbo, downTex, halTex, hw, hh, wTex, wKx = -1, wKy = -1;
    private String wKey;
    private final Map<Integer, Map<String, Integer>> locs = new HashMap<>();
    private Stock tableFor;
    private float[] table;
    private long frame;

    public FilmGl(Film film) { this.film = film; }

    public boolean ready() { return ready; }

    /** Größe des Halationspuffers zuletzt (Bildpunkte). */
    public int[] halationSize() { return new int[]{hw, hh}; }

    /** Nummer des Filmbilds zuletzt. */
    public long frame() { return frame; }

    public void init(GL4 gl) {
        String vs = read("fullscreen.vert");
        progDown = program(gl, vs, read("halation_down.frag"));
        progHal = program(gl, vs, read("halation.frag"));
        progFilm = program(gl, vs, read("film.frag"));
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
        gl.glGenFramebuffers(1, a, 0);
        fbo = a[0];
        ready = true;
    }

    public void apply(GL4 gl, View v) {
        if (!ready) return;
        Film f = film;
        Stock n = f.negative, p = f.print;
        frame = (long) Math.floor(v.timeS * f.fps);
        double flick = f.flicker(frame);
        double[] wv = f.weaveMm(frame / f.fps);
        double exp = v.exposure * Math.pow(2, f.exposureStops) / Film.GRAY * flick;
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(vao);
        boolean hal = f.halation && n.halation() > 0 && f.halationGain > 0;
        if (hal) {
            if (tableFor != n) { table = Film.halationTable(n); tableFor = n; }
            double[] r = f.halationRadiusPx2(v.outWidth);
            int[] bs = Film.halationBuffer(v.outWidth, v.outHeight, r[0], r[1]);
            if (bs[0] != hw || bs[1] != hh) {
                if (halTex != 0) gl.glDeleteTextures(2, new int[]{downTex, halTex}, 0);
                downTex = r32f(gl, bs[0], bs[1]);
                halTex = r32f(gl, bs[0], bs[1]);
                hw = bs[0];
                hh = bs[1];
            }
            double rbx = r[0] * hw / v.outWidth, rby = r[1] * hh / v.outHeight;
            String key = System.identityHashCode(table) + "|" + Math.round(rbx * 1000) + "|" + Math.round(rby * 1000);
            if (!key.equals(wKey)) {   // Gewichte des Kerns als Textur, neu nur, wenn Material oder Puffer wechseln
                double[] nk = Film.halationNorm(table, rbx, rby);
                wKx = (int) nk[1];
                wKy = (int) nk[2];
                float[] wt = Film.halationWeights(table, rbx, rby);
                if (wTex != 0) gl.glDeleteTextures(1, new int[]{wTex}, 0);
                int[] t = new int[1];
                gl.glGenTextures(1, t, 0);
                wTex = t[0];
                gl.glBindTexture(GL.GL_TEXTURE_2D, wTex);
                gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R32F, 2 * wKx + 1, 2 * wKy + 1);
                gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
                gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, 2 * wKx + 1, 2 * wKy + 1, GL4.GL_RED, GL.GL_FLOAT, java.nio.FloatBuffer.wrap(wt));
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
                gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
                wKey = key;
            }
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
            gl.glViewport(0, 0, hw, hh);
            // 1 die Belichtung der roten Schicht auf den Puffer gemittelt
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, downTex, 0);
            gl.glUseProgram(progDown);
            tex(gl, progDown, "uHdr", 0, v.hdrTexture);
            setf(gl, progDown, "uExposure", (float) exp);
            mat(gl, progDown, "uMatrix", n.matrix());
            set2(gl, progDown, "uBuf", hw, hh);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            // 2 mit dem Kern gefaltet
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, halTex, 0);
            gl.glUseProgram(progHal);
            tex(gl, progHal, "uDown", 0, downTex);
            tex(gl, progHal, "uW", 1, wTex);
            int lk = loc(gl, progHal, "uK");
            if (lk >= 0) gl.glUniform2i(lk, wKx, wKy);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, v.targetFbo);
        gl.glViewport(0, 0, v.outWidth, v.outHeight);
        int q = progFilm;
        gl.glUseProgram(q);
        tex(gl, q, "uHdr", 0, v.hdrTexture);
        tex(gl, q, "uHal", 1, hal ? halTex : v.hdrTexture);
        seti(gl, q, "uHalOn", hal ? 1 : 0);
        double hs = n.halation() * f.halationGain;
        set3(gl, q, "uHalRgb", (float) (hs * n.halationRgb()[0]), (float) (hs * n.halationRgb()[1]), (float) (hs * n.halationRgb()[2]));
        setf(gl, q, "uExposure", (float) exp);
        mat(gl, q, "uMatrix", n.matrix());
        double[] dye = p.dye().clone();
        for (int i = 0; i < 3; i++) { double s = dye[3 * i] + dye[3 * i + 1] + dye[3 * i + 2]; for (int j = 0; j < 3; j++) dye[3 * i + j] /= s; }
        mat(gl, q, "uDye", dye);
        double[] keep = f.dyeKept();
        set3(gl, q, "uKeep", (float) keep[0], (float) keep[1], (float) keep[2]);
        curves(gl, q, "uNeg", n);
        curves(gl, q, "uPrt", p);
        double[] b = f.printerOffsets(), o = f.grade.offsets();
        set3(gl, q, "uOffset", (float) (b[0] + o[0]), (float) (b[1] + o[1]), (float) (b[2] + o[2]));
        set2(gl, q, "uWeave", (float) (wv[0] / f.format.widthMm()), (float) (wv[1] / f.format.heightMm()));
        setf(gl, q, "uGain", (float) f.displayGain);
        double a = f.grainArea(v.outWidth), k = Math.sqrt(Film.A48 / a) * f.grainGain;
        double[] cl = f.grainCloudPx(v.outWidth);
        seti(gl, q, "uGrainOn", f.grain && f.grainGain > 0 ? 1 : 0);
        set3(gl, q, "uSigmaNeg", (float) (n.rms() / 1000 * n.layerGrain()[0] * k), (float) (n.rms() / 1000 * n.layerGrain()[1] * k), (float) (n.rms() / 1000 * n.layerGrain()[2] * k));
        setf(gl, q, "uSigmaPrt", (float) (p.rms() / 1000 * k));
        set2(gl, q, "uCloud", (float) cl[0], (float) cl[1]);
        setf(gl, q, "uFrame", (float) (frame % 100000));
        set2(gl, q, "uOut", v.outWidth, v.outHeight);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
    }

    private static int r32f(GL4 gl, int w, int h) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R32F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    private void curves(GL4 gl, int p, String n, Stock s) {
        float[] c = new float[18];
        for (int i = 0; i < 3; i++) System.arraycopy(s.curves()[i].toArray(), 0, c, 6 * i, 6);
        int l = loc(gl, p, n + "[0]");
        if (l >= 0) gl.glUniform1fv(l, 18, c, 0);
    }

    private void mat(GL4 gl, int p, String n, double[] m) {
        float[] f = new float[9];
        for (int i = 0; i < 9; i++) f[i] = (float) m[i];
        int l = loc(gl, p, n);
        if (l >= 0) gl.glUniformMatrix3fv(l, 1, true, f, 0);   // Zeilen
    }

    private int loc(GL4 gl, int p, String n) { return locs.computeIfAbsent(p, k -> new HashMap<>()).computeIfAbsent(n, k -> gl.glGetUniformLocation(p, k)); }

    private void seti(GL4 gl, int p, String n, int v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1i(l, v); }

    private void setf(GL4 gl, int p, String n, float v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1f(l, v); }

    private void set2(GL4 gl, int p, String n, float a, float b) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform2f(l, a, b); }

    private void set3(GL4 gl, int p, String n, float a, float b, float c) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform3f(l, a, b, c); }

    private void tex(GL4 gl, int p, String n, int unit, int t) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t);
        seti(gl, p, n, unit);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(progDown);
        gl.glDeleteProgram(progHal);
        gl.glDeleteProgram(progFilm);
        if (halTex != 0) gl.glDeleteTextures(2, new int[]{downTex, halTex}, 0);
        if (wTex != 0) gl.glDeleteTextures(1, new int[]{wTex}, 0);
        wTex = 0;
        wKey = null;
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        locs.clear();
        halTex = downTex = 0;
        hw = hh = 0;
        tableFor = null;
        ready = false;
    }

    static String read(String file) {
        try (InputStream in = FilmGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("Shader " + file + " fehlt (src mit auf den Klassenpfad?)");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Shader " + file + ": " + e.getMessage(), e);
        }
    }

    private static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int s = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(s, 1, new String[]{"#version 430 core\n" + (k == 0 ? vs : fs)}, null, 0);
            gl.glCompileShader(s);
            int[] ok = new int[1];
            gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] log = new byte[8192];
                gl.glGetShaderInfoLog(s, log.length, null, 0, log, 0);
                throw new IllegalStateException("com.dan.film: " + new String(log, StandardCharsets.UTF_8).trim());
            }
            gl.glAttachShader(p, s);
            gl.glDeleteShader(s);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] log = new byte[8192];
            gl.glGetProgramInfoLog(p, log.length, null, 0, log, 0);
            throw new IllegalStateException("com.dan.film: " + new String(log, StandardCharsets.UTF_8).trim());
        }
        return p;
    }

    /** Für Prüfungen: das Kopierlicht des Films. */
    public Grade grade() { return film.grade; }
}
