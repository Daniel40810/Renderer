package com.dan.precip.gl;

import com.dan.precip.DropOptics;
import com.dan.precip.Drops;
import com.dan.precip.Precip;
import com.dan.precip.Ripples;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

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
 * Niederschlag auf der Grafikkarte (GL 4.3, Compute):
 * <ul>
 * <li><b>Tropfen</b> ({@code drops.comp}): jeder Tropfen im Kasten um das Auge hat einen Zustand, fällt mit seinem
 * Luftwiderstand durch Wind, Böen und Wirbel, schwingt, schlägt auf Wasser und Grund auf (ins Buch der Einschläge) und
 * kommt oben wieder; unter dem Kronendach fällt ein Teil frei durch, der Rest tropft groß vom Dach.</li>
 * <li><b>Wellen</b> ({@code ripple_*.comp}, {@code fft.comp}): ein Wellenfeld fest in der Welt, spektral genau gelöst
 * ({@link Ripples}); jeder Einschlag gibt seinen Druckstoß hinein, die echten aus dem Kasten und zufällige nach der
 * Verteilung für alle anderen. Ergebnis: {@link #rippleTexture()} mit (∂h/∂x, ∂h/∂z, h, ∂h/∂t) für das Wasser
 * ({@code com.dan.ocean}, {@code detailMode} 1).</li>
 * <li><b>Spritzer</b> ({@code splash.comp}): aus jedem Einschlag Krone, Strahl, Tröpfchen, Bläschen oder nichts.</li>
 * <li><b>Licht</b> ({@code dropenv.comp}): was ein Tropfen aus jeder Richtung zeigt, ∫ L p dΩ mit der Phasenfunktion aus
 * {@link DropOptics}.</li>
 * <li><b>Zeichnen</b>: Vorhänge ab 60 m ({@code curtain.frag}), Schichten bis 60 m ({@code layers.frag}), Tropfen und
 * Tröpfchen als ehrlich belichtete Spuren mit Linse ({@code drop.vert/frag}), Kronen, Strahlen und Bläschen
 * ({@code splash.vert/frag}).</li>
 * </ul>
 * Aufruf: {@link #update} vor dem Wasser (rechnet Tropfen, Wellen, Spritzer), {@link #draw} nach der Szene ins HDR-Ziel
 * mit Tiefe (Tropfen testen gegen sie), mit einer Kopie der Tiefe ({@link View#depthTexture}) für Vorhänge und
 * Schichten. Das Paket kennt weder Himmel noch Wolken noch Meer: Licht, Rundumbild und Schattenkarte reicht die App.
 */
public final class PrecipGl {

    /** Ansicht: Projektion·Blick und Umkehrung, Auge, Blickrichtung, Tiefenkonvention, Bildgröße, Kopie der Szenentiefe. */
    public static final class View {
        public float[] viewProj, invViewProj;
        public double[] eye = new double[3], forward = {0, 0, -1};
        public boolean reversedZ;
        public double near = 0.2, far = 60_000, fovyDeg = 50;
        public int width = 1, height = 1, depthTexture;
    }

    /**
     * Licht, vorbelichtet. Sonne und Mond als Bestrahlung senkrecht zum Strahl (wie {@code Sky.State.sunColor}), Himmel
     * oben und am Horizont und der Grund als Strahldichte; das Rundumbild (0 = keins) wie in {@code com.dan.sky}
     * abgebildet (u = atan(x, −z)/2π + ½, v = asin(y)/π + ½). sunVisible: Anteil der Sonne am Auge durch die Wolken; die
     * Schattenkarte (optional, R = Licht durch die Wolken ab der Höhe shadowRect[3], von oben) gibt ihn für die Vorhänge
     * am Ort. Dunst: Länge (m) und Farbe.
     */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunIrradiance = {0, 0, 0}, moonDir = {0, -1, 0}, moonIrradiance = {0, 0, 0};
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7}, ground = {0.05, 0.05, 0.05};
        public int panorama;
        public double sunVisible = 1;
        public int shadowTexture;
        public double[] shadowRect = {0, 0, 0, 0};
        public double fogDistance = 1e9;
        public double[] fogColor = {0.5, 0.6, 0.7};
    }

    /** Plätze im Buch der Einschläge: echte aus dem Kasten, zufällige ab 1 mm (für Spritzer). */
    public static final int BOX_CAP = 8192, STAT_CAP = 8192;
    /** Höchstens so viele zufällige Einschläge je Bild ins Wellenfeld. */
    public static final int STAT_MAX = 262_144;
    /** Ende der Schichten, Beginn der Vorhänge (m). */
    public static final double LAYERS_END = 60;

    /** Stärke der Ringe auf dem Wasser (1 = nach Rechnung), Stärke der Zeichnung. */
    public volatile double rippleGain = 1;
    /**
     * Blickrichtung (x, y, z; oder x, z) und Bildwinkel (Grad, senkrecht) für das Wellenfenster: es liegt dort, wo der
     * untere Bildrand das Wasser trifft, und reicht von dort ins Bild (höchstens 30 m vor dem Auge). null: am Auge.
     */
    public volatile double[] aim;
    public volatile double aimFovy = 50;
    /** Neu säen beim nächsten {@link #update} (Sprung des Auges, neue Szene). */
    public volatile boolean reseed;

    private int progDrops, progDrop, progLayers, progCurtain, progEnv, progSplash, progSplashDraw, progRSrc, progRPack, progRStep, progROut, vao;
    private final Map<Integer, Integer> fftByN = new HashMap<>();
    private int ssboDrops, ssboParts, ssboImpacts, ssboRSrc;
    /**
     * Seit LituyaBay1958 W8b Runde 2: das feine zweite Wellenfeld nah am Auge (Kaskade): ein Fenster mit
     * {@code Quality.fineCell} statt {@code rippleCell}, das nur die Wellen zwischen π/rippleCell und π/fineCell trägt –
     * zusammen mit dem groben Feld genau dasselbe Spektrum, nah bis in die Kapillarwellen von 2 bis 3 mm. Vorgabe aus.
     */
    public volatile boolean fine = false;
    /** Seit W8b Runde 2: die Schichten ziehen mit dem Wind quer zum Blick (Vorgabe an). */
    public volatile boolean layersDrift = true;
    /**
     * Seit W8b Runde 2: Schritte im Vorhang je Stufe mal diesem Anteil. Der Vorhang wird vor der zeitlichen Glättung
     * gezeichnet und tastet je Bild versetzt ab; die Glättung mittelt die Schritte mehrerer Bilder. 1 = wie bisher.
     */
    public volatile double curtainStepShare = 1;
    private int ssboRSrc2, imgSrc2, imgState2, imgOut2, texRipple2, rippleN2;
    private double rippleDx2;
    private final double[] rippleMid2 = new double[2];
    private int texMap, texGround, texPhase, texEnv, imgSrc, imgState, imgOut, texRipple;
    private int dropsCap, partsCap, rippleN;
    private double rippleDx;
    private int mapVersion = -1, groundVersion = -1;
    private Precip.Snapshot snap;
    private Light light = new Light();
    private double envAge = 1e9, lastEnvKey = Double.NaN;
    private double[] lastEye;
    private double lastTime = Double.NaN;
    private final double[] rippleMid = new double[2];
    private int frame;
    private boolean ready;
    private String info = "";
    private final java.util.SplittableRandom rng = new java.util.SplittableRandom(1958);

    public boolean ready() { return ready; }
    public String info() { return info; }

    // ------------------------------------------------------------------ Aufbau

    public void init(GL4 gl) {
        String c = read("precip_common.glsl");
        progDrops = compute(gl, "drops.comp", c, null);
        progSplash = compute(gl, "splash.comp", c, null);
        progRSrc = compute(gl, "ripple_src.comp", c, null);
        progRPack = compute(gl, "ripple_pack.comp", null, null);
        progRStep = compute(gl, "ripple_step.comp", null, null);
        progROut = compute(gl, "ripple_out.comp", null, null);
        progEnv = compute(gl, "dropenv.comp", c, null);
        progDrop = program(gl, src("drop.vert", c), src("drop.frag", c));
        progSplashDraw = program(gl, src("splash.vert", c), src("splash.frag", c));
        progLayers = program(gl, src("fullscreen.vert", null), src("layers.frag", c));
        progCurtain = program(gl, src("fullscreen.vert", null), src("curtain.frag", c));
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        texMap = upload(gl, 0, GL4.GL_R32F, 1, 1, GL4.GL_RED, new float[]{1});
        linear(gl, texMap);
        texGround = upload(gl, 0, GL4.GL_RGBA32F, 1, 1, GL.GL_RGBA, new float[]{0, 0, -1e9f, 0});
        // Phasenfunktion: Zeile 0 scharf, Zeile 1 auf rund 6° geglättet (für die Umgebung)
        DropOptics o = new DropOptics();
        float[] t = o.table(), sm = smooth(t, 3.0);
        float[] ph = new float[DropOptics.BINS * 2 * 4];
        for (int i = 0; i < DropOptics.BINS; i++)
            for (int k = 0; k < 3; k++) {
                ph[i * 4 + k] = t[i * 3 + k];
                ph[(DropOptics.BINS + i) * 4 + k] = sm[i * 3 + k];
            }
        texPhase = tex2d(gl, GL4.GL_RGBA16F, DropOptics.BINS, 2, GL.GL_LINEAR, GL.GL_CLAMP_TO_EDGE);
        texPhase = upload(gl, texPhase, GL4.GL_RGBA16F, DropOptics.BINS, 2, GL.GL_RGBA, ph);
        texEnv = tex2d(gl, GL4.GL_RGBA16F, 64, 32, GL.GL_LINEAR, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texEnv);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        int[] b = new int[4];
        gl.glGenBuffers(4, b, 0);
        ssboDrops = b[0];
        ssboParts = b[1];
        ssboImpacts = b[2];
        ssboRSrc = b[3];
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssboImpacts);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 16L + (long) (BOX_CAP + STAT_CAP) * 32, null, GL4.GL_DYNAMIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        ready = true;
        info = "Tropfen, Wellen, Spritzer, Schichten und Vorhänge bereit";
    }

    /** Phasentabelle (je Stufe RGB) mit einer Gaußglocke von sigma Grad geglättet. */
    static float[] smooth(float[] t, double sigmaDeg) {
        int n = DropOptics.BINS, r = (int) Math.ceil(3 * sigmaDeg / DropOptics.STEP);
        float[] out = new float[t.length];
        for (int i = 0; i < n; i++)
            for (int k = 0; k < 3; k++) {
                double s = 0, w = 0;
                for (int j = -r; j <= r; j++) {
                    int q = i + j;
                    if (q < 0 || q >= n) continue;
                    // Gewicht mit dem Raumwinkel der Stufe, damit die Glättung ∫ p dΩ erhält
                    double th = Math.toRadians((q + 0.5) * DropOptics.STEP), g = Math.exp(-0.5 * Math.pow(j * DropOptics.STEP / sigmaDeg, 2)) * Math.max(1e-4, Math.sin(th));
                    s += t[q * 3 + k] * g;
                    w += g;
                }
                out[i * 3 + k] = (float) (s / w);
            }
        return out;
    }

    private void ensureBuffers(GL4 gl, Precip.Quality q) {
        if (q.drops != dropsCap) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssboDrops);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) q.drops * 48, null, GL4.GL_DYNAMIC_DRAW);
            dropsCap = q.drops;
            reseed = true;
        }
        if (q.splashes != partsCap) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssboParts);
            ByteBuffer z = ByteBuffer.allocateDirect(q.splashes * 48).order(ByteOrder.nativeOrder());
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) q.splashes * 48, z, GL4.GL_DYNAMIC_DRAW);
            partsCap = q.splashes;
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        if (q.rippleRes != rippleN || q.rippleCell != rippleDx) {
            Gl.deleteTextures(gl, imgSrc, imgState, imgOut, texRipple);
            int n = q.rippleRes;
            imgSrc = tex2d(gl, GL4.GL_RGBA32F, n, n, GL.GL_NEAREST, GL.GL_REPEAT);
            imgState = tex2d(gl, GL4.GL_RGBA32F, n, n, GL.GL_NEAREST, GL.GL_REPEAT);
            imgOut = tex2d(gl, GL4.GL_RGBA32F, n, n, GL.GL_NEAREST, GL.GL_REPEAT);
            clearTex(gl, imgState, n);
            int levels = 1 + (int) Math.floor(Math.log(n) / Math.log(2));
            int[] v = new int[1];
            gl.glGenTextures(1, v, 0);
            texRipple = v[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, texRipple);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, levels, GL4.GL_RGBA16F, n, n);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
            // streifender Blick auf das Wasser: ohne anisotrope Filterung verschwimmen die Ringe in der Mip-Stufe
            if (gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic")) gl.glTexParameterf(GL.GL_TEXTURE_2D, 0x84FE, 16f);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssboRSrc);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) n * n * 4, ByteBuffer.allocateDirect(n * n * 4).order(ByteOrder.nativeOrder()), GL4.GL_DYNAMIC_DRAW);
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
            rippleN = n;
            rippleDx = q.rippleCell;
            int fn = n;
            fftByN.computeIfAbsent(n, k -> compute(gl, "fft.comp", null, new String[]{"N " + fn, "HALF " + (fn / 2)}));
        }
        int n2 = fine ? q.fineRes : 0;
        if (n2 != rippleN2 || (n2 > 0 && q.fineCell != rippleDx2)) {
            Gl.deleteTextures(gl, imgSrc2, imgState2, imgOut2, texRipple2);
            imgSrc2 = imgState2 = imgOut2 = texRipple2 = 0;
            if (n2 > 0) {
                imgSrc2 = tex2d(gl, GL4.GL_RGBA32F, n2, n2, GL.GL_NEAREST, GL.GL_REPEAT);
                imgState2 = tex2d(gl, GL4.GL_RGBA32F, n2, n2, GL.GL_NEAREST, GL.GL_REPEAT);
                imgOut2 = tex2d(gl, GL4.GL_RGBA32F, n2, n2, GL.GL_NEAREST, GL.GL_REPEAT);
                clearTex(gl, imgState2, n2);
                int levels = 1 + (int) Math.floor(Math.log(n2) / Math.log(2));
                int[] v = new int[1];
                gl.glGenTextures(1, v, 0);
                texRipple2 = v[0];
                gl.glBindTexture(GL.GL_TEXTURE_2D, texRipple2);
                gl.glTexStorage2D(GL.GL_TEXTURE_2D, levels, GL4.GL_RGBA16F, n2, n2);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
                if (gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic")) gl.glTexParameterf(GL.GL_TEXTURE_2D, 0x84FE, 16f);
                gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
                if (ssboRSrc2 == 0) { int[] b2 = new int[1]; gl.glGenBuffers(1, b2, 0); ssboRSrc2 = b2[0]; }
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssboRSrc2);
                gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) n2 * n2 * 4, ByteBuffer.allocateDirect(n2 * n2 * 4).order(ByteOrder.nativeOrder()), GL4.GL_DYNAMIC_DRAW);
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
                int fn2 = n2;
                fftByN.computeIfAbsent(n2, k -> compute(gl, "fft.comp", null, new String[]{"N " + fn2, "HALF " + (fn2 / 2)}));
            }
            rippleN2 = n2;
            rippleDx2 = q.fineCell;
        }
    }

    /** Linearer Filter (die Karte der Schauer wird bilinear zwischen den Zellmitten gelesen). */
    private static void linear(GL4 gl, int tex) {
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    private static void clearTex(GL4 gl, int tex, int n) {
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL.GL_RGBA, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(n * n * 4));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    // ------------------------------------------------------------------ Lauf

    /**
     * Karte und Grund hochladen (wenn neu), die Umgebung im Tropfen (bis 4-mal je Sekunde), dann Tropfen, Wellen und
     * Spritzer um dt der Momentaufnahme. Danach sind Programm, Puffer und Bindungen gelöst.
     */
    public void update(GL4 gl, Precip.Snapshot s, Light l) {
        snap = s;
        light = l == null ? new Light() : l;
        Precip.Quality q = s.quality();
        ensureBuffers(gl, q);
        if (s.mapVersion() != mapVersion) {
            mapVersion = s.mapVersion();
            if (s.mapN() > 0) { texMap = upload(gl, texMap, GL4.GL_R32F, s.mapN(), s.mapN(), GL4.GL_RED, s.map()); linear(gl, texMap); }
        }
        Precip.Grid g = s.ground();
        if (g.version() != groundVersion) {
            groundVersion = g.version();
            texGround = upload(gl, texGround, GL4.GL_RGBA32F, g.n(), g.n(), GL.GL_RGBA, g.cells());
        }
        // Sprung des Auges oder neue Zeit: neu säen
        if (lastEye != null && Math.hypot(s.eyeX() - lastEye[0], s.eyeZ() - lastEye[2]) + Math.abs(s.eyeY() - lastEye[1]) > 2 * q.box) reseed = true;
        if (!Double.isNaN(lastTime) && s.time() < lastTime) reseed = true;
        lastEye = new double[]{s.eyeX(), s.eyeY(), s.eyeZ()};
        lastTime = s.time();
        // Umgebung im Tropfen
        envAge += s.dt() / Math.max(1e-4, s.timeScale());
        double key = l == null ? 0 : l.skyTop[1] + 3 * l.skyHorizon[1] + 7 * l.ground[1] + l.panorama;
        if (envAge >= 0.25 || key != lastEnvKey) {
            envAge = 0;
            lastEnvKey = key;
            gl.glUseProgram(progEnv);
            common(gl, progEnv, s);
            lightU(gl, progEnv);
            gl.glBindImageTexture(0, texEnv, 0, false, 0, GL4.GL_WRITE_ONLY, GL4.GL_RGBA16F);
            gl.glDispatchCompute(8, 4, 1);
            gl.glMemoryBarrier(GL4.GL_TEXTURE_FETCH_BARRIER_BIT | GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        }
        if (s.rate() <= 0 && !reseed) { gl.glUseProgram(0); frame++; return; }
        // Buch leeren (echte, zufällige; der Ring der Teilchen läuft weiter)
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssboImpacts);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 8, Buffers.newDirectIntBuffer(new int[]{0, 0}));
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssboDrops);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, ssboImpacts);
        // 1 Tropfen
        gl.glUseProgram(progDrops);
        common(gl, progDrops, s);
        gl.glUniform1i(loc(gl, progDrops, "uCount"), q.drops);
        gl.glUniform1ui(loc(gl, progDrops, "uFrame"), frame);
        gl.glUniform1i(loc(gl, progDrops, "uImpactCap"), BOX_CAP);
        if (reseed) {
            clearTex(gl, imgState, rippleN);   // neue Szene: auch das Wasser beruhigt
            gl.glUniform1i(loc(gl, progDrops, "uMode"), 0);
            gl.glDispatchCompute((q.drops + 63) / 64, 1, 1);
            gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
            reseed = false;
        }
        gl.glUniform1i(loc(gl, progDrops, "uMode"), 1);
        gl.glDispatchCompute((q.drops + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
        // 2 Wellen
        ripples(gl, s);
        // 3 Spritzer: entstehen, dann fliegen
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssboParts);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, ssboImpacts);
        gl.glUseProgram(progSplash);
        common(gl, progSplash, s);
        gl.glUniform1i(loc(gl, progSplash, "uCap"), q.splashes);
        gl.glUniform1i(loc(gl, progSplash, "uBoxCap"), BOX_CAP);
        gl.glUniform1i(loc(gl, progSplash, "uStatCap"), STAT_CAP);
        gl.glUniform1ui(loc(gl, progSplash, "uFrame"), frame);
        gl.glUniform1i(loc(gl, progSplash, "uMode"), 0);
        gl.glDispatchCompute((BOX_CAP + STAT_CAP + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
        gl.glUniform1i(loc(gl, progSplash, "uMode"), 1);
        gl.glDispatchCompute((q.splashes + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
        for (int i = 0; i < 3; i++) gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, i, 0);
        gl.glUseProgram(0);
        frame++;
    }

    /** Mitte des Wellenfensters: vom Treffpunkt des unteren Bildrands mit dem Wasser ein gutes Drittel des Fensters ins Bild. */
    private void rippleCenter(Precip.Snapshot s) { rippleCenter(s, rippleN * rippleDx, rippleMid); }

    private void rippleCenter(Precip.Snapshot s, double size, double[] rippleMid) {
        double h = s.eyeY() - s.waterLevel();
        double[] a = aim;
        double ax = 0, ay = 0, az = 0;
        if (a != null) {
            if (a.length >= 3) { ax = a[0]; ay = a[1]; az = a[2]; } else { ax = a[0]; az = a[1]; }
        }
        double hl = Math.hypot(ax, az), off = 0;
        if (hl > 1e-6) {
            double bottom = Math.atan2(ay, hl) - Math.toRadians(aimFovy) / 2;
            double near = h > 0.05 && bottom < -1e-3 ? h / Math.tan(-bottom) : 30;
            off = Math.min(30, near + 0.4 * size);
            ax /= hl;
            az /= hl;
        }
        rippleMid[0] = s.eyeX() + ax * off;
        rippleMid[1] = s.eyeZ() + az * off;
    }

    private void ripples(GL4 gl, Precip.Snapshot s) {
        rippleCenter(s);
        field(gl, s, rippleN, rippleDx, rippleMid, imgSrc, imgState, imgOut, texRipple, ssboRSrc, 0, Math.PI / rippleDx, 1);
        if (rippleN2 > 0 && texRipple2 != 0) {
            rippleCenter(s, rippleN2 * rippleDx2, rippleMid2);
            field(gl, s, rippleN2, rippleDx2, rippleMid2, imgSrc2, imgState2, imgOut2, texRipple2, ssboRSrc2, Math.PI / rippleDx, Math.PI / rippleDx2, 0);
        }
    }

    /** Ein Wellenfeld: Einschläge ins Fenster (n Zellen zu dx um mid), Band kLow … kHigh fortschreiben, ausgeben. */
    private void field(GL4 gl, Precip.Snapshot s, int n, double rippleDx, double[] rippleMid, int imgSrc, int imgState, int imgOut, int texRipple, int ssboRSrc,
                       double kLow, double kHigh, int append) {
        double size = n * rippleDx;
        // zufällige Einschläge: alle Tropfen ab 0,3 mm bei voller Menge auf das Fenster (der Shader dünnt nach der Karte aus
        // und behält nach v / v(dmax): so viele Züge, dass im Mittel Drops.impacts ankommen)
        double rate = s.rate() <= 0 ? 0 : Drops.impactDraws(s.n0(), s.lambda(), 0.3, s.dmax()) * size * size * s.dt();
        int stat = (int) Math.min(STAT_MAX, Math.floor(rate + rng.nextDouble()));
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, ssboImpacts);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 2, ssboRSrc);
        gl.glUseProgram(progRSrc);
        common(gl, progRSrc, s);
        gl.glUniform1i(loc(gl, progRSrc, "uN"), n);
        gl.glUniform1f(loc(gl, progRSrc, "uDx"), (float) rippleDx);
        gl.glUniform2f(loc(gl, progRSrc, "uRMid"), (float) rippleMid[0], (float) rippleMid[1]);
        gl.glUniform1i(loc(gl, progRSrc, "uStatCount"), stat);
        gl.glUniform1ui(loc(gl, progRSrc, "uFrame"), frame);
        gl.glUniform1i(loc(gl, progRSrc, "uBoxCap"), BOX_CAP);
        gl.glUniform1i(loc(gl, progRSrc, "uStatCap"), STAT_CAP);
        gl.glUniform1i(loc(gl, progRSrc, "uAppend"), append);
        gl.glDispatchCompute((stat + BOX_CAP + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
        // Festkomma in die Fläche, vorwärts wandeln
        gl.glUseProgram(progRPack);
        gl.glUniform1i(loc(gl, progRPack, "uN"), n);
        gl.glBindImageTexture(0, imgSrc, 0, false, 0, GL4.GL_WRITE_ONLY, GL4.GL_RGBA32F);
        gl.glDispatchCompute(n / 8, n / 8, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL4.GL_SHADER_STORAGE_BARRIER_BIT);
        fft(gl, imgSrc, n, -1);
        // jede Welle fortschreiben
        gl.glUseProgram(progRStep);
        gl.glUniform1i(loc(gl, progRStep, "uN"), n);
        gl.glUniform1f(loc(gl, progRStep, "uSize"), (float) size);
        gl.glUniform1f(loc(gl, progRStep, "uDt"), (float) s.dt());
        gl.glUniform2f(loc(gl, progRStep, "uBand"), (float) kLow, (float) kHigh);
        gl.glUniform2f(loc(gl, progRStep, "uDamp"), (float) Ripples.FILM, (float) Ripples.BASE);
        gl.glUniform1i(loc(gl, progRStep, "uInject"), 1);
        gl.glBindImageTexture(0, imgSrc, 0, false, 0, GL4.GL_READ_ONLY, GL4.GL_RGBA32F);
        gl.glBindImageTexture(1, imgState, 0, false, 0, GL4.GL_READ_WRITE, GL4.GL_RGBA32F);
        gl.glBindImageTexture(2, imgOut, 0, false, 0, GL4.GL_WRITE_ONLY, GL4.GL_RGBA32F);
        gl.glDispatchCompute(n / 8, n / 8, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        fft(gl, imgOut, n, 1);
        gl.glUseProgram(progROut);
        gl.glUniform1i(loc(gl, progROut, "uN"), n);
        gl.glBindImageTexture(0, imgOut, 0, false, 0, GL4.GL_READ_ONLY, GL4.GL_RGBA32F);
        gl.glBindImageTexture(1, texRipple, 0, false, 0, GL4.GL_WRITE_ONLY, GL4.GL_RGBA16F);
        gl.glDispatchCompute(n / 8, n / 8, 1);
        gl.glMemoryBarrier(GL4.GL_TEXTURE_FETCH_BARRIER_BIT | GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texRipple);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        for (int i = 0; i < 3; i++) gl.glBindImageTexture(i, 0, 0, false, 0, GL4.GL_READ_ONLY, GL4.GL_RGBA32F);
    }

    private void fft(GL4 gl, int img, int n, int sign) {
        int p = fftByN.get(n);
        gl.glUseProgram(p);
        gl.glUniform1f(loc(gl, p, "uSign"), sign);
        gl.glBindImageTexture(0, img, 0, false, 0, GL4.GL_READ_WRITE, GL4.GL_RGBA32F);
        for (int vert = 0; vert < 2; vert++) {
            gl.glUniform1i(loc(gl, p, "uVertical"), vert);
            gl.glDispatchCompute(n, 1, 1);
            gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        }
    }

    /** Seit W8b Runde 2: das feine Feld (0 = keins), seine Kante, seine Mitte, die Rauigkeit unter seinem Raster. */
    public int fineTexture() { return texRipple2; }
    public double fineSize() { return rippleN2 * rippleDx2; }
    public double[] fineCenter() { return rippleMid2.clone(); }
    public double fineRoughness() {
        Precip.Snapshot s = snap;
        if (s == null || s.rate() <= 0 || rippleN2 <= 0) return 0;
        return Math.max(0, Ripples.slopeVariance(s.n0(), s.lambda(), 0.3, s.dmax(), 0) - Ripples.slopeVariance(s.n0(), s.lambda(), 0.3, s.dmax(), rippleDx2));
    }

    /** Wellenfeld fürs Wasser: (∂h/∂x, ∂h/∂z, h, ∂h/∂t), RGBA16F, ringsum geschlossen, mit Mip-Stufen; 0 vor dem ersten {@link #update}. */
    public int rippleTexture() { return texRipple; }
    /** Kante des Wellenfelds (m). */
    public double rippleSize() { return rippleN * rippleDx; }
    /** Mitte des Fensters, in dem das Feld echt ist (x, z). */
    public double[] rippleCenter() { return rippleMid.clone(); }
    /** Stärke für das Wasser: die Steigungen sind echt (1 · {@link #rippleGain}), ohne Regen 0. */
    public double rippleStrength() { return snap == null || snap.rate() <= 0 ? 0 : rippleGain; }

    /**
     * Rauigkeit, die das Feld nicht trägt: mittlere quadratische Steigung ⟨|∇h|²⟩ der Wellen unter dem Raster
     * ({@link Ripples#subgridVariance}; bei 5 mm/h und 8 mm fast alles). Das Wasser legt sie zu seiner eigenen
     * Rauigkeit (Varianz erhalten), mal Stärke², verteilt nach {@link #rippleShare}. 0 ohne Regen.
     */
    public double rippleRoughness() { roughness(); return roughSub; }

    /**
     * Mittlere quadratische Steigung, die das Feld trägt ({@link Ripples#slopeVariance} beim Raster), mal Stärke²: wo
     * die Fläche die Steigungen wegmittelt (Mip-Stufen, Ferne), gehört das Weggemittelte zur Rauigkeit.
     */
    public double rippleVariance() { roughness(); return roughGrid; }

    /**
     * Anteil der Fläche, der die Steigung trägt ({@link Ripples#intermittency}): {unter dem Raster, im Raster}. Das
     * Wasser kippt nur diesen Anteil der Facetten, dafür stärker (gleiche Varianz und Kurtosis): zwischen den
     * Einschlägen bleibt der Spiegel glatt.
     */
    public double[] rippleShare() { return snap == null ? new double[]{1, 1} : Ripples.intermittency(snap.rate()); }

    private double roughSub, roughGrid, roughN0 = -1, roughLam, roughDmax, roughDx;

    /** Neu gerechnet (je rund 1 ms), wenn sich die Verteilung um mehr als 2 % (N0) bzw. 0,5 % (Λ) geändert hat. */
    private void roughness() {
        Precip.Snapshot s = snap;
        if (s == null || s.rate() <= 0) { roughSub = roughGrid = 0; roughN0 = -1; return; }
        if (roughN0 > 0 && Math.abs(s.n0() / roughN0 - 1) < 0.02 && Math.abs(s.lambda() / roughLam - 1) < 0.005 && s.dmax() == roughDmax && rippleDx == roughDx) return;
        roughN0 = s.n0();
        roughLam = s.lambda();
        roughDmax = s.dmax();
        roughDx = rippleDx;
        roughGrid = Ripples.slopeVariance(s.n0(), s.lambda(), 0.3, s.dmax(), rippleDx);
        roughSub = Math.max(0, Ripples.slopeVariance(s.n0(), s.lambda(), 0.3, s.dmax(), 0) - roughGrid);
    }
    /** Karte der Schauer (R32F) und ihr Rechteck (x0, z0, Kante), etwa für eine Maske auf dem Wasser. */
    public int curtainTexture() { return texMap; }
    public double[] curtainRect() { return snap == null ? new double[]{0, 0, 1} : new double[]{snap.mapX0(), snap.mapZ0(), snap.mapCell() * Math.max(1, snap.mapN())}; }

    // ------------------------------------------------------------------ Zeichnen

    /** Teile für {@link #draw(GL4, View, double, int)}: Vorhänge, Schichten, Tropfen mit Spritzern. */
    public static final int CURTAIN = 1, LAYERS = 2, DROPS = 4, ALL = 7;

    /** Alles: {@link #draw(GL4, View, double, int)} mit {@link #ALL}. */
    public void draw(GL4 gl, View v, double strength) { draw(gl, v, strength, ALL); }

    /**
     * Vorhänge, Schichten, Tropfen, Tröpfchen und Spritzer ins gebundene Ziel (vormultipliziert, mit Tiefe der Szene
     * gebunden für den Test der Tropfen). strength 0..2 (1 = nach Rechnung). parts wählt die Teile: Mit zeitlicher
     * Kantenglättung (TAA) gehören die Vorhänge davor (ihr Rauschen glättet der Verlauf), Schichten und Tropfen danach,
     * sonst mischt der Verlauf die bewegten Spuren auf ein Zehntel weg.
     */
    public void draw(GL4 gl, View v, double strength, int parts) {
        Precip.Snapshot s = snap;
        if (s == null || s.rate() <= 0 || strength <= 0 || !ready) return;
        Light l = light;
        gl.glBindVertexArray(vao);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        double pixel = 2 * Math.tan(Math.toRadians(v.fovyDeg) / 2) / Math.max(1, v.height);
        boolean depth = v.depthTexture != 0 && v.invViewProj != null;
        // 1 Vorhänge ab 60 m
        gl.glDisable(GL.GL_DEPTH_TEST);
        if (v.invViewProj != null && (parts & CURTAIN) != 0) {
            gl.glUseProgram(progCurtain);
            common(gl, progCurtain, s);
            lightU(gl, progCurtain);
            viewU(gl, progCurtain, v, depth);
            gl.glUniform1f(loc(gl, progCurtain, "uStart"), (float) LAYERS_END);
            gl.glUniform1i(loc(gl, progCurtain, "uSteps"), Math.max(4, (int) Math.round(s.quality().curtainSteps * Math.max(0.1, Math.min(1, curtainStepShare)))));
            gl.glUniform1i(loc(gl, progCurtain, "uFrame"), frame);
            gl.glUniform1f(loc(gl, progCurtain, "uStrength"), (float) Math.min(2, strength));
            bindTex(gl, 7, l.shadowTexture, progCurtain, "uPShadow");
            gl.glUniform4f(loc(gl, progCurtain, "uPShadowRect"), (float) l.shadowRect[0], (float) l.shadowRect[1],
                    l.shadowTexture != 0 ? (float) l.shadowRect[2] : 0f, (float) l.shadowRect[3]);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        if (v.invViewProj != null && (parts & LAYERS) != 0) {
            // 2 Schichten bis 60 m
            gl.glUseProgram(progLayers);
            common(gl, progLayers, s);
            lightU(gl, progLayers);
            viewU(gl, progLayers, v, depth);
            gl.glUniform1f(loc(gl, progLayers, "uPixel"), (float) pixel);
            gl.glUniform1i(loc(gl, progLayers, "uLayers"), s.quality().layers);
            gl.glUniform1f(loc(gl, progLayers, "uStart"), 0.6f);
            gl.glUniform1f(loc(gl, progLayers, "uEnd"), (float) LAYERS_END);
            gl.glUniform1f(loc(gl, progLayers, "uDrift"), layersDrift ? 1f : 0f);
            gl.glUniform1f(loc(gl, progLayers, "uStrength"), (float) Math.min(2, strength));
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        gl.glEnable(GL.GL_DEPTH_TEST);
        if ((parts & DROPS) != 0) {
            // 3 Tropfen, dann Tröpfchen
            gl.glUseProgram(progDrop);
            common(gl, progDrop, s);
            lightU(gl, progDrop);
            gl.glUniformMatrix4fv(loc(gl, progDrop, "uVP"), 1, false, v.viewProj, 0);
            set3(gl, progDrop, "uEye", v.eye);
            set3(gl, progDrop, "uFwd", v.forward);
            gl.glUniform1f(loc(gl, progDrop, "uPixel"), (float) pixel);
            gl.glUniform1f(loc(gl, progDrop, "uNow"), (float) (s.time() % 3600));
            gl.glUniform1f(loc(gl, progDrop, "uStrength"), (float) Math.min(2, strength));
            gl.glUniform1i(loc(gl, progDrop, "uSource"), 0);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssboDrops);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, s.quality().drops);
            gl.glUniform1i(loc(gl, progDrop, "uSource"), 1);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssboParts);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, s.quality().splashes);
            // 4 Kronen, Strahlen, Bläschen
            gl.glUseProgram(progSplashDraw);
            common(gl, progSplashDraw, s);
            lightU(gl, progSplashDraw);
            gl.glUniformMatrix4fv(loc(gl, progSplashDraw, "uVP"), 1, false, v.viewProj, 0);
            set3(gl, progSplashDraw, "uEye", v.eye);
            gl.glUniform1f(loc(gl, progSplashDraw, "uPixel"), (float) pixel);
            gl.glUniform1f(loc(gl, progSplashDraw, "uStrength"), (float) Math.min(2, strength));
            gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, s.quality().splashes);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, 0);
        }
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        for (int u = 0; u < 8; u++) { gl.glActiveTexture(GL.GL_TEXTURE0 + u); gl.glBindTexture(GL.GL_TEXTURE_2D, 0); }
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUseProgram(0);
        info = String.format(Locale.GERMAN, "%,d Tropfen ab %.1f mm (%.0f/m³), Schichten %d bis %.0f m, Wellenfeld %d² zu %.0f mm (%.1f m), Spritzer %,d, Belichtung 1/%.0f s",
                s.quality().drops, s.dmin(), s.densityAboveDmin(), s.quality().layers, LAYERS_END, rippleN, rippleDx * 1000, rippleN * rippleDx,
                s.quality().splashes, 1 / Math.max(1e-6, s.shutterSim() / Math.max(1e-4, s.timeScale())));
    }

    // ------------------------------------------------------------------ Uniforms

    private void common(GL4 gl, int p, Precip.Snapshot s) {
        Precip.Quality q = s.quality();
        gl.glUniform4f(loc(gl, p, "uPA"), (float) s.eyeX(), (float) s.eyeY(), (float) s.eyeZ(), (float) q.box);
        gl.glUniform4f(loc(gl, p, "uPB"), (float) q.boxY, (float) (s.time() % 3600), (float) s.dt(), (float) s.shutterSim());
        gl.glUniform4f(loc(gl, p, "uPC"), (float) s.windX(), (float) s.windZ(), (float) s.gustX(), (float) s.gustZ());
        gl.glUniform4f(loc(gl, p, "uPD"), (float) s.rate(), (float) s.lambda(), (float) s.n0(), (float) s.dmin());
        gl.glUniform4f(loc(gl, p, "uPE"), (float) s.dmax(), (float) s.turb(), (float) Math.pow(Drops.RHO_AIR0 / Math.max(0.3, s.airDensity()), 0.4), (float) s.cloudBase());
        gl.glUniform4f(loc(gl, p, "uPF"), (float) s.dsd().a, (float) s.dsd().b, (float) s.n0Exponent(), (float) s.waterLevel());
        gl.glUniform4f(loc(gl, p, "uPG"), (float) s.throughfall(), (float) s.interception(), (float) s.dripMin(), (float) s.dripMax());
        gl.glUniform4f(loc(gl, p, "uPMap"), (float) s.mapX0(), (float) s.mapZ0(), (float) s.mapCell(), s.mapN());
        Precip.Grid g = s.ground();
        gl.glUniform4f(loc(gl, p, "uPGround"), (float) g.x0(), (float) g.z0(), (float) g.size(), g.n());
        gl.glUniform4f(loc(gl, p, "uPBox"), q.drops, (float) q.volume(), (float) s.aperture(), (float) s.focus());
        gl.glUniform1ui(loc(gl, p, "uPSeed"), s.seed());
        float[] sh = new float[16];
        System.arraycopy(s.shelter(), 0, sh, 0, s.shelter().length);
        gl.glUniform4fv(loc(gl, p, "uPShelter[0]"), 4, sh, 0);
        gl.glUniform1i(loc(gl, p, "uPShelterN"), s.shelter().length / 4);
        bindTex(gl, 1, texMap, p, "uPMapTex");
        bindTex(gl, 2, texGround, p, "uPGroundTex");
        bindTex(gl, 3, texPhase, p, "uPPhase");
        bindTex(gl, 4, texEnv, p, "uPEnv");
    }

    private void lightU(GL4 gl, int p) {
        Light l = light;
        set3(gl, p, "uPSunDir", l.sunDir);
        set3(gl, p, "uPSunE", l.sunIrradiance);
        set3(gl, p, "uPMoonDir", l.moonDir);
        set3(gl, p, "uPMoonE", l.moonIrradiance);
        set3(gl, p, "uPSkyTop", l.skyTop);
        set3(gl, p, "uPSkyHorizon", l.skyHorizon);
        set3(gl, p, "uPGroundL", l.ground);
        bindTex(gl, 5, l.panorama, p, "uPPano");
        gl.glUniform1i(loc(gl, p, "uPHasPano"), l.panorama != 0 ? 1 : 0);
        gl.glUniform1f(loc(gl, p, "uPSunVis"), (float) Math.max(0, Math.min(1, l.sunVisible)));
        gl.glUniform1f(loc(gl, p, "uPFogDist"), (float) Math.max(1, Math.min(1e9, l.fogDistance)));
        set3(gl, p, "uPFogColor", l.fogColor);
    }

    private void viewU(GL4 gl, int p, View v, boolean depth) {
        gl.glUniformMatrix4fv(loc(gl, p, "uInvVP"), 1, false, v.invViewProj, 0);
        set3(gl, p, "uEye", v.eye);
        gl.glUniform1i(loc(gl, p, "uReversed"), v.reversedZ ? 1 : 0);
        gl.glUniform1i(loc(gl, p, "uHasDepth"), depth ? 1 : 0);
        bindTex(gl, 0, depth ? v.depthTexture : 0, p, "uSceneDepth");
    }

    private static void bindTex(GL4 gl, int unit, int tex, int p, String name) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        int l = gl.glGetUniformLocation(p, name);
        if (l >= 0) gl.glUniform1i(l, unit);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    private static void set3(GL4 gl, int p, String name, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(p, name), (float) v[0], (float) v[1], (float) v[2]);
    }

    private static int loc(GL4 gl, int p, String name) { return gl.glGetUniformLocation(p, name); }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Grafikkarte gegen Prozessor: (1) Tropfen – 2 000 frisch gesäte Tropfen, 10 Schritte zu 1/60 s ohne Wirbel, Lage und
     * Geschwindigkeit gegen dieselbe Rechnung in Java; (2) Wellen – ein Einschlag von 2 mm, 30 Schritte, Steigungen gegen
     * {@link Ripples.Field}; (3) Umgebung – gleichmäßiges Licht kommt im Tropfen gleich hell an. Ändert den Zustand
     * (danach wird neu gesät).
     */
    public String selfCheck(GL4 gl) {
        StringBuilder out = new StringBuilder();
        Precip p = Precip.moderate();
        p.quality = Precip.Quality.LOW;
        p.windSpeed = 6;
        p.gust = 0;
        p.turbulence = 0;
        p.eyeX = 1000;
        p.eyeY = 50;
        p.eyeZ = -300;
        p.waterLevel = -100;
        p.step(1 / 60.0);
        Precip.Snapshot s0 = p.snapshot();
        Light l = new Light();
        reseed = true;
        update(gl, s0, l);   // säen und ein Schritt
        float[] a = readBuffer(gl, ssboDrops, 2000 * 12);
        for (int i = 0; i < 10; i++) { p.step(1 / 60.0); update(gl, p.snapshot(), l); }
        float[] b = readBuffer(gl, ssboDrops, 2000 * 12);
        double[] w = p.windTo();
        double worstP = 0, worstV = 0;
        int n = 0;
        for (int i = 0; i < 2000; i++) {
            int o = i * 12;
            double d = a[o + 3], vt = Drops.terminal(d, p.snapshot().airDensity());
            double[] pos = {a[o], a[o + 1], a[o + 2]}, vel = {a[o + 4], a[o + 5], a[o + 6]};
            double wx = w[0] * 0.9, wz = w[1] * 0.9;
            for (int k = 0; k < 10; k++) {
                double dt = 1 / 60.0;
                int sub = (int) Math.max(1, Math.min(8, Math.ceil(dt / (0.25 * vt / Drops.G))));
                double h = dt / sub;
                for (int q = 0; q < sub; q++) {
                    double rx = vel[0] - wx, ry = vel[1], rz = vel[2] - wz, kk = Drops.G * Math.sqrt(rx * rx + ry * ry + rz * rz) / (vt * vt);
                    vel[0] = (vel[0] + h * kk * wx) / (1 + kk * h);
                    vel[1] = (vel[1] - h * Drops.G) / (1 + kk * h);
                    vel[2] = (vel[2] + h * kk * wz) / (1 + kk * h);
                    for (int c = 0; c < 3; c++) pos[c] += vel[c] * h;
                }
            }
            // gewickelt oder neu gesät: nicht vergleichbar
            if (Math.abs(b[o + 3] - d) > 1e-6 || Math.abs(b[o + 1] - pos[1]) > 0.5 * s0.quality().boxY) continue;
            double ex = Math.abs(b[o] - pos[0]), ez = Math.abs(b[o + 2] - pos[2]);
            if (ex > s0.quality().box || ez > s0.quality().box) continue;
            worstP = Math.max(worstP, Math.max(ex, Math.max(Math.abs(b[o + 1] - pos[1]), ez)));
            worstV = Math.max(worstV, Math.max(Math.abs(b[o + 4] - vel[0]), Math.max(Math.abs(b[o + 5] - vel[1]), Math.abs(b[o + 6] - vel[2]))));
            n++;
        }
        out.append(String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Tropfen nach 10 Schritten ± %.2f mm, ± %.1f mm/s (%d Proben)", worstP * 1000, worstV * 1000, n));
        // Wellen: ein Einschlag in der Mitte des Fensters
        int rn = rippleN;
        double size = rn * rippleDx;
        Ripples rp = new Ripples(rn, rippleDx);
        Ripples.Field f = rp.new Field();
        double[] mid = rippleMid.clone();
        double ix = mid[0] + 0.013, iz = mid[1] - 0.021;
        f.impact(ix, iz, 2, Drops.terminal(2));   // das Feld ist ringsum geschlossen wie auf der Grafikkarte (Zelle = floor(x / dx) mod n)
        clearTex(gl, imgState, rn);
        // die Quelle von Hand: ein Einschlag ins Buch, keine zufälligen (Regen 0 in der Momentaufnahme hätte nichts gerechnet)
        int[] head = {1, 0};
        float[] imp = {(float) ix, 0f, (float) iz, 2f, 0f, (float) -Drops.terminal(2), 0f, 0f};
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssboImpacts);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 8, Buffers.newDirectIntBuffer(head));
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 16, 32, Buffers.newDirectFloatBuffer(imp));
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        Precip q = Precip.moderate();
        q.quality = Precip.Quality.LOW;
        q.eyeX = mid[0];
        q.eyeZ = mid[1];
        q.eyeY = 50;
        q.step(1 / 60.0);
        Precip.Snapshot rs = q.snapshot();
        rippleOnly(gl, rs, true);
        f.step(1 / 60.0);
        for (int i = 1; i < 30; i++) { rippleOnly(gl, rs, false); f.step(1 / 60.0); }
        float[] tex = readTexture(gl, texRipple, rn);
        double[][] cpu = f.surface();
        double maxS = 0, err = 0;
        for (int m = 0; m < rn * rn; m++) {
            maxS = Math.max(maxS, Math.abs(cpu[1][m]));
            err = Math.max(err, Math.max(Math.abs(tex[m * 4] - cpu[1][m]), Math.abs(tex[m * 4 + 1] - cpu[2][m])));
        }
        out.append(String.format(Locale.GERMAN, " · Wellen nach 0,5 s: größte Steigung %.4f, Abweichung %.1f %% davon", maxS, err / Math.max(1e-12, maxS) * 100));
        clearTex(gl, imgState, rn);
        // Umgebung: gleichmäßig 1
        Light u = new Light();
        u.skyTop = new double[]{1, 1, 1};
        u.skyHorizon = new double[]{1, 1, 1};
        u.ground = new double[]{1, 1, 1};
        envAge = 1e9;
        Precip e = Precip.moderate();
        e.rate = 0;
        e.step(0);
        update(gl, e.snapshot(), u);
        float[] env = readTexture(gl, texEnv, 64, 32);
        double lo = 9, hi = 0;
        for (int i = 0; i < 64 * 32; i++) { lo = Math.min(lo, env[i * 4 + 1]); hi = Math.max(hi, env[i * 4 + 1]); }
        out.append(String.format(Locale.GERMAN, " · Umgebung gleichmäßig 1: im Tropfen %.3f bis %.3f", lo, hi));
        envAge = 1e9;
        reseed = true;
        snap = null;
        return out.toString();
    }

    /** Nur das Wellenfeld einen Schritt (für die Prüfung): ohne zufällige Einschläge, mit dem Buch, wie es ist. */
    private void rippleOnly(GL4 gl, Precip.Snapshot s, boolean withImpacts) {
        if (!withImpacts) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssboImpacts);
            gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 8, Buffers.newDirectIntBuffer(new int[]{0, 0}));
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        }
        // Wasser überall: Raster auf Wasser setzen
        float[] cells = new float[4];
        cells[2] = -1e9f;
        texGround = upload(gl, texGround, GL4.GL_RGBA32F, 1, 1, GL.GL_RGBA, cells);
        groundVersion = -1;
        Precip.Snapshot z = new Precip.Snapshot(s.time(), 1 / 60.0, s.frame(), s.eyeX(), s.eyeY(), s.eyeZ(), s.quality(), 0, s.dsd(), s.lambda(), s.n0(),
                s.dmin(), s.dmax(), 0, 0, 0, 0, 0, s.airDensity(), s.shutterSim(), s.timeScale(), s.cloudBase(), s.waterLevel(), s.mapX0(), s.mapZ0(), s.mapCell(), 0,
                new float[0], s.mapVersion(), s.seed(), new float[0], new Precip.Grid(s.eyeX() - 50, s.eyeZ() - 50, 100, 1, cells, -7),
                s.throughfall(), s.interception(), s.dripMin(), s.dripMax(), s.aperture(), s.focus());
        ripplesFixed(gl, z);
    }

    private void ripplesFixed(GL4 gl, Precip.Snapshot s) {
        double[] keep = aim;
        aim = null;
        double[] mid = rippleMid.clone();
        ripples(gl, s);
        rippleMid[0] = mid[0];
        rippleMid[1] = mid[1];
        aim = keep;
    }

    private static float[] readBuffer(GL4 gl, int buf, int floats) {
        gl.glMemoryBarrier(GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(floats);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, (long) floats * 4, fb);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        float[] a = new float[floats];
        fb.get(a);
        return a;
    }

    private static float[] readTexture(GL4 gl, int tex, int n) { return readTexture(gl, tex, n, n); }

    private static float[] readTexture(GL4 gl, int tex, int w, int h) {
        gl.glMemoryBarrier(GL4.GL_TEXTURE_UPDATE_BARRIER_BIT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(w * h * 4);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 4);
        gl.glGetTexImage(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        float[] a = new float[w * h * 4];
        fb.get(a);
        return a;
    }

    public void dispose(GL4 gl) {
        for (int p : new int[]{progDrops, progDrop, progLayers, progCurtain, progEnv, progSplash, progSplashDraw, progRSrc, progRPack, progRStep, progROut})
            if (p != 0) gl.glDeleteProgram(p);
        for (int p : fftByN.values()) gl.glDeleteProgram(p);
        fftByN.clear();
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(4, new int[]{ssboDrops, ssboParts, ssboImpacts, ssboRSrc}, 0);
        Gl.deleteTextures(gl, texMap, texGround, texPhase, texEnv, imgSrc, imgState, imgOut, texRipple);
        Gl.deleteTextures(gl, imgSrc2, imgState2, imgOut2, texRipple2);
        if (ssboRSrc2 != 0) gl.glDeleteBuffers(1, new int[]{ssboRSrc2}, 0);
        imgSrc2 = imgState2 = imgOut2 = texRipple2 = ssboRSrc2 = rippleN2 = 0;
        progDrops = progDrop = progLayers = progCurtain = progEnv = progSplash = progSplashDraw = progRSrc = progRPack = progRStep = progROut = vao = 0;
        texMap = texGround = texPhase = texEnv = imgSrc = imgState = imgOut = texRipple = 0;
        dropsCap = partsCap = rippleN = 0;
        mapVersion = groundVersion = -1;
        ready = false;
    }

    // ------------------------------------------------------------------ Shader und Texturen

    private static int tex2d(GL4 gl, int fmt, int w, int h, int filter, int wrap) {
        int[] v = new int[1];
        gl.glGenTextures(1, v, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, v[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, fmt, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, filter);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, filter);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, wrap);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, wrap);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return v[0];
    }

    /** Werte laden; hat die Textur eine andere Größe, wird sie neu angelegt (Filter nächster Nachbar, am Rand fest). Gibt die Textur zurück. */
    private static int upload(GL4 gl, int tex, int fmt, int w, int h, int format, float[] data) {
        int[] cur = new int[2];
        if (tex != 0) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
            gl.glGetTexLevelParameteriv(GL.GL_TEXTURE_2D, 0, GL4.GL_TEXTURE_WIDTH, cur, 0);
            gl.glGetTexLevelParameteriv(GL.GL_TEXTURE_2D, 0, GL4.GL_TEXTURE_HEIGHT, cur, 1);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        }
        if (tex == 0 || cur[0] != w || cur[1] != h) {
            int[] filt = new int[1];
            if (tex != 0) {
                gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
                gl.glGetTexParameteriv(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, filt, 0);
                gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
                Gl.deleteTextures(gl, tex);
            }
            tex = tex2d(gl, fmt, w, h, filt[0] == 0 ? GL.GL_NEAREST : filt[0], GL.GL_CLAMP_TO_EDGE);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, w, h, format, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(data));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return tex;
    }

    static String read(String file) {
        try (InputStream in = PrecipGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("precip: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("precip: " + file + ": " + e.getMessage(), e);
        }
    }

    /** Text eines Shaders mit #version, Defines und dem gemeinsamen Teil statt der #include-Zeile. */
    private static String src(String file, String common) { return src(file, common, null); }

    private static String src(String file, String common, String[] defines) {
        StringBuilder b = new StringBuilder("#version 430 core\n");
        if (defines != null) for (String d : defines) b.append("#define ").append(d).append('\n');
        for (String l : read(file).split("\r?\n", -1)) {
            if (l.trim().startsWith("#include")) { if (common != null) b.append(common).append('\n'); }
            else b.append(l).append('\n');
        }
        return b.toString();
    }

    private static int compute(GL4 gl, String file, String common, String[] defines) {
        int p = gl.glCreateProgram(), s = stage(gl, GL4.GL_COMPUTE_SHADER, src(file, common, defines), file);
        gl.glAttachShader(p, s);
        link(gl, p, file);
        gl.glDeleteShader(s);
        return p;
    }

    /** Übersetzt und bindet zwei Stufen (fertiger Text mit #version); auch für Demos. */
    public static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL4.GL_VERTEX_SHADER, vert, "vert"), fs = stage(gl, GL4.GL_FRAGMENT_SHADER, frag, "frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        link(gl, p, "Programm");
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        return p;
    }

    private static void link(GL4 gl, int p, String name) {
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("precip: " + name + " lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
    }

    static int stage(GL4 gl, int type, String src, String name) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            gl.glDeleteShader(s);
            throw new IllegalStateException("precip: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }

    /** Kleine Helfer. */
    static final class Gl {
        private Gl() {}

        static void deleteTextures(GL4 gl, int... t) {
            int n = 0;
            for (int x : t) if (x != 0) n++;
            if (n == 0) return;
            int[] a = new int[n];
            int i = 0;
            for (int x : t) if (x != 0) a[i++] = x;
            gl.glDeleteTextures(n, a, 0);
        }
    }
}
