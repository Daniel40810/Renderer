package com.dan.fog.gl;

import com.dan.fog.TerrainFog;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Der Nebel über Gelände auf der Grafikkarte (seit Brocken Phase 6). Zwei Schritte je Bild:
 * <ol>
 * <li>{@link #update}: die Karte (Boden, Kaltluft, Gürtel; RGBA32F) neu bauen, wenn {@link TerrainFog#mapVersion},
 * das Rechteck oder die Stufe sich geändert haben. Gebaut wird in einem eigenen Faden; bis sie fertig ist, gilt die
 * alte (oder nur das Nebelmeer).</li>
 * <li>{@link #draw}: ein Bilddurchgang über die fertige Szene. Er braucht eine Kopie der Szenentiefe und schreibt
 * (Streulicht, Durchlässigkeit) mit der Mischung {@code ONE, SRC_ALPHA} ins gebundene Ziel.</li>
 * </ol>
 * Shader: {@code terrain_fog.frag} mit {@code terrain_fog_common.glsl}, Vertex {@code fog.vert}.
 * <p>
 * Seit Brocken Phase 7: der Durchgang läuft nach der Stufe ({@link TerrainFog.Quality#scale}) in halber oder drittel
 * Auflösung in ein eigenes Ziel (Streulicht und Entfernung) und wird kantentreu auf das gebundene Ziel hochgerechnet
 * ({@code terrain_fog_up.frag}); das Licht kann eine Phasenfunktion von außen nehmen ({@link Light#phaseTexture}, etwa
 * die Tröpfchen aus {@code com.dan.sky}), Schattenwerfer ({@link Light#casters}) und die Höhe, ab der die Sonne scheint
 * ({@link Light#sunHeightTexture}).
 */
public final class TerrainFogGl {
    /** Ansicht: inverse Projektion·Blick, Auge, Tiefenkonvention, Bildgröße, Kopie der Szenentiefe, Erdkrümmung. */
    public static final class View {
        public float[] invViewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        public int width = 1, height = 1, depthTexture, frame;
        /** Größte Entfernung des Blicks (m): das Nebelmeer reicht bis zur Kimm. */
        public double maxDistance = 80_000;
        /** Absenkung der Szene je m² waagerechten Abstands (1 / 2 R_eff; 0 = flach). */
        public double curvature;
        /** Seit Brocken Phase 7: Auflösung des Durchgangs (1, 2, 3); 0 = nach der Stufe ({@link TerrainFog.Quality#scale}). */
        public int scale;
    }

    /** Licht, vorbelichtet: Sonne und Mond (Richtung, Farbe), Himmel oben und am Horizont; Sonne unter der Decke 0..1. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] ambientTop = {0.2, 0.3, 0.5}, ambientHorizon = {0.5, 0.6, 0.7};
        public double sunUnderCap = 0.15;
        /**
         * Seit Brocken Phase 7: Phasenfunktion als Textur (u = Streuwinkel / 180°, RGB auf 4π normiert, isotrop = 1),
         * 0 = Henyey-Greenstein mit g = 0,75 wie bisher.
         */
        public int phaseTexture;
        /** Schattenwerfer im Sonnenlicht ({@link TerrainFog#CASTER_STRIDE} Werte je Körper, höchstens 8), null = keine. */
        public double[] casters;
        /** Scheinbarer Halbmesser der Sonne (rad) für den Rand der Schatten. */
        public double sunRadius = Math.toRadians(0.2665);
        /**
         * Karte der Höhe (m, Kanal G), ab der die Sonne über das Gelände scheint, über {x0, z0, Kantenlänge}; 0 = keine.
         * Darunter fehlt das direkte Licht im Nebel: der Schatten eines Bergs auf dem Nebelmeer.
         */
        public int sunHeightTexture;
        public double[] sunHeightRect = {0, 0, 1};
    }

    private int prog, checkProg, upProg, vao, mapTex, mapN;
    private int lowFbo, lowTex, lowDist, lowW, lowH;
    private final ExecutorService builder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Nebelkarte");
        t.setDaemon(true);
        return t;
    });
    private Future<float[]> pending;
    private int pendingN, builtVersion = Integer.MIN_VALUE, builtN;
    private double[] builtRect, pendingRect, mapRect = {0, 0, 1};
    private float[] bounds = {0, 0, 0, 0};
    private boolean ready;
    private String info = "";

    public boolean ready() { return ready; }
    public String info() { return info; }

    public void init(GL4 gl) {
        String common = FogGl.read("terrain_fog_common.glsl"), vert = "#version 430 core\n" + FogGl.read("fog.vert");
        String march = FogGl.strip(FogGl.read("terrain_fog_march.glsl"));
        prog = FogGl.program(gl, vert, "#version 430 core\n" + common + "\n" + march + "\n" + FogGl.strip(FogGl.read("terrain_fog.frag")));
        checkProg = FogGl.program(gl, vert, "#version 430 core\n" + common + "\n" + FogGl.strip(FogGl.read("terrain_fog_check.frag")));
        upProg = FogGl.program(gl, vert, "#version 430 core\n" + common + "\n" + march + "\n" + FogGl.strip(FogGl.read("terrain_fog_up.frag")));
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        ready = true;
        info = "Nebel über Gelände, Karte im Aufbau";
    }

    /** Karte bei Bedarf neu bauen (im Hintergrund) und eine fertige hochladen. */
    public void update(GL4 gl, TerrainFog fog) {
        if (pending != null && pending.isDone()) {
            try {
                float[] m = pending.get();
                int n = pendingN;
                if (mapTex == 0 || mapN != n) {
                    if (mapTex != 0) gl.glDeleteTextures(1, new int[]{mapTex}, 0);
                    int[] t = new int[1];
                    gl.glGenTextures(1, t, 0);
                    mapTex = t[0];
                    gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
                    gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RGBA32F, n, n);
                    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
                    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
                    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
                    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
                    mapN = n;
                }
                gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
                gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RGBA, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(m, 0, n * n * TerrainFog.MAP_STRIDE));
                gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
                int o = n * n * TerrainFog.MAP_STRIDE;
                bounds = new float[]{m[o], m[o + 1], m[o + 2], m[o + 3]};
                mapRect = pendingRect;
                info = String.format(Locale.GERMAN, "Karte %d² über %.1f km, Boden %.0f–%.0f m, Kaltluft bis %.0f m, Gürtel bis %.0f m",
                        n, mapRect[2] / 1000, bounds[0], bounds[1], bounds[2], bounds[3]);
            } catch (Exception e) {
                System.err.println("fog: Nebelkarte: " + e);
            }
            pending = null;
        }
        int n = fog.quality.mapSize;
        double[] rect = fog.mapRect;
        if (pending == null && (fog.mapVersion != builtVersion || rect != builtRect || n != builtN)) {
            builtVersion = fog.mapVersion;
            builtRect = rect;
            builtN = n;
            pendingN = n;
            pendingRect = rect.clone();
            pending = builder.submit(() -> fog.buildMap(n));
        }
    }

    /** Ist eine Karte geladen? */
    public boolean mapReady() { return mapTex != 0; }

    /** Tiefste und höchste Stelle, an der Nebel sein kann (m). */
    public double[] bounds(TerrainFog.Snapshot s) {
        double lo = 1e9, hi = -1e9;
        if (s.sea()) { lo = Math.min(lo, s.seaBase() - 15); hi = Math.max(hi, s.seaTop() + s.seaRough() + 6); }
        if (mapTex != 0) {
            if (s.valley()) { lo = Math.min(lo, bounds[0] - 3); hi = Math.max(hi, bounds[1] + bounds[2] * s.valleyFill() + 4); }
            if (s.cap() && bounds[3] > 0.5) { lo = Math.min(lo, s.capBase() - bounds[3] - 15); hi = Math.max(hi, s.capBase() + 40); }
        }
        return new double[]{lo, hi};
    }

    /** Der Nebel über das gebundene Ziel (Tiefentest aus, Mischung ONE, SRC_ALPHA). */
    public void draw(GL4 gl, TerrainFog.Snapshot s, View view, Light light) {
        if (!ready || !s.any()) return;
        double[] b = bounds(s);
        if (b[1] <= b[0]) return;
        int scale = Math.max(1, Math.min(4, view.scale > 0 ? view.scale : s.quality().scale));
        int[] prevFbo = new int[1], vp = new int[4];
        if (scale > 1) {
            gl.glGetIntegerv(GL4.GL_DRAW_FRAMEBUFFER_BINDING, prevFbo, 0);
            gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
            lowTarget(gl, (view.width + scale - 1) / scale, (view.height + scale - 1) / scale);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, lowFbo);
            gl.glViewport(0, 0, lowW, lowH);
        }
        gl.glUseProgram(prog);
        setAll(gl, prog, s, view, light, b, scale);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glBindVertexArray(vao);
        if (scale > 1) {
            gl.glDisable(GL.GL_BLEND);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            // hochrechnen ins vorher gebundene Ziel
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prevFbo[0]);
            gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
            gl.glUseProgram(upProg);
            setAll(gl, upProg, s, view, light, b, scale);
            gl.glActiveTexture(GL.GL_TEXTURE5);
            gl.glBindTexture(GL.GL_TEXTURE_2D, lowTex);
            gl.glActiveTexture(GL.GL_TEXTURE6);
            gl.glBindTexture(GL.GL_TEXTURE_2D, lowDist);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glUniform1i(gl.glGetUniformLocation(upProg, "uLow"), 5);
            gl.glUniform1i(gl.glGetUniformLocation(upProg, "uLowDist"), 6);
        }
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        for (int u = 6; u >= 1; u--) {
            gl.glActiveTexture(GL.GL_TEXTURE0 + u);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        }
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
    }

    /** Alle Uniforms des Blicks (Nebel, Licht, Ansicht) für den Durchgang und das Hochrechnen; Tiefe auf Einheit 0. */
    private void setAll(GL4 gl, int p, TerrainFog.Snapshot s, View view, Light light, double[] b, int scale) {
        setFog(gl, p, s);
        setLight(gl, p, light);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uInvVP"), 1, false, view.invViewProj, 0);
        set3(gl, p, "uCam", view.eye);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uReversed"), view.reversedZ ? 1 : 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uSteps"), s.quality().steps);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uScale"), scale);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uMaxDist"), (float) view.maxDistance);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uCurv"), (float) view.curvature);
        gl.glUniform2f(gl.glGetUniformLocation(p, "uViewport"), view.width, view.height);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uTfBounds"), (float) b[0], (float) b[1], 0f, 0f);
        set3(gl, p, "uSunDir", light.sunDir);
        set3(gl, p, "uSunCol", light.sunColor);
        set3(gl, p, "uMoonDir", light.moonDir);
        set3(gl, p, "uMoonCol", light.moonColor);
        set3(gl, p, "uAmbTop", light.ambientTop);
        set3(gl, p, "uAmbHor", light.ambientHorizon);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uCapSun"), (float) light.sunUnderCap);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uFrame"), view.frame);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, view.depthTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uDepth"), 0);
    }

    /** Ziel in kleiner Auflösung: Streulicht und Durchlässigkeit (RGBA16F), Entfernung (R32F). */
    private void lowTarget(GL4 gl, int w, int h) {
        if (lowFbo != 0 && w == lowW && h == lowH) return;
        freeLow(gl);
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        lowTex = t[0];
        lowDist = t[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, lowTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        nearest(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, lowDist);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R32F, w, h);
        nearest(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        lowFbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, lowFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, lowTex, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL4.GL_COLOR_ATTACHMENT1, GL.GL_TEXTURE_2D, lowDist, 0);
        gl.glDrawBuffers(2, new int[]{GL.GL_COLOR_ATTACHMENT0, GL4.GL_COLOR_ATTACHMENT1}, 0);
        lowW = w;
        lowH = h;
    }

    private static void nearest(GL4 gl) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    }

    private void freeLow(GL4 gl) {
        if (lowFbo != 0) gl.glDeleteFramebuffers(1, new int[]{lowFbo}, 0);
        if (lowTex != 0) gl.glDeleteTextures(2, new int[]{lowTex, lowDist}, 0);
        lowFbo = lowTex = lowDist = lowW = lowH = 0;
    }

    /** Auflösung des letzten Durchgangs („halbe Auflösung 816×459“) für HUD und Prüfung. */
    public String resolution(TerrainFog.Snapshot s, int w, int h) {
        int sc = Math.max(1, s.quality().scale);
        return sc == 1 ? String.format(Locale.GERMAN, "volle Auflösung %d×%d", w, h)
                : String.format(Locale.GERMAN, "%s Auflösung %d×%d", sc == 2 ? "halbe" : "drittel", (w + sc - 1) / sc, (h + sc - 1) / sc);
    }

    /** Phasenfunktion, Schattenwerfer und Gelände-Schatten (Einheiten 3 und 4). */
    private void setLight(GL4 gl, int p, Light light) {
        gl.glUniform1i(gl.glGetUniformLocation(p, "uPhaseOn"), light.phaseTexture != 0 ? 1 : 0);
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.phaseTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uPhase"), 3);
        gl.glActiveTexture(GL.GL_TEXTURE4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.sunHeightTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uSunH"), 4);
        double[] r = light.sunHeightRect;
        gl.glUniform4f(gl.glGetUniformLocation(p, "uSunHRect"), (float) r[0], (float) r[1], (float) Math.max(1, r[2]), light.sunHeightTexture != 0 ? 1f : 0f);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        setCasters(gl, p, light.casters, light.sunDir, light.sunRadius);
    }

    private static void setCasters(GL4 gl, int p, double[] c, double[] sunDir, double sunRadius) {
        int n = c == null ? 0 : Math.min(TerrainFog.MAX_CASTERS, c.length / TerrainFog.CASTER_STRIDE);
        float[] u = new float[16 * 4];
        for (int i = 0; i < n; i++) {
            int o = i * TerrainFog.CASTER_STRIDE;
            u[i * 8] = (float) c[o]; u[i * 8 + 1] = (float) c[o + 1]; u[i * 8 + 2] = (float) c[o + 2]; u[i * 8 + 3] = (float) c[o + 6];
            u[i * 8 + 4] = (float) c[o + 3]; u[i * 8 + 5] = (float) c[o + 4]; u[i * 8 + 6] = (float) c[o + 5];
        }
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uTfCast[0]"), 16, u, 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uTfCastN"), n);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uTfSun"), (float) sunDir[0], (float) sunDir[1], (float) sunDir[2], (float) sunRadius);
    }

    private void setFog(GL4 gl, int p, TerrainFog.Snapshot s) {
        gl.glUniform4f(gl.glGetUniformLocation(p, "uTfSea"), (float) s.seaBase(), (float) s.seaTop(), (float) s.seaSigma(), (float) s.seaRough());
        gl.glUniform4f(gl.glGetUniformLocation(p, "uTfValley"), (float) s.valleyFill(), (float) s.valleySigma(), 0f, 0f);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uTfCap"), (float) s.capBase(), (float) s.capSigma(), (float) s.capBelt(), 0f);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uTfNoise"), (float) s.bankSize(), (float) s.wisps(), (float) s.offX(), (float) s.offZ());
        gl.glUniform1i(gl.glGetUniformLocation(p, "uTfSeed"), s.seed());
        gl.glUniform4f(gl.glGetUniformLocation(p, "uTfMapRect"), (float) mapRect[0], (float) mapRect[1], (float) Math.max(1, mapRect[2]), (float) mapN);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uTfMap"), 1);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    private static void set3(GL4 gl, int p, String name, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(p, name), (float) v[0], (float) v[1], (float) v[2]);
    }

    /**
     * Rechnet die Dichte an 64 Orten auf der Grafikkarte und vergleicht mit {@link TerrainFog#density(TerrainFog.Snapshot,
     * float[], double, double, double)} mit der Karte gleicher Größe; seit Brocken Phase 7 dazu den Schatten eines
     * Menschen im Sonnenlicht an 64 Orten in und neben seiner Säule gegen {@link TerrainFog#sunShadow}. „Grafikkarte =
     * Prozessor: Nebel über Gelände ± 0,1 % (Median), größte 0,8 %, 64 Orte, 30 im Nebel · Schatten ± 0,000, 21 im Schatten“
     */
    public String selfCheck(GL4 gl, TerrainFog fog) {
        TerrainFog.Snapshot s = fog.snapshot();
        if (!s.any() || mapTex == 0) return "";
        float[] map = fog.buildMap(mapN);
        double[] b = bounds(s);
        float[] pts = new float[64 * 4];
        java.util.Random r = new java.util.Random(64);
        for (int i = 0; i < 64; i++) {
            pts[i * 4] = (float) (mapRect[0] + r.nextDouble() * mapRect[2]);
            pts[i * 4 + 2] = (float) (mapRect[1] + r.nextDouble() * mapRect[2]);
            float[] m = TerrainFog.mapAt(map, mapN, mapRect, pts[i * 4], pts[i * 4 + 2]);
            double lo = Math.max(b[0], m != null ? m[0] - 5 : b[0]);
            pts[i * 4 + 1] = (float) (lo + r.nextDouble() * Math.max(10, b[1] - lo));
        }
        // ein Mensch mitten in der Karte, die Sonne tief im Südosten; Orte bis 90 m hinter ihm, bis 0,7 m neben der Achse
        double cx = mapRect[0] + mapRect[2] / 2, cz = mapRect[1] + mapRect[2] / 2, cy = 1000;
        double[] sun = {0.62, 0.22, 0.75};
        double sl = Math.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]);
        for (int k = 0; k < 3; k++) sun[k] /= sl;
        double[] cast = TerrainFog.person(cx, cy, cz, 1.75);
        double sunR = Math.toRadians(0.2665);
        float[] spts = new float[64 * 4];
        for (int i = 0; i < 64; i++) {
            double dist = 0.5 + 90 * Math.pow(r.nextDouble(), 2), side = (r.nextDouble() * 2 - 1) * 0.7, up = 0.2 + 1.6 * r.nextDouble();
            double ex = -sun[2], ez = sun[0], el = Math.hypot(ex, ez);
            spts[i * 4] = (float) (cx - sun[0] * dist + ex / el * side);
            spts[i * 4 + 1] = (float) (cy + up - sun[1] * dist);
            spts[i * 4 + 2] = (float) (cz - sun[2] * dist + ez / el * side);
        }
        FloatBuffer fb = runCheck(gl, s, pts, null, sun, sunR);
        FloatBuffer sb = runCheck(gl, s, spts, cast, sun, sunR);
        double ref = Math.max(s.seaSigma(), Math.max(s.valley() ? s.valleySigma() : 0, s.capSigma()));
        List<Double> err = new ArrayList<>();
        int in = 0, shaded = 0;
        double shadeErr = 0;
        for (int i = 0; i < 64; i++) {
            double c = TerrainFog.density(s, TerrainFog.mapAt(map, mapN, mapRect, pts[i * 4], pts[i * 4 + 2]), pts[i * 4], pts[i * 4 + 1], pts[i * 4 + 2]);
            double g = fb.get(i * 4) / 1000.0;
            if (c > 0) in++;
            err.add(Math.abs(g - c) / ref);
            double sc = TerrainFog.sunShadow(cast, sun, sunR, spts[i * 4], spts[i * 4 + 1], spts[i * 4 + 2]);
            if (sc < 0.5) shaded++;
            shadeErr = Math.max(shadeErr, Math.abs(sb.get(i * 4 + 3) - sc));
        }
        double[] e = err.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Nebel über Gelände ± %.2f %% (Median), größte %.2f %%, 64 Orte, %d im Nebel · Schatten ± %.3f, %d von 64 im Schatten",
                e[e.length / 2] * 100, e[e.length - 1] * 100, in, shadeErr, shaded);
    }

    /** Ein Durchgang des Selbsttests: 64 Orte, Schattenwerfer (oder keine), Ergebnis RGBA je Ort. */
    private FloatBuffer runCheck(GL4 gl, TerrainFog.Snapshot s, float[] pts, double[] casters, double[] sun, double sunR) {
        int[] v = new int[2];
        gl.glGenTextures(1, v, 0);
        int tex = v[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RGBA32F, 64, 1);
        gl.glGenFramebuffers(1, v, 1);
        int fbo = v[1];
        int[] prev = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        gl.glViewport(0, 0, 64, 1);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(checkProg);
        setFog(gl, checkProg, s);
        setCasters(gl, checkProg, casters, sun, sunR);
        gl.glUniform4fv(gl.glGetUniformLocation(checkProg, "uP[0]"), 64, pts, 0);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(64 * 4);
        gl.glReadPixels(0, 0, 64, 1, GL4.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteTextures(1, new int[]{tex}, 0);
        return fb;
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (checkProg != 0) gl.glDeleteProgram(checkProg);
        if (upProg != 0) gl.glDeleteProgram(upProg);
        freeLow(gl);
        if (mapTex != 0) gl.glDeleteTextures(1, new int[]{mapTex}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        prog = checkProg = upProg = mapTex = vao = mapN = 0;
        ready = false;
        builder.shutdownNow();
    }
}
