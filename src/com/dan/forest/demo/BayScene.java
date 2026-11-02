package com.dan.forest.demo;

import com.dan.forest.ForestCulture;
import com.dan.forest.Ground;
import com.dan.forest.Species;
import com.dan.forest.TreeStates;
import com.dan.forest.gl.ForestGl;
import com.dan.forest.gl.ForestGpu;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 * Eine gerechnete Fjordbucht für {@link ForestGpu} (seit LituyaBay1958 Phase 6), ohne Daten: 16 × 12 km, eine Bucht
 * entlang x mit steilen Ufern, darauf Sitka-Fichte (unten), Hemlocktanne (bis zur Baumgrenze bei 600 m) und Roterle (am
 * Ufer); am Nordufer ein Band der Welle wie 1958 [G]: unten fort und umgelegt (hangab), oben gebrochen und entrindet,
 * darüber unberührt. Dazu Boden, Wasser und Himmel, nur so viel, dass der Wald einen Ort hat. Zeitmessung je Teil mit
 * Abfragen der Grafikkarte.
 */
final class BayScene {
    static final double X0 = -8000, X1 = 8000, Z0 = -6000, Z1 = 6000, SPACING = 6;
    /** Höhe der Spurlinie am Nordufer (m) nach x: 524 m am Kopf (x = 5 600), zur Mündung auf 30 m [G]. */
    static double trimline(double x) { return 30 + 494 * Math.pow(Math.max(0, Math.min(1, (x + 6000) / 11600)), 2.2); }

    static double noise(double x, double z) {
        return Math.sin(x / 830.0 + Math.cos(z / 1270.0) * 1.7) * 0.5 + Math.sin(z / 610.0 - x / 1900.0) * 0.3 + Math.sin((x + 2 * z) / 233.0) * 0.12;
    }

    static final Ground TERRAIN = (x, z) -> {
        double half = 1150 + 250 * Math.sin(x / 2300.0) - (x > 5600 ? (x - 5600) * 0.9 : 0);
        double d = Math.abs(z) - Math.max(0, half);
        // Nordufer steil (Fels und Gletscher darüber), Südufer ein breites Vorland mit Wald bis zur Küste [G]
        double h = d < 0 ? -60 - Math.min(200, -d * 0.25)
                : z < 0 ? 4 + d * (0.2 + 0.06 * noise(x * 0.7, z * 0.7)) + d * d * 0.00006
                : 4 + d * (0.035 + 0.02 * noise(x * 0.7, z * 0.7)) + d * d * 0.000014;
        h += 60 * noise(x, z) * Math.min(1, Math.max(0, d) / 400);
        if (x < -6500) h = Math.min(h, -40 + (x + 6500) * 0.02 + Math.abs(z) * 0.01);   // Mündung zum Meer
        return (float) h;
    };

    static final List<Species> SPECIES = List.of(Species.sitkaSpruce(), Species.westernHemlock(), Species.redAlder());

    static double density(double x, double z, int s) {
        double h = TERRAIN.height(x, z);
        if (h < 2) return 0;
        double n = 0.5 + 0.5 * noise(x * 3.1 + 77, z * 2.7 - 31);
        double lowland = smooth(450, 250, h), up = smooth(40, 200, h) * smooth(640, 520, h);
        return switch (s) {
            case 0 -> 0.75 * lowland * (0.6 + 0.4 * n);
            case 1 -> 0.55 * up * (0.5 + 0.5 * (1 - n));
            default -> 0.7 * smooth(45, 6, h) + 0.1 * smooth(300, 100, h) * n * n;
        };
    }

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    /** Zustände im Band der Welle am Nordufer (z &lt; 0). */
    static TreeStates states() {
        TreeStates st = TreeStates.covering(-6500, Z0, 6200, 0, SPACING);
        for (int j = st.j0; j < st.j0 + st.h; j++)
            for (int i = st.i0; i < st.i0 + st.w; i++) {
                double x = (i + 0.5) * SPACING, z = (j + 0.5) * SPACING, h = TERRAIN.height(x, z);
                double tl = trimline(x) * (1 + 0.04 * noise(x * 9, z * 9));
                if (h <= 0 || h > tl) continue;
                double u = h / tl, r = ForestCulture.u01(ForestCulture.slotHash(i, j, 99), 1);
                int s = u < 0.5 ? (r < 0.7 ? TreeStates.GONE : TreeStates.FELLED)
                        : u < 0.85 ? (r < 0.5 ? TreeStates.FELLED : r < 0.8 ? TreeStates.BROKEN : TreeStates.STRIPPED)
                        : (r < 0.5 ? TreeStates.STRIPPED : r < 0.8 ? TreeStates.BROKEN : TreeStates.STANDING);
                st.set(i, j, s, 180 + (r - 0.5) * 40);   // hangab nach Süden in die Bucht, mit Streuung
            }
        return st;
    }

