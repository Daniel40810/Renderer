package com.dan.cloth.gl;

import com.dan.cloth.Cloth;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * XPBD auf der Grafikkarte (F9 A, seit Timmendorf Phase 6): dieselbe Rechnung wie {@link Cloth#step} im
 * Compute-Shader {@code cloth.comp}, eine Arbeitsgruppe je Tuch (bis 1024 Punkte), alle Unterschritte eines Bildes in
 * einem Aufruf. Die Tücher bleiben auf der Grafikkarte; {@link ClothGl} zeichnet direkt aus den Puffern
 * ({@link #positions()}, {@link #normals()}).
 * <p>
 * Was auf dem Prozessor bleibt: Wind und Phase je Schritt ({@link Cloth#advance}), die festen Punkte (die App bewegt
 * sie am Objekt, sie werden je Bild hochgeladen), Leinen und Reffen ({@link #refresh} nach einer Änderung). Der Grund
 * ist auf der Grafikkarte eine Ebene in der Höhe des Grunds am Bezugspunkt.
 */
public final class ClothGpu {
    /** Größte Zahl der Punkte je Tuch (geteilter Speicher der Arbeitsgruppe). */
    public static final int MAX_POINTS = 1024;
    private static final int PARAM_FLOATS = 24, VERT_FLOATS = 20;

    private int program;
    private final int[] buf = new int[6];
    private final List<Cloth> cloths = new ArrayList<>();
    private int[] offset = new int[0];
    private int total;
    private FloatBuffer params, kin;
    private boolean ok;
    private String info = "aus";

    public void init(GL4 gl) {
        int sh = gl.glCreateShader(GL4.GL_COMPUTE_SHADER);
        gl.glShaderSource(sh, 1, new String[]{ClothGl.source("cloth.comp")}, null, 0);
        gl.glCompileShader(sh);
        int[] st = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, st, 0);
        if (st[0] == 0) throw new IllegalStateException("cloth: cloth.comp lässt sich nicht übersetzen: " + ClothGl.log(gl, sh, false));
        program = gl.glCreateProgram();
        gl.glAttachShader(program, sh);
        gl.glLinkProgram(program);
        gl.glDeleteShader(sh);
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, st, 0);
        if (st[0] == 0) throw new IllegalStateException("cloth: Compute-Programm lässt sich nicht binden: " + ClothGl.log(gl, program, true));
        gl.glGenBuffers(6, buf, 0);
        ok = true;
    }

    public boolean ok() { return ok; }

    /** Die Tücher auf die Grafikkarte legen (Lage und Geschwindigkeit wie jetzt am Objekt). */
    public void setCloths(GL4 gl, List<Cloth> cs) {
        cloths.clear();
        cloths.addAll(cs);
        offset = new int[cs.size()];
        total = 0;
        for (int c = 0; c < cs.size(); c++) {
            Cloth cl = cs.get(c);
            if (cl.nu * cl.nv > MAX_POINTS) throw new IllegalArgumentException("cloth: Tuch mit " + cl.nu * cl.nv + " Punkten, höchstens " + MAX_POINTS);
            offset[c] = total;
            total += cl.nu * cl.nv;
        }
        FloatBuffer pos = Buffers.newDirectFloatBuffer(total * 4), vel = Buffers.newDirectFloatBuffer(total * 4);
        for (Cloth cl : cs)
            for (int k = 0; k < cl.nu * cl.nv; k++) {
                pos.put((float) cl.pos[k * 3]).put((float) cl.pos[k * 3 + 1]).put((float) cl.pos[k * 3 + 2]).put(1);
                vel.put((float) cl.vel[k * 3]).put((float) cl.vel[k * 3 + 1]).put((float) cl.vel[k * 3 + 2]).put(0);
            }
        pos.flip();
        vel.flip();
        data(gl, 2, pos, GL4.GL_DYNAMIC_COPY);
        data(gl, 3, vel, GL4.GL_DYNAMIC_COPY);
        data(gl, 4, Buffers.newDirectFloatBuffer(total * 4), GL4.GL_DYNAMIC_COPY);
        params = Buffers.newDirectFloatBuffer(Math.max(1, cs.size()) * PARAM_FLOATS);
        kin = Buffers.newDirectFloatBuffer(Math.max(1, total) * 4);
        refresh(gl);
        info = String.format(Locale.GERMAN, "%d Tücher, %,d Punkte auf der Grafikkarte", cs.size(), total);
    }

    /** Feste Punkte, Leinen, Ruhelängen und Massen neu hochladen (nach Reffen, neuer Leine). */
    public void refresh(GL4 gl) {
        FloatBuffer v = Buffers.newDirectFloatBuffer(Math.max(1, total) * VERT_FLOATS);
        for (Cloth cl : cloths)
            for (int k = 0; k < cl.nu * cl.nv; k++) {
                v.put((float) cl.mass[k]).put(cl.pinned[k] ? 1 : 0).put(cl.lra[k]).put((float) cl.lraLength[k]);
                v.put((float) cl.tether[k * 4]).put((float) cl.tether[k * 4 + 1]).put((float) cl.tether[k * 4 + 2]).put((float) cl.tether[k * 4 + 3]);
                for (int d = 0; d < 12; d++) v.put((float) cl.rest[k * 12 + d]);
            }
        v.flip();
        data(gl, 1, v, GL.GL_STATIC_DRAW);
    }

    private void data(GL4 gl, int i, java.nio.Buffer b, int usage) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf[i]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) b.limit() * 4, b, usage);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /** Alle Tücher dt Sekunden weiter (ein Aufruf des Compute-Shaders). */
    public void step(GL4 gl, double dt) {
        if (!ok || cloths.isEmpty()) return;
        params.clear();
        kin.clear();
        for (int c = 0; c < cloths.size(); c++) {
            Cloth cl = cloths.get(c);
            double[] a = cl.advance(dt);
            double u = Math.sqrt(a[2] * a[2] + a[3] * a[3] + a[4] * a[4]);
            double g = cl.ground == null ? -1e9 : cl.ground.height(cl.reference[0], cl.reference[2]);
            params.put(Float.intBitsToFloat(cl.nu)).put(Float.intBitsToFloat(cl.nv)).put(Float.intBitsToFloat(offset[c])).put(Float.intBitsToFloat((int) a[0]));
            params.put((float) a[1]).put(cl.iterations).put((float) cl.damping).put((float) cl.airDensity);
            params.put((float) a[2]).put((float) a[3]).put((float) a[4]).put((float) (cl.flutter > 0 ? 0.5 * cl.airDensity * u * u * cl.flutter : 0));
            params.put((float) a[6]).put((float) a[5]).put((float) ((cl.nv - 1) * cl.dv)).put((float) ((cl.seed % 997) * 0.37));
            params.put((float) cl.stretchCompliance).put((float) cl.shearCompliance).put((float) cl.bendCompliance).put((float) g);
            params.put((float) cl.normalCoefficient).put((float) cl.tangentCoefficient)
                    .put((float) (cl.arealDensity * (1 + 1.5 * Math.max(0, Math.min(1, cl.wetness))))).put((float) u);
            for (int k = 0; k < cl.nu * cl.nv; k++)
                if (cl.pinned[k]) kin.put((float) cl.pos[k * 3]).put((float) cl.pos[k * 3 + 1]).put((float) cl.pos[k * 3 + 2]).put(1);
                else kin.put(0).put(0).put(0).put(0);
        }
        params.flip();
        kin.flip();
        data(gl, 0, params, GL4.GL_STREAM_DRAW);
        data(gl, 5, kin, GL4.GL_STREAM_DRAW);
        gl.glUseProgram(program);
        for (int i = 0; i < 6; i++) gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, i, buf[i]);
        gl.glDispatchCompute(cloths.size(), 1, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        for (int i = 0; i < 6; i++) gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, i, 0);
        gl.glUseProgram(0);
    }

    /** Lage und Geschwindigkeit von der Grafikkarte in die Objekte zurück (Prüfung, Ereignisse; langsam). */
    public void readBack(GL4 gl) {
        FloatBuffer p = Buffers.newDirectFloatBuffer(total * 4), v = Buffers.newDirectFloatBuffer(total * 4);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf[2]);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, (long) total * 16, p);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf[3]);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, (long) total * 16, v);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        for (int c = 0; c < cloths.size(); c++) {
            Cloth cl = cloths.get(c);
            for (int k = 0; k < cl.nu * cl.nv; k++) {
                int o = (offset[c] + k) * 4;
                for (int a = 0; a < 3; a++) {
                    cl.pos[k * 3 + a] = p.get(o + a);
                    cl.vel[k * 3 + a] = v.get(o + a);
                }
            }
        }
    }

    /** Puffer der Lagen (vec4 je Punkt) und Normalen, Reihenfolge wie {@link #cloths()}. */
    public int positions() { return buf[2]; }
    public int normals() { return buf[4]; }
    public List<Cloth> cloths() { return cloths; }
    public int offset(int c) { return offset[c]; }
    public int points() { return total; }
    public String info() { return info; }

    /**
     * Grafikkarte gegen Prozessor: zwei gleiche Flaggen bei 8 m/s ohne Flattern, eine 0,5 s auf der Grafikkarte, eine
     * auf dem Prozessor; größte Abweichung eines Punkts. Läuft in eigenen Puffern und stellt die eigenen Tücher wieder her.
     */
    public String selfCheck(GL4 gl) {
        List<Cloth> keep = new ArrayList<>(cloths);
        Cloth a = Cloth.beachFlag(0, 0, 0), b = Cloth.beachFlag(0, 0, 0);
        for (Cloth c : new Cloth[]{a, b}) {
            c.flutter = 0;
            c.wind = (x, y, z, t, o) -> { o[0] = 8; o[1] = 0; o[2] = 1.5; };
        }
        setCloths(gl, List.of(a));
        for (int i = 0; i < 30; i++) { step(gl, 1 / 60.0); b.step(1 / 60.0); }
        readBack(gl);
        double m = 0;
        for (int k = 0; k < a.pos.length; k++) m = Math.max(m, Math.abs(a.pos[k] - b.pos[k]));
        String r = String.format(Locale.GERMAN, "Grafikkarte = Prozessor: größte Abweichung %.1f mm nach 0,5 s (%s)", m * 1000, m < 0.02 ? "in Ordnung" : "zu groß");
        if (!keep.isEmpty()) setCloths(gl, keep); else { cloths.clear(); total = 0; }
        return (m < 0.02 ? "" : "!") + r;
    }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        gl.glDeleteBuffers(6, buf, 0);
        program = 0;
        ok = false;
    }

    static IntBuffer ints(int[] a) { return Buffers.newDirectIntBuffer(a); }
}
