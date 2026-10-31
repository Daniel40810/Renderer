package com.dan.cliff.gl;

import com.dan.cliff.Cliff;
import com.dan.cliff.CliffMap;
import com.dan.cliff.Blockfield;
import com.dan.cliff.Overhang;
import com.dan.cliff.Sod;
import com.dan.cliff.StrataMap;
import com.dan.cliff.Tor;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * com.dan.cliff auf der Grafikkarte (JOGL, OpenGL 4.3):
 * <ul>
 * <li>{@link #GLSL}: der Baustein {@code cliff.glsl} zum Einbinden in eigene Shader (Gelände): Feinform im
 * Scheitelshader, Material je Bildpunkt; {@link #bind} setzt seine Uniforms und die Wandkarte.</li>
 * <li>{@link #upload}: die Wandkarte ({@link CliffMap}) als RGBA8.</li>
 * <li>{@link #setOverhangs}, {@link #drawOverhangs}: Überhänge als Zusatzgeometrie mit demselben Material.</li>
 * <li>{@link #setWall}, {@link #drawWall}: ein Höhenfeld als Netz mit Feinform (für die Vorführung ohne App).</li>
 * <li>{@link #selfCheck}: 64 Punkte auf der Grafikkarte gegen {@link Cliff#material} und {@link Cliff#relief}.</li>
 * <li>Seit Brocken Phase 8: {@link #setTors}, {@link #drawTors} (Felsburgen aus Wollsäcken) und {@link #drawBlocks}
 * (Blockmeer, instanziert, {@code block.vert}); beide als Fels erzwungen ({@code uCliffForce}).</li>
 * <li>Seit Sylt Phase 4: Lockergestein ({@link Cliff#rotesKliff}): {@link #bind} lädt die Schichtkarte
 * ({@link Cliff#strata}) als RGBA16F auf Einheit {@link #strataUnit}, wenn sie neu ist oder sich geändert hat;
 * {@link #setSods}, {@link #drawSods}: die überhängende Grasnarbe an der Kante als Netz.</li>
 * </ul>
 * Das Package kennt keine Szene: Licht und Blick kommen als Zahlen ({@link View}, {@link Light}), vorbelichtet.
 */
public final class CliffGl {
    /** Pfad des Bausteins im Klassenpfad (für {@code #include}). */
    public static final String GLSL = "/com/dan/cliff/gl/cliff.glsl";
    /** Einheit der Wandkarte, wenn der Aufrufer nichts anderes sagt. */
    public static final int MAP_UNIT = 14;
    /** Einheit der Rauschtextur (64³, R16): die Gitterwerte von {@link com.dan.cliff.CNoise}. */
    public static final int NOISE_UNIT = 15;
    /** Einheit der Schichtkarte (Lockergestein, seit Sylt Phase 4); der Aufrufer kann sie vor {@link #bind} ändern. */
    public int strataUnit = 13;

    /** Blick: Matrix, Auge, umgekehrte Tiefe, Öffnung eines Bildpunkts (rad). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = {0, 0, 0};
        public boolean reversedZ;
        public double pixelAngle = 0.001;
    }

    /** Licht, vorbelichtet: Richtung und Bestrahlung der Sonne, Himmel (Bestrahlung/π), Dunst; Anteil direkt, Nässe, Schnee. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {2.6, 2.5, 2.3}, ambient = {0.25, 0.3, 0.38}, fogColor = {0.6, 0.7, 0.8};
        public double fogDistance = 40_000, shade = 1, rainWet, snow;
        /** Für Vorführungen ohne eigene Nachbearbeitung: Belichtung, Filmkurve und Gamma gleich im Fels (0 = linear, HDR). */
        public double display;
        /**
         * Eis auf dem Fels (seit Brocken Phase 10; das Paket rechnet kein Eis, es trägt nur den Belag): Dicke (m), Art
         * (0 keins, 1 Raueis, 2 Raureif, 3 Klareis), woher der Wind kam (Grad), Höhenband (darunter keins, darüber
         * voll, m). Raueis setzt sich auf der Luvseite und an Kanten in Federn an, Raureif überall.
         */
        public double rimeThickness, rimeFromDeg = 270, rimeBase = -1e5, rimeFull = -1e5 + 1;
        public int rimeKind;
    }

    private int mapTex, mapN, noiseTex;
    private int strataTex, strataNx, strataNz, strataVersion = -1;
    private StrataMap strataMap;
    private List<Sod> sods = List.of();
    private boolean sodsDirty;
    private int sodVao, sodVbo, sodIbo, sodCount;
    private double[] mapRect = {0, 0, 1};
    private int progRock, progCheck, vao, vbo, ibo, count, wallVao, wallVbo, wallIbo, wallCount, checkVao, checkVbo;
    private List<Overhang> overhangs = List.of();
    private List<Tor> tors = List.of();
    private boolean torsDirty;
    private int torVao, torVbo, torIbo, torCount;
    private int progBlock, blockVao, blockMesh, blockIbo, blockInst, blockIdx, blockCap, blockVersion = -1, blockN;
    private boolean overhangsDirty, ready;
    private String info = "";

    public boolean ready() { return ready; }
    public String info() { return info; }
    public int mapTexture() { return mapTex; }
    public double[] mapRect() { return mapRect.clone(); }

    public void init(GL4 gl) {
        progRock = program(gl, "Fels", "rock.vert", "rock.frag");
        progCheck = program(gl, "Fels-Prüfung", "check.vert", "check.frag");
        progBlock = program(gl, "Blöcke", "block.vert", "rock.frag");
        // die Gitterwerte als 3D-Textur: ein Abruf je Rauschwert statt acht Hashes
        int P = com.dan.cliff.CNoise.PERIOD;
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        noiseTex = t[0];
        gl.glBindTexture(GL4.GL_TEXTURE_3D, noiseTex);
        gl.glTexStorage3D(GL4.GL_TEXTURE_3D, 1, GL4.GL_R16, P, P, P);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 2);
        gl.glTexSubImage3D(GL4.GL_TEXTURE_3D, 0, 0, 0, 0, P, P, P, GL4.GL_RED, GL.GL_UNSIGNED_SHORT,
                java.nio.ShortBuffer.wrap(com.dan.cliff.CNoise.latticeTexture()));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_WRAP_R, GL.GL_REPEAT);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, 0);
        ready = true;
        info = "Baustein cliff.glsl, Fels-Netz";
    }

    // ------------------------------------------------------------------ Baustein

    /**
     * Setzt die Uniforms von {@code cliff.glsl} im Programm prog und bindet die Wandkarte auf Einheit unit (ohne Karte:
     * Wand aus der Neigung). Das Programm muss gebunden sein.
     */
    public void bind(GL4 gl, int prog, Cliff c, int unit) {
        int k = gl.glGetUniformLocation(prog, "uCliffSeed");
        if (k < 0) return;   // Programm ohne Fels
        gl.glUniform1i(k, c.seed);
        double rockFrom = 1 - Math.cos(Math.toRadians(c.rockSlopeDeg)), w0 = 1 - Math.cos(Math.toRadians(c.wallSlopeDeg - 5)), w1 = 1 - Math.cos(Math.toRadians(c.wallSlopeDeg + 5));
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffSlope"), (float) rockFrom, (float) w0, (float) w1, c.quality.octaves);
        double dip = Math.toRadians(c.foliationDipDeg), dd = Math.toRadians(c.foliationDipDirDeg);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uCliffFol"), (float) (Math.sin(dip) * Math.sin(dd)), (float) Math.cos(dip), (float) (-Math.sin(dip) * Math.cos(dd)));
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffJoint"), (float) Math.toRadians(c.jointADeg + 90), (float) Math.toRadians(c.jointBDeg + 90),
                (float) c.jointSpacing, (float) c.jointWidth);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uCliffRelief"), (float) c.reliefAmp, (float) c.reliefScale);
        set3(gl, prog, "uCliffLight", c.gneissLight);
        set3(gl, prog, "uCliffDark", c.gneissDark);
        set3(gl, prog, "uCliffFeld", c.feldspar);
        set3(gl, prog, "uCliffLichen", c.lichen);
        set3(gl, prog, "uCliffMoss", c.moss);
        set3(gl, prog, "uCliffTalus", c.talus);
        set3(gl, prog, "uCliffSnow", c.snow);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffAmount"), (float) c.lichenAmount, (float) c.mossAmount, (float) c.seepAmount, (float) c.feldsparAmount);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uCliffFallWet"), (float) Math.max(0, Math.min(1, c.fallWet)));
        // seit Brocken Phase 8: Gestein, Bankung und Rundung (Granit); Netze setzen uCliffForce danach selbst
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uCliffRock"), c.rock == Cliff.Rock.GRANIT ? 1 : c.rock == Cliff.Rock.LOCKER ? 2 : 0);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uCliffSheet"), (float) c.sheetSpacing, (float) c.blockRound);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uCliffForce"), 0f);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uCliffPillow"), 1f);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uCliffTopMoss"), (float) Math.max(0, Math.min(1, c.topMoss)));
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glActiveTexture(GL.GL_TEXTURE0 + NOISE_UNIT);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, noiseTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uCliffNoise"), NOISE_UNIT);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uCliffMap"), unit);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffMapRect"), (float) mapRect[0], (float) mapRect[1], (float) mapRect[2], mapTex != 0 ? 1f : 0f);
        bindLoose(gl, prog, c, unit);
    }

    /** Lockergestein (seit Sylt Phase 4): Schichtkarte, Deckschichten, Farben, Runsen, Hohlkehle. */
    private void bindLoose(GL4 gl, int prog, Cliff c, int unit) {
        StrataMap m = c.rock == Cliff.Rock.LOCKER ? c.strata : null;
        if (m != null) uploadStrata(gl, m);
        boolean on = m != null && strataTex != 0;
        if (on) {
            gl.glActiveTexture(GL.GL_TEXTURE0 + strataUnit);
            gl.glBindTexture(GL.GL_TEXTURE_2D, strataTex);
            gl.glActiveTexture(GL.GL_TEXTURE0);
        }
        // ohne Karte zeigt der Sampler auf die Einheit der Wandkarte (gleicher Typ, wird nicht gelesen)
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uCliffStrata"), on ? strataUnit : unit);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uCliffStrataOn"), on ? 1 : 0);
        if (on) gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffStrataRect"), (float) m.x0, (float) m.z0, (float) (m.nx * m.cell), (float) (m.nz * m.cell));
        double[] d = c.strataDefault;
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffStrataDef"), (float) d[0], (float) d[1], (float) d[2], (float) d[3]);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffThick"), (float) c.sodThick, (float) c.bleachThick, (float) c.podsolThick, (float) c.stoneThick);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffThick2"), (float) c.ironThick, (float) c.notchBase, (float) Math.max(0.1, c.notchHeight), (float) Math.max(1.1, c.seepLength));
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffGully"), (float) Math.max(0.1, c.gullySpacing), (float) c.gullyDepth, (float) c.gullyAmount, (float) c.trickleAmount);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uCliffLoose"), (float) c.crackAmount, (float) c.erraticAmount);
        set3(gl, prog, "uCliffLehm", c.lehm);
        set3(gl, prog, "uCliffLehmDark", c.lehmDark);
        set3(gl, prog, "uCliffKaolin", c.kaolin);
        set3(gl, prog, "uCliffKaolinDark", c.kaolinDark);
        set3(gl, prog, "uCliffIron", c.iron);
        set3(gl, prog, "uCliffSand", c.sand);
        set3(gl, prog, "uCliffBleach", c.bleach);
        set3(gl, prog, "uCliffPodsol", c.podsol);
        set3(gl, prog, "uCliffStone", c.stone);
        set3(gl, prog, "uCliffSod", c.sod);
        set3(gl, prog, "uCliffGrass", c.grass);
    }

    /** Die Schichtkarte hochladen, wenn sie neu ist oder sich geändert hat ({@link StrataMap#version}). */
    public void uploadStrata(GL4 gl, StrataMap m) {
        if (m == strataMap && m.version == strataVersion && strataTex != 0) return;
        if (strataTex == 0 || strataNx != m.nx || strataNz != m.nz) {
            if (strataTex != 0) gl.glDeleteTextures(1, new int[]{strataTex}, 0);
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            strataTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, strataTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, m.nx, m.nz);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            strataNx = m.nx;
            strataNz = m.nz;
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, strataTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 2);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, m.nx, m.nz, GL.GL_RGBA, GL.GL_HALF_FLOAT, java.nio.ShortBuffer.wrap(m.rgba));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        strataMap = m;
        strataVersion = m.version;
    }

    /** Die Textur der Schichtkarte (0 = keine), für Programme, die sie selbst binden. */
    public int strataTexture() { return strataTex; }

    // ------------------------------------------------------------------ Grasnarbe (seit Sylt Phase 4)

    /** Die Grasnarben setzen (das Netz entsteht beim nächsten Zeichnen). */
    public void setSods(List<Sod> s) {
        sods = List.copyOf(s);
        sodsDirty = true;
    }

    public int sodCount() { return sods.size(); }

    /** Die überhängende Grasnarbe zeichnen (Material als Fels erzwungen, die Schichten nach der Tiefe unter der Oberkante). */
    public void drawSods(GL4 gl, Cliff c, View v, Light l) {
        if (sods.isEmpty()) return;
        if (sodsDirty) {
            List<float[]> vs = new ArrayList<>();
            List<int[]> is = new ArrayList<>();
            for (Sod s : sods) { Overhang.Mesh m = s.mesh(); vs.add(m.vertices); is.add(m.indices); }
            int[] ids = upload(gl, sodVao, sodVbo, sodIbo, vs, is);
            sodVao = ids[0]; sodVbo = ids[1]; sodIbo = ids[2]; sodCount = ids[3];
            sodsDirty = false;
        }
        drawProgram(gl, progRock, c, v, l, sodVao, sodCount, false, 1, 0);
    }

    private static void set3(GL4 gl, int prog, String u, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(prog, u), (float) v[0], (float) v[1], (float) v[2]);
    }

    /** Die Wandkarte hochladen (ersetzt eine vorige). */
    public void upload(GL4 gl, CliffMap m) {
        if (mapTex == 0 || mapN != m.n) {
            if (mapTex != 0) gl.glDeleteTextures(1, new int[]{mapTex}, 0);
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            mapTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA8, m.n, m.n);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            mapN = m.n;
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, m.n, m.n, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, ByteBuffer.wrap(m.rgba));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        mapRect = new double[]{m.x0, m.z0, m.n * m.cell};
    }

    // ------------------------------------------------------------------ Überhänge und Wand

    /** Die Überhänge setzen (das Netz entsteht beim nächsten Zeichnen). */
    public void setOverhangs(List<Overhang> o) {
        overhangs = List.copyOf(o);
        overhangsDirty = true;
    }

    public int overhangCount() { return overhangs.size(); }

    /** Überhänge zeichnen (in den gebundenen Bildspeicher, Tiefentest wie der Aufrufer). */
    public void drawOverhangs(GL4 gl, Cliff c, View v, Light l) {
        if (!c.quality.overhangs || overhangs.isEmpty()) return;
        if (overhangsDirty) {
            List<float[]> vs = new ArrayList<>();
            List<int[]> is = new ArrayList<>();
            for (Overhang o : overhangs) { Overhang.Mesh m = o.mesh(28); vs.add(m.vertices); is.add(m.indices); }
            int[] ids = upload(gl, vao, vbo, ibo, vs, is);
            vao = ids[0]; vbo = ids[1]; ibo = ids[2]; count = ids[3];
            overhangsDirty = false;
        }
        drawRock(gl, c, v, l, vao, count, false);
    }

    // ------------------------------------------------------------------ Felsburgen und Blockmeer (seit Brocken Phase 8)

    /** Die Felsburgen setzen (das Netz entsteht beim nächsten Zeichnen). */
    public void setTors(List<Tor> t) {
        tors = List.copyOf(t);
        torsDirty = true;
    }

    public int torCount() { return tors.size(); }

    /** Felsburgen zeichnen (Material als Fels erzwungen; ohne Feinform, die Rundung tragen die Blöcke). */
    public void drawTors(GL4 gl, Cliff c, View v, Light l) {
        if (tors.isEmpty()) return;
        if (torsDirty) {
            List<float[]> vs = new ArrayList<>();
            List<int[]> is = new ArrayList<>();
            for (Tor t : tors) { Overhang.Mesh m = t.mesh(28); vs.add(m.vertices); is.add(m.indices); }
            int[] ids = upload(gl, torVao, torVbo, torIbo, vs, is);
            torVao = ids[0]; torVbo = ids[1]; torIbo = ids[2]; torCount = ids[3];
            torsDirty = false;
        }
        drawProgram(gl, progRock, c, v, l, torVao, torCount, false, 1, 0);
    }

    /**
     * Ein Blockmeer zeichnen: die Blöcke um das Auge aus {@link Blockfield#instances}, hochgeladen, wenn sich etwas
     * geändert hat, je Block ein Superellipsoid mit 6 × 10 Feldern. Gibt die Zahl der gezeichneten Blöcke zurück.
     */
    public int drawBlocks(GL4 gl, Cliff c, Blockfield f, View v, Light l) {
        float[] inst = f.instances(v.eye[0], v.eye[2], v.pixelAngle);
        int n = f.count();
        if (blockVao == 0) {
            int nu = 6, nv = 10;
            float[] g = new float[(nu + 1) * (nv + 1) * 3];
            int k = 0;
            for (int i = 0; i <= nu; i++)
                for (int j = 0; j <= nv; j++) {
                    g[k++] = (float) (-Math.PI / 2 + Math.PI * i / nu);
                    g[k++] = (float) (-Math.PI + 2 * Math.PI * j / nv);
                    g[k++] = 0;
                }
            int[] idx = new int[nu * nv * 6];
            int t = 0;
            for (int i = 0; i < nu; i++)
                for (int j = 0; j < nv; j++) {
                    int a = i * (nv + 1) + j, b = a + nv + 1;
                    idx[t++] = a; idx[t++] = b; idx[t++] = a + 1;
                    idx[t++] = a + 1; idx[t++] = b; idx[t++] = b + 1;
                }
            int[] o = new int[1];
            gl.glGenVertexArrays(1, o, 0);
            blockVao = o[0];
            int[] b = new int[3];
            gl.glGenBuffers(3, b, 0);
            blockMesh = b[0]; blockIbo = b[1]; blockInst = b[2];
            gl.glBindVertexArray(blockVao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, blockMesh);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) g.length * 4, FloatBuffer.wrap(g), GL.GL_STATIC_DRAW);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, blockIbo);
            gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) idx.length * 4, IntBuffer.wrap(idx), GL.GL_STATIC_DRAW);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, blockInst);
            gl.glEnableVertexAttribArray(2);
            gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, 32, 0);
            gl.glVertexAttribDivisor(2, 1);
            gl.glEnableVertexAttribArray(3);
            gl.glVertexAttribPointer(3, 4, GL.GL_FLOAT, false, 32, 16);
            gl.glVertexAttribDivisor(3, 1);
            gl.glBindVertexArray(0);
            blockIdx = idx.length;
        }
        if (f.version() != blockVersion) {
            blockVersion = f.version();
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, blockInst);
            long bytes = (long) Math.max(1, n) * Blockfield.STRIDE * 4;
            if (n > blockCap) {
                blockCap = Math.max(n, blockCap * 2);
                gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) blockCap * Blockfield.STRIDE * 4, null, GL.GL_DYNAMIC_DRAW);
            }
            if (n > 0) gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, bytes, FloatBuffer.wrap(inst, 0, n * Blockfield.STRIDE));
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
            blockN = n;
        }
        if (blockN > 0) drawProgram(gl, progBlock, c, v, l, blockVao, blockIdx, false, 1, blockN);
        return blockN;
    }

    /**
     * Ein Höhenfeld als Netz (für die Vorführung): n × n Ecken über dem Quadrat (x0, z0, Kante), Normalen aus dem
     * Feld. Die Feinform verschiebt die Ecken beim Zeichnen.
     */
    public void setWall(GL4 gl, Cliff.Heights h, double x0, double z0, double size, int n) {
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
        int[] ids = upload(gl, wallVao, wallVbo, wallIbo, List.of(v), List.of(idx));
        wallVao = ids[0]; wallVbo = ids[1]; wallIbo = ids[2]; wallCount = ids[3];
    }

    public void drawWall(GL4 gl, Cliff c, View v, Light l) { drawRock(gl, c, v, l, wallVao, wallCount, true); }

    private void drawRock(GL4 gl, Cliff c, View v, Light l, int va, int n, boolean displace) {
        drawProgram(gl, progRock, c, v, l, va, n, displace, 0, 0);
    }

    /** Fels zeichnen mit dem Programm prog; force 0..1 erzwingt Fels; instances > 0 zeichnet so viele Exemplare. */
    private void drawProgram(GL4 gl, int progRock, Cliff c, View v, Light l, int va, int n, boolean displace, double force, int instances) {
        if (va == 0 || n == 0) return;
        gl.glUseProgram(progRock);
        bind(gl, progRock, c, MAP_UNIT);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uCliffForce"), (float) force);
        // Netze aus Blöcken (Felsburgen, Blockmeer) tragen die Rundung selbst: keine Wollsäcke darauf
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uCliffPillow"), force > 0 ? 0f : 1f);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(progRock, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(progRock, "uEye"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1i(gl.glGetUniformLocation(progRock, "uDisplace"), displace ? 1 : 0);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uPixelAngle"), (float) v.pixelAngle);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uRange"), (float) c.quality.reliefRange);
        set3(gl, progRock, "uSunDir", l.sunDir);
        set3(gl, progRock, "uSunColor", l.sunColor);
        set3(gl, progRock, "uAmbient", l.ambient);
        set3(gl, progRock, "uFogColor", l.fogColor);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uShade"), (float) l.shade);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uRainWet"), (float) l.rainWet);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uSnow"), (float) l.snow);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uDisplay"), (float) l.display);
        double rf = Math.toRadians(l.rimeFromDeg);
        gl.glUniform4f(gl.glGetUniformLocation(progRock, "uRime"), (float) (l.rimeKind == 0 ? 0 : l.rimeThickness), l.rimeKind, (float) Math.sin(rf), (float) -Math.cos(rf));
        gl.glUniform2f(gl.glGetUniformLocation(progRock, "uRimeBand"), (float) l.rimeBase, (float) Math.max(l.rimeBase + 1, l.rimeFull));
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(va);
        if (instances > 0) gl.glDrawElementsInstanced(GL.GL_TRIANGLES, n, GL.GL_UNSIGNED_INT, 0, instances);
        else gl.glDrawElements(GL.GL_TRIANGLES, n, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** Netze zu einem Puffer zusammenlegen; gibt {vao, vbo, ibo, Indexzahl}. */
    private static int[] upload(GL4 gl, int va, int vb, int ib, List<float[]> vs, List<int[]> is) {
        int nv = 0, ni = 0;
        for (float[] v : vs) nv += v.length;
        for (int[] i : is) ni += i.length;
        FloatBuffer fb = FloatBuffer.allocate(nv);
        IntBuffer ibuf = IntBuffer.allocate(ni);
        int base = 0;
        for (int k = 0; k < vs.size(); k++) {
            fb.put(vs.get(k));
            for (int x : is.get(k)) ibuf.put(x + base);
            base += vs.get(k).length / 6;
        }
        fb.flip();
        ibuf.flip();
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
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) nv * 4, fb, GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ib);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) ni * 4, ibuf, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12);
        gl.glBindVertexArray(0);
        return new int[]{va, vb, ib, ni};
    }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Rechnet 64 Punkte (an einer Wand, im Umkreis von 200 m um den Ursprung) auf der Grafikkarte und vergleicht Farbe
     * und Relief mit dem Prozessor (ohne Wandkarte). Ergebnis: „Grafikkarte = Prozessor: Farbe ± 0,4 %, Relief ± 1 mm“.
     */
    public String selfCheck(GL4 gl, Cliff c) {
        int n = 64;
        float[] pts = new float[n * 6];
        java.util.Random r = new java.util.Random(c.seed);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, sl = Math.toRadians(30 + r.nextDouble() * 60);
            pts[i * 6] = (float) (r.nextDouble() * 400 - 200);
            pts[i * 6 + 1] = (float) (r.nextDouble() * 300);
            pts[i * 6 + 2] = (float) (r.nextDouble() * 400 - 200);
            pts[i * 6 + 3] = (float) (Math.sin(sl) * Math.sin(a));
            pts[i * 6 + 4] = (float) Math.cos(sl);
            pts[i * 6 + 5] = (float) (-Math.sin(sl) * Math.cos(a));
        }
        double fp = 0.05;
        int[] save = new int[4];
        gl.glGetIntegerv(GL.GL_VIEWPORT, save, 0);
        int[] fbo = new int[1], tex = new int[1];
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
        int mt = mapTex;
        mapTex = 0;   // ohne Karte: Wand aus der Neigung, wie Cliff.material(..., null, ...)
        bind(gl, progCheck, c, MAP_UNIT);
        mapTex = mt;
        gl.glUniform1f(gl.glGetUniformLocation(progCheck, "uCount"), n);
        gl.glUniform1f(gl.glGetUniformLocation(progCheck, "uFootprint"), (float) fp);
        int[] ids = upload(gl, checkVao, checkVbo, 0, List.of(pts), List.of(new int[0]));
        checkVao = ids[0];
        checkVbo = ids[1];
        if (ids[2] != 0) gl.glDeleteBuffers(1, new int[]{ids[2]}, 0);
        gl.glBindVertexArray(checkVao);
        gl.glDrawArrays(GL.GL_POINTS, 0, n);
        gl.glBindVertexArray(0);
        FloatBuffer out = FloatBuffer.allocate(n * 4);
        gl.glReadPixels(0, 0, n, 1, GL.GL_RGBA, GL.GL_FLOAT, out);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glDeleteFramebuffers(1, fbo, 0);
        gl.glDeleteTextures(1, tex, 0);
        gl.glViewport(save[0], save[1], save[2], save[3]);
        gl.glUseProgram(0);
        return compare(c, pts, out.array(), fp);
    }

    /** Vergleich Grafikkarte gegen Prozessor (auch für Prüfungen mit einer anderen Grafikschicht). */
    public static String compare(Cliff c, float[] pts, float[] gpu, double fp) {
        double col = 0, rel = 0;
        for (int i = 0; i < pts.length / 6; i++) {
            Cliff.Sample s = c.material(pts[i * 6], pts[i * 6 + 1], pts[i * 6 + 2], pts[i * 6 + 3], pts[i * 6 + 4], pts[i * 6 + 5], null, 0, 0, fp);
            for (int k = 0; k < 3; k++) col = Math.max(col, Math.abs(s.albedo[k] - gpu[i * 4 + k]) / Math.max(0.05, s.albedo[k]));
            rel = Math.max(rel, Math.abs(c.relief(pts[i * 6], pts[i * 6 + 1], pts[i * 6 + 2], fp) - gpu[i * 4 + 3]));
        }
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Farbe ± %.2f %%, Relief ± %.1f mm", col * 100, rel * 1000);
    }

    public void dispose(GL4 gl) {
        for (int p : new int[]{progRock, progCheck, progBlock}) if (p != 0) gl.glDeleteProgram(p);
        for (int a : new int[]{vao, wallVao, checkVao, torVao, blockVao, sodVao}) if (a != 0) gl.glDeleteVertexArrays(1, new int[]{a}, 0);
        for (int b : new int[]{vbo, ibo, wallVbo, wallIbo, checkVbo, torVbo, torIbo, blockMesh, blockIbo, blockInst, sodVbo, sodIbo}) if (b != 0) gl.glDeleteBuffers(1, new int[]{b}, 0);
        if (strataTex != 0) gl.glDeleteTextures(1, new int[]{strataTex}, 0);
        strataTex = 0;
        strataVersion = -1;
        sodVao = 0;
        sodsDirty = !sods.isEmpty();
        torVao = blockVao = progBlock = 0;
        blockVersion = -1;
        blockCap = 0;
        torsDirty = !tors.isEmpty();
        if (mapTex != 0) gl.glDeleteTextures(1, new int[]{mapTex}, 0);
        mapTex = progRock = progCheck = vao = wallVao = checkVao = 0;
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
        try (InputStream in = CliffGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("cliff: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cliff: " + file + ": " + e.getMessage(), e);
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
                throw new IllegalStateException("cliff: " + f[i] + " (" + name + ") lässt sich nicht übersetzen:\n" + new String(log, StandardCharsets.UTF_8));
            }
            gl.glAttachShader(prog, sh[i]);
        }
        gl.glLinkProgram(prog);
        int[] ok = new int[1];
        gl.glGetProgramiv(prog, GL4.GL_LINK_STATUS, ok, 0);
        for (int s : sh) gl.glDeleteShader(s);
        if (ok[0] == 0) throw new IllegalStateException("cliff: " + name + " lässt sich nicht binden");
        return prog;
    }
}