    final ForestGpu forest;
    final TreeStates states;
    int sky, ground, water, groundVao, groundIbo, groundVbo, groundIdx, emptyVao;
    final int[] q = new int[4];
    double cullMs, forestMs, restMs;

    BayScene(ForestGpu.Quality quality) {
        forest = new ForestGpu(SPECIES, 3, SPACING, 1958);
        forest.apply(quality);
        forest.setTerrain(TERRAIN, X0, X0, X1 - X0, 2048);
        forest.setDensity(BayScene::density, X0, X0, X1 - X0, 1024);
        states = states();
        forest.setStates(states);
    }

    void init(GL4 gl) {
        forest.init(gl);
        forest.setSeason(190, 0);
        sky = prog(gl, FULL, SKY);
        ground = prog(gl, GROUND_V, GROUND_F);
        water = prog(gl, WATER_V, WATER_F);
        int[] v = new int[2];
        gl.glGenVertexArrays(2, v, 0);
        groundVao = v[0];
        emptyVao = v[1];
        int nx = 640, nz = 480;
        FloatBuffer vb = Buffers.newDirectFloatBuffer(nx * nz * 3);
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double x = X0 + (X1 - X0) * i / (nx - 1), z = Z0 + (Z1 - Z0) * j / (nz - 1);
                vb.put((float) x).put(TERRAIN.height(x, z)).put((float) z);
            }
        vb.flip();
        IntBuffer ib = Buffers.newDirectIntBuffer((nx - 1) * (nz - 1) * 6);
        for (int j = 0; j < nz - 1; j++)
            for (int i = 0; i < nx - 1; i++) {
                int a = j * nx + i;
                ib.put(a).put(a + nx).put(a + 1).put(a + 1).put(a + nx).put(a + nx + 1);
            }
        ib.flip();
        groundIdx = (nx - 1) * (nz - 1) * 6;
        gl.glGenBuffers(2, v, 0);
        groundVbo = v[0];
        groundIbo = v[1];
        gl.glBindVertexArray(groundVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, groundVbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, vb.capacity() * 4L, vb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, groundIbo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ib.capacity() * 4L, ib, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        gl.glGenQueries(4, q, 0);
    }

