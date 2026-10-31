package com.dan.firework.gl;

import com.dan.firework.Firework;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Darstellung von {@code firework} mit JOGL (Paketvertrag Punkt 3), seit Timmendorf Phase 11 echt:
 * <ul>
 * <li><b>Sterne</b> als Bildtafeln zum Auge hin ({@code firework.vert}/{@code .frag}, Modus 0): ein Gauß, dessen
 * Fläche genau die Beleuchtungsstärke I/d² trägt – ein Stern bleibt gleich hell, ob er einen oder zwanzig Bildpunkte
 * groß ist; Farbe aus dem Spektrum ({@code Spectrum}), additiv in den HDR-Puffer.</li>
 * <li><b>Funkenschweife</b> und Spuren der Langzeitbelichtung als Linien ({@code firework_trail.*}), die Leuchtdichte
 * aus Lichtstärke je Länge.</li>
 * <li><b>Rauch</b> als weiche Scheiben (Modus 1), auf der CPU von allen Lichtern der Bomben beleuchtet
 * ({@link Firework#lights()}, Albedo {@link #smokeAlbedo}), hinten nach vorn sortiert, vormultipliziert.</li>
 * <li><b>Spiegelung</b> im Wasser: {@link #drawReflection} zeichnet alles noch einmal an {@link #waterY} gespiegelt
 * und um {@link #reflect} geschwächt, unter dem Wasser abgeschnitten (Schnittebene 0); seit Timmendorf Phase 12 mit der
 * Tiefe der Szene nur auf dem Wasser ({@link View#depthTexture}).</li>
 * </ul>
 * Ein Licht je Bombe für Wolken, Wasser und Gelände liefert {@link Firework#lights()} – keine Tausende Lichtquellen
 * (Werkbuch, Risiko „Feuerwerk“). Aufruf nach allem Undurchsichtigen, mit Tiefentest, ohne Tiefe zu schreiben.
 */
public final class FireworkGl {
    /** Ansicht: Projektion·Blick, Auge, Bildpunkte je Bogenmaß, Vorbelichtung (Leuchtdichte → Pufferwert). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public double pxPerRad = 700, preExposure = 1;
        /**
         * Seit Timmendorf Phase 12, für die Spiegelung: Tiefe der Szene (Textur, 0 = ohne), ihre Umkehrung, Bildgröße. Mit
         * Tiefe zeichnet {@link #drawReflection} ohne Tiefentest und nur dort, wo die Szene das Wasser zeigt (Höhe
         * {@link #waterY} ± {@link #waterTolerance}) – so verdeckt die Wasserfläche ihre eigene Spiegelung nicht.
         */
        public int depthTexture;
        public float[] invViewProj;
        public boolean reversedZ;
        public int width = 1, height = 1;
    }

    static final int STAR = 8, TRAIL = 7, SMOKE = 8;
    /** Höhe der Wasserfläche (m), Anteil der Spiegelung (Fresnel bei flachem Blick, rau) [G]. */
    public volatile double waterY = 0, reflect = 0.12;
    /** Seit Timmendorf Phase 12: wie weit die Wasserfläche (Wellen) von {@link #waterY} abweichen darf (m). */
    public volatile double waterTolerance = 0.8;
    /** Wie lange ein Funkenschweif nachglüht (s, ohne Langzeitbelichtung) und welcher Anteil der Spur zählt [G]. */
    public volatile double sparkCool = 0.18, sparkShare = 0.35;
    /** Langzeitbelichtung (s, 0 = aus): die Spuren der letzten Sekunden mit gleichem Gewicht. */
    public volatile double longExposure = 0;
    /** Albedo des Rauchs; Himmelslicht auf dem Rauch (cd/m², linear) ohne Feuerwerk. */
    public volatile double smokeAlbedo = 0.6;
    /**
     * Seit Timmendorf Phase 14: Deckung des Rauchs je Scheibe höchstens so viel, und ein Licht wirkt erst ab diesem Abstand
     * (m) zur Mitte der Wolke – das Licht einer Bombe sitzt in ihrem Bild, nicht im Rauch (Befund Phase 13: der Rauch
     * leuchtete als helle, gelbe Ballen).
     */
    public volatile double smokeMaxOpacity = 0.35, smokeLightDistance = 25;
    public volatile double[] ambient = {0.0006, 0.0007, 0.001};
    /** Kleinster Halbmesser eines Sterns (Bildpunkte). */
    public volatile double minPixels = 0.8;

    private int progBill, progTrail, vaoStar, vaoSmoke, vaoTrail, vboStar, vboSmoke, vboTrail;
    private int capStar, capSmoke, capTrail;
    private float[] star = new float[0], smoke = new float[0], trail = new float[0];
    private int nStar, nSmoke, nTrail;
    private boolean ready;

    public boolean ready() { return ready; }
    public int stars() { return nStar; }
    public int smokes() { return nSmoke; }
    public int segments() { return nTrail / 2; }

    public void init(GL4 gl) {
        progBill = program(gl, "firework.vert", "firework.frag");
        progTrail = program(gl, "firework_trail.vert", "firework_trail.frag");
        int[] v = new int[3], b = new int[3];
        gl.glGenVertexArrays(3, v, 0);
        gl.glGenBuffers(3, b, 0);
        vaoStar = v[0]; vaoSmoke = v[1]; vaoTrail = v[2];
        vboStar = b[0]; vboSmoke = b[1]; vboTrail = b[2];
        capStar = capSmoke = capTrail = 1024;
        layoutBill(gl, vaoStar, vboStar, capStar * STAR);
        layoutBill(gl, vaoSmoke, vboSmoke, capSmoke * SMOKE);
        gl.glBindVertexArray(vaoTrail);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboTrail);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, capTrail * TRAIL * 4L, null, GL.GL_DYNAMIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 4, GL.GL_FLOAT, false, TRAIL * 4, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, TRAIL * 4, 16);
        gl.glBindVertexArray(0);
        ready = true;
    }

    private static void layoutBill(GL4 gl, int vao, int vbo, int floats) {
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, floats * 4L, null, GL.GL_DYNAMIC_DRAW);
        for (int i = 0; i < 2; i++) {
            gl.glEnableVertexAttribArray(i);
            gl.glVertexAttribPointer(i, 4, GL.GL_FLOAT, false, 32, i * 16L);
            gl.glVertexAttribDivisor(i, 1);
        }
        gl.glBindVertexArray(0);
    }

    /**
     * Füllt die Felder aus dem Zustand des Feuerwerks (CPU, ohne GL); {@link #upload} schiebt sie dann auf die Karte.
     * Getrennt, damit das Füllen prüfbar ist ({@code FireworkCheck}).
     */
    public void prepare(Firework f, double[] eye) {
        List<Firework.Star> st = f.stars();
        // Sterne
        if (star.length < st.size() * STAR) star = new float[Math.max(1024, st.size()) * STAR];
        nStar = 0;
        for (Firework.Star s : st) {
            double cd = s.candela();
            if (cd <= 0) continue;
            double[] c = s.salt.rgb();
            int o = nStar++ * STAR;
            star[o] = (float) s.x; star[o + 1] = (float) s.y; star[o + 2] = (float) s.z; star[o + 3] = (float) cd;
            star[o + 4] = (float) c[0]; star[o + 5] = (float) c[1]; star[o + 6] = (float) c[2]; star[o + 7] = (float) s.diameter();
        }
        // Schweife: je Stück zwei Ecken
        double keep = longExposure > 0 ? longExposure : 0.45;
        int max = (int) Math.ceil(keep / Firework.TRAIL_STEP);
        int need = 0;
        for (Firework.Star s : st) need += Math.max(0, Math.min(s.trailCount, max) - 1) * 2;
        if (trail.length < need * TRAIL) trail = new float[Math.max(2048, need) * TRAIL];
        nTrail = 0;
        float[] a = new float[4], b = new float[4];
        double[] gold = Firework.Salt.GOLD.rgb();
        for (Firework.Star s : st) {
            int m = Math.min(s.trailCount, max);
            if (m < 2) continue;
            double[] c = s.salt.rgb();
            double[] cc = longExposure > 0 || s.sparks < 0.5 ? c : mix(c, gold, 0.6);
            for (int i = 1; i < m; i++) {
                s.trailPoint(i - 1, a);
                s.trailPoint(i, b);
                double w = longExposure > 0 ? Firework.TRAIL_STEP / longExposure * 30 : s.sparks * sparkShare * Math.exp(-i * Firework.TRAIL_STEP / sparkCool);
                double cd = 0.5 * (a[3] + b[3]) * w;
                if (cd <= 0) continue;
                double len = Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
                for (float[] p : new float[][]{a, b}) {
                    int o = nTrail++ * TRAIL;
                    trail[o] = p[0]; trail[o + 1] = p[1]; trail[o + 2] = p[2]; trail[o + 3] = (float) len;
                    trail[o + 4] = (float) (cc[0] * cd); trail[o + 5] = (float) (cc[1] * cd); trail[o + 6] = (float) (cc[2] * cd);
                }
            }
        }
        // Rauch: beleuchtet, hinten nach vorn
        List<Firework.Light> lights = f.lights();
        List<Firework.Smoke> sm = new ArrayList<>(f.smoke());
        sm.sort((p, q) -> Double.compare(d2(q, eye), d2(p, eye)));
        if (smoke.length < sm.size() * SMOKE) smoke = new float[Math.max(1024, sm.size()) * SMOKE];
        nSmoke = 0;
        double k = smokeAlbedo / Math.PI;
        for (Firework.Smoke s : sm) {
            double op = Math.min(smokeMaxOpacity, s.opacity());
            if (op < 0.003) continue;
            double er = 0, eg = 0, eb = 0;
            for (Firework.Light l : lights) {
                double dx = l.x() - s.x, dy = l.y() - s.y, dz = l.z() - s.z, dd = Math.max(dx * dx + dy * dy + dz * dz, Math.pow(s.radius + smokeLightDistance, 2));
                er += l.r() / dd; eg += l.g() / dd; eb += l.b() / dd;
            }
            int o = nSmoke++ * SMOKE;
            smoke[o] = (float) s.x; smoke[o + 1] = (float) s.y; smoke[o + 2] = (float) s.z; smoke[o + 3] = (float) s.radius;
            smoke[o + 4] = (float) (er * k + ambient[0]); smoke[o + 5] = (float) (eg * k + ambient[1]); smoke[o + 6] = (float) (eb * k + ambient[2]);
            smoke[o + 7] = (float) op;
        }
    }

    /** Schiebt die mit {@link #prepare} gefüllten Felder auf die Karte (wächst bei Bedarf). */
    public void upload(GL4 gl) {
        capStar = push(gl, vboStar, star, nStar * STAR, capStar, STAR);
        capSmoke = push(gl, vboSmoke, smoke, nSmoke * SMOKE, capSmoke, SMOKE);
        capTrail = push(gl, vboTrail, trail, nTrail * TRAIL, capTrail, TRAIL);
    }

    private static int push(GL4 gl, int vbo, float[] d, int n, int cap, int per) {
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        if (n > cap * per) {
            cap = Math.max(n / per, cap * 2);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) cap * per * 4, null, GL.GL_DYNAMIC_DRAW);
        }
        if (n > 0) gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, n * 4L, Buffers.newDirectFloatBuffer(d, 0, n));
        return cap;
    }

    /** Zeichnet Rauch, Schweife und Sterne in den gebundenen (HDR-)Bildspeicher. */
    public void draw(GL4 gl, View v) { pass(gl, v, false, 1); }

    /** Die Spiegelung im Wasser; vor der Wasserfläche oder in deren Spiegelpuffer aufzurufen. */
    public void drawReflection(GL4 gl, View v) { pass(gl, v, true, reflect); }

    private void pass(GL4 gl, View v, boolean mirror, double gain) {
        if (!ready) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        if (mirror) gl.glEnable(GL4.GL_CLIP_DISTANCE0);
        boolean mask = mirror && v.depthTexture != 0 && v.invViewProj != null;
        if (mask) {
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glActiveTexture(GL.GL_TEXTURE0 + 7);
            gl.glBindTexture(GL.GL_TEXTURE_2D, v.depthTexture);
            gl.glActiveTexture(GL.GL_TEXTURE0);
        }
        float g = (float) (v.preExposure * gain);
        // Rauch: vormultipliziert
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glUseProgram(progBill);
        common(gl, progBill, v, mirror, g, mask);
        gl.glUniform1f(gl.glGetUniformLocation(progBill, "uMinPx"), (float) minPixels);
        gl.glUniform1i(gl.glGetUniformLocation(progBill, "uMode"), 1);
        gl.glBindVertexArray(vaoSmoke);
        if (nSmoke > 0) gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, nSmoke);
        // Licht: additiv
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glUniform1i(gl.glGetUniformLocation(progBill, "uMode"), 0);
        gl.glBindVertexArray(vaoStar);
        if (nStar > 0) gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, nStar);
        gl.glUseProgram(progTrail);
        common(gl, progTrail, v, mirror, g, mask);
        gl.glBindVertexArray(vaoTrail);
        if (nTrail > 0) gl.glDrawArrays(GL.GL_LINES, 0, nTrail);
        gl.glBindVertexArray(0);
        if (mirror) gl.glDisable(GL4.GL_CLIP_DISTANCE0);
        if (mask) {
            gl.glActiveTexture(GL.GL_TEXTURE0 + 7);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glEnable(GL.GL_DEPTH_TEST);
        }
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
    }

    private void common(GL4 gl, int p, View v, boolean mirror, float gain, boolean mask) {
        gl.glUniform1i(gl.glGetUniformLocation(p, "uMask"), mask ? 1 : 0);
        if (mask) {
            gl.glUniform1i(gl.glGetUniformLocation(p, "uDepth"), 7);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uInvViewProj"), 1, false, v.invViewProj, 0);
            gl.glUniform2f(gl.glGetUniformLocation(p, "uViewport"), v.width, v.height);
            gl.glUniform1i(gl.glGetUniformLocation(p, "uReversed"), v.reversedZ ? 1 : 0);
            gl.glUniform1f(gl.glGetUniformLocation(p, "uWaterLevel"), (float) waterY);
            gl.glUniform1f(gl.glGetUniformLocation(p, "uWaterTol"), (float) waterTolerance);
        }
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uViewProj"), 1, false, v.viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uEye"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uMirror"), mirror ? 1 : 0);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uWaterY"), (float) waterY);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uPxPerRad"), (float) v.pxPerRad);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uGain"), gain);
    }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(progBill);
        gl.glDeleteProgram(progTrail);
        gl.glDeleteVertexArrays(3, new int[]{vaoStar, vaoSmoke, vaoTrail}, 0);
        gl.glDeleteBuffers(3, new int[]{vboStar, vboSmoke, vboTrail}, 0);
        ready = false;
    }

    /** Die Programme (für Prüfungen). */
    public static String[] files() { return new String[]{"firework.vert", "firework.frag", "firework_trail.vert", "firework_trail.frag"}; }

    /** Für Prüfungen: gefüllte Werte (Sterne, Rauch, Schweif-Ecken). */
    public float[] starData() { return star; }
    public float[] smokeData() { return smoke; }
    public int trailVertices() { return nTrail; }

    private static double d2(Firework.Smoke s, double[] e) {
        double dx = s.x - e[0], dy = s.y - e[1], dz = s.z - e[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] * (1 - t) + b[0] * t, a[1] * (1 - t) + b[1] * t, a[2] * (1 - t) + b[2] * t};
    }

    /** Liest eine Shader-Datei aus diesem Package (Ressource neben der Klasse). */
    public static String source(String file) {
        try (InputStream in = FireworkGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("firework: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("firework: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int program(GL4 gl, String vsFile, String fsFile) {
        int p = gl.glCreateProgram();
        for (String file : new String[]{vsFile, fsFile}) {
            int sh = gl.glCreateShader(file.endsWith(".vert") ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
            gl.glCompileShader(sh);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[4096];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("firework: " + file + ": " + new String(b, 0, len[0], StandardCharsets.UTF_8));
            }
            gl.glAttachShader(p, sh);
            gl.glDeleteShader(sh);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("firework: Programm " + vsFile + " lässt sich nicht binden");
        return p;
    }
}
