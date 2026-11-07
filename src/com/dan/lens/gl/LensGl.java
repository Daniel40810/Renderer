package com.dan.lens.gl;

import com.dan.lens.Lens;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Das Objektiv auf der Grafikkarte (GL 4.3): aus dem scharfen HDR-Bild und der Tiefe das Bild auf dem Sensor.
 * <ol>
 * <li><b>Schärfezieher</b> ({@code focus.frag}): misst die Tiefe in der Bildmitte (oder nimmt den Fokus von Hand) und
 * führt den Fokus kritisch gedämpft nach; ein Bildpunkt als Zustand, ohne Warten auf den Prozessor (der liest ihn ein
 * Bild später über einen Puffer).</li>
 * <li><b>Unschärfekreis</b> ({@code coc.frag}, halbe Auflösung), <b>Kacheln</b> ({@code tiles.frag}), <b>Lichter</b>
 * ({@code highlights.comp}: helle unscharfe Punkte werden gestreut), <b>Sammeln</b> ({@code bokeh.frag}) und
 * <b>Streuen</b> ({@code sprite.vert/frag}, gezeichnet über einen indirekten Aufruf).</li>
 * <li><b>Schleierlicht</b> ({@code glare_down/glare_up}, 7 Stufen).</li>
 * <li><b>Sensor</b> ({@code lens.frag}): Mischung, Verzeichnung, Farbquerfehler, Schleier, Randabfall.</li>
 * <li><b>Sonne</b> ({@code flare.frag}): Geisterbilder und Stern, addiert.</li>
 * </ol>
 * Aufruf je Bild: {@link #apply}; Ergebnis ist eine eigene Textur (RGBA16F, volle Größe, mit Mip-Stufen) in Einheiten der
 * Szene. Danach sind Mischen und Tiefentest aus; Framebuffer, Viewport und Programm sind verändert.
 */
public final class LensGl {

    /** Was die App je Bild mitgibt. */
    public static final class View {
        /** Scharfes HDR-Bild (RGBA16F) und Tiefe (DEPTH_COMPONENT32F) gleicher Größe. */
        public int colorTexture, depthTexture, width = 1, height = 1;
        public boolean reversedZ;
        public double near = 0.3, far = 400_000;
        /** Projektion · Blick (Spalten), für die Lage der Sonne im Bild. */
        public float[] viewProj;
        /** Richtung zur Sonne (Welt, Einheitsvektor) oder null; Bestrahlung senkrecht (Einheiten der Szene) und Anteil durch Wolken. */
        public double[] sunDir;
        public double[] sunIrradiance = {0, 0, 0};
        public double sunVisible = 1;
        /** Belichtung der App (Szene → Bild): Lichter ab dem Doppelten von Weiß werden gestreut. */
        public double exposure = 1;
        /** Zeit seit dem letzten Bild (s); snap: Fokus springt (Schnitt, Standbild). */
        public double dt = 1 / 60.0;
        public boolean snap;
        /** Messfeld des Autofokus (uv). */
        public double focusU = 0.5, focusV = 0.5;
        /**
         * Seit W9c Runde 2: Tropfen auf der Frontlinse ({@link com.dan.lens.FrontDrops}): der Anteil des Bündels unter Wasser
         * als Gitter dropW × dropH über das Bild (Zeilen von unten; null: trocken), die Verteilung der Ablenkung
         * ({@link com.dan.lens.FrontDrops#phase}, je Steradiant, Ringe bis dropMaxRad) und der Anteil, der durchgeht.
         * Ein neues Gitter (anderes Feld) wird hochgeladen.
         */
        public float[] dropCover;
        public int dropW, dropH;
        public double[] dropPhase;
        public double dropMaxRad, dropTransmit = 0.75;
        /** Ob die Sonne für die Tropfen zu sehen ist (0 … 1; mal sunVisible): auch außerhalb des Bilds, das die Tiefe nicht kennt. */
        public double dropSunVisible = 1;
    }

    private int dropTex;
    private float[] dropUploaded;

    public static final int TILE = 8, GLARE_LEVELS = 7, SPOT_CAP = 4096;

    private final Lens lens;
    private boolean ready;
    private final Map<String, Integer> progs = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> locs = new HashMap<>();
    private int vao, w, h, hw, hh, tw, th;
    private int texHalf, texHalf2, texT1, texT2, texDof, texSpr, texOut;
    private final int[] texDown = new int[GLARE_LEVELS], texUp = new int[GLARE_LEVELS], dw = new int[GLARE_LEVELS], dh = new int[GLARE_LEVELS];
    private int fbo;
    private final int[] state = new int[2];
    private int stateIdx;
    private int cmdBuf, spotBuf, pbo;
    private boolean pboFull;
    private long fence;
    private volatile double focusRead = Double.NaN, measureRead = Double.NaN;
    private volatile Lens.Optics optics;
    private double lastFocusM = 10;

    public LensGl(Lens lens) { this.lens = lens; }

    public boolean ready() { return ready; }

    /** Fokus (m) nach dem Schärfezieher, ein Bild alt; NaN vor dem ersten Lesen. */
    public double focusM() { return focusRead; }

    /** Was der Autofokus zuletzt gemessen hat (m, ∞ = Himmel). */
    public double measuredM() { return Double.isNaN(measureRead) ? Double.NaN : measureRead <= 0 ? Double.POSITIVE_INFINITY : 1 / measureRead; }

    /** Die Optik des letzten Bilds (oder null). */
    public Lens.Optics optics() { return optics; }

    public void init(GL4 gl) {
        String common = read("lens_common.glsl");
        String fs = read("fullscreen.vert");
        progs.put("focus", program(gl, fs, common + read("focus.frag")));
        progs.put("coc", program(gl, fs, common + read("coc.frag")));
        progs.put("tiles", program(gl, fs, read("tiles.frag")));
        progs.put("bokeh", program(gl, fs, common + read("bokeh.frag")));
        progs.put("sprite", program(gl, common + read("sprite.vert"), common + read("sprite.frag")));
        progs.put("lens", program(gl, fs, common + read("lens.frag")));
        progs.put("flare", program(gl, fs, common + read("flare.frag")));
        progs.put("gdown", program(gl, fs, read("glare_down.frag")));
        progs.put("gup", program(gl, fs, read("glare_up.frag")));
        progs.put("hi", compute(gl, read("highlights.comp")));
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
        gl.glGenFramebuffers(1, a, 0);
        fbo = a[0];
        for (int i = 0; i < 2; i++) state[i] = texture(gl, 1, 1, GL.GL_RGBA32F, GL.GL_NEAREST, false);
        // Zustand des Schärfeziehers auf 0 (heißt: beim ersten Bild sofort stellen), nicht undefiniert
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        for (int i = 0; i < 2; i++) {
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, state[i], 0);
            gl.glViewport(0, 0, 1, 1);
            gl.glClearColor(0, 0, 0, 0);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        int[] b = new int[3];
        gl.glGenBuffers(3, b, 0);
        cmdBuf = b[0];
        spotBuf = b[1];
        pbo = b[2];
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, cmdBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 16, null, GL.GL_DYNAMIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, spotBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) SPOT_CAP * 32, null, GL.GL_DYNAMIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, pbo);
        gl.glBufferData(GL2ES3.GL_PIXEL_PACK_BUFFER, 16, null, GL2ES3.GL_STREAM_READ);
        gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, 0);
        ready = true;
    }

    private void resize(GL4 gl, int nw, int nh) {
        int[] old = {texHalf, texHalf2, texT1, texT2, texDof, texSpr, texOut};
        for (int t : old) if (t != 0) gl.glDeleteTextures(1, new int[]{t}, 0);
        for (int i = 0; i < GLARE_LEVELS; i++) {
            if (texDown[i] != 0) gl.glDeleteTextures(1, new int[]{texDown[i]}, 0);
            if (texUp[i] != 0) gl.glDeleteTextures(1, new int[]{texUp[i]}, 0);
        }
        w = nw;
        h = nh;
        hw = (w + 1) / 2;
        hh = (h + 1) / 2;
        tw = (hw + TILE - 1) / TILE;
        th = (hh + TILE - 1) / TILE;
        texHalf = texture(gl, hw, hh, GL.GL_RGBA16F, GL.GL_NEAREST, false);
        texHalf2 = texture(gl, hw, hh, GL.GL_RGBA16F, GL.GL_NEAREST, false);
        texT1 = texture(gl, tw, th, GL4.GL_RG16F, GL.GL_NEAREST, false);
        texT2 = texture(gl, tw, th, GL4.GL_RG16F, GL.GL_NEAREST, false);
        texDof = texture(gl, hw, hh, GL.GL_RGBA16F, GL.GL_LINEAR, false);
        texSpr = texture(gl, hw, hh, GL.GL_RGBA16F, GL.GL_LINEAR, false);
        texOut = texture(gl, w, h, GL.GL_RGBA16F, GL.GL_LINEAR, true);
        int sw = w, sh = h;
        for (int i = 0; i < GLARE_LEVELS; i++) {
            sw = Math.max(1, (sw + 1) / 2);
            sh = Math.max(1, (sh + 1) / 2);
            dw[i] = sw;
            dh[i] = sh;
            texDown[i] = texture(gl, sw, sh, GL.GL_RGBA16F, GL.GL_LINEAR, false);
            texUp[i] = texture(gl, sw, sh, GL.GL_RGBA16F, GL.GL_LINEAR, false);
        }
    }

    /**
     * W9c: Zeiten der Teile auf der Grafikkarte messen (Zeitstempel, gelesen zwei Bilder später, ohne zu warten). Namen in
     * {@link #TIME_NAMES}, Werte in {@link #timingsMs()} (geglättet).
     */
    public volatile boolean timing;
    public static final String[] TIME_NAMES = {"Schärfe", "Kreise", "Lichter", "Sammeln", "Streuen", "Schleier", "Sensor", "Sonne"};
    private final int[] tq = new int[2 * 9];
    private final boolean[] tqIssued = new boolean[2];
    private final double[] tms = new double[8];
    private int tset;

    /** Geglättete Zeiten der Teile (ms), wie {@link #TIME_NAMES}. */
    public double[] timingsMs() { return tms.clone(); }

    /** „Sammeln 3,1 · Kreise 0,4 · …“ (die teuersten zuerst) oder leer. */
    public String timingLine() {
        if (!timing) return "";
        Integer[] o = new Integer[tms.length];
        for (int i = 0; i < o.length; i++) o[i] = i;
        java.util.Arrays.sort(o, (a, b) -> Double.compare(tms[b], tms[a]));
        StringBuilder b = new StringBuilder();
        for (int i : o) b.append(b.length() > 0 ? " · " : "").append(TIME_NAMES[i]).append(' ').append(String.format(java.util.Locale.GERMAN, "%.2f", tms[i]));
        return b.toString();
    }

    private boolean tActive;

    private void stamp(GL4 gl, int i) { if (tActive) gl.glQueryCounter(tq[tset * 9 + i], GL4.GL_TIMESTAMP); }

    private void timingBegin(GL4 gl) {
        tActive = false;
        if (!timing) return;
        if (tq[0] == 0) gl.glGenQueries(tq.length, tq, 0);
        for (int k = 0; k < 2; k++) {
            if (!tqIssued[k]) continue;
            int[] av = new int[1];
            gl.glGetQueryObjectiv(tq[k * 9 + 8], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
            if (av[0] == 0) continue;
            long[] t = new long[9];
            for (int i = 0; i < 9; i++) gl.glGetQueryObjecti64v(tq[k * 9 + i], GL4.GL_QUERY_RESULT, t, i);
            for (int i = 0; i < 8; i++) tms[i] = tms[i] * 0.85 + 0.15 * Math.max(0, (t[i + 1] - t[i]) / 1e6);
            tqIssued[k] = false;
        }
        if (tqIssued[tset]) return;   // noch unterwegs: dieses Bild ohne Messung
        tActive = true;
        stamp(gl, 0);
    }

    /** Ein Bild durch das Objektiv. Gibt die Textur mit dem Bild auf dem Sensor zurück (oder die Eingabe, wenn nicht bereit). */
    public int apply(GL4 gl, View v) {
        if (!ready || v.colorTexture == 0 || v.depthTexture == 0) return v.colorTexture;
        if (v.width != w || v.height != h) resize(gl, v.width, v.height);
        readBack(gl);
        timingBegin(gl);
        Lens L = lens;
        double focusNow = L.autoFocus ? (Double.isNaN(focusRead) ? lastFocusM : focusRead) : (Double.isNaN(focusRead) ? L.focusM : focusRead);
        L.aspect = w / (double) h;
        Lens.Optics o = L.optics(focusNow);
        optics = o;
        lastFocusM = focusNow;
        double pitch = L.sensorHeightMm / h;
        int maxCoc = switch (L.quality) { case LOW -> 32; case MEDIUM -> 48; case HIGH -> 64; case ULTRA -> 96; };
        int rings = switch (L.quality) { case LOW -> 3; case MEDIUM -> 4; case HIGH -> 5; case ULTRA -> 6; };
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);

        // 1 Schärfezieher
        int src = state[stateIdx], dst = state[stateIdx ^ 1];
        int p = use(gl, "focus");
        common(gl, p, o, pitch, v);
        tex(gl, p, "uDepth", 0, v.depthTexture);
        tex(gl, p, "uState", 1, src);
        seti(gl, p, "uAuto", L.autoFocus ? 1 : 0);
        seti(gl, p, "uSnap", v.snap ? 1 : 0);
        setf(gl, p, "uTargetD", (float) (1 / Math.max(0.05, L.focusM)));
        setf(gl, p, "uDt", (float) Math.max(0, v.dt));
        setf(gl, p, "uTimeS", (float) L.puller.timeS);
        setf(gl, p, "uMaxRate", (float) L.puller.maxDioptersPerS);
        setf(gl, p, "uWin", 0.04f);
        setf(gl, p, "uMinD", (float) (1 / L.puller.farM));
        setf(gl, p, "uMaxD", (float) (1 / L.puller.nearM));
        set2(gl, p, "uCenter", (float) v.focusU, (float) v.focusV);
        draw(gl, dst, 1, 1);
        stateIdx ^= 1;
        int st = dst;
        requestRead(gl, st);
        stamp(gl, 1);

        boolean dof = L.depthOfField;
        if (dof) {
            // 2 halbe Auflösung
            p = use(gl, "coc");
            common(gl, p, o, pitch, v);
            tex(gl, p, "uColor", 0, v.colorTexture);
            tex(gl, p, "uDepth", 1, v.depthTexture);
            tex(gl, p, "uState", 2, st);
            setf(gl, p, "uMaxCoc", maxCoc);
            draw(gl, texHalf, hw, hh);
            // 3 Kacheln
            p = use(gl, "tiles");
            tex(gl, p, "uSrc", 0, texHalf);
            seti(gl, p, "uMode", 0);
            seti(gl, p, "uTile", TILE);
            draw(gl, texT1, tw, th);
            tex(gl, p, "uSrc", 0, texT1);
            seti(gl, p, "uMode", 1);
            draw(gl, texT2, tw, th);
            stamp(gl, 2);
            // 4 Lichter herauslösen
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, cmdBuf);
            IntBuffer cmd = Buffers.newDirectIntBuffer(new int[]{4, 0, 0, 0});
            gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 16, cmd);
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
            p = use(gl, "hi");
            tex(gl, p, "uHalf", 0, texHalf);
            gl.glBindImageTexture(1, texHalf2, 0, false, 0, GL4.GL_WRITE_ONLY, GL.GL_RGBA16F);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 2, cmdBuf);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 3, spotBuf);
            setf(gl, p, "uThresh", (float) (2.0 / Math.max(1e-6, v.exposure)));
            seti(gl, p, "uCap", SPOT_CAP);
            gl.glDispatchCompute((hw + 7) / 8, (hh + 7) / 8, 1);
            gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL4.GL_TEXTURE_FETCH_BARRIER_BIT | GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_COMMAND_BARRIER_BIT);
            stamp(gl, 3);
            // 5 Sammeln
            p = use(gl, "bokeh");
            common(gl, p, o, pitch, v);
            tex(gl, p, "uHalf", 0, texHalf2);
            tex(gl, p, "uTiles", 1, texT2);
            seti(gl, p, "uTile", TILE);
            seti(gl, p, "uRings", rings);
            setf(gl, p, "uMaxCoc", maxCoc);
            draw(gl, texDof, hw, hh);
            stamp(gl, 4);
            // 6 Lichter als Kreise (indirekt: so viele, wie die Liste hat)
            target(gl, texSpr, hw, hh);
            gl.glClearColor(0, 0, 0, 0);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            p = use(gl, "sprite");
            common(gl, p, o, pitch, v);
            tex(gl, p, "uHalf", 0, texHalf);
            set2(gl, p, "uHalfSize", hw, hh);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 3, spotBuf);
            gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, cmdBuf);
            gl.glDrawArraysIndirect(GL.GL_TRIANGLE_STRIP, 0);
            gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, 0);
            gl.glDisable(GL.GL_BLEND);
        } else { stamp(gl, 2); stamp(gl, 3); stamp(gl, 4); }
        stamp(gl, 5);

        // 6b Schleierlicht
        boolean glare = L.glare && L.glareFraction > 0;
        boolean drops = v.dropCover != null && v.dropW > 0 && v.dropH > 0 && v.dropCover.length >= v.dropW * v.dropH && v.dropPhase != null;
        if (drops && v.dropCover != dropUploaded) {   // W9c Runde 2: das Gitter der Tropfen
            if (dropTex == 0) dropTex = texture(gl, 1, 1, GL4.GL_R16F, GL.GL_LINEAR, false);
            gl.glBindTexture(GL.GL_TEXTURE_2D, dropTex);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R16F, v.dropW, v.dropH, 0, GL4.GL_RED, GL.GL_FLOAT, java.nio.FloatBuffer.wrap(v.dropCover));
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            dropUploaded = v.dropCover;
        }
        if (glare || drops) {
            p = use(gl, "gdown");
            int s = v.colorTexture, sw = w, sh = h;
            for (int i = 0; i < GLARE_LEVELS; i++) {
                tex(gl, p, "uSrc", 0, s);
                set2(gl, p, "uTexel", 1f / sw, 1f / sh);
                draw(gl, texDown[i], dw[i], dh[i]);
                s = texDown[i];
                sw = dw[i];
                sh = dh[i];
            }
            double tot = 0;
            for (int i = 0; i < GLARE_LEVELS; i++) tot += Math.pow(2, -0.75 * (i + 1));
            p = use(gl, "gup");
            for (int i = GLARE_LEVELS - 1; i >= 0; i--) {
                tex(gl, p, "uHere", 1, texDown[i]);
                boolean first = i == GLARE_LEVELS - 1;
                tex(gl, p, "uLow", 0, first ? texDown[i] : texUp[i + 1]);
                if (!first) set2(gl, p, "uTexel", 1f / dw[i + 1], 1f / dh[i + 1]);
                setf(gl, p, "uWeight", (float) (Math.pow(2, -0.75 * (i + 1)) / tot));
                seti(gl, p, "uFirst", first ? 1 : 0);
                draw(gl, texUp[i], dw[i], dh[i]);
            }
        }

        stamp(gl, 6);
        // 7 Sensor
        p = use(gl, "lens");
        common(gl, p, o, pitch, v);
        tex(gl, p, "uColor", 0, v.colorTexture);
        tex(gl, p, "uDepth", 1, v.depthTexture);
        tex(gl, p, "uState", 2, st);
        tex(gl, p, "uDof", 3, texDof);
        tex(gl, p, "uSprites", 4, texSpr);
        tex(gl, p, "uGlareTex", 5, texUp[0]);
        seti(gl, p, "uDofOn", dof ? 1 : 0);
        seti(gl, p, "uDistOn", L.distortion ? 1 : 0);
        seti(gl, p, "uChromaOn", L.chromatic ? 1 : 0);
        seti(gl, p, "uVigOn", L.vignetting ? 1 : 0);
        seti(gl, p, "uGlareOn", glare ? 1 : 0);
        arr(gl, p, "uRI", o.relIllum());
        arr(gl, p, "uDist", o.distortion());
        arr(gl, p, "uLatR", o.latR());
        arr(gl, p, "uLatB", o.latB());
        setf(gl, p, "uHalfDiag", (float) o.halfDiagMm());
        double zoom = 1;
        if (L.distortion) zoom = Math.min(zoom, o.distortion()[Lens.TABLE - 1] * (L.chromatic ? Math.min(o.latR()[Lens.TABLE - 1], o.latB()[Lens.TABLE - 1]) : 1));
        setf(gl, p, "uZoom", (float) zoom);
        setf(gl, p, "uGlare", (float) L.glareFraction);
        setf(gl, p, "uVigGain", (float) L.vignettingGain);
        // W9c Runde 2: Tropfen auf der Frontlinse
        seti(gl, p, "uDropOn", drops ? 1 : 0);
        if (drops) {
            tex(gl, p, "uDropCover", 6, dropTex);
            tex(gl, p, "uDropWide", 7, texDown[Math.min(4, GLARE_LEVELS - 1)]);
            setf(gl, p, "uDropT", (float) v.dropTransmit);
            setf(gl, p, "uDropDmax", (float) v.dropMaxRad);
            float[] ph = new float[32];
            for (int k = 0; k < 32; k++) ph[k] = k < v.dropPhase.length ? (float) v.dropPhase[k] : 0f;
            int lp = loc(gl, p, "uDropP");
            if (lp < 0) lp = loc(gl, p, "uDropP[0]");
            if (lp >= 0) gl.glUniform1fv(lp, 32, ph, 0);
            setf(gl, p, "uFocalPx", (float) (o.efl() / pitch));
            boolean sunOn = v.sunDir != null && v.viewProj != null && v.sunIrradiance != null;
            seti(gl, p, "uDropSunOn", sunOn ? 1 : 0);
            if (sunOn) {
                float[] inv = invert(v.viewProj);
                int li = loc(gl, p, "uInvVP");
                if (li >= 0 && inv != null) gl.glUniformMatrix4fv(li, 1, false, inv, 0);
                // das Auge: der Punkt, den die Projektion auf w = 0 legt, (e, 1) ∝ inv · (0, 0, 1, 0)
                if (inv != null && Math.abs(inv[11]) > 1e-20) set3(gl, p, "uDropEye", inv[8] / inv[11], inv[9] / inv[11], inv[10] / inv[11]);
                set3(gl, p, "uDropSunDir", (float) v.sunDir[0], (float) v.sunDir[1], (float) v.sunDir[2]);
                double sv = Math.max(0, Math.min(1, v.sunVisible)) * Math.max(0, Math.min(1, v.dropSunVisible));
                set3(gl, p, "uDropSunE", (float) (v.sunIrradiance[0] * sv), (float) (v.sunIrradiance[1] * sv), (float) (v.sunIrradiance[2] * sv));
            }
        }
        draw(gl, texOut, w, h);
        stamp(gl, 7);

        // 8 Sonne im Objektiv
        double[] sun = sunPixel(v);
        if (sun != null && (L.ghosts || L.star) && v.sunIrradiance != null) {
            p = use(gl, "flare");
            common(gl, p, o, pitch, v);
            tex(gl, p, "uDepth", 1, v.depthTexture);
            set2(gl, p, "uSun", (float) sun[0], (float) sun[1]);
            set3(gl, p, "uSunE", (float) v.sunIrradiance[0], (float) v.sunIrradiance[1], (float) v.sunIrradiance[2]);
            setf(gl, p, "uSunVis", (float) Math.max(0, Math.min(1, v.sunVisible)));
            setf(gl, p, "uSunMm", (float) o.sunRadiusMm());
            seti(gl, p, "uGhosts", o.ghostCount());
            int lg = loc(gl, p, "uG[0]");
            if (lg < 0) lg = loc(gl, p, "uG");
            if (lg >= 0) gl.glUniform4fv(lg, o.ghostCount() * 3, o.ghostData(), 0);
            double[] sa = o.spikeAngles();
            seti(gl, p, "uSpikes", Math.min(22, sa.length));
            arr(gl, p, "uSpikeA", sa.length > 22 ? java.util.Arrays.copyOf(sa, 22) : sa);
            double[] k = o.spikeCoef();
            set3(gl, p, "uSpikeK", (float) k[0], (float) k[1], (float) k[2]);
            setf(gl, p, "uGhostGain", (float) L.ghostGain);
            setf(gl, p, "uStarGain", (float) L.starGain);
            seti(gl, p, "uGhostOn", L.ghosts ? 1 : 0);
            seti(gl, p, "uStarOn", L.star ? 1 : 0);
            target(gl, texOut, w, h);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glDisable(GL.GL_BLEND);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, texOut);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        stamp(gl, 8);
        if (tActive) { tqIssued[tset] = true; tset ^= 1; }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
        return texOut;
    }

    /** Lage der Sonne im Bild (Bildpunkte, Ursprung unten links) oder null (hinter der Kamera). */
    private double[] sunPixel(View v) {
        if (v.sunDir == null || v.viewProj == null) return null;
        float[] m = v.viewProj;
        double x = v.sunDir[0], y = v.sunDir[1], z = v.sunDir[2];
        double cx = m[0] * x + m[4] * y + m[8] * z, cy = m[1] * x + m[5] * y + m[9] * z, cw = m[3] * x + m[7] * y + m[11] * z;
        if (cw <= 1e-6) return null;
        return new double[]{(cx / cw * 0.5 + 0.5) * w, (cy / cw * 0.5 + 0.5) * h};
    }

    private void common(GL4 gl, int p, Lens.Optics o, double pitch, View v) {
        Lens L = lens;
        seti(gl, p, "uReversed", v.reversedZ ? 1 : 0);
        setf(gl, p, "uNear", (float) v.near);
        setf(gl, p, "uFar", (float) v.far);
        set2(gl, p, "uFull", w, h);
        setf(gl, p, "uPitch", (float) pitch);
        setf(gl, p, "uEfl", (float) o.efl());
        setf(gl, p, "uPupil", (float) o.pupilMm());
        setf(gl, p, "uFocusFallback", (float) o.focusMm());
        setf(gl, p, "uAreaRoot", (float) (1 / Math.sqrt(o.areaFactor())));
        seti(gl, p, "uBlades", L.aperture.blades);
        setf(gl, p, "uRound", (float) L.aperture.roundness);
        setf(gl, p, "uRot", (float) Math.toRadians(L.aperture.rotationDeg));
        setf(gl, p, "uSqueeze", (float) Math.max(1, L.squeeze));
        double[] c = o.catEye();
        set4(gl, p, "uCat", (float) c[0], (float) c[1], (float) c[2], (float) c[3]);
        arr(gl, p, "uSAProf", o.saProfile());
        setf(gl, p, "uSAScale", L.sphericalAberration ? (float) o.saScale() : 0f);
        set2(gl, p, "uDz", L.chromatic ? (float) o.dzR() : 0f, L.chromatic ? (float) o.dzB() : 0f);
        setf(gl, p, "uFNumber", (float) o.fNumber());
        setf(gl, p, "uField", (float) o.fieldScale());
    }

    // ------------------------------------------------------------------ Lesen des Fokus (ein Bild später, ohne Warten)

    private void requestRead(GL4 gl, int stateTex) {
        if (pboFull) return;
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_READ_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, stateTex, 0);
        gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);
        gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, pbo);
        gl.glReadPixels(0, 0, 1, 1, GL.GL_RGBA, GL.GL_FLOAT, 0);
        gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, 0);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, 0);
        fence = gl.glFenceSync(GL4.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        pboFull = true;
    }

    private void readBack(GL4 gl) {
        if (!pboFull) return;
        int r = gl.glClientWaitSync(fence, 0, 0);
        if (r != GL4.GL_ALREADY_SIGNALED && r != GL4.GL_CONDITION_SATISFIED) return;
        gl.glDeleteSync(fence);
        gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, pbo);
        ByteBuffer b = gl.glMapBufferRange(GL2ES3.GL_PIXEL_PACK_BUFFER, 0, 16, GL2ES3.GL_MAP_READ_BIT);
        if (b != null) {
            FloatBuffer f = b.order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
            float x = f.get(0);
            measureRead = f.get(3);
            if (x > 0) focusRead = 1 / x;
            gl.glUnmapBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER);
        }
        gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, 0);
        pboFull = false;
    }

    // ------------------------------------------------------------------ Hilfen

    private void target(GL4 gl, int t, int tw2, int th2) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, t, 0);
        gl.glDrawBuffer(GL.GL_COLOR_ATTACHMENT0);
        gl.glViewport(0, 0, tw2, th2);
    }

    private void draw(GL4 gl, int t, int tw2, int th2) {
        target(gl, t, tw2, th2);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
    }

    private int use(GL4 gl, String name) {
        int p = progs.get(name);
        gl.glUseProgram(p);
        return p;
    }

    private int loc(GL4 gl, int p, String n) {
        return locs.computeIfAbsent(p, k -> new HashMap<>()).computeIfAbsent(n, k -> gl.glGetUniformLocation(p, k));
    }

    private void seti(GL4 gl, int p, String n, int v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1i(l, v); }

    private void setf(GL4 gl, int p, String n, float v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1f(l, v); }

    private void set2(GL4 gl, int p, String n, float a, float b) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform2f(l, a, b); }

    private void set3(GL4 gl, int p, String n, float a, float b, float c) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform3f(l, a, b, c); }

    private void set4(GL4 gl, int p, String n, float a, float b, float c, float d) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform4f(l, a, b, c, d); }

    private void arr(GL4 gl, int p, String n, double[] v) {
        int l = loc(gl, p, n + "[0]");
        if (l < 0) l = loc(gl, p, n);
        if (l < 0 || v.length == 0) return;
        float[] f = new float[v.length];
        for (int i = 0; i < v.length; i++) f[i] = (float) v[i];
        gl.glUniform1fv(l, f.length, f, 0);
    }

    private void tex(GL4 gl, int p, String n, int unit, int t) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t);
        seti(gl, p, n, unit);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    /** Inverse einer 4 × 4-Matrix (Spalten), null wenn singulär (W9c Runde 2: Richtung je Bildpunkt). */
    static float[] invert(float[] m) {
        double[] a = new double[16];
        for (int i = 0; i < 16; i++) a[i] = m[i];
        double[] inv = new double[16];
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] + a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10];
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] - a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10];
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] + a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9];
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] - a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9];
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] - a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10];
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] + a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10];
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] - a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9];
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] + a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9];
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] + a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6];
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] - a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6];
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] + a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5];
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] - a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5];
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] - a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6];
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] + a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6];
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] - a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5];
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] + a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5];
        double det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        if (Math.abs(det) < 1e-30) return null;
        float[] o = new float[16];
        for (int i = 0; i < 16; i++) o[i] = (float) (inv[i] / det);
        return o;
    }

    private static int texture(GL4 gl, int tw2, int th2, int fmt, int filter, boolean mips) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        int levels = mips ? 1 + (int) Math.floor(Math.log(Math.max(tw2, th2)) / Math.log(2)) : 1;
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, levels, fmt, tw2, th2);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, mips ? GL.GL_LINEAR_MIPMAP_LINEAR : filter);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, filter);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    public void dispose(GL4 gl) {
        if (tq[0] != 0) { gl.glDeleteQueries(tq.length, tq, 0); java.util.Arrays.fill(tq, 0); tqIssued[0] = tqIssued[1] = false; }
        if (!ready) return;
        for (int p : progs.values()) gl.glDeleteProgram(p);
        progs.clear();
        locs.clear();
        int[] all = {texHalf, texHalf2, texT1, texT2, texDof, texSpr, texOut, state[0], state[1]};
        for (int t : all) if (t != 0) gl.glDeleteTextures(1, new int[]{t}, 0);
        for (int i = 0; i < GLARE_LEVELS; i++) {
            if (texDown[i] != 0) gl.glDeleteTextures(1, new int[]{texDown[i]}, 0);
            if (texUp[i] != 0) gl.glDeleteTextures(1, new int[]{texUp[i]}, 0);
        }
        gl.glDeleteBuffers(3, new int[]{cmdBuf, spotBuf, pbo}, 0);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        ready = false;
        w = h = 0;
    }

    // ------------------------------------------------------------------ Shader

    static String read(String file) {
        try (InputStream in = LensGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("Shader " + file + " fehlt (src mit auf den Klassenpfad?)");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Shader " + file + ": " + e.getMessage(), e);
        }
    }

    private static int shader(GL4 gl, int type, String src, String what) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{"#version 430 core\n" + src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] log = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, log.length, null, 0, log, 0);
            throw new IllegalStateException("com.dan.lens: " + what + ": " + new String(log, StandardCharsets.UTF_8).trim());
        }
        return s;
    }

    private static int link(GL4 gl, int p, String what) {
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] log = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, log.length, null, 0, log, 0);
            throw new IllegalStateException("com.dan.lens: " + what + ": " + new String(log, StandardCharsets.UTF_8).trim());
        }
        return p;
    }

    private static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        int a = shader(gl, GL4.GL_VERTEX_SHADER, vs, "Eckpunkte"), b = shader(gl, GL4.GL_FRAGMENT_SHADER, fs, "Bildpunkte");
        gl.glAttachShader(p, a);
        gl.glAttachShader(p, b);
        link(gl, p, "Programm");
        gl.glDeleteShader(a);
        gl.glDeleteShader(b);
        return p;
    }

    private static int compute(GL4 gl, String cs) {
        int p = gl.glCreateProgram();
        int a = shader(gl, GL4.GL_COMPUTE_SHADER, cs, "Rechnung");
        gl.glAttachShader(p, a);
        link(gl, p, "Rechnung");
        gl.glDeleteShader(a);
        return p;
    }
}
