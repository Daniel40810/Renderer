package com.dan.forest.gl;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

/**
 * Eine Sonnenschattenkarte um das Auge (seit LituyaBay1958 W6 Runde 5): Tiefe aus der Sonne, senkrecht projiziert,
 * für {@link ForestGl.Light#sunShadowTexture} und {@link ForestGl.Light#sunShadowMatrix} (und alles, was dieselbe Karte
 * liest, etwa {@code com.dan.tree.gl.TreeGl.Light} oder ein Gelände der App). Die Mitte rastet auf ganze Bildpunkte der
 * Karte ein: wandert das Auge, springen die Schattenkanten nicht von Bild zu Bild (kein Kriechen der Kanten).
 *
 * <pre>
 * SunShadowMap sm = new SunShadowMap();
 * sm.init(gl);
 * // je Bild:
 * sm.begin(gl, sonne, mitteX, mitteY, mitteZ);
 * forest.drawShadow(gl, sm.matrix, t, 1);
 * sm.end(gl);
 * licht.sunShadowTexture = sm.texture();
 * licht.sunShadowMatrix = sm.matrix;
 * </pre>
 */
public final class SunShadowMap {
    /** Kante (Bildpunkte), halbe Kante der Fläche (m), halbe Tiefe entlang der Sonne (m). Vor {@link #init} setzen. */
    public int size = 2048;
    public double radius = 90, depth = 400;
    /** Licht-Projektion·Blick (Spalten) des letzten {@link #begin}. */
    public final float[] matrix = new float[16];
    private int tex, fbo;
    private final int[] saveFb = new int[1], saveVp = new int[4];
    private final float[] saveClear = new float[1];
    private boolean saveCull;
    private final int[] saveClip = new int[2], saveFunc = new int[1];
    private final byte[] saveMask = new byte[1];
    private boolean saveDepth, saveBlend;
    /** GL_CLIP_ORIGIN, GL_CLIP_DEPTH_MODE, GL_NEGATIVE_ONE_TO_ONE (OpenGL 4.5, ARB_clip_control). */
    public static final int CLIP_ORIGIN = 0x935C, CLIP_DEPTH_MODE = 0x935D, NEGATIVE_ONE_TO_ONE = 0x935E;

    public int texture() { return tex; }

    /** Größe eines Bildpunkts der Karte (m): so scharf kann ein Schatten höchstens sein. */
    public double texel() { return 2 * radius / size; }