    /** Ein Bild in den gebundenen Bildspeicher (w × h): Himmel, Boden, Wasser, Wald; mit Zeitmessung je Teil. */
    void render(GL4 gl, int w, int h, ForestGl.View v, ForestGl.Light l, double t) {
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0.5f, 0.6f, 0.7f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glBeginQuery(GL4.GL_TIME_ELAPSED, q[0]);
        forest.update(gl, v);
        gl.glEndQuery(GL4.GL_TIME_ELAPSED);
        gl.glBeginQuery(GL4.GL_TIME_ELAPSED, q[1]);
        float[] inv = invert(v.viewProj);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(sky);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(sky, "uInv"), 1, false, inv, 0);
        light(gl, sky, v, l);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glUseProgram(ground);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(ground, "uVP"), 1, false, v.viewProj, 0);
        light(gl, ground, v, l);
        gl.glBindVertexArray(groundVao);
        gl.glDrawElements(GL.GL_TRIANGLES, groundIdx, GL.GL_UNSIGNED_INT, 0);
        gl.glUseProgram(water);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(water, "uVP"), 1, false, v.viewProj, 0);
        light(gl, water, v, l);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 6);
        gl.glEndQuery(GL4.GL_TIME_ELAPSED);
        gl.glBeginQuery(GL4.GL_TIME_ELAPSED, q[2]);
        forest.draw(gl, v, l, t);
        gl.glEndQuery(GL4.GL_TIME_ELAPSED);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        long[] ns = new long[1];
        gl.glGetQueryObjecti64v(q[0], GL4.GL_QUERY_RESULT, ns, 0);
        cullMs = ns[0] / 1e6;
        gl.glGetQueryObjecti64v(q[1], GL4.GL_QUERY_RESULT, ns, 0);
        restMs = ns[0] / 1e6;
        gl.glGetQueryObjecti64v(q[2], GL4.GL_QUERY_RESULT, ns, 0);
        forestMs = ns[0] / 1e6;
    }

    /** Boden unter der Spurlinie abgeräumt zeichnen (mit den Zuständen an). */
    static volatile boolean trimOn = true;

    static void light(GL4 gl, int p, ForestGl.View v, ForestGl.Light l) {
        gl.glUniform3f(gl.glGetUniformLocation(p, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunDir"), (float) l.sunDir[0], (float) l.sunDir[1], (float) l.sunDir[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSkyColor"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uExposure"), (float) v.exposure);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uTrim"), trimOn ? 1 : 0);
    }

    /** Licht eines Juliabends in 58,6° N: Sonne 9° hoch im Nordwesten [G]. */
    static ForestGl.Light evening() {
        ForestGl.Light l = new ForestGl.Light();
        double el = Math.toRadians(9), az = Math.toRadians(300);
        l.sunDir = new double[]{Math.cos(el) * Math.sin(az), Math.sin(el), -Math.cos(el) * Math.cos(az)};
        l.sunColor = new double[]{2.4, 1.9, 1.35};
        l.skyColor = new double[]{0.30, 0.38, 0.52};
        l.fogColor = new double[]{0.62, 0.66, 0.72};
        l.fogDistance = 24_000;
        return l;
    }

    void dispose(GL4 gl) { forest.dispose(gl); }

    // ------------------------------------------------------------------ Hilfen

    static int prog(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (String[] s : new String[][]{{vs, "v"}, {COMMON + fs, "f"}}) {
            int sh = gl.glCreateShader(s[1].equals("v") ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + s[0]}, null, 0);
            gl.glCompileShader(sh);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[8192];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("Shader: " + new String(b, 0, len[0]));
            }
            gl.glAttachShader(p, sh);
        }
        gl.glLinkProgram(p);
        return p;
    }

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
        float[] r = new float[16];
        for (int i = 0; i < 16; i++) r[i] = (float) (inv[i] / det);
        return r;
    }

    static float[] perspective(double fovy, double aspect, double near, double far) {
        float t = (float) (1 / Math.tan(Math.toRadians(fovy) / 2));
        return new float[]{t / (float) aspect, 0, 0, 0, 0, t, 0, 0, 0, 0, (float) ((far + near) / (near - far)), -1, 0, 0, (float) (2 * far * near / (near - far)), 0};
    }

    static float[] lookAt(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= l; fy /= l; fz /= l;
        double sx = -fz, sy = 0, sz = fx;
        l = Math.sqrt(sx * sx + sz * sz);
        sx /= l; sz /= l;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        return new float[]{(float) sx, (float) ux, (float) -fx, 0, (float) sy, (float) uy, (float) -fy, 0, (float) sz, (float) uz, (float) -fz, 0,
                (float) -(sx * e[0] + sy * e[1] + sz * e[2]), (float) -(ux * e[0] + uy * e[1] + uz * e[2]), (float) (fx * e[0] + fy * e[1] + fz * e[2]), 1};
    }

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

    // ------------------------------------------------------------------ Shader von Himmel, Boden, Wasser

    static final String COMMON = """
            uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uFogColor;
            uniform float uFogDist, uExposure, uTrim;
            vec3 skyCol(vec3 d) {
                float up = clamp(d.y, 0.0, 1.0);
                vec3 c = mix(uFogColor, uSkyColor * 1.6, pow(up, 0.45));
                float s = max(dot(d, uSunDir), 0.0);
                return c + uSunColor * (pow(s, 600.0) * 4.0 + pow(s, 8.0) * 0.12);
            }
            vec3 tone(vec3 c) { vec3 x = c * uExposure; return pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2)); }
            vec3 fog(vec3 col, vec3 w) {
                vec3 d = w - uCam;
                return mix(col, skyCol(normalize(vec3(d.x, 0.02 * length(d.xz), d.z))), 1.0 - exp(-length(d) / uFogDist));
            }
            """;
    static final String FULL = "out vec2 vNdc;\nvoid main(){ vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vNdc = p * 2.0 - 1.0; gl_Position = vec4(vNdc, 1.0, 1.0); }\n";
    static final String SKY = """
            in vec2 vNdc;
            out vec4 frag;
            uniform mat4 uInv;
            void main() {
                vec4 a = uInv * vec4(vNdc, -1.0, 1.0), b = uInv * vec4(vNdc, 1.0, 1.0);
                vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
                frag = vec4(tone(skyCol(d)), 1.0);
            }
            """;
    static final String GROUND_V = "layout(location = 0) in vec3 aPos;\nuniform mat4 uVP;\nout vec3 vW;\nvoid main(){ vW = aPos; gl_Position = uVP * vec4(aPos, 1.0); }\n";
    static final String GROUND_F = """
            in vec3 vW;
            out vec4 frag;
            void main() {
                vec3 n = normalize(cross(dFdx(vW), dFdy(vW)));
                if (n.y < 0.0) n = -n;
                float h = vW.y, slope = 1.0 - n.y;
                vec3 forest = vec3(0.035, 0.05, 0.022), rock = vec3(0.10, 0.095, 0.085), snow = vec3(0.7, 0.72, 0.76), beach = vec3(0.16, 0.15, 0.12);
                vec3 alb = mix(forest, rock, smoothstep(520.0, 700.0, h + slope * 300.0));
                alb = mix(alb, rock, smoothstep(0.55, 0.75, slope));
                alb = mix(alb, snow, smoothstep(1050.0, 1250.0, h) * (1.0 - smoothstep(0.6, 0.8, slope)));
                alb = mix(beach, alb, smoothstep(2.0, 8.0, h));
                // Nordufer unter der Spurlinie: der Boden bis auf Schutt und Fels abgeräumt (wie 1958)
                float trim = 30.0 + 494.0 * pow(clamp((vW.x + 6000.0) / 11600.0, 0.0, 1.0), 2.2);
                if (vW.z < 0.0 && h > 0.5) alb = mix(alb, vec3(0.13, 0.115, 0.095), (1.0 - smoothstep(trim * 0.9, trim * 1.02, h)) * uTrim);
                vec3 col = alb * (uSunColor * max(dot(n, uSunDir), 0.0) + uSkyColor * (0.5 + 0.5 * n.y));
                frag = vec4(tone(fog(col, vW)), 1.0);
            }
            """;
    static final String WATER_V = """
            uniform mat4 uVP;
            out vec3 vW;
            void main() {
                vec2 c[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(1, 1), vec2(-1, -1), vec2(1, 1), vec2(-1, 1));
                vW = vec3(c[gl_VertexID].x * 20000.0, 0.0, c[gl_VertexID].y * 20000.0);
                gl_Position = uVP * vec4(vW, 1.0);
            }
            """;
    static final String WATER_F = """
            in vec3 vW;
            out vec4 frag;
            void main() {
                vec3 v = normalize(vW - uCam);
                vec3 n = normalize(vec3(sin(vW.x * 0.11 + vW.z * 0.07) * 0.02, 1.0, cos(vW.z * 0.13 - vW.x * 0.05) * 0.02));
                vec3 r = reflect(v, n);
                float f = 0.02 + 0.98 * pow(1.0 - max(dot(-v, n), 0.0), 5.0);
                vec3 deep = vec3(0.03, 0.07, 0.07) * (uSkyColor + uSunColor * 0.15);   // Gletschermilch, grün-grau
                vec3 col = mix(deep, skyCol(vec3(r.x, abs(r.y), r.z)), f);
                frag = vec4(tone(fog(col, vW)), 1.0);
            }
            """;
}
