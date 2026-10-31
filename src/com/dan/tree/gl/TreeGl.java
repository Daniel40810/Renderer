package com.dan.tree.gl;

import com.dan.tree.TreeAsset;
import com.dan.tree.TreeLod;
import com.dan.tree.TreeMesh;
import com.dan.tree.TreeSeason;
import com.dan.tree.TreeSpecies;
import com.dan.tree.TreeWind;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asset-Bäume einer {@link TreeSpecies} mit JOGL (OpenGL 4.3): je Variante und Stufe ein Netz, das im
 * Hintergrund gebacken und beim nächsten {@link #update} hochgeladen wird; je Bild werden die Bäume nach
 * Entfernung in Eimer sortiert und jeder Eimer mit einem Aufruf gezeichnet. Wind (wie {@link TreeWind}) und
 * Jahreszeit rechnet der Shader, ein Wechsel des Tages backt nichts neu.
 *
 * <pre>
 * TreeGl gl = new TreeGl(BlackAlder.species());
 * gl.add(variante, x, y, z, drehung, maßstab);  // beliebig viele
 * gl.init(gl4);
 * // je Bild:
 * gl.setSeason(tag, schnee);
 * gl.setWind(wind);                           // TreeWind
 * gl.update(gl4, auge);
 * gl.draw(gl4, view, light, t);
 * </pre>
 */
public final class TreeGl {

    /** Kamera. */
    public static final class View {
        public float[] viewProj;
        public double[] eye;
        /** true: Belichtung, Reinhard und sRGB im Shader; false: linear (die App macht das selbst) */
        public boolean tonemap = true;
        public double exposure = 1;
    }

    /** Licht und Dunst, Farben linear. */
    public static final class Light {
        public double[] sunDir = {-0.45, 0.75, 0.35};
        public double[] sunColor = {2.6, 2.45, 2.2};
        public double[] skyColor = {0.45, 0.55, 0.75};
        public double[] groundColor = {0.12, 0.11, 0.08};
        public double[] fogColor = {0.62, 0.70, 0.80};
        public double fogDistance = 1500;
    }

    /** Ein Baum: Variante, Fuß, Drehung (rad), Maßstab. */
    public record Tree(int variant, float x, float y, float z, float yaw, float scale) {}

    public final TreeSpecies species;
    /** Grenzen der Stufen 0, 1, 2 in Baumhöhen; darüber Stufe 3 (Vorgabe aus {@link TreeSpecies#lodDistance}) */
    public float[] lodDistance;
    /** Ausdünnen ab, ganz weg ab (in Höhen; Vorgabe aus der Art, für Gras) */
    public float fadeDistance, maxDistance;
    /** ohne Stufe 0 (Original mit allen Blättern), etwa für große Bestände */
    public int minLevel = 0;

    private final List<Tree> trees = new ArrayList<>();
    private final int nv;
    private final Slot[][] slots;
    private final float[] heights;
    private int program;
    private TreeSeason.Look look;
    private final TreeWind wind = new TreeWind();
    private long drawnTriangles;

    private static final class Slot {
        CompletableFuture<TreeMesh> baking;
        TreeMesh mesh;
        int vao, vbo, ibo, inst;
        int count;                       // Instanzen in diesem Bild
        float[] data = new float[64];
        FloatBuffer direct;
        boolean uploaded, failed;
    }

    public TreeGl(TreeSpecies species) {
        this.species = species;
        lodDistance = species.lodDistance.clone();
        fadeDistance = species.fadeDistance;
        maxDistance = species.maxDistance;
        nv = species.variantCount();
        slots = new Slot[nv][TreeLod.LEVELS];
        barkTex = new int[nv];
        leafTex = new int[nv];
        heights = new float[nv];
        for (int v = 0; v < nv; v++) {
            for (int l = 0; l < TreeLod.LEVELS; l++) slots[v][l] = new Slot();
            TreeAsset a = species.load(v);
            // Maß für die Stufen: Höhe, bei Gelände der halbe Durchmesser
            heights[v] = species.form == TreeSpecies.Form.GROUND ? species.bake(v, TreeLod.LEVELS - 1).radius : a.height();
        }
        look = species.season.look(180, 0);
    }

    public void add(int variant, float x, float y, float z, float yaw, float scale) {
        trees.add(new Tree(variant, x, y, z, yaw, scale));
    }

    public void clear() {
        trees.clear();
    }

    public List<Tree> trees() {
        return trees;
    }

    public float height(int variant) {
        return heights[variant];
    }

    public void setSeason(int day, float snow) {
        look = species.season.look(day, snow);
    }

    public void setWind(TreeWind w) {
        wind.speed = w.speed;
        wind.fromDeg = w.fromDeg;
        wind.gustiness = w.gustiness;
        wind.external = w.external;
    }

    /** Dreiecke im letzten Bild. */
    public long triangles() {
        return drawnTriangles;
    }

    /** true, wenn alle Netze hochgeladen sind. */
    public boolean ready() {
        for (Slot[] row : slots) for (Slot s : row) if (s.baking != null && !s.uploaded && !s.failed) return false;
        return true;
    }

    public void init(GL4 gl) {
        program = program(gl, source("tree.vert"), source("tree.frag"));
        for (int v = 0; v < nv; v++) {
            TreeAsset a = species.load(v);
            barkTex[v] = texture(gl, a.barkTexture, false);
            leafTex[v] = texture(gl, a.leafTexture, true);
        }
    }

    private final int[] barkTex, leafTex;
    private final java.util.Map<String, Integer> texIds = new java.util.HashMap<>();

    /** Textur aus der Art hochladen (sRGB, Mipmaps, wiederholt); 0, wenn keine. */
    private int texture(GL4 gl, String name, boolean alpha) {
        if (name == null || name.isEmpty()) return 0;
        Integer known = texIds.get(name);
        if (known != null) return known;
        int id = uploadTexture(gl, species.texture(name));
        texIds.put(name, id);
        return id;
    }

    /** Bild als sRGB-Textur mit Mipmaps hochladen, wiederholt; liefert die Kennung. */
    public static int uploadTexture(GL4 gl, java.awt.image.BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        java.nio.ByteBuffer buf = Buffers.newDirectByteBuffer(w * h * 4);
        boolean hasAlpha = img.getColorModel().hasAlpha();
        for (int p : px) {
            buf.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put(hasAlpha ? (byte) (p >>> 24) : (byte) 255);
        }
        buf.flip();
        int[] id = new int[1];
        gl.glGenTextures(1, id, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, id[0]);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_SRGB8_ALPHA8, w, h, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, buf);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return id[0];
    }

    /** Stufe je Baum wählen, fertig gebackene Netze hochladen, Instanzen füllen. */
    public void update(GL4 gl, double[] eye) {
        this.gl = gl;
        for (Slot[] row : slots)
            for (Slot s : row) {
                s.count = 0;
                if (s.baking != null && !s.uploaded && !s.failed && s.baking.isDone()) upload(gl, s);
            }
        int index = -1;
        for (Tree t : trees) {
            index++;
            float h = heights[t.variant] * t.scale;
            double d = Math.sqrt(sq(eye[0] - t.x) + sq(eye[1] - t.y - h * 0.5) + sq(eye[2] - t.z)) / Math.max(h, 0.05f);
            if (d > maxDistance) continue;
            if (d > fadeDistance) {
                double f = (d - fadeDistance) / Math.max(maxDistance - fadeDistance, 1e-3);
                if (com.dan.tree.TreeBaker.hash(index, 97) < f) continue;
            }
            int lod = d < lodDistance[0] ? 0 : d < lodDistance[1] ? 1 : d < lodDistance[2] ? 2 : 3;
            lod = Math.max(lod, minLevel);
            Slot s = slot(t.variant, lod);
            if (s == null) continue;
            if (s.data.length < (s.count + 1) * 8) s.data = java.util.Arrays.copyOf(s.data, s.data.length * 2);
            int b = s.count * 8;
            s.data[b] = t.x;
            s.data[b + 1] = t.y;
            s.data[b + 2] = t.z;
            s.data[b + 3] = t.yaw;
            s.data[b + 4] = t.scale;
            s.data[b + 5] = s.mesh.height;
            s.data[b + 6] = TreeWind.hashPhase(t.x, t.z);
            s.data[b + 7] = species.load(t.variant).gustAttenuation;
            s.count++;
        }
        for (Slot[] row : slots)
            for (Slot s : row) {
                if (!s.uploaded || s.count == 0) continue;
                if (s.direct == null || s.direct.capacity() < s.count * 8) s.direct = Buffers.newDirectFloatBuffer(s.data.length);
                s.direct.clear();
                s.direct.put(s.data, 0, s.count * 8).flip();
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.inst);
                gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) s.count * 8 * 4, s.direct, GL4.GL_STREAM_DRAW);
            }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    private GL4 gl;

    /** Letzter Fehler beim Backen oder Hochladen, sonst null. */
    public String error() {
        return error;
    }

    private volatile String error;

    /** Fertiges Netz der Stufe oder einer gröberen; startet das Backen, wenn nötig. */
    private Slot slot(int v, int lod) {
        for (int l = lod; l < TreeLod.LEVELS; l++) {
            Slot s = slots[v][l];
            if (s.baking == null) {
                final int fl = l;
                s.baking = CompletableFuture.supplyAsync(() -> species.bake(v, fl));
            }
            if (s.uploaded) return s;
        }
        // noch nichts fertig: auf die gröbste Stufe warten (klein, schnell)
        Slot s = slots[v][TreeLod.LEVELS - 1];
        if (gl == null || s.failed) return null;
        s.baking.join();
        upload(gl, s);
        return s.uploaded ? s : null;
    }

    /** Größe der Stücke beim Hochladen (Werte), damit kein riesiger Direct-Buffer nötig ist. */
    private static final int CHUNK = 1 << 20;
    private FloatBuffer chunkF;
    private java.nio.IntBuffer chunkI;

    private void upload(GL4 gl, Slot s) {
        TreeMesh m;
        try {
            m = s.baking.join();
        } catch (RuntimeException e) {
            s.failed = true;
            error = "Backen fehlgeschlagen: " + e.getCause();
            e.printStackTrace();
            return;
        }
        int[] ids = new int[3];
        gl.glGenVertexArrays(1, ids, 0);
        s.vao = ids[0];
        gl.glGenBuffers(3, ids, 0);
        s.vbo = ids[0];
        s.ibo = ids[1];
        s.inst = ids[2];
        gl.glBindVertexArray(s.vao);
        if (chunkF == null) {
            chunkF = Buffers.newDirectFloatBuffer(CHUNK);
            chunkI = Buffers.newDirectIntBuffer(CHUNK);
        }
        int nf = m.vertices * TreeMesh.STRIDE;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) nf * 4, null, GL.GL_STATIC_DRAW);
        for (int o = 0; o < nf; o += CHUNK) {
            int n = Math.min(CHUNK, nf - o);
            chunkF.clear();
            chunkF.put(m.v, o, n).flip();
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, (long) o * 4, (long) n * 4, chunkF);
        }
        int stride = TreeMesh.STRIDE * 4;
        attrib(gl, 0, 3, stride, 0);
        attrib(gl, 1, 3, stride, 3);
        attrib(gl, 2, 4, stride, TreeMesh.LIMB);
        attrib(gl, 3, 3, stride, TreeMesh.PHASE_TWIG);
        attrib(gl, 6, 2, stride, TreeMesh.U);
        int ni = m.triangles * 3;
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, s.ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) ni * 4, null, GL.GL_STATIC_DRAW);
        for (int o = 0; o < ni; o += CHUNK) {
            int n = Math.min(CHUNK, ni - o);
            chunkI.clear();
            chunkI.put(m.tri, o, n).flip();
            gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) o * 4, (long) n * 4, chunkI);
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.inst);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 8 * 4, null, GL4.GL_STREAM_DRAW);
        attrib(gl, 4, 4, 32, 0);
        attrib(gl, 5, 4, 32, 4);
        gl.glVertexAttribDivisor(4, 1);
        gl.glVertexAttribDivisor(5, 1);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        int err = gl.glGetError();
        if (err == GL.GL_OUT_OF_MEMORY) {
            s.failed = true;
            error = "Grafikspeicher voll beim Hochladen von " + m;
            gl.glDeleteBuffers(3, new int[]{s.vbo, s.ibo, s.inst}, 0);
            gl.glDeleteVertexArrays(1, new int[]{s.vao}, 0);
            return;
        }
        s.mesh = m;
        s.uploaded = true;
    }

    private static void attrib(GL4 gl, int loc, int size, int stride, int offsetFloats) {
        gl.glEnableVertexAttribArray(loc);
        gl.glVertexAttribPointer(loc, size, GL.GL_FLOAT, false, stride, offsetFloats * 4L);
    }

    public void draw(GL4 gl, View view, Light light, double time) {
        gl.glUseProgram(program);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, view.viewProj, 0);
        gl.glUniform3f(loc(gl, "uCam"), (float) view.eye[0], (float) view.eye[1], (float) view.eye[2]);
        gl.glUniform1f(loc(gl, "uTime"), (float) time);
        float[] w = wind.at(view.eye[0], 0, view.eye[2]);
        gl.glUniform4f(loc(gl, "uWind"), w[0], w[1], w[2], wind.gustiness);
        gl.glUniform3f(loc(gl, "uFlex"), species.flexTrunk, species.flexBranch, species.flutter);
        gl.glUniform2f(loc(gl, "uVisible"), look.lo, look.hi);
        gl.glUniform3fv(loc(gl, "uLeafLo"), 1, look.colorLo, 0);
        gl.glUniform3fv(loc(gl, "uLeafHi"), 1, look.colorHi, 0);
        float[] bark = species.bark != null ? species.bark : species.load(0).barkColor;
        gl.glUniform3fv(loc(gl, "uBark"), 1, bark, 0);
        gl.glUniform1f(loc(gl, "uSnow"), look.snow);
        gl.glUniform3fv(loc(gl, "uLeafRef"), 1, look.reference, 0);
        gl.glUniform1i(loc(gl, "uBarkTex"), 0);
        gl.glUniform1i(loc(gl, "uLeafTex"), 1);
        double[] sd = light.sunDir;
        double sl = Math.sqrt(sd[0] * sd[0] + sd[1] * sd[1] + sd[2] * sd[2]);
        gl.glUniform3f(loc(gl, "uSunDir"), (float) (sd[0] / sl), (float) (sd[1] / sl), (float) (sd[2] / sl));
        gl.glUniform3f(loc(gl, "uSunColor"), (float) light.sunColor[0], (float) light.sunColor[1], (float) light.sunColor[2]);
        gl.glUniform3f(loc(gl, "uSkyColor"), (float) light.skyColor[0], (float) light.skyColor[1], (float) light.skyColor[2]);
        gl.glUniform3f(loc(gl, "uGroundColor"), (float) light.groundColor[0], (float) light.groundColor[1], (float) light.groundColor[2]);
        gl.glUniform4f(loc(gl, "uFog"), (float) light.fogColor[0], (float) light.fogColor[1], (float) light.fogColor[2], (float) light.fogDistance);
        gl.glUniform1f(loc(gl, "uExposure"), (float) view.exposure);
        gl.glUniform1i(loc(gl, "uTonemap"), view.tonemap ? 1 : 0);
        gl.glDisable(GL.GL_CULL_FACE);          // Blätter beidseitig, Röhren ohne Deckel
        long tris = 0;
        for (Slot[] row : slots)
            for (Slot s : row) {
                if (!s.uploaded || s.count == 0) continue;
                int v = variantOf(s);
                boolean tb = s.mesh.barkTextured && barkTex[v] != 0, tl = s.mesh.leafTextured && leafTex[v] != 0;
                gl.glUniform2i(loc(gl, "uTextured"), tb ? 1 : 0, tl ? 1 : 0);
                gl.glActiveTexture(GL.GL_TEXTURE0);
                gl.glBindTexture(GL.GL_TEXTURE_2D, tb ? barkTex[v] : 0);
                gl.glActiveTexture(GL.GL_TEXTURE1);
                gl.glBindTexture(GL.GL_TEXTURE_2D, tl ? leafTex[v] : 0);
                gl.glActiveTexture(GL.GL_TEXTURE0);
                gl.glBindVertexArray(s.vao);
                gl.glDrawElementsInstanced(GL.GL_TRIANGLES, s.mesh.triangles * 3, GL.GL_UNSIGNED_INT, 0L, s.count);
                tris += (long) s.mesh.triangles * s.count;
            }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        drawnTriangles = tris;
    }

    private int variantOf(Slot s) {
        for (int v = 0; v < nv; v++) for (Slot x : slots[v]) if (x == s) return v;
        return 0;
    }

    public void dispose(GL4 gl) {
        for (int id : texIds.values()) gl.glDeleteTextures(1, new int[]{id}, 0);
        texIds.clear();
        for (Slot[] row : slots)
            for (Slot s : row) {
                if (!s.uploaded) continue;
                gl.glDeleteBuffers(3, new int[]{s.vbo, s.ibo, s.inst}, 0);
                gl.glDeleteVertexArrays(1, new int[]{s.vao}, 0);
                s.uploaded = false;
            }
        if (program != 0) gl.glDeleteProgram(program);
        program = 0;
    }

    private int loc(GL4 gl, String name) {
        return gl.glGetUniformLocation(program, name);
    }

    private static double sq(double x) {
        return x * x;
    }

    // ------------------------------------------------------------------ Shader

    /** Quelltext eines Shaders aus diesem Paket. */
    public static String source(String name) {
        try (InputStream in = TreeGl.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalArgumentException("Shader fehlt: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static int program(GL4 gl, String vs, String fs) {
        int v = shader(gl, GL4.GL_VERTEX_SHADER, vs), f = shader(gl, GL4.GL_FRAGMENT_SHADER, fs);
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v);
        gl.glAttachShader(p, f);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("Shader binden: " + log(gl, p, false));
        gl.glDeleteShader(v);
        gl.glDeleteShader(f);
        return p;
    }

    static int shader(GL4 gl, int type, String src) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{"#version 430 core\n" + src}, null);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("Shader übersetzen: " + log(gl, s, true));
        return s;
    }

    static String log(GL4 gl, int id, boolean shader) {
        int[] len = new int[1];
        if (shader) gl.glGetShaderiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0);
        else gl.glGetProgramiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        if (shader) gl.glGetShaderInfoLog(id, b.length, null, 0, b, 0);
        else gl.glGetProgramInfoLog(id, b.length, null, 0, b, 0);
        return new String(b, StandardCharsets.UTF_8).trim();
    }
}
