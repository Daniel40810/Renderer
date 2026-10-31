package com.dan.fall.gl;

import com.dan.fall.Fall;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ein Wasserfall auf der Grafikkarte (JOGL, GL 4.3 core). Gezeichnet in das gebundene Ziel, HDR linear und
 * vorbelichtet wie das Licht, das hereinkommt, mit Tiefentest ohne Tiefe zu schreiben, vormultipliziertes Alpha:
 * <ol>
 * <li><b>Strähnen</b> ({@code strand.vert}, {@code strand.frag}): ein Band je Strähne von der Kante bis zum Aufprall,
 * oben glatt, zum Zerfall hin streifig, danach ausgeblendet;</li>
 * <li><b>Schleier</b> ({@code veil.vert}, {@code veil.frag}): die Bündel aus {@link Fall#bundles}, jedes auf seiner Bahn in
 * geschlossener Form ({@code fall.glsl}, wie {@link Fall#parcel});</li>
 * <li><b>Tosbecken</b> ({@code pool.*}): Schaumringe am Aufprall;</li>
 * <li><b>Nebel</b> ({@code mist.vert}): Ballen, die vom Fuß aufsteigen und mit dem Wind ziehen.</li>
 * </ol>
 * Dazu für Vorführungen Boden und Wasser ({@link #setGround}, {@link #drawGround}) und der Selbsttest
 * ({@link #selfCheck}): 64 Bahnpunkte auf der Grafikkarte gegen {@link Fall#parcel}.
 * <p>
 * Seit Geiranger Phase 7: ein {@code FallGl} zeichnet beliebig viele Fälle (die Programme gibt es einmal, Puffer und
 * Zeitmessung je Fall), der Lauf über Fels liest seinen Weg aus einer Textur ({@link Fall#PATH} × 16, RGBA32F), Fälle
 * außerhalb des Blickfelds fallen weg, und in der Ferne zeichnet er weniger, dafür größere Bündel ({@link #lodShare}).
 */
public final class FallGl {
    /** Pfad des Bausteins im Klassenpfad (für {@code #include}). */
    public static final String GLSL = "/com/dan/fall/gl/fall.glsl";
    /** Höchstzahl der Strähnen je Fall (Uniform-Felder in {@code fall.glsl}). */
    public static final int MAX_STRANDS = Fall.MAX_STRANDS;
    /** Texture-Einheit für den Weg im Lauf über Fels. */
    public static final int PATH_UNIT = 10;
    /** Bis zu diesem Abstand (m) zeichnet ein Fall alle Bündel; weiter weg weniger ({@link #lodShare}). */
    public double lodNear = 350;
    /** Kleinster Anteil der Bündel in der Ferne. */
    public double lodMin = 0.12;
    /**
     * Nah am Auge (seit Geiranger Phase 8): ein Bündel des Schleiers wird höchstens so groß gezeichnet, dass es diesen
     * Winkel (rad) deckt, ein Nebelballen höchstens {@link #nearCapMist}; unter {@link #nearFade} m blenden beide aus.
     * Ohne die Grenze deckte jeder Fleck eines Falls, an dem das Auge steht, das ganze Bild (Storfossen von der Treppe:
     * 18,9 ms). 0 schaltet die Grenze ab.
     */
    public double nearCap = 0.05, nearCapMist = 0.3, nearFade = 6;

    /** Blick: Matrix, Auge, umgekehrte Tiefe. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = {0, 0, 0};
        public boolean reversedZ;
    }

    /** Licht, vorbelichtet: Sonne (Richtung, Bestrahlung), Himmel (Bestrahlung/π), Dunst; Ausgabe linear oder fertig. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {2.6, 2.5, 2.3}, ambient = {0.25, 0.3, 0.38}, fogColor = {0.6, 0.7, 0.8};
        public double fogDistance = 40_000;
        /** Für Vorführungen ohne eigene Nachbearbeitung: Belichtung, Filmkurve und Gamma gleich hier (0 = linear, HDR). */
        public double display;
    }

    private int progStrand, progVeil, progMist, progPool, progGround, progCheck, emptyVao;
    private int groundVao, groundVbo, groundIbo, groundCount, waterVao, waterVbo, waterIbo;
    private boolean ready;

    /** Puffer, Weg und Zeitmessung eines Falls. */
    private static final class Slot {
        int strandVao, strandVbo, strandIbo, strandCount, veilVao, veilVbo, veilCount, mistVao, mistVbo, mistCount, pathTex;
        long stamp = -1;
        Fall.Quality builtFor;
        final int[] query = new int[2];
        boolean queried, drawn;
        double gpuMs, share = 1;
        /** Hülle (x, y, z min und max) für den Blicktest. */
        final double[] box = new double[6];
    }

    private final java.util.IdentityHashMap<Fall, Slot> slots = new java.util.IdentityHashMap<>();
    private Slot last;

    public boolean ready() { return ready; }

    /** Uniform-Orte je Programm, einmal gefragt (viele Fälle je Bild, zwei Durchgänge). */
    private final java.util.Map<Integer, java.util.Map<String, Integer>> locs = new java.util.HashMap<>();

    private int loc(GL4 gl, int prog, String name) {
        return locs.computeIfAbsent(prog, k -> new java.util.HashMap<>()).computeIfAbsent(name, k -> gl.glGetUniformLocation(prog, k));
    }

    /** Zeit des zuletzt gezeichneten Falls auf der Grafikkarte (ms, geglättet). */
    public double gpuMs() { return last == null ? 0 : last.gpuMs; }

    /** Zeit eines Falls auf der Grafikkarte (ms, geglättet; 0, wenn er nicht gezeichnet wurde). */
    public double gpuMs(Fall f) { Slot s = slots.get(f); return s == null || !s.drawn ? 0 : s.gpuMs; }

    /** Anteil der Bündel, mit dem der Fall zuletzt gezeichnet wurde (1 nah, kleiner in der Ferne, 0 außerhalb des Blicks). */
    public double share(Fall f) { Slot s = slots.get(f); return s == null || !s.drawn ? 0 : s.share; }

    public void init(GL4 gl) {
        progStrand = program(gl, "Strähnen", "strand.vert", "strand.frag");
        progVeil = program(gl, "Schleier", "veil.vert", "veil.frag");
        progMist = program(gl, "Nebel", "mist.vert", "veil.frag");
        progPool = program(gl, "Tosbecken", "pool.vert", "pool.frag");
        progGround = program(gl, "Boden", "ground.vert", "ground.frag");
        progCheck = program(gl, "Fall-Prüfung", "check.vert", "check.frag");
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        emptyVao = t[0];
        ready = true;
    }

    // ------------------------------------------------------------------ Daten

    /** Ecken der Strähnen: je Ecke (Strähne, Lage 0..1, Seite −1/1); Abschnitte nach Stufe. */
    public static float[] strandVertices(Fall f) {
        int n = f.strands.size(), seg = f.quality.segments;
        float[] v = new float[n * (seg + 1) * 2 * 3];
        int k = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j <= seg; j++)
                for (int s = -1; s <= 1; s += 2) { v[k++] = i; v[k++] = j / (float) seg; v[k++] = s; }
        return v;
    }

    /** Dreiecke der Strähnen (zwei je Abschnitt). */
    public static int[] strandIndices(Fall f) {
        int n = f.strands.size(), seg = f.quality.segments;
        int[] idx = new int[n * seg * 6];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int b = i * (seg + 1) * 2;
            for (int j = 0; j < seg; j++) {
                int a = b + j * 2;
                idx[k++] = a; idx[k++] = a + 1; idx[k++] = a + 2;
                idx[k++] = a + 1; idx[k++] = a + 3; idx[k++] = a + 2;
            }
        }
        return idx;
    }

    /** Bündel: je Bündel acht Werte (Strähne, Phase, seitlich, vorwärts, Endgeschwindigkeit, Größe, Lebensdauer, Zufall). */
    public static float[] bundleData(Fall f) {
        float[] v = new float[f.bundles.size() * 8];
        int k = 0;
        for (Fall.Bundle b : f.bundles) {
            v[k++] = b.strand; v[k++] = (float) b.phase; v[k++] = (float) b.lateral; v[k++] = (float) b.forward;
            v[k++] = (float) b.vt; v[k++] = (float) b.size; v[k++] = (float) b.life; v[k++] = (float) b.rnd;
        }
        return v;
    }

    /** Nebelballen: je Ballen (Strähne, Phase, Winkel, Zufall), gleicher Seed gleiches Bild. */
    public static float[] mistData(Fall f) {
        int n = f.quality.mist, ns = Math.max(1, f.strands.size());
        java.util.SplittableRandom r = new java.util.SplittableRandom(f.seed * 31 + 7);
        float[] v = new float[n * 4];
        for (int i = 0; i < n; i++) {
            v[i * 4] = i % ns;
            v[i * 4 + 1] = (float) r.nextDouble();
            v[i * 4 + 2] = (float) (r.nextDouble() * Math.PI * 2);
            v[i * 4 + 3] = (float) r.nextDouble();
        }
        return v;
    }

    /** Weg im Lauf über Fels als Textur: {@link Fall#PATH} × 16 Texel (x, y, z, Geschwindigkeit), Zeile = Strähne; ohne Weg leer. */
    public static float[] pathData(Fall f) {
        float[] v = new float[Fall.PATH * MAX_STRANDS * 4];
        for (int i = 0; i < Math.min(MAX_STRANDS, f.strands.size()); i++) {
            float[] p = f.strands.get(i).path;
            if (p != null) System.arraycopy(p, 0, v, i * Fall.PATH * 4, Fall.PATH * 4);
        }
        return v;
    }

    /** Ob der Fall seinen Weg aus der Tabelle liest (Lauf über Fels). */
    public static boolean course(Fall f) { return !f.strands.isEmpty() && f.strands.get(0).path != null; }

    /** Uniforms von {@code fall.glsl} als Zahlen: je Strähne A, B, C, I (je vier Werte), dann Richtung, Wind, Kante, g, Strahl. */
    public static float[][] strandUniforms(Fall f) {
        int n = Math.min(MAX_STRANDS, f.strands.size());
        float[] a = new float[MAX_STRANDS * 4], b = new float[MAX_STRANDS * 4], c = new float[MAX_STRANDS * 4], im = new float[MAX_STRANDS * 4];
        for (int i = 0; i < n; i++) {
            Fall.Strand s = f.strands.get(i);
            a[i * 4] = (float) s.x; a[i * 4 + 1] = (float) s.y; a[i * 4 + 2] = (float) s.z; a[i * 4 + 3] = (float) s.v0;
            b[i * 4] = (float) s.tBreak; b[i * 4 + 1] = (float) s.vtDrop; b[i * 4 + 2] = (float) s.tImpact; b[i * 4 + 3] = (float) s.width;
            c[i * 4] = (float) (s.seed / 1048576.0); c[i * 4 + 1] = (float) s.u; c[i * 4 + 2] = (float) s.breakup; c[i * 4 + 3] = s.onWater ? 1 : 0;
            im[i * 4] = (float) s.ix; im[i * 4 + 1] = (float) s.iy; im[i * 4 + 2] = (float) s.iz; im[i * 4 + 3] = (float) s.impactSpeed;
        }
        double[] d = f.dir(), w = f.wind(), al = f.along();
        float[] g = {(float) d[0], (float) d[1], (float) d[2], (float) w[0], (float) w[1], (float) w[2], (float) al[0], (float) al[1], (float) al[2],
                (float) f.gravity, (float) f.coreTerminal, n, (float) f.ice()};
        return new float[][]{a, b, c, im, g};
    }

    private void bindFall(GL4 gl, int prog, Fall f, Slot sl) {
        gl.glUniform1i(loc(gl, prog, "uCourse"), course(f) ? 1 : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0 + PATH_UNIT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, sl == null ? 0 : sl.pathTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, prog, "uFallPath"), PATH_UNIT);
        float[][] u = strandUniforms(f);
        gl.glUniform4fv(loc(gl, prog, "uStrandA"), MAX_STRANDS, u[0], 0);
        gl.glUniform4fv(loc(gl, prog, "uStrandB"), MAX_STRANDS, u[1], 0);
        gl.glUniform4fv(loc(gl, prog, "uStrandC"), MAX_STRANDS, u[2], 0);
        gl.glUniform4fv(loc(gl, prog, "uStrandI"), MAX_STRANDS, u[3], 0);
        float[] g = u[4];
        gl.glUniform3f(loc(gl, prog, "uFallDir"), g[0], g[1], g[2]);
        gl.glUniform3f(loc(gl, prog, "uFallWind"), g[3], g[4], g[5]);
        gl.glUniform3f(loc(gl, prog, "uFallAlong"), g[6], g[7], g[8]);
        gl.glUniform2f(loc(gl, prog, "uFallPhys"), g[9], g[10]);
        gl.glUniform1i(loc(gl, prog, "uStrands"), (int) g[11]);
        gl.glUniform1f(loc(gl, prog, "uIce"), g[12]);
    }

    private void bindLight(GL4 gl, int prog, View v, Light l) {
        gl.glUniformMatrix4fv(loc(gl, prog, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, prog, "uEye"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        set3(gl, prog, "uSunDir", l.sunDir);
        set3(gl, prog, "uSunColor", l.sunColor);
        set3(gl, prog, "uAmbient", l.ambient);
        set3(gl, prog, "uFogColor", l.fogColor);
        gl.glUniform1f(loc(gl, prog, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, prog, "uDisplay"), (float) l.display);
    }

    private void set3(GL4 gl, int prog, String u, double[] v) {
        gl.glUniform3f(loc(gl, prog, u), (float) v[0], (float) v[1], (float) v[2]);
    }

    /** Lädt Strähnen, Bündel, Nebel und Weg neu, wenn {@link Fall#update()} seit dem letzten Mal lief. */
    public void upload(GL4 gl, Fall f) { slot(gl, f); }

    private Slot slot(GL4 gl, Fall f) {
        if (f.strands.isEmpty()) f.update();
        Slot sl = slots.get(f);
        if (sl == null) {
            sl = new Slot();
            gl.glGenQueries(2, sl.query, 0);
            slots.put(f, sl);
        }
        if (f.stamp() == sl.stamp && f.quality == sl.builtFor) return sl;
        int[] s = mesh(gl, sl.strandVao, sl.strandVbo, sl.strandIbo, strandVertices(f), 3, strandIndices(f));
        sl.strandVao = s[0]; sl.strandVbo = s[1]; sl.strandIbo = s[2]; sl.strandCount = s[3];
        int[] b = instances(gl, sl.veilVao, sl.veilVbo, bundleData(f), 2);
        sl.veilVao = b[0]; sl.veilVbo = b[1]; sl.veilCount = f.bundles.size();
        int[] m = instances(gl, sl.mistVao, sl.mistVbo, mistData(f), 1);
        sl.mistVao = m[0]; sl.mistVbo = m[1]; sl.mistCount = f.quality.mist;
        if (sl.pathTex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            sl.pathTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, sl.pathTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, Fall.PATH, MAX_STRANDS);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, sl.pathTex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, Fall.PATH, MAX_STRANDS, GL.GL_RGBA, GL.GL_FLOAT, FloatBuffer.wrap(pathData(f)));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        box(f, sl.box);
        sl.stamp = f.stamp();
        sl.builtFor = f.quality;
        return sl;
    }

    /** Hülle des Falls: Kante, Wege und Aufprall, dazu 60 m Rand für Schleier und Nebel. */
    static void box(Fall f, double[] b) {
        b[0] = b[1] = b[2] = Double.MAX_VALUE;
        b[3] = b[4] = b[5] = -Double.MAX_VALUE;
        for (Fall.Strand s : f.strands) {
            grow(b, s.x, s.y, s.z);
            grow(b, s.ix, s.iy, s.iz);
            if (s.path != null) for (int k = 0; k < Fall.PATH; k += 8) grow(b, s.path[k * 4], s.path[k * 4 + 1], s.path[k * 4 + 2]);
        }
        for (int i = 0; i < 3; i++) { b[i] -= 60; b[i + 3] += 60; }
    }

    private static void grow(double[] b, double x, double y, double z) {
        b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.min(b[2], z);
        b[3] = Math.max(b[3], x); b[4] = Math.max(b[4], y); b[5] = Math.max(b[5], z);
    }

    /** Ob die Hülle ganz außerhalb des Blickfelds liegt (alle acht Ecken hinter einer Ebene des Bildraums). */
    static boolean outside(float[] vp, double[] b) {
        int[] out = new int[6];
        for (int c = 0; c < 8; c++) {
            double x = (c & 1) == 0 ? b[0] : b[3], y = (c & 2) == 0 ? b[1] : b[4], z = (c & 4) == 0 ? b[2] : b[5];
            double cx = vp[0] * x + vp[4] * y + vp[8] * z + vp[12], cy = vp[1] * x + vp[5] * y + vp[9] * z + vp[13];
            double cw = vp[3] * x + vp[7] * y + vp[11] * z + vp[15];
            if (cx < -cw) out[0]++;
            if (cx > cw) out[1]++;
            if (cy < -cw) out[2]++;
            if (cy > cw) out[3]++;
            if (cw < 0) out[4]++;
        }
        for (int i = 0; i < 5; i++) if (out[i] == 8) return true;
        return false;
    }

    /** Anteil der Bündel nach dem Abstand des Auges zur Hülle: bis {@link #lodNear} alle, dann wie lodNear/Abstand, nie unter {@link #lodMin}. */
    public double lodShare(Fall f, double[] eye) {
        double[] b = new double[6];
        Slot sl = slots.get(f);
        if (sl != null && sl.stamp == f.stamp()) System.arraycopy(sl.box, 0, b, 0, 6); else box(f, b);
        double dx = Math.max(0, Math.max(b[0] - eye[0], eye[0] - b[3])), dy = Math.max(0, Math.max(b[1] - eye[1], eye[1] - b[4])),
                dz = Math.max(0, Math.max(b[2] - eye[2], eye[2] - b[5]));
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.max(lodMin, Math.min(1, lodNear / Math.max(1, d)));
    }

    /**
     * Zeichnet den Fall in das gebundene Ziel zur Zeit {@code time} (s): Tosbecken, Strähnen, Nebel, Schleier. Misst die
     * Zeit auf der Grafikkarte ({@link #gpuMs()}).
     */
    public void draw(GL4 gl, Fall f, View v, Light l, double time) {
        if (!ready) return;
        Slot sl = slot(gl, f);
        last = sl;
        if (sl.queried) {
            int[] av = new int[1];
            gl.glGetQueryObjectiv(sl.query[1], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
            if (av[0] != 0) {
                long[] t = new long[2];
                gl.glGetQueryObjecti64v(sl.query[0], GL4.GL_QUERY_RESULT, t, 0);
                gl.glGetQueryObjecti64v(sl.query[1], GL4.GL_QUERY_RESULT, t, 1);
                sl.gpuMs = sl.gpuMs * 0.9 + (t[1] - t[0]) / 1e6 * 0.1;
                sl.queried = false;
            }
        }
        sl.drawn = f.discharge > 0 && !outside(v.viewProj, sl.box);
        if (!sl.drawn) return;
        sl.share = lodShare(f, v.eye);
        float gain = (float) (1 / Math.sqrt(sl.share));
        boolean measure = !sl.queried;
        if (measure) gl.glQueryCounter(sl.query[0], GL4.GL_TIMESTAMP);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        float tm = (float) (time % 3600.0);
        // Tosbecken
        gl.glUseProgram(progPool);
        bindFall(gl, progPool, f, sl);
        bindLight(gl, progPool, v, l);
        gl.glUniform1f(loc(gl, progPool, "uTime"), tm);
        gl.glUniform1f(loc(gl, progPool, "uFoam"), (float) Math.min(1, 0.4 + f.mist()));
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, Math.min(MAX_STRANDS, f.strands.size()));
        // Strähnen
        gl.glUseProgram(progStrand);
        bindFall(gl, progStrand, f, sl);
        bindLight(gl, progStrand, v, l);
        gl.glUniform1f(loc(gl, progStrand, "uTime"), tm);
        gl.glBindVertexArray(sl.strandVao);
        gl.glDrawElements(GL.GL_TRIANGLES, sl.strandCount, GL.GL_UNSIGNED_INT, 0);
        // Nebel
        gl.glUseProgram(progMist);
        bindFall(gl, progMist, f, sl);
        bindLight(gl, progMist, v, l);
        gl.glUniform1f(loc(gl, progMist, "uTime"), tm);
        gl.glUniform1f(loc(gl, progMist, "uMist"), (float) f.mist());
        gl.glUniform1f(loc(gl, progMist, "uSizeGain"), gain);
        gl.glUniform2f(loc(gl, progMist, "uNear"), (float) nearCapMist, (float) nearFade);
        gl.glBindVertexArray(sl.mistVao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, Math.max(4, (int) Math.round(sl.mistCount * sl.share)));
        // Schleier
        gl.glUseProgram(progVeil);
        bindFall(gl, progVeil, f, sl);
        bindLight(gl, progVeil, v, l);
        gl.glUniform1f(loc(gl, progVeil, "uTime"), tm);
        gl.glUniform1f(loc(gl, progVeil, "uAlpha"), (float) veilAlpha(f));
        gl.glUniform1f(loc(gl, progVeil, "uSizeGain"), gain);
        gl.glUniform2f(loc(gl, progVeil, "uNear"), (float) nearCap, (float) nearFade);
        gl.glBindVertexArray(sl.veilVao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, Math.max(16, (int) Math.round(sl.veilCount * sl.share)));
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        if (measure) {
            gl.glQueryCounter(sl.query[1], GL4.GL_TIMESTAMP);
            sl.queried = true;
        }
    }

    /** Deckung je Bündel: mehr Abfluss, dichterer Schleier; weniger Bündel (Niedrig), jedes etwas dichter. */
    public static double veilAlpha(Fall f) {
        double perBundle = 4000.0 / Math.max(1, f.bundles.size());
        return Math.min(1, (0.35 + 0.65 * Math.sqrt(Math.min(4, f.discharge * (1 - 0.9 * f.ice()) / 2))) * Math.sqrt(perBundle));
    }

    // ------------------------------------------------------------------ Boden der Vorführung

    /** Boden und Wand als Netz: n × n Ecken über dem Quadrat (x0, z0, Kante), Normalen aus dem Feld; Wasser als Fläche. */
    public void setGround(GL4 gl, Fall.Ground h, double x0, double z0, double size, int n, double waterLevel) {
        float[] v = new float[n * n * 6];
        double cell = size / (n - 1);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = x0 + i * cell, z = z0 + j * cell, y = h.height(x, z);
                double gx = (h.height(x + cell, z) - h.height(x - cell, z)) / (2 * cell), gz = (h.height(x, z + cell) - h.height(x, z - cell)) / (2 * cell);
                double l = Math.sqrt(gx * gx + 1 + gz * gz);
                int k = (j * n + i) * 6;
                v[k] = (float) x; v[k + 1] = (float) y; v[k + 2] = (float) z;
                v[k + 3] = (float) (-gx / l); v[k + 4] = (float) (1 / l); v[k + 5] = (float) (-gz / l);
            }
        int[] idx = new int[(n - 1) * (n - 1) * 6];
        int t = 0;
        for (int j = 0; j < n - 1; j++)
            for (int i = 0; i < n - 1; i++) {
                int a = j * n + i, b = a + 1, c = a + n, d = c + 1;
                idx[t++] = a; idx[t++] = c; idx[t++] = d;
                idx[t++] = a; idx[t++] = d; idx[t++] = b;
            }
        int[] g = mesh(gl, groundVao, groundVbo, groundIbo, v, 6, idx);
        groundVao = g[0]; groundVbo = g[1]; groundIbo = g[2]; groundCount = g[3];
        float wl = (float) waterLevel, a = (float) x0, b = (float) z0, s = (float) size;
        float[] w = {a, wl, b, 0, 1, 0, a + s, wl, b, 0, 1, 0, a, wl, b + s, 0, 1, 0, a + s, wl, b + s, 0, 1, 0};
        int[] wv = mesh(gl, waterVao, waterVbo, waterIbo, w, 6, new int[]{0, 2, 3, 0, 3, 1});
        waterVao = wv[0]; waterVbo = wv[1]; waterIbo = wv[2];
    }

    /** Boden und Wasser zeichnen (schreibt Tiefe; Himmelsfarbe für die Spiegelung des Wassers). */
    public void drawGround(GL4 gl, View v, Light l, double[] sky) {
        if (groundVao == 0) return;
        gl.glUseProgram(progGround);
        bindLight(gl, progGround, v, l);
        set3(gl, progGround, "uSky", sky);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glUniform1f(loc(gl, progGround, "uWater"), 0);
        gl.glBindVertexArray(groundVao);
        gl.glDrawElements(GL.GL_TRIANGLES, groundCount, GL.GL_UNSIGNED_INT, 0);
        gl.glUniform1f(loc(gl, progGround, "uWater"), 1);
        gl.glBindVertexArray(waterVao);
        gl.glDrawElements(GL.GL_TRIANGLES, 6, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    // ------------------------------------------------------------------ Selbsttest

    /** 64 Proben (Strähne, Flugzeit, Endgeschwindigkeit, seitlich, vorwärts, frei) über alle Strähnen, gleicher Seed. */
    public static float[] checkPoints(Fall f) {
        if (f.strands.isEmpty()) f.update();
        java.util.SplittableRandom r = new java.util.SplittableRandom(f.seed + 99);
        float[] p = new float[64 * 6];
        for (int i = 0; i < 64; i++) {
            int s = i % f.strands.size();
            Fall.Strand st = f.strands.get(s);
            p[i * 6] = s;
            p[i * 6 + 1] = (float) (r.nextDouble() * st.tImpact);
            p[i * 6 + 2] = (float) (st.vtDrop * (0.7 + 0.6 * r.nextDouble()));
            p[i * 6 + 3] = (float) ((r.nextDouble() - 0.5) * 2 * f.dropSpread);
            p[i * 6 + 4] = (float) ((r.nextDouble() - 0.5) * f.dropSpread);
        }
        return p;
    }

    /** Vergleich Grafikkarte gegen Prozessor: größte Abweichung des Orts (m) und der Geschwindigkeit (m/s). */
    public static String compare(Fall f, float[] pts, float[] gpu) {
        double dp = 0, dv = 0;
        for (int i = 0; i < pts.length / 6; i++) {
            double[] c = f.parcel(f.strands.get((int) pts[i * 6]), pts[i * 6 + 1], pts[i * 6 + 2], pts[i * 6 + 3], pts[i * 6 + 4]);
            dp = Math.max(dp, Math.sqrt(sq(c[0] - gpu[i * 4]) + sq(c[1] - gpu[i * 4 + 1]) + sq(c[2] - gpu[i * 4 + 2])));
            dv = Math.max(dv, Math.abs(c[3] - gpu[i * 4 + 3]));
        }
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Bahn ± %.0f mm, Geschwindigkeit ± %.2f m/s", dp * 1000, dv);
    }

    static double sq(double x) { return x * x; }

    /** 64 Bahnpunkte auf der Grafikkarte gegen {@link Fall#parcel}; stellt Ziel und Ausschnitt wieder her. */
    public String selfCheck(GL4 gl, Fall f) {
        slot(gl, f);
        float[] pts = checkPoints(f);
        int n = 64;
        int[] save = new int[4], fbo = new int[1], tex = new int[1], prevFbo = new int[1];
        gl.glGetIntegerv(GL.GL_VIEWPORT, save, 0);
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
        gl.glGenTextures(1, tex, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, n, 1);
        gl.glGenFramebuffers(1, fbo, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex[0], 0);
        gl.glViewport(0, 0, n, 1);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(progCheck);
        bindFall(gl, progCheck, f, slots.get(f));
        gl.glUniform1f(loc(gl, progCheck, "uCount"), n);
        int[] t = new int[1], b = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        gl.glGenBuffers(1, b, 0);
        gl.glBindVertexArray(t[0]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b[0]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, pts.length * 4L, FloatBuffer.wrap(pts), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 4, GL.GL_FLOAT, false, 24, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 2, GL.GL_FLOAT, false, 24, 16);
        gl.glDrawArrays(GL.GL_POINTS, 0, n);
        FloatBuffer out = FloatBuffer.allocate(n * 4);
        gl.glReadPixels(0, 0, n, 1, GL.GL_RGBA, GL.GL_FLOAT, out);
        gl.glBindVertexArray(0);
        gl.glDeleteVertexArrays(1, t, 0);
        gl.glDeleteBuffers(1, b, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prevFbo[0]);
        gl.glDeleteFramebuffers(1, fbo, 0);
        gl.glDeleteTextures(1, tex, 0);
        gl.glViewport(save[0], save[1], save[2], save[3]);
        gl.glUseProgram(0);
        return compare(f, pts, out.array());
    }

    // ------------------------------------------------------------------ Puffer

    private static int[] mesh(GL4 gl, int va, int vb, int ib, float[] v, int stride, int[] idx) {
        if (va == 0) {
            int[] t = new int[1];
            gl.glGenVertexArrays(1, t, 0);
            va = t[0];
            int[] b = new int[2];
            gl.glGenBuffers(2, b, 0);
            vb = b[0];
            ib = b[1];
        }
        gl.glBindVertexArray(va);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vb);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, FloatBuffer.wrap(v), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ib);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.length * 4L, IntBuffer.wrap(idx), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride * 4, 0);
        if (stride >= 6) {
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, stride * 4, 12);
        }
        gl.glBindVertexArray(0);
        return new int[]{va, vb, ib, idx.length};
    }

    /** Je Instanz vier (vec4 × k) Werte, Teiler 1. */
    private static int[] instances(GL4 gl, int va, int vb, float[] v, int vec4s) {
        if (va == 0) {
            int[] t = new int[1];
            gl.glGenVertexArrays(1, t, 0);
            va = t[0];
            gl.glGenBuffers(1, t, 0);
            vb = t[0];
        }
        gl.glBindVertexArray(va);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vb);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4, v.length) * 4L, v.length == 0 ? null : FloatBuffer.wrap(v), GL.GL_STATIC_DRAW);
        for (int k = 0; k < vec4s; k++) {
            gl.glEnableVertexAttribArray(k);
            gl.glVertexAttribPointer(k, 4, GL.GL_FLOAT, false, vec4s * 16, k * 16L);
            gl.glVertexAttribDivisor(k, 1);
        }
        gl.glBindVertexArray(0);
        return new int[]{va, vb};
    }

    /** „7 Strähnen, 4000 Bündel, 48 Nebelballen · 0,62 ms“ */
    public String describe(Fall f) {
        Slot sl = slots.get(f);
        return String.format(Locale.GERMAN, "%d Strähnen, %d Bündel, %d Nebelballen · %.2f ms", f.strands.size(), f.bundles.size(), f.quality.mist, sl == null ? 0 : sl.gpuMs);
    }

    /** Puffer eines Falls freigeben (wenn die Szene ihn ersetzt). */
    public void forget(GL4 gl, Fall f) {
        Slot sl = slots.remove(f);
        if (sl != null) free(gl, sl);
        if (last == sl) last = null;
    }

    private static void free(GL4 gl, Slot sl) {
        for (int a : new int[]{sl.strandVao, sl.veilVao, sl.mistVao}) if (a != 0) gl.glDeleteVertexArrays(1, new int[]{a}, 0);
        for (int b : new int[]{sl.strandVbo, sl.strandIbo, sl.veilVbo, sl.mistVbo}) if (b != 0) gl.glDeleteBuffers(1, new int[]{b}, 0);
        if (sl.pathTex != 0) gl.glDeleteTextures(1, new int[]{sl.pathTex}, 0);
        gl.glDeleteQueries(2, sl.query, 0);
    }

    public void dispose(GL4 gl) {
        for (Slot sl : slots.values()) free(gl, sl);
        slots.clear();
        last = null;
        for (int p : new int[]{progStrand, progVeil, progMist, progPool, progGround, progCheck}) if (p != 0) gl.glDeleteProgram(p);
        for (int a : new int[]{emptyVao, groundVao, waterVao}) if (a != 0) gl.glDeleteVertexArrays(1, new int[]{a}, 0);
        for (int b : new int[]{groundVbo, groundIbo, waterVbo, waterIbo}) if (b != 0) gl.glDeleteBuffers(1, new int[]{b}, 0);
        locs.clear();
        progStrand = progVeil = progMist = progPool = progGround = progCheck = 0;
        emptyVao = groundVao = waterVao = 0;
        ready = false;
    }

    // ------------------------------------------------------------------ Shader laden

    /** Quelltext mit eingesetzten Bausteinen ({@code #include "datei"} aus diesem Package). */
    public static String source(String file) {
        StringBuilder sb = new StringBuilder("#version 430 core\n");
        expand(file, sb, new HashSet<>());
        return sb.toString();
    }

    private static void expand(String file, StringBuilder sb, Set<String> seen) {
        if (!seen.add(file)) return;
        for (String l : read(file).split("\r?\n", -1)) {
            String t = l.trim();
            if (t.startsWith("#include")) expand(t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')), sb, seen);
            else sb.append(l).append('\n');
        }
    }

    static String read(String file) {
        try (InputStream in = FallGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("fall: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("fall: " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String name, String vert, String frag) {
        int prog = gl.glCreateProgram();
        int[] sh = new int[2];
        String[] f = {vert, frag};
        for (int i = 0; i < 2; i++) {
            sh[i] = gl.glCreateShader(i == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh[i], 1, new String[]{source(f[i])}, null, 0);
            gl.glCompileShader(sh[i]);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh[i], GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                int[] len = new int[1];
                gl.glGetShaderiv(sh[i], GL4.GL_INFO_LOG_LENGTH, len, 0);
                byte[] log = new byte[Math.max(1, len[0])];
                gl.glGetShaderInfoLog(sh[i], log.length, null, 0, log, 0);
                throw new IllegalStateException("fall: " + f[i] + " (" + name + ") lässt sich nicht übersetzen:\n" + new String(log, StandardCharsets.UTF_8));
            }
            gl.glAttachShader(prog, sh[i]);
        }
        gl.glLinkProgram(prog);
        int[] ok = new int[1];
        gl.glGetProgramiv(prog, GL4.GL_LINK_STATUS, ok, 0);
        for (int s : sh) gl.glDeleteShader(s);
        if (ok[0] == 0) throw new IllegalStateException("fall: " + name + " lässt sich nicht binden");
        return prog;
    }
}