    public void init(GL4 gl) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        tex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT24, size, size);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL4.GL_CLAMP_TO_BORDER);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL4.GL_CLAMP_TO_BORDER);
        gl.glTexParameterfv(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_BORDER_COLOR, new float[]{1, 1, 1, 1}, 0);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_MODE, GL4.GL_COMPARE_REF_TO_TEXTURE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_FUNC, GL.GL_LEQUAL);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glGenFramebuffers(1, t, 0);
        fbo = t[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, tex, 0);
        gl.glDrawBuffer(GL.GL_NONE);
        gl.glReadBuffer(GL.GL_NONE);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
    }

    /** Karte binden und leeren; Matrix für die Sonne (zur Sonne, Einheit egal) um (cx, cy, cz). Zustände werden gemerkt. */
    public void begin(GL4 gl, double[] sun, double cx, double cy, double cz) {
        matrix(sun, cx, cy, cz, radius, depth, size, matrix);
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, saveFb, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, saveVp, 0);
        gl.glGetFloatv(GL.GL_DEPTH_CLEAR_VALUE, saveClear, 0);
        saveCull = gl.glIsEnabled(GL.GL_CULL_FACE);
        saveDepth = gl.glIsEnabled(GL.GL_DEPTH_TEST);
        saveBlend = gl.glIsEnabled(GL.GL_BLEND);
        gl.glGetIntegerv(GL.GL_DEPTH_FUNC, saveFunc, 0);
        gl.glGetBooleanv(GL.GL_DEPTH_WRITEMASK, saveMask, 0);
        // Die Karte rechnet mit Tiefe −1..1 (wie die Leser: q · 0,5 + 0,5). Hat die App glClipControl auf 0..1 gestellt
        // (umgekehrte Tiefe), fiele die sonnennahe Hälfte weg und die Tiefen passten nicht: für die Karte zurückstellen.
        gl.glGetIntegerv(CLIP_ORIGIN, saveClip, 0);
        gl.glGetIntegerv(CLIP_DEPTH_MODE, saveClip, 1);
        if (saveClip[1] != NEGATIVE_ONE_TO_ONE && saveClip[1] != 0) gl.glClipControl(saveClip[0], NEGATIVE_ONE_TO_ONE);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, size, size);
        gl.glDepthMask(true);
        gl.glClearDepth(1.0);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_CULL_FACE);
    }

    /** Vorige Zustände (Ziel, Ausschnitt, Leerwert der Tiefe, Rückseiten, Clip-Steuerung, Tiefenvergleich, Mischen, Schreibmaske der Tiefe) zurück. */
    public void end(GL4 gl) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, saveFb[0]);
        gl.glViewport(saveVp[0], saveVp[1], saveVp[2], saveVp[3]);
        if (saveClip[1] != NEGATIVE_ONE_TO_ONE && saveClip[1] != 0) gl.glClipControl(saveClip[0], saveClip[1]);
        gl.glClearDepth(saveClear[0]);
        if (saveCull) gl.glEnable(GL.GL_CULL_FACE);
        gl.glDepthFunc(saveFunc[0]);   // seit W6 Runde 6 auch Tiefenvergleich, Tiefentest und Mischen
        if (!saveDepth) gl.glDisable(GL.GL_DEPTH_TEST);
        if (saveBlend) gl.glEnable(GL.GL_BLEND);
        gl.glDepthMask(saveMask[0] != 0);   // seit Runde 6b
    }

    public void dispose(GL4 gl) {
        if (fbo != 0) gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        fbo = tex = 0;
    }

    /**
     * Licht-Projektion·Blick (Spalten) in out: Blick entlang −sun auf (cx, cy, cz), senkrecht, halbe Kante half (m), halbe
     * Tiefe d (m); die Mitte rastet quer zur Sonne auf ganze Bildpunkte einer Karte mit n Bildpunkten ein (n ≤ 0: nicht).
     * Ergebnis im Clipraum: x, y in −1..1 über die Fläche, z = −1 am sonnennahen Rand, +1 am fernen, 0 in der Mitte.
     */
    public static void matrix(double[] sun, double cx, double cy, double cz, double half, double d, int n, float[] out) {
        double sl = Math.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]);
        double fx = -sun[0] / sl, fy = -sun[1] / sl, fz = -sun[2] / sl;   // Blick des Lichts
        // rechts = f × oben (oben = y; bei senkrechter Sonne z)
        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(fy) > 0.999) { uy = 0; uz = 1; }
        double rx = fy * uz - fz * uy, ry = fz * ux - fx * uz, rz = fx * uy - fy * ux;
        double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl; ry /= rl; rz /= rl;
        double vx = ry * fz - rz * fy, vy = rz * fx - rx * fz, vz = rx * fy - ry * fx;   // oben = rechts × f
        if (n > 0) {
            double tx = 2 * half / n;
            double a = rx * cx + ry * cy + rz * cz, b = vx * cx + vy * cy + vz * cz;
            double da = Math.floor(a / tx) * tx - a, db = Math.floor(b / tx) * tx - b;
            cx += rx * da + vx * db; cy += ry * da + vy * db; cz += rz * da + vz * db;
        }
        double ox = rx * cx + ry * cy + rz * cz, oy = vx * cx + vy * cy + vz * cz, oz = fx * cx + fy * cy + fz * cz;
        // Zeilen: x = (r·p − ox)/half, y = (v·p − oy)/half, z = (f·p − oz)/d
        out[0] = (float) (rx / half); out[4] = (float) (ry / half); out[8] = (float) (rz / half); out[12] = (float) (-ox / half);
        out[1] = (float) (vx / half); out[5] = (float) (vy / half); out[9] = (float) (vz / half); out[13] = (float) (-oy / half);
        out[2] = (float) (fx / d); out[6] = (float) (fy / d); out[10] = (float) (fz / d); out[14] = (float) (-oz / d);
        out[3] = 0; out[7] = 0; out[11] = 0; out[15] = 1;
    }
}
